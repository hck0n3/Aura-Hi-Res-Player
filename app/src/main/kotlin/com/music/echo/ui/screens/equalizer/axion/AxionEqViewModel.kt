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
        prefs.edit().putFloat("preampDb", 0f).apply()
        setBandsGains(FloatArray(n) { 0f })
    }

    fun saveCustomProfile(name: String) {
        viewModelScope.launch {
            val profile = SavedEQProfile(
                id = "custom_${System.currentTimeMillis()}",
                name = name,
                deviceModel = "Equalizer",
                bands = buildEqBands(_bandGains.value, _bandTypes.value),
                preamp = _preamp.value.toDouble(),
                isCustom = true,
                isActive = true,
            )
            eqProfileRepository.saveProfile(profile)
            eqProfileRepository.setActiveProfile(profile.id)
            _isDirty.value = false
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
                bands = buildEqBands(_bandGains.value, _bandTypes.value),
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
        bands = buildEqBands(_bandGains.value, _bandTypes.value),
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
