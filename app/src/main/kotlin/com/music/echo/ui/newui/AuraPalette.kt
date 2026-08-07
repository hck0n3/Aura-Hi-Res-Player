package iad1tya.echo.music.ui.newui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import iad1tya.echo.music.constants.AppThemePresetKey
import iad1tya.echo.music.constants.DynamicThemeKey
import iad1tya.echo.music.constants.PureBlackKey
import iad1tya.echo.music.constants.SelectedThemeColorKey
import iad1tya.echo.music.constants.ThumbnailCornerRadiusKey
import iad1tya.echo.music.ui.component.ColorPickerConversions
import iad1tya.echo.music.ui.theme.DefaultThemeColor
import iad1tya.echo.music.ui.theme.ThemePreset
import iad1tya.echo.music.ui.theme.ensureLegibleOn
import iad1tya.echo.music.utils.rememberEnumPreference
import iad1tya.echo.music.utils.rememberPreference

/**
 * The "Interfaz nueva" palette, transcribed 1:1 from the reference render
 * (`8-DISENO-REAL-con-iconos.html`, `:root` custom properties).
 *
 * Three accents and one ground. NOTHING else is a brand colour: the gradient play button is the only
 * full-colour element on a screen, everything else is white at a low alpha over [Ground].
 *
 * Screen agents: never hard-code a hex. Use these values or the derived helpers below, so a palette
 * tweak lands everywhere at once.
 *
 * ## THE THREE ACCENTS AND THE GROUND ARE NO LONGER CONSTANTS (0.6.147)
 * They were, and that was the owner's original complaint coming back through the side door: he picks
 * one of the 46 swatches (or types a hex, or leaves the dynamic artwork/wallpaper accent on), the ~89
 * classic screens and every dialog repaint, and the redesign stayed a fixed teal because ~143 call
 * sites read `AuraPalette.Teal` / `.Blue` / `.Violet` from this object. The same shape of bug hid
 * AMOLED: `PureBlackKey` moved `colorScheme.surface` to black while the new screens kept painting
 * [Ground] (`#060A12`), so dragging the queue up went pure black and dragging it back down brought the
 * blue-black ground back, in one gesture.
 *
 * Both are fixed HERE, in one file, rather than at 143 call sites:
 *  · [Teal] / [Blue] / [Violet] and everything derived from them read [accent], a snapshot-backed value.
 *  · [Ground] / [GroundRaised] read [pureBlackGround], likewise.
 *  · [AuraPaletteSync] resolves both ONCE per theme change and writes them. It is called from
 *    [NewUiGate] (so every gated surface has them before it composes) and from the shell
 *    (`AuraNavigationBar`, `AuraMiniPlayer`), which `MainActivity` composes outside any gate.
 *
 * Because the reads happen inside composition and inside draw lambdas, a theme change repaints every
 * new surface with no per-frame work and no call-site edits. **With the flag off [AuraPaletteSync]
 * never runs, nothing is ever written, and every value below is the literal that shipped** — which is
 * what makes this reversible term for term.
 */
object AuraPalette {

    // ── The literals of the render. Private: they are the FALLBACK, not the value. ────────────────

    /** `--teal:#3FE7CE` — the render's primary accent. */
    private val BrandTeal = Color(0xFF3FE7CE)

    /** `--blue:#2FA6F0` — the render's middle gradient stop. */
    private val BrandBlue = Color(0xFF2FA6F0)

    /** `--violet:#6A5BFF` — the render's end gradient stop. */
    private val BrandViolet = Color(0xFF6A5BFF)

    /**
     * `--deep:#060A12` — the ground. Blue-biased near-black, deliberately NOT pure black: the ambient
     * bloom needs something to sit on. Do not swap this for [Color.Black] — except through AMOLED,
     * which is the user asking for exactly that (see [pureBlackGround]).
     */
    private val BrandGround = Color(0xFF060A12)

    /** Slightly lifted ground used by the merged player menu sheet (`background:#080D18` in the render). */
    private val BrandGroundRaised = Color(0xFF080D18)

    /**
     * AMOLED's raised ground. NOT [Color.Black]: the mini-player pill and the player menu sheet stand
     * ON the ground and would disappear into it. Six levels of grey is the smallest lift that still
     * leaves an OLED pixel effectively off.
     */
    private val AmoledGroundRaised = Color(0xFF060606)

