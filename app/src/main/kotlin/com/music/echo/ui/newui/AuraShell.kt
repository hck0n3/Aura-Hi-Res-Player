package iad1tya.echo.music.ui.newui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColor
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.updateTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableLongState
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
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
import iad1tya.echo.music.constants.MiniPlayerBackgroundStyleKey
import iad1tya.echo.music.constants.MiniPlayerHeight
import iad1tya.echo.music.constants.PlayerBackgroundStyle
import iad1tya.echo.music.constants.SwipeSensitivityKey
import iad1tya.echo.music.constants.SwipeThumbnailKey
import iad1tya.echo.music.extensions.togglePlayPause
import iad1tya.echo.music.ui.player.PlayerVideoSurface
import iad1tya.echo.music.ui.screens.Screens
import iad1tya.echo.music.ui.utils.rememberIsTvOrCar
import iad1tya.echo.music.ui.utils.tvFocusable
import iad1tya.echo.music.utils.rememberEnumPreference
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
 * the active one in [AuraPalette.Teal] and the rest in [AuraPalette.NavInactive].
 *
 * ## It ANIMATES, because this is the animation seen most often per day
 * Switching tab used to be a one-frame colour swap: no indicator, no scale, no press feedback. It now
 * runs the same three motions the classic floating toolbar has shipped for months
 * ([iad1tya.echo.music.ui.component.FloatingNavigationToolbar]), expressed through [AuraMotion] so the
 * whole redesign speaks one dialect:
 *  · a [AuraPalette.NavIndicator] pill that SLIDES from the old cell to the new one — width and offset
 *    on [AuraMotion.standard], measured from the cells themselves via [onGloballyPositioned] so the
 *    pill can never disagree with the layout;
 *  · glyph + label tint on [AuraMotion.color];
 *  · a 1.12× glyph scale on the selected cell and [AuraMotion.PRESS_SCALE] while a finger is down,
 *    both on [AuraMotion.press] and both applied in a `graphicsLayer` block, i.e. in the DRAW phase.
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
    // The shell is composed by `MainActivity` OUTSIDE any [NewUiGate], so it cannot rely on a gate
    // having resolved the palette first — on a cold start the bar can compose before any screen does.
    // Idempotent, so this costs three comparisons once the values have settled.
    AuraPaletteSync()

    val density = LocalDensity.current
    // Measured geometry of every cell, keyed by its index in the row (Ajustes, when present, is the
    // last index). Populated from onGloballyPositioned, so the pill tracks whatever the row actually
    // laid out — equal weights, an odd trailing pixel, a three-cell row without Listen Together.
    val cellOffsets = remember { mutableStateMapOf<Int, Dp>() }
    val cellWidths = remember { mutableStateMapOf<Int, Dp>() }

    val settingsIndex = if (onSettingsClick != null) items.size else -1
    val selectedIndex = when {
        settingsSelected && settingsIndex >= 0 -> settingsIndex
        else -> items.indexOfFirst { isSelected(it) }
    }

    val targetWidth = cellWidths[selectedIndex] ?: 0.dp
    val targetOffset = cellOffsets[selectedIndex] ?: 0.dp
    val pillWidth = remember { Animatable(0.dp, Dp.VectorConverter) }
    val pillOffset = remember { Animatable(0.dp, Dp.VectorConverter) }
    LaunchedEffect(targetWidth, targetOffset) {
        if (pillWidth.value == 0.dp) {
            // First measurement of this bar: LAND on the selected cell. Animating from the zero the
            // Animatable starts at would fly the pill in from the left edge every cold start and every
            // configuration change, which reads as a glitch rather than as a transition.
            pillWidth.snapTo(targetWidth)
            pillOffset.snapTo(targetOffset)
        } else {
            launch { pillWidth.animateTo(targetWidth, AuraMotion.dp) }
            pillOffset.animateTo(targetOffset, AuraMotion.dp)
        }
    }
    // Passed as State, never unwrapped here: the values are read inside [AuraNavIndicator], so the
    // slide recomposes that one Box and never this bar (which would re-run every cell, every
    // stringResource and every icon lookup, 60×/s, on every tab switch).

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(AuraPalette.Ground),
    ) {
        AuraDivider()
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(AuraNavBarHeight),
        ) {
            AuraNavIndicator(width = pillWidth.asState(), offsetX = pillOffset.asState())
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxSize(),
            ) {
                items.forEachIndexed { index, screen ->
                    val selected = isSelected(screen)
                    AuraNavItem(
                        icon = auraNavIcon(screen),
                        label = stringResource(screen.titleId),
                        selected = selected,
                        onClick = { onItemClick(screen, selected) },
                        modifier = Modifier.onGloballyPositioned { coordinates ->
                            cellWidths[index] = with(density) { coordinates.size.width.toDp() }
                            cellOffsets[index] = with(density) { coordinates.positionInParent().x.toDp() }
                        },
                    )
                }
                if (onSettingsClick != null) {
                    AuraNavItem(
                        icon = AuraIcons.Settings,
                        label = stringResource(R.string.settings),
                        selected = settingsSelected,
                        onClick = onSettingsClick,
                        modifier = Modifier.onGloballyPositioned { coordinates ->
                            cellWidths[settingsIndex] = with(density) { coordinates.size.width.toDp() }
                            cellOffsets[settingsIndex] =
                                with(density) { coordinates.positionInParent().x.toDp() }
                        },
                    )
                }
            }
        }
        // The bar's own ground continues under the gesture bar, so content scrolling past the last row
        // is covered by the bar itself rather than by a separate opaque rectangle.
        Spacer(Modifier.height(bottomInset))
    }
}

