package iad1tya.echo.music.listentogether

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * These pin the three reported Listen Together defects as decisions, so they can be checked without two
 * phones and a server:
 *
 *  1. the guest landing exactly ONE SONG AHEAD of the host,
 *  2. the host's volume never reaching anyone,
 *  3. the pair drifting apart from the first automatic track advance onward.
 */
class ListenTogetherSyncTest {

    // ---------------------------------------------------------------------------------------------
    // Defect 1: identity vs index, and the double-advance
    // ---------------------------------------------------------------------------------------------

    /**
     * THE off-by-one. A single host skip publishes an absolute CHANGE_TRACK (media3 fires the transition
     * synchronously inside seekToNext) and, historically, a relative SKIP_NEXT right behind it. Applying
     * both moves the guest twice. The second one must be dropped.
     */
    @Test
    fun aRelativeSkipArrivingRightAfterAnAbsoluteTrackChangeIsIgnored() {
        val trackChangeAt = 10_000L
        val skipArrivesAt = trackChangeAt + 40L // same user action, one network hop apart

        assertFalse(
            "a skip landing on the heels of a track change is the second half of a double-publish",
            ListenTogetherSync.shouldApplyRelativeSkip(skipArrivesAt, trackChangeAt),
        )
    }

    /** A genuine, separate skip later on must still be honoured — the guard is a window, not a mute. */
    @Test
    fun aRelativeSkipLongAfterATrackChangeIsStillApplied() {
        val trackChangeAt = 10_000L
        val skipArrivesAt = trackChangeAt + ListenTogetherSync.SKIP_SUPPRESSION_WINDOW_MS + 1

        assertTrue(ListenTogetherSync.shouldApplyRelativeSkip(skipArrivesAt, trackChangeAt))
    }

    /** Nothing absolute has ever been applied (fresh guest): a relative skip is all we have. */
    @Test
    fun aRelativeSkipIsAppliedWhenNoTrackChangeHasEverLanded() {
        assertTrue(ListenTogetherSync.shouldApplyRelativeSkip(nowMs = 5_000L, lastTrackChangeAppliedAtMs = 0L))
    }

    /** Identity, not position: the guest anchors on the media id wherever it now sits. */
    @Test
    fun theStartIndexIsFoundByMediaIdNotByPosition() {
        val hostQueue = listOf("a", "b", "c", "d")

        assertEquals(2, ListenTogetherSync.resolveStartIndex(hostQueue, "c"))
    }

    /**
     * The queues have diverged by one item (the radio appended, or shuffle reordered). A positional
     * anchor would land on the wrong song; identity still finds the right one.
     */
    @Test
    fun anExtraItemAheadOfTheCurrentSongDoesNotShiftTheAnchor() {
        val hostQueue = listOf("a", "b", "c", "d")
        val guestQueueWithAnExtraItem = listOf("a", "zz", "b", "c", "d")

        val hostIndex = ListenTogetherSync.resolveStartIndex(hostQueue, "c")
        val guestIndex = ListenTogetherSync.resolveStartIndex(guestQueueWithAnExtraItem, "c")

        assertEquals(2, hostIndex)
        assertEquals(3, guestIndex)
        // Different positions, same song — which is the entire point of anchoring by identity.
        assertEquals("c", guestQueueWithAnExtraItem[guestIndex])
    }

    @Test
    fun aTrackThatIsNotInTheQueueReportsAbsenceInsteadOfDefaultingToTheFirstSong() {
        assertEquals(-1, ListenTogetherSync.resolveStartIndex(listOf("a", "b"), "nope"))
        assertEquals(-1, ListenTogetherSync.resolveStartIndex(listOf("a", "b"), null))
        assertEquals(-1, ListenTogetherSync.resolveStartIndex(emptyList(), "a"))
    }

    // ---------------------------------------------------------------------------------------------
    // Defect 3: the buffer wait must not become permanent lag
    // ---------------------------------------------------------------------------------------------

    /**
     * The guest received "play at 0" and only got to apply it 2.4 s later, after buffering the new track
     * and waiting for the server's BUFFER_COMPLETE. The host never waited, so it is 2.4 s in.
     */
    @Test
    fun timeSpentBufferingIsAddedBackWhenTheHostIsPlaying() {
        val applied = ListenTogetherSync.compensatedPosition(
            basePositionMs = 0L,
            capturedAtMs = 1_000_000L,
            nowMs = 1_002_400L,
            isPlaying = true,
        )

        assertEquals(2_400L, applied)
    }

