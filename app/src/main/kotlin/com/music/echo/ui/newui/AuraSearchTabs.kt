package iad1tya.echo.music.ui.newui

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import iad1tya.echo.music.LocalPlayerAwareWindowInsets
import iad1tya.echo.music.LocalPlayerConnection
import iad1tya.echo.music.R
import iad1tya.echo.music.constants.GridItemSize
import iad1tya.echo.music.constants.GridItemsSizeKey
import iad1tya.echo.music.constants.SuggestionRegionKey
import iad1tya.echo.music.constants.SuggestionRegionSlugToName
import iad1tya.echo.music.ui.component.LocalMenuState
import iad1tya.echo.music.ui.menu.YouTubeAlbumMenu
import iad1tya.echo.music.ui.screens.search.suggestions.SuggestionAlbum
import iad1tya.echo.music.ui.screens.search.suggestions.SuggestionArtist
import iad1tya.echo.music.ui.screens.search.suggestions.SuggestionMatch
import iad1tya.echo.music.ui.screens.search.suggestions.SuggestionTrack
import iad1tya.echo.music.ui.screens.search.suggestions.SuggestionsViewModel
import iad1tya.echo.music.utils.rememberEnumPreference
import iad1tya.echo.music.utils.rememberPreference
import iad1tya.echo.music.utils.systemRegionCode
import iad1tya.echo.music.viewmodels.ExploreViewModel
import iad1tya.echo.music.viewmodels.MoodAndGenresViewModel
import kotlin.math.max

/**
 * # The three content tabs of the new Buscar
 *
 * Explorar (estados de ánimo y géneros), Sugerencias (las listas de Apple Music / YouTube) y Álbum
 * (novedades). Each one keeps the SAME view model, the SAME endpoints and the SAME retry as its
 * classic counterpart — only the drawing changed.
 */

// ── Shared furniture ──────────────────────────────────────────────────────────────────────────────

/** The loading line the whole redesign uses. Cheap, no spinner, no per-frame work. */
@Composable
private fun AuraLoadingLine(modifier: Modifier = Modifier) {
    AuraTechnicalText(
        text = "CARGANDO…",
        color = AuraPalette.OnGroundGhost,
        modifier = modifier.padding(horizontal = AuraSpacing.Gutter, vertical = 24.dp),
    )
}

/** A "Reintentar" / "Actualizar" pill in the new language. */
@Composable
private fun AuraPillAction(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .sizeIn(minHeight = AuraSpacing.MinTouchTarget)
            .clip(AuraShapes.Pill)
            .background(AuraPalette.SurfaceFill)
            .border(1.dp, AuraPalette.SurfaceLine, AuraShapes.Pill)
            .auraClickableInternal(onClick = onClick, contentDescription = label)
            .padding(horizontal = 22.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = AuraType.Chip,
            color = AuraPalette.Teal,
            maxLines = 1,
            overflow = AuraDefaultOverflow,
        )
    }
}

/** An empty / failed state with one action under it. */
@Composable
private fun AuraStateWithAction(
    text: String,
    actionLabel: String,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = AuraSpacing.Gutter, vertical = 48.dp),
    ) {
        Text(
            text = text,
            style = AuraType.RowTitle,
            color = AuraPalette.OnGroundMuted,
            textAlign = TextAlign.Center,
            maxLines = 4,
            overflow = AuraDefaultOverflow,
        )
        AuraPillAction(label = actionLabel, onClick = onAction)
    }
}

/** The rank marker the charts carry ("#1"), drawn as a technical badge over the cover. */
@Composable
private fun AuraRankBadge(rank: Int, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .padding(6.dp)
            .clip(AuraShapes.Pill)
            .background(AuraPalette.Ground.copy(alpha = 0.72f))
            .padding(horizontal = 7.dp, vertical = 3.dp),
    ) {
        AuraTechnicalText(text = "#$rank", color = AuraPalette.Teal)
    }
}

// ── 1. Explorar ───────────────────────────────────────────────────────────────────────────────────

/**
 * Estados de ánimo y géneros. Same `MoodAndGenresViewModel`, same `youtube_browse` destination with
 * the same `browseId`/`params`, same retry.
 */
