package com.music.lrclib

import com.music.lrclib.models.Track
import com.music.lrclib.models.bestMatchingForRelaxed
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for LrcLib's OWN behaviour: which of the five strategies runs, what each one sends, which
 * fields it therefore constrained server-side, and which artist string reaches the matcher.
 *
 * This file exists because the matcher-level suite cannot see any of that. Hand
 * `List<Track>.bestMatchingFor` an explicit artistName and it never touches `cleanArtist`, never
 * touches the strategy ladder, and never touches the five provenance flags - so the entire suite
 * stays green while the change that actually fixes the reported bug is reverted. Each test that
 * guards one of those decisions names, in its comment, the exact edit that turns it red; every one
 * of those was checked by making the edit and watching this file fail.
 */
class LrcLibStrategyTest {

    private data class Query(
        val trackName: String? = null,
        val artistName: String? = null,
        val albumName: String? = null,
        val query: String? = null,
    )

    /** Stands in for the network: records every request and replays a canned answer. */
    private class FakeLrcLib(private val answer: (Query) -> List<Track>) {
        val requests = mutableListOf<Query>()

        val search: LrcLibSearch = { trackName, artistName, albumName, query ->
            val request = Query(trackName, artistName, albumName, query)
            requests += request
            answer(request)
        }
    }

    private var nextId = 1

    private fun track(
        title: String,
        artist: String,
        duration: Double,
        synced: String? = "[00:01.00] line",
        plain: String? = "line",
        album: String? = null,
    ) = Track(
        id = nextId++,
        trackName = title,
        artistName = artist,
        duration = duration,
        plainLyrics = plain,
        syncedLyrics = synced,
        albumName = album,
    )

    private fun lyricsOrNull(
        title: String,
        artist: String,
        duration: Int,
        album: String? = null,
        fake: FakeLrcLib,
    ): String? = runBlocking {
        runCatching { LrcLib.resolveLyrics(title, artist, duration, album, fake.search) }.getOrNull()
    }

    // ---------------------------------------------------------------------------------------
    // What each strategy sends, and in what order
    // ---------------------------------------------------------------------------------------

    @Test
    fun strategyOneSendsTheCleanedTitleAndTheCleanedPrimaryArtist() {
        val fake = FakeLrcLib { listOf(track("Uptown Funk", "Mark Ronson", duration = 270.0)) }

        lyricsOrNull(
            title = "Uptown Funk (Official Video)",
            artist = "Bruno Mars, Mark Ronson",
            duration = 270,
            album = "Uptown Special",
            fake = fake,
        )

        assertEquals("the ladder must stop at the first non-empty strategy", 1, fake.requests.size)
        assertEquals(
            Query(trackName = "Uptown Funk", artistName = "Bruno Mars", albumName = "Uptown Special"),
            fake.requests.single(),
        )
    }

    @Test
    fun theFiveStrategiesRunInOrderWithTheParametersEachOneClaims() {
        // Pins the ladder itself: the provenance flags asserted further down are only meaningful if
        // each strategy really sends the fields it says it does.
        val fake = FakeLrcLib { emptyList() }

        lyricsOrNull(title = "Alone (Official Video)", artist = "Halsey", duration = 212, fake = fake)

        assertEquals(
            listOf(
                Query(trackName = "Alone", artistName = "Halsey"),          // 1: both fields filtered
                Query(trackName = "Alone"),                                 // 2: title only
                Query(query = "Halsey Alone"),                              // 3: free text
                Query(query = "Alone"),                                     // 4: free text
                Query(trackName = "Alone (Official Video)", artistName = "Halsey"), // 5: raw retry
            ),
            fake.requests,
        )
    }

    @Test
    fun aResultWithNoLyricsAtAllDoesNotStopTheLadder() {
        val fake = FakeLrcLib { request ->
            when {
                request.artistName != null && request.query == null ->
                    listOf(track("Alone", "Halsey", duration = 213.0, synced = null, plain = null))

                else -> listOf(track("Alone", "Halsey", duration = 213.0))
            }
        }

        assertEquals("[00:01.00] line", lyricsOrNull("Alone", "Halsey", duration = 212, fake = fake))
        assertEquals(2, fake.requests.size)
    }

