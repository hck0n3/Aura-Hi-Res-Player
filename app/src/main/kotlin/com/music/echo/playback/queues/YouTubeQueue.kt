

package iad1tya.echo.music.playback.queues

import androidx.media3.common.MediaItem
import com.music.innertube.YouTube
import com.music.innertube.models.WatchEndpoint
import iad1tya.echo.music.extensions.toMediaItem
import iad1tya.echo.music.models.MediaMetadata
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.withContext

class YouTubeQueue(
    private var endpoint: WatchEndpoint,
    override val preloadItem: MediaMetadata? = null,
    /**
     * True when Aura seeded this queue as automatic radio/related (not a user tapping a single song /
     * playlist endpoint). Gates NO_MUSIC skip + auto-append non-music filter.
     */
    val automaticRadio: Boolean = false,
) : Queue {
    private var continuation: String? = null
    private val maxRetries = 3

    override suspend fun getInitialStatus(): Queue.Status {
        return withContext(IO) {
            var lastException: Throwable? = null
            
            
            for (attempt in 0..maxRetries) {
                try {
                    val nextResult = YouTube.next(endpoint, continuation).getOrThrow()
                    endpoint = nextResult.endpoint
                    continuation = nextResult.continuation
                    return@withContext Queue.Status(
                        title = nextResult.title,
                        items = nextResult.items.map { it.toMediaItem() },
                        mediaItemIndex = nextResult.currentIndex ?: 0,
                    )
                } catch (e: Exception) {
                    lastException = e
                    
                    if (attempt == 0 && endpoint.videoId != null && endpoint.playlistId == null) {
                        endpoint = WatchEndpoint(
                            videoId = endpoint.videoId,
                            playlistId = "RDAMVM${endpoint.videoId}"
                        )
                    }
                }
            }
            throw lastException ?: Exception("Failed to get initial status")
        }
    }

    override fun hasNextPage(): Boolean = continuation != null

    /**
     * Fetch the next page. On failure the continuation is KEPT so a later attempt can resume.
     *
     * This used to be a one-way latch and is the main cause of "the infinite queue works sometimes":
     * `retryCount` is an INSTANCE field that was never reset on entry, so three failures ANYWHERE in the
     * session — not even consecutive ones — hit `continuation = null`. After that `hasNextPage()` is false
     * forever for this queue (the continuation is only repopulated by a SUCCESSFUL fetch, and MusicService
     * only calls nextPage() when hasNextPage() is true), so a single lift/tunnel/WiFi-to-data handover
     * killed autoplay permanently — and it stayed dead after the network came back, with no error shown.
     *
     * A network failure is NOT "there are no more pages": those two must not share a signal. The
     * continuation is now only ever replaced by a successful response, and the retry budget is per CALL.
     */
    override suspend fun nextPage(): List<MediaItem> {
        return withContext(IO) {
            var lastException: Throwable? = null
            for (attempt in 0..maxRetries) {
                try {
                    val nextResult = YouTube.next(endpoint, continuation).getOrThrow()
                    endpoint = nextResult.endpoint
                    continuation = nextResult.continuation
                    return@withContext nextResult.items.map { it.toMediaItem() }
                } catch (e: Exception) {
                    lastException = e
                    // Brief backoff: the old loop burned all its attempts within milliseconds, so a blip that
                    // lasted a second consumed the whole budget. Skipped after the final attempt.
                    if (attempt < maxRetries) {
                        kotlinx.coroutines.delay(RETRY_BACKOFF_MS * (attempt + 1))
                    }
                }
            }
            // Continuation deliberately left INTACT — the caller sees the failure and can simply try again
            // on the next transition, which is what makes autoplay survive a transient outage.
            throw lastException ?: Exception("Failed to get next page")
        }
    }

    companion object {
        /** Base backoff between page retries; multiplied by the attempt number. */
        private const val RETRY_BACKOFF_MS = 400L

        fun radio(song: MediaMetadata): YouTubeQueue {
            return YouTubeQueue(
                WatchEndpoint(videoId = song.id),
                song,
                automaticRadio = true,
            )
        }
    }
}
