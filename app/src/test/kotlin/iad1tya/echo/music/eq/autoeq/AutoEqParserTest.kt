package iad1tya.echo.music.eq.autoeq

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoEqParserTest {

    private val sample = """
        Preamp: -6.7 dB
        Filter 1: ON PK Fc 21 Hz Gain 5.5 dB Q 0.70
        Filter 2: ON PK Fc 105 Hz Gain -2.1 dB Q 1.41
        Filter 3: ON PK Fc 3000 Hz Gain 4.0 dB Q 2.00
    """.trimIndent()

    @Test fun parsesPreamp() {
        assertEquals(-6.7, AutoEqParser.parse(sample).preampDb, 1e-9)
    }

    @Test fun parsesAllBands() {
        val p = AutoEqParser.parse(sample)
        assertEquals(3, p.bands.size)
        assertEquals(21.0, p.bands[0].fc, 1e-9)
        assertEquals(5.5, p.bands[0].gainDb, 1e-9)
        assertEquals(0.70, p.bands[0].q, 1e-9)
        assertEquals(3000.0, p.bands[2].fc, 1e-9)
    }

    @Test fun ignoresGarbageLines() {
        val p = AutoEqParser.parse("nonsense\nFilter X: OFF\nPreamp: not a number")
        assertEquals(0.0, p.preampDb, 1e-9)
        assertTrue(p.bands.isEmpty())
    }

    // ── projection ──

    @Test fun projectionPutsGainNearBandCenter() {
        // a single +6 dB peak at 1000 Hz, Q 4.3 → the 1000 Hz graphic band gets ~the full gain
        val profile = AutoEqProfile(0.0, listOf(AutoEqBand(1000.0, 6.0, 4.318)))
        val freqs = doubleArrayOf(100.0, 1000.0, 10000.0)
        val gains = projectAutoEqToBands(profile, freqs)
        assertEquals(6.0f, gains[1], 0.01f)      // at center → full gain
        assertTrue(gains[0] < 0.5f)              // far below → almost nothing
        assertTrue(gains[2] < 0.5f)              // far above → almost nothing
    }

    @Test fun projectionClampsToRange() {
        val profile = AutoEqProfile(0.0, listOf(AutoEqBand(1000.0, 99.0, 4.318)))
        val gains = projectAutoEqToBands(profile, doubleArrayOf(1000.0))
        assertEquals(18.0f, gains[0], 1e-4f)
    }

    @Test fun emptyProfileIsFlat() {
        val gains = projectAutoEqToBands(AutoEqProfile(0.0, emptyList()), doubleArrayOf(100.0, 1000.0))
        assertEquals(0.0f, gains[0], 1e-6f)
        assertEquals(0.0f, gains[1], 1e-6f)
    }
}
