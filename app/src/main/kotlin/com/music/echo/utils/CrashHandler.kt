

package iad1tya.echo.music.utils

import android.content.Context
import android.content.Intent
import android.os.Build
import iad1tya.echo.music.BuildConfig
import iad1tya.echo.music.ui.screens.CrashActivity
import timber.log.Timber
import java.io.PrintWriter
import java.io.StringWriter
import kotlin.system.exitProcess

class CrashHandler private constructor(
    private val applicationContext: Context
) : Thread.UncaughtExceptionHandler {

    private val defaultHandler: Thread.UncaughtExceptionHandler? =
        Thread.getDefaultUncaughtExceptionHandler()

    override fun uncaughtException(thread: Thread, throwable: Throwable) {
        // Backstop for the media3 ForegroundServiceStartNotAllowedException (e.g. thrown off the main
        // looper, which MainThreadCrashGuard can't reach). It's non-fatal for a media app — the
        // notification just couldn't go foreground — so report and survive instead of killing the process.
        // Swallow on TYPE, exactly as before — narrowing this to the stack whitelist would turn an
        // exception that used to be survived into a fresh crash, and the FGS family reaching here at all
        // means it escaped the guard (e.g. thrown off the main looper, or rethrown with a stack that no
        // longer shows the framework frames). Keeping the proven behaviour is the conservative choice.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            throwable is android.app.ForegroundServiceStartNotAllowedException) {
            reportException(throwable)
            Timber.w(throwable, "Swallowed FGS-start-not-allowed in uncaught handler")
            // But only KEEP THE MAIN THREAD RUNNING for the framework paths we have evidence for.
            // surviveOnMainThread re-enters Looper.loop(), and from then on this handler is the main
            // looper's whole exception policy — so entering it on an unrecognised stack would silently
            // swallow our OWN services' FGS bugs forever. On any other stack we return as before: the
            // exception stays swallowed and the process behaves exactly like the previous release.
            if (isSurvivable(throwable)) surviveOnMainThread(thread)
            return
        }
        // During an intentional RESTORE restart, restore() closes the shared Room DB, then copies song.db
        // and restarts. Other coroutines still collecting Room Flows (the player, screen ViewModels) can hit
        // the now-closed pool in that window → SQLite code 21 "connection is closed". That is BENIGN — the
        // process is about to exitProcess and reopen the DB. Swallow it ONLY while isRestoring (so it can
        // NEVER mask a real DB error in normal operation) and let restore's own restart finish cleanly.
        if (isRestoring && throwable.isConnectionClosed()) {
            reportException(throwable)
            Timber.w(throwable, "Swallowed benign 'connection is closed' during restore restart")
            surviveOnMainThread(thread)
            return
        }
        // OOM-SAFE PROLOGUE: write a minimal header FIRST. buildCrashLog allocates (StringWriter, the
        // whole stack trace, 25 log lines), so an OutOfMemoryError could — and on a silent close very
        // likely did — blow up INSIDE the handler before anything was persisted, leaving zero trace.
        // This tiny write needs almost no memory and guarantees the next launch has something to show.
        runCatching {
            // Self-contained: writeCrash REPLACES the file, and if buildCrashLog succeeds it overwrites
            // this with the full report a moment later. So this text must stand alone — in the only
            // scenario where it survives (an OOM while building the full report) it is all we get.
            AppLogger.writeCrash(
                applicationContext,
                "PRELIMINARY REPORT — the full one could not be built (likely out of memory)\n" +
                    "App: ${iad1tya.echo.music.BuildConfig.VERSION_NAME} (${iad1tya.echo.music.BuildConfig.VERSION_CODE})\n" +
                    "Device: ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}, Android ${android.os.Build.VERSION.RELEASE}\n" +
                    "Thread: ${thread.name}\n" +
                    "Exception: $throwable\n"
            )
        }
        try {
            val crashLog = buildCrashLog(throwable, thread)
            Timber.e(throwable, "App crashed")
            // Send the fatal to Crashlytics (GMS flavor; no-op on FOSS) before we kill the process,
            // so uncaught fatals are not invisible in telemetry. Does not change crash-killing behavior.
            CrashReporter.setKey("crash_source", "uncaught_handler")
            CrashReporter.setKey("crash_thread", thread.name)
            CrashReporter.record(throwable)
            AppLogger.writeCrash(applicationContext, crashLog)
            
            
            val intent = Intent(applicationContext, CrashActivity::class.java).apply {
                putExtra(EXTRA_CRASH_LOG, crashLog)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            }
            applicationContext.startActivity(intent)
            
            
            android.os.Process.killProcess(android.os.Process.myPid())
            exitProcess(1)
        } catch (t: Throwable) {
            // Throwable, not Exception: an OutOfMemoryError raised while BUILDING the report is exactly
            // the case that used to escape here, killing the process with no dialog and no file.
            runCatching { Timber.e(t, "Error handling crash") }
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }

    private fun buildCrashLog(throwable: Throwable, thread: Thread? = null): String {
        val stackTrace = StringWriter().apply {
            throwable.printStackTrace(PrintWriter(this))
        }.toString()

        val appName = runCatching {
            applicationContext.getString(iad1tya.echo.music.R.string.app_name)
        }.getOrDefault("Aura Hi-Res Player")
        return buildString {
            appendLine("$appName Crash Report")
            appendLine("=".repeat(50))
            appendLine()
            appendLine("Manufacturer: ${Build.MANUFACTURER}")
            appendLine("Device: ${Build.MODEL}")
            appendLine("Android version: ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
            appendLine("App version: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
            appendLine("Thread: ${thread?.name ?: Thread.currentThread().name}")
            appendLine("Uptime: ${android.os.SystemClock.elapsedRealtime() / 1000}s since boot")
            // Explicit exception line incl. MESSAGE and each cause's message — some report viewers trim
            // the stacktrace header, and a bare exception class alone is nearly undiagnosable.
            appendLine("Exception: $throwable")
            var cause: Throwable? = throwable.cause
            var depth = 0
            while (cause != null && depth < 8) {
                appendLine("Caused by: $cause")
                cause = cause.cause
                depth++
            }
            appendLine()
            appendLine("=".repeat(50))
            appendLine("Stacktrace:")
            appendLine("=".repeat(50))
            appendLine()
            append(stackTrace)
            // WHAT WAS HAPPENING: the in-memory playback log carries the per-transition and per-resolve
            // verdicts (CROSSFADE_TRACE / RESOLVE_TIMING) right up to the crash — the difference between
            // an unreadable obfuscated frame and an attributable failure. Video ids + timings only, never
            // titles/artists (shareable-log privacy rule).
            runCatching {
                val recent = PlaybackLogManager.logs.value.takeLast(25)
                if (recent.isNotEmpty()) {
                    appendLine()
                    appendLine("=".repeat(50))
                    appendLine("Recent playback log (last ${recent.size}):")
                    appendLine("=".repeat(50))
                    recent.forEach {
                        appendLine("${it.timestamp} [${it.level}] ${it.message} ${it.details.orEmpty()}")
                    }
                }
            }
        }
    }

    /**
     * Keeps the MAIN thread alive after a swallowed exception, and returns only when [thread] is not
     * the main thread (in which case the caller's plain `return` is already correct).
     *
     * WHY THIS IS NOT OPTIONAL: swallowing here means "don't kill the process", but by the time an
     * uncaught handler runs on the main thread, `Looper.loop()` has ALREADY unwound. Returning
     * normally ends the main thread's Runnable — the UI thread dies, no more messages are dispatched,
     * and the app simply disappears with no CrashActivity and no crash file. That is exactly what a
     * user reports as "it closed by itself for no reason". Re-entering the loop restores message
     * dispatch, which is the whole point of deciding the exception was survivable.
     *
     * [MainThreadCrashGuard] already does this for the exceptions it recognizes; this is the backstop
     * for the ones that reach the uncaught handler instead (e.g. a GMS Cast FGS throw on a stack the
     * guard's whitelist misses). Anything NOT survivable that surfaces from the re-entered loop is
     * handed back to [uncaughtException], which takes the normal report-and-die path — so a real bug
     * still crashes normally and is still reported.
     */
    private fun surviveOnMainThread(thread: Thread) {
        val mainThread = android.os.Looper.getMainLooper().thread
        if (thread !== mainThread || Thread.currentThread() !== mainThread) return
        var survived = 0
        while (true) {
            try {
                android.os.Looper.loop()
                // loop() returned without throwing: the looper was quit deliberately. Nothing left to
                // keep alive — stop spinning.
                return
            } catch (t: Throwable) {
                val survivable = isSurvivable(t) || (isRestoring && t.isConnectionClosed())
                // A survivable exception that keeps re-firing is no longer survivable: it means the
                // state that produces it is not recovering, and looping over it burns CPU and battery
                // (each turn does a Crashlytics record) for as long as the process lives. Past the cap
                // we take the normal fatal path, which at least leaves a crash report behind.
                if (!survivable || survived >= MAX_SURVIVED_EXCEPTIONS) {
                    if (survivable) {
                        Timber.e(t, "Survivable main-thread exception repeated $survived times; crashing for real")
                    }
                    // Real crash on the re-entered loop: full report + CrashActivity + kill.
                    uncaughtException(thread, t)
                    return
                }
                survived++
                runCatching { reportException(t) }
                Timber.w(t, "Swallowed another survivable main-thread exception; re-entering looper")
            }
        }
    }

    /**
     * The FGS-start family, restricted to the framework paths [MainThreadCrashGuard] whitelists.
     *
     * Deliberately the SAME predicate the guard uses, not a looser type-only test: once
     * [surviveOnMainThread] re-enters the looper, the guard's own frame is gone for the rest of the
     * process, so this becomes the main looper's only exception policy. A type-only test there would
     * silently swallow an FGS exception thrown by OUR OWN services (AudioExportService,
     * RecognitionForegroundService) — a genuine bug — forever, with no report and no CrashActivity.
     */
    private fun isSurvivable(t: Throwable): Boolean =
        with(MainThreadCrashGuard) { t.isSwallowableForegroundServiceCrash() }

    /** True only for a benign SQLite "connection is closed" (code 21) anywhere in the cause chain. */
    private fun Throwable.isConnectionClosed(): Boolean {
        var t: Throwable? = this
        var depth = 0
        while (t != null && depth < 12) {
            val m = t.message?.lowercase().orEmpty()
            if (m.contains("connection is closed") || m.contains("error code: 21")) return true
            t = t.cause
            depth++
        }
        return false
    }

    companion object {
        const val EXTRA_CRASH_LOG = "crash_log"

        /** Survivable exceptions tolerated on a re-entered main looper before we crash for real. */
        private const val MAX_SURVIVED_EXCEPTIONS = 50

        // Set true (never reset) by BackupRestoreViewModel just before it closes the shared Room DB for a
        // restore restart, so the uncaught handler treats the ensuing "connection is closed" as benign.
        @Volatile
        @JvmStatic
        var isRestoring: Boolean = false

        fun install(context: Context) {
            val handler = CrashHandler(context.applicationContext)
            Thread.setDefaultUncaughtExceptionHandler(handler)
            Timber.d("CrashHandler installed")
        }
    }
}
