package iad1tya.echo.music.utils

import com.music.innertube.models.AlbumItem
import com.music.innertube.models.ArtistItem
import com.music.innertube.models.PlaylistItem
import com.music.innertube.models.SongItem
import com.music.innertube.models.YTItem
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

/**
 * Subscribed + most-played + liked-song artists. Novedades (and any "for you" shelf) must use this
 * instead of the global YTM chart — never fall back to world-trending if the set is empty.
 */
suspend fun MusicDatabase.tasteArtistKeys(): SubscribedArtistKeys {
    val subscribed = subscribedArtistKeys()
    val played = mostPlayedArtists(fromTimeStamp = 0L, limit = 40).first()
    val likedArtists = likedSongsByCreateDateAsc().first()
        .asSequence()
        .take(80)
        .flatMap { it.artists }
    val ids = buildSet {
        addAll(subscribed.ids)
        played.forEach { artist -> if (artist.id.isNotBlank()) add(artist.id) }
        likedArtists.forEach { artist -> if (artist.id.isNotBlank()) add(artist.id) }
    }
    val names = buildSet {
        addAll(subscribed.namesLower)
        played.forEach { artist ->
            val name = artist.artist.name.trim().lowercase()
            if (name.isNotEmpty()) add(name)
        }
        likedArtists.forEach { artist ->
            val name = artist.name.trim().lowercase()
            if (name.isNotEmpty()) add(name)
        }
    }
    return SubscribedArtistKeys(ids = ids, namesLower = names)
}

/** True when any credited artist matches a subscribed id or name. */
fun AlbumItem.isFromSubscribedArtist(keys: SubscribedArtistKeys): Boolean {
    if (keys.ids.isEmpty() && keys.namesLower.isEmpty()) return false
    val credited = artists.orEmpty()
    if (credited.isEmpty()) return false
    return credited.any { artist -> matchesTasteArtist(artist.id, artist.name, keys) }
}

fun SongItem.isFromTasteArtist(keys: SubscribedArtistKeys): Boolean {
    if (keys.ids.isEmpty() && keys.namesLower.isEmpty()) return false
    if (artists.isEmpty()) return false
    return artists.any { artist -> matchesTasteArtist(artist.id, artist.name, keys) }
}

fun YTItem.isFromTasteArtist(keys: SubscribedArtistKeys): Boolean = when (this) {
    is AlbumItem -> isFromSubscribedArtist(keys)
    is SongItem -> isFromTasteArtist(keys)
    is ArtistItem -> matchesTasteArtist(id, title, keys)
    is PlaylistItem -> author?.let { matchesTasteArtist(it.id, it.name, keys) } == true
}

fun List<AlbumItem>.filterToSubscribedArtists(keys: SubscribedArtistKeys): List<AlbumItem> =
    filter { it.isFromSubscribedArtist(keys) }

fun List<SongItem>.filterSongsToTasteArtists(keys: SubscribedArtistKeys): List<SongItem> =
    filter { it.isFromTasteArtist(keys) }

fun List<YTItem>.filterToTasteArtists(keys: SubscribedArtistKeys): List<YTItem> =
    filter { it.isFromTasteArtist(keys) }

private fun matchesTasteArtist(id: String?, name: String, keys: SubscribedArtistKeys): Boolean {
    if (!id.isNullOrBlank() && id in keys.ids) return true
    val lower = name.trim().lowercase()
    return lower.isNotEmpty() && lower in keys.namesLower
}
