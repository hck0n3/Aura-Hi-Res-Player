package iad1tya.echo.music.utils

import com.music.innertube.models.ArtistItem
import com.music.innertube.models.YTItem

/**
 * All-tab preview for search.
 *
 * 0.6.190 did `if (first item is Artist) take(2)`. YouTube's "Top result" shelf is a mixed list
 * that **starts** with the artist card and then the matching songs — so that truncated the real
 * results to two rows and made Buscar look dead.
 *
 * Cap at 2 only when every item is an artist (the dedicated Artistas shelf). Mixed / songs /
 * albums / playlists stay intact. The Artistas chip still has the full list.
 */
fun List<YTItem>.forAllTabSearchPreview(): List<YTItem> {
    if (isEmpty()) return this
    return if (all { it is ArtistItem }) take(2) else this
}

/** First-seen id wins; later shelves drop repeats. Used by Novedades so radar ≠ latest ≠ hero. */
fun <T> MutableSet<String>.claimUnique(items: List<T>, idOf: (T) -> String): List<T> =
    items.filter { add(idOf(it)) }
