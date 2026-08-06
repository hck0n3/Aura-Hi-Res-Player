package com.music.lrclib.models

import kotlinx.serialization.Serializable
import java.text.Normalizer
import kotlin.math.abs

@Serializable
data class Track(
    val id: Int,
    val trackName: String,
    val artistName: String,
    val duration: Double,
    val plainLyrics: String?,
    val syncedLyrics: String?,
    /**
     * The album LrcLib names for this hit. The search response carries it; this class did not
     * declare it, so `ignoreUnknownKeys = true` silently dropped it. Read for exactly one purpose:
     * to let a release corroborate a candidate whose ARTIST credit disagrees with the local tag.
     * Nullable and defaulted, because the field can be absent or null.
     */
    val albumName: String? = null,
)

// ---------------------------------------------------------------------------------------------
// WRONG-SONG LYRICS FIX
//
// LrcLib's search can be asked for a title WITHOUT an artist (LrcLib.queryLyrics strategies 2/3/4).
// Those queries come back with every song on the service that carries that title, by ANY artist.
// The winner used to be picked by DURATION ALONE (`bestMatchingForRelaxed`, +/-5s), and neither the
// track name nor the artist name was ever read. A +/-5s window over a title-only result list is not
// a discriminator: unrelated 3-4 minute songs collide inside 5 seconds constantly, so users got
// perfectly time-synced lyrics belonging to a different artist's song.
//
// The guard that should have caught this was dead code: `bestMatchingFor(duration, trackName,
// artistName)` only consulted the names when `duration == -1`, and otherwise threw both arguments
// away and delegated to the duration-only matcher. A real song always has a duration, so the name
// scoring never ran.
//
// The check below is applied ONLY to the FIELDS the query did not constrain server-side. LrcLib's
// `artist_name=` is a real artist filter and `track_name=` is a real title filter; the free-text
// `q=` parameter is neither. So each result set carries two provenance flags and only the
// unconstrained field is re-judged locally. Strategy 1 (title+artist) constrains both and is
// therefore untouched on the duration path - see `LrcLibStrategyTest`, which drives the real
// strategy ladder and asserts that path is the pre-fix `bestMatchingForRelaxed` verbatim.
//
// SECOND ROUND - the gate was rejecting CORRECT lyrics for a whole class of music. Every one of
// these comparisons was a MISMATCH, i.e. the lyrics were thrown away (the exact assertions are in
// `theOldWholeCreditComparisonIsWhatRejectedUptownFunk`):
//
//   "Bruno Mars, Mark Ronson" vs LrcLib's "Mark Ronson"     (cleanArtist kept only "Bruno Mars")
//   "Rauw Alejandro, Bad Bunny" vs "Bad Bunny"              (credit order flipped)
//   "Ye" vs "Kanye West"                                    (alias)
//   "Various Artists" vs the real artist                    (compilation tag)
//
// Multi-artist credits and differing credit ORDER are the norm in Spanish-language and Latin music,
// not an edge case, so this was not a "safe default": throwing away correct lyrics for half a genre
// is its own failure. The artist comparison is now MULTI-ARTIST AWARE (both sides are split into
// individual credits and any-to-any is enough), and comparisons that cannot carry information -
// generic tags, names too short to refute - report UNKNOWN instead of MISMATCH.
//
// THIRD ROUND - what the second round left standing, and what it cost:
//
//   UNKNOWN had become a cheap pass. A generic tag, an alias, or a cross-script name switched the
//   artist field OFF, and all that remained was +/-2s and a FUZZY title. "Alone" and "Alone Again"
//   score 0.85 against each other, so with the artist disarmed a different song two seconds away was
//   accepted. So: WHEN THE ARTIST CANNOT VOUCH, THE TITLE MUST BE EXACT (after normalisation and
//   decoration stripping, see [titlesAreTheSameRecording]). Nothing that had a real artist match is
//   affected, and every case the UNKNOWN verdict was introduced for - the alias, the compilation
//   tag, the cross-script credit - still passes here, because in all of them the title is exact.
//   What it does cost is stated at the bottom of `TrackMatchingTest`: an unjudgeable artist plus a
//   title LrcLib decorated in a way this file does not strip is now dropped.
//
//   A MISMATCH artist is still dropped, and it is dropped even when the title is EXACT and the
//   duration is within +/-2s. That escape was specified, and it is refused, because it is not
//   distinguishable from the reported bug:
//
//     recover:  local "Bruno Mars"  / LrcLib "Mark Ronson" / both titled "Uptown Funk" / ~equal
//     the bug:  local "Halsey"      / LrcLib "Marshmello"  / both titled "Alone"       / ~equal
//
//   Those two are the SAME four strings in the same shape. No rule reading only the title, the
//   artist and the duration can accept the first and reject the second, and same-title collisions
//   ("Alone", "Hello", "Stay", "Closer") are exactly where users hit this. What DOES separate them
//   is a fourth field: the ALBUM. A confirmed-different artist is therefore accepted only when the
//   title is exact, the duration is within +/-2s, AND both sides name the same non-generic album -
//   evidence that is independent of the artist string and that the bug scenario cannot produce.
//   When the caller has no album tag the escape simply never fires and the candidate is dropped;
//   the manual "search lyrics" sheet still lists it (see [rankedByArtistConfidence]).
// ---------------------------------------------------------------------------------------------

