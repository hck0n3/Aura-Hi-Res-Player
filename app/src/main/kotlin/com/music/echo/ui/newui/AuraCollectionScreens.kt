package iad1tya.echo.music.ui.newui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.pulltorefresh.pullToRefresh
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.graphics.vector.ImageVector
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
import iad1tya.echo.music.LocalDownloadUtil
import iad1tya.echo.music.LocalPlayerAwareWindowInsets
import iad1tya.echo.music.LocalPlayerConnection
import iad1tya.echo.music.R
import iad1tya.echo.music.constants.MyTopFilter
import iad1tya.echo.music.constants.SongSortDescendingKey
import iad1tya.echo.music.constants.SongSortType
import iad1tya.echo.music.constants.SongSortTypeKey
import iad1tya.echo.music.db.entities.Song
import iad1tya.echo.music.extensions.toMediaItem
import iad1tya.echo.music.playback.ExoDownloadService
import iad1tya.echo.music.playback.queues.ListQueue
import iad1tya.echo.music.ui.component.DefaultDialog
import iad1tya.echo.music.ui.component.DraggableScrollbar
import iad1tya.echo.music.ui.component.EnhancedShuffleChip
import iad1tya.echo.music.ui.component.LocalMenuState
import iad1tya.echo.music.ui.component.rememberPlayedShuffleSet
import iad1tya.echo.music.ui.component.rememberShuffleMemoryPrompt
import iad1tya.echo.music.ui.menu.CachePlaylistMenu
import iad1tya.echo.music.ui.menu.SelectionSongMenu
import iad1tya.echo.music.ui.menu.SongMenu
import iad1tya.echo.music.ui.menu.TopPlaylistMenu
import iad1tya.echo.music.ui.utils.rememberIsTvOrCar
import iad1tya.echo.music.ui.utils.rememberIsWideLayout
import iad1tya.echo.music.ui.utils.tvFocusable
import iad1tya.echo.music.utils.makeTimeString
import iad1tya.echo.music.utils.rememberEnumPreference
import iad1tya.echo.music.utils.rememberPreference
import iad1tya.echo.music.viewmodels.CachePlaylistViewModel
import iad1tya.echo.music.viewmodels.TopPlaylistViewModel
import java.time.LocalDateTime
import java.util.Locale

/**
 * # Auto-colecciones — "Interfaz nueva"
 *
 * Three classic screens draw the SAME thing: a cover, a title, Aleatorio / Reproducir / ⋯, a sort
 * control and a flat list of [Song] rows with search, multi-select and a per-row ⋯.
 *
 *  · `auto_playlist/{playlist}` — Descargado / Me gusta / Subidas / Exportado ([AuraAutoPlaylistScreen]).
 *  · `cache_playlist/{playlist}` — En caché ([AuraCachePlaylistScreen]).
 *  · `top_playlist/{top}` — Mi Top N ([AuraTopPlaylistScreen]).
 *
 * The audit found the first one redesigned and the other two still classic, so inside ONE feature some
 * lists looked new and others old. They could not be collapsed into a single call of
 * [AuraAutoPlaylistScreen] with different arguments — each has its OWN ViewModel
 * ([iad1tya.echo.music.viewmodels.AutoPlaylistViewModel] / [CachePlaylistViewModel] /
 * [TopPlaylistViewModel]), its own sheet ([iad1tya.echo.music.ui.menu.AutoPlaylistMenu] /
 * [CachePlaylistMenu] / [TopPlaylistMenu]) and, in Mi Top's case, a "sort" that is really a PERIOD
 * filter with no direction. What they CAN share is the shape, so the shape lives here once
 * ([AuraSongCollectionScaffold]) and the three screens are thin bindings on top of it. One dialect,
 * three screens, one place to fix.
 *
 * ## Presentation only
 * Every list, order, filter and action below is the classic one: the same `hiltViewModel()`, the same
 * `"AP:"` shuffle-memory context ids (so toggling "Interfaz nueva" never hands the user a second
 * no-repeat memory), the same [SongMenu] / [SelectionSongMenu] / per-collection sheets, the same
 * `DownloadService` calls.
 */

