package iad1tya.echo.music.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The decision that says a list is FINISHED, and the decision that says its no-repeat memory may be
 * reset. Both used to live inline in MusicService, wired to the live player, where nothing could test
 * them — which is why this class of bug reached a seventh round.
 *
 * Two properties are pinned here:
 *  - COVERAGE is a fact about the CONTEXT, not about whatever the player happens to be holding. A size
 *    that belongs to another list must not be used, and a live timeline that no longer covers the list
 *    must not be able to finish it.
 *  - FINISHING a list and RESETTING its memory are two different events. The owner's rule needs both the
 *    list to have ended AND him to re-activate shuffle; either one alone changes nothing.
 */
class EnhancedShuffleCycleTest {

    private fun idsOf(vararg ids: String?): (Int) -> String? = { i -> ids[i] }

    // ---------------------------------------------------------------- coverage

    @Test
    fun coverageCountsOnlyWhenItDescribesTheLiveContext() {
        assertEquals(200, EnhancedShuffleCycle.coverageOf("AP:liked", "AP:liked", 200))
    }

    /**
     * The Android Auto defect. The coverage number used to be a shared field written by one code path
     * only, so a queue adopted from the car was measured against whatever list the phone had opened last.
     * An 80-song playlist's size against a 12-song car queue made the car queue impossible to finish: the
     * handoff to the infinite radio never fired and the queue kept re-shuffling songs already heard.
     */
    @Test
    fun aSizeMeasuredForAnotherListIsNotCoverage() {
        assertEquals(
            EnhancedShuffleCycle.COVERAGE_UNKNOWN,
            EnhancedShuffleCycle.coverageOf("AP:liked", "PL:some-other-playlist", 80),
        )
    }

    @Test
    fun aQueueWithNoContextHasNoCoverage() {
        assertEquals(EnhancedShuffleCycle.COVERAGE_UNKNOWN, EnhancedShuffleCycle.coverageOf(null, "AP:liked", 200))
        assertEquals(EnhancedShuffleCycle.COVERAGE_UNKNOWN, EnhancedShuffleCycle.coverageOf("AP:liked", null, 200))
        assertEquals(EnhancedShuffleCycle.COVERAGE_UNKNOWN, EnhancedShuffleCycle.coverageOf("AP:liked", "AP:liked", 0))
    }

    /**
     * The tempting shortcut — "coverage unknown, so report NOT finished" — is strictly worse than being
     * permissive, because the same reading also drives the handoff to the infinite radio. This pins the
     * deliberate choice: unknown coverage judges by the timeline and lets the list end.
     */
    @Test
    fun unknownCoverageStillLetsAListEnd() {
        assertTrue(EnhancedShuffleCycle.coversContext(12, EnhancedShuffleCycle.COVERAGE_UNKNOWN))
        assertTrue(
            EnhancedShuffleCycle.isCycleComplete(
                timelineSize = 2,
                coverageSize = EnhancedShuffleCycle.COVERAGE_UNKNOWN,
                playedIds = setOf("a", "b"),
                idAt = idsOf("a", "b"),
            )
        )
    }

    @Test
    fun aTimelineThatStillCoversTheContextIsAccepted() {
        assertTrue("exact cover", EnhancedShuffleCycle.coversContext(200, 200))
        assertTrue("radio appended on top", EnhancedShuffleCycle.coversContext(220, 200))
        assertFalse("user removed songs from the queue", EnhancedShuffleCycle.coversContext(199, 200))
    }

    // ---------------------------------------------------------------- completion

    @Test
    fun aListWhoseEverySongWasPlayedIsFinished() {
        assertTrue(
            EnhancedShuffleCycle.isCycleComplete(
                timelineSize = 3,
                coverageSize = 3,
                playedIds = setOf("a", "b", "c"),
                idAt = idsOf("a", "b", "c"),
            )
        )
    }

    @Test
    fun oneUnheardSongKeepsTheListUnfinished() {
        assertFalse(
            EnhancedShuffleCycle.isCycleComplete(
                timelineSize = 3,
                coverageSize = 3,
                playedIds = setOf("a", "c"),
                idAt = idsOf("a", "b", "c"),
            )
        )
    }

