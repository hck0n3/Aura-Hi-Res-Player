package iad1tya.echo.music.api

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Parses an AI chat completion's text content into an [AiPlaylistSpec]. Tolerant of markdown fences
 * and surrounding prose (mirrors [OpenRouterService]). Pure → unit-testable; uses
 * kotlinx.serialization (works on the JVM, unlike Android's stubbed `org.json`).
 */
object AiPlaylistParser {

    @Serializable
    private data class Dto(val name: String = "", val tracks: List<TrackDto> = emptyList())

    @Serializable
    private data class TrackDto(val title: String = "", val artist: String = "")

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    fun parse(content: String, expectedCount: Int): Result<AiPlaylistSpec> {
        val dto = candidateJsons(content).firstNotNullOfOrNull { candidate ->
            runCatching { json.decodeFromString<Dto>(candidate) }.getOrNull()
        } ?: return Result.failure(IllegalArgumentException("AI response was not valid JSON"))

        val tracks = dto.tracks
            .map { TrackQuery(it.title.trim(), it.artist.trim()) }
            .filter { it.title.isNotBlank() }
            .distinctBy { it.title.lowercase() to it.artist.lowercase() }
            .let { if (expectedCount > 0) it.take(expectedCount) else it }

        if (tracks.isEmpty()) {
            return Result.failure(IllegalArgumentException("AI returned no usable tracks"))
        }
        return Result.success(AiPlaylistSpec(name = dto.name.trim(), tracks = tracks))
    }

    /** Ordered, de-duplicated JSON-object candidates: direct, fence-stripped, then between braces. */
    private fun candidateJsons(content: String): List<String> {
        val candidates = ArrayList<String>(3)
        val trimmed = content.trim()
        if (trimmed.startsWith("{")) candidates += trimmed
        val noFence = trimmed
            .removePrefix("```json")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()
        if (noFence.startsWith("{")) candidates += noFence
        val start = content.indexOf('{')
        val end = content.lastIndexOf('}')
        if (start != -1 && end > start) candidates += content.substring(start, end + 1)
        return candidates.distinct()
    }
}
