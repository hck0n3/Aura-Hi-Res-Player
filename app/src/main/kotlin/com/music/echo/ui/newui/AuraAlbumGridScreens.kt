package iad1tya.echo.music.ui.newui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import com.music.innertube.models.AlbumItem
import com.music.innertube.models.ArtistItem
import com.music.innertube.models.PlaylistItem
import com.music.innertube.models.SongItem
import com.music.innertube.models.WatchEndpoint
import iad1tya.echo.music.LocalPlayerAwareWindowInsets
import iad1tya.echo.music.LocalPlayerConnection
import iad1tya.echo.music.R
import iad1tya.echo.music.models.toMediaMetadata
import iad1tya.echo.music.playback.queues.YouTubeQueue
import iad1tya.echo.music.ui.component.LocalMenuState
import iad1tya.echo.music.ui.menu.AlbumMenu
import iad1tya.echo.music.ui.menu.YouTubeAlbumMenu
import iad1tya.echo.music.ui.menu.YouTubeArtistMenu
import iad1tya.echo.music.ui.menu.YouTubePlaylistMenu
import iad1tya.echo.music.ui.menu.YouTubeSongMenu
import iad1tya.echo.music.ui.screens.artist.ArtistSectionBuffer
import iad1tya.echo.music.ui.utils.rememberIsTvOrCar
import iad1tya.echo.music.ui.utils.tvFocusRestorer
import iad1tya.echo.music.ui.utils.tvFocusable
import iad1tya.echo.music.viewmodels.FavoriteAlbumsViewModel

/**
 * Favorite / saved albums — Aura poster grid (title on art, no Material TopAppBar clip).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AuraFavoriteAlbumsScreen(
    navController: NavController,
    viewModel: FavoriteAlbumsViewModel = hiltViewModel(),
) {
    val menuState = LocalMenuState.current
    val haptic = LocalHapticFeedback.current
    val playerConnection = LocalPlayerConnection.current ?: return
    val isPlaying by playerConnection.isEffectivelyPlaying.collectAsState()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsState()
    val albums by viewModel.albums.collectAsState()
    val bloom = rememberAuraBloom(mediaMetadata?.id)
    val gridState = rememberLazyGridState()
    val isTvOrCar = rememberIsTvOrCar()
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
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val columns = 2
            LazyVerticalGrid(
                state = gridState,
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
                item(span = { GridItemSpan(maxLineSpan) }, key = "aura_fav_albums_header") {
                    AuraSectionGridHeader(
                        title = stringResource(R.string.favorite_albums),
                        countLabel = pluralStringResource(R.plurals.n_album, albums.size, albums.size),
                        onBack = navController::navigateUp,
                    )
                }

                if (albums.isEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }, key = "aura_fav_albums_empty") {
                        AuraEmpty(
                            text = stringResource(R.string.library_album_empty),
                            modifier = Modifier.animateItem(),
                        )
                    }
                }

                items(
                    items = albums.distinctBy { it.id },
                    key = { it.id },
                ) { album ->
                    AuraPosterCard(
                        title = album.album.title,
                        subtitle = album.artists.joinToString { it.name }
                            .takeIf { it.isNotBlank() },
                        thumbnailUrl = album.album.thumbnailUrl,
                        seed = album.id,
                        shape = AuraShapes.Artwork,
                        isActive = album.id == mediaMetadata?.album?.id,
                        isPlaying = isPlaying,
                        typeIcon = AuraIcons.Album,
                        onClick = { navController.navigate("album/${album.id}") },
                        onLongClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            menuState.show {
                                AlbumMenu(
                                    originalAlbum = album,
                                    navController = navController,
                                    onDismiss = menuState::dismiss,
                                )
                            }
                        },
                        modifier = Modifier
                            .animateItem()
                            .tvFocusable(isTvOrCar, AuraShapes.Highlight, scaleFocused = 1f),
                    )
                }
            }
        }
    }
}

/**
 * Artist section "ver todos" without moreEndpoint — same premium posters as [AuraArtistItemsScreen].
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AuraArtistSectionGridScreen(
    navController: NavController,
) {
    val menuState = LocalMenuState.current
    val haptic = LocalHapticFeedback.current
    val playerConnection = LocalPlayerConnection.current ?: return
    val isPlaying by playerConnection.isEffectivelyPlaying.collectAsState()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsState()
    val coroutineScope = rememberCoroutineScope()
    val bloom = rememberAuraBloom(mediaMetadata?.id)
    val isTvOrCar = rememberIsTvOrCar()
    val title = ArtistSectionBuffer.title
    val items = ArtistSectionBuffer.items
    val bottomPad = LocalPlayerAwareWindowInsets.current
        .only(WindowInsetsSides.Bottom)
        .asPaddingValues()
        .calculateBottomPadding() + 24.dp
    val topPad = auraStatusBarPadding() + 8.dp
    val videoHeavy = items.any { it is SongItem && it.isVideoSong }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .auraScreenBackground(bloom, intensity = 0.42f),
    ) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val columns = if (videoHeavy) 1 else 2
            LazyVerticalGrid(
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
                item(span = { GridItemSpan(maxLineSpan) }, key = "aura_artist_section_header") {
                    AuraSectionGridHeader(
                        title = title.ifBlank { stringResource(R.string.albums) },
                        countLabel = items.size.takeIf { it > 0 }?.toString(),
                        onBack = navController::navigateUp,
                    )
                }

                if (items.isEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }, key = "aura_artist_section_empty") {
                        AuraEmpty(
                            text = stringResource(R.string.library_album_empty),
                            modifier = Modifier.animateItem(),
                        )
                    }
                }

                items(
                    items = items.distinctBy { it.id },
                    key = { it.id },
                ) { item ->
                    when (item) {
                        is AlbumItem, is SongItem, is ArtistItem, is PlaylistItem -> {
                            AuraYtPosterGridItem(
                                item = item,
                                isPlaying = isPlaying,
                                mediaMetadataId = mediaMetadata?.id,
                                mediaAlbumId = mediaMetadata?.album?.id,
                                onClick = {
                                    when (item) {
                                        is SongItem -> playerConnection.playQueue(
                                            YouTubeQueue(
                                                item.endpoint ?: WatchEndpoint(videoId = item.id),
                                                item.toMediaMetadata(),
                                            ),
                                        )
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
                    }
                }
            }
        }
    }
}
