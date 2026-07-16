package iad1tya.echo.music.api

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive

/**
 * Parses an AI chat completion's text content into an [AiPlaylistSpec] or an [AiPlaylistEdit].
 * Tolerant of markdown fences and surrounding prose (mirrors [OpenRouterService]). Pure →
 * unit-testable; uses kotlinx.serialization (works on the JVM, unlike Android's stubbed `org.json`).
 */
object AiPlaylistParser {

    @Serializable
    private data class Dto(val name: String = "", val tracks: List<TrackDto> = emptyList())

    /**
     * [year] = original release year the model committed to (see [AiPlaylistPrompt]). Nullable: a
     * model that omits it is NOT punished (the existing JSON tolerance), it just can't be year-checked.
     */
    @Serializable
    private data class TrackDto(
        val title: String = "",
        val artist: String = "",
        val year: Int? = null,
    )

    /** `remove` is read as raw JSON so both `[1,2]` and `["1","2"]` decode; see [parseEdit]. */
    @Serializable
    private data class EditDto(
        val remove: List<JsonElement> = emptyList(),
        val additions: List<TrackDto> = emptyList(),
    )

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    /**
     * [eraRange] (from [eraRange]) drops tracks whose self-reported year contradicts a request that
     * asked for a decade/era — a soft chain-of-verification: the model commits to a year, we check it.
     * Tracks with no year survive, so this can only bite when the model was explicit AND wrong; the
     * caller over-generates ~1.5× and tops up, which absorbs the loss.
     */
    fun parse(
        content: String,
        expectedCount: Int,
        eraRange: IntRange? = null,
    ): Result<AiPlaylistSpec> {
        val dto = candidateJsons(content).firstNotNullOfOrNull { candidate ->
            runCatching { json.decodeFromString<Dto>(candidate) }.getOrNull()
        } ?: return Result.failure(IllegalArgumentException("AI response was not valid JSON"))

        val tracks = dto.tracks
            .filter { eraRange == null || it.year == null || it.year in eraRange }
            .map { TrackQuery(it.title.trim(), it.artist.trim()) }
            .filter { it.title.isNotBlank() }
            .distinctBy { it.title.lowercase() to it.artist.lowercase() }
            .let { if (expectedCount > 0) it.take(expectedCount) else it }

        if (tracks.isEmpty()) {
            return Result.failure(IllegalArgumentException("AI returned no usable tracks"))
        }
        return Result.success(AiPlaylistSpec(name = dto.name.trim(), tracks = tracks))
    }

    /**
     * Parses an edit reply `{"remove":[1,3], "additions":[{"title","artist"}]}`.
     *
     * The model answers with the 1-BASED positions [AiPlaylistPrompt.buildModifyMessages] printed;
     * they are normalized to 0-based here and validated against [trackCount], so anything
     * hallucinated (0, 999, a title, a duplicate) is simply dropped rather than resolving to some
     * unrelated row. No database id is ever part of this exchange.
     *
     * Failure (invalid JSON, or an edit that would change nothing) → the caller no-ops. A wrong edit
     * is worse than no edit, so there is deliberately no "best effort" guess here.
     */
    fun parseEdit(content: String, trackCount: Int): Result<AiPlaylistEdit> {
        val dto = candidateJsons(content).firstNotNullOfOrNull { candidate ->
            runCatching { json.decodeFromString<EditDto>(candidate) }.getOrNull()
        } ?: return Result.failure(IllegalArgumentException("AI response was not valid JSON"))

        val removeIndices = dto.remove
            .mapNotNull { (it as? JsonPrimitive)?.content?.trim()?.toIntOrNull() }
            .map { it - 1 }
            .filter { it in 0 until trackCount }
            .distinct()
            .sorted()

        val additions = dto.additions
            .map { TrackQuery(it.title.trim(), it.artist.trim()) }
            .filter { it.title.isNotBlank() }
            .distinctBy { it.title.lowercase() to it.artist.lowercase() }

        if (removeIndices.isEmpty() && additions.isEmpty()) {
            return Result.failure(IllegalArgumentException("AI returned no usable edit"))
        }
        return Result.success(AiPlaylistEdit(removeIndices = removeIndices, additions = additions))
    }

    /**
     * One decade, or a run of them ("70 y 80", "60, 70 y 80", "70 a los 90"). A connector run only
     * ever WIDENS the span (see [eraRange]), so it is read greedily.
     */
    private const val DECADE_RUN =
        """(?:19|20)?\d0(?:\s*(?:,|y|hasta|al|a|-)\s*(?:los\s+)?(?:19|20)?\d0)*"""

    /**
     * Strong "this request is about a decade/era" markers. Only these ARM the year check — a bare
     * number never does, so "los 20 mejores temas" is not read as the 2020s.
     *
     * The decade is captured INSIDE the marker (group 1), never rescanned out of the rest of the
     * prompt: a free-floating two-digit number is almost always the requested COUNT, so
     * "las 40 mejores canciones de la década" must not be read as the 1940s. A marker word with no
     * decade attached therefore arms nothing and simply yields no year check at all.
     */
    private val DECADE_MARKERS = listOf(
        // década de los 80, década de 1990, décadas de los 70 y 80, década del 90
        Regex("""d[ée]cadas?\s+(?:de[l]?\s+)?(?:los\s+)?($DECADE_RUN)"""),
        Regex("""a[ñn]os\s+($DECADE_RUN)"""), // años 80
        // "de los 80" — but not "de los 80 mejores", which is a count, not a decade.
        Regex("""\bde\s+los\s+($DECADE_RUN)\b(?!\s+(?:mejores|mejor|primeros|top|canciones|temas|[ée]xitos))"""),
        Regex("""\b((?:19|20)?\d0)'?s\b"""), // 1980s, 1990's, 80s, 90's
    )

    /** Pulls the individual decades back out of a [DECADE_RUN] captured by a marker. */
    private val DECADE_NUMBER = Regex("""(?:19|20)?\d0""")

    /**
     * The decade/era a request implies, or null when it implies none (→ no year check at all).
     *
     * Deliberately FAILS OPEN: several decades ("de los 70 y 80") widen into one span instead of
     * narrowing, and an ambiguous number can only ever widen the range. Over-constraining would cost
     * real songs; a too-wide range simply means the check rarely bites. No marker, or a marker with
     * no decade attached to it → null → no year check at all.
     */
    fun eraRange(prompt: String): IntRange? {
        val text = prompt.lowercase()
        val decades = sortedSetOf<Int>()
        DECADE_MARKERS.forEach { marker ->
            marker.findAll(text).forEach { match ->
                DECADE_NUMBER.findAll(match.groupValues[1]).forEach { number ->
                    // 1980 as-is; 30..90 → 1930..1990; 00/10/20 → the 2000s/2010s/2020s (the modern reading).
                    val decade = number.value.toInt()
                    decades += when {
                        decade >= 1900 -> decade
                        decade >= 30 -> 1900 + decade
                        else -> 2000 + decade
                    }
                }
            }
        }
        if (decades.isEmpty()) return null
        return decades.first()..(decades.last() + 9)
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