    /** A paused host's position is frozen; adding elapsed time would jump the guest forward. */
    @Test
    fun aPausedHostsPositionIsAppliedVerbatim() {
        val applied = ListenTogetherSync.compensatedPosition(
            basePositionMs = 45_000L,
            capturedAtMs = 1_000_000L,
            nowMs = 1_009_000L,
            isPlaying = false,
        )

        assertEquals(45_000L, applied)
    }

    /** A clock that runs backwards must never rewind the guest. */
    @Test
    fun aBackwardsClockNeverSeeksBackwards() {
        val applied = ListenTogetherSync.compensatedPosition(
            basePositionMs = 30_000L,
            capturedAtMs = 1_005_000L,
            nowMs = 1_000_000L,
            isPlaying = true,
        )

        assertEquals(30_000L, applied)
    }

    /** Compensation must not run past the end of the song and strand the guest at STATE_ENDED. */
    @Test
    fun compensationIsClampedToTheTrackDuration() {
        val applied = ListenTogetherSync.compensatedPosition(
            basePositionMs = 170_000L,
            capturedAtMs = 1_000_000L,
            nowMs = 1_030_000L,
            isPlaying = true,
            durationMs = 180_000L,
        )

        assertEquals(180_000L, applied)
    }

    /** Unknown duration (player not ready) disables the clamp rather than clamping to zero. */
    @Test
    fun anUnknownDurationDoesNotClampToZero() {
        val applied = ListenTogetherSync.compensatedPosition(
            basePositionMs = 10_000L,
            capturedAtMs = 1_000_000L,
            nowMs = 1_001_000L,
            isPlaying = true,
            durationMs = 0L,
        )

        assertEquals(11_000L, applied)
    }

    /** No capture timestamp means nothing can be reasoned about staleness — apply as-is. */
    @Test
    fun aMissingCaptureTimestampAppliesThePositionUnchanged() {
        val applied = ListenTogetherSync.compensatedPosition(
            basePositionMs = 12_345L,
            capturedAtMs = 0L,
            nowMs = 9_999_999L,
            isPlaying = true,
        )

        assertEquals(12_345L, applied)
    }

    // ---------------------------------------------------------------------------------------------
    // Defect 2: what the host actually publishes as "my level"
    // ---------------------------------------------------------------------------------------------

    /**
     * The regression that made the feature look dead: only the in-app attenuation was ever observed, so a
     * host who turned the device volume down (player-screen slider or hardware buttons) published a
     * constant 1.0 and nobody followed.
     */
    @Test
    fun theDeviceVolumeIsPartOfThePublishedLevel() {
        assertEquals(0.5f, ListenTogetherSync.effectiveHostVolume(1f, muted = false, streamFraction = 0.5f), 1e-6f)
        assertEquals(0.25f, ListenTogetherSync.effectiveHostVolume(0.5f, muted = false, streamFraction = 0.5f), 1e-6f)
    }

    /** Muting does not change playerVolume, so mute has to be folded in explicitly or it is invisible. */
    @Test
    fun aMutedHostPublishesSilence() {
        assertEquals(0f, ListenTogetherSync.effectiveHostVolume(1f, muted = true, streamFraction = 1f), 1e-6f)
    }

    @Test
    fun outOfRangeOrUnreadableVolumesAreCoercedInsteadOfPoisoningTheRoom() {
        assertEquals(1f, ListenTogetherSync.effectiveHostVolume(4f, muted = false, streamFraction = 2f), 1e-6f)
        assertEquals(0f, ListenTogetherSync.effectiveHostVolume(-1f, muted = false, streamFraction = 1f), 1e-6f)
        assertEquals(1f, ListenTogetherSync.effectiveHostVolume(Float.NaN, muted = false, streamFraction = Float.NaN), 1e-6f)
    }

    @Test
    fun theFirstVolumeIsAlwaysPublishedAndTinyJittersAreNot() {
        assertTrue(ListenTogetherSync.volumeChangedEnough(null, 1f))
        assertFalse(ListenTogetherSync.volumeChangedEnough(0.5f, 0.5005f))
        assertTrue(ListenTogetherSync.volumeChangedEnough(0.5f, 0.6f))
    }

    /** Silence is a real level: dragging the host to 0 must still be published. */
    @Test
    fun droppingToSilenceCountsAsAChange() {
        assertTrue(ListenTogetherSync.volumeChangedEnough(0.4f, 0f))
    }
}
