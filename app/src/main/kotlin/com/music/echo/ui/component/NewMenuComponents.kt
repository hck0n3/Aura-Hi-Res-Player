

package iad1tya.echo.music.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import iad1tya.echo.music.ui.utils.rememberIsTvOrCar
import iad1tya.echo.music.ui.utils.tvFocusable

// This file used to also carry NewActionButton / NewMenuItem / NewMenuSectionHeader / NewMenuContent /
// NewIconButton / NewMenuContainer. None of them had a single caller anywhere in the app — the bottom
// sheets build their rows from ListItem directly — so they were removed. NewActionGrid + NewAction are
// the live part: every menu sheet (Song/Album/Artist/Playlist/Queue/Lyrics/Player/YouTube*) uses them.

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun NewActionGrid(
    actions: List<NewAction>,
    modifier: Modifier = Modifier,
    columns: Int = 3
) {
    val isTvOrCar = rememberIsTvOrCar()
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        val indexedActions = actions.mapIndexed { index, action -> index to action }
        val chunks = indexedActions.chunked(columns)
        chunks.forEach { rowIndexedActions ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)
            ) {
                rowIndexedActions.forEach { (index, action) ->
                    var performAction by remember { mutableStateOf(false) }

                    if (performAction) {
                        action.onClick()
                        // SideEffect, NOT LaunchedEffect: it runs synchronously as soon as this composition
                        // is applied, so the flag is cleared before any recomposition can re-enter the
                        // branch. The old LaunchedEffect dispatched the reset to a coroutine, and the state
                        // change caused BY the action itself (queue changed, download started, playback
                        // moved) recomposed first — running the side effect a second time. That is why
                        // "Reproducir a continuación" / "Añadir a la cola" / "Descargar" / "Compartir" /
                        // "Iniciar radio" could fire twice from one tap.
                        SideEffect { performAction = false }
                    }

                    val bgColor = if (action.backgroundColor != Color.Unspecified) action.backgroundColor else MaterialTheme.colorScheme.surfaceVariant
                    val contentCol = if (action.contentColor != Color.Unspecified) action.contentColor else MaterialTheme.colorScheme.onSurfaceVariant

                    ToggleButton(
                        checked = false,
                        onCheckedChange = { performAction = true },
                        enabled = action.enabled,
                        shapes = when {
                            actions.size == 1 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                            index == 0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                            index == actions.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                            else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                        },
                        colors = ToggleButtonDefaults.toggleButtonColors(
                            containerColor = bgColor,
                            contentColor = contentCol,
                            disabledContainerColor = bgColor.copy(alpha = 0.5f),
                            disabledContentColor = contentCol.copy(alpha = 0.5f)
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .semantics { role = Role.Button }
                            .tvFocusable(isTvOrCar, RoundedCornerShape(16.dp), scaleFocused = 1f)
                    ) {
                        action.icon()
                        Spacer(Modifier.size(ToggleButtonDefaults.IconSpacing))
                        Text(
                            text = action.text,
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}


data class NewAction(
    val icon: @Composable () -> Unit,
    val text: String,
    val onClick: @Composable () -> Unit,
    val enabled: Boolean = true,
    val backgroundColor: Color = Color.Unspecified,
    val contentColor: Color = Color.Unspecified
)
