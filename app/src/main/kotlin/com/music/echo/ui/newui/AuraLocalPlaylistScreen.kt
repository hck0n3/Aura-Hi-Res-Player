package iad1tya.echo.music.ui.newui

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadRequest
import androidx.media3.exoplayer.offline.DownloadService
import androidx.navigation.NavController
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
import iad1tya.echo.music.playback.ShuffleContexts
import iad1tya.echo.music.playback.queues.ListQueue
import iad1tya.echo.music.ui.component.DefaultDialog
import iad1tya.echo.music.ui.component.DraggableScrollbar
import iad1tya.echo.music.ui.component.EnhancedShuffleChip
import iad1tya.echo.music.ui.component.LocalMenuState
import iad1tya.echo.music.ui.component.OverlayEditButton
import iad1tya.echo.music.ui.component.TextFieldDialog
import iad1tya.echo.music.ui.component.rememberPlayedShuffleSet
import iad1tya.echo.music.ui.component.rememberShuffleMemoryPrompt
import iad1tya.echo.music.ui.menu.LocalPlaylistMenu
import iad1tya.echo.music.ui.menu.SelectionSongMenu
import iad1tya.echo.music.ui.menu.SongMenu
import iad1tya.echo.music.ui.screens.playlist.AddMusicSheet
import iad1tya.echo.music.ui.screens.playlist.AiModifyPlaylistDialog
import iad1tya.echo.music.ui.screens.playlist.FeaturedArtistsSection
import iad1tya.echo.music.ui.screens.playlist.SuggestedSongsSection
import iad1tya.echo.music.ui.screens.playlist.rememberPlaylistCoverEditor
import iad1tya.echo.music.ui.screens.playlist.rememberSongPreviewController
import iad1tya.echo.music.ui.utils.rememberIsTvOrCar
import iad1tya.echo.music.ui.utils.rememberIsWideLayout
import iad1tya.echo.music.ui.utils.tvFocusable
import iad1tya.echo.music.utils.makeTimeString
import iad1tya.echo.music.utils.rememberEnumPreference
import iad1tya.echo.music.utils.rememberPreference
import iad1tya.echo.music.utils.reportException
import iad1tya.echo.music.viewmodels.LocalPlaylistViewModel
import java.time.LocalDateTime
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

/**
 * # Lista propia — "Interfaz nueva"
 *
 * The redesigned `LocalPlaylistScreen` (route `local_playlist/{playlistId}`). This is the screen with
 * the most controls in the whole app, and the one where something is most easily lost, so the list
 * below is what it carries and where each control now lives:
 *
 *  · **Reordenar arrastrando** — kept. Same `sh.calvin.reorderable` state, same commit
 *    (`database.move` + `lastUpdateTime`) and the same `YouTube.moveSongPlaylist` mirror for a synced
 *    playlist. The handle moved from the right of the row to its LEFT, where the redesigned queue
 *    already puts it; it appears under exactly the classic conditions (orden personalizado, lista
 *    desbloqueada, editable, sin buscar, sin selección).
 *  · **Deslizar para quitar** — kept, still behind `SwipeToRemoveSongKey` and still disabled while
 *    locked or selecting, with the same `removeFromPlaylist` + reposition transaction.
 *  · **Candado** — the sort row's trailing control.
 *  · **Editar portada** — the ✎ on the cover, running the SAME
 *    [rememberPlaylistCoverEditor] the classic header now runs (it was lifted out of it, not copied).
 *  · **Renombrar / Sincronizar / Eliminar / Descargar / Añadir a la cola / Editar con IA** — the ⋯ of
 *    the header, i.e. the classic [LocalPlaylistMenu] with the classic lambdas.
 *  · **Añadir música, Sugeridas, Artistas destacados** — the classic footer sections, reused as they
 *    are: they own a real state machine (shimmer, per-row "+", preview) and `SongListItem` already
 *    adopts the Aura palette under the flag.
 *
 * Presentation only: every action is the classic lambda, and the queue keeps the classic
 * `contextId = "PL:<id>"`, so the "Aleatorio mejorado" memory is the same bucket in both UIs.
 *
 * @param scrollBehavior accepted for signature parity with the classic screen; this shape draws its own
 *   header instead of a `TopAppBar`, so there is no collapsing bar to drive with it.
 */
