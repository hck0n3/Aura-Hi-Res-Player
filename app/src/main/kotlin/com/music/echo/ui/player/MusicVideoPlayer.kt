package iad1tya.echo.music.ui.player

import android.view.TextureView
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import com.music.innertube.YouTube
import com.music.innertube.models.YouTubeClient
import okhttp3.OkHttpClient

/**
 * Dedicated, self-contained player for the music VIDEO (video mode). Modeled on the proven
 * [CanvasArtworkPlayer]: its own [ExoPlayer] + an OkHttp data source that sets the correct
 * `User-Agent` per YouTube client — `googlevideo` URLs return 403 without it (this is why feeding the
 * video URL through the main music player's data source never rendered).
 *
 * Unlike the canvas it plays WITH SOUND (a single MUXED video+audio stream) and does NOT loop. The
 * music engine is paused by MusicService while this is shown, so there is no double audio.
 */
@Composable
fun MusicVideoPlayer(
    url: String,
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
    onEnded: () -> Unit = {},
) {
    val context = LocalContext.current
    var videoAspectRatio by remember(url) { mutableFloatStateOf(16f / 9f) }

    val okHttpClient = remember {
        OkHttpClient.Builder()
            .proxy(YouTube.proxy)
            .addInterceptor { chain ->
                val request = chain.request()
                val host = request.url.host
                val isYouTubeMediaHost =
                    host.endsWith("googlevideo.com") ||
                        host.endsWith("googleusercontent.com") ||
                        host.endsWith("youtube.com") ||
                        host.endsWith("youtube-nocookie.com") ||
                        host.endsWith("ytimg.com")
                if (!isYouTubeMediaHost) return@addInterceptor chain.proceed(request)

                val clientParam = request.url.queryParameter("c")?.trim().orEmpty()
                val isWeb =
                    clientParam.startsWith("WEB", ignoreCase = true) ||
                        clientParam.startsWith("WEB_REMIX", ignoreCase = true) ||
                        request.url.toString().contains("c=WEB", ignoreCase = true)
                val userAgent = when {
                    clientParam.startsWith("WEB", ignoreCase = true) ||
                        clientParam.startsWith("WEB_REMIX", ignoreCase = true) -> YouTubeClient.USER_AGENT_WEB
                    clientParam.startsWith("IOS", ignoreCase = true) -> YouTubeClient.IOS.userAgent
                    clientParam.startsWith("ANDROID_VR", ignoreCase = true) -> YouTubeClient.ANDROID_VR_NO_AUTH.userAgent
                    clientParam.startsWith("ANDROID", ignoreCase = true) -> YouTubeClient.MOBILE.userAgent
                    else -> YouTubeClient.USER_AGENT_WEB
                }
                val builder = request.newBuilder().header("User-Agent", userAgent)
                if (isWeb) {
                    builder.header("Origin", YouTubeClient.ORIGIN_YOUTUBE_MUSIC)
                    builder.header("Referer", YouTubeClient.REFERER_YOUTUBE_MUSIC)
                }
                chain.proceed(builder.build())
            }
            .build()
    }

    val mediaSourceFactory = remember(okHttpClient) {
        DefaultMediaSourceFactory(
            DefaultDataSource.Factory(context, OkHttpDataSource.Factory(okHttpClient)),
        )
    }

    val exoPlayer = remember {
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(mediaSourceFactory)
            .build()
            .apply {
                trackSelectionParameters = trackSelectionParameters.buildUpon()
                    .setForceHighestSupportedBitrate(true)
                    .build()
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(C.USAGE_MEDIA)
                        .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                        .build(),
                    false,
                )
                videoScalingMode = C.VIDEO_SCALING_MODE_SCALE_TO_FIT
                volume = 1f
                repeatMode = Player.REPEAT_MODE_OFF
            }
    }

    LaunchedEffect(isPlaying) {
        exoPlayer.playWhenReady = isPlaying
    }

    DisposableEffect(exoPlayer) {
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
                    if (height > 0) videoAspectRatio = width / height
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) onEnded()
            }
        }
        exoPlayer.addListener(listener)
        onDispose { exoPlayer.removeListener(listener) }
    }

    LaunchedEffect(url) {
        val mimeType = if (url.contains(".m3u8", ignoreCase = true)) {
            MimeTypes.APPLICATION_M3U8
        } else {
            MimeTypes.VIDEO_MP4
        }
        val mediaItem = MediaItem.Builder().setUri(url).setMimeType(mimeType).build()
        exoPlayer.setMediaItem(mediaItem)
        exoPlayer.prepare()
        exoPlayer.playWhenReady = isPlaying
    }

    DisposableEffect(exoPlayer) {
        onDispose { exoPlayer.release() }
    }

    AndroidView(
        factory = { viewContext ->
            AspectRatioFrameLayout(viewContext).apply {
                layoutParams = ViewGroup.LayoutParams(MATCH_PARENT, MATCH_PARENT)
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                val textureView = TextureView(viewContext).apply {
                    layoutParams = ViewGroup.LayoutParams(MATCH_PARENT, MATCH_PARENT)
                }
                addView(textureView)
                exoPlayer.setVideoTextureView(textureView)
                setBackgroundColor(android.graphics.Color.BLACK)
            }
        },
        update = { view -> view.setAspectRatio(videoAspectRatio) },
        modifier = modifier,
    )
}
