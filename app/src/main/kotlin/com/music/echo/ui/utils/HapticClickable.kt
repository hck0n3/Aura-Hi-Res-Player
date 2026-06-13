package iad1tya.echo.music.ui.utils

import android.view.HapticFeedbackConstants
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalView
import iad1tya.echo.music.constants.GlobalHapticsKey
import iad1tya.echo.music.utils.rememberPreference

/**
 * Wraps [onClick] so a light click haptic fires first when global haptics are enabled
 * (default ON). Used by the shared button/row components so haptics cover the whole app
 * from one place. Long presses already get their own haptic from `combinedClickable`.
 */
@Composable
fun rememberHapticClick(onClick: () -> Unit): () -> Unit {
    val view = LocalView.current
    val (hapticsEnabled) = rememberPreference(GlobalHapticsKey, defaultValue = true)
    return {
        if (hapticsEnabled) {
            view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
        }
        onClick()
    }
}
