

package iad1tya.echo.music.ui.player

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.widget.Toast
import android.content.BroadcastReceiver
import android.content.IntentFilter
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.activity.compose.BackHandler
import androidx.compose.ui.BiasAlignment
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import iad1tya.echo.music.ui.utils.rememberIsWideLayout
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.add
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ThumbDown
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.outlined.ThumbDown
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.runtime.produceState
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.foundation.focusGroup
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.toArgb
import coil3.size.Size as CoilSize
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.input.pointer.util.addPointerInputChange
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitVerticalTouchSlopOrCancellation
import androidx.compose.foundation.gestures.verticalDrag
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import androidx.core.net.toUri
import androidx.core.view.WindowCompat
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.Player.STATE_ENDED
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadRequest
import androidx.media3.exoplayer.offline.DownloadService
import androidx.navigation.NavController
import androidx.palette.graphics.Palette
import coil3.compose.AsyncImage
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.toBitmap
import iad1tya.echo.music.LocalDatabase
import iad1tya.echo.music.LocalDownloadUtil
import iad1tya.echo.music.LocalIsInPipMode
import iad1tya.echo.music.LocalListenTogetherManager
import iad1tya.echo.music.LocalPlayerConnection
import iad1tya.echo.music.R
import iad1tya.echo.music.constants.AudioQuality
import iad1tya.echo.music.constants.AudioQualityKey
import iad1tya.echo.music.constants.CropAlbumArtKey
import iad1tya.echo.music.constants.HidePlayerThumbnailKey
import iad1tya.echo.music.constants.EnableLyricsThumbnailPlayPauseKey
import iad1tya.echo.music.constants.PlayerBackgroundStyle
import iad1tya.echo.music.constants.PlayerBackgroundStyleKey
import iad1tya.echo.music.constants.PlayerButtonsStyle
import iad1tya.echo.music.constants.PlayerButtonsStyleKey
import iad1tya.echo.music.constants.PlayerHorizontalPadding
import iad1tya.echo.music.constants.QueuePeekHeight
import iad1tya.echo.music.constants.SwipeLyricsKey
import iad1tya.echo.music.constants.ThumbnailCornerRadius
import iad1tya.echo.music.constants.UseNewPlayerDesignKey
import iad1tya.echo.music.db.entities.LyricsEntity
import iad1tya.echo.music.extensions.SwipeGesture
import iad1tya.echo.music.extensions.togglePlayPause
import iad1tya.echo.music.extensions.toggleRepeatMode
import iad1tya.echo.music.listentogether.RoomRole
import iad1tya.echo.music.models.MediaMetadata
import iad1tya.echo.music.models.rememberResolvedAlbum
import iad1tya.echo.music.playback.ExoDownloadService
import iad1tya.echo.music.echomusic.getConnectedBluetoothDeviceName
import iad1tya.echo.music.echomusic.isBuds
import iad1tya.echo.music.echomusic.isSpeaker
import iad1tya.echo.music.echomusic.AudioDeviceBottomSheet
import iad1tya.echo.music.ui.component.BottomSheet
import iad1tya.echo.music.ui.component.BottomSheetState
import iad1tya.echo.music.ui.component.CastButton
import iad1tya.echo.music.ui.component.LocalBottomSheetPageState
import iad1tya.echo.music.ui.component.LocalMenuState
import iad1tya.echo.music.ui.component.Lyrics
import iad1tya.echo.music.ui.component.PlayerProgressSlider
import iad1tya.echo.music.ui.component.PlayerSliderTrack
import iad1tya.echo.music.ui.component.ResizableIconButton
import iad1tya.echo.music.ui.component.rememberBottomSheetState
import iad1tya.echo.music.ui.menu.OldPlayerMenu
import iad1tya.echo.music.ui.menu.PlayerMenu
import iad1tya.echo.music.ui.menu.AddToPlaylistDialog
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import iad1tya.echo.music.ui.component.VolumeSlider
import iad1tya.echo.music.ui.theme.PlayerColorExtractor
import iad1tya.echo.music.ui.theme.rememberEffectiveDarkTheme
import iad1tya.echo.music.ui.theme.PlayerSliderColors
import iad1tya.echo.music.ui.utils.rememberIsAppInForeground
import iad1tya.echo.music.ui.utils.tvFocusable
import iad1tya.echo.music.ui.utils.tvFocusRestorer
import iad1tya.echo.music.utils.DeviceCapabilities
import iad1tya.echo.music.utils.DeviceTier
import iad1tya.echo.music.ui.utils.ShowMediaInfo
import iad1tya.echo.music.ui.utils.ShowOffsetDialog
import iad1tya.echo.music.utils.makeTimeString
import iad1tya.echo.music.utils.isLocalMediaId
import iad1tya.echo.music.utils.rememberEnumPreference
import iad1tya.echo.music.utils.rememberPreference
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.roundToInt
import iad1tya.echo.music.ui.component.Icon as MIcon
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.exoplayer.DefaultLoadControl
import android.view.TextureView
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import iad1tya.echo.music.applecanvas.AppleMusicCanvasProvider
import iad1tya.echo.music.canvas.AppleMusicArtistBackgroundProvider
import iad1tya.echo.music.canvas.CanvasArtwork
import iad1tya.echo.music.canvas.TidalCanvasProvider
import iad1tya.echo.music.constants.CanvasThumbnailAnimationKey
import iad1tya.echo.music.extensions.metadata
import iad1tya.echo.music.ui.player.CanvasArtworkPlaybackCache
import iad1tya.echo.music.ui.player.normalizeCanvasArtistName
import iad1tya.echo.music.ui.player.normalizeCanvasSongTitle
import iad1tya.echo.music.echomusiccanvas.echomusicCanvasProvider
import java.util.Locale
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun BottomSheetPlayer(
    state: BottomSheetState,
    navController: NavController,
    modifier: Modifier = Modifier,
    pureBlack: Boolean,
) {
    val context = LocalContext.current
    val database = LocalDatabase.current
    val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val menuState = LocalMenuState.current
    val bottomSheetPageState = LocalBottomSheetPageState.current
    val playerConnection = LocalPlayerConnection.current ?: return

    val (useNewPlayerDesign, onUseNewPlayerDesignChange) = rememberPreference(
        UseNewPlayerDesignKey,
        defaultValue = true
    )
    val showCodecOnPlayer by rememberPreference(iad1tya.echo.music.constants.ShowCodecOnPlayerKey, false)
    val hidePlayerSlider by rememberPreference(iad1tya.echo.music.constants.HidePlayerSliderKey, false)
    // High-Performance Mode hides these heavy visuals without touching the user's stored toggles (reversible).
    val highPerfMode by rememberPreference(iad1tya.echo.music.constants.HighPerformanceModeKey, false)
    // Hardware floor (fluidity audit): even if a LOW/ultra-low device has Performance Mode turned OFF,
    // never run the per-frame blurred/animated player backgrounds on it — the perf flag is user-
    // reversible, so it's the only thing standing between weak silicon and a full-screen shader. The raw
    // hardware tier is not user-toggleable. compute() only returns LOW/MID/HIGH, so LOW == genuinely weak.
    val rawTierLow = remember {
        iad1tya.echo.music.utils.DeviceCapabilities.tier(context) == iad1tya.echo.music.utils.DeviceTier.LOW
    }
    // Anti-overheating: true only while the OS reports MODERATE+ thermal (always false below API 29 / while
    // cool). When hot, the heavy CONTINUOUS visuals below drop to their cheap/off path exactly like Perf Mode,
    // then restore automatically once the device cools. A cool/capable device: deviceThrottle == false, so every
    // gate that ORs it in is byte-identical to today.
    val deviceThrottle = iad1tya.echo.music.utils.rememberDeviceThrottle()
    // TV / car: enable visible D-pad focus on the transport controls + auto-focus play/pause when the player opens.
    val isTvOrCar = iad1tya.echo.music.ui.utils.rememberIsTvOrCar()
    val playFocusRequester = remember { androidx.compose.ui.focus.FocusRequester() }
    // LATCH: set true (and never back to false) the first time any transport button gains focus after the
    // player expands. The initial-focus retry stops the instant this latches, so it lands focus once the enter
    // animation attaches the node and then NEVER re-grabs it — even if the user D-pads away during the retry's
    // 50ms tick (a transient hasFocus=false must not re-arm the retry). Reset per expansion inside the effect.
    var transportFocusLanded by remember { mutableStateOf(false) }
    // Big screen (TV / tablet / car / unfolded foldable): show the Spotify-style split player (queue | now-playing).
    // WIDTH-driven, never orientation-driven — a foldable is used mostly in a near-square PORTRAIT, so gating the
    // split on landscape (as this file used to) meant the owner's unfolded phone never got it.
    val isWideLayout = rememberIsWideLayout()
    // Request a HIGH-RES cover source for the blurred backdrops on any big surface. Deliberately
    // `isTvOrCar || isWideLayout`: rememberIsTvOrCar() no longer carries a width term, so on a tablet this must
    // come from the width side or the backdrop would silently drop from 720px back to 100px and look pixelated.
    val wantsHiResBackdrop = isTvOrCar || isWideLayout
    // OPT-IN (default false): rotating to landscape with a canvas active replaces the player with a bare
    // fullscreen canvas. See ImmersiveCanvasOnRotateKey / registry #48 — it used to be unconditional.
    val immersiveCanvasOnRotate by rememberPreference(
        iad1tya.echo.music.constants.ImmersiveCanvasOnRotateKey, false,
    )
    val (hidePlayerThumbnail, onHidePlayerThumbnailChange) = rememberPreference(HidePlayerThumbnailKey, false)
    val cropAlbumArt by rememberPreference(CropAlbumArtKey, false)
    val mediaMetadata by playerConnection.mediaMetadata.collectAsState()
    val videoMode by playerConnection.videoMode.collectAsState()
    val videoUrlState by playerConnection.videoUrl.collectAsState()
    // True only while the immersive video layout is actually up (used to hide the redundant queue bar, etc.).
    val onImmersiveVideo = videoMode && !videoUrlState.isNullOrEmpty()
    val isLocalMedia = mediaMetadata?.id?.isLocalMediaId() == true

    val playerBackgroundPref by rememberEnumPreference(
        key = PlayerBackgroundStyleKey,
        defaultValue = PlayerBackgroundStyle.GRADIENT
    )
    val playerBackground = when {
        isLocalMedia -> PlayerBackgroundStyle.DEFAULT
        else -> playerBackgroundPref
    }
    val playerButtonsStyle by rememberEnumPreference(
        key = PlayerButtonsStyleKey,
        defaultValue = PlayerButtonsStyle.DEFAULT
    )

    // The APP's dark/light, not this screen's own reading of the preference — see
    // [rememberEffectiveDarkTheme]. It drives `shouldUseDarkButtonColors`, the status-bar icon
    // appearance (`isAppearanceLightStatusBars = !useDarkTheme`) and the cover blur radius.
    // With "Interfaz nueva" on — which forces the app dark — the old local `when` still said
    // "light" for a user on Claro: a black DEFAULT play button on the redesign's near-black ground, and
    // the status bar told to draw its DARK icons over that same dark bar. Reduces to that `when` with
    // the flag off.
    val useDarkTheme = rememberEffectiveDarkTheme()

    // DATA SAVER: canvas animations stream artwork/video data — forced OFF while the switch is ON
    // (the user's CanvasThumbnailAnimationKey pref stays persisted and returns when it goes OFF).
    val dataSaverEnabled by rememberPreference(key = iad1tya.echo.music.constants.DataSaverEnabledKey, defaultValue = false)
    val enableCanvasPref = iad1tya.echo.music.utils.rememberPerfGatedBoolean(CanvasThumbnailAnimationKey, true).value && !deviceThrottle
    val enableCanvas = if (dataSaverEnabled) false else enableCanvasPref
    val showArtistBackgroundVideo = iad1tya.echo.music.utils.rememberPerfGatedBoolean(
        iad1tya.echo.music.constants.ShowArtistBackgroundVideoKey, true
    ).value && !deviceThrottle

    val shouldUseDarkButtonColors = remember(playerBackground, useDarkTheme, highPerfMode) {
        when {
            // Perf mode paints a dark-scrimmed cover as the background, so use the same over-a-dark-background
            // button colors as BLUR/GRADIENT (otherwise a DEFAULT pref on a light theme gives black buttons).
            highPerfMode -> true
            playerBackground == PlayerBackgroundStyle.BLUR || playerBackground == PlayerBackgroundStyle.GRADIENT ||
                playerBackground == PlayerBackgroundStyle.GLOW_ANIMATED || playerBackground == PlayerBackgroundStyle.APPLE_MUSIC ||
                playerBackground == PlayerBackgroundStyle.LIVE_MESH || playerBackground == PlayerBackgroundStyle.LIQUID_GLASS -> true
            else -> useDarkTheme
        }
    }
    val isPlaying by playerConnection.isPlaying.collectAsState()
    // True while MusicService is crossfading between two tracks (drives the codec/timer box's shining indicator).
    val isCrossfading by playerConnection.isCrossfading.collectAsState()
    // Keep the screen awake only while a video is actively PLAYING (not paused/idle) — don't let it sleep
    // mid-video, but don't drain battery when paused. View-level flag, cleared when video stops/leaves.
    val keepScreenOnView = LocalView.current
    DisposableEffect(videoMode, isPlaying) {
        keepScreenOnView.keepScreenOn = videoMode && isPlaying
        onDispose { keepScreenOnView.keepScreenOn = false }
    }
    
    var currentAudioFormat by remember { mutableStateOf<androidx.media3.common.Format?>(null) }
    // Re-key on isCrossfading: a crossfade swaps the underlying ExoPlayer instance, so re-attach the codec
    // listener to whatever playerConnection.player points at once the swap settles (keeps the codec box accurate).
    DisposableEffect(playerConnection, isCrossfading) {
        val playerToListen = playerConnection.player
        val listener = object : Player.Listener {
            override fun onTracksChanged(tracks: androidx.media3.common.Tracks) {
                val audioTrack = tracks.groups.firstOrNull { it.type == C.TRACK_TYPE_AUDIO }
                currentAudioFormat = audioTrack?.getTrackFormat(0)
            }
        }
        playerToListen.addListener(listener)
        currentAudioFormat = playerToListen.currentTracks.groups.firstOrNull { it.type == C.TRACK_TYPE_AUDIO }?.getTrackFormat(0)
        onDispose {
            playerToListen.removeListener(listener)
        }
    }
    val swipeLyrics by rememberPreference(SwipeLyricsKey, false)
    val enableLyricsThumbnailPlayPause by rememberPreference(EnableLyricsThumbnailPlayPauseKey, false)

    // "Mantener la pantalla encendida cuando el reproductor está expandido". The predicate, the window
    // flag and the release-on-every-exit rule used to live inline in the insets DisposableEffect below;
    // they now live ONCE in [KeepScreenOnWhilePlayerExpandedEffect], which the new portrait player calls
    // too — it could not honour the switch at all before, so it only worked after rotating the phone.
    // Same preference, same predicate (isExpanded && isPlaying && pref), same track key.
    KeepScreenOnWhilePlayerExpandedEffect(
        isExpanded = state.isExpanded,
        isPlaying = isPlaying,
        currentMediaId = mediaMetadata?.id,
    )

    // TV / car: when the player opens, land the D-pad on the play/pause button so the user sees where they are
    // and can control playback immediately (Material3 gives no initial focus on a remote). No-op off-TV.
    LaunchedEffect(state.isExpanded, isTvOrCar) {
        if (isTvOrCar && state.isExpanded) {
            // The transport composes behind a ~300ms AnimatedVisibility slide-in, so on the first tick the
            // FocusRequester node isn't attached yet. The no-arg requestFocus() returns Unit and does NOT throw
            // when unattached (it just no-ops), so we can't detect success from its return — instead we retry
            // until the transport reports it has been focused (the transportFocusLanded LATCH), then stop. Using
            // a latch (not live hasFocus) closes the sub-50ms edge where the user D-pads away mid-tick and a
            // transient hasFocus=false would otherwise re-arm the retry and yank focus back to play/pause.
            transportFocusLanded = false
            repeat(40) {
                if (transportFocusLanded) return@LaunchedEffect
                playFocusRequester.requestFocus()
                kotlinx.coroutines.delay(50)
            }
        }
    }

    // Status-bar ICON colour only. The KEEP_SCREEN_ON half of this effect moved to
    // [KeepScreenOnWhilePlayerExpandedEffect] above (one implementation, shared with the new player);
    // `keepScreenOn` is gone from the key list because nothing left in here reads it — the insets
    // branch depends on playerBackground / useDarkTheme / the track's locality and never did.
    DisposableEffect(playerBackground, state.isExpanded, useDarkTheme, mediaMetadata?.id) {
        val window = (context as? android.app.Activity)?.window
        if (window != null && state.isExpanded) {
            val insetsController = WindowCompat.getInsetsController(window, window.decorView)

            val isLocal = mediaMetadata?.id?.isLocalMediaId() == true
            if (isLocal || playerBackground in listOf(PlayerBackgroundStyle.BLUR, PlayerBackgroundStyle.GRADIENT, PlayerBackgroundStyle.GLOW_ANIMATED, PlayerBackgroundStyle.APPLE_MUSIC, PlayerBackgroundStyle.LIVE_MESH, PlayerBackgroundStyle.LIQUID_GLASS)) {
                insetsController.isAppearanceLightStatusBars = false
            } else {
                insetsController.isAppearanceLightStatusBars = !useDarkTheme
            }
        }

        onDispose {
            if (window != null) {
                val insetsController = WindowCompat.getInsetsController(window, window.decorView)
                insetsController.isAppearanceLightStatusBars = !useDarkTheme
            }
        }
    }
    val onBackgroundColor = when (playerBackground) {
        PlayerBackgroundStyle.DEFAULT -> MaterialTheme.colorScheme.secondary
        else -> MaterialTheme.colorScheme.onSurface
    }
    // Second copy of the same derivation, now folded onto the one above: AMOLED paints black only over
    // a dark app, and under "Interfaz nueva" the app IS dark.
    val useBlackBackground =
        remember(useDarkTheme, pureBlack) {
            useDarkTheme && pureBlack
        }

    val playbackState by playerConnection.playbackState.collectAsState()
    val currentFormatEntity by database.format(mediaMetadata?.id).collectAsState(initial = null)
    val currentSong by playerConnection.currentSong.collectAsState(initial = null)
    val automix by playerConnection.service.automixItems.collectAsState()
    val repeatMode by playerConnection.repeatMode.collectAsState()
    val canSkipPrevious by playerConnection.canSkipPrevious.collectAsState()
    val canSkipNext by playerConnection.canSkipNext.collectAsState()
    val isMuted by playerConnection.isMuted.collectAsState()
    val playerVolume by playerConnection.service.playerVolume.collectAsState()

    val (audioQuality) = rememberEnumPreference(
        AudioQualityKey,
        defaultValue = AudioQuality.OPUS
    )
    // The slider style / squiggly preferences are read inside [PlayerProgressSlider] — the one read
    // site both player shapes share. (Perf mode still degrades the animated wave to the plain slider:
    // that gate lives with the read, in rememberPerfGatedBoolean.)

    val listenTogetherManager = LocalListenTogetherManager.current
    val listenTogetherRoleState = listenTogetherManager?.role?.collectAsState(initial = RoomRole.NONE)
    val isListenTogetherGuest = listenTogetherRoleState?.value == RoomRole.GUEST
    
    
    val castHandler = remember(playerConnection) {
        try {
            playerConnection.service.castConnectionHandler
        } catch (e: Exception) {
            null
        }
    }
    val isCasting by castHandler?.isCasting?.collectAsState() ?: remember { mutableStateOf(false) }
    val castPosition by castHandler?.castPosition?.collectAsState() ?: remember { mutableLongStateOf(0L) }
    val castDuration by castHandler?.castDuration?.collectAsState() ?: remember { mutableLongStateOf(0L) }
    val castIsPlaying by castHandler?.castIsPlaying?.collectAsState() ?: remember { mutableStateOf(false) }
    val castVolume by castHandler?.castVolume?.collectAsState() ?: remember { mutableFloatStateOf(1f) }
    
    
    val effectiveIsPlaying = if (isCasting) castIsPlaying else isPlaying

    
    
    val positionState = remember { mutableLongStateOf(0L) }
    val durationState = remember { mutableLongStateOf(0L) }
    
    
    var position by positionState
    var duration by durationState
    
    val effectivePosition by remember {
        derivedStateOf {
            if (isCasting) {
                castPosition
            } else {
                position
            }
        }
    }
    
    var sliderPosition by remember {
        mutableStateOf<Long?>(null)
    }
    
    var lastManualSeekTime by remember { mutableLongStateOf(0L) }
    
    var gradientColors by remember {
        mutableStateOf<List<Color>>(emptyList())
    }
    val gradientColorsCache = remember { mutableMapOf<String, List<Color>>() }

    // Mutating the player queue must NOT happen in the composition body: BottomSheetPlayer recomposes ~10x/s
    // (the position ticker), and Compose can run the body speculatively — so this enqueued duplicates / looped.
    // Run it as an effect keyed on the real inputs instead.
    LaunchedEffect(canSkipNext, automix) {
        if (!canSkipNext && automix.isNotEmpty()) {
            playerConnection.service.addToQueueAutomix(automix[0], 0)
        }
    }

    val bluetoothDeviceName by produceState<String?>(initialValue = getConnectedBluetoothDeviceName(context)) {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                value = getConnectedBluetoothDeviceName(context)
            }
        }

        val callback = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            object : android.media.AudioDeviceCallback() {
                override fun onAudioDevicesAdded(addedDevices: Array<out android.media.AudioDeviceInfo>?) {
                    value = getConnectedBluetoothDeviceName(context)
                }
                override fun onAudioDevicesRemoved(removedDevices: Array<out android.media.AudioDeviceInfo>?) {
                    value = getConnectedBluetoothDeviceName(context)
                }
            }
        } else null

        val filter = IntentFilter().apply {
            addAction(AudioManager.ACTION_HEADSET_PLUG)
            addAction("android.bluetooth.adapter.action.STATE_CHANGED")
            addAction("android.bluetooth.device.action.ACL_CONNECTED")
            addAction("android.bluetooth.device.action.ACL_DISCONNECTED")
            addAction("android.media.AUDIO_BECOMING_NOISY")
        }
        
        context.registerReceiver(receiver, filter)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && callback != null) {
            audioManager.registerAudioDeviceCallback(callback, Handler(Looper.getMainLooper()))
        }
        
        awaitDispose {
            context.unregisterReceiver(receiver)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && callback != null) {
                audioManager.unregisterAudioDeviceCallback(callback)
            }
        }
    }

    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val maxSystemVolume = remember { audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).toFloat() }
    val systemVolume by produceState(initialValue = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / maxSystemVolume) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.action == "android.media.VOLUME_CHANGED_ACTION") {
                    value = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / maxSystemVolume
                }
            }
        }
        val filter = IntentFilter("android.media.VOLUME_CHANGED_ACTION")
        context.registerReceiver(receiver, filter)
        awaitDispose {
            context.unregisterReceiver(receiver)
        }
    }

    val defaultGradientColors = listOf(MaterialTheme.colorScheme.surface, MaterialTheme.colorScheme.surfaceVariant)
    val fallbackColor = MaterialTheme.colorScheme.surface.toArgb()

    LaunchedEffect(mediaMetadata?.id, playerBackground, highPerfMode) {
        // Perf mode draws only the plain cover (no gradient/glow), so skip the palette extraction entirely —
        // its per-song image fetch + Palette.generate() would be wasted CPU/IO for colors never rendered.
        if (!highPerfMode && (playerBackground == PlayerBackgroundStyle.GRADIENT || playerBackground == PlayerBackgroundStyle.GLOW_ANIMATED)) {
            val currentMetadata = mediaMetadata
            if (currentMetadata != null && currentMetadata.thumbnailUrl != null) {
                val cachedColors = gradientColorsCache[currentMetadata.id]
                if (cachedColors != null) {
                    gradientColors = cachedColors
                    return@LaunchedEffect
                }
                withContext(Dispatchers.IO) {
                    val request = ImageRequest.Builder(context)
                        .data(currentMetadata.thumbnailUrl)
                        .size(100, 100)
                        .allowHardware(false)
                        .memoryCacheKey("gradient_${currentMetadata.id}")
                        .build()

                    val result = runCatching { context.imageLoader.execute(request) }.getOrNull()
                    if (result != null) {
                        val bitmap = result.image?.toBitmap()
                        if (bitmap != null) {
                            val palette = withContext(Dispatchers.Default) {
                                Palette.from(bitmap)
                                    .maximumColorCount(8)
                                    .resizeBitmapArea(100 * 100)
                                    .generate()
                            }
                            val extractedColors = if (playerBackground == PlayerBackgroundStyle.GLOW_ANIMATED) {
                                listOfNotNull(
                                    palette.getVibrantColor(fallbackColor).let { Color(it) },
                                    palette.getLightVibrantColor(fallbackColor).let { Color(it) },
                                    palette.getDarkVibrantColor(fallbackColor).let { Color(it) },
                                    palette.getMutedColor(fallbackColor).let { Color(it) },
                                    palette.getLightMutedColor(fallbackColor).let { Color(it) },
                                    palette.getDarkMutedColor(fallbackColor).let { Color(it) }
                                ).distinct()
                            } else {
                                PlayerColorExtractor.extractGradientColors(
                                    palette = palette,
                                    fallbackColor = fallbackColor
                                )
                            }
                            gradientColorsCache[currentMetadata.id] = extractedColors
                            withContext(Dispatchers.Main) { gradientColors = extractedColors }
                        }
                    }
                }
            }
        } else {
            gradientColors = emptyList()
        }
    }

    val TextBackgroundColor by animateColorAsState(
        targetValue = when {
            isLocalMedia -> Color.White
            // Perf mode paints a dark-scrimmed cover as the background, so white text is always readable —
            // otherwise a DEFAULT pref on a light theme gives near-black text over the dark cover.
            highPerfMode -> Color.White
            playerBackground == PlayerBackgroundStyle.DEFAULT -> MaterialTheme.colorScheme.onBackground
            else -> Color.White
        },
        label = "TextBackgroundColor"
    )

    val icBackgroundColor by animateColorAsState(
        targetValue = when {
            isLocalMedia -> Color.Black
            playerBackground == PlayerBackgroundStyle.DEFAULT -> MaterialTheme.colorScheme.surface
            else -> Color.Black
        },
        label = "icBackgroundColor"
    )

    var canvasArtwork by remember(mediaMetadata?.id) { mutableStateOf<CanvasArtwork?>(null) }
    var canvasFetchInFlight by remember(mediaMetadata?.id) { mutableStateOf(false) }

    LaunchedEffect(mediaMetadata?.id, playerBackground) {
        if (playerBackground != PlayerBackgroundStyle.APPLE_MUSIC || !enableCanvas) {
            canvasArtwork = null
            return@LaunchedEffect
        }
        val item = mediaMetadata ?: return@LaunchedEffect
        
        
        CanvasArtworkPlaybackCache.get(item.id)?.let { cached ->
            canvasArtwork = cached
            return@LaunchedEffect
        }

        if (canvasFetchInFlight) return@LaunchedEffect
        canvasFetchInFlight = true
        
        withContext(Dispatchers.IO) {
            val storefront = Locale.getDefault().country.lowercase(Locale.ROOT).takeIf { it.length == 2 } ?: "us"
            val requestedTitle = item.title
            val requestedArtist = item.artists.joinToString { it.name }
            val requestedAlbum = item.album?.title ?: ""
            
            val s = normalizeCanvasSongTitle(requestedTitle)
            val a = normalizeCanvasArtistName(requestedArtist)

            val fetched = (if (requestedAlbum.isNotBlank()) {
                    // Album-level lookups first (most motion art is album/track scoped). Tidal has the
                    // widest coverage of real video covers, so it's tried before Apple.
                    TidalCanvasProvider.getByAlbumArtist(album = requestedAlbum, artist = a)
                        ?.takeIf { !it.preferredAnimationUrl.isNullOrBlank() }
                        ?: AppleMusicCanvasProvider.getByAlbumArtist(album = requestedAlbum, artist = a, storefront = storefront)
                            ?.takeIf { !it.preferredAnimationUrl.isNullOrBlank() }
                } else null)
                ?: echomusicCanvasProvider.getBySongArtist(s, a)
                ?.takeIf { !it.preferredAnimationUrl.isNullOrBlank() }
                ?: TidalCanvasProvider.getBySongArtist(s, a, requestedAlbum)
                ?.takeIf { !it.preferredAnimationUrl.isNullOrBlank() }
                ?: AppleMusicCanvasProvider.getBySongArtist(s, a, requestedAlbum, storefront)
                ?.takeIf { !it.preferredAnimationUrl.isNullOrBlank() }

            val validated = fetched?.let { artwork ->
                val resultArtist = artwork.artist
                val artistMatches = if (resultArtist != null && requestedArtist.isNotBlank()) {
                    resultArtist.contains(requestedArtist, ignoreCase = true) ||
                    requestedArtist.contains(resultArtist, ignoreCase = true)
                } else true

                if (artistMatches) artwork else null
            }

            // Fallback: if this song/album has no canvas, play the ARTIST's motion background so the
            // player still shows a moving backdrop (the artist canvas the user asked for).
            val finalArtwork = validated ?: run {
                if (showArtistBackgroundVideo && a.isNotBlank()) {
                    val artistUrl = runCatching {
                        AppleMusicArtistBackgroundProvider.getByArtistName(a, storefront)
                    }.getOrNull()
                    if (!artistUrl.isNullOrBlank()) {
                        CanvasArtwork(artist = a, animated = artistUrl, videoUrl = artistUrl)
                    } else null
                } else null
            }

            withContext(Dispatchers.Main) {
                canvasArtwork = finalArtwork
                if (finalArtwork != null) {
                    CanvasArtworkPlaybackCache.put(item.id, finalArtwork)
                }
                canvasFetchInFlight = false
            }
        }
    }

    // "Estilo de los botones del reproductor" — the derivation moved to [rememberPlayerButtonColors]
    // (PlayerAppearancePrefs.kt) VERBATIM, so the "Interfaz nueva" transport can reuse the same colours
    // instead of growing a second copy that drifts. The values produced here are unchanged: the
    // over-dark condition below is the same list of backgrounds this `when` used to spell out.
    val playerButtonColors = rememberPlayerButtonColors(
        style = playerButtonsStyle,
        // Perf mode paints a dark-scrimmed cover background -> use the light (over-dark) button colors.
        overDarkBackground = highPerfMode ||
            isLocalMedia ||
            playerBackground == PlayerBackgroundStyle.BLUR ||
            playerBackground == PlayerBackgroundStyle.GRADIENT ||
            playerBackground == PlayerBackgroundStyle.GLOW_ANIMATED ||
            playerBackground == PlayerBackgroundStyle.APPLE_MUSIC ||
            playerBackground == PlayerBackgroundStyle.LIVE_MESH ||
            playerBackground == PlayerBackgroundStyle.LIQUID_GLASS,
        useDarkTheme = useDarkTheme,
    )
    val textButtonColor = playerButtonColors.textButtonColor
    val iconButtonColor = playerButtonColors.iconButtonColor
    val sideButtonContainerColor = playerButtonColors.sideButtonContainerColor
    val sideButtonContentColor = playerButtonColors.sideButtonContentColor

    val download by LocalDownloadUtil.current.getDownload(mediaMetadata?.id ?: "")
        .collectAsState(initial = null)

    var showChoosePlaylistDialog by rememberSaveable {
        mutableStateOf(false)
    }
    AddToPlaylistDialog(
        isVisible = showChoosePlaylistDialog,
        onGetSong = {
            val meta = mediaMetadata
            if (meta != null) {
                // withTransaction (suspending), NOT transaction {}: the latter posts to Room's
                // transaction executor and returns immediately, so the id could be handed back
                // before the song row is committed. AddToPlaylistDialog then inserts a
                // PlaylistSongMap row whose songId FK is ON DELETE CASCADE — if that wins the race,
                // the whole @Transaction addSongToPlaylist aborts and nothing is added, silently.
                database.withTransaction { insert(meta) }
            }
            // No remote add here: AddToPlaylistDialog is the single writer to the remote playlist
            // (it calls YouTube.addToPlaylist for every returned id, on the duplicate-confirm
            // branches too). Adding here as well made every song land TWICE in a synced YouTube
            // playlist, and "add anyway" issue two remote adds.
            listOfNotNull(meta?.id)
        },
        onDismiss = { showChoosePlaylistDialog = false }
    )

    var showInlineLyrics by rememberSaveable {
        mutableStateOf(false)
    }
    // Lyrics aren't available while watching the video — close them when video mode turns on.
    LaunchedEffect(videoMode) { if (videoMode && showInlineLyrics) showInlineLyrics = false }

    var isFullScreen by rememberSaveable {
        mutableStateOf(false)
    }
    // Both flags are rememberSaveable, and nothing used to clear this one when the lyrics closed. Leaving
    // fullscreen set with no lyrics on screen strips the transport and the queue bar off a player that is
    // still playing — recoverable only by reopening the lyrics and closing them the other way round. The
    // redesigned player already guards this; the classic one is where most users are.
    LaunchedEffect(showInlineLyrics) { if (!showInlineLyrics) isFullScreen = false }

    // Mirror of the immersive layouts' tap-toggled controls visibility (ptControls / lsControls /
    // lsCanvasControls are LOCAL to their deep layout branches, so root-pinned overlays can't read
    // them directly). The pinned CastButton follows this so it obeys the same tap-to-hide "clean
    // view" rule instead of floating alone over the video/canvas. Each immersive branch syncs it
    // while composed and resets it to true on dispose (non-immersive layouts always see true).
    var immersiveControlsVisible by remember { mutableStateOf(true) }

    // "Ocultar la barra de estado en pantalla completa" — the effect moved to
    // [HideStatusBarOnFullscreenEffect] (PlayerAppearancePrefs.kt) unchanged, so the "Interfaz nueva"
    // player's own fullscreen lyrics mode obeys the same switch instead of ignoring it.
    HideStatusBarOnFullscreenEffect(isFullScreen = isFullScreen)

    
    
    
    LaunchedEffect(isPlaying, isCasting) {
        if (!isCasting && isPlaying) {
            while (isActive) {
                // Perf mode: poll position half as often (1s vs 500ms) — one fewer recomposition/sec.
                delay(if (highPerfMode) 1000L else 500L)
                if (sliderPosition == null) {
                    position = playerConnection.player.currentPosition
                    duration = playerConnection.player.duration
                }
            }
        }
    }

    // Mirror the player sheet's expanded/collapsed state into the service layer so it knows when
    // the full player UI is actually on screen. state.isExpanded is a derivedStateOf, so reading it
    // as the key re-runs this effect exactly on expand/collapse (no polling). DisposableEffect (not
    // LaunchedEffect) so leaving composition — activity destroyed, player dismissed — RESETS the
    // flag: otherwise the service would be stranded thinking the player is still expanded and keep
    // warming video connections with no UI on screen.
    DisposableEffect(state.isExpanded) {
        playerConnection.setPlayerSheetExpanded(state.isExpanded)
        onDispose { playerConnection.setPlayerSheetExpanded(false) }
    }

    // Video is integrated into the main player now, so the seekbar reads the main player's position
    // natively (no separate video position plumbing, no flicker, native scrubbing).
    LaunchedEffect(playbackState, mediaMetadata?.id) {
        if (!isCasting) {
            position = playerConnection.player.currentPosition
            duration = playerConnection.player.duration
        }
    }
    
    
    
    LaunchedEffect(isCasting, castPosition, castDuration) {
        if (isCasting && sliderPosition == null) {
            val timeSinceManualSeek = System.currentTimeMillis() - lastManualSeekTime
            if (timeSinceManualSeek > 1500) {
                
                position = castPosition
                if (castDuration > 0) duration = castDuration
            }
        }
    }

    val dismissedBound = QueuePeekHeight + WindowInsets.systemBars.asPaddingValues().calculateBottomPadding()

    val queueSheetState = rememberBottomSheetState(
        dismissedBound = dismissedBound,
        expandedBound = state.expandedBound,
        collapsedBound = dismissedBound + 1.dp,
        initialAnchor = 1
    )

    val bottomSheetBackgroundColor = when {
        isLocalMedia -> Color.Black
        playerBackground in listOf(PlayerBackgroundStyle.BLUR, PlayerBackgroundStyle.GRADIENT, PlayerBackgroundStyle.GLOW_ANIMATED, PlayerBackgroundStyle.APPLE_MUSIC) ->
            MaterialTheme.colorScheme.surfaceContainer
        playerBackground == PlayerBackgroundStyle.LIVE_MESH || playerBackground == PlayerBackgroundStyle.LIQUID_GLASS -> Color.Black
        else ->
            if (useBlackBackground) Color.Black
            else MaterialTheme.colorScheme.surfaceContainer
    }

    val backgroundAlpha = state.progress.coerceIn(0f, 1f)

    BottomSheet(
        state = state,
        modifier = modifier,
        background = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(bottomSheetBackgroundColor)
            ) {
                if (highPerfMode || deviceThrottle || rawTierLow) {
                    // Perf mode OR the device is HOT (deviceThrottle) OR genuinely weak hardware (rawTierLow):
                    // show the cover as a PLAIN, opaque, STATIC
                    // background — a single downsized image draw, NO blur/glow/mesh shader, NO palette extraction,
                    // NO per-frame animation. This is the single gate for the heavy blurred/animated backgrounds
                    // (BLUR / GRADIENT / GLOW_ANIMATED / APPLE_MUSIC / LIVE_MESH) and the animated Canvas draw. So
                    // the artwork is still there (the user wants to see it) but the heavy backgrounds cost nothing.
                    // A fixed dark scrim keeps the title/controls readable.
                    val perfThumb = mediaMetadata?.thumbnailUrl
                    if (perfThumb != null) {
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(perfThumb)
                                .size(400, 400)
                                .build(),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxSize()
                                .alpha(backgroundAlpha),
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .alpha(backgroundAlpha)
                                .background(Color.Black.copy(alpha = 0.45f)),
                        )
                    }
                } else when (playerBackground) {
                    PlayerBackgroundStyle.BLUR -> {
                        AnimatedContent(
                            targetState = mediaMetadata?.thumbnailUrl,
                            transitionSpec = {
                                fadeIn(tween(800)).togetherWith(fadeOut(tween(800)))
                            },
                            label = "blurBackground"
                        ) { thumbnailUrl ->
                            if (thumbnailUrl != null) {
                                Box(modifier = Modifier.alpha(backgroundAlpha)) {
                                    AsyncImage(
                                        model = ImageRequest.Builder(context)
                                            .data(thumbnailUrl)
                                            // TV/large screen: request a high-res cover so the blurred backdrop
                                            // isn't pixelated on a big display. Phones keep the tiny 100px source
                                            // (and Performance Mode never reaches here — it draws the plain cover).
                                            .size(if (wantsHiResBackdrop) 720 else 100, if (wantsHiResBackdrop) 720 else 100)
                                            .allowHardware(false)
                                            .build(),
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .blur(if (useDarkTheme) 150.dp else 100.dp)
                                    )
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(Color.Black.copy(alpha = 0.3f))
                                    )
                                }
                            }
                        }
                    }
                    PlayerBackgroundStyle.GRADIENT -> {
                        AnimatedContent(
                            targetState = gradientColors,
                            transitionSpec = {
                                fadeIn(tween(800)).togetherWith(fadeOut(tween(800)))
                            },
                            label = "gradientBackground"
                        ) { colors ->
                            if (colors.isNotEmpty()) {
                                val gradientColorStops = if (colors.size >= 3) {
                                    arrayOf(
                                        0.0f to colors[0],
                                        0.5f to colors[1],
                                        1.0f to colors[2]
                                    )
                                } else {
                                    arrayOf(
                                        0.0f to colors[0],
                                        0.6f to colors[0].copy(alpha = 0.7f),
                                        1.0f to Color.Black
                                    )
                                }
                                Box(
                                    Modifier
                                        .fillMaxSize()
                                        .alpha(backgroundAlpha)
                                        .background(Brush.verticalGradient(colorStops = gradientColorStops))
                                        .background(Color.Black.copy(alpha = 0.2f))
                                )
                            }
                        }
                    }
                    PlayerBackgroundStyle.GLOW_ANIMATED -> {
                        AnimatedContent(
                            targetState = gradientColors,
                            transitionSpec = {
                                fadeIn(tween(1200)) togetherWith fadeOut(tween(1200))
                            },
                            label = "GlowAnimatedContent"
                        ) { colors ->
                            if (colors.isNotEmpty()) {
                                val infiniteTransition =
                                    rememberInfiniteTransition(label = "GlowAnimation")

                                val progress by infiniteTransition.animateFloat(
                                    initialValue = 0f,
                                    targetValue = 1f,
                                    animationSpec = infiniteRepeatable(
                                        animation = tween(20000, easing = LinearEasing),
                                        repeatMode = RepeatMode.Restart
                                    ),
                                    label = "glowProgress"
                                )

                                fun rotatedColorAt(index: Int): Color {
                                    val size = colors.size
                                    val idx = index.toFloat() + progress * size
                                    val a = kotlin.math.floor(idx).toInt() % size
                                    val b = (a + 1) % size
                                    val frac = idx - kotlin.math.floor(idx)
                                    return androidx.compose.ui.graphics.lerp(
                                        colors.getOrElse(a) { Color.DarkGray },
                                        colors.getOrElse(b) { Color.DarkGray },
                                        frac
                                    )
                                }

                                fun oscillate(
                                    min: Float,
                                    max: Float,
                                    phase: Float,
                                    speed: Float = 1f
                                ): Float {
                                    val v = kotlin.math.sin(
                                        2f * kotlin.math.PI.toFloat() * (progress * speed + phase)
                                    )
                                    return min + (max - min) * ((v + 1f) * 0.5f)
                                }

                                val color1 = rotatedColorAt(0)
                                val color2 = rotatedColorAt(1)
                                val color3 = rotatedColorAt(2)
                                val color4 = rotatedColorAt(3)
                                val color5 = rotatedColorAt(4)
                                val color6 = rotatedColorAt(5)

                                val o1x = oscillate(0.0f, 1.0f, 0.00f, 1.0f)
                                val o1y = oscillate(0.0f, 0.5f, 0.07f, 1.0f)
                                val r1 = oscillate(0.8f, 1.6f, 0.12f, 1.0f)

                                val o2x = oscillate(1.0f, 0.0f, 0.2f, 1.0f)
                                val o2y = oscillate(0.5f, 1.0f, 0.25f, 1.0f)
                                val r2 = oscillate(0.7f, 1.5f, 0.18f, 1.0f)

                                val o3x = oscillate(0.2f, 0.8f, 0.33f, 1.0f)
                                val o3y = oscillate(0.8f, 0.2f, 0.36f, 1.0f)
                                val r3 = oscillate(0.6f, 1.4f, 0.29f, 1.0f)

                                val o4x = oscillate(0.3f, 0.7f, 0.44f, 1.0f)
                                val o4y = oscillate(0.2f, 0.8f, 0.41f, 1.0f)
                                val r4 = oscillate(0.9f, 1.7f, 0.47f, 1.0f)

                                val o5x = oscillate(0.4f, 0.6f, 0.55f, 1.0f)
                                val o5y = oscillate(0.0f, 1.0f, 0.51f, 1.0f)
                                val r5 = oscillate(0.7f, 1.5f, 0.58f, 1.0f)

                                val o6x = oscillate(0.0f, 1.0f, 0.66f, 1.0f)
                                val o6y = oscillate(0.5f, 0.7f, 0.62f, 1.0f)
                                val r6 = oscillate(0.8f, 1.8f, 0.69f, 1.0f)

                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .alpha(backgroundAlpha)
                                        .drawWithCache {
                                            val width = size.width
                                            val height = size.height
                                            val baseColor = Color(0xFF050505)

                                            val brush1 = Brush.radialGradient(
                                                colors = listOf(
                                                    color1.copy(alpha = 0.85f),
                                                    color1.copy(alpha = 0.5f),
                                                    Color.Transparent
                                                ),
                                                center = Offset(width * o1x, height * o1y),
                                                radius = width * r1
                                            )
                                            val brush2 = Brush.radialGradient(
                                                colors = listOf(
                                                    color2.copy(alpha = 0.8f),
                                                    color2.copy(alpha = 0.45f),
                                                    Color.Transparent
                                                ),
                                                center = Offset(width * o2x, height * o2y),
                                                radius = width * r2
                                            )
                                            val brush3 = Brush.radialGradient(
                                                colors = listOf(
                                                    color3.copy(alpha = 0.75f),
                                                    color3.copy(alpha = 0.4f),
                                                    Color.Transparent
                                                ),
                                                center = Offset(width * o3x, height * o3y),
                                                radius = width * r3
                                            )
                                            val brush4 = Brush.radialGradient(
                                                colors = listOf(
                                                    color4.copy(alpha = 0.7f),
                                                    color4.copy(alpha = 0.35f),
                                                    Color.Transparent
                                                ),
                                                center = Offset(width * o4x, height * o4y),
                                                radius = width * r4
                                            )
                                            val brush5 = Brush.radialGradient(
                                                colors = listOf(
                                                    color5.copy(alpha = 0.65f),
                                                    color5.copy(alpha = 0.3f),
                                                    Color.Transparent
                                                ),
                                                center = Offset(width * o5x, height * o5y),
                                                radius = width * r5
                                            )
                                            val brush6 = Brush.radialGradient(
                                                colors = listOf(
                                                    color6.copy(alpha = 0.6f),
                                                    color6.copy(alpha = 0.25f),
                                                    Color.Transparent
                                                ),
                                                center = Offset(width * o6x, height * o6y),
                                                radius = width * r6
                                            )

                                            onDrawBehind {
                                                drawRect(color = baseColor)
                                                drawRect(brush = brush1)
                                                drawRect(brush = brush2)
                                                drawRect(brush = brush3)
                                                drawRect(brush = brush4)
                                                drawRect(brush = brush5)
                                                drawRect(brush = brush6)
                                            }
                                        }
                                )
                            }
                        }
                    }
                    PlayerBackgroundStyle.APPLE_MUSIC -> {
                        AnimatedContent(
                            targetState = mediaMetadata?.thumbnailUrl,
                            transitionSpec = {
                                fadeIn(tween(1200)).togetherWith(fadeOut(tween(1200)))
                            },
                            label = "appleMusicBackground"
                        ) { thumbnailUrl ->
                            if (thumbnailUrl != null) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .alpha(backgroundAlpha)
                                ) {
                                    
                                    AsyncImage(
                                        model = ImageRequest.Builder(context)
                                            .data(thumbnailUrl)
                                            // TV/large screen: high-res source so the blurred Apple-Music backdrop
                                            // stays clean on a big display (phones/Performance Mode unchanged).
                                            .size(if (wantsHiResBackdrop) 720 else 128, if (wantsHiResBackdrop) 720 else 128)
                                            .allowHardware(false)
                                            .build(),
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .blur(150.dp)
                                    )

                                    
                                    
                                    val clearArtworkAlpha by animateFloatAsState(
                                        targetValue = if (showInlineLyrics) 0f else 1f,
                                        animationSpec = tween(500),
                                        label = "clearArtworkAlpha"
                                    )
                                    
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .fillMaxHeight(0.65f) 
                                            .alpha(clearArtworkAlpha)
                                            .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
                                            .drawWithContent {
                                                drawContent()
                                                
                                                drawRect(
                                                    brush = Brush.verticalGradient(
                                                        colorStops = arrayOf(
                                                            0.00f to Color.Black,
                                                            0.75f to Color.Black,
                                                            0.92f to Color.Black.copy(alpha = 0.4f),
                                                            1.00f to Color.Transparent,
                                                        )
                                                    ),
                                                    blendMode = BlendMode.DstIn
                                                )
                                            }
                                    ) {
                                        AsyncImage(
                                            model = ImageRequest.Builder(context)
                                                .data(thumbnailUrl)
                                                .size(CoilSize.ORIGINAL)
                                                .build(),
                                            contentDescription = null,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )

                                        if (enableCanvas && canvasArtwork != null && backgroundAlpha > 0.01f && !videoMode) {
                                            BackgroundVideoView(
                                                videoUrl = canvasArtwork?.animated ?: canvasArtwork?.videoUrl ?: "",
                                                isPlaying = isPlaying,
                                                modifier = Modifier.fillMaxSize()
                                            )
                                        }
                                    }
                                    
                                    
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(
                                                Brush.verticalGradient(
                                                    listOf(
                                                        Color.Black.copy(alpha = 0.05f),
                                                        Color.Black.copy(alpha = 0.4f)
                                                    )
                                                )
                                            )
                                    )
                                }
                            }
                        }
                    }
                    PlayerBackgroundStyle.LIVE_MESH, PlayerBackgroundStyle.LIQUID_GLASS -> {
                        val infiniteTransition = rememberInfiniteTransition(label = "liveMeshRotation")
                        
                        val anchorRotation by infiniteTransition.animateFloat(
                            initialValue = 0f,
                            targetValue = -360f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(80000, easing = LinearEasing),
                                repeatMode = RepeatMode.Restart
                            ),
                            label = "anchorRotation"
                        )
                        
                        val fastRotation by infiniteTransition.animateFloat(
                            initialValue = 0f,
                            targetValue = 360f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(40000, easing = LinearEasing),
                                repeatMode = RepeatMode.Restart
                            ),
                            label = "fastRotation"
                        )
                        
                        val slowRotation by infiniteTransition.animateFloat(
                            initialValue = 0f,
                            targetValue = 360f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(60000, easing = LinearEasing),
                                repeatMode = RepeatMode.Restart
                            ),
                            label = "slowRotation"
                        )

                        AnimatedContent(
                            targetState = mediaMetadata?.thumbnailUrl,
                            transitionSpec = {
                                fadeIn(tween(1500)).togetherWith(fadeOut(tween(1500)))
                            },
                            label = "liveMeshBackground"
                        ) { thumbnailUrl ->
                            if (thumbnailUrl != null) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .alpha(backgroundAlpha)
                                        .graphicsLayer {
                                            
                                            scaleX = 1.7f
                                            scaleY = 1.7f
                                        }
                                ) {
                                    val matrix = remember { 
                                        val m = ColorMatrix()
                                        m.setToSaturation(1.8f) 
                                        m
                                    }
                                    val colorFilter = ColorFilter.colorMatrix(matrix)

                                    
                                    AsyncImage(
                                        model = ImageRequest.Builder(context)
                                            .data(thumbnailUrl)
                                            .size(if (wantsHiResBackdrop) 384 else 128, if (wantsHiResBackdrop) 384 else 128)
                                            .allowHardware(false)
                                            .build(),
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        colorFilter = colorFilter,
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .blur(100.dp)
                                            .graphicsLayer { rotationZ = anchorRotation }
                                    )

                                    
                                    AsyncImage(
                                        model = ImageRequest.Builder(context)
                                            .data(thumbnailUrl)
                                            .size(if (wantsHiResBackdrop) 384 else 128, if (wantsHiResBackdrop) 384 else 128)
                                            .allowHardware(false)
                                            .build(),
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        colorFilter = colorFilter,
                                        alignment = Alignment.TopStart,
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .blur(120.dp)
                                            .graphicsLayer { 
                                                rotationZ = fastRotation
                                                alpha = 0.6f
                                            }
                                    )

                                    
                                    AsyncImage(
                                        model = ImageRequest.Builder(context)
                                            .data(thumbnailUrl)
                                            .size(if (wantsHiResBackdrop) 384 else 128, if (wantsHiResBackdrop) 384 else 128)
                                            .allowHardware(false)
                                            .build(),
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        colorFilter = colorFilter,
                                        alignment = Alignment.BottomEnd,
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .blur(120.dp)
                                            .graphicsLayer { 
                                                rotationZ = slowRotation
                                                alpha = 0.5f
                                            }
                                    )
                                    
                                    
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(Color.Black.copy(alpha = 0.2f))
                                    )
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(
                                                Brush.verticalGradient(
                                                    listOf(
                                                        Color.Transparent,
                                                        Color.Black.copy(alpha = 0.25f)
                                                    )
                                                )
                                            )
                                    )
                                }
                            }
                        }
                    }
                    PlayerBackgroundStyle.DEFAULT -> {
                        
                    }
                }
            }
        },
        onDismiss = {
            playerConnection.service.clearAutomix()
            playerConnection.player.stop()
            playerConnection.player.clearMediaItems()
        },
        collapsedContent = {
            MiniPlayer(
                positionState = positionState,
                durationState = durationState,
                // P13: the full player's PlayerVideoSurface and the MiniPlayer thumbnail both bind the SAME
                // shared ExoPlayer via setVideoTextureView. During a sheet drag both the expanded content and
                // this collapsed content are composed at once, so two TextureViews would fight over the one
                // video output. Only let the mini bind the surface while the sheet is fully collapsed (the
                // expanded content isn't composed then); otherwise the mini shows the static artwork and the
                // expanded player owns the surface. Also skip binding in PiP, where MainActivity's fullscreen
                // PlayerVideoSurface owns the single output (otherwise two TextureViews bind and the mini
                // freezes on PiP exit). Together these keep exactly one surface bound at a time.
                shouldBindVideoSurface = state.isCollapsed && !LocalIsInPipMode.current
            )
        },
    ) {
        val controlsContent: @Composable ColumnScope.(MediaMetadata, Boolean) -> Unit = { mediaMetadata, immersiveVideo ->
            // immersiveVideo = rendered over the forced-dark premium video backdrop. Force light-on-dark
            // text/buttons (the theme-derived colors can be dark for PlayerBackgroundStyle.DEFAULT + light
            // theme → unreadable). Shadowing the vars keeps all internal usages readable without duplicating
            // the whole controls block (the Queue keeps the original colors — only these locals are forced).
            val TextBackgroundColor = if (immersiveVideo) Color.White else TextBackgroundColor
            val textButtonColor = if (immersiveVideo) Color.White else textButtonColor
            val iconButtonColor = if (immersiveVideo) Color.Black else iconButtonColor
            val playPauseRoundness by animateDpAsState(
                targetValue = if (isPlaying) 24.dp else 36.dp,
                animationSpec = tween(durationMillis = 90, easing = LinearEasing),
                label = "playPauseRoundness",
            )

            // Recover the album on-demand so tapping the title opens the album even when the
            // queue/radio/search renderer omitted it (one-shot lookup, only when missing; true
            // videos with no album leave the title inert). Called unconditionally to keep the
            // composition slot table stable across the immersive-video branch below.
            val resolvedAlbum = rememberResolvedAlbum(
                songId = mediaMetadata.id,
                initial = mediaMetadata.album,
                dbAlbumId = null,
                dbAlbumName = null,
            )

            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = PlayerHorizontalPadding),
            ) {
                AnimatedContent(
                    targetState = showInlineLyrics,
                    label = "ThumbnailAnimation"
                ) { showLyrics ->
                    if (showLyrics) {
                        Row {
                            if (hidePlayerThumbnail) {
                                Box(
                                    modifier = Modifier
                                        .size(56.dp)
                                        .clip(RoundedCornerShape(ThumbnailCornerRadius))
                                        .background(MaterialTheme.colorScheme.surfaceVariant),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        painter = painterResource(R.drawable.ic_launcher_nobg),
                                        contentDescription = null,
                                        modifier = Modifier
                                            .size(32.dp),
                                        tint = textButtonColor.copy(alpha = 0.7f)
                                    )
                                }
                            } else {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .size(56.dp)
                                        .clip(RoundedCornerShape(ThumbnailCornerRadius))
                                        .clickable(enabled = isFullScreen && enableLyricsThumbnailPlayPause) {
                                            playerConnection.togglePlayPause()
                                        }
                                ) {
                                    AsyncImage(
                                        model = mediaMetadata.thumbnailUrl,
                                        contentDescription = null,
                                        contentScale = if (cropAlbumArt) ContentScale.Crop else ContentScale.Fit,
                                        modifier = Modifier.fillMaxSize()
                                    )

                                    if (isFullScreen && enableLyricsThumbnailPlayPause) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .background(Color.Black.copy(alpha = if (isPlaying) 0f else 0.4f))
                                        )

                                        androidx.compose.animation.AnimatedVisibility(
                                            visible = !isPlaying,
                                            enter = fadeIn(),
                                            exit = fadeOut()
                                        ) {
                                            Icon(
                                                painter = painterResource(
                                                    if (playbackState == Player.STATE_ENDED) R.drawable.replay
                                                    else R.drawable.play
                                                ),
                                                contentDescription = null,
                                                tint = Color.White,
                                                modifier = Modifier.size(24.dp)
                                            )
                                        }
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                        }
                    } else {
                        Spacer(modifier = Modifier.width(0.dp))
                    }
                }
                // Title moved below this button row (full width, larger) — see the block after the Row.
                Spacer(Modifier.weight(1f))

                if (useNewPlayerDesign) {
                    val shareShape = RoundedCornerShape(
                        topStart = 50.dp, bottomStart = 50.dp,
                        topEnd = 3.dp, bottomEnd = 3.dp
                    )

                    val favShape = RoundedCornerShape(
                        topStart = 3.dp, bottomStart = 3.dp,
                        topEnd = 50.dp, bottomEnd = 50.dp
                    )

                    val middleShape = RoundedCornerShape(3.dp)

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AnimatedContent(targetState = showInlineLyrics, label = "DownloadButton") { showLyrics ->
                            if (showLyrics) {
                                FilledIconButton(
                                    onClick = { isFullScreen = !isFullScreen },
                                    shape = shareShape,
                                    colors = IconButtonDefaults.filledIconButtonColors(
                                        containerColor = textButtonColor,
                                        contentColor = iconButtonColor,
                                    ),
                                    modifier = Modifier.size(42.dp),
                                ) {
                                    Icon(
                                        painter = painterResource(R.drawable.fullscreen),
                                        contentDescription = null,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            } else if (!isLocalMedia) {
                                FilledIconButton(
                                    onClick = {
                                        mediaMetadata?.let { meta ->
                                            when (download?.state) {
                                                Download.STATE_COMPLETED, Download.STATE_QUEUED, Download.STATE_DOWNLOADING -> {
                                                    DownloadService.sendRemoveDownload(
                                                        context,
                                                        ExoDownloadService::class.java,
                                                        meta.id,
                                                        false,
                                                    )
                                                }
                                                else -> {
                                                    database.transaction {
                                                        insert(meta)
                                                    }
                                                    val downloadRequest =
                                                        DownloadRequest
                                                            .Builder(meta.id, meta.id.toUri())
                                                            .setCustomCacheKey(meta.id)
                                                            .setData(meta.title.toByteArray())
                                                            .build()
                                                    DownloadService.sendAddDownload(
                                                        context,
                                                        ExoDownloadService::class.java,
                                                        downloadRequest,
                                                        false,
                                                    )
                                                }
                                            }
                                        }
                                    },
                                    shape = shareShape,
                                    colors = IconButtonDefaults.filledIconButtonColors(
                                        containerColor = textButtonColor,
                                        contentColor = iconButtonColor,
                                    ),
                                    modifier = Modifier.size(42.dp),
                                ) {
                                    when (download?.state) {
                                        Download.STATE_COMPLETED -> {
                                            Icon(
                                                painter = painterResource(R.drawable.offline),
                                                contentDescription = null,
                                                modifier = Modifier.size(24.dp)
                                            )
                                        }
                                        Download.STATE_QUEUED, Download.STATE_DOWNLOADING -> {
                                            CircularWavyProgressIndicator(
                                                modifier = Modifier.size(24.dp),
                                            )
                                        }
                                        else -> {
                                            Icon(
                                                painter = painterResource(R.drawable.download),
                                                contentDescription = null,
                                                modifier = Modifier.size(24.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // (Audio↔video toggle moved to the END of the song title — see the title row below.)
                        AnimatedContent(targetState = showInlineLyrics, label = "LikeButton") { showLyrics ->
                            if (showLyrics) {
                                val currentLyrics by playerConnection.currentLyrics.collectAsState(initial = null)
                                FilledIconButton(
                                    onClick = {
                                        menuState.show {
                                            iad1tya.echo.music.ui.menu.LyricsMenu(
                                                lyricsProvider = { currentLyrics },
                                                songProvider = { currentSong?.song },
                                                mediaMetadataProvider = { mediaMetadata },
                                                onDismiss = menuState::dismiss,
                                                onShowOffsetDialog = {
                                                    bottomSheetPageState.show {
                                                        ShowOffsetDialog(
                                                            songProvider = { currentSong?.song }
                                                        )
                                                    }
                                                }
                                            )
                                        }
                                    },
                                    shape = favShape,
                                    colors = IconButtonDefaults.filledIconButtonColors(
                                        containerColor = textButtonColor,
                                        contentColor = iconButtonColor,
                                    ),
                                    modifier = Modifier.size(42.dp),
                                ) {
                                    Icon(
                                        painter = painterResource(R.drawable.more_horiz),
                                        contentDescription = null,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            } else {
                                // Nothing up here in audio mode — like/dislike are the compact icon
                                // buttons flanking the song title row below (YT Music layout).
                                Spacer(Modifier.size(0.dp))
                            }
                        }
                    }
                } else {
                    // Sound/equalizer moved into the "+" menu; only the menu (+) button stays up here.
                    AnimatedContent(targetState = showInlineLyrics, label = "DownloadButton") { showLyrics ->
                        if (showLyrics) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(24.dp))
                                    .background(textButtonColor.copy(alpha = 0.2f))
                                    .clickable { isFullScreen = !isFullScreen },
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.fullscreen),
                                    contentDescription = null,
                                    tint = textButtonColor,
                                    modifier = Modifier
                                        .align(Alignment.Center)
                                        .size(24.dp),
                                )
                            }
                        } else {
                            // "+" menu moved next to the title.
                            Spacer(Modifier.size(0.dp))
                        }
                    }

                    Spacer(modifier = Modifier.size(12.dp))

                    AnimatedContent(targetState = showInlineLyrics, label = "LikeButton") { showLyrics ->
                        if (showLyrics) {
                            val currentLyrics by playerConnection.currentLyrics.collectAsState(initial = null)
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(24.dp))
                                    .background(textButtonColor.copy(alpha = 0.2f))
                                    .clickable {
                                        menuState.show {
                                            iad1tya.echo.music.ui.menu.LyricsMenu(
                                                lyricsProvider = { currentLyrics },
                                                songProvider = { currentSong?.song },
                                                mediaMetadataProvider = { mediaMetadata },
                                                onDismiss = menuState::dismiss,
                                                onShowOffsetDialog = {
                                                    bottomSheetPageState.show {
                                                        ShowOffsetDialog(
                                                            songProvider = { currentSong?.song }
                                                        )
                                                    }
                                                }
                                            )
                                        }
                                    },
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.more_horiz),
                                    contentDescription = null,
                                    tint = textButtonColor,
                                    modifier = Modifier
                                        .align(Alignment.Center)
                                        .size(24.dp),
                                )
                            }
                        } else {
                            // (Audio↔video toggle moved to the END of the song title — not duplicated here.)
                            Spacer(Modifier.size(0.dp))
                        }
                    }
                }
            }

            // Song title + artist (audio mode: the single toggle is at the END of the title). HIDDEN in
            // immersive video — there the title is ABOVE the video and the toggle is at its bottom-right.
            if (!immersiveVideo) {
            Spacer(Modifier.height(2.dp))
            // Title + artist row (shared by both player designs — this block is OUTSIDE the
            // useNewPlayerDesign branch). Like/dislike are no longer flanking the title; they live in the
            // YT-Music divided like/dislike pill in the action-chip row below.
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    // "Deslizar en la letra para cambiar de canción" (SwipeLyricsKey). The switch has
                    // existed for releases, was read once at the top of this file and then NEVER used —
                    // dead in the classic player too. This is the gesture its own description promises:
                    // swipe the artist/title block while the lyrics are full screen to change track.
                    .swipeLyricsToChangeSong(
                        // Same four-term gate the new player uses, one definition
                        // (PlayerAppearancePrefs.kt). `lyricsVisible = true` because in THIS shape
                        // `isFullScreen` is only ever set from the lyrics UI (Player.kt:1640/1770), so it
                        // already implies the lyrics are up — the expression is unchanged.
                        enabled = swipeLyricsGestureArmed(
                            swipeLyricsEnabled = swipeLyrics,
                            lyricsVisible = true,
                            lyricsFullScreen = isFullScreen,
                            isListenTogetherGuest = isListenTogetherGuest,
                        ),
                        onPrevious = { if (canSkipPrevious) playerConnection.seekToPrevious() },
                        onNext = { if (canSkipNext) playerConnection.seekToNext() },
                    )
                    .padding(horizontal = PlayerHorizontalPadding),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                AnimatedContent(
                    targetState = mediaMetadata.title,
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                    label = "playerTitle",
                ) { title ->
                    Text(
                        text = title,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = TextBackgroundColor,
                        modifier = Modifier
                            .fillMaxWidth()
                            .basicMarquee(iterations = 1, initialDelayMillis = 3000, velocity = 30.dp)
                            // TV/car: own visible D-pad focus stop for the TITLE (opens the album), separate
                            // from the cover. Ring modifier sits ABOVE the clickable so it observes its focus.
                            .tvFocusable(isTvOrCar, RoundedCornerShape(6.dp))
                            .combinedClickable(
                                enabled = true,
                                indication = null,
                                interactionSource = remember { MutableInteractionSource() },
                                onClick = {
                                    resolvedAlbum?.let { album ->
                                        navController.navigate("album/${album.id}")
                                        state.collapseSoft()
                                    }
                                },
                                onLongClick = {
                                    val clip = ClipData.newPlainText(context.getString(R.string.copied_title), title)
                                    clipboardManager.setPrimaryClip(clip)
                                    Toast.makeText(context, context.getString(R.string.copied_title), Toast.LENGTH_SHORT).show()
                                },
                            ),
                    )
                }
                Spacer(Modifier.height(2.dp))
                if (mediaMetadata.artists.any { it.name.isNotBlank() }) {
                    val artistText = mediaMetadata.artists.joinToString(", ") { it.name }
                    Text(
                        text = artistText,
                        style = MaterialTheme.typography.titleMedium.copy(color = TextBackgroundColor),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .fillMaxWidth()
                            .basicMarquee(iterations = 1, initialDelayMillis = 3000, velocity = 30.dp)
                            // TV/car: the ARTIST is its OWN focusable target (navigates to the artist), distinct
                            // from the cover and the title — so the D-pad can land on it and show the ring.
                            .tvFocusable(isTvOrCar, RoundedCornerShape(6.dp))
                            .combinedClickable(
                                enabled = true,
                                indication = null,
                                interactionSource = remember { MutableInteractionSource() },
                                onClick = {
                                    mediaMetadata.artists.firstOrNull { !it.id.isNullOrBlank() }?.id?.let { artistId ->
                                        navController.navigate("artist/$artistId")
                                        state.collapseSoft()
                                    }
                                },
                                onLongClick = {
                                    val clip = ClipData.newPlainText(context.getString(R.string.copied_artist), artistText)
                                    clipboardManager.setPrimaryClip(clip)
                                    Toast.makeText(context, context.getString(R.string.copied_artist), Toast.LENGTH_SHORT).show()
                                },
                            ),
                    )
                }
                }
                // Audio↔video toggle at the END of the song title (per request). Hidden in High-Performance Mode
                // on phones (audio only), BUT shown on TV/car even in perf mode — the user asked to be able to
                // switch to video on the big screen on demand (the video track is resolution-capped by device tier).
                // wantsHiResBackdrop (= isTvOrCar || isWideLayout), not isTvOrCar: this is a FUNCTIONAL gate, and
                // isTvOrCar no longer carries a width term, so a plain tablet in High-Performance Mode would
                // otherwise lose the video button it used to have.
                if ((!highPerfMode || wantsHiResBackdrop) && (mediaMetadata.isVideoSong || !mediaMetadata.podcastVideoUrl.isNullOrEmpty())) {
                    Spacer(Modifier.width(4.dp))
                    // Chip-style (matches the action chips below) and pinned to the right of the title.
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(50))
                            .background(
                                if (videoMode) textButtonColor.copy(alpha = 0.45f)
                                else textButtonColor.copy(alpha = 0.18f)
                            )
                            .clickable { playerConnection.toggleVideoMode() },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            painter = painterResource(if (videoMode) R.drawable.music_note else R.drawable.videocam),
                            contentDescription = null,
                            tint = textButtonColor,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }
            }

            Spacer(Modifier.height(10.dp))
            // YouTube-Music-style scrollable action row — every key action is labeled and visible here
            // instead of hidden in the "+" menu.
            run {
                val chipBg = textButtonColor.copy(alpha = 0.18f)
                val mixActive by playerConnection.mixActive.collectAsState()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        // Fade the right edge so it's clear the row scrolls / there are more buttons.
                        .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                        .drawWithContent {
                            drawContent()
                            drawRect(
                                brush = Brush.horizontalGradient(
                                    0f to Color.Black, 0.86f to Color.Black, 1f to Color.Transparent,
                                ),
                                blendMode = BlendMode.DstIn,
                            )
                        }
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = PlayerHorizontalPadding),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // YT-Music-style divided like/dislike pill: ONE rounded container split into a LEFT
                    // like half and a RIGHT dislike half with a subtle divider. The active half fills/tints
                    // (liked → up half; disliked → down half); neutral = both outline. Shared by both player
                    // designs (this row is outside the useNewPlayerDesign branch). Taps route through the
                    // untouched toggleLike / toggleDislikeCurrentSong paths (no audio re-leveling).
                    val liked = currentSong?.song?.liked == true
                    val disliked by playerConnection.currentSongDisliked.collectAsState()
                    PlayerLikeDislikePill(
                        liked = liked,
                        disliked = disliked,
                        activeColor = textButtonColor,
                        activeContentColor = iconButtonColor,
                        container = chipBg,
                        onToggleLike = playerConnection::toggleLike,
                        onToggleDislike = { playerConnection.toggleDislikeCurrentSong() },
                        likeContentDescription = stringResource(R.string.action_like),
                        dislikeContentDescription = stringResource(R.string.action_dislike),
                    )
                    PlayerActionChip("Agregar", textButtonColor, chipBg, { showChoosePlaylistDialog = true }) {
                        Icon(painterResource(R.drawable.playlist_add), null, tint = textButtonColor, modifier = Modifier.size(20.dp))
                    }
                    PlayerActionChip("Compartir", textButtonColor, chipBg, {
                        val intent = Intent().apply {
                            action = Intent.ACTION_SEND
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, iad1tya.echo.music.utils.ShareLinks.song(mediaMetadata.id))
                        }
                        context.startActivity(Intent.createChooser(intent, null))
                    }) {
                        Icon(painterResource(R.drawable.share), null, tint = textButtonColor, modifier = Modifier.size(20.dp))
                    }
                    if (!isLocalMedia) {
                    PlayerActionChip(
                        label = "Descargar",
                        tint = textButtonColor,
                        container = chipBg,
                        onClick = {
                            when (download?.state) {
                                Download.STATE_COMPLETED, Download.STATE_QUEUED, Download.STATE_DOWNLOADING ->
                                    DownloadService.sendRemoveDownload(context, ExoDownloadService::class.java, mediaMetadata.id, false)
                                else -> {
                                    database.transaction { insert(mediaMetadata) }
                                    DownloadService.sendAddDownload(
                                        context, ExoDownloadService::class.java,
                                        DownloadRequest.Builder(mediaMetadata.id, mediaMetadata.id.toUri())
                                            .setCustomCacheKey(mediaMetadata.id)
                                            .setData(mediaMetadata.title.toByteArray()).build(),
                                        false,
                                    )
                                }
                            }
                        },
                    ) {
                        when (download?.state) {
                            Download.STATE_COMPLETED -> Icon(painterResource(R.drawable.offline), null, tint = textButtonColor, modifier = Modifier.size(20.dp))
                            Download.STATE_QUEUED, Download.STATE_DOWNLOADING -> CircularWavyProgressIndicator(modifier = Modifier.size(18.dp))
                            else -> Icon(painterResource(R.drawable.download), null, tint = textButtonColor, modifier = Modifier.size(20.dp))
                        }
                    }
                    }
                    PlayerActionChip(
                        label = "Mix",
                        tint = if (mixActive) iconButtonColor else textButtonColor,
                        container = if (mixActive) textButtonColor else chipBg,
                        onClick = { playerConnection.startRadioSeamlessly() },
                    ) {
                        Icon(painterResource(R.drawable.radio), null, tint = if (mixActive) iconButtonColor else textButtonColor, modifier = Modifier.size(20.dp))
                    }
                    PlayerActionChip("Audio", textButtonColor, chipBg, {
                        // Collapse the player first so the equalizer screen is actually visible (otherwise
                        // the expanded player sheet covers it and it looks like nothing happened).
                        // launchSingleTop so a re-tap can't push a 2nd EQ entry (which made back return to the
                        // SAME screen, needing a 2nd press to actually leave).
                        navController.navigate("settings/equalizer") { launchSingleTop = true }
                        state.collapseSoft()
                    }) {
                        Icon(painterResource(R.drawable.graphic_eq), null, tint = textButtonColor, modifier = Modifier.size(20.dp))
                    }
                    PlayerActionChip(
                        label = "Más",
                        tint = textButtonColor,
                        container = chipBg,
                        onClick = {
                            menuState.show {
                                OldPlayerMenu(
                                    mediaMetadata = mediaMetadata,
                                    navController = navController,
                                    playerBottomSheetState = state,
                                    onShowDetailsDialog = { mediaMetadata.id.let { bottomSheetPageState.show { ShowMediaInfo(it) } } },
                                    onDismiss = menuState::dismiss,
                                )
                            }
                        },
                    ) {
                        Icon(painterResource(R.drawable.add), null, tint = textButtonColor, modifier = Modifier.size(20.dp))
                    }
                }
            }

            // Tighter gap so the title sits lower, closer to the progress bar (one-handed reach).
            Spacer(Modifier.height(8.dp))

            // The four styles live in ONE place now — [PlayerProgressSlider] is the single read site of
            // SliderStyleKey/SquigglySliderKey, so the classic player and the "Interfaz nueva" player
            // cannot disagree about what the setting does.
            PlayerProgressSlider(
                value = (sliderPosition ?: effectivePosition).toFloat(),
                valueRange = 0f..(if (duration == C.TIME_UNSET) 0f else duration.toFloat()),
                onValueChange = { sliderPosition = it.toLong() },
                onValueChangeFinished = {
                    sliderPosition?.let {
                        if (isCasting) {
                            castHandler?.seekTo(it)
                            lastManualSeekTime = System.currentTimeMillis()
                        } else {
                            playerConnection.player.seekTo(it)
                        }
                        position = it
                    }
                    sliderPosition = null
                },
                enabled = !isListenTogetherGuest,
                colors = PlayerSliderColors.getSliderColors(
                    activeColor = if (useNewPlayerDesign) textButtonColor else textButtonColor.copy(alpha = 0.7f),
                    playerBackground = playerBackground,
                    useDarkTheme = useDarkTheme
                ),
                isPlaying = effectiveIsPlaying,
                slimTrackGrowsOnDrag = !useNewPlayerDesign,
                modifier = Modifier
                    .padding(horizontal = PlayerHorizontalPadding)
                    // TV/car: visible D-pad focus ring around the timeline (Material's Slider shows no
                    // focus affordance on a remote). D-pad left/right seeks once focused. No-op off-TV.
                    .tvFocusable(isTvOrCar, RoundedCornerShape(12.dp)),
            )
            Spacer(Modifier.height(4.dp))

            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = PlayerHorizontalPadding + 4.dp),
            ) {
                Text(
                    text = makeTimeString(sliderPosition ?: effectivePosition),
                    style = MaterialTheme.typography.labelMedium,
                    color = TextBackgroundColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                    modifier = Modifier.weight(1f)
                ) {
                    // Crossfade indicator: while MusicService is crossfading, show a subtle "shining" chip
                    // (icon + label pulsing alpha) in the codec/timer info area. Transient, so it's left on in
                    // perf mode too — it only animates during the brief crossfade window. Handles the
                    // crossfading state alongside the existing sleep-timer / codec displays in this same row.
                    if (isCrossfading) {
                        val crossfadeTransition = rememberInfiniteTransition(label = "ShiningCrossfade")
                        val crossfadeAlpha by crossfadeTransition.animateFloat(
                            initialValue = 0.3f,
                            targetValue = 1f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(800, easing = LinearEasing),
                                repeatMode = RepeatMode.Reverse
                            ),
                            label = "CrossfadeAlpha"
                        )
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(TextBackgroundColor.copy(alpha = 0.08f))
                                .border(
                                    width = 0.5.dp,
                                    color = TextBackgroundColor.copy(alpha = 0.12f),
                                    shape = RoundedCornerShape(4.dp)
                                )
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.sync),
                                    contentDescription = stringResource(R.string.crossfading),
                                    tint = TextBackgroundColor.copy(alpha = crossfadeAlpha),
                                    modifier = Modifier.size(12.dp)
                                )
                                Text(
                                    text = stringResource(R.string.crossfading),
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 1.sp
                                    ),
                                    color = TextBackgroundColor.copy(alpha = crossfadeAlpha),
                                    maxLines = 1,
                                )
                            }
                        }
                    }
                    if (showCodecOnPlayer) {
                        val formatText = remember(currentAudioFormat, currentFormatEntity) {
                            val localAudioFormat = currentAudioFormat
                            val localFormatEntity = currentFormatEntity
                            val codecStr = localAudioFormat?.sampleMimeType?.substringAfter("audio/")?.uppercase() ?: localFormatEntity?.codecs?.uppercase() ?: ""
                            var bitrateStr = ""
                            if (localFormatEntity?.bitrate != null && localFormatEntity.bitrate > 0) {
                                bitrateStr = "${localFormatEntity.bitrate / 1000} kbps"
                            } else if (localAudioFormat?.bitrate != null && localAudioFormat.bitrate > 0) {
                                bitrateStr = "${localAudioFormat.bitrate / 1000} kbps"
                            }
                            val isLossless = codecStr.contains("FLAC") || codecStr.contains("ALAC") || codecStr.contains("WAV")
                            val losslessStr = if (isLossless) "Lossless" else ""
                            listOf(codecStr, bitrateStr, losslessStr).filter { it.isNotEmpty() }.joinToString(" • ")
                        }
                        if (formatText.isNotEmpty()) {
                            Text(
                                text = formatText,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.sp,
                                    fontSize = 10.sp
                                ),
                                color = TextBackgroundColor.copy(alpha = 0.7f),
                            )
                        }
                    }
                }

                Text(
                    text = if (duration != C.TIME_UNSET) makeTimeString(duration) else "",
                    style = MaterialTheme.typography.labelMedium,
                    color = TextBackgroundColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Spacer(Modifier.height(if (useNewPlayerDesign) 12.dp else 8.dp))

            AnimatedVisibility(
                // In immersive video the play/pause/skip transport must always show (the persisted
                // lyrics-fullscreen flag must not hide it — there's no other transport in that overlay).
                visible = !isFullScreen || immersiveVideo,
                enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                exit = shrinkVertically(shrinkTowards = Alignment.Top) + slideOutVertically(targetOffsetY = { it }) + fadeOut()
            ) {
                // TV/car: latch the moment any transport button first gains focus so the initial-focus retry
                // stops for good (only ever sets true; the retry resets it to false per expansion).
                Column(modifier = Modifier.onFocusChanged { if (it.hasFocus) transportFocusLanded = true }) {
                    if (useNewPlayerDesign) {
                        Row(
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = PlayerHorizontalPadding)
                        ) {
                            val backInteractionSource = remember { MutableInteractionSource() }
                            val nextInteractionSource = remember { MutableInteractionSource() }
                            val playPauseInteractionSource = remember { MutableInteractionSource() }

                            val isPlayPausePressed by playPauseInteractionSource.collectIsPressedAsState()
                            val isBackPressed by backInteractionSource.collectIsPressedAsState()
                            val isNextPressed by nextInteractionSource.collectIsPressedAsState()

                            val playPauseWeight by animateFloatAsState(
                                targetValue = if (isPlayPausePressed) 1.9f else if (isBackPressed || isNextPressed) 1.1f else 1.3f,
                                animationSpec = spring(
                                    dampingRatio = 0.6f,
                                    stiffness = 500f
                                ),
                                label = "playPauseWeight"
                            )

                            val backButtonWeight by animateFloatAsState(
                                targetValue = if (isBackPressed) 0.65f else if (isPlayPausePressed) 0.35f else 0.45f,
                                animationSpec = spring(
                                    dampingRatio = 0.6f,
                                    stiffness = 500f
                                ),
                                label = "backButtonWeight"
                            )

                            val nextButtonWeight by animateFloatAsState(
                                targetValue = if (isNextPressed) 0.65f else if (isPlayPausePressed) 0.35f else 0.45f,
                                animationSpec = spring(
                                    dampingRatio = 0.6f,
                                    stiffness = 500f
                                ),
                                label = "nextButtonWeight"
                            )

                            FilledIconButton(
                                onClick = playerConnection::seekToPrevious,
                                enabled = canSkipPrevious && !isListenTogetherGuest,
                                shape = RoundedCornerShape(50),
                                interactionSource = backInteractionSource,
                                colors = IconButtonDefaults.filledIconButtonColors(
                                    containerColor = sideButtonContainerColor,
                                    contentColor = sideButtonContentColor,
                                ),
                                modifier = Modifier
                                    .height(68.dp)
                                    .weight(backButtonWeight)
                                    .tvFocusable(isTvOrCar)
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.skip_previous),
                                    contentDescription = null,
                                    modifier = Modifier.size(32.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            FilledIconButton(
                                onClick = {
                                    if (isListenTogetherGuest) {
                                        playerConnection.toggleMute()
                                        return@FilledIconButton
                                    }
                                    if (isCasting) {
                                        if (castIsPlaying) {
                                            castHandler?.pause()
                                        } else {
                                            castHandler?.play()
                                        }
                                    } else if (playbackState == STATE_ENDED) {
                                        playerConnection.player.seekTo(0, 0)
                                        playerConnection.player.playWhenReady = true
                                    } else {
                                        playerConnection.togglePlayPause()
                                    }
                                },
                                shape = RoundedCornerShape(50),
                                interactionSource = playPauseInteractionSource,
                                colors = IconButtonDefaults.filledIconButtonColors(
                                    containerColor = textButtonColor,
                                    contentColor = iconButtonColor,
                                ),
                                modifier = Modifier
                                    .height(68.dp)
                                    .weight(playPauseWeight)
                                    .focusRequester(playFocusRequester)
                                    .tvFocusable(isTvOrCar)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        painter = painterResource(
                                            if (isListenTogetherGuest) {
                                                if (isMuted) R.drawable.volume_off else R.drawable.volume_up
                                            } else {
                                                if (effectiveIsPlaying) R.drawable.pause else R.drawable.play
                                            }
                                        ),
                                        contentDescription = if (isListenTogetherGuest) {
                                            if (isMuted) stringResource(R.string.unmute) else stringResource(R.string.mute)
                                        } else {
                                            if (effectiveIsPlaying) stringResource(R.string.pause) else stringResource(R.string.play)
                                        },
                                        modifier = Modifier.size(32.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            FilledIconButton(
                                onClick = playerConnection::seekToNext,
                                enabled = canSkipNext && !isListenTogetherGuest,
                                shape = RoundedCornerShape(50),
                                interactionSource = nextInteractionSource,
                                colors = IconButtonDefaults.filledIconButtonColors(
                                    containerColor = sideButtonContainerColor,
                                    contentColor = sideButtonContentColor,
                                ),
                                modifier = Modifier
                                    .height(68.dp)
                                    .weight(nextButtonWeight)
                                    .tvFocusable(isTvOrCar)
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.skip_next),
                                    contentDescription = null,
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                        }
                    } else {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = PlayerHorizontalPadding),
                        ) {




















                            Box(modifier = Modifier.weight(1f)) {
                                ResizableIconButton(
                                    icon = R.drawable.apple_skip_previous,
                                    enabled = canSkipPrevious && !isListenTogetherGuest,
                                    color = TextBackgroundColor,
                                    modifier =
                                Modifier
                                    .size(48.dp)
                                    .align(Alignment.Center)
                                    .alpha(if (isListenTogetherGuest) 0.5f else 1f)
                                    .tvFocusable(isTvOrCar),
                                    onClick = playerConnection::seekToPrevious,
                                )
                            }

                            Spacer(Modifier.width(8.dp))

                            Box(
                                modifier =
                                Modifier
                                    .size(100.dp)
                                    .clip(RoundedCornerShape(playPauseRoundness))
                                    .focusRequester(playFocusRequester)
                                    .tvFocusable(isTvOrCar, RoundedCornerShape(playPauseRoundness))
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null
                                    ) {
                                        if (isListenTogetherGuest) {
                                            playerConnection.toggleMute()
                                            return@clickable
                                        }
                                        if (isCasting) {
                                            if (castIsPlaying) {
                                                castHandler?.pause()
                                            } else {
                                                castHandler?.play()
                                            }
                                        } else if (playbackState == STATE_ENDED) {
                                            playerConnection.player.seekTo(0, 0)
                                            playerConnection.player.playWhenReady = true
                                        } else {
                                            playerConnection.player.togglePlayPause()
                                        }
                                    },
                            ) {
                                Image(
                                    painter =
                                    painterResource(
                                        if (isListenTogetherGuest) {
                                            if (isMuted) R.drawable.volume_off else R.drawable.volume_up
                                        } else if (playbackState ==
                                            STATE_ENDED
                                        ) {
                                            R.drawable.replay
                                        } else if (effectiveIsPlaying) {
                                            R.drawable.pause_applemusic
                                        } else {
                                            R.drawable.play_applemusic
                                        },
                                    ),
                                    contentDescription = null,
                                    colorFilter = ColorFilter.tint(TextBackgroundColor),
                                    modifier =
                                    Modifier
                                        .align(Alignment.Center)
                                        .size(72.dp),
                                )
                            }

                            Spacer(Modifier.width(8.dp))

                            Box(modifier = Modifier.weight(1f)) {
                                ResizableIconButton(
                                    icon = R.drawable.apple_skip_next,
                                    enabled = canSkipNext && !isListenTogetherGuest,
                                    color = TextBackgroundColor,
                                    modifier =
                                Modifier
                                    .size(48.dp)
                                    .align(Alignment.Center)
                                    .alpha(if (isListenTogetherGuest) 0.5f else 1f)
                                    .tvFocusable(isTvOrCar),
                                    onClick = playerConnection::seekToNext,
                                )
                            }













                        }

                        if (showPlayerVolumeControl(hidePlayerSlider)) {
                            Spacer(modifier = Modifier.height(8.dp)) 

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = PlayerHorizontalPadding)
                            ) {
                                val volumeInteractionSource = remember { MutableInteractionSource() }
                                val isVolumeDragged by volumeInteractionSource.collectIsDraggedAsState()
                                val isVolumePressed by volumeInteractionSource.collectIsPressedAsState()
                                val isVolumeActive = isVolumeDragged || isVolumePressed

                                
                                var dragVolume by remember { mutableFloatStateOf(systemVolume) }
                                
                                
                                val scope = rememberCoroutineScope()
                                
                                LaunchedEffect(systemVolume) {
                                    if (!isVolumeActive) dragVolume = systemVolume
                                }

                                
                                val animatedSystemVolume by animateFloatAsState(
                                    targetValue = systemVolume,
                                    animationSpec = tween(150, easing = LinearOutSlowInEasing),
                                    label = "animatedSystemVolume"
                                )
                                
                                val volume = if (isCasting) castVolume else {
                                    if (isVolumeActive) dragVolume else animatedSystemVolume
                                }
                                
                                val volumeTrackHeight by animateDpAsState(
                                    targetValue = if (isVolumeActive) 16.dp else 10.dp,
                                    animationSpec = spring(
                                        dampingRatio = 0.7f, 
                                        stiffness = 600f 
                                    ),
                                    label = "volumeTrackHeight"
                                )

                                val volumeIconScale by animateFloatAsState(
                                    targetValue = if (isVolumeActive) 1.15f else 1f,
                                    animationSpec = spring(
                                        dampingRatio = 0.7f,
                                        stiffness = 600f
                                    ),
                                    label = "volumeIconScale"
                                )

                                Icon(
                                    painter = painterResource(R.drawable.volume_mute),
                                    contentDescription = null,
                                    tint = textButtonColor,
                                    modifier = Modifier
                                        .size(20.dp)
                                        .graphicsLayer(scaleX = volumeIconScale, scaleY = volumeIconScale)
                                )

                                Spacer(Modifier.width(12.dp))

                                Slider(
                                    value = volume,
                                    onValueChange = { newVolume ->
                                        dragVolume = newVolume
                                        if (isCasting) {
                                            castHandler?.setVolume(newVolume)
                                        } else {
                                            val newStep = (newVolume * maxSystemVolume).roundToInt()
                                            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, newStep, 0)
                                        }
                                    },
                                    modifier = Modifier.weight(1f),
                                    interactionSource = volumeInteractionSource,
                                    thumb = {},
                                    track = { sliderState ->
                                        PlayerSliderTrack(
                                            sliderState = sliderState,
                                            colors = SliderDefaults.colors(
                                                activeTrackColor = textButtonColor.copy(alpha = 0.7f),
                                                inactiveTrackColor = textButtonColor.copy(alpha = 0.15f)
                                            ),
                                            trackHeight = volumeTrackHeight
                                        )
                                    }
                                )

                                Spacer(Modifier.width(12.dp))

                                Icon(
                                    painter = painterResource(R.drawable.volume_up),
                                    contentDescription = null,
                                    tint = textButtonColor,
                                    modifier = Modifier
                                        .size(20.dp)
                                        .graphicsLayer(scaleX = volumeIconScale, scaleY = volumeIconScale)
                                )
                            }
                        }

                        val displayBluetoothName = remember(bluetoothDeviceName) {
                            if (bluetoothDeviceName != null) bluetoothDeviceName else bluetoothDeviceName
                        }
                        
                        var lastNonNullName by remember { mutableStateOf<String?>(null) }
                        LaunchedEffect(bluetoothDeviceName) {
                            if (bluetoothDeviceName != null) lastNonNullName = bluetoothDeviceName
                        }

                        AnimatedVisibility(
                            visible = !useNewPlayerDesign && bluetoothDeviceName != null,
                            enter = fadeIn(tween(400)) + expandVertically(tween(400)),
                            exit = fadeOut(tween(400)) + shrinkVertically(tween(400)),
                            label = "BluetoothInfoVisibility"
                        ) {
                            val nameToShow = bluetoothDeviceName ?: lastNonNullName
                            Column {
                                Spacer(Modifier.height(8.dp))
                                Row(
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(
                                        painter = painterResource(
                                            when {
                                                isSpeaker(nameToShow) -> R.drawable.speaker_applemusic
                                                isBuds(nameToShow) -> R.drawable.apple_airpods
                                                else -> R.drawable.apple_headset
                                            }
                                        ),
                                        contentDescription = null,
                                        tint = textButtonColor.copy(alpha = 0.7f),
                                        modifier = Modifier.size(
                                            when {
                                                isSpeaker(nameToShow) -> 18.dp
                                                isBuds(nameToShow) -> 20.dp
                                                else -> 16.dp
                                            }
                                        )
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        text = nameToShow ?: "",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = textButtonColor.copy(alpha = 0.7f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // #47 — the two-pane "Spotify" player is selected by WIDTH, not by orientation.
        //
        // Everything needed already existed, but it hung off ORIENTATION_LANDSCAPE, and a foldable is used
        // mostly OPEN IN PORTRAIT: a near-square ~700x900 window that reports ORIENTATION_PORTRAIT and so fell
        // through to the phone layout no matter how much room it had. The split branch below is already gated
        // internally on `isWideLayout`, so routing a wide portrait window here reuses it verbatim — the two
        // branch BODIES are unchanged, only which one is picked.
        //
        // VIDEO IS DELIBERATELY EXCLUDED from the width route. Video has its own orientation-specific paths
        // (landscape = fullscreen with system bars hidden, portrait = the premium immersive/ambient layout),
        // and the video surface is the single most regression-prone area in this file (registry #43 — the
        // freeze traced to the surface lifecycle). Sending wide portrait into the landscape video path would
        // silently change which surface path runs, for no gain. So video keeps routing by real orientation and
        // only the NON-video player picks up the width rule.
        val playerOrientation = LocalConfiguration.current.orientation
        val isRealLandscape = playerOrientation == Configuration.ORIENTATION_LANDSCAPE
        val videoUrlNow by playerConnection.videoUrl.collectAsState()
        val inVideoMode = videoMode && !videoUrlNow.isNullOrEmpty()
        val useWidePlayer = isRealLandscape || (isWideLayout && !inVideoMode)
        when {
            useWidePlayer -> {
              val videoUrlLs by playerConnection.videoUrl.collectAsState()
              if (videoMode && !videoUrlLs.isNullOrEmpty()) {
                // Rotated + video → FULLSCREEN video with auto-hiding controls (tap toggles them). In PiP
                // (a 16:9 video makes the floating window landscape) render a CLEAN view: only title+artist
                // over the video, no controls (playback controls come from the system PiP actions).
                val inPip = LocalIsInPipMode.current
                // True FULLSCREEN in landscape video: hide the system bars so the video covers the whole
                // device screen (swipe to reveal them); restore them when leaving this view.
                val lsView = LocalView.current
                DisposableEffect(Unit) {
                    val win = (lsView.context as? android.app.Activity)?.window
                    val ctrl = win?.let { WindowCompat.getInsetsController(it, lsView) }
                    runCatching {
                        ctrl?.hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
                        ctrl?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                    }
                    onDispose { runCatching { ctrl?.show(androidx.core.view.WindowInsetsCompat.Type.systemBars()) } }
                }
                var lsControls by remember { mutableStateOf(true) }
                // Keep the root-pinned CastButton in step with the tap-to-hide controls (clean view).
                LaunchedEffect(lsControls) { immersiveControlsVisible = lsControls }
                DisposableEffect(Unit) { onDispose { immersiveControlsVisible = true } }
                // Auto-hide after 3.5 s. NOT keyed on isPlaying — buffering/play-state changes would keep
                // restarting the timer (so it never hid, e.g. while HD video rebuffers).
                // TV/car: a remote can't tap to bring hidden controls back, so DON'T auto-hide on TV — the
                // transport stays on screen and D-pad focusable. Touch devices keep the 3.5 s auto-hide.
                LaunchedEffect(lsControls, inPip, isTvOrCar) {
                    if (lsControls && !inPip && !isTvOrCar) { delay(3500); lsControls = false }
                }
                // TV/car: land initial focus on the video play/pause so the controls are immediately navigable;
                // combined with the key handler below, a D-pad user is never stuck without controls.
                val lsVideoFocus = remember { androidx.compose.ui.focus.FocusRequester() }
                LaunchedEffect(isTvOrCar, lsControls) {
                    if (isTvOrCar && lsControls) {
                        repeat(10) { runCatching { lsVideoFocus.requestFocus() }; delay(50) }
                    }
                }
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black)
                        // TV/car: any D-pad key re-shows the controls (and, with no auto-hide on TV, keeps them
                        // up). Returns false so the key still performs its normal focus navigation.
                        .onPreviewKeyEvent {
                            if (isTvOrCar && it.type == KeyEventType.KeyDown) lsControls = true
                            false
                        }
                        .pointerInput(Unit) { detectTapGestures { if (!inPip) lsControls = !lsControls } },
                ) {
                    // In PiP the top-level overlay (MainActivity) renders the video; skip the sheet's surface
                    // so only one TextureView attaches to the player.
                    if (!inPip) {
                        PlayerVideoSurface(
                            playerConnection = playerConnection,
                            modifier = Modifier.fillMaxSize(),
                            // Fill the whole screen (cover/crop) in landscape — no black side bars.
                            fillCrop = true,
                        )
                    }
                    // Transparent layer OVER the video — the fullscreen TextureView can swallow taps, so this
                    // ensures a tap reliably shows/hides the controls in landscape fullscreen.
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .pointerInput(Unit) { detectTapGestures { if (!inPip) lsControls = !lsControls } },
                    )
                    if (inPip) {
                        mediaMetadata?.let { mm ->
                            Column(
                                modifier = Modifier
                                    .align(Alignment.TopStart)
                                    .fillMaxWidth()
                                    .background(Color.Black.copy(alpha = 0.35f))
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                            ) {
                                Text(
                                    text = mm.title,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                if (mm.artists.any { it.name.isNotBlank() }) {
                                    Text(
                                        text = mm.artists.joinToString(", ") { it.name },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color.White.copy(alpha = 0.85f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                    }
                    androidx.compose.animation.AnimatedVisibility(
                        visible = lsControls && !inPip,
                        enter = fadeIn(),
                        exit = fadeOut(),
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth(),
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(20.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color.Black.copy(alpha = 0.4f))
                                .windowInsetsPadding(WindowInsets.systemBars)
                                .padding(horizontal = 24.dp, vertical = 14.dp),
                        ) {
                            Spacer(Modifier.weight(1f))
                            IconButton(
                                onClick = { playerConnection.seekToPrevious() },
                                modifier = Modifier.tvFocusable(isTvOrCar),
                            ) {
                                Icon(painterResource(R.drawable.skip_previous), null, tint = Color.White, modifier = Modifier.size(34.dp))
                            }
                            IconButton(
                                onClick = { playerConnection.player.togglePlayPause() },
                                modifier = Modifier
                                    .focusRequester(lsVideoFocus)
                                    .tvFocusable(isTvOrCar),
                            ) {
                                Icon(painterResource(if (isPlaying) R.drawable.pause else R.drawable.play), null, tint = Color.White, modifier = Modifier.size(44.dp))
                            }
                            IconButton(
                                onClick = { playerConnection.seekToNext() },
                                modifier = Modifier.tvFocusable(isTvOrCar),
                            ) {
                                Icon(painterResource(R.drawable.skip_next), null, tint = Color.White, modifier = Modifier.size(34.dp))
                            }
                            Spacer(Modifier.weight(1f))
                            IconButton(
                                onClick = { playerConnection.exitVideoMode() },
                                modifier = Modifier.tvFocusable(isTvOrCar),
                            ) {
                                Icon(painterResource(R.drawable.music_note), null, tint = Color.White, modifier = Modifier.size(30.dp))
                            }
                        }
                    }
                }
              // No `!isWideLayout` here: with it, the setting was a no-op on exactly the devices it is offered
              // to (a foldable IS wide), i.e. a translated, user-visible toggle that did nothing — the same
              // placebo shape this release exists to remove. The opt-in is the gate; it defaults OFF, so the
              // "interface disappears when I rotate" complaint stays fixed unless the user asks for it.
              } else if (canvasArtwork != null && immersiveCanvasOnRotate) {
                // Rotated + canvas (Apple-Music animated background) → show it FULLSCREEN (the background
                // canvas already fills the screen behind) with auto-hiding controls (tap toggles them).
                //
                // REGISTRY #48: this branch renders ONLY a transport Row that auto-hides after 3.5s — no cover,
                // title, progress, queue or lyrics — and it used to intercept EVERY landscape rotation whenever a
                // canvas existed (artist background video defaults ON), so rotating simply ate the entire UI. It
                // is now (a) OPT-IN via [ImmersiveCanvasOnRotateKey] (default false) so the interface stays put
                // "sin importar en qué ángulo giro", and (b) never taken on a wide screen, which has ample room
                // for the real split layout below. The VIDEO branch above keeps its fullscreen behaviour.
                var lsCanvasControls by remember { mutableStateOf(true) }
                // Keep the root-pinned CastButton in step with the tap-to-hide controls (clean view).
                LaunchedEffect(lsCanvasControls) { immersiveControlsVisible = lsCanvasControls }
                DisposableEffect(Unit) { onDispose { immersiveControlsVisible = true } }
                // TV/car: don't auto-hide (a remote can't tap them back); touch keeps the 3.5 s auto-hide.
                LaunchedEffect(lsCanvasControls, isPlaying, isTvOrCar) {
                    if (lsCanvasControls && !isTvOrCar) { delay(3500); lsCanvasControls = false }
                }
                val lsCanvasFocus = remember { androidx.compose.ui.focus.FocusRequester() }
                LaunchedEffect(isTvOrCar, lsCanvasControls) {
                    if (isTvOrCar && lsCanvasControls) {
                        repeat(10) { runCatching { lsCanvasFocus.requestFocus() }; delay(50) }
                    }
                }
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        // TV/car: any D-pad key re-shows the controls. Returns false so navigation still runs.
                        .onPreviewKeyEvent {
                            if (isTvOrCar && it.type == KeyEventType.KeyDown) lsCanvasControls = true
                            false
                        }
                        .pointerInput(Unit) { detectTapGestures { lsCanvasControls = !lsCanvasControls } },
                ) {
                    androidx.compose.animation.AnimatedVisibility(
                        visible = lsCanvasControls,
                        enter = fadeIn(),
                        exit = fadeOut(),
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth(),
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(20.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color.Black.copy(alpha = 0.35f))
                                .windowInsetsPadding(WindowInsets.systemBars)
                                .padding(horizontal = 24.dp, vertical = 14.dp),
                        ) {
                            Spacer(Modifier.weight(1f))
                            IconButton(
                                onClick = { playerConnection.seekToPrevious() },
                                modifier = Modifier.tvFocusable(isTvOrCar),
                            ) {
                                Icon(painterResource(R.drawable.skip_previous), null, tint = Color.White, modifier = Modifier.size(34.dp))
                            }
                            IconButton(
                                onClick = { playerConnection.player.togglePlayPause() },
                                modifier = Modifier
                                    .focusRequester(lsCanvasFocus)
                                    .tvFocusable(isTvOrCar),
                            ) {
                                Icon(painterResource(if (isPlaying) R.drawable.pause else R.drawable.play), null, tint = Color.White, modifier = Modifier.size(44.dp))
                            }
                            IconButton(
                                onClick = { playerConnection.seekToNext() },
                                modifier = Modifier.tvFocusable(isTvOrCar),
                            ) {
                                Icon(painterResource(R.drawable.skip_next), null, tint = Color.White, modifier = Modifier.size(34.dp))
                            }
                            Spacer(Modifier.weight(1f))
                        }
                    }
                }
              } else {
                val density = LocalDensity.current
                val verticalPadding = max(
                    WindowInsets.systemBars.getTop(density),
                    WindowInsets.systemBars.getBottom(density)
                )
                val verticalPaddingDp = with(density) { verticalPadding.toDp() }
                val verticalWindowInsets = WindowInsets(left = 0.dp, top = verticalPaddingDp, right = 0.dp, bottom = verticalPaddingDp)

                // The collapsed queue handle is drawn OVER this pane on exactly the same condition as in
                // portrait (`!isFullScreen && !onImmersiveVideo` — see the Queue's AnimatedVisibility at the
                // end of this sheet), and its height is SHEET-DERIVED: collapsedBound = QueuePeekHeight (64dp)
                // + the bottom system inset + 1dp. A hardcoded 24.dp was therefore always short, leaving the
                // bottom of the wide/landscape layout sitting UNDER the handle. Pad by the sheet's own bound
                // like the portrait branch does. The vertical inset is ALREADY applied by
                // `verticalWindowInsets` above, so subtract it to avoid double-counting, and never drop below
                // the original 24.dp. When the sheet is not drawn this stays exactly at 24.dp, so no permanent
                // dead space is added to the fullscreen / immersive-video cases.
                val wideBottomPadding by animateDpAsState(
                    targetValue =
                        if (isFullScreen || onImmersiveVideo) 24.dp
                        else (queueSheetState.collapsedBound - verticalPaddingDp).coerceAtLeast(24.dp),
                    label = "wideBottomPadding"
                )

                Row(
                    modifier = Modifier
                        .windowInsetsPadding(
                            WindowInsets.systemBars.only(WindowInsetsSides.Horizontal).add(verticalWindowInsets)
                        )
                        .padding(bottom = wideBottomPadding)
                        .fillMaxSize()
                ) {
                    val currentSliderPosition by rememberUpdatedState(sliderPosition)
                    val sliderPositionProvider = remember { { currentSliderPosition } }
                    val isExpandedProvider = remember(state) { { state.isExpanded } }

                    if (isWideLayout && !showInlineLyrics) {
                        // Spotify-style WIDE player: live queue on the LEFT, and a BALANCED now-playing pane on the
                        // RIGHT — cover on top, controls centered below it — instead of a huge cover beside a thin
                        // controls column jammed against the screen edge. Only on wide screens; lyrics/phones fall
                        // through to the side-by-side layout below.
                        LandscapeQueuePane(
                            // Match the player's own text treatment (white over blur/gradient) so the queue is
                            // readable in every theme now that the rows are flat/transparent.
                            contentColor = TextBackgroundColor,
                            castHandler = castHandler,
                            isCasting = isCasting,
                            castIsPlaying = castIsPlaying,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .padding(end = 16.dp)
                        )
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier
                                .weight(1.7f)
                                .fillMaxHeight()
                                // TV/car: group the now-playing pane so D-pad directional search moves cleanly
                                // between this pane and the queue pane (free traversal in every direction).
                                .focusGroup()
                                .windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Top))
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .weight(1f, fill = false)
                                    .fillMaxWidth()
                                    .nestedScroll(state.preUpPostDownNestedScrollConnection)
                                    .SwipeGesture(
                                        enabled = isFullScreen,
                                        onSwipeRight = { playerConnection.seekToPrevious() },
                                        onSwipeLeft = { playerConnection.seekToNext() },
                                    )
                            ) {
                                Thumbnail(
                                    sliderPositionProvider = sliderPositionProvider,
                                    modifier = Modifier.animateContentSize(),
                                    isPlayerExpanded = isExpandedProvider,
                                    isLandscape = true,
                                    isListenTogetherGuest = isListenTogetherGuest
                                )
                            }
                            Spacer(Modifier.height(16.dp))
                            mediaMetadata?.let {
                                controlsContent(it, false)
                            }
                        }
                    } else {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .weight(1f)
                                .nestedScroll(state.preUpPostDownNestedScrollConnection)
                                // Swipe the artwork left/right to change song (landscape).
                                .SwipeGesture(
                                    enabled = isFullScreen,
                                    onSwipeRight = { playerConnection.seekToPrevious() },
                                    onSwipeLeft = { playerConnection.seekToNext() },
                                )
                        ) {
                            AnimatedContent(
                                targetState = showInlineLyrics,
                                label = "Lyrics",
                                transitionSpec = { fadeIn() togetherWith fadeOut() }
                            ) { showLyrics ->
                                if (showLyrics) {
                                    InlineLyricsView(
                                        mediaMetadata = mediaMetadata,
                                        showLyrics = showLyrics,
                                        positionProvider = { sliderPosition ?: if (isCasting) castPosition else null }
                                    )
                                } else {
                                    Thumbnail(
                                        sliderPositionProvider = sliderPositionProvider,
                                        modifier = Modifier.animateContentSize(),
                                        isPlayerExpanded = isExpandedProvider,
                                        isLandscape = true,
                                        isListenTogetherGuest = isListenTogetherGuest
                                    )
                                }
                            }
                        }

                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .weight(if (showInlineLyrics) 0.65f else 1f, false)
                                .animateContentSize()
                                .windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Top))
                        ) {
                            Spacer(Modifier.weight(1f))

                            mediaMetadata?.let {
                                controlsContent(it, false)
                            }

                            Spacer(Modifier.weight(1f))
                        }
                    }
                }
              }
            }

            else -> {
                val videoUrlPt by playerConnection.videoUrl.collectAsState()
                // Smooth crossfade between the immersive video layout and the normal song layout when video
                // mode toggles. Safe re: the video surface — the video is only ever rendered here (premium)
                // / in the landscape branch, never by Thumbnail, so no two TextureViews fight during the fade.
                Crossfade(
                    targetState = videoMode && !videoUrlPt.isNullOrEmpty(),
                    animationSpec = tween(350),
                    modifier = Modifier.fillMaxSize(),
                    label = "videoModeTransition",
                ) { immersive ->
                if (immersive) {
                    // PORTRAIT + video → PREMIUM immersive video: ambient blurred-cover backdrop, the video
                    // edge-to-edge (full width, centered, correct aspect via PlayerVideoSurface), and the
                    // normal controls OVERLAID and AUTO-HIDING (tap toggles them). Back exits video.
                    // Controls toggle ONLY by tapping the video (no timed auto-hide): tap once to hide them
                    // for a clean view, tap again to bring them back. Back exits video.
                    var ptControls by remember { mutableStateOf(true) }
                    // Keep the root-pinned CastButton in step with the tap-to-hide controls (clean view).
                    LaunchedEffect(ptControls) { immersiveControlsVisible = ptControls }
                    DisposableEffect(Unit) { onDispose { immersiveControlsVisible = true } }
                    // In Picture-in-Picture render a CLEAN view: keep the title/artist over the video, but hide
                    // the bottom controls and the toggle — playback controls come from the system PiP actions.
                    val inPip = LocalIsInPipMode.current
                    // NOTE: no BackHandler here — the Android back gesture must NOT exit video; it should just
                    // minimize the player (keeping video mode on), per user request.
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(Unit) { detectTapGestures { ptControls = !ptControls } },
                    ) {
                        // Ambient backdrop: blurred + darkened cover art (cheap → smooth on low-end; NO live
                        // video-frame sampling). Mirrors the BLUR player-background pattern.
                        mediaMetadata?.thumbnailUrl?.let { thumb ->
                            AsyncImage(
                                model = ImageRequest.Builder(context)
                                    .data(thumb)
                                    .size(100, 100)
                                    .allowHardware(false)
                                    .build(),
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .matchParentSize()
                                    .blur(40.dp),
                            )
                        }
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .background(Color.Black.copy(alpha = 0.55f)),
                        )
                        // Title (top) + video (centered) + controls (bottom) in a Column so the video is
                        // truly CENTERED in the space between the top title and the bottom controls.
                        Column(modifier = Modifier.fillMaxSize()) {
                        // Title + artist ABOVE the video — shown only while the controls are visible (or in
                        // PiP). Tapping to hide the controls hides the title too → clean full-screen video.
                        if (inPip || ptControls) mediaMetadata?.let { mm ->
                            Column(
                                horizontalAlignment = if (inPip) Alignment.Start else Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .then(if (inPip) Modifier.background(Color.Black.copy(alpha = 0.35f)) else Modifier)
                                    .windowInsetsPadding(WindowInsets.systemBars)
                                    // 56dp side padding (non-PiP) keeps long marquee titles clear of the
                                    // root-pinned cast button at the top-right; symmetric so the title
                                    // stays visually centered.
                                    .padding(horizontal = if (inPip) 10.dp else 56.dp, vertical = if (inPip) 6.dp else 10.dp),
                            ) {
                                Text(
                                    text = mm.title,
                                    style = if (inPip) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = if (inPip) Modifier else Modifier.basicMarquee(iterations = 1, initialDelayMillis = 3000, velocity = 30.dp),
                                )
                                if (mm.artists.any { it.name.isNotBlank() }) {
                                    Text(
                                        text = mm.artists.joinToString(", ") { it.name },
                                        style = if (inPip) MaterialTheme.typography.bodySmall else MaterialTheme.typography.titleMedium,
                                        color = Color.White.copy(alpha = 0.85f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                        // Video band CENTERED in the remaining space (between the top title and the bottom
                        // controls). The inner Box wraps the band so the toggle pins to the band's corner.
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            // When the controls (and title) are visible they take more room at the bottom than
                            // the title does at the top, so the region centre sits high on screen — push the
                            // video DOWN to look screen-centred. When everything is hidden, just centre it.
                            contentAlignment = if (ptControls && !inPip) BiasAlignment(0f, 0.28f) else Alignment.Center,
                        ) {
                            Box(
                                modifier = Modifier.fillMaxWidth(),
                                contentAlignment = Alignment.Center,
                            ) {
                                // In PiP the video is rendered by the top-level PiP overlay (MainActivity) so
                                // the floating window is just the video — don't attach a 2nd TextureView here.
                                if (!inPip) {
                                    PlayerVideoSurface(
                                        playerConnection = playerConnection,
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                }
                                if (ptControls && !inPip) {
                                    FilledIconButton(
                                        onClick = { playerConnection.toggleVideoMode() },
                                        colors = IconButtonDefaults.filledIconButtonColors(
                                            containerColor = Color.White.copy(alpha = 0.85f),
                                            contentColor = Color.Black,
                                        ),
                                        modifier = Modifier
                                            .align(Alignment.BottomEnd)
                                            .padding(8.dp)
                                            .size(40.dp),
                                    ) {
                                        Icon(
                                            painter = painterResource(R.drawable.music_note),
                                            contentDescription = null,
                                            modifier = Modifier.size(22.dp),
                                        )
                                    }
                                }
                            }
                        }
                        // Tap-toggled controls (title, scrubbable seekbar, transport, chips, video toggle).
                        // NO dark bar behind them — they sit over the dark ambient so the video stays fully
                        // visible; their colors are forced white-on-dark (immersiveVideo = true).
                        androidx.compose.animation.AnimatedVisibility(
                            visible = ptControls && !inPip,
                            enter = fadeIn(),
                            exit = fadeOut(),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    // Absorb taps that land in the controls band (and its empty gaps) so a
                                    // near-miss on a button doesn't fall through to the parent and hide the
                                    // controls — only taps on the video/ambient area toggle them.
                                    .pointerInput(Unit) { detectTapGestures { } }
                                    .windowInsetsPadding(WindowInsets.systemBars)
                                    .padding(horizontal = 8.dp, vertical = 12.dp),
                            ) {
                                mediaMetadata?.let { controlsContent(it, true) }
                            }
                        }
                        }
                    }
                } else {
                val bottomPadding by animateDpAsState(
                    targetValue = if (isFullScreen) 0.dp else queueSheetState.collapsedBound,
                    label = "bottomPadding"
                )
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier =
                    Modifier
                        .windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Horizontal))
                        .padding(bottom = bottomPadding)
                        .animateContentSize()
                        // YT-Music parity: swiping UP anywhere on the player body (artwork + controls)
                        // drags the queue sheet open, mirroring BottomSheet.kt's own gesture math
                        // (dispatchRawDelta while dragging + performFling on release, same ±250 velocity
                        // threshold). Only clearly-vertical UPWARD drags are claimed: the touch-slop
                        // callback consumes EXCLUSIVELY when overSlop < 0, so
                        //  - downward drags stay unconsumed and the parent player BottomSheet's own
                        //    detectVerticalDragGestures still collapses the player,
                        //  - horizontal artwork swipes (prev/next SwipeGesture) win their own axis slop,
                        //  - lyrics scroll / seekbar / chip-row scroll sit DEEPER in the tree, so they
                        //    consume first and this ancestor detector is cancelled.
                        // Event-driven only (no polling/animation while idle) — heat/battery safe.
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
                    Box(
                        contentAlignment = Alignment.TopCenter,
                        modifier = Modifier
                            .weight(1f)
                            // Swipe the artwork left/right to change song. Placed here (deepest cover
                            // container) and consuming horizontal drags so it wins over the bottom
                            // sheet's vertical collapse gesture instead of minimizing the player.
                            .SwipeGesture(
                                enabled = isFullScreen,
                                onSwipeRight = { playerConnection.seekToPrevious() },
                                onSwipeLeft = { playerConnection.seekToNext() },
                            ),
                    ) {
                        
                        val currentSliderPosition by rememberUpdatedState(sliderPosition)
                        val sliderPositionProvider = remember { { currentSliderPosition } }
                        val isExpandedProvider = remember(state) { { state.isExpanded } }
                        AnimatedContent(
                            targetState = showInlineLyrics,
                            label = "Lyrics",
                            transitionSpec = { fadeIn() togetherWith fadeOut() }
                        ) { showLyrics ->
                            if (showLyrics) {
                                InlineLyricsView(
                                    mediaMetadata = mediaMetadata,
                                    showLyrics = showLyrics,
                                    positionProvider = { sliderPosition ?: if (isCasting) castPosition else null }
                                )
                            } else {
                                Thumbnail(
                                    sliderPositionProvider = sliderPositionProvider,
                                    modifier = Modifier.nestedScroll(state.preUpPostDownNestedScrollConnection),
                                    isPlayerExpanded = isExpandedProvider,
                                    isListenTogetherGuest = isListenTogetherGuest
                                )
                            }
                        }
                    }

                    mediaMetadata?.let {
                        controlsContent(it, false)
                    }

                    Spacer(Modifier.height(if (useNewPlayerDesign) 30.dp else 8.dp))
                }
                }
                }
            }
        }

        // CAST: the cast/output button is PINNED to the TOP-RIGHT of the expanded player in EVERY layout
        // (portrait, inline lyrics, Apple-Music full-screen canvas, landscape, wide/TV split, video) — it
        // used to live only inside the portrait ThumbnailHeader, so it vanished with lyrics/canvas/landscape.
        // Hidden in PiP (the floating window must stay a clean video). Gated on the queue sheet NOT being
        // expanded — merely drawing it before the Queue covered it visually but left it in the D-pad focus
        // search under the expanded sheet. Gated on immersiveControlsVisible so it follows the immersive
        // layouts' tap-to-hide controls (clean full-screen video/canvas view) instead of floating alone.
        // FOSS builds: CastButton is a no-op stub, nothing renders.
        if (!LocalIsInPipMode.current && !queueSheetState.isExpanded && immersiveControlsVisible) {
            CastButton(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Top + WindowInsetsSides.End))
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .size(24.dp),
                tintColor = TextBackgroundColor,
            )
        }

        AnimatedVisibility(
            // Hide the collapsed queue bar while immersive video is up — it would otherwise overlap the
            // video's own bottom transport controls.
            visible = !isFullScreen && !onImmersiveVideo,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = shrinkVertically(shrinkTowards = Alignment.Top) + slideOutVertically(targetOffsetY = { it }) + fadeOut()
        ) {
            Queue(
                state = queueSheetState,
                playerBottomSheetState = state,
            navController = navController,
            background =
            if (useBlackBackground) {
                Color.Black
            } else {
                MaterialTheme.colorScheme.surface 
            },
            onBackgroundColor = onBackgroundColor,
            TextBackgroundColor = TextBackgroundColor,
            textButtonColor = textButtonColor,
            iconButtonColor = iconButtonColor,
            pureBlack = pureBlack,
            showInlineLyrics = showInlineLyrics,
            playerBackground = playerBackground,
            onToggleLyrics = {
                // Lyrics aren't available while watching the video.
                if (!videoMode) showInlineLyrics = !showInlineLyrics
            },
            )
        }
    }
}

