package iad1tya.echo.music.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the three real-world windows in which the lyrics view stayed on a song the user was no longer
 * hearing, plus the invariant that the fix can only ever make the lyrics come back to the live song EARLIER.
 *
 * The simulator below is a faithful transcription of the dual-clock fade loop in
 * `MusicService.performCrossfadeSwap` (`outElapsed`/`inElapsed` advance only while their own player renders,
 * `outDone` forces `outP = 1f`, the loop exits on `inP >= 1f && outP >= 1f`, 40 ms ticks). Only the pin
 * decision is under test — no volume is written and no fade math is changed by anything here.
 */
class CrossfadeLyricsPinTest {

    private companion object {
        const val TICK_MS = 40L
        const val DEFAULT_CURVE = 4          // MusicService: dataStore.get(CrossfadeCurveKey, 4)
        const val DEFAULT_DURATION_MS = 5_000L // MusicService: crossfadeDuration default
    }

    /** One tick's worth of "what were the two players doing". */
    private data class Tick(
        val outRendering: Boolean = true,
        val inRendering: Boolean = true,
        val outEnded: Boolean = false,
        val detectorSilent: Boolean = false,
    )

    private class FadeSim(
        val durOut: Long,
        val durIn: Long = DEFAULT_DURATION_MS,
        val curve: Int = DEFAULT_CURVE,
    ) {
        private var outElapsed = 0L
        private var inElapsed = 0L
        private var wall = 0L
        var pinned = true
            private set
        /** Wall-clock ms at which the lyrics returned to the live song with the fix in place. */
        var releasedAtMs: Long? = null
            private set
        /** Wall-clock ms at which the loop would exit — i.e. when the OLD code released the pin. */
        var loopEndMs: Long? = null
            private set

        fun run(ticks: Long, state: (Long) -> Tick) {
            repeat(ticks.toInt()) {
                if (loopEndMs != null) return
                val t = state(wall)
                wall += TICK_MS
                if (t.outRendering && !t.outEnded) outElapsed += TICK_MS
                if (t.inRendering) inElapsed += TICK_MS

                val outDone = t.outEnded
                val outP = if (outDone) 1f else (outElapsed / durOut.toFloat()).coerceAtMost(1f)
                val inP = (inElapsed / durIn.toFloat()).coerceAtMost(1f)
                val fadeOut = CrossfadeMath.getGains(curve, outP).second

                if (CrossfadeLyricsPin.shouldRelease(pinned, outDone, fadeOut, t.detectorSilent)) {
                    pinned = false
                    if (releasedAtMs == null) releasedAtMs = wall
                }
                if (inP >= 1f && outP >= 1f) loopEndMs = wall
            }
        }
    }

    // ---------------------------------------------------------------- window 1

    @Test
    fun `window 1 - slow-resolving incoming no longer holds the lyrics on the dead song`() {
        // Fade fired close to the file end: durOut is capped to the outgoing's remaining audible life.
        // The incoming is still buffering (streaming / Lossless), so its clock does not advance at all.
        val sim = FadeSim(durOut = 600L)
        sim.run(ticks = 250) { Tick(outRendering = true, inRendering = false) }

        val released = sim.releasedAtMs
        assertNotNull("pin must be released even though the incoming ramp never progressed", released)
        assertTrue("released $released ms in; the outgoing is silent from ~510ms", released!! <= 600L)
        assertNull("the loop itself is still running - that is the whole point", sim.loopEndMs)
    }

    // ---------------------------------------------------------------- window 2

    @Test
    fun `window 2 - pause after the outgoing has decayed releases instead of freezing for the pause`() {
        val sim = FadeSim(durOut = 600L)
        // 700ms of real playback (outgoing fully decayed), then the user pauses to read the lyrics: both
        // clocks freeze. Old behaviour: the pin was held for the entire pause, up to the ~40s safety bail.
        sim.run(ticks = 1_000) { elapsed ->
            if (elapsed < 700L) Tick() else Tick(outRendering = false, inRendering = false)
        }

        val released = sim.releasedAtMs
        assertNotNull("a pause must not strand the lyrics on the finished song", released)
        assertTrue("released $released ms in, before the pause froze the clocks", released!! <= 700L)
        assertNull("the loop is frozen by the pause and never exits", sim.loopEndMs)
    }

    @Test
    fun `window 2 - pausing while the outgoing is still audible must NOT release`() {
        // The guard against over-correcting: pause 200ms into a 5s outgoing ramp. The outgoing is at ~full
        // level and resumes on play, so the lyrics on screen are the RIGHT ones. Releasing here would show
        // the incoming song's lyrics over a song the user is about to keep hearing.
        val sim = FadeSim(durOut = DEFAULT_DURATION_MS)
        sim.run(ticks = 1_000) { elapsed ->
            if (elapsed < 200L) Tick() else Tick(outRendering = false, inRendering = false)
        }
        assertNull("must stay pinned: the paused outgoing is still the audible song", sim.releasedAtMs)
        assertTrue(sim.pinned)
    }

    // ---------------------------------------------------------------- window 3

    @Test
    fun `window 3 - a manual skip mid-fade releases the pin`() {
        assertTrue(
            "song C is playing, the pin still names song A",
            CrossfadeLyricsPin.shouldReleaseOnTransition(pinnedId = "A", liveMediaId = "C"),
        )
    }

