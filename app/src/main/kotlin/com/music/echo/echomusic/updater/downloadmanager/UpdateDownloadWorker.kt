package iad1tya.echo.music.echomusic.updater.downloadmanager

import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Environment
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import iad1tya.echo.music.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipInputStream

/**
 * Downloads the update APK. Runs as a FOREGROUND worker so Android won't kill it when the app is
 * backgrounded, and RESUMES from the partial file (HTTP Range) instead of restarting from zero on
 * every hiccup — which is why the download used to "keep restarting" / not continue in the
 * background on some devices.
 */
class UpdateDownloadWorker(private val context: Context, workerParams: WorkerParameters) :
    CoroutineWorker(context, workerParams) {

    override suspend fun getForegroundInfo(): ForegroundInfo {
        val version = inputData.getString("version") ?: ""
        val notification = DownloadNotificationManager.buildOngoingNotification(context, version, 0)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(
                DownloadNotificationManager.FOREGROUND_NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
            )
        } else {
            ForegroundInfo(DownloadNotificationManager.FOREGROUND_NOTIFICATION_ID, notification)
        }
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val apkUrl = inputData.getString("apk_url") ?: return@withContext Result.failure()
        val version = inputData.getString("version") ?: "unknown"
        val fileSize = inputData.getString("file_size") ?: ""

        // Run in the foreground so the OS keeps the download alive in the background.
        runCatching { setForeground(getForegroundInfo()) }
        DownloadNotificationManager.showDownloadStarting(version, fileSize)

        val isZip = apkUrl.contains("nightly.link") || apkUrl.endsWith(".zip")
        val downloadDir = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "echo_updates")
        if (!downloadDir.exists()) downloadDir.mkdirs()
        val downloadFile = if (isZip) File(downloadDir, "echo_temp.zip") else File(downloadDir, "echomusic.apk")

        try {
            // Resume from the bytes already on disk (APK only; zips re-download for a clean extract).
            if (isZip && downloadFile.exists()) downloadFile.delete()
            val existing = if (!isZip && downloadFile.exists()) downloadFile.length() else 0L

            val connection = (URL(apkUrl).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                instanceFollowRedirects = true
                connectTimeout = 30000
                readTimeout = 60000
                if (existing > 0) setRequestProperty("Range", "bytes=$existing-")
            }
            connection.connect()

            val code = connection.responseCode
            if (code != HttpURLConnection.HTTP_OK && code != HttpURLConnection.HTTP_PARTIAL) {
                DownloadNotificationManager.showDownloadFailed(version, context.getString(R.string.server_error, code))
                return@withContext Result.retry()
            }

            // 206 = server honoured the range -> resume/append. 200 = full body -> start fresh.
            val resuming = code == HttpURLConnection.HTTP_PARTIAL && existing > 0
            val startBytes = if (resuming) existing else 0L
            val remaining = connection.contentLength.toLong()
            val totalLength = if (remaining > 0) startBytes + remaining else -1L

            val output = if (resuming) {
                RandomAccessFile(downloadFile, "rw").apply { seek(startBytes) }
            } else {
                if (downloadFile.exists()) downloadFile.delete()
                RandomAccessFile(downloadFile, "rw")
            }

            connection.inputStream.use { input ->
                val buffer = ByteArray(64 * 1024)
                var totalBytesRead = startBytes
                var lastProgress = -1
                var bytesRead: Int
                while (input.read(buffer).also { bytesRead = it } != -1) {
                    if (isStopped) {
                        // Keep the partial file so the next run resumes instead of restarting.
                        output.close(); connection.disconnect()
                        return@withContext Result.retry()
                    }
                    output.write(buffer, 0, bytesRead)
                    totalBytesRead += bytesRead
                    if (totalLength > 0) {
                        val progress = (totalBytesRead * 100 / totalLength).toInt()
                        if (progress != lastProgress) {
                            lastProgress = progress
                            DownloadNotificationManager.updateDownloadProgress(progress, version)
                            setProgress(workDataOf("progress" to progress / 100f))
                            runCatching { setForeground(ForegroundInfo(
                                DownloadNotificationManager.FOREGROUND_NOTIFICATION_ID,
                                DownloadNotificationManager.buildOngoingNotification(context, version, progress),
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC else 0,
                            )) }
                        }
                    }
                }
            }
            output.close()
            connection.disconnect()

            val finalFile = if (isZip) extractApkFromZip(downloadFile, downloadDir, version) ?: return@withContext Result.failure()
            else downloadFile

            if (version.startsWith("nightly-r")) {
                version.removePrefix("nightly-r").toIntOrNull()?.let { runNumber ->
                    context.getSharedPreferences("update_settings", Context.MODE_PRIVATE)
                        .edit().putInt("last_installed_nightly_run", runNumber).apply()
                }
            }

            val publicName = "Aura-Hi-Res-Player-${version.removePrefix("v")}.apk"
            iad1tya.echo.music.echomusic.updater.PublicDownloads.saveApk(context, finalFile, publicName)
            DownloadNotificationManager.showDownloadComplete(version, finalFile.absolutePath)
            Result.success(workDataOf("file_path" to finalFile.absolutePath))
        } catch (e: Exception) {
            // Don't delete the partial: retry resumes from where it stopped (with WorkManager backoff).
            DownloadNotificationManager.showDownloadFailed(version, e.message ?: context.getString(R.string.download_failed))
            Result.retry()
        }
    }

    private fun extractApkFromZip(zipFile: File, dir: File, version: String): File? {
        val targetApk = File(dir, "echomusic.apk")
        var extracted = false
        try {
            ZipInputStream(zipFile.inputStream()).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    if (!entry.isDirectory && entry.name.endsWith(".apk")) {
                        FileOutputStream(targetApk).use { fos -> zis.copyTo(fos) }
                        extracted = true
                        break
                    }
                    entry = zis.nextEntry
                }
            }
        } catch (e: Exception) {
            DownloadNotificationManager.showDownloadFailed(version, e.message ?: "Failed to extract zip file")
            return null
        } finally {
            if (zipFile.exists()) zipFile.delete()
        }
        if (!extracted) {
            DownloadNotificationManager.showDownloadFailed(version, "Could not find APK in zip")
            return null
        }
        return targetApk
    }
}
