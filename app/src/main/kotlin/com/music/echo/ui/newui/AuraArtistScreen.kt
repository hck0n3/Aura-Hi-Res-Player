package iad1tya.echo.music.ui.newui

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import com.music.innertube.models.AlbumItem
import com.music.innertube.pages.ArtistSection
import com.music.innertube.models.ArtistItem
import com.music.innertube.models.PlaylistItem
import com.music.innertube.models.SongItem
import com.music.innertube.models.WatchEndpoint
import com.music.innertube.models.YTItem
import iad1tya.echo.music.LocalDatabase
import iad1tya.echo.music.LocalListenTogetherManager
import iad1tya.echo.music.LocalPlayerAwareWindowInsets
import iad1tya.echo.music.LocalPlayerConnection
import iad1tya.echo.music.R
import iad1tya.echo.music.artistvideo.ArtistVideo
import iad1tya.echo.music.canvas.AppleMusicArtistBackgroundProvider
import iad1tya.echo.music.constants.HideExplicitKey
import iad1tya.echo.music.constants.ShowArtistBackgroundVideoKey
import iad1tya.echo.music.constants.ShowArtistDescriptionKey
import iad1tya.echo.music.constants.ShowArtistSubscriberCountKey
import iad1tya.echo.music.constants.ShowArtistVideoKey
import iad1tya.echo.music.constants.ShowMonthlyListenersKey
import iad1tya.echo.music.db.entities.ArtistEntity
import iad1tya.echo.music.db.entities.Song
import iad1tya.echo.music.extensions.toMediaItem
import iad1tya.echo.music.models.toMediaMetadata
import iad1tya.echo.music.playback.PlayerConnection
import iad1tya.echo.music.playback.queues.ListQueue
import iad1tya.echo.music.playback.queues.YouTubeQueue
import iad1tya.echo.music.ui.component.LinkSegment
import iad1tya.echo.music.ui.component.LocalMenuState
import iad1tya.echo.music.ui.component.MenuState
import iad1tya.echo.music.ui.component.rememberPlayedShuffleSet
import iad1tya.echo.music.ui.component.rememberShuffleMemoryPrompt
import iad1tya.echo.music.ui.component.shimmer.ShimmerHost
import iad1tya.echo.music.ui.menu.AlbumMenu
import iad1tya.echo.music.ui.menu.SongMenu
import iad1tya.echo.music.ui.menu.YouTubeAlbumMenu
import iad1tya.echo.music.ui.menu.YouTubeArtistMenu
import iad1tya.echo.music.ui.menu.YouTubePlaylistMenu
import iad1tya.echo.music.ui.menu.YouTubeSongMenu
import iad1tya.echo.music.ui.screens.artist.ArtistSectionBuffer
import iad1tya.echo.music.ui.utils.rememberIsAppInForeground
import iad1tya.echo.music.ui.utils.rememberIsTvOrCar
import iad1tya.echo.music.ui.utils.tvFocusable
import iad1tya.echo.music.utils.rememberDeviceThrottle
import iad1tya.echo.music.utils.rememberPerfGatedBoolean
import iad1tya.echo.music.utils.rememberPreference
import iad1tya.echo.music.viewmodels.ArtistViewModel
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * # Artista — "Interfaz nueva"
 *
 * The `artist/{artistId}` route. The redesigned player's ARTIST line lands here, so together with
 * `AuraAlbumScreen` this is the pair the owner reaches first after the player itself.
 *
 * ## Same data, same actions
 * One [ArtistViewModel], the classic one, behind the same `NavBackStackEntry`. That matters more here
 * than anywhere else: the ViewModel resolves a locally-created artist row ("LA########") to its real
 * YouTube channel by name, caches the page in memory and on disk, and appends the "Aparece en" and
 * "Videos oficiales" sections asynchronously. None of that is re-implemented — this screen only draws
 * `artistPage`, `libraryArtist`, `allLibrarySongs`, `libraryAlbums`, `artistVideoUrl`
 * and `hasFailed`.
 *
 * The local/online decision is the classic `showLocal` rule verbatim: a REAL local artist, or a
 * generated-id row whose channel could not be resolved, opens on the library view; everything else
 * opens on the YouTube page.
 *
 * ## Everything the classic screen offers
 *  · Suscribirse / Suscrito — the same `database.transaction` (update or insert + `toggleLike`), which
 *    is what keeps the YouTube subscription sync working.
 *  · Radio and Aleatorio from the page's own endpoints; when the screen is LOCAL, Aleatorio becomes
 *    the "AR:" Aleatorio-mejorado shuffle over the artist's FULL local catalogue (`allLibrarySongs`,
 *    never a 4-row page of the catalogue), with the same unplayed-first opener and `startShuffled = true`.
 *  · Escuchar juntos: a guest hides Radio and Aleatorio, exactly as today.
 *  · Suscriptores / oyentes mensuales / descripción — each behind its own existing setting.
 *  · El vídeo de fondo del artista y el vídeo pequeño, behind `ShowArtistBackgroundVideoKey` /
 *    `ShowArtistVideoKey` plus Modo alto rendimiento, the thermal gate and the foreground gate.
 *  · Every section header keeps its "ver todos" — YouTube's `moreEndpoint` when there is one, and
 *    otherwise the already-loaded items through `ArtistSectionBuffer`.
 *  · Every ⋯ and long-press opens the classic `SongMenu` / `AlbumMenu` / `YouTube*Menu` sheets.
 *  · Copiar enlace, the error + Reintentar state and the loading skeleton.
 *
 * ## Keys
 * Section rows are keyed by POSITION (`section index` + `row index`), never by item id: the same song
 * or album legitimately appears in two sections of one artist page ("Canciones populares" and
 * "Aparece en"), and an id-keyed row would then be a duplicate key in a single `LazyColumn` — the
 * crash class that shipped in 0.6.148-beta1.
 *
 * @param scrollBehavior accepted for parity with the classic screen's signature; the redesign draws no
 *   Material `TopAppBar`, so nothing consumes it.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
