package iad1tya.echo.music.ui.newui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import com.music.innertube.models.AlbumItem
import com.music.innertube.models.ArtistItem
import com.music.innertube.models.PlaylistItem
import com.music.innertube.models.SongItem
import com.music.innertube.models.WatchEndpoint
import com.music.innertube.models.YTItem
import iad1tya.echo.music.LocalPlayerAwareWindowInsets
import iad1tya.echo.music.LocalPlayerConnection
import iad1tya.echo.music.R
import iad1tya.echo.music.extensions.toMediaItem
import iad1tya.echo.music.models.toMediaMetadata
import iad1tya.echo.music.playback.queues.ListQueue
import iad1tya.echo.music.playback.queues.YouTubeQueue
import iad1tya.echo.music.ui.component.LocalMenuState
import iad1tya.echo.music.ui.menu.YouTubeAlbumMenu
import iad1tya.echo.music.ui.menu.YouTubeArtistMenu
import iad1tya.echo.music.ui.menu.YouTubePlaylistMenu
import iad1tya.echo.music.ui.menu.YouTubeSongMenu
import iad1tya.echo.music.ui.utils.rememberIsTvOrCar
import iad1tya.echo.music.ui.utils.tvFocusRestorer
import iad1tya.echo.music.ui.utils.tvFocusable
import iad1tya.echo.music.viewmodels.ArtistItemsViewModel

