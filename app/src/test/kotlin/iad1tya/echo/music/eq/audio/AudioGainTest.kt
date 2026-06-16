package iad1tya.echo.music.eq.audio

import org.junit.Assert.assertEquals
import org.junit.Test

class AudioGainTest {

    // ── headroomPreampDb ──

    @Test fun headroomNoBoostKeepsUserPreamp() {
        assertEquals(0.0, headroomPreampDb(0.0, listOf(-3.0, -6.0, 0.0)), 1e-9)
    }

    @Test fun headroomSubtractsMaxPositiveGainPlusSafety() {
        // max boost +6 dB → -6 - 1 = -7
        assertEquals(-7.0, headroomPreampDb(0.0, listOf(6.0, 2.0, -3.0)), 1e-9)
    }

    @Test fun headroomKeepsUserPreampOnTop() {
        // user -2, max boost +4 → -2 - 4 - 1 = -7
        assertEquals(-7.0, headroomPreampDb(-2.0, listOf(4.0)), 1e-9)
    }

    @Test fun headroomEmptyBandsKeepsUserPreamp() {
        assertEquals(-1.5, headroomPreampDb(-1.5, emptyList()), 1e-9)
    }

    // ── normalizationMultiplier (attenuate-only) ──

    @Test fun normDisabledIsUnity() {
        assertEquals(1.0f, normalizationMultiplier(5.0, enabled = false), 1e-6f)
    }

    @Test fun normNullLoudnessIsUnity() {
        assertEquals(1.0f, normalizationMultiplier(null, enabled = true), 1e-6f)
    }

    @Test fun normQuietTrackIsNotBoosted() {
        // loudnessDb -6 (below reference) would boost → clamped to unity
        assertEquals(1.0f, normalizationMultiplier(-6.0, enabled = true), 1e-6f)
    }

    @Test fun normLoudTrackIsAttenuated() {
        // loudnessDb +6 → gain -6 dB → 10^(-0.3) ≈ 0.5012
        assertEquals(0.5012f, normalizationMultiplier(6.0, enabled = true), 1e-3f)
    }

    @Test fun normAttenuationIsFloored() {
        // loudnessDb +100 → clamped to -12 dB → 10^(-0.6) ≈ 0.2512
        assertEquals(0.2512f, normalizationMultiplier(100.0, enabled = true), 1e-3f)
    }

    // ── declipSample ──

    @Test fun declipContinuesUpwardRamp() {
        // 0.8 -> 0.9 (slope +0.1), 1 step into the clip → 1.0
        assertEquals(1.0f, declipSample(0.9f, 0.8f, 1), 1e-6f)
    }

    @Test fun declipIsBoundedToMaxOvershoot() {
        // steep slope, many steps → clamps to +1.35
        assertEquals(1.35f, declipSample(0.9f, 0.7f, 10), 1e-6f)
    }

    @Test fun declipFlatApproachStaysFlat() {
        assertEquals(0.98f, declipSample(0.98f, 0.98f, 3), 1e-6f)
    }
}
