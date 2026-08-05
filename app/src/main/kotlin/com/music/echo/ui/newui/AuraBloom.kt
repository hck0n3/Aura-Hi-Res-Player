package iad1tya.echo.music.ui.newui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale

/**
 * The ambient bloom: three soft radial gradients behind the content of every new screen
 * (`.bl { inset:-12% -22% 48%; filter:blur(26px); radial-gradient ×3 }` in the reference render).
 *
 * ## Thermal / battery contract — READ THIS BEFORE TOUCHING IT
 * The owner's permanent quality gate is that nothing new may heat the device or drain the battery.
 * Two rules follow, and both are enforced by the implementation below:
 *
 *  1. **Computed per TRACK, not per frame.** The colours are plain data ([AuraBloomColors]) resolved
 *     once per media id and memoised here. A screen that scrolls, a progress bar that ticks 60×/s, a
 *     recomposing row — none of them recompute anything. Only a track change does.
 *  2. **No `Modifier.blur`.** A 26 dp render-effect blur over a full-screen layer is a GPU cost paid on
 *     every single frame, and below API 31 it silently degrades. Radial gradients that fade to
 *     transparent already produce the render's look, so the bloom is drawn as three gradients inside
 *     [Modifier.drawWithCache] — the `Brush` objects are allocated once per (size, colours, intensity)
 *     triple and reused for every frame after that. No offscreen layer, no readback, no per-frame
 *     allocation.
 *
 * If you ever make the bloom react to the artwork, extract the colours OFF the main thread when the
 * track changes and call [AuraBloomCache.put] — never inside a composable body or a draw lambda.
 */
object AuraBloomCache {

    /**
     * Bounded, snapshot-aware map keyed by media id. Snapshot-aware so a bloom stored *after* a screen
     * already composed still reaches it; bounded because a long session must not grow without limit.
     * 32 entries is a whole session's worth of tracks at a few bytes each.
     */
    private const val MAX_ENTRIES = 32

    private val entries = mutableStateMapOf<String, AuraBloomColors>()
    private val insertionOrder = ArrayDeque<String>()

    /** Returns the stored bloom for [mediaId], or [AuraBloomColors.Brand] when nothing was stored. */
    fun get(mediaId: String?): AuraBloomColors {
        if (mediaId.isNullOrEmpty()) return AuraBloomColors.Brand
        return entries[mediaId] ?: AuraBloomColors.Brand
    }

    /**
     * Stores a bloom for [mediaId]. Call this from a coroutine on a background dispatcher when a track
     * changes — NEVER from a composable body or a draw lambda.
     */
    @Synchronized
    fun put(mediaId: String, colors: AuraBloomColors) {
        if (mediaId.isEmpty()) return
        if (entries.put(mediaId, colors) == null) {
            insertionOrder.addLast(mediaId)
            while (insertionOrder.size > MAX_ENTRIES) {
                entries.remove(insertionOrder.removeFirst())
            }
        }
    }

    /** Test / diagnostic hook. */
    @Synchronized
    fun clear() {
        entries.clear()
        insertionOrder.clear()
    }
}

/**
 * Resolves the bloom for the currently playing track, once per track.
 *
 * @param mediaId the current media id; pass `null` on screens with no now-playing context (the brand
 *   bloom is used, which is exactly what the render shows for Inicio / Biblioteca / Ajustes).
 */
@Composable
fun rememberAuraBloom(mediaId: String?): AuraBloomColors = AuraBloomCache.get(mediaId)

/**
 * Paints the ambient bloom behind the content of a screen. Put it on the ROOT container of a new
 * screen, above the [AuraPalette.Ground] fill and below everything else — or simply use
 * [auraScreenBackground], which does both.
 *
 * @param intensity global multiplier, 0f..1f. The render dims the bloom on the denser screens
 *   (Cola `.45`, Biblioteca `.40`, Ajustes `.32`); pass those values, do not restyle the colours.
 */
fun Modifier.auraBloom(
    colors: AuraBloomColors,
    intensity: Float = 1f,
): Modifier = this.drawWithCache {
    // Everything below runs ONCE per (size, colours, intensity) — never per frame.
    val w = size.width
    val h = size.height
    val a = intensity.coerceIn(0f, 1f)

    if (a <= 0f || w <= 0f || h <= 0f) {
        return@drawWithCache onDrawBehind { }
    }

    // `.bl { inset: -12% -22% 48% }` — the bloom band covers the top ~52% of the screen and overhangs
    // the edges, so its falloff is never visibly clipped.
    val bandTop = -0.12f * h
    val bandHeight = 0.64f * h
    val bandLeft = -0.22f * w
    val bandWidth = 1.44f * w

    val lobes = listOf(
        // radial-gradient(44% 38% at 26% 20%, rgba(63,231,206,.32))
        BloomLobe(colors.topLeft, 0.26f, 0.20f, 0.44f, 0.38f),
        // radial-gradient(48% 42% at 82% 16%, rgba(122,92,255,.30))
        BloomLobe(colors.topRight, 0.82f, 0.16f, 0.48f, 0.42f),
        // radial-gradient(52% 38% at 50% 50%, rgba(47,166,240,.24))
        BloomLobe(colors.center, 0.50f, 0.50f, 0.52f, 0.38f),
    ).map { lobe ->
        val cx = bandLeft + lobe.xFraction * bandWidth
        val cy = bandTop + lobe.yFraction * bandHeight
        val rx = (lobe.xRadiusFraction * bandWidth).coerceAtLeast(1f)
        val ry = (lobe.yRadiusFraction * bandHeight).coerceAtLeast(1f)
        // Compose has no elliptical gradient: build a circle of radius rx and squash it vertically.
        // The vertical scale IS the ellipse. `blur(26px)` is intentionally not applied — see the KDoc.
        PreparedLobe(
            brush = Brush.radialGradient(
                colors = listOf(lobe.color.copy(alpha = lobe.color.alpha * a), Color.Transparent),
                center = Offset(cx, cy),
                radius = rx,
            ),
            centerX = cx,
            centerY = cy,
            verticalScale = (ry / rx).coerceIn(0.05f, 4f),
            radius = rx,
        )
    }

    onDrawBehind { lobes.forEach { it.draw(this) } }
}

/** Ground fill + bloom in one modifier, for a screen root. */
fun Modifier.auraScreenBackground(
    colors: AuraBloomColors,
    intensity: Float = 1f,
): Modifier = this
    .drawBehind { drawRect(AuraPalette.Ground) }
    .auraBloom(colors, intensity)

private class BloomLobe(
    val color: Color,
    val xFraction: Float,
    val yFraction: Float,
    val xRadiusFraction: Float,
    val yRadiusFraction: Float,
)

private class PreparedLobe(
    val brush: Brush,
    val centerX: Float,
    val centerY: Float,
    val verticalScale: Float,
    val radius: Float,
) {
    /**
     * Fills exactly the circle's bounding box (which, once squashed by [verticalScale], is the
     * ellipse's bounding box) — not the whole screen. Three small transparent-falloff fills per frame.
     */
    fun draw(scope: DrawScope) {
        val boxTopLeft = Offset(centerX - radius, centerY - radius)
        val boxSize = Size(radius * 2f, radius * 2f)
        scope.scale(scaleX = 1f, scaleY = verticalScale, pivot = Offset(centerX, centerY)) {
            drawRect(brush = brush, topLeft = boxTopLeft, size = boxSize)
        }
    }
}
