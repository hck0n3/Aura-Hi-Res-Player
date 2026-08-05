package iad1tya.echo.music.playback

import iad1tya.echo.music.extensions.toPersistQueue
import iad1tya.echo.music.extensions.toQueue
import iad1tya.echo.music.models.MediaMetadata
import iad1tya.echo.music.playback.queues.ListQueue
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The three pure halves of "¿quieres volver a la cola anterior?":
 *
 *  1. WHEN to offer — the contextId prefix transition. Over-firing is the real risk: a prompt that pops
 *     up on ordinary browsing is worse than no prompt at all, so the matrix below walks EVERY pair of
 *     prefixes the app can produce, not only the ones the feature was built for.
 *  2. HOW LONG the offer lives — a snapshot that outlives the wandering is a prompt detached from
 *     anything the user did, and a queue held in memory for nothing.
 *  3. WHERE it resumes — the PersistQueue round trip has to carry the song index AND the position, or
 *     "volver a donde estaba" silently becomes "empezar la lista otra vez".
 *
 * All three are deliberately free of Android, so they run in the plain JVM unit-test task.
 */
class PreviousQueueOfferTest {

    // ---------------------------------------------------------------------------------------------
    // 1. Detour detection
    //
    // The rule in one line: leaving a LISTENING LIST ("PL:" playlist / "AP:" auto-playlist / "LIB:"
    // library) for something that is NOT a listening list ("AL:" album, "AR:" artist, or an untagged
    // queue) is a detour. Everything else is not.
    // ---------------------------------------------------------------------------------------------

    /** Listening to a playlist, opens a song's album, taps the album's SHUFFLE button ("AL:<id>"). */
    @Test
    fun leavingAPlaylistForAnAlbumFires() {
        assertTrue(PreviousQueueRule.isDetour("PL:VLxyz123", "AL:MPREb_abc"))
    }

    /**
     * THE OWNER'S LITERAL CASE, and the one a prefix-matched rule would have missed. The album screen's
     * Play button and a tap on a track both start a LocalAlbumRadio with contextId = null — only its
     * Shuffle button tags the queue "AL:<id>". A rule that required an "AL:" destination would compile,
     * test green and never fire once in his hands.
     */
    @Test
    fun leavingAPlaylistForAnUntaggedAlbumQueueFires() {
        assertTrue(PreviousQueueRule.isDetour("PL:VLxyz123", null))
    }

    /** Auto-playlists (liked, downloads…) are lists he listens to just the same. */
    @Test
    fun leavingAnAutoPlaylistForAnAlbumFires() {
        assertTrue(PreviousQueueRule.isDetour("AP:liked", "AL:MPREb_abc"))
        assertTrue(PreviousQueueRule.isDetour("AP:liked", null))
    }

    /**
     * The library tab played as one list ("LIB:LIBRARY") is a listening list too — the LONGEST queue the
     * app can build, and the one whose cursor is least reproducible by hand. Leaving it to look up an
     * album or an artist is the same shape as leaving a playlist.
     */
    @Test
    fun leavingTheLibraryForAnAlbumOrArtistFires() {
        assertTrue(PreviousQueueRule.isDetour("LIB:LIBRARY", "AL:MPREb_abc"))
        assertTrue(PreviousQueueRule.isDetour("LIB:LIBRARY", "AR:UCabc"))
        assertTrue(PreviousQueueRule.isDetour("LIB:LIBRARY", null))
        // The hide-explicit variant of the same bucket (LibrarySongsScreen appends ":NX") is still LIB:.
        assertTrue(PreviousQueueRule.isDetour("LIB:LIBRARY:NX", "AL:MPREb_abc"))
    }

    /** Browsing album to album is not a detour — there is no list waiting behind it. */
    @Test
    fun albumToAlbumDoesNotFire() {
        assertFalse(PreviousQueueRule.isDetour("AL:MPREb_one", "AL:MPREb_two"))
        assertFalse(PreviousQueueRule.isDetour("AL:MPREb_one", null))
    }

    /** Artist to artist, same reasoning: an artist page is a place he goes to, not a list he sat with. */
    @Test
    fun artistToArtistDoesNotFire() {
        assertFalse(PreviousQueueRule.isDetour("AR:UCone", "AR:UCtwo"))
        assertFalse(PreviousQueueRule.isDetour("AR:UCone", null))
    }

