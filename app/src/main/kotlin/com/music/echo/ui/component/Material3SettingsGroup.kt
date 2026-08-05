

package iad1tya.echo.music.ui.component

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import iad1tya.echo.music.ui.newui.AuraIcons
import iad1tya.echo.music.ui.newui.AuraPalette
import iad1tya.echo.music.ui.newui.AuraShapes
import iad1tya.echo.music.ui.newui.AuraSpacing
import iad1tya.echo.music.ui.newui.AuraType
import iad1tya.echo.music.ui.newui.rememberNewUiEnabled
import iad1tya.echo.music.ui.utils.rememberIsTvOrCar
import iad1tya.echo.music.ui.utils.scrollToOnHighlight
import iad1tya.echo.music.ui.utils.tvFocusableItem


// ──────────────────────────────────────────────────────────────────────────────────────────────────
// "Interfaz nueva" seam
// ──────────────────────────────────────────────────────────────────────────────────────────────────
//
// [Material3SettingsGroup] is the single owner of every settings row and group card in the app: 341
// call sites across 18 sub-screens (Apariencia, Contenido, Reproductor, Sonido, Privacidad,
// Almacenamiento, Cuentas, Copias, Rendimiento, IA, Romanización, Escuchar juntos, Actualización,
// Scrobbling, Qobuz, Sincronización, Descifrado, Acerca de) plus the settings dialogs, the Axion EQ,
// the migration screen and the Spotify import. The redesigned index (`ui/newui/AuraSettingsScreen`)
// was rebuilt, but everything BEHIND it still looked classic — restyling this one file is what makes
// all of them adopt the new design without rewriting a single screen.
//
// The rules this seam exists to keep — the same three `ui/component/Items.kt` follows:
//  · ONE resolution point. [rememberAuraSettingsSkin] is read exactly once PER GROUP (not per row,
//    and certainly not per glyph) and handed down as a plain parameter, because the only consumer is
//    the private row below. That is ZERO extra `rememberPreference` per row — a group of eight rows
//    still holds exactly one DataStore subscription.
//  · With the flag OFF every expression below reduces to the ORIGINAL literal: `skin.enabled` is
//    `false`, so each `if (skin.enabled) … else X` is `X`, each `.then(if (skin.enabled) … else
//    Modifier)` is identity, `border = null` is the `Card` default, and the two added composables
//    (the hairline, the chevron) are not composed at all. The classic path is provably unchanged
//    rather than merely believed to be.
//  · The redesign is a DARK design — `AuraPalette` has no light variant and every rebuilt screen
//    paints `AuraPalette.Ground`. These rows, unlike those screens, are drawn on whatever background
//    the CLASSIC sub-screen has, which follows the user's theme. So the skin resolves its ink against
//    the ambient surface: on a dark ground it is the render's `#EAF2FF` / teal; on a light one it
//    keeps Material's ink and only the type, the rhythm, the radii and the flat sheet change.
//    Painting `OnGround` unconditionally would have put near-white text on a near-white surface for
//    anyone running the beta in light mode.
//
// What the render (`8-DISENO-REAL-con-iconos.html`, the "Ajustes" cell) asks for, and what this file
// therefore does under the flag:
//  · a FLAT sheet instead of stacked grey cards — the row no longer paints a plate of its own, so it
//    sits directly on the page exactly as `.mrow` does, with a hairline between consecutive rows;
//  · a bare teal glyph — the classic `primary @ 0.1` rounded icon plate is dropped, while the painter
//    keeps its own size and alignment so nothing shifts;
//  · `AuraType` for the label and the description, `AuraType.MenuGroupLabel` for the group header;
//  · the render's `.mrow` chevron on rows that lead somewhere and carry no control of their own.
//
// ## Why the flat row paints NOTHING instead of an opaque `surface`
// The obvious way to build a flat sheet is to fill the row with the screen's own `surface`, which on
// every scheme in `ui/theme` equals `background` — so on the 18 settings SCREENS the row would vanish
// into the page, correctly. It is wrong everywhere else: `ui/screens/SettingDialoge.kt` hosts three of
// these groups inside a `Card` filled with `surfaceContainerHigh`, and an opaque `surface` row would
// stamp five mismatched rectangles across that dialog. A `surface` fill therefore buys nothing on the
// hosts where it is right and breaks the one host where it is wrong.
//
// Not painting is safe HERE, specifically, and the check was done rather than assumed: a settings row
// never drags and never reorders (that was the hazard in `Items.kt`, whose rows are dragged in a
// playlist and in the queue), nothing translucent is composited over moving content, and the classic
// row is ALREADY 70 % see-through (`surfaceVariant @ 0.3`) with no ill effect. The highlight wash is
// likewise left translucent — `AuraPalette.NowPlayingFill` is a `.10` alpha in the render too — so it
// composites correctly over the page background AND over that dialog card, instead of being
// pre-composited over a `surface` that is only sometimes what is actually behind it.

