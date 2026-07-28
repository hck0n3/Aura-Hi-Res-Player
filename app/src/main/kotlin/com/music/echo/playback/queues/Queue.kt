

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
        fun filterExplicit(enabled: Boolean = true) =
            if (enabled) {
                copy(
                    items = items.filterExplicit(),
                )
            } else {
                this
            }

        fun filterVideoSongs(disableVideos: Boolean = false) =
            if (disableVideos) {
                copy(
                    items = items.filterVideoSongs(true),
                )
            } else {
                this
            }
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
