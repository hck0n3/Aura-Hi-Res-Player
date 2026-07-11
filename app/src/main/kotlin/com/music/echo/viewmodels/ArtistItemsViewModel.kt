

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
import kotlinx.coroutines.withTimeoutOrNull
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
    // Terminal-failure flag: when YouTube.artistItems fails (e.g. the Videos "more" browse), flip this so
    // the screen STOPS the shimmer and offers Retry instead of spinning forever (itemsPage stays null).
    val hasFailed = MutableStateFlow(false)

    companion object {
        // Per-artist cache of the completed (iTunes-driven) discography for this app session, so
        // re-opening the same artist's albums shows the full list instantly instead of re-fetching.
        private val completedCache =
            java.util.concurrent.ConcurrentHashMap<String, List<com.music.innertube.models.YTItem>>()
    }

    init {
        load()
    }

    fun load() {
        hasFailed.value = false
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
                    // Is THIS see-all the Singles/EP section (vs Albums)? Drives which iTunes releases the
                    // completion targets, so Singles/EPs get completed too (the owner wants ALL of them).
                    val isSinglesSection =
                        Regex("(?i)single|sencillo|\\bep\\b").containsMatchIn(artistItemsPage.title ?: "")
                    val baseItems = artistItemsPage.items
                        .distinctBy { it.id }
                        .filterExplicit(hideExplicit)
                        .filterVideoSongs(hideVideoSongs)
                    // For the albums list, build the COMPLETE discography up front (iTunes-driven, in
                    // parallel) and publish it in ONE shot, so the whole discography shows at once instead
                    // of extra albums popping in seconds later. Other lists publish as-is.
                    // Show the YouTube list IMMEDIATELY (fast), then complete it against iTunes/Apple Music
                    // in the background and publish the full discography in ONE update (not in batches).
                    itemsPage.value = ItemsPage(items = baseItems, continuation = artistItemsPage.continuation)
                    if (baseItems.any { it is AlbumItem }) {
                        // Key by artist AND browse: the same artist's Albums and Singles/EP see-all screens
                        // share artistId but differ by browseId. Keying by artistId alone made opening one
                        // section serve the OTHER's cached list (albums showing under Singles).
                        val cacheKey = "${artistId ?: ""}:$browseId"
                        val cached = completedCache[cacheKey]
                        if (cached != null) {
                            // Re-opening the same section: show the full discography instantly. Merge with
                            // whatever is already shown and KEEP the live continuation — never rewind paging.
                            val current = itemsPage.value
                            itemsPage.value = ItemsPage(
                                items = (cached + (current?.items ?: emptyList())).distinctBy { it.id },
                                continuation = current?.continuation ?: artistItemsPage.continuation,
                            )
                        } else {
                            viewModelScope.launch {
                                val complete =
                                    runCatching { buildCompleteDiscography(baseItems, hideExplicit, isSinglesSection) }
                                        .getOrDefault(baseItems)
                                if (complete.size > baseItems.size) {
                                    completedCache[cacheKey] = complete
                                    // Merge with whatever the user has scrolled in by now and keep the LIVE
                                    // continuation, so the completion never overwrites loaded pages or
                                    // rewinds paging back to page 1.
                                    val current = itemsPage.value
                                    itemsPage.value = ItemsPage(
                                        items = (complete + (current?.items ?: emptyList())).distinctBy { it.id },
                                        continuation = current?.continuation ?: artistItemsPage.continuation,
                                    )
                                }
                            }
                        }
                    }
                }.onFailure {
                    reportException(it)
                    hasFailed.value = true
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
        isSinglesSection: Boolean,
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
            // Short titles (now that singles feed Albums completion) need an EXACT match — a fuzzy substring
            // would let a single like "Amor" match the album "Lenguaje de Amor". Only titles ≥6 chars use
            // the fuzzy contains() so genuinely long titles still tolerate minor punctuation/edition diffs.
            return yt == target || (target.length >= 6 && (yt.contains(target) || target.contains(yt)))
        }

        val have = baseAlbums.map { norm(it.title) }.toMutableSet()

        // iTunes / Apple Music is the authority on which releases exist. Query several stores in
        // parallel and merge — the US store, the device's local store, and the main Spanish-speaking
        // markets — so a Latin/regional artist's catalog (e.g. Alex Campos) isn't cut short by the US
        // store alone. Each store is one cheap request; only releases that ALSO resolve on YouTube are
        // actually added, so extra stores can only make the list more complete, never add junk.
        val stores = listOf("us", systemRegionCode(), "mx", "es", "co", "ar", "cl").distinct()
        val itunes = stores
            .map { store -> async { iTunesDiscography.fetchAlbumTitles(artistName, store) } }
            .awaitAll()
            .flatten()
            .distinct()
        // Don't mix EPs/Singles into the Albums list (iTunes/Apple keep them separate). iTunes marks them
        // as "Title - EP" / "Title - Single", so only complete FULL albums here.
        val epOrSingle = Regex("(?i)[-–—]\\s*(ep|single)\\b|\\((?:ep|single)\\)")
        val missing = itunes
            .filter { norm(it).isNotBlank() && norm(it) !in have }
            // Complete the WHOLE iTunes/Apple catalog for the main (Albums) discography — INCLUDING EPs and
            // singles. iTunes returns most of an artist's releases as "… - Single"/"… - EP" (especially
            // Latin/regional catalogs) and YouTube Music usually omits them; the old EP/Single exclusion
            // (regression 866b4c8) left singles-heavy catalogs with nothing to add → nothing published
            // ("ya estaba y no lo hace"). A dedicated Singles/EP see-all, when it exists, still narrows to
            // EP/Single only. This restores the iTunes-authoritative completion the owner remembers.
            .let { list -> if (isSinglesSection) list.filter { epOrSingle.containsMatchIn(it) } else list }
            .distinctBy { norm(it) }
            .take(80)

        // 6 concurrent searches (was 10): 10 at once can itself trip YouTube throttling, which then
        // times out individual lookups and silently drops real albums (e.g. "Lenguaje de Amor").
        val semaphore = Semaphore(6)
        val found = missing.map { mt ->
            async {
                semaphore.withPermit {
                  // Bound each lookup so one slow YouTube search can't stall the whole completion.
                  // 12s (was 8s) gives a throttled search enough time to return before being dropped.
                  withTimeoutOrNull(12000L) {
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
