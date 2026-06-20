

package iad1tya.echo.music.viewmodels

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.music.innertube.YouTube
import com.music.innertube.models.AlbumItem
import com.music.innertube.models.BrowseEndpoint
import com.music.innertube.models.filterExplicit
import com.music.innertube.models.filterVideoSongs
import iad1tya.echo.music.utils.iTunesDiscography
import iad1tya.echo.music.utils.systemRegionCode
import iad1tya.echo.music.constants.HideExplicitKey
import iad1tya.echo.music.constants.HideVideoSongsKey
import iad1tya.echo.music.db.MusicDatabase
import iad1tya.echo.music.models.ItemsPage
import kotlinx.coroutines.flow.first
import iad1tya.echo.music.utils.dataStore
import iad1tya.echo.music.utils.get
import iad1tya.echo.music.utils.reportException
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ArtistItemsViewModel
@Inject
constructor(
    @ApplicationContext val context: Context,
    private val database: MusicDatabase,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val browseId = savedStateHandle.get<String>("browseId")!!
    private val params = savedStateHandle.get<String>("params")
    private val artistId = savedStateHandle.get<String>("artistId")

    val title = MutableStateFlow("")
    val itemsPage = MutableStateFlow<ItemsPage?>(null)

    init {
        viewModelScope.launch {
            YouTube
                .artistItems(
                    BrowseEndpoint(
                        browseId = browseId,
                        params = params,
                    ),
                ).onSuccess { artistItemsPage ->
                    val hideExplicit = context.dataStore.get(HideExplicitKey, false)
                    val hideVideoSongs = context.dataStore.get(HideVideoSongsKey, false)
                    title.value = artistItemsPage.title
                    itemsPage.value =
                        ItemsPage(
                            items = artistItemsPage.items
                                .distinctBy { it.id }
                                .filterExplicit(hideExplicit)
                                .filterVideoSongs(hideVideoSongs),
                            continuation = artistItemsPage.continuation,
                        )
                    // If this is the artist's albums list, complete the discography: YouTube omits some
                    // albums (e.g. "Privé"). Show the full YouTube list first (fast), then append the
                    // missing ones found by cross-checking the artist's real discography (iTunes) and the
                    // artist-name album search. Base list is shown unfiltered so we never lose albums.
                    runCatching { enrichAlbums(hideExplicit) }
                }.onFailure {
                    reportException(it)
                }
        }
    }

    private suspend fun resolveArtistName(): String? {
        val id = artistId ?: return null
        runCatching { database.artist(id).first()?.artist?.name }.getOrNull()
            ?.takeIf { it.isNotBlank() }?.let { return it }
        return runCatching { YouTube.artist(id).getOrNull()?.artist?.title }.getOrNull()?.takeIf { it.isNotBlank() }
    }

    private suspend fun enrichAlbums(hideExplicit: Boolean) {
        val baseAlbums = itemsPage.value?.items?.filterIsInstance<AlbumItem>().orEmpty()
        if (baseAlbums.isEmpty()) return
        // moreEndpoint album items have artists = null, so the artist name must come from the DB/page.
        val artistName = resolveArtistName() ?: return
        val norm = iTunesDiscography::normalizeTitle

        fun credited(a: AlbumItem): Boolean {
            val arts = a.artists
            if (arts.isNullOrEmpty()) return true // the search was scoped to this artist; keep it
            return arts.any {
                it.id == artistId ||
                    it.name.contains(artistName, ignoreCase = true) ||
                    artistName.contains(it.name, ignoreCase = true)
            }
        }

        fun titleMatch(ytTitle: String, target: String): Boolean {
            val yt = norm(ytTitle)
            return yt == target || (target.length >= 4 && (yt.contains(target) || target.contains(yt)))
        }

        val have = baseAlbums.map { norm(it.title) }.toMutableSet()
        val additions = mutableListOf<com.music.innertube.models.YTItem>()

        // The REAL discography (iTunes Apple Music: albums, EPs AND singles) is the AUTHORITY. We do NOT
        // add albums off a broad YouTube search anymore — that pulled in tributes/compilations/karaoke
        // that don't belong. For every iTunes release we don't already have, look it up on YouTube: first
        // as a proper album, otherwise as a community/user playlist. Only real matches are added.
        val itunes = iTunesDiscography.fetchAlbumTitles(artistName, systemRegionCode())
        val missing = itunes.filter { norm(it) !in have }.distinctBy { norm(it) }.take(30)
        for (mt in missing) {
            val target = norm(mt)
            if (target.isBlank() || target in have) continue
            // a) The album as a proper YouTube Music album.
            val albumMatch = YouTube.search("$artistName $mt", YouTube.SearchFilter.FILTER_ALBUM).getOrNull()?.items
                ?.filterIsInstance<AlbumItem>()
                ?.firstOrNull { credited(it) && titleMatch(it.title, target) }
            if (albumMatch != null) { additions.add(albumMatch); have.add(target); continue }
            // b) Fallback: a third party may have uploaded the album as a community playlist (e.g.
            //    "Lenguaje de Amor" by Alex Campos). Add that so the discography is still complete.
            val playlistMatch = YouTube.search("$artistName $mt", YouTube.SearchFilter.FILTER_COMMUNITY_PLAYLIST)
                .getOrNull()?.items
                ?.filterIsInstance<com.music.innertube.models.PlaylistItem>()
                ?.firstOrNull { pl ->
                    val t = norm(pl.title)
                    (t == target || t.contains(target)) &&
                        (pl.title.contains(artistName, ignoreCase = true) ||
                            pl.author?.name?.contains(artistName, ignoreCase = true) == true)
                }
            if (playlistMatch != null) { additions.add(playlistMatch); have.add(target) }
        }

        if (additions.isEmpty()) return
        itemsPage.update { old ->
            val merged = ((old?.items ?: baseAlbums) + additions)
                .distinctBy { it.id }
                .let { if (hideExplicit) it.filterExplicit(true) else it }
            ItemsPage(items = merged, continuation = old?.continuation)
        }
    }

    fun loadMore() {
        viewModelScope.launch {
            val oldItemsPage = itemsPage.value ?: return@launch
            val continuation = oldItemsPage.continuation ?: return@launch
            YouTube
                .artistItemsContinuation(continuation)
                .onSuccess { artistItemsContinuationPage ->
                    val hideExplicit = context.dataStore.get(HideExplicitKey, false)
                    val hideVideoSongs = context.dataStore.get(HideVideoSongsKey, false)
                    itemsPage.update {
                        ItemsPage(
                            items =
                            (oldItemsPage.items + artistItemsContinuationPage.items)
                                .distinctBy { it.id }
                                .filterExplicit(hideExplicit)
                                .filterVideoSongs(hideVideoSongs),
                            continuation = artistItemsContinuationPage.continuation,
                        )
                    }
                }.onFailure {
                    reportException(it)
                }
        }
    }
}
