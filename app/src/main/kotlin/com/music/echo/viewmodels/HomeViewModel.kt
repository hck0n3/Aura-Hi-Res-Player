

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
import iad1tya.echo.music.db.MusicDatabase
import iad1tya.echo.music.db.entities.Album
import iad1tya.echo.music.db.entities.LocalItem
import iad1tya.echo.music.db.entities.Song
import iad1tya.echo.music.db.entities.SpeedDialItem
import iad1tya.echo.music.extensions.filterVideoSongs
import iad1tya.echo.music.extensions.toEnum
import iad1tya.echo.music.models.SimilarRecommendation
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
import javax.inject.Inject
import kotlin.random.Random

data class DailyDiscoverItem(
    val seed: Song,
    val recommendation: YTItem,
    val relatedEndpoint: BrowseEndpoint?
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
    val dailyDiscover = MutableStateFlow<List<DailyDiscoverItem>?>(null)
    val forgottenFavorites = MutableStateFlow<List<Song>?>(null)
    val keepListening = MutableStateFlow<List<LocalItem>?>(null)
    val similarRecommendations = MutableStateFlow<List<SimilarRecommendation>?>(null)
    val accountPlaylists = MutableStateFlow<List<PlaylistItem>?>(null)
    val homePage = MutableStateFlow<HomePage?>(null)
    val explorePage = MutableStateFlow<ExplorePage?>(null)
    val communityPlaylists = MutableStateFlow<List<CommunityPlaylistItem>?>(null)
    val selectedChip = MutableStateFlow<HomePage.Chip?>(null)
    private val previousHomePage = MutableStateFlow<HomePage?>(null)

    // On-device taste model (see AffinityEngine). Rebuilt once per load/refresh and reused to RANK every
    // shelf by how much you'd actually like each candidate — instead of the old plain .shuffled().
    @Volatile
    private var tasteProfile: iad1tya.echo.music.reco.TasteProfile? = null

    /** Rebuild the taste profile from the latest listening history + dislikes. Cheap, fully on-device. */
    private suspend fun refreshTasteProfile() {
        val events = runCatching { database.recentEventsWithSong(3000).first() }.getOrDefault(emptyList())
        val disliked = runCatching { dislikeStore.snapshot() }
            .getOrDefault(iad1tya.echo.music.dislike.DislikeStore.Disliked())
        runCatching {
            val genres = iad1tya.echo.music.reco.GenreCache.snapshot(context)
            tasteProfile = iad1tya.echo.music.reco.AffinityEngine.buildProfile(events, disliked, artistGenres = genres)
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
    }

    /** Rank songs by taste, with a little jitter so refreshes vary instead of being identical. */
    private fun List<Song>.rankedByTaste(): List<Song> {
        val p = tasteProfile ?: return shuffled()
        val rnd = java.util.Random()
        // Precompute the key ONCE per item — rnd inside the comparator would be inconsistent between
        // comparisons and crash TimSort ("Comparison method violates its general contract").
        return map { it to (p.score(it) + rnd.nextDouble() * 0.2) }
            .sortedByDescending { it.second }
            .map { it.first }
    }

    private fun tasteScoreYt(item: com.music.innertube.models.YTItem): Double {
        val p = tasteProfile ?: return 0.0
        return when (item) {
            is com.music.innertube.models.SongItem -> p.scoreNames(item.artists.map { it.name }, item.title)
            is com.music.innertube.models.AlbumItem -> p.scoreNames(item.artists?.map { it.name } ?: emptyList(), item.title)
            else -> 0.0
        }
    }

    val allLocalItems = MutableStateFlow<List<LocalItem>>(emptyList())
    val allYtItems = MutableStateFlow<List<YTItem>>(emptyList())

    val speedDialItems: StateFlow<List<YTItem>> =
        combine(
            database.speedDialDao.getAll(),
            keepListening,
            quickPicks
        ) { pinned, keepListening, quick ->
            val pinnedItems = pinned.map { it.toYTItem() }
            val filled = pinnedItems.toMutableList()
            val targetSize = 27

            if (filled.size < targetSize) {
                
                keepListening?.let { k ->
                    val needed = targetSize - filled.size
                    val available = k.filter { item ->
                        filled.none { p -> p.id == item.id }
                    }.mapNotNull { item ->
                        when (item) {
                            is Song -> SongItem(
                                id = item.id,
                                title = item.title,
                                artists = item.artists.map { Artist(name = it.name, id = it.id) },
                                thumbnail = item.thumbnailUrl ?: "",
                                explicit = false
                            )
                            is Album -> AlbumItem(
                                browseId = item.id,
                                playlistId = item.album.playlistId ?: "",
                                title = item.title,
                                artists = item.artists.map { Artist(name = it.name, id = it.id) },
                                year = item.album.year,
                                thumbnail = item.thumbnailUrl ?: ""
                            )
                            else -> null
                        }
                    }
                    filled.addAll(available.take(needed))
                }
            }

            if (filled.size < targetSize) {
                
                quick?.let { q ->
                    val needed = targetSize - filled.size
                    val available = q.filter { song ->
                        filled.none { p -> p.id == song.id }
                    }.map { song ->
                        SongItem(
                            id = song.id,
                            title = song.title,
                            artists = song.artists.map { Artist(name = it.name, id = it.id) },
                            thumbnail = song.thumbnailUrl ?: "",
                            explicit = false
                        )
                    }
                    filled.addAll(available.take(needed))
                }
            }

            filled.take(targetSize)
        }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

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
            val speedDialItem = SpeedDialItem.fromYTItem(item)
            val isPinned = database.speedDialDao.isPinned(speedDialItem.id).first()
            if (isPinned) {
                database.speedDialDao.delete(speedDialItem.id)
            } else {
                database.speedDialDao.insert(speedDialItem)
            }
        }
    }
    
    private var lastProcessedCookie: String? = null
    
    private var isProcessingAccountData = false

    private suspend fun getDailyDiscover() {
        val hideVideoSongs = context.dataStore.get(HideVideoSongsKey, false)
        val likedSongs = database.likedSongsByCreateDateAsc().first()
        if (likedSongs.isEmpty()) return

        val seeds = likedSongs.distinctBy { it.id }.rankedByTaste().take(5)

        
        val items = java.util.Collections.synchronizedList(mutableListOf<DailyDiscoverItem>())

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

                            // Pick the related song that best fits your taste (with a little jitter for
                            // variety) instead of a random one.
                            val rndPick = java.util.Random()
                            val recommendation = recommendations
                                .filter { it.id != seed.id }
                                .maxByOrNull { tasteScoreYt(it) + rndPick.nextDouble() * 0.15 }

                            if (recommendation != null) {
                                items.add(
                                    DailyDiscoverItem(
                                        seed = seed,
                                        recommendation = recommendation,
                                        relatedEndpoint = endpoint
                                    )
                                )
                            }
                        }
                    }
                }
            }.forEach { it.join() }
        }

        
        val rnd = java.util.Random()
        // Precompute the key once per item (rnd inside the comparator would crash TimSort).
        dailyDiscover.value = items.toList().distinctBy { it.recommendation.id }
            .map { it to (tasteScoreYt(it.recommendation) + rnd.nextDouble() * 0.2) }
            .sortedByDescending { it.second }
            .map { it.first }
    }

    private suspend fun getQuickPicks() {
        val hideVideoSongs = context.dataStore.get(HideVideoSongsKey, false)
        when (quickPicksEnum.first()) {
            QuickPicks.QUICK_PICKS -> {
                val relatedSongs = database.quickPicks().first().filterVideoSongs(hideVideoSongs)
                val forgotten = database.forgottenFavorites().first().filterVideoSongs(hideVideoSongs).take(8)

                // Paint quick picks from LOCAL data immediately so the home appears fast — don't block the
                // first paint on a YouTube "related" network call. Guard against blanking a populated
                // shelf on a transient empty read (the "home went empty" bug); empty only on first load.
                val localQuick = (relatedSongs + forgotten).distinctBy { it.id }.rankedByTaste().take(20)
                if (localQuick.isNotEmpty() || quickPicks.value == null) quickPicks.value = localQuick

                // Enrich with YouTube "related to your last play" in the background and update once it
                // returns — this no longer delays the home from appearing.
                val recentSong = database.events().first().firstOrNull()?.song
                if (recentSong != null) {
                    viewModelScope.launch(Dispatchers.IO) {
                        val endpoint = YouTube.next(WatchEndpoint(videoId = recentSong.id))
                            .getOrNull()?.relatedEndpoint ?: return@launch
                        YouTube.related(endpoint).onSuccess { page ->
                            val ytSimilarSongs = mutableListOf<Song>()
                            page.songs.take(10).forEach { ytSong ->
                                database.song(ytSong.id).first()?.let { localSong ->
                                    if (!hideVideoSongs || !localSong.song.isVideo) ytSimilarSongs.add(localSong)
                                }
                            }
                            val combined = (relatedSongs + forgotten + ytSimilarSongs)
                                .distinctBy { it.id }.rankedByTaste().take(20)
                            if (combined.isNotEmpty()) quickPicks.value = combined
                        }
                    }
                }
            }
            QuickPicks.LAST_LISTEN -> {
                val song = database.events().first().firstOrNull()?.song
                if (song != null && database.hasRelatedSongs(song.id)) {
                    quickPicks.value = database.getRelatedSongs(song.id).first().filterVideoSongs(hideVideoSongs).shuffled().take(20)
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
                                .shuffled()
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
                                .shuffled()
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
            launch(Dispatchers.IO) { getDailyDiscover() }
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

        loadLocalDataPhase()
        isLoading.value = false

        loadNetworkDataPhase()
        saveSnapshot()
    }

    /** Cache the loaded home at process level so returning to Home / resuming restores it instantly. */
    private fun saveSnapshot() {
        snapshot = HomeSnapshot(
            quickPicks = quickPicks.value,
            dailyDiscover = dailyDiscover.value,
            forgottenFavorites = forgottenFavorites.value,
            keepListening = keepListening.value,
            similarRecommendations = similarRecommendations.value,
            accountPlaylists = accountPlaylists.value,
            homePage = homePage.value,
            explorePage = explorePage.value,
            communityPlaylists = communityPlaylists.value,
            allLocalItems = allLocalItems.value,
            allYtItems = allYtItems.value,
        )
    }

    companion object {
        // Process-level snapshot of the loaded home. A recreated ViewModel (returning to the Home tab
        // or resuming the app from the background) restores from this INSTANTLY and does NOT auto-reload
        // — after that, refreshing is manual (pull to refresh). Only a cold app start (fresh process =
        // null snapshot) loads from scratch.
        @Volatile
        private var snapshot: HomeSnapshot? = null

        private class HomeSnapshot(
            val quickPicks: List<Song>?,
            val dailyDiscover: List<DailyDiscoverItem>?,
            val forgottenFavorites: List<Song>?,
            val keepListening: List<LocalItem>?,
            val similarRecommendations: List<SimilarRecommendation>?,
            val accountPlaylists: List<PlaylistItem>?,
            val homePage: HomePage?,
            val explorePage: ExplorePage?,
            val communityPlaylists: List<CommunityPlaylistItem>?,
            val allLocalItems: List<LocalItem>,
            val allYtItems: List<YTItem>,
        )
    }

    private val _isLoadingMore = MutableStateFlow(false)
    fun loadMoreYouTubeItems(continuation: String?) {
        if (continuation == null || _isLoadingMore.value) return
        val hideExplicit = context.dataStore.get(HideExplicitKey, false)
        val hideVideoSongs = context.dataStore.get(HideVideoSongsKey, false)
        val hideYoutubeShorts = context.dataStore.get(HideYoutubeShortsKey, false)

        viewModelScope.launch(Dispatchers.IO) {
            _isLoadingMore.value = true
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
                load()
            } finally {
                isRefreshing.value = false
            }
        }
        
        viewModelScope.launch(Dispatchers.IO) {
            syncUtils.tryAutoSync()
        }
    }

    init {

        
        val restored = snapshot
        if (restored != null) {
            // Returning to Home / resuming the app: restore the already-loaded home instantly and DON'T
            // auto-reload (the user refreshes manually). Only a cold app start loads from scratch.
            quickPicks.value = restored.quickPicks
            dailyDiscover.value = restored.dailyDiscover
            forgottenFavorites.value = restored.forgottenFavorites
            keepListening.value = restored.keepListening
            similarRecommendations.value = restored.similarRecommendations
            accountPlaylists.value = restored.accountPlaylists
            homePage.value = restored.homePage
            explorePage.value = restored.explorePage
            communityPlaylists.value = restored.communityPlaylists
            allLocalItems.value = restored.allLocalItems
            allYtItems.value = restored.allYtItems
        } else {
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
