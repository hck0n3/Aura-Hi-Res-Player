package iad1tya.echo.music.playlistimport

import com.music.innertube.YouTube
import com.music.innertube.models.SongItem
import iad1tya.echo.music.db.MusicDatabase
import iad1tya.echo.music.db.entities.PlaylistEntity
import iad1tya.echo.music.db.entities.PlaylistSongMap
import iad1tya.echo.music.models.MediaMetadata
import iad1tya.echo.music.models.toMediaMetadata
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Imports playlists exported from the JR Music Pro desktop app (`.jrpl.json`).
 *
 * Per track, resolution order matches the desktop's own data model:
 *  1. an existing local library song matching title + artist (no network),
 *  2. the embedded `youtubeVideoId` (resolved in batches via [YouTube.queue]),
 *  3. a YouTube search by "title artist".
 * Resolved songs are persisted and added to a new local playlist named after the file.
 */
object JrPlaylistImporter {

    @Serializable
    data class JrPlaylistFile(
        val version: Int = 1,
        val name: String = "",
        val exportedAt: String = "",
        val tracks: List<JrTrack> = emptyList(),
    )

    @Serializable
    data class JrTrack(
        val title: String = "",
        val artist: String = "",
        val album: String = "",
        val duration: String = "",
        val youtubeVideoId: String? = null,
    )

    data class Result(val playlistName: String, val total: Int, val resolved: Int)

    private val json = Json { ignoreUnknownKeys = true }

    fun parse(content: String): JrPlaylistFile = json.decodeFromString(content)

    /** Resolves and persists [file] into a new playlist. Returns counts for a result toast. */
    suspend fun import(database: MusicDatabase, file: JrPlaylistFile): Result {
        // Batch-resolve embedded video ids up front to cut request count.
        val videoIds = file.tracks.mapNotNull { it.youtubeVideoId?.takeIf(String::isNotBlank) }.distinct()
        val byVideoId = HashMap<String, SongItem>()
        videoIds.chunked(50).forEach { chunk ->
            YouTube.queue(videoIds = chunk).getOrNull()?.forEach { song -> byVideoId[song.id] = song }
        }

        val resolved = ArrayList<MediaMetadata>(file.tracks.size)
        for (track in file.tracks) {
            val metadata = resolveTrack(database, track, byVideoId)
            if (metadata != null) resolved += metadata
        }

        val playlistName = file.name.ifBlank { "JR Playlist" }
        val playlist = PlaylistEntity(name = playlistName)
        val ordered = resolved.distinctBy { it.id }
        // Single transaction: create the playlist, persist songs, map them in order.
        // Done atomically so there is no read-back race on the freshly-inserted row.
        database.transaction {
            insert(playlist)
            ordered.forEachIndexed { index, metadata ->
                insert(metadata)
                insert(
                    PlaylistSongMap(
                        playlistId = playlist.id,
                        songId = metadata.id,
                        position = index,
                    ),
                )
            }
        }

        return Result(playlistName = playlistName, total = file.tracks.size, resolved = ordered.size)
    }

    private suspend fun resolveTrack(
        database: MusicDatabase,
        track: JrTrack,
        byVideoId: Map<String, SongItem>,
    ): MediaMetadata? {
        // 1. existing local library song by title + artist
        localMatch(database, track)?.let { return it }

        // 2. embedded YouTube video id
        track.youtubeVideoId?.let { id -> byVideoId[id]?.let { return it.toMediaMetadata() } }

        // 3. search by title + artist
        val query = listOf(track.title, track.artist).filter { it.isNotBlank() }.joinToString(" ")
        if (query.isBlank()) return null
        val items = YouTube.search(query, YouTube.SearchFilter.FILTER_SONG).getOrNull()
            ?.items?.filterIsInstance<SongItem>()
        return items?.firstOrNull()?.toMediaMetadata()
    }

    private suspend fun localMatch(database: MusicDatabase, track: JrTrack): MediaMetadata? {
        if (track.title.isBlank()) return null
        val candidates = database.searchSongs(track.title, previewSize = 10).first()
        val match = candidates.firstOrNull { song ->
            song.song.title.equals(track.title, ignoreCase = true) &&
                (track.artist.isBlank() || song.artists.any { it.name.equals(track.artist, ignoreCase = true) })
        } ?: return null
        return match.toMediaMetadata()
    }
}