/**
 * Artist section "ver todos" with a YouTube moreEndpoint (Singles & EPs, Videos, Live, Playlists…).
 *
 * Classic [iad1tya.echo.music.ui.screens.artist.ArtistItemsScreen] draws a Material TopAppBar over the
 * grid; with New UI AppBarHeight = 0 the first row is clipped and titles under covers vanish. This
 * screen owns its Aura header + poster cells (title ON the art) and keeps the same ViewModel /
 * ListQueue song behaviour.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AuraArtistItemsScreen(
    navController: NavController,
    viewModel: ArtistItemsViewModel = hiltViewModel(),
) {
    val menuState = LocalMenuState.current
    val haptic = LocalHapticFeedback.current
    val playerConnection = LocalPlayerConnection.current ?: return
    val isPlaying by playerConnection.isEffectivelyPlaying.collectAsState()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsState()
    val lazyListState = rememberLazyListState()
    val lazyGridState = rememberLazyGridState()
    val coroutineScope = rememberCoroutineScope()
    val isTvOrCar = rememberIsTvOrCar()

    val title by viewModel.title.collectAsState()
    val itemsPage by viewModel.itemsPage.collectAsState()
    val hasFailed by viewModel.hasFailed.collectAsState()
    val bloom = rememberAuraBloom(mediaMetadata?.id)
    val pageItems = itemsPage?.items.orEmpty().distinctBy { it.id }
    val asSongList = pageItems.firstOrNull() is SongItem &&
        pageItems.filterIsInstance<SongItem>().none { it.isVideoSong }

    val playSongFromPage: (SongItem) -> Unit = { song ->
        if (song.id == mediaMetadata?.id) {
            playerConnection.togglePlayPause()
        } else {
            val pageSongs = pageItems.filterIsInstance<SongItem>()
            if (pageSongs.isEmpty()) {
                playerConnection.playQueue(
                    YouTubeQueue(
                        song.endpoint ?: WatchEndpoint(videoId = song.id),
                        song.toMediaMetadata(),
                    ),
                )
            } else {
                playerConnection.playQueue(
                    ListQueue(
                        title = title,
                        items = pageSongs.map { it.toMediaItem() },
                        startIndex = pageSongs.indexOfFirst { it.id == song.id }.coerceAtLeast(0),
                    ),
                )
            }
        }
    }

    LaunchedEffect(lazyListState) {
        snapshotFlow {
            lazyListState.layoutInfo.visibleItemsInfo.any { it.key == "loading" }
        }.collect { shouldLoadMore ->
            if (shouldLoadMore) viewModel.loadMore()
        }
    }
    LaunchedEffect(lazyGridState) {
        snapshotFlow {
            lazyGridState.layoutInfo.visibleItemsInfo.any { it.key == "loading" }
        }.collect { shouldLoadMore ->
            if (shouldLoadMore) viewModel.loadMore()
        }
    }

    val bottomPad = LocalPlayerAwareWindowInsets.current
        .only(WindowInsetsSides.Bottom)
        .asPaddingValues()
        .calculateBottomPadding() + 24.dp
    val topPad = auraStatusBarPadding() + 8.dp

    Box(
        modifier = Modifier
            .fillMaxSize()
            .auraScreenBackground(bloom, intensity = 0.42f),
    ) {
        when {
            itemsPage == null && !hasFailed -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = topPad),
                ) {
                    AuraDetailHeader(
                        title = title.ifBlank { stringResource(R.string.albums) },
                        onBack = navController::navigateUp,
                    )
                    AuraEmpty(
                        text = stringResource(R.string.please_wait),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 48.dp),
                    )
                }
            }

            itemsPage == null && hasFailed -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = topPad),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    AuraDetailHeader(
                        title = title.ifBlank { stringResource(R.string.albums) },
                        onBack = navController::navigateUp,
                    )
                    SpacerHeader()
                    AuraHeaderButton(
                        icon = AuraIcons.Radio,
                        label = stringResource(R.string.retry),
                        onClick = viewModel::load,
                        accent = true,
                        modifier = Modifier.padding(horizontal = AuraSpacing.Gutter),
                    )
                }
            }

            asSongList -> {
                LazyColumn(
                    state = lazyListState,
                    contentPadding = PaddingValues(
                        top = topPad,
                        bottom = bottomPad,
                    ),
                    modifier = Modifier
                        .fillMaxSize()
                        .tvFocusRestorer(),
                ) {
                    item(key = "aura_artist_items_header") {
                        AuraSectionGridHeader(
                            title = title.ifBlank { stringResource(R.string.songs) },
                            countLabel = "${pageItems.size}",
                            onBack = navController::navigateUp,
                        )
                    }
                    itemsIndexed(
                        items = pageItems,
                        key = { _, it -> it.id },
                    ) { index, item ->
                        val song = item as? SongItem ?: return@itemsIndexed
                        AuraAppleListRowFrame(
                            showDivider = index < pageItems.lastIndex,
                            dividerInset = AuraAppleCoverDividerInset,
                            modifier = Modifier.animateItem(),
                        ) {
                            AuraSongRow(
                                title = song.title,
                                subtitle = song.artists.joinToString { it.name },
                                thumbnailUrl = song.thumbnail,
                                seed = song.id,
                                isActive = mediaMetadata?.id == song.id,
                                isPlaying = isPlaying,
                                explicit = song.explicit,
                                durationLabel = auraAppleDurationLabel(song.duration),
                                showQualityBadge = false,
                                onClick = { playSongFromPage(song) },
                                onLongClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    menuState.show {
                                        YouTubeSongMenu(
                                            song = song,
                                            navController = navController,
                                            onDismiss = menuState::dismiss,
                                        )
                                    }
                                },
                                onMenuClick = {
                                    menuState.show {
                                        YouTubeSongMenu(
                                            song = song,
                                            navController = navController,
                                            onDismiss = menuState::dismiss,
                                        )
                                    }
                                },
                                modifier = Modifier.tvFocusable(
                                    isTvOrCar,
                                    AuraShapes.Highlight,
                                    scaleFocused = 1f,
                                ),
                            )
                        }
                    }
                    if (itemsPage?.continuation != null) {
                        item(key = "loading") {
                            Box(Modifier.height(72.dp))
                        }
                    }
                }
            }

            else -> {
                    val videoHeavy = pageItems.any { it is SongItem && it.isVideoSong }
                BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                    // Two wide columns (Apple/YTM “see all”) — never a dense postage stamp wall.
                    val columns = if (videoHeavy) 1 else 2
                    LazyVerticalGrid(
                        state = lazyGridState,
                        columns = GridCells.Fixed(columns),
                        contentPadding = PaddingValues(
                            start = AuraSpacing.Gutter,
                            end = AuraSpacing.Gutter,
                            top = topPad,
                            bottom = bottomPad,
                        ),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                        modifier = Modifier
                            .fillMaxSize()
                            .tvFocusRestorer(),
                    ) {
                        item(span = { GridItemSpan(maxLineSpan) }, key = "aura_artist_items_header") {
                            AuraSectionGridHeader(
                                title = title.ifBlank { stringResource(R.string.albums) },
                                countLabel = "${pageItems.size}",
                                onBack = navController::navigateUp,
                            )
                        }
                        items(
                            items = pageItems.sortedByDescending { (it as? AlbumItem)?.year ?: 0 },
                            key = { it.id },
                        ) { item ->
                            AuraYtPosterGridItem(
                                item = item,
                                isPlaying = isPlaying,
                                mediaMetadataId = mediaMetadata?.id,
                                mediaAlbumId = mediaMetadata?.album?.id,
                                onClick = {
                                    when (item) {
                                        is SongItem -> playSongFromPage(item)
                                        is AlbumItem -> navController.navigate("album/${item.id}")
                                        is ArtistItem -> navController.navigate("artist/${item.id}")
                                        is PlaylistItem -> navController.navigate("online_playlist/${item.id}")
                                    }
                                },
                                onLongClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    menuState.show {
                                        when (item) {
                                            is SongItem -> YouTubeSongMenu(
                                                song = item,
                                                navController = navController,
                                                onDismiss = menuState::dismiss,
                                            )
                                            is AlbumItem -> YouTubeAlbumMenu(
                                                albumItem = item,
                                                navController = navController,
                                                onDismiss = menuState::dismiss,
                                            )
                                            is ArtistItem -> YouTubeArtistMenu(
                                                artist = item,
                                                onDismiss = menuState::dismiss,
                                            )
                                            is PlaylistItem -> YouTubePlaylistMenu(
                                                playlist = item,
                                                coroutineScope = coroutineScope,
                                                onDismiss = menuState::dismiss,
                                            )
                                        }
                                    }
                                },
                                modifier = Modifier
                                    .animateItem()
                                    .tvFocusable(isTvOrCar, AuraShapes.Highlight, scaleFocused = 1f),
                            )
                        }
                        if (itemsPage?.continuation != null) {
                            item(key = "loading") {
                                Box(Modifier.height(72.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun AuraSectionGridHeader(
    title: String,
    onBack: () -> Unit,
    countLabel: String? = null,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        AuraDetailHeader(title = title, onBack = onBack)
        if (!countLabel.isNullOrBlank()) {
            Text(
                text = countLabel,
                style = AuraType.RowSubtitle,
                color = AuraPalette.OnGroundMuted,
                modifier = Modifier.padding(horizontal = AuraSpacing.Gutter, vertical = 4.dp),
            )
        }
        Box(Modifier.height(10.dp))
    }
}

@Composable
private fun SpacerHeader() {
    Box(Modifier.height(24.dp))
}

@Composable
internal fun AuraYtPosterGridItem(
    item: YTItem,
    isPlaying: Boolean,
    mediaMetadataId: String?,
    mediaAlbumId: String?,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val visual = auraTypeVisual(item)
    val subtitle = when (item) {
        is SongItem -> item.artists.joinToString { it.name }
        is AlbumItem -> listOfNotNull(visual.label, item.year?.toString()).joinToString(" · ")
        is PlaylistItem -> item.author?.name ?: visual.label
        is ArtistItem -> visual.label
    }
    val active = when (item) {
        is SongItem -> mediaMetadataId == item.id
        is AlbumItem -> mediaAlbumId == item.id
        else -> false
    }
    AuraPosterCard(
        title = item.title,
        subtitle = subtitle,
        thumbnailUrl = item.thumbnail,
        seed = item.id,
        ratio = visual.ratio,
        shape = visual.shape,
        isActive = active,
        isPlaying = isPlaying,
        typeIcon = visual.icon,
        typeLabel = visual.label,
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = modifier,
    )
}
