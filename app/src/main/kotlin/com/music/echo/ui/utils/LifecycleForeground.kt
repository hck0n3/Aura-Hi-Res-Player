package iad1tya.echo.music.ui.utils

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

/**
 * True while the app/screen is in the foreground (between ON_START and ON_STOP). Lets a heavy off-screen
 * decoder — e.g. the animated Canvas video background — PAUSE when the user can't actually see it (screen
 * off, app backgrounded, another window on top), so it stops decoding frames and heating the device. The
 * audio keeps playing via the foreground service; only the invisible video work is suspended.
 */
@Composable
fun rememberIsAppInForeground(): Boolean {
    val lifecycleOwner = LocalLifecycleOwner.current
    var foreground by remember {
        mutableStateOf(lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))
    }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> foreground = true
                Lifecycle.Event.ON_STOP -> foreground = false
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    return foreground
}
