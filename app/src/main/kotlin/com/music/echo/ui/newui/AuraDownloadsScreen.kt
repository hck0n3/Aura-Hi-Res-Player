package iad1tya.echo.music.ui.newui

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults.Indicator
import androidx.compose.material3.pulltorefresh.pullToRefresh
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadRequest
import androidx.media3.exoplayer.offline.DownloadService
import androidx.navigation.NavController
import com.music.innertube.utils.parseCookieString
import iad1tya.echo.music.LocalDatabase
import iad1tya.echo.music.LocalDownloadUtil
import iad1tya.echo.music.LocalPlayerAwareWindowInsets
import iad1tya.echo.music.LocalPlayerConnection
import iad1tya.echo.music.R
import iad1tya.echo.music.constants.InnerTubeCookieKey
import iad1tya.echo.music.constants.SongSortDescendingKey
import iad1tya.echo.music.constants.SongSortType
import iad1tya.echo.music.constants.SongSortTypeKey
import iad1tya.echo.music.constants.YtmSyncKey
import iad1tya.echo.music.db.entities.Song
import iad1tya.echo.music.extensions.toMediaItem
import iad1tya.echo.music.extensions.tryOrNull
import iad1tya.echo.music.playback.ExoDownloadService
import iad1tya.echo.music.playback.queues.ListQueue
import iad1tya.echo.music.ui.component.DefaultDialog
import iad1tya.echo.music.ui.component.DraggableScrollbar
import iad1tya.echo.music.ui.component.EnhancedShuffleChip
import iad1tya.echo.music.ui.component.LocalMenuState
import iad1tya.echo.music.ui.component.rememberPlayedShuffleSet
import iad1tya.echo.music.ui.component.rememberShuffleMemoryPrompt
import iad1tya.echo.music.ui.menu.AutoPlaylistMenu
import iad1tya.echo.music.ui.menu.SelectionSongMenu
import iad1tya.echo.music.ui.menu.SongMenu
import iad1tya.echo.music.ui.screens.playlist.PlaylistType
import iad1tya.echo.music.ui.utils.formatFileSize
import iad1tya.echo.music.ui.utils.rememberIsTvOrCar
import iad1tya.echo.music.ui.utils.rememberIsWideLayout
import iad1tya.echo.music.ui.utils.tvFocusable
import iad1tya.echo.music.utils.makeTimeString
import iad1tya.echo.music.utils.rememberEnumPreference
import iad1tya.echo.music.utils.rememberPreference
import iad1tya.echo.music.viewmodels.AutoPlaylistViewModel
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

/**
 * # Descargado / auto-listas — "Interfaz nueva"
 *
 * The redesigned `AutoPlaylistScreen` (route `auto_playlist/{playlist}`). That ONE classic screen is
 * four screens to the user — **Descargado**, Canciones que me gustan, Subidas, Exportado — and the
 * downloads screen the order asks for is its `downloaded` mode. Rebuilding only that mode would have
 * meant a host that branches on a route argument; rebuilding the screen keeps one host, one call site
 * and the other three modes are covered for free (they were going to need it anyway).
 *
 * ## Presentation only
 * The list, the sort keys, the search filter, the queue, the "Aleatorio mejorado" memory and every
 * menu are the classic ones:
 *  · [AutoPlaylistViewModel] — the same `hiltViewModel()`, so the same DataStore-driven flow decides
 *    which songs are in the list, in which order, with the same explicit/video filters.
 *  · `contextId = "AP:<playlist>"` — the SAME no-repeat bucket, so toggling "Interfaz nueva" does not
 *    hand the user a second shuffle memory and make songs repeat.
 *  · [SongMenu] / [SelectionSongMenu] / [AutoPlaylistMenu] — the classic sheets, which is where
 *    per-song *Descargar / Descargando (cancela) / Eliminar descarga* and every bulk action live.
 *
 * ## What the classic screen could NOT show, and this one does
 * `database.downloadedSongs(...)` only lists songs whose download has **completed**, so on the classic
 * "Descargado" screen a download in flight is invisible: no progress, nothing to cancel, no sign a
 * failure happened. The three additions below all read REAL state and are gated to
 * [PlaylistType.DOWNLOAD]:
 *  · **Descargas en curso** — the live `DownloadUtil.downloads` map, one row per queued / downloading /
 *    failed / paused item, with the real percentage (see [rememberAuraDownloadProgress]), a cancel
 *    that is `DownloadService.sendRemoveDownload` and a retry that re-enqueues the SAME
 *    [DownloadRequest] the rest of the app builds.
 *  · **Almacenamiento** — `downloadCache.cacheSpace`, the exact number Ajustes ▸ Almacenamiento
 *    prints, formatted with the same [formatFileSize]. Tapping it opens that screen, where the
 *    "vaciar descargas" action already lives.
 *  · The per-row download tick, which the classic screen omits here (everything listed is downloaded)
 *    and which stays omitted for the same reason.
 *
 * @param scrollBehavior accepted for signature parity with the classic screen; this shape draws its own
 *   header instead of a `TopAppBar`, so there is no collapsing bar to drive with it.
 */
