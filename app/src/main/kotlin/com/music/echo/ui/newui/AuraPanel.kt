package iad1tya.echo.music.ui.newui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.Card
import androidx.compose.material3.CardColors
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// ──────────────────────────────────────────────────────────────────────────────────────────────────
// "Interfaz nueva" — the BESPOKE-CARD seam
// ──────────────────────────────────────────────────────────────────────────────────────────────────
//
// `ui/component/Items.kt` owns every song/album/artist row, and `ui/component/Material3SettingsGroup`
// owns every settings row; between them they carry ~49 screens into the redesign. What neither can
// reach is the handful of screens that hand-roll their own `Card`s and therefore share no primitive
// with anybody: Reconocer música (+ su historial), Escuchar juntos (+ sus ajustes), Acerca de, Qobuz
// and the Estadísticas sheet.
//
// Those screens are NOT one family, so there is no seam to restyle. But they do all draw the SAME
// object: a rounded, flat, 0-elevation card holding a titled block of content — 26 call sites of
// `Card(shape = RoundedCornerShape(20…28.dp), containerColor = <some low-contrast fill>, elevation =
// 0.dp)`. That, and only that, is what [AuraPanel] extracts. Everything else on those screens (the
// listening animation, the room code, the avatar strip, the counters) stays where it lives, because
// it does NOT repeat and an abstraction for a single use is worse than the literal.
//
// The three rules the other two seams follow, kept here:
//  · ONE resolution point. [rememberAuraPanelSkin] is the only place in the redesign of these screens
//    that reads the flag. Each screen calls it ONCE, at the top, and passes the result down as a
//    plain parameter — never once per card and never once per row, so a screen with 13 panels
//    (Acerca de) still holds exactly one DataStore subscription.
//  · With the flag OFF every expression reduces to the ORIGINAL literal: [AuraPanelSkin.enabled] is
//    `false`, so each `if (skin.enabled) … else X` is `X`, the panel's `border` is `null` (the `Card`
//    default) and its shape/colors/elevation are precisely the values the caller was already passing.
//  · The redesign is a DARK design — [AuraPalette] has no light variant — but these screens follow
//    the user's theme, so the skin resolves against the ambient surface. On a dark ground it is the
//    render's `#EAF2FF` / teal; on a light one it keeps Material's ink and only the type, the rhythm,
//    the radii and the flat fill change. Painting `OnGround` unconditionally would put near-white
//    text on a near-white surface for anyone running the beta in light mode.
//
// ## Why the panel fill is TRANSLUCENT
// The render's card is `rgba(255,255,255,.07)` over the bloom — a wash, not a plate — and that is
// also the right answer for where these panels are actually hosted, which was checked rather than
// assumed:
//  · `ListenTogetherScreen` paints its own `background` behind the list, `AboutScreen` its `surface`,
//    `RecognitionHistoryScreen` its scaffold; a wash lands correctly on all three.
//  · `ListenTogetherSettings` draws its server chooser INSIDE `DefaultDialog`, i.e. on a dialog
//    surface, and `ActivityHistoryBottomSheet` draws on a `surfaceContainerLow` sheet. An opaque
//    fill pre-composited over `surface` would stamp mismatched rectangles across both — the exact
//    trap `Material3SettingsGroup` documented for `SettingDialoge`.
//  · Nothing here drags or reorders (that was `Items.kt`'s reason to stay opaque: its rows are
//    dragged in a playlist and in the queue), so no translucent surface is ever composited over
//    moving content.

/**
 * Everything a redesigned bespoke card needs, resolved once per SCREEN.
 *
 * [enabled] is the "Interfaz nueva" master switch; [darkGround] says whether the ambient surface can
 * carry the render's own palette.
 */
@Immutable
data class AuraPanelSkin(
    val enabled: Boolean = false,
    val darkGround: Boolean = false,
    /** Primary ink: card titles, values, the room code. */
    val ink: Color = Color.Unspecified,
    /** Secondary ink — the render's `opacity:.55`. Descriptions, subtitles. */
    val inkMuted: Color = Color.Unspecified,
    /** Section-label ink — the render's `.mh { opacity:.42 }`. */
    val inkFaint: Color = Color.Unspecified,
    /** Teal on a dark ground, the theme's primary on a light one. Glyphs, active markers. */
    val accent: Color = Color.Unspecified,
    /** Card fill — the render's `rgba(255,255,255,.07)`. Translucent on purpose; see the note above. */
    val fill: Color = Color.Transparent,
    /** Card hairline — the render's `rgba(255,255,255,.10)`. */
    val line: Color = Color.Transparent,
    /** Separator INSIDE a card — the render's `rgba(255,255,255,.09)`. */
    val hairline: Color = Color.Transparent,
)

/** Classic. Every field is the neutral value, and [AuraPanelSkin.enabled] gates every use of it. */
private val ClassicPanelSkin = AuraPanelSkin()

/**
 * Resolves the skin. The ONLY flag read in this seam — call it once per screen and pass it down.
 */
@Composable
fun rememberAuraPanelSkin(): AuraPanelSkin {
    val newUi = rememberNewUiEnabled()
    val surface = MaterialTheme.colorScheme.surface
    val onSurface = MaterialTheme.colorScheme.onSurface
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
    val primary = MaterialTheme.colorScheme.primary
    return remember(newUi, surface, onSurface, onSurfaceVariant, primary) {
        if (!newUi) {
            ClassicPanelSkin
        } else {
            val darkGround = surface.luminance() < 0.4f
            AuraPanelSkin(
                enabled = true,
                darkGround = darkGround,
                ink = if (darkGround) AuraPalette.OnGround else onSurface,
                inkMuted = if (darkGround) AuraPalette.OnGroundMuted else onSurfaceVariant,
                inkFaint = if (darkGround) AuraPalette.OnGroundGhost else onSurfaceVariant.copy(alpha = 0.75f),
                accent = if (darkGround) AuraPalette.Teal else primary,
                // A white wash reads as a card on the near-black ground; on a light ground it would be
                // invisible, so there the wash is the theme's own ink at the same low alpha.
                fill = if (darkGround) AuraPalette.SurfaceFill else onSurface.copy(alpha = 0.05f),
                line = if (darkGround) AuraPalette.SurfaceLine else onSurface.copy(alpha = 0.12f),
                hairline = if (darkGround) AuraPalette.Divider else onSurface.copy(alpha = 0.09f),
            )
        }
    }
}

/**
 * The one card those screens share: flat, rounded, hairlined, 0-elevation.
 *
 * The CLASSIC look is passed in rather than guessed, because the six screens do not agree on it
 * (24 dp / `surfaceVariant @ .3`, 28 dp / `surfaceContainerLow`, 20 dp / the `Card` default, …). With
 * the flag off this composable IS the caller's original `Card` — same shape, same colours, same
 * elevation, and `border = null`, which is the `Card` default.
 */
@Composable
fun AuraPanel(
    skin: AuraPanelSkin,
    classicShape: Shape,
    classicColors: CardColors,
    modifier: Modifier = Modifier,
    classicElevation: Dp = 0.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier,
        shape = if (skin.enabled) AuraShapes.Card else classicShape,
        colors = if (skin.enabled) {
            CardDefaults.cardColors(containerColor = skin.fill, contentColor = skin.ink)
        } else {
            classicColors
        },
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (skin.enabled) 0.dp else classicElevation,
        ),
        border = if (skin.enabled) BorderStroke(1.dp, skin.line) else null,
        content = content,
    )
}