/** Duration window for a candidate whose artist we could positively confirm. */
internal const val RELAXED_TOLERANCE_SECONDS = 5

/**
 * Duration window for a candidate that only ONE of the two name fields could identify - typically
 * an unjudgeable artist (see [NameVerdict.UNKNOWN]) carried by an exact title. Deliberately tighter:
 * with one field doing the identifying, the duration has to corroborate it.
 */
internal const val UNJUDGEABLE_TOLERANCE_SECONDS = 2

/** Minimum name similarity that counts as the same artist. */
internal const val NAME_MATCH_THRESHOLD = 0.6

/**
 * A name shorter than this carries too little information to REFUTE another name.
 *
 * This is a statement about the evidence, not an alias table. Below four letters a Levenshtein
 * ratio is pure noise: a two-letter name sits nearly the whole length of the other string away from
 * anything longer, so the score reads "different" whether or not the names are - "Ye" scores 0.2
 * against "Kanye West", its own expansion. Short forms are also exactly where aliases, initialisms
 * and renamings live ("Ye" for Kanye West, "VA", "M.I.A."). So a too-short name yields UNKNOWN, and
 * the decision falls to an EXACT title plus the tight duration window instead of a MISMATCH we
 * cannot actually justify.
 */
private const val MIN_LETTERS_TO_REFUTE = 4

/**
 * Separators this metadata actually uses between artist credits. Splitting is symmetric (both the
 * local tag and LrcLib's credit go through it), and over-splitting is the safe direction: a MATCH is
 * decided by the whole-credit comparison first and then by any-to-any agreement, while a MISMATCH
 * needs every pair to disagree AND both sides to still be long enough to refute - so cutting a
 * credit into more pieces can only ever make MISMATCH harder to reach.
 *
 * The word-shaped separators ("x", "y", "con", "and", "vs", "with") are anchored on word boundaries
 * so they cannot cut inside a name ("Charli XCX", "Sandy", "Wisin").
 */
private val ARTIST_SEPARATORS = Regex(
    """\s*(?:[,;/&+×]|\bfeat\b\.?|\bft\b\.?|\bfeaturing\b|\bwith\b|\band\b|\bcon\b|\bvs\b\.?|\bversus\b|\bx\b|\by\b)\s*""",
    RegexOption.IGNORE_CASE,
)

/**
 * Placeholder credits that identify no artist at all. A compilation tagged "Various Artists" is not
 * evidence that LrcLib's artist is wrong - it is the absence of evidence - so it must be UNKNOWN,
 * never MISMATCH. Compared after [tokenize], so accents and punctuation do not matter.
 */
private val GENERIC_ARTIST_TAGS = setOf(
    "various artists", "various", "va", "varios artistas", "artistas varios",
    "unknown artist", "unknown", "artista desconocido", "desconocido",
    "soundtrack", "original soundtrack", "original motion picture soundtrack",
    "motion picture soundtrack", "ost", "banda sonora", "banda sonora original",
    "cast", "original cast", "compilation", "recopilacion",
    "no artist", "sin artista", "traditional", "tradicional", "anonymous", "anonimo",
)

