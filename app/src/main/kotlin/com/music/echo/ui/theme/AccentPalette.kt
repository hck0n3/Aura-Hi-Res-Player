/**
 * Aura Hi-Res Player (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package iad1tya.echo.music.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import iad1tya.echo.music.ui.component.ColorPickerConversions

/**
 * How literally the chosen accent colour is applied to the Material scheme.
 *
 * WHY THIS EXISTS — the "everything looks pastel" complaint:
 * [echomusicTheme] builds its scheme with `PaletteStyle.TonalSpot`, Material 3's default. TonalSpot
 * (like every stock M3 style except Fidelity/Content) builds its primary tonal palette from
 * `TonalPalette.fromHueAndChroma(seed.hue, 36.0)` — a HARD-CODED chroma. The seed's own chroma is
 * thrown away: only its HUE survives. A screaming #FF0000 (chroma ~107 in HCT) and a washed-out
 * #C08080 (chroma ~25) therefore produce the IDENTICAL primary. That is not a bug in the colour list,
 * it is the tonal generator doing exactly what it was designed to do, and no seed colour can ever
 * escape it. Secondary is capped harder still (chroma 16), tertiary at 24.
 *
 * So a wider palette alone would have changed nothing. The accent roles have to bypass the tonal
 * generator, which is what [withAccent] does.
 */
enum class AccentVividness {
    /** Material 3 tonal generation, untouched. Today's look and the default — nobody's theme moves. */
    SOFT,

    /** The seed's hue AND saturation, at Material's own tone for each role. Vivid but still tonally safe. */
    VIVID,

    /** Like [VIVID], and `primary` becomes the literal picked colour (nudged only if unreadable). */
    EXACT,
}

/**
 * WCAG relative-luminance contrast ratio between two opaque colours, 1.0 (identical) .. 21.0
 * (black on white). Uses [Color.luminance], the same signal
 * [iad1tya.echo.music.ui.component.glassContentColor] already trusts for Liquid Glass.
 */
fun contrastRatio(a: Color, b: Color): Float {
    val la = a.luminance()
    val lb = b.luminance()
    return (maxOf(la, lb) + 0.05f) / (minOf(la, lb) + 0.05f)
}

/**
 * Text/icon colour guaranteed to read on top of [background]. Deliberately the SAME luminance rule
 * (and the same near-black constant) as [iad1tya.echo.music.ui.component.glassContentColor], so a
 * user who types #FFFFF0 or #050505 gets legible content on their accent instead of invisible text.
 */
fun onColorFor(background: Color): Color =
    if (background.luminance() > 0.5f) Color(0xFF1B1B1B) else Color.White

/**
 * Minimum contrast an accent must keep against the surface it is drawn ON. 3.0 is WCAG 2.1's
 * threshold for large text and graphical objects (1.4.11) — the right bar for an accent, which the
 * app uses for section titles, icons and active states rather than body copy.
 */
const val MIN_ACCENT_CONTRAST = 3f

/**
 * Nudges [color] until it reaches [minRatio] contrast against [against], moving ONLY the HSV *value*
 * and keeping hue and saturation intact — the whole point being that the user's saturation survives.
 * Returns [color] untouched when it already passes, which is the common case.
 *
 * The direction is decided by the background: darken on a light surface, lighten on a dark one. If
 * value alone cannot get there (a near-white pick with zero saturation on a white surface has nowhere
 * saturated to go) it falls back to [onColorFor], which is readable by construction. This is what
 * stops "I picked black and now the whole UI is invisible".
 */
