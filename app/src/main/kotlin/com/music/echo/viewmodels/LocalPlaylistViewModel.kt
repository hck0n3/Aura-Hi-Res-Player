

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

    // Bumped by refreshSuggestions() to regenerate the footer's 5 suggested songs on demand.
    private val suggestionsRefresh = MutableStateFlow(0)

    /** 5 songs recommended FROM this playlist's own content (footer section). */
    val suggestedSongs: StateFlow<List<Song>> =
        combine(playlistSongs, suggestionsRefresh) { songs, refresh -> songs to refresh }
            .flatMapLatest { (songs, refresh) ->
                flow { emit(computeSuggestions(songs, limit = 5, shuffle = refresh > 0)) }
            }
            .flowOn(Dispatchers.IO)
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

    /** Recently added library songs (inLibrary DESC). */
    val recentlyAddedSongs: StateFlow<List<Song>> =
        database
            .librarySongsForTaste(50)
            .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    /** Larger suggested list (~20) for the Add-Music sheet. */
    val sheetSuggestedSongs: StateFlow<List<Song>> =
        playlistSongs
            .flatMapLatest { songs ->
                flow { emit(computeSuggestions(songs, limit = 20, shuffle = false)) }
            }
            .flowOn(Dispatchers.IO)
            .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    /** The user's liked songs (multi-select source). */
    val librarySongs: StateFlow<List<Song>> =
        database
            .likedSongsByCreateDateAsc()
            .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery

    fun onSearchQuery(q: String) {
        _searchQuery.value = q
    }

    /** Global YouTube Music song search for the Add-Music sheet (debounced). */
    val searchResults: StateFlow<List<SongItem>> =
        _searchQuery
            .debounce(350)
            .distinctUntilChanged()
            .flatMapLatest { q ->
                flow {
                    if (q.isBlank()) {
                        emit(emptyList())
                    } else {
                        val items = YouTube
                            .search(q, YouTube.SearchFilter.FILTER_SONG)
                            .getOrNull()
                            ?.items
                            .orEmpty()
                            .filterIsInstance<SongItem>()
                            .distinctBy { it.id }
                        emit(items)
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

    private fun addByIds(songIds: List<String>): Int {
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
        songs: List<PlaylistSong>,
        limit: Int,
        shuffle: Boolean,
    ): List<Song> {
        val playlistIds = songs.map { it.song.id }.toSet()
        if (playlistIds.isEmpty()) return emptyList()

        val seeds = if (shuffle) songs.shuffled() else songs
        val related = seeds
            .flatMap { database.relatedSongs(it.song.id) }
            .filter { it.id !in playlistIds }
            .distinctBy { it.id }
        val local = if (shuffle) related.shuffled() else related
        if (local.isNotEmpty()) return local.take(limit)

        // Local corpus empty (songs never played → no related_song_map). Fall back ONLINE.
        val seedId = seeds.firstOrNull()?.song?.id ?: return emptyList()
        return onlineRelatedSongs(seedId, playlistIds, limit)
    }

    private suspend fun onlineRelatedSongs(
        seedId: String,
        exclude: Set<String>,
        limit: Int,
    ): List<Song> {
        val next = YouTube.next(WatchEndpoint(videoId = seedId)).getOrNull()
        val fromNext = next?.items.orEmpty()
        val fromRelated = next?.relatedEndpoint
            ?.let { YouTube.related(it).getOrNull()?.songs }
            .orEmpty()
        val songItems = (fromNext + fromRelated)
            .distinctBy { it.id }
            .filter { it.id !in exclude }
            .take(limit)
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
