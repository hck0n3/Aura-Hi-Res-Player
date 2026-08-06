package iad1tya.echo.music.ui.newui

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The rule that decides WHICH SHAPE the "Interfaz nueva" player draws, pinned.
 *
 * This exists because the bug it replaces was invisible to every other kind of check: the player
 * compiled, its tests passed and its portrait screenshots were correct while `isLandscape ||
 * isWideLayout` handed the whole screen to the CLASSIC player — so rotating a phone made the redesign
 * disappear, and TV, coche and tablet never saw it at all. A shape rule that lives only inside a
 * composable body cannot be checked; these two predicates are the rule, extracted, and this is where
 * anyone changing them finds out what each term was for.
 *
 * The terms are deliberately the same ones the classic player uses (`useWidePlayer`, Player.kt:2757, and
 * the queue-column condition at Player.kt:3020), so the two shapes agree about what a window IS even
 * where they disagree about what to draw in it.
 */
class AuraPlayerShapeTest {

    // ---------------------------------------------------------------- which shape

    @Test
    fun `a portrait phone gets the portrait column`() {
        assertFalse(
            auraUsesWideShape(
                isLandscape = false,
                isWideLayout = false,
                videoSurfaceVisible = false,
            ),
        )
    }

    @Test
    fun `rotating a phone is the whole point - landscape always takes the wide shape`() {
        // A phone in landscape is ~900 dp wide, so both terms are true; but the rule must hold even for
        // a landscape window NARROWER than the 600 dp breakpoint (split-screen, free-form), which is the
        // only case where `isLandscape` carries the decision on its own.
        assertTrue(
            auraUsesWideShape(
                isLandscape = true,
                isWideLayout = false,
                videoSurfaceVisible = false,
            ),
        )
        assertTrue(
            auraUsesWideShape(
                isLandscape = true,
                isWideLayout = true,
                videoSurfaceVisible = false,
            ),
        )
    }

    @Test
    fun `a tablet, a TV or an unfolded foldable is wide even while it reports PORTRAIT`() {
        // The exact case a pure-orientation rule loses: a 800x1280 tablet held upright, or an unfolded
        // Z Fold at ~690x829. Both report ORIENTATION_PORTRAIT and both have room for two panes.
        assertTrue(
            auraUsesWideShape(
                isLandscape = false,
                isWideLayout = true,
                videoSurfaceVisible = false,
            ),
        )
    }

    @Test
    fun `a wide PORTRAIT window with video stays on the portrait path`() {
        // Video routes by REAL orientation, never by width. The portrait shape hosts the surface in its
        // artwork slot; sending a wide portrait window into the landscape fullscreen path would change
        // which surface lifecycle runs for no gain (registry #43).
        assertFalse(
            auraUsesWideShape(
                isLandscape = false,
                isWideLayout = true,
                videoSurfaceVisible = true,
            ),
        )
    }

    @Test
    fun `a REAL landscape window with video still takes the wide shape - that is the fullscreen video`() {
        assertTrue(
            auraUsesWideShape(
                isLandscape = true,
                isWideLayout = true,
                videoSurfaceVisible = true,
            ),
        )
    }

    @Test
    fun `video only diverts the width term, never the orientation term`() {
        // Stated as a property rather than a case list: with `isLandscape` true the answer is true for
        // every combination of the other two, so no future video term can ever strand a rotated phone
        // back on the portrait column.
        for (wide in listOf(false, true)) {
            for (video in listOf(false, true)) {
                assertTrue(
                    "landscape must always be wide (wide=$wide, video=$video)",
                    auraUsesWideShape(
                        isLandscape = true,
                        isWideLayout = wide,
                        videoSurfaceVisible = video,
                    ),
                )
            }
        }
    }

    // ---------------------------------------------------------------- the queue column

    @Test
    fun `the live queue column needs width`() {
        assertTrue(auraShowsQueueColumn(isWideLayout = true, showInlineLyrics = false))
        assertFalse(auraShowsQueueColumn(isWideLayout = false, showInlineLyrics = false))
    }

    @Test
    fun `the lyrics take the queue column away, so they get the whole left pane`() {
        assertFalse(auraShowsQueueColumn(isWideLayout = true, showInlineLyrics = true))
    }

    // ---------------------------------------------------------------- the cover budget

    @Test
    fun `the cover threshold leaves room for the controls, which are what the pane exists for`() {
        // The now-playing pane must fit, in order: the header, the dense controls block, the engine
        // status bar — and only THEN a cover. The number is a budget, not a taste: assert it clears the
        // parts it is derived from with a usable cover left over, so anyone lowering it sees what breaks.
        val header = 48.dp
        val denseControls = 300.dp
        val engineBar = 46.dp
        val leftForCover = AURA_WIDE_COVER_MIN_PANE_HEIGHT - header - denseControls - engineBar
        assertTrue(
            "a pane at the threshold must have a cover worth drawing, got $leftForCover",
            leftForCover >= 100.dp,
        )
    }

    @Test
    fun `a landscape phone falls below the threshold and a landscape tablet clears it`() {
        // ~380 dp of usable height on a phone in landscape once the system bars and the collapsed queue
        // bar are paid for; a 10-inch tablet in landscape has ~800 dp. The threshold has to separate
        // those two, because "wide" alone does not.
        assertTrue(380.dp < AURA_WIDE_COVER_MIN_PANE_HEIGHT)
        assertTrue(800.dp >= AURA_WIDE_COVER_MIN_PANE_HEIGHT)
    }

    @Test
    fun `the threshold is the shipped literal`() {
        assertEquals(520.dp, AURA_WIDE_COVER_MIN_PANE_HEIGHT)
    }
}
