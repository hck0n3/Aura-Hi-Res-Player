package iad1tya.echo.music.ui.player

import android.view.TextureView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
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
 * [videoUrl]: when this becomes non-null after the surface was first composed (resolve finished),
 * re-bind the TextureView so the first frame is not stuck on a blank/frozen frame.
 *
 * [fillCrop]: when true the video COVERS the whole area (fills both width and height, cropping the
 * overflow) instead of fitting with letterbox bars — used for true fullscreen in landscape.
 *
 * **Binding contract (regression #43 + 0.6.161):** media3's `setVideoTextureView` is NOT a no-op when
 * the same view is already attached — it always tears down callbacks / may null the output. Calling it
 * from AndroidView `update` on every recomposition caused freeze-then-resume while audio kept going.
 * Bind only when the TextureView instance changes (or [videoUrl] changes); skip identical rebinds.
 */
@Composable
fun PlayerVideoSurface(
    playerConnection: PlayerConnection,
    modifier: Modifier = Modifier,
    fillCrop: Boolean = false,
    videoUrl: String? = null,
) {
    var videoAspectRatio by remember { mutableFloatStateOf(16f / 9f) }
    var surfaceRef by remember { mutableStateOf<TextureView?>(null) }
    // Last TextureView we successfully handed to ExoPlayer. Identity check prevents setVideoTextureView
    // spam on Compose recompositions (progress ticks, bloom, lyrics chrome, etc.).
    var boundTextureView by remember { mutableStateOf<TextureView?>(null) }

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
            boundTextureView = null
        }
    }

    fun bindIfNeeded(textureView: TextureView, force: Boolean) {
        if (!textureView.isAvailable) return
        if (!force && boundTextureView === textureView) return
        runCatching { playerConnection.player.setVideoTextureView(textureView) }
            .onSuccess { boundTextureView = textureView }
    }

    // New URL (resolve finished / track swap): force re-bind even if the same TextureView instance.
    LaunchedEffect(videoUrl, playerConnection.player) {
        if (videoUrl.isNullOrEmpty()) return@LaunchedEffect
        val textureView = surfaceRef ?: return@LaunchedEffect
        bindIfNeeded(textureView, force = true)
    }

    val factory = { ctx: android.content.Context ->
        TextureView(ctx).also { tv ->
            surfaceRef = tv
            // First attach: media3 installs a SurfaceTextureListener when !isAvailable and binds when
            // the texture appears. Mark this instance bound so AndroidView `update` does not call
            // setVideoTextureView again (that teardown is what freezes the picture mid-song).
            runCatching { playerConnection.player.setVideoTextureView(tv) }
                .onSuccess { boundTextureView = tv }
        }
    }
    // Re-assert ONLY when the TextureView instance changed AND its SurfaceTexture is live.
    // Rotation destroys this view and creates a new one — identity differs → rebind once available.
    // Same instance on recomposition → skip (avoids media3 teardown that freezes the picture).
    val update = { tv: android.view.View ->
        val textureView = tv as TextureView
        surfaceRef = textureView
        bindIfNeeded(textureView, force = false)
        Unit
    }
    val onRelease = { tv: android.view.View ->
        if (surfaceRef === tv) surfaceRef = null
        if (boundTextureView === tv) boundTextureView = null
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
