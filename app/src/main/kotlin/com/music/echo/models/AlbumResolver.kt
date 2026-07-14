package iad1tya.echo.music.models

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.music.innertube.YouTube
import com.music.innertube.models.WatchEndpoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Recovers the album linked to a song when the InnerTube renderer that seeded its
 * [MediaMetadata] (radio/queue/search/artist flat lists) omitted the album from the byline.
 *
 * The recovered `album.id` is the same `MPRE…` browseId that the `album/{id}` route and
 * AlbumScreen already consume, so callers can navigate straight to it.
 *
 * A true "video" item legitimately has no album on either endpoint, so this returns null and
 * the "view album" option stays hidden (correct). Never throws — any failure resolves to null.
 */
suspend fun resolveAlbumForSong(id: String): MediaMetadata.Album? = withContext(Dispatchers.IO) {
    try {
        // Primary: a single-item queue exposes the linked album in the byline.
        val fromQueue = YouTube.queue(listOf(id)).getOrNull()?.firstOrNull()?.album
        if (fromQueue != null) {
            return@withContext MediaMetadata.Album(id = fromQueue.id, title = fromQueue.name)
        }
        // Fallback: the watch "next" panel carries the same album on the matching item.
        val fromNext = YouTube.next(WatchEndpoint(videoId = id)).getOrNull()
            ?.items?.find { it.id == id }?.album
        if (fromNext != null) {
            return@withContext MediaMetadata.Album(id = fromNext.id, title = fromNext.name)
        }
        null
    } catch (e: Exception) {
        null
    }
}

/**
 * Returns the album for a song, resolving it on-demand ONLY when it is missing.
 *
 * Resolution order (first hit wins, no network unless the album is genuinely absent):
 *  1. [initial] — the album already carried by the model (metadata / [SongItem]).
 *  2. [dbAlbumId] — a locally-stored album id (with [dbAlbumName]) from the library row.
 *  3. a one-shot [resolveAlbumForSong] lookup keyed on [songId].
 *
 * The lookup runs inside a [LaunchedEffect], so its coroutine is cancelled automatically when
 * the menu/composable leaves composition. This is menu-local state only: it never mutates the
 * immutable MediaMetadata tag or the queue.
 */
@Composable
fun rememberResolvedAlbum(
    songId: String,
    initial: MediaMetadata.Album?,
    dbAlbumId: String?,
    dbAlbumName: String?,
): MediaMetadata.Album? {
    // Album from cheap, already-known sources (no network): the model first, then the local DB row.
    val known: MediaMetadata.Album? =
        initial ?: dbAlbumId?.let { MediaMetadata.Album(id = it, title = dbAlbumName.orEmpty()) }

    // Holds the album recovered by the one-shot network lookup when nothing is known yet.
    var fetched by remember(songId) { mutableStateOf<MediaMetadata.Album?>(null) }

    LaunchedEffect(songId, known) {
        // Only hit the network when the album is genuinely missing everywhere.
        if (known == null && fetched == null) {
            fetched = resolveAlbumForSong(songId)
        }
    }

    return known ?: fetched
}
