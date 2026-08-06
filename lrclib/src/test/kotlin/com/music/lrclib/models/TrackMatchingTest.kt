package com.music.lrclib.models

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression tests for the "lyrics belong to a different song" bug.
 *
 * LrcLib falls back to searching by TITLE ONLY - and then by free text - when a title+artist search
 * comes back empty. The winner was then picked purely by a +/-5s duration window, so an unrelated
 * song by another artist that happened to be a similar length won, and its perfectly time-synced
 * lyrics were shown - and written to the database - for the song actually playing.
 *
 * Two things are under test and they pull in opposite directions:
 *   - showing NOTHING beats showing another song's lyrics, and
 *   - throwing away CORRECT lyrics for a whole class of music is its own failure, not a safe
 *     default. Multi-artist credits and differing credit order are the norm in Latin music.
 * Each test below names which of the two it is defending.
 *
 * These tests call the matcher directly, so they say nothing about which strategy ran or what it
 * sent. That half of the fix lives in `com.music.lrclib.LrcLibStrategyTest`, which drives the real
 * strategy ladder through the [com.music.lrclib.LrcLibSearch] seam.
 */
class TrackMatchingTest {

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

    // -----------------------------------------------------------------------------------------
    // The bug itself
    // -----------------------------------------------------------------------------------------

    @Test
    fun titleOnlyResultsFromADifferentArtistAreRejected() {
        // What LrcLib returns for track_name="Alone" with no artist filter: same title, same rough
        // length, completely different songs. Every one of these is inside the old +/-5s window.
        val results = listOf(
            track("Alone", "Marshmello", duration = 211.0),
            track("Alone", "Heart", duration = 213.0),
            track("Alone", "Alan Walker", duration = 210.0),
        )

        val picked = results.bestMatchingFor(
            duration = 212,
            trackName = "Alone",
            artistName = "Halsey",
            artistWasConstrained = false,
            titleWasConstrained = true,
        )

        assertNull("A confirmed different artist must never be shown", picked)
    }

    @Test
    fun thePreFixDurationOnlyMatcherStillReproducesTheBug() {
        // Non-vacuity guard for the test above: on the SAME input, the duration-only matcher that
        // used to decide this happily returns another artist's song. If this ever starts returning
        // null, the test above has stopped proving anything.
        val results = listOf(
            track("Alone", "Marshmello", duration = 211.0),
            track("Alone", "Heart", duration = 213.0),
        )

        assertEquals("Marshmello", results.bestMatchingForRelaxed(212)?.artistName)
    }

    @Test
    fun theClosestDurationDoesNotWinOverTheCorrectArtist() {
        // The heart of the bug: duration alone would pick the wrong-artist track, because it is
        // 0s off while the correct one is 3s off.
        val results = listOf(
            track("Creep", "Vanilla Ice", duration = 238.0),
            track("Creep", "Radiohead", duration = 235.0),
        )

        val picked = results.bestMatchingFor(
            duration = 238,
            trackName = "Creep",
            artistName = "Radiohead",
            artistWasConstrained = false,
            titleWasConstrained = true,
        )

        assertEquals("Radiohead", picked?.artistName)
    }

    @Test
    fun everyCandidateRejectedReturnsNullRatherThanABestEffort() {
        val results = listOf(track("Hello", "Someone Else", duration = 295.0))

        assertNull(
            results.bestMatchingFor(
                duration = 295,
                trackName = "Hello",
                artistName = "Adele",
                artistWasConstrained = false,
                titleWasConstrained = true,
            ),
        )
    }

    // -----------------------------------------------------------------------------------------
    // The fix must not over-block
    // -----------------------------------------------------------------------------------------

    @Test
    fun correctArtistWithinThreeSecondsIsStillAccepted() {
        val results = listOf(track("Bohemian Rhapsody", "Queen", duration = 358.0))

        val picked = results.bestMatchingFor(
            duration = 355, // 3s apart - a normal tagging difference
            trackName = "Bohemian Rhapsody",
            artistName = "Queen",
            artistWasConstrained = false,
            titleWasConstrained = true,
        )

        assertEquals("Queen", picked?.artistName)
    }

    @Test
    fun artistConstrainedResultsAreNotReGated() {
        // Strategy 1 already filtered by artist server-side. LrcLib's own artist spelling must not
        // be second-guessed locally, or we would start rejecting results the server vouched for.
        val results = listOf(track("Ojitos Lindos", "Bad Bunny with Bomba Estéreo", duration = 258.0))

        val picked = results.bestMatchingFor(
            duration = 260,
            trackName = "Ojitos Lindos",
            artistName = "Bad Bunny",
            artistWasConstrained = true,
            titleWasConstrained = true,
        )

        assertNotNull(picked)
    }

