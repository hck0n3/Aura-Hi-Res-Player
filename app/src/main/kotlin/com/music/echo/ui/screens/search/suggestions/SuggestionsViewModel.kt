

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
    // normalize(title)+"|"+normalize(artist); value is the resolved YouTube videoId. Filled in the
    // BACKGROUND when the tab loads (see prewarm) so a later tap plays INSTANTLY from the cache
    // instead of doing a search round-trip at tap time. Only successful resolutions are stored (no
    // failure sentinel), so a transient network miss during prewarm just falls back to on-tap search.
    // ConcurrentHashMap: prewarm workers write from several coroutines while taps read concurrently.
    private val resolvedIds = java.util.concurrent.ConcurrentHashMap<String, String>()
    // Bounds so pre-resolution never becomes a heat/battery/network hog: resolve at most this many
    // visible items, at most this many searches at once. Do NOT fire 100 searches.
    private val prewarmMax = 15
    private val prewarmConcurrency = 3

    private fun cacheKey(title: String, artist: String) = normalize(title) + "|" + normalize(artist)

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

    /** Normalize a title/artist for matching. Suggestion sources (Apple charts) use typographic
     *  punctuation — curly ’ quotes, en/em dashes — where YouTube metadata uses straight ASCII, and
     *  "feat." credits appear on one side only; a strict equals/contains fails on those LEGIT matches
     *  and taps show "No results" even when result #1 is the right song. Lowercases, straightens
     *  quotes/dashes, strips "(feat. …)"/"[feat. …]" and trailing "feat. …" credits, collapses
     *  whitespace. */
    private fun normalize(s: String): String =
        s.lowercase()
            .replace('‘', '\'') // ‘ left single quote
            .replace('’', '\'') // ’ right single quote (Apple-style apostrophe)
            .replace('“', '"') // " left double quote
            .replace('”', '"') // " right double quote
            .replace('–', '-') // – en dash
            .replace('—', '-') // — em dash
            // Parenthesized/bracketed featuring credits: "(feat. X)", "[ft. X]", "(featuring X)".
            .replace(Regex("""\s*[(\[](?:feat|ft|featuring)\.?\s[^)\]]*[)\]]"""), " ")
            // Trailing bare featuring credit: "… feat. X".
            .replace(Regex("""\s+(?:feat|ft|featuring)\.?\s.*$"""), " ")
            .replace(Regex("""\s+"""), " ")
            .trim()

    // Bidirectional artist match (from upstream Echo-Music): more robust than a one-way contains, so a
    // suggestion plays the RIGHT song instead of a wrong same-title track by another artist. Compared
    // NORMALIZED so punctuation variants ("A$AP", curly quotes) don't break legit matches.
    private fun artistMatches(ytArtistName: String, suggestionArtist: String): Boolean {
        val ytNorm = normalize(ytArtistName)
        val apNorm = normalize(suggestionArtist)
        if (ytNorm.isEmpty() || apNorm.isEmpty()) return false
        return apNorm.contains(ytNorm) || ytNorm.contains(apNorm)
    }

    /** Bidirectional normalized title match — either side may carry extra qualifiers the other lacks
     *  (feat credits already stripped; remaining "(Remastered)"-style tails still match one-way). */
    private fun titleMatches(ytTitle: String, suggestionTitle: String): Boolean {
        val ytNorm = normalize(ytTitle)
        val sgNorm = normalize(suggestionTitle)
        if (ytNorm.isEmpty() || sgNorm.isEmpty()) return false
        return ytNorm.contains(sgNorm) || sgNorm.contains(ytNorm)
    }

    /** The exact search + best-match used at tap time, isolated so a background pre-resolve produces a
     *  BYTE-IDENTICAL videoId (same infinite-radio seed). Returns the id, or null on failure/no-match. */
    private suspend fun resolveVideoId(title: String, artist: String): String? {
        val songs = YouTube.search("$title $artist", YouTube.SearchFilter.FILTER_SONG)
            .getOrNull()?.items?.filterIsInstance<SongItem>() ?: return null
        return (songs.firstOrNull { s ->
            normalize(s.title) == normalize(title) &&
            s.artists.any { a -> artistMatches(a.name, artist) }
        } ?: songs.firstOrNull { s ->
            titleMatches(s.title, title) &&
            s.artists.any { a -> artistMatches(a.name, artist) }
        })?.id
    }

    /** Pre-resolve the videoIds of the VISIBLE videoId-less suggestions in the background, so a tap
     *  later plays instantly from [resolvedIds] instead of searching at tap time. Fire-and-forget:
     *  the caller (a LaunchedEffect in the tab) does not block on it, and because it is a suspend fn
     *  driven by that LaunchedEffect its work is CANCELLED when the tab leaves composition or the
     *  visible set changes (no leak, bounded heat/battery). Skips items that already carry a videoId
     *  (YouTube charts) or are already cached; caps the count ([prewarmMax]) and the concurrency
     *  ([prewarmConcurrency]) so it never fans out into 100 searches. */
    suspend fun prewarm(items: List<SuggestionTrack>) {
        val pending = items
            .asSequence()
            .filter { it.videoId == null }                              // charts items are already instant
            .filter { resolvedIds[cacheKey(it.title, it.artist)] == null } // don't re-resolve cached
            .distinctBy { cacheKey(it.title, it.artist) }
            .take(prewarmMax)
            .toList()
        if (pending.isEmpty()) return
        withContext(Dispatchers.IO) {
            val gate = Semaphore(prewarmConcurrency)
            coroutineScope {
                pending.forEach { item ->
                    launch {
                        gate.withPermit {
                            val key = cacheKey(item.title, item.artist)
                            if (resolvedIds[key] != null) return@withPermit
                            try {
                                resolveVideoId(item.title, item.artist)?.let { resolvedIds[key] = it }
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
            // YouTube Music chart song: we already have the exact video id, play it directly.
            if (track.videoId != null) {
                withContext(Dispatchers.Main) {
                    playerConnection?.playQueue(YouTubeQueue(WatchEndpoint(videoId = track.videoId)))
                }
                return@launch
            }
            // Pre-resolved in the background when the tab loaded? Play INSTANTLY from the cache — no
            // search round-trip at tap time. Same best-match id as the on-tap search would produce.
            val key = cacheKey(track.title, track.artist)
            resolvedIds[key]?.let { cachedId ->
                withContext(Dispatchers.Main) {
                    playerConnection?.playQueue(YouTubeQueue(WatchEndpoint(videoId = cachedId)))
                }
                return@launch
            }
            val query = "${track.title} ${track.artist}"
            YouTube.search(query, YouTube.SearchFilter.FILTER_SONG).onSuccess { searchResult ->
                val songs = searchResult.items.filterIsInstance<SongItem>()


                // BOTH title AND artist must match. The old artist-agnostic fallbacks (title-only, then
                // a blind first result) could resolve a cover / remix / any-song, so a "top" tap played
                // a DIFFERENT song. If nothing matches on both, we show "no results" rather than lie.
                // Compared NORMALIZED (straight quotes/dashes, feat-credits stripped) and the contains
                // check is BIDIRECTIONAL, so punctuation variants don't fail a legit tap.
                val bestMatch = songs.firstOrNull { s ->
                    normalize(s.title) == normalize(track.title) &&
                    s.artists.any { a -> artistMatches(a.name, track.artist) }
                } ?:
                songs.firstOrNull { s ->
                    titleMatches(s.title, track.title) &&
                    s.artists.any { a -> artistMatches(a.name, track.artist) }
                }

                if (bestMatch != null) {
                    resolvedIds[key] = bestMatch.id // cache so any later tap on this item is instant
                    withContext(Dispatchers.Main) {
                        playerConnection?.playQueue(YouTubeQueue(WatchEndpoint(videoId = bestMatch.id)))
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
                    val firstArtist =
                        searchResult.items.filterIsInstance<ArtistItem>().firstOrNull()
                    if (firstArtist != null) {
                        withContext(Dispatchers.Main) {
                            navController.navigate("artist/${firstArtist.id}")
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
                    val firstAlbum =
                        searchResult.items.filterIsInstance<com.music.innertube.models.AlbumItem>().firstOrNull()
                    if (firstAlbum != null) {
                        withContext(Dispatchers.Main) {
                            navController.navigate("album/${firstAlbum.id}")
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
            // Pre-resolved in the background when the tab loaded? Play INSTANTLY from the cache.
            val key = cacheKey(video.title, video.artist)
            resolvedIds[key]?.let { cachedId ->
                withContext(Dispatchers.Main) {
                    playerConnection?.playQueue(YouTubeQueue(WatchEndpoint(videoId = cachedId)))
                }
                return@launch
            }
            val query = "${video.title} ${video.artist}"
            YouTube.search(query, YouTube.SearchFilter.FILTER_SONG)
                .onSuccess { searchResult ->
                    val songs = searchResult.items.filterIsInstance<SongItem>()


                    // BOTH title AND artist must match (no artist-agnostic / first-result fallback), so a
                    // tap plays THAT song, never a same-title cover or a different track by the artist.
                    // Compared NORMALIZED with a BIDIRECTIONAL contains fallback (see playTrack).
                    val bestMatch = songs.firstOrNull { s ->
                        normalize(s.title) == normalize(video.title) &&
                        s.artists.any { a -> artistMatches(a.name, video.artist) }
                    } ?:
                    songs.firstOrNull { s ->
                        titleMatches(s.title, video.title) &&
                        s.artists.any { a -> artistMatches(a.name, video.artist) }
                    }

                    if (bestMatch != null) {
                        resolvedIds[key] = bestMatch.id // cache so any later tap on this item is instant
                        withContext(Dispatchers.Main) {
                            playerConnection?.playQueue(YouTubeQueue(WatchEndpoint(videoId = bestMatch.id)))
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
