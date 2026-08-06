package iad1tya.echo.music.ui.player

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The classic queue sheet's autoplay/related rows (`AutomixSongRow` in `Queue.kt`) used to hand
 * `MusicService.playNextAutomix` / `addToQueueAutomix` the COMPOSITION index of the row. Both of
 * those do an unchecked `automixItems.value.toMutableList().apply { removeAt(position) }`, and the
 * pool is mutated from OUTSIDE the sheet while it is open (the player auto-queues `automix[0]` when
 * the queue runs dry; the service re-publishes the whole list on every track change).
 *
 * So a tap that landed between the pool shrinking and the row recomposing removed the WRONG song —
 * the tapped one survived and could be auto-queued a second time — and a tap on the last row after
 * a shrink called `removeAt(size)`, an IndexOutOfBoundsException thrown on the main thread from
 * inside a click handler, i.e. a crash.
 *
 * [automixPoolPosition] is the fix: resolve by identity against the pool AS IT IS AT CLICK TIME,
 * and let the caller skip when the answer is `-1`. The assertions below fail if anyone goes back to
 * trusting the row index.
 */
class AutomixPoolPositionTest {

    /** The pool as the rows were composed against it. */
    private val composed = listOf("a", "b", "c", "d")

    @Test
    fun `resolves the tapped song after the pool shifted underneath the row`() {
        // The player consumed the head while the sheet was open: every row's index is now off by one.
        val live = composed.drop(1) // b, c, d

        val rowIndexOfC = composed.indexOf("c") // 2 — what the old code passed
        assertEquals(1, automixPoolPosition(live, "c"))

        // Pinning the actual damage: the stale index points at a DIFFERENT song, so the old code
        // removed "d" and left "c" in the pool.
        assertEquals("d", live[rowIndexOfC])
    }

    @Test
    fun `returns -1 instead of an out-of-bounds index when the song already left the pool`() {
        val live = listOf("c", "d")

        assertEquals(-1, automixPoolPosition(live, "a"))
        assertEquals(-1, automixPoolPosition(emptyList(), "a"))
    }

    @Test
    fun `the stale last-row index that used to crash is out of bounds for the live pool`() {
        val live = composed.dropLast(1) // a, b, c

        val staleLastIndex = composed.lastIndex // 3 → removeAt(3) on a size-3 list = crash
        assertEquals(live.size, staleLastIndex)
        assertEquals(2, automixPoolPosition(live, "c"))
    }

    @Test
    fun `an unshifted pool still resolves to the row index, so the normal case is unchanged`() {
        composed.forEachIndexed { index, id ->
            assertEquals(index, automixPoolPosition(composed, id))
        }
    }
}
