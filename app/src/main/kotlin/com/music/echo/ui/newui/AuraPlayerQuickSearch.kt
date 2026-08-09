package iad1tya.echo.music.ui.newui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import com.music.innertube.models.AlbumItem
import com.music.innertube.models.ArtistItem
import com.music.innertube.models.PlaylistItem
import com.music.innertube.models.SongItem
import com.music.innertube.models.WatchEndpoint
import com.music.innertube.models.YTItem
import iad1tya.echo.music.LocalDatabase
import iad1tya.echo.music.LocalPlayerConnection
import iad1tya.echo.music.R
import iad1tya.echo.music.constants.HideVideoSongsKey
import iad1tya.echo.music.constants.OfflineModeKey
import iad1tya.echo.music.constants.SearchSource
import iad1tya.echo.music.constants.SearchSourceKey
import iad1tya.echo.music.db.entities.SearchHistory
import iad1tya.echo.music.extensions.toMediaItem
import iad1tya.echo.music.models.toMediaMetadata
import iad1tya.echo.music.playback.queues.ListQueue
import iad1tya.echo.music.playback.queues.YouTubeQueue
import iad1tya.echo.music.ui.component.LocalMenuState
import iad1tya.echo.music.ui.menu.AddToPlaylistDialog
import iad1tya.echo.music.ui.menu.YouTubeAlbumMenu
import iad1tya.echo.music.ui.menu.YouTubeArtistMenu
import iad1tya.echo.music.ui.menu.YouTubePlaylistMenu
import iad1tya.echo.music.ui.menu.YouTubeSongMenu
import iad1tya.echo.music.utils.rememberEnumPreference
import iad1tya.echo.music.utils.rememberPreference
import iad1tya.echo.music.viewmodels.OnlineSearchSuggestionViewModel
import iad1tya.echo.music.viewmodels.PlayerOnlineSearchViewModel
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

/**
 * Player quick-search: same sources as Buscar (biblioteca ↔ YouTube Music), full YTM summary results.
 * Artist / album / playlist open **inside** this frost sheet (local browse stack) — the sheet stays open
 * until the user closes it (back / leading chevron). Songs keep the sheet open for play / next / queue.
 */
