package iad1tya.echo.music.ui.newui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.res.Configuration
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitVerticalTouchSlopOrCancellation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.verticalDrag
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderColors
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.input.pointer.util.addPointerInputChange
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import coil3.compose.AsyncImage
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.Player
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadRequest
import androidx.media3.exoplayer.offline.DownloadService
import androidx.navigation.NavController
import iad1tya.echo.music.LocalDatabase
import iad1tya.echo.music.LocalDownloadUtil
import iad1tya.echo.music.LocalIsInPipMode
import iad1tya.echo.music.LocalListenTogetherManager
import iad1tya.echo.music.LocalPlayerConnection
import iad1tya.echo.music.R
import iad1tya.echo.music.constants.EnableLyricsThumbnailPlayPauseKey
import iad1tya.echo.music.constants.HighPerformanceModeKey
import iad1tya.echo.music.constants.PlayerButtonsStyle
import iad1tya.echo.music.constants.QueuePeekHeight
import iad1tya.echo.music.constants.SafeVolumeEnabledKey
import iad1tya.echo.music.constants.ShowCodecOnPlayerKey
import iad1tya.echo.music.echomusic.AudioDeviceBottomSheet
import iad1tya.echo.music.extensions.togglePlayPause
import iad1tya.echo.music.extensions.toggleRepeatMode
import iad1tya.echo.music.listentogether.RoomRole
import iad1tya.echo.music.models.rememberResolvedAlbum
import iad1tya.echo.music.playback.ExoDownloadService
import iad1tya.echo.music.ui.component.BottomSheet
import iad1tya.echo.music.ui.component.BottomSheetState
import iad1tya.echo.music.ui.component.CastButton
import iad1tya.echo.music.ui.component.LocalBottomSheetPageState
import iad1tya.echo.music.ui.component.LocalMenuState
import iad1tya.echo.music.ui.component.PlayerProgressSlider
import iad1tya.echo.music.ui.component.rememberBottomSheetState
import iad1tya.echo.music.ui.player.CanvasArtworkPlaybackCache
import iad1tya.echo.music.ui.player.rememberCanvasAnimationEnabled
import iad1tya.echo.music.ui.player.InlineLyricsView
import iad1tya.echo.music.ui.player.MiniPlayer
import iad1tya.echo.music.ui.player.Thumbnail
import iad1tya.echo.music.ui.player.ThumbnailHost
import iad1tya.echo.music.ui.player.BottomSheetPlayer
import iad1tya.echo.music.ui.player.HideStatusBarOnFullscreenEffect
import iad1tya.echo.music.ui.player.rememberPlayerButtonColors
import iad1tya.echo.music.ui.player.rememberPlayerButtonsStyle
import iad1tya.echo.music.ui.player.rememberSwipeLyricsEnabled
import iad1tya.echo.music.ui.player.swipeLyricsGestureArmed
import iad1tya.echo.music.ui.player.swipeLyricsToChangeSong
import iad1tya.echo.music.ui.screens.equalizer.axion.AxionEqViewModel
import iad1tya.echo.music.ui.utils.ShowMediaInfo
import iad1tya.echo.music.ui.utils.ShowOffsetDialog
import iad1tya.echo.music.ui.utils.rememberIsWideLayout
import iad1tya.echo.music.utils.makeTimeString
import iad1tya.echo.music.utils.rememberPreference
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import androidx.core.net.toUri
import kotlin.math.roundToInt

/**
 * # "Interfaz nueva" — Reproductor
 *
 * The FOURTEENTH shape of the player, selected by [NewUiEnabledKey] through [BottomSheetPlayerHost].
 * It does not replace the thirteen classic shapes; it stands beside them.
 *
 * ## What this file is and is not
 * It is **presentation only**. Every action below routes through the SAME `PlayerConnection` /
 * `DownloadService` / `navController` call the classic player uses — there is one copy of the
 * behaviour, so one place to fix a bug. Nothing here writes a preference the classic player does not
 * already write, and toggling the beta flag off restores classic behaviour with no migration.
 *
 * ## Shape delegation (deliberate)
 * Landscape, wide/TV/car layouts and video mode are NOT re-drawn here — they fall straight through to
 * [BottomSheetPlayer]. Those shapes carry the immersive video surface, the landscape canvas, the split
 * queue pane and the D-pad focus ring; re-implementing them would have meant re-implementing ~1500
 * lines of behaviour the inventory documents (§2.7–§2.11) with no mockup to match. Delegating keeps
 * every one of those functions bit-identical while the new look owns the portrait audio/canvas shape,
 * which is the shape the render draws.
 *
 * ## Thermal / battery
 * No blur, no palette extraction, no per-frame shader. The ambient bloom is [AuraBloomColors.Brand],
 * resolved through [AuraBloomCache] (per track, never per frame). The position ticker is the classic
 * one (500 ms, 1 s in High-Performance Mode).
 */
