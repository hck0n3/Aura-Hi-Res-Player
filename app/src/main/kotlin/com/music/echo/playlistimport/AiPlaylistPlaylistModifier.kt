package iad1tya.echo.music.playlistimport

import iad1tya.echo.music.api.AiPlaylistPrompt
import iad1tya.echo.music.api.AiPlaylistService
import iad1tya.echo.music.api.TrackQuery
import iad1tya.echo.music.db.MusicDatabase
import iad1tya.echo.music.db.entities.ArtistEntity
import iad1tya.echo.music.db.entities.PlaylistSong
import iad1tya.echo.music.db.entities.PlaylistSongMap
import iad1tya.echo.music.models.MediaMetadata
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import java.time.LocalDateTime

/**
 * "Edit this playlist with a text instruction" — the modify counterpart of [AiPlaylistGenerator].
 *
 * Two explicit phases, because this is DESTRUCTIVE to a user's own playlist:
 *  1. [plan] — ask the AI ([AiPlaylistService.modify]), resolve the proposed additions with the shared
 *     [SongResolver], and return a [Plan]. Touches the database ONLY to read.
 *  2. [apply] — commit that plan in ONE [MusicDatabase.transaction], after the user confirmed it.
 * The UI shows the plan and requires confirmation in between, so nothing is ever removed unattended.
 *
 * NO-OP ON FAILURE, by design: if the AI chain is unavailable, times out, or answers with something
 * unusable, [plan] returns a failure and NOTHING is written. Unlike generation — where a non-AI
 * fallback built from search/radio is the right call — a WRONG edit to an existing playlist is worse
 * than no edit, so there is deliberately no guessing fallback here.
 *
 * NO Room id ever reaches the AI: the model only sees positions in the list it was given (see
 * [AiPlaylistPrompt.buildModifyMessages]); positions are mapped back to `PlaylistSongMap.id` here,
 * inside the app.
 */
object AiPlaylistPlaylistModifier {

    /**
     * Cap on additions actually resolved per edit. Bounds the network burst (each resolve is a
     * search) so an over-eager AI can't turn one tap into a hundred lookups (battery/heat rule).
     */
    private const val MAX_ADDITIONS = 25

    /** One song the plan will remove. [map] is internal (never sent to the AI); the rest is for the preview. */
    data class Removal(
        val map: PlaylistSongMap,
        val title: String,
        val artist: String,
    )

    /**
     * A reviewed-then-applied edit. [removals]/[additions] are exactly what the confirmation preview
     * shows and exactly what [apply] commits — no hidden extras.
     */
    data class Plan(
        val playlistId: String,
        val removals: List<Removal>,
        val additions: List<MediaMetadata>,
        /** Proposed additions that will NOT be added: no playable match, or already in the playlist. */
        val skippedAdditions: Int,
    ) {
        val isEmpty: Boolean get() = removals.isEmpty() && additions.isEmpty()
    }

    /** The AI answered, but nothing it proposed survived validation → the caller no-ops. */
    class NoChangesException : Exception("The AI proposed no applicable change")