// ── The shared shape ──────────────────────────────────────────────────────────────────────────────

/**
 * The body of an auto-collection screen.
 *
 * @param songs `null` while the collection is still loading — nothing is drawn, exactly as the classic
 *   screens do. An EMPTY list is a real "there is nothing here" and draws the empty state.
 * @param contextId the "Aleatorio mejorado" bucket. Always the classic `"AP:…"` id of the collection.
 * @param sortItem drawn as a list item above the rows; it receives the FILTERED list so it can print
 *   the visible count. Null for a collection with no sort control.
 * @param extraItems rows inserted between the header and the sort control (the Descargado screen's
 *   storage card and in-flight downloads). [extraItemsPresent] tells the empty state that those rows
 *   exist, so a collection with zero songs but an active download is not called empty.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AuraSongCollectionScaffold(
    title: String,
    songs: List<Song>?,
    contextId: String?,
    aboutTitleRes: Int,
    aboutText: String,
    onBack: () -> Unit,
    onHeaderMenu: () -> Unit,
    onSongMenu: (Song) -> Unit,
    modifier: Modifier = Modifier,
    queueTitle: String = title,
    coverUrl: String? = songs?.firstOrNull()?.song?.thumbnailUrl,
    showDownloadTick: Boolean = true,
    canRefresh: Boolean = false,
    isRefreshing: Boolean = false,
    onRefresh: () -> Unit = {},
    sortItem: (@Composable (List<Song>) -> Unit)? = null,
    extraItemsPresent: Boolean = false,
    extraItems: (LazyListScope.(isSearching: Boolean) -> Unit)? = null,
    /** Overrides the default "playlist is empty" copy (e.g. Exported videos CTA). */
    emptyText: String? = null,
    /** Use [R.plurals.n_video] in the header count (exported videos). */
    useVideoCount: Boolean = false,
    /** 16:9 header cover + full-width [AuraPosterCard] rows (exported videos). */
    videoPosterLayout: Boolean = false,
) {
    val menuState = LocalMenuState.current
    val haptic = LocalHapticFeedback.current
    val focusManager = LocalFocusManager.current
    val playerConnection = LocalPlayerConnection.current ?: return
    val isTvOrCar = rememberIsTvOrCar()

    val isPlaying by playerConnection.isEffectivelyPlaying.collectAsState()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsState()

    var isSearching by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(isSearching) {
        if (isSearching) focusRequester.requestFocus()
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

    if (isSearching) {
        BackHandler {
            isSearching = false
            query = ""
        }
    } else if (inSelectMode) {
        BackHandler(onBack = onExitSelectionMode)
    }

    val shufflePlayedSet = rememberPlayedShuffleSet(contextId)
    val likeLength = remember(songs) { songs?.sumOf { it.song.duration } ?: 0 }

    // Same filter the classic screens apply: title OR any artist name.
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
    val pullRefreshState = rememberPullToRefreshState()
    val bloom = rememberAuraBloom(mediaMetadata?.id)

    Box(
        modifier = modifier
            .fillMaxSize()
            .auraScreenBackground(bloom, intensity = 0.40f)
            .then(
                if (canRefresh) {
                    Modifier.pullToRefresh(
                        state = pullRefreshState,
                        isRefreshing = isRefreshing,
                        onRefresh = onRefresh,
                    )
                } else Modifier,
            ),
    ) {
        LazyColumn(
            state = listState,
            contentPadding = LocalPlayerAwareWindowInsets.current.asPaddingValues(),
            modifier = Modifier.fillMaxSize(),
        ) {
            // Still loading: nothing is drawn, exactly as the classic screens do. Bound to a local so
            // every nested lambda below reads the same non-null list without a smart cast.
            val list = songs ?: return@LazyColumn

            if (list.isEmpty() && !extraItemsPresent) {
                item(key = "aura_coll_empty") {
                    AuraEmpty(
                        text = emptyText ?: stringResource(R.string.playlist_is_empty),
                        modifier = Modifier.animateItem(),
                    )
                }
            }

            if (list.isNotEmpty() && !isSearching) {
                item(key = "aura_coll_header") {
                    AuraCollectionHeader(
                        name = title,
                        queueTitle = queueTitle,
                        songs = list,
                        likeLength = likeLength,
                        contextId = contextId,
                        coverUrl = coverUrl,
                        aboutTitleRes = aboutTitleRes,
                        aboutText = aboutText,
                        onMenu = onHeaderMenu,
                        onSearch = { isSearching = true },
                        useVideoCount = useVideoCount,
                        videoPosterLayout = videoPosterLayout,
                        modifier = Modifier.animateItem(),
                    )
                }
            }

            extraItems?.invoke(this, isSearching)

            if (list.isNotEmpty()) {
                if (isSearching) {
                    item(key = "aura_coll_search") {
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

                if (sortItem != null) {
                    item(key = "aura_coll_sort") { sortItem(filteredSongs) }
                }

                // Searching with no match is NOT an empty collection — the classic "En caché" screen is
                // the only one of the three that says so today; all three do now.
                if (isSearching && filteredSongs.isEmpty()) {
                    item(key = "aura_coll_no_results") {
                        AuraEmpty(
                            text = stringResource(R.string.no_results_found),
                            modifier = Modifier.animateItem(),
                        )
                    }
                }
            }

            itemsIndexed(
                items = filteredSongs,
                // Unique by construction: every source list is a set of distinct songs keyed by the
                // song's own primary key (most-played rows, a cache-key set, a DB relation).
                key = { _, song -> song.id },
            ) { _, song ->
                val selected = song.id in selection
                val onCheckedChange: (Boolean) -> Unit = { checked ->
                    if (checked) selection.add(song.id) else selection.remove(song.id)
                }
                val dimmed = song.id in shufflePlayedSet && song.id != mediaMetadata?.id
                val playFromFiltered = {
                        when {
                            inSelectMode -> onCheckedChange(!selected)
                            song.song.id == mediaMetadata?.id -> {
                                playerConnection.togglePlayPause()
                                if (videoPosterLayout) playerConnection.enterVideoModeIfNeeded(forceFromUserTap = true)
                            }
                            else -> {
                                playerConnection.playQueue(
                                    ListQueue(
                                        title = queueTitle,
                                        items = filteredSongs.map { it.toMediaItem() },
                                        startIndex = filteredSongs.indexOfFirst { it.id == song.id },
                                        contextId = contextId,
                                    ),
                                )
                                // Exported videos: always open in video mode; user can switch to audio later.
                                if (videoPosterLayout) playerConnection.enterVideoModeIfNeeded(forceFromUserTap = true)
                            }
                        }
                }

                if (videoPosterLayout) {
                    val videoLabel = auraTypeLabel(AuraContentKind.Video)
                    AuraPosterCard(
                        title = song.song.title,
                        subtitle = song.artists.joinToString { it.name }.takeIf { it.isNotBlank() },
                        thumbnailUrl = song.song.thumbnailUrl,
                        seed = song.id,
                        ratio = 16f / 9f,
                        shape = AuraShapes.Card,
                        isActive = song.id == mediaMetadata?.id,
                        isPlaying = song.id == mediaMetadata?.id && isPlaying,
                        typeIcon = AuraIcons.Video,
                        typeLabel = videoLabel,
                        contentDescription = song.song.title,
                        onClick = playFromFiltered,
                        onLongClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            if (inSelectMode) {
                                onCheckedChange(!selected)
                            } else {
                                onSongMenu(song)
                            }
                        },
                        modifier = Modifier
                            .animateItem()
                            .padding(horizontal = AuraSpacing.Gutter, vertical = 6.dp)
                            .tvFocusable(isTvOrCar, AuraShapes.Highlight, scaleFocused = 1f),
                    )
                } else {
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
                                        title = queueTitle,
                                        items = list.map { it.toMediaItem() },
                                        startIndex = list.indexOfFirst { it.id == song.id },
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
                                        contentDescription = stringResource(R.string.cd_shuffle_already_played),
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
                                if (showDownloadTick) {
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
                                        onClick = { onSongMenu(song) },
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

            item(key = "aura_coll_tail") { Spacer(Modifier.height(50.dp)) }
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
            AuraPullRefreshIndicator(
                state = pullRefreshState,
                isRefreshing = isRefreshing,
            )
        }

        // Sticky chrome: back only (owner: no floating title + lupa on liked/downloads/cache/top).
        // Search lives in the scrolling [AuraCollectionHeader], same as local/online playlists.
        AuraDetailTopBar(
            listState = listState,
            title = title,
            onBack = {
                when {
                    isSearching -> {
                        isSearching = false
                        query = ""
                        focusManager.clearFocus()
                    }
                    inSelectMode -> onExitSelectionMode()
                    else -> onBack()
                }
            },
            inSelectMode = inSelectMode,
            selectionCount = selection.size,
            // Never force the sticky title plate while searching — it covers the search field
            // and results (owner: New UI playlists / liked / downloads / cache / exported).
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
                    contentDescription = stringResource(R.string.cd_selection_actions),
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
            },
        )
    }
}

// ── Cabecera de la colección ──────────────────────────────────────────────────────────────────────

/**
 * Cover, title, Aleatorio / Reproducir / ⋯, the count line, the "Aleatorio mejorado" pill and the
 * "Acerca de" block — the classic headers' contents, in the new language.
 *
 * The shuffle button is the SAME [rememberShuffleMemoryPrompt] + unplayed-first ordering the classic
 * headers use; re-deriving it would have given these screens a shuffle that behaves differently from
 * the identical button one tab away.
 */
@Composable
internal fun AuraCollectionHeader(
    name: String,
    songs: List<Song>,
    likeLength: Int,
    contextId: String?,
    coverUrl: String?,
    aboutTitleRes: Int,
    aboutText: String,
    onMenu: () -> Unit,
    onSearch: () -> Unit,
    modifier: Modifier = Modifier,
    queueTitle: String = name,
    useVideoCount: Boolean = false,
    videoPosterLayout: Boolean = false,
) {
    val playerConnection = LocalPlayerConnection.current ?: return
    val isTvOrCar = rememberIsTvOrCar()
    val isWideLayout = rememberIsWideLayout()
    val countPlural = if (useVideoCount) R.plurals.n_video else R.plurals.n_song

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(modifier.height(56.dp))

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = if (videoPosterLayout) 20.dp else 48.dp),
            contentAlignment = Alignment.Center,
        ) {
            val coverWidth = when {
                videoPosterLayout -> maxWidth
                isWideLayout -> 320.dp
                else -> 260.dp
            }
            AuraCover(
                thumbnailUrl = coverUrl,
                size = coverWidth,
                seed = contextId ?: name,
                shape = if (videoPosterLayout) AuraShapes.Card else AuraShapes.PlayerArtwork,
                decodeTo = 512,
                ratio = if (videoPosterLayout) 16f / 9f else 1f,
                fillBleed = videoPosterLayout,
            )
        }

        Spacer(modifier.height(26.dp))

        Text(
            text = name,
            style = AuraType.ScreenTitle,
            color = AuraPalette.OnGround,
            maxLines = 2,
            overflow = AuraDefaultOverflow,
            modifier = Modifier.padding(horizontal = 32.dp),
        )

        Spacer(modifier.height(6.dp))

        Text(
            text = buildString {
                append(pluralStringResource(countPlural, songs.size, songs.size))
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

        // "Aleatorio mejorado · X/Y reproducidas" — feature-gated inside the shared chip, and inert
        // when this collection has no persistent memory bucket.
        if (contextId != null) {
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
                        title = queueTitle,
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
                            title = queueTitle,
                            items = songs.map { it.toMediaItem() },
                            contextId = contextId,
                        ),
                    )
                },
                accent = true,
                modifier = Modifier.weight(1f).tvFocusable(isTvOrCar, scaleFocused = 1f),
            )
        }

        Spacer(Modifier.height(12.dp))

        // Search · Más — in the scrolling header (owner: no sticky title + lupa plate).
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
                contentDescription = stringResource(R.string.cd_collection_more_options),
                onClick = onMenu,
                modifier = Modifier.tvFocusable(isTvOrCar, scaleFocused = 1f),
            )
        }

        Spacer(Modifier.height(18.dp))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AuraSpacing.Gutter),
        ) {
            AuraSectionLabel(text = stringResource(aboutTitleRes).uppercase(Locale.ROOT))
            Spacer(Modifier.height(6.dp))
            AuraExpandableText(text = aboutText, collapsedMaxLines = 3)
        }
    }
}