@Composable
fun AuraPlayer(
    state: BottomSheetState,
    navController: NavController,
    pureBlack: Boolean,
    modifier: Modifier = Modifier,
) {
    val playerConnection = LocalPlayerConnection.current ?: return
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val isWideLayout = rememberIsWideLayout()
    val videoMode by playerConnection.videoMode.collectAsState()

    // Read unconditionally, branch afterwards: the composable call order must not shift with the shape.
    if (isLandscape || isWideLayout || videoMode) {
        BottomSheetPlayer(
            state = state,
            navController = navController,
            modifier = modifier,
            pureBlack = pureBlack,
        )
        return
    }

    AuraPortraitPlayer(
        state = state,
        navController = navController,
        pureBlack = pureBlack,
        modifier = modifier,
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AuraPortraitPlayer(
    state: BottomSheetState,
    navController: NavController,
    pureBlack: Boolean,
    modifier: Modifier,
) {
    val playerConnection = LocalPlayerConnection.current ?: return
    val context = LocalContext.current
    val database = LocalDatabase.current
    val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val menuState = LocalMenuState.current
    val bottomSheetPageState = LocalBottomSheetPageState.current

    val mediaMetadata by playerConnection.mediaMetadata.collectAsState()
    val currentSong by playerConnection.currentSong.collectAsState(initial = null)
    val currentLyrics by playerConnection.currentLyrics.collectAsState(initial = null)
    val isPlaying by playerConnection.isPlaying.collectAsState()
    val playbackState by playerConnection.playbackState.collectAsState()
    val repeatMode by playerConnection.repeatMode.collectAsState()
    val shuffleModeEnabled by playerConnection.shuffleModeEnabled.collectAsState()
    val canSkipPrevious by playerConnection.canSkipPrevious.collectAsState()
    val canSkipNext by playerConnection.canSkipNext.collectAsState()
    val isMuted by playerConnection.isMuted.collectAsState()
    val automix by playerConnection.service.automixItems.collectAsState()
    val currentFormatEntity by database.format(mediaMetadata?.id).collectAsState(initial = null)
    val download by LocalDownloadUtil.current.getDownload(mediaMetadata?.id ?: "")
        .collectAsState(initial = null)

    val listenTogetherManager = LocalListenTogetherManager.current
    val listenTogetherRoleState = listenTogetherManager?.role?.collectAsState(initial = RoomRole.NONE)
    val isListenTogetherGuest = listenTogetherRoleState?.value == RoomRole.GUEST

    val castHandler = remember(playerConnection) {
        runCatching { playerConnection.service.castConnectionHandler }.getOrNull()
    }
    val isCasting by castHandler?.isCasting?.collectAsState() ?: remember { mutableStateOf(false) }
    val castPosition by castHandler?.castPosition?.collectAsState() ?: remember { mutableLongStateOf(0L) }
    val castDuration by castHandler?.castDuration?.collectAsState() ?: remember { mutableLongStateOf(0L) }
    val castIsPlaying by castHandler?.castIsPlaying?.collectAsState() ?: remember { mutableStateOf(false) }
    val effectiveIsPlaying = if (isCasting) castIsPlaying else isPlaying

    val highPerfMode by rememberPreference(HighPerformanceModeKey, false)

    // ── "Estilo de los botones del reproductor" (PlayerButtonsStyleKey) ───────────────────────────
    // The row lives in Ajustes ▸ Apariencia and is reachable with the new UI on (AuraSettingsScreen
    // navigates to the SAME "settings/appearance" screen), so a transport that ignored it would be a
    // control the user can change that does nothing. The colours come from the CLASSIC derivation —
    // [rememberPlayerButtonColors] — not from a second copy: `overDarkBackground = true` because this
    // shape always paints over [AuraPalette.Ground], a near-black.
    val playerButtonsStyle = rememberPlayerButtonsStyle()
    val classicButtonColors = rememberPlayerButtonColors(
        style = playerButtonsStyle,
        overDarkBackground = true,
        useDarkTheme = true,
    )
    // DEFAULT means "the app's default look", which for THIS shape is the render's gradient button and
    // teal accents — mapping DEFAULT to the classic white/black pair would silently repaint the new
    // player's identity. PRIMARY / TERTIARY take the theme colours the classic transport uses, so
    // picking either one visibly changes this transport too.
    val isDefaultButtons = playerButtonsStyle == PlayerButtonsStyle.DEFAULT
    val transportAccent: Color =
        if (isDefaultButtons) AuraPalette.Teal else classicButtonColors.textButtonColor
    val playButtonFill: Brush =
        if (isDefaultButtons) AuraPalette.PlayButtonGradient
        else SolidColor(classicButtonColors.textButtonColor)
    val playButtonInk: Color =
        if (isDefaultButtons) AuraPalette.OnAccent else classicButtonColors.iconButtonColor

    // ── Position / duration (same ticker the classic player uses) ─────────────────────────────────
    val positionState = remember { mutableLongStateOf(0L) }
    val durationState = remember { mutableLongStateOf(0L) }
    var position by positionState
    var duration by durationState
    var sliderPosition by remember { mutableStateOf<Long?>(null) }
    var lastManualSeekTime by remember { mutableLongStateOf(0L) }

    LaunchedEffect(isPlaying, isCasting, highPerfMode) {
        if (!isCasting && isPlaying) {
            while (isActive) {
                delay(if (highPerfMode) 1000L else 500L)
                if (sliderPosition == null) {
                    position = playerConnection.player.currentPosition
                    duration = playerConnection.player.duration
                }
            }
        }
    }
    LaunchedEffect(playbackState, mediaMetadata?.id) {
        if (!isCasting) {
            position = playerConnection.player.currentPosition
            duration = playerConnection.player.duration
        }
    }
    LaunchedEffect(isCasting, castPosition, castDuration) {
        if (isCasting && sliderPosition == null) {
            if (System.currentTimeMillis() - lastManualSeekTime > 1500) {
                position = castPosition
                if (castDuration > 0) duration = castDuration
            }
        }
    }
    val effectivePosition = if (isCasting) castPosition else position

    // Same automix top-up the classic player runs (kept as an effect, never in the composition body).
    LaunchedEffect(canSkipNext, automix) {
        if (!canSkipNext && automix.isNotEmpty()) {
            playerConnection.service.addToQueueAutomix(automix[0], 0)
        }
    }

    // Mirror the sheet's expanded state into the service, exactly as the classic player does.
    DisposableEffect(state.isExpanded) {
        playerConnection.setPlayerSheetExpanded(state.isExpanded)
        onDispose { playerConnection.setPlayerSheetExpanded(false) }
    }

    // ── Codec / bit depth / sample rate, read off the live track (never invented) ──────────────────
    val isCrossfading by playerConnection.isCrossfading.collectAsState()
    var currentAudioFormat by remember { mutableStateOf<Format?>(null) }
    DisposableEffect(playerConnection, isCrossfading) {
        val target = playerConnection.player
        val listener = object : Player.Listener {
            override fun onTracksChanged(tracks: androidx.media3.common.Tracks) {
                currentAudioFormat = tracks.groups.firstOrNull { it.type == C.TRACK_TYPE_AUDIO }
                    ?.getTrackFormat(0)
            }
        }
        target.addListener(listener)
        currentAudioFormat = target.currentTracks.groups
            .firstOrNull { it.type == C.TRACK_TYPE_AUDIO }?.getTrackFormat(0)
        onDispose { target.removeListener(listener) }
    }
    // "Mostrar códec en el reproductor" (ShowCodecOnPlayerKey, default OFF) governs the format read-out
    // in BOTH players. It covers everything derived from the decoder's Format — the `FLAC · 24 BIT ·
    // 96 kHz` chips AND the `◆ HI-RES` badge, which is just the same bit depth / sample rate stated as a
    // verdict. Turning it off in Ajustes must silence all of it, or the switch is a half-dead control.
    val showCodecOnPlayer by rememberPreference(ShowCodecOnPlayerKey, false)
    val techInfo = remember(currentAudioFormat, currentFormatEntity, showCodecOnPlayer) {
        if (!showCodecOnPlayer) AuraTechInfo(emptyList(), isHiRes = false)
        else auraTechnicalInfo(currentAudioFormat, currentFormatEntity?.codecs, currentFormatEntity?.sampleRate)
    }

    // ── Canvas presence, for the "CANVAS ▸ EN MOVIMIENTO" badge of the render ──────────────────────
    // The badge describes the canvas [Thumbnail] is drawing, so it asks THE canvas gate — the same
    // [rememberCanvasAnimationEnabled] that decides whether CanvasArtworkPlayer runs at all. It used to
    // keep a private copy of the condition (wrong default, plus a data-saver term the real gate never
    // had), which let the label claim a canvas that was not playing. A label that lies is a placebo.
    val canvasEnabled = rememberCanvasAnimationEnabled()
    var canvasAvailable by remember(mediaMetadata?.id) { mutableStateOf(false) }
    LaunchedEffect(mediaMetadata?.id, canvasEnabled) {
        canvasAvailable = false
        val id = mediaMetadata?.id ?: return@LaunchedEffect
        if (!canvasEnabled) return@LaunchedEffect
        // The canvas cache is a plain map (not snapshot-aware) filled by an async fetch. Ten cheap reads
        // spread over ~6 s per TRACK — not per frame — is all it takes to notice it landed.
        repeat(10) {
            if (CanvasArtworkPlaybackCache.get(id) != null) {
                canvasAvailable = true
                return@LaunchedEffect
            }
            delay(600)
        }
    }

    // ── Lyrics / fullscreen ───────────────────────────────────────────────────────────────────────
    var showInlineLyrics by rememberSaveable { mutableStateOf(false) }
    var isFullScreen by rememberSaveable { mutableStateOf(false) }
    val enableLyricsThumbnailPlayPause by rememberPreference(EnableLyricsThumbnailPlayPauseKey, false)
    val swipeLyrics = rememberSwipeLyricsEnabled()

    // "Ocultar la barra de estado en pantalla completa" — SAME effect the classic player runs
    // (PlayerAppearancePrefs.kt), so this shape's own fullscreen lyrics mode obeys the switch instead
    // of ignoring the insets controller entirely. `&& showInlineLyrics` because the flag is
    // rememberSaveable and only the lyrics header can set it: the switch's own description says "while
    // the fullscreen LYRICS mode is active", and the bar must come back when the lyrics close.
    HideStatusBarOnFullscreenEffect(isFullScreen = isFullScreen && showInlineLyrics)

    // ── Sleep timer (the merged menu's "Temporizador" opens this) ──────────────────────────────────
    var showSleepTimerDialog by remember { mutableStateOf(false) }
    var sleepTimerValue by remember { mutableFloatStateOf(30f) }
    if (showSleepTimerDialog) {
        AlertDialog(
            properties = DialogProperties(usePlatformDefaultWidth = false),
            onDismissRequest = { showSleepTimerDialog = false },
            title = { Text(stringResource(R.string.sleep_timer)) },
            confirmButton = {
                TextButton(onClick = {
                    showSleepTimerDialog = false
                    playerConnection.service.sleepTimer.start(sleepTimerValue.roundToInt())
                }) { Text(stringResource(android.R.string.ok)) }
            },
            dismissButton = {
                TextButton(onClick = { showSleepTimerDialog = false }) {
                    Text(stringResource(android.R.string.cancel))
                }
            },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = pluralStringResource(
                            R.plurals.minute,
                            sleepTimerValue.roundToInt(),
                            sleepTimerValue.roundToInt(),
                        ),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Slider(
                        value = sleepTimerValue,
                        onValueChange = { sleepTimerValue = it },
                        valueRange = 5f..120f,
                        steps = (120 - 5) / 5 - 1,
                    )
                    OutlinedIconButton(onClick = {
                        showSleepTimerDialog = false
                        playerConnection.service.sleepTimer.start(-1)
                    }) { Text(stringResource(R.string.end_of_song)) }
                }
            },
        )
    }

    // ── The audio-output picker (classic reaches it only from the Queue — carried here) ────────────
    var showAudioDeviceSheet by remember { mutableStateOf(false) }
    if (showAudioDeviceSheet) {
        AudioDeviceBottomSheet(onDismiss = { showAudioDeviceSheet = false })
    }

    // ── Queue sheet ───────────────────────────────────────────────────────────────────────────────
    val dismissedBound = QueuePeekHeight +
        WindowInsets.systemBars.asPaddingValues().calculateBottomPadding()
    val queueSheetState = rememberBottomSheetState(
        dismissedBound = dismissedBound,
        expandedBound = state.expandedBound,
        collapsedBound = dismissedBound + 1.dp,
        initialAnchor = 1,
    )

    val bloom = rememberAuraBloom(mediaMetadata?.id)

    BottomSheet(
        state = state,
        modifier = modifier,
        background = {
            Box(Modifier.fillMaxSize().auraScreenBackground(bloom))
        },
        // DESTRUCTIVE GESTURE, PRESERVED VERBATIM: dragging the sheet below the dismiss threshold stops
        // playback and wipes the queue + automix. Identical to the classic player (Player.kt:1550).
        onDismiss = {
            playerConnection.service.clearAutomix()
            playerConnection.player.stop()
            playerConnection.player.clearMediaItems()
        },
        collapsedContent = {
            MiniPlayer(
                positionState = positionState,
                durationState = durationState,
                shouldBindVideoSurface = state.isCollapsed && !LocalIsInPipMode.current,
            )
        },
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Horizontal))
                .padding(bottom = queueSheetState.collapsedBound)
                // GESTURE: swipe UP anywhere on the player body drags the queue sheet open. Mirrors the
                // classic implementation (Player.kt:3459) — only clearly-upward drags are claimed, so the
                // parent sheet's own downward collapse/dismiss still works.
                .pointerInput(queueSheetState, state) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val drag = awaitVerticalTouchSlopOrCancellation(down.id) { change, overSlop ->
                            if (overSlop < 0f && state.isExpanded && !isFullScreen && !queueSheetState.isExpanded) {
                                change.consume()
                                queueSheetState.dispatchRawDelta(overSlop)
                            }
                        }
                        if (drag != null) {
                            val velocityTracker = VelocityTracker()
                            velocityTracker.addPointerInputChange(drag)
                            verticalDrag(drag.id) { change ->
                                velocityTracker.addPointerInputChange(change)
                                queueSheetState.dispatchRawDelta(change.positionChange().y)
                                change.consume()
                            }
                            queueSheetState.performFling(-velocityTracker.calculateVelocity().y, null)
                        }
                    }
                },
        ) {
            // ── Cabecera ──────────────────────────────────────────────────────────────────────────
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Top))
                    .padding(horizontal = 8.dp),
            ) {
                AuraIconButton(
                    icon = AuraIcons.ChevronDown,
                    contentDescription = "Minimizar el reproductor",
                    onClick = { state.collapseSoft() },
                    size = 22.dp,
                    tint = AuraPalette.OnGround.copy(alpha = 0.6f),
                )
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    if (techInfo.isHiRes) {
                        AuraTechnicalText(text = "◆ HI-RES", color = AuraPalette.Teal)
                    }
                }
                if (showInlineLyrics) {
                    // §2.1 — the lyrics header controls only exist while the lyrics are open.
                    AuraIconButton(
                        icon = AuraIcons.Lyrics,
                        contentDescription = "Pantalla completa de la letra",
                        onClick = { isFullScreen = !isFullScreen },
                        size = 20.dp,
                        tint = if (isFullScreen) AuraPalette.Teal else AuraPalette.OnGround.copy(alpha = 0.6f),
                    )
                }
                AuraIconButton(
                    icon = AuraIcons.More,
                    contentDescription = if (showInlineLyrics) "Menú de la letra" else "Más opciones",
                    onClick = {
                        val meta = mediaMetadata
                        if (showInlineLyrics) {
                            if (meta != null) {
                                menuState.show {
                                    iad1tya.echo.music.ui.menu.LyricsMenu(
                                        lyricsProvider = { currentLyrics },
                                        songProvider = { currentSong?.song },
                                        mediaMetadataProvider = { meta },
                                        onDismiss = menuState::dismiss,
                                        onShowOffsetDialog = {
                                            bottomSheetPageState.show {
                                                ShowOffsetDialog(songProvider = { currentSong?.song })
                                            }
                                        },
                                    )
                                }
                            }
                        } else {
                            menuState.show {
                                PlayerMenuHost(
                                    mediaMetadata = meta,
                                    navController = navController,
                                    playerBottomSheetState = state,
                                    onShowDetailsDialog = {
                                        meta?.id?.let { id -> bottomSheetPageState.show { ShowMediaInfo(id) } }
                                    },
                                    onDismiss = menuState::dismiss,
                                    onSleepTimer = { showSleepTimerDialog = true },
                                )
                            }
                        }
                    },
                    size = 22.dp,
                    tint = AuraPalette.OnGround.copy(alpha = 0.6f),
                )
            }

            // ── Portada / letra. THE ARTWORK AREA IS THE CLASSIC [Thumbnail] ──────────────────────
            // Canvas is content, not decoration: [Thumbnail] is the composable that hosts the animated
            // canvas (CanvasArtworkPlayer), the cover carousel, the double-tap seek with its
            // accumulating multiplier, the centre double-tap play/pause, the seek overlay, the
            // rotating cover, the hidden-thumbnail placeholder and the playback-error retry. Rebuilding
            // it would have meant rebuilding ~55 gestures; reusing it keeps every one of them.
            Box(
                contentAlignment = Alignment.BottomStart,
                modifier = Modifier.weight(1f).fillMaxWidth(),
            ) {
                if (showInlineLyrics) {
                    Box(Modifier.fillMaxSize().padding(horizontal = AuraSpacing.Gutter)) {
                        InlineLyricsView(
                            mediaMetadata = mediaMetadata,
                            showLyrics = true,
                            positionProvider = { sliderPosition ?: if (isCasting) castPosition else null },
                        )
                    }
                    if (isFullScreen && enableLyricsThumbnailPlayPause) {
                        // §2.1 — the 56 dp cover doubles as play/pause while the lyrics are full screen.
                        AuraArtwork(
                            size = 56.dp,
                            placeholderSeed = mediaMetadata?.id,
                            modifier = Modifier
                                .padding(AuraSpacing.Gutter)
                                .clip(AuraShapes.Artwork)
                                .pointerInput(Unit) {
                                    detectTapGestures { playerConnection.togglePlayPause() }
                                },
                        ) {
                            AsyncImage(
                                model = mediaMetadata?.thumbnailUrl,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    }
                } else {
                    val currentSliderPosition by rememberUpdatedState(sliderPosition)
                    val sliderPositionProvider = remember { { currentSliderPosition } }
                    Thumbnail(
                        sliderPositionProvider = sliderPositionProvider,
                        modifier = Modifier.nestedScroll(state.preUpPostDownNestedScrollConnection),
                        isPlayerExpanded = { state.isExpanded },
                        isListenTogetherGuest = isListenTogetherGuest,
                        // THIS is what makes the artwork and the canvas appear at all. [Thumbnail] used to
                        // hide its whole cover/carousel/canvas block whenever the background style was
                        // APPLE_MUSIC and the layout was not landscape — a rule that belongs to the CLASSIC
                        // portrait player, which paints the cover full-screen as its background. This shape
                        // paints `auraScreenBackground(bloom)` and no full-screen cover, so inheriting that
                        // rule left an empty weight(1f) box at the shipped default (App.kt:604 seeds
                        // APPLE_MUSIC on every fresh install). Declaring the host fixes it for ALL SEVEN
                        // style values at once instead of special-casing one.
                        host = ThumbnailHost.OPAQUE_DARK,
                    )
                    // Both terms: the gate says a canvas MAY play, the cache says one really landed for
                    // this track. Either going false (thermal throttle, the switch, a track with no
                    // canvas) takes the badge with it.
                    if (canvasEnabled && canvasAvailable) {
                        AuraTechnicalText(
                            text = "CANVAS ▸ EN MOVIMIENTO",
                            color = AuraPalette.OnGround.copy(alpha = 0.75f),
                            modifier = Modifier
                                .padding(AuraSpacing.Gutter)
                                .clip(AuraShapes.Highlight)
                                .background(AuraPalette.Ground.copy(alpha = 0.6f))
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                        )
                    }
                }
            }

            val meta = mediaMetadata
            if (meta != null) {
                // ── Título y artista ──────────────────────────────────────────────────────────────
                val resolvedAlbum = rememberResolvedAlbum(
                    songId = meta.id,
                    initial = meta.album,
                    dbAlbumId = null,
                    dbAlbumName = null,
                )
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        // "Deslizar en la letra para cambiar de canción" (SwipeLyricsKey) — the same
                        // gesture the classic player now attaches to ITS title/artist block. This block
                        // stays on screen while the lyrics are full screen, which is exactly the area
                        // the switch's description names.
                        .swipeLyricsToChangeSong(
                            enabled = swipeLyricsGestureArmed(
                                swipeLyricsEnabled = swipeLyrics,
                                lyricsVisible = showInlineLyrics,
                                lyricsFullScreen = isFullScreen,
                                isListenTogetherGuest = isListenTogetherGuest,
                            ),
                            onPrevious = { if (canSkipPrevious) playerConnection.seekToPrevious() },
                            onNext = { if (canSkipNext) playerConnection.seekToNext() },
                        )
                        .padding(horizontal = AuraSpacing.Gutter, vertical = 0.dp),
                ) {
                    Spacer(Modifier.height(14.dp))
                    Text(
                        text = meta.title,
                        style = AuraType.PlayerTitle,
                        color = AuraPalette.OnGround,
                        maxLines = 1,
                        overflow = AuraDefaultOverflow,
                        modifier = Modifier
                            .fillMaxWidth()
                            .basicMarquee(iterations = 1, initialDelayMillis = 3000, velocity = 30.dp)
                            .combinedClickable(
                                indication = null,
                                interactionSource = remember { MutableInteractionSource() },
                                role = Role.Button,
                                onClick = {
                                    resolvedAlbum?.let { album ->
                                        navController.navigate("album/${album.id}")
                                        state.collapseSoft()
                                    }
                                },
                                // GESTURE: long-press copies the title (same string, same Toast).
                                onLongClick = {
                                    val label = context.getString(R.string.copied_title)
                                    clipboardManager.setPrimaryClip(ClipData.newPlainText(label, meta.title))
                                    Toast.makeText(context, label, Toast.LENGTH_SHORT).show()
                                },
                            ),
                    )
                    if (meta.artists.any { it.name.isNotBlank() }) {
                        val artistText = meta.artists.joinToString(", ") { it.name }
                        Text(
                            text = artistText,
                            style = AuraType.PlayerArtist,
                            color = AuraPalette.OnGroundMuted,
                            maxLines = 1,
                            overflow = AuraDefaultOverflow,
                            modifier = Modifier
                                .fillMaxWidth()
                                .basicMarquee(iterations = 1, initialDelayMillis = 3000, velocity = 30.dp)
                                .combinedClickable(
                                    indication = null,
                                    interactionSource = remember { MutableInteractionSource() },
                                    role = Role.Button,
                                    onClick = {
                                        meta.artists.firstOrNull { !it.id.isNullOrBlank() }?.id?.let { artistId ->
                                            navController.navigate("artist/$artistId")
                                            state.collapseSoft()
                                        }
                                    },
                                    // GESTURE: long-press copies the artist.
                                    onLongClick = {
                                        val label = context.getString(R.string.copied_artist)
                                        clipboardManager.setPrimaryClip(ClipData.newPlainText(label, artistText))
                                        Toast.makeText(context, label, Toast.LENGTH_SHORT).show()
                                    },
                                ),
                        )
                    }

                    // ── Datos técnicos: FLAC · 24 BIT · 96 kHz ────────────────────────────────────
                    // techInfo.chips is already empty when "mostrar códec" is off (see the read site
                    // above). The crossfade chip is NOT codec data — the classic player shows it
                    // independently of that switch — so it keeps its own condition.
                    if (techInfo.chips.isNotEmpty() || isCrossfading) {
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            techInfo.chips.forEach { chip ->
                                AuraTechnicalText(
                                    text = chip.text,
                                    color = if (chip.highlight) AuraPalette.Teal else AuraPalette.OnGroundFaint,
                                )
                            }
                            if (isCrossfading) {
                                // Same string resource the classic crossfade chip uses — never retyped,
                                // never re-cased at runtime.
                                AuraTechnicalText(
                                    text = stringResource(R.string.crossfading),
                                    color = AuraPalette.Blue,
                                )
                            }
                        }
                    }

                    // ── Barra de progreso + tiempos ───────────────────────────────────────────────
                    // "Estilo de la barra de progreso" (DEFAULT / ONDULADO / SQUIGGLY / FINO) is a live
                    // control here: this is the SAME [PlayerProgressSlider] the classic player calls, and
                    // that composable is the only place SliderStyleKey/SquigglySliderKey are read. There is
                    // no second timeline to keep in sync, so the setting cannot go quiet in one UI.
                    Spacer(Modifier.height(14.dp))
                    PlayerProgressSlider(
                        value = (sliderPosition ?: effectivePosition).toFloat(),
                        valueRange = 0f..(if (duration == C.TIME_UNSET) 0f else duration.toFloat()),
                        onValueChange = { sliderPosition = it.toLong() },
                        onValueChangeFinished = {
                            sliderPosition?.let {
                                if (isCasting) {
                                    lastManualSeekTime = System.currentTimeMillis()
                                    castHandler?.seekTo(it)
                                } else {
                                    playerConnection.player.seekTo(it)
                                }
                                position = it
                            }
                            sliderPosition = null
                        },
                        enabled = !isListenTogetherGuest,
                        colors = auraSliderColors(),
                        isPlaying = effectiveIsPlaying,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        AuraTechnicalText(
                            text = makeTimeString(sliderPosition ?: effectivePosition),
                            style = AuraType.Timecode,
                        )
                        AuraTechnicalText(
                            text = if (duration != C.TIME_UNSET) makeTimeString(duration) else "",
                            style = AuraType.Timecode,
                        )
                    }
                }

                // ── Transporte ────────────────────────────────────────────────────────────────────
                Spacer(Modifier.height(12.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    AuraIconButton(
                        icon = AuraIcons.Shuffle,
                        contentDescription = stringResource(R.string.shuffle),
                        onClick = { playerConnection.player.shuffleModeEnabled = !shuffleModeEnabled },
                        enabled = !isListenTogetherGuest,
                        size = 24.dp,
                        tint = if (shuffleModeEnabled) transportAccent else AuraPalette.OnGroundFaint,
                    )
                    AuraIconButton(
                        icon = AuraIcons.SkipPrevious,
                        contentDescription = stringResource(R.string.previous),
                        onClick = playerConnection::seekToPrevious,
                        enabled = canSkipPrevious && !isListenTogetherGuest,
                        size = 34.dp,
                        tint = AuraPalette.OnGround.copy(alpha = 0.9f),
                    )
                    Spacer(Modifier.width(6.dp))
                    AuraPlayButton(
                        isPlaying = if (isListenTogetherGuest) !isMuted else effectiveIsPlaying,
                        contentDescription = when {
                            isListenTogetherGuest ->
                                if (isMuted) stringResource(R.string.unmute) else stringResource(R.string.mute)
                            effectiveIsPlaying -> stringResource(R.string.pause)
                            else -> stringResource(R.string.play)
                        },
                        onClick = {
                            when {
                                // Same three branches the classic transport has: guest = mute,
                                // casting = the remote device, ENDED = restart, otherwise play/pause.
                                isListenTogetherGuest -> playerConnection.toggleMute()
                                isCasting -> if (castIsPlaying) castHandler?.pause() else castHandler?.play()
                                playbackState == Player.STATE_ENDED -> {
                                    playerConnection.player.seekTo(0, 0)
                                    playerConnection.player.playWhenReady = true
                                }
                                else -> playerConnection.togglePlayPause()
                            }
                        },
                        fill = playButtonFill,
                        ink = playButtonInk,
                    )
                    Spacer(Modifier.width(6.dp))
                    AuraIconButton(
                        icon = AuraIcons.SkipNext,
                        contentDescription = stringResource(R.string.next),
                        onClick = playerConnection::seekToNext,
                        enabled = canSkipNext && !isListenTogetherGuest,
                        size = 34.dp,
                        tint = AuraPalette.OnGround.copy(alpha = 0.9f),
                    )
                    Box(contentAlignment = Alignment.Center) {
                        AuraIconButton(
                            icon = AuraIcons.Repeat,
                            contentDescription = stringResource(R.string.repeat),
                            onClick = { playerConnection.player.toggleRepeatMode() },
                            enabled = !isListenTogetherGuest,
                            size = 24.dp,
                            tint = if (repeatMode != Player.REPEAT_MODE_OFF) transportAccent
                            else AuraPalette.OnGroundDisabled,
                        )
                        if (repeatMode == Player.REPEAT_MODE_ONE) {
                            // The render draws ONE repeat glyph, but the control has THREE states —
                            // mark "repetir una" so it stays distinguishable from "repetir todo".
                            AuraTechnicalText(
                                text = "1",
                                color = transportAccent,
                                modifier = Modifier.align(Alignment.BottomEnd).padding(6.dp),
                            )
                        }
                    }
                }

                // ── Cinco accesos rápidos ─────────────────────────────────────────────────────────
                Spacer(Modifier.height(6.dp))
                val liked = currentSong?.song?.liked == true
                Row(
                    horizontalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    AuraIconButton(
                        icon = AuraIcons.Volume,
                        contentDescription = "Volumen y salida de audio",
                        onClick = { showAudioDeviceSheet = true },
                        size = 22.dp,
                        tint = AuraPalette.OnGround.copy(alpha = 0.7f),
                    )
                    AuraIconButton(
                        icon = if (liked) AuraIcons.HeartFilled else AuraIcons.Heart,
                        contentDescription = stringResource(R.string.action_like),
                        onClick = playerConnection::toggleLike,
                        size = 22.dp,
                        tint = if (liked) transportAccent else AuraPalette.OnGround.copy(alpha = 0.7f),
                    )
                    AuraIconButton(
                        icon = AuraIcons.Lyrics,
                        contentDescription = stringResource(R.string.lyrics),
                        onClick = { showInlineLyrics = !showInlineLyrics },
                        size = 22.dp,
                        tint = if (showInlineLyrics) transportAccent else AuraPalette.OnGround.copy(alpha = 0.7f),
                    )
                    AuraIconButton(
                        icon = AuraIcons.Queue,
                        contentDescription = stringResource(R.string.queue),
                        onClick = { queueSheetState.expandSoft() },
                        size = 22.dp,
                        tint = AuraPalette.OnGround.copy(alpha = 0.7f),
                    )
                    Box(contentAlignment = Alignment.Center) {
                        AuraIconButton(
                            icon = if (download?.state == Download.STATE_COMPLETED) AuraIcons.Check
                            else AuraIcons.Download,
                            contentDescription = stringResource(R.string.action_download),
                            onClick = {
                                when (download?.state) {
                                    Download.STATE_COMPLETED, Download.STATE_QUEUED, Download.STATE_DOWNLOADING ->
                                        DownloadService.sendRemoveDownload(
                                            context, ExoDownloadService::class.java, meta.id, false,
                                        )
                                    else -> {
                                        database.transaction { insert(meta) }
                                        DownloadService.sendAddDownload(
                                            context,
                                            ExoDownloadService::class.java,
                                            DownloadRequest.Builder(meta.id, meta.id.toUri())
                                                .setCustomCacheKey(meta.id)
                                                .setData(meta.title.toByteArray())
                                                .build(),
                                            false,
                                        )
                                    }
                                }
                            },
                            size = 22.dp,
                            tint = if (download?.state == Download.STATE_COMPLETED) transportAccent
                            else AuraPalette.OnGround.copy(alpha = 0.7f),
                        )
                        if (download?.state == Download.STATE_QUEUED ||
                            download?.state == Download.STATE_DOWNLOADING
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(22.dp),
                                strokeWidth = 2.dp,
                                color = AuraPalette.Teal,
                            )
                        }
                    }
                }
            }

            // ── Barra de estado del motor ─────────────────────────────────────────────────────────
            Spacer(Modifier.height(8.dp))
            AuraEngineStatusBar()
        }

        // CAST: pinned top-right in every layout, exactly as the classic player pins it. FOSS = no-op.
        if (!LocalIsInPipMode.current && !queueSheetState.isExpanded) {
            CastButton(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .windowInsetsPadding(
                        WindowInsets.systemBars.only(WindowInsetsSides.Top + WindowInsetsSides.End)
                    )
                    .padding(horizontal = 52.dp, vertical = 20.dp)
                    .size(22.dp),
                tintColor = AuraPalette.OnGround,
            )
        }

        if (!isFullScreen) {
            QueueHost(
                state = queueSheetState,
                playerBottomSheetState = state,
                navController = navController,
                background = AuraPalette.Ground,
                onBackgroundColor = AuraPalette.OnGround,
                textBackgroundColor = AuraPalette.OnGround,
                textButtonColor = AuraPalette.OnGround,
                iconButtonColor = AuraPalette.OnAccent,
                pureBlack = pureBlack,
                showInlineLyrics = showInlineLyrics,
                onToggleLyrics = { showInlineLyrics = !showInlineLyrics },
            )
        }
    }
}