/**
 * Album titles that name no particular release. Two songs both tagged "Greatest Hits" have not been
 * shown to come from the same record, so such a pair must not corroborate anything.
 * Compared after [canonicalTitle], i.e. lowercased, accent- and punctuation-stripped.
 */
private val GENERIC_ALBUM_TAGS = setOf(
    "greatest hits", "the greatest hits", "hits", "best of", "the best of", "essential",
    "the essential", "collection", "the collection", "compilation", "various artists", "various",
    "single", "singles", "ep", "album", "unknown album", "unknown", "soundtrack", "ost",
    "banda sonora", "grandes exitos", "exitos", "demo", "demos", "live", "en vivo", "remixes",
)

/**
 * Words that mark a fragment as decoration rather than part of the song's name.
 *
 * "con" ("with") and other everyday Spanish/English words are deliberately NOT in this list: a
 * keyword here can delete a meaningful parenthetical and make two different titles look identical,
 * which is the failure this whole file exists to prevent.
 */
private const val TITLE_DECORATION_KEYWORDS =
    "official|video|audio|lyrics?|visualizer|hd|hq|4k|remaster|remastered|remasterizado|remix|live|" +
        "en vivo|acoustic|acustico|version|edit|extended|radio|clean|explicit|mono|stereo|bonus|" +
        "deluxe|instrumental|karaoke|demo|single|album|topic|feat|ft|featuring"

/**
 * Decoration that does not change WHICH RECORDING a title names. Used only for matching, never for
 * building a query: `LrcLib.cleanTitle` owns the query form and is deliberately left untouched, so
 * the request Strategy 1 sends is byte-for-byte what it always was.
 */
private val TITLE_DECORATION = listOf(
    Regex("""\s*\([^()]*\b(?:$TITLE_DECORATION_KEYWORDS)\b[^()]*\)""", RegexOption.IGNORE_CASE),
    Regex("""\s*\[[^\[\]]*\b(?:$TITLE_DECORATION_KEYWORDS)\b[^\[\]]*]""", RegexOption.IGNORE_CASE),
    Regex("""\s*【[^】]*】"""),
    Regex("""\s*\|.*$"""),
    Regex("""\s*-\s*[^-]*\b(?:$TITLE_DECORATION_KEYWORDS)\b.*$""", RegexOption.IGNORE_CASE),
)

/**
 * Three-valued name comparison.
 *
 * The third value is the important one. A plain Levenshtein/substring comparison scores ~0.0 ACROSS
 * WRITING SYSTEMS: "米津玄師" vs "Kenshi Yonezu", kana vs romaji, Cyrillic vs Latin, Hangul vs a
 * romanised tag. All of those are legitimate taggings of the same artist, and a two-valued gate
 * would silently throw away correct lyrics for a whole class of music - trading one bug for
 * another. So a comparison with no writing system in common is reported as "cannot judge" rather
 * than "does not match", and the caller compensates - an EXACT title plus a stricter duration
 * window - instead of rejecting outright.
 */
internal enum class NameVerdict {
    /** Confidently the same name. */
    MATCH,

    /** Confidently a different name - the candidate must be rejected. */
    MISMATCH,

    /** No shared writing system (or nothing comparable at all): the names carry no verdict. */
    UNKNOWN,
}

/**
 * Scripts present in [value], ignoring punctuation, digits and other script-neutral characters.
 * An empty set means "nothing here identifies a writing system" (e.g. an artist tagged "21").
 */
private fun scriptsOf(value: String): Set<Character.UnicodeScript> {
    val scripts = HashSet<Character.UnicodeScript>()
    var index = 0
    while (index < value.length) {
        val codePoint = value.codePointAt(index)
        index += Character.charCount(codePoint)
        if (!Character.isLetter(codePoint)) continue
        val script = runCatching { Character.UnicodeScript.of(codePoint) }.getOrNull() ?: continue
        when (script) {
            Character.UnicodeScript.COMMON,
            Character.UnicodeScript.INHERITED,
            Character.UnicodeScript.UNKNOWN,
            -> continue

            else -> scripts += script
        }
    }
    return scripts
}

/** Apostrophes are DELETED rather than turned into a gap, so "Don't" tokenises like "Dont". */
private const val APOSTROPHES = "'’ʼ`´"

