package iad1tya.echo.music.reco

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Genre-aware infinite queue — the pure math of [ContextProfile]: build/coverage/genre-share, the
 * minimum-signal activation gate, and the BOUNDED steering term (never a filter; unknown = neutral).
 * The fake [genres] maps mirror a GenreCache snapshot: lowercased artist -> iTunes primary genre.
 */
class ContextProfileTest {
    private fun track(artist: String, title: String = "Track de $artist") =
        ContextProfile.Track(artists = listOf(artist), title = title)

    private val salsaGenres = mapOf(
        "marc anthony" to "Salsa y Tropical",
        "willie colon" to "Salsa y Tropical",
        "grupo niche" to "Salsa y Tropical",
    )

    /** A fully known, pure-salsa context: the owner's playlist case. */
    private fun salsaProfile(): ContextProfile.Profile =
        ContextProfile.build(
            listOf(track("Marc Anthony"), track("Willie Colon"), track("Grupo Niche")),
            salsaGenres,
        )!!

    @Test
    fun buildProfilesASalsaPlaylist() {
        val pool = listOf(
            track("Marc Anthony"),
            track("Willie Colon"),
            track("Grupo Niche"),
            track("Artista Desconocido"), // not in the cache → contributes no genre
        )
        val p = ContextProfile.build(pool, salsaGenres)!!
        // Shares are among KNOWN tracks (sum to 1.0); coverage is honest about the unknown quarter.
        assertEquals(1.0, p.genreShare["salsa y tropical"]!!, 1e-9)
        assertEquals(0.75, p.coverage, 1e-9)
        assertEquals(3, p.knownArtists)
        // Every artist (known or not) is in the cache-free membership set, lowercased.
        assertTrue("artista desconocido" in p.artistSet)
        assertTrue("marc anthony" in p.artistSet)
        assertEquals(ContextProfile.LANG_ES, p.languageHint)
        assertTrue(p.active)
    }

    @Test
    fun genreShareMathOnMixedPool() {
        val genres = salsaGenres + mapOf(
            "hector lavoe" to "Salsa y Tropical",
            "queen" to "Rock",
        )
        val pool = listOf(
            track("Marc Anthony"), track("Willie Colon"), track("Grupo Niche"),
            track("Hector Lavoe"), track("Hector Lavoe", "Otro tema"), track("Marc Anthony", "Otro tema"),
            track("Queen"), track("Queen", "Other song"),
            track("Desconocido Uno"), track("Desconocido Dos"),
        )
        val p = ContextProfile.build(pool, genres)!!
        assertEquals(0.75, p.genreShare["salsa y tropical"]!!, 1e-9) // 6 of 8 known
        assertEquals(0.25, p.genreShare["rock"]!!, 1e-9)             // 2 of 8 known
        assertEquals(0.8, p.coverage, 1e-9)                          // 8 of 10 tracks
        assertEquals(ContextProfile.LANG_ES, p.languageHint)         // salsa majority (0.75 > 0.5)
    }

    @Test
    fun coverageGateBlocksLowSignal() {
        // 1 known track of 10 → coverage 0.1 < 0.3 AND knownArtists 1 < 3 → inactive.
        val pool = listOf(track("Marc Anthony")) + (1..9).map { track("Desconocido $it") }
        val p = ContextProfile.build(pool, salsaGenres)!!
        assertFalse(p.active)
        // An inactive profile steers NOTHING — even a known off-context genre stays exactly neutral.
        assertEquals(0.0, ContextProfile.steerTerm(p, listOf("Somebody"), "rock"), 0.0)
    }

    @Test
    fun knownArtistsAlternativeGateActivates() {
        // 3 known of 11 → coverage ~0.27 < 0.3, but 3 DISTINCT known artists → active via the artist gate.
        val pool = listOf(track("Marc Anthony"), track("Willie Colon"), track("Grupo Niche")) +
            (1..8).map { track("Desconocido $it") }
        val p = ContextProfile.build(pool, salsaGenres)!!
        assertTrue(p.coverage < ContextProfile.MIN_COVERAGE)
        assertEquals(3, p.knownArtists)
        assertTrue(p.active)
    }

    @Test
    fun steerTermPullsContextArtistsAndGenres() {
        val p = salsaProfile()
        // Context artist (case-insensitive), genre unknown → the cache-free artist pull, exactly -3.
        assertEquals(-3.0, ContextProfile.steerTerm(p, listOf("MARC ANTHONY"), null), 1e-9)
        // Unknown artist, dominant context genre (share 1.0, Spanish) → -4*1.0 - 1 clamped to STEER_MIN.
        assertEquals(ContextProfile.STEER_MIN, ContextProfile.steerTerm(p, listOf("La India"), "salsa y tropical"), 1e-9)
        // KNOWN off-context genre → the bounded push, exactly +6 (a demotion, never a drop).
        assertEquals(ContextProfile.STEER_MAX, ContextProfile.steerTerm(p, listOf("Queen"), "rock"), 1e-9)
    }

    @Test
    fun unknownGenreLosesExactlyOneRankNotMore() {
        // This USED to be "exactly 0.0", and that was the hole behind the owner's intermittent genre
        // mixing: an unknown candidate scored 0.0 while an on-genre one earned -4, so a wrong-genre song
        // at relatedness rank 2 beat a right-genre song at rank 8. The nudge is ONE — the sort key's
        // `index` term steps by exactly 1.0 per rank, so this is a tie-break and nothing more.
        val p = salsaProfile()
        assertEquals(
            ContextProfile.UNKNOWN_GENRE_PUSH,
            ContextProfile.steerTerm(p, listOf("Artista Nuevo"), null),
            1e-9,
        )
        // Registry #39/#41 holds: it is 6x smaller than the KNOWN off-genre push, so "we don't know" is
        // never treated as "wrong genre"...
        assertTrue(
            ContextProfile.steerTerm(p, listOf("Artista Nuevo"), null) <
                ContextProfile.steerTerm(p, listOf("Queen"), "rock"),
        )
        // ...and a candidate BY A CONTEXT ARTIST is matched cache-free, so the push can never fire on
        // the collection's own artists however blind the cache is.
        assertEquals(-3.0, ContextProfile.steerTerm(p, listOf("Marc Anthony"), null), 1e-9)
    }

