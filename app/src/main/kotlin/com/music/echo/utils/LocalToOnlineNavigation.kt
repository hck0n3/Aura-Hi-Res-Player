package iad1tya.echo.music.utils

import com.music.innertube.YouTube
import com.music.innertube.models.AlbumItem
import com.music.innertube.models.ArtistItem

/** True when [id] is missing or a synthetic local browse id that cannot open an online page. */
fun needsOnlineBrowseResolution(id: String?): Boolean =
    id.isNullOrBlank() || id.startsWith("LOCAL_")

/**
 * Resolve a YouTube Music artist browse id from a display name (e.g. local library artist).
 * Returns the first [ArtistItem.id], or null if search fails / is empty.
 */
suspend fun resolveOnlineArtistBrowseId(artistName: String): String? {
    val query = artistName.trim()
    if (query.isEmpty()) return null
    val result = YouTube.search(query, YouTube.SearchFilter.FILTER_ARTIST).getOrNull() ?: return null
    return result.items.filterIsInstance<ArtistItem>().firstOrNull()?.id
}

/**
 * Resolve a YouTube Music album browse id from a search query (typically "album artist").
 * Returns the first [AlbumItem]'s browse/id, or null if search fails / is empty.
 */
suspend fun resolveOnlineAlbumBrowseId(query: String): String? {
    val q = query.trim()
    if (q.isEmpty()) return null
    val result = YouTube.search(q, YouTube.SearchFilter.FILTER_ALBUM).getOrNull() ?: return null
    val album = result.items.filterIsInstance<AlbumItem>().firstOrNull() ?: return null
    return album.browseId.ifBlank { album.id }.takeIf { it.isNotBlank() }
}
