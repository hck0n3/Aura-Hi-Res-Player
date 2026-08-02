package com.music.jiosaavn

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SaavnMatcherTest {

    @Test
    fun cleanCandidateReturnsZeroAndKaraokeCandidateReturnsNegative() {
        assertEquals(0, SaavnMatcher.variantPenalty("Expectations", "Expectations"))
        assertTrue(SaavnMatcher.variantPenalty("Expectations", "Expectations (Karaoke Version)") < 0)
    }

    @Test
    fun penaltyIsSuppressedWhenYtTitleContainsVariant() {
        assertEquals(0, SaavnMatcher.variantPenalty("Song (Instrumental)", "Song (Instrumental)"))
    }

    @Test
    fun markerMatchingIsCaseInsensitive() {
        assertTrue(SaavnMatcher.variantPenalty("Song", "Song KARAOKE") < 0)
        assertTrue(SaavnMatcher.variantPenalty("Song", "Song Minus One") < 0)
        assertTrue(SaavnMatcher.variantPenalty("Song", "Song minus-one") < 0)
    }

    @Test
    fun markerAsParentheticalAndHyphenSuffixArePenalized() {
        assertTrue(SaavnMatcher.variantPenalty("Song", "Song (Remix)") < 0)
        assertTrue(SaavnMatcher.variantPenalty("Song", "Song - Remix") < 0)
    }

    @Test
    fun noMarkerCandidateReturnsExactlyZero() {
        assertEquals(0, SaavnMatcher.variantPenalty("Song", "Song"))
    }

    // ==================== TITLE GATE ====================
    // The scorer in YTPlayerUtils is ADDITIVE: duration (+30) + artist (+20) + explicit (+5) = 55,
    // past its MIN_CONFIDENCE of 40, with a title score of ZERO. These are the three proven
    // wrong-song paths; the gate must reject all of them BEFORE any of those points can count.

    /** Case A: zero title similarity + same artist + duration within 5s used to play a DIFFERENT song. */
    @Test
    fun caseA_differentSongBySameArtistIsRejected() {
        assertEquals(0.0, SaavnMatcher.titleSimilarity("Tití Me Preguntó", "Neverita", "Bad Bunny"), 0.0)
        assertFalse(SaavnMatcher.titleMatches("Tití Me Preguntó", "Neverita", "Bad Bunny"))
    }

    /** Case B: a partial ARTIST overlap ("Bad Bunny" vs "Bad Gyal") must not carry a mismatched title. */
    @Test
    fun caseB_partialArtistOverlapCannotCarryAMismatchedTitle() {
        // Documents the leak: 1 of 2 artist words in common = half of the 20 artist points = +10,
        // which with a +30 duration hit exactly reached the old threshold of 40.
        assertEquals(0.5, SaavnMatcher.overlapRatio("Bad Bunny", "Bad Gyal"), 0.0001)
        assertFalse(SaavnMatcher.titleMatches("Mónaco", "Chulo", "Bad Bunny"))
    }

    /** Case C: metadata fetch failed => artist is blank => the artist check is vacuous for everyone. */
    @Test
    fun caseC_blankArtistStillRejectsALowSimilarityTitle() {
        assertFalse(SaavnMatcher.titleMatches("Ella Baila Sola", "Sola", ""))
        assertFalse(SaavnMatcher.titleMatches("Quiero Bailar Contigo Toda La Noche", "Noche", ""))
    }

    /** A candidate that is a FRAGMENT of the YouTube title is a different song, artist known or not. */
    @Test
    fun fragmentOfTheTitleIsNotAMatch() {
        assertFalse(SaavnMatcher.titleMatches("Ella Baila Sola", "Sola", "Eslabon Armado, Peso Pluma"))
    }

    // ── Non-regression: real matches must still pass ──

    @Test
    fun decoratedYouTubeTitleStillMatchesTheCleanSaavnName() {
        assertTrue(SaavnMatcher.titleMatches("Karol G - PROVENZA (Official Video)", "Provenza", "Karol G"))
        // …and also when the artist is unknown, since the decoration alone is stripped.
        assertTrue(SaavnMatcher.titleMatches("Karol G - PROVENZA (Official Video)", "Provenza", ""))
    }

    @Test
    fun saavnAlbumSuffixStillMatches() {
        // Saavn habitually appends album context; the YouTube title contained in the candidate is fine.
        assertTrue(SaavnMatcher.titleMatches("Provenza", "Provenza (From \"Mañana Será Bonito\")", "Karol G"))
    }

    @Test
    fun featureCreditOnlyOnTheYouTubeSideStillMatches() {
        assertTrue(
            SaavnMatcher.titleMatches(
                "Me Porto Bonito (feat. Chencho Corleone)",
                "Me Porto Bonito",
                "Bad Bunny, Chencho Corleone",
            )
        )
    }

    /**
     * Accent folding. Without it the `[^a-z0-9]` squash shreds accented words ("preguntó" ->
     * "pregunt"), the title similarity collapses to the shared "me", and the gate would reject a
     * PERFECT match — turning this fix into a Spanish-language regression.
     */
    @Test
    fun accentsAreFoldedSoSpanishTitlesMatch() {
        assertEquals(1.0, SaavnMatcher.titleSimilarity("Tití Me Preguntó", "Titi Me Pregunto", "Bad Bunny"), 0.0001)
        assertEquals(1.0, SaavnMatcher.overlapRatio("Ñengo Flow", "Nengo Flow"), 0.0001)
        assertTrue(SaavnMatcher.titleMatches("Mónaco", "Monaco", "Bad Bunny"))
    }

    /** Same title by a different artist passes the TITLE gate by design — the artist check owns that. */
    @Test
    fun titleGateDoesNotTryToDoTheArtistCheck() {
        assertTrue(SaavnMatcher.titleMatches("Dirt", "Dirt", "Alice In Chains"))
    }

    @Test
    fun exactTitleIsAlwaysAMatch() {
        assertEquals(1.0, SaavnMatcher.titleSimilarity("Provenza", "PROVENZA", "Karol G"), 0.0001)
        assertTrue(SaavnMatcher.titleMatches("One", "One", "Metallica"))
    }

    @Test
    fun emptyInputsAreNeverAMatch() {
        assertEquals(0.0, SaavnMatcher.titleSimilarity("", "Provenza", "Karol G"), 0.0)
        assertEquals(0.0, SaavnMatcher.titleSimilarity("Provenza", "", "Karol G"), 0.0)
        assertEquals(0.0, SaavnMatcher.overlapRatio("", ""), 0.0)
        // An empty side must NOT trigger the cross-script abstention — that would open the gate for
        // every candidate whenever the title failed to load.
        assertFalse(SaavnMatcher.scriptsAreIncomparable("", "Provenza"))
        assertFalse(SaavnMatcher.titleMatches("", "Provenza", "Karol G"))
        assertFalse(SaavnMatcher.titleMatches("Provenza", "", "Karol G"))
    }

    // ==================== NON-LATIN SCRIPTS ====================
    // jiosaavn is an INDIAN service and YouTube Music stores plenty of titles in native script. An
    // ASCII-only normalizer deletes every such character, leaving an empty token set, which would make
    // the gate reject 100% of that catalogue — a hard playback regression for its core market.

    @Test
    fun devanagariTitlesSurviveNormalizationAndMatchThemselves() {
        assertEquals(1.0, SaavnMatcher.titleSimilarity("तेरा बन जाऊंगा", "तेरा बन जाऊंगा", "Akhil Sachdeva"), 0.0001)
        assertTrue(SaavnMatcher.titleMatches("केसरिया", "केसरिया", "Arijit Singh"))
        // …and a genuinely different Hindi title is still rejected.
        assertFalse(SaavnMatcher.titleMatches("केसरिया", "तेरा बन जाऊंगा", "Arijit Singh"))
    }

    @Test
    fun cjkAndCyrillicTitlesSurviveNormalization() {
        assertEquals(1.0, SaavnMatcher.titleSimilarity("夜に駆ける", "夜に駆ける", "YOASOBI"), 0.0001)
        assertTrue(SaavnMatcher.titleMatches("아무노래", "아무노래", "ZICO"))
        assertTrue(SaavnMatcher.titleMatches("Хочу перемен", "Хочу перемен", "Кино"))
    }

    /**
     * Native script on one side, transliteration on the other. No token metric can bridge those, so
     * the gate must ABSTAIN rather than reject — otherwise it makes a cross-script library strictly
     * worse than having no gate at all.
     */
    @Test
    fun crossScriptPairsAbstainInsteadOfRejecting() {
        assertTrue(SaavnMatcher.scriptsAreIncomparable("तेरा बन जाऊंगा", "Tera Ban Jaunga"))
        assertTrue(SaavnMatcher.titleMatches("तेरा बन जाऊंगा", "Tera Ban Jaunga", "Akhil Sachdeva"))
        assertTrue(SaavnMatcher.titleMatches("夜に駆ける", "Yoru ni Kakeru", "YOASOBI"))
        // Two Latin titles are always comparable, so the abstention can never leak into that path.
        assertFalse(SaavnMatcher.scriptsAreIncomparable("Ella Baila Sola", "Sola"))
    }

    /**
     * MIXED script is the COMMON case, not the edge: a Hindi title decorated with a Latin word. The
     * shared Latin token must not make the pair look comparable, because the words that actually name
     * the song are still in a script the other side never uses.
     */
    @Test
    fun mixedScriptTitlesStillAbstain() {
        assertTrue(SaavnMatcher.scriptsAreIncomparable("केसरिया (Lofi)", "Kesariya"))
        assertTrue(SaavnMatcher.titleMatches("केसरिया (Lofi)", "Kesariya", "Arijit Singh"))
        assertTrue(SaavnMatcher.scriptsAreIncomparable("夜に駆ける (Official Video)", "Yoru ni Kakeru"))
        // Both sides in native script are comparable again, and a different song is still rejected.
        assertFalse(SaavnMatcher.scriptsAreIncomparable("केसरिया (Lofi)", "केसरिया"))
        assertFalse(SaavnMatcher.titleMatches("केसरिया", "तेरा बन जाऊंगा", "Arijit Singh"))
    }

    /**
     * "with" is an ordinary English word, not a credit marker. Treating it as one truncates the title
     * to its first word, which then matches an unrelated song that merely starts the same way.
     */
    @Test
    fun withIsNotTreatedAsAFeatureCredit() {
        assertFalse(SaavnMatcher.titleMatches("Dancing With Myself", "Dancing Queen", ""))
        assertFalse(SaavnMatcher.titleMatches("Gone With The Wind", "Gone", ""))
        assertTrue(SaavnMatcher.titleMatches("Dancing With Myself", "Dancing With Myself", "Billy Idol"))
    }

    // ==================== CONTAINMENT DIRECTION ====================

    /**
     * The wrong-song substitution this gate exists to stop, tested in BOTH directions. A candidate
     * that merely ENDS with the requested word is a different song; only a shared LEADING word makes
     * the extra text read as context rather than as a different title.
     */
    @Test
    fun containmentIsRejectedInBothDirectionsWhenTheLeadingWordDiffers() {
        assertFalse(SaavnMatcher.titleMatches("Sola", "Ella Baila Sola", ""))
        assertFalse(SaavnMatcher.titleMatches("Ella Baila Sola", "Sola", ""))
        assertFalse(SaavnMatcher.titleMatches("Nunca", "Nunca Es Suficiente", ""))
        assertFalse(SaavnMatcher.titleMatches("Amor", "Amor Eterno", ""))
    }

    /** Same lead word => the extra text is album/film context, which Saavn adds routinely. */
    @Test
    fun containmentIsCreditedWhenTheLeadingWordIsShared() {
        assertTrue(SaavnMatcher.titleMatches("Kesariya", "Kesariya (From \"Brahmastra\")", "Arijit Singh"))
        assertTrue(SaavnMatcher.titleMatches("Provenza", "Provenza (From \"Mañana Será Bonito\")", "Karol G"))
    }

    // ==================== DECORATED TITLES ====================

    /**
     * Feature credits the DB does not enumerate. Whether a track plays must not depend on the local
     * SongArtistMap happening to list every guest artist.
     */
    @Test
    fun multiCreditTitlesMatchEvenWhenTheArtistHintOmitsTheGuests() {
        assertTrue(SaavnMatcher.titleMatches("Peaches (feat. Daniel Caesar, Giveon)", "Peaches", "Justin Bieber"))
        assertTrue(SaavnMatcher.titleMatches("Levitating (feat. DaBaby)", "Levitating", "Dua Lipa"))
        assertTrue(SaavnMatcher.titleMatches("Despacito (Remix) ft. Justin Bieber", "Despacito", "Luis Fonsi, Daddy Yankee"))
    }

    /** Indian YouTube titles list the cast after pipes; none of it identifies the song. */
    @Test
    fun pipeSeparatedCastListIsNotPartOfTheTitle() {
        assertTrue(
            SaavnMatcher.titleMatches(
                "Kesariya – Brahmastra | Ranbir Kapoor | Alia Bhatt | Pritam | Amitabh B",
                "Kesariya (From \"Brahmastra\")",
                "Arijit Singh",
            )
        )
    }

    /** Latin letters with no NFD decomposition must fold too, or they truncate their own token. */
    @Test
    fun nonDecomposingLatinLettersAreFolded() {
        assertTrue(SaavnMatcher.titleMatches("Straße", "Strasse", ""))
        assertEquals(1.0, SaavnMatcher.overlapRatio("Sonbahar Rüzgarları", "Sonbahar Ruzgarlari"), 0.0001)
        assertEquals(1.0, SaavnMatcher.overlapRatio("Björk", "Bjork"), 0.0001)
    }
}