    /**
     * Album -> artist is the most common browsing hop in the app (open an album, tap the artist) and it
     * must stay silent. An album is not a source: pressing Play on it again costs one track's position,
     * and mechanically it could only half-work — an album queue carries "AL:" ONLY when it was started
     * from the album's Shuffle button, so a rule keyed on it would fire for a shuffled album and stay
     * silent for the very same album started with Play.
     */
    @Test
    fun albumToArtistDoesNotFire() {
        assertFalse(PreviousQueueRule.isDetour("AL:MPREb_abc", "AR:UCabc"))
        assertFalse(PreviousQueueRule.isDetour("AR:UCabc", "AL:MPREb_abc"))
    }

    /** Switching lists is a deliberate change of what he is listening to, not a lookup. */
    @Test
    fun playlistToPlaylistDoesNotFire() {
        assertFalse(PreviousQueueRule.isDetour("PL:VLone", "PL:VLtwo"))
        assertFalse(PreviousQueueRule.isDetour("AP:liked", "AP:downloads"))
        assertFalse(PreviousQueueRule.isDetour("PL:VLone", "AP:liked"))
        assertFalse(PreviousQueueRule.isDetour("AP:liked", "PL:VLone"))
    }

    /** Playing the whole library is another deliberate switch, not a lookup — in BOTH directions. */
    @Test
    fun playlistToLibraryDoesNotFire() {
        assertFalse(PreviousQueueRule.isDetour("PL:VLone", "LIB:"))
        assertFalse(PreviousQueueRule.isDetour("PL:VLone", "LIB:LIBRARY"))
        assertFalse(PreviousQueueRule.isDetour("AP:liked", "LIB:LIBRARY"))
        assertFalse(PreviousQueueRule.isDetour("LIB:LIBRARY", "PL:VLone"))
        assertFalse(PreviousQueueRule.isDetour("LIB:LIBRARY", "AP:liked"))
    }

    /** Re-tapping the SAME list must not offer to "go back" to what is already playing. */
    @Test
    fun theSameContextOnBothSidesDoesNotFire() {
        assertFalse(PreviousQueueRule.isDetour("PL:VLone", "PL:VLone"))
        assertFalse(PreviousQueueRule.isDetour("AP:liked", "AP:liked"))
        assertFalse(PreviousQueueRule.isDetour("LIB:LIBRARY", "LIB:LIBRARY"))
        assertFalse(PreviousQueueRule.isDetour("AL:MPREb_abc", "AL:MPREb_abc"))
    }

    /**
     * The whole matrix in one place, so a future edit to the prefix lists cannot quietly flip a pair that
     * no single test happened to name. Sources across the top, destinations down the side.
     */
    @Test
    fun theWholeTransitionMatrix() {
        val listContexts = listOf("PL:VLone", "AP:liked", "LIB:LIBRARY")
        val lookupContexts = listOf("AL:MPREb_abc", "AR:UCabc", null)

        // list -> lookup: the ONLY combination that fires.
        for (from in listContexts) {
            for (to in lookupContexts) {
                assertTrue("$from -> $to must be a detour", PreviousQueueRule.isDetour(from, to))
            }
        }
        // list -> list (including itself): a deliberate switch.
        for (from in listContexts) {
            for (to in listContexts) {
                assertFalse("$from -> $to must NOT fire", PreviousQueueRule.isDetour(from, to))
            }
        }
        // lookup -> anything: there is no list waiting behind an album, an artist or a radio.
        for (from in lookupContexts) {
            for (to in listContexts + lookupContexts) {
                assertFalse("$from -> $to must NOT fire", PreviousQueueRule.isDetour(from, to))
            }
        }
    }

    /**
     * A null SOURCE is a radio, a search result, a mix — or the boot restore, whose outgoing queue is
     * EmptyQueue. Nothing identifiable to return to, and the restore must never greet him with a prompt.
     */
    @Test
    fun anAnonymousSourceQueueDoesNotFire() {
        assertFalse(PreviousQueueRule.isDetour(null, "AL:MPREb_abc"))
        assertFalse(PreviousQueueRule.isDetour(null, null))
        assertFalse(PreviousQueueRule.isDetour(null, "PL:VLone"))
    }

