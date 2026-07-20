package iad1tya.echo.music.ui.utils

import androidx.compose.foundation.focusable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusEventModifierNode
import androidx.compose.ui.focus.FocusState
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import iad1tya.echo.music.utils.DeviceForm

/**
 * True on a D-pad / remote context where the TV affordances (focus ring, initial focus, TV-only behaviours
 * like "never auto-hide the transport") should show. That is: a real Android TV / car head unit, OR the user
 * forced the split. The forced fallback is CRUCIAL: cheap Android-TV BOXES and Chinese car head units run
 * PLAIN Android and do NOT report as TV (no FEATURE_LEANBACK / UI_MODE_TYPE_TELEVISION), so form-factor
 * detection alone left every TV feature dark on them — [ForceSplitViewKey] is the manual escape hatch.
 *
 * DELIBERATELY NOT width-based. This used to also OR in "window >= 840dp", which conflated two unrelated
 * things: "this screen is wide enough for a two-pane LAYOUT" and "this device is driven by a REMOTE". They
 * only coincided because the layout threshold was so high that only TVs/tablets reached it. Lowering the
 * layout breakpoint to cover unfolded foldables (see [rememberIsWideLayout]) would have switched the D-pad
 * focus ring, forced initial focus and the TV transport behaviour ON for a phone the moment it got wide —
 * which is exactly how Android Auto / TV regressions get introduced (registry #12 / #26).
 *
 * Use this ONLY for D-pad/remote affordances ([tvFocusable], [tvFocusRestorer], initial focus requests).
 * For "is there room for a wider layout", use [rememberIsWideLayout].
 */
@Composable
fun rememberIsTvOrCar(): Boolean {
    val context = LocalContext.current
    val deviceTvCar = remember { DeviceForm.isTvOrCar(context) }
    // Cheap Android-TV BOXES and Chinese head units run PLAIN Android and report neither FEATURE_LEANBACK nor
    // UI_MODE_TYPE_TELEVISION, so `deviceTvCar` is false on them. The old code caught them with a ">= 840dp"
    // width term, which is why removing that term (correctly — width says nothing about the INPUT device) would
    // have gone dark on every D-pad affordance there: initial focus, ~40 tvFocusable rings, the never-auto-hide
    // transport and the D-pad key wake. The absence of a TOUCHSCREEN is the signal that actually means "driven
    // by a remote", and unlike width it can never mis-fire on a big phone or an unfolded foldable.
    val noTouchscreen = remember(context) {
        !context.packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_TOUCHSCREEN)
    }
    val forceSplit by iad1tya.echo.music.utils.rememberPreference(
        iad1tya.echo.music.constants.ForceSplitViewKey, false,
    )
    return deviceTvCar || noTouchscreen || forceSplit
}

/**
 * True on a BIG screen where a wide "Spotify-style" layout fits: TV / car head unit / tablet / car box /
 * unfolded foldable / any large-landscape display — i.e. the CURRENT window is wide enough for a genuinely
 * wider LAYOUT (two panes, a nav rail, a capped content column). REACTIVE and based on the REAL CURRENT
 * WINDOW width ([rememberIsExpandedWidth] -> [currentWindowAdaptiveInfo]), NOT the physical
 * smallestScreenWidthDp — so folding/unfolding a foldable, entering split-screen / free-form multiwindow, or
 * rotating flips this live without a restart, and a phone-width window on a big display (e.g. a narrow
 * multiwindow pane) correctly stays single pane.
 *
 * PURELY a width question — it says NOTHING about the input device, so it never enables the TV/car D-pad ring
 * (that is [rememberIsTvOrCar]).
 *
 * Breakpoint is [WIDE_LAYOUT_BREAKPOINT_DP] (600dp), measured against the window's REAL width in dp.
 *
 * It must NOT go through `WindowSizeClass.isWidthAtLeastBreakpoint()`: that API only knows the bucket
 * boundaries **0 / 600 / 840**, so it silently rounds any custom value DOWN to the bucket below. Asking it for
 * 700 is arithmetically identical to asking for 840 — which is how an earlier attempt at this shipped a
 * foldable release that did nothing at all: an unfolded Z Fold is ~690x829dp, lands in the 600 bucket, and
 * `>= 700` stayed false exactly as `>= 840` had. Read the width directly instead.
 *
 * 600dp is Material's own "medium" boundary and the classic sw600dp tablet line: ordinary phones in portrait
 * are ~360-430dp (excluded), while unfolded foldables, tablets and phones in landscape are above it.
 *
 * REACTIVE: it reads the CURRENT window, not the physical screen, so folding/unfolding, entering split-screen
 * or free-form multiwindow, and rotating all flip it live without a restart — and a narrow multiwindow pane on
 * a big display correctly stays single-pane.
 *
 * PURELY a width question — it says NOTHING about the input device, so it never enables the TV/car D-pad ring
 * (that is [rememberIsTvOrCar]). [ForceSplitViewKey] still forces it true.
 */
