

package iad1tya.echo.music.playback.queues

import androidx.media3.common.MediaItem
import iad1tya.echo.music.models.MediaMetadata

class ListQueue(
    val title: String? = null,
    val items: List<MediaItem>,
    val startIndex: Int = 0,
    val position: Long = 0L,
    // Enhanced Shuffle context id (e.g. "PL:<id>", "LIBRARY"). Null = no persistent per-context
    // no-repeat memory for this queue (falls back to the classic in-memory shuffle). Optional so every
    // existing call site is unaffected.
    val contextId: String? = null,
    // Set by the screens' Shuffle buttons: ask playQueue to turn shuffle MODE on once the items land.
    // The buttons used to only pre-shuffle the items with shuffle mode OFF, which bypassed the whole
    // enhanced-shuffle system (no memory-aware order, no played recording → replayed played songs).
    // Transient intent — deliberately NOT persisted with the queue (restore must not re-force it).
    val startShuffled: Boolean = false,
) : Queue {
    override val preloadItem: MediaMetadata? = null

    override suspend fun getInitialStatus() = Queue.Status(title, items, startIndex, position)

    override fun hasNextPage(): Boolean = false

    override suspend fun nextPage() = throw UnsupportedOperationException()
}
