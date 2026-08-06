package iad1tya.echo.music.ui.newui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import com.music.innertube.models.WatchEndpoint
import iad1tya.echo.music.LocalPlayerAwareWindowInsets
import iad1tya.echo.music.LocalPlayerConnection
import iad1tya.echo.music.R
import iad1tya.echo.music.extensions.toMediaItem
import iad1tya.echo.music.models.toMediaMetadata
import iad1tya.echo.music.playback.queues.ListQueue
import iad1tya.echo.music.playback.queues.YouTubeQueue
import iad1tya.echo.music.ui.component.LocalMenuState
import iad1tya.echo.music.ui.menu.AlbumMenu
import iad1tya.echo.music.ui.menu.ArtistMenu
import iad1tya.echo.music.ui.menu.SongMenu
import iad1tya.echo.music.ui.screens.ActivityHistoryBottomSheet
import iad1tya.echo.music.ui.screens.OptionStats
import iad1tya.echo.music.ui.screens.statsPeriodChips
import iad1tya.echo.music.ui.screens.statsPeriodLabel
import iad1tya.echo.music.ui.utils.isScrollingUp
import iad1tya.echo.music.ui.utils.rememberIsTvOrCar
import iad1tya.echo.music.ui.utils.tvFocusRestorer
import iad1tya.echo.music.ui.utils.tvFocusable
import iad1tya.echo.music.utils.joinByBullet
import iad1tya.echo.music.utils.makeTimeString
import iad1tya.echo.music.viewmodels.StatsViewModel
import java.time.LocalDateTime

/**
 * # Estadísticas — "Interfaz nueva"
 *
 * The classic screen is a range selector over three "most played" shelves, a shuffle action and the
 * Resumen de actividad sheet. Every one of those is here, driven by the SAME [StatsViewModel] — the
 * host composes this instead of `StatsScreen` behind the same `NavBackStackEntry`, so it is the same
 * ViewModel instance and the selected range survives toggling the interface in either direction.
 *
 * ## What is reused rather than rebuilt
 *  · **The numbers.** Every count, duration and ordering comes from the ViewModel's flows. Nothing
 *    is recomputed here, so the two skins can never disagree about a metric.
 *  · **The ranges.** The chip sets come from `statsPeriodChips`, which the classic screen now calls
 *    too — see `ui/screens/StatsPeriods.kt`. A second copy of those sequences would have been a
 *    second definition of "this month".
 *  · **The Resumen sheet.** `ActivityHistoryBottomSheet` verbatim: it already reads
 *    [rememberAuraPanelSkin] and restyles itself, and it is a modal surface, not a screen.
 *  · **The menus.** `SongMenu` / `ArtistMenu` / `AlbumMenu`, opened on long-press exactly as today.
 *
 * ## What is redrawn
 * The chrome only: the header (the classic `TopAppBar` is replaced by [AuraDetailHeader] plus the
 * historial action), the range selector (`ChoiceChipsRow` → a mode pill + [AuraChip]s over the same
 * two state values), the three shelves ([AuraCoverCard], as on Inicio) and the shuffle action
 * (`HideOnScrollFAB` → the same hide-on-scroll behaviour in the redesign's pill language).
 *
 * ## Not invented
 * The classic screen has no empty state, no per-section "reproducir todo" and no chart. It shows the
 * counts even when they are zero, and so does this one.
 */
