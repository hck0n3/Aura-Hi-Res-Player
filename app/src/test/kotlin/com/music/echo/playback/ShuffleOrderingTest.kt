package iad1tya.echo.music.playback

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * The order array is fed to media3's DefaultShuffleOrder, where the NEXT song to play is order[1].
 * These tests pin the two properties that actually matter: anchoring the current song must never push an
 * UNPLAYED song behind an already-played one, and artist spacing must never lose, duplicate or freeze a
 * song while it breaks up same-artist clusters.
 */
class ShuffleOrderingTest {

    // ---------------------------------------------------------------------------------------------
    // anchorCurrentFirst
    // ---------------------------------------------------------------------------------------------

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

    // ---------------------------------------------------------------------------------------------
    // Helpers for the spacing tests
    // ---------------------------------------------------------------------------------------------

    /**
     * A queue described by its artists, one letter per song. `"AABBC"` = 5 songs; timeline index i has
     * artist `artists[i]`. `'-'` means the song has no usable artist ([ShuffleOrdering.ARTIST_UNKNOWN]).
     */
    private class Queue(spec: String) {
        val artistKey = IntArray(spec.length) { i ->
            if (spec[i] == '-') ShuffleOrdering.ARTIST_UNKNOWN else spec[i].code - 'A'.code + 1
        }
        val oneGroup = IntArray(spec.length)
        val size = spec.length
        val distinct = artistKey.filter { it != ShuffleOrdering.ARTIST_UNKNOWN }.distinct().size
        val densest = artistKey.filter { it != ShuffleOrdering.ARTIST_UNKNOWN }
            .groupingBy { it }.eachCount().values.maxOrNull() ?: 0
        val window = ShuffleOrdering.artistWindowFor(distinct, size, densest)

        /** Renders an order back into its artist string, so failures are readable. */
        fun render(order: IntArray): String = order.joinToString("") { idx ->
            if (artistKey[idx] == ShuffleOrdering.ARTIST_UNKNOWN) "-"
            else ('A' + artistKey[idx] - 1).toString()
        }
    }

    private fun space(
        q: Queue,
        order: IntArray,
        startAt: Int = 0,
        seed: IntArray = IntArray(0),
        groups: IntArray = q.oneGroup,
        window: Int = q.window,
    ): IntArray = ShuffleOrdering.spaceArtists(
        order = order,
        artistKey = q.artistKey,
        groupKey = groups,
        window = window,
        startAt = startAt,
        seedRecent = seed,
    )

    /** THE invariant: whatever spacing does, the same songs must come out — all of them, once each. */
    private fun assertPermutationOf(input: IntArray, output: IntArray) {
        assertEquals("spacing must not change the queue length", input.size, output.size)
        assertArrayEquals(
            "spacing must not drop or duplicate a song (in=${input.toList()} out=${output.toList()})",
            input.sortedArray(),
            output.sortedArray(),
        )
    }

    private fun backToBackPairs(q: Queue, order: IntArray): Int =
        ShuffleOrdering.artistAdjacency(order, q.artistKey, 0, order.size).gap1

    // ---------------------------------------------------------------------------------------------
    // artistWindowFor
    // ---------------------------------------------------------------------------------------------

    @Test
    fun aSingleArtistQueueAsksForNoSpacingAtAll() {
        assertEquals(0, ShuffleOrdering.artistWindowFor(distinctArtists = 1, totalItems = 40, densestArtistCount = 40))
        assertEquals(0, ShuffleOrdering.artistWindowFor(distinctArtists = 0, totalItems = 40, densestArtistCount = 0))
    }

    @Test
    fun theWindowNeverExceedsWhatTheDensestArtistCanPhysicallyGet() {
        // 5 of 10 songs by one artist: the best possible gap is 2 (A_A_A_A_A), so the window is 1.
        assertEquals(1, ShuffleOrdering.artistWindowFor(distinctArtists = 6, totalItems = 10, densestArtistCount = 5))
        // 9 of 10: adjacent pairs are unavoidable → do not even try.
        assertEquals(0, ShuffleOrdering.artistWindowFor(distinctArtists = 2, totalItems = 10, densestArtistCount = 9))
    }

