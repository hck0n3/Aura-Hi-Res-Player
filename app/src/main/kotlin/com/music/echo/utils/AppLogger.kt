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
    private const val MAX_EXIT_REASONS_SIZE = 128 * 1024 // 128 KB, oldest entries dropped
    private val timestampFormat = SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US)
    private val ioExecutor = Executors.newSingleThreadExecutor()

    private fun logDir(context: Context): File =
        File(context.filesDir, "logs").apply { mkdirs() }

    fun logFile(context: Context): File = File(logDir(context), "app.log")
    private fun backupFile(context: Context): File = File(logDir(context), "app.log.1")
    fun crashFile(context: Context): File = File(logDir(context), "last_crash.txt")

    /**
     * Android's own record of why previous processes died (low memory / ANR / native crash / …),
     * written by [ExitReasonReporter]. Kept in its OWN file, not in `app.log`: a system kill leaves
     * no Java throwable, so this is the only trace of it, and it must not be rotated away by a
     * chatty playback session.
     */
    fun exitReasonsFile(context: Context): File = File(logDir(context), "exit_reasons.txt")

    /** Plant the file tree (call once at startup, in addition to the debug tree). */
    fun plant(context: Context) {
        Timber.plant(FileTree(context.applicationContext))
    }

    /**
     * Recent log text (backup + current), newest at the bottom. Empty string if none.
     *
     * Re-scrubbed on the way OUT as well as on the way in. Writes are redacted from this build on, but
     * a customer who updates still carries an `app.log` written by an older build that predates the
     * chokepoint — and that file is exactly what they are about to send. Reading is already off the
     * main thread at every call site.
     */
    fun readRecentLog(context: Context): String = LogRedaction.redact(
        buildString {
            val backup = backupFile(context)
            val current = logFile(context)
            runCatching { if (backup.exists()) append(backup.readText()) }
            runCatching { if (current.exists()) append(current.readText()) }
        }
    )

    fun readLastCrash(context: Context): String = LogRedaction.redact(
        runCatching { crashFile(context).takeIf { it.exists() }?.readText() }.getOrNull().orEmpty()
    )

    fun writeCrash(context: Context, text: String) {
        // Synchronous on purpose — the caller is the uncaught handler and the process is about to be
        // killed, so an executor hand-off would lose the report. Redacted here rather than at the call
        // site so no future crash-report field can leak by being added upstream.
        runCatching { crashFile(context).writeText(LogRedaction.redact(text)) }
    }

    /** System-exit records, oldest at the top. Empty string if none. */
    fun readExitReasons(context: Context): String = LogRedaction.redact(
        runCatching { exitReasonsFile(context).takeIf { it.exists() }?.readText() }.getOrNull().orEmpty()
    )

    /**
     * Full shareable diagnostics: header + last crash + app log + system exits + recent playback
     * log (in-memory). Used by feedback email, crash share, and Logs ▸ Share so every report
     * carries every log type the owner needs to diagnose "si o no" without asking the user to
     * open three tabs.
     *
     * Still redacted via [LogRedaction] on every section. Never includes song titles/artists or
     * account cookies.
     */
    fun buildFullShareBundle(
        context: Context,
        reason: String = "shared diagnostics",
        prepend: String? = null,
    ): String = buildString {
        if (!prepend.isNullOrBlank()) {
            append(prepend.trimEnd())
            appendLine()
            appendLine()
        }
        append(DiagnosticHeader.build(context, reason))
        appendLine()

        val crash = readLastCrash(context)
        if (crash.isNotBlank()) {
            appendLine("--- LAST CRASH ---")
            appendLine(crash)
            appendLine()
        }

        val appLog = readRecentLog(context)
        if (appLog.isNotBlank()) {
            appendLine("--- APP LOG (recent) ---")
            appendLine(appLog)
            appendLine()
        }

        val exits = readExitReasons(context)
        if (exits.isNotBlank()) {
            appendLine("--- SYSTEM EXITS ---")
            appendLine(exits)
            appendLine()
        }

        // 2026-08-19, tenth postmortem: a single failed song's own resolve trace (11 clients × 2-4
        // lines each: "Trying fallback", "Player response OK"/"Client failed", "No stream URL"/"HEAD
        // validation failed", plus RESOLVE_TIMING) already runs 25-35 lines. With the old limit of 80,
        // a SECOND failing song in the same session pushed the first song's per-client detail out of
        // the export entirely — every feedback log the owner sent only ever showed the tail end of the
        // LAST song tried, never the full cascade, no matter how many songs actually failed. Raised to
        // stay under MAX_LOG_ENTRIES (500) with margin, so a full session's worth of resolve traces
        // survives into the exported diagnostic instead of only the most recent fragment.
        val playback = PlaybackLogManager.formatRecent(400)
        if (playback.isNotBlank()) {
            appendLine("--- PLAYBACK LOG (recent, in-memory) ---")
            append(playback)
        }
    }

    /**
     * Writes the [DiagnosticHeader] block into `app.log` once per launch.
     *
     * Two jobs. It makes the log self-describing — build, flavor, device, battery saver and the
     * behaviour-changing settings, so the owner is not guessing at line one — and it marks where each
     * process START is, which is itself the answer to "se cerró sola": two headers with no shutdown
     * between them means the process died and came back.
     *
     * Called after settings load (not from `onCreate`) so the settings half is populated rather than
     * "not read yet".
     */
    fun logSessionHeader(context: Context) {
        append(context, "\n" + DiagnosticHeader.build(context, "session start") )
    }

    /**
     * Appends already-formatted system-exit lines. Grows by a few lines per app start at most (and
     * usually not at all), so instead of the app.log rotate-to-backup scheme it simply drops the
     * oldest half once past [MAX_EXIT_REASONS_SIZE] — the recent kills are the ones being diagnosed.
     */
    fun appendExitReasons(context: Context, text: String, onWritten: () -> Unit = {}) {
        if (text.isEmpty()) return
        ioExecutor.execute {
            runCatching {
                val file = exitReasonsFile(context)
                if (file.exists() && file.length() > MAX_EXIT_REASONS_SIZE) {
                    val kept = file.readText().takeLast(MAX_EXIT_REASONS_SIZE / 2).substringAfter('\n')
                    file.writeText(kept)
                }
                // An ANR trace is the one exit record that carries app-authored strings (thread names,
                // frames), so it goes through the same scrub as everything else.
                file.appendText(LogRedaction.redact(text))
            }.onSuccess {
                // Runs on the executor thread, AFTER the bytes are on disk. The caller advances its
                // "already imported" watermark here, so a process death between the enqueue and the
                // write can never lose records by marking them seen without having stored them.
                runCatching { onWritten() }
            }
        }
    }

    fun clear(context: Context) {
        ioExecutor.execute {
            runCatching {
                logFile(context).delete()
                backupFile(context).delete()
                crashFile(context).delete()
                exitReasonsFile(context).delete()
                // Reset the import watermark TOO. Android still holds ~16 exit records; without this
                // reset, deleting the file destroyed the only copy of the data this feature exists to
                // capture, permanently and with no confirmation. Re-importing on the next launch is
                // harmless — the batch is labelled as pre-existing history.
                ExitReasonReporter.resetWatermark(context)
            }
        }
    }

    /**
     * THE PRIVACY CHOKEPOINT. Every byte that reaches `app.log` passes through here, so redaction is
     * applied once, on the IO executor (never on the caller's thread), and cannot be forgotten by a
     * future call site the way [iad1tya.echo.music.utils.potoken.PoTokenWebView] once forgot it.
     */
    private fun append(context: Context, line: String) {
        ioExecutor.execute {
            runCatching {
                val redacted = LogRedaction.redact(line)
                val file = logFile(context)
                if (file.exists() && file.length() > MAX_SIZE) {
                    val backup = backupFile(context)
                    backup.delete()
                    file.renameTo(backup)
                }
                file.appendText(redacted)
            }
        }
    }

    /** Full-stack-trace emission state for one distinct fault. */
    private class TraceState(var lastFullMs: Long, var repeats: Int)

    /**
     * Faults whose FULL stack trace was recently written. Bounded LRU; eviction only ever causes one
     * extra full trace, never a lost one.
     */
    private val traceSeen = object : LinkedHashMap<String, TraceState>(32, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, TraceState>?) = size > 32
    }

    /** One full trace per distinct fault per minute; repeats collapse to a counted one-liner. */
    private const val TRACE_REPEAT_WINDOW_MS = 60_000L

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
            // COLLAPSE REPEATED STACK TRACES. A full trace is 15-40 lines (~2 KB), and the stream
            // resolver logs one per failing client inside a fallback loop of 11 clients that the whole
            // resolve retries ~3 times — up to ~130 KB for ONE unplayable song, against a 256 KB cap.
            // A single failing song could therefore rotate the entire log away, destroying every
            // RESOLVE_TIMING / CROSSFADE_TRACE / NO_REPEAT line in it at exactly the moment the user
            // decides to file a report: the traces built for diagnosis were being erased by the failure
            // they exist to diagnose. Fixed HERE rather than at the noisy call sites so it also covers
            // the ones this agent must not edit (MusicService logs the whole media3 cause chain twice
            // per player error, and its retry ladder re-enters that path every few seconds).
            //
            // The FIRST occurrence of each distinct fault still gets its complete trace — nothing is
            // lost, the repeats are counted, and the count is itself the useful signal ("this failed
            // 47 times" vs "once").
            if (t != null) {
                val signature = runCatching { exceptionSignature(t) }.getOrDefault(t.javaClass.name)
                val now = android.os.SystemClock.elapsedRealtime()
                var writeFull = false
                var repeats = 0
                synchronized(traceSeen) {
                    val state = traceSeen[signature]
                    if (state == null || now - state.lastFullMs > TRACE_REPEAT_WINDOW_MS) {
                        repeats = state?.repeats ?: 0
                        traceSeen[signature] = TraceState(now, 0)
                        writeFull = true
                    } else {
                        state.repeats++
                        repeats = state.repeats
                    }
                }
                if (writeFull) {
                    if (repeats > 0) {
                        builder.append("    (identical trace suppressed ").append(repeats)
                            .append(" times since the last one)\n")
                    }
                    builder.append(Log.getStackTraceString(t)).append('\n')
                } else {
                    // One line instead of ~2 KB. Keeps the exception identity so the entry is still
                    // attributable, and points at the full trace already in the file above.
                    builder.append("    ").append(t.toString().take(200))
                        .append("  [full trace already logged; repeat #").append(repeats).append("]\n")
                }
            }
            append(context, builder.toString())
        }
    }
}
