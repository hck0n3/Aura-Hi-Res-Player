

package iad1tya.echo.music.lyrics

import android.content.Context
import iad1tya.echo.music.api.DeepLService
import iad1tya.echo.music.api.MistralService
import iad1tya.echo.music.api.OpenRouterService
import iad1tya.echo.music.api.OpenRouterStreamingService
import iad1tya.echo.music.constants.LanguageCodeToName
import iad1tya.echo.music.db.MusicDatabase
import iad1tya.echo.music.db.entities.LyricsEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import timber.log.Timber
import java.net.URLEncoder
import java.util.Locale
import java.util.concurrent.TimeUnit

object LyricsTranslationHelper {
    private val _status = MutableStateFlow<TranslationStatus>(TranslationStatus.Idle)
    val status: StateFlow<TranslationStatus> = _status.asStateFlow()

    // FREE keyless translation (no user API key). There is NO embedded OpenRouter key in the app:
    // AiPlaylistService's "free" path is fully KEYLESS (no Authorization header) against the public
    // OpenAI-compatible Pollinations endpoint, trying several free models in turn. We reuse the same
    // endpoint + model cascade here so lyric translation works without the user configuring any key.
    private const val FREE_KEYLESS_BASE_URL = "https://text.pollinations.ai/openai"
    private val FREE_KEYLESS_MODELS = listOf("openai", "mistral", "llama", "deepseek")

    // FREE, reliable, keyless lyric translation via Google Translate's public web endpoint.
    // GET https://translate.googleapis.com/translate_a/single?client=gtx&sl=auto&tl=<code>&dt=t&q=<text>
    // returns HTTP 200 with NO API key. It preserves \n and translates line-by-line, which is ideal for
    // lyrics. This is the primary keyless path; the old Pollinations AI cascade is only a fallback.
    private const val GOOGLE_TRANSLATE_URL = "https://translate.googleapis.com/translate_a/single"

    // Overall budget for the whole keyless path so the UI can never hang on "Translating" forever.
    private const val KEYLESS_TRANSLATE_TIMEOUT_MS = 30_000L

