package iad1tya.echo.music.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
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
    fun `soft mode still carries the seed instead of being a no-op`() {
        // SOFT used to return the scheme untouched. Since SOFT is the default, that meant the bypass
        // never ran for anyone and all 42 swatches collapsed back to TonalSpot's fixed-chroma pastel —
        // "I pick a colour and nothing changes". It is now the gentlest of three real settings.
        val scheme = androidx.compose.material3.lightColorScheme()
        val seed = Color(0xFFFF1744)
        val (seedHue, seedSaturation, _) = ColorPickerConversions.colorToHsv(seed)

        val soft = scheme.withAccent(seed, AccentVividness.SOFT, lightSurface)
        val (softHue, softSaturation, _) = ColorPickerConversions.colorToHsv(soft.primary)

        assertEquals("SOFT dropped the seed's hue", seedHue, softHue, 2f)
        assertTrue("SOFT is still a no-op", soft.primary != scheme.primary)
        assertTrue(
            "SOFT ($softSaturation) kept none of the seed's saturation",
            softSaturation > 0.3f,
        )
        assertTrue(
            "SOFT ($softSaturation) is not gentler than the seed ($seedSaturation)",
            softSaturation < seedSaturation,
        )
    }

    @Test
    fun `the three intensities are ordered and distinct`() {
        // The user must be able to tell them apart, or the control is decoration.
        val scheme = androidx.compose.material3.lightColorScheme()
        val seed = Color(0xFF00E5FF)

        val soft = scheme.withAccent(seed, AccentVividness.SOFT, lightSurface).primary
        val vivid = scheme.withAccent(seed, AccentVividness.VIVID, lightSurface).primary

        val (_, softSaturation, _) = ColorPickerConversions.colorToHsv(soft)
        val (_, vividSaturation, _) = ColorPickerConversions.colorToHsv(vivid)
        assertTrue(
            "SOFT ($softSaturation) is not gentler than VIVID ($vividSaturation)",
            softSaturation < vividSaturation,
        )
    }

    @Test
    fun `two different swatches produce two clearly different accents at the default intensity`() {
        // The actual complaint, pinned: at the DEFAULT intensity, picking red and picking green must
        // not land on near-identical colours.
        val scheme = androidx.compose.material3.lightColorScheme()
        val red = scheme.withAccent(Color(0xFFFF1744), AccentVividness.SOFT, lightSurface).primary
        val green = scheme.withAccent(Color(0xFF00C853), AccentVividness.SOFT, lightSurface).primary

        val distance = kotlin.math.abs(red.red - green.red) +
            kotlin.math.abs(red.green - green.green) +
            kotlin.math.abs(red.blue - green.blue)
        assertTrue("red and green only differ by $distance", distance > 0.5f)
    }

    // ---------------------------------------------------------------- accent seed / sentinel

    @Test
    fun `wallpaper and preset schemes are never re-tinted`() {
        // The contract that used to be spelled "force SOFT", back when SOFT was a no-op.
        assertNull(accentSeedFor(Color(0xFFFF1744), usingSystemDynamicColor = true, hasPreset = false))
        assertNull(accentSeedFor(Color(0xFFFF1744), usingSystemDynamicColor = false, hasPreset = true))
        assertEquals(
            Color(0xFFFF1744),
            accentSeedFor(Color(0xFFFF1744), usingSystemDynamicColor = false, hasPreset = false),
        )
    }

    @Test
    fun `a deliberate pick can never be stored as the no-accent sentinel`() {
        // Picking this exact red used to read back as "the user picked nothing", which silently turned
        // wallpaper/artwork colour ON instead of applying the red.
        val nudged = DefaultThemeColor.distinctFromNoAccentSentinel()
        assertTrue("the sentinel survived the nudge", nudged != DefaultThemeColor)
        // ...and it must still be the same colour to the eye: one step of one 8-bit channel. The
        // tolerance is deliberately looser than 1/255 so the assertion tests "indistinguishable"
        // rather than float rounding.
        assertEquals(DefaultThemeColor.red, nudged.red, 0.01f)
        assertEquals(DefaultThemeColor.green, nudged.green, 0.01f)
        assertEquals(DefaultThemeColor.blue, nudged.blue, 0.01f)
        assertTrue(
            "the nudge is visible (${contrastRatio(DefaultThemeColor, nudged)})",
            contrastRatio(DefaultThemeColor, nudged) < 1.02f,
        )
        // Every other colour is handed back untouched.
        for (other in listOf(Color(0xFFEC5464), Color(0xFF00E5FF), Color.Black, Color.White)) {
            assertEquals(other, other.distinctFromNoAccentSentinel())
        }
    }

    // ---------------------------------------------------------------- surfaces

    @Test
    fun `the surface tint moves with the seed but keeps the ground near black`() {
        val ground = Color(0xFF111A1D)
        val redRoom = surfaceRetint(Color(0xFFFF1744), gain = 1f, maxSaturation = 1f)(ground)
        val cyanRoom = surfaceRetint(Color(0xFF00E5FF), gain = 1f, maxSaturation = 1f)(ground)

        assertTrue("the ground did not move with the seed", redRoom != cyanRoom)

        // Every tinted ground keeps the shipped one's DARKNESS exactly — only the hue moved.
        val (_, _, groundValue) = ColorPickerConversions.colorToHsv(ground)
        for (room in listOf(redRoom, cyanRoom)) {
            val (_, _, roomValue) = ColorPickerConversions.colorToHsv(room)
            assertEquals("$room is not as dark as the shipped ground", groundValue, roomValue, 0.01f)
            assertTrue("$room is not near-black (luminance ${room.luminance()})", room.luminance() < 0.05f)
        }
    }

    @Test
    fun `a grey seed leaves the surfaces alone instead of inventing a hue`() {
        // A pure grey has no meaningful hue (it rounds to 0 == red); tinting the whole app red from it
        // would be inventing a colour the user never chose.
        val ground = Color(0xFF111A1D)
        assertEquals(ground, surfaceRetint(Color(0xFF757575), gain = 1f, maxSaturation = 1f)(ground))
        assertEquals(ground, surfaceRetint(null, gain = 1f, maxSaturation = 1f)(ground))
    }

    @Test
    fun `the light ladder stays paper-like however saturated the seed is`() {
        val page = Color(0xFFF1F3F4)
        for (seed in listOf(Color(0xFFFF1744), Color(0xFF00E5FF), Color(0xFFFFEA00))) {
            val tinted = surfaceRetint(seed, gain = 3f, maxSaturation = 0.08f)(page)
            val (_, saturation, value) = ColorPickerConversions.colorToHsv(tinted)
            assertTrue("light page reached saturation $saturation", saturation <= 0.08f)
            assertTrue("light page darkened to $value", value > 0.9f)
        }
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

    // ------------------------------------------- the canvas follows the pick, never the artwork

    /** Base scheme for the surface tests. Both ladders overwrite every field they read, so it is inert. */
    private val base = androidx.compose.material3.darkColorScheme()

    /** Every surface [ColorScheme.deepTeal] substitutes, as (name, literal, tinted). */
    private fun darkLadder(seed: Color?) = base.deepTeal(seed).let { s ->
        listOf(
            "background" to s.background,
            "surface" to s.surface,
            "surfaceDim" to s.surfaceDim,
            "surfaceBright" to s.surfaceBright,
            "surfaceContainerLowest" to s.surfaceContainerLowest,
            "surfaceContainerLow" to s.surfaceContainerLow,
            "surfaceContainer" to s.surfaceContainer,
            "surfaceContainerHigh" to s.surfaceContainerHigh,
            "surfaceContainerHighest" to s.surfaceContainerHighest,
            "surfaceVariant" to s.surfaceVariant,
        )
    }

    @Test
    fun `the no-accent sentinel is not a seed, so the ground stays on its shipped literals`() {
        // The sentinel is a real colour (#ED5564). Handed to the surfaces it painted every device
        // without Material You — anything below Android 12, where nothing else forces the seed to
        // null — a permanent maroon. It can only ever mean "the user has not picked an accent".
        assertNull(surfaceSeedFor(DefaultThemeColor))
        // ...and so does having no stored accent at all (the crash screen, previews).
        assertNull(surfaceSeedFor(null))

        assertEquals(darkSurface, base.deepTeal(surfaceSeedFor(DefaultThemeColor)).surface)
        assertEquals(darkSurface, base.deepTeal(surfaceSeedFor(DefaultThemeColor)).background)
        assertEquals(lightSurface, base.softLight(surfaceSeedFor(DefaultThemeColor)).surface)
        assertEquals(lightSurface, base.softLight(surfaceSeedFor(DefaultThemeColor)).background)
    }

    @Test
    fun `the artwork colour never reaches the surfaces`() {
        // THE DEFECT: the surfaces used to share the ACCENT seed, which under the dynamic theme is the
        // current cover's colour. A red cover turned the home background, every card, the library
        // ground, the settings sheet, the nav bar and the mini player dark maroon; the next track,
        // purple cover, dark violet. Nobody asked for that.
        //
        // With the dynamic theme on, the STORED accent is the sentinel (ThemeScreen only writes a real
        // colour when the pick turns dynamic OFF), so no cover can move the canvas.
        val storedWhileDynamic = DefaultThemeColor
        val covers = listOf(Color(0xFFB71C1C), Color(0xFF651FFF), Color(0xFF00C853), Color(0xFFFFEA00))

        val literals = darkLadder(null)
        for (cover in covers) {
            // The accent roles DO follow the artwork — that half is deliberate and unchanged.
            assertEquals(
                cover,
                accentSeedFor(cover, usingSystemDynamicColor = false, hasPreset = false),
            )
            // The canvas does not: the surface seed is not even a function of the cover.
            for ((literal, room) in literals.zip(darkLadder(surfaceSeedFor(storedWhileDynamic)))) {
                assertEquals("${literal.first} moved with the cover $cover", literal.second, room.second)
            }
            assertEquals(
                lightSurface,
                base.softLight(surfaceSeedFor(storedWhileDynamic)).background,
            )
        }
    }

    @Test
    fun `an explicit swatch does repaint the whole canvas`() {
        // The owner's ORIGINAL complaint, which this release exists to fix: picking one of the 42
        // swatches must move the ground itself, not just a few accented details. Splitting the seed
        // must not quietly give that back.
        val pick = Color(0xFFFF1744)
        assertEquals(pick, surfaceSeedFor(pick))

        val (seedHue, seedSaturation, _) = ColorPickerConversions.colorToHsv(pick)
        val literals = darkLadder(null)
        val tinted = darkLadder(surfaceSeedFor(pick))

        for ((literalEntry, tintedEntry) in literals.zip(tinted)) {
            val (name, literal) = literalEntry
            val room = tintedEntry.second
            assertTrue("$name did not move with the pick", room != literal)

            val (_, literalSaturation, literalValue) = ColorPickerConversions.colorToHsv(literal)
            val (roomHue, roomSaturation, roomValue) = ColorPickerConversions.colorToHsv(room)
            // Only the HUE is replaced. The tolerance is 3 deg because these grounds are near-black:
            // their max-min channel spread is ~11/255, so one 8-bit rounding step IS several degrees.
            assertEquals("$name did not take the pick's hue", seedHue, roomHue, 3f)
            // ...each literal keeps its OWN step on the depth ladder...
            assertEquals("$name changed darkness", literalValue, roomValue, 0.01f)
            // ...and its OWN amount of tint, scaled by how saturated the pick itself is.
            assertEquals(
                "$name changed how much tint it carries",
                literalSaturation * seedSaturation,
                roomSaturation,
                0.02f,
            )
        }

        // The light ladder lifts to a perceptible wash but stays paper-like.
        val page = base.softLight(surfaceSeedFor(pick))
        assertTrue("the light page did not move", page.background != lightSurface)
        val (pageHue, pageSaturation, pageValue) = ColorPickerConversions.colorToHsv(page.background)
        // 8 deg: the light page is a near-neutral by design (channel spread ~8/255 after tinting), so
        // a single 8-bit rounding step is worth ~7.5 deg of hue here. The hue is still unmistakably the
        // pick's and not the shipped cool-grey's, which sits ~200 deg away.
        assertEquals(seedHue, pageHue, 8f)
        assertTrue("the light page reached saturation $pageSaturation", pageSaturation <= 0.08f)
        assertTrue("the light page darkened to $pageValue", pageValue > 0.9f)

        // Typing the sentinel's own hex is a real pick too: it is STORED nudged, so it seeds the room.
        val deliberateSentinel = DefaultThemeColor.distinctFromNoAccentSentinel()
        assertEquals(deliberateSentinel, surfaceSeedFor(deliberateSentinel))
    }

    @Test
    fun `AMOLED and the named presets take no surface tint whatever is picked`() {
        val pick = Color(0xFFFF1744)

        // AMOLED must stay TRULY black. echomusicTheme's `darkTheme && pureBlack` branch is taken
        // BEFORE the deep-teal substitution, so no seed can ever reach it — pinned by the fact that
        // pureBlack has no seed to take.
        val amoled = base.pureBlack(true)
        assertEquals(Color.Black, amoled.surface)
        assertEquals(Color.Black, amoled.background)

        // A named preset ships its own finished ground; echomusicTheme's `baseColorScheme ===
        // presetScheme` branch skips both ladders. If it ever stops doing so, the preset's surface and
        // the seeded generic one differ loudly rather than silently.
        assertTrue(
            "the seeded generic ground equals the preset's own — this test is now blind",
            MuestreoDarkColorScheme.surface != MuestreoDarkColorScheme.deepTeal(pick).surface,
        )
    }
}
