

package iad1tya.echo.music.ui.screens

import android.app.Activity
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.compose.dialog
import androidx.navigation.navArgument
import iad1tya.echo.music.constants.PureBlackKey
import iad1tya.echo.music.ui.screens.artist.ArtistAlbumsScreen
import iad1tya.echo.music.ui.screens.artist.ArtistSongsScreen
import iad1tya.echo.music.ui.screens.library.FavoriteAlbumsScreen
import iad1tya.echo.music.ui.screens.library.ReleaseRadarScreen
import iad1tya.echo.music.ui.screens.library.LibraryScreen
import iad1tya.echo.music.ui.screens.library.LocalSongScreen
import iad1tya.echo.music.ui.screens.search.OnlineSearchResult
import iad1tya.echo.music.ui.screens.search.SearchScreen
import iad1tya.echo.music.ui.screens.settings.AboutScreen
import iad1tya.echo.music.ui.screens.settings.AccountsScreen
import iad1tya.echo.music.ui.screens.settings.FeedbackScreen
import iad1tya.echo.music.ui.screens.settings.LogsScreen
import iad1tya.echo.music.ui.screens.settings.AppearanceSettings
import iad1tya.echo.music.ui.screens.settings.BackupAndRestore
import iad1tya.echo.music.ui.screens.settings.GlassEffectSettings
import iad1tya.echo.music.ui.screens.settings.LIQUID_GLASS_ROUTE
import iad1tya.echo.music.ui.screens.settings.ContentSettings
import iad1tya.echo.music.ui.screens.settings.UptimeScreen
import iad1tya.echo.music.ui.screens.settings.PlayerSettings
import iad1tya.echo.music.ui.screens.settings.YoutubeDecryptionSettings
import iad1tya.echo.music.ui.screens.settings.PrivacySettings
import iad1tya.echo.music.ui.screens.settings.RomanizationSettings
import iad1tya.echo.music.ui.screens.settings.SettingsScreen
import iad1tya.echo.music.ui.screens.settings.StorageSettings
import iad1tya.echo.music.ui.screens.settings.ThemeScreen
import iad1tya.echo.music.ui.screens.settings.AiSettings
import iad1tya.echo.music.ui.screens.settings.integrations.ListenTogetherSettings
import iad1tya.echo.music.ui.screens.recognition.RecognitionScreen
import iad1tya.echo.music.ui.screens.recognition.RecognitionHistoryScreen
import iad1tya.echo.music.ui.screens.settings.UpdateSettings
import iad1tya.echo.music.echomusic.updater.UpdateScreen
import iad1tya.echo.music.ui.theme.rememberEffectiveDarkTheme
import iad1tya.echo.music.utils.rememberPreference
import iad1tya.echo.music.echomusic.changelog.ChangelogScreen
import iad1tya.echo.music.ui.screens.equalizer.axion.AxionEqScreen
import iad1tya.echo.music.ui.screens.ambient.AmbientModeScreen
import iad1tya.echo.music.ui.newui.AlbumScreenHost
import iad1tya.echo.music.ui.newui.ArtistScreenHost
import iad1tya.echo.music.ui.newui.AutoPlaylistScreenHost
import iad1tya.echo.music.ui.newui.CachePlaylistScreenHost
import iad1tya.echo.music.ui.newui.HomeScreenHost
import iad1tya.echo.music.ui.newui.LibraryScreenHost
import iad1tya.echo.music.ui.newui.NovedadesScreenHost
import iad1tya.echo.music.ui.newui.LocalPlaylistScreenHost
import iad1tya.echo.music.ui.newui.MigrationAppleScreenHost
import iad1tya.echo.music.ui.newui.MigrationScreenHost
import iad1tya.echo.music.ui.newui.MigrationTidalScreenHost
import iad1tya.echo.music.ui.newui.OnlinePlaylistScreenHost
import iad1tya.echo.music.ui.newui.SearchResultHost
import iad1tya.echo.music.ui.newui.SearchScreenHost
import iad1tya.echo.music.ui.newui.StatsScreenHost
import iad1tya.echo.music.ui.newui.SettingsScreenHost
import iad1tya.echo.music.ui.newui.TopPlaylistScreenHost

