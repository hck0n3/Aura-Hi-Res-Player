package iad1tya.echo.music.ui.newui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.flowOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import com.music.innertube.models.AlbumItem
import com.music.innertube.models.ArtistItem
import com.music.innertube.models.PlaylistItem
import com.music.innertube.models.SongItem
import com.music.innertube.models.YTItem
import iad1tya.echo.music.LocalDatabase
import iad1tya.echo.music.LocalPlayerAwareWindowInsets
import iad1tya.echo.music.LocalPlayerConnection
import iad1tya.echo.music.R
import iad1tya.echo.music.db.entities.Album
import iad1tya.echo.music.db.entities.Artist
import iad1tya.echo.music.db.entities.Playlist
import iad1tya.echo.music.db.entities.Song
import iad1tya.echo.music.extensions.toMediaItem
import iad1tya.echo.music.models.toMediaMetadata
import iad1tya.echo.music.playback.queues.ListQueue
import iad1tya.echo.music.playback.queues.YouTubeQueue
import iad1tya.echo.music.ui.component.LocalMenuState
import iad1tya.echo.music.ui.menu.SongMenu
import iad1tya.echo.music.ui.menu.YouTubeAlbumMenu
import iad1tya.echo.music.ui.menu.YouTubeArtistMenu
import iad1tya.echo.music.ui.menu.YouTubePlaylistMenu
import iad1tya.echo.music.ui.menu.YouTubeSongMenu
import iad1tya.echo.music.viewmodels.LocalFilter
import iad1tya.echo.music.viewmodels.LocalSearchViewModel
import iad1tya.echo.music.viewmodels.OnlineSearchSuggestionViewModel
import kotlinx.coroutines.flow.drop

/**
 * # The two panels behind the Buscar bar
 *
 * [AuraOnlineSearchSuggestions] replaces `OnlineSearchScreen` and [AuraLocalSearchResults] replaces
 * `LocalSearchScreen`. Both are pure presentation over the SAME view models the classic panels use —
 * the debounce, the link parsing, the explicit/video filtering and the database queries all stay where
 * they already are.
 */

// ── 1. En línea: historial + sugerencias + mejor resultado ─────────────────────────────────────────

/**
 * History (each row deletable), YouTube's own query suggestions, and the "mejor resultado" items.
 *
 * Every control of the classic panel survives:
 *  · tap a row → run that search,
 *  · ✕ on a history row → delete that single entry from the database,
 *  · ↖ on any row → copy it into the field instead of running it,
 *  · tap a result → play / open it, long-press or ⋯ → its full YouTube menu.
 */
