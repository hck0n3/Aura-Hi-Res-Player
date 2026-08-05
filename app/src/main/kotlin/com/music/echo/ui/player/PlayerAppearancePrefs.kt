package iad1tya.echo.music.ui.player

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitHorizontalTouchSlopOrCancellation
import androidx.compose.foundation.gestures.horizontalDrag
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import iad1tya.echo.music.constants.HidePlayerSliderKey
import iad1tya.echo.music.constants.HideStatusBarOnFullscreenKey
import iad1tya.echo.music.constants.PlayerButtonsStyle
import iad1tya.echo.music.constants.PlayerButtonsStyleKey
import iad1tya.echo.music.constants.SwipeLyricsKey
import iad1tya.echo.music.utils.rememberEnumPreference
import iad1tya.echo.music.utils.rememberPreference

/**
 * # Player appearance preferences that EVERY player shape must obey
 *
 * The classic player used to be the only read site of four Ajustes ▸ Apariencia controls, while the
 * "Interfaz nueva" player drew its own transport, its own fullscreen lyrics and its own menu. The rows
 * stayed visible with the new UI on — so the user could pick a button style, hide the status bar or
 * hide the volume control and nothing happened. That is the placebo pattern the owner had 1717 lines
 * deleted to erradicate.
 *
 * Everything below is the ONE implementation of those preferences. Both players call into this file;
 * neither re-derives a colour, re-writes an insets effect or re-invents the gesture. If a fifth player
 * shape ever appears, it honours the four settings by calling these — and cannot honour them halfway.
 */

// ── "Estilo de los botones del reproductor" (PlayerButtonsStyleKey) ───────────────────────────────

/**
 * The four colours the classic transport derives from `PlayerButtonsStyleKey`.
 *
 *  · [textButtonColor] / [iconButtonColor] — the play button's FILL and its INK.
 *  · [sideButtonContainerColor] / [sideButtonContentColor] — the two flanking pills.
 */
@Immutable
data class PlayerButtonColors(
    val textButtonColor: Color,
    val iconButtonColor: Color,
    val sideButtonContainerColor: Color,
    val sideButtonContentColor: Color,
)

/**
 * THE derivation of `PlayerButtonsStyleKey`, as a PURE function. Lifted verbatim out of
 * [BottomSheetPlayer] (it was the only copy) so the new player can reuse it instead of growing a
 * second, drifting one, and kept free of `@Composable` so it can be pinned by a unit test — the five
 * controls this file rescued from placebo status shipped with no test at all.
 *
 * The theme colours are parameters rather than `MaterialTheme` reads for the same reason.
 *
 * @param overDarkBackground true when the player is painted over a dark cover/gradient/scrim, so the
 *   DEFAULT style must use the light-on-dark pair regardless of the app theme. The classic player
 *   computes this from its background style + High-Performance Mode; the new player is always true
 *   (its ground is [iad1tya.echo.music.ui.newui.AuraPalette.Ground], a near-black).
 * @param useDarkTheme only consulted for DEFAULT when [overDarkBackground] is false.
 */
fun playerButtonColors(
    style: PlayerButtonsStyle,
    overDarkBackground: Boolean,
    useDarkTheme: Boolean,
    primary: Color,
    onPrimary: Color,
    tertiary: Color,
    onTertiary: Color,
    primaryContainer: Color,
    onPrimaryContainer: Color,
    tertiaryContainer: Color,
    onTertiaryContainer: Color,
): PlayerButtonColors {
    val (text, icon) = when (style) {
        PlayerButtonsStyle.DEFAULT ->
            if (overDarkBackground || useDarkTheme) Color.White to Color.Black
            else Color.Black to Color.White
        PlayerButtonsStyle.PRIMARY -> primary to onPrimary
        PlayerButtonsStyle.TERTIARY -> tertiary to onTertiary
    }
    val (sideContainer, sideContent) = when (style) {
        PlayerButtonsStyle.DEFAULT -> Color.White.copy(alpha = 0.2f) to Color.White
        PlayerButtonsStyle.PRIMARY -> primaryContainer to onPrimaryContainer
        PlayerButtonsStyle.TERTIARY -> tertiaryContainer to onTertiaryContainer
    }
    return PlayerButtonColors(text, icon, sideContainer, sideContent)
}

