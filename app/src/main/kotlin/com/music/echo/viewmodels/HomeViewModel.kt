

package iad1tya.echo.music.viewmodels

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.music.innertube.YouTube
import com.music.innertube.models.AlbumItem
import com.music.innertube.models.Artist
import com.music.innertube.models.PlaylistItem
import com.music.innertube.models.SongItem
import kotlinx.coroutines.flow.combine
import com.music.innertube.models.WatchEndpoint
import com.music.innertube.models.BrowseEndpoint
import com.music.innertube.models.YTItem
import com.music.innertube.models.filterExplicit
import com.music.innertube.models.filterVideoSongs
import com.music.innertube.models.filterYoutubeShorts
import com.music.innertube.pages.ExplorePage
import com.music.innertube.pages.HomePage
import com.music.innertube.utils.completed
import iad1tya.echo.music.constants.HideExplicitKey
import iad1tya.echo.music.constants.HideVideoSongsKey
import iad1tya.echo.music.constants.HideYoutubeShortsKey
import iad1tya.echo.music.constants.InnerTubeCookieKey
import iad1tya.echo.music.constants.OnboardingArtistsDoneKey
import iad1tya.echo.music.constants.QuickPicks
import iad1tya.echo.music.constants.QuickPicksKey
import iad1tya.echo.music.constants.ShowSpeedDialKey
import iad1tya.echo.music.db.MusicDatabase
import iad1tya.echo.music.db.entities.Album
import iad1tya.echo.music.db.entities.LocalItem
import iad1tya.echo.music.db.entities.Playlist
import iad1tya.echo.music.db.entities.Song
import iad1tya.echo.music.db.entities.SpeedDialItem
import iad1tya.echo.music.extensions.filterVideoSongs
import iad1tya.echo.music.extensions.toEnum
import iad1tya.echo.music.models.GenreMix
import iad1tya.echo.music.models.SimilarRecommendation
import iad1tya.echo.music.models.toMediaMetadata
import iad1tya.echo.music.utils.SyncUtils
import iad1tya.echo.music.utils.dataStore
import iad1tya.echo.music.utils.get
import iad1tya.echo.music.utils.reportException
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import kotlin.random.Random

data class DailyDiscoverItem(
    val seed: Song,
    val recommendation: YTItem,
    val relatedEndpoint: BrowseEndpoint?
)

/** One "Mix diario N" shelf: a single seed song and its taste-picked related recommendations. */
data class DailyMix(
    val seed: Song,
    val items: List<DailyDiscoverItem>
)

/** "Mix de la mañana / tarde / noche": bucket 0 = morning (5-11h), 1 = afternoon (12-18h), 2 = night. */
data class TimeOfDayMix(
    val bucket: Int,
    val songs: List<Song>
)