@Composable
fun AuraExploreTab(
    navController: NavController,
    viewModel: MoodAndGenresViewModel = hiltViewModel(),
) {
    val sections by viewModel.moodAndGenres.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    LazyColumn(
        contentPadding = LocalPlayerAwareWindowInsets.current.asPaddingValues(),
        modifier = Modifier.fillMaxSize(),
    ) {
        // KEYS BY POSITION, NOT BY CONTENT. The previous key was
        // "aura_explore_row_<section title>_<first entry's browseId>", and it CRASHED the app the moment
        // the user opened Buscar: every entry under "Estados de ánimo y momentos" carries the SAME
        // browseId (FEmusic_moods_and_genres_category) and differs only in `params`, so from the second
        // chunk on the key repeated and Compose threw IllegalArgumentException ("Key ... was already
        // used") during draw. Two sections sharing a title would have done the same to the headers.
        // Section index + row index cannot collide whatever the server returns, and they are stable
        // across recomposition (this list never reorders), which is what `animateItem` needs.
        sections?.forEachIndexed { sectionIndex, section ->
            item(key = "aura_explore_header_$sectionIndex") {
                AuraSectionHeader(title = section.title, modifier = Modifier.animateItem())
            }

            val rows = section.items.chunked(2)
            itemsIndexed(
                items = rows,
                key = { rowIndex, _ -> "aura_explore_row_${sectionIndex}_$rowIndex" },
            ) { _, row ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier
                        .animateItem()
                        .fillMaxWidth()
                        .padding(horizontal = AuraSpacing.Gutter, vertical = 5.dp),
                ) {
                    row.forEach { entry ->
                        AuraGenreTile(
                            label = entry.title,
                            onClick = {
                                navController.navigate(
                                    "youtube_browse/${entry.endpoint.browseId}?params=${entry.endpoint.params}",
                                )
                            },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    // Keep a lone tile at half width instead of letting it stretch.
                    repeat(2 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }

        if (sections == null) {
            item(key = "aura_explore_state") {
                if (isLoading) {
                    AuraLoadingLine()
                } else {
                    AuraStateWithAction(
                        text = stringResource(R.string.couldnt_load_recommendations),
                        actionLabel = stringResource(R.string.retry),
                        onAction = viewModel::retry,
                    )
                }
            }
        }

        item(key = "aura_explore_bottom_spacer") { Spacer(Modifier.height(24.dp)) }
    }
}

/** A mood/genre entry: a `SurfaceFill` card with a hairline and the label. */
@Composable
private fun AuraGenreTile(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .sizeIn(minHeight = 64.dp)
            .clip(AuraShapes.Card)
            .background(AuraPalette.SurfaceFill)
            .border(1.dp, AuraPalette.SurfaceLine, AuraShapes.Card)
            .auraClickableInternal(onClick = onClick, contentDescription = label)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(
            text = label,
            style = AuraType.Chip,
            color = AuraPalette.OnGround,
            maxLines = 2,
            overflow = AuraDefaultOverflow,
        )
    }
}

// ── 2. Sugerencias ────────────────────────────────────────────────────────────────────────────────

/**
 * The charts tab: YouTube's global top plus Apple Music's songs / albums / artists / videos for the
 * region chosen in Ajustes › Contenido ([SuggestionRegionKey]).
 *
 * Same `SuggestionsViewModel`: the same scrape, the same background pre-resolution of video ids
 * (`prewarm`, so a tap plays instantly instead of doing a search round-trip), the same
 * `playTrack` / `playVideo` / `navigateToAlbum` / `navigateToArtist`, the same pull-to-refresh.
 *
 * **One thing is deliberately dropped: the "plays" counters.** The classic rows print
 * `2_500_000 / (rank + 2)` and `15_000_000 / (rank + 8)` as "1.2M plays" — a number computed from the
 * chart position, not fetched from anywhere. Nothing in this app knows a track's play count on Apple
 * Music, so the new rows show the rank (which IS real) and nothing else.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuraTrendingTab(
    navController: NavController,
    viewModel: SuggestionsViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val playerConnection = LocalPlayerConnection.current

    val tracks by viewModel.suggestionTracks.collectAsState()
    val artists by viewModel.suggestionArtists.collectAsState()
    val albums by viewModel.suggestionAlbums.collectAsState()
    val videos by viewModel.suggestionVideos.collectAsState()
    val youtubeTop by viewModel.youtubeTopTracks.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val isManualLoading by viewModel.isManualLoading.collectAsState()

    val (regionCode) = rememberPreference(key = SuggestionRegionKey, defaultValue = "system")

    LaunchedEffect(regionCode) { viewModel.refresh(regionCode) }

    // Pre-resolve the visible Apple entries in the background, exactly as the classic tab does. Driven
    // by a LaunchedEffect so it is cancelled when the tab leaves composition — no leak, no heat.
    LaunchedEffect(tracks, videos) {
        viewModel.prewarm(tracks.orEmpty().take(29), SuggestionMatch.Kind.SONG)
        viewModel.prewarm(videos.orEmpty(), SuggestionMatch.Kind.VIDEO)
    }

    val appleChartsUrl: (String) -> String = { path ->
        val code = if (regionCode == "system") systemRegionCode() else regionCode.lowercase()
        "https://music.apple.com/$code/charts$path"
    }
    val regionLabel = SuggestionRegionSlugToName[regionCode]

    val everythingEmpty = tracks == null && artists == null && albums == null &&
        videos == null && youtubeTop == null

    val pullState = rememberPullToRefreshState()

    PullToRefreshBox(
        isRefreshing = isManualLoading,
        onRefresh = { viewModel.refresh(regionCode, force = true) },
        state = pullState,
        indicator = {
            AuraPullRefreshIndicator(
                state = pullState,
                isRefreshing = isManualLoading,
            )
        },
        modifier = Modifier.fillMaxSize(),
    ) {
        LazyColumn(
            contentPadding = LocalPlayerAwareWindowInsets.current.asPaddingValues(),
            modifier = Modifier.fillMaxSize(),
        ) {
            if (isLoading && !isManualLoading && everythingEmpty) {
                item(key = "aura_trending_loading") { AuraLoadingLine() }
            }

            youtubeTop?.takeIf { it.isNotEmpty() }?.let { list ->
                item(key = "aura_trending_yt") {
                    AuraTrackShelf(
                        title = stringResource(R.string.trending_top_global),
                        label = null,
                        tracks = list,
                        onMore = { uriHandler.openUri("https://music.youtube.com/charts") },
                        onTrack = { track ->
                            Toast.makeText(
                                context,
                                "Cargando ${track.title}...",
                                Toast.LENGTH_SHORT,
                            ).show()
                            viewModel.playTrack(track, playerConnection)
                        },
                    )
                }
            }

            tracks?.takeIf { it.isNotEmpty() }?.let { list ->
                item(key = "aura_trending_apple") {
                    AuraTrackShelf(
                        title = stringResource(R.string.trending_apple_top),
                        label = regionLabel,
                        tracks = list,
                        onMore = { uriHandler.openUri(appleChartsUrl("")) },
                        onTrack = { track ->
                            Toast.makeText(
                                context,
                                "Cargando ${track.title}...",
                                Toast.LENGTH_SHORT,
                            ).show()
                            viewModel.playTrack(track, playerConnection)
                        },
                    )
                }
            }

            albums?.takeIf { it.isNotEmpty() }?.let { list ->
                item(key = "aura_trending_albums") {
                    AuraAlbumShelf(
                        title = stringResource(R.string.trending_albums),
                        albums = list,
                        onMore = { uriHandler.openUri(appleChartsUrl("/albums")) },
                        onAlbum = { album ->
                            Toast.makeText(
                                context,
                                "Cargando ${album.title}...",
                                Toast.LENGTH_SHORT,
                            ).show()
                            viewModel.navigateToAlbum(album, navController)
                        },
                    )
                }
            }

            artists?.takeIf { it.isNotEmpty() }?.let { list ->
                item(key = "aura_trending_artists") {
                    AuraArtistShelf(
                        title = stringResource(R.string.trending_artists),
                        artists = list,
                        onArtist = { artist ->
                            Toast.makeText(
                                context,
                                "Cargando ${artist.name}...",
                                Toast.LENGTH_SHORT,
                            ).show()
                            viewModel.navigateToArtist(artist, navController)
                        },
                    )
                }
            }

            videos?.takeIf { it.isNotEmpty() }?.let { list ->
                item(key = "aura_trending_videos") {
                    AuraTrackShelf(
                        title = stringResource(R.string.trending_videos),
                        label = null,
                        tracks = list,
                        // Videos keep their native 16:9 frame, as the results list does.
                        ratio = 16f / 9f,
                        cardWidth = 210.dp,
                        onMore = { uriHandler.openUri(appleChartsUrl("/videos")) },
                        onTrack = { video ->
                            Toast.makeText(
                                context,
                                "Cargando video ${video.title}...",
                                Toast.LENGTH_SHORT,
                            ).show()
                            viewModel.playVideo(video, playerConnection)
                        },
                    )
                }
            }

            if (everythingEmpty && !isLoading) {
                item(key = "aura_trending_empty") {
                    AuraStateWithAction(
                        text = stringResource(R.string.trending_none),
                        actionLabel = stringResource(R.string.retry),
                        onAction = { viewModel.refresh(regionCode, force = true) },
                    )
                }
            }

            if (!everythingEmpty) {
                item(key = "aura_trending_source") {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .padding(top = 36.dp, bottom = 12.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        AuraTechnicalText(
                            text = stringResource(R.string.trending_source_apple),
                            color = AuraPalette.OnGroundGhost,
                        )
                    }
                }
            }

            item(key = "aura_trending_bottom_spacer") { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun AuraTrackShelf(
    title: String,
    label: String?,
    tracks: List<SuggestionTrack>,
    onMore: () -> Unit,
    onTrack: (SuggestionTrack) -> Unit,
    modifier: Modifier = Modifier,
    ratio: Float = 1f,
    cardWidth: androidx.compose.ui.unit.Dp = 130.dp,
) {
    Column(modifier) {
        AuraSectionHeader(title = title, label = label, onClick = onMore)
        val (listState, fling) = rememberAuraShelfFlingBehavior()
        LazyRow(
            state = listState,
            flingBehavior = fling,
            contentPadding = PaddingValues(horizontal = AuraSpacing.Gutter),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = AuraSpacing.SectionGap)
                .passVerticalToParent(listState),
        ) {
            items(tracks, key = { "${it.rank}_${it.title}_${it.artist}" }) { track ->
                AuraCoverCard(
                    title = track.title,
                    subtitle = track.artist,
                    thumbnailUrl = track.thumbnailUrl,
                    seed = track.videoId ?: (track.title + track.artist),
                    width = cardWidth,
                    ratio = ratio,
                    onClick = { onTrack(track) },
                    badge = { AuraRankBadge(track.rank, Modifier.align(Alignment.TopStart)) },
                    modifier = Modifier.animateItem(),
                )
            }
            item(key = "more") {
                AuraPillAction(
                    label = stringResource(R.string.trending_see_more),
                    onClick = onMore,
                    modifier = Modifier.padding(top = cardWidth / 2),
                )
            }
        }
    }
}

@Composable
private fun AuraAlbumShelf(
    title: String,
    albums: List<SuggestionAlbum>,
    onMore: () -> Unit,
    onAlbum: (SuggestionAlbum) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        AuraSectionHeader(title = title, onClick = onMore)
        val (listState, fling) = rememberAuraShelfFlingBehavior()
        LazyRow(
            state = listState,
            flingBehavior = fling,
            contentPadding = PaddingValues(horizontal = AuraSpacing.Gutter),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = AuraSpacing.SectionGap)
                .passVerticalToParent(listState),
        ) {
            items(albums, key = { "${it.rank}_${it.title}" }) { album ->
                AuraCoverCard(
                    title = album.title,
                    subtitle = album.artist,
                    thumbnailUrl = album.thumbnailUrl,
                    seed = album.title + album.artist,
                    width = 130.dp,
                    onClick = { onAlbum(album) },
                    badge = { AuraRankBadge(album.rank, Modifier.align(Alignment.TopStart)) },
                    modifier = Modifier.animateItem(),
                )
            }
            item(key = "more") {
                AuraPillAction(
                    label = stringResource(R.string.trending_see_more),
                    onClick = onMore,
                    modifier = Modifier.padding(top = 65.dp),
                )
            }
        }
    }
}

@Composable
private fun AuraArtistShelf(
    title: String,
    artists: List<SuggestionArtist>,
    onArtist: (SuggestionArtist) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        AuraSectionHeader(title = title)
        val (listState, fling) = rememberAuraShelfFlingBehavior()
        LazyRow(
            state = listState,
            flingBehavior = fling,
            contentPadding = PaddingValues(horizontal = AuraSpacing.Gutter),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = AuraSpacing.SectionGap)
                .passVerticalToParent(listState),
        ) {
            items(artists, key = { "${it.rank}_${it.name}" }) { artist ->
                AuraCoverCard(
                    title = artist.name,
                    thumbnailUrl = artist.thumbnailUrl,
                    seed = artist.name,
                    width = 104.dp,
                    shape = CircleShape,
                    onClick = { onArtist(artist) },
                    badge = { AuraRankBadge(artist.rank, Modifier.align(Alignment.BottomEnd)) },
                    modifier = Modifier.animateItem(),
                )
            }
        }
    }
}

// ── 3. Álbum (novedades) ──────────────────────────────────────────────────────────────────────────

/**
 * Novedades. Same `ExploreViewModel`, same `GridItemsSizeKey` size preference, same long-press
 * `YouTubeAlbumMenu`, and the same honest split between "no se pudo cargar" and "no hay novedades".
 */
@Composable
fun AuraNewAlbumsTab(
    navController: NavController,
    viewModel: ExploreViewModel = hiltViewModel(),
) {
    val menuState = LocalMenuState.current
    val haptic = LocalHapticFeedback.current
    val playerConnection = LocalPlayerConnection.current
    val mediaMetadata by (
        playerConnection?.mediaMetadata?.collectAsState()
            ?: remember { mutableStateOf(null) }
        )
    val isPlaying by (
        playerConnection?.isEffectivelyPlaying?.collectAsState()
            ?: remember { mutableStateOf(false) }
        )

    val explorePage by viewModel.explorePage.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val loadFailed by viewModel.loadFailed.collectAsState()
    val albums = explorePage?.newReleaseAlbums

    val gridItemSize by rememberEnumPreference(GridItemsSizeKey, GridItemSize.BIG)

    // isNullOrEmpty (not == null): after a successful-but-empty load the page is non-null with an
    // empty album list — Reintentar must show the loading line again, not freeze on the empty state.
    when {
        isLoading && albums.isNullOrEmpty() -> Box(
            Modifier.fillMaxSize(),
            contentAlignment = Alignment.TopCenter,
        ) { AuraLoadingLine() }

        albums.isNullOrEmpty() -> Box(
            Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            AuraStateWithAction(
                text = stringResource(
                    if (loadFailed) R.string.couldnt_load_new_releases else R.string.no_new_releases,
                ),
                actionLabel = stringResource(R.string.retry),
                onAction = viewModel::retry,
            )
        }

        else -> BoxWithConstraints(Modifier.fillMaxSize()) {
            val spacing = 12.dp
            val minCard = if (gridItemSize == GridItemSize.BIG) 140.dp else 104.dp
            val available = maxWidth - AuraSpacing.Gutter * 2
            val columns = max(2, ((available + spacing) / (minCard + spacing)).toInt())
            val cardWidth = (available - spacing * (columns - 1)) / columns

            LazyVerticalGrid(
                columns = GridCells.Fixed(columns),
                horizontalArrangement = Arrangement.spacedBy(spacing),
                verticalArrangement = Arrangement.spacedBy(spacing),
                contentPadding = PaddingValues(
                    start = AuraSpacing.Gutter,
                    end = AuraSpacing.Gutter,
                    top = 10.dp,
                    bottom = 24.dp +
                        LocalPlayerAwareWindowInsets.current.asPaddingValues()
                            .calculateBottomPadding(),
                ),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(
                    items = albums.distinctBy { it.id },
                    key = { it.id },
                ) { album ->
                    AuraCoverCard(
                        title = album.title,
                        subtitle = album.artists?.joinToString { it.name },
                        thumbnailUrl = album.thumbnail,
                        seed = album.id,
                        width = cardWidth,
                        isActive = mediaMetadata?.album?.id == album.id,
                        isPlaying = isPlaying,
                        onClick = { navController.navigate("album/${album.id}") },
                        onLongClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            menuState.show {
                                YouTubeAlbumMenu(
                                    albumItem = album,
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
