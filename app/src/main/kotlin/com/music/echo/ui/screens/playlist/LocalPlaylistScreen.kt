

package iad1tya.echo.music.ui.screens.playlist

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconToggleButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastAny
import androidx.compose.ui.util.fastForEachIndexed
import androidx.compose.ui.util.fastForEachReversed
import androidx.compose.ui.util.fastSumBy
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadRequest
import androidx.media3.exoplayer.offline.DownloadService
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import com.music.innertube.YouTube
import com.music.innertube.utils.parseCookieString
import iad1tya.echo.music.LocalDatabase
import iad1tya.echo.music.LocalDownloadUtil
import iad1tya.echo.music.LocalPlayerAwareWindowInsets
import iad1tya.echo.music.LocalPlayerConnection
import iad1tya.echo.music.LocalSyncUtils
import iad1tya.echo.music.R
import iad1tya.echo.music.constants.AiPlaylistEnabledKey
import iad1tya.echo.music.constants.InnerTubeCookieKey
import iad1tya.echo.music.constants.PlaylistEditLockKey
import iad1tya.echo.music.constants.PlaylistSongSortDescendingKey
import iad1tya.echo.music.constants.PlaylistSongSortType
import iad1tya.echo.music.constants.PlaylistSongSortTypeKey
import iad1tya.echo.music.constants.SwipeToRemoveSongKey
import iad1tya.echo.music.db.entities.Playlist
import iad1tya.echo.music.db.entities.PlaylistSong
import iad1tya.echo.music.extensions.move
import iad1tya.echo.music.extensions.toMediaItem
import iad1tya.echo.music.playback.ExoDownloadService
import iad1tya.echo.music.playback.queues.ListQueue
import iad1tya.echo.music.ui.component.ActionPromptDialog
import iad1tya.echo.music.ui.component.DefaultDialog
import iad1tya.echo.music.ui.component.DraggableScrollbar
import iad1tya.echo.music.ui.component.EmptyPlaceholder
import iad1tya.echo.music.ui.component.IconButton
import iad1tya.echo.music.ui.component.LocalMenuState
import iad1tya.echo.music.ui.component.OverlayEditButton
import iad1tya.echo.music.ui.component.SongListItem
import iad1tya.echo.music.ui.component.EnhancedShuffleChip
import iad1tya.echo.music.ui.component.rememberPlayedShuffleSet
import iad1tya.echo.music.ui.component.rememberShuffleMemoryPrompt
import iad1tya.echo.music.ui.component.SortHeader
import iad1tya.echo.music.ui.component.TextFieldDialog
import iad1tya.echo.music.ui.menu.CustomThumbnailMenu
import iad1tya.echo.music.ui.component.ExpandableText
import iad1tya.echo.music.ui.menu.LocalPlaylistMenu
import iad1tya.echo.music.ui.menu.SelectionSongMenu
import iad1tya.echo.music.ui.menu.SongMenu
import iad1tya.echo.music.ui.theme.rememberEffectiveDarkTheme
import iad1tya.echo.music.ui.utils.rememberIsTvOrCar
import iad1tya.echo.music.ui.utils.tvFocusable
import iad1tya.echo.music.utils.listItemShape
import iad1tya.echo.music.utils.makeTimeString
import iad1tya.echo.music.utils.rememberEnumPreference
import iad1tya.echo.music.utils.rememberPreference
import iad1tya.echo.music.utils.reportException
import iad1tya.echo.music.viewmodels.LocalPlaylistViewModel
import com.yalantis.ucrop.UCrop
import io.ktor.client.plugins.ClientRequestException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import java.time.LocalDateTime

