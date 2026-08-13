package iad1tya.echo.music.ui.newui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.items as lazyGridItems
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import com.music.innertube.models.AlbumItem
import com.music.innertube.models.Artist
import com.music.innertube.models.ArtistItem
import com.music.innertube.models.PlaylistItem
import com.music.innertube.models.SongItem
import com.music.innertube.models.WatchEndpoint
import com.music.innertube.models.YTItem
import iad1tya.echo.music.LocalPlayerAwareWindowInsets
import iad1tya.echo.music.LocalPlayerConnection
import iad1tya.echo.music.R
import iad1tya.echo.music.constants.HomeRichLayoutKey
import iad1tya.echo.music.constants.TopSize
import iad1tya.echo.music.db.entities.Song
import iad1tya.echo.music.db.entities.UpcomingReleaseEntity
import iad1tya.echo.music.extensions.toMediaItem
import iad1tya.echo.music.models.toMediaMetadata
import iad1tya.echo.music.playback.queues.ListQueue
import iad1tya.echo.music.playback.queues.YouTubeQueue
import iad1tya.echo.music.ui.component.LocalMenuState
import iad1tya.echo.music.ui.menu.SongMenu
import iad1tya.echo.music.ui.menu.YouTubeAlbumMenu
import iad1tya.echo.music.ui.menu.YouTubePlaylistMenu
import iad1tya.echo.music.ui.menu.YouTubeSongMenu
import iad1tya.echo.music.utils.rememberPreference
import iad1tya.echo.music.viewmodels.NovedadesViewModel
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuraNovedadesScreen(
    navController: NavController,
    viewModel: NovedadesViewModel = hiltViewModel(),
) {
    val playerConnection = LocalPlayerConnection.current ?: return
    val menuState = LocalMenuState.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val isPlaying by playerConnection.isEffectivelyPlaying.collectAsState()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsState()

    val radar by viewModel.radarReleases.collectAsState()
    val upcoming by viewModel.upcoming.collectAsState()
    val newAlbums by viewModel.newAlbums.collectAsState()
    val featured by viewModel.featuredSongs.collectAsState()
    val moment by viewModel.momentSongs.collectAsState()
    val listening by viewModel.listening.collectAsState()
    val playlists by viewModel.updatedPlaylists.collectAsState()
    val topSongs by viewModel.topSongs.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val (topSize) = rememberPreference(TopSize, "50")
    val (homeRichLayout) = rememberPreference(HomeRichLayoutKey, true)
    val cardScale = if (homeRichLayout) 1f else 1f / 1.25f

    val radarAlbums = remember(radar) {
        radar.mapNotNull { r ->
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
    }
    val hero = (radarAlbums + newAlbums).distinctBy { it.id }.take(8)

    val pullRefreshState = rememberPullToRefreshState()
    val listState = rememberLazyListState()
    val bloom = rememberAuraBloom(mediaMetadata?.id)

    val openYt: (YTItem) -> Unit = { item ->
        when (item) {
            is SongItem -> playerConnection.playQueue(
                YouTubeQueue(item.endpoint ?: WatchEndpoint(videoId = item.id), item.toMediaMetadata()),
            )
            is AlbumItem -> navController.navigate("album/${item.id}")
            is PlaylistItem -> navController.navigate("online_playlist/${item.id}")
            is ArtistItem -> navController.navigate("artist/${item.id}")
        }
    }
    val ytMenu: (YTItem) -> Unit = { item ->
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        menuState.show {
            when (item) {
                is SongItem -> YouTubeSongMenu(item, navController, menuState::dismiss)
                is AlbumItem -> YouTubeAlbumMenu(item, navController, menuState::dismiss)
                is PlaylistItem -> YouTubePlaylistMenu(
                    playlist = item,
                    coroutineScope = scope,
                    onDismiss = menuState::dismiss,
                )
                else -> {}
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .auraScreenBackground(bloom, intensity = 1f),
    ) {
        PullToRefreshBox(
            state = pullRefreshState,
            isRefreshing = isRefreshing,
            onRefresh = viewModel::refresh,
            indicator = {
                AuraPullRefreshIndicator(state = pullRefreshState, isRefreshing = isRefreshing)
            },
        ) {
            LazyColumn(
                state = listState,
                contentPadding = LocalPlayerAwareWindowInsets.current.asPaddingValues(),
                modifier = Modifier.fillMaxSize(),
            ) {
                item(key = "aura_novedades_header") {
                    AuraScreenHeader(
                        title = stringResource(R.string.tab_novedades),
                        trailing = { AuraTopActions() },
                    )
                }

                if (hero.isNotEmpty()) {
                    item(key = "aura_novedades_hero") {
                        Column(Modifier.padding(top = AuraSpacing.SectionGap)) {
                            AuraSectionHeader(title = stringResource(R.string.tab_novedades))
                            AuraShelf {
                                items(hero, key = { it.id }) { album ->
                                    AuraTypedYtCoverCard(
                                        item = album,
                                        cardScale = cardScale * 1.15f,
                                        isActive = album.id == mediaMetadata?.album?.id,
                                        isPlaying = isPlaying,
                                        onClick = { openYt(album) },
                                        onLongClick = { ytMenu(album) },
                                    )
                                }
                            }
                        }
                    }
                }

                novedadesSongShelf(
                    key = "featured",
                    titleRes = R.string.novedades_featured_songs,
                    songs = featured,
                    cardScale = cardScale,
                    isPlaying = isPlaying,
                    activeId = mediaMetadata?.id,
                    onOpen = openYt,
                    onMenu = ytMenu,
                )
                novedadesAlbumShelf(
                    key = "latest",
                    titleRes = R.string.novedades_latest_albums,
                    albums = newAlbums,
                    cardScale = cardScale,
                    isPlaying = isPlaying,
                    activeAlbumId = mediaMetadata?.album?.id,
                    onOpen = openYt,
                    onMenu = ytMenu,
                )
                novedadesAlbumShelf(
                    key = "recent",
                    titleRes = R.string.novedades_recent_releases,
                    albums = radarAlbums,
                    cardScale = cardScale,
                    isPlaying = isPlaying,
                    activeAlbumId = mediaMetadata?.album?.id,
                    onOpen = openYt,
                    onMenu = ytMenu,
                )
                if (playlists.isNotEmpty()) {
                    item(key = "aura_novedades_playlists") {
                        Column {
                            AuraSectionHeader(title = stringResource(R.string.novedades_updated_playlists))
                            val w = auraTypeVisual(AuraContentKind.Playlist).shelfWidth * cardScale
                            AuraDoubleRowShelf(rowHeight = auraShelfCardStackHeight(w)) {
                                lazyGridItems(playlists, key = { it.id }) { item ->
                                    AuraTypedYtCoverCard(
                                        item = item,
                                        cardScale = cardScale,
                                        onClick = { openYt(item) },
                                        onLongClick = { ytMenu(item) },
                                    )
                                }
                            }
                        }
                    }
                }
                novedadesLocalSongShelf(
                    key = "moment",
                    titleRes = R.string.novedades_songs_of_the_moment,
                    songs = moment,
                    queue = moment,
                    cardScale = cardScale,
                    isPlaying = isPlaying,
                    activeId = mediaMetadata?.id,
                    onPlayAt = { index, queueSongs, title ->
                        playerConnection.playQueue(
                            ListQueue(
                                title = title,
                                items = queueSongs.map { it.toMediaItem() },
                                startIndex = index,
                            ),
                        )
                    },
                    onMenu = { song ->
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        menuState.show {
                            SongMenu(
                                originalSong = song,
                                navController = navController,
                                onDismiss = menuState::dismiss,
                            )
                        }
                    },
                )
                if (listening.isNotEmpty()) {
                    item(key = "aura_novedades_listening") {
                        Column {
                            AuraSectionHeader(title = stringResource(R.string.novedades_everyones_listening))
                            AuraShelf {
                                items(listening, key = { it.id }) { item ->
                                    AuraTypedYtCoverCard(
                                        item = item,
                                        cardScale = cardScale,
                                        isActive = item.id == mediaMetadata?.id,
                                        isPlaying = isPlaying,
                                        onClick = { openYt(item) },
                                        onLongClick = { ytMenu(item) },
                                    )
                                }
                            }
                        }
                    }
                }
                if (topSongs.isNotEmpty()) {
                    novedadesLocalSongShelf(
                        key = "daily_top",
                        titleRes = R.string.novedades_daily_top,
                        songs = topSongs.take(12),
                        queue = topSongs,
                        cardScale = cardScale,
                        isPlaying = isPlaying,
                        activeId = mediaMetadata?.id,
                        headerClick = { navController.navigate("top_playlist/$topSize") },
                        onPlayAt = { index, queueSongs, title ->
                            playerConnection.playQueue(
                                ListQueue(
                                    title = title,
                                    items = queueSongs.map { it.toMediaItem() },
                                    startIndex = index,
                                ),
                            )
                        },
                        onMenu = { song ->
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            menuState.show {
                                SongMenu(
                                    originalSong = song,
                                    navController = navController,
                                    onDismiss = menuState::dismiss,
                                )
                            }
                        },
                    )
                }
                if (upcoming.isNotEmpty()) {
                    item(key = "aura_novedades_coming_soon") {
                        Column {
                            AuraSectionHeader(title = stringResource(R.string.novedades_coming_soon))
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = AuraSpacing.Gutter),
                                horizontalArrangement = Arrangement.spacedBy(AuraSpacing.ShelfItemGap),
                                modifier = Modifier.padding(top = AuraSpacing.SectionGap),
                            ) {
                                items(upcoming, key = { it.id }) { item ->
                                    UpcomingCard(
                                        item = item,
                                        cardScale = cardScale,
                                        onOpen = {
                                            item.youtubeBrowseId?.let { navController.navigate("album/$it") }
                                        },
                                        onPresave = { viewModel.togglePresave(item) },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.novedadesSongShelf(
    key: String,
    titleRes: Int,
    songs: List<SongItem>,
    cardScale: Float,
    isPlaying: Boolean,
    activeId: String?,
    onOpen: (YTItem) -> Unit,
    onMenu: (YTItem) -> Unit,
) {
    if (songs.isEmpty()) return
    item(key = "aura_novedades_$key") {
        Column {
            AuraSectionHeader(title = stringResource(titleRes))
            AuraShelf {
                items(songs, key = { it.id }) { song ->
                    AuraTypedYtCoverCard(
                        item = song,
                        cardScale = cardScale,
                        isActive = song.id == activeId,
                        isPlaying = isPlaying,
                        onClick = { onOpen(song) },
                        onLongClick = { onMenu(song) },
                    )
                }
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.novedadesLocalSongShelf(
    key: String,
    titleRes: Int,
    songs: List<Song>,
    queue: List<Song>,
    cardScale: Float,
    isPlaying: Boolean,
    activeId: String?,
    onPlayAt: (Int, List<Song>, String) -> Unit,
    onMenu: (Song) -> Unit,
    headerClick: (() -> Unit)? = null,
) {
    if (songs.isEmpty()) return
    item(key = "aura_novedades_$key") {
        val title = stringResource(titleRes)
        val visual = auraTypeVisual(AuraContentKind.Song)
        Column {
            AuraSectionHeader(title = title, onClick = headerClick)
            AuraShelf {
                items(songs, key = { it.id }) { song ->
                    AuraCoverCard(
                        title = song.song.title,
                        subtitle = song.artists.joinToString { it.name }
                            .takeIf { it.isNotBlank() },
                        thumbnailUrl = song.song.thumbnailUrl,
                        seed = song.id,
                        width = visual.shelfWidth * cardScale,
                        isActive = song.id == activeId,
                        isPlaying = isPlaying,
                        onClick = {
                            val index = queue.indexOfFirst { it.id == song.id }.coerceAtLeast(0)
                            onPlayAt(index, queue, title)
                        },
                        onLongClick = { onMenu(song) },
                    )
                }
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.novedadesAlbumShelf(
    key: String,
    titleRes: Int,
    albums: List<AlbumItem>,
    cardScale: Float,
    isPlaying: Boolean,
    activeAlbumId: String?,
    onOpen: (YTItem) -> Unit,
    onMenu: (YTItem) -> Unit,
) {
    if (albums.isEmpty()) return
    item(key = "aura_novedades_$key") {
        Column {
            AuraSectionHeader(title = stringResource(titleRes))
            val w = auraTypeVisual(AuraContentKind.Album).shelfWidth * cardScale
            AuraDoubleRowShelf(rowHeight = auraShelfCardStackHeight(w)) {
                lazyGridItems(albums, key = { it.id }) { album ->
                    AuraTypedYtCoverCard(
                        item = album,
                        cardScale = cardScale,
                        isActive = album.id == activeAlbumId,
                        isPlaying = isPlaying,
                        onClick = { onOpen(album) },
                        onLongClick = { onMenu(album) },
                    )
                }
            }
        }
    }
}

@Composable
private fun UpcomingCard(
    item: UpcomingReleaseEntity,
    cardScale: Float,
    onOpen: () -> Unit,
    onPresave: () -> Unit,
) {
    val w = auraTypeVisual(AuraContentKind.Album).shelfWidth * cardScale
    val date = remember(item.releaseEpochMs) {
        LocalDate.ofInstant(Instant.ofEpochMilli(item.releaseEpochMs), ZoneOffset.UTC)
            .format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault()))
    }
    Column {
        AuraCoverCard(
            title = item.title,
            subtitle = "${item.artistName} · $date",
            thumbnailUrl = item.artworkUri,
            seed = item.id,
            width = w,
            onClick = onOpen,
        )
        androidx.compose.material3.TextButton(onClick = onPresave) {
            Text(
                text = stringResource(
                    if (item.presaved) R.string.action_presaved else R.string.action_presave,
                ),
                style = AuraType.RowSubtitle,
                color = AuraPalette.Teal,
            )
        }
    }
}
