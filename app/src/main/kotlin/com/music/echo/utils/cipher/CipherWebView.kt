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
 * Refactorizado defensivamente: Control estricto de excepciones y mitigación de fugas/spam.
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
        // Reducimos el nivel de verbosidad para evitar saturar el Logcat
        Timber.tag(TAG).d("Iniciando CipherWebView seguro...")

        runCatching {
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
                    val didCrash = runCatching { detail.didCrash() }.getOrDefault(true)
                    // Mitigamos el spam de error a solo warning
                    Timber.tag(TAG).w("Renderizador WebView finalizado (crashed=$didCrash)")
                    onRendererGone("WebView render process gone")
                    return true // True indica que hemos manejado el error
                }
            }

            webView.webChromeClient = object : WebChromeClient() {
                override fun onConsoleMessage(m: ConsoleMessage): Boolean {
                    // SILENCIO TOTAL AL LOGCAT: Evita el spam infinito generado por player.js
                    // Consumimos el mensaje devolviendo true, pero no lo imprimimos.
                    return true
                }
            }
        }.onFailure { e ->
            Timber.tag(TAG).w("Error no fatal al configurar WebView: ${e.message}")
        }
    }

    private fun onRendererGone(reason: String) {
        isDead = true
        val e = CipherRendererGoneException(reason)
        takeInitContinuation()?.resumeSafely { it.resumeWithException(e) }
        takeSigContinuation()?.resumeSafely { it.resumeWithException(e) }
        takeNContinuation()?.resumeSafely { it.resumeWithException(e) }
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

    private fun loadPlayerJsFromFile() {
        runCatching {
            val sigFuncName = sigInfo?.name
            val nFuncName = nFuncInfo?.name
            val nArrayIdx = nFuncInfo?.arrayIndex
            val isHardcoded = sigInfo?.isHardcoded == true || nFuncInfo?.isHardcoded == true

            usingHardcodedMode = isHardcoded

            val exports = buildList {
                val sigJsExpr = sigInfo?.jsExpression
                if (sigJsExpr != null) {
                    val expr = sigJsExpr.replace("INPUT", "sig")
                    add("window._cipherSigFunc = function(sig) { try { return $expr; } catch(e) { return null; } };")
                } else if (sigFuncName != null) {
                    val sigConstArgs = sigInfo.constantArgs
                    val preprocessFunc = sigInfo.preprocessFunc
                    val preprocessArgs = sigInfo.preprocessArgs

                    if (!sigConstArgs.isNullOrEmpty() && preprocessFunc != null && !preprocessArgs.isNullOrEmpty()) {
                        val mainArgsStr = sigConstArgs.joinToString(", ")
                        val prepArgsStr = preprocessArgs.joinToString(", ")
                        add("window._cipherSigFunc = function(sig) { return $sigFuncName($mainArgsStr, $preprocessFunc($prepArgsStr, sig)); };")
                    } else if (!sigConstArgs.isNullOrEmpty()) {
                        val argsStr = sigConstArgs.joinToString(", ")
                        add("window._cipherSigFunc = function(sig) { return $sigFuncName($argsStr, sig); };")
                    } else {
                        add("window._cipherSigFunc = typeof $sigFuncName !== 'undefined' ? $sigFuncName : null;")
                    }
                }
                val nJsExpr = nFuncInfo?.jsExpression
                if (nJsExpr != null) {
                    val expr = nJsExpr.replace("INPUT", "n")
                    add("window._nTransformFunc = function(n) { try { return $expr; } catch(e) { return n; } };")
                } else if (nFuncName != null) {
                    val nConstArgs = nFuncInfo.constantArgs
                    if (!nConstArgs.isNullOrEmpty()) {
                        val argsStr = nConstArgs.joinToString(", ")
                        add("window._nTransformFunc = function(n) { return $nFuncName($argsStr, n); };")
                    } else {
                        val nExpr = if (nArrayIdx != null) "$nFuncName[$nArrayIdx]" else nFuncName
                        add("window._nTransformFunc = typeof $nFuncName !== 'undefined' ? $nExpr : null;")
                    }
                }
            }

            val modifiedJs = if (exports.isNotEmpty()) {
                val exportCode = "; " + exports.joinToString(" ")
                val lastIifeEnd = playerJs.lastIndexOf("})(")
                val lastIifeCallEnd = playerJs.lastIndexOf("}).call(")
                val injectionIndex = maxOf(lastIifeEnd, lastIifeCallEnd)
                
                if (injectionIndex != -1) {
                    playerJs.substring(0, injectionIndex) + exportCode + playerJs.substring(injectionIndex)
                } else {
                    playerJs + "\n" + exportCode
                }
            } else {
                playerJs
            }

            val cacheDir = File(webView.context.cacheDir, "cipher")
            cacheDir.mkdirs()
            val playerJsFile = File(cacheDir, "player.js")
            playerJsFile.writeText(modifiedJs)

            val html = buildDiscoveryHtml()
            webView.loadDataWithBaseURL(
                "file://${cacheDir.absolutePath}/",
                html, "text/html", "utf-8", null
            )
        }.onFailure {
            // Falla segura: Informar pero no bloquear
            Timber.tag(TAG).w("Error silencioso al procesar/escribir JS: ${it.message}")
            takeInitContinuation()?.resumeSafely { cont -> 
                cont.resumeWithException(CipherException("Fallo en inicializacion JS silencioso"))
            }
        }
    }

    private fun buildDiscoveryHtml(): String = """<!DOCTYPE html>
<html><head><script>
function deobfuscateSig(funcName, constantArg, obfuscatedSig) {
    try {
        var func = window._cipherSigFunc;
        if (typeof func !== 'function') { CipherBridge.onSigError("Func missing"); return; }
        var result;
        if (func.length === 1) { result = func(obfuscatedSig); }
        else if (constantArg !== null && constantArg !== undefined) { result = func(constantArg, obfuscatedSig); }
        else { result = func(obfuscatedSig); }
        
        if (result == null) { CipherBridge.onSigError("Result null"); return; }
        CipherBridge.onSigResult(String(result));
    } catch (error) { CipherBridge.onSigError(String(error)); }
}

function transformN(nValue) {
    try {
        var func = window._nTransformFunc;
        if (typeof func !== 'function') { CipherBridge.onNError("Func missing"); return; }
        var result = func(nValue);
        if (result == null) { CipherBridge.onNError("Result null"); return; }
        CipherBridge.onNResult(String(result));
    } catch (error) { CipherBridge.onNError(String(error)); }
}

function discoverAndInit() {
    var nFuncName = ""; var sigFuncName = ""; var info = "";
    if (typeof window._cipherSigFunc === 'function') { sigFuncName = "exported_sig_func"; }
    
    if (typeof window._nTransformFunc === 'function') {
        try {
            var testInput = "KdrqFlzJXl9EcCwlmEy";
            var testResult = window._nTransformFunc(testInput);
            if (typeof testResult === 'string' && testResult !== testInput && testResult.length >= 5) {
                if (/^[a-zA-Z0-9_-]+$/.test(testResult)) {
                    nFuncName = "exported_n_func"; info = "export_valid";
                } else { window._nTransformFunc = null; }
            } else { window._nTransformFunc = null; }
        } catch(e) { window._nTransformFunc = null; }
    }

    if (!nFuncName) {
        try {
            var testInput = "T2Xw3pWQ_Wk0xbOg";
            var keys = Object.getOwnPropertyNames(window);
            for (var i = 0; i < keys.length; i++) {
                try {
                    var key = keys[i];
                    if (key.startsWith("webkit") || key.startsWith("on") || key === "CipherBridge" || key === "window") continue;
                    var fn = window[key];
                    if (typeof fn !== 'function' || fn.length !== 1) continue;
                    var result = fn(testInput);
                    if (typeof result === 'string' && result !== testInput && result.length >= 5 && /^[a-zA-Z0-9_-]+$/.test(result)) {
                        if (!nFuncName) { window._nTransformFunc = fn; nFuncName = key; }
                    }
                } catch(e) {}
            }
        } catch(e) {}
    }
    CipherBridge.onDiscoveryDone(sigFuncName, nFuncName, info);
    CipherBridge.onPlayerJsLoaded();
}
</script>
<script src="player.js" onload="discoverAndInit()" onerror="CipherBridge.onPlayerJsError('Failed load')"></script>
</head><body></body></html>"""

    @JavascriptInterface
    fun logDebug(message: String) { /* Silenciado intencionalmente */ }

    @JavascriptInterface
    fun onDiscoveryDone(sigFuncName: String, nFuncName: String, info: String) {
        sigFunctionAvailable = sigFuncName.isNotEmpty()
        if (nFuncName.isNotEmpty()) {
            discoveredNFuncName = nFuncName
            nFunctionAvailable = true
        } else {
            nFunctionAvailable = false
        }
    }

    @JavascriptInterface
    fun onNDiscoveryDone(funcName: String, info: String) {
        if (funcName.isNotEmpty()) {
            discoveredNFuncName = funcName
            nFunctionAvailable = true
        }
    }

    @JavascriptInterface
    fun onPlayerJsLoaded() {
        takeInitContinuation()?.resumeSafely { it.resume(this) }
    }

    @JavascriptInterface
    fun onPlayerJsError(error: String) {
        takeInitContinuation()?.resumeSafely {
            it.resumeWithException(CipherException("Player JS load failed: $error"))
        }
    }

    // ==================== SIGNATURE DEOBFUSCATION CON FALLBACK ====================

    suspend fun deobfuscateSignature(obfuscatedSig: String): String {
        if (sigInfo == null || isDead) return obfuscatedSig // Fallback: retornar string original sin crashear

        return runCatching {
            withTimeout(EVAL_TIMEOUT_MS) {
                withContext(Dispatchers.Main) {
                    suspendCancellableCoroutine { cont ->
                        sigContinuation = cont
                        val constArgJs = if (sigInfo.constantArg != null) "${sigInfo.constantArg}" else "null"
                        val jsCall = "deobfuscateSig('${sigInfo.name}', $constArgJs, '${escapeJsString(obfuscatedSig)}')"
                        webView.evaluateJavascript(jsCall, null)
                    }
                }
            }
        }.onFailure { e ->
            // Propagar cancelaciones del sistema, atrapar todo lo demas silenciosamente
            if (e is CancellationException && e !is TimeoutCancellationException) throw e
        }.getOrDefault(obfuscatedSig) // FALLBACK SEGURO
    }

    @JavascriptInterface
    fun onSigResult(result: String) {
        takeSigContinuation()?.resumeSafely { it.resume(result) }
    }

    @JavascriptInterface
    fun onSigError(error: String) {
        takeSigContinuation()?.resumeSafely { it.resumeWithException(CipherException("Sig error")) }
    }

    // ==================== N-TRANSFORM CON FALLBACK ====================

    suspend fun transformN(nValue: String): String {
        if (!nFunctionAvailable || isDead) return nValue // Fallback: retornar N original

        return runCatching {
            withTimeout(EVAL_TIMEOUT_MS) {
                withContext(Dispatchers.Main) {
                    suspendCancellableCoroutine { cont ->
                        nContinuation = cont
                        val jsCall = "transformN('${escapeJsString(nValue)}')"
                        webView.evaluateJavascript(jsCall, null)
                    }
                }
            }
        }.onFailure { e ->
            if (e is CancellationException && e !is TimeoutCancellationException) throw e
        }.getOrDefault(nValue) // FALLBACK SEGURO
    }

    @JavascriptInterface
    fun onNResult(result: String) {
        takeNContinuation()?.resumeSafely { it.resume(result) }
    }

    @JavascriptInterface
    fun onNError(error: String) {
        takeNContinuation()?.resumeSafely { it.resumeWithException(CipherException("N error")) }
    }

    // ==================== CLEANUP ====================

    fun close() {
        destroyWebView()
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
        } // Silenciado el log de fallo al destruir
    }

    private fun escapeJsString(s: String): String {
        return s.replace("\\", "\\\\").replace("'", "\\'")
            .replace("\"", "\\\"").replace("\n", "\\n")
            .replace("\r", "\\r").replace("\t", "\\t")
    }

    companion object {
        private const val TAG = "Metrolist_CipherWebView"
        private const val JS_INTERFACE = "CipherBridge"
        private const val CREATE_TIMEOUT_MS = 30_000L
        private const val EVAL_TIMEOUT_MS = 15_000L

        suspend fun create(
            context: Context,
            playerJs: String,
            sigInfo: FunctionNameExtractor.SigFunctionInfo?,
            nFuncInfo: FunctionNameExtractor.NFunctionInfo? = null,
        ): CipherWebView {
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
            }.onFailure { e ->
                destroyQuietly(created)
                if (e is CancellationException && e !is TimeoutCancellationException) throw e
                // Si falla (ej. por timeout), lanzamos un error que el orquestador capturará limpiamente
                throw CipherTimeoutException("CipherWebView safe abort: ${e.message}")
            }.getOrThrow()
        }

        private suspend fun destroyQuietly(wv: CipherWebView?) {
            if (wv == null) return
            runCatching {
                withContext(NonCancellable + Dispatchers.Main) {
                    wv.isDead = true
                    wv.takeInitContinuation()
                    wv.destroyWebView()
                }
            }
        }
    }
}

class CipherException(message: String) : Exception(message)
class CipherRendererGoneException(message: String) : Exception(message)
class CipherTimeoutException(message: String) : Exception(message)
