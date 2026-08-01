package iad1tya.echo.music.qobuz

import iad1tya.echo.music.utils.qobuz.QobuzSearchData
import iad1tya.echo.music.utils.qobuz.QobuzTrack
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.Json
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import timber.log.Timber
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Stateless, low-level client for the OFFICIAL Qobuz API. Every call takes the credentials it needs
 * (app_id, optionally the user_auth_token, and for the signed getFileUrl the app_secret), so the SAME
 * instance serves both the one-time login/secret-discovery flow ([QobuzAuthenticator]) and the playback
 * hot path ([QobuzHiRes]) without holding any session state of its own.
 *
 * This talks to Qobuz DIRECTLY with the user's real subscription token — it is NOT the third-party
 * squid.wtf proxy used by [iad1tya.echo.music.utils.qobuz.QobuzApiClient].
 *
 * CANCELLABILITY (playback budget): every request goes through [awaitCall], i.e. OkHttp's ASYNC
 * `enqueue` wrapped in a cancellable continuation that cancels the underlying [Call] as soon as the
 * coroutine is cancelled. A blocking `execute()` cannot be interrupted — the caller's
 * `withTimeoutOrNull(...)` would return on time while the socket kept running to its own connect/read
 * timeout, so the "budget" was advisory. With `enqueue` + `invokeOnCancellation` the budget is real.
 *
 * LOGGING: this client handles a live subscription credential, so failures are logged by exception TYPE
 * only. Serialization errors embed the offending payload in their message, and that payload can contain
 * the `user_auth_token` (user/login) or the app_secret-signed url (getFileUrl) — never log the throwable.
 */