/** YouTube-Music-style labeled action chip (icon + name) for the player's scrollable action row. */
@Composable
private fun PlayerActionChip(
    label: String,
    tint: Color,
    container: Color,
    onClick: () -> Unit,
    leading: @Composable () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(container)
            // TV/car: visible focus ring observing the .clickable below (Me gusta / No me gusta / Agregar / Compartir).
            .tvFocusable(iad1tya.echo.music.ui.utils.rememberIsTvOrCar())
            .clickable(onClick = onClick)
            .padding(start = 12.dp, end = 16.dp, top = 9.dp, bottom = 9.dp),
    ) {
        leading()
        Spacer(Modifier.width(6.dp))
        Text(
            text = label,
            color = tint,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
        )
    }
}

/**
 * YouTube-Music-style divided like/dislike pill. ONE rounded container split into two tappable halves —
 * LEFT = like (thumb up), RIGHT = dislike (thumb down) — separated by a subtle vertical divider. The active
 * half fills with [activeColor] (icon in [activeContentColor]); a neutral half stays transparent over
 * [container] with the icon outlined in [activeColor]. Compact height matches [PlayerActionChip]. Each half
 * routes straight through the caller's toggle callback — no audio re-leveling happens here.
 */
@Composable
private fun PlayerLikeDislikePill(
    liked: Boolean,
    disliked: Boolean,
    activeColor: Color,
    activeContentColor: Color,
    container: Color,
    onToggleLike: () -> Unit,
    onToggleDislike: () -> Unit,
    likeContentDescription: String,
    dislikeContentDescription: String,
) {
    val isTvOrCar = iad1tya.echo.music.ui.utils.rememberIsTvOrCar()
    Row(
        verticalAlignment = Alignment.CenterVertically,
        // Parent clip rounds the outer corners of BOTH filled halves; each half butts flat against the divider.
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(container),
    ) {
        // LEFT half — LIKE (filled when liked).
        Box(
            modifier = Modifier
                .background(if (liked) activeColor else Color.Transparent)
                .tvFocusable(isTvOrCar)
                .clickable(onClick = onToggleLike)
                .padding(start = 14.dp, end = 11.dp, top = 9.dp, bottom = 9.dp),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = if (liked) Icons.Filled.ThumbUp else Icons.Outlined.ThumbUp,
                contentDescription = likeContentDescription,
                tint = if (liked) activeContentColor else activeColor,
                modifier = Modifier.size(20.dp),
            )
        }
        // Subtle divider between the two halves.
        Box(
            modifier = Modifier
                .width(1.dp)
                .height(20.dp)
                .background(activeColor.copy(alpha = 0.25f)),
        )
        // RIGHT half — DISLIKE (filled when disliked).
        Box(
            modifier = Modifier
                .background(if (disliked) activeColor else Color.Transparent)
                .tvFocusable(isTvOrCar)
                .clickable(onClick = onToggleDislike)
                .padding(start = 11.dp, end = 14.dp, top = 9.dp, bottom = 9.dp),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = if (disliked) Icons.Filled.ThumbDown else Icons.Outlined.ThumbDown,
                contentDescription = dislikeContentDescription,
                tint = if (disliked) activeContentColor else activeColor,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

/**
 * Spotify-style queue pane for the LEFT side of the wide (TV / tablet / car / unfolded-foldable) player split.
 * Renders the live play queue as a focusable list: the current song is highlighted, tapping/clicking (or D-pad
 * center) a row jumps to it, and the list auto-scrolls to the current song. Rows use the flat modern
 * [LandscapeQueueRow] (with the TV focus ring), so D-pad navigation works out of the box.
 */
@Composable
private fun LandscapeQueuePane(
    contentColor: Color,
    // Cast state is threaded in rather than re-derived: the player already holds it, and a second
    // `collectAsState` here would give this pane its own view of "are we casting" that can disagree
    // with the transport's for a frame.
    castHandler: iad1tya.echo.music.playback.CastConnectionHandler?,
    isCasting: Boolean,
    castIsPlaying: Boolean,
    modifier: Modifier = Modifier,
) {
    val playerConnection = LocalPlayerConnection.current ?: return
    val windows by playerConnection.queueWindows.collectAsState()
    val currentWindowIndex by playerConnection.currentWindowIndex.collectAsState()
    val isPlaying by playerConnection.isPlaying.collectAsState()
    val lazyState = rememberLazyListState()

    LaunchedEffect(currentWindowIndex, windows.size) {
        if (currentWindowIndex in windows.indices) {
            runCatching { lazyState.animateScrollToItem(currentWindowIndex) }
        }
    }

    val isTvOrCar = iad1tya.echo.music.ui.utils.rememberIsTvOrCar()
    LazyColumn(
        state = lazyState,
        // TV/car: focus group so D-pad directional search enters/leaves the queue pane cleanly, plus a focus
        // restorer so scrolling to a not-yet-composed row keeps the ring on the last row instead of dropping it.
        modifier = modifier.tvFocusRestorer().focusGroup(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        itemsIndexed(
            items = windows,
            key = { _, window -> window.uid.hashCode() },
        ) { index, window ->
            val isActive = index == currentWindowIndex
            window.mediaItem.metadata?.let { meta ->
                LandscapeQueueRow(
                    meta = meta,
                    isActive = isActive,
                    isPlaying = isPlaying && isActive,
                    isTvOrCar = isTvOrCar,
                    contentColor = contentColor,
                    onClick = {
                        // CASTING: the local player is not what the user is listening to. Without these
                        // branches, tapping a row while casting seeked the PHONE — the speaker kept
                        // playing the old song and the tap looked like it had done nothing.
                        // `navigateToMediaIfInQueue` returns false when the receiver's own queue does not
                        // hold that id, and then seeking locally is the correct fallback (that is what
                        // re-casts the right track).
                        if (isActive) {
                            if (isCasting) {
                                if (castIsPlaying) castHandler?.pause() else castHandler?.play()
                            } else {
                                playerConnection.togglePlayPause()
                            }
                        } else if (isCasting) {
                            val navigated =
                                castHandler?.navigateToMediaIfInQueue(window.mediaItem.mediaId) ?: false
                            if (!navigated) {
                                playerConnection.player.seekToDefaultPosition(window.firstPeriodIndex)
                            }
                        } else {
                            playerConnection.player.seekToDefaultPosition(window.firstPeriodIndex)
                            playerConnection.player.playWhenReady = true
                        }
                    },
                )
            }
        }
    }
}

/**
 * Modern, flat Material3 row for the wide/TV split-player queue — replaces the old boxed white ListItem look.
 * The row is transparent by default; the CURRENT track gets a soft secondaryContainer tint, a primary accent
 * bar and a bold title so it's clearly marked. Larger cover + spacing for a TV, and the D-pad focus ring lights
 * on the whole row. Only used by the wide/TV [LandscapeQueuePane], so the phone queue stays exactly as it was.
 */
@Composable
private fun LandscapeQueueRow(
    meta: MediaMetadata,
    isActive: Boolean,
    isPlaying: Boolean,
    isTvOrCar: Boolean,
    contentColor: Color,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(12.dp)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
            .clip(shape)
            // Ring sits ABOVE the clickable so it observes the row's focus and lights the whole row.
            .tvFocusable(isTvOrCar, shape)
            .clickable(onClick = onClick)
            .background(
                if (isActive) MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.9f)
                else Color.Transparent
            )
            .padding(horizontal = 8.dp, vertical = 8.dp),
    ) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(44.dp)
                .clip(RoundedCornerShape(50))
                .background(if (isActive) MaterialTheme.colorScheme.primary else Color.Transparent)
        )
        AsyncImage(
            model = meta.thumbnailUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(8.dp)),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = meta.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
                color = if (isActive) MaterialTheme.colorScheme.onSecondaryContainer else contentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = meta.artists.joinToString { it.name },
                style = MaterialTheme.typography.bodySmall,
                color = if (isActive) MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.75f)
                        else contentColor.copy(alpha = 0.7f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        // Small now-playing dot for the active + playing track (clear affordance without an extra icon asset).
        if (isPlaying) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(RoundedCornerShape(50))
                    .background(MaterialTheme.colorScheme.primary)
            )
        }
        Text(
            text = makeTimeString(meta.duration * 1000L),
            style = MaterialTheme.typography.labelMedium,
            color = if (isActive) MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.75f)
                    else contentColor.copy(alpha = 0.7f),
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun InlineLyricsView(
    mediaMetadata: MediaMetadata?,
    showLyrics: Boolean,
    // Nullable on purpose: when it returns null, the Lyrics view falls through to the LIVE player position
    // (its own 8 ms ticker) so the word-by-word highlight sweeps smoothly. The outer player position state is
    // throttled to 500 ms (to avoid a recomposition storm); feeding that to the lyrics made them jump in
    // 500 ms steps. We only provide a value while scrubbing (preview) or casting (the active stream).
    positionProvider: () -> Long?
) {
    val playerConnection = LocalPlayerConnection.current ?: return
    val rawCurrentLyrics by playerConnection.currentLyrics.collectAsState(initial = null)
    // Wrong-song guard: ignore a lyrics row that belongs to a previously-playing song (the
    // flatMapLatest DB flow lags a track change by a frame), so the previous song's lyrics can
    // never flash onto the current one — during that gap we correctly stay in the LOADING state.
    val currentLyrics = rawCurrentLyrics?.takeIf { it.id == mediaMetadata?.id }
    val lyrics = remember(currentLyrics) { currentLyrics?.lyrics?.trim() }
    val context = LocalContext.current
    val database = LocalDatabase.current
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(mediaMetadata?.id, currentLyrics) {
        val requested = mediaMetadata ?: return@LaunchedEffect
        if (currentLyrics != null) return@LaunchedEffect
        delay(500)
        // Song may have changed during the delay — effect is cancelled on id change; this
        // guard covers the window between delay wake and the next cancellation delivery.
        if (mediaMetadata?.id != requested.id) return@LaunchedEffect
        withContext(Dispatchers.IO) {
            try {
                val entryPoint = EntryPointAccessors.fromApplication(
                    context.applicationContext,
                    iad1tya.echo.music.di.LyricsHelperEntryPoint::class.java
                )
                val lyricsHelper = entryPoint.lyricsHelper()
                val fetchedLyricsWithProvider = lyricsHelper.getLyrics(requested)
                // Structured cancel: if the user skipped mid-fetch, do not write the result.
                ensureActive()
                database.query {
                    upsert(
                        LyricsEntity(
                            id = requested.id,
                            lyrics = fetchedLyricsWithProvider.lyrics,
                            provider = fetchedLyricsWithProvider.provider,
                        ),
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                timber.log.Timber.e(e, "Lyrics fetch/save failed")
            }
        }
    }

    Box (
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(12.dp)),
        contentAlignment = Alignment.Center
    ) {
        when {
            lyrics == null -> {
                ContainedLoadingIndicator()
            }
            lyrics == LyricsEntity.LYRICS_NOT_FOUND -> {
                Text(
                    text = stringResource(R.string.lyrics_not_found),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center
                )
            }
            else -> {
                val lyricsContent: @Composable () -> Unit = {
                    Lyrics(
                        sliderPositionProvider = positionProvider,
                        modifier = Modifier.padding(horizontal = 24.dp),
                        showLyrics = showLyrics
                    )
                }
                ProvideTextStyle(
                    value = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center
                    )
                ) {
                    lyricsContent()
                }
            }
        }
    }
}


