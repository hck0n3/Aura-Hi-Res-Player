package com.music.jiosaavn

object SaavnMatcher {

    private const val VARIANT_PENALTY = -40

    /**
     * Everything that is not a letter or a digit IN ANY SCRIPT becomes a separator.
     *
     * Deliberately `\p{L}\p{N}` and NOT `[^a-z0-9]`: the ASCII form DELETES every Devanagari, CJK,
     * Hangul, Cyrillic, Greek and Thai character, leaving an empty token set. JioSaavn is an INDIAN
     * service and YouTube Music stores plenty of titles in native script, so an ASCII-only squash
     * makes the title gate reject 100% of that catalogue.
     */
    private val nonAlphanumeric = Regex("[^\\p{L}\\p{N}]+")
    private val whitespace = Regex("\\s+")

    /**
     * Combining diacritics for the LATIN/Greek/Cyrillic blocks only (U+0300–U+036F).
     *
     * Deliberately NOT `\p{Mn}`: Indic matras, the anusvara and the virama are also Mn, and stripping
     * them rewrites the Hindi word itself ("तेरा" -> "तर"). Restricting the range folds "ó"->"o"
     * while leaving Indic and other complex scripts untouched.
     */
    private val combiningMarks = Regex("[\\u0300-\\u036F]+")

    /**
     * Latin letters that carry no NFD decomposition, so accent-folding alone cannot reach them and
     * they would survive as distinct characters ("straße" never matching "strasse").
     */
    private val nonDecomposingLatin = mapOf(
        'ß' to "ss", 'ø' to "o", 'ł' to "l", 'đ' to "d", 'ð' to "d", 'þ' to "th",
        'ı' to "i", 'æ' to "ae", 'œ' to "oe", 'å' to "a",
    )

    /**
     * Trailing context a title carries but a song identity does not:
     *  - a parenthesised/bracketed segment ("(Official Video)", "(From \"Brahmastra\")", "(feat. X)"),
     *  - everything from a feature credit onward ("ft. Justin Bieber"),
     *  - everything after a pipe, which is how Indian YouTube titles list the cast
     *    ("Kesariya – Brahmastra | Ranbir Kapoor | Alia Bhatt | Pritam").
     */
    private val parentheticalSegment = Regex("[\\(\\[\\{][^\\)\\]\\}]*[\\)\\]\\}]?")
    // "with" is deliberately NOT a credit marker: it is an ordinary English word, and treating it as one
    // truncates "Dancing With Myself" to "Dancing" — which then matches "Dancing Queen" exactly and plays
    // the wrong song. Only the unambiguous credit abbreviations qualify.
    private val featureCreditTail = Regex("(?:^|[\\s\\-–—,])(?:feat|ft|featuring)[\\.\\s].*$", RegexOption.IGNORE_CASE)
    private val pipeTail = Regex("\\|.*$")

    /** Dash used as a separator — surrounded by whitespace, so "Jay-Z" is never split. */
    private val dashSeparator = Regex("\\s+[\\-–—]\\s+")

    private val variantMarkerPhrases = listOf(
        "karaoke",
        "instrumental",
        "minus one",
        "cover",
        "remix",
        "lo-fi",
        "lofi",
        "sped up",
        "slowed",
        "reverb"
    )

    private val variantMarkers = variantMarkerPhrases.map { " ${normalize(it)} " }

    /**
     * Penalizes Saavn variant titles (karaoke/instrumental/remix/…) while preserving explicit variant
     * searches: if the YouTube title itself contains the marker, no penalty is applied.
     */
    fun variantPenalty(ytTitle: String, candidateName: String): Int {
        val normalizedYtTitle = searchable(ytTitle)
        val normalizedCandidateName = searchable(candidateName)
        var penalty = 0

        for (marker in variantMarkers) {
            if (!normalizedYtTitle.contains(marker) && normalizedCandidateName.contains(marker)) {
                penalty += VARIANT_PENALTY
            }
        }

        return penalty
    }

    // ==================== TITLE GATE ====================

    /**
     * Minimum [titleSimilarity] a Saavn candidate must reach before ANY other signal (duration,
     * artist, explicit flag) is allowed to qualify it.
     *
     * WHY: the caller's score is ADDITIVE, so a candidate whose title has nothing in common with the
     * YouTube title can still clear the confidence threshold purely on duration (+30), artist (+20)
     * and the explicit flag (+5) — a DIFFERENT song by the same artist, or (when the artist is
     * unknown, which makes the artist check vacuous) a different song by anyone. [variantPenalty]
     * cannot catch that: a clean-but-wrong title carries no variant marker. This gate gives the
     * Saavn path the discipline the Qobuz matcher already has, where title similarity is a
     * MULTIPLICATIVE factor and a zero title kills the candidate outright.
     */
    /**
     * Strictly more than half the words in common.
     *
     * 0.5 is exactly the Jaccard of "one shared word out of two" — and that single value covers BOTH
     * a real match ("Karol G - PROVENZA" vs "Provenza") and a wrong-song substitution ("Amor" vs
     * "Amor Eterno"), so no threshold at 0.5 can separate them. The real match is instead recognised
     * structurally, by [containmentCredit], which scores it 1.0; that frees this threshold to demand
     * a genuine majority and reject the ambiguous tie.
     */
    const val MIN_TITLE_SIMILARITY = 0.6

