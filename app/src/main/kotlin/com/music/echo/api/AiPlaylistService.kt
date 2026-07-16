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
 *    Worker `/ai` route (Workers AI relay, no Authorization header). Treated as a PROBE: anything
 *    that is NOT a usable OpenAI-shape completion (HTTP 4xx incl. a not-yet-deployed 404, or a 200
 *    whose body has no choices[0].message.content — e.g. {"status":"invalid"}) is a FAST FAILURE
 *    and falls through to Pollinations immediately. Only a genuine 5xx (a deployed Worker hiccup)
 *    is retried. So an undeployed Worker costs ~one quick 404, never wasted retries; once the owner
 *    deploys /ai it becomes the reliable primary automatically, with no further app change.
 * 3. POLLINATIONS — public keyless endpoint text.pollinations.ai. It intermittently returns empty
 *    content or rate-limits, so it gets a bounded retry (POLLINATIONS_MAX_RETRIES) with short backoff
 *    before the chain gives up.
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

    /**
     * Several FREE Pollinations models, tried IN ORDER until one actually returns a usable playlist.
     * A single busy / rate-limited / empty model no longer sinks the whole request — the chain walks
     * to the next model. Only if EVERY model fails does the keyless chain give up (and the caller then
     * builds a non-AI playlist from search/radio). Order = most reliable first.
     */
    private val POLLINATIONS_MODELS = listOf("openai", "mistral", "llama", "deepseek")

    /** Modest per-endpoint retries for the keyless chain so the chained worst case stays bounded. */
    private const val KEYLESS_MAX_RETRIES = 2

    /**
     * Per-MODEL retries for Pollinations. Kept small (2) because we now try several models in turn, so
     * total worst-case = models × retries — still bounded, to respect the battery/heat budget.
     */
    private const val POLLINATIONS_MAX_RETRIES = 2

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

        val messages = toJsonArray(AiPlaylistPrompt.buildMessages(prompt, count))
        // Scale the token budget with the requested count (~80 tok/track + overhead) so a 50-song
        // request isn't truncated mid-JSON (the old fixed 2048 cut off large playlists → fewer songs).
        val maxTokens = (count * 80 + 512).coerceIn(1024, 8192)
        // The era the REQUEST implies gates the parser's year check (soft chain-of-verification: the
        // prompt makes the model commit to a year, this checks it). Null for requests with no era.
        val era = AiPlaylistParser.eraRange(prompt)

        runChain(
            messages = messages,
            maxTokens = maxTokens,
            provider = provider,
            apiKey = apiKey,
            baseUrl = baseUrl,
            model = model,
            maxRetries = maxRetries,
        ) { content -> AiPlaylistParser.parse(content, count, era) }
    }

    /**
     * Asks the AI how to EDIT an existing playlist: which positions to remove and which tracks to add.
     * Runs the exact same provider chain as [generate] (user key → Aura Worker → free Pollinations
     * models, with the same 429/408/425/5xx + Retry-After retries), so the modify feature inherits the
     * "never says IA ocupada for a transient blip" behavior for free.
     *
     * [currentTracks] is the playlist in display order. Only positional indices cross the wire — see
     * [AiPlaylistPrompt.buildModifyMessages]. Unlike [generate] there is NO non-AI fallback: if the
     * chain fails the caller must no-op, because a wrong edit to the user's playlist is worse than none.
     */
    suspend fun modify(
        currentTracks: List<TrackQuery>,
        prompt: String,
        provider: String,
        apiKey: String,
        baseUrl: String,
        model: String,
        maxRetries: Int = 3,
    ): Result<AiPlaylistEdit> = withContext(Dispatchers.IO) {
        if (prompt.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Prompt is empty"))
        }
        if (currentTracks.isEmpty()) {
            return@withContext Result.failure(IllegalArgumentException("Playlist is empty"))
        }

        val messages = toJsonArray(AiPlaylistPrompt.buildModifyMessages(currentTracks, prompt))
        // Only the tracks actually shown to the model can be referenced by an index, so the parser
        // validates against the SAME capped count the prompt printed.
        val visibleCount = currentTracks.size.coerceAtMost(AiPlaylistPrompt.MAX_MODIFY_TRACKS)
        // The reply is small (a few indices + a few additions); the playlist itself is INPUT tokens.
        val maxTokens = (visibleCount * 8 + 1024).coerceIn(1024, 8192)

        runChain(
            messages = messages,
            maxTokens = maxTokens,
            provider = provider,
            apiKey = apiKey,
            baseUrl = baseUrl,
            model = model,
            maxRetries = maxRetries,
        ) { content -> AiPlaylistParser.parseEdit(content, visibleCount) }
    }

    private fun toJsonArray(messages: List<ChatMessage>): JSONArray = JSONArray().apply {
        messages.forEach { message ->
            put(
                JSONObject().apply {
                    put("role", message.role)
                    put("content", message.content)
                },
            )
        }
    }

    /**
     * THE provider chain, shared by [generate] and [modify] so the two can never drift apart:
     * user key → Aura Worker probe → free Pollinations models. [parse] turns a successful completion's
     * text into the caller's result type; everything else (ordering, retries, fast-fail rules) is
     * identical for both features.
     */
    private suspend fun <T> runChain(
        messages: JSONArray,
        maxTokens: Int,
        provider: String,
        apiKey: String,
        baseUrl: String,
        model: String,
        maxRetries: Int,
        parse: (String) -> Result<T>,
    ): Result<T> {
        // 1. User key override: their provider/baseUrl/model, exactly the pre-existing behavior.
        if (apiKey.isNotBlank()) {
            if (provider in UNSUPPORTED_PROVIDERS) {
                return Result.failure(UnsupportedProviderException(provider))
            }
            return requestChatCompletion(
                url = baseUrl.ifBlank { DEFAULT_BASE_URL },
                apiKey = apiKey,
                model = model,
                messages = messages,
                maxTokens = maxTokens,
                parse = parse,
                maxRetries = maxRetries,
            )
        }

        // 2. Aura Worker (keyless primary). Probe-style: only a 5xx from a deployed Worker is retried;
        // a 4xx (incl. the current not-yet-deployed 404) or a 200 with no usable content fast-fails
        // so we fall through to Pollinations without burning retries on a route that isn't serving.
        val workerResult = requestChatCompletion(
            url = AURA_WORKER_URL,
            apiKey = null,
            model = AURA_WORKER_MODEL,
            messages = messages,
            maxTokens = maxTokens,
            parse = parse,
            maxRetries = KEYLESS_MAX_RETRIES,
            retryEmptyContent = false,
        )
        if (workerResult.isSuccess) return workerResult

        // 3. Pollinations fallback (keyless) — try SEVERAL free models in turn until one returns a
        // usable playlist. A busy/rate-limited/empty model just advances to the next; only if EVERY
        // model fails does the keyless chain give up. For generation the caller (AiPlaylistGenerator)
        // then builds a non-AI playlist from search/radio, so the feature never dead-ends on
        // "servicio no disponible"; for modification the caller no-ops instead (never guesses an edit).
        var lastFailure: Throwable? = null
        for (pollModel in POLLINATIONS_MODELS) {
            val r = requestChatCompletion(
                url = POLLINATIONS_URL,
                apiKey = null,
                model = pollModel,
                messages = messages,
                maxTokens = maxTokens,
                parse = parse,
                maxRetries = POLLINATIONS_MAX_RETRIES,
                retryEmptyContent = true,
            )
            if (r.isSuccess) return r
            lastFailure = r.exceptionOrNull()
        }

        return Result.failure(AiServiceUnavailableException(lastFailure))
    }

    /**
     * One OpenAI-compatible chat-completion round trip against [url] with 5xx retries/backoff.
     * [apiKey] null/blank → no Authorization header (keyless endpoints). Returns the parsed spec.
     *
     * [retryEmptyContent] controls how a "reachable but useless" reply is treated (a 200 with an
     * empty body, or a 200 whose choices[0].message.content is missing/blank — e.g. an undeployed
     * Worker stub like {"status":"invalid"}):
     *  - true  (Pollinations): keep retrying up to [maxRetries], it flakes transiently.
     *  - false (Aura Worker probe): fast-fail immediately so the chain falls through without wasting
     *    retries on a route that isn't actually serving completions.
     * A genuine 5xx is retried regardless of this flag, since it signals a deployed-but-hiccuping
     * endpoint; a 4xx (incl. the not-yet-deployed 404) always fails fast as before.
     */
    private suspend fun <T> requestChatCompletion(
        url: String,
        apiKey: String?,
        model: String,
        messages: JSONArray,
        maxTokens: Int,
        parse: (String) -> Result<T>,
        maxRetries: Int,
        retryEmptyContent: Boolean = true,
    ): Result<T> {
        val requestJson = JSONObject().apply {
            if (model.isNotBlank()) put("model", model)
            put("messages", messages)
            // 0.7: enough variety for song picks, but tighter than 0.8 so the strict-JSON output drifts less.
            put("temperature", 0.7)
            put("max_tokens", maxTokens)
            // Keyless fallbacks (Pollinations "openai", some Workers AI models) route to REASONING models
            // that otherwise burn the whole token budget on hidden reasoning and return null content
            // (finish_reason "length") → "servicio ocupado". "low" makes them emit the JSON in ~5s.
            // KEYLESS CHAIN ONLY (apiKey null/blank): some BYO-key providers hard-reject unknown
            // fields, so the user-key request body stays byte-identical to the pre-existing one.
            if (apiKey.isNullOrBlank()) {
                put("reasoning_effort", "low")
            }
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
                    // Retry rate-limits (429) and 408/425 too, not only 5xx. HTTP 429 is Pollinations'
                    // single most common failure; the old `>= 500` check treated it as a hard error and
                    // returned immediately with zero retries — the main cause of the "IA ocupada / no
                    // disponible" the user keeps seeing. Honor Retry-After when the server sends it
                    // (capped at 10s so a bad value can't hang the dialog), else jittered backoff.
                    if (response.code == 429 || response.code == 408 || response.code == 425 || response.code >= 500) {
                        attempt++
                        lastError = "HTTP ${response.code}"
                        val retryAfterMs = response.header("Retry-After")?.toLongOrNull()?.times(1000L)
                        val backoffMs = 1000L * attempt + (0L..500L).random()
                        delay((retryAfterMs ?: backoffMs).coerceAtMost(10_000L))
                        continue
                    }
                    val errorMsg = runCatching {
                        JSONObject(responseBody ?: "").optJSONObject("error")?.optString("message")
                    }.getOrNull()?.takeIf { it.isNotBlank() }
                        ?: "HTTP ${response.code}: ${response.message}"
                    return Result.failure(Exception(errorMsg))
                }

                if (responseBody == null) {
                    if (!retryEmptyContent) {
                        return Result.failure(Exception("Empty AI response body"))
                    }
                    attempt++
                    lastError = "Empty AI response body"
                    delay(1000L * attempt)
                    continue
                }

                val message = JSONObject(responseBody)
                    .optJSONArray("choices")
                    ?.optJSONObject(0)
                    ?.optJSONObject("message")

                val content = message?.optString("content")?.trim()

                if (!content.isNullOrBlank()) {
                    return parse(content)
                }

                // Reasoning models sometimes leave content null but emit the JSON inside "reasoning"
                // (finish_reason "length"). AiPlaylistParser extracts the {...} substring, so passing
                // the reasoning text as a fallback content lets the request still recover. Return
                // early ONLY when the parse SUCCEEDS — reasoning text is usually truncated prose, and
                // failing the whole request on it would kill the remaining retries; fall through to
                // "Empty AI response" instead so the retry loop keeps going.
                val reasoning = message?.optString("reasoning")?.trim()
                if (!reasoning.isNullOrBlank()) {
                    val parsed = parse(reasoning)
                    if (parsed.isSuccess) {
                        return parsed
                    }
                }

                // Reachable but no usable content. For the Worker probe (retryEmptyContent=false)
                // this means "not really serving completions" (e.g. an undeployed {"status":"invalid"}
                // stub) → fail fast and fall through to Pollinations instead of retrying a dead route.
                if (!retryEmptyContent) {
                    return Result.failure(Exception("Empty AI response"))
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
