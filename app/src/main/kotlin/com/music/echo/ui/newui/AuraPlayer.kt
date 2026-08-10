package iad1tya.echo.music.ui.newui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitVerticalTouchSlopOrCancellation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.verticalDrag
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.input.pointer.util.addPointerInputChange
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.request.crossfade
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
import iad1tya.echo.music.constants.PlayerBackgroundStyle
import iad1tya.echo.music.constants.PlayerBackgroundStyleKey
import iad1tya.echo.music.constants.PlayerButtonsStyle
import iad1tya.echo.music.constants.QueuePeekHeight
import iad1tya.echo.music.constants.SafeVolumeEnabledKey
import iad1tya.echo.music.constants.ShowCodecOnPlayerKey
import iad1tya.echo.music.extensions.SwipeGesture
import iad1tya.echo.music.extensions.togglePlayPause
import iad1tya.echo.music.extensions.toggleRepeatMode
import iad1tya.echo.music.listentogether.RoomRole
import iad1tya.echo.music.models.rememberResolvedAlbum
import iad1tya.echo.music.playback.enqueueSongDownloads
import iad1tya.echo.music.playback.removeSongDownloads
import iad1tya.echo.music.ui.component.BottomSheet
import iad1tya.echo.music.ui.component.BottomSheetState
import iad1tya.echo.music.ui.component.CastButton
import iad1tya.echo.music.ui.component.ListDialog
import iad1tya.echo.music.ui.component.LocalBottomSheetPageState
import iad1tya.echo.music.ui.component.LocalMenuState
import iad1tya.echo.music.ui.component.PlayerProgressSlider
import iad1tya.echo.music.ui.component.rememberBottomSheetState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.clickable
import iad1tya.echo.music.ui.menu.AddToPlaylistDialog
import iad1tya.echo.music.ui.player.CanvasArtworkPlaybackCache
import iad1tya.echo.music.ui.player.rememberCanvasAnimationEnabled
import iad1tya.echo.music.ui.player.InlineLyricsView
import iad1tya.echo.music.ui.player.PlayerVideoSurface
import iad1tya.echo.music.ui.player.Thumbnail
import iad1tya.echo.music.ui.player.ThumbnailHost
import iad1tya.echo.music.ui.player.HideStatusBarOnFullscreenEffect
import iad1tya.echo.music.ui.player.KeepScreenOnWhilePlayerExpandedEffect
import iad1tya.echo.music.ui.player.rememberPlayerButtonColors
import iad1tya.echo.music.ui.player.rememberPlayerButtonsStyle
import iad1tya.echo.music.ui.player.rememberSwipeLyricsEnabled
import iad1tya.echo.music.ui.player.swipeLyricsGestureArmed
import iad1tya.echo.music.ui.player.swipeLyricsToChangeSong
import iad1tya.echo.music.ui.screens.equalizer.axion.AxionEqViewModel
import iad1tya.echo.music.ui.utils.ShowMediaInfo
import iad1tya.echo.music.ui.utils.ShowOffsetDialog
import iad1tya.echo.music.ui.utils.rememberIsTvOrCar
import iad1tya.echo.music.ui.utils.rememberIsWideLayout
import iad1tya.echo.music.ui.utils.tvFocusable
import iad1tya.echo.music.utils.DeviceCapabilities
import iad1tya.echo.music.utils.DeviceTier
import iad1tya.echo.music.utils.isLocalMediaId
import iad1tya.echo.music.utils.makeTimeString
import iad1tya.echo.music.utils.needsOnlineBrowseResolution
import iad1tya.echo.music.utils.rememberDeviceThrottle
import iad1tya.echo.music.utils.rememberEnumPreference
import iad1tya.echo.music.utils.rememberPreference
import iad1tya.echo.music.utils.resolveOnlineAlbumBrowseId
import iad1tya.echo.music.utils.resolveOnlineArtistBrowseId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.core.net.toUri
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import java.net.URLEncoder
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
 * ## NOTHING DELEGATES ANY MORE — the wide shape is here
 * This file used to hand `isLandscape || isWideLayout` straight to [BottomSheetPlayer]. That is the
 * complaint this round answers: *"al girar el móvil, el diseño nuevo desaparece"*. Rotating a phone
 * tore down this entire subtree and put the classic sheet — with the classic `MiniPlayer` and its
 * Liquid-Glass pill — on screen mid-song, and TV / car / tablet never saw the redesign at all.
 *
 * Video mode had already come back from that list for the same reason (a canvas dragged the classic
 * shape in during PORTRAIT playback). Now the rest follows.
 *
 * **There is exactly ONE body.** [AuraPlayerShape] reads every flow, every preference and every effect
 * once, declares the header / artwork / controls as three local content blocks, and then only chooses
 * how to ARRANGE them:
 *
 * | window | arrangement |
 * |---|---|
 * | vertical | one column — cabecera, portada, controles, barra del motor (byte for byte what ships) |
 * | ancho (`>= 600 dp`) y sin letra | three parts — COLA VIVA on the left ([AuraWideQueuePane]), then the now-playing pane: cabecera, portada, controles, barra del motor |
 * | ancho con la LETRA abierta, o apaisado bajo 600 dp | two panes — letra/portada on the left, cabecera + controles + barra del motor on the right |
 * | apaisado REAL + vídeo | fullscreen video with auto-hiding transport ([AuraImmersiveVideo], §2.8) |
 *
 * Because it is one body, rotating cannot restart, reseek or rebuild anything: no effect is keyed on
 * the orientation, the `BottomSheetState` is hoisted by the caller, and `showInlineLyrics` /
 * `isFullScreen` are `rememberSaveable`. Only the arrangement changes.
 *
 * ## Distinción `isLandscape` vs `isWideLayout` (deliberada, no colapsada)
 * They are different questions and the project keeps them apart on purpose (`ui/utils/TvUi.kt`):
 *  · `isWideLayout` is *"is this WINDOW at least 600 dp wide"* — REACTIVE, from the real current
 *    window, so folding a foldable or entering split-screen flips it live. A phone in LANDSCAPE is
 *    ~900 dp wide, so it is wide: that is not an accident, it is the same rule the classic wide player
 *    uses (Player.kt:3020), and it is why a rotated phone gets the queue column too.
 *  · `isLandscape` is only the orientation, and it is what routes VIDEO — the classic player is
 *    explicit that video must pick its path by real orientation and never by width (Player.kt:2747).
 *    It also keeps the two-pane shape alive for a landscape window UNDER 600 dp (split-screen,
 *    free-form), which is wide-ish but has no room for three parts.
 *  · Neither of them is [rememberIsTvOrCar], which is a question about the INPUT DEVICE (a remote).
 *    That one, and only that one, lights the D-pad focus ring.
 *
 * Width is not the whole layout question either: the now-playing pane checks its own HEIGHT
 * ([AURA_WIDE_COVER_MIN_PANE_HEIGHT]) before it spends any of it on a cover, because a landscape phone
 * and a landscape tablet are equally "wide" and one of them has half the height.
 *
 * ## TV y coche
 * Every interactive element in every arrangement carries [tvFocusable]; the queue column carries
 * `tvFocusRestorer().focusGroup()` and the now-playing pane `focusGroup()`, so the D-pad crosses
 * between the two panes cleanly and the ring survives a row scrolling out of composition. When the
 * player opens, focus is driven onto play/pause with the classic latch-and-retry. `tvFocusable` returns
 * its receiver unchanged off TV, so a phone pays nothing for any of it.
 *
 * ## Thermal / battery
 * No per-frame shader and no full-screen live blur. The ambient bloom is NOT the fixed brand wash any
 * more: [rememberAuraBloom] extracts it from the artwork, so there IS a palette extraction — bounded
 * to exactly one per TRACK. That cost is one 100×100 Coil request (the same request, and the same
 * memory-cache slot, the dynamic theme already uses, so it is usually a cache hit and no decode at
 * all) plus one [androidx.palette.graphics.Palette] pass on a background dispatcher, at most one in
 * flight per media id and memoised in [AuraBloomCache] afterwards. [AuraBloomColors.Brand] is now only
 * the fallback for a track whose cover yields nothing. The DRAW side is unchanged: three cached
 * radial gradients, no `Modifier.blur`, nothing recomputed per frame — including the ~1 s dissolve
 * between two tracks' blooms, which runs entirely in the draw phase.
 *
 * The collapsed mini player does blur one 128×128 cover per track ([AuraMiniPlayer]); that is a small
 * still image, not a backdrop sample. The position ticker is the classic one (500 ms, 1 s in
 * High-Performance Mode).
 */
/**
 * Height a wide player's now-playing pane needs before a cover is worth drawing in it.
 *
 * The pane has to fit, in order: the header (~48 dp), the dense controls block — title, artist, the
 * technical chips, the timeline, the timecodes, the transport and the quick-access row, ~300 dp at the
 * default font scale — the engine status bar (~46 dp), and only then a cover. 520 dp leaves ~125 dp for
 * the cover at the smallest window that clears the bar; every real tablet, TV and unfolded foldable is
 * far above it and gets a large one. A phone in landscape (~380 dp of usable height) is below it and
 * gets the controls whole instead of a cover squeezed to nothing.
 *
 * `internal` rather than private so it can be pinned by a test rather than restated.
 */
internal val AURA_WIDE_COVER_MIN_PANE_HEIGHT = 520.dp

/**
 * **WHICH SHAPE.** Pure, so the rule can be pinned by a test instead of restated in a comment — the same
 * discipline as [iad1tya.echo.music.ui.player.playerHoldsScreenOn] and
 * [iad1tya.echo.music.ui.player.swipeLyricsGestureArmed].
 *
 * Term for term the classic player's own rule (`useWidePlayer`, Player.kt:2757). Each term is one a
 * refactor can silently drop, and each drop is a shipped bug:
 *  · dropping [isLandscape] sends a rotated phone back to the portrait column — a 900×400 window drawing
 *    a portrait player, which is the shape of the original complaint in reverse;
 *  · dropping [isWideLayout] leaves a tablet or a TV (both often reporting PORTRAIT) on the phone shape;
 *  · dropping [videoSurfaceVisible] routes a WIDE PORTRAIT window with video into the landscape
 *    fullscreen video path, changing which surface lifecycle runs (registry #43).
 *
 * @param videoSurfaceVisible video mode is on AND a URL has resolved — the mode alone is not enough,
 *   because the artwork slot is still drawing its loading state in the window between the two.
 */
internal fun auraUsesWideShape(
    isLandscape: Boolean,
    isWideLayout: Boolean,
    videoSurfaceVisible: Boolean,
): Boolean = isLandscape || (isWideLayout && !videoSurfaceVisible)