@Composable
fun AuraPlayerQuickSearchContent(
    navController: NavController,
    onDismiss: () -> Unit,
    @Suppress("UNUSED_PARAMETER")
    onBrowseAway: () -> Unit = onDismiss,
    isListenTogetherGuest: Boolean = false,
    modifier: Modifier = Modifier,
    suggestionViewModel: OnlineSearchSuggestionViewModel = hiltViewModel(),
    onlineResultsViewModel: PlayerOnlineSearchViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val database = LocalDatabase.current
    val playerConnection = LocalPlayerConnection.current ?: return
    val menuState = LocalMenuState.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()

    val offlineMode by rememberPreference(OfflineModeKey, false)
    var searchSource by rememberEnumPreference(SearchSourceKey, SearchSource.ONLINE)
    val effectiveSource = if (offlineMode) SearchSource.LOCAL else searchSource

    val isPlaying by playerConnection.isEffectivelyPlaying.collectAsState()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsState()
    val hideVideoSongs by rememberPreference(HideVideoSongsKey, false)

    var query by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue())
    }
    var showChoosePlaylistDialog by rememberSaveable { mutableStateOf(false) }
    var pendingPlaylistSong by remember { mutableStateOf<SongItem?>(null) }

    var browseStack by remember { mutableStateOf<List<QuickSearchBrowse>>(listOf(QuickSearchBrowse.Search)) }
    val browseTop = browseStack.lastOrNull() ?: QuickSearchBrowse.Search

    val focusRequester = remember { FocusRequester() }
    // Defer IME/focus until after the frost sheet finishes expanding — focusing on the first
    // frame races ModalBottomSheet.show() and causes the open animation to hitch then resume.
    LaunchedEffect(browseTop) {
        if (browseTop is QuickSearchBrowse.Search) {
            kotlinx.coroutines.delay(280)
            runCatching { focusRequester.requestFocus() }
        }
    }

    fun popBrowse() {
        if (browseStack.size > 1) {
            browseStack = browseStack.dropLast(1)
        } else {
            onDismiss()
        }
    }

    fun pushBrowse(dest: QuickSearchBrowse) {
        browseStack = browseStack + dest
    }

    BackHandler(onBack = { popBrowse() })

    val suggestionState by suggestionViewModel.viewState.collectAsState()
    val summaryPage = onlineResultsViewModel.summaryPage
    val onlineLoading = onlineResultsViewModel.loading
    val committedOnline = onlineResultsViewModel.committedQuery

    LaunchedEffect(query.text, effectiveSource) {
        if (effectiveSource == SearchSource.ONLINE && !offlineMode) {
            suggestionViewModel.query.value = query.text
            if (committedOnline != null && query.text.trim() != committedOnline) {
                onlineResultsViewModel.clearCommitted()
            }
        }
    }

    AddToPlaylistDialog(
        isVisible = showChoosePlaylistDialog,
        onGetSong = {
            val item = pendingPlaylistSong ?: return@AddToPlaylistDialog emptyList()
            val meta = item.toMediaMetadata()
            database.withTransaction { insert(meta) }
            listOf(meta.id)
        },
        onDismiss = {
            showChoosePlaylistDialog = false
            pendingPlaylistSong = null
        },
    )

    val voice = rememberAuraVoiceSearch(
        onPartial = { spoken -> query = TextFieldValue(spoken, TextRange(spoken.length)) },
        onResult = { spoken ->
            query = TextFieldValue(spoken, TextRange(spoken.length))
            if (effectiveSource == SearchSource.ONLINE && !offlineMode) {
                onlineResultsViewModel.search(spoken)
            }
        },
    )

    val openSongActions: (SongItem) -> Unit = { song ->
        menuState.show {
            AuraPlayerQuickSearchSongMenu(
                title = song.title,
                isGuest = isListenTogetherGuest,
                onDismiss = menuState::dismiss,
                onPlayNow = {
                    menuState.dismiss()
                    playerConnection.playQueue(
                        YouTubeQueue(WatchEndpoint(videoId = song.id), song.toMediaMetadata()),
                    )
                },
                onPlayNext = {
                    menuState.dismiss()
                    playerConnection.playNext(song.toMediaItem())
                },
                onAddToQueue = {
                    menuState.dismiss()
                    playerConnection.addToQueue(song.toMediaItem())
                },
                onAddToPlaylist = {
                    menuState.dismiss()
                    pendingPlaylistSong = song
                    showChoosePlaylistDialog = true
                },
            )
        }
    }

    val openYtMenu: (YTItem) -> Unit = { item ->
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
                is ArtistItem -> YouTubeArtistMenu(artist = item, onDismiss = menuState::dismiss)
                is PlaylistItem -> YouTubePlaylistMenu(
                    playlist = item,
                    coroutineScope = scope,
                    onDismiss = menuState::dismiss,
                )
            }
        }
    }

    val openBrowse: (YTItem) -> Unit = { item ->
        when (item) {
            is SongItem -> openSongActions(item)
            is AlbumItem -> pushBrowse(QuickSearchBrowse.Album(item.id, item.title))
            is ArtistItem -> pushBrowse(QuickSearchBrowse.Artist(item.id, item.title))
            is PlaylistItem -> pushBrowse(QuickSearchBrowse.Playlist(item.id, item.title))
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        when (val page = browseTop) {
            is QuickSearchBrowse.Search -> {
                Text(
                    text = stringResource(R.string.search),
                    style = AuraType.ScreenTitle,
                    color = AuraPalette.OnGround,
                    modifier = Modifier.padding(horizontal = AuraSpacing.Gutter, vertical = 4.dp),
                )

                AuraSearchInputBar(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = stringResource(
                        when (effectiveSource) {
                            SearchSource.LOCAL -> R.string.search_library
                            SearchSource.ONLINE -> R.string.search_yt_music
                        },
                    ),
                    active = true,
                    onLeadingClick = onDismiss,
                    onSubmit = {
                        keyboardController?.hide()
                        if (effectiveSource == SearchSource.ONLINE && !offlineMode) {
                            onlineResultsViewModel.search(query.text)
                        }
                    },
                    onClear = {
                        query = TextFieldValue("")
                        onlineResultsViewModel.clearCommitted()
                    },
                    onVoice = voice,
                    focusRequester = focusRequester,
                    onFieldTap = {},
                    showSource = !offlineMode,
                    offlineMode = offlineMode,
                    sourceIsLocal = effectiveSource == SearchSource.LOCAL,
                    onToggleSource = { searchSource = searchSource.toggle() },
                )

                when (effectiveSource) {
                    SearchSource.LOCAL -> {
                        // Library destinations still leave the sheet (local screens), but do not
                        // collapse the player — only dismiss this frost plate.
                        AuraLocalSearchResults(
                            query = query.text,
                            navController = navController,
                            onDismiss = onDismiss,
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                        )
                    }

                    SearchSource.ONLINE -> {
                        val showCommitted = committedOnline != null &&
                            query.text.trim() == committedOnline &&
                            (summaryPage != null || onlineLoading)

                        if (showCommitted) {
                            PlayerOnlineSummaryResults(
                                summaryPage = summaryPage,
                                loading = onlineLoading,
                                hideVideoSongs = hideVideoSongs,
                                isPlaying = isPlaying,
                                mediaId = mediaMetadata?.id,
                                albumId = mediaMetadata?.album?.id,
                                onSongClick = { song ->
                                    if (song.id == mediaMetadata?.id) {
                                        playerConnection.togglePlayPause()
                                    } else {
                                        openSongActions(song)
                                    }
                                },
                                onSongLongClick = { song ->
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    openSongActions(song)
                                },
                                onItemClick = openBrowse,
                                onItemMenu = openYtMenu,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                            )
                        } else {
                            PlayerOnlineSuggestionPanel(
                                query = query.text,
                                viewState = suggestionState,
                                isPlaying = isPlaying,
                                mediaId = mediaMetadata?.id,
                                albumId = mediaMetadata?.album?.id,
                                onRunSearch = { picked ->
                                    query = TextFieldValue(picked, TextRange(picked.length))
                                    onlineResultsViewModel.search(picked)
                                },
                                onFillQuery = { filled ->
                                    query = TextFieldValue(filled, TextRange(filled.length))
                                },
                                onDeleteHistory = { history ->
                                    database.query { delete(history) }
                                },
                                onSongClick = openSongActions,
                                onItemClick = openBrowse,
                                onItemMenu = openYtMenu,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                            )
                        }
                    }
                }
            }

            is QuickSearchBrowse.Artist,
            is QuickSearchBrowse.Album,
            is QuickSearchBrowse.Playlist,
            -> {
                QuickSearchBrowsePane(
                    dest = page,
                    isPlaying = isPlaying,
                    mediaId = mediaMetadata?.id,
                    albumId = mediaMetadata?.album?.id,
                    hideVideoSongs = hideVideoSongs,
                    onBack = { popBrowse() },
                    onSongClick = { song ->
                        if (song.id == mediaMetadata?.id) {
                            playerConnection.togglePlayPause()
                        } else {
                            openSongActions(song)
                        }
                    },
                    onSongLongClick = { song ->
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        openSongActions(song)
                    },
                    onItemClick = openBrowse,
                    onItemMenu = openYtMenu,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                )
            }
        }
    }
}