@Suppress("UNUSED_PARAMETER")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuraAutoPlaylistScreen(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
    viewModel: AutoPlaylistViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val menuState = LocalMenuState.current
    val haptic = LocalHapticFeedback.current
    val focusManager = LocalFocusManager.current
    val playerConnection = LocalPlayerConnection.current ?: return
    val downloadUtil = LocalDownloadUtil.current
    val isTvOrCar = rememberIsTvOrCar()

    val isPlaying by playerConnection.isEffectivelyPlaying.collectAsState()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsState()

    // Same title mapping as the classic screen (AutoPlaylistScreen.kt:139-145).
    val playlist = when (viewModel.playlist) {
        "liked" -> stringResource(R.string.liked)
        "uploaded" -> stringResource(R.string.uploaded_playlist)
        "exported" -> stringResource(R.string.action_exported)
        else -> stringResource(R.string.offline)
    }
    val playlistId = viewModel.playlist
    val playlistType = when (playlistId) {
        "liked" -> PlaylistType.LIKE
        "downloaded" -> PlaylistType.DOWNLOAD
        "uploaded" -> PlaylistType.UPLOADED
        "exported" -> PlaylistType.EXPORTED
        else -> PlaylistType.OTHER
    }
    val contextId = "AP:$playlistId"

    val songs by viewModel.likedSongs.collectAsState(null)

    var isSearching by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(isSearching) {
        if (isSearching) focusRequester.requestFocus()
    }

    val (ytmSync) = rememberPreference(YtmSyncKey, true)
    val (innerTubeCookie) = rememberPreference(InnerTubeCookieKey, "")
    val isLoggedIn = remember(innerTubeCookie) { "SAPISID" in parseCookieString(innerTubeCookie) }

    val likeLength = remember(songs) { songs?.sumOf { it.song.duration } ?: 0 }
    val shufflePlayedSet = rememberPlayedShuffleSet(contextId)

    var inSelectMode by rememberSaveable { mutableStateOf(false) }
    val selection = rememberSaveable(
        saver = listSaver<MutableList<String>, String>(
            save = { it.toList() },
            restore = { it.toMutableStateList() },
        ),
    ) { mutableStateListOf<String>() }
    val onExitSelectionMode = {
        inSelectMode = false
        selection.clear()
    }

    if (isSearching) {
        BackHandler {
            isSearching = false
            query = ""
        }
    } else if (inSelectMode) {
        BackHandler(onBack = onExitSelectionMode)
    }

    val (sortType, onSortTypeChange) = rememberEnumPreference(SongSortTypeKey, SongSortType.CREATE_DATE)
    val (sortDescending, onSortDescendingChange) = rememberPreference(SongSortDescendingKey, true)

    // Aggregate download state of the whole collection — the value AutoPlaylistMenu turns into
    // "Descargar" / "Descargando (cancela)" / "Eliminar descarga". Same derivation as the classic
    // screen, driven by the same flow.
    var downloadState by remember { mutableIntStateOf(Download.STATE_STOPPED) }
    LaunchedEffect(songs) {
        if (songs?.isEmpty() != false) return@LaunchedEffect
        downloadUtil.downloads.collect { downloads ->
            downloadState = when {
                songs?.all { downloads[it.song.id]?.state == Download.STATE_COMPLETED } == true ->
                    Download.STATE_COMPLETED

                songs?.all {
                    downloads[it.song.id]?.state == Download.STATE_QUEUED ||
                        downloads[it.song.id]?.state == Download.STATE_DOWNLOADING ||
                        downloads[it.song.id]?.state == Download.STATE_COMPLETED
                } == true -> Download.STATE_DOWNLOADING

                else -> Download.STATE_STOPPED
            }
        }
    }

    var showRemoveDownloadDialog by remember { mutableStateOf(false) }
    if (showRemoveDownloadDialog) {
        DefaultDialog(
            onDismiss = { showRemoveDownloadDialog = false },
            content = {
                Text(
                    text = stringResource(R.string.remove_download_playlist_confirm, playlist),
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
                        songs?.forEach { song ->
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

    val filteredSongs = remember(songs, query) {
        if (query.isEmpty()) songs ?: emptyList()
        else songs?.filter { song ->
            song.song.title.contains(query, true) ||
                song.artists.any { it.name.contains(query, true) }
        } ?: emptyList()
    }

    LaunchedEffect(filteredSongs) {
        selection.toList().forEach { songId ->
            if (filteredSongs.none { it.id == songId }) selection.remove(songId)
        }
    }

    val listState = rememberLazyListState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val pullRefreshState = rememberPullToRefreshState()
    val canRefresh = playlistType == PlaylistType.LIKE || playlistType == PlaylistType.UPLOADED

    LaunchedEffect(Unit) {
        if (ytmSync) {
            withContext(Dispatchers.IO) {
                if (playlistType == PlaylistType.LIKE) viewModel.syncLikedSongs()
                if (playlistType == PlaylistType.UPLOADED) viewModel.syncUploadedSongs()
            }
        }
    }

    val isDownloadsScreen = playlistType == PlaylistType.DOWNLOAD
    val activeDownloads = rememberAuraActiveDownloads(enabled = isDownloadsScreen)
    val downloadProgress by rememberAuraDownloadProgress(enabled = isDownloadsScreen)

    val bloom = rememberAuraBloom(mediaMetadata?.id)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .auraScreenBackground(bloom, intensity = 0.40f)
            .then(
                if (canRefresh) {
                    Modifier.pullToRefresh(
                        state = pullRefreshState,
                        isRefreshing = isRefreshing,
                        onRefresh = viewModel::refresh,
                    )
                } else Modifier,
            ),
    ) {
        LazyColumn(
            state = listState,
            contentPadding = LocalPlayerAwareWindowInsets.current.asPaddingValues(),
            modifier = Modifier.fillMaxSize(),
        ) {
            if (songs == null) {
                // Still loading: nothing is drawn, exactly as the classic screen does.
                return@LazyColumn
            }

            if (songs!!.isEmpty() && activeDownloads.isEmpty()) {
                item(key = "aura_ap_empty") {
                    AuraEmpty(
                        text = stringResource(R.string.playlist_is_empty),
                        modifier = Modifier.animateItem(),
                    )
                }
            }

            if (songs!!.isNotEmpty() && !isSearching) {
                item(key = "aura_ap_header") {
                    AuraAutoPlaylistHeader(
                        name = playlist,
                        songs = songs!!,
                        likeLength = likeLength,
                        contextId = contextId,
                        downloadState = downloadState,
                        onShowRemoveDownloadDialog = { showRemoveDownloadDialog = true },
                        // Only Liked / Uploaded can sync, and only when signed in — the classic gate.
                        onSync = if (isLoggedIn &&
                            (playlistType == PlaylistType.LIKE || playlistType == PlaylistType.UPLOADED)
                        ) {
                            {
                                if (playlistType == PlaylistType.LIKE) viewModel.syncLikedSongs()
                                else viewModel.syncUploadedSongs()
                                Toast.makeText(
                                    context,
                                    "Sincronizando con YouTube Music…",
                                    Toast.LENGTH_SHORT,
                                ).show()
                            }
                        } else null,
                        modifier = Modifier.animateItem(),
                    )
                }
            }

            // ── Almacenamiento (solo "Descargado") ────────────────────────────────────────────────
            if (isDownloadsScreen && !isSearching) {
                item(key = "aura_ap_storage") {
                    AuraDownloadStorageCard(
                        onOpenStorageSettings = { navController.navigate("settings/storage") },
                        modifier = Modifier.animateItem(),
                    )
                }
            }

            // ── Descargas en curso (solo "Descargado") ────────────────────────────────────────────
            if (isDownloadsScreen && activeDownloads.isNotEmpty()) {
                item(key = "aura_ap_active_label") {
                    AuraSectionLabel(
                        text = stringResource(R.string.aura_downloads_in_progress).uppercase(Locale.ROOT),
                        modifier = Modifier
                            .animateItem()
                            .padding(
                                start = AuraSpacing.Gutter,
                                end = AuraSpacing.Gutter,
                                top = AuraSpacing.SectionTop,
                                bottom = AuraSpacing.SectionGap,
                            ),
                    )
                }
                items(
                    items = activeDownloads,
                    key = { "aura_dl_" + it.request.id },
                ) { download ->
                    AuraActiveDownloadRow(
                        download = download,
                        percent = downloadProgress[download.request.id],
                        onCancel = {
                            DownloadService.sendRemoveDownload(
                                context,
                                ExoDownloadService::class.java,
                                download.request.id,
                                false,
                            )
                        },
                        onRetry = {
                            // Re-enqueue the SAME request the rest of the app builds, after dropping the
                            // memoised stream URL so a failed download re-resolves instead of pulling the
                            // same stale stream again (DownloadUtil.invalidateSongUrl).
                            downloadUtil.invalidateSongUrl(download.request.id)
                            DownloadService.sendAddDownload(
                                context,
                                ExoDownloadService::class.java,
                                download.request,
                                false,
                            )
                        },
                        modifier = Modifier
                            .animateItem()
                            .padding(horizontal = AuraSpacing.Gutter),
                    )
                }
            }

            if (songs!!.isNotEmpty()) {
                if (isSearching) {
                    item(key = "aura_ap_search") {
                        AuraInlineSearchField(
                            value = query,
                            onValueChange = { query = it },
                            placeholder = stringResource(R.string.search),
                            focusRequester = focusRequester,
                            onSearch = { focusManager.clearFocus() },
                            modifier = Modifier.animateItem(),
                        )
                    }
                }

                item(key = "aura_ap_sort") {
                    AuraInlineSortControl(
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
                            AuraTechnicalText(
                                text = pluralStringResource(
                                    R.plurals.n_song,
                                    filteredSongs.size,
                                    filteredSongs.size,
                                ).uppercase(Locale.ROOT),
                                color = AuraPalette.OnGroundGhost,
                                modifier = Modifier.padding(end = 12.dp),
                            )
                        },
                    )
                }
            }

            itemsIndexed(
                items = filteredSongs,
                key = { _, song -> song.id },
            ) { _, song ->
                val selected = song.id in selection
                val onCheckedChange: (Boolean) -> Unit = { checked ->
                    if (checked) selection.add(song.id) else selection.remove(song.id)
                }
                val dimmed = song.id in shufflePlayedSet && song.id != mediaMetadata?.id

                AuraRow(
                    title = song.song.title,
                    subtitle = song.artists.joinToString { it.name },
                    highlighted = song.id == mediaMetadata?.id,
                    dimmed = dimmed,
                    contentDescription = song.song.title,
                    onClick = {
                        when {
                            inSelectMode -> onCheckedChange(!selected)
                            song.song.id == mediaMetadata?.id -> playerConnection.togglePlayPause()
                            else -> playerConnection.playQueue(
                                ListQueue(
                                    title = playlist,
                                    items = songs!!.map { it.toMediaItem() },
                                    startIndex = songs!!.indexOfFirst { it.id == song.id },
                                    contextId = contextId,
                                ),
                            )
                        }
                    },
                    onLongClick = {
                        if (!inSelectMode) {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            inSelectMode = true
                            onCheckedChange(true)
                        }
                    },
                    artwork = {
                        AuraCover(
                            thumbnailUrl = song.song.thumbnailUrl,
                            size = 50.dp,
                            seed = song.id,
                        ) {
                            if (song.id == mediaMetadata?.id && isPlaying) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(AuraPalette.Ground.copy(alpha = 0.55f)),
                                    contentAlignment = Alignment.Center,
                                ) { AuraPlayingBars() }
                            }
                        }
                    },
                    trailing = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(7.dp),
                        ) {
                            if (dimmed) {
                                AuraIconGlyph(
                                    icon = AuraIcons.Check,
                                    contentDescription = "Ya reproducida en aleatorio",
                                    size = 16.dp,
                                    tint = AuraPalette.Teal,
                                )
                            }
                            if (song.song.explicit) {
                                AuraTechnicalText(
                                    text = "E",
                                    color = AuraPalette.OnGroundDisabled,
                                    style = AuraType.QualityBadge,
                                )
                            }
                            if (song.song.liked) {
                                AuraIconGlyph(
                                    icon = AuraIcons.HeartFilled,
                                    contentDescription = null,
                                    size = 15.dp,
                                    tint = AuraPalette.Teal,
                                )
                            }
                            // On "Descargado" every row IS a download, so the tick would be noise —
                            // the same rule the new Biblioteca applies to its Descargado sub-filter.
                            if (!isDownloadsScreen) {
                                AuraDownloadTick(songId = song.id)
                            }
                            AuraQualityBadge(format = song.format)
                            if (inSelectMode) {
                                Checkbox(
                                    checked = selected,
                                    onCheckedChange = onCheckedChange,
                                    colors = CheckboxDefaults.colors(
                                        checkedColor = AuraPalette.Teal,
                                        uncheckedColor = AuraPalette.OnGroundDisabled,
                                        checkmarkColor = AuraPalette.OnAccent,
                                    ),
                                )
                            } else {
                                AuraIconButton(
                                    icon = AuraIcons.More,
                                    contentDescription = song.song.title,
                                    onClick = {
                                        menuState.show {
                                            SongMenu(
                                                originalSong = song,
                                                navController = navController,
                                                onDismiss = menuState::dismiss,
                                            )
                                        }
                                    },
                                    size = 18.dp,
                                    tint = AuraPalette.OnGroundDisabled,
                                )
                            }
                        }
                    },
                    modifier = Modifier
                        .animateItem()
                        .padding(horizontal = AuraSpacing.Gutter)
                        .tvFocusable(isTvOrCar, AuraShapes.Highlight, scaleFocused = 1f),
                )
            }

            item(key = "aura_ap_tail") { Spacer(Modifier.height(50.dp)) }
        }

        DraggableScrollbar(
            modifier = Modifier
                .padding(
                    LocalPlayerAwareWindowInsets.current.union(WindowInsets.ime).asPaddingValues(),
                )
                .align(Alignment.CenterEnd),
            scrollState = listState,
            headerItems = 2,
        )

        if (canRefresh) {
            Indicator(
                isRefreshing = isRefreshing,
                state = pullRefreshState,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(LocalPlayerAwareWindowInsets.current.asPaddingValues()),
            )
        }

        // ── Barra superior ────────────────────────────────────────────────────────────────────────
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Top))
                .background(AuraPalette.Ground.copy(alpha = 0.82f))
                .padding(horizontal = 6.dp, vertical = 4.dp),
        ) {
            AuraIconButton(
                icon = if (inSelectMode) AuraIcons.Plus else AuraIcons.ChevronRight,
                // Spanish content descriptions, written here as the rest of the new UI does — the
                // classic bar leaves both of these icons with `contentDescription = null`.
                contentDescription = if (inSelectMode) "Cancelar la selección" else "Volver",
                onClick = {
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
                size = 22.dp,
                // A "+" turned 45° is the render's close glyph; the back arrow is a chevron mirrored.
                modifier = Modifier.graphicsLayer {
                    rotationZ = if (inSelectMode) 45f else 180f
                },
            )
            Text(
                text = if (inSelectMode) {
                    pluralStringResource(R.plurals.n_song, selection.size, selection.size)
                } else {
                    playlist
                },
                style = AuraType.SheetTitle,
                color = AuraPalette.OnGround,
                maxLines = 1,
                overflow = AuraDefaultOverflow,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 6.dp),
            )
            if (inSelectMode) {
                Checkbox(
                    checked = selection.size == filteredSongs.size && selection.isNotEmpty(),
                    onCheckedChange = {
                        if (selection.size == filteredSongs.size) {
                            selection.clear()
                        } else {
                            selection.clear()
                            selection.addAll(filteredSongs.map { it.id })
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
                    contentDescription = "Acciones de la selección",
                    enabled = selection.isNotEmpty(),
                    onClick = {
                        menuState.show {
                            SelectionSongMenu(
                                songSelection = filteredSongs.filter { it.id in selection },
                                onDismiss = menuState::dismiss,
                                clearAction = onExitSelectionMode,
                            )
                        }
                    },
                    size = 20.dp,
                )
            } else if (!isSearching) {
                AuraIconButton(
                    icon = AuraIcons.Search,
                    contentDescription = stringResource(R.string.search),
                    onClick = { isSearching = true },
                    size = 20.dp,
                )
            }
        }
    }
}

