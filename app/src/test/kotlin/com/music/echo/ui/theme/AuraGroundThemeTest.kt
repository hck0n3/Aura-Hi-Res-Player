package iad1tya.echo.music.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import iad1tya.echo.music.ui.newui.AuraPalette
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The promises the "Interfaz nueva" app theme makes, pinned.
 *
 * The bug this file exists to stop: the redesign painted [AuraPalette.Ground] itself while the app
 * ColorScheme was still derived from `DarkModeKey` alone, so a light-theme user got six dark screens
 * and ~89 white ones plus white dialogs. Nothing in the type system connects those two grounds — only
 * these tests do.
 *
 * The other half is just as important: with the flag OFF the theme has to be the one that shipped,
 * term for term. [surfacesFor] exists as a pure function so that can actually be asserted.
 */
class AuraGroundThemeTest {

    private val accent = Color(0xFFED5564)
    private val dark = darkColorScheme()
    private val light = lightColorScheme()

    /** Every role [ColorScheme.auraGround], [ColorScheme.deepTeal] and [ColorScheme.softLight] touch. */
    private fun assertSameCanvas(label: String, expected: ColorScheme, actual: ColorScheme) {
        assertEquals("$label background", expected.background, actual.background)
        assertEquals("$label surface", expected.surface, actual.surface)
        assertEquals("$label surfaceDim", expected.surfaceDim, actual.surfaceDim)
        assertEquals("$label surfaceBright", expected.surfaceBright, actual.surfaceBright)
        assertEquals("$label containerLowest", expected.surfaceContainerLowest, actual.surfaceContainerLowest)
        assertEquals("$label containerLow", expected.surfaceContainerLow, actual.surfaceContainerLow)
        assertEquals("$label container", expected.surfaceContainer, actual.surfaceContainer)
        assertEquals("$label containerHigh", expected.surfaceContainerHigh, actual.surfaceContainerHigh)
        assertEquals("$label containerHighest", expected.surfaceContainerHighest, actual.surfaceContainerHighest)
        assertEquals("$label surfaceVariant", expected.surfaceVariant, actual.surfaceVariant)
        assertEquals("$label onBackground", expected.onBackground, actual.onBackground)
        assertEquals("$label onSurface", expected.onSurface, actual.onSurface)
        assertEquals("$label onSurfaceVariant", expected.onSurfaceVariant, actual.onSurfaceVariant)
    }

    // ---------------------------------------------------------------- flag OFF: nothing moved

    @Test
    fun `flag off dark is exactly the deep-teal canvas it has always been`() {
        assertSameCanvas(
            "dark",
            expected = dark.deepTeal(accent),
            actual = surfacesFor(
                base = dark, presetScheme = null, darkTheme = true, pureBlack = false,
                newUiGround = false, surfaceSeed = accent,
            ),
        )
    }

    @Test
    fun `flag off light is exactly the soft-light canvas it has always been`() {
        assertSameCanvas(
            "light",
            expected = light.softLight(accent),
            actual = surfacesFor(
                base = light, presetScheme = null, darkTheme = false, pureBlack = false,
                newUiGround = false, surfaceSeed = accent,
            ),
        )
    }

    @Test
    fun `flag off keeps a preset's own surfaces`() {
        val preset = MuestreoDarkColorScheme
        assertSameCanvas(
            "preset",
            expected = preset,
            actual = surfacesFor(
                base = preset, presetScheme = preset, darkTheme = true, pureBlack = false,
                newUiGround = false, surfaceSeed = accent,
            ),
        )
    }

    @Test
    fun `flag off keeps AMOLED pure black`() {
        val scheme = surfacesFor(
            base = dark, presetScheme = null, darkTheme = true, pureBlack = true,
            newUiGround = false, surfaceSeed = accent,
        )
        assertEquals(Color.Black, scheme.background)
        assertEquals(Color.Black, scheme.surface)
    }

    // ---------------------------------------------------------------- flag ON: one ground

    @Test
    fun `flag on lands the app canvas on the redesign's own ground`() {
        val scheme = surfacesFor(
            base = dark, presetScheme = null, darkTheme = true, pureBlack = false,
            newUiGround = true, surfaceSeed = accent,
        )
        // Not "some dark colour": the SAME value `auraScreenBackground` draws, read from the same
        // object, so the two halves of the app cannot drift apart again.
        assertEquals(AuraPalette.Ground, scheme.background)
        assertEquals(AuraPalette.Ground, scheme.surface)
        assertEquals(AuraPalette.Ground, scheme.surfaceContainerLowest)
        assertEquals(AuraPalette.Ground, scheme.surfaceDim)
        assertEquals(AuraPalette.GroundRaised, scheme.surfaceContainerLow)
        // The render fills its cards with white@7% and rules them with white@10% OVER the ground, so a
        // classic M3 card must be that exact composite rather than merely "dark".
        assertEquals(
            AuraPalette.SurfaceFill.compositeOver(AuraPalette.Ground),
            scheme.surfaceContainer,
        )
        assertEquals(
            AuraPalette.SurfaceLine.compositeOver(AuraPalette.Ground),
            scheme.surfaceContainerHigh,
        )
        assertEquals(
            AuraPalette.TrackEmpty.compositeOver(AuraPalette.Ground),
            scheme.surfaceContainerHighest,
        )
    }