    // ---------------------------------------------------------------------------------------
    // THE REPORTED BUG, end to end through the real ladder
    // ---------------------------------------------------------------------------------------

    @Test
    fun aTitleOnlyResultSetCannotSmuggleInAnotherArtistsSong() {
        // Strategy 2 sends track_name= and NOTHING else, so every artist on the service can come
        // back. Reverting `artistConstrained = false` on Strategy 2 (LrcLib.kt) makes this return
        // Marshmello's lyrics for Halsey's song - the exact user report.
        val fake = FakeLrcLib { request ->
            if (request.artistName == null && request.trackName == "Alone") {
                listOf(track("Alone", "Marshmello", duration = 211.0))
            } else {
                emptyList()
            }
        }

        assertNull(
            "Strategy 2 is title-constrained ONLY; a confirmed different artist must be dropped",
            lyricsOrNull("Alone", "Halsey", duration = 212, fake = fake),
        )
    }

    @Test
    fun aFreeTextResultSetCannotSmuggleInAnotherSong() {
        // `q=` is fuzzy full text: it filters the title exactly as much as it filters the artist,
        // i.e. not at all. Setting `titleConstrained = true` on Strategy 3 makes this return
        // "Save Your Tears" for "Blinding Lights" - same artist, wrong song, identical duration.
        val fake = FakeLrcLib { request ->
            if (request.query == "The Weeknd Blinding Lights") {
                listOf(track("Save Your Tears", "The Weeknd", duration = 200.0))
            } else {
                emptyList()
            }
        }

        assertNull(
            "Strategy 3 constrains NEITHER field, so the title has to be re-judged locally",
            lyricsOrNull("Blinding Lights", "The Weeknd", duration = 200, fake = fake),
        )
    }

    @Test
    fun aFreeTextTitleOnlyResultSetIsJudgedOnBothFields() {
        // Strategy 4, same argument as Strategy 3.
        val fake = FakeLrcLib { request ->
            if (request.query == "Alone") listOf(track("Alone", "Marshmello", duration = 211.0)) else emptyList()
        }

        assertNull(lyricsOrNull("Alone", "Halsey", duration = 212, fake = fake))
    }

    // ---------------------------------------------------------------------------------------
    // THE HEADLINE FIX: the RAW artist tag reaches the matcher, not cleanArtist's reduction
    // ---------------------------------------------------------------------------------------

    @Test
    fun theRawArtistTagIsWhatTheMatcherJudges() {
        // `cleanArtist("Bruno Mars, Mark Ronson")` is "Bruno Mars" - correct for the query, fatal
        // for the comparison, because LrcLib credits this recording to "Mark Ronson" alone.
        //
        // DISCRIMINATING ASSERTION: revert `artistName = artist` to `artistName = cleanArtist(artist)`
        // in LrcLib.resolveLyrics and this returns null. "Bruno Mars" vs "Mark Ronson" is a
        // confirmed MISMATCH, and 3s is outside the only window a mismatch could ever reach.
        val fake = FakeLrcLib { request ->
            if (request.artistName == null && request.trackName == "Uptown Funk") {
                listOf(track("Uptown Funk", "Mark Ronson", duration = 269.0, synced = "[00:01.00] funk"))
            } else {
                emptyList()
            }
        }

        assertEquals(
            "the raw credit list is what can meet LrcLib's single-artist credit",
            "[00:01.00] funk",
            lyricsOrNull(
                title = "Uptown Funk (Official Video)",
                artist = "Bruno Mars, Mark Ronson",
                duration = 272, // 3s: reachable only as a confirmed artist MATCH
                fake = fake,
            ),
        )
    }

