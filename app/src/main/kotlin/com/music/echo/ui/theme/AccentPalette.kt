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
 *
 * EVERY mode now bypasses it, including [SOFT]. SOFT used to return the scheme untouched, and since
 * SOFT is the default nobody had ever seen the bypass run: all 42 swatches collapsed back to the same
 * fixed-chroma pastel, which is precisely the "I change the colour and nothing changes" report. The
 * three modes are now three amounts of the seed's saturation, not "off / on / literal".
 *
 * "Leave this scheme completely alone" (Material You from the wallpaper, and the named presets) is no
 * longer expressed as a vividness — it is [accentSeedFor] returning null, so the caller simply does
 * not run [withAccent]. That separation is what let SOFT become a real setting.
 */
enum class AccentVividness(
    /** Share of the seed's own saturation this mode puts on the accent roles. */
    internal val seedSaturationScale: Float,
) {
    /**
     * Gentle: the seed's hue with a bit under two thirds of its saturation, at Material's own tone.
     * Still the default and still the calmest of the three, but a red now reads as red and a teal as
     * teal instead of both landing on the same pastel.
     */
    SOFT(0.62f),

    /** The seed's hue AND its full saturation, at Material's own tone for each role. */
    VIVID(1f),

    /** Like [VIVID], and `primary` becomes the literal picked colour (nudged only if unreadable). */
    EXACT(1f),
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
    val (seedHue, rawSeedSaturation, _) = ColorPickerConversions.colorToHsv(seed)
    // [AccentVividness] is a dial on the seed's saturation, not an on/off switch — see the enum's doc
    // for why "off" no longer exists here. The role scales below still multiply on top of this.
    val seedSaturation = rawSeedSaturation * vividness.seedSaturationScale

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

// ------------------------------------------------------------------ the "no accent picked" sentinel

/**
 * The accent [DefaultThemeColor] would be nudged to if the user picked it literally. One step of blue
 * (1/255) away from the sentinel: a different Int, an indistinguishable colour.
 */
private val DefaultThemeColorNudged = Color(0xFFED5565)

/**
 * Makes a DELIBERATELY chosen colour distinguishable from the "the user has not picked an accent"
 * sentinel, which [DefaultThemeColor] doubles as.
 *
 * The bug this closes: that sentinel is a real, reachable colour. Typing its exact hex (#ED5564) into
 * the accent field stored it as the user's accent, and every consumer then read it back as "nothing
 * picked" — so instead of the red they asked for, the app silently switched to wallpaper Material You
 * ([echomusicTheme]), reported "no custom colour" in Appearance, and lit up the Dynamic swatch as
 * selected. Choosing a colour turned the colour off.
 *
 * Rather than give the sentinel a second meaning (an alpha marker, say), which would have re-tinted
 * every existing install the moment their stored value stopped matching, the collision is removed at
 * the source: a real pick is never STORED as the sentinel. Everything else keeps reading the same
 * value it always has, so no existing accent moves.
 */
fun Color.distinctFromNoAccentSentinel(): Color =
    if (this == DefaultThemeColor) DefaultThemeColorNudged else this

/**
 * The seed the accent roles should be rebuilt from, or null when the scheme must be left exactly as it
 * came.
 *
 * Null in the two cases where the seed is not the user's accent at all:
 *  - [usingSystemDynamicColor]: Material You derives everything from the WALLPAPER and [themeColor] is
 *    only the sentinel, so re-tinting from it would paint the default red over the system theme.
 *  - [hasPreset]: a named theme ships a finished, contrast-verified scheme; re-tinting its accents
 *    from a seed would undo exactly the work that makes it readable.
 *
 * This used to be spelled "force [AccentVividness.SOFT]", which only worked while SOFT happened to be
 * a no-op. Making SOFT a real setting required saying it properly.
 */
fun accentSeedFor(
    themeColor: Color,
    usingSystemDynamicColor: Boolean,
    hasPreset: Boolean,
): Color? = if (usingSystemDynamicColor || hasPreset) null else themeColor

/**
 * The seed the SURFACES may be re-hued from, or null to keep the shipped deep-teal / soft-light
 * literals exactly as they are.
 *
 * DELIBERATELY NOT [accentSeedFor]. The accent seed follows the LIVE theme colour, which under the
 * dynamic theme is the current cover's colour — feeding that to the surfaces repainted the entire
 * canvas (background, every card, the library ground, the settings sheet, the nav bar, the mini
 * player) on EVERY TRACK CHANGE: a red cover turned the app maroon, the next one violet. The canvas
 * may only follow something the user chose ON PURPOSE, so it reads the STORED accent and nothing else.
 * The accent ROLES keep following [accentSeedFor], artwork included — that part was never the problem.
 *
 * [storedAccent] must be [iad1tya.echo.music.constants.SelectedThemeColorKey]'s value rather than the
 * live theme colour: the live one is the artwork while the dynamic theme is on, and it also lags a
 * frame behind a fresh pick.
 *
 * Null when there is no pick. A real pick is STORED nudged off the sentinel
 * ([distinctFromNoAccentSentinel]), so a stored value that still IS the sentinel can only mean "the
 * user has not picked an accent". Running the same nudge here is what stops [DefaultThemeColor] from
 * acting as a seed — without it, every pre-Android-12 device (no Material You, so nothing else forces
 * the seed to null) sat on a permanently maroon canvas nobody had asked for.
 */
fun surfaceSeedFor(storedAccent: Color?): Color? {
    val pick = storedAccent ?: return null
    return if (pick.distinctFromNoAccentSentinel() != pick) null else pick
}

// ------------------------------------------------------------------ surfaces

/**
 * Below this the seed is effectively a grey, its hue is whatever rounding produced (0 == red for a
 * pure grey), and tinting the whole app from it would be inventing a colour the user never chose.
 */
private const val MIN_SEED_SATURATION_FOR_SURFACE_TINT = 0.05f

/**
 * Re-hues one of the app's hardcoded surface literals toward [seed], or hands it back untouched when
 * there is no seed to follow.
 *
 * WHY: the surfaces were fixed literals (#111A1D dark, #F1F3F4 light and their container ladders), so
 * the entire canvas — background, every card, the nav bar, the mini player — stayed the same colour no
 * matter which of the 42 swatches was chosen. Only a few small accented details moved, which is why
 * picking a colour "barely changed anything".
 *
 * HOW IT STAYS SUBTLE: each literal keeps its own VALUE (its step on the depth ladder) and its own
 * SATURATION (how much tint the design already carried); only the HUE is replaced. So the dark ground
 * stays exactly as dark and exactly as lightly tinted as the shipped deep-teal — it just leans the
 * user's way instead of teal's. The tint is then scaled by the seed's own saturation, so a muted pick
 * gets a muted room. [gain] lets the light ladder, whose literals are near-neutral by design, lift
 * enough to be perceptible; [maxSaturation] is the hard ceiling that keeps it a wash.
 */
internal fun surfaceRetint(
    seed: Color?,
    gain: Float,
    maxSaturation: Float,
): (Color) -> Color {
    if (seed == null) return { it }
    val (seedHue, seedSaturation, _) = ColorPickerConversions.colorToHsv(seed)
    if (seedSaturation < MIN_SEED_SATURATION_FOR_SURFACE_TINT) return { it }

    val scale = seedSaturation * gain
    return { literal ->
        val (_, literalSaturation, literalValue) = ColorPickerConversions.colorToHsv(literal)
        ColorPickerConversions.hsvToColor(
            seedHue,
            (literalSaturation * scale).coerceIn(0f, maxSaturation),
            literalValue,
        )
    }
}
