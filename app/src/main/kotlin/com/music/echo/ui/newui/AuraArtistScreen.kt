package iad1tya.echo.music.ui.newui

import android.content.Intent
import android.widget.Toast
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
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
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
import iad1tya.echo.music.extensions.toMediaItem
import iad1tya.echo.music.models.toMediaMetadata
import iad1tya.echo.music.playback.queues.ListQueue
import iad1tya.echo.music.playback.queues.YouTubeQueue
import iad1tya.echo.music.ui.component.LinkSegment
import iad1tya.echo.music.ui.component.LocalMenuState
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
 *    never the 3-item shelf preview), with the same unplayed-first opener and `startShuffled = true`.
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
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
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
    LaunchedEffect(libraryArtist, artistPage) {
        // The classic rule, unchanged. A library row with a GENERATED id has no YouTube page of its
        // own, but the artist usually exists on YouTube and the ViewModel resolves it by name — so the
        // local view is only the fallback while that resolution has produced nothing. Otherwise the
        // user would get his library's six albums instead of the full discography, with no way back.
        val artist = libraryArtist?.artist
        showLocal = artist != null && (artist.isLocal || (!artist.isYouTubeArtist && artistPage == null))
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
    ) {
        val seenTitles = linkedSetOf<String>()
        artistPage?.sections.orEmpty().filter { section ->
            val title = section.title.trim()
            if (title.isEmpty() || section.items.isEmpty()) return@filter false
            if (showedLocalLibraryPreview &&
                isArtistLibrarySectionTitle(title, yourLibraryTitle, fromYourLibraryTitle)
            ) {
                return@filter false
            }
            val key = title.lowercase()
            seenTitles.add(key)
        }
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
            if (artistPage == null && !showLocal && hasFailed) {
                item(key = "aura_artist_error") {
                    Column {
                        Spacer(Modifier.height(auraStatusBarPadding() + 72.dp))
                        AuraDetailErrorState(
                            message = stringResource(R.string.couldnt_load_artist),
                            onRetry = { viewModel.fetchArtistsFromYTM() },
                        )
                    }
                }
            } else if (artistPage == null && !showLocal) {
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
                    val librarySongsPreview = filteredLibrarySongs.take(8)
                    if (librarySongsPreview.isNotEmpty()) {
                        item(key = "aura_artist_local_songs_label") {
                            AuraSectionHeader(
                                title = stringResource(R.string.songs),
                                onClick = {
                                    navController.navigate("artist/${viewModel.artistId}/songs")
                                },
                                modifier = Modifier.animateItem(),
                            )
                        }
                        itemsIndexed(
                            items = librarySongsPreview,
                            key = { position, _ -> "aura_artist_local_song_$position" },
                        ) { _, song ->
                            AuraSongRow(
                                title = song.song.title,
                                subtitle = song.artists.joinToString { it.name }
                                    .takeIf { it.isNotBlank() },
                                thumbnailUrl = song.song.thumbnailUrl,
                                seed = song.id,
                                isActive = song.id == mediaMetadata?.id,
                                isPlaying = isPlaying,
                                liked = song.song.liked,
                                explicit = song.song.explicit,
                                inLibrary = song.song.inLibrary != null,
                                downloadId = song.id,
                                format = song.format,
                                playedInShuffle = song.song.totalPlayTime > 0L,
                                onClick = {
                                    if (song.id == mediaMetadata?.id) {
                                        playerConnection.togglePlayPause()
                                    } else {
                                        playerConnection.playQueue(
                                            ListQueue(
                                                title = libraryArtist?.artist?.name
                                                    ?: context.getString(R.string.unknown_artist),
                                                items = filteredLibrarySongs.map { it.toMediaItem() },
                                                startIndex = filteredLibrarySongs
                                                    .indexOfFirst { it.id == song.id }
                                                    .coerceAtLeast(0),
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
                                menuContentDescription = song.song.title,
                                modifier = Modifier
                                    .animateItem()
                                    .padding(horizontal = AuraSpacing.Gutter)
                                    .tvFocusable(isTvOrCar, AuraShapes.Highlight, scaleFocused = 1f),
                            )
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
                    // "Tu biblioteca" — owner request: this used to be reachable only by fully
                    // switching the screen to the local view (`showLocal`, whose toggle FAB has been
                    // dead since it shipped, `visible = false`), so a normal YouTube artist page never
                    // showed the songs the user already has. Now it always shows here, ABOVE the
                    // online sections, when there is anything to show.
                    // Play uses the FULL local catalogue (`allLibrarySongs`), never the 3-row DB
                    // preview — otherwise tapping song 1 of 10 stopped after the three drawn rows.
                    // YTM's own "From your library" shelf is filtered out of [onlineArtistSections]
                    // so it does not repeat under this block.
                    val filteredLibraryPreview = filteredLibrarySongsYt.take(8)
                    if (showedLocalLibraryPreview) {
                        item(key = "aura_artist_library_preview_label") {
                            AuraSectionHeader(
                                title = yourLibraryTitle,
                                onClick = {
                                    navController.navigate("artist/${viewModel.artistId}/songs")
                                },
                                modifier = Modifier.animateItem(),
                            )
                        }
                        itemsIndexed(
                            items = filteredLibraryPreview,
                            key = { position, _ -> "aura_artist_library_preview_$position" },
                        ) { _, song ->
                            AuraSongRow(
                                title = song.song.title,
                                subtitle = song.artists.joinToString { it.name }
                                    .takeIf { it.isNotBlank() },
                                thumbnailUrl = song.song.thumbnailUrl,
                                seed = song.id,
                                isActive = song.id == mediaMetadata?.id,
                                isPlaying = isPlaying,
                                liked = song.song.liked,
                                explicit = song.song.explicit,
                                inLibrary = song.song.inLibrary != null,
                                downloadId = song.id,
                                format = song.format,
                                playedInShuffle = song.song.totalPlayTime > 0L,
                                onClick = {
                                    if (song.id == mediaMetadata?.id) {
                                        playerConnection.togglePlayPause()
                                    } else {
                                        playerConnection.playQueue(
                                            ListQueue(
                                                title = libraryArtist?.artist?.name
                                                    ?: context.getString(R.string.unknown_artist),
                                                items = filteredLibrarySongsYt.map { it.toMediaItem() },
                                                startIndex = filteredLibrarySongsYt
                                                    .indexOfFirst { it.id == song.id }
                                                    .coerceAtLeast(0),
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
                                menuContentDescription = song.song.title,
                                modifier = Modifier
                                    .animateItem()
                                    .padding(horizontal = AuraSpacing.Gutter)
                                    .tvFocusable(isTvOrCar, AuraShapes.Highlight, scaleFocused = 1f),
                            )
                        }
                    }

                    onlineArtistSections.forEachIndexed { sectionIndex, section ->
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
                                        ArtistSectionBuffer.title = section.title
                                        ArtistSectionBuffer.items = sectionItems
                                        navController.navigate("artist_section_buffer")
                                    }
                                },
                                modifier = Modifier.animateItem(),
                            )
                        }

                        // Tracks → vertical list (Apple/YTM). Videos / albums / EPs / playlists →
                        // typed cover shelves. Never dump plain songs into a postage-stamp grid.
                        if (
                            sectionItems.all { it is SongItem } &&
                            sectionItems.none { (it as SongItem).isVideoSong }
                        ) {
                            itemsIndexed(
                                items = sectionItems,
                                key = { position, _ ->
                                    "aura_artist_section_${sectionIndex}_row_$position"
                                },
                            ) { _, item ->
                                val song = item as SongItem
                                AuraYtItemRow(
                                    item = song,
                                    isActive = mediaMetadata?.id == song.id,
                                    isPlaying = isPlaying,
                                    onClick = {
                                        if (song.id == mediaMetadata?.id) {
                                            playerConnection.togglePlayPause()
                                        } else {
                                            // The WHOLE section becomes the queue, starting at the
                                            // tapped song — like an album — instead of a single-song
                                            // queue that stops after it.
                                            val sectionSongs = sectionItems.filterIsInstance<SongItem>()
                                            playerConnection.playQueue(
                                                ListQueue(
                                                    title = section.title,
                                                    items = sectionSongs.map { it.toMediaItem() },
                                                    startIndex = sectionSongs
                                                        .indexOfFirst { it.id == song.id }
                                                        .coerceAtLeast(0),
                                                ),
                                            )
                                        }
                                    },
                                    onLongClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        openYtMenu(song)
                                    },
                                    onMenuClick = { openYtMenu(song) },
                                    modifier = Modifier
                                        .animateItem()
                                        .tvFocusable(
                                            isTvOrCar,
                                            AuraShapes.Highlight,
                                            scaleFocused = 1f,
                                        ),
                                )
                            }
                        } else {
                            item(key = "aura_artist_section_${sectionIndex}_shelf") {
                                val videoHeavy = sectionItems.any { it is SongItem && it.isVideoSong }
                                if (videoHeavy) {
                                    // YTM-style: one row of large full-bleed 16:9 cards (not a cramped 2×N stamp grid).
                                    AuraDetailShelf(modifier = Modifier.animateItem()) {
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
                                val cardW = AuraAlbumShelfWidth
                                // Albums / EPs / Singles / playlists: two sideways rows (Apple + YTM).
                                AuraDoubleRowShelf(
                                    rowHeight = auraShelfCardStackHeight(cardW),
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
                                            cardScale = 1.05f,
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

/**
 * YouTube Music often injects a "From your library" / "De tu biblioteca" shelf on artist pages.
 * Aura already draws [R.string.your_library] from the local catalogue above the online sections —
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
