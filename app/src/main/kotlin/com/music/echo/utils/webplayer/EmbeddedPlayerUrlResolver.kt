package iad1tya.echo.music.utils.webplayer

import android.os.SystemClock
import com.music.innertube.YouTube
import iad1tya.echo.music.utils.cipher.CipherDeobfuscator
import iad1tya.echo.music.utils.cipher.RendererRecoveryPolicy
import kotlinx.coroutines.CancellationException
import timber.log.Timber

/**
 * Last-resort YouTube stream URL resolver: loads YouTube's real embed player in a hidden WebView
 * and captures the signed googlevideo.com URL it requests, WITHOUT letting audio play inside the
 * WebView. Only invoked once per song, after the normal 12-client InnerTube cascade (cipher
 * deobfuscation + client fallback) has already exhausted itself — see
 * YTPlayerUtils.playerResponseForPlaybackImpl.
 *
 * This sidesteps cipher/bot-detection entirely: YouTube's OWN JavaScript resolves its own
 * signature/n-transform/PoToken challenge, exactly as it would in a real browser tab, because it
 * IS YouTube's real player running in a real WebView — the same reason a reference app (RiPlay)
 * never hits these failures. Unlike that app, this does NOT become the primary playback engine:
 * it hands a bare URL back to the existing ExoPlayer pipeline and gets destroyed, so the
 * Superpowered DSP / EQ / crossfade path is completely unaffected.
 */
object EmbeddedPlayerUrlResolver {
    private const val TAG = "Metrolist_EmbeddedPlayerResolver"

    // Additional budget layered ON TOP of YTPlayerUtils.RESOLVE_TIMEOUT_MS — this only ever runs
    // after that 30s cascade (times up to 2-3 attempts) has already fully completed, so it must
    // stay short: the user has already been waiting.
    private const val TIMEOUT_MS = 10_000L

    data class ResolvedStream(
        val streamUrl: String,
        val requestHeaders: Map<String, String>,
    )

    // Separate failure domain from the cipher/PoToken WebViews (CipherDeobfuscator's own instance)
    // — a renderer death here must not lock out, or be locked out by, unrelated WebView work.
    private val rendererRecoveryPolicy = RendererRecoveryPolicy()

    /**
     * Never throws. Returns null on any failure (timeout, renderer death, backoff window active,
     * no audio-only request ever observed) so the caller falls through to its existing
     * StreamResolutionException dead-end — identical behavior to before this fallback existed.
     */
    suspend fun tryResolve(videoId: String): ResolvedStream? {
        val now = SystemClock.elapsedRealtime()
        if (!rendererRecoveryPolicy.shouldAttempt(now)) {
            Timber.tag(TAG).d("Skipping embedded-player fallback for $videoId: in backoff window")
            return null
        }
        return try {
            val result = EmbeddedPlayerWebView.resolve(
                context = CipherDeobfuscator.appContext,
                videoId = videoId,
                cookie = YouTube.cookie,
                timeoutMs = TIMEOUT_MS,
            )
            rendererRecoveryPolicy.onSuccess()
            Timber.tag(TAG).d("Embedded-player fallback resolved a stream for $videoId")
            result
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            rendererRecoveryPolicy.onFailure(SystemClock.elapsedRealtime())
            Timber.tag(TAG).w("Embedded-player fallback failed for $videoId: ${e.message}")
            null
        }
    }
}
