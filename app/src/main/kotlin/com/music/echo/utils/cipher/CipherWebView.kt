package iad1tya.echo.music.utils.cipher

import android.content.Context
import android.webkit.ConsoleMessage
import android.webkit.JavascriptInterface
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import timber.log.Timber
import java.io.File
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * WebView-based cipher executor for YouTube stream URL deobfuscation
 *
 * Executes signature decipher and n-transform functions extracted from player.js.
 * Supports both regex-extracted functions and hardcoded fallback for Q-array obfuscated players.
 */
class CipherWebView private constructor(
    context: Context,
    private val playerJs: String,
    private val sigInfo: FunctionNameExtractor.SigFunctionInfo?,
    private val nFuncInfo: FunctionNameExtractor.NFunctionInfo?,
    initContinuation: Continuation<CipherWebView>,
) {
    private val webView = WebView(context)

    private var initContinuation: Continuation<CipherWebView>? = initContinuation
    private var sigContinuation: Continuation<String>? = null
    private var nContinuation: Continuation<String>? = null

    @Volatile
    var isDead: Boolean = false
        private set

    @Volatile
    private var destroyed = false

    @Volatile
    var nFunctionAvailable: Boolean = false
        private set

    @Volatile
    var sigFunctionAvailable: Boolean = false
        private set

    @Volatile
    var discoveredNFuncName: String? = null
        private set

    @Volatile
    var usingHardcodedMode: Boolean = false
        private set

    init {
        Timber.tag(TAG).d("Initializing CipherWebView...")
        Timber.tag(TAG).d("  sigInfo: name=${sigInfo?.name}, constantArg=${sigInfo?.constantArg}, hardcoded=${sigInfo?.isHardcoded}")
        Timber.tag(TAG).d("  nFuncInfo: name=${nFuncInfo?.name}, arrayIdx=${nFuncInfo?.arrayIndex}, hardcoded=${nFuncInfo?.isHardcoded}")

        val settings = webView.settings
        @Suppress("SetJavaScriptEnabled")
        settings.javaScriptEnabled = true
        settings.allowFileAccess = true
        @Suppress("DEPRECATION")
        settings.allowFileAccessFromFileURLs = true
        settings.blockNetworkLoads = true

        webView.addJavascriptInterface(this, JS_INTERFACE)

        webView.webViewClient = object : WebViewClient() {
            @androidx.annotation.RequiresApi(android.os.Build.VERSION_CODES.O)
            override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
                val didCrash = runCatching { detail.didCrash() }.getOrNull()
                Timber.tag(TAG).e("=== RENDER PROCESS GONE === didCrash=$didCrash")
                onRendererGone("WebView render process gone (didCrash=$didCrash)")
                return true
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onConsoleMessage(m: ConsoleMessage): Boolean {
                val msg = m.message()
                val src = "${m.sourceId()}:${m.lineNumber()}"

                // TRUNCATED, and only ERROR persists — same reasoning as PoTokenWebView. This is console
                // output from YouTube's ~2 MB player.js operating on the signature and `n` values, so
                // the message can carry those values, and a chatty player.js can burst fast enough to
                // rotate the 256 KB log away on its own. The error identity is what is actionable when
                // YouTube rotates the cipher; the payload never was.
                when (m.messageLevel()) {
                    ConsoleMessage.MessageLevel.ERROR -> {
                        if (!msg.contains("is not defined")) {
                            Timber.tag(TAG).e("JS ERROR: ${msg.take(200)} at $src")
                        }
                    }
                    ConsoleMessage.MessageLevel.WARNING -> {
                        Timber.tag(TAG).d("JS WARN: $msg at $src")
                    }
                    else -> {
                        Timber.tag(TAG).v("JS LOG: $msg")
                    }
                }
                return super.onConsoleMessage(m)
            }
        }

        Timber.tag(TAG).d("WebView settings configured")
    }

    /**
     * Called when the WebView's renderer process dies (OOM-killed under memory pressure, or
     * crashed). Marks this instance dead, fails every pending continuation with a
     * [CipherRendererGoneException] so callers don't hang, and tears the WebView down. The
     * orchestrator (CipherDeobfuscator) then discards this instance and applies backoff.
     */
    private fun onRendererGone(reason: String) {
        isDead = true
        val e = CipherRendererGoneException(reason)
        runCatching { takeInitContinuation()?.resumeWithException(e) }
        runCatching { takeSigContinuation()?.resumeWithException(e) }
        runCatching { takeNContinuation()?.resumeWithException(e) }
        destroyWebView()
    }

    @Synchronized
    private fun takeInitContinuation(): Continuation<CipherWebView>? =
        initContinuation.also { initContinuation = null }

    @Synchronized
    private fun takeSigContinuation(): Continuation<String>? =
        sigContinuation.also { sigContinuation = null }

    @Synchronized
    private fun takeNContinuation(): Continuation<String>? =
        nContinuation.also { nContinuation = null }

    private inline fun <T> T.resumeSafely(block: (T) -> Unit) {
        runCatching { block(this) }
    }

    private fun throwIfDead() {
        if (isDead) {
            throw CipherRendererGoneException("CipherWebView renderer is gone")
        }
    }

    /**
     * A JS evaluation (sig/n) that never called back within the timeout. The renderer is WEDGED
     * (slow/busy CPU), not proven dead: we mark this instance dead so the orchestrator drops it and
     * rebuilds on the next song, but we surface a [CipherTimeoutException] — NOT renderer-gone — so a
     * mere timeout does not count toward the renderer-death backoff and lock out the WebView path.
     */
    private fun failAsTimeout(reason: String): Nothing {
        isDead = true
        takeSigContinuation()
        takeNContinuation()
        throw CipherTimeoutException(reason)
    }

    private fun loadPlayerJsFromFile() {
        val sigFuncName = sigInfo?.name
        val nFuncName = nFuncInfo?.name
        val nArrayIdx = nFuncInfo?.arrayIndex
        val isHardcoded = sigInfo?.isHardcoded == true || nFuncInfo?.isHardcoded == true

        Timber.tag(TAG).d("=== LOADING PLAYER.JS INTO WEBVIEW ===")
        Timber.tag(TAG).d("Player.js size: ${playerJs.length} chars")
        Timber.tag(TAG).d("Export mode: ${if (isHardcoded) "HARDCODED" else "EXTRACTED"}")
        Timber.tag(TAG).d("Sig function: $sigFuncName (constantArg=${sigInfo?.constantArg})")
        Timber.tag(TAG).d("N function: $nFuncName (arrayIdx=$nArrayIdx)")

        usingHardcodedMode = isHardcoded

        val exports = buildList {
            val sigJsExpr = sigInfo?.jsExpression
            if (sigJsExpr != null) {
                val expr = sigJsExpr.replace("INPUT", "sig")
                Timber.tag(TAG).d("Sig: expression-based export: $expr")
                add("window._cipherSigFunc = function(sig) { try { return $expr; } catch(e) { return null; } };")
            } else if (sigFuncName != null) {
                val sigConstArgs = sigInfo?.constantArgs
                val preprocessFunc = sigInfo?.preprocessFunc
                val preprocessArgs = sigInfo?.preprocessArgs

                if (!sigConstArgs.isNullOrEmpty() && preprocessFunc != null && !preprocessArgs.isNullOrEmpty()) {
                    val mainArgsStr = sigConstArgs.joinToString(", ")
                    val prepArgsStr = preprocessArgs.joinToString(", ")
                    Timber.tag(TAG).d("Sig function needs full wrapper:")
                    Timber.tag(TAG).d("  $sigFuncName($mainArgsStr, $preprocessFunc($prepArgsStr, sig))")
                    add("window._cipherSigFunc = function(sig) { return $sigFuncName($mainArgsStr, $preprocessFunc($prepArgsStr, sig)); };")
                } else if (!sigConstArgs.isNullOrEmpty()) {
                    val argsStr = sigConstArgs.joinToString(", ")
                    Timber.tag(TAG).d("Sig function needs wrapper with constant args: $argsStr")
                    add("window._cipherSigFunc = function(sig) { return $sigFuncName($argsStr, sig); };")
                } else if (isHardcoded) {
                    Timber.tag(TAG).d("Will export sig function $sigFuncName in hardcoded mode (legacy)")
                    add("window._cipherSigFunc = typeof $sigFuncName !== 'undefined' ? $sigFuncName : null;")
                } else {
                    add("window._cipherSigFunc = typeof $sigFuncName !== 'undefined' ? $sigFuncName : null;")
                }
            }
            val nJsExpr = nFuncInfo?.jsExpression
            if (nJsExpr != null) {
                val expr = nJsExpr.replace("INPUT", "n")
                Timber.tag(TAG).d("N: expression-based export: ${expr.take(80)}")
                add("window._nTransformFunc = function(n) { try { return $expr; } catch(e) { return n; } };")
            } else if (nFuncName != null) {
                val nConstArgs = nFuncInfo?.constantArgs
                if (!nConstArgs.isNullOrEmpty()) {
                    val argsStr = nConstArgs.joinToString(", ")
                    Timber.tag(TAG).d("N-function needs wrapper with constant args: $argsStr")
                    add("window._nTransformFunc = function(n) { return $nFuncName($argsStr, n); };")
                } else {
                    val nExpr = if (nArrayIdx != null) {
                        "$nFuncName[$nArrayIdx]"
                    } else {
                        nFuncName
                    }
                    add("window._nTransformFunc = typeof $nFuncName !== 'undefined' ? $nExpr : null;")
                }
            }
        }

        Timber.tag(TAG).d("Export statements: ${exports.size}")
        exports.forEachIndexed { idx, stmt ->
            Timber.tag(TAG).v("  Export[$idx]: ${stmt.take(80)}...")
        }

        val modifiedJs = if (exports.isNotEmpty()) {
            val exportCode = "; " + exports.joinToString(" ")
            
            val lastIifeEnd = playerJs.lastIndexOf("})(")
            val lastIifeCallEnd = playerJs.lastIndexOf("}).call(")
            val injectionIndex = maxOf(lastIifeEnd, lastIifeCallEnd)
            
            if (injectionIndex != -1) {
                Timber.tag(TAG).d("Exports injected into IIFE closure")
                playerJs.substring(0, injectionIndex) + exportCode + playerJs.substring(injectionIndex)
            } else {
                Timber.tag(TAG).w("Export injection point not found, appending exports")
                playerJs + "\n" + exportCode
            }
        } else {
            Timber.tag(TAG).w("No exports to inject")
            playerJs
        }

        val cacheDir = File(webView.context.cacheDir, "cipher")
        cacheDir.mkdirs()
        val playerJsFile = File(cacheDir, "player.js")
        playerJsFile.writeText(modifiedJs)
        Timber.tag(TAG).d("Player.js written to cache: ${playerJsFile.absolutePath} (${modifiedJs.length} chars)")

        // Build HTML with comprehensive discovery and validation
        val html = buildDiscoveryHtml()
        Timber.tag(TAG).d("Discovery HTML built (${html.length} chars)")

        webView.loadDataWithBaseURL(
            "file://${cacheDir.absolutePath}/",
            html, "text/html", "utf-8", null
        )
        Timber.tag(TAG).d("WebView loading started...")
    }

    /**
     * Build HTML with JS discovery logic
     *
     * Key changes from original:
     * 1. Removed outdated `_w8_` pattern check
     * 2. Accept any valid alphanumeric transform result
     * 3. More comprehensive logging to bridge
     */
    private fun buildDiscoveryHtml(): String = """<!DOCTYPE html>
<html><head><script>
// ============================================================
// SIGNATURE DEOBFUSCATION
// ============================================================
function deobfuscateSig(funcName, constantArg, obfuscatedSig) {
    CipherBridge.logDebug("deobfuscateSig called: funcName=" + funcName + ", constantArg=" + constantArg + ", sigLen=" + obfuscatedSig.length);

    try {
        var func = window._cipherSigFunc;
        CipherBridge.logDebug("window._cipherSigFunc type: " + typeof func + ", length: " + (func ? func.length : "N/A"));

        if (typeof func !== 'function') {
            CipherBridge.onSigError("Sig func not found on window (type: " + typeof func + ")");
            return;
        }

        var result;
        // Check if this is a wrapper function (takes 1 arg: sig) vs direct function (takes 2+ args)
        if (func.length === 1) {
            // Wrapper function: window._cipherSigFunc = function(sig) { return JI(48, 1918, f1(1, 6528, sig)); }
            CipherBridge.logDebug("Calling wrapped sig func with just sig (func.length=1)");
            result = func(obfuscatedSig);
        } else if (constantArg !== null && constantArg !== undefined) {
            // Direct function with constantArg
            CipherBridge.logDebug("Calling sig func with constantArg: " + constantArg);
            result = func(constantArg, obfuscatedSig);
        } else {
            CipherBridge.logDebug("Calling sig func without constantArg");
            result = func(obfuscatedSig);
        }

        if (result === undefined || result === null) {
            CipherBridge.onSigError("Function returned null/undefined");
            return;
        }

        CipherBridge.logDebug("Sig result type: " + typeof result + ", length: " + String(result).length);
        CipherBridge.onSigResult(String(result));
    } catch (error) {
        CipherBridge.onSigError(error + "\n" + (error.stack || ""));
    }
}

// ============================================================
// N-PARAMETER TRANSFORM
// ============================================================
function transformN(nValue) {
    CipherBridge.logDebug("transformN called: nValue=" + nValue);

    try {
        var func = window._nTransformFunc;
        CipherBridge.logDebug("window._nTransformFunc type: " + typeof func);

        if (typeof func !== 'function') {
            CipherBridge.onNError("N-transform func not available (type: " + typeof func + ")");
            return;
        }

        var result = func(nValue);
        CipherBridge.logDebug("N-transform raw result: " + (result ? String(result).substring(0, 50) : "null/undefined"));

        if (result === undefined || result === null) {
            CipherBridge.onNError("N-transform returned null/undefined");
            return;
        }

        var resultStr = String(result);
        CipherBridge.logDebug("N-transform result: length=" + resultStr.length + ", value=" + resultStr.substring(0, 30));
        CipherBridge.onNResult(resultStr);
    } catch (error) {
        CipherBridge.onNError(error + "\n" + (error.stack || ""));
    }
}

// ============================================================
// RUNTIME RESULT VALIDATORS
// ============================================================
// A signature transform is ALWAYS a character-level rearrangement (reverse/swap/splice)
// of the exact input array — it never invents new characters. That makes "every output
// char comes from the input's character bag" a strong, obfuscation-proof signal, far more
// reliable than matching the current minifier's exact source syntax with regex.
function charMultisetSubset(input, output) {
    var counts = {};
    for (var i = 0; i < input.length; i++) {
        counts[input[i]] = (counts[input[i]] || 0) + 1;
    }
    for (var j = 0; j < output.length; j++) {
        var c = output[j];
        if (!counts[c]) return false;
        counts[c]--;
    }
    return true;
}

function looksLikeSigResult(input, output) {
    if (typeof output !== 'string') return false;
    if (output === input) return false;
    // Splice-based ciphers can drop more than a couple characters (verified against a real
    // player.js: 83 chars in, 78 out — a 5-char drop). The real correctness proof is the
    // character-multiset-subset check below; this length band only rejects degenerate results
    // (near-empty strings), so keep it generous rather than guessing an exact drop count.
    if (output.length < input.length - 15 || output.length > input.length) return false;
    return charMultisetSubset(input, output);
}

function looksLikeNResult(input, output) {
    if (typeof output !== 'string') return false;
    if (output === input) return false;
    if (output.length < 5) return false;
    return /^[a-zA-Z0-9_-]+$/.test(output);
}

// ============================================================
// FUNCTION DISCOVERY AND INITIALIZATION
// ============================================================
function discoverAndInit() {
    CipherBridge.logDebug("========== DISCOVERY AND INIT ==========");

    // Realistic-shaped probes: a ~84-char urlsafe-alphabet string for sig (typical obfuscated
    // signature length) and a short urlsafe string for n (typical throttle-param length).
    var SIG_TEST = "AOq0QJ8wRQIhAOZ1FvVYX3vwQnJmZK9tRk7bY0J5xW2pP8oT4sL6mN1cVbA9dEfGhIjKlMnOpQrStUv-_Wx";
    var N_TEST = "T2Xw3pWQ_Wk0xbOg";

    var nFuncName = "";
    var sigFuncName = "";
    var info = "";
    var nInfo = "";

    // Validate (not just trust) the statically-exported sig function: a regex hit on the WRONG
    // function name would otherwise silently produce garbage signatures instead of failing loudly.
    if (typeof window._cipherSigFunc === 'function') {
        try {
            var sigTestResult = window._cipherSigFunc(SIG_TEST);
            if (looksLikeSigResult(SIG_TEST, sigTestResult)) {
                sigFuncName = "exported_sig_func";
                CipherBridge.logDebug("Exported sig function VALID: " + String(sigTestResult).substring(0, 30));
            } else {
                CipherBridge.logDebug("Exported sig function produced invalid result, discarding: " + sigTestResult);
                window._cipherSigFunc = null;
            }
        } catch (e) {
            CipherBridge.logDebug("Exported sig function threw, discarding: " + e);
            window._cipherSigFunc = null;
        }
    } else {
        CipherBridge.logDebug("window._cipherSigFunc not exported (type=" + typeof window._cipherSigFunc + "), will try brute force");
    }

    // Check if N-transform function was exported
    if (typeof window._nTransformFunc === 'function') {
        CipherBridge.logDebug("Testing exported window._nTransformFunc...");
        try {
            var testResult = window._nTransformFunc(N_TEST);
            CipherBridge.logDebug("N-func test result: " + (testResult ? String(testResult).substring(0, 50) : "null"));

            if (looksLikeNResult(N_TEST, testResult)) {
                nFuncName = "exported_n_func";
                nInfo = "export_valid,test=" + String(testResult).substring(0, 20);
                CipherBridge.logDebug("N-function VALID: " + testResult);
            } else {
                nInfo = "export_bad_result:" + testResult;
                CipherBridge.logDebug("N-function export invalid, discarding");
                window._nTransformFunc = null;
            }
        } catch(e) {
            nInfo = "export_threw:" + e;
            CipherBridge.logDebug("N-function threw exception: " + e);
            window._nTransformFunc = null;
        }
    } else {
        CipherBridge.logDebug("window._nTransformFunc not exported, will try brute force");
    }

    // Single-pass brute-force scan for whichever function(s) are still missing. Same technique
    // real extractors use for resilience: instead of parsing the current minifier's exact syntax,
    // execute every 1-arg candidate live in the real JS engine and keep the one whose output shape
    // proves it's the right transform. This survives obfuscation changes that break regex patterns.
    if (!sigFuncName || !nFuncName) {
        try {
            var keys = Object.getOwnPropertyNames(window);
            var sigCandidates = 0;
            var nCandidates = 0;
            CipherBridge.logDebug("Brute force: scanning " + keys.length + " window properties (need sig=" + !sigFuncName + ", n=" + !nFuncName + ")");

            for (var i = 0; i < keys.length; i++) {
                var key = keys[i];
                if (key.startsWith("webkit") || key.startsWith("on") ||
                    key === "CipherBridge" || key === "_cipherSigFunc" ||
                    key === "_nTransformFunc" || key === "window" || key === "self") {
                    continue;
                }

                var fn;
                try { fn = window[key]; } catch (e) { continue; }
                if (typeof fn !== 'function' || fn.length !== 1) continue;

                if (!sigFuncName) {
                    try {
                        var sr = fn(SIG_TEST);
                        if (looksLikeSigResult(SIG_TEST, sr)) {
                            sigCandidates++;
                            window._cipherSigFunc = fn;
                            sigFuncName = key;
                            CipherBridge.logDebug("Sig function discovered via brute force: " + key + " -> " + String(sr).substring(0, 30));
                        }
                    } catch (e) { /* expected - most window props throw when called with this arg */ }
                }
                if (!nFuncName) {
                    try {
                        var nr = fn(N_TEST);
                        if (looksLikeNResult(N_TEST, nr)) {
                            nCandidates++;
                            window._nTransformFunc = fn;
                            nFuncName = key;
                            CipherBridge.logDebug("N-function discovered via brute force: " + key + " -> " + String(nr).substring(0, 30));
                        }
                    } catch (e) { /* expected */ }
                }
                if (sigFuncName && nFuncName) break;
            }

            info = "brute_force:sigCandidates=" + sigCandidates + ",nCandidates=" + nCandidates + ",total=" + keys.length;
        } catch(e) {
            info = "brute_force_error:" + e;
            CipherBridge.logDebug("Brute force failed: " + e);
        }
    }

    CipherBridge.logDebug("Discovery complete:");
    CipherBridge.logDebug("  sigFuncName=" + sigFuncName);
    CipherBridge.logDebug("  nFuncName=" + nFuncName);
    CipherBridge.logDebug("  info=" + info + " " + nInfo);

    CipherBridge.onDiscoveryDone(sigFuncName, nFuncName, info + " " + nInfo);
    CipherBridge.onPlayerJsLoaded();
}
</script>
<script src="player.js"
    onload="discoverAndInit()"
    onerror="CipherBridge.onPlayerJsError('Failed to load player.js from file')">
</script>
</head><body></body></html>"""

    // ==================== JAVASCRIPT INTERFACE ====================

    @JavascriptInterface
    fun logDebug(message: String) {
        Timber.tag(TAG).d("JS: $message")
    }

    @JavascriptInterface
    fun onDiscoveryDone(sigFuncName: String, nFuncName: String, info: String) {
        Timber.tag(TAG).d("=== DISCOVERY COMPLETE ===")
        Timber.tag(TAG).d("Sig function: ${sigFuncName.ifEmpty { "NOT FOUND" }}")
        Timber.tag(TAG).d("N function: ${nFuncName.ifEmpty { "NOT FOUND" }}")
        Timber.tag(TAG).d("Info: $info")

        sigFunctionAvailable = sigFuncName.isNotEmpty()
        if (nFuncName.isNotEmpty()) {
            discoveredNFuncName = nFuncName
            nFunctionAvailable = true
            Timber.tag(TAG).d("N-function AVAILABLE: $nFuncName")
        } else {
            Timber.tag(TAG).e("N-function NOT AVAILABLE")
            nFunctionAvailable = false
        }
    }

    @JavascriptInterface
    fun onNDiscoveryDone(funcName: String, info: String) {
        // Legacy interface - redirects to new combined discovery
        Timber.tag(TAG).d("Legacy onNDiscoveryDone: funcName=$funcName, info=$info")
        if (funcName.isNotEmpty()) {
            discoveredNFuncName = funcName
            nFunctionAvailable = true
        }
    }

    @JavascriptInterface
    fun onPlayerJsLoaded() {
        Timber.tag(TAG).d("=== PLAYER.JS LOAD COMPLETE ===")
        Timber.tag(TAG).d("sigFunctionAvailable=$sigFunctionAvailable")
        Timber.tag(TAG).d("nFunctionAvailable=$nFunctionAvailable")
        Timber.tag(TAG).d("discoveredNFuncName=$discoveredNFuncName")
        Timber.tag(TAG).d("usingHardcodedMode=$usingHardcodedMode")

        takeInitContinuation()?.resumeSafely { it.resume(this) }
    }

    @JavascriptInterface
    fun onPlayerJsError(error: String) {
        Timber.tag(TAG).e("=== PLAYER.JS LOAD FAILED ===")
        Timber.tag(TAG).e("Error: $error")
        takeInitContinuation()?.resumeSafely {
            it.resumeWithException(CipherException("Player JS load failed: $error"))
        }
    }

    // ==================== SIGNATURE DEOBFUSCATION ====================

    suspend fun deobfuscateSignature(obfuscatedSig: String): String {
        Timber.tag(TAG).d("========== DEOBFUSCATE SIGNATURE ==========")
        Timber.tag(TAG).d("Input sig length: ${obfuscatedSig.length}")
        Timber.tag(TAG).d("Input sig preview: ${obfuscatedSig.take(50)}...")
        Timber.tag(TAG).d("sigInfo: name=${sigInfo?.name}, constantArg=${sigInfo?.constantArg}")
        Timber.tag(TAG).d("sigFunctionAvailable (static or brute-force discovered): $sigFunctionAvailable")

        // sigInfo being null no longer means failure: the WebView-side discovery in
        // discoverAndInit() may have found and validated a signature function via runtime
        // brute-force even when static regex extraction found nothing. sigFunctionAvailable is
        // the authoritative signal — it reflects window._cipherSigFunc actually existing AND
        // having passed the character-rearrangement validation, regardless of how it got there.
        if (!sigFunctionAvailable) {
            Timber.tag(TAG).e("Signature function not available (static extraction and brute-force both failed)")
            throw CipherException("Signature function info not available")
        }

        throwIfDead()
        return runCatching {
            withTimeout(EVAL_TIMEOUT_MS) {
                withContext(Dispatchers.Main) {
                    suspendCancellableCoroutine { cont ->
                        sigContinuation = cont
                        val constArgJs = if (sigInfo?.constantArg != null) "${sigInfo.constantArg}" else "null"
                        val funcNameForLog = sigInfo?.name?.takeIf { it.isNotEmpty() } ?: "brute_force_sig"
                        val jsCall = "deobfuscateSig('$funcNameForLog', $constArgJs, '${escapeJsString(obfuscatedSig)}')"
                        Timber.tag(TAG).d("Evaluating JS: ${jsCall.take(100)}...")
                        webView.evaluateJavascript(jsCall, null)
                    }
                }
            }
        }.getOrElse { e ->
            when (e) {
                is TimeoutCancellationException -> {
                    Timber.tag(TAG).e("Sig deobfuscation timed out after ${EVAL_TIMEOUT_MS}ms")
                    failAsTimeout("Sig deobfuscation timed out after ${EVAL_TIMEOUT_MS}ms")
                }
                is CancellationException -> throw e
                else -> {
                    Timber.tag(TAG).e(e, "Sig deobfuscation failed unexpectedly")
                    throw CipherException("Sig deobfuscation failed: ${e.message}")
                }
            }
        }
    }

    @JavascriptInterface
    fun onSigResult(result: String) {
        Timber.tag(TAG).d("========== SIGNATURE RESULT ==========")
        Timber.tag(TAG).d("Result length: ${result.length}")
        Timber.tag(TAG).d("Result preview: ${result.take(50)}...")
        runCatching { takeSigContinuation()?.resume(result) }
    }

    @JavascriptInterface
    fun onSigError(error: String) {
        Timber.tag(TAG).e("========== SIGNATURE ERROR ==========")
        Timber.tag(TAG).e("Error: ${error.take(200)}") // Truncate to prevent log spam
        runCatching { takeSigContinuation()?.resumeWithException(CipherException("Sig deobfuscation failed")) }
    }

    // ==================== N-TRANSFORM ====================

    suspend fun transformN(nValue: String): String {
        Timber.tag(TAG).d("========== N-TRANSFORM ==========")
        Timber.tag(TAG).d("Input n value: $nValue")
        Timber.tag(TAG).d("nFunctionAvailable: $nFunctionAvailable")
        Timber.tag(TAG).d("discoveredNFuncName: $discoveredNFuncName")

        if (!nFunctionAvailable) {
            Timber.tag(TAG).e("N-transform function not discovered")
            throw CipherException("N-transform function not discovered")
        }

        throwIfDead()
        return runCatching {
            withTimeout(EVAL_TIMEOUT_MS) {
                withContext(Dispatchers.Main) {
                    suspendCancellableCoroutine { cont ->
                        nContinuation = cont
                        val jsCall = "transformN('${escapeJsString(nValue)}')"
                        Timber.tag(TAG).d("Evaluating JS: $jsCall")
                        webView.evaluateJavascript(jsCall, null)
                    }
                }
            }
        }.getOrElse { e ->
            when (e) {
                is TimeoutCancellationException -> {
                    Timber.tag(TAG).e("N-transform timed out after ${EVAL_TIMEOUT_MS}ms")
                    failAsTimeout("N-transform timed out after ${EVAL_TIMEOUT_MS}ms")
                }
                is CancellationException -> throw e
                else -> {
                    Timber.tag(TAG).e(e, "N-transform failed unexpectedly")
                    throw CipherException("N-transform failed: ${e.message}")
                }
            }
        }
    }

    @JavascriptInterface
    fun onNResult(result: String) {
        Timber.tag(TAG).d("========== N-TRANSFORM RESULT ==========")
        Timber.tag(TAG).d("Result length: ${result.length}")
        runCatching { takeNContinuation()?.resume(result) }
    }

    @JavascriptInterface
    fun onNError(error: String) {
        Timber.tag(TAG).e("========== N-TRANSFORM ERROR ==========")
        Timber.tag(TAG).e("Error: ${error.take(200)}") // Truncate to prevent log spam
        runCatching { takeNContinuation()?.resumeWithException(CipherException("N-transform failed")) }
    }

    // ==================== CLEANUP ====================

    fun close() {
        destroyWebView()
        Timber.tag(TAG).d("CipherWebView closed")
    }

    private fun destroyWebView() {
        if (destroyed) return
        destroyed = true
        runCatching {
            webView.clearHistory()
            webView.clearCache(true)
            webView.loadUrl("about:blank")
            webView.onPause()
            webView.removeAllViews()
            webView.destroy()
        }.onFailure { Timber.tag(TAG).w("WebView teardown threw: $it") }
    }

    // ==================== UTILITIES ====================

    private fun escapeJsString(s: String): String {
        return s.replace("\\", "\\\\")
            .replace("'", "\\'")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
    }

    companion object {
        private const val TAG = "Metrolist_CipherWebView"
        private const val JS_INTERFACE = "CipherBridge"

        // Cold-start (WebView spin-up + ~2.8 MB player.js parse + function discovery) is a few
        // seconds on a healthy device; 30s is generous slack before we treat init as a wedged
        // renderer. Per-eval calls (sig/n) are sub-second, so 15s is a comfortable ceiling.
        private const val CREATE_TIMEOUT_MS = 30_000L
        private const val EVAL_TIMEOUT_MS = 15_000L

        suspend fun create(
            context: Context,
            playerJs: String,
            sigInfo: FunctionNameExtractor.SigFunctionInfo?,
            nFuncInfo: FunctionNameExtractor.NFunctionInfo? = null,
        ): CipherWebView {
            Timber.tag(TAG).d("=== CREATING CIPHER WEBVIEW ===")
            Timber.tag(TAG).d("playerJs size: ${playerJs.length} chars")
            Timber.tag(TAG).d("sigInfo: $sigInfo")
            Timber.tag(TAG).d("nFuncInfo: $nFuncInfo")

            var created: CipherWebView? = null
            return runCatching {
                withTimeout(CREATE_TIMEOUT_MS) {
                    withContext(Dispatchers.Main) {
                        suspendCancellableCoroutine { cont ->
                            val wv = CipherWebView(context, playerJs, sigInfo, nFuncInfo, cont)
                            created = wv
                            wv.loadPlayerJsFromFile()
                        }
                    }
                }
            }.getOrElse { e ->
                when (e) {
                    is TimeoutCancellationException -> {
                        Timber.tag(TAG).e("CipherWebView init timed out after ${CREATE_TIMEOUT_MS}ms")
                        destroyQuietly(created)
                        throw CipherTimeoutException("CipherWebView init timed out")
                    }
                    is CancellationException -> {
                        destroyQuietly(created)
                        throw e
                    }
                    else -> {
                        Timber.tag(TAG).e(e, "CipherWebView creation failed unexpectedly")
                        destroyQuietly(created)
                        throw CipherException("CipherWebView creation failed: ${e.message}")
                    }
                }
            }
        }

        private suspend fun destroyQuietly(wv: CipherWebView?) {
            if (wv == null) return
            withContext(NonCancellable + Dispatchers.Main) {
                wv.isDead = true
                wv.takeInitContinuation()
                wv.destroyWebView()
            }
        }
    }
}

class CipherException(message: String) : Exception(message)
class CipherRendererGoneException(message: String) : Exception(message)

/**
 * A cipher WebView create/eval that never called back within its timeout. The renderer is WEDGED
 * (slow/busy CPU), NOT proven dead: the orchestrator drops the WebView and rebuilds it on the next
 * attempt, but — unlike [CipherRendererGoneException] — a timeout must NOT count toward the
 * renderer-death backoff, so a slow first-play on a low-end box can't lock streaming out of the
 * cipher WebView path.
 */
class CipherTimeoutException(message: String) : Exception(message)
