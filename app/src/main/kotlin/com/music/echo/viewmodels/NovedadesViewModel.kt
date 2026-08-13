package iad1tya.echo.music.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.music.innertube.YouTube
import com.music.innertube.models.AlbumItem
import com.music.innertube.models.PlaylistItem
import com.music.innertube.models.SongItem
import com.music.innertube.models.YTItem
import com.music.innertube.pages.ChartsPage
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import iad1tya.echo.music.constants.HideExplicitKey
import iad1tya.echo.music.constants.HideVideoSongsKey
import iad1tya.echo.music.constants.MyTopFilter
import iad1tya.echo.music.constants.SpotifyAccessTokenKey
import iad1tya.echo.music.constants.TopSize
import iad1tya.echo.music.db.MusicDatabase
import iad1tya.echo.music.db.entities.ReleaseRadarItem
import iad1tya.echo.music.db.entities.Song
import iad1tya.echo.music.db.entities.UpcomingReleaseEntity
import iad1tya.echo.music.extensions.filterExplicit
import iad1tya.echo.music.notices.OwnerAnnouncements
import iad1tya.echo.music.releaseradar.ReleaseRadarWorker
import iad1tya.echo.music.spotify.Spotify
import iad1tya.echo.music.utils.dataStore
import iad1tya.echo.music.utils.filterSongsToTasteArtists
import iad1tya.echo.music.utils.filterToSubscribedArtists
import iad1tya.echo.music.utils.filterToTasteArtists
import iad1tya.echo.music.utils.get
import iad1tya.echo.music.utils.SubscribedArtistKeys
import iad1tya.echo.music.utils.tasteArtistKeys
import java.time.LocalDate
import java.time.ZoneOffset
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class NovedadesViewModel
@Inject
constructor(
    @ApplicationContext private val context: Context,
    private val database: MusicDatabase,
) : ViewModel() {

    val radarReleases: StateFlow<List<ReleaseRadarItem>> =
        database.releasesByDateDesc()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val upcoming: StateFlow<List<UpcomingReleaseEntity>> =
        database.upcomingReleases()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val hideFlags = context.dataStore.data
        .map { (it[HideExplicitKey] ?: false) to (it[HideVideoSongsKey] ?: false) }
        .distinctUntilChanged()

    val topSongs: StateFlow<List<Song>> =
        combine(
            context.dataStore.data.map { it[TopSize] ?: "50" }.distinctUntilChanged(),
            hideFlags,
        ) { size, flags -> size to flags }
            .flatMapLatest { (size, flags) ->
                val (hideExplicit, hideVideoSongs) = flags
                val limit = size.toIntOrNull()?.coerceIn(1, 200) ?: 50
                database.mostPlayedSongs(0L, limit).map { songs ->
                    songs
                        .let { if (hideExplicit) it.filterExplicit() else it }
                        .let { if (hideVideoSongs) it.filter { song -> !song.song.isVideo } else it }
                }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val momentSongs: StateFlow<List<Song>> =
        hideFlags.flatMapLatest { flags ->
            val (hideExplicit, hideVideoSongs) = flags
            database.mostPlayedSongs(MyTopFilter.WEEK.toTimeMillis(), limit = 12).map { songs ->
                songs
                    .let { if (hideExplicit) it.filterExplicit() else it }
                    .let { if (hideVideoSongs) it.filter { song -> !song.song.isVideo } else it }
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _newAlbums = MutableStateFlow<List<AlbumItem>>(emptyList())
    val newAlbums: StateFlow<List<AlbumItem>> = _newAlbums

    private val _featuredSongs = MutableStateFlow<List<SongItem>>(emptyList())
    val featuredSongs: StateFlow<List<SongItem>> = _featuredSongs

    private val _listening = MutableStateFlow<List<YTItem>>(emptyList())
    val listening: StateFlow<List<YTItem>> = _listening

    private val _updatedPlaylists = MutableStateFlow<List<PlaylistItem>>(emptyList())
    val updatedPlaylists: StateFlow<List<PlaylistItem>> = _updatedPlaylists

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing

    init {
        refresh()
        ReleaseRadarWorker.refreshIfStale(context)
    }

    fun refresh() {
        if (_isRefreshing.value) return
        viewModelScope.launch {
            _isRefreshing.value = true
            try {
                withContext(Dispatchers.IO) { loadFeeds() }
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    fun togglePresave(item: UpcomingReleaseEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            val next = !item.presaved
            database.setUpcomingPresaved(item.id, next)
            if (next) {
                OwnerAnnouncements.recordLocal(
                    context,
                    id = "presave-${item.id}",
                    title = item.title,
                    body = item.artistName,
                    priority = "info",
                )
            }
        }
    }

    private suspend fun loadFeeds() {
        val hideExplicit = context.dataStore.get(HideExplicitKey, false)
        coroutineScope {
            val albumsJob = async {
                YouTube.newReleaseAlbums().getOrNull()
                    ?.let { if (hideExplicit) it.filter { a -> !a.explicit } else it }
                    .orEmpty()
            }
            val chartsJob = async { YouTube.getChartsPage().getOrNull() }
            val libraryJob = async { YouTube.library("FEmusic_liked_playlists").getOrNull() }
            val upcomingJob = async { scanUpcoming() }

            val albums = albumsJob.await()
            val keys = database.tasteArtistKeys()
            _newAlbums.value = albums.filterToSubscribedArtists(keys).take(20)

            val charts = chartsJob.await()
            if (charts != null) applyCharts(charts, keys)
            else {
                _featuredSongs.value = emptyList()
                _listening.value = emptyList()
            }

            val playlists = libraryJob.await()?.items?.filterIsInstance<PlaylistItem>().orEmpty()
            if (playlists.isNotEmpty()) _updatedPlaylists.value = playlists.take(16)

            upcomingJob.await()
        }
    }

    private fun applyCharts(page: ChartsPage, keys: SubscribedArtistKeys) {
        val songs = page.sections
            .flatMap { it.items }
            .filterIsInstance<SongItem>()
            .distinctBy { it.id }
            .filterSongsToTasteArtists(keys)
        _featuredSongs.value = songs.take(12)
        val featuredIds = _featuredSongs.value.map { it.id }.toSet()
        val mix = page.sections
            .firstOrNull { it.chartType == ChartsPage.ChartType.TRENDING || it.chartType == ChartsPage.ChartType.TOP }
            ?.items
            .orEmpty()
            .ifEmpty { page.sections.firstOrNull()?.items.orEmpty() }
            .filterToTasteArtists(keys)
            .filterNot { it.id in featuredIds }
        _listening.value = mix.take(16)
    }

    private suspend fun scanUpcoming() {
        val token = context.dataStore.data.first()[SpotifyAccessTokenKey].orEmpty()
        if (token.isBlank()) return
        val today = LocalDate.now()
        val existing = database.upcomingReleases().first().associateBy { it.id }
        val semaphore = Semaphore(4)
        val followed = runCatching {
            val page = Spotify.myArtists(limit = 20, offset = 0).getOrThrow()
            page.items
        }.getOrDefault(emptyList())
        if (followed.isEmpty()) return
        val rows = coroutineScope {
            followed.take(12).map { artist ->
                async {
                    semaphore.withPermit {
                        val disco = runCatching { Spotify.artistDiscography(artist.id).getOrThrow() }
                            .getOrDefault(emptyList())
                        disco.mapNotNull { album ->
                            val date = runCatching {
                                album.releaseDate?.take(10)?.let { LocalDate.parse(it) }
                            }.getOrNull() ?: return@mapNotNull null
                            if (!date.isAfter(today)) return@mapNotNull null
                            val id = album.id.ifBlank { "${artist.id}|${album.name}" }
                            val prev = existing[id]
                            UpcomingReleaseEntity(
                                id = id,
                                artistId = artist.id,
                                artistName = artist.name,
                                title = album.name,
                                releaseEpochMs = date.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli(),
                                artworkUri = album.images.firstOrNull()?.url ?: prev?.artworkUri,
                                youtubeBrowseId = prev?.youtubeBrowseId,
                                presaved = prev?.presaved == true,
                            )
                        }
                    }
                }
            }.awaitAll().flatten()
        }
        database.prunePastUpcoming(System.currentTimeMillis())
        if (rows.isNotEmpty()) database.upsertUpcomingReleases(rows)
    }
}
