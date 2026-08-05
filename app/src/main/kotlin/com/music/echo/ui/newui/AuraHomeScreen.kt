package iad1tya.echo.music.ui.newui

import android.text.format.DateUtils
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState
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
import iad1tya.echo.music.ui.menu.AlbumMenu
import iad1tya.echo.music.ui.menu.ArtistMenu
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
import iad1tya.echo.music.utils.isInternetAvailable
import iad1tya.echo.music.utils.rememberPreference
import iad1tya.echo.music.viewmodels.HomeViewModel
import java.time.LocalTime
import java.time.ZoneId
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

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
    val accountName by viewModel.accountName.collectAsState()

    val isLoading by viewModel.isLoading.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val isRandomizing by viewModel.isRandomizing.collectAsState()

    val perfOn by rememberPreference(HighPerformanceModeKey, false)
    var offlineMode by rememberPreference(OfflineModeKey, false)
    val (randomizeHomeOrder) = rememberPreference(RandomizeHomeOrderKey, false)
    val (showSpeedDial) = rememberPreference(ShowSpeedDialKey, false)
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
    val cardScale = if (homeRichLayout) 1f else 1f / 1.25f

    val pullRefreshState = rememberPullToRefreshState()
    val listState = rememberLazyListState()
    var randomizeJob by remember { mutableStateOf<Job?>(null) }
    var randomSeed by rememberSaveable { mutableLongStateOf(System.currentTimeMillis()) }

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

    val homeSections = remember(
        randomizeHomeOrder, randomSeed, tasteOnlyHome, speedDialItems, quickPicks, dailyMixes,
        timeOfDayMix, aiRecommendedSongs, keepListening, accountPlaylists, forgottenFavorites,
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
            aiRecommendedSongCount = aiRecommendedSongs?.size ?: 0,
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
            is PlaylistItem -> navController.navigate("online_playlist/${item.id}")
        }
    }
    val ytItemMenu: (YTItem) -> Unit = { item ->
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        menuState.show {
            when (item) {
                is SongItem -> YouTubeSongMenu(song = item, navController = navController, onDismiss = menuState::dismiss)
                is AlbumItem -> YouTubeAlbumMenu(albumItem = item, navController = navController, onDismiss = menuState::dismiss)
                is ArtistItem -> YouTubeArtistMenu(artist = item, onDismiss = menuState::dismiss)
                is PlaylistItem -> YouTubePlaylistMenu(playlist = item, coroutineScope = scope, onDismiss = menuState::dismiss)
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
                PullToRefreshDefaults.LoadingIndicator(
                    state = pullRefreshState,
                    isRefreshing = isRefreshing,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(LocalPlayerAwareWindowInsets.current.asPaddingValues()),
                )
            },
        ) {
            LazyColumn(
                state = listState,
                contentPadding = LocalPlayerAwareWindowInsets.current.asPaddingValues(),
                modifier = Modifier.fillMaxSize(),
            ) {
                item(key = "aura_home_header") {
                    AuraScreenHeader(
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
                            modifier = Modifier.padding(top = 10.dp),
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

                // Tus podcasts (fijados)
                if (pinnedPodcasts.isNotEmpty()) {
                    item(key = "aura_home_podcasts") {
                        Column {
                            AuraSectionHeader(title = stringResource(R.string.home_your_podcasts))
                            AuraShelf {
                                items(pinnedPodcasts, key = { it.id }) { show ->
                                    AuraCoverCard(
                                        title = show.title,
                                        thumbnailUrl = show.artworkUrl,
                                        seed = show.id,
                                        width = 118.dp * cardScale,
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
                        Column {
                            AuraSectionHeader(
                                title = recentTitle,
                                onPlayAll = { playAllSongs(recentTitle, recentSongs) },
                            )
                            AuraShelf {
                                items(recentSongs.distinctBy { it.id }, key = { it.id }) { song ->
                                    AuraSongShelfCard(
                                        song = song,
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

                homeSections.forEach { section ->
                    when (section) {
                        HomeSection.SpeedDial -> {
                            speedDialItems.takeIf { it.isNotEmpty() }?.let { items ->
                                item(key = "aura_speed_dial") {
                                    Column {
                                        AuraSectionHeader(title = stringResource(R.string.speed_dial))
                                        AuraShelf {
                                            // "Botón aleatorio de 5 puntos": first cell of the shelf.
                                            // Tapping it WHILE LOADING cancels, as today.
                                            item(key = "aura_speed_dial_random") {
                                                AuraRandomizeTile(
                                                    isLoading = isRandomizing,
                                                    onClick = {
                                                        if (isRandomizing) {
                                                            randomizeJob?.cancel()
                                                        } else {
                                                            randomizeJob = scope.launch {
                                                                viewModel.getRandomItem()?.let(openYtItem)
                                                            }
                                                        }
                                                    },
                                                )
                                            }
                                            items(items, key = { it.id }) { item ->
                                                val isPinned by database.speedDialDao
                                                    .isPinned(item.id)
                                                    .collectAsState(initial = false)
                                                AuraCoverCard(
                                                    title = item.title,
                                                    subtitle = auraYtSubtitle(item),
                                                    thumbnailUrl = item.thumbnail,
                                                    seed = item.id,
                                                    width = 118.dp * cardScale,
                                                    isActive = item.id == mediaMetadata?.id ||
                                                        item.id == mediaMetadata?.album?.id,
                                                    isPlaying = isPlaying,
                                                    onClick = { openYtItem(item) },
                                                    onLongClick = { ytItemMenu(item) },
                                                    badge = {
                                                        if (isPinned) {
                                                            AuraIconGlyph(
                                                                icon = AuraIcons.Check,
                                                                contentDescription = null,
                                                                size = 14.dp,
                                                                tint = AuraPalette.Teal,
                                                                modifier = Modifier
                                                                    .align(Alignment.TopStart)
                                                                    .padding(7.dp),
                                                            )
                                                        }
                                                    },
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
                                    Column {
                                        AuraSectionHeader(
                                            title = forYouTitle,
                                            accent = AuraPalette.Teal.copy(alpha = 0.75f),
                                            onPlayAll = { playAllSongs(forYouTitle, picks) },
                                        )
                                        AuraShelf {
                                            items(picks.distinctBy { it.id }, key = { it.id }) { originalSong ->
                                                val song by database.song(originalSong.id)
                                                    .collectAsState(initial = originalSong)
                                                val current = song ?: originalSong
                                                AuraSongShelfCard(
                                                    song = current,
                                                    // "Para ti" is the hero shelf of the render: the widest card.
                                                    width = (if (perfOn) 118.dp else 168.dp) * cardScale,
                                                    isActive = current.id == mediaMetadata?.id,
                                                    isPlaying = isPlaying,
                                                    onClick = { playSong(current) },
                                                    // Perf mode keeps the light path: no long-press menu, as today.
                                                    onLongClick = if (perfOn) null else ({ songMenu(current) }),
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        HomeSection.FromTheCommunity -> {
                            communityPlaylists?.takeIf { it.isNotEmpty() }?.let { playlists ->
                                item(key = "aura_community") {
                                    Column {
                                        AuraSectionHeader(title = stringResource(R.string.from_the_community))
                                        LazyRow(
                                            contentPadding = PaddingValues(horizontal = AuraSpacing.Gutter),
                                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                                            modifier = Modifier.padding(top = AuraSpacing.SectionGap),
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
                                    Column {
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
                                                val ytSong = entry.recommendation as? SongItem
                                                AuraCoverCard(
                                                    title = entry.recommendation.title,
                                                    subtitle = auraYtSubtitle(entry.recommendation),
                                                    thumbnailUrl = entry.recommendation.thumbnail,
                                                    seed = entry.recommendation.id,
                                                    width = (if (perfOn) 118.dp else 168.dp) * cardScale,
                                                    isActive = entry.recommendation.id == mediaMetadata?.id,
                                                    isPlaying = isPlaying,
                                                    onClick = {
                                                        val mm = ytSong?.toMediaMetadata()
                                                        if (mm != null) {
                                                            playerConnection.playQueue(
                                                                YouTubeQueue(
                                                                    ytSong.endpoint
                                                                        ?: WatchEndpoint(videoId = ytSong.id),
                                                                    mm,
                                                                ),
                                                            )
                                                        }
                                                    },
                                                    onLongClick = if (perfOn || ytSong == null) null
                                                    else ({ ytItemMenu(ytSong) }),
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
                                    Column {
                                        AuraSectionHeader(
                                            title = klTitle,
                                            onPlayAll = {
                                                playAllSongs(klTitle, items.filterIsInstance<Song>())
                                            },
                                        )
                                        AuraShelf {
                                            items(items, key = { it.id }) { item ->
                                                AuraCoverCard(
                                                    title = auraLocalTitle(item),
                                                    subtitle = auraLocalSubtitle(item),
                                                    thumbnailUrl = auraLocalThumbnail(item),
                                                    seed = item.id,
                                                    width = 118.dp * cardScale,
                                                    shape = if (item is Artist) CircleShape else AuraShapes.Artwork,
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
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        HomeSection.AccountPlaylists -> {
                            accountPlaylists?.takeIf { it.isNotEmpty() }?.let { playlists ->
                                item(key = "aura_account_playlists") {
                                    Column {
                                        AuraSectionHeader(
                                            title = stringResource(R.string.your_youtube_playlists),
                                            label = accountName,
                                            onClick = { navController.navigate("account") },
                                        )
                                        AuraShelf {
                                            items(playlists.distinctBy { it.id }, key = { it.id }) { item ->
                                                AuraCoverCard(
                                                    title = item.title,
                                                    subtitle = auraYtSubtitle(item),
                                                    thumbnailUrl = item.thumbnail,
                                                    seed = item.id,
                                                    width = 140.dp * cardScale,
                                                    onClick = { openYtItem(item) },
                                                    onLongClick = { ytItemMenu(item) },
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        HomeSection.NewFromArtists -> {
                            newFromArtists?.takeIf { it.isNotEmpty() }?.let { albums ->
                                item(key = "aura_new_from_artists") {
                                    Column {
                                        AuraSectionHeader(title = stringResource(R.string.home_new_from_artists))
                                        AuraShelf {
                                            items(albums.distinctBy { it.id }, key = { it.id }) { item ->
                                                AuraCoverCard(
                                                    title = item.title,
                                                    subtitle = auraYtSubtitle(item),
                                                    thumbnailUrl = item.thumbnail,
                                                    seed = item.id,
                                                    width = 140.dp * cardScale,
                                                    isActive = item.id == mediaMetadata?.album?.id,
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

                        HomeSection.NewReleases -> {
                            explorePage?.newReleaseAlbums?.takeIf { it.isNotEmpty() }?.let { albums ->
                                item(key = "aura_new_releases") {
                                    Column {
                                        AuraSectionHeader(title = stringResource(R.string.new_release_albums))
                                        AuraShelf {
                                            items(albums.distinctBy { it.id }, key = { it.id }) { item ->
                                                AuraCoverCard(
                                                    title = item.title,
                                                    subtitle = auraYtSubtitle(item),
                                                    thumbnailUrl = item.thumbnail,
                                                    seed = item.id,
                                                    width = 140.dp * cardScale,
                                                    isActive = item.id == mediaMetadata?.album?.id,
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
                                    Column {
                                        AuraSectionHeader(
                                            title = mixTitle,
                                            onPlayAll = { playAllSongs(mixTitle, mix.songs) },
                                        )
                                        AuraShelf {
                                            items(mix.songs.distinctBy { it.id }, key = { it.id }) { song ->
                                                AuraSongShelfCard(
                                                    song = song,
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
                            aiRecommendedSongs?.takeIf { it.isNotEmpty() }?.let { recommended ->
                                item(key = "aura_ai_recommended") {
                                    val recEntity = aiRecommendedPlaylist?.playlist
                                    val recTitle = recEntity?.name ?: AutoRecoPlaylistWorker.PLAYLIST_NAME
                                    Column {
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
                                    Column {
                                        AuraSectionHeader(
                                            title = mixTitle,
                                            onPlayAll = { playAllSongs(mixTitle, mix.songs) },
                                        )
                                        AuraShelf {
                                            items(mix.songs.distinctBy { it.id }, key = { it.id }) { song ->
                                                AuraSongShelfCard(
                                                    song = song,
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
                                    Column {
                                        AuraSectionHeader(
                                            title = title,
                                            onPlayAll = { playAllSongs(title, favorites) },
                                        )
                                        Column(
                                            verticalArrangement = Arrangement.spacedBy(2.dp),
                                            modifier = Modifier.padding(
                                                horizontal = AuraSpacing.Gutter,
                                                vertical = AuraSpacing.SectionGap,
                                            ),
                                        ) {
                                            favorites.distinctBy { it.id }.take(8).forEach { originalSong ->
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
                                    Column {
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
                                        AuraShelf {
                                            items(recommendation.items, key = { it.id }) { item ->
                                                AuraCoverCard(
                                                    title = item.title,
                                                    subtitle = auraYtSubtitle(item),
                                                    thumbnailUrl = item.thumbnail,
                                                    seed = item.id,
                                                    width = 140.dp * cardScale,
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

                        is HomeSection.HomePageSection -> {
                            homePage?.sections?.getOrNull(section.index)?.let { sectionData ->
                                item(key = "aura_yt_section_${section.index}") {
                                    val sectionSongs = sectionData.items.filterIsInstance<SongItem>()
                                    val isSongsOnly = sectionData.items.isNotEmpty() &&
                                        sectionData.items.all { it is SongItem }
                                    Column {
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
                                        if (isSongsOnly) {
                                            Column(
                                                verticalArrangement = Arrangement.spacedBy(2.dp),
                                                modifier = Modifier.padding(
                                                    horizontal = AuraSpacing.Gutter,
                                                    vertical = AuraSpacing.SectionGap,
                                                ),
                                            ) {
                                                sectionSongs.distinctBy { it.id }.take(8).forEach { song ->
                                                    AuraSongRow(
                                                        title = song.title,
                                                        subtitle = song.artists.joinToString { it.name },
                                                        thumbnailUrl = song.thumbnail,
                                                        seed = song.id,
                                                        isActive = song.id == mediaMetadata?.id,
                                                        isPlaying = isPlaying,
                                                        explicit = song.explicit,
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
                                            AuraShelf {
                                                items(sectionData.items, key = { it.id }) { item ->
                                                    AuraCoverCard(
                                                        title = item.title,
                                                        subtitle = auraYtSubtitle(item),
                                                        thumbnailUrl = item.thumbnail,
                                                        seed = item.id,
                                                        width = 140.dp * cardScale,
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
                        }

                        HomeSection.MoodAndGenres -> {
                            explorePage?.moodAndGenres?.let { moodAndGenres ->
                                item(key = "aura_mood_and_genres") {
                                    Column {
                                        AuraSectionHeader(
                                            title = stringResource(R.string.mood_and_genres),
                                            onClick = { navController.navigate("mood_and_genres") },
                                        )
                                        LazyRow(
                                            contentPadding = PaddingValues(horizontal = AuraSpacing.Gutter),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            modifier = Modifier.padding(top = AuraSpacing.SectionGap),
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

                if (isLoading) {
                    item(key = "aura_home_loading") {
                        AuraTechnicalText(
                            text = "CARGANDO…",
                            color = AuraPalette.OnGroundGhost,
                            modifier = Modifier.padding(
                                horizontal = AuraSpacing.Gutter,
                                vertical = 24.dp,
                            ),
                        )
                    }
                }

                item(key = "aura_home_bottom_spacer") {
                    Spacer(Modifier.height(30.dp))
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

/** The render's horizontal shelf: gutter-aligned, 8 px gaps, sitting under a section rule. */
@Composable
private fun AuraShelf(
    modifier: Modifier = Modifier,
    content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit,
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = AuraSpacing.Gutter),
        horizontalArrangement = Arrangement.spacedBy(11.dp),
        modifier = modifier.padding(top = AuraSpacing.SectionGap),
        content = content,
    )
}

/** A local [Song] as a shelf card — the shape every taste row of the render uses. */
@Composable
private fun AuraSongShelfCard(
    song: Song,
    isActive: Boolean,
    isPlaying: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    width: androidx.compose.ui.unit.Dp = 118.dp,
    onLongClick: (() -> Unit)? = null,
) {
    AuraCoverCard(
        title = song.song.title,
        subtitle = song.artists.joinToString { it.name },
        thumbnailUrl = song.song.thumbnailUrl,
        seed = song.id,
        width = width,
        isActive = isActive,
        isPlaying = isPlaying,
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = modifier,
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

private fun auraYtSubtitle(item: YTItem): String? = when (item) {
    is SongItem -> item.artists.joinToString { it.name }
    is AlbumItem -> item.artists?.joinToString { it.name }
    is PlaylistItem -> item.author?.name
    is ArtistItem -> null
    else -> null
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