    // ── Resolved state. Written only by [AuraPaletteSync]. ────────────────────────────────────────

    /** The three accents in force, plus everything derived from them, resolved once per theme change. */
    private var accent by mutableStateOf(AuraAccent.Brand)

    /** True while "Negro puro (AMOLED)" is on, which is the only thing that moves the ground. */
    private var pureBlackGround by mutableStateOf(false)

    /**
     * "Radio de esquina de la miniatura" ([ThumbnailCornerRadiusKey]) resolved into the two shapes the
     * redesign cuts its covers with. Held as the finished shapes, not as the multiplier: they are read
     * from composition bodies and from `.clip(…)` chains on every row of every list.
     */
    private var corners by mutableStateOf(AuraCoverCorners.Render)

    /**
     * Applied as a plain (idempotent) write from [AuraPaletteSync], which composes BEFORE the surfaces
     * that read these — so a theme change lands within the same composition pass instead of one frame
     * later. Writing only on a real change is what keeps it from looping.
     */
    internal fun apply(next: AuraAccent, pureBlack: Boolean, coverCorners: AuraCoverCorners) {
        if (accent != next) accent = next
        if (pureBlackGround != pureBlack) pureBlackGround = pureBlack
        if (corners != coverCorners) corners = coverCorners
    }

    /** Test / process hook: back to the shipped literals. */
    internal fun reset() = apply(AuraAccent.Brand, pureBlack = false, coverCorners = AuraCoverCorners.Render)

    // ── Accents ───────────────────────────────────────────────────────────────────────────────────

    /** Primary accent: active states, hi-res badges, the "ON" side of a switch. */
    val Teal: Color get() = accent.primary

    /** Middle stop of every gradient; the radio/secondary accent. */
    val Blue: Color get() = accent.secondary

    /** Infinite-radio marker and the end stop of every gradient. */
    val Violet: Color get() = accent.tertiary

    // ── Ground ────────────────────────────────────────────────────────────────────────────────────

    /** The ground every new screen paints. Pure black while AMOLED is on, so AMOLED is not half-applied. */
    val Ground: Color get() = if (pureBlackGround) Color.Black else BrandGround

    /** The lifted ground of the mini pill and the merged player menu sheet. */
    val GroundRaised: Color get() = if (pureBlackGround) AmoledGroundRaised else BrandGroundRaised

    /** `color:#EAF2FF` — foreground text/icon colour on [Ground]. Never accent-derived. */
    val OnGround = Color(0xFFEAF2FF)

    /** Knob colour of a switch and the ink INSIDE the gradient play button (`#061018`). */
    val OnAccent = Color(0xFF061018)

    // ── Derived, non-negotiable alpha steps ────────────────────────────────────────────────────────
    /** Secondary text (artist names): `.a { opacity:.55 }`. */
    val OnGroundMuted = OnGround.copy(alpha = 0.55f)

    /** Technical data / section labels: `opacity:.5` and `.42`. */
    val OnGroundFaint = OnGround.copy(alpha = 0.50f)

    /**
     * The faintest step that is still allowed to carry TEXT.
     *
     * The render's `.42` was transcribed literally and it does not survive Android's contrast floor:
     * `#EAF2FF` at 42 % over [Ground] composites to ~3.7:1, and every site that uses this step draws
     * text at 11–12 sp, which needs 4.5:1. `.48` composites to ~4.55:1 on the same ground, so the step
     * stays visibly below [OnGroundFaint] while clearing AA. Do not take it back down.
     */
    val OnGroundGhost = OnGround.copy(alpha = 0.48f)

    /**
     * An UNSELECTED bottom-nav cell — glyph and label together.
     *
     * Deliberately NOT [OnGroundGhost]: the ghost step is for tertiary data you may or may not read,
     * whereas three of the four nav cells are the app's primary navigation and were reading as
     * disabled ("está tan transparente que no se define nada"). At 62 % this composites to ~7:1 on
     * [Ground], which is roughly where the classic toolbar's `onSurfaceVariant` sits, so the redesign
     * stops looking dimmer than the bar it replaced.
     *
     * It is a step of [OnGround], NOT of the accent, which is why the accent work above cannot move it:
     * it reads ~7:1 on `#060A12` and better still on AMOLED's pure black, for every one of the 46
     * swatches, because no swatch is a term in it.
     */
    val NavInactive = OnGround.copy(alpha = 0.62f)

