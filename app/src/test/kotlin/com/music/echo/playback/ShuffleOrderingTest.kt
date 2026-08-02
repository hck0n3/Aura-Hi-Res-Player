package iad1tya.echo.music.playback

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The order array is fed to media3's DefaultShuffleOrder, where the NEXT song to play is order[1].
 * These tests pin the property that actually matters: anchoring the current song must never push an
 * UNPLAYED song behind an already-played one.
 */
class ShuffleOrderingTest {

    /**
     * The end-of-cycle case that made shuffle repeat. Sorted order is [U, P1..P5] (the single unplayed
     * song leads), the current song is somewhere in the played tail, and the next song to play must be
     * U — not a song that was already heard.
     */
    @Test
    fun theLastUnplayedSongIsPlayedNextInsteadOfBeingBuried() {
        val unplayed = 10
        val current = 3
        val order = intArrayOf(unplayed, 1, 2, current, 4, 5)

        ShuffleOrdering.anchorCurrentFirst(order, current)

        assertEquals("current song must be anchored at slot 0", current, order[0])
        assertEquals("the only unplayed song must be the NEXT one to play", unplayed, order[1])
    }

    /** The old swap implementation, kept as executable documentation of what it got wrong. */
    @Test
    fun theOldSwapWouldHaveBuriedIt() {
        val unplayed = 10
        val current = 3
        val order = intArrayOf(unplayed, 1, 2, current, 4, 5)

        val k = order.indexOf(current)
        val tmp = order[0]
        order[0] = order[k]
        order[k] = tmp

        assertEquals(current, order[0])
        assertEquals("the swap put an ALREADY-PLAYED song next — this was the bug", 1, order[1])
        assertEquals("…and exiled the unplayed song into the played tail", unplayed, order[k])
    }

    @Test
    fun relativeOrderOfEveryOtherEntryIsPreserved() {
        val order = intArrayOf(7, 8, 9, 4, 5, 6)
        ShuffleOrdering.anchorCurrentFirst(order, 4)
        assertArrayEquals(intArrayOf(4, 7, 8, 9, 5, 6), order)
    }

    @Test
    fun resultIsAlwaysAPermutationOfTheInput() {
        val original = intArrayOf(5, 3, 9, 1, 7, 0, 2)
        val order = original.copyOf()
        ShuffleOrdering.anchorCurrentFirst(order, 7)
        assertArrayEquals(original.sortedArray(), order.sortedArray())
        assertEquals(original.size, order.size)
    }

    @Test
    fun currentAlreadyFirstIsANoOp() {
        val order = intArrayOf(4, 7, 8, 9)
        ShuffleOrdering.anchorCurrentFirst(order, 4)
        assertArrayEquals(intArrayOf(4, 7, 8, 9), order)
    }

    /** A current index that is not in the queue (filtered out, stale) must not corrupt the order. */
    @Test
    fun missingCurrentIsANoOp() {
        val order = intArrayOf(4, 7, 8, 9)
        ShuffleOrdering.anchorCurrentFirst(order, 99)
        assertArrayEquals(intArrayOf(4, 7, 8, 9), order)
    }

    @Test
    fun singleItemAndEmptyQueuesAreSafe() {
        assertArrayEquals(intArrayOf(4), ShuffleOrdering.anchorCurrentFirst(intArrayOf(4), 4))
        assertArrayEquals(intArrayOf(), ShuffleOrdering.anchorCurrentFirst(intArrayOf(), 0))
    }

    /**
     * Full sweep: for every position the current song can occupy, the entry that led the sorted order
     * must end up as the next song to play. That is the invariant the swap violated.
     */
    @Test
    fun leadingEntryAlwaysBecomesTheNextSongWhicheverSlotTheCurrentSongOccupies() {
        for (k in 1 until 8) {
            val order = IntArray(8) { it }
            val leader = order[0]
            ShuffleOrdering.anchorCurrentFirst(order, k)
            assertEquals("current anchored (k=$k)", k, order[0])
            assertEquals("leader must play next (k=$k)", leader, order[1])
        }
    }
}
