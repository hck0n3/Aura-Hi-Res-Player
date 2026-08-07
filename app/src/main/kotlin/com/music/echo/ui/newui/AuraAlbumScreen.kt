package iad1tya.echo.music.ui.newui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.media3.exoplayer.offline.Download
import androidx.navigation.NavController
import iad1tya.echo.music.LocalDatabase
import iad1tya.echo.music.LocalDownloadUtil
import iad1tya.echo.music.LocalPlayerAwareWindowInsets
import iad1tya.echo.music.LocalPlayerConnection
import iad1tya.echo.music.R
import iad1tya.echo.music.constants.AlbumCanvasEnabledKey
import iad1tya.echo.music.constants.DataSaverEnabledKey
import iad1tya.echo.music.constants.HideExplicitKey
import iad1tya.echo.music.constants.HideVideoSongsKey
import iad1tya.echo.music.db.entities.Album
import iad1tya.echo.music.playback.queues.LocalAlbumRadio
import iad1tya.echo.music.ui.component.EnhancedShuffleChip
import iad1tya.echo.music.ui.component.LinkSegment
import iad1tya.echo.music.ui.component.LocalMenuState
import iad1tya.echo.music.ui.component.rememberPlayedShuffleSet
import iad1tya.echo.music.ui.component.rememberShuffleMemoryPrompt
import iad1tya.echo.music.ui.component.shimmer.ShimmerHost
import iad1tya.echo.music.ui.menu.AlbumMenu
import iad1tya.echo.music.ui.menu.SelectionSongMenu
import iad1tya.echo.music.ui.menu.SongMenu
import iad1tya.echo.music.ui.menu.YouTubeAlbumMenu
import iad1tya.echo.music.ui.player.CanvasArtworkPlayer
import iad1tya.echo.music.ui.screens.rememberAlbumCanvas
import iad1tya.echo.music.ui.utils.rememberIsTvOrCar
import iad1tya.echo.music.ui.utils.tvFocusRestorer
import iad1tya.echo.music.ui.utils.tvFocusable
import iad1tya.echo.music.utils.ShareLinks
import iad1tya.echo.music.utils.likedFirst
import iad1tya.echo.music.utils.makeTimeString
import iad1tya.echo.music.utils.rememberPerfGatedBoolean
import iad1tya.echo.music.utils.rememberPreference
import iad1tya.echo.music.viewmodels.AlbumViewModel
import java.util.Locale
import kotlinx.coroutines.launch