@SuppressLint("RememberReturnType")
@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun LocalPlaylistScreen(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
    viewModel: LocalPlaylistViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val menuState = LocalMenuState.current
    val database = LocalDatabase.current
    val haptic = LocalHapticFeedback.current
    val playerConnection = LocalPlayerConnection.current ?: return
    val isPlaying by playerConnection.isEffectivelyPlaying.collectAsState()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsState()

    val playlist by viewModel.playlist.collectAsState()
    val songs by viewModel.playlistSongs.collectAsState()
    // Enhanced Shuffle: reactive per-context played-set for the "ya reproducida" dim/check + the X/Y chip.
    val shufflePlayedSet = rememberPlayedShuffleSet("PL:" + viewModel.playlistId)
    val mutableSongs = remember { mutableStateListOf<PlaylistSong>() }
    val playlistLength =
        remember(songs) {
            songs.fastSumBy { it.song.song.duration }
        }
    val (sortType, onSortTypeChange) = rememberEnumPreference(
        PlaylistSongSortTypeKey,
        PlaylistSongSortType.CUSTOM
    )
    val (sortDescending, onSortDescendingChange) = rememberPreference(
        PlaylistSongSortDescendingKey,
        true
    )
    var locked by rememberPreference(PlaylistEditLockKey, defaultValue = true)

    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    // Apple-Music-style "Add Music" footer (only shown on pure-local editable playlists — see below).
    var showAddMusicSheet by remember { mutableStateOf(false) }
    val previewController = rememberSongPreviewController()

    var isSearching by rememberSaveable { mutableStateOf(false) }

    var query by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue())
    }

    val filteredSongs =
        remember(songs, query) {
            if (query.text.isEmpty()) {
                songs
            } else {
                songs.filter { song ->
                    song.song.song.title
                        .contains(query.text, ignoreCase = true) ||
                            song.song.artists
                                .fastAny { it.name.contains(query.text, ignoreCase = true) }
                }
            }
        }

    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(isSearching) {
        if (isSearching) {
            focusRequester.requestFocus()
        }
    }

    var inSelectMode by rememberSaveable { mutableStateOf(false) }
    val selection = rememberSaveable(
        saver = listSaver<MutableList<Int>, Int>(
            save = { it.toList() },
            restore = { it.toMutableStateList() }
        )
    ) { mutableStateListOf() }
    val onExitSelectionMode = {
        inSelectMode = false
        selection.clear()
    }

    // Entering search or multi-select hides the Add-Music footer; stop any active preview so it doesn't
    // keep playing invisibly.
    LaunchedEffect(isSearching, inSelectMode) {
        if (isSearching || inSelectMode) previewController.stop()
    }

    if (isSearching) {
        BackHandler {
            isSearching = false
            query = TextFieldValue()
        }
    } else if (inSelectMode) {
        BackHandler(onBack = onExitSelectionMode)
    }

    val downloadUtil = LocalDownloadUtil.current
    var downloadState by remember {
        mutableIntStateOf(Download.STATE_STOPPED)
    }

    val editable: Boolean = playlist?.playlist?.isEditable == true
    // The Add-Music feature shows on ALL editable playlists (pure-local AND YouTube-synced) — addByIds
    // mirrors adds to YouTube for synced playlists.

    LaunchedEffect(songs) {
        selection.fastForEachReversed { mapId ->
            if (songs.find { it.map.id == mapId } == null) {
                selection.remove(Integer.valueOf(mapId))
            }
        }
    }

    LaunchedEffect(songs) {
        mutableSongs.apply {
            clear()
            addAll(songs)
        }
        if (songs.isEmpty()) return@LaunchedEffect
        downloadUtil.downloads.collect { downloads ->
            downloadState =
                if (songs.all { downloads[it.song.id]?.state == Download.STATE_COMPLETED }) {
                    Download.STATE_COMPLETED
                } else if (songs.all {
                        downloads[it.song.id]?.state == Download.STATE_QUEUED ||
                                downloads[it.song.id]?.state == Download.STATE_DOWNLOADING ||
                                downloads[it.song.id]?.state == Download.STATE_COMPLETED
                    }
                ) {
                    Download.STATE_DOWNLOADING
                } else {
                    Download.STATE_STOPPED
                }
        }
    }

    var showEditDialog by remember {
        mutableStateOf(false)
    }

    if (showEditDialog) {
        playlist?.playlist?.let { playlistEntity ->
            TextFieldDialog(
                icon = {
                    Icon(
                        painter = painterResource(R.drawable.edit),
                        contentDescription = null
                    )
                },
                title = { Text(text = stringResource(R.string.edit_playlist)) },
                onDismiss = { showEditDialog = false },
                initialTextFieldValue = TextFieldValue(
                    playlistEntity.name,
                    TextRange(playlistEntity.name.length)
                ),
                onDone = { name ->
                    database.query {
                        update(
                            playlistEntity.copy(
                                name = name,
                                lastUpdateTime = LocalDateTime.now()
                            )
                        )
                    }
                    viewModel.viewModelScope.launch(Dispatchers.IO) {
                        playlistEntity.browseId?.let { YouTube.renamePlaylist(it, name) }
                    }
                },
            )
        }
    }

    // "Editar con IA" — same feature toggle that gates the create-playlist AI FAB in LibraryScreen.
    val (aiPlaylistEnabled) = rememberPreference(AiPlaylistEnabledKey, true)
    var showAiModifyDialog by remember { mutableStateOf(false) }

    // Pure-local editable playlists ONLY. A YouTube-synced playlist gets wiped and re-imported by
    // "Sincronizar" (clearPlaylist + re-insert), so local-only AI edits there would silently vanish.
    val canAiModify = aiPlaylistEnabled && editable && playlist?.playlist?.browseId == null

    if (showAiModifyDialog && canAiModify) {
        AiModifyPlaylistDialog(
            playlistId = viewModel.playlistId,
            // The list as DISPLAYED, so an instruction about what the user sees lines up.
            songs = songs,
            onDismiss = { showAiModifyDialog = false },
            onOpenAiSettings = {
                showAiModifyDialog = false
                navController.navigate("settings/ai")
            },
        )
    }

    var showRemoveDownloadDialog by remember {
        mutableStateOf(false)
    }

    if (showRemoveDownloadDialog) {
        DefaultDialog(
            onDismiss = { showRemoveDownloadDialog = false },
            content = {
                Text(
                    text = stringResource(
                        R.string.remove_download_playlist_confirm,
                        playlist?.playlist!!.name
                    ),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(horizontal = 18.dp),
                )
            },
            buttons = {
                TextButton(
                    onClick = { showRemoveDownloadDialog = false },
                ) {
                    Text(text = stringResource(android.R.string.cancel))
                }

                TextButton(
                    onClick = {
                        showRemoveDownloadDialog = false
                        if (!editable) {
                            database.transaction {
                                playlist?.id?.let { clearPlaylist(it) }
                            }
                        }
                        songs.forEach { song ->
                            DownloadService.sendRemoveDownload(
                                context,
                                ExoDownloadService::class.java,
                                song.song.id,
                                false
                            )
                        }
                    }
                ) {
                    Text(text = stringResource(android.R.string.ok))
                }
            },
        )
    }

    var showDeletePlaylistDialog by remember {
        mutableStateOf(false)
    }
    if (showDeletePlaylistDialog) {
        val ytBrowseId = playlist?.playlist?.browseId

        // Local delete only: removes the playlist from the app, never from YouTube.
        // A SYNCED playlist (browseId != null) keeps its row as a tombstone (bookmarkedAt = null)
        // instead of being deleted — the account still has it, so the next sync would re-create it and
        // the deletion would undo itself (owner report). The sync skips browseIds whose only local rows
        // are un-bookmarked. A purely local playlist is deleted for real.
        // [alsoDeletedRemotely] = "delete from YouTube too": the account copy is going away, so delete the
        // row for real — a tombstone would stop the sync from ever restoring the playlist if the remote
        // delete FAILS. Removing it only from the app keeps the tombstone (without clearing its songs:
        // the re-save paths only re-bookmark the row, so wiping the map would bring it back EMPTY).
        // transaction{}, not query{}: a kill between the two writes must not leave a bookmarked empty row.
        val deletePlaylistLocally: (alsoDeletedRemotely: Boolean) -> Unit = { alsoDeletedRemotely ->
            showDeletePlaylistDialog = false
            database.transaction {
                playlist?.let {
                    if (it.playlist.browseId != null && !alsoDeletedRemotely) {
                        update(it.playlist.copy(bookmarkedAt = null))
                    } else {
                        delete(it.playlist)
                    }
                }
            }
        }

        DefaultDialog(
            onDismiss = { showDeletePlaylistDialog = false },
            content = {
                Text(
                    text = stringResource(
                        R.string.delete_playlist_confirm,
                        playlist?.playlist!!.name
                    ),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(horizontal = 18.dp)
                )

                // Local-only playlist: SAY SO. Without this the missing "delete from YouTube too" option
                // reads as a broken dialog (owner reported exactly that) instead of what it is: there is
                // no remote copy to delete because the playlist was never synced to his account.
                if (ytBrowseId == null) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.delete_playlist_only_local_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 18.dp)
                    )
                }

                // Synced playlist: let the user choose whether to also delete it on YouTube.
                if (ytBrowseId != null) {
                    Spacer(Modifier.height(16.dp))
                    TextButton(
                        onClick = {
                            deletePlaylistLocally(true)
                            viewModel.viewModelScope.launch(Dispatchers.IO) {
                                // Surface a remote failure: fire-and-forget made a YouTube rejection look
                                // like success (the playlist vanished locally either way).
                                YouTube.deletePlaylist(ytBrowseId).onFailure { e ->
                                    reportException(e)
                                    withContext(Dispatchers.Main) {
                                        Toast.makeText(
                                            context,
                                            context.getString(R.string.delete_playlist_youtube_failed),
                                            Toast.LENGTH_LONG
                                        ).show()
                                    }
                                }
                            }
                            navController.popBackStack()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(text = stringResource(R.string.delete_playlist_from_youtube_too))
                    }
                    TextButton(
                        onClick = {
                            deletePlaylistLocally(false)
                            navController.popBackStack()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(text = stringResource(R.string.delete_playlist_local_only))
                    }
                }
            },
            buttons = {
                TextButton(
                    onClick = {
                        showDeletePlaylistDialog = false
                    }
                ) {
                    Text(text = stringResource(android.R.string.cancel))
                }
                // Pure local playlist: keep the simple confirm (no YouTube option).
                if (ytBrowseId == null) {
                    TextButton(
                        onClick = {
                            deletePlaylistLocally(false)
                            navController.popBackStack()
                        }
                    ) {
                        Text(text = stringResource(android.R.string.ok))
                    }
                }
            }
        )
    }

    val headerItems = 2
    val lazyListState = rememberLazyListState()
    var dragInfo by remember {
        mutableStateOf<Pair<Int, Int>?>(null)
    }
    val reorderableState = rememberReorderableLazyListState(
        lazyListState = lazyListState,
        scrollThresholdPadding = LocalPlayerAwareWindowInsets.current.asPaddingValues()
    ) { from, to ->
        if (to.index >= headerItems && from.index >= headerItems) {
            val currentDragInfo = dragInfo
            dragInfo = if (currentDragInfo == null) {
                (from.index - headerItems) to (to.index - headerItems)
            } else {
                currentDragInfo.first to (to.index - headerItems)
            }

            mutableSongs.move(from.index - headerItems, to.index - headerItems)
        }
    }

    LaunchedEffect(reorderableState.isAnyItemDragging) {
        if (!reorderableState.isAnyItemDragging) {
            dragInfo?.let { (from, to) ->
                database.transaction {
                    move(viewModel.playlistId, from, to)
                    playlist?.playlist?.let { update(it.copy(lastUpdateTime = LocalDateTime.now())) }
                }

                if (viewModel.playlist.value?.playlist?.browseId != null) {
                    viewModel.viewModelScope.launch(Dispatchers.IO) {
                        val playlistSongMap = database.playlistSongMaps(viewModel.playlistId, 0)
                        val successorIndex = if (from > to) to else to + 1
                        val successorSetVideoId = playlistSongMap.getOrNull(successorIndex)?.setVideoId

                        playlistSongMap.getOrNull(from)?.setVideoId?.let { setVideoId ->
                            YouTube.moveSongPlaylist(
                                viewModel.playlist.value?.playlist?.browseId!!,
                                setVideoId,
                                successorSetVideoId
                            )
                        }
                    }
                }

                dragInfo = null
            }
        }
    }

    val showTopBarTitle by remember {
        derivedStateOf {
            lazyListState.firstVisibleItemIndex > 0
        }
    }

    Box(
        modifier = Modifier.fillMaxSize(),
    ) {
        LazyColumn(
            state = lazyListState,
            contentPadding = LocalPlayerAwareWindowInsets.current.union(WindowInsets.ime).asPaddingValues(),
        ) {
            playlist?.let { playlist ->
                if (playlist.songCount == 0 && playlist.playlist.remoteSongCount == 0) {
                    item(key = "empty_placeholder") {
                        EmptyPlaceholder(
                            icon = R.drawable.music_note,
                            text = stringResource(R.string.playlist_is_empty),
                            modifier = Modifier.animateItem()
                        )
                    }
                } else {
                    if (!isSearching) {
                        item(key = "playlist_header") {
                            LocalPlaylistHeader(
                                playlist = playlist,
                                songs = songs,
                                onShowEditDialog = { showEditDialog = true },
                                onShowRemoveDownloadDialog = { showRemoveDownloadDialog = true },
                                onshowDeletePlaylistDialog = { showDeletePlaylistDialog = true },
                                onShowAiModifyDialog = if (canAiModify) {
                                    { showAiModifyDialog = true }
                                } else {
                                    null
                                },
                                onStartSearch = { isSearching = true },
                                snackbarHostState = snackbarHostState,
                                modifier = Modifier.animateItem()
                            )
                        }
                    }

                    item(key = "controls_row") {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .padding(start = 16.dp)
                                .animateItem(),
                        ) {
                            SortHeader(
                                sortType = sortType,
                                sortDescending = sortDescending,
                                onSortTypeChange = onSortTypeChange,
                                onSortDescendingChange = onSortDescendingChange,
                                sortTypeText = { sortType ->
                                    when (sortType) {
                                        PlaylistSongSortType.CUSTOM -> R.string.sort_by_custom
                                        PlaylistSongSortType.CREATE_DATE -> R.string.sort_by_create_date
                                        PlaylistSongSortType.NAME -> R.string.sort_by_name
                                        PlaylistSongSortType.ARTIST -> R.string.sort_by_artist
                                        PlaylistSongSortType.PLAY_TIME -> R.string.sort_by_play_time
                                    }
                                },
                                modifier = Modifier.weight(1f),
                            )
                            if (editable) {
                                val description = if (locked) "Unlock playlist" else "Lock playlist"
                                TooltipBox(
                                    positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
                                    tooltip = { PlainTooltip { Text(description) } },
                                    state = rememberTooltipState(),
                                ) {
                                    FilledIconToggleButton(
                                        checked = locked,
                                        onCheckedChange = { locked = it },
                                        modifier = Modifier.padding(horizontal = 6.dp),
                                    ) {
                                        if (locked) {
                                            Icon(
                                                painter = painterResource(R.drawable.lock),
                                                contentDescription = description,
                                            )
                                        } else {
                                            Icon(
                                                painter = painterResource(R.drawable.lock_open),
                                                contentDescription = description,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            itemsIndexed(
                items = if (isSearching) filteredSongs else mutableSongs,
                key = { _, song -> song.map.id },
            ) { index, song ->
                ReorderableItem(
                    state = reorderableState,
                    key = song.map.id,
                ) {
                    val currentItem by rememberUpdatedState(song)

                    fun deleteFromPlaylist() {
                        database.transaction {
                            coroutineScope.launch {
                                playlist?.playlist?.browseId?.let { browseId ->
                                    val setVideoId = getSetVideoId(currentItem.map.songId)
                                    setVideoId?.setVideoId?.let { setVideoIdValue ->
                                        YouTube.removeFromPlaylist(
                                            browseId,
                                            currentItem.map.songId,
                                            setVideoIdValue
                                        )
                                    }
                                }
                            }
                            move(
                                currentItem.map.playlistId,
                                currentItem.map.position,
                                Int.MAX_VALUE
                            )
                            delete(currentItem.map.copy(position = Int.MAX_VALUE))
                            playlist?.playlist?.let { update(it.copy(lastUpdateTime = java.time.LocalDateTime.now())) }
                        }
                    }

                    val swipeRemoveEnabled by rememberPreference(SwipeToRemoveSongKey, defaultValue = false)
                    val dismissBoxState =
                        rememberSwipeToDismissBoxState(
                            positionalThreshold = { totalDistance -> totalDistance }
                        )
                    var processedDismiss by remember { mutableStateOf(false) }
                    LaunchedEffect(dismissBoxState.currentValue) {
                        val dv = dismissBoxState.currentValue
                        if (swipeRemoveEnabled && !processedDismiss && (
                                dv == SwipeToDismissBoxValue.StartToEnd ||
                                dv == SwipeToDismissBoxValue.EndToStart
                            )
                        ) {
                            processedDismiss = true
                            deleteFromPlaylist()
                        }
                        if (dv == SwipeToDismissBoxValue.Settled) {
                            processedDismiss = false
                        }
                    }

                    val onCheckedChange: (Boolean) -> Unit = {
                        if (it) {
                            selection.add(song.map.id)
                        } else {
                            selection.remove(Integer.valueOf(song.map.id))
                        }
                    }

                    val content: @Composable () -> Unit = {
                        SongListItem(
                            song = song.song,
                            isActive = song.song.id == mediaMetadata?.id,
                            isPlaying = isPlaying,
                            showInLibraryIcon = true,
                            playedInShuffle = song.song.id in shufflePlayedSet,
                            shape = listItemShape(
                                index = index,
                                count = if (isSearching) filteredSongs.size else mutableSongs.size
                            ),
                            trailingContent = {
                                if (inSelectMode) {
                                    Checkbox(
                                        checked = selection.contains(song.map.id),
                                        onCheckedChange = onCheckedChange
                                    )
                                } else {
                                    IconButton(
                                        onClick = {
                                            menuState.show {
                                                SongMenu(
                                                    originalSong = song.song,
                                                    playlistSong = song,
                                                    playlistBrowseId = playlist?.playlist?.browseId,
                                                    navController = navController,
                                                    onDismiss = menuState::dismiss,
                                                )
                                            }
                                        },
                                    ) {
                                        Icon(
                                            painter = painterResource(R.drawable.more_vert),
                                            contentDescription = null,
                                        )
                                    }

                                    if (sortType == PlaylistSongSortType.CUSTOM && !locked && !inSelectMode && !isSearching && editable) {
                                        IconButton(
                                            onClick = { },
                                            modifier = Modifier.draggableHandle(),
                                        ) {
                                            Icon(
                                                painter = painterResource(R.drawable.drag_handle),
                                                contentDescription = null,
                                            )
                                        }
                                    }
                                }
                            },
                            modifier =
                            Modifier
                                .fillMaxWidth()
                                .combinedClickable(
                                    onClick = {
                                        if (inSelectMode) {
                                            onCheckedChange(!selection.contains(song.map.id))
                                        } else {
                                            // Starting real playback must stop any active footer/sheet
                                            // preview so two songs don't play at once.
                                            previewController.stop()
                                            if (song.song.id == mediaMetadata?.id) {
                                                playerConnection.togglePlayPause()
                                            } else {
                                                playerConnection.playQueue(
                                                    ListQueue(
                                                        title = playlist!!.playlist.name,
                                                        items = songs.map { it.song.toMediaItem() },
                                                        startIndex = songs.indexOfFirst { it.map.id == song.map.id },
                                                        contextId = "PL:" + viewModel.playlistId,
                                                    ),
                                                )
                                            }
                                        }
                                    },
                                    onLongClick = {
                                        if (!inSelectMode) {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            inSelectMode = true
                                            onCheckedChange(true)
                                        }
                                    },
                                ),
                        )
                    }

                    if (locked || inSelectMode || !swipeRemoveEnabled) {
                        Box(modifier = Modifier.animateItem()) {
                            content()
                        }
                    } else {
                        SwipeToDismissBox(
                            state = dismissBoxState,
                            backgroundContent = {},
                            modifier = Modifier.animateItem()
                        ) {
                            content()
                        }
                    }
                }
            }
            if (editable && !isSearching && !inSelectMode) {
                item(key = "add_music_button") {
                    AddMusicButton(
                        onClick = {
                            // Stop any footer preview so it doesn't overlap the sheet's own preview player.
                            previewController.stop()
                            showAddMusicSheet = true
                        },
                        modifier = Modifier.animateItem(),
                    )
                }
                item(key = "suggested_songs_section") {
                    SuggestedSongsSection(
                        viewModel = viewModel,
                        previewController = previewController,
                        playerConnection = playerConnection,
                        modifier = Modifier.animateItem(),
                    )
                }
                item(key = "featured_artists_section") {
                    FeaturedArtistsSection(
                        viewModel = viewModel,
                        navController = navController,
                        modifier = Modifier.animateItem(),
                    )
                }
            }
            item(key = "bottom_spacer") {
                Spacer(Modifier.height(50.dp))
            }
        }

        if (showAddMusicSheet) {
            AddMusicSheet(
                viewModel = viewModel,
                onDismiss = { showAddMusicSheet = false },
            )
        }

        DraggableScrollbar(
            modifier = Modifier
                .padding(
                    LocalPlayerAwareWindowInsets.current.union(WindowInsets.ime)
                        .asPaddingValues()
                )
                .align(Alignment.CenterEnd),
            scrollState = lazyListState,
            headerItems = 2
        )

        TopAppBar(
            title = {
                if (inSelectMode) {
                    Text(pluralStringResource(R.plurals.n_selected, selection.size, selection.size))
                } else if (isSearching) {
                    TextField(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = {
                            Text(
                                text = stringResource(R.string.search),
                                style = MaterialTheme.typography.titleLarge
                            )
                        },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.titleLarge,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            disabledIndicatorColor = Color.Transparent,
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focusRequester)
                    )
                } else if (showTopBarTitle) {
                    Text(playlist?.playlist?.name.orEmpty())
                }
            },
            navigationIcon = {
                if (inSelectMode) {
                    IconButton(onClick = onExitSelectionMode) {
                        Icon(
                            painter = painterResource(R.drawable.close),
                            contentDescription = null,
                        )
                    }
                } else {
                    IconButton(
                        onClick = {
                            if (isSearching) {
                                isSearching = false
                                query = TextFieldValue()
                            } else {
                                navController.navigateUp()
                            }
                        },
                        onLongClick = null
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.arrow_back),
                            contentDescription = null
                        )
                    }
                }
            },
            actions = {
                if (inSelectMode) {
                    Checkbox(
                        checked = selection.size == songs.size && selection.isNotEmpty(),
                        onCheckedChange = {
                            if (selection.size == songs.size) {
                                selection.clear()
                            } else {
                                selection.clear()
                                selection.addAll(songs.map { it.map.id })
                            }
                        }
                    )
                    IconButton(
                        enabled = selection.isNotEmpty(),
                        onClick = {
                            menuState.show {
                                SelectionSongMenu(
                                    songSelection = selection.mapNotNull { mapId ->
                                        songs.find { it.map.id == mapId }?.song
                                    },
                                    songPosition = selection.mapNotNull { mapId ->
                                        songs.find { it.map.id == mapId }?.map
                                    },
                                    onDismiss = menuState::dismiss,
                                    clearAction = onExitSelectionMode
                                )
                            }
                        }
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.more_vert),
                            contentDescription = null
                        )
                    }
                } else if (!isSearching) {
                    
                    IconButton(
                        onClick = { isSearching = true }
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.search),
                            contentDescription = null
                        )
                    }
                }
            }
        )

        SnackbarHost(
            hostState = snackbarHostState,
            modifier =
            Modifier
                .windowInsetsPadding(LocalPlayerAwareWindowInsets.current.union(WindowInsets.ime))
                .align(Alignment.BottomCenter),
        )
    }
}