// ── Muebles compartidos ───────────────────────────────────────────────────────────────────────────

/**
 * The classic headers' "Acerca de" text with its Mostrar más / Mostrar menos toggle, in the new UI's
 * own colours.
 *
 * `ui/component/ExpandableText` is not reused here for one reason: it paints itself with
 * `MaterialTheme.colorScheme.onSurfaceVariant`, and these screens draw their own near-black ground
 * WITHOUT installing a Material theme. Same `R.string.show_more` / `R.string.show_less` labels, same
 * collapse behaviour.
 */
@Composable
internal fun AuraExpandableText(
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

/** Aleatorio / Reproducir / Guardar… as labeled pills. Prefer [AuraHeaderCircleButton] in collection
 *  headers (icon-only); keep this for full-width actions that still need a word (Añadir música, Reintentar). */
@Composable
internal fun AuraHeaderButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    accent: Boolean,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
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
            .auraClickableInternal(
                onClick = onClick,
                enabled = enabled,
                contentDescription = label,
            )
            .padding(horizontal = 16.dp),
    ) {
        val alpha = if (enabled) 1f else 0.4f
        AuraIconGlyph(
            icon = icon,
            contentDescription = null,
            size = 19.dp,
            tint = (if (accent) AuraPalette.OnAccent else AuraPalette.OnGround).copy(alpha = alpha),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = label,
            style = AuraType.Chip,
            color = (if (accent) AuraPalette.OnAccent else AuraPalette.OnGround).copy(alpha = alpha),
            maxLines = 1,
            overflow = AuraDefaultOverflow,
        )
    }
}