/**
 * Whether the wide shape gets its THIRD part — the live queue column.
 *
 * Width buys it; the lyrics take it away, because a split-screen lyrics view needs the whole left pane
 * and not a third of it. Same condition as the classic wide player (Player.kt:3020).
 */
internal fun auraShowsQueueColumn(
    isWideLayout: Boolean,
    showInlineLyrics: Boolean,
): Boolean = isWideLayout && !showInlineLyrics

@Composable
fun AuraPlayer(
    state: BottomSheetState,
    navController: NavController,
    pureBlack: Boolean,
    modifier: Modifier = Modifier,
) {
    LocalPlayerConnection.current ?: return
    AuraPlayerShape(
        state = state,
        navController = navController,
        pureBlack = pureBlack,
        modifier = modifier,
    )
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
private fun AuraPlayerShape(
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
    val coroutineScope = rememberCoroutineScope()

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
    // "No me gusta" is now a button ON the player, not only a row inside the merged menu. Same flow the
    // menu reads (AuraPlayerMenu.kt:146) and the same flow the classic like/dislike pill reads
    // (Player.kt:2006) — one source of truth, so the surfaced button and the menu row can never disagree.
    val disliked by playerConnection.currentSongDisliked.collectAsState()
    val automix by playerConnection.service.automixItems.collectAsState()
    val currentFormatEntity by database.format(mediaMetadata?.id).collectAsState(initial = null)
    val download by LocalDownloadUtil.current.getDownload(mediaMetadata?.id ?: "")
        .collectAsState(initial = null)

    val listenTogetherManager = LocalListenTogetherManager.current
    val listenTogetherRoleState = listenTogetherManager?.role?.collectAsState(initial = RoomRole.NONE)
    val listenTogetherRole = listenTogetherRoleState?.value ?: RoomRole.NONE
    val isListenTogetherGuest = listenTogetherRole == RoomRole.GUEST

    // "Reproduciendo desde …". [Thumbnail] used to print this (plus the Listen Together banner) as its own
    // two-line header above the cover; that header is now CLASSIC-only (Thumbnail.kt), because on this shape
    // it ate ~60 dp of the artwork slot and squashed the cover into a rectangle. The FACTS it carried are not
    // lost — they move into the empty centre of this header, in the new UI's own type.
    val queueTitle by playerConnection.queueTitle.collectAsState()

    val castHandler = remember(playerConnection) {
        runCatching { playerConnection.service.castConnectionHandler }.getOrNull()
    }
    val isCasting by castHandler?.isCasting?.collectAsState() ?: remember { mutableStateOf(false) }
    val castPosition by castHandler?.castPosition?.collectAsState() ?: remember { mutableLongStateOf(0L) }
    val castDuration by castHandler?.castDuration?.collectAsState() ?: remember { mutableLongStateOf(0L) }
    val castIsPlaying by castHandler?.castIsPlaying?.collectAsState() ?: remember { mutableStateOf(false) }
    val effectiveIsPlaying = if (isCasting) castIsPlaying else isPlaying

    val highPerfMode by rememberPreference(HighPerformanceModeKey, false)
    // Only term of the CLASSIC video gate that is not already read here (Player.kt:1944). Read
    // unconditionally, like every other preference in this body, so the composable call order never shifts.
    // It is ALSO the D-pad gate: [tvFocusable] is a no-op (returns the receiver unchanged) while false,
    // so every ring below costs a phone exactly nothing.
    val isTvOrCar = rememberIsTvOrCar()

    // ── QUÉ FORMA ─────────────────────────────────────────────────────────────────────────────────
    // Read unconditionally and branch only at the ARRANGEMENT, never around a `remember` or an effect —
    // that is what makes rotation free of playback consequences. See the KDoc for why the two terms are
    // kept apart instead of collapsed into "not portrait".
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val isWideLayout = rememberIsWideLayout()

    // TV / coche: land the D-pad on play/pause when the player opens, the same latch-and-retry the
    // classic player uses (Player.kt:425) — Material3 gives a remote no initial focus, so without this
    // the ring has nothing to draw on and the player is unusable until the user guesses a direction.
    val playFocusRequester = remember { FocusRequester() }
    var transportFocusLanded by remember { mutableStateOf(false) }
    LaunchedEffect(state.isExpanded, isTvOrCar) {
        if (isTvOrCar && state.isExpanded) {
            transportFocusLanded = false
            repeat(40) {
                if (transportFocusLanded) return@LaunchedEffect
                runCatching { playFocusRequester.requestFocus() }
                delay(50)
            }
        }
    }

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

    // "Mantener la pantalla encendida cuando el reproductor está expandido" (Ajustes ▸ Reproductor).
    // The row is ungated and reachable with this UI on, and its only consumer was the classic sheet —
    // so in PORTRAIT the switch did nothing and only started working once the phone was rotated into
    // the classic shape. The CLASSIC mechanism is reused, not re-implemented: the window flag, the
    // predicate and the release-on-every-exit rule are the same [KeepScreenOnWhilePlayerExpandedEffect]
    // the classic player now calls (ui/player/PlayerAppearancePrefs.kt).
    //
    // `isPlaying` (the local player), not `effectiveIsPlaying`: while casting, playback is on the other
    // device and this screen has no reason to stay lit — which is also what the classic player does.
    // Independent of the video keep-screen-on further down: that one is a per-VIEW flag
    // (`View.keepScreenOn`), this one a WINDOW flag, so neither can clear the other's.
    KeepScreenOnWhilePlayerExpandedEffect(
        isExpanded = state.isExpanded,
        isPlaying = isPlaying,
        currentMediaId = mediaMetadata?.id,
    )

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
    // "Recortar las portadas" — read once here for the lyrics-mode cover below, the same key every
    // classic renderer reads (Thumbnail.kt:341, Items.kt:1371/1453/1546, MiniPlayer.kt:857).
    val cropAlbumArt by rememberPreference(iad1tya.echo.music.constants.CropAlbumArtKey, false)

    // "Ocultar la barra de estado en pantalla completa" — SAME effect the classic player runs
    // (PlayerAppearancePrefs.kt), so this shape's own fullscreen lyrics mode obeys the switch instead
    // of ignoring the insets controller entirely. `&& showInlineLyrics` because the flag is
    // rememberSaveable and only the lyrics header can set it: the switch's own description says "while
    // the fullscreen LYRICS mode is active", and the bar must come back when the lyrics close.
    HideStatusBarOnFullscreenEffect(isFullScreen = isFullScreen && showInlineLyrics)

    // `isFullScreen` is a LYRICS mode: only the lyrics header can set it, and the queue sheet (which now
    // owns the queue / audio-output / lyrics buttons) is not composed while it is on. Closing the lyrics
    // without clearing it therefore left a player with no queue bar and no way to bring it back — the exact
    // dead end de-duplicating the two rows would otherwise have created. One state, one meaning.
    LaunchedEffect(showInlineLyrics) {
        if (!showInlineLyrics) isFullScreen = false
    }

    // ── VÍDEO EN VERTICAL ─────────────────────────────────────────────────────────────────────────
    // This shape now owns portrait video instead of delegating it. Everything the delegation used to
    // provide is provided here, from the classic player, verbatim:
    //  · the surface itself — the SAME [PlayerVideoSurface] (one implementation, one TextureView
    //    contract), never a second copy;
    //  · the single-surface rule — the mini binds while the sheet is collapsed (in which case this
    //    expanded content is not composed at all) and MainActivity's overlay owns it in PiP;
    //  · keep-screen-on while a video is actually PLAYING (Player.kt:388);
    //  · closing the lyrics when video starts (Player.kt:907) — one surface at a time.
    val videoMode by playerConnection.videoMode.collectAsState()
    val videoUrl by playerConnection.videoUrl.collectAsState()
    val inPipMode = LocalIsInPipMode.current
    // Both terms, like the classic player's `onImmersiveVideo`: the mode can be on while the URL is
    // still resolving, and in that window [Thumbnail] already draws its own loading state.
    val showVideoSurface = videoMode && !videoUrl.isNullOrEmpty()

    // ── LA FORMA, resuelta ────────────────────────────────────────────────────────────────────────
    // Term for term the classic player's own rule (Player.kt:2757): `isRealLandscape || (isWideLayout
    // && !inVideoMode)`. The video carve-out is not cosmetic — a WIDE PORTRAIT window (an unfolded
    // foldable, a tablet held upright) with video on must stay on the portrait path, because that is
    // where this shape hosts the surface in its artwork slot; routing it into the landscape fullscreen
    // path would change which surface lifecycle runs for no gain (registry #43).
    val useWideShape = auraUsesWideShape(
        isLandscape = isLandscape,
        isWideLayout = isWideLayout,
        videoSurfaceVisible = showVideoSurface,
    )
    val immersiveVideo = useWideShape && showVideoSurface

    // Published by the fullscreen video so the pinned Cast button follows its tap-to-hide controls,
    // exactly as the classic player's `immersiveControlsVisible` does. Lives in the BODY, not in the
    // branch, so it is not a `remember` that appears and disappears with the orientation.
    var immersiveControlsVisible by remember { mutableStateOf(true) }
    // Hoisted for the same reason: the wide arrangements' controls column scrolls when the window is
    // too short, and its position must not be a state that only exists while rotated.
    val wideControlsScroll = rememberScrollState()

    val keepScreenOnView = LocalView.current
    DisposableEffect(videoMode, isPlaying) {
        keepScreenOnView.keepScreenOn = videoMode && isPlaying
        onDispose { keepScreenOnView.keepScreenOn = false }
    }
    LaunchedEffect(showVideoSurface) {
        if (showVideoSurface && showInlineLyrics) showInlineLyrics = false
    }


    // ── "Añadir a playlist" ───────────────────────────────────────────────────────────────────────
    // The SAME dialog the merged menu opens (AuraPlayerMenu.kt:174) with the SAME `onGetSong` body,
    // including the `withTransaction` — the song row must be committed before the dialog inserts the
    // ON DELETE CASCADE map row, or the map insert fails for a song that is not in the library yet.
    //
    // Declared in the BODY, next to the sleep-timer dialog, and never inside `controlsContent`: that
    // block is invoked from three different arrangements, so a `rememberSaveable` living inside it would
    // be destroyed and recreated by a rotation and the open dialog would vanish mid-gesture.
    var showChoosePlaylistDialog by rememberSaveable { mutableStateOf(false) }
    var showSelectArtistDialog by rememberSaveable { mutableStateOf(false) }
    if (showSelectArtistDialog) {
        val artists = mediaMetadata?.artists.orEmpty().filter { it.name.isNotBlank() }.distinctBy { it.id to it.name }
        ListDialog(onDismiss = { showSelectArtistDialog = false }) {
            items(items = artists, key = { "${it.id}_${it.name}" }) { artist ->
                Text(
                    text = artist.name,
                    style = AuraType.MenuLabel,
                    color = AuraPalette.OnGround,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            showSelectArtistDialog = false
                            coroutineScope.launch {
                                val browseId = if (needsOnlineBrowseResolution(artist.id)) {
                                    withContext(Dispatchers.IO) {
                                        resolveOnlineArtistBrowseId(artist.name)
                                    }
                                } else {
                                    artist.id
                                }
                                if (!browseId.isNullOrBlank()) {
                                    navController.navigate("artist/$browseId")
                                } else {
                                    navController.navigate(
                                        "search/${URLEncoder.encode(artist.name, "UTF-8")}"
                                    )
                                }
                                state.collapseSoft()
                            }
                        }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                )
            }
        }
    }
    AddToPlaylistDialog(
        isVisible = showChoosePlaylistDialog,
        onGetSong = {
            val meta = mediaMetadata
            if (meta == null) {
                emptyList()
            } else {
                database.withTransaction { insert(meta) }
                listOf(meta.id)
            }
        },
        onDismiss = { showChoosePlaylistDialog = false },
    )

    // ── The audio-output picker ───────────────────────────────────────────────────────────────────
    // NOT opened from here any more. It has exactly ONE owner in this shape: the queue bar
    // ([AuraQueueBar], AuraQueue.kt), which is on screen at the bottom of the player the whole time the
    // player is expanded. This player used to draw a second speaker button, ~40 dp above the first one,
    // wired to a second copy of the same [AudioDeviceBottomSheet] — "no quiero botones repetidos".

    // ── Queue sheet ───────────────────────────────────────────────────────────────────────────────
    val dismissedBound = QueuePeekHeight +
        WindowInsets.systemBars.asPaddingValues().calculateBottomPadding()
    val queueSheetState = rememberBottomSheetState(
        dismissedBound = dismissedBound,
        expandedBound = state.expandedBound,
        collapsedBound = dismissedBound + 1.dp,
        initialAnchor = 1,
    )

    // ── "Fondo del reproductor" (PlayerBackgroundStyleKey) ────────────────────────────────────────
    // The ground of this sheet, its queue sheet and the mini pill is the ONE layer the seven values own
    // — see [rememberAuraGround]. It used to be `auraScreenBackground(bloom)`, i.e. the DEFAULT recipe
    // hard-coded, which left six of the seven inert (APPLE_MUSIC among them, the value App.kt seeds on
    // every fresh install). Nothing above this layer reads the style.
    //
    // AMOLED needs no term here any more either: "Negro puro" moves [AuraPalette.Ground] itself
    // (AuraPalette.kt), so the sheet, the queue and every other new screen go black together instead of
    // the queue alone — which is what made dragging the queue up and down flip between two grounds in
    // one gesture.
    val ground = rememberAuraGround(mediaMetadata?.id, mediaMetadata?.thumbnailUrl)
    var audioSessionId by remember {
        mutableIntStateOf(playerConnection.player.audioSessionId)
    }
    DisposableEffect(playerConnection) {
        val player = playerConnection.player
        val listener = object : androidx.media3.common.Player.Listener {
            override fun onAudioSessionIdChanged(sessionId: Int) {
                audioSessionId = sessionId
            }
        }
        player.addListener(listener)
        audioSessionId = player.audioSessionId
        onDispose { player.removeListener(listener) }
    }
    val rhythmLevel by rememberAuraRhythmLevel(
        audioSessionId = audioSessionId,
        enabled = state.isExpanded && !highPerfMode && !isCasting,
        playing = effectiveIsPlaying,
    )
    // Rhythm boosts bloom/lobes/wash only — never buttons, cover chrome, or transport (owner:
    // animated ground by song rhythm across the player).
    val rhythmIntensity = 1f + (0.28f * rhythmLevel)

    BottomSheet(
        state = state,
        modifier = modifier,
        background = { AuraGroundLayer(ground, intensity = rhythmIntensity) },
        // DESTRUCTIVE GESTURE, PRESERVED VERBATIM: dragging the sheet below the dismiss threshold stops
        // playback and wipes the queue + automix. Identical to the classic player (Player.kt:1550).
        onDismiss = {
            playerConnection.service.clearAutomix()
            playerConnection.player.stop()
            playerConnection.player.clearMediaItems()
        },
        collapsedContent = {
            // Was the CLASSIC [MiniPlayer] verbatim — which is why Liquid Glass kept appearing inside
            // the new player: the classic mini reads MiniPlayerBackgroundStyleKey, and the one-time
            // high-tier migration in App.kt:810-822 had set that key to LIQUID_GLASS. [AuraMiniPlayer]
            // is the render's `.mi` pill and samples no glass at all. Same position/duration state,
            // same single-video-surface contract.
            AuraMiniPlayer(
                positionState = positionState,
                durationState = durationState,
                shouldBindVideoSurface = state.isCollapsed && !LocalIsInPipMode.current,
            )
        },
    ) {
        // ── LOS TRES BLOQUES DEL REPRODUCTOR, DECLARADOS UNA SOLA VEZ ─────────────────────────────
        // Cabecera, portada y controles are local content blocks, exactly as the classic player keeps
        // its `controlsContent` (Player.kt:1525). Every arrangement below CALLS them; none re-draws
        // them. That is what makes "el mismo reproductor, girado" true rather than a claim: there is no
        // landscape copy of the transport that can drift from the portrait one, and adding a control
        // adds it to every device class at once.
        //
        // They capture the state read above. They are NOT `remember`ed — a remembered composable lambda
        // would pin the captured values to the composition that created it.

        // GESTURE: swipe UP anywhere on the player body drags the queue sheet open. Mirrors the classic
        // implementation (Player.kt:3459) — only clearly-upward drags are claimed, so the parent sheet's
        // own downward collapse/dismiss still works. Hoisted to a val so BOTH arrangements carry it: it
        // used to live on the portrait Column, i.e. rotating lost it.
        val openQueueOnSwipeUp = Modifier.pointerInput(queueSheetState, state) {
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
        }

        // ── Cabecera ──────────────────────────────────────────────────────────────────────────────
        val headerContent: @Composable (Modifier) -> Unit = { headerModifier ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = headerModifier,
            ) {
                AuraIconButton(
                    icon = AuraIcons.ChevronDown,
                    contentDescription = "Minimizar el reproductor",
                    onClick = { state.collapseSoft() },
                    size = 22.dp,
                    tint = AuraPalette.OnGround.copy(alpha = 0.6f),
                    // D-pad: a remote cannot drag the sheet down, so this is the ONLY way off the player
                    // on a TV. It has to be reachable and it has to show the ring.
                    modifier = Modifier.tvFocusable(isTvOrCar, CircleShape),
                )
                // The centre of the header carries what [Thumbnail]'s own portrait header used to carry for
                // this shape — "Reproduciendo desde <álbum / cola>" and the Listen Together banner — plus the
                // hi-res badge. Two single lines of technical type instead of two lines of `titleMedium`
                // inside the artwork slot, so the cover gets the height back (see report 4).
                Column(
                    // Reserve space for pinned top-right Cast (~40dp) so long album titles never
                    // run under the icon. Cast stays TopEnd overlay (owner rule); hidden with
                    // inline lyrics so this spacer only matters when Cast can show.
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 4.dp, end = if (showInlineLyrics) 4.dp else 40.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    if (techInfo.isHiRes) {
                        AuraTechnicalText(text = "◆ HI-RES", color = AuraPalette.Teal)
                    }
                    // A live session outranks the queue title: it is state the user can act on.
                    val contextLine = when (listenTogetherRole) {
                        RoomRole.HOST -> "ESCUCHA COMPARTIDA · ANFITRIÓN"
                        RoomRole.GUEST -> "ESCUCHA COMPARTIDA"
                        else -> (mediaMetadata?.album?.title ?: queueTitle)?.takeIf { it.isNotBlank() }
                    }
                    if (contextLine != null) {
                        AuraTechnicalText(
                            text = contextLine,
                            color = if (listenTogetherRole != RoomRole.NONE) AuraPalette.Violet
                            else AuraPalette.OnGroundFaint,
                        )
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
                        modifier = Modifier.tvFocusable(isTvOrCar, CircleShape),
                    )
                    // Lyrics menu stays in the header; the player menu's door is the bottom-bar Más.
                    AuraIconButton(
                        icon = AuraIcons.More,
                        contentDescription = "Menú de la letra",
                        onClick = {
                            val meta = mediaMetadata ?: return@AuraIconButton
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
                        },
                        size = 22.dp,
                        tint = AuraPalette.OnGround.copy(alpha = 0.6f),
                        modifier = Modifier.tvFocusable(isTvOrCar, CircleShape),
                    )
                }
            }
        }

        // ── Portada / letra. THE ARTWORK AREA IS THE CLASSIC [Thumbnail] ──────────────────────────
        // Canvas is content, not decoration: [Thumbnail] is the composable that hosts the animated
        // canvas (CanvasArtworkPlayer), the cover carousel, the double-tap seek with its
        // accumulating multiplier, the centre double-tap play/pause, the seek overlay, the
        // rotating cover, the hidden-thumbnail placeholder and the playback-error retry. Rebuilding
        // it would have meant rebuilding ~55 gestures; reusing it keeps every one of them.
        //
        // `isLandscape` is handed to [Thumbnail] in the wide arrangements: it is that composable's own
        // parameter for "this slot is short and wide" and it changes the cover sizing AND drops the
        // status-bar padding it adds in portrait. The wide arrangement below passes it; portrait does
        // not, which is exactly what the classic player does (`isLandscape = true` at Player.kt:3061).
        val artworkContent: @Composable (Modifier) -> Unit = { artworkModifier ->
            Box(
                contentAlignment = Alignment.BottomStart,
                modifier = artworkModifier
                    // GESTURE, restored from the classic player (Player.kt:3336 portrait / :3051 and
                    // :3077 wide): while the LYRICS are full screen the artwork slot swipes to change
                    // song. The Aura shape never had it — in portrait either. It is armed by the same
                    // `isFullScreen` term the classic uses, and in that state this slot is drawing the
                    // lyrics, so it does not fight the cover carousel's own horizontal drag.
                    .SwipeGesture(
                        enabled = isFullScreen,
                        onSwipeRight = { if (!isListenTogetherGuest) playerConnection.seekToPrevious() },
                        onSwipeLeft = { if (!isListenTogetherGuest) playerConnection.seekToNext() },
                    ),
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
                                // "Recortar las portadas" (CropAlbumArtKey, default OFF), as every
                                // classic renderer does — Thumbnail.kt:341 is the counterpart of this
                                // very cover. Hard-coded Crop here was one of the three new-UI sites
                                // that ignored the switch.
                                contentScale = if (cropAlbumArt) ContentScale.Crop else ContentScale.Fit,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    }
                } else if (showVideoSurface) {
                    // The video takes the artwork slot and NOTHING else moves: the header, the title
                    // block, the whole new transport and the new mini stay exactly where they are, so
                    // turning video on no longer swaps the entire player (and with it, the glass).
                    // In PiP the fullscreen overlay in MainActivity owns the one surface — binding a
                    // second TextureView here is what used to freeze the picture on PiP exit.
                    // The way OUT of video is the SAME quick-access button that is the way in, a few dp
                    // below and lit teal — not a second copy pinned to this corner. Two toggles for one
                    // preference, both on screen at once, is the complaint this round is about.
                    if (!inPipMode) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            PlayerVideoSurface(
                                playerConnection = playerConnection,
                                modifier = Modifier.fillMaxWidth(),
                                videoUrl = videoUrl,
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
                        // [Thumbnail]'s own "this slot is short and wide" parameter — it centres the
                        // cover vertically, drops the portrait status-bar padding and re-runs its size
                        // maths for a landscape slot. Passed for exactly the shapes the classic player
                        // passes it for (Player.kt:3061 / :3099), so the cover is not a portrait square
                        // squashed sideways.
                        isLandscape = useWideShape,
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
                    // Canvas animates in Thumbnail; no on-screen "CANVAS EN MOVIMIENTO" badge (owner).
                }
            }
        }

        // ── Controles: título, datos técnicos, línea de tiempo, transporte y accesos rápidos ──────
        // ONE block for every device class. [dense] only shrinks: a landscape phone has ~360 dp of
        // height for the whole right column, so the same controls are drawn a size down rather than
        // dropped. Nothing is conditional on the shape except size — no control exists in one
        // arrangement and not another.
        val controlsContent: @Composable (Boolean) -> Unit = { dense ->
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
                    Spacer(Modifier.height(if (dense) 6.dp else 14.dp))
                    Text(
                        text = meta.title,
                        style = AuraType.PlayerTitle,
                        color = AuraPalette.OnGround,
                        maxLines = 1,
                        overflow = AuraDefaultOverflow,
                        modifier = Modifier
                            .fillMaxWidth()
                            .basicMarquee(iterations = 1, initialDelayMillis = 3000, velocity = 30.dp)
                            // D-pad: «ver álbum» is a real destination and on a remote the title IS the
                            // button. The ring sits ABOVE the clickable so it observes that focus stop.
                            .tvFocusable(isTvOrCar, RoundedCornerShape(8.dp))
                            .combinedClickable(
                                indication = null,
                                interactionSource = remember { MutableInteractionSource() },
                                role = Role.Button,
                                onClick = {
                                    resolvedAlbum?.let { album ->
                                        coroutineScope.launch {
                                            val browseId = if (needsOnlineBrowseResolution(album.id)) {
                                                val query = listOfNotNull(
                                                    album.title.takeIf { it.isNotBlank() },
                                                    meta.artists.joinToString(" ") { it.name }
                                                        .takeIf { it.isNotBlank() },
                                                ).joinToString(" ")
                                                withContext(Dispatchers.IO) {
                                                    resolveOnlineAlbumBrowseId(query)
                                                }
                                            } else {
                                                album.id
                                            }
                                            if (!browseId.isNullOrBlank()) {
                                                navController.navigate("album/$browseId")
                                                state.collapseSoft()
                                            } else if (album.title.isNotBlank()) {
                                                navController.navigate(
                                                    "search/${URLEncoder.encode(album.title, "UTF-8")}"
                                                )
                                                state.collapseSoft()
                                            }
                                        }
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
                                .tvFocusable(isTvOrCar, RoundedCornerShape(8.dp))
                                .combinedClickable(
                                    indication = null,
                                    interactionSource = remember { MutableInteractionSource() },
                                    role = Role.Button,
                                    onClick = {
                                        val named = meta.artists.filter { it.name.isNotBlank() }
                                        if (named.isEmpty()) return@combinedClickable
                                        if (named.size > 1) {
                                            showSelectArtistDialog = true
                                            return@combinedClickable
                                        }
                                        val artist = named.first()
                                        coroutineScope.launch {
                                            val browseId = if (needsOnlineBrowseResolution(artist.id)) {
                                                withContext(Dispatchers.IO) {
                                                    resolveOnlineArtistBrowseId(artist.name)
                                                }
                                            } else {
                                                artist.id
                                            }
                                            if (!browseId.isNullOrBlank()) {
                                                navController.navigate("artist/$browseId")
                                                state.collapseSoft()
                                            } else {
                                                navController.navigate(
                                                    "search/${URLEncoder.encode(artist.name, "UTF-8")}"
                                                )
                                                state.collapseSoft()
                                            }
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
                        Spacer(Modifier.height(if (dense) 6.dp else 10.dp))
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
                    Spacer(Modifier.height(if (dense) 6.dp else 14.dp))
                    PlayerProgressSlider(
                        // D-pad: Material's Slider shows no focus affordance on a remote, so without the
                        // ring a TV user cannot tell the timeline is selected. Same shape the classic
                        // player rings it with (Player.kt:2132). Once focused, D-pad left/right seeks.
                        modifier = Modifier.tvFocusable(isTvOrCar, RoundedCornerShape(12.dp)),
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
                Spacer(Modifier.height(if (dense) 6.dp else 12.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    AuraIconButton(
                        icon = AuraIcons.Shuffle,
                        contentDescription = stringResource(R.string.shuffle),
                        onClick = {
                            if (shuffleModeEnabled) {
                                // OFF (toggle). Reshuffle = turn on again → beginShuffleSession.
                                playerConnection.player.shuffleModeEnabled = false
                            } else {
                                // Ensure anti-repeat session even if media3 quirks skip the callback.
                                playerConnection.service.toggleShuffleOrReshuffle()
                            }
                        },
                        enabled = !isListenTogetherGuest,
                        size = if (dense) 22.dp else 24.dp,
                        tint = if (shuffleModeEnabled) transportAccent else AuraPalette.OnGroundFaint,
                        modifier = Modifier.tvFocusable(isTvOrCar, CircleShape),
                    )
                    AuraIconButton(
                        icon = AuraIcons.SkipPrevious,
                        contentDescription = stringResource(R.string.previous),
                        onClick = playerConnection::seekToPrevious,
                        enabled = canSkipPrevious && !isListenTogetherGuest,
                        size = if (dense) 30.dp else 34.dp,
                        tint = AuraPalette.OnGround.copy(alpha = 0.9f),
                        modifier = Modifier.tvFocusable(isTvOrCar, CircleShape),
                    )
                    Spacer(Modifier.width(6.dp))
                    AuraPlayButton(
                        diameter = if (dense) 58.dp else 70.dp,
                        // TV / coche: this is where the D-pad lands when the player opens. The latch is
                        // set from the button's OWN focus event rather than from a live `hasFocus`, so a
                        // user who D-pads away inside the retry window is not yanked back (the classic
                        // player's `transportFocusLanded`, Player.kt:430).
                        modifier = Modifier
                            .focusRequester(playFocusRequester)
                            .onFocusChanged { if (it.isFocused) transportFocusLanded = true }
                            .tvFocusable(isTvOrCar, CircleShape),
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
                        size = if (dense) 30.dp else 34.dp,
                        tint = AuraPalette.OnGround.copy(alpha = 0.9f),
                        modifier = Modifier.tvFocusable(isTvOrCar, CircleShape),
                    )
                    Box(contentAlignment = Alignment.Center) {
                        AuraIconButton(
                            icon = AuraIcons.Repeat,
                            contentDescription = stringResource(R.string.repeat),
                            onClick = { playerConnection.player.toggleRepeatMode() },
                            enabled = !isListenTogetherGuest,
                            size = if (dense) 22.dp else 24.dp,
                            tint = if (repeatMode != Player.REPEAT_MODE_OFF) transportAccent
                            else AuraPalette.OnGroundDisabled,
                            modifier = Modifier.tvFocusable(isTvOrCar, CircleShape),
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

                // ── Accesos rápidos ───────────────────────────────────────────────────────────────
                // DE-DUPLICATED BY OWNER. This row used to draw five buttons, three of which the queue
                // bar below it ([AuraQueueBar]) already draws with IDENTICAL wiring while the player is
                // expanded — speaker → the same [AudioDeviceBottomSheet], letra → the same
                // `showInlineLyrics` toggle (it is literally passed down as `onToggleLyrics`), cola → the
                // same `queueSheetState.expandSoft()`. Those three now live ONLY in the queue bar. What is
                // left here is what nothing else on this screen owns: like, descarga, búsqueda.
                //
                // FIVE: like, no me gusta, añadir a playlist, descargar, lupa. Biblioteca removed by
                // owner (still in [AuraPlayerMenu]); lupa is the more useful fifth slot.
                // Compartir / Ecualizador / Vídeo live in [AuraQueueBar]'s bottom row instead.
                Spacer(Modifier.height(if (dense) 2.dp else 6.dp))
                val liked = currentSong?.song?.liked == true
                val quickAccessGlyph = if (dense) 20.dp else 22.dp
                val isLocalTrack = meta.id.isLocalMediaId() || currentSong?.song?.isLocal == true
                Row(
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    AuraIconButton(
                        icon = if (liked) AuraIcons.HeartFilled else AuraIcons.Heart,
                        contentDescription = stringResource(R.string.action_like),
                        onClick = playerConnection::toggleLike,
                        size = quickAccessGlyph,
                        tint = if (liked) transportAccent else AuraPalette.OnGround.copy(alpha = 0.7f),
                        modifier = Modifier.tvFocusable(isTvOrCar, CircleShape),
                    )
                    // "No me gusta" — the SAME call the menu row makes, and the same live flow behind it.
                    //
                    // ACTIVE STATE: [AuraIcons] pairs `Heart` with a filled `HeartFilled`, but `ThumbDown`
                    // has no filled twin. Rather than introduce a second dislike silhouette — the classic
                    // pill swaps to Material's `Icons.Filled.ThumbDown`, which is a different drawing
                    // weight from this icon set — the "filled" state is the accent-filled disc this UI
                    // already uses for an active control ([AuraBarButton], AuraQueue.kt), plus the accent
                    // tint every other stateful button in this row uses. One glyph, two unmistakable
                    // states, and the glyph stays identical to the menu row this shortcuts.
                    AuraIconButton(
                        icon = AuraIcons.ThumbDown,
                        contentDescription = stringResource(R.string.action_dislike),
                        onClick = { playerConnection.toggleDislikeCurrentSong() },
                        size = quickAccessGlyph,
                        tint = if (disliked) transportAccent else AuraPalette.OnGround.copy(alpha = 0.7f),
                        modifier = Modifier
                            .tvFocusable(isTvOrCar, CircleShape)
                            .then(
                                if (disliked) Modifier.background(
                                    transportAccent.copy(alpha = 0.16f),
                                    CircleShape,
                                ) else Modifier,
                            ),
                    )
                    AuraIconButton(
                        icon = AuraIcons.PlaylistAdd,
                        contentDescription = stringResource(R.string.add_to_playlist),
                        onClick = { showChoosePlaylistDialog = true },
                        size = quickAccessGlyph,
                        tint = AuraPalette.OnGround.copy(alpha = 0.7f),
                        modifier = Modifier.tvFocusable(isTvOrCar, CircleShape),
                    )
                    // Local files are already on-device — hide download; SpaceEvenly keeps spacing even.
                    if (!isLocalTrack) {
                    Box(contentAlignment = Alignment.Center) {
                        AuraIconButton(
                            icon = if (download?.state == Download.STATE_COMPLETED) AuraIcons.Check
                            else AuraIcons.Download,
                            contentDescription = stringResource(R.string.action_download),
                            onClick = {
                                val isVideo = meta.isVideoSong
                                when (download?.state) {
                                    Download.STATE_COMPLETED, Download.STATE_QUEUED, Download.STATE_DOWNLOADING ->
                                        removeSongDownloads(context, meta.id, isVideo)
                                    else -> {
                                        database.transaction { insert(meta) }
                                        // Same race as like-while-watching: don't pull a companion
                                        // video download while this track is live in video mode.
                                        enqueueSongDownloads(
                                            context,
                                            meta.id,
                                            meta.title,
                                            isVideoSong = isVideo && !videoMode,
                                        )
                                    }
                                }
                            },
                            size = quickAccessGlyph,
                            tint = if (download?.state == Download.STATE_COMPLETED) transportAccent
                            else AuraPalette.OnGround.copy(alpha = 0.7f),
                            modifier = Modifier.tvFocusable(isTvOrCar, CircleShape),
                        )
                        if (download?.state == Download.STATE_QUEUED ||
                            download?.state == Download.STATE_DOWNLOADING
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(quickAccessGlyph),
                                strokeWidth = 2.dp,
                                color = AuraPalette.Teal,
                            )
                        }
                    }
                    }
                    AuraIconButton(
                        icon = AuraIcons.Search,
                        contentDescription = stringResource(R.string.search),
                        onClick = {
                            bottomSheetPageState.show {
                                AuraPlayerQuickSearchContent(
                                    navController = navController,
                                    onDismiss = bottomSheetPageState::dismiss,
                                    onBrowseAway = {
                                        bottomSheetPageState.dismiss()
                                        state.collapseSoft()
                                    },
                                    isListenTogetherGuest = isListenTogetherGuest,
                                )
                            }
                        },
                        size = quickAccessGlyph,
                        tint = AuraPalette.OnGround.copy(alpha = 0.7f),
                        modifier = Modifier.tvFocusable(isTvOrCar, CircleShape),
                    )
                }
            }
        }

        // ── LA DISPOSICIÓN ────────────────────────────────────────────────────────────────────────
        // The ONLY thing that changes with the window. Each branch calls the same three blocks above;
        // none of them re-declares a control, a gesture or a preference read.
        when {
            // 1) LANDSCAPE REAL + VÍDEO → pantalla completa (§2.8). Routed by the REAL orientation, not
            //    by width, for the same reason the classic player does it (Player.kt:2747): the video
            //    surface has two orientation-specific paths and the wide/width rule must not silently
            //    change which one runs. A WIDE PORTRAIT window with video therefore keeps the portrait
            //    shape and its in-slot surface.
            immersiveVideo -> {
                AuraImmersiveVideo(
                    // A Listen Together GUEST does not pause the room — the button mutes, and it must
                    // SAY so and SHOW the mute state, exactly like the main transport a few hundred
                    // lines up. Passing `effectiveIsPlaying` here would have drawn a pause glyph on a
                    // button that mutes: a control that lies about what it does.
                    isPlaying = if (isListenTogetherGuest) !isMuted else effectiveIsPlaying,
                    playPauseDescription = when {
                        isListenTogetherGuest ->
                            if (isMuted) stringResource(R.string.unmute) else stringResource(R.string.mute)
                        effectiveIsPlaying -> stringResource(R.string.pause)
                        else -> stringResource(R.string.play)
                    },
                    canSkipPrevious = canSkipPrevious && !isListenTogetherGuest,
                    canSkipNext = canSkipNext && !isListenTogetherGuest,
                    isTvOrCar = isTvOrCar,
                    inPipMode = inPipMode,
                    title = mediaMetadata?.title,
                    artist = mediaMetadata?.artists
                        ?.filter { it.name.isNotBlank() }
                        ?.joinToString(", ") { it.name }
                        ?.takeIf { it.isNotBlank() },
                    playButtonFill = playButtonFill,
                    playButtonInk = playButtonInk,
                    onPrevious = playerConnection::seekToPrevious,
                    onNext = playerConnection::seekToNext,
                    onPlayPause = {
                        when {
                            isListenTogetherGuest -> playerConnection.toggleMute()
                            isCasting -> if (castIsPlaying) castHandler?.pause() else castHandler?.play()
                            else -> playerConnection.togglePlayPause()
                        }
                    },
                    onExitVideo = { playerConnection.toggleVideoMode() },
                    onControlsVisibilityChanged = { immersiveControlsVisible = it },
                )
            }

            // 2) ANCHO / APAISADO → dos o tres partes.
            //
            //    `isWideLayout` (>= 600 dp of WINDOW width) buys the third part: the live queue column.
            //    A phone in landscape is ~900 dp wide and therefore IS wide — that is deliberate and is
            //    what the classic player does too (Player.kt:3020). The narrower case that survives is a
            //    landscape window under 600 dp (split-screen, free-form): portada a la izquierda,
            //    controles a la derecha, sin cola — there is no room for three.
            //
            //    Con la LETRA abierta the queue column stands down in both, exactly as classic does, so
            //    the lyrics get the full left pane instead of a third of it.
            useWideShape -> {
                // BoxWithConstraints, not a guess: WIDTH buys the queue column, but only HEIGHT can pay
                // for it, and width alone cannot tell the two cases apart — a phone in landscape and a
                // tablet in landscape are equally "wide" and one of them has half the height. One
                // subcomposition per player composition; nothing per frame.
                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxSize()
                        .windowInsetsPadding(
                            WindowInsets.systemBars.only(
                                WindowInsetsSides.Horizontal + WindowInsetsSides.Top,
                            ),
                        )
                        // The collapsed queue bar is drawn over this on exactly the same condition as in
                        // portrait, and its height is sheet-derived — pad by the sheet's own bound or the
                        // bottom of the layout sits under the bar (the classic bug fixed at
                        // Player.kt:2994). Applied BEFORE the constraints are read, so `maxHeight` below
                        // is the height the panes really get.
                        .padding(bottom = queueSheetState.collapsedBound)
                        .then(openQueueOnSwipeUp),
                ) {
                    // ── EL ÚNICO CANJE DE ESTA FORMA, DICHO EN VOZ ALTA ───────────────────────────
                    // Three parts need ~520 dp of height ([AURA_WIDE_COVER_MIN_PANE_HEIGHT]). A tablet,
                    // a TV, a car head unit and an unfolded foldable all clear that. A PHONE in
                    // landscape does not: ~380 dp once the system bars and the queue bar are paid for.
                    //
                    // The classic wide player runs three parts there anyway, and the arithmetic makes its
                    // cover — `weight(1f, fill = false)`, Player.kt:3048 — resolve to nothing. So on a
                    // rotated phone the classic shape shows a queue column, NO cover at all, and a
                    // transport pushed past the bottom edge. A 0 dp cover is not "keeping the cover":
                    // [Thumbnail] is where the cover carousel, the double-tap seek and its multiplier,
                    // the canvas, the rotating cover, the hidden-thumbnail placeholder and the
                    // playback-error retry live, and none of them survive a slot with no height.
                    //
                    // So a short wide window keeps the ARTWORK and stands the queue column down. Nothing
                    // becomes unreachable: the queue bar under the player opens the full queue sheet,
                    // which carries strictly more than the column does (reordenar, deslizar para quitar,
                    // selección múltiple, las pestañas LETRA y RELACIONADOS). This is the one place this
                    // shape deliberately differs from the classic wide player.
                    val showQueuePane = auraShowsQueueColumn(isWideLayout, showInlineLyrics) &&
                        maxHeight >= AURA_WIDE_COVER_MIN_PANE_HEIGHT
                    Row(modifier = Modifier.fillMaxSize()) {
                        if (showQueuePane) {
                            AuraWideQueuePane(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .padding(start = AuraSpacing.Gutter, end = 10.dp),
                            )
                        } else {
                            artworkContent(
                                Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .padding(start = AuraSpacing.Gutter, end = 8.dp),
                            )
                        }

                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .weight(if (showQueuePane) 1.7f else 1f)
                                .fillMaxHeight()
                                // TV / coche: group the now-playing pane so D-pad directional search
                                // moves cleanly between it and the queue column instead of jumping
                                // across the two panes.
                                .focusGroup(),
                        ) {
                            headerContent(Modifier.fillMaxWidth().padding(horizontal = 8.dp))
                            if (showQueuePane) {
                                // Cover ON TOP of the controls — the balanced now-playing pane, not a
                                // huge cover beside a thin column jammed against the screen edge. The
                                // height guard above is what makes this `weight` safe.
                                artworkContent(
                                    Modifier
                                        .weight(1f)
                                        .fillMaxWidth()
                                        .padding(horizontal = AuraSpacing.Gutter),
                                )
                                Spacer(Modifier.height(6.dp))
                            } else {
                                Spacer(Modifier.weight(1f))
                            }
                            // The controls + engine bar scroll TOGETHER rather than clip. On a window
                            // tall enough for all of it the scroll never engages and this measures
                            // exactly like a plain Column; on a short one the engine bar is one flick
                            // away instead of cut off the bottom edge.
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.fillMaxWidth().verticalScroll(wideControlsScroll),
                            ) {
                                controlsContent(true)
                                Spacer(Modifier.height(6.dp))
                                AuraEngineStatusBar()
                            }
                            if (!showQueuePane) Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }

            // 3) VERTICAL — la forma que ya se envía, sin un solo cambio de estructura.
            else -> {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .fillMaxSize()
                        .windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Horizontal))
                        .padding(bottom = queueSheetState.collapsedBound)
                        .then(openQueueOnSwipeUp),
                ) {
                    headerContent(
                        Modifier
                            .fillMaxWidth()
                            .windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Top))
                            .padding(horizontal = 8.dp),
                    )
                    artworkContent(Modifier.weight(1f).fillMaxWidth())
                    controlsContent(false)
                    // ── Barra de estado del motor ─────────────────────────────────────────────────
                    Spacer(Modifier.height(8.dp))
                    AuraEngineStatusBar()
                }
            }
        }

        // CAST: pinned top-right (owner). Hidden while inline lyrics are open — the lyrics pane owns
        // that corner; Cast must not float over the text. Also gated on immersiveControlsVisible so
        // fullscreen video tap-to-hide stays a clean view.
        if (!LocalIsInPipMode.current &&
            !queueSheetState.isExpanded &&
            immersiveControlsVisible &&
            !showInlineLyrics
        ) {
            CastButton(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .windowInsetsPadding(
                        WindowInsets.systemBars.only(WindowInsetsSides.Top + WindowInsetsSides.End)
                    )
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .size(22.dp),
                tintColor = AuraPalette.OnGround,
            )
        }

        // `!immersiveVideo` as well as `!isFullScreen`: the collapsed queue bar would otherwise sit on
        // top of the fullscreen video's own bottom transport — the same reason the classic player hides
        // it there (Player.kt:3401). In PORTRAIT video the bar deliberately stays, because that shape
        // keeps the whole player and only swaps the artwork slot.
        if (!isFullScreen && !immersiveVideo) {
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
                // The queue sheet is part of "the player surface", so it takes the same style — it was
                // being handed the parameter's DEFAULT while the player drew something else.
                playerBackground = ground.style,
                onToggleLyrics = { showInlineLyrics = !showInlineLyrics },
                onMore = {
                    menuState.show {
                        PlayerMenuHost(
                            mediaMetadata = mediaMetadata,
                            navController = navController,
                            playerBottomSheetState = state,
                            onShowDetailsDialog = {
                                mediaMetadata?.id?.let { id -> bottomSheetPageState.show { ShowMediaInfo(id) } }
                            },
                            onDismiss = menuState::dismiss,
                        )
                    }
                },
            )
        }
    }
}

// ── VÍDEO A PANTALLA COMPLETA (APAISADO) ──────────────────────────────────────────────────────────

/**
 * §2.8 of the inventory — *"Reproductor > vídeo a pantalla completa (apaisado)"* — in the redesign's
 * language, and the reason rotating a video no longer swaps in the classic sheet.
 *
 * ## Why this exists rather than "just show the wide layout with a video in the cover slot"
 * Landscape is where users actually watch. The classic shape hides the system bars, crops the picture
 * edge to edge, and reduces to four controls that get out of the way — anything else is a video with a
 * player pasted on top of it. This reproduces that behaviour term for term (Player.kt:2761-2900):
 *
 *  · **`fillCrop = true`** on the SAME [PlayerVideoSurface] — one implementation, one TextureView
 *    contract. Video in this app is a DEDICATED ExoPlayer and nothing here touches the main one; the
 *    only call it makes about video is [onExitVideo], which flips `videoMode`.
 *  · **The single-surface rule.** In PiP the top-level overlay in `MainActivity` owns the surface, so
 *    this composable binds nothing — attaching a second TextureView is what used to freeze the picture
 *    on PiP exit (registry #43).
 *  · **System bars hidden and always restored.** Deliberately NOT gated on "Ocultar la barra de estado
 *    en pantalla completa": that switch scopes itself to the fullscreen LYRICS, and the classic video
 *    branch does not read it either. `onDispose` shows them unconditionally — a user left without a
 *    status bar cannot recover without killing the app.
 *  · **Tap toggles the controls; they auto-hide after 3.5 s.** The timer is NOT keyed on `isPlaying`,
 *    because a rebuffering HD video would otherwise restart it forever and they would never hide.
 *
 * ## TV y coche
 * A remote cannot tap hidden controls back, so on [isTvOrCar] they **never** auto-hide, ANY D-pad key
 * re-shows them (the handler returns `false`, so the key still performs its normal focus navigation),
 * and focus is driven onto play/pause on entry. Every button carries the ring.
 *
 * ## Thermal / battery
 * Nothing per frame: one surface, one spring fade, one 3.5 s coroutine that ends. No blur, no ambient
 * backdrop — the picture covers the screen, so there is nothing to paint behind it.
 */
@Composable
private fun AuraImmersiveVideo(
    isPlaying: Boolean,
    playPauseDescription: String,
    canSkipPrevious: Boolean,
    canSkipNext: Boolean,
    isTvOrCar: Boolean,
    inPipMode: Boolean,
    title: String?,
    artist: String?,
    playButtonFill: Brush,
    playButtonInk: Color,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onPlayPause: () -> Unit,
    onExitVideo: () -> Unit,
    onControlsVisibilityChanged: (Boolean) -> Unit,
) {
    val playerConnection = LocalPlayerConnection.current ?: return
    val videoUrl by playerConnection.videoUrl.collectAsState()
    val view = LocalView.current

    DisposableEffect(Unit) {
        val window = (view.context as? android.app.Activity)?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        runCatching {
            controller?.hide(WindowInsetsCompat.Type.systemBars())
            controller?.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        onDispose { runCatching { controller?.show(WindowInsetsCompat.Type.systemBars()) } }
    }

    var controlsVisible by remember { mutableStateOf(true) }
    val publishVisibility by rememberUpdatedState(onControlsVisibilityChanged)
    LaunchedEffect(controlsVisible) { publishVisibility(controlsVisible) }
    DisposableEffect(Unit) { onDispose { publishVisibility(true) } }

    LaunchedEffect(controlsVisible, inPipMode, isTvOrCar) {
        if (controlsVisible && !inPipMode && !isTvOrCar) {
            delay(3500)
            controlsVisible = false
        }
    }

    val playFocus = remember { FocusRequester() }
    LaunchedEffect(isTvOrCar, controlsVisible) {
        if (isTvOrCar && controlsVisible) {
            repeat(10) {
                runCatching { playFocus.requestFocus() }
                delay(50)
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .onPreviewKeyEvent {
                if (isTvOrCar && it.type == KeyEventType.KeyDown) controlsVisible = true
                false
            }
            .pointerInput(inPipMode) {
                detectTapGestures { if (!inPipMode) controlsVisible = !controlsVisible }
            },
    ) {
        if (!inPipMode) {
            PlayerVideoSurface(
                playerConnection = playerConnection,
                modifier = Modifier.fillMaxSize(),
                fillCrop = true,
                videoUrl = videoUrl,
            )
            // The fullscreen TextureView can swallow taps, so a transparent layer over it keeps
            // tap-to-toggle reliable. Same device as the classic branch (Player.kt:2821).
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .pointerInput(Unit) { detectTapGestures { controlsVisible = !controlsVisible } },
            )
        }

        if ((inPipMode || controlsVisible) && title != null) {
            Column(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.35f))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                Text(
                    text = title,
                    style = AuraType.RowTitle,
                    color = Color.White,
                    maxLines = 1,
                    overflow = AuraDefaultOverflow,
                )
                if (artist != null) {
                    Text(
                        text = artist,
                        style = AuraType.RowSubtitle,
                        color = Color.White.copy(alpha = 0.85f),
                        maxLines = 1,
                        overflow = AuraDefaultOverflow,
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = controlsVisible && !inPipMode,
            enter = fadeIn(animationSpec = AuraMotion.float),
            exit = fadeOut(animationSpec = AuraMotion.float),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth(),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(18.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.35f))
                    .windowInsetsPadding(WindowInsets.systemBars)
                    .padding(horizontal = 24.dp, vertical = 12.dp)
                    // Absorb taps that land in the control band (and its gaps) so a near-miss on a
                    // button does not fall through to the video and hide the controls.
                    .pointerInput(Unit) { detectTapGestures { } },
            ) {
                AuraIconButton(
                    icon = AuraIcons.SkipPrevious,
                    contentDescription = stringResource(R.string.previous),
                    onClick = onPrevious,
                    enabled = canSkipPrevious,
                    size = 32.dp,
                    tint = Color.White,
                    modifier = Modifier.tvFocusable(isTvOrCar, CircleShape),
                )
                AuraPlayButton(
                    isPlaying = isPlaying,
                    contentDescription = playPauseDescription,
                    onClick = onPlayPause,
                    diameter = 56.dp,
                    fill = playButtonFill,
                    ink = playButtonInk,
                    modifier = Modifier
                        .focusRequester(playFocus)
                        .tvFocusable(isTvOrCar, CircleShape),
                )
                AuraIconButton(
                    icon = AuraIcons.SkipNext,
                    contentDescription = stringResource(R.string.next),
                    onClick = onNext,
                    enabled = canSkipNext,
                    size = 32.dp,
                    tint = Color.White,
                    modifier = Modifier.tvFocusable(isTvOrCar, CircleShape),
                )
                // The way OUT of video. Names the DESTINATION — "Música" — exactly like the classic
                // affordance, whose glyph is literally `music_note` while video is on.
                AuraIconButton(
                    icon = AuraIcons.Video,
                    contentDescription = stringResource(R.string.music),
                    onClick = onExitVideo,
                    size = 26.dp,
                    tint = AuraPalette.Teal,
                    modifier = Modifier.tvFocusable(isTvOrCar, CircleShape),
                )
            }
        }
    }
}

// ── EL FONDO DEL REPRODUCTOR ES TUYO ──────────────────────────────────────────────────────────────

/**
 * "Fondo del reproductor" ([PlayerBackgroundStyleKey]), the SEVEN values, made real in this shape.
 *
 * ## What was wrong
 * `AuraPortraitPlayer` hard-coded its ground (`auraScreenBackground(bloom)`), `AuraQueue` took a
 * `playerBackground` parameter and carried `@Suppress("UNUSED_PARAMETER")` on it, and the Apariencia row
 * carried a subtitle saying the new portrait player "keeps its own ambient background". Six of the seven
 * values did nothing — including [PlayerBackgroundStyle.APPLE_MUSIC], which `App.kt:604` SEEDS on every
 * fresh install, so the shipped default value of the setting was one the new player did not honour.
 *
 * ## The rule these implement
 * **El fondo del reproductor es tuyo; lo que va encima es de Aura.** The seven values own exactly ONE
 * layer — the ground of the player surface (this sheet, its queue sheet and the mini pill). The header,
 * the `Thumbnail(host = OPAQUE_DARK)` artwork slot, the title block, the transport, the quick-access row
 * and the engine bar are untouched by every one of them.
 *
 * ## The contrast budget, and why the styles are tints rather than full-bleed covers
 * Because nothing above the ground moves, the redesign's ink does not move either: the player draws text
 * at [AuraPalette.OnGroundMuted] (55 %), [AuraPalette.OnGroundFaint] (50 %) and, in the queue,
 * [AuraPalette.OnGroundGhost] (48 %) — steps calibrated against `#060A12`. The classic player solves this
 * by switching its text to full white whenever the ground becomes artwork; that is exactly the "layer
 * above" this rule forbids. So the ground carries a budget instead:
 *
 *  · **Flat (uniform) artwork — the blurred cover — is capped at [AURA_COVER_ALPHA] = 10 %.** That is the
 *    same alpha the render fills [AuraPalette.SurfaceLine] with, so the brightest imaginable cover (a
 *    uniformly white sleeve) can lift the ground no further than `SurfaceLine` over `Ground` — which is
 *    literally `colorScheme.surfaceContainerHigh` in this app's own theme (ui/theme/Theme.kt), a surface
 *    it already prints text on. Measured at that worst case the 55 % step reads 5.2:1, the 50 % step
 *    4.6:1 and the 48 % ghost step 4.3:1 (it is 4.55:1 on the bare ground). Every real cover is darker
 *    than white, so every real case sits between those and today's values.
 *  · **Localised artwork — gradients that fade to transparent — follows the ambient bloom's own
 *    0.24–0.32 ceiling**, because that is the level the shipped [PlayerBackgroundStyle.DEFAULT] ground
 *    has always drawn at and the design it was signed off on.
 *
 * ## The thermal budget
 *  · The artwork COLOURS are free: they are the ones [AuraBloomCache] already extracts once per track
 *    for the bloom. No style adds a decode, a `Palette` pass or a cache.
 *  · The blurred cover is the cheap glass this app already ships (`MiniPlayer.kt`,
 *    `PlayerBackgroundStyle.BLUR`): ONE 128×128 decode per track under `Modifier.blur`, guarded by
 *    `Build.VERSION.SDK_INT >= S`. It is a small still image in a cached layer, never a backdrop sample.
 *  · The two moving styles read their phase INSIDE a draw lambda over brushes built in
 *    `drawWithCache` — draw invalidation only, no recomposition and no per-frame allocation — and they
 *    stop dead under Performance Mode, thermal throttling or a LOW hardware tier, exactly like the
 *    classic player's own backgrounds (Player.kt:1015).
 *  · A sheet's ground slot is not composed while the sheet is collapsed (`BottomSheet.kt`), so the
 *    motion only runs while the player is actually open.
 */
@Immutable
internal data class AuraGroundRecipe(
    /** Multiplier on the ambient bloom, 0 = no bloom. */
    val bloom: Float = 0f,
    /** Blurred-cover alpha as a fraction of [AURA_COVER_ALPHA]; 0 = no cover. */
    val cover: Float = 0f,
    val coverBlur: Dp = 34.dp,
    val coverSaturation: Float = 1f,
    val coverScale: Float = 1f,
    /** Slow rotation of the (already blurred, already cached) cover layer. */
    val spin: Boolean = false,
    /** Vertical artwork gradient, alpha at the TOP edge; fades to nothing. 0 = none. */
    val wash: Float = 0f,
    /** Two artwork lobes. [drift] moves them; false draws the same two, still. */
    val lobes: Float = 0f,
    val drift: Boolean = false,
    /** The frosted film + top hairline of the glass look. Shares the flat budget with [cover]. */
    val film: Float = 0f,
)

/**
 * The flat white-equivalent ceiling any style may add to the ground. See the KDoc above.
 *
 * `internal` so `AuraPlayerGroundTest` can pin the claim rather than restate the number.
 */
internal const val AURA_COVER_ALPHA = 0.10f

/** Everything the ground layers need, resolved once per (style, track, thermal state). */
@Stable
internal class AuraGround internal constructor(
    val style: PlayerBackgroundStyle,
    internal val recipe: AuraGroundRecipe,
    internal val bloom: AuraBloomState,
    internal val artwork: AuraBloomColors,
    internal val coverUrl: String?,
)

/**
 * Resolves the ground for the current track. Call ONCE per surface; it already contains
 * [rememberAuraBloom], so a caller must not resolve the bloom a second time.
 *
 * @param styleOverride the style the CALLER has already decided on, for two different reasons. The queue
 *   sheet passes what the player handed it, so the two halves of the player surface can never read the
 *   preference at two different instants and disagree. The mini pill passes its OWN key
 *   ([iad1tya.echo.music.constants.MiniPlayerBackgroundStyleKey], AuraShell.kt) — a separate control from
 *   the player's, exactly as in the classic app — so for the pill this is not "the same value, resolved
 *   once" but "a different value entirely". Null re-reads [PlayerBackgroundStyleKey], which is what the
 *   player itself does. Everything downstream (the local-media pin, the API-31 cover gate, the thermal
 *   gate) applies identically whichever way the style arrived.
 */
@Composable
internal fun rememberAuraGround(
    mediaId: String?,
    thumbnailUrl: String?,
    styleOverride: PlayerBackgroundStyle? = null,
): AuraGround {
    val bloom = rememberAuraBloom(mediaId)
    val context = LocalContext.current

    // Read unconditionally, decide afterwards — the same discipline as the rest of this file.
    val storedStyle by rememberEnumPreference(
        key = PlayerBackgroundStyleKey,
        // The classic player's default for the same key (Player.kt:341). One key, one default.
        defaultValue = PlayerBackgroundStyle.GRADIENT,
    )
    val stylePref = styleOverride ?: storedStyle
    val highPerfMode by rememberPreference(HighPerformanceModeKey, false)
    val deviceThrottle = rememberDeviceThrottle()
    val rawTierLow = remember {
        DeviceCapabilities.tier(context) == DeviceTier.LOW
    }

    // A local file has no remote cover to extract from, and the classic player pins it to DEFAULT for
    // exactly that reason (Player.kt:344). Same rule, so the two shapes agree on what a local track gets.
    val isLocalMedia = mediaId?.isLocalMediaId() == true
    val style = if (isLocalMedia) PlayerBackgroundStyle.DEFAULT else stylePref

    // `Modifier.blur` is a no-op below API 31 — drawing an UNBLURRED cover there would be a different
    // style, not a degraded one, so the cover is dropped and the recipe substitutes a colour wash.
    val coverUrl = thumbnailUrl
        ?.takeIf { it.isNotEmpty() && !isLocalMedia && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S }

    val motion = !(highPerfMode || deviceThrottle || rawTierLow)
    val recipe = remember(style, coverUrl != null, motion) {
        auraGroundRecipe(style, hasCover = coverUrl != null, motion = motion)
    }
    val artwork = AuraBloomCache.get(mediaId)
    return remember(style, recipe, bloom, artwork, coverUrl) {
        AuraGround(style, recipe, bloom, artwork, coverUrl)
    }
}

/**
 * What each of the seven draws.
 *
 * `hasCover` false (a local track, no artwork yet, or API < 31) swaps a cover for a colour wash, so a
 * style is never silently the same as DEFAULT — it keeps its own shape, drawn from the artwork colours
 * the bloom cache already holds.
 */
internal fun auraGroundRecipe(
    style: PlayerBackgroundStyle,
    hasCover: Boolean,
    motion: Boolean,
): AuraGroundRecipe = when (style) {
    // The redesign's own ambient bloom — three soft lobes in the top band. Unchanged, and now named:
    // it is what "Predeterminado / Seguir el tema" gives you.
    PlayerBackgroundStyle.DEFAULT -> AuraGroundRecipe(bloom = 1f)

    // A coloured sky: one vertical gradient from the artwork's dominant hue at the top edge down to the
    // bare ground. No cover, no bloom, no motion — the cheapest of the seven (one cached brush).
    PlayerBackgroundStyle.GRADIENT -> AuraGroundRecipe(wash = 0.32f)

    // The classic "Desenfoque": the cover itself, blurred, plus a half-strength bloom so the colour
    // still reads at the 10 % flat ceiling. Without a cover it degrades to a wash + that same bloom.
    PlayerBackgroundStyle.BLUR ->
        if (hasCover) AuraGroundRecipe(cover = 1f, coverBlur = 34.dp, bloom = 0.45f)
        else AuraGroundRecipe(wash = 0.24f, bloom = 0.45f)

    // Two artwork lobes that drift across the sheet on a ~28 s cycle. The one style whose identity IS
    // the movement, so when motion is denied it draws the same two lobes standing still rather than
    // pretending: a still glow is honest, a still glow labelled "animado" while claiming to move is not.
    PlayerBackgroundStyle.GLOW_ANIMATED -> AuraGroundRecipe(lobes = 0.30f, drift = motion)

    // The seeded default of a fresh install, and the richest of the seven: the blurred cover, a strong
    // top wash and the full bloom. Bright and colour-forward at the top, clean ground at the bottom —
    // the shape the classic APPLE_MUSIC background draws.
    PlayerBackgroundStyle.APPLE_MUSIC ->
        if (hasCover) AuraGroundRecipe(cover = 0.7f, coverBlur = 46.dp, wash = 0.26f, bloom = 1f)
        else AuraGroundRecipe(wash = 0.30f, bloom = 1f)

    // The classic mesh: the cover over-saturated (×1.6, the classic's own constant), scaled past the
    // edges and turned very slowly, so the colour field keeps moving without any shape being legible.
    PlayerBackgroundStyle.LIVE_MESH ->
        if (hasCover) AuraGroundRecipe(
            cover = 0.9f, coverBlur = 40.dp, coverSaturation = 1.6f, coverScale = 1.5f,
            spin = motion, bloom = 0.5f,
        )
        else AuraGroundRecipe(lobes = 0.26f, drift = motion, bloom = 0.5f)

    // Frosted, NOT backdrop-sampled. The glass engine has no renderer under this flag (its Apariencia
    // row is SHOWN but disabled, saying so — AppearanceSettings.kt:1315) and a live full-screen sample is
    // the one thing the thermal contract rules out outright — so this is the same still, blurred cover
    // under the render's own white film and top hairline. It is a real, distinct ground; it is not the
    // Liquid Glass shader.
    PlayerBackgroundStyle.LIQUID_GLASS ->
        if (hasCover) AuraGroundRecipe(cover = 0.55f, coverBlur = 52.dp, film = 0.45f)
        else AuraGroundRecipe(wash = 0.18f, film = 0.45f)
}

/**
 * The mini pill's version of a recipe.
 *
 * The pill is a different surface: 64 dp tall, always on screen, and its ink is full-alpha
 * [AuraPalette.OnGround] over its own 45 % scrim rather than the sheet's 48–55 % steps. Two rules
 * follow from that shape, and one from the names:
 *
 *  · **The bloom is dropped.** Three soft lobes inside a 64 dp pill is a flat wash, not an ambient
 *    bloom; it would only darken the pill for no visible gain.
 *  · **A cover is drawn at full strength or not at all.** The sheet's per-style cover alphas exist to
 *    stay under the sheet's 10 % flat ceiling ([AURA_COVER_ALPHA]); the pill passes `coverCeiling = 1f`
 *    and leans on its scrim instead, so a fractional alpha here would only mean "a fainter cover".
 *  · **Each name gets to mean what it says.** "Predeterminado / Seguir el tema" is the theme's own
 *    opaque ground ([AuraPalette.GroundRaised], which follows the AMOLED switch) — it brings NO artwork
 *    layer, which is exactly the flat `.mi` the render draws. "Desenfoque" is the blurred cover. Those
 *    two used to be the same pill 4 dp of blur apart, i.e. the second one did nothing; a style that
 *    brings no ground of its own no longer inherits a cover to make up the difference.
 *
 * Legibility of the ground this leaves under "Predeterminado", measured with `contrastRatio` and pinned
 * in `AuraAppearanceTest`: title 14.8:1 and artist 5.4:1 on the brand ground, 15.9:1 and 5.5:1 on
 * AMOLED. That is strictly better than the cover ground it replaces, which bottoms out at 2.7:1 / 1.8:1
 * under a white sleeve — the reason the cover is now something you ask for rather than the default.
 *
 * Cost: unchanged where a cover is drawn (the same one 128×128 decode per track), and one decode
 * CHEAPER under "Predeterminado", which no longer asks for a cover at all.
 */
internal fun auraPillRecipe(recipe: AuraGroundRecipe): AuraGroundRecipe = recipe.copy(
    bloom = 0f,
    cover = if (recipe.cover > 0f) 1f else 0f,
)

/**
 * Paints [ground]. Put it in a sheet's `background` slot, or behind a screen's content.
 *
 * @param intensity global dimmer, the same knob `auraScreenBackground` takes: the render dims the ground
 *   on the denser surfaces (the queue at .45), and a style must dim with it rather than shout over it.
 * @param base the opaque fill everything is drawn on. The pill stands on [AuraPalette.GroundRaised].
 * @param coverCeiling the flat artwork budget for THIS surface — [AURA_COVER_ALPHA] for the full-height
 *   surfaces, whose ink is the redesign's 48–55 % alpha steps. The pill passes 1f because its ink is
 *   full-alpha [AuraPalette.OnGround] over its own [scrim], which is the budget it has always had.
 * @param scrim black drawn OVER everything, the classic BLUR style's own device for keeping light text
 *   on a bright cover. 0 on the full-height surfaces, where the ceiling above does that job instead.
 */
@Composable
internal fun AuraGroundLayer(
    ground: AuraGround,
    modifier: Modifier = Modifier,
    intensity: Float = 1f,
    base: Color = AuraPalette.Ground,
    recipe: AuraGroundRecipe = ground.recipe,
    coverCeiling: Float = AURA_COVER_ALPHA,
    scrim: Float = 0f,
) {
    val i = intensity.coerceIn(0f, 1f)
    Box(modifier.fillMaxSize().drawBehind { drawRect(base) }) {
        val cover = ground.coverUrl
        if (recipe.cover > 0f && cover != null) {
            AuraCoverGround(url = cover, recipe = recipe, alpha = coverCeiling * recipe.cover * i)
        }
        if (recipe.wash > 0f) {
            Box(Modifier.fillMaxSize().auraArtworkWash(ground.artwork, recipe.wash * i))
        }
        if (recipe.lobes > 0f) {
            AuraLobeGround(colors = ground.artwork, alpha = recipe.lobes * i, drift = recipe.drift)
        }
        if (recipe.bloom > 0f) {
            Box(Modifier.fillMaxSize().auraBloom(ground.bloom, recipe.bloom * i))
        }
        if (recipe.film > 0f) {
            Box(Modifier.fillMaxSize().auraGlassFilm(AURA_COVER_ALPHA * recipe.film * i))
        }
        if (scrim > 0f) {
            Box(Modifier.fillMaxSize().drawBehind { drawRect(Color.Black.copy(alpha = scrim)) })
        }
    }
}

/**
 * ONE 128×128 decode per track, blurred once, drawn at [alpha].
 *
 * Byte for byte the request [AuraMiniPlayer] and the classic `PlayerBackgroundStyle.BLUR` already make,
 * and — because a Coil memory-cache key without transformations does not include the size — the same
 * cache slot the bloom's own extraction fills. So for a track whose bloom has resolved this costs no
 * decode at all.
 */
@Composable
private fun AuraCoverGround(url: String, recipe: AuraGroundRecipe, alpha: Float) {
    val context = LocalContext.current
    val request = remember(context, url) {
        ImageRequest.Builder(context)
            .data(url)
            .size(128, 128)
            .allowHardware(false)
            .crossfade(false)
            .build()
    }
    val filter = remember(recipe.coverSaturation) {
        if (recipe.coverSaturation == 1f) null
        else ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(recipe.coverSaturation) })
    }
    if (recipe.spin) {
        // 90 s per turn. `rotationZ` on a graphicsLayer transforms an ALREADY RENDERED layer: the blur
        // is not recomputed, the image is not re-decoded, and nothing recomposes.
        val transition = rememberInfiniteTransition(label = "auraGroundSpin")
        val angle by transition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(
                animation = tween(90_000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
            label = "auraGroundSpinAngle",
        )
        AuraCoverGroundImage(request, filter, alpha, recipe) { angle }
    } else {
        AuraCoverGroundImage(request, filter, alpha, recipe) { 0f }
    }
}

@Composable
private fun AuraCoverGroundImage(
    request: ImageRequest,
    filter: ColorFilter?,
    alpha: Float,
    recipe: AuraGroundRecipe,
    angle: () -> Float,
) {
    AsyncImage(
        model = request,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        colorFilter = filter,
        alpha = alpha,
        modifier = Modifier
            .fillMaxSize()
            // Read in the LAYER block, so a turning mesh invalidates the layer's transform and nothing
            // else. `.blur` sits inside it: the blurred result is what gets scaled and rotated.
            .graphicsLayer {
                scaleX = recipe.coverScale
                scaleY = recipe.coverScale
                rotationZ = angle()
            }
            .blur(recipe.coverBlur),
    )
}

/**
 * The vertical artwork wash: the track's dominant hue at the top edge, gone by two thirds down.
 *
 * One `Brush` per (size, colours, alpha) inside `drawWithCache`, then one rect per frame. `.copy(alpha)`
 * on a bloom colour keeps the hue and replaces the bloom's own alpha — the colours in [AuraBloomColors]
 * are already normalised (saturation floored, value clamped to 0.55..0.95) by the bloom's extractor, so
 * a black-and-white sleeve stays grey and a blown-out one cannot flare.
 */
private fun Modifier.auraArtworkWash(colors: AuraBloomColors, alpha: Float): Modifier = drawWithCache {
    val a = alpha.coerceIn(0f, 1f)
    if (a <= 0f) return@drawWithCache onDrawBehind { }
    val brush = Brush.verticalGradient(
        colorStops = arrayOf(
            0f to colors.topLeft.copy(alpha = a),
            0.42f to colors.topRight.copy(alpha = a * 0.55f),
            0.78f to colors.center.copy(alpha = a * 0.14f),
            1f to Color.Transparent,
        ),
    )
    onDrawBehind { drawRect(brush) }
}

/**
 * Two artwork lobes. [drift] moves them on a ~28 s cycle; otherwise they stand still.
 *
 * The brushes are built ONCE per (size, colours, alpha) and the phase is read inside `onDrawBehind`, so
 * a drifting ground invalidates DRAW and allocates nothing per frame — the rule [AuraBloom] is built on.
 */
@Composable
private fun AuraLobeGround(colors: AuraBloomColors, alpha: Float, drift: Boolean) {
    if (drift) {
        val transition = rememberInfiniteTransition(label = "auraGroundDrift")
        val phase by transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(28_000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
            label = "auraGroundDriftPhase",
        )
        Box(Modifier.fillMaxSize().auraLobes(colors, alpha) { phase })
    } else {
        Box(Modifier.fillMaxSize().auraLobes(colors, alpha) { 0f })
    }
}

private fun Modifier.auraLobes(
    colors: AuraBloomColors,
    alpha: Float,
    phase: () -> Float,
): Modifier = drawWithCache {
    val a = alpha.coerceIn(0f, 1f)
    val w = size.width
    val h = size.height
    if (a <= 0f || w <= 0f || h <= 0f) return@drawWithCache onDrawBehind { }

    val radius = maxOf(w, h) * 0.72f
    fun lobe(color: Color, strength: Float) = Brush.radialGradient(
        colors = listOf(color.copy(alpha = a * strength), Color.Transparent),
        center = Offset.Zero,
        radius = radius,
    )
    val first = lobe(colors.topLeft, 1f)
    val second = lobe(colors.topRight, 0.85f)
    val box = Size(radius * 2f, radius * 2f)
    val corner = Offset(-radius, -radius)

    onDrawBehind {
        val p = phase()
        val twoPi = 2f * kotlin.math.PI.toFloat()
        // Two circles of the same period, a third of a turn apart, so they never coincide and never
        // fully leave the sheet. sin/cos of one float: the whole per-frame cost besides the two fills.
        val x1 = w * (0.28f + 0.22f * kotlin.math.sin(twoPi * p))
        val y1 = h * (0.24f + 0.16f * kotlin.math.cos(twoPi * p))
        val x2 = w * (0.76f - 0.20f * kotlin.math.sin(twoPi * (p + 0.33f)))
        val y2 = h * (0.34f + 0.18f * kotlin.math.cos(twoPi * (p + 0.33f)))
        translate(x1, y1) { drawRect(brush = first, topLeft = corner, size = box) }
        translate(x2, y2) { drawRect(brush = second, topLeft = corner, size = box) }
    }
}

/**
 * The frosted film of the glass style: the render's own white wash, brightest at the top edge, plus the
 * 1 dp hairline that reads as the lip of a pane. Shares the flat budget with the cover under it, so the
 * two together stay inside [AURA_COVER_ALPHA].
 */
private fun Modifier.auraGlassFilm(alpha: Float): Modifier = drawWithCache {
    val a = alpha.coerceIn(0f, 1f)
    if (a <= 0f) return@drawWithCache onDrawBehind { }
    val sheen = Brush.verticalGradient(
        colorStops = arrayOf(
            0f to Color.White.copy(alpha = a),
            0.5f to Color.White.copy(alpha = a * 0.45f),
            1f to Color.White.copy(alpha = a * 0.2f),
        ),
    )
    val hairline = 1.dp.toPx()
    onDrawBehind {
        drawRect(sheen)
        drawRect(color = AuraPalette.SurfaceLine, size = Size(size.width, hairline))
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