// ── Cabecera de la colección ──────────────────────────────────────────────────────────────────────

/**
 * Cover, title, Aleatorio / Reproducir / ⋯, the count line, the "Aleatorio mejorado" pill and the
 * "Acerca de" block — the classic header's contents, in the new language.
 *
 * The shuffle button is the SAME [rememberShuffleMemoryPrompt] + unplayed-first ordering the classic
 * header uses; re-deriving it would have given this screen a shuffle that behaves differently from the
 * identical button one tab away.
 */
@Composable
private fun AuraAutoPlaylistHeader(
    name: String,
    songs: List<Song>,
    likeLength: Int,
    contextId: String,
    downloadState: Int,
    onShowRemoveDownloadDialog: () -> Unit,
    onSync: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val playerConnection = LocalPlayerConnection.current ?: return
    val context = LocalContext.current
    val menuState = LocalMenuState.current
    val isTvOrCar = rememberIsTvOrCar()
    val isWideLayout = rememberIsWideLayout()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(56.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 48.dp),
            contentAlignment = Alignment.Center,
        ) {
            AuraCover(
                thumbnailUrl = songs.firstOrNull()?.song?.thumbnailUrl,
                // Wide screens cap the cover, exactly as the classic header does.
                size = if (isWideLayout) 320.dp else 260.dp,
                seed = contextId,
                shape = AuraShapes.PlayerArtwork,
                decodeTo = 512,
            )
        }

        Spacer(Modifier.height(26.dp))

        Text(
            text = name,
            style = AuraType.ScreenTitle,
            color = AuraPalette.OnGround,
            maxLines = 2,
            overflow = AuraDefaultOverflow,
            modifier = Modifier.padding(horizontal = 32.dp),
        )

        Spacer(Modifier.height(6.dp))

        Text(
            text = buildString {
                append(pluralStringResource(R.plurals.n_song, songs.size, songs.size))
                if (likeLength > 0) {
                    append(" • ")
                    append(makeTimeString(likeLength * 1000L))
                }
            },
            style = AuraType.RowSubtitle,
            color = AuraPalette.OnGroundMuted,
            maxLines = 1,
            overflow = AuraDefaultOverflow,
        )

        // "Aleatorio mejorado · X/Y reproducidas" — feature-gated inside the shared chip.
        run {
            val playedSet = rememberPlayedShuffleSet(contextId)
            val playedCount = remember(playedSet, songs) { songs.count { it.song.id in playedSet } }
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
                playedCount = songs.count { it.id in playedForStart },
                totalCount = songs.size,
            ) { resetMemory ->
                val ordered = if (resetMemory) {
                    songs.shuffled()
                } else {
                    val (unheard, heard) = songs.partition { it.id !in playedForStart }
                    unheard.shuffled() + heard.shuffled()
                }
                playerConnection.playQueue(
                    ListQueue(
                        title = name,
                        items = ordered.map { it.toMediaItem() },
                        contextId = contextId,
                        startShuffled = true,
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
                            title = name,
                            items = songs.map { it.toMediaItem() },
                            contextId = contextId,
                        ),
                    )
                },
                accent = true,
                modifier = Modifier.weight(1f).tvFocusable(isTvOrCar, scaleFocused = 1f),
            )
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(AuraPalette.SurfaceFill)
                    .border(1.dp, AuraPalette.SurfaceLine, CircleShape)
                    .tvFocusable(isTvOrCar, scaleFocused = 1f)
                    .auraClickableInternal(
                        onClick = {
                            menuState.show {
                                AutoPlaylistMenu(
                                    downloadState = downloadState,
                                    onSync = onSync,
                                    onQueue = {
                                        playerConnection.addToQueue(songs.map { it.toMediaItem() })
                                    },
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
                                                val request = DownloadRequest
                                                    .Builder(song.song.id, song.song.id.toUri())
                                                    .setCustomCacheKey(song.song.id)
                                                    .setData(song.song.title.toByteArray())
                                                    .build()
                                                DownloadService.sendAddDownload(
                                                    context,
                                                    ExoDownloadService::class.java,
                                                    request,
                                                    false,
                                                )
                                            }
                                        }
                                    },
                                    onDismiss = menuState::dismiss,
                                )
                            }
                        },
                        contentDescription = "Más opciones de la colección",
                    ),
                contentAlignment = Alignment.Center,
            ) {
                AuraIconGlyph(
                    icon = AuraIcons.More,
                    contentDescription = null,
                    size = 20.dp,
                    tint = AuraPalette.OnGround,
                )
            }
        }

        Spacer(Modifier.height(18.dp))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AuraSpacing.Gutter),
        ) {
            AuraSectionLabel(text = stringResource(R.string.about_album).uppercase(Locale.ROOT))
            Spacer(Modifier.height(6.dp))
            AuraExpandableText(
                text = stringResource(R.string.aura_auto_playlist_about, name),
                collapsedMaxLines = 3,
            )
        }
    }
}

