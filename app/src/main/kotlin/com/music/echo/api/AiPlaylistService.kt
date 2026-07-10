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
 * Provider chain (in priority order):
 * 1. USER KEY OVERRIDE — if the user configured an API key, their provider/baseUrl/model are used
 *    exactly as before (BYO key, full control).
 * 2. AURA WORKER — with no key, the same OpenAI-shape body is POSTed to the owner's Cloudflare
 *    Worker `/ai` route (Workers AI relay, no Authorization header).
 * 3. POLLINATIONS — if the Worker fails (non-2xx after retries, network error, or unparseable
 *    reply), the request falls back to the public keyless endpoint text.pollinations.ai.
 * If every keyless endpoint fails, [AiServiceUnavailableException] is returned so the UI can show
 * a friendly "try again" message instead of asking for an API key.
 *
 * DeepL (not a chat API) and Claude (different `/v1/messages` schema) are intentionally unsupported
 * for the BYO-key path; the caller surfaces a "pick a chat provider" message.
 */
object AiPlaylistService {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val JSON = "application/json; charset=utf-8".toMediaType()

    private val UNSUPPORTED_PROVIDERS = setOf("DeepL", "Claude")

    private const val DEFAULT_BASE_URL = "https://openrouter.ai/api/v1/chat/completions"

    /** Owner-hosted Workers AI relay: keyless primary. Shares the license Worker (routes /verify and /demo untouched). */
    private const val AURA_WORKER_URL = "https://round-math-d64e.toberto4000.workers.dev/ai"

    /** Suggested model for the Worker; the Worker may ignore/override it server-side. */
    private const val AURA_WORKER_MODEL = "@cf/meta/llama-3.1-8b-instruct"

    /** Public keyless OpenAI-compatible endpoint, used when the Aura Worker is unavailable. */
    private const val POLLINATIONS_URL = "https://text.pollinations.ai/openai"
    private const val POLLINATIONS_MODEL = "openai"

    /** Modest per-endpoint retries for the keyless chain so the chained worst case stays bounded. */
    private const val KEYLESS_MAX_RETRIES = 2

    class UnsupportedProviderException(val providerName: String) :
        Exception("Provider not supported for AI playlists: $providerName")

    /** Kept for compatibility; no longer reachable from [generate] (blank key now uses the keyless chain). */
    class MissingApiKeyException : Exception("AI API key is not set")

    /** Every keyless endpoint (Aura Worker + fallback) failed; the user should simply retry later. */
    class AiServiceUnavailableException(cause: Throwable? = null) :
        Exception("Keyless AI endpoints are unavailable", cause)

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
        // Scale the token budget with the requested count (~80 tok/track + overhead) so a 50-song
        // request isn't truncated mid-JSON (the old fixed 2048 cut off large playlists → fewer songs).
        val maxTokens = (count * 80 + 512).coerceIn(1024, 8192)

        // 1. User key override: their provider/baseUrl/model, exactly the pre-existing behavior.
        if (apiKey.isNotBlank()) {
            if (provider in UNSUPPORTED_PROVIDERS) {
                return@withContext Result.failure(UnsupportedProviderException(provider))
            }
            return@withContext requestChatCompletion(
                url = baseUrl.ifBlank { DEFAULT_BASE_URL },
                apiKey = apiKey,
                model = model,
                messages = messages,
                maxTokens = maxTokens,
                count = count,
                maxRetries = maxRetries,
            )
        }

        // 2. Aura Worker (keyless primary).
        val workerResult = requestChatCompletion(
            url = AURA_WORKER_URL,
            apiKey = null,
            model = AURA_WORKER_MODEL,
            messages = messages,
            maxTokens = maxTokens,
            count = count,
            maxRetries = KEYLESS_MAX_RETRIES,
        )
        if (workerResult.isSuccess) return@withContext workerResult

        // 3. Pollinations fallback (keyless).
        val fallbackResult = requestChatCompletion(
            url = POLLINATIONS_URL,
            apiKey = null,
            model = POLLINATIONS_MODEL,
            messages = messages,
            maxTokens = maxTokens,
            count = count,
            maxRetries = KEYLESS_MAX_RETRIES,
        )
        if (fallbackResult.isSuccess) return@withContext fallbackResult

        Result.failure(AiServiceUnavailableException(fallbackResult.exceptionOrNull()))
    }

    /**
     * One OpenAI-compatible chat-completion round trip against [url] with 5xx retries/backoff.
     * [apiKey] null/blank → no Authorization header (keyless endpoints). Returns the parsed spec.
     */
    private suspend fun requestChatCompletion(
        url: String,
        apiKey: String?,
        model: String,
        messages: JSONArray,
        maxTokens: Int,
        count: Int,
        maxRetries: Int,
    ): Result<AiPlaylistSpec> {
        val requestJson = JSONObject().apply {
            if (model.isNotBlank()) put("model", model)
            put("messages", messages)
            // 0.7: enough variety for song picks, but tighter than 0.8 so the strict-JSON output drifts less.
            put("temperature", 0.7)
            put("max_tokens", maxTokens)
            // Keyless fallbacks (Pollinations "openai", some Workers AI models) route to REASONING models
            // that otherwise burn the whole token budget on hidden reasoning and return null content
            // (finish_reason "length") → "servicio ocupado". "low" makes them emit the JSON in ~5s.
            // Harmless for non-reasoning models and BYO-key OpenAI-compatible providers ignore unknown fields.
            put("reasoning_effort", "low")
        }

        var attempt = 0
        var lastError: String? = null
        while (attempt < maxRetries) {
            try {
                val builder = Request.Builder()
                    .url(url)
                    .addHeader("Content-Type", "application/json")
                    .addHeader("HTTP-Referer", "https://github.com/EchoMusicApp/Echo-Music")
                    .addHeader("X-Title", "echomusic")
                    .post(requestJson.toString().toRequestBody(JSON))
                if (!apiKey.isNullOrBlank()) {
                    builder.addHeader("Authorization", "Bearer ${apiKey.trim()}")
                }

                val response = client.newCall(builder.build()).execute()
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
                    return Result.failure(Exception(errorMsg))
                }

                if (responseBody == null) {
                    attempt++
                    continue
                }

                val message = JSONObject(responseBody)
                    .optJSONArray("choices")
                    ?.optJSONObject(0)
                    ?.optJSONObject("message")

                val content = message?.optString("content")?.trim()

                if (!content.isNullOrBlank()) {
                    return AiPlaylistParser.parse(content, count)
                }

                // Reasoning models sometimes leave content null but emit the JSON inside "reasoning"
                // (finish_reason "length"). AiPlaylistParser extracts the {...} substring, so passing
                // the reasoning text as a fallback content lets the request still recover.
                val reasoning = message?.optString("reasoning")?.trim()
                if (!reasoning.isNullOrBlank()) {
                    return AiPlaylistParser.parse(reasoning, count)
                }

                lastError = "Empty AI response"
            } catch (e: Exception) {
                if (attempt == maxRetries - 1) {
                    return Result.failure(e)
                }
                lastError = e.message
            }
            attempt++
            delay(1000L * attempt)
        }
        return Result.failure(Exception(lastError ?: "Max retries exceeded"))
    }
}
