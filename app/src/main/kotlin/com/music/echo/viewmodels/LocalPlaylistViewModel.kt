

package iad1tya.echo.music.viewmodels

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import iad1tya.echo.music.constants.HideVideoSongsKey
import iad1tya.echo.music.constants.PlaylistSongSortDescendingKey
import iad1tya.echo.music.constants.PlaylistSongSortType
import iad1tya.echo.music.constants.PlaylistSongSortTypeKey
import iad1tya.echo.music.db.MusicDatabase
import iad1tya.echo.music.db.entities.ArtistEntity
import iad1tya.echo.music.db.entities.PlaylistSong
import iad1tya.echo.music.db.entities.Song
import iad1tya.echo.music.extensions.reversed
import iad1tya.echo.music.extensions.toEnum
import iad1tya.echo.music.models.toMediaMetadata
import iad1tya.echo.music.utils.SyncUtils
import iad1tya.echo.music.utils.dataStore
import com.music.innertube.YouTube
import com.music.innertube.models.SongItem
import com.music.innertube.models.WatchEndpoint
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.Collator
import java.util.Locale
import javax.inject.Inject
import kotlin.random.Random

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel
class LocalPlaylistViewModel
@Inject
constructor(
    @ApplicationContext context: Context,
    private val database: MusicDatabase,
    private val syncUtils: SyncUtils,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    val playlistId = savedStateHandle.get<String>("playlistId")!!
    val playlist =
        database
            .playlist(playlistId)
            .stateIn(viewModelScope, SharingStarted.Lazily, null)
    val playlistSongs: StateFlow<List<PlaylistSong>> =
        combine(
            database.playlistSongs(playlistId),
            context.dataStore.data
                .map {
                    Triple(
                        it[PlaylistSongSortTypeKey].toEnum(PlaylistSongSortType.CUSTOM),
                        it[PlaylistSongSortDescendingKey] ?: true,
                        it[HideVideoSongsKey] ?: false
                    )
                }.distinctUntilChanged(),
        ) { songs, (sortType, sortDescending, hideVideoSongs) ->
            val filteredSongs = if (hideVideoSongs) {
                songs.filter { !it.song.song.isVideo }
            } else {
                songs
            }
            when (sortType) {
                PlaylistSongSortType.CUSTOM -> filteredSongs
                PlaylistSongSortType.CREATE_DATE -> filteredSongs.sortedBy { it.map.id }
                PlaylistSongSortType.NAME -> {
                    val collator = Collator.getInstance(Locale.getDefault())
                    collator.strength = Collator.PRIMARY
                    filteredSongs.sortedWith(compareBy(collator) { it.song.song.title })
                }
                PlaylistSongSortType.ARTIST -> {
                    val collator = Collator.getInstance(Locale.getDefault())
                    collator.strength = Collator.PRIMARY
                    filteredSongs
                        .sortedWith(compareBy(collator) { song -> song.song.artists.joinToString("") { it.name } })
                        .groupBy { it.song.album?.title }
                        .flatMap { (_, songsByAlbum) ->
                            songsByAlbum.sortedBy {
                                it.song.artists.joinToString(
                                    ""
                                ) { it.name }
                            }
                        }
                }

                PlaylistSongSortType.PLAY_TIME -> filteredSongs.sortedBy { it.song.song.totalPlayTime }
            }.reversed(sortDescending && sortType != PlaylistSongSortType.CUSTOM)
        }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    // ---- Apple-Music-style "Add Music" feature ----------------------------------------------------

    // Bumped by refreshSuggestions(); its value doubles as a per-refresh shuffle/seed nonce (NOT a
    // latched boolean) so every tap rotates to fresh candidates.
    private val suggestionsRefresh = MutableStateFlow(0)

    // The SET of song ids in this playlist. Suggestions key off THIS (not the full playlistSongs, which
    // also re-emits on sort / hide-video changes) so the network + insert side effects don't re-run on
    // every reorder.
    private val playlistSongIds: StateFlow<List<String>> =
        playlistSongs
            .map { songs -> songs.map { it.song.id } }
            .distinctUntilChanged { old, new -> old.toSet() == new.toSet() }
            .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    fun refreshSuggestions() {
        suggestionsRefresh.value = suggestionsRefresh.value + 1
    }

    /** Distinct artists across this playlist's songs, most-frequent first, up to 12. */
    val featuredArtists: StateFlow<List<ArtistEntity>> =
        playlistSongs
            .map { songs ->
                val byId = LinkedHashMap<String, ArtistEntity>()
                val count = HashMap<String, Int>()
                songs.forEach { ps ->
                    ps.song.artists.forEach { artist ->
                        byId.putIfAbsent(artist.id, artist)
                        count[artist.id] = (count[artist.id] ?: 0) + 1
                    }
                }
                byId.values
                    .sortedByDescending { count[it.id] ?: 0 }
                    .take(12)
            }
            .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    /** Most-played songs in the last ~30 days ("From Replay"). */
    val replaySongs: StateFlow<List<Song>> =
        database
            .mostPlayedSongs(System.currentTimeMillis() - 30L * 24 * 60 * 60 * 1000, limit = 30)
            .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    /** Recently added library songs, newest-first (inLibrary DESC). librarySongsForTaste orders by
     *  rowId (song-table insertion order), not library-add time, so reuse songsByCreateDateAsc
     *  (inLibrary ASC) reversed. */
    val recentlyAddedSongs: StateFlow<List<Song>> =
        database
            .songsByCreateDateAsc()
            .map { it.asReversed().take(50) }
            .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    /** Larger suggested list (~20) for the Add-Music sheet; the footer's 5 are derived from this so the
     *  (network-touching) computation runs ONCE, not twice. */
    val sheetSuggestedSongs: StateFlow<List<Song>> =
        combine(playlistSongIds, suggestionsRefresh) { ids, nonce -> ids to nonce }
            .flatMapLatest { (ids, nonce) ->
                flow { emit(computeSuggestions(ids, limit = 20, shuffle = nonce > 0, seed = nonce)) }
            }
            .flowOn(Dispatchers.IO)
            .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    /** Footer's 5 suggestions — the head of [sheetSuggestedSongs] (single shared computation). */
    val suggestedSongs: StateFlow<List<Song>> =
        sheetSuggestedSongs
            .map { it.take(5) }
            .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    /** The user's whole library (multi-select source), newest-first. */
    val librarySongs: StateFlow<List<Song>> =
        database
            .songsByCreateDateAsc()
            .map { it.asReversed() }
            .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery

    fun onSearchQuery(q: String) {
        _searchQuery.value = q
    }

    private val _searchLoading = MutableStateFlow(false)

    /** True while a global YouTube-Music search is in flight, so the sheet can tell "loading" apart from
     *  "no results" instead of spinning forever on a zero-result / failed search. */
    val searchLoading: StateFlow<Boolean> = _searchLoading

    /** Global YouTube Music song search for the Add-Music sheet (debounced). */
    val searchResults: StateFlow<List<SongItem>> =
        _searchQuery
            .debounce(350)
            .distinctUntilChanged()
            .flatMapLatest { q ->
                flow {
                    if (q.isBlank()) {
                        _searchLoading.value = false
                        emit(emptyList())
                        return@flow
                    }
                    _searchLoading.value = true
                    emit(emptyList()) // drop stale results while the new query loads
                    try {
                        val items = YouTube
                            .search(q, YouTube.SearchFilter.FILTER_SONG)
                            .getOrNull()
                            ?.items
                            .orEmpty()
                            .filterIsInstance<SongItem>()
                            .distinctBy { it.id }
                        emit(items)
                    } finally {
                        _searchLoading.value = false
                    }
                }.flowOn(Dispatchers.IO)
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Add local songs (by id) to THIS playlist, skipping duplicates. */
    fun addLocalSongs(songIds: List<String>, onDone: (Int) -> Unit = {}) {
        viewModelScope.launch(Dispatchers.IO) {
            val added = addByIds(songIds)
            withContext(Dispatchers.Main) { onDone(added) }
        }
    }

    /** Insert online songs into the DB then add them to THIS playlist, skipping duplicates. */
    fun addOnlineSongs(items: List<SongItem>, onDone: (Int) -> Unit = {}) {
        viewModelScope.launch(Dispatchers.IO) {
            items.forEach { database.insert(it.toMediaMetadata()) }
            val added = addByIds(items.map { it.id })
            withContext(Dispatchers.Main) { onDone(added) }
        }
    }

    private suspend fun addByIds(songIds: List<String>): Int {
        if (songIds.isEmpty()) return 0
        val pl = playlist.value ?: return 0
        val duplicates = database.playlistDuplicates(pl.id, songIds)
        val toAdd = songIds.filter { it !in duplicates }
        if (toAdd.isEmpty()) return 0
        database.addSongToPlaylist(pl, toAdd)
        // Pure-local playlists have browseId == null (the only case this feature is shown), but keep the
        // YouTube mirror for safety/parity with AddToPlaylistDialog if ever used on a synced playlist.
        pl.playlist.browseId?.let { browseId ->
            toAdd.forEach { YouTube.addToPlaylist(browseId, it) }
        }
        return toAdd.size
    }

    private suspend fun computeSuggestions(
        songIds: List<String>,
        limit: Int,
        shuffle: Boolean,
        seed: Int = 0,
    ): List<Song> {
        val idSet = songIds.toSet()
        if (idSet.isEmpty()) return emptyList()

        val random = Random(seed)
        val seeds = if (shuffle) songIds.shuffled(random) else songIds
        val related = seeds
            .flatMap { database.relatedSongs(it) }
            .filter { it.id !in idSet }
            .distinctBy { it.id }
        val local = if (shuffle) related.shuffled(random) else related
        if (local.size >= limit) return local.take(limit)

        // Not enough local related candidates (small / never-played corpus) — top up ONLINE. Rotate the
        // seed song per refresh so each refresh pulls fresh candidates instead of the same few.
        val seedSong = seeds[seed.mod(seeds.size)]
        val exclude = idSet + local.mapTo(HashSet()) { it.id }
        val online = onlineRelatedSongs(seedSong, exclude, limit - local.size)
        return (local + online).distinctBy { it.id }.take(limit)
    }

    private suspend fun onlineRelatedSongs(
        seedId: String,
        exclude: Set<String>,
        limit: Int,
    ): List<Song> {
        if (limit <= 0) return emptyList()
        val next = YouTube.next(WatchEndpoint(videoId = seedId)).getOrNull()
        val fromNext = next?.items.orEmpty()
        val fromRelated = next?.relatedEndpoint
            ?.let { YouTube.related(it).getOrNull()?.songs }
            .orEmpty()
        val songItems = (fromNext + fromRelated)
            .distinctBy { it.id }
            .filter { it.id !in exclude }
            .take(limit)
        // insert() is IGNORE-on-conflict — idempotent and never clobbers an existing song's liked /
        // library flags. Persisting here is what lets the "+" (add-by-id) path resolve these songs;
        // it's bounded to playlist set-changes / refreshes by the distinctUntilChanged above, so it no
        // longer runs on every recomposition.
        return songItems.mapNotNull { item ->
            database.insert(item.toMediaMetadata())
            database.song(item.id).first()
        }
    }

    init {
        viewModelScope.launch {

            playlist.first { it != null }?.playlist?.browseId?.let { browseId ->
                syncUtils.syncPlaylist(browseId, playlistId)
            }
        }

        viewModelScope.launch {
            val sortedSongs =
                playlistSongs.first().sortedWith(compareBy({ it.map.position }, { it.map.id }))
            database.transaction {
                sortedSongs.forEachIndexed { index, playlistSong ->
                    if (playlistSong.map.position != index) {
                        update(playlistSong.map.copy(position = index))
                    }
                }
            }
        }
    }
}