@Suppress("UNUSED_PARAMETER")
@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun AuraLocalPlaylistScreen(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
    viewModel: LocalPlaylistViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val menuState = LocalMenuState.current
    val database = LocalDatabase.current
    val haptic = LocalHapticFeedback.current
    val focusManager = LocalFocusManager.current
    val playerConnection = LocalPlayerConnection.current ?: return
    val downloadUtil = LocalDownloadUtil.current
    val isTvOrCar = rememberIsTvOrCar()

    val isPlaying by playerConnection.isEffectivelyPlaying.collectAsState()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsState()

    val playlist by viewModel.playlist.collectAsState()
    val songs by viewModel.playlistSongs.collectAsState()
    val contextId = playlist?.playlist?.let {
        ShuffleContexts.forPlaylist(it.isEditable, it.id, it.browseId)
    } ?: ("PL:" + viewModel.playlistId)
    val shufflePlayedSet = rememberPlayedShuffleSet(contextId)
    val mutableSongs = remember { mutableStateListOf<PlaylistSong>() }

    val (sortType, onSortTypeChange) = rememberEnumPreference(
        PlaylistSongSortTypeKey,
        PlaylistSongSortType.CUSTOM,
    )
    val (sortDescending, onSortDescendingChange) = rememberPreference(
        PlaylistSongSortDescendingKey,
        true,
    )
    var locked by rememberPreference(PlaylistEditLockKey, defaultValue = true)
    val swipeRemoveEnabled by rememberPreference(SwipeToRemoveSongKey, defaultValue = false)

    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    // Apple-Music-style "Añadir música" footer (editable playlists only — see the gate below).
    var showAddMusicSheet by remember { mutableStateOf(false) }
    val previewController = rememberSongPreviewController()

    var isSearching by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(isSearching) {
        if (isSearching) focusRequester.requestFocus()
    }

    val filteredSongs = remember(songs, query) {
        if (query.isEmpty()) songs
        else songs.filter { song ->
            song.song.song.title.contains(query, ignoreCase = true) ||
                song.song.artists.any { it.name.contains(query, ignoreCase = true) }
        }
    }

    var inSelectMode by rememberSaveable { mutableStateOf(false) }
    val selection = rememberSaveable(
        saver = listSaver<MutableList<Int>, Int>(
            save = { it.toList() },
            restore = { it.toMutableStateList() },
        ),
    ) { mutableStateListOf<Int>() }
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
            query = ""
        }
    } else if (inSelectMode) {
        BackHandler(onBack = onExitSelectionMode)
    }

    val editable: Boolean = playlist?.playlist?.isEditable == true

    LaunchedEffect(songs) {
        selection.toList().forEach { mapId ->
            if (songs.none { it.map.id == mapId }) selection.remove(Integer.valueOf(mapId))
        }
    }

    var downloadState by remember { mutableIntStateOf(Download.STATE_STOPPED) }
    LaunchedEffect(songs) {
        mutableSongs.apply {
            clear()
            addAll(songs)
        }
        if (songs.isEmpty()) return@LaunchedEffect
        downloadUtil.downloads.collect { downloads ->
            downloadState = when {
                songs.all { downloads[it.song.id]?.state == Download.STATE_COMPLETED } ->
                    Download.STATE_COMPLETED

                songs.all {
                    downloads[it.song.id]?.state == Download.STATE_QUEUED ||
                        downloads[it.song.id]?.state == Download.STATE_DOWNLOADING ||
                        downloads[it.song.id]?.state == Download.STATE_COMPLETED
                } -> Download.STATE_DOWNLOADING

                else -> Download.STATE_STOPPED
            }
        }
    }

    // ── Diálogos ──────────────────────────────────────────────────────────────────────────────────

    var showEditDialog by remember { mutableStateOf(false) }
    if (showEditDialog) {
        playlist?.playlist?.let { playlistEntity ->
            TextFieldDialog(
                icon = {
                    Icon(painter = painterResource(R.drawable.edit), contentDescription = null)
                },
                title = { Text(text = stringResource(R.string.edit_playlist)) },
                onDismiss = { showEditDialog = false },
                initialTextFieldValue = TextFieldValue(
                    playlistEntity.name,
                    TextRange(playlistEntity.name.length),
                ),
                onDone = { name ->
                    database.query {
                        update(playlistEntity.copy(name = name, lastUpdateTime = LocalDateTime.now()))
                    }
                    viewModel.viewModelScope.launch(Dispatchers.IO) {
                        playlistEntity.browseId?.let { YouTube.renamePlaylist(it, name) }
                    }
                },
            )
        }
    }

    // "Editar con IA" — same feature toggle that gates the create-playlist AI FAB in Biblioteca.
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

    var showRemoveDownloadDialog by remember { mutableStateOf(false) }
    if (showRemoveDownloadDialog) {
        DefaultDialog(
            onDismiss = { showRemoveDownloadDialog = false },
            content = {
                Text(
                    text = stringResource(
                        R.string.remove_download_playlist_confirm,
                        playlist?.playlist?.name.orEmpty(),
                    ),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(horizontal = 18.dp),
                )
            },
            buttons = {
                TextButton(onClick = { showRemoveDownloadDialog = false }) {
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
                                false,
                            )
                        }
                    },
                ) {
                    Text(text = stringResource(android.R.string.ok))
                }
            },
        )
    }

    var showDeletePlaylistDialog by remember { mutableStateOf(false) }
    if (showDeletePlaylistDialog) {
        val ytBrowseId = playlist?.playlist?.browseId

        // Local delete only: removes the playlist from the app, never from YouTube. A SYNCED playlist
        // keeps its row as a tombstone (bookmarkedAt = null) instead of being deleted — the account
        // still has it, so the next sync would re-create it and the deletion would undo itself.
        // [alsoDeletedRemotely] = "delete from YouTube too": the account copy is going away, so delete
        // the row for real — a tombstone would stop the sync from ever restoring the playlist if the
        // remote delete FAILS.
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
                        playlist?.playlist?.name.orEmpty(),
                    ),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(horizontal = 18.dp),
                )

                // Local-only playlist: SAY SO. Without this the missing "delete from YouTube too"
                // option reads as a broken dialog instead of what it is.
                if (ytBrowseId == null) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.delete_playlist_only_local_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 18.dp),
                    )
                }

                if (ytBrowseId != null) {
                    Spacer(Modifier.height(16.dp))
                    TextButton(
                        onClick = {
                            deletePlaylistLocally(true)
                            viewModel.viewModelScope.launch(Dispatchers.IO) {
                                // Surface a remote failure: fire-and-forget made a YouTube rejection
                                // look like success (the playlist vanished locally either way).
                                YouTube.deletePlaylist(ytBrowseId).onFailure { e ->
                                    reportException(e)
                                    withContext(Dispatchers.Main) {
                                        Toast.makeText(
                                            context,
                                            context.getString(R.string.delete_playlist_youtube_failed),
                                            Toast.LENGTH_LONG,
                                        ).show()
                                    }
                                }
                            }
                            navController.popBackStack()
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(text = stringResource(R.string.delete_playlist_from_youtube_too))
                    }
                    TextButton(
                        onClick = {
                            deletePlaylistLocally(false)
                            navController.popBackStack()
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(text = stringResource(R.string.delete_playlist_local_only))
                    }
                }
            },
            buttons = {
                TextButton(onClick = { showDeletePlaylistDialog = false }) {
                    Text(text = stringResource(android.R.string.cancel))
                }
                if (ytBrowseId == null) {
                    TextButton(
                        onClick = {
                            deletePlaylistLocally(false)
                            navController.popBackStack()
                        },
                    ) {
                        Text(text = stringResource(android.R.string.ok))
                    }
                }
            },
        )
    }

    // ── Reordenar arrastrando ─────────────────────────────────────────────────────────────────────

    val lazyListState = rememberLazyListState()
    var dragInfo by remember { mutableStateOf<Pair<Int, Int>?>(null) }

    // Key-based index mapping instead of the classic "lazy index − headerItems" arithmetic: the number
    // of leading items changes with search mode, and a stale constant would move the WRONG song and
    // corrupt the stored order. A drop over a non-song item resolves to -1 and is ignored.
    fun songIndexOfKey(key: Any?): Int = mutableSongs.indexOfFirst { it.map.id == key }

    val reorderableState = rememberReorderableLazyListState(
        lazyListState = lazyListState,
        scrollThresholdPadding = LocalPlayerAwareWindowInsets.current.asPaddingValues(),
    ) { from, to ->
        val safeFrom = songIndexOfKey(from.key)
        val safeTo = songIndexOfKey(to.key)
        if (safeFrom >= 0 && safeTo >= 0) {
            val currentDragInfo = dragInfo
            dragInfo = if (currentDragInfo == null) safeFrom to safeTo else currentDragInfo.first to safeTo
            mutableSongs.move(safeFrom, safeTo)
        }
    }

    // Commit exactly the way the classic screen does: the local move, the playlist timestamp, and for a
    // synced playlist the YouTube mirror driven by the successor's setVideoId.
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
                                successorSetVideoId,
                            )
                        }
                    }
                }

                dragInfo = null
            }
        }
    }

    val canDrag = sortType == PlaylistSongSortType.CUSTOM && !locked && !inSelectMode &&
        !isSearching && editable
    val canSwipeRemove = swipeRemoveEnabled && !locked && !inSelectMode

    val bloom = rememberAuraBloom(mediaMetadata?.id)
    val rows = if (isSearching) filteredSongs else mutableSongs

    Box(modifier = Modifier.fillMaxSize().auraScreenBackground(bloom, intensity = 0.40f)) {
        Column(Modifier.fillMaxSize()) {
            if (isSearching) {
                AuraInlineSearchField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = stringResource(R.string.search),
                    focusRequester = focusRequester,
                    onSearch = { focusManager.clearFocus() },
                    modifier = Modifier
                        .windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Top))
                        .padding(start = 44.dp),
                )
            }
            LazyColumn(
            state = lazyListState,
            contentPadding = if (isSearching) {
                LocalPlayerAwareWindowInsets.current
                    .union(WindowInsets.ime)
                    .only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal)
                    .asPaddingValues()
            } else {
                LocalPlayerAwareWindowInsets.current.union(WindowInsets.ime).asPaddingValues()
            },
            modifier = Modifier
                .weight(1f)
                .fillMaxSize(),
        ) {
            val current = playlist
            if (current != null) {
                if (current.songCount == 0 && current.playlist.remoteSongCount == 0) {
                    item(key = "aura_lp_empty") {
                        AuraEmpty(
                            text = stringResource(R.string.playlist_is_empty),
                            modifier = Modifier.animateItem(),
                        )
                    }
                } else {
                    if (!isSearching) {
                        item(key = "aura_lp_header") {
                            AuraLocalPlaylistHeader(
                                playlist = current,
                                songs = songs,
                                contextId = contextId,
                                snackbarHostState = snackbarHostState,
                                onSearch = { isSearching = true },
                                onMenu = {
                                    menuState.show {
                                        AuraLocalPlaylistMenuHost(
                                            playlist = current,
                                            songs = songs,
                                            downloadState = downloadState,
                                            snackbarHostState = snackbarHostState,
                                            onAiModify = if (canAiModify) {
                                                { showAiModifyDialog = true }
                                            } else null,
                                            onEdit = { showEditDialog = true },
                                            onDelete = { showDeletePlaylistDialog = true },
                                            onShowRemoveDownloadDialog = {
                                                showRemoveDownloadDialog = true
                                            },
                                            onDismiss = menuState::dismiss,
                                        )
                                    }
                                },
                                modifier = Modifier.animateItem(),
                            )
                        }
                    }

                    item(key = "aura_lp_sort") {
                        AuraInlineSortControl(
                            sortType = sortType,
                            sortDescending = sortDescending,
                            options = listOf(
                                PlaylistSongSortType.CUSTOM to R.string.sort_by_custom,
                                PlaylistSongSortType.CREATE_DATE to R.string.sort_by_create_date,
                                PlaylistSongSortType.NAME to R.string.sort_by_name,
                                PlaylistSongSortType.ARTIST to R.string.sort_by_artist,
                                PlaylistSongSortType.PLAY_TIME to R.string.sort_by_play_time,
                            ),
                            onSortTypeChange = onSortTypeChange,
                            onSortDescendingChange = onSortDescendingChange,
                            trailing = {
                                if (editable) {
                                    AuraDrawableIconButton(
                                        painterId = if (locked) R.drawable.lock else R.drawable.lock_open,
                                        contentDescription = stringResource(
                                            if (locked) R.string.aura_unlock_playlist
                                            else R.string.aura_lock_playlist,
                                        ),
                                        onClick = { locked = !locked },
                                        size = 18.dp,
                                        tint = if (locked) AuraPalette.Teal else AuraPalette.OnGroundMuted,
                                    )
                                }
                                AuraSongCountLabel(rows.size)
                            },
                        )
                    }
                }
            }

            itemsIndexed(
                items = rows,
                // `map.id` is the PlaylistSongMap primary key — unique by construction, one row per
                // entry even when the same song appears twice in the playlist.
                key = { _, song -> song.map.id },
            ) { index, song ->
                ReorderableItem(state = reorderableState, key = song.map.id) {
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
                                            setVideoIdValue,
                                        )
                                    }
                                }
                            }
                            move(currentItem.map.playlistId, currentItem.map.position, Int.MAX_VALUE)
                            delete(currentItem.map.copy(position = Int.MAX_VALUE))
                            playlist?.playlist?.let { update(it.copy(lastUpdateTime = LocalDateTime.now())) }
                        }
                    }

                    val dismissBoxState = rememberSwipeToDismissBoxState(
                        positionalThreshold = { totalDistance -> totalDistance },
                    )
                    var processedDismiss by remember { mutableStateOf(false) }
                    LaunchedEffect(dismissBoxState.currentValue) {
                        val dv = dismissBoxState.currentValue
                        if (canSwipeRemove && !processedDismiss && (
                                dv == SwipeToDismissBoxValue.StartToEnd ||
                                    dv == SwipeToDismissBoxValue.EndToStart
                                )
                        ) {
                            processedDismiss = true
                            deleteFromPlaylist()
                        }
                        if (dv == SwipeToDismissBoxValue.Settled) processedDismiss = false
                    }

                    val selected = selection.contains(song.map.id)
                    val onCheckedChange: (Boolean) -> Unit = { checked ->
                        if (checked) selection.add(song.map.id)
                        else selection.remove(Integer.valueOf(song.map.id))
                    }

                    val isActive = song.song.id == mediaMetadata?.id
                    val alreadyPlayed = song.song.song.totalPlayTime > 0L
                    val dimmed = (song.song.id in shufflePlayedSet || alreadyPlayed) && !isActive

                    val dragHandle: (@Composable () -> Unit)? = if (canDrag) {
                        {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .sizeIn(
                                        minWidth = AuraSpacing.MinTouchTarget,
                                        minHeight = AuraSpacing.MinTouchTarget,
                                    )
                                    .clip(CircleShape)
                                    .draggableHandle(),
                            ) {
                                AuraIconGlyph(
                                    icon = AuraIcons.DragHandle,
                                    contentDescription = stringResource(R.string.cd_reorder),
                                    size = 21.dp,
                                    tint = AuraPalette.OnGroundDisabled,
                                )
                            }
                        }
                    } else null

                    val row: @Composable () -> Unit = {
                        AuraSongRow(
                            title = song.song.song.title,
                            subtitle = song.song.artists.joinToString { it.name },
                            thumbnailUrl = song.song.song.thumbnailUrl,
                            seed = song.song.id,
                            isActive = isActive,
                            isPlaying = isPlaying,
                            liked = song.song.song.liked,
                            explicit = song.song.song.explicit,
                            downloadId = song.song.id,
                            format = song.song.format,
                            playedInShuffle = dimmed,
                            artworkSize = 50.dp,
                            artworkRatio = 1f,
                            artworkShape = AuraShapes.Artwork,
                            typeChip = null,
                            leading = dragHandle,
                            selected = selected.takeIf { inSelectMode },
                            onSelectedChange = if (inSelectMode) onCheckedChange else null,
                            durationLabel = auraAppleDurationLabel(song.song.song.duration),
                            showQualityBadge = false,
                            onClick = {
                                if (inSelectMode) {
                                    onCheckedChange(!selected)
                                } else {
                                    // Starting real playback must stop any active footer/sheet preview
                                    // so two songs don't play at once.
                                    previewController.stop()
                                    if (isActive) {
                                        playerConnection.togglePlayPause()
                                    } else {
                                        playerConnection.playQueue(
                                            ListQueue(
                                                title = current?.playlist?.name.orEmpty(),
                                                items = songs.map { it.song.toMediaItem() },
                                                startIndex = songs.indexOfFirst { it.map.id == song.map.id },
                                                contextId = contextId,
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
                            onMenuClick = if (inSelectMode) null else {
                                {
                                    menuState.show {
                                        SongMenu(
                                            originalSong = song.song,
                                            playlistSong = song,
                                            playlistBrowseId = playlist?.playlist?.browseId,
                                            navController = navController,
                                            onDismiss = menuState::dismiss,
                                        )
                                    }
                                }
                            },
                            modifier = Modifier.tvFocusable(
                                isTvOrCar,
                                AuraShapes.Highlight,
                                scaleFocused = 1f,
                            ),
                        )
                    }

                    AuraAppleListRowFrame(
                        showDivider = index < rows.lastIndex,
                        dividerInset = AuraAppleCoverDividerInset,
                        modifier = Modifier.animateItem(),
                    ) {
                        if (!canSwipeRemove) {
                            Box { row() }
                        } else {
                            SwipeToDismissBox(
                                state = dismissBoxState,
                                backgroundContent = {},
                            ) { row() }
                        }
                    }
                }
            }

            if (editable && !isSearching && !inSelectMode) {
                item(key = "aura_lp_add_music") {
                    AuraAddMusicButton(
                        onClick = {
                            // Stop any footer preview so it doesn't overlap the sheet's own preview.
                            previewController.stop()
                            showAddMusicSheet = true
                        },
                        modifier = Modifier.animateItem(),
                    )
                }
                item(key = "aura_lp_suggested") {
                    SuggestedSongsSection(
                        viewModel = viewModel,
                        previewController = previewController,
                        playerConnection = playerConnection,
                        modifier = Modifier.animateItem(),
                    )
                }
                item(key = "aura_lp_featured") {
                    FeaturedArtistsSection(
                        viewModel = viewModel,
                        navController = navController,
                        modifier = Modifier.animateItem(),
                    )
                }
            }

            item(key = "aura_lp_tail") { Spacer(Modifier.height(50.dp)) }
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
                    LocalPlayerAwareWindowInsets.current.union(WindowInsets.ime).asPaddingValues(),
                )
                .align(Alignment.CenterEnd),
            scrollState = lazyListState,
            headerItems = 2,
        )

        // Sticky chrome: back only. Title/search stay in the scrolling header (owner: no black
        // sticky bar with playlist name + search).
        AuraDetailTopBar(
            listState = lazyListState,
            title = playlist?.playlist?.name.orEmpty(),
            onBack = {
                when {
                    isSearching -> {
                        isSearching = false
                        query = ""
                        focusManager.clearFocus()
                    }

                    inSelectMode -> onExitSelectionMode()
                    else -> navController.navigateUp()
                }
            },
            inSelectMode = inSelectMode,
            selectionCount = selection.size,
            forceOpaque = false,
            pinTitleOnScroll = false,
            selectionActions = {
                Checkbox(
                    checked = selection.size == songs.size && selection.isNotEmpty(),
                    onCheckedChange = {
                        if (selection.size == songs.size) {
                            selection.clear()
                        } else {
                            selection.clear()
                            selection.addAll(songs.map { it.map.id })
                        }
                    },
                    colors = CheckboxDefaults.colors(
                        checkedColor = AuraPalette.Teal,
                        uncheckedColor = AuraPalette.OnGroundDisabled,
                        checkmarkColor = AuraPalette.OnAccent,
                    ),
                )
                AuraIconButton(
                    icon = AuraIcons.More,
                    contentDescription = stringResource(R.string.cd_selection_actions),
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
                                clearAction = onExitSelectionMode,
                            )
                        }
                    },
                    size = 20.dp,
                )
            },
        )

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .windowInsetsPadding(LocalPlayerAwareWindowInsets.current.union(WindowInsets.ime))
                .align(Alignment.BottomCenter),
        )
    }
}

