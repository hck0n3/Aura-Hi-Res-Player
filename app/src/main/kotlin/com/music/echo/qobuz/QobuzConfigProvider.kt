package iad1tya.echo.music.qobuz

import iad1tya.echo.music.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber

/**
 * Resolves the app_id + candidate app_secrets used to sign the owner's Qobuz requests, in priority order:
 *
 *  1. BuildConfig.QOBUZ_APP_ID / QOBUZ_APP_SECRET — what the owner baked in (or a CI secret).
 *  2. A self-healing REMOTE CONFIG (same idea as the app's `player_configs.json` cipher self-heal): a
 *     tiny JSON the owner can publish if/when Qobuz rotates the web-player keys, fixing every install with
 *     no app update. Best-effort, cached in memory for the session.
 *  3. The runtime bundle.js scraper ([QobuzBundleScraper]) — so a build shipping blank keys still works.
 *
 * Every source can contribute; the results are merged (app_id: first non-blank wins; secrets: all, in
 * order, de-duplicated) and handed to [QobuzAuthenticator], which validates each secret by signing a real
 * getFileUrl and persists only the one that actually works. So a stale/wrong candidate is harmless.
 */
class QobuzConfigProvider(
    private val httpClient: OkHttpClient,
    private val scraper: QobuzBundleScraper,
) {
    data class Config(val appId: String?, val secrets: List<String>)

    @Volatile private var remoteCache: Config? = null
    @Volatile private var scrapeCache: Config? = null
    @Volatile private var remoteEtag: String? = null

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    suspend fun resolve(forceRefresh: Boolean = false): Config = withContext(Dispatchers.IO) {
        val appIds = ArrayList<String>()
        val secrets = ArrayList<String>()

        // 1. Build-time keys.
        BuildConfig.QOBUZ_APP_ID.takeIf { it.isNotBlank() }?.let { appIds.add(it) }
        BuildConfig.QOBUZ_APP_SECRET.takeIf { it.isNotBlank() }?.let { secrets.add(it) }

        // 2. Remote self-heal config.
        val remote = (if (forceRefresh) null else remoteCache) ?: fetchRemote().also { remoteCache = it }
        remote.appId?.takeIf { it.isNotBlank() }?.let { appIds.add(it) }
        secrets.addAll(remote.secrets)

        // 3. Runtime scrape — only if we still lack an app_id OR have no secret to try.
        if (appIds.isEmpty() || secrets.isEmpty()) {
            val scraped = (if (forceRefresh) null else scrapeCache) ?: scraper.scrape().let {
                Config(it.appId, it.secrets)
            }.also { scrapeCache = it }
            scraped.appId?.takeIf { it.isNotBlank() }?.let { appIds.add(it) }
            secrets.addAll(scraped.secrets)
        }

        Config(appId = appIds.firstOrNull(), secrets = secrets.distinct())
    }

    /**
     * Conditional, size-capped GET of the owner's published config, following the same discipline as
     * [iad1tya.echo.music.utils.cipher.RemotePlayerConfig]:
     *  - `If-None-Match` when we already hold a config, and a 304 keeps what we have,
     *  - the body is read through `peekBody` so a pathological response can never be slurped whole,
     *  - ANY non-2xx (a 404 is the normal, healthy state — the owner only publishes this file if Qobuz
     *    rotates its web-player keys) yields an empty config and the caller falls through to the scraper.
     *
     * The ETag is per-process (this provider has no Context, and the config is consulted only on the
     * one-time link flow) — unlike RemotePlayerConfig, which persists its ETag next to its disk cache.
     */
    private fun fetchRemote(): Config {
        val builder = Request.Builder()
            .url(REMOTE_CONFIG_URL)
            .header("User-Agent", QobuzApi.USER_AGENT)
            .get()
        val previous = remoteCache
        val etag = remoteEtag
        if (previous != null && !etag.isNullOrBlank()) builder.header("If-None-Match", etag)
        return runCatching {
            httpClient.newCall(builder.build()).execute().use { response ->
                if (response.code == HTTP_NOT_MODIFIED) {
                    return@use previous ?: Config(null, emptyList())
                }
                if (!response.isSuccessful) return@use Config(null, emptyList())
                val body = response.peekBody(MAX_BODY_BYTES).string()
                val parsed = json.decodeFromString<RemoteConfig>(body)
                remoteEtag = response.header("ETag")
                Config(
                    appId = parsed.appId,
                    secrets = buildList {
                        parsed.appSecret?.let { add(it) }
                        addAll(parsed.appSecrets)
                    }.filter { it.isNotBlank() },
                )
            }
        }.getOrElse {
            // Type only: a decoding error quotes the payload, and that payload IS the app_secret material.
            Timber.tag(TAG).d("Qobuz remote config unavailable (%s)", it.javaClass.simpleName)
            Config(null, emptyList())
        }
    }

    @Serializable
    private data class RemoteConfig(
        @SerialName("app_id") val appId: String? = null,
        @SerialName("app_secret") val appSecret: String? = null,
        @SerialName("app_secrets") val appSecrets: List<String> = emptyList(),
    )

    private companion object {
        const val TAG = "QobuzConfigProvider"

        /**
         * Self-heal endpoint, published by the OWNER only if Qobuz rotates the web-player keys. Mirrors the
         * existing player_configs.json cipher self-heal, and points at the SAME owner repository as
         * [iad1tya.echo.music.utils.cipher.RemotePlayerConfig] and
         * [iad1tya.echo.music.recognition.RemoteRecognitionConfig].
         *
         * It must never point at a third party's repo: the owner cannot publish there (so the promised
         * no-app-update key rotation would be impossible), it would hand that third party control of the
         * app_id used alongside the owner's own user_auth_token, and it breaks the Aura-branding rule.
         *
         * The file does NOT exist yet — a 404 is the normal, healthy state and simply falls through to the
         * bundle scraper (see [fetchRemote]).
         */
        const val REMOTE_CONFIG_URL =
            "https://raw.githubusercontent.com/hck0n3/Aura-Hi-Res-Player/main/qobuz_config.json"

        // Guard against pathological downloads: a real config file is well under a kilobyte.
        const val MAX_BODY_BYTES = 64L * 1024L
        const val HTTP_NOT_MODIFIED = 304
    }
}