    /** An artist page is "se fue a buscar a otro lado" just as much as an album is. */
    @Test
    fun leavingAPlaylistForAnArtistFires() {
        assertTrue(PreviousQueueRule.isDetour("PL:VLone", "AR:UCabc"))
    }

    /** The direction matters: an album is a place he goes TO, not a list he comes back to. */
    @Test
    fun theRuleIsNotSymmetric() {
        assertTrue(PreviousQueueRule.isDetour("PL:VLone", "AL:MPREb_abc"))
        assertFalse(PreviousQueueRule.isDetour("AL:MPREb_abc", "PL:VLone"))
    }

    // ---------------------------------------------------------------------------------------------
    // 2. The offer expires
    // ---------------------------------------------------------------------------------------------

    /**
     * "Me fui a mirar otra cosa y vuelvo" is a few minutes, not a session. The exact number is a
     * judgement call, but the ORDER of magnitude is not: seconds would kill the feature (the prompt waits
     * for him to leave the screen he jumped to), and an hour would let an offer armed before the app was
     * backgrounded surface detached from anything he was doing — with a whole queue's metadata held in
     * memory meanwhile.
     */
    @Test
    fun theOfferLivesForMinutesNotForASession() {
        assertEquals(10L * 60L * 1000L, PreviousQueueRule.OFFER_TTL_MS)
        assertTrue("a bound under a minute would kill the feature", PreviousQueueRule.OFFER_TTL_MS >= 60_000L)
        assertTrue("a bound over 15 min is a session, not a detour", PreviousQueueRule.OFFER_TTL_MS <= 15L * 60L * 1000L)
    }

    /** The deadline is inclusive: at the exact millisecond it is due, the offer is already gone. */
    @Test
    fun anOfferLapsesAtItsDeadline() {
        val armedAt = 1_000_000L
        val expiresAt = armedAt + PreviousQueueRule.OFFER_TTL_MS

        assertFalse(PreviousQueueRule.hasLapsed(armedAt, expiresAt))
        assertFalse(PreviousQueueRule.hasLapsed(expiresAt - 1L, expiresAt))
        assertTrue(PreviousQueueRule.hasLapsed(expiresAt, expiresAt))
        assertTrue(PreviousQueueRule.hasLapsed(expiresAt + 1L, expiresAt))
        // A long doze: elapsedRealtime keeps counting while the phone sleeps, which is the point.
        assertTrue(PreviousQueueRule.hasLapsed(armedAt + 3_600_000L, expiresAt))
    }

    /**
     * A dismissed / never-armed offer leaves the service's deadline at 0, and every clock reading is
     * greater than that — so "no offer" reads as lapsed and can never be resumed by a stray tap.
     */
    @Test
    fun anUnarmedDeadlineIsAlreadyLapsed() {
        assertTrue(PreviousQueueRule.hasLapsed(1L, 0L))
        assertTrue(PreviousQueueRule.hasLapsed(0L, 0L))
    }

    /**
     * The shell adds [PreviousQueueRule.DISPLAY_GRACE_MS] to "now" before deciding whether to raise the
     * prompt, so a snackbar is never shown in the last seconds of the window and then refused when he
     * taps it. The grace must outlast SnackbarDuration.Long (~10 s) plus a moment to reach for it.
     */
    @Test
    fun aPromptIsNotRaisedInTheLastSecondsOfItsWindow() {
        assertTrue("must outlast SnackbarDuration.Long", PreviousQueueRule.DISPLAY_GRACE_MS >= 10_000L)
        assertTrue("but must stay a rounding error next to the TTL", PreviousQueueRule.DISPLAY_GRACE_MS < PreviousQueueRule.OFFER_TTL_MS / 10L)

        val expiresAt = 1_000_000L
        // Five seconds left: the service would still resume it, but the shell declines to ASK.
        val fiveSecondsLeft = expiresAt - 5_000L
        assertFalse(PreviousQueueRule.hasLapsed(fiveSecondsLeft, expiresAt))
        assertTrue(PreviousQueueRule.hasLapsed(fiveSecondsLeft + PreviousQueueRule.DISPLAY_GRACE_MS, expiresAt))
        // A minute left: shown, and still answerable long after the snackbar goes away.
        val oneMinuteLeft = expiresAt - 60_000L
        assertFalse(PreviousQueueRule.hasLapsed(oneMinuteLeft + PreviousQueueRule.DISPLAY_GRACE_MS, expiresAt))
    }