    @Test
    fun aDiverseLibrarySaturatesAtTheCap() {
        assertEquals(
            ShuffleOrdering.MAX_ARTIST_WINDOW,
            ShuffleOrdering.artistWindowFor(distinctArtists = 80, totalItems = 300, densestArtistCount = 20),
        )
    }

    @Test
    fun twoArtistsAlternate() {
        assertEquals(1, ShuffleOrdering.artistWindowFor(distinctArtists = 2, totalItems = 10, densestArtistCount = 5))
    }

    // ---------------------------------------------------------------------------------------------
    // spaceArtists — the cases the owner actually listens to
    // ---------------------------------------------------------------------------------------------

    /**
     * A normal mixed library: 12 artists, 5 songs each, handed to the pass in the worst possible order
     * (fully clustered by artist). No randomness — this is deterministic and must stay green forever.
     */
    @Test
    fun aNormalLibraryComesOutWithNoBackToBackRepeats() {
        val spec = buildString { for (a in 0 until 12) repeat(5) { append('A' + a) } }
        val q = Queue(spec)
        val input = IntArray(q.size) { it }
        val order = input.copyOf()

        space(q, order)

        assertPermutationOf(input, order)
        assertEquals(ShuffleOrdering.MAX_ARTIST_WINDOW, q.window)
        // The tail of any pool runs out of variety; the head — everything the listener actually reaches
        // before the next re-apply — must be clean.
        val head = ShuffleOrdering.artistAdjacency(order, q.artistKey, 0, 40)
        assertEquals("no back-to-back repeats in the head: ${q.render(order)}", 0, head.gap1)
        assertTrue("head must respect the window: ${q.render(order)}", head.minGap > ShuffleOrdering.MAX_ARTIST_WINDOW)
    }

    /** An album or a one-artist playlist: nothing to space. It must come back byte-identical. */
    @Test
    fun aSingleArtistPlaylistIsLeftExactlyAsItArrived() {
        val q = Queue("AAAAAAAA")
        val input = intArrayOf(5, 2, 7, 0, 3, 6, 1, 4)
        val order = input.copyOf()

        space(q, order)

        assertEquals("a single-artist queue must not even ask for spacing", 0, q.window)
        assertArrayEquals("the random order must survive untouched", input, order)
        assertPermutationOf(input, order)
    }

    /** Two songs: there is no third slot to move anything into. Must not crash, must not reorder. */
    @Test
    fun aTwoSongPlaylistIsLeftAlone() {
        val q = Queue("AA")
        val input = intArrayOf(1, 0)
        val order = input.copyOf()
        space(q, order)
        assertArrayEquals(input, order)

        val mixed = Queue("AB")
        val mixedInput = intArrayOf(1, 0)
        val mixedOrder = mixedInput.copyOf()
        space(mixed, mixedOrder)
        assertArrayEquals("fewer than 3 entries → nothing to do", mixedInput, mixedOrder)
        assertPermutationOf(mixedInput, mixedOrder)
    }

    /**
     * An artist that is 50% of the list. This is the case that caught a defect INSIDE the first version
     * of this fix: a greedy pass with an oversized window spends every rare artist up front and strands
     * the dominant artist's leftovers in one block at the end — `A B C D E F A A A A`, WORSE than what it
     * was asked to fix. The window cap is what prevents that, so this test pins the outcome, not just
     * "no crash".
     */
    @Test
    fun anArtistThatIsHalfTheListEndsUpAlternatingInsteadOfBlockedAtTheEnd() {
        val q = Queue("AAAAABCDEF")
        val input = IntArray(q.size) { it }
        val order = input.copyOf()

        space(q, order)

        assertPermutationOf(input, order)
        assertEquals("half the list by one artist → the achievable gap is 2, i.e. window 1", 1, q.window)
        assertEquals(
            "the dominant artist must alternate, not be dumped at the end: ${q.render(order)}",
            "ABACADAEAF",
            q.render(order),
        )
        assertEquals(0, backToBackPairs(q, order))
    }

    /**
     * An artist that is 90% of the list. Spacing is impossible, so the honest answer is to leave the
     * random order alone — never to fake variety, drop songs, or spin over the whole queue for nothing.
     */
    @Test
    fun anArtistThatIsNinetyPercentOfTheListKeepsItsRandomOrder() {
        val q = Queue("AAAAAAAAAB")
        val input = intArrayOf(4, 9, 1, 7, 0, 3, 8, 2, 6, 5)
        val order = input.copyOf()

        space(q, order)

        assertEquals("nothing achievable → do not even try", 0, q.window)
        assertArrayEquals("a 90%-one-artist queue must stay exactly as random as it arrived", input, order)
        assertPermutationOf(input, order)
    }