/**
 * The classic header's "Acerca de" text with its Mostrar más / Mostrar menos toggle, in the new UI's
 * own colours.
 *
 * `ui/component/ExpandableText` is not reused here for one reason: it paints itself with
 * `MaterialTheme.colorScheme.onSurfaceVariant`, and this screen draws its own near-black ground
 * WITHOUT installing a Material theme — so on a user running the beta in the light theme that classic
 * text would be dark ink on a dark ground. Same `R.string.show_more` / `R.string.show_less` labels,
 * same collapse behaviour, legible in both themes.
 */
@Composable
private fun AuraExpandableText(
    text: String,
    modifier: Modifier = Modifier,
    collapsedMaxLines: Int = 3,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    var hasOverflow by rememberSaveable { mutableStateOf(false) }

    Column(modifier) {
        Text(
            text = text,
            style = AuraType.RowSubtitle,
            color = AuraPalette.OnGroundMuted,
            maxLines = if (expanded) Int.MAX_VALUE else collapsedMaxLines,
            overflow = AuraDefaultOverflow,
            onTextLayout = { result -> if (!expanded) hasOverflow = result.hasVisualOverflow },
        )
        if (hasOverflow || expanded) {
            Text(
                text = stringResource(if (expanded) R.string.show_less else R.string.show_more),
                style = AuraType.Chip,
                color = AuraPalette.Teal,
                maxLines = 1,
                modifier = Modifier
                    .padding(top = 4.dp)
                    .clip(AuraShapes.Pill)
                    .auraClickableInternal(
                        onClick = { expanded = !expanded },
                        contentDescription = null,
                    )
                    .padding(vertical = 8.dp, horizontal = 4.dp),
            )
        }
    }
}