fun ensureLegibleOn(color: Color, against: Color, minRatio: Float = MIN_ACCENT_CONTRAST): Color {
    if (contrastRatio(color, against) >= minRatio) return color

    val (hue, saturation, value) = ColorPickerConversions.colorToHsv(color)
    val backgroundIsLight = against.luminance() > 0.5f

    var best = color
    var bestRatio = contrastRatio(color, against)
    // 40 x 0.025 sweeps the entire 0..1 value range, so the loop always terminates.
    for (step in 1..40) {
        val candidateValue = if (backgroundIsLight) {
            value - step * 0.025f
        } else {
            value + step * 0.025f
        }
        if (candidateValue < 0f || candidateValue > 1f) break

        val candidate = ColorPickerConversions.hsvToColor(hue, saturation, candidateValue)
        val ratio = contrastRatio(candidate, against)
        if (ratio >= minRatio) return candidate
        if (ratio > bestRatio) {
            best = candidate
            bestRatio = ratio
        }
    }
    return if (bestRatio >= minRatio) best else onColorFor(against)
}

/** Saturation the seed contributes to each accent role, so the scheme keeps a readable hierarchy. */
private const val SECONDARY_SATURATION_SCALE = 0.72f
private const val TERTIARY_SATURATION_SCALE = 0.88f

/**
 * Rebuilds the ACCENT roles of this scheme from [seed], bypassing the tonal generator's chroma clamp
 * (see [AccentVividness]). Surfaces, backgrounds and the error roles are left exactly as the
 * generator produced them, so the app's existing depth/elevation work is untouched.
 *
 * Each role keeps MATERIAL'S OWN TONE (the HSV value of the generated colour) and only takes the
 * seed's hue + saturation. That is what makes this safe: light themes keep their darker accents and
 * dark themes their lighter ones, exactly as before — only the washed-out chroma is restored. Every
 * `onX` colour is then recomputed from the final background by luminance, so contrast holds in BOTH
 * light and dark no matter what the user typed.
 *
 * [surfaceForContrast] is the surface the foreground accents are drawn on; it is what
 * [ensureLegibleOn] measures against.
 */
fun ColorScheme.withAccent(
    seed: Color,
    vividness: AccentVividness,
    surfaceForContrast: Color = surface,
): ColorScheme {
    if (vividness == AccentVividness.SOFT) return this

    val (seedHue, seedSaturation, _) = ColorPickerConversions.colorToHsv(seed)

    /** [base]'s tone, re-tinted with the seed's hue and (a fraction of) its saturation. */
    fun retint(base: Color, saturationScale: Float = 1f): Color {
        val (_, _, baseValue) = ColorPickerConversions.colorToHsv(base)
        return ColorPickerConversions.hsvToColor(
            seedHue,
            (seedSaturation * saturationScale).coerceIn(0f, 1f),
            baseValue,
        )
    }

    val newPrimary = when (vividness) {
        // The literal colour the user asked for, moved only far enough to stay readable.
        AccentVividness.EXACT -> ensureLegibleOn(seed, surfaceForContrast)
        else -> ensureLegibleOn(retint(primary), surfaceForContrast)
    }
    val newSecondary = ensureLegibleOn(retint(secondary, SECONDARY_SATURATION_SCALE), surfaceForContrast)
    val newTertiary = ensureLegibleOn(retint(tertiary, TERTIARY_SATURATION_SCALE), surfaceForContrast)
    val newPrimaryContainer = retint(primaryContainer)
    val newSecondaryContainer = retint(secondaryContainer, SECONDARY_SATURATION_SCALE)
    val newTertiaryContainer = retint(tertiaryContainer, TERTIARY_SATURATION_SCALE)
    val newInversePrimary = retint(inversePrimary)

    return copy(
        primary = newPrimary,
        onPrimary = onColorFor(newPrimary),
        primaryContainer = newPrimaryContainer,
        onPrimaryContainer = onColorFor(newPrimaryContainer),
        secondary = newSecondary,
        onSecondary = onColorFor(newSecondary),
        secondaryContainer = newSecondaryContainer,
        onSecondaryContainer = onColorFor(newSecondaryContainer),
        tertiary = newTertiary,
        onTertiary = onColorFor(newTertiary),
        tertiaryContainer = newTertiaryContainer,
        onTertiaryContainer = onColorFor(newTertiaryContainer),
        inversePrimary = newInversePrimary,
        surfaceTint = newPrimary,
    )
}
