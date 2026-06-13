# DSP Phase 8A — 24-Band Graphic EQ + Preamp + Factory Presets Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the Android equalizer match the desktop "JR DSP Pro" 24-band graphic EQ exactly — same ISO ⅓-octave frequencies, Q, per-band filter type, pre-amp, and the exact factory preset curves — wired through the already-working biquad audio chain.

**Architecture:** The audio path already works: `AxionEqViewModel` builds a `SavedEQProfile` → `EQProfileRepository.setActiveProfile` → observed in `MusicService:557` → `EqualizerService.applyProfile` → `CustomEqualizerAudioProcessor` rebuilds `BiquadFilter`s (RBJ peaking/low-shelf/high-shelf, identical math to the desktop's miniaudio `ma_peak2/loshelf2/hishelf2`). This phase widens the model from 10 bands to 24, stores gains in dB, adds pre-amp and per-band type, ports the exact factory presets, and rebuilds the EQ screen UI to the JR DSP Pro layout.

**Tech Stack:** Kotlin, Jetpack Compose, Hilt, Media3 `AudioProcessor`, kotlinx.serialization, JUnit.

**Reference (source of truth):** `C:\Users\Hck0n3\Desktop\2\src\ui\renderer\js\native_dsp_ui.js` (presets, band model) and `native\src\jr_audio_engine.cpp` (signal math).

---

## Canonical constants (from desktop, verified)

- **24 frequencies (Hz):** `20, 31, 50, 80, 100, 160, 200, 315, 440, 500, 800, 1000, 1600, 2000, 2500, 3150, 4000, 5000, 6300, 8000, 10000, 12500, 16000, 20000`
- **Q:** `4.318` for every band (= 1/(2^(1/6) − 2^(−1/6)), ISO ⅓-oct)
- **Band type:** `0` = Peaking, `1` = Low shelf, `2` = High shelf (default all `0`)
- **Gain range:** −18…+18 dB. **Pre-amp range:** −20…+6 dB.
- **Factory presets (24 dB gains each):**
  - `flat`: all 0
  - `bass_boost` ("Bass Boost"): `6,5,4,3,2,1,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0`
  - `treble_boost` ("Treble+"): `0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,1,1,2,2,3,4,5,4,3`
  - `v_shape` ("V-Shape"): `5,4,3,2,1,0,-1,-2,-2,-2,-2,-1,0,0,0,0,0,0,1,1,2,3,4,3`
  - `vocal` ("Vocal"): `-1,-1,0,0,1,1,1,2,2,3,3,4,4,3,3,2,2,2,1,1,0,0,0,0`
  - `classical` ("Classical"): `3,3,2,1,0,0,0,0,0,0,0,0,0,0,1,1,1,1,2,2,2,2,1,0`
  - `jazz` ("Jazz"): `2,2,2,1,1,0,0,-1,-1,-1,-1,0,0,1,1,2,2,2,1,1,1,1,0,-1`

---

## File Structure

- **Create:** `app/src/main/kotlin/com/music/echo/eq/data/EqConstants.kt` — the 24 freqs, Q, `FactoryPreset` enum + curves. One responsibility: canonical EQ data.
- **Create:** `app/src/test/kotlin/com/music/echo/eq/EqConstantsTest.kt` — unit tests for constants/presets.
- **Modify:** `app/src/main/kotlin/com/music/echo/eq/data/ParametricEQ.kt:23` — `MAX_BANDS = 20` → `24`.
- **Modify:** `app/src/main/kotlin/com/music/echo/ui/screens/equalizer/axion/AxionEqViewModel.kt` — 24 bands, dB gains, preamp state, band-type state, factory presets, profile building.
- **Create:** `app/src/test/kotlin/com/music/echo/eq/AxionEqProfileTest.kt` — unit tests for VM→profile mapping (extract pure mapping into a testable function).
- **Modify:** `app/src/main/kotlin/com/music/echo/ui/screens/equalizer/axion/AxionEqScreen.kt` — rebuild UI: 24-band sliders, preamp slider, preset chips, EQ enable, response curve. (Build + manual verify.)
- **Modify:** `app/src/main/kotlin/com/music/echo/ui/screens/equalizer/axion/CircularEqControl.kt` — only if the band UI component needs the new band count/units.

---

## Task 1: Canonical EQ constants + factory presets

**Files:**
- Create: `app/src/main/kotlin/com/music/echo/eq/data/EqConstants.kt`
- Test: `app/src/test/kotlin/com/music/echo/eq/EqConstantsTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package iad1tya.echo.music.eq

import iad1tya.echo.music.eq.data.EqConstants
import iad1tya.echo.music.eq.data.FactoryPreset
import org.junit.Assert.assertEquals
import org.junit.Test

class EqConstantsTest {
    @Test fun has24FrequenciesAndQ() {
        assertEquals(24, EqConstants.FREQUENCIES.size)
        assertEquals(20.0, EqConstants.FREQUENCIES.first(), 0.001)
        assertEquals(20000.0, EqConstants.FREQUENCIES.last(), 0.001)
        assertEquals(440.0, EqConstants.FREQUENCIES[8], 0.001)
        assertEquals(4.318, EqConstants.Q, 0.0001)
    }

    @Test fun everyPresetHas24Gains() {
        FactoryPreset.entries.forEach { preset ->
            assertEquals("${preset.name} must have 24 gains", 24, preset.gains.size)
        }
    }

    @Test fun bassBoostCurveMatchesDesktop() {
        assertEquals(
            listOf(6,5,4,3,2,1,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0).map { it.toFloat() },
            FactoryPreset.BASS_BOOST.gains.toList()
        )
    }

    @Test fun flatIsAllZero() {
        assertEquals(List(24){0f}, FactoryPreset.FLAT.gains.toList())
    }
}
```

- [ ] **Step 2: Run test, verify it fails**

Run: `./gradlew :app:testUniversalFossDebugUnitTest --tests "iad1tya.echo.music.eq.EqConstantsTest"`
Expected: FAIL — `EqConstants` / `FactoryPreset` unresolved.

- [ ] **Step 3: Implement `EqConstants.kt`**

```kotlin
package iad1tya.echo.music.eq.data

/** Canonical 24-band ISO 1/3-octave EQ data — matches desktop JR DSP Pro exactly. */
object EqConstants {
    val FREQUENCIES = doubleArrayOf(
        20.0, 31.0, 50.0, 80.0, 100.0, 160.0, 200.0, 315.0,
        440.0, 500.0, 800.0, 1000.0, 1600.0, 2000.0, 2500.0, 3150.0,
        4000.0, 5000.0, 6300.0, 8000.0, 10000.0, 12500.0, 16000.0, 20000.0
    )
    const val Q = 4.318
    const val BAND_COUNT = 24
    const val GAIN_MIN = -18f
    const val GAIN_MAX = 18f
    const val PREAMP_MIN = -20f
    const val PREAMP_MAX = 6f
}

/** Band filter shape — index matches desktop bandType (0=Peak,1=LowShelf,2=HighShelf). */
enum class EqBandType(val code: Int) { PEAK(0), LOW_SHELF(1), HIGH_SHELF(2) }

/** Factory EQ presets — gain curves copied verbatim from desktop native_dsp_ui.js. */
enum class FactoryPreset(val displayName: String, val gains: FloatArray) {
    FLAT("Flat", FloatArray(24) { 0f }),
    BASS_BOOST("Bass Boost", floatArrayOf(6f,5f,4f,3f,2f,1f,0f,0f,0f,0f,0f,0f,0f,0f,0f,0f,0f,0f,0f,0f,0f,0f,0f,0f)),
    TREBLE_BOOST("Treble+", floatArrayOf(0f,0f,0f,0f,0f,0f,0f,0f,0f,0f,0f,0f,0f,0f,0f,1f,1f,2f,2f,3f,4f,5f,4f,3f)),
    V_SHAPE("V-Shape", floatArrayOf(5f,4f,3f,2f,1f,0f,-1f,-2f,-2f,-2f,-2f,-1f,0f,0f,0f,0f,0f,0f,1f,1f,2f,3f,4f,3f)),
    VOCAL("Vocal", floatArrayOf(-1f,-1f,0f,0f,1f,1f,1f,2f,2f,3f,3f,4f,4f,3f,3f,2f,2f,2f,1f,1f,0f,0f,0f,0f)),
    CLASSICAL("Classical", floatArrayOf(3f,3f,2f,1f,0f,0f,0f,0f,0f,0f,0f,0f,0f,0f,1f,1f,1f,1f,2f,2f,2f,2f,1f,0f)),
    JAZZ("Jazz", floatArrayOf(2f,2f,2f,1f,1f,0f,0f,-1f,-1f,-1f,-1f,0f,0f,1f,1f,2f,2f,2f,1f,1f,1f,1f,0f,-1f)),
}
```

- [ ] **Step 4: Run test, verify it passes**

Run: `./gradlew :app:testUniversalFossDebugUnitTest --tests "iad1tya.echo.music.eq.EqConstantsTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/com/music/echo/eq/data/EqConstants.kt app/src/test/kotlin/com/music/echo/eq/EqConstantsTest.kt
git commit -m "feat(eq): canonical 24-band ISO constants + desktop factory presets"
```

---

## Task 2: Allow 24 bands in the model

**Files:**
- Modify: `app/src/main/kotlin/com/music/echo/eq/data/ParametricEQ.kt:23`

- [ ] **Step 1: Change MAX_BANDS**

In `ParametricEQ.kt`, change `const val MAX_BANDS = 20` to `const val MAX_BANDS = 24`.

- [ ] **Step 2: Verify the processor has no other 20 cap**

Run: `grep -rn "MAX_BANDS\|take(20\|\b20\b" app/src/main/kotlin/com/music/echo/eq/`
Expected: no hard limit in `CustomEqualizerAudioProcessor` (it filters by `enabled` + `frequency < sampleRate/2`). If a `.take(20)` exists, raise to 24.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/kotlin/com/music/echo/eq/data/ParametricEQ.kt
git commit -m "feat(eq): raise MAX_BANDS to 24 for ISO 1/3-oct EQ"
```

---

## Task 3: Pure VM→profile mapping (testable) + 24-band/preamp/preset state

**Files:**
- Modify: `app/src/main/kotlin/com/music/echo/ui/screens/equalizer/axion/AxionEqViewModel.kt`
- Test: `app/src/test/kotlin/com/music/echo/eq/AxionEqProfileTest.kt`

Extract a pure mapping function so it can be unit-tested without Android.

- [ ] **Step 1: Write the failing test**

```kotlin
package iad1tya.echo.music.eq

import iad1tya.echo.music.eq.data.EqConstants
import iad1tya.echo.music.ui.screens.equalizer.axion.buildEqBands
import org.junit.Assert.assertEquals
import org.junit.Test

class AxionEqProfileTest {
    @Test fun buildsOneBandPerFrequencyWithDbGainsDirect() {
        val gains = FloatArray(24) { it.toFloat() - 6f } // -6..+17 dB
        val types = IntArray(24) { 0 }
        val bands = buildEqBands(gains, types)
        assertEquals(24, bands.size)
        assertEquals(EqConstants.FREQUENCIES[0], bands[0].frequency, 0.001)
        // gain stored directly in dB (NOT divided by 50)
        assertEquals(-6.0, bands[0].gain, 0.001)
        assertEquals(17.0, bands[23].gain, 0.001)
        assertEquals(EqConstants.Q, bands[0].q, 0.0001)
    }
}
```

- [ ] **Step 2: Run test, verify it fails**

Run: `./gradlew :app:testUniversalFossDebugUnitTest --tests "iad1tya.echo.music.eq.AxionEqProfileTest"`
Expected: FAIL — `buildEqBands` unresolved.

- [ ] **Step 3: Add the pure mapping + rewrite VM state to 24 bands/preamp/presets**

Add top-level function in `AxionEqViewModel.kt` (file scope, outside the class):

```kotlin
import iad1tya.echo.music.eq.data.EqBandType
import iad1tya.echo.music.eq.data.EqConstants
import iad1tya.echo.music.eq.data.FactoryPreset

/** Pure mapping: per-band dB gains + types → ParametricEQ bands (gain stored directly in dB). */
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
            enabled = true
        )
    }
