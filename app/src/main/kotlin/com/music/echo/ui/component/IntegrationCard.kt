

package iad1tya.echo.music.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter


// ──────────────────────────────────────────────────────────────────────────────────────────────────
// [IntegrationCard] is [Material3SettingsGroup], duplicated.
// ──────────────────────────────────────────────────────────────────────────────────────────────────
//
// This file used to hold its own copy of the settings group: the same grouped-corner `Card` (24 dp /
// 6 dp, `surfaceVariant @ 0.3`, 0 elevation, `animateContentSize`), the same row (20 × 16 dp padding,
// `titleMedium` title, `bodyMedium` / `onSurfaceVariant` description, 40 dp icon plate clipped to
// 12 dp and filled with `primary @ 0.1` / `0.15`, 16 dp gap, trailing slot), and the same group
// header (`labelLarge`, `primary`, 8 dp above and below). Not "similar" — the same values, line for
// line, with a narrower item type.
//
// That duplicate is why "Escuchar juntos ▸ Ajustes" stayed classic while the other 18 settings
// sub-screens adopted the redesign: `Material3SettingsGroup` is the seam that got restyled, and this
// screen was the one settings screen that did not go through it. It has exactly ONE caller
// (`ui/screens/settings/integrations/ListenTogetherSettings`), so rather than restyle the copy — a
// second flag read, a second skin, and two files to keep in step forever — the copy is deleted and
// this becomes a thin adapter onto the real seam.
//
// What that changes on the CLASSIC path: nothing that is drawn. Every value listed above is identical
// in both files, `compact` stays false (the 20 × 16 dp rhythm), `scrollState` stays null (the
// scroll-to-highlight modifier reduces to `Modifier`), and `enabled` is true for every item, so
// `enabled && onClick != null` is `onClick != null`, the condition this file used. The one addition
// is `tvFocusableItem`, a no-op unless the app is running on a TV or in a car — where these seven
// rows gain the D-pad focus ring every other settings row already has.
//
// What it changes on the NEW path: the seven rows become the flat sheet, the bare teal glyph, the
// `AuraType` label and the `.mrow` chevron, at the same moment as every other settings row, with no
// second `rememberPreference` anywhere.

/**
 * A titled group of integration rows. An adapter over [Material3SettingsGroup] — see the note above
 * for why this is not its own implementation.
 */
@Composable
fun IntegrationCard(
    title: String? = null,
    items: List<IntegrationCardItem>
) {
    Material3SettingsGroup(
        title = title,
        items = items.map { item ->
            Material3SettingsItem(
                icon = item.icon,
                title = item.title,
                description = item.description,
                trailingContent = item.trailingContent,
                showBadge = item.showBadge,
                isHighlighted = item.isHighlighted,
                onClick = item.onClick,
            )
        }
    )
}


data class IntegrationCardItem(
    val icon: Painter? = null,
    val title: @Composable () -> Unit,
    val description: (@Composable () -> Unit)? = null,
    val trailingContent: (@Composable () -> Unit)? = null,
    val showBadge: Boolean = false,
    val isHighlighted: Boolean = false,
    val onClick: (() -> Unit)? = null
)
