package iad1tya.echo.music.ui.newui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import iad1tya.echo.music.constants.PlayerBackgroundStyle
import iad1tya.echo.music.ui.screens.settings.PaletteColors
import iad1tya.echo.music.ui.theme.contrastRatio
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The promises the redesign's Apariencia work makes, pinned.
 *
 * Three of them cannot be seen in a screenshot and none of them can be seen in a type signature:
 *
 *  1. **The accent reaches the new screens, and stays readable.** ~143 call sites read
 *     `AuraPalette.Teal` / `.Blue` / `.Violet`; those now follow the user's accent, and a swatch chosen
 *     for its colour must never produce text nobody can read on the ground.
 *  2. **All seven `PlayerBackgroundStyle` values do something, and something different.** Six of them
 *     used to be inert in the new portrait player — including the one `App.kt` seeds.
 *  3. **With nothing chosen, everything is the literal that shipped.** That is what makes the whole
 *     thing reversible.
 */
class AuraAppearanceTest {

    /** Every test that writes global palette state must hand it back, or it leaks into the next one. */
    @After
    fun restorePalette() {
        AuraPalette.reset()
    }

    // ------------------------------------------------------------------ 1. the accent

    @Test
    fun `every one of the 46 swatches produces a legible trio on the redesign's ground`() {
        val ground = AuraPalette.Ground
        PaletteColors.forEach { palette ->
            // The Dynamic entry is a sentinel (transparent), not a colour; the accent it stands for
            // arrives through colorScheme.primary like any other.
            if (palette.seedColor == Color.Transparent) return@forEach
            val accent = AuraAccent.from(palette.seedColor, ground)
            listOf(
                "primary" to accent.primary,
                "secondary" to accent.secondary,
                "tertiary" to accent.tertiary,
            ).forEach { (role, color) ->
                val ratio = contrastRatio(color, ground)
                assertTrue(
                    "swatch ${palette.seedColor} -> $role ${color} reads only $ratio:1 on the ground",
                    ratio >= TEXT_AA,
                )
            }
        }
    }

    @Test
    fun `the same holds on AMOLED's pure black, which is a different ground`() {
        PaletteColors.forEach { palette ->
            if (palette.seedColor == Color.Transparent) return@forEach
            val accent = AuraAccent.from(palette.seedColor, Color.Black)
            assertTrue(
                "swatch ${palette.seedColor} is unreadable on pure black",
                contrastRatio(accent.primary, Color.Black) >= TEXT_AA &&
                    contrastRatio(accent.secondary, Color.Black) >= TEXT_AA &&
                    contrastRatio(accent.tertiary, Color.Black) >= TEXT_AA,
            )
        }
    }

    @Test
    fun `the darkest and the lightest swatches are the ones that prove the clamp works`() {
        val ground = AuraPalette.Ground
        // Rojo oscuro: too dark to read on a near-black ground as picked, so it must be LIFTED…
        val darkRed = Color(0xFF7F0000)
        assertTrue(contrastRatio(darkRed, ground) < TEXT_AA)
        assertTrue(contrastRatio(AuraAccent.from(darkRed, ground).primary, ground) >= TEXT_AA)
        // …and Marfil already passes, so it must be handed back UNTOUCHED rather than "corrected".
        val ivory = Color(0xFFF2EDE3)
        assertEquals(ivory, AuraAccent.from(ivory, ground).primary)
    }

    @Test
    fun `the trio keeps the render's hue spacing, so a gradient is still a gradient`() {
        val ground = AuraPalette.Ground
        // A saturated pick: the three stops must be three different colours, or the play button's
        // gradient collapses to a flat fill.
        val accent = AuraAccent.from(Color(0xFF1E88E5), ground)
        assertNotEquals(accent.primary, accent.secondary)
        assertNotEquals(accent.secondary, accent.tertiary)
    }