    /**
     * Decoration tokens that appear on YouTube titles but never identify a song. Stripped from BOTH
     * sides (symmetric, so it only ever removes noise), which is what lets
     * "Karol G - PROVENZA (Official Video)" still match "PROVENZA".
     */
    private val decorationTokens = setOf(
        "official", "video", "videoclip", "audio", "lyric", "lyrics", "letra",
        "visualizer", "hd", "hq", "4k", "mv", "mp3", "clip",
        // Feature credits: Saavn names routinely drop them, YouTube titles routinely carry them.
        "feat", "ft", "featuring", "prod"
    )

    /**
     * Title similarity in 0..1.
     *
     * Shape (mirrors the Qobuz matcher, adapted to the noise a YouTube title carries):
     *  1. Tokens are accent-folded, so "Tití Me Preguntó" and "Titi Me Pregunto" are the SAME words.
     *     Without this the plain `[^a-z0-9]` normalization shreds every accented word ("preguntó" ->
     *     "pregunt") and a Spanish-language library would score 0 on titles that match perfectly.
     *  2. [artistHint] tokens and [decorationTokens] are removed from BOTH sides, so the comparison is
     *     song-title vs song-title rather than "ARTIST - TITLE (Official Video)" vs "TITLE".
     *  3. The score is the max of Jaccard overlap and an ASYMMETRIC containment credit.
     *
     * The containment credit is granted only when the two CORE titles (see [coreTokenList]) are the
     * same set, or when one contains the other AND both start with the same leading word. The leading
     * word is what separates the two directions that look identical to a token count:
     *  - "Kesariya – Brahmastra | Ranbir Kapoor | …" vs "Kesariya (From \"Brahmastra\")" — same lead,
     *    the extra text is context. Credit.
     *  - "Sola" vs "Ella Baila Sola" — different lead, the candidate is a DIFFERENT song that merely
     *    ends with the requested word. No credit; it falls to Jaccard (0.33) and is rejected. That
     *    substitution is exactly what this gate exists to stop.
     */
    @JvmOverloads
    fun titleSimilarity(ytTitle: String, candidateName: String, artistHint: String = ""): Double {
        val noise = decorationTokens + tokens(artistHint)
        // Strip noise, but never strip a side down to nothing (a title that IS the artist name, or
        // is made entirely of decoration words, must still be comparable).
        val setA = tokens(ytTitle).let { raw -> (raw - noise).ifEmpty { raw } }
        val setB = tokens(candidateName).let { raw -> (raw - noise).ifEmpty { raw } }
        if (setA.isEmpty() || setB.isEmpty()) return 0.0

        val intersection = setA.intersect(setB)
        if (intersection.isEmpty()) return 0.0

        val jaccard = intersection.size.toDouble() / setA.union(setB).size.toDouble()

        val coreB = coreTokenList(candidateName).filterNot { it in noise }.ifEmpty { setB.toList() }
        val coreSetB = coreB.toSet()
        val containment = coreCandidateSegments(ytTitle)
            .map { segment -> segment.filterNot { it in noise }.ifEmpty { segment } }
            .maxOf { coreA -> containmentCredit(coreA, coreB, coreSetB) }

        return maxOf(jaccard, containment)
    }

    /**
     * 1.0 when one segment of the YouTube title IS the candidate's title, 0.0 otherwise.
     *
     * EQUALITY ONLY — deliberately not "the candidate's words are a subset". Subset matching cannot
     * tell "Kesariya – Brahmastra | cast" -> "Kesariya" (context, correct) apart from "Gone With The
     * Wind" -> "Gone" (a different song that merely starts the same way); both are a subset sharing a
     * leading word. Splitting the YouTube title into segments already covers the context case
     * exactly, so equality is all that is needed and it cannot manufacture that false positive.
     */
    private fun containmentCredit(coreA: List<String>, coreB: List<String>, coreSetB: Set<String>): Double {
        val coreSetA = coreA.toSet()
        if (coreSetA != coreSetB) return 0.0
        // "Distinctive" = unlikely to collide by chance: a longer word or one carrying a digit. Stops
        // two titles made entirely of short common words from certifying each other.
        return if (coreSetA.any { it.length > 3 || it.any(Char::isDigit) }) 1.0 else 0.0
    }

