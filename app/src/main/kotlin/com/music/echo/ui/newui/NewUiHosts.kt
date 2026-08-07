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
import iad1tya.echo.music.ui.screens.AlbumScreen
import iad1tya.echo.music.ui.screens.HomeScreen
import iad1tya.echo.music.ui.screens.StatsScreen
import iad1tya.echo.music.ui.screens.artist.ArtistScreen
import iad1tya.echo.music.ui.screens.library.LibraryScreen
import iad1tya.echo.music.ui.screens.migration.MigrationAppleScreen
import iad1tya.echo.music.ui.screens.migration.MigrationScreen
import iad1tya.echo.music.ui.screens.migration.MigrationTidalScreen
import iad1tya.echo.music.ui.screens.playlist.AutoPlaylistScreen
import iad1tya.echo.music.ui.screens.playlist.CachePlaylistScreen
import iad1tya.echo.music.ui.screens.playlist.LocalPlaylistScreen
import iad1tya.echo.music.ui.screens.playlist.OnlinePlaylistScreen
import iad1tya.echo.music.ui.screens.playlist.TopPlaylistScreen
import iad1tya.echo.music.ui.screens.search.OnlineSearchResult
import iad1tya.echo.music.ui.screens.search.SearchScreen
import iad1tya.echo.music.ui.screens.settings.SettingsScreen

/**
 * # The gated surfaces of the "Interfaz nueva" beta
 *
 * Sixteen routes plus the three non-route surfaces (player, player menu, queue) as of this file's
 * section 12. Count the `NewUiGate` calls below rather than trusting this sentence.
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

// ── 7. Buscar ─────────────────────────────────────────────────────────────────────────────────────

/**
 * The Buscar TAB (history + suggestions + the input bar), not the results.
 *
 * This one was reported by the owner — "cuando toco el boton de inicio y paso al de buscar no me sale
 * personalizado" — and he was right: it had no host at all, so the tab always rendered classic. It is a
 * bottom-bar destination, i.e. one of the four most-visited screens in the app.
 */
@Composable
fun SearchScreenHost(
    navController: NavController,
    pureBlack: Boolean,
) {
    NewUiGate(
        classic = { SearchScreen(navController = navController, pureBlack = pureBlack) },
        new = { AuraSearchScreen(navController = navController, pureBlack = pureBlack) },
    )
}

/**
 * The RESULTS screen (`search/{query}`). Separate from the tab above because they are separate classic
 * screens with separate ViewModels — gating them together would have meant one of the two silently
 * keeping the other's state.
 */
@Composable
fun SearchResultHost(
    navController: NavController,
    pureBlack: Boolean,
) {
    NewUiGate(
        classic = { OnlineSearchResult(navController) },
        new = { AuraSearchResultScreen(navController = navController, pureBlack = pureBlack) },
    )
}

// ── 8. Estadísticas ───────────────────────────────────────────────────────────────────────────────

@Composable
fun StatsScreenHost(navController: NavController) {
    NewUiGate(
        classic = { StatsScreen(navController) },
        new = { AuraStatsScreen(navController) },
    )
}

// ── 9. Migrar lista ───────────────────────────────────────────────────────────────────────────────

/**
 * The migration wizard and its two per-source screens. Three hosts rather than one because each is a
 * separately registered route — the source picker can be left without entering a source screen, and
 * Tidal/Apple are reachable directly from a deep link.
 */
@Composable
fun MigrationScreenHost(navController: NavController) {
    NewUiGate(
        classic = { MigrationScreen(navController) },
        new = { AuraMigrationScreen(navController) },
    )
}

@Composable
fun MigrationTidalScreenHost(navController: NavController) {
    NewUiGate(
        classic = { MigrationTidalScreen(navController) },
        new = { AuraMigrationTidalScreen(navController) },
    )
}

@Composable
fun MigrationAppleScreenHost(navController: NavController) {
    NewUiGate(
        classic = { MigrationAppleScreen(navController) },
        new = { AuraMigrationAppleScreen(navController) },
    )
}

// ── 10. Auto-listas (Descargadas, Me gusta, Más escuchadas, En caché, Subidas) ─────────────────────

/**
 * ONE host covers all six auto-playlists: they are a single classic screen parameterised by the
 * `{playlist}` route argument, so "Descargados" — the one the owner asked for — comes with the other
 * five for free. Do not split this into six.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AutoPlaylistScreenHost(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
) {
    NewUiGate(
        classic = { AutoPlaylistScreen(navController, scrollBehavior) },
        new = { AuraAutoPlaylistScreen(navController, scrollBehavior) },
    )
}

// ── 11. Álbum y Artista ───────────────────────────────────────────────────────────────────────────

/**
 * The two destinations the NEW player itself opens: the track title goes to `album/{albumId}` and the
 * artist line to `artist/{artistId}`. Until these hosts existed, tapping either from the redesigned
 * player dropped the user onto a classic-layout screen — the most visible seam in the beta.
 *
 * `scrollBehavior` is forwarded to both branches because both take it. The new screens accept it and
 * draw no Material `TopAppBar`, so nothing consumes it — which is parity, not a drop: `AlbumScreen`
 * and `ArtistScreen` take the same parameter and never hand it to their top bar either.
 *
 * The `viewModel` parameter stays DEFAULTED on both sides. Hoisting it into the host and passing it
 * down would keep the other skin's instance alive; leaving it defaulted lets `hiltViewModel()` resolve
 * against the same `NavBackStackEntry` the classic screen would have used.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlbumScreenHost(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
) {
    NewUiGate(
        classic = { AlbumScreen(navController, scrollBehavior) },
        new = { AuraAlbumScreen(navController, scrollBehavior) },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArtistScreenHost(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
) {
    NewUiGate(
        classic = { ArtistScreen(navController, scrollBehavior) },
        new = { AuraArtistScreen(navController, scrollBehavior) },
    )
}

// ── 12. Listas (en caché, más escuchadas, propias, de YouTube) ─────────────────────────────────────

/**
 * The four remaining playlist destinations. They are four separate hosts, not one, because they are
 * four separate classic screens with four separate ViewModels — unlike the six auto-playlists above,
 * which really are one parameterised screen.
 *
 * Same `viewModel`-stays-defaulted rule as section 11.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CachePlaylistScreenHost(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
) {
    NewUiGate(
        classic = { CachePlaylistScreen(navController, scrollBehavior) },
        new = { AuraCachePlaylistScreen(navController, scrollBehavior) },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TopPlaylistScreenHost(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
) {
    NewUiGate(
        classic = { TopPlaylistScreen(navController, scrollBehavior) },
        new = { AuraTopPlaylistScreen(navController, scrollBehavior) },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocalPlaylistScreenHost(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
) {
    NewUiGate(
        classic = { LocalPlaylistScreen(navController, scrollBehavior) },
        new = { AuraLocalPlaylistScreen(navController, scrollBehavior) },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnlinePlaylistScreenHost(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
) {
    NewUiGate(
        classic = { OnlinePlaylistScreen(navController, scrollBehavior) },
        new = { AuraOnlinePlaylistScreen(navController, scrollBehavior) },
    )
}