/** Aleatorio / Reproducir. [accent] draws the gradient (the one full-colour element per screen). */
@Composable
private fun AuraHeaderButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    accent: Boolean,
    modifier: Modifier = Modifier,
) {
    Row(
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .height(48.dp)
            .clip(AuraShapes.Pill)
            .then(
                if (accent) {
                    Modifier.background(AuraPalette.PlayButtonGradient)
                } else {
                    Modifier
                        .background(AuraPalette.SurfaceFill)
                        .border(1.dp, AuraPalette.SurfaceLine, AuraShapes.Pill)
                },
            )
            .auraClickableInternal(onClick = onClick, contentDescription = label),
    ) {
        AuraIconGlyph(
            icon = icon,
            contentDescription = null,
            size = 19.dp,
            tint = if (accent) AuraPalette.OnAccent else AuraPalette.OnGround,
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = label,
            style = AuraType.Chip,
            color = if (accent) AuraPalette.OnAccent else AuraPalette.OnGround,
            maxLines = 1,
            overflow = AuraDefaultOverflow,
        )
    }
}

// ── Descargas ─────────────────────────────────────────────────────────────────────────────────────

/**
 * "Canciones descargadas · <tamaño real>". The number is `downloadCache.cacheSpace`, i.e. the exact
 * value Ajustes ▸ Almacenamiento prints, through the same [formatFileSize]; tapping opens that screen,
 * which already owns "vaciar descargas". Read off the main thread and only every 2 s — the cache walk
 * is disk work and this card is decoration, not a meter.
 */
