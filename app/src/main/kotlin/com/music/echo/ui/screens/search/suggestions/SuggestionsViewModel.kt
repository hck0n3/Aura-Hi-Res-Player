

package iad1tya.echo.music.ui.screens.search.suggestions

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import com.music.innertube.YouTube
import com.music.innertube.models.SongItem
import com.music.innertube.models.ArtistItem
import com.music.innertube.models.WatchEndpoint
import iad1tya.echo.music.playback.PlayerConnection
import iad1tya.echo.music.playback.queues.YouTubeQueue
import androidx.navigation.NavController

@HiltViewModel
class SuggestionsViewModel @Inject constructor() : ViewModel() {
    private var currentLoadedRegion: String? = null
    
    private val _suggestionTracks = MutableStateFlow<List<SuggestionTrack>?>(null)
    val suggestionTracks: StateFlow<List<SuggestionTrack>?> = _suggestionTracks

    private val _suggestionArtists = MutableStateFlow<List<SuggestionArtist>?>(null)
    val suggestionArtists: StateFlow<List<SuggestionArtist>?> = _suggestionArtists

    private val _suggestionAlbums = MutableStateFlow<List<SuggestionAlbum>?>(null)
    val suggestionAlbums: StateFlow<List<SuggestionAlbum>?> = _suggestionAlbums

    private val _suggestionVideos = MutableStateFlow<List<SuggestionTrack>?>(null)
    val suggestionVideos: StateFlow<List<SuggestionTrack>?> = _suggestionVideos

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _isManualLoading = MutableStateFlow(false)
    val isManualLoading: StateFlow<Boolean> = _isManualLoading

    fun refresh(countryCode: String = "system", force: Boolean = false) {
        val resolvedCode = if (countryCode == "system") {
            // NOT Locale.getDefault(): the app forces "es" as its UI language, which blanks out the
            // country and made the Apple charts URL invalid. Use the device's real region instead.
            iad1tya.echo.music.utils.systemRegionCode()
        } else {
            countryCode.lowercase()
        }

        
        if (_isLoading.value && !force && currentLoadedRegion == resolvedCode) return
        
        viewModelScope.launch(Dispatchers.IO) {
            _isLoading.value = true
            if (force) _isManualLoading.value = true
            
            
            if (currentLoadedRegion != resolvedCode || force) {
                _suggestionTracks.value = null
                _suggestionArtists.value = null
                _suggestionAlbums.value = null
                _suggestionVideos.value = null
            }

            try {
                coroutineScope {
                    
                    launch {
                        try {
                            val tracks = AppleMusicScraper.fetchTopSongs(resolvedCode)
                            if (tracks.isNotEmpty()) {
                                _suggestionTracks.value = tracks
                                _suggestionArtists.value = AppleMusicScraper.getTrendingArtists(tracks)
                            }
                        } catch (e: Exception) {
                            Log.e("SuggestionsViewModel", "Failed to fetch songs", e)
                        }
                    }

                    launch {
                        try {
                            val albums = AppleMusicScraper.fetchTopAlbums(resolvedCode)
                            if (albums.isNotEmpty()) {
                                _suggestionAlbums.value = albums
                            }
                        } catch (e: Exception) {
                            Log.e("SuggestionsViewModel", "Failed to fetch albums", e)
                        }
                    }

                    launch {
                        try {
                            val videos = AppleMusicScraper.fetchTopVideos(resolvedCode)
                            if (videos.isNotEmpty()) {
                                _suggestionVideos.value = videos
                            }
                        } catch (e: Exception) {
                            Log.e("SuggestionsViewModel", "Failed to fetch videos", e)
                        }
                    }
                }

                currentLoadedRegion = resolvedCode
            } catch (e: Exception) {
                Log.e("SuggestionsViewModel", "Failed to fetch suggestions", e)
            } finally {
                _isLoading.value = false
                _isManualLoading.value = false
            }
        }
    }

    // Bidirectional artist match (from upstream Echo-Music): more robust than a one-way contains, so a
    // suggestion plays the RIGHT song instead of a wrong same-title track by another artist.
    private fun artistMatches(ytArtistName: String, suggestionArtist: String): Boolean {
        val ytNorm = ytArtistName.trim().lowercase()
        val apNorm = suggestionArtist.trim().lowercase()
        return apNorm.contains(ytNorm) || ytNorm.contains(apNorm)
    }

