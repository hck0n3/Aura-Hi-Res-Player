package iad1tya.echo.music.viewmodels

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Reconciliation contract of the artist discography completion (ArtistItemsViewModel).
 *
 * Guards the two regressions that made the owner's discographies come back incomplete:
 *  1. asymmetric keys — `have`/`missing` compared flat normalized titles while the rest of the pipeline
 *     compared reconKeys, so a live edition on the YouTube page masked the studio album of the same name;
 *  2. network-probe fragility — an inconclusive quality probe counted as "missing", so a throttled run
 *     spent its whole search budget re-looking-up albums the user already had.
 */
class DiscographyKeysTest {

    private fun quality(songCount: Int, basicOk: Boolean) = AlbumQuality(songCount, basicOk)

    // ── reconKey: live/acoustic stays distinct, everything else still collapses ──

    @Test fun liveEditionIsNotTheStudioAlbum() {
        // the bug: these two must NEVER share a key, or one masks/overwrites the other
        assertNotEquals(reconKey("Lenguaje de Amor"), reconKey("Lenguaje de Amor (En Vivo)"))
    }

    @Test fun liveVariantsAllCarryTheMarker() {
        for (raw in listOf(
            "Lenguaje de Amor (En Vivo)",
            "Lenguaje de Amor - En Vivo",
            "Lenguaje de Amor (Live)",
            "Lenguaje de Amor (Acústico)",
            "Lenguaje de Amor (Unplugged)",
        )) {
            assertTrue(raw, reconKey(raw).endsWith(LIVE_MARKER))
        }
    }

    @Test fun sameRecordingStillCollapses() {
        // deluxe / remaster / EP suffixes are the SAME recording → one entry, not two
        val studio = reconKey("Lenguaje de Amor")
        assertEquals(studio, reconKey("Lenguaje de Amor (Deluxe)"))
        assertEquals(studio, reconKey("Lenguaje de Amor - Remastered"))
        assertEquals(studio, reconKey("lenguaje de amor"))
    }

    @Test fun plainKeyStripsTheMarkerForITunesLookups() {
        assertEquals(reconKey("Lenguaje de Amor"), plainKey(reconKey("Lenguaje de Amor (En Vivo)")))
    }

    // ── defect 1: have/missing must use the SAME key as grouping/assembly ──

    /** The `missing` filter, exactly as buildCompleteDiscography computes it. */
    private fun missing(youtubeHas: List<String>, itunesLists: List<String>): List<String> {
        val have = youtubeHas.map { reconKey(it) }.toSet()
        return itunesLists.filter { reconKey(it) !in have }
    }

    @Test fun studioAlbumIsStillSearchedWhenYouTubeOnlyHasTheLiveEdition() {
        // Alex Campos: the artist page lists only "Lenguaje de Amor (En Vivo)"; iTunes lists the studio one.
        val toSearch = missing(
            youtubeHas = listOf("Lenguaje de Amor (En Vivo)", "Regreso a Ti"),
            itunesLists = listOf("Lenguaje de Amor", "Regreso a Ti"),
        )
        assertEquals(listOf("Lenguaje de Amor"), toSearch)
    }

    @Test fun liveEditionIsSearchedWhenYouTubeOnlyHasTheStudioAlbum() {
        val toSearch = missing(
            youtubeHas = listOf("Lenguaje de Amor"),
            itunesLists = listOf("Lenguaje de Amor", "Lenguaje de Amor (En Vivo)"),
        )
        assertEquals(listOf("Lenguaje de Amor (En Vivo)"), toSearch)
    }

    @Test fun aTrueDuplicateIsStillNotResearched() {
        // dedupe must survive the fix: an album already listed (any edition suffix) is never re-searched
        val toSearch = missing(
            youtubeHas = listOf("Lenguaje de Amor (Deluxe Edition)"),
            itunesLists = listOf("Lenguaje de Amor"),
        )
        assertTrue(toSearch.toString(), toSearch.isEmpty())
    }

