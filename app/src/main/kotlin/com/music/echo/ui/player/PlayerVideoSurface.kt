package iad1tya.echo.music.ui.player

import android.view.TextureView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
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
    var videoAspectRatio by remember { mutableFloatStateOf(16f / 9f) }

    DisposableEffect(playerConnection.player) {
        val listener = object : Player.Listener {
            override fun onVideoSizeChanged(videoSize: VideoSize) {
                if (videoSize.width > 0 && videoSize.height > 0) {
                    var width = videoSize.width * videoSize.pixelWidthHeightRatio
                    var height = videoSize.height.toFloat()
                    if (videoSize.unappliedRotationDegrees == 90 || videoSize.unappliedRotationDegrees == 270) {
                        val temp = width
                        width = height
                        height = temp
                    }
                    if (height > 0) {
                        videoAspectRatio = width / height
                    }
                }
            }
        }
        playerConnection.player.addListener(listener)
        
        val initialSize = playerConnection.player.videoSize
        if (initialSize.width > 0 && initialSize.height > 0) {
            var width = initialSize.width * initialSize.pixelWidthHeightRatio
            var height = initialSize.height.toFloat()
            if (initialSize.unappliedRotationDegrees == 90 || initialSize.unappliedRotationDegrees == 270) {
                val temp = width
                width = height
                height = temp
            }
            if (height > 0) {
                videoAspectRatio = width / height
            }
        }
        
        onDispose {
            playerConnection.player.removeListener(listener)
        }
    }

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        AndroidView(
            modifier = Modifier.aspectRatio(videoAspectRatio),
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
}
