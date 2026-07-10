

package iad1tya.echo.music
import iad1tya.echo.music.ui.screens.settings.RingtoneViewModel
import iad1tya.echo.music.ui.component.RingtoneTrimmerDialog
import iad1tya.echo.music.ui.component.RingtoneProgressDialog
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue


import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.IntentFilter
import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.view.View
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.draw.blur
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.animateLottieCompositionAsState
import com.airbnb.lottie.compose.rememberLottieComposition
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.util.fastAny
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.core.util.Consumer
import androidx.core.view.WindowCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.coroutineScope
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import coil3.compose.AsyncImage
import coil3.imageLoader
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.request.crossfade
import coil3.toBitmap
import com.music.innertube.YouTube
import com.music.innertube.models.SongItem
import com.music.innertube.models.WatchEndpoint
import iad1tya.echo.music.constants.AppBarHeight
import iad1tya.echo.music.constants.AppLanguageKey
import iad1tya.echo.music.constants.DarkModeKey
import iad1tya.echo.music.constants.DefaultOpenTabKey
import iad1tya.echo.music.constants.DisableScreenshotKey
import iad1tya.echo.music.constants.DynamicThemeKey
import iad1tya.echo.music.constants.EnableHighRefreshRateKey
import iad1tya.echo.music.constants.FloatingToolbarBottomPadding
import iad1tya.echo.music.constants.FloatingToolbarHorizontalPadding
import iad1tya.echo.music.constants.ListenTogetherInTopBarKey
import iad1tya.echo.music.constants.ListenTogetherUsernameKey
import iad1tya.echo.music.constants.MiniPlayerBottomSpacing
import iad1tya.echo.music.constants.MiniPlayerHeight
import iad1tya.echo.music.constants.NavigationBarAnimationSpec
import iad1tya.echo.music.constants.NavigationBarHeight
import iad1tya.echo.music.echomusic.updater.checkForUpdate
import iad1tya.echo.music.echomusic.updater.getAutoUpdateCheckSetting
import iad1tya.echo.music.echomusic.updater.isNewerVersion
import iad1tya.echo.music.echomusic.updater.saveUpdateAvailableState
import iad1tya.echo.music.echomusic.updater.getUpdateNotificationsSetting
import iad1tya.echo.music.echomusic.UpdateNotificationHelper
import android.util.Log
import androidx.compose.ui.platform.LocalContext
import iad1tya.echo.music.constants.OfflineModeKey
import iad1tya.echo.music.constants.PauseListenHistoryKey
import iad1tya.echo.music.constants.PauseSearchHistoryKey
import iad1tya.echo.music.constants.PureBlackKey
import iad1tya.echo.music.constants.SYSTEM_DEFAULT
import iad1tya.echo.music.constants.SelectedThemeColorKey
import iad1tya.echo.music.constants.ShowNowPlayingPanelKey
import iad1tya.echo.music.constants.StopMusicOnTaskClearKey
import iad1tya.echo.music.constants.UseNewMiniPlayerDesignKey
import iad1tya.echo.music.db.MusicDatabase
import iad1tya.echo.music.db.entities.SearchHistory
import iad1tya.echo.music.extensions.toEnum
import iad1tya.echo.music.models.toMediaMetadata
import iad1tya.echo.music.playback.DownloadUtil
import iad1tya.echo.music.playback.MusicService
import iad1tya.echo.music.playback.MusicService.MusicBinder
import iad1tya.echo.music.playback.PlayerConnection
import iad1tya.echo.music.playback.queues.YouTubeQueue
import iad1tya.echo.music.recognition.RecognitionForegroundService
import iad1tya.echo.music.ui.component.AppNavigationRail
import iad1tya.echo.music.ui.component.BottomSheetMenu
import iad1tya.echo.music.ui.component.BottomSheetPage
import iad1tya.echo.music.ui.component.FloatingNavigationToolbar
import iad1tya.echo.music.ui.component.GlassEffectConfig
import iad1tya.echo.music.ui.component.LocalAppBackdrop
import iad1tya.echo.music.ui.component.LocalGlassEffectConfig
import iad1tya.echo.music.ui.component.backdrop.backdrops.layerBackdrop
import iad1tya.echo.music.ui.component.backdrop.backdrops.rememberLayerBackdrop
import iad1tya.echo.music.ui.component.isGlassEligible
import iad1tya.echo.music.constants.LiquidGlassBlurRadiusKey
import iad1tya.echo.music.constants.LiquidGlassChromaticAberrationKey
import iad1tya.echo.music.constants.LiquidGlassDepthEffectKey
import iad1tya.echo.music.constants.LiquidGlassGlobalEnabledKey
import iad1tya.echo.music.constants.LiquidGlassLensAmountKey
import iad1tya.echo.music.constants.LiquidGlassLensHeightKey
import iad1tya.echo.music.constants.LiquidGlassMiniPlayerEnabledKey
import iad1tya.echo.music.constants.LiquidGlassNavBarEnabledKey
import iad1tya.echo.music.constants.LiquidGlassPlayerEnabledKey
import iad1tya.echo.music.constants.LiquidGlassSurfaceOpacityKey
import iad1tya.echo.music.constants.LiquidGlassSurfaceTintColorKey
import iad1tya.echo.music.constants.LiquidGlassTextColorKey
import iad1tya.echo.music.constants.LiquidGlassVibrancyKey
import iad1tya.echo.music.ui.component.LocalBottomSheetPageState
import iad1tya.echo.music.ui.component.LocalMenuState
import iad1tya.echo.music.ui.component.rememberBottomSheetState
import iad1tya.echo.music.ui.component.shimmer.ShimmerTheme
import iad1tya.echo.music.ui.menu.YouTubeSongMenu
import iad1tya.echo.music.ui.player.BottomSheetPlayer
import iad1tya.echo.music.ui.player.NowPlayingSidePanel
import iad1tya.echo.music.ui.screens.Screens
import iad1tya.echo.music.ui.screens.SettingDialoge
import iad1tya.echo.music.license.LicenseGate
import iad1tya.echo.music.ui.screens.WelcomeDialog
import iad1tya.echo.music.ui.screens.navigationBuilder
import iad1tya.echo.music.ui.screens.settings.DarkMode
import iad1tya.echo.music.ui.screens.settings.NavigationTab
import iad1tya.echo.music.ui.theme.BrandAccent
import iad1tya.echo.music.ui.theme.ColorSaver
import iad1tya.echo.music.ui.theme.DefaultThemeColor
import iad1tya.echo.music.ui.theme.echomusicTheme
import iad1tya.echo.music.ui.theme.extractThemeColor
import iad1tya.echo.music.ui.utils.appBarScrollBehavior
import iad1tya.echo.music.ui.utils.resetHeightOffset
import iad1tya.echo.music.utils.SyncUtils
import iad1tya.echo.music.utils.dataStore
import iad1tya.echo.music.utils.get
import iad1tya.echo.music.utils.rememberEnumPreference
import iad1tya.echo.music.constants.ForceSplitViewKey
import iad1tya.echo.music.constants.SidePanelOnLeftKey
import iad1tya.echo.music.utils.rememberPreference
import iad1tya.echo.music.utils.reportException
import android.content.Context
import iad1tya.echo.music.utils.localeAwareContext
import iad1tya.echo.music.viewmodels.HomeViewModel
import com.valentinilk.shimmer.LocalShimmerTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.net.URLDecoder
import java.net.URLEncoder
import java.util.Locale
import javax.inject.Inject