/**
 * Everything a redesigned settings row needs, resolved once per group.
 *
 * [enabled] is the "Interfaz nueva" master switch; [darkGround] says whether the ambient surface can
 * carry the render's own palette.
 */
@Immutable
data class AuraSettingsSkin(
    val enabled: Boolean = false,
    val darkGround: Boolean = false,
    /** Row title ink. */
    val ink: Color = Color.Unspecified,
    /** Description / secondary ink — the render's `opacity:.55`. */
    val inkMuted: Color = Color.Unspecified,
    /** Group header ink — the render's `.mh { opacity:.42 }`. */
    val inkFaint: Color = Color.Unspecified,
    /** Teal on a dark ground, the theme's primary on a light one. The leading glyph. */
    val accent: Color = Color.Unspecified,
    /**
     * Resting row fill. The render's settings list is a FLAT sheet with no per-row plate at all, and
     * this row is drawn on two different backgrounds (the page on the 18 settings screens, a
     * `surfaceContainerHigh` card in `SettingDialoge`), so it paints NOTHING and lets whichever one is
     * behind it show. See the note at the top of the file for why an opaque `surface` is wrong here.
     */
    val fill: Color = Color.Transparent,
    /** `isHighlighted` wash + hairline. Translucent on purpose, so it lands on either background. */
    val highlightFill: Color = Color.Transparent,
    val highlightLine: Color = Color.Transparent,
    /** Separator between two consecutive rows — the render's `rgba(255,255,255,.09)`. */
    val hairline: Color = Color.Transparent,
    /** The render's `.mrow` chevron: `opacity:.3`. */
    val chevron: Color = Color.Transparent,
)

/** Classic. Every field is the neutral value, and [AuraSettingsSkin.enabled] gates every use of it. */
private val ClassicSettingsSkin = AuraSettingsSkin()

/**
 * Resolves the skin. The ONLY place in this file that reads the flag — one DataStore subscription per
 * GROUP, whatever the group's row count.
 */
@Composable
private fun rememberAuraSettingsSkin(): AuraSettingsSkin {
    val newUi = rememberNewUiEnabled()
    val surface = MaterialTheme.colorScheme.surface
    val onSurface = MaterialTheme.colorScheme.onSurface
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
    val primary = MaterialTheme.colorScheme.primary
    return remember(newUi, surface, onSurface, onSurfaceVariant, primary) {
        if (!newUi) {
            ClassicSettingsSkin
        } else {
            val darkGround = surface.luminance() < 0.4f
            val accent = if (darkGround) AuraPalette.Teal else primary
            AuraSettingsSkin(
                enabled = true,
                darkGround = darkGround,
                ink = if (darkGround) AuraPalette.OnGround else onSurface,
                inkMuted = if (darkGround) AuraPalette.OnGroundMuted else onSurfaceVariant,
                inkFaint = if (darkGround) AuraPalette.OnGroundGhost else onSurfaceVariant.copy(alpha = 0.75f),
                accent = accent,
                fill = Color.Transparent,
                highlightFill = accent.copy(alpha = 0.10f),
                highlightLine = accent.copy(alpha = 0.25f),
                hairline = if (darkGround) AuraPalette.Divider else onSurface.copy(alpha = 0.09f),
                chevron = if (darkGround) AuraPalette.OnGroundDisabled else onSurface.copy(alpha = 0.30f),
            )
        }
    }
}