    @Test
    fun `the nav bar's unselected tint is not a term of the accent, so no swatch can dim it`() {
        // It is a step of OnGround, and the guarantee is that it clears AA on both grounds.
        assertTrue(contrastRatio(AuraPalette.NavInactive, AuraPalette.Ground) >= TEXT_AA)
        AuraPalette.apply(
            AuraAccent.from(Color(0xFFF2EDE3), Color.Black),
            pureBlack = true,
            coverCorners = AuraCoverCorners.Render,
        )
        assertEquals(Color.Black, AuraPalette.Ground)
        assertTrue(contrastRatio(AuraPalette.NavInactive, AuraPalette.Ground) >= TEXT_AA)
    }

    // ------------------------------------------------------------------ 2. AMOLED and the corners

    @Test
    fun `AMOLED moves the redesign's own ground, not only the app ColorScheme`() {
        assertEquals(Color(0xFF060A12), AuraPalette.Ground)
        AuraPalette.apply(AuraAccent.Brand, pureBlack = true, coverCorners = AuraCoverCorners.Render)
        assertEquals(Color.Black, AuraPalette.Ground)
        // The raised ground must NOT collapse into it, or the mini pill and the player menu sheet
        // disappear into the page.
        assertNotEquals(Color.Black, AuraPalette.GroundRaised)
        assertTrue(AuraPalette.GroundRaised.luminance() < 0.01f)
    }

    @Test
    fun `the cover corner control is the render's radius scaled, identity at the shipped default`() {
        // The shipped default of ThumbnailCornerRadiusKey is 3, which AuraPaletteSync turns into a
        // multiplier of 1 — so a user who never touched the control gets the render's own radii.
        assertEquals(1f, AuraCoverCorners.Render.scale, 0f)
        assertEquals(RoundedCornerShape(11.dp), AuraCoverCorners.Render.row)
        assertEquals(RoundedCornerShape(20.dp), AuraCoverCorners.Render.player)
        // 0 is square and anything above 1 is rounder: the control is live and monotonic, not a switch
        // between two looks.
        assertEquals(RoundedCornerShape(0.dp), AuraCoverCorners(0f).row)
        assertNotEquals(AuraCoverCorners(4f).row, AuraCoverCorners.Render.row)
    }

    // ------------------------------------------------------------------ 3. the seven grounds

    @Test
    fun `no PlayerBackgroundStyle draws nothing`() {
        PlayerBackgroundStyle.entries.forEach { style ->
            val recipe = auraGroundRecipe(style, hasCover = true, motion = true)
            assertTrue(
                "$style draws no ground at all",
                recipe.bloom > 0f || recipe.cover > 0f || recipe.wash > 0f ||
                    recipe.lobes > 0f || recipe.film > 0f,
            )
        }
    }

    @Test
    fun `no two styles draw the same ground`() {
        val recipes = PlayerBackgroundStyle.entries.associateWith {
            auraGroundRecipe(it, hasCover = true, motion = true)
        }
        val distinct = recipes.values.distinct()
        assertEquals(
            "two styles resolve to the same recipe: $recipes",
            PlayerBackgroundStyle.entries.size,
            distinct.size,
        )
    }

    @Test
    fun `losing the cover never leaves a style empty`() {
        // No artwork (a local track), or API below 31 where Modifier-blur is a no-op.
        PlayerBackgroundStyle.entries.forEach { style ->
            val recipe = auraGroundRecipe(style, hasCover = false, motion = true)
            assertEquals("$style still asks for a cover it cannot have", 0f, recipe.cover, 0f)
            assertTrue(
                "$style degrades to an empty ground",
                recipe.bloom > 0f || recipe.wash > 0f || recipe.lobes > 0f || recipe.film > 0f,
            )
        }
    }

    @Test
    fun `denying motion stops every animation and leaves the ground standing`() {
        PlayerBackgroundStyle.entries.forEach { style ->
            val still = auraGroundRecipe(style, hasCover = true, motion = false)
            assertTrue("$style still drifts under the thermal gate", !still.drift)
            assertTrue("$style still spins under the thermal gate", !still.spin)
            assertTrue(
                "$style has nothing left once its motion is denied",
                still.bloom > 0f || still.cover > 0f || still.wash > 0f ||
                    still.lobes > 0f || still.film > 0f,
            )
        }
    }

