

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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
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
                .map { it.filterExplicit(hideExplicit).filterVideoSongsLocal(hideVideoSongs) }
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
            // A (re)try is starting: clear any previous terminal failure so the screen leaves the
            // retry state and shows progress again.
            _hasFailed.value = false
            // An artist row that was auto-created locally gets a generated id ("LA########", see
            // ArtistEntity.generateArtistId) and therefore has NO YouTube page: YouTube.artist(id) can
            // only fail for it, three times, plus a reportException. Skip the network entirely and leave
            // hasFailed FALSE — ArtistScreen renders these as local (see its showLocal logic). Gated on the
            // library row (not on the id alone) so an artist we simply don't have locally still fetches.
            // An artist row auto-created locally gets a generated id ("LA########") and has no YouTube
            // page of its own — but the ARTIST usually does exist on YouTube. Before falling back to the
            // (tiny) local view, try to resolve the real channel: use a channelId we already stored, else
            // search YouTube by name and accept ONLY an exact normalized name match. The resolved id is
            // persisted on the row, so this costs one search the first time and nothing afterwards.
            // Without this, following such an artist showed at most 6 local albums and NO discography
            // (owner report: "las discografías no salen completas") with no way back to the online page.
            val localRow = database.artist(artistId).first()?.artist
            var effectiveArtistId = artistId
            // `!isLocal` is REQUIRED, not decoration: rows scanned from local files are isLocal = true and
            // their ids are not YouTube ids either, but ArtistScreen always renders them local — resolving
            // them would burn a network search per open whose result can never be shown.
            if (localRow != null && !localRow.isYouTubeArtist && !localRow.isLocal) {
                val cached = resolvedChannelIds[artistId]
                val resolved: String?
                if (cached != null) {
                    resolved = cached
                } else {
                    // Distinguish "YouTube answered, this artist isn't there" from "the request failed".
                    // Only the FORMER may be cached as a miss: caching a transient failure (offline, one
                    // throttled request) would pin the artist to the tiny local view for the whole
                    // session, with no retry — the very dead end this resolution exists to remove.
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
                    // Local-only, offline, or a cached miss: leave hasFailed FALSE so the screen renders
                    // the local view instead of a retry that could never succeed.
                    return@launch
                }
                effectiveArtistId = resolved
                // DELIBERATELY NOT PERSISTED to ArtistEntity.channelId. That column feeds
                // ArtistEntity.toggleLike() and SyncUtils' subscription sync, which call
                // YouTube.subscribeChannel — writing a NAME-MATCHED guess there would make "follow"
                // subscribe the user's real YouTube account, possibly to a same-named wrong artist, with
                // no way to re-resolve. A whole-row @Update from this stale snapshot could also revert a
                // "follow" the user made while the search was in flight (lost update). Session cache only.
                resolvedChannelIds[artistId] = resolved
            }
            // Instant re-open: show this session's cached artist page immediately (no spinner), then
            // still refresh from YouTube below so the data stays up to date. On a COLD first entry (no
            // in-memory cache yet) fall back to the page persisted last session, so the shelves (e.g.
            // "Canciones más escuchadas") render instantly instead of waiting for the slow live fetch —
            // which previously left the screen blank until the user backed out and re-entered.
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
            // Capture any already-resolved "Aparece en" (appears-on) section from this session's cached
            // page or the page just seeded from disk, BEFORE the live fetch below overwrites
            // artistPage/pageCache. If present, the appears-on job reuses it verbatim and SKIPS the ~80
            // YouTube guest searches entirely — so those searches run at most once per artist (per session,
            // and per cache TTL once persisted), never again on revisits.
            val cachedAppearsOn: com.music.innertube.pages.ArtistSection? =
                artistPage?.sections?.find { it.title.equals("Aparece en", ignoreCase = true) }
                    ?: pageCache[artistId]?.sections?.find { it.title.equals("Aparece en", ignoreCase = true) }
            // Retry transient failures (YouTube throttling) so the screen doesn't get stuck on the spinner,
            // which forced the user to leave and re-enter the artist several times.
            var attempt = 0
            var loaded = false
            // `isActive` in the condition: YouTube.artist is runCatching{}-wrapped, so a CANCELLED job
            // gets a plain failure back instead of unwinding — without this a superseded fetch kept
            // burning its remaining attempts.
            while (!loaded && attempt < 3 && isActive) {
            // effectiveArtistId, not artistId: for a locally-created row this is the resolved channel id.
            // The CACHES stay keyed by artistId (what the user navigated to), so lookups still hit.
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
                    // Persist so a COLD first entry next session can show this instantly (see seed above).
                    launch { ArtistPageCache.save(context, artistId, filteredPage) }

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

                    // NOTE: an album-preload used to run here, fetching up to 6 albums the moment the
                    // artist page opened. It saturated YouTube (which then throttles for ~30s), so the
                    // FIRST album you tapped hung ~30s while re-entering worked (the storm was over). It
                    // also only cached album metadata (withSongs=false), which AlbumViewModel re-fetches
                    // anyway — net-negative. Removed: albums now load cleanly on open (AlbumViewModel)
                    // and are cached for instant re-open, with no background storm starving that open.

                    // "Aparece en" (Appears on), like Spotify: albums where this artist is a guest. Built
                    // from iTunes guest credits + a parallel YouTube lookup, appended as its own section.
                    // Cache-first: once resolved, the section is written back into pageCache + disk, so a
                    // later visit (this session, or a cold restart within the cache TTL) reuses it and skips
                    // the ~80 guest YouTube searches entirely. Those searches run at most ONCE per artist —
                    // not on every revisit, which was cooking battery/network and risking throttling.
                    launch(Dispatchers.IO) {
                        val artistName = page.artist?.title ?: return@launch
                        val items: List<com.music.innertube.models.YTItem> = if (cachedAppearsOn != null) {
                            // Already resolved on a previous visit — reuse verbatim, zero new searches.
                            // (Re-apply the hide-explicit filter in case the preference changed since.)
                            cachedAppearsOn.items.filter { !hideExplicit || !it.explicit }
                        } else {
                            val norm = iad1tya.echo.music.utils.iTunesDiscography::normalizeTitle
                            // Cap at 40 (covers virtually all real collaborations) with GENTLE concurrency:
                            // resolving the full uncapped iTunes list (100+ items) with high concurrency flooded
                            // YouTube and made the whole app's responses slower. 40 keeps it complete enough
                            // without hammering the network.
                            val guest = iad1tya.echo.music.utils.iTunesDiscography
                                .fetchAppearsOn(artistName, "us")
                                .take(40)
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
                            found.distinctBy { it.id }.filter { !hideExplicit || !it.explicit }
                        }
                        if (items.isEmpty()) return@launch
                        withContext(Dispatchers.Main) {
                            val current = artistPage ?: return@withContext
                            if (current.sections.any { it.title.equals("Aparece en", ignoreCase = true) }) return@withContext
                            val updated = current.copy(
                                sections = current.sections + com.music.innertube.pages.ArtistSection(
                                    title = "Aparece en",
                                    items = items,
                                    moreEndpoint = null,
                                ),
                            )
                            artistPage = updated
                            // Write the resolved page back so revisits reuse the appears-on section instantly:
                            // pageCache for this session, disk (ArtistPageCache) for cold restarts within TTL.
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
}