    /**
     * True when the two strings are written in scripts that can be compared token-by-token at all.
     *
     * YouTube Music stores plenty of titles in native script while Saavn returns the transliterated
     * Latin name (or the reverse). No token metric can bridge "तेरा बन जाऊंगा" and "Tera Ban Jaunga";
     * the honest answer is "this gate has no competence here", NOT "reject". When that happens the
     * gate ABSTAINS and the caller's additive score (duration + artist) decides on its own, exactly
     * as it did before the gate existed — so the gate can never make a cross-script library worse.
     */
    fun scriptsAreIncomparable(a: String, b: String): Boolean {
        val tokensA = tokens(a)
        val tokensB = tokens(b)
        // An EMPTY side is not "incomparable", it is unmatchable: abstaining there would open the gate
        // for every candidate whenever a title failed to load. Only two non-empty, disjoint-script
        // sides earn an abstention.
        if (tokensA.isEmpty() || tokensB.isEmpty()) return false
        fun isAscii(token: String) = token.all { it.code < 0x80 }
        // XOR, not "shares nothing": the common real case is MIXED — "केसरिया (Lofi)" vs "Kesariya" has a
        // Latin token on both sides ("lofi"), yet the words that actually name the song are in scripts the
        // other side does not use at all. Requiring only a shared script CLASS would let that pair be
        // judged, score 0 and be rejected. One side carrying a script the other never uses is the signal.
        val aHasNonLatin = tokensA.any { !isAscii(it) }
        val bHasNonLatin = tokensB.any { !isAscii(it) }
        return aHasNonLatin != bHasNonLatin
    }

    /**
     * True when [candidateName] is title-wise close enough to [ytTitle] to be considered at all, or
     * when the two are written in scripts this matcher cannot compare (see [scriptsAreIncomparable]).
     */
    @JvmOverloads
    fun titleMatches(ytTitle: String, candidateName: String, artistHint: String = ""): Boolean =
        scriptsAreIncomparable(ytTitle, candidateName) ||
            titleSimilarity(ytTitle, candidateName, artistHint) >= MIN_TITLE_SIMILARITY

    /**
     * Word-overlap ratio in 0..1 as `|A ∩ B| / max(|A|, |B|)` — the exact formula the caller's
     * additive scorer uses for its title and artist points, exposed here so the gate and the score
     * share ONE tokenizer (in particular the accent folding).
     */
    fun overlapRatio(a: String, b: String): Double {
        val setA = tokens(a)
        val setB = tokens(b)
        if (setA.isEmpty() || setB.isEmpty()) return 0.0
        return setA.intersect(setB).size.toDouble() / maxOf(setA.size, setB.size).toDouble()
    }

    /**
     * Lowercase, accent-fold, non-alphanumerics to spaces, drop single-character tokens (initials and
     * stray letters carry no matching signal).
     */
    private fun tokens(value: String): Set<String> = tokenList(value).toSet()

    /** Same tokens as [tokens], in title order — the leading word is a matching signal of its own. */
    private fun tokenList(value: String): List<String> =
        normalize(value)
            .split(whitespace)
            // Single ASCII letters are initials and stray noise, but a single CJK character IS a whole
            // word ("光", "夜"), so dropping every 1-length token would empty out short Japanese/Chinese
            // titles and leave the gate nothing to compare.
            .filter { it.length > 1 || it.any { c -> c.code >= 0x80 } }
            .distinct()

    private fun searchable(value: String): String = " ${normalize(value)} "

    /**
     * NFD-decompose, fold Latin diacritics and the non-decomposing Latin letters, then treat every
     * remaining non-letter/non-digit as a separator. Accented characters degrade to their base letter
     * ("ñ" -> "n", "ó" -> "o") instead of being deleted mid-word, and non-Latin scripts survive whole.
     */
    private fun normalize(value: String): String {
        val lowered = value.lowercase()
        val folded = if (lowered.any { it in nonDecomposingLatin }) {
            buildString(lowered.length) {
                for (ch in lowered) append(nonDecomposingLatin[ch] ?: ch)
            }
        } else {
            lowered
        }
        return java.text.Normalizer.normalize(folded, java.text.Normalizer.Form.NFD)
            .replace(combiningMarks, "")
            .replace(nonAlphanumeric, " ")
            .trim()
            .replace(whitespace, " ")
    }

    /**
     * The identifying part of a title: the text with parenthetical segments, feature-credit tails and
     * pipe-separated cast lists removed. Applied to BOTH sides, so it only ever removes noise.
     */
    private fun coreTokenList(value: String): List<String> {
        val stripped = value
            .replace(parentheticalSegment, " ")
            .replace(pipeTail, " ")
            .replace(featureCreditTail, " ")
        return tokenList(stripped).ifEmpty { tokenList(value) }
    }

    /**
     * The core, plus each space-delimited dash segment of it, because a YouTube title puts the song
     * title on ONE side of the dash and something else on the other — and which side is which is not
     * knowable in general:
     *  - "Karol G - PROVENZA"        -> artist on the left, title on the right
     *  - "Kesariya – Brahmastra"     -> title on the left, film on the right
     *
     * Trying both is what lets a candidate match either shape without a rule that guesses wrong half
     * the time. Only dashes SURROUNDED BY SPACES split, so "Jay-Z" and "Spider-Man" stay whole.
     */
    private fun coreCandidateSegments(value: String): List<List<String>> {
        val full = coreTokenList(value)
        val stripped = value
            .replace(parentheticalSegment, " ")
            .replace(pipeTail, " ")
            .replace(featureCreditTail, " ")
        val segments = stripped.split(dashSeparator)
            .map { tokenList(it) }
            .filter { it.isNotEmpty() }
        return (listOf(full) + segments).filter { it.isNotEmpty() }.distinct()
    }
}
