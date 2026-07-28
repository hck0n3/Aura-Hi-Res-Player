package iad1tya.echo.music.utils

import android.app.ActivityManager
import android.app.Application
import android.app.ApplicationExitInfo
import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import java.io.BufferedReader
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Mirrors Android's own record of WHY previous app processes died into a persistent, shareable log
 * (`filesDir/logs/exit_reasons.txt`, see [AppLogger.exitReasonsFile]).
 *
 * WHY THIS EXISTS: the owner reported "the app just closed while playing, no error dialog, reopened
 * fine". A process killed by the system — low memory, an ANR, a native SIGSEGV, an OEM battery
 * manager — never reaches [CrashHandler]: no Java throwable is ever thrown, so `last_crash.txt`
 * stays empty and the disappearance looks like a ghost with nothing to diagnose. The platform DOES
 * record the real cause in [ActivityManager.getHistoricalProcessExitReasons] (API 30+); this app
 * simply never read it. It does now, once per start, off the main thread.
 *
 * PRIVACY: [ApplicationExitInfo] carries no media metadata, and nothing here adds any. No song
 * titles, artists, IDs or URLs are ever written to this file — keep it that way.
 *
 * COST: one binder call returning at most [MAX_RECORDS] small records, plus a bounded head of the
 * ANR trace when there is one. Everything is wrapped in [runCatching]: a failure here must never
 * cost the user a launch, and the feature is purely diagnostic.
 */
object ExitReasonReporter {

    /** How many historical records to ask the platform for (it keeps ~16 per package anyway). */
    private const val MAX_RECORDS = 16

    /** ANR traces can be MEGABYTES; only a bounded head is worth keeping. */
    private const val ANR_TRACE_MAX_LINES = 40
    private const val ANR_TRACE_MAX_CHARS = 8 * 1024

    /**
     * Watermark of the newest record already written, so each exit is logged exactly once.
     *
     * Stored in the app's existing lightweight `app_prefs` SharedPreferences (the same file
     * `resolveAppLanguageTag` and `ReleaseRadarWorker` use) rather than DataStore: this runs on a
     * startup coroutine and a DataStore read here would queue behind the settings actor for no
     * reason. The file is already in memory by this point (attachBaseContext reads it), so the
     * lookup costs nothing.
     */
    private const val PREFS_NAME = "app_prefs"
    private const val KEY_LAST_SEEN = "exit_reasons_last_ts"

