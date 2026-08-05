package iad1tya.echo.music.ui.newui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import com.music.innertube.models.PlaylistItem
import com.music.innertube.models.WatchEndpoint
import iad1tya.echo.music.LocalPlayerAwareWindowInsets
import iad1tya.echo.music.LocalPlayerConnection
import iad1tya.echo.music.R
import iad1tya.echo.music.constants.AlbumFilter
import iad1tya.echo.music.constants.AlbumFilterKey
import iad1tya.echo.music.constants.AlbumSortDescendingKey
import iad1tya.echo.music.constants.AlbumSortType
import iad1tya.echo.music.constants.AlbumSortTypeKey
import iad1tya.echo.music.constants.ArtistFilter
import iad1tya.echo.music.constants.ArtistFilterKey
import iad1tya.echo.music.constants.ArtistSortDescendingKey
import iad1tya.echo.music.constants.ArtistSortType
import iad1tya.echo.music.constants.ArtistSortTypeKey
import iad1tya.echo.music.constants.HideExplicitKey
import iad1tya.echo.music.constants.MixSortDescendingKey
import iad1tya.echo.music.constants.MixSortType
import iad1tya.echo.music.constants.MixSortTypeKey
import iad1tya.echo.music.constants.PlaylistSortDescendingKey
import iad1tya.echo.music.constants.PlaylistSortType
import iad1tya.echo.music.constants.PlaylistSortTypeKey
import iad1tya.echo.music.constants.ShowCachedPlaylistKey
import iad1tya.echo.music.constants.ShowDownloadedPlaylistKey
import iad1tya.echo.music.constants.ShowExportedPlaylistKey
import iad1tya.echo.music.constants.ShowLikedPlaylistKey
import iad1tya.echo.music.constants.ShowTopPlaylistKey
import iad1tya.echo.music.constants.ShowUploadedPlaylistKey
import iad1tya.echo.music.constants.SongFilter
import iad1tya.echo.music.constants.SongFilterKey
import iad1tya.echo.music.constants.SongSortDescendingKey
import iad1tya.echo.music.constants.SongSortType
import iad1tya.echo.music.constants.SongSortTypeKey
import iad1tya.echo.music.constants.YtmSyncKey
import iad1tya.echo.music.db.entities.Album
import iad1tya.echo.music.db.entities.Artist
import iad1tya.echo.music.db.entities.Playlist
import iad1tya.echo.music.extensions.toMediaItem
import iad1tya.echo.music.playback.queues.ListQueue
import iad1tya.echo.music.ui.component.EnhancedShuffleChip
import iad1tya.echo.music.ui.component.LocalMenuState
import iad1tya.echo.music.ui.component.rememberPlayedShuffleSet
import iad1tya.echo.music.ui.component.rememberShuffleMemoryPrompt
import iad1tya.echo.music.ui.menu.AlbumMenu
import iad1tya.echo.music.ui.menu.ArtistMenu
import iad1tya.echo.music.ui.menu.PlaylistMenu
import iad1tya.echo.music.ui.menu.SongMenu
import iad1tya.echo.music.ui.menu.YouTubePlaylistMenu
import iad1tya.echo.music.utils.rememberEnumPreference
import iad1tya.echo.music.utils.rememberPreference
import iad1tya.echo.music.viewmodels.LibraryAlbumsViewModel
import iad1tya.echo.music.viewmodels.LibraryArtistsViewModel
import iad1tya.echo.music.viewmodels.LibraryMixViewModel
import iad1tya.echo.music.viewmodels.LibraryPlaylistsViewModel
import iad1tya.echo.music.viewmodels.LibrarySongsViewModel
import java.text.Collator
import java.time.LocalDateTime
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The five tabs of the new Biblioteca.
 *
 * Each one holds the SAME `hiltViewModel()` the classic tab holds, reads the SAME sort/filter/view
 * preference keys, and plays through the SAME `ListQueue` with the SAME `contextId` — so the
 * "Aleatorio mejorado" no-repeat memory is one shared memory, not a second one that would make songs
 * repeat the moment the user toggles the interface.
 */

// ── Shared tab furniture ──────────────────────────────────────────────────────────────────────────

/**
 * The sort control: the current criterion opens a menu, the arrow flips ascending/descending.
 * The menu itself is a plain `DropdownMenu` — popups are not one of the six screens.
 */