    @Test fun bothEditionsAreLookedUpWhenYouTubeHasNeither() {
        val toSearch = missing(
            youtubeHas = emptyList(),
            itunesLists = listOf("Lenguaje de Amor", "Lenguaje de Amor (En Vivo)"),
        )
        assertEquals(2, toSearch.distinctBy { reconKey(it) }.size)
    }

    // ── defect 2: an inconclusive probe must count as "have", never as "missing" ──

    @Test fun unprobedAlbumCountsAsPresent() {
        // past the 60-fetch cap → never probed → trust it, don't burn the search budget on it
        assertTrue(countsAsHave(null, 12))
    }

    @Test fun throttledProbeCountsAsPresent() {
        // YouTube throttled the album fetch → inconclusive, NOT a verdict of "truncated"
        assertTrue(countsAsHave(null, null))
    }

    @Test fun provenTruncatedAlbumStaysResearchable() {
        // the probe's positive signal is preserved: real data, real failure → eligible for re-completion
        assertFalse(countsAsHave(quality(12, false), 12))
        assertFalse(countsAsHave(quality(2, true), 12))
    }

    @Test fun goodAlbumCountsAsPresent() {
        assertTrue(countsAsHave(quality(12, true), 12))
    }

    // ── acceptance floor: the SMALLEST iTunes edition, not the largest ──

    @Test fun standardEditionPassesAgainstItsOwnTrackCount() {
        assertTrue(isComplete(quality(12, true), 12))
    }

    @Test fun standardEditionIsRejectedIfADeluxeCountIsUsedAsTheFloor() {
        // documents WHY floorTracks is the MIN across stores: with the deluxe count (24) as the floor a
        // perfectly good 12-track standard edition fails ceil(24*0.6)=15 and is thrown away
        assertFalse(isComplete(quality(12, true), 24))
        assertTrue(isComplete(quality(12, true), 12))
    }

    @Test fun unknownTrackCountDoesNotGate() {
        assertTrue(isComplete(quality(3, true), null))
        assertTrue(isComplete(quality(3, true), 0))
    }

    @Test fun halfAnAlbumIsStillRejected() {
        assertFalse(isComplete(quality(4, true), 12))
    }

    // ── defect 1: how the acceptance floor is BUILT (not just how it is applied) ──
    //
    // The tests above hand isComplete a hardcoded floor, so they say nothing about whether the pipeline ever
    // produces a usable one. It did not: normalizeTitle strips the "- Single" suffix, so iTunes' 1-track
    // "Look Up Child - Single" shared the 13-track album's key and a plain MIN pinned the floor at 1 —
    // `songCount < ceil(1 * 0.6)` rejects only an album with ZERO songs, i.e. the anti-truncation gate was
    // disabled and a 3-of-13 upload counted as complete.

    @Test fun aSingleEntryCannotCollapseTheFloorOfItsAlbum() {
        val floor = buildFloorTracks(listOf("Look Up Child" to 13, "Look Up Child - Single" to 1))
        assertEquals(13, floor.getValue("look up child"))
    }

    @Test fun theBuiltFloorActuallyRejectsATruncatedUpload() {
        // end to end: build the floor the way the pipeline does, then run the real gate against it
        val floor = buildFloorTracks(listOf("Look Up Child" to 13, "Look Up Child - Single" to 1))
        val truncated = quality(3, true) // 3 of 13 tracks, durations fine — the shape that used to slip through
        assertFalse(isComplete(truncated, floor["look up child"]))
        assertFalse(countsAsHave(truncated, floor["look up child"]))
        assertTrue(isComplete(quality(13, true), floor["look up child"]))
    }

