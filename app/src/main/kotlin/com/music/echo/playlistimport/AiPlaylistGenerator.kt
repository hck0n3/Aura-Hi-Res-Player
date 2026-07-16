package iad1tya.echo.music.playlistimport

import com.music.innertube.YouTube
import com.music.innertube.models.SongItem
import com.music.innertube.models.WatchEndpoint
import iad1tya.echo.music.api.AiPlaylistService
import iad1tya.echo.music.db.MusicDatabase
import iad1tya.echo.music.db.entities.PlaylistEntity
import iad1tya.echo.music.db.entities.PlaylistSongMap
import iad1tya.echo.music.models.MediaMetadata
import iad1tya.echo.music.models.toMediaMetadata
import kotlinx.coroutines.withTimeoutOrNull
import java.time.LocalDateTime

/**
 * Orchestrates the AI text-to-playlist flow: ask the AI for a track list ([AiPlaylistService]),
 * resolve each track against the catalog ([SongResolver], shared with the JR importer), then persist
 * a new local playlist in a single transaction. Network + DB, so no unit tests (manual APK testing,
 * like the rest of the project).
 *
 * NEVER dead-ends: if the whole keyless AI chain is unavailable (or resolves zero songs), it falls
 * back to a non-AI playlist built from YouTube search + radio ([buildFallbackSongs]) and flags the
 * result [Result.generatedWithoutAi] so the UI can add a subtle "generada sin IA" note instead of an
 * "IA ocupada / no disponible" error.
 */
object AiPlaylistGenerator {

    private const val MAX_NAME_LENGTH = 40

    /**
     * Hard ceiling on the whole AI phase (worst case ≈ worker + 4 models × 2 retries). Bounds the
     * pathological all-timeouts case so the dialog can't spin for minutes holding the modem awake
     * (battery/heat rule); on timeout we simply treat AI as unavailable and build the non-AI playlist.
     *
     * Shared with [AiPlaylistPlaylistModifier], which bounds its own AI phase with the same budget.
     */
    internal const val AI_BUDGET_MS = 60_000L

    data class Result(
        val playlistId: String,
        val name: String,
        val total: Int,
        val resolved: Int,
        /** True when the AI chain failed and the playlist was built from search/radio, not AI. */
        val generatedWithoutAi: Boolean = false,
    )

    class EmptyResultException : Exception("No tracks could be resolved")

