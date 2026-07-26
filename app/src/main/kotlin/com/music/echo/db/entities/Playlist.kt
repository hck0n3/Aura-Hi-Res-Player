

package iad1tya.echo.music.db.entities

import androidx.compose.runtime.Immutable
import androidx.room.Embedded
import androidx.room.Junction
import androidx.room.Relation

@Immutable
data class Playlist(
    @Embedded
    val playlist: PlaylistEntity,
    val songCount: Int,
    @Relation(
        entity = SongEntity::class,
        entityColumn = "id",
        parentColumn = "id",
        projection = ["thumbnailUrl"],
        associateBy =
        Junction(
            value = PlaylistSongMapPreview::class,
            parentColumn = "playlistId",
            entityColumn = "songId",
        ),
    )
    val songThumbnails: List<String?>,
) : LocalItem() {
    override val id: String
        get() = playlist.id
    override val title: String
        get() = playlist.name
    override val thumbnailUrl: String?
        get() = null
    
    val thumbnails: List<String>
        get() {
            return if (playlist.thumbnailUrl != null)
                listOf(playlist.thumbnailUrl)
            else songCovers
        }

    /**
     * Song-cover mosaic candidates (up to 4, from the playlist's first songs), independent of
     * [PlaylistEntity.thumbnailUrl]. A non-null thumbnailUrl can still be DEAD (purged content://
     * custom cover, rotted Spotify/YT mosaic URL) — [thumbnails] can't know that, so the UI passes
     * this as the fallback to render when the primary URL fails to load.
     */
    val songCovers: List<String>
        get() = songThumbnails.filterNotNull()
}
