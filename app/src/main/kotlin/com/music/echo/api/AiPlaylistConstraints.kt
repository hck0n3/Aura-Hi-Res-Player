package iad1tya.echo.music.api

import iad1tya.echo.music.playlistimport.SongResolver
import java.text.Normalizer

/**
 * Lightweight intent helpers for Lista IA. Pure / Android-free so unit tests can lock
 * "solo este artista" without mocking network.
 */
object AiPlaylistConstraints {

    /**
     * When the user clearly asks for ONE artist, return that name; otherwise null (genre/mood mixes).
     * Patterns cover Spanish/English phrasing owners actually type into the dialog.
     */
    fun extractSoloArtist(prompt: String): String? {
        val raw = prompt.trim()
        if (raw.isBlank()) return null
        val patterns = listOf(
            Regex("""(?iu)^\s*solo\s+(.+?)\s*$"""),
            Regex("""(?iu)^\s*only\s+(.+?)\s*$"""),
            Regex("""(?iu)^\s*canciones\s+de\s+(.+?)\s*$"""),
            Regex("""(?iu)^\s*temas\s+de\s+(.+?)\s*$"""),
            Regex("""(?iu)^\s*m[uú]sica\s+de\s+(.+?)\s*$"""),
            Regex("""(?iu)^\s*playlist\s+de\s+(.+?)\s*$"""),
            Regex("""(?iu)^\s*(.+?)\s+solamente\s*$"""),
            Regex("""(?iu)^\s*(.+?)\s+nada\s+m[aá]s\s*$"""),
            Regex("""(?iu)\bsolo\s+([^,.]+?)(?:\s+canciones|\s+temas)?\s*$"""),
            Regex("""(?iu)\bcanciones\s+(?:solo\s+)?de\s+([^,.]+?)\s*$"""),
        )
        for (p in patterns) {
            val m = p.find(raw) ?: continue
            val name = cleanArtistCandidate(m.groupValues.getOrNull(1).orEmpty())
            if (name != null) return name
        }
        return null
    }

    fun artistAllowed(trackArtist: String, soloArtist: String?): Boolean {
        if (soloArtist.isNullOrBlank()) return true
        return SongResolver.artistMatches(trackArtist, soloArtist)
    }

    private fun cleanArtistCandidate(value: String): String? {
        var s = value.trim()
            .trim('"', '\'', '«', '»')
            .replace(Regex("""(?iu)\s+(canciones|temas|m[uú]sica|playlist)\s*$"""), "")
            .trim()
        if (s.length < 2 || s.length > 80) return null
        // Reject prompts that are clearly genres/moods, not people.
        val folded = fold(s)
        val genreLike = listOf(
            "rock", "pop", "salsa", "reggaeton", "cumbia", "jazz", "blues", "metal",
            "punk", "hip hop", "rap", "trap", "electronica", "electrónica", "lofi", "lo-fi",
            "clasica", "clásica", "romantica", "romántica", "triste", "fiesta", "gym",
            "correr", "dormir", "estudio", "workout",
        )
        if (genreLike.any { folded == it || folded.startsWith("$it ") }) return null
        return s
    }

    private fun fold(value: String): String =
        Normalizer.normalize(value.trim().lowercase(), Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
}