@Composable
fun rememberIsWideLayout(): Boolean {
    val forceSplit by iad1tya.echo.music.utils.rememberPreference(
        iad1tya.echo.music.constants.ForceSplitViewKey, false,
    )
    return forceSplit || rememberIsExpandedWidth()
}

/** Window width (dp) at/above which the wide two-pane layouts turn on. See [rememberIsWideLayout]. */
const val WIDE_LAYOUT_BREAKPOINT_DP = 600

/**
 * The CURRENT window is at least [WIDE_LAYOUT_BREAKPOINT_DP] dp wide, from the real container size rather than
 * a bucketed size class (see [rememberIsWideLayout] for why the size-class API cannot express this).
 */
@Composable
private fun rememberIsExpandedWidth(): Boolean {
    val widthPx = androidx.compose.ui.platform.LocalWindowInfo.current.containerSize.width
    val density = androidx.compose.ui.platform.LocalDensity.current
    return with(density) { widthPx.toDp() } >= WIDE_LAYOUT_BREAKPOINT_DP.dp
}

/**
 * D-pad focus ring for TV / car remotes: draws a clearly VISIBLE, HIGH-CONTRAST ring around the control while
 * it holds focus, so the user always sees which one is selected (plain Material3 shows no visible focus on a TV
 * remote). The ring is a DOUBLE stroke that reads on ANY background (light covers included): a dark outer halo
 * ([Color.Black] @ 0.6 alpha, ~6dp) drawn first, then the theme [MaterialTheme.colorScheme.primary] ring
 * (~3.75dp) on top. A ~2dp inset keeps the stroke inside the node bounds so a child's own `.clip(shape)` can't
 * cut it. On focus the content also gets a subtle [scaleFocused] pop (default ~1.06) drawn as a pure canvas
 * scale — it does NOT reflow layout, so tight rows never shift; pass `scaleFocused = 1f` to disable it.
 *
 * COLOR: a [Modifier.Node] draw pass can't call composable APIs, so the primary color is captured at THIS
 * composable factory and threaded into the Element/Node.
 *
 * PERFORMANCE: this is a pure draw-layer [Modifier.Node] — NOT `composed {}`. Focus flips only invalidate the
 * DRAW (a redraw of the ring), so scrolling a long TV list/grid where every row/card wraps this stays smooth
 * (the old composed{} version broke Compose skipping and allocated per recomposition per item — the cause of
 * the TV/low-end jank). Reading [MaterialTheme] here does NOT re-introduce that: the Element is a stable data
 * class, so recomposition only touches the node if the primary color/shape actually change.
 *
 * It does NOT add its own `.focusable()` (unless addFocusable=true): it relies on the control's OWN focus
 * target (any Button / IconButton / `.clickable {}`), sitting ABOVE it so its FocusEvent observes that one
 * stop — D-pad center still fires the control's real onClick. A no-op off-TV (returns `this` unchanged), so
 * phones/tablets are completely unaffected and pay ZERO cost.
 */
@Composable
fun Modifier.tvFocusable(
    enabled: Boolean,
    shape: Shape = RoundedCornerShape(50),
    addFocusable: Boolean = false,
    scaleFocused: Float = 1.06f,
): Modifier {
    // Capture the theme accent here (draw nodes can't call composables). Read unconditionally so the composable
    // call count is stable across `enabled` flips (fold/unfold, force-split).
    val ringColor = MaterialTheme.colorScheme.primary
    if (!enabled) return this
    val ring = this.then(TvFocusRingElement(shape, ringColor, scaleFocused))
    return if (addFocusable) ring.focusable() else ring
}

