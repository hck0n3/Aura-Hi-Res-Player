

package iad1tya.echo.music.db.entities

import androidx.compose.runtime.Immutable
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalDateTime

@Immutable
@Entity(
    tableName = "event",
    foreignKeys = [
        ForeignKey(
            entity = SongEntity::class,
            parentColumns = ["id"],
            childColumns = ["songId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    // P39: index event.timestamp so the ~12 analytics Flow queries that filter/aggregate on
    // timestamp ranges use an index instead of full table scans. Room expects the auto-generated
    // index name "index_event_timestamp"; MIGRATION_37_38 creates it with that exact name.
    indices = [Index(value = ["timestamp"])],
)
data class Event(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(index = true) val songId: String,
    val timestamp: LocalDateTime,
    val playTime: Long,
)
