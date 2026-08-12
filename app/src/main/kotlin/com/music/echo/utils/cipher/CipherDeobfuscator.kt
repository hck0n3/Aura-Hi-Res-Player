package iad1tya.echo.music.utils.cipher

import android.content.Context
import android.net.Uri
import android.os.SystemClock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * Main cipher deobfuscation orchestrator for YouTube stream URLs.
 *
 * Handles both signature deobfuscation (for signatureCipher streams) and
 * n-parameter transformation (for throttle avoidance / 403 fix).
 */
object CipherDeobfuscator {
    private const val TAG = "Metrolist_CipherDeobfusc"

    lateinit var appContext: Context
        private set

    fun initialize(context: Context) {
        Timber.tag(TAG).d("CipherDeobfuscator initializing...")
        appContext = context.applicationContext
        Timber.tag(TAG).d("CipherDeobfuscator initialized")
    }

    private val rendererRecoveryPolicy = RendererRecoveryPolicy()
    private var cipherWebView: CipherWebView? = null
    private var currentPlayerHash: String? = null

    // The RemotePlayerConfig.configEpoch the current [cipherWebView] was built against. When the
    // epoch moves (a self-healing config was fetched and applied), the WebView is rebuilt on the
    // next use so the published fix takes effect WITHOUT an app restart.
    private var builtConfigEpoch = -1

    private val deobfuscateMutex = Mutex()

    /** True while a cipher/n-param WebView job holds the process-wide lock — speculative video prefetch must yield. */
    val isBusy: Boolean get() = deobfuscateMutex.isLocked

    @Volatile
    private var prewarmed = false

    /**
     * Best-effort warm-up of the cipher path BEFORE the first play, so the first song after a cold
     * start / fresh install doesn't pay for (a) the ~2.8 MB player.js fetch and (b) the multi-second
     * cipher WebView creation + JS discovery on the critical path.
     *
     * It warms the EXACT instances the real playback path reuses: it fills [PlayerJsFetcher]'s on-disk
     * cache and pre-creates the singleton [cipherWebView] that [getOrCreateWebView] then reuses for
     * every subsequent song. The WebView is long-lived — it is only recreated on a renderer death or a
     * player-hash change, never torn down between songs — so only this one prewarm pays the cost.
     *
     * Idempotent, never throws, never blocks playback. MUST be called off the main thread (the WebView
     * creation hops to Main internally). A prewarm timeout/failure is swallowed and, unlike the real
     * path, is deliberately NOT routed through the renderer-death backoff.
     */
    suspend fun prewarm() {
        if (prewarmed) return
        prewarmed = true
        try {
            deobfuscateMutex.withLock {
                if (cipherWebView == null) {
                    // forceRefresh=false → reuse the cached player.js and store the created WebView in
                    // [cipherWebView], exactly what the first real deobfuscate/transform would do.
                    getOrCreateWebView(forceRefresh = false)
                }
            }
        } catch (e: CancellationException) {
            prewarmed = false
            throw e
        } catch (e: Exception) {
            Timber.tag(TAG).d("Cipher prewarm skipped (best-effort): ${e.message}")
        }
    }

    /**
     * Deobfuscate a signatureCipher stream URL.
     *
     * The signatureCipher is a query string containing:
     * - s: The obfuscated signature
     * - sp: The signature parameter name (usually "sig" or "signature")
     * - url: The base stream URL
     *
     * Returns the full URL with deobfuscated signature, or null if failed.
     */
    suspend fun deobfuscateStreamUrl(signatureCipher: String, videoId: String): String? = deobfuscateMutex.withLock {
        try {
            deobfuscateInternal(signatureCipher, videoId, isRetry = false)
                ?.also { rendererRecoveryPolicy.onSuccess() }
        } catch (e: CancellationException) {
            throw e
        } catch (e: CipherTimeoutException) {
            onTimeout(e, "deobfuscate")
            null
        } catch (e: CipherRendererGoneException) {
            onRendererGone(e, "deobfuscate")
            null
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Cipher deobfuscation failed, retrying with fresh JS: ${e.message}")
            try {
                PlayerJsFetcher.invalidateCache()
                closeWebView()
                deobfuscateInternal(signatureCipher, videoId, isRetry = true)
                    ?.also { rendererRecoveryPolicy.onSuccess() }
            } catch (retryE: CancellationException) {
                throw retryE
            } catch (retryE: CipherTimeoutException) {
                onTimeout(retryE, "deobfuscate-retry")
                null
            } catch (retryE: CipherRendererGoneException) {
                onRendererGone(retryE, "deobfuscate-retry")
                null
            } catch (retryE: Exception) {
                Timber.tag(TAG).e(retryE, "Cipher deobfuscation retry also failed: ${retryE.message}")
                null
            }
        }
    }

