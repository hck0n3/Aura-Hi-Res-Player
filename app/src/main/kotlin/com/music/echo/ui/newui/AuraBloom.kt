package iad1tya.echo.music.ui.newui

import android.graphics.Bitmap
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.palette.graphics.Palette
import coil3.imageLoader
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.request.crossfade
import coil3.size.Precision
import coil3.toBitmap
import iad1tya.echo.music.LocalPlayerConnection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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
 * ## The bloom REACTS to the artwork (0.6.146)
 * It used to claim to and did not: [AuraBloomCache.put] had no caller anywhere in `app/src/main`, so
 * [AuraBloomCache.get] always fell through to [AuraBloomColors.Brand] and every screen, every song and
 * every session got the identical teal/violet wash — a shipped placebo with a settings comment
 * documenting it. [rememberAuraBloom] now resolves the real thing:
 *
 *  · one 100×100 `Precision.INEXACT` Coil request per track, on [Dispatchers.IO]. That is byte for byte
 *    the request `MainActivity` already issues for the dynamic theme, so with the dynamic theme on this
 *    is a memory-cache HIT and costs no decode at all; with it off it is one 100×100 decode per track
 *    change, i.e. ~40 KB and a few ms, once.
 *  · [Palette] on [Dispatchers.Default], `maximumColorCount(8)`, exactly as the classic player's
 *    `GRADIENT` / `APPLE_MUSIC` extractor does.
 *  · at most ONE extraction in flight per media id ([AuraBloomCache.claim]), because Inicio, the
 *    player sheet and the queue can all be composed at the same instant with the same id.
 *
 * Nothing about the DRAW changed: still three cached gradients, still no `Modifier.blur`.
 */
object AuraBloomCache {

    /**
     * Bounded, snapshot-aware map keyed by media id. Snapshot-aware so a bloom stored *after* a screen
     * already composed still reaches it; bounded because a long session must not grow without limit.
     * 32 entries is a whole session's worth of tracks at a few bytes each.
     */
    private const val MAX_ENTRIES = 32

    private val entries = mutableStateMapOf<String, AuraBloomEntry>()
    private val insertionOrder = ArrayDeque<String>()

    /** Media ids whose extraction is currently running, so two screens never decode the same cover. */
    private val inFlight = HashSet<String>()

    /** Returns the stored bloom for [mediaId], or [AuraBloomColors.Brand] when nothing was stored. */
    fun get(mediaId: String?): AuraBloomColors {
        if (mediaId.isNullOrEmpty()) return AuraBloomColors.Brand
        return entries[mediaId]?.colors ?: AuraBloomColors.Brand
    }

    /**
     * Opaque accent seed extracted with the bloom for [mediaId], or null when the cover has not been
     * resolved yet / there is no now-playing id. [AuraPaletteSync] reads this so Teal / buttons /
     * section titles follow the same cover as the wash — not only the ambient gradients.
     */
    fun accentSeed(mediaId: String?): Color? {
        if (mediaId.isNullOrEmpty()) return null
        return entries[mediaId]?.accentSeed
    }

    /**
     * Reserves [mediaId] for extraction. Returns `true` only for the FIRST caller: `false` means the
     * bloom is already stored or another composition is already resolving it. Every `true` must be
     * paired with a [put] or a [release], or that id can never be retried in this process.
     */
    @Synchronized
    fun claim(mediaId: String): Boolean {
        if (mediaId.isEmpty()) return false
        if (entries.containsKey(mediaId)) return false
        return inFlight.add(mediaId)
    }

    /** Gives up a [claim] that produced nothing, so a later screen may try again. */
    @Synchronized
    fun release(mediaId: String) {
        inFlight.remove(mediaId)
    }

    /**
     * Stores a bloom (+ accent seed) for [mediaId]. Call this from a coroutine on a background
     * dispatcher when a track changes — NEVER from a composable body or a draw lambda.
     */
    @Synchronized
    fun put(mediaId: String, entry: AuraBloomEntry) {
        if (mediaId.isEmpty()) return
        inFlight.remove(mediaId)
        if (entries.put(mediaId, entry) == null) {
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
        inFlight.clear()
    }

    /**
     * Ensures [mediaId] has a bloom+seed entry. Safe to call from several compositions: [claim]
     * dedupes in-flight work. Used by [rememberAuraBloom] and [AuraPaletteSync].
     */
    suspend fun ensure(context: android.content.Context, mediaId: String, thumbnailUrl: String) {
        if (!claim(mediaId)) return
        val entry = runCatching { extractAuraBloom(context, thumbnailUrl) }.getOrNull()
        if (entry != null) put(mediaId, entry) else release(mediaId)
    }
}

/** One cover extraction: ambient lobes + the opaque seed [AuraPalette] uses for chrome. */
@Immutable
data class AuraBloomEntry(
    val colors: AuraBloomColors,
    val accentSeed: Color,
)

/**
 * The bloom a screen is currently painting, plus the dissolve between the previous one and it.
 *
 * A track change must not CUT from one wash to another — that is a full-screen colour jump on every
 * song. [progress] runs 0→1 on a very low-stiffness spring (~1 s) and the two blooms are cross-faded
 * in the DRAW phase, so the dissolve costs three extra transparent gradient fills for one second and
 * recomposes nothing at all.
 */
@Stable
class AuraBloomState internal constructor(initial: AuraBloomColors) {
    /** What we are dissolving FROM (the previous bloom, sampled at the instant it was interrupted). */
    internal var from by mutableStateOf(initial)

