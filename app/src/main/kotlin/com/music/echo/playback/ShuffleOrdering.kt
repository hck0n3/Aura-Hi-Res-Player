package iad1tya.echo.music.playback

/**
 * Pure ordering helpers for the enhanced shuffle, kept free of Android/media3 types so they can be
 * unit-tested. `applyShuffleOrder` lives inside MusicService and has never had a single test — which
 * is a large part of why the same class of no-repeat bug has now been fixed five times.
 */
object ShuffleOrdering {

    /**
     * The artist key of an item whose artist is unknown (no metadata, empty name). It never collides
     * with anything and is never itself constrained — spacing must degrade to "leave it alone", never
     * to "treat every unknown as the same artist" (that would cluster them all).
     */
    const val ARTIST_UNKNOWN = 0

    /**
     * Hard cap on the gap [spaceArtists] will try to open between two songs by the same artist. Five is
     * ~17 minutes of music: past that the ear stops registering "otra vez el mismo". The cap also bounds
     * the pass's cost, which matters because it runs on the Main thread at song boundaries.
     */
    const val MAX_ARTIST_WINDOW = 5

    /** How far ahead a colliding slot may look for a better-spaced candidate. Bounds the pass to O(n·L). */
    const val ARTIST_LOOKAHEAD = 60

    /** Adjacent pairs the trace measures at the head of the order — what the user hears before the next re-apply. */
    const val ADJACENCY_SAMPLE = 50

    private val NO_SEED = IntArray(0)

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

    /**
     * How large a spacing gap it is worth ASKING for. Three independent caps, smallest wins:
     *
     *  1. **Variety on hand** — [distinctArtists] `- 1`. One artist → 0 → spacing is switched off
     *     entirely (an album or a single-artist playlist has no variety to find, and pretending otherwise
     *     would only burn CPU). Two → 1 (alternate). Three → 2, which is the gap the old pass enforced.
     *  2. **What the densest artist can physically get.** Spreading `c` songs over `n` slots as evenly as
     *     possible puts them at both ends, so the best achievable gap is `(n - 1) / (c - 1)` and the
     *     window is one less than that. THIS CAP IS NOT COSMETIC. Asking for more than the pool can give
     *     makes the result WORSE, not merely unachievable: [spaceArtists] would spend every rare artist as
     *     early as possible to satisfy the oversized gap and strand the dominant artist's leftovers in one
     *     block at the end. Concretely, on `A A A A A B C D E F` a window of 5 yields
     *     `A B C D E F A A A A` — three back-to-back pairs, WORSE than the input's clustering had to be —
     *     while the correct window of 1 yields `A B A C A D A E A F`, with none.
     *  3. **[MAX_ARTIST_WINDOW]** — past ~17 minutes the ear stops noticing, and the pass runs on the
     *     Main thread at song boundaries.
     *
     * A queue that is 90% one artist lands on window 0 through cap 2 and is left exactly as random as it
     * arrived, which is the honest answer: there is no spacing to find, and every swap would only be
     * theatre that costs battery.
     *
     * The density is measured over the WHOLE queue rather than per swap-group. Late in a lap, when the
     * unplayed remainder is much denser than the queue it came from, the window can be a little larger
     * than that remainder can honour; the pass is best-effort and simply achieves less there.
     */
    fun artistWindowFor(distinctArtists: Int, totalItems: Int, densestArtistCount: Int): Int {
        if (distinctArtists <= 1 || totalItems < 3 || densestArtistCount <= 0) return 0
        // densestArtistCount == 1 → nobody repeats at all → only caps 1 and 3 bind.
        val achievableGap =
            if (densestArtistCount <= 1) MAX_ARTIST_WINDOW + 1
            else (totalItems - 1) / (densestArtistCount - 1)
        return minOf(distinctArtists - 1, achievableGap - 1, MAX_ARTIST_WINDOW).coerceAtLeast(0)
    }