    @Test
    fun strategyOneIsUnchangedEvenWhenBothFieldsWouldFailTheLocalCheck() {
        // Strategy 1 must stay byte-identical: both fields were filtered server-side, so neither
        // the artist gate nor the new title gate may fire, and the window stays at +/-5s.
        val results = listOf(track("完全に別の曲", "誰か別の人", duration = 200.0))

        assertNotNull(
            results.bestMatchingFor(
                duration = 205, // 5s - only reachable on the unre-gated path
                trackName = "Something Else Entirely",
                artistName = "Someone Else Entirely",
                artistWasConstrained = true,
                titleWasConstrained = true,
            ),
        )
    }

    @Test
    fun primaryArtistMatchesACollaborationCredit() {
        // The local tag names ONE artist and LrcLib's credit names the whole collaboration - the
        // usual shape when the local metadata comes from YouTube. The local check has to survive the
        // extra credits on LrcLib's side.
        val results = listOf(track("Party", "Bad Bunny, Rauw Alejandro", duration = 245.0))

        val picked = results.bestMatchingFor(
            duration = 245,
            trackName = "Party",
            artistName = "Bad Bunny",
            artistWasConstrained = false,
            titleWasConstrained = true,
        )

        assertNotNull(picked)
    }

    @Test
    fun accentDifferencesDoNotRejectTheArtist() {
        val results = listOf(track("Halo", "Beyonce", duration = 261.0))

        val picked = results.bestMatchingFor(
            duration = 263,
            trackName = "Halo",
            artistName = "Beyoncé",
            artistWasConstrained = false,
            titleWasConstrained = true,
        )

        assertNotNull(picked)
    }

    // -----------------------------------------------------------------------------------------
    // PROBLEM 1: collaborations, credit ORDER, aliases and compilations must be ACCEPTED
    // -----------------------------------------------------------------------------------------

    @Test
    fun theOldWholeCreditComparisonIsWhatRejectedUptownFunk() {
        // Non-vacuity guard for the four tests below. These are the exact comparisons the previous
        // round performed, and every one of them threw away CORRECT lyrics. If any of these stops
        // being a MISMATCH, the tests below have stopped proving anything.
        assertEquals(
            "cleanArtist(\"Bruno Mars, Mark Ronson\") == \"Bruno Mars\", which cannot meet Mark Ronson",
            NameVerdict.MISMATCH,
            compareNames("Bruno Mars", "Mark Ronson"),
        )
        assertEquals(NameVerdict.MISMATCH, compareNames("Rauw Alejandro", "Bad Bunny"))
        assertEquals(NameVerdict.MISMATCH, compareNames("Ye", "Kanye West"))
        assertEquals(NameVerdict.MISMATCH, compareNames("Various Artists", "Adele"))
    }

    @Test
    fun uptownFunkIsAcceptedAgain() {
        // The reported regression. The local tag credits both artists; LrcLib credits only the one
        // that cleanArtist discarded. Splitting the RAW tag is what makes them meet.
        assertEquals(
            NameVerdict.MATCH,
            compareArtists("Bruno Mars, Mark Ronson", "Mark Ronson"),
        )

        val results = listOf(track("Uptown Funk", "Mark Ronson", duration = 269.0))

        assertNotNull(
            "Uptown Funk previously returned the CORRECT lyrics and must do so again",
            results.bestMatchingFor(
                duration = 272, // 3s - only reachable if the artist is a confirmed MATCH
                trackName = "Uptown Funk",
                artistName = "Bruno Mars, Mark Ronson",
                artistWasConstrained = false,
                titleWasConstrained = true,
            ),
        )
    }

    @Test
    fun aFeatureOrderFlipIsAccepted() {
        // Same recording, credits listed the other way round. cleanArtist kept "Rauw Alejandro",
        // LrcLib leads with "Bad Bunny", and the old comparison called that a different artist.
        assertEquals(
            NameVerdict.MATCH,
            compareArtists("Rauw Alejandro, Bad Bunny", "Bad Bunny"),
        )
        assertEquals(
            NameVerdict.MATCH,
            compareArtists("Rauw Alejandro, Bad Bunny", "Bad Bunny feat. Rauw Alejandro"),
        )
    }

    @Test
    fun theCreditSplitSavesAPairNeitherSideContains() {
        // The discriminating case for the SPLIT specifically: neither credit list is a subset of
        // the other, so a whole-string token comparison scores this as a different artist. Only
        // splitting both sides into individual credits finds the shared "Daddy Yankee".
        assertEquals(
            NameVerdict.MISMATCH,
            compareNames("Wisin y Yandel, Daddy Yankee", "Daddy Yankee, Don Omar"),
        )
        assertEquals(
            NameVerdict.MATCH,
            compareArtists("Wisin y Yandel, Daddy Yankee", "Daddy Yankee, Don Omar"),
        )
    }

