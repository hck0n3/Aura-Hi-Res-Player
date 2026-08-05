

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

/**
 * The stored accent that means "the user has not picked one".
 *
 * It is a real colour as well as a sentinel, which used to make a deliberate pick of this exact value
 * indistinguishable from "nothing picked" — see [Color.distinctFromNoAccentSentinel], which is what
 * every selection path now goes through so the two can never collide again.
 */
val DefaultThemeColor = Color(0xFFED5564)

@Composable
fun echomusicTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    pureBlack: Boolean = false,
    themeColor: Color = DefaultThemeColor,
    vividness: AccentVividness = AccentVividness.SOFT,
    preset: ThemePreset = ThemePreset.NONE,
    /**
     * The accent as it is STORED (SelectedThemeColorKey), i.e. the user's explicit pick — NOT
     * [themeColor], which under the dynamic theme is the current cover's colour. Only this drives the
     * surfaces; see [surfaceSeedFor] for why they must not follow the artwork. Null (the default)
     * means "no pick", which leaves the canvas on its shipped literals — the right answer for any
     * caller that has no accent preference to hand, such as the crash screen.
     */
    selectedThemeColor: Color? = null,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current

    // A named preset ("Muestreo") is a LITERAL scheme, so it short-circuits the whole seed engine —
    // there is no seed that makes TonalSpot emit the icon's #080D18 ground. Null when NONE, which is
    // every existing user, so nothing below changes for them.
    val presetScheme = preset.colorSchemeOrNull(darkTheme)

    val useSystemDynamicColor = presetScheme == null &&
        (themeColor == DefaultThemeColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)

    val baseColorScheme = if (useSystemDynamicColor) {
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else if (presetScheme != null) {
        presetScheme
    } else {
        // PaletteStyle.TonalSpot throws the seed's chroma away and re-derives it at a fixed 36 — the
        // single reason every accent came out pastel. The seed still sets the HUE here (and all the
        // surface/error roles), and [ColorScheme.withAccent] below puts the saturation back on the
        // accent roles when the user asks for it. See [AccentVividness].
        rememberDynamicColorScheme(
            seedColor = themeColor,
            isDark = darkTheme,
            specVersion = ColorSpec.SpecVersion.SPEC_2025,
            style = PaletteStyle.TonalSpot,
        )
    }

    // Null when the scheme must be left exactly as it came (wallpaper Material You, or a named
    // preset) — see [accentSeedFor]. Non-null it drives the ACCENT ROLES, artwork included when the
    // dynamic theme is on.
    val accentSeed = accentSeedFor(
        themeColor = themeColor,
        usingSystemDynamicColor = useSystemDynamicColor,
        hasPreset = presetScheme != null,
    )

    // The SURFACES follow a different seed on purpose: only a deliberate accent pick, never the
    // artwork — see [surfaceSeedFor]. Sharing [accentSeed] here re-hued the whole canvas on every
    // track change.
    val surfaceSeed = surfaceSeedFor(selectedThemeColor)

    val colorScheme = remember(
        baseColorScheme, presetScheme, pureBlack, darkTheme, accentSeed, surfaceSeed, vividness,
    ) {
        // Surfaces FIRST: withAccent measures its legibility clamp against the final surface, so the
        // AMOLED/deep-teal/soft-light substitutions have to be in place before it runs.
        val surfaced = when {
            // AMOLED must stay TRULY black, so it takes no surface tint at all. Its containers still
            // come from the seed-derived base scheme, so the accent is not absent here either.
            darkTheme && pureBlack -> baseColorScheme.pureBlack(true)
            // A preset brings its OWN surfaces (the icon's ground is the whole point), so the generic
            // deep-teal / soft-light substitutions must not overwrite them. AMOLED still wins above:
            // it is a separate, explicit user toggle.
            baseColorScheme === presetScheme -> baseColorScheme
            darkTheme -> baseColorScheme.deepTeal(surfaceSeed)
            else -> baseColorScheme.softLight(surfaceSeed)
        }
        if (accentSeed == null) surfaced else surfaced.withAccent(accentSeed, vividness)
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
 * The dark ladder's literals already carry their own tint (the deep teal is ~0.41 saturated), so
 * keeping each one's saturation as-is and only swapping the hue reproduces the shipped design exactly
 * when the seed is teal. No gain, and the ceiling never bites — it is a rail, not a setting.
 */
private const val DARK_SURFACE_TINT_GAIN = 1f
private const val MAX_DARK_SURFACE_SATURATION = 1f

/**
 * The light ladder's literals are near-neutral by design (~0.01–0.03 saturation), so a proportional
 * re-hue would be invisible. A small gain lifts it to a perceptible wash while the ceiling keeps the
 * page paper-like rather than coloured.
 */
private const val LIGHT_SURFACE_TINT_GAIN = 3f
private const val MAX_LIGHT_SURFACE_SATURATION = 0.08f

/**
 * "Deep Teal / Midnight Green" dark surfaces: a very dark charcoal with a subtle cyan/teal tint instead
 * of a neutral grey or pure black. The slight colour gives the glassmorphism blur something to pick up
 * (a fully black background would flatten the frosted-glass effect), and the graduated surface containers
 * add an almost-imperceptible depth. Accent colours (primary/secondary/etc.) are left untouched.
 *
 * The literals below are the design AT THE SHIPPED TEAL HUE. With a [seed] they are re-hued to it
 * ([surfaceRetint]) — same darkness, same amount of tint, different direction — so choosing an accent
 * moves the entire canvas (background, cards, nav bar, mini player) and not just a few details. Null
 * seed hands back the literals untouched, which is what Material You and the presets get.
 */
fun ColorScheme.deepTeal(seed: Color? = null): ColorScheme {
    val tint = surfaceRetint(seed, DARK_SURFACE_TINT_GAIN, MAX_DARK_SURFACE_SATURATION)
    return copy(
        background = tint(Color(0xFF111A1D)),
        surface = tint(Color(0xFF111A1D)),
        surfaceDim = tint(Color(0xFF0E161A)),
        surfaceBright = tint(Color(0xFF1F2F34)),
        surfaceContainerLowest = tint(Color(0xFF0D1518)),
        surfaceContainerLow = tint(Color(0xFF152024)),
        surfaceContainer = tint(Color(0xFF172429)),
        surfaceContainerHigh = tint(Color(0xFF1A282C)),
        surfaceContainerHighest = tint(Color(0xFF1F2F34)),
        surfaceVariant = tint(Color(0xFF1A282C)),
    )
}

/**
 * Soft light theme: a gentle cool-grey instead of a harsh pure white, easier on the eyes while keeping
 * strong contrast (text/icons stay dark from the base scheme). Graduated containers add subtle depth.
 *
 * Re-hued from [seed] on the same terms as [deepTeal]; see [surfaceRetint].
 */
fun ColorScheme.softLight(seed: Color? = null): ColorScheme {
    val tint = surfaceRetint(seed, LIGHT_SURFACE_TINT_GAIN, MAX_LIGHT_SURFACE_SATURATION)
    return copy(
        background = tint(Color(0xFFF1F3F4)),
        surface = tint(Color(0xFFF1F3F4)),
        surfaceBright = tint(Color(0xFFFAFBFC)),
        surfaceDim = tint(Color(0xFFDCE0E2)),
        surfaceContainerLowest = tint(Color(0xFFFFFFFF)),
        surfaceContainerLow = tint(Color(0xFFF3F5F6)),
        surfaceContainer = tint(Color(0xFFEBEEF0)),
        surfaceContainerHigh = tint(Color(0xFFE5E9EB)),
        surfaceContainerHighest = tint(Color(0xFFDFE4E6)),
    )
}

val ColorSaver = object : Saver<Color, Int> {
    override fun restore(value: Int): Color = Color(value)
    override fun SaverScope.save(value: Color): Int = value.toArgb()
}