/**
 * # Álbum — "Interfaz nueva"
 *
 * The `album/{albumId}` route. This is the screen the redesigned player hands the user to when the
 * title line is tapped, so it is one of the two the owner meets first after the player.
 *
 * ## Same data, same actions
 * Everything comes from the SAME [AlbumViewModel] the classic `ui/screens/AlbumScreen.kt` uses, behind
 * the same `NavBackStackEntry`: the album, the other versions, the "novedades para ti" shelf, the
 * description (YouTube ▸ Wikipedia ▸ Apple, resolved in the ViewModel) and the two terminal states.
 * Every action is the classic lambda, verbatim:
 *  · Reproducir / Pausar → `getAutomix(playlistId)` + `LocalAlbumRadio(albumWithSongs)`, built from the
 *    RAW album so track order is preserved (the displayed list is liked-first).
 *  · Aleatorio → [rememberShuffleMemoryPrompt] on the `"AL:" + albumId` context, unplayed-first opener,
 *    `startShuffled = true` — the same no-repeat memory the classic button writes, so the two skins
 *    share one memory.
 *  · Guardar → `AlbumEntity.toggleLike()` + `downloadSongsIfAutoOnLike`.
 *  · The row ⋯ / long-press ⋯ / header ⋯ / selection ⋯ open `SongMenu`, `AlbumMenu`,
 *    `SelectionSongMenu` and `YouTubeAlbumMenu` — the classic sheets, unmodified.
 *
 * ## Canvas
 * The animated album artwork is content, not decoration. [rememberAlbumCanvas] and
 * [CanvasArtworkPlayer] are the classic ones, behind the same two gates (`AlbumCanvasEnabledKey`
 * through `rememberPerfGatedBoolean`, forced off by Ahorro de datos).
 *
 * ## What the redesign adds, and why it is not an invention
 *  · The hero PARALLAXES and fades on scroll. The classic header only had a static negative tuck under
 *    the app bar; the redesign draws no Material bar, so the tuck has nothing to hide behind and the
 *    motion replaces it. It is a `graphicsLayer` read — draw phase, no recomposition, no allocation.
 *  · The title appears in the top bar once the hero is scrolled away. The classic album bar computed
 *    `transparentAppBar` and never used it, so the album title vanished entirely while scrolling.
 *  · The download state of the whole album is DRAWN. The classic screen computes exactly this value
 *    (`downloadState`, from the same `DownloadUtil.downloads` flow) and never renders it; the number
 *    here is that flow, not a guess.
 *
 * @param scrollBehavior accepted so the host can call this with the classic screen's parameters. The
 *   redesign draws no Material `TopAppBar`, so nothing consumes it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuraAlbumScreen(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
    viewModel: AlbumViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val database = LocalDatabase.current
    val menuState = LocalMenuState.current
    val haptic = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()
    val playerConnection = LocalPlayerConnection.current ?: return
    val isTvOrCar = rememberIsTvOrCar()

    val isPlaying by playerConnection.isEffectivelyPlaying.collectAsState()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsState()

    val playlistId by viewModel.playlistId.collectAsState()
    val albumWithSongs by viewModel.albumWithSongs.collectAsState()
    val otherVersions by viewModel.otherVersions.collectAsState()
    val releasesForYou by viewModel.releasesForYou.collectAsState()
    val description by viewModel.description.collectAsState()
    val descriptionRuns by viewModel.descriptionRuns.collectAsState()
    val hasFailed by viewModel.hasFailed.collectAsState()
    val notFound by viewModel.notFound.collectAsState()

    val hideExplicit by rememberPreference(key = HideExplicitKey, defaultValue = false)
    // Ahorro de datos, exactly as the classic screen reads it: video songs hidden and the album canvas
    // off while it is ON, both user preferences left untouched underneath.
    val dataSaverEnabled by rememberPreference(key = DataSaverEnabledKey, defaultValue = false)
    val hideVideoSongsPref by rememberPreference(key = HideVideoSongsKey, defaultValue = false)
    val hideVideoSongs = if (dataSaverEnabled) true else hideVideoSongsPref
    val albumCanvasEnabledPref by rememberPerfGatedBoolean(
        key = AlbumCanvasEnabledKey,
        defaultValue = false,
    )
    val albumCanvasEnabled = if (dataSaverEnabled) false else albumCanvasEnabledPref

    val canvasArtwork = rememberAlbumCanvas(
        albumTitle = albumWithSongs?.album?.title,
        artistName = albumWithSongs?.artists?.firstOrNull()?.name,
        firstSongTitle = albumWithSongs?.songs?.firstOrNull()?.song?.title,
    )

    // The DISPLAY list: the classic filters plus liked-first, and then `distinctBy { it.id }`.
    // The distinct is not cosmetic — every row of this list is keyed by its song id, and a repeated key
    // is the crash that shipped in 0.6.148-beta1. The classic screen keys by the same id without the
    // guard, so this is strictly safer and identical whenever the ids were already unique.
    val filteredSongs = remember(albumWithSongs, hideExplicit, hideVideoSongs) {
        var songs = albumWithSongs?.songs ?: emptyList()
        if (hideExplicit) songs = songs.filter { !it.song.explicit }
        if (hideVideoSongs) songs = songs.filter { !it.song.isVideo }
        songs.likedFirst().distinctBy { it.id }
    }

    var inSelectMode by rememberSaveable { mutableStateOf(false) }
    val selection = rememberSaveable(
        saver = listSaver<MutableList<String>, String>(
            save = { it.toList() },
            restore = { it.toMutableStateList() },
        ),
    ) { mutableStateListOf() }
    val onExitSelectionMode = {
        inSelectMode = false
        selection.clear()
    }
    if (inSelectMode) {
        BackHandler(onBack = onExitSelectionMode)
    }

    LaunchedEffect(filteredSongs) {
        val present = filteredSongs.mapTo(HashSet()) { it.id }
        selection.retainAll { it in present }
    }

    // The whole-album download state, from the same flow the classic screen collects.
    val downloadUtil = LocalDownloadUtil.current
    var downloadState by remember { mutableIntStateOf(Download.STATE_STOPPED) }
    LaunchedEffect(albumWithSongs) {
        val songs = albumWithSongs?.songs?.map { it.id }
        if (songs.isNullOrEmpty()) return@LaunchedEffect
        downloadUtil.downloads.collect { downloads ->
            downloadState = if (songs.all { downloads[it]?.state == Download.STATE_COMPLETED }) {
                Download.STATE_COMPLETED
            } else if (
                songs.all {
                    downloads[it]?.state == Download.STATE_QUEUED ||
                        downloads[it]?.state == Download.STATE_DOWNLOADING ||
                        downloads[it]?.state == Download.STATE_COMPLETED
                }
            ) {
                Download.STATE_DOWNLOADING
            } else {
                Download.STATE_STOPPED
            }
        }
    }

    val listState = rememberLazyListState()
    val bloom = rememberAuraBloom(mediaMetadata?.id)

    val insets = LocalPlayerAwareWindowInsets.current
    val bottomClearance = insets
        .only(WindowInsetsSides.Bottom)
        .asPaddingValues()
        .calculateBottomPadding()

    val album = albumWithSongs

    Box(
        modifier = Modifier
            .fillMaxSize()
            .auraScreenBackground(bloom, intensity = 0.45f),
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(insets.only(WindowInsetsSides.Horizontal)),
            // Top = 0: the hero is full-bleed and scrolls under the status bar. See AuraDetailChrome
            // for why the redesigned pushed screens never consume the 64 dp app-bar reserve.
            contentPadding = PaddingValues(bottom = bottomClearance + 56.dp),
        ) {
            if (album != null && album.songs.isNotEmpty()) {
                item(key = "aura_album_hero") {
                    AuraAlbumHero(
                        thumbnailUrl = album.album.thumbnailUrl,
                        canvasPrimaryUrl = canvasArtwork?.animated,
                        canvasFallbackUrl = canvasArtwork?.videoUrl,
                        canvasEnabled = albumCanvasEnabled,
                        listState = listState,
                    )
                }

                item(key = "aura_album_header") {
                    AuraAlbumHeader(
                        album = album,
                        filteredSongs = filteredSongs,
                        playlistId = playlistId,
                        isPlaying = isPlaying,
                        isCurrentAlbum = mediaMetadata?.album?.id == album.album.id,
                        downloadState = downloadState,
                        description = description,
                        descriptionRuns = descriptionRuns,
                        isTvOrCar = isTvOrCar,
                        onOpenArtist = { artistId -> navController.navigate("artist/$artistId") },
                        onToggleSave = {
                            val willLike = album.album.bookmarkedAt == null
                            database.query { update(album.album.toggleLike()) }
                            // Liking an album also downloads it — the classic mirror of
                            // download-on-like for a single song.
                            if (willLike) {
                                coroutineScope.launch {
                                    iad1tya.echo.music.utils.downloadSongsIfAutoOnLike(
                                        context,
                                        album.songs,
                                    )
                                }
                            }
                        },
                    )
                }

                if (filteredSongs.isNotEmpty()) {
                    item(key = "aura_album_songs_label") {
                        AuraSectionHeader(
                            title = stringResource(R.string.songs),
                            modifier = Modifier.animateItem(),
                        )
                    }

                    itemsIndexed(
                        items = filteredSongs,
                        key = { _, song -> song.id },
                    ) { index, song ->
                        val selected = song.id in selection
                        val onCheckedChange: (Boolean) -> Unit = { checked ->
                            if (checked) selection.add(song.id) else selection.remove(song.id)
                        }

                        AuraRow(
                            title = song.song.title,
                            subtitle = song.artists.joinToString { it.name }.takeIf { it.isNotBlank() },
                            highlighted = song.id == mediaMetadata?.id,
                            contentDescription = song.song.title,
                            onClick = {
                                when {
                                    inSelectMode -> onCheckedChange(!selected)
                                    song.id == mediaMetadata?.id -> playerConnection.togglePlayPause()
                                    else -> {
                                        playerConnection.service.getAutomix(playlistId)
                                        // The DISPLAYED list, so the tapped index maps to the right
                                        // song — the classic row does exactly this.
                                        playerConnection.playQueue(
                                            LocalAlbumRadio(
                                                album.copy(songs = filteredSongs),
                                                startIndex = index,
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
                                    if (song.song.explicit) {
                                        AuraTechnicalText(
                                            text = "E",
                                            color = AuraPalette.OnGroundDisabled,
                                            style = AuraType.QualityBadge,
                                        )
                                    }
                                    if (song.song.inLibrary != null) {
                                        AuraIconGlyph(
                                            icon = AuraIcons.Library,
                                            contentDescription = null,
                                            size = 15.dp,
                                            tint = AuraPalette.OnGroundDisabled,
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
                                    AuraDownloadTick(songId = song.id)
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
                }

                val versions = otherVersions.distinctBy { it.id }
                if (versions.isNotEmpty()) {
                    item(key = "aura_album_versions_label") {
                        AuraSectionHeader(
                            title = stringResource(R.string.other_versions),
                            modifier = Modifier.animateItem(),
                        )
                    }
                    item(key = "aura_album_versions_row") {
                        AuraDetailShelf(modifier = Modifier.animateItem()) {
                            itemsIndexed(
                                items = versions,
                                key = { position, _ -> "aura_album_version_$position" },
                            ) { _, item ->
                                AuraCoverCard(
                                    title = item.title,
                                    subtitle = auraSearchYtSubtitle(item),
                                    thumbnailUrl = item.thumbnail,
                                    seed = item.id,
                                    width = 128.dp,
                                    isActive = mediaMetadata?.album?.id == item.id,
                                    isPlaying = isPlaying,
                                    onClick = { navController.navigate("album/${item.id}") },
                                    onLongClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        menuState.show {
                                            YouTubeAlbumMenu(
                                                albumItem = item,
                                                navController = navController,
                                                onDismiss = menuState::dismiss,
                                            )
                                        }
                                    },
                                    modifier = Modifier
                                        .animateItem()
                                        .tvFocusable(isTvOrCar, AuraShapes.Highlight, scaleFocused = 1f),
                                )
                            }
                        }
                    }
                }

                val releases = releasesForYou.distinctBy { it.id }
                if (releases.isNotEmpty()) {
                    item(key = "aura_album_releases_label") {
                        AuraSectionHeader(
                            title = stringResource(R.string.releases_for_you),
                            modifier = Modifier.animateItem(),
                        )
                    }
                    item(key = "aura_album_releases_row") {
                        AuraDetailShelf(modifier = Modifier.animateItem()) {
                            itemsIndexed(
                                items = releases,
                                key = { position, _ -> "aura_album_release_$position" },
                            ) { _, item ->
                                AuraCoverCard(
                                    title = item.title,
                                    subtitle = auraSearchYtSubtitle(item),
                                    thumbnailUrl = item.thumbnail,
                                    seed = item.id,
                                    width = 128.dp,
                                    isActive = mediaMetadata?.album?.id == item.id,
                                    isPlaying = isPlaying,
                                    onClick = { navController.navigate("album/${item.id}") },
                                    onLongClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        menuState.show {
                                            YouTubeAlbumMenu(
                                                albumItem = item,
                                                navController = navController,
                                                onDismiss = menuState::dismiss,
                                            )
                                        }
                                    },
                                    modifier = Modifier
                                        .animateItem()
                                        .tvFocusable(isTvOrCar, AuraShapes.Highlight, scaleFocused = 1f),
                                )
                            }
                        }
                    }
                }
            } else if (notFound) {
                // Terminal NOT_FOUND: the album is gone from YouTube. Deliberately NO retry — the
                // classic screen makes the same distinction, because retrying a deleted album can only
                // fail and the generic "check your connection" would send the user hunting a problem
                // that does not exist.
                item(key = "aura_album_not_found") {
                    Column {
                        Spacer(Modifier.height(auraStatusBarPadding() + 72.dp))
                        AuraEmpty(text = stringResource(R.string.album_no_longer_available))
                    }
                }
            } else if (hasFailed) {
                item(key = "aura_album_error") {
                    Column {
                        Spacer(Modifier.height(auraStatusBarPadding() + 72.dp))
                        AuraDetailErrorState(
                            message = stringResource(R.string.couldnt_load_album),
                            onRetry = viewModel::load,
                        )
                    }
                }
            } else {
                item(key = "aura_album_skeleton") {
                    Column {
                        Spacer(Modifier.height(auraStatusBarPadding() + 56.dp))
                        ShimmerHost { repeat(8) { AuraDetailSkeletonRow() } }
                    }
                }
            }
        }

        // ── Barra superior ────────────────────────────────────────────────────────────────────────
        AuraDetailTopBar(
            listState = listState,
            title = album?.album?.title.orEmpty(),
            inSelectMode = inSelectMode,
            selectionCount = selection.size,
            onBack = { if (inSelectMode) onExitSelectionMode() else navController.navigateUp() },
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
                    contentDescription = stringResource(R.string.more_options),
                    enabled = selection.isNotEmpty(),
                    onClick = {
                        menuState.show {
                            SelectionSongMenu(
                                songSelection = selection.mapNotNull { songId ->
                                    filteredSongs.find { it.id == songId }
                                },
                                onDismiss = menuState::dismiss,
                                clearAction = onExitSelectionMode,
                            )
                        }
                    },
                    size = 20.dp,
                )
            },
            actions = {
                if (album != null) {
                    AuraIconButton(
                        icon = AuraIcons.Share,
                        contentDescription = stringResource(R.string.share),
                        onClick = {
                            val intent = android.content.Intent(android.content.Intent.ACTION_SEND)
                                .apply {
                                    type = "text/plain"
                                    putExtra(
                                        android.content.Intent.EXTRA_TEXT,
                                        ShareLinks.playlist(album.album.playlistId),
                                    )
                                }
                            context.startActivity(
                                android.content.Intent.createChooser(intent, null),
                            )
                        },
                        size = 20.dp,
                    )
                    AuraIconButton(
                        icon = AuraIcons.More,
                        contentDescription = stringResource(R.string.more_options),
                        onClick = {
                            menuState.show {
                                AlbumMenu(
                                    originalAlbum = Album(album.album, album.artists),
                                    navController = navController,
                                    onDismiss = menuState::dismiss,
                                )
                            }
                        },
                        size = 20.dp,
                    )
                }
            },
        )
    }
}

// ── Hero ──────────────────────────────────────────────────────────────────────────────────────────

/**
 * The full-bleed cover, its canvas video and the scrim that hands the image over to the ground.
 *
 * The parallax is a DRAW-phase read: `listState`'s offset is sampled inside [graphicsLayer], so the
 * hero translates and fades without recomposing anything, which is what keeps a scrolling album off
 * the thermal budget.
 *
 * A pane ≥ 840 dp (the expanded breakpoint, measured on the CONTENT pane so a narrow split-screen
 * still behaves like a phone) caps the hero to a 320 dp banner instead of a full-width square — the
 * same rule the classic header applies, for the same reason: a square hero on a TV eats the screen.
 */