fun AuraArtistScreen(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
    viewModel: ArtistViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val database = LocalDatabase.current
    val menuState = LocalMenuState.current
    val haptic = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()
    val playerConnection = LocalPlayerConnection.current ?: return
    val isTvOrCar = rememberIsTvOrCar()

    val listenTogetherManager = LocalListenTogetherManager.current
    val isGuest = listenTogetherManager?.isInRoom == true && !listenTogetherManager.isHost

    val isPlaying by playerConnection.isEffectivelyPlaying.collectAsState()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsState()

    val artistPage = viewModel.artistPage
    val libraryArtist by viewModel.libraryArtist.collectAsState()
    val allLibrarySongs by viewModel.allLibrarySongs.collectAsState()
    val libraryAlbums by viewModel.libraryAlbums.collectAsState()
    val artistVideoUrl by viewModel.artistVideoUrl.collectAsState()
    val artistVideoSong by viewModel.artistVideoSong.collectAsState()
    val hasFailed by viewModel.hasFailed.collectAsState()
    val expandedPopularSongs by viewModel.expandedPopularSongs.collectAsState()

    val hideExplicit by rememberPreference(key = HideExplicitKey, defaultValue = false)
    val showArtistDescription by rememberPreference(ShowArtistDescriptionKey, defaultValue = true)
    val showArtistSubscriberCount by rememberPreference(ShowArtistSubscriberCountKey, defaultValue = true)
    val showMonthlyListeners by rememberPreference(ShowMonthlyListenersKey, defaultValue = true)

    // The three gates the classic screen ANDs together, kept term for term: the user's setting (through
    // Modo alto rendimiento), the OS thermal report, and app foreground — the ArtistVideo ExoPlayer does
    // not self-pause off-screen, so leaving composition is what releases it.
    val deviceThrottle = rememberDeviceThrottle()
    val appInForeground = rememberIsAppInForeground()
    val showArtistVideo = rememberPerfGatedBoolean(ShowArtistVideoKey, defaultValue = true).value &&
        !deviceThrottle && appInForeground
    val showArtistBackgroundVideo =
        rememberPerfGatedBoolean(ShowArtistBackgroundVideoKey, defaultValue = true).value &&
            !deviceThrottle && appInForeground

    var showLocal by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(libraryArtist, artistPage, hasFailed) {
        val artist = libraryArtist?.artist
        val isYtId = viewModel.artistId.startsWith("UC") || (artist?.isYouTubeArtist == true)
        if (isYtId) {
            // Online artist: only fall back to local if online fetch failed and we have local songs
            showLocal = artistPage == null && hasFailed && allLibrarySongs.isNotEmpty()
        } else {
            // Pure local artist or non-YouTube ID: show local unless online page was resolved
            showLocal = artist != null && (artist.isLocal || (artistPage == null && hasFailed))
        }
    }

    val listState = rememberLazyListState()
    val bloom = rememberAuraBloom(mediaMetadata?.id)

    val insets = LocalPlayerAwareWindowInsets.current
    val bottomClearance = insets
        .only(WindowInsetsSides.Bottom)
        .asPaddingValues()
        .calculateBottomPadding()

    val artistName = artistPage?.artist?.title ?: libraryArtist?.artist?.name

    val fromYourLibraryTitle = stringResource(R.string.from_your_library)
    val yourLibraryTitle = stringResource(R.string.your_library)
    val latestReleaseTitle = stringResource(R.string.latest_release)
    val filteredLibrarySongsYt = remember(allLibrarySongs, hideExplicit) {
        if (hideExplicit) allLibrarySongs.filter { !it.song.explicit } else allLibrarySongs
    }
    // YouTube artist view: local "Tu biblioteca" preview is drawn when there is anything local.
    val showedLocalLibraryPreview = !showLocal && filteredLibrarySongsYt.isNotEmpty()
    val onlineArtistSections = remember(
        artistPage?.sections,
        showedLocalLibraryPreview,
        fromYourLibraryTitle,
        yourLibraryTitle,
        latestReleaseTitle,
    ) {
        val seenTitles = linkedSetOf<String>()
        val ranked = artistPage?.sections.orEmpty().filter { section ->
            val title = section.title.trim()
            if (title.isEmpty() || section.items.isEmpty()) return@filter false
            if (showedLocalLibraryPreview &&
                isArtistLibrarySectionTitle(title, yourLibraryTitle, fromYourLibraryTitle)
            ) {
                return@filter false
            }
            val key = title.lowercase()
            seenTitles.add(key)
        }.sortedBy { appleArtistSectionRank(it.title) }
        pinLatestArtistRelease(ranked, latestReleaseTitle)
    }

    // The Apple-Music background clip. HOISTED out of the hero on purpose: the header needs the same
    // answer, because the classic screen hides the SMALL artist video whenever the big background video
    // is the one on screen, and that condition cannot be evaluated from inside the hero. One lookup, on
    // IO, re-issued only when the artist or the setting changes — the same call the classic header makes.
    var backgroundVideoUrl by remember(artistName) { mutableStateOf<String?>(null) }
    LaunchedEffect(artistName, showArtistBackgroundVideo) {
        if (artistName != null && showArtistBackgroundVideo) {
            withContext(Dispatchers.IO) {
                backgroundVideoUrl = AppleMusicArtistBackgroundProvider.getByArtistName(artistName)
            }
        }
    }

    // One menu opener for every YouTube item type, so a section, a shelf and a row can never disagree
    // about which sheet an item gets.
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
                    coroutineScope = coroutineScope,
                    onDismiss = menuState::dismiss,
                )
            }
        }
    }

    val onYtItemClick: (YTItem) -> Unit = { item ->
        when (item) {
            is SongItem -> playerConnection.playQueue(
                YouTubeQueue(WatchEndpoint(videoId = item.id), item.toMediaMetadata()),
            )

            is AlbumItem -> navController.navigate("album/${item.id}")
            is ArtistItem -> navController.navigate("artist/${item.id}")
            is PlaylistItem -> navController.navigate("online_playlist/${item.id}")
        }
    }

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
            contentPadding = PaddingValues(bottom = bottomClearance + 56.dp),
        ) {
            val isKnownArtist = libraryArtist != null || artistPage != null
            if (!isKnownArtist && !showLocal && hasFailed) {
                item(key = "aura_artist_error") {
                    Column {
                        Spacer(Modifier.height(auraStatusBarPadding() + 72.dp))
                        AuraDetailErrorState(
                            message = stringResource(R.string.couldnt_load_artist),
                            onRetry = { viewModel.fetchArtistsFromYTM() },
                        )
                    }
                }
            } else if (!isKnownArtist && !showLocal) {
                item(key = "aura_artist_skeleton") {
                    Column {
                        Spacer(Modifier.height(auraStatusBarPadding() + 56.dp))
                        ShimmerHost { repeat(8) { AuraDetailSkeletonRow() } }
                    }
                }
            } else {
                item(key = "aura_artist_hero") {
                    AuraArtistHero(
                        thumbnailUrl = artistPage?.artist?.thumbnail
                            ?: libraryArtist?.artist?.thumbnailUrl,
                        artistName = artistName,
                        backgroundVideoUrl = backgroundVideoUrl,
                        showBackgroundVideo = showArtistBackgroundVideo,
                        listState = listState,
                    )
                }

                item(key = "aura_artist_header") {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = AuraSpacing.Gutter),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            // The small artist video, only when the big background video is NOT the
                            // one on screen — the classic condition, so the two never play at once.
                            if (showArtistVideo &&
                                !(showArtistBackgroundVideo && backgroundVideoUrl != null)
                            ) {
                                val url = artistVideoUrl
                                val radioEndpoint = artistPage?.artist?.radioEndpoint
                                if (url != null && radioEndpoint != null) {
                                    ArtistVideo(
                                        videoUrl = url,
                                        modifier = Modifier
                                            .size(45.dp)
                                            .clip(AuraShapes.Artwork),
                                        onClick = {
                                            val watchEndpoint = artistVideoSong?.endpoint
                                                ?: radioEndpoint
                                            playerConnection.playQueue(YouTubeQueue(watchEndpoint))
                                        },
                                    )
                                }
                            }
                            Text(
                                text = artistName ?: stringResource(R.string.unknown_artist),
                                style = AuraType.ScreenTitle,
                                color = AuraPalette.OnGround,
                                maxLines = 2,
                                overflow = AuraDefaultOverflow,
                                modifier = Modifier.weight(1f),
                            )
                        }

                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 10.dp),
                        ) {
                            if (showArtistSubscriberCount) {
                                artistPage?.subscriberCountText
                                    ?.takeIf { it.isNotBlank() }
                                    ?.let { subscribers ->
                                        AuraArtistStatChip(
                                            icon = AuraIcons.People,
                                            text = "$subscribers ${stringResource(R.string.subscribers)}",
                                        )
                                    }
                            }
                            if (showMonthlyListeners) {
                                artistPage?.monthlyListenerCount
                                    ?.takeIf { it.isNotBlank() }
                                    ?.let { monthly ->
                                        AuraArtistStatChip(
                                            icon = AuraIcons.Equalizer,
                                            text = "$monthly ${stringResource(R.string.monthly_listeners)}",
                                        )
                                    }
                            }
                        }

                        if (!showLocal && showArtistDescription && artistPage != null) {
                            val description = artistPage.description
                            val descriptionRuns = artistPage.descriptionRuns
                            if (!description.isNullOrEmpty() || !descriptionRuns.isNullOrEmpty()) {
                                Spacer(Modifier.height(16.dp))
                                AuraSectionLabel(
                                    text = stringResource(R.string.about_artist).uppercase(Locale.ROOT),
                                )
                                Spacer(Modifier.height(6.dp))
                                AuraDetailDescription(
                                    text = description.orEmpty(),
                                    runs = descriptionRuns?.map {
                                        LinkSegment(
                                            text = it.text,
                                            url = it.navigationEndpoint?.urlEndpoint?.url,
                                        )
                                    },
                                )
                            }
                        }

                        Spacer(Modifier.height(18.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            val subscribed = libraryArtist?.artist?.bookmarkedAt != null
                            AuraDetailActionButton(
                                icon = if (subscribed) AuraIcons.Check else AuraIcons.Plus,
                                label = stringResource(
                                    if (subscribed) R.string.subscribed else R.string.subscribe,
                                ),
                                accent = false,
                                onClick = {
                                    // Byte for byte the classic handler: update the existing row, or
                                    // insert one built from the page. `confirmArtistUnsubscribed` is
                                    // what keeps the YouTube subscription sync honest.
                                    database.transaction {
                                        val artist = libraryArtist?.artist
                                        if (artist != null) {
                                            update(
                                                artist.toggleLike(database::confirmArtistUnsubscribed),
                                            )
                                        } else {
                                            artistPage?.artist?.let {
                                                insert(
                                                    ArtistEntity(
                                                        id = it.id,
                                                        name = it.title,
                                                        channelId = it.channelId,
                                                        thumbnailUrl = it.thumbnail,
                                                    ).toggleLike(database::confirmArtistUnsubscribed),
                                                )
                                            }
                                        }
                                    }
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .tvFocusable(isTvOrCar, AuraShapes.Pill, scaleFocused = 1f),
                            )

                            if (!showLocal && !isGuest) {
                                artistPage?.artist?.radioEndpoint?.let { radioEndpoint ->
                                    AuraDetailActionButton(
                                        icon = AuraIcons.Radio,
                                        label = stringResource(R.string.radio),
                                        accent = true,
                                        onClick = {
                                            playerConnection.playQueue(YouTubeQueue(radioEndpoint))
                                        },
                                        modifier = Modifier
                                            .weight(1f)
                                            .tvFocusable(isTvOrCar, AuraShapes.Pill, scaleFocused = 1f),
                                    )
                                }
                                artistPage?.artist?.shuffleEndpoint?.let { shuffleEndpoint ->
                                    AuraDetailActionButton(
                                        icon = AuraIcons.Shuffle,
                                        label = stringResource(R.string.shuffle),
                                        accent = false,
                                        onClick = {
                                            playerConnection.playQueue(YouTubeQueue(shuffleEndpoint))
                                        },
                                        modifier = Modifier
                                            .weight(1f)
                                            .tvFocusable(isTvOrCar, AuraShapes.Pill, scaleFocused = 1f),
                                    )
                                }
                            } else if (allLibrarySongs.isNotEmpty() && !isGuest) {
                                // Enhanced Shuffle over the artist's FULL local catalogue. "AR:" + the
                                // local row id, NOT "PL:": the startup orphan prune wipes every "PL:%"
                                // context with no playlist row, so a PL:-namespaced artist would forget
                                // its no-repeat memory on every launch. Nothing prunes "AR:%".
                                val artistShuffleContextId = libraryArtist?.id?.let { "AR:$it" }
                                val playedForStart = rememberPlayedShuffleSet(artistShuffleContextId)
                                val onArtistShuffleClick = rememberShuffleMemoryPrompt(
                                    contextId = artistShuffleContextId,
                                    playedCount = allLibrarySongs.count { it.id in playedForStart },
                                    totalCount = allLibrarySongs.size,
                                ) { resetMemory ->
                                    val shuffledSongs = if (resetMemory) {
                                        allLibrarySongs.shuffled()
                                    } else {
                                        val (unheard, heard) =
                                            allLibrarySongs.partition { it.id !in playedForStart }
                                        unheard.shuffled() + heard.shuffled()
                                    }
                                    if (shuffledSongs.isNotEmpty()) {
                                        playerConnection.playQueue(
                                            ListQueue(
                                                title = libraryArtist?.artist?.name
                                                    ?: context.getString(R.string.unknown_artist),
                                                items = shuffledSongs.map { it.toMediaItem() },
                                                contextId = artistShuffleContextId,
                                                // Shuffle MODE on: pre-scrambling alone left the icon
                                                // off and the order frozen.
                                                startShuffled = true,
                                            ),
                                        )
                                    }
                                }
                                AuraDetailActionButton(
                                    icon = AuraIcons.Shuffle,
                                    label = stringResource(R.string.shuffle),
                                    accent = true,
                                    onClick = onArtistShuffleClick,
                                    modifier = Modifier
                                        .weight(1f)
                                        .tvFocusable(isTvOrCar, AuraShapes.Pill, scaleFocused = 1f),
                                )
                            }
                        }
                    }
                }

                if (showLocal) {
                    // Full local catalogue for play/queue; the shelf preview is only for what we DRAW.
                    val filteredLibrarySongs = if (hideExplicit) {
                        allLibrarySongs.filter { !it.song.explicit }
                    } else {
                        allLibrarySongs
                    }
                    if (filteredLibrarySongs.isNotEmpty()) {
                        item(key = "aura_artist_local_songs_label") {
                            AuraSectionHeader(
                                title = stringResource(R.string.songs),
                                onClick = {
                                    navController.navigate("artist/${viewModel.artistId}/songs")
                                },
                                modifier = Modifier.animateItem(),
                            )
                        }
                        item(key = "aura_artist_local_songs_pages") {
                            AuraSongPages(
                                itemCount = filteredLibrarySongs.size,
                                modifier = Modifier.animateItem(),
                            ) { index ->
                                AuraArtistLibrarySongRow(
                                    song = filteredLibrarySongs[index],
                                    allSongs = filteredLibrarySongs,
                                    artistName = libraryArtist?.artist?.name
                                        ?: context.getString(R.string.unknown_artist),
                                    mediaMetadataId = mediaMetadata?.id,
                                    isPlaying = isPlaying,
                                    isTvOrCar = isTvOrCar,
                                    navController = navController,
                                    playerConnection = playerConnection,
                                    menuState = menuState,
                                    haptic = haptic,
                                )
                            }
                        }
                    }

                    val filteredLibraryAlbums = if (hideExplicit) {
                        libraryAlbums.filter { !it.album.explicit }
                    } else {
                        libraryAlbums
                    }
                    if (filteredLibraryAlbums.isNotEmpty()) {
                        item(key = "aura_artist_local_albums_label") {
                            AuraSectionHeader(
                                title = stringResource(R.string.albums),
                                onClick = {
                                    navController.navigate("artist/${viewModel.artistId}/albums")
                                },
                                modifier = Modifier.animateItem(),
                            )
                        }
                        item(key = "aura_artist_local_albums_row") {
                            val albumW = AuraAlbumShelfWidth
                            AuraDoubleRowShelf(
                                rowHeight = auraShelfCardStackHeight(albumW),
                                modifier = Modifier.animateItem(),
                            ) {
                                items(
                                    count = filteredLibraryAlbums.size,
                                    key = { position -> "aura_artist_local_album_$position" },
                                ) { position ->
                                    val album = filteredLibraryAlbums[position]
                                    AuraCoverCard(
                                        title = album.album.title,
                                        subtitle = album.artists.joinToString { it.name }
                                            .takeIf { it.isNotBlank() },
                                        thumbnailUrl = album.album.thumbnailUrl,
                                        seed = album.id,
                                        width = albumW,
                                        isActive = mediaMetadata?.album?.id == album.id,
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
                                        modifier = Modifier
                                            .tvFocusable(
                                                isTvOrCar,
                                                AuraShapes.Highlight,
                                                scaleFocused = 1f,
                                            ),
                                    )
                                }
                            }
                        }
                    }
                } else {
                    // Popular → albums → videos, then "Tu biblioteca". Putting the local list under
                    // populares packed the top of the page (owner: ruins the layout). Never instead of
                    // populares. Play uses the FULL local catalogue (`allLibrarySongs`). YTM's own
                    // "From your library" shelf is filtered out so it does not repeat.
                    val showLibrarySection = showedLocalLibraryPreview
                    val libraryInsertIndex = if (showLibrarySection) {
                        artistLibraryInsertIndex(onlineArtistSections.map { it.title })
                    } else {
                        -1
                    }

                    var libraryPreviewAdded = false

                    if (onlineArtistSections.isEmpty() && showLibrarySection) {
                        auraArtistLibraryPreviewItems(
                            songs = filteredLibrarySongsYt,
                            allSongs = filteredLibrarySongsYt,
                            artistName = libraryArtist?.artist?.name
                                ?: context.getString(R.string.unknown_artist),
                            headerTitle = yourLibraryTitle,
                            artistId = viewModel.artistId,
                            mediaMetadataId = mediaMetadata?.id,
                            isPlaying = isPlaying,
                            isTvOrCar = isTvOrCar,
                            navController = navController,
                            playerConnection = playerConnection,
                            menuState = menuState,
                            haptic = haptic,
                        )
                        libraryPreviewAdded = true
                    }

                    if (artistPage == null && !showLocal) {
                        item(key = "aura_artist_online_loading") {
                            Column(Modifier.padding(top = 16.dp)) {
                                ShimmerHost { repeat(3) { AuraDetailSkeletonRow() } }
                            }
                        }
                    }

                    onlineArtistSections.forEachIndexed { sectionIndex, section ->
                        if (sectionIndex == libraryInsertIndex && !libraryPreviewAdded) {
                            auraArtistLibraryPreviewItems(
                                songs = filteredLibrarySongsYt,
                                allSongs = filteredLibrarySongsYt,
                                artistName = libraryArtist?.artist?.name
                                    ?: context.getString(R.string.unknown_artist),
                                headerTitle = yourLibraryTitle,
                                artistId = viewModel.artistId,
                                mediaMetadataId = mediaMetadata?.id,
                                isPlaying = isPlaying,
                                isTvOrCar = isTvOrCar,
                                navController = navController,
                                playerConnection = playerConnection,
                                menuState = menuState,
                                haptic = haptic,
                            )
                            libraryPreviewAdded = true
                        }
                        val sectionItems = section.items.distinctBy { it.id }
                        if (sectionItems.isEmpty()) return@forEachIndexed

                        item(key = "aura_artist_section_${sectionIndex}_label") {
                            AuraSectionHeader(
                                title = section.title,
                                // The "ver todos" affordance is ALWAYS offered: YouTube's own "more"
                                // endpoint when there is one, otherwise the already-loaded items in a
                                // grid through ArtistSectionBuffer — the classic rule.
                                onClick = {
                                    val more = section.moreEndpoint
                                    if (more != null) {
                                        navController.navigate(
                                            "artist/${viewModel.artistId}/items" +
                                                "?browseId=${more.browseId}&params=${more.params}",
                                        )
                                    } else {
                                        ArtistSectionBuffer.open(section.title, sectionItems)
                                        navController.navigate("artist_section_buffer")
                                    }
                                },
                                modifier = Modifier.animateItem(),
                            )
                        }

                        // Populares / top songs → Apple Music 4-row pages (swipe L–R).
                        // Other audio tracks on this page stay a vertical list (no horizontal pager).
                        // Videos / albums / EPs / playlists stay typed cover shelves.
                        // Queue is still the WHOLE section, not the visible page.
                        if (
                            sectionItems.all { it is SongItem } &&
                            sectionItems.none { (it as SongItem).isVideoSong }
                        ) {
                            val sectionSongs =
                                if (isArtistPopularSectionTitle(section.title) &&
                                    expandedPopularSongs.isNotEmpty()
                                ) {
                                    expandedPopularSongs
                                } else {
                                    sectionItems.filterIsInstance<SongItem>()
                                }
                            if (isArtistPopularSectionTitle(section.title)) {
                                item(key = "aura_artist_section_${sectionIndex}_pages") {
                                    AuraSongPages(
                                        itemCount = sectionSongs.size,
                                        modifier = Modifier.animateItem(),
                                    ) { index ->
                                        val song = sectionSongs[index]
                                        AuraArtistYtSongRow(
                                            song = song,
                                            sectionSongs = sectionSongs,
                                            sectionTitle = section.title,
                                            mediaMetadataId = mediaMetadata?.id,
                                            isPlaying = isPlaying,
                                            isTvOrCar = isTvOrCar,
                                            playerConnection = playerConnection,
                                            onMenu = { openYtMenu(song) },
                                            haptic = haptic,
                                        )
                                    }
                                }
                            } else {
                                items(
                                    count = sectionSongs.size,
                                    key = { position ->
                                        "aura_artist_section_${sectionIndex}_song_$position"
                                    },
                                ) { index ->
                                    val song = sectionSongs[index]
                                    Box(
                                        modifier = Modifier
                                            .animateItem()
                                            .padding(horizontal = AuraSpacing.Gutter),
                                    ) {
                                        AuraArtistYtSongRow(
                                            song = song,
                                            sectionSongs = sectionSongs,
                                            sectionTitle = section.title,
                                            mediaMetadataId = mediaMetadata?.id,
                                            isPlaying = isPlaying,
                                            isTvOrCar = isTvOrCar,
                                            playerConnection = playerConnection,
                                            onMenu = { openYtMenu(song) },
                                            haptic = haptic,
                                        )
                                    }
                                }
                            }
                        } else {
                            item(key = "aura_artist_section_${sectionIndex}_shelf") {
                                val videoHeavy = sectionItems.any { it is SongItem && it.isVideoSong }
                                if (videoHeavy) {
                                    // YTM-style: one row of large full-bleed 16:9 cards (not a cramped 2×N stamp grid).
                                    AuraShelf(modifier = Modifier.animateItem()) {
                                        items(
                                            count = sectionItems.size,
                                            key = { position ->
                                                "aura_artist_section_${sectionIndex}_card_$position"
                                            },
                                        ) { position ->
                                            val item = sectionItems[position]
                                            AuraTypedYtCoverCard(
                                                item = item,
                                                cardScale = 1.2f,
                                                isActive = when (item) {
                                                    is SongItem -> mediaMetadata?.id == item.id
                                                    is AlbumItem -> mediaMetadata?.album?.id == item.id
                                                    else -> false
                                                },
                                                isPlaying = isPlaying,
                                                onClick = {
                                                    if (item is SongItem && sectionItems.all { it is SongItem }) {
                                                        if (item.id == mediaMetadata?.id) {
                                                            playerConnection.togglePlayPause()
                                                        } else {
                                                            val sectionSongs = sectionItems.filterIsInstance<SongItem>()
                                                            playerConnection.playQueue(
                                                                ListQueue(
                                                                    title = section.title,
                                                                    items = sectionSongs.map { it.toMediaItem() },
                                                                    startIndex = sectionSongs
                                                                        .indexOfFirst { it.id == item.id }
                                                                        .coerceAtLeast(0),
                                                                ),
                                                            )
                                                        }
                                                    } else {
                                                        onYtItemClick(item)
                                                    }
                                                },
                                                onLongClick = {
                                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                    openYtMenu(item)
                                                },
                                                modifier = Modifier.tvFocusable(
                                                    isTvOrCar,
                                                    AuraShapes.Highlight,
                                                    scaleFocused = 1f,
                                                ),
                                            )
                                        }
                                    }
                                } else {
                                // Keep scale in ONE place: rowHeight must use the same scaled width as
                                // AuraTypedYtCoverCard, or the year under the cover is clipped (owner report).
                                val albumCardScale = 1.05f
                                val cardW = AuraAlbumShelfWidth * albumCardScale
                                // Albums / EPs / Singles / playlists: two sideways rows (Apple + YTM).
                                AuraDoubleRowShelf(
                                    rowHeight = auraShelfCardStackHeight(cardW),
                                    itemCount = sectionItems.size,
                                    modifier = Modifier.animateItem(),
                                ) {
                                    items(
                                        count = sectionItems.size,
                                        key = { position ->
                                            "aura_artist_section_${sectionIndex}_card_$position"
                                        },
                                    ) { position ->
                                        val item = sectionItems[position]
                                        AuraTypedYtCoverCard(
                                            item = item,
                                            cardScale = albumCardScale,
                                            isActive = when (item) {
                                                is SongItem -> mediaMetadata?.id == item.id
                                                is AlbumItem -> mediaMetadata?.album?.id == item.id
                                                else -> false
                                            },
                                            isPlaying = isPlaying,
                                            onClick = {
                                                if (item is SongItem && sectionItems.all { it is SongItem }) {
                                                    if (item.id == mediaMetadata?.id) {
                                                        playerConnection.togglePlayPause()
                                                    } else {
                                                        val sectionSongs = sectionItems.filterIsInstance<SongItem>()
                                                        playerConnection.playQueue(
                                                            ListQueue(
                                                                title = section.title,
                                                                items = sectionSongs.map { it.toMediaItem() },
                                                                startIndex = sectionSongs
                                                                    .indexOfFirst { it.id == item.id }
                                                                    .coerceAtLeast(0),
                                                            ),
                                                        )
                                                    }
                                                } else {
                                                    onYtItemClick(item)
                                                }
                                            },
                                            onLongClick = {
                                                haptic.performHapticFeedback(
                                                    HapticFeedbackType.LongPress,
                                                )
                                                openYtMenu(item)
                                            },
                                            modifier = Modifier
                                                .tvFocusable(
                                                    isTvOrCar,
                                                    AuraShapes.Highlight,
                                                    scaleFocused = 1f,
                                                ),
                                        )
                                    }
                                }
                                }
                            }
                        }
                    }
                    if (onlineArtistSections.isNotEmpty() && libraryInsertIndex >= onlineArtistSections.size && !libraryPreviewAdded) {
                        auraArtistLibraryPreviewItems(
                            songs = filteredLibrarySongsYt,
                            allSongs = filteredLibrarySongsYt,
                            artistName = libraryArtist?.artist?.name
                                ?: context.getString(R.string.unknown_artist),
                            headerTitle = yourLibraryTitle,
                            artistId = viewModel.artistId,
                            mediaMetadataId = mediaMetadata?.id,
                            isPlaying = isPlaying,
                            isTvOrCar = isTvOrCar,
                            navController = navController,
                            playerConnection = playerConnection,
                            menuState = menuState,
                            haptic = haptic,
                        )
                        libraryPreviewAdded = true
                    }
                }
            }
        }

        AuraDetailTopBar(
            listState = listState,
            title = artistName.orEmpty(),
            onBack = navController::navigateUp,
            actions = {
                AuraIconButton(
                    icon = AuraIcons.Share,
                    contentDescription = stringResource(R.string.share),
                    onClick = {
                        val artistId = viewModel.artistPage?.artist?.id
                            ?: viewModel.artistId
                        val link = viewModel.artistPage?.artist?.shareLink
                            ?: iad1tya.echo.music.utils.ShareLinks.channel(artistId)
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, link)
                        }
                        context.startActivity(Intent.createChooser(intent, null))
                    },
                    size = 20.dp,
                )
            },
        )
    }
}

