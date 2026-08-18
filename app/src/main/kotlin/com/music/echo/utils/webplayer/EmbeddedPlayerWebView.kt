package iad1tya.echo.music.utils.webplayer

import android.content.Context
import android.os.Build
import android.webkit.CookieManager
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.annotation.RequiresApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import timber.log.Timber
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Last-resort YouTube stream URL sniffer. Loads YouTube's real `/embed/{videoId}` page in a
 * WebView with real network access (unlike [iad1tya.echo.music.utils.cipher.CipherWebView] /
 * [iad1tya.echo.music.utils.potoken.PoTokenWebView], which only ever execute local JS) and lets
 * YouTube's OWN player JavaScript resolve its own signature/n-transform/PoToken challenge exactly
 * as it would in a real browser tab — because it IS YouTube's real player. The moment it requests
 * the actual audio stream, [shouldInterceptRequest] captures that URL + the exact headers the page
 * sent, aborts the request (so nothing ever plays or downloads inside this WebView — this is a
 * disposable URL resolver, not a player), and the captured URL is handed back to the EXISTING
 * ExoPlayer pipeline in MusicService. No audio ever plays through this WebView; no MediaSession /
 * Player wrapper is needed.
 *
 * Only ever used AFTER the normal 12-client InnerTube cascade has fully exhausted itself for a
 * song — see [EmbeddedPlayerUrlResolver] and YTPlayerUtils.playerResponseForPlaybackImpl.
 */
class EmbeddedPlayerWebView private constructor(
    context: Context,
    initContinuation: Continuation<EmbeddedPlayerUrlResolver.ResolvedStream>,
) {
    private val webView = WebView(context)

    private var resultContinuation: Continuation<EmbeddedPlayerUrlResolver.ResolvedStream>? = initContinuation

    @Volatile
    private var captured = false

    @Volatile
    private var destroyed = false

    init {
        val settings = webView.settings
        @Suppress("SetJavaScriptEnabled")
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        // Opposite of CipherWebView/PoTokenWebView: this WebView's entire job is to make the SAME
        // real network requests a browser tab would, so YouTube's own player JS can resolve its
        // own challenge. blockNetworkLoads=true here would defeat the whole point.
        settings.blockNetworkLoads = false
        settings.mediaPlaybackRequiresUserGesture = false

        webView.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                val url = request.url
                // Only the actual audio-bearing adaptive stream — reject muxed/video itags so a
                // song doesn't drag a decoded video track along with it. If no audio-only request
                // ever arrives before the timeout, tryResolve() simply returns null (identical to
                // today's behavior), so this filter is safe to be strict.
                if (!captured &&
                    url.host?.endsWith("googlevideo.com") == true &&
                    url.path?.contains("videoplayback") == true &&
                    isAudioOnlyItag(url.getQueryParameter("itag"))
                ) {
                    captured = true
                    val headers = request.requestHeaders
                    Timber.tag(TAG).d("Captured embedded-player stream URL (itag=${url.getQueryParameter("itag")}, headers=${headers.size})")
                    resumeSafely { it.resume(EmbeddedPlayerUrlResolver.ResolvedStream(url.toString(), headers)) }
                    return WebResourceResponse("audio/mpeg", null, 204, "No Content", emptyMap(), null)
                }
                return super.shouldInterceptRequest(view, request)
            }

            @RequiresApi(Build.VERSION_CODES.O)
            override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
                val didCrash = runCatching { detail.didCrash() }.getOrNull()
                Timber.tag(TAG).w("Embedded player WebView render process gone (didCrash=$didCrash)")
                resumeSafely { it.resumeWithException(EmbeddedPlayerException("WebView render process gone (didCrash=$didCrash)")) }
                destroyWebView()
                return true
            }
        }
    }

    private inline fun resumeSafely(block: (Continuation<EmbeddedPlayerUrlResolver.ResolvedStream>) -> Unit) {
        val cont = takeContinuation() ?: return
        runCatching { block(cont) }
    }

    @Synchronized
    private fun takeContinuation(): Continuation<EmbeddedPlayerUrlResolver.ResolvedStream>? =
        resultContinuation.also { resultContinuation = null }

    private fun start(videoId: String, cookie: String?) {
        runCatching {
            if (!cookie.isNullOrBlank()) {
                val cm = CookieManager.getInstance()
                cm.setAcceptCookie(true)
                cm.setCookie("https://www.youtube.com", cookie)
                cm.flush()
            }
        }.onFailure { Timber.tag(TAG).d("Cookie injection skipped: ${it.message}") }
        webView.loadUrl(EMBED_URL_TEMPLATE.format(videoId))
    }

    fun close() = destroyWebView()

    private fun destroyWebView() {
        if (destroyed) return
        destroyed = true
        runCatching {
            webView.stopLoading()
            webView.clearHistory()
            webView.loadUrl("about:blank")
            webView.onPause()
            webView.removeAllViews()
            webView.destroy()
        }.onFailure { Timber.tag(TAG).w("Embedded player WebView teardown threw: $it") }
    }

    companion object {
        private const val TAG = "Metrolist_EmbeddedPlayer"
        private const val EMBED_URL_TEMPLATE = "https://www.youtube.com/embed/%s?autoplay=1&mute=1"

        // Standard YouTube adaptive audio-only itags (opus webm + AAC mp4 variants). Anything else
        // seen at /videoplayback is a muxed audio+video (or video-only) format we deliberately skip.
        private val AUDIO_ONLY_ITAGS = setOf(
            "139", "140", "141", "171", "172", "249", "250", "251", "256", "258",
        )

        private fun isAudioOnlyItag(itag: String?): Boolean = itag != null && itag in AUDIO_ONLY_ITAGS

        suspend fun resolve(
            context: Context,
            videoId: String,
            cookie: String?,
            timeoutMs: Long,
        ): EmbeddedPlayerUrlResolver.ResolvedStream {
            var created: EmbeddedPlayerWebView? = null
            val result = runCatching {
                withTimeout(timeoutMs) {
                    withContext(Dispatchers.Main) {
                        suspendCancellableCoroutine { cont ->
                            val wv = EmbeddedPlayerWebView(context, cont)
                            created = wv
                            cont.invokeOnCancellation { wv.close() }
                            wv.start(videoId, cookie)
                        }
                    }
                }
            }
            withContext(NonCancellable + Dispatchers.Main) { created?.close() }
            return result.getOrElse { e ->
                when (e) {
                    is TimeoutCancellationException -> throw EmbeddedPlayerException("Embedded player resolve timed out")
                    is CancellationException -> throw e
                    else -> throw EmbeddedPlayerException("Embedded player resolve failed: ${e.message}")
                }
            }
        }
    }
}

class EmbeddedPlayerException(message: String) : Exception(message)
