package iad1tya.echo.music.api

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Sends an OpenAI-compatible chat completion to turn a natural-language prompt into a playlist.
 * Mirrors [OpenRouterService] (OkHttp client, headers, 5xx retries, timeouts): builds the request
 * via [AiPlaylistPrompt] and parses the reply via [AiPlaylistParser].
 *
 * DeepL (not a chat API) and Claude (different `/v1/messages` schema) are intentionally unsupported
 * here; the caller surfaces a "pick a chat provider" message.
 */
object AiPlaylistService {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val JSON = "application/json; charset=utf-8".toMediaType()

    private val UNSUPPORTED_PROVIDERS = setOf("DeepL", "Claude")

    class UnsupportedProviderException(val providerName: String) :
        Exception("Provider not supported for AI playlists: $providerName")

    class MissingApiKeyException : Exception("AI API key is not set")

    suspend fun generate(
        prompt: String,
        count: Int,
        provider: String,
        apiKey: String,
        baseUrl: String,
        model: String,
        maxRetries: Int = 3,
    ): Result<AiPlaylistSpec> = withContext(Dispatchers.IO) {
        if (prompt.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Prompt is empty"))
        }
        if (provider in UNSUPPORTED_PROVIDERS) {
            return@withContext Result.failure(UnsupportedProviderException(provider))
        }
        if (apiKey.isBlank()) {
            return@withContext Result.failure(MissingApiKeyException())
        }

        val messages = JSONArray().apply {
            AiPlaylistPrompt.buildMessages(prompt, count).forEach { message ->
                put(
                    JSONObject().apply {
                        put("role", message.role)
                        put("content", message.content)
                    },
                )
            }
        }
        val requestJson = JSONObject().apply {
            if (model.isNotBlank()) put("model", model)
            put("messages", messages)
            put("temperature", 0.8)
            put("max_tokens", 2048)
        }

        var attempt = 0
        var lastError: String? = null
        while (attempt < maxRetries) {
            try {
                val request = Request.Builder()
                    .url(baseUrl.ifBlank { "https://openrouter.ai/api/v1/chat/completions" })
                    .addHeader("Authorization", "Bearer ${apiKey.trim()}")
                    .addHeader("Content-Type", "application/json")
                    .addHeader("HTTP-Referer", "https://github.com/EchoMusicApp/Echo-Music")
                    .addHeader("X-Title", "echomusic")
                    .post(requestJson.toString().toRequestBody(JSON))
                    .build()

                val response = client.newCall(request).execute()
                val responseBody = response.body?.string()

                if (!response.isSuccessful) {
                    if (response.code >= 500) {
                        attempt++
                        lastError = "HTTP ${response.code}"
                        delay(1000L * attempt)
                        continue
                    }
                    val errorMsg = runCatching {
                        JSONObject(responseBody ?: "").optJSONObject("error")?.optString("message")
                    }.getOrNull()?.takeIf { it.isNotBlank() }
                        ?: "HTTP ${response.code}: ${response.message}"
                    return@withContext Result.failure(Exception(errorMsg))
                }

                if (responseBody == null) {
                    attempt++
                    continue
                }

                val content = JSONObject(responseBody)
                    .optJSONArray("choices")
                    ?.optJSONObject(0)
                    ?.optJSONObject("message")
                    ?.optString("content")
                    ?.trim()

                if (!content.isNullOrBlank()) {
                    return@withContext AiPlaylistParser.parse(content, count)
                }
                lastError = "Empty AI response"
            } catch (e: Exception) {
                if (attempt == maxRetries - 1) {
                    return@withContext Result.failure(e)
                }
                lastError = e.message
            }
            attempt++
            delay(1000L * attempt)
        }
        Result.failure(Exception(lastError ?: "Max retries exceeded"))
    }
}
