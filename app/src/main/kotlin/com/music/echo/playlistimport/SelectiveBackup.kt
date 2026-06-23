package iad1tya.echo.music.playlistimport

import iad1tya.echo.music.eq.data.SavedEQProfile
import kotlinx.serialization.Serializable

/**
 * A selective, mergeable export: only the chosen playlists, all followed artists, and/or all EQ
 * presets — separate from the all-or-nothing full ZIP backup. Import is strictly ADDITIVE (it only
 * inserts; it never deletes), so re-importing can at worst create duplicates, never lose data.
 *
 * Playlists reuse [JrPlaylistImporter]'s file format so import goes through the already-tested
 * resolve-and-create path.
 */
@Serializable
data class SelectiveBackup(
    val type: String = TYPE,
    val version: Int = 1,
    val playlists: List<JrPlaylistImporter.JrPlaylistFile> = emptyList(),
    val artists: List<ArtistRec> = emptyList(),
    val eqPresets: List<SavedEQProfile> = emptyList(),
) {
    @Serializable
    data class ArtistRec(
        val id: String,
        val name: String,
        val thumbnailUrl: String? = null,
        val channelId: String? = null,
    )

    companion object {
        const val TYPE = "aura-selective-backup"
    }
}
