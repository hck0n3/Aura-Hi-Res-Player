package iad1tya.echo.music.ui.newui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableLongState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import coil3.compose.AsyncImage
import iad1tya.echo.music.LocalPlayerConnection
import iad1tya.echo.music.R
import iad1tya.echo.music.constants.CropAlbumArtKey
import iad1tya.echo.music.constants.MiniPlayerHeight
import iad1tya.echo.music.constants.SwipeSensitivityKey
import iad1tya.echo.music.constants.SwipeThumbnailKey
import iad1tya.echo.music.extensions.togglePlayPause
import iad1tya.echo.music.ui.player.PlayerVideoSurface
import iad1tya.echo.music.ui.screens.Screens
import iad1tya.echo.music.ui.utils.rememberIsTvOrCar
import iad1tya.echo.music.ui.utils.tvFocusable
import iad1tya.echo.music.utils.rememberPreference
import kotlin.math.exp
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

/**
 * # The SHELL of the "Interfaz nueva"
 *
 * Six CONTENT screens were rebuilt for the 0.6.144 beta and the frame around them was left classic:
 * an opaque Material top bar, a Material floating-toolbar pill (a glass pill under Liquid Glass), a
 * Material mini-player and two opaque strips over the gesture area. That frame is most of what the
 * owner still saw — "los botones de abajo siguen saliendo como antes", "la parte donde está el título
 * de la app está fea, solo es una barra negra y ya".
 *
 * This file is that frame, in the render's language. Everything here is chosen by
 * `MainActivity` behind [rememberNewUiEnabled]; with the flag OFF not one composable below is
 * reached and the classic shell is byte-identical.
 *
 * ## What lives where
 *  · [AuraNavigationBar] — the render's `.nav`: a flush bar on [AuraPalette.Ground] with a hairline
 *    top rule and one glyph+label cell per destination, the active one in [AuraPalette.Teal].
 *  · [AuraMiniPlayer] — the render's `.mi`: a rounded `rgba(255,255,255,.07)` pill above the nav bar.
 *  · [AuraGlobalActions] / [LocalAuraTopActions] — the four actions the deleted global top bar owned,
 *    re-hosted inside each new screen's own header.
 *
 * ## Nothing here re-implements playback
 * Every action is the same call the classic component makes on the same `PlayerConnection`
 * (`togglePlayPause`, `seekToNext`, `seekToPreviousMediaItem`, `castHandler.play/pause`,
 * `toggleMute`), and the same preferences gate the same behaviours ([SwipeThumbnailKey],
 * [SwipeSensitivityKey], [CropAlbumArtKey]).
 */

// ── Metrics ───────────────────────────────────────────────────────────────────────────────────────

/**
 * Height of the new bottom bar's CONTENT band, excluding the system gesture inset (the bar paints its
 * own ground under that inset, which is what replaces the classic opaque strip).
 *
 * This is the new-UI counterpart of `constants.NavigationBarHeight` (72 dp: a 72 dp floating pill plus
 * `FloatingToolbarBottomPadding`). It is deliberately NOT a change to that constant — the classic
 * shell, the nav rail and the TV/car path all still measure themselves with it. `MainActivity` picks
 * between the two, so both the slide distance and the bottom window inset stay consistent with
 * whichever bar is actually drawn; feeding only one of them is how every list ends up mis-padded.
 */
val AuraNavBarHeight: Dp = 64.dp

// ── Global actions, re-hosted ─────────────────────────────────────────────────────────────────────

/**
 * The global top-bar actions, published by `MainActivity` for the new screens to place inside their
 * own header.
 *
 * The render has NO opaque title bar: the section name ("Inicio", "Biblioteca") lives in the content
 * and the only affordance beside it is a glyph at the right. So the global bar is not drawn at all on
 * routes that own an [AuraScreenHeader] — but its four actions are real controls and dropping them
 * would be a regression, so they move into that header's trailing slot instead.
 *
 * `null` whenever the new UI is off (or the shell has nothing to offer), in which case
 * [AuraTopActions] draws nothing.
 */
val LocalAuraTopActions = compositionLocalOf<(@Composable () -> Unit)?> { null }