@Composable
private fun AuraDownloadStorageCard(
    onOpenStorageSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val downloadUtil = LocalDownloadUtil.current
    val downloads by downloadUtil.downloads.collectAsState()
    var sizeBytes by remember { mutableLongStateOf(-1L) }
    // Only a transfer in flight makes the number move without the map changing; the rest of the time
    // one measurement per map change is exact. `cacheSpace` walks the cache index, so this is a slow
    // 5 s tick and never an idle-screen loop.
    val measuringWhileActive = downloads.values.any { it.state == Download.STATE_DOWNLOADING }

    LaunchedEffect(downloads.size, measuringWhileActive) {
        while (isActive) {
            sizeBytes = withContext(Dispatchers.IO) {
                tryOrNull { downloadUtil.downloadCache.cacheSpace } ?: 0L
            }
            if (!measuringWhileActive) return@LaunchedEffect
            delay(5000)
        }
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = AuraSpacing.Gutter, vertical = 6.dp)
            .clip(AuraShapes.Card)
            .background(AuraPalette.SurfaceFill)
            .border(1.dp, AuraPalette.SurfaceLine, AuraShapes.Card)
            .auraClickableInternal(
                onClick = onOpenStorageSettings,
                contentDescription = stringResource(R.string.storage),
            )
            .padding(horizontal = 14.dp, vertical = 13.dp),
    ) {
        AuraIconGlyph(
            icon = AuraIcons.Download,
            contentDescription = null,
            size = 19.dp,
            tint = AuraPalette.Teal,
        )
        Column(Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.downloaded_songs),
                style = AuraType.RowTitle,
                color = AuraPalette.OnGround,
                maxLines = 1,
                overflow = AuraDefaultOverflow,
            )
            // Until the first measurement lands nothing is printed — a "0 B" that later jumps would be
            // a number the screen never actually knew.
            if (sizeBytes >= 0L) {
                AuraTechnicalText(text = formatFileSize(sizeBytes))
            }
        }
        AuraIconGlyph(
            icon = AuraIcons.ChevronRight,
            contentDescription = null,
            size = 16.dp,
            tint = AuraPalette.OnGroundDisabled,
        )
    }
}

