package iad1tya.echo.music.ui.newui

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarScrollBehavior
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
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadRequest
import androidx.media3.exoplayer.offline.DownloadService
import androidx.navigation.NavController
import com.music.innertube.models.AlbumItem
import com.music.innertube.models.ArtistItem
import com.music.innertube.models.PlaylistItem
import com.music.innertube.models.SongItem
import com.music.innertube.models.WatchEndpoint
import iad1tya.echo.music.LocalDatabase
import iad1tya.echo.music.LocalDownloadUtil
import iad1tya.echo.music.LocalPlayerAwareWindowInsets
import iad1tya.echo.music.LocalPlayerConnection
import iad1tya.echo.music.R
import iad1tya.echo.music.constants.HideExplicitKey
import iad1tya.echo.music.db.entities.Playlist
import iad1tya.echo.music.db.entities.PlaylistEntity
import iad1tya.echo.music.db.entities.PlaylistSongMap
import iad1tya.echo.music.models.toMediaMetadata
import iad1tya.echo.music.playback.ExoDownloadService
import iad1tya.echo.music.playback.ShuffleContexts
import iad1tya.echo.music.playback.queues.YouTubePlaylistQueue
import iad1tya.echo.music.playback.queues.YouTubeQueue
import iad1tya.echo.music.ui.component.EnhancedShuffleChip
import iad1tya.echo.music.ui.component.LocalMenuState
import iad1tya.echo.music.ui.component.rememberHeardSongIds
import iad1tya.echo.music.ui.component.rememberPlayedShuffleSet
import iad1tya.echo.music.ui.component.rememberShuffleMemoryPrompt
import iad1tya.echo.music.ui.menu.YouTubeAlbumMenu
import iad1tya.echo.music.ui.menu.YouTubeArtistMenu
import iad1tya.echo.music.ui.menu.YouTubePlaylistMenu
import iad1tya.echo.music.ui.menu.YouTubeSelectionSongMenu
import iad1tya.echo.music.ui.menu.YouTubeSongMenu
import iad1tya.echo.music.ui.utils.rememberIsTvOrCar
import iad1tya.echo.music.ui.utils.rememberIsWideLayout
import iad1tya.echo.music.ui.utils.tvFocusable
import iad1tya.echo.music.utils.rememberPreference
import iad1tya.echo.music.viewmodels.OnlinePlaylistViewModel
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * # Lista de YouTube Music — "Interfaz nueva"
 *
 * The redesigned `OnlinePlaylistScreen` (route `online_playlist/{playlistId}`).
 *
 * Presentation only. The songs, the paging, the background "completion" repair, the error and the
 * retry all come from the same [OnlinePlaylistViewModel]; Guardar writes exactly the transaction the
 * classic header writes, with the same "never persist a playlist whose songs have not loaded" guard;
 * Descargar / Compartir / ⋯ are the same calls and the same [YouTubePlaylistMenu].
 *
 * ## Honest scope, carried over from the classic screen
 * Aleatorio uses context `OL:<playlistId>` so followed YouTube lists share the same no-repeat
 * memory as a saved copy of that list. The "already played" checkmark uses lifetime play time
 * even when Aleatorio mejorado is off.
 *
 * @param scrollBehavior accepted for signature parity with the classic screen; this shape draws its own
 *   header instead of a `TopAppBar`, so there is no collapsing bar to drive with it.
 */
