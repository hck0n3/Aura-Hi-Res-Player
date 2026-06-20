

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

        val have = baseAlbums.map { norm(it.title) }.toMutableSet()
        val additions = mutableListOf<AlbumItem>()

        // 1) Albums from the artist-name album search — PAGINATE so prolific discographies are fully
        //    covered. Using only the first page is why some artists weren't completing (the missing
        //    albums were on later pages of the same search the user scrolls through manually).
        var result = YouTube.search(artistName, YouTube.SearchFilter.FILTER_ALBUM).getOrNull()
        var page = 0
        while (result != null && page < 6) {
            result.items.filterIsInstance<AlbumItem>().filter(::credited).forEach { alb ->
                val t = norm(alb.title)
                if (t.isNotBlank() && t !in have) { additions.add(alb); have.add(t) }
            }
            val cont = result.continuation ?: break
            result = YouTube.searchContinuation(cont).getOrNull()
            page++
        }

        // 2) Cross-check the real discography (iTunes) and look up still-missing titles on YouTube.
        val itunes = iTunesDiscography.fetchAlbumTitles(artistName, systemRegionCode())
        val missing = itunes.filter { norm(it) !in have }.distinctBy { norm(it) }.take(20)
        for (mt in missing) {
            val target = norm(mt)
            if (target.isBlank() || target in have) continue
            val match = YouTube.search("$artistName $mt", YouTube.SearchFilter.FILTER_ALBUM).getOrNull()?.items
                ?.filterIsInstance<AlbumItem>()
                ?.firstOrNull {
                    credited(it) && run {
                        val yt = norm(it.title)
                        yt == target || (target.length >= 4 && (yt.contains(target) || target.contains(yt)))
                    }
                }
            if (match != null) { additions.add(match); have.add(target) }
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