    /** What we are dissolving TO — the current track's bloom. */
    internal var to by mutableStateOf(initial)

    /** 0f = fully [from], 1f = fully [to]. Read in the draw phase only. */
    internal var progress by mutableFloatStateOf(1f)

    /** Applied as ONE snapshot so no frame can ever see the new colours at the old progress. */
    internal fun retarget(next: AuraBloomColors) {
        Snapshot.withMutableSnapshot {
            from = blend(from, to, progress)
            to = next
            progress = 0f
        }
    }

    private fun blend(a: AuraBloomColors, b: AuraBloomColors, t: Float): AuraBloomColors =
        if (t >= 1f) b else AuraBloomColors(
            topLeft = lerp(a.topLeft, b.topLeft, t),
            topRight = lerp(a.topRight, b.topRight, t),
            center = lerp(a.center, b.center, t),
        )
}

/**
 * Resolves the bloom for the currently playing track, once per track, and animates the dissolve.
 *
 * @param mediaId the current media id; pass `null` on screens with no now-playing context (the brand
 *   bloom is used, which is exactly what the render shows for Inicio / Biblioteca / Ajustes).
 */
@Composable
fun rememberAuraBloom(mediaId: String?): AuraBloomState {
    val target = AuraBloomCache.get(mediaId)
    val state = remember { AuraBloomState(target) }

    // The cover to extract from. Read from the SAME PlayerConnection the caller derived [mediaId] from,
    // and only used when the ids still agree — a stale url must never colour the wrong track.
    val playerConnection = LocalPlayerConnection.current
    val thumbnailUrl: String? = if (mediaId.isNullOrEmpty() || playerConnection == null) {
        null
    } else {
        val metadata by playerConnection.mediaMetadata.collectAsState()
        metadata?.takeIf { it.id == mediaId }?.thumbnailUrl
    }

    val context = LocalContext.current
    LaunchedEffect(mediaId, thumbnailUrl) {
        val id = mediaId
        val url = thumbnailUrl
        if (id.isNullOrEmpty() || url.isNullOrEmpty()) return@LaunchedEffect
        AuraBloomCache.ensure(context, id, url)
    }

    LaunchedEffect(target) {
        if (target == state.to) return@LaunchedEffect
        state.retarget(target)
        animate(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                // Deliberately the softest spring in the app: this is a full-screen colour change and
                // anything faster reads as a cut. ~1 s.
                stiffness = Spring.StiffnessVeryLow,
            ),
        ) { value, _ -> state.progress = value }
    }

    return state
}

/**
 * One 100×100 decode (shared with the dynamic theme's Coil memory-cache slot) → [Palette] → three
 * lobe colours + an opaque accent seed for [AuraPalette]. Suspends on IO/Default; never call it from
 * composition or a draw lambda.
 */
private suspend fun extractAuraBloom(
    context: android.content.Context,
    thumbnailUrl: String,
): AuraBloomEntry? {
    val bitmap: Bitmap = withContext(Dispatchers.IO) {
        val request = ImageRequest.Builder(context)
            .data(thumbnailUrl)
            // 100×100 + INEXACT is the request MainActivity already makes for the dynamic theme, and
            // the memory-cache key of a request without transformations does not include the size —
            // so this SHARES that slot instead of forcing a second decode. Do not make it EXACT.
            .size(100, 100)
            .precision(Precision.INEXACT)
            .allowHardware(false)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .diskCachePolicy(CachePolicy.ENABLED)
            .networkCachePolicy(CachePolicy.ENABLED)
            .crossfade(false)
            .build()
        context.imageLoader.execute(request).image?.toBitmap()
    } ?: return null

    val palette = withContext(Dispatchers.Default) {
        Palette.from(bitmap).maximumColorCount(8).resizeBitmapArea(100 * 100).generate()
    }

    val swatches = listOfNotNull(
        palette.vibrantSwatch,
        palette.lightVibrantSwatch,
        palette.darkVibrantSwatch,
        palette.dominantSwatch,
        palette.mutedSwatch,
        palette.lightMutedSwatch,
        palette.darkMutedSwatch,
    ).distinctBy { it.rgb }
    if (swatches.isEmpty()) return null

    val primary = swatches[0].rgb
    val secondary = swatches.getOrNull(1)?.rgb ?: primary
    val tertiary = swatches.getOrNull(2)?.rgb ?: secondary

    // The render's own alphas (.32 / .30 / .24) are kept: they are what makes this a wash on a
    // near-black ground rather than three coloured blobs.
    return AuraBloomEntry(
        colors = AuraBloomColors(
            topLeft = bloomLobeColor(primary, 0.32f),
            topRight = bloomLobeColor(secondary, 0.30f),
            center = bloomLobeColor(tertiary, 0.24f),
        ),
        // Opaque, chroma-floored seed — same HSV floors as the lobes, so chrome and wash agree.
        accentSeed = bloomLobeColor(primary, 1f),
    )
}

