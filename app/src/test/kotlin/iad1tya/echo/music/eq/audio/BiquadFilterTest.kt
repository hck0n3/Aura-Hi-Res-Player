package iad1tya.echo.music.eq.audio

import iad1tya.echo.music.eq.data.FilterType
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

class BiquadFilterTest {

    private val sampleRate = 48000

    /** Feeds a sine of [frequency] and returns the steady-state output peak amplitude. */
    private fun measureGain(filter: BiquadFilter, frequency: Double): Double {
        val warmup = 16384
        val measure = 8192
        var peak = 0.0
        for (i in 0 until warmup + measure) {
            val x = sin(2.0 * PI * frequency * i / sampleRate)
            val y = filter.processSample(x)
            if (i >= warmup) peak = maxOf(peak, abs(y))
        }
        return peak
    }

    @Test
    fun peakingWithZeroGainIsPassthrough() {
        val filter = BiquadFilter(sampleRate, 1000.0, 0.0, 1.41, FilterType.PK)
        assertEquals(1.0, measureGain(filter, 1000.0), 0.01)
    }

    @Test
    fun peakingBoostsCenterFrequencyByConfiguredGain() {
        val filter = BiquadFilter(sampleRate, 1000.0, 12.0, 1.41, FilterType.PK)
        // +12 dB == x3.98 linear at the center frequency
        assertEquals(3.98, measureGain(filter, 1000.0), 0.15)
    }

    @Test
    fun peakingLeavesFarFrequenciesUntouched() {
        val filter = BiquadFilter(sampleRate, 1000.0, 12.0, 1.41, FilterType.PK)
        assertEquals(1.0, measureGain(filter, 60.0), 0.05)
    }

    @Test
    fun lowShelfBoostsLowFrequencies() {
        val filter = BiquadFilter(sampleRate, 1000.0, 6.0, 1.41, FilterType.LSC)
        // +6 dB == x2 linear well below the corner frequency
        assertEquals(2.0, measureGain(filter, 40.0), 0.1)
    }

    @Test
    fun highShelfBoostsHighFrequencies() {
        val filter = BiquadFilter(sampleRate, 1000.0, 6.0, 1.41, FilterType.HSC)
        assertEquals(2.0, measureGain(filter, 12000.0), 0.15)
    }

    @Test
    fun resetClearsFilterState() {
        val filter = BiquadFilter(sampleRate, 1000.0, 12.0, 1.41, FilterType.PK)
        filter.processSample(1.0)
        filter.processSample(0.5)
        filter.reset()
        assertEquals(0.0, filter.processSample(0.0), 0.0)
    }

    @Test
    fun stereoChannelsAreProcessedIndependently() {
        val filter = BiquadFilter(sampleRate, 1000.0, 12.0, 1.41, FilterType.PK)
        for (i in 0 until 256) {
            val x = sin(2.0 * PI * 1000.0 * i / sampleRate)
            val (_, right) = filter.processStereo(x, 0.0)
            assertEquals(0.0, right, 0.0)
        }
    }
}
