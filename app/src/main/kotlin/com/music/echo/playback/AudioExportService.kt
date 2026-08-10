package iad1tya.echo.music.playback

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaScannerConnection
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.content.getSystemService
import androidx.documentfile.provider.DocumentFile
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.ReturnCode
import com.music.innertube.YouTube
import dagger.hilt.android.AndroidEntryPoint
import iad1tya.echo.music.R
import iad1tya.echo.music.constants.AudioQuality
import iad1tya.echo.music.constants.ExportingSongIdsKey
import iad1tya.echo.music.constants.ExportedSongIdsKey
import iad1tya.echo.music.constants.ExportedVideoIdsKey
import iad1tya.echo.music.db.MusicDatabase
import iad1tya.echo.music.db.entities.LyricsEntity
import iad1tya.echo.music.reco.GenreCache
import iad1tya.echo.music.utils.YTPlayerUtils
import iad1tya.echo.music.utils.dataStore
import androidx.datastore.preferences.core.edit
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
@AndroidEntryPoint
class AudioExportService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val httpClient = OkHttpClient()

    @Inject
    lateinit var database: MusicDatabase

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // P11: promote to a foreground dataSync service for the whole export so the OS does not
        // kill the long download+FFmpeg transcode mid-flight. The manifest already declares this
        // service with foregroundServiceType="dataSync" and the matching permission. Call this
        // FIRST (before any early return) to honour the startForegroundService 5s deadline.
        startExportForeground()

        val songId = intent?.getStringExtra(EXTRA_SONG_ID)
        val targetDirectoryUri = intent?.getStringExtra(EXTRA_TARGET_DIRECTORY_URI)
        if (songId == null || targetDirectoryUri == null) {
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        val songTitle = intent.getStringExtra(EXTRA_SONG_TITLE).orEmpty()
        val songArtist = intent.getStringExtra(EXTRA_SONG_ARTIST).orEmpty()
        val songAlbum = intent.getStringExtra(EXTRA_SONG_ALBUM).orEmpty()
        val artworkUrl = intent.getStringExtra(EXTRA_ARTWORK_URL).orEmpty()
        val exportAsVideo = intent.getBooleanExtra(EXTRA_EXPORT_AS_VIDEO, false)

        serviceScope.launch {
            if (exportAsVideo) {
                exportVideo(
                    songId = songId,
                    songTitle = songTitle,
                    targetDirectoryUri = targetDirectoryUri,
                )
            } else {
                exportSong(
                    songId = songId,
                    songTitle = songTitle,
                    songArtist = songArtist,
                    songAlbum = songAlbum,
                    artworkUrl = artworkUrl,
                    targetDirectoryUri = targetDirectoryUri,
                )
            }
        }
        return START_NOT_STICKY
    }

    private suspend fun exportVideo(
        songId: String,
        songTitle: String,
        targetDirectoryUri: String,
    ) {
        val safeTitle = sanitizeTitle(songTitle.ifBlank { songId })
        addExportingSongId(songId)
        var videoFileRef: File? = null
        var audioFileRef: File? = null
        var mp4FileRef: File? = null
        runCatching {
            val connectivityManager = getSystemService<ConnectivityManager>()
                ?: error("No connectivity manager")
            val playbackData = YTPlayerUtils.playerResponseForPlayback(
                videoId = songId,
                audioQuality = AudioQuality.OPUS,
                connectivityManager = connectivityManager,
            ).getOrThrow()
            val videoStreamUrl = YTPlayerUtils.videoStreamUrlDiag(
                videoId = songId,
                connectivityManager = connectivityManager,
                videoMaxHeight = 1080,
            ).getOrNull()?.takeIf { it.isNotBlank() }
                ?: error("No video stream available for this song")

            val rangedAudioUrl = playbackData.streamUrl.let { baseUrl ->
                val totalLength = playbackData.format.contentLength ?: 10_000_000L
                "$baseUrl&range=0-$totalLength"
            }

            val tempVideoFile = File.createTempFile("export_video_", ".mp4", cacheDir).also { videoFileRef = it }
            val tempAudioFile = File.createTempFile("export_audio_", ".m4a", cacheDir).also { audioFileRef = it }
            val tempMp4File = File.createTempFile("export_result_", ".mp4", cacheDir).also { mp4FileRef = it }

            fun downloadTo(url: String, dest: File) {
                httpClient.newCall(Request.Builder().url(url).build()).execute().use { response ->
                    if (!response.isSuccessful) {
                        error("Stream request failed with ${response.code}")
                    }
                    val body = response.body ?: error("No response body")
                    body.byteStream().use { input ->
                        dest.outputStream().use { output ->
                            input.copyTo(output)
                            output.flush()
                        }
                    }
                }
                if (!dest.exists() || dest.length() <= 0L) {
                    error("Downloaded stream is empty")
                }
            }

            updateExportProgress(15, getString(R.string.export_processing_audio))
            downloadTo(videoStreamUrl, tempVideoFile)
            updateExportProgress(45, getString(R.string.export_processing_audio))
            downloadTo(rangedAudioUrl, tempAudioFile)
            updateExportProgress(65, getString(R.string.export_processing_tags))

            val ffmpegCommand = buildVideoFfmpegCommand(
                videoPath = tempVideoFile.absolutePath,
                audioPath = tempAudioFile.absolutePath,
                outputPath = tempMp4File.absolutePath,
            )
            val session = FFmpegKit.execute(ffmpegCommand)
            val returnCode = session.returnCode
            if (returnCode == null || !ReturnCode.isSuccess(returnCode)) {
                Log.e(TAG, "FFmpeg video export failed: ${session.output?.take(400)}")
                error("FFmpeg failed")
            }
            if (!tempMp4File.exists() || tempMp4File.length() <= 0L) {
                error("Exported MP4 file is empty")
            }
            updateExportProgress(90, getString(R.string.export_writing_file))

            val destinationDir = DocumentFile.fromTreeUri(this, Uri.parse(targetDirectoryUri))
                ?: error("Export directory unavailable")
            val outputFile = destinationDir.createFile("video/mp4", "$safeTitle.mp4")
                ?: error("Unable to create output file")

            tempMp4File.inputStream().use { input ->
                contentResolver.openOutputStream(outputFile.uri, "w")?.use { output ->
                    input.copyTo(output)
                    output.flush()
                } ?: error("Unable to open output stream")
            }

            addExportedVideoId(songId)

            val shared = runCatching {
                val shareCopy = File(cacheDir, "share_export_$safeTitle.mp4")
                tempMp4File.copyTo(shareCopy, overwrite = true)
                withContext(Dispatchers.Main) {
                    val uri = FileProvider.getUriForFile(
                        this@AudioExportService,
                        "${packageName}.FileProvider",
                        shareCopy,
                    )
                    val send = Intent(Intent.ACTION_SEND).apply {
                        type = "video/mp4"
                        putExtra(Intent.EXTRA_STREAM, uri)
                        clipData = android.content.ClipData.newRawUri(shareCopy.name, uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    startActivity(
                        Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                }
            }.isSuccess
            if (!shared) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        this@AudioExportService,
                        "Exported: $safeTitle.mp4",
                        Toast.LENGTH_LONG,
                    ).show()
                }
            }
        }.onFailure { throwable ->
            if (throwable is CancellationException) {
                Log.w(TAG, "Video export cancelled for $songId")
            } else {
                Log.e(TAG, "Video export failed for $songId", throwable)
                runCatching {
                    withContext(NonCancellable + Dispatchers.Main) {
                        Toast.makeText(
                            this@AudioExportService,
                            "Export failed: $safeTitle",
                            Toast.LENGTH_LONG,
                        ).show()
                    }
                }
            }
        }
        videoFileRef?.delete()
        audioFileRef?.delete()
        mp4FileRef?.delete()
        withContext(NonCancellable) {
            removeExportingSongId(songId)
        }
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private suspend fun exportSong(
        songId: String,
        songTitle: String,
        songArtist: String,
        songAlbum: String,
        artworkUrl: String,
        targetDirectoryUri: String,
    ) {
        val safeTitle = sanitizeTitle(songTitle.ifBlank { songId })
        addExportingSongId(songId)
        // P9: hold temp-file references outside runCatching so they can be cleaned up on BOTH the
        // success and failure paths (runCatching never rethrows, so the block after it acts as a
        // finally). Previously the .delete() calls sat after every error() throw site and leaked
        // multi-MB temp files into cacheDir on any failed export.
        var sourceFileRef: File? = null
        var artworkFileRef: File? = null
        var mp3FileRef: File? = null
        runCatching {
            val connectivityManager = getSystemService<ConnectivityManager>()
                ?: error("No connectivity manager")
            val playbackData = YTPlayerUtils.playerResponseForPlayback(
                videoId = songId,
                audioQuality = AudioQuality.OPUS,
                connectivityManager = connectivityManager,
            ).getOrThrow()
            val year = YouTube.getMediaInfo(songId)
                .getOrNull()
                ?.uploadDate
                ?.let { uploadDate ->
                    Regex("(19|20)\\d{2}")
                        .find(uploadDate)
                        ?.value
                        ?.toIntOrNull()
                }
            val dbSong = runCatching { database.getSongById(songId) }.getOrNull()
            val albumId = dbSong?.song?.albumId
            val albumArtist = albumId
                ?.let { id -> runCatching { database.album(id).first() }.getOrNull() }
                ?.artists
                ?.joinToString(", ") { it.name }
                ?.takeIf { it.isNotBlank() }
            val trackNumber = albumId?.let { id ->
                runCatching {
                    database.openHelper.readableDatabase.query(
                        "SELECT `index` FROM song_album_map WHERE songId = ? AND albumId = ? LIMIT 1",
                        arrayOf(songId, id),
                    ).use { cursor ->
                        if (cursor.moveToFirst() && !cursor.isNull(0)) {
                            cursor.getInt(0) + 1
                        } else {
                            null
                        }
                    }
                }.getOrNull()
            }
            val genre = dbSong?.artists
                ?.firstOrNull()
                ?.name
                ?.lowercase()
                ?.let { key -> GenreCache.snapshot(this@AudioExportService)[key] }
                ?.takeIf { it.isNotBlank() }
            val lyrics = runCatching { database.lyrics(songId).first() }.getOrNull()
                ?.lyrics
                ?.takeIf { it.isNotBlank() && it != LyricsEntity.LYRICS_NOT_FOUND }
            val rangedStreamUrl = playbackData.streamUrl.let { baseUrl ->
                val totalLength = playbackData.format.contentLength ?: 10_000_000L
                "$baseUrl&range=0-$totalLength"
            }

            val tempSourceFile = File.createTempFile("export_source_", ".m4a", cacheDir).also { sourceFileRef = it }
            val tempArtworkFile = File.createTempFile("export_cover_", ".jpg", cacheDir).also { artworkFileRef = it }
            val tempMp3File = File.createTempFile("export_result_", ".mp3", cacheDir).also { mp3FileRef = it }

            val streamRequest = Request.Builder().url(rangedStreamUrl).build()
            var totalBytes = -1L
            var bytesWritten = 0L
            httpClient.newCall(streamRequest).execute().use { response ->
                if (!response.isSuccessful) {
                    error("Stream request failed with ${response.code}")
                }
                val body = response.body ?: error("No response body")
                totalBytes = body.contentLength()
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                body.byteStream().use { input ->
                    tempSourceFile.outputStream().use { output ->
                        var read: Int
                        var lastProgress = -1
                        while (input.read(buffer).also { read = it } != -1) {
                            output.write(buffer, 0, read)
                            bytesWritten += read
                            if (totalBytes > 0) {
                                val progress = ((bytesWritten * 55L) / totalBytes).toInt().coerceIn(0, 55)
                                if (progress > lastProgress) {
                                    lastProgress = progress
                                    updateExportProgress(progress, getString(R.string.export_processing_audio))
                                }
                            }
                        }
                        output.flush()
                    }
                }
            }
            if (totalBytes > 0 && bytesWritten < totalBytes) {
                error("Incomplete export source: wrote $bytesWritten of $totalBytes bytes")
            }

            val resolvedArtworkUrl = artworkUrl.takeIf { it.isNotBlank() }
                ?: dbSong?.song?.thumbnailUrl?.takeIf { it.isNotBlank() }
                ?: playbackData.videoDetails?.thumbnail?.thumbnails?.lastOrNull()?.url
                ?: ""

            val artworkDownloaded = prepareJpegCover(
                artworkUrl = resolvedArtworkUrl,
                destFile = tempArtworkFile,
            )
            Log.i(
                TAG,
                "export artwork=${if (artworkDownloaded) "ok" else "fail"} " +
                    "bytes=${if (artworkDownloaded) tempArtworkFile.length() else 0}",
            )

            fun runFfmpeg(coverPath: String?, useLoudnorm: Boolean): Boolean {
                val ffmpegCommand = buildFfmpegCommand(
                    inputPath = tempSourceFile.absolutePath,
                    outputPath = tempMp3File.absolutePath,
                    title = songTitle,
                    artist = songArtist,
                    album = songAlbum,
                    albumArtist = albumArtist,
                    genre = genre,
                    track = trackNumber,
                    lyrics = lyrics,
                    year = year,
                    coverPath = coverPath,
                    useLoudnorm = useLoudnorm,
                )
                val session = FFmpegKit.execute(ffmpegCommand)
                val returnCode = session.returnCode
                val ok = returnCode != null && ReturnCode.isSuccess(returnCode)
                if (!ok) {
                    Log.e(
                        TAG,
                        "FFmpeg failed cover=${coverPath != null} loudnorm=$useLoudnorm: " +
                            "${session.output?.take(400)}",
                    )
                }
                return ok
            }

            updateExportProgress(60, getString(R.string.export_processing_tags))
            val coverPath = if (artworkDownloaded) tempArtworkFile.absolutePath else null
            var usedLoudnorm = true
            val ffmpegOk = run {
                if (coverPath != null) {
                    if (runFfmpeg(coverPath, useLoudnorm = true)) return@run true
                    if (runFfmpeg(null, useLoudnorm = true)) return@run true
                } else if (runFfmpeg(null, useLoudnorm = true)) {
                    return@run true
                }
                usedLoudnorm = false
                Log.i(TAG, "export loudnorm=skip retrying without")
                if (coverPath != null) {
                    if (runFfmpeg(coverPath, useLoudnorm = false)) return@run true
                    if (runFfmpeg(null, useLoudnorm = false)) return@run true
                }
                runFfmpeg(null, useLoudnorm = false)
            }
            if (!ffmpegOk) {
                error("FFmpeg failed")
            }
            if (!tempMp3File.exists() || tempMp3File.length() <= 0L) {
                error("Exported MP3 file is empty")
            }
            Log.i(
                TAG,
                "export ffmpeg=ok loudnorm=${if (usedLoudnorm) "ok" else "skip"} size=${tempMp3File.length()}",
            )
            updateExportProgress(90, getString(R.string.export_writing_file))
            val destinationDir = DocumentFile.fromTreeUri(this, Uri.parse(targetDirectoryUri))
                ?: error("Export directory unavailable")
            val outputFile = destinationDir.createFile("audio/mpeg", "$safeTitle.mp3")
                ?: error("Unable to create output file")

            tempMp3File.inputStream().use { input ->
                contentResolver.openOutputStream(outputFile.uri, "w")?.use { output ->
                    input.copyTo(output)
                    output.flush()
                } ?: error("Unable to open output stream")
            }

            // Help Files / media apps pick up ID3 + cover after SAF write.
            // scanFile needs a filesystem path — a SAF content:// string is ignored by MediaScanner.
            runCatching {
                val scanPath = outputFile.uri.path?.takeIf { it.startsWith("/") && java.io.File(it).exists() }
                if (scanPath != null) {
                    MediaScannerConnection.scanFile(
                        this,
                        arrayOf(scanPath),
                        arrayOf("audio/mpeg"),
                        null,
                    )
                }
            }

            // Point the in-app song row at the exported file so Biblioteca ▸ Exportadas / player
            // read the MP3's embedded cover via LocalAudioArtFetcher (not only the remote YT URL).
            runCatching {
                val artModel = iad1tya.echo.music.utils.coil.LocalAudioArtFetcher.uriFor(
                    outputFile.uri.toString(),
                )
                val entity = database.getSongById(songId)?.song
                if (entity != null) {
                    database.update(entity.copy(thumbnailUrl = artModel))
                }
            }

            addExportedSongId(songId)
        }.onFailure { throwable ->
            // P7: never swallow the failure silently. Cancellation (e.g. the service being
            // destroyed) is expected teardown, not an export error, so don't alarm the user for it.
            if (throwable is CancellationException) {
                Log.w(TAG, "Export cancelled for $songId")
            } else {
                Log.e(TAG, "Export failed for $songId", throwable)
                // Surface the failure to the user on the main thread. NonCancellable keeps the
                // toast alive even if we got here via cancellation-adjacent teardown.
                runCatching {
                    withContext(NonCancellable + Dispatchers.Main) {
                        Toast.makeText(
                            this@AudioExportService,
                            "Export failed: $safeTitle",
                            Toast.LENGTH_LONG,
                        ).show()
                    }
                }
            }
        }
        // P9: clean up temp files on every path (success, failure, cancellation).
        sourceFileRef?.delete()
        artworkFileRef?.delete()
        mp3FileRef?.delete()
        // Clear the "exporting" flag even if the coroutine was cancelled, so the song is never
        // left stuck in the exporting set (P11 graceful-teardown).
        withContext(NonCancellable) {
            removeExportingSongId(songId)
        }
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun updateExportProgress(percent: Int, text: String) {
        runCatching {
            val nm = getSystemService<NotificationManager>() ?: return
            val notification = NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_launcher_nobg)
                .setContentTitle(getString(R.string.exporting))
                .setContentText(text)
                .setProgress(100, percent.coerceIn(0, 100), false)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build()
            nm.notify(NOTIFICATION_ID, notification)
        }
    }

    private fun startExportForeground() {
        runCatching {
            val nm = getSystemService<NotificationManager>()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && nm?.getNotificationChannel(CHANNEL_ID) == null) {
                nm?.createNotificationChannel(
                    NotificationChannel(
                        CHANNEL_ID,
                        "Audio export",
                        NotificationManager.IMPORTANCE_LOW,
                    ).apply { setShowBadge(false) },
                )
            }
            val notification = NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_launcher_nobg)
                .setContentTitle(getString(R.string.exporting))
                .setContentText(getString(R.string.export_preparing))
                .setProgress(100, 0, false)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build()
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                notification,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC else 0,
            )
        }.onFailure {
            // NOTE: this is NOT a graceful fallback to a plain service. The caller starts us via
            // ContextCompat.startForegroundService(), which obliges the service to call
            // startForeground() within ~5s; if we never make that call successfully the OS raises
            // ForegroundServiceDidNotStartInTimeException and kills the process. This runCatching
            // only swallows an exception thrown by startForeground() itself in the rare cases where
            // the platform does not also crash us (e.g. notification build/channel hiccups); it does
            // not let the export keep running as an ordinary background service. We log so the
            // failure is diagnosable when it does surface.
            Log.e(TAG, "Unable to start export foreground service", it)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    private suspend fun addExportedSongId(songId: String) {
        dataStore.edit { preferences ->
            val current = preferences[ExportedSongIdsKey].orEmpty()
                .split(',')
                .map { it.trim() }
                .filter { it.isNotBlank() }
            val updated = listOf(songId) + current.filterNot { it == songId }
            preferences[ExportedSongIdsKey] = updated.take(1000).joinToString(",")
        }
    }

    private suspend fun addExportedVideoId(songId: String) {
        dataStore.edit { preferences ->
            val current = preferences[ExportedVideoIdsKey].orEmpty()
                .split(',')
                .map { it.trim() }
                .filter { it.isNotBlank() }
            val updated = listOf(songId) + current.filterNot { it == songId }
            preferences[ExportedVideoIdsKey] = updated.take(1000).joinToString(",")
        }
        // So Biblioteca ▸ Vídeos exportados / tap-to-watch arms video mode + expands the player.
        runCatching {
            val entity = database.getSongById(songId)?.song
            if (entity != null && !entity.isVideo) {
                database.update(entity.copy(isVideo = true))
            }
        }
    }

    private suspend fun addExportingSongId(songId: String) {
        dataStore.edit { preferences ->
            val current = preferences[ExportingSongIdsKey].orEmpty()
                .split(',')
                .map { it.trim() }
                .filter { it.isNotBlank() }
            val updated = listOf(songId) + current.filterNot { it == songId }
            preferences[ExportingSongIdsKey] = updated.take(1000).joinToString(",")
        }
    }

    private suspend fun removeExportingSongId(songId: String) {
        dataStore.edit { preferences ->
            val current = preferences[ExportingSongIdsKey].orEmpty()
                .split(',')
                .map { it.trim() }
                .filter { it.isNotBlank() }
            preferences[ExportingSongIdsKey] = current.filterNot { it == songId }.joinToString(",")
        }
    }

    /**
     * Download artwork bytes and re-encode as a real JPEG. YouTube often serves webp; FFmpeg's
     * attached_pic path is unreliable unless the second input is a plain JPEG.
     */
    private fun prepareJpegCover(artworkUrl: String, destFile: File): Boolean {
        if (artworkUrl.isBlank()) return false
        return runCatching {
            val bytes = httpClient.newCall(Request.Builder().url(artworkUrl).build()).execute().use { response ->
                if (!response.isSuccessful) return@runCatching false
                response.body?.bytes() ?: return@runCatching false
            }
            if (bytes.isEmpty()) return@runCatching false
            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                ?: return@runCatching false
            FileOutputStream(destFile).use { out ->
                val ok = bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
                out.flush()
                if (!ok) return@runCatching false
            }
            if (!bitmap.isRecycled) bitmap.recycle()
            destFile.length() > 0L
        }.getOrDefault(false)
    }

    companion object {
        private const val TAG = "AudioExportService"
        private const val CHANNEL_ID = "audio_export"
        private const val NOTIFICATION_ID = 0xE5A0

        private const val EXTRA_SONG_ID = "extra_song_id"
        private const val EXTRA_SONG_TITLE = "extra_song_title"
        private const val EXTRA_SONG_ARTIST = "extra_song_artist"
        private const val EXTRA_SONG_ALBUM = "extra_song_album"
        private const val EXTRA_ARTWORK_URL = "extra_artwork_url"
        private const val EXTRA_TARGET_DIRECTORY_URI = "extra_target_directory_uri"
        private const val EXTRA_EXPORT_AS_VIDEO = "extra_export_as_video"

        fun start(
            context: Context,
            songId: String,
            songTitle: String,
            songArtist: String,
            songAlbum: String,
            artworkUrl: String,
            targetDirectoryUri: String,
            exportAsVideo: Boolean = false,
        ) {
            val intent = Intent(context, AudioExportService::class.java).apply {
                putExtra(EXTRA_SONG_ID, songId)
                putExtra(EXTRA_SONG_TITLE, songTitle)
                putExtra(EXTRA_SONG_ARTIST, songArtist)
                putExtra(EXTRA_SONG_ALBUM, songAlbum)
                putExtra(EXTRA_ARTWORK_URL, artworkUrl)
                putExtra(EXTRA_TARGET_DIRECTORY_URI, targetDirectoryUri)
                putExtra(EXTRA_EXPORT_AS_VIDEO, exportAsVideo)
            }
            // P11: start as a foreground service so onStartCommand can call startForeground within
            // the platform deadline and the export survives the app leaving the foreground.
            ContextCompat.startForegroundService(context, intent)
        }

        private fun sanitizeTitle(title: String): String =
            title
                .replace(Regex("[\\\\/:*?\"<>|]"), "_")
                .replace(Regex("\\s+"), " ")
                .trim()
                .ifBlank { "song_${System.currentTimeMillis()}" }

        private fun buildFfmpegCommand(
            inputPath: String,
            outputPath: String,
            title: String,
            artist: String,
            album: String,
            albumArtist: String?,
            genre: String?,
            track: Int?,
            lyrics: String?,
            year: Int?,
            coverPath: String?,
            useLoudnorm: Boolean = true,
        ): String {
            val escapedInput = inputPath.ffmpegEscape()
            val escapedOutput = outputPath.ffmpegEscape()
            val titleMeta = title.ffmpegEscape()
            val artistMeta = artist.ffmpegEscape()
            val albumMeta = album.ffmpegEscape()
            val yearMeta = year?.toString()?.ffmpegEscape()
            val albumArtistMeta = albumArtist?.takeIf { it.isNotBlank() }?.ffmpegEscape()
            val genreMeta = genre?.takeIf { it.isNotBlank() }?.ffmpegEscape()
            val trackMeta = track?.takeIf { it > 0 }?.toString()?.ffmpegEscape()
            val lyricsMeta = lyrics?.takeIf { it.isNotBlank() }?.ffmpegEscape()
            val dateFlags = if (yearMeta != null) " -metadata date='$yearMeta' -metadata year='$yearMeta'" else ""
            val albumArtistFlag =
                if (albumArtistMeta != null) " -metadata album_artist='$albumArtistMeta'" else ""
            val genreFlag = if (genreMeta != null) " -metadata genre='$genreMeta'" else ""
            val trackFlag = if (trackMeta != null) " -metadata track='$trackMeta'" else ""
            val lyricsFlag = if (lyricsMeta != null) " -metadata lyrics='$lyricsMeta'" else ""
            val extraMeta = "$dateFlags$albumArtistFlag$genreFlag$trackFlag$lyricsFlag"
            // Match typical streaming loudness so exported MP3s aren't quieter than Aura's leveled stream.
            // Target closer to in-app Safe Volume perceived level (was I=-14 → exports sounded quieter).
            val loudnormFilter = if (useLoudnorm) " -af loudnorm=I=-11:TP=-1.5:LRA=11" else ""
            return if (coverPath != null) {
                val escapedCover = coverPath.ffmpegEscape()
                "-y -i '$escapedInput' -i '$escapedCover' -map 0:a -map 1:v -c:v mjpeg -disposition:v attached_pic$loudnormFilter -c:a libmp3lame -b:a 320k -id3v2_version 3 -metadata title='$titleMeta' -metadata artist='$artistMeta' -metadata album='$albumMeta'$extraMeta -metadata:s:v title='Album cover' -metadata:s:v comment='Cover (front)' '$escapedOutput'"
            } else {
                "-y -i '$escapedInput'$loudnormFilter -c:a libmp3lame -b:a 320k -id3v2_version 3 -metadata title='$titleMeta' -metadata artist='$artistMeta' -metadata album='$albumMeta'$extraMeta '$escapedOutput'"
            }
        }

        private fun String.ffmpegEscape(): String = replace("'", "'\\''")

        private fun buildVideoFfmpegCommand(
            videoPath: String,
            audioPath: String,
            outputPath: String,
        ): String {
            val escapedVideo = videoPath.ffmpegEscape()
            val escapedAudio = audioPath.ffmpegEscape()
            val escapedOutput = outputPath.ffmpegEscape()
            return "-y -i '$escapedVideo' -i '$escapedAudio' -map 0:v:0 -map 1:a:0 " +
                "-vf scale='min(1920,iw)':-2 -c:v libx264 -pix_fmt yuv420p -preset veryfast -crf 20 " +
                "-af loudnorm=I=-11:TP=-1.5:LRA=11 -c:a aac -b:a 192k -movflags +faststart '$escapedOutput'"
        }
    }
}