/**
 * Lowercase, strip combining accents, drop punctuation, and reduce to whitespace-separated tokens.
 * Both sides get the identical transform, so this can only ever close a gap the raw forms would
 * have opened ("Beyonce"/"Beyoncé", "Don't"/"Dont"); it never rewrites one name into another.
 */
private fun tokenize(value: String): List<String> {
    val decomposed = Normalizer.normalize(value.trim().lowercase(), Normalizer.Form.NFD)
    val builder = StringBuilder(decomposed.length)
    for (char in decomposed) {
        when {
            Character.getType(char) == Character.NON_SPACING_MARK.toInt() -> Unit
            char in APOSTROPHES -> Unit
            char.isLetterOrDigit() -> builder.append(char)
            else -> builder.append(' ')
        }
    }
    return builder.toString().split(' ').filter { it.isNotEmpty() }
}

/**
 * A title reduced to the recording it names: decoration removed (see [TITLE_DECORATION]), then
 * [tokenize]d. Two titles with the same token LIST are the same title down to case, accents,
 * punctuation and decoration - and nothing else. Word ORDER still matters.
 */
internal fun canonicalTitle(value: String): List<String> {
    var cleaned = value.trim()
    for (pattern in TITLE_DECORATION) {
        cleaned = cleaned.replace(pattern, "")
    }
    return tokenize(cleaned)
}

/**
 * True when the two titles name the same recording under [canonicalTitle]. This is the EXACT test
 * that stands in for the artist when the artist cannot vouch; a fuzzy title score is explicitly not
 * enough there, because "Alone" scores 0.85 against "Alone Again".
 */
internal fun titlesAreTheSameRecording(expected: String?, candidate: String?): Boolean {
    val left = canonicalTitle(expected.orEmpty())
    if (left.isEmpty()) return false
    return left == canonicalTitle(candidate.orEmpty())
}

/**
 * True when both sides name the same, identifiable album. Strict on purpose: exact equality after
 * [canonicalTitle], and generic release names ([GENERIC_ALBUM_TAGS]) corroborate nothing. Strictness
 * is the safe direction here - this is the ONLY thing that can override a confirmed-wrong artist, so
 * every case it declines simply falls back to dropping the candidate.
 */
internal fun albumsCorroborate(expected: String?, candidate: String?): Boolean {
    val left = canonicalTitle(expected.orEmpty()).joinToString(" ")
    val right = canonicalTitle(candidate.orEmpty()).joinToString(" ")
    if (left.isEmpty() || right.isEmpty()) return false
    if (left in GENERIC_ALBUM_TAGS || right in GENERIC_ALBUM_TAGS) return false
    return left == right
}

/**
 * Token-set similarity. Token sets rather than raw substrings, because `contains` is treacherous on
 * short names: a raw substring test matches "Yes" inside "Yesterday's Band", while a genuine token
 * subset ("Simon" inside "Simon & Garfunkel", which is what `LrcLib.cleanArtist` leaves of that
 * name) is exactly what we want to keep.
 */
private fun nameSimilarity(first: String, second: String): Double {
    val firstTokens = tokenize(first)
    val secondTokens = tokenize(second)
    if (firstTokens.isEmpty() || secondTokens.isEmpty()) return 0.0

    val firstSet = firstTokens.toSet()
    val secondSet = secondTokens.toSet()
    if (firstSet == secondSet) return 1.0
    if (firstSet.containsAll(secondSet) || secondSet.containsAll(firstSet)) return 0.85

    val firstJoined = firstTokens.joinToString(" ")
    val secondJoined = secondTokens.joinToString(" ")
    val maxLength = maxOf(firstJoined.length, secondJoined.length)
    if (maxLength == 0) return 0.0
    return 1.0 - (levenshteinDistance(firstJoined, secondJoined).toDouble() / maxLength)
}

