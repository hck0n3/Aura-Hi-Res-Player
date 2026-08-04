/**
 * Aura Hi-Res Player (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package iad1tya.echo.music.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * A named, hand-authored app theme — as opposed to the seed-driven accents in
 * [iad1tya.echo.music.ui.screens.settings.PaletteColors].
 *
 * WHY THIS EXISTS AT ALL. Every accent the app has ever shipped is ONE seed colour pushed through
 * materialkolor's tonal generator; surfaces then get overwritten wholesale by [ColorScheme.deepTeal]
 * or [ColorScheme.softLight]. That model cannot express "these five exact colours", which is precisely
 * what a theme derived from the app icon has to be: the icon's ground is #080D18, and no seed on earth
 * makes TonalSpot emit #080D18. So a preset bypasses the generator and ships a literal scheme.
 *
 * It is deliberately a SMALL extension of the existing model, not a parallel one:
 *  - it is chosen from the same palette grid, through the same `handleColorSelection` call;
 *  - it still travels through the single [echomusicTheme] entry point;
 *  - selecting anything else (a swatch, Dynamic, a typed hex) sets it back to [NONE], so it is
 *    reversible by construction rather than by a special "undo" path.
 */
enum class ThemePreset {
    /** No preset. The seed/tonal/Material-You engine behaves exactly as it always has. */
    NONE,

    /** "Muestreo" — the palette of the Aura Hi-Res app icon. See [MuestreoDarkColorScheme]. */
    MUESTREO,
}

// ---------------------------------------------------------------------------------------------
// The icon's five colours. These are the source of truth: the mark, the wordmark gradient and the
// theme all read them from here, so the icon and the app can never drift apart.
// ---------------------------------------------------------------------------------------------

/** Light end of the icon gradient — the highlight. Very light (L* ~0.63), see the contrast note below. */
val MuestreoTeal = Color(0xFF3FE7CE)

/** Mid of the icon gradient, and the theme's primary. */
val MuestreoBlue = Color(0xFF2FA6F0)

/** Deep end of the icon gradient. */
val MuestreoViolet = Color(0xFF6A5BFF)

/** The icon's ground: near-black with a blue bias. NOT pure black, NOT grey. */
val MuestreoGround = Color(0xFF080D18)

/**
 * The soft radial bloom behind the mark: [MuestreoBlue] at 38% fading to 0. Exposed so a surface that
 * wants the icon's glow uses the icon's actual glow instead of re-inventing one.
 */
val MuestreoGlow = Color(0x612FA6F0)

/** Stops of the icon gradient, teal -> blue -> violet, in the icon's own order. */
val MuestreoGradientStops = listOf(MuestreoTeal, MuestreoBlue, MuestreoViolet)

// ---------------------------------------------------------------------------------------------
// Dark. The palette is native to dark, so these are the icon's colours essentially untouched.
//
// Contrast measured against the ground #080D18 (WCAG 2.1 relative luminance):
//   primary   #2FA6F0 on #080D18 ..... 7.25:1   (>= 4.5, body text bar)
//   secondary #3FE7CE on #080D18 .... 12.52:1
//   tertiary  #8B7CFF on #080D18 ..... 5.94:1
//   onSurface #E4ECF6 on #080D18 .... 16.30:1
//   onSurfaceVariant #A9B7CB ......... 9.55:1
//   onPrimary #04101C on #2FA6F0 ..... 7.15:1
//   onSecondary #00201C on #3FE7CE .. 11.06:1
//   onTertiary #150C4A on #8B7CFF .... 5.43:1
// Worst case anywhere in the dark scheme is primary on the top container #192539, at 5.74:1.
//
// THE #3FE7CE PROBLEM, handled rather than shipped: teal's luminance is 0.627, so WHITE on it is
// 1.63:1 — illegible. [onColorFor] would ALSO get this wrong for the blue (it flips to white below
// luminance 0.5, and blue sits at 0.342, giving 2.68:1). That is exactly why every `onX` here is
// authored by hand and verified, instead of being derived by the generic luminance rule.
//
// The exact icon violet #6A5BFF is only 4.21:1 on the ground — fine for a graphical accent, short of
// the 4.5 text bar. `tertiary` therefore carries a lightness-raised relative (#8B7CFF, same hue 245.5
// and saturation) and the literal #6A5BFF survives as [MuestreoViolet] for the gradient and swatch.
// ---------------------------------------------------------------------------------------------

