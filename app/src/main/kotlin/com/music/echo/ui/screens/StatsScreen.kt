

package iad1tya.echo.music.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import com.music.innertube.models.WatchEndpoint
import iad1tya.echo.music.LocalPlayerAwareWindowInsets
import iad1tya.echo.music.LocalPlayerConnection
import iad1tya.echo.music.R
import iad1tya.echo.music.extensions.toMediaItem
import iad1tya.echo.music.models.toMediaMetadata
import iad1tya.echo.music.playback.queues.ListQueue
import iad1tya.echo.music.playback.queues.YouTubeQueue
import iad1tya.echo.music.ui.component.ChoiceChipsRow
import iad1tya.echo.music.ui.component.HideOnScrollFAB
import iad1tya.echo.music.ui.component.IconButton
import iad1tya.echo.music.ui.component.LocalAlbumsGrid
import iad1tya.echo.music.ui.component.LocalArtistsGrid
import iad1tya.echo.music.ui.component.LocalMenuState
import iad1tya.echo.music.ui.component.LocalSongsGrid
import iad1tya.echo.music.ui.component.NavigationTitle
import iad1tya.echo.music.ui.menu.AlbumMenu
import iad1tya.echo.music.ui.menu.ArtistMenu
import iad1tya.echo.music.ui.menu.SongMenu
import iad1tya.echo.music.utils.joinByBullet
import iad1tya.echo.music.utils.makeTimeString
import iad1tya.echo.music.viewmodels.StatsViewModel
import java.time.LocalDateTime

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun StatsScreen(
    navController: NavController,
    viewModel: StatsViewModel = hiltViewModel(),
) {
    val menuState = LocalMenuState.current
    val haptic = LocalHapticFeedback.current
    val playerConnection = LocalPlayerConnection.current ?: return
    val isPlaying by playerConnection.isEffectivelyPlaying.collectAsState()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsState()
    val context = LocalContext.current

    val indexChips by viewModel.indexChips.collectAsState()
    val mostPlayedSongs by viewModel.mostPlayedSongs.collectAsState()
    val mostPlayedSongsStats by viewModel.mostPlayedSongsStats.collectAsState()
    val mostPlayedArtists by viewModel.mostPlayedArtists.collectAsState()
    val mostPlayedAlbums by viewModel.mostPlayedAlbums.collectAsState()
    val firstEvent by viewModel.firstEvent.collectAsState()
    val currentDate = LocalDateTime.now()

    val totalPlayTime by viewModel.totalPlayTime.collectAsState()
    val allTimePlayTime by viewModel.allTimePlayTime.collectAsState()
    val uniqueSongsCount by viewModel.uniqueSongsCount.collectAsState()
    val uniqueArtistsCount by viewModel.uniqueArtistsCount.collectAsState()
    val uniqueAlbumsCount by viewModel.uniqueAlbumsCount.collectAsState()

    var showHistorySheet by remember { mutableStateOf(false) }

    val coroutineScope = rememberCoroutineScope()
    val lazyListState = rememberLazyListState()
    val selectedOption by viewModel.selectedOption.collectAsState()

    // The four chip sets (semanas / meses / años / continuo) live in StatsPeriods.kt so that the
    // redesigned Estadísticas selects the SAME ranges from the SAME labels — two copies of these
    // sequences is how the two skins would end up describing different windows over one dataset.
    val periodChips = statsPeriodChips(
        option = selectedOption,
        currentDate = currentDate,
        firstEventTimestamp = firstEvent?.event?.timestamp,
    )

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = lazyListState,
            contentPadding = LocalPlayerAwareWindowInsets.current
                .only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom)
                .asPaddingValues(),
            modifier = Modifier.windowInsetsPadding(
                LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Top)
            )
        ) {
            item(key = "choice_chips") {
                ChoiceChipsRow(
                    chips = periodChips,
                    options =
                    listOf(
                        OptionStats.CONTINUOUS to stringResource(id = R.string.continuous),
                        OptionStats.WEEKS to stringResource(R.string.weeks),
                        OptionStats.MONTHS to stringResource(R.string.months),
                        OptionStats.YEARS to stringResource(R.string.years),
                    ),
                    selectedOption = selectedOption,
                    onSelectionChange = {
                        viewModel.selectedOption.value = it
                        viewModel.indexChips.value = 0
                    },
                    currentValue = indexChips,
                    onValueUpdate = { viewModel.indexChips.value = it },
                )
            }

            item(key = "mostPlayedSongs") {
                NavigationTitle(
                    title = "${mostPlayedSongsStats.size} ${stringResource(id = R.string.songs)}",
                    modifier = Modifier.animateItem(),
                )

                LazyRow(
                    modifier = Modifier.animateItem(),
                ) {
                    itemsIndexed(
                        items = mostPlayedSongsStats,
                        key = { _, song -> song.id },
                    ) { index, song ->
                        LocalSongsGrid(
                            title = "${index + 1}. ${song.title}",
                            subtitle =
                            joinByBullet(
                                pluralStringResource(
                                    R.plurals.n_time,
                                    song.songCountListened,
                                    song.songCountListened,
                                ),
                                makeTimeString(song.timeListened),
                            ),
                            thumbnailUrl = song.thumbnailUrl,
                            isActive = song.id == mediaMetadata?.id,
                            isPlaying = isPlaying,
                            modifier =
                            Modifier
                                .fillMaxWidth()
                                .combinedClickable(
                                    onClick = {
                                        if (song.id == mediaMetadata?.id) {
                                            playerConnection.togglePlayPause()
                                        } else {
                                            playerConnection.playQueue(
                                                YouTubeQueue(
                                                    endpoint = WatchEndpoint(song.id),
                                                    preloadItem = mostPlayedSongs[index].toMediaMetadata(),
                                                ),
                                            )
                                        }
                                    },
                                    onLongClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        menuState.show {
                                            SongMenu(
                                                originalSong = mostPlayedSongs[index],
                                                navController = navController,
                                                onDismiss = menuState::dismiss,
                                            )
                                        }
                                    },
                                )
                                .animateItem(),
                        )
                    }
                }
            }

            item(key = "mostPlayedArtists") {
                NavigationTitle(
                    title = "${mostPlayedArtists.size} ${stringResource(id = R.string.artists)}",
                    modifier = Modifier.animateItem(),
                )

                LazyRow(
                    modifier = Modifier.animateItem(),
                ) {
                    itemsIndexed(
                        items = mostPlayedArtists,
                        key = { _, artist -> artist.id },
                    ) { index, artist ->
                        LocalArtistsGrid(
                            title = "${index + 1}. ${artist.artist.name}",
                            subtitle =
                            joinByBullet(
                                pluralStringResource(
                                    R.plurals.n_time,
                                    artist.songCount,
                                    artist.songCount
                                ),
                                makeTimeString(artist.timeListened?.toLong()),
                            ),
                            thumbnailUrl = artist.artist.thumbnailUrl,
                            modifier =
                            Modifier
                                .combinedClickable(
                                    onClick = {
                                        navController.navigate("artist/${artist.id}")
                                    },
                                    onLongClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        menuState.show {
                                            ArtistMenu(
                                                originalArtist = artist,
                                                coroutineScope = coroutineScope,
                                                onDismiss = menuState::dismiss,
                                            )
                                        }
                                    },
                                )
                                .animateItem(),
                        )
                    }
                }
            }

            item(key = "mostPlayedAlbums") {
                NavigationTitle(
                    title = "${mostPlayedAlbums.size} ${stringResource(id = R.string.albums)}",
                    modifier = Modifier.animateItem(),
                )

                if (mostPlayedAlbums.isNotEmpty()) {
                    LazyRow(
                        modifier = Modifier.animateItem(),
                    ) {
                        itemsIndexed(
                            items = mostPlayedAlbums,
                            key = { _, album -> album.id },
                        ) { index, album ->
                            LocalAlbumsGrid(
                                title = "${index + 1}. ${album.album.title}",
                                subtitle =
                                joinByBullet(
                                    pluralStringResource(
                                        R.plurals.n_time,
                                        album.songCountListened!!,
                                        album.songCountListened
                                    ),
                                    makeTimeString(album.timeListened),
                                ),
                                thumbnailUrl = album.album.thumbnailUrl,
                                isActive = album.id == mediaMetadata?.album?.id,
                                isPlaying = isPlaying,
                                modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .combinedClickable(
                                        onClick = {
                                            navController.navigate("album/${album.id}")
                                        },
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
                                    )
                                    .animateItem(),
                            )
                        }
                    }
                }
            }
        }

        
        if (mostPlayedSongs.isNotEmpty()) {
            HideOnScrollFAB(
                visible = true,
                lazyListState = lazyListState,
                icon = R.drawable.shuffle,
                onClick = {
                    playerConnection.playQueue(
                        ListQueue(
                            title = context.getString(R.string.most_played_songs),
                            items = mostPlayedSongs.map { it.toMediaMetadata().toMediaItem() }.shuffled()
                        )
                    )
                }
            )
        }

        TopAppBar(
            title = { Text(stringResource(R.string.stats)) },
            navigationIcon = {
                IconButton(
                    onClick = navController::navigateUp,
                    onLongClick = null,
                ) {
                    Icon(
                        painterResource(R.drawable.arrow_back),
                        contentDescription = null,
                    )
                }
            },
            actions = {
                IconButton(
                    onClick = { showHistorySheet = true }
                ) {
                    Icon(
                        painter = painterResource(R.drawable.history),
                        contentDescription = stringResource(R.string.activity_history),
                    )
                }
            }
        )

        if (showHistorySheet) {
            // Same list the chip row is drawn from, so the sheet can never name a different range
            // from the one that is selected.
            val currentPeriodLabel = statsPeriodLabel(periodChips, indexChips)

            ActivityHistoryBottomSheet(
                onDismiss = { showHistorySheet = false },
                totalPlayTimeMs = totalPlayTime,
                allTimePlayTimeMs = allTimePlayTime,
                uniqueSongs = uniqueSongsCount,
                uniqueArtists = uniqueArtistsCount,
                uniqueAlbums = uniqueAlbumsCount,
                periodLabel = currentPeriodLabel
            )
        }
    }
}

enum class OptionStats { WEEKS, MONTHS, YEARS, CONTINUOUS }
