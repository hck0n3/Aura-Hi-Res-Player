package iad1tya.echo.music.utils.potoken

import android.webkit.CookieManager
import iad1tya.echo.music.utils.cipher.CipherDeobfuscator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber

class PoTokenGenerator {
    private val TAG = "PoTokenGenerator"

    // `.isSuccess` discarded the throwable. On a device whose WebView is missing, disabled or mid-update
    // this silently disables poToken for the WHOLE process, every poToken client then fails, and the
    // customer reports "songs don't play on my phone" while an identical build works everywhere else —
    // with nothing in the log to distinguish it from a YouTube-side outage.
    private val webViewSupported by lazy {
        runCatching { CookieManager.getInstance() }
            .onFailure {
                Timber.tag("RESOLVE_FAIL").e(
                    "WebView unavailable, poToken disabled for this process: ${it.javaClass.simpleName}: ${it.message}"
                )
            }
            .isSuccess
    }
    private var webViewBadImpl = false // whether the system has a bad WebView implementation

    private val webPoTokenGenLock = Mutex()
    private var webPoTokenSessionId: String? = null
    private var webPoTokenStreamingPot: String? = null
    private var webPoTokenGenerator: PoTokenWebView? = null

    /** True while this generator holds the WebView poToken lock — speculative video prefetch must yield. */
    val isBusy: Boolean get() = webPoTokenGenLock.isLocked

    fun getWebClientPoToken(videoId: String, sessionId: String): PoTokenResult? {
        Timber.tag(TAG).d("getWebClientPoToken called: videoId=$videoId, sessionId=$sessionId")
        Timber.tag(TAG).d("WebView state: supported=$webViewSupported, badImpl=$webViewBadImpl")
        if (!webViewSupported || webViewBadImpl) {
            Timber.tag(TAG).d("WebView not available: supported=$webViewSupported, badImpl=$webViewBadImpl")
            return null
        }

        val result = runCatching {
            runBlocking {
                withTimeoutOrNull(POTOKEN_TIMEOUT_MS) {
                    getWebClientPoToken(videoId, sessionId, forceRecreate = false)
                }
            }
        }.getOrElse { e ->
            if (e is BadWebViewException) {
                Timber.tag(TAG).e(e, "Could not obtain poToken because WebView is broken")
                webViewBadImpl = true
            } else {
                Timber.tag(TAG).e(e, "poToken generation exception: ${e.javaClass.simpleName}: ${e.message}")
            }
            null
        }

        if (result == null) {
            // Either the call above threw (handled above) or withTimeoutOrNull hit POTOKEN_TIMEOUT_MS
            // without the block ever completing. Either way the WebView may be wedged: tear it down so
            // the NEXT call starts from a clean slate instead of blocking on a dead renderer again.
            Timber.tag(TAG).w("poToken unavailable after ${POTOKEN_TIMEOUT_MS}ms; proceeding without PoToken")
            runBlocking {
                webPoTokenGenLock.withLock {
                    try {
                        withContext(Dispatchers.Main) {
                            webPoTokenGenerator?.close()
                        }
                    } catch (closeEx: Exception) {
                        Timber.tag(TAG).e(closeEx, "Exception closing PoTokenWebView during cleanup")
                    }
                    webPoTokenGenerator = null
                    webPoTokenStreamingPot = null
                    webPoTokenSessionId = null
                }
            }
        }
        return result
    }

    /**
     * Pre-create the WebView + streaming poToken (the slow botguard init) BEFORE the first playback, so
     * the first song after opening the app starts faster. Fully guarded — never throws, never blocks
     * playback; if anything fails it just no-ops and the normal path runs at play time.
     */
    fun prewarm(sessionId: String) {
        if (!webViewSupported || webViewBadImpl) return
        runCatching {
            runBlocking {
                withTimeoutOrNull(POTOKEN_TIMEOUT_MS) {
                    webPoTokenGenLock.withLock {
                        val needsCreate = webPoTokenGenerator == null ||
                            webPoTokenGenerator!!.isExpired ||
                            webPoTokenSessionId != sessionId
                        if (needsCreate) {
                            withContext(Dispatchers.Main) { webPoTokenGenerator?.close() }
                            // Same all-or-nothing discipline as the real path: clear the fields after
                            // closing the old generator, build into LOCALS, destroy the WebView on a
                            // mid-flow failure, and assign the fields only after full success.
                            webPoTokenGenerator = null
                            webPoTokenStreamingPot = null
                            webPoTokenSessionId = null

                            val newGenerator = PoTokenWebView.getNewPoTokenGenerator(CipherDeobfuscator.appContext)
                            val newStreamingPot = try {
                                newGenerator.generatePoToken(sessionId)
                            } catch (t: Throwable) {
                                runCatching { newGenerator.close() }
                                throw t
                            }

                            // Handle nullable result from generatePoToken
                            newStreamingPot ?: run {
                                runCatching { newGenerator.close() }
                                throw PoTokenException("Prewarm streaming poToken generation returned null")
                            }

                            webPoTokenGenerator = newGenerator
                            webPoTokenStreamingPot = newStreamingPot
                            webPoTokenSessionId = sessionId
                            Timber.tag(TAG).d("poToken prewarmed for sessionId=${sessionId.take(16)}...")
                        }
                    }
                }
            }
        }.onFailure { Timber.tag(TAG).d("poToken prewarm skipped: ${it.message}") }
    }

