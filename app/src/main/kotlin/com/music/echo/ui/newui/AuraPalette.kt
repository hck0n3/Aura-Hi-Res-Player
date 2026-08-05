package iad1tya.echo.music.ui.newui

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * The "Interfaz nueva" palette, transcribed 1:1 from the reference render
 * (`8-DISENO-REAL-con-iconos.html`, `:root` custom properties).
 *
 * Three accents and one ground. NOTHING else is a brand colour: the gradient play button is the only
 * full-colour element on a screen, everything else is white at a low alpha over [Ground].
 *
 * Screen agents: never hard-code a hex. Use these values or the derived helpers below, so a palette
 * tweak lands everywhere at once.
 */
object AuraPalette {
    /** `--teal:#3FE7CE` — primary accent: active states, hi-res badges, the "ON" side of a switch. */
    val Teal = Color(0xFF3FE7CE)

    /** `--blue:#2FA6F0` — middle stop of every gradient; the radio/secondary accent. */
    val Blue = Color(0xFF2FA6F0)

    /** `--violet:#6A5BFF` — infinite-radio marker and the end stop of every gradient. */
    val Violet = Color(0xFF6A5BFF)

    /**
     * `--deep:#060A12` — the ground. Blue-biased near-black, deliberately NOT pure black: the ambient
     * bloom needs something to sit on. Do not swap this for [Color.Black].
     */
    val Ground = Color(0xFF060A12)

    /** Slightly lifted ground used by the merged player menu sheet (`background:#080D18` in the render). */
    val GroundRaised = Color(0xFF080D18)

    /** `color:#EAF2FF` — foreground text/icon colour on [Ground]. */
    val OnGround = Color(0xFFEAF2FF)

    /** Knob colour of a switch and the ink INSIDE the gradient play button (`#061018`). */
    val OnAccent = Color(0xFF061018)

    // ── Derived, non-negotiable alpha steps ────────────────────────────────────────────────────────
    /** Secondary text (artist names): `.a { opacity:.55 }`. */
    val OnGroundMuted = OnGround.copy(alpha = 0.55f)

    /** Technical data / section labels: `opacity:.5` and `.42`. */
    val OnGroundFaint = OnGround.copy(alpha = 0.50f)
    val OnGroundGhost = OnGround.copy(alpha = 0.42f)

    /** Inactive transport icons: `opacity:.3`. Drag handles live here too. */
    val OnGroundDisabled = OnGround.copy(alpha = 0.30f)

    /** Card / chip fill: `rgba(255,255,255,.07)`. */
    val SurfaceFill = Color.White.copy(alpha = 0.07f)

    /** Card / chip hairline: `rgba(255,255,255,.10)`. */
    val SurfaceLine = Color.White.copy(alpha = 0.10f)

    /** Section separators (nav bar top, engine status bar top): `rgba(255,255,255,.08….09)`. */
    val Divider = Color.White.copy(alpha = 0.09f)

    /** Empty progress track: `rgba(255,255,255,.13)`. */
    val TrackEmpty = Color.White.copy(alpha = 0.13f)

    /** "SONANDO" row highlight: fill `rgba(63,231,206,.10)` + border `rgba(63,231,206,.25)`. */
    val NowPlayingFill = Teal.copy(alpha = 0.10f)
    val NowPlayingLine = Teal.copy(alpha = 0.25f)

    /** "Interfaz nueva" callout card: fill `rgba(122,92,255,.12)` + border `rgba(122,92,255,.30)`. */
    val BetaFill = Color(0xFF7A5CFF).copy(alpha = 0.12f)
    val BetaLine = Color(0xFF7A5CFF).copy(alpha = 0.30f)

    // ── Brushes ───────────────────────────────────────────────────────────────────────────────────
    /**
     * `linear-gradient(140deg, teal, blue 55%, violet)` — the play button. The ONLY saturated fill on
     * a screen; do not reuse it for decoration.
     */
    val PlayButtonGradient: Brush = Brush.linearGradient(
        colorStops = arrayOf(0f to Teal, 0.55f to Blue, 1f to Violet),
    )

    // The render's `linear-gradient(90deg, teal, blue 60%, violet)` progress fill is intentionally
    // absent: the timeline is the shared `PlayerProgressSlider` (four user-selectable styles), which
    // takes flat SliderColors — see AuraPlayer.auraSliderColors, which tints it with Teal/TrackEmpty.

    /**
     * `.cv { linear-gradient(145deg, teal, blue 45%, violet) }` — artwork placeholder while a cover
     * loads or when there is none. Variants exist in the render (teal→violet, violet→teal, …); use
     * [coverPlaceholder] with a stable seed so a given track always gets the same one.
     */
    val CoverPlaceholder: Brush = Brush.linearGradient(
        colorStops = arrayOf(0f to Teal, 0.45f to Blue, 1f to Violet),
    )

    private val coverVariants = listOf(
        listOf(Teal, Blue, Violet),
        listOf(Blue, Violet),
        listOf(Violet, Teal),
        listOf(Teal, Blue),
        listOf(Blue, Teal),
    )

    /**
     * Deterministic artwork placeholder. [seed] should be the media id (or any stable per-item string)
     * so the same row keeps the same colours across scrolls and process deaths.
     */
    fun coverPlaceholder(seed: String?): Brush {
        val index = ((seed?.hashCode() ?: 0) and 0x7FFFFFFF) % coverVariants.size
        return Brush.linearGradient(coverVariants[index])
    }
}

/**
 * The three blurred radial gradients that sit behind every new screen. Held as data (not as a [Brush])
 * precisely so it can be computed ONCE PER TRACK and cached — see [AuraBloomCache].
 */
@Immutable
data class AuraBloomColors(
    /** `radial-gradient(44% 38% at 26% 20%, rgba(63,231,206,.32))`. */
    val topLeft: Color,
    /** `radial-gradient(48% 42% at 82% 16%, rgba(122,92,255,.30))`. */
    val topRight: Color,
    /** `radial-gradient(52% 38% at 50% 50%, rgba(47,166,240,.24))`. */
    val center: Color,
) {
    companion object {
        /** The render's own bloom. Used whenever a track has no extracted colours. */
        val Brand = AuraBloomColors(
            topLeft = AuraPalette.Teal.copy(alpha = 0.32f),
            topRight = Color(0xFF7A5CFF).copy(alpha = 0.30f),
            center = AuraPalette.Blue.copy(alpha = 0.24f),
        )
    }
}
