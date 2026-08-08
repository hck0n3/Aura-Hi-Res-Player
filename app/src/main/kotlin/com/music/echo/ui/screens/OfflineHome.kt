package iad1tya.echo.music.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import iad1tya.echo.music.LocalDatabase
import iad1tya.echo.music.LocalPlayerAwareWindowInsets
import iad1tya.echo.music.LocalPlayerConnection
import iad1tya.echo.music.R
import iad1tya.echo.music.constants.OfflineModeKey
import iad1tya.echo.music.constants.SongSortType
import iad1tya.echo.music.extensions.toMediaItem
import iad1tya.echo.music.playback.queues.ListQueue
import iad1tya.echo.music.ui.component.EmptyPlaceholder
import iad1tya.echo.music.ui.component.LocalMenuState
import iad1tya.echo.music.ui.component.SongListItem
import iad1tya.echo.music.ui.menu.SongMenu
import iad1tya.echo.music.utils.rememberPreference

/**
 * Offline body: shown instead of network feeds when [OfflineModeKey] is ON. Lists ONLY fully
 * downloaded songs. Disable toggle lives ON THIS SCREEN (owner request) — not only in Settings.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DownloadedOnlyView(
    navController: NavController,
    modifier: Modifier = Modifier,
) {
    val database = LocalDatabase.current
    val menuState = LocalMenuState.current
    val haptic = LocalHapticFeedback.current
    val playerConnection = LocalPlayerConnection.current ?: return
    var offlineMode by rememberPreference(OfflineModeKey, false)

    val isPlaying by playerConnection.isEffectivelyPlaying.collectAsState()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsState()

    val downloadedSongs by database
        .downloadedSongs(SongSortType.CREATE_DATE, descending = true)
        .collectAsState(initial = emptyList())

    val downloadsTitle = stringResource(R.string.downloaded_songs)

    LazyColumn(
        state = rememberLazyListState(),
        contentPadding = LocalPlayerAwareWindowInsets.current.asPaddingValues(),
        modifier = modifier.fillMaxSize(),
    ) {
        item(key = "offline_banner") {
            OfflineBanner(
                onDisable = { offlineMode = false },
            )
        }

        if (downloadedSongs.isEmpty()) {
            item(key = "offline_empty") {
                EmptyPlaceholder(
                    icon = R.drawable.download,
                    text = stringResource(R.string.offline_empty_downloads),
                )
            }
        } else {
            items(
                items = downloadedSongs,
                key = { it.id },
            ) { song ->
                SongListItem(
                    song = song,
                    isActive = song.id == mediaMetadata?.id,
                    isPlaying = isPlaying,
                    trailingContent = {
                        IconButton(
                            onClick = {
                                menuState.show {
                                    SongMenu(
                                        originalSong = song,
                                        navController = navController,
                                        onDismiss = menuState::dismiss,
                                    )
                                }
                            }
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.more_vert),
                                contentDescription = null,
                            )
                        }
                    },
                    modifier = Modifier.combinedClickable(
                        onClick = {
                            if (song.id == mediaMetadata?.id) {
                                playerConnection.togglePlayPause()
                            } else {
                                playerConnection.playQueue(
                                    ListQueue(
                                        title = downloadsTitle,
                                        items = downloadedSongs.map { it.toMediaItem() },
                                        startIndex = downloadedSongs.indexOfFirst { it.id == song.id },
                                    )
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
                    ),
                )
            }
        }
    }
}

@Composable
private fun OfflineBanner(
    onDisable: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            Icon(
                painter = painterResource(R.drawable.offline),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(24.dp),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.offline_mode),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
                Spacer(modifier = Modifier.size(2.dp))
                Text(
                    text = stringResource(R.string.offline_banner_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
            TextButton(onClick = onDisable) {
                Text(stringResource(R.string.offline_disable))
            }
        }
    }
}