    @Test
    fun everySeparatorThisMetadataActuallyUsesIsSplit() {
        assertEquals(listOf("Bruno Mars", "Mark Ronson"), splitArtists("Bruno Mars, Mark Ronson"))
        assertEquals(listOf("Karol G", "Nicki Minaj"), splitArtists("Karol G & Nicki Minaj"))
        assertEquals(listOf("Bad Bunny", "Jhay Cortez"), splitArtists("Bad Bunny feat. Jhay Cortez"))
        assertEquals(listOf("Bad Bunny", "Jhay Cortez"), splitArtists("Bad Bunny ft. Jhay Cortez"))
        assertEquals(listOf("Shakira", "Bizarrap"), splitArtists("Shakira con Bizarrap"))
        assertEquals(listOf("Romeo Santos", "Drake"), splitArtists("Romeo Santos x Drake"))
        assertEquals(listOf("Wisin", "Yandel"), splitArtists("Wisin y Yandel"))
        assertEquals(listOf("Nas", "Jay-Z"), splitArtists("Nas vs Jay-Z"))
        assertEquals(listOf("Tiesto", "Karol G"), splitArtists("Tiesto / Karol G"))
        assertEquals(listOf("Peso Pluma", "Becky G"), splitArtists("Peso Pluma × Becky G"))
    }

    @Test
    fun aWordSeparatorNeverCutsInsideAName() {
        // "x" and "y" are separators only as whole words, or names get shredded.
        assertEquals(listOf("Charli XCX"), splitArtists("Charli XCX"))
        assertEquals(listOf("Sandy"), splitArtists("Sandy"))
        assertEquals(listOf("Yandel"), splitArtists("Yandel"))
    }

    @Test
    fun anAliasIsUnjudgeableRatherThanRejected() {
        // "Ye" is two letters. A Levenshtein ratio over two letters says "different" no matter what
        // the truth is, so it cannot be used to REFUTE "Kanye West". No alias table - just a
        // refusal to assert something the evidence does not support.
        assertEquals(NameVerdict.UNKNOWN, compareArtists("Ye", "Kanye West"))
        assertEquals(NameVerdict.UNKNOWN, compareArtists("Kanye West", "Ye"))
    }

    @Test
    fun anAliasSurvivesOnTheTightWindowInsteadOfBeingDiscarded() {
        val results = listOf(track("Runaway", "Kanye West", duration = 548.0))

        assertNotNull(
            "an alias must reach the duration check, not be thrown away",
            results.bestMatchingFor(
                duration = 547,
                trackName = "Runaway",
                artistName = "Ye",
                artistWasConstrained = false,
                titleWasConstrained = true,
            ),
        )
    }

    @Test
    fun aGenericArtistTagIsUnjudgeableRatherThanRejected() {
        // A compilation tag names nobody. Absence of evidence is not counter-evidence.
        assertEquals(NameVerdict.UNKNOWN, compareArtists("Various Artists", "Adele"))
        assertEquals(NameVerdict.UNKNOWN, compareArtists("VA", "Adele"))
        assertEquals(NameVerdict.UNKNOWN, compareArtists("Soundtrack", "Adele"))
        assertEquals(
            NameVerdict.UNKNOWN,
            compareArtists("Original Motion Picture Soundtrack", "Adele"),
        )
        assertEquals(NameVerdict.UNKNOWN, compareArtists("Varios Artistas", "Adele"))
        // ...and it works from LrcLib's side too.
        assertEquals(NameVerdict.UNKNOWN, compareArtists("Adele", "Various Artists"))
    }

    @Test
    fun aCompilationSurvivesOnTheTightWindowInsteadOfBeingDiscarded() {
        val results = listOf(track("Hello", "Adele", duration = 295.0))

        assertNotNull(
            results.bestMatchingFor(
                duration = 294,
                trackName = "Hello",
                artistName = "Various Artists",
                artistWasConstrained = false,
                titleWasConstrained = true,
            ),
        )
    }

    @Test
    fun theMultiArtistSplitIsNotABlanketAccept() {
        // The permissiveness has to stop somewhere: sharing NO credit is still a MISMATCH.
        assertEquals(
            NameVerdict.MISMATCH,
            compareArtists("Bruno Mars, Mark Ronson", "Coldplay, Chris Martin"),
        )

        val results = listOf(track("Uptown Funk", "Coldplay", duration = 269.0))

        assertNull(
            results.bestMatchingFor(
                duration = 271,
                trackName = "Uptown Funk",
                artistName = "Bruno Mars, Mark Ronson",
                artistWasConstrained = false,
                titleWasConstrained = true,
            ),
        )
    }