data class CommunityPlaylistItem(
    val playlist: PlaylistItem,
    val songs: List<SongItem>
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    @ApplicationContext val context: Context,
    val database: MusicDatabase,
    val syncUtils: SyncUtils,
    podcastRepository: iad1tya.echo.music.podcast.PodcastRepository,
    private val dislikeStore: iad1tya.echo.music.dislike.DislikeStore,
) : ViewModel() {
    /** Saved/pinned podcasts, surfaced on the home for quick access. */
    val pinnedPodcasts: kotlinx.coroutines.flow.Flow<List<iad1tya.echo.music.podcast.PodcastShow>> =
        podcastRepository.pinnedShows
    val isRefreshing = MutableStateFlow(false)
    val isLoading = MutableStateFlow(false)
    val isRandomizing = MutableStateFlow(false)

    private val quickPicksEnum = context.dataStore.data.map {
        it[QuickPicksKey].toEnum(QuickPicks.QUICK_PICKS)
    }.distinctUntilChanged()

    val quickPicks = MutableStateFlow<List<Song>?>(null)
    // "Mix diario N" (up to 3): the old single Daily Discover carousel split into one shelf per seed,
    // each keeping its own "Porque escuchas X" line. Same network calls (next+related per seed).
    val dailyMixes = MutableStateFlow<List<DailyMix>?>(null)
    // "Reproducido recientemente": strict chronological history (most recent listen first).
    val recentlyPlayed = MutableStateFlow<List<Song>?>(null)
    // "Mix de la mañana/tarde/noche": taste pool reshuffled with a time-bucket-stable seed. No network.
    val timeOfDayMix = MutableStateFlow<TimeOfDayMix?>(null)
    val forgottenFavorites = MutableStateFlow<List<Song>?>(null)
    val keepListening = MutableStateFlow<List<LocalItem>?>(null)
    val similarRecommendations = MutableStateFlow<List<SimilarRecommendation>?>(null)
    val accountPlaylists = MutableStateFlow<List<PlaylistItem>?>(null)
    val homePage = MutableStateFlow<HomePage?>(null)
    val explorePage = MutableStateFlow<ExplorePage?>(null)
    val communityPlaylists = MutableStateFlow<List<CommunityPlaylistItem>?>(null)
    // "Novedades de tus artistas": new releases from followed/most-played artists, surfaced from the
    // already-built weekly Release Radar store (windowed + deduped), rendered as an album shelf on Home.
    val newFromArtists = MutableStateFlow<List<AlbumItem>?>(null)
    // "Tu mix de [Género]": your own songs from your top genre (via on-device GenreCache), ranked by taste.
    val genreMix = MutableStateFlow<GenreMix?>(null)
    val selectedChip = MutableStateFlow<HomePage.Chip?>(null)
    private val previousHomePage = MutableStateFlow<HomePage?>(null)

    // On-device taste model (see AffinityEngine). Rebuilt once per load/refresh and reused to RANK every
    // shelf by how much you'd actually like each candidate — instead of the old plain .shuffled().
    @Volatile
    private var tasteProfile: iad1tya.echo.music.reco.TasteProfile? = null

    /** Rebuild the taste profile from the latest listening history + dislikes. Cheap, fully on-device. */
    private suspend fun refreshTasteProfile() {
        val events = runCatching { database.recentEventsWithSong(3000).first() }.getOrDefault(emptyList())
        val library = runCatching {
            database.librarySongsForTaste(iad1tya.echo.music.reco.AffinityEngine.MAX_LIBRARY).first()
        }.getOrDefault(emptyList())
        val followed = runCatching {
            database.artistsBookmarkedByCreateDateAsc().first().map { it.artist }
        }.getOrDefault(emptyList())
        val disliked = runCatching { dislikeStore.snapshot() }
            .getOrDefault(iad1tya.echo.music.dislike.DislikeStore.Disliked())
        runCatching {
            val genres = iad1tya.echo.music.reco.GenreCache.snapshot(context)
            val onboarding = iad1tya.echo.music.reco.OnboardingGenres.itunesGenres(context)
            // SECONDARY, opt-in Last.fm taste seed. Gated: only when the toggle is ON AND a Last.fm username
            // exists — otherwise emptyMap => zero behavior change. Reads the daily-worker cache only (no network).
            val lastfm = if (
                context.dataStore.get(iad1tya.echo.music.constants.UseLastFmTasteKey, false) &&
                context.dataStore.get(iad1tya.echo.music.constants.LastFMUsernameKey, "").isNotBlank()
            ) iad1tya.echo.music.reco.LastFmTasteSource.cached(context) else emptyMap()
            // buildProfile is CPU-bound (genre/lane scans over events + library) — run it on Default (not the
            // IO dispatcher this is called on) to free IO threads, matching MusicService.tasteProfile().
            tasteProfile = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                iad1tya.echo.music.reco.AffinityEngine.buildProfile(events, disliked, artistGenres = genres, onboardingGenres = onboarding, librarySongs = library, followedArtists = followed, externalArtistWeights = lastfm)
            }
        }
        // Learn the real genres of your FOLLOWED artists too (not just played ones), so a followed artist's
        // genre affinity works even before you play them — WiFi only, same as the events enrichment below.
        if (followed.isNotEmpty()) {
            viewModelScope.launch(Dispatchers.IO) {
                runCatching {
                    iad1tya.echo.music.reco.GenreCache.enrich(context, followed.map { it.name }, onlyWifi = true)
                }
            }
        }
        // Learn the real genres of your most-heard artists for next time — WiFi only (your choice).
        if (events.isNotEmpty()) {
            viewModelScope.launch(Dispatchers.IO) {
                runCatching {
                    val names = events.asSequence().take(400)
                        .flatMap { it.song.artists.asSequence().map { a -> a.name } }.toList()
                    iad1tya.echo.music.reco.GenreCache.enrich(context, names, onlyWifi = true)
                }
            }
        }
        // Phase B #5 — learn the SONG-similarity graph of your most-recent liked songs (their YouTube-related
        // ids) so radio can prefer candidates co-related to MULTIPLE things you liked. WiFi only, best-effort,
        // bounded (~20), and only alongside the GenreCache enrich above — no new work on the playback path.
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                // The 50 most-recent liked songs = the graph's bounded anchor/keep set (enrich prunes anything
                // no longer here, so the graph can't grow forever). Each call fetches only a few uncached ones.
                val likedIds = database.likedSongs(
                    iad1tya.echo.music.constants.SongSortType.CREATE_DATE,
                    descending = true,
                ).first().take(50).map { it.id }
                if (likedIds.isNotEmpty()) {
                    iad1tya.echo.music.reco.SongGraphCache.enrich(context, likedIds, onlyWifi = true)
                }
            }
        }
    }

    /** Rank songs by taste, with a little jitter so refreshes vary instead of being identical. */
    private fun List<Song>.rankedByTaste(): List<Song> {
        val p = tasteProfile ?: return shuffled()
        val rnd = java.util.Random()
        // Precompute the key ONCE per item — rnd inside the comparator would be inconsistent between
        // comparisons and crash TimSort ("Comparison method violates its general contract").
        val scored = map { it to (p.score(it) + rnd.nextDouble() * 0.08) }.sortedByDescending { it.second }
        // Diversity re-rank (MMR-lite): keep taste order but greedily spread artists so a shelf doesn't tunnel
        // into a single artist/genre (filter-bubble). Each already-placed artist adds a penalty to its next
        // song. O(n^2) — only worth it for shelf-sized lists; large lists keep the plain taste sort.
        if (scored.size !in 2..120) return scored.map { it.first }
        val diversityPenalty = 0.4
        val artistSeen = HashMap<String, Int>()
        val remaining = scored.toMutableList()
        val out = ArrayList<Song>(scored.size)
        while (remaining.isNotEmpty()) {
            var bestIdx = 0
            var bestAdj = Double.NEGATIVE_INFINITY
            for (i in remaining.indices) {
                val (song, base) = remaining[i]
                val key = song.artists.firstOrNull()?.id ?: ""
                // Don't penalize artist-less songs (empty id) against each other — they aren't the same artist.
                val adj = if (key.isEmpty()) base else base - (artistSeen[key] ?: 0) * diversityPenalty
                if (adj > bestAdj) { bestAdj = adj; bestIdx = i }
            }
            val chosen = remaining.removeAt(bestIdx)
            out.add(chosen.first)
            val k = chosen.first.artists.firstOrNull()?.id ?: ""
            if (k.isNotEmpty()) artistSeen[k] = (artistSeen[k] ?: 0) + 1
        }
        return out
    }

    private fun tasteScoreYt(item: com.music.innertube.models.YTItem): Double {
        val p = tasteProfile ?: return 0.0
        return when (item) {
            is com.music.innertube.models.SongItem -> p.scoreNames(item.artists.map { it.name }, item.title)
            is com.music.innertube.models.AlbumItem -> p.scoreNames(item.artists?.map { it.name } ?: emptyList(), item.title)
            else -> 0.0
        }
    }

    /** Order a YouTube item list by taste (songs/albums scored; artists/playlists neutral) with a little
     *  jitter — replaces the old plain .shuffled() so the best-fit songs/albums surface first in each row. */
    private fun List<com.music.innertube.models.YTItem>.rankedByTasteYt(): List<com.music.innertube.models.YTItem> {
        if (tasteProfile == null) return shuffled()
        val rnd = java.util.Random()
        return map { it to (tasteScoreYt(it) + rnd.nextDouble() * 0.08) }
            .sortedByDescending { it.second }
            .map { it.first }
    }

    val allLocalItems = MutableStateFlow<List<LocalItem>>(emptyList())
    val allYtItems = MutableStateFlow<List<YTItem>>(emptyList())

    /**
     * Playlists the user pinned to Home ("Fijar playlist al inicio").
     *
     * Previously this shelf was a song "speed dial" that DROPPED pinned playlists
     * (`as? SongItem`) and back-filled from Keep Listening / Quick Picks — so pinning a
     * playlist never appeared. The Home slot now shows exactly the playlists the user pinned,
     * in pin order, matching the playlist widget which already reads the same table.
     */
    val speedDialItems: StateFlow<List<YTItem>> =
        database.speedDialDao.getAll()
            .map { pinned ->
                pinned
                    .asSequence()
                    .filter { it.type == "PLAYLIST" || it.type == "LOCAL_PLAYLIST" }
                    .sortedBy { it.createDate }
                    .map { item ->
                        PlaylistItem(
                            id = item.id,
                            title = item.title,
                            author = item.subtitle?.let { Artist(name = it, id = null) },
                            songCountText = null,
                            thumbnail = item.thumbnailUrl,
                            playEndpoint = null,
                            shuffleEndpoint = null,
                            radioEndpoint = null,
                        )
                    }
                    .toList()
            }
            .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    // ---- Cross-section dedup (top of the home) -------------------------------------------------
    // When the pinned-playlists shelf is visible, avoid repeating the same covers in Quick Picks /
    // Keep Listening. Ids are playlist browse/local ids — song shelves rarely collide, but the
    // filter is cheap and keeps the contract if a future pin type shares an id space.

    private val showSpeedDialFlow = context.dataStore.data
        // ON by default: pinning a playlist must surface it on Home without a hidden settings toggle.
        .map { it[ShowSpeedDialKey] ?: true }
        .distinctUntilChanged()

    /** Ids on SpeedDial's first pager page (~the tiles actually visible without swiping). Empty when
     *  the SpeedDial shelf is disabled, so nothing is deduped against a hidden section. */
    private val speedDialVisibleIds: StateFlow<Set<String>> =
        combine(speedDialItems, showSpeedDialFlow) { items, shown ->
            if (!shown) emptySet()
            else items.take(SPEED_DIAL_VISIBLE_TILES).map { it.id }.toSet()
        }.stateIn(viewModelScope, SharingStarted.Lazily, emptySet())

    /** QuickPicks as displayed: excludes songs already visible on SpeedDial. Never empties a populated
     *  shelf — if dedup would remove everything, the original list is shown instead. */
    val quickPicksDisplay: StateFlow<List<Song>?> =
        combine(quickPicks, speedDialVisibleIds) { picks, sdIds ->
            if (picks == null || sdIds.isEmpty()) picks
            else picks.filterNot { it.id in sdIds }.ifEmpty { picks }
        }.stateIn(viewModelScope, SharingStarted.Lazily, null)

    /** KeepListening as displayed: excludes SpeedDial's visible tiles ∪ the QuickPicks head. */
    val keepListeningDisplay: StateFlow<List<LocalItem>?> =
        combine(keepListening, speedDialVisibleIds, quickPicksDisplay) { keep, sdIds, picks ->
            if (keep == null) null
            else {
                val shownAbove = sdIds + picks.orEmpty().take(10).map { it.id }
                if (shownAbove.isEmpty()) keep
                else keep.filterNot { it.id in shownAbove }.ifEmpty { keep }
            }
        }.stateIn(viewModelScope, SharingStarted.Lazily, null)

    // ---- "Recomendado para ti (IA)" ------------------------------------------------------------
    // The ONE persistent AI-recommendations playlist (fixed id), rebuilt daily by the opt-in
    // AutoRecoPlaylistWorker. Reactive DB flows only — zero network from the Home path; the section
    // simply doesn't exist until the worker has produced a non-empty playlist.

    /** The playlist entity (carries name + lastUpdateTime for the "Actualizado" header label). */
    val aiRecommendedPlaylist: StateFlow<Playlist?> =
        database.playlist(iad1tya.echo.music.reco.AutoRecoPlaylistWorker.PLAYLIST_ID)
            .stateIn(viewModelScope, SharingStarted.Lazily, null)

    /** Its songs in playlist order (the DAO query already sorts by position). */
    val aiRecommendedSongs: StateFlow<List<Song>?> =
        database.playlistSongs(iad1tya.echo.music.reco.AutoRecoPlaylistWorker.PLAYLIST_ID)
            .map { playlistSongs -> playlistSongs.map { it.song } }
            .stateIn(viewModelScope, SharingStarted.Lazily, null)

    /** TimeOfDayMix as displayed: its pool IS quickPicks + forgottenFavorites + keepListening, and the
     *  shelf renders right under QuickPicks, so the raw mix duplicated the first screenful (worst in
     *  perf mode, where it's 1 of only 4 shelves). Same pattern + ifEmpty guard as above: excludes
     *  SpeedDial's visible tiles ∪ the QuickPicks head; total overlap shows the original mix instead
     *  of emptying the shelf. */
    val timeOfDayMixDisplay: StateFlow<TimeOfDayMix?> =
        combine(timeOfDayMix, speedDialVisibleIds, quickPicksDisplay) { mix, sdIds, picks ->
            if (mix == null) null
            else {
                val shownAbove = sdIds + picks.orEmpty().take(10).map { it.id }
                if (shownAbove.isEmpty()) mix
                else mix.copy(songs = mix.songs.filterNot { it.id in shownAbove }.ifEmpty { mix.songs })
            }
        }.stateIn(viewModelScope, SharingStarted.Lazily, null)

    suspend fun getRandomItem(): YTItem? {
        try {
            isRandomizing.value = true
            
            kotlinx.coroutines.delay(1000)

            val userSongs = mutableListOf<YTItem>()
            val otherSources = mutableListOf<YTItem>()

            quickPicks.value?.let { songs ->
                userSongs.addAll(songs.map { song ->
                    SongItem(
                        id = song.id,
                        title = song.title,
                        artists = song.artists.map { Artist(name = it.name, id = it.id) },
                        thumbnail = song.thumbnailUrl ?: "",
                        explicit = false
                    )
                })
            }

            keepListening.value?.let { items ->
                items.forEach { item ->
                    when (item) {
                        is Song -> userSongs.add(SongItem(
                            id = item.id,
                            title = item.title,
                            artists = item.artists.map { Artist(name = it.name, id = it.id) },
                            thumbnail = item.thumbnailUrl ?: "",
                            explicit = false
                        ))
                        is Album -> otherSources.add(AlbumItem(
                            browseId = item.id,
                            playlistId = item.album.playlistId ?: "",
                            title = item.title,
                            artists = item.artists.map { Artist(name = it.name, id = it.id) },
                            year = item.album.year,
                            thumbnail = item.thumbnailUrl ?: ""
                        ))
                        else -> {}
                    }
                }
            }

            otherSources.addAll(allYtItems.value)

            
            val item = if (userSongs.isNotEmpty() && (otherSources.isEmpty() || Random.nextFloat() < 0.8f)) {
                userSongs.distinctBy { it.id }.shuffled().firstOrNull()
            } else {
                otherSources.distinctBy { it.id }.shuffled().firstOrNull()
            } ?: userSongs.firstOrNull() ?: otherSources.firstOrNull()

            return item
        } finally {
            isRandomizing.value = false
        }
    }

    val accountName = MutableStateFlow("Guest")
    val accountImageUrl = MutableStateFlow<String?>(null)

    fun togglePin(item: YTItem) {
        viewModelScope.launch(Dispatchers.IO) {
            // Home pins are playlists only — songs/albums/artists are ignored.
            if (item !is PlaylistItem) return@launch
            val speedDialItem = SpeedDialItem.fromYTItem(item)
            val isPinned = database.speedDialDao.isPinned(speedDialItem.id).first()
            if (isPinned) {
                database.speedDialDao.delete(speedDialItem.id)
            } else {
                database.speedDialDao.insert(speedDialItem)
            }
        }
    }

    /**
     * Pinned playlists (Speed Dial + the playlist widget, which observes the same table) snapshot a
     * SINGLE thumbnailUrl at pin time and never refresh it — a playlist pinned while empty, or whose
     * cover later changed or died, keeps a stale/blank tile forever. On every Home load, re-align
     * each pinned playlist's stored URL with the playlist's LIVE cover from the DB (custom cover >
     * first song cover). DB-only, no network; REPLACE insert keeps id/createDate so tile order never
     * moves. Pins of online playlists that were never saved locally have no DB row to read — their
     * URL can only rot with YouTube; refreshing those would need a network fetch (deliberately
     * skipped here, the mosaic fallback in PlaylistThumbnail covers the Library surfaces).
     */
    private fun refreshSpeedDialThumbnails() {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                database.speedDialDao.getAll().first()
                    .filter { it.type == "PLAYLIST" || it.type == "LOCAL_PLAYLIST" }
                    .forEach { item ->
                        val live = database.playlist(item.id).first()
                            ?: database.playlistByBrowseId(item.id).first()
                            ?: return@forEach
                        // Null fresh cover (empty playlist, no songs yet) keeps the stored URL: a
                        // possibly-alive snapshot beats wiping the tile.
                        val freshUrl = live.thumbnails.firstOrNull() ?: return@forEach
                        if (freshUrl != item.thumbnailUrl) {
                            database.speedDialDao.insert(item.copy(thumbnailUrl = freshUrl))
                        }
                    }
            }.onFailure { reportException(it) }
        }
    }
    
    private var lastProcessedCookie: String? = null
    
    private var isProcessingAccountData = false

    /**
     * "Mix diario 1..3" — the old single Daily Discover carousel split into up to 3 per-seed mixes.
     * Same network shape as before (one next+related fetch per seed, now 3 seeds instead of 5); each
     * mix takes its seed's top taste-ranked related songs instead of collapsing every seed into one row.
     */
    private suspend fun getDailyMixes() {
        val hideVideoSongs = context.dataStore.get(HideVideoSongsKey, false)
        val likedSongs = database.likedSongsByCreateDateAsc().first()
        // Cold-start: a heavy listener who never taps "like" would otherwise get EMPTY daily mixes.
        // Fall back to their most-played songs (all-time) as seeds so discovery still works from real habits.
        val seedPool = likedSongs.ifEmpty {
            runCatching { database.mostPlayedSongs(0L, limit = 20).first() }.getOrDefault(emptyList())
        }
        if (seedPool.isEmpty()) return

        val seeds = seedPool.distinctBy { it.id }.rankedByTaste().take(3)

        val mixes = java.util.Collections.synchronizedList(mutableListOf<DailyMix>())

        kotlinx.coroutines.coroutineScope {
            seeds.map { seed ->
                launch(Dispatchers.IO) {
                    val endpoint = YouTube.next(WatchEndpoint(videoId = seed.id)).getOrNull()?.relatedEndpoint
                    if (endpoint != null) {
                        YouTube.related(endpoint).onSuccess { page ->
                            val recommendations = page.songs
                                .filter { item ->
                                    if (hideVideoSongs && item.isVideoSong) return@filter false
                                    if (item.explicit) return@filter false
                                    true
                                }

                            // Keep the related songs that best fit your taste (with a little jitter for
                            // variety). Precompute the key once per item (rnd inside the comparator
                            // would crash TimSort).
                            val rndPick = java.util.Random()
                            val picks = recommendations
                                .filter { it.id != seed.id }
                                .distinctBy { it.id }
                                .map { it to (tasteScoreYt(it) + rndPick.nextDouble() * 0.15) }
                                .sortedByDescending { it.second }
                                .take(5)
                                .map { DailyDiscoverItem(seed = seed, recommendation = it.first, relatedEndpoint = endpoint) }

                            if (picks.isNotEmpty()) {
                                mixes.add(DailyMix(seed = seed, items = picks))
                            }
                        }
                    }
                }
            }.forEach { it.join() }
        }

        // Keep the taste-ranked seed order, and dedup ACROSS mixes so the same recommendation never
        // shows in two "Mix diario" shelves. Never wipe a populated shelf on a transient empty result.
        val seen = HashSet<String>()
        val ordered = seeds
            .mapNotNull { s -> mixes.firstOrNull { it.seed.id == s.id } }
            .mapNotNull { mix ->
                val items = mix.items.filter { seen.add(it.recommendation.id) }
                if (items.isEmpty()) null else mix.copy(items = items)
            }
        if (ordered.isNotEmpty() || dailyMixes.value == null) dailyMixes.value = ordered
    }

    private suspend fun getQuickPicks() {
        val hideVideoSongs = context.dataStore.get(HideVideoSongsKey, false)
        val hideExplicit = context.dataStore.get(HideExplicitKey, false)
        when (quickPicksEnum.first()) {
            QuickPicks.QUICK_PICKS -> {
                val relatedSongs = database.quickPicks().first().filterVideoSongs(hideVideoSongs)
                val forgotten = database.forgottenFavorites().first().filterVideoSongs(hideVideoSongs).take(8)

                // Paint quick picks from LOCAL data immediately so the home appears fast — don't block the
                // first paint on a YouTube "related" network call. Guard against blanking a populated
                // shelf on a transient empty read (the "home went empty" bug); empty only on first load.
                // Day-stable rotate over a wider taste pool so "Para ti" changes across days even when
                // the underlying history barely moved (was: same ranked top-20 for days/weeks).
                val daySeed = LocalDate.now().toEpochDay()
                val localPool = (relatedSongs + forgotten).distinctBy { it.id }.rankedByTaste().take(48)
                val localQuick = if (localPool.size <= 20) {
                    localPool
                } else {
                    localPool.shuffled(Random(daySeed)).take(20)
                }
                if (localQuick.isNotEmpty() || quickPicks.value == null) quickPicks.value = localQuick

                // Enrich with YouTube related songs. IMPORTANT: persist + resolve SongItems — previously
                // we only kept related ids that were ALREADY in the local DB, so the enrich path was a
                // silent no-op for discovery and "Para ti" froze on the same local top forever.
                val recentSong = database.lastEvent().first()?.song
                val seedCandidates = (
                    listOfNotNull(recentSong) + relatedSongs.take(12) + forgotten
                    ).distinctBy { it.id }
                if (seedCandidates.isNotEmpty()) {
                    viewModelScope.launch(Dispatchers.IO) {
                        val seeds = seedCandidates.shuffled(Random(daySeed)).take(3)
                        val ytSimilarSongs = ArrayList<Song>(24)
                        val seen = HashSet<String>()
                        for (seed in seeds) {
                            val endpoint = YouTube.next(WatchEndpoint(videoId = seed.id))
                                .getOrNull()?.relatedEndpoint ?: continue
                            val page = YouTube.related(endpoint).getOrNull() ?: continue
                            for (ytSong in page.songs.take(10)) {
                                if (!seen.add(ytSong.id)) continue
                                if (hideExplicit && ytSong.explicit) continue
                                if (hideVideoSongs && ytSong.isVideoSong) continue
                                // IGNORE-on-conflict insert: never clobbers liked/library flags.
                                runCatching { database.insert(ytSong.toMediaMetadata()) }
                                val resolved = database.song(ytSong.id).first() ?: continue
                                if (hideVideoSongs && resolved.song.isVideo) continue
                                ytSimilarSongs += resolved
                                if (ytSimilarSongs.size >= 18) break
                            }
                            if (ytSimilarSongs.size >= 18) break
                        }
                        if (ytSimilarSongs.isEmpty()) return@launch
                        val combinedPool = (relatedSongs + forgotten + ytSimilarSongs)
                            .distinctBy { it.id }.rankedByTaste().take(48)
                        val combined = if (combinedPool.size <= 20) {
                            combinedPool
                        } else {
                            combinedPool.shuffled(Random(daySeed * 31L + 17L)).take(20)
                        }
                        if (combined.isNotEmpty()) quickPicks.value = combined
                    }
                }
            }
            QuickPicks.LAST_LISTEN -> {
                val song = database.lastEvent().first()?.song
                if (song != null && database.hasRelatedSongs(song.id)) {
                    val daySeed = LocalDate.now().toEpochDay()
                    quickPicks.value = database.getRelatedSongs(song.id).first()
                        .filterVideoSongs(hideVideoSongs)
                        .shuffled(Random(daySeed))
                        .take(20)
                }
            }
        }
    }

    private suspend fun getCommunityPlaylists() {
        val fromTimeStamp = System.currentTimeMillis() - 86400000L * 7 * 4
        // Seed from BOTH most-played AND followed/synced artists, so artists brought in by a YouTube
        // Music / Spotify sync (which have no play history yet) also shape the home — not only the few
        // artists picked at onboarding.
        val artistSeeds = (
            database.mostPlayedArtists(fromTimeStamp, limit = 10).first() +
                database.artistsBookmarkedByCreateDateAsc().first()
            )
            .distinctBy { it.id }
            .filter { it.artist.isYouTubeArtist }
            .shuffled().take(3)
        val songSeeds = database.mostPlayedSongs(fromTimeStamp, limit = 5).first()
            .shuffled().take(2)

        val candidatePlaylists = java.util.Collections.synchronizedList(mutableListOf<PlaylistItem>())

        kotlinx.coroutines.coroutineScope {
            artistSeeds.map { seed ->
                launch(Dispatchers.IO) {
                    YouTube.artist(seed.id).onSuccess { page ->
                        page.sections.forEach { section ->
                            section.items.filterIsInstance<PlaylistItem>().forEach { playlist ->
                                if (playlist.author?.name != "YouTube Music" &&
                                    playlist.author?.name != "YouTube" &&
                                    playlist.author?.name != "Playlist" &&
                                    playlist.author?.name != seed.artist.name &&
                                    !playlist.id.startsWith("RD") &&
                                    !playlist.id.startsWith("OLAK")
                                ) {
                                    candidatePlaylists.add(playlist)
                                }
                            }
                        }
                    }
                }
            }

            songSeeds.map { seed ->
                launch(Dispatchers.IO) {
                    val endpoint = YouTube.next(WatchEndpoint(videoId = seed.id)).getOrNull()?.relatedEndpoint
                    if (endpoint != null) {
                        YouTube.related(endpoint).onSuccess { page ->
                            page.playlists.forEach { playlist ->
                                if (playlist.author?.name != "YouTube Music" &&
                                    playlist.author?.name != "YouTube" &&
                                    playlist.author?.name != "Playlist" &&
                                    !playlist.id.startsWith("RD") &&
                                    !playlist.id.startsWith("OLAK")
                                ) {
                                    candidatePlaylists.add(playlist)
                                }
                            }
                        }
                    }
                }
            }
        }

        val uniqueCandidates = candidatePlaylists.distinctBy { it.id }.shuffled().take(5)

        val playlists = java.util.Collections.synchronizedList(mutableListOf<CommunityPlaylistItem>())

        kotlinx.coroutines.coroutineScope {
            uniqueCandidates.map { playlist ->
                launch(Dispatchers.IO) {
                    YouTube.playlist(playlist.id).onSuccess { page ->
                        val songs = page.songs.take(10)
                        if (songs.isNotEmpty()) {
                            
                            val songCountText = page.playlist.songCountText ?: playlist.songCountText
                            val updatedPlaylist = playlist.copy(songCountText = songCountText)
                            playlists.add(CommunityPlaylistItem(updatedPlaylist, songs))
                        }
                    }
                }
            }.forEach { it.join() }
        }

        communityPlaylists.value = playlists.shuffled()
    }

    
    private suspend fun loadLocalDataPhase() {
        val hideVideoSongs = context.dataStore.get(HideVideoSongsKey, false)

        // Build the taste model first so every shelf below can rank by it.
        refreshTasteProfile()

        getQuickPicks()

        // "Reproducido recientemente": pure chronological history (latest listen first), one bounded
        // SELECT. Over-fetch then trim so hideVideoSongs filters BEFORE the shelf cap (filtering after
        // a LIMIT 15 shrank/hid the shelf on video-heavy history). Same empty-guard as every shelf: a
        // transient empty read must not wipe a populated row.
        val newRecentlyPlayed = runCatching { database.recentlyPlayedSongs(40).first() }
            .getOrDefault(emptyList())
            .filterVideoSongs(hideVideoSongs)
            .take(15)
        if (newRecentlyPlayed.isNotEmpty() || recentlyPlayed.value == null) recentlyPlayed.value = newRecentlyPlayed

        val newForgotten = database.forgottenFavorites().first()
            .filterVideoSongs(hideVideoSongs).rankedByTaste().take(20)
        if (newForgotten.isNotEmpty() || forgottenFavorites.value == null) forgottenFavorites.value = newForgotten

        val fromTimeStamp = System.currentTimeMillis() - 86400000L * 7 * 2
        val keepListeningSongs = database.mostPlayedSongs(fromTimeStamp, limit = 15, offset = 5).first()
            .filterVideoSongs(hideVideoSongs).shuffled().take(10)
        val keepListeningAlbums = database.mostPlayedAlbums(fromTimeStamp, limit = 8, offset = 2).first()
            .filter { it.album.thumbnailUrl != null }.shuffled().take(5)
        val keepListeningArtists = database.mostPlayedArtists(fromTimeStamp).first()
            .filter { it.artist.isYouTubeArtist && it.artist.thumbnailUrl != null }.shuffled().take(5)
        val newKeepListening = (keepListeningSongs + keepListeningAlbums + keepListeningArtists).shuffled()
        // Same guard: a transient empty read must not wipe a populated "keep listening" shelf.
        if (newKeepListening.isNotEmpty() || keepListening.value == null) keepListening.value = newKeepListening

        allLocalItems.value = (quickPicks.value.orEmpty() + forgottenFavorites.value.orEmpty() + keepListening.value.orEmpty())
            .filter { it is Song || it is Album }

        // "Mix de la mañana/tarde/noche": in-memory only (reuses the shelves loaded above). Computed once
        // per load/refresh — NO clock polling, NO background work (heat/battery rule). The mix is stable
        // within a time bucket + day (seeded shuffle) and changes between buckets/days on the next load.
        buildTimeOfDayMix()
        // NOTE: the new taste shelves (getNewFromArtists / getGenreMix) are intentionally NOT called here.
        // They run isolated + guarded in load() so a failure in them can never abort this phase and, worse,
        // skip the network phase that loads the "Similar/Community/Daily Discover/YouTube" carousels.
    }

    /**
     * "Mix de la mañana / de la tarde / de la noche" — a LIGHT shelf built from the taste-ranked local
     * pool the home already computed (quick picks + forgotten favorites + keep-listening songs). No new
     * queries, no network: just a seeded reshuffle where seed = dayOfYear*10 + timeBucket, so the mix is
     * stable within a bucket (morning 5-11h / afternoon 12-18h / night otherwise) and rotates between them.
     */
    private fun buildTimeOfDayMix() {
        val pool = (quickPicks.value.orEmpty() +
            forgottenFavorites.value.orEmpty() +
            keepListening.value.orEmpty().filterIsInstance<Song>())
            .distinctBy { it.id }
        if (pool.size < 4) return // too little history for a meaningful mix — shelf stays hidden
        val hour = java.time.LocalTime.now().hour
        val bucket = when (hour) {
            in 5..11 -> 0  // morning
            in 12..18 -> 1 // afternoon
            else -> 2      // night
        }
        val seed = (LocalDate.now().dayOfYear * 10 + bucket).toLong()
        timeOfDayMix.value = TimeOfDayMix(
            bucket = bucket,
            songs = pool.shuffled(Random(seed)).take(12),
        )
    }

    /**
     * "Tu mix de [Género]" — build a shelf of the user's OWN songs (history + library) from their single
     * top genre, derived on-device via [GenreCache] (artist → genre) and ranked by the taste model. Fully
     * local, no network. Hidden until enough of the user's songs share a known genre (a real cluster).
     */
    private suspend fun getGenreMix() {
        val genres = runCatching { iad1tya.echo.music.reco.GenreCache.snapshot(context) }.getOrDefault(emptyMap())
        if (genres.isEmpty()) return // genres not learned yet (enriched over WiFi) → shelf stays hidden this pass
        val fromTimeStamp = System.currentTimeMillis() - 86400000L * 30
        val played = runCatching { database.mostPlayedSongs(fromTimeStamp, limit = 100).first() }.getOrDefault(emptyList())
        val library = runCatching { database.librarySongsForTaste(400).first() }.getOrDefault(emptyList())
        val hideVideoSongs = runCatching { context.dataStore.get(HideVideoSongsKey, false) }.getOrDefault(false)
        val candidates = (played + library).distinctBy { it.id }.filterVideoSongs(hideVideoSongs)
        if (candidates.isEmpty()) return
        // Group each song under the genre of its first artist we know a genre for.
        val byGenre = HashMap<String, MutableList<Song>>()
        candidates.forEach { s ->
            val g = s.artists.firstNotNullOfOrNull { a -> genres[a.name.lowercase()]?.takeIf { it.isNotBlank() } }
                ?: return@forEach
            byGenre.getOrPut(g) { mutableListOf() }.add(s)
        }
        // Pick the top genre by summed TASTE WEIGHT, not raw song count — otherwise a big imported discography
        // wins on bulk over what the user actually plays/likes (a fresh reggaeton import would beat the jazz you
        // actually listen to). This mirrors AffinityEngine's LIBRARY_*_CAP anti-domination intent. Clamp per-song
        // scores at 0 so disliked/neutral songs never pull a genre negative. Keep the >= 4 cluster gate so the mix
        // is substantial. Fall back to count if the taste model isn't built yet. Never null out an existing mix on
        // a transient empty read — only overwrite with a good one.
        val p = tasteProfile
        val top = byGenre.entries
            .filter { it.value.size >= 4 }
            .maxByOrNull { e ->
                if (p == null) e.value.size.toDouble()
                else e.value.sumOf { p.score(it).coerceAtLeast(0.0) }
            } ?: return
        val songs = top.value.rankedByTaste().distinctBy { it.id }.take(12)
        if (songs.size >= 4) genreMix.value = GenreMix(top.key, songs)
    }

    /**
     * "Novedades de tus artistas" — surface this week's Release Radar (new releases from artists you follow /
     * play most) as an album shelf on Home. Reads the already-built, windowed, deduped Release Radar store
     * (no extra network); each [ReleaseRadarItem] becomes an [AlbumItem] whose tap opens the album page.
     */
    private suspend fun getNewFromArtists() {
        // Read the already-pruned radar table directly. The weekly wipe/prune in ReleaseRadarWorker
        // already enforces "this week only", so an extra fetchedAt >= currentWindowStart() filter here
        // was redundant AND hid a valid drop whenever the last successful fetch landed outside the
        // current Friday window (once-per-install seed, or a Doze-deferred Friday worker) — the classic
        // "Release Radar always empty even though I follow artists" bug.
        val releases = runCatching {
            database.releasesByDateDesc().first()
        }.getOrDefault(emptyList())
        val albums = releases
            .sortedByDescending { it.releaseDate }
            .distinctBy { it.playId.ifBlank { it.id } }
            .mapNotNull { r ->
                val browse = r.playId.ifBlank { return@mapNotNull null }
                AlbumItem(
                    browseId = browse,
                    playlistId = "",
                    title = r.title,
                    artists = listOf(Artist(name = r.artist, id = r.artistId.ifEmpty { null })),
                    year = runCatching { r.releaseDate.year }.getOrNull(),
                    thumbnail = r.artworkUri ?: "",
                )
            }
            .take(20)
        // Same empty-guard as every other shelf: a transient empty read must not wipe a populated shelf.
        if (albums.isNotEmpty() || newFromArtists.value == null) newFromArtists.value = albums
    }

    
    private suspend fun loadSimilarRecommendations() {
        val hideExplicit = context.dataStore.get(HideExplicitKey, false)
        val hideVideoSongs = context.dataStore.get(HideVideoSongsKey, false)
        val fromTimeStamp = System.currentTimeMillis() - 86400000L * 7 * 2

        coroutineScope {
            // Seed the home from the user's taste: most-played artists PLUS the artists they chose in
            // onboarding / follow (bookmarked). A fresh user with no play history still gets a
            // taste-based home built from YouTube's per-artist recommendations (the artist page's
            // "fans might also like" + their music = YouTube's algorithm).
            val playedArtists = database.mostPlayedArtists(fromTimeStamp, limit = 15).first()
            val bookmarkedArtists = database.artistsBookmarkedByCreateDateAsc().first()
            val artistDeferreds = (playedArtists + bookmarkedArtists)
                .filter { it.artist.isYouTubeArtist }
                .distinctBy { it.id }
                .shuffled().take(6)
                .map { artist ->
                    async(Dispatchers.IO) {
                        val items = mutableListOf<YTItem>()
                        YouTube.artist(artist.id).onSuccess { page ->
                            page.sections.takeLast(3).forEach { section -> items += section.items }
                        }
                        SimilarRecommendation(
                            title = artist,
                            items = items
                                .distinctBy { item -> item.id }
                                .filterExplicit(hideExplicit)
                                .filterVideoSongs(hideVideoSongs)
                                .rankedByTasteYt()
                                .take(12)
                                .ifEmpty { return@async null }
                        )
                    }
                }

            // Seed from the user's most-played AND liked (favourite) songs — including everything
            // imported from Spotify, which lands as liked songs — so the "mix"-style recommendations
            // reflect the music they actually love, not just recent plays.
            val playedSongs = database.mostPlayedSongs(fromTimeStamp, limit = 15).first().filter { it.album != null }
            val likedSongs = database.likedSongsByCreateDateAsc().first()
            val songDeferreds = (playedSongs + likedSongs)
                .distinctBy { it.id }
                .shuffled().take(5)
                .map { song ->
                    async(Dispatchers.IO) {
                        val endpoint = YouTube.next(WatchEndpoint(videoId = song.id)).getOrNull()?.relatedEndpoint
                            ?: return@async null
                        val page = YouTube.related(endpoint).getOrNull() ?: return@async null
                        SimilarRecommendation(
                            title = song,
                            items = (page.songs.shuffled().take(10) +
                                    page.albums.shuffled().take(5) +
                                    page.artists.shuffled().take(3) +
                                    page.playlists.shuffled().take(3))
                                .distinctBy { it.id }
                                .filterExplicit(hideExplicit)
                                .filterVideoSongs(hideVideoSongs)
                                .rankedByTasteYt()
                                .ifEmpty { return@async null }
                        )
                    }
                }

            // Seed from most-played albums PLUS the user's favourite (bookmarked) albums, so
            // recommendations also reflect albums they explicitly saved.
            val playedAlbums = database.mostPlayedAlbums(fromTimeStamp, limit = 10).first()
            val likedAlbums = database.albumsLikedByCreateDateAsc().first()
            val albumDeferreds = (playedAlbums + likedAlbums)
                .filter { it.album.thumbnailUrl != null }
                .distinctBy { it.id }
                .shuffled().take(3)
                .map { album ->
                    async(Dispatchers.IO) {
                        val items = mutableListOf<YTItem>()
                        YouTube.album(album.id).onSuccess { page ->
                            page.otherVersions.let { items += it }
                        }
                        album.artists.firstOrNull()?.id?.let { artistId ->
                            YouTube.artist(artistId).onSuccess { page ->
                                page.sections.lastOrNull()?.items?.let { items += it }
                            }
                        }
                        SimilarRecommendation(
                            title = album,
                            items = items
                                .distinctBy { it.id }
                                .filterExplicit(hideExplicit)
                                .filterVideoSongs(hideVideoSongs)
                                .shuffled()
                                .take(10)
                                .ifEmpty { return@async null }
                        )
                    }
                }

            val results = (artistDeferreds + songDeferreds + albumDeferreds).awaitAll()
            val dislikedNow = runCatching { dislikeStore.snapshot() }.getOrDefault(iad1tya.echo.music.dislike.DislikeStore.Disliked())
            similarRecommendations.value = results.filterNotNull()
                .filterNot { rec ->
                    // Drop a whole "Similar a X" row if its seed is something the user disliked.
                    when (val t = rec.title) {
                        is iad1tya.echo.music.db.entities.Song -> t.id in dislikedNow.songs
                        is iad1tya.echo.music.db.entities.Album -> t.id in dislikedNow.albums
                        is iad1tya.echo.music.db.entities.Artist -> t.id in dislikedNow.artists
                        else -> false
                    }
                }
                .mapNotNull { rec ->
                    val items = rec.items.filterDisliked(dislikedNow)
                    if (items.isEmpty()) null else rec.copy(items = items)
                }
                .shuffled()
        }
    }

    
    /** Drop anything the user marked "No me gusta" (the song, its artist, its album, or the item id). */
    private fun List<com.music.innertube.models.YTItem>.filterDisliked(
        d: iad1tya.echo.music.dislike.DislikeStore.Disliked,
    ): List<com.music.innertube.models.YTItem> {
        if (d.isEmpty) return this
        return filterNot { item ->
            when (item) {
                is com.music.innertube.models.SongItem ->
                    item.id in d.songs || item.artists.any { it.id != null && it.id in d.artists }
                is com.music.innertube.models.AlbumItem ->
                    item.id in d.albums || (item.artists?.any { it.id != null && it.id in d.artists } == true)
                is com.music.innertube.models.ArtistItem -> item.id in d.artists
                is com.music.innertube.models.PlaylistItem -> item.id in d.playlists
                else -> false
            }
        }
    }

    private suspend fun loadNetworkDataPhase() {
        val hideExplicit = context.dataStore.get(HideExplicitKey, false)
        val hideVideoSongs = context.dataStore.get(HideVideoSongsKey, false)
        val hideYoutubeShorts = context.dataStore.get(HideYoutubeShortsKey, false)
        val dislikedNow = runCatching { dislikeStore.snapshot() }.getOrDefault(iad1tya.echo.music.dislike.DislikeStore.Disliked())

        // Hard ceiling so a throttled/hung YouTube call (e.g. while a big Spotify import is hammering
        // the network) can NEVER leave the home stuck "loading forever" — whatever arrived in time is
        // shown, the rest is cancelled and the refresh spinner clears.
        withTimeoutOrNull(25_000) {
        coroutineScope {
            launch(Dispatchers.IO) { getDailyMixes() }
            launch(Dispatchers.IO) { getCommunityPlaylists() }
            launch(Dispatchers.IO) { loadSimilarRecommendations() }
            launch(Dispatchers.IO) {
                YouTube.home().onSuccess { page ->
                    homePage.value = page.copy(
                        sections = page.sections.mapNotNull { section ->
                            val filteredItems = section.items
                                .filterExplicit(hideExplicit)
                                .filterVideoSongs(hideVideoSongs)
                                .filterYoutubeShorts(hideYoutubeShorts)
                                .filterDisliked(dislikedNow)
                            if (filteredItems.isEmpty()) null else section.copy(items = filteredItems)
                        }
                    )
                }.onFailure { reportException(it) }
            }
            launch(Dispatchers.IO) {
                YouTube.explore().onSuccess { page ->
                    explorePage.value = page.copy(
                        newReleaseAlbums = page.newReleaseAlbums.filterExplicit(hideExplicit)
                    )
                }.onFailure { reportException(it) }
            }
            if (YouTube.cookie != null) {
                launch(Dispatchers.IO) { loadAccountPlaylists() }
            }
        }
        }


        allYtItems.value = similarRecommendations.value?.flatMap { it.items }.orEmpty() +
                homePage.value?.sections?.flatMap { it.items }.orEmpty()
    }

    private suspend fun load() {
        isLoading.value = true

        // Guard EACH phase independently: a throw in the local phase must never skip the network phase (that
        // would drop the Similar/Community/Daily Discover/YouTube carousels — a regression). reportException
        // surfaces the failure without killing the load.
        runCatching { loadLocalDataPhase() }.onFailure { reportException(it) }
        isLoading.value = false

        runCatching { loadNetworkDataPhase() }.onFailure { reportException(it) }

        // The new taste shelves run LAST and fully isolated, so they can never delay or break the core load.
        runCatching { getNewFromArtists() }.onFailure { reportException(it) }
        runCatching { getGenreMix() }.onFailure { reportException(it) }

        saveSnapshot()
    }

    /** Cache the loaded home at process level so returning to Home / resuming restores it instantly. */
    private fun saveSnapshot() {
        snapshot = HomeSnapshot(
            savedAtMs = System.currentTimeMillis(),
            quickPicks = quickPicks.value,
            dailyMixes = dailyMixes.value,
            recentlyPlayed = recentlyPlayed.value,
            timeOfDayMix = timeOfDayMix.value,
            forgottenFavorites = forgottenFavorites.value,
            keepListening = keepListening.value,
            similarRecommendations = similarRecommendations.value,
            accountPlaylists = accountPlaylists.value,
            homePage = homePage.value,
            explorePage = explorePage.value,
            communityPlaylists = communityPlaylists.value,
            newFromArtists = newFromArtists.value,
            genreMix = genreMix.value,
            allLocalItems = allLocalItems.value,
            allYtItems = allYtItems.value,
        )
    }

    companion object {
        // How many SpeedDial tiles count as "visible" (≈ the first pager page on a phone) for the
        // cross-section dedup above — cheap approximation, layout-independent.
        private const val SPEED_DIAL_VISIBLE_TILES = 12

        // Process-level snapshot of the loaded home. A recreated ViewModel (returning to the Home tab
        // or resuming the app from the background) restores from this INSTANTLY and does NOT auto-reload
        // — after that, refreshing is manual (pull to refresh). Only a cold app start (fresh process =
        // null snapshot) loads from scratch. Exception: if the snapshot is from a PREVIOUS calendar
        // day, treat it as missing so "Para ti" / mixes rotate without requiring pull-to-refresh
        // (Xiaomi/etc. keep the process alive for days with a frozen snapshot).
        @Volatile
        private var snapshot: HomeSnapshot? = null

        private class HomeSnapshot(
            val savedAtMs: Long,
            val quickPicks: List<Song>?,
            val dailyMixes: List<DailyMix>?,
            val recentlyPlayed: List<Song>?,
            val timeOfDayMix: TimeOfDayMix?,
            val forgottenFavorites: List<Song>?,
            val keepListening: List<LocalItem>?,
            val similarRecommendations: List<SimilarRecommendation>?,
            val accountPlaylists: List<PlaylistItem>?,
            val homePage: HomePage?,
            val explorePage: ExplorePage?,
            val communityPlaylists: List<CommunityPlaylistItem>?,
            val newFromArtists: List<AlbumItem>?,
            val genreMix: GenreMix?,
            val allLocalItems: List<LocalItem>,
            val allYtItems: List<YTItem>,
        ) {
            fun isSameLocalDay(nowMs: Long = System.currentTimeMillis()): Boolean {
                val zone = ZoneId.systemDefault()
                val savedDay = java.time.Instant.ofEpochMilli(savedAtMs).atZone(zone).toLocalDate()
                val today = java.time.Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate()
                return savedDay == today
            }
        }
    }

    private val _isLoadingMore = MutableStateFlow(false)
    fun loadMoreYouTubeItems(continuation: String?) {
        if (continuation == null || _isLoadingMore.value) return

        viewModelScope.launch(Dispatchers.IO) {
            _isLoadingMore.value = true
            val hideExplicit = context.dataStore.get(HideExplicitKey, false)
            val hideVideoSongs = context.dataStore.get(HideVideoSongsKey, false)
            val hideYoutubeShorts = context.dataStore.get(HideYoutubeShortsKey, false)
            val nextSections = YouTube.home(continuation).getOrNull() ?: run {
                _isLoadingMore.value = false
                return@launch
            }

            homePage.value = nextSections.copy(
                chips = homePage.value?.chips,
                sections = (homePage.value?.sections.orEmpty() + nextSections.sections).mapNotNull { section ->
                    val filteredItems = section.items.filterExplicit(hideExplicit).filterVideoSongs(hideVideoSongs).filterYoutubeShorts(hideYoutubeShorts)
                    if (filteredItems.isEmpty()) null else section.copy(items = filteredItems)
                }
            )
            _isLoadingMore.value = false
        }
    }

    fun toggleChip(chip: HomePage.Chip?) {
        if (chip == null || chip == selectedChip.value && previousHomePage.value != null) {
            homePage.value = previousHomePage.value
            previousHomePage.value = null
            selectedChip.value = null
            return
        }

        if (selectedChip.value == null) {
            previousHomePage.value = homePage.value
        }

        viewModelScope.launch(Dispatchers.IO) {
            val hideExplicit = context.dataStore.get(HideExplicitKey, false)
            val hideVideoSongs = context.dataStore.get(HideVideoSongsKey, false)
            val hideYoutubeShorts = context.dataStore.get(HideYoutubeShortsKey, false)
            val nextSections = YouTube.home(params = chip.endpoint?.params).getOrNull() ?: return@launch

            homePage.value = nextSections.copy(
                chips = homePage.value?.chips,
                sections = nextSections.sections.map { section ->
                    section.copy(items = section.items.filterExplicit(hideExplicit).filterVideoSongs(hideVideoSongs).filterYoutubeShorts(hideYoutubeShorts))
                }
            )
            selectedChip.value = chip
        }
    }

    private suspend fun loadAccountPlaylists() {
        val hideYoutubeShorts = context.dataStore.get(HideYoutubeShortsKey, false)
        YouTube.library("FEmusic_liked_playlists").completed().onSuccess {
            accountPlaylists.value = it.items.filterIsInstance<PlaylistItem>()
                .filterNot { it.id == "SE" }
                .filterYoutubeShorts(hideYoutubeShorts)
        }.onFailure {
            reportException(it)
        }
    }

    fun refresh() {
        if (isRefreshing.value) return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                isRefreshing.value = true
                // Guard like the init-block load: a throw here must not kill the manual refresh silently.
                runCatching { load() }.onFailure { reportException(it) }
            } finally {
                isRefreshing.value = false
            }
        }
        
        viewModelScope.launch(Dispatchers.IO) {
            syncUtils.tryAutoSync()
        }
    }

    init {

        // Runs on BOTH paths (snapshot restore and cold load): the stale-tile fix must not depend
        // on which way the home came up.
        refreshSpeedDialThumbnails()

        val restored = snapshot?.takeIf { it.isSameLocalDay() }
        if (restored != null) {
            // Returning to Home / resuming the app: restore the already-loaded home instantly and DON'T
            // auto-reload (the user refreshes manually). Only a cold app start — or a snapshot from a
            // previous calendar day — loads from scratch so recommendations actually rotate.
            quickPicks.value = restored.quickPicks
            dailyMixes.value = restored.dailyMixes
            recentlyPlayed.value = restored.recentlyPlayed
            timeOfDayMix.value = restored.timeOfDayMix
            forgottenFavorites.value = restored.forgottenFavorites
            keepListening.value = restored.keepListening
            similarRecommendations.value = restored.similarRecommendations
            accountPlaylists.value = restored.accountPlaylists
            homePage.value = restored.homePage
            explorePage.value = restored.explorePage
            communityPlaylists.value = restored.communityPlaylists
            newFromArtists.value = restored.newFromArtists
            genreMix.value = restored.genreMix
            allLocalItems.value = restored.allLocalItems
            allYtItems.value = restored.allYtItems
            // The snapshot can carry a stale time-of-day bucket ("Mix de la mañana" restored at 22:00
            // and kept indefinitely, since restore skips the auto-reload). Recompute bucket + seed for
            // the current hour/day from the pools just restored above — pure in-memory reshuffle, no
            // queries/network. If the restored pool is too small, the guard inside keeps the old mix.
            buildTimeOfDayMix()
        } else {
            if (snapshot != null && restored == null) snapshot = null
            viewModelScope.launch(Dispatchers.IO) {
                context.dataStore.data
                    .map { it[InnerTubeCookieKey] }
                    .distinctUntilChanged()
                    .first()

                // Guard the initial load so a transient exception (e.g. a throttled DB/network read)
                // can't silently kill the coroutine and leave the home blank until the app is restarted.
                runCatching { load() }.onFailure { reportException(it) }
            }
        }

        // First-run onboarding finishes AFTER this ViewModel already ran its initial home load (with an
        // empty library) and cached an empty snapshot — so returning to Home just restored that blank
        // snapshot and the home stayed empty until the app was restarted. Reload once when onboarding
        // completes so the artists the user just picked seed the home immediately.
        viewModelScope.launch(Dispatchers.IO) {
            context.dataStore.data
                .map { it[OnboardingArtistsDoneKey] ?: false }
                .distinctUntilChanged()
                .drop(1) // ignore the current value; only react to onboarding completing this session
                .filter { it }
                .collect {
                    snapshot = null
                    runCatching { load() }.onFailure { reportException(it) }
                }
        }

        // Rebuild Home in REAL TIME when the user's followed/subscribed artists change (e.g. they subscribe
        // to artists after skipping onboarding). Previously following an artist only wrote bookmarkedAt and
        // nothing re-read it, so Home stayed empty/stale until a pull-to-refresh or cold restart. React to
        // the followed-artist id SET (distinctUntilChanged = only real follow/unfollow, not row re-emits);
        // drop(1) skips the current value (the cold-start load already ran); collectLatest + delay debounces
        // a burst of subscriptions into a single rebuild.
        viewModelScope.launch(Dispatchers.IO) {
            database.artistsBookmarkedByCreateDateAsc()
                .map { artists -> artists.map { it.id }.toSet() }
                .distinctUntilChanged()
                .drop(1)
                .collectLatest {
                    delay(600)
                    snapshot = null
                    runCatching { load() }.onFailure { reportException(it) }
                }
        }


        // On a FRESH install the home is empty until there's listening history — and the first playback seeds
        // that history, but nothing re-read it, so the home stayed blank until a cold restart. Reload ONCE, in
        // real time, when the first play event lands while the home is still empty (guarded on quickPicks being
        // empty so it does NOT reload on every subsequent song, and skipping the initial value so a returning
        // user with existing history is unaffected). No account/subscription needed.
        viewModelScope.launch(Dispatchers.IO) {
            database.lastEvent()
                .map { it != null }
                .distinctUntilChanged()
                .drop(1)
                .filter { it }
                .collect {
                    if (quickPicks.value.isNullOrEmpty()) {
                        delay(600)
                        snapshot = null
                        runCatching { load() }.onFailure { reportException(it) }
                    }
                }
        }

        viewModelScope.launch(Dispatchers.IO) {
            syncUtils.tryAutoSync()
        }


        viewModelScope.launch(Dispatchers.IO) {
            context.dataStore.data
                .map { it[InnerTubeCookieKey] }
                .collect { cookie ->
                    
                    if (isProcessingAccountData) return@collect

                    
                    lastProcessedCookie = cookie
                    isProcessingAccountData = true

                    try {
                        if (cookie != null && cookie.isNotEmpty()) {

                            
                            YouTube.cookie = cookie

                            
                            YouTube.accountInfo().onSuccess { info ->
                                accountName.value = info.name
                                accountImageUrl.value = info.thumbnailUrl
                            }.onFailure {
                                reportException(it)
                            }
                        } else {
                            accountName.value = "Guest"
                            accountImageUrl.value = null
                            accountPlaylists.value = null
                        }
                    } finally {
                        isProcessingAccountData = false
                    }
                }
        }

        
        viewModelScope.launch(Dispatchers.IO) {
            context.dataStore.data
                .map { it[HideYoutubeShortsKey] ?: false }
                .distinctUntilChanged()
                .collect {
                    if (YouTube.cookie != null && accountPlaylists.value != null) {
                        loadAccountPlaylists()
                    }
                }
        }
    }
}
