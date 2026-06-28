package iad1tya.echo.music.ui.screens.equalizer.axion

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import iad1tya.echo.music.eq.EqualizerService
import iad1tya.echo.music.eq.data.EQProfileRepository
import iad1tya.echo.music.eq.data.EqConstants
import iad1tya.echo.music.eq.data.EqMode
import iad1tya.echo.music.eq.data.FactoryPreset
import iad1tya.echo.music.eq.data.FilterType
import iad1tya.echo.music.eq.data.ParametricEQBand
import iad1tya.echo.music.eq.data.SavedEQProfile
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Pure mapping: per-band dB gains + band types → ParametricEQ bands.
 * Gain is stored directly in dB (matches the desktop engine; no scaling).
 */
fun buildEqBands(gainsDb: FloatArray, types: IntArray): List<ParametricEQBand> =
    EqConstants.FREQUENCIES.mapIndexed { i, freq ->
        ParametricEQBand(
            frequency = freq,
            gain = gainsDb.getOrElse(i) { 0f }.toDouble(),
            q = EqConstants.Q,
            filterType = when (types.getOrElse(i) { 0 }) {
                1 -> FilterType.LSC
                2 -> FilterType.HSC
                else -> FilterType.PK
            },
            enabled = true,
        )
    }

/**
 * Parametric (PEQ) constraints. The 5–8 PEQ bands are fully user-defined (free frequency / Q / gain),
 * unlike the fixed 24 graphic centers. Same [ParametricEQBand] type → same DSP path.
 */
object PeqConstants {
    const val MIN_BANDS = 5
    const val MAX_BANDS = 8
    const val FREQ_MIN = 20.0
    const val FREQ_MAX = 20000.0
    const val Q_MIN = 0.3
    const val Q_MAX = 10.0
    const val GAIN_MIN = -18.0
    const val GAIN_MAX = 18.0
    const val Q_DEFAULT = 1.0

    /** Default 6-band PEQ — sensible full-range anchors, flat (0 dB), peak filters. */
    val DEFAULT_FREQS = doubleArrayOf(60.0, 200.0, 600.0, 2000.0, 6000.0, 12000.0)

    fun defaultBands(): List<ParametricEQBand> = DEFAULT_FREQS.map { f ->
        ParametricEQBand(frequency = f, gain = 0.0, q = Q_DEFAULT, filterType = FilterType.PK, enabled = true)
    }
}

/**
 * Serializable DTO for persisting PEQ bands as JSON (the engine model [ParametricEQBand] is already
 * @Serializable, but we keep a stable, minimal {freqHz, q, gainDb, type} shape independent of it).
 */
@kotlinx.serialization.Serializable
data class PeqBandDto(
    val freqHz: Double,
    val q: Double,
    val gainDb: Double,
    val type: String, // FilterType.name (PK / LSC / HSC)
) {
    fun toBand(): ParametricEQBand = ParametricEQBand(
        frequency = freqHz,
        gain = gainDb,
        q = q,
        filterType = runCatching { FilterType.valueOf(type) }.getOrDefault(FilterType.PK),
        enabled = true,
    )

    companion object {
        fun from(b: ParametricEQBand): PeqBandDto =
            PeqBandDto(freqHz = b.frequency, q = b.q, gainDb = b.gain, type = b.filterType.name)
    }
}

/**
 * 24-band ISO 1/3-octave graphic equalizer view model — desktop JR DSP Pro parity.
 * Band gains and pre-amp are kept in dB and pushed to the real biquad chain through
 * [EqualizerService] / [EQProfileRepository].
 */