@Composable
private fun <T : Enum<T>> AuraSortControl(
    sortType: T,
    sortDescending: Boolean,
    options: List<Pair<T, Int>>,
    onSortTypeChange: (T) -> Unit,
    onSortDescendingChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
) {
    var expanded by remember { mutableStateOf(false) }
    val currentLabelRes = options.firstOrNull { it.first == sortType }?.second
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = modifier
            .fillMaxWidth()
            .padding(start = AuraSpacing.Gutter, end = 6.dp, top = 12.dp),
    ) {
        Box {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier
                    // The pill DRAWS at the render's height (~34 dp with a 14 dp glyph); the finger gets
                    // the 48 dp Android requires. minimumInteractiveComponentSize expands the touch
                    // bounds without stretching the background, so the look is unchanged.
                    .minimumInteractiveComponentSize()
                    .clip(AuraShapes.Pill)
                    .background(AuraPalette.SurfaceFill)
                    .auraClickableInternal(
                        onClick = { expanded = true },
                        contentDescription = "Cambiar el criterio de orden",
                    )
                    .padding(horizontal = 14.dp, vertical = 9.dp),
            ) {
                Text(
                    text = currentLabelRes?.let { stringResource(it) } ?: sortType.name,
                    style = AuraType.Chip,
                    color = AuraPalette.OnGround,
                    maxLines = 1,
                    overflow = AuraDefaultOverflow,
                )
                AuraIconGlyph(
                    icon = AuraIcons.ChevronDown,
                    contentDescription = null,
                    size = 14.dp,
                    tint = AuraPalette.OnGroundFaint,
                )
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                options.forEach { (option, labelRes) ->
                    DropdownMenuItem(
                        text = { Text(stringResource(labelRes)) },
                        onClick = {
                            onSortTypeChange(option)
                            expanded = false
                        },
                    )
                }
            }
        }
        AuraIconButton(
            icon = AuraIcons.ChevronDown,
            // The classic header hardcodes "Toggle sort order" in English; the new UI owns this string
            // and the app's user-facing language is Spanish.
            contentDescription = "Invertir el orden",
            onClick = { onSortDescendingChange(!sortDescending) },
            size = 16.dp,
            tint = AuraPalette.OnGroundFaint,
            modifier = Modifier.graphicsLayer { rotationZ = if (sortDescending) 0f else 180f },
        )
        Box(Modifier.weight(1f))
        trailing?.invoke()
    }
}

/** A count line: "%d canciones", "%d álbumes"… kept as the same plural resource. */
@Composable
private fun AuraCount(text: String, modifier: Modifier = Modifier) {
    AuraTechnicalText(
        text = text.uppercase(Locale.ROOT),
        modifier = modifier.padding(end = 12.dp),
        color = AuraPalette.OnGroundGhost,
    )
}

/** The in-tab search field, shown when the header's search glyph is toggled on. */
@Composable
private fun AuraSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = AuraSpacing.Gutter, vertical = 10.dp)
            .clip(AuraShapes.Card)
            .background(AuraPalette.SurfaceFill)
            .border(1.dp, AuraPalette.SurfaceLine, AuraShapes.Card)
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        AuraIconGlyph(
            icon = AuraIcons.Search,
            contentDescription = null,
            size = 16.dp,
            tint = AuraPalette.OnGroundFaint,
        )
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (value.isEmpty()) {
                Text(
                    text = placeholder,
                    style = AuraType.RowSubtitle,
                    color = AuraPalette.OnGroundGhost,
                    maxLines = 1,
                    overflow = AuraDefaultOverflow,
                )
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = AuraType.RowSubtitle.copy(color = AuraPalette.OnGround),
                cursorBrush = SolidColor(AuraPalette.Teal),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (value.isNotEmpty()) {
            AuraIconButton(
                // A "+" turned 45° is the render's own close glyph — no separate icon needed.
                icon = AuraIcons.Plus,
                contentDescription = "Borrar la búsqueda",
                onClick = { onValueChange("") },
                size = 16.dp,
                tint = AuraPalette.OnGroundFaint,
                modifier = Modifier.graphicsLayer { rotationZ = 45f },
            )
        }
    }
}

// ── 1. Hub ────────────────────────────────────────────────────────────────────────────────────────