    // Short-timeout client for the Google Translate endpoint (must be fast; never block the UI).
    private val googleTranslateClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .build()
    }

    /**
     * FREE, keyless lyric translation via Google Translate's public endpoint.
     * Joins [lines] with '\n', URL-encodes them, and asks Google to translate to [targetLang]
     * (a 2-letter code such as "es"/"en"/"pt"). Google keeps the '\n' boundaries, so we reconstruct
     * the translated text by concatenating each segment's translated part and re-split on '\n'.
     *
     * Returns exactly [lines].size translated strings (padded/truncated as needed), or null on ANY
     * failure (empty input, network error, non-200, parse error). Never throws — safe for the keyless
     * branch, where null simply advances to the AI cascade fallback.
     */
    /** One free Google-Translate request for a single text; returns the concatenated translation or null. */
    private fun googleTranslateOnce(text: String, tl: String): String? {
        if (text.isBlank()) return ""
        return try {
            val encoded = URLEncoder.encode(text, "UTF-8")
            val url = "$GOOGLE_TRANSLATE_URL?client=gtx&sl=auto&tl=$tl&dt=t&q=$encoded"
            val request = Request.Builder().url(url).header("User-Agent", "Mozilla/5.0").get().build()
            googleTranslateClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val body = response.body?.string()
                if (body.isNullOrBlank()) return null
                // Shape: [[[ "<translated seg>", "<orig seg>", ...], ...], null, "<detected src>", ...]
                val segments = JSONArray(body).optJSONArray(0) ?: return null
                val b = StringBuilder()
                for (i in 0 until segments.length()) b.append(segments.optJSONArray(i)?.optString(0, "") ?: "")
                b.toString().ifBlank { null }
            }
        } catch (e: Exception) {
            Timber.w(e, "Google Translate single call failed")
            null
        }
    }

    private suspend fun googleTranslateFree(
        lines: List<String>,
        targetLang: String,
    ): List<String>? = withContext(Dispatchers.IO) {
        if (lines.isEmpty()) return@withContext null
        // Google wants a 2-letter code; strip any region suffix (es-ES -> es) just in case.
        val tl = targetLang.substringBefore('-').lowercase().ifBlank { return@withContext null }

        // Fast path: ONE request with the lines joined by '\n' (Google preserves the newlines). Accept it
        // ONLY if the split lines up 1:1 with the input — otherwise a merged/split line would shift every
        // subsequent line (blind end-padding is exactly that bug).
        val batch = googleTranslateOnce(lines.joinToString("\n"), tl)?.split("\n")
        if (batch != null && batch.size == lines.size) return@withContext batch

        // Alignment fallback: translate each line INDIVIDUALLY → guaranteed 1:1 mapping, no line-shift.
        // Bounded concurrency (thermal/battery gate); a line that fails keeps its original text.
        val sem = Semaphore(6)
        val perLine = coroutineScope {
            lines.map { line ->
                async {
                    if (line.isBlank()) "" else (sem.withPermit { googleTranslateOnce(line, tl) } ?: line)
                }
            }.awaitAll()
        }
        // Every non-blank line came back unchanged/failed → treat as failure so the caller can fall back.
        if (lines.indices.all { lines[it].isBlank() || perLine[it] == lines[it] }) return@withContext null
        return@withContext perLine
    }

    
    private val _hasActiveTranslations = MutableStateFlow(false)
    val hasActiveTranslations: StateFlow<Boolean> = _hasActiveTranslations.asStateFlow()

    private val _manualTrigger = MutableSharedFlow<Unit>(
        extraBufferCapacity = 1,
        onBufferOverflow = kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST,
    )
    val manualTrigger: SharedFlow<Unit> = _manualTrigger.asSharedFlow()

    private val _clearTranslationsTrigger = MutableSharedFlow<Unit>(
        extraBufferCapacity = 1,
        onBufferOverflow = kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST,
    )
    val clearTranslationsTrigger: SharedFlow<Unit> = _clearTranslationsTrigger.asSharedFlow()

    private val _translationSaved = MutableSharedFlow<Unit>(
        extraBufferCapacity = 1,
        onBufferOverflow = kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST,
    )
    val translationSaved: SharedFlow<Unit> = _translationSaved.asSharedFlow()

    private var translationJob: Job? = null
    @Volatile
    private var isCompositionActive = true


    private val translationCache = java.util.concurrent.ConcurrentHashMap<String, List<String>>()

    private fun getCacheKey(lyricsText: String, mode: String, language: String): String =
        "${lyricsText.hashCode()}_${mode}_$language"

    
    private fun tryParsePartialTranslation(content: String, expectedCount: Int): List<String> {
        val startIdx = content.indexOf('[')
        if (startIdx == -1) return emptyList()

        val result = mutableListOf<String>()
        var pos = startIdx + 1
        var inString = false
        var escaping = false
        val currentString = StringBuilder()

        while (pos < content.length && result.size < expectedCount) {
            val char = content[pos]

            when {
                escaping -> {
                    currentString.append(char)
                    escaping = false
                }
                char == '\\' && inString -> {
                    currentString.append(char)
                    escaping = true
                }
                char == '"' -> {
                    if (inString) {
                        result.add(currentString.toString())
                        currentString.clear()
                        inString = false
                    } else {
                        inString = true
                    }
                }
                inString -> {
                    currentString.append(char)
                }
                char == ']' -> {
                    break
                }
            }
            pos++
        }

        return result
    }

    fun getCachedTranslations(lyrics: List<LyricsEntry>, mode: String, language: String): List<String>? {
        val lyricsText = lyrics.filter { it.text.isNotBlank() }.joinToString("\n") { it.text }
        val key = getCacheKey(lyricsText, mode, language)
        return translationCache[key]
    }

    fun applyCachedTranslations(lyrics: List<LyricsEntry>, mode: String, language: String): Boolean {
        val cached = getCachedTranslations(lyrics, mode, language) ?: return false
        val nonEmptyEntries = lyrics.mapIndexedNotNull { index, entry ->
            if (entry.text.isNotBlank()) index to entry else null
        }

        if (cached.size >= nonEmptyEntries.size) {
            nonEmptyEntries.forEachIndexed { idx, (originalIndex, _) ->
                lyrics[originalIndex].translatedTextFlow.value = cached[idx]
            }
            return true
        }
        return false
    }

    fun triggerManualTranslation() {
        _manualTrigger.tryEmit(Unit)
    }

    fun triggerClearTranslations() {
        _hasActiveTranslations.value = false
        _clearTranslationsTrigger.tryEmit(Unit)
    }

    fun hasTranslations(lyricsEntity: LyricsEntity?): Boolean = !lyricsEntity?.translatedLyrics.isNullOrBlank()

    fun clearTranslations(lyricsEntity: LyricsEntity): LyricsEntity =
        lyricsEntity.copy(
            translatedLyrics = "",
            translationLanguage = "",
            translationMode = "",
        )

    fun resetStatus() {
        _status.value = TranslationStatus.Idle
    }

    fun clearCache() {
        translationCache.clear()
    }

    fun setCompositionActive(active: Boolean) {
        isCompositionActive = active
    }

    fun cancelTranslation() {
        isCompositionActive = false
        translationJob?.cancel()
        translationJob = null
    }

    
    fun loadTranslationsFromDatabase(
        lyrics: List<LyricsEntry>,
        lyricsEntity: LyricsEntity?,
        targetLanguage: String,
        mode: String,
    ) {
        
        lyrics.forEach { it.translatedTextFlow.value = null }

        
        if (lyricsEntity?.translatedLyrics.isNullOrBlank()) {
            _hasActiveTranslations.value = false
            return
        }
        if (lyricsEntity.translationLanguage != targetLanguage) {
            _hasActiveTranslations.value = false
            return
        }
        if (lyricsEntity.translationMode != mode) {
            _hasActiveTranslations.value = false
            return
        }

        val translatedLines = lyricsEntity.translatedLyrics.lines()
        val nonEmptyEntries = lyrics.mapIndexedNotNull { index, entry ->
            if (entry.text.isNotBlank()) index to entry else null
        }

        nonEmptyEntries.forEachIndexed { idx, (originalIndex, _) ->
            if (idx < translatedLines.size) {
                lyrics[originalIndex].translatedTextFlow.value = translatedLines[idx]
            }
        }

        
        
        
        val lyricsText = lyrics.filter { it.text.isNotBlank() }.joinToString("\n") { it.text }
        val cacheKey = getCacheKey(lyricsText, mode, targetLanguage)
        translationCache[cacheKey] = translatedLines
        _hasActiveTranslations.value = true
    }

    fun translateLyrics(
        lyrics: List<LyricsEntry>,
        targetLanguage: String,
        apiKey: String,
        baseUrl: String,
        model: String,
        mode: String,
        scope: CoroutineScope,
        context: Context,
        provider: String = "OpenRouter",
        deeplApiKey: String = "",
        deeplFormality: String = "default",
        useStreaming: Boolean = true,
        songId: String = "",
        database: MusicDatabase? = null,
        keyless: Boolean = false,
    ) {
        translationJob?.cancel()
        _status.value = TranslationStatus.Translating

        
        lyrics.forEach { it.translatedTextFlow.value = null }

        translationJob = scope.launch(Dispatchers.IO) {
            try {
                
                val effectiveApiKey = if (provider == "DeepL") deeplApiKey else apiKey
                // keyless = FREE built-in AI (no user key needed): skip the "key required" abort and
                // route through the keyless endpoint/model cascade below. DeepL never runs keyless.
                if (!keyless && effectiveApiKey.isBlank()) {
                    _status.value = TranslationStatus.Error(context.getString(iad1tya.echo.music.R.string.ai_error_api_key_required))
                    return@launch
                }

                if (lyrics.isEmpty()) {
                    _status.value = TranslationStatus.Error(context.getString(iad1tya.echo.music.R.string.ai_error_no_lyrics))
                    return@launch
                }

                
                val nonEmptyEntries = lyrics.mapIndexedNotNull { index, entry ->
                    if (entry.text.isNotBlank()) index to entry else null
                }

                if (nonEmptyEntries.isEmpty()) {
                    _status.value = TranslationStatus.Error(context.getString(iad1tya.echo.music.R.string.ai_error_lyrics_empty))
                    return@launch
                }

                
                val fullText = nonEmptyEntries.joinToString("\n") { it.second.text }

                
                val cacheKey = getCacheKey(fullText, mode, targetLanguage)
                val cachedTranslations = translationCache[cacheKey]
                if (cachedTranslations != null && cachedTranslations.size >= nonEmptyEntries.size) {
                    
                    nonEmptyEntries.forEachIndexed { idx, (originalIndex, _) ->
                        if (idx < cachedTranslations.size) {
                            lyrics[originalIndex].translatedTextFlow.value = cachedTranslations[idx]
                        }
                    }
                    _hasActiveTranslations.value = true
                    _status.value = TranslationStatus.Success

                    
                    
                    if (songId.isNotBlank() && database != null) {
                        try {
                            val currentLyrics = database.lyrics(songId).first()
                            if (currentLyrics != null && currentLyrics.translatedLyrics.isNullOrBlank()) {
                                database.query {
                                    upsert(
                                        currentLyrics.copy(
                                            translatedLyrics = cachedTranslations.joinToString("\n"),
                                            translationLanguage = targetLanguage,
                                            translationMode = mode,
                                        ),
                                    )
                                }
                                _translationSaved.tryEmit(Unit)
                            }
                        } catch (e: Exception) {
                            Timber.e(e, "Failed to persist cached translations to database")
                        }
                    }

                    delay(3000)
                    if (_status.value is TranslationStatus.Success && isCompositionActive) {
                        _status.value = TranslationStatus.Idle
                    }
                    return@launch
                }

                
                if (targetLanguage.isBlank()) {
                    _status.value = TranslationStatus.Error(context.getString(iad1tya.echo.music.R.string.ai_error_language_required))
                    return@launch
                }

                
                val fullLanguageName = LanguageCodeToName[targetLanguage]
                    ?: try {
                        Locale.forLanguageTag(targetLanguage).displayLanguage.takeIf { it.isNotBlank() && it != targetLanguage }
                    } catch (e: Exception) { null }
                    ?: targetLanguage

                val result = if (provider == "DeepL") {
                    Timber.d("Using DeepL for translation")
                    DeepLService.translate(
                        text = fullText,
                        targetLanguage = targetLanguage,
                        apiKey = deeplApiKey,
                        formality = deeplFormality,
                    )
                } else if (provider == "Mistral") {
                    Timber.d("Using Mistral for translation")
                    MistralService.translate(
                        text = fullText,
                        targetLanguage = fullLanguageName,
                        apiKey = apiKey,
                        model = model,
                        mode = mode,
                    )
                } else if (keyless) {
                    // FREE keyless path. Google Translate's public endpoint (no key, HTTP 200) is
                    // reliable and fast, so it goes FIRST for standard translation. The old Pollinations
                    // AI cascade — which was DOWN (502) / not deployed (404) and hung on "Translating" —
                    // is now only a fallback (and still needed for Romanized/Transcribed, which Google's
                    // dt=t translation cannot do). An overall timeout guarantees the UI never hangs: if
                    // every provider stalls we surface a clear error card (no crash, no infinite spinner).
                    Timber.d("Using FREE keyless translation (Google Translate first)")
                    val standardTranslate = mode != "Romanized" && mode != "Transcribed"
                    withTimeoutOrNull(KEYLESS_TRANSLATE_TIMEOUT_MS) {
                        var freeResult: Result<List<String>> =
                            Result.failure(Exception("No free translation available"))

                        // 1) Google Translate FREE keyless endpoint (reliable, no key).
                        if (standardTranslate) {
                            val googleLines = googleTranslateFree(
                                lines = nonEmptyEntries.map { it.second.text },
                                targetLang = targetLanguage,
                            )
                            if (googleLines != null) {
                                Timber.d("Google Translate returned ${googleLines.size} lines")
                                freeResult = Result.success(googleLines)
                            }
                        }

                        // 2) Fallback: keyless AI cascade (Pollinations) — used only if Google failed
                        // or the mode needs AI. Mirrors AiPlaylistService's free path.
                        if (freeResult.isFailure) {
                            Timber.d("Falling back to keyless AI cascade")
                            for (freeModel in FREE_KEYLESS_MODELS) {
                                freeResult = OpenRouterService.translate(
                                    text = fullText,
                                    targetLanguage = fullLanguageName,
                                    apiKey = "",
                                    baseUrl = FREE_KEYLESS_BASE_URL,
                                    model = freeModel,
                                    mode = mode,
                                )
                                if (freeResult.isSuccess) break
                            }
                        }
                        freeResult
                    } ?: Result.failure(
                        Exception(context.getString(iad1tya.echo.music.R.string.ai_error_translation_failed)),
                    )
                } else if (useStreaming && provider != "Custom") {
                    Timber.d("Using streaming for translation with provider: $provider")
                    var translatedLines: List<String>? = null
                    var hasError = false
                    var errorMessage = ""
                    val contentAccumulator = StringBuilder()

                    OpenRouterStreamingService.streamTranslation(
                        text = fullText,
                        targetLanguage = fullLanguageName,
                        apiKey = apiKey,
                        baseUrl = baseUrl,
                        model = model,
                        mode = mode,
                    ).collect { chunk ->
                        Timber.v("Received streaming chunk: $chunk")
                        when (chunk) {
                            is OpenRouterStreamingService.StreamChunk.Content -> {
                                contentAccumulator.append(chunk.text)

                                val partialContent = contentAccumulator.toString()
                                val partialResult = tryParsePartialTranslation(partialContent, nonEmptyEntries.size)
                                if (partialResult.isNotEmpty()) {
                                    partialResult.forEachIndexed { idx, translation ->
                                        if (idx < nonEmptyEntries.size && translation.isNotBlank()) {
                                            val originalIndex = nonEmptyEntries[idx].first
                                            lyrics[originalIndex].translatedTextFlow.value = translation
                                        }
                                    }
                                    _status.value = TranslationStatus.Translating
                                }
                            }
                            is OpenRouterStreamingService.StreamChunk.Complete -> {
                                Timber.d("Streaming complete with ${chunk.translatedLines.size} lines")
                                translatedLines = chunk.translatedLines
                            }
                            is OpenRouterStreamingService.StreamChunk.Error -> {
                                Timber.e("Streaming error: ${chunk.message}")
                                hasError = true
                                errorMessage = chunk.message
                            }
                        }
                    }

                    Timber.d("Streaming collection complete. hasError=$hasError, translatedLines=${translatedLines?.size}")
                    if (hasError) {
                        Result.failure(Exception(errorMessage))
                    } else if (translatedLines != null) {
                        Result.success(translatedLines)
                    } else {
                        Result.failure(Exception("No translation received"))
                    }
                } else {
                    Timber.d("Using non-streaming for translation")
                    OpenRouterService.translate(
                        text = fullText,
                        targetLanguage = fullLanguageName,
                        apiKey = apiKey,
                        baseUrl = baseUrl,
                        model = model,
                        mode = mode,
                    )
                }

                result.onSuccess { translatedLines ->
                    if (!isCompositionActive) {
                        return@onSuccess
                    }

                    
                    val cacheKey2 = getCacheKey(fullText, mode, targetLanguage)
                    translationCache[cacheKey2] = translatedLines

                    
                    if (songId.isNotBlank() && database != null) {
                        scope.launch(Dispatchers.IO) {
                            try {
                                val currentLyrics = database.lyrics(songId).first()
                                if (currentLyrics != null) {
                                    database.query {
                                        upsert(
                                            currentLyrics.copy(
                                                translatedLyrics = translatedLines.joinToString("\n"),
                                                translationLanguage = targetLanguage,
                                                translationMode = mode,
                                            ),
                                        )
                                    }
                                    
                                    _translationSaved.tryEmit(Unit)
                                }
                            } catch (e: Exception) {
                                timber.log.Timber.e(e, "Failed to save translated lyrics to database")
                            }
                        }
                    }

                    
                    val expectedCount = nonEmptyEntries.size

                    when {
                        translatedLines.size >= expectedCount -> {
                            nonEmptyEntries.forEachIndexed { idx, (originalIndex, _) ->
                                lyrics[originalIndex].translatedTextFlow.value = translatedLines[idx]
                            }
                            _hasActiveTranslations.value = true
                            _status.value = TranslationStatus.Success
                        }
                        translatedLines.size < expectedCount -> {
                            translatedLines.forEachIndexed { idx, translation ->
                                if (idx < nonEmptyEntries.size) {
                                    val originalIndex = nonEmptyEntries[idx].first
                                    lyrics[originalIndex].translatedTextFlow.value = translation
                                }
                            }
                            _hasActiveTranslations.value = true
                            _status.value = TranslationStatus.Success
                        }
                        else -> {
                            _status.value = TranslationStatus.Error(context.getString(iad1tya.echo.music.R.string.ai_error_unexpected))
                        }
                    }

                    
                    delay(3000)
                    if (_status.value is TranslationStatus.Success && isCompositionActive) {
                        _status.value = TranslationStatus.Idle
                    }
                }.onFailure { error ->
                    if (!isCompositionActive) {
                        return@onFailure
                    }

                    val errorMessage = error.message ?: context.getString(iad1tya.echo.music.R.string.ai_error_unknown)
                    _status.value = TranslationStatus.Error(errorMessage)
                }
            } catch (e: Exception) {
                if (e !is kotlinx.coroutines.CancellationException && isCompositionActive) {
                    val errorMessage = e.message ?: context.getString(iad1tya.echo.music.R.string.ai_error_translation_failed)
                    _status.value = TranslationStatus.Error(errorMessage)
                }
            }
        }
    }

    sealed class TranslationStatus {
        data object Idle : TranslationStatus()
        data object Translating : TranslationStatus()
        data object Success : TranslationStatus()
        data class Error(val message: String) : TranslationStatus()
    }
}
