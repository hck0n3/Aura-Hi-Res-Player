package iad1tya.echo.music.migration

import com.aura.migration.model.SourcePlaylist
import com.aura.migration.model.SourceTrack
import com.aura.migration.model.SourceType
import com.aura.migration.source.PlaylistSource

/**
 * Adapts an already-parsed list of tracks (e.g. from FileSource.parse) into the [PlaylistSource] contract
 * that [com.aura.migration.MigrationEngine.import] consumes.
 *
 * FileSource itself throws from fetchTracks on purpose (it exposes parse(content, extension) instead), so
 * the app parses the file first and hands the resulting tracks to the engine through this wrapper. This
 * keeps the engine's single, sequential import flow untouched — no per-track parallelism is introduced.
 */
class InMemoryPlaylistSource(
    private val tracks: List<SourceTrack>,
    override val displayName: String,
    override val type: SourceType = SourceType.FILE,
) : PlaylistSource {

    override fun accepts(input: String): Boolean = true

    override suspend fun listPlaylists(input: String?): List<SourcePlaylist> = listOf(
        SourcePlaylist(id = "", name = displayName, trackCount = tracks.size, origin = type),
    )

    override suspend fun fetchTracks(playlistId: String): List<SourceTrack> = tracks
}
