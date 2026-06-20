

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
import iad1tya.echo.music.models.ItemsPage
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

    private suspend fun enrichAlbums(hideExplicit: Boolean) {
        val baseAlbums = itemsPage.value?.items?.filterIsInstance<AlbumItem>().orEmpty()
        if (baseAlbums.isEmpty()) return
        val artistName = baseAlbums.flatMap { it.artists.orEmpty() }
            .firstOrNull { it.id == artistId && it.name.isNotBlank() }?.name
            ?: baseAlbums.flatMap { it.artists.orEmpty() }
                .mapNotNull { it.name.takeIf { n -> n.isNotBlank() } }
                .groupingBy { it }.eachCount().maxByOrNull { it.value }?.key
            ?: return
        val norm = iTunesDiscography::normalizeTitle

        fun credited(a: AlbumItem): Boolean =
            a.artists?.any { it.id == artistId || it.name.contains(artistName, ignoreCase = true) } == true

        val have = baseAlbums.map { norm(it.title) }.toMutableSet()
        val additions = mutableListOf<AlbumItem>()

        // 1) Albums YouTube returns when searching the artist name.
        YouTube.search(artistName, YouTube.SearchFilter.FILTER_ALBUM).getOrNull()?.items
            ?.filterIsInstance<AlbumItem>()?.filter(::credited)?.forEach { alb ->
                val t = norm(alb.title)
                if (t.isNotBlank() && t !in have) { additions.add(alb); have.add(t) }
            }

        // 2) Cross-check the real discography (iTunes) and look up still-missing titles on YouTube.
        val itunes = iTunesDiscography.fetchAlbumTitles(artistName, systemRegionCode())
        val missing = itunes.filter { norm(it) !in have }.distinctBy { norm(it) }.take(15)
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