    // ---------------------------------------------------------------------------------------------
    // 3. Round trip: it must come back to the SAME SONG at the SAME SECOND
    // ---------------------------------------------------------------------------------------------

    /**
     * The whole capture -> restore loop the feature reuses (the same pair the boot restore uses).
     *
     * Items are empty on purpose: [MediaMetadata.toMediaItem] builds an android `Bundle` and parses a
     * `Uri`, neither of which exists in a plain JVM test. What is under test here is the CURSOR — index,
     * position, context, title — which is exactly what "que empiece por donde iba" depends on, and it is
     * carried by the same code path regardless of how many items ride along.
     */
    @Test
    fun theRoundTripPreservesTheIndexAndThePosition() {
        val original = ListQueue(
            title = "Salsa de los domingos",
            items = emptyList(),
            startIndex = 7,
            position = 96_500L,
            contextId = "PL:VLxyz123",
        )

        val persisted = original.toPersistQueue(
            title = original.title,
            items = emptyList<MediaMetadata>(),
            mediaItemIndex = 7,
            position = 96_500L,
        )
        assertEquals(7, persisted.mediaItemIndex)
        assertEquals(96_500L, persisted.position)
        assertEquals("PL:VLxyz123", persisted.contextId)

        val restored = persisted.toQueue()
        assertTrue("QueueType.LIST must round-trip back to a ListQueue", restored is ListQueue)
        restored as ListQueue
        assertEquals("the song he was on must survive", 7, restored.startIndex)
        assertEquals("the second he was at must survive", 96_500L, restored.position)
        assertEquals("PL:VLxyz123", restored.contextId)
        assertEquals("Salsa de los domingos", restored.title)
    }

    /**
     * playQueue reads the cursor off `getInitialStatus()`, not off the ListQueue fields, and feeds it
     * straight into `setMediaItems(items, index, position)`. If the status dropped the position the
     * restored queue would land on the right song at 0:00 — a silent rewind. Pin the hand-off.
     */
    @Test
    fun theRestoredQueueHandsTheCursorToPlayback() = runBlocking {
        val restored = ListQueue(
            title = null,
            items = emptyList(),
            startIndex = 4,
            position = 12_345L,
            contextId = "AP:liked",
        ).toPersistQueue(
            title = null,
            items = emptyList<MediaMetadata>(),
            mediaItemIndex = 4,
            position = 12_345L,
        ).toQueue()

        val status = restored.getInitialStatus()
        assertEquals(4, status.mediaItemIndex)
        assertEquals(12_345L, status.position)
    }

    /**
     * A resumed queue must NOT re-force shuffle. `startShuffled` is a transient intent of the button
     * that started the list; if the round trip resurrected it, coming back would scramble a queue he
     * left playing in order.
     */
    @Test
    fun resumingDoesNotResurrectTheShuffleIntent() {
        val restored = ListQueue(
            title = null,
            items = emptyList(),
            startIndex = 2,
            position = 0L,
            contextId = "PL:VLone",
            startShuffled = true,
        ).toPersistQueue(
            title = null,
            items = emptyList<MediaMetadata>(),
            mediaItemIndex = 2,
            position = 0L,
        ).toQueue()

        assertFalse(restored.startShuffled)
    }

    /**
     * The offer carries the outgoing queue's title so the prompt can name the list (null is allowed), and
     * its own DEADLINE so the shell can refuse to raise a prompt whose button the service would already
     * decline.
     */
    @Test
    fun theOfferCarriesTheTitleATokenAndItsDeadline() {
        val armedAt = 5_000L
        val offer = PreviousQueueOffer(
            token = 3L,
            title = "Salsa de los domingos",
            expiresAtElapsedRealtime = armedAt + PreviousQueueRule.OFFER_TTL_MS,
        )
        assertEquals(3L, offer.token)
        assertEquals("Salsa de los domingos", offer.title)
        assertFalse(PreviousQueueRule.hasLapsed(armedAt, offer.expiresAtElapsedRealtime))
        assertTrue(
            PreviousQueueRule.hasLapsed(
                armedAt + PreviousQueueRule.OFFER_TTL_MS,
                offer.expiresAtElapsedRealtime,
            )
        )
        assertNull(
            PreviousQueueOffer(token = 1L, title = null, expiresAtElapsedRealtime = 0L).title
        )
    }
}
