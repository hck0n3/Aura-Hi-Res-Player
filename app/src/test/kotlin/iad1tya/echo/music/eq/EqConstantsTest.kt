package iad1tya.echo.music.eq

import iad1tya.echo.music.eq.data.EqBandType
import iad1tya.echo.music.eq.data.EqConstants
import iad1tya.echo.music.eq.data.FactoryPreset
import org.junit.Assert.assertEquals
import org.junit.Test

class EqConstantsTest {

    @Test
    fun has24FrequenciesAndQ() {
        assertEquals(24, EqConstants.FREQUENCIES.size)
        assertEquals(24, EqConstants.FREQUENCY_LABELS.size)
        assertEquals(20.0, EqConstants.FREQUENCIES.first(), 0.001)
        assertEquals(20000.0, EqConstants.FREQUENCIES.last(), 0.001)
        // ISO 1/3-octave centers: index 8 = 315 Hz, index 11 = 1 kHz reference.
        assertEquals(315.0, EqConstants.FREQUENCIES[8], 0.001)
        assertEquals(1000.0, EqConstants.FREQUENCIES[11], 0.001)
        assertEquals(4.318, EqConstants.Q, 0.0001)
    }

    @Test
    fun everyPresetHas24Gains() {
        FactoryPreset.entries.forEach { preset ->
            assertEquals("${preset.name} must have 24 gains", 24, preset.gains.size)
        }
    }

    @Test
    fun bassBoostCurveMatchesDesktop() {
        assertEquals(
            listOf(6, 5, 4, 3, 2, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0).map { it.toFloat() },
            FactoryPreset.BASS_BOOST.gains.toList()
        )
    }

    @Test
    fun vocalCurveMatchesDesktop() {
        assertEquals(
            listOf(-1, -1, 0, 0, 1, 1, 1, 2, 2, 3, 3, 4, 4, 3, 3, 2, 2, 2, 1, 1, 0, 0, 0, 0).map { it.toFloat() },
            FactoryPreset.VOCAL.gains.toList()
        )
    }

    @Test
    fun flatIsAllZero() {
        assertEquals(List(24) { 0f }, FactoryPreset.FLAT.gains.toList())
    }

    @Test
    fun bandTypeFromCodeMapsCorrectly() {
        assertEquals(EqBandType.PEAK, EqBandType.fromCode(0))
        assertEquals(EqBandType.LOW_SHELF, EqBandType.fromCode(1))
        assertEquals(EqBandType.HIGH_SHELF, EqBandType.fromCode(2))
        assertEquals(EqBandType.PEAK, EqBandType.fromCode(99))
    }
}