    /**
     * STABLE descending sort of [order] (timeline indices) by `keys[index]`, in place, with NO boxing.
     * Returns [order].
     *
     * ### Why this exists
     * `applyShuffleOrder` built its order with `(0 until n).toMutableList().sortByDescending { keys[it] }`.
     * That is `List<Int>` + `compareByDescending`, so it allocated `n` boxed `Integer`s for the list and a
     * boxed `Double` **per comparison** — O(n log n) short-lived objects on the Main thread, every
     * re-apply, on a queue the infinite radio only ever grows. The keys array was deliberately made a flat
     * `DoubleArray` for exactly this reason; the index list was the half that was missed.
     *
     * ### Why the ORDER is byte-identical, not merely equivalent
     * `sortByDescending { keys[it] }` is `sortWith(compareByDescending(selector))`, whose comparator is
     * `compareValues(keys[b], keys[a])` — i.e. **`java.lang.Double.compare(keys[b], keys[a])`**, because
     * `compareValues` goes through boxed `Comparable`. It is backed by `Arrays.sort(Object[], Comparator)`
     * = TimSort, which is **stable**. This is a bottom-up merge sort using that exact comparison, taking
     * the LEFT run on ties — also stable. So it agrees element-for-element, including on `NaN` (which
     * `Double.compare` orders as the largest value, unlike `<`/`>`) and on `-0.0` vs `0.0`. Proven by a
     * randomised differential test over duplicate-heavy and NaN-bearing key arrays.
     *
     * @param order timeline indices to sort. Every entry must be a valid index into [keys].
     * @param keys  `keys[timelineIndex]` = sort key. Higher sorts EARLIER.
     */
    fun sortIndicesByKeyDescending(order: IntArray, keys: DoubleArray): IntArray {
        val n = order.size
        if (n < 2) return order
        var src = order
        var dst = IntArray(n)
        var width = 1
        while (width < n) {
            var lo = 0
            while (lo < n) {
                val mid = minOf(lo + width, n)
                val hi = minOf(lo + width + width, n)
                var l = lo
                var r = mid
                var o = lo
                while (l < mid && r < hi) {
                    // <= 0 keeps the LEFT run on ties, which is what makes the merge stable.
                    dst[o++] = if (java.lang.Double.compare(keys[src[r]], keys[src[l]]) <= 0) src[l++] else src[r++]
                }
                while (l < mid) dst[o++] = src[l++]
                while (r < hi) dst[o++] = src[r++]
                lo = hi
            }
            val swap = src
            src = dst
            dst = swap
            width += width
        }
        if (src !== order) System.arraycopy(src, 0, order, 0, n)
        return order
    }

    /**
     * BEST-EFFORT artist spacing over an already-ordered [order], in place. Returns [order].
     *
     * ### What it fixes
     * The owner reported, for the third time, that shuffle "me ponía bastante seguido el mismo artista".
     * The previous pass had two structural holes:
     *
     *  1. **The current→next adjacency was never checked.** `applyShuffleOrder` spaced the sorted array
     *     and only THEN rotated the current song to slot 0 ([anchorCurrentFirst]), which manufactures a
     *     brand-new `(current, next)` pair that no comparison ever saw. That pair is the single most
     *     audible adjacency in the whole order, and it was the only unprotected one. The order is now
     *     anchored FIRST and spaced SECOND, with `startAt = 1` so slot 0 (the playing song, or a song the
     *     user picked by hand) is frozen and merely seeds the history.
     *  2. **No memory across re-applies.** The whole order is rebuilt from scratch on every mutation — a
     *     radio append, the persistent-memory seed landing, a manual jump — and with crossfade ON that is
     *     roughly once per song. Each rebuild started with an EMPTY history, so the gap was reset at every
     *     boundary and an artist could legitimately land at slots 1, 4, 7… of successive rebuilds.
     *     [seedRecent] carries the artists actually heard just before, so the gap survives a rebuild.
     *
     * ### Why "take the best available" and not "enforce a fixed gap"
     * A fixed gap fails closed: when no candidate in the lookahead satisfies it, the old pass gave up and
     * left a back-to-back pair it could easily have broken (its gap was 2, so on a dense block it
     * regularly did). This one takes the first candidate that clears [window] and otherwise the best
     * available, so it is never worse than a smaller gap and degrades continuously — a playlist that is
     * 90% one artist simply finds nothing better and keeps its random order.
     *
     * That only holds with a RIGHT-SIZED [window]; see [artistWindowFor], cap 2, for why an oversized one
     * actively harms a queue dominated by a single artist.
     *
     * ### Invariants
     *  - Result is always a PERMUTATION of the input: the pass only ever swaps, never drops or clones.
     *  - Positions below [startAt] are never touched.
     *  - A swap only ever happens between two entries in the SAME [groupKey] group, and the scan stops at
     *    the first group boundary. The caller uses that to encode "already played" vs "not yet played", so
     *    spacing can never lift an already-heard song above an unheard one — the no-repeat guarantee is
     *    structurally untouched by this pass.
     *  - `window <= 0` or fewer than 3 entries → returns immediately, order unchanged.
     *
     * @param order      shuffle order: `order[position]` = timeline index. Mutated in place.
     * @param artistKey  `artistKey[timelineIndex]` = interned primary artist, [ARTIST_UNKNOWN] if none.
     * @param groupKey   `groupKey[timelineIndex]` = swap group; swaps never cross groups.
     * @param window     gap to aim for, from [artistWindowFor].
     * @param startAt    first position the pass may move; everything before it only seeds the history.
     * @param seedRecent artists heard BEFORE this order, OLDEST first (see the class doc's point 2).
     * @param lookahead  how far ahead a colliding slot may search.
     */
    fun spaceArtists(
        order: IntArray,
        artistKey: IntArray,
        groupKey: IntArray,
        window: Int,
        startAt: Int = 0,
        seedRecent: IntArray = NO_SEED,
        lookahead: Int = ARTIST_LOOKAHEAD,
    ): IntArray {
        val n = order.size
        val begin = startAt.coerceAtLeast(0)
        if (window <= 0 || n < 3 || begin >= n || lookahead < 1) return order

        // recent[window - 1] is the MOST recent artist, recent[0] the oldest still in scope. Empty slots
        // hold ARTIST_UNKNOWN, which `distance` short-circuits, so they can never match a real artist.
        val recent = IntArray(window)

        fun push(key: Int) {
            if (key == ARTIST_UNKNOWN) return
            // Collapse consecutive duplicates: two songs in a row by the same artist carry exactly the
            // same information as one for this metric ("distance 1"), and collapsing frees a slot so an
            // older artist stays in scope instead of being shifted out by a repeat.
            if (recent[window - 1] == key) return
            for (t in 0 until window - 1) recent[t] = recent[t + 1]
            recent[window - 1] = key
        }

        /**
         * How long ago [key] was heard: 1 = the previous song, `window + 1` = out of scope (or unknown).
         * Counted in KNOWN-ARTIST songs, so a song with no artist between two by the same artist does not
         * buy that artist any distance. That errs toward more spacing, never less.
         */
        fun distance(key: Int): Int {
            if (key == ARTIST_UNKNOWN) return window + 1
            for (t in window - 1 downTo 0) {
                if (recent[t] == key) return window - t
            }
            return window + 1
        }

        fun keyAt(position: Int): Int {
            val idx = order[position]
            return if (idx >= 0 && idx < artistKey.size) artistKey[idx] else ARTIST_UNKNOWN
        }

        fun groupAt(position: Int): Int {
            val idx = order[position]
            return if (idx >= 0 && idx < groupKey.size) groupKey[idx] else Int.MIN_VALUE
        }

        for (key in seedRecent) push(key)
        for (position in 0 until begin) push(keyAt(position))

        for (i in begin until n) {
            var bestDistance = distance(keyAt(i))
            if (bestDistance <= window) {
                var bestJ = -1
                val group = groupAt(i)
                val maxJ = minOf(i + lookahead, n - 1)
                var j = i + 1
                while (j <= maxJ) {
                    if (groupAt(j) != group) break
                    val d = distance(keyAt(j))
                    if (d > bestDistance) {
                        bestDistance = d
                        bestJ = j
                        if (d > window) break // maximal — nothing beats "not heard within the window"
                    }
                    j++
                }
                if (bestJ >= 0) {
                    val tmp = order[i]
                    order[i] = order[bestJ]
                    order[bestJ] = tmp
                }
            }
            push(keyAt(i))
        }
        return order
    }

