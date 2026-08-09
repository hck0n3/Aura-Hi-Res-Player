package iad1tya.echo.music.utils

import android.content.Context
import iad1tya.echo.music.constants.AutoDownloadOnLikeKey
import iad1tya.echo.music.db.entities.Song
import iad1tya.echo.music.playback.enqueueSongDownloads
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * Enqueue offline downloads for [songs] when the user's "download on like" preference is on
 * (default true). Used when liking an album so the whole album is saved offline, mirroring the
 * per-song auto-download in MusicService.toggleLike.
 */
suspend fun downloadSongsIfAutoOnLike(context: Context, songs: List<Song>) {
    if (songs.isEmpty()) return
    val auto = context.dataStore.data.map { it[AutoDownloadOnLikeKey] ?: true }.first()
    if (!auto) return
    songs.forEach { song ->
        enqueueSongDownloads(
            context,
            song.id,
            song.song.title,
            song.song.isVideo,
        )
    }
}