    /**
     * Registry row 94(e): judging "everything played" against the LIVE TIMELINE caused false completions.
     * Swipe away the songs you have not heard yet and the remainder reads as fully played — which used to
     * wipe the whole list's memory, and now would falsely mark the list finished.
     */
    @Test
    fun aTrimmedQueueCannotFinishAListItNeverPlayed() {
        assertFalse(
            EnhancedShuffleCycle.isCycleComplete(
                timelineSize = 2,
                coverageSize = 200,
                playedIds = setOf("a", "b"),
                idAt = idsOf("a", "b"),
            )
        )
    }

    /** An item whose id cannot be read is never proof that the list finished. */
    @Test
    fun anUnreadableItemBlocksCompletion() {
        assertFalse(
            EnhancedShuffleCycle.isCycleComplete(
                timelineSize = 3,
                coverageSize = 3,
                playedIds = setOf("a", "c"),
                idAt = idsOf("a", null, "c"),
            )
        )
    }

    @Test
    fun anEmptyTimelineIsNeverFinished() {
        assertFalse(
            EnhancedShuffleCycle.isCycleComplete(
                timelineSize = 0,
                coverageSize = 0,
                playedIds = setOf("a"),
                idAt = idsOf(),
            )
        )
    }

    /**
     * The owner's real morning: the phone killed the app overnight, he opens the car and taps
     * "Me gusta → Aleatorio". The queue never went through the in-app path, so the coverage of that list
     * has to be learned when the car's items land. With it, 200 songs heard = finished, and 199 of 200
     * still on the timeline = not finished.
     */
    @Test
    fun anExternallyAdoptedQueueIsJudgedAgainstItsOwnCoverage() {
        val ids = (0 until 200).map { "s$it" }
        val coverage = EnhancedShuffleCycle.coverageOf("AP:liked", "AP:liked", 200)

        assertTrue(
            "all 200 heard → the list is finished and the radio may take over",
            EnhancedShuffleCycle.isCycleComplete(200, coverage, ids.toSet()) { ids[it] },
        )
        assertFalse(
            "one song swiped away → the remainder must not finish the list",
            EnhancedShuffleCycle.isCycleComplete(199, coverage, ids.toSet()) { ids[it] },
        )
    }

    // ---------------------------------------------------------------- reset

    @Test
    fun finishingAListDoesNotResetItsMemory() {
        assertFalse(
            "the count must survive until he activates shuffle again",
            EnhancedShuffleCycle.shouldResetForNewCycle(isUserActivation = false, cycleComplete = true),
        )
    }

    @Test
    fun reActivatingShuffleOnAnUnfinishedListDoesNotResetItEither() {
        assertFalse(
            EnhancedShuffleCycle.shouldResetForNewCycle(isUserActivation = true, cycleComplete = false),
        )
    }

    @Test
    fun bothConditionsTogetherStartAFreshCycle() {
        assertTrue(
            EnhancedShuffleCycle.shouldResetForNewCycle(isUserActivation = true, cycleComplete = true),
        )
    }

    /**
     * Three songs added to a finished playlist make it unfinished again, and those three are exactly what
     * should play first — so re-activating shuffle must NOT wipe what he already heard. This is why
     * completion is re-derived from the persistent memory at activation time instead of being a stored
     * flag: a flag set when the list ended could not know the list changed afterwards.
     */
    @Test
    fun songsAddedAfterTheListFinishedCancelTheReset() {
        val played = setOf("a", "b", "c")
        val queue = listOf("a", "b", "c", "new1", "new2", "new3")
        val complete = EnhancedShuffleCycle.isCycleComplete(queue.size, queue.size, played) { queue[it] }

        assertFalse("the list is not finished any more", complete)
        assertFalse(
            "so the memory of a, b, c survives and only the new songs are unplayed",
            EnhancedShuffleCycle.shouldResetForNewCycle(isUserActivation = true, cycleComplete = complete),
        )
    }
}
