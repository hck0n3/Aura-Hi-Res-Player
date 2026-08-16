

package iad1tya.echo.music.viewmodels

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.music.innertube.YouTube
import com.music.innertube.models.PlaylistItem
import com.music.innertube.models.SongItem
import com.music.innertube.models.YTItem
import com.music.innertube.models.filterVideoSongs
import iad1tya.echo.music.constants.HideVideoSongsKey
import iad1tya.echo.music.db.MusicDatabase
import iad1tya.echo.music.utils.PlaylistCompletion
import iad1tya.echo.music.utils.dataStore
import iad1tya.echo.music.utils.get
import iad1tya.echo.music.utils.reportException
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class OnlinePlaylistViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    savedStateHandle: SavedStateHandle,
    private val database: MusicDatabase
) : ViewModel() {
    private val playlistId = savedStateHandle.get<String>("playlistId")!!
    private val autoSave = savedStateHandle.get<Boolean>("autoSave") ?: false
    private var hasAutoSaved = false

    val playlist = MutableStateFlow<PlaylistItem?>(null)

    // Raw playlist order as returned by YouTube (relatedness order). The public [playlistSongs] pins the
    // user's liked songs to the top; keeping the raw list separate lets continuations append cleanly.
    private val _rawSongs = MutableStateFlow<List<SongItem>>(emptyList())

    // Ids of songs the user has liked locally. Online SongItems carry no local `liked` flag, so we
    // cross-reference the DB to pin liked items to the top. Updates live as the user likes/unlikes.
    private val likedIds = database.likedSongIds()
        .map { it.toHashSet() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, HashSet())

    // Displayed list: liked songs pinned to the top, YouTube relatedness order preserved within each
    // group (sortedBy is stable). The screen builds both the row list AND the play queue from this, so
    // the tapped index stays aligned after the re-sort.
    val playlistSongs: StateFlow<List<SongItem>> =
        combine(_rawSongs, likedIds) { songs, liked ->
            songs.sortedBy { if (it.id in liked) 0 else 1 }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val relatedItems = MutableStateFlow<List<YTItem>>(emptyList())

    private val _isLoading = MutableStateFlow(true)
    val isLoading = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()

    private val _isLoadingMore = MutableStateFlow(false)
    val isLoadingMore = _isLoadingMore.asStateFlow()

    val dbPlaylist = database.playlistByBrowseId(playlistId)
        .stateIn(viewModelScope, SharingStarted.Lazily, null)

    @Volatile
    var continuation: String? = null
        private set

    private var proactiveLoadJob: Job? = null

    // Background "completion / repair" pass — swaps unplayable tracks for playable equivalents. Kept as its
    // own job so it can be cancelled/retried independently of paging and never blocks initial display.
    private var completionJob: Job? = null

    // True once EVERY page of the playlist has been loaded (continuation exhausted normally, not via an
    // error). Only then is the repaired list cached for the session — a failure-truncated load must be
    // repaired for display but retried (and re-completed) on the next open.
    @Volatile
    private var reachedEnd = false

    companion object {
        // Per-playlist session cache of the repaired (completion-driven) song list, keyed by playlistId, so
        // re-opening the same playlist shows the fully-playable list instantly and never re-runs the repair
        // network calls. Mirrors ArtistItemsViewModel.completedCache. In-memory only (no Room migration).
        private val completedCache =
            java.util.concurrent.ConcurrentHashMap<String, List<SongItem>>()
    }

    init {
        fetchInitialPlaylistData()
    }

    private fun fetchInitialPlaylistData() {
        viewModelScope.launch(Dispatchers.IO) {
            _isLoading.value = true
            _error.value = null
            continuation = null
            reachedEnd = false
            proactiveLoadJob?.cancel()
            completionJob?.cancel()

            YouTube.playlist(playlistId)
                .onSuccess { playlistPage ->
                    playlist.value = playlistPage.playlist
                    _rawSongs.value = applySongFilters(playlistPage.songs)
                    relatedItems.value = playlistPage.related ?: emptyList()
                    continuation = playlistPage.songsContinuation
                    _isLoading.value = false
                    refreshStoredThumbnail(playlistPage.playlist)

                    if (autoSave && !hasAutoSaved && _rawSongs.value.isNotEmpty()) {
                        hasAutoSaved = true
                        viewModelScope.launch(Dispatchers.IO) {
                            val existing = database.playlistByBrowseId(playlistId).first()
                            if (existing == null) {
                                database.withTransaction {
                                    val newPlaylist = iad1tya.echo.music.db.entities.PlaylistEntity(
                                        name = playlistPage.playlist.title,
                                        browseId = playlistPage.playlist.id,
                                    )
                                    database.insert(newPlaylist)
                                    val playlistObj = iad1tya.echo.music.db.entities.Playlist(
                                        playlist = newPlaylist,
                                        songCount = 0,
                                        songThumbnails = emptyList()
                                    )
                                    database.addSongToPlaylist(playlistObj, _rawSongs.value.map { it.id })
                                }
                            }
                        }
                    }

                    // Instant re-open: a fully-repaired list from earlier this session is the authority and
                    // needs no more network (the base fetch already gave us metadata/related). It was cached
                    // only after a COMPLETE load, so it holds every track — stop paging and publish in one
                    // shot instead of re-completing.
                    val cached = completedCache[playlistId]
                    if (cached != null) {
                        _rawSongs.value = cached
                        continuation = null
                        reachedEnd = true
                        proactiveLoadJob?.cancel()
                        return@onSuccess
                    }

                    if (continuation != null) {
                        startProactiveBackgroundLoading()
                    } else {
                        // Single-page playlist: fully loaded now → repair it in the background, one-shot.
                        reachedEnd = true
                        launchPlaylistCompletion()
                    }
                }.onFailure { throwable ->
                    _error.value = throwable.message?.takeIf { it.isNotBlank() }
                        ?: throwable::class.java.simpleName
                        ?: "Failed to load playlist"
                    _isLoading.value = false
                    reportException(throwable)
                }
        }
    }

    private fun startProactiveBackgroundLoading() {
        proactiveLoadJob?.cancel() 
        proactiveLoadJob = viewModelScope.launch(Dispatchers.IO) {
            var currentProactiveToken = continuation
            while (currentProactiveToken != null && isActive) {
                
                if (_isLoadingMore.value) {
                    
                    
                    break 
                }

                YouTube.playlistContinuation(currentProactiveToken)
                    .onSuccess { playlistContinuationPage ->
                        val currentSongs = _rawSongs.value.toMutableList()
                        currentSongs.addAll(playlistContinuationPage.songs)
                        _rawSongs.value = applySongFilters(currentSongs)
                        currentProactiveToken = playlistContinuationPage.continuation

                        this@OnlinePlaylistViewModel.continuation = currentProactiveToken
                    }.onFailure { throwable ->
                        reportException(throwable)
                        currentProactiveToken = null
                    }
            }

            // The loop ended. If it was NOT pre-empted by a manual loadMore (which flips _isLoadingMore and
            // breaks / cancels this job), every page we could load is in → run the ONE background repair
            // pass over the full list. Cache only when the continuation drained cleanly (a mid-load failure
            // leaves continuation non-null, so it repairs for display but is not cached → retried next time).
            if (isActive && !_isLoadingMore.value) {
                reachedEnd = this@OnlinePlaylistViewModel.continuation == null
                launchPlaylistCompletion()
            }
        }
    }

    fun loadMoreSongs() {
        if (_isLoadingMore.value) return 
        
        val tokenForManualLoad = continuation ?: return 

        proactiveLoadJob?.cancel() 
        _isLoadingMore.value = true

        viewModelScope.launch(Dispatchers.IO) {
            YouTube.playlistContinuation(tokenForManualLoad)
                .onSuccess { playlistContinuationPage ->
                    val currentSongs = _rawSongs.value.toMutableList()
                    currentSongs.addAll(playlistContinuationPage.songs)
                    _rawSongs.value = applySongFilters(currentSongs)
                    continuation = playlistContinuationPage.continuation
                }.onFailure { throwable ->
                    reportException(throwable)
                }.also {
                    _isLoadingMore.value = false

                    if (continuation != null && isActive) {
                        startProactiveBackgroundLoading()
                    } else if (continuation == null && isActive) {
                        // Manual load drained the last page → nothing left to page. Run the repair pass now,
                        // since proactive loading won't be (re)started to reach the end.
                        reachedEnd = true
                        launchPlaylistCompletion()
                    }
                }
        }
    }

    /**
     * Launch the bounded background repair pass over the CURRENT full song list. Swaps unplayable tracks
     * (duration == null) for playable equivalents via [PlaylistCompletion], then publishes the repaired list
     * in ONE shot — never blocking initial display and keeping [continuation] alive. Idempotent: a no-op
     * repair never overwrites the live list.
     */
    private fun launchPlaylistCompletion() {
        completionJob?.cancel()
        completionJob = viewModelScope.launch(Dispatchers.IO) {
            val snapshot = _rawSongs.value
            if (snapshot.isEmpty()) return@launch
            val repaired = runCatching { PlaylistCompletion.completePlaylist(snapshot) }
                .getOrDefault(snapshot)
            if (repaired !== snapshot && repaired != snapshot) {
                // Some track was actually swapped: cache (when the load was complete) and publish once.
                if (reachedEnd) completedCache[playlistId] = repaired
                publishRepaired(repaired, snapshot)
            } else if (reachedEnd) {
                // Nothing needed repair, but the list is final — cache it so re-open is instant and skips
                // the pass entirely. No publish (the live list is already identical).
                completedCache[playlistId] = repaired
            }
        }
    }

    /**
     * Publish [repaired] as the new raw list, merging in any songs the live list gained AFTER [snapshot]
     * (mirrors the merge discipline in ArtistItemsViewModel). [repaired] is the authority for the tracks it
     * covers; only genuinely-new live ids are appended (never a swapped-out original — those ids are in the
     * snapshot), so a late page can't be lost and a broken original can't be resurrected. Keeps continuation.
     */
    private fun publishRepaired(repaired: List<SongItem>, snapshot: List<SongItem>) {
        val repairedIds = repaired.mapTo(HashSet()) { it.id }
        val snapshotIds = snapshot.mapTo(HashSet()) { it.id }
        // Atomic read-modify-write so a page appended by a concurrent loadMoreSongs can't be lost.
        _rawSongs.update { liveNow ->
            val extras = liveNow.filter { it.id !in repairedIds && it.id !in snapshotIds }
            if (extras.isEmpty()) repaired else repaired + extras
        }
    }

    /**
     * Saved copies of online playlists capture thumbnailUrl ONCE at save-time and only account sync
     * (SyncUtils → DatabaseDao.update(entity, item)) ever refreshes it — YT/Spotify auto-mosaic URLs
     * rot when the playlist's content changes, leaving the Library tile dead for users without
     * login/ytmSync. Opening the playlist just fetched the fresh URL, so mirror it into the saved
     * entity. Custom covers (studio_square_thumbnail uploads or local content:// crops) are the
     * user's explicit choice and must never be overwritten. Queries the DAO directly instead of
     * [dbPlaylist].value because that StateFlow is Lazily-shared and may not have emitted yet.
     */
    private fun refreshStoredThumbnail(remotePlaylist: PlaylistItem) {
        val newThumbnailUrl = remotePlaylist.thumbnail ?: return
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val saved = database.playlistByBrowseId(playlistId).first() ?: return@launch
                val current = saved.playlist.thumbnailUrl
                val isCustomCover = current != null &&
                    (current.contains("studio_square_thumbnail") || current.contains("content://"))
                if (!isCustomCover && current != newThumbnailUrl) {
                    database.query {
                        update(saved.playlist.copy(thumbnailUrl = newThumbnailUrl))
                    }
                }
            }.onFailure { reportException(it) }
        }
    }

    fun retry() {
        proactiveLoadJob?.cancel()
        fetchInitialPlaylistData()
    }

    private fun applySongFilters(songs: List<SongItem>): List<SongItem> {
        val hideVideoSongs = context.dataStore.get(HideVideoSongsKey, false)
        val uniqueSongs = songs.distinctBy { it.id }
        if (!hideVideoSongs) return uniqueSongs

        val filtered = uniqueSongs.filterVideoSongs(true)
        // If filtering hides everything, keep original list to avoid false "empty playlist" UX.
        return if (filtered.isEmpty() && uniqueSongs.isNotEmpty()) uniqueSongs else filtered
    }

    override fun onCleared() {
        super.onCleared()
        proactiveLoadJob?.cancel()
        completionJob?.cancel()
    }
}
