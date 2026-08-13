package iad1tya.echo.music.playlistimport

import com.music.innertube.YouTube
import com.music.innertube.models.SongItem
import iad1tya.echo.music.db.MusicDatabase
import iad1tya.echo.music.models.MediaMetadata
import iad1tya.echo.music.models.toMediaMetadata
import kotlinx.coroutines.flow.first
import java.text.Normalizer

/**
 * Resolves a `(title, artist[, videoId])` into a library [MediaMetadata]. Shared by the JR playlist
 * importer and the AI playlist generator (DRY). Resolution order:
 *  1. an existing local library song matching title + artist (no network),
 *  2. an embedded YouTube video id (via [byVideoId]),
 *  3. a YouTube search by "title artist", preferring a result whose artists match.
 */
object SongResolver {

    suspend fun resolve(
        database: MusicDatabase,
        title: String,
        artist: String,
        byVideoId: Map<String, SongItem> = emptyMap(),
        videoId: String? = null,
    ): MediaMetadata? = resolveOrdered(
        title = title,
        artist = artist,
        videoId = videoId,
        localMatch = { t, a -> localMatch(database, t, a) },
        fromVideoId = { id -> byVideoId[id]?.toMediaMetadata() },
        search = { _, a -> searchSong(title, a) },
    )

    /**
     * Pure precedence logic with the three lookups injected, so the ordering is unit-testable
     * without a database or network. Generic on the result type purely to keep that seam Android-free.
     */
    internal suspend fun <T> resolveOrdered(
        title: String,
        artist: String,
        videoId: String?,
        localMatch: suspend (title: String, artist: String) -> T?,
        fromVideoId: (videoId: String) -> T?,
        search: suspend (query: String, expectedArtist: String) -> T?,
    ): T? {
        localMatch(title, artist)?.let { return it }
        if (videoId != null) fromVideoId(videoId)?.let { return it }
        val query = listOf(title, artist).filter { it.isNotBlank() }.joinToString(" ")
        if (query.isBlank()) return null
        return search(query, artist)
    }

    private suspend fun localMatch(database: MusicDatabase, title: String, artist: String): MediaMetadata? {
        if (title.isBlank()) return null
        val candidates = database.searchSongs(title, previewSize = 10).first()
        val match = candidates.firstOrNull { song ->
            song.song.title.equals(title, ignoreCase = true) &&
                (artist.isBlank() || song.artists.any { artistMatches(it.name, artist) })
        } ?: return null
        return match.toMediaMetadata()
    }

    /**
     * YouTube search that requires the proposed TITLE and (when named) ARTIST. An invented title
     * with a real artist must not resolve to "any song by that artist".
     */
    private suspend fun searchSong(expectedTitle: String, expectedArtist: String): MediaMetadata? {
        if (expectedTitle.isBlank()) return null
        val query = listOf(expectedTitle, expectedArtist).filter { it.isNotBlank() }.joinToString(" ")
        fun pick(items: List<SongItem>): MediaMetadata? {
            val index = pickSearchHit(
                titles = items.map { it.title },
                artists = items.map { it.artists.map { a -> a.name } },
                expectedTitle = expectedTitle,
                expectedArtist = expectedArtist,
            ) ?: return null
            return items[index].toMediaMetadata()
        }

        YouTube.search(query, YouTube.SearchFilter.FILTER_SONG).getOrNull()
            ?.items?.filterIsInstance<SongItem>()
            ?.let { pick(it) }?.let { return it }

        return YouTube.search(query, YouTube.SearchFilter.FILTER_VIDEO).getOrNull()
            ?.items?.filterIsInstance<SongItem>()
            ?.let { pick(it) }
    }

    /**
     * Index of the first hit whose title AND artist match the AI proposal. No title match → null,
     * never the first result of that artist.
     */
    internal fun pickSearchHit(
        titles: List<String>,
        artists: List<List<String>>,
        expectedTitle: String,
        expectedArtist: String,
    ): Int? {
        if (titles.isEmpty() || expectedTitle.isBlank()) return null
        fun titleEquals(candidate: String): Boolean = fold(candidate) == fold(expectedTitle)
        fun titleClose(candidate: String): Boolean {
            val a = fold(candidate)
            val b = fold(expectedTitle)
            if (a == b) return true
            val shorter = if (a.length <= b.length) a else b
            val longer = if (a.length <= b.length) b else a
            if (shorter.length < 6) return false
            return longer.startsWith(shorter) &&
                (longer.length == shorter.length || !longer[shorter.length].isLetterOrDigit())
        }
        fun artistOk(names: List<String>): Boolean {
            if (expectedArtist.isBlank()) return true
            return names.any { artistMatches(it, expectedArtist) }
        }
        titles.indices.firstOrNull { i ->
            titleEquals(titles[i]) && artistOk(artists.getOrNull(i).orEmpty())
        }?.let { return it }
        return titles.indices.firstOrNull { i ->
            titleClose(titles[i]) && artistOk(artists.getOrNull(i).orEmpty())
        }
    }

    /** Accent/case-insensitive containment either way ("Bad Bunny" ↔ "Bad Bunny & Jhayco"). */
    internal fun artistMatches(candidate: String, expected: String): Boolean {
        val a = fold(candidate)
        val b = fold(expected)
        if (a.isEmpty() || b.isEmpty()) return false
        if (a == b) return true
        val shorter = if (a.length <= b.length) a else b
        val longer = if (a.length <= b.length) b else a
        if (shorter.length < 4) return false
        val idx = longer.indexOf(shorter)
        if (idx < 0) return false
        val beforeOk = idx == 0 || !longer[idx - 1].isLetterOrDigit()
        val afterIdx = idx + shorter.length
        val afterOk = afterIdx == longer.length || !longer[afterIdx].isLetterOrDigit()
        return beforeOk && afterOk
    }

    private fun fold(value: String): String =
        Normalizer.normalize(value.trim().lowercase(), Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
}