// ── Engine status bar ─────────────────────────────────────────────────────────────────────────────

/**
 * `EQ ON · V.SEGURO ON · −1.0 dBFS` — the render's engine bar, reporting the REAL engine state.
 *
 * The ceiling is only shown as `−1.0 dBFS` while Safe Volume is on, because that is the only time the
 * limiter runs (`SuperpoweredBridge.cpp:482`, `AudioGain.kt:56`). With Safe Volume off there is no
 * limiter, so the bar says so instead of printing a number that is not true.
 *
 * Read-only: the EQ flag comes from the `echo_eq_prefs` file the EQ view model owns. Nothing here
 * writes to it, so opening the new player can never change a sound setting.
 */
@Composable
private fun AuraEngineStatusBar(modifier: Modifier = Modifier) {
    val eqEnabled = rememberEqEnabledReadOnly()
    val safeVolume by rememberPreference(SafeVolumeEnabledKey, false)

    Column(modifier.fillMaxWidth()) {
        AuraDivider()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AuraSpacing.Gutter, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            AuraEngineStat(label = "EQ", value = if (eqEnabled) "ON" else "OFF", on = eqEnabled)
            AuraEngineStat(label = "V.SEGURO", value = if (safeVolume) "ON" else "OFF", on = safeVolume)
            AuraTechnicalText(text = if (safeVolume) "−1.0 dBFS" else "SIN LÍMITE")
        }
    }
}

