package iad1tya.echo.music.ui.newui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.media3.common.MediaItem
import com.music.innertube.models.SongItem
import iad1tya.echo.music.LocalDatabase
import iad1tya.echo.music.LocalPlayerConnection
import iad1tya.echo.music.R
import iad1tya.echo.music.db.entities.Song
import iad1tya.echo.music.extensions.toMediaItem
import iad1tya.echo.music.models.toMediaMetadata
import iad1tya.echo.music.playback.queues.ListQueue
import iad1tya.echo.music.playback.queues.YouTubeQueue
import iad1tya.echo.music.ui.component.LocalMenuState
import iad1tya.echo.music.ui.menu.AddToPlaylistDialog
import iad1tya.echo.music.viewmodels.LocalFilter
import iad1tya.echo.music.viewmodels.LocalSearchViewModel
import iad1tya.echo.music.viewmodels.OnlineSearchSuggestionViewModel
import kotlinx.coroutines.flow.drop

/**
 * Frost quick-search sheet opened from the expanded player. Stays on the player — no navigation.
 * Local library songs + online song hits; tap a row for play / next / queue / playlist actions.
 */
@Composable
fun AuraPlayerQuickSearchContent(
    onDismiss: () -> Unit,
    isListenTogetherGuest: Boolean = false,
    modifier: Modifier = Modifier,
    localViewModel: LocalSearchViewModel = hiltViewModel(),
    onlineViewModel: OnlineSearchSuggestionViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val database = LocalDatabase.current
    val playerConnection = LocalPlayerConnection.current ?: return
    val menuState = LocalMenuState.current
    val keyboardController = LocalSoftwareKeyboardController.current

    val isPlaying by playerConnection.isEffectivelyPlaying.collectAsState()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsState()

    var query by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue())
    }
    var showChoosePlaylistDialog by rememberSaveable { mutableStateOf(false) }
    var pendingPlaylistHit by remember { mutableStateOf<QuickSearchSong?>(null) }

    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        runCatching { focusRequester.requestFocus() }
    }

    val localResult by localViewModel.result.collectAsState()
    val onlineState by onlineViewModel.viewState.collectAsState()

    LaunchedEffect(query.text) {
        localViewModel.query.value = query.text
        onlineViewModel.query.value = query.text
    }

    val localSongs = remember(localResult, query.text) {
        if (query.text.isBlank()) emptyList()
        else localResult.map
            .getOrDefault(LocalFilter.SONG, emptyList())
            .filterIsInstance<Song>()
            .distinctBy { it.id }
    }
    val onlineSongs = remember(onlineState.items, query.text) {
        if (query.text.isBlank()) emptyList()
        else onlineState.items.filterIsInstance<SongItem>().distinctBy { it.id }
    }

    AddToPlaylistDialog(
        isVisible = showChoosePlaylistDialog,
        onGetSong = {
            when (val hit = pendingPlaylistHit) {
                is QuickSearchSong.Local -> {
                    val meta = hit.song.toMediaMetadata()
                    database.withTransaction { insert(meta) }
                    listOf(meta.id)
                }
                is QuickSearchSong.Online -> {
                    val meta = hit.item.toMediaMetadata()
                    database.withTransaction { insert(meta) }
                    listOf(meta.id)
                }
                null -> emptyList()
            }
        },
        onDismiss = {
            showChoosePlaylistDialog = false
            pendingPlaylistHit = null
        },
    )

    val voice = rememberAuraVoiceSearch(
        onPartial = { spoken -> query = TextFieldValue(spoken, TextRange(spoken.length)) },
        onResult = { spoken -> query = TextFieldValue(spoken, TextRange(spoken.length)) },
    )

    val openSongMenu: (QuickSearchSong) -> Unit = { hit ->
        menuState.show {
            AuraPlayerQuickSearchSongMenu(
                hit = hit,
                isGuest = isListenTogetherGuest,
                onDismiss = menuState::dismiss,
                onPlayNow = {
                    menuState.dismiss()
                    onDismiss()
                    when (hit) {
                        is QuickSearchSong.Local ->
                            playerConnection.playQueue(
                                ListQueue(
                                    title = context.getString(R.string.queue_searched_songs),
                                    items = listOf(hit.song.toMediaItem()),
                                ),
                            )
                        is QuickSearchSong.Online ->
                            playerConnection.playQueue(
                                YouTubeQueue.radio(hit.item.toMediaMetadata()),
                            )
                    }
                },
                onPlayNext = {
                    menuState.dismiss()
                    val item = hit.mediaItem
                    playerConnection.playNext(item)
                },
                onAddToQueue = {
                    menuState.dismiss()
                    playerConnection.addToQueue(hit.mediaItem)
                },
                onAddToPlaylist = {
                    menuState.dismiss()
                    pendingPlaylistHit = hit
                    showChoosePlaylistDialog = true
                },
            )
        }
    }

    val lazyListState = rememberLazyListState()
    LaunchedEffect(Unit) {
        snapshotFlow { lazyListState.firstVisibleItemScrollOffset }
            .drop(1)
            .collect { if (lazyListState.isScrollInProgress) keyboardController?.hide() }
    }

    Column(modifier = modifier.fillMaxSize()) {
        Text(
            text = stringResource(R.string.search),
            style = AuraType.ScreenTitle,
            color = AuraPalette.OnGround,
            modifier = Modifier.padding(horizontal = AuraSpacing.Gutter, vertical = 4.dp),
        )

        AuraSearchInputBar(
            value = query,
            onValueChange = { query = it },
            placeholder = stringResource(R.string.search_yt_music),
            active = true,
            onLeadingClick = onDismiss,
            onSubmit = { keyboardController?.hide() },
            onClear = { query = TextFieldValue("") },
            onVoice = voice,
            focusRequester = focusRequester,
            onFieldTap = {},
            showSource = false,
        )

        LazyColumn(
            state = lazyListState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        ) {
            if (query.text.isNotBlank() && localSongs.isEmpty() && onlineSongs.isEmpty()) {
                item(key = "quick_search_empty") {
                    Text(
                        text = stringResource(R.string.no_results_found),
                        style = AuraType.RowSubtitle,
                        color = AuraPalette.OnGroundGhost,
                        modifier = Modifier.padding(
                            horizontal = AuraSpacing.Gutter,
                            vertical = 16.dp,
                        ),
                    )
                }
            }

            if (localSongs.isNotEmpty()) {
                item(key = "quick_search_local_header") {
                    AuraSectionHeader(
                        title = stringResource(R.string.filter_local),
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                items(localSongs, key = { "quick_local_${it.id}" }) { song ->
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
                        downloadId = song.id,
                        format = song.format,
                        onClick = { openSongMenu(QuickSearchSong.Local(song)) },
                        modifier = Modifier.padding(horizontal = AuraSpacing.Gutter),
                    )
                }
            }

            if (onlineSongs.isNotEmpty()) {
                item(key = "quick_search_online_header") {
                    AuraSectionHeader(
                        title = stringResource(R.string.search_online),
                        modifier = Modifier.padding(top = if (localSongs.isNotEmpty()) 8.dp else 4.dp),
                    )
                }
                items(onlineSongs, key = { "quick_online_${it.id}" }) { item ->
                    AuraYtItemRow(
                        item = item,
                        isActive = mediaMetadata?.id == item.id,
                        isPlaying = isPlaying,
                        onClick = { openSongMenu(QuickSearchSong.Online(item)) },
                        onLongClick = { openSongMenu(QuickSearchSong.Online(item)) },
                        onMenuClick = { openSongMenu(QuickSearchSong.Online(item)) },
                    )
                }
            }

            item(key = "quick_search_bottom_spacer") {
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

/** A song hit from quick search — local DB row or online suggestion item. */
internal sealed class QuickSearchSong {
    abstract val mediaItem: MediaItem

    data class Local(val song: Song) : QuickSearchSong() {
        override val mediaItem: MediaItem get() = song.toMediaItem()
    }

    data class Online(val item: SongItem) : QuickSearchSong() {
        override val mediaItem: MediaItem get() = item.toMediaItem()
    }
}

/**
 * Frost action sheet for a quick-search song. Play-next / queue / playlist do not interrupt playback;
 * play-now replaces the queue (caller dismisses the search sheet).
 */
@Composable
internal fun AuraPlayerQuickSearchSongMenu(
    hit: QuickSearchSong,
    isGuest: Boolean,
    onDismiss: () -> Unit,
    onPlayNow: () -> Unit,
    onPlayNext: () -> Unit,
    onAddToQueue: () -> Unit,
    onAddToPlaylist: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        val title = when (hit) {
            is QuickSearchSong.Local -> hit.song.song.title
            is QuickSearchSong.Online -> hit.item.title
        }
        Text(
            text = title,
            style = AuraType.RowTitle,
            color = AuraPalette.OnGround,
            maxLines = 2,
            overflow = AuraDefaultOverflow,
            modifier = Modifier.padding(
                horizontal = AuraSpacing.Gutter,
                vertical = 8.dp,
            ),
        )

        AuraMenuRow(
            icon = AuraIcons.Play,
            label = stringResource(R.string.play),
            onClick = {
                onPlayNow()
                onDismiss()
            },
        )

        if (!isGuest) {
            AuraMenuRow(
                icon = AuraIcons.SkipNext,
                label = stringResource(R.string.play_next),
                onClick = {
                    onPlayNext()
                    onDismiss()
                },
            )
            AuraMenuRow(
                icon = AuraIcons.Queue,
                label = stringResource(R.string.add_to_queue),
                onClick = {
                    onAddToQueue()
                    onDismiss()
                },
            )
        }

        AuraMenuRow(
            icon = AuraIcons.PlaylistAdd,
            label = stringResource(R.string.add_to_playlist),
            onClick = {
                onAddToPlaylist()
                onDismiss()
            },
        )

        Spacer(Modifier.height(12.dp))
    }
}
