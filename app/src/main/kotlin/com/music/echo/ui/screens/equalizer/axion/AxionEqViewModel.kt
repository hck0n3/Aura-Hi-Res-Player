package iad1tya.echo.music.ui.screens.equalizer.axion

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import iad1tya.echo.music.eq.EqualizerService
import iad1tya.echo.music.eq.data.EQProfileRepository
import iad1tya.echo.music.eq.data.EqConstants
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

    // Auto-EQ vs 24-band graphic EQ are mutually exclusive (both write the SAME _bandGains array).
    // When Auto-EQ is applied this is true and the graphic EQ is greyed/locked; any manual graphic
    // edit clears it (last-touched-wins).
    private val _autoEqActive = MutableStateFlow(prefs.getBoolean("autoeq_active", false))
    val autoEqActive = _autoEqActive.asStateFlow()

    /** Leave the Auto-EQ lock and return to manual editing. The bands keep the applied Auto-EQ curve as the
     *  starting point (so the user can fine-tune from it). Wired to the "Cambiar a manual" banner button so
     *  the unlock path never depends on the (now-greyed) sliders/presets/reset. */
    fun unlockGraphic() = setAutoEqActive(false)

    private fun setAutoEqActive(active: Boolean) {
        _autoEqActive.value = active
        prefs.edit().putBoolean("autoeq_active", active).apply()
    }

    // Band gains in dB (new "band24_" keys; legacy 10-band "band_" keys are intentionally ignored).
    private val _bandGains = MutableStateFlow(FloatArray(n) { prefs.getFloat("band24_$it", 0f) })
    val bandGains = _bandGains.asStateFlow()

    // Per-band filter type: 0=Peak, 1=LowShelf, 2=HighShelf.
    private val _bandTypes = MutableStateFlow(IntArray(n) { prefs.getInt("type24_$it", 0) })
    val bandTypes = _bandTypes.asStateFlow()

    private val _preamp = MutableStateFlow(prefs.getFloat("preampDb", 0f))
    val preamp = _preamp.asStateFlow()

    private val _isDirty = MutableStateFlow(false)
    val isDirty = _isDirty.asStateFlow()

    val customProfiles = eqProfileRepository.profiles.map { profiles ->
        profiles.filter { it.isCustom && it.id != "echo_tuning" }
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    init {
        if (_enabled.value) applyToService()
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
        setAutoEqActive(false)
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
        setAutoEqActive(false)
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
        _bandGains.value = arr
        _preamp.value = preampDb.coerceIn(EqConstants.PREAMP_MIN, EqConstants.PREAMP_MAX)
        _enabled.value = true
        prefs.edit().apply {
            arr.forEachIndexed { i, f -> putFloat("band24_$i", f) }
            putFloat("preampDb", _preamp.value)
            putBoolean("enabled", true)
        }.apply()
        _isDirty.value = true
        // Only a true Auto-EQ headphone profile locks the graphic EQ (last-touched-wins). Loading a
        // user's saved custom profile keeps it manual (isAutoEq=false → clears the lock). Set AFTER
        // the band writes so it reflects the final intent.
        setAutoEqActive(isAutoEq)
        applyToService()
    }

    fun setBandType(index: Int, type: Int) {
        setAutoEqActive(false)
        if (index !in 0 until n) return
        val arr = _bandTypes.value.copyOf()
        arr[index] = type.coerceIn(0, 2)
        _bandTypes.value = arr
        prefs.edit().putInt("type24_$index", arr[index]).apply()
        _isDirty.value = true
        if (_enabled.value) applyToService()
    }

    fun applyPreset(preset: FactoryPreset) {
        setAutoEqActive(false)
        setBandsGains(preset.gains.copyOf(), fromUser = true)
    }

    fun reset() {
        setAutoEqActive(false)
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

    /** Apply a saved profile: restore its EQ bands + preamp AND its sound-effects snapshot. */
    fun applySavedProfile(profile: SavedEQProfile) {
        val gains = FloatArray(n) { i -> profile.bands.getOrNull(i)?.gain?.toFloat() ?: 0f }
        applyProfileBatch(gains, profile.preamp.toFloat())
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
                preamp = _preamp.value.toDouble(),
                isCustom = false,
                isActive = true,
            )
            eqProfileRepository.saveProfile(profile)
            eqProfileRepository.setActiveProfile(profile.id)
            equalizerService.applyProfile(profile)
        }
    }

    /** Current UI state as a live EQ profile (not yet persisted). */
    private fun liveProfile(): SavedEQProfile = SavedEQProfile(
        id = "echo_tuning",
        name = "JR Tuning",
        deviceModel = "Equalizer",
        bands = allBands(),
        preamp = _preamp.value.toDouble(),
        isCustom = false,
        isActive = true,
    )

    /**
     * Live band drag: update the DSP coefficients in place (no disk writes, no profile save, no
     * player re-seek) so the change is heard in real time without stutter. Persist via [commit].
     */
    fun setBandGainLive(index: Int, gainDb: Float) {
        setAutoEqActive(false)
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

    /** The 24 graphic bands — the full filter set sent to the DSP. */
    private fun allBands(): List<ParametricEQBand> =
        buildEqBands(_bandGains.value, _bandTypes.value)

    /** Persist the current tuning once — call on slider release (onValueChangeFinished). */
    fun commit() {
        val editor = prefs.edit()
        _bandGains.value.forEachIndexed { i, f -> editor.putFloat("band24_$i", f) }
        _bandTypes.value.forEachIndexed { i, t -> editor.putInt("type24_$i", t) }
        editor.putFloat("preampDb", _preamp.value)
        editor.apply()
        if (_enabled.value) viewModelScope.launch {
            val p = liveProfile()
            eqProfileRepository.saveProfile(p)
            eqProfileRepository.setActiveProfile(p.id)
        }
    }
}
