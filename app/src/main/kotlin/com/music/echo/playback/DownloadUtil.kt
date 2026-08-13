

package iad1tya.echo.music.playback

import coil3.SingletonImageLoader
import coil3.request.CachePolicy
import coil3.request.ImageRequest

import android.content.Context
import android.net.ConnectivityManager
import androidx.core.content.getSystemService
import androidx.core.net.toUri
import androidx.media3.database.DatabaseProvider
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadNotificationHelper
import com.music.innertube.YouTube
import iad1tya.echo.music.constants.AudioQuality
import iad1tya.echo.music.constants.AudioQualityKey
import iad1tya.echo.music.constants.IpVersionKey
import com.music.innertube.models.IpVersion
import okhttp3.Dns
import java.net.InetAddress
import java.net.Inet4Address
import java.net.Inet6Address
import iad1tya.echo.music.db.MusicDatabase
import iad1tya.echo.music.db.entities.FormatEntity
import iad1tya.echo.music.db.entities.SongEntity
import iad1tya.echo.music.di.DownloadCache
import iad1tya.echo.music.di.PlayerCache
import iad1tya.echo.music.ui.utils.resize
import iad1tya.echo.music.utils.YTPlayerUtils
import iad1tya.echo.music.utils.enumPreference
import iad1tya.echo.music.utils.DeviceTier
import iad1tya.echo.music.utils.PerformanceMode
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.Runnable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import java.time.LocalDateTime
import java.util.concurrent.Executor
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The `codecs` value to persist for a FormatEntity, derived from the stream mimeType.
 *
 * NEVER returns "" when the container is knowable. An empty codec is read by the format-fallback
 * guard as OPUS (`codecs == "flac"` / `"mp4a.40.2"` both false), so on a LOSSLESS/SAAVN preference it
 * would look like a mismatch on EVERY open → purge the playing bytes + full re-resolve mid-song, i.e.
 * exactly the #57 micro-cut this whole line of work is chasing (and registry #40's 33s-stall/loop).
 * The three call sites used `mimeType.split("codecs=")[1]`, which also threw IndexOutOfBounds for a
 * mimeType with no codecs parameter. Prefer the explicit codecs= token; else map the container; else
 * keep the row's previous codec; only "" as a last resort when nothing is knowable.
 */
internal fun codecsFromMimeType(mimeType: String, existingCodecs: String? = null): String {
    mimeType.substringAfter("codecs=", "").takeIf { it.isNotBlank() }?.let { return it.removeSurrounding("\"") }
    return when {
        mimeType.contains("flac", ignoreCase = true) -> "flac"
        mimeType.contains("mp4", ignoreCase = true) || mimeType.contains("m4a", ignoreCase = true) -> "mp4a.40.2"
        mimeType.contains("webm", ignoreCase = true) || mimeType.contains("opus", ignoreCase = true) -> "opus"
        else -> existingCodecs ?: ""
    }
}

