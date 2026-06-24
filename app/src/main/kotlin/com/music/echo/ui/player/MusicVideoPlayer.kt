package iad1tya.echo.music.ui.player

import android.net.Uri
import android.view.TextureView
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.common.Tracks
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
 * `User-Agent` per YouTube client — `googlevideo` URLs return 403 without it.
 *
 * Plays a single MUXED (video+audio) stream WITH SOUND, seeking to [startPositionMs] so it continues
 * from where the song was. The music engine is paused by MusicService while this is shown.
 *
 * NOTE: currently shows a small on-screen DIAGNOSTIC overlay (itag/tracks/size/first-frame/error) so we
 * can see exactly why video does or doesn't render.
 */
@Composable
fun MusicVideoPlayer(
    url: String,
    isPlaying: Boolean,
    startPositionMs: Long,
    modifier: Modifier = Modifier,
    onEnded: () -> Unit = {},
) {
    val context = LocalContext.current
    var videoAspectRatio by remember(url) { mutableFloatStateOf(16f / 9f) }

    // --- diagnostics ---
    val itag = remember(url) { runCatching { Uri.parse(url).getQueryParameter("itag") }.getOrNull() ?: "?" }
    var diag by remember(url) { mutableStateOf("resolviendo…") }
    var videoTracks by remember(url) { mutableStateOf(-1) }
    var audioTracks by remember(url) { mutableStateOf(-1) }
    var sizeText by remember(url) { mutableStateOf("-") }
    var firstFrame by remember(url) { mutableStateOf(false) }
    var errText by remember(url) { mutableStateOf("") }

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
                        val temp = width; width = height; height = temp
                    }
                    if (height > 0) videoAspectRatio = width / height
                }
                sizeText = "${videoSize.width}x${videoSize.height}"
            }

            override fun onTracksChanged(tracks: Tracks) {
                var v = 0; var a = 0
                for (group in tracks.groups) {
                    when (group.type) {
                        C.TRACK_TYPE_VIDEO -> v += group.length
                        C.TRACK_TYPE_AUDIO -> a += group.length
                    }
                }
                videoTracks = v; audioTracks = a
            }

            override fun onRenderedFirstFrame() { firstFrame = true }

            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                errText = "${error.errorCodeName}: ${error.message}"
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                diag = when (playbackState) {
                    Player.STATE_IDLE -> "idle"
                    Player.STATE_BUFFERING -> "buffering"
                    Player.STATE_READY -> "ready"
                    Player.STATE_ENDED -> "ended"
                    else -> "?"
                }
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
        exoPlayer.setMediaItem(mediaItem, startPositionMs.coerceAtLeast(0L))
        exoPlayer.prepare()
        exoPlayer.playWhenReady = isPlaying
    }

    DisposableEffect(exoPlayer) {
        onDispose { exoPlayer.release() }
    }

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
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
            modifier = Modifier.fillMaxSize(),
        )

        // DIAGNOSTIC overlay — temporary. Tells us exactly what's happening with the video.
        Text(
            text = "itag=$itag · estado=$diag · vid=$videoTracks aud=$audioTracks · size=$sizeText · " +
                "frame=${if (firstFrame) "sí" else "no"}" + (if (errText.isNotEmpty()) " · ERR=$errText" else ""),
            color = Color.White,
            fontSize = 10.sp,
            modifier = Modifier
                .align(Alignment.TopStart)
                .background(Color(0xCC000000))
                .padding(4.dp),
        )
    }
}