/** Compares the name we are looking for against the name LrcLib returned. */
internal fun compareNames(expected: String?, candidate: String?): NameVerdict {
    val left = expected?.trim().orEmpty()
    val right = candidate?.trim().orEmpty()
    if (left.isEmpty() || right.isEmpty()) return NameVerdict.UNKNOWN

    val leftScripts = scriptsOf(left)
    val rightScripts = scriptsOf(right)
    // Nothing script-bearing on one side, or no writing system in common: we cannot judge these two
    // strings against each other. Note this is deliberately checked BEFORE similarity scoring, so a
    // cross-script pair can never be reported as a MISMATCH.
    if (leftScripts.isEmpty() || rightScripts.isEmpty()) return NameVerdict.UNKNOWN
    if (leftScripts.intersect(rightScripts).isEmpty()) return NameVerdict.UNKNOWN

    return if (nameSimilarity(left, right) >= NAME_MATCH_THRESHOLD) {
        NameVerdict.MATCH
    } else {
        NameVerdict.MISMATCH
    }
}

/** Splits a credit string into the individual artists it names. */
internal fun splitArtists(value: String): List<String> =
    value.split(ARTIST_SEPARATORS)
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .ifEmpty { listOf(value.trim()).filter { it.isNotEmpty() } }

private fun isGenericArtist(value: String) = tokenize(value).joinToString(" ") in GENERIC_ARTIST_TAGS

/** True when at least one credit on this side is long enough for a low score to mean anything. */
private fun canRefute(artists: List<String>) = artists.any { artist ->
    tokenize(artist).sumOf { it.length } >= MIN_LETTERS_TO_REFUTE
}

/**
 * Compares an ARTIST CREDIT - which is very often a list of artists - against LrcLib's credit.
 *
 * [compareNames] compares two names. That is the wrong shape for this field: the local tag and
 * LrcLib's tag routinely name the same collaboration in a different ORDER, or list a different
 * subset of it, and a whole-string score then reads "completely different artist" for what is the
 * same recording. `LrcLib.cleanArtist` makes it worse by keeping only the credit before the first
 * separator, so "Bruno Mars, Mark Ronson" would reach the comparison as "Bruno Mars" and could never
 * meet LrcLib's "Mark Ronson". That is why the caller feeds this function the RAW tag, not the
 * cleaned one - the cleaned form is for the network query, where LrcLib wants a single artist.
 *
 * Both sides are split into individual credits and ANY-to-ANY agreement is a MATCH. The cost of
 * any-to-any is stated plainly: "Wisin y Yandel" will match a solo "Yandel" credit. That is
 * deliberate - one shared artist is the strongest signal this field can offer, and the title check
 * is what separates two different songs by overlapping artists.
 *
 * MISMATCH is only returned when it can be justified: no pair agreed, no pair was unjudgeable, and
 * both sides carry a credit long enough to refute (see [MIN_LETTERS_TO_REFUTE]). Everything else -
 * empty, generic placeholder, cross-script, too short - is UNKNOWN, which keeps the candidate alive
 * on an exact title plus the tight duration window instead of discarding it.
 */
internal fun compareArtists(expected: String?, candidate: String?): NameVerdict {
    val left = expected?.trim().orEmpty()
    val right = candidate?.trim().orEmpty()
    if (left.isEmpty() || right.isEmpty()) return NameVerdict.UNKNOWN

    // "Various Artists" and friends name nobody. No information is not counter-evidence.
    if (isGenericArtist(left) || isGenericArtist(right)) return NameVerdict.UNKNOWN

    // Whole-credit agreement first: this is what keeps "Earth" vs "Earth, Wind & Fire" working even
    // when a name legitimately contains a separator word.
    if (compareNames(left, right) == NameVerdict.MATCH) return NameVerdict.MATCH

    val leftArtists = splitArtists(left)
    val rightArtists = splitArtists(right)
    if (leftArtists.isEmpty() || rightArtists.isEmpty()) return NameVerdict.UNKNOWN

    var sawUnjudgeable = false
    for (leftArtist in leftArtists) {
        for (rightArtist in rightArtists) {
            when (compareNames(leftArtist, rightArtist)) {
                NameVerdict.MATCH -> return NameVerdict.MATCH
                NameVerdict.UNKNOWN -> sawUnjudgeable = true
                NameVerdict.MISMATCH -> Unit
            }
        }
    }
    if (sawUnjudgeable) return NameVerdict.UNKNOWN

    // No pair agreed. Only call it a different artist if both sides said something substantial.
    if (!canRefute(leftArtists) || !canRefute(rightArtists)) return NameVerdict.UNKNOWN

    return NameVerdict.MISMATCH
}