    @Test
    fun `window 3 - repeat-one and spurious null transitions do not release`() {
        assertFalse(
            "repeat-one crossfades A into A; the pinned song is the live one",
            CrossfadeLyricsPin.shouldReleaseOnTransition(pinnedId = "A", liveMediaId = "A"),
        )
        assertFalse(
            "media3 fires a null-item transition around a swap; A is still audible",
            CrossfadeLyricsPin.shouldReleaseOnTransition(pinnedId = "A", liveMediaId = null),
        )
        assertFalse(
            "no pin held - nothing to release",
            CrossfadeLyricsPin.shouldReleaseOnTransition(pinnedId = null, liveMediaId = "C"),
        )
    }

    // ---------------------------------------------------------------- tail-silence fire

    @Test
    fun `a fade fired on the silent tail releases on the first tick`() {
        // onTailSilenceDetected fires the crossfade while the outgoing is already digitally silent: the gain
        // is still ~1 for the whole ramp, so only the detector can see that there is nothing to hear.
        val sim = FadeSim(durOut = DEFAULT_DURATION_MS)
        sim.run(ticks = 10) { Tick(detectorSilent = true) }
        assertEquals("released on the first tick", TICK_MS, sim.releasedAtMs)
    }

    @Test
    fun `the outgoing player ending releases the pin`() {
        val sim = FadeSim(durOut = DEFAULT_DURATION_MS)
        sim.run(ticks = 10) { elapsed -> Tick(outEnded = elapsed >= 80L) }
        assertEquals(120L, sim.releasedAtMs)
    }

    // ---------------------------------------------------------------- invariants

    @Test
    fun `every curve releases no later than the old cleanup did`() {
        // THE invariant: the change may only ADVANCE the moment the lyrics come back, never delay it.
        // Swept over every selectable curve and over the clock patterns that actually differ in the field.
        val patterns: List<Pair<String, (Long) -> Tick>> = listOf(
            "both rendering" to { _: Long -> Tick() },
            "incoming buffering 2s" to { e: Long -> Tick(inRendering = e >= 2_000L) },
            "outgoing stalls 1s" to { e: Long -> Tick(outRendering = e !in 500L..1_500L) },
            "outgoing ends early" to { e: Long -> Tick(outEnded = e >= 1_200L) },
        )
        for (curve in 0..8) {
            for (durOut in listOf(600L, 1_500L, 4_750L, DEFAULT_DURATION_MS)) {
                for ((name, pattern) in patterns) {
                    val sim = FadeSim(durOut = durOut, curve = curve)
                    sim.run(ticks = 2_000, state = pattern)
                    val released = sim.releasedAtMs
                    val loopEnd = sim.loopEndMs
                    assertNotNull(
                        "curve $curve durOut $durOut [$name]: pin never released",
                        released ?: loopEnd,
                    )
                    if (loopEnd != null) {
                        assertNotNull(
                            "curve $curve durOut $durOut [$name]: loop ended but the pin was never released",
                            released,
                        )
                        assertTrue(
                            "curve $curve durOut $durOut [$name]: released at $released, old code at $loopEnd",
                            released!! <= loopEnd,
                        )
                    }
                }
            }
        }
    }

    @Test
    fun `the pin is never re-taken once released`() {
        val sim = FadeSim(durOut = 600L)
        sim.run(ticks = 500) { Tick() }
        assertFalse(sim.pinned)
        assertNotNull(sim.releasedAtMs)
    }

    @Test
    fun `an unpinned fade never asks for a release`() {
        assertFalse(CrossfadeLyricsPin.shouldRelease(false, outgoingGone = true, 0f, true))
    }

    @Test
    fun `the threshold is on the curve gain, not on the volume actually written`() {
        // The rejected alternative was `startVolume * fadeOut * headroom <= 0.02f`. startVolume is the
        // USER'S real volume, so for anyone listening at a low in-app level (or muted) that expression is
        // already under the threshold at p = 0, and the pin would be dropped on the first tick of EVERY
        // fade - showing the next song's lyrics over a clearly audible previous one. Same trap that is
        // documented on MusicService.crossfadeOutgoingPositionMs.
        val fadeOutAtStart = CrossfadeMath.getGains(DEFAULT_CURVE, 0f).second
        val quietUserVolume = 0.01f

        assertTrue(
            "the rejected absolute-gain form fires immediately for a quiet listener",
            fadeOutAtStart * quietUserVolume <= CrossfadeLyricsPin.INAUDIBLE_CURVE_GAIN,
        )
        assertFalse(
            "the shipped predicate does not, because it never sees the user's volume",
            CrossfadeLyricsPin.shouldRelease(true, outgoingGone = false, fadeOutAtStart, false),
        )
    }

    @Test
    fun `a not-a-number gain releases rather than pinning forever`() {
        assertTrue(CrossfadeLyricsPin.shouldRelease(true, outgoingGone = false, Float.NaN, false))
    }

    @Test
    fun `default curve is inaudible from 85 percent of the outgoing ramp`() {
        // Documents WHY ramp progress is the wrong release signal: with the shipped default the outgoing has
        // been at zero gain for the last 15% of its ramp, and the loop would still be waiting on the
        // incoming clock.
        assertTrue(CrossfadeMath.getGains(DEFAULT_CURVE, 0.85f).second <= CrossfadeLyricsPin.INAUDIBLE_CURVE_GAIN)
        assertTrue(CrossfadeMath.getGains(DEFAULT_CURVE, 0.70f).second > CrossfadeLyricsPin.INAUDIBLE_CURVE_GAIN)
    }
}
