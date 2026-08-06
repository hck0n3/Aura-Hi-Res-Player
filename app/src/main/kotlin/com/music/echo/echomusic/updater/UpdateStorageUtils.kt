package iad1tya.echo.music.echomusic.updater

import android.content.Context
import android.os.Environment
import iad1tya.echo.music.echomusic.updater.downloadmanager.DownloadNotificationManager
import java.io.File

/**
 * Housekeeping for the update APKs on disk.
 *
 * "Borrar actualizaciones descargadas" used to be the only way to get a new update to install: the
 * updater kept one fixed `echomusic.apk`, so deleting it was what forced a real download. Files are
 * versioned now and every install is checked against the archive itself, so this is what its name
 * says — freeing space — and nothing depends on the user pressing it.
 */

fun getDownloadedApksDir(context: Context): File {
    return File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "echo_updates")
}

private fun ourFiles(context: Context): List<File> {
    val dir = getDownloadedApksDir(context)
    if (!dir.exists() || !dir.isDirectory) return emptyList()
    return dir.listFiles()?.filter { it.isFile && UpdateApkFiles.isOwnUpdateFile(it.name) } ?: emptyList()
}

/**
 * How many finished update APKs this app is holding — the private copy plus the copies it placed in
 * the user's Downloads folder, which is where they actually piled up (one per release, ~100 MB each).
 */
fun getDownloadedApkCount(context: Context): Int =
    ourFiles(context).count { it.name.endsWith(UpdateApkFiles.APK_SUFFIX, ignoreCase = true) } +
        runCatching { PublicDownloads.countOurApks(context) }.getOrDefault(0)

/**
 * Deletes every update file this app wrote: the private copies AND the copies it placed in the user's
 * Downloads folder. Only names this app itself produces are touched.
 */
fun clearDownloadedApks(context: Context): Boolean {
    var allDeleted = true
    ourFiles(context).forEach { file ->
        if (!file.delete()) allDeleted = false
    }
    runCatching { PublicDownloads.deleteOurApks(context, keepNames = emptySet()) }
    // The "tap to install" notification would otherwise point at a file that is gone.
    runCatching { DownloadNotificationManager.cancelNotification(context) }
    return allDeleted
}

/**
 * Drops update files older than a day. Kept for the space it frees; it is no longer load-bearing for
 * correctness — a leftover APK from another release can no longer be installed by mistake.
 */
fun autoClearOldApks(context: Context) {
    val oneDayAgo = System.currentTimeMillis() - (24 * 60 * 60 * 1000)
    ourFiles(context)
        .filter { it.lastModified() < oneDayAgo }
        .forEach { it.delete() }
}