    /**
     * The pill that slides behind the SELECTED nav cell. The accent at a low alpha so the ground still
     * reads through it — the render marks the active cell with colour, and the pill is the motion that
     * carries that colour from one cell to the next.
     */
    val NavIndicator: Color get() = accent.navIndicator

    /** Inactive transport icons: `opacity:.3`. Drag handles live here too. */
    val OnGroundDisabled = OnGround.copy(alpha = 0.30f)

    /** Card / chip fill: `rgba(255,255,255,.07)`. */
    val SurfaceFill = Color.White.copy(alpha = 0.07f)

    /**
     * [SurfaceFill] for a control that FLOATS over other content instead of sitting on the ground.
     *
     * ## Why this exists
     * The render's `rgba(255,255,255,.07)` is 7 % white over the page body, and in the render nothing
     * is ever stacked under it. On Android two situations break that assumption and both have now been
     * reported by the owner as the same symptom — "está tan transparente que no se define nada":
     *  · the mini-player pill, because `BottomSheet` does not compose its ground slot while collapsed,
     *    so the 7 % landed straight on the NavHost (fixed in `AuraMiniPlayer` with an opaque base);
     *  · Biblioteca's three floating actions, which DO have `auraScreenBackground` under them but are
     *    drawn ABOVE the scrolling list, so what is actually behind the glass is a grid of album
     *    covers. 7 % white over a bright cover is not a surface at all: the cover reads through the
     *    label.
     *
     * ## Why it is opaque and not a blur
     * The owner asked for the buttons to be "borrosos" so their content can be read. A live backdrop
     * blur is precisely what the thermal contract forbids (it samples and blurs the frame under the
     * control on every frame, for as long as the control is on screen). Compositing the render's own
     * film onto an opaque base costs nothing, and it is *more* legible than a blur, not less: the
     * label lands at ~14.8:1 over this plate (`#EAF2FF` on `#191E28`) — a blurred bright cover would
     * still leave a light backdrop under light text.
     *
     * The base is [GroundRaised] rather than [Ground] so a floating control reads one step ABOVE the
     * screen it floats over instead of punching a hole in it, which is the same base
     * `AuraMiniPlayer` already stands on.
     *
     * Wherever a `SurfaceFill` control DOES have the ground directly behind it (every card, chip, sort
     * pill and search field inside a list) keep using [SurfaceFill]: on the ground the two are within
     * one step of each other and the film is the render's.
     *
     * A `Color` is an inline value class, so this getter allocates nothing on the heap.
     */
    val FloatingFill: Color get() = SurfaceFill.compositeOver(GroundRaised)

    /**
     * Card / chip hairline: `rgba(255,255,255,.10)`.
     *
     * It stays the hairline of a [FloatingFill] control too, and it does not get weaker there: on the
     * ground the render's own pairing separates fill from edge by only 1.08:1, whereas 10 % white over
     * the opaque plate is 1.36:1 — a *more* defined edge than the one the render draws, so there is no
     * second "floating" line value to keep in step with this one.
     */
    val SurfaceLine = Color.White.copy(alpha = 0.10f)

    /** Section separators (nav bar top, engine status bar top): `rgba(255,255,255,.08….09)`. */
    val Divider = Color.White.copy(alpha = 0.09f)

    /** Empty progress track: `rgba(255,255,255,.13)`. */
    val TrackEmpty = Color.White.copy(alpha = 0.13f)

    /** "SONANDO" row highlight: fill `rgba(accent,.10)` + border `rgba(accent,.25)`. */
    val NowPlayingFill: Color get() = accent.nowPlayingFill
    val NowPlayingLine: Color get() = accent.nowPlayingLine

    /** "Interfaz nueva" callout card: fill `rgba(122,92,255,.12)` + border `rgba(122,92,255,.30)`. */
    val BetaFill = Color(0xFF7A5CFF).copy(alpha = 0.12f)
    val BetaLine = Color(0xFF7A5CFF).copy(alpha = 0.30f)

    // ── Brushes ───────────────────────────────────────────────────────────────────────────────────
    /**
     * `linear-gradient(140deg, teal, blue 55%, violet)` — the play button. The ONLY saturated fill on
     * a screen; do not reuse it for decoration.
     *
     * Built once per accent inside [AuraAccent], never per read: this getter is called from a
     * composition body on every player recomposition, and a `Brush` is a heap allocation.
     */
    val PlayButtonGradient: Brush get() = accent.playButtonGradient