    @Test
    fun theAliasRuleCostsTheTightWindowNotTheGate() {
        // Documented COST of "too short to refute": "Ye" can no longer reject anything, so an
        // unrelated artist is kept - but only inside +/-2s, never the loose +/-5s window.
        assertNull(
            listOf(track("Runaway", "Del Shannon", duration = 132.0)).bestMatchingFor(
                duration = 137, // 5s - fine for a confirmed artist, not for an unjudgeable one
                trackName = "Runaway",
                artistName = "Ye",
                artistWasConstrained = false,
                titleWasConstrained = true,
            ),
        )
    }

    // -----------------------------------------------------------------------------------------
    // PROBLEM 2: the title is checked wherever the server did not constrain it
    // -----------------------------------------------------------------------------------------

    @Test
    fun theRightArtistsWrongSongIsRejectedOnAFreeTextQuery() {
        // Strategies 3 and 4 use q=, which is a fuzzy full-text search: it constrains the title
        // exactly as much as it constrains the artist, i.e. not at all. So the surviving symptom
        // was the RIGHT artist's WRONG song, which a duration window cannot tell apart.
        val results = listOf(track("Save Your Tears", "The Weeknd", duration = 200.0))

        assertNull(
            "same artist, different song: the title is the only thing that can reject this",
            results.bestMatchingFor(
                duration = 200, // exact duration match - duration is no help here at all
                trackName = "Blinding Lights",
                artistName = "The Weeknd",
                artistWasConstrained = false,
                titleWasConstrained = false,
            ),
        )
    }

    @Test
    fun theSameCandidateIsAcceptedWhenTheServerDidConstrainTheTitle() {
        // Non-vacuity guard for the test above: it is the TITLE PROVENANCE that rejects, not the
        // artist check and not the duration. Flip the one flag and the same input is accepted.
        val results = listOf(track("Save Your Tears", "The Weeknd", duration = 200.0))

        assertNotNull(
            results.bestMatchingFor(
                duration = 200,
                trackName = "Blinding Lights",
                artistName = "The Weeknd",
                artistWasConstrained = false,
                titleWasConstrained = true,
            ),
        )
    }

    @Test
    fun theRightSongOnAFreeTextQueryIsStillAccepted() {
        val results = listOf(track("Blinding Lights", "The Weeknd", duration = 200.0))

        assertNotNull(
            results.bestMatchingFor(
                duration = 204, // 4s - both fields confirmed, so the +/-5s window applies
                trackName = "Blinding Lights",
                artistName = "The Weeknd",
                artistWasConstrained = false,
                titleWasConstrained = false,
            ),
        )
    }

    @Test
    fun aTitleDecoratedByLrcLibIsNotTreatedAsADifferentSong() {
        // Remix/feature decoration on LrcLib's side must not read as a different title.
        val results = listOf(track("Despacito (Remix) [feat. Justin Bieber]", "Luis Fonsi", duration = 229.0))

        assertNotNull(
            results.bestMatchingFor(
                duration = 232,
                trackName = "Despacito",
                artistName = "Luis Fonsi, Daddy Yankee",
                artistWasConstrained = false,
                titleWasConstrained = false,
            ),
        )
    }

    @Test
    fun anUnjudgeableTitleFallsToTheTightWindowRatherThanBeingDropped() {
        // Cross-script titles get the same treatment as cross-script artists: cannot judge.
        val results = listOf(track("レモン", "Kenshi Yonezu", duration = 256.0))

        assertNotNull(
            results.bestMatchingFor(
                duration = 255,
                trackName = "Lemon",
                artistName = "Kenshi Yonezu",
                artistWasConstrained = false,
                titleWasConstrained = false,
            ),
        )
        assertNull(
            results.bestMatchingFor(
                duration = 251, // 5s - not enough with the title unjudgeable
                trackName = "Lemon",
                artistName = "Kenshi Yonezu",
                artistWasConstrained = false,
                titleWasConstrained = false,
            ),
        )
    }

    // -----------------------------------------------------------------------------------------
    // Cross-script: "cannot judge", never "does not match"
    // -----------------------------------------------------------------------------------------

    @Test
    fun crossScriptArtistPairsAreNotThrownAway() {
        // A raw Levenshtein gate scores these ~0.0 and would silently lose the lyrics for a whole
        // class of music. They must be reported as unjudgeable, not as a mismatch.
        assertEquals(NameVerdict.UNKNOWN, compareNames("Kenshi Yonezu", "米津玄師"))
        assertEquals(NameVerdict.UNKNOWN, compareNames("ヨネヅケンシ", "Kenshi Yonezu"))
        assertEquals(NameVerdict.UNKNOWN, compareNames("Zemfira", "Земфира"))
        assertEquals(NameVerdict.UNKNOWN, compareNames("아이유", "IU"))
    }