/**
 * Item-level D-pad focus ring for list rows / grid cards. Place it as the OUTERMOST modifier of a SHARED item
 * composable, wrapping the caller's own `.clickable`/`.combinedClickable`:
 *   Row(modifier = Modifier.tvFocusableItem(isTvOrCar).then(callerModifier) ...)
 * Its FocusEvent sits ABOVE the caller's clickable and observes that one focus stop. Defaults to NO scale
 * (`scaleFocused = 1f`) since full-width rows shouldn't pop. No-op + zero cost off-TV.
 */
@Composable
fun Modifier.tvFocusableItem(
    enabled: Boolean,
    shape: Shape = RoundedCornerShape(8.dp),
    scaleFocused: Float = 1f,
): Modifier = tvFocusable(enabled = enabled, shape = shape, addFocusable = false, scaleFocused = scaleFocused)

/**
 * TV/car ONLY: make a Lazy* container (LazyColumn / LazyRow / LazyVerticalGrid / LazyHorizontalGrid) — or any
 * focus group — REMEMBER its last-focused child and RESTORE focus to it when the D-pad re-enters. Place it on
 * the CONTAINER'S own `modifier`, e.g. `LazyRow(modifier = Modifier.tvFocusRestorer()) { … }`.
 *
 * WHY (the "se pierde el apuntador" bug): moving the D-pad toward an item that is off-screen and NOT YET
 * COMPOSED, or scrolling the currently-focused item out of view (it gets disposed), leaves nothing focusable,
 * so Compose drops focus to the container root — the [tvFocusable] ring has nothing to draw on and disappears
 * mid-navigation. [focusRestorer] plants a parent focus node that re-targets the remembered child (by
 * scrolling it back / restoring it) so focus — and the ring — stay put. On the FIRST entry (nothing saved yet)
 * it falls back to a normal directional search (the first focusable child), so a list entered by D-pad always
 * lights the ring.
 *
 * No-op + ZERO cost off TV: touch devices never traverse focus, so phone/tablet behavior is byte-for-byte
 * unchanged (returns `this`). Gated by the SAME [rememberIsTvOrCar] as [tvFocusable]. Called ONCE per
 * container (not per item), so reading [rememberIsTvOrCar] here is cheap.
 */
@Composable
fun Modifier.tvFocusRestorer(): Modifier =
    if (rememberIsTvOrCar()) this.focusRestorer() else this

private data class TvFocusRingElement(
    val shape: Shape,
    val ringColor: Color,
    val scaleFocused: Float,
) : ModifierNodeElement<TvFocusRingNode>() {
    override fun create(): TvFocusRingNode = TvFocusRingNode(shape, ringColor, scaleFocused)
    override fun update(node: TvFocusRingNode) {
        node.shape = shape
        node.ringColor = ringColor
        node.scaleFocused = scaleFocused
    }
}

private class TvFocusRingNode(
    var shape: Shape,
    var ringColor: Color,
    var scaleFocused: Float,
) : Modifier.Node(), FocusEventModifierNode, DrawModifierNode {
    private var focused = false

    override fun onFocusEvent(focusState: FocusState) {
        val nowFocused = focusState.isFocused
        if (nowFocused != focused) {
            focused = nowFocused
            invalidateDraw()
        }
    }

    override fun ContentDrawScope.draw() {
        if (!focused) {
            drawContent()
            return
        }
        if (scaleFocused != 1f) {
            // Pure canvas scale around center: pops the pixels without reflowing layout.
            scale(scaleFocused) {
                this@draw.drawContent()
                drawFocusRing()
            }
        } else {
            drawContent()
            drawFocusRing()
        }
    }

    /** Dark halo first, then the primary ring on top, both inset ~2dp so a child `.clip(shape)` can't cut it. */
    private fun DrawScope.drawFocusRing() {
        val inset = 2.dp.toPx()
        val insetSize = Size(
            width = (size.width - inset * 2f).coerceAtLeast(0f),
            height = (size.height - inset * 2f).coerceAtLeast(0f),
        )
        val outline = shape.createOutline(insetSize, layoutDirection, this)
        translate(left = inset, top = inset) {
            drawOutline(
                outline = outline,
                color = Color.Black.copy(alpha = 0.6f),
                style = Stroke(width = 6.dp.toPx()),
            )
            drawOutline(
                outline = outline,
                color = ringColor,
                style = Stroke(width = 3.75.dp.toPx()),
            )
        }
    }
}