    // The render's `linear-gradient(90deg, teal, blue 60%, violet)` progress fill is intentionally
    // absent: the timeline is the shared `PlayerProgressSlider` (four user-selectable styles), which
    // takes flat SliderColors — see AuraPlayer.auraSliderColors, which tints it with Teal/TrackEmpty.

    /**
     * `.cv { linear-gradient(145deg, teal, blue 45%, violet) }` — artwork placeholder while a cover
     * loads or when there is none. Variants exist in the render (teal→violet, violet→teal, …); use
     * [coverPlaceholder] with a stable seed so a given track always gets the same one.
     */
    val CoverPlaceholder: Brush get() = accent.coverPlaceholder

    /**
     * Deterministic artwork placeholder. [seed] should be the media id (or any stable per-item string)
     * so the same row keeps the same colours across scrolls and process deaths.
     */
    fun coverPlaceholder(seed: String?): Brush {
        val variants = accent.coverVariants
        val index = ((seed?.hashCode() ?: 0) and 0x7FFFFFFF) % variants.size
        return variants[index]
    }

    // ── Cover corner radius ───────────────────────────────────────────────────────────────────────

    /**
     * "Radio de esquina de la miniatura" (Ajustes ▸ Apariencia ▸ Reproductor), as the redesign's radii.
     *
     * The setting stores a raw float, 0..45, default 3. The classic player renders it as
     * `RoundedCornerShape((pref * 2).dp)` (Thumbnail.kt:172) — taking that mapping literally here would
     * have re-cut every cover in the redesign from the render's 11 dp to 6 dp for a user who never
     * touched the control, i.e. changed the design to honour a default. So the stored value is applied
     * as a RATIO of the shipped default instead: `pref / 3`. At 3 these are exactly the render's 11 dp
     * and 20 dp; 0 is square; large values saturate because `RoundedCornerShape` clamps a radius to half
     * the box, which is a circle — the same thing 45 does to the classic player's artwork.
     */
    val coverCorners: AuraCoverCorners get() = corners
}

/**
 * The redesign's two cover radii, already cut into shapes.
 *
 * Read through `AuraShapes.Artwork` / `AuraShapes.PlayerArtwork`, which is where every cover in the new
 * UI — and, through `Items.AuraItemSkin`, every classic list thumbnail while the flag is on — takes its
 * shape from. Built once per settings change so the getters hand back an existing object instead of
 * allocating a `RoundedCornerShape` on every row of every list.
 */
@Immutable
class AuraCoverCorners(val scale: Float) {
    /**
     * Artwork in a list row — render `border-radius:8px` ×1.4 = 11 dp at the default.
     *
     * CAPPED, and the cap is load-bearing. `RoundedCornerShape` silently clamps a radius to HALF the
     * box, so the smallest artwork the app draws (48 dp rows) turns into a perfect circle from 24 dp up
     * — which the preference reaches at 7 of 10 (11 × 7/3 ≈ 26 dp). The user asking for "more rounded"
     * is not asking for circles, and because `Items.AuraItemSkin` hands this same shape to every
     * CLASSIC list row while the flag is on, the same stored value would otherwise render differently
     * in the two interfaces. 16 dp keeps a visible corner on a 48 dp thumbnail (a third of the box) and
     * still reads as fully rounded at the top of the scale.
     */
    val row: RoundedCornerShape = RoundedCornerShape((11f * scale).coerceAtMost(16f).dp)

    /**
     * Player artwork — render `border-radius:14px` ×1.4 = 20 dp at the default.
     *
     * Capped for the same reason, but far higher: the player cover is never smaller than ~280 dp, so it
     * cannot round to a circle. The ceiling exists only so the top of the scale stays a rounded square
     * rather than a lozenge.
     */
    val player: RoundedCornerShape = RoundedCornerShape((20f * scale).coerceAtMost(44f).dp)

    override fun equals(other: Any?): Boolean = other is AuraCoverCorners && scale == other.scale
    override fun hashCode(): Int = scale.hashCode()

    companion object {
        /** The render's own radii, i.e. the shipped default of 3. */
        val Render = AuraCoverCorners(1f)
    }
}

