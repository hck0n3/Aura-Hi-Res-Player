package iad1tya.echo.music.echomusic.updater

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File

/**
 * Copies a finished update APK into the user's public **Downloads** folder so it's easy to find
 * (and re-install manually) from any file manager — not buried in the app-private
 * `Android/data/.../files/Download` directory used for the in-app install.
 *
 * On API 29+ this goes through MediaStore (no storage permission needed, survives scoped storage).
 * On older devices it falls back to the public Downloads dir. Best-effort: failures are swallowed so
 * a copy problem never breaks the actual update install.
 */
object PublicDownloads {

    private const val MIME_APK = "application/vnd.android.package-archive"

    /** Copies [source] into public Downloads as [displayName]. Returns true on success. */
    fun saveApk(context: Context, source: File, displayName: String): Boolean = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            saveViaMediaStore(context, source, displayName)
        } else {
            saveToPublicDir(source, displayName)
        }
    } catch (_: Exception) {
        false
    }

    private fun saveViaMediaStore(context: Context, source: File, displayName: String): Boolean {
        val resolver = context.contentResolver
        val collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, displayName)
            put(MediaStore.Downloads.MIME_TYPE, MIME_APK)
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val uri = resolver.insert(collection, values) ?: return false
        resolver.openOutputStream(uri)?.use { out ->
            source.inputStream().use { it.copyTo(out) }
        } ?: return false
        values.clear()
        values.put(MediaStore.Downloads.IS_PENDING, 0)
        resolver.update(uri, values, null, null)
        return true
    }

    @Suppress("DEPRECATION")
    private fun saveToPublicDir(source: File, displayName: String): Boolean {
        val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        if (!dir.exists()) dir.mkdirs()
        val dest = File(dir, displayName)
        source.inputStream().use { input ->
            dest.outputStream().use { input.copyTo(it) }
        }
        return dest.exists()
    }
}