@Composable
private fun AuraAlbumHero(
    thumbnailUrl: String?,
    canvasPrimaryUrl: String?,
    canvasFallbackUrl: String?,
    canvasEnabled: Boolean,
    listState: LazyListState,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        // Hoisted: inside the inner Box the closest implicit receiver is BoxScope, not
        // BoxWithConstraintsScope, so `maxWidth` cannot be reached there without an explicit receiver.
        val heroWidth = maxWidth
        val isWideHero = heroWidth >= 840.dp
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (isWideHero) Modifier.height(320.dp) else Modifier.aspectRatio(1f))
                .graphicsLayer {
                    val scrolled = if (listState.firstVisibleItemIndex == 0) {
                        listState.firstVisibleItemScrollOffset.toFloat()
                    } else {
                        size.height
                    }
                    translationY = scrolled * 0.35f
                    alpha = (1f - scrolled / size.height.coerceAtLeast(1f)).coerceIn(0f, 1f)
                },
        ) {
            AuraCover(
                thumbnailUrl = thumbnailUrl,
                size = heroWidth,
                seed = thumbnailUrl,
                shape = androidx.compose.ui.graphics.RectangleShape,
                decodeTo = 1200,
                ratio = if (isWideHero) heroWidth / 320.dp else 1f,
            )

            if (canvasEnabled && (canvasPrimaryUrl != null || canvasFallbackUrl != null)) {
                CanvasArtworkPlayer(
                    primaryUrl = canvasPrimaryUrl,
                    fallbackUrl = canvasFallbackUrl,
                    isPlaying = true,
                    modifier = Modifier.fillMaxSize(),
                )
            }

            // The hand-over to the ground. A gradient, never a blur: a live full-screen blur is what
            // the thermal contract forbids.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, AuraPalette.Ground),
                        ),
                    ),
            )
        }
    }
}

