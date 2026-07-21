

package iad1tya.echo.music.models

import java.io.Serializable

data class PersistQueue(
    val title: String?,
    val items: List<MediaMetadata>,
    val mediaItemIndex: Int,
    val position: Long,
    val queueType: QueueType = QueueType.LIST,
    val queueData: QueueData? = null,
    // Enhanced Shuffle context of this queue (e.g. "PL:<id>", "LIBRARY"); null = no persistent-shuffle
    // memory for this queue. Optional with a default so it survives across the explicit serialVersionUID.
    val contextId: String? = null,
) : Serializable {
    companion object {
        // Pinned so future additive fields deserialize old files with defaults instead of throwing
        // InvalidClassException. (Files written before this UID existed still fail once and are cleared
        // by clearPersistedQueueFiles — acceptable, and it never happens again after this build.)
        private const val serialVersionUID: Long = 1L
    }
}

sealed class QueueType : Serializable {
    object LIST : QueueType()
    object YOUTUBE : QueueType()
    object YOUTUBE_ALBUM_RADIO : QueueType()
    object LOCAL_ALBUM_RADIO : QueueType()
}

sealed class QueueData : Serializable {
    data class YouTubeData(
        val endpoint: String,
        val continuation: String? = null
    ) : QueueData()
    
    data class YouTubeAlbumRadioData(
        val playlistId: String,
        val albumSongCount: Int = 0,
        val continuation: String? = null,
        val firstTimeLoaded: Boolean = false
    ) : QueueData()
    
    data class LocalAlbumRadioData(
        val albumId: String,
        val startIndex: Int = 0,
        val playlistId: String? = null,
        val continuation: String? = null,
        val firstTimeLoaded: Boolean = false
    ) : QueueData()
}