@Suppress("UNUSED_PARAMETER")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuraOnlinePlaylistScreen(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
    viewModel: OnlinePlaylistViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val menuState = LocalMenuState.current
    val haptic = LocalHapticFeedback.current
    val focusManager = LocalFocusManager.current
    val playerConnection = LocalPlayerConnection.current ?: return
    val database = LocalDatabase.current
    val downloadUtil = LocalDownloadUtil.current
    val coroutineScope = rememberCoroutineScope()
    val isTvOrCar = rememberIsTvOrCar()

    val isPlaying by playerConnection.isEffectivelyPlaying.collectAsState()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsState()

    val playlist by viewModel.playlist.collectAsState()
    val songs by viewModel.playlistSongs.collectAsState()
    val dbPlaylist by viewModel.dbPlaylist.collectAsState()
    val relatedItems by viewModel.relatedItems.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val isLoadingMore by viewModel.isLoadingMore.collectAsState()
    val error by viewModel.error.collectAsState()
    val onlineShuffleContextId = playlist?.let { ShuffleContexts.onlinePlaylist(it.id) }
    val shufflePlayedSet = rememberPlayedShuffleSet(onlineShuffleContextId)
    val heardIds = rememberHeardSongIds(songs.map { it.id })

    val hideExplicit by rememberPreference(key = HideExplicitKey, defaultValue = false)

    val lazyListState = rememberLazyListState()
    val snackbarHostState = remember { SnackbarHostState() }

    var isSearching by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(isSearching) { if (isSearching) focusRequester.requestFocus() }

    var downloadState by remember { mutableIntStateOf(Download.STATE_STOPPED) }
    LaunchedEffect(songs) {
        if (songs.isEmpty()) return@LaunchedEffect
        downloadUtil.downloads.collect { downloads ->
            downloadState = when {
                songs.all { downloads[it.id]?.state == Download.STATE_COMPLETED } ->
                    Download.STATE_COMPLETED

                songs.all {
                    downloads[it.id]?.state == Download.STATE_QUEUED ||
                        downloads[it.id]?.state == Download.STATE_DOWNLOADING ||
                        downloads[it.id]?.state == Download.STATE_COMPLETED
                } -> Download.STATE_DOWNLOADING

                else -> Download.STATE_STOPPED
            }
        }
    }

    // Pairs of (position in the full list, song) — the position is what the queue's startIndex needs,
    // and it is also what keys the rows: unique BY CONSTRUCTION, unlike a video id.
    val filteredSongs = remember(songs, query) {
        if (query.isEmpty()) songs.mapIndexed { i, s -> i to s }
        else songs.mapIndexed { i, s -> i to s }.filter {
            it.second.title.contains(query, true) ||
                it.second.artists.any { a -> a.name.contains(query, true) }
        }
    }
    val videoPlaylist = remember(songs) {
        songs.isNotEmpty() && songs.count { it.isVideoSong } * 2 >= songs.size
    }

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

    LaunchedEffect(filteredSongs) {
        selection.toList().forEach { songId ->
            if (filteredSongs.none { it.second.id == songId }) selection.remove(songId)
        }
    }

    if (isSearching) {
        BackHandler {
            isSearching = false
            query = ""
        }
    } else if (inSelectMode) {
        BackHandler(onBack = onExitSelectionMode)
    }

    // Load the next page when the list is scrolled near its end — the classic screen pages proactively
    // in the ViewModel and exposes [OnlinePlaylistViewModel.loadMoreSongs] for the manual case.
    val shouldLoadMore by remember {
        derivedStateOf {
            val info = lazyListState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: return@derivedStateOf false
            last >= info.totalItemsCount - 3
        }
    }
    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore) viewModel.loadMoreSongs()
    }

    val bloom = rememberAuraBloom(mediaMetadata?.id)
    val currentPlaylist = playlist

    Box(modifier = Modifier.fillMaxSize().auraScreenBackground(bloom, intensity = 0.40f)) {
        LazyColumn(
            state = lazyListState,
            contentPadding = LocalPlayerAwareWindowInsets.current.union(WindowInsets.ime).asPaddingValues(),
            modifier = Modifier.fillMaxSize(),
        ) {
            // Gated on the PLAYLIST only, never on the song list: an online playlist with 0 songs still
            // has a header, and hiding it rendered literally nothing. A terminal failure shows a retry
            // instead of a blank screen forever.
            if (currentPlaylist == null) {
                if (isLoading) {
                    item(key = "aura_op_loading") {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 120.dp, bottom = 32.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator(color = AuraPalette.Teal)
                        }
                    }
                } else {
                    item(key = "aura_op_error") {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 120.dp, start = AuraSpacing.Gutter, end = AuraSpacing.Gutter),
                        ) {
                            Text(
                                text = stringResource(R.string.couldnt_load_playlist),
                                style = AuraType.RowTitle,
                                color = AuraPalette.OnGroundMuted,
                                textAlign = TextAlign.Center,
                            )
                            // Raw cause as a muted secondary line (technical, not translated) so a
                            // failure is diagnosable without hiding the localized headline.
                            error?.let { detail ->
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    text = detail,
                                    style = AuraType.RowSubtitle,
                                    color = AuraPalette.OnGroundGhost,
                                    textAlign = TextAlign.Center,
                                )
                            }
                            Spacer(Modifier.height(16.dp))
                            AuraHeaderButton(
                                icon = AuraIcons.Radio,
                                label = stringResource(R.string.retry),
                                onClick = { viewModel.retry() },
                                accent = true,
                            )
                        }
                    }
                }
            } else {
                if (!isSearching) {
                    item(key = "aura_op_header") {
                        AuraOnlinePlaylistHeader(
                            playlist = currentPlaylist,
                            songs = songs,
                            dbPlaylist = dbPlaylist,
                            downloadState = downloadState,
                            coroutineScope = coroutineScope,
                            continuation = viewModel.continuation,
                            onSearch = { isSearching = true },
                            videoPlaylist = videoPlaylist,
                            shuffleContextId = onlineShuffleContextId,
                            shufflePlayedSet = shufflePlayedSet,
                            heardIds = heardIds,
                            modifier = Modifier.animateItem(),
                        )
                    }
                } else {
                    item(key = "aura_op_search") {
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

                itemsIndexed(
                    items = filteredSongs,
                    // The song's POSITION in the full list. Unique by construction; a video id is not
                    // (a playlist may legitimately hold the same track twice).
                    key = { _, entry -> "aura_op_song_${entry.first}" },
                ) { _, entry ->
                    val index = entry.first
                    val songItem = entry.second
                    if (videoPlaylist) {
                        val selected = songItem.id in selection
                        val onCheckedChange: (Boolean) -> Unit = { checked ->
                            if (checked) selection.add(songItem.id) else selection.remove(songItem.id)
                        }
                        val isActive = mediaMetadata?.id == songItem.id
                        val blocked = hideExplicit && songItem.explicit
                        val videoLabel = auraTypeLabel(AuraContentKind.Video)
                        val playFromFiltered = {
                            when {
                                inSelectMode -> onCheckedChange(!selected)
                                isActive -> {
                                    playerConnection.togglePlayPause()
                                    playerConnection.enterVideoModeIfNeeded(forceFromUserTap = true)
                                }
                                else -> {
                                    playerConnection.playQueue(
                                        YouTubePlaylistQueue(
                                            playlistId = currentPlaylist.id,
                                            playlistTitle = currentPlaylist.title,
                                            initialSongs = songs,
                                            initialContinuation = viewModel.continuation,
                                            startIndex = index,
                                        ),
                                    )
                                    playerConnection.enterVideoModeIfNeeded(forceFromUserTap = true)
                                }
                            }
                        }
                        AuraPosterCard(
                            title = songItem.title,
                            subtitle = songItem.artists.joinToString { it.name }.takeIf { it.isNotBlank() },
                            thumbnailUrl = songItem.thumbnail,
                            seed = songItem.id,
                            ratio = 16f / 9f,
                            shape = AuraShapes.Card,
                            isActive = isActive,
                            isPlaying = isActive && isPlaying,
                            typeIcon = AuraIcons.Video,
                            typeLabel = videoLabel,
                            contentDescription = songItem.title,
                            onClick = if (blocked) null else playFromFiltered,
                            onLongClick = if (blocked) null else {
                                {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    if (inSelectMode) onCheckedChange(!selected)
                                    else {
                                        menuState.show {
                                            YouTubeSongMenu(
                                                songItem,
                                                navController,
                                                menuState::dismiss,
                                            )
                                        }
                                    }
                                }
                            },
                            modifier = Modifier
                                .animateItem()
                                .padding(horizontal = AuraSpacing.Gutter, vertical = 6.dp)
                                .tvFocusable(isTvOrCar, AuraShapes.Highlight, scaleFocused = 1f),
                        )
                    } else {
                    val selected = songItem.id in selection
                    val onCheckedChange: (Boolean) -> Unit = { checked ->
                        if (checked) selection.add(songItem.id) else selection.remove(songItem.id)
                    }
                    val isActive = mediaMetadata?.id == songItem.id
                    val blocked = hideExplicit && songItem.explicit
                    val dbSong by database.song(songItem.id).collectAsState(initial = null)
                    val alreadyPlayed = (
                        songItem.id in shufflePlayedSet ||
                            songItem.id in heardIds ||
                            (dbSong?.song?.totalPlayTime ?: 0L) > 0L
                        ) && !isActive

                    AuraRow(
                        title = songItem.title,
                        subtitle = songItem.artists.joinToString { it.name },
                        highlighted = isActive,
                        dimmed = blocked || alreadyPlayed,
                        contentDescription = songItem.title,
                        onClick = if (blocked) null else {
                            {
                                when {
                                    inSelectMode -> onCheckedChange(!selected)
                                    isActive -> playerConnection.togglePlayPause()
                                    else -> playerConnection.playQueue(
                                        YouTubePlaylistQueue(
                                            playlistId = currentPlaylist.id,
                                            playlistTitle = currentPlaylist.title,
                                            initialSongs = songs,
                                            initialContinuation = viewModel.continuation,
                                            startIndex = index,
                                            contextId = onlineShuffleContextId,
                                        ),
                                    )
                                }
                            }
                        },
                        onLongClick = if (blocked) null else {
                            {
                                if (!inSelectMode) {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    inSelectMode = true
                                    onCheckedChange(true)
                                }
                            }
                        },
                        artwork = {
                            val videoThumb = songItem.isVideoSong
                            AuraCover(
                                thumbnailUrl = songItem.thumbnail,
                                size = if (videoThumb) 88.dp else 50.dp,
                                seed = songItem.id,
                                ratio = if (videoThumb) 16f / 9f else 1f,
                                shape = if (videoThumb) AuraShapes.Card else AuraShapes.Artwork,
                                fillBleed = videoThumb,
                            ) {
                                if (isActive && isPlaying) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(AuraPalette.Ground.copy(alpha = 0.55f)),
                                        contentAlignment = Alignment.Center,
                                    ) { AuraPlayingBars() }
                                } else if (videoThumb && !isActive) {
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.TopStart)
                                            .padding(4.dp)
                                            .clip(AuraShapes.Pill)
                                            .background(AuraPalette.Ground.copy(alpha = 0.72f))
                                            .padding(horizontal = 5.dp, vertical = 2.dp),
                                    ) {
                                        Text(
                                            text = auraTypeLabel(AuraContentKind.Video),
                                            style = AuraType.QualityBadge,
                                            color = AuraPalette.Teal,
                                            maxLines = 1,
                                        )
                                    }
                                }
                            }
                        },
                            trailing = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(7.dp),
                            ) {
                                if (alreadyPlayed) {
                                    AuraIconGlyph(
                                        icon = AuraIcons.Check,
                                        contentDescription = stringResource(R.string.cd_shuffle_already_played),
                                        size = 16.dp,
                                        tint = AuraPalette.Teal,
                                    )
                                }
                                if (songItem.explicit) {
                                    AuraTechnicalText(
                                        text = "E",
                                        color = AuraPalette.OnGroundDisabled,
                                        style = AuraType.QualityBadge,
                                    )
                                }
                                AuraDownloadTick(songId = songItem.id)
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
                                        contentDescription = songItem.title,
                                        onClick = {
                                            menuState.show {
                                                YouTubeSongMenu(
                                                    songItem,
                                                    navController,
                                                    menuState::dismiss,
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
                }

                if (isLoadingMore) {
                    item(key = "aura_op_loading_more") {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator(
                                color = AuraPalette.Teal,
                                strokeWidth = 2.dp,
                                modifier = Modifier.height(22.dp).width(22.dp),
                            )
                        }
                    }
                }

                if (relatedItems.isNotEmpty() && !isSearching) {
                    item(key = "aura_op_related_title") {
                        AuraSectionLabel(
                            text = stringResource(R.string.you_might_also_like).uppercase(Locale.ROOT),
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
                    item(key = "aura_op_related") {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = AuraSpacing.Gutter),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .animateItem(),
                        ) {
                            // POSITION as the key: "you might also like" comes from the server, and
                            // 0.6.148-beta1 crashed on exactly this shape of list because several
                            // entries shared one id.
                            itemsIndexed(
                                items = relatedItems,
                                key = { position, _ -> "aura_op_rel_$position" },
                            ) { _, item ->
                                AuraTypedYtCoverCard(
                                    item = item,
                                    cardScale = 1f,
                                    onClick = {
                                        when (item) {
                                            is PlaylistItem ->
                                                navController.navigate("online_playlist/${item.id}")

                                            is AlbumItem ->
                                                navController.navigate("album/${item.browseId}")

                                            is ArtistItem ->
                                                navController.navigate("artist/${item.id}")

                                            is SongItem -> playerConnection.playQueue(
                                                YouTubeQueue(WatchEndpoint(videoId = item.id)),
                                            )
                                        }
                                    },
                                    onLongClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        menuState.show {
                                            when (item) {
                                                is PlaylistItem -> YouTubePlaylistMenu(
                                                    playlist = item,
                                                    coroutineScope = coroutineScope,
                                                    onDismiss = menuState::dismiss,
                                                )

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
                                            }
                                        }
                                    },
                                )
                            }
                        }
                    }
                }

                item(key = "aura_op_tail") { Spacer(Modifier.height(50.dp)) }
            }
        }

        // Sticky chrome: back only. Title/search stay in the scrolling header (owner: no black
        // sticky bar with playlist name + search).
        AuraDetailTopBar(
            listState = lazyListState,
            title = currentPlaylist?.title.orEmpty(),
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
                    checked = selection.size == filteredSongs.size && selection.isNotEmpty(),
                    onCheckedChange = {
                        if (selection.size == filteredSongs.size) {
                            selection.clear()
                        } else {
                            selection.clear()
                            selection.addAll(filteredSongs.map { it.second.id })
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
                            YouTubeSelectionSongMenu(
                                songSelection = filteredSongs
                                    .filter { it.second.id in selection }
                                    .map { it.second },
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
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

// ── Cabecera ──────────────────────────────────────────────────────────────────────────────────────

@Composable
private fun AuraOnlinePlaylistHeader(
    playlist: PlaylistItem,
    songs: List<SongItem>,
    dbPlaylist: Playlist?,
    downloadState: Int,
    coroutineScope: CoroutineScope,
    continuation: String?,
    onSearch: () -> Unit,
    videoPlaylist: Boolean = false,
    shuffleContextId: String? = null,
    shufflePlayedSet: Set<String> = emptySet(),
    heardIds: Set<String> = emptySet(),
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val database = LocalDatabase.current
    val menuState = LocalMenuState.current
    val playerConnection = LocalPlayerConnection.current ?: return
    val isPlaying by playerConnection.isEffectivelyPlaying.collectAsState()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsState()
    val isTvOrCar = rememberIsTvOrCar()
    val isWideLayout = rememberIsWideLayout()

    val saved = dbPlaylist?.playlist?.bookmarkedAt != null
    val playingThisPlaylist = isPlaying && mediaMetadata?.album?.id == playlist.id
    val hasExplicitContent = remember(songs) { songs.any { it.explicit } }
    val totalDuration = remember(songs) { songs.sumOf { it.duration ?: 0 } }

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
                thumbnailUrl = playlist.thumbnail,
                size = if (isWideLayout) 320.dp else 260.dp,
                seed = playlist.id,
                shape = AuraShapes.PlayerArtwork,
                decodeTo = 512,
            )
        }

        Spacer(Modifier.height(26.dp))

        Text(
            text = playlist.title,
            style = AuraType.ScreenTitle,
            color = AuraPalette.OnGround,
            maxLines = 2,
            overflow = AuraDefaultOverflow,
            modifier = Modifier.padding(horizontal = 32.dp),
        )

        playlist.author?.name?.let { authorName ->
            Spacer(Modifier.height(4.dp))
            Text(
                text = authorName,
                style = AuraType.RowSubtitle,
                color = AuraPalette.Teal,
                maxLines = 1,
                overflow = AuraDefaultOverflow,
                modifier = Modifier.padding(horizontal = 32.dp),
            )
        }

        Spacer(Modifier.height(6.dp))

        Text(
            text = buildString {
                append(
                    if (videoPlaylist) {
                        pluralStringResource(R.plurals.n_video, songs.size, songs.size)
                    } else {
                        pluralStringResource(R.plurals.n_song, songs.size, songs.size)
                    },
                )
                if (totalDuration > 0) {
                    append(" • ")
                    val hours = totalDuration / 3600
                    val minutes = (totalDuration % 3600) / 60
                    if (hours > 0) append("${hours}h ${minutes}m") else append("${minutes}m")
                }
                if (hasExplicitContent) {
                    append(" • ")
                    append(stringResource(R.string.explicit))
                }
            },
            style = AuraType.RowSubtitle,
            color = AuraPalette.OnGroundMuted,
            maxLines = 1,
            overflow = AuraDefaultOverflow,
        )

        run {
            val playedCount = remember(shufflePlayedSet, heardIds, songs) {
                songs.count { it.id in shufflePlayedSet || it.id in heardIds }
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
            // Hybrid (Apple + YTM): primary actions keep their name; search/more stay icon-only.
            val contextId = shuffleContextId ?: ShuffleContexts.onlinePlaylist(playlist.id)
            val onShuffleClick = rememberShuffleMemoryPrompt(
                contextId = contextId,
                playedCount = songs.count { it.id in shufflePlayedSet || it.id in heardIds },
                totalCount = songs.size,
            ) { resetMemory ->
                val seed = ShuffleContexts.seedPlayedIds(
                    resetMemory = resetMemory,
                    songIds = songs.map { it.id },
                    shufflePlayed = shufflePlayedSet,
                    playTimeMs = { id -> if (id in heardIds) 1L else 0L },
                )
                val ordered = if (resetMemory) {
                    songs.shuffled()
                } else {
                    val (unheard, heard) = songs.partition { it.id !in seed }
                    unheard.shuffled() + heard.shuffled()
                }
                playerConnection.playQueue(
                    YouTubePlaylistQueue(
                        playlistId = playlist.id,
                        playlistTitle = playlist.title,
                        initialSongs = ordered,
                        initialContinuation = continuation,
                        contextId = contextId,
                        startShuffled = true,
                        seedPlayedIds = seed,
                    ),
                )
            }
            AuraHeaderButton(
                icon = AuraIcons.Shuffle,
                label = stringResource(R.string.shuffle_label),
                onClick = onShuffleClick,
                accent = false,
                enabled = songs.isNotEmpty(),
                modifier = Modifier.weight(1f).tvFocusable(isTvOrCar, scaleFocused = 1f),
            )
            AuraHeaderButton(
                icon = if (playingThisPlaylist) AuraIcons.Pause else AuraIcons.Play,
                label = stringResource(if (playingThisPlaylist) R.string.pause else R.string.play),
                onClick = {
                    if (songs.isNotEmpty()) {
                        playerConnection.playQueue(
                            YouTubePlaylistQueue(
                                playlistId = playlist.id,
                                playlistTitle = playlist.title,
                                initialSongs = songs,
                                initialContinuation = continuation,
                                contextId = shuffleContextId ?: ShuffleContexts.onlinePlaylist(playlist.id),
                            ),
                        )
                    }
                },
                accent = true,
                enabled = songs.isNotEmpty(),
                modifier = Modifier.weight(1f).tvFocusable(isTvOrCar, scaleFocused = 1f),
            )
        }

        Spacer(Modifier.height(12.dp))

        // Guardar · Descargar · Compartir · Buscar · Más — secondary row.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AuraSpacing.Gutter),
            horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AuraHeaderCircleButton(
                icon = if (saved) AuraIcons.HeartFilled else AuraIcons.Heart,
                contentDescription = stringResource(if (saved) R.string.saved else R.string.save),
                onClick = {
                    coroutineScope.launch(Dispatchers.IO) {
                        if (dbPlaylist != null) {
                            database.withTransaction {
                                // Save/un-save ONLY. Deliberately does NOT refresh metadata here: this
                                // screen's PlaylistItem comes from YouTube.playlist(), which hardcodes
                                // playEndpoint = null and can yield an empty title/thumbnail; and
                                // refreshing thumbnailUrl would DESTROY a custom cover. The library sync
                                // owns metadata refresh. This also un-tombstones a row removed from the
                                // app, so saving again reuses it instead of creating a duplicate.
                                update(dbPlaylist.playlist.toggleLike())
                            }
                        } else {
                            // Never persist a NEW playlist before its songs have loaded: the header
                            // renders as soon as `playlist` is set (songs arrive later), and saving in
                            // that window wrote a PlaylistEntity with remoteSongCount = N and ZERO
                            // PlaylistSongMap rows. `enabled` below already blocks the tap; this is the
                            // backstop.
                            if (songs.isEmpty()) return@launch
                            database.withTransaction {
                                val playlistEntity = PlaylistEntity(
                                    name = playlist.title,
                                    browseId = playlist.id,
                                    thumbnailUrl = playlist.thumbnail,
                                    isEditable = playlist.isEditable,
                                    remoteSongCount = playlist.songCountText?.let {
                                        Regex("""\d+""").find(it)?.value?.toIntOrNull()
                                    },
                                    playEndpointParams = playlist.playEndpoint?.params,
                                    shuffleEndpointParams = playlist.shuffleEndpoint?.params,
                                    radioEndpointParams = playlist.radioEndpoint?.params,
                                ).toggleLike()
                                insert(playlistEntity)
                                songs.map { it.toMediaMetadata() }
                                    .onEach { insert(it) }
                                    .mapIndexed { index, song ->
                                        PlaylistSongMap(
                                            songId = song.id,
                                            playlistId = playlistEntity.id,
                                            position = index,
                                            setVideoId = song.setVideoId,
                                        )
                                    }
                                    .forEach { insert(it) }
                            }
                        }
                    }
                },
                accent = false,
                // Saving a playlist that has no songs yet would store an empty one; un-saving an already
                // saved playlist stays available because it touches no song rows.
                enabled = songs.isNotEmpty() || dbPlaylist != null,
                modifier = Modifier.tvFocusable(isTvOrCar, scaleFocused = 1f),
            )
            AuraHeaderCircleButton(
                icon = AuraIcons.Download,
                contentDescription = when (downloadState) {
                    Download.STATE_COMPLETED -> stringResource(R.string.remove_download)
                    Download.STATE_DOWNLOADING -> stringResource(R.string.downloading)
                    else -> stringResource(R.string.action_download)
                },
                onClick = {
                    when (downloadState) {
                        Download.STATE_COMPLETED, Download.STATE_DOWNLOADING -> songs.forEach { song ->
                            DownloadService.sendRemoveDownload(
                                context,
                                ExoDownloadService::class.java,
                                song.id,
                                false,
                            )
                        }

                        else -> songs.forEach { song ->
                            val downloadRequest = DownloadRequest
                                .Builder(song.id, song.id.toUri())
                                .setCustomCacheKey(song.id)
                                .setData(song.title.toByteArray())
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
                accent = false,
                // Both branches iterate `songs`, so a tap before they arrive did nothing at all while
                // looking enabled.
                enabled = songs.isNotEmpty(),
                modifier = Modifier.tvFocusable(isTvOrCar, scaleFocused = 1f),
            )
            AuraHeaderCircleButton(
                icon = AuraIcons.Share,
                contentDescription = stringResource(R.string.share),
                onClick = {
                    val intent = Intent().apply {
                        action = Intent.ACTION_SEND
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, playlist.shareLink)
                    }
                    context.startActivity(Intent.createChooser(intent, null))
                },
                modifier = Modifier.tvFocusable(isTvOrCar, scaleFocused = 1f),
            )
            AuraHeaderCircleButton(
                icon = AuraIcons.Search,
                contentDescription = stringResource(R.string.search),
                onClick = onSearch,
                modifier = Modifier.tvFocusable(isTvOrCar, scaleFocused = 1f),
            )
            AuraHeaderCircleButton(
                icon = AuraIcons.More,
                contentDescription = stringResource(R.string.more_options),
                onClick = {
                    menuState.show {
                        YouTubePlaylistMenu(
                            playlist = playlist,
                            songs = songs,
                            coroutineScope = coroutineScope,
                            onDismiss = menuState::dismiss,
                        )
                    }
                },
                modifier = Modifier.tvFocusable(isTvOrCar, scaleFocused = 1f),
            )
        }
    }
}