// ── Cabecera ──────────────────────────────────────────────────────────────────────────────────────

/**
 * Title, artist(s), the technical meta line, the three actions and the "Acerca de" block.
 *
 * Every action is passed in or built from the classic call, and the shuffle is the shared
 * [rememberShuffleMemoryPrompt] on the `"AL:"` context — see the file KDoc for why the prefix is not
 * `"PL:"` (the startup orphan prune deletes `PL:%` contexts with no playlist row).
 */
@Composable
private fun AuraAlbumHeader(
    album: iad1tya.echo.music.db.entities.AlbumWithSongs,
    filteredSongs: List<iad1tya.echo.music.db.entities.Song>,
    playlistId: String,
    isPlaying: Boolean,
    isCurrentAlbum: Boolean,
    downloadState: Int,
    description: String?,
    descriptionRuns: List<com.music.innertube.models.Run>?,
    isTvOrCar: Boolean,
    onOpenArtist: (String) -> Unit,
    onToggleSave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val playerConnection = LocalPlayerConnection.current ?: return

    val albumShuffleContextId = "AL:" + album.album.id
    val albumPlayedForStart = rememberPlayedShuffleSet(albumShuffleContextId)
    val onShuffleClick = rememberShuffleMemoryPrompt(
        contextId = albumShuffleContextId,
        playedCount = album.songs.count { it.id in albumPlayedForStart },
        totalCount = album.songs.size,
    ) { resetMemory ->
        playerConnection.service.getAutomix(playlistId)
        // The RAW album, never the liked-first display list: shuffling a biased list stays biased.
        // Still a LocalAlbumRadio, so YouTube's album-radio continuation (what plays AFTER the album)
        // survives and the no-repeat memory is added on top of it.
        val (unheard, heard) = if (resetMemory) {
            album.songs to emptyList()
        } else {
            album.songs.partition { it.id !in albumPlayedForStart }
        }
        val openerId = (unheard.ifEmpty { heard }).randomOrNull()?.id
        playerConnection.playQueue(
            LocalAlbumRadio(
                albumWithSongs = album,
                startIndex = openerId
                    ?.let { id -> album.songs.indexOfFirst { it.id == id } }
                    ?.coerceAtLeast(0)
                    ?: 0,
                contextId = albumShuffleContextId,
                startShuffled = true,
            ),
        )
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = AuraSpacing.Gutter),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = album.album.title,
            style = AuraType.ScreenTitle,
            color = AuraPalette.OnGround,
            maxLines = 2,
            overflow = AuraDefaultOverflow,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )

        val singleArtist = album.artists.singleOrNull()
        if (singleArtist != null) {
            Spacer(Modifier.height(10.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .clip(AuraShapes.Pill)
                    .auraClickableInternal(
                        onClick = { onOpenArtist(singleArtist.id) },
                        contentDescription = singleArtist.name,
                    )
                    .tvFocusable(isTvOrCar, AuraShapes.Pill, scaleFocused = 1f)
                    .padding(horizontal = 8.dp, vertical = 6.dp),
            ) {
                AuraCover(
                    thumbnailUrl = singleArtist.thumbnailUrl,
                    size = 28.dp,
                    seed = singleArtist.id,
                    shape = CircleShape,
                    decodeTo = 128,
                )
                Text(
                    text = singleArtist.name,
                    style = AuraType.RowTitle,
                    color = AuraPalette.OnGroundMuted,
                    maxLines = 1,
                    overflow = AuraDefaultOverflow,
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        // "ÁLBUM · 2019 · 12 CANCIONES · 48:31" — the classic meta line, as the redesign's technical
        // type. The track word is the app's own plural (the classic literal was English "Tracks").
        val totalSeconds = remember(album) { album.songs.sumOf { it.song.duration } }
        val metaLine = listOfNotNull(
            stringResource(R.string.album_text),
            album.album.year?.toString(),
            pluralStringResource(R.plurals.n_song, album.songs.size, album.songs.size),
            makeTimeString(totalSeconds * 1000L),
        ).joinToString(" · ").uppercase(Locale.ROOT)

        AuraTechnicalText(text = metaLine)

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.padding(top = 8.dp),
        ) {
            if (album.album.explicit) {
                AuraTechnicalText(
                    text = stringResource(R.string.explicit).uppercase(Locale.ROOT),
                    color = AuraPalette.OnGroundDisabled,
                )
            }
            // The whole-album download state. Real data: the same DownloadUtil flow every row's tick
            // reads. The classic screen computes this value and never draws it.
            when (downloadState) {
                Download.STATE_COMPLETED -> Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    AuraIconGlyph(
                        icon = AuraIcons.Check,
                        contentDescription = null,
                        size = 14.dp,
                        tint = AuraPalette.Teal,
                    )
                    AuraTechnicalText(
                        text = stringResource(R.string.filter_downloaded).uppercase(Locale.ROOT),
                        color = AuraPalette.Teal,
                    )
                }

                Download.STATE_DOWNLOADING -> AuraTechnicalText(
                    text = stringResource(R.string.downloading).uppercase(Locale.ROOT),
                    color = AuraPalette.OnGroundFaint,
                )

                else -> Unit
            }
        }

        // "Aleatorio mejorado · X/Y reproducidas" — the shared chip, which hides itself when the
        // feature is off.
        EnhancedShuffleChip(
            playedCount = album.songs.count { it.id in albumPlayedForStart },
            total = album.songs.size,
            modifier = Modifier.padding(top = 10.dp),
        )

        Spacer(Modifier.height(18.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AuraDetailCircleButton(
                icon = if (album.album.bookmarkedAt != null) AuraIcons.HeartFilled else AuraIcons.Heart,
                contentDescription = if (album.album.bookmarkedAt != null) {
                    stringResource(R.string.saved)
                } else {
                    stringResource(R.string.save)
                },
                tint = if (album.album.bookmarkedAt != null) AuraPalette.Teal else AuraPalette.OnGround,
                onClick = onToggleSave,
                modifier = Modifier.tvFocusable(isTvOrCar, AuraShapes.Pill, scaleFocused = 1f),
            )

            AuraDetailActionButton(
                icon = if (isPlaying && isCurrentAlbum) AuraIcons.Pause else AuraIcons.Play,
                label = if (isPlaying && isCurrentAlbum) {
                    stringResource(R.string.pause)
                } else {
                    stringResource(R.string.play)
                },
                accent = true,
                onClick = {
                    if (isPlaying && isCurrentAlbum) {
                        playerConnection.player.pause()
                    } else if (isCurrentAlbum) {
                        playerConnection.player.play()
                    } else {
                        playerConnection.service.getAutomix(playlistId)
                        // The RAW album in TRACK ORDER: an album is an ordered work, and the display
                        // list is liked-first. MusicService.playQueue re-applies the hide-explicit /
                        // hide-video filters to every queue it builds, so nothing is lost by it.
                        playerConnection.playQueue(LocalAlbumRadio(album))
                    }
                },
                modifier = Modifier
                    .weight(1f)
                    .tvFocusable(isTvOrCar, AuraShapes.Pill, scaleFocused = 1f),
            )

            AuraDetailCircleButton(
                icon = AuraIcons.Shuffle,
                contentDescription = stringResource(R.string.shuffle_label),
                onClick = onShuffleClick,
                modifier = Modifier.tvFocusable(isTvOrCar, AuraShapes.Pill, scaleFocused = 1f),
            )
        }

        Spacer(Modifier.height(20.dp))

        // "Acerca de este álbum". The fallback sentence is the classic one, kept so an album with no
        // fetched description still says something instead of showing an empty block.
        val staticDescription = remember(album) {
            "${album.album.title} is an album by ${album.artists.joinToString { it.name }}${
                if (album.album.year != null) ", released in ${album.album.year}" else ""
            }. This collection features ${album.songs.size} tracks showcasing their musical artistry."
        }
        Column(Modifier.fillMaxWidth()) {
            AuraSectionLabel(text = stringResource(R.string.about_album).uppercase(Locale.ROOT))
            Spacer(Modifier.height(6.dp))
            AuraDetailDescription(
                text = description ?: staticDescription,
                runs = descriptionRuns?.map {
                    LinkSegment(text = it.text, url = it.navigationEndpoint?.urlEndpoint?.url)
                },
            )
        }

        if (album.artists.size > 1) {
            Spacer(Modifier.height(14.dp))
            val byLabel = stringResource(R.string.by_text)
            val accent = AuraPalette.Teal
            val byLine = remember(album.artists, byLabel, accent) {
                buildAnnotatedString {
                    append(byLabel)
                    append(" ")
                    album.artists.forEachIndexed { index, artist ->
                        withLink(LinkAnnotation.Clickable(artist.id) { onOpenArtist(artist.id) }) {
                            withStyle(SpanStyle(color = accent)) { append(artist.name) }
                        }
                        if (index != album.artists.lastIndex) append(", ")
                    }
                }
            }
            Text(
                text = byLine,
                style = AuraType.RowSubtitle,
                color = AuraPalette.OnGroundMuted,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Spacer(Modifier.height(6.dp))
    }
}

// ── Piezas compartidas con AuraArtistScreen ───────────────────────────────────────────────────────

/**
 * The floating top bar of a redesigned DETAIL screen: back, the title once the hero is gone, and the
 * screen's actions. In selection mode it becomes the count + "seleccionar todo" + the selection ⋯,
 * which is what the classic `TopAppBar` does on Álbum.
 *
 * The plate behind it only appears once there is content under it, so the hero opens edge to edge.
 */
@Composable
internal fun AuraDetailTopBar(
    listState: LazyListState,
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    inSelectMode: Boolean = false,
    selectionCount: Int = 0,
    selectionActions: (@Composable () -> Unit)? = null,
    actions: (@Composable () -> Unit)? = null,
) {
    // A threshold cross, not a per-pixel read: this recomposes twice per scroll, not per frame.
    val scrolled by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 220
        }
    }
    val plate by animateColorAsState(
        targetValue = if (scrolled || inSelectMode) {
            AuraPalette.Ground.copy(alpha = 0.88f)
        } else {
            Color.Transparent
        },
        animationSpec = AuraMotion.tint,
        label = "auraDetailBarPlate",
    )

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .background(plate)
            .windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Top))
            .padding(horizontal = 6.dp, vertical = 4.dp),
    ) {
        AuraDetailBackButton(onClick = onBack)
        Text(
            text = if (inSelectMode) {
                pluralStringResource(R.plurals.n_selected, selectionCount, selectionCount)
            } else {
                title
            },
            style = AuraType.SheetTitle,
            color = AuraPalette.OnGround,
            maxLines = 1,
            overflow = AuraDefaultOverflow,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 4.dp)
                // The title belongs to the hero until the hero is gone; fading it in the draw phase
                // keeps the whole bar out of the scroll's recomposition path.
                .graphicsLayer { alpha = if (inSelectMode || scrolled) 1f else 0f },
        )
        if (inSelectMode) selectionActions?.invoke() else actions?.invoke()
    }
}