// ── Hero ──────────────────────────────────────────────────────────────────────────────────────────

/**
 * The artist's full-bleed portrait, its Apple-Music background video and the scrim that hands the
 * image over to the ground.
 *
 * [backgroundVideoUrl] is resolved by the CALLER, not here: the header has to know the same answer in
 * order to hide the small artist video while this clip is playing, so a single hoisted lookup feeds
 * both and a second [AppleMusicArtistBackgroundProvider] call is never issued.
 *
 * The parallax is a DRAW-phase read of the list offset inside [graphicsLayer]: the hero translates and
 * fades without recomposing, which is what keeps scrolling an artist off the thermal budget.
 */
@Composable
private fun AuraArtistHero(
    thumbnailUrl: String?,
    artistName: String?,
    backgroundVideoUrl: String?,
    showBackgroundVideo: Boolean,
    listState: LazyListState,
    modifier: Modifier = Modifier,
) {
    if (thumbnailUrl == null && backgroundVideoUrl == null) {
        // No portrait and no video: leave the status-bar clearance the top bar needs and nothing else,
        // exactly as the classic header does when `thumbnail == null`.
        Spacer(modifier.height(auraStatusBarPadding() + 56.dp))
        return
    }

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
                seed = thumbnailUrl ?: artistName,
                shape = RectangleShape,
                decodeTo = 1200,
                ratio = if (isWideHero) heroWidth / 320.dp else 1f,
            )

            val videoUrl = backgroundVideoUrl
            if (videoUrl != null && showBackgroundVideo) {
                ArtistVideo(
                    videoUrl = videoUrl,
                    modifier = Modifier.fillMaxSize(),
                    onClick = { },
                )
            }

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

