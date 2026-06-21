

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
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
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
                    val baseItems = artistItemsPage.items
                        .distinctBy { it.id }
                        .filterExplicit(hideExplicit)
                        .filterVideoSongs(hideVideoSongs)
                    // For the albums list, build the COMPLETE discography up front (iTunes-driven, in
                    // parallel) and publish it in ONE shot, so the whole discography shows at once instead
                    // of extra albums popping in seconds later. Other lists publish as-is.
                    val items =
                        if (baseItems.any { it is AlbumItem }) {
                            runCatching { buildCompleteDiscography(baseItems, hideExplicit) }
                                .getOrDefault(baseItems)
                        } else {
                            baseItems
                        }
                    itemsPage.value = ItemsPage(items = items, continuation = artistItemsPage.continuation)
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

    /**
     * Complete the artist's discography using iTunes / Apple Music as the authoritative source of which
     * releases exist (albums, EPs AND singles), then resolve each missing one on YouTube / YouTube Music
     * IN PARALLEL — first as a proper album, otherwise as a community/user-uploaded playlist. Only items
     * that match a real release are added (no tributes/compilations). Returns the full list to publish
     * at once.
     */
    private suspend fun buildCompleteDiscography(
        baseItems: List<com.music.innertube.models.YTItem>,
        hideExplicit: Boolean,
    ): List<com.music.innertube.models.YTItem> = coroutineScope {
        val artistName = resolveArtistName() ?: return@coroutineScope baseItems
        val norm = iTunesDiscography::normalizeTitle
        val baseAlbums = baseItems.filterIsInstance<AlbumItem>()

        fun credited(a: AlbumItem): Boolean {
            val arts = a.artists
            if (arts.isNullOrEmpty()) return true
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

        // iTunes / Apple Music is the authority. Query the US store (most complete for international
        // artists) AND the local store, merged, so nothing is missed.
        val itunesUs = async { iTunesDiscography.fetchAlbumTitles(artistName, "us") }
        val itunesLocal = async { iTunesDiscography.fetchAlbumTitles(artistName, systemRegionCode()) }
        val itunes = (itunesUs.await() + itunesLocal.await()).distinct()
        val missing = itunes
            .filter { norm(it).isNotBlank() && norm(it) !in have }
            .distinctBy { norm(it) }
            .take(50)

        val semaphore = Semaphore(10)
        val found = missing.map { mt ->
            async {
                semaphore.withPermit {
                    val target = norm(mt)
                    val album = YouTube.search("$artistName $mt", YouTube.SearchFilter.FILTER_ALBUM)
                        .getOrNull()?.items?.filterIsInstance<AlbumItem>()
                        ?.firstOrNull { credited(it) && titleMatch(it.title, target) }
                    if (album != null) {
                        target to (album as com.music.innertube.models.YTItem)
                    } else {
                        val pl = YouTube.search("$artistName $mt", YouTube.SearchFilter.FILTER_COMMUNITY_PLAYLIST)
                            .getOrNull()?.items?.filterIsInstance<com.music.innertube.models.PlaylistItem>()
                            ?.firstOrNull { p ->
                                val t = norm(p.title)
                                (t == target || (target.length >= 4 && (t.contains(target) || target.contains(t)))) &&
                                    (p.title.contains(artistName, ignoreCase = true) ||
                                        p.author?.name?.contains(artistName, ignoreCase = true) == true)
                            }

                        // Quality control: only add a community playlist if its tracks are NOT truncated
                        // previews (the Lauren Daigle problem — list looks complete but songs play for
                        // seconds). Reject short/preview uploads so the user gets full, playable audio.
                        if (pl != null && isSourceComplete(pl.id)) {
                            target to (pl as com.music.innertube.models.YTItem)
                        } else {
                            null
                        }
                    }
                }
            }
        }.awaitAll().filterNotNull()

        val result = baseItems.toMutableList()
        found.forEach { (t, item) ->
            if (t !in have) { have.add(t); result.add(item) }
        }
        if (hideExplicit) result.filterExplicit(true) else result
    }

    /**
     * Quality control for a community-uploaded source: reject it if its tracks look truncated/previews
     * (median track under ~90 s) or it has too few tracks. Real album tracks average a few minutes, so a
     * source full of 20-60 s clips is a bad/incomplete upload and is skipped.
     */
    private suspend fun isSourceComplete(playlistId: String): Boolean {
        val songs = YouTube.playlist(playlistId).getOrNull()?.songs ?: return false
        if (songs.size < 2) return false
        val durations = songs.mapNotNull { it.duration }.filter { it > 0 }
        if (durations.isEmpty()) return true // no duration metadata -> don't reject on that basis
        val median = durations.sorted()[durations.size / 2]
        return median >= 90
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
