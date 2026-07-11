

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

fun reportException(throwable: Throwable) {
    // Local logging (unchanged) — keeps stack traces in logcat for local debugging.
    throwable.printStackTrace()
    // Telemetry: records to Firebase Crashlytics on the GMS flavor; no-op on FOSS (flavor source set).
    CrashReporter.record(throwable)
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