    /**
     * Unknown artists must never be treated as one big artist — that would cluster every song with no
     * metadata together, inventing the exact bug this pass exists to remove.
     */
    @Test
    fun songsWithNoArtistAreNeverTreatedAsSharingOne() {
        val q = Queue("----AA--BB--")
        val input = IntArray(q.size) { it }
        val order = input.copyOf()

        space(q, order)

        assertPermutationOf(input, order)
        // The four unknown-artist songs may sit anywhere, including next to each other; what must NOT
        // happen is a real artist landing back-to-back with itself.
        assertEquals("real artists must still be spaced: ${q.render(order)}", 0, backToBackPairs(q, order))
    }

    // ---------------------------------------------------------------------------------------------
    // spaceArtists — the invariants that protect the no-repeat guarantee
    // ---------------------------------------------------------------------------------------------

    /**
     * THE guarantee the owner already confirmed works and that must not regress: a played song may never
     * be lifted above an unplayed one. Spacing enforces that structurally by refusing to swap across
     * groups, so here the whole unplayed block (group 0) must still precede the played block (group 1).
     */
    @Test
    fun spacingNeverLiftsAPlayedSongAboveAnUnplayedOne() {
        val q = Queue("AAABBBCCC")
        // Indices 0..4 not yet played, 5..8 already played — the layout applyShuffleOrder produces.
        val groups = IntArray(q.size) { if (it < 5) 0 else 1 }
        val input = IntArray(q.size) { it }
        val order = input.copyOf()

        space(q, order, groups = groups, window = 2)

        assertPermutationOf(input, order)
        val firstPlayedSlot = order.indexOfFirst { groups[it] == 1 }
        val lastUnplayedSlot = order.indexOfLast { groups[it] == 0 }
        assertTrue(
            "every unplayed song must still come before every played one (order=${order.toList()})",
            lastUnplayedSlot < firstPlayedSlot,
        )
    }

    /**
     * The head fix. In production the current song is anchored at slot 0 and `startAt = 1`, so slot 0 is
     * frozen — a song the user picked by hand always stays the one that is playing — while still
     * constraining its successor. Before this, spacing ran BEFORE the anchor and the `(current, next)`
     * pair was the one adjacency nobody ever checked.
     */
    @Test
    fun theSongAfterTheCurrentOneIsNoLongerChosenBlind() {
        val q = Queue("AABCD")
        // Slot 0 is the current song (artist A); the naive order puts another A right behind it.
        val order = intArrayOf(0, 1, 2, 3, 4)
        val input = order.copyOf()

        space(q, order, startAt = 1)

        assertPermutationOf(input, order)
        assertEquals("the current song must stay put", 0, order[0])
        assertTrue(
            "the NEXT song must not be by the artist that is playing right now: ${q.render(order)}",
            q.artistKey[order[1]] != q.artistKey[order[0]],
        )
    }

    /**
     * The cross-rebuild fix. applyShuffleOrder rebuilds the whole order on every mutation, so without a
     * seeded history the gap silently restarts at each rebuild. Here the queue itself holds no clue that
     * artist A was just heard — only [seedRecent] does.
     */
    @Test
    fun artistsHeardBeforeThisRebuildStillConstrainTheNewOrder() {
        val q = Queue("ABCD")
        val order = intArrayOf(0, 1, 2, 3) // index 0 is artist A, which was just playing
        val input = order.copyOf()
        val artistA = q.artistKey[0]

        space(q, order, seed = intArrayOf(artistA), window = 2)

        assertPermutationOf(input, order)
        assertTrue(
            "an artist heard immediately before the rebuild must not open it: ${q.render(order)}",
            q.artistKey[order[0]] != artistA,
        )
    }

    @Test
    fun aZeroWindowIsAlwaysANoOp() {
        val q = Queue("AABBCC")
        val input = intArrayOf(3, 0, 5, 1, 4, 2)
        val order = input.copyOf()
        space(q, order, window = 0)
        assertArrayEquals(input, order)
    }

