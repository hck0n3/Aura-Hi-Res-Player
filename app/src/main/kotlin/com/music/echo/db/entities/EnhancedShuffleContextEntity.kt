

package iad1tya.echo.music.db.entities

import androidx.compose.runtime.Immutable
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Enhanced Shuffle ("Aleatorio mejorado") per-context bookkeeping.
 *
 * One row per shuffle context. Tracks the last song/position played (best-effort resume) and how many
 * full no-repeat cycles the context has completed. The played-set itself lives in
 * [EnhancedShufflePlayedEntity]; this row is just the lightweight cursor + counter.
 */
@Immutable
@Entity(tableName = "enhanced_shuffle_context")
data class EnhancedShuffleContextEntity(
    @PrimaryKey val contextId: String,
    val lastSongId: String? = null,
    val lastPositionMs: Long = 0,
    val cycleCount: Int = 0,
    val updatedAt: Long,
)
