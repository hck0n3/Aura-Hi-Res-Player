

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
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject

/** How many suggestions the footer's "Suggested Songs" section shows — the fill target that decides
 *  whether the instant cache-only batch needs a background network top-up. */
private const val FOOTER_SUGGESTIONS = 5

/** How many suggestions the Add-Music sheet shows (the full batch size computed per pass). The
 *  network top-up (Phase B) also escalates when the cached relatedness pool fills less than HALF of
 *  this, so the sheet isn't mostly quickPicks padding even when the footer's 5 look fine. */
private const val SHEET_SUGGESTIONS = 20

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

    /** Last accepted refresh tap — a SMALL debounce (only double-fire protection). The visible list
     *  now swaps instantly (cache-only Phase A), so this no longer needs to cover a network window. */
    private var lastRefreshTapMs = 0L

    private val _isRefreshingSuggestions = MutableStateFlow(false)

    /** True ONLY while the background network top-up (Phase B) is in flight — never during the instant
     *  cache-only swap. Drives just the refresh button's spinner/disable; the list already updated. */
    val isRefreshingSuggestions: StateFlow<Boolean> = _isRefreshingSuggestions

    private val _suggestionsLoaded = MutableStateFlow(false)

    /** False until the FIRST suggestion batch has been computed (even if it came back empty). Lets the
     *  footer distinguish "still computing the instant local-first pass" from "computed, genuinely
     *  empty" and show shimmer placeholders instead of a long blank on first open. */
    val suggestionsLoaded: StateFlow<Boolean> = _suggestionsLoaded

    fun refreshSuggestions() {
        val now = System.currentTimeMillis()
        // Small debounce: reject only a rapid double-fire; a single tap swaps the list instantly.
        if (now - lastRefreshTapMs < 300) return
        lastRefreshTapMs = now
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
                flow {
                    // Snapshot the refresh flag BEFORE Phase A runs — Phase A is recordShown=false so it
                    // won't latch lastAppliedNonce, keeping this accurate for the branch below.
                    val wasRefresh = nonce > lastAppliedNonce

                    // ── Phase A — INSTANT swap. On a refresh tap: local DB relatedSongs ∪ already-cached
                    // online only, ZERO network, so the footer's 5 change with no round-trip wait; it does
                    // not record the batch as shown / latch the nonce yet (that decision is made below).
                    // On a per-'+'-add recompute (not a refresh): forceNetwork = null keeps the
                    // pre-existing heuristic — the bounded network fetch still kicks in when the local
                    // pool alone can't fill the batch (recordShown stays false: no recording/latching).
                    val instant = computeSuggestions(
                        ids,
                        limit = SHEET_SUGGESTIONS,
                        seed = nonce,
                        forceNetwork = if (wasRefresh) false else null,
                        recordShown = false,
                    )
                    emit(instant)
                    // First batch is in hand — the footer can stop showing shimmer (an empty result while
                    // a refresh is still running keeps shimmer via isRefreshingSuggestions below).
                    _suggestionsLoaded.value = true

                    if (wasRefresh) {
                        // Escalate to the network top-up when the instant batch can't satisfy either
                        // surface: (a) the footer's head isn't 5 FRESH (never-shown) picks — an exhausted
                        // cache otherwise re-records and repeats the same batch forever (recycle stays the
                        // last-resort fallback inside computeSuggestions); or (b) the cached relatedness
                        // pool filled less than half the sheet, i.e. it's mostly quickPicks padding.
                        if (lastComputeFreshHeadCount < FOOTER_SUGGESTIONS ||
                            lastComputeRelatednessCount < SHEET_SUGGESTIONS / 2
                        ) {
                            // ── Phase B — BACKGROUND top-up: hit the existing bounded (≤3) network fetch
                            // and re-emit when it arrives. The spinner shows ONLY here.
                            _isRefreshingSuggestions.value = true
                            try {
                                val topped = computeSuggestions(
                                    ids,
                                    limit = SHEET_SUGGESTIONS,
                                    seed = nonce,
                                    forceNetwork = true,
                                    recordShown = true,
                                )
                                emit(topped)
                            } finally {
                                _isRefreshingSuggestions.value = false
                            }
                        } else {
                            // Cache filled the footer — the instant list is final. Record it as shown so
                            // infinite-no-repeat advances, and latch the nonce so a following per-'+'-add
                            // recompute stays stable (same-nonce → isRefresh=false).
                            recordBatchShown(instant.map { it.id }, nonce)
                        }
                    }
                }
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

    /** Ids surfaced by earlier suggestion batches this session — excluded on an EXPLICIT refresh so
     *  every tap yields fresh candidates. When the pool runs dry the engine EXPANDS it (new seeds,
     *  see step 6 of [computeSuggestions]) instead of recycling; this set is cleared only as the
     *  last-resort fallback when an expansion round adds nothing (offline / tiny corpus), so the
     *  section never goes empty. Doubles as the seed pool for suggestion-of-suggestion expansion.
     *  Only touched from the (serialized) suggestions flow. */
    private val shownSuggestionIds = mutableSetOf<String>()

    /** Seeds whose online-related fetch COMPLETED this session (even with an empty result), so seed
     *  rotation never re-spends network on them — over successive refreshes this walks through ALL
     *  playlist songs, then through previously shown suggestions. FAILED fetches (offline) are NOT
     *  recorded, so a later refresh retries them (offline → online recovery). Concurrent set because
     *  parallel fetch coroutines write it. */
    private val usedOnlineSeeds: MutableSet<String> = ConcurrentHashMap.newKeySet()

    /** Highest refresh nonce whose batch has been applied. Lets [computeSuggestions] tell an explicit
     *  refresh (nonce bumped → rotate to fresh candidates, record the new batch) apart from an id-set
     *  recompute (a song was added → keep the visible list stable minus the now-in-playlist songs).
     *  Starts at -1 so the very first compute (nonce 0) counts as a refresh and records its batch. */
    private var lastAppliedNonce = -1

    /** Size of the last compute's RELATEDNESS pool (candidates chosen from local+cache related songs,
     *  BEFORE the quickPicks backfill pads the batch). The two-phase flow reads this after the instant
     *  cache-only pass to decide if a background network top-up is needed: if the cached pool alone
     *  couldn't fill the footer's 5, quickPicks padding is standing in and Phase B fetches real ones.
     *  Only written from the (serialized) suggestions flow. */
    private var lastComputeRelatednessCount = 0

    /** How many of the last compute's HEAD picks (the footer's [FOOTER_SUGGESTIONS]) were FRESH —
     *  not in [shownSuggestionIds] at compute time (measured BEFORE the batch is recorded). The
     *  two-phase flow gates Phase B on this rather than the total: an exhausted cache can still fill
     *  the batch with already-shown songs, and without the freshness signal it would re-record and
     *  repeat the same batch forever. Only written from the (serialized) suggestions flow. */
    private var lastComputeFreshHeadCount = 0

    /** Online related-songs memo, one entry per seed id — recomputes never re-hit the network for a
     *  seed already fetched. Bounded, access-ordered LRU (64 seeds) so a long session hopping across
     *  playlists can't grow it without bound; evicting a seed only costs a potential re-fetch.
     *  FAILED fetches are NOT cached (nor recorded in [usedOnlineSeeds]) so a later explicit refresh
     *  retries them (offline → online recovery); successful-but-EMPTY fetches are not cached either
     *  but ARE in [usedOnlineSeeds] — the engine treats those as known-empty and never re-spends
     *  network on them. Synchronized wrapper because parallel fetch coroutines write it; NEVER
     *  iterate it directly — snapshot under `synchronized(onlineRelatedCache)` first. */
    private val onlineRelatedCache: MutableMap<String, List<SongItem>> =
        java.util.Collections.synchronizedMap(
            object : LinkedHashMap<String, List<SongItem>>(16, 0.75f, true) {
                override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, List<SongItem>>): Boolean {
                    val evict = size > 64
                    // Keep the invariant "in usedOnlineSeeds but NOT cached == fetch succeeded EMPTY":
                    // an evicted seed had results, so drop it from usedOnlineSeeds too — if rotation
                    // lands on it again it re-fetches instead of being mistaken for known-empty.
                    if (evict) usedOnlineSeeds.remove(eldest.key)
                    return evict
                }
            },
        )

    /** Ids added to the playlist through this ViewModel this session — extra exclusion guard while
     *  the Room id-set flow re-emits. ConcurrentHashMap-backed set: written from add paths, iterated
     *  by the engine — its weakly-consistent iterator never throws ConcurrentModificationException. */
    private val sessionAddedIds: MutableSet<String> = ConcurrentHashMap.newKeySet()

    /** Online suggestion ids already persisted by this ViewModel — bounds the DB insert to at most
     *  ONE call per id per session (insert() is IGNORE-on-conflict anyway, so this is belt-and-braces). */
    private val insertedOnlineIds = mutableSetOf<String>()

    /**
     * Suggestion engine — "the playlist's own content, ranked":
     *  1. SEEDS: up to 8 of the playlist's OWN songs, stride-sampled so they SPREAD across the whole
     *     playlist (never just the first song); the sample window rotates with each refresh nonce.
     *  2. LOCAL layer: database.relatedSongs(seed) for every sampled seed.
     *  3. ONLINE layer (YouTube's relatedness algorithm): next(seed).relatedEndpoint → related() for
     *     up to 3 of the sampled seeds, fetched in parallel and memoized per seed. Cached results are
     *     always blended in; the NETWORK is hit only on an explicit refresh or when the local pool
     *     can't fill [limit] — so per-'+'-add recomputes never touch the network.
     *  4. RANK by relatedness frequency (how many DISTINCT seeds a candidate is related to), with
     *     round-robin over the interleaved local/online seed lists as the tiebreak so no single seed
     *     or layer dominates the head of the list.
     *  5. ARTIST DIVERSITY: at most 2 songs per primary artist in the top list (overflow goes to the
     *     tail), so 5 footer suggestions are never one artist.
     *  6. EXCLUDES playlist songs and songs added this session always; previously shown batches are
     *     excluded ONLY on an explicit refresh — an id-set recompute after a '+' add keeps the
     *     visible list stable minus the just-added songs. When never-shown candidates can't fill a
     *     refresh batch, the pool EXPANDS (cache-wide blend, then up to the remaining per-tap fetch
     *     budget of new seeds: unused playlist songs first, then shown suggestions one hop deeper),
     *     so refreshes keep yielding NEW songs indefinitely; only a zero-yield expansion (offline /
     *     tiny corpus) falls back to recycling so the section never goes empty.
     *  7. BACKFILL from quickPicks (recently/most played) when relatedness alone can't fill [limit]
     *     — restores the "playlist content + recently played" blend, offline-safe.
     */
    private suspend fun computeSuggestions(
        songIds: List<String>,
        limit: Int,
        seed: Int = 0,
        // When non-null, OVERRIDES the network-permission heuristic: false = local DB + already-cached
        // online only (the instant Phase A, zero round-trips); true = allow the ≤3 related fetches
        // (the background Phase B top-up). null keeps today's per-add/first-load heuristic untouched.
        forceNetwork: Boolean? = null,
        // When false, the batch is NOT recorded as shown and the nonce is NOT latched — the two-phase
        // flow records the batch the user actually keeps (instant if it fills, else the topped-up one).
        recordShown: Boolean = true,
    ): List<Song> {
        if (songIds.isEmpty()) return emptyList()
        // Explicit refresh (nonce bumped) vs. id-set recompute (a song was added): only a refresh may
        // rotate the batch; per-add recomputes stay deterministic so the visible list is stable.
        val isRefresh = seed > lastAppliedNonce
        val excluded = songIds.toHashSet() + sessionAddedIds

        // 1. Stride-sampled seeds, rotated by the refresh nonce.
        val seedCount = minOf(8, songIds.size)
        val stride = maxOf(1, songIds.size / seedCount)
        val seedSongs = List(seedCount) { i -> songIds[(seed + i * stride).mod(songIds.size)] }
            .distinct()

        // 2. LOCAL layer first — it's free and decides whether the online layer needs the network.
        val localBySeed: List<Pair<String, List<Song>>> =
            seedSongs.map { sid -> sid to database.relatedSongs(sid).distinctBy { it.id } }
        val localPoolSize = localBySeed
            .asSequence()
            .flatMap { it.second }
            .map { it.id }
            .filter { it !in excluded }
            .distinct()
            .count()

        // 3. ONLINE layer: cached per-seed results are always blended in (free, and keeps per-add
        // recomputes deterministic), but the NETWORK is hit only on an explicit refresh or when the
        // local pool can't fill [limit] by itself — per-add recomputes are pure-local + cache.
        // Fetches run in parallel and fail soft (offline-safe).
        val allowNetwork = forceNetwork ?: (isRefresh || localPoolSize < limit)
        val onlineSeeds = seedSongs.take(3)
        // HARD per-tap network bound: at most 3 related-fetch attempts per compute, SHARED between
        // this base layer and the expansion pass in step 6 (cache hits are free and don't count).
        // Zero when the network is disallowed (Phase A / per-add) so step 6b never fetches either.
        var networkBudget = if (allowNetwork) 3 else 0
        if (allowNetwork) {
            // Count only seeds that will REALLY hit the network: not cached AND not known-empty
            // (in usedOnlineSeeds without a cache entry = an earlier fetch SUCCEEDED with zero
            // results — re-fetching those would burn the tap budget for nothing).
            networkBudget = (
                networkBudget - onlineSeeds.count {
                    !onlineRelatedCache.containsKey(it) && it !in usedOnlineSeeds
                }
            ).coerceAtLeast(0)
        }
        val onlineBySeed: List<Pair<String, List<SongItem>>> = coroutineScope {
            onlineSeeds.map { sid ->
                async {
                    val cached = onlineRelatedCache[sid]
                    val items = when {
                        cached != null -> cached
                        // Known-empty: an earlier fetch this session SUCCEEDED with no results
                        // (empty results aren't cached) — don't re-spend network on it.
                        sid in usedOnlineSeeds -> emptyList()
                        allowNetwork ->
                            runCatching { onlineRelatedItems(sid) }
                                .onSuccess { usedOnlineSeeds += sid }
                                .getOrDefault(emptyList())
                                .also { if (it.isNotEmpty()) onlineRelatedCache[sid] = it }
                        else -> emptyList()
                    }
                    sid to items
                }
            }.awaitAll()
        }

        // 4. Accumulate candidates round-robin (position j of every seed list, then j+1, …); count
        // how many DISTINCT seeds each candidate is related to.
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

        // ONE round-robin over ALL seed lists, local and online lanes interleaved
        // (local[0], online[0], local[1], online[1], …) so the online layer genuinely blends into
        // the head of the ranking instead of trailing after every local candidate.
        val lanes = ArrayList<Pair<String, List<Any>>>()
        for (i in 0 until maxOf(localBySeed.size, onlineBySeed.size)) {
            localBySeed.getOrNull(i)?.let { (sid, list) -> lanes += sid to list }
            onlineBySeed.getOrNull(i)?.let { (sid, list) -> lanes += sid to list }
        }
        val maxLane = lanes.maxOfOrNull { it.second.size } ?: 0
        for (j in 0 until maxLane) {
            lanes.forEach { (sid, list) ->
                when (val candidate = list.getOrNull(j)) {
                    is Song -> offerLocal(sid, candidate)
                    is SongItem -> offerOnline(sid, candidate)
                }
            }
        }

        fun rank(): List<String> {
            val firstSeen = orderIds.withIndex().associate { (i, id) -> id to i }
            return orderIds.sortedWith(
                compareByDescending<String> { seedHits[it]?.size ?: 0 }
                    .thenBy { firstSeen[it] ?: Int.MAX_VALUE },
            )
        }
        var ranked = rank()

        // 6. Cross-refresh memory — applied ONLY on an explicit refresh: prefer never-shown
        // candidates. When they can't fill a batch, EXPAND the pool instead of recycling:
        //   6a. FREE: blend in every seed already fetched this session (pure cache, zero network)
        //       that this tap's stride sample missed.
        //   6b. NETWORK (bounded by the shared 3-fetch-per-tap budget): fetch related for seeds
        //       never used this session — the playlist's own songs first (successive taps walk the
        //       WHOLE playlist because used seeds are skipped), then previously SHOWN suggestions
        //       one hop deeper (suggestion-of-suggestion — still content-derived; depth needs no
        //       explicit bookkeeping since an id only becomes a seed after it has been shown).
        //   6c. Only if expansion adds ZERO never-shown candidates (offline / tiny corpus) fall
        //       back to recycling: clear the memory so the section never goes empty — repeats are
        //       acceptable there.
        // Same-nonce recomputes (a song was added) skip all of this — the current batch IS in
        // shownSuggestionIds, so excluding it would replace the whole visible list on every '+' add.
        val ordered = if (isRefresh) {
            var fresh = ranked.filter { it !in shownSuggestionIds }
            if (fresh.size < limit) {
                // 6a. Cache-wide blend — free candidates from seeds fetched on earlier taps.
                // Snapshot under the wrapper's lock: the LRU map is a synchronizedMap and parallel
                // fetch coroutines mutate it, so direct iteration could throw CME.
                val cacheSnapshot = synchronized(onlineRelatedCache) {
                    onlineRelatedCache.entries.map { it.key to it.value }
                }
                cacheSnapshot.forEach { (sid, items) ->
                    items.forEach { offerOnline(sid, it) }
                }
                // 6b. Bounded network expansion with never-used seeds.
                if (networkBudget > 0) {
                    val expansionSeeds =
                        (songIds.asSequence() +
                            shownSuggestionIds.asSequence().filter { it !in excluded })
                            .filter { it !in usedOnlineSeeds && !onlineRelatedCache.containsKey(it) }
                            .distinct()
                            .take(networkBudget)
                            .toList()
                    val fetched: List<Pair<String, List<SongItem>>> = coroutineScope {
                        expansionSeeds.map { sid ->
                            async {
                                val items = runCatching { onlineRelatedItems(sid) }
                                    .onSuccess { usedOnlineSeeds += sid }
                                    .getOrDefault(emptyList())
                                if (items.isNotEmpty()) onlineRelatedCache[sid] = items
                                sid to items
                            }
                        }.awaitAll()
                    }
                    // Round-robin offer so no single expansion seed dominates the tiebreak order.
                    val maxRow = fetched.maxOfOrNull { it.second.size } ?: 0
                    for (j in 0 until maxRow) {
                        fetched.forEach { (sid, list) ->
                            list.getOrNull(j)?.let { offerOnline(sid, it) }
                        }
                    }
                }
                ranked = rank()
                val expandedFresh = ranked.filter { it !in shownSuggestionIds }
                if (expandedFresh.size > fresh.size) {
                    fresh = expandedFresh
                } else if (allowNetwork) {
                    // 6c. Network expansion yielded nothing new — recycle fallback so the section never
                    // goes empty. SKIPPED on the cache-only instant pass (Phase A, allowNetwork=false):
                    // the following background top-up may still find fresh songs, so don't wipe the
                    // no-repeat memory prematurely.
                    shownSuggestionIds.clear()
                }
            }
            val freshSet = fresh.toHashSet()
            fresh + ranked.filter { it !in freshSet }
        } else {
            ranked
        }

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
        // Remember how many came from RELATEDNESS (before the quickPicks pad) — the two-phase flow
        // uses this to decide whether the instant cache-only batch needs a network top-up.
        lastComputeRelatednessCount = chosen.size

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

        // 7b. Library backfill — LAST-resort local content so the instant Phase A pass (zero network)
        // NEVER paints blank on a freshly-imported/light-usage library, where relatedSongs is empty and
        // quickPicks is tiny. Newest-added library songs, offline-safe, excluding playlist + session
        // songs. Phase B still tops up with real online relatedness in the background.
        if (chosen.size < limit) {
            val chosenSet = chosen.toHashSet()
            database.songsByCreateDateAsc().first()
                .asReversed()
                .asSequence()
                .filter { it.id !in excluded && it.id !in chosenSet }
                .take(limit - chosen.size)
                .forEach { lib ->
                    localById.putIfAbsent(lib.id, lib)
                    chosen += lib.id
                }
        }

        val result = chosen.mapNotNull { id ->
            localById[id] ?: onlineById[id]?.let { resolveOnlineSong(it) }
        }
        // Freshness of the HEAD (the footer's picks), measured BEFORE any recording below mutates
        // shownSuggestionIds — the two-phase flow reads this to decide if Phase B must escalate.
        lastComputeFreshHeadCount =
            result.take(FOOTER_SUGGESTIONS).count { it.id !in shownSuggestionIds }
        // Record the batch as "shown" (and latch the nonce) ONLY for explicit refreshes AND only when
        // this compute is the one the user keeps (recordShown) — same-nonce recomputes must not feed
        // the exclusion memory, and the instant Phase A defers recording to the flow so a following
        // network top-up records the batch actually shown.
        if (isRefresh && recordShown) {
            recordBatchShown(result.map { it.id }, seed)
        }
        return result
    }

    /** Record a suggestion batch as shown (feeding infinite-no-repeat) and latch its nonce. Capped at
     *  the ~1000 most-recent shown ids (LinkedHashSet insertion order ≈ recency; order only matters for
     *  recycling): trimmed old ids simply become suggestable again, which the recycle fallback tolerates
     *  by design. Only called from the (serialized) suggestions flow. */
    private fun recordBatchShown(resultIds: List<String>, nonce: Int) {
        shownSuggestionIds += resultIds
        if (shownSuggestionIds.size > 1000) {
            val iterator = shownSuggestionIds.iterator()
            repeat(shownSuggestionIds.size - 1000) {
                iterator.next()
                iterator.remove()
            }
        }
        lastAppliedNonce = nonce
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
