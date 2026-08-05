

package iad1tya.echo.music.ui.component

import androidx.annotation.DrawableRes
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.Indication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.IconButtonColors
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import iad1tya.echo.music.ui.utils.rememberIsTvOrCar
import iad1tya.echo.music.ui.utils.tvFocusable

@Composable
fun ResizableIconButton(
    @DrawableRes icon: Int,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurface,
    enabled: Boolean = true,
    indication: Indication? = null,
    onClick: () -> Unit = {},
) {
    Image(
        painter = painterResource(icon),
        contentDescription = null,
        colorFilter = ColorFilter.tint(color),
        modifier = modifier
            .tvFocusable(rememberIsTvOrCar(), CircleShape)
            .clickable(
                indication = indication ?: ripple(bounded = false),
                interactionSource = remember { MutableInteractionSource() },
                enabled = enabled,
                onClick = onClick,
            )
            .alpha(if (enabled) 1f else 0.5f),
    )
}

/**
 * [onLongClick] is nullable, and `null` means "no long-press handler at all".
 *
 * ~44 screens used to wire it to `NavController.backToMain()`: an undiscoverable ~500ms press on
 * the back arrow that silently popped the whole chain to a tab root. A slow or resting thumb
 * destroyed the user's place ("estaba en una playlist … me manda al inicio"). Another 7 screens
 * passed `{}` to opt out, which was just as bad — a non-null handler makes `combinedClickable`
 * SWALLOW the long press, so a slow tap on those buttons did nothing at all. Passing `null` is
 * what actually disables the gesture: the press falls through to [onClick] on release, so a slow
 * tap behaves exactly like a normal tap.
 *
 * ⚠️ [onLongClick] deliberately has NO DEFAULT VALUE, and must not be given one. This function
 * differs from [androidx.compose.material3.IconButton] only by this parameter; dozens of files
 * import BOTH. The moment `onLongClick` defaults, every remaining argument has a default too and
 * a bare `IconButton(onClick = …) { }` matches both signatures — "Overload resolution ambiguity",
 * hundreds of errors across ~20 files. Keeping it required is what keeps the two apart.
 *
 * Only pass a non-null [onLongClick] when the long press is a real, discoverable feature (e.g. a
 * context menu). Otherwise pass `null`.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun IconButton(
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: IconButtonColors = IconButtonDefaults.iconButtonColors(),
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .minimumInteractiveComponentSize()
            .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
            .tvFocusable(rememberIsTvOrCar(), CircleShape)
            .clip(CircleShape)
            .background(color = colors.containerColor)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
                enabled = enabled,
                role = Role.Button,
                interactionSource = interactionSource,
                indication = ripple(
                    bounded = false,
                    radius = 24.dp
                ),
            ),
        contentAlignment = Alignment.Center,
    ) {
        val contentColor = colors.contentColor
        CompositionLocalProvider(LocalContentColor provides contentColor, content = content)
    }
}