/** Renders [LocalAuraTopActions], or nothing. Put it in `AuraScreenHeader(trailing = ...)`. */
@Composable
fun AuraTopActions() {
    LocalAuraTopActions.current?.invoke()
}

/**
 * The four actions the classic `TopAppBar` carried, drawn in the render's language — plus
 * Reconocimiento.
 *
 * Every label is the SAME string resource the classic bar used — `R.string.together`,
 * `R.string.history`, `R.string.offline_mode`, `R.string.account`, `R.string.recognition` — and every
 * click is the same lambda, built in `MainActivity` where the state lives. Nothing is re-derived here.
 *
 * ## Why Reconocimiento is here
 * The render's bottom bar has exactly four cells and the fourth is AJUSTES, so [AuraNavigationBar] now
 * draws that. The build had been spending that slot on a recognition mic instead, and the mic is not
 * a free thing to drop: `navigate("recognition…")` exists in exactly ONE place in the whole app, so
 * that cell was the feature's only door. Rather than delete a shipped feature to satisfy the render,
 * it moves up here — still persistent, still one tap, still labelled in Spanish from the same string.
 * It is drawn FIRST so the account avatar keeps the trailing corner the render gives it.
 */
@Composable
fun AuraGlobalActions(
    showListenTogether: Boolean,
    onListenTogetherClick: () -> Unit,
    showHistory: Boolean,
    onHistoryClick: () -> Unit,
    offlineMode: Boolean,
    onOfflineToggle: () -> Unit,
    accountImageUrl: String?,
    onAccountClick: () -> Unit,
    onRecognitionClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        modifier = modifier,
    ) {
        AuraIconButton(
            icon = AuraIcons.Mic,
            contentDescription = stringResource(R.string.recognition),
            onClick = onRecognitionClick,
            size = 20.dp,
            tint = AuraPalette.OnGroundFaint,
        )
        if (showListenTogether) {
            AuraIconButton(
                icon = AuraIcons.People,
                contentDescription = stringResource(R.string.together),
                onClick = onListenTogetherClick,
                size = 20.dp,
                tint = AuraPalette.OnGroundFaint,
            )
        }
        if (showHistory) {
            AuraIconButton(
                icon = AuraIcons.History,
                contentDescription = stringResource(R.string.history),
                onClick = onHistoryClick,
                size = 20.dp,
                tint = AuraPalette.OnGroundFaint,
            )
        }
        // Modo sin conexión: teal while ON, because it is a state the user must be able to read at a
        // glance — the classic bar only swapped the glyph.
        AuraIconButton(
            icon = if (offlineMode) AuraIcons.CloudOff else AuraIcons.Cloud,
            contentDescription = stringResource(R.string.offline_mode),
            onClick = onOfflineToggle,
            size = 20.dp,
            tint = if (offlineMode) AuraPalette.Teal else AuraPalette.OnGroundFaint,
        )
        // Cuenta / ajustes. With a signed-in avatar it is the avatar, exactly as today; otherwise the
        // render's cog. Drawn small, touched at 48 dp.
        val accountLabel = stringResource(R.string.account)
        if (accountImageUrl != null) {
            Box(
                modifier = Modifier
                    .sizeIn(
                        minWidth = AuraSpacing.MinTouchTarget,
                        minHeight = AuraSpacing.MinTouchTarget,
                    )
                    .clip(CircleShape)
                    .clickable(
                        onClickLabel = accountLabel,
                        role = Role.Button,
                        onClick = onAccountClick,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                AsyncImage(
                    model = accountImageUrl,
                    contentDescription = null,
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape),
                )
            }
        } else {
            AuraIconButton(
                icon = AuraIcons.Settings,
                contentDescription = accountLabel,
                onClick = onAccountClick,
                size = 20.dp,
                tint = AuraPalette.OnGroundFaint,
            )
        }
    }
}

// ── Bottom navigation ─────────────────────────────────────────────────────────────────────────────

