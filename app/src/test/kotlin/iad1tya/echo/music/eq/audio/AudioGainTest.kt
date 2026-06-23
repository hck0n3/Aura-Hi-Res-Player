package iad1tya.echo.music.eq.audio

import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.abs

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

    // ── eqMakeupDb (cancels EQ auto-headroom so volume doesn't drop) ──

    @Test fun eqMakeupZeroWhenNoBoost() {
        // no positive band → no auto-attenuation → nothing to compensate
        assertEquals(0.0, eqMakeupDb(0.0, listOf(-3.0, -6.0, 0.0)), 1e-9)
    }

    @Test fun eqMakeupCompensatesBoostPlusSafety() {
        // max boost +6 → headroom pulled -7 below preamp → makeup +7
        assertEquals(7.0, eqMakeupDb(0.0, listOf(6.0, 2.0, -3.0)), 1e-9)
    }

    @Test fun eqMakeupIgnoresUserPreamp() {
        // user preamp -2 is intentional; only the auto part (+4 boost +1) is compensated → 5
        assertEquals(5.0, eqMakeupDb(-2.0, listOf(4.0)), 1e-9)
    }

    @Test fun eqMakeupIsCapped() {
        // +18 boost → +19 → capped at 12
        assertEquals(12.0, eqMakeupDb(0.0, listOf(18.0)), 1e-9)
    }

    // ── loudnessMakeupDb (target loudness, boost-only part) ──

    @Test fun makeupDisabledIsZero() {
        assertEquals(0.0, loudnessMakeupDb(-6.0, enabled = false), 1e-9)
    }

    @Test fun makeupNullLoudnessIsZero() {
        assertEquals(0.0, loudnessMakeupDb(null, enabled = true), 1e-9)
    }

    @Test fun makeupLoudTrackIsZero() {
        // loudnessDb +6 (above reference) → attenuation handles it, no boost here
        assertEquals(0.0, loudnessMakeupDb(6.0, enabled = true), 1e-9)
    }

    @Test fun makeupQuietTrackIsBoosted() {
        // loudnessDb -6 (below reference) → +6 dB makeup
        assertEquals(6.0, loudnessMakeupDb(-6.0, enabled = true), 1e-9)
    }

    @Test fun makeupIsCappedAtMaxBoost() {
        // loudnessDb -20 → would be +20 dB → capped at the default +12 dB (the v0.0.9 configuration).
        assertEquals(12.0, loudnessMakeupDb(-20.0, enabled = true), 1e-9)
    }

    // ── dbToLinear ──

    @Test fun dbToLinearUnityAtZero() {
        assertEquals(1.0f, dbToLinear(0.0), 1e-6f)
    }

    @Test fun dbToLinearMinusSixIsHalfish() {
        assertEquals(0.5012f, dbToLinear(-6.0), 1e-3f)
    }

    // ── softLimit (bounded) ──

    @Test fun softLimitPassthroughBelowKnee() {
        assertEquals(0.5f, softLimit(0.5f), 1e-6f)
        assertEquals(-0.5f, softLimit(-0.5f), 1e-6f)
    }

    @Test fun softLimitNeverExceedsCeiling() {
        // large input → output magnitude clamped to the ceiling, never above
        assertEquals(0.95f, softLimit(5.0f), 1e-3f)
        assertEquals(-0.95f, softLimit(-5.0f), 1e-3f)
    }

    @Test fun softLimitIsContinuousAtKnee() {
        // at exactly the knee the bounded curve still equals the linear value
        assertEquals(0.80f, softLimit(0.80f), 1e-4f)
    }

    @Test fun softLimitBoundedForAnyInput() {
        var x = -4.0f
        while (x <= 4.0f) {
            assert(abs(softLimit(x)) <= 0.95f + 1e-4f) { "softLimit($x) exceeded ceiling" }
            x += 0.05f
        }
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