/**
 * How much duration slack a candidate has earned, or none at all.
 *
 * The tiers, and what each one is paying for:
 *  - [WIDE]: the artist is confirmed and the title is confirmed. Two independent fields agree, so
 *    the +/-5s window of the pre-fix matcher applies.
 *  - [TIGHT]: one field is carrying the identification, so the duration must corroborate it within
 *    +/-2s. Three ways in: the artist matched and the title is unjudgeable (a cross-script title);
 *    the artist cannot be judged and the title is EXACT; or the artist is confirmed WRONG but an
 *    exact title and a matching album outvote it.
 *  - [REJECT]: a confirmed-different song, or a confirmed-different artist without an album to
 *    vouch for it, or nothing that identifies the recording at all.
 */
private enum class Acceptance { WIDE, TIGHT, REJECT }

/**
 * @param artistWasConstrained true when the query passed `artist_name=`, i.e. LrcLib already
 *   filtered by artist and its own spelling must not be second-guessed here.
 * @param titleWasConstrained true when the query passed `track_name=`. Note this forces the TITLE
 *   VERDICT only; the exact-title test below always reads the real strings, because "the server
 *   filtered on this field" and "these two titles are the same recording" are different claims.
 */
private fun acceptanceOf(
    track: Track,
    trackName: String?,
    artistName: String?,
    albumName: String?,
    artistWasConstrained: Boolean,
    titleWasConstrained: Boolean,
): Acceptance {
    // A field the server filtered is taken as confirmed; only the unconstrained ones are judged
    // here. Which strategy constrains which field:
    //   1  track_name + artist_name (+ album)  -> both constrained
    //   2  track_name                          -> title constrained, artist NOT
    //   3  q="artist title"                    -> NEITHER; `q=` is fuzzy full text
    //   4  q="title"                           -> NEITHER
    //   5  track_name + artist_name (raw)      -> both constrained
    // Strategies 3 and 4 are why the title has to be re-checked: without it the surviving failure is
    // the RIGHT artist's WRONG song, which a +/-5s window cannot tell apart. (`LrcLibStrategyTest`
    // pins these five flag pairs to the real ladder, end to end.)
    //
    // ASSUMPTION, not a verified fact: `track_name=` is taken to be a real title filter, on the same
    // footing as `artist_name=`. That is what the parameter is named for, but its server-side
    // matching was NOT observed here. If `track_name=` turns out to be fuzzy, Strategy 2
    // leaks the RIGHT-artist/WRONG-song failure and the fix is one flag in LrcLib.queryLyrics
    // (titleConstrained = false on Strategy 2); this function needs no change.
    val artistVerdict =
        if (artistWasConstrained) NameVerdict.MATCH else compareArtists(artistName, track.artistName)
    val titleVerdict =
        if (titleWasConstrained) NameVerdict.MATCH else compareNames(trackName, track.trackName)
    val titleIsExact = titlesAreTheSameRecording(trackName, track.trackName)

    return when {
        // A confirmed different song is never recoverable, whoever performs it.
        titleVerdict == NameVerdict.MISMATCH -> Acceptance.REJECT

        artistVerdict == NameVerdict.MATCH && titleVerdict == NameVerdict.MATCH -> Acceptance.WIDE

        // Artist confirmed, title unjudgeable (typically a cross-script title).
        artistVerdict == NameVerdict.MATCH -> Acceptance.TIGHT

        // Artist cannot vouch - generic tag, alias too short to refute, cross-script, missing. The
        // title has to carry the identification alone, so a fuzzy title is not enough.
        artistVerdict == NameVerdict.UNKNOWN -> if (titleIsExact) Acceptance.TIGHT else Acceptance.REJECT

        // Artist confirmed WRONG. Only an independent field can overrule that, and the title is not
        // independent enough (see the header: this is the reported bug's own shape). The album is.
        else -> if (titleIsExact && albumsCorroborate(albumName, track.albumName)) {
            Acceptance.TIGHT
        } else {
            Acceptance.REJECT
        }
    }
}

