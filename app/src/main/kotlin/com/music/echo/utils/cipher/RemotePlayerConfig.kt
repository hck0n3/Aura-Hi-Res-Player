package iad1tya.echo.music.utils.cipher

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Self-healing remote player-config for the YouTube cipher.
 *
 * WHY: [FunctionNameExtractor.KNOWN_PLAYER_CONFIGS] is a HARDCODED map of player.js hash -> cipher
 * config. When YouTube rotates player.js to a brand-new hash that isn't in that map, signature/n-transform
 * extraction fails and streaming breaks — historically fixed only by shipping an app update. This object
 * lets the owner publish NEW configs for new hashes at runtime, so the app "heals" WITHOUT an update.
 *
 * SAFETY / SCOPE:
 *  - This is a SECOND, AUGMENT-ONLY source. It is consulted ONLY on a hardcoded miss (see the hook in
 *    [FunctionNameExtractor.getHardcodedConfig]); the hardcoded entries always take priority and their
 *    resolution path is completely unchanged.
 *  - Everything here is best-effort: a failed fetch, an offline launch, or malformed JSON leaves the app
 *    EXACTLY as it was (no throw, no crash, empty remote map = current behaviour).
 *  - It REUSES [FunctionNameExtractor.HardcodedPlayerConfig] — the config type is never redefined, so a
 *    remote entry is used byte-for-byte as if it had been hardcoded.
 *
 * ENTRY POINTS (the parent wires these; do NOT call from here):
 *  - [loadCache] — synchronous, cheap: populates the in-memory map from the on-disk cache so a resolution
 *    works immediately, even offline, on the very next extraction. Call once at startup.
 *  - [refresh]   — suspend, network: fetches the latest configs, merges them in, and rewrites the cache.
 *    Idempotent and throttled (a repeated call within [MIN_REFRESH_INTERVAL_MS] no-ops).
 *
 * ─────────────────────────────────────────────────────────────────────────────────────────────────────
 * JSON SCHEMA the owner publishes at [REMOTE_CONFIG_URL]
 * ─────────────────────────────────────────────────────────────────────────────────────────────────────
 * Either a bare array, or an object with a "configs" array. Each entry mirrors a HardcodedPlayerConfig
 * plus the player.js "hash" it is keyed by. Absent/null optional fields are treated as null.
 *
 *   [
 *     {
 *       "hash": "ecd4b80a",              // REQUIRED: 8-hex player.js hash (as extracted by the app)
 *       "signatureTimestamp": 20613,     // REQUIRED: the player's signatureTimestamp / sts
 *
 *       // ── Expression-based form (the 2026 VM-dispatch players; PREFERRED) ──
 *       "sigFuncName": "_expr_sig",      // sentinel name used with an expression
 *       "sigJsExpression": "mP(4,155,INPUT)",
 *       "nFuncName": "_expr_n",          // sentinel name used with an expression
 *       "nJsExpression": "(function(n){try{var u=new g.Yx('https://x.googlevideo.com/videoplayback?n='+n,true);var t=u.get('n');return(t&&t!==n)?t:n;}catch(e){return n;}})(INPUT)",
 *
 *       // ── Legacy name/arg form (only if NOT using expressions; leave null otherwise) ──
 *       "sigConstantArg": null,          // Int  or null  (legacy single arg, e.g. 48)
 *       "sigConstantArgs": null,         // [Int] or null (e.g. [48, 1918])
 *       "sigPreprocessFunc": null,       // String or null (e.g. "f1")
 *       "sigPreprocessArgs": null,       // [Int] or null (e.g. [1, 6528])
 *       "nArrayIndex": null,             // Int  or null
 *       "nConstantArgs": null            // [Int] or null (e.g. [6, 6010])
 *     }
 *   ]
 *
 * NOTE: publish VERIFIED functions only. Do not ship placeholder/example values — they would be used
 * exactly as hardcoded and could break playback for the rotated hash.
 */
object RemotePlayerConfig {

    private const val TAG = "Metrolist_RemotePlayerCfg"

    /**
     * URL of the published config JSON. The OWNER controls this file (host it wherever you like — a raw
     * GitHub file, a gist, an object-store URL, …). Point it at your own published `player_configs.json`.
     */
    const val REMOTE_CONFIG_URL =
        "https://raw.githubusercontent.com/hck0n3/Aura-Hi-Res-Player/main/player_configs.json"

    private const val CACHE_FILE_NAME = "player_configs_cache.json"

    // Guard against pathological downloads: a real config file is a few KB.
    private const val MAX_BODY_BYTES = 512 * 1024

    private const val CONNECT_TIMEOUT_MS = 8_000
    private const val READ_TIMEOUT_MS = 8_000

    // Idempotent/cheap: a successful refresh is not repeated within this window (per process).
    private const val MIN_REFRESH_INTERVAL_MS = 60L * 60L * 1_000L // 1 hour

    // Immutable map behind a volatile reference => lock-free, thread-safe reads from configFor().
    // Writes replace the whole reference atomically (last-writer-wins), so readers never see a torn map.
    @Volatile
    private var remoteConfigs: Map<String, FunctionNameExtractor.HardcodedPlayerConfig> = emptyMap()

    @Volatile
    private var lastRefreshAtMs: Long = 0L

    @Volatile
    private var refreshing: Boolean = false

    /**
     * The merged (remote + cached) config for [hash], or null if none is known remotely.
     * Thread-safe, allocation-free, and side-effect-free — safe to call from the extraction path.
     */
    fun configFor(hash: String): FunctionNameExtractor.HardcodedPlayerConfig? =
        remoteConfigs[hash.trim().lowercase()]

