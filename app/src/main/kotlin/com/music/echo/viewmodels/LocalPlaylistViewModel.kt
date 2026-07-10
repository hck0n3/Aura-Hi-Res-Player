

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
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
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
import java.util.Collections
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
                flow { emit(computeSuggestions(ids, limit = 20, seed = nonce)) }
            }
            .flowOn(Dispatchers.IO)
            .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    /** Artists for the "Featured Artists" row: the playlist's own artists most-frequent first; if
     *  fewer than 6, topped up with the artists of the top suggested songs so the row still looks
     *  full — still 100% content-derived. Artists with a blank id or name are dropped, and
     *  duplicates (same id OR same normalized name) collapse into one avatar. */
    val featuredArtists: StateFlow<List<ArtistEntity>> =
        combine(playlistSongs, sheetSuggestedSongs) { songs, suggested ->
            val byId = LinkedHashMap<String, ArtistEntity>()
            val count = HashMap<String, Int>()
            songs.forEach { ps ->
                ps.song.artists.forEach { artist ->
                    if (artist.id.isBlank() || artist.name.isBlank()) return@forEach
                    byId.putIfAbsent(artist.id, artist)
                    count[artist.id] = (count[artist.id] ?: 0) + 1
                }
            }
            val result = byId.values
                .sortedByDescending { count[it.id] ?: 0 }
                .distinctBy { it.name.trim().lowercase() }
                .toMutableList()
            if (result.size < 6) {
                val seenIds = result.mapTo(HashSet()) { it.id }
                val seenNames = result.mapTo(HashSet()) { it.name.trim().lowercase() }
                outer@ for (song in suggested) {
                    for (artist in song.artists) {
                        if (result.size >= 6) break@outer
                        if (artist.id.isBlank() || artist.name.isBlank()) continue
                        val nameKey = artist.name.trim().lowercase()
                        if (artist.id in seenIds || nameKey in seenNames) continue
                        result += artist
                        seenIds += artist.id
                        seenNames += nameKey
                    }
                }
            }
            result.take(12)
        }
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
        sessionAddedIds += toAdd
        // Pure-local playlists have browseId == null (the only case this feature is shown), but keep the
        // YouTube mirror for safety/parity with AddToPlaylistDialog if ever used on a synced playlist.
        pl.playlist.browseId?.let { browseId ->
            toAdd.forEach { YouTube.addToPlaylist(browseId, it) }
        }
        return toAdd.size
    }

    /** Ids surfaced by earlier suggestion batches this session — excluded on refresh so every tap
     *  yields fresh candidates; cleared once the pool runs dry so suggestions never go empty.
     *  Only touched from the (serialized) suggestions flow. */
    private val shownSuggestionIds = mutableSetOf<String>()

    /** Ids added to the playlist through this ViewModel this session — extra exclusion guard while
     *  the Room id-set flow re-emits. Synchronized: written from add paths, read by the engine. */
    private val sessionAddedIds: MutableSet<String> = Collections.synchronizedSet(mutableSetOf())

    /** Online suggestion ids already persisted by this ViewModel — bounds the DB insert to at most
     *  ONE call per id per session (insert() is IGNORE-on-conflict anyway, so this is belt-and-braces). */
    private val insertedOnlineIds = mutableSetOf<String>()

    /**
     * Suggestion engine — "the playlist's own content, ranked":
     *  1. SEEDS: up to 8 of the playlist's OWN songs, stride-sampled so they SPREAD across the whole
     *     playlist (never just the first song); the sample window rotates with each refresh nonce.
     *  2. LOCAL layer: database.relatedSongs(seed) for every sampled seed.
     *  3. ONLINE layer (YouTube's relatedness algorithm): next(seed).relatedEndpoint → related() for
     *     up to 3 of the sampled seeds, fetched in parallel — always blended in, not fallback-only.
     *  4. RANK by relatedness frequency (how many DISTINCT seeds a candidate is related to), with
     *     per-seed round-robin as the tiebreak so no single seed dominates the head of the list.
     *  5. ARTIST DIVERSITY: at most 2 songs per primary artist in the top list (overflow goes to the
     *     tail), so 5 footer suggestions are never one artist.
     *  6. EXCLUDES playlist songs, songs added this session, and (until the pool runs dry)
     *     previously shown batches, so refresh doesn't recycle the same candidates.
     *  7. BACKFILL from quickPicks (recently/most played) when relatedness alone can't fill [limit]
     *     — restores the "playlist content + recently played" blend, offline-safe.
     */
    private suspend fun computeSuggestions(
        songIds: List<String>,
        limit: Int,
        seed: Int = 0,
    ): List<Song> {
        if (songIds.isEmpty()) return emptyList()
        val excluded = songIds.toHashSet() + sessionAddedIds.toSet()

        // 1. Stride-sampled seeds, rotated by the refresh nonce.
        val seedCount = minOf(8, songIds.size)
        val stride = maxOf(1, songIds.size / seedCount)
        val seedSongs = List(seedCount) { i -> songIds[(seed + i * stride).mod(songIds.size)] }
            .distinct()

        // 2 + 3. Fetch both layers; the online fetches run in parallel and fail soft (offline-safe).
        val onlineSeeds = seedSongs.take(3)
        val onlineBySeed: List<Pair<String, List<SongItem>>> = coroutineScope {
            onlineSeeds.map { sid ->
                async { sid to runCatching { onlineRelatedItems(sid) }.getOrDefault(emptyList()) }
            }.awaitAll()
        }
        val localBySeed: List<Pair<String, List<Song>>> =
            seedSongs.map { sid -> sid to database.relatedSongs(sid).distinctBy { it.id } }

        // 4. Accumulate candidates round-robin (position j of every seed, then j+1, …); count how
        // many DISTINCT seeds each candidate is related to.
        val seedHits = HashMap<String, MutableSet<String>>()
        val orderIds = LinkedHashSet<String>() // round-robin first-seen order = the tiebreak
        val localById = HashMap<String, Song>()
        val onlineById = HashMap<String, SongItem>()

        fun offerLocal(seedId: String, song: Song) {
            if (song.id in excluded) return
            seedHits.getOrPut(song.id) { mutableSetOf() } += seedId
            orderIds += song.id
            localById.putIfAbsent(song.id, song)
        }

        fun offerOnline(seedId: String, item: SongItem) {
            if (item.id in excluded) return
            seedHits.getOrPut(item.id) { mutableSetOf() } += seedId
            orderIds += item.id
            onlineById.putIfAbsent(item.id, item)
        }

        val maxLocal = localBySeed.maxOfOrNull { it.second.size } ?: 0
        for (j in 0 until maxLocal) {
            localBySeed.forEach { (sid, list) -> list.getOrNull(j)?.let { offerLocal(sid, it) } }
        }
        val maxOnline = onlineBySeed.maxOfOrNull { it.second.size } ?: 0
        for (j in 0 until maxOnline) {
            onlineBySeed.forEach { (sid, list) -> list.getOrNull(j)?.let { offerOnline(sid, it) } }
        }

        val firstSeen = orderIds.withIndex().associate { (i, id) -> id to i }
        val ranked = orderIds.sortedWith(
            compareByDescending<String> { seedHits[it]?.size ?: 0 }
                .thenBy { firstSeen[it] ?: Int.MAX_VALUE },
        )

        // 6. Cross-refresh memory: prefer never-shown candidates; once they can't fill a batch,
        // reset the memory so the pool recycles instead of going empty.
        val fresh = ranked.filter { it !in shownSuggestionIds }
        if (fresh.size < limit) shownSuggestionIds.clear()
        val freshSet = fresh.toHashSet()
        val ordered = fresh + ranked.filter { it !in freshSet }

        // 5. Artist-diversity pass: greedy max 2 per primary artist; overflow appended at the tail.
        fun primaryArtist(id: String): String =
            localById[id]?.artists?.firstOrNull()?.id
                ?: onlineById[id]?.artists?.firstOrNull()?.id
                ?: id
        val perArtist = HashMap<String, Int>()
        val head = ArrayList<String>()
        val overflow = ArrayList<String>()
        for (id in ordered) {
            val key = primaryArtist(id)
            val n = perArtist[key] ?: 0
            if (n < 2) {
                perArtist[key] = n + 1
                head += id
            } else {
                overflow += id
            }
        }
        val chosen = (head + overflow).take(limit).toMutableList()

        // 7. Recently-played backfill (quickPicks) if relatedness alone couldn't fill the batch.
        if (chosen.size < limit) {
            val chosenSet = chosen.toHashSet()
            database.quickPicks().first()
                .asSequence()
                .filter { it.id !in excluded && it.id !in chosenSet }
                .take(limit - chosen.size)
                .forEach { qp ->
                    localById.putIfAbsent(qp.id, qp)
                    chosen += qp.id
                }
        }

        val result = chosen.mapNotNull { id ->
            localById[id] ?: onlineById[id]?.let { resolveOnlineSong(it) }
        }
        shownSuggestionIds += result.map { it.id }
        return result
    }

    /** Raw online-related candidates for one seed (YouTube's own relatedness). No DB writes here —
     *  only the songs that make the FINAL list get persisted (see [resolveOnlineSong]). */
    private suspend fun onlineRelatedItems(seedId: String): List<SongItem> {
        val next = YouTube.next(WatchEndpoint(videoId = seedId)).getOrNull()
        val fromNext = next?.items.orEmpty()
        val fromRelated = next?.relatedEndpoint
            ?.let { YouTube.related(it).getOrNull()?.songs }
            .orEmpty()
        return (fromNext + fromRelated).distinctBy { it.id }
    }

    /** Persist an online suggestion (at most one insert per id per session; insert() is
     *  IGNORE-on-conflict and never clobbers liked/library flags) and resolve it as a [Song] so the
     *  "+" (add-by-id) path can find it. */
    private suspend fun resolveOnlineSong(item: SongItem): Song? {
        if (item.id !in insertedOnlineIds) {
            database.insert(item.toMediaMetadata())
            insertedOnlineIds += item.id
        }
        return database.song(item.id).first()
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
