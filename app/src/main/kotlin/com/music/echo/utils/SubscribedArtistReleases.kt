package iad1tya.echo.music.utils

import com.music.innertube.models.AlbumItem
import iad1tya.echo.music.db.MusicDatabase
import kotlinx.coroutines.flow.first

/**
 * Keys that identify artists the user has subscribed to / followed in the app
 * (`bookmarkedAt != null`). Used to keep "Álbumes recién lanzados" personal — global YTM
 * explore shelves are filtered down to these artists only.
 */
data class SubscribedArtistKeys(
    val ids: Set<String>,
    val namesLower: Set<String>,
)

suspend fun MusicDatabase.subscribedArtistKeys(): SubscribedArtistKeys {
    val bookmarked = artistsBookmarkedByNameAsc().first()
    return SubscribedArtistKeys(
        ids = bookmarked.map { it.id }.filter { it.isNotBlank() }.toSet(),
        namesLower = bookmarked.map { it.artist.name.trim().lowercase() }.filter { it.isNotEmpty() }.toSet(),
    )
}

/** True when any credited artist matches a subscribed id or name. */
fun AlbumItem.isFromSubscribedArtist(keys: SubscribedArtistKeys): Boolean {
    if (keys.ids.isEmpty() && keys.namesLower.isEmpty()) return false
    val credited = artists.orEmpty()
    if (credited.isEmpty()) return false
    return credited.any { artist ->
        val id = artist.id
        if (!id.isNullOrBlank() && id in keys.ids) return@any true
        val name = artist.name.trim().lowercase()
        name.isNotEmpty() && name in keys.namesLower
    }
}

fun List<AlbumItem>.filterToSubscribedArtists(keys: SubscribedArtistKeys): List<AlbumItem> =
    filter { it.isFromSubscribedArtist(keys) }
