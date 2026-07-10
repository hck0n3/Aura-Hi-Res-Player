package iad1tya.echo.music.ui.screens.playlist

import android.content.Context
import android.net.ConnectivityManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.getSystemService
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.music.innertube.YouTube
import com.music.innertube.models.YouTubeClient
import iad1tya.echo.music.LocalPlayerConnection
import iad1tya.echo.music.constants.AudioQuality
import iad1tya.echo.music.utils.YTPlayerUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient

/**
 * In-place, NON-disruptive song preview. Plays a song through a DEDICATED short-lived [ExoPlayer]
 * (modeled on [iad1tya.echo.music.ui.player.MusicVideoPlayer] / CanvasArtworkPlayer) WITHOUT ever
 * touching the main player's queue / crossfade.
 *
 * Starting a preview pauses the main player (only if it was playing) and remembers that so it can be
 * resumed exactly when the preview stops (tap again / another row / sheet or screen dismissed). URL
 * resolution failures are a silent no-op that resumes the main player. Only ONE preview plays at a
 * time — starting a new one stops the previous.
 */
class SongPreviewController(
    private val context: Context,
    private val scope: CoroutineScope,
    private val onRequestMainPause: () -> Boolean,
    private val onResumeMain: () -> Unit,
) {
    /** The videoId currently being previewed (null = nothing playing). Drives the row UI. */
    var currentPreviewId by mutableStateOf<String?>(null)
        private set

    /** True while the stream URL for [currentPreviewId] is being resolved. */
    var isLoading by mutableStateOf(false)
        private set

    private var exoPlayer: ExoPlayer? = null
    private var resolveJob: Job? = null
    private var mainWasPlaying = false

    /** Tap handler: same song toggles it off; a different song starts a new preview. */
    fun toggle(videoId: String) {
        if (currentPreviewId == videoId) {
            stop()
        } else {
            start(videoId)
        }
    }

    private fun start(videoId: String) {
        // Stop any in-flight preview but DON'T resume the main player yet — we're immediately starting
        // another preview, so the main player should stay paused across the swap.
        teardown(resumeMain = false)

        currentPreviewId = videoId
        isLoading = true
        // Preserve the remembered state across a preview→preview swap: the main player is already paused
        // by us (onRequestMainPause returns false), so only capture a fresh value when nothing was active.
        mainWasPlaying = onRequestMainPause() || mainWasPlaying

        resolveJob = scope.launch {
            val url = withContext(Dispatchers.IO) {
                runCatching {
                    val cm = context.getSystemService<ConnectivityManager>() ?: return@runCatching null
                    YTPlayerUtils.playerResponseForPlayback(
                        videoId = videoId,
                        audioQuality = AudioQuality.OPUS,
                        connectivityManager = cm,
                        context = context,
                    ).getOrNull()?.streamUrl
                }.getOrNull()
            }

            // The user tapped a different row (or dismissed) while we were resolving — abandon.
            if (currentPreviewId != videoId) return@launch

            isLoading = false
            if (url.isNullOrBlank()) {
                // Resolution failed: no-op, resume the main player.
                stop()
                return@launch
            }

            val player = getOrCreatePlayer()
            player.setMediaItem(MediaItem.fromUri(url))
            player.prepare()
            player.playWhenReady = true
        }
    }

    /** Stop the current preview and resume the main player if it was playing. */
    fun stop() = teardown(resumeMain = true)

    private fun teardown(resumeMain: Boolean) {
        resolveJob?.cancel()
        resolveJob = null
        currentPreviewId = null
        isLoading = false
        exoPlayer?.let {
            it.stop()
            it.clearMediaItems()
        }
        if (resumeMain && mainWasPlaying) {
            mainWasPlaying = false
            onResumeMain()
        }
    }

    /** Release the dedicated player entirely (call from onDispose). */
    fun release() {
        resolveJob?.cancel()
        resolveJob = null
        exoPlayer?.release()
        exoPlayer = null
        val resume = mainWasPlaying
        mainWasPlaying = false
        currentPreviewId = null
        isLoading = false
        if (resume) onResumeMain()
    }

    private fun getOrCreatePlayer(): ExoPlayer {
        exoPlayer?.let { return it }
        // googlevideo stream URLs 403 without the right per-client User-Agent — reuse the same interceptor
        // pattern as the video/canvas players (keyed off the URL's `c=` client param).
        val okHttpClient = OkHttpClient.Builder()
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

        val mediaSourceFactory = DefaultMediaSourceFactory(
            DefaultDataSource.Factory(context, OkHttpDataSource.Factory(okHttpClient)),
        )

        return ExoPlayer.Builder(context)
            .setMediaSourceFactory(mediaSourceFactory)
            .build()
            .apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(C.USAGE_MEDIA)
                        .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                        .build(),
                    true,
                )
                repeatMode = Player.REPEAT_MODE_OFF
                // Approximates Safe Volume headroom so a hot-mastered preview isn't jarringly louder than the just-paused (attenuated) main playback; preview player only, main audio chain untouched.
                volume = 0.85f
                // Natural end (song plays to completion) or a playback error (e.g. googlevideo 403) must
                // stop the preview so the main player is resumed — otherwise it stays paused forever.
                addListener(object : Player.Listener {
                    override fun onPlaybackStateChanged(playbackState: Int) {
                        if (playbackState == Player.STATE_ENDED) stop()
                    }

                    override fun onPlayerError(error: PlaybackException) {
                        stop()
                    }
                })
            }
            .also { exoPlayer = it }
    }
}

/**
 * Creates a [SongPreviewController] tied to the current composition. Pauses/resumes the app's main
 * player via [LocalPlayerConnection] and releases the dedicated preview player on dispose.
 */
@Composable
fun rememberSongPreviewController(): SongPreviewController {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val playerConnection = LocalPlayerConnection.current

    val controller = remember(playerConnection) {
        SongPreviewController(
            context = context.applicationContext,
            scope = scope,
            onRequestMainPause = {
                val player = playerConnection?.player
                val wasPlaying = player != null &&
                    player.playWhenReady &&
                    player.playbackState != Player.STATE_ENDED
                if (wasPlaying) playerConnection?.pause()
                wasPlaying
            },
            onResumeMain = { playerConnection?.play() },
        )
    }

    DisposableEffect(controller) {
        onDispose { controller.release() }
    }

    return controller
}