@Composable
private fun AuraEngineStat(label: String, value: String, on: Boolean) {
    Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        AuraTechnicalText(text = label)
        AuraTechnicalText(
            text = value,
            color = if (on) AuraPalette.Teal else AuraPalette.OnGroundDisabled,
        )
    }
}

/**
 * Reactive, READ-ONLY view of the equalizer's own on/off flag, taken from the view model that OWNS it.
 *
 * This used to open `echo_eq_prefs` and its `"enabled"` key by hand — a second read path onto a file
 * whose schema belongs to [AxionEqViewModel]. Two paths onto one key drift: rename the key, add a
 * per-output override, change the default, and the engine bar keeps reporting the old answer while the
 * EQ screen reports the new one. There is now ONE reader — [AxionEqViewModel.enabled] — and this bar
 * consumes it like any other consumer.
 *
 * Still strictly read-only: nothing here calls a mutator, so opening the new player cannot change a
 * sound setting. Constructing the view model is side-effect-free for audio by design — its `init` only
 * reconciles the Auto-EQ chip and deliberately does NOT re-apply the EQ (AxionEqViewModel.kt:210).
 */
@Composable
private fun rememberEqEnabledReadOnly(): Boolean {
    val eqViewModel: AxionEqViewModel = hiltViewModel()
    val enabled by eqViewModel.enabled.collectAsState()
    return enabled
}