/**
 * Normalises one extracted swatch into something the ground can carry: a cover that is nearly black,
 * blown out or muddy must still produce a legible wash, not a grey smear or a white flare.
 */
private fun bloomLobeColor(rgb: Int, alpha: Float): Color {
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(rgb, hsv)
    // An ACHROMATIC cover (black-and-white sleeve) keeps its zero saturation — forcing colour into it
    // would invent a hue the artwork does not have. Anything with a hue gets a floor so it survives
    // the low alpha.
    if (hsv[1] > 0.05f) hsv[1] = hsv[1].coerceIn(0.35f, 0.95f)
    hsv[2] = hsv[2].coerceIn(0.55f, 0.95f)
    return Color(android.graphics.Color.HSVToColor(hsv)).copy(alpha = alpha)
}

/**
 * Paints the ambient bloom behind the content of a screen. Put it on the ROOT container of a new
 * screen, above the [AuraPalette.Ground] fill and below everything else — or simply use
 * [auraScreenBackground], which does both.
 *
 * @param intensity global multiplier, 0f..1f. The render dims the bloom on the denser screens
 *   (Cola `.45`, Biblioteca `.40`, Ajustes `.32`); pass those values, do not restyle the colours.
 */
fun Modifier.auraBloom(
    colors: AuraBloomState,
    intensity: Float = 1f,
): Modifier = this.drawWithCache {
    // Everything below runs ONCE per (size, colours, intensity) — never per frame. `colors.from` and
    // `colors.to` are read HERE, so a track change rebuilds the brushes exactly once; `colors.progress`
    // is read in `onDrawBehind` below, so the one-second dissolve invalidates DRAW only.
    val w = size.width
    val h = size.height
    val a = intensity.coerceIn(0f, 1f)

    if (a <= 0f || w <= 0f || h <= 0f) {
        return@drawWithCache onDrawBehind { }
    }

    val to = colors.to
    val from = colors.from
    val toLobes = prepareBloomLobes(to, a, w, h)
    val fromLobes = if (from == to) null else prepareBloomLobes(from, a, w, h)

    onDrawBehind {
        val p = colors.progress
        if (fromLobes != null && p < 1f) {
            fromLobes.forEach { it.draw(this, 1f - p) }
            toLobes.forEach { it.draw(this, p) }
        } else {
            toLobes.forEach { it.draw(this, 1f) }
        }
    }
}

/** Ground fill + bloom in one modifier, for a screen root. */
fun Modifier.auraScreenBackground(
    colors: AuraBloomState,
    intensity: Float = 1f,
): Modifier = this
    .drawBehind { drawRect(AuraPalette.Ground) }
    .auraBloom(colors, intensity)

/**
 * `.bl { inset: -12% -22% 48% }` — the bloom band covers the top ~52% of the screen and overhangs the
 * edges, so its falloff is never visibly clipped. Called from the cache block only.
 */
private fun prepareBloomLobes(
    colors: AuraBloomColors,
    intensity: Float,
    w: Float,
    h: Float,
): List<PreparedLobe> {
    val bandTop = -0.12f * h
    val bandHeight = 0.64f * h
    val bandLeft = -0.22f * w
    val bandWidth = 1.44f * w

    return listOf(
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
                colors = listOf(
                    lobe.color.copy(alpha = lobe.color.alpha * intensity),
                    Color.Transparent,
                ),
                center = Offset(cx, cy),
                radius = rx,
            ),
            centerX = cx,
            centerY = cy,
            verticalScale = (ry / rx).coerceIn(0.05f, 4f),
            radius = rx,
        )
    }
}

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
    fun draw(scope: DrawScope, alpha: Float) {
        if (alpha <= 0f) return
        val boxTopLeft = Offset(centerX - radius, centerY - radius)
        val boxSize = Size(radius * 2f, radius * 2f)
        scope.scale(scaleX = 1f, scaleY = verticalScale, pivot = Offset(centerX, centerY)) {
            drawRect(brush = brush, topLeft = boxTopLeft, size = boxSize, alpha = alpha)
        }
    }
}
