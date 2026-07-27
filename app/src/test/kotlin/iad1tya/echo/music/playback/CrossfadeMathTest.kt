package iad1tya.echo.music.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Locks the crossfade gain curves. These are the audio-critical invariants of the transition the owner
 * likes — any accidental change to the math must fail here before it ships.
 */
class CrossfadeMathTest {

    private val curves = listOf(0, 1, 2, 3, 4, 5, 6, 7, 8)

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
    fun `curve 4 asymmetric rise - gradual both ways, loudness-capped, rise spans 85 percent`() {
        // Fully resolved by p=0.85: incoming EXACTLY full, outgoing EXACTLY silent from there on.
        val (inFull, outAtFull) = CrossfadeMath.getGains(4, 0.85f)
        assertEquals("incoming must reach full level at p=0.85", 1f, inFull, 1e-3f)
        assertTrue("outgoing must be silent from p=0.85", outAtFull <= 1e-3f)
        val (inLate, _) = CrossfadeMath.getGains(4, 0.9f)
        assertEquals("incoming must hold full level after p=0.85", 1f, inLate, 1e-3f)
        // BOTH movements audible together: by 40% of the window the outgoing has ALREADY dropped
        // noticeably (the owner could not hear the old asymmetric pair's decay at all).
        val outAt40 = CrossfadeMath.getGains(4, 0.4f).second
        assertTrue("outgoing must be audibly lowering by p=0.4 (<0.93)", outAt40 < 0.93f)
        val (inNearFull, _) = CrossfadeMath.getGains(4, 0.8f)
        assertTrue("incoming must land smoothly (>=0.98 at p=0.8)", inNearFull >= 0.98f)
        // The rise is AUDIBLE for most of the blend ("se va haciendo notar"): clearly rising mid-window.
        val (inMid, _) = CrossfadeMath.getGains(4, 0.5f)
        assertTrue("incoming must be mid-rise at p=0.5 (0.6..0.95)", inMid in 0.6f..0.95f)
        // Gentle start: the first tenth of the window stays quiet (eased ramp, no abrupt entry).
        val (inEarly, _) = CrossfadeMath.getGains(4, 0.1f)
        assertTrue("incoming must start gently (<0.15 at p=0.1)", inEarly < 0.15f)
        // Outgoing decays gradually across the WHOLE window: audible mid-window, gone at 1.
        val outMid = CrossfadeMath.getGains(4, 0.5f).second
        assertTrue("outgoing must still be audible mid-window (0.5..0.9)", outMid in 0.5f..0.9f)
        val outEarly = CrossfadeMath.getGains(4, 0.1f).second
        assertTrue("outgoing must decay gently at the start (>0.95 at p=0.1)", outEarly > 0.95f)
        // LOUDNESS CAP: combined power never exceeds a single song's — the blend can never sound louder
        // than normal playback (the owner heard the raw +1.8dB mid-blend bump as "suena más").
        for (step in 0..100) {
            val p = step / 100f
            val (fadeIn, fadeOut) = CrossfadeMath.getGains(4, p)
            assertTrue("power must stay <= 1 (p=$p)", fadeIn * fadeIn + fadeOut * fadeOut <= 1f + 1e-3f)
        }
    }

    @Test
    fun `curve 5 V-fade - outgoing dies at the midpoint, incoming is born there, never both audible`() {
        // First half: fade-out only — the incoming stays silent.
        val (inEarly, outEarly) = CrossfadeMath.getGains(5, 0.25f)
        assertEquals("incoming silent in the first half", 0f, inEarly, 1e-4f)
        assertEquals("outgoing mid-fade at p=0.25 (cos pi/4)", 0.70711f, outEarly, 1e-3f)
        // Midpoint: back-to-back handover — the outgoing has just reached 0 and the incoming starts
        // rising from 0 at that very instant (a=b=0.5, so there is NO silence gap in between).
        val (inMid, outMid) = CrossfadeMath.getGains(5, 0.5f)
        assertEquals("outgoing fully out at p=0.5", 0f, outMid, 1e-4f)
        assertEquals("incoming starts exactly at p=0.5", 0f, inMid, 1e-4f)
        // Second half: fade-in only — the outgoing stays silent.
        val (inLate, outLate) = CrossfadeMath.getGains(5, 0.75f)
        assertEquals("outgoing silent in the second half", 0f, outLate, 1e-4f)
        assertEquals("incoming mid-fade at p=0.75 (sin pi/4)", 0.70711f, inLate, 1e-3f)
        // The defining property: the two songs are NEVER audible at the same time.
        for (step in 0..100) {
            val p = step / 100f
            val (fadeIn, fadeOut) = CrossfadeMath.getGains(5, p)
            assertTrue("tracks must never overlap (p=$p)", fadeIn < 1e-3f || fadeOut < 1e-3f)
        }
    }