    @Test
    fun theCrossScriptRuleSurvivesTheMultiArtistSplit() {
        // The split must not become a back door that reintroduces a cross-script MISMATCH.
        assertEquals(NameVerdict.UNKNOWN, compareArtists("Kenshi Yonezu", "米津玄師"))
        assertEquals(
            NameVerdict.UNKNOWN,
            compareArtists("Kenshi Yonezu, Hikaru Utada", "米津玄師 & 宇多田ヒカル"),
        )
    }

    @Test
    fun crossScriptArtistIsKeptWhenTheDurationIsTight() {
        val results = listOf(track("Lemon", "米津玄師", duration = 256.0))

        val picked = results.bestMatchingFor(
            duration = 255, // 1s - inside the tightened unjudgeable window
            trackName = "Lemon",
            artistName = "Kenshi Yonezu",
            artistWasConstrained = false,
            titleWasConstrained = true,
        )

        assertNotNull("A cross-script artist must not be rejected outright", picked)
    }

    @Test
    fun crossScriptArtistNeedsATighterDurationWindow() {
        // The documented COST of treating cross-script as "cannot judge": with no usable artist
        // signal the duration carries the decision alone, so the window shrinks from 5s to 2s.
        val results = listOf(track("Lemon", "米津玄師", duration = 260.0))

        assertNull(
            results.bestMatchingFor(
                duration = 255, // 5s - fine for a confirmed artist, not enough with no artist signal
                trackName = "Lemon",
                artistName = "Kenshi Yonezu",
                artistWasConstrained = false,
                titleWasConstrained = true,
            ),
        )
    }

    @Test
    fun aMixedScriptNameStillMatchesItsLatinShortForm() {
        // "BTS (방탄소년단)" shares the Latin script with "BTS", so this pair IS judgeable.
        assertEquals(NameVerdict.MATCH, compareNames("BTS", "BTS (방탄소년단)"))
    }

    @Test
    fun aConfirmedArtistBeatsAnUnjudgeableOne() {
        val results = listOf(
            track("Lemon", "米津玄師", duration = 256.0),
            track("Lemon", "Kenshi Yonezu", duration = 256.0),
        )

        val picked = results.bestMatchingFor(
            duration = 256,
            trackName = "Lemon",
            artistName = "Kenshi Yonezu",
            artistWasConstrained = false,
            titleWasConstrained = true,
        )

        assertEquals("Kenshi Yonezu", picked?.artistName)
    }

    // -----------------------------------------------------------------------------------------
    // Verdict details
    // -----------------------------------------------------------------------------------------

    @Test
    fun shortNamesDoNotMatchOnASubstringAccident() {
        // Token sets, not `contains`: "Yes" must not match "Yesterdays Band".
        assertEquals(NameVerdict.MISMATCH, compareNames("Yes", "Yesterdays Band"))
        // ...while a genuine token subset still does.
        assertEquals(NameVerdict.MATCH, compareNames("Earth", "Earth, Wind & Fire"))
    }

    @Test
    fun aNameContainingASeparatorWordStillMatchesAsAWhole() {
        // The split must not lose names that legitimately contain a separator: the whole-credit
        // comparison runs first, and both sides are split symmetrically anyway.
        assertEquals(NameVerdict.MATCH, compareArtists("Earth", "Earth, Wind & Fire"))
        assertEquals(NameVerdict.MATCH, compareArtists("Wisin y Yandel", "Wisin y Yandel"))
        assertEquals(NameVerdict.MATCH, compareArtists("Simon & Garfunkel", "Simon and Garfunkel"))
    }

    @Test
    fun anEmptyArtistIsUnjudgeableNotAMismatch() {
        assertEquals(NameVerdict.UNKNOWN, compareNames("", "Queen"))
        assertEquals(NameVerdict.UNKNOWN, compareNames("Queen", ""))
        assertEquals(NameVerdict.UNKNOWN, compareArtists("", "Queen"))
        assertEquals(NameVerdict.UNKNOWN, compareArtists("Queen", null))
    }

    @Test
    fun aMissingArtistTagFallsBackToTheTightDurationWindow() {
        // No artist to compare with is "cannot judge", so it must not silently reopen the loose
        // +/-5s window that caused the bug in the first place.
        val results = listOf(track("Alone", "Marshmello", duration = 216.0))

        assertNull(
            results.bestMatchingFor(
                duration = 211,
                trackName = "Alone",
                artistName = "",
                artistWasConstrained = false,
                titleWasConstrained = true,
            ),
        )
        assertNotNull(
            results.bestMatchingFor(
                duration = 215,
                trackName = "Alone",
                artistName = "",
                artistWasConstrained = false,
                titleWasConstrained = true,
            ),
        )
    }

