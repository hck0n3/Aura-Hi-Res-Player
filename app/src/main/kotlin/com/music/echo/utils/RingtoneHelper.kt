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
import android.provider.MediaStore
import android.provider.Settings
import androidx.core.content.getSystemService
import iad1tya.echo.music.constants.AudioQuality
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer

object RingtoneHelper {

    suspend fun getStreamUrl(context: Context, songId: String): String? = withContext(Dispatchers.IO) {
        try {
            val connectivityManager = context.getSystemService<ConnectivityManager>()!!
            val audioQuality = AudioQuality.OPUS

            val result = YTPlayerUtils.playerResponseForPlayback(
                videoId = songId,
                audioQuality = audioQuality,
                connectivityManager = connectivityManager,
            )
            result.getOrNull()?.streamUrl
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
        onProgress: (Float, String) -> Unit,
        onComplete: (Boolean, String, Uri?) -> Unit
    ) = withContext(Dispatchers.IO) {
        try {
            onProgress(0.05f, "Getting audio stream...")

            val streamUrl = getStreamUrl(context, songId)
            if (streamUrl == null) {
                withContext(Dispatchers.Main) {
                    onComplete(false, "Failed to get audio stream", null)
                }
                return@withContext
            }

            onProgress(0.1f, "Fetching audio...")

            val tempFile = File(context.cacheDir, "temp_ringtone_source_$songId")

            // P43: finite connect/read timeouts so a stalled CDN fails instead of hanging the
            // IO thread forever, and ensureActive() below makes the blocking read loop honour
            // coroutine cancellation (e.g. the user leaving the screen) between chunks.
            val connection = java.net.URL(streamUrl).openConnection().apply {
                connectTimeout = 10_000
                readTimeout = 10_000
            }
            connection.connect()
            val contentLength = connection.contentLength.toLong()

            connection.getInputStream().use { input ->
                tempFile.outputStream().use { output ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    var totalBytesRead = 0L

                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        ensureActive()
                        output.write(buffer, 0, bytesRead)
                        totalBytesRead += bytesRead

                        if (contentLength > 0) {
                            val progress = 0.1f + (totalBytesRead.toFloat() / contentLength) * 0.4f
                            withContext(Dispatchers.Main) {
                                onProgress(progress, "Downloading... ${(progress * 100).toInt()}%")
                            }
                        }
                    }
                }
            }

            if (!tempFile.exists() || tempFile.length() == 0L) {
                withContext(Dispatchers.Main) {
                    onComplete(false, "Failed to prepare source file", null)
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
                    onComplete(false, "Failed to process audio or output is empty", null)
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
                    onComplete(true, "\"$title\" quedó establecida como tu tono de llamada.", ringtoneUri)
                } else {
                    onComplete(true, "\"$title\" added to system ringtones. Please select it from settings.", ringtoneUri)
                }
            }

        } catch (e: Exception) {
            e.printStackTrace()
            withContext(Dispatchers.Main) {
                onComplete(false, "Error: ${e.message}", null)
            }
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
