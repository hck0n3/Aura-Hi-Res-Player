package iad1tya.echo.music.dislike

import android.content.Context
import androidx.datastore.preferences.core.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import iad1tya.echo.music.constants.DislikedItemsKey
import iad1tya.echo.music.utils.dataStore
import iad1tya.echo.music.utils.get
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * "No me gusta" store: remembers the songs, artists, albums and playlists the user explicitly disliked,
 * so they are skipped during playback and filtered out of every recommendation surface — the algorithm
 * stops surfacing them. Persisted as a small JSON object of id sets.
 */
@Singleton
class DislikeStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    data class Disliked(
        val songs: Set<String> = emptySet(),
        val artists: Set<String> = emptySet(),
        val albums: Set<String> = emptySet(),
        val playlists: Set<String> = emptySet(),
        // Graded "Menos de esto" ("show less"): a MILD demotion, not a hard drop. Kept separate from the
        // hard-dislike sets above so the algorithm can nudge these later without ever removing them.
        val softSongs: Set<String> = emptySet(),
        val softArtists: Set<String> = emptySet(),
    ) {
        val isEmpty: Boolean get() = songs.isEmpty() && artists.isEmpty() && albums.isEmpty() && playlists.isEmpty() &&
            softSongs.isEmpty() && softArtists.isEmpty()
    }

    /** Live disliked sets. */
    val disliked: Flow<Disliked> = context.dataStore.data.map { parse(it[DislikedItemsKey]) }

    suspend fun snapshot(): Disliked = parse(context.dataStore.get(DislikedItemsKey, ""))

    suspend fun dislikeSong(id: String) = mutate { it.copy(songs = it.songs + id) }
    suspend fun undislikeSong(id: String) = mutate { it.copy(songs = it.songs - id) }
    suspend fun dislikeArtist(id: String) = mutate { it.copy(artists = it.artists + id) }
    suspend fun undislikeArtist(id: String) = mutate { it.copy(artists = it.artists - id) }
    suspend fun dislikeAlbum(id: String) = mutate { it.copy(albums = it.albums + id) }
    suspend fun undislikeAlbum(id: String) = mutate { it.copy(albums = it.albums - id) }
    suspend fun dislikePlaylist(id: String) = mutate { it.copy(playlists = it.playlists + id) }
    suspend fun undislikePlaylist(id: String) = mutate { it.copy(playlists = it.playlists - id) }

    // "Menos de esto" — graded negative feedback (a mild demotion between a weak skip and the hard dislike).
    suspend fun softDislikeSong(id: String) = mutate { it.copy(softSongs = it.softSongs + id) }
    suspend fun undoSoftDislikeSong(id: String) = mutate { it.copy(softSongs = it.softSongs - id) }
    suspend fun softDislikeArtist(id: String) = mutate { it.copy(softArtists = it.softArtists + id) }
    suspend fun undoSoftDislikeArtist(id: String) = mutate { it.copy(softArtists = it.softArtists - id) }

    private suspend fun mutate(block: (Disliked) -> Disliked) {
        context.dataStore.edit { prefs ->
            prefs[DislikedItemsKey] = toJson(block(parse(prefs[DislikedItemsKey])))
        }
    }

    private fun parse(s: String?): Disliked {
        if (s.isNullOrBlank()) return Disliked()
        return runCatching {
            val o = JSONObject(s)
            Disliked(
                songs = o.optJSONArray("songs").toStringSet(),
                artists = o.optJSONArray("artists").toStringSet(),
                albums = o.optJSONArray("albums").toStringSet(),
                playlists = o.optJSONArray("playlists").toStringSet(),
                // Backward-compatible: old blobs without these keys → optJSONArray returns null → empty set.
                softSongs = o.optJSONArray("softSongs").toStringSet(),
                softArtists = o.optJSONArray("softArtists").toStringSet(),
            )
        }.getOrDefault(Disliked())
    }

    private fun toJson(d: Disliked): String = JSONObject()
        .put("songs", JSONArray(d.songs.toList()))
        .put("artists", JSONArray(d.artists.toList()))
        .put("albums", JSONArray(d.albums.toList()))
        .put("playlists", JSONArray(d.playlists.toList()))
        .put("softSongs", JSONArray(d.softSongs.toList()))
        .put("softArtists", JSONArray(d.softArtists.toList()))
        .toString()

    private fun JSONArray?.toStringSet(): Set<String> {
        if (this == null) return emptySet()
        return (0 until length()).mapNotNull { optString(it).takeIf { s -> s.isNotBlank() } }.toSet()
    }
}
