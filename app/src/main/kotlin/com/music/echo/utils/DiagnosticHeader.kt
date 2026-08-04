package iad1tya.echo.music.utils

import android.content.Context
import android.os.Build
import android.os.PowerManager
import android.os.StatFs
import iad1tya.echo.music.BuildConfig
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The block every shared log and crash report opens with.
 *
 * WHY: a customer writes "se cerró sola" and sends a log. Without this block the owner cannot answer
 * even the first question — which build is this, is it the foss or gms flavor, is the phone in battery
 * saver, is crossfade on — so every diagnosis started with a round-trip of questions the app already
 * knew the answers to. Line one of a report should already rule out half the hypotheses.
 *
 * WHAT IS DELIBERATELY ABSENT: which account is logged in (only yes/no), the cookie, the licence key,
 * any song title or artist. Those never appear here and must never be added — see [LogRedaction] for
 * the backstop that scrubs them if some other code path tries.
 *
 * COST: built at most three times per session (once at startup, once when the user taps share, once on
 * a crash) and never on a playback path. The only syscalls are one [StatFs] stat and two cheap system
 * service lookups. The settings half costs nothing at all: it reads a [snapshot] that App already had
 * in hand, rather than doing its own DataStore read — a DataStore read from the crash handler would
 * block on the settings actor at exactly the worst moment.
 */
object DiagnosticHeader {

    /** Captured when the class first loads, which is early in `App.onCreate`. */
    private val processStartElapsedMs = android.os.SystemClock.elapsedRealtime()

    /**
     * The behaviour-changing settings, as of the last time App observed them.
     *
     * Deliberately a plain immutable snapshot behind `@Volatile` rather than a Flow: the crash handler
     * reads this while the process is dying, so it must be a non-blocking, non-suspending field read
     * that cannot touch disk, allocate a collector, or deadlock on a coroutine dispatcher.
     */
    @Volatile
    private var snapshot: Settings? = null

    data class Settings(
        val crossfadeEnabled: Boolean,
        val crossfadeSeconds: Float,
        val enhancedShuffle: Boolean,
        val safeVolume: Boolean,
        val audioOffload: Boolean,
        /** Whether ANY account cookie is present. NEVER which account — that is the customer's identity. */
        val loggedIn: Boolean,
    )

    /** Called by App once at startup and again whenever one of these settings changes. */
    fun updateSettings(settings: Settings) {
        snapshot = settings
    }

    private val timestampFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)

    /**
     * Builds the header. [reason] labels which surface asked for it ("app log", "crash", "shared"), so
     * the owner can tell a header written at launch from one written when the user hit share — the gap
     * between the two is itself information (settings changed mid-session).
     *
     * Never throws: every field is independently [runCatching]-guarded and degrades to "?" so one
     * unavailable system service cannot cost the owner the whole header.
     */
    fun build(context: Context, reason: String): String = buildString {
        appendLine("=".repeat(64))
        appendLine("AURA DIAGNOSTIC HEADER — $reason @ ${field { timestampFormat.format(Date()) }}")
        appendLine("=".repeat(64))
        appendLine(
            "App      : ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}) " +
                "${BuildConfig.FLAVOR_variant}/${BuildConfig.BUILD_TYPE} " +
                "abi=${BuildConfig.FLAVOR_abi} id=${BuildConfig.APPLICATION_ID}" +
                if (BuildConfig.IS_NIGHTLY) " nightly" else ""
        )
        appendLine(
            "Device   : ${Build.MANUFACTURER} ${Build.MODEL} (${Build.DEVICE}) " +
                "Android ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT}) " +
                "abi=${field { Build.SUPPORTED_ABIS.firstOrNull() ?: "?" }}"
        )
        appendLine(
            "Locale   : app=${field { resolveAppLanguageTag(context) }} " +
                "system=${field { Locale.getDefault().toLanguageTag() }} " +
                "region=${field { systemRegionCode() }}"
        )
        appendLine("Storage  : ${field { storageLine(context) }}")
        appendLine("Memory   : ${field { memoryLine(context) }}")
        appendLine("Power    : ${field { powerLine(context) }}")
        appendLine("Settings : ${settingsLine()}")
        appendLine(
            "Uptime   : ${(android.os.SystemClock.elapsedRealtime() - processStartElapsedMs) / 1000}s " +
                "this process, ${android.os.SystemClock.elapsedRealtime() / 1000}s since boot"
        )
        appendLine("=".repeat(64))
    }

    /** One field failing (a missing system service on an odd ROM) must not cost the whole header. */
    private inline fun field(block: () -> String): String = runCatching(block).getOrDefault("?")

    private fun storageLine(context: Context): String {
        val stat = StatFs(context.filesDir.absolutePath)
        val freeMb = stat.availableBytes / (1024 * 1024)
        val totalMb = stat.totalBytes / (1024 * 1024)
        // Free space is a first-class suspect: a full disk makes the media3 cache fail to write, which
        // the user experiences as "no reproduce", and makes the log itself fail to persist.
        val warn = if (freeMb < 200) "  <-- LOW, cache writes and logging will fail" else ""
        return "$freeMb MB free of $totalMb MB$warn"
    }

    private fun memoryLine(context: Context): String {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
        val runtime = Runtime.getRuntime()
        return "heapMax=${runtime.maxMemory() / (1024 * 1024)} MB " +
            "used=${(runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024)} MB " +
            "class=${am.memoryClass}/${am.largeMemoryClass} MB lowRam=${am.isLowRamDevice}"
    }

    private fun powerLine(context: Context): String {
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val saver = pm.isPowerSaveMode
        // Battery-optimisation exemption is THE recurring cause of "the app closed by itself" on the
        // OEM ROMs this app ships to: unexempt + battery saver is a background kill waiting to happen,
        // and it pairs with the LOW_MEMORY/FREEZER records in exit_reasons.txt to make the kill provable
        // rather than guessed.
        val exempt = pm.isIgnoringBatteryOptimizations(context.packageName)
        val warn = if (saver || !exempt) "  <-- can kill background playback" else ""
        return "batterySaver=${saver.onOff()} batteryOptimisationExempt=${exempt.yesNo()}$warn"
    }

    private fun settingsLine(): String {
        val s = snapshot ?: return "not read yet (crashed before settings loaded)"
        return "crossfade=${s.crossfadeEnabled.onOff()}" +
            (if (s.crossfadeEnabled) "/${s.crossfadeSeconds}s" else "") +
            " enhancedShuffle=${s.enhancedShuffle.onOff()}" +
            " safeVolume=${s.safeVolume.onOff()}" +
            " audioOffload=${s.audioOffload.onOff()}" +
            " account=${if (s.loggedIn) "logged-in" else "logged-out"}"
    }

    private fun Boolean.onOff(): String = if (this) "ON" else "OFF"
    private fun Boolean.yesNo(): String = if (this) "yes" else "no"
}