@Composable
fun AuraOnlineSearchSuggestions(
    query: String,
    onQueryChange: (TextFieldValue) -> Unit,
    navController: NavController,
    onSearch: (String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: OnlineSearchSuggestionViewModel = hiltViewModel(),
) {
    val database = LocalDatabase.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val menuState = LocalMenuState.current
    val haptic = LocalHapticFeedback.current
    val playerConnection = LocalPlayerConnection.current ?: return
    val scope = rememberCoroutineScope()

    val isPlaying by playerConnection.isEffectivelyPlaying.collectAsState()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsState()
    val viewState by viewModel.viewState.collectAsState()

    val lazyListState = rememberLazyListState()

    // Hide the keyboard on a manual scroll (so the user can see more results), but NOT when the
    // offset shifts because new suggestions streamed in and reflowed the list — `isScrollInProgress`
    // is true only for a real touch-driven drag/fling, never for a content-driven relayout, so this
    // is what stopped the keyboard from disappearing mid-type as debounced results arrived.
    LaunchedEffect(Unit) {
        snapshotFlow { lazyListState.firstVisibleItemScrollOffset }
            .drop(1)
            .collect { if (lazyListState.isScrollInProgress) keyboardController?.hide() }
    }

    // The view model owns the debounce; the panel only hands it the current text.
    LaunchedEffect(query) { viewModel.query.value = query }

    val openMenu: (YTItem) -> Unit = { item ->
        menuState.show {
            val dismiss = {
                menuState.dismiss()
                onDismiss()
            }
            when (item) {
                is SongItem -> YouTubeSongMenu(
                    song = item,
                    navController = navController,
                    onDismiss = dismiss,
                )

                is AlbumItem -> YouTubeAlbumMenu(
                    albumItem = item,
                    navController = navController,
                    onDismiss = dismiss,
                )

                is ArtistItem -> YouTubeArtistMenu(artist = item, onDismiss = dismiss)

                is PlaylistItem -> YouTubePlaylistMenu(
                    playlist = item,
                    coroutineScope = scope,
                    onDismiss = dismiss,
                )
            }
        }
    }

    LazyColumn(
        state = lazyListState,
        contentPadding = LocalPlayerAwareWindowInsets.current
            .only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal)
            .asPaddingValues(),
        // Never steal focus from the search field while suggestions recompose mid-type.
        modifier = modifier
            .fillMaxSize()
            .focusProperties { canFocus = false },
    ) {
        if (viewState.history.isNotEmpty()) {
            item(key = "aura_history_header") {
                AuraSectionHeader(
                    title = stringResource(R.string.search_history),
                    modifier = Modifier.animateItem(),
                )
            }
        }

        items(viewState.history, key = { "aura_history_${it.query}" }) { history ->
            AuraQueryRow(
                query = history.query,
                fromHistory = true,
                onClick = { onSearch(history.query) },
                onDelete = { database.query { delete(history) } },
                onFillTextField = {
                    onQueryChange(
                        TextFieldValue(history.query, TextRange(history.query.length)),
                    )
                },
                modifier = Modifier.animateItem(),
            )
        }

        if (viewState.suggestions.isNotEmpty()) {
            item(key = "aura_suggestions_header") {
                AuraSectionHeader(
                    title = stringResource(R.string.suggestions),
                    modifier = Modifier.animateItem(),
                )
            }
        }

        items(viewState.suggestions, key = { "aura_suggestion_$it" }) { suggestion ->
            AuraQueryRow(
                query = suggestion,
                fromHistory = false,
                onClick = { onSearch(suggestion) },
                onDelete = null,
                onFillTextField = {
                    onQueryChange(TextFieldValue(suggestion, TextRange(suggestion.length)))
                },
                modifier = Modifier.animateItem(),
            )
        }

        if (viewState.items.isNotEmpty()) {
            item(key = "aura_top_result_header") {
                AuraSectionHeader(
                    title = stringResource(
                        if (viewState.isFromLink) R.string.parsed_from_link else R.string.top_result
                    ),
                    accent = AuraPalette.Teal,
                    modifier = Modifier.animateItem(),
                )
            }
        }

        items(viewState.items, key = { "aura_item_${it.id}" }) { item ->
            AuraYtItemRow(
                item = item,
                isActive = when (item) {
                    is SongItem -> mediaMetadata?.id == item.id
                    is AlbumItem -> mediaMetadata?.album?.id == item.id
                    else -> false
                },
                isPlaying = isPlaying,
                onClick = {
                    when (item) {
                        is SongItem -> if (item.id == mediaMetadata?.id) {
                            playerConnection.togglePlayPause()
                        } else {
                            playerConnection.playQueue(
                                YouTubeQueue.radio(item.toMediaMetadata()),
                            )
                            onDismiss()
                        }

                        is AlbumItem -> {
                            navController.navigate("album/${item.id}")
                            onDismiss()
                        }

                        is ArtistItem -> {
                            navController.navigate("artist/${item.id}")
                            onDismiss()
                        }

                        is PlaylistItem -> {
                            navController.navigate("online_playlist/${item.id}")
                            onDismiss()
                        }
                    }
                },
                onLongClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    openMenu(item)
                },
                onMenuClick = { openMenu(item) },
                modifier = Modifier.animateItem(),
            )
        }

        item(key = "aura_suggestions_bottom_spacer") {
            Spacer(Modifier.height(24.dp))
        }
    }
}

/**
 * One suggestion row: a leading history/search glyph, the query, and the two trailing affordances the
 * classic `SuggestionItem` has — delete (history only) and "copy into the field".
 */
