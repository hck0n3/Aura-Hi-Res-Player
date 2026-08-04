package iad1tya.echo.music.utils.cipher

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
 *  - [forceRefresh] — suspend, network: REACTIVE self-healing. Called by the cipher path when it hits a
 *    player hash with no config anywhere; bypasses the 1h throttle but is bounded by a 5-minute cooldown.
 *  - [configEpoch] — bumped whenever the applied config set changes; the cipher orchestrator uses it to
 *    rebuild its WebView so a fetched fix applies WITHOUT an app restart.
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
    private const val ETAG_FILE_NAME = "player_configs_cache.etag"

    // Guard against pathological downloads: a real config file is a few KB.
    private const val MAX_BODY_BYTES = 512 * 1024

    private const val CONNECT_TIMEOUT_MS = 8_000
    private const val READ_TIMEOUT_MS = 8_000

    // Idempotent/cheap: a successful refresh is not repeated within this window (per process).
    private const val MIN_REFRESH_INTERVAL_MS = 60L * 60L * 1_000L // 1 hour

    // Bound for the REACTIVE (failure-triggered) refetch: when YouTube rotates to a hash the owner
    // has not published a fix for yet, the extra network cost is at most one small GET per window.
    private const val FORCE_REFRESH_COOLDOWN_MS = 5L * 60L * 1_000L // 5 minutes

    // Immutable map behind a volatile reference => lock-free, thread-safe reads from configFor().
    // Writes replace the whole reference atomically (last-writer-wins), so readers never see a torn map.
    @Volatile
    private var remoteConfigs: Map<String, FunctionNameExtractor.HardcodedPlayerConfig> = emptyMap()

    @Volatile
    private var lastRefreshAtMs: Long = 0L

    @Volatile
    private var lastForcedAttemptMs: Long = 0L

    /**
     * Monotonic version of the APPLIED config set: bumped every time the in-memory map actually
     * changes (startup cache load, periodic refresh, or a reactive [forceRefresh]). The cipher
     * orchestrator records the epoch its WebView was built against and rebuilds when the epoch
     * moves, so a freshly published fix is applied WITHOUT restarting the app.
     */
    @Volatile
    var configEpoch: Int = 0
        private set

    // Serializes refresh/forceRefresh (fetch + apply + cache write): with the reactive path there
    // are now two real writers, and an unserialized cache write could publish a truncated file.
    private val refreshMutex = Mutex()

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
                applyConfigs(parsed)
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
        refreshMutex.withLock {
            // Cheap/idempotent: skip if we already have configs and refreshed recently. The lower
            // bound guards clock rollback: a NEGATIVE elapsed (wall clock moved back past the last
            // refresh) would otherwise count as "recent" and block refreshing until the clock
            // catches up — treat drift as stale and refetch instead.
            val sinceLast = System.currentTimeMillis() - lastRefreshAtMs
            if (remoteConfigs.isNotEmpty() && sinceLast in 0 until MIN_REFRESH_INTERVAL_MS) return@withLock
            fetchAndApply(context)
        }
    }

    /**
     * REACTIVE self-healing: called by the cipher path when stream resolution hits a player.js
     * [missingHash] that has NO config anywhere (pattern extraction failed + hardcoded miss +
     * remote miss) — i.e. YouTube rotated its player. Bypasses the 1-hour throttle so a fix the
     * owner just published is picked up IMMEDIATELY (today this only healed on app restart), but is
     * bounded by [FORCE_REFRESH_COOLDOWN_MS] so an unpublished hash costs at most one small GET per
     * window. Best-effort: never throws.
     *
     * @return true if a config for [missingHash] is available after the call.
     */
    suspend fun forceRefresh(context: Context, missingHash: String): Boolean = withContext(Dispatchers.IO) {
        val hash = missingHash.trim().lowercase()
        if (hash.isBlank()) return@withContext false
        refreshMutex.withLock {
            // The config may have arrived via a concurrent startup/periodic refresh while we waited.
            if (remoteConfigs.containsKey(hash)) return@withLock true

            // Same clock-drift discipline as refresh(): negative elapsed = cooldown expired.
            val sinceForced = System.currentTimeMillis() - lastForcedAttemptMs
            if (sinceForced in 0 until FORCE_REFRESH_COOLDOWN_MS) {
                Timber.tag(TAG).d("forceRefresh($hash) skipped (cooldown)")
                return@withLock false
            }
            lastForcedAttemptMs = System.currentTimeMillis()
            fetchAndApply(context)
            remoteConfigs.containsKey(hash)
        }
    }

    // ==================== SETTINGS-SCREEN SURFACE (additive, read/trigger only) ====================
    // These expose the ALREADY-EXISTING self-healing machinery to the "Descifrado de YouTube" settings
    // screen. They add NO new behaviour: manualRefresh() just runs the same fetch path on demand, and the
    // two getters only read state that already changes above. The cipher resolution path is untouched.

    /**
     * User-initiated manual refresh for the "Descifrado de YouTube" settings screen. Performs a real
     * conditional GET IMMEDIATELY, bypassing the periodic [MIN_REFRESH_INTERVAL_MS] throttle used by
     * [refresh] (this is an explicit, UI-rate-limited action), while keeping every other guarantee: it is
     * serialized by the same [refreshMutex], is best-effort, and NEVER throws. It does NOT touch the
     * reactive [forceRefresh] cooldown.
     *
     * @return true if the remote server was reached — a fresh config was applied OR a 304 confirmed the
     *   current set is already up to date; false on an offline/network failure (existing configs kept).
     */
    suspend fun manualRefresh(context: Context): Boolean = withContext(Dispatchers.IO) {
        refreshMutex.withLock {
            // fetchAndApply advances lastRefreshAtMs ONLY when the server was reached (a 2xx apply or a
            // 304), never on an offline/network error — so this before/after check is an accurate
            // "reached server" signal without changing fetchAndApply.
            val before = lastRefreshAtMs
            fetchAndApply(context)
            lastRefreshAtMs != before
        }
    }

    /** Wall-clock ms of the last successful remote contact this process (fresh fetch or 304), or 0 if none yet. */
    fun lastRefreshTimeMs(): Long = lastRefreshAtMs

    /** Number of self-healing player-config hashes currently applied (remote + cached); 0 if none yet. */
    fun knownHashCount(): Int = remoteConfigs.size

    // ==================== INTERNAL ====================

    /** Fetch + parse + apply + rewrite cache. Must be called with [refreshMutex] held. Never throws. */
    private fun fetchAndApply(context: Context) {
        try {
            // Conditional GET: send If-None-Match only when we actually hold configs a 304 would
            // preserve (guards the corner case of a persisted ETag with a lost/corrupt body cache).
            val etag = if (remoteConfigs.isNotEmpty()) readEtag(context) else null
            val result = httpGet(REMOTE_CONFIG_URL, etag)
            if (result == null) {
                Timber.tag(TAG).d("refresh: no body (offline or non-2xx); keeping existing configs")
                return
            }
            if (result.notModified) {
                lastRefreshAtMs = System.currentTimeMillis()
                Timber.tag(TAG).d("refresh: remote configs unchanged (304)")
                return
            }
            val body = result.body ?: return
            val parsed = parseConfigs(body)
            if (parsed.isEmpty()) {
                Timber.tag(TAG).d("refresh: parsed 0 valid entries; keeping existing configs")
                return
            }
            // Freshly published set is the source of truth for the remote layer.
            applyConfigs(parsed)
            lastRefreshAtMs = System.currentTimeMillis()
            runCatching {
                writeAtomic(cacheFile(context), body)
                writeEtag(context, result.etag)
            }.onFailure { Timber.tag(TAG).w(it, "refresh: cache write failed (ignored)") }
            Timber.tag(TAG).d("refresh: applied ${parsed.size} remote config(s) (epoch=$configEpoch): ${parsed.keys.joinToString()}")
        } catch (e: Exception) {
            // Best-effort: any network/parse error leaves the app exactly as-is.
            Timber.tag(TAG).w(e, "refresh failed (ignored)")
        }
    }

    /** Replace the in-memory map; bumps [configEpoch] only when the applied set actually changed. */
    private fun applyConfigs(parsed: Map<String, FunctionNameExtractor.HardcodedPlayerConfig>) {
        val changed = parsed != remoteConfigs
        remoteConfigs = parsed
        if (changed) configEpoch++
    }

    private fun cacheFile(context: Context): File = File(context.filesDir, CACHE_FILE_NAME)

    private fun etagFile(context: Context): File = File(context.filesDir, ETAG_FILE_NAME)

    private fun readEtag(context: Context): String? = runCatching {
        etagFile(context).takeIf { it.exists() }?.readText()?.trim()?.takeIf { it.isNotEmpty() }
    }.getOrNull()

    private fun writeEtag(context: Context, etag: String?) {
        runCatching {
            val file = etagFile(context)
            if (etag.isNullOrBlank()) file.delete() else writeAtomic(file, etag)
        }
    }

    /**
     * Temp-file + rename publish (rename within one directory is atomic on Android/Linux), with a
     * delete-and-retry fallback and a direct write as the last resort — a reader never sees a
     * half-written cache accepted as valid.
     */
    private fun writeAtomic(file: File, content: String) {
        val tmp = File(file.parentFile, "${file.name}.tmp")
        tmp.writeText(content)
        if (!tmp.renameTo(file)) {
            file.delete()
            if (!tmp.renameTo(file)) {
                file.writeText(content)
                tmp.delete()
            }
        }
    }

    private class FetchResult(val body: String?, val etag: String?, val notModified: Boolean)

    /**
     * Best-effort conditional GET. 2xx => body (capped) + response ETag; 304 => notModified; any
     * other code or error => null. Never throws.
     */
    private fun httpGet(urlStr: String, ifNoneMatch: String?): FetchResult? {
        var conn: HttpURLConnection? = null
        return try {
            conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                requestMethod = "GET"
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", "Aura-Hi-Res-Player")
                setRequestProperty("Accept", "application/json")
                if (!ifNoneMatch.isNullOrBlank()) setRequestProperty("If-None-Match", ifNoneMatch)
            }
            val code = conn.responseCode
            if (code == HttpURLConnection.HTTP_NOT_MODIFIED) {
                return FetchResult(body = null, etag = ifNoneMatch, notModified = true)
            }
            if (code !in 200..299) {
                // This is the SELF-HEALING channel: publishing one JSON is how a YouTube cipher
                // rotation gets fixed for every user without an app update. A silent failure here means
                // the fix never arrives and nobody ever finds out why the repair "didn't work".
                Timber.tag("RESOLVE_CIPHER").w("remote player config fetch HTTP $code")
                return null
            }
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            if (body.length > MAX_BODY_BYTES) {
                Timber.tag("RESOLVE_CIPHER").w("remote player config too large (${body.length} bytes), ignored")
                null
            } else {
                FetchResult(body = body, etag = conn.getHeaderField("ETag"), notModified = false)
            }
        } catch (e: Exception) {
            Timber.tag("RESOLVE_CIPHER").w("remote player config unreachable: ${e.javaClass.simpleName}: ${e.message}")
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
