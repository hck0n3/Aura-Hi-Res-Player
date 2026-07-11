package iad1tya.echo.music.recognition

import android.content.Context
import com.music.shazamkit.ShazamConfig
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
 * Self-healing REMOTE CONFIG for song recognition — the recognition-layer twin of
 * [iad1tya.echo.music.utils.cipher.RemotePlayerConfig].
 *
 * WHY: recognition POSTs the audio signature to a HARDCODED Shazam host/path with a hardcoded set of
 * User-Agents (see [ShazamConfig]). If Shazam rotates that endpoint, geo-blocks our egress, or bumps
 * its API, recognition breaks for EVERYONE until an app update ships. This client fetches an optional
 * `shazam_recognition_config.json` the owner may publish next to `player_configs.json`, and pushes any
 * overrides into [ShazamConfig] — curing a rotation WITHOUT an app update.
 *
 * SAFETY / SCOPE:
 *  - INERT until published: a 404 / offline launch / malformed JSON leaves the compiled defaults in
 *    place, so the app behaves EXACTLY as before. Never throws.
 *  - OVERRIDE-ONLY: [ShazamConfig.applyRemote] keeps any default whose remote value is missing/blank,
 *    so a partial file can't brick the transport.
 *  - Does NOT touch the signature algorithm, the license Worker's /verify or /demo routes, or anything
 *    outside the recognition transport knobs.
 *
 * JSON SCHEMA the owner publishes at [REMOTE_CONFIG_URL] (every field optional):
 * ```
 *   {
 *     "enabled": true,
 *     "host": "amp.shazam.com",
 *     "pathTemplate": "/discovery/v5/en/US/android/-/tag/{uuid1}/{uuid2}",
 *     "userAgents": ["Dalvik/2.1.0 (...)", "..."],
 *     "relayUrl": "https://round-math-d64e.toberto4000.workers.dev/recognize",
 *     "providerOrder": ["direct", "relay"]
 *   }
 * ```
 * `pathTemplate` MUST keep both `{uuid1}` and `{uuid2}` placeholders or it is ignored.
 */
object RemoteRecognitionConfig {

    private const val TAG = "Aura_RemoteRecogCfg"

    /**
     * Published config JSON. Hosted next to the cipher's `player_configs.json` on the SAME base the
     * owner already controls, so both self-healing files live together.
     */
    const val REMOTE_CONFIG_URL =
        "https://raw.githubusercontent.com/hck0n3/Aura-Hi-Res-Player/main/shazam_recognition_config.json"

    private const val CACHE_FILE_NAME = "shazam_recognition_config_cache.json"
    private const val ETAG_FILE_NAME = "shazam_recognition_config_cache.etag"

    // A real config file is well under a KB; guard against a pathological download.
    private const val MAX_BODY_BYTES = 128 * 1024

    private const val CONNECT_TIMEOUT_MS = 8_000
    private const val READ_TIMEOUT_MS = 8_000

    // Idempotent/cheap: a successful refresh is not repeated within this window (per process).
    private const val MIN_REFRESH_INTERVAL_MS = 60L * 60L * 1_000L // 1 hour

    @Volatile
    private var lastRefreshAtMs: Long = 0L

    @Volatile
    private var applied: Boolean = false

    // Serializes fetch + apply + cache write so a concurrent write can't publish a truncated cache file.
    private val refreshMutex = Mutex()

    /**
     * Populate [ShazamConfig] from the on-disk cache. Synchronous and cheap, so a previously-fetched
     * override is active for the very first recognition, even offline. Best-effort: any failure leaves
     * the compiled defaults untouched. Call once at startup, off the main thread.
     */
    fun loadCache(context: Context) {
        try {
            val file = cacheFile(context)
            if (!file.exists()) return
            val body = file.readText()
            if (applyJson(body)) {
                applied = true
                Timber.tag(TAG).d("Loaded cached recognition config")
            }
        } catch (e: Exception) {
            Timber.tag(TAG).w(e, "loadCache failed (ignored)")
        }
    }

