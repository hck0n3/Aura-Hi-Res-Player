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
    fun unknownCandidateStaysExactlyNeutral() {
        // Registry #39/#41: GenreCache only covers known artists — an unknown artist with unknown genre
        // must score EXACTLY 0.0 so the infinite queue and the exploration quota never collapse.
        assertEquals(0.0, ContextProfile.steerTerm(salsaProfile(), listOf("Artista Nuevo"), null), 0.0)
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
        assertEquals(6.0, ContextProfile.steerTerm(rock, listOf("Bad Bunny"), "urbano latino"), 1e-9)
    }

    @Test
    fun emptyPoolBuildsNoProfile() {
        assertNull(ContextProfile.build(emptyList(), salsaGenres))
    }
}
