

package iad1tya.echo.music.ui.menu

import iad1tya.echo.music.utils.ShareLinks

import android.content.Intent
import android.content.res.Configuration
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import iad1tya.echo.music.LocalDatabase
import iad1tya.echo.music.LocalListenTogetherManager
import iad1tya.echo.music.LocalPlayerConnection
import iad1tya.echo.music.R
import iad1tya.echo.music.constants.ArtistSongSortType
import iad1tya.echo.music.db.entities.SpeedDialItem
import iad1tya.echo.music.db.entities.Artist
import iad1tya.echo.music.extensions.toMediaItem
import iad1tya.echo.music.playback.queues.ListQueue
import iad1tya.echo.music.ui.component.ArtistListItem
import iad1tya.echo.music.ui.component.Material3MenuGroup
import iad1tya.echo.music.ui.component.Material3MenuItemData
import iad1tya.echo.music.ui.component.NewAction
import iad1tya.echo.music.ui.component.NewActionGrid
import iad1tya.echo.music.ui.component.rememberPlayedShuffleSet
import iad1tya.echo.music.ui.component.rememberShuffleMemoryPrompt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun ArtistMenu(
    originalArtist: Artist,
    coroutineScope: CoroutineScope,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val database = LocalDatabase.current
    val playerConnection = LocalPlayerConnection.current ?: return
    val listenTogetherManager = LocalListenTogetherManager.current
    val isGuest = listenTogetherManager?.isInRoom == true && !listenTogetherManager.isHost
    val artistState = database.artist(originalArtist.id).collectAsState(initial = originalArtist)
    val artist = artistState.value ?: originalArtist
    val isPinned by database.speedDialDao.isPinned(artist.id).collectAsState(initial = false)
    // Enhanced Shuffle context for this menu's Shuffle action — must match ArtistScreen /
    // ArtistSongsScreen ("AR:" + the LOCAL artist row id) so all three share one no-repeat memory.
    // Never "PL:": DatabaseDao's startup orphan prune deletes every "PL:%" context with no matching
    // `playlist` row, which would erase the memory on each launch. Nothing prunes "AR:%".
    val menuShuffleContextId = "AR:" + artist.id
    val menuPlayedSet = rememberPlayedShuffleSet(menuShuffleContextId)
    // Hoisted out of the action list on purpose: the actions are built conditionally, and a
    // remember/rememberSaveable inside a conditional branch would lose its slot when the condition
    // flips. The sheet is only dismissed from inside the callback, so closing it can't kill the dialog.
    // The song list is loaded off the main thread, so the counters use the artist's own songCount.
    val onMenuShuffleClick = rememberShuffleMemoryPrompt(
        contextId = menuShuffleContextId,
        playedCount = minOf(menuPlayedSet.size, artist.songCount),
        totalCount = artist.songCount,
    ) { resetMemory ->
        coroutineScope.launch {
            val songs = withContext(Dispatchers.IO) {
                val all = database
                    .artistSongs(artist.id, ArtistSongSortType.CREATE_DATE, true)
                    .first()
                // UNPLAYED-FIRST start: the opener is guaranteed to be an unheard song while any
                // remain. After a reset the memory is empty, so a plain shuffle already is that order.
                if (resetMemory) {
                    all.shuffled().map { it.toMediaItem() }
                } else {
                    val (unheard, heard) = all.partition { it.id !in menuPlayedSet }
                    (unheard.shuffled() + heard.shuffled()).map { it.toMediaItem() }
                }
            }
            playerConnection.playQueue(
                ListQueue(
                    title = artist.artist.name,
                    items = songs,
                    // Persistent per-artist no-repeat memory: without a contextId it lives only in RAM
                    // and dies with the process (app killed → the same songs come back next morning).
                    contextId = menuShuffleContextId,
                    // See AlbumMenu: without this the mode stays OFF and the scramble is frozen,
                    // bypassing the whole anti-repeat system.
                    startShuffled = true,
                ),
            )
        }
        onDismiss()
    }

    ArtistListItem(
        artist = artist,
        badges = {},
        trailingContent = {},
    )

    HorizontalDivider()

    Spacer(modifier = Modifier.height(12.dp))

    val configuration = LocalConfiguration.current
    val isPortrait = configuration.orientation == Configuration.ORIENTATION_PORTRAIT

    LazyColumn(
        contentPadding = PaddingValues(
            start = 0.dp,
            top = 0.dp,
            end = 0.dp,
            bottom = 8.dp + WindowInsets.systemBars.asPaddingValues().calculateBottomPadding(),
        ),
    ) {
        item {
            NewActionGrid(
                actions = buildList {
                    if (!isGuest) {
                        if (artist.songCount > 0) {
                            add(
                                NewAction(
                                    icon = {
                                        Icon(
                                            painter = painterResource(R.drawable.play),
                                            contentDescription = null,
                                            modifier = Modifier.size(28.dp),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    },
                                    text = stringResource(R.string.play),
                                    onClick = {
                                        coroutineScope.launch {
                                            val songs = withContext(Dispatchers.IO) {
                                                database
                                                    .artistSongs(artist.id, ArtistSongSortType.CREATE_DATE, true)
                                                    .first()
                                                    .map { it.toMediaItem() }
                                            }
                                            playerConnection.playQueue(
                                                ListQueue(
                                                    title = artist.artist.name,
                                                    items = songs,
                                                ),
                                            )
                                        }
                                        onDismiss()
                                    }
                                )
                            )

                            add(
                                NewAction(
                                    icon = {
                                        Icon(
                                            painter = painterResource(R.drawable.shuffle),
                                            contentDescription = null,
                                            modifier = Modifier.size(28.dp),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    },
                                    text = stringResource(R.string.shuffle),
                                    onClick = { onMenuShuffleClick() }
                                )
                            )
                        }
                    }

                    add(
                        NewAction(
                            icon = {
                                Icon(
                                    painter = painterResource(if (isPinned) R.drawable.remove else R.drawable.add),
                                    contentDescription = null,
                                    modifier = Modifier.size(28.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            },
                            text = if (isPinned) stringResource(R.string.unpin_from_speed_dial) else stringResource(R.string.pin_to_speed_dial),
                            onClick = {
                                coroutineScope.launch(Dispatchers.IO) {
                                    if (isPinned) {
                                        database.speedDialDao.delete(artist.id)
                                    } else {
                                        database.speedDialDao.insert(
                                            SpeedDialItem(
                                                id = artist.id,
                                                title = artist.artist.name,
                                                subtitle = null,
                                                thumbnailUrl = artist.artist.thumbnailUrl,
                                                type = "ARTIST"
                                            )
                                        )
                                    }
                                }
                                onDismiss()
                            }
                        )
                    )

                    if (artist.artist.isYouTubeArtist) {
                        add(
                            NewAction(
                                icon = {
                                    Icon(
                                        painter = painterResource(R.drawable.share),
                                        contentDescription = null,
                                        modifier = Modifier.size(28.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                },
                                text = stringResource(R.string.share),
                                onClick = {
                                    onDismiss()
                                val intent = Intent().apply {
                                        action = Intent.ACTION_SEND
                                        type = "text/plain"
                                        putExtra(
                                            Intent.EXTRA_TEXT,
                                            ShareLinks.channel(artist.id)
                                        )
                                    }
                                    context.startActivity(Intent.createChooser(intent, null))
                                }
                            )
                        )
                    }
                },
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 16.dp),
                columns = if (isGuest) 1 else 3
            )
        }

        item {
            Material3MenuGroup(
                items = listOf(
                    Material3MenuItemData(
                        title = {
                            Text(text = if (artist.artist.bookmarkedAt != null) stringResource(R.string.subscribed) else stringResource(R.string.subscribe))
                        },
                        icon = {
                            Icon(
                                painter = painterResource(if (artist.artist.bookmarkedAt != null) R.drawable.subscribed else R.drawable.subscribe),
                                contentDescription = null,
                            )
                        },
                        onClick = {
                            database.transaction {
                                update(artist.artist.toggleLike(database::confirmArtistUnsubscribed))
                            }
                        }
                    )
                )
            )
        }
    }
}
