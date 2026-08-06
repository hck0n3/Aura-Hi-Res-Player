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
import iad1tya.echo.music.echomusic.updater.ApkVersionInspector
import iad1tya.echo.music.echomusic.updater.PublicDownloads
import iad1tya.echo.music.echomusic.updater.UpdateApkFiles
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
 *
 * Every file it writes carries the version in its name, and an unfinished download is kept under a
 * `.apk.part` suffix. Before this, one fixed `echomusic.apk` held whatever the last run downloaded:
 * a Range request for a NEW release was answered relative to the PREVIOUS release's bytes, and the
 * install screen happily pointed the system installer at that leftover file. Both failures were
 * silent — the user installed the old build and believed they were current.
 */
class UpdateDownloadWorker(private val context: Context, workerParams: WorkerParameters) :
    CoroutineWorker(context, workerParams) {

    override suspend fun getForegroundInfo(): ForegroundInfo {
        val version = inputData.getString(KEY_VERSION) ?: ""
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
        val apkUrl = inputData.getString(KEY_APK_URL) ?: return@withContext Result.failure()
        val version = inputData.getString(KEY_VERSION) ?: "unknown"
        val fileSize = inputData.getString(KEY_FILE_SIZE) ?: ""
        val expectedBytes = inputData.getLong(KEY_FILE_SIZE_BYTES, 0L)
        DownloadNotificationManager.ensureInitialized(context)

        // A retrying run stays ENQUEUED and holds the unique work slot. Give up after a few attempts so
        // the screen can report the failure and the next tap starts a clean run, instead of a dead
        // request sitting there absorbing taps forever.
        if (runAttemptCount >= MAX_ATTEMPTS) {
            DownloadNotificationManager.showDownloadFailed(version, context.getString(R.string.download_failed))
            return@withContext Result.failure()
        }

        // Run in the foreground so the OS keeps the download alive in the background.
        runCatching { setForeground(getForegroundInfo()) }
        DownloadNotificationManager.showDownloadStarting(version, fileSize)

        val isZip = apkUrl.contains("nightly.link") || apkUrl.endsWith(".zip")
        val downloadDir = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "echo_updates")
        if (!downloadDir.exists()) downloadDir.mkdirs()

        val finalApk = File(downloadDir, UpdateApkFiles.apkFileName(version))
        val partFile = File(downloadDir, if (isZip) UpdateApkFiles.zipPartFileName(version) else UpdateApkFiles.partFileName(version))

        // Anything else in this private folder belongs to a release we are no longer installing: a
        // previous version's APK (the file the old updater used to install by mistake) or an abandoned
        // partial. Removing it here means the wrong APK is not merely un-chosen, it is not present.
        purgeOtherFiles(downloadDir, keepVersion = version)

        try {
            // The finished APK for THIS version may already be here (app killed right after a download).
            // Trust it only after the archive itself says it is that version and carries our signature.
            if (!isZip && finalApk.isFile) {
                if (ApkVersionInspector.isInstallable(context, finalApk, version)) {
                    return@withContext finish(finalApk, version, downloadDir)
                }
                finalApk.delete()
            }

            val onDisk = if (partFile.isFile) partFile.length() else 0L
            when (UpdateApkFiles.partState(onDisk, expectedBytes)) {
                UpdateApkFiles.PartState.RESTART -> if (partFile.exists()) partFile.delete()
                UpdateApkFiles.PartState.COMPLETE -> if (!isZip) {
                    // Already fully on disk: promote and verify without touching the network.
                    return@withContext promoteAndFinish(partFile, finalApk, version, downloadDir)
                }
                UpdateApkFiles.PartState.RESUME -> Unit
            }
            // Zips are always fetched whole so the extract is clean.
            if (isZip && partFile.exists()) partFile.delete()

            val existing = if (partFile.isFile) partFile.length() else 0L

            val connection = (URL(apkUrl).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                instanceFollowRedirects = true
                connectTimeout = 30000
                readTimeout = 60000
                if (existing > 0) setRequestProperty("Range", "bytes=$existing-")
            }
            connection.connect()

            val code = connection.responseCode
            if (code == HTTP_RANGE_NOT_SATISFIABLE) {
                // The bytes on disk are not a prefix of this file. Throw them away and start over
                // instead of asking for the same impossible range forever.
                connection.disconnect()
                partFile.delete()
                return@withContext Result.retry()
            }
            if (code != HttpURLConnection.HTTP_OK && code != HttpURLConnection.HTTP_PARTIAL) {
                connection.disconnect()
                DownloadNotificationManager.showDownloadFailed(version, context.getString(R.string.server_error, code))
                return@withContext Result.retry()
            }

            // 206 = server honoured the range -> resume/append. 200 = full body -> start fresh.
            val resuming = code == HttpURLConnection.HTTP_PARTIAL && existing > 0
            val startBytes = if (resuming) existing else 0L
            val remaining = connection.contentLength.toLong()
            val declaredTotal = if (remaining > 0) startBytes + remaining else -1L
            // Prefer the size GitHub reported for the asset; fall back to Content-Length.
            val totalLength = if (expectedBytes > 0L) expectedBytes else declaredTotal

            val output = if (resuming) {
                RandomAccessFile(partFile, "rw").apply { seek(startBytes) }
            } else {
                if (partFile.exists()) partFile.delete()
                RandomAccessFile(partFile, "rw")
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
                        val progress = (totalBytesRead * 100 / totalLength).toInt().coerceIn(0, 100)
                        if (progress != lastProgress) {
                            lastProgress = progress
                            // Single silent progress update per tick: setForeground re-posts the ongoing
                            // notification (setOnlyAlertOnce), so no per-1% re-alert/heads-up/sound.
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

            // A connection that dies mid-body still ends the read loop cleanly. Without this check a
            // truncated file would be reported as a finished download.
            val written = partFile.length()
            val expectedFinal = if (expectedBytes > 0L) expectedBytes else declaredTotal
            if (!isZip && expectedFinal > 0L && written != expectedFinal) {
                if (written > expectedFinal) partFile.delete()
                DownloadNotificationManager.showDownloadFailed(
                    version,
                    context.getString(R.string.download_incomplete),
                )
                return@withContext Result.retry()
            }

            if (isZip) {
                val extracted = extractApkFromZip(partFile, finalApk, version) ?: return@withContext Result.failure()
                return@withContext verifyAndFinish(extracted, version, downloadDir)
            }

            promoteAndFinish(partFile, finalApk, version, downloadDir)
        } catch (e: Exception) {
            // Don't delete the partial: retry resumes from where it stopped (with WorkManager backoff).
            DownloadNotificationManager.showDownloadFailed(version, e.message ?: context.getString(R.string.download_failed))
            Result.retry()
        }
    }

    /** Moves the finished `.part` onto its final `.apk` name, then verifies it. */
    private fun promoteAndFinish(partFile: File, finalApk: File, version: String, dir: File): Result {
        if (finalApk.exists()) finalApk.delete()
        if (!partFile.renameTo(finalApk)) {
            try {
                partFile.copyTo(finalApk, overwrite = true)
                partFile.delete()
            } catch (e: Exception) {
                DownloadNotificationManager.showDownloadFailed(version, e.message ?: "rename failed")
                return Result.retry()
            }
        }
        return verifyAndFinish(finalApk, version, dir)
    }

    /**
     * The gate the old updater never had: ask the downloaded archive which version it is. A file that
     * is not the release we were told to fetch is deleted, never installed — one more download costs
     * bandwidth, installing the wrong build costs the user every fix since.
     */
    private fun verifyAndFinish(apk: File, version: String, dir: File): Result {
        if (!ApkVersionInspector.isInstallable(context, apk, version)) {
            apk.delete()
            DownloadNotificationManager.showDownloadFailed(
                version,
                context.getString(R.string.update_version_mismatch),
            )
            // One clean retry covers a corrupt transfer; beyond that the asset itself is wrong and
            // re-downloading it forever would only burn the user's data.
            return if (runAttemptCount < 2) Result.retry() else Result.failure()
        }
        return finish(apk, version, dir)
    }

    /** Publishes the verified APK: user-visible copy, housekeeping, "tap to install" notification. */
    private fun finish(apk: File, version: String, dir: File): Result {
        if (version.startsWith("nightly-r")) {
            version.removePrefix("nightly-r").toIntOrNull()?.let { runNumber ->
                context.getSharedPreferences("update_settings", Context.MODE_PRIVATE)
                    .edit().putInt("last_installed_nightly_run", runNumber).apply()
            }
        }

        purgeOtherFiles(dir, keepVersion = version)

        // Keep exactly one copy in the user's Downloads folder: the update they are about to install.
        // Previous releases piled up there — one ~100 MB APK per update, never removed. Keeping the
        // current name also stops a re-run from writing a second "… (1).apk" beside it, and skipping
        // the copy when it is already there avoids re-writing ~100 MB on every tap.
        val publicName = UpdateApkFiles.apkFileName(version)
        runCatching { PublicDownloads.deleteOurApks(context, keepNames = setOf(publicName)) }
        if (!PublicDownloads.hasApk(context, publicName)) {
            PublicDownloads.saveApk(context, apk, publicName)
        }

        DownloadNotificationManager.showDownloadComplete(version, apk.absolutePath)
        return Result.success(
            workDataOf(
                "file_path" to apk.absolutePath,
                KEY_VERSION to version,
            ),
        )
    }

    /**
     * Removes files this app previously wrote for OTHER versions. Only names produced by
     * [UpdateApkFiles] (plus the two legacy fixed names) are touched — never an unrecognised file.
     */
    private fun purgeOtherFiles(dir: File, keepVersion: String) {
        val names = dir.listFiles()?.filter { it.isFile }?.map { it.name } ?: return
        UpdateApkFiles.deletableNames(names, keepVersion).forEach { name ->
            runCatching { File(dir, name).delete() }
        }
    }

    private fun extractApkFromZip(zipFile: File, targetApk: File, version: String): File? {
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

    companion object {
        const val WORK_NAME = "update_download"
        const val KEY_APK_URL = "apk_url"
        const val KEY_VERSION = "version"
        const val KEY_FILE_SIZE = "file_size"
        const val KEY_FILE_SIZE_BYTES = "file_size_bytes"

        private const val HTTP_RANGE_NOT_SATISFIABLE = 416
        private const val MAX_ATTEMPTS = 5
    }
}
