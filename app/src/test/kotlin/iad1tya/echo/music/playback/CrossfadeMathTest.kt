package iad1tya.echo.music.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Locks the crossfade gain curves. These are the audio-critical invariants of the transition the owner
 * likes — any accidental change to the math must fail here before it ships.
 */
class CrossfadeMathTest {

    private val curves = listOf(0, 1, 2, 3, 4)

    @Test
    fun `every curve starts silent-in full-out and ends full-in silent-out`() {
        curves.forEach { curve ->
            val (in0, out0) = CrossfadeMath.getGains(curve, 0f)
            val (in1, out1) = CrossfadeMath.getGains(curve, 1f)
            assertEquals("curve $curve incoming at p=0", 0f, in0, 1e-4f)
            assertEquals("curve $curve outgoing at p=0", 1f, out0, 1e-4f)
            assertEquals("curve $curve incoming at p=1", 1f, in1, 1e-4f)
            assertEquals("curve $curve outgoing at p=1", 0f, out1, 1e-4f)
        }
    }

    @Test
    fun `every curve is monotonic - incoming rises outgoing falls`() {
        curves.forEach { curve ->
            var lastIn = -1f
            var lastOut = 2f
            for (step in 0..100) {
                val p = step / 100f
                val (fadeIn, fadeOut) = CrossfadeMath.getGains(curve, p)
                assertTrue("curve $curve incoming must not decrease (p=$p)", fadeIn >= lastIn - 1e-4f)
                assertTrue("curve $curve outgoing must not increase (p=$p)", fadeOut <= lastOut + 1e-4f)
                // Epsilon on both ends: cos(PI/2) in float is ~-4.4e-8, not exactly 0 (ExoPlayer clamps
                // volume to 0..1 internally, so the sub-float negative is inaudible and harmless).
                assertTrue(
                    "curve $curve gains in range (p=$p)",
                    fadeIn >= -1e-4f && fadeIn <= 1f + 1e-4f && fadeOut >= -1e-4f && fadeOut <= 1f + 1e-4f
                )
                lastIn = fadeIn
                lastOut = fadeOut
            }
        }
    }

    @Test
    fun `default curve 1 is equal-power - constant power through the whole blend`() {
        for (step in 0..100) {
            val p = step / 100f
            val (fadeIn, fadeOut) = CrossfadeMath.getGains(1, p)
            assertEquals("in^2+out^2 must stay 1 at p=$p", 1f, fadeIn * fadeIn + fadeOut * fadeOut, 1e-3f)
        }
    }

    @Test
    fun `curve 0 is linear - amplitudes always sum to exactly 1`() {
        for (step in 0..100) {
            val p = step / 100f
            val (fadeIn, fadeOut) = CrossfadeMath.getGains(0, p)
            assertEquals("in+out must stay 1 at p=$p", 1f, fadeIn + fadeOut, 1e-4f)
        }
    }

    @Test
    fun `curve 4 asymmetric rise - gradual both ways, incoming full by 60 percent with a gentle landing`() {
        // Incoming reaches full level at p=0.6 and HOLDS it — but arrives gently (zero-slope landing).
        val (inSixty, _) = CrossfadeMath.getGains(4, 0.6f)
        assertEquals("incoming must reach full level at p=0.6", 1f, inSixty, 1e-4f)
        val (inLate, _) = CrossfadeMath.getGains(4, 0.8f)
        assertEquals("incoming must hold full level after p=0.6", 1f, inLate, 1e-4f)
        // Gentle landing: just before full, the gain is already within 1% of unity (no hard clamp kink).
        val (inNearFull, _) = CrossfadeMath.getGains(4, 0.55f)
        assertTrue("incoming must land smoothly (>=0.99 at p=0.55)", inNearFull >= 0.99f)
        // Gentle start: the first tenth of the window stays quiet (eased ramp, no abrupt entry).
        val (inEarly, _) = CrossfadeMath.getGains(4, 0.1f)
        assertTrue("incoming must start gently (<0.15 at p=0.1)", inEarly < 0.15f)
        // Outgoing decays gradually across the WHOLE window: gentle at first, ~mid-level mid-window, gone at 1.
        val outMid = CrossfadeMath.getGains(4, 0.5f).second
        assertTrue("outgoing must still be audible mid-window (0.5..0.9)", outMid in 0.5f..0.9f)
        val outEarly = CrossfadeMath.getGains(4, 0.1f).second
        assertTrue("outgoing must decay gently at the start (>0.95 at p=0.1)", outEarly > 0.95f)
    }

    @Test
    fun `unknown curve falls back to linear`() {
        for (step in 0..10) {
            val p = step / 10f
            assertEquals(CrossfadeMath.getGains(0, p), CrossfadeMath.getGains(99, p))
        }
    }
}