    // -----------------------------------------------------------------------------------------
    // The user-facing picker keeps everything, only reorders
    // -----------------------------------------------------------------------------------------

    @Test
    fun thePickerRanksTheRightArtistFirstWithoutDroppingAnything() {
        val results = listOf(
            track("Alone", "Marshmello", duration = 211.0),
            track("Lemon", "米津玄師", duration = 256.0),
            track("Alone", "Halsey", duration = 213.0),
        )

        val ranked = results.rankedByArtistConfidence("Halsey")

        assertEquals("nothing may be removed from the manual search sheet", 3, ranked.size)
        assertEquals("Halsey", ranked.first().artistName)
        assertEquals("Marshmello", ranked.last().artistName)
    }

    @Test
    fun thePickerRanksACollaboratorAsHighAsTheLeadArtist() {
        val results = listOf(
            track("Uptown Funk", "Coldplay", duration = 269.0),
            track("Uptown Funk", "Mark Ronson", duration = 269.0),
        )

        val ranked = results.rankedByArtistConfidence("Bruno Mars, Mark Ronson")

        assertEquals(2, ranked.size)
        assertEquals("Mark Ronson", ranked.first().artistName)
    }

    // -----------------------------------------------------------------------------------------
    // Unknown-duration path (duration == -1) still consults the names
    // -----------------------------------------------------------------------------------------

    @Test
    fun unknownDurationStillRejectsAClearlyWrongArtist() {
        val results = listOf(track("Hello", "Someone Else", duration = 295.0))

        assertNull(
            results.bestMatchingFor(
                duration = -1,
                trackName = "Hello",
                artistName = "Adele",
                artistWasConstrained = false,
                titleWasConstrained = true,
            ),
        )
    }

    @Test
    fun unknownDurationRejectsTheRightArtistsWrongSongOnAFreeTextQuery() {
        val results = listOf(track("Save Your Tears", "The Weeknd", duration = 200.0))

        assertNull(
            results.bestMatchingFor(
                duration = -1,
                trackName = "Blinding Lights",
                artistName = "The Weeknd",
                artistWasConstrained = false,
                titleWasConstrained = false,
            ),
        )
    }

    @Test
    fun unknownDurationAcceptsACollaborationCredit() {
        val results = listOf(track("Uptown Funk", "Mark Ronson", duration = 269.0))

        assertNotNull(
            results.bestMatchingFor(
                duration = -1,
                trackName = "Uptown Funk",
                artistName = "Bruno Mars, Mark Ronson",
                artistWasConstrained = false,
                titleWasConstrained = true,
            ),
        )
    }

    @Test
    fun unknownDurationKeepsACrossScriptArtistWhenTheTitleMatches() {
        val results = listOf(track("Lemon", "米津玄師", duration = 256.0))

        assertNotNull(
            results.bestMatchingFor(
                duration = -1,
                trackName = "Lemon",
                artistName = "Kenshi Yonezu",
                artistWasConstrained = false,
                titleWasConstrained = true,
            ),
        )
    }

    @Test
    fun syncedLyricsAreStillPreferredAmongAcceptedCandidates() {
        val results = listOf(
            track("Creep", "Radiohead", duration = 238.0, synced = null),
            track("Creep", "Radiohead", duration = 236.0),
        )

        val picked = results.bestMatchingFor(
            duration = 238,
            trackName = "Creep",
            artistName = "Radiohead",
            artistWasConstrained = false,
            titleWasConstrained = true,
        )

        assertTrue(picked?.syncedLyrics != null)
    }

    // -----------------------------------------------------------------------------------------
    // PROBLEM 3: when the artist cannot vouch, the title must be EXACT
    //
    // UNKNOWN was a cheap pass: a generic tag, an alias or a cross-script name switched the artist
    // field off and left only +/-2s and a FUZZY title, which is not a discriminator at all.
    // -----------------------------------------------------------------------------------------

    @Test
    fun aFuzzyTitleIsNotEnoughWhenTheArtistTagIsGeneric() {
        // Non-vacuity: the title check ALONE passes this pair, which is exactly the problem.
        assertEquals(NameVerdict.MATCH, compareNames("Alone", "Alone Again"))
        assertEquals(NameVerdict.UNKNOWN, compareArtists("Various Artists", "Marshmello"))

        assertNull(
            "a compilation tag disarms the artist, so a merely similar title must not pass",
            listOf(track("Alone Again", "Marshmello", duration = 211.0)).bestMatchingFor(
                duration = 212, // 1s - inside the tight window, which used to be the whole gate
                trackName = "Alone",
                artistName = "Various Artists",
                artistWasConstrained = false,
                titleWasConstrained = true,
            ),
        )
    }

