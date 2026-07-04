package iad1tya.echo.music.ui.utils

import androidx.compose.foundation.focusable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusEventModifierNode
import androidx.compose.ui.focus.FocusState
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import iad1tya.echo.music.utils.DeviceForm

/** True on Android TV or a car head unit (form-factor, cached). Used to switch layouts + D-pad focus on. */
@Composable
fun rememberIsTvOrCar(): Boolean {
    val context = LocalContext.current
    return remember { DeviceForm.isTvOrCar(context) }
}

/**
 * True on a BIG screen where a wide "Spotify-style" layout fits: TV / car head unit / tablet / car box /
 * unfolded foldable / any large-landscape display. REACTIVE — reads LocalConfiguration.smallestScreenWidthDp
 * each composition, so folding/unfolding a foldable flips this live without a restart. Phones (and folded
 * foldables, sw < 600dp) stay false in every orientation -> they keep the normal portrait UI.
 *
 * Distinct from [rememberIsTvOrCar], which gates the D-pad focus RING (only remote-driven TV/car need it).
 */
@Composable
fun rememberIsWideScreen(): Boolean {
    val configuration = LocalConfiguration.current
    val isTvOrCar = rememberIsTvOrCar()
    val forceSplit by iad1tya.echo.music.utils.rememberPreference(
        iad1tya.echo.music.constants.ForceSplitViewKey, false,
    )
    return isTvOrCar || forceSplit || configuration.smallestScreenWidthDp >= 600
}

/**
 * D-pad focus ring for TV / car remotes: draws a clearly VISIBLE white ring around the control while it holds
 * focus, so the user sees which one is selected (plain Material3 shows no visible focus on a TV remote).
 *
 * PERFORMANCE: this is a pure draw-layer [Modifier.Node] — NOT `composed {}` and NO recomposition. Focus flips
 * only invalidate the DRAW (a redraw of the ring), so scrolling a long TV list/grid where every row/card wraps
 * this stays smooth (the old composed{} version broke Compose skipping and allocated per recomposition per
 * item — the cause of the TV/low-end jank). It draws ONLY a ring (no scale) for the same reason.
 *
 * It does NOT add its own `.focusable()` (unless addFocusable=true): it relies on the control's OWN focus
 * target (any Button / IconButton / `.clickable {}`), sitting ABOVE it so its FocusEvent observes that one
 * stop — D-pad center still fires the control's real onClick. A no-op off-TV (returns `this` unchanged), so
 * phones/tablets are completely unaffected and pay ZERO cost.
 */
fun Modifier.tvFocusable(
    enabled: Boolean,
    shape: Shape = RoundedCornerShape(50),
    addFocusable: Boolean = false,
    @Suppress("UNUSED_PARAMETER") scaleFocused: Float = 1.12f,
): Modifier {
    if (!enabled) return this
    val ring = this.then(TvFocusRingElement(shape))
    return if (addFocusable) ring.focusable() else ring
}

/**
 * Item-level D-pad focus ring for list rows / grid cards. Place it as the OUTERMOST modifier of a SHARED item
 * composable, wrapping the caller's own `.clickable`/`.combinedClickable`:
 *   Row(modifier = Modifier.tvFocusableItem(isTvOrCar).then(callerModifier) ...)
 * Its FocusEvent sits ABOVE the caller's clickable and observes that one focus stop. No-op + zero cost off-TV.
 */
fun Modifier.tvFocusableItem(
    enabled: Boolean,
    shape: Shape = RoundedCornerShape(8.dp),
    @Suppress("UNUSED_PARAMETER") scaleFocused: Float = 1f,
): Modifier = tvFocusable(enabled = enabled, shape = shape, addFocusable = false)

private data class TvFocusRingElement(val shape: Shape) : ModifierNodeElement<TvFocusRingNode>() {
    override fun create(): TvFocusRingNode = TvFocusRingNode(shape)
    override fun update(node: TvFocusRingNode) { node.shape = shape }
}

private class TvFocusRingNode(var shape: Shape) : Modifier.Node(), FocusEventModifierNode, DrawModifierNode {
    private var focused = false

    override fun onFocusEvent(focusState: FocusState) {
        val nowFocused = focusState.isFocused
        if (nowFocused != focused) {
            focused = nowFocused
            invalidateDraw()
        }
    }

    override fun ContentDrawScope.draw() {
        drawContent()
        if (focused) {
            val outline = shape.createOutline(size, layoutDirection, this)
            drawOutline(outline = outline, color = Color.White, style = Stroke(width = 3.dp.toPx()))
        }
    }
}
