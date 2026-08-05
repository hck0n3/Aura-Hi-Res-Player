package iad1tya.echo.music.ui.player

import androidx.compose.ui.graphics.Color
import iad1tya.echo.music.constants.PlayerButtonsStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The Ajustes ▸ Apariencia controls that were placebos until [PlayerAppearancePrefs] gave them a single
 * implementation, pinned.
 *
 * Every assertion below is written so that REVERTING the fix fails it, not merely so that it passes
 * today:
 *
 *  · Button colours — the DEFAULT branch used to be inlined in the classic player only, so the new
 *    player could not reuse it. The tests pin the light-on-dark rule AND that PRIMARY / TERTIARY really
 *    take the theme colours (make them all return the same pair and the tests fail).
 *  · Volume gate — [showPlayerVolumeControl] exists because the key is stored as `hidePlayerSlider`,
 *    hides the VOLUME (not the timeline) and is labelled `hide_player_volume`. Drop the negation and
 *    the switch inverts; the test catches it.
 *  · Swipe gate — four terms, any of which a refactor can silently drop, plus a sign convention.
 */
class PlayerAppearancePrefsTest {

    // Stand-ins for the live MaterialTheme; deliberately eight DISTINCT colours so a derivation that
    // returns the wrong slot cannot accidentally match the right one.
    private val primary = Color(0xFF0A84FF)
    private val onPrimary = Color(0xFF00131F)
    private val tertiary = Color(0xFFFF9F0A)
    private val onTertiary = Color(0xFF1F1200)
    private val primaryContainer = Color(0xFF00325B)
    private val onPrimaryContainer = Color(0xFFD3E4FF)
    private val tertiaryContainer = Color(0xFF5B3A00)
    private val onTertiaryContainer = Color(0xFFFFDDB3)

    private fun colors(
        style: PlayerButtonsStyle,
        overDarkBackground: Boolean = false,
        useDarkTheme: Boolean = false,
    ) = playerButtonColors(
        style = style,
        overDarkBackground = overDarkBackground,
        useDarkTheme = useDarkTheme,
        primary = primary,
        onPrimary = onPrimary,
        tertiary = tertiary,
        onTertiary = onTertiary,
        primaryContainer = primaryContainer,
        onPrimaryContainer = onPrimaryContainer,
        tertiaryContainer = tertiaryContainer,
        onTertiaryContainer = onTertiaryContainer,
    )

    // ------------------------------------------------------------------ button colours: DEFAULT

    @Test
    fun `default style over a dark background is white on black whatever the theme says`() {
        // This is the case the new player relies on: its ground is a near-black, so DEFAULT must be
        // light-on-dark even while the app is in the light theme.
        val overDarkLightTheme = colors(
            PlayerButtonsStyle.DEFAULT,
            overDarkBackground = true,
            useDarkTheme = false,
        )
        assertEquals(Color.White, overDarkLightTheme.textButtonColor)
        assertEquals(Color.Black, overDarkLightTheme.iconButtonColor)

        val overDarkDarkTheme = colors(
            PlayerButtonsStyle.DEFAULT,
            overDarkBackground = true,
            useDarkTheme = true,
        )
        assertEquals(Color.White, overDarkDarkTheme.textButtonColor)
        assertEquals(Color.Black, overDarkDarkTheme.iconButtonColor)
    }

    @Test
    fun `default style on a light surface in a light theme flips to black on white`() {
        // The only combination that inverts. If the derivation ever hardcodes white-on-black (the shape
        // the new player happens to want), the classic light-theme player loses its contrast and this
        // fails.
        val c = colors(
            PlayerButtonsStyle.DEFAULT,
            overDarkBackground = false,
            useDarkTheme = false,
        )
        assertEquals(Color.Black, c.textButtonColor)
        assertEquals(Color.White, c.iconButtonColor)
    }

    @Test
    fun `default style in a dark theme is light on dark even without a dark background`() {
        val c = colors(
            PlayerButtonsStyle.DEFAULT,
            overDarkBackground = false,
            useDarkTheme = true,
        )
        assertEquals(Color.White, c.textButtonColor)
        assertEquals(Color.Black, c.iconButtonColor)
    }

    @Test
    fun `default side buttons are the translucent white pill, never a theme container`() {
        val c = colors(PlayerButtonsStyle.DEFAULT, overDarkBackground = true, useDarkTheme = true)
        assertEquals(Color.White.copy(alpha = 0.2f), c.sideButtonContainerColor)
        assertEquals(Color.White, c.sideButtonContentColor)
    }

    // --------------------------------------------------------- button colours: PRIMARY / TERTIARY

    @Test
    fun `primary style takes the theme primary pair and ignores the background darkness`() {
        for (overDark in listOf(true, false)) {
            for (dark in listOf(true, false)) {
                val c = colors(PlayerButtonsStyle.PRIMARY, overDark, dark)
                assertEquals(primary, c.textButtonColor)
                assertEquals(onPrimary, c.iconButtonColor)
                assertEquals(primaryContainer, c.sideButtonContainerColor)
                assertEquals(onPrimaryContainer, c.sideButtonContentColor)
            }
        }
    }

