

package iad1tya.echo.music.playback.queues

import androidx.media3.common.MediaItem
import com.music.innertube.YouTube
import com.music.innertube.models.WatchEndpoint
import iad1tya.echo.music.db.entities.AlbumWithSongs
import iad1tya.echo.music.extensions.toMediaItem
import iad1tya.echo.music.models.MediaMetadata
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.withContext

class LocalAlbumRadio(
    private val albumWithSongs: AlbumWithSongs,
    private val startIndex: Int = 0,
    // The album screen's Shuffle button: pre-scrambling the list alone left shuffle MODE off, so the
    // shuffle icon stayed dark, the order was a one-shot scramble and the no-repeat memory saw nothing.
    override val startShuffled: Boolean = false,
    // "AL:<local album id>" — gives the album/EP Shuffle a persistent no-repeat memory WITHOUT
    // converting it to a ListQueue, which would have replaced YouTube's album-radio continuation with
    // Aura's own radio, i.e. changed what plays once the album ends.
    override val contextId: String? = null,
    override val seedPlayedIds: Set<String> = emptySet(),
) : Queue {
    override val preloadItem: MediaMetadata? = null

    private lateinit var playlistId: String
    private val endpoint: WatchEndpoint
        get() = WatchEndpoint(
            playlistId = playlistId
        )

    private var continuation: String? = null
    private var firstTimeLoaded: Boolean = false

    override suspend fun getInitialStatus(): Queue.Status = withContext(IO) {
        Queue.Status(
            title = albumWithSongs.album.title,
            items = albumWithSongs.songs.map { it.toMediaItem() },
            mediaItemIndex = startIndex
        )
    }

    override fun hasNextPage(): Boolean {
        // With a shuffle-memory context, YouTube's album-radio continuation must NOT land mid-album:
        // pagination mixed foreign tracks into the no-repeat pool and made shuffle look broken.
        // MusicService hands off to Aura radio once the album/EP itself is exhausted.
        if (contextId != null) return false
        return !firstTimeLoaded || continuation != null
    }

    override suspend fun nextPage(): List<MediaItem> = withContext(IO) {
        if (!firstTimeLoaded) {
            playlistId = YouTube.album(albumWithSongs.album.id).getOrThrow().album.playlistId
            val nextResult = YouTube.next(endpoint, continuation).getOrThrow()
            continuation = nextResult.continuation
            firstTimeLoaded = true
            return@withContext nextResult.items
                .drop(albumWithSongs.songs.size)
                .map { it.toMediaItem() }
        }
        val nextResult = YouTube.next(endpoint, continuation).getOrThrow()
        continuation = nextResult.continuation
        nextResult.items.map { it.toMediaItem() }
    }
}
