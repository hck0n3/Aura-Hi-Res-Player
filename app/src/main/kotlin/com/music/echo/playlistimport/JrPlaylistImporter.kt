package iad1tya.echo.music.playlistimport

import com.music.innertube.YouTube
import com.music.innertube.models.SongItem
import iad1tya.echo.music.db.MusicDatabase
import iad1tya.echo.music.db.entities.PlaylistEntity
import iad1tya.echo.music.db.entities.PlaylistSongMap
import iad1tya.echo.music.models.MediaMetadata
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Imports playlists exported from the Aura Hi-Res Player desktop app (`.jrpl.json`).
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

    /**
     * DIRECT import for an app-to-app selective migration: the exported [JrTrack.youtubeVideoId] IS a real
     * YouTube video id from THIS app, so we do NOT re-resolve tracks over the network (which failed offline /
     * on flaky networks and left playlists empty). We insert each track's song row by its embedded id and map
     * it into a new playlist. `insert(MediaMetadata)` is IGNORE-on-conflict, so a song already in the library
     * keeps its full row (thumbnail/album/liked/inLibrary) untouched; only unknown tracks get a minimal row
     * (title + artist), which fills in its artwork/metadata the first time it's played. No network, works offline.
     */
    suspend fun importDirect(database: MusicDatabase, file: JrPlaylistFile): Result {
        val tracks = file.tracks.filter { !it.youtubeVideoId.isNullOrBlank() }
        val playlistName = file.name.ifBlank { "JR Playlist" }
        val playlist = PlaylistEntity(name = playlistName)
        val seen = HashSet<String>()
        database.transaction {
            insert(playlist)
            var position = 0
            tracks.forEach { track ->
                val id = track.youtubeVideoId!!
                if (!seen.add(id)) return@forEach
                val artists = track.artist.split(",")
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                    .map { MediaMetadata.Artist(id = null, name = it) }
                insert(
                    MediaMetadata(
                        id = id,
                        title = track.title,
                        artists = artists,
                        duration = 0,
                    ),
                )
                insert(PlaylistSongMap(playlistId = playlist.id, songId = id, position = position++))
            }
        }
        return Result(playlistName = playlistName, total = file.tracks.size, resolved = seen.size)
    }

    // Resolution is shared with the AI playlist generator via SongResolver (same package).
    private suspend fun resolveTrack(
        database: MusicDatabase,
        track: JrTrack,
        byVideoId: Map<String, SongItem>,
    ): MediaMetadata? = SongResolver.resolve(
        database = database,
        title = track.title,
        artist = track.artist,
        byVideoId = byVideoId,
        videoId = track.youtubeVideoId,
    )
}
