package iad1tya.echo.music.ui.player

import android.view.TextureView
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import iad1tya.echo.music.playback.PlayerConnection

/**
 * Renders the current player's video to a [TextureView]. A TextureView (not SurfaceView) is used
 * so it composes cleanly inside Compose overlays (z-order, clipping, rounded corners). The view
 * attaches to the player on enter and detaches on leave; show it only while video mode is on.
 */
@Composable
fun PlayerVideoSurface(
    playerConnection: PlayerConnection,
    modifier: Modifier = Modifier,
) {
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            TextureView(ctx).also { tv ->
                runCatching { playerConnection.player.setVideoTextureView(tv) }
            }
        },
        onRelease = { tv ->
            runCatching { playerConnection.player.clearVideoTextureView(tv as TextureView) }
        },
    )
}