/** The redesign's horizontal shelf: gutter-aligned, 11 dp gaps, D-pad focus restored. */
@Composable
internal fun AuraDetailShelf(
    modifier: Modifier = Modifier,
    content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit,
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = AuraSpacing.Gutter),
        horizontalArrangement = Arrangement.spacedBy(11.dp),
        modifier = modifier
            .padding(top = AuraSpacing.SectionGap)
            .tvFocusRestorer(),
        content = content,
    )
}

/** Reproducir / Pausar. [accent] draws the gradient — the one full-colour element on a screen. */
@Composable
internal fun AuraDetailActionButton(
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

/** A 48 dp `SurfaceFill` circle with a hairline — Guardar and Aleatorio on the album header. */
@Composable
internal fun AuraDetailCircleButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = AuraPalette.OnGround,
) {
    Box(
        modifier = modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(AuraPalette.SurfaceFill)
            .border(1.dp, AuraPalette.SurfaceLine, CircleShape)
            .auraClickableInternal(onClick = onClick, contentDescription = contentDescription),
        contentAlignment = Alignment.Center,
    ) {
        AuraIconGlyph(
            icon = icon,
            contentDescription = null,
            size = 20.dp,
            tint = tint,
        )
    }
}

/**
 * The "Acerca de" text with its Mostrar más / Mostrar menos toggle and YouTube's inline links.
 *
 * `ui/component/ExpandableText` is deliberately not reused: it paints itself with
 * `MaterialTheme.colorScheme.onSurfaceVariant`, and these screens draw the redesign's near-black
 * ground WITHOUT installing a matching Material theme — so for a user running the beta in the light
 * theme that classic text would be dark ink on a dark ground. Same `R.string.show_more` /
 * `R.string.show_less` labels, same collapse behaviour, same links.
 */