/** Biblioteca › hub: the auto-playlist tiles, then the followed artists, then playlists and albums. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuraLibraryHub(
    navController: NavController,
    viewModel: LibraryMixViewModel = hiltViewModel(),
) {
    val menuState = LocalMenuState.current
    val haptic = LocalHapticFeedback.current
    val playerConnection = LocalPlayerConnection.current ?: return
    val isPlaying by playerConnection.isEffectivelyPlaying.collectAsState()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsState()
    val coroutineScope = rememberCoroutineScope()

    val (sortType, onSortTypeChange) = rememberEnumPreference(MixSortTypeKey, MixSortType.CREATE_DATE)
    val (sortDescending, onSortDescendingChange) = rememberPreference(MixSortDescendingKey, true)
    val (ytmSync) = rememberPreference(YtmSyncKey, true)
    val topSize by viewModel.topValue.collectAsState(initial = 50)

    val (showLiked) = rememberPreference(ShowLikedPlaylistKey, true)
    val (showDownloaded) = rememberPreference(ShowDownloadedPlaylistKey, true)
    val (showExported) = rememberPreference(ShowExportedPlaylistKey, true)
    val (showTop) = rememberPreference(ShowTopPlaylistKey, true)
    val (showCached) = rememberPreference(ShowCachedPlaylistKey, true)
    val (showUploaded) = rememberPreference(ShowUploadedPlaylistKey, true)

    val albums by viewModel.albums.collectAsState()
    val artists by viewModel.artists.collectAsState()
    val playlists by viewModel.playlists.collectAsState()

    val allItems = remember(albums, artists, playlists, sortType, sortDescending) {
        val collator = Collator.getInstance(Locale.getDefault()).apply { strength = Collator.PRIMARY }
        val merged = albums + artists + playlists
        val sorted = when (sortType) {
            MixSortType.CREATE_DATE -> merged.sortedBy { item ->
                when (item) {
                    is Album -> item.album.bookmarkedAt
                    is Artist -> item.artist.bookmarkedAt
                    is Playlist -> item.playlist.createdAt
                    else -> LocalDateTime.now()
                }
            }

            MixSortType.NAME -> merged.sortedWith(
                compareBy(collator) { item ->
                    when (item) {
                        is Album -> item.album.title
                        is Artist -> item.artist.name
                        is Playlist -> item.playlist.name
                        else -> ""
                    }
                },
            )

            MixSortType.LAST_UPDATED -> merged.sortedBy { item ->
                when (item) {
                    is Album -> item.album.lastUpdateTime
                    is Artist -> item.artist.lastUpdateTime
                    is Playlist -> item.playlist.lastUpdateTime
                    else -> LocalDateTime.now()
                }
            }
        }
        if (sortDescending) sorted.reversed() else sorted
    }
    val artistItems = allItems.filterIsInstance<Artist>().distinctBy { it.id }
    val nonArtistItems = allItems.filterNot { it is Artist }.distinctBy { it.id }

    LaunchedEffect(Unit) {
        if (ytmSync) withContext(Dispatchers.IO) { viewModel.syncAllLibrary() }
    }

    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val pullRefreshState = rememberPullToRefreshState()

    PullToRefreshBox(
        state = pullRefreshState,
        isRefreshing = isRefreshing,
        onRefresh = viewModel::refresh,
        indicator = {
            PullToRefreshDefaults.LoadingIndicator(
                state = pullRefreshState,
                isRefreshing = isRefreshing,
                modifier = Modifier.align(Alignment.TopCenter),
            )
        },
    ) {
        LazyColumn(
            state = rememberLazyListState(),
            contentPadding = LocalPlayerAwareWindowInsets.current.asPaddingValues(),
            modifier = Modifier.fillMaxSize(),
        ) {
            item(key = "aura_hub_sort") {
                AuraSortControl(
                    sortType = sortType,
                    sortDescending = sortDescending,
                    options = listOf(
                        MixSortType.CREATE_DATE to R.string.sort_by_create_date,
                        MixSortType.LAST_UPDATED to R.string.sort_by_last_updated,
                        MixSortType.NAME to R.string.sort_by_name,
                    ),
                    onSortTypeChange = onSortTypeChange,
                    onSortDescendingChange = onSortDescendingChange,
                )
            }

            item(key = "aura_hub_auto_playlists") {
                FlowRow(
                    maxItemsInEachRow = 2,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = AuraSpacing.Gutter, vertical = 12.dp),
                ) {
                    val tile = Modifier.weight(1f)
                    if (showLiked) {
                        AuraTile(AuraIcons.HeartFilled, stringResource(R.string.liked),
                            { navController.navigate("auto_playlist/liked") }, tile)
                    }
                    if (showDownloaded) {
                        AuraTile(AuraIcons.Download, stringResource(R.string.offline),
                            { navController.navigate("auto_playlist/downloaded") }, tile)
                    }
                    if (showExported) {
                        AuraTile(AuraIcons.Export, stringResource(R.string.action_exported),
                            { navController.navigate("auto_playlist/exported") }, tile)
                    }
                    if (showCached) {
                        AuraTile(AuraIcons.Queue, stringResource(R.string.cached_playlist),
                            { navController.navigate("cache_playlist/cached") }, tile)
                    }
                    if (showUploaded) {
                        AuraTile(AuraIcons.Export, stringResource(R.string.uploaded_playlist),
                            { navController.navigate("auto_playlist/uploaded") }, tile)
                    }
                    if (showTop) {
                        AuraTile(AuraIcons.Speed, stringResource(R.string.my_top) + " $topSize",
                            { navController.navigate("top_playlist/$topSize") }, tile)
                    }
                    AuraTile(AuraIcons.Album, stringResource(R.string.favorite_albums),
                        { navController.navigate("favorite_albums") }, tile)
                    AuraTile(AuraIcons.Radio, stringResource(R.string.release_radar_title),
                        { navController.navigate("release_radar") }, tile)
                    AuraTile(AuraIcons.Lyrics, stringResource(R.string.podcasts),
                        { navController.navigate("podcasts") }, tile)
                    AuraTile(AuraIcons.Library, stringResource(R.string.filter_local),
                        { navController.navigate("local_songs") }, tile)
                }
            }

            if (artistItems.isNotEmpty()) {
                item(key = "aura_hub_artists_header") {
                    AuraSectionHeader(title = stringResource(R.string.filter_artists))
                }
                items(artistItems, key = { "artist_${it.id}" }) { item ->
                    AuraSongRow(
                        title = item.artist.name,
                        subtitle = pluralStringResource(R.plurals.n_song, item.songCount, item.songCount),
                        thumbnailUrl = item.artist.thumbnailUrl,
                        seed = item.id,
                        onClick = { navController.navigate("artist/${item.id}") },
                        onLongClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            menuState.show {
                                ArtistMenu(item, coroutineScope, menuState::dismiss)
                            }
                        },
                        onMenuClick = {
                            menuState.show { ArtistMenu(item, coroutineScope, menuState::dismiss) }
                        },
                        modifier = Modifier.padding(horizontal = AuraSpacing.Gutter),
                    )
                }
            }

            item(key = "aura_hub_playlists_header") {
                AuraSectionHeader(title = stringResource(R.string.filter_playlists))
            }
            items(nonArtistItems, key = { it.id }) { item ->
                when (item) {
                    is Playlist -> AuraSongRow(
                        title = item.playlist.name,
                        subtitle = pluralStringResource(
                            R.plurals.n_song,
                            item.songCount,
                            item.songCount,
                        ),
                        thumbnailUrl = item.thumbnails.firstOrNull(),
                        seed = item.id,
                        onClick = { navController.navigate("local_playlist/${item.id}") },
                        onLongClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            menuState.show { PlaylistMenu(item, coroutineScope, menuState::dismiss) }
                        },
                        onMenuClick = {
                            menuState.show { PlaylistMenu(item, coroutineScope, menuState::dismiss) }
                        },
                        modifier = Modifier.padding(horizontal = AuraSpacing.Gutter),
                    )

                    is Album -> AuraSongRow(
                        title = item.album.title,
                        subtitle = item.artists.joinToString { it.name },
                        thumbnailUrl = item.album.thumbnailUrl,
                        seed = item.id,
                        isActive = item.id == mediaMetadata?.album?.id,
                        isPlaying = isPlaying,
                        onClick = { navController.navigate("album/${item.id}") },
                        onLongClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            menuState.show {
                                AlbumMenu(item, navController, menuState::dismiss)
                            }
                        },
                        onMenuClick = {
                            menuState.show { AlbumMenu(item, navController, menuState::dismiss) }
                        },
                        modifier = Modifier.padding(horizontal = AuraSpacing.Gutter),
                    )

                    else -> Unit
                }
            }
        }
    }
}

// ── 2. Canciones ──────────────────────────────────────────────────────────────────────────────────

/**
 * Biblioteca › Canciones — the tab the render actually draws: filter chips, rows, quality badge on the
 * right, download tick, and the shuffle action.
 */
