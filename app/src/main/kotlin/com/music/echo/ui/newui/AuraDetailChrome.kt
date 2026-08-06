package iad1tya.echo.music.ui.newui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * # Chrome for the redesigned screens that are NOT a bottom-bar tab
 *
 * Inicio, Biblioteca and Ajustes are tabs: they own the whole window, so their header is
 * [AuraScreenHeader] — a big title with nothing to its left. Estadísticas and Migrar lista are
 * pushed destinations, and a pushed destination needs a way back. The classic versions get one for
 * free from `LargeTopAppBar`/`TopAppBar`; the redesign draws no Material bar at all, so the back
 * affordance has to be part of the content.
 *
 * This file is that one header, shared, so two pushed screens cannot drift into two different back
 * buttons.
 *
 * ## The top inset, and why it is read here
 * `MainActivity` reserves an extra [iad1tya.echo.music.constants.AppBarHeight] (64 dp) of top inset
 * on every route where `auraOwnsHeader` is false — which is every route except Inicio, Biblioteca
 * and Ajustes. That reserve exists for screens that draw a Material top bar, and the redesigned
 * pushed screens draw none: consuming it would open them with 64 dp of dead black above the title,
 * the exact non-immersive look the beta verdict called out on Ajustes. So the redesigned pushed
 * screens clear the STATUS BAR only ([auraStatusBarPadding]) and take horizontal/bottom clearance
 * from `LocalPlayerAwareWindowInsets` as usual.
 */

/** The status-bar height, in dp. Use it as the top `contentPadding` of a redesigned pushed screen. */
@Composable
fun auraStatusBarPadding(): Dp =
    WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

/**
 * `‹ Título   [acciones]` — the header of a redesigned pushed screen.
 *
 * [title] is smaller than [AuraType.ScreenTitle] on purpose: it shares its line with a control, and
 * the 29 sp tab title next to a 48 dp button reads as a mistake. [AuraType.SheetTitle] is the same
 * scale the queue sheet uses for exactly this reason.
 */
@Composable
fun AuraDetailHeader(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = modifier
            .fillMaxWidth()
            .padding(start = AuraSpacing.Gutter - 14.dp, end = 6.dp, top = 6.dp, bottom = 2.dp),
    ) {
        AuraDetailBackButton(onClick = onBack)
        Text(
            text = title,
            style = AuraType.SheetTitle,
            color = AuraPalette.OnGround,
            maxLines = 1,
            overflow = AuraDefaultOverflow,
            modifier = Modifier.weight(1f),
        )
        trailing?.invoke()
    }
}

/**
 * Back.
 *
 * Deliberately NO long-press handler, matching [AuraSettingsScreen]'s: the classic bars carry a
 * hidden long-press-to-home that silently popped the user's whole chain to a tab root. With no
 * handler the long press falls through to [onClick], so a slow tap is just a tap.
 */
@Composable
fun AuraDetailBackButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(AuraSpacing.MinTouchTarget)
            .clip(AuraShapes.Pill)
            .clickable(
                role = Role.Button,
                onClickLabel = "Atrás",
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = AuraIcons.ChevronRight,
            contentDescription = null,
            tint = AuraPalette.OnGroundMuted,
            modifier = Modifier
                .size(22.dp)
                .rotate(180f),
        )
    }
}
