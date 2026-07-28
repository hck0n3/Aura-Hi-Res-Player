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

    // ── defect 6: "- EP" collapses the floor exactly like "- Single" did ──
    //
    // normalizeTitle strips "- EP" too, so a 4-track "X - EP" shared the 12-track album's key. "More than
    // one track" let it through and the floor became 4 → ceil(4*0.6)=3, so a 3-of-12 TRUNCATED upload passed
    // isComplete, was marked present by countsAsHave and could win Phase D. The MAX rule demanded 8.

    @Test fun anEpEditionCannotSetTheFloorOfItsAlbum() {
        val floor = buildFloorTracks(listOf("Sin Fronteras" to 12, "Sin Fronteras - EP" to 4))
        assertEquals(12, floor.getValue("sin fronteras"))
        val truncated = quality(3, true) // 3 of 12, durations fine — the shape that slipped through
        assertFalse(isComplete(truncated, floor["sin fronteras"]))
        assertFalse(countsAsHave(truncated, floor["sin fronteras"]))
        assertTrue(isComplete(quality(12, true), floor["sin fronteras"]))
    }

    @Test fun aTwoTrackMiniEditionCannotSetTheFloorEither() {
        // 2 tracks passes the old "more than one" test but is still not an edition of a 12-track album
        assertEquals(12, buildFloorTracks(listOf("X Album" to 12, "X Album - EP" to 2)).getValue("x album"))
    }

    @Test fun aStandardEditionIsNotMistakenForAMiniEdition() {
        // the ratio rule must NOT undo the MIN-across-stores protection: a standard edition is never under
        // half of its own deluxe, so it still sets the floor (and still passes its own gate)
        for (pair in listOf(listOf(24, 12), listOf(24, 13), listOf(18, 10))) {
            val (deluxe, standard) = pair
            val floor = buildFloorTracks(listOf("Look Up Child" to deluxe, "Look Up Child" to standard))
            assertEquals("$deluxe/$standard", standard, floor.getValue("look up child"))
            assertTrue("$deluxe/$standard", isComplete(quality(standard, true), floor["look up child"]))
        }
    }

    @Test fun aGenuinelyShortAlbumStillKeepsARealFloor() {
        // the fullest edition of itself always passes its own ratio test → a 5-track album still gates at 3
        val floor = buildFloorTracks(listOf("Corto" to 5))
        assertEquals(5, floor.getValue("corto"))
        assertFalse(isComplete(quality(2, true), floor["corto"]))
        assertTrue(isComplete(quality(5, true), floor["corto"]))
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

    @Test fun everyUNAMBIGUOUSLiveMarkerNormalizesToTheStudioBase() {
        val expected = reconKey("Lenguaje de Amor") + LIVE_MARKER
        for (raw in listOf(
            // parenthesised (iTunes' form) — the whole parenthetical goes, venue/date clause included
            "Lenguaje de Amor (En Vivo)",
            "Lenguaje de Amor (Live)",
            "Lenguaje de Amor (Acústico)",
            "Lenguaje de Amor (Unplugged)",
            "Lenguaje de Amor (En Vivo Desde Bogotá)",
            // after a REAL separator — any marker of the list, plus its venue/date tail
            "Lenguaje de Amor - En Vivo",
            "Lenguaje de Amor - Acústico",
            "Lenguaje de Amor: Live",
            "Lenguaje de Amor, Unplugged",
            "Lenguaje de Amor - Live at the Apollo",
            "Lenguaje de Amor - En Vivo 2020",
            "Lenguaje de Amor - En Vivo Desde Bogotá",
            // separator-less: ONLY the multi-word Spanish forms (YouTube Music's form, the owner's case)
            "Lenguaje de Amor En Vivo",
            "Lenguaje de Amor En Directo",
            "Lenguaje de Amor En Concierto",
        )) {
            assertEquals(raw, expected, reconKey(raw))
        }
    }

    // ── defect 5: the strip must never MUTILATE a legitimate title ──
    //
    // The two mistakes are NOT symmetric. A missed strip costs one wasted lookup (the release is searched
    // again and, at worst, listed twice). A WRONG strip renames the album to a shorter title that collides
    // with another release — and Phase D keeps ONE winner per key, so the loser disappears from the
    // discography, the exact opposite of what this feature is for. Every title below is real and every one
    // was mutilated by the separator-less venue clause ("marker + everything after it, any separator or
    // none"), which is why that clause now exists only in the parenthesised/separator form.

    @Test fun aLegitimateTitleIsNeverShortenedByTheLiveStrip() {
        for ((raw, whole) in listOf(
            "We Live in Time" to "we live in time",                       // used to become "we"
            "Sessions Live at the Apollo" to "sessions live at the apollo", // used to become "sessions"
            "Nada Es Igual Live 2019" to "nada es igual live 2019",        // digit branch → "nada es igual"
            "Long Live" to "long live",
            "MTV Unplugged" to "mtv unplugged",
            "MTV Unplugged in New York" to "mtv unplugged in new york",
            "Radio Live" to "radio live",
            "One Live" to "one live",
        )) {
            assertEquals(raw, whole, plainKey(reconKey(raw)))
        }
    }

    @Test fun twoRealAlbumsOfTheSameSeriesKeepTwoKeys() {
        // "MTV Unplugged" is a huge series (Maná, Shakira, Alejandro Sanz, Café Tacvba). Both entries used
        // to collapse to "mtv", so an artist holding both lost one of them in Phase D.
        assertNotEquals(reconKey("MTV Unplugged"), reconKey("MTV Unplugged in New York"))
        assertNotEquals(reconKey("Long Live"), reconKey("Long Live the King"))
        assertNotEquals(reconKey("We Live in Time"), reconKey("We Live in Sound"))
    }

    @Test fun anAmbiguousSeparatorLessMarkerIsDeliberatelyNotStripped() {
        // Conservative by design: a bare single-word marker with no separator stays in the key. The cost is
        // one wasted lookup (and at worst a duplicate row); the alternative deletes "MTV Unplugged" & co.
        for (raw in listOf(
            "Lenguaje de Amor Live",
            "Lenguaje de Amor Unplugged",
            "Lenguaje de Amor Acústico",
            "Lenguaje de Amor Acoustic",
            // venue/date tail with NO separator: this shape is what ate whole titles, so it is left alone
            "Lenguaje de Amor En Vivo Desde Bogotá",
            "Lenguaje de Amor En Vivo 2020",
            "Lenguaje de Amor Unplugged in New York",
        )) {
            assertNotEquals(raw, reconKey("Lenguaje de Amor") + LIVE_MARKER, reconKey(raw))
            assertTrue(raw, plainKey(reconKey(raw)).startsWith("lenguaje de amor "))
        }
    }

    @Test fun aTitleThatIsNothingButAMarkerKeepsAllItsWords() {
        // the strip may never leave fewer than one meaningful word, and never fires when the marker IS the
        // title — otherwise every such release would share one empty key
        for (raw in listOf("En Vivo", "Live", "En Directo", "Unplugged", "Acústico", "Directo al Corazón")) {
            assertEquals(raw, raw.lowercase(), plainKey(reconKey(raw)))
        }
    }

    @Test fun theOwnersCaseKeysTheSameInAllThreeForms() {
        // "Alex Campos En Vivo" (YouTube Music) == "Alex Campos (En Vivo)" (iTunes) == "Alex Campos - En Vivo"
        val paren = reconKey("Alex Campos (En Vivo)")
        assertEquals(paren, reconKey("Alex Campos En Vivo"))
        assertEquals(paren, reconKey("Alex Campos - En Vivo"))
        assertNotEquals(paren, reconKey("Alex Campos"))
        // …while the separator-less venue form is simply left whole (one wasted lookup, no mutilation)
        assertEquals("alex campos en vivo desde bogotá", plainKey(reconKey("Alex Campos En Vivo Desde Bogotá")))
    }

    @Test fun aTitleWhoseOwnWordsLookLikeMarkersIsNotMutilated() {
        // stripping only fires after a real separator, or for a multi-word Spanish marker that ENDS the
        // title — so these survive whole. Otherwise "Long Live the King" collapses to "long" and collides.
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
