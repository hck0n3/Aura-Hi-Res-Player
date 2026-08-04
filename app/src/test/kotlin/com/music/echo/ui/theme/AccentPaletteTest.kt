package iad1tya.echo.music.ui.theme

import androidx.compose.ui.graphics.Color
import iad1tya.echo.music.ui.component.ColorPickerConversions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The two promises the manual colour picker makes, pinned.
 *
 *  1. A malformed hex code can only ever produce null (an inline error), never a crash and never a
 *     nonsense colour. [ColorPickerConversions.parseHexColor] used to lean on `toIntOrNull(16)`,
 *     which happily accepts a leading sign, so "-FFFFF" parsed into a real colour.
 *  2. Whatever the user picks stays READABLE on the surface it lands on, in BOTH themes, WITHOUT
 *     throwing away the saturation that was the whole point of the feature.
 */
class AccentPaletteTest {

    /** Aura's real light surface (softLight) and dark surface (deepTeal / AMOLED). */
    private val lightSurface = Color(0xFFF1F3F4)
    private val darkSurface = Color(0xFF111A1D)
    private val amoledSurface = Color.Black

    // ---------------------------------------------------------------- hex parsing

    @Test
    fun `parses six digit hex with and without hash`() {
        assertEquals(Color(0xFFFF1744), ColorPickerConversions.parseHexColor("#FF1744"))
        assertEquals(Color(0xFFFF1744), ColorPickerConversions.parseHexColor("FF1744"))
        assertEquals(Color(0xFFFF1744), ColorPickerConversions.parseHexColor("0xFF1744"))
        assertEquals(Color(0xFFFF1744), ColorPickerConversions.parseHexColor("  #ff1744  "))
    }

    @Test
    fun `parses three digit shorthand by doubling each digit`() {
        assertEquals(Color(0xFF44AAFF), ColorPickerConversions.parseHexColor("#4AF"))
        assertEquals(Color(0xFF000000), ColorPickerConversions.parseHexColor("000"))
        assertEquals(Color(0xFFFFFFFF), ColorPickerConversions.parseHexColor("fff"))
    }

    @Test
    fun `rejects malformed input instead of inventing a colour`() {
        // Empty / partial.
        assertNull(ColorPickerConversions.parseHexColor(""))
        assertNull(ColorPickerConversions.parseHexColor("#"))
        assertNull(ColorPickerConversions.parseHexColor("FF17"))
        assertNull(ColorPickerConversions.parseHexColor("FF17444"))
        // Non-hex digits.
        assertNull(ColorPickerConversions.parseHexColor("GGGGGG"))
        assertNull(ColorPickerConversions.parseHexColor("rebecca"))
        assertNull(ColorPickerConversions.parseHexColor("#12 345"))
        // The signed-number hole toIntOrNull(16) left open.
        assertNull(ColorPickerConversions.parseHexColor("-FFFFF"))
        assertNull(ColorPickerConversions.parseHexColor("+FFFFF"))
    }

    @Test
    fun `hex round trips through the formatter`() {
        val hex = ColorPickerConversions.colorToHex(Color(0xFF2979FF))
        assertEquals("2979FF", hex)
        assertEquals(Color(0xFF2979FF), ColorPickerConversions.parseHexColor(hex))
    }

    // ---------------------------------------------------------------- contrast safety

    @Test
    fun `near black stays readable on every dark surface`() {
        for (surface in listOf(darkSurface, amoledSurface)) {
            val clamped = ensureLegibleOn(Color(0xFF000000), surface)
            assertTrue(
                "pure black on $surface produced ${contrastRatio(clamped, surface)}",
                contrastRatio(clamped, surface) >= MIN_ACCENT_CONTRAST,
            )
        }
    }

    @Test
    fun `near white stays readable on the light surface`() {
        val clamped = ensureLegibleOn(Color(0xFFFFFFFF), lightSurface)
        assertTrue(
            "pure white produced ${contrastRatio(clamped, lightSurface)}",
            contrastRatio(clamped, lightSurface) >= MIN_ACCENT_CONTRAST,
        )
    }

