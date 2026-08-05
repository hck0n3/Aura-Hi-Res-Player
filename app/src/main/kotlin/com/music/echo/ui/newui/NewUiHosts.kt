package iad1tya.echo.music.ui.newui

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.navigation.NavController
import iad1tya.echo.music.constants.PlayerBackgroundStyle
import iad1tya.echo.music.models.MediaMetadata
import iad1tya.echo.music.ui.component.BottomSheetState
import iad1tya.echo.music.ui.menu.OldPlayerMenu
import iad1tya.echo.music.ui.player.BottomSheetPlayer
import iad1tya.echo.music.ui.player.Queue
import iad1tya.echo.music.ui.screens.HomeScreen
import iad1tya.echo.music.ui.screens.library.LibraryScreen
import iad1tya.echo.music.ui.screens.settings.SettingsScreen

/**
 * # The six gated surfaces of the "Interfaz nueva" beta
 *
 * **This is the only file a screen agent edits to plug a new screen in.** Every call site in the app
 * (NavigationBuilder, MainActivity, the new player) points at a `*Host` below; each host asks
 * [NewUiGate] for the flag and falls back to the classic screen whenever `new` is still `null`.
 *
 * ## How to plug your screen in
 * 1. Build your screen somewhere in `ui/newui/` with the SAME parameters as the classic one.
 * 2. Replace the `new = null` of your host with `new = { YourScreen(...) }`.
 * 3. Change nothing else. Do not touch the classic screen, do not touch the call sites.
 *
 * ## Hard rules (from the order)
 *  · **Presentation only.** Your screen must call the same ViewModel / repository / action lambda the
 *    classic screen calls. No duplicated business logic — one copy, so one fix.
 *  · **No silent drops.** Before you flip a `null`, walk your screen's section of `docs/UI_INVENTORY.md`
 *    and account for every control. Anything you cannot place gets reported, not deleted.
 *  · **Toggling is inert.** Nothing here may write to the database, the queue, playback or another
 *    preference. Flipping the switch twice must land back on identical classic behaviour.
 */

// ── 1. Reproductor ────────────────────────────────────────────────────────────────────────────────

/**
 * Full player + mini player (they are the same composable at two sheet positions).
 *
 * Reminder from the inventory: the player is already THIRTEEN shapes (`useNewPlayerDesign`, the mini
 * player design, four progress-bar implementations, audio/video/canvas, orientation, device class).
 * The new player is ONE MORE shape, selected here — it does not replace any of the thirteen.
 *
 * Non-negotiable content for the new shape: the artwork area hosts **video** (canvas is content, the
 * owner demanded it), the five quick-access icons sit under the transport, the engine status bar
 * (EQ / V.SEGURO / dBFS) is pinned to the bottom, and ~55 undocumented gestures survive — including
 * the DESTRUCTIVE one: dragging the sheet down past the threshold stops playback and wipes the queue.
 */
@Composable
fun BottomSheetPlayerHost(
    state: BottomSheetState,
    navController: NavController,
    pureBlack: Boolean,
    modifier: Modifier = Modifier,
) {
    NewUiGate(
        classic = {
            BottomSheetPlayer(
                state = state,
                navController = navController,
                modifier = modifier,
                pureBlack = pureBlack,
            )
        },
        new = {
            AuraPlayer(
                state = state,
                navController = navController,
                pureBlack = pureBlack,
                modifier = modifier,
            )
        },
    )
}

// ── 2. Menú del reproductor ───────────────────────────────────────────────────────────────────────

/**
 * The merged player menu (REPRODUCCIÓN / BIBLIOTECA / IR A / MÁS).
 *
 * **Two live menus exist today and they do NOT contain the same entries.** `OldPlayerMenu` is the real
 * player menu; `PlayerMenu` is reachable ONLY from the Queue and is the ONLY place that has *Modo
 * ambiente*, *Ecualizador* and *Velocidad y tono*. The render merges them, so the new menu must carry
 * entries from BOTH or those three vanish. The audio-output picker is likewise reachable only from the
 * Queue in the old player design — carry it too.
 *
 * The classic fallback is `OldPlayerMenu`, i.e. exactly what the player opens today.
 */