    /**
     * Reads every process-exit record newer than the stored watermark and appends it to the exit
     * log. Safe to call unconditionally: it no-ops below API 30 and never throws.
     *
     * MUST be called off the main thread (it does a binder call + SharedPreferences + file IO).
     */
    fun collect(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        runCatching { collectInternal(context.applicationContext) }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun collectInternal(context: Context) {
        // Only the default process reports. App.onCreate also runs in `:crash` (CrashActivity) and
        // `:phoenix` (ProcessPhoenix); each of those would re-read the very same platform records and
        // race the watermark, duplicating lines. getProcessName() is API 28+ and this path is API 30+.
        val processName = runCatching { Application.getProcessName() }.getOrNull()
        if (processName != null && processName != context.packageName) return

        val activityManager =
            context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager ?: return
        // pid = 0 means "any pid of this package"; the package must be our own.
        val records = runCatching {
            activityManager.getHistoricalProcessExitReasons(context.packageName, 0, MAX_RECORDS)
        }.getOrNull().orEmpty()
        if (records.isEmpty()) return

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        // A watermark in the FUTURE (bad RTC before NTP, or the user moving the clock) would make the
        // `timestamp > lastSeen` filter never match again — the feature would go permanently dark with
        // no way back. Treat it as unset.
        val storedLastSeen = prefs.getLong(KEY_LAST_SEEN, 0L)
        val lastSeen = if (storedLastSeen > System.currentTimeMillis()) 0L else storedLastSeen

        // Oldest first, so the file reads chronologically like every other log in this app.
        val fresh = records.filter { it.timestamp > lastSeen }.sortedBy { it.timestamp }
        if (fresh.isEmpty()) return

        // SimpleDateFormat is not thread-safe; this instance never leaves this call.
        val format = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
        val text = buildString {
            if (lastSeen == 0L) {
                // First run on this install/update: Android had already been recording these, so the
                // batch below is history, not something that just happened. Label it so the owner
                // does not read an old kill as a fresh one.
                append("--- exits Android had already recorded before this build ---\n")
            }
            fresh.forEach { append(formatRecord(it, format)) }
        }
        // The watermark advances only once the lines are actually on disk (callback runs on AppLogger's
        // IO executor). Otherwise a kill between enqueue and write would mark these records as seen
        // and they would never be logged — the exact silent-loss this class exists to prevent.
        // Clamp the watermark to "now" as well. Storing a FUTURE timestamp (a record written while the
        // clock was ahead) would make the guard above re-open the whole batch on every launch — the same
        // records re-imported forever, each run re-emitting the history header and pushing real entries
        // out of the size-capped file.
        val newest = minOf(fresh.last().timestamp, System.currentTimeMillis())
        AppLogger.appendExitReasons(context, text) {
            // commit(), not apply(): this callback ALREADY runs on AppLogger's IO executor, so the
            // synchronous write is free here — and apply()'s deferred flush is exactly what a
            // low-memory kill moments after startup would lose, re-importing the same records.
            prefs.edit().putLong(KEY_LAST_SEEN, newest).commit()
        }
    }

    /**
     * Forget which platform records were already imported, so the next launch re-reads everything
     * Android still keeps (~16 entries). Called when the user clears the logs: otherwise deleting
     * `exit_reasons.txt` would destroy the only copy of the very data this class exists to capture,
     * with no way to get it back.
     */
    fun resetWatermark(context: Context) {
        runCatching {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit().remove(KEY_LAST_SEEN).commit()
        }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun formatRecord(info: ApplicationExitInfo, format: SimpleDateFormat): String {
        val builder = StringBuilder()
        runCatching {
            builder
                .append(format.format(Date(info.timestamp)))
                .append(" | ").append(reasonName(info.reason))
                .append(" | proc=").append(info.processName)
                .append(" | importance=").append(importanceName(info.importance))
                .append(" | pss=").append(formatKb(info.pss))
                .append(" | rss=").append(formatKb(info.rss))
                .append(" | status=").append(info.status)
            // The platform description is a short system string (e.g. the signal, or the OOM adj
            // bucket). It never contains app content; flatten newlines so one exit stays one line.
            info.description?.takeIf { it.isNotBlank() }?.let {
                builder.append(" | desc=").append(it.replace('\n', ' ').trim())
            }
            builder.append('\n')
            if (info.reason == ApplicationExitInfo.REASON_ANR) {
                readAnrTrace(info)?.let { builder.append(it) }
            }
        }.onFailure { builder.append("<unreadable exit record>\n") }
        return builder.toString()
    }

    /**
     * A bounded head of the ANR trace. Only ANR records carry one, [ApplicationExitInfo.getTraceInputStream]
     * may still return null, and the full trace can be megabytes — so this reads line by line and STOPS
     * (rather than consuming the stream and trimming afterwards). Null on anything unexpected.
     */
    @RequiresApi(Build.VERSION_CODES.R)
    private fun readAnrTrace(info: ApplicationExitInfo): String? = runCatching {
        val stream = info.traceInputStream ?: return@runCatching null
        val builder = StringBuilder("    --- ANR trace (first $ANR_TRACE_MAX_LINES lines) ---\n")
        var lines = 0
        stream.use { raw ->
            val reader = BufferedReader(InputStreamReader(raw))
            while (lines < ANR_TRACE_MAX_LINES && builder.length < ANR_TRACE_MAX_CHARS) {
                val line = reader.readLine() ?: break
                builder.append("    ").append(line).append('\n')
                lines++
            }
        }
        if (lines == 0) null else builder.toString()
    }.getOrNull()

    /** Readable name for [ApplicationExitInfo.getReason]. */
    private fun reasonName(reason: Int): String = when (reason) {
        ApplicationExitInfo.REASON_UNKNOWN -> "UNKNOWN"
        ApplicationExitInfo.REASON_EXIT_SELF -> "EXIT_SELF"
        ApplicationExitInfo.REASON_SIGNALED -> "SIGNALED"
        ApplicationExitInfo.REASON_LOW_MEMORY -> "LOW_MEMORY"
        ApplicationExitInfo.REASON_CRASH -> "CRASH"
        ApplicationExitInfo.REASON_CRASH_NATIVE -> "CRASH_NATIVE"
        ApplicationExitInfo.REASON_ANR -> "ANR"
        ApplicationExitInfo.REASON_INITIALIZATION_FAILURE -> "INITIALIZATION_FAILURE"
        ApplicationExitInfo.REASON_PERMISSION_CHANGE -> "PERMISSION_CHANGE"
        ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE -> "EXCESSIVE_RESOURCE_USAGE"
        ApplicationExitInfo.REASON_USER_REQUESTED -> "USER_REQUESTED"
        ApplicationExitInfo.REASON_USER_STOPPED -> "USER_STOPPED"
        ApplicationExitInfo.REASON_DEPENDENCY_DIED -> "DEPENDENCY_DIED"
        ApplicationExitInfo.REASON_OTHER -> "OTHER"
        // Literals on purpose: these constants were added after API 30, and a record carrying one can
        // still reach an API-30 device's list. Values are frozen platform constants.
        14 -> "FREEZER" // ApplicationExitInfo.REASON_FREEZER (API 31)
        15 -> "PACKAGE_STATE_CHANGE" // ApplicationExitInfo.REASON_PACKAGE_STATE_CHANGE (API 33)
        16 -> "PACKAGE_UPDATED" // ApplicationExitInfo.REASON_PACKAGE_UPDATED (API 33)
        else -> "REASON_$reason"
    }

    /** Readable name for [ApplicationExitInfo.getImportance] (what the process was doing when it died). */
    private fun importanceName(importance: Int): String = when (importance) {
        ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND -> "FOREGROUND"
        ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND_SERVICE -> "FOREGROUND_SERVICE"
        ActivityManager.RunningAppProcessInfo.IMPORTANCE_VISIBLE -> "VISIBLE"
        ActivityManager.RunningAppProcessInfo.IMPORTANCE_PERCEPTIBLE -> "PERCEPTIBLE"
        ActivityManager.RunningAppProcessInfo.IMPORTANCE_SERVICE -> "SERVICE"
        ActivityManager.RunningAppProcessInfo.IMPORTANCE_TOP_SLEEPING -> "TOP_SLEEPING"
        ActivityManager.RunningAppProcessInfo.IMPORTANCE_CANT_SAVE_STATE -> "CANT_SAVE_STATE"
        ActivityManager.RunningAppProcessInfo.IMPORTANCE_CACHED -> "CACHED"
        ActivityManager.RunningAppProcessInfo.IMPORTANCE_GONE -> "GONE"
        else -> "IMPORTANCE_$importance"
    }

    /** [ApplicationExitInfo.getPss]/[ApplicationExitInfo.getRss] are in kB; 0 means "not recorded". */
    private fun formatKb(kb: Long): String =
        if (kb <= 0L) "n/a" else String.format(Locale.US, "%.1f MB", kb / 1024.0)
}
