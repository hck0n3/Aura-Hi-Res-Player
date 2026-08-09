package iad1tya.echo.music.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import iad1tya.echo.music.ui.newui.AuraAccent
import iad1tya.echo.music.ui.newui.AuraCoverCorners
import iad1tya.echo.music.ui.newui.AuraPalette
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Pins Tema ▸ Personalizar roles: 0/null = no-op, overrides paint the role, AMOLED ignores custom Fondo.
 */
class CustomThemeRolesTest {

    private val base = darkColorScheme()

    @Before
    fun setUp() {
        AuraPalette.reset()
    }

    @After
    fun tearDown() {
        AuraPalette.reset()
    }

    @Test
    fun fromArgb_zeroMeansNull() {
        val roles = CustomThemeRoles.fromArgb(
            background = CustomThemeRoles.AUTO_ARGB,
            surface = 0xFF112233.toInt(),
        )
        assertNull(roles.background)
        assertEquals(Color(0xFF112233), roles.surface)
        assertTrue(!roles.isEmpty)
    }

    @Test
    fun applyCustomRoles_noneIsIdentity() {
        val out = base.applyCustomRoles(CustomThemeRoles.None, pureBlack = false)
        assertSame(base, out)
    }

    @Test
    fun applyCustomRoles_backgroundAndText() {
        val bg = Color(0xFF102030)
        val ink = Color(0xFFE8F0FF)
        val roles = CustomThemeRoles(background = bg, onBackground = ink)
        val out = base.applyCustomRoles(roles, pureBlack = false)
        assertEquals(bg, out.background)
        assertEquals(bg, out.surface) // surface follows background when surface still auto
        assertEquals(ink, out.onBackground)
        assertEquals(ink, out.onSurface)
    }

    @Test
    fun applyCustomRoles_amoledIgnoresCustomBackground() {
        val customBg = Color(0xFF334455)
        val roles = CustomThemeRoles(background = customBg, outline = Color(0xFFFF00AA))
        val blackBase = base.copy(background = Color.Black, surface = Color.Black)
        val out = blackBase.applyCustomRoles(roles, pureBlack = true)
        assertEquals(Color.Black, out.background)
        assertNotEquals(customBg, out.background)
        assertEquals(Color(0xFFFF00AA), out.outline)
    }

    @Test
    fun applyCustomRoles_surfaceIndependentOfBackground() {
        val bg = Color(0xFF101010)
        val surface = Color(0xFF202830)
        val out = base.applyCustomRoles(
            CustomThemeRoles(background = bg, surface = surface),
            pureBlack = false,
        )
        assertEquals(bg, out.background)
        assertEquals(surface, out.surface)
    }

    @Test
    fun effectiveAuraGround_amoledWins() {
        val roles = CustomThemeRoles(background = Color(0xFF123456))
        assertEquals(Color.Black, roles.effectiveAuraGround(pureBlack = true, fallback = Color.Red))
        assertEquals(Color(0xFF123456), roles.effectiveAuraGround(pureBlack = false, fallback = Color.Red))
        assertEquals(Color.Red, CustomThemeRoles.None.effectiveAuraGround(false, Color.Red))
    }

    @Test
    fun auraPalette_appliesRoleOverrides() {
        val bg = Color(0xFF0A1520)
        val ink = Color(0xFFF0F4FF)
        val line = Color(0xFF88AACC)
        val onAccent = Color(0xFF051018)
        AuraPalette.apply(
            AuraAccent.Brand,
            pureBlack = false,
            coverCorners = AuraCoverCorners.Render,
            roles = CustomThemeRoles(
                background = bg,
                onBackground = ink,
                outline = line,
                onPrimary = onAccent,
            ),
        )
        assertEquals(bg, AuraPalette.Ground)
        assertEquals(ink, AuraPalette.OnGround)
        assertEquals(line, AuraPalette.SurfaceLine)
        assertEquals(onAccent, AuraPalette.OnAccent)
    }

    @Test
    fun auraPalette_amoledWinsOverCustomBackground() {
        AuraPalette.apply(
            AuraAccent.Brand,
            pureBlack = true,
            coverCorners = AuraCoverCorners.Render,
            roles = CustomThemeRoles(background = Color(0xFF445566)),
        )
        assertEquals(Color.Black, AuraPalette.Ground)
    }

    @Test
    fun toArgbOrAuto_roundTrip() {
        assertEquals(CustomThemeRoles.AUTO_ARGB, null.toArgbOrAuto())
        val c = Color(0xFFABCDEF)
        assertEquals(c.toArgb(), c.toArgbOrAuto())
        assertEquals(c, c.toArgbOrAuto().toColorOrNull())
    }
}