@Composable
fun LocalPlaylistHeader(
    playlist: Playlist,
    songs: List<PlaylistSong>,
    onShowEditDialog: () -> Unit,
    onShowRemoveDownloadDialog: () -> Unit,
    onshowDeletePlaylistDialog: () -> Unit,
    onStartSearch: () -> Unit,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier,
    /** Null hides the "Editar con IA" menu entry (see the gate at the call site). */
    onShowAiModifyDialog: (() -> Unit)? = null,
) {
    val playerConnection = LocalPlayerConnection.current ?: return
    val context = LocalContext.current
    val database = LocalDatabase.current
    val menuState = LocalMenuState.current
    val syncUtils = LocalSyncUtils.current
    val scope = rememberCoroutineScope()
    val isTvOrCar = rememberIsTvOrCar()
    // Same cookie gate the rest of the app uses to know if the user is signed into YouTube Music.
    val (innerTubeCookie) = rememberPreference(InnerTubeCookieKey, "")
    val isLoggedIn = remember(innerTubeCookie) { "SAPISID" in parseCookieString(innerTubeCookie) }

    val playlistLength =
        remember(songs) {
            songs.fastSumBy { it.song.song.duration }
        }

    val downloadUtil = LocalDownloadUtil.current
    var downloadState by remember {
        mutableIntStateOf(Download.STATE_STOPPED)
    }

    val liked = playlist.playlist.bookmarkedAt != null
    val editable: Boolean = playlist.playlist.isEditable

    val overrideThumbnail = remember {mutableStateOf<String?>(null)}
    var isCustomThumbnail: Boolean = playlist.thumbnails.firstOrNull()?.let {
        it.contains("studio_square_thumbnail") || it.contains("content://com.echomusic.music")
    } ?: false


    val result = remember { mutableStateOf<Uri?>(null) }
    var pendingCropDestUri by remember { mutableStateOf<Uri?>(null) }
    var showEditNoteDialog by remember { mutableStateOf(false) }

    val cropLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        if (res.resultCode == android.app.Activity.RESULT_OK) {
            val output = res.data?.let { UCrop.getOutput(it) } ?: pendingCropDestUri
            if (output != null) result.value = output
        }
    }

    val cropColor = MaterialTheme.colorScheme
    // The APP's dark/light — see [rememberEffectiveDarkTheme]. Its ONE consumer is
    // `setStatusBarLight(!darkTheme)` below, while the cropper's toolbar and root come from
    // `cropColor` (the live ColorScheme, already forced dark under "Interfaz nueva"). Derived from
    // DarkModeKey alone the two disagreed: a dark UCrop toolbar with the status bar told to draw its
    // DARK icons on top of it. Reduces to that same expression with the flag off.
    val darkTheme = rememberEffectiveDarkTheme()

    val pickLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        uri?.let { sourceUri ->
            // filesDir, NOT cacheDir: Android purges cacheDir at will, which killed the persisted
            // content:// URI and left the playlist painting the error logo forever. One stable file
            // per playlist (overwritten on re-crop; the URI string stays the same, so a same-session
            // re-change may briefly show coil's cached image — next load reads the fresh bytes).
            val coversDir = java.io.File(context.filesDir, "playlist_covers").apply { mkdirs() }
            val destFile = java.io.File(coversDir, "${playlist.id}.jpg")
            val destUri = FileProvider.getUriForFile(context, "${context.packageName}.FileProvider", destFile)
            pendingCropDestUri = destUri
    
            val options = UCrop.Options().apply {
                setCompressionFormat(Bitmap.CompressFormat.JPEG)
                setCompressionQuality(90)
                setHideBottomControls(true)
                setToolbarTitle(context.getString(R.string.edit_playlist_cover))
                
                setStatusBarLight(!darkTheme)

                setToolbarColor(cropColor.surface.toArgb())
                setToolbarWidgetColor(cropColor.inverseSurface.toArgb())
                setRootViewBackgroundColor(cropColor.surface.toArgb())
                setLogoColor(cropColor.surface.toArgb())
            }

            val intent = UCrop.of(sourceUri, destUri)
                .withAspectRatio(1f, 1f)
                .withOptions(options)
                .getIntent(context)
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            cropLauncher.launch(intent)
        }
    }

    LaunchedEffect(result.value) {
        val uri = result.value ?: return@LaunchedEffect
        withContext(Dispatchers.IO) {
            when {
                playlist.playlist.browseId == null -> {
                    overrideThumbnail.value = uri.toString()
                    isCustomThumbnail = true

                    
                    database.query {
                        update(playlist.playlist.copy(thumbnailUrl = uri.toString()))
                    }
                }

                else -> {
                    val bytes = uriToByteArray(context, uri)
                    YouTube.uploadCustomThumbnailLink(
                        playlist.playlist.browseId,
                        bytes!!
                    ).onSuccess { newThumbnailUrl ->
                        overrideThumbnail.value = newThumbnailUrl
                        isCustomThumbnail = true

                        
                        database.query {
                            update(playlist.playlist.copy(thumbnailUrl = newThumbnailUrl))
                        }
                    }.onFailure {
                        if (it is ClientRequestException) {
                            snackbarHostState.showSnackbar("${it.response.status.value} ${it.response.status.description} — portada guardada en local")
                        }
                        reportException(it)
                        // Fallback: the YouTube thumbnail upload failed (often a 403). Keep the user's chosen
                        // cover LOCALLY (same path as a local playlist) instead of a silent no-op.
                        overrideThumbnail.value = uri.toString()
                        isCustomThumbnail = true
                        database.query {
                            update(playlist.playlist.copy(thumbnailUrl = uri.toString()))
                        }
                    }
                }
            }
        }
    }

    LaunchedEffect(songs) {
        if (songs.isEmpty()) return@LaunchedEffect
        downloadUtil.downloads.collect { downloads ->
            downloadState =
                if (songs.all { downloads[it.song.id]?.state == Download.STATE_COMPLETED }) {
                    Download.STATE_COMPLETED
                } else if (songs.all {
                        downloads[it.song.id]?.state == Download.STATE_QUEUED ||
                                downloads[it.song.id]?.state == Download.STATE_DOWNLOADING ||
                                downloads[it.song.id]?.state == Download.STATE_COMPLETED
                    }
                ) {
                    Download.STATE_DOWNLOADING
                } else {
                    Download.STATE_STOPPED
                }
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (showEditNoteDialog) {
            ActionPromptDialog(
                title = stringResource(R.string.edit_playlist_cover),
                onDismiss = { showEditNoteDialog = false },
                onConfirm = {
                    showEditNoteDialog = false
                    pickLauncher.launch(
                        PickVisualMediaRequest(mediaType = ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                },
                onCancel = { showEditNoteDialog = false }
            ) {
                if (playlist.playlist.browseId != null) {
                    Text(
                        text = stringResource(R.string.edit_playlist_cover_note),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(Modifier.height(8.dp))
                }
                Text(
                    text = stringResource(R.string.edit_playlist_cover_note_wait),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
            }
        }
        
        val isTvOrCarCover = iad1tya.echo.music.ui.utils.rememberIsWideLayout()
        Box(
            modifier = Modifier
                .padding(horizontal = 48.dp)
                .padding(bottom = 24.dp)
                // On TV / car the full-width square cover fills the whole screen — cap it and center it.
                .then(
                    if (isTvOrCarCover) Modifier.widthIn(max = 320.dp).align(Alignment.CenterHorizontally)
                    else Modifier.fillMaxWidth(),
                )
                .aspectRatio(1f)
                .clip(RoundedCornerShape(8.dp))
        ) {
            when (playlist.thumbnails.size) {
                0 -> Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.queue_music),
                            contentDescription = null,
                            modifier = Modifier.size(80.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                1 -> {
                    AsyncImage(
                        model = overrideThumbnail.value ?: playlist.thumbnails[0],
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    if (editable) {
                        OverlayEditButton(
                            visible = true,
                            alignment = Alignment.BottomEnd,
                            onClick = {
                                if (isCustomThumbnail) {
                                    menuState.show(
                                        {
                                            CustomThumbnailMenu(
                                                onEdit = {
                                                    pickLauncher.launch(
                                                        PickVisualMediaRequest(mediaType = ActivityResultContracts.PickVisualMedia.ImageOnly)
                                                    )
                                                },
                                                onRemove = {
                                                    when {
                                                        playlist.playlist.browseId == null -> {
                                                            overrideThumbnail.value = null
                                                            database.query {
                                                                update(playlist.playlist.copy(thumbnailUrl = null))
                                                            }
                                                        }
                                                        else -> {
                                                            scope.launch(Dispatchers.IO) {
                                                                YouTube.removeThumbnailPlaylist(playlist.playlist.browseId).onSuccess { newThumbnailUrl -> 
                                                                    overrideThumbnail.value = newThumbnailUrl
                                                                    database.query {
                                                                        update(playlist.playlist.copy(thumbnailUrl = newThumbnailUrl))
                                                                    }
                                                                }
                                                            }
                                                        }
                                                    }
                                                    isCustomThumbnail = false 
                                                },
                                                onDismiss = menuState::dismiss
                                            )
                                        }
                                    )
                                } else {
                                    showEditNoteDialog = true
                                }
                            }
                        )
                    }
                }
                else -> {
                    Box(modifier = Modifier.fillMaxSize()) {
                        listOf(
                            Alignment.TopStart,
                            Alignment.TopEnd,
                            Alignment.BottomStart,
                            Alignment.BottomEnd,
                        ).fastForEachIndexed { index, alignment ->
                            AsyncImage(
                                model = playlist.thumbnails.getOrNull(index),
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .align(alignment)
                                    .fillMaxSize(0.5f)
                            )
                        }
                    }
                    if (editable) {
                        OverlayEditButton(
                            visible = true,
                            alignment = Alignment.BottomEnd,
                            onClick = {
                                if (isCustomThumbnail) {
                                    menuState.show(
                                        {
                                            CustomThumbnailMenu(
                                                onEdit = {
                                                    pickLauncher.launch(
                                                        PickVisualMediaRequest(mediaType = ActivityResultContracts.PickVisualMedia.ImageOnly)
                                                    )
                                                },
                                                onRemove = {
                                                    when {
                                                        playlist.playlist.browseId == null -> {
                                                            overrideThumbnail.value = null
                                                            database.query {
                                                                update(playlist.playlist.copy(thumbnailUrl = null))
                                                            }
                                                        }
                                                        else -> {
                                                            scope.launch(Dispatchers.IO) {
                                                                YouTube.removeThumbnailPlaylist(playlist.playlist.browseId).onSuccess { newThumbnailUrl ->
                                                                    overrideThumbnail.value = newThumbnailUrl
                                                                    database.query {
                                                                        update(playlist.playlist.copy(thumbnailUrl = newThumbnailUrl))
                                                                    }
                                                                }
                                                            }
                                                        }
                                                    }
                                                    isCustomThumbnail = false 
                                                },
                                                onDismiss = menuState::dismiss
                                            )
                                        }
                                    )
                                } else {
                                    showEditNoteDialog = true
                                }
                            }
                        )
                    }
                }
            }
        }

        
        Text(
            text = playlist.playlist.name,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 32.dp)
        )

        Spacer(modifier = Modifier.height(12.dp))

        
        val songCount = if (playlist.songCount == 0 && playlist.playlist.remoteSongCount != null) {
            playlist.playlist.remoteSongCount
        } else {
            playlist.songCount
        }
        Text(
            text = buildString {
                append(pluralStringResource(R.plurals.n_song, songCount, songCount))
                if (playlistLength > 0) {
                    append(" • ")
                    append(makeTimeString(playlistLength * 1000L))
                }
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
        )

        Spacer(modifier = Modifier.height(24.dp))

        
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            
            TextButton(
                onClick = {
                    playerConnection.playQueue(
                        ListQueue(
                            title = playlist.playlist.name,
                            items = songs.map { it.song.toMediaItem() },
                            contextId = "PL:" + playlist.playlist.id,
                        )
                    )
                },
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .tvFocusable(isTvOrCar, scaleFocused = 1f),
                shape = CircleShape,
                colors = androidx.compose.material3.ButtonDefaults.textButtonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                Icon(
                    painter = painterResource(R.drawable.play),
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.play),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
            }

            
            val playedForStart = rememberPlayedShuffleSet("PL:" + playlist.playlist.id)
            // Ask "continue or start over" only when this playlist already has no-repeat memory.
            val onShuffleClick = rememberShuffleMemoryPrompt(
                contextId = "PL:" + playlist.playlist.id,
                playedCount = songs.count { it.song.id in playedForStart },
                totalCount = songs.size,
            ) { resetMemory ->
                // UNPLAYED-FIRST start: slot 0 is what actually PLAYS first, and a uniform pick over
                // the whole list started with an already-heard song P/T of the time (the owner's
                // "repeats when I activate" symptom). Partitioning keeps the full list (the memory-
                // aware order re-sorts #2..N anyway) but guarantees an unheard opener while any remain.
                // After a reset the memory is empty, so a plain shuffle IS the unplayed-first order.
                val ordered = if (resetMemory) {
                    songs.shuffled()
                } else {
                    val (unheard, heard) = songs.partition { it.song.id !in playedForStart }
                    unheard.shuffled() + heard.shuffled()
                }
                playerConnection.playQueue(
                    ListQueue(
                        title = playlist.playlist.name,
                        items = ordered.map { it.song.toMediaItem() },
                        contextId = "PL:" + playlist.playlist.id,
                        // Turn shuffle MODE on so the enhanced no-repeat memory drives the order and
                        // records plays (pre-shuffling alone bypassed it → replayed played songs).
                        startShuffled = true,
                    )
                )
            }
            TextButton(
                onClick = onShuffleClick,
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .tvFocusable(isTvOrCar, scaleFocused = 1f),
                shape = CircleShape,
                colors = androidx.compose.material3.ButtonDefaults.textButtonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            ) {
                Icon(
                    painter = painterResource(R.drawable.shuffle),
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.shuffle),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            
            Surface(
                onClick = {
                    menuState.show {
                        LocalPlaylistMenu(
                            playlist = playlist,
                            songs = songs,
                            context = context,
                            downloadState = downloadState,
                            onAiModify = onShowAiModifyDialog,
                            onEdit = onShowEditDialog,
                            onSync = {
                                val browseId = playlist.playlist.browseId
                                when {
                                    browseId == null -> {}
                                    !isLoggedIn -> Toast.makeText(
                                        context,
                                        context.getString(R.string.sync_login_required),
                                        Toast.LENGTH_SHORT
                                    ).show()
                                    else -> {
                                        Toast.makeText(
                                            context,
                                            context.getString(R.string.playlist_syncing),
                                            Toast.LENGTH_SHORT
                                        ).show()
                                        // Route through the guarded single-playlist sync (an empty or
                                        // truncated remote page never wipes the local copy) and report the
                                        // real result — the old inline clear-and-reinsert could wipe the
                                        // playlist and always claimed success even when the fetch failed.
                                        scope.launch(Dispatchers.IO) {
                                            val ok = syncUtils.syncPlaylistNow(browseId, playlist.id)
                                            withContext(Dispatchers.Main) {
                                                snackbarHostState.showSnackbar(
                                                    context.getString(
                                                        if (ok) R.string.playlist_synced else R.string.playlist_sync_failed
                                                    )
                                                )
                                            }
                                        }
                                    }
                                }
                            },
                            onDelete = onshowDeletePlaylistDialog,
                            onDownload = {
                                when (downloadState) {
                                    Download.STATE_COMPLETED -> onShowRemoveDownloadDialog()
                                    Download.STATE_DOWNLOADING -> {
                                        songs.forEach { song ->
                                            DownloadService.sendRemoveDownload(
                                                context,
                                                ExoDownloadService::class.java,
                                                song.song.id,
                                                false
                                            )
                                        }
                                    }
                                    else -> {
                                        songs.forEach { song ->
                                            val downloadRequest = DownloadRequest
                                                .Builder(song.song.id, song.song.id.toUri())
                                                .setCustomCacheKey(song.song.id)
                                                .setData(song.song.song.title.toByteArray())
                                                .build()
                                            DownloadService.sendAddDownload(
                                                context,
                                                ExoDownloadService::class.java,
                                                downloadRequest,
                                                false
                                            )
                                        }
                                    }
                                }
                            },
                            onQueue = {
                                playerConnection.addToQueue(
                                    items = songs.map { it.song.toMediaItem() }
                                )
                            },
                            onDismiss = { menuState.dismiss() }
                        )
                    }
                },
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.size(48.dp).tvFocusable(isTvOrCar, scaleFocused = 1f)
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(R.drawable.more_vert),
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Enhanced Shuffle: "activo · X/Y reproducidas" pill — visible whenever the feature is ON, so the
        // user knows this playlist's shuffle carries persistent no-repeat memory and can watch it progress.
        run {
            val playedSet = rememberPlayedShuffleSet("PL:" + playlist.playlist.id)
            val playedCount = remember(playedSet, songs) { songs.count { it.song.id in playedSet } }
            EnhancedShuffleChip(
                playedCount = playedCount,
                total = songs.size,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(top = 12.dp),
            )
        }

        Spacer(Modifier.height(24.dp))

        val staticDescription = remember(songCount, playlistLength) {
            val name = playlist.playlist.name
            val trackCountText = context.resources.getQuantityString(R.plurals.n_song, songCount, songCount)
            "$name is a custom playlist featuring $trackCountText.${
                if (playlistLength > 0) " Combined duration is ${makeTimeString(playlistLength * 1000L)}." else ""
            }"
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
        ) {
            Text(
                text = stringResource(R.string.about_playlist),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            ExpandableText(
                text = staticDescription,
                runs = null,
                collapsedMaxLines = 3
            )
        }
    }
}

@Composable
private fun MetadataChip(
    icon: Int,
    text: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                painter = painterResource(icon),
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
    }
}

fun uriToByteArray(context: Context, uri: Uri): ByteArray? {
    return try {
        context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
    } catch (_: SecurityException) {
        null
    }
}