@Composable
fun MoreActionsButton(
    mediaMetadata: MediaMetadata,
    navController: NavController,
    state: BottomSheetState,
    textButtonColor: Color,
    iconButtonColor: Color
) {
    val menuState = LocalMenuState.current
    val bottomSheetPageState = LocalBottomSheetPageState.current

    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(textButtonColor)
            .clickable {
                menuState.show {
                    PlayerMenu(
                        mediaMetadata = mediaMetadata,
                        navController = navController,
                        playerBottomSheetState = state,
                        onShowDetailsDialog = {
                            mediaMetadata.id.let {
                                bottomSheetPageState.show {
                                    ShowMediaInfo(it)
                                }
                            }
                        },
                        onDismiss = menuState::dismiss
                    )
                }
            }
    ) {
        Image(
            painter = painterResource(R.drawable.more_vert),
            contentDescription = null,
            colorFilter = ColorFilter.tint(iconButtonColor)
        )
    }
}

@Composable
private fun PlayerMoreMenuButton(
    mediaMetadata: MediaMetadata,
    navController: NavController,
    state: BottomSheetState,
    textButtonColor: Color,
    iconButtonColor: Color,
) {
    val menuState = LocalMenuState.current
    val bottomSheetPageState = LocalBottomSheetPageState.current

    Box(
        contentAlignment = Alignment.Center,
        modifier =
        Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(textButtonColor)
            .clickable {
                menuState.show {
                    PlayerMenu(
                        mediaMetadata = mediaMetadata,
                        navController = navController,
                        playerBottomSheetState = state,
                        onShowDetailsDialog = {
                            mediaMetadata.id.let {
                                bottomSheetPageState.show {
                                    ShowMediaInfo(it)
                                }
                            }
                        },
                        onDismiss = menuState::dismiss,
                    )
                }
            },
    ) {
        Image(
            painter = painterResource(R.drawable.more_horiz),
            contentDescription = null,
            colorFilter = ColorFilter.tint(iconButtonColor),
        )
    }
}

