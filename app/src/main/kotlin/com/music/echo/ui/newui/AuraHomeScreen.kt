package iad1tya.echo.music.ui.newui

import android.text.format.DateUtils
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.items as lazyGridItems
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.carousel.HorizontalCenteredHeroCarousel
import androidx.compose.material3.carousel.rememberCarouselState
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState
import coil3.compose.AsyncImage
import com.music.innertube.models.AlbumItem
import com.music.innertube.models.ArtistItem
import com.music.innertube.models.PlaylistItem
import com.music.innertube.models.SongItem
import com.music.innertube.models.WatchEndpoint
import com.music.innertube.models.YTItem
import iad1tya.echo.music.LocalDatabase
import iad1tya.echo.music.LocalPlayerAwareWindowInsets
import iad1tya.echo.music.LocalPlayerConnection
import iad1tya.echo.music.R
import iad1tya.echo.music.constants.GridItemSize
import iad1tya.echo.music.constants.GridItemsSizeKey
import iad1tya.echo.music.constants.HighPerformanceModeKey
import iad1tya.echo.music.constants.HomeRichLayoutKey
import iad1tya.echo.music.constants.HomeTasteOnlyKey
import iad1tya.echo.music.constants.OfflineModeKey
import iad1tya.echo.music.constants.RandomizeHomeOrderKey
import iad1tya.echo.music.constants.ShowSpeedDialKey
import iad1tya.echo.music.db.entities.Album
import iad1tya.echo.music.db.entities.Artist
import iad1tya.echo.music.db.entities.LocalItem
import iad1tya.echo.music.db.entities.Playlist
import iad1tya.echo.music.db.entities.Song
import iad1tya.echo.music.extensions.toMediaItem
import iad1tya.echo.music.models.toMediaMetadata
import iad1tya.echo.music.playback.queues.ListQueue
import iad1tya.echo.music.playback.queues.YouTubeQueue
import iad1tya.echo.music.reco.AutoRecoPlaylistWorker
import iad1tya.echo.music.ui.component.LocalMenuState
import iad1tya.echo.music.ui.component.shimmer.ShimmerHost
import iad1tya.echo.music.ui.menu.AlbumMenu
import iad1tya.echo.music.ui.menu.ArtistMenu
import iad1tya.echo.music.ui.menu.PlaylistMenu
import iad1tya.echo.music.ui.menu.SongMenu
import iad1tya.echo.music.ui.menu.YouTubeAlbumMenu
import iad1tya.echo.music.ui.menu.YouTubeArtistMenu
import iad1tya.echo.music.ui.menu.YouTubePlaylistMenu
import iad1tya.echo.music.ui.menu.YouTubeSongMenu
import iad1tya.echo.music.ui.screens.CommunityPlaylistCard
import iad1tya.echo.music.ui.screens.DownloadedOnlyView
import iad1tya.echo.music.ui.screens.HomeSection
import iad1tya.echo.music.ui.screens.MoodAndGenresButton
import iad1tya.echo.music.ui.screens.NetworkReload
import iad1tya.echo.music.ui.screens.computeHomeSections
import iad1tya.echo.music.ui.utils.rememberIsTvOrCar
import iad1tya.echo.music.ui.utils.resize
import iad1tya.echo.music.ui.utils.tvFocusRestorer
import iad1tya.echo.music.ui.utils.tvFocusable
import iad1tya.echo.music.utils.isInternetAvailable
import iad1tya.echo.music.constants.AiRecommendedPlaylistKey
import iad1tya.echo.music.utils.rememberEnumPreference
import iad1tya.echo.music.utils.rememberPreference
import iad1tya.echo.music.viewmodels.HomeViewModel
import java.time.LocalTime
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * # Inicio — "Interfaz nueva"
 *
 * A second PRESENTATION of the existing Home. It holds the same [HomeViewModel], reads the same flows,
 * calls the same `playerConnection` / `navController` / `menuState` actions, and orders its shelves with
 * the same [computeHomeSections] the classic Home uses — one copy, so one fix.
 *
 * ## What the render defines and what the inventory defines
 * The render draws three things: a `BUENAS NOCHES / Inicio` header, a row of cover cards, and a
 * `RECOMENDADO PARA TI · IA` list of song rows. That is the LANGUAGE, not the content: the classic Home
 * has up to eighteen conditional shelves and the inventory lists every one of them. So every shelf is
 * here, expressed in that language — a tracked mono section rule, then either a shelf of [AuraCoverCard]
 * or a column of [AuraSongRow].
 *
 * ## What this screen deliberately does NOT draw
 * The mini player and the bottom navigation bar in the render's Inicio belong to the app SKELETON
 * (`MainActivity` draws them over every destination). Drawing them here would double them.
 *
 * ## Surfaces reused as-is
 * `CommunityPlaylistCard`, `MoodAndGenresButton` and `DownloadedOnlyView` are called verbatim. They are
 * separate surfaces (the offline home is its own inventory section) and the community card owns a real
 * database transaction — re-skinning it would mean a second copy of that write. They keep their classic
 * look inside the new Home; that is a reported, deliberate trade.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun AuraHomeScreen(
    navController: NavController,
    @Suppress("UNUSED_PARAMETER") snackbarHostState: SnackbarHostState,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val menuState = LocalMenuState.current
    val database = LocalDatabase.current
    val playerConnection = LocalPlayerConnection.current ?: return
    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val isPlaying by playerConnection.isEffectivelyPlaying.collectAsState()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsState()

    val quickPicks by viewModel.quickPicksDisplay.collectAsState()
    val forgottenFavorites by viewModel.forgottenFavorites.collectAsState()
    val keepListening by viewModel.keepListeningDisplay.collectAsState()
    val similarRecommendations by viewModel.similarRecommendations.collectAsState()
    val accountPlaylists by viewModel.accountPlaylists.collectAsState()
    val homePage by viewModel.homePage.collectAsState()
    val explorePage by viewModel.explorePage.collectAsState()
    val dailyMixes by viewModel.dailyMixes.collectAsState()
    val recentlyPlayed by viewModel.recentlyPlayed.collectAsState()
    val timeOfDayMix by viewModel.timeOfDayMixDisplay.collectAsState()
    val aiRecommendedPlaylist by viewModel.aiRecommendedPlaylist.collectAsState()
    val aiRecommendedSongs by viewModel.aiRecommendedSongs.collectAsState()
    val communityPlaylists by viewModel.communityPlaylists.collectAsState()
    val newFromArtists by viewModel.newFromArtists.collectAsState()
    val genreMix by viewModel.genreMix.collectAsState()
    val pinnedPodcasts by viewModel.pinnedPodcasts.collectAsState(initial = emptyList())
    val speedDialItems by viewModel.speedDialItems.collectAsState()
    val selectedChip by viewModel.selectedChip.collectAsState()
    val isChipLoading by viewModel.isChipLoading.collectAsState()
    val chipError by viewModel.chipError.collectAsState()
    val accountName by viewModel.accountName.collectAsState()

    val isLoading by viewModel.isLoading.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val hasPlayHistory by viewModel.hasPlayHistory.collectAsState()

    val perfOn by rememberPreference(HighPerformanceModeKey, false)
    var offlineMode by rememberPreference(OfflineModeKey, false)
    val (randomizeHomeOrder) = rememberPreference(RandomizeHomeOrderKey, false)
    val (showSpeedDial) = rememberPreference(ShowSpeedDialKey, true)
    val (tasteOnlyHome) = rememberPreference(HomeTasteOnlyKey, true)
    // ── "Inicio enriquecido" (HomeRichLayoutKey) ──────────────────────────────────────────────────
    // The SAME preference, with the SAME meaning, that the classic Home reads to size its editorial
    // cards (HomeScreen.kt:818-820 — rich = GridThumbnailHeight × 1.25, compact = GridThumbnailHeight).
    // Its description promises "carátulas más grandes […] desactívalo para volver al estilo compacto de
    // cuadritos"; the classic Home was its only read site, so with the new Home on it changed nothing.
    // This Home sizes its shelves by card WIDTH rather than height, so the same 1.25 ratio is applied
    // there: rich keeps the render's editorial widths, compact divides every one of them by 1.25. One
    // scale for every shelf, so no shelf can be left behind at one setting.
    val (homeRichLayout) = rememberPreference(HomeRichLayoutKey, true)
    // ── "Tamaño de la celda de la cuadrícula" (GridItemsSizeKey) ──────────────────────────────────
    // The classic Home sizes its shelves with `currentGridThumbnailHeight()` (HomeScreen.kt:837), i.e.
    // 128 dp at Grande and 104 dp at Pequeño, so under the new Home the control silently stopped moving
    // Inicio while it still moved the ~13 classic grids, the Novedades tab (AuraSearchTabs.kt:600) and
    // now Biblioteca (AuraLibraryTabs.kt) — one setting, half the app following it. It folds into the
    // same width scale rather than becoming a second one, so the two controls compose instead of
    // fighting: `SmallGridThumbnailHeight / GridThumbnailHeight` is exactly the ratio the classic Home
    // shrinks by.
    val gridItemSize by rememberEnumPreference(GridItemsSizeKey, GridItemSize.BIG)
    val cardScale = (if (homeRichLayout) 1f else 1f / 1.25f) *
        (if (gridItemSize == GridItemSize.BIG) 1f else 104f / 128f)

    val pullRefreshState = rememberPullToRefreshState()
    val listState = rememberLazyListState()
    var randomSeed by rememberSaveable { mutableLongStateOf(System.currentTimeMillis()) }
    var forgottenExpanded by rememberSaveable { mutableStateOf(false) }

    // The ambient bloom is resolved ONCE PER TRACK (AuraBloomCache), never per frame — thermal gate.
    val bloom = rememberAuraBloom(mediaMetadata?.id)

    LaunchedEffect(isRefreshing) {
        if (isRefreshing) randomSeed = System.currentTimeMillis()
    }

    val backStackEntry by navController.currentBackStackEntryAsState()
    val scrollToTop =
        backStackEntry?.savedStateHandle?.getStateFlow("scrollToTop", false)?.collectAsState()

    LaunchedEffect(scrollToTop?.value) {
        if (scrollToTop?.value == true) {
            listState.animateScrollToItem(0)
            backStackEntry?.savedStateHandle?.set("scrollToTop", false)
        }
    }

    LaunchedEffect(Unit) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index }
            .collect { lastVisibleIndex ->
                val len = listState.layoutInfo.totalItemsCount
                if (lastVisibleIndex != null && lastVisibleIndex >= len - 3) {
                    viewModel.loadMoreYouTubeItems(homePage?.continuation)
                }
            }
    }

    NetworkReload(
        onReload = { if (homePage == null && quickPicks.isNullOrEmpty()) viewModel.refresh() },
    )

    if (selectedChip != null) {
        BackHandler { viewModel.toggleChip(selectedChip) }
    }

    // Mood-active mode: identical hook to the classic Home — one source of truth (selectedChip) covers
    // activate and every deactivate path.
    LaunchedEffect(selectedChip) {
        val chip = selectedChip
        if (chip != null) {
            playerConnection.setActiveMood(params = chip.endpoint?.params, title = chip.title)
        } else {
            playerConnection.setActiveMood(null, null)
        }
    }

    val (aiRecsEnabled) = rememberPreference(AiRecommendedPlaylistKey, false)
    val homeSections = remember(
        randomizeHomeOrder, randomSeed, tasteOnlyHome, speedDialItems, quickPicks, dailyMixes,
        timeOfDayMix, aiRecommendedSongs, aiRecsEnabled, keepListening, accountPlaylists, forgottenFavorites,
        communityPlaylists, newFromArtists, genreMix, similarRecommendations, homePage?.sections,
        explorePage?.moodAndGenres, explorePage?.newReleaseAlbums, perfOn, showSpeedDial,
    ) {
        computeHomeSections(
            randomizeHomeOrder = randomizeHomeOrder,
            randomSeed = randomSeed,
            tasteOnlyHome = tasteOnlyHome,
            showSpeedDial = showSpeedDial,
            perfOn = perfOn,
            speedDialCount = speedDialItems.size,
            quickPickCount = quickPicks?.size ?: 0,
            dailyMixItemCounts = dailyMixes?.map { it.items.size }.orEmpty(),
            timeOfDayMixSongCount = timeOfDayMix?.songs?.size ?: 0,
            // Settings toggle must win: a leftover AI playlist in the DB must not keep the shelf alive.
            aiRecommendedSongCount = if (aiRecsEnabled) aiRecommendedSongs?.size ?: 0 else 0,
            keepListeningCount = keepListening?.size ?: 0,
            accountPlaylistCount = accountPlaylists?.size ?: 0,
            forgottenFavoritesCount = forgottenFavorites?.size ?: 0,
            communityPlaylistCount = communityPlaylists?.size ?: 0,
            newFromArtistsCount = newFromArtists?.size ?: 0,
            genreMixSongCount = genreMix?.songs?.size ?: 0,
            similarRecommendationCount = similarRecommendations?.size ?: 0,
            homePageSectionCount = homePage?.sections?.size ?: 0,
            hasMoodAndGenres = explorePage?.moodAndGenres != null,
            newReleaseAlbumCount = explorePage?.newReleaseAlbums?.size ?: 0,
        )
    }

    // ── Shared item actions (the SAME lambdas the classic Home installs) ──────────────────────────

    val playSong: (Song) -> Unit = { song ->
        if (song.id == mediaMetadata?.id) playerConnection.togglePlayPause()
        else playerConnection.playQueue(YouTubeQueue.radio(song.toMediaMetadata()))
    }
    val songMenu: (Song) -> Unit = { song ->
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        menuState.show {
            SongMenu(originalSong = song, navController = navController, onDismiss = menuState::dismiss)
        }
    }
    val openYtItem: (YTItem) -> Unit = { item ->
        when (item) {
            is SongItem -> playerConnection.playQueue(
                YouTubeQueue(item.endpoint ?: WatchEndpoint(videoId = item.id), item.toMediaMetadata()),
            )

            is AlbumItem -> navController.navigate("album/${item.id}")
            is ArtistItem -> navController.navigate("artist/${item.id}")
            is PlaylistItem -> {
                // Pinned Home playlists may be local (UUID) or YouTube browse ids.
                scope.launch {
                    val local = withContext(Dispatchers.IO) {
                        database.playlist(item.id).first()
                            ?: database.playlistByBrowseId(item.id).first()
                    }
                    if (local != null) {
                        navController.navigate("local_playlist/${local.id}")
                    } else {
                        navController.navigate("online_playlist/${item.id}")
                    }
                }
            }
        }
    }
    val ytItemMenu: (YTItem) -> Unit = { item ->
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        menuState.show {
            when (item) {
                is SongItem -> YouTubeSongMenu(song = item, navController = navController, onDismiss = menuState::dismiss)
                is AlbumItem -> YouTubeAlbumMenu(albumItem = item, navController = navController, onDismiss = menuState::dismiss)
                is ArtistItem -> YouTubeArtistMenu(artist = item, onDismiss = menuState::dismiss)
                is PlaylistItem -> {
                    val local by database.playlist(item.id).collectAsState(initial = null)
                    val byBrowse by database.playlistByBrowseId(item.id).collectAsState(initial = null)
                    val saved = local ?: byBrowse
                    if (saved != null) {
                        PlaylistMenu(
                            playlist = saved,
                            coroutineScope = scope,
                            onDismiss = menuState::dismiss,
                        )
                    } else {
                        YouTubePlaylistMenu(playlist = item, coroutineScope = scope, onDismiss = menuState::dismiss)
                    }
                }
            }
        }
    }
    val playAllSongs: (String, List<Song>) -> Unit = { title, songs ->
        val items = songs.distinctBy { it.id }.map { it.toMediaItem() }
        if (items.isNotEmpty()) playerConnection.playQueue(ListQueue(title = title, items = items))
    }

    if (offlineMode) {
        // Offline mode ON: the downloaded-only home replaces the whole body, exactly as in the classic
        // Home. It is its own inventory section (10.1) and is reused verbatim.
        DownloadedOnlyView(navController = navController)
        return
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .auraScreenBackground(bloom, intensity = 1f),
    ) {
        PullToRefreshBox(
            state = pullRefreshState,
            isRefreshing = isRefreshing,
            onRefresh = viewModel::refresh,
            indicator = {
                AuraPullRefreshIndicator(
                    state = pullRefreshState,
                    isRefreshing = isRefreshing,
                )
            },
        ) {
            LazyColumn(
                state = listState,
                contentPadding = LocalPlayerAwareWindowInsets.current.asPaddingValues(),
                // TV/car: keep the D-pad's place vertically the same way the classic Home does
                // (`HomeScreen.kt:1137`). No-op on touch.
                modifier = Modifier.fillMaxSize().tvFocusRestorer(),
            ) {
                // ── Why every item carries `animateItem()` ────────────────────────────────────────
                // The shelves of this screen arrive ASYNCHRONOUSLY (quickPicks, homePage, dailyMixes,
                // explorePage each land at their own time) and `computeHomeSections` reorders them, so
                // the list inserts and moves whole blocks while the user is watching. Without a
                // placement animation each of those is a hard cut, which is most of why the screen felt
                // cheap on launch. `animateItem()`'s defaults are the same
                // `spring(dampingRatio = NoBouncy, stiffness = MediumLow)` AuraMotion.standard() is —
                // plus the IntOffset visibility threshold a placement spec needs — and are what the
                // classic Home already uses in its 34 call sites, so this decides nothing new.
                // Every item below has a stable `key`; animateItem does nothing without one.
                item(key = "aura_home_header") {
                    AuraScreenHeader(
                        modifier = Modifier.animateItem(),
                        label = auraGreeting(),
                        title = stringResource(R.string.home),
                        // The global top bar is no longer drawn on this route (it was a second, opaque
                        // header stacked over this one). Its four actions — Escuchar juntos, Historial,
                        // Modo sin conexión and Cuenta — live here now; see [LocalAuraTopActions].
                        trailing = { AuraTopActions() },
                    )
                }

                // Chips de estado de ánimo de YouTube (dynamic text; only when YouTube returns chips).
                homePage?.chips?.takeIf { it.isNotEmpty() }?.let { chips ->
                    item(key = "aura_home_chips") {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = AuraSpacing.Gutter),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier
                                .animateItem()
                                .padding(top = 10.dp)
                                .tvFocusRestorer(),
                        ) {
                            items(chips, key = { it.title }) { chip ->
                                AuraChip(
                                    text = chip.title,
                                    selected = selectedChip == chip,
                                    onClick = { viewModel.toggleChip(chip) },
                                )
                            }
                        }
                    }
                }

                if (selectedChip != null) {
                    if (isChipLoading) {
                        item(key = "aura_home_chip_loading") {
                            ShimmerHost(modifier = Modifier.animateItem()) {
                                AuraHomeShelfSkeleton(cardScale = cardScale)
                                repeat(4) { AuraDetailSkeletonRow() }
                            }
                        }
                    }
                    chipError?.let { message ->
                        item(key = "aura_home_chip_error") {
                            AuraTechnicalText(
                                text = message,
                                color = AuraPalette.OnGroundMuted,
                                modifier = Modifier
                                    .animateItem()
                                    .padding(
                                        horizontal = AuraSpacing.Gutter,
                                        vertical = 16.dp,
                                    ),
                            )
                        }
                    }
                    homePage?.sections.orEmpty().forEachIndexed { index, sectionData ->
                        item(key = "aura_mood_section_$index") {
                            val sectionSongs = sectionData.items.filterIsInstance<SongItem>()
                            val isSongsOnly = sectionData.items.isNotEmpty() &&
                                sectionData.items.all { it is SongItem }
                            val preferVideoShelf = sectionSongs.any { it.isVideoSong }
                            Column(Modifier.animateItem()) {
                                AuraSectionHeader(
                                    title = sectionData.title,
                                    label = sectionData.label,
                                    onPlayAll = if (sectionSongs.isNotEmpty()) {
                                        {
                                            playerConnection.playQueue(
                                                ListQueue(
                                                    title = sectionData.title,
                                                    items = sectionSongs.map {
                                                        it.toMediaMetadata().toMediaItem()
                                                    },
                                                ),
                                            )
                                        }
                                    } else null,
                                )
                                if (isSongsOnly && !preferVideoShelf) {
                                    Column(
                                        verticalArrangement = Arrangement.spacedBy(2.dp),
                                        modifier = Modifier.padding(
                                            horizontal = AuraSpacing.Gutter,
                                            vertical = AuraSpacing.SectionGap,
                                        ),
                                    ) {
                                        sectionSongs.distinctBy { it.id }.take(8).forEach { song ->
                                            val dbSong by database.song(song.id).collectAsState(initial = null)
                                            AuraSongRow(
                                                title = song.title,
                                                subtitle = song.artists.joinToString { it.name },
                                                thumbnailUrl = song.thumbnail,
                                                seed = song.id,
                                                isActive = song.id == mediaMetadata?.id,
                                                isPlaying = isPlaying,
                                                explicit = song.explicit,
                                                playedInShuffle = (dbSong?.song?.totalPlayTime ?: 0L) > 0L,
                                                onClick = {
                                                    if (song.id == mediaMetadata?.id) {
                                                        playerConnection.togglePlayPause()
                                                    } else {
                                                        playerConnection.playQueue(
                                                            YouTubeQueue.radio(song.toMediaMetadata()),
                                                        )
                                                    }
                                                },
                                                onLongClick = { ytItemMenu(song) },
                                                onMenuClick = { ytItemMenu(song) },
                                            )
                                        }
                                    }
                                } else {
                                    AuraGroupedYtItemShelves(
                                        items = sectionData.items,
                                        cardScale = cardScale,
                                        isPlaying = isPlaying,
                                        activeId = mediaMetadata?.id,
                                        activeAlbumId = mediaMetadata?.album?.id,
                                        onClick = openYtItem,
                                        onLongClick = ytItemMenu,
                                    )
                                }
                            }
                        }
                    }
                } else {

                // Tus podcasts (fijados)
                if (pinnedPodcasts.isNotEmpty()) {
                    item(key = "aura_home_podcasts") {
                        Column(Modifier.animateItem()) {
                            AuraSectionHeader(title = stringResource(R.string.home_your_podcasts))
                            AuraShelf {
                                items(pinnedPodcasts, key = { it.id }) { show ->
                                    val visual = auraTypeVisual(AuraContentKind.Podcast)
                                    AuraCoverCard(
                                        title = show.title,
                                        thumbnailUrl = show.artworkUrl,
                                        seed = show.id,
                                        width = visual.shelfWidth * cardScale,
                                        ratio = visual.ratio,
                                        shape = visual.shape,
                                        onClick = {
                                            navController.navigate(
                                                "podcasts?feedUrl=" +
                                                    java.net.URLEncoder.encode(show.feedUrl, "UTF-8"),
                                            )
                                        },
                                    )
                                }
                            }
                        }
                    }
                }

                // Reproducido recientemente — pinned outside homeSections, exactly as in the classic Home.
                recentlyPlayed?.takeIf { it.isNotEmpty() }?.let { recentSongs ->
                    item(key = "aura_home_recent") {
                        val recentTitle = stringResource(R.string.home_recently_played)
                        Column(Modifier.animateItem()) {
                            AuraSectionHeader(
                                title = recentTitle,
                                onPlayAll = { playAllSongs(recentTitle, recentSongs) },
                            )
                            AuraShelf {
                                items(recentSongs.distinctBy { it.id }, key = { it.id }) { song ->
                                    AuraSongShelfCard(
                                        song = song,
                                        cardScale = cardScale,
                                        isActive = song.id == mediaMetadata?.id,
                                        isPlaying = isPlaying,
                                        onClick = { playSong(song) },
                                        onLongClick = { songMenu(song) },
                                    )
                                }
                            }
                        }
                    }
                }

                // Cold home: onboarding/import done but never played — taste shelves stay empty on purpose.
                val tasteShelvesEmpty = quickPicks.isNullOrEmpty() &&
                    dailyMixes.isNullOrEmpty() &&
                    similarRecommendations.isNullOrEmpty() &&
                    recentlyPlayed.isNullOrEmpty() &&
                    keepListening.isNullOrEmpty() &&
                    forgottenFavorites.isNullOrEmpty()
                if (!isLoading && !hasPlayHistory && tasteShelvesEmpty) {
                    item(key = "aura_home_cold_empty") {
                        Column(
                            modifier = Modifier
                                .animateItem()
                                .fillMaxWidth()
                                .padding(horizontal = AuraSpacing.Gutter, vertical = 56.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(64.dp)
                                    .clip(CircleShape)
                                    .background(AuraPalette.SurfaceFill)
                                    .border(1.dp, AuraPalette.SurfaceLine, CircleShape),
                                contentAlignment = Alignment.Center,
                            ) {
                                AuraIconGlyph(
                                    icon = AuraIcons.Music,
                                    contentDescription = null,
                                    size = 28.dp,
                                    tint = AuraPalette.Teal,
                                )
                            }
                            Text(
                                text = stringResource(R.string.home_cold_empty_title),
                                style = AuraType.ContentSection,
                                color = AuraPalette.OnGround,
                            )
                            Text(
                                text = stringResource(R.string.home_cold_empty_hint),
                                style = AuraType.RowSubtitle,
                                color = AuraPalette.OnGroundMuted,
                            )
                        }
                    }
                }

                homeSections.forEach { section ->
                    when (section) {
                        HomeSection.SpeedDial -> {
                            speedDialItems.takeIf { it.isNotEmpty() }?.let { items ->
                                item(key = "aura_speed_dial") {
                                    Column(Modifier.animateItem()) {
                                        AuraSectionHeader(
                                            title = stringResource(R.string.speed_dial),
                                            accent = AuraPalette.Teal,
                                            leading = {
                                                AuraIconGlyph(
                                                    icon = AuraIcons.Pin,
                                                    contentDescription = null,
                                                    size = 18.dp,
                                                    tint = AuraPalette.Teal,
                                                )
                                            },
                                        )
                                        // Playlists the user pinned to Home — no song backfill, no random tile.
                                        val pinW = auraTypeVisual(AuraContentKind.Playlist).shelfWidth * cardScale
                                        AuraDoubleRowShelf(
                                            rowHeight = auraShelfCardStackHeight(pinW),
                                        ) {
                                            lazyGridItems(items, key = { it.id }) { item ->
                                                AuraTypedYtCoverCard(
                                                    item = item,
                                                    cardScale = cardScale,
                                                    isActive = item.id == mediaMetadata?.id ||
                                                        item.id == mediaMetadata?.album?.id,
                                                    isPlaying = isPlaying,
                                                    onClick = { openYtItem(item) },
                                                    onLongClick = { ytItemMenu(item) },
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        HomeSection.QuickPicks -> {
                            quickPicks?.takeIf { it.isNotEmpty() }?.let { picks ->
                                item(key = "aura_quick_picks") {
                                    val forYouTitle = stringResource(R.string.home_for_you)
                                    val distinctQuickPicks = remember(picks) { picks.distinctBy { it.id } }
                                    Column(Modifier.animateItem()) {
                                        Spacer(Modifier.height(AuraSpacing.SectionGap))
                                        AuraSectionHeader(
                                            title = forYouTitle,
                                            label = stringResource(R.string.quick_picks),
                                            accent = AuraPalette.Teal,
                                            onPlayAll = { playAllSongs(forYouTitle, picks) },
                                        )
                                        if (perfOn) {
                                            // High-performance mode keeps the light path the classic Home also
                                            // keeps (HomeScreen.kt:1486): a plain shelf, no mask, no snapping,
                                            // no long-press menu. The masked hero carousel is the heaviest
                                            // visual on the screen and perf mode exists to skip exactly that.
                                            AuraShelf {
                                                items(distinctQuickPicks, key = { it.id }) { originalSong ->
                                                    val song by database.song(originalSong.id)
                                                        .collectAsState(initial = originalSong)
                                                    val current = song ?: originalSong
                                                    AuraSongShelfCard(
                                                        song = current,
                                                        cardScale = cardScale,
                                                        isActive = current.id == mediaMetadata?.id,
                                                        isPlaying = isPlaying,
                                                        onClick = { playSong(current) },
                                                        onLongClick = null,
                                                    )
                                                }
                                            }
                                        } else {
                                            // Stronger hero: ~classic editorial presence (was 168 dp).
                                            // Still square — Para ti is taste songs; video shelves below
                                            // own the 16:9 language.
                                            AuraQuickPicksCarousel(
                                                songs = distinctQuickPicks,
                                                itemSize = 208.dp * cardScale,
                                                activeId = mediaMetadata?.id,
                                                isPlaying = isPlaying,
                                                onClick = playSong,
                                                onLongClick = songMenu,
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        HomeSection.FromTheCommunity -> {
                            communityPlaylists?.takeIf { it.isNotEmpty() }?.let { playlists ->
                                item(key = "aura_community") {
                                    Column(Modifier.animateItem()) {
                                        AuraSectionHeader(title = stringResource(R.string.from_the_community))
                                        LazyRow(
                                            contentPadding = PaddingValues(horizontal = AuraSpacing.Gutter),
                                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                                            modifier = Modifier
                                                .padding(top = AuraSpacing.SectionGap)
                                                .tvFocusRestorer(),
                                        ) {
                                            items(playlists, key = { it.playlist.id }) { item ->
                                                CommunityPlaylistCard(
                                                    item = item,
                                                    onClick = {
                                                        navController.navigate(
                                                            "online_playlist/${item.playlist.id.removePrefix("VL")}",
                                                        )
                                                    },
                                                    onSongClick = { song ->
                                                        playerConnection.playQueue(
                                                            YouTubeQueue(
                                                                song.endpoint ?: WatchEndpoint(videoId = song.id),
                                                                song.toMediaMetadata(),
                                                            ),
                                                        )
                                                    },
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        is HomeSection.DailyMix -> {
                            dailyMixes?.getOrNull(section.index)?.takeIf { it.items.isNotEmpty() }?.let { mix ->
                                item(key = "aura_daily_mix_${section.index}") {
                                    val title = stringResource(R.string.home_daily_mix, section.index + 1)
                                    val seedLine = stringResource(
                                        R.string.daily_discover_because_you_listen_to,
                                        "${mix.seed.title} • ${mix.seed.artists.joinToString(", ") { it.name }}",
                                    )
                                    Column(Modifier.animateItem()) {
                                        AuraSectionHeader(
                                            title = title,
                                            label = seedLine,
                                            onPlayAll = {
                                                val queueItems = mix.items
                                                    .mapNotNull { (it.recommendation as? SongItem)?.toMediaMetadata() }
                                                    .map { it.toMediaItem() }
                                                if (queueItems.isNotEmpty()) {
                                                    playerConnection.playQueue(
                                                        ListQueue(title = title, items = queueItems),
                                                    )
                                                }
                                            },
                                        )
                                        AuraShelf {
                                            items(mix.items, key = { it.recommendation.id }) { entry ->
                                                val yt = entry.recommendation
                                                AuraTypedYtCoverCard(
                                                    item = yt,
                                                    cardScale = cardScale,
                                                    isActive = yt.id == mediaMetadata?.id ||
                                                        yt.id == mediaMetadata?.album?.id,
                                                    isPlaying = isPlaying,
                                                    onClick = {
                                                        when (yt) {
                                                            is SongItem -> {
                                                                playerConnection.playQueue(
                                                                    YouTubeQueue(
                                                                        yt.endpoint
                                                                            ?: WatchEndpoint(videoId = yt.id),
                                                                        yt.toMediaMetadata(),
                                                                    ),
                                                                )
                                                            }
                                                            else -> openYtItem(yt)
                                                        }
                                                    },
                                                    onLongClick = if (perfOn) null else ({ ytItemMenu(yt) }),
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        HomeSection.KeepListening -> {
                            keepListening?.takeIf { it.isNotEmpty() }?.let { items ->
                                item(key = "aura_keep_listening") {
                                    val klTitle = stringResource(R.string.keep_listening)
                                    Column(Modifier.animateItem()) {
                                        AuraSectionHeader(
                                            title = klTitle,
                                            onPlayAll = {
                                                playAllSongs(klTitle, items.filterIsInstance<Song>())
                                            },
                                        )
                                        val klGroups = remember(items) {
                                            items.groupedByAuraKind { auraContentKind(it) }
                                        }
                                        val showKlLabels = klGroups.size > 1
                                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                            klGroups.forEach { (kind, group) ->
                                                if (showKlLabels) {
                                                    AuraSectionLabel(
                                                        text = auraTypeLabel(kind).uppercase(),
                                                        modifier = Modifier.padding(
                                                            start = AuraSpacing.Gutter,
                                                            end = AuraSpacing.Gutter,
                                                            top = 10.dp,
                                                        ),
                                                    )
                                                }
                                                AuraShelf {
                                                    items(group, key = { it.id }) { item ->
                                                        val visual = auraTypeVisual(item)
                                                        AuraCoverCard(
                                                            title = auraLocalTitle(item),
                                                            subtitle = auraLocalSubtitle(item),
                                                            thumbnailUrl = auraLocalThumbnail(item),
                                                            seed = item.id,
                                                            width = visual.shelfWidth * cardScale,
                                                            ratio = visual.ratio,
                                                            shape = visual.shape,
                                                            isActive = item.id == mediaMetadata?.id,
                                                            isPlaying = isPlaying,
                                                            onClick = {
                                                                when (item) {
                                                                    is Song -> playSong(item)
                                                                    is Album -> navController.navigate("album/${item.id}")
                                                                    is Artist -> navController.navigate("artist/${item.id}")
                                                                    else -> Unit
                                                                }
                                                            },
                                                            badge = if (visual.kind == AuraContentKind.Video) {
                                                                {
                                                                    Box(
                                                                        modifier = Modifier
                                                                            .align(Alignment.TopStart)
                                                                            .padding(8.dp)
                                                                            .clip(AuraShapes.Pill)
                                                                            .background(AuraPalette.Ground.copy(alpha = 0.72f))
                                                                            .padding(horizontal = 7.dp, vertical = 4.dp),
                                                                    ) {
                                                                        Row(
                                                                            verticalAlignment = Alignment.CenterVertically,
                                                                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                                                                        ) {
                                                                            AuraIconGlyph(
                                                                                icon = visual.icon,
                                                                                contentDescription = visual.label,
                                                                                size = 11.dp,
                                                                                tint = AuraPalette.Teal,
                                                                            )
                                                                            Text(
                                                                                text = visual.label,
                                                                                style = AuraType.QualityBadge,
                                                                                color = AuraPalette.Teal,
                                                                                maxLines = 1,
                                                                            )
                                                                        }
                                                                    }
                                                                }
                                                            } else null,
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        HomeSection.AccountPlaylists -> {
                            accountPlaylists?.takeIf { it.isNotEmpty() }?.let { playlists ->
                                item(key = "aura_account_playlists") {
                                    Column(Modifier.animateItem()) {
                                        AuraSectionHeader(
                                            title = stringResource(R.string.your_youtube_playlists),
                                            label = accountName,
                                            onClick = { navController.navigate("account") },
                                        )
                                        val playlistW = auraTypeVisual(AuraContentKind.Playlist).shelfWidth * cardScale
                                        AuraDoubleRowShelf(
                                            rowHeight = auraShelfCardStackHeight(playlistW),
                                        ) {
                                            lazyGridItems(playlists.distinctBy { it.id }, key = { it.id }) { item ->
                                                AuraTypedYtCoverCard(
                                                    item = item,
                                                    cardScale = cardScale,
                                                    onClick = { openYtItem(item) },
                                                    onLongClick = { ytItemMenu(item) },
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        HomeSection.NewFromArtists -> Unit
                        HomeSection.NewReleases -> Unit

                        HomeSection.TimeOfDayMix -> {
                            timeOfDayMix?.takeIf { it.songs.isNotEmpty() }?.let { mix ->
                                item(key = "aura_time_of_day_mix") {
                                    val mixTitle = stringResource(
                                        when (mix.bucket) {
                                            0 -> R.string.home_mix_morning
                                            1 -> R.string.home_mix_afternoon
                                            else -> R.string.home_mix_night
                                        },
                                    )
                                    Column(Modifier.animateItem()) {
                                        AuraSectionHeader(
                                            title = mixTitle,
                                            onPlayAll = { playAllSongs(mixTitle, mix.songs) },
                                        )
                                        AuraShelf {
                                            items(mix.songs.distinctBy { it.id }, key = { it.id }) { song ->
                                                AuraSongShelfCard(
                                                    song = song,
                                                    cardScale = cardScale,
                                                    isActive = song.id == mediaMetadata?.id,
                                                    isPlaying = isPlaying,
                                                    onClick = { playSong(song) },
                                                    onLongClick = { songMenu(song) },
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        HomeSection.AiRecommended -> {
                            if (!aiRecsEnabled) return@forEach
                            aiRecommendedSongs?.takeIf { it.isNotEmpty() }?.let { recommended ->
                                item(key = "aura_ai_recommended") {
                                    val recEntity = aiRecommendedPlaylist?.playlist
                                    val recTitle = recEntity?.name ?: AutoRecoPlaylistWorker.PLAYLIST_NAME
                                    Column(Modifier.animateItem()) {
                                        AuraSectionHeader(
                                            // The render's own "RECOMENDADO PARA TI · IA" rule, in violet.
                                            title = recTitle,
                                            accent = AuraPalette.Violet,
                                            label = recEntity?.lastUpdateTime?.let { updated ->
                                                "Actualizado: " + DateUtils.getRelativeTimeSpanString(
                                                    updated.atZone(ZoneId.systemDefault()).toInstant()
                                                        .toEpochMilli(),
                                                    System.currentTimeMillis(),
                                                    DateUtils.MINUTE_IN_MILLIS,
                                                )
                                            },
                                            onClick = {
                                                navController.navigate(
                                                    "local_playlist/${AutoRecoPlaylistWorker.PLAYLIST_ID}",
                                                )
                                            },
                                            onPlayAll = { playAllSongs(recTitle, recommended) },
                                        )
                                        // The render draws THIS section as rows, not as a shelf.
                                        Column(
                                            verticalArrangement = Arrangement.spacedBy(2.dp),
                                            modifier = Modifier.padding(
                                                horizontal = AuraSpacing.Gutter,
                                                vertical = AuraSpacing.SectionGap,
                                            ),
                                        ) {
                                            recommended.distinctBy { it.id }.take(6).forEach { song ->
                                                AuraSongRow(
                                                    title = song.song.title,
                                                    subtitle = song.artists.joinToString { it.name },
                                                    thumbnailUrl = song.song.thumbnailUrl,
                                                    seed = song.id,
                                                    isActive = song.id == mediaMetadata?.id,
                                                    isPlaying = isPlaying,
                                                    liked = song.song.liked,
                                                    explicit = song.song.explicit,
                                                    downloadId = song.id,
                                                    format = song.format,
                                                    playedInShuffle = song.song.totalPlayTime > 0L,
                                                    swipeMediaItem = song.toMediaItem(),
                                                    onClick = { playSong(song) },
                                                    onLongClick = { songMenu(song) },
                                                    onMenuClick = { songMenu(song) },
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        HomeSection.GenreMix -> {
                            genreMix?.takeIf { it.songs.isNotEmpty() }?.let { mix ->
                                item(key = "aura_genre_mix") {
                                    val mixTitle = stringResource(R.string.home_genre_mix, mix.genre)
                                    Column(Modifier.animateItem()) {
                                        AuraSectionHeader(
                                            title = mixTitle,
                                            onPlayAll = { playAllSongs(mixTitle, mix.songs) },
                                        )
                                        AuraShelf {
                                            items(mix.songs.distinctBy { it.id }, key = { it.id }) { song ->
                                                AuraSongShelfCard(
                                                    song = song,
                                                    cardScale = cardScale,
                                                    isActive = song.id == mediaMetadata?.id,
                                                    isPlaying = isPlaying,
                                                    onClick = { playSong(song) },
                                                    onLongClick = { songMenu(song) },
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        HomeSection.ForgottenFavorites -> {
                            forgottenFavorites?.takeIf { it.isNotEmpty() }?.let { favorites ->
                                item(key = "aura_forgotten_favorites") {
                                    val title = stringResource(R.string.forgotten_favorites)
                                    val distinctFavorites = remember(favorites) { favorites.distinctBy { it.id } }
                                    val visibleFavorites = if (forgottenExpanded) {
                                        distinctFavorites
                                    } else {
                                        distinctFavorites.take(8)
                                    }
                                    Column(Modifier.animateItem()) {
                                        AuraSectionHeader(
                                            title = title,
                                            onClick = if (distinctFavorites.size > 8) {
                                                { forgottenExpanded = !forgottenExpanded }
                                            } else {
                                                null
                                            },
                                            onPlayAll = { playAllSongs(title, favorites) },
                                        )
                                        Column(
                                            verticalArrangement = Arrangement.spacedBy(2.dp),
                                            modifier = Modifier.padding(
                                                horizontal = AuraSpacing.Gutter,
                                                vertical = AuraSpacing.SectionGap,
                                            ),
                                        ) {
                                            visibleFavorites.forEach { originalSong ->
                                                val song by database.song(originalSong.id)
                                                    .collectAsState(initial = originalSong)
                                                val current = song ?: originalSong
                                                AuraSongRow(
                                                    title = current.song.title,
                                                    subtitle = current.artists.joinToString { it.name },
                                                    thumbnailUrl = current.song.thumbnailUrl,
                                                    seed = current.id,
                                                    isActive = current.id == mediaMetadata?.id,
                                                    isPlaying = isPlaying,
                                                    liked = current.song.liked,
                                                    explicit = current.song.explicit,
                                                    inLibrary = current.song.inLibrary != null,
                                                    downloadId = current.id,
                                                    format = current.format,
                                                    playedInShuffle = current.song.totalPlayTime > 0L,
                                                    swipeMediaItem = current.toMediaItem(),
                                                    onClick = { playSong(current) },
                                                    onLongClick = { songMenu(current) },
                                                    onMenuClick = { songMenu(current) },
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        is HomeSection.SimilarRecommendation -> {
                            similarRecommendations?.getOrNull(section.index)?.let { recommendation ->
                                item(key = "aura_similar_${section.index}") {
                                    // Only offer a destination that exists — a song with no album falls
                                    // back to its artist; with neither, the header is not clickable.
                                    val similarDest: String? = when (val t = recommendation.title) {
                                        is Song -> t.album?.id?.let { "album/$it" }
                                            ?: t.artists.firstOrNull()?.id?.let { "artist/$it" }

                                        is Album -> "album/${t.id}"
                                        is Artist -> "artist/${t.id}"
                                        is Playlist -> null
                                    }
                                    Column(Modifier.animateItem()) {
                                        AuraSectionHeader(
                                            title = stringResource(R.string.similar_to),
                                            label = recommendation.title.title,
                                            onClick = similarDest?.let { d -> { navController.navigate(d) } },
                                            onPlayAll = {
                                                val items = recommendation.items
                                                    .filterIsInstance<SongItem>()
                                                    .map { it.toMediaMetadata().toMediaItem() }
                                                if (items.isNotEmpty()) {
                                                    playerConnection.playQueue(
                                                        ListQueue(
                                                            title = recommendation.title.title,
                                                            items = items,
                                                        ),
                                                    )
                                                }
                                            },
                                            leading = {
                                                AuraCover(
                                                    thumbnailUrl = recommendation.title.thumbnailUrl,
                                                    size = 34.dp,
                                                    seed = recommendation.title.id,
                                                    shape = if (recommendation.title is Artist) CircleShape
                                                    else AuraShapes.Artwork,
                                                )
                                            },
                                        )
                                        AuraGroupedYtItemShelves(
                                            items = recommendation.items,
                                            cardScale = cardScale,
                                            isPlaying = isPlaying,
                                            activeId = mediaMetadata?.id,
                                            activeAlbumId = mediaMetadata?.album?.id,
                                            onClick = openYtItem,
                                            onLongClick = ytItemMenu,
                                        )
                                    }
                                }
                            }
                        }

                        is HomeSection.HomePageSection -> {
                            homePage?.sections?.getOrNull(section.index)?.let { sectionData ->
                                item(key = "aura_yt_section_${section.index}") {
                                    val sectionSongs = sectionData.items.filterIsInstance<SongItem>()
                                    val isSongsOnly = sectionData.items.isNotEmpty() &&
                                        sectionData.items.all { it is SongItem }
                                    // Video tracks need the 16:9 typed shelf — not the compact song rows.
                                    val preferVideoShelf = sectionSongs.any { it.isVideoSong }
                                    Column(Modifier.animateItem()) {
                                        AuraSectionHeader(
                                            title = sectionData.title,
                                            label = sectionData.label,
                                            onClick = sectionData.endpoint?.let { endpoint ->
                                                {
                                                    when {
                                                        endpoint.browseId == "FEmusic_moods_and_genres" ->
                                                            navController.navigate("mood_and_genres")

                                                        endpoint.params != null ->
                                                            navController.navigate(
                                                                "youtube_browse/${endpoint.browseId}?params=${endpoint.params}",
                                                            )

                                                        else ->
                                                            navController.navigate("browse/${endpoint.browseId}")
                                                    }
                                                }
                                            },
                                            onPlayAll = if (sectionSongs.isNotEmpty()) {
                                                {
                                                    playerConnection.playQueue(
                                                        ListQueue(
                                                            title = sectionData.title,
                                                            items = sectionSongs.map {
                                                                it.toMediaMetadata().toMediaItem()
                                                            },
                                                        ),
                                                    )
                                                }
                                            } else null,
                                        )
                                        if (isSongsOnly && !preferVideoShelf) {
                                            Column(
                                                verticalArrangement = Arrangement.spacedBy(2.dp),
                                                modifier = Modifier.padding(
                                                    horizontal = AuraSpacing.Gutter,
                                                    vertical = AuraSpacing.SectionGap,
                                                ),
                                            ) {
                                                sectionSongs.distinctBy { it.id }.take(8).forEach { song ->
                                                    val dbSong by database.song(song.id).collectAsState(initial = null)
                                                    AuraSongRow(
                                                        title = song.title,
                                                        subtitle = song.artists.joinToString { it.name },
                                                        thumbnailUrl = song.thumbnail,
                                                        seed = song.id,
                                                        isActive = song.id == mediaMetadata?.id,
                                                        isPlaying = isPlaying,
                                                        explicit = song.explicit,
                                                        playedInShuffle = (dbSong?.song?.totalPlayTime ?: 0L) > 0L,
                                                        onClick = {
                                                            if (song.id == mediaMetadata?.id) {
                                                                playerConnection.togglePlayPause()
                                                            } else {
                                                                playerConnection.playQueue(
                                                                    YouTubeQueue.radio(song.toMediaMetadata()),
                                                                )
                                                            }
                                                        },
                                                        onLongClick = { ytItemMenu(song) },
                                                        onMenuClick = { ytItemMenu(song) },
                                                    )
                                                }
                                            }
                                        } else {
                                            // Mixed artists / videos / albums / songs: group by kind so
                                            // 16:9 and squares never share one grid (owner: desorden + huecos).
                                            AuraGroupedYtItemShelves(
                                                items = sectionData.items,
                                                cardScale = cardScale,
                                                isPlaying = isPlaying,
                                                activeId = mediaMetadata?.id,
                                                activeAlbumId = mediaMetadata?.album?.id,
                                                onClick = openYtItem,
                                                onLongClick = ytItemMenu,
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        HomeSection.MoodAndGenres -> {
                            explorePage?.moodAndGenres?.let { moodAndGenres ->
                                item(key = "aura_mood_and_genres") {
                                    Column(Modifier.animateItem()) {
                                        AuraSectionHeader(
                                            title = stringResource(R.string.mood_and_genres),
                                            onClick = { navController.navigate("mood_and_genres") },
                                        )
                                        LazyRow(
                                            contentPadding = PaddingValues(horizontal = AuraSpacing.Gutter),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            modifier = Modifier
                                                .padding(top = AuraSpacing.SectionGap)
                                                .tvFocusRestorer(),
                                        ) {
                                            items(moodAndGenres, key = { it.title }) {
                                                MoodAndGenresButton(
                                                    title = it.title,
                                                    onClick = {
                                                        navController.navigate(
                                                            "youtube_browse/${it.endpoint.browseId}?params=${it.endpoint.params}",
                                                        )
                                                    },
                                                    modifier = Modifier.width(180.dp * cardScale),
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Initial load: shimmer shelves instead of a bare "CARGANDO…" label. Once any
                // section is on screen, pull-to-refresh covers further loads — no cheap footer.
                if (isLoading && homeSections.isEmpty()) {
                    item(key = "aura_home_skeleton") {
                        ShimmerHost(modifier = Modifier.animateItem()) {
                            AuraHomeShelfSkeleton(cardScale = cardScale)
                            AuraHomeShelfSkeleton(cardScale = cardScale)
                            repeat(5) { AuraDetailSkeletonRow() }
                        }
                    }
                }

                } // selectedChip == null — normal home shelves

                item(key = "aura_home_bottom_spacer") {
                    Spacer(
                        Modifier
                            .animateItem()
                            .height(30.dp),
                    )
                }
            }
        }

        // No internet + the network home never loaded: offer to continue offline (downloads only).
        if (!isLoading && homePage == null && !isInternetAvailable(context)) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(AuraPalette.Ground)
                    .padding(32.dp),
                contentAlignment = Alignment.Center,
            ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = stringResource(R.string.home_offline_no_internet),
                    style = AuraType.RowTitle,
                    color = AuraPalette.OnGround,
                )
                Text(
                    text = stringResource(R.string.home_offline_downloads_hint),
                    style = AuraType.RowSubtitle,
                    color = AuraPalette.OnGroundMuted,
                )
                Spacer(Modifier.height(4.dp))
                Box(
                    modifier = Modifier
                        // Drawn at the render's pill height, touched at 48 dp — see the sort chip in
                        // AuraLibraryTabs for the same treatment.
                        .minimumInteractiveComponentSize()
                        .clip(AuraShapes.Pill)
                        .background(AuraPalette.PlayButtonGradient)
                        .auraClickableInternal(
                            onClick = { offlineMode = true },
                            contentDescription = stringResource(R.string.home_offline_continue),
                        )
                        .padding(horizontal = 22.dp, vertical = 12.dp),
                ) {
                    Text(
                        text = stringResource(R.string.home_offline_continue),
                        style = AuraType.Chip,
                        color = AuraPalette.OnAccent,
                    )
                }
            }
            }
        }
    }
}

// ── Home-local pieces ─────────────────────────────────────────────────────────────────────────────

/**
 * Mixed YT shelves (Similar / Home sections): group by kind so videos, songs, albums and artists
 * never share one DoubleRow grid — mixed cell heights left huge empty gaps between cards.
 */
@Composable
private fun AuraGroupedYtItemShelves(
    items: List<YTItem>,
    cardScale: Float,
    isPlaying: Boolean,
    activeId: String?,
    activeAlbumId: String?,
    onClick: (YTItem) -> Unit,
    onLongClick: (YTItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    val groups = remember(items) { items.groupedByAuraKind { auraContentKind(it) } }
    val showGroupLabels = groups.size > 1
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        groups.forEach { (kind, group) ->
            if (group.isEmpty()) return@forEach
            if (showGroupLabels) {
                AuraSectionLabel(
                    text = auraTypeLabel(kind).uppercase(),
                    modifier = Modifier.padding(
                        start = AuraSpacing.Gutter,
                        end = AuraSpacing.Gutter,
                        top = 10.dp,
                    ),
                )
            }
            val visual = auraTypeVisual(kind)
            val scale = if (kind == AuraContentKind.Video) cardScale * 1.12f else cardScale
            when (kind) {
                AuraContentKind.Album, AuraContentKind.Ep, AuraContentKind.Single,
                AuraContentKind.Playlist, AuraContentKind.Artist, AuraContentKind.Podcast,
                -> {
                    if (group.size >= 4) {
                        val w = visual.shelfWidth * scale
                        AuraDoubleRowShelf(
                            rowHeight = auraShelfCardStackHeight(w, visual.ratio),
                        ) {
                            lazyGridItems(group, key = { it.id }) { item ->
                                AuraTypedYtCoverCard(
                                    item = item,
                                    cardScale = scale,
                                    isActive = item.id == activeId || item.id == activeAlbumId,
                                    isPlaying = isPlaying,
                                    onClick = { onClick(item) },
                                    onLongClick = { onLongClick(item) },
                                )
                            }
                        }
                    } else {
                        AuraShelf {
                            items(group, key = { it.id }) { item ->
                                AuraTypedYtCoverCard(
                                    item = item,
                                    cardScale = scale,
                                    isActive = item.id == activeId || item.id == activeAlbumId,
                                    isPlaying = isPlaying,
                                    onClick = { onClick(item) },
                                    onLongClick = { onLongClick(item) },
                                )
                            }
                        }
                    }
                }
                else -> {
                    AuraShelf {
                        items(group, key = { it.id }) { item ->
                            AuraTypedYtCoverCard(
                                item = item,
                                cardScale = scale,
                                isActive = item.id == activeId || item.id == activeAlbumId,
                                isPlaying = isPlaying,
                                onClick = { onClick(item) },
                                onLongClick = { onLongClick(item) },
                            )
                        }
                    }
                }
            }
        }
    }
}

/** The render's horizontal shelf: gutter-aligned, 8 px gaps, sitting under a section rule. */
@Composable
internal fun AuraShelf(
    modifier: Modifier = Modifier,
    content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit,
) {
    val (listState, fling) = rememberAuraShelfFlingBehavior()
    LazyRow(
        state = listState,
        flingBehavior = fling,
        contentPadding = PaddingValues(horizontal = AuraSpacing.Gutter),
        horizontalArrangement = Arrangement.spacedBy(AuraSpacing.ShelfItemGap),
        // TV/car: the classic Home carries this on all 20 of its scrollers (e.g. `HomeScreen.kt:1214`),
        // so the D-pad returns to the card it left instead of dropping focus when a shelf scrolls
        // sideways. One call here covers every shelf of this screen. No-op on touch.
        modifier = modifier
            .padding(top = AuraSpacing.SectionGap)
            .passVerticalToParent(listState)
            .tvFocusRestorer(),
        content = content,
    )
}

/**
 * The bottom-weighted scrim the hero text sits on, so a white title stays legible on a pale cover.
 * Held at file scope and not built inside the item: a carousel holds several items at once and this
 * would otherwise be a fresh [Brush] per item per composition, which is exactly the kind of allocation
 * the standing heat/battery gate exists to stop. The stops are the classic hero's
 * (`HomeScreen.kt:1596-1602`) unchanged.
 */
private val AuraHeroScrim: Brush = Brush.verticalGradient(
    colors = listOf(Color.Transparent, Color.Transparent, Color.Black.copy(alpha = 0.7f)),
)

/**
 * "Para ti" — the masked hero carousel, restored.
 *
 * ## Why this is not an [AuraShelf]
 * The first cut of this screen drew "Para ti" as a flat [AuraShelf] of [AuraSongShelfCard]s. That is a
 * different COMPONENT, not a smaller one: a plain `LazyRow` has no centring, no snap, and above all no
 * mask — and the mask is the whole effect. `maskClip`/`maskBorder` are what make the card at the edge
 * of the viewport squeeze and the centred one open out as you scroll; that deformation is what the
 * owner means by "el carrusel de antes". So the classic component comes back verbatim
 * (`HomeScreen.kt:1518-1650`) and only its MEASUREMENTS and COLOURS become the redesign's:
 * `168.dp * cardScale` square instead of `(maxWidth*0.5f)` × 260.dp, [AuraShapes.Card] instead of
 * `shapes.extraLarge`, [AuraPalette.SurfaceLine] instead of `outlineVariant`.
 *
 * ## The re-key is load-bearing, not decoration
 * `HorizontalCenteredHeroCarousel` is experimental and lays out BLANK when `pageCount` changes under a
 * live `CarouselState` — and `quickPicksDisplay` re-emits a re-filtered list two or three times after
 * first paint, so the count DOES change while the user is looking at it. `key(songs.size)` rebuilds a
 * fresh `CarouselState` on every size change, which is the only reason the shelf is not intermittently
 * empty. Do not "simplify" it away; the classic Home carries the same guard for the same reason.
 *
 * The text is drawn OVER the image (that is why the item needs no height beyond its own square) on the
 * same bottom-weighted scrim the classic hero uses, so a white title stays legible on a pale cover.
 *
 * Size: the call site passes a larger editorial width (~208 dp × cardScale) than the first Aura cut
 * (168 dp) so "Para ti" reads as a hero, not another shelf of postage stamps. Do not shrink it back
 * without an owner ask — that was the "Inicio no se siente premium" complaint.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun AuraQuickPicksCarousel(
    songs: List<Song>,
    itemSize: androidx.compose.ui.unit.Dp,
    activeId: String?,
    isPlaying: Boolean,
    onClick: (Song) -> Unit,
    onLongClick: (Song) -> Unit,
    modifier: Modifier = Modifier,
) {
    val database = LocalDatabase.current
    // Read once for the whole carousel instead of once per item: the value is identical for every item
    // and this keeps the per-item composable call count down (standing heat gate).
    val isTvOrCar = rememberIsTvOrCar()
    key(songs.size) {
        HorizontalCenteredHeroCarousel(
            state = rememberCarouselState { songs.size },
            maxItemWidth = itemSize,
            itemSpacing = 8.dp,
            contentPadding = PaddingValues(horizontal = AuraSpacing.Gutter),
            modifier = modifier
                .fillMaxWidth()
                .padding(top = AuraSpacing.SectionGap)
                .height(itemSize)
                // TV/car: hand focus back to the hero the D-pad last stood on when it re-enters, so the
                // ring does not drop to the container root while the carousel scrolls. No-op on touch.
                .tvFocusRestorer(),
        ) { index ->
            // The item count and the list are read from the same composition, but a carousel that is
            // mid-recomposition can still ask for an index the new list no longer has.
            val originalSong = songs.getOrNull(index)
            if (originalSong != null) {
                val song by database.song(originalSong.id).collectAsState(initial = originalSong)
                val current = song ?: originalSong
                val isActive = current.id == activeId
                val placeholder = remember(current.id) { AuraPalette.coverPlaceholder(current.id) }
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        // TV/car: the ring goes BEFORE maskClip so it draws OUTSIDE the carousel's clip —
                        // placed after it, the stroke is cut down to a sliver. It observes the
                        // `.focusable()` below. Same order and reason as the classic hero
                        // (`HomeScreen.kt:1552-1558`).
                        .tvFocusable(isTvOrCar, AuraShapes.Card, scaleFocused = 1f)
                        .maskClip(AuraShapes.Card)
                        .maskBorder(BorderStroke(1.dp, AuraPalette.SurfaceLine), AuraShapes.Card)
                        .background(placeholder)
                        .focusable()
                        .combinedClickable(
                            onClick = { onClick(current) },
                            // What tapping does, spoken by TalkBack. `AuraCoverCard` — the card every
                            // other Home shelf uses — passes `contentDescription` here, which defaults
                            // to the title (`AuraContent.kt:355, 368`); the hero now says the same.
                            onClickLabel = current.song.title,
                            onLongClick = { onLongClick(current) },
                        ),
                ) {
                    current.song.thumbnailUrl?.let { url ->
                        AsyncImage(
                            // Decode for the larger hero frame (~208 dp); 640 keeps edges sharp without
                            // the full-res heat of a 1280 source on every carousel item.
                            model = url.resize(640, 640),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(AuraHeroScrim),
                    )
                    if (isActive && isPlaying) {
                        AuraPlayingBars(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(10.dp),
                        )
                    }
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(12.dp),
                    ) {
                        Text(
                            text = current.song.title,
                            style = AuraType.RowTitle,
                            color = AuraPalette.OnGround,
                            maxLines = 1,
                            overflow = AuraDefaultOverflow,
                        )
                        Text(
                            text = current.artists.joinToString { it.name },
                            style = AuraType.RowSubtitle,
                            color = AuraPalette.OnGroundMuted,
                            maxLines = 1,
                            overflow = AuraDefaultOverflow,
                        )
                    }
                }
            }
        }
    }
}

/** A local [Song] as a shelf card — videos use 16:9 (same language as artist shelves). */
@Composable
internal fun AuraSongShelfCard(
    song: Song,
    isActive: Boolean,
    isPlaying: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    cardScale: Float = 1f,
    onLongClick: (() -> Unit)? = null,
) {
    val visual = auraTypeVisual(song)
    AuraCoverCard(
        title = song.song.title,
        subtitle = song.artists.joinToString { it.name },
        thumbnailUrl = song.song.thumbnailUrl,
        seed = song.id,
        width = visual.shelfWidth * cardScale,
        ratio = visual.ratio,
        shape = visual.shape,
        isActive = isActive,
        isPlaying = isPlaying,
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = modifier,
        badge = if (visual.kind == AuraContentKind.Video) {
            {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp)
                        .clip(AuraShapes.Pill)
                        .background(AuraPalette.Ground.copy(alpha = 0.72f))
                        .padding(horizontal = 7.dp, vertical = 4.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        AuraIconGlyph(
                            icon = visual.icon,
                            contentDescription = visual.label,
                            size = 11.dp,
                            tint = AuraPalette.Teal,
                        )
                        Text(
                            text = visual.label,
                            style = AuraType.QualityBadge,
                            color = AuraPalette.Teal,
                            maxLines = 1,
                        )
                    }
                }
            }
        } else null,
    )
}

/**
 * "Botón aleatorio": the five-dot tile that plays / opens something at random. Tapping it WHILE it is
 * loading cancels the pending pick — the same behaviour the inventory records for the classic tile.
 */
@Composable
private fun AuraRandomizeTile(
    isLoading: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    width: androidx.compose.ui.unit.Dp = 118.dp,
) {
    Box(
        modifier = modifier
            .width(width)
            .padding(vertical = 4.dp),
    ) {
        Box(
            modifier = Modifier
                .size(width)
                .clip(AuraShapes.Artwork)
                .background(AuraPalette.SurfaceFill)
                .border(1.dp, AuraPalette.SurfaceLine, AuraShapes.Artwork)
                .auraClickableInternal(
                    onClick = onClick,
                    contentDescription = stringResource(R.string.shuffle),
                ),
            contentAlignment = Alignment.Center,
        ) {
            AuraIconGlyph(
                icon = AuraIcons.Shuffle,
                contentDescription = null,
                size = 26.dp,
                tint = if (isLoading) AuraPalette.OnGroundDisabled else AuraPalette.Teal,
            )
        }
    }
}

/** "BUENOS DÍAS / BUENAS TARDES / BUENAS NOCHES" — the render's greeting rule. New UI string. */
@Composable
private fun auraGreeting(): String = remember(LocalTime.now().hour) {
    when (LocalTime.now().hour) {
        in 5..11 -> "BUENOS DÍAS"
        in 12..19 -> "BUENAS TARDES"
        else -> "BUENAS NOCHES"
    }
}

@Composable
internal fun AuraTypedYtCoverCard(
    item: YTItem,
    cardScale: Float,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isActive: Boolean = false,
    isPlaying: Boolean = false,
    onLongClick: (() -> Unit)? = null,
    badge: (@Composable BoxScope.() -> Unit)? = null,
    fillBleed: Boolean = true,
) {
    // Premium identity from [auraTypeVisual]: Apple release sizes + YTM 16:9 videos / soft playlists.
    val visual = auraTypeVisual(item)
    val width = visual.shelfWidth * cardScale
    val density = androidx.compose.ui.platform.LocalDensity.current
    val decodeTo = with(density) { (width.toPx() * 1.5f).toInt().coerceIn(128, 512) }
    AuraCoverCard(
        title = item.title,
        subtitle = auraYtSubtitle(item),
        thumbnailUrl = item.thumbnail,
        seed = item.id,
        width = width,
        ratio = visual.ratio,
        shape = visual.shape,
        isActive = isActive,
        isPlaying = isPlaying,
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = modifier,
        fillBleed = fillBleed,
        decodeTo = decodeTo,
        badge = {
            badge?.invoke(this)
            // Hybrid badge: videos/EP/Single show a YTM text pill; albums/playlists/artists
            // stay icon-only so Apple-scale art is not covered in chrome.
            Box(
                modifier = Modifier
                    .align(
                        if (visual.kind == AuraContentKind.Video) Alignment.TopStart
                        else Alignment.BottomStart,
                    )
                    .padding(8.dp)
                    .clip(AuraShapes.Pill)
                    .background(AuraPalette.Ground.copy(alpha = 0.72f))
                    .padding(
                        horizontal = if (visual.badgeShowsLabel) 7.dp else 6.dp,
                        vertical = if (visual.badgeShowsLabel) 4.dp else 6.dp,
                    ),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    AuraIconGlyph(
                        icon = visual.icon,
                        contentDescription = visual.label,
                        size = 11.dp,
                        tint = if (visual.kind == AuraContentKind.Video) AuraPalette.Teal
                        else AuraPalette.OnGround,
                    )
                    if (visual.badgeShowsLabel) {
                        Text(
                            text = visual.label,
                            style = AuraType.QualityBadge,
                            color = if (visual.kind == AuraContentKind.Video) AuraPalette.Teal
                            else AuraPalette.OnGround,
                            maxLines = 1,
                        )
                    }
                }
            }
        },
    )
}

private fun auraYtSubtitle(item: YTItem): String? {
    // Apple-style under-title: artist / year only — type lives on the badge, not repeated here.
    return when (item) {
        is SongItem -> item.artists.joinToString { it.name }.takeIf { it.isNotBlank() }
        is AlbumItem -> listOfNotNull(
            item.artists?.joinToString { it.name }?.takeIf { it.isNotBlank() },
            item.year?.toString(),
        ).joinToString(" · ").takeIf { it.isNotBlank() }
        is PlaylistItem -> item.author?.name
        is ArtistItem -> null
    }
}

private fun auraLocalTitle(item: LocalItem): String = when (item) {
    is Song -> item.song.title
    is Album -> item.album.title
    is Artist -> item.artist.name
    is Playlist -> item.playlist.name
}

private fun auraLocalSubtitle(item: LocalItem): String? = when (item) {
    is Song -> item.artists.joinToString { it.name }
    is Album -> item.artists.joinToString { it.name }
    else -> null
}

private fun auraLocalThumbnail(item: LocalItem): String? = when (item) {
    is Song -> item.song.thumbnailUrl
    is Album -> item.album.thumbnailUrl
    is Artist -> item.artist.thumbnailUrl
    is Playlist -> item.playlist.thumbnailUrl
}

/** Initial / mood-chip loading: cover shelf placeholders matching typed album width. */
@Composable
private fun AuraHomeShelfSkeleton(cardScale: Float) {
    val cover = 156.dp * cardScale
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = AuraSpacing.SectionTop),
    ) {
        Spacer(
            Modifier
                .padding(horizontal = AuraSpacing.Gutter)
                .width(160.dp)
                .height(22.dp)
                .clip(AuraShapes.Pill)
                .background(AuraPalette.SurfaceFill),
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(AuraSpacing.ShelfItemGap),
            modifier = Modifier
                .padding(horizontal = AuraSpacing.Gutter, vertical = AuraSpacing.SectionGap),
        ) {
            repeat(3) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Spacer(
                        Modifier
                            .size(cover)
                            .clip(AuraShapes.Artwork)
                            .background(AuraPalette.SurfaceFill),
                    )
                    Spacer(
                        Modifier
                            .width(cover * 0.85f)
                            .height(13.dp)
                            .clip(AuraShapes.Pill)
                            .background(AuraPalette.SurfaceFill),
                    )
                    Spacer(
                        Modifier
                            .width(cover * 0.55f)
                            .height(11.dp)
                            .clip(AuraShapes.Pill)
                            .background(AuraPalette.SurfaceFill),
                    )
                }
            }
        }
    }
}
