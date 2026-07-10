package iad1tya.echo.music.utils

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.media.RingtoneManager
import android.net.ConnectivityManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.SystemClock
import android.provider.MediaStore
import android.provider.Settings
import androidx.core.content.getSystemService
import androidx.media3.common.C
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.ContentMetadata
import com.music.innertube.YouTube
import com.music.innertube.models.YouTubeClient
import iad1tya.echo.music.R
import iad1tya.echo.music.constants.AudioQuality
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.util.concurrent.TimeUnit

object RingtoneHelper {

    /** Container headers the trimmer needs live at byte 0; slack covers SeekHead/Cues/moov before audio. */
    private const val HEADER_SLACK_BYTES = 256L * 1024L

    /** Mid-download IOException (googlevideo throttle/reset) is retried this many times, resuming. */
    private const val MAX_DOWNLOAD_RETRIES = 3

    /** DownloadUtil's fallback range end when the format reports no contentLength. */
    private const val RANGE_FALLBACK_END = 10_000_000L

    // Same proxy behaviour as the app's other stream clients; finite timeouts so a stalled CDN fails
    // instead of hanging the IO thread forever (the old raw HttpURLConnection had 10s read timeout,
    // no retry, and no UA — googlevideo throttled then reset it).
    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .proxy(YouTube.proxy)
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build()
    }

    /** Everything the ringtone downloader needs from the resolved stream (the URL alone drops contentLength). */
    private data class StreamInfo(
        val streamUrl: String,
        val contentLength: Long?,
        val bitrate: Int,
        val isSaavnStream: Boolean,
    )

    suspend fun getStreamUrl(context: Context, songId: String): String? =
        getStreamInfo(context, songId)?.streamUrl

    private suspend fun getStreamInfo(context: Context, songId: String): StreamInfo? = withContext(Dispatchers.IO) {
        try {
            val connectivityManager = context.getSystemService<ConnectivityManager>()!!

            val result = YTPlayerUtils.playerResponseForPlayback(
                videoId = songId,
                audioQuality = AudioQuality.OPUS,
                connectivityManager = connectivityManager,
                // Ringtone-only: smallest transferable audio stream (we keep a few seconds anyway).
                preferSmallestAudio = true,
            )
            result.getOrNull()?.let {
                StreamInfo(
                    streamUrl = it.streamUrl,
                    contentLength = it.format.contentLength,
                    bitrate = it.format.bitrate,
                    isSaavnStream = it.isSaavnStream,
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    suspend fun downloadAndTrimAsRingtone(
        context: Context,
        songId: String,
        title: String,
        artist: String,
        startMs: Long,
        endMs: Long,
        // Cache-first sources (injected by RingtoneViewModel): a song already fully downloaded/cached
        // is served from local bytes instead of being re-downloaded from googlevideo.
        downloadCache: Cache? = null,
        playerCache: Cache? = null,
        onProgress: (Float, String) -> Unit,
        // (success, message, ringtoneUri, appliedDirectly) — appliedDirectly is true only when the
        // ringtone was really set via RingtoneManager (requires WRITE_SETTINGS); when false the UI
        // should offer both the picker fallback AND the WRITE_SETTINGS grant action.
        onComplete: (Boolean, String, Uri?, Boolean) -> Unit
    ) = withContext(Dispatchers.IO) {
        try {
            onProgress(0.05f, "Getting audio stream...")

            val tempFile = File(context.cacheDir, "temp_ringtone_source_$songId")
            if (tempFile.exists()) tempFile.delete()

            // 1) CACHE FIRST: a fully downloaded/cached song is copied from the local media3 caches
            // (instant, works offline, zero googlevideo traffic). Any miss/problem falls through to
            // the ranged network download below.
            val servedFromCache = copyFromCacheIfComplete(songId, tempFile, downloadCache, playerCache, onProgress)

            if (!servedFromCache) {
                val stream = getStreamInfo(context, songId)
                if (stream == null) {
                    withContext(Dispatchers.Main) {
                        onComplete(false, "Failed to get audio stream", null, false)
                    }
                    return@withContext
                }

                onProgress(0.1f, "Fetching audio...")

                // 2) Ranged, resumable OkHttp download with the proper per-client User-Agent —
                // 4) capped at the trim-window prefix when the format metadata allows it.
                downloadStreamToFile(stream, tempFile, endMs, onProgress)
            }

            if (!tempFile.exists() || tempFile.length() == 0L) {
                withContext(Dispatchers.Main) {
                    onComplete(false, "Failed to prepare source file", null, false)
                }
                return@withContext
            }

            onProgress(0.6f, "Processing audio...")

            // P23: actually trim to [startMs, endMs] (previously a whole-file copy).
            // P42: the returned extension/MIME describe the REAL container the trim produced,
            // instead of the hardcoded .m4a / audio/mp4 that mislabelled Opus/WebM streams.
            val trim = trimAudio(tempFile, context.cacheDir, startMs, endMs)
            val trimmedFile = trim?.file

            if (trim == null || trimmedFile == null || !trimmedFile.exists() || trimmedFile.length() == 0L) {
                trimmedFile?.delete()
                withContext(Dispatchers.Main) {
                    onComplete(false, "Failed to process audio or output is empty", null, false)
                }
                return@withContext
            }

            val outputMime = trim.mimeType

            onProgress(0.85f, "Saving ringtone...")

            val fileName = "${title.replace(Regex("[^a-zA-Z0-9\\s]"), "")}_trimmed_$songId.${trim.extension}"

            val ringtoneUri: Uri = try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val contentValues = ContentValues().apply {
                        put(MediaStore.Audio.Media.DISPLAY_NAME, fileName)
                        put(MediaStore.Audio.Media.MIME_TYPE, outputMime)
                        put(MediaStore.Audio.Media.RELATIVE_PATH, Environment.DIRECTORY_RINGTONES)
                        put(MediaStore.Audio.Media.IS_RINGTONE, true)
                        put(MediaStore.Audio.Media.IS_NOTIFICATION, true)
                        put(MediaStore.Audio.Media.IS_ALARM, true)
                        put(MediaStore.Audio.Media.TITLE, "$title (Ringtone)")
                        put(MediaStore.Audio.Media.ARTIST, artist)
                        put(MediaStore.Audio.Media.IS_PENDING, 1)
                    }

                    val uri = context.contentResolver.insert(
                        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                        contentValues
                    ) ?: throw Exception("Failed to create MediaStore entry")

                    context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                        trimmedFile.inputStream().use { inputStream ->
                            inputStream.copyTo(outputStream)
                        }
                    }

                    contentValues.clear()
                    contentValues.put(MediaStore.Audio.Media.IS_PENDING, 0)
                    context.contentResolver.update(uri, contentValues, null, null)
                    uri
                } else {
                    val ringtonesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_RINGTONES)
                    if (!ringtonesDir.exists()) ringtonesDir.mkdirs()

                    val file = File(ringtonesDir, fileName)
                    trimmedFile.copyTo(file, overwrite = true)

                    val contentValues = ContentValues().apply {
                        put(MediaStore.Audio.Media.DATA, file.absolutePath)
                        put(MediaStore.Audio.Media.TITLE, "$title (Ringtone)")
                        put(MediaStore.Audio.Media.ARTIST, artist)
                        put(MediaStore.Audio.Media.MIME_TYPE, outputMime)
                        put(MediaStore.Audio.Media.IS_RINGTONE, true)
                        put(MediaStore.Audio.Media.IS_NOTIFICATION, true)
                        put(MediaStore.Audio.Media.IS_ALARM, true)
                    }

                    context.contentResolver.insert(
                        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                        contentValues
                    ) ?: Uri.fromFile(file)
                }
            } finally {
                tempFile.delete()
                trimmedFile.delete()
            }

            onProgress(0.95f, "Song saved to Ringtones...")

            // Actually APPLY the ringtone: inserting into MediaStore (and even the system picker intent)
            // never calls RingtoneManager.setActualDefaultRingtoneUri, so on many ROMs nothing got set.
            // When the app holds WRITE_SETTINGS we set it directly; otherwise the existing picker path
            // (openRingtoneSettings, offered by the success dialog) stays as the fallback.
            val appliedDirectly = if (hasSettingsPermission(context)) {
                try {
                    RingtoneManager.setActualDefaultRingtoneUri(
                        context,
                        RingtoneManager.TYPE_RINGTONE,
                        ringtoneUri,
                    )
                    true
                } catch (e: Exception) {
                    e.printStackTrace()
                    false
                }
            } else {
                false
            }

            withContext(Dispatchers.Main) {
                onProgress(1f, "Done!")
                if (appliedDirectly) {
                    onComplete(true, context.getString(R.string.ringtone_set_directly, title), ringtoneUri, true)
                } else {
                    onComplete(true, context.getString(R.string.ringtone_saved_select_in_settings, title), ringtoneUri, false)
                }
            }

        } catch (e: Exception) {
            e.printStackTrace()
            withContext(Dispatchers.Main) {
                onComplete(false, "Error: ${e.message}", null, false)
            }
        }
    }

    /**
     * CACHE FIRST: serves the trim source from the local media3 caches when the FULL song is already
     * there — downloadCache (complete downloads only, same check as MusicService's fully-downloaded
     * short-circuit) first, then playerCache. Reads through a manually opened, cache-only
     * CacheDataSource (null upstream => can never touch the network) keyed exactly like playback keys
     * the caches (DataSpec.key = mediaId). Returns false on ANY miss/problem so the caller falls back
     * to the ranged network download.
     */
    private suspend fun copyFromCacheIfComplete(
        mediaId: String,
        tempFile: File,
        downloadCache: Cache?,
        playerCache: Cache?,
        onProgress: (Float, String) -> Unit,
    ): Boolean = withContext(Dispatchers.IO) {
        val hit = fullyCachedLength(downloadCache, mediaId)?.let { downloadCache!! to it }
            ?: fullyCachedLength(playerCache, mediaId)?.let { playerCache!! to it }
            ?: return@withContext false
        val (cache, length) = hit

        val dataSource = CacheDataSource(cache, /* upstream = */ null, /* flags = */ 0)
        try {
            dataSource.open(
                DataSpec.Builder()
                    .setUri(Uri.parse("aura-cache://$mediaId")) // placeholder; cache reads resolve by key
                    .setKey(mediaId)
                    .setPosition(0)
                    .setLength(length)
                    .build()
            )
            val buffer = ByteArray(64 * 1024)
            var headerChecked = false
            var copied = 0L
            var lastProgressAt = 0L
            tempFile.outputStream().use { output ->
                while (true) {
                    ensureActive()
                    val read = dataSource.read(buffer, 0, buffer.size)
                    if (read == C.RESULT_END_OF_INPUT) break
                    if (read <= 0) continue
                    if (!headerChecked) {
                        headerChecked = true
                        // The cached bytes keep whatever container playback stored. Containers the
                        // MediaExtractor/MediaMuxer trimmer can't handle (e.g. FLAC lossless
                        // downloads) fall back to the network path, which fetches a SMALL Opus
                        // prefix instead of full-copying a huge lossless file.
                        if (!isTrimmableContainer(buffer, read)) return@withContext false
                    }
                    output.write(buffer, 0, read)
                    copied += read
                    val now = SystemClock.elapsedRealtime()
                    if (now - lastProgressAt >= 250L) {
                        lastProgressAt = now
                        val progress = 0.1f + (copied.toFloat() / length) * 0.4f
                        onProgress(progress.coerceIn(0.1f, 0.5f), "Preparing cached audio...")
                    }
                }
            }
            copied == length
        } catch (e: Exception) {
            e.printStackTrace()
            tempFile.delete()
            false
        } finally {
            try { dataSource.close() } catch (_: Exception) {}
        }
    }

    /**
     * Full-cache check, MusicService pattern: the cache's own ContentMetadata length must be known and
     * the whole [0, length) span cached. Returns the length on a hit, null otherwise.
     */
    private fun fullyCachedLength(cache: Cache?, mediaId: String): Long? {
        if (cache == null) return null
        return try {
            val length = ContentMetadata.getContentLength(cache.getContentMetadata(mediaId))
            if (length != C.LENGTH_UNSET.toLong() && length > 0 && cache.isCached(mediaId, 0, length)) length else null
        } catch (e: Exception) {
            null
        }
    }

    /** True when [header] starts with a container the trimmer handles: WebM/Matroska, Ogg, or MP4. */
    private fun isTrimmableContainer(header: ByteArray, length: Int): Boolean {
        if (length < 12) return false
        return when {
            header[0] == 0x1A.toByte() && header[1] == 0x45.toByte() &&
                header[2] == 0xDF.toByte() && header[3] == 0xA3.toByte() -> true // WebM/Matroska (EBML)
            header[0] == 'O'.code.toByte() && header[1] == 'g'.code.toByte() &&
                header[2] == 'g'.code.toByte() && header[3] == 'S'.code.toByte() -> true // Ogg
            header[4] == 'f'.code.toByte() && header[5] == 't'.code.toByte() &&
                header[6] == 'y'.code.toByte() && header[7] == 'p'.code.toByte() -> true // MP4/M4A
            else -> false // FLAC & friends: fall back to the small network Opus fetch
        }
    }

    /**
     * THE ACTUAL BUG FIX: googlevideo throttles-then-RESETS un-ranged full GETs — exactly what the old
     * raw HttpURLConnection did ("connection reset"). Download like the player/DownloadUtil instead:
     * OkHttp + explicit `&range=<start>-<end>` URL parameter (googlevideo only; Saavn/other CDNs get a
     * plain GET, as in DownloadUtil) + the per-client User-Agent the URL was minted for. On a mid-read
     * IOException the download RESUMES from the last written byte (up to [MAX_DOWNLOAD_RETRIES] times,
     * appending) instead of failing outright.
     *
     * PREFIX-ONLY FETCH: the trimmer needs the container headers at byte 0 plus samples through
     * [endMs] only, so when contentLength/bitrate are known the ranged fetch is capped at
     * min(contentLength, [HEADER_SLACK_BYTES] + endMs worth of audio + 25% VBR margin).
     */
    private suspend fun downloadStreamToFile(
        stream: StreamInfo,
        tempFile: File,
        endMs: Long,
        onProgress: (Float, String) -> Unit,
    ) = withContext(Dispatchers.IO) {
        val httpUrl = stream.streamUrl.toHttpUrlOrNull()
        // Range the URL exactly like DownloadUtil: googlevideo only; Saavn/other CDNs get a plain GET.
        val useRangeParam = !stream.isSaavnStream && httpUrl?.host?.endsWith("googlevideo.com") == true
        val knownLength = stream.contentLength?.takeIf { it > 0 }

        val targetLength = if (useRangeParam && knownLength != null && endMs > 0 && stream.bitrate > 0) {
            minOf(knownLength, HEADER_SLACK_BYTES + ((endMs / 1000.0) * (stream.bitrate / 8.0) * 1.25).toLong())
        } else {
            knownLength
        }

        val userAgent = httpUrl?.let { userAgentForStreamUrl(it) }

        var bytesWritten = 0L
        var lastError: IOException? = null

        for (attempt in 0..MAX_DOWNLOAD_RETRIES) {
            if (attempt > 0) delay(500L * attempt)
            try {
                val requestBuilder = Request.Builder()
                if (useRangeParam) {
                    // Resume mid-file by asking googlevideo for the remaining byte range.
                    val endByte = targetLength?.minus(1) ?: RANGE_FALLBACK_END
                    requestBuilder.url("${stream.streamUrl}&range=$bytesWritten-$endByte")
                } else {
                    requestBuilder.url(stream.streamUrl)
                    if (bytesWritten > 0) requestBuilder.header("Range", "bytes=$bytesWritten-")
                }
                userAgent?.let { requestBuilder.header("User-Agent", it) }

                httpClient.newCall(requestBuilder.build()).execute().use { response ->
                    if (!response.isSuccessful) throw IOException("Stream request failed with HTTP ${response.code}")
                    // Plain-GET resume the server ignored (200 instead of 206): restart from scratch.
                    if (!useRangeParam && bytesWritten > 0 && response.code != 206) bytesWritten = 0

                    val body = response.body ?: throw IOException("Empty stream response")
                    val total = targetLength
                        ?: body.contentLength().takeIf { it > 0 }?.plus(bytesWritten)

                    FileOutputStream(tempFile, /* append = */ bytesWritten > 0).use { output ->
                        body.byteStream().use { input ->
                            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                            var lastProgressAt = 0L
                            var lastPercent = -1
                            while (true) {
                                ensureActive()
                                val read = input.read(buffer)
                                if (read == -1) break
                                output.write(buffer, 0, read)
                                bytesWritten += read

                                if (total != null && total > 0) {
                                    // Throttled to ~250ms / 1% steps, and delivered WITHOUT hopping to
                                    // Main per 8KB chunk (the callback lands in a thread-safe
                                    // StateFlow update).
                                    val percent = ((bytesWritten * 100) / total).toInt().coerceIn(0, 100)
                                    val now = SystemClock.elapsedRealtime()
                                    if (percent > lastPercent && now - lastProgressAt >= 250L) {
                                        lastPercent = percent
                                        lastProgressAt = now
                                        val progress = 0.1f + (bytesWritten.toFloat() / total) * 0.4f
                                        onProgress(
                                            progress.coerceIn(0.1f, 0.5f),
                                            "Downloading... ${(progress * 100).toInt()}%"
                                        )
                                    }
                                }
                            }
                            output.flush()
                        }
                    }
                }

                // A ranged fetch that ended before the requested span is a silent truncation — retry it.
                if (useRangeParam && targetLength != null && bytesWritten < targetLength) {
                    throw IOException("Stream ended early: $bytesWritten of $targetLength bytes")
                }
                return@withContext
            } catch (e: IOException) {
                e.printStackTrace()
                lastError = e
            }
        }
        throw lastError ?: IOException("Download failed")
    }

    /**
     * Same per-client User-Agent selection as MusicService's video OkHttp interceptor: googlevideo
     * URLs are minted for the client named in their `c=` param and throttle/403 under a mismatched
     * (or missing) User-Agent. Null for non-YouTube hosts (e.g. Saavn) — no UA is forced there.
     */
    private fun userAgentForStreamUrl(url: HttpUrl): String? {
        val host = url.host
        val isYt = host.endsWith("googlevideo.com") || host.endsWith("youtube.com") ||
            host.endsWith("googleusercontent.com") || host.endsWith("youtube-nocookie.com") ||
            host.endsWith("ytimg.com")
        if (!isYt) return null
        val c = url.queryParameter("c")?.trim().orEmpty()
        return when {
            c.startsWith("WEB", true) -> YouTubeClient.USER_AGENT_WEB
            c.startsWith("IOS", true) -> YouTubeClient.IOS.userAgent
            c.startsWith("ANDROID_VR", true) -> YouTubeClient.ANDROID_VR_NO_AUTH.userAgent
            c.startsWith("ANDROID", true) -> YouTubeClient.MOBILE.userAgent
            else -> YouTubeClient.USER_AGENT_WEB
        }
    }

    /** Result of a trim: the produced file plus the container extension/MIME that truly match its bytes. */
    private data class TrimResult(val file: File, val extension: String, val mimeType: String)

    /**
     * P23/P42: losslessly extract the [startMs, endMs] window from [inputFile] and remux it into a
     * container that matches the real codec (no re-encode). MediaExtractor sniffs the true container
     * from the bytes, so the extension/MIME we return are always honest.
     *
     * - AAC   -> MPEG-4 (.m4a, audio/mp4)
     * - Opus  -> Ogg   (.ogg, audio/ogg)  [needs API 29+ MediaMuxer OGG support]
     * - Vorbis-> WebM  (.webm, audio/webm)
     *
     * If the codec can't be remuxed on this API level (e.g. Opus on API < 29) we fall back to an
     * honestly-labelled full copy so the file still plays and the stored MIME matches the bytes.
     */
    private suspend fun trimAudio(
        inputFile: File,
        cacheDir: File,
        startMs: Long,
        endMs: Long
    ): TrimResult? = withContext(Dispatchers.IO) {
        val songTag = inputFile.name.removePrefix("temp_ringtone_source_")
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(inputFile.absolutePath)

            var audioTrackIndex = -1
            var format: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val f = extractor.getTrackFormat(i)
                val mime = f.getString(MediaFormat.KEY_MIME) ?: continue
                if (mime.startsWith("audio/")) {
                    audioTrackIndex = i
                    format = f
                    break
                }
            }
            val srcMime = format?.getString(MediaFormat.KEY_MIME)
            if (audioTrackIndex < 0 || format == null || srcMime == null) {
                return@withContext honestFullCopy(inputFile, cacheDir, songTag)
            }

            // Pick the output container from the actual codec (labels then match the real bytes — P42).
            val outMuxFormat: Int
            val outExt: String
            val outMime: String
            when {
                srcMime == "audio/mp4a-latm" -> {
                    outMuxFormat = MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4
                    outExt = "m4a"; outMime = "audio/mp4"
                }
                srcMime == "audio/opus" && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> {
                    outMuxFormat = MediaMuxer.OutputFormat.MUXER_OUTPUT_OGG
                    outExt = "ogg"; outMime = "audio/ogg"
                }
                srcMime == "audio/vorbis" -> {
                    outMuxFormat = MediaMuxer.OutputFormat.MUXER_OUTPUT_WEBM
                    outExt = "webm"; outMime = "audio/webm"
                }
                else -> {
                    // Codec we can't remux/trim on this API level -> honest full copy fallback.
                    return@withContext honestFullCopy(inputFile, cacheDir, songTag)
                }
            }

            val outputFile = File(cacheDir, "trimmed_ringtone_$songTag.$outExt")
            if (outputFile.exists()) outputFile.delete()

            extractor.selectTrack(audioTrackIndex)
            extractor.seekTo(startMs * 1000L, MediaExtractor.SEEK_TO_CLOSEST_SYNC)

            val maxInput = if (format.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) {
                format.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE).coerceAtLeast(64 * 1024)
            } else {
                256 * 1024
            }
            val buffer = ByteBuffer.allocate(maxInput)
            val bufferInfo = MediaCodec.BufferInfo()
            val endUs = endMs * 1000L

            val muxer = MediaMuxer(outputFile.absolutePath, outMuxFormat)
            var started = false
            var wroteSample = false
            try {
                val outTrack = muxer.addTrack(format)
                muxer.start()
                started = true
                var firstSampleUs = -1L
                while (true) {
                    ensureActive()
                    buffer.clear()
                    val sampleSize = extractor.readSampleData(buffer, 0)
                    if (sampleSize < 0) break
                    val sampleTimeUs = extractor.sampleTime
                    if (sampleTimeUs > endUs) break
                    if (firstSampleUs < 0) firstSampleUs = sampleTimeUs
                    bufferInfo.offset = 0
                    bufferInfo.size = sampleSize
                    // Rebase to 0 so the clip starts immediately (no leading silence/gap).
                    bufferInfo.presentationTimeUs = (sampleTimeUs - firstSampleUs).coerceAtLeast(0L)
                    bufferInfo.flags =
                        if (extractor.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC != 0) {
                            MediaCodec.BUFFER_FLAG_KEY_FRAME
                        } else {
                            0
                        }
                    muxer.writeSampleData(outTrack, buffer, bufferInfo)
                    wroteSample = true
                    extractor.advance()
                }
            } finally {
                if (started) {
                    try { muxer.stop() } catch (_: Exception) {}
                }
                try { muxer.release() } catch (_: Exception) {}
            }

            if (!wroteSample || !outputFile.exists() || outputFile.length() == 0L) {
                outputFile.delete()
                return@withContext null
            }
            TrimResult(outputFile, outExt, outMime)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        } finally {
            try { extractor.release() } catch (_: Exception) {}
        }
    }

    /**
     * Fallback for codecs we can't losslessly trim on this API level. Copies the whole source but
     * labels it with the container sniffed from the actual bytes, so the MediaStore MIME is truthful
     * (fixes P42 even when the trim path is unavailable) and the ringtone still plays.
     */
    private fun honestFullCopy(inputFile: File, cacheDir: File, songTag: String): TrimResult? {
        return try {
            val header = ByteArray(12)
            val read = inputFile.inputStream().use { it.read(header) }
            val ext: String
            val mime: String
            when {
                read >= 4 && header[0] == 0x1A.toByte() && header[1] == 0x45.toByte() &&
                    header[2] == 0xDF.toByte() && header[3] == 0xA3.toByte() -> {
                    ext = "webm"; mime = "audio/webm" // Matroska/WebM (EBML)
                }
                read >= 4 && header[0] == 'O'.code.toByte() && header[1] == 'g'.code.toByte() &&
                    header[2] == 'g'.code.toByte() && header[3] == 'S'.code.toByte() -> {
                    ext = "ogg"; mime = "audio/ogg"
                }
                read >= 8 && header[4] == 'f'.code.toByte() && header[5] == 't'.code.toByte() &&
                    header[6] == 'y'.code.toByte() && header[7] == 'p'.code.toByte() -> {
                    ext = "m4a"; mime = "audio/mp4"
                }
                else -> {
                    // The ringtone path requests OPUS, delivered as Opus-in-WebM -> label as WebM.
                    ext = "webm"; mime = "audio/webm"
                }
            }
            val outputFile = File(cacheDir, "trimmed_ringtone_$songTag.$ext")
            if (outputFile.exists()) outputFile.delete()
            inputFile.copyTo(outputFile, overwrite = true)
            if (!outputFile.exists() || outputFile.length() == 0L) null
            else TrimResult(outputFile, ext, mime)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun openRingtoneSettings(context: Context, ringtoneUri: Uri? = null) {
        try {
            val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_RINGTONE)
                putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "Select Ringtone")
                if (ringtoneUri != null) {
                    putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, ringtoneUri)
                }
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            val intent = Intent(Settings.ACTION_SOUND_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
    }

    /**
     * True when the app can modify system settings (WRITE_SETTINGS), which is required to call
     * [RingtoneManager.setActualDefaultRingtoneUri] and truly apply the ringtone. Previously a stub
     * that always returned true, which made [requestSettingsPermission] dead code and let callers
     * assume a permission the app might not hold.
     */
    fun hasSettingsPermission(context: Context): Boolean {
        return Settings.System.canWrite(context)
    }

    /** Opens the system "modify system settings" grant screen for this app (WRITE_SETTINGS). */
    fun requestSettingsPermission(context: Context) {
        try {
            val intent = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