    /**
     * Populate the in-memory map from the on-disk cache. Synchronous and cheap (a small local file), so a
     * cached config is available for the very first extraction, even with no network. Best-effort: any
     * failure leaves the current (possibly empty) map untouched. Call once at startup, ideally off the
     * main thread.
     */
    fun loadCache(context: Context) {
        try {
            val file = cacheFile(context)
            if (!file.exists()) return
            val body = file.readText()
            val parsed = parseConfigs(body)
            if (parsed.isNotEmpty()) {
                remoteConfigs = parsed
                Timber.tag(TAG).d("Loaded ${parsed.size} cached remote config(s): ${parsed.keys.joinToString()}")
            }
        } catch (e: Exception) {
            // Offline-safe: a missing/corrupt cache must never affect startup.
            Timber.tag(TAG).w(e, "loadCache failed (ignored)")
        }
    }

    /**
     * Fetch the latest configs from [REMOTE_CONFIG_URL], merge them in, and rewrite the cache. Runs on
     * [Dispatchers.IO], never throws, and is a no-op if a successful refresh already happened within
     * [MIN_REFRESH_INTERVAL_MS]. A failed/empty/malformed fetch keeps whatever was already loaded.
     */
    suspend fun refresh(context: Context) = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        // Cheap/idempotent: skip if we already have configs and refreshed recently, or if one is in flight.
        if (refreshing) return@withContext
        if (remoteConfigs.isNotEmpty() && now - lastRefreshAtMs < MIN_REFRESH_INTERVAL_MS) return@withContext

        refreshing = true
        try {
            val body = httpGet(REMOTE_CONFIG_URL)
            if (body == null) {
                Timber.tag(TAG).d("refresh: no body (offline or non-2xx); keeping existing configs")
                return@withContext
            }
            val parsed = parseConfigs(body)
            if (parsed.isEmpty()) {
                Timber.tag(TAG).d("refresh: parsed 0 valid entries; keeping existing configs")
                return@withContext
            }
            // Freshly published set is the source of truth for the remote layer.
            remoteConfigs = parsed
            lastRefreshAtMs = now
            runCatching { cacheFile(context).writeText(body) }
                .onFailure { Timber.tag(TAG).w(it, "refresh: cache write failed (ignored)") }
            Timber.tag(TAG).d("refresh: applied ${parsed.size} remote config(s): ${parsed.keys.joinToString()}")
        } catch (e: Exception) {
            // Best-effort: any network/parse error leaves the app exactly as-is.
            Timber.tag(TAG).w(e, "refresh failed (ignored)")
        } finally {
            refreshing = false
        }
    }

    // ==================== INTERNAL ====================

    private fun cacheFile(context: Context): File = File(context.filesDir, CACHE_FILE_NAME)

    /** Best-effort GET. Returns the body (capped) on 2xx, else null. Never throws. */
    private fun httpGet(urlStr: String): String? {
        var conn: HttpURLConnection? = null
        return try {
            conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                requestMethod = "GET"
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", "Aura-Hi-Res-Player")
                setRequestProperty("Accept", "application/json")
            }
            val code = conn.responseCode
            if (code !in 200..299) return null
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            if (body.length > MAX_BODY_BYTES) null else body
        } catch (e: Exception) {
            null
        } finally {
            runCatching { conn?.disconnect() }
        }
    }

    /**
     * Parse the published JSON (bare array, or `{ "configs": [...] }`) into hash -> config. Skips any
     * malformed entry; the whole call returns emptyMap() on any structural error. Uses the SAME schema
     * for the remote body and the on-disk cache.
     */
    private fun parseConfigs(json: String): Map<String, FunctionNameExtractor.HardcodedPlayerConfig> {
        val arr: JSONArray = runCatching {
            val trimmed = json.trim()
            if (trimmed.startsWith("[")) JSONArray(trimmed)
            else JSONObject(trimmed).optJSONArray("configs") ?: JSONArray()
        }.getOrNull() ?: return emptyMap()

        val out = LinkedHashMap<String, FunctionNameExtractor.HardcodedPlayerConfig>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val hash = o.optString("hash").trim().lowercase()
            if (hash.isBlank()) continue
            out[hash] = FunctionNameExtractor.HardcodedPlayerConfig(
                sigFuncName = strOrEmpty(o, "sigFuncName"),
                sigConstantArg = intOrNull(o, "sigConstantArg"),
                sigConstantArgs = intListOrNull(o, "sigConstantArgs"),
                sigPreprocessFunc = strOrNull(o, "sigPreprocessFunc"),
                sigPreprocessArgs = intListOrNull(o, "sigPreprocessArgs"),
                sigJsExpression = strOrNull(o, "sigJsExpression"),
                nFuncName = strOrEmpty(o, "nFuncName"),
                nArrayIndex = intOrNull(o, "nArrayIndex"),
                nConstantArgs = intListOrNull(o, "nConstantArgs"),
                nJsExpression = strOrNull(o, "nJsExpression"),
                signatureTimestamp = o.optInt("signatureTimestamp", 0),
            )
        }
        return out
    }

    private fun strOrEmpty(o: JSONObject, key: String): String =
        if (o.has(key) && !o.isNull(key)) o.optString(key) else ""

    private fun strOrNull(o: JSONObject, key: String): String? =
        if (o.has(key) && !o.isNull(key)) o.optString(key).takeIf { it.isNotBlank() } else null

    private fun intOrNull(o: JSONObject, key: String): Int? =
        if (o.has(key) && !o.isNull(key)) o.optInt(key) else null

    private fun intListOrNull(o: JSONObject, key: String): List<Int>? {
        if (!o.has(key) || o.isNull(key)) return null
        val a = o.optJSONArray(key) ?: return null
        val list = ArrayList<Int>(a.length())
        for (i in 0 until a.length()) list += a.optInt(i)
        return list
    }
}