    @Test
    fun aFuzzyTitleIsNotEnoughWhenTheArtistNameIsTooShortToRefute() {
        assertEquals(NameVerdict.UNKNOWN, compareArtists("IU", "Marshmello"))

        assertNull(
            listOf(track("Alone Again", "Marshmello", duration = 211.0)).bestMatchingFor(
                duration = 212,
                trackName = "Alone",
                artistName = "IU",
                artistWasConstrained = false,
                titleWasConstrained = true,
            ),
        )
    }

    @Test
    fun aFuzzyTitleIsNotEnoughWhenTheArtistIsCrossScript() {
        assertNull(
            listOf(track("Lemon Tree", "米津玄師", duration = 256.0)).bestMatchingFor(
                duration = 255,
                trackName = "Lemon",
                artistName = "Kenshi Yonezu",
                artistWasConstrained = false,
                titleWasConstrained = true,
            ),
        )
    }

    @Test
    fun anExactTitleStillCarriesAnUnjudgeableArtistThroughDecoration() {
        // The songs the UNKNOWN verdict exists to save are not lost by the exactness rule: LrcLib's
        // decoration is stripped from both sides before the comparison.
        assertNotNull(
            listOf(track("Blinding Lights (Official Video)", "Various Artists", duration = 200.0))
                .bestMatchingFor(
                    duration = 201,
                    trackName = "Blinding Lights",
                    artistName = "Soundtrack",
                    artistWasConstrained = false,
                    titleWasConstrained = true,
                ),
        )
    }

    @Test
    fun theExactTitleTestIgnoresOnlyCaseAccentsPunctuationAndDecoration() {
        assertTrue(titlesAreTheSameRecording("Blinding Lights", "blinding lights"))
        assertTrue(titlesAreTheSameRecording("Dont Stop Me Now", "Don't Stop Me Now"))
        assertTrue(titlesAreTheSameRecording("Corazon", "Corazón"))
        assertTrue(titlesAreTheSameRecording("Blinding Lights", "Blinding Lights (Official Video)"))
        assertTrue(titlesAreTheSameRecording("Bohemian Rhapsody", "Bohemian Rhapsody - Remastered 2011"))
        assertTrue(titlesAreTheSameRecording("Despacito", "Despacito (Remix) [feat. Justin Bieber]"))
        // ...and nothing else. A different song is a different song.
        assertFalse(titlesAreTheSameRecording("Alone", "Alone Again"))
        assertFalse(titlesAreTheSameRecording("Creep", "Creepin"))
        assertFalse(titlesAreTheSameRecording("Blinding Lights", "Lights Blinding"))
        assertFalse(titlesAreTheSameRecording("", "Alone"))
    }

    // -----------------------------------------------------------------------------------------
    // PROBLEM 4: a one-sided local credit ("Bruno Mars" alone vs LrcLib's "Mark Ronson")
    //
    // A confirmed-wrong artist is recoverable ONLY with an independent field behind it. The
    // rejected alternative - "exact title plus +/-2s is enough" - is the reported bug's own shape.
    // -----------------------------------------------------------------------------------------

    @Test
    fun anExactTitleAloneCannotOverruleAConfirmedWrongArtist() {
        // These two inputs are the SAME four strings in the same shape. The first is the pair we
        // would like to recover; the second is the bug the whole module was fixed for. No rule that
        // reads only title/artist/duration can accept one and reject the other, so BOTH are
        // rejected, and recovery is left to the album test below.
        assertEquals(NameVerdict.MISMATCH, compareArtists("Bruno Mars", "Mark Ronson"))
        assertEquals(NameVerdict.MISMATCH, compareArtists("Halsey", "Marshmello"))

        assertNull(
            "recoverable-looking, but indistinguishable from the bug",
            listOf(track("Uptown Funk", "Mark Ronson", duration = 269.0)).bestMatchingFor(
                duration = 270,
                trackName = "Uptown Funk",
                artistName = "Bruno Mars",
                artistWasConstrained = false,
                titleWasConstrained = true,
            ),
        )
        assertNull(
            "THE reported bug: same title, unrelated artist, 1s apart",
            listOf(track("Alone", "Marshmello", duration = 211.0)).bestMatchingFor(
                duration = 212,
                trackName = "Alone",
                artistName = "Halsey",
                artistWasConstrained = false,
                titleWasConstrained = true,
            ),
        )
    }

