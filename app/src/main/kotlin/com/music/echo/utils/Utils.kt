

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
    throwable.printStackTrace()
}

@Suppress("DEPRECATION")
fun setAppLocale(context: Context, locale: Locale) {
    val config = Configuration(context.resources.configuration)
    config.setLocale(locale)
    context.resources.updateConfiguration(config, context.resources.displayMetrics)
}

/**
 * Resolves the in-app language from a lightweight SharedPreferences mirror. Safe to read in
 * attachBaseContext — DataStore must NEVER be read there: its blocking read at cold start
 * crashes/ANRs the launch (notably on some OEM ROMs). Defaults to Spanish ("es"). The mirror is
 * kept in sync from App's settings observer whenever the in-app language changes.
 */
fun resolveAppLanguageTag(context: Context): String =
    context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        .getString("app_language", null)
        ?.takeUnless { it == SYSTEM_DEFAULT }
        ?: "es"

/**
 * Wraps [base] with a configuration forced to the resolved app language so every component
 * (Application, Activity, Service) resolves resources in the selected language on all API levels.
 * Call from each component's attachBaseContext. Never throws — falls back to [base] on any error
 * so a locale issue can never prevent the app from launching.
 */
fun localeAwareContext(base: Context): Context = try {
    val locale = Locale.forLanguageTag(resolveAppLanguageTag(base))
    Locale.setDefault(locale)
    val config = Configuration(base.resources.configuration)
    config.setLocale(locale)
    // Android TV: the phone UI looks zoomed/giant on a ~1080p TV panel (few, oversized elements).
    // Shrink the effective density so more, smaller content fits — closer to a tablet/desktop layout.
    val uiMode = base.getSystemService(Context.UI_MODE_SERVICE) as? android.app.UiModeManager
    if (uiMode?.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION && config.densityDpi > 0) {
        config.densityDpi = (config.densityDpi * 0.7f).toInt().coerceAtLeast(120)
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
