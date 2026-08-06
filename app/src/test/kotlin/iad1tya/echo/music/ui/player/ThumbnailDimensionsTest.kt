package iad1tya.echo.music.ui.player

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [calculateThumbnailDimensions] sizes the player artwork. Its PORTRAIT branch used to size the
 * "square" off the WIDTH alone and ignore the height it was given (the landscape branch already did
 * `minOf(width, height)`), and the height cap was then handed only to the new player's host.
 *
 * Wherever the classic portrait slot is shorter than `width - 2*32dp` — split-screen / multi-window
 * portrait, foldable cover screens, and any phone once Ajustes ▸ Tamaño de pantalla shrinks the dp
 * height, made worse by the ~60 dp the classic layout spends on its own ThumbnailHeader —
 * `Modifier.size(thumbnailSize)` was coerced by the incoming max-height constraint, so the square
 * was measured as a wide RECTANGLE: the artwork (`ContentScale.Fit` by default) was fitted by
 * height and the leftover left/right bars were painted with `surfaceVariant`, with the user's
 * corner radius rounding the rectangle instead of the cover.
 *
 * The caller now passes the slot height as `maxThumbnailSize` for every host. These tests pin both
 * halves of that: it CLAMPS on a short slot, and it is a strict no-op on a tall one, which is what
 * keeps the ordinary classic phone byte-for-byte unchanged.
 */
class ThumbnailDimensionsTest {

    private val padding = 32.dp // PlayerHorizontalPadding, doubled inside the function

    private fun portrait(width: Dp, height: Dp, cap: Dp) =
        calculateThumbnailDimensions(
            containerWidth = width,
            containerHeight = height,
            isLandscape = false,
            maxThumbnailSize = cap,
        )

    @Test
    fun `tall slot - the height cap changes nothing, so the ordinary phone is untouched`() {
        val width = 411.dp
        val tallSlot = 520.dp

        val uncapped = portrait(width, tallSlot, Dp.Unspecified)
        val capped = portrait(width, tallSlot, tallSlot)

        assertEquals(width - padding * 2, uncapped.thumbnailSize)
        assertEquals(uncapped.thumbnailSize, capped.thumbnailSize)
        assertTrue(capped.thumbnailSize < tallSlot)
    }

    @Test
    fun `short slot - the cover becomes a real square instead of a letterboxed rectangle`() {
        val width = 411.dp
        val shortSlot = 240.dp

        // What the classic path asked for before the fix: no cap, so a 347dp "square" in a 240dp
        // slot, which the layout then flattened into 347 x 240.
        assertEquals(width - padding * 2, portrait(width, shortSlot, Dp.Unspecified).thumbnailSize)

        // With the slot height as the cap the square fits, so nothing is letterboxed.
        assertEquals(shortSlot, portrait(width, shortSlot, shortSlot).thumbnailSize)
    }

    @Test
    fun `the cap only ever coerces DOWN - it can never grow the cover`() {
        val small = portrait(width = 300.dp, height = 4000.dp, cap = 4000.dp)
        assertEquals(300.dp - padding * 2, small.thumbnailSize)

        // An unbounded BoxWithConstraints reports Dp.Infinity; coerceAtMost must be a no-op there.
        val unbounded = portrait(width = 300.dp, height = Dp.Infinity, cap = Dp.Infinity)
        assertEquals(small.thumbnailSize, unbounded.thumbnailSize)
    }

    @Test
    fun `itemWidth stays the full container width, so the pager page size is unaffected`() {
        val width = 411.dp
        val dims = portrait(width, 200.dp, 200.dp)

        assertEquals(width, dims.itemWidth)
        assertEquals(width, dims.containerSize)
        assertTrue(dims.thumbnailSize < dims.itemWidth)
    }

    @Test
    fun `wide layout keeps the #50 cap and now also respects a short slot`() {
        // Wide + tall: the 420dp #50 cap wins, exactly as before.
        val tall = calculateThumbnailDimensions(
            containerWidth = 900.dp,
            containerHeight = 800.dp,
            isLandscape = false,
            maxThumbnailSize = minOf(420.dp, 800.dp),
        )
        assertEquals(420.dp, tall.thumbnailSize)

        // Wide + short: 420dp in a 300dp slot letterboxes the same way, so the slot wins.
        val short = calculateThumbnailDimensions(
            containerWidth = 900.dp,
            containerHeight = 300.dp,
            isLandscape = false,
            maxThumbnailSize = minOf(420.dp, 300.dp),
        )
        assertEquals(300.dp, short.thumbnailSize)
    }

    @Test
    fun `landscape already clamped by height, so the new cap is a no-op there`() {
        val landscape = calculateThumbnailDimensions(
            containerWidth = 900.dp,
            containerHeight = 380.dp,
            isLandscape = true,
            maxThumbnailSize = Dp.Unspecified,
        )
        val landscapeCapped = calculateThumbnailDimensions(
            containerWidth = 900.dp,
            containerHeight = 380.dp,
            isLandscape = true,
            maxThumbnailSize = 380.dp,
        )

        assertEquals(380.dp - padding * 2, landscape.thumbnailSize)
        assertEquals(landscape.thumbnailSize, landscapeCapped.thumbnailSize)
    }
}
