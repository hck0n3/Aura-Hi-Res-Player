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
        search = { q, a -> searchSong(q, a) },
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

    private suspend fun searchSong(query: String, expectedArtist: String): MediaMetadata? {
        fun pick(items: List<SongItem>): MediaMetadata? {
            if (items.isEmpty()) return null
            if (expectedArtist.isBlank()) return items.first().toMediaMetadata()
            // Prefer a hit whose credited artists match the AI-proposed artist — first result alone
            // often drifts to covers / wrong acts and made "Lista IA" feel like it improvises.
            val matched = items.firstOrNull { song ->
                song.artists.any { artistMatches(it.name, expectedArtist) }
            }
            return matched?.toMediaMetadata()
        }

        YouTube.search(query, YouTube.SearchFilter.FILTER_SONG).getOrNull()
            ?.items?.filterIsInstance<SongItem>()
            ?.let { pick(it) }?.let { return it }

        // Resilience: many real tracks never surface under FILTER_SONG. Retry VIDEO, still requiring
        // an artist match when the AI named one — better a miss than the wrong act.
        return YouTube.search(query, YouTube.SearchFilter.FILTER_VIDEO).getOrNull()
            ?.items?.filterIsInstance<SongItem>()
            ?.let { pick(it) }
    }

    /** Accent/case-insensitive containment either way ("Bad Bunny" ↔ "Bad Bunny & Jhayco"). */
    internal fun artistMatches(candidate: String, expected: String): Boolean {
        val a = fold(candidate)
        val b = fold(expected)
        if (a.isEmpty() || b.isEmpty()) return false
        return a == b || a.contains(b) || b.contains(a)
    }

    private fun fold(value: String): String =
        Normalizer.normalize(value.trim().lowercase(), Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
}