@Composable
fun PlayerMenuHost(
    mediaMetadata: MediaMetadata?,
    navController: NavController,
    playerBottomSheetState: BottomSheetState,
    onShowDetailsDialog: () -> Unit,
    onDismiss: () -> Unit,
    onSleepTimer: () -> Unit = {},
) {
    NewUiGate(
        classic = {
            OldPlayerMenu(
                mediaMetadata = mediaMetadata,
                navController = navController,
                playerBottomSheetState = playerBottomSheetState,
                onShowDetailsDialog = onShowDetailsDialog,
                onSleepTimer = onSleepTimer,
                onDismiss = onDismiss,
            )
        },
        new = {
            AuraPlayerMenu(
                mediaMetadata = mediaMetadata,
                navController = navController,
                playerBottomSheetState = playerBottomSheetState,
                onShowDetailsDialog = onShowDetailsDialog,
                onDismiss = onDismiss,
                onSleepTimer = onSleepTimer,
            )
        },
    )
}

// ── 3. Cola ───────────────────────────────────────────────────────────────────────────────────────

/**
 * The queue sheet: "tu lista" and the infinite radio visually separated, a visible drag handle on
 * every row, swipe-to-remove with Undo, drag-to-reorder, and the audio-output picker.
 */
@Composable
fun QueueHost(
    state: BottomSheetState,
    playerBottomSheetState: BottomSheetState,
    navController: NavController,
    background: Color,
    onBackgroundColor: Color,
    textBackgroundColor: Color,
    textButtonColor: Color,
    iconButtonColor: Color,
    pureBlack: Boolean,
    showInlineLyrics: Boolean,
    modifier: Modifier = Modifier,
    playerBackground: PlayerBackgroundStyle = PlayerBackgroundStyle.DEFAULT,
    onToggleLyrics: () -> Unit = {},
) {
    NewUiGate(
        classic = {
            Queue(
                state = state,
                playerBottomSheetState = playerBottomSheetState,
                navController = navController,
                modifier = modifier,
                background = background,
                onBackgroundColor = onBackgroundColor,
                TextBackgroundColor = textBackgroundColor,
                textButtonColor = textButtonColor,
                iconButtonColor = iconButtonColor,
                pureBlack = pureBlack,
                showInlineLyrics = showInlineLyrics,
                playerBackground = playerBackground,
                onToggleLyrics = onToggleLyrics,
            )
        },
        new = {
            AuraQueue(
                state = state,
                playerBottomSheetState = playerBottomSheetState,
                navController = navController,
                modifier = modifier,
                background = background,
                onBackgroundColor = onBackgroundColor,
                textBackgroundColor = textBackgroundColor,
                textButtonColor = textButtonColor,
                iconButtonColor = iconButtonColor,
                pureBlack = pureBlack,
                showInlineLyrics = showInlineLyrics,
                playerBackground = playerBackground,
                onToggleLyrics = onToggleLyrics,
            )
        },
    )
}

// ── 4. Inicio ─────────────────────────────────────────────────────────────────────────────────────

@Composable
fun HomeScreenHost(
    navController: NavController,
    snackbarHostState: SnackbarHostState,
) {
    NewUiGate(
        classic = { HomeScreen(navController = navController, snackbarHostState = snackbarHostState) },
        new = {
            AuraHomeScreen(navController = navController, snackbarHostState = snackbarHostState)
        },
    )
}

// ── 5. Biblioteca ─────────────────────────────────────────────────────────────────────────────────

@Composable
fun LibraryScreenHost(navController: NavController) {
    NewUiGate(
        classic = { LibraryScreen(navController) },
        new = { AuraLibraryScreen(navController) },
    )
}

// ── 6. Ajustes ────────────────────────────────────────────────────────────────────────────────────

/**
 * The settings index. Whatever replaces it MUST keep the "Interfaz nueva" switch on screen — it is the
 * only way back to the classic UI, and a new settings screen that loses it traps the user in the beta.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreenHost(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
) {
    NewUiGate(
        classic = { SettingsScreen(navController, scrollBehavior) },
        new = { AuraSettingsScreen(navController, scrollBehavior) },
    )
}