/**
 * The pill that marks the selected cell. Sized and positioned from the cells' own measured geometry,
 * so it lands on the cell rather than on an assumption about how the row divided itself.
 *
 * Nothing is drawn until the row has reported a width, so no zero-width pill is ever laid out.
 */
@Composable
private fun AuraNavIndicator(width: State<Dp>, offsetX: State<Dp>) {
    val w = width.value
    if (w <= 0.dp) return
    Box(
        modifier = Modifier
            .offset(x = offsetX.value)
            .width(w)
            .fillMaxHeight()
            // Inset so the pill reads as a marker behind the cell, not as a second bar.
            .padding(horizontal = 10.dp, vertical = 8.dp)
            .background(AuraPalette.NavIndicator, AuraShapes.Highlight),
    )
}

/**
 * One cell of [AuraNavigationBar]: `.nv` in the render — a 24 dp glyph over an 8.5 px label.
 *
 * The unselected tint is [AuraPalette.NavInactive] (62 %, ~7:1 on [AuraPalette.Ground]), NOT
 * [AuraPalette.OnGroundGhost] (48 %, ~4.5:1). The ghost step only just clears the AA floor and it is
 * meant for tertiary data you may or may not read; on the PRIMARY navigation it made three of the four
 * cells look disabled — and the bar itself is fully opaque, so this was never a transparency problem
 * to fix on the bar.
 */