    private suspend fun deobfuscateInternal(signatureCipher: String, videoId: String, isRetry: Boolean): String? {
        Timber.tag(TAG).d("deobfuscateInternal: videoId=$videoId, isRetry=$isRetry")

        // Parse the signatureCipher query string
        val params = parseQueryParams(signatureCipher)
        val obfuscatedSig = params["s"]
        val sigParam = params["sp"] ?: "signature"
        val baseUrl = params["url"]

        Timber.tag(TAG).d("Parsed signatureCipher params:")
        Timber.tag(TAG).d("  s (obfuscated sig): ${obfuscatedSig?.take(30)}... (length=${obfuscatedSig?.length})")
        Timber.tag(TAG).d("  sp (sig param name): $sigParam")
        Timber.tag(TAG).d("  url: ${baseUrl?.take(80)}...")

        if (obfuscatedSig == null || baseUrl == null) {
            Timber.tag(TAG).e("Could not parse signatureCipher params: s=${obfuscatedSig != null}, url=${baseUrl != null}")
            return null
        }

        val webView = getOrCreateWebView(forceRefresh = isRetry)
        if (webView == null) {
            Timber.tag(TAG).e("Failed to get/create CipherWebView")
            return null
        }

        Timber.tag(TAG).d("Calling webView.deobfuscateSignature()...")
        val deobfuscatedSig = webView.deobfuscateSignature(obfuscatedSig)
        Timber.tag(TAG).d("Deobfuscated signature: ${deobfuscatedSig.take(30)}... (length=${deobfuscatedSig.length})")

        // Build the URL with deobfuscated signature
        val separator = if ("?" in baseUrl) "&" else "?"
        val finalUrl = "$baseUrl${separator}${sigParam}=${Uri.encode(deobfuscatedSig)}"

        Timber.tag(TAG).d("=== CIPHER DEOBFUSCATION SUCCESS ===")
        Timber.tag(TAG).d("videoId: $videoId")
        Timber.tag(TAG).d("Final URL length: ${finalUrl.length}")
        Timber.tag(TAG).d("Final URL preview: ${finalUrl.take(100)}...")

        return finalUrl
    }