@Composable
fun Material3SettingsGroup(
    title: String? = null,
    compact: Boolean = false,
    scrollState: ScrollState? = null,
    items: List<Material3SettingsItem>
) {
    val skin = rememberAuraSettingsSkin()
    Column(
        modifier = Modifier
            .fillMaxWidth()
    ) {

        title?.let {
            Text(
                text = it,
                // The render's `.mh`: tracked monospace at low opacity. The STRING is never touched —
                // no `.uppercase()`, no retype — so the Spanish (and its accents) stays exactly what
                // the screen already passes, resource or hardcoded literal.
                style = if (skin.enabled) AuraType.MenuGroupLabel else MaterialTheme.typography.labelLarge,
                color = if (skin.enabled) skin.inkFaint else MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(
                    bottom = if (skin.enabled) (if (compact) 5.dp else 7.dp) else if (compact) 4.dp else 8.dp,
                    top = if (skin.enabled) (if (compact) 9.dp else 15.dp) else if (compact) 4.dp else 8.dp
                )
            )
        }


        Column(
            modifier = Modifier.fillMaxWidth(),
            // The redesign is a continuous sheet: the 4 dp gutter that separated the classic cards
            // would cut it back into slices.
            verticalArrangement = Arrangement.spacedBy(if (skin.enabled) 0.dp else 4.dp)
        ) {
            items.forEachIndexed { index, item ->
                val shape = if (skin.enabled) {
                    // Flat, except a highlighted row, which the render draws as a rounded wash.
                    if (item.isHighlighted) AuraShapes.Highlight else RectangleShape
                } else when {
                    items.size == 1 -> RoundedCornerShape(24.dp)
                    index == 0 -> RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp, bottomStart = 6.dp, bottomEnd = 6.dp)
                    index == items.size - 1 -> RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp, bottomStart = 24.dp, bottomEnd = 24.dp)
                    else -> RoundedCornerShape(6.dp)
                }

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .animateContentSize(),
                    shape = shape,
                    colors = CardDefaults.cardColors(
                        containerColor = if (skin.enabled) {
                            if (item.isHighlighted) skin.highlightFill else skin.fill
                        } else if (item.isHighlighted)
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                        else
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                    // `null` IS the `Card` default, so the classic card is byte-identical.
                    border = if (skin.enabled && item.isHighlighted) BorderStroke(1.dp, skin.highlightLine) else null
                ) {
                    Material3SettingsItemRow(item = item, compact = compact, scrollState = scrollState, skin = skin)
                }

                // Hairline between two consecutive rows. Never composed on the classic path, never
                // drawn against a highlighted neighbour (it would collide with that row's rounded
                // wash) and never after the last row.
                if (skin.enabled &&
                    index < items.size - 1 &&
                    !item.isHighlighted &&
                    !items[index + 1].isHighlighted
                ) {
                    Spacer(
                        Modifier
                            .padding(start = if (compact) 14.dp else 20.dp)
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(skin.hairline)
                    )
                }
            }
        }
    }
}