// ── Seek bar ──────────────────────────────────────────────────────────────────────────────────────

/**
 * The new player's tint for the SHARED timeline ([PlayerProgressSlider]).
 *
 * Only the colours are the render's; the shape is whichever of the four styles the user picked in
 * Ajustes. There is deliberately no Aura-specific seek bar: a fifth, preference-blind timeline is
 * exactly what made "Estilo de la barra de progreso" a dead control while the new UI was on.
 *
 * The DISABLED colours are deliberately different from the enabled ones. A Listen Together guest cannot
 * seek (`enabled = !isListenTogetherGuest`); painting the disabled timeline in full teal made it look
 * live and silently swallow every touch — a timeline that lies. The classic player greys its slider out,
 * so this one does too: the track drops to the disabled foreground step and the thumb goes with it.
 */
@Composable
private fun auraSliderColors(): SliderColors = SliderDefaults.colors(
    activeTrackColor = AuraPalette.Teal,
    activeTickColor = AuraPalette.Teal,
    thumbColor = AuraPalette.Teal,
    inactiveTrackColor = AuraPalette.TrackEmpty,
    inactiveTickColor = AuraPalette.TrackEmpty,
    disabledActiveTrackColor = AuraPalette.OnGroundDisabled,
    disabledActiveTickColor = AuraPalette.OnGroundDisabled,
    disabledInactiveTrackColor = AuraPalette.TrackEmpty.copy(alpha = 0.06f),
    disabledInactiveTickColor = AuraPalette.TrackEmpty.copy(alpha = 0.06f),
    disabledThumbColor = AuraPalette.OnGroundDisabled,
)