@HiltViewModel
class AxionEqViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val equalizerService: EqualizerService,
    private val eqProfileRepository: EQProfileRepository,
) : ViewModel() {

    private val prefs = context.getSharedPreferences("echo_eq_prefs", Context.MODE_PRIVATE)
    private val n = EqConstants.BAND_COUNT

    private val _enabled = MutableStateFlow(prefs.getBoolean("enabled", false))
    val enabled = _enabled.asStateFlow()

    // Auto-EQ now runs as its OWN cascaded correction stage that the manual EQ STACKS on top of (it no
    // longer replaces / locks the manual bands). This flag means "an Auto-EQ correction stage is present"
    // — NOT a lock on the manual editor.
    private val _autoEqActive = MutableStateFlow(prefs.getBoolean("autoeq_active", false))
    val autoEqActive = _autoEqActive.asStateFlow()

    // The Auto-EQ correction curve, kept independently from the manual graphic/parametric bands and
    // persisted as its own JSON (reuses the PeqBandDto serialization). Fed to the DSP as ParametricEQ.autoBands.
    private val _autoEqBands = MutableStateFlow(loadAutoEqBands())
    val autoEqBands = _autoEqBands.asStateFlow()

    private fun setAutoEqActive(active: Boolean) {
        _autoEqActive.value = active
        prefs.edit().putBoolean("autoeq_active", active).apply()
    }

    private fun loadAutoEqBands(): List<ParametricEQBand> {
        val json = prefs.getString("autoeq_bands", null) ?: return emptyList()
        return runCatching {
            kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
                .decodeFromString(
                    kotlinx.serialization.builtins.ListSerializer(PeqBandDto.serializer()),
                    json,
                )
                .map { it.toBand() }
        }.getOrNull() ?: emptyList()
    }

    private fun persistAutoEqBands() {
        val dtos = _autoEqBands.value.map { PeqBandDto.from(it) }
        val json = kotlinx.serialization.json.Json
            .encodeToString(kotlinx.serialization.builtins.ListSerializer(PeqBandDto.serializer()), dtos)
        prefs.edit().putString("autoeq_bands", json).apply()
    }

    /** Remove the Auto-EQ correction stage entirely (manual EQ is untouched). Wired to "Quitar Auto-EQ". */
    fun clearAutoEq() {
        _autoEqBands.value = emptyList()
        persistAutoEqBands()
        setAutoEqActive(false)
        if (_enabled.value) applyToService()
    }

    // Band gains in dB (new "band24_" keys; legacy 10-band "band_" keys are intentionally ignored).
    private val _bandGains = MutableStateFlow(FloatArray(n) { prefs.getFloat("band24_$it", 0f) })
    val bandGains = _bandGains.asStateFlow()

    // Per-band filter type: 0=Peak, 1=LowShelf, 2=HighShelf.
    private val _bandTypes = MutableStateFlow(IntArray(n) { prefs.getInt("type24_$it", 0) })
    val bandTypes = _bandTypes.asStateFlow()

    private val _preamp = MutableStateFlow(prefs.getFloat("preampDb", 0f))
    val preamp = _preamp.asStateFlow()

    // EQ editing mode: GRAPHIC (24-band, default) vs PARAMETRIC (5–8 free PEQ bands). Both curves are
    // kept independently (separate state + prefs keys) so switching modes never loses the other.
    private val _eqMode = MutableStateFlow(
        runCatching { EqMode.valueOf(prefs.getString("eq_mode", "GRAPHIC")!!) }.getOrDefault(EqMode.GRAPHIC)
    )
    val eqMode = _eqMode.asStateFlow()

    // Parametric (PEQ) bands — defaults to the 6 anchor bands. Persisted as a JSON DTO list.
    private val _peqBands = MutableStateFlow(loadPeqBands())
    val peqBands = _peqBands.asStateFlow()

    private val _isDirty = MutableStateFlow(false)
    val isDirty = _isDirty.asStateFlow()

    val customProfiles = eqProfileRepository.profiles.map { profiles ->
        profiles.filter { it.isCustom && it.id != "echo_tuning" }
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    init {
        // Migrated users from before the stacking change have autoeq_active=true but no autoeq_bands JSON
        // (their old Auto-EQ curve lives in the manual band24_* gains). Reconcile so they don't see a phantom
        // "Auto-EQ activo" chip for a correction stage that doesn't exist.
        if (_autoEqActive.value && _autoEqBands.value.isEmpty()) setAutoEqActive(false)
        // Do NOT re-apply the EQ here. applyToService() persists echo_tuning as the active profile, so the
        // service already applies it at startup (MusicService collects eqProfileRepository.activeProfile).
        // Re-applying when the EQ screen opens just re-emits the same profile and caused an audible blip
        // ("the sound changes as if the EQ activates" on entry). The chain is already live from startup; user
        // edits re-apply through their own mutators.
    }

    fun setEnabled(enabled: Boolean) {
        _enabled.value = enabled
        prefs.edit().putBoolean("enabled", enabled).apply()
        if (enabled) {
            applyToService()
        } else {
            viewModelScope.launch { eqProfileRepository.setActiveProfile(null) }
            equalizerService.disable()
        }
    }

    fun setBandGain(index: Int, gainDb: Float) {
        if (index !in 0 until n) return
        val v = gainDb.coerceIn(EqConstants.GAIN_MIN, EqConstants.GAIN_MAX)
        val arr = _bandGains.value.copyOf()
        arr[index] = v
        _bandGains.value = arr
        prefs.edit().putFloat("band24_$index", v).apply()
        _isDirty.value = true
        if (_enabled.value) applyToService()
    }

    fun setBandsGains(gains: FloatArray, fromUser: Boolean = false) {
        val arr = FloatArray(n) { i ->
            gains.getOrElse(i) { 0f }.coerceIn(EqConstants.GAIN_MIN, EqConstants.GAIN_MAX)
        }
        _bandGains.value = arr
        val editor = prefs.edit()
        arr.forEachIndexed { i, f -> editor.putFloat("band24_$i", f) }
        editor.apply()
        _isDirty.value = fromUser
        if (_enabled.value) applyToService()
    }

    fun setPreamp(db: Float) {
        val v = db.coerceIn(EqConstants.PREAMP_MIN, EqConstants.PREAMP_MAX)
        _preamp.value = v
        prefs.edit().putFloat("preampDb", v).apply()
        _isDirty.value = true
        if (_enabled.value) applyToService()
    }

    /** Switch between GRAPHIC and PARAMETRIC. The inactive curve is preserved (separate state/prefs). */
    fun setEqMode(m: EqMode) {
        _eqMode.value = m
        prefs.edit().putString("eq_mode", m.name).apply()
        if (_enabled.value) applyToService()
    }

    // ---- Parametric (PEQ) editing ----------------------------------------------------------------

    private fun loadPeqBands(): List<ParametricEQBand> {
        val json = prefs.getString("peq_bands", null) ?: return PeqConstants.defaultBands()
        return runCatching {
            kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
                .decodeFromString(
                    kotlinx.serialization.builtins.ListSerializer(PeqBandDto.serializer()),
                    json,
                )
                .map { it.toBand() }
                .takeIf { it.size in PeqConstants.MIN_BANDS..PeqConstants.MAX_BANDS }
        }.getOrNull() ?: PeqConstants.defaultBands()
    }

    private fun persistPeqBands() {
        val dtos = _peqBands.value.map { PeqBandDto.from(it) }
        val json = kotlinx.serialization.json.Json
            .encodeToString(kotlinx.serialization.builtins.ListSerializer(PeqBandDto.serializer()), dtos)
        prefs.edit().putString("peq_bands", json).apply()
    }

    /**
     * Edit a single PEQ band LIVE (any null arg is left unchanged). Clamps to PEQ ranges. Mirrors the
     * graphic [setBandGainLive] split: this only updates the DSP coefficients in place (no disk writes,
     * no profile save) so typing doesn't hit disk every keystroke. Persist via [commitPeq].
     */
    fun setPeqBand(index: Int, freq: Double? = null, q: Double? = null, gain: Double? = null, type: FilterType? = null) {
        val list = _peqBands.value
        if (index !in list.indices) return
        val cur = list[index]
        val updated = cur.copy(
            frequency = (freq ?: cur.frequency).coerceIn(PeqConstants.FREQ_MIN, PeqConstants.FREQ_MAX),
            q = (q ?: cur.q).coerceIn(PeqConstants.Q_MIN, PeqConstants.Q_MAX),
            gain = (gain ?: cur.gain).coerceIn(PeqConstants.GAIN_MIN, PeqConstants.GAIN_MAX),
            filterType = type ?: cur.filterType,
        )
        _peqBands.value = list.toMutableList().also { it[index] = updated }
        _isDirty.value = true
        if (_enabled.value) equalizerService.applyProfile(liveProfile())
    }

    /** Append a sensible default PEQ band (max 8). Inserts at 1 kHz, flat, peak. */
    fun addPeqBand() {
        val list = _peqBands.value
        if (list.size >= PeqConstants.MAX_BANDS) return
        val band = ParametricEQBand(
            frequency = 1000.0, gain = 0.0, q = PeqConstants.Q_DEFAULT, filterType = FilterType.PK, enabled = true,
        )
        _peqBands.value = list + band
        _isDirty.value = true
        commitPeq()
        if (_enabled.value) applyToService()
    }

    /** Remove a PEQ band (min 5). */
    fun removePeqBand(index: Int) {
        val list = _peqBands.value
        if (list.size <= PeqConstants.MIN_BANDS || index !in list.indices) return
        _peqBands.value = list.toMutableList().also { it.removeAt(index) }
        _isDirty.value = true
        commitPeq()
        if (_enabled.value) applyToService()
    }

    /** Reset the PEQ back to the default flat 6-band anchors (clears any custom curve). Persists + applies. */
    fun resetPeq() {
        _peqBands.value = PeqConstants.defaultBands()
        _isDirty.value = true
        commitPeq()
        if (_enabled.value) applyToService()
    }

    /** Persist the PEQ bands JSON — call on text-field focus loss / value settle (or via [commit]). */
    fun commitPeq() {
        persistPeqBands()
        if (_enabled.value) viewModelScope.launch {
            val p = liveProfile()
            eqProfileRepository.saveProfile(p)
            eqProfileRepository.setActiveProfile(p.id)
        }
    }

    /**
     * Apply a full profile (bands + preamp + enable) in ONE shot. Calling setBandsGains + setPreamp +
     * setEnabled separately fired applyToService() three times (each doing 2 DB writes + a DSP
     * re-apply), which stuttered the audio every time an AutoEq profile was selected. This batches it
     * into a single apply.
     */
    fun applyProfileBatch(gains: FloatArray, preampDb: Float, isAutoEq: Boolean = false) {
        val arr = FloatArray(n) { i ->
            gains.getOrElse(i) { 0f }.coerceIn(EqConstants.GAIN_MIN, EqConstants.GAIN_MAX)
        }
        _preamp.value = preampDb.coerceIn(EqConstants.PREAMP_MIN, EqConstants.PREAMP_MAX)
        _enabled.value = true
        if (isAutoEq) {
            // Auto-EQ correction stage: store the projected curve as its OWN autoBands (24 PK bands at the
            // ISO centers). The manual graphic/parametric EQ is left UNTOUCHED so it stacks on top.
            _autoEqBands.value = buildEqBands(arr, IntArray(n) { 0 })
            persistAutoEqBands()
            prefs.edit().apply {
                putFloat("preampDb", _preamp.value)
                putBoolean("enabled", true)
            }.apply()
            setAutoEqActive(true)
        } else {
            // Loading a saved graphic profile: write the manual 24-band curve as before.
            _bandGains.value = arr
            prefs.edit().apply {
                arr.forEachIndexed { i, f -> putFloat("band24_$i", f) }
                putFloat("preampDb", _preamp.value)
                putBoolean("enabled", true)
            }.apply()
        }
        _isDirty.value = true
        applyToService()
    }

    fun setBandType(index: Int, type: Int) {
        if (index !in 0 until n) return
        val arr = _bandTypes.value.copyOf()
        arr[index] = type.coerceIn(0, 2)
        _bandTypes.value = arr
        prefs.edit().putInt("type24_$index", arr[index]).apply()
        _isDirty.value = true
        if (_enabled.value) applyToService()
    }

    fun applyPreset(preset: FactoryPreset) {
        setBandsGains(preset.gains.copyOf(), fromUser = true)
    }

    fun reset() {
        _preamp.value = 0f
        prefs.edit()
            .putFloat("preampDb", 0f)
            .apply()
        setBandsGains(FloatArray(n) { 0f })
    }

    fun saveCustomProfile(name: String) {
        viewModelScope.launch {
            val profile = SavedEQProfile(
                id = "custom_${System.currentTimeMillis()}",
                name = name,
                deviceModel = "Equalizer",
                bands = allBands(),
                preamp = _preamp.value.toDouble(),
                isCustom = true,
                isActive = true,
                // Save the active effects + their levels alongside the EQ curve.
                effects = iad1tya.echo.music.eq.data.SoundEffectsSnapshot.capture(context),
            )
            eqProfileRepository.saveProfile(profile)
            eqProfileRepository.setActiveProfile(profile.id)
            _isDirty.value = false
        }
    }

    /**
     * Apply a saved profile: restore its EQ bands + preamp AND its sound-effects snapshot.
     *
     * Mode is detected by band count: exactly [EqConstants.BAND_COUNT] (24) bands → graphic profile;
     * anything else (5–8 free bands) → a parametric profile. Either branch ends in the matching mode +
     * an apply so the curve is audible, and loading the other kind switches the mode accordingly.
     */
    fun applySavedProfile(profile: SavedEQProfile) {
        if (profile.bands.size != EqConstants.BAND_COUNT) {
            // Parametric profile: restore the 5–8 free bands, switch to PARAMETRIC, apply. The Auto-EQ
            // correction stage (if any) is left in place — the loaded manual curve stacks on top of it.
            val bands = profile.bands
                .map { it.copy(enabled = true) }
                .let { list ->
                    when {
                        list.size < PeqConstants.MIN_BANDS ->
                            list + PeqConstants.defaultBands().drop(list.size)
                        list.size > PeqConstants.MAX_BANDS -> list.take(PeqConstants.MAX_BANDS)
                        else -> list
                    }
                }
            _peqBands.value = bands
            _preamp.value = profile.preamp.toFloat().coerceIn(EqConstants.PREAMP_MIN, EqConstants.PREAMP_MAX)
            _enabled.value = true
            _eqMode.value = EqMode.PARAMETRIC
            prefs.edit()
                .putFloat("preampDb", _preamp.value)
                .putBoolean("enabled", true)
                .putString("eq_mode", EqMode.PARAMETRIC.name)
                .apply()
            persistPeqBands()
            _isDirty.value = true
            applyToService()
        } else {
            // Graphic profile: positional 24-band load + ensure GRAPHIC mode (switches back from PEQ).
            _eqMode.value = EqMode.GRAPHIC
            prefs.edit().putString("eq_mode", EqMode.GRAPHIC.name).apply()
            val gains = FloatArray(n) { i -> profile.bands.getOrNull(i)?.gain?.toFloat() ?: 0f }
            applyProfileBatch(gains, profile.preamp.toFloat())
        }
        viewModelScope.launch {
            eqProfileRepository.setActiveProfile(profile.id)
            iad1tya.echo.music.eq.data.SoundEffectsSnapshot.apply(context, profile.effects)
        }
    }

    /** Write all custom profiles (EQ + effects) as JSON to a file the user picked (export). */
    fun exportProfiles(uri: android.net.Uri) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            runCatching {
                val list = eqProfileRepository.getAllProfiles().filter { it.isCustom }
                val text = kotlinx.serialization.json.Json { prettyPrint = true }
                    .encodeToString(kotlinx.serialization.builtins.ListSerializer(SavedEQProfile.serializer()), list)
                context.contentResolver.openOutputStream(uri)?.use { it.write(text.toByteArray()) }
            }
        }
    }

    /** Import profiles from a previously-exported JSON file. */
    fun importProfiles(uri: android.net.Uri) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            runCatching {
                val text = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                    ?: return@launch
                val list = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
                    .decodeFromString(kotlinx.serialization.builtins.ListSerializer(SavedEQProfile.serializer()), text)
                list.forEach { p ->
                    eqProfileRepository.saveProfile(p.copy(id = "custom_${System.currentTimeMillis()}_${p.name.hashCode()}", isActive = false, isCustom = true))
                }
            }
        }
    }

    fun deleteProfiles(ids: List<String>) {
        viewModelScope.launch { ids.forEach { eqProfileRepository.deleteProfile(it) } }
    }

    private fun applyToService() {
        viewModelScope.launch {
            val profile = SavedEQProfile(
                id = "echo_tuning",
                name = "JR Tuning",
                deviceModel = "Equalizer",
                bands = allBands(),
                // Auto-EQ runs as a separate cascaded correction stage the manual EQ stacks on top of.
                autoBands = if (_autoEqActive.value) _autoEqBands.value else emptyList(),
                preamp = _preamp.value.toDouble(),
                isCustom = false,
                isActive = true,
            )
            eqProfileRepository.setUnsavedProfile(profile)
            equalizerService.applyProfile(profile)
        }
    }

    /** Current UI state as a live EQ profile (not yet persisted). */
    private fun liveProfile(): SavedEQProfile = SavedEQProfile(
        id = "echo_tuning",
        name = "JR Tuning",
        deviceModel = "Equalizer",
        bands = allBands(),
        // Auto-EQ runs as a separate cascaded correction stage the manual EQ stacks on top of.
        autoBands = if (_autoEqActive.value) _autoEqBands.value else emptyList(),
        preamp = _preamp.value.toDouble(),
        isCustom = false,
        isActive = true,
    )

    /**
     * Live band drag: update the DSP coefficients in place (no disk writes, no profile save, no
     * player re-seek) so the change is heard in real time without stutter. Persist via [commit].
     */
    fun setBandGainLive(index: Int, gainDb: Float) {
        if (index !in 0 until n) return
        val v = gainDb.coerceIn(EqConstants.GAIN_MIN, EqConstants.GAIN_MAX)
        val arr = _bandGains.value.copyOf()
        arr[index] = v
        _bandGains.value = arr
        _isDirty.value = true
        if (_enabled.value) equalizerService.applyProfile(liveProfile())
    }

    /** Live preamp drag (see [setBandGainLive]). */
    fun setPreampLive(db: Float) {
        val v = db.coerceIn(EqConstants.PREAMP_MIN, EqConstants.PREAMP_MAX)
        _preamp.value = v
        _isDirty.value = true
        if (_enabled.value) equalizerService.applyProfile(liveProfile())
    }

    /**
     * The full filter set sent to the DSP. Branches on the active mode — both produce
     * [ParametricEQBand]s consumed by the SAME engine path (CustomEqualizerAudioProcessor.createFilters).
     */
    private fun allBands(): List<ParametricEQBand> = when (_eqMode.value) {
        EqMode.GRAPHIC -> buildEqBands(_bandGains.value, _bandTypes.value)
        EqMode.PARAMETRIC -> _peqBands.value.filter { it.enabled }
    }

    /** Persist the current tuning once — call on slider release (onValueChangeFinished). */
    fun commit() {
        val editor = prefs.edit()
        _bandGains.value.forEachIndexed { i, f -> editor.putFloat("band24_$i", f) }
        _bandTypes.value.forEachIndexed { i, t -> editor.putInt("type24_$i", t) }
        editor.putFloat("preampDb", _preamp.value)
        editor.apply()
        // PEQ curve is persisted independently of the graphic bands so neither overwrites the other.
        persistPeqBands()
        if (_enabled.value) viewModelScope.launch {
            val p = liveProfile()
            eqProfileRepository.saveProfile(p)
            eqProfileRepository.setActiveProfile(p.id)
        }
    }
}
