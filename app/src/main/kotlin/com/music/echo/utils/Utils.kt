

package iad1tya.echo.music.utils

import android.content.Context
import android.content.res.Configuration
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape
import androidx.compose.ui.graphics.Shape
import java.util.Locale
import iad1tya.echo.music.constants.SYSTEM_DEFAULT

/** Per-signature rate-limit state. [suppressed] is reported when the signature is next allowed through. */
private class ReportState(var lastLoggedMs: Long, var suppressed: Int)

/**
 * Recently-logged signatures. CrashHandler's survive-on-main loop can call reportException up to 50 times
 * for the SAME fault, and a repeating error is precisely the case that would otherwise rotate the real
 * context out of a 256 KB log.
 *
 * ACCESS-ORDER LRU, capped at 64. Eviction is fail-OPEN: an evicted signature simply logs again on its
 * next occurrence, so the cache can only ever cause MORE logging, never the loss of a different error.
 */
private val reportSeen = object : LinkedHashMap<String, ReportState>(64, 0.75f, true) {
    override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ReportState>?) = size > 64
}

/**
 * Values that differ between two occurrences of the SAME fault: URLs, hex blobs, and any number
 * (a byte offset, a playback position, a timestamp, a retry counter).
 *
 * URLs are listed FIRST because they contain digits — regex alternation is leftmost-first, so a URL is
 * consumed whole rather than being shredded into fragments by the digit rule.
 */
private val VARIABLE_NOISE = Regex("""https?://\S+|\b0x[0-9a-fA-F]+\b|\b[0-9a-fA-F]{8,}\b|\d+""")

/**
 * Collapses the parts of a message that vary between occurrences, so a fault that repeats can be
 * RECOGNISED as a repeat.
 *
 * Keying the rate limiter on the raw text defeated it completely: `"read failed at offset 81920"` and
 * `"read failed at offset 98304"` are the same fault, but they minted a new key every time — and an
 * exception whose message carries a moving offset or position is exactly the kind that fires in a
 * loop. The limiter looked correct and did nothing for the only case it existed to handle.
 */
internal fun normalizeLogMessage(message: String): String = VARIABLE_NOISE.replace(message, "#")

/**
 * A stable identity for a throwable: exception class + top frame + normalised message.
 *
 * The class and frame are kept VERBATIM (digits and all), so two different faults can never collapse
 * into one — a different throw site means a different line number means a different signature. Only
 * the free-text message is normalised. That is what keeps "collapse a repeat" from turning into
 * "lose a different error".
 */
internal fun exceptionSignature(throwable: Throwable): String {
    val frame = throwable.stackTrace.firstOrNull()
    val classes = generateSequence(throwable) { it.cause }.take(4).joinToString("|") { it.javaClass.name }
    return "$classes@${frame?.className}.${frame?.methodName}:${frame?.lineNumber}" +
        "|${normalizeLogMessage(throwable.message.orEmpty().take(200))}"
}

fun reportException(throwable: Throwable) {
    // Local logging (unchanged) — keeps stack traces in logcat for local debugging.
    throwable.printStackTrace()
    // Telemetry: records to Firebase Crashlytics on the GMS flavor; no-op on FOSS (flavor source set).
    CrashReporter.record(throwable)
    // …and into the log the USER can actually send. printStackTrace() writes to System.err, which never
    // passes through Timber — and AppLogger's persistence is a Timber tree. So every one of the ~157
    // reportException call sites, including the main playback-error report, left ZERO evidence in
    // filesDir/logs/app.log: a customer reporting "no reproduce" sent a log with none of the errors in it.
    // That is the single biggest reason a remote report forced guesswork.
    //
    // Compact on purpose (class + message + cause chain + top frame, not a full trace): app.log is capped,
    // so ~20x more incidents survive the same budget, and the full trace is still captured by CrashHandler
    // for anything fatal. Runs LAST and inside runCatching so it can never cost the Crashlytics record —
    // this also executes on the uncaught path, which is hardened against an OOM in the handler itself.
    // Release builds keep R8 obfuscation, so the frame name is mangled: deobfuscate with the r8-mapping
    // artifact of the exact tag, the workflow docs/CRASH_REPORTS.md already uses.
    runCatching {
        val chain = generateSequence(throwable) { it.cause }
            .take(4)
            // REDACT BEFORE TRUNCATING. take(200) on the raw message could slice through the middle of
            // a `?pot=` value and leave its first characters in the clear; redacting first means the
            // truncation only ever cuts already-masked text. AppLogger's write chokepoint scrubs this
            // again on the way to disk — this pass is what protects the Crashlytics/logcat copies too.
            .joinToString(" <- ") {
                "${it.javaClass.simpleName}: ${LogRedaction.redact(it.message.orEmpty()).take(200)}"
            }
        val frame = throwable.stackTrace.firstOrNull()
        val line = "$chain @ ${frame?.className}.${frame?.methodName}:${frame?.lineNumber}"
        // Key on the NORMALISED signature, not on `line`. `line` embeds the raw message, so a fault
        // whose message carries a moving byte offset or position minted a fresh key on every occurrence
        // and slipped past the limiter entirely — the one case the limiter exists for.
        val key = exceptionSignature(throwable)

        // Rate limit on LAST-LOGGED, not last-seen. Refreshing the timestamp on every occurrence (as
        // this did originally) starves any error that recurs faster than the window: a fault firing
        // every 30s would be logged exactly ONCE and then stay invisible for the life of the process,
        // so a log showing one line at 10:00 looked like a one-off when it was in fact still happening
        // an hour later. The suppressed count is carried into the next emission, which turns the
        // dedup from a loss of information into a measurement: "+142 more in 60s" is the difference
        // between an incident and a storm.
        val now = android.os.SystemClock.elapsedRealtime()
        var suffix: String? = null
        synchronized(reportSeen) {
            val state = reportSeen[key]
            when {
                state == null -> {
                    reportSeen[key] = ReportState(now, 0)
                    suffix = ""
                }
                now - state.lastLoggedMs > 60_000L -> {
                    val windowSec = (now - state.lastLoggedMs) / 1000
                    suffix = if (state.suppressed > 0) {
                        " [+${state.suppressed} identical in the last ${windowSec}s]"
                    } else {
                        ""
                    }
                    state.lastLoggedMs = now
                    state.suppressed = 0
                }
                else -> state.suppressed++
            }
        }
        suffix?.let { timber.log.Timber.tag("REPORT").e(line + it) }
    }
}