    @Test
    fun `tertiary style takes the theme tertiary pair and ignores the background darkness`() {
        for (overDark in listOf(true, false)) {
            for (dark in listOf(true, false)) {
                val c = colors(PlayerButtonsStyle.TERTIARY, overDark, dark)
                assertEquals(tertiary, c.textButtonColor)
                assertEquals(onTertiary, c.iconButtonColor)
                assertEquals(tertiaryContainer, c.sideButtonContainerColor)
                assertEquals(onTertiaryContainer, c.sideButtonContentColor)
            }
        }
    }

    @Test
    fun `the three styles are visibly different, which is the whole point of the setting`() {
        // A placebo control is one where every value renders the same. Pin that they cannot.
        val d = colors(PlayerButtonsStyle.DEFAULT, overDarkBackground = true, useDarkTheme = true)
        val p = colors(PlayerButtonsStyle.PRIMARY, overDarkBackground = true, useDarkTheme = true)
        val t = colors(PlayerButtonsStyle.TERTIARY, overDarkBackground = true, useDarkTheme = true)
        assertNotEquals(d.textButtonColor, p.textButtonColor)
        assertNotEquals(d.textButtonColor, t.textButtonColor)
        assertNotEquals(p.textButtonColor, t.textButtonColor)
    }

    @Test
    fun `every style is covered, so a new style cannot ship uncoloured`() {
        // PlayerButtonsStyle is exhaustively matched inside the derivation; adding a value without a
        // branch is a compile error there, and this keeps the test suite honest about the count.
        assertEquals(3, PlayerButtonsStyle.entries.size)
        PlayerButtonsStyle.entries.forEach { style ->
            val c = colors(style, overDarkBackground = true, useDarkTheme = true)
            assertNotEquals(c.textButtonColor, c.iconButtonColor)
        }
    }

    // ------------------------------------------------------------------------------ volume gate

    @Test
    fun `hiding the volume control hides it and nothing else shows it`() {
        assertFalse(showPlayerVolumeControl(hidePlayerVolume = true))
        assertTrue(showPlayerVolumeControl(hidePlayerVolume = false))
    }

    // ------------------------------------------------------------------------------- swipe gate

    @Test
    fun `the lyrics swipe is armed only with all four terms satisfied`() {
        assertTrue(
            swipeLyricsGestureArmed(
                swipeLyricsEnabled = true,
                lyricsVisible = true,
                lyricsFullScreen = true,
                isListenTogetherGuest = false,
            )
        )
    }

    @Test
    fun `dropping any single term disarms the lyrics swipe`() {
        // Each case is one term flipped away from the armed combination above — drop that term from the
        // gate and its case starts returning true.
        assertFalse(
            "the preference is off",
            swipeLyricsGestureArmed(false, lyricsVisible = true, lyricsFullScreen = true, isListenTogetherGuest = false)
        )
        assertFalse(
            "the lyrics are not on screen",
            swipeLyricsGestureArmed(true, lyricsVisible = false, lyricsFullScreen = true, isListenTogetherGuest = false)
        )
        assertFalse(
            "the lyrics are inline, not full screen",
            swipeLyricsGestureArmed(true, lyricsVisible = true, lyricsFullScreen = false, isListenTogetherGuest = false)
        )
        assertFalse(
            "a Listen Together guest must not be able to change the host's song",
            swipeLyricsGestureArmed(true, lyricsVisible = true, lyricsFullScreen = true, isListenTogetherGuest = true)
        )
    }

    @Test
    fun `a drag shorter than the threshold changes nothing`() {
        val threshold = 48f
        assertEquals(SwipeLyricsAction.NONE, swipeLyricsAction(0f, threshold))
        assertEquals(SwipeLyricsAction.NONE, swipeLyricsAction(47.9f, threshold))
        assertEquals(SwipeLyricsAction.NONE, swipeLyricsAction(-47.9f, threshold))
    }

    @Test
    fun `dragging left goes to the next song and right to the previous one`() {
        val threshold = 48f
        assertEquals(SwipeLyricsAction.NEXT, swipeLyricsAction(-48f, threshold))
        assertEquals(SwipeLyricsAction.NEXT, swipeLyricsAction(-400f, threshold))
        assertEquals(SwipeLyricsAction.PREVIOUS, swipeLyricsAction(48f, threshold))
        assertEquals(SwipeLyricsAction.PREVIOUS, swipeLyricsAction(400f, threshold))
    }

    @Test
    fun `the threshold is inclusive at exactly the boundary`() {
        // Pins the comparison operators: turn <= / >= into < / > and both of these fail.
        assertEquals(SwipeLyricsAction.NEXT, swipeLyricsAction(-48f, 48f))
        assertEquals(SwipeLyricsAction.PREVIOUS, swipeLyricsAction(48f, 48f))
    }
}