private fun List<Track>.bestWithinTolerance(duration: Int, toleranceSeconds: Int): Track? {
    if (isEmpty()) return null

    // Prefer synced lyrics inside the window, exactly as before.
    val syncedMatch = filter { it.syncedLyrics != null }
        .minByOrNull { abs(it.duration.toInt() - duration) }
        ?.takeIf { abs(it.duration.toInt() - duration) <= toleranceSeconds }
    if (syncedMatch != null) return syncedMatch

    return minByOrNull { abs(it.duration.toInt() - duration) }
        ?.takeIf { abs(it.duration.toInt() - duration) <= toleranceSeconds }
}

internal fun List<Track>.bestMatchingFor(duration: Int): Track? {
    if (isEmpty()) return null

    if (duration == -1) {
        return firstOrNull { it.syncedLyrics != null } ?: firstOrNull()
    }

    return minByOrNull { abs(it.duration.toInt() - duration) }
        ?.takeIf { abs(it.duration.toInt() - duration) <= 2 }
}

/** Duration-only matching with a +/-5s window. Safe ONLY on artist-constrained result sets. */
internal fun List<Track>.bestMatchingForRelaxed(duration: Int): Track? {
    if (isEmpty()) return null

    if (duration == -1) {
        return firstOrNull { it.syncedLyrics != null } ?: firstOrNull()
    }

    return bestWithinTolerance(duration, RELAXED_TOLERANCE_SECONDS)
}

/**
 * Picks the track whose lyrics we are willing to show, or null when nothing clears the bar.
 * Returning null is a deliberate outcome: showing nothing beats showing another song's lyrics.
 *
 * @param artistName the RAW artist tag, not `LrcLib.cleanArtist`'s single-artist reduction. See
 *   [compareArtists] - the cleaned form has already thrown away the collaborators this has to match.
 * @param albumName the local album tag, if the caller has one. Used for nothing except overruling a
 *   confirmed-wrong artist (see [acceptanceOf]); null simply means that escape cannot fire.
 * @param artistWasConstrained true when the query that produced this list passed `artist_name=` to
 *   LrcLib, i.e. the server already filtered by artist. Pass false for a title-only or free-text
 *   query, where the list can contain other artists entirely.
 * @param titleWasConstrained true when the query passed `track_name=`. Pass false for a free-text
 *   `q=` query: `q=` is a fuzzy full-text search, so it filters by title exactly as much as it
 *   filters by artist, which is to say not at all.
 */
internal fun List<Track>.bestMatchingFor(
    duration: Int,
    trackName: String? = null,
    artistName: String? = null,
    albumName: String? = null,
    artistWasConstrained: Boolean,
    titleWasConstrained: Boolean,
): Track? {
    if (isEmpty()) return null

    if (duration == -1) {
        if (trackName != null && artistName != null) {
            return findBestMatch(trackName, artistName, artistWasConstrained, titleWasConstrained)
        }
        return firstOrNull { it.syncedLyrics != null } ?: firstOrNull()
    }

    // Both fields filtered server-side (Strategy 1, and Strategy 5's retry). Nothing left for a
    // local check to add, and this is the common path: behaviour is the pre-fix matcher itself, the
    // same call the old `else ->` branch of getLyrics made.
    if (artistWasConstrained && titleWasConstrained) return bestMatchingForRelaxed(duration)

    val byAcceptance = groupBy { track ->
        acceptanceOf(
            track = track,
            trackName = trackName,
            artistName = artistName,
            albumName = albumName,
            artistWasConstrained = artistWasConstrained,
            titleWasConstrained = titleWasConstrained,
        )
    }

    // Two fields agree: the normal +/-5s window.
    byAcceptance[Acceptance.WIDE]?.bestWithinTolerance(duration, RELAXED_TOLERANCE_SECONDS)
        ?.let { return it }

    // One field is carrying the identification alone: the duration has to corroborate it closely.
    byAcceptance[Acceptance.TIGHT]?.bestWithinTolerance(duration, UNJUDGEABLE_TOLERANCE_SECONDS)
        ?.let { return it }

    // Everything left is a confirmed different artist or a confirmed different song. Show nothing.
    return null
}