@Composable
private fun AuraQueryRow(
    query: String,
    fromHistory: Boolean,
    onClick: () -> Unit,
    onDelete: (() -> Unit)?,
    onFillTextField: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AuraRow(
        title = query,
        onClick = onClick,
        contentDescription = query,
        leading = {
            AuraIconGlyph(
                icon = if (fromHistory) AuraIcons.History else AuraIcons.Search,
                contentDescription = null,
                size = 17.dp,
                tint = AuraPalette.OnGroundDisabled,
            )
        },
        trailing = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                if (onDelete != null) {
                    AuraIconButton(
                        // The render's close glyph is a "+" turned 45°.
                        icon = AuraIcons.Plus,
                        contentDescription = stringResource(R.string.search_history_remove),
                        onClick = onDelete,
                        size = 15.dp,
                        tint = AuraPalette.OnGroundDisabled,
                        modifier = Modifier.graphicsLayer { rotationZ = 45f },
                    )
                }
                AuraIconButton(
                    // A chevron turned 135° anticlockwise points up-and-left: the classic "↖".
                    icon = AuraIcons.ChevronRight,
                    contentDescription = stringResource(R.string.search_use_suggestion),
                    onClick = onFillTextField,
                    size = 16.dp,
                    tint = AuraPalette.OnGroundDisabled,
                    modifier = Modifier.graphicsLayer { rotationZ = -135f },
                )
            }
        },
        modifier = modifier.padding(horizontal = AuraSpacing.Gutter),
    )
}

/** A YouTube search result as an Aura row: typed thumb (circle / 16:9 / release), title, subtitle. */
@Composable
internal fun AuraYtItemRow(
    item: YTItem,
    isActive: Boolean,
    isPlaying: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onMenuClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val database = LocalDatabase.current
    val songFlow = remember(item.id) {
        if (item is SongItem) database.song(item.id) else flowOf(null)
    }
    val dbSong by songFlow.collectAsState(initial = null)
    val alreadyPlayed = item is SongItem && (dbSong?.song?.totalPlayTime ?: 0L) > 0L
    val visual = auraTypeVisual(item)

    AuraSongRow(
        title = item.title,
        subtitle = auraSearchYtSubtitle(item),
        thumbnailUrl = item.thumbnail,
        seed = item.id,
        isActive = isActive,
        isPlaying = isPlaying,
        explicit = item.explicit,
        playedInShuffle = alreadyPlayed,
        artworkShape = visual.shape,
        artworkRatio = visual.ratio,
        artworkSize = visual.rowWidth,
        typeChip = visual.label.takeIf {
            visual.kind != AuraContentKind.Song && visual.kind != AuraContentKind.Album
        },
        onClick = onClick,
        onLongClick = onLongClick,
        onMenuClick = onMenuClick,
        menuContentDescription = item.title,
        modifier = modifier.padding(horizontal = AuraSpacing.Gutter),
    )
}

/** Owner-facing type label: canción / vídeo / álbum / EP / Single / playlist / artista. */
internal fun auraYtTypeLabel(item: YTItem): String = auraTypeLabel(auraContentKind(item))

internal fun auraLooksLikeEp(item: AlbumItem): Boolean {
    val t = item.title.trim()
    val d = item.description.orEmpty()
    return t.contains(Regex("""(?i)(^|[^\w])EP([^\w]|$)""")) ||
        t.endsWith(" - EP", ignoreCase = true) ||
        d.contains(Regex("""(?i)\bEP\b"""))
}

/** The subtitle the classic `YouTubeListItem` builds for each result type, plus a type prefix. */
@Composable
internal fun auraSearchYtSubtitle(item: YTItem): String? {
    val type = auraYtTypeLabel(item)
    val detail = when (item) {
        is SongItem -> item.artists.joinToString { it.name }.takeIf { it.isNotBlank() }

        is AlbumItem -> listOfNotNull(
            item.artists?.joinToString { it.name }?.takeIf { it.isNotBlank() },
            item.year?.toString(),
        ).joinToString(" · ").takeIf { it.isNotBlank() }

        is ArtistItem -> null

        is PlaylistItem -> listOfNotNull(
            item.author?.name,
            item.songCountText,
        ).joinToString(" · ").takeIf { it.isNotBlank() }

        else -> null
    }
    return when {
        type.isBlank() -> detail
        detail.isNullOrBlank() -> type
        else -> "$type · $detail"
    }
}