@Composable
fun AuraStatsScreen(
    navController: NavController,
    viewModel: StatsViewModel = hiltViewModel(),
) {
    val menuState = LocalMenuState.current
    val haptic = LocalHapticFeedback.current
    val playerConnection = LocalPlayerConnection.current ?: return
    val isPlaying by playerConnection.isEffectivelyPlaying.collectAsState()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsState()
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val indexChips by viewModel.indexChips.collectAsState()
    val selectedOption by viewModel.selectedOption.collectAsState()
    val mostPlayedSongs by viewModel.mostPlayedSongs.collectAsState()
    val mostPlayedSongsStats by viewModel.mostPlayedSongsStats.collectAsState()
    val mostPlayedArtists by viewModel.mostPlayedArtists.collectAsState()
    val mostPlayedAlbums by viewModel.mostPlayedAlbums.collectAsState()
    val firstEvent by viewModel.firstEvent.collectAsState()

    val totalPlayTime by viewModel.totalPlayTime.collectAsState()
    val allTimePlayTime by viewModel.allTimePlayTime.collectAsState()
    val uniqueSongsCount by viewModel.uniqueSongsCount.collectAsState()
    val uniqueArtistsCount by viewModel.uniqueArtistsCount.collectAsState()
    val uniqueAlbumsCount by viewModel.uniqueAlbumsCount.collectAsState()

    var showHistorySheet by rememberSaveable { mutableStateOf(false) }

    val currentDate = LocalDateTime.now()
    val periodChips = statsPeriodChips(
        option = selectedOption,
        currentDate = currentDate,
        firstEventTimestamp = firstEvent?.event?.timestamp,
    )

    val listState = rememberLazyListState()
    // Estadísticas is a pushed screen with no now-playing context of its own; the brand bloom, dimmed
    // to Ajustes' .32 because this screen is dense with small type.
    val bloom = rememberAuraBloom(mediaId = null)

    val songsLabel = stringResource(R.string.songs)
    val artistsLabel = stringResource(R.string.artists)
    val albumsLabel = stringResource(R.string.albums)
    val shuffleQueueTitle = stringResource(R.string.most_played_songs)

    val insets = LocalPlayerAwareWindowInsets.current
    val bottomClearance = insets
        .only(WindowInsetsSides.Bottom)
        .asPaddingValues()
        .calculateBottomPadding()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .auraScreenBackground(bloom, intensity = 0.32f),
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(insets.only(WindowInsetsSides.Horizontal)),
            // Top = the status bar ONLY, so the shelves scroll under it instead of starting below a
            // 64 dp reserve for a Material bar this screen does not draw. See AuraDetailChrome.
            contentPadding = PaddingValues(
                top = auraStatusBarPadding(),
                bottom = bottomClearance + 96.dp,
            ),
        ) {
            item(key = "aura_stats_header") {
                AuraDetailHeader(
                    title = stringResource(R.string.stats),
                    onBack = navController::navigateUp,
                    modifier = Modifier.animateItem(),
                    trailing = {
                        AuraIconButton(
                            icon = AuraIcons.History,
                            contentDescription = stringResource(R.string.activity_history),
                            onClick = { showHistorySheet = true },
                            size = 20.dp,
                            tint = AuraPalette.OnGroundMuted,
                        )
                    },
                )
            }

            item(key = "aura_stats_period") {
                AuraStatsRangeSelector(
                    option = selectedOption,
                    chips = periodChips,
                    selectedIndex = indexChips,
                    onOptionChange = {
                        // Byte for byte the classic handler: switching the mode resets the index,
                        // because index 0 means something different in every mode.
                        viewModel.selectedOption.value = it
                        viewModel.indexChips.value = 0
                    },
                    onIndexChange = { viewModel.indexChips.value = it },
                    modifier = Modifier.animateItem(),
                )
            }

            item(key = "aura_stats_songs") {
                Column(Modifier.animateItem()) {
                    AuraSectionHeader(title = "${mostPlayedSongsStats.size} $songsLabel")
                    if (mostPlayedSongsStats.isNotEmpty()) {
                        AuraStatsShelf {
                            itemsIndexed(
                                items = mostPlayedSongsStats,
                                key = { _, song -> song.id },
                            ) { index, song ->
                                // The stats row and the full Song row are two projections of the same
                                // query in the same order; `getOrNull` only guards the instant between
                                // the two flows emitting, where the classic screen would have thrown.
                                val original = mostPlayedSongs.getOrNull(index)
                                AuraCoverCard(
                                    title = "${index + 1}. ${song.title}",
                                    subtitle = joinByBullet(
                                        pluralStringResource(
                                            R.plurals.n_time,
                                            song.songCountListened,
                                            song.songCountListened,
                                        ),
                                        makeTimeString(song.timeListened),
                                    ),
                                    thumbnailUrl = song.thumbnailUrl,
                                    seed = song.id,
                                    width = 128.dp,
                                    isActive = song.id == mediaMetadata?.id,
                                    isPlaying = isPlaying,
                                    onClick = {
                                        if (song.id == mediaMetadata?.id) {
                                            playerConnection.togglePlayPause()
                                        } else {
                                            playerConnection.playQueue(
                                                YouTubeQueue(
                                                    endpoint = WatchEndpoint(song.id),
                                                    preloadItem = original?.toMediaMetadata(),
                                                ),
                                            )
                                        }
                                    },
                                    onLongClick = if (original == null) null else ({
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        menuState.show {
                                            SongMenu(
                                                originalSong = original,
                                                navController = navController,
                                                onDismiss = menuState::dismiss,
                                            )
                                        }
                                    }),
                                    modifier = Modifier.animateItem(),
                                )
                            }
                        }
                    }
                }
            }

            item(key = "aura_stats_artists") {
                Column(Modifier.animateItem()) {
                    AuraSectionHeader(title = "${mostPlayedArtists.size} $artistsLabel")
                    if (mostPlayedArtists.isNotEmpty()) {
                        AuraStatsShelf {
                            itemsIndexed(
                                items = mostPlayedArtists,
                                key = { _, artist -> artist.id },
                            ) { index, artist ->
                                AuraCoverCard(
                                    title = "${index + 1}. ${artist.artist.name}",
                                    subtitle = joinByBullet(
                                        pluralStringResource(
                                            R.plurals.n_time,
                                            artist.songCount,
                                            artist.songCount,
                                        ),
                                        makeTimeString(artist.timeListened?.toLong()),
                                    ),
                                    thumbnailUrl = artist.artist.thumbnailUrl,
                                    seed = artist.id,
                                    width = 128.dp,
                                    // Artists are round everywhere in this app, classic and new.
                                    shape = CircleShape,
                                    onClick = { navController.navigate("artist/${artist.id}") },
                                    onLongClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        menuState.show {
                                            ArtistMenu(
                                                originalArtist = artist,
                                                coroutineScope = coroutineScope,
                                                onDismiss = menuState::dismiss,
                                            )
                                        }
                                    },
                                    modifier = Modifier.animateItem(),
                                )
                            }
                        }
                    }
                }
            }

            item(key = "aura_stats_albums") {
                Column(Modifier.animateItem()) {
                    AuraSectionHeader(title = "${mostPlayedAlbums.size} $albumsLabel")
                    if (mostPlayedAlbums.isNotEmpty()) {
                        AuraStatsShelf {
                            itemsIndexed(
                                items = mostPlayedAlbums,
                                key = { _, album -> album.id },
                            ) { index, album ->
                                val played = album.songCountListened ?: 0
                                AuraCoverCard(
                                    title = "${index + 1}. ${album.album.title}",
                                    subtitle = joinByBullet(
                                        pluralStringResource(R.plurals.n_time, played, played),
                                        makeTimeString(album.timeListened),
                                    ),
                                    thumbnailUrl = album.album.thumbnailUrl,
                                    seed = album.id,
                                    width = 128.dp,
                                    isActive = album.id == mediaMetadata?.album?.id,
                                    isPlaying = isPlaying,
                                    onClick = { navController.navigate("album/${album.id}") },
                                    onLongClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        menuState.show {
                                            AlbumMenu(
                                                originalAlbum = album,
                                                navController = navController,
                                                onDismiss = menuState::dismiss,
                                            )
                                        }
                                    },
                                    modifier = Modifier.animateItem(),
                                )
                            }
                        }
                    }
                }
            }
        }

        // "Reproducir en aleatorio las más escuchadas" — the classic HideOnScrollFAB, same condition,
        // same queue, same hide-on-scroll rule, drawn in the redesign's pill language.
        if (mostPlayedSongs.isNotEmpty()) {
            AnimatedVisibility(
                visible = listState.isScrollingUp(),
                enter = slideInVertically { it },
                exit = slideOutVertically { it },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .windowInsetsPadding(
                        insets.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal),
                    ),
            ) {
                AuraStatsShuffleButton(
                    contentDescription = shuffleQueueTitle,
                    onClick = {
                        playerConnection.playQueue(
                            ListQueue(
                                title = context.getString(R.string.most_played_songs),
                                items = mostPlayedSongs
                                    .map { it.toMediaMetadata().toMediaItem() }
                                    .shuffled(),
                            ),
                        )
                    },
                    modifier = Modifier.padding(end = 16.dp, bottom = 24.dp),
                )
            }
        }

        if (showHistorySheet) {
            ActivityHistoryBottomSheet(
                onDismiss = { showHistorySheet = false },
                totalPlayTimeMs = totalPlayTime,
                allTimePlayTimeMs = allTimePlayTime,
                uniqueSongs = uniqueSongsCount,
                uniqueArtists = uniqueArtistsCount,
                uniqueAlbums = uniqueAlbumsCount,
                periodLabel = statsPeriodLabel(periodChips, indexChips),
            )
        }
    }
}