@Suppress("DEPRECATION", "ASSIGNED_BUT_NEVER_ACCESSED_VARIABLE")
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    companion object {
        private const val ACTION_SEARCH = "iad1tya.echo.music.action.SEARCH"
        private const val ACTION_LIBRARY = "iad1tya.echo.music.action.LIBRARY"
        // Quick Settings recognition tile opens the app straight to the Recognition screen.
        const val ACTION_RECOGNITION = "iad1tya.echo.music.action.RECOGNITION"
        // With this extra the Recognition screen starts listening immediately (no second tap needed) —
        // used by the tile/widget flow when the mic permission still has to be requested in-app.
        const val EXTRA_AUTO_START_RECOGNITION = "auto_start_recognition"
        // Picture-in-Picture playback controls (system RemoteActions shown when the PiP window is tapped).
        const val PIP_ACTION = "iad1tya.echo.music.action.PIP"
        const val PIP_CONTROL = "control"
    }

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(localeAwareContext(newBase))
    }

    @Inject
    lateinit var database: MusicDatabase

    @Inject
    lateinit var downloadUtil: DownloadUtil

    @Inject
    lateinit var syncUtils: SyncUtils

    @Inject
    lateinit var listenTogetherManager: iad1tya.echo.music.listentogether.ListenTogetherManager

    private lateinit var navController: NavHostController
    private var pendingIntent: Intent? = null

    private var playerConnection by mutableStateOf<PlayerConnection?>(null)
    // True while the app is in Android Picture-in-Picture mode (video floating window).
    private var inPipMode by mutableStateOf(false)
    private var pipPlayPauseJob: kotlinx.coroutines.Job? = null
    // Receives the PiP RemoteAction taps (play/pause, next, prev) and drives the player.
    private val pipReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val pc = playerConnection ?: return
            when (intent?.getStringExtra(PIP_CONTROL)) {
                "playpause" -> if (pc.player.isPlaying) pc.player.pause() else pc.player.play()
                "next" -> pc.player.seekToNext()
                "prev" -> pc.player.seekToPrevious()
            }
        }
    }

    // Tracks whether serviceConnection is currently registered, so we never double-unbind
    // (double unbindService with the same connection throws IllegalArgumentException).
    private var isServiceBound = false

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            if (service is MusicBinder) {
                try {
                    playerConnection = PlayerConnection(this@MainActivity, service, database, lifecycleScope)
                    Timber.tag("MainActivity").d("PlayerConnection created successfully")
                    
                    listenTogetherManager.setPlayerConnection(playerConnection)
                } catch (e: Exception) {
                    Timber.tag("MainActivity").e(e, "Failed to create PlayerConnection")
                    
                    lifecycleScope.launch {
                        delay(500)
                        try {
                            playerConnection = PlayerConnection(this@MainActivity, service, database, lifecycleScope)
                            listenTogetherManager.setPlayerConnection(playerConnection)
                        } catch (e2: Exception) {
                            Timber.tag("MainActivity").e(e2, "Failed to create PlayerConnection on retry")
                        }
                    }
                }
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            
            listenTogetherManager.setPlayerConnection(null)
            playerConnection?.dispose()
            playerConnection = null
        }
    }

    override fun onStart() {
        super.onStart()
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1000)
            }
        }

        
        
        
        bindService(
            Intent(this, MusicService::class.java),
            serviceConnection,
            BIND_AUTO_CREATE
        )
        isServiceBound = true
    }

    override fun onStop() {
        if (isServiceBound) {
            runCatching { unbindService(serviceConnection) }
            isServiceBound = false
        }
        super.onStop()
    }

    override fun onDestroy() {
        super.onDestroy()
        runCatching { unregisterReceiver(pipReceiver) }
        pipPlayPauseJob?.cancel()
        if (dataStore.get(StopMusicOnTaskClearKey, false) &&
            playerConnection?.isPlaying?.value == true &&
            isFinishing
        ) {
            stopService(Intent(this, MusicService::class.java))
            if (isServiceBound) {
                runCatching { unbindService(serviceConnection) }
                isServiceBound = false
            }
            playerConnection = null
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (::navController.isInitialized) {
            handleDeepLinkIntent(intent, navController)
        } else {
            pendingIntent = intent
        }
    }

    // Picture-in-Picture: when the user leaves the app while a video is playing, float it in a PiP window
    // so the video keeps showing. (Audio already keeps playing via the foreground service regardless.)
    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (playerConnection?.videoMode?.value == true && playerConnection?.isPlaying?.value == true) {
            enterPipModeIfVideo()
        }
    }

    private fun enterPipModeIfVideo() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val pc = playerConnection ?: return
        if (pc.videoMode.value != true) return
        val builder = android.app.PictureInPictureParams.Builder()
        runCatching {
            val size = pc.player.videoSize
            if (size.width > 0 && size.height > 0) {
                val ratio = (size.width.toFloat() / size.height).coerceIn(0.45f, 2.3f)
                builder.setAspectRatio(android.util.Rational((ratio * 1000f).toInt(), 1000))
            }
        }
        runCatching { builder.setActions(buildPipActions()) }
        runCatching { enterPictureInPictureMode(builder.build()) }
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: android.content.res.Configuration,
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        inPipMode = isInPictureInPictureMode
        if (isInPictureInPictureMode) {
            updatePipActions()
            // Keep the play/pause action icon in sync with playback while floating.
            pipPlayPauseJob?.cancel()
            pipPlayPauseJob = lifecycleScope.launch {
                playerConnection?.isPlaying?.collect { updatePipActions() }
            }
        } else {
            pipPlayPauseJob?.cancel()
            pipPlayPauseJob = null
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun buildPipActions(): ArrayList<android.app.RemoteAction> {
        val playing = playerConnection?.isPlaying?.value == true
        fun act(iconRes: Int, title: String, control: String, req: Int): android.app.RemoteAction {
            val pi = PendingIntent.getBroadcast(
                this, req,
                Intent(PIP_ACTION).setPackage(packageName).putExtra(PIP_CONTROL, control),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            return android.app.RemoteAction(
                android.graphics.drawable.Icon.createWithResource(this, iconRes), title, title, pi,
            )
        }
        return arrayListOf(
            act(iad1tya.echo.music.R.drawable.skip_previous, "Anterior", "prev", 11),
            act(
                if (playing) iad1tya.echo.music.R.drawable.pause else iad1tya.echo.music.R.drawable.play,
                "Reproducir o pausar", "playpause", 12,
            ),
            act(iad1tya.echo.music.R.drawable.skip_next, "Siguiente", "next", 13),
        )
    }

    private fun updatePipActions() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O || !inPipMode) return
        runCatching {
            setPictureInPictureParams(
                android.app.PictureInPictureParams.Builder().setActions(buildPipActions()).build()
            )
        }
    }

    // Safety net for touch dispatch: some OEM input pipelines (notably Xiaomi's "Mirror") can drive
    // Compose's pointer dispatch into a rare NullPointerException deep in obfuscated framework code,
    // which would otherwise take down the whole app on a single tap. Dropping one stray touch is far
    // better than crashing the session, so we log and continue instead of propagating.
    override fun dispatchTouchEvent(ev: android.view.MotionEvent): Boolean =
        try {
            super.dispatchTouchEvent(ev)
        } catch (e: Exception) {
            timber.log.Timber.e(e, "Swallowed touch-dispatch exception")
            false
        }

    @SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        ContextCompat.registerReceiver(
            this, pipReceiver, IntentFilter(PIP_ACTION), ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        window.decorView.layoutDirection = View.LAYOUT_DIRECTION_LTR
        WindowCompat.setDecorFitsSystemWindows(window, false)

        
        listenTogetherManager.initialize()

        // App language (device/system locale by default) is applied for all API levels in attachBaseContext().

        lifecycleScope.launch {
            dataStore.data
                .map { it[DisableScreenshotKey] ?: false }
                .distinctUntilChanged()
                .collectLatest {
                    if (it) {
                        window.setFlags(
                            WindowManager.LayoutParams.FLAG_SECURE,
                            WindowManager.LayoutParams.FLAG_SECURE,
                        )
                    } else {
                        window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                    }
                }
        }

        setContent {
            // Terms & Conditions gate OUTSIDE the license gate: until the user accepts the current
            // TermsInfo.TERMS_VERSION, the acceptance screen renders INSTEAD of everything else
            // (license flow included, untouched behind it). Re-appears only when the version bumps.
            iad1tya.echo.music.legal.TermsGate {
                LicenseGate {
                    echomusicApp(
                        playerConnection = playerConnection,
                        database = database,
                        downloadUtil = downloadUtil,
                        syncUtils = syncUtils,
                    )
                }
            }
        }
    }

    @SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
    @OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
    @Composable
    private fun echomusicApp(
        playerConnection: PlayerConnection?,
        database: MusicDatabase,
        downloadUtil: DownloadUtil,
        syncUtils: SyncUtils,
    ) {
        val enableDynamicTheme by rememberPreference(DynamicThemeKey, defaultValue = true)
        val enableHighRefreshRateRaw by rememberPreference(EnableHighRefreshRateKey, defaultValue = true)
        // High-Performance Mode keeps weak/TV/car panels at 60 Hz (skip forcing the highest refresh mode).
        // Read reactively (NOT the blocking PerformanceMode.isOn) so it never blocks the main thread on recompose.
        val highPerfMode by rememberPreference(iad1tya.echo.music.constants.HighPerformanceModeKey, defaultValue = false)
        val enableHighRefreshRate = enableHighRefreshRateRaw && !highPerfMode
        val context = LocalContext.current
        // NOTE: do NOT tear down / early-return the whole app UI when entering PiP — rebuilding the entire
        // NavHost on every app-switch froze the app for seconds (and could pause playback). PiP simply shows
        // the current content (the video player) in the floating window; no special teardown.

        // Updates are manual: no automatic update check on startup.

        LaunchedEffect(enableHighRefreshRate) {
            val window = this@MainActivity.window
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val layoutParams = window.attributes
                if (enableHighRefreshRate) {
                    // Actively request the panel's HIGHEST refresh-rate mode at the current resolution,
                    // instead of leaving it to the system (= 0) — several OEMs cap non-game apps at 60 Hz
                    // unless the app asks. This makes scrolling/animations run at 90/120 Hz when supported.
                    val modes = window.windowManager.defaultDisplay.supportedModes
                    val current = window.windowManager.defaultDisplay.mode
                    val best = modes
                        .filter {
                            it.physicalWidth == current.physicalWidth &&
                                it.physicalHeight == current.physicalHeight
                        }
                        .maxByOrNull { it.refreshRate }
                        ?: modes.maxByOrNull { it.refreshRate }
                    layoutParams.preferredDisplayModeId = best?.modeId ?: 0
                } else {
                    val modes = window.windowManager.defaultDisplay.supportedModes
                    val mode60 = modes.firstOrNull { kotlin.math.abs(it.refreshRate - 60f) < 1f }
                        ?: modes.minByOrNull { kotlin.math.abs(it.refreshRate - 60f) }

                    if (mode60 != null) {
                        layoutParams.preferredDisplayModeId = mode60.modeId
                    }
                }
                window.attributes = layoutParams
            } else {
                val params = window.attributes
                if (enableHighRefreshRate) {
                    params.preferredRefreshRate = 0f
                } else {
                    params.preferredRefreshRate = 60f
                }
                window.attributes = params
            }
        }

        val darkTheme by rememberEnumPreference(DarkModeKey, defaultValue = DarkMode.AUTO)
        val isSystemInDarkTheme = isSystemInDarkTheme()
        val useDarkTheme = remember(darkTheme, isSystemInDarkTheme) {
            if (darkTheme == DarkMode.AUTO) isSystemInDarkTheme else darkTheme == DarkMode.ON
        }

        LaunchedEffect(useDarkTheme) {
            setSystemBarAppearance(useDarkTheme)
        }

        val pureBlackEnabled by rememberPreference(PureBlackKey, defaultValue = false)
        val pureBlack = remember(pureBlackEnabled, useDarkTheme) {
            pureBlackEnabled && useDarkTheme
        }

        val (selectedThemeColorInt) = rememberPreference(SelectedThemeColorKey, defaultValue = DefaultThemeColor.toArgb())
        val selectedThemeColor = Color(selectedThemeColorInt)

        var themeColor by rememberSaveable(stateSaver = ColorSaver) {
            mutableStateOf(selectedThemeColor)
        }

        // Dynamic theme derives the accent from the current artwork when enabled.
        val dynamicThemeActive = enableDynamicTheme

        LaunchedEffect(selectedThemeColor, dynamicThemeActive) {
            if (!dynamicThemeActive) {
                themeColor = selectedThemeColor
            }
        }

        LaunchedEffect(playerConnection, dynamicThemeActive, selectedThemeColor) {
            val playerConnection = playerConnection
            if (!dynamicThemeActive || playerConnection == null) {
                themeColor = selectedThemeColor
                return@LaunchedEffect
            }

            playerConnection.service.currentMediaMetadata.collectLatest { song ->
                if (song?.thumbnailUrl != null) {
                    withContext(Dispatchers.IO) {
                        try {
                            val result = imageLoader.execute(
                                ImageRequest.Builder(this@MainActivity)
                                    .data(song.thumbnailUrl)
                                    .allowHardware(false)
                                    .memoryCachePolicy(CachePolicy.ENABLED)
                                    .diskCachePolicy(CachePolicy.ENABLED)
                                    .networkCachePolicy(CachePolicy.ENABLED)
                                    .crossfade(false)
                                    .build()
                            )
                            themeColor = result.image?.toBitmap()?.extractThemeColor() ?: selectedThemeColor
                        } catch (e: Exception) {
                            
                            themeColor = selectedThemeColor
                        }
                    }
                } else {
                    themeColor = selectedThemeColor
                }
            }
        }

        echomusicTheme(
            darkTheme = useDarkTheme,
            pureBlack = pureBlack,
            themeColor = themeColor,
        ) {
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .background(if (pureBlack) Color.Black else MaterialTheme.colorScheme.surface)
            ) {
                val focusManager = LocalFocusManager.current
                val density = LocalDensity.current
                val configuration = LocalWindowInfo.current
                val cutoutInsets = WindowInsets.displayCutout
                val windowsInsets = WindowInsets.systemBars
                val bottomInset = with(density) { windowsInsets.getBottom(density).toDp() }
                val bottomInsetDp = WindowInsets.systemBars.asPaddingValues().calculateBottomPadding()

                val navController = rememberNavController()
                val homeViewModel: HomeViewModel = hiltViewModel()
                val accountImageUrl by homeViewModel.accountImageUrl.collectAsState()
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val (previousTab, setPreviousTab) = rememberSaveable { mutableStateOf("home") }

                val (listenTogetherInTopBar) = rememberPreference(ListenTogetherInTopBarKey, defaultValue = true)
                val navigationItems = remember(listenTogetherInTopBar) { 
                    if (listenTogetherInTopBar) {
                        Screens.MainScreens.filter { it != Screens.ListenTogether }
                    } else {
                        Screens.MainScreens
                    }
                }
                val (useNewMiniPlayerDesign) = rememberPreference(UseNewMiniPlayerDesignKey, defaultValue = true)
                val defaultOpenTab = remember {
                    dataStore[DefaultOpenTabKey].toEnum(defaultValue = NavigationTab.HOME)
                }
                val tabOpenedFromShortcut = remember {
                    when (intent?.action) {
                        ACTION_SEARCH -> NavigationTab.SEARCH
                        ACTION_LIBRARY -> NavigationTab.LIBRARY
                        else -> null
                    }
                }

                val topLevelScreens = remember {
                    listOf(
                        Screens.Home.route,
                        Screens.Library.route,
                        Screens.ListenTogether.route,
                        "settings",
                    )
                }

                val (query, onQueryChange) = rememberSaveable(stateSaver = TextFieldValue.Saver) {
                    mutableStateOf(TextFieldValue())
                }

                val onSearch: (String) -> Unit = remember {
                    { searchQuery ->
                        if (searchQuery.isNotEmpty()) {
                            navController.navigate("search/${URLEncoder.encode(searchQuery, "UTF-8")}")

                            if (dataStore[PauseSearchHistoryKey] != true) {
                                lifecycleScope.launch(Dispatchers.IO) {
                                    database.query {
                                        insert(SearchHistory(query = searchQuery))
                                    }
                                }
                            }
                        }
                    }
                }

                
                val currentRoute by remember {
                    derivedStateOf { navBackStackEntry?.destination?.route }
                }

                val inSearchScreen by remember {
                    derivedStateOf { currentRoute?.startsWith("search/") == true }
                }
                val navigationItemRoutes = remember(navigationItems) {
                    navigationItems.map { it.route }.toSet()
                }

                val shouldShowNavigationBar = remember(currentRoute, navigationItemRoutes) {
                    currentRoute == null ||
                        navigationItemRoutes.contains(currentRoute) ||
                        currentRoute!!.startsWith("search/")
                }

                val isLandscape = configuration.containerDpSize.width > configuration.containerDpSize.height

                val showRail = isLandscape && !inSearchScreen

                // Manual override for the Spotify split: the user can force the wide layout on. Combined with the
                // real width so the persistent browse now-playing panel shows either on a genuinely wide screen
                // (>=800dp) OR whenever the user forced it — but always requires landscape (a side panel needs
                // horizontal room; forcing it in portrait would crush the content).
                val forceSplitView by rememberPreference(ForceSplitViewKey, false)
                val sidePanelOnLeft by rememberPreference(SidePanelOnLeftKey, false)
                val showNowPlayingPanel by rememberPreference(ShowNowPlayingPanelKey, true)
                // Flexible split-panel width: ~30% of the REAL current window width, clamped so it never
                // squeezes the content pane (the old FIXED 340dp left a ~600dp screen with only ~180dp of
                // content) nor grows unwieldy on a huge display. maxWidth is the real window width here
                // (BoxWithConstraints), so this flexes live with folds / multiwindow resizes.
                val sidePanelWidth = (maxWidth * 0.30f).coerceIn(300.dp, 360.dp)
                // MIN-CONTENT guard: only keep the split panel when, after the rail (~80dp) and the panel, the
                // browse/content pane still has real room (>= 560dp). Otherwise fall back to single pane (no
                // panel) so a merely-wide-ish window (e.g. 840-1000dp) is NOT split into two cramped columns.
                val contentHasRoom = (maxWidth - 80.dp - sidePanelWidth) >= 560.dp
                // Show the persistent split panel on any WIDE context (real TV/car, forced split, or a >=840dp
                // expanded window) in landscape — but only when the user hasn't hidden the panel AND the content
                // pane keeps enough room. The width guard is what fixes the small-screen crush (bug 7b); folding
                // it into showSideNowPlaying (not just the render) keeps the bottom mini-player visible as the
                // now-playing surface whenever no panel shows. Matches the ring gate otherwise.
                val showSideNowPlaying = showRail &&
                    showNowPlayingPanel &&
                    contentHasRoom &&
                    (forceSplitView || iad1tya.echo.music.ui.utils.rememberIsWideScreen())

                val navPadding = if (shouldShowNavigationBar && !showRail) {
                    NavigationBarHeight + FloatingToolbarBottomPadding
                } else {
                    0.dp
                }

                val navigationBarHeight by animateDpAsState(
                    targetValue = if (shouldShowNavigationBar && !showRail) NavigationBarHeight else 0.dp,
                    animationSpec = NavigationBarAnimationSpec,
                    label = "navBarHeight",
                )

                val playerBottomSheetState = rememberBottomSheetState(
                    dismissedBound = 0.dp,
                    collapsedBound = bottomInset +
                        (if (!showRail && shouldShowNavigationBar) navPadding else 0.dp) +
                        (if (useNewMiniPlayerDesign) MiniPlayerBottomSpacing else 0.dp) +
                        MiniPlayerHeight,
                    expandedBound = maxHeight,
                )

                val onShuffleClick: (() -> Unit)? = remember(playerConnection, playerBottomSheetState) {
                    playerConnection?.let { connection ->
                        {
                            if (playerBottomSheetState.isExpanded) {
                                playerBottomSheetState.collapseSoft()
                            }
                            connection.player.shuffleModeEnabled = !connection.player.shuffleModeEnabled
                        }
                    }
                }

                val onMusicRecognitionClick: (() -> Unit) = remember(navController, playerBottomSheetState) {
                    {
                        if (playerBottomSheetState.isExpanded) {
                            playerBottomSheetState.collapseSoft()
                        }
                        navController.navigate("recognition") {
                            launchSingleTop = true
                        }
                    }
                }

                val playerAwareWindowInsets = remember(
                    bottomInset,
                    shouldShowNavigationBar,
                    playerBottomSheetState.isDismissed,
                    showRail,
                ) {
                    var bottom = bottomInset
                    if (shouldShowNavigationBar && !showRail) {
                        bottom += NavigationBarHeight
                    }
                    if (!playerBottomSheetState.isDismissed) bottom += MiniPlayerHeight
                    windowsInsets
                        .only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top)
                        .add(WindowInsets(top = AppBarHeight, bottom = bottom))
                }
                appBarScrollBehavior(
                    canScroll = {
                        !inSearchScreen &&
                            (playerBottomSheetState.isCollapsed || playerBottomSheetState.isDismissed)
                    }
                )

                val topAppBarScrollBehavior = appBarScrollBehavior(
                    canScroll = {
                        !inSearchScreen &&
                            (playerBottomSheetState.isCollapsed || playerBottomSheetState.isDismissed)
                    },
                )

                
                LaunchedEffect(navBackStackEntry) {
                    if (inSearchScreen) {
                        val searchQuery = withContext(Dispatchers.IO) {
                            val rawQuery = navBackStackEntry?.arguments?.getString("query")!!
                            try {
                                URLDecoder.decode(rawQuery, "UTF-8")
                            } catch (e: IllegalArgumentException) {
                                rawQuery
                            }
                        }
                        onQueryChange(
                            TextFieldValue(
                                searchQuery,
                                TextRange(searchQuery.length)
                            )
                        )
                    } else if (navigationItems.fastAny { it.route == navBackStackEntry?.destination?.route }) {
                        onQueryChange(TextFieldValue())
                    }

                    
                    if (navigationItems.fastAny { it.route == navBackStackEntry?.destination?.route }) {
                        if (navigationItems.fastAny { it.route == previousTab }) {
                            topAppBarScrollBehavior.state.resetHeightOffset()
                        }
                    }

                    topAppBarScrollBehavior.state.resetHeightOffset()

                    
                    navController.currentBackStackEntry?.destination?.route?.let {
                        setPreviousTab(it)
                    }
                }

                LaunchedEffect(playerConnection) {
                    val player = playerConnection?.player ?: return@LaunchedEffect
                    if (player.currentMediaItem == null) {
                        if (!playerBottomSheetState.isDismissed) {
                            playerBottomSheetState.dismiss()
                        }
                    } else {
                        if (playerBottomSheetState.isDismissed) {
                            playerBottomSheetState.collapseSoft()
                        }
                    }
                }

                DisposableEffect(playerConnection, playerBottomSheetState) {
                    val player = playerConnection?.player ?: return@DisposableEffect onDispose { }
                    val listener = object : Player.Listener {
                        override fun onMediaItemTransition(
                            mediaItem: MediaItem?,
                            reason: Int,
                        ) {
                            if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED &&
                                mediaItem != null &&
                                playerBottomSheetState.isDismissed
                            ) {
                                playerBottomSheetState.collapseSoft()
                            }
                        }
                    }
                    player.addListener(listener)
                    onDispose {
                        player.removeListener(listener)
                    }
                }

                var shouldShowTopBar by rememberSaveable { mutableStateOf(false) }

                LaunchedEffect(navBackStackEntry, listenTogetherInTopBar) {
                    val currentRoute = navBackStackEntry?.destination?.route
                    val isListenTogetherScreen = currentRoute == Screens.ListenTogether.route || 
                        currentRoute == "listen_together_from_topbar"
                    shouldShowTopBar = currentRoute in topLevelScreens &&
                        currentRoute != "settings" &&
                        !(isListenTogetherScreen && listenTogetherInTopBar)
                }

                val coroutineScope = rememberCoroutineScope()
                var sharedSong: SongItem? by remember {
                    mutableStateOf(null)
                }
                val snackbarHostState = remember { SnackbarHostState() }
                var showSettingDialoge by remember { mutableStateOf(false) }
                val (offlineMode, onOfflineModeChange) = rememberPreference(OfflineModeKey, defaultValue = false)

                val (lastOpenedVersionCode, setLastOpenedVersionCode) = rememberPreference(iad1tya.echo.music.constants.LastOpenedVersionCodeKey, -1)
                var showWelcomeDialog by remember { mutableStateOf(false) }
                val onboardingArtistsDone by rememberPreference(iad1tya.echo.music.constants.OnboardingArtistsDoneKey, false)

                LaunchedEffect(lastOpenedVersionCode) {
                    if (lastOpenedVersionCode < BuildConfig.VERSION_CODE) {
                        showWelcomeDialog = true
                    }
                }

                LaunchedEffect(Unit) {
                    if (pendingIntent != null) {
                        handleDeepLinkIntent(pendingIntent!!, navController)
                        pendingIntent = null
                    } else {
                        handleDeepLinkIntent(intent, navController)
                    }
                }

                // ONE-SHOT at startup only: if the YouTube Music login (which cold-restarts the app) set
                // this flag in the PREVIOUS process, return now to the sync selection. Reading it
                // reactively also fired the instant the user tapped "Iniciar sesión" — yanking them off
                // the login screen ("the page keeps restarting / I can't log in"). LaunchedEffect(Unit)
                // runs once on launch, so setting the flag later in-session never triggers a navigation.
                val (_, clearOpenYtmSyncAfterLogin) =
                    rememberPreference(iad1tya.echo.music.constants.OpenYtmSyncAfterLoginKey, false)
                LaunchedEffect(Unit) {
                    val open = context.dataStore.data
                        .map { it[iad1tya.echo.music.constants.OpenYtmSyncAfterLoginKey] ?: false }
                        .first()
                    if (open) {
                        clearOpenYtmSyncAfterLogin(false)
                        kotlinx.coroutines.delay(700)
                        runCatching { navController.navigate("settings/ytm_sync") }
                    }
                }

                DisposableEffect(Unit) {
                    val listener = Consumer<Intent> { intent ->
                        handleDeepLinkIntent(intent, navController)
                    }

                    addOnNewIntentListener(listener)
                    onDispose { removeOnNewIntentListener(listener) }
                }

                val currentTitle = when (navBackStackEntry?.destination?.route) {
                    Screens.Home.route -> "Aura Hi-Res Player"
                    Screens.Search.route -> stringResource(R.string.search)
                    Screens.Library.route -> stringResource(R.string.filter_library)
                    Screens.ListenTogether.route -> stringResource(R.string.together)
                    else -> ""
                }



                val pauseListenHistory by rememberPreference(PauseListenHistoryKey, defaultValue = false)
                val eventCount by database.eventCount().collectAsState(initial = 0)
                val showHistoryButton = remember(pauseListenHistory, eventCount) {
                    !(pauseListenHistory && eventCount == 0)
                }

                val baseBg = if (pureBlack) Color.Black else MaterialTheme.colorScheme.surfaceContainer

                // ── Liquid Glass (Beta) ── DEFAULT OFF. The stored master switch is AND-ed with the
                // runtime eligibility gate (API 31+, raw tier MID/HIGH, not TV/car, Performance Mode
                // off) so the effect can never render on excluded devices. Keyed on highPerfMode so
                // toggling Performance Mode kills glass immediately without restart.
                val glassEligible = remember(highPerfMode) { isGlassEligible(context) }
                val (liquidGlassGlobalEnabled) = rememberPreference(LiquidGlassGlobalEnabledKey, defaultValue = false)
                val (liquidGlassVibrancy) = rememberPreference(LiquidGlassVibrancyKey, defaultValue = 1f)
                val (liquidGlassBlurRadius) = rememberPreference(LiquidGlassBlurRadiusKey, defaultValue = 8f)
                val (liquidGlassLensHeight) = rememberPreference(LiquidGlassLensHeightKey, defaultValue = 0.5f)
                val (liquidGlassLensAmount) = rememberPreference(LiquidGlassLensAmountKey, defaultValue = 0.5f)
                val (liquidGlassChromaticAberration) = rememberPreference(LiquidGlassChromaticAberrationKey, defaultValue = true)
                val (liquidGlassDepthEffect) = rememberPreference(LiquidGlassDepthEffectKey, defaultValue = true)
                val (liquidGlassSurfaceTintColorInt) = rememberPreference(LiquidGlassSurfaceTintColorKey, defaultValue = 0)
                val (liquidGlassSurfaceOpacity) = rememberPreference(LiquidGlassSurfaceOpacityKey, defaultValue = 0.4f)
                // 0 = theme-adaptive text color (dark on light glass, white on dark). Deliberately NOT
                // upstream's hardcoded-white default, which was illegible on light themes.
                val (liquidGlassTextColorInt) = rememberPreference(LiquidGlassTextColorKey, defaultValue = 0)
                val (liquidGlassPlayerEnabled) = rememberPreference(LiquidGlassPlayerEnabledKey, defaultValue = true)
                val (liquidGlassMiniPlayerEnabled) = rememberPreference(LiquidGlassMiniPlayerEnabledKey, defaultValue = true)
                val (liquidGlassNavBarEnabled) = rememberPreference(LiquidGlassNavBarEnabledKey, defaultValue = true)
                val glassEffectConfig = remember(
                    liquidGlassGlobalEnabled, glassEligible, liquidGlassVibrancy, liquidGlassBlurRadius,
                    liquidGlassLensHeight, liquidGlassLensAmount, liquidGlassChromaticAberration,
                    liquidGlassDepthEffect, liquidGlassSurfaceTintColorInt,
                    liquidGlassSurfaceOpacity, liquidGlassTextColorInt, liquidGlassPlayerEnabled,
                    liquidGlassMiniPlayerEnabled, liquidGlassNavBarEnabled,
                ) {
                    GlassEffectConfig(
                        globalEnabled = liquidGlassGlobalEnabled && glassEligible,
                        vibrancy = liquidGlassVibrancy,
                        blurRadius = liquidGlassBlurRadius,
                        lensHeight = liquidGlassLensHeight,
                        lensAmount = liquidGlassLensAmount,
                        chromaticAberration = liquidGlassChromaticAberration,
                        depthEffect = liquidGlassDepthEffect,
                        surfaceTintColor = if (liquidGlassSurfaceTintColorInt == 0) Color.Unspecified else Color(liquidGlassSurfaceTintColorInt),
                        surfaceOpacity = liquidGlassSurfaceOpacity,
                        textColor = if (liquidGlassTextColorInt == 0) Color.Unspecified else Color(liquidGlassTextColorInt),
                        playerEnabled = liquidGlassPlayerEnabled,
                        miniPlayerEnabled = liquidGlassMiniPlayerEnabled,
                        navBarEnabled = liquidGlassNavBarEnabled,
                    )
                }
                // The app-content layer glass surfaces sample from. Only recorded into while glass is
                // globally enabled (see the conditional layerBackdrop on the NavHost below), so the
                // default-OFF path costs nothing.
                val appBackdrop = rememberLayerBackdrop {
                    drawRect(baseBg)
                    drawContent()
                }

                val ringtoneViewModel: RingtoneViewModel = hiltViewModel()
                val ringtoneUiState by ringtoneViewModel.uiState.collectAsState()

                CompositionLocalProvider(
                    LocalRingtoneViewModel provides ringtoneViewModel,
                    LocalDatabase provides database,
                    LocalContentColor provides if (pureBlack) Color.White else contentColorFor(MaterialTheme.colorScheme.surface),
                    LocalPlayerConnection provides playerConnection,
                    LocalPlayerAwareWindowInsets provides playerAwareWindowInsets,
                    LocalDownloadUtil provides downloadUtil,
                    LocalShimmerTheme provides ShimmerTheme,
                    LocalSyncUtils provides syncUtils,
                    LocalListenTogetherManager provides listenTogetherManager,
                    LocalIsInPipMode provides inPipMode,
                    LocalGlassEffectConfig provides glassEffectConfig,
                    LocalAppBackdrop provides appBackdrop,
                ) {

                    Scaffold(
                        snackbarHost = { SnackbarHost(snackbarHostState) },
                        topBar = {
                            AnimatedVisibility(
                                visible = shouldShowTopBar,
                                enter = fadeIn(animationSpec = tween(durationMillis = 300)),
                                exit = fadeOut(animationSpec = tween(durationMillis = 200))
                            ) {
                                Row {
                                    TopAppBar(
                                        title = {
                                            if (navBackStackEntry?.destination?.route == Screens.Home.route) {
                                                Text(
                                                    text = buildAnnotatedString {
                                                        withStyle(
                                                            SpanStyle(
                                                                color = MaterialTheme.colorScheme.onSurface,
                                                                fontWeight = FontWeight.Light
                                                            )
                                                        ) {
                                                            append("AURA ")
                                                        }
                                                        withStyle(
                                                            SpanStyle(
                                                                color = BrandAccent,
                                                                fontWeight = FontWeight.SemiBold
                                                            )
                                                        ) {
                                                            append("HI-RES")
                                                        }
                                                    },
                                                    // Relative letter spacing (em) so it scales with the
                                                    // font, and autoSize shrinks the wordmark to fit any
                                                    // width (e.g. Pixel 8) instead of clipping to "AURA".
                                                    style = MaterialTheme.typography.titleLarge.copy(
                                                        letterSpacing = 0.2.em
                                                    ),
                                                    maxLines = 1,
                                                    softWrap = false,
                                                    autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(
                                                        minFontSize = 12.sp,
                                                        maxFontSize = 22.sp,
                                                    ),
                                                )
                                            } else {
                                                Text(
                                                    text = currentTitle,
                                                    style = MaterialTheme.typography.titleLarge.copy(
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 24.sp
                                                    ),
                                                )
                                            }
                                        },
                                        actions = {
                                            // When Listen Together is hosted in the top bar it is filtered out of
                                            // the bottom navigation, so provide its entry point here — otherwise the
                                            // default (top-bar) config leaves the feature with no way to reach it.
                                            if (listenTogetherInTopBar) {
                                                IconButton(onClick = {
                                                    navController.navigate(Screens.ListenTogether.route) {
                                                        launchSingleTop = true
                                                    }
                                                }) {
                                                    Icon(
                                                        painter = painterResource(R.drawable.group_outlined),
                                                        contentDescription = stringResource(R.string.together)
                                                    )
                                                }
                                            }
                                            if (showHistoryButton) {
                                                IconButton(onClick = { navController.navigate("history") }) {
                                                    Icon(
                                                        painter = painterResource(R.drawable.music_history),
                                                        contentDescription = stringResource(R.string.history)
                                                    )
                                                }
                                            }
                                            val offlineModeOnMsg = stringResource(R.string.offline_mode_on)
                                            val offlineModeOffMsg = stringResource(R.string.offline_mode_off)
                                            IconButton(onClick = {
                                                val enabled = !offlineMode
                                                onOfflineModeChange(enabled)
                                                coroutineScope.launch {
                                                    snackbarHostState.showSnackbar(
                                                        if (enabled) offlineModeOnMsg else offlineModeOffMsg
                                                    )
                                                }
                                            }) {
                                                Icon(
                                                    painter = painterResource(if (offlineMode) R.drawable.offline else R.drawable.cloud),
                                                    contentDescription = stringResource(R.string.offline_mode)
                                                )
                                            }
                                             IconButton(onClick = { showSettingDialoge = true }) {
                                                BadgedBox(badge = {}) {
                                                    if (accountImageUrl != null) {
                                                        AsyncImage(
                                                            model = accountImageUrl,
                                                            contentDescription = stringResource(R.string.account),
                                                            modifier = Modifier
                                                                .size(24.dp)
                                                                .clip(CircleShape)
                                                        )
                                                     } else {
                                                         Icon(
                                                             painter = painterResource(R.drawable.settings),
                                                             contentDescription = stringResource(R.string.account),
                                                             modifier = Modifier.size(24.dp)
                                                         )
                                                     }
                                                }
                                            }
                                        },
                                        scrollBehavior = topAppBarScrollBehavior,
                                        colors = TopAppBarDefaults.topAppBarColors(
                                            containerColor = if (pureBlack) Color.Black else MaterialTheme.colorScheme.surfaceContainer,
                                            scrolledContainerColor = if (pureBlack) Color.Black else MaterialTheme.colorScheme.surfaceContainer,
                                            titleContentColor = MaterialTheme.colorScheme.onSurface,
                                            actionIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                            navigationIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                                        ),
                                        windowInsets = WindowInsets.systemBars.only(WindowInsetsSides.Top),
                                        modifier = Modifier
                                            .windowInsetsPadding(
                                            if (showRail) {
                                                WindowInsets(left = NavigationBarHeight)
                                                    .add(cutoutInsets.only(WindowInsetsSides.Start))
                                            } else {
                                                cutoutInsets.only(WindowInsetsSides.Start + WindowInsetsSides.End)
                                            }
                                        )
                                    )
                                }
                            }
                        },
                        bottomBar = {
                            val onNavItemClick: (Screens, Boolean) -> Unit = remember(navController, coroutineScope, topAppBarScrollBehavior, playerBottomSheetState) {
                                { screen: Screens, isSelected: Boolean ->
                                    if (playerBottomSheetState.isExpanded) {
                                        playerBottomSheetState.collapseSoft()
                                    }

                                    if (isSelected) {
                                        navController.currentBackStackEntry?.savedStateHandle?.set("scrollToTop", true)
                                        coroutineScope.launch {
                                            topAppBarScrollBehavior.state.resetHeightOffset()
                                        }
                                    } else {
                                        navController.navigate(screen.route) {
                                            popUpTo(navController.graph.startDestinationId) {
                                                saveState = true
                                            }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    }
                                }
                            }

                            if (!showRail && currentRoute != "update" && currentRoute != "listen_together/chat") {
                                Box {
                                    BottomSheetPlayer(
                                        state = playerBottomSheetState,
                                        navController = navController,
                                        pureBlack = pureBlack
                                    )

                                    val navSlideDistance = bottomInset + FloatingToolbarBottomPadding + NavigationBarHeight

                                    val navOffsetY = if (navigationBarHeight == 0.dp) {
                                        navSlideDistance
                                    } else {
                                        val slideOffset =
                                            navSlideDistance * playerBottomSheetState.progress.coerceIn(0f, 1f)
                                        val hideOffset =
                                            navSlideDistance * (1 - navigationBarHeight.coerceAtMost(NavigationBarHeight) / NavigationBarHeight)
                                        slideOffset + hideOffset
                                    }

                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.BottomCenter)
                                            .height(navSlideDistance)
                                            .offset(y = navOffsetY),
                                    ) {
                                        FloatingNavigationToolbar(
                                            items = navigationItems,
                                            pureBlack = pureBlack,
                                            onShuffleClick = onShuffleClick,
                                            shuffleIconRes = R.drawable.shuffle,
                                            shuffleContentDescription = stringResource(R.string.shuffle),
                                            onMusicRecognitionClick = onMusicRecognitionClick,
                                            musicRecognitionContentDescription = stringResource(R.string.recognition),
                                            isSelected = { screen ->
                                                currentRoute == screen.route || currentRoute?.startsWith("${screen.route}/") == true
                                            },
                                            onItemClick = onNavItemClick,
                                            modifier = Modifier
                                                .align(Alignment.BottomCenter)
                                                .padding(
                                                    start = FloatingToolbarHorizontalPadding,
                                                    end = FloatingToolbarHorizontalPadding,
                                                    bottom = bottomInset + FloatingToolbarBottomPadding,
                                                )
                                                .height(NavigationBarHeight)
                                        )
                                    }

                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .align(Alignment.BottomCenter)
                                            .height(bottomInsetDp)
                                            
                                            .graphicsLayer {
                                                val progress = playerBottomSheetState.progress
                                                alpha = if (progress > 0f || (useNewMiniPlayerDesign && !shouldShowNavigationBar)) 0f else 1f
                                            }
                                            .background(baseBg)
                                    )
                                }
                            } else {
                                if (currentRoute != "update" && currentRoute != "listen_together/chat") {
                                    // Two-pane: on a genuinely wide screen the persistent right NowPlayingSidePanel
                                    // is the now-playing surface, so fade the redundant bottom mini-player out while
                                    // it's collapsed — alpha follows the sheet progress (0 = invisible when collapsed,
                                    // fades in as it expands to the full player). No dismiss, so the panel's cover tap
                                    // still opens the full player with no animation race. Phones/narrow: alpha 1.
                                    Box(
                                        modifier = if (showSideNowPlaying) {
                                            Modifier
                                                .graphicsLayer {
                                                    alpha = playerBottomSheetState.progress.coerceIn(0f, 1f)
                                                }
                                                // While the sheet is fully collapsed (alpha == 0) the persistent
                                                // side panel is the now-playing surface, so this redundant bottom
                                                // mini-player is invisible. alpha=0 does NOT disable pointer input,
                                                // so its full-width collapsed clickable/drag strip along the bottom
                                                // would still hit-test and steal taps meant for the content list
                                                // beneath it. Keep it composed (no state loss / recompose churn),
                                                // but skip PLACEMENT while hidden so it is neither drawn nor
                                                // hit-tested; it re-appears the instant expansion begins
                                                // (progress > 0), e.g. from the side panel's expandSoft(). Size is
                                                // reported unchanged, so the Scaffold bottomBar layout is untouched.
                                                .layout { measurable, constraints ->
                                                    val placeable = measurable.measure(constraints)
                                                    layout(placeable.width, placeable.height) {
                                                        if (playerBottomSheetState.progress > 0f) {
                                                            placeable.place(0, 0)
                                                        }
                                                    }
                                                }
                                        } else Modifier
                                    ) {
                                        BottomSheetPlayer(
                                            state = playerBottomSheetState,
                                            navController = navController,
                                            pureBlack = pureBlack
                                        )
                                    }
                                }

                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .align(Alignment.BottomCenter)
                                        .height(bottomInsetDp)
                                        
                                        .graphicsLayer {
                                            val progress = playerBottomSheetState.progress
                                            alpha = if (progress > 0f || (useNewMiniPlayerDesign && !shouldShowNavigationBar)) 0f else 1f
                                        }
                                        .background(baseBg)
                                )
                            }
                        },
                        modifier = Modifier
                            .fillMaxSize()
                            .nestedScroll(topAppBarScrollBehavior.nestedScrollConnection)
                    ) {
                        Row(Modifier.fillMaxSize()) {
                            val onRailItemClick: (Screens, Boolean) -> Unit = remember(navController, coroutineScope, topAppBarScrollBehavior, playerBottomSheetState) {
                                { screen: Screens, isSelected: Boolean ->
                                    if (playerBottomSheetState.isExpanded) {
                                        playerBottomSheetState.collapseSoft()
                                    }

                                    if (isSelected) {
                                        navController.currentBackStackEntry?.savedStateHandle?.set("scrollToTop", true)
                                        coroutineScope.launch {
                                            topAppBarScrollBehavior.state.resetHeightOffset()
                                        }
                                    } else {
                                        navController.navigate(screen.route) {
                                            popUpTo(navController.graph.startDestinationId) {
                                                saveState = true
                                            }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    }
                                }
                            }

                            val onRailSearchLongClick: () -> Unit = remember(navController) {
                                {
                                    navController.navigate("recognition") {
                                        launchSingleTop = true
                                    }
                                }
                            }

                            if (showRail && currentRoute != "update") {
                                AppNavigationRail(
                                    navigationItems = navigationItems,
                                    currentRoute = currentRoute,
                                    onItemClick = onRailItemClick,
                                    pureBlack = pureBlack,
                                    onSearchLongClick = onRailSearchLongClick
                                )
                            }
                            // LEFT-side variant of the persistent now-playing panel (some Android-auto users
                            // prefer it on the left). Same gate as the right one; only the position differs.
                            if (showSideNowPlaying &&
                                sidePanelOnLeft &&
                                currentRoute != "update" &&
                                !playerBottomSheetState.isExpanded
                            ) {
                                NowPlayingSidePanel(
                                    onExpand = { playerBottomSheetState.expandSoft() },
                                    modifier = Modifier
                                        .fillMaxHeight()
                                        .widthIn(min = 300.dp, max = 360.dp)
                                        .width(sidePanelWidth),
                                )
                            }
                            Box(Modifier.weight(1f)) {

                                NavHost(
                                    navController = navController,
                                    startDestination = when (tabOpenedFromShortcut ?: defaultOpenTab) {
                                        NavigationTab.HOME -> Screens.Home
                                        NavigationTab.SEARCH -> Screens.Search
                                        NavigationTab.LIBRARY -> Screens.Library
                                        else -> Screens.Home
                                    }.route,
                                    
                                    enterTransition = {
                                        val currentRouteIndex = navigationItems.indexOfFirst {
                                            it.route == targetState.destination.route
                                        }
                                        val previousRouteIndex = navigationItems.indexOfFirst {
                                            it.route == initialState.destination.route
                                        }

                                        if (currentRouteIndex == -1 || currentRouteIndex > previousRouteIndex)
                                            slideInHorizontally { it / 8 } + fadeIn(tween(200))
                                        else
                                            slideInHorizontally { -it / 8 } + fadeIn(tween(200))
                                    },
                                    
                                    exitTransition = {
                                        val currentRouteIndex = navigationItems.indexOfFirst {
                                            it.route == initialState.destination.route
                                        }
                                        val targetRouteIndex = navigationItems.indexOfFirst {
                                            it.route == targetState.destination.route
                                        }

                                        if (targetRouteIndex == -1 || targetRouteIndex > currentRouteIndex)
                                            slideOutHorizontally { -it / 8 } + fadeOut(tween(200))
                                        else
                                            slideOutHorizontally { it / 8 } + fadeOut(tween(200))
                                    },
                                    
                                    popEnterTransition = {
                                        val currentRouteIndex = navigationItems.indexOfFirst {
                                            it.route == targetState.destination.route
                                        }
                                        val previousRouteIndex = navigationItems.indexOfFirst {
                                            it.route == initialState.destination.route
                                        }

                                        if (previousRouteIndex != -1 && previousRouteIndex < currentRouteIndex)
                                            slideInHorizontally { it / 8 } + fadeIn(tween(200))
                                        else
                                            slideInHorizontally { -it / 8 } + fadeIn(tween(200))
                                    },
                                    
                                    popExitTransition = {
                                        val currentRouteIndex = navigationItems.indexOfFirst {
                                            it.route == initialState.destination.route
                                        }
                                        val targetRouteIndex = navigationItems.indexOfFirst {
                                            it.route == targetState.destination.route
                                        }

                                        if (currentRouteIndex != -1 && currentRouteIndex < targetRouteIndex)
                                            slideOutHorizontally { -it / 8 } + fadeOut(tween(200))
                                        else
                                            slideOutHorizontally { it / 8 } + fadeOut(tween(200))
                                    },
                                    // Record the app content into the glass backdrop ONLY while Liquid
                                    // Glass is enabled + eligible AND at least one per-component surface
                                    // is on — with all three toggles off nothing samples the backdrop, so
                                    // don't pay for a full-screen layer re-record. The default-OFF path
                                    // adds no layer work.
                                    modifier = Modifier
                                        .then(
                                            if (glassEffectConfig.globalEnabled && glassEffectConfig.anyComponentEnabled) {
                                                Modifier.layerBackdrop(appBackdrop)
                                            } else {
                                                Modifier
                                            }
                                        )
                                        .nestedScroll(topAppBarScrollBehavior.nestedScrollConnection)
                                ) {
                                    navigationBuilder(
                                        navController = navController,
                                        scrollBehavior = topAppBarScrollBehavior,
                                        activity = this@MainActivity,
                                        snackbarHostState = snackbarHostState
                                    )
                                }
                            }
                            // Spotify-desktop-style persistent now-playing panel on genuinely-wide screens: while
                            // the user browses on the left, the current song's cover + transport stay on the RIGHT
                            // (tap the cover to open the full split player). Only when there's real width (>=800dp
                            // content, landscape rail shown, not searching, player not already expanded) so phones/
                            // portrait/narrow are never squeezed. Renders nothing when no song is active.
                            if (showSideNowPlaying &&
                                !sidePanelOnLeft &&
                                currentRoute != "update" &&
                                !playerBottomSheetState.isExpanded
                            ) {
                                NowPlayingSidePanel(
                                    onExpand = { playerBottomSheetState.expandSoft() },
                                    modifier = Modifier
                                        .fillMaxHeight()
                                        .widthIn(min = 300.dp, max = 360.dp)
                                        .width(sidePanelWidth),
                                )
                            }
                        }
                    }

                    BottomSheetMenu(
                        state = LocalMenuState.current,
                        modifier = Modifier.align(Alignment.BottomCenter)
                    )

                    BottomSheetPage(
                        state = LocalBottomSheetPageState.current,
                        modifier = Modifier.align(Alignment.BottomCenter)
                    )

                    // PICTURE-IN-PICTURE: cover everything with JUST the video so the floating window is a
                    // clean fullscreen video (no title bar / Home / playlist behind it). The NavHost stays
                    // composed underneath (no teardown → no freeze, unlike the old early-return). The sheet's
                    // own video surface is suppressed while inPip (Player.kt), so only this one attaches.
                    if (inPipMode) {
                        androidx.compose.foundation.layout.Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(androidx.compose.ui.graphics.Color.Black),
                        ) {
                            playerConnection?.let {
                                iad1tya.echo.music.ui.player.PlayerVideoSurface(
                                    playerConnection = it,
                                    modifier = Modifier.fillMaxSize(),
                                )
                            }
                        }
                    }



                    sharedSong?.let { song ->
                        playerConnection?.let {
                            Dialog(
                                onDismissRequest = { sharedSong = null },
                                properties = DialogProperties(usePlatformDefaultWidth = false),
                            ) {
                                Surface(
                                    modifier = Modifier.padding(24.dp),
                                    shape = RoundedCornerShape(16.dp),
                                    color = AlertDialogDefaults.containerColor,
                                    tonalElevation = AlertDialogDefaults.TonalElevation,
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                    ) {
                                        YouTubeSongMenu(
                                            song = song,
                                            navController = navController,
                                            onDismiss = { sharedSong = null },
                                        )
                                    }
                                }
                            }
                        }
                    }

                    val ringtoneUiState by ringtoneViewModel.uiState.collectAsState()
                    RingtoneTrimmerDialog(
                        isVisible = ringtoneUiState.showTrimmer,
                        songId = ringtoneUiState.targetSongId,
                        songTitle = ringtoneUiState.targetSongTitle,
                        duration = ringtoneUiState.targetSongDuration,
                        onDismiss = { ringtoneViewModel.hideTrimmer() },
                        onResolveStreamUrl = { ringtoneViewModel.getStreamUrl(this@MainActivity, it) },
                        onConfirm = { start, end -> ringtoneViewModel.setAsRingtone(this@MainActivity, start, end) }
                    )

                    if (ringtoneUiState.showProgress) {
                        RingtoneProgressDialog(
                            isVisible = ringtoneUiState.showProgress,
                            progress = ringtoneUiState.progress,
                            statusMessage = ringtoneUiState.statusMessage,
                            isComplete = ringtoneUiState.isComplete,
                            isSuccess = ringtoneUiState.isSuccess,
                            appliedDirectly = ringtoneUiState.appliedDirectly,
                            onDismiss = { ringtoneViewModel.dismissProgress() },
                            onOpenSettings = { ringtoneViewModel.openRingtoneSettings(this@MainActivity) },
                            onRequestWriteSettings = { ringtoneViewModel.requestSettingsPermission(this@MainActivity) }
                        )
                    }

                    if (showSettingDialoge) {
                        SettingDialoge(
                            onDismissRequest = { showSettingDialoge = false },
                            onNavigate = { route ->
                                showSettingDialoge = false
                                navController.navigate(route)
                            },
                            homeViewModel = homeViewModel
                        )
                    }

                    if (showWelcomeDialog) {
                        WelcomeDialog(
                            onDismissRequest = {
                                val wasFirstRun = lastOpenedVersionCode == -1
                                showWelcomeDialog = false
                                setLastOpenedVersionCode(BuildConfig.VERSION_CODE)
                                // First run only: send the user to the artist-onboarding screen so the
                                // home can be seeded by their taste (independent of signing in).
                                if (wasFirstRun && !onboardingArtistsDone) {
                                    navController.navigate("onboarding_artists")
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    private fun handleDeepLinkIntent(intent: Intent, navController: NavHostController) {
        // Recognition entry (tile permission fallback, result-notification tap, or a RECOGNITION deep
        // link): open the Recognition screen; with EXTRA_AUTO_START_RECOGNITION it starts listening
        // immediately instead of requiring a second tap.
        if (intent.action == ACTION_RECOGNITION) {
            intent.action = null
            val autoStart = intent.getBooleanExtra(EXTRA_AUTO_START_RECOGNITION, false)
            intent.removeExtra(EXTRA_AUTO_START_RECOGNITION)
            // "Listen on Aura" tap on the result notification: the headless flow identified a song and
            // put it in the intent — open it in search (mirrors the in-app SuccessState's play action)
            // instead of a bare Recognition screen that may have already forgotten the result.
            val recognizedTitle = intent.getStringExtra(RecognitionForegroundService.EXTRA_RECOGNITION_TITLE)
            val recognizedArtist = intent.getStringExtra(RecognitionForegroundService.EXTRA_RECOGNITION_ARTIST)
            intent.removeExtra(RecognitionForegroundService.EXTRA_RECOGNITION_TRACK_ID)
            intent.removeExtra(RecognitionForegroundService.EXTRA_RECOGNITION_TITLE)
            intent.removeExtra(RecognitionForegroundService.EXTRA_RECOGNITION_ARTIST)
            if (!recognizedTitle.isNullOrBlank()) {
                val searchQuery = listOfNotNull(
                    recognizedTitle,
                    recognizedArtist?.takeIf { it.isNotBlank() },
                ).joinToString(" ")
                runCatching {
                    navController.navigate("search/${URLEncoder.encode(searchQuery, "UTF-8")}") {
                        launchSingleTop = true
                    }
                }
                return
            }
            runCatching {
                navController.navigate(if (autoStart) "recognition?autoStart=true" else "recognition") {
                    launchSingleTop = true
                }
            }
            return
        }
        val uri = intent.data ?: run {
            val sharedText = intent.extras?.getString(Intent.EXTRA_TEXT) ?: return
            // Extract first http(s) URL from shared text (message may contain extra text around the URL)
            val urlRegex = Regex("""https?://\S+""")
            val extracted = urlRegex.find(sharedText)?.value ?: return
            extracted.toUri()
        }
        intent.data = null
        intent.removeExtra(Intent.EXTRA_TEXT)
        val coroutineScope = lifecycle.coroutineScope

        val listenCode = uri.getQueryParameter("code")
            ?: uri.getQueryParameter("room")
            ?: uri.pathSegments.getOrNull(1)
        val isListenLink = uri.pathSegments.firstOrNull() == "listen" || uri.host?.equals("listen", ignoreCase = true) == true
        if (!listenCode.isNullOrBlank() && isListenLink) {
            val username = dataStore.get(ListenTogetherUsernameKey, "").ifBlank { "Guest" }
            listenTogetherManager.joinRoom(listenCode, username)
            return
        }

        when (val path = uri.pathSegments.firstOrNull()) {
            "playlist" -> uri.getQueryParameter("list")?.let { playlistId ->
                if (playlistId.startsWith("OLAK5uy_")) {
                    coroutineScope.launch(Dispatchers.IO) {
                        YouTube.albumSongs(playlistId).onSuccess { songs ->
                            songs.firstOrNull()?.album?.id?.let { browseId ->
                                withContext(Dispatchers.Main) {
                                    navController.navigate("album/$browseId")
                                }
                            }
                        }.onFailure { reportException(it) }
                    }
                } else {
                    navController.navigate("online_playlist/$playlistId")
                }
            }

            "browse" -> uri.lastPathSegment?.let { browseId ->
                navController.navigate("album/$browseId")
            }

            "channel", "c" -> uri.lastPathSegment?.let { artistId ->
                navController.navigate("artist/$artistId")
            }

            "search" -> {
                uri.getQueryParameter("q")?.let {
                    navController.navigate("search/${URLEncoder.encode(it, "UTF-8")}")
                }
            }

            else -> {
                val videoId = when {
                    path == "watch" -> uri.getQueryParameter("v")
                    uri.host == "youtu.be" -> uri.pathSegments.firstOrNull()
                    else -> null
                }

                val playlistId = uri.getQueryParameter("list")

                if (videoId != null) {
                    coroutineScope.launch(Dispatchers.IO) {
                        YouTube.queue(listOf(videoId), playlistId).onSuccess { queue ->
                            withContext(Dispatchers.Main) {
                                playerConnection?.playQueue(
                                    YouTubeQueue(
                                        WatchEndpoint(videoId = queue.firstOrNull()?.id, playlistId = playlistId),
                                        queue.firstOrNull()?.toMediaMetadata()
                                    )
                                )
                            }
                        }.onFailure {
                            reportException(it)
                        }
                    }
                } else if (playlistId != null) {
                    coroutineScope.launch(Dispatchers.IO) {
                        YouTube.queue(null, playlistId).onSuccess { queue ->
                            val firstItem = queue.firstOrNull()
                            withContext(Dispatchers.Main) {
                                playerConnection?.playQueue(
                                    YouTubeQueue(
                                        WatchEndpoint(videoId = firstItem?.id, playlistId = playlistId),
                                        firstItem?.toMediaMetadata()
                                    )
                                )
                            }
                        }.onFailure {
                            reportException(it)
                        }
                    }
                }
            }
        }
    }

    @SuppressLint("ObsoleteSdkInt")
    private fun setSystemBarAppearance(isDark: Boolean) {
        WindowCompat.getInsetsController(window, window.decorView.rootView).apply {
            isAppearanceLightStatusBars = !isDark
            isAppearanceLightNavigationBars = !isDark
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            window.statusBarColor = (if (isDark) Color.Transparent else Color.Black.copy(alpha = 0.2f)).toArgb()
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            window.navigationBarColor = (if (isDark) Color.Transparent else Color.Black.copy(alpha = 0.2f)).toArgb()
        }
    }
}

val LocalDatabase = staticCompositionLocalOf<MusicDatabase> { error("No database provided") }
val LocalRingtoneViewModel = compositionLocalOf<RingtoneViewModel> { error("No RingtoneViewModel provided") }

val LocalPlayerConnection = staticCompositionLocalOf<PlayerConnection?> { error("No PlayerConnection provided") }
val LocalPlayerAwareWindowInsets = compositionLocalOf<WindowInsets> { error("No WindowInsets provided") }
val LocalDownloadUtil = staticCompositionLocalOf<DownloadUtil> { error("No DownloadUtil provided") }
val LocalSyncUtils = staticCompositionLocalOf<SyncUtils> { error("No SyncUtils provided") }
val LocalListenTogetherManager = staticCompositionLocalOf<iad1tya.echo.music.listentogether.ListenTogetherManager?> { null }
val LocalIsPlayerExpanded = compositionLocalOf { false }
/** True while the app is in Android Picture-in-Picture mode — lets the player render a clean PiP view
 *  (title/artist over the video, no bottom controls; playback controls come from the system PiP actions). */
val LocalIsInPipMode = compositionLocalOf { false }
