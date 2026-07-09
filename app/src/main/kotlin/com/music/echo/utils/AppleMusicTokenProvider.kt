package iad1tya.echo.music.utils

import android.util.Base64
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

object AppleMusicTokenProvider {
    // Refetch a little before the real `exp` so we never hand out a token that
    // expires mid-request.
    private const val EXPIRY_SKEW_MS = 5 * 60 * 1000L // 5 minutes
    // TTL used only when a freshly extracted token has no parseable `exp` claim.
    private const val DEFAULT_TTL_MS = 60 * 60 * 1000L // 1 hour

    // The extracted (or fallback) Apple Music developer JWT, plus the epoch-millis
    // instant at which the cached copy must be considered stale and refetched.
    private var cachedToken: String? = null
    private var cachedTokenExpiryMs: Long = 0L
    private val mutex = Mutex()

    private val httpClient = HttpClient(OkHttp) {
        expectSuccess = true
    }

    // Self-healing is handled entirely by the TTL/`exp`-derived expiry window below
    // (see [computeExpiryMs]): a stale token is dropped and re-extracted on the next
    // [getToken], and the fallback token is deliberately never cached. An explicit
    // 401/403 invalidation hook would have to live at the API call site (which is in a
    // separate file), so it is intentionally omitted here rather than left as dead code.
    suspend fun getToken(): String {
        return mutex.withLock {
            // Reuse the cached token only while it is still within its validity window.
            cachedToken?.let { token ->
                if (System.currentTimeMillis() < cachedTokenExpiryMs) {
                    return@withLock token
                }
            }
            // Expired (or never fetched): clear and (re)extract.
            cachedToken = null
            cachedTokenExpiryMs = 0L

            try {
                // 1. Fetch main page
                val htmlResponse = httpClient.get("https://beta.music.apple.com")
                val htmlBody = htmlResponse.bodyAsText()

                // 2. Find index.js URI
                val indexJsRegex = Regex("""src="(/assets/index-[^"]+\.js)"""")
                val match = indexJsRegex.find(htmlBody)
                    ?: throw Exception("Could not find index.js in Apple Music web player")
                val indexJsUri = match.groupValues[1]

                // 3. Fetch index.js
                val indexJsResponse = httpClient.get("https://beta.music.apple.com$indexJsUri")
                val indexJsBody = indexJsResponse.bodyAsText()

                // 4. Extract JWT token using the updated regex
                val tokenRegex = Regex("""eyJ[A-Za-z0-9\-_=]+\.[A-Za-z0-9\-_=]+\.[A-Za-z0-9\-_=]+""")
                val tokenMatch = tokenRegex.find(indexJsBody)
                    ?: throw Exception("Could not find token in index.js")

                val token = tokenMatch.value
                cachedToken = token
                cachedTokenExpiryMs = computeExpiryMs(token)
                token
            } catch (e: Exception) {
                e.printStackTrace()
                // Fallback token if live extraction fails. It is deliberately NOT
                // cached, so the very next call re-attempts extraction and the
                // provider self-heals once Apple's web player is parseable again.
                // If the fallback is itself expired the caller just gets a 401 ->
                // "no album description" (handled gracefully upstream), never a
                // crash and never a permanently poisoned cache.
                FALLBACK_TOKEN
            }
        }
    }

    /**
     * Derive a cache-expiry instant (epoch millis) from a JWT's `exp` claim, minus a
     * safety skew. Falls back to a short TTL when the token can't be parsed. Best-effort:
     * any failure yields the default TTL rather than throwing.
     */
    private fun computeExpiryMs(jwt: String): Long {
        val now = System.currentTimeMillis()
        val expSeconds = parseJwtExpSeconds(jwt) ?: return now + DEFAULT_TTL_MS
        val expiryMs = expSeconds * 1000L - EXPIRY_SKEW_MS
        // If the freshly extracted token is already at/past its skewed expiry (device
        // clock skew or a stale asset), don't pin it for long — allow a near-immediate
        // refetch on the next call instead of caching a dead token for the process life.
        return if (expiryMs <= now) now else expiryMs
    }

    /**
     * Decode the JWT payload (second dot-separated segment, base64url) and read the
     * numeric `exp` claim (seconds since epoch). Returns null on any malformed input.
     */
    private fun parseJwtExpSeconds(jwt: String): Long? {
        return try {
            val parts = jwt.split(".")
            if (parts.size < 2) return null
            val payloadJson = String(
                Base64.decode(parts[1], Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING),
                Charsets.UTF_8
            )
            val expMatch = Regex(""""exp"\s*:\s*(\d+)""").find(payloadJson) ?: return null
            expMatch.groupValues[1].toLongOrNull()
        } catch (e: Exception) {
            null
        }
    }

    // Last-resort token used only when live extraction fails. Never cached; treated as
    // best-effort. May be expired, in which case requests fail gracefully with no
    // description rather than crashing.
    private const val FALLBACK_TOKEN =
        "eyJhbGciOiJFUzI1NiIsInR5cCI6IkpXVCIsImtpZCI6IldlYlBsYXlLaWQifQ.eyJpc3MiOiJBTVBXZWJQbGF5IiwiaWF0IjoxNzc0NDU2MzgyLCJleHAiOjE3ODE3MTM5ODIsInJvb3RfaHR0cHNfb3JpZ2luIjpbImFwcGxlLmNvbSJdfQ.4n8qYF4qa18sL1E0G9A3qX35cD8wQ-IJcS9Bh8ZT8JV_yLBtVq46B-9-2ZS3EvWHuw3yK9BYFYAhAdTaDm38vQ"
}
