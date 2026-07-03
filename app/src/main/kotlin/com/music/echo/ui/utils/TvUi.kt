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
import androidx.compose.ui.platform.LocalConfiguration
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
 * True on a BIG screen where a wide "Spotify-style" layout fits: TV / car head unit / tablet / car box /
 * unfolded foldable / any large-landscape display. REACTIVE — reads LocalConfiguration.smallestScreenWidthDp
 * each composition, so folding/unfolding a foldable (a runtime config change the manifest keeps the Activity
 * alive through) flips this live and the layout re-flows without a restart. Phones (and folded foldables,
 * sw < 600dp) stay false in every orientation -> they keep the normal portrait UI.
 *
 * Distinct from [rememberIsTvOrCar], which gates the D-pad focus RING (only remote-driven TV/car need it — a
 * touch tablet gets the wide layout but never shows a ring since touch raises no focus events).
 */
@Composable
fun rememberIsWideScreen(): Boolean {
    val configuration = LocalConfiguration.current
    val isTvOrCar = rememberIsTvOrCar()
    // Manual override: the user can force the wide "Spotify split" layout on any device (tablet/phone) they want.
    val forceSplit by iad1tya.echo.music.utils.rememberPreference(
        iad1tya.echo.music.constants.ForceSplitViewKey, false,
    )
    return isTvOrCar || forceSplit || configuration.smallestScreenWidthDp >= 600
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
    scaleFocused: Float = 1.12f,
): Modifier =
    if (!enabled) this
    else composed {
        var focused by remember { mutableStateOf(false) }
        this
            .onFocusChanged { focused = it.isFocused }
            .then(
                if (focused) {
                    val ring = Modifier.border(3.dp, Color.White, shape).padding(2.dp)
                    if (scaleFocused != 1f) Modifier.scale(scaleFocused).then(ring) else ring
                } else Modifier,
            )
            .then(if (addFocusable) Modifier.focusable() else Modifier)
    }

/**
 * Item-level D-pad focus for list rows / grid cards. Meant to be the OUTERMOST modifier of a SHARED item
 * composable, wrapping the caller's own `.clickable`/`.combinedClickable` (which is the real focus target):
 *   Row(modifier = Modifier.tvFocusableItem(isTvOrCar).then(callerModifier) ...)
 * Because `onFocusChanged` sits ABOVE the caller's clickable, it observes that one focus stop — no extra
 * focusable, D-pad center still fires the row/card's real onClick. Rows use no scale (a scaled full-width row
 * clips its neighbours); cards keep the scale pop. No-op off-TV.
 */
fun Modifier.tvFocusableItem(
    enabled: Boolean,
    shape: RoundedCornerShape = RoundedCornerShape(8.dp),
    scaleFocused: Float = 1f,
): Modifier = tvFocusable(enabled = enabled, shape = shape, addFocusable = false, scaleFocused = scaleFocused)