    @Test
    fun `the clamp moves brightness only, never hue or saturation`() {
        // Pure yellow is the worst case on a light surface: maximum saturation, almost no contrast.
        val yellow = Color(0xFFFFEA00)
        val clamped = ensureLegibleOn(yellow, lightSurface)

        val (originalHue, originalSaturation, _) = ColorPickerConversions.colorToHsv(yellow)
        val (clampedHue, clampedSaturation, _) = ColorPickerConversions.colorToHsv(clamped)

        assertEquals(originalHue, clampedHue, 1f)
        assertEquals(originalSaturation, clampedSaturation, 0.02f)
        assertTrue(
            "clamped yellow only reached ${contrastRatio(clamped, lightSurface)}",
            contrastRatio(clamped, lightSurface) >= MIN_ACCENT_CONTRAST,
        )
    }

    @Test
    fun `an already legible colour is returned untouched`() {
        val deepBlue = Color(0xFF0A2352)
        assertEquals(deepBlue, ensureLegibleOn(deepBlue, lightSurface))
    }

    @Test
    fun `every preset and both extremes survive both themes`() {
        val candidates = listOf(
            0xFF000000, 0xFFFFFFFF, 0xFFFF1744, 0xFFFFEA00, 0xFFC6FF00,
            0xFF00E5FF, 0xFF651FFF, 0xFFF2EDE3, 0xFF23272B, 0xFF0C5227,
        ).map { Color(it) }

        for (surface in listOf(lightSurface, darkSurface, amoledSurface)) {
            for (candidate in candidates) {
                val accent = ensureLegibleOn(candidate, surface)
                assertTrue(
                    "accent $candidate on $surface only reached ${contrastRatio(accent, surface)}",
                    contrastRatio(accent, surface) >= MIN_ACCENT_CONTRAST,
                )
                // And the text drawn ON that accent must itself be readable.
                assertTrue(
                    "content on $accent only reached ${contrastRatio(onColorFor(accent), accent)}",
                    contrastRatio(onColorFor(accent), accent) >= 3f,
                )
            }
        }
    }

    @Test
    fun `on-colour flips with luminance`() {
        assertEquals(Color.White, onColorFor(Color.Black))
        assertEquals(Color(0xFF1B1B1B), onColorFor(Color.White))
    }

    // ---------------------------------------------------------------- vividness

    @Test
    fun `soft mode is a no-op so nobody's theme moves on update`() {
        val scheme = androidx.compose.material3.lightColorScheme()
        val untouched = scheme.withAccent(Color(0xFFFF1744), AccentVividness.SOFT)
        assertEquals(scheme.primary, untouched.primary)
        assertEquals(scheme.secondary, untouched.secondary)
        assertEquals(scheme.primaryContainer, untouched.primaryContainer)
    }

    @Test
    fun `vivid mode restores the saturation the tonal generator discarded`() {
        val scheme = androidx.compose.material3.lightColorScheme()
        // A fully saturated seed. TonalSpot would have re-derived it at a fixed chroma; withAccent
        // must hand back something at least as saturated as what the user actually chose.
        val seed = Color(0xFFFF1744)
        val (seedHue, seedSaturation, _) = ColorPickerConversions.colorToHsv(seed)

        val vivid = scheme.withAccent(seed, AccentVividness.VIVID, lightSurface)
        val (primaryHue, primarySaturation, _) = ColorPickerConversions.colorToHsv(vivid.primary)

        assertEquals(seedHue, primaryHue, 2f)
        assertEquals(seedSaturation, primarySaturation, 0.05f)

        val (_, baseSaturation, _) = ColorPickerConversions.colorToHsv(scheme.primary)
        assertTrue(
            "vivid primary ($primarySaturation) is no more saturated than the stock scheme ($baseSaturation)",
            primarySaturation > baseSaturation,
        )
    }

    @Test
    fun `exact mode hands back the literal colour when it is already readable`() {
        val scheme = androidx.compose.material3.lightColorScheme()
        val seed = Color(0xFF7F0000)
        val exact = scheme.withAccent(seed, AccentVividness.EXACT, lightSurface)
        assertEquals(seed, exact.primary)
        assertNotNull(exact.onPrimary)
        assertEquals(onColorFor(seed), exact.onPrimary)
    }
}