    /**
     * Fuzz: 400 randomly composed queues, random artist densities, random incoming orders. Nothing may
     * ever be lost, duplicated or moved out of its group, and the pass must always terminate.
     */
    @Test
    fun spacingIsAlwaysAPermutationAndAlwaysGroupSafeAcrossRandomQueues() {
        val rnd = Random(20260803)
        repeat(400) { iteration ->
            val size = 1 + rnd.nextInt(60)
            val artistCount = 1 + rnd.nextInt(6)
            val spec = buildString {
                repeat(size) {
                    // ~1 in 10 songs has no usable artist.
                    append(if (rnd.nextInt(10) == 0) '-' else ('A' + rnd.nextInt(artistCount)))
                }
            }
            val q = Queue(spec)
            // Groups must be contiguous blocks, exactly as applyShuffleOrder builds them.
            val groups = IntArray(size) { if (rnd.nextBoolean()) 0 else 1 }
            groups.sort()
            val input = (0 until size).shuffled(java.util.Random(iteration.toLong())).toIntArray()
            val order = input.copyOf()

            space(q, order, startAt = if (rnd.nextBoolean()) 1 else 0, groups = groups)

            assertPermutationOf(input, order)
            // Group membership per slot is untouched: a swap may only trade two entries of one group.
            assertArrayEquals(
                "iteration $iteration: an entry crossed a group boundary (spec=$spec)",
                input.map { groups[it] }.toIntArray(),
                order.map { groups[it] }.toIntArray(),
            )
        }
    }

    /**
     * Spacing must not collapse into a fixed pattern. With plenty of variety, two different incoming
     * random orders must still produce two different outputs — otherwise "shuffle" would be a rotation.
     */
    @Test
    fun spacingPreservesRandomnessRatherThanImposingOneFixedOrder() {
        val spec = buildString { for (a in 0 until 10) repeat(4) { append('A' + a) } }
        val q = Queue(spec)
        val first = (0 until q.size).shuffled(java.util.Random(1)).toIntArray()
        val second = (0 until q.size).shuffled(java.util.Random(2)).toIntArray()

        space(q, first)
        space(q, second)

        assertTrue("two random inputs must not converge on the same order", !first.contentEquals(second))
    }

    // ---------------------------------------------------------------------------------------------
    // artistAdjacency — the trace the owner will read
    // ---------------------------------------------------------------------------------------------

    @Test
    fun theTraceReportsTheClusteringItActuallySees() {
        val q = Queue("AABCA")
        val adj = ShuffleOrdering.artistAdjacency(intArrayOf(0, 1, 2, 3, 4), q.artistKey, 0, 5)
        assertEquals(5, adj.measured)
        assertEquals("A, B, C", 3, adj.distinct)
        assertEquals("A at slots 0 and 1", 1, adj.gap1)
        assertEquals(0, adj.gap2)
        assertEquals(1, adj.minGap)
        assertEquals("A appears 3 times", 3, adj.topArtistCount)
    }

    @Test
    fun theTraceReportsACleanStretchAsClean() {
        val q = Queue("ABCDEF")
        val adj = ShuffleOrdering.artistAdjacency(intArrayOf(0, 1, 2, 3, 4, 5), q.artistKey, 0, 6)
        assertEquals(0, adj.gap1)
        assertEquals(0, adj.gap2)
        assertEquals("no artist repeated at all", 0, adj.minGap)
        assertEquals(1, adj.topArtistCount)
    }

    @Test
    fun theTraceIsSafeOnAnEmptyOrShortOrder() {
        val q = Queue("AB")
        assertEquals(0, ShuffleOrdering.artistAdjacency(IntArray(0), q.artistKey).measured)
        assertEquals(2, ShuffleOrdering.artistAdjacency(intArrayOf(0, 1), q.artistKey).measured)
    }

    // ---------------------------------------------------------------------------------------------
    // sortIndicesByKeyDescending — the boxing-free replacement for applyShuffleOrder's
    // `(0 until n).toMutableList().sortByDescending { keys[it] }`.
    //
    // This is a PERFORMANCE change to code that decides what the user hears next, so "equivalent" is not
    // good enough: the differential tests below assert the output is IDENTICAL, element for element,
    // against the exact expression that was replaced.
    // ---------------------------------------------------------------------------------------------

