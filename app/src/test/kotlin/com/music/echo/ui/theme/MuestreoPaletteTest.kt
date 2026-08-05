package iad1tya.echo.music.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import iad1tya.echo.music.ui.component.ColorPickerConversions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The promises the "Muestreo" theme makes, pinned.
 *
 * A hand-authored scheme has no generator to keep it honest — nothing recomputes its `onX` colours,
 * so a one-character typo in a hex literal ships as unreadable text and no compiler notices. These
 * tests are that missing generator: every pair a user actually reads is measured here.
 */
class MuestreoPaletteTest {

    /** WCAG 2.1: 4.5 for body text, 3.0 for large text and graphical objects. */
    private val textBar = 4.5f
    private val graphicBar = 3f

    private fun assertContrast(label: String, fg: Color, bg: Color, min: Float) {
        val ratio = contrastRatio(fg, bg)
        assertTrue("$label reached only ${"%.2f".format(ratio)}:1 (needs $min)", ratio >= min)
    }

    // ---------------------------------------------------------------- the icon's colours

    @Test
    fun `the palette constants are literally the icon's colours`() {
        assertEquals(Color(0xFF3FE7CE), MuestreoTeal)
        assertEquals(Color(0xFF2FA6F0), MuestreoBlue)
        assertEquals(Color(0xFF6A5BFF), MuestreoViolet)
        assertEquals(Color(0xFF080D18), MuestreoGround)
        assertEquals(listOf(MuestreoTeal, MuestreoBlue, MuestreoViolet), MuestreoGradientStops)
    }

    @Test
    fun `the ground is blue-biased near-black, not pure black and not grey`() {
        assertNotEquals(Color.Black, MuestreoGround)
        // Blue clearly above red/green is what makes it read as a blue night rather than charcoal.
        assertTrue("ground has no blue bias", MuestreoGround.blue > MuestreoGround.red * 2f)
        assertTrue("ground has no blue bias", MuestreoGround.blue > MuestreoGround.green * 1.5f)
        assertTrue("ground is not near-black", contrastRatio(Color.White, MuestreoGround) > 15f)
    }

    // ---------------------------------------------------------------- dark

    @Test
    fun `dark accents carry the icon's own blue and teal`() {
        assertEquals(MuestreoBlue, MuestreoDarkColorScheme.primary)
        assertEquals(MuestreoTeal, MuestreoDarkColorScheme.secondary)
        assertEquals(MuestreoGround, MuestreoDarkColorScheme.surface)
        assertEquals(MuestreoGround, MuestreoDarkColorScheme.background)
    }

    @Test
    fun `dark accents and text clear the body-text bar on the ground`() {
        val s = MuestreoDarkColorScheme
        assertContrast("primary on surface", s.primary, s.surface, textBar)
        assertContrast("secondary on surface", s.secondary, s.surface, textBar)
        assertContrast("tertiary on surface", s.tertiary, s.surface, textBar)
        assertContrast("onSurface", s.onSurface, s.surface, textBar)
        assertContrast("onBackground", s.onBackground, s.background, textBar)
        assertContrast("onSurfaceVariant", s.onSurfaceVariant, s.surfaceVariant, textBar)
    }

    @Test
    fun `dark accents survive the whole container ladder, not just the flat ground`() {
        // An accent-tinted section title sits on a RAISED card; the top container is the darkest
        // ground it ever meets, and the pair the flat-background check would have missed.
        val s = MuestreoDarkColorScheme
        for (bg in listOf(s.surfaceContainerLow, s.surfaceContainer, s.surfaceContainerHigh, s.surfaceContainerHighest)) {
            assertContrast("primary on $bg", s.primary, bg, textBar)
            assertContrast("onSurface on $bg", s.onSurface, bg, textBar)
        }
    }

    @Test
    fun `dark stays readable when AMOLED forces the surface to pure black`() {
        // pureBlack() replaces surface/background only, so the accents have to hold on black too.
        val s = MuestreoDarkColorScheme.pureBlack(true)
        assertContrast("primary on black", s.primary, s.surface, textBar)
        assertContrast("secondary on black", s.secondary, s.surface, textBar)
        assertContrast("tertiary on black", s.tertiary, s.surface, textBar)
        assertContrast("onSurface on black", s.onSurface, s.surface, textBar)
    }

    // ---------------------------------------------------------------- light

    @Test
    fun `light is not an inversion - same hue and saturation, lower brightness`() {
        // The whole "do not naively invert" requirement, as an assertion: each light accent is its
        // dark counterpart at a lower HSV value, with hue and saturation carried over untouched.
        val pairs = listOf(
            "blue" to (MuestreoBlue to MuestreoLightColorScheme.primary),
            "teal" to (MuestreoTeal to MuestreoLightColorScheme.secondary),
            "violet" to (MuestreoViolet to MuestreoLightColorScheme.tertiary),
        )
        for ((name, pair) in pairs) {
            val (source, lightened) = pair
            val (sourceHue, sourceSat, sourceVal) = ColorPickerConversions.colorToHsv(source)
            val (lightHue, lightSat, lightVal) = ColorPickerConversions.colorToHsv(lightened)
            assertEquals("$name hue drifted", sourceHue, lightHue, 2f)
            assertEquals("$name saturation drifted", sourceSat, lightSat, 0.02f)
            assertTrue("$name was not darkened for the light theme", lightVal < sourceVal)
        }
    }

