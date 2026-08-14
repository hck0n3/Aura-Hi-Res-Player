

package iad1tya.echo.music.viewmodels

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.music.innertube.YouTube
import com.music.innertube.models.SongItem
import com.music.innertube.models.filterExplicit
import com.music.innertube.models.filterVideoSongs
import com.music.innertube.models.filterYoutubeShorts
import com.music.innertube.pages.ArtistPage
import com.music.innertube.pages.ArtistSection
import iad1tya.echo.music.constants.HideExplicitKey
import iad1tya.echo.music.constants.HideVideoSongsKey
import iad1tya.echo.music.constants.HideYoutubeShortsKey
import iad1tya.echo.music.db.MusicDatabase
import iad1tya.echo.music.extensions.filterExplicit
import iad1tya.echo.music.extensions.filterExplicitAlbums
import iad1tya.echo.music.utils.ArtistPageCache
import iad1tya.echo.music.utils.dataStore
import iad1tya.echo.music.utils.reportException
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
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

    // Terminal-failure flag: true once every retry of the artist fetch failed AND we have no page to
    // show (not even a cached one). The screen uses it to stop the endless shimmer and offer Retry,
    // mirroring ArtistItemsViewModel.hasFailed.
    private val _hasFailed = MutableStateFlow(false)
    val hasFailed: StateFlow<Boolean> = _hasFailed

    // YouTube's artist shelf only ships ~5 "Songs" / populares. The moreEndpoint is the full
    // popularity-sorted list the owner swipes 4-at-a-time. Kept off [artistPage] so the classic
    // vertical artist screen does not dump dozens of rows.
    private val _expandedPopularSongs = MutableStateFlow<List<SongItem>>(emptyList())
    val expandedPopularSongs: StateFlow<List<SongItem>> = _expandedPopularSongs

    val libraryArtist = database.artist(artistId)
        .stateIn(viewModelScope, SharingStarted.Lazily, null)

    // Display name used to bridge YouTube channel ids and local LA######## artist rows that share a name.
    // Updated whenever the page (or a local artist row) resolves — empty means id-only matching.
    private val libraryMatchName = MutableStateFlow("")

    // FULL local song list for this artist. [librarySongs] below is deliberately a 3-item PREVIEW for the
    // shelf, so any action that plays "the artist's songs" (Shuffle / Play all) must read this instead —
    // shuffling the preview could only ever pick from 3 songs.
    // Includes liked-only songs and same-name artist ids (YTM sync often sets liked without inLibrary).
    val allLibrarySongs = context.dataStore.data
        .map { (it[HideExplicitKey] ?: false) to (it[HideVideoSongsKey] ?: false) }
        .distinctUntilChanged()
        .combine(libraryMatchName) { prefs, name -> prefs to name }
        .flatMapLatest { (prefs, name) ->
            val (hideExplicit, hideVideoSongs) = prefs
            database.artistLibraryOrLikedSongs(artistId, name)
                .map { songs ->
                    songs.filterExplicit(hideExplicit)
                        .filterVideoSongsLocal(hideVideoSongs)
                        .sortedByDescending { it.song.totalPlayTime }
                }
        }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val librarySongs = allLibrarySongs
        .map { it.take(3) }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val libraryAlbums = context.dataStore.data
        .map { it[HideExplicitKey] ?: false }
        .distinctUntilChanged()
        .flatMapLatest { hideExplicit ->
            database.artistAlbumsPreview(artistId).map { it.filterExplicitAlbums(hideExplicit) }
        }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    companion object {
        // moreEndpoint + a few continuations. Enough for every page of 4 the owner can swipe,
        // without walking a 400-track catalogue on every artist open (heat/battery gate).
        private const val POPULAR_SONGS_CAP = 80
        private const val POPULAR_SONGS_MAX_CONTINUATIONS = 4

        // In-memory, per-session cache of the fetched artist page keyed by artistId, so re-opening an
        // artist renders instantly (no spinner) while a fresh copy is still fetched in the background.
        private val pageCache =
            java.util.concurrent.ConcurrentHashMap<String, ArtistPage>()

        // Locally-created artist row id ("LA########") -> the YouTube channel id resolved by NAME.
        // An empty value marks a resolved-miss so the search isn't repeated all session. In memory ONLY:
        // writing this into ArtistEntity.channelId would feed YouTube.subscribeChannel (toggleLike +
        // the subscriptions sync) and could subscribe the user's account to a same-named wrong artist.
        private val resolvedChannelIds =
            java.util.concurrent.ConcurrentHashMap<String, String>()

        // Session cache of the expanded populares list (moreEndpoint), keyed by the navigated artistId.
        private val popularSongsCache =
            java.util.concurrent.ConcurrentHashMap<String, List<SongItem>>()
    }

    init {
        viewModelScope.launch {
            libraryArtist.collect { row ->
                val localName = row?.artist?.name?.trim().orEmpty()
                if (localName.isNotBlank()) libraryMatchName.value = localName
            }
        }
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
                .collect { (hideExplicit, hideVideoSongs, hideYoutubeShorts) ->
                    fetchArtistsFromYTM(hideExplicit, hideVideoSongs, hideYoutubeShorts)
                }
        }
    }

    private fun noteLibraryArtistName(name: String?) {
        val trimmed = name?.trim().orEmpty()
        if (trimmed.isNotBlank()) libraryMatchName.value = trimmed
    }

    private var fetchJob: Job? = null

    // Public entry point used by ArtistScreen. Reads the current hide-keys off the
    // main thread via the DataStore Flow (which reads on its own IO dispatcher)
    // instead of three blocking runBlocking reads, then delegates to the worker.
    fun fetchArtistsFromYTM() {
        viewModelScope.launch {
            val prefs = context.dataStore.data.first()
            fetchArtistsFromYTM(
                prefs[HideExplicitKey] ?: false,
                prefs[HideVideoSongsKey] ?: false,
                prefs[HideYoutubeShortsKey] ?: false,
            )
        }
    }

    // Cancels any in-flight fetch before starting a new one, so a hide-key change
    // (or a manual re-fetch) supersedes the previous fetch instead of racing an
    // uncancelled concurrent one. Hide-key values are passed in (already in hand
    // from the init collect / the public overload), avoiding blocking DataStore reads.
    fun fetchArtistsFromYTM(
        hideExplicit: Boolean,
        hideVideoSongs: Boolean,
        hideYoutubeShorts: Boolean,
    ) {
        fetchJob?.cancel()
        fetchJob = viewModelScope.launch {
            _hasFailed.value = false
            
            popularSongsCache[artistId]?.let { cachedPopular ->
                if (cachedPopular.isNotEmpty()) _expandedPopularSongs.value = cachedPopular
            }
            if (artistPage == null) {
                pageCache[artistId]?.let {
                    artistPage = it
                    noteLibraryArtistName(it.artist.title)
                }
                    ?: ArtistPageCache.load(context, artistId)?.let { persisted ->
                        if (artistPage == null) {
                            val filtered = persisted.copy(
                                sections = persisted.sections
                                    .map { s -> s.copy(items = s.items.filterExplicit(hideExplicit).filterVideoSongs(hideVideoSongs).filterYoutubeShorts(hideYoutubeShorts)) }
                                    .filter { s -> s.items.isNotEmpty() }
                            )
                            pageCache[artistId] = filtered
                            artistPage = filtered
                            noteLibraryArtistName(filtered.artist.title)
                        }
                    }
            } else {
                noteLibraryArtistName(artistPage?.artist?.title)
            }

            val localRow = database.artist(artistId).first()?.artist
            var effectiveArtistId = artistId
            if (localRow != null && !localRow.isYouTubeArtist && !localRow.isLocal) {
                val cached = resolvedChannelIds[artistId]
                val resolved: String?
                if (cached != null) {
                    resolved = cached
                } else {
                    val searchResult = runCatching {
                        YouTube.search(localRow.name, YouTube.SearchFilter.FILTER_ARTIST).getOrNull()
                    }.getOrNull()
                    val match = searchResult?.items
                        ?.filterIsInstance<com.music.innertube.models.ArtistItem>()
                        ?.firstOrNull { candidate ->
                            candidate.id.startsWith("UC") &&
                                candidate.title.trim().equals(localRow.name.trim(), ignoreCase = true)
                        }
                        ?.id
                    if (match == null && searchResult != null) resolvedChannelIds[artistId] = ""
                    resolved = match
                }
                if (resolved.isNullOrEmpty()) {
                    return@launch
                }
                effectiveArtistId = resolved
                resolvedChannelIds[artistId] = resolved
            }
            
            val cachedAppearsOn: com.music.innertube.pages.ArtistSection? =
                artistPage?.sections?.find { it.title.equals("Aparece en", ignoreCase = true) }
                    ?: pageCache[artistId]?.sections?.find { it.title.equals("Aparece en", ignoreCase = true) }

            var attempt = 0
            var loaded = false
            while (!loaded && attempt < 3 && isActive) {
            YouTube.artist(effectiveArtistId)
                .onSuccess { page ->
                    val filteredSections = page.sections
                        .map { section ->
                            section.copy(items = section.items.filterExplicit(hideExplicit).filterVideoSongs(hideVideoSongs).filterYoutubeShorts(hideYoutubeShorts))
                        }
                        .filter { section -> section.items.isNotEmpty() }

                    val filteredPage = page.copy(sections = filteredSections)
                    artistPage = filteredPage
                    noteLibraryArtistName(filteredPage.artist.title)
                    pageCache[artistId] = filteredPage
                    loaded = true
                    launch { ArtistPageCache.save(context, artistId, filteredPage) }
                    launch(Dispatchers.IO) {
                        expandPopularSongsSection(
                            page = filteredPage,
                            hideExplicit = hideExplicit,
                            hideVideoSongs = hideVideoSongs,
                            hideYoutubeShorts = hideYoutubeShorts,
                        )
                    }

                    val topSongsSection = page.sections.find { it.items.firstOrNull() is com.music.innertube.models.SongItem }
                    launch(Dispatchers.IO) {
                        for (item in topSongsSection?.items.orEmpty()) {
                            if (item is com.music.innertube.models.SongItem) {
                                val canvas = ArtistVideoCanvasProvider.getBySongArtist(
                                    song = item.title,
                                    artist = page.artist?.title ?: ""
                                )
                                if (canvas?.preferredAnimationUrl != null) {
                                    _artistVideoUrl.value = canvas.preferredAnimationUrl
                                    _artistVideoSong.value = item
                                    break
                                }
                            }
                        }
                    }

                    launch(Dispatchers.IO) {
                        val artistName = page.artist?.title ?: return@launch
                        val items: List<com.music.innertube.models.YTItem> = if (cachedAppearsOn != null) {
                            cachedAppearsOn.items.filter { !hideExplicit || !it.explicit }
                        } else {
                            val nativeSeed = page.sections
                                .filter { isAppearsOnSectionTitle(it.title) }
                                .flatMap { it.items }
                            val norm = iad1tya.echo.music.utils.iTunesDiscography::normalizeTitle
                            val guest = iad1tya.echo.music.utils.iTunesDiscography
                                .fetchAppearsOn(artistName, "us")
                                .take(12)
                            val sem = Semaphore(2)
                            val fromItunes = if (guest.isEmpty()) {
                                emptyList()
                            } else {
                                withTimeoutOrNull(5000L) {
                                    coroutineScope {
                                        guest.map { (title, primary) ->
                                            async {
                                                sem.withPermit {
                                                    val target = norm(title)
                                                    fun matches(t: String) =
                                                        t == target ||
                                                            (target.length >= 4 &&
                                                                (t.contains(target) || target.contains(t)))
                                                    val album = YouTube.search(
                                                        "$primary $title",
                                                        YouTube.SearchFilter.FILTER_ALBUM,
                                                    ).getOrNull()?.items
                                                        ?.filterIsInstance<com.music.innertube.models.AlbumItem>()
                                                        ?.firstOrNull { matches(norm(it.title)) }
                                                    if (album != null) {
                                                        return@withPermit album as com.music.innertube.models.YTItem
                                                    }
                                                    val song = YouTube.search(
                                                        "$primary $title",
                                                        YouTube.SearchFilter.FILTER_SONG,
                                                    ).getOrNull()?.items
                                                        ?.filterIsInstance<com.music.innertube.models.SongItem>()
                                                        ?.firstOrNull { s ->
                                                            matches(norm(s.title)) &&
                                                                s.artists.any {
                                                                    it.name.contains(artistName, ignoreCase = true)
                                                                }
                                                        }
                                                    song as? com.music.innertube.models.YTItem
                                                }
                                            }
                                        }.awaitAll().filterNotNull()
                                    }
                                }.orEmpty()
                            }
                            val fromFeatSearch = withTimeoutOrNull(4000L) {
                                coroutineScope {
                                    listOf(
                                        "feat. $artistName",
                                        "ft. $artistName",
                                        "featuring $artistName",
                                    ).map { q ->
                                        async {
                                            sem.withPermit {
                                                YouTube.search(q, YouTube.SearchFilter.FILTER_SONG)
                                                    .getOrNull()?.items
                                                    ?.filterIsInstance<com.music.innertube.models.SongItem>()
                                                    ?.filter { s ->
                                                        val credited = s.artists.any {
                                                            it.name.contains(artistName, ignoreCase = true)
                                                        }
                                                        val inTitle = s.title.contains(artistName, ignoreCase = true) &&
                                                            (s.title.contains("feat", ignoreCase = true) ||
                                                                s.title.contains("ft.", ignoreCase = true) ||
                                                                s.title.contains("with ", ignoreCase = true))
                                                        (credited || inTitle) &&
                                                            s.artists.firstOrNull()
                                                                ?.name
                                                                ?.equals(artistName, ignoreCase = true) != true
                                                    }
                                                    ?.take(15)
                                                    .orEmpty()
                                            }
                                        }
                                    }.awaitAll().flatten()
                                }
                            }.orEmpty()
                            (nativeSeed + fromItunes + fromFeatSearch)
                                .distinctBy { it.id }
                                .filter { !hideExplicit || !it.explicit }
                                .take(60)
                        }
                        if (items.isEmpty()) return@launch
                        withContext(Dispatchers.Main) {
                            val current = artistPage ?: return@withContext
                            if (current.sections.any { it.title.equals("Aparece en", ignoreCase = true) }) {
                                return@withContext
                            }
                            // Drop native Appears/Featuring shelves we already merged into "Aparece en"
                            // so the page does not show the same collaborations twice under two titles.
                            val withoutNativeAppears = current.sections.filterNot {
                                isAppearsOnSectionTitle(it.title)
                            }
                            val updated = current.copy(
                                sections = withoutNativeAppears + com.music.innertube.pages.ArtistSection(
                                    title = "Aparece en",
                                    items = items,
                                    moreEndpoint = null,
                                ),
                            )
                            artistPage = updated
                            pageCache[artistId] = updated
                            launch { ArtistPageCache.save(context, artistId, updated) }
                        }
                    }

                    // "Videos oficiales": the artist's official music videos from YouTube, playable via the
                    // integrated video mode. (iTunes only exposes ~30s previews + Apple links, not playable
                    // streams, so YouTube is the playable source for this section.)
                    // Prefer native YTM Videos/Videoclips shelves that already expose moreEndpoint —
                    // those paginate via AuraArtistItemsScreen. Only synthesize when missing.
                    launch(Dispatchers.IO) {
                        val artistName = page.artist?.title ?: return@launch
                        if (page.sections.any { isNativeOrOfficialVideosSection(it) }) return@launch
                        val searchResult = YouTube.search(artistName, YouTube.SearchFilter.FILTER_VIDEO)
                            .getOrNull() ?: return@launch
                        val videos = searchResult.items
                            .filterIsInstance<com.music.innertube.models.SongItem>()
                            .filter { v ->
                                v.isVideoSong && v.artists.any { it.name.contains(artistName, ignoreCase = true) }
                            }
                            .filter { !hideExplicit || !it.explicit }
                            .distinctBy { it.id }
                            .take(20)
                        if (videos.isEmpty()) return@launch
                        withContext(Dispatchers.Main) {
                            val current = artistPage ?: return@withContext
                            if (current.sections.any { isNativeOrOfficialVideosSection(it) }) return@withContext
                            // Stash search continuation so "Ver todos" can paginate (shelf stays ~20).
                            iad1tya.echo.music.ui.screens.artist.ArtistSectionBuffer.videoSearchContinuation =
                                searchResult.continuation
                            iad1tya.echo.music.ui.screens.artist.ArtistSectionBuffer.videoSearchQuery =
                                artistName
                            iad1tya.echo.music.ui.screens.artist.ArtistSectionBuffer.videoSearchArtistFilter =
                                artistName
                            artistPage = current.copy(
                                sections = current.sections + com.music.innertube.pages.ArtistSection(
                                    title = "Videos oficiales",
                                    items = videos,
                                    moreEndpoint = null,
                                ),
                            )
                        }
                    }
                }.onFailure {
                    if (attempt >= 2) reportException(it)
                }
            if (!loaded) {
                attempt++
                if (attempt < 3) kotlinx.coroutines.delay(700L * attempt)
            }
            }
            // Every retry failed. Only a terminal failure if there is nothing to show at all — with a
            // cached/persisted page the screen renders normally and must NOT flip to the retry state.
            //
            // `isActive` is REQUIRED, not defensive: YouTube.artist is runCatching{}-wrapped, so a job
            // cancelled by a newer fetchArtistsFromYTM() returns failure here instead of throwing, and on
            // attempt == 2 there is no suspension point between the last call and this write. Without the
            // guard the dead job could set hasFailed = true AFTER its replacement cleared it, showing the
            // error + Retry UI while a fresh fetch was still running.
            if (!loaded && artistPage == null && isActive) {
                _hasFailed.value = true
            }
        }
    }

    /**
     * True when the page already has a usable Videos shelf — either native YTM Videos/Videoclips
     * with a browse moreEndpoint (paginates via ArtistItems), or our synthetic Videos oficiales.
     */
    private fun isNativeOrOfficialVideosSection(section: com.music.innertube.pages.ArtistSection): Boolean {
        val title = section.title.trim()
        if (title.equals("Videos oficiales", ignoreCase = true)) return true
        val looksLikeVideos =
            title.contains("video", ignoreCase = true) ||
                title.contains("vídeo", ignoreCase = true) ||
                title.contains("videoclips", ignoreCase = true)
        return looksLikeVideos && section.moreEndpoint != null
    }

    /**
     * Replace YouTube's 5-song populares teaser with the moreEndpoint list (then continuations),
     * bounded so a huge catalogue cannot fan out on every artist open.
     */
    private suspend fun expandPopularSongsSection(
        page: ArtistPage,
        hideExplicit: Boolean,
        hideVideoSongs: Boolean,
        hideYoutubeShorts: Boolean,
    ) {
        val section = page.sections.firstOrNull { isPopularSongsSection(it) } ?: return
        val more = section.moreEndpoint ?: return
        val shelfSongs = section.items.filterIsInstance<SongItem>()
        val first = YouTube.artistItems(more).getOrNull() ?: return
        val collected = first.items.filterIsInstance<SongItem>().toMutableList()
        var continuation = first.continuation
        var pages = 0
        while (
            currentCoroutineContext().isActive &&
            continuation != null &&
            collected.size < POPULAR_SONGS_CAP &&
            pages < POPULAR_SONGS_MAX_CONTINUATIONS
        ) {
            val next = YouTube.artistItemsContinuation(continuation).getOrNull() ?: break
            collected += next.items.filterIsInstance<SongItem>()
            continuation = next.continuation
            pages++
        }
        if (!currentCoroutineContext().isActive) return
        val expanded = (shelfSongs + collected)
            .distinctBy { it.id }
            .filterExplicit(hideExplicit)
            .filterVideoSongs(hideVideoSongs)
            .filterYoutubeShorts(hideYoutubeShorts)
            .take(POPULAR_SONGS_CAP)
        if (expanded.size <= shelfSongs.size) return
        _expandedPopularSongs.value = expanded
        popularSongsCache[artistId] = expanded
    }

    private fun isPopularSongsSection(section: ArtistSection): Boolean {
        if (section.items.isEmpty()) return false
        if (section.items.any { item -> item !is SongItem || item.isVideoSong }) return false
        return isArtistPopularSongsTitle(section.title)
    }

    /** Same titles the New UI ranks as "Canciones populares" / Songs. */
    private fun isArtistPopularSongsTitle(title: String): Boolean {
        val t = title.trim().lowercase()
        if (t == "songs" || t == "canciones") return true
        return t.contains("top song") ||
            t.contains("popular song") ||
            t.contains("canciones populares") ||
            t.contains("canciones más populares") ||
            t.contains("canciones mas populares") ||
            t.contains("canciones más escuchadas") ||
            t.contains("canciones mas escuchadas") ||
            t.contains("canciones más reproducid") ||
            t.contains("canciones mas reproducid") ||
            t.contains("top track")
    }

    /** YTM shelves that already list guest/featuring credits — merge into our "Aparece en". */
    private fun isAppearsOnSectionTitle(title: String): Boolean {
        val t = title.trim()
        if (t.equals("Aparece en", ignoreCase = true)) return true
        return t.contains("appear", ignoreCase = true) ||
            t.contains("featuring", ignoreCase = true) ||
            t.contains("colabor", ignoreCase = true) ||
            t.contains("appears on", ignoreCase = true)
    }
}