/**
 * The render's bottom bar: flush against the bottom edge, filled with [AuraPalette.Ground], separated
 * from the content by a single `rgba(255,255,255,.08)` hairline, one glyph + label per destination,
 * the active one in teal at full opacity and the rest at `.42`.
 *
 * It replaces the M3 `HorizontalFloatingToolbar` pill — the thing the owner called "el reproductor
 * flotante y sus botones flotantes en liquid glass". No glass surface is sampled here and no
 * `FloatingActionButton` is used, so the Liquid Glass nav-bar surface simply has nothing to attach to
 * while the new UI is on.
 *
 * @param items the SAME `navigationItems` the classic toolbar receives (Listen Together is already
 *   filtered out of it when the user hosts it in the top bar), so the two bars can never disagree.
 * @param onSettingsClick the render's FOURTH cell: `#i-cog` / "Ajustes". It is not a member of
 *   [items] because the shared [Screens] sealed class has no Settings destination — and giving it one
 *   would change the CLASSIC floating toolbar too, which is out of bounds while the flag is off. So it
 *   is a trailing cell drawn from the same [AuraNavItem], with the same geometry, next to the others.
 *   It replaces the recognition mic that used to hold this slot; the mic moved into
 *   [AuraGlobalActions], because it is the only entry point that feature has anywhere in the app.
 * @param settingsSelected whether the Ajustes route is current — the render draws that cell in teal on
 *   the Ajustes screen (`nv on`), so the state has to be passed in; there is no [Screens] to match on.
 * @param bottomInset the system gesture inset. The bar paints its ground THROUGH it, which is what
 *   makes the classic opaque strip over the gesture area unnecessary.
 */
@Composable
fun AuraNavigationBar(
    items: List<Screens>,
    isSelected: (Screens) -> Boolean,
    onItemClick: (Screens, Boolean) -> Unit,
    bottomInset: Dp,
    modifier: Modifier = Modifier,
    onSettingsClick: (() -> Unit)? = null,
    settingsSelected: Boolean = false,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(AuraPalette.Ground),
    ) {
        AuraDivider()
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .height(AuraNavBarHeight),
        ) {
            items.forEach { screen ->
                val selected = isSelected(screen)
                AuraNavItem(
                    icon = auraNavIcon(screen),
                    label = stringResource(screen.titleId),
                    selected = selected,
                    onClick = { onItemClick(screen, selected) },
                )
            }
            if (onSettingsClick != null) {
                AuraNavItem(
                    icon = AuraIcons.Settings,
                    label = stringResource(R.string.settings),
                    selected = settingsSelected,
                    onClick = onSettingsClick,
                )
            }
        }
        // The bar's own ground continues under the gesture bar, so content scrolling past the last row
        // is covered by the bar itself rather than by a separate opaque rectangle.
        Spacer(Modifier.height(bottomInset))
    }
}

/** One cell of [AuraNavigationBar]: `.nv` in the render — a 24 dp glyph over an 8.5 px label. */
@Composable
private fun RowScope.AuraNavItem(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val tint = if (selected) AuraPalette.Teal else AuraPalette.OnGroundGhost
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .weight(1f)
            .fillMaxWidth()
            .sizeIn(minHeight = AuraSpacing.MinTouchTarget)
            .tvFocusable(rememberIsTvOrCar(), AuraShapes.Highlight, scaleFocused = 1f)
            .clip(AuraShapes.Highlight)
            .clickable(onClickLabel = label, role = Role.Tab, onClick = onClick)
            .padding(vertical = 8.dp),
    ) {
        AuraIconGlyph(icon = icon, contentDescription = null, size = 24.dp, tint = tint)
        Spacer(Modifier.height(4.dp))
        Text(
            text = label,
            style = AuraType.NavLabel,
            color = tint,
            maxLines = 1,
            textAlign = TextAlign.Center,
            overflow = AuraDefaultOverflow,
        )
    }
}

/**
 * Destination → glyph. Exhaustive over the sealed [Screens] hierarchy on purpose: adding a destination
 * without giving it an Aura glyph must fail to compile, not fall back to a blank cell.
 */
private fun auraNavIcon(screen: Screens): ImageVector = when (screen) {
    Screens.Home -> AuraIcons.Home
    Screens.Search -> AuraIcons.Search
    Screens.Library -> AuraIcons.Library
    Screens.ListenTogether -> AuraIcons.People
}