    @Test
    fun `DEFAULT is exactly the ambient bloom the redesign shipped`() {
        val recipe = auraGroundRecipe(PlayerBackgroundStyle.DEFAULT, hasCover = true, motion = true)
        assertEquals(1f, recipe.bloom, 0f)
        assertEquals(0f, recipe.cover, 0f)
        assertEquals(0f, recipe.wash, 0f)
        assertEquals(0f, recipe.lobes, 0f)
        assertEquals(0f, recipe.film, 0f)
    }

    @Test
    fun `the flat artwork ceiling is the redesign's own hairline, not an invented number`() {
        // The brightest imaginable cover, at the ceiling, over the ground.
        val worstCase = Color.White.copy(alpha = AURA_COVER_ALPHA).compositeOver(AuraPalette.Ground)
        // …is exactly the surface the app theme already calls surfaceContainerHigh and prints text on.
        val shippedCeiling = AuraPalette.SurfaceLine.compositeOver(AuraPalette.Ground)
        assertEquals(shippedCeiling.luminance(), worstCase.luminance(), 1e-6f)
        // And the redesign's ink still clears AA on it, at the two steps the player uses.
        listOf(AuraPalette.OnGroundMuted, AuraPalette.OnGroundFaint).forEach { ink ->
            val composited = ink.compositeOver(worstCase)
            assertTrue(
                "ink $ink reads only ${contrastRatio(composited, worstCase)}:1 on the worst-case ground",
                contrastRatio(composited, worstCase) >= TEXT_AA,
            )
        }
    }

    // --------------------------------------------- 3b. the mini player's OWN background control

    /**
     * The five values `AppearanceSettings` offers for "Estilo de fondo del minirreproductor"
     * (`availableMiniPlayerBackgroundStyles` = every style except GRADIENT and APPLE_MUSIC).
     *
     * The row was hidden under the new UI because the key had no renderer; `AuraMiniPlayer` now reads
     * it. These tests are what stops "restored" from meaning "visible again but still inert".
     */
    private val miniPlayerOfferedStyles = PlayerBackgroundStyle.entries.filter {
        it != PlayerBackgroundStyle.GRADIENT && it != PlayerBackgroundStyle.APPLE_MUSIC
    }

    @Test
    fun `Predeterminado is the theme's own ground on the pill, and Desenfoque is the cover`() {
        // The blocker this replaces: both values resolved to a full-strength blurred cover 4 dp of blur
        // apart on a 128x128 decode inside a 64 dp pill, i.e. "Desenfoque" did nothing. Each name now
        // means what it says, and THIS is the assertion that would have caught it.
        val default = auraPillRecipe(
            auraGroundRecipe(PlayerBackgroundStyle.DEFAULT, hasCover = true, motion = true),
        )
        // "Seguir el tema": no artwork layer at all. The pill is [AuraPalette.GroundRaised] (which the
        // AMOLED switch moves) plus the content Row's own SurfaceFill — the flat `.mi` the render draws.
        assertEquals(AuraGroundRecipe(), default)

        val blur = auraPillRecipe(
            auraGroundRecipe(PlayerBackgroundStyle.BLUR, hasCover = true, motion = true),
        )
        // "Desenfoque": the blurred cover, at the full strength the pill's own scrim is sized for.
        assertEquals(1f, blur.cover, 0f)
        assertNotEquals(default, blur)
    }

    @Test
    fun `the pill's ink clears AA on the ground Predeterminado draws, on both themes`() {
        // The ground under the pill's text when no artwork layer is drawn: the opaque raised ground with
        // the content Row's SurfaceFill over it (AuraShell.kt). No cover means `pillHasArtwork` is false,
        // so the 45 % scrim is not drawn either — this really is the whole stack.
        fun assertLegible(where: String) {
            val ground = AuraPalette.SurfaceFill.compositeOver(AuraPalette.GroundRaised)
            val title = contrastRatio(AuraPalette.OnGround, ground)
            assertTrue("the pill's title reads only $title:1 on $where", title >= TEXT_AA)
            // The artist line is the 55 % step, so it is the one that decides this.
            val artist = contrastRatio(AuraPalette.OnGroundMuted.compositeOver(ground), ground)
            assertTrue("the pill's artist line reads only $artist:1 on $where", artist >= TEXT_AA)
        }
        assertLegible("the brand ground")
        AuraPalette.apply(AuraAccent.Brand, pureBlack = true, coverCorners = AuraCoverCorners.Render)
        assertLegible("AMOLED")
    }

