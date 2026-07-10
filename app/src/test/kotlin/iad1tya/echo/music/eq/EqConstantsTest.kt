package iad1tya.echo.music.eq

import iad1tya.echo.music.eq.data.EqBandType
import iad1tya.echo.music.eq.data.EqConstants
import iad1tya.echo.music.eq.data.FactoryPreset
import org.junit.Assert.assertEquals
import org.junit.Test

class EqConstantsTest {

    @Test
    fun hasTenFrequenciesAndQ() {
        assertEquals(EqConstants.BAND_COUNT, EqConstants.FREQUENCIES.size)
        assertEquals(EqConstants.BAND_COUNT, EqConstants.FREQUENCY_LABELS.size)
        assertEquals(10, EqConstants.BAND_COUNT)
        assertEquals(31.5, EqConstants.FREQUENCIES.first(), 0.001)
        assertEquals(16000.0, EqConstants.FREQUENCIES.last(), 0.001)
        // ISO octave centers: index 3 = 250 Hz, index 5 = 1 kHz reference.
        assertEquals(250.0, EqConstants.FREQUENCIES[3], 0.001)
        assertEquals(1000.0, EqConstants.FREQUENCIES[5], 0.001)
        // Musical octave Q for a 10-band graphic EQ.
        assertEquals(1.414, EqConstants.Q, 0.0001)
    }

    @Test
    fun everyPresetHasTenGains() {
        FactoryPreset.entries.forEach { preset ->
            assertEquals(
                "${preset.name} must have ${EqConstants.BAND_COUNT} gains",
                EqConstants.BAND_COUNT,
                preset.gains.size,
            )
        }
    }

    @Test
    fun subBassRumbleOnlyLiftsTheLowestBands() {
        assertEquals(
            listOf(6.0f, 3.0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f),
            FactoryPreset.SUB_BASS_RUMBLE.gains.toList()
        )
    }

    @Test
    fun vocalPresenceCurveMatchesSpec() {
        assertEquals(
            listOf(-1.0f, -0.5f, -1.5f, -0.5f, 1.0f, 3.0f, 3.5f, 2.0f, 0.5f, 0f),
            FactoryPreset.VOCAL_PRESENCE.gains.toList()
        )
    }

    @Test
    fun flatIsAllZero() {
        assertEquals(List(10) { 0f }, FactoryPreset.FLAT.gains.toList())
    }

    @Test
    fun everyPresetGainStaysWithinRange() {
        FactoryPreset.entries.forEach { preset ->
            preset.gains.forEach { gain ->
                org.junit.Assert.assertTrue(
                    "${preset.name} gain $gain must be within [${EqConstants.GAIN_MIN}, ${EqConstants.GAIN_MAX}]",
                    gain in EqConstants.GAIN_MIN..EqConstants.GAIN_MAX,
                )
            }
        }
    }

    @Test
    fun bandTypeFromCodeMapsCorrectly() {
        assertEquals(EqBandType.PEAK, EqBandType.fromCode(0))
        assertEquals(EqBandType.LOW_SHELF, EqBandType.fromCode(1))
        assertEquals(EqBandType.HIGH_SHELF, EqBandType.fromCode(2))
        assertEquals(EqBandType.PEAK, EqBandType.fromCode(99))
    }
}
