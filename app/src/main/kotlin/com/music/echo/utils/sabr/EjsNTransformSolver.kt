package iad1tya.echo.music.utils.sabr

import android.content.Context
import android.net.Uri
import android.webkit.ConsoleMessage
import android.webkit.JavascriptInterface
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import iad1tya.echo.music.utils.cipher.CipherDeobfuscator
import iad1tya.echo.music.utils.cipher.PlayerJsFetcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber
import java.io.File
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException


object EjsNTransformSolver {
    private const val TAG = "echomusic_EjsNSolver"

    private var solverWebView: SolverWebView? = null

    
    suspend fun transformNParamInUrl(url: String): String {
        val nMatch = Regex("[?&]n=([^&]+)").find(url)
        if (nMatch == null) {
            Timber.tag(TAG).d("No 'n' parameter in SABR URL")
            return url
        }
        val nValue = Uri.decode(nMatch.groupValues[1])
        Timber.tag(TAG).d("SABR n-param: $nValue")

        // Run in the normal (cancellable) context. The previous NonCancellable wrapping made an
        // outer timeout/skip unable to interrupt a wedged WebView, so the resolution coroutine could
        // hang forever. Every failure branch below FALLS BACK to the original url (never throws into
        // the caller), so streaming continues on the happy path and degrades gracefully otherwise.
        val solver = getOrCreateSolver()
        if (solver == null) {
            return url
        }

        if (!solver.nFunctionAvailable) {
            Timber.tag(TAG).e("EJS n-solver not available")
            return url
        }

        return try {
            val transformed = solver.transformN(nValue)
            if (transformed == null) {
                // WebView eval never called back within the timeout (wedged/dead renderer).
                Timber.tag(TAG).e("SABR n-transform timed out; using original url")
                return url
            }
            Timber.tag(TAG).d("SABR n-param transformed: $nValue -> $transformed")
            url.replaceFirst(
                Regex("([?&])n=[^&]+"),
                "$1n=${Uri.encode(transformed)}"
            )
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "SABR n-transform failed: ${e.message}")
            url
        }
    }

    private suspend fun getOrCreateSolver(): SolverWebView? {
        solverWebView?.let { if (!it.isDead) return it }
        // A previously cached solver whose renderer died is unusable; drop it and rebuild.
        solverWebView = null

        val result = PlayerJsFetcher.getPlayerJs(forceRefresh = false)
        if (result == null) {
            Timber.tag(TAG).e("Failed to get player JS for EJS solver")
            return null
        }
        val (playerJs, hash) = result
        Timber.tag(TAG).d("Creating EJS n-solver (player hash=$hash, ${playerJs.length} chars)")

        return try {
            val sv = SolverWebView.create(CipherDeobfuscator.appContext, playerJs)
            if (sv == null) {
                // create() timed out or the renderer died before init completed.
                Timber.tag(TAG).e("EJS solver creation timed out; falling back")
                null
            } else {
                solverWebView = sv
                sv
            }
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Failed to create EJS solver: ${e.message}")
            null
        }
    }

    suspend fun close() {
        withContext(Dispatchers.Main) {
            solverWebView?.close()
        }
        solverWebView = null
    }

    class SolverWebView private constructor(
        context: Context,
        private val playerJs: String,
        initContinuation: Continuation<SolverWebView>,
    ) {
        private val webView = WebView(context)

        private var initContinuation: Continuation<SolverWebView>? = initContinuation
        private var nContinuation: Continuation<String>? = null

        @Volatile
        var isDead: Boolean = false
            private set

        @Volatile
        private var destroyed = false

        @Volatile
        var nFunctionAvailable: Boolean = false
            private set

        init {
            val settings = webView.settings
            @Suppress("SetJavaScriptEnabled")
            settings.javaScriptEnabled = true
            settings.allowFileAccess = true
            @Suppress("DEPRECATION")
            settings.allowFileAccessFromFileURLs = true
            settings.blockNetworkLoads = true

            webView.addJavascriptInterface(this, "EjsBridge")

            // Without this, a renderer death (e.g. OOM-kill) crashes the whole app AND leaves the
            // init/n continuations forever unresumed. Resume them with an exception and return true
            // so the process survives, mirroring CipherWebView.onRenderProcessGone.
            webView.webViewClient = object : WebViewClient() {
                @androidx.annotation.RequiresApi(android.os.Build.VERSION_CODES.O)
                override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
                    val didCrash = runCatching { detail.didCrash() }.getOrNull()
                    Timber.tag(TAG).e("=== EJS RENDER PROCESS GONE === didCrash=$didCrash")
                    onRendererGone("EJS WebView render process gone (didCrash=$didCrash)")
                    return true
                }
            }

            webView.webChromeClient = object : WebChromeClient() {
                override fun onConsoleMessage(m: ConsoleMessage): Boolean {
                    val msg = m.message()
                    if (msg.contains("Uncaught")) {
                        Timber.tag(TAG).e("EJS WebView error: $msg at ${m.sourceId()}:${m.lineNumber()}")
                    }
                    return super.onConsoleMessage(m)
                }
            }
        }

        /**
         * Renderer process died (OOM-killed or crashed). Marks this instance dead, fails every
         * pending continuation so no caller hangs, and tears the WebView down. The next resolution
         * rebuilds a fresh solver via [getOrCreateSolver].
         */
        private fun onRendererGone(reason: String) {
            isDead = true
            val e = SabrException(reason)
            takeInitContinuation()?.resumeSafely { it.resumeWithException(e) }
            takeNContinuation()?.resumeSafely { it.resumeWithException(e) }
            close()
        }

        @Synchronized
        private fun takeInitContinuation(): Continuation<SolverWebView>? =
            initContinuation.also { initContinuation = null }

        @Synchronized
        private fun takeNContinuation(): Continuation<String>? =
            nContinuation.also { nContinuation = null }

        private inline fun <T> T.resumeSafely(block: (T) -> Unit) {
            runCatching { block(this) }
        }

        private fun loadSolverAndPlayer() {
            val cacheDir = File(webView.context.cacheDir, "ejs_solver")
            cacheDir.mkdirs()

            val assetManager = webView.context.assets
            for (file in listOf("meriyah.js", "astring.js", "yt.solver.core.js")) {
                assetManager.open("solver/$file").use { input ->
                    File(cacheDir, file).outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
            }

            File(cacheDir, "player.js").writeText(playerJs)
            Timber.tag(TAG).d("EJS solver assets written (${playerJs.length} chars)")

            val html = """<!DOCTYPE html>
<html><head>
<script src="meriyah.js"></script>
<script src="astring.js"></script>
<script src="yt.solver.core.js"></script>
<script>
var _nSolver = null;

function initSolver() {
    try {
        EjsBridge.onLog('Reading player.js via XHR...');
        var xhr = new XMLHttpRequest();
        xhr.open('GET', 'player.js', true);
        xhr.onload = function() {
            try {
                var playerCode = xhr.responseText;
                if (!playerCode || playerCode.length < 1000) {
                    EjsBridge.onSolverError('Player JS too small: ' + (playerCode ? playerCode.length : 0));
                    return;
                }
                EjsBridge.onLog('Preprocessing with EJS solver (' + playerCode.length + ' chars)...');

                var result = jsc({
                    type: 'player',
                    player: playerCode,
                    requests: [],
                    output_preprocessed: true
                });

                if (result.type === 'error') {
                    EjsBridge.onSolverError('EJS preprocess: ' + result.error);
                    return;
                }

                EjsBridge.onLog('Evaluating preprocessed code...');
                var resultObj = {n: null, sig: null};
                (new Function('_result', result.preprocessed_player))(resultObj);

                _nSolver = resultObj.n;
                EjsBridge.onSolverReady((typeof _nSolver === 'function').toString());
            } catch(e) {
                EjsBridge.onSolverError(e.toString() + '\n' + (e.stack || ''));
            }
        };
        xhr.onerror = function() {
            EjsBridge.onSolverError('XHR failed to read player.js');
        };
        xhr.send();
    } catch(e) {
        EjsBridge.onSolverError(e.toString() + '\n' + (e.stack || ''));
    }
}

function transformN(nValue) {
    try {
        if (!_nSolver) {
            EjsBridge.onNError('N solver not available');
            return;
        }
        var result = _nSolver(nValue);
        if (result === undefined || result === null) {
            EjsBridge.onNError('N solver returned null/undefined');
            return;
        }
        EjsBridge.onNResult(String(result));
    } catch(e) {
        EjsBridge.onNError(e.toString() + '\n' + (e.stack || ''));
    }
}
</script>
</head><body onload="initSolver()"></body></html>"""

            webView.loadDataWithBaseURL(
                "file://${cacheDir.absolutePath}/",
                html, "text/html", "utf-8", null
            )
        }

        @JavascriptInterface
        fun onLog(message: String) {
            Timber.tag(TAG).d(message)
        }

        @JavascriptInterface
        fun onSolverReady(nAvailable: String) {
            nFunctionAvailable = nAvailable == "true"
            Timber.tag(TAG).d("EJS solver ready: n=$nFunctionAvailable")
            takeInitContinuation()?.resumeSafely { it.resume(this) }
        }

        @JavascriptInterface
        fun onSolverError(error: String) {
            Timber.tag(TAG).e("EJS solver error: $error")
            takeInitContinuation()?.resumeSafely { it.resume(this) }
        }

        /**
         * Runs the n-transform inside the WebView. Returns null if the eval never called back within
         * [EVAL_TIMEOUT_MS] (wedged/dead renderer) so the caller can fall back to the original url
         * instead of hanging. The suspension is cancellable and clears [nContinuation] on cancel.
         */
        suspend fun transformN(nValue: String): String? {
            if (!nFunctionAvailable || isDead) {
                throw SabrException("EJS n-transform not available")
            }

            return withTimeoutOrNull(EVAL_TIMEOUT_MS) {
                withContext(Dispatchers.Main) {
                    suspendCancellableCoroutine { cont ->
                        nContinuation = cont
                        cont.invokeOnCancellation { takeNContinuation() }
                        webView.evaluateJavascript(
                            "transformN('${escapeJsString(nValue)}')",
                            null
                        )
                    }
                }
            }
        }

        @JavascriptInterface
        fun onNResult(result: String) {
            Timber.tag(TAG).d("N-transform result: ${result.take(50)}")
            takeNContinuation()?.resumeSafely { it.resume(result) }
        }

        @JavascriptInterface
        fun onNError(error: String) {
            Timber.tag(TAG).e("N-transform error: $error")
            takeNContinuation()?.resumeSafely { it.resumeWithException(SabrException("EJS n-transform failed: $error")) }
        }

        fun close() {
            // Idempotent: now reachable from onRendererGone, create-timeout cleanup, and the outer
            // close(), so guard against a double webView.destroy().
            if (destroyed) return
            destroyed = true
            runCatching {
                webView.clearHistory()
                webView.clearCache(true)
                webView.loadUrl("about:blank")
                webView.onPause()
                webView.removeAllViews()
                webView.destroy()
            }.onFailure { Timber.tag(TAG).w("EJS WebView teardown threw: $it") }
            Timber.tag(TAG).d("EJS solver WebView closed")
        }

        private fun escapeJsString(s: String): String {
            return s.replace("\\", "\\\\")
                .replace("'", "\\'")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
        }

        companion object {
            // Cold-start (WebView spin-up + EJS preprocess of the multi-MB player.js) is a few
            // seconds on a healthy device; these ceilings turn a wedged/dead solver into a fast
            // fallback instead of an unbounded hang. Per-eval n-transform is sub-second.
            private const val CREATE_TIMEOUT_MS = 15_000L
            private const val EVAL_TIMEOUT_MS = 8_000L

            /**
             * Returns null if the WebView never fired onSolverReady/onSolverError within
             * [CREATE_TIMEOUT_MS] (page never loaded, JS never hit the bridge, or renderer died).
             * Callers fall back to the untransformed url in that case.
             */
            suspend fun create(context: Context, playerJs: String): SolverWebView? {
                var created: SolverWebView? = null
                val result = withTimeoutOrNull(CREATE_TIMEOUT_MS) {
                    withContext(Dispatchers.Main) {
                        suspendCancellableCoroutine { cont ->
                            val sv = SolverWebView(context, playerJs, cont)
                            created = sv
                            cont.invokeOnCancellation { sv.takeInitContinuation() }
                            sv.loadSolverAndPlayer()
                        }
                    }
                }
                if (result == null) {
                    // Timed out: tear the half-built WebView down so it doesn't leak/linger.
                    created?.let { sv ->
                        withContext(NonCancellable + Dispatchers.Main) {
                            sv.isDead = true
                            sv.takeInitContinuation()
                            sv.close()
                        }
                    }
                }
                return result
            }
        }
    }
}