val MuestreoDarkColorScheme: ColorScheme = darkColorScheme(
    primary = MuestreoBlue,
    onPrimary = Color(0xFF04101C),
    primaryContainer = Color(0xFF10395C),
    onPrimaryContainer = Color(0xFFB9E0FF),
    inversePrimary = Color(0xFF0B5C93),

    secondary = MuestreoTeal,
    onSecondary = Color(0xFF00201C),
    secondaryContainer = Color(0xFF0C4A44),
    onSecondaryContainer = Color(0xFFA9F5E7),

    tertiary = Color(0xFF8B7CFF),
    onTertiary = Color(0xFF150C4A),
    tertiaryContainer = Color(0xFF322A82),
    onTertiaryContainer = Color(0xFFDCD6FF),

    background = MuestreoGround,
    onBackground = Color(0xFFE4ECF6),
    surface = MuestreoGround,
    onSurface = Color(0xFFE4ECF6),
    surfaceVariant = Color(0xFF1C2636),
    onSurfaceVariant = Color(0xFFA9B7CB),
    surfaceTint = MuestreoBlue,

    // The container ladder keeps the ground's blue bias all the way up, which is what gives the
    // glassmorphism blur something to pick up (a neutral grey ladder flattens it).
    surfaceDim = Color(0xFF05090F),
    surfaceBright = Color(0xFF1E2938),
    surfaceContainerLowest = Color(0xFF04080E),
    surfaceContainerLow = Color(0xFF0B1220),
    surfaceContainer = Color(0xFF0E1626),
    surfaceContainerHigh = Color(0xFF131D2F),
    surfaceContainerHighest = Color(0xFF192539),

    inverseSurface = Color(0xFFE4ECF6),
    inverseOnSurface = Color(0xFF0B1220),

    outline = Color(0xFF6B7A90),
    outlineVariant = Color(0xFF2A3547),
    scrim = Color(0xFF000000),

    error = Color(0xFFFF7A85),
    onError = Color(0xFF3A0009),
    errorContainer = Color(0xFF7A1220),
    onErrorContainer = Color(0xFFFFD9DC),
)

// ---------------------------------------------------------------------------------------------
// Light. NOT an inversion: the hues are identical (blue 203 deg, teal 171 deg, violet 245.5 deg) and
// the SATURATION is identical too — only the HSV value drops, which is the one move that keeps a
// palette recognisable while making it readable on a light ground.
//
//   primary   #1E6A99 = blue   at V 0.60 ... 5.41:1 on #F2F6FB, white on it 5.87:1
//   secondary #1F7064 = teal   at V 0.44 ... 5.44:1 on #F2F6FB, white on it 5.90:1
//   tertiary  #4A40B2 = violet at V 0.70 ... 7.25:1 on #F2F6FB, white on it 7.87:1
//   onSurface #0B1220 on #F2F6FB ....... 17.25:1
//   onSurfaceVariant #43506A ............ 7.46:1
//
// The accents are measured against the TOP container #DDE5EF, not just the background — a section
// title tinted `primary` sits on a raised card, and that is the darkest ground it ever meets. The
// first pass (blue at V 0.64) cleared 4.90:1 on the background but only 4.18:1 there, so each accent
// was darkened one more notch until the worst case in the whole scheme is 4.62:1.
//
// The light ground #F2F6FB is the same blue bias as #080D18 read from the other end, so the theme
// still feels like itself with the lights on.
// ---------------------------------------------------------------------------------------------

val MuestreoLightColorScheme: ColorScheme = lightColorScheme(
    primary = Color(0xFF1E6A99),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFCDE7FA),
    onPrimaryContainer = Color(0xFF05263A),
    inversePrimary = Color(0xFF8FD1FF),

    secondary = Color(0xFF1F7064),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFB8F1E4),
    onSecondaryContainer = Color(0xFF04302A),

    tertiary = Color(0xFF4A40B2),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFDFDBFF),
    onTertiaryContainer = Color(0xFF1B1259),

    background = Color(0xFFF2F6FB),
    onBackground = Color(0xFF0B1220),
    surface = Color(0xFFF2F6FB),
    onSurface = Color(0xFF0B1220),
    surfaceVariant = Color(0xFFDFE6F0),
    onSurfaceVariant = Color(0xFF43506A),
    surfaceTint = Color(0xFF1E6A99),

    surfaceDim = Color(0xFFDCE3ED),
    surfaceBright = Color(0xFFFBFDFF),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF5F8FC),
    surfaceContainer = Color(0xFFEBF0F7),
    surfaceContainerHigh = Color(0xFFE4EAF3),
    surfaceContainerHighest = Color(0xFFDDE5EF),

    inverseSurface = Color(0xFF0B1220),
    inverseOnSurface = Color(0xFFE4ECF6),

    outline = Color(0xFF6F7C93),
    outlineVariant = Color(0xFFC3CDDC),
    scrim = Color(0xFF000000),

    error = Color(0xFFB3261E),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF410E0B),
)

/**
 * The scheme for [preset], or `null` when there is no preset (i.e. keep the seed engine).
 *
 * Both schemes are process-wide `val`s built once at class-init, so this is a field read: no
 * allocation, no arithmetic, nothing that could land in a recomposition or per-frame hot path. A
 * theme has to be free at runtime, and this one literally is.
 */
fun ThemePreset.colorSchemeOrNull(darkTheme: Boolean): ColorScheme? = when (this) {
    ThemePreset.NONE -> null
    ThemePreset.MUESTREO -> if (darkTheme) MuestreoDarkColorScheme else MuestreoLightColorScheme
}
