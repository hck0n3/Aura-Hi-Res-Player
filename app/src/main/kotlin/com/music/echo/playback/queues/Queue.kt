

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

    /**
     * Persistent Enhanced Shuffle bucket for this queue ("PL:<id>", "AP:liked", "AL:<id>", "AR:<id>"…),
     * or null when the queue has no memory (raw radio, search results).
     *
     * Lives on the INTERFACE, not just on ListQueue: the service used to read it as
     * `(queue as? ListQueue)?.contextId`, so any screen that legitimately started a different queue type
     * silently lost its no-repeat memory, and the only workaround was to convert the screen to a
     * ListQueue — which changes what plays after the queue ends. Declaring it here lets every queue type
     * carry a bucket while keeping its own continuation behaviour.
     */
    val contextId: String? get() = null

    /**
     * Song ids that must count as already heard when this shuffle lap starts (Continue, not Start over).
     * Filled by the screen from persistent shuffle memory ∪ [iad1tya.echo.music.db.entities.SongEntity.totalPlayTime]
     * so songs heard with Aleatorio mejorado OFF still sit behind the unplayed remainder.
     */
    val seedPlayedIds: Set<String> get() = emptySet()

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
            if (filtered.isEmpty()) return copy(items = filtered, mediaItemIndex = 0)
            val anchor = items.getOrNull(mediaItemIndex)
                ?: return copy(items = filtered, mediaItemIndex = 0)

            // Match the same OCCURRENCE, not merely the same id: a queue may legitimately hold the same
            // song twice, and anchoring on the FIRST copy rewinds playback to the earlier one.
            val occurrence = items.take(mediaItemIndex).count { it.mediaId == anchor.mediaId }
            var seen = 0
            filtered.forEachIndexed { i, item ->
                if (item.mediaId == anchor.mediaId) {
                    if (seen == occurrence) {
                        return copy(items = filtered, mediaItemIndex = i)
                    }
                    seen++
                }
            }

            // The anchor itself was filtered out. Land on the first SURVIVOR that followed it rather than
            // on index 0: restarting the queue from the top is a rewind the user never asked for, and on a
            // restore it silently replays songs already heard.
            val survivorIds = filtered.mapTo(HashSet()) { it.mediaId }
            val nextSurvivor = items.drop(mediaItemIndex + 1).firstOrNull { it.mediaId in survivorIds }
            val fallback = nextSurvivor
                ?.let { n -> filtered.indexOfFirst { it.mediaId == n.mediaId } }
                ?.takeIf { it >= 0 }
                ?: 0
            return copy(items = filtered, mediaItemIndex = fallback)
        }

        fun filterExplicit(enabled: Boolean = true) =
            if (enabled) reanchor(items.filterExplicit()) else this

        fun filterVideoSongs(disableVideos: Boolean = false) =
            if (disableVideos) reanchor(items.filterVideoSongs(true)) else this

        /**
         * Automatic radio / related / mood appends: keep only items YouTube Music tagged with a
         * [iad1tya.echo.music.models.MediaMetadata.musicVideoType] (ATV songs + official music videos).
         * Drops tutorials / how-tos / non-music uploads that arrive with a null type.
         */
        fun filterNonMusicForAutoQueue(enabled: Boolean = true) =
            if (enabled) reanchor(items.filterNonMusicForAutoQueue(true)) else this
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

/** See [Queue.Status.filterNonMusicForAutoQueue]. */
fun List<MediaItem>.filterNonMusicForAutoQueue(enabled: Boolean = true) =
    if (enabled) {
        filter { it.metadata?.musicVideoType != null }
    } else {
        this
    }