```

Then update the VM: replace `bandFrequencies`/10-band arrays with 24-band state, add `preamp` and `bandTypes` state, and use `buildEqBands` in both `applyToService()` and `saveCustomProfile()`. Replace the body of `applyToService()` with:

```kotlin
private fun applyToService() {
    viewModelScope.launch {
        val profile = SavedEQProfile(
            id = "echo_tuning",
            name = "JR Tuning",
            deviceModel = "Equalizer",
            bands = buildEqBands(_bandGains.value, _bandTypes.value),
            preamp = _preamp.value.toDouble(),
            isCustom = false,
            isActive = true
        )
        eqProfileRepository.saveProfile(profile)
        eqProfileRepository.setActiveProfile(profile.id)
        equalizerService.applyProfile(profile)
    }
}
```

New/changed state fields (replace the 10-band declarations):

```kotlin
private val _bandGains = MutableStateFlow(FloatArray(EqConstants.BAND_COUNT) { prefs.getFloat("band_$it", 0f) })
val bandGains = _bandGains.asStateFlow()

private val _bandTypes = MutableStateFlow(IntArray(EqConstants.BAND_COUNT) { prefs.getInt("type_$it", 0) })
val bandTypes = _bandTypes.asStateFlow()

private val _preamp = MutableStateFlow(prefs.getFloat("preamp", 0f))
val preamp = _preamp.asStateFlow()
```

Add setters + preset application:

```kotlin
fun setPreamp(db: Float) {
    val v = db.coerceIn(EqConstants.PREAMP_MIN, EqConstants.PREAMP_MAX)
    _preamp.value = v
    prefs.edit().putFloat("preamp", v).apply()
    _isDirty.value = true
    if (_enabled.value) applyToService()
}

