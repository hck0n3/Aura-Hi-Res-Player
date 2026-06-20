

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
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
    database: MusicDatabase,
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

                    // Discography completion: cross-check the artist's REAL discography (from iTunes)
                    // against what YouTube returns and look up the missing albums on YouTube one by one,
                    // then merge them into the album section. This surfaces albums YouTube omits from the
                    // artist page (e.g. "Privé"), so the whole discography lives ON the artist instead of
                    // forcing a separate search. Runs in the background after the first render.
                    launch(kotlinx.coroutines.Dispatchers.IO) {
                        val artistName = page.artist?.title ?: return@launch
                        val region = iad1tya.echo.music.utils.systemRegionCode()
                        val norm = iad1tya.echo.music.utils.iTunesDiscography::normalizeTitle

                        fun creditedToArtist(a: com.music.innertube.models.AlbumItem): Boolean =
                            a.artists?.any { it.id == artistId || it.name.contains(artistName, ignoreCase = true) } == true

                        // 1) Cheap pass: albums YouTube returns when searching the artist name.
                        val byArtistSearch = YouTube.search(artistName, YouTube.SearchFilter.FILTER_ALBUM)
                            .getOrNull()?.items
                            ?.filterIsInstance<com.music.innertube.models.AlbumItem>()
                            ?.filter(::creditedToArtist)
                            .orEmpty()

                        // 1b) YouTube's full "more albums" list (the carousel only previews a few).
                        val albumSection = (artistPage?.sections ?: page.sections)
                            .firstOrNull { it.items.firstOrNull() is com.music.innertube.models.AlbumItem }
                        val fromMore = albumSection?.moreEndpoint?.let { ep ->
                            YouTube.artistItems(ep).getOrNull()?.items
                                ?.filterIsInstance<com.music.innertube.models.AlbumItem>()
                                ?.filter(::creditedToArtist)
                        }.orEmpty()

                        // Titles we already have (artist page + the passes above), normalized for matching.
                        val haveTitles = ((artistPage?.sections?.flatMap { it.items } ?: emptyList()) + byArtistSearch + fromMore)
                            .filterIsInstance<com.music.innertube.models.AlbumItem>()
                            .map { norm(it.title) }
                            .toMutableSet()

                        // 2) Thorough pass: for each iTunes album we DON'T have, look it up on YouTube.
                        val itunesTitles = iad1tya.echo.music.utils.iTunesDiscography.fetchAlbumTitles(artistName, region)
                        val missingTitles = itunesTitles
                            .filter { norm(it) !in haveTitles }
                            .distinctBy { norm(it) }
                            .take(12)

                        val byTitleLookup = mutableListOf<com.music.innertube.models.AlbumItem>()
                        for (title in missingTitles) {
                            val target = norm(title)
                            if (target.isBlank() || target in haveTitles) continue
                            val match = YouTube.search("$artistName $title", YouTube.SearchFilter.FILTER_ALBUM)
                                .getOrNull()?.items
                                ?.filterIsInstance<com.music.innertube.models.AlbumItem>()
                                ?.firstOrNull {
                                    creditedToArtist(it) && run {
                                        val yt = norm(it.title)
                                        yt == target || (target.length >= 4 && (yt.contains(target) || target.contains(yt)))
                                    }
                                }
                            if (match != null) {
                                byTitleLookup.add(match)
                                haveTitles.add(target)
                            }
                        }

                        val extra = (byArtistSearch + fromMore + byTitleLookup)
                            .filter { !hideExplicit || !it.explicit }
                            .distinctBy { it.id }
                        if (extra.isEmpty()) return@launch

                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                            val current = artistPage ?: return@withContext
                            val existingIds = current.sections.flatMap { it.items }.map { it.id }.toHashSet()
                            val missing = extra.filterNot { it.id in existingIds }
                            if (missing.isEmpty()) return@withContext
                            val idx = current.sections.indexOfFirst {
                                it.items.firstOrNull() is com.music.innertube.models.AlbumItem
                            }
                            val sections = current.sections.toMutableList()
                            if (idx >= 0) {
                                val sec = sections[idx]
                                sections[idx] = sec.copy(items = (sec.items + missing).distinctBy { it.id })
                            } else {
                                sections.add(
                                    com.music.innertube.pages.ArtistSection(
                                        title = context.getString(iad1tya.echo.music.R.string.albums),
                                        items = missing,
                                        moreEndpoint = null,
                                    )
                                )
                            }
                            artistPage = current.copy(sections = sections)
                        }
                    }
                }.onFailure {
                    reportException(it)
                }
        }
    }
}
