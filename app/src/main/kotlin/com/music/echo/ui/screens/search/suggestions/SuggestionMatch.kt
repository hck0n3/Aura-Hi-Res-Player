package iad1tya.echo.music.ui.screens.search.suggestions

/**
 * Pure matching for Sugerencias taps. Isolated so a song tap cannot resolve a video (or the reverse),
 * and so album/artist navigation cannot open the first YouTube hit that merely shares a word.
 */
object SuggestionMatch {
    enum class Kind { SONG, VIDEO }

    fun cacheKey(kind: Kind, title: String, artist: String): String =
        "${kind.name}:${normalize(title)}|${normalize(artist)}"

    fun normalize(s: String): String =
        s.lowercase()
            .replace('‘', '\'')
            .replace('’', '\'')
            .replace('“', '"')
            .replace('”', '"')
            .replace('–', '-')
            .replace('—', '-')
            .replace(Regex("""\s*[(\[](?:feat|ft|featuring)\.?\s[^)\]]*[)\]]"""), " ")
            .replace(Regex("""\s+(?:feat|ft|featuring)\.?\s.*$"""), " ")
            .replace(Regex("""\s+"""), " ")
            .trim()

    /**
     * Lead credited name. Split on feat/ft/& only — never on comma, or "Tyler, The Creator" becomes
     * "Tyler" and FILTER_ARTIST opens the wrong channel.
     */
    fun primaryArtistName(credited: String): String =
        credited.split(Regex("""\s*(?:&|feat\.?|ft\.?|featuring)\s+""", RegexOption.IGNORE_CASE))
            .first()
            .trim()

    fun artistMatches(candidate: String, expected: String): Boolean {
        val a = normalize(candidate)
        val b = normalize(expected)
        if (a.isEmpty() || b.isEmpty()) return false
        if (a == b) return true
        val shorter = if (a.length <= b.length) a else b
        val longer = if (a.length <= b.length) b else a
        // "Ed" ⊂ "Ed Sheeran" is a false positive. Require a real token, not a 1–3 letter stub.
        if (shorter.length < 4) return false
        val idx = longer.indexOf(shorter)
        if (idx < 0) return false
        val beforeOk = idx == 0 || !longer[idx - 1].isLetterOrDigit()
        val afterIdx = idx + shorter.length
        val afterOk = afterIdx == longer.length || !longer[afterIdx].isLetterOrDigit()
        return beforeOk && afterOk
    }

    fun titleMatches(candidate: String, expected: String): Boolean {
        val a = normalize(candidate)
        val b = normalize(expected)
        if (a.isEmpty() || b.isEmpty()) return false
        if (a == b) return true
        val shorter = if (a.length <= b.length) a else b
        val longer = if (a.length <= b.length) b else a
        // "Love" ⊂ "Love Story" is the same class of false positive. Allow only a qualified suffix
        // ("SOS (Deluxe)", "GUTS - Live") after a stem of at least 6 characters.
        if (shorter.length < 6) return false
        if (!longer.startsWith(shorter)) return false
        return longer.length == shorter.length || !longer[shorter.length].isLetterOrDigit()
    }

    fun pickTitleArtist(
        titles: List<String>,
        artists: List<List<String>>,
        expectedTitle: String,
        expectedArtist: String,
    ): Int? {
        if (titles.isEmpty()) return null
        titles.indices.firstOrNull { i ->
            normalize(titles[i]) == normalize(expectedTitle) &&
                artists.getOrNull(i).orEmpty().any { artistMatches(it, expectedArtist) }
        }?.let { return it }
        return titles.indices.firstOrNull { i ->
            titleMatches(titles[i], expectedTitle) &&
                artists.getOrNull(i).orEmpty().any { artistMatches(it, expectedArtist) }
        }
    }

    fun pickArtist(names: List<String>, expected: String): Int? {
        if (names.isEmpty() || expected.isBlank()) return null
        names.indices.firstOrNull { normalize(names[it]) == normalize(expected) }?.let { return it }
        return names.indices.firstOrNull { artistMatches(names[it], expected) }
    }
}
