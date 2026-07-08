package iad1tya.echo.music.ui.utils

import androidx.compose.foundation.focusable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.window.core.layout.WindowSizeClass
import iad1tya.echo.music.utils.DeviceForm

/**
 * True on a D-pad / remote context where the TV affordances (focus ring, initial focus, TV layouts) should
 * show. That is: a real Android TV / car head unit, OR the user forced the split, OR a genuinely wide CURRENT
 * window (>= 840dp, the "expanded" width breakpoint — see [rememberIsExpandedWidth]). The wide/forced
 * fallbacks are CRUCIAL: cheap Android-TV BOXES and Chinese car head units run PLAIN Android and do NOT report
 * as TV (no FEATURE_LEANBACK / UI_MODE_TYPE_TELEVISION), so form-factor detection alone left every TV feature
 * dark on them. Harmless on touch tablets — touch raises no focus events, so the ring never actually draws
 * there.
 */
@Composable
fun rememberIsTvOrCar(): Boolean {
    val context = LocalContext.current
    val deviceTvCar = remember { DeviceForm.isTvOrCar(context) }
    val forceSplit by iad1tya.echo.music.utils.rememberPreference(
        iad1tya.echo.music.constants.ForceSplitViewKey, false,
    )
    return deviceTvCar || forceSplit || rememberIsExpandedWidth()
}

/**
 * True on a BIG screen where a wide "Spotify-style" layout fits: TV / car head unit / tablet / car box /
 * unfolded foldable / any large-landscape display. REACTIVE and based on the REAL CURRENT WINDOW width
 * ([rememberIsExpandedWidth] -> [currentWindowAdaptiveInfo]), NOT the physical smallestScreenWidthDp — so
 * folding/unfolding a foldable, entering split-screen / free-form multiwindow, or rotating flips this live
 * without a restart, and a phone-width window on a big display (e.g. a narrow multiwindow pane) correctly
 * stays single pane. Requires the "expanded" >= 840dp width (real room for a rail + content + panel); BELOW
 * that -> single pane, even on a device whose old smallestScreenWidthDp >= 600 used to force the split.
 *
 * Distinct from [rememberIsTvOrCar], which gates the D-pad focus RING (only remote-driven TV/car need it).
 */
@Composable
fun rememberIsWideScreen(): Boolean {
    val isTvOrCar = rememberIsTvOrCar()
    val forceSplit by iad1tya.echo.music.utils.rememberPreference(
        iad1tya.echo.music.constants.ForceSplitViewKey, false,
    )
    return isTvOrCar || forceSplit || rememberIsExpandedWidth()
}

/**
 * The CURRENT window is at least Material's "expanded" width breakpoint (840dp). Uses
 * [currentWindowAdaptiveInfo] so it reflects the real, orientation-aware, multiwindow-aware WINDOW width —
 * NOT the physical screen (smallestScreenWidthDp) — and recomposes when that width changes (fold/unfold,
 * split-screen resize, rotation). This is the responsive gate that keeps foldables / multiwindow from being
 * forced into the split by a coarse physical-size check.
 */
@Composable
private fun rememberIsExpandedWidth(): Boolean {
    val windowSizeClass = currentWindowAdaptiveInfo().windowSizeClass
    return windowSizeClass.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_EXPANDED_LOWER_BOUND)
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