private fun LazyListScope.auraArtistLibraryPreviewItems(
    songs: List<Song>,
    allSongs: List<Song>,
    artistName: String,
    headerTitle: String,
    artistId: String,
    mediaMetadataId: String?,
    isPlaying: Boolean,
    isTvOrCar: Boolean,
    navController: NavController,
    playerConnection: PlayerConnection,
    menuState: MenuState,
    haptic: HapticFeedback,
) {
    if (songs.isEmpty()) return
    item(key = "aura_artist_library_preview_label") {
        AuraSectionHeader(
            title = headerTitle,
            onClick = { navController.navigate("artist/$artistId/songs") },
            modifier = Modifier.animateItem(),
        )
    }
    item(key = "aura_artist_library_preview_pages") {
        AuraSongPages(
            itemCount = songs.size,
            modifier = Modifier.animateItem(),
        ) { index ->
            AuraArtistLibrarySongRow(
                song = songs[index],
                allSongs = allSongs,
                artistName = artistName,
                mediaMetadataId = mediaMetadataId,
                isPlaying = isPlaying,
                isTvOrCar = isTvOrCar,
                navController = navController,
                playerConnection = playerConnection,
                menuState = menuState,
                haptic = haptic,
            )
        }
    }
}

@Composable
private fun AuraArtistLibrarySongRow(
    song: Song,
    allSongs: List<Song>,
    artistName: String,
    mediaMetadataId: String?,
    isPlaying: Boolean,
    isTvOrCar: Boolean,
    navController: NavController,
    playerConnection: PlayerConnection,
    menuState: MenuState,
    haptic: HapticFeedback,
) {
    AuraSongRow(
        title = song.song.title,
        subtitle = song.artists.joinToString { it.name },
        thumbnailUrl = song.song.thumbnailUrl,
        seed = song.id,
        isActive = song.id == mediaMetadataId,
        isPlaying = isPlaying,
        liked = song.song.liked,
        explicit = song.song.explicit,
        inLibrary = false,
        downloadId = song.id,
        format = song.format,
        playedInShuffle = song.song.totalPlayTime > 0L,
        onClick = {
            if (song.id == mediaMetadataId) {
                playerConnection.togglePlayPause()
            } else {
                playerConnection.playQueue(
                    ListQueue(
                        title = artistName,
                        items = allSongs.map { it.toMediaItem() },
                        startIndex = allSongs.indexOfFirst { it.id == song.id }.coerceAtLeast(0),
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
        onMenuClick = {
            menuState.show {
                SongMenu(
                    originalSong = song,
                    navController = navController,
                    onDismiss = menuState::dismiss,
                )
            }
        },
        modifier = Modifier.tvFocusable(isTvOrCar, AuraShapes.Highlight, scaleFocused = 1f),
    )
}

@Composable
private fun AuraArtistYtSongRow(
    song: SongItem,
    sectionSongs: List<SongItem>,
    sectionTitle: String,
    mediaMetadataId: String?,
    isPlaying: Boolean,
    isTvOrCar: Boolean,
    playerConnection: PlayerConnection,
    onMenu: () -> Unit,
    haptic: HapticFeedback,
) {
    val database = LocalDatabase.current
    val dbSong by database.song(song.id).collectAsState(initial = null)
    AuraSongRow(
        title = song.title,
        subtitle = song.artists.joinToString { it.name },
        thumbnailUrl = song.thumbnail,
        seed = song.id,
        isActive = song.id == mediaMetadataId,
        isPlaying = isPlaying,
        liked = dbSong?.song?.liked == true,
        explicit = song.explicit,
        inLibrary = dbSong?.song?.inLibrary != null,
        downloadId = song.id,
        format = dbSong?.format,
        playedInShuffle = (dbSong?.song?.totalPlayTime ?: 0L) > 0L,
        onClick = {
            if (song.id == mediaMetadataId) {
                playerConnection.togglePlayPause()
            } else {
                playerConnection.playQueue(
                    ListQueue(
                        title = sectionTitle,
                        items = sectionSongs.map { it.toMediaItem() },
                        startIndex = sectionSongs.indexOfFirst { it.id == song.id }.coerceAtLeast(0),
                    ),
                )
            }
        },
        onLongClick = {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            onMenu()
        },
        onMenuClick = onMenu,
        modifier = Modifier.tvFocusable(isTvOrCar, AuraShapes.Highlight, scaleFocused = 1f),
    )
}

/**
 * Apple Music / YTM artist-page order for YouTube shelves. Aura's "Tu biblioteca" is NOT a
 * YouTube shelf: [artistLibraryInsertIndex] draws it after albums and videos so the top of
 * the page (populares + discs + videos) stays uncluttered. Do not hide populares to put
 * library in that slot.
 */
internal fun appleArtistSectionRank(title: String): Int {
    val t = title.trim().lowercase()
    return when {
        isArtistPopularSectionTitle(title) -> 0
        isArtistLatestSectionTitle(title) -> 1
        t.contains("essential") || t.contains("imprescind") -> 3
        t.contains("album") && !t.contains("live") && !t.contains("compilation") &&
            !t.contains("en vivo") && !t.contains("recopil") -> 4
        t.contains("álbum") && !t.contains("vivo") && !t.contains("recopil") -> 4
        t.contains("video") || t.contains("vídeo") -> 5
        t.contains("playlist") || t.contains("lista") -> 6
        t.contains("single") || t.contains("ep") -> 7
        t.contains("live") || t.contains("en vivo") -> 8
        t.contains("compilation") || t.contains("recopil") -> 9
        t.contains("appear") || t.contains("aparece") || t.contains("featuring") -> 10
        t.contains("similar") || t.contains("also like") || t.contains("fans might") ||
            t.contains("artistas similares") -> 13
        else -> 50
    }
}

internal fun isArtistLatestSectionTitle(title: String): Boolean {
    val t = title.trim().lowercase()
    return t.contains("latest") || t.contains("último") || t.contains("ultimo lanz")
}

internal fun isArtistPopularSectionTitle(title: String): Boolean {
    val t = title.trim().lowercase()
    if (t == "songs" || t == "canciones") return true
    return t.contains("top song") ||
        t.contains("popular song") ||
        t.contains("canciones populares") ||
        t.contains("canciones más populares") ||
        t.contains("canciones mas populares") ||
        t.contains("canciones más escuchadas") ||
        t.contains("canciones mas escuchadas") ||
        t.contains("canciones más reproducid") ||
        t.contains("canciones mas reproducid") ||
        t.contains("top track")
}

/** Videos are rank 5. "Tu biblioteca" is inserted after the last section at or below this rank. */
internal const val ARTIST_LIBRARY_AFTER_MAX_RANK = 5

/**
 * Index in [sectionTitles] at which to draw Aura's local "Tu biblioteca" preview: after
 * populares, latest, essentials, albums and videos (the last title whose
 * [appleArtistSectionRank] is ≤ [ARTIST_LIBRARY_AFTER_MAX_RANK]). Playlists / singles /
 * similar stay below it. Empty list → 0.
 */
internal fun artistLibraryInsertIndex(sectionTitles: List<String>): Int {
    var lastAbove = -1
    sectionTitles.forEachIndexed { i, title ->
        if (appleArtistSectionRank(title) <= ARTIST_LIBRARY_AFTER_MAX_RANK) lastAbove = i
    }
    return lastAbove + 1
}

internal fun pinLatestArtistRelease(
    sections: List<ArtistSection>,
    latestTitle: String,
): List<ArtistSection> {
    if (sections.any { isArtistLatestSectionTitle(it.title) }) return sections
    val latest = sections.asSequence()
        .flatMap { it.items.asSequence() }
        .filterIsInstance<AlbumItem>()
        .maxWithOrNull(
            compareBy<AlbumItem> { it.year ?: Int.MIN_VALUE }.thenBy { it.title },
        ) ?: return sections
    return (listOf(
        ArtistSection(title = latestTitle, items = listOf(latest), moreEndpoint = null),
    ) + sections).sortedBy { appleArtistSectionRank(it.title) }
}

/**
 * YouTube Music often injects a "From your library" / "De tu biblioteca" shelf on artist pages.
 * Aura already draws [R.string.your_library] from the local catalogue after albums/videos —
 * showing both looks like a duplicated "Tu biblioteca".
 */
private fun isArtistLibrarySectionTitle(
    title: String,
    yourLibrary: String,
    fromYourLibrary: String,
): Boolean {
    val t = title.trim().lowercase()
    if (t.isEmpty()) return false
    if (t == yourLibrary.trim().lowercase()) return true
    if (t == fromYourLibrary.trim().lowercase()) return true
    if (t == "from your library" || t == "your library") return true
    if (t == "de tu biblioteca" || t == "tu biblioteca") return true
    // Loose match: any YTM shelf whose title is clearly the library mirror.
    if (t.contains("biblioteca") && (t.contains("tu") || t.contains("your") || t.startsWith("de "))) {
        return true
    }
    if (t.contains("library") && (t.contains("your") || t.startsWith("from "))) {
        return true
    }
    return false
}

/** "1,2 M suscriptores" / "3,4 M oyentes mensuales" — the two header stats, each behind its setting. */
@Composable
private fun AuraArtistStatChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = modifier
            .clip(AuraShapes.Pill)
            .background(AuraPalette.SurfaceFill)
            .border(1.dp, AuraPalette.SurfaceLine, AuraShapes.Pill)
            .padding(horizontal = 12.dp, vertical = 7.dp),
    ) {
        AuraIconGlyph(
            icon = icon,
            contentDescription = null,
            size = 15.dp,
            tint = AuraPalette.Teal,
        )
        Text(
            text = text,
            style = AuraType.Chip,
            color = AuraPalette.OnGroundMuted,
            maxLines = 1,
            overflow = AuraDefaultOverflow,
        )
    }
}