    @Test
    fun `light accents and text clear the body-text bar on the light ground`() {
        val s = MuestreoLightColorScheme
        assertContrast("primary on surface", s.primary, s.surface, textBar)
        assertContrast("secondary on surface", s.secondary, s.surface, textBar)
        assertContrast("tertiary on surface", s.tertiary, s.surface, textBar)
        assertContrast("onSurface", s.onSurface, s.surface, textBar)
        assertContrast("onSurfaceVariant", s.onSurfaceVariant, s.surfaceVariant, textBar)
    }

    @Test
    fun `light accents survive the whole container ladder`() {
        val s = MuestreoLightColorScheme
        for (bg in listOf(s.surfaceContainerLow, s.surfaceContainer, s.surfaceContainerHigh, s.surfaceContainerHighest, s.surfaceDim)) {
            assertContrast("primary on $bg", s.primary, bg, textBar)
            assertContrast("onSurface on $bg", s.onSurface, bg, textBar)
        }
    }

    // ---------------------------------------------------------------- on-colours

    @Test
    fun `every on-colour is readable on its own role, in both themes`() {
        for (s in listOf(MuestreoDarkColorScheme, MuestreoLightColorScheme)) {
            assertContrast("onPrimary", s.onPrimary, s.primary, textBar)
            assertContrast("onSecondary", s.onSecondary, s.secondary, textBar)
            assertContrast("onTertiary", s.onTertiary, s.tertiary, textBar)
            assertContrast("onError", s.onError, s.error, textBar)
            assertContrast("onPrimaryContainer", s.onPrimaryContainer, s.primaryContainer, textBar)
            assertContrast("onSecondaryContainer", s.onSecondaryContainer, s.secondaryContainer, textBar)
            assertContrast("onTertiaryContainer", s.onTertiaryContainer, s.tertiaryContainer, textBar)
            assertContrast("onErrorContainer", s.onErrorContainer, s.errorContainer, textBar)
            assertContrast("inverseOnSurface", s.inverseOnSurface, s.inverseSurface, textBar)
            assertContrast("outline", s.outline, s.surface, graphicBar)
        }
    }

    @Test
    fun `the generic luminance rule would have shipped unreadable text on teal and blue`() {
        // WHY the on-colours above are authored by hand: onColorFor flips to white below luminance
        // 0.5, and BOTH the icon's blue (0.34) and — worse — nothing saves the teal (0.63), which is
        // so light that white on it is barely visible. This test exists so that anyone tempted to
        // "simplify" the scheme by deriving onPrimary/onSecondary sees the numbers first.
        assertTrue(contrastRatio(Color.White, MuestreoBlue) < textBar)
        assertTrue(contrastRatio(Color.White, MuestreoTeal) < graphicBar)
        assertEquals(Color.White, onColorFor(MuestreoBlue))
        // ...and the hand-authored inks do pass, which is the point.
        assertContrast("authored onPrimary", MuestreoDarkColorScheme.onPrimary, MuestreoBlue, textBar)
        assertContrast("authored onSecondary", MuestreoDarkColorScheme.onSecondary, MuestreoTeal, textBar)
    }

    // ---------------------------------------------------------------- opting in and out

    @Test
    fun `no preset means the seed engine is untouched, in both themes`() {
        // Reversibility, at its root: NONE resolves to no scheme at all, so echomusicTheme falls
        // through to exactly the code path every existing user is already on.
        assertNull(ThemePreset.NONE.colorSchemeOrNull(darkTheme = true))
        assertNull(ThemePreset.NONE.colorSchemeOrNull(darkTheme = false))
    }

    @Test
    fun `the preset resolves to the right scheme for each mode`() {
        assertEquals(MuestreoDarkColorScheme, ThemePreset.MUESTREO.colorSchemeOrNull(darkTheme = true))
        assertEquals(MuestreoLightColorScheme, ThemePreset.MUESTREO.colorSchemeOrNull(darkTheme = false))
    }

    @Test
    fun `resolving the preset allocates nothing - it hands back the same instance every time`() {
        // "A theme must be free at runtime": these are process-wide vals, so repeated resolution in a
        // recomposition is a field read, not a scheme build.
        val first: ColorScheme? = ThemePreset.MUESTREO.colorSchemeOrNull(darkTheme = true)
        val second: ColorScheme? = ThemePreset.MUESTREO.colorSchemeOrNull(darkTheme = true)
        assertTrue("preset rebuilt its scheme instead of reusing it", first === second)
    }

    @Test
    fun `vividness cannot repaint the preset - the accent seed is null by contract`() {
        // This used to be spelled "echomusicTheme forces SOFT, and SOFT is a no-op in withAccent".
        // SOFT is now a real setting (it had to become one — as a no-op it made the whole palette
        // inert), so the contract moved to where it belongs: with a preset there is NO seed, so
        // echomusicTheme never calls withAccent at all and the verified colours above survive.
        assertNull(
            "a preset scheme was handed a seed to re-tint from",
            accentSeedFor(Color(0xFFFF1744), usingSystemDynamicColor = false, hasPreset = true),
        )
        // The preset's own surfaces are protected separately: echomusicTheme's
        // `baseColorScheme === presetScheme` branch skips deepTeal/softLight entirely, because those
        // substitute the generic ground whether or not a seed tints it. Pinned here so that if the
        // preset ever DOES reach deepTeal, the difference is loud rather than silent.
        assertNotEquals(
            "the generic dark ground is the same colour as the preset's own — this test is now blind",
            MuestreoDarkColorScheme.surface,
            MuestreoDarkColorScheme.deepTeal(seed = null).surface,
        )
    }
}