@Composable
fun AuraLibrarySongsTab(
    navController: NavController,
    searchOpen: Boolean,
    viewModel: LibrarySongsViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val menuState = LocalMenuState.current
    val haptic = LocalHapticFeedback.current
    val playerConnection = LocalPlayerConnection.current ?: return
    val isPlaying by playerConnection.isEffectivelyPlaying.collectAsState()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsState()

    val (sortType, onSortTypeChange) = rememberEnumPreference(SongSortTypeKey, SongSortType.CREATE_DATE)
    val (sortDescending, onSortDescendingChange) = rememberPreference(SongSortDescendingKey, true)
    val (ytmSync) = rememberPreference(YtmSyncKey, true)
    val hideExplicit by rememberPreference(HideExplicitKey, false)
    var filter by rememberEnumPreference(SongFilterKey, SongFilter.LIKED)

    val songs by viewModel.allSongs.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()

    LaunchedEffect(Unit) {
        if (ytmSync) {
            when (filter) {
                SongFilter.LIKED -> viewModel.syncLikedSongs()
                SongFilter.LIBRARY -> viewModel.syncLibrarySongs()
                SongFilter.UPLOADED -> viewModel.syncUploadedSongs()
                else -> return@LaunchedEffect
            }
        }
    }

    val filteredSongs = songs
        .let { list -> if (hideExplicit) list.filter { !it.song.explicit } else list }
        .let { list ->
            if (searchQuery.isBlank()) list
            else list.filter { it.song.title.contains(searchQuery, ignoreCase = true) }
        }

    // Identical bucket derivation to the classic tab — the no-repeat memory must be ONE memory.
    val canonicalCollectionId = when (filter) {
        SongFilter.LIKED -> "AP:liked"
        SongFilter.DOWNLOADED -> "AP:downloaded"
        SongFilter.UPLOADED -> "AP:uploaded"
        SongFilter.EXPORTED -> "AP:exported"
        SongFilter.LIBRARY -> "LIB:LIBRARY"
    }
    val libraryContextId = when {
        searchQuery.isNotBlank() -> null
        hideExplicit && filter == SongFilter.LIBRARY -> "$canonicalCollectionId:NX"
        else -> canonicalCollectionId
    }
    val shufflePlayedSet = rememberPlayedShuffleSet(libraryContextId)

    val listState = rememberLazyListState()

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            contentPadding = LocalPlayerAwareWindowInsets.current.asPaddingValues(),
            modifier = Modifier.fillMaxSize(),
        ) {
            item(key = "aura_songs_subfilters") {
                AuraSubFilterRow(
                    options = listOf(
                        SongFilter.LIKED to R.string.filter_liked,
                        SongFilter.LIBRARY to R.string.filter_library,
                        SongFilter.UPLOADED to R.string.filter_uploaded,
                        SongFilter.DOWNLOADED to R.string.filter_downloaded,
                        SongFilter.EXPORTED to R.string.action_exported,
                    ),
                    selected = filter,
                    onSelect = { filter = it },
                )
            }

            if (searchOpen) {
                item(key = "aura_songs_search") {
                    AuraSearchField(
                        value = searchQuery,
                        onValueChange = { viewModel.searchQuery.value = it },
                        placeholder = stringResource(R.string.search_library),
                    )
                }
            }

            item(key = "aura_songs_sort") {
                AuraSortControl(
                    sortType = sortType,
                    sortDescending = sortDescending,
                    options = listOf(
                        SongSortType.CREATE_DATE to R.string.sort_by_create_date,
                        SongSortType.NAME to R.string.sort_by_name,
                        SongSortType.ARTIST to R.string.sort_by_artist,
                        SongSortType.PLAY_TIME to R.string.sort_by_play_time,
                    ),
                    onSortTypeChange = onSortTypeChange,
                    onSortDescendingChange = onSortDescendingChange,
                    trailing = {
                        AuraCount(
                            pluralStringResource(
                                R.plurals.n_song,
                                filteredSongs.size,
                                filteredSongs.size,
                            ),
                        )
                    },
                )
            }

            if (libraryContextId != null) {
                item(key = "aura_songs_shuffle_chip") {
                    val playedCount = remember(shufflePlayedSet, filteredSongs) {
                        filteredSongs.count { it.id in shufflePlayedSet }
                    }
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .padding(top = 10.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        EnhancedShuffleChip(playedCount = playedCount, total = filteredSongs.size)
                    }
                }
            }

            itemsIndexed(filteredSongs, key = { _, item -> item.song.id }) { index, song ->
                AuraSongRow(
                    title = song.song.title,
                    subtitle = song.artists.joinToString { it.name },
                    thumbnailUrl = song.song.thumbnailUrl,
                    seed = song.id,
                    isActive = song.id == mediaMetadata?.id,
                    isPlaying = isPlaying,
                    liked = song.song.liked,
                    explicit = song.song.explicit,
                    inLibrary = song.song.inLibrary != null,
                    // The DOWNLOADED tab is all downloads: the tick there would be noise, as today.
                    downloadId = if (filter != SongFilter.DOWNLOADED) song.id else null,
                    format = song.format,
                    playedInShuffle = song.id in shufflePlayedSet,
                    swipeMediaItem = song.toMediaItem(),
                    onClick = {
                        if (song.id == mediaMetadata?.id) {
                            playerConnection.togglePlayPause()
                        } else {
                            playerConnection.playQueue(
                                ListQueue(
                                    title = context.getString(R.string.queue_all_songs),
                                    items = filteredSongs.map { it.toMediaItem() },
                                    startIndex = index,
                                    contextId = libraryContextId,
                                ),
                            )
                        }
                    },
                    onLongClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        menuState.show {
                            SongMenu(
                                originalSong = song,
                                navController = navController,
                                onDismiss = menuState::dismiss,
                            )
                        }
                    },
                    onMenuClick = {
                        menuState.show { SongMenu(
                                originalSong = song,
                                navController = navController,
                                onDismiss = menuState::dismiss,
                            ) }
                    },
                    modifier = Modifier.padding(horizontal = AuraSpacing.Gutter),
                )
            }
        }

        // Shuffle: the SAME memory prompt and the SAME unplayed-first ordering as the classic tab.
        val onShuffleClick = rememberShuffleMemoryPrompt(
            contextId = libraryContextId,
            playedCount = filteredSongs.count { it.id in shufflePlayedSet },
            totalCount = filteredSongs.size,
        ) { resetMemory ->
            val ordered = if (resetMemory) {
                filteredSongs.shuffled()
            } else {
                val (unheard, heard) = filteredSongs.partition { it.id !in shufflePlayedSet }
                unheard.shuffled() + heard.shuffled()
            }
            playerConnection.playQueue(
                ListQueue(
                    title = context.getString(R.string.queue_all_songs),
                    items = ordered.map { it.toMediaItem() },
                    contextId = libraryContextId,
                    startShuffled = true,
                ),
            )
        }

        if (filteredSongs.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 20.dp, bottom = 20.dp)
                    .clip(CircleShape)
                    .background(AuraPalette.PlayButtonGradient)
                    .auraClickableInternal(
                        onClick = onShuffleClick,
                        contentDescription = stringResource(R.string.shuffle),
                    )
                    .padding(16.dp),
            ) {
                AuraIconGlyph(
                    icon = AuraIcons.Shuffle,
                    contentDescription = null,
                    size = 24.dp,
                    tint = AuraPalette.OnAccent,
                )
            }
        }
    }
}