/** 48 dp circular header action — shuffle / play / save / download / search / ⋯ / share.
 *  [accent] = play gradient (the one full-colour control). Labels live only in [contentDescription]. */
@Composable
internal fun AuraHeaderCircleButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    accent: Boolean = false,
    enabled: Boolean = true,
) {
    val shape = CircleShape
    val alpha = if (enabled) 1f else 0.4f
    Box(
        modifier = modifier
            .size(48.dp)
            .clip(shape)
            .then(
                if (accent) {
                    Modifier.background(AuraPalette.PlayButtonGradient)
                } else {
                    Modifier
                        .background(AuraPalette.SurfaceFill)
                        .border(1.dp, AuraPalette.SurfaceLine, shape)
                },
            )
            .auraClickableInternal(
                onClick = onClick,
                enabled = enabled,
                contentDescription = contentDescription,
            ),
        contentAlignment = Alignment.Center,
    ) {
        AuraIconGlyph(
            icon = icon,
            contentDescription = null,
            size = 20.dp,
            tint = (if (accent) AuraPalette.OnAccent else AuraPalette.OnGround).copy(alpha = alpha),
        )
    }
}

/** The in-list search field. */
@Composable
internal fun AuraInlineSearchField(
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
                contentDescription = stringResource(R.string.search_clear_query),
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
 * controls `SortHeader` gives the classic screens, writing the same preference keys.
 *
 * @param showDescending mirrors `SortHeader`'s own flag: Mi Top's control is a PERIOD filter with no
 *   direction, so it hides the arrow instead of showing one that writes nothing.
 * @param trailingSlot drawn at the far right (the visible song count, the lock toggle).
 */
@Composable
internal fun <T : Enum<T>> AuraInlineSortControl(
    sortType: T,
    sortDescending: Boolean,
    options: List<Pair<T, Int>>,
    onSortTypeChange: (T) -> Unit,
    onSortDescendingChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    showDescending: Boolean = true,
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
                        contentDescription = stringResource(R.string.cd_change_sort_order),
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
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                shape = AuraShapes.Card,
                containerColor = AuraPalette.FrostFill,
            ) {
                options.forEach { (option, labelRes) ->
                    DropdownMenuItem(
                        text = {
                            Text(stringResource(labelRes), color = AuraPalette.OnGround)
                        },
                        onClick = {
                            onSortTypeChange(option)
                            expanded = false
                        },
                    )
                }
            }
        }
        if (showDescending) {
            AuraIconButton(
                icon = AuraIcons.ChevronDown,
                contentDescription = stringResource(R.string.cd_reverse_sort_order),
                onClick = { onSortDescendingChange(!sortDescending) },
                size = 16.dp,
                tint = AuraPalette.OnGroundFaint,
                modifier = Modifier.graphicsLayer { rotationZ = if (sortDescending) 0f else 180f },
            )
        }
        Box(Modifier.weight(1f))
        trailing?.invoke()
    }
}