/**
 * The three accents in force plus every value derived from them, computed ONCE per theme change.
 *
 * Immutable and cached on purpose: [AuraPalette.PlayButtonGradient] and [AuraPalette.coverPlaceholder]
 * are read from composition bodies (the player recomposes on every position tick), so building a
 * `Brush` inside the getter would be a per-recomposition allocation on the hottest screen in the app.
 */
@Immutable
class AuraAccent(
    val primary: Color,
    val secondary: Color,
    val tertiary: Color,
) {
    val navIndicator: Color = primary.copy(alpha = 0.12f)
    val nowPlayingFill: Color = primary.copy(alpha = 0.10f)
    val nowPlayingLine: Color = primary.copy(alpha = 0.25f)

    val playButtonGradient: Brush = Brush.linearGradient(
        colorStops = arrayOf(0f to primary, 0.55f to secondary, 1f to tertiary),
    )

    val coverPlaceholder: Brush = Brush.linearGradient(
        colorStops = arrayOf(0f to primary, 0.45f to secondary, 1f to tertiary),
    )

    val coverVariants: List<Brush> = listOf(
        Brush.linearGradient(listOf(primary, secondary, tertiary)),
        Brush.linearGradient(listOf(secondary, tertiary)),
        Brush.linearGradient(listOf(tertiary, primary)),
        Brush.linearGradient(listOf(primary, secondary)),
        Brush.linearGradient(listOf(secondary, primary)),
    )

    override fun equals(other: Any?): Boolean = other is AuraAccent &&
        primary == other.primary && secondary == other.secondary && tertiary == other.tertiary

    override fun hashCode(): Int =
        (primary.hashCode() * 31 + secondary.hashCode()) * 31 + tertiary.hashCode()

    companion object {
        /** The render's own trio. Used whenever the user has not chosen an accent — see [AuraPaletteSync]. */
        val Brand = AuraAccent(
            primary = Color(0xFF3FE7CE),
            secondary = Color(0xFF2FA6F0),
            tertiary = Color(0xFF6A5BFF),
        )

        /**
         * The render's hue spacing, measured off its own three literals: teal 170°, blue 203°,
         * violet 246°. The trio is rebuilt around the USER'S hue by rotating by the same two offsets,
         * so a red accent gives red → orange → amber and a blue one gives blue → indigo → violet: the
         * gradient keeps the render's shape, only its position on the wheel moves.
         */
        private const val SECONDARY_HUE_OFFSET = 33f
        private const val TERTIARY_HUE_OFFSET = 76f

        /**
         * Body text is the binding constraint, not decoration: the accent carries 11–12 sp technical
         * type ("◆ HI-RES", the codec chips, "SONANDO", the queue's radio caption), which WCAG 2.1 puts
         * at 4.5:1 — above the 3.0 the theme's own [ensureLegibleOn] defaults to for large text and
         * graphical objects.
         */
        private const val MIN_TEXT_CONTRAST = 4.5f

        /**
         * Builds the trio from the accent the app is actually wearing, clamped against the ground the
         * redesign is actually painting.
         *
         * **The contrast guarantee, and why it holds for all 46 swatches plus any typed hex.**
         * [ensureLegibleOn] keeps the hue and the saturation and walks the HSV *value* until the pair
         * clears [MIN_TEXT_CONTRAST]; on a near-black ground that means "lighten until readable", and it
         * sweeps the whole 0..1 range, so the dark tier (Rojo oscuro `#7F0000`, Azul marino `#0A2352`,
         * Carbón `#23272B`) comes back as a legible light tint of the same hue instead of a swatch that
         * cannot be read. If value alone cannot get there — only possible for a pick that is already
         * near-white, i.e. Marfil `#F2EDE3`, which passes on the first test anyway — it falls back to
         * `onColorFor(ground)`, white on this ground, which is readable by construction. There is no
         * path out of this function that returns a colour below 4.5:1 on [ground].
         *
         * The hue-rotated stops are clamped INDEPENDENTLY rather than inheriting the primary's value:
         * hue moves luminance a long way at a fixed value (a yellow is ~4× a blue), so a trio clamped
         * only on its first member would ship two illegible stops.
         */
        fun from(seed: Color, ground: Color): AuraAccent {
            val (hue, saturation, value) = ColorPickerConversions.colorToHsv(seed)
            fun stop(offset: Float) = ensureLegibleOn(
                color = ColorPickerConversions.hsvToColor(hue + offset, saturation, value),
                against = ground,
                minRatio = MIN_TEXT_CONTRAST,
            )
            return AuraAccent(
                primary = ensureLegibleOn(seed, ground, MIN_TEXT_CONTRAST),
                secondary = stop(SECONDARY_HUE_OFFSET),
                tertiary = stop(TERTIARY_HUE_OFFSET),
            )
        }
    }
}

