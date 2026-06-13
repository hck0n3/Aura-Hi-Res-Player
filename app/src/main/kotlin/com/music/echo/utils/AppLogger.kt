package iad1tya.echo.music.utils

import android.content.Context
import android.util.Log
import timber.log.Timber
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

/**
 * Persistent app logging for the in-app Logs screen and shareable diagnostics.
 *
 * Plants a Timber [Timber.Tree] that appends INFO+ entries to `filesDir/logs/app.log`,
 * rotating to a single `app.log.1` backup once the file passes [MAX_SIZE]. The crash
 * handler additionally writes the last crash to `last_crash.txt`.
 */
object AppLogger {

    private const val MAX_SIZE = 256 * 1024 // 256 KB per file, one backup kept
    private val timestampFormat = SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US)
    private val ioExecutor = Executors.newSingleThreadExecutor()

    private fun logDir(context: Context): File =
        File(context.filesDir, "logs").apply { mkdirs() }

    fun logFile(context: Context): File = File(logDir(context), "app.log")
    private fun backupFile(context: Context): File = File(logDir(context), "app.log.1")
    fun crashFile(context: Context): File = File(logDir(context), "last_crash.txt")

    /** Plant the file tree (call once at startup, in addition to the debug tree). */
    fun plant(context: Context) {
        Timber.plant(FileTree(context.applicationContext))
    }

    /** Recent log text (backup + current), newest at the bottom. Empty string if none. */
    fun readRecentLog(context: Context): String = buildString {
        val backup = backupFile(context)
        val current = logFile(context)
        runCatching { if (backup.exists()) append(backup.readText()) }
        runCatching { if (current.exists()) append(current.readText()) }
    }

    fun readLastCrash(context: Context): String =
        runCatching { crashFile(context).takeIf { it.exists() }?.readText() }.getOrNull().orEmpty()

    fun writeCrash(context: Context, text: String) {
        runCatching { crashFile(context).writeText(text) }
    }

    fun clear(context: Context) {
        ioExecutor.execute {
            runCatching {
                logFile(context).delete()
                backupFile(context).delete()
                crashFile(context).delete()
            }
        }
    }

    private fun append(context: Context, line: String) {
        ioExecutor.execute {
            runCatching {
                val file = logFile(context)
                if (file.exists() && file.length() > MAX_SIZE) {
                    val backup = backupFile(context)
                    backup.delete()
                    file.renameTo(backup)
                }
                file.appendText(line)
            }
        }
    }

    private class FileTree(private val context: Context) : Timber.Tree() {
        override fun isLoggable(tag: String?, priority: Int): Boolean = priority >= Log.INFO

        override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
            val level = when (priority) {
                Log.INFO -> "I"
                Log.WARN -> "W"
                Log.ERROR -> "E"
                Log.ASSERT -> "A"
                else -> "D"
            }
            val ts = timestampFormat.format(Date())
            val builder = StringBuilder()
                .append(ts).append(' ').append(level).append('/')
                .append(tag ?: "App").append(": ").append(message).append('\n')
            if (t != null) builder.append(Log.getStackTraceString(t)).append('\n')
            append(context, builder.toString())
        }
    }
}