// ── Technical data ────────────────────────────────────────────────────────────────────────────────

internal class AuraTechChip(val text: String, val highlight: Boolean)

internal class AuraTechInfo(val chips: List<AuraTechChip>, val isHiRes: Boolean)

/**
 * Derives `FLAC · 24 BIT · 96 kHz` from what the engine actually reports.
 *
 * A chip is emitted ONLY when the value is known. Bit depth in particular is only known once the
 * decoder reports a PCM encoding — for a still-compressed track it is omitted rather than guessed, and
 * the `◆ HI-RES` badge only lights when the depth or the rate really exceeds CD.
 */
internal fun auraTechnicalInfo(
    format: Format?,
    dbCodecs: String?,
    dbSampleRate: Int?,
): AuraTechInfo {
    val codec = (format?.sampleMimeType?.substringAfter("audio/") ?: dbCodecs)
        ?.substringBefore('.')
        ?.uppercase()
        ?.takeIf { it.isNotBlank() }

    val bits = when (format?.pcmEncoding) {
        C.ENCODING_PCM_8BIT -> 8
        C.ENCODING_PCM_16BIT -> 16
        C.ENCODING_PCM_24BIT -> 24
        C.ENCODING_PCM_32BIT -> 32
        C.ENCODING_PCM_FLOAT -> 32
        else -> null
    }

    val rate = format?.sampleRate?.takeIf { it > 0 } ?: dbSampleRate?.takeIf { it > 0 }
    val hiResRate = rate != null && rate > 48_000
    val rateText = rate?.let {
        if (it % 1000 == 0) "${it / 1000} kHz"
        else String.format(java.util.Locale.ROOT, "%.1f kHz", it / 1000f)
    }

    val hiRes = (bits != null && bits >= 24) || hiResRate

    val chips = buildList {
        codec?.let { add(AuraTechChip(it, highlight = false)) }
        bits?.let { add(AuraTechChip("$it BIT", highlight = it >= 24)) }
        rateText?.let { add(AuraTechChip(it, highlight = hiResRate)) }
    }
    return AuraTechInfo(chips, hiRes)
}