/** "N CANCIONES" / "N VÍDEOS" at the right of a sort row. */
@Composable
internal fun AuraSongCountLabel(count: Int, useVideoCount: Boolean = false) {
    val plural = if (useVideoCount) R.plurals.n_video else R.plurals.n_song
    AuraTechnicalText(
        text = pluralStringResource(plural, count, count).uppercase(Locale.ROOT),
        color = AuraPalette.OnGroundGhost,
        modifier = Modifier.padding(end = 12.dp),
    )
}

// ── En caché ──────────────────────────────────────────────────────────────────────────────────────

/**
 * The redesigned `CachePlaylistScreen` (route `cache_playlist/{playlist}`).
 *
 * @param scrollBehavior accepted for signature parity with the classic screen; this shape draws its own
 *   header instead of a `TopAppBar`, so there is no collapsing bar to drive with it.
 */
@Suppress("UNUSED_PARAMETER")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuraCachePlaylistScreen(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
    viewModel: CachePlaylistViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val menuState = LocalMenuState.current
    val playerConnection = LocalPlayerConnection.current ?: return

    val cachedSongs by viewModel.cachedSongs.collectAsState()

    // The SAME "AP:" bucket the classic screen uses (vs real playlists' "PL:"), so this virtual id stays
    // out of the PL:%-orphan prune and its no-repeat memory survives every launch.
    val contextId = "AP:cached"
    val name = stringResource(R.string.cached_playlist)

    val (sortType, onSortTypeChange) = rememberEnumPreference(SongSortTypeKey, SongSortType.CREATE_DATE)
    val (sortDescending, onSortDescendingChange) = rememberPreference(SongSortDescendingKey, true)

    // The classic screen's ordering, term for term.
    val sortedSongs = remember(cachedSongs, sortType, sortDescending) {
        val sorted = when (sortType) {
            SongSortType.CREATE_DATE -> cachedSongs.sortedBy { it.song.dateDownload ?: LocalDateTime.MIN }
            SongSortType.NAME -> cachedSongs.sortedBy { it.song.title }
            SongSortType.ARTIST -> cachedSongs.sortedBy { song ->
                song.artists.joinToString(separator = "") { it.name }
            }
            SongSortType.PLAY_TIME -> cachedSongs.sortedBy { it.song.totalPlayTime }
        }
        if (sortDescending) sorted.reversed() else sorted
    }

    AuraSongCollectionScaffold(
        title = name,
        songs = sortedSongs,
        contextId = contextId,
        aboutTitleRes = R.string.about_album,
        aboutText = stringResource(R.string.aura_auto_playlist_about, name),
        onBack = { navController.navigateUp() },
        onHeaderMenu = {
            menuState.show {
                CachePlaylistMenu(
                    // The classic header hardcodes STATE_STOPPED here: nothing in "En caché" is
                    // downloaded by definition, so the sheet always offers "Descargar".
                    downloadState = Download.STATE_STOPPED,
                    onQueue = {
                        playerConnection.addToQueue(sortedSongs.map { it.toMediaItem() })
                    },
                    onDownload = {
                        sortedSongs.forEach { song ->
                            val request = DownloadRequest
                                .Builder(song.id, song.id.toUri())
                                .setCustomCacheKey(song.id)
                                .setData(song.title.toByteArray())
                                .build()
                            DownloadService.sendAddDownload(
                                context,
                                ExoDownloadService::class.java,
                                request,
                                false,
                            )
                        }
                    },
                    onDismiss = menuState::dismiss,
                )
            }
        },
        onSongMenu = { song ->
            menuState.show {
                SongMenu(
                    originalSong = song,
                    navController = navController,
                    onDismiss = menuState::dismiss,
                    // Carries "Quitar de la caché" — the one entry this screen's rows must have.
                    isFromCache = true,
                )
            }
        },
        sortItem = { filtered ->
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
                trailing = { AuraSongCountLabel(filtered.size) },
            )
        },
    )
}

