package iad1tya.echo.music.utils

import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
import androidx.media3.exoplayer.offline.DownloadRequest
import androidx.media3.exoplayer.offline.DownloadService
import iad1tya.echo.music.db.MusicDatabase
import iad1tya.echo.music.playback.DownloadUtil
import iad1tya.echo.music.playback.ExoDownloadService
import iad1tya.echo.music.playback.MusicService

/**
 * "Volver a obtener" — force the next play of [songId] to RE-RESOLVE its stream instead of replaying the audio
 * we already hold. Refreshing the metadata alone is a no-op for the audio: the resolver serves a songUrlCache
 * hit, cached bytes, or the fully-downloaded short-circuit long before it would ask YouTube again.
 *
 * [isCurrentlyPlaying] deliberately spares the format row of the track on air. That row is not just metadata:
 * it LOCKS the running stream's container (dropping it lets the next fetch return a different one to an
 * extractor mid-period) and it carries the loudness the normalization reads (deleting it re-levels the volume
 * mid-song — the exact jump the currentFormat de-dup in MusicService exists to prevent). Nothing is lost: the
 * URL and bytes are still dropped, so the next play re-resolves and overwrites the row anyway.
 *
 * [isDownloaded] songs are re-enqueued, never merely deleted. A refetch must not be a silent no-op for an
 * offline track (the fully-downloaded short-circuit would serve the old bytes forever) and must not cost the
 * user the offline copy either — so the download is removed and immediately requested again at their
 * downloadQuality (a separate preference from playback quality).
 */
fun refetchSongAudio(
    context: Context,
    database: MusicDatabase,
    downloadUtil: DownloadUtil,
    songId: String,
    songTitle: String,
    isDownloaded: Boolean,
    isCurrentlyPlaying: Boolean,
) {
    // The service owns songUrlCache (in-memory + its persisted mirror) and the player cache.
    context.startService(
        Intent(context, MusicService::class.java).apply {
            action = MusicService.ACTION_CLEAR_SONG_CACHE
            putExtra(MusicService.EXTRA_SONG_ID, songId)
        },
    )

    if (!isCurrentlyPlaying) {
        database.query { deleteFormat(songId) }
    }

    if (isDownloaded) {
        DownloadService.sendRemoveDownload(context, ExoDownloadService::class.java, songId, false)
        // The download factory memoises stream URLs in its OWN cache; without this the re-download would
        // resolve straight back to the stale stream.
        downloadUtil.invalidateSongUrl(songId)
        DownloadService.sendAddDownload(
            context,
            ExoDownloadService::class.java,
            DownloadRequest
                .Builder(songId, songId.toUri())
                .setCustomCacheKey(songId)
                .setData(songTitle.toByteArray()) // ExoDownloadService names the notification from this
                .build(),
            false,
        )
    }
}