@Suppress("DEPRECATION")
fun setAppLocale(context: Context, locale: Locale) {
    val config = Configuration(context.resources.configuration)
    config.setLocale(locale)
    context.resources.updateConfiguration(config, context.resources.displayMetrics)
}

/**
 * The device's real region (country) code, lowercased — read from the SYSTEM resources so it is NOT
 * affected by the app forcing its UI language. Forcing "es" blanks out Locale.getDefault().country,
 * which broke region feeds (e.g. the Apple Music charts URL became invalid). Falls back to "us".
 */
fun systemRegionCode(): String =
    runCatching {
        android.content.res.Resources.getSystem().configuration.locales[0].country
    }.getOrNull()?.lowercase(Locale.ROOT)?.takeIf { it.isNotBlank() } ?: "us"

/**
 * Resolves the in-app language from a lightweight SharedPreferences mirror. Safe to read in
 * attachBaseContext — DataStore must NEVER be read there: its blocking read at cold start
 * crashes/ANRs the launch (notably on some OEM ROMs). When no in-app language is set it follows
 * the device/system locale (not a forced "es"), which also keeps YouTube search gl/hl on the real
 * device locale. The mirror is kept in sync from App's settings observer whenever it changes.
 */
fun resolveAppLanguageTag(context: Context): String =
    context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        .getString("app_language", null)
        ?.takeUnless { it == SYSTEM_DEFAULT }
        ?: Locale.getDefault().toLanguageTag()

/**
 * Wraps [base] with a configuration forced to the resolved app language so every component
 * (Application, Activity, Service) resolves resources in the selected language on all API levels.
 * Call from each component's attachBaseContext. Never throws — falls back to [base] on any error
 * so a locale issue can never prevent the app from launching.
 */
fun localeAwareContext(base: Context): Context = try {
    val locale = Locale.forLanguageTag(resolveAppLanguageTag(base))
    Locale.setDefault(locale)
    // Override ONLY the locale (+ TV density). Do NOT copy base.resources.configuration wholesale:
    // copying it PINS uiMode (night/day), fontScale, orientation, etc. into this wrapped context, so a
    // LIVE system light↔dark switch was ignored — the app kept the uiMode captured at launch (this is
    // why "tema automático" didn't follow the phone). MainActivity has configChanges=uiMode so it isn't
    // recreated on the switch; with the full-config copy the new night bit never reached Compose's
    // isSystemInDarkTheme(). An EMPTY Configuration overrides only the fields we set; uiMode and the rest
    // keep following the system live.
    val config = Configuration()
    config.setLocale(locale)
    // Android TV: the phone UI looks zoomed/giant on a ~1080p TV panel. Shrink the effective density so
    // more, smaller content fits. Only for TV; leaving densityDpi at 0 elsewhere inherits the system value.
    val uiMode = base.getSystemService(Context.UI_MODE_SERVICE) as? android.app.UiModeManager
    if (uiMode?.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION) {
        val baseDensity = base.resources.configuration.densityDpi
        if (baseDensity > 0) config.densityDpi = (baseDensity * 0.7f).toInt().coerceAtLeast(120)
    }
    base.createConfigurationContext(config)
} catch (t: Throwable) {
    base
}

fun listItemShape(index: Int, count: Int, radius: Dp = 24.dp): Shape {
    val smoothness = 60
    return when {
        count == 1 -> AbsoluteSmoothCornerShape(
            cornerRadiusTL = radius, smoothnessAsPercentTL = smoothness,
            cornerRadiusTR = radius, smoothnessAsPercentTR = smoothness,
            cornerRadiusBL = radius, smoothnessAsPercentBL = smoothness,
            cornerRadiusBR = radius, smoothnessAsPercentBR = smoothness
        )
        index == 0 -> AbsoluteSmoothCornerShape(
            cornerRadiusTL = radius, smoothnessAsPercentTL = smoothness,
            cornerRadiusTR = radius, smoothnessAsPercentTR = smoothness,
            cornerRadiusBL = 0.dp, smoothnessAsPercentBL = 0,
            cornerRadiusBR = 0.dp, smoothnessAsPercentBR = 0
        )
        index == count - 1 -> AbsoluteSmoothCornerShape(
            cornerRadiusTL = 0.dp, smoothnessAsPercentTL = 0,
            cornerRadiusTR = 0.dp, smoothnessAsPercentTR = 0,
            cornerRadiusBL = radius, smoothnessAsPercentBL = smoothness,
            cornerRadiusBR = radius, smoothnessAsPercentBR = smoothness
        )
        else -> RectangleShape
    }
}