fun setBandType(index: Int, type: Int) {
    val arr = _bandTypes.value.copyOf(); arr[index] = type.coerceIn(0, 2)
    _bandTypes.value = arr
    prefs.edit().putInt("type_$index", arr[index]).apply()
    _isDirty.value = true
    if (_enabled.value) applyToService()
}

fun applyPreset(preset: FactoryPreset) {
    setBandsGains(preset.gains.copyOf(), fromUser = true)
}
```

Update `setBandGain` clamp to `EqConstants.GAIN_MIN..GAIN_MAX`. Update `reset()` to `FloatArray(EqConstants.BAND_COUNT){0f}` and reset preamp to 0. Update `saveCustomProfile` to use `buildEqBands(_bandGains.value, _bandTypes.value)` and `preamp = _preamp.value.toDouble()`. Remove the old `bandFrequencies` array and `/50.0` scaling.

- [ ] **Step 4: Run test, verify it passes**

Run: `./gradlew :app:testUniversalFossDebugUnitTest --tests "iad1tya.echo.music.eq.AxionEqProfileTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/com/music/echo/ui/screens/equalizer/axion/AxionEqViewModel.kt app/src/test/kotlin/com/music/echo/eq/AxionEqProfileTest.kt
git commit -m "feat(eq): 24-band EQ + preamp + factory presets in view model (dB gains)"
```

---

## Task 4: Rebuild EQ screen UI (JR DSP Pro style)

**Files:**
- Modify: `app/src/main/kotlin/com/music/echo/ui/screens/equalizer/axion/AxionEqScreen.kt`
- Modify (if needed): `app/src/main/kotlin/com/music/echo/ui/screens/equalizer/axion/CircularEqControl.kt`

This is UI work (verified by build + on-device look, not unit tests). First Read the current `AxionEqScreen.kt` fully to preserve its scaffold (TopAppBar, enable switch, profile save/delete dialogs).

- [ ] **Step 1: Read current screen**

Run: open `AxionEqScreen.kt`; note how it reads `bandGains`, calls `setBandGain`, renders the enable switch and profiles.

- [ ] **Step 2: Replace EQ body** with: a horizontally scrollable row of 24 vertical sliders (one per `EqConstants.FREQUENCIES`, label = compact Hz e.g. `20, 31, 50, 80, 100, 160, 200, 315, 440, 500, 800, 1k, 1.6k, 2k, 2.5k, 3.15k, 4k, 5k, 6.3k, 8k, 10k, 12.5k, 16k, 20k`), each bound to `vm.bandGains[i]` via `vm.setBandGain(i, value)` over range `-18f..18f`; a preamp slider (`-20f..6f`) bound to `vm.preamp`/`vm.setPreamp`; a row of preset chips from `FactoryPreset.entries` calling `vm.applyPreset(it)`; the existing EQ enable switch; a "Flat"/reset button calling `vm.reset()`. Keep existing profile save/delete UI.

- [ ] **Step 3: Build**

Run: `./gradlew :app:assembleUniversalFossDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Manual verify** — open Settings → Player & audio → Equalizer: 24 sliders + preamp + preset chips visible; toggling a preset moves sliders; enabling EQ + playing audio changes sound.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/com/music/echo/ui/screens/equalizer/axion/
git commit -m "feat(eq): rebuild EQ screen as 24-band graphic EQ with preamp + presets"
```

---

## Task 5: Full build + lint gate

- [ ] **Step 1:** `./gradlew :app:assembleUniversalFossDebug` → BUILD SUCCESSFUL
- [ ] **Step 2:** `./gradlew :app:testUniversalFossDebugUnitTest` → all green
- [ ] **Step 3:** `./gradlew :app:lintUniversalFossDebug` → no new errors
- [ ] **Step 4: Commit** any lint fixes: `git commit -m "chore(eq): phase 8A build/lint gate"`

---

## Self-Review

- **Spec coverage:** 8A items — 24 ISO bands ✓ (Task 1/3), Q 4.318 ✓ (Task 1), per-band type ✓ (Task 3), preamp −20..+6 ✓ (Task 3/4), factory presets exact ✓ (Task 1), global EQ enable ✓ (existing, kept Task 4), curve UI — band sliders ✓ (Task 4; response-curve canvas deferred to 8E visualizer, acceptable). Real biquad chain ✓ (existing engine, unchanged).
- **Placeholder scan:** none — preset arrays and code are literal.
- **Type consistency:** `buildEqBands(FloatArray, IntArray)` used identically in test + VM; `EqConstants.BAND_COUNT=24`; `FactoryPreset.gains: FloatArray`; profile uses existing `SavedEQProfile`/`ParametricEQBand` fields. Gain stored in dB directly (drops the old `/50.0`); ensure no other reader assumes the old scaling (grep `/ 50` in axion package during Task 3).
- **Note:** verify the unit-test source set/task name for this project (`testUniversalFossDebugUnitTest` vs `testDebugUnitTest`) on first run; adjust commands if the variant differs.