/** The [playerButtonColors] derivation, wired to the live theme and memoised. */
@Composable
fun rememberPlayerButtonColors(
    style: PlayerButtonsStyle,
    overDarkBackground: Boolean,
    useDarkTheme: Boolean,
): PlayerButtonColors {
    val primary = MaterialTheme.colorScheme.primary
    val onPrimary = MaterialTheme.colorScheme.onPrimary
    val tertiary = MaterialTheme.colorScheme.tertiary
    val onTertiary = MaterialTheme.colorScheme.onTertiary
    val primaryContainer = MaterialTheme.colorScheme.primaryContainer
    val onPrimaryContainer = MaterialTheme.colorScheme.onPrimaryContainer
    val tertiaryContainer = MaterialTheme.colorScheme.tertiaryContainer
    val onTertiaryContainer = MaterialTheme.colorScheme.onTertiaryContainer

    return remember(
        style, overDarkBackground, useDarkTheme,
        primary, onPrimary, tertiary, onTertiary,
        primaryContainer, onPrimaryContainer, tertiaryContainer, onTertiaryContainer,
    ) {
        playerButtonColors(
            style = style,
            overDarkBackground = overDarkBackground,
            useDarkTheme = useDarkTheme,
            primary = primary,
            onPrimary = onPrimary,
            tertiary = tertiary,
            onTertiary = onTertiary,
            primaryContainer = primaryContainer,
            onPrimaryContainer = onPrimaryContainer,
            tertiaryContainer = tertiaryContainer,
            onTertiaryContainer = onTertiaryContainer,
        )
    }
}

/** Reads `PlayerButtonsStyleKey` (default DEFAULT). One read site for every player shape. */
@Composable
fun rememberPlayerButtonsStyle(): PlayerButtonsStyle {
    val style by rememberEnumPreference(PlayerButtonsStyleKey, defaultValue = PlayerButtonsStyle.DEFAULT)
    return style
}

// ── "Ocultar la barra de estado en pantalla completa" (HideStatusBarOnFullscreenKey) ──────────────

/**
 * Hides / restores the status bar while a fullscreen lyrics mode is up.
 *
 * Reads the preference itself so a caller cannot forget it, and always restores the bar on dispose —
 * leaving a user stuck without a status bar because a player left composition is not recoverable
 * without killing the app.
 *
 * @param isFullScreen true only while a fullscreen lyrics view is actually on screen.
 */
@Composable
fun HideStatusBarOnFullscreenEffect(isFullScreen: Boolean) {
    val context = LocalContext.current
    val hideStatusBarOnFullscreen by rememberPreference(
        HideStatusBarOnFullscreenKey,
        defaultValue = true,
    )

    DisposableEffect(isFullScreen, hideStatusBarOnFullscreen) {
        val window = (context as? android.app.Activity)?.window
        if (window != null) {
            val insetsController = WindowCompat.getInsetsController(window, window.decorView)
            if (isFullScreen && hideStatusBarOnFullscreen) {
                insetsController.hide(WindowInsetsCompat.Type.statusBars())
                insetsController.systemBarsBehavior =
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            } else {
                insetsController.show(WindowInsetsCompat.Type.statusBars())
            }
        }

        onDispose {
            if (window != null) {
                val insetsController = WindowCompat.getInsetsController(window, window.decorView)
                insetsController.show(WindowInsetsCompat.Type.statusBars())
            }
        }
    }
}

// ── "Deslizar en la letra para cambiar de canción" (SwipeLyricsKey) ───────────────────────────────

/** Reads `SwipeLyricsKey` (default OFF). */
@Composable
fun rememberSwipeLyricsEnabled(): Boolean {
    val enabled by rememberPreference(SwipeLyricsKey, defaultValue = false)
    return enabled
}

/**
 * The gesture `R.string.lyrics_swipe_to_change_song_desc` has always promised and no player ever
 * implemented: *"desliza a la izquierda o a la derecha en la sección del artista y el nombre de la
 * canción en las letras a pantalla completa para cambiar de canción"*.
 *
 * `SwipeLyricsKey` was read once in the classic player and then never used again — a switch the user
 * could flip that did nothing in EITHER UI. Rather than delete a documented feature, the gesture is
 * implemented here, once, and both players attach it to their title/artist block.
 *
 * Implementation notes:
 *  · `awaitFirstDown(requireUnconsumed = false)` — the title and the artist are each `combinedClickable`
 *    (open album / open artist, long-press to copy), and a clickable consumes the down. Same pattern the
 *    queue-open drag already uses (Player.kt / AuraPlayer.kt).
 *  · Only a HORIZONTAL slop is claimed, so the vertical drags that open the queue sheet and collapse the
 *    player are untouched, and a plain tap still reaches the clickable underneath.
 *  · Fires on release past a 48 dp threshold — no per-frame work, nothing animated.
 */
