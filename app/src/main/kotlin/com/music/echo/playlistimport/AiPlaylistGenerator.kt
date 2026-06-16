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
        val spec = AiPlaylistService.generate(prompt, count, provider, apiKey, baseUrl, model)
            .getOrElse { return kotlin.Result.failure(it) }

        val resolvedSongs = ArrayList<MediaMetadata>(spec.tracks.size)
        spec.tracks.forEachIndexed { index, track ->
            SongResolver.resolve(database, track.title, track.artist)?.let { resolvedSongs += it }
            onResolveProgress(index + 1, spec.tracks.size)
        }

        val ordered = resolvedSongs.distinctBy { it.id }
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
                total = spec.tracks.size,
                resolved = ordered.size,
            ),
        )
    }
}
