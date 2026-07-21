

package iad1tya.echo.music.db.entities

import androidx.compose.runtime.Immutable
import androidx.room.Entity
import androidx.room.Index

/**
 * Enhanced Shuffle ("Aleatorio mejorado") persistent no-repeat memory.
 *
 * One row per (context, song) that has already PLAYED in the current shuffle cycle of that context.
 * The memory PERSISTS across app restarts / days / toggling shuffle off-on, so a partially-heard
 * playlist (or the whole library) never repeats a song until every song in that context has played.
 *
 * No foreign keys on purpose: [contextId] is synthetic (e.g. "PL:<id>", "LIBRARY") and [songId] may be
 * a raw YouTube id that is not present in the `song` table.
 */
@Immutable
@Entity(
    tableName = "enhanced_shuffle_played",
    primaryKeys = ["contextId", "songId"],
    indices = [Index(value = ["contextId"])],
)
data class EnhancedShufflePlayedEntity(
    val contextId: String,
    val songId: String,
    val playedAt: Long,
)