/**
 * Ordering for the user-facing "search lyrics" sheet, which is the escape hatch when the automatic
 * pick above declines. Confirmed artists first, unjudgeable next, confirmed-wrong last - but
 * nothing is REMOVED, because there a human reads the result before it is saved, and narrowing that
 * list would strand exactly the users whose correct lyrics the automatic gate rejected.
 */
internal fun List<Track>.rankedByArtistConfidence(artistName: String?): List<Track> =
    sortedBy { track ->
        when (compareArtists(artistName, track.artistName)) {
            NameVerdict.MATCH -> 0
            NameVerdict.UNKNOWN -> 1
            NameVerdict.MISMATCH -> 2
        }
    }

/**
 * Used when the song's duration is unknown, so the duration window cannot help at all and the
 * names are the ONLY evidence available.
 *
 * A confirmed-wrong artist is REJECTED outright rather than averaged with the title score. That
 * matters: with an average of the two and a 0.6 bar, a perfect title carries any artist scoring
 * above 0.2 - which is the same "different artist, same title" failure this whole fix is
 * about, just on the unknown-duration path. There is no album escape here: with no duration at all,
 * an exact title plus an album is one corroborating signal short of what the duration path demands.
 */
private fun List<Track>.findBestMatch(
    trackName: String,
    artistName: String,
    artistWasConstrained: Boolean,
    titleWasConstrained: Boolean,
): Track? {
    // A cross-script pair carries no verdict, so it must not drag a score to zero and veto an
    // otherwise good match; judge on whichever side IS comparable. If neither side is comparable
    // and we have no duration either, we have no evidence at all - score 0 and show nothing.
    fun score(track: Track): Double {
        val titleVerdict = compareNames(trackName, track.trackName)
        val artistVerdict = compareArtists(artistName, track.artistName)
        val titleSimilarity = nameSimilarity(trackName, track.trackName)
        // Any-to-any again: score the best individual credit, or the whole-credit score for a name
        // that legitimately contains a separator word, whichever is higher.
        val artistSimilarity = maxOf(
            nameSimilarity(artistName, track.artistName),
            splitArtists(artistName).maxOfOrNull { left ->
                splitArtists(track.artistName).maxOfOrNull { right ->
                    nameSimilarity(left, right)
                } ?: 0.0
            } ?: 0.0,
        )
        return when {
            titleVerdict == NameVerdict.UNKNOWN && artistVerdict == NameVerdict.UNKNOWN -> 0.0
            titleVerdict == NameVerdict.UNKNOWN -> artistSimilarity
            artistVerdict == NameVerdict.UNKNOWN -> titleSimilarity
            else -> (titleSimilarity + artistSimilarity) / 2.0
        }
    }

    val eligible = filter { track ->
        val artistVerdict =
            if (artistWasConstrained) NameVerdict.MATCH else compareArtists(artistName, track.artistName)
        val titleVerdict =
            if (titleWasConstrained) NameVerdict.MATCH else compareNames(trackName, track.trackName)
        when {
            titleVerdict == NameVerdict.MISMATCH -> false
            artistVerdict == NameVerdict.MISMATCH -> false
            // Same rule as the duration path: with the artist unable to vouch, only an exact title
            // identifies the recording. Here it is the ONLY evidence there is.
            artistVerdict == NameVerdict.UNKNOWN -> titlesAreTheSameRecording(trackName, track.trackName)
            else -> true
        }
    }
    if (eligible.isEmpty()) return null

    return eligible
        .maxByOrNull { track -> score(track) + if (track.syncedLyrics != null) 0.1 else 0.0 }
        ?.takeIf { score(it) > NAME_MATCH_THRESHOLD }
}

private fun levenshteinDistance(str1: String, str2: String): Int {
    val len1 = str1.length
    val len2 = str2.length
    val matrix = Array(len1 + 1) { IntArray(len2 + 1) }

    for (i in 0..len1) matrix[i][0] = i
    for (j in 0..len2) matrix[0][j] = j

    for (i in 1..len1) {
        for (j in 1..len2) {
            val cost = if (str1[i - 1] == str2[j - 1]) 0 else 1
            matrix[i][j] = minOf(
                matrix[i - 1][j] + 1,      // deletion
                matrix[i][j - 1] + 1,      // insertion
                matrix[i - 1][j - 1] + cost // substitution
            )
        }
    }

    return matrix[len1][len2]
}