// ── 3. Álbumes ────────────────────────────────────────────────────────────────────────────────────

@Composable
fun AuraLibraryAlbumsTab(
    navController: NavController,
    viewModel: LibraryAlbumsViewModel = hiltViewModel(),
) {
    val menuState = LocalMenuState.current
    val haptic = LocalHapticFeedback.current
    val playerConnection = LocalPlayerConnection.current ?: return
    val isPlaying by playerConnection.isEffectivelyPlaying.collectAsState()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsState()

    var filter by rememberEnumPreference(AlbumFilterKey, AlbumFilter.LIKED)
    val (sortType, onSortTypeChange) = rememberEnumPreference(AlbumSortTypeKey, AlbumSortType.CREATE_DATE)
    val (sortDescending, onSortDescendingChange) = rememberPreference(AlbumSortDescendingKey, true)
    val (ytmSync) = rememberPreference(YtmSyncKey, true)

    val albums by viewModel.allAlbums.collectAsState()

    LaunchedEffect(Unit) {
        if (ytmSync) withContext(Dispatchers.IO) { viewModel.sync() }
    }

    LazyColumn(
        state = rememberLazyListState(),
        contentPadding = LocalPlayerAwareWindowInsets.current.asPaddingValues(),
        modifier = Modifier.fillMaxSize(),
    ) {
        item(key = "aura_albums_subfilters") {
            AuraSubFilterRow(
                options = listOf(
                    AlbumFilter.LIKED to R.string.filter_liked,
                    AlbumFilter.LIBRARY to R.string.filter_library,
                    AlbumFilter.UPLOADED to R.string.filter_uploaded,
                ),
                selected = filter,
                onSelect = { filter = it },
            )
        }
        item(key = "aura_albums_sort") {
            AuraSortControl(
                sortType = sortType,
                sortDescending = sortDescending,
                options = listOf(
                    AlbumSortType.CREATE_DATE to R.string.sort_by_create_date,
                    AlbumSortType.NAME to R.string.sort_by_name,
                    AlbumSortType.ARTIST to R.string.sort_by_artist,
                    AlbumSortType.YEAR to R.string.sort_by_year,
                    AlbumSortType.SONG_COUNT to R.string.sort_by_song_count,
                    AlbumSortType.LENGTH to R.string.sort_by_length,
                    AlbumSortType.PLAY_TIME to R.string.sort_by_play_time,
                ),
                onSortTypeChange = onSortTypeChange,
                onSortDescendingChange = onSortDescendingChange,
                trailing = {
                    AuraCount(pluralStringResource(R.plurals.n_album, albums.size, albums.size))
                },
            )
        }

        if (albums.isEmpty()) {
            item(key = "aura_albums_empty") {
                AuraEmpty(text = stringResource(R.string.library_album_empty))
            }
        }

        items(albums, key = { it.id }) { album ->
            AuraSongRow(
                title = album.album.title,
                subtitle = album.artists.joinToString { it.name },
                thumbnailUrl = album.album.thumbnailUrl,
                seed = album.id,
                isActive = album.id == mediaMetadata?.album?.id,
                isPlaying = isPlaying,
                onClick = { navController.navigate("album/${album.id}") },
                onLongClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    menuState.show { AlbumMenu(album, navController, menuState::dismiss) }
                },
                onMenuClick = {
                    menuState.show { AlbumMenu(album, navController, menuState::dismiss) }
                },
                modifier = Modifier.padding(horizontal = AuraSpacing.Gutter),
            )
        }
    }
}

