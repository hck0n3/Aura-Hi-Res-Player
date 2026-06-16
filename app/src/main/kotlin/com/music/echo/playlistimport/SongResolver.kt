package iad1tya.echo.music.playlistimport

import com.music.innertube.YouTube
import com.music.innertube.models.SongItem
import iad1tya.echo.music.db.MusicDatabase
import iad1tya.echo.music.models.MediaMetadata
import iad1tya.echo.music.models.toMediaMetadata
import kotlinx.coroutines.flow.first

/**
 * Resolves a `(title, artist[, videoId])` into a library [MediaMetadata]. Shared by the JR playlist
 * importer and the AI playlist generator (DRY). Resolution order:
 *  1. an existing local library song matching title + artist (no network),
 *  2. an embedded YouTube video id (via [byVideoId]),
 *  3. a YouTube search by "title artist".
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
        search = { q -> searchSong(q) },
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
        search: suspend (query: String) -> T?,
    ): T? {
        localMatch(title, artist)?.let { return it }
        if (videoId != null) fromVideoId(videoId)?.let { return it }
        val query = listOf(title, artist).filter { it.isNotBlank() }.joinToString(" ")
        if (query.isBlank()) return null
        return search(query)
    }

    private suspend fun localMatch(database: MusicDatabase, title: String, artist: String): MediaMetadata? {
        if (title.isBlank()) return null
        val candidates = database.searchSongs(title, previewSize = 10).first()
        val match = candidates.firstOrNull { song ->
            song.song.title.equals(title, ignoreCase = true) &&
                (artist.isBlank() || song.artists.any { it.name.equals(artist, ignoreCase = true) })
        } ?: return null
        return match.toMediaMetadata()
    }

    private suspend fun searchSong(query: String): MediaMetadata? {
        val items = YouTube.search(query, YouTube.SearchFilter.FILTER_SONG).getOrNull()
            ?.items?.filterIsInstance<SongItem>()
        return items?.firstOrNull()?.toMediaMetadata()
    }
}