    /** The expression applyShuffleOrder used before the change — the reference implementation. */
    private fun referenceSort(n: Int, keys: DoubleArray): IntArray =
        (0 until n).toMutableList().sortedByDescending { keys[it] }.toIntArray()

    private fun assertSameAsReference(keys: DoubleArray, what: String) {
        val n = keys.size
        val actual = ShuffleOrdering.sortIndicesByKeyDescending(IntArray(n) { it }, keys)
        assertArrayEquals(what, referenceSort(n, keys), actual)
    }

    @Test
    fun theSortMatchesTheOldComparatorOnRandomQueues() {
        val rnd = Random(20260805)
        // Queue sizes from degenerate to bigger than any real queue, incl. the merge-sort width edges.
        for (n in listOf(0, 1, 2, 3, 4, 5, 7, 8, 9, 16, 17, 31, 32, 33, 100, 999, 4096)) {
            repeat(5) { round ->
                val keys = DoubleArray(n) {
                    // The real key: capped taste nudge + uniform random, +1000 for unplayed items.
                    val base = rnd.nextDouble(-1.7, 1.7) * 0.15 + rnd.nextDouble()
                    if (rnd.nextBoolean()) base + 1000.0 else base
                }
                assertSameAsReference(keys, "n=$n round=$round")
            }
        }
    }

    @Test
    fun theSortIsStableOnDuplicateKeysExactlyLikeTimSort() {
        val rnd = Random(7)
        // Ties are what stability is about: a coarse key set forces many of them, so any difference in
        // tie-breaking between the merge sort and TimSort shows up immediately.
        for (n in listOf(2, 3, 8, 33, 250, 1000)) {
            repeat(10) { round ->
                val keys = DoubleArray(n) { rnd.nextInt(0, 3).toDouble() }
                assertSameAsReference(keys, "duplicate-heavy n=$n round=$round")
            }
        }
        // Every key identical → the order must come out completely untouched (0, 1, 2, ...).
        val flat = DoubleArray(50) { 1000.5 }
        assertArrayEquals(IntArray(50) { it }, ShuffleOrdering.sortIndicesByKeyDescending(IntArray(50) { it }, flat))
    }

    @Test
    fun theSortAgreesOnNaNAndSignedZeroToo() {
        // compareByDescending goes through boxed Comparable, i.e. java.lang.Double.compare, which orders
        // NaN as the LARGEST value and -0.0 below 0.0 — unlike `<`/`>`. A hand-rolled comparison that
        // used those operators would silently disagree here, so pin it.
        val keys = doubleArrayOf(
            0.0, Double.NaN, -0.0, 1.0, Double.NaN, -1.0, 1000.0, Double.NaN, 0.0, -0.0,
            Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY,
        )
        assertSameAsReference(keys, "NaN / signed zero")
    }

    @Test
    fun theSortIsAPermutationAndSortsDescending() {
        val rnd = Random(99)
        val n = 500
        val keys = DoubleArray(n) { rnd.nextDouble() }
        val out = ShuffleOrdering.sortIndicesByKeyDescending(IntArray(n) { it }, keys)
        assertEquals("no index lost or duplicated", n, out.toSet().size)
        for (i in 1 until n) {
            assertTrue("descending at $i", keys[out[i - 1]] >= keys[out[i]])
        }
    }

    /**
     * The whole point of the +1000 offset: already-played songs must stay behind every unplayed one after
     * the sort, whatever their taste term is. Pinned here because this is the invariant the replacement
     * sort could most plausibly have broken.
     */
    @Test
    fun playedSongsStayBehindUnplayedOnesAfterTheSort() {
        val rnd = Random(4242)
        val n = 400
        val unplayed = BooleanArray(n) { rnd.nextBoolean() }
        val keys = DoubleArray(n) {
            val base = rnd.nextDouble(-1.7, 1.7) * 0.15 + rnd.nextDouble()
            if (unplayed[it]) base + 1000.0 else base
        }
        val out = ShuffleOrdering.sortIndicesByKeyDescending(IntArray(n) { it }, keys)
        var seenPlayed = false
        for (idx in out) {
            if (!unplayed[idx]) seenPlayed = true
            else assertTrue("an unplayed song sorted behind a played one", !seenPlayed)
        }
    }
}
