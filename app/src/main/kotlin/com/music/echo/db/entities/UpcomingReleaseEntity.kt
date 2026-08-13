package iad1tya.echo.music.db.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "upcoming_release")
data class UpcomingReleaseEntity(
    @PrimaryKey val id: String,
    val artistId: String,
    val artistName: String,
    val title: String,
    val releaseEpochMs: Long,
    val artworkUri: String? = null,
    val youtubeBrowseId: String? = null,
    val presaved: Boolean = false,
)
