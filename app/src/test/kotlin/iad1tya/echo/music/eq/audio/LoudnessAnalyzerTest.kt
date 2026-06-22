package iad1tya.echo.music.eq.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin

class LoudnessAnalyzerTest {

    private val fs = 48000

    /** Stereo 1 kHz sine of the given linear amplitude, [seconds] long, fed through the analyzer. */
    private fun measureSine(amplitude: Double, seconds: Double = 2.0): Double? {
        val analyzer = LoudnessAnalyzer(fs, 2)
        val total = (fs * seconds).toInt()
        val chunk = 4096
        val buf = FloatArray(chunk * 2)
        var n = 0
        while (n < total) {
            val frames = minOf(chunk, total - n)
            for (f in 0 until frames) {
                val s = (amplitude * sin(2.0 * PI * 1000.0 * (n + f) / fs)).toFloat()
                buf[f * 2] = s
                buf[f * 2 + 1] = s
            }
            analyzer.process(buf, frames)
            n += frames
        }
        return analyzer.integratedLufs
    }

    @Test fun silenceIsNull() {
        val analyzer = LoudnessAnalyzer(fs, 2)
        analyzer.process(FloatArray(4096 * 2), 4096)
        assertNull(analyzer.integratedLufs)
    }

    @Test fun tooShortIsNull() {
        // < 400 ms → no complete block → null.
        assertNull(measureSine(0.5, seconds = 0.1))
    }

    @Test fun sineProducesPlausibleLoudness() {
        val lufs = measureSine(0.5)!!
        // A -9 dBFS-RMS 1 kHz tone lands well within a sane LUFS window.
        assertTrue("lufs=$lufs", lufs in -25.0..0.0)
    }

    @Test fun louderSineIsLouder() {
        val quiet = measureSine(0.25)!!
        val loud = measureSine(0.75)!!
        assertTrue("quiet=$quiet loud=$loud", loud > quiet)
    }

    @Test fun doublingAmplitudeAddsAboutSixLu() {
        val a = measureSine(0.25)!!
        val b = measureSine(0.5)!!
        // Energy-based meter: doubling amplitude ≈ +6.02 LU.
        assertEquals(6.02, b - a, 0.3)
    }

    @Test fun loudnessDbConventionMatchesReference() {
        // -20 LUFS is 6 dB below the -14 LUFS reference → loudnessDb = -6 (needs +6 dB makeup).
        assertEquals(-6.0, LoudnessAnalyzer.lufsToLoudnessDb(-20.0), 1e-9)
        // At reference → 0.
        assertEquals(0.0, LoudnessAnalyzer.lufsToLoudnessDb(-14.0), 1e-9)
    }
}
