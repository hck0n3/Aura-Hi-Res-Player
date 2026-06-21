

package iad1tya.echo.music.ui.theme

import android.graphics.Bitmap
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.SaverScope
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.palette.graphics.Palette
import com.materialkolor.PaletteStyle
import com.materialkolor.dynamiccolor.ColorSpec
import com.materialkolor.rememberDynamicColorScheme
import com.materialkolor.score.Score

val DefaultThemeColor = Color(0xFFED5564)

@Composable
fun echomusicTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    pureBlack: Boolean = false,
    themeColor: Color = DefaultThemeColor,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current

    val useSystemDynamicColor =
        (themeColor == DefaultThemeColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)

    val baseColorScheme = if (useSystemDynamicColor) {
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else {
        rememberDynamicColorScheme(
            seedColor = themeColor,
            isDark = darkTheme,
            specVersion = ColorSpec.SpecVersion.SPEC_2025,
            style = PaletteStyle.TonalSpot,
        )
    }

    val colorScheme = remember(baseColorScheme, pureBlack, darkTheme) {
        when {
            darkTheme && pureBlack -> baseColorScheme.pureBlack(true)
            darkTheme -> baseColorScheme.deepTeal()
            else -> baseColorScheme.softLight()
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppTypography,
        shapes = androidx.compose.material3.Shapes(),
        content = content
    )
}

fun Bitmap.extractThemeColor(): Color {
    val colorsToPopulation = Palette.from(this)
        .maximumColorCount(8)
        .generate()
        .swatches
        .associate { it.rgb to it.population }
    val rankedColors = Score.score(colorsToPopulation)
    return Color(rankedColors.first())
}

fun Bitmap.extractGradientColors(): List<Color> {
    val extractedColors = Palette.from(this)
        .maximumColorCount(64)
        .generate()
        .swatches
        .associate { it.rgb to it.population }

    val orderedColors = Score.score(extractedColors, 2, 0xff4285f4.toInt(), true)
        .sortedByDescending { Color(it).luminance() }

    return if (orderedColors.size >= 2)
        listOf(Color(orderedColors[0]), Color(orderedColors[1]))
    else
        listOf(Color(0xFF595959), Color(0xFF0D0D0D))
}

fun ColorScheme.pureBlack(apply: Boolean) =
    if (apply) copy(
        surface = Color.Black,
        background = Color.Black
    ) else this

/**
 * "Deep Teal / Midnight Green" dark surfaces: a very dark charcoal with a subtle cyan/teal tint instead
 * of a neutral grey or pure black. The slight colour gives the glassmorphism blur something to pick up
 * (a fully black background would flatten the frosted-glass effect), and the graduated surface containers
 * add an almost-imperceptible depth. Accent colours (primary/secondary/etc.) are left untouched.
 */
fun ColorScheme.deepTeal() = copy(
    background = Color(0xFF111A1D),
    surface = Color(0xFF111A1D),
    surfaceDim = Color(0xFF0E161A),
    surfaceBright = Color(0xFF1F2F34),
    surfaceContainerLowest = Color(0xFF0D1518),
    surfaceContainerLow = Color(0xFF152024),
    surfaceContainer = Color(0xFF172429),
    surfaceContainerHigh = Color(0xFF1A282C),
    surfaceContainerHighest = Color(0xFF1F2F34),
    surfaceVariant = Color(0xFF1A282C),
)

/**
 * Soft light theme: a gentle cool-grey instead of a harsh pure white, easier on the eyes while keeping
 * strong contrast (text/icons stay dark from the base scheme). Graduated containers add subtle depth.
 */
fun ColorScheme.softLight() = copy(
    background = Color(0xFFF1F3F4),
    surface = Color(0xFFF1F3F4),
    surfaceBright = Color(0xFFFAFBFC),
    surfaceDim = Color(0xFFDCE0E2),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF3F5F6),
    surfaceContainer = Color(0xFFEBEEF0),
    surfaceContainerHigh = Color(0xFFE5E9EB),
    surfaceContainerHighest = Color(0xFFDFE4E6),
)

val ColorSaver = object : Saver<Color, Int> {
    override fun restore(value: Int): Color = Color(value)
    override fun SaverScope.save(value: Color): Int = value.toArgb()
}
