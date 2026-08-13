

package iad1tya.echo.music.ui.screens.search.suggestions

import android.content.Context
import android.util.Log
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import javax.inject.Inject
import com.music.innertube.YouTube
import com.music.innertube.models.AlbumItem
import com.music.innertube.models.SongItem
import com.music.innertube.models.ArtistItem
import com.music.innertube.models.WatchEndpoint
import iad1tya.echo.music.playback.PlayerConnection
import iad1tya.echo.music.playback.queues.YouTubeQueue
import androidx.navigation.NavController

@HiltViewModel
class SuggestionsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
) : ViewModel() {
    private var currentLoadedRegion: String? = null
    
    private val _suggestionTracks = MutableStateFlow<List<SuggestionTrack>?>(null)
    val suggestionTracks: StateFlow<List<SuggestionTrack>?> = _suggestionTracks

    private val _suggestionArtists = MutableStateFlow<List<SuggestionArtist>?>(null)
    val suggestionArtists: StateFlow<List<SuggestionArtist>?> = _suggestionArtists

    private val _suggestionAlbums = MutableStateFlow<List<SuggestionAlbum>?>(null)
    val suggestionAlbums: StateFlow<List<SuggestionAlbum>?> = _suggestionAlbums

    private val _suggestionVideos = MutableStateFlow<List<SuggestionTrack>?>(null)
    val suggestionVideos: StateFlow<List<SuggestionTrack>?> = _suggestionVideos

    private val _youtubeTopTracks = MutableStateFlow<List<SuggestionTrack>?>(null)
    val youtubeTopTracks: StateFlow<List<SuggestionTrack>?> = _youtubeTopTracks

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _isManualLoading = MutableStateFlow(false)
    val isManualLoading: StateFlow<Boolean> = _isManualLoading

    // Pre-resolution cache for videoId-less suggestions (Apple/charts scrapes). Keyed by
    // kind+title+artist so a song resolve cannot poison a video tap (and the reverse). Filled in
    // the BACKGROUND when the tab loads (see prewarm). Only successful resolutions are stored.
    private val resolvedIds = java.util.concurrent.ConcurrentHashMap<String, String>()
    private val prewarmMax = 15
    private val prewarmConcurrency = 3

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
                _youtubeTopTracks.value = null
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

                    // YouTube Music Top: real chart songs WITH their video ids, so they play exactly (no
                    // search needed). Pulled from FEmusic_charts. We take EVERY chart song (not just sections
                    // tagged TOP/TRENDING): that tag is derived from the English section title, so in a
                    // Spanish (or any non-English) region the titles are localized, every section falls back
                    // to GENRE, and the old filter returned nothing -> the section never showed.
                    launch {
                        try {
                            val charts = YouTube.getChartsPage().getOrNull()
                            val topSongs = charts?.sections
                                ?.flatMap { it.items }
                                ?.filterIsInstance<SongItem>()
                                ?.distinctBy { it.id }
                                ?.take(40)
                                ?.mapIndexed { index, s ->
                                    SuggestionTrack(
                                        rank = index + 1,
                                        title = s.title,
                                        artist = s.artists.joinToString { it.name },
                                        thumbnailUrl = s.thumbnail,
                                        videoId = s.id,
                                    )
                                }
                            if (!topSongs.isNullOrEmpty()) _youtubeTopTracks.value = topSongs
                        } catch (e: Exception) {
                            Log.e("SuggestionsViewModel", "Failed to fetch YouTube Music top", e)
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

    private suspend fun resolveVideoId(
        title: String,
        artist: String,
        kind: SuggestionMatch.Kind,
    ): String? {
        val filter = if (kind == SuggestionMatch.Kind.VIDEO) {
            YouTube.SearchFilter.FILTER_VIDEO
        } else {
            YouTube.SearchFilter.FILTER_SONG
        }
        val songs = YouTube.search("$title $artist", filter)
            .getOrNull()?.items?.filterIsInstance<SongItem>() ?: return null
        val index = SuggestionMatch.pickTitleArtist(
            titles = songs.map { it.title },
            artists = songs.map { it.artists.map { a -> a.name } },
            expectedTitle = title,
            expectedArtist = artist,
        ) ?: return null
        return songs[index].id
    }

    /** Pre-resolve visible videoId-less suggestions. [kind] must match the tap handler that will
     *  read the cache — songs and videos never share a key. */
    suspend fun prewarm(items: List<SuggestionTrack>, kind: SuggestionMatch.Kind) {
        val pending = items
            .asSequence()
            .filter { it.videoId == null }
            .filter { resolvedIds[SuggestionMatch.cacheKey(kind, it.title, it.artist)] == null }
            .distinctBy { SuggestionMatch.cacheKey(kind, it.title, it.artist) }
            .take(prewarmMax)
            .toList()
        if (pending.isEmpty()) return
        withContext(Dispatchers.IO) {
            val gate = Semaphore(prewarmConcurrency)
            coroutineScope {
                pending.forEach { item ->
                    launch {
                        gate.withPermit {
                            val key = SuggestionMatch.cacheKey(kind, item.title, item.artist)
                            if (resolvedIds[key] != null) return@withPermit
                            try {
                                resolveVideoId(item.title, item.artist, kind)?.let { resolvedIds[key] = it }
                            } catch (e: Exception) {
                                Log.e("SuggestionsViewModel", "prewarm resolve failed", e)
                            }
                        }
                    }
                }
            }
        }
    }

    // Surfaces a brief message on the main thread. Tap handlers used to swallow both search failures
    // (flaky network) and no-match cases silently, so a tap did nothing with zero feedback (P36).
    private suspend fun showToast(resId: Int) {
        withContext(Dispatchers.Main) {
            Toast.makeText(context, resId, Toast.LENGTH_SHORT).show()
        }
    }

    fun playTrack(track: SuggestionTrack, playerConnection: PlayerConnection?) {
        viewModelScope.launch(Dispatchers.IO) {
            if (track.videoId != null) {
                withContext(Dispatchers.Main) {
                    playerConnection?.playQueue(YouTubeQueue(WatchEndpoint(videoId = track.videoId)))
                }
                return@launch
            }
            val key = SuggestionMatch.cacheKey(SuggestionMatch.Kind.SONG, track.title, track.artist)
            resolvedIds[key]?.let { cachedId ->
                withContext(Dispatchers.Main) {
                    playerConnection?.playQueue(YouTubeQueue(WatchEndpoint(videoId = cachedId)))
                }
                return@launch
            }
            val query = "${track.title} ${track.artist}"
            YouTube.search(query, YouTube.SearchFilter.FILTER_SONG).onSuccess { searchResult ->
                val songs = searchResult.items.filterIsInstance<SongItem>()
                val index = SuggestionMatch.pickTitleArtist(
                    titles = songs.map { it.title },
                    artists = songs.map { it.artists.map { a -> a.name } },
                    expectedTitle = track.title,
                    expectedArtist = track.artist,
                )
                if (index != null) {
                    resolvedIds[key] = songs[index].id
                    withContext(Dispatchers.Main) {
                        playerConnection?.playQueue(YouTubeQueue(WatchEndpoint(videoId = songs[index].id)))
                    }
                } else {
                    showToast(iad1tya.echo.music.R.string.no_results_found)
                }
            }.onFailure {
                Log.e("SuggestionsViewModel", "playTrack search failed", it)
                showToast(iad1tya.echo.music.R.string.error_unknown)
            }
        }
    }

    fun navigateToArtist(artist: SuggestionArtist, navController: NavController) {
        viewModelScope.launch(Dispatchers.IO) {
            YouTube.search(artist.name, YouTube.SearchFilter.FILTER_ARTIST)
                .onSuccess { searchResult ->
                    val artists = searchResult.items.filterIsInstance<ArtistItem>()
                    val index = SuggestionMatch.pickArtist(
                        names = artists.map { it.title },
                        expected = artist.name,
                    )
                    if (index != null) {
                        withContext(Dispatchers.Main) {
                            navController.navigate("artist/${artists[index].id}")
                        }
                    } else {
                        showToast(iad1tya.echo.music.R.string.no_results_found)
                    }
                }
                .onFailure {
                    Log.e("SuggestionsViewModel", "navigateToArtist search failed", it)
                    showToast(iad1tya.echo.music.R.string.error_unknown)
                }
        }
    }
    fun navigateToAlbum(album: SuggestionAlbum, navController: NavController) {
        viewModelScope.launch(Dispatchers.IO) {
            val query = "${album.title} ${album.artist}"
            YouTube.search(query, YouTube.SearchFilter.FILTER_ALBUM)
                .onSuccess { searchResult ->
                    val albums = searchResult.items.filterIsInstance<AlbumItem>()
                    val index = SuggestionMatch.pickTitleArtist(
                        titles = albums.map { it.title },
                        artists = albums.map { it.artists.orEmpty().map { a -> a.name } },
                        expectedTitle = album.title,
                        expectedArtist = album.artist,
                    )
                    if (index != null) {
                        withContext(Dispatchers.Main) {
                            navController.navigate("album/${albums[index].id}")
                        }
                    } else {
                        showToast(iad1tya.echo.music.R.string.no_results_found)
                    }
                }
                .onFailure {
                    Log.e("SuggestionsViewModel", "navigateToAlbum search failed", it)
                    showToast(iad1tya.echo.music.R.string.error_unknown)
                }
        }
    }

    fun playVideo(video: SuggestionTrack, playerConnection: PlayerConnection?) {
        viewModelScope.launch(Dispatchers.IO) {
            val key = SuggestionMatch.cacheKey(SuggestionMatch.Kind.VIDEO, video.title, video.artist)
            resolvedIds[key]?.let { cachedId ->
                withContext(Dispatchers.Main) {
                    playerConnection?.playQueue(YouTubeQueue(WatchEndpoint(videoId = cachedId)))
                    playerConnection?.enterVideoModeIfNeeded(forceFromUserTap = true)
                }
                return@launch
            }
            val query = "${video.title} ${video.artist}"
            YouTube.search(query, YouTube.SearchFilter.FILTER_VIDEO)
                .onSuccess { searchResult ->
                    val songs = searchResult.items.filterIsInstance<SongItem>()
                    val index = SuggestionMatch.pickTitleArtist(
                        titles = songs.map { it.title },
                        artists = songs.map { it.artists.map { a -> a.name } },
                        expectedTitle = video.title,
                        expectedArtist = video.artist,
                    )
                    if (index != null) {
                        resolvedIds[key] = songs[index].id
                        withContext(Dispatchers.Main) {
                            playerConnection?.playQueue(YouTubeQueue(WatchEndpoint(videoId = songs[index].id)))
                            playerConnection?.enterVideoModeIfNeeded(forceFromUserTap = true)
                        }
                    } else {
                        showToast(iad1tya.echo.music.R.string.no_results_found)
                    }
                }
                .onFailure {
                    Log.e("SuggestionsViewModel", "playVideo search failed", it)
                    showToast(iad1tya.echo.music.R.string.error_unknown)
                }
        }
    }
}
