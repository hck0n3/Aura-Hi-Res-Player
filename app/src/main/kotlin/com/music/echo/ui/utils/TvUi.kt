package iad1tya.echo.music.ui.utils

import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import iad1tya.echo.music.utils.DeviceForm

/** True on Android TV or a car head unit (form-factor, cached). Used to switch layouts + D-pad focus on. */
@Composable
fun rememberIsTvOrCar(): Boolean {
    val context = LocalContext.current
    return remember { DeviceForm.isTvOrCar(context) }
}

/**
 * D-pad focus for TV / car remotes: draws a clearly VISIBLE highlight (a white ring + a slight scale-up) while
 * the control holds focus, so the user can see which button is selected — plain Material3 shows no visible
 * focus on a TV remote. A no-op off-TV, so phones/tablets are completely unaffected.
 *
 * IMPORTANT: this does NOT add `.focusable()`. It relies on the control providing its OWN focus target (any
 * Button / IconButton / `.clickable {}` already is focusable). `onFocusChanged` sits above that inner focusable
 * and observes it, so there is exactly ONE focus stop and D-pad center triggers the control's real onClick.
 * Adding a second `.focusable()` here would create a dead extra stop that swallows a D-pad press.
 *
 * Put this on the control's own `modifier` (before it applies its internal clickable) — for a Button/IconButton
 * pass it via the `modifier =` param; for a bare `.clickable {}` Box, put it right before `.clickable`.
 * For a non-clickable element that still must be reachable, pass `addFocusable = true`.
 */
fun Modifier.tvFocusable(
    enabled: Boolean,
    shape: RoundedCornerShape = RoundedCornerShape(50),
    addFocusable: Boolean = false,
): Modifier =
    if (!enabled) this
    else composed {
        var focused by remember { mutableStateOf(false) }
        this
            .onFocusChanged { focused = it.isFocused }
            .then(
                if (focused) Modifier
                    .scale(1.12f)
                    .border(3.dp, Color.White, shape)
                    .padding(2.dp)
                else Modifier,
            )
            .then(if (addFocusable) Modifier.focusable() else Modifier)
    }