@Composable
private fun RowScope.AuraNavItem(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val transition = updateTransition(targetState = selected, label = "auraNavItem")
    val tint by transition.animateColor(
        transitionSpec = { AuraMotion.color() },
        label = "auraNavTint",
    ) { isSelected -> if (isSelected) AuraPalette.Teal else AuraPalette.NavInactive }
    val iconScale by transition.animateFloat(
        transitionSpec = { AuraMotion.press() },
        label = "auraNavIconScale",
    ) { isSelected -> if (isSelected) 1.12f else 1f }

    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (pressed) AuraMotion.PRESS_SCALE else 1f,
        animationSpec = AuraMotion.pressFloat,
        label = "auraNavPressScale",
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier
            .weight(1f)
            .fillMaxWidth()
            .sizeIn(minHeight = AuraSpacing.MinTouchTarget)
            // Read in the layer block, i.e. in the draw phase: a press scales the cell without
            // recomposing it.
            .graphicsLayer {
                scaleX = pressScale
                scaleY = pressScale
            }
            .tvFocusable(rememberIsTvOrCar(), AuraShapes.Highlight, scaleFocused = 1f)
            .clip(AuraShapes.Highlight)
            .clickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                onClickLabel = label,
                role = Role.Tab,
                onClick = onClick,
            )
            .padding(vertical = 8.dp),
    ) {
        AuraIconGlyph(
            icon = icon,
            contentDescription = null,
            size = 24.dp,
            tint = tint,
            modifier = Modifier.graphicsLayer {
                scaleX = iconScale
                scaleY = iconScale
            },
        )
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
 * The render's mini player (`.mi`): a rounded pill inset from both gutters, outlined with
 * [AuraPalette.SurfaceLine] — cover, title, artist, transport. Nothing else, exactly as drawn.
 *
 * ## It has an OPAQUE ground, and that is not optional
 * The render's `.mi` is `rgba(255,255,255,.07)` because in a browser it sits on the page's own dark
 * body. On Android it does not: the sheet's ground slot is deliberately not composed while the sheet
 * is collapsed (`BottomSheet.kt:67-77`), and collapsed is exactly when this pill is on screen — so
 * [AuraPalette.SurfaceFill] alone meant 7 % white composited onto whatever the NavHost was drawing,
 * with a scrolling list showing through the title. "Está tan transparente que no se define nada."
 *
 * The pill therefore starts from [AuraPalette.GroundRaised] (opaque) and only then applies the
 * render's film. On top of that ground, and only from API 31 up, it draws the same cheap "glass" the
 * classic mini already ships: ONE 128×128 decode of the current cover, blurred once with
 * `Modifier.blur(30.dp)`, under a 45 % black scrim. Per TRACK, not per frame, and no backdrop is
 * sampled — see the comment at the call site.
 *
 * It replaces the call to the CLASSIC `MiniPlayer` that `AuraPlayer` made verbatim. That classic mini
 * is where the unrequested Liquid Glass came from — it reads `MiniPlayerBackgroundStyleKey`, a one-time
 * migration had written `LIQUID_GLASS` into that key on high-tier devices, and its `LIQUID_GLASS` branch
 * draws the real backdrop-sampling shader (`MiniPlayer.kt:1219`).
 *
 * This pill reads the SAME key — the control belongs to the user and hiding it was itself a loss — but
 * it can never draw that shader: its `LIQUID_GLASS` is a still, frosted cover under the render's film
 * (`auraGroundRecipe`), one decode per track, no backdrop sample. The value the migration wrote is
 * undone once in `App.kt`, so honouring the key does not resurrect what the owner rejected. See the
 * comment at the ground call site below.
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
        // ── The pill's ground ─────────────────────────────────────────────────────────────────────
        // Bottom to top: an OPAQUE fill, then whatever "Mini reproductor" → "Estilo de fondo" says (its
        // OWN setting, not the player's — see below), then the 45 % scrim, which is drawn only when that
        // choice put artwork there. The content Row below is a sibling carrying the same offset and clip,
        // so the two move together while the finger drags. Without the OPAQUE fill the pill was
        // [AuraPalette.SurfaceFill] — 7 % white — with nothing but the NavHost behind it: "está tan
        // transparente que no se define nada". That fill is drawn under EVERY style, including the one
        // that adds nothing over it, so no choice here can bring the transparency back.
        //
        // The pill is the third surface the seven [PlayerBackgroundStyle] values own (with the player
        // sheet and the queue sheet). [auraPillRecipe] adapts the sheet's recipe to a 64 dp pill, and it
        // holds each name to what it promises: DEFAULT ("Seguir el tema") is the opaque ground above and
        // nothing else — the flat `.mi` the render draws — while "Desenfoque", mesh and glass each bring
        // their own blurred cover on top of it. The pill DID ship with a blurred cover under every style,
        // which is what made those two names the same pill 4 dp of blur apart; the cover is now something
        // you ask for. No cost is added — where a cover is drawn it is the one 128×128 decode per track
        // under `Modifier.blur` already paid for, guarded by API 31, never a backdrop sample — and DEFAULT
        // now asks for no decode at all. A live full-width blur here is exactly what the thermal contract
        // in AuraBloom.kt forbids: this pill is on screen for the whole of every song.
        //
        // ── Which KEY the pill follows, and why it is its own ─────────────────────────────────────
        // [MiniPlayerBackgroundStyleKey], the same key the classic mini reads (MiniPlayer.kt:217) with
        // the same default. It briefly followed the PLAYER's key instead, and that reasoning ("the mini
        // and the player can never disagree") cost the user a control the classic app has always had —
        // "en ajustes no se ven las mismas funciones que antes de personalización". Two surfaces, two
        // keys, exactly as classic; the ability to make them agree is a choice the user still has, by
        // picking the same value twice.
        //
        // DEFAULT is the value App.kt seeds on every fresh install, so someone who never touches this
        // control gets the pill's own opaque ground rather than inheriting the player's heavier
        // Apple-Music blur and wash. It is also the most legible of the five: the ink measures 14.8:1
        // (title) and 5.4:1 (artist) there, against 2.7:1 / 1.8:1 on a white sleeve once a cover is drawn.
        //
        // The stored value is NOT taken on trust: the 0.6.127 high-tier order wrote LIQUID_GLASS into
        // this key unrequested, so honouring it naively would restore the very look the owner rejected
        // twice. That value is undone once, by a fresh key, in App.kt (`applyMiniPlayerGlassUndoV1`) —
        // NOT here, because a renderer that silently overrides a stored preference is how the row above
        // it becomes a placebo in the first place.
        val miniStyle by rememberEnumPreference(
            key = MiniPlayerBackgroundStyleKey,
            defaultValue = PlayerBackgroundStyle.GLOW_ANIMATED,
        )
        val ground = rememberAuraGround(
            mediaMetadata?.id,
            mediaMetadata?.thumbnailUrl,
            styleOverride = miniStyle,
        )
        val pillRecipe = remember(ground.recipe) { auraPillRecipe(ground.recipe) }
        // The 45 % scrim exists to keep light text on artwork. Below API 31 (`Modifier.blur` is a no-op,
        // so `coverUrl` is null there) and on a local track there IS no artwork on this pill, and drawing
        // the scrim anyway would only darken the ground for nothing — which is what the shipped pill
        // already avoided by gating cover and scrim together.
        val pillHasArtwork = (pillRecipe.cover > 0f && ground.coverUrl != null) ||
            pillRecipe.wash > 0f || pillRecipe.lobes > 0f
        Box(
            modifier = Modifier
                .fillMaxSize()
                .offset { IntOffset(offsetX.value.roundToInt(), 0) }
                .clip(AuraShapes.Card),
        ) {
            AuraGroundLayer(
                ground = ground,
                recipe = pillRecipe,
                base = AuraPalette.GroundRaised,
                // The pill's ink is full-alpha OnGround under its own scrim, not the sheet's 48–55 %
                // steps, so it keeps the full-strength cover it has always drawn instead of the sheet's
                // 10 % flat ceiling.
                coverCeiling = 1f,
                scrim = if (pillHasArtwork) 0.45f else 0f,
            )
        }

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
                } else {
                    AuraStableCoverImage(
                        url = mediaMetadata?.thumbnailUrl,
                        contentScale = if (cropAlbumArt) ContentScale.Crop else ContentScale.Fit,
                        decodeTo = 128,
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