/**
 * One in-flight download: title, state, the real percentage, and the two actions the classic screen
 * had nowhere to put — cancel (`sendRemoveDownload`, the same call the ⋯ menus make) and, for a failed
 * one, retry.
 *
 * The title comes from the DB row when the song is known and otherwise from `request.data`, which every
 * enqueue site in the app fills with the song title (`setData(title.toByteArray())`) — so a download
 * queued before the song was ever stored still names itself instead of showing a video id.
 */
@Composable
private fun AuraActiveDownloadRow(
    download: Download,
    percent: Float?,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val database = LocalDatabase.current
    val song by remember(download.request.id) { database.song(download.request.id) }
        .collectAsState(initial = null)

    val fallbackTitle = remember(download.request) {
        runCatching { download.request.data.toString(Charsets.UTF_8) }
            .getOrNull()
            ?.takeIf { it.isNotBlank() }
            ?: download.request.id
    }
    val title = song?.song?.title ?: fallbackTitle
    val failed = download.state == Download.STATE_FAILED
    val subtitle = when (download.state) {
        Download.STATE_FAILED -> stringResource(R.string.aura_download_failed)
        Download.STATE_QUEUED -> stringResource(R.string.aura_download_queued)
        Download.STATE_STOPPED -> stringResource(R.string.aura_download_paused)
        else -> stringResource(R.string.downloading)
    }

    Column(modifier = modifier.fillMaxWidth()) {
        AuraRow(
            title = title,
            subtitle = subtitle,
            contentDescription = title,
            artwork = {
                AuraCover(
                    thumbnailUrl = song?.song?.thumbnailUrl,
                    size = 50.dp,
                    seed = download.request.id,
                )
            },
            trailing = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    // `percentDownloaded` is C.PERCENTAGE_UNSET (-1) until the transfer knows the
                    // content length, so an unknown percentage prints nothing instead of "0 %".
                    if (percent != null && percent >= 0f) {
                        AuraTechnicalText(
                            text = "${percent.toInt()} %",
                            color = if (failed) AuraPalette.OnGroundGhost else AuraPalette.Teal,
                        )
                    }
                    if (failed) {
                        AuraIconButton(
                            icon = AuraIcons.Download,
                            contentDescription = stringResource(R.string.retry),
                            onClick = onRetry,
                            size = 18.dp,
                            tint = AuraPalette.Teal,
                        )
                    }
                    AuraIconButton(
                        // A "+" turned 45° is the render's close glyph.
                        icon = AuraIcons.Plus,
                        contentDescription = stringResource(R.string.remove_download),
                        onClick = onCancel,
                        size = 18.dp,
                        tint = AuraPalette.OnGroundDisabled,
                        modifier = Modifier.graphicsLayer { rotationZ = 45f },
                    )
                }
            },
        )
        // Determinate while the percentage is known, indeterminate while it is not — never a bar that
        // pretends to know a progress nobody reported.
        if (percent != null && percent >= 0f) {
            LinearProgressIndicator(
                progress = { (percent / 100f).coerceIn(0f, 1f) },
                color = if (failed) AuraPalette.OnGroundDisabled else AuraPalette.Teal,
                trackColor = AuraPalette.TrackEmpty,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .clip(AuraShapes.Pill),
            )
        } else if (!failed) {
            LinearProgressIndicator(
                color = AuraPalette.Teal,
                trackColor = AuraPalette.TrackEmpty,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .clip(AuraShapes.Pill),
            )
        }
    }
}

