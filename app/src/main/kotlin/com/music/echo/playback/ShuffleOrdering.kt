package iad1tya.echo.music.playback

/**
 * Pure ordering helpers for the enhanced shuffle, kept free of Android/media3 types so they can be
 * unit-tested. `applyShuffleOrder` lives inside MusicService and has never had a single test — which
 * is a large part of why the same class of no-repeat bug has now been fixed five times.
 */
object ShuffleOrdering {

    /**
     * Pins [currentIndex] at shuffle position 0 while preserving the relative order of everything else.
     *
     * media3's `DefaultShuffleOrder(shuffled, seed)` treats `shuffled[i]` as the timeline index at
     * shuffle position `i`, and `getNextIndex` returns `shuffled[indexInShuffled[current] + 1]` — so the
     * NEXT song to play is literally `order[1]`, and whatever lands there decides whether shuffle
     * repeats.
     *
     * This used to be a SWAP, which sent the entry at slot 0 to the current song's old slot. Slot 0 is
     * by construction the best UNPLAYED candidate, and the current song has just been played so its slot
     * sits deep in the played region: the swap exiled an unheard song into already-heard territory. At
     * the end of a cycle, with a single unplayed song left at slot 0, it buried that song and left
     * `order[1]` holding a played one — a repeat before the cycle closed, which also denied the
     * exhaustion handoff its trigger.
     *
     * Returns [order] itself, mutated in place.
     */
    fun anchorCurrentFirst(order: IntArray, currentIndex: Int): IntArray {
        val k = order.indexOf(currentIndex)
        if (k > 0) {
            System.arraycopy(order, 0, order, 1, k)
            order[0] = currentIndex
        }
        return order
    }
}
