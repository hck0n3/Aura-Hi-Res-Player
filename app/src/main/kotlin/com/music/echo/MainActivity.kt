

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
import android.os.SystemClock
import android.view.HapticFeedbackConstants
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
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToDown
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalView
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
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController
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
import iad1tya.echo.music.constants.GlobalHapticsKey
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
import iad1tya.echo.music.migration.TidalAuthCallbackBus
import iad1tya.echo.music.models.toMediaMetadata
import iad1tya.echo.music.playback.DownloadUtil
import iad1tya.echo.music.playback.MusicService
import iad1tya.echo.music.playback.MusicService.MusicBinder
import iad1tya.echo.music.playback.PlayerConnection
import iad1tya.echo.music.playback.PreviousQueueOffer
import iad1tya.echo.music.playback.PreviousQueueRule
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
import iad1tya.echo.music.ui.newui.AuraGlobalActions
import iad1tya.echo.music.ui.newui.AuraNavBarHeight
import iad1tya.echo.music.ui.newui.AuraNavigationBar
import iad1tya.echo.music.ui.newui.BottomSheetPlayerHost
import iad1tya.echo.music.ui.newui.LocalAuraTopActions
import iad1tya.echo.music.ui.newui.rememberNewUiEnabled
import iad1tya.echo.music.ui.player.NowPlayingSidePanel
import iad1tya.echo.music.ui.screens.Screens
import iad1tya.echo.music.widget.PlaylistWidgetReceiver
import iad1tya.echo.music.ui.screens.SettingDialoge
import iad1tya.echo.music.license.LicenseGate
import iad1tya.echo.music.ui.screens.WelcomeDialog
import iad1tya.echo.music.ui.screens.navigationBuilder
import iad1tya.echo.music.ui.screens.settings.DarkMode
import iad1tya.echo.music.ui.screens.settings.NavigationTab
import iad1tya.echo.music.ui.theme.AccentVividness
import iad1tya.echo.music.ui.theme.BrandAccent
import iad1tya.echo.music.ui.theme.ColorSaver
import iad1tya.echo.music.ui.theme.DefaultThemeColor
import iad1tya.echo.music.ui.theme.echomusicTheme
import iad1tya.echo.music.ui.theme.extractThemeColor
import iad1tya.echo.music.ui.utils.appBarScrollBehavior
import iad1tya.echo.music.ui.utils.navigateToReentryTarget
import iad1tya.echo.music.ui.utils.resetHeightOffset
import iad1tya.echo.music.utils.SyncUtils
import iad1tya.echo.music.utils.dataStore
import iad1tya.echo.music.utils.get
import iad1tya.echo.music.utils.rememberEnumPreference
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
        // Playlist widget: tapping a card (anywhere but its play button) opens the app on that target.
        // Values must stay in sync with PlaylistWidgetManager/PlaylistWidgetReceiver.
        private const val ACTION_OPEN_WIDGET_TARGET = "iad1tya.echo.music.action.OPEN_WIDGET_TARGET"
        private const val EXTRA_WIDGET_TARGET_TYPE = "extra_widget_target_type"
        private const val EXTRA_WIDGET_TARGET_ID = "extra_widget_target_id"
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
                // REUSE the existing connection when we rebound to the SAME live service (the normal
                // background -> foreground round trip: onStop unbinds, onStart rebinds). Building a new one
                // there was doing real damage: onServiceDisconnected — the ONLY caller of dispose() — is not
                // invoked on a normal unbind, so the old PlayerConnection stayed registered as a
                // Player.Listener (leak), AND replacing this mutableStateOf forced a full recomposition of
                // everything reading it. That recomposition storm is what re-ran PlayerVideoSurface's update
                // while the TextureView's SurfaceTexture was not yet available — the "leave the app, come
                // back, video frozen forever" bug. Same service = same player = nothing to rebuild.
                val existing = playerConnection
                if (existing != null && existing.service === service.service) {
                    Timber.tag("MainActivity").d("Rebound to the same service — reusing PlayerConnection")
                    existing.service.onAppForegrounded()
                    listenTogetherManager.setPlayerConnection(existing)
                    return
                }
                // A genuinely different service instance (process death / fresh start): the old connection can
                // never be reused, so release its listeners before dropping it.
                existing?.dispose()
                try {
                    playerConnection = PlayerConnection(this@MainActivity, service, database, lifecycleScope)
                    Timber.tag("MainActivity").d("PlayerConnection created successfully")
                    // #27: binding on a cold start = the app is in the foreground → genuine engagement, so drop
                    // the cold-restore PLAY veto and let external controls (BT/AA/notification/watch) work.
                    service.service.onAppForegrounded()

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
        // #27: app coming to the foreground is genuine engagement → drop the cold-restore PLAY veto so all
        // external controls work normally (onServiceConnected covers the cold-start bind-after-onStart case).
        playerConnection?.service?.onAppForegrounded()

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

        // ── "Interfaz nueva": the SHELL and the APP THEME both follow the flag ────────────────────
        // Read here, ABOVE echomusicTheme, and not (as it was) inside the theme's content lambda: by
        // the time that lambda runs the ColorScheme is already fixed, so the flag could only reach the
        // layout. That is the whole of "la app son dos temas a la vez" — the six rebuilt screens paint
        // AuraPalette.Ground themselves while the ~89 classic screens and every dialog kept whatever
        // scheme the app theme had picked, which for a light-theme user is white.
        // With the flag OFF every expression below reduces to the one that shipped.
        val newUiShell = rememberNewUiEnabled()

        val darkTheme by rememberEnumPreference(DarkModeKey, defaultValue = DarkMode.AUTO)
        val isSystemInDarkTheme = isSystemInDarkTheme()
        // The redesign is DARK-ONLY: its ground is a near-black literal and every step of its palette
        // is white-at-low-alpha over it, so there is no light variant to fall back to. Forcing dark is
        // therefore part of turning it on, not a preference being ignored quietly — see the report.
        val useDarkTheme = remember(darkTheme, isSystemInDarkTheme, newUiShell) {
            newUiShell || if (darkTheme == DarkMode.AUTO) isSystemInDarkTheme else darkTheme == DarkMode.ON
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
        // How literally the accent is applied (see AccentVividness). SOFT == the Material 3 tonal look
        // the app has always had, so an update changes nothing until the user opts in.
        val accentVividness by rememberEnumPreference(
            iad1tya.echo.music.constants.AccentVividnessKey,
            defaultValue = AccentVividness.SOFT,
        )
        // Named theme ("Muestreo"). NONE by default, so this is a no-op for everyone who has not
        // opted in; when set it replaces the whole scheme, surfaces included.
        val themePreset by rememberEnumPreference(
            iad1tya.echo.music.constants.AppThemePresetKey,
            defaultValue = iad1tya.echo.music.ui.theme.ThemePreset.NONE,
        )

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
                                    // This bitmap exists ONLY to extract an accent colour, yet it was
                                    // requested at full resolution (a 1200x1200 cover decodes to ~5.8 MB
                                    // on the software heap, allowHardware(false)) — a prime suspect for
                                    // the silent low-memory kills. Palette downsamples to 112x112 anyway,
                                    // so 100x100 loses nothing visually.
                                    // INEXACT is load-bearing: the memory-cache key does NOT include the
                                    // size for a request without transformations, so this shares the very
                                    // slot the UI uses. With the default EXACT precision the UI's
                                    // full-size consumer would reject this downsampled entry and
                                    // re-decode, costing an extra decode per track change instead of
                                    // saving one. INEXACT reuses whatever the UI already cached.
                                    .size(100, 100)
                                    .precision(coil3.size.Precision.INEXACT)
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

        // Global haptics: a single root-level observer covers taps and scrolling app-wide, so the
        // toggle is honoured everywhere instead of only in the few components that opted in.
        val (globalHaptics) = rememberPreference(GlobalHapticsKey, defaultValue = true)
        val rootView = LocalView.current

        echomusicTheme(
            darkTheme = useDarkTheme,
            pureBlack = pureBlack,
            themeColor = themeColor,
            vividness = accentVividness,
            preset = themePreset,
            // The STORED pick, not `themeColor`: with the dynamic theme on `themeColor` is the current
            // cover's colour, and the surfaces must never follow that (the whole canvas re-hued on every
            // track change). Passing the stored value also means a fresh pick repaints the surfaces on
            // the same frame it is written, instead of waiting for the LaunchedEffect above to catch up.
            selectedThemeColor = selectedThemeColor,
            // Puts the classic screens and every dialog on the redesign's own ground instead of a
            // second, clashing canvas. Accent roles are untouched, so the owner's colour still lands.
            newUiGround = newUiShell,
        ) {
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .background(if (pureBlack) Color.Black else MaterialTheme.colorScheme.surface)
                    .pointerInput(globalHaptics) {
                        if (!globalHaptics) return@pointerInput
                        // Observe-only on the Initial pass: nothing is ever consumed, so children,
                        // scrolling and D-pad/TV focus keep the exact behaviour they had before.
                        // The scroll tick is throttled to 100ms, otherwise a drag would fire a
                        // haptic per motion event and turn into continuous vibration (battery/heat).
                        // It also requires real movement (touch slop): `positionChange() != Zero` alone
                        // fires on sub-pixel finger jitter while merely HOLDING a control, which is both a
                        // wrong-feeling buzz and pointless vibrator work.
                        var lastScrollHaptic = 0L
                        val slop = viewConfiguration.touchSlop
                        awaitPointerEventScope {
                            while (true) {
                                val changes = awaitPointerEvent(PointerEventPass.Initial).changes
                                if (changes.fastAny { it.changedToDown() }) {
                                    rootView.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                                } else if (changes.fastAny { it.pressed && it.positionChange().getDistance() > slop }) {
                                    val now = SystemClock.uptimeMillis()
                                    if (now - lastScrollHaptic > SCROLL_HAPTIC_THROTTLE_MS) {
                                        rootView.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                        lastScrollHaptic = now
                                    }
                                }
                            }
                        }
                    }
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

                // NOTE: `newUiShell` is read ABOVE echomusicTheme (it has to drive the ColorScheme, not
                // just the layout) and is in scope here. Every shell branch below gates on it; with the
                // flag OFF each one takes the classic side, so the shell is byte-identical to today.
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

                val shouldShowNavigationBar = remember(currentRoute, navigationItemRoutes, newUiShell) {
                    currentRoute == null ||
                        navigationItemRoutes.contains(currentRoute) ||
                        currentRoute!!.startsWith("search/") ||
                        // The render makes Ajustes the FOURTH bottom-bar cell and draws that cell in
                        // teal ON the Ajustes screen (`nv on`), i.e. under the new UI Ajustes is a
                        // top-level destination and keeps the bar. GATED: with the flag off this term
                        // is false and "settings" is a bar-less route exactly as it is today.
                        (newUiShell && currentRoute == "settings")
                }

                // Routes whose NEW screen draws its own [AuraScreenHeader]. The render has no opaque
                // title bar — the section name lives in the content — so on these routes the global
                // TopAppBar is not drawn at all (it was stacking a second header on top of Home's) and
                // the 64 dp phantom top inset it needs is not reserved either. Its four actions are not
                // dropped: they move into that header through [LocalAuraTopActions] below.
                // "settings" belongs here too, and for a slightly different reason: no TopAppBar has
                // EVER been drawn on that route (the `currentRoute != "settings"` guard on
                // `shouldShowTopBar` below predates the new UI), yet the 64 dp was still reserved — so
                // AuraSettingsScreen consumed it as a Spacer and opened with 64 dp of dead black above
                // "Ajustes". That was the third of the three new full screens still not immersive.
                // Gated on `newUiShell`: with the flag off this is false and the classic SettingsScreen
                // keeps the exact inset it has today.
                val auraOwnsHeader = newUiShell &&
                    (
                        currentRoute == Screens.Home.route ||
                            currentRoute == Screens.Library.route ||
                            currentRoute == "settings"
                        )

                val isLandscape = configuration.containerDpSize.width > configuration.containerDpSize.height

                // #47 — the nav rail is a WIDTH decision, not an orientation one. An unfolded foldable is a
                // near-square PORTRAIT window (~700x900): it reports ORIENTATION_PORTRAIT, so `isLandscape`
                // was false and it kept the phone's bottom navigation bar even with room for the rail. Read
                // ONCE here and reuse: this is a pure layout gate ([rememberIsWideLayout], width-only) and
                // never [rememberIsTvOrCar], which would light the TV/car D-pad ring on a wide phone and
                // regress Android Auto / TV (registry #12 / #26).
                val isWideLayout = iad1tya.echo.music.ui.utils.rememberIsWideLayout()

                // Ambient Mode is a full-screen lean-back view: never overlay the rail on it.
                val showRail = (isLandscape || isWideLayout) && !inSearchScreen && currentRoute != "ambient_mode"

                // The persistent browse now-playing panel shows on any genuinely wide window (or when the user
                // forced the split via ForceSplitViewKey, which [rememberIsWideLayout] already folds in).
                // It no longer requires LANDSCAPE (#47): a side panel needs horizontal ROOM, not a particular
                // orientation, and an unfolded foldable has that room in portrait. What actually protects the
                // content pane from being crushed is the `contentHasRoom` (>= 560dp) guard below — that is the
                // real invariant, and it stays.
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
                // NOTE: [isWideLayout] is the value hoisted above, NOT a call here. `forceSplitView ||
                // rememberIsWideLayout()` short-circuited, so the composable was invoked conditionally — an
                // unstable call count across recompositions, which is exactly the shape that corrupts Compose's
                // slot table. [rememberIsWideLayout] already folds in ForceSplitViewKey, so this is equivalent.
                // The `contentHasRoom` (>= 560dp) guard is preserved untouched: a wide window still refuses to
                // split when the content pane would end up cramped.
                val showSideNowPlaying = showRail &&
                    showNowPlayingPanel &&
                    contentHasRoom &&
                    isWideLayout

                // THE bottom-bar measurement, in one place. The classic shell is a 72 dp floating pill
                // that hovers `FloatingToolbarBottomPadding` above the gesture bar; the new shell is a
                // flush [AuraNavigationBar] of [AuraNavBarHeight] that paints its own ground through the
                // inset. Both the slide distance and the bottom window inset are derived from this, so a
                // shell swap can never mis-pad a list.
                val shellNavBarHeight = if (newUiShell) AuraNavBarHeight else NavigationBarHeight

                val navPadding = if (shouldShowNavigationBar && !showRail) {
                    if (newUiShell) AuraNavBarHeight else NavigationBarHeight + FloatingToolbarBottomPadding
                } else {
                    0.dp
                }

                val navigationBarHeight by animateDpAsState(
                    targetValue = if (shouldShowNavigationBar && !showRail) shellNavBarHeight else 0.dp,
                    animationSpec = NavigationBarAnimationSpec,
                    label = "navBarHeight",
                )

                val playerBottomSheetState = rememberBottomSheetState(
                    dismissedBound = 0.dp,
                    collapsedBound = bottomInset +
                        (if (!showRail && shouldShowNavigationBar) navPadding else 0.dp) +
                        // The new shell's mini player is also a detached pill (the render's `.mi`), so it
                        // needs the same breathing room the new classic mini design asks for.
                        (if (useNewMiniPlayerDesign || newUiShell) MiniPlayerBottomSpacing else 0.dp) +
                        MiniPlayerHeight,
                    expandedBound = maxHeight,
                )

                // Shuffle FAB removed from the floating toolbar (0.6.92) — only the recognition mic FAB
                // is surfaced there now, so the shuffle click handler is no longer wired.

                val onMusicRecognitionClick: (() -> Unit) = remember(navController, playerBottomSheetState) {
                    {
                        if (playerBottomSheetState.isExpanded) {
                            playerBottomSheetState.collapseSoft()
                        }
                        // In-app tap starts recognizing immediately (autoStart) — the screen requests the
                        // mic permission if missing, then begins listening, so no idle second tap is needed.
                        navController.navigate("recognition?autoStart=true") {
                            launchSingleTop = true
                        }
                    }
                }

                val playerAwareWindowInsets = remember(
                    bottomInset,
                    shouldShowNavigationBar,
                    playerBottomSheetState.isDismissed,
                    showRail,
                    shellNavBarHeight,
                    auraOwnsHeader,
                ) {
                    var bottom = bottomInset
                    if (shouldShowNavigationBar && !showRail) {
                        bottom += shellNavBarHeight
                    }
                    if (!playerBottomSheetState.isDismissed) bottom += MiniPlayerHeight
                    windowsInsets
                        .only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top)
                        // IMMERSIVE: `WindowInsets(top = AppBarHeight)` is a 64 dp PHANTOM bar. Every
                        // screen consumes this as `contentPadding`, so content could never scroll under
                        // the status bar — the new screens looked boxed in ("la interfaz no es inmersiva,
                        // no se muestra en pantalla completa") even though edge-to-edge itself is correct.
                        // Reserve it only when a bar is actually drawn there. On the routes where the new
                        // screen owns its header there is no bar, so there is nothing to reserve, and the
                        // remaining `.only(Top)` still keeps the first row clear of the status bar while
                        // the list scrolls behind it.
                        .add(
                            WindowInsets(
                                top = if (auraOwnsHeader) 0.dp else AppBarHeight,
                                bottom = bottom,
                            )
                        )
                }
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
                val snackbarHostState = remember { SnackbarHostState() }

                // "¿quieres volver a la cola anterior?" — the service noticed he left a playlist to go
                // play an album (PreviousQueueRule) and snapshotted the queue he walked away from.
                //
                // ARMED at the jump, SHOWN when he leaves the screen he jumped to. His words are "cuando
                // le dé para atrás y vuelva a donde estaba… que me dé esa opción": prompting the instant
                // he taps play on the album would ask him to undo the choice he just made, one second
                // after making it, and the snackbar would be long gone by the time he actually wants it.
                // So the arming records WHICH back-stack entry he was on, and the prompt waits until that
                // entry is no longer the current one — a back press out of the album, in practice.
                //
                // It is OFFERED, never automatic: a snackbar interrupts no playback, steals no focus, and
                // expires on its own; if he ignores it the snapshot is simply dropped. Compared by
                // back-stack ENTRY (not by route pattern) so album -> album also counts as having moved
                // on, and by reference because NavBackStackEntry.id is @RestrictTo on some versions.
                var lastPreviousQueueOfferToken by rememberSaveable { mutableStateOf(0L) }
                var pendingPreviousQueueOffer by remember { mutableStateOf<PreviousQueueOffer?>(null) }
                var previousQueueOfferArmedEntry by remember { mutableStateOf<NavBackStackEntry?>(null) }
                val previousQueueOfferMessage = stringResource(R.string.previous_queue_offer)
                val previousQueueOfferTitled = stringResource(R.string.previous_queue_offer_titled)
                val previousQueueResumeLabel = stringResource(R.string.previous_queue_resume)

                LaunchedEffect(playerConnection) {
                    val connection = playerConnection ?: return@LaunchedEffect
                    connection.previousQueueOffer.collectLatest { offer ->
                        // The token guard makes ONE detour prompt exactly ONCE — without it a rotation
                        // would replay the StateFlow's last value and re-offer a queue already answered.
                        if (offer == null || offer.token <= lastPreviousQueueOfferToken) {
                            pendingPreviousQueueOffer = null
                            previousQueueOfferArmedEntry = null
                            return@collectLatest
                        }
                        pendingPreviousQueueOffer = offer
                        previousQueueOfferArmedEntry = navBackStackEntry
                    }
                }

                // Keyed on the BOOLEAN, not on the entry: navigating again while the snackbar is up must
                // not restart this effect and cancel the prompt mid-air (he presses back, then taps
                // something else a second later — extremely ordinary). The flag stays true for as long as
                // the offer is pending and he is off the armed entry, so the snackbar survives.
                val previousQueueOfferDue = pendingPreviousQueueOffer != null &&
                        navBackStackEntry !== previousQueueOfferArmedEntry
                LaunchedEffect(previousQueueOfferDue) {
                    if (!previousQueueOfferDue) return@LaunchedEffect
                    val offer = pendingPreviousQueueOffer ?: return@LaunchedEffect
                    val connection = playerConnection ?: return@LaunchedEffect

                    // The offer has a deadline (PreviousQueueRule.OFFER_TTL_MS from the capture) and the
                    // service drops it on a timer — but that timer is a main-looper delay, which does not
                    // tick through deep sleep. So check the deadline HERE too: if the phone slept through
                    // it, raising the snackbar anyway would show a button whose action is already refused
                    // on the service side. Answer it as a dismissal instead and show nothing.
                    //
                    // DISPLAY_GRACE_MS is added to "now" on purpose: a prompt raised in the last seconds
                    // of the window would lapse WHILE he is reading it, so it is treated as already gone.
                    // Anything actually shown is therefore still answerable when he taps it.
                    if (PreviousQueueRule.hasLapsed(
                            android.os.SystemClock.elapsedRealtime() + PreviousQueueRule.DISPLAY_GRACE_MS,
                            offer.expiresAtElapsedRealtime,
                        )
                    ) {
                        lastPreviousQueueOfferToken = offer.token
                        pendingPreviousQueueOffer = null
                        previousQueueOfferArmedEntry = null
                        connection.dismissPreviousQueueOffer()
                        return@LaunchedEffect
                    }

                    lastPreviousQueueOfferToken = offer.token
                    val result = snackbarHostState.showSnackbar(
                        message = offer.title
                            ?.let { previousQueueOfferTitled.format(it) }
                            ?: previousQueueOfferMessage,
                        actionLabel = previousQueueResumeLabel,
                        duration = SnackbarDuration.Long,
                    )
                    pendingPreviousQueueOffer = null
                    // Release the held entry: nothing references it once the prompt is answered.
                    previousQueueOfferArmedEntry = null
                    if (result == SnackbarResult.ActionPerformed) {
                        connection.resumePreviousQueue()
                    } else {
                        connection.dismissPreviousQueueOffer()
                    }
                }

                var showSettingDialoge by remember { mutableStateOf(false) }
                val (offlineMode, onOfflineModeChange) = rememberPreference(OfflineModeKey, defaultValue = false)

                val (lastOpenedVersionCode, setLastOpenedVersionCode) = rememberPreference(iad1tya.echo.music.constants.LastOpenedVersionCodeKey, -1)
                var showWelcomeDialog by remember { mutableStateOf(false) }
                val onboardingArtistsDone by rememberPreference(iad1tya.echo.music.constants.OnboardingArtistsDoneKey, false)

                val (batteryReliabilityPromptShown, setBatteryReliabilityPromptShown) =
                    rememberPreference(iad1tya.echo.music.constants.BatteryReliabilityPromptShownKey, false)
                var showBatteryReliabilityDialog by remember { mutableStateOf(false) }
                // Whether a welcome/changelog dialog is due THIS launch (first run or a version bump). Used to
                // defer the battery prompt to a later launch so it never stacks on welcome/onboarding — more
                // robust than reading the instantaneous dialog flag (which a fast dismiss could race).
                val welcomeWillShow = lastOpenedVersionCode < BuildConfig.VERSION_CODE

                LaunchedEffect(lastOpenedVersionCode) {
                    if (lastOpenedVersionCode < BuildConfig.VERSION_CODE) {
                        showWelcomeDialog = true
                    }
                }

                LaunchedEffect(Unit) {
                    // Proactively offer the battery-exemption + autostart prompt on aggressive OEMs (MIUI/etc.)
                    // ONCE, since those skins kill the backgrounded media service → background playback stops and
                    // the app "aparece y desaparece" in Android Auto. Never stack it on the welcome/onboarding
                    // flow — if a welcome shows this launch, the prompt surfaces on a later one instead.
                    kotlinx.coroutines.delay(1500)
                    if (!batteryReliabilityPromptShown && !welcomeWillShow && !showWelcomeDialog &&
                        iad1tya.echo.music.utils.BackgroundReliability.isAggressiveOem() &&
                        !iad1tya.echo.music.utils.BackgroundReliability.isIgnoringBatteryOptimizations(this@MainActivity)
                    ) {
                        showBatteryReliabilityDialog = true
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

                // ── The four global top-bar actions, re-hosted in the new screens' own header ──────
                // Read the two snackbar strings here (not inside the TopAppBar's `actions` scope) so the
                // offline toggle behaves identically whichever shell renders it.
                val offlineModeOnMessage = stringResource(R.string.offline_mode_on)
                val offlineModeOffMessage = stringResource(R.string.offline_mode_off)
                val auraTopActions: (@Composable () -> Unit)? = if (newUiShell) {
                    {
                        AuraGlobalActions(
                            // Same condition as the classic bar: Listen Together only appears here when
                            // the user hosts it in the top bar, because it is then filtered OUT of the
                            // bottom navigation and this is its only entry point.
                            showListenTogether = listenTogetherInTopBar,
                            // Escuchar juntos is a TOP-LEVEL destination (it is one of
                            // Screens.MainScreens, merely filtered out of the bottom bar while it lives
                            // up here), so it has to travel on the tab NavOptions for the same reason
                            // Ajustes does: pushed on top of Biblioteca it poisons the saved back stack
                            // and every later Biblioteca tap lands on it. This branch only exists while
                            // the flag is on (`auraTopActions` is null otherwise), so the classic top
                            // bar's copy of this call is left exactly as it is.
                            onListenTogetherClick = {
                                if (currentRoute != Screens.ListenTogether.route) {
                                    navController.navigateAsTab(Screens.ListenTogether.route)
                                }
                            },
                            showHistory = showHistoryButton,
                            onHistoryClick = { navController.navigate("history") },
                            offlineMode = offlineMode,
                            onOfflineToggle = {
                                val enabled = !offlineMode
                                onOfflineModeChange(enabled)
                                coroutineScope.launch {
                                    snackbarHostState.showSnackbar(
                                        if (enabled) offlineModeOnMessage else offlineModeOffMessage
                                    )
                                }
                            },
                            accountImageUrl = accountImageUrl,
                            onAccountClick = { showSettingDialoge = true },
                            // Reconocimiento used to be the fourth bottom-bar cell; the render gives
                            // that slot to Ajustes, and `navigate("recognition…")` exists in exactly
                            // one place in the app, so the feature would have lost its only door.
                            // Same lambda as before — it still collapses the sheet and autostarts.
                            onRecognitionClick = onMusicRecognitionClick,
                        )
                    }
                } else {
                    null
                }

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
                    LocalAuraTopActions provides auraTopActions,
                ) {

                    Scaffold(
                        snackbarHost = { SnackbarHost(snackbarHostState) },
                        topBar = {
                            AnimatedVisibility(
                                // "la parte donde está el título de la app está fea, solo es una barra
                                // negra y ya" — with the new UI on, routes that draw their own
                                // [AuraScreenHeader] get NO global bar: it was an opaque
                                // surfaceContainer rectangle occluding the ambient bloom, and on Inicio
                                // it stacked a SECOND header above the new one. Its actions are not lost;
                                // they are provided through [LocalAuraTopActions] and rendered inside
                                // that header's trailing slot.
                                visible = shouldShowTopBar && !auraOwnsHeader,
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
                                        // The same options as before, named once — see [navigateAsTab].
                                        navController.navigateAsTab(screen.route)
                                    }
                                }
                            }

                            if (!showRail && currentRoute != "update" && currentRoute != "listen_together/chat" && currentRoute != "ambient_mode") {
                                Box {
                                    // "Interfaz nueva" beta: falls back to the classic BottomSheetPlayer
                                    // whenever the flag is OFF or the new player is not built yet.
                                    BottomSheetPlayerHost(
                                        state = playerBottomSheetState,
                                        navController = navController,
                                        pureBlack = pureBlack
                                    )

                                    // Derived from [shellNavBarHeight], so the bar, the slide animation and
                                    // the bottom window inset always agree about how tall the bar is.
                                    val navSlideDistance = if (newUiShell) {
                                        bottomInset + AuraNavBarHeight
                                    } else {
                                        bottomInset + FloatingToolbarBottomPadding + NavigationBarHeight
                                    }

                                    val navOffsetY = if (navigationBarHeight == 0.dp) {
                                        navSlideDistance
                                    } else {
                                        val slideOffset =
                                            navSlideDistance * playerBottomSheetState.progress.coerceIn(0f, 1f)
                                        val hideOffset =
                                            navSlideDistance * (1 - navigationBarHeight.coerceAtMost(shellNavBarHeight) / shellNavBarHeight)
                                        slideOffset + hideOffset
                                    }

                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.BottomCenter)
                                            .height(navSlideDistance)
                                            .offset(y = navOffsetY),
                                    ) {
                                        if (newUiShell) {
                                            // The render's `.nav`: a flush bar on the ground with a
                                            // hairline rule, NOT an M3 HorizontalFloatingToolbar pill
                                            // with per-FAB glass circles ("por qué me sigue saliendo el
                                            // reproductor flotante y sus botones flotantes en liquid
                                            // glass"). Same items, same click contract, same recognition
                                            // entry point — only the drawing changes.
                                            AuraNavigationBar(
                                                items = navigationItems,
                                                isSelected = { screen ->
                                                    currentRoute == screen.route ||
                                                        currentRoute?.startsWith("${screen.route}/") == true
                                                },
                                                onItemClick = onNavItemClick,
                                                bottomInset = bottomInset,
                                                // The render's fourth cell. It is a TAB, so it
                                                // navigates on exactly the NavOptions the other three
                                                // use — see [navigateAsTab]. `launchSingleTop` alone
                                                // was what made Biblioteca unusable: it left Ajustes
                                                // stacked ON TOP of Biblioteca, and the next Biblioteca
                                                // tap then popped both with `saveState`, which
                                                // androidx-navigation stores as ONE deque keyed by the
                                                // deepest popped destination (library) — so
                                                // `restoreState` put Ajustes straight back on top.
                                                // Self-perpetuating, hence "no muestra nada y me manda
                                                // ajustes".
                                                onSettingsClick = {
                                                    // Same guard the other cells have: re-navigating to
                                                    // the tab you are already on would pop and rebuild
                                                    // the entry, losing the Ajustes scroll position.
                                                    if (currentRoute != "settings") {
                                                        navController.navigateAsTab("settings")
                                                    }
                                                },
                                                settingsSelected = currentRoute == "settings",
                                                modifier = Modifier
                                                    .align(Alignment.BottomCenter)
                                                    .fillMaxWidth(),
                                            )
                                        } else {
                                        FloatingNavigationToolbar(
                                            items = navigationItems,
                                            pureBlack = pureBlack,
                                            // Shuffle FAB intentionally removed from the floating toolbar (user request):
                                            // only the recognition mic FAB is surfaced here (glass-styled). Shuffle params
                                            // default to null in FloatingNavigationToolbar, so the shuffle FAB never renders.
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
                                    }

                                    // IMMERSIVE: an opaque `baseBg` rectangle over the gesture-nav strip.
                                    // It cut the ambient bloom dead along the bottom edge of every new
                                    // screen. With the new shell the [AuraNavigationBar] already paints its
                                    // own ground through that inset when a bar is shown, and when no bar is
                                    // shown the point is that the content reaches the bottom edge — so the
                                    // strip is simply not drawn.
                                    if (!newUiShell) {
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
                                }
                            } else {
                                if (currentRoute != "update" && currentRoute != "listen_together/chat" && currentRoute != "ambient_mode") {
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
                                        BottomSheetPlayerHost(
                                            state = playerBottomSheetState,
                                            navController = navController,
                                            pureBlack = pureBlack
                                        )
                                    }
                                }

                                // Same opaque gesture-strip as above, on the rail / wide / TV path. The
                                // navigation rail itself is untouched (still [AppNavigationRail]); only
                                // this decorative rectangle is skipped, so the new player's bloom reaches
                                // the bottom edge there too.
                                if (!newUiShell) {
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
                                        // The same options as before, named once — see [navigateAsTab].
                                        navController.navigateAsTab(screen.route)
                                    }
                                }
                            }

                            val onRailSearchLongClick: () -> Unit = remember(navController) {
                                {
                                    // In-app entry (nav-rail search long-press): auto-start recognizing.
                                    navController.navigate("recognition?autoStart=true") {
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
                                    //
                                    // `!newUiShell` (0.6.148): with the new shell neither sampler is
                                    // composed — the nav bar is AuraNavigationBar, not
                                    // FloatingNavigationToolbar, and AuraMiniPlayer replaces the classic
                                    // mini in every orientation — yet `anyComponentEnabled` only reads the
                                    // two per-surface PREFERENCES, which both default to true. So every
                                    // HIGH-tier device that the 0.6.127 order switched glass ON for was
                                    // re-recording the whole NavHost into an offscreen layer, on every
                                    // frame that invalidated, feeding a sampler that does not exist. Pure
                                    // heat. With the flag OFF the term disappears and the condition is the
                                    // one that shipped.
                                    modifier = Modifier
                                        .then(
                                            if (!newUiShell && glassEffectConfig.globalEnabled && glassEffectConfig.anyComponentEnabled) {
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



                    // The "shared song" dialog used to live here. `sharedSong` was only ever assigned
                    // null, so the dialog could never appear. Sharing a YouTube/YTM link into Aura is
                    // handled live by handleDeepLinkIntent() below, which plays/navigates directly.

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
                                // The FOURTH raw door, and the last one. This dialog can hand back a
                                // TOP-LEVEL route ("settings" is one under the new shell, and so is every
                                // Screens.MainScreens entry). Pushing one of those on top of whatever tab
                                // is showing stacks two top-level destinations, and the next tab tap saves
                                // them as ONE deque keyed by the deepest — after which that tab reopens
                                // the wrong screen for the rest of the session. Detail routes (album/,
                                // artist/, settings/xxx ...) must NOT go through navigateAsTab: it pops to
                                // the start destination, which would throw away the caller's back stack.
                                val topLevel = route == "settings" ||
                                    Screens.MainScreens.any { it.route == route }
                                if (topLevel) navController.navigateAsTab(route)
                                else navController.navigate(route)
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

                    if (showBatteryReliabilityDialog) {
                        iad1tya.echo.music.ui.screens.BackgroundReliabilityDialog(
                            onAllowBattery = {
                                setBatteryReliabilityPromptShown(true)
                                showBatteryReliabilityDialog = false
                                iad1tya.echo.music.utils.BackgroundReliability
                                    .requestIgnoreBatteryOptimizations(this@MainActivity)
                            },
                            onOpenAutostart = {
                                setBatteryReliabilityPromptShown(true)
                                showBatteryReliabilityDialog = false
                                iad1tya.echo.music.utils.BackgroundReliability.openAppDetails(this@MainActivity)
                            },
                            onDismiss = {
                                setBatteryReliabilityPromptShown(true)
                                showBatteryReliabilityDialog = false
                            },
                        )
                    }
                }
            }
        }
    }

    private fun handleDeepLinkIntent(intent: Intent, navController: NavHostController) {
        // Launcher shortcuts ("Buscar" / "Biblioteca"). A COLD start picks the start destination from the
        // same action while composing; a WARM start (the app is singleTask, so it arrives through
        // onNewIntent) never re-runs that remember(), which is why the shortcuts did nothing once the app
        // was already open. navigateToReentryTarget keeps the cold-start case a no-op AND stops the warm
        // one from wiping the user's chain: it used to popUpTo(startDestination), so tapping a shortcut
        // while three screens deep reset you through Home.
        if (intent.action == ACTION_SEARCH || intent.action == ACTION_LIBRARY) {
            val route = if (intent.action == ACTION_SEARCH) Screens.Search.route else Screens.Library.route
            intent.action = null
            runCatching { navController.navigateToReentryTarget(route) }
            return
        }

        // Playlist widget: a card tap opens the app on that playlist/collection. Without this branch the
        // activity launched and the intent fell through, so the app just opened wherever it had been.
        // navigateToReentryTarget also stops N taps on the same card from stacking N copies of the
        // playlist (a bare launchSingleTop only de-dupes when the target is already on TOP).
        if (intent.action == ACTION_OPEN_WIDGET_TARGET) {
            val targetType = intent.getStringExtra(EXTRA_WIDGET_TARGET_TYPE)
            val targetId = intent.getStringExtra(EXTRA_WIDGET_TARGET_ID)?.takeIf { it.isNotBlank() }
            intent.action = null
            intent.removeExtra(EXTRA_WIDGET_TARGET_TYPE)
            intent.removeExtra(EXTRA_WIDGET_TARGET_ID)
            val route = when (targetType) {
                PlaylistWidgetReceiver.TARGET_TYPE_LIKED -> "auto_playlist/liked"
                PlaylistWidgetReceiver.TARGET_TYPE_DOWNLOADED -> "auto_playlist/downloaded"
                PlaylistWidgetReceiver.TARGET_TYPE_TOP -> targetId?.let { "top_playlist/$it" }
                PlaylistWidgetReceiver.TARGET_TYPE_LOCAL -> targetId?.let { "local_playlist/$it" }
                PlaylistWidgetReceiver.TARGET_TYPE_ONLINE -> targetId?.let { "online_playlist/$it" }
                else -> null
            } ?: return
            runCatching { navController.navigateToReentryTarget(route) }
            return
        }

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

        // Tidal OAuth redirect (echomusic://tidal-callback?code=...&state=..., or ?error=... when the
        // user cancels): this is an auth handshake, not content to open. MUST be handled before the
        // Listen Together branch below — that one keys off getQueryParameter("code") too, and an OAuth
        // authorization code must never be mistaken for a room code.
        if (uri.scheme == "echomusic" && uri.host.equals("tidal-callback", ignoreCase = true)) {
            TidalAuthCallbackBus.emit(
                code = uri.getQueryParameter("code"),
                state = uri.getQueryParameter("state"),
                error = uri.getQueryParameter("error"),
            )
            return
        }

        val coroutineScope = lifecycle.coroutineScope

        // ── Shared helpers so every YouTube/YTM link shape below reuses the SAME play/navigate paths.

        // Enqueue + play a single video (optionally inside a playlist/mix context). This is how the app
        // "opens a song": it does NOT push a screen — it hands the videoId to the player via YouTubeQueue.
        fun playVideo(videoId: String, playlistId: String?) {
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
                }.onFailure { reportException(it) }
            }
        }

        // Enqueue + play a whole playlist/mix (no specific starting video).
        fun playPlaylist(playlistId: String) {
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
                }.onFailure { reportException(it) }
            }
        }

        // A YTM browseId (from music.youtube.com/browse/<id> or /channel/<id>) is polymorphic: its PREFIX
        // decides the screen. The old code always opened "album/<id>", so channels, playlists and podcasts
        // shared as /browse/ links wrongly landed on the album screen (blank) or dropped. Route by prefix:
        //   MPREb… = album · UC…/MPLA… = artist (channel) · VL… = playlist (innertube re-adds the VL prefix,
        //   so strip it here) · anything else (MPSP podcasts, moods, FEmusic…) = the generic browse grid.
        fun navigateBrowseId(browseId: String) {
            // Guard degenerate ids: a bare "VL" (empty playlist id) or an empty browseId would navigate
            // to a route with an empty required arg and throw IllegalArgumentException. Real YT/YTM share
            // links never produce these, but a malformed link must no-op (open the app), not crash.
            if (browseId.isBlank()) return
            when {
                browseId.startsWith("MPREb") -> navController.navigate("album/$browseId")
                browseId.startsWith("UC") || browseId.startsWith("MPLA") -> navController.navigate("artist/$browseId")
                browseId.startsWith("VL") ->
                    browseId.removePrefix("VL").takeIf { it.isNotBlank() }
                        ?.let { navController.navigate("online_playlist/$it") }
                else -> navController.navigate("browse/$browseId")
            }
        }

        val listenCode = uri.getQueryParameter("code")
            ?: uri.getQueryParameter("room")
            ?: uri.pathSegments.getOrNull(1)
        val isListenLink = uri.pathSegments.firstOrNull() == "listen" || uri.host?.equals("listen", ignoreCase = true) == true
        if (!listenCode.isNullOrBlank() && isListenLink) {
            val username = dataStore.get(ListenTogetherUsernameKey, "").ifBlank { "Guest" }
            listenTogetherManager.joinRoom(listenCode, username)
            return
        }

        // Legacy "open in YouTube" scheme: vnd.youtube:<id>, vnd.youtube://<id>, vnd.youtube.launch:<id>,
        // or vnd.youtube://…/watch?v=<id>. The video id is the scheme-specific part (or a v= param).
        if (uri.scheme?.startsWith("vnd.youtube") == true) {
            val ssp = uri.schemeSpecificPart.orEmpty()
            val videoId = when {
                ssp.contains("v=") -> ssp.substringAfter("v=").substringBefore("&")
                ssp.startsWith("//") -> ssp.removePrefix("//").substringAfterLast('/').substringBefore('?').substringBefore('&')
                else -> ssp.substringBefore('?').substringBefore('&')
            }.trim()
            if (videoId.isNotBlank()) playVideo(videoId, uri.getQueryParameter("list"))
            return
        }

        when (val path = uri.pathSegments.firstOrNull()) {
            // /playlist?list=… — OLAK5uy_ ids are album playlists, so resolve them to the album screen;
            // everything else opens the online playlist screen.
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

            // /browse/<id> — resolve album vs artist vs playlist vs podcast by prefix (see helper).
            "browse" -> uri.lastPathSegment?.let { navigateBrowseId(it) }

            // /channel/UC… is a real artist browseId → artist screen.
            "channel" -> uri.lastPathSegment?.let { artistId ->
                navController.navigate("artist/$artistId")
            }

            // /c/NAME and /user/NAME are *custom* URLs, NOT browseIds — the artist screen can't load them,
            // so fall back to a search on the name instead of dropping the link.
            "c", "user" -> uri.lastPathSegment?.let { name ->
                navController.navigate("search/${URLEncoder.encode(name, "UTF-8")}")
            }

            "search" -> {
                uri.getQueryParameter("q")?.let {
                    navController.navigate("search/${URLEncoder.encode(it, "UTF-8")}")
                }
            }

            // All the direct-video shapes: /watch?v=ID, /embed/ID, /v/ID, /shorts/ID (youtu.be/ID is
            // handled in the else branch, since its id is the FIRST path segment).
            "watch", "embed", "v", "shorts" -> {
                val videoId = if (path == "watch") uri.getQueryParameter("v") else uri.lastPathSegment
                val playlistId = uri.getQueryParameter("list")
                when {
                    !videoId.isNullOrBlank() -> playVideo(videoId, playlistId)   // song (in playlist context if present)
                    !playlistId.isNullOrBlank() -> playPlaylist(playlistId)      // e.g. /watch?list=… with no v
                    else -> { /* nothing usable — leave the app open */ }
                }
            }

            else -> {
                val playlistId = uri.getQueryParameter("list")
                when {
                    // /@handle — a channel handle can't be resolved to a browseId offline; search for it.
                    path != null && path.startsWith("@") ->
                        navController.navigate("search/${URLEncoder.encode(path.removePrefix("@"), "UTF-8")}")

                    // youtu.be/<id> (id is the first path segment); carries an optional &list= context.
                    uri.host == "youtu.be" && !path.isNullOrBlank() -> playVideo(path, playlistId)

                    // Any other URL that still carries a ?list= (e.g. a bare list share) → play it.
                    !playlistId.isNullOrBlank() -> playPlaylist(playlistId)

                    // Unrecognized YouTube URL: don't crash — the app is already open, just stay put.
                    else -> { }
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

/** Minimum gap between two scroll haptics. Below this a drag fires one tick per motion event,
 *  which the vibrator renders as a continuous buzz (and burns battery). */
private const val SCROLL_HAPTIC_THROTTLE_MS = 100L

/**
 * Navigate to a TOP-LEVEL destination — a bottom-bar cell, or anything else that behaves like one.
 *
 * This is the standard androidx bottom-navigation contract and it is not optional: `popUpTo` the start
 * destination is what guarantees two top-level destinations are never on the stack at the same time,
 * and that guarantee is what makes `saveState`/`restoreState` safe.
 *
 * Get it wrong for ONE cell and the whole bar breaks, which is exactly what 0.6.146-beta1 shipped: the
 * Ajustes cell navigated with `launchSingleTop` only, so Ajustes sat on top of Biblioteca. The next
 * Biblioteca tap popped both under `saveState`, and androidx-navigation files a multi-entry pop as a
 * SINGLE saved deque keyed by the DEEPEST popped destination — `library`. `restoreState` on that same
 * tap then restored the whole deque, Ajustes included, so Biblioteca opened Ajustes. Forever: the
 * restore re-creates the shape that produces the save.
 *
 * Extracted so the four cells cannot drift apart again; it is the exact options block the other three
 * already used, so nothing about them changes.
 *
 * `internal`, not private, ON PURPOSE: the bottom bar is not the only door to a top-level destination.
 * `AuraPlayerMenu` ("Ir a > Ajustes") and `AuraSettingsScreen` ("Escuchar juntos") both navigate to one
 * raw today and will poison the same saved deque; they must route through here rather than re-type the
 * options and get one of the three wrong.
 */
internal fun NavController.navigateAsTab(route: String) {
    navigate(route) {
        popUpTo(graph.startDestinationId) {
            saveState = true
        }
        launchSingleTop = true
        restoreState = true
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
