

package iad1tya.echo.music.playback.queues

import androidx.media3.common.MediaItem
import iad1tya.echo.music.extensions.metadata
import iad1tya.echo.music.models.MediaMetadata

interface Queue {
    val preloadItem: MediaMetadata?

    /**
     * The queue was started by a "Shuffle" button, so playback must ENABLE shuffle mode once the items
     * land — not merely pre-scramble the list. Lives on the interface (not just ListQueue) because the
     * online-playlist screen starts a YouTubePlaylistQueue, and gating on ListQueue alone left those
     * Shuffle buttons bypassing the whole enhanced-shuffle system (frozen scramble, no memory).
     */
    val startShuffled: Boolean get() = false

    suspend fun getInitialStatus(): Status

    fun hasNextPage(): Boolean

    suspend fun nextPage(): List<MediaItem>

    data class Status(
        val title: String?,
        val items: List<MediaItem>,
        val mediaItemIndex: Int,
        val position: Long = 0L,
    ) {
        /**
         * Re-anchors [mediaItemIndex] after a filter shrinks [items].
         *
         * Both filters used to `copy(items = …)` and KEEP the old index, so removing any item before it
         * shifted the whole queue: playback started on a DIFFERENT song than the one that was requested.
         * The worst case was a single-song play whose seed sat at index 0 — drop the seed and index 0 now
         * points at the radio's NEXT track, i.e. "it plays something unrelated". Note the video filter is
         * ORed with Data Saver in MusicService, so this fired for a setting users don't read as "filter".
         *
         * The anchor is the item the index pointed at. If the anchor itself was filtered out we fall back
         * to 0 (the caller's preloadItem, when present, still pins the right song regardless).
         */
        private fun reanchor(filtered: List<MediaItem>): Status {
            val anchorId = items.getOrNull(mediaItemIndex)?.mediaId
            val newIndex = anchorId
                ?.let { id -> filtered.indexOfFirst { it.mediaId == id }.takeIf { it >= 0 } }
                ?: 0
            return copy(items = filtered, mediaItemIndex = newIndex.coerceAtMost((filtered.size - 1).coerceAtLeast(0)))
        }

        fun filterExplicit(enabled: Boolean = true) =
            if (enabled) reanchor(items.filterExplicit()) else this

        fun filterVideoSongs(disableVideos: Boolean = false) =
            if (disableVideos) reanchor(items.filterVideoSongs(true)) else this
    }
}

fun List<MediaItem>.filterExplicit(enabled: Boolean = true) =
    if (enabled) {
        filterNot {
            it.metadata?.explicit == true
        }
    } else {
        this
    }

fun List<MediaItem>.filterVideoSongs(disableVideos: Boolean = false) =
    if (disableVideos) {
        filterNot { it.metadata?.isVideoSong == true }
    } else {
        this
    }
