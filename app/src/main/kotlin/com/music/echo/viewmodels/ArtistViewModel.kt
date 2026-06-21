

package iad1tya.echo.music.viewmodels

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.music.innertube.YouTube
import com.music.innertube.models.filterExplicit
import com.music.innertube.models.filterVideoSongs
import com.music.innertube.models.filterYoutubeShorts
import com.music.innertube.pages.ArtistPage
import iad1tya.echo.music.constants.HideExplicitKey
import iad1tya.echo.music.constants.HideVideoSongsKey
import iad1tya.echo.music.constants.HideYoutubeShortsKey
import iad1tya.echo.music.db.MusicDatabase
import iad1tya.echo.music.extensions.filterExplicit
import iad1tya.echo.music.extensions.filterExplicitAlbums
import iad1tya.echo.music.utils.dataStore
import iad1tya.echo.music.utils.get
import iad1tya.echo.music.utils.reportException
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import iad1tya.echo.music.extensions.filterVideoSongs as filterVideoSongsLocal
import iad1tya.echo.music.artistvideo.ArtistVideoCanvasProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ArtistViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: MusicDatabase,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    val artistId = savedStateHandle.get<String>("artistId")!!
    var artistPage by mutableStateOf<ArtistPage?>(null)
    
    private val _artistVideoUrl = MutableStateFlow<String?>(null)
    val artistVideoUrl: StateFlow<String?> = _artistVideoUrl

    private val _artistVideoSong = MutableStateFlow<com.music.innertube.models.SongItem?>(null)
    val artistVideoSong: StateFlow<com.music.innertube.models.SongItem?> = _artistVideoSong
    
    val libraryArtist = database.artist(artistId)
        .stateIn(viewModelScope, SharingStarted.Lazily, null)
    val librarySongs = context.dataStore.data
        .map { (it[HideExplicitKey] ?: false) to (it[HideVideoSongsKey] ?: false) }
        .distinctUntilChanged()
        .flatMapLatest { (hideExplicit, hideVideoSongs) ->
            database.artistSongsPreview(artistId).map { it.filterExplicit(hideExplicit).filterVideoSongsLocal(hideVideoSongs) }
        }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    val libraryAlbums = context.dataStore.data
        .map { it[HideExplicitKey] ?: false }
        .distinctUntilChanged()
        .flatMapLatest { hideExplicit ->
            database.artistAlbumsPreview(artistId).map { it.filterExplicitAlbums(hideExplicit) }
        }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    init {
        
        viewModelScope.launch {
            context.dataStore.data
                .map {
                    Triple(
                        it[HideExplicitKey] ?: false,
                        it[HideVideoSongsKey] ?: false,
                        it[HideYoutubeShortsKey] ?: false
                    )
                }
                .distinctUntilChanged()
                .collect {
                    fetchArtistsFromYTM()
                }
        }
    }

    fun fetchArtistsFromYTM() {
        viewModelScope.launch {
            val hideExplicit = context.dataStore.get(HideExplicitKey, false)
            val hideVideoSongs = context.dataStore.get(HideVideoSongsKey, false)
            val hideYoutubeShorts = context.dataStore.get(HideYoutubeShortsKey, false)
            // Retry transient failures (YouTube throttling) so the screen doesn't get stuck on the spinner,
            // which forced the user to leave and re-enter the artist several times.
            var attempt = 0
            while (artistPage == null && attempt < 3) {
            YouTube.artist(artistId)
                .onSuccess { page ->
                    val filteredSections = page.sections
                        .map { section ->
                            section.copy(items = section.items.filterExplicit(hideExplicit).filterVideoSongs(hideVideoSongs).filterYoutubeShorts(hideYoutubeShorts))
                        }
                        .filter { section -> section.items.isNotEmpty() }

                    artistPage = page.copy(sections = filteredSections)
                    
                    
                    val topSongsSection = page.sections.find { it.items.firstOrNull() is com.music.innertube.models.SongItem }
                    topSongsSection?.items?.forEach { item ->
                        if (item is com.music.innertube.models.SongItem) {
                            val canvas = ArtistVideoCanvasProvider.getBySongArtist(
                                song = item.title,
                                artist = page.artist?.title ?: ""
                            )
                            if (canvas?.preferredAnimationUrl != null) {
                                _artistVideoUrl.value = canvas.preferredAnimationUrl
                                _artistVideoSong.value = item
                                return@forEach
                            }
                        }
                    }

                    // Preload each album's contents in the background so opening an album (and its songs)
                    // is instant. Skips albums already cached; gentle concurrency to not hammer the network.
                    launch(Dispatchers.IO) {
                        val albumIds = filteredSections.flatMap { it.items }
                            .filterIsInstance<com.music.innertube.models.AlbumItem>()
                            .map { it.id }.distinct().take(6)
                        if (albumIds.isEmpty()) return@launch
                        val sem = Semaphore(2)
                        coroutineScope {
                            albumIds.map { id ->
                                async {
                                    sem.withPermit {
                                        if (database.album(id).first() != null) return@withPermit
                                        YouTube.album(id, withSongs = false).onSuccess { albumPage ->
                                            runCatching { database.transaction { insert(albumPage) } }
                                        }
                                    }
                                }
                            }.awaitAll()
                        }
                    }

                    // "Aparece en" (Appears on), like Spotify: albums where this artist is a guest. Built
                    // from iTunes guest credits + a parallel YouTube lookup, appended as its own section.
                    launch(Dispatchers.IO) {
                        val artistName = page.artist?.title ?: return@launch
                        val norm = iad1tya.echo.music.utils.iTunesDiscography::normalizeTitle
                        // Cap at 40 (covers virtually all real collaborations) with GENTLE concurrency:
                        // resolving the full uncapped iTunes list (100+ items) with high concurrency flooded
                        // YouTube and made the whole app's responses slower. 40 keeps it complete enough
                        // without hammering the network.
                        val guest = iad1tya.echo.music.utils.iTunesDiscography
                            .fetchAppearsOn(artistName, "us")
                            .take(10)
                        if (guest.isEmpty()) return@launch
                        val sem = Semaphore(1)
                        val found = coroutineScope {
                            guest.map { (title, primary) ->
                                async {
                                    sem.withPermit {
                                        val target = norm(title)
                                        fun matches(t: String) =
                                            t == target || (target.length >= 4 && (t.contains(target) || target.contains(t)))
                                        // As a full album/collection...
                                        val album = YouTube.search("$primary $title", YouTube.SearchFilter.FILTER_ALBUM)
                                            .getOrNull()?.items
                                            ?.filterIsInstance<com.music.innertube.models.AlbumItem>()
                                            ?.firstOrNull { matches(norm(it.title)) }
                                        if (album != null) return@withPermit album as com.music.innertube.models.YTItem
                                        // ...otherwise as a collab/feat single (most collaborations are songs).
                                        val song = YouTube.search("$primary $title", YouTube.SearchFilter.FILTER_SONG)
                                            .getOrNull()?.items
                                            ?.filterIsInstance<com.music.innertube.models.SongItem>()
                                            ?.firstOrNull { s ->
                                                matches(norm(s.title)) &&
                                                    s.artists.any { it.name.contains(artistName, ignoreCase = true) }
                                            }
                                        song as? com.music.innertube.models.YTItem
                                    }
                                }
                            }.awaitAll().filterNotNull()
                        }
                        val items = found.distinctBy { it.id }.filter { !hideExplicit || !it.explicit }
                        if (items.isEmpty()) return@launch
                        withContext(Dispatchers.Main) {
                            val current = artistPage ?: return@withContext
                            if (current.sections.any { it.title.equals("Aparece en", ignoreCase = true) }) return@withContext
                            artistPage = current.copy(
                                sections = current.sections + com.music.innertube.pages.ArtistSection(
                                    title = "Aparece en",
                                    items = items,
                                    moreEndpoint = null,
                                ),
                            )
                        }
                    }
                }.onFailure {
                    if (attempt >= 2) reportException(it)
                }
            if (artistPage == null) {
                attempt++
                if (attempt < 3) kotlinx.coroutines.delay(700L * attempt)
            }
            }
        }
    }
}