    @Test
    fun theLocalAlbumTagReachesTheMatcher() {
        // The album is the only thing that can overrule a confirmed-wrong artist, so it has to
        // travel from getLyrics() into the matcher. Dropping `albumName = album` makes this null.
        val fake = FakeLrcLib { request ->
            if (request.artistName == null && request.trackName == "Uptown Funk") {
                listOf(
                    track(
                        "Uptown Funk",
                        "Mark Ronson",
                        duration = 271.0,
                        synced = "[00:01.00] funk",
                        album = "Uptown Special",
                    ),
                )
            } else {
                emptyList()
            }
        }

        assertEquals(
            "[00:01.00] funk",
            lyricsOrNull(
                title = "Uptown Funk",
                artist = "Bruno Mars", // one-sided local credit: a confirmed MISMATCH on its own
                duration = 269,
                album = "Uptown Special",
                fake = fake,
            ),
        )
        assertNull(
            "without the album there is nothing independent behind the mismatch",
            lyricsOrNull(
                title = "Uptown Funk",
                artist = "Bruno Mars",
                duration = 269,
                album = null,
                fake = fake,
            ),
        )
    }

    // ---------------------------------------------------------------------------------------
    // Strategy 1 on the duration path is the pre-fix matcher, unchanged
    // ---------------------------------------------------------------------------------------

    @Test
    fun strategyOneOnTheDurationPathIsTheOldRelaxedMatcherVerbatim() {
        // Differential test against `bestMatchingForRelaxed`, which is the exact call the pre-fix
        // `getLyrics` made for every song with a real duration. Includes result sets whose names
        // agree with nothing, because the point is that on a both-constrained set the names are
        // never consulted at all.
        val candidateSets = listOf(
            listOf(track("Uptown Funk", "Mark Ronson", duration = 270.0)),
            listOf(track("完全に別の曲", "誰か別の人", duration = 270.0)),
            listOf(track("Something Else", "Someone Else", duration = 275.0)),
            listOf(track("Something Else", "Someone Else", duration = 276.0)),
            listOf(
                track("Uptown Funk", "Mark Ronson", duration = 270.0, synced = null),
                track("Uptown Funk", "Mark Ronson", duration = 273.0),
            ),
            listOf(
                track("Uptown Funk", "Mark Ronson", duration = 268.0),
                track("Uptown Funk", "Mark Ronson", duration = 271.0),
            ),
        )

        for (candidates in candidateSets) {
            for (duration in 265..275) {
                val fake = FakeLrcLib { candidates }
                val expected = candidates.bestMatchingForRelaxed(duration)
                    ?.let { it.syncedLyrics ?: it.plainLyrics }

                assertEquals(
                    "Strategy 1 must stay the pre-fix +/-5s matcher (duration=$duration)",
                    expected,
                    lyricsOrNull("Uptown Funk", "Bruno Mars, Mark Ronson", duration, fake = fake),
                )
            }
        }
    }

    // ---------------------------------------------------------------------------------------
    // The manual "search lyrics" sheet keeps everything and only reorders
    // ---------------------------------------------------------------------------------------

    @Test
    fun theSearchSheetRanksTheRightArtistFirstAndDropsNothing() {
        val fake = FakeLrcLib { request ->
            if (request.artistName == null && request.trackName == "Alone") {
                listOf(
                    track("Alone", "Marshmello", duration = 211.0, synced = "[00:01.00] wrong"),
                    track("Alone", "Halsey", duration = 213.0, synced = "[00:01.00] right"),
                )
            } else {
                emptyList()
            }
        }

        val collected = mutableListOf<String>()
        runBlocking {
            LrcLib.collectLyrics("Alone", "Halsey", duration = 212, search = fake.search) {
                collected += it
            }
        }

        assertEquals("[00:01.00] right", collected.first())
        assertTrue(
            "the sheet is the escape hatch for what the automatic gate declines - nothing is removed",
            collected.contains("[00:01.00] wrong"),
        )
    }
}