// ── 4. Artistas ───────────────────────────────────────────────────────────────────────────────────

@Composable
fun AuraLibraryArtistsTab(
    navController: NavController,
    searchOpen: Boolean,
    viewModel: LibraryArtistsViewModel = hiltViewModel(),
) {
    val menuState = LocalMenuState.current
    val haptic = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()

    var filter by rememberEnumPreference(ArtistFilterKey, ArtistFilter.LIKED)
    val (sortType, onSortTypeChange) = rememberEnumPreference(ArtistSortTypeKey, ArtistSortType.CREATE_DATE)
    val (sortDescending, onSortDescendingChange) = rememberPreference(ArtistSortDescendingKey, true)
    val (ytmSync) = rememberPreference(YtmSyncKey, true)

    val searchQuery by viewModel.searchQuery.collectAsState()
    val artists by viewModel.filteredArtists.collectAsState()

    LaunchedEffect(Unit) {
        if (ytmSync) withContext(Dispatchers.IO) { viewModel.sync() }
    }

    LazyColumn(
        state = rememberLazyListState(),
        contentPadding = LocalPlayerAwareWindowInsets.current.asPaddingValues(),
        modifier = Modifier.fillMaxSize(),
    ) {
        item(key = "aura_artists_subfilters") {
            AuraSubFilterRow(
                options = listOf(
                    ArtistFilter.LIKED to R.string.filter_liked,
                    ArtistFilter.LIBRARY to R.string.filter_library,
                ),
                selected = filter,
                onSelect = { filter = it },
            )
        }
        if (searchOpen) {
            item(key = "aura_artists_search") {
                AuraSearchField(
                    value = searchQuery,
                    onValueChange = { viewModel.searchQuery.value = it },
                    placeholder = stringResource(R.string.search_library),
                )
            }
        }
        item(key = "aura_artists_sort") {
            AuraSortControl(
                sortType = sortType,
                sortDescending = sortDescending,
                options = listOf(
                    ArtistSortType.CREATE_DATE to R.string.sort_by_create_date,
                    ArtistSortType.NAME to R.string.sort_by_name,
                    ArtistSortType.SONG_COUNT to R.string.sort_by_song_count,
                    ArtistSortType.PLAY_TIME to R.string.sort_by_play_time,
                ),
                onSortTypeChange = onSortTypeChange,
                onSortDescendingChange = onSortDescendingChange,
                trailing = {
                    AuraCount(pluralStringResource(R.plurals.n_artist, artists.size, artists.size))
                },
            )
        }

        if (artists.isEmpty()) {
            item(key = "aura_artists_empty") {
                AuraEmpty(
                    text = if (searchQuery.isNotEmpty()) {
                        stringResource(R.string.no_results_found)
                    } else {
                        stringResource(R.string.library_artist_empty)
                    },
                )
            }
        }

        items(artists, key = { it.id }) { artist ->
            AuraSongRow(
                title = artist.artist.name,
                subtitle = pluralStringResource(R.plurals.n_song, artist.songCount, artist.songCount),
                thumbnailUrl = artist.artist.thumbnailUrl,
                seed = artist.id,
                onClick = { navController.navigate("artist/${artist.id}") },
                onLongClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    menuState.show { ArtistMenu(artist, coroutineScope, menuState::dismiss) }
                },
                onMenuClick = {
                    menuState.show { ArtistMenu(artist, coroutineScope, menuState::dismiss) }
                },
                modifier = Modifier.padding(horizontal = AuraSpacing.Gutter),
            )
        }
    }
}

