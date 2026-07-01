package iad1tya.echo.music.ui.player

import android.view.TextureView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import iad1tya.echo.music.playback.PlayerConnection

/**
 * Renders the current player's video to a [TextureView]. A TextureView (not SurfaceView) is used
 * so it composes cleanly inside Compose overlays (z-order, clipping, rounded corners). The view
 * attaches to the player on enter and detaches on leave; show it only while video mode is on.
 *
 * [fillCrop]: when true the video COVERS the whole area (fills both width and height, cropping the
 * overflow) instead of fitting with letterbox bars — used for true fullscreen in landscape.
 */
@Composable
fun PlayerVideoSurface(
    playerConnection: PlayerConnection,
    modifier: Modifier = Modifier,
    fillCrop: Boolean = false,
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

    val factory = { ctx: android.content.Context ->
        TextureView(ctx).also { tv ->
            runCatching { playerConnection.player.setVideoTextureView(tv) }
        }
    }
    // Re-assert the surface binding on every recomposition/attach. This is the fix for the "video freezes,
    // audio keeps playing after rotating" bug: rotation swaps the player sheet between its landscape and
    // portrait branches, which DESTROYS this TextureView and creates a NEW one whose SurfaceTexture isn't
    // available yet at factory() time. With only the factory bind, the new surface was never re-pointed at
    // the player once it became available. setVideoTextureView(sameTv) is a no-op in media3 when tv is already
    // the current one (re-installs media3's own SurfaceTextureListener), so re-asserting here is cheap and safe.
    val update = { tv: android.view.View ->
        runCatching { playerConnection.player.setVideoTextureView(tv as TextureView) }
        Unit
    }
    val onRelease = { tv: android.view.View ->
        runCatching { playerConnection.player.clearVideoTextureView(tv as TextureView) }
        Unit
    }

    if (fillCrop) {
        // COVER: scale the video so it fills both dimensions, cropping whatever overflows (no black bars).
        BoxWithConstraints(
            modifier = modifier.clipToBounds(),
            contentAlignment = Alignment.Center,
        ) {
            val boxAspect = if (constraints.maxHeight > 0) {
                constraints.maxWidth.toFloat() / constraints.maxHeight
            } else {
                videoAspectRatio
            }
            // If the area is wider than the video, fill the width (crop top/bottom); otherwise fill the
            // height (crop the sides).
            val surfaceMod = if (boxAspect > videoAspectRatio) {
                Modifier.fillMaxWidth().aspectRatio(videoAspectRatio)
            } else {
                Modifier.fillMaxHeight().aspectRatio(videoAspectRatio)
            }
            AndroidView(modifier = surfaceMod, factory = factory, update = update, onRelease = onRelease)
        }
    } else {
        // FIT: the whole video is visible, letterboxed if the area's aspect differs.
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            AndroidView(
                modifier = Modifier.aspectRatio(videoAspectRatio),
                factory = factory,
                update = update,
                onRelease = onRelease,
            )
        }
    }
}