// ── Range selector ────────────────────────────────────────────────────────────────────────────────

/**
 * The classic `ChoiceChipsRow`, in the redesign's language: a pill that opens the mode menu
 * (Continuo / Semanas / Meses / Años) and, beside it, the ranges of that mode as [AuraChip]s.
 *
 * The two state values it writes are the ViewModel's own `selectedOption` and `indexChips` — the same
 * two the classic row writes, so every flow downstream re-collects identically. The menu itself stays
 * a Material `DropdownMenu`: popups are not one of the redesigned surfaces, and the Biblioteca sort
 * control already established that.
 */
@Composable
private fun AuraStatsRangeSelector(
    option: OptionStats,
    chips: List<Pair<Int, String>>,
    selectedIndex: Int,
    onOptionChange: (OptionStats) -> Unit,
    onIndexChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val isTvOrCar = rememberIsTvOrCar()
    var expanded by remember { mutableStateOf(false) }

    val modes = listOf(
        OptionStats.CONTINUOUS to stringResource(R.string.continuous),
        OptionStats.WEEKS to stringResource(R.string.weeks),
        OptionStats.MONTHS to stringResource(R.string.months),
        OptionStats.YEARS to stringResource(R.string.years),
    )
    val currentModeLabel = modes.firstOrNull { it.first == option }?.second.orEmpty()

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 14.dp),
    ) {
        Box(Modifier.padding(start = AuraSpacing.Gutter)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier
                    // Draws at the render's pill height; the finger still gets its 48 dp.
                    .minimumInteractiveComponentSize()
                    .clip(AuraShapes.Pill)
                    .background(AuraPalette.SurfaceFill)
                    .border(1.dp, AuraPalette.SurfaceLine, AuraShapes.Pill)
                    .tvFocusable(isTvOrCar, AuraShapes.Pill, scaleFocused = 1f)
                    .auraClickableInternal(
                        onClick = { expanded = true },
                        contentDescription = "Cambiar el tipo de periodo",
                    )
                    .padding(horizontal = 14.dp, vertical = 9.dp),
            ) {
                Text(
                    text = currentModeLabel,
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
                modes.forEach { (mode, label) ->
                    DropdownMenuItem(
                        text = { Text(label) },
                        onClick = {
                            onOptionChange(mode)
                            expanded = false
                        },
                    )
                }
            }
        }

        LazyRow(
            contentPadding = PaddingValues(start = 10.dp, end = AuraSpacing.Gutter),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .weight(1f)
                .tvFocusRestorer(),
        ) {
            items(
                count = chips.size,
                key = { position -> "${option.name}_${chips[position].first}" },
            ) { position ->
                val (value, label) = chips[position]
                AuraChip(
                    text = label,
                    selected = value == selectedIndex,
                    onClick = { onIndexChange(value) },
                    modifier = Modifier
                        .tvFocusable(isTvOrCar, AuraShapes.Pill, scaleFocused = 1f)
                        .animateItem(),
                )
            }
        }
    }
}

// ── Local pieces ──────────────────────────────────────────────────────────────────────────────────

/** The render's horizontal shelf, as on Inicio: gutter-aligned, 11 dp gaps, D-pad focus restored. */
@Composable
private fun AuraStatsShelf(
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

/** The shuffle action: a `SurfaceFill` circle with a hairline and a teal glyph, like Biblioteca's. */
@Composable
private fun AuraStatsShuffleButton(
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(56.dp)
            .clip(AuraShapes.Pill)
            .background(AuraPalette.SurfaceFill)
            .border(1.dp, AuraPalette.SurfaceLine, AuraShapes.Pill)
            .auraClickableInternal(onClick = onClick, contentDescription = contentDescription),
        contentAlignment = Alignment.Center,
    ) {
        AuraIconGlyph(
            icon = AuraIcons.Shuffle,
            contentDescription = null,
            size = 24.dp,
            tint = AuraPalette.Teal,
        )
    }
}
