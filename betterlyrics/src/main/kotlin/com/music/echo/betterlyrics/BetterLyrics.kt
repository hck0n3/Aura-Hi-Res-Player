package iad1tya.echo.music.betterlyrics

import iad1tya.echo.music.betterlyrics.models.TTMLResponse
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.http.HttpStatusCode
import io.ktor.client.plugins.ClientRequestException
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import timber.log.Timber

object BetterLyrics {
    private val client by lazy {
        HttpClient(OkHttp) {
            install(ContentNegotiation) {
                json(
                    Json {
                        isLenient = true
                        ignoreUnknownKeys = true
                    },
                )
            }

            install(HttpTimeout) {
                requestTimeoutMillis = 15000
                connectTimeoutMillis = 10000
                socketTimeoutMillis = 15000
            }

            defaultRequest {
                url("https://lyrics-api.boidu.dev")
                header("User-Agent", "Mozilla/5.0 (Linux; Android 13) AuraHiRes/1.0")
                header("Accept", "application/json")
            }

            expectSuccess = false
        }
    }

    private suspend fun fetchWithRetry(
        maxRetries: Int = 2,
        block: suspend () -> String?,
    ): String? {
        var lastException: Exception? = null
        repeat(maxRetries + 1) { attempt ->
            try {
                val result = block()
                if (result != null) return result
                // If null returned (404, 403, etc.), don't retry
                return null
            } catch (e: Exception) {
                lastException = e
                val isNetworkError = e is java.net.SocketException ||
                    e is java.io.InterruptedIOException ||
                    e is java.net.UnknownHostException ||
                    e is java.io.IOException
                
                if (!isNetworkError || attempt == maxRetries) {
                    throw e
                }
                Timber.tag("BetterLyrics").d("Retry attempt ${attempt + 1} after network error: ${e.message}")
                kotlinx.coroutines.delay(500L * (attempt + 1)) // Exponential backoff
            }
        }
        throw lastException ?: Exception("Unknown error after retries")
    }

    private suspend fun fetchTTML(
        artist: String,
        title: String,
        duration: Int = -1,
        album: String? = null,
    ): String? = fetchWithRetry(maxRetries = 2) {
        runCatching {
            val response = client.get("/getLyrics") {
                parameter("s", title)
                parameter("a", artist)
                if (duration > 0) {
                    parameter("d", duration)
                }
                if (!album.isNullOrBlank()) {
                    parameter("al", album)
                }
            }
            when (response.status) {
                HttpStatusCode.OK -> response.body<TTMLResponse>().ttml
                HttpStatusCode.NotFound -> {
                    Timber.tag("BetterLyrics").d("Lyrics not found (404): $title - $artist")
                    null
                }
                HttpStatusCode.Forbidden -> {
                    // Cloudflare 403 - silent failure, no stacktrace
                    Timber.tag("BetterLyrics").d("Access forbidden (403): $title - $artist")
                    null
                }
                HttpStatusCode.BadRequest -> {
                    // Precondition check failed (400) - bad payload/headers
                    Timber.tag("BetterLyrics").d("Bad request (400): $title - $artist")
                    null
                }
                HttpStatusCode.TooManyRequests -> {
                    // Rate limited - silent, will retry via HttpRequestRetry
                    Timber.tag("BetterLyrics").d("Rate limited (429): $title - $artist")
                    null
                }
                else -> {
                    Timber.tag("BetterLyrics").d("HTTP ${response.status.value}: $title - $artist")
                    null
                }
            }
        }.getOrElse { e ->
            // Silent failure — catch specific Ktor exceptions and network errors, no stacktrace spam
            when (e) {
                is ClientRequestException -> {
                    Timber.tag("BetterLyrics").d("Client request failed: ${e.response?.status?.value} — ${e.message?.take(120)}")
                }
                is java.net.SocketException,
                is java.io.InterruptedIOException,
                is java.net.UnknownHostException -> {
                    Timber.tag("BetterLyrics").d("Network error: ${e.javaClass.simpleName}")
                    throw e // Re-throw network errors for retry
                }
                else -> {
                    Timber.tag("BetterLyrics").d("Fetch failed: ${e.javaClass.simpleName} — ${e.message?.take(120)}")
                }
            }
            null
        }
    }

    suspend fun getLyrics(
        title: String,
        artist: String,
        duration: Int,
        album: String? = null,
    ): Result<String> = runCatching {
        // Use exact title and artist - no normalization to ensure correct sync
        // Normalizing can return wrong lyrics (e.g., radio edit vs original)
        val ttml = fetchTTML(artist, title, duration, album)
            ?: throw IllegalStateException("Lyrics unavailable")

        val parsedLines = TTMLParser.parseTTML(ttml)
        if (parsedLines.isEmpty()) {
            throw IllegalStateException("Failed to parse lyrics")
        }

        TTMLParser.toLRC(parsedLines)
    }

    suspend fun getAllLyrics(
        title: String,
        artist: String,
        duration: Int,
        album: String? = null,
        callback: (String) -> Unit,
    ) {
        getLyrics(title, artist, duration, album)
            .onSuccess { lrcString ->
                callback(lrcString)
            }
    }
}