// ── Cabecera ──────────────────────────────────────────────────────────────────────────────────────

/**
 * Cover (with its ✎), title, count line, Aleatorio / Reproducir / ⋯, the "Aleatorio mejorado" pill and
 * "Acerca de".
 */
@Composable
private fun AuraLocalPlaylistHeader(
    playlist: Playlist,
    songs: List<PlaylistSong>,
    contextId: String,
    snackbarHostState: SnackbarHostState,
    onSearch: () -> Unit,
    onMenu: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val playerConnection = LocalPlayerConnection.current ?: return
    val isTvOrCar = rememberIsTvOrCar()
    val isWideLayout = rememberIsWideLayout()
    val editable = playlist.playlist.isEditable
    val coverEditor = rememberPlaylistCoverEditor(playlist, snackbarHostState)

    val playlistLength = remember(songs) { songs.sumOf { it.song.song.duration } }
    val songCount = if (playlist.songCount == 0 && playlist.playlist.remoteSongCount != null) {
        playlist.playlist.remoteSongCount
    } else {
        playlist.songCount
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(56.dp))

        val coverSize: Dp = if (isWideLayout) 320.dp else 260.dp
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 48.dp),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .width(coverSize)
                    .aspectRatio(1f)
                    .clip(AuraShapes.PlayerArtwork),
            ) {
                when (playlist.thumbnails.size) {
                    0 -> AuraCover(
                        thumbnailUrl = null,
                        size = coverSize,
                        seed = contextId,
                        shape = AuraShapes.PlayerArtwork,
                    )

                    1 -> AuraCover(
                        thumbnailUrl = coverEditor.displayThumbnail,
                        size = coverSize,
                        seed = contextId,
                        shape = AuraShapes.PlayerArtwork,
                        decodeTo = 512,
                    )

                    // The classic 2×2 mosaic of the four first covers.
                    else -> Column(Modifier.fillMaxSize()) {
                        repeat(2) { rowIndex ->
                            Row(Modifier.weight(1f)) {
                                repeat(2) { columnIndex ->
                                    val index = rowIndex * 2 + columnIndex
                                    AuraCover(
                                        thumbnailUrl = playlist.thumbnails.getOrNull(index),
                                        size = coverSize / 2,
                                        seed = "$contextId#$index",
                                        shape = androidx.compose.ui.graphics.RectangleShape,
                                        decodeTo = 256,
                                    )
                                }
                            }
                        }
                    }
                }
                if (editable) {
                    OverlayEditButton(
                        visible = true,
                        alignment = Alignment.BottomEnd,
                        onClick = coverEditor.onEditCoverClick,
                    )
                }
            }
        }

        Spacer(Modifier.height(26.dp))

        Text(
            text = playlist.playlist.name,
            style = AuraType.ScreenTitle,
            color = AuraPalette.OnGround,
            maxLines = 2,
            overflow = AuraDefaultOverflow,
            modifier = Modifier.padding(horizontal = 32.dp),
        )

        Spacer(Modifier.height(6.dp))

        Text(
            text = buildString {
                append(pluralStringResource(R.plurals.n_song, songCount, songCount))
                if (playlistLength > 0) {
                    append(" • ")
                    append(makeTimeString(playlistLength * 1000L))
                }
            },
            style = AuraType.RowSubtitle,
            color = AuraPalette.OnGroundMuted,
            maxLines = 1,
            overflow = AuraDefaultOverflow,
        )

        run {
            val playedSet = rememberPlayedShuffleSet(contextId)
            val playedCount = remember(playedSet, songs) {
                songs.count { it.song.id in playedSet || it.song.song.totalPlayTime > 0L }
            }
            EnhancedShuffleChip(
                playedCount = playedCount,
                total = songs.size,
                modifier = Modifier.padding(top = 10.dp),
            )
        }

        Spacer(Modifier.height(18.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AuraSpacing.Gutter),
            horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val playedForStart = rememberPlayedShuffleSet(contextId)
            val onShuffleClick = rememberShuffleMemoryPrompt(
                contextId = contextId,
                playedCount = songs.count {
                    it.song.id in playedForStart || it.song.song.totalPlayTime > 0L
                },
                totalCount = songs.size,
            ) { resetMemory ->
                val seed = ShuffleContexts.seedPlayedIds(
                    resetMemory = resetMemory,
                    songIds = songs.map { it.song.id },
                    shufflePlayed = playedForStart,
                    playTimeMs = { id ->
                        songs.firstOrNull { it.song.id == id }?.song?.song?.totalPlayTime ?: 0L
                    },
                )
                val ordered = if (resetMemory) {
                    songs.shuffled()
                } else {
                    val (unheard, heard) = songs.partition { it.song.id !in seed }
                    unheard.shuffled() + heard.shuffled()
                }
                playerConnection.playQueue(
                    ListQueue(
                        title = playlist.playlist.name,
                        items = ordered.map { it.song.toMediaItem() },
                        contextId = contextId,
                        startShuffled = true,
                        seedPlayedIds = seed,
                    ),
                )
            }

            AuraHeaderButton(
                icon = AuraIcons.Shuffle,
                label = stringResource(R.string.shuffle),
                onClick = onShuffleClick,
                accent = false,
                modifier = Modifier.weight(1f).tvFocusable(isTvOrCar, scaleFocused = 1f),
            )
            AuraHeaderButton(
                icon = AuraIcons.Play,
                label = stringResource(R.string.play),
                onClick = {
                    playerConnection.playQueue(
                        ListQueue(
                            title = playlist.playlist.name,
                            items = songs.map { it.song.toMediaItem() },
                            contextId = contextId,
                        ),
                    )
                },
                accent = true,
                modifier = Modifier.weight(1f).tvFocusable(isTvOrCar, scaleFocused = 1f),
            )
        }

        Spacer(modifier.height(12.dp))

        // Search · Más — secondary row so labeled Play/Shuffle stay centered.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AuraSpacing.Gutter),
            horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AuraHeaderCircleButton(
                icon = AuraIcons.Search,
                contentDescription = stringResource(R.string.search),
                onClick = onSearch,
                modifier = Modifier.tvFocusable(isTvOrCar, scaleFocused = 1f),
            )
            AuraHeaderCircleButton(
                icon = AuraIcons.More,
                contentDescription = stringResource(R.string.cd_playlist_more_options),
                onClick = onMenu,
                modifier = Modifier.tvFocusable(isTvOrCar, scaleFocused = 1f),
            )
        }

        Spacer(modifier.height(18.dp))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AuraSpacing.Gutter),
        ) {
            AuraSectionLabel(text = stringResource(R.string.about_playlist).uppercase(Locale.ROOT))
            Spacer(Modifier.height(6.dp))
            AuraExpandableText(
                text = stringResource(R.string.aura_local_playlist_about, playlist.playlist.name),
                collapsedMaxLines = 3,
            )
        }
    }
}