    /**
     * Asks the AI for an edit and turns it into a concrete, reviewable [Plan]. READ-ONLY.
     *
     * [songs] is the playlist as the user currently SEES it (the screen's sorted/filtered list), so
     * "quita la tercera" means the third row on screen. Indices are resolved against this exact list
     * and immediately converted to internal row ids, so the display order never reaches the database.
     */
    suspend fun plan(
        database: MusicDatabase,
        playlistId: String,
        songs: List<PlaylistSong>,
        prompt: String,
        provider: String,
        apiKey: String,
        baseUrl: String,
        model: String,
    ): Result<Plan> {
        if (prompt.isBlank()) {
            return Result.failure(IllegalArgumentException("Prompt is empty"))
        }
        // The AI only ever sees this capped snapshot, so index N always means snapshot[N].
        val snapshot = songs.take(AiPlaylistPrompt.MAX_MODIFY_TRACKS)
        if (snapshot.isEmpty()) {
            return Result.failure(NoChangesException())
        }

        val currentTracks = snapshot.map { TrackQuery(it.song.song.title, it.song.artists.displayName()) }

        // Bound the AI phase with the SAME 60s budget as generation: on timeout we treat the service as
        // unavailable and no-op, so the dialog can never spin holding the modem awake (battery/heat).
        val edit = withTimeoutOrNull(AiPlaylistGenerator.AI_BUDGET_MS) {
            AiPlaylistService.modify(
                currentTracks = currentTracks,
                prompt = prompt,
                provider = provider,
                apiKey = apiKey,
                baseUrl = baseUrl,
                model = model,
            )
        }?.getOrElse { return Result.failure(it) }
            ?: return Result.failure(AiPlaylistService.AiServiceUnavailableException())

        // Positions → rows. Already range-checked by the parser; getOrNull keeps this total anyway.
        val removals = edit.removeIndices
            .mapNotNull { index -> snapshot.getOrNull(index) }
            .map { Removal(it.map, it.song.song.title, it.song.artists.displayName()) }

        // Skip additions already in the playlist. Read from the DATABASE, never from [songs]: that is
        // the screen's list, which the "hide video songs" setting filters — a song hidden from the
        // screen is still in the playlist, and deduping against the filtered list would let the AI
        // silently re-add it as a duplicate. This is the dedupe set ONLY; what the AI sees, and the
        // indices removals map against, deliberately stay the display list.
        val existingIds = database.playlistSongs(playlistId).first().map { it.map.songId }.toSet()
        val resolved = LinkedHashMap<String, MediaMetadata>()
        val proposed = edit.additions.take(MAX_ADDITIONS)
        // Resolution is network-bound; give it its own bound so a stalled search can't hang the dialog.
        // Partial results are kept on timeout (whatever resolved so far is still a valid, reviewable plan).
        withTimeoutOrNull(AiPlaylistGenerator.AI_BUDGET_MS) {
            for (track in proposed) {
                val metadata = SongResolver.resolve(database, track.title, track.artist) ?: continue
                if (metadata.id in existingIds) continue
                resolved.putIfAbsent(metadata.id, metadata)
            }
        }

        val plan = Plan(
            playlistId = playlistId,
            removals = removals,
            additions = resolved.values.toList(),
            skippedAdditions = (proposed.size - resolved.size).coerceAtLeast(0),
        )
        if (plan.isEmpty) return Result.failure(NoChangesException())
        return Result.success(plan)
    }

    /**
     * Commits [plan] in ONE transaction: delete the removed rows, close the position gaps, append the
     * additions at the end.
     *
     * Positions are re-numbered densely (0..n-1) on purpose: the rest of the app relies on that
     * invariant — `addSongToPlaylist` starts appending at `songCount`, so leaving holes would make a
     * later "Add music" collide with an existing position. The survivors are renumbered IN THEIR
     * STORED POSITION ORDER (re-read from the database here, NOT the screen's sorted/filtered list),
     * so a playlist being viewed sorted by title, or with videos hidden, is never silently reordered.
     *
     * Re-reading also makes this tolerant of the gap between preview and confirmation: rows deleted
     * meanwhile simply aren't there any more.
     *
     * Registry invariants: `insert(MediaMetadata)` is IGNORE-on-conflict and returns early for a song
     * that already exists, so re-adding one NEVER rewrites its row and can never clobber its `liked`
     * flag; the playlist row is only ever touched through `copy()` of the row read from the database,
     * which preserves `bookmarkedAt` (a null there would make the playlist vanish from Library).
     */
    suspend fun apply(database: MusicDatabase, plan: Plan) {
        if (plan.isEmpty) return
        // STORED order (ORDER BY position), unfiltered — the only safe basis for renumbering.
        val stored = database.playlistSongs(plan.playlistId).first()
        val playlistEntity = database.getPlaylistById(plan.playlistId)?.playlist
        val removedMapIds = plan.removals.map { it.map.id }.toSet()
        val kept = stored.filter { it.map.id !in removedMapIds }.map { it.map }

        // Re-check the dedupe against the survivors of THIS read, closing the plan→confirm gap: a song
        // added meanwhile is already here and must not be duplicated. A song this plan removes is not
        // in `kept`, so re-adding one in the same edit still works.
        val keptSongIds = kept.map { it.songId }.toSet()
        val additions = plan.additions.filter { it.id !in keptSongIds }

        database.transaction {
            plan.removals.forEach { delete(it.map) }
            // Close the holes the deletions left; only rows that actually move are written.
            kept.forEachIndexed { index, map ->
                if (map.position != index) update(map.copy(position = index))
            }
            additions.forEachIndexed { offset, metadata ->
                insert(metadata)
                insert(
                    PlaylistSongMap(
                        playlistId = plan.playlistId,
                        songId = metadata.id,
                        position = kept.size + offset,
                    ),
                )
            }
            // copy() → bookmarkedAt (Library visibility) and every other column survive untouched.
            playlistEntity?.let { update(it.copy(lastUpdateTime = LocalDateTime.now())) }
        }
    }

    private fun List<ArtistEntity>.displayName(): String = joinToString(", ") { it.name }
}