    @Test
    fun aProfileThatKnowsNoGenresStillSteersNothing() {
        // COLLAPSE GUARD. A pool the cache knows nothing about fails the activation gate outright, so it
        // steers nothing through [Profile.active]...
        val blind = ContextProfile.build(
            listOf(track("Desconocido 1"), track("Desconocido 2"), track("Desconocido 3")),
            emptyMap(),
        )!!
        assertFalse(blind.active)
        assertTrue(blind.genreShare.isEmpty())
        assertEquals(0.0, ContextProfile.steerTerm(blind, listOf("Artista Nuevo"), null), 0.0)
        // ...and the SECOND, independent guard inside steerTerm holds even for a hand-built profile that
        // claims to be active with no genre signal at all: with nothing known, nothing is pushed.
        val activeButBlind = ContextProfile.Profile(
            artistSet = setOf("alguien"),
            genreShare = emptyMap(),
            coverage = 1.0,
            knownArtists = 0,
            languageHint = null,
        )
        assertTrue(activeButBlind.active)
        assertEquals(0.0, ContextProfile.steerTerm(activeButBlind, listOf("Artista Nuevo"), null), 0.0)
        assertEquals(0.0, ContextProfile.steerTerm(activeButBlind, listOf("Queen"), "rock"), 0.0)
    }

    @Test
    fun unknownGenreIsStillABoundedNudgeNotAFilter() {
        // A batch where NOTHING is known comes out in YouTube's own relatedness order: every candidate
        // takes the SAME +1, so no candidate is reordered relative to another and none is removed.
        val p = salsaProfile()
        val batch = listOf("Nuevo A", "Nuevo B", "Nuevo C")
        val terms = batch.map { ContextProfile.steerTerm(p, listOf(it), null) }
        assertEquals(1, terms.distinct().size)
        // And it stays inside the documented clamp, like every other term.
        terms.forEach { assertTrue(it in ContextProfile.STEER_MIN..ContextProfile.STEER_MAX) }
    }

    @Test
    fun languageHintSoftensOffGenreLatinCandidates() {
        // Spanish context + a KNOWN Spanish-proving genre that is NOT in the context → +6 - 1 = +5:
        // the weak tie-break keeps same-language music a little closer, never punishes, never filters.
        assertEquals(5.0, ContextProfile.steerTerm(salsaProfile(), listOf("Bad Bunny"), "urbano latino"), 1e-9)
        // An English-flavoured context has no hint → no tie-break anywhere.
        val rockPool = listOf(track("Queen"), track("Queen", "Two"), track("Queen", "Three"))
        val rock = ContextProfile.build(rockPool, mapOf("queen" to "Rock"))!!
        assertNull(rock.languageHint)
        assertEquals(7.0, ContextProfile.steerTerm(rock, listOf("Bad Bunny"), "urbano latino"), 1e-9)
    }

    @Test
    fun unknownGenreNeverBuysAnExplorationBlock() {
        // REGISTRY #39/#41, the exact collision: profile non-empty (so the +1 unknown push is live) and a
        // candidate whose genre we do NOT know — an artist the radio surfaces for the FIRST time is
        // unknown BY CONSTRUCTION, because GenreCache only ever fills with artists already around the
        // user. The steer may push it back by one rank, but it must STAY eligible for the exploration
        // reserve; blocking on absence emptied the fresh partition on a cold cache and narrowed the
        // infinite radio onto artists already in the taste profile.
        val p = salsaProfile()
        assertTrue(p.genreShare.isNotEmpty())
        val steer = ContextProfile.steerTerm(p, listOf("Artista Nuevo"), null)
        assertEquals(ContextProfile.UNKNOWN_GENRE_PUSH, steer, 1e-9) // it IS pushed back...
        assertFalse(ContextProfile.blocksExploration(steer, null))   // ...and it is STILL explorable.
        // Neither does a candidate the steer PULLED forward (context genre / context artist).
        assertFalse(
            ContextProfile.blocksExploration(
                ContextProfile.steerTerm(p, listOf("La India"), "salsa y tropical"), "salsa y tropical",
            ),
        )
        assertFalse(
            ContextProfile.blocksExploration(ContextProfile.steerTerm(p, listOf("Marc Anthony"), null), null),
        )
    }

    @Test
    fun knownOffContextGenreStillBlocksExploration() {
        // The owner's original "deja de mezclar géneros" fix must NOT be undone by the line above: a genre
        // we can NAME and know to be off-context is still kept out of the reserved 1-in-5 slots, so the
        // quota cannot hand the front of the batch back to what the steer just demoted.
        val p = salsaProfile()
        assertTrue(ContextProfile.blocksExploration(ContextProfile.steerTerm(p, listOf("Queen"), "rock"), "rock"))
        // Also when the weak Spanish tie-break softens it (+6 - 1 = +5) — still a known wrong genre.
        val softened = ContextProfile.steerTerm(p, listOf("Bad Bunny"), "urbano latino")
        assertEquals(5.0, softened, 1e-9)
        assertTrue(ContextProfile.blocksExploration(softened, "urbano latino"))
    }

    @Test
    fun emptyPoolBuildsNoProfile() {
        assertNull(ContextProfile.build(emptyList(), salsaGenres))
    }
}