// ── 5. Listas ─────────────────────────────────────────────────────────────────────────────────────

@Composable
fun AuraLibraryPlaylistsTab(
    navController: NavController,
    searchOpen: Boolean,
    viewModel: LibraryPlaylistsViewModel = hiltViewModel(),
) {
    val menuState = LocalMenuState.current
    val haptic = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()

    val (sortType, onSortTypeChange) = rememberEnumPreference(PlaylistSortTypeKey, PlaylistSortType.CREATE_DATE)
    val (sortDescending, onSortDescendingChange) = rememberPreference(PlaylistSortDescendingKey, true)
    val (ytmSync) = rememberPreference(YtmSyncKey, true)
    val topSize by viewModel.topValue.collectAsState(initial = 50)

    val (showLiked) = rememberPreference(ShowLikedPlaylistKey, true)
    val (showDownloaded) = rememberPreference(ShowDownloadedPlaylistKey, true)
    val (showExported) = rememberPreference(ShowExportedPlaylistKey, true)
    val (showTop) = rememberPreference(ShowTopPlaylistKey, true)
    val (showCached) = rememberPreference(ShowCachedPlaylistKey, true)
    val (showUploaded) = rememberPreference(ShowUploadedPlaylistKey, true)

    val playlists by viewModel.allPlaylists.collectAsState()
    var playlistSearchQuery by rememberSaveable { mutableStateOf("") }

    // Same diacritic-insensitive match as the classic tab: "cancion" finds "Canción".
    val filteredPlaylists = remember(playlists, playlistSearchQuery) {
        val q = playlistSearchQuery.trim()
        if (q.isEmpty()) playlists
        else {
            fun norm(s: String) =
                java.text.Normalizer.normalize(s.lowercase(), java.text.Normalizer.Form.NFD)
                    .replace(Regex("\\p{Mn}+"), "")
            val nq = norm(q)
            playlists.filter { norm(it.playlist.name).contains(nq) }
        }
    }

    LaunchedEffect(Unit) {
        if (ytmSync) withContext(Dispatchers.IO) { viewModel.sync() }
    }

    LazyVerticalGrid(
        state = rememberLazyGridState(),
        columns = GridCells.Adaptive(minSize = 150.dp),
        contentPadding = LocalPlayerAwareWindowInsets.current.asPaddingValues(),
        horizontalArrangement = Arrangement.spacedBy(11.dp),
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = AuraSpacing.Gutter),
    ) {
        if (searchOpen) {
            item(key = "aura_playlists_search", span = { GridItemSpan(maxLineSpan) }) {
                AuraSearchField(
                    value = playlistSearchQuery,
                    onValueChange = { playlistSearchQuery = it },
                    placeholder = stringResource(R.string.search_playlists),
                    modifier = Modifier.padding(horizontal = 0.dp),
                )
            }
        }
        item(key = "aura_playlists_sort", span = { GridItemSpan(maxLineSpan) }) {
            AuraSortControl(
                sortType = sortType,
                sortDescending = sortDescending,
                options = listOf(
                    PlaylistSortType.CREATE_DATE to R.string.sort_by_create_date,
                    PlaylistSortType.NAME to R.string.sort_by_name,
                    PlaylistSortType.SONG_COUNT to R.string.sort_by_song_count,
                    PlaylistSortType.LAST_UPDATED to R.string.sort_by_last_updated,
                ),
                onSortTypeChange = onSortTypeChange,
                onSortDescendingChange = onSortDescendingChange,
                modifier = Modifier.padding(start = 0.dp),
                trailing = {
                    AuraCount(
                        pluralStringResource(
                            R.plurals.n_playlist,
                            filteredPlaylists.size,
                            filteredPlaylists.size,
                        ),
                    )
                },
            )
        }
        item(key = "aura_playlists_auto", span = { GridItemSpan(maxLineSpan) }) {
            FlowRow(
                maxItemsInEachRow = 2,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
            ) {
                val tile = Modifier.weight(1f)
                if (showLiked) {
                    AuraTile(AuraIcons.HeartFilled, stringResource(R.string.liked),
                        { navController.navigate("auto_playlist/liked") }, tile)
                }
                if (showDownloaded) {
                    AuraTile(AuraIcons.Download, stringResource(R.string.offline),
                        { navController.navigate("auto_playlist/downloaded") }, tile)
                }
                if (showExported) {
                    AuraTile(AuraIcons.Export, stringResource(R.string.action_exported),
                        { navController.navigate("auto_playlist/exported") }, tile)
                }
                if (showCached) {
                    AuraTile(AuraIcons.Queue, stringResource(R.string.cached_playlist),
                        { navController.navigate("cache_playlist/cached") }, tile)
                }
                if (showUploaded) {
                    AuraTile(AuraIcons.Export, stringResource(R.string.uploaded_playlist),
                        { navController.navigate("auto_playlist/uploaded") }, tile)
                }
                if (showTop) {
                    AuraTile(AuraIcons.Speed, stringResource(R.string.my_top) + " $topSize",
                        { navController.navigate("top_playlist/$topSize") }, tile)
                }
            }
        }
        item(key = "aura_playlists_header", span = { GridItemSpan(maxLineSpan) }) {
            AuraSectionHeader(
                title = stringResource(R.string.filter_playlists),
                modifier = Modifier.padding(start = 0.dp),
            )
        }

        items(filteredPlaylists.distinctBy { it.id }, key = { it.id }) { playlist ->
            AuraCoverCard(
                title = playlist.playlist.name,
                subtitle = pluralStringResource(
                    R.plurals.n_song,
                    playlist.songCount,
                    playlist.songCount,
                ),
                thumbnailUrl = playlist.thumbnails.firstOrNull(),
                seed = playlist.id,
                width = 150.dp,
                // Routing copied verbatim from `LibraryPlaylistGridItem`: a saved-but-unfetched YouTube
                // playlist opens ONLINE, everything else opens local. Getting this wrong sends a synced
                // playlist to an empty local screen.
                onClick = {
                    if (!playlist.playlist.isEditable &&
                        playlist.songCount == 0 &&
                        playlist.playlist.remoteSongCount != 0
                    ) {
                        navController.navigate("online_playlist/${playlist.playlist.browseId}")
                    } else {
                        navController.navigate("local_playlist/${playlist.id}")
                    }
                },
                onLongClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    menuState.show {
                        if (playlist.playlist.isEditable || playlist.songCount != 0) {
                            PlaylistMenu(
                                playlist = playlist,
                                coroutineScope = coroutineScope,
                                onDismiss = menuState::dismiss,
                            )
                        } else {
                            playlist.playlist.browseId?.let { browseId ->
                                YouTubePlaylistMenu(
                                    playlist = PlaylistItem(
                                        id = browseId,
                                        title = playlist.playlist.name,
                                        author = null,
                                        songCountText = null,
                                        thumbnail = playlist.thumbnails.getOrNull(0) ?: "",
                                        playEndpoint = WatchEndpoint(
                                            playlistId = browseId,
                                            params = playlist.playlist.playEndpointParams,
                                        ),
                                        shuffleEndpoint = WatchEndpoint(
                                            playlistId = browseId,
                                            params = playlist.playlist.shuffleEndpointParams,
                                        ),
                                        radioEndpoint = WatchEndpoint(
                                            playlistId = "RDAMPL$browseId",
                                            params = playlist.playlist.radioEndpointParams,
                                        ),
                                        isEditable = false,
                                    ),
                                    coroutineScope = coroutineScope,
                                    onDismiss = menuState::dismiss,
                                )
                            }
                        }
                    }
                },
            )
        }
    }
}

// ── Sub-filter chips (Me gusta / Biblioteca / Subido / Descargado / Exportado) ─────────────────────

@Composable
private fun <T> AuraSubFilterRow(
    options: List<Pair<T, Int>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier
            .fillMaxWidth()
            .padding(start = AuraSpacing.Gutter, end = AuraSpacing.Gutter, top = 8.dp),
    ) {
        options.forEach { (option, labelRes) ->
            AuraChip(
                text = stringResource(labelRes),
                selected = selected == option,
                onClick = { onSelect(option) },
            )
        }
    }
}