/**
 * The FOUR terms that must all hold before the lyrics swipe is armed. Pure, so the gate can be pinned:
 * every term here is one a refactor can silently drop, and dropping the guest term in particular would
 * hand a Listen Together guest a way to change the host's song.
 *
 * @param lyricsFullScreen the switch's own description scopes the gesture to the FULLSCREEN lyrics, so
 *   an open-but-inline lyrics view must not arm it.
 */
fun swipeLyricsGestureArmed(
    swipeLyricsEnabled: Boolean,
    lyricsVisible: Boolean,
    lyricsFullScreen: Boolean,
    isListenTogetherGuest: Boolean,
): Boolean =
    swipeLyricsEnabled && lyricsVisible && lyricsFullScreen && !isListenTogetherGuest

/** What a finished horizontal drag on the title/artist block means. */
enum class SwipeLyricsAction { NONE, PREVIOUS, NEXT }

/**
 * Distance → action, pure. Note the SIGN: dragging LEFT (negative travel) advances to the NEXT song,
 * matching the carousel. Anything short of the threshold is [SwipeLyricsAction.NONE] so a small
 * horizontal wobble during a tap (the block is also clickable) never changes the track.
 */
fun swipeLyricsAction(travelledPx: Float, thresholdPx: Float): SwipeLyricsAction = when {
    travelledPx <= -thresholdPx -> SwipeLyricsAction.NEXT
    travelledPx >= thresholdPx -> SwipeLyricsAction.PREVIOUS
    else -> SwipeLyricsAction.NONE
}

@Composable
fun Modifier.swipeLyricsToChangeSong(
    enabled: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
): Modifier {
    // The callbacks are fresh lambdas on every recomposition; keying `pointerInput` on them would tear
    // the gesture down and rebuild it constantly. Latch them instead and key the block on nothing.
    val previous by rememberUpdatedState(onPrevious)
    val next by rememberUpdatedState(onNext)
    if (!enabled) return this
    return this.pointerInput(Unit) {
        val threshold = 48.dp.toPx()
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            var travelled = 0f
            val drag = awaitHorizontalTouchSlopOrCancellation(down.id) { change, overSlop ->
                travelled += overSlop
                change.consume()
            } ?: return@awaitEachGesture
            horizontalDrag(drag.id) { change ->
                travelled += change.positionChange().x
                change.consume()
            }
            when (swipeLyricsAction(travelledPx = travelled, thresholdPx = threshold)) {
                SwipeLyricsAction.NEXT -> next()
                SwipeLyricsAction.PREVIOUS -> previous()
                SwipeLyricsAction.NONE -> Unit
            }
        }
    }
}

// ── "Ocultar control de volumen" (HidePlayerSliderKey) ────────────────────────────────────────────

/**
 * Reads `HidePlayerSliderKey` (default OFF).
 *
 * Despite the key's legacy name (`hidePlayerSlider`) this has never hidden the TIMELINE — it hides the
 * player's VOLUME control, which is why the Ajustes row is labelled `R.string.hide_player_volume`.
 * The classic Apple-Music transport gates its inline volume row with it; the new player has no inline
 * volume row, so it gates the volume slider at the head of its merged player menu — the one volume
 * control that shape draws.
 */
@Composable
fun rememberHidePlayerVolume(): Boolean {
    val hide by rememberPreference(HidePlayerSliderKey, defaultValue = false)
    return hide
}

/**
 * The polarity of the volume gate, pure and in ONE place.
 *
 * This exists because the key is a trap: it is stored as `hidePlayerSlider`, it does not hide a slider
 * called "player slider" (the timeline), and the row that writes it is labelled
 * `R.string.hide_player_volume`. A read site that treats it as "show" instead of "hide" — or that drops
 * the `!` in a refactor — leaves the switch doing the exact opposite of its label, which is worse than
 * the placebo it replaced. Both players ask this.
 */
fun showPlayerVolumeControl(hidePlayerVolume: Boolean): Boolean = !hidePlayerVolume
