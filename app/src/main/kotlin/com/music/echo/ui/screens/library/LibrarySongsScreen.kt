

package iad1tya.echo.music.ui.screens.library

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState
import iad1tya.echo.music.LocalPlayerAwareWindowInsets
import iad1tya.echo.music.ui.utils.tvFocusRestorer
import iad1tya.echo.music.LocalPlayerConnection
import iad1tya.echo.music.R
import iad1tya.echo.music.constants.CONTENT_TYPE_HEADER
import iad1tya.echo.music.constants.CONTENT_TYPE_SONG
import iad1tya.echo.music.constants.GridItemSize
import iad1tya.echo.music.constants.GridItemsSizeKey
import iad1tya.echo.music.constants.GridThumbnailHeight
import iad1tya.echo.music.constants.HideExplicitKey
import iad1tya.echo.music.constants.LibraryViewType
import iad1tya.echo.music.constants.SongFilter
import iad1tya.echo.music.constants.SongFilterKey
import iad1tya.echo.music.constants.SongSortDescendingKey
import iad1tya.echo.music.constants.SongSortType
import iad1tya.echo.music.constants.SongSortTypeKey
import iad1tya.echo.music.constants.SongViewTypeKey
import iad1tya.echo.music.constants.YtmSyncKey
import iad1tya.echo.music.extensions.toMediaItem
import iad1tya.echo.music.playback.queues.ListQueue
import iad1tya.echo.music.ui.component.ChipsRow
import iad1tya.echo.music.ui.component.HideOnScrollFAB
import iad1tya.echo.music.ui.component.LibraryViewToggle
import iad1tya.echo.music.ui.component.LocalMenuState
import iad1tya.echo.music.ui.component.SongGridItem
import iad1tya.echo.music.ui.component.SongListItem
import iad1tya.echo.music.ui.component.SortHeader
import iad1tya.echo.music.ui.menu.SongMenu
import iad1tya.echo.music.utils.listItemShape
import iad1tya.echo.music.utils.rememberEnumPreference
import iad1tya.echo.music.utils.rememberPreference
import iad1tya.echo.music.viewmodels.LibrarySongsViewModel

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun LibrarySongsScreen(
    navController: NavController,
    onDeselect: () -> Unit,
    viewModel: LibrarySongsViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val menuState = LocalMenuState.current
    val haptic = LocalHapticFeedback.current
    val playerConnection = LocalPlayerConnection.current ?: return
    val isPlaying by playerConnection.isEffectivelyPlaying.collectAsState()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsState()

    var viewType by rememberEnumPreference(SongViewTypeKey, LibraryViewType.LIST)

    val (sortType, onSortTypeChange) = rememberEnumPreference(
        SongSortTypeKey,
        SongSortType.CREATE_DATE
    )
    val (sortDescending, onSortDescendingChange) = rememberPreference(SongSortDescendingKey, true)
    val gridItemSize by rememberEnumPreference(GridItemsSizeKey, GridItemSize.BIG)

    val (ytmSync) = rememberPreference(YtmSyncKey, true)
    val hideExplicit by rememberPreference(key = HideExplicitKey, defaultValue = false)

    val songs by viewModel.allSongs.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()

    var filter by rememberEnumPreference(SongFilterKey, SongFilter.LIKED)

    LaunchedEffect(Unit) {
        if (ytmSync) {
            when (filter) {
                SongFilter.LIKED -> viewModel.syncLikedSongs()
                SongFilter.LIBRARY -> viewModel.syncLibrarySongs()
                SongFilter.UPLOADED -> viewModel.syncUploadedSongs()
                else -> return@LaunchedEffect
            }
        }
    }

    val lazyListState = rememberLazyListState()
    val lazyGridState = rememberLazyGridState()

    val backStackEntry by navController.currentBackStackEntryAsState()
    val scrollToTop =
        backStackEntry?.savedStateHandle?.getStateFlow("scrollToTop", false)?.collectAsState()

    LaunchedEffect(scrollToTop?.value) {
        if (scrollToTop?.value == true) {
            when (viewType) {
                LibraryViewType.LIST -> lazyListState.animateScrollToItem(0)
                LibraryViewType.GRID -> lazyGridState.animateScrollToItem(0)
            }
            backStackEntry?.savedStateHandle?.set("scrollToTop", false)
        }
    }

    val filteredSongs = songs
        .let { list -> if (hideExplicit) list.filter { !it.song.explicit } else list }
        .let { list ->
            if (searchQuery.isBlank()) list
            else list.filter { it.song.title.contains(searchQuery, ignoreCase = true) }
        }

    // Enhanced Shuffle context. Each library TAB (Liked / Library / Uploaded) is a distinct collection, so
    // each gets its own persistent no-repeat bucket ("LIB:<filter>"). A search-/explicit-filtered SUBSET must
    // NOT share a tab's bucket — exhausting a 4-song search result would otherwise clear the tab's whole
    // accumulated memory — so filtered subsets fall back to null = classic in-memory shuffle.
    val libraryContextId = if (searchQuery.isBlank() && !hideExplicit) "LIB:${filter.name}" else null

    // Header sub-blocks are hoisted so the LIST (LazyColumn) and GRID (LazyVerticalGrid) branches
    // render byte-identical filter/search/sort headers — the only difference is the item layout.
    val filterContent = @Composable {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Spacer(Modifier.width(12.dp))
            FilterChip(
                label = { Text(stringResource(R.string.songs)) },
                selected = true,
                colors = FilterChipDefaults.filterChipColors(containerColor = MaterialTheme.colorScheme.surface),
                onClick = onDeselect,
                shape = RoundedCornerShape(16.dp),
                leadingIcon = {
                    Icon(
                        painter = painterResource(R.drawable.close),
                        contentDescription = ""
                    )
                },
                modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
            )
            ChipsRow(
                chips =
                listOf(
                    SongFilter.LIKED to stringResource(R.string.filter_liked),
                    SongFilter.LIBRARY to stringResource(R.string.filter_library),
                    SongFilter.UPLOADED to stringResource(R.string.filter_uploaded),
                    SongFilter.DOWNLOADED to stringResource(R.string.filter_downloaded),
                    SongFilter.EXPORTED to stringResource(R.string.action_exported),
                ),
                currentValue = filter,
                onValueUpdate = {
                    filter = it
                },
                modifier = Modifier.weight(1f),
            )
        }
    }

    val searchContent = @Composable {
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { viewModel.searchQuery.value = it },
            singleLine = true,
            leadingIcon = {
                Icon(painter = painterResource(R.drawable.search), contentDescription = null)
            },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { viewModel.searchQuery.value = "" }) {
                        Icon(painter = painterResource(R.drawable.close), contentDescription = null)
                    }
                }
            },
            placeholder = { Text(stringResource(R.string.search_library)) },
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp),
        )
    }

    val headerContent = @Composable {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 16.dp),
        ) {
            SortHeader(
                sortType = sortType,
                sortDescending = sortDescending,
                onSortTypeChange = onSortTypeChange,
                onSortDescendingChange = onSortDescendingChange,
                sortTypeText = { sortType ->
                    when (sortType) {
                        SongSortType.CREATE_DATE -> R.string.sort_by_create_date
                        SongSortType.NAME -> R.string.sort_by_name
                        SongSortType.ARTIST -> R.string.sort_by_artist
                        SongSortType.PLAY_TIME -> R.string.sort_by_play_time
                    }
                },
            )

            Spacer(Modifier.weight(1f))

            Text(
                text = pluralStringResource(
                    R.plurals.n_song,
                    filteredSongs.size,
                    filteredSongs.size
                ),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.secondary,
            )

            LibraryViewToggle(
                viewType = viewType,
                onViewTypeChange = { viewType = it },
            )
        }
    }

    BoxWithConstraints(
        modifier = Modifier.fillMaxSize(),
    ) {
        when (viewType) {
            LibraryViewType.LIST ->
                LazyColumn(
                    state = lazyListState,
                    contentPadding = LocalPlayerAwareWindowInsets.current.asPaddingValues(),
                    // TV/car: keep the D-pad focus ring alive when scrolling to not-yet-composed rows. No-op on phone.
                    modifier = Modifier.tvFocusRestorer(),
                ) {
                    item(
                        key = "filter",
                        contentType = CONTENT_TYPE_HEADER,
                    ) {
                        filterContent()
                    }

                    item(
                        key = "song_search",
                        contentType = CONTENT_TYPE_HEADER,
                    ) {
                        searchContent()
                    }

                    item(
                        key = "header",
                        contentType = CONTENT_TYPE_HEADER,
                    ) {
                        headerContent()
                    }

                    itemsIndexed(
                        items = filteredSongs,
                        key = { _, item -> item.song.id },
                        contentType = { _, _ -> CONTENT_TYPE_SONG },
                    ) { index, song ->
                        SongListItem(
                            song = song,
                            showInLibraryIcon = true,
                            isActive = song.id == mediaMetadata?.id,
                            isPlaying = isPlaying,
                            showLikedIcon = true,
                            showDownloadIcon = filter != SongFilter.DOWNLOADED,
                            showSize = filter == SongFilter.DOWNLOADED,
                            shape = listItemShape(index, filteredSongs.size),
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
                                    },
                                ) {
                                    Icon(
                                        painter = painterResource(R.drawable.more_vert),
                                        contentDescription = null,
                                    )
                                }
                            },
                            modifier =
                            Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (song.id == mediaMetadata?.id) {
                                        playerConnection.togglePlayPause()
                                    } else {
                                        playerConnection.playQueue(
                                            ListQueue(
                                                title = context.getString(R.string.queue_all_songs),
                                                items = filteredSongs.map { it.toMediaItem() },
                                                startIndex = index,
                                                contextId = libraryContextId,
                                            ),
                                        )
                                    }
                                }
                                .animateItem(),
                        )
                    }
                }

            LibraryViewType.GRID ->
                LazyVerticalGrid(
                    state = lazyGridState,
                    columns =
                    GridCells.Adaptive(
                        // Reflow to the REAL panel width (split-screen aware), NOT the window. Only a
                        // genuinely wide content pane (>= 840dp, the expanded breakpoint) uses the compact
                        // 110dp cell = more, smaller cards; a narrow split pane keeps phone-size cards
                        // instead of inheriting the wide window's compact sizing.
                        minSize = if (maxWidth >= 840.dp) 110.dp
                        else GridThumbnailHeight + if (gridItemSize == GridItemSize.BIG) 24.dp else (-24).dp,
                    ),
                    contentPadding = LocalPlayerAwareWindowInsets.current.asPaddingValues(),
                    modifier = Modifier.tvFocusRestorer(),
                ) {
                    item(
                        key = "filter",
                        span = { GridItemSpan(maxLineSpan) },
                        contentType = CONTENT_TYPE_HEADER,
                    ) {
                        filterContent()
                    }

                    item(
                        key = "song_search",
                        span = { GridItemSpan(maxLineSpan) },
                        contentType = CONTENT_TYPE_HEADER,
                    ) {
                        searchContent()
                    }

                    item(
                        key = "header",
                        span = { GridItemSpan(maxLineSpan) },
                        contentType = CONTENT_TYPE_HEADER,
                    ) {
                        headerContent()
                    }

                    itemsIndexed(
                        items = filteredSongs,
                        key = { _, item -> item.song.id },
                        contentType = { _, _ -> CONTENT_TYPE_SONG },
                    ) { index, song ->
                        SongGridItem(
                            song = song,
                            showInLibraryIcon = true,
                            isActive = song.id == mediaMetadata?.id,
                            isPlaying = isPlaying,
                            showLikedIcon = true,
                            showDownloadIcon = filter != SongFilter.DOWNLOADED,
                            modifier = Modifier
                                .fillMaxWidth()
                                .combinedClickable(
                                    onClick = {
                                        if (song.id == mediaMetadata?.id) {
                                            playerConnection.togglePlayPause()
                                        } else {
                                            playerConnection.playQueue(
                                                ListQueue(
                                                    title = context.getString(R.string.queue_all_songs),
                                                    items = filteredSongs.map { it.toMediaItem() },
                                                    startIndex = index,
                                                    contextId = libraryContextId,
                                                ),
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
                                )
                                .animateItem(),
                        )
                    }
                }
        }

        when (viewType) {
            LibraryViewType.LIST ->
                HideOnScrollFAB(
                    visible = filteredSongs.isNotEmpty(),
                    lazyListState = lazyListState,
                    icon = R.drawable.shuffle,
                    onClick = {
                        playerConnection.playQueue(
                            ListQueue(
                                title = context.getString(R.string.queue_all_songs),
                                items = filteredSongs.shuffled().map { it.toMediaItem() },
                                contextId = libraryContextId,
                            ),
                        )
                    },
                )

            LibraryViewType.GRID ->
                HideOnScrollFAB(
                    visible = filteredSongs.isNotEmpty(),
                    lazyListState = lazyGridState,
                    icon = R.drawable.shuffle,
                    onClick = {
                        playerConnection.playQueue(
                            ListQueue(
                                title = context.getString(R.string.queue_all_songs),
                                items = filteredSongs.shuffled().map { it.toMediaItem() },
                                contextId = libraryContextId,
                            ),
                        )
                    },
                )
        }
    }
}