@Composable
private fun Material3SettingsItemRow(
    item: Material3SettingsItem,
    compact: Boolean = false,
    scrollState: ScrollState? = null,
    skin: AuraSettingsSkin
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .tvFocusableItem(rememberIsTvOrCar())
            .clickable(
                enabled = item.enabled && item.onClick != null,
                onClick = { item.onClick?.invoke() }
            )
            .then(if (scrollState != null) Modifier.scrollToOnHighlight(scrollState, item.isHighlighted) else Modifier)
            // The redesign's rhythm is tighter than the classic 16 dp, and a compact row with no icon
            // would fall under the 48 dp touch minimum on the way down — so the minimum is asserted
            // here rather than assumed. Identity on the classic path.
            .then(if (skin.enabled) Modifier.sizeIn(minHeight = AuraSpacing.MinTouchTarget) else Modifier)
            .padding(
                horizontal = if (compact) 14.dp else 20.dp,
                vertical = if (skin.enabled) (if (compact) 9.dp else 13.dp) else if (compact) 10.dp else 16.dp
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {

        item.icon?.let { icon ->
            // Resolved once instead of twice: the badge and no-badge branches below carried the exact
            // same tint expression. The `!enabled` arm keeps the inventory's 38 % alpha.
            val iconTint = if (!item.enabled)
                (if (skin.enabled) skin.ink.copy(alpha = 0.38f) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f))
            else if (item.isHighlighted)
                (if (skin.enabled) skin.accent else MaterialTheme.colorScheme.primary)
            else
                (if (skin.enabled) skin.accent.copy(alpha = 0.9f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.9f))

            Box(
                modifier = Modifier
                    // The frame keeps its classic size in BOTH paths: an untinted service logo is
                    // drawn to FILL it, so shrinking it would shrink every Last.fm / ListenBrainz /
                    // Spotify mark, and the text column would shift the moment the flag flipped.
                    .size(if (compact) 34.dp else 40.dp)
                    .clip(item.iconShape ?: RoundedCornerShape(12.dp))
                    .background(
                        if (item.tintIcon) {
                            // The render has no icon plate — just the bare teal glyph.
                            if (skin.enabled) Color.Transparent
                            else MaterialTheme.colorScheme.primary.copy(
                                alpha = if (item.isHighlighted) 0.15f else 0.1f
                            )
                        } else {
                            androidx.compose.ui.graphics.Color.Transparent
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (item.showBadge) {
                    BadgedBox(
                        badge = {
                            Badge(
                                containerColor = MaterialTheme.colorScheme.error
                            )
                        }
                    ) {
                        if (item.tintIcon) {
                            Icon(
                                painter = icon,
                                contentDescription = null,
                                tint = iconTint,
                                modifier = Modifier.size(if (compact) 20.dp else 24.dp)
                            )
                        } else {
                            Image(
                                painter = icon,
                                contentDescription = null,
                                modifier = Modifier.size(if (compact) 34.dp else 40.dp),
                                contentScale = ContentScale.Crop
                            )
                        }
                    }
                } else {
                    if (item.tintIcon) {
                        Icon(
                            painter = icon,
                            contentDescription = null,
                            tint = iconTint,
                            modifier = Modifier.size(if (compact) 20.dp else 24.dp)
                        )
                    } else {
                        Image(
                            painter = icon,
                            contentDescription = null,
                            modifier = Modifier.size(if (compact) 34.dp else 40.dp),
                            contentScale = ContentScale.Crop
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(if (compact) 12.dp else 16.dp))
        }


        Column(
            modifier = Modifier.weight(1f)
        ) {

            // `AuraType.MenuLabel` is the SAME style the redesigned index (`AuraSettingsScreen`) gives
            // its own rows, which is the whole point: the owner's report was that the index looked new
            // and everything behind it did not.
            ProvideTextStyle(
                if (skin.enabled)
                    AuraType.MenuLabel.copy(
                        color = if (!item.enabled)
                            skin.ink.copy(alpha = 0.38f)
                        else
                            skin.ink
                    )
                else
                    MaterialTheme.typography.titleMedium.copy(
                        color = if (!item.enabled)
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                        else
                            MaterialTheme.colorScheme.onSurface
                    )
            ) {
                item.title()
            }


            item.description?.let { desc ->
                Spacer(modifier = Modifier.height(2.dp))
                // `RowSubtitle` rather than the index's 13 sp `CalloutSubtitle`: it is 14 sp, the same
                // size as the `bodyMedium` it replaces, so the long explanatory descriptions on these
                // screens keep their line count instead of reflowing.
                ProvideTextStyle(
                    if (skin.enabled)
                        AuraType.RowSubtitle.copy(
                            color = if (!item.enabled)
                                skin.ink.copy(alpha = 0.38f)
                            else
                                skin.inkMuted
                        )
                    else
                        MaterialTheme.typography.bodyMedium.copy(
                            color = if (!item.enabled)
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                            else
                                MaterialTheme.colorScheme.onSurfaceVariant
                        )
                ) {
                    desc()
                }
            }
        }


        item.trailingContent?.let { trailing ->
            Spacer(modifier = Modifier.width(8.dp))
            trailing()
        }

        // The render's `.mrow` chevron. Only on a row that LEADS somewhere and owns no control of its
        // own — a row whose trailing slot already holds a switch, a value or a button keeps that, and
        // a row that is pure text gets nothing, so the chevron never promises an action that is not
        // there. Not composed at all on the classic path.
        if (skin.enabled && item.trailingContent == null && item.onClick != null) {
            Spacer(modifier = Modifier.width(8.dp))
            Icon(
                imageVector = AuraIcons.ChevronRight,
                contentDescription = null,
                tint = if (item.enabled) skin.chevron else skin.chevron.copy(alpha = skin.chevron.alpha * 0.38f),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}


data class Material3SettingsItem(
    val icon: Painter? = null,
    val title: @Composable () -> Unit,
    val description: (@Composable () -> Unit)? = null,
    val trailingContent: (@Composable () -> Unit)? = null,
    val showBadge: Boolean = false,
    val isHighlighted: Boolean = false,
    val tintIcon: Boolean = true,
    val iconShape: Shape? = null,
    val enabled: Boolean = true,
    val onClick: (() -> Unit)? = null
)