// ── Mini player ───────────────────────────────────────────────────────────────────────────────────

/**
 * The render's mini player (`.mi`): a rounded pill inset from both gutters, filled with
 * [AuraPalette.SurfaceFill] and outlined with [AuraPalette.SurfaceLine] — cover, title, artist,
 * play/pause. Nothing else, exactly as drawn.
 *
 * It replaces the call to the CLASSIC `MiniPlayer` that `AuraPlayer` made verbatim, which is why
 * Liquid Glass kept appearing inside the new player: the classic mini reads
 * `MiniPlayerBackgroundStyleKey`, and a one-time migration had set that key to `LIQUID_GLASS` on
 * high-tier devices.
 *
 * ## What is preserved from the classic mini, and how
 *  · **Video.** When the player is in video mode and this mini owns the surface, the artwork slot
 *    hosts `PlayerVideoSurface` — the same single-surface contract (`shouldBindVideoSurface`) the
 *    classic mini honours, so the expanded player and the mini never bind two surfaces at once.
 *  · **Prev / next.** Drawn as real buttons flanking play/pause, the same three-control row the
 *    classic mini has (`MiniPlayer.kt` prev / play / next). The render's `.mi` draws only play/pause
 *    and an earlier revision of this composable followed it literally, leaving the swipe below as the
 *    ONLY way to change track — and that swipe is gated on [SwipeThumbnailKey], an OPTIONAL
 *    preference. Turning that preference off therefore removed skip from the mini player entirely.
 *    A core transport control may not depend on an optional gesture, so the buttons are always drawn
 *    and the swipe stays as the accelerator it was meant to be. Disabled (not hidden) when
 *    `canSkipPrevious` / `canSkipNext` is false or for a Listen Together guest, exactly as classic.
 *  · **Swipe to change track.** Gated on [SwipeThumbnailKey] (default ON) with the classic
 *    [SwipeSensitivityKey] threshold curve and the classic actions, and disabled for a Listen Together
 *    guest exactly as today. A shortcut on top of the buttons, never a replacement for them.
 *  · **Play/pause.** The same four branches as the full transport: guest → mute, casting → the remote
 *    device, ENDED → restart, otherwise `togglePlayPause`.
 *  · **Progress.** A 2 dp teal rule along the bottom edge of the pill, read inside a draw lambda, so a
 *    position tick repaints without recomposing anything.
 *  · **Error.** `R.string.error_playing` still surfaces under the artist line.
 *  · **Recorte de portadas.** Honours [CropAlbumArtKey] like every classic renderer.
 */