    @Test
    fun `no mini-player style draws the same layers as Predeterminado on the pill`() {
        val default = auraPillRecipe(
            auraGroundRecipe(PlayerBackgroundStyle.DEFAULT, hasCover = true, motion = true),
        )
        // A style counts as restored only if it changes the LAYERS. `coverBlur` is deliberately NOT in
        // this disjunction: a different blur radius on the same layer is exactly the 4 dp placebo.
        miniPlayerOfferedStyles.filter { it != PlayerBackgroundStyle.DEFAULT }.forEach { style ->
            val pill = auraPillRecipe(auraGroundRecipe(style, hasCover = true, motion = true))
            assertTrue(
                "$style draws the same layers as Predeterminado on the pill — it would be a placebo",
                pill.cover != default.cover ||
                    pill.lobes != default.lobes ||
                    pill.film != default.film ||
                    pill.wash != default.wash ||
                    pill.coverSaturation != default.coverSaturation ||
                    pill.coverScale != default.coverScale ||
                    pill.spin != default.spin,
            )
        }
    }

    @Test
    fun `LIQUID_GLASS on the pill is a frosted cover, never a live backdrop sample`() {
        // The claim the restored settings row makes out loud, in both languages. A backdrop sample
        // would have to be a per-frame effect; this recipe is a still cover plus a cached film, and it
        // still resolves to something when there is no cover to blur (API < 31, or a local track).
        val withCover = auraPillRecipe(
            auraGroundRecipe(PlayerBackgroundStyle.LIQUID_GLASS, hasCover = true, motion = true),
        )
        assertTrue("the frosted film is missing", withCover.film > 0f)
        assertTrue("nothing is being frosted", withCover.cover > 0f)
        assertTrue("a still ground must not drift", !withCover.drift && !withCover.spin)

        val withoutCover = auraPillRecipe(
            auraGroundRecipe(PlayerBackgroundStyle.LIQUID_GLASS, hasCover = false, motion = true),
        )
        assertEquals("asks for a cover it cannot have", 0f, withoutCover.cover, 0f)
        assertTrue("degrades to an empty pill", withoutCover.film > 0f || withoutCover.wash > 0f)
        // Motion is denied on a throttled device; this style never had any to lose.
        val throttled = auraPillRecipe(
            auraGroundRecipe(PlayerBackgroundStyle.LIQUID_GLASS, hasCover = true, motion = false),
        )
        assertEquals(withCover, throttled)
    }

    // ------------------------------------------------------------------ 4. reversibility

    @Test
    fun `with nothing resolved the palette is the literal that shipped`() {
        AuraPalette.reset()
        assertEquals(Color(0xFF3FE7CE), AuraPalette.Teal)
        assertEquals(Color(0xFF2FA6F0), AuraPalette.Blue)
        assertEquals(Color(0xFF6A5BFF), AuraPalette.Violet)
        assertEquals(Color(0xFF060A12), AuraPalette.Ground)
        assertEquals(Color(0xFF080D18), AuraPalette.GroundRaised)
        assertEquals(1f, AuraPalette.coverCorners.scale, 0f)
        // The bloom's fallback is built from those, so it comes back with them.
        assertEquals(AuraPalette.Teal.copy(alpha = 0.32f), AuraBloomColors.Brand.topLeft)
    }

    companion object {
        /**
         * WCAG 2.1 body text. The accent carries 11–12 sp technical type in this UI ("◆ HI-RES", the
         * codec chips, "SONANDO", the queue's radio caption), so 4.5 is the bar — not the 3.0 the
         * theme's own `MIN_ACCENT_CONTRAST` uses for large text and graphical objects.
         */
        private const val TEXT_AA = 4.5f
    }
}