    suspend fun generate(
        database: MusicDatabase,
        prompt: String,
        count: Int,
        provider: String,
        apiKey: String,
        baseUrl: String,
        model: String,
        onResolveProgress: (done: Int, total: Int) -> Unit = { _, _ -> },
    ): kotlin.Result<Result> {
        val target = count

        // Ask the AI (user key → Aura Worker → several free Pollinations models). getOrNull() so a total
        // failure doesn't dead-end — we fall back to a non-AI playlist below instead of surfacing an error.
        // Over-generate then take N: SongResolver silently drops tracks with no YouTube match, so asking
        // for exactly N returns fewer than N. Pad the AI ask by ~1.5× (token budget scales with count).
        val requestCount = (count * 3 + 1) / 2
        // Bound the whole AI phase (AI_BUDGET_MS): on timeout, spec stays null and we build the non-AI
        // playlist below — so a stuck cascade can't hold the modem for minutes.
        val spec = withTimeoutOrNull(AI_BUDGET_MS) {
            AiPlaylistService.generate(prompt, requestCount, provider, apiKey, baseUrl, model).getOrNull()
        }

        var ordered: List<MediaMetadata> = emptyList()
        var aiName: String? = null
        var generatedWithoutAi = false

        if (spec != null) {
            val resolvedSongs = ArrayList<MediaMetadata>(spec.tracks.size)
            // Short-circuit: stop resolving as soon as we have `target` distinct songs so we don't waste
            // network calls resolving the rest of the padded list. Progress reflects the user's request.
            for (track in spec.tracks) {
                SongResolver.resolve(database, track.title, track.artist)?.let { resolvedSongs += it }
                val resolvedCount = resolvedSongs.distinctBy { it.id }.size
                onResolveProgress(resolvedCount.coerceAtMost(target), target)
                if (resolvedCount >= target) break
            }

            ordered = resolvedSongs.distinctBy { it.id }.take(target)
            // If padding still fell short, ask ONCE more for just the missing songs, excluding the ones
            // already chosen so the AI doesn't repeat them. Best-effort: silently skip on any failure.
            if (ordered.size < target) {
                val missing = target - ordered.size
                val exclude = ordered.joinToString(", ") { it.title }
                val topUpPrompt = "$prompt. NO incluyas ninguna de estas canciones ya elegidas: $exclude"
                AiPlaylistService.generate(topUpPrompt, (missing * 3 + 1) / 2, provider, apiKey, baseUrl, model)
                    .getOrNull()?.let { extra ->
                        for (track in extra.tracks) {
                            SongResolver.resolve(database, track.title, track.artist)?.let { resolvedSongs += it }
                            val resolvedCount = resolvedSongs.distinctBy { it.id }.size
                            onResolveProgress(resolvedCount.coerceAtMost(target), target)
                            if (resolvedCount >= target) break
                        }
                        ordered = resolvedSongs.distinctBy { it.id }.take(target)
                    }
            }
            aiName = spec.name
        }

        // NON-AI FALLBACK — the feature never dead-ends. If the AI chain was unavailable, or it came back
        // but resolved to zero playable songs, build a best-effort playlist straight from YouTube search +
        // radio (the same relatedness the app's infinite radio already uses). Only if THIS also comes back
        // empty (no connection / gibberish prompt) do we surface an error.
        if (ordered.isEmpty()) {
            ordered = buildFallbackSongs(prompt, target)
            generatedWithoutAi = true
            onResolveProgress(ordered.size.coerceAtMost(target), target)
        }

        if (ordered.isEmpty()) {
            return kotlin.Result.failure(EmptyResultException())
        }

        // The AI proposes a short name; fall back to the user's prompt (also used for the non-AI playlist).
        val name = (aiName ?: "").ifBlank { prompt }.trim().ifBlank { prompt }.take(MAX_NAME_LENGTH)
        val playlist = PlaylistEntity(
            name = name,
            bookmarkedAt = LocalDateTime.now(),
            isEditable = true,
        )
        // Single transaction: create the playlist, persist songs, map them in order (atomic).
        database.transaction {
            insert(playlist)
            ordered.forEachIndexed { index, metadata ->
                insert(metadata)
                insert(
                    PlaylistSongMap(
                        playlistId = playlist.id,
                        songId = metadata.id,
                        position = index,
                    ),
                )
            }
        }

        return kotlin.Result.success(
            Result(
                playlistId = playlist.id,
                name = name,
                total = target,
                resolved = ordered.size,
                generatedWithoutAi = generatedWithoutAi,
            ),
        )
    }

    /**
     * Best-effort, AI-free playlist from the user's prompt. Uses the prompt as a YouTube search, seeds
     * radio ([YouTube.next]) from the top hit for a mood/genre-coherent set, and tops up with the direct
     * search hits. Everything is best-effort (`getOrNull`); returns whatever it can gather, deduped and
     * capped at [target]. Cheaper than the AI retry loop (~1 search + 1 radio call), so it stays within
     * the battery/heat budget. Empty only when offline or the prompt yields nothing at all.
     */
    private suspend fun buildFallbackSongs(prompt: String, target: Int): List<MediaMetadata> {
        if (prompt.isBlank() || target <= 0) return emptyList()
        val collected = LinkedHashMap<String, MediaMetadata>() // insertion order + dedupe by id

        fun addAll(items: List<SongItem>?) {
            items?.forEach { item ->
                if (collected.size >= target) return
                val mm = item.toMediaMetadata()
                collected.putIfAbsent(mm.id, mm)
            }
        }

        // 1. Search the prompt for songs; broaden to videos only if the SONG filter finds nothing
        //    (same resilience SongResolver uses — many tracks only surface under the VIDEO filter).
        val songHits = YouTube.search(prompt, YouTube.SearchFilter.FILTER_SONG).getOrNull()
            ?.items?.filterIsInstance<SongItem>().orEmpty()
        val seedHits = songHits.ifEmpty {
            YouTube.search(prompt, YouTube.SearchFilter.FILTER_VIDEO).getOrNull()
                ?.items?.filterIsInstance<SongItem>().orEmpty()
        }

        // 2. Radio from the top hit → coherent related songs (YouTube's own relatedness).
        seedHits.firstOrNull()?.let { seed ->
            addAll(YouTube.next(WatchEndpoint(videoId = seed.id)).getOrNull()?.items)
        }
        // 3. Top up with the direct search hits so we still fill if radio was thin.
        addAll(seedHits)

        return collected.values.toList().take(target)
    }
}