    @Test fun theFloorIsStillTheSmallestMultiTrackEditionAcrossStores() {
        // the MIN-across-stores rule survives: a standard edition must not be judged against a deluxe count
        val floor = buildFloorTracks(
            listOf("Look Up Child" to 24, "Look Up Child" to 13, "Look Up Child - Single" to 1),
        )
        assertEquals(13, floor.getValue("look up child"))
        assertTrue(isComplete(quality(13, true), floor["look up child"]))
    }

    @Test fun anUnknownTrackCountNeverBecomesTheFloor() {
        val floor = buildFloorTracks(listOf("Look Up Child" to 0, "Look Up Child" to 10))
        assertEquals(10, floor.getValue("look up child"))
        // nothing but unknowns → 0 → isComplete does not gate at all
        assertEquals(0, buildFloorTracks(listOf("Sin Datos" to 0)).getValue("sin datos"))
        assertTrue(isComplete(quality(3, true), buildFloorTracks(listOf("Sin Datos" to 0))["sin datos"]))
    }

    @Test fun aReleaseITunesOnlyKnowsAsASingleKeepsItsOwnFloor() {
        val floor = buildFloorTracks(listOf("Solo Un Sencillo - Single" to 1))
        assertEquals(1, floor.getValue("solo un sencillo"))
    }

    @Test fun expectedTracksStillRanksByTheFullestEdition() {
        // ranking keeps the MAX — a deluxe upload must still beat a truncated one
        val expected = buildExpectedTracks(
            listOf("Look Up Child" to 13, "Look Up Child" to 24, "Look Up Child - Single" to 1),
        )
        assertEquals(24, expected.getValue("look up child"))
    }

    // ── defect 2: an album hit must have the SAME liveness as the key it was searched for ──
    //
    // Phase B searches the marker-FREE normalized title, so a lookup of "X (En Vivo)" happily matched the
    // studio album "X". The pair was returned under the live key, that key was then discarded, and Phase D
    // regrouped the item by reconKey(item.title) — into the STUDIO group. No live group was ever created,
    // and because the album search had "succeeded" the community-playlist fallback never ran.

    @Test fun aStudioAlbumCannotSatisfyALiveRequest() {
        val requested = reconKey("Lenguaje de Amor (En Vivo)")
        assertFalse(matchesLiveness("Lenguaje de Amor", requested))
        assertFalse(matchesLiveness("Lenguaje de Amor (Deluxe)", requested))
    }

    @Test fun aLiveAlbumCannotSatisfyAStudioRequest() {
        val requested = reconKey("Lenguaje de Amor")
        assertFalse(matchesLiveness("Lenguaje de Amor (En Vivo)", requested))
        assertFalse(matchesLiveness("Lenguaje de Amor En Vivo", requested))
    }

    @Test fun aGenuineStudioRequestStillMatchesAStudioAlbum() {
        val requested = reconKey("Lenguaje de Amor")
        assertTrue(matchesLiveness("Lenguaje de Amor", requested))
        assertTrue(matchesLiveness("Lenguaje de Amor (Deluxe Edition)", requested))
        assertTrue(matchesLiveness("Lenguaje de Amor - Remastered", requested))
    }

    @Test fun anAcceptedHitLandsInTheGroupThatAskedForIt() {
        // the real invariant: Phase D regroups by reconKey(item.title), so a hit is only usable if that key
        // equals the key the search was filed under. matchesLiveness is what guarantees it.
        for ((requested, hit) in listOf(
            "Lenguaje de Amor (En Vivo)" to "Lenguaje de Amor En Vivo",
            "Lenguaje de Amor (En Vivo)" to "Lenguaje de Amor (Live)",
            "Lenguaje de Amor" to "Lenguaje de Amor (Deluxe)",
        )) {
            val key = reconKey(requested)
            assertTrue("$requested <- $hit", matchesLiveness(hit, key))
            assertEquals("$requested <- $hit", key, reconKey(hit))
        }
    }

    // ── defect 3: a parenthesised "(Instrumental)" must not whitelist the studio title ──