// ── Mi Top N ──────────────────────────────────────────────────────────────────────────────────────

/**
 * The redesigned `TopPlaylistScreen` (route `top_playlist/{top}`).
 *
 * @param scrollBehavior accepted for signature parity with the classic screen; this shape draws its own
 *   header instead of a `TopAppBar`, so there is no collapsing bar to drive with it.
 */
@Suppress("UNUSED_PARAMETER")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuraTopPlaylistScreen(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
    viewModel: TopPlaylistViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val menuState = LocalMenuState.current
    val playerConnection = LocalPlayerConnection.current ?: return
    val downloadUtil = LocalDownloadUtil.current

    val maxSize = viewModel.top
    val songs by viewModel.topSongs.collectAsState(initial = null)
    val period by viewModel.topPeriod.collectAsState()
    val name = stringResource(R.string.my_top) + " $maxSize"

    // The period is PART of the id because changing it changes the song list, so the per-period
    // no-repeat memories must never mix — the classic scheme, unchanged.
    val contextId = "AP:top:" + period.name

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
                    text = stringResource(R.string.remove_download_playlist_confirm, name),
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

    val currentSongs = songs
    AuraSongCollectionScaffold(
        title = name,
        songs = currentSongs,
        contextId = contextId,
        aboutTitleRes = R.string.about_album,
        aboutText = stringResource(R.string.aura_auto_playlist_about, name),
        onBack = { navController.navigateUp() },
        onHeaderMenu = {
            menuState.show {
                TopPlaylistMenu(
                    downloadState = downloadState,
                    onQueue = {
                        playerConnection.addToQueue(currentSongs.orEmpty().map { it.toMediaItem() })
                    },
                    onDownload = {
                        when (downloadState) {
                            Download.STATE_COMPLETED -> showRemoveDownloadDialog = true
                            Download.STATE_DOWNLOADING -> currentSongs.orEmpty().forEach { song ->
                                DownloadService.sendRemoveDownload(
                                    context,
                                    ExoDownloadService::class.java,
                                    song.id,
                                    false,
                                )
                            }

                            else -> currentSongs.orEmpty().forEach { song ->
                                val request = DownloadRequest
                                    .Builder(song.id, song.id.toUri())
                                    .setCustomCacheKey(song.id)
                                    .setData(song.title.toByteArray())
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
        onSongMenu = { song ->
            menuState.show {
                SongMenu(
                    originalSong = song,
                    navController = navController,
                    onDismiss = menuState::dismiss,
                )
            }
        },
        sortItem = { filtered ->
            AuraInlineSortControl(
                sortType = period,
                sortDescending = false,
                options = listOf(
                    MyTopFilter.ALL_TIME to R.string.all_time,
                    MyTopFilter.DAY to R.string.past_24_hours,
                    MyTopFilter.WEEK to R.string.past_week,
                    MyTopFilter.MONTH to R.string.past_month,
                    MyTopFilter.YEAR to R.string.past_year,
                ),
                onSortTypeChange = { viewModel.topPeriod.value = it },
                onSortDescendingChange = {},
                // "Mi Top" is ranked by play count; there is no ascending/descending to offer, and the
                // classic screen hides the arrow for the same reason (`showDescending = false`).
                showDescending = false,
                trailing = { AuraSongCountLabel(filtered.size) },
            )
        },
    )
}