    /**
     * Transform the 'n' parameter in a streaming URL to avoid throttling/403.
     *
     * Uses the runtime-discovered n-function from the player JS WebView.
     * Returns the URL with the transformed 'n' value, or the original URL if transform fails.
     *
     * IMPORTANT: This must be called for WEB_REMIX, WEB, WEB_CREATOR, TVHTML5 clients
     * and for privately owned tracks (uploaded songs).
     */
    suspend fun transformNParamInUrl(url: String): String = deobfuscateMutex.withLock {
        Timber.tag(TAG).d("=== N-TRANSFORM URL ===")
        Timber.tag(TAG).d("Input URL length: ${url.length}")
        Timber.tag(TAG).d("Input URL preview: ${url.take(100)}...")

        // Serialize the n-transform through the SAME mutex as deobfuscateStreamUrl: both drive the
        // single long-lived CipherWebView with one shared continuation. Without this lock, concurrent
        // n-transforms (e.g. download-while-playing) overwrite the WebView's continuation and the
        // unlocked closeWebView could tear down the WebView the sig path is awaiting. Per-eval calls
        // are sub-second, so serializing is acceptable. transformNInternal/onTimeout/onRendererGone do
        // NOT take this mutex, so there is no re-entrant double-lock here.
        try {
            transformNInternal(url)
        } catch (e: CancellationException) {
            throw e
        } catch (e: CipherTimeoutException) {
            onTimeout(e, "n-transform")
            url
        } catch (e: CipherRendererGoneException) {
            onRendererGone(e, "n-transform")
            url
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "N-transform failed, returning original URL: ${e.message}")
            url
        }
    }

    private suspend fun transformNInternal(url: String): String {
        // Extract the 'n' parameter value from the URL
        val nMatch = Regex("[?&]n=([^&]+)").find(url)
        if (nMatch == null) {
            Timber.tag(TAG).d("No 'n' parameter found in URL, skipping transform")
            return url
        }

        val nValueEncoded = nMatch.groupValues[1]
        val nValue = Uri.decode(nValueEncoded)
        Timber.tag(TAG).d("N-param found:")
        Timber.tag(TAG).d("  encoded: $nValueEncoded")
        Timber.tag(TAG).d("  decoded: $nValue")

        val webView = getOrCreateWebView(forceRefresh = false)
        if (webView == null) {
            Timber.tag(TAG).e("Failed to get CipherWebView for n-transform")
            return url
        }

        Timber.tag(TAG).d("CipherWebView state:")
        Timber.tag(TAG).d("  nFunctionAvailable: ${webView.nFunctionAvailable}")
        Timber.tag(TAG).d("  discoveredNFuncName: ${webView.discoveredNFuncName}")
        Timber.tag(TAG).d("  usingHardcodedMode: ${webView.usingHardcodedMode}")

        if (!webView.nFunctionAvailable) {
            Timber.tag(TAG).e("N-transform function was not discovered at init time")
            return url
        }

        Timber.tag(TAG).d("Calling webView.transformN()...")
        val transformedN = webView.transformN(nValue)
        rendererRecoveryPolicy.onSuccess()

        Timber.tag(TAG).d("=== N-TRANSFORM SUCCESS ===")
        Timber.tag(TAG).d("N-param: $nValue -> $transformedN")

        // Replace n= parameter in URL
        val transformedUrl = url.replaceFirst(
            Regex("([?&])n=[^&]+"),
            "$1n=${Uri.encode(transformedN)}"
        )

        Timber.tag(TAG).d("Transformed URL length: ${transformedUrl.length}")
        return transformedUrl
    }

    private suspend fun onRendererGone(e: CipherRendererGoneException, where: String) {
        rendererRecoveryPolicy.onFailure(SystemClock.elapsedRealtime())
        Timber.tag(TAG).e(e, "WebView renderer gone during $where (consecutive failures: ${rendererRecoveryPolicy.consecutiveFailures}) — dropping cipher WebView")
        closeWebView()
    }

    /**
     * A create/eval TIMEOUT (slow or busy device), NOT a proven renderer death. Drop the wedged
     * WebView so the next song rebuilds it, but do NOT count it against the renderer-death backoff:
     * otherwise ~1–2 slow first-plays on a weak device would open the 60s no-WebView window and lock
     * streaming out of the cipher path. Genuine repeated onRenderProcessGone deaths still back off via
     * [onRendererGone].
     */
    private suspend fun onTimeout(e: CipherTimeoutException, where: String) {
        Timber.tag(TAG).w(e, "Cipher WebView timed out during $where — dropping WebView (NOT counted as a renderer death)")
        closeWebView()
    }

    private suspend fun getOrCreateWebView(forceRefresh: Boolean): CipherWebView? {
        Timber.tag(TAG).d("getOrCreateWebView: forceRefresh=$forceRefresh, existing=${cipherWebView != null}")

        // Never reuse a WebView whose renderer already died — its JS bridge is gone.
        if (cipherWebView?.isDead == true) {
            Timber.tag(TAG).w("Cached cipher WebView renderer is dead — discarding")
            closeWebView()
        }

        // After repeated renderer deaths (memory pressure), skip the doomed multi-second rebuild
        // for a short backoff window and let non-WebView fallbacks handle this song.
        if (!rendererRecoveryPolicy.shouldAttempt(SystemClock.elapsedRealtime())) {
            Timber.tag(TAG).w("Skipping cipher WebView creation: ${rendererRecoveryPolicy.consecutiveFailures} consecutive renderer deaths, in backoff window")
            return null
        }

        // Reuse only while the WebView was built against the CURRENT remote-config epoch: when a
        // self-healing config lands (startup refresh or reactive forceRefresh below), the epoch
        // moves and the next use rebuilds the WebView so the fix applies without an app restart.
        val epochAtStart = RemotePlayerConfig.configEpoch
        if (!forceRefresh && cipherWebView != null && builtConfigEpoch == epochAtStart) {
            Timber.tag(TAG).d("Reusing existing CipherWebView (hash=$currentPlayerHash, epoch=$builtConfigEpoch)")
            return cipherWebView
        }
        if (!forceRefresh && cipherWebView != null) {
            Timber.tag(TAG).d("Remote config epoch changed ($builtConfigEpoch -> $epochAtStart) — rebuilding cipher WebView")
        }
        var builtEpoch = epochAtStart

        // Close existing WebView if any
        if (cipherWebView != null) {
            Timber.tag(TAG).d("Closing existing CipherWebView...")
            closeWebView()
        }

        // Fetch player JS
        Timber.tag(TAG).d("Fetching player JS...")
        val result = PlayerJsFetcher.getPlayerJs(forceRefresh = forceRefresh)
        if (result == null) {
            Timber.tag(TAG).e("Failed to get player JS")
            return null
        }
        val (playerJs, hash) = result
        Timber.tag(TAG).d("Got player JS: hash=$hash, length=${playerJs.length}")

        // Run full analysis for logging - pass the known hash from PlayerJsFetcher
        Timber.tag(TAG).d("Analyzing player JS for cipher functions (knownHash=$hash)...")
        var analysis = FunctionNameExtractor.analyzePlayerJs(playerJs, knownHash = hash)

        if (analysis.sigInfo == null) {
            // REACTIVE self-healing: no sig pattern matched AND no (hardcoded or remote) config for
            // this hash — YouTube likely rotated player.js past everything we know. Trigger ONE
            // immediate remote-config refetch (bounded by a 5-minute cooldown inside forceRefresh)
            // and re-run the analysis, so a fix the owner just published heals streaming NOW
            // instead of only after an app restart. Best-effort: on a miss we fail exactly as before.
            Timber.tag(TAG).w("No cipher config for player hash $hash — trying reactive remote-config refresh")
            val healed = RemotePlayerConfig.forceRefresh(appContext, hash)
            if (healed) {
                builtEpoch = RemotePlayerConfig.configEpoch
                analysis = FunctionNameExtractor.analyzePlayerJs(playerJs, knownHash = hash)
            }
        }

        val sigInfo = analysis.sigInfo
        if (sigInfo == null) {
            Timber.tag(TAG).e("Could not extract signature function info from player JS")
            return null
        }

        val nFuncInfo = analysis.nFuncInfo
        if (nFuncInfo == null) {
            Timber.tag(TAG).w("Could not extract n-function info from player JS (will try brute-force)")
        }

        Timber.tag(TAG).d("Creating CipherWebView...")
        Timber.tag(TAG).d("  sig: ${sigInfo.name} (constantArg=${sigInfo.constantArg}, hardcoded=${sigInfo.isHardcoded})")
        Timber.tag(TAG).d("  nFunc: ${nFuncInfo?.name}[${nFuncInfo?.arrayIndex}] (hardcoded=${nFuncInfo?.isHardcoded})")

        // Create WebView
        val webView = CipherWebView.create(
            context = appContext,
            playerJs = playerJs,
            sigInfo = sigInfo,
            nFuncInfo = nFuncInfo,
        )

        Timber.tag(TAG).d("CipherWebView created successfully")
        Timber.tag(TAG).d("  nFunctionAvailable: ${webView.nFunctionAvailable}")
        Timber.tag(TAG).d("  sigFunctionAvailable: ${webView.sigFunctionAvailable}")
        Timber.tag(TAG).d("  discoveredNFuncName: ${webView.discoveredNFuncName}")

        cipherWebView = webView
        currentPlayerHash = hash
        builtConfigEpoch = builtEpoch
        return webView
    }

    private suspend fun closeWebView() {
        Timber.tag(TAG).d("closeWebView: existing=${cipherWebView != null}")
        withContext(Dispatchers.Main) {
            cipherWebView?.close()
        }
        cipherWebView = null
        currentPlayerHash = null
        Timber.tag(TAG).d("CipherWebView closed and cleared")
    }

    private fun parseQueryParams(query: String): Map<String, String> {
        val result = mutableMapOf<String, String>()
        for (pair in query.split("&")) {
            val idx = pair.indexOf('=')
            if (idx > 0) {
                val key = Uri.decode(pair.substring(0, idx))
                val value = Uri.decode(pair.substring(idx + 1))
                result[key] = value
            }
        }
        Timber.tag(TAG).v("parseQueryParams: ${result.keys.joinToString()}")
        return result
    }

    /**
     * Debug method: Get current state information
     */
    fun getDebugInfo(): Map<String, Any?> {
        return mapOf(
            "hasWebView" to (cipherWebView != null),
            "playerHash" to currentPlayerHash,
            "nFunctionAvailable" to cipherWebView?.nFunctionAvailable,
            "sigFunctionAvailable" to cipherWebView?.sigFunctionAvailable,
            "discoveredNFuncName" to cipherWebView?.discoveredNFuncName,
            "usingHardcodedMode" to cipherWebView?.usingHardcodedMode,
        )
    }
}