    /**
     * Fetch the latest config from [REMOTE_CONFIG_URL], apply it, and rewrite the cache. Runs on
     * [Dispatchers.IO], never throws, and no-ops if a successful refresh already happened within
     * [MIN_REFRESH_INTERVAL_MS]. A failed/empty/malformed fetch keeps whatever was already applied.
     */
    suspend fun refresh(context: Context) = withContext(Dispatchers.IO) {
        refreshMutex.withLock {
            // Same clock-drift discipline as RemotePlayerConfig: a NEGATIVE elapsed (wall clock moved
            // back) is treated as stale, not "recent", so it refetches instead of blocking.
            val sinceLast = System.currentTimeMillis() - lastRefreshAtMs
            if (applied && sinceLast in 0 until MIN_REFRESH_INTERVAL_MS) return@withLock
            fetchAndApply(context)
        }
    }

    /** Fetch + parse + apply + rewrite cache. Must hold [refreshMutex]. Never throws. */
    private fun fetchAndApply(context: Context) {
        try {
            val etag = if (applied) readEtag(context) else null
            val result = httpGet(REMOTE_CONFIG_URL, etag)
            if (result == null) {
                Timber.tag(TAG).d("refresh: no body (offline / non-2xx / not published); keeping defaults")
                return
            }
            if (result.notModified) {
                lastRefreshAtMs = System.currentTimeMillis()
                return
            }
            val body = result.body ?: return
            if (!applyJson(body)) {
                Timber.tag(TAG).d("refresh: no valid config fields; keeping defaults")
                return
            }
            applied = true
            lastRefreshAtMs = System.currentTimeMillis()
            runCatching {
                writeAtomic(cacheFile(context), body)
                writeEtag(context, result.etag)
            }.onFailure { Timber.tag(TAG).w(it, "refresh: cache write failed (ignored)") }
            Timber.tag(TAG).d("refresh: applied recognition config")
        } catch (e: Exception) {
            Timber.tag(TAG).w(e, "refresh failed (ignored)")
        }
    }

    /**
     * Parse the published JSON and push overrides into [ShazamConfig]. Returns true if the body was a
     * usable JSON object (even if every field was defaulted), false if it was structurally invalid.
     */
    private fun applyJson(json: String): Boolean {
        val obj: JSONObject = runCatching { JSONObject(json.trim()) }.getOrNull() ?: return false
        ShazamConfig.applyRemote(
            enabled = if (obj.has("enabled") && !obj.isNull("enabled")) obj.optBoolean("enabled", true) else null,
            host = strOrNull(obj, "host"),
            pathTemplate = strOrNull(obj, "pathTemplate"),
            userAgents = stringListOrNull(obj, "userAgents"),
            relayUrl = strOrNull(obj, "relayUrl"),
            providerOrder = stringListOrNull(obj, "providerOrder"),
        )
        return true
    }

    private fun strOrNull(o: JSONObject, key: String): String? =
        if (o.has(key) && !o.isNull(key)) o.optString(key).takeIf { it.isNotBlank() } else null

    private fun stringListOrNull(o: JSONObject, key: String): List<String>? {
        if (!o.has(key) || o.isNull(key)) return null
        val a: JSONArray = o.optJSONArray(key) ?: return null
        val list = ArrayList<String>(a.length())
        for (i in 0 until a.length()) a.optString(i).takeIf { it.isNotBlank() }?.let { list += it }
        return list.takeIf { it.isNotEmpty() }
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

    /** Temp-file + rename publish so a reader never sees a half-written cache. */
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

    /** Best-effort conditional GET. 2xx → capped body + ETag; 304 → notModified; else/error → null. */
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
            if (code !in 200..299) return null
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            if (body.length > MAX_BODY_BYTES) null
            else FetchResult(body = body, etag = conn.getHeaderField("ETag"), notModified = false)
        } catch (e: Exception) {
            null
        } finally {
            runCatching { conn?.disconnect() }
        }
    }
}