    @Test
    fun `the aura ground is a near-black, not a mid grey`() {
        val scheme = dark.auraGround()
        assertTrue(
            "ground luminance ${scheme.background.luminance()} is too high to read as the render's canvas",
            scheme.background.luminance() < 0.02f,
        )
    }

    @Test
    fun `the aura container ladder never goes backwards`() {
        val s = dark.auraGround()
        val ladder = listOf(
            s.surfaceContainerLowest, s.surfaceContainerLow, s.surfaceContainer,
            s.surfaceContainerHigh, s.surfaceContainerHighest,
        )
        ladder.zipWithNext { lower, higher ->
            assertTrue(
                "container ladder dips: ${lower.luminance()} -> ${higher.luminance()}",
                higher.luminance() >= lower.luminance(),
            )
        }
    }

    @Test
    fun `every on-colour stays readable on the aura ground`() {
        val s = dark.auraGround()
        // WCAG 2.1: 4.5 for body text.
        assertTrue(
            "onSurface reached only ${contrastRatio(s.onSurface, s.surface)}:1",
            contrastRatio(s.onSurface, s.surface) >= 4.5f,
        )
        assertTrue(
            "onBackground reached only ${contrastRatio(s.onBackground, s.background)}:1",
            contrastRatio(s.onBackground, s.background) >= 4.5f,
        )
        // Secondary text is read on the raised containers too, not only on the ground.
        assertTrue(
            "onSurfaceVariant on the top container reached only " +
                "${contrastRatio(s.onSurfaceVariant, s.surfaceContainerHighest)}:1",
            contrastRatio(s.onSurfaceVariant, s.surfaceContainerHighest) >= 4.5f,
        )
        // An opaque role: a translucent onSurfaceVariant composites against whatever is behind the
        // card, which is not the ground.
        assertEquals(1f, s.onSurfaceVariant.alpha, 0f)
    }

    @Test
    fun `AMOLED still wins over the aura ground`() {
        val scheme = surfacesFor(
            base = dark, presetScheme = null, darkTheme = true, pureBlack = true,
            newUiGround = true, surfaceSeed = accent,
        )
        assertEquals(Color.Black, scheme.background)
        assertEquals(Color.Black, scheme.surface)
    }

    @Test
    fun `the aura ground wins over a preset, because the new screens paint it unconditionally`() {
        val preset = MuestreoDarkColorScheme
        val scheme = surfacesFor(
            base = preset, presetScheme = preset, darkTheme = true, pureBlack = false,
            newUiGround = true, surfaceSeed = accent,
        )
        assertEquals(AuraPalette.Ground, scheme.background)
        // …but the preset still supplies the ACCENT roles: it is the base scheme.
        assertEquals(preset.primary, scheme.primary)
    }

    // ---------------------------------------------------------------- the accent still lands

    @Test
    fun `the aura ground touches no accent role`() {
        val grounded = dark.auraGround()
        assertEquals(dark.primary, grounded.primary)
        assertEquals(dark.onPrimary, grounded.onPrimary)
        assertEquals(dark.primaryContainer, grounded.primaryContainer)
        assertEquals(dark.secondary, grounded.secondary)
        assertEquals(dark.tertiary, grounded.tertiary)
        assertEquals(dark.inversePrimary, grounded.inversePrimary)
        assertEquals(dark.error, grounded.error)
        assertEquals(dark.outline, grounded.outline)
    }

    @Test
    fun `a picked accent still reaches the accent roles on the aura ground`() {
        val grounded = surfacesFor(
            base = dark, presetScheme = null, darkTheme = true, pureBlack = false,
            newUiGround = true, surfaceSeed = accent,
        )
        // This is what echomusicTheme does after the surface substitution.
        val accented = grounded.withAccent(accent, AccentVividness.EXACT)
        assertNotEquals(grounded.primary, accented.primary)
        // …and it is still legible on the ground the accent was clamped against.
        assertTrue(
            "primary reached only ${contrastRatio(accented.primary, accented.surface)}:1 on the ground",
            contrastRatio(accented.primary, accented.surface) >= 3f,
        )
        // The canvas must NOT follow the accent — that is the surfaceSeed/accentSeed split.
        assertEquals(AuraPalette.Ground, accented.background)
        assertEquals(AuraPalette.Ground, accented.surface)
    }

    // ---------------------------------------------------------------- the dark-only guard

    @Test
    fun `the aura ground is never applied to a light base scheme`() {
        // The caller forces dark whenever the flag is on; this is the belt-and-braces term in
        // surfacesFor, and it must keep working if that ever regresses.
        val scheme = surfacesFor(
            base = light, presetScheme = null, darkTheme = false, pureBlack = false,
            newUiGround = true, surfaceSeed = accent,
        )
        assertSameCanvas("light fallback", expected = light.softLight(accent), actual = scheme)
        assertNotEquals(AuraPalette.Ground, scheme.background)
    }
}