private sealed class QuickSearchBrowse {
    data object Search : QuickSearchBrowse()
    data class Artist(val id: String, val title: String) : QuickSearchBrowse()
    data class Album(val id: String, val title: String) : QuickSearchBrowse()
    data class Playlist(val id: String, val title: String) : QuickSearchBrowse()
}

@Composable
private fun QuickSearchBrowsePane(
    dest: QuickSearchBrowse,
    isPlaying: Boolean,
    mediaId: String?,
    albumId: String?,
    hideVideoSongs: Boolean,
    onBack: () -> Unit,
    onSongClick: (SongItem) -> Unit,
    onSongLongClick: (SongItem) -> Unit,
    onItemClick: (YTItem) -> Unit,
    onItemMenu: (YTItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    val title = when (dest) {
        is QuickSearchBrowse.Artist -> dest.title
        is QuickSearchBrowse.Album -> dest.title
        is QuickSearchBrowse.Playlist -> dest.title
        QuickSearchBrowse.Search -> stringResource(R.string.search)
    }
    var loading by remember(dest) { mutableStateOf(true) }
    var error by remember(dest) { mutableStateOf(false) }
    var sections by remember(dest) { mutableStateOf<List<Pair<String, List<YTItem>>>>(emptyList()) }

    LaunchedEffect(dest) {
        loading = true
        error = false
        sections = emptyList()
        val result = runCatching {
            when (dest) {
                is QuickSearchBrowse.Artist -> {
                    val page = com.music.innertube.YouTube.artist(dest.id).getOrThrow()
                    page.sections.map { it.title to it.items }
                }
                is QuickSearchBrowse.Album -> {
                    val page = com.music.innertube.YouTube.album(dest.id).getOrThrow()
                    listOf("" to page.songs)
                }
                is QuickSearchBrowse.Playlist -> {
                    val page = com.music.innertube.YouTube.playlist(dest.id).getOrThrow()
                    listOf("" to page.songs)
                }
                QuickSearchBrowse.Search -> emptyList()
            }
        }
        loading = false
        if (result.isFailure) {
            error = true
        } else {
            sections = result.getOrDefault(emptyList())
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AuraSpacing.Gutter, vertical = 4.dp),
        ) {
            AuraIconButton(
                icon = AuraIcons.ChevronRight,
                contentDescription = stringResource(R.string.back_button_desc),
                onClick = onBack,
                tint = AuraPalette.OnGround,
                modifier = Modifier.graphicsLayer { rotationZ = 180f },
            )
            Text(
                text = title,
                style = AuraType.ScreenTitle,
                color = AuraPalette.OnGround,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 4.dp),
            )
        }

        when {
            loading -> {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    CircularProgressIndicator(color = AuraPalette.Teal)
                }
            }
            error -> {
                Text(
                    text = stringResource(R.string.error_unknown),
                    style = AuraType.RowSubtitle,
                    color = AuraPalette.OnGroundGhost,
                    modifier = Modifier.padding(AuraSpacing.Gutter),
                )
            }
            else -> {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    sections.forEach { (sectionTitle, items) ->
                        val filtered = items.filterNot {
                            hideVideoSongs && it is SongItem && it.isVideoSong
                        }
                        if (filtered.isEmpty()) return@forEach
                        if (sectionTitle.isNotBlank()) {
                            item(key = "qb_h_$sectionTitle") {
                                AuraSectionHeader(title = sectionTitle)
                            }
                        }
                        items(filtered, key = { "qb_${sectionTitle}_${it.id}" }) { item ->
                            AuraYtItemRow(
                                item = item,
                                isActive = when (item) {
                                    is SongItem -> mediaId == item.id
                                    is AlbumItem -> albumId == item.id
                                    else -> false
                                },
                                isPlaying = isPlaying,
                                onClick = {
                                    when (item) {
                                        is SongItem -> onSongClick(item)
                                        else -> onItemClick(item)
                                    }
                                },
                                onLongClick = {
                                    if (item is SongItem) onSongLongClick(item) else onItemMenu(item)
                                },
                                onMenuClick = { onItemMenu(item) },
                            )
                        }
                    }
                    item(key = "qb_spacer") { Spacer(Modifier.height(24.dp)) }
                }
            }
        }
    }
}