    @Test fun aParenthesisedInstrumentalDoesNotWhitelistTheStudioTitle() {
        // normalizeTitle drops parentheticals, so keying the flag by the RAW title marked the STUDIO album's
        // key as "instrumental is genuine here" and the anti-karaoke guard filtered nothing.
        val instrumental = buildInstrumentalTitles(
            listOf("Lenguaje de Amor" to 12, "Lenguaje de Amor (Instrumental)" to 12),
        )
        assertFalse(instrumental.toString(), reconKey("Lenguaje de Amor") in instrumental)
    }

    @Test fun aGenuinelyInstrumentalReleaseIsStillWhitelisted() {
        val instrumental = buildInstrumentalTitles(
            listOf("Instrumental Worship" to 10, "Piano Karaoke Sessions" to 8, "Lenguaje de Amor" to 12),
        )
        assertTrue(instrumental.toString(), "instrumental worship" in instrumental)
        assertTrue(instrumental.toString(), "piano karaoke sessions" in instrumental)
        assertFalse(instrumental.toString(), "lenguaje de amor" in instrumental)
    }

    // ── defect 4: the separator-less live form must share the parenthesised key ──

    @Test fun theSeparatorLessLiveFormSharesTheParenthesisedKey() {
        // "X En Vivo" is YouTube Music's usual form, "X (En Vivo)" is iTunes'. Two keys for one record meant
        // it was declared missing, re-searched, and then emitted TWICE by the assembly dedupe.
        val live = reconKey("Lenguaje de Amor (En Vivo)")
        assertEquals(live, reconKey("Lenguaje de Amor En Vivo"))
        assertEquals(live, reconKey("Lenguaje de Amor - En Vivo"))
    }

    @Test fun everyLiveMarkerNormalizesToTheStudioBaseWithOrWithoutASeparator() {
        val expected = reconKey("Lenguaje de Amor") + LIVE_MARKER
        for (raw in listOf(
            // separator-less (the case the old suffix list missed entirely)
            "Lenguaje de Amor En Vivo",
            "Lenguaje de Amor En Directo",
            "Lenguaje de Amor En Concierto",
            "Lenguaje de Amor Acústico",
            "Lenguaje de Amor Acoustic",
            "Lenguaje de Amor Unplugged",
            "Lenguaje de Amor Live",
            // separators the old list also did not cover for these words
            "Lenguaje de Amor - Acústico",
            "Lenguaje de Amor: Live",
            "Lenguaje de Amor, Unplugged",
            // marker plus a venue/date clause
            "Lenguaje de Amor (En Vivo Desde Bogotá)",
            "Lenguaje de Amor En Vivo Desde Bogotá",
            "Lenguaje de Amor En Vivo 2020",
            "Lenguaje de Amor Unplugged in New York",
        )) {
            assertEquals(raw, expected, reconKey(raw))
        }
    }

    @Test fun aTitleWhoseOwnWordsLookLikeMarkersIsNotMutilated() {
        // stripping only fires when the marker FOLLOWS real title text and ENDS it, so these survive whole —
        // otherwise "Long Live the King" would collapse to "long" and collide with unrelated releases.
        assertEquals("en vivo", plainKey(reconKey("En Vivo")))
        assertEquals("directo al corazón", plainKey(reconKey("Directo al Corazón")))
        assertEquals("live your life", plainKey(reconKey("Live Your Life")))
        assertEquals("long live the king", plainKey(reconKey("Long Live the King")))
        assertEquals("el directo al corazón", plainKey(reconKey("El Directo al Corazón")))
    }

    @Test fun studioTitlesAreUnaffectedByTheLiveStrip() {
        assertEquals("lenguaje de amor", reconKey("Lenguaje de Amor"))
        assertEquals("regreso a ti", reconKey("Regreso a Ti - EP"))
        assertEquals("look up child", reconKey("Look Up Child (Deluxe)"))
        assertEquals("look up child", reconKey("Look Up Child - Single"))
    }
}