@Composable
fun AuraMiniPlayer(
    positionState: MutableLongState,
    durationState: MutableLongState,
    modifier: Modifier = Modifier,
    shouldBindVideoSurface: Boolean = true,
) {
    val playerConnection = LocalPlayerConnection.current ?: return

    val mediaMetadata by playerConnection.mediaMetadata.collectAsState()
    val playbackState by playerConnection.playbackState.collectAsState()
    val isPlaying by playerConnection.isPlaying.collectAsState()
    val isMuted by playerConnection.isMuted.collectAsState()
    val error by playerConnection.error.collectAsState()
    val canSkipNext by playerConnection.canSkipNext.collectAsState()
    val canSkipPrevious by playerConnection.canSkipPrevious.collectAsState()

    val castHandler = remember(playerConnection) {
        runCatching { playerConnection.service.castConnectionHandler }.getOrNull()
    }
    val isCasting by castHandler?.isCasting?.collectAsState() ?: remember { mutableStateOf(false) }
    val castIsPlaying by castHandler?.castIsPlaying?.collectAsState() ?: remember { mutableStateOf(false) }
    val effectiveIsPlaying = if (isCasting) castIsPlaying else isPlaying

    val listenTogetherManager = iad1tya.echo.music.LocalListenTogetherManager.current
    val isListenTogetherGuest = listenTogetherManager?.let { it.isInRoom && !it.isHost } ?: false

    // ── Swipe to change track — the classic gesture, verbatim ─────────────────────────────────────
    val swipeThumbnailPref by rememberPreference(SwipeThumbnailKey, true)
    val swipeSensitivity by rememberPreference(SwipeSensitivityKey, 0.73f)
    val swipeEnabled = swipeThumbnailPref && !isListenTogetherGuest
    val offsetX = remember { Animatable(0f) }
    var dragStartTime by remember { mutableLongStateOf(0L) }
    var totalDrag by remember { mutableFloatStateOf(0f) }
    val scope = rememberCoroutineScope()
    val settleSpec = remember {
        spring<Float>(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessLow)
    }
    // The classic logistic sensitivity curve (MiniPlayer.kt) — same constants, same feel.
    val autoSwipeThreshold = remember(swipeSensitivity) {
        (600 / (1f + exp(-(-11.44748 * swipeSensitivity + 9.04945)))).roundToInt()
    }

    val cropAlbumArt by rememberPreference(CropAlbumArtKey, false)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(MiniPlayerHeight)
            .padding(horizontal = 14.dp)
            .then(
                if (swipeEnabled) {
                    Modifier.pointerInputSwipe(
                        onStart = {
                            dragStartTime = System.currentTimeMillis()
                            totalDrag = 0f
                        },
                        onDrag = { delta ->
                            totalDrag += delta
                            scope.launch { offsetX.snapTo(offsetX.value + delta) }
                        },
                        onEnd = {
                            val elapsed = (System.currentTimeMillis() - dragStartTime).coerceAtLeast(1L)
                            val velocity = kotlin.math.abs(totalDrag) / elapsed * 1000f
                            val committed = kotlin.math.abs(offsetX.value) > autoSwipeThreshold ||
                                velocity > autoSwipeThreshold * 4
                            if (committed) {
                                if (offsetX.value > 0) {
                                    if (canSkipPrevious) playerConnection.player.seekToPreviousMediaItem()
                                } else {
                                    if (canSkipNext) playerConnection.player.seekToNext()
                                }
                            }
                            scope.launch { offsetX.animateTo(0f, settleSpec) }
                        },
                    )
                } else Modifier,
            ),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(11.dp),
            modifier = Modifier
                .fillMaxSize()
                .offset { IntOffset(offsetX.value.roundToInt(), 0) }
                .clip(AuraShapes.Card)
                .background(AuraPalette.SurfaceFill)
                .border(1.dp, AuraPalette.SurfaceLine, AuraShapes.Card)
                // The render's `.mi` has no timeline; the classic mini does, and losing "how far in am
                // I" is a real loss. Drawn as a hairline along the bottom edge, inside the draw phase:
                // reading the position here repaints, it does not recompose.
                .drawWithContent {
                    drawContent()
                    val duration = durationState.longValue
                    if (duration > 0) {
                        val progress = (positionState.longValue.toFloat() / duration).coerceIn(0f, 1f)
                        val strokeHeight = 2.dp.toPx()
                        drawRect(
                            color = AuraPalette.Teal,
                            topLeft = androidx.compose.ui.geometry.Offset(0f, size.height - strokeHeight),
                            size = androidx.compose.ui.geometry.Size(size.width * progress, strokeHeight),
                        )
                    }
                }
                // Render `.mi`: `padding: 7px 10px 7px 7px`. The trailing 10 is dropped to 2 because
                // the transport group that follows is made of 48 dp touch boxes drawing 22–25 dp
                // glyphs — the box itself already contributes ~12 dp of optical inset on that side, so
                // keeping the full 10 would both double-inset the glyph AND steal 8 dp from the title.
                .padding(start = 10.dp, end = 2.dp, top = 10.dp, bottom = 10.dp),
        ) {
            val videoMode by playerConnection.videoMode.collectAsState()
            val videoUrl by playerConnection.videoUrl.collectAsState()
            AuraArtwork(
                size = 40.dp,
                placeholderSeed = mediaMetadata?.id,
            ) {
                if (videoMode && !videoUrl.isNullOrEmpty() && shouldBindVideoSurface) {
                    PlayerVideoSurface(
                        playerConnection = playerConnection,
                        modifier = Modifier.fillMaxSize().clip(AuraShapes.Artwork),
                    )
                } else if (mediaMetadata?.thumbnailUrl != null) {
                    AsyncImage(
                        model = mediaMetadata?.thumbnailUrl,
                        contentDescription = null,
                        contentScale = if (cropAlbumArt) ContentScale.Crop else ContentScale.Fit,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }

            Column(
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    text = mediaMetadata?.title.orEmpty(),
                    style = AuraType.MiniTitle,
                    color = AuraPalette.OnGround,
                    maxLines = 1,
                    overflow = AuraDefaultOverflow,
                )
                val artists = mediaMetadata?.artists
                    ?.filter { it.name.isNotBlank() }
                    ?.joinToString { it.name }
                    .orEmpty()
                if (artists.isNotBlank()) {
                    Text(
                        text = artists,
                        style = AuraType.MiniArtist,
                        color = AuraPalette.OnGroundMuted,
                        maxLines = 1,
                        overflow = AuraDefaultOverflow,
                    )
                }
                AnimatedVisibility(visible = error != null, enter = fadeIn(), exit = fadeOut()) {
                    Text(
                        text = stringResource(R.string.error_playing),
                        style = AuraType.MiniArtist,
                        color = MaterialTheme.colorScheme.error,
                        maxLines = 1,
                        overflow = AuraDefaultOverflow,
                    )
                }
            }

            if (isCasting) {
                AuraIconGlyph(
                    icon = AuraIcons.Cast,
                    contentDescription = null,
                    size = 18.dp,
                    tint = AuraPalette.Teal,
                )
            }

            // The transport group. Zero spacing between the three: each [AuraIconButton] already
            // reserves AuraSpacing.MinTouchTarget (48 dp), so the fingers never overlap and the pill
            // stays as compact as the render's row while carrying all three controls.
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(0.dp),
            ) {
                AuraIconButton(
                    icon = AuraIcons.SkipPrevious,
                    contentDescription = stringResource(R.string.previous),
                    onClick = { playerConnection.player.seekToPreviousMediaItem() },
                    enabled = canSkipPrevious && !isListenTogetherGuest,
                    size = 22.dp,
                )

                AuraIconButton(
                    // A Listen Together GUEST cannot pause the room — the classic mini turns this
                    // button into the local mute, so the new one does exactly the same rather than
                    // showing a play/pause that would do nothing.
                    icon = when {
                        isListenTogetherGuest -> AuraIcons.Volume
                        effectiveIsPlaying -> AuraIcons.Pause
                        else -> AuraIcons.Play
                    },
                    contentDescription = when {
                        isListenTogetherGuest ->
                            if (isMuted) stringResource(R.string.unmute) else stringResource(R.string.mute)
                        effectiveIsPlaying -> stringResource(R.string.pause)
                        else -> stringResource(R.string.play)
                    },
                    onClick = {
                        when {
                            isListenTogetherGuest -> playerConnection.toggleMute()
                            isCasting -> if (castIsPlaying) castHandler?.pause() else castHandler?.play()
                            playbackState == Player.STATE_ENDED -> {
                                playerConnection.player.seekTo(0, 0)
                                playerConnection.player.playWhenReady = true
                            }
                            else -> playerConnection.togglePlayPause()
                        }
                    },
                    size = 25.dp,
                    tint = if (isListenTogetherGuest && isMuted) {
                        AuraPalette.OnGroundDisabled
                    } else {
                        AuraPalette.OnGround
                    },
                )

                AuraIconButton(
                    icon = AuraIcons.SkipNext,
                    contentDescription = stringResource(R.string.next),
                    onClick = { playerConnection.player.seekToNext() },
                    enabled = canSkipNext && !isListenTogetherGuest,
                    size = 22.dp,
                )
            }
        }
    }
}

/** The classic mini's horizontal drag, extracted so the modifier chain above stays readable. */
private fun Modifier.pointerInputSwipe(
    onStart: () -> Unit,
    onDrag: (Float) -> Unit,
    onEnd: () -> Unit,
): Modifier = this.pointerInput(Unit) {
    detectHorizontalDragGestures(
        onDragStart = { onStart() },
        onDragEnd = { onEnd() },
        onDragCancel = { onEnd() },
        onHorizontalDrag = { _, dragAmount -> onDrag(dragAmount) },
    )
}
