package iad1tya.echo.music.playback

import android.content.Context
import androidx.core.net.toUri
import androidx.media3.exoplayer.offline.DownloadRequest
import androidx.media3.exoplayer.offline.DownloadService
import iad1tya.echo.music.R
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Full song downloads deferred while live video mode is active. Previously only the companion
 * VIDEO request was deferred while AUDIO still raced the live mux — that bandwidth fight is a
 * prime hitch source. Both are held here and flushed from [MusicService.exitVideoMode].
 */
internal object PendingDeferredDownloads {
    data class Entry(val title: String, val isVideoSong: Boolean)

    private val pending = ConcurrentHashMap<String, Entry>()
    private val _pendingIds = MutableStateFlow<Set<String>>(emptySet())
    val pendingIds: StateFlow<Set<String>> = _pendingIds

    fun mark(songId: String, title: String, isVideoSong: Boolean) {
        if (songId.isBlank()) return
        pending[songId] = Entry(title = title.ifBlank { songId }, isVideoSong = isVideoSong)
        _pendingIds.value = pending.keys.toSet()
    }

    fun take(songId: String): Entry? {
        val entry = pending.remove(songId)
        if (entry != null) _pendingIds.value = pending.keys.toSet()
        return entry
    }

    fun contains(songId: String): Boolean = pending.containsKey(songId)
}

/**
 * Enqueue audio (+ companion video when [isVideoSong]).
 *
 * When [deferWhileLiveVideo] is true, enqueue NOTHING yet — store for
 * [flushPendingSongDownload] after video mode ends (avoids ExoDownload fighting the live A/V mux).
 */
fun enqueueSongDownloads(
    context: Context,
    songId: String,
    title: String,
    isVideoSong: Boolean,
    deferWhileLiveVideo: Boolean = false,
) {
    if (deferWhileLiveVideo) {
        PendingDeferredDownloads.mark(songId, title, isVideoSong)
        return
    }
    val audio = DownloadRequest.Builder(songId, songId.toUri())
        .setCustomCacheKey(songId)
        .setData(title.toByteArray())
        .build()
    DownloadService.sendAddDownload(context, ExoDownloadService::class.java, audio, false)
    if (isVideoSong) {
        enqueueVideoCompanionDownload(context, songId, title)
    }
}

/** Enqueue only the companion video offline download (`id::video`). */
fun enqueueVideoCompanionDownload(
    context: Context,
    songId: String,
    title: String,
) {
    val vidId = videoDownloadMediaId(songId)
    val video = DownloadRequest.Builder(vidId, vidId.toUri())
        .setCustomCacheKey(vidId)
        .setData(context.getString(R.string.downloading_video, title).toByteArray())
        .build()
    DownloadService.sendAddDownload(context, ExoDownloadService::class.java, video, false)
}

/**
 * Flush a download deferred while watching video (audio + optional companion).
 * Safe to call when leaving video mode; no-op when nothing is pending.
 */
fun flushPendingSongDownload(context: Context, songId: String?) {
    if (songId.isNullOrBlank()) return
    val entry = PendingDeferredDownloads.take(songId) ?: return
    enqueueSongDownloads(
        context = context,
        songId = songId,
        title = entry.title,
        isVideoSong = entry.isVideoSong,
        deferWhileLiveVideo = false,
    )
}

/**
 * Flush all pending deferred downloads (e.g. on media item transition or when video mux reaches steady state).
 */
fun flushAllPendingSongDownloads(context: Context) {
    val ids = PendingDeferredDownloads.pendingIds.value
    for (id in ids) {
        flushPendingSongDownload(context, id)
    }
}

/** @deprecated Use [flushPendingSongDownload]. Kept name as thin alias for call-site grep. */
fun flushPendingVideoCompanionDownload(context: Context, songId: String?) =
    flushPendingSongDownload(context, songId)

/** Remove audio and, for video songs, the companion video download. */
fun removeSongDownloads(
    context: Context,
    songId: String,
    isVideoSong: Boolean,
) {
    PendingDeferredDownloads.take(songId)
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
