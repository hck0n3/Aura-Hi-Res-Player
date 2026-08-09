package iad1tya.echo.music.playback

import android.content.Context
import androidx.core.net.toUri
import androidx.media3.exoplayer.offline.DownloadRequest
import androidx.media3.exoplayer.offline.DownloadService

/** Enqueue audio (+ companion video when [isVideoSong]). */
fun enqueueSongDownloads(
    context: Context,
    songId: String,
    title: String,
    isVideoSong: Boolean,
) {
    val audio = DownloadRequest.Builder(songId, songId.toUri())
        .setCustomCacheKey(songId)
        .setData(title.toByteArray())
        .build()
    DownloadService.sendAddDownload(context, ExoDownloadService::class.java, audio, false)
    if (isVideoSong) {
        val vidId = videoDownloadMediaId(songId)
        val video = DownloadRequest.Builder(vidId, vidId.toUri())
            .setCustomCacheKey(vidId)
            .setData(title.toByteArray())
            .build()
        DownloadService.sendAddDownload(context, ExoDownloadService::class.java, video, false)
    }
}

/** Remove audio and, for video songs, the companion video download. */
fun removeSongDownloads(
    context: Context,
    songId: String,
    isVideoSong: Boolean,
) {
    DownloadService.sendRemoveDownload(context, ExoDownloadService::class.java, songId, false)
    if (isVideoSong) {
        DownloadService.sendRemoveDownload(
            context,
            ExoDownloadService::class.java,
            videoDownloadMediaId(songId),
            false,
        )
    }
}
