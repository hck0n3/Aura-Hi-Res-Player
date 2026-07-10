package iad1tya.echo.music.playlistimport

import iad1tya.echo.music.api.AiPlaylistService
import iad1tya.echo.music.db.MusicDatabase
import iad1tya.echo.music.db.entities.PlaylistEntity
import iad1tya.echo.music.db.entities.PlaylistSongMap
import iad1tya.echo.music.models.MediaMetadata
import java.time.LocalDateTime

/**
 * Orchestrates the AI text-to-playlist flow: ask the AI for a track list ([AiPlaylistService]),
 * resolve each track against the catalog ([SongResolver], shared with the JR importer), then persist
 * a new local playlist in a single transaction. Network + DB, so no unit tests (manual APK testing,
 * like the rest of the project).
 */
object AiPlaylistGenerator {

    private const val MAX_NAME_LENGTH = 40

    data class Result(
        val playlistId: String,
        val name: String,
        val total: Int,
        val resolved: Int,
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
        // Over-generate then take N: SongResolver silently drops tracks with no YouTube match, so
        // asking for exactly N returns fewer than N (BUG: asked 20, got 14). Pad the AI ask by ~1.5×
        // — AiPlaylistService scales the prompt count AND the token budget with requestCount, so a
        // larger list isn't truncated mid-JSON (e.g. 20→30 tracks ≈ 6512 tokens, under the 8192 cap).
        val target = count
        val requestCount = (count * 3 + 1) / 2
        val spec = AiPlaylistService.generate(prompt, requestCount, provider, apiKey, baseUrl, model)
            .getOrElse { return kotlin.Result.failure(it) }

        val resolvedSongs = ArrayList<MediaMetadata>(spec.tracks.size)
        // Short-circuit: stop resolving as soon as we have `target` distinct songs so we don't waste
        // network calls resolving the rest of the padded list. Progress reflects the user's request.
        for (track in spec.tracks) {
            SongResolver.resolve(database, track.title, track.artist)?.let { resolvedSongs += it }
            val resolvedCount = resolvedSongs.distinctBy { it.id }.size
            onResolveProgress(resolvedCount.coerceAtMost(target), target)
            if (resolvedCount >= target) break
        }

        var ordered = resolvedSongs.distinctBy { it.id }.take(target)
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

        if (ordered.isEmpty()) {
            return kotlin.Result.failure(EmptyResultException())
        }

        // The AI proposes a short name; fall back to the user's prompt if it's blank.
        val name = spec.name.ifBlank { prompt }.trim().ifBlank { prompt }.take(MAX_NAME_LENGTH)
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
            ),
        )
    }
}