@Composable
internal fun AuraDetailDescription(
    text: String,
    modifier: Modifier = Modifier,
    runs: List<LinkSegment>? = null,
    collapsedMaxLines: Int = 3,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    var hasOverflow by rememberSaveable { mutableStateOf(false) }
    val uriHandler = LocalUriHandler.current
    val accent = AuraPalette.Teal

    val annotated: AnnotatedString = remember(text, runs, accent) {
        if (runs.isNullOrEmpty()) {
            AnnotatedString(text)
        } else {
            buildAnnotatedString {
                runs.forEach { segment ->
                    val url = segment.url
                    if (url != null) {
                        withLink(LinkAnnotation.Clickable(url) { uriHandler.openUri(url) }) {
                            withStyle(SpanStyle(color = accent)) { append(segment.text) }
                        }
                    } else {
                        append(segment.text)
                    }
                }
            }
        }
    }

    Column(modifier) {
        Text(
            text = annotated,
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

/** Terminal failure with a retry — the classic "no se pudo cargar" block, in the new language. */
@Composable
internal fun AuraDetailErrorState(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = AuraSpacing.Gutter, vertical = 32.dp),
    ) {
        Text(
            text = message,
            style = AuraType.RowTitle,
            color = AuraPalette.OnGroundMuted,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            maxLines = 3,
            overflow = AuraDefaultOverflow,
        )
        Spacer(Modifier.height(14.dp))
        AuraDetailActionButton(
            icon = AuraIcons.Repeat,
            label = stringResource(R.string.retry),
            accent = false,
            onClick = onRetry,
            modifier = Modifier.width(180.dp),
        )
    }
}

/**
 * The skeleton row of a detail screen. Drawn inside the app's own [ShimmerHost] — only the shapes are
 * Aura's, because the classic `ListItemPlaceHolder` paints Material surfaces that would stamp pale
 * rectangles across the near-black ground.
 */
@Composable
internal fun AuraDetailSkeletonRow(modifier: Modifier = Modifier) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AuraSpacing.RowInner),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = AuraSpacing.Gutter, vertical = 8.dp),
    ) {
        Spacer(
            Modifier
                .size(50.dp)
                .clip(AuraShapes.Artwork)
                .background(AuraPalette.SurfaceFill),
        )
        Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Spacer(
                Modifier
                    .width(170.dp)
                    .height(13.dp)
                    .clip(AuraShapes.Pill)
                    .background(AuraPalette.SurfaceFill),
            )
            Spacer(
                Modifier
                    .width(110.dp)
                    .height(11.dp)
                    .clip(AuraShapes.Pill)
                    .background(AuraPalette.SurfaceFill),
            )
        }
    }
}
