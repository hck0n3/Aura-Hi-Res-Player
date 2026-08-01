package iad1tya.echo.music.qobuz

import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber

/**
 * Runtime fallback that scrapes the PUBLIC play.qobuz.com web-player bundle for the app_id and the
 * timezone-seeded app_secret candidates ("spoofbuz"). Used only when no app_id/app_secret is baked into
 * the build (or the remote config) — i.e. so the feature can work even on a build that ships blank keys.
 *
 * Steps (all best-effort; any failure returns what we already have):
 *  1. GET play.qobuz.com/login and find the `/resources/<ver>/bundle.js` reference.
 *  2. GET that bundle.js.
 *  3. app_id: `appId:"(\d{9})"`.
 *  4. secrets: each `…initialSeed("<seed>",window.utimezone.<tz>)` gives a per-timezone seed; each
 *     `name:"…/<Tz>",info:"<info>",extras:"<extras>"` gives that timezone's info+extras. For every
 *     timezone, `seed+info+extras`, drop the last 44 chars, base64-decode → one secret candidate.
 *
 * This CANNOT be validated here (it depends on the live bundle); [QobuzAuthenticator] validates each
 * candidate by actually signing a getFileUrl, and only a working one is ever persisted.
 */
class QobuzBundleScraper(
    private val httpClient: OkHttpClient,
) {
    data class Spoof(val appId: String?, val secrets: List<String>)

    suspend fun scrape(): Spoof = withContext(Dispatchers.IO) {
        runCatching {
            val loginPage = get(LOGIN_URL) ?: return@runCatching Spoof(null, emptyList())
            val bundlePath = BUNDLE_REF_REGEX.find(loginPage)?.groupValues?.get(1)
                ?: return@runCatching Spoof(null, emptyList())
            val bundleUrl = if (bundlePath.startsWith("http")) bundlePath else "https://play.qobuz.com$bundlePath"
            val bundle = get(bundleUrl) ?: return@runCatching Spoof(null, emptyList())

            val appId = APP_ID_REGEX.find(bundle)?.groupValues?.get(1)
            val secrets = extractSecrets(bundle)
            Timber.tag(TAG).d("Scraped Qobuz bundle: appId=%s secrets=%d", appId, secrets.size)
            Spoof(appId, secrets)
        }.getOrElse {
            Timber.tag(TAG).w(it, "Qobuz bundle scrape failed")
            Spoof(null, emptyList())
        }
    }

    private fun extractSecrets(bundle: String): List<String> {
        // timezone -> seed
        val seeds = LinkedHashMap<String, String>()
        for (m in SEED_REGEX.findAll(bundle)) {
            val seed = m.groupValues[1]
            val tz = m.groupValues[2].lowercase()
            seeds[tz] = seed
        }
        // timezone -> seed + info + extras (the info/extras block names the timezone capitalised)
        val out = ArrayList<String>()
        for (m in INFO_EXTRAS_REGEX.findAll(bundle)) {
            val tz = m.groupValues[1].lowercase()
            val info = m.groupValues[2]
            val extras = m.groupValues[3]
            val seed = seeds[tz] ?: continue
            val combined = seed + info + extras
            if (combined.length <= 44) continue
            val trimmed = combined.substring(0, combined.length - 44)
            val decoded = runCatching {
                String(Base64.decode(trimmed, Base64.DEFAULT), Charsets.UTF_8)
            }.getOrNull()
            if (!decoded.isNullOrBlank()) out.add(decoded)
        }
        return out.distinct()
    }

    private fun get(url: String): String? {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", QobuzApi.USER_AGENT)
            .get()
            .build()
        return runCatching {
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) null else response.body?.string()
            }
        }.getOrNull()
    }

    private companion object {
        const val TAG = "QobuzBundleScraper"
        const val LOGIN_URL = "https://play.qobuz.com/login"
        val BUNDLE_REF_REGEX = Regex("""<script src="(/resources/\d+\.\d+\.\d+-[a-z]\d+/bundle\.js)"""")
        val APP_ID_REGEX = Regex("""appId:"(\d{9})"""")
        val SEED_REGEX = Regex("""[a-z]\.initialSeed\("([\w=]+)",window\.utimezone\.([a-z]+)\)""")
        val INFO_EXTRAS_REGEX =
            Regex("""name:"\w+/([A-Z][a-z]+)",info:"([\w=]+)",extras:"([\w=]+)"""")
    }
}