@Composable
private fun PlayerOnlineSuggestionPanel(
    query: String,
    viewState: iad1tya.echo.music.viewmodels.SearchSuggestionViewState,
    isPlaying: Boolean,
    mediaId: String?,
    albumId: String?,
    onRunSearch: (String) -> Unit,
    onFillQuery: (String) -> Unit,
    onDeleteHistory: (SearchHistory) -> Unit,
    onSongClick: (SongItem) -> Unit,
    onItemClick: (YTItem) -> Unit,
    onItemMenu: (YTItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    val keyboardController = LocalSoftwareKeyboardController.current
    val lazyListState = rememberLazyListState()
    LaunchedEffect(Unit) {
        snapshotFlow { lazyListState.firstVisibleItemScrollOffset }
            .drop(1)
            .collect { if (lazyListState.isScrollInProgress) keyboardController?.hide() }
    }
    LaunchedEffect(query, viewState) {
        if (!lazyListState.isScrollInProgress) {
            lazyListState.scrollToItem(0)
        }
    }

    LazyColumn(state = lazyListState, modifier = modifier) {
        if (viewState.history.isNotEmpty()) {
            item(key = "pq_history_header") {
                AuraSectionHeader(title = stringResource(R.string.search_history))
            }
            items(viewState.history, key = { "pq_hist_${it.query}" }) { history ->
                AuraPlayerQueryRow(
                    text = history.query,
                    fromHistory = true,
                    onClick = { onRunSearch(history.query) },
                    onDelete = { onDeleteHistory(history) },
                    onFill = { onFillQuery(history.query) },
                )
            }
        }

        if (viewState.suggestions.isNotEmpty()) {
            item(key = "pq_sug_header") {
                AuraSectionHeader(title = stringResource(R.string.suggestions))
            }
            items(viewState.suggestions, key = { "pq_sug_$it" }) { suggestion ->
                AuraPlayerQueryRow(
                    text = suggestion,
                    fromHistory = false,
                    onClick = { onRunSearch(suggestion) },
                    onDelete = null,
                    onFill = { onFillQuery(suggestion) },
                )
            }
        }

        if (viewState.items.isNotEmpty()) {
            item(key = "pq_top_header") {
                AuraSectionHeader(
                    title = stringResource(
                        if (viewState.isFromLink) R.string.parsed_from_link else R.string.top_result,
                    ),
                    accent = AuraPalette.Teal,
                )
            }
            items(viewState.items, key = { "pq_item_${it.id}" }) { item ->
                AuraYtItemRow(
                    item = item,
                    isActive = when (item) {
                        is SongItem -> mediaId == item.id
                        is AlbumItem -> albumId == item.id
                        else -> false
                    },
                    isPlaying = isPlaying,
                    onClick = {
                        if (item is SongItem) onSongClick(item) else onItemClick(item)
                    },
                    onLongClick = { onItemMenu(item) },
                    onMenuClick = { onItemMenu(item) },
                )
            }
        }

        if (query.isNotBlank() &&
            viewState.history.isEmpty() &&
            viewState.suggestions.isEmpty() &&
            viewState.items.isEmpty()
        ) {
            item(key = "pq_hint") {
                Text(
                    text = stringResource(R.string.search_yt_music),
                    style = AuraType.RowSubtitle,
                    color = AuraPalette.OnGroundGhost,
                    modifier = Modifier.padding(
                        horizontal = AuraSpacing.Gutter,
                        vertical = 16.dp,
                    ),
                )
            }
        }

        item(key = "pq_sug_spacer") { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun PlayerOnlineSummaryResults(
    summaryPage: com.music.innertube.pages.SearchSummaryPage?,
    loading: Boolean,
    hideVideoSongs: Boolean,
    isPlaying: Boolean,
    mediaId: String?,
    albumId: String?,
    onSongClick: (SongItem) -> Unit,
    onSongLongClick: (SongItem) -> Unit,
    onItemClick: (YTItem) -> Unit,
    onItemMenu: (YTItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    val lazyListState = rememberLazyListState()

    LazyColumn(state = lazyListState, modifier = modifier) {
        if (loading && summaryPage == null) {
            item(key = "pq_loading") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    CircularProgressIndicator(color = AuraPalette.Teal)
                }
            }
        }

        summaryPage?.summaries
            ?.sortedBy { summary ->
                when (summary.items.firstOrNull()) {
                    is ArtistItem -> 0
                    is SongItem -> 1
                    is AlbumItem -> 2
                    is PlaylistItem -> 3
                    else -> 4
                }
            }
            ?.forEach { summary ->
                val musicItems = summary.items.filterNot {
                    hideVideoSongs && it is SongItem && it.isVideoSong
                }
                if (musicItems.isEmpty()) return@forEach

                item(key = "pq_sum_h_${summary.title}") {
                    AuraSectionHeader(title = summary.title)
                }
                items(musicItems, key = { "pq_sum_${summary.title}_${it.id}" }) { item ->
                    AuraYtItemRow(
                        item = item,
                        isActive = when (item) {
                            is SongItem -> mediaId == item.id
                            is AlbumItem -> albumId == item.id
                            else -> false
                        },
                        isPlaying = isPlaying,
                        onClick = {
                            when (item) {
                                is SongItem -> onSongClick(item)
                                else -> onItemClick(item)
                            }
                        },
                        onLongClick = {
                            if (item is SongItem) onSongLongClick(item) else onItemMenu(item)
                        },
                        onMenuClick = { onItemMenu(item) },
                    )
                }
            }

        if (!loading && summaryPage != null &&
            summaryPage.summaries.all { s ->
                s.items.none { !(hideVideoSongs && it is SongItem && it.isVideoSong) }
            }
        ) {
            item(key = "pq_empty") {
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

        item(key = "pq_sum_spacer") { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun AuraPlayerQueryRow(
    text: String,
    fromHistory: Boolean,
    onClick: () -> Unit,
    onDelete: (() -> Unit)?,
    onFill: () -> Unit,
) {
    AuraRow(
        title = text,
        onClick = onClick,
        contentDescription = text,
        leading = {
            AuraIconGlyph(
                icon = if (fromHistory) AuraIcons.History else AuraIcons.Search,
                contentDescription = null,
                size = 17.dp,
                tint = AuraPalette.OnGroundDisabled,
            )
        },
        trailing = {
            androidx.compose.foundation.layout.Row(
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (onDelete != null) {
                    AuraIconButton(
                        icon = AuraIcons.Plus,
                        contentDescription = stringResource(R.string.search_history_remove),
                        onClick = onDelete,
                        size = 15.dp,
                        tint = AuraPalette.OnGroundDisabled,
                        modifier = Modifier.padding(end = 2.dp),
                    )
                }
                AuraIconButton(
                    icon = AuraIcons.ChevronRight,
                    contentDescription = stringResource(R.string.search_use_suggestion),
                    onClick = onFill,
                    size = 16.dp,
                    tint = AuraPalette.OnGroundDisabled,
                )
            }
        },
        modifier = Modifier.padding(horizontal = AuraSpacing.Gutter),
    )
}

@Composable
internal fun AuraPlayerQuickSearchSongMenu(
    title: String,
    isGuest: Boolean,
    onDismiss: () -> Unit,
    onPlayNow: () -> Unit,
    onPlayNext: () -> Unit,
    onAddToQueue: () -> Unit,
    onAddToPlaylist: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
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
            onClick = onPlayNow,
        )

        if (!isGuest) {
            AuraMenuRow(
                icon = AuraIcons.SkipNext,
                label = stringResource(R.string.play_next),
                onClick = onPlayNext,
            )
            AuraMenuRow(
                icon = AuraIcons.Queue,
                label = stringResource(R.string.add_to_queue),
                onClick = onAddToQueue,
            )
        }

        AuraMenuRow(
            icon = AuraIcons.PlaylistAdd,
            label = stringResource(R.string.add_to_playlist),
            onClick = onAddToPlaylist,
        )

        Spacer(Modifier.height(12.dp))
    }
}
