

package iad1tya.echo.music.db.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.LocalDateTime

@Entity(tableName = "release_radar")
data class ReleaseRadarItem(
    @PrimaryKey val id: String,
    val artistId: String,
    val title: String,
    val artist: String,
    val type: String,
    val releaseDate: LocalDateTime,
    val artworkUri: String? = null,
    val source: String,
    val playId: String,
    val fetchedAt: LocalDateTime = LocalDateTime.now(),
    val seen: Boolean = false,
)