    @Test
    fun aMatchingAlbumRecoversAOneSidedLocalCredit() {
        // The album is independent of the artist string, and it is what the bug scenario cannot
        // produce: Halsey's "Alone" and Marshmello's "Alone" are not on the same record.
        val results = listOf(
            track("Uptown Funk", "Mark Ronson", duration = 271.0, album = "Uptown Special"),
        )

        assertNotNull(
            results.bestMatchingFor(
                duration = 269, // 2s - the tight window still has to be satisfied
                trackName = "Uptown Funk",
                artistName = "Bruno Mars",
                albumName = "Uptown Special",
                artistWasConstrained = false,
                titleWasConstrained = true,
            ),
        )
    }

    @Test
    fun theAlbumEscapeNeedsTheAlbumTheTitleAndTheDurationAllThree() {
        val onUptownSpecial = listOf(
            track("Uptown Funk", "Mark Ronson", duration = 271.0, album = "Uptown Special"),
        )

        // No album on the local side: nothing corroborates, so the mismatch stands.
        assertNull(
            onUptownSpecial.bestMatchingFor(
                duration = 269,
                trackName = "Uptown Funk",
                artistName = "Bruno Mars",
                albumName = null,
                artistWasConstrained = false,
                titleWasConstrained = true,
            ),
        )
        // A different album is counter-evidence, not corroboration.
        assertNull(
            onUptownSpecial.bestMatchingFor(
                duration = 269,
                trackName = "Uptown Funk",
                artistName = "Bruno Mars",
                albumName = "24K Magic",
                artistWasConstrained = false,
                titleWasConstrained = true,
            ),
        )
        // Right album, but 4s apart: the tight window is not relaxed by the escape.
        assertNull(
            onUptownSpecial.bestMatchingFor(
                duration = 267,
                trackName = "Uptown Funk",
                artistName = "Bruno Mars",
                albumName = "Uptown Special",
                artistWasConstrained = false,
                titleWasConstrained = true,
            ),
        )
        // Right album, near-exact duration, but a different song on that album.
        assertNull(
            listOf(track("Feel Right", "Mark Ronson", duration = 271.0, album = "Uptown Special"))
                .bestMatchingFor(
                    duration = 270,
                    trackName = "Uptown Funk",
                    artistName = "Bruno Mars",
                    albumName = "Uptown Special",
                    artistWasConstrained = false,
                    titleWasConstrained = false,
                ),
        )
    }

    @Test
    fun aGenericAlbumNameCorroboratesNothing() {
        assertFalse(albumsCorroborate("Greatest Hits", "Greatest Hits"))
        assertFalse(albumsCorroborate("Various Artists", "Various Artists"))
        assertFalse(albumsCorroborate("", "Uptown Special"))
        assertFalse(albumsCorroborate("Uptown Special", null))
        assertTrue(albumsCorroborate("Uptown Special", "uptown special"))
        assertTrue(albumsCorroborate("Uptown Special", "Uptown Special (Deluxe Edition)"))
        assertFalse(albumsCorroborate("Uptown Special", "24K Magic"))

        assertNull(
            "two unrelated songs both tagged Greatest Hits must not vouch for each other",
            listOf(track("Alone", "Marshmello", duration = 211.0, album = "Greatest Hits"))
                .bestMatchingFor(
                    duration = 212,
                    trackName = "Alone",
                    artistName = "Halsey",
                    albumName = "Greatest Hits",
                    artistWasConstrained = false,
                    titleWasConstrained = true,
                ),
        )
    }

    @Test
    fun theExactTitleRuleCostsDecorationThisFileDoesNotKnowHowToStrip() {
        // The documented COST of demanding an exact title from an unjudgeable artist. "(Amsterdam
        // Session)" carries no decoration keyword, so it survives canonicalisation and the two
        // titles are not equal - these lyrics are dropped even though they are probably right. The
        // manual "search lyrics" sheet still offers them.
        assertNull(
            listOf(track("Alone (Amsterdam Session)", "Halsey", duration = 211.0)).bestMatchingFor(
                duration = 212,
                trackName = "Alone",
                artistName = "Various Artists",
                artistWasConstrained = false,
                titleWasConstrained = true,
            ),
        )
    }

    @Test
    fun theAlbumEscapeDoesNotReopenTheReportedBug() {
        // The full reported scenario with album metadata present on both sides, which is the only
        // situation in which the escape can fire at all.
        assertNull(
            listOf(track("Alone", "Marshmello", duration = 211.0, album = "Joytime"))
                .bestMatchingFor(
                    duration = 212,
                    trackName = "Alone",
                    artistName = "Halsey",
                    albumName = "hopeless fountain kingdom",
                    artistWasConstrained = false,
                    titleWasConstrained = true,
                ),
        )
    }
}