    fun playTrack(track: SuggestionTrack, playerConnection: PlayerConnection?) {
        viewModelScope.launch(Dispatchers.IO) {
            val query = "${track.title} ${track.artist}"
            YouTube.search(query, YouTube.SearchFilter.FILTER_SONG).onSuccess { searchResult ->
                val songs = searchResult.items.filterIsInstance<SongItem>()
                
                
                val bestMatch = songs.firstOrNull { s ->
                    s.title.equals(track.title, ignoreCase = true) &&
                    s.artists.any { a -> artistMatches(a.name, track.artist) }
                } ?:

                songs.firstOrNull { s ->
                    s.title.contains(track.title, ignoreCase = true) &&
                    s.artists.any { a -> artistMatches(a.name, track.artist) }
                } ?:

                // Prioritize the TITLE so tapping a chart song plays THAT song, not a different track by
                // the same artist (the old "artist-only" / "first result" fallbacks played wrong songs).
                songs.firstOrNull { s -> s.title.equals(track.title, ignoreCase = true) } ?:
                songs.firstOrNull { s -> s.title.contains(track.title, ignoreCase = true) } ?:

                songs.firstOrNull()

                if (bestMatch != null) {
                    withContext(Dispatchers.Main) {
                        playerConnection?.playQueue(YouTubeQueue(WatchEndpoint(videoId = bestMatch.id)))
                    }
                }
            }
        }
    }

    fun navigateToArtist(artist: SuggestionArtist, navController: NavController) {
        viewModelScope.launch(Dispatchers.IO) {
            YouTube.search(artist.name, YouTube.SearchFilter.FILTER_ARTIST)
                .onSuccess { searchResult ->
                    val firstArtist =
                        searchResult.items.filterIsInstance<ArtistItem>().firstOrNull()
                    if (firstArtist != null) {
                        withContext(Dispatchers.Main) {
                            navController.navigate("artist/${firstArtist.id}")
                        }
                    }
                }
        }
    }
    fun navigateToAlbum(album: SuggestionAlbum, navController: NavController) {
        viewModelScope.launch(Dispatchers.IO) {
            val query = "${album.title} ${album.artist}"
            YouTube.search(query, YouTube.SearchFilter.FILTER_ALBUM)
                .onSuccess { searchResult ->
                    val firstAlbum =
                        searchResult.items.filterIsInstance<com.music.innertube.models.AlbumItem>().firstOrNull()
                    if (firstAlbum != null) {
                        withContext(Dispatchers.Main) {
                            navController.navigate("album/${firstAlbum.id}")
                        }
                    }
                }
        }
    }

    fun playVideo(video: SuggestionTrack, playerConnection: PlayerConnection?) {
        viewModelScope.launch(Dispatchers.IO) {
            val query = "${video.title} ${video.artist}"
            YouTube.search(query, YouTube.SearchFilter.FILTER_SONG)
                .onSuccess { searchResult ->
                    val songs = searchResult.items.filterIsInstance<SongItem>()

                    
                    val bestMatch = songs.firstOrNull { s ->
                        s.title.equals(video.title, ignoreCase = true) &&
                        s.artists.any { a -> artistMatches(a.name, video.artist) }
                    } ?:
                    songs.firstOrNull { s ->
                        s.title.contains(video.title, ignoreCase = true) &&
                        s.artists.any { a -> artistMatches(a.name, video.artist) }
                    } ?:
                    // Title-first so the right track plays (not a different song by the same artist).
                    songs.firstOrNull { s -> s.title.equals(video.title, ignoreCase = true) } ?:
                    songs.firstOrNull { s -> s.title.contains(video.title, ignoreCase = true) } ?:
                    songs.firstOrNull()

                    if (bestMatch != null) {
                        withContext(Dispatchers.Main) {
                            playerConnection?.playQueue(YouTubeQueue(WatchEndpoint(videoId = bestMatch.id)))
                        }
                    }
                }
        }
    }
}