/**
 * The classic [LocalPlaylistMenu] with the classic lambdas. It lives in its own composable because
 * "Sincronizar" needs [LocalSyncUtils] and the cookie gate, i.e. a composition scope, and the sheet is
 * opened from a lambda.
 */
@Composable
private fun AuraLocalPlaylistMenuHost(
    playlist: Playlist,
    songs: List<PlaylistSong>,
    downloadState: Int,
    snackbarHostState: SnackbarHostState,
    onAiModify: (() -> Unit)?,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onShowRemoveDownloadDialog: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val playerConnection = LocalPlayerConnection.current ?: return
    val syncUtils = LocalSyncUtils.current
    val scope = rememberCoroutineScope()
    // Same cookie gate the rest of the app uses to know if the user is signed into YouTube Music.
    val (innerTubeCookie) = rememberPreference(InnerTubeCookieKey, "")
    val isLoggedIn = remember(innerTubeCookie) { "SAPISID" in parseCookieString(innerTubeCookie) }

    LocalPlaylistMenu(
        playlist = playlist,
        songs = songs,
        context = context,
        downloadState = downloadState,
        onAiModify = onAiModify,
        onEdit = onEdit,
        onSync = {
            val browseId = playlist.playlist.browseId
            when {
                browseId == null -> {}
                !isLoggedIn -> Toast.makeText(
                    context,
                    context.getString(R.string.sync_login_required),
                    Toast.LENGTH_SHORT,
                ).show()

                else -> {
                    Toast.makeText(
                        context,
                        context.getString(R.string.playlist_syncing),
                        Toast.LENGTH_SHORT,
                    ).show()
                    // The guarded single-playlist sync (an empty or truncated remote page never wipes
                    // the local copy), reporting the real result.
                    scope.launch(Dispatchers.IO) {
                        val ok = syncUtils.syncPlaylistNow(browseId, playlist.id)
                        withContext(Dispatchers.Main) {
                            snackbarHostState.showSnackbar(
                                context.getString(
                                    if (ok) R.string.playlist_synced else R.string.playlist_sync_failed,
                                ),
                            )
                        }
                    }
                }
            }
        },
        onDelete = onDelete,
        onDownload = {
            when (downloadState) {
                Download.STATE_COMPLETED -> onShowRemoveDownloadDialog()
                Download.STATE_DOWNLOADING -> songs.forEach { song ->
                    DownloadService.sendRemoveDownload(
                        context,
                        ExoDownloadService::class.java,
                        song.song.id,
                        false,
                    )
                }

                else -> songs.forEach { song ->
                    val downloadRequest = DownloadRequest
                        .Builder(song.song.id, song.song.id.toUri())
                        .setCustomCacheKey(song.song.id)
                        .setData(song.song.song.title.toByteArray())
                        .build()
                    DownloadService.sendAddDownload(
                        context,
                        ExoDownloadService::class.java,
                        downloadRequest,
                        false,
                    )
                }
            }
        },
        onQueue = {
            playerConnection.addToQueue(items = songs.map { it.song.toMediaItem() })
        },
        onDismiss = onDismiss,
    )
}

// ── Muebles ───────────────────────────────────────────────────────────────────────────────────────

/** "Añadir música" — the classic footer's primary action, in the new language. */
@Composable
private fun AuraAddMusicButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AuraHeaderButton(
        icon = AuraIcons.Plus,
        label = stringResource(R.string.add_music),
        onClick = onClick,
        accent = true,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = AuraSpacing.Gutter, vertical = 10.dp),
    )
}

/** [AuraIconButton] for a glyph that only exists as a drawable (the padlock). */
@Composable
private fun AuraDrawableIconButton(
    painterId: Int,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 20.dp,
    tint: Color = AuraPalette.OnGround,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .sizeIn(minWidth = AuraSpacing.MinTouchTarget, minHeight = AuraSpacing.MinTouchTarget)
            .clip(CircleShape)
            .auraClickableInternal(onClick = onClick, contentDescription = contentDescription),
    ) {
        Icon(
            painter = painterResource(painterId),
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(size),
        )
    }
}