@OptIn(ExperimentalMaterial3Api::class)
fun NavGraphBuilder.navigationBuilder(
    navController: NavHostController,
    scrollBehavior: TopAppBarScrollBehavior,
    activity: Activity,
    snackbarHostState: SnackbarHostState
) {
    composable(Screens.Home.route) {
        // "Interfaz nueva" beta: the Host picks the new Inicio when the flag is ON and a new
        // implementation exists, and falls back to this exact classic screen otherwise.
        HomeScreenHost(navController = navController, snackbarHostState = snackbarHostState)
    }

    composable(Screens.Novedades.route) {
        NovedadesScreenHost(navController)
    }

    composable(Screens.Search.route) {
        val pureBlackEnabled by rememberPreference(PureBlackKey, defaultValue = false)
        // The APP's dark/light — see [rememberEffectiveDarkTheme]. AMOLED only paints black over a dark
        // app, and "Interfaz nueva" forces the app dark. Same term-for-term expression with the flag off.
        val useDarkTheme = rememberEffectiveDarkTheme()
        val pureBlack = remember(pureBlackEnabled, useDarkTheme) {
            pureBlackEnabled && useDarkTheme
        }
        SearchScreenHost(
            navController = navController,
            pureBlack = pureBlack
        )
    }

    composable(Screens.Library.route) {
        LibraryScreenHost(navController)
    }

    composable(Screens.ListenTogether.route) {
        ListenTogetherScreen(navController, showTopBar = false)
    }

    composable(
        route = "listen_together_from_topbar",
    ) {
        ListenTogetherScreen(navController, showTopBar = true)
    }

    composable("listen_together/chat") {
        CommentTogetherScreen(navController)
    }

    composable("history") {
        HistoryScreen(navController)
    }

    composable("local_songs") {
        LocalSongScreen(navController)
    }

    composable("favorite_albums") {
        iad1tya.echo.music.ui.newui.FavoriteAlbumsScreenHost(navController)
    }

    composable("release_radar") {
        ReleaseRadarScreen(navController)
    }

    composable("stats") {
        StatsScreenHost(navController)
    }

    composable("mood_and_genres") {
        MoodAndGenresScreen(navController, scrollBehavior)
    }

    composable("account") {
        AccountScreen(navController, scrollBehavior)
    }

    composable("new_release") {
        NewReleaseScreen(navController, scrollBehavior)
    }

    // IA hygiene (F0): "charts_screen" (ChartsScreen) de-registered — unreachable, no navigate() caller.

    composable(
        route = "browse/{browseId}",
        arguments = listOf(
            navArgument("browseId") {
                type = NavType.StringType
            }
        )
    ) {
        BrowseScreen(
            navController,
            scrollBehavior,
            it.arguments?.getString("browseId")
        )
    }

    composable(
        route = "search/{query}",
        arguments = listOf(
            navArgument("query") {
                type = NavType.StringType
            },
        ),
        enterTransition = {
            fadeIn(tween(250))
        },
        exitTransition = {
            if (targetState.destination.route?.startsWith("search/") == true) {
                fadeOut(tween(200))
            } else {
                fadeOut(tween(200)) + slideOutHorizontally { -it / 2 }
            }
        },
        popEnterTransition = {
            if (initialState.destination.route?.startsWith("search/") == true) {
                fadeIn(tween(250))
            } else {
                fadeIn(tween(250)) + slideInHorizontally { -it / 2 }
            }
        },
        popExitTransition = {
            fadeOut(tween(200))
        },
    ) {
        // Same AMOLED expression as the Buscar tab above: PureBlackKey only paints black over a dark
        // app, and "Interfaz nueva" forces the app dark. Term-for-term identical with the flag off.
        val pureBlackEnabled by rememberPreference(PureBlackKey, defaultValue = false)
        val useDarkTheme = rememberEffectiveDarkTheme()
        val pureBlack = remember(pureBlackEnabled, useDarkTheme) { pureBlackEnabled && useDarkTheme }
        SearchResultHost(navController = navController, pureBlack = pureBlack)
    }

    composable(
        route = "album/{albumId}",
        arguments = listOf(
            navArgument("albumId") {
                type = NavType.StringType
            },
        ),
    ) {
        // Reached from the NEW player's title, from Inicio, Biblioteca, Buscar and from every artist
        // and playlist screen. The Host picks the redesign when the flag is on; with the flag off this
        // is the same AlbumScreen(navController, scrollBehavior) call as before.
        AlbumScreenHost(navController, scrollBehavior)
    }

    composable(
        route = "artist/{artistId}",
        arguments = listOf(
            navArgument("artistId") {
                type = NavType.StringType
            },
        ),
    ) {
        // Reached from the NEW player's artist line, and from the player menu's "Ir a > Artista".
        ArtistScreenHost(navController, scrollBehavior)
    }

    composable(
        route = "artist/{artistId}/songs",
        arguments = listOf(
            navArgument("artistId") {
                type = NavType.StringType
            },
        ),
    ) {
        ArtistSongsScreen(navController, scrollBehavior)
    }

    composable(
        route = "artist/{artistId}/albums",
        arguments = listOf(
            navArgument("artistId") {
                type = NavType.StringType
            }
        )
    ) {
        ArtistAlbumsScreen(navController, scrollBehavior)
    }

    composable(
        route = "artist/{artistId}/items?browseId={browseId}&params={params}",
        arguments = listOf(
            navArgument("artistId") {
                type = NavType.StringType
            },
            navArgument("browseId") {
                type = NavType.StringType
                nullable = true
            },
            navArgument("params") {
                type = NavType.StringType
                nullable = true
            },
        ),
    ) {
        iad1tya.echo.music.ui.newui.ArtistItemsScreenHost(navController, scrollBehavior)
    }

    // NOT "artist/section_buffer": that collides with the "artist/{artistId}" pattern above, and
    // which one wins is decided by Navigation's best-match scoring rather than by registration
    // order — so "see all" on a section with no moreEndpoint could open ArtistScreen with
    // artistId = "section_buffer" instead of this grid. A route with no slash cannot collide.
    composable("artist_section_buffer") {
        iad1tya.echo.music.ui.newui.ArtistSectionGridScreenHost(navController, scrollBehavior)
    }

    composable(
        route = "online_playlist/{playlistId}",
        arguments = listOf(
            navArgument("playlistId") {
                type = NavType.StringType
            },
        ),
    ) {
        OnlinePlaylistScreenHost(navController, scrollBehavior)
    }

    composable(
        route = "local_playlist/{playlistId}",
        arguments = listOf(
            navArgument("playlistId") {
                type = NavType.StringType
            },
        ),
    ) {
        LocalPlaylistScreenHost(navController, scrollBehavior)
    }

    composable(
        route = "auto_playlist/{playlist}",
        arguments = listOf(
            navArgument("playlist") {
                type = NavType.StringType
            },
        ),
    ) {
        AutoPlaylistScreenHost(navController, scrollBehavior)
    }

    composable(
        route = "cache_playlist/{playlist}",
        arguments = listOf(
            navArgument("playlist") {
                type = NavType.StringType
            },
        ),
    ) {
        CachePlaylistScreenHost(navController, scrollBehavior)
    }

    composable(
        route = "top_playlist/{top}",
        arguments = listOf(
            navArgument("top") {
                type = NavType.StringType
            },
        ),
    ) {
        TopPlaylistScreenHost(navController, scrollBehavior)
    }

    composable(
        route = "youtube_browse/{browseId}?params={params}",
        arguments = listOf(
            navArgument("browseId") {
                type = NavType.StringType
                nullable = true
            },
            navArgument("params") {
                type = NavType.StringType
                nullable = true
            },
        ),
    ) {
        YouTubeBrowseScreen(navController)
    }

    composable("settings") {
        SettingsScreenHost(navController, scrollBehavior)
    }

    composable("settings/update") {
       UpdateSettings(navController, scrollBehavior)
    }

    // IA hygiene (F0): "settings/account" (AccountSettingsScreen) de-registered — orphan, no navigate() caller.

    composable("settings/accounts") {
        AccountsScreen(navController, scrollBehavior)
    }

    composable("settings/lastfm") {
        iad1tya.echo.music.ui.screens.settings.LastFMSettingsScreen(navController)
    }

    composable("settings/qobuz") {
        iad1tya.echo.music.ui.screens.settings.QobuzSettingsScreen(navController)
    }

    composable("settings/appearance") {
        AppearanceSettings(navController, scrollBehavior, activity)
    }

    composable("settings/appearance/theme") {
        ThemeScreen(navController)
    }

    // Liquid Glass: the DESTINATION is closed while "Interfaz nueva" is on, not just its doors.
    //
    // Hiding the entry row (AppearanceSettings.kt) and dropping the index entry (SearchableSettings.kt)
    // closes the two ways a user can reach it, but a route that still composes is a route a restored
    // back stack, a future caller or a deep link can still land on -- and what it composes is ~11
    // controls with no renderer behind them under this flag (the glass system's only two render sites
    // are FloatingNavigationToolbar, composed on the !newUiShell branch only, and MiniPlayer.kt, pinned
    // to DEFAULT under the flag). So the guard lives HERE, at the destination, where every path in has
    // to pass through it.
    //
    // `rememberNewUiEnabled()` is seeded by a synchronous DataStore read (utils/DataStore.kt), so it is
    // already the stored value on the FIRST composition -- the screen never flashes before the bounce.
    // navigateUp() always has somewhere to go: this route is only ever entered by navigating from
    // inside Ajustes, so it is never the start destination.
    //
    // The classic path is untouched: with the flag off the branch below composes GlassEffectSettings
    // with the same two arguments as before.
    composable(LIQUID_GLASS_ROUTE) {
        if (iad1tya.echo.music.ui.newui.rememberNewUiEnabled()) {
            LaunchedEffect(Unit) { navController.navigateUp() }
        } else {
            GlassEffectSettings(navController, scrollBehavior)
        }
    }

    composable("settings/content") {
        ContentSettings(navController, scrollBehavior)
    }

    composable("uptime") {
        UptimeScreen(navController, scrollBehavior)
    }

    composable("settings/content/romanization") {
        RomanizationSettings(navController, scrollBehavior)
    }

    composable("settings/ai") {
        AiSettings(navController, scrollBehavior)
    }
    
    composable("settings/player") {
        PlayerSettings(navController, scrollBehavior)
    }

    composable("settings/youtube_decryption") {
        YoutubeDecryptionSettings(navController, scrollBehavior)
    }

    composable("settings/sound") {
        iad1tya.echo.music.ui.screens.settings.SoundSettings(navController, scrollBehavior)
    }

    composable("settings/performance") {
        iad1tya.echo.music.ui.screens.settings.PerformanceSettings(navController, scrollBehavior)
    }

    composable("settings/sound/autoeq") {
        iad1tya.echo.music.ui.screens.equalizer.autoeq.AutoEqScreen(navController)
    }

    composable(
        route = "settings/storage?autoOpenExportPicker={autoOpenExportPicker}",
        arguments = listOf(
            navArgument("autoOpenExportPicker") {
                type = NavType.BoolType
                defaultValue = false
            }
        )
    ) { backStackEntry ->
        val autoOpenExportPicker =
            backStackEntry.arguments?.getBoolean("autoOpenExportPicker") ?: false
        StorageSettings(
            navController = navController,
            scrollBehavior = scrollBehavior,
            autoOpenExportPicker = autoOpenExportPicker,
        )
    }

    composable("settings/equalizer") {
        AxionEqScreen(
            onBackClick = { navController.navigateUp() },
            onAutoEqClick = { navController.navigate("settings/sound/autoeq") },
        )
    }

    composable("settings/privacy") {
        PrivacySettings(navController, scrollBehavior)
    }

    composable("settings/backup_restore") {
        BackupAndRestore(navController, scrollBehavior)
    }

    // IA hygiene: "settings/integrations" (IntegrationScreen) removed — its Column body was empty, so the
    // screen only ever drew a top bar over nothing. No navigate() caller either.
    // Note: the live sub-route "settings/integrations/listen_together" below is unaffected and stays registered.

    composable(
        route = "settings/spotify_import?onboarding={onboarding}&link={link}",
        arguments = listOf(
            navArgument("onboarding") { type = NavType.BoolType; defaultValue = false },
            navArgument("link") { type = NavType.StringType; nullable = true; defaultValue = null },
        ),
    ) { backStackEntry ->
        SpotifyImportScreen(
            navController,
            onboarding = backStackEntry.arguments?.getBoolean("onboarding") ?: false,
            initialLink = backStackEntry.arguments?.getString("link"),
        )
    }

    // Playlist migration (Tidal / Deezer / archivo / Apple → YouTube Music). Entry point lives in the
    // Library "Importar" menu, next to the Spotify/YouTube import options.
    composable("migration") {
        MigrationScreenHost(navController)
    }

    composable("migration/tidal") {
        MigrationTidalScreenHost(navController)
    }

    composable("migration/apple") {
        MigrationAppleScreenHost(navController)
    }

    composable(
        route = "settings/ytm_sync?onboarding={onboarding}",
        arguments = listOf(navArgument("onboarding") { type = NavType.BoolType; defaultValue = false }),
    ) { backStackEntry ->
        iad1tya.echo.music.ui.screens.settings.YtmSyncScreen(
            navController,
            scrollBehavior,
            onboarding = backStackEntry.arguments?.getBoolean("onboarding") ?: false,
        )
    }

    composable(route = "settings/integrations/listen_together") {
        ListenTogetherSettings(navController, scrollBehavior)
    }

    composable("settings/about") {
        AboutScreen(navController, scrollBehavior)
    }

    composable("settings/feedback") {
        FeedbackScreen(navController, scrollBehavior)
    }

    composable("settings/terms") {
        iad1tya.echo.music.legal.TermsScreen(navController, scrollBehavior)
    }

    composable("settings/logs") {
        LogsScreen(navController, scrollBehavior)
    }

    composable("update") {
        UpdateScreen(navController)
    }

    composable("login") {
        LoginScreen(navController)
    }

    composable("onboarding_artists") {
        OnboardingArtistsScreen(navController)
    }

    composable("onboarding_genres") {
        OnboardingGenresScreen(navController)
    }

    composable("onboarding_spotify") {
        OnboardingSpotifyScreen(navController)
    }

    composable("onboarding_youtube") {
        OnboardingYouTubeScreen(navController)
    }

    composable(
        "podcasts?feedUrl={feedUrl}",
        arguments = listOf(
            androidx.navigation.navArgument("feedUrl") {
                type = androidx.navigation.NavType.StringType
                nullable = true
                defaultValue = null
            },
        ),
    ) {
        PodcastScreen(navController)
    }

    composable(
        "recognition?autoStart={autoStart}",
        arguments = listOf(
            androidx.navigation.navArgument("autoStart") {
                type = androidx.navigation.NavType.BoolType
                defaultValue = false
            },
        ),
    ) { backStackEntry ->
        RecognitionScreen(
            navController,
            autoStart = backStackEntry.arguments?.getBoolean("autoStart") == true,
        )
    }

    composable("recognition_history") {
        RecognitionHistoryScreen(navController)
    }
    composable("settings/changelog") {
        ChangelogScreen(navController,scrollBehavior)
    }
    composable("settings/notices") {
        iad1tya.echo.music.ui.newui.AuraNoticesScreen(navController)
    }
    // IA hygiene: "settings/commits" (CommitScreen) removed — it had no navigate() caller AND it fetched
    // the commit list of the UPSTREAM repo (github.com/EchoMusicApp/Echo-Music), an Aura branding leak.
    // The live, Aura-owned equivalent is "settings/changelog" (ChangelogScreen) above.

    // 0.6.92 Modo Ambiente — full-screen, landscape, lean-back now-playing view.
    composable("ambient_mode") {
        AmbientModeScreen(navController)
    }
}