    private companion object {
        // Healthy cold-start (WebView spin-up + botguard JS + token gen) is ~2–5s in practice;
        // 8s leaves slack for a slow device without making the user wait too long before the
        // fallback chain (ANDROID_VR, etc.) takes over when the WebView hangs.
        const val POTOKEN_TIMEOUT_MS = 8_000L
    }

    /**
     * @param forceRecreate whether to force the recreation of [webPoTokenGenerator], to be used in
     * case the current [webPoTokenGenerator] threw an error last time
     * [PoTokenWebView.generatePoToken] was called
     */
    private suspend fun getWebClientPoToken(videoId: String, sessionId: String, forceRecreate: Boolean): PoTokenResult {
        Timber.tag(TAG).d("Web poToken requested: videoId=$videoId, sessionId=$sessionId")

        val (poTokenGenerator, streamingPot, hasBeenRecreated) =
            webPoTokenGenLock.withLock {
                val shouldRecreate =
                    forceRecreate || webPoTokenGenerator == null || webPoTokenGenerator!!.isExpired ||
                        webPoTokenGenerator!!.isDead ||
                        webPoTokenSessionId != sessionId

                if (shouldRecreate) {
                    Timber.tag(TAG).d("Creating new PoTokenWebView (forceRecreate=$forceRecreate)")

                    withContext(Dispatchers.Main) {
                        webPoTokenGenerator?.close()
                    }
                    // The old generator is closed: clear the fields NOW so a throw mid-creation can
                    // never leave them pointing at a closed WebView with a stale streaming pot.
                    webPoTokenGenerator = null
                    webPoTokenStreamingPot = null
                    webPoTokenSessionId = null

                    // Build into LOCALS and assign to the fields only after the FULL flow succeeds:
                    // if generatePoToken throws mid-flow, the new WebView must be destroyed (not
                    // leaked) and the session fields must stay consistent (all-or-nothing).
                    val newGenerator = PoTokenWebView.getNewPoTokenGenerator(CipherDeobfuscator.appContext)

                    // The streaming poToken needs to be generated exactly once before generating
                    // any other (player) tokens.
                    val newStreamingPot = try {
                        newGenerator.generatePoToken(sessionId)
                    } catch (t: Throwable) {
                        // close() is idempotent and self-dispatches to the main thread, so it is safe
                        // here even when this coroutine is being cancelled (timeout).
                        runCatching { newGenerator.close() }
                        throw t
                    }

                    // Handle nullable result from generatePoToken
                    newStreamingPot ?: run {
                        runCatching { newGenerator.close() }
                        throw PoTokenException("Streaming poToken generation returned null")
                    }

                    webPoTokenGenerator = newGenerator
                    webPoTokenStreamingPot = newStreamingPot
                    webPoTokenSessionId = sessionId
                    Timber.tag(TAG).d("Streaming poToken generated for sessionId=${sessionId.take(20)}...")
                }

                Triple(webPoTokenGenerator!!, webPoTokenStreamingPot!!, shouldRecreate)
            }

        val playerPot = try {
            poTokenGenerator.generatePoToken(videoId)
        } catch (throwable: Throwable) {
            if (hasBeenRecreated) {
                // the poTokenGenerator has just been recreated (and possibly this is already the
                // second time we try), so there is likely nothing we can do
                throw throwable
            } else {
                // retry, this time recreating the [webPoTokenGenerator] from scratch;
                // this might happen for example if the app goes in the background and the WebView
                // content is lost
                Timber.tag(TAG).e(throwable, "Failed to obtain poToken, retrying")
                return getWebClientPoToken(videoId = videoId, sessionId = sessionId, forceRecreate = true)
            }
        }

        // Handle nullable result from generatePoToken
        playerPot ?: throw PoTokenException("Player poToken generation returned null")

        Timber.tag(TAG).d("poToken generated successfully: session=${streamingPot.take(20)}..., video=${playerPot.take(20)}...")

        // CONTENT BINDING — these two are NOT interchangeable, and both are String, so a swap compiles
        // silently and only shows up as HTTP 403 / streams that die mid-song:
        //  - the /player REQUEST token must be bound to the VIDEO ID   -> playerPot   (generated from videoId)
        //  - the STREAM URL token (?pot=) must be bound to the SESSION -> streamingPot (generated from sessionId)
        // They were assigned crosswise here, so every request carried the other one's binding.
        return PoTokenResult(
            playerRequestPoToken = playerPot,
            streamingDataPoToken = streamingPot,
        )
    }
}