class QobuzApi(
    private val httpClient: OkHttpClient,
) {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    /**
     * `user/login`. Login itself is UNSIGNED — only getFileUrl needs the app_secret. Some Qobuz
     * deployments expect the password hashed (md5), others plain; we try plain first and, on an auth
     * rejection, retry with md5 so either backend works. Returns null on a hard failure (bad app_id,
     * network). A wrong password surfaces as a null token inside a 401 → caller reports "credenciales".
     */
    suspend fun login(
        appId: String,
        email: String,
        password: String,
    ): QobuzLoginResponse? =
        loginAttempt(appId, email, password)
            ?: loginAttempt(appId, email, QobuzSignature.md5Hex(password))

    private suspend fun loginAttempt(appId: String, email: String, password: String): QobuzLoginResponse? {
        val url = "${BASE_URL}user/login".toHttpUrl().newBuilder()
            .addQueryParameter("email", email)
            .addQueryParameter("password", password)
            .addQueryParameter("app_id", appId)
            .build()
        val request = baseRequest(appId, token = null).url(url).get().build()
        return try {
            awaitCall(request) { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    Timber.tag(TAG).d("user/login HTTP %d", response.code)
                    null
                } else {
                    json.decodeFromString<QobuzLoginResponse>(body)
                        .takeIf { !it.userAuthToken.isNullOrBlank() }
                }
            }
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: Throwable) {
            // Type only: a decoding error's message can quote the body, which holds the user_auth_token.
            Timber.tag(TAG).d("user/login failed (%s)", e.javaClass.simpleName)
            null
        }
    }

    /** `track/search`. Works with just the app_id; the token (when present) unlocks the user's region. */
    suspend fun searchTracks(
        appId: String,
        token: String?,
        query: String,
        limit: Int = 20,
    ): List<QobuzTrack> {
        val url = "${BASE_URL}track/search".toHttpUrl().newBuilder()
            .addQueryParameter("query", query)
            .addQueryParameter("limit", limit.toString())
            .addQueryParameter("app_id", appId)
            .build()
        val request = baseRequest(appId, token).url(url).get().build()
        return try {
            awaitCall(request) { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) emptyList()
                else json.decodeFromString<QobuzSearchData>(body).tracks?.items ?: emptyList()
            }
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: Throwable) {
            Timber.tag(TAG).d("track/search failed (%s)", e.javaClass.simpleName)
            emptyList()
        }
    }

    /** `track/get` — full metadata for one track (used by the "reproducir en Qobuz" explicit action). */
    suspend fun getTrack(
        appId: String,
        token: String?,
        trackId: Long,
    ): QobuzTrack? {
        val url = "${BASE_URL}track/get".toHttpUrl().newBuilder()
            .addQueryParameter("track_id", trackId.toString())
            .addQueryParameter("app_id", appId)
            .build()
        val request = baseRequest(appId, token).url(url).get().build()
        return try {
            awaitCall(request) { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) null else json.decodeFromString<QobuzTrack>(body)
            }
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: Throwable) {
            Timber.tag(TAG).d("track/get failed (%s)", e.javaClass.simpleName)
            null
        }
    }

    /**
     * `track/getFileUrl` — the signed, entitled stream request. Returns a distinguishable outcome:
     *  - [FileUrlResult.Ok] with the parsed response (which may still be a `sample` preview, or a
     *    server-side LOSSY downgrade — the caller decides what counts as a win),
     *  - [FileUrlResult.BadSignature] (HTTP 400) so the caller can try the NEXT candidate app_secret,
     *  - [FileUrlResult.Unauthorized] (HTTP 401) so the caller knows the TOKEN is bad (stop trying secrets),
     *  - [FileUrlResult.Failed] for anything else.
     */
    suspend fun getFileUrl(
        appId: String,
        appSecret: String,
        token: String,
        trackId: Long,
        formatId: Int,
    ): FileUrlResult {
        val ts = System.currentTimeMillis() / 1000L
        val sig = QobuzSignature.trackGetFileUrl(trackId, formatId, ts, appSecret)
        val url = "${BASE_URL}track/getFileUrl".toHttpUrl().newBuilder()
            .addQueryParameter("request_ts", ts.toString())
            .addQueryParameter("request_sig", sig)
            .addQueryParameter("track_id", trackId.toString())
            .addQueryParameter("format_id", formatId.toString())
            .addQueryParameter("intent", "stream")
            .addQueryParameter("app_id", appId)
            .build()
        val request = baseRequest(appId, token).url(url).get().build()
        return try {
            awaitCall(request) { response ->
                val body = response.body?.string().orEmpty()
                when {
                    response.isSuccessful ->
                        FileUrlResult.Ok(json.decodeFromString<QobuzFileUrlResponse>(body))
                    response.code == 400 -> FileUrlResult.BadSignature
                    response.code == 401 -> FileUrlResult.Unauthorized
                    else -> FileUrlResult.Failed("HTTP ${response.code}")
                }
            }
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: Throwable) {
            // The signed body carries the temporary CDN url — log the type, not the payload.
            val type = e.javaClass.simpleName
            Timber.tag(TAG).d("track/getFileUrl failed (%s)", type)
            FileUrlResult.Failed(type)
        }
    }

    /**
     * Runs [request] asynchronously and suspends until it completes, mapping the response with [transform]
     * while the body is still open (the response is closed on the way out either way).
     *
     * The point is [kotlinx.coroutines.CancellableContinuation.invokeOnCancellation]: when the caller's
     * timeout (or a song change) cancels the coroutine, the in-flight [Call] is cancelled too, so the
     * socket is released immediately instead of running on to its own connect/read timeout. [transform]
     * runs on OkHttp's dispatcher thread — never the main thread — so no extra Dispatchers.IO hop is
     * needed and none is used (a `withContext(IO)` around a blocking `execute()` was the old, uncancellable
     * shape).
     */
    private suspend fun <T> awaitCall(request: Request, transform: (Response) -> T): T =
        suspendCancellableCoroutine { cont ->
            val httpCall = httpClient.newCall(request)
            cont.invokeOnCancellation { runCatching { httpCall.cancel() } }
            httpCall.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (cont.isActive) cont.resumeWithException(e)
                }

                override fun onResponse(call: Call, response: Response) {
                    val mapped = runCatching { response.use(transform) }
                    if (!cont.isActive) return
                    mapped.fold(
                        onSuccess = { cont.resume(it) },
                        onFailure = { cont.resumeWithException(it) },
                    )
                }
            })
        }

    private fun baseRequest(appId: String, token: String?): Request.Builder {
        val builder = Request.Builder()
            .header("X-App-Id", appId)
            .header("User-Agent", USER_AGENT)
        if (!token.isNullOrBlank()) builder.header("X-User-Auth-Token", token)
        return builder
    }

    sealed interface FileUrlResult {
        data class Ok(val response: QobuzFileUrlResponse) : FileUrlResult
        data object BadSignature : FileUrlResult
        data object Unauthorized : FileUrlResult
        data class Failed(val reason: String) : FileUrlResult
    }

    companion object {
        const val BASE_URL = "https://www.qobuz.com/api.json/0.2/"
        // A desktop User-Agent — the web-player keys are validated against a browser-shaped client.
        const val USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Chrome/124.0.0.0 Safari/537.36"
        private const val TAG = "QobuzApi"
    }
}