/**
 * Resolves the redesign's accent, ground and cover radius from the user's Apariencia settings and
 * publishes them to [AuraPalette].
 *
 * ## Once per theme change, never per composable and never per frame
 * The only work is one `remember` keyed on (the accent the app is wearing, the ground, the
 * "has the user chosen anything?" verdict) — so the HSV walk in [AuraAccent.from] runs on a theme
 * change and on nothing else. [AuraPalette.apply] then writes global snapshot state, so all ~143 call
 * sites see one resolution rather than each doing its own. The write is idempotent, so composing this
 * from several places at once (the gate, the nav bar, the mini player) costs a comparison.
 *
 * ## Where the accent comes from
 * [MaterialTheme.colorScheme] `primary` — i.e. whatever the app's own theme engine produced, so the
 * swatch, the typed hex, "Intensidad del color", the named preset and the dynamic artwork/wallpaper
 * accent all arrive already resolved. There is deliberately no second copy of that engine here; a
 * second copy is how the two halves of the app drifted apart in the first place.
 *
 * ## When it does NOT follow
 * When the user has chosen nothing at all — the stored accent is still the sentinel, no preset is
 * active and the dynamic theme is off — the app's own scheme is generated from [DefaultThemeColor], a
 * red the user never asked for. In that one state the redesign keeps its shipped teal/blue/violet,
 * which is what "keep the fixed values as the fallback" means. In every other state it follows, so the
 * six new screens and the ~89 classic ones are the same colour.
 */
@Composable
fun AuraPaletteSync() {
    val scheme = MaterialTheme.colorScheme
    val pureBlack by rememberPreference(PureBlackKey, defaultValue = false)
    val selectedThemeColorInt by rememberPreference(
        SelectedThemeColorKey,
        defaultValue = DefaultThemeColor.toArgb(),
    )
    val themePreset by rememberEnumPreference(AppThemePresetKey, defaultValue = ThemePreset.NONE)
    val dynamicTheme by rememberPreference(DynamicThemeKey, defaultValue = true)
    val cornerRadius by rememberPreference(ThumbnailCornerRadiusKey, defaultValue = 3f)

    val followsUser = selectedThemeColorInt != DefaultThemeColor.toArgb() ||
        themePreset != ThemePreset.NONE ||
        dynamicTheme

    // The ground the accent is measured against is the one that will be PAINTED, AMOLED included —
    // measuring against `#060A12` and then drawing on `#000000` would understate the contrast.
    val ground = if (pureBlack) Color.Black else scheme.surface
    val resolved = remember(followsUser, scheme.primary, ground) {
        if (followsUser) AuraAccent.from(scheme.primary, ground) else AuraAccent.Brand
    }
    // 3 is the shipped default of ThumbnailCornerRadiusKey and 1.0 the identity multiplier, so a user
    // who never touched the control gets the render's radii to the pixel.
    val scale = (cornerRadius / 3f).coerceAtLeast(0f)
    val corners = remember(scale) {
        if (scale == 1f) AuraCoverCorners.Render else AuraCoverCorners(scale)
    }

    AuraPalette.apply(resolved, pureBlack, corners)
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
        /**
         * The render's own bloom, in the accent the user chose. Used whenever a track has no extracted
         * colours (and on every screen with no now-playing context at all).
         *
         * A getter, not a `val`: it is built from [AuraPalette]'s accents, which move with the theme.
         * Equality is structural, so [AuraBloomCache] and `rememberAuraBloom`'s `LaunchedEffect` key
         * still compare two brands as equal.
         */
        val Brand: AuraBloomColors
            get() = AuraBloomColors(
                topLeft = AuraPalette.Teal.copy(alpha = 0.32f),
                topRight = AuraPalette.Violet.copy(alpha = 0.30f),
                center = AuraPalette.Blue.copy(alpha = 0.24f),
            )
    }
}