    /**
     * What the order ACTUALLY produced, measured over the head the listener will reach before the next
     * re-apply. Logged at INFO so a report of "me repite el mismo artista" can be settled from a shared
     * log instead of guessed at — see MusicService.traceShuffleSpacing.
     *
     * [gap1] counts back-to-back same-artist pairs, [gap2] pairs with exactly one song between them.
     * [minGap] is the closest any artist came to itself (0 = the stretch held no repeat at all).
     */
    data class ArtistAdjacency(
        val measured: Int,
        val distinct: Int,
        val gap1: Int,
        val gap2: Int,
        val minGap: Int,
        val topArtistCount: Int,
    )

    fun artistAdjacency(
        order: IntArray,
        artistKey: IntArray,
        from: Int = 0,
        count: Int = ADJACENCY_SAMPLE,
    ): ArtistAdjacency {
        val begin = from.coerceAtLeast(0)
        val end = minOf(order.size, begin + count)
        if (begin >= end) return ArtistAdjacency(0, 0, 0, 0, 0, 0)
        val lastSeen = HashMap<Int, Int>()
        val seenCount = HashMap<Int, Int>()
        var gap1 = 0
        var gap2 = 0
        var minGap = 0
        for (position in begin until end) {
            val idx = order[position]
            val key = if (idx >= 0 && idx < artistKey.size) artistKey[idx] else ARTIST_UNKNOWN
            if (key == ARTIST_UNKNOWN) continue
            seenCount[key] = (seenCount[key] ?: 0) + 1
            lastSeen.put(key, position)?.let { previous ->
                val gap = position - previous
                if (gap == 1) gap1++
                if (gap == 2) gap2++
                if (minGap == 0 || gap < minGap) minGap = gap
            }
        }
        return ArtistAdjacency(
            measured = end - begin,
            distinct = lastSeen.size,
            gap1 = gap1,
            gap2 = gap2,
            minGap = minGap,
            topArtistCount = seenCount.values.maxOrNull() ?: 0,
        )
    }
}