/**
 * The downloads that are NOT finished: queued, downloading, failed or paused. Read from the same
 * `DownloadUtil.downloads` flow the badges and the menus read, so the section can never disagree with
 * the rest of the app.
 */
@Composable
private fun rememberAuraActiveDownloads(enabled: Boolean): List<Download> {
    // Collected UNCONDITIONALLY — `enabled` only decides what comes out, never whether a composable
    // runs, so the call order can never depend on which auto-playlist is open.
    val downloads by LocalDownloadUtil.current.downloads.collectAsState()
    return remember(downloads, enabled) {
        if (!enabled) return@remember emptyList()
        downloads.values
            .filter {
                it.state == Download.STATE_DOWNLOADING ||
                    it.state == Download.STATE_QUEUED ||
                    it.state == Download.STATE_FAILED ||
                    it.state == Download.STATE_STOPPED
            }
            // Active first, then queued, then the ones that need attention — the order the user acts in.
            .sortedBy { download ->
                when (download.state) {
                    Download.STATE_DOWNLOADING -> 0
                    Download.STATE_QUEUED -> 1
                    Download.STATE_STOPPED -> 2
                    else -> 3
                }
            }
    }
}

/**
 * Live per-download percentage, `id -> percentDownloaded`.
 *
 * `DownloadManager.Listener.onDownloadChanged` fires on STATE changes, not on progress, so the map in
 * `DownloadUtil.downloads` carries no moving percentage — a bar driven off it would sit still and lie.
 * media3's own live source is `DownloadManager.currentDownloads`, which is polled here.
 *
 * Heat/battery: the loop exists ONLY while something is actually downloading (`hasActive`), ticks once
 * a second, and is cancelled with the composition. Nothing polls on an idle screen.
 */
@Composable
private fun rememberAuraDownloadProgress(enabled: Boolean): State<Map<String, Float>> {
    val downloadUtil = LocalDownloadUtil.current
    val downloads by downloadUtil.downloads.collectAsState()
    val hasActive = enabled && downloads.values.any {
        it.state == Download.STATE_DOWNLOADING || it.state == Download.STATE_QUEUED
    }
    val progress = remember { mutableStateOf<Map<String, Float>>(emptyMap()) }

    LaunchedEffect(hasActive) {
        if (!hasActive) {
            progress.value = emptyMap()
            return@LaunchedEffect
        }
        while (isActive) {
            progress.value = downloadUtil.downloadManager.currentDownloads
                .associate { it.request.id to it.percentDownloaded }
            delay(1000)
        }
    }
    return progress
}

// ── Furniture ─────────────────────────────────────────────────────────────────────────────────────

/** The in-list search field. Same filter the classic screen applies (title OR any artist name). */
@Composable
private fun AuraInlineSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    focusRequester: FocusRequester,
    onSearch: () -> Unit,
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
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester),
            )
        }
        if (value.isNotEmpty()) {
            AuraIconButton(
                icon = AuraIcons.Plus,
                contentDescription = "Borrar la búsqueda",
                onClick = {
                    onValueChange("")
                    onSearch()
                },
                size = 16.dp,
                tint = AuraPalette.OnGroundFaint,
                modifier = Modifier.graphicsLayer { rotationZ = 45f },
            )
        }
    }
}

/**
 * The sort control: the criterion opens a menu, the arrow flips ascending/descending — the same two
 * controls `SortHeader` gives the classic screen, writing the same two preference keys.
 */
@Composable
private fun <T : Enum<T>> AuraInlineSortControl(
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