@Singleton
class DownloadUtil
@Inject
constructor(
    @ApplicationContext context: Context,
    val database: MusicDatabase,
    val databaseProvider: DatabaseProvider,
    @DownloadCache val downloadCache: SimpleCache,
    @PlayerCache val playerCache: SimpleCache,
) {
    private val connectivityManager = context.getSystemService<ConnectivityManager>()!!
    private val downloadQuality by enumPreference(context, iad1tya.echo.music.constants.DownloadQualityKey, iad1tya.echo.music.constants.DownloadQuality.YOUTUBE)
    private val ipVersion by enumPreference(context, IpVersionKey, IpVersion.AUTO)
    private val songUrlCache = java.util.concurrent.ConcurrentHashMap<String, Pair<String, Long>>()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * Refetch: drop the memoised stream URL for [songId] so a re-enqueued download RE-RESOLVES instead of
     * pulling the same stale stream again. This cache is private to the download factory — clearing
     * MusicService's songUrlCache does not reach it, so a refetch that skipped this would silently
     * re-download the very bytes the user asked to replace. In-memory only: nothing to persist.
     */
    fun invalidateSongUrl(songId: String) {
        songUrlCache.remove(songId)
        songUrlCache.remove(videoDownloadMediaId(songId))
    }

    val downloads = MutableStateFlow<Map<String, Download>>(emptyMap())

    /**
     * Live `mediaId -> percentDownloaded` (0–100, or [C.PERCENTAGE_UNSET] / negative while unknown).
     * [downloads] only refreshes on *state* changes; this map is polled from
     * [DownloadManager.getCurrentDownloads] while anything is queued/downloading so UI arcs can
     * fill like Apple Music instead of spinning forever.
     */
    val liveProgress = MutableStateFlow<Map<String, Float>>(emptyMap())

    private var progressPollJob: Job? = null

    private fun hasActiveTransfers(): Boolean {
        if (downloads.value.values.any {
                it.state == Download.STATE_DOWNLOADING || it.state == Download.STATE_QUEUED
            }
        ) {
            return true
        }
        return runCatching { downloadManager.currentDownloads.isNotEmpty() }.getOrDefault(false)
    }

    /** Start/stop the 500 ms poller based on whether anything is actually transferring. */
    private fun syncProgressPolling() {
        if (!hasActiveTransfers()) {
            progressPollJob?.cancel()
            progressPollJob = null
            liveProgress.value = emptyMap()
            return
        }
        if (progressPollJob?.isActive == true) return
        progressPollJob = scope.launch {
            while (isActive) {
                liveProgress.value = runCatching {
                    downloadManager.currentDownloads.associate { d ->
                        d.request.id to d.percentDownloaded
                    }
                }.getOrDefault(emptyMap())
                if (!hasActiveTransfers()) {
                    liveProgress.value = emptyMap()
                    break
                }
                delay(500)
            }
        }
    }

    // Factory order matters: resolver OUTSIDE, chunker INSIDE. ResolvingDataSource resolves the
    // stream URL (cipher/PoToken + FormatEntity/SongEntity upserts) ONCE per download, then the
    // ChunkingDataSource re-opens the RESOLVED googlevideo URL every 5MB via Range headers (the
    // throttling bypass). Swapping the order would re-run the whole resolution per chunk.
    private val dataSourceFactory =
        ResolvingDataSource.Factory(
            ChunkingDataSourceFactory(
                OkHttpDataSource.Factory(
                        OkHttpClient.Builder()
                            .dns(object : Dns {
                                override fun lookup(hostname: String): List<InetAddress> {
                                    val addresses = Dns.SYSTEM.lookup(hostname)
                                    return when (this@DownloadUtil.ipVersion) {
                                        IpVersion.IPV4 -> addresses.filter { it is Inet4Address }.ifEmpty { addresses }
                                        IpVersion.IPV6 -> addresses.filter { it is Inet6Address }.ifEmpty { addresses }
                                        IpVersion.AUTO -> addresses
                                    }
                                }
                            })
                            .proxy(YouTube.proxy)
                            .proxyAuthenticator { _, response ->
                                YouTube.proxyAuth?.let { auth ->
                                    response.request.newBuilder()
                                        .header("Proxy-Authorization", auth)
                                        .build()
                                } ?: response.request
                            }
                            .build(),
                    ),
            ),
        ) { dataSpec ->
            val mediaId = dataSpec.key ?: error("No media id")

            if (isVideoDownloadId(mediaId)) {
                val songId = baseSongIdFromVideoDownload(mediaId)
                songUrlCache[mediaId]?.takeIf { it.second > System.currentTimeMillis() }?.let {
                    return@Factory dataSpec.withUri(it.first.toUri())
                }
                val videoUrl = runBlocking(Dispatchers.IO) {
                    var url = YTPlayerUtils.videoStreamUrlDiag(songId, connectivityManager, null).getOrNull()
                    if (url.isNullOrEmpty()) {
                        url = runCatching {
                            YTPlayerUtils.videoStreamUrl(songId, connectivityManager, null)
                        }.getOrNull()
                    }
                    url
                } ?: error("No video stream for $songId")
                songUrlCache[mediaId] = videoUrl to (5 * 60 * 1000L)
                return@Factory dataSpec.withUri(videoUrl.toUri())
            }

            songUrlCache[mediaId]?.takeIf { it.second > System.currentTimeMillis() }?.let {
                return@Factory dataSpec.withUri(it.first.toUri())
            }

            val playbackData = runBlocking(Dispatchers.IO) {
                YTPlayerUtils.playerResponseForPlayback(
                    mediaId,
                    audioQuality = when (downloadQuality) {
                        iad1tya.echo.music.constants.DownloadQuality.LOSSLESS -> AudioQuality.LOSSLESS
                        iad1tya.echo.music.constants.DownloadQuality.SAAVN -> AudioQuality.SAAVN
                        else -> AudioQuality.OPUS
                    },
                    connectivityManager = connectivityManager,
                    context = context,
                    isDownload = true
                )
            }.getOrThrow()
            val format = playbackData.format

            // PRESERVE any loudness already stored for this track. The download is a SEPARATE fetch (logged-in,
            // different quality) that can return a DIFFERENT loudness or null; the playback fetch already stored
            // the right value. Overwriting it changed the CURRENTLY-PLAYING track's normalization (currentFormat
            // is a Room Flow) → the volume audibly ROSE when the user liked a song (auto-download) and fell back
            // on unlike. Mirror the playback factory's loudness-preservation so a download never re-levels the
            // playing track. MusicService also FREEZES the live gain for the rest of this play, so even a first
            // fill (existing was null) is cached for the NEXT play only.
            val existingFmt = runBlocking(Dispatchers.IO) { database.format(mediaId).first() }

            database.query {
                upsert(
                    FormatEntity(
                        id = mediaId,
                        itag = format.itag,
                        mimeType = format.mimeType.split(";")[0],
                        codecs = codecsFromMimeType(format.mimeType, existingFmt?.codecs),
                        bitrate = format.bitrate,
                        sampleRate = format.audioSampleRate,
                        contentLength = format.contentLength ?: 0L,
                        // Prefer the loudness already stored from stream playback. A download fetch can
                        // return a different (or null) value; overwriting re-levels the playing track and
                        // made downloads sound quieter than the same song while streaming.
                        loudnessDb = existingFmt?.loudnessDb ?: playbackData.audioConfig?.loudnessDb,
                        perceptualLoudnessDb = existingFmt?.perceptualLoudnessDb
                            ?: playbackData.audioConfig?.perceptualLoudnessDb,
                        // Preserve a cached per-play measurement so a like/auto-download doesn't force a re-measure.
                        measuredLoudnessDb = existingFmt?.measuredLoudnessDb,
                        playbackUrl = playbackData.playbackTracking?.videostatsPlaybackUrl?.baseUrl
                    ),
                )

                val now = LocalDateTime.now()
                val existing = getSongByIdBlocking(mediaId)?.song

                val updatedSong = if (existing != null) {
                    if (existing.dateDownload == null) {
                        existing.copy(dateDownload = now)
                    } else {
                        existing
                    }
                } else {
                    SongEntity(
                        id = mediaId,
                        title = playbackData.videoDetails?.title ?: "Unknown",
                        duration = playbackData.videoDetails?.lengthSeconds?.toIntOrNull() ?: 0,
                        thumbnailUrl = playbackData.videoDetails?.thumbnail?.thumbnails?.lastOrNull()?.url?.resize(1200, 1200),
                        dateDownload = now,
                        isDownloaded = false
                    )
                }

                upsert(updatedSong)

                
                updatedSong.thumbnailUrl?.let { url ->
                    val request = ImageRequest.Builder(context)
                        .data(url)
                        .memoryCachePolicy(CachePolicy.ENABLED)
                        .diskCachePolicy(CachePolicy.ENABLED)
                        .build()
                    SingletonImageLoader.get(context).enqueue(request)
                }
            }

            // Plain URL for ALL qualities. The old &range=0-N URL-param throttling bypass is REPLACED
            // by the ChunkingDataSource above — the two tricks are mutually exclusive: googlevideo
            // gives the range= URL param precedence over Range headers, so stacking them would serve
            // every 5MB chunk from byte 0 (per-chunk prefix re-downloads) and break resume.
            val streamUrl = playbackData.streamUrl

            songUrlCache[mediaId] = streamUrl to playbackData.streamExpiresInSeconds * 1000L
            dataSpec.withUri(streamUrl.toUri())
        }

    val downloadNotificationHelper =
        DownloadNotificationHelper(context, ExoDownloadService.CHANNEL_ID)

    @OptIn(DelicateCoroutinesApi::class)
    val downloadManager: DownloadManager =
        DownloadManager(
            context,
            databaseProvider,
            downloadCache,
            dataSourceFactory,
            Executor(Runnable::run)
        ).apply {
            // Concurrent downloads hammer a weak device with parallel network + transcode/mux work (CPU/RAM/heat/
            // data-contention). Throttle ONLY on the weak paths; capable devices are BYTE-IDENTICAL to before (3).
            // effectiveTier() is synchronous/cached and returns ULTRA when Performance Mode is ON (perf-mode), else
            // the real DeviceCapabilities tier — so this is strictly gated to perf-mode / LOW-tier. Downloads are a
            // separate pipeline from playback: this never touches the 9s crossfade, playback buffers, or effects.
            maxParallelDownloads = when (PerformanceMode.effectiveTier(context)) {
                DeviceTier.ULTRA -> 1   // Performance Mode ON: one transfer at a time, minimal contention.
                DeviceTier.LOW -> 2     // Genuinely low-end hardware: halve the concurrency.
                else -> 3               // MID / HIGH (capable): unchanged.
            }
            addListener(
                object : DownloadManager.Listener {
                    override fun onDownloadChanged(
                        downloadManager: DownloadManager,
                        download: Download,
                        finalException: Exception?,
                    ) {
                        downloads.update { map ->
                            map.toMutableMap().apply {
                                set(download.request.id, download)
                            }
                        }
                        syncProgressPolling()

                        // media3 hands us the cause of the failure and it used to be dropped on the
                        // floor: the DB simply recorded "not downloaded". The user then reports a song
                        // that never finishes downloading and later "doesn't play offline", and there
                        // was no record anywhere of whether it was a 403, a full disk, or a dead
                        // network. `failureReason` distinguishes a genuine failure from a user pause.
                        if (download.state == Download.STATE_FAILED) {
                            timber.log.Timber.tag("DOWNLOAD").e(
                                "failed id=${download.request.id} reason=${download.failureReason} " +
                                    "bytes=${download.bytesDownloaded} " +
                                    "${finalException?.javaClass?.simpleName}: ${finalException?.message?.take(180)}"
                            )
                        }

                        scope.launch {
                            when (download.state) {
                                Download.STATE_COMPLETED -> {
                                    database.updateDownloadedInfo(download.request.id, true, LocalDateTime.now())
                                }
                                Download.STATE_FAILED,
                                Download.STATE_STOPPED,
                                Download.STATE_REMOVING -> {
                                    database.updateDownloadedInfo(download.request.id, false, null)
                                }
                                else -> {
                                }
                            }
                        }
                    }

                    override fun onDownloadRemoved(
                        downloadManager: DownloadManager,
                        download: Download,
                    ) {
                        downloads.update { map ->
                            map.toMutableMap().apply { remove(download.request.id) }
                        }
                        syncProgressPolling()
                    }
                }
            )
        }

    init {
        // Synchronous index scan. DownloadUtil is now constructed OFF the main thread (App.onCreate warms it
        // on Dispatchers.IO via dagger.Lazy), so this disk/DB scan runs off-main anyway — no cold-start freeze.
        // Keeping it synchronous means `downloads` is fully populated the instant construction returns, so
        // Android Auto / media-browser clients never see 0 downloaded songs during the first moments (an async
        // fill would also have to reconcile removals that arrive mid-scan, which the merge did not handle).
        val result = mutableMapOf<String, Download>()
        downloadManager.downloadIndex.getDownloads().use { cursor ->
            while (cursor.moveToNext()) {
                result[cursor.download.request.id] = cursor.download
            }
        }
        downloads.value = result
        syncProgressPolling()
    }

    fun getDownload(songId: String): Flow<Download?> = downloads.map { it[songId] }

    fun release() {
        scope.cancel()
    }
}