    @Test
    fun `curve 6 dB-linear - minus 30dB deep valley at the midpoint, mirrored halves`() {
        // Straight line in dB over R=60dB: at p=0.5 each track sits at −30dB → raw 10^−1.5 (~0.0316),
        // then the exact endpoint splice (subtract the 10^−3 floor, renormalize) gives ~0.03065.
        val expectedMid = ((Math.pow(10.0, -1.5) - 1e-3) / (1.0 - 1e-3)).toFloat()
        val (inMid, outMid) = CrossfadeMath.getGains(6, 0.5f)
        assertEquals("outgoing at −30dB (spliced) at p=0.5", expectedMid, outMid, 1e-4f)
        assertEquals("incoming at −30dB (spliced) at p=0.5", expectedMid, inMid, 1e-4f)
        // "Dies for real": the outgoing is below −20dB (0.1) well before the midpoint region ends.
        assertTrue("outgoing under 0.1 by p=0.35", CrossfadeMath.getGains(6, 0.35f).second < 0.1f)
        // The incoming is the exact mirror of the outgoing: g_in(p) = g_out(1−p).
        for (step in 0..100) {
            val p = step / 100f
            val fadeIn = CrossfadeMath.getGains(6, p).first
            val mirroredOut = CrossfadeMath.getGains(6, 1f - p).second
            assertEquals("g_in(p) must equal g_out(1−p) at p=$p", mirroredOut, fadeIn, 1e-4f)
        }
    }

    @Test
    fun `curve 7 deep dip - a minus 12dB cubic breath at the center, never silence, distinct from curve 3`() {
        val (inMid, outMid) = CrossfadeMath.getGains(7, 0.5f)
        assertEquals("incoming at 0.125 at the center", 0.125f, inMid, 1e-4f)
        assertEquals("outgoing at 0.125 at the center", 0.125f, outMid, 1e-4f)
        assertEquals("summed amplitude dips to 0.25 (−12dB) at the center", 0.25f, inMid + outMid, 1e-4f)
        // The dip is a deep breath, not silence: summed amplitude never falls below 0.25 anywhere.
        for (step in 0..100) {
            val p = step / 100f
            val (fadeIn, fadeOut) = CrossfadeMath.getGains(7, p)
            assertTrue("sum must stay >= 0.25 (p=$p)", fadeIn + fadeOut >= 0.25f - 1e-4f)
        }
        // Cubic ≠ parabolic: curve 7 must NOT alias curve 3 (the original parabolic dipped duplicate).
        val three = CrossfadeMath.getGains(3, 0.5f)
        assertTrue("curve 7 must differ from curve 3", kotlin.math.abs(three.first - inMid) > 0.05f)
    }

    @Test
    fun `curve 8 equal-gain - amplitudes sum to exactly 1 with zero-slope ends`() {
        for (step in 0..100) {
            val p = step / 100f
            val (fadeIn, fadeOut) = CrossfadeMath.getGains(8, p)
            assertEquals("in+out must stay exactly 1 at p=$p", 1f, fadeIn + fadeOut, 1e-4f)
        }
        // Zero-slope ends (raised cosine): the edges barely move — gentlest possible start/landing.
        assertTrue("gentle start (<0.01 at p=0.05)", CrossfadeMath.getGains(8, 0.05f).first < 0.01f)
        assertTrue("gentle landing (>0.99 at p=0.95)", CrossfadeMath.getGains(8, 0.95f).first > 0.99f)
    }

    @Test
    fun `unknown curve falls back to linear`() {
        for (step in 0..10) {
            val p = step / 10f
            assertEquals(CrossfadeMath.getGains(0, p), CrossfadeMath.getGains(99, p))
        }
    }
}