// ── 2. Biblioteca: resultados locales ──────────────────────────────────────────────────────────────

/**
 * The library panel: the five filter chips, then live database results. Same `LocalSearchViewModel`,
 * same three-per-section preview under "Todo", same `queue_searched_songs` queue title, and the same
 * section rows that jump straight to a single filter.
 */
@Composable
fun AuraLocalSearchResults(
    query: String,
    navController: NavController,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    isFromCache: Boolean = false,
    viewModel: LocalSearchViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val menuState = LocalMenuState.current
    val haptic = LocalHapticFeedback.current
    val playerConnection = LocalPlayerConnection.current ?: return

    val isPlaying by playerConnection.isEffectivelyPlaying.collectAsState()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsState()

    val searchFilter by viewModel.filter.collectAsState()
    val result by viewModel.result.collectAsState()

    val lazyListState = rememberLazyListState()

    // See AuraOnlineSearchSuggestions above for why this is gated on isScrollInProgress: without it,
    // a content-driven reflow (local results arriving as the user types) could hide the keyboard too.
    LaunchedEffect(Unit) {
        snapshotFlow { lazyListState.firstVisibleItemScrollOffset }
            .drop(1)
            .collect { if (lazyListState.isScrollInProgress) keyboardController?.hide() }
    }

    LaunchedEffect(query) { viewModel.query.value = query }

    LazyColumn(
        state = lazyListState,
        contentPadding = LocalPlayerAwareWindowInsets.current
            .only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal)
            .asPaddingValues(),
        modifier = modifier
            .fillMaxSize()
            .focusProperties { canFocus = false },
    ) {
        item(key = "aura_local_filters") {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = AuraSpacing.Gutter, vertical = 2.dp),
            ) {
                auraLocalFilters().forEach { (filter, labelRes) ->
                    AuraChip(
                        text = stringResource(labelRes),
                        selected = searchFilter == filter,
                        onClick = { viewModel.filter.value = filter },
                    )
                }
            }
        }

        result.map.forEach { (filter, items) ->
            if (result.filter == LocalFilter.ALL) {
                item(key = "aura_local_section_${filter.name}") {
                    AuraSectionHeader(
                        title = stringResource(auraLocalFilterLabel(filter)),
                        onClick = { viewModel.filter.value = filter },
                        modifier = Modifier.animateItem(),
                    )
                }
            }

            items(
                items = items.distinctBy { it.id },
                key = { "aura_local_${filter.name}_${it.id}" },
            ) { item ->
                when (item) {
                    is Song -> AuraSongRow(
                        title = item.song.title,
                        subtitle = item.artists.joinToString { it.name },
                        thumbnailUrl = item.song.thumbnailUrl,
                        seed = item.id,
                        isActive = item.id == mediaMetadata?.id,
                        isPlaying = isPlaying,
                        liked = item.song.liked,
                        explicit = item.song.explicit,
                        inLibrary = item.song.inLibrary != null,
                        downloadId = item.id,
                        format = item.format,
                        swipeMediaItem = item.toMediaItem(),
                        onClick = {
                            if (item.id == mediaMetadata?.id) {
                                playerConnection.togglePlayPause()
                            } else {
                                val songs = result.map
                                    .getOrDefault(LocalFilter.SONG, emptyList())
                                    .filterIsInstance<Song>()
                                    .map { it.toMediaItem() }
                                playerConnection.playQueue(
                                    ListQueue(
                                        title = context.getString(R.string.queue_searched_songs),
                                        items = songs,
                                        startIndex = songs.indexOfFirst { it.mediaId == item.id },
                                    ),
                                )
                            }
                        },
                        onLongClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            menuState.show {
                                SongMenu(
                                    originalSong = item,
                                    navController = navController,
                                    onDismiss = {
                                        onDismiss()
                                        menuState.dismiss()
                                    },
                                    isFromCache = isFromCache,
                                )
                            }
                        },
                        onMenuClick = {
                            menuState.show {
                                SongMenu(
                                    originalSong = item,
                                    navController = navController,
                                    onDismiss = {
                                        onDismiss()
                                        menuState.dismiss()
                                    },
                                    isFromCache = isFromCache,
                                )
                            }
                        },
                        modifier = Modifier
                            .animateItem()
                            .padding(horizontal = AuraSpacing.Gutter),
                    )

                    is Album -> {
                        val visual = auraTypeVisual(AuraContentKind.Album)
                        AuraSongRow(
                        title = item.album.title,
                        subtitle = "${visual.label} · ${item.artists.joinToString { it.name }}",
                        thumbnailUrl = item.album.thumbnailUrl,
                        seed = item.id,
                        isActive = item.id == mediaMetadata?.album?.id,
                        isPlaying = isPlaying,
                        artworkShape = visual.shape,
                        artworkSize = visual.rowWidth,
                        typeChip = visual.label,
                        onClick = {
                            onDismiss()
                            navController.navigate("album/${item.id}")
                        },
                        modifier = Modifier
                            .animateItem()
                            .padding(horizontal = AuraSpacing.Gutter),
                    )
                    }

                    is Artist -> {
                        val visual = auraTypeVisual(AuraContentKind.Artist)
                        AuraSongRow(
                        title = item.artist.name,
                        subtitle = pluralStringResource(
                            R.plurals.n_song,
                            item.songCount,
                            item.songCount,
                        ),
                        thumbnailUrl = item.artist.thumbnailUrl,
                        seed = item.id,
                        artworkShape = visual.shape,
                        artworkSize = visual.rowWidth,
                        typeChip = visual.label,
                        onClick = {
                            onDismiss()
                            navController.navigate("artist/${item.id}")
                        },
                        modifier = Modifier
                            .animateItem()
                            .padding(horizontal = AuraSpacing.Gutter),
                    )
                    }

                    is Playlist -> {
                        val visual = auraTypeVisual(AuraContentKind.Playlist)
                        AuraSongRow(
                        title = item.playlist.name,
                        subtitle = "${visual.label} · " + pluralStringResource(
                            R.plurals.n_song,
                            item.songCount,
                            item.songCount,
                        ),
                        thumbnailUrl = item.thumbnails.firstOrNull(),
                        seed = item.id,
                        artworkShape = visual.shape,
                        artworkSize = visual.rowWidth,
                        typeChip = visual.label,
                        onClick = {
                            onDismiss()
                            navController.navigate("local_playlist/${item.id}")
                        },
                        modifier = Modifier
                            .animateItem()
                            .padding(horizontal = AuraSpacing.Gutter),
                    )
                    }

                    else -> Unit
                }
            }
        }

        if (result.query.isNotEmpty() && result.map.isEmpty()) {
            item(key = "aura_local_no_result") {
                AuraEmpty(text = stringResource(R.string.no_results_found))
            }
        }

        item(key = "aura_local_bottom_spacer") {
            Spacer(Modifier.height(24.dp))
        }
    }
}

/** The five library filters, in the classic order. */
private fun auraLocalFilters(): List<Pair<LocalFilter, Int>> = listOf(
    LocalFilter.ALL to R.string.filter_all,
    LocalFilter.SONG to R.string.filter_songs,
    LocalFilter.ALBUM to R.string.filter_albums,
    LocalFilter.ARTIST to R.string.filter_artists,
    LocalFilter.PLAYLIST to R.string.filter_playlists,
)

private fun auraLocalFilterLabel(filter: LocalFilter): Int = when (filter) {
    LocalFilter.SONG -> R.string.filter_songs
    LocalFilter.ALBUM -> R.string.filter_albums
    LocalFilter.ARTIST -> R.string.filter_artists
    LocalFilter.PLAYLIST -> R.string.filter_playlists
    LocalFilter.ALL -> R.string.filter_all
}