@Composable
private fun BackgroundVideoView(
    videoUrl: String,
    isPlaying: Boolean,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val appInForeground = rememberIsAppInForeground()
    var isVideoReady by remember(videoUrl) { mutableStateOf(false) }

    // E2: scale the canvas decode to the device tier — weaker phones cap resolution, skip forced max-bitrate
    // and use a smaller buffer, so the animated background doesn't push them into thermal/jank territory.
    // High-Performance Mode forces the LOW path even on MID/HIGH hardware (effectiveTier).
    val deviceTier = remember { iad1tya.echo.music.utils.PerformanceMode.effectiveTier(context) }
    val trackSelector = remember(deviceTier) {
        DefaultTrackSelector(context).apply {
            val maxDim = if (deviceTier == DeviceTier.LOW || deviceTier == DeviceTier.ULTRA) 1280 else 4096
            parameters = buildUponParameters()
                .setMaxVideoSize(maxDim, maxDim)
                .setForceHighestSupportedBitrate(deviceTier == DeviceTier.HIGH)
                .build()
        }
    }
    val canvasBufferBytes = when (deviceTier) {
        DeviceTier.ULTRA -> 2 * 1024 * 1024
        DeviceTier.LOW -> 4 * 1024 * 1024
        DeviceTier.MID -> 8 * 1024 * 1024
        DeviceTier.HIGH -> 16 * 1024 * 1024
    }

    val exoPlayer = remember {
        ExoPlayer.Builder(context)
            .setTrackSelector(trackSelector)
            .setLoadControl(
                DefaultLoadControl.Builder()
                    .setTargetBufferBytes(canvasBufferBytes)
                    .build()
            )
            .build().apply {
                repeatMode = Player.REPEAT_MODE_ONE
                volume = 0f
                videoScalingMode = C.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING
                playWhenReady = isPlaying
            }
    }

    val aspectRatioFrameLayout = remember {
        AspectRatioFrameLayout(context).apply {
            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
            setBackgroundColor(android.graphics.Color.TRANSPARENT)
        }
    }

    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onVideoSizeChanged(videoSize: androidx.media3.common.VideoSize) {
                if (videoSize.width > 0 && videoSize.height > 0) {
                    aspectRatioFrameLayout.setAspectRatio(videoSize.width.toFloat() / videoSize.height)
                }
            }
            override fun onRenderedFirstFrame() {
                isVideoReady = true
            }
        }
        exoPlayer.addListener(listener)
        onDispose { exoPlayer.removeListener(listener) }
    }

    LaunchedEffect(videoUrl) {
        isVideoReady = false
        val mediaItem = MediaItem.Builder()
            .setUri(videoUrl)
            .setMimeType(if (videoUrl.contains("m3u8")) MimeTypes.APPLICATION_M3U8 else MimeTypes.VIDEO_MP4)
            .build()
        exoPlayer.setMediaItem(mediaItem)
        exoPlayer.prepare()
    }

    LaunchedEffect(isPlaying, appInForeground) {
        // E1: pause the (invisible) animated background while the app isn't in the foreground — no video
        // decoding with the screen off / app backgrounded, so it stops heating the device. Audio is unaffected.
        exoPlayer.playWhenReady = isPlaying && appInForeground
    }

    DisposableEffect(Unit) {
        onDispose {
            exoPlayer.release()
        }
    }

    val alpha by animateFloatAsState(
        targetValue = if (isVideoReady) 1f else 0f,
        animationSpec = tween(800),
        label = "videoAlpha"
    )

    AndroidView(
        factory = { _ ->
            aspectRatioFrameLayout.apply {
                
                isEnabled = false
                isClickable = false
                isFocusable = false

                
                if (childCount == 0) {
                    val textureView = TextureView(context).apply {
                        layoutParams = ViewGroup.LayoutParams(MATCH_PARENT, MATCH_PARENT)
                    }
                    addView(textureView)
                    exoPlayer.setVideoTextureView(textureView)
                }
            }
        },
        modifier = modifier.alpha(alpha)
    )
}
