

@file:Suppress("DEPRECATION")

package iad1tya.echo.music.playback

import iad1tya.echo.music.utils.ShareLinks

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import iad1tya.echo.music.utils.localeAwareContext
import android.content.Intent
import android.content.IntentFilter
import android.database.SQLException
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.audiofx.AudioEffect
import android.media.audiofx.LoudnessEnhancer
import android.net.ConnectivityManager
import android.os.Binder
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.getSystemService
import androidx.core.net.toUri
import androidx.datastore.preferences.core.edit
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.Player.EVENT_POSITION_DISCONTINUITY
import androidx.media3.common.Player.EVENT_TIMELINE_CHANGED
import androidx.media3.common.Player.REPEAT_MODE_ALL
import androidx.media3.common.Player.REPEAT_MODE_OFF
import androidx.media3.common.Player.REPEAT_MODE_ONE
import androidx.media3.common.Player.STATE_IDLE
import androidx.media3.common.Timeline
import androidx.media3.common.audio.SonicAudioProcessor
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.analytics.PlaybackStats
import androidx.media3.exoplayer.analytics.PlaybackStatsListener
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.audio.SilenceSkippingAudioProcessor
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.ShuffleOrder.DefaultShuffleOrder
import androidx.media3.extractor.ExtractorsFactory
import androidx.media3.extractor.mkv.MatroskaExtractor
import androidx.media3.extractor.mp4.FragmentedMp4Extractor
import androidx.media3.session.CommandButton
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaController
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.MoreExecutors
import com.music.innertube.YouTube
import com.music.innertube.models.SongItem
import com.music.innertube.models.WatchEndpoint
import iad1tya.echo.music.MainActivity
import iad1tya.echo.music.R
import iad1tya.echo.music.constants.AudioEnhanceEnabledKey
import iad1tya.echo.music.constants.AudioNormalizationKey
import iad1tya.echo.music.constants.SafeVolumeEnabledKey
import iad1tya.echo.music.constants.AudioOffload
import iad1tya.echo.music.constants.AudioQualityKey
import iad1tya.echo.music.constants.AutoDownloadOnLikeKey
import iad1tya.echo.music.constants.AutoLoadMoreKey
import iad1tya.echo.music.constants.KeepGenreLaneKey
import iad1tya.echo.music.constants.AutoSkipNextOnErrorKey
import iad1tya.echo.music.constants.CrossfadeDurationKey
import iad1tya.echo.music.constants.CrossfadeEnabledKey
import iad1tya.echo.music.constants.SpectrumVisualizerEnabledKey
import iad1tya.echo.music.constants.CrossfadeGaplessKey
import iad1tya.echo.music.constants.CrossfadeCurveKey
import iad1tya.echo.music.constants.DisableLoadMoreWhenRepeatAllKey
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import iad1tya.echo.music.constants.DiscordActivityNameKey
import iad1tya.echo.music.constants.DiscordActivityTypeKey
import iad1tya.echo.music.constants.DiscordAdvancedModeKey
import iad1tya.echo.music.constants.DiscordButton1TextKey
import iad1tya.echo.music.constants.DiscordButton1VisibleKey
import iad1tya.echo.music.constants.DiscordButton2TextKey
import iad1tya.echo.music.constants.DiscordButton2VisibleKey
import iad1tya.echo.music.constants.DiscordStatusKey
import iad1tya.echo.music.constants.DiscordTokenKey
import iad1tya.echo.music.constants.DiscordUseDetailsKey
import iad1tya.echo.music.constants.EnableDiscordRPCKey
import iad1tya.echo.music.constants.EnableLastFMScrobblingKey
import iad1tya.echo.music.constants.HideExplicitKey
import iad1tya.echo.music.constants.HideVideoSongsKey
import iad1tya.echo.music.constants.HistoryDuration
import iad1tya.echo.music.constants.LastFMUseNowPlaying
import iad1tya.echo.music.constants.MediaSessionConstants.CommandToggleLike
import iad1tya.echo.music.constants.MediaSessionConstants.CommandToggleRepeatMode
import iad1tya.echo.music.constants.MediaSessionConstants.CommandToggleShuffle
import iad1tya.echo.music.constants.MediaSessionConstants.CommandToggleStartRadio
import iad1tya.echo.music.constants.PauseListenHistoryKey
import iad1tya.echo.music.constants.PauseOnMute
import iad1tya.echo.music.constants.PersistentQueueKey
import iad1tya.echo.music.constants.PersistentShuffleAcrossQueuesKey
import iad1tya.echo.music.constants.PlayerVolumeKey
import iad1tya.echo.music.constants.RememberShuffleAndRepeatKey
import iad1tya.echo.music.constants.RepeatModeKey
import iad1tya.echo.music.constants.ResumeOnBluetoothConnectKey
import iad1tya.echo.music.constants.ScrobbleDelayPercentKey
import iad1tya.echo.music.constants.ScrobbleDelaySecondsKey
import iad1tya.echo.music.constants.ScrobbleMinSongDurationKey
import iad1tya.echo.music.constants.ShowLyricsKey
import iad1tya.echo.music.constants.ShuffleModeKey
import iad1tya.echo.music.constants.ShufflePlaylistFirstKey
import iad1tya.echo.music.constants.PreventDuplicateTracksInQueueKey
import iad1tya.echo.music.constants.SimilarContent
import iad1tya.echo.music.constants.SkipSilenceInstantKey
import iad1tya.echo.music.constants.SkipSilenceKey
import iad1tya.echo.music.constants.IpVersionKey
import com.music.innertube.models.IpVersion
import okhttp3.Dns
import java.net.InetAddress
import java.net.Inet4Address
import java.net.Inet6Address
import iad1tya.echo.music.db.MusicDatabase
import iad1tya.echo.music.db.entities.Event
import iad1tya.echo.music.db.entities.FormatEntity
import iad1tya.echo.music.db.entities.LyricsEntity
import iad1tya.echo.music.db.entities.RelatedSongMap
import iad1tya.echo.music.db.entities.Song
import iad1tya.echo.music.di.DownloadCache
import iad1tya.echo.music.di.PlayerCache
import iad1tya.echo.music.eq.EqualizerService
import iad1tya.echo.music.eq.audio.AudioEnhanceProcessor
import iad1tya.echo.music.eq.audio.JrDspAudioProcessor
import iad1tya.echo.music.eq.audio.CustomEqualizerAudioProcessor
import iad1tya.echo.music.eq.audio.NormalizationGainAudioProcessor
import iad1tya.echo.music.eq.audio.SpectrumAudioProcessor
import iad1tya.echo.music.eq.audio.TruePeakLimiterAudioProcessor
import iad1tya.echo.music.eq.audio.normalizationMultiplier
import iad1tya.echo.music.eq.audio.loudnessMakeupDb
import iad1tya.echo.music.eq.audio.dbToLinear
import iad1tya.echo.music.eq.audio.effectiveLoudnessDb
import iad1tya.echo.music.eq.audio.SpectrumBus
import iad1tya.echo.music.eq.data.EQProfileRepository
import iad1tya.echo.music.extensions.SilentHandler
import iad1tya.echo.music.extensions.collect
import iad1tya.echo.music.extensions.collectLatest
import iad1tya.echo.music.extensions.currentMetadata
import iad1tya.echo.music.extensions.findNextMediaItemById
import iad1tya.echo.music.extensions.mediaItems
import iad1tya.echo.music.extensions.metadata
import iad1tya.echo.music.extensions.setOffloadEnabled
import iad1tya.echo.music.extensions.toEnum
import iad1tya.echo.music.extensions.toMediaItem
import iad1tya.echo.music.extensions.toPersistQueue
import iad1tya.echo.music.extensions.toQueue
import iad1tya.echo.music.lyrics.LyricsHelper
import iad1tya.echo.music.models.PersistPlayerState
import iad1tya.echo.music.models.PersistQueue
import iad1tya.echo.music.models.toMediaMetadata
import iad1tya.echo.music.playback.audio.SilenceDetectorAudioProcessor
import iad1tya.echo.music.playback.queues.EmptyQueue
import iad1tya.echo.music.playback.queues.Queue
import iad1tya.echo.music.playback.queues.YouTubeQueue
import iad1tya.echo.music.playback.queues.filterExplicit
import iad1tya.echo.music.playback.queues.filterVideoSongs
import iad1tya.echo.music.utils.CoilBitmapLoader
import iad1tya.echo.music.utils.DiscordRPC
import iad1tya.echo.music.utils.NetworkConnectivityObserver
import iad1tya.echo.music.utils.ScrobbleManager
import iad1tya.echo.music.utils.SyncUtils
import iad1tya.echo.music.utils.YTPlayerUtils
import iad1tya.echo.music.utils.dataStore
import iad1tya.echo.music.utils.get
import iad1tya.echo.music.utils.reportException
import iad1tya.echo.music.widget.EchoMusicWidgetManager
import iad1tya.echo.music.widget.MusicWidgetReceiver
import dagger.hilt.android.AndroidEntryPoint
import iad1tya.echo.music.utils.isLocalMediaId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import timber.log.Timber
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.time.LocalDateTime
import javax.inject.Inject
import kotlin.coroutines.coroutineContext
import kotlin.time.Duration.Companion.seconds

private const val INSTANT_SILENCE_SKIP_STEP_MS = 15_000L
private const val INSTANT_SILENCE_SKIP_SETTLE_MS = 350L

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@androidx.annotation.OptIn(UnstableApi::class)
@AndroidEntryPoint
class MusicService :
    MediaLibraryService(),
    Player.Listener,
    PlaybackStatsListener.Callback {

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(localeAwareContext(newBase))
    }

    @Inject
    lateinit var database: MusicDatabase

    @Inject
    lateinit var lyricsHelper: LyricsHelper

    @Inject
    lateinit var syncUtils: SyncUtils

    @Inject
    lateinit var mediaLibrarySessionCallback: MediaLibrarySessionCallback

    @Inject
    lateinit var equalizerService: EqualizerService

    @Inject
    lateinit var eqProfileRepository: EQProfileRepository

    @Inject
    lateinit var widgetManager: EchoMusicWidgetManager

    @Inject
    lateinit var listenTogetherManager: iad1tya.echo.music.listentogether.ListenTogetherManager

    @Inject
    lateinit var podcastProgressStore: iad1tya.echo.music.podcast.PodcastProgressStore

    @Inject
    lateinit var dislikeStore: iad1tya.echo.music.dislike.DislikeStore


    private lateinit var audioManager: AudioManager
    private var audioFocusRequest: AudioFocusRequest? = null
    private var lastAudioFocusState = AudioManager.AUDIOFOCUS_NONE
    private var wasPlayingBeforeAudioFocusLoss = false
    private var hasAudioFocus = false
    private var reentrantFocusGain = false
    private var wasPlayingBeforeVolumeMute = false
    private var isPausedByVolumeMute = false
    var preferredDeviceId: Int? = null 
        private set

    private var crossfadeEnabled = false
    private var crossfadeDuration = 5000f
    private var crossfadeGapless = true
    private var crossfadeTriggerJob: Job? = null
    // Builds + buffers the incoming player a few seconds BEFORE the fade so the transition has no gap.
    private var crossfadePreloadJob: Job? = null

    private val secondaryPlayerListener = object : Player.Listener {
        override fun onPlayerError(error: PlaybackException) {
            Timber.tag(TAG).e(error, "Secondary player error")
            secondaryPlayer?.stop()
            secondaryPlayer?.clearMediaItems()
            secondaryPlayer?.let { playerNormProcessors.remove(it); playerLimiterProcessors.remove(it) }
            secondaryPlayer = null
        }
    }

    private var scope = CoroutineScope(Dispatchers.Main) + Job()

    private val binder = MusicBinder()

    inner class MusicBinder : Binder() {
        val service: MusicService
            get() = this@MusicService
    }

    private lateinit var connectivityManager: ConnectivityManager
    lateinit var connectivityObserver: NetworkConnectivityObserver
    val waitingForNetworkConnection = MutableStateFlow(false)
    private val isNetworkConnected = MutableStateFlow(false)

    private lateinit var audioQuality: iad1tya.echo.music.constants.AudioQuality
    private lateinit var ipVersion: IpVersion

    private var currentQueue: Queue = EmptyQueue
    var queueTitle: String? = null

    val currentMediaMetadata = MutableStateFlow<iad1tya.echo.music.models.MediaMetadata?>(null)
    private val currentSong =
        currentMediaMetadata
            .flatMapLatest { mediaMetadata ->
                database.song(mediaMetadata?.id)
            }.stateIn(scope, SharingStarted.Lazily, null)
    private val currentFormat =
        currentMediaMetadata.flatMapLatest { mediaMetadata ->
            database.format(mediaMetadata?.id)
        }

    lateinit var playerVolume: MutableStateFlow<Float>
    val isMuted = MutableStateFlow(false)

    fun toggleMute() {
        val newMutedState = !isMuted.value
        isMuted.value = newMutedState
        
        player.volume = if (newMutedState) 0f else playerVolume.value
    }

    fun setMuted(muted: Boolean) {
        isMuted.value = muted
        
        
        player.volume = if (muted) 0f else playerVolume.value
    }

    fun setPreferredAudioDevice(deviceId: Int?) { 
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            val deviceInfo = devices.find { it.id == deviceId }
            player.setPreferredAudioDevice(deviceInfo)
            preferredDeviceId = deviceId
        }
    }


    lateinit var sleepTimer: SleepTimer

    @Inject
    @PlayerCache
    lateinit var playerCache: SimpleCache

    @Inject
    @DownloadCache
    lateinit var downloadCache: SimpleCache

    lateinit var player: ExoPlayer
        private set
    private var secondaryPlayer: ExoPlayer? = null
    private var fadingPlayer: ExoPlayer? = null
    private var isCrossfading = false
    /** Read-only view for PlayerConnection: a spurious null-item transition during a crossfade swap must not
     *  blank the now-playing UI, but outside a crossfade a null transition is real and should update. */
    val crossfadingNow: Boolean get() = isCrossfading
    private var crossfadeJob: Job? = null

    private lateinit var mediaSession: MediaLibrarySession

    
    private val playerInitialized = MutableStateFlow(false)
    val isPlayerReady: kotlinx.coroutines.flow.StateFlow<Boolean> = playerInitialized.asStateFlow()

    
    private val _playerFlow = MutableStateFlow<ExoPlayer?>(null)
    val playerFlow = _playerFlow.asStateFlow()

    private val playerSilenceProcessors = HashMap<Player, SilenceDetectorAudioProcessor>()
    private val playerNormProcessors = HashMap<Player, NormalizationGainAudioProcessor>()
    private val playerLimiterProcessors = HashMap<Player, TruePeakLimiterAudioProcessor>()
    private val playerEqProcessors = mutableMapOf<ExoPlayer, CustomEqualizerAudioProcessor>()


    private val instantSilenceSkipEnabled = MutableStateFlow(false)

    private var isAudioEffectSessionOpened = false
    private var loudnessEnhancer: LoudnessEnhancer? = null

    // Which mediaId we've already applied REAL loudness normalization to. Used so a format re-store for
    // the SAME already-playing track (e.g. liking it kicks off an auto-download that re-saves the format)
    // doesn't recompute the gain and audibly bump the volume mid-song.
    private var lastNormalizedId: String? = null
    // The id of the track currently playing, updated from onMediaItemTransition on the player thread. Lets the
    // ResolvingDataSource loader thread know "is this the current track?" WITHOUT a runBlocking hop to Main
    // (which could deadlock/stall stream resolution when Main is busy).
    @Volatile private var currentPlayingMediaId: String? = null
    private var lastNormalizedHadLoudness: Boolean = false
    // The gain/makeup actually applied for the current track. Re-asserted whenever setupLoudnessEnhancer is
    // re-invoked for the SAME track (e.g. an audio-effect-session re-open / processor flush when the screen
    // turns off or playback blips), so the chain can never be left at a stale/unity (raw, LOUDER) level — the
    // "volume rises on its own when the screen is off" bug. Re-asserting (not recomputing) means no mid-song jump.
    @Volatile private var lastAppliedGain: Float = 1.0f
    @Volatile private var lastAppliedMakeup: Float = 1.0f
    // In-memory loudness hint cache (mediaId → resolved effectiveLoudnessDb). Populated by setupLoudnessEnhancer
    // (every track start) and the upcoming-track preload (Fix A). Lets the crossfade pre-level (Fix B) resolve
    // the incoming track's gain SYNCHRONOUSLY without a blocking disk read on the main thread — the crossfade
    // runs on Dispatchers.Main and a runBlocking Room/DataStore read there stutters the transition / risks ANR.
    private val loudnessHintCache = java.util.concurrent.ConcurrentHashMap<String, Double>()
    // AudioNormalization toggle mirrored into memory (collector in onCreate) so the crossfade pre-level needn't
    // block on a DataStore read either.
    @Volatile private var normalizationEnabledHint: Boolean = true
    // Mirror of SafeVolumeEnabledKey for the crossfade pre-level (so the incoming secondary player gets
    // Safe Volume from the first fade-in sample, not only after the swap settles).
    @Volatile private var safeVolumeEnabledHint: Boolean = false
    @Volatile private var audioOffloadHint: Boolean = false
    // The track id whose MEASURED loudness we've already committed to the gains + DB (so the one-shot
    // measurement-driven re-level fires at most ONCE per song, never re-levels twice). Null = none yet.
    @Volatile private var measuredAppliedForId: String? = null

    private var discordRpc: DiscordRPC? = null
    private var lastPlaybackSpeed = 1.0f
    private var discordUpdateJob: kotlinx.coroutines.Job? = null

    private var scrobbleManager: ScrobbleManager? = null

    val automixItems = MutableStateFlow<List<MediaItem>>(emptyList())

    // Cached on-device taste model (see AffinityEngine), used to order what plays next (autoplay/radio)
    // by your taste and to drop "No me gusta". Rebuilt at most every few minutes.
    @Volatile private var cachedTaste: iad1tya.echo.music.reco.TasteProfile? = null
    @Volatile private var cachedTasteAt: Long = 0L

    
    private var originalQueueSize: Int = 0
    // B5 — anti-repeat shuffle memory: media IDs already played in the current shuffle session. While
    // shuffling, not-yet-played songs are ordered ahead of these, so nothing repeats until the whole pool is
    // exhausted (then it auto-resets for a new cycle). Reset whenever shuffle is (re)enabled.
    private val shufflePlayedIds = LinkedHashSet<String>()
    /** Recently-played media ids (bounded, most-recent last) so autoplay/radio don't resurface a song you
     *  JUST heard. A soft demotion (not a hard drop) — see [orderedByTaste] — so it can never dead-end the queue. */
    private val recentRadioIds = LinkedHashSet<String>()
    private fun rememberRecentRadioId(id: String?) {
        if (id.isNullOrBlank()) return
        synchronized(recentRadioIds) {
            recentRadioIds.remove(id) // move-to-most-recent
            recentRadioIds.add(id)
            while (recentRadioIds.size > 60) {
                val it = recentRadioIds.iterator()
                if (it.hasNext()) { it.next(); it.remove() } else break
            }
        }
    }
    // B3 — guards against double-seeding a radio when a finite queue ends: true while a startRadioSeamlessly
    // fetch is in flight, so onMediaItemTransition can't fire a second (racing) radio fetch over the same end.
    @Volatile private var radioSeedInFlight = false
    // True when a radio seed was started from the STATE_ENDED safety net (the queue truly ended): once the
    // seed appends items, advance+play into them so predictive infinite playback resumes with no manual action.
    @Volatile private var resumeAfterSeed = false

    private var consecutivePlaybackErr = 0
    private var retryJob: Job? = null
    private var retryCount = 0
    private var silenceSkipJob: Job? = null

    
    private val songUrlCache = HashMap<String, Pair<String, Long>>()

    
    private val bypassCacheForQualityChange = mutableSetOf<String>()

    // Video mode is INTEGRATED into the main player (one engine). videoMode = sticky on/off intent;
    // videoUrl = resolved muxed URL of the current video track (null while resolving → UI spinner).
    // videoModeMediaId = the track whose source is currently the video stream; videoModeOriginalUri
    // restores that track to its normal audio source.
    val playbackState = PlaybackStateManager()

    private var videoModeMediaId: String?
        get() = playbackState.videoModeMediaId
        set(value) { playbackState.videoModeMediaId = value }

    private var videoModeOriginalUri: String?
        get() = playbackState.videoModeOriginalUri
        set(value) { playbackState.videoModeOriginalUri = value }

    private var videoModeIsMuxedPodcast: Boolean
        get() = playbackState.videoModeIsMuxedPodcast
        set(value) { playbackState.videoModeIsMuxedPodcast = value }

    private val videoUrlCache = HashMap<String, Pair<String, Long>>()

    private var userHasUsedVideo: Boolean
        get() = playbackState.userHasUsedVideo
        set(value) { playbackState.userHasUsedVideo = value }

    private val _mixActive get() = playbackState.mixActive
    val mixActive: kotlinx.coroutines.flow.StateFlow<Boolean> get() = playbackState.mixActive

    private val _videoMode get() = playbackState.videoMode
    val videoMode: kotlinx.coroutines.flow.StateFlow<Boolean> get() = playbackState.videoMode

    private val _videoUrl get() = playbackState.videoUrl
    val videoUrl: kotlinx.coroutines.flow.StateFlow<String?> get() = playbackState.videoUrl.asStateFlow()

    private val preloadedVideoOriginalUris = mutableMapOf<String, String>()

    
    private var currentMediaIdRetryCount = mutableMapOf<String, Int>()
    private val MAX_RETRY_PER_SONG = 3
    private val RETRY_DELAY_MS = 1000L

    
    private val recentlyFailedSongs = mutableSetOf<String>()
    private var failedSongsClearJob: Job? = null

    
    var castConnectionHandler: CastConnectionHandler? = null
    // Cast is initialized lazily on the first playback (see initializeCast) so the Cast framework never
    // tries to promote this service to the foreground while the app is in the background.
    private var castInitAttempted = false

    // Periodic podcast-progress + position persistence; runs ONLY while playing (battery — see onCreate).
    private var periodicPersistJob: kotlinx.coroutines.Job? = null
        private set

    private val screenStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    if (!player.isPlaying) {
                        scope.launch(Dispatchers.IO) {
                            discordRpc?.closeRPC()
                        }
                    }
                }
                Intent.ACTION_SCREEN_ON -> {
                    if (player.isPlaying) {
                        scope.launch {
                            currentSong.value?.let { song ->
                                updateDiscordRPC(song)
                            }
                        }
                    }
                }
            }
        }
    }

    private val audioDeviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) {
            super.onAudioDevicesAdded(addedDevices)
            val hasBluetooth = addedDevices?.any {
                it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
                it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO
            } == true

            if (hasBluetooth) {
                if (dataStore.get(ResumeOnBluetoothConnectKey, false)) {
                    if (player.playbackState == Player.STATE_READY && !player.isPlaying) {
                        player.play()
                    }
                }
            }
            applyEqForCurrentOutput()
        }

        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) {
            super.onAudioDevicesRemoved(removedDevices)
            applyEqForCurrentOutput()
        }
    }

    /**
     * PowerAmp-style per-output EQ: when the active audio output changes (e.g. a Bluetooth speaker
     * connects/disconnects), apply the EQ profile the user assigned to it — or do nothing if none.
     * Switches the EQ bands live, reflects them in the EQ screen, and persists the choice.
     */
    private fun applyEqForCurrentOutput() {
        if (!::eqProfileRepository.isInitialized || !::equalizerService.isInitialized) return
        scope.launch {
            val key = iad1tya.echo.music.eq.data.EqDeviceProfileStore.currentOutputKey(this@MusicService)
            val profileId = iad1tya.echo.music.eq.data.EqDeviceProfileStore
                .assignedProfileId(this@MusicService, key) ?: return@launch
            val profile = eqProfileRepository.getAllProfiles().firstOrNull { it.id == profileId } ?: return@launch
            getSharedPreferences("echo_eq_prefs", Context.MODE_PRIVATE).edit().apply {
                profile.bands.forEachIndexed { i, b -> putFloat("band24_$i", b.gain.toFloat()) }
                putFloat("preampDb", profile.preamp.toFloat())
                putBoolean("enabled", true)
            }.apply()
            // Sync unsavedProfile too: the combine(activeProfile, unsavedProfile){ unsaved ?: active }
            // observer prefers unsaved, so a stale unsaved (from a prior manual edit) would otherwise
            // override this device profile the instant setActiveProfile fires.
            eqProfileRepository.setUnsavedProfile(profile)
            eqProfileRepository.setActiveProfile(profile.id)
            equalizerService.applyProfile(profile)
            runCatching {
                iad1tya.echo.music.eq.data.SoundEffectsSnapshot.apply(this@MusicService, profile.effects)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        isRunning = true

        // Catch ForegroundServiceStartNotAllowedException (e.g. when playback is (re)started while the app
        // is in the background) so it's logged/reported instead of crashing. (From upstream Echo-Music.)
        setListener(object : Listener {
            override fun onForegroundServiceStartNotAllowedException() {
                Timber.tag(TAG).e("ForegroundServiceStartNotAllowedException caught by MediaSessionService listener")
                reportException(Exception("ForegroundServiceStartNotAllowedException caught by MediaSessionService listener"))
            }
        })

        playerInitialized.value = false

        
        

        try {
            val nm = getSystemService(NotificationManager::class.java)
            nm?.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    getString(R.string.music_player),
                    NotificationManager.IMPORTANCE_LOW
                )
            )
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Failed to create notification channel")
            reportException(e)
        }

        setMediaNotificationProvider(
            DefaultMediaNotificationProvider(
                this,
                { NOTIFICATION_ID },
                CHANNEL_ID,
                R.string.music_player
            )
                .apply {
                    setSmallIcon(R.drawable.ic_launcher_nobg)
                },
        )
        player = createExoPlayer()
        player.addListener(this@MusicService)
        sleepTimer = SleepTimer(scope, player)
        player.addListener(sleepTimer)
        playerInitialized.value = true
        Timber.tag(TAG).d("Player successfully initialized")

        // Warm up the poToken WebView shortly after startup so the FIRST song starts faster (the slow
        // botguard/WebView init happens ahead of play time instead of when you press play). Fully guarded;
        // no-ops if the session/WebView isn't ready yet. Delayed so cipher init + visitorData settle first.
        scope.launch(Dispatchers.IO) {
            kotlinx.coroutines.delay(500)
            runCatching { iad1tya.echo.music.utils.YTPlayerUtils.prewarmPoToken() }
        }

        // Podcast progress + periodic position persistence used to run in two while(true) loops that
        // woke the CPU every 5s/8s for the WHOLE life of the service — even while paused or idle — which
        // drained the battery. They now run only WHILE PLAYING, started/stopped from onIsPlayingChanged
        // (see startPeriodicPersist). Position is also saved once on pause so nothing is lost.

        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        abandonAudioFocus()
        setupAudioFocusRequest()

        mediaLibrarySessionCallback.apply {
            toggleLike = ::toggleLike
            toggleStartRadio = ::toggleStartRadio
            toggleLibrary = ::toggleLibrary
        }
        mediaSession =
            MediaLibrarySession
                .Builder(this, player, mediaLibrarySessionCallback)
                .setSessionActivity(
                    PendingIntent.getActivity(
                        this,
                        0,
                        Intent(this, MainActivity::class.java),
                        PendingIntent.FLAG_IMMUTABLE,
                    ),
                ).setBitmapLoader(CoilBitmapLoader(this, scope))
                .build()
        player.repeatMode = dataStore.get(RepeatModeKey, REPEAT_MODE_OFF)

        
        if (dataStore.get(RememberShuffleAndRepeatKey, true)) {
            player.shuffleModeEnabled = dataStore.get(ShuffleModeKey, false)
        }

        
        val sessionToken = SessionToken(this, ComponentName(this, MusicService::class.java))
        val controllerFuture = MediaController.Builder(this, sessionToken).buildAsync()
        controllerFuture.addListener({ controllerFuture.get() }, MoreExecutors.directExecutor())

        connectivityManager = getSystemService()!!
        connectivityObserver = NetworkConnectivityObserver(this)

        val screenStateFilter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
        }
        registerReceiver(screenStateReceiver, screenStateFilter)

        audioManager.registerAudioDeviceCallback(audioDeviceCallback, null)

        audioQuality = dataStore.get(AudioQualityKey).toEnum(iad1tya.echo.music.constants.AudioQuality.OPUS)
        ipVersion = dataStore.get(IpVersionKey).toEnum(IpVersion.AUTO)
        // Repair: a persisted ~0 volume means it was captured mid-crossfade/duck by the old bug (a real
        // "I want silence" never persists as 0 — the user pauses/mutes instead). Treat it as full.
        playerVolume = MutableStateFlow(
            dataStore.get(PlayerVolumeKey, 1f).let { if (it < 0.05f) 1f else it.coerceIn(0f, 1f) },
        )

        // Cast is initialized lazily on first playback (see initializeCast) — NOT here in onCreate,
        // which can run while the app is in the background and would crash on Android 12+.


        scope.launch {
            combine(eqProfileRepository.activeProfile, eqProfileRepository.unsavedProfile) { active, unsaved ->
                unsaved ?: active
            }.collect { profile ->
                if (profile != null) {
                    val result = equalizerService.applyProfile(profile)
                    if (result.isSuccess && player.playbackState == Player.STATE_READY && player.isPlaying) {
                        
                        
                        
                        // EQ changes are applied gaplessly in place; no re-seek (it caused stutter/stop).
                    }
                } else {
                    equalizerService.disable()
                    if (player.playbackState == Player.STATE_READY && player.isPlaying) {
                        // EQ changes are applied gaplessly in place; no re-seek (it caused stutter/stop).
                    }
                }
            }
        }

        scope.launch {
            connectivityObserver.networkStatus.collect { isConnected ->
                isNetworkConnected.value = isConnected
                if (isConnected && waitingForNetworkConnection.value) {
                    triggerRetry()
                }
                
                if (isConnected && discordRpc != null && player.isPlaying) {
                    val mediaId = player.currentMetadata?.id
                    if (mediaId != null) {
                        database.song(mediaId).first()?.let { song ->
                            updateDiscordRPC(song)
                        }
                    }
                }
            }
        }

        
        var isFirstQualityEmit = true
        scope.launch {
            dataStore.data
                .map { it[AudioQualityKey]?.let { value ->
                    iad1tya.echo.music.constants.AudioQuality.entries.find { it.name == value }
                } ?: iad1tya.echo.music.constants.AudioQuality.OPUS }
                .distinctUntilChanged()
                .collect { newQuality ->
                    val oldQuality = audioQuality
                    audioQuality = newQuality

                    
                    if (isFirstQualityEmit) {
                        isFirstQualityEmit = false
                        Timber.tag("MusicService").i("QUALITY INIT: $newQuality")
                        return@collect
                    }

                    Timber.tag("MusicService").i("QUALITY CHANGED: $oldQuality -> $newQuality")

                    Timber.tag("MusicService").i("QUALITY CHANGED: $oldQuality -> $newQuality. Will take effect for upcoming songs.")

                    val mediaId = player.currentMediaItem?.mediaId ?: return@collect
                    val currentUrl = songUrlCache[mediaId]

                    // Clear cache for upcoming songs so they fetch the new quality
                    songUrlCache.clear()
                    
                    // Restore the currently playing song's URL so it doesn't break
                    if (currentUrl != null) {
                        songUrlCache[mediaId] = currentUrl
                    }

                    // Re-trigger prefetch to fetch the next songs in the new quality
                    preloadUpcomingItems()
                }
        }

        
        scope.launch {
            dataStore.data
                .map { it[IpVersionKey]?.toEnum(IpVersion.AUTO) ?: IpVersion.AUTO }
                .distinctUntilChanged()
                .collect { newIpVersion ->
                    val oldIpVersion = ipVersion
                    ipVersion = newIpVersion

                    if (isFirstQualityEmit) return@collect

                    Timber.tag("MusicService").i("IP VERSION CHANGED: $oldIpVersion -> $newIpVersion")

                    
                    val mediaId = player.currentMediaItem?.mediaId ?: return@collect
                    val currentPosition = player.currentPosition
                    val currentIndex = player.currentMediaItemIndex
                    val wasPlaying = player.isPlaying

                    
                    songUrlCache.remove(mediaId)

                    
                    player.stop()
                    player.seekTo(currentIndex, currentPosition)
                    player.prepare()
                    if (wasPlaying) {
                        player.play()
                    }
                }
        }

        combine(playerVolume, isMuted) { volume, muted ->
            if (muted) 0f else volume
        }.collectLatest(scope) {
            player.volume = it
        }

        playerVolume.debounce(1000).collect(scope) { volume ->
            dataStore.edit { settings ->
                settings[PlayerVolumeKey] = volume
            }
        }

        currentSong.debounce(1000).collect(scope) { song ->
            updateNotification()
            updateWidgetUI(player.isPlaying)
        }

        combine(
            currentMediaMetadata.distinctUntilChangedBy { it?.id },
            dataStore.data.map { it[ShowLyricsKey] ?: false }.distinctUntilChanged(),
        ) { mediaMetadata, showLyrics ->
            mediaMetadata to showLyrics
        }.collectLatest(scope) { (mediaMetadata, showLyrics) ->
            if (showLyrics && mediaMetadata != null && database.lyrics(mediaMetadata.id)
                    .first() == null
            ) {
                val lyricsWithProvider = lyricsHelper.getLyrics(mediaMetadata)
                database.query {
                    upsert(
                        LyricsEntity(
                            id = mediaMetadata.id,
                            lyrics = lyricsWithProvider.lyrics,
                            provider = lyricsWithProvider.provider,
                        ),
                    )
                }
            }
        }

        dataStore.data
            .map { (it[SkipSilenceKey] ?: false) to (it[SkipSilenceInstantKey] ?: false) }
            .distinctUntilChanged()
            .collectLatest(scope) { (_, _) ->
                // Forced false: skipSilence interferes with Hi-Res playback and breaks video A/V sync.
                player.skipSilenceEnabled = false
                secondaryPlayer?.skipSilenceEnabled = false

                val enableInstant = false
                instantSilenceSkipEnabled.value = false

                playerSilenceProcessors.values.forEach { processor ->
                    processor.instantModeEnabled = enableInstant
                    if (!enableInstant) {
                        processor.resetTracking()
                    }
                }

                if (!enableInstant) {
                    silenceSkipJob?.cancel()
                }
            }

        combine(
            // Only re-run normalization when the LOUDNESS actually changes — not on every format-row write.
            // Liking a song triggers an auto-download that re-stores the FormatEntity; without this distinct
            // that re-store re-ran setupLoudnessEnhancer mid-song and re-applied the makeup → a sudden volume
            // jump + the limiter slamming (saturation / raspy voice). De-dup on the loudness fields kills it.
            currentFormat.distinctUntilChanged { a, b ->
                a?.id == b?.id &&
                    a?.loudnessDb == b?.loudnessDb &&
                    a?.perceptualLoudnessDb == b?.perceptualLoudnessDb
            },
            dataStore.data
                .map { it[AudioNormalizationKey] ?: true }
                .distinctUntilChanged(),
        ) { format, normalizeAudio ->
            format to normalizeAudio
        }.collectLatest(scope) { (format, normalizeAudio) ->
            normalizationEnabledHint = normalizeAudio // mirror to memory for the crossfade pre-level (Fix B)
            setupLoudnessEnhancer()
        }

        // Re-apply when the Safe Volume toggle changes so it takes effect live (mid-song), not just next track.
        scope.launch {
            dataStore.data
                .map { it[SafeVolumeEnabledKey] ?: false }
                .distinctUntilChanged()
                .collect {
                    safeVolumeEnabledHint = it // mirror for the crossfade pre-level
                    setupLoudnessEnhancer()
                }
        }

        dataStore.data
            .map { it[AudioEnhanceEnabledKey] ?: false }
            .distinctUntilChanged()
            .collectLatest(scope) { enabled ->
                AudioEnhanceProcessor.enabled = enabled
            }

        dataStore.data
            .map { prefs ->
                JrDspAudioProcessor.Config(
                    signatureEnabled = prefs[iad1tya.echo.music.constants.AuraSignatureToneEnabledKey] ?: true,
                    loudnessEnabled = prefs[iad1tya.echo.music.constants.JrLoudnessEnabledKey] ?: false,
                    // Virtual room (HRTF) removed — always off regardless of any old saved preference.
                    hrtfEnabled = false,
                    // Bass enhancer removed — always off regardless of any old saved preference.
                    bassEnhanceEnabled = false,
                    bassEnhanceAmount = prefs[iad1tya.echo.music.constants.JrBassEnhanceAmountKey] ?: 0.28f,
                    exciterEnabled = prefs[iad1tya.echo.music.constants.JrExciterEnabledKey] ?: false,
                    exciterAmount = prefs[iad1tya.echo.music.constants.JrExciterAmountKey] ?: 0.15f,
                    // Multiband compressor removed — always off regardless of any old saved preference.
                    mbCompEnabled = false,
                    stereoWidthEnabled = prefs[iad1tya.echo.music.constants.JrStereoWidthEnabledKey] ?: false,
                    stereoWidth = prefs[iad1tya.echo.music.constants.JrStereoWidthKey] ?: 1.0f,
                    dialogueEnabled = prefs[iad1tya.echo.music.constants.JrDialogueEnabledKey] ?: false,
                    dialogueAmount = prefs[iad1tya.echo.music.constants.JrDialogueAmountKey] ?: 0.35f,
                )
            }
            .distinctUntilChanged()
            .collectLatest(scope) { cfg ->
                JrDspAudioProcessor.config = cfg
            }

        dataStore.data
            .map { it[SpectrumVisualizerEnabledKey] ?: false }
            .distinctUntilChanged()
            .collectLatest(scope) { enabled ->
                SpectrumBus.enabled = enabled
                if (!enabled) SpectrumBus.clear()
            }

        combine(
            dataStore.data.map { it[AudioOffload] ?: false }.distinctUntilChanged(),
            // Audio offload streams compressed audio straight to the DSP hardware, BYPASSING the whole
            // AudioProcessor chain — EQ, JR DSP / Aura signature, AudioEnhance, loudness normalization AND the
            // true-peak limiter. With any of those active, offload would make them silently do nothing (and a
            // loud master could clip). So offload is only allowed when the ENTIRE chain is off. The Aura
            // signature is ON by default, so offload stays off by default (the safe direction).
            dataStore.data.map { p ->
                (p[CrossfadeEnabledKey] ?: false) ||
                    (p[AudioNormalizationKey] ?: true) ||
                    (p[iad1tya.echo.music.constants.AuraSignatureToneEnabledKey] ?: true) ||
                    (p[AudioEnhanceEnabledKey] ?: false) ||
                    (p[iad1tya.echo.music.constants.JrLoudnessEnabledKey] ?: false) ||
                    (p[iad1tya.echo.music.constants.JrExciterEnabledKey] ?: false) ||
                    (p[iad1tya.echo.music.constants.JrStereoWidthEnabledKey] ?: false) ||
                    (p[iad1tya.echo.music.constants.JrDialogueEnabledKey] ?: false)
            }.distinctUntilChanged(),
        ) { offloadPref, chainActive ->
            if (chainActive) false else offloadPref
        }.distinctUntilChanged()
        .collectLatest(scope) { useOffload ->
             audioOffloadHint = useOffload
             player.setOffloadEnabled(useOffload)
             secondaryPlayer?.setOffloadEnabled(useOffload)
        }



        combine(
            dataStore.data.map { prefs ->
                Triple(
                    prefs[CrossfadeEnabledKey] ?: false,
                    prefs[CrossfadeDurationKey] ?: 10f,
                    prefs[CrossfadeGaplessKey] ?: true
                )
            },
            listenTogetherManager.roomState
        ) { (enabled, duration, gapless), roomState ->
            
            Triple(enabled && roomState == null, duration, gapless)
        }
            .distinctUntilChanged()
            .collect(scope) { (enabled, duration, gapless) ->
                crossfadeEnabled = enabled
                crossfadeDuration = duration * 1000f 
                crossfadeGapless = gapless
            }


        if (dataStore.get(PersistentQueueKey, true)) {
            val queueFile = filesDir.resolve(PERSISTENT_QUEUE_FILE)
            if (queueFile.exists()) {
                runCatching {
                    queueFile.inputStream().use { fis ->
                        ObjectInputStream(fis).use { oos ->
                            oos.readObject() as PersistQueue
                        }
                    }
                }.onSuccess { queue ->
                    runCatching {
                        
                        val restoredQueue = queue.toQueue()
                        
                        scope.launch {
                            playerInitialized.first { it }
                            if (isActive) {
                                playQueue(
                                    queue = restoredQueue,
                                    playWhenReady = false,
                                )
                            }
                        }
                    }.onFailure { error ->
                        Timber.tag(TAG).w(error, "Failed to restore persisted queue, clearing data")
                        clearPersistedQueueFiles()
                    }
                }.onFailure { error ->
                    Timber.tag(TAG).w(error, "Failed to read persisted queue, clearing data")
                    clearPersistedQueueFiles()
                }
            }

            val automixFile = filesDir.resolve(PERSISTENT_AUTOMIX_FILE)
            if (automixFile.exists()) {
                runCatching {
                    automixFile.inputStream().use { fis ->
                        ObjectInputStream(fis).use { oos ->
                            oos.readObject() as PersistQueue
                        }
                    }
                }.onSuccess { queue ->
                    runCatching {
                        automixItems.value = queue.items.map { it.toMediaItem() }
                    }.onFailure { error ->
                        Timber.tag(TAG).w(error, "Failed to restore automix queue, clearing data")
                        clearPersistedQueueFiles()
                    }
                }.onFailure { error ->
                    Timber.tag(TAG).w(error, "Failed to read automix queue, clearing data")
                    clearPersistedQueueFiles()
                }
            }

            
            val playerStateFile = filesDir.resolve(PERSISTENT_PLAYER_STATE_FILE)
            if (playerStateFile.exists()) {
                runCatching {
                    playerStateFile.inputStream().use { fis ->
                        ObjectInputStream(fis).use { oos ->
                            oos.readObject() as PersistPlayerState
                        }
                    }
                }.onSuccess { playerState ->
                    
                    scope.launch {
                        delay(1000) 
                        
                        
                        
                        // Same repair on queue restore: a near-0 persisted volume = the old capture bug.
                        playerVolume.value = playerState.volume.let { if (it < 0.05f) 1f else it.coerceIn(0f, 1f) }

                        
                        if (playerState.currentMediaItemIndex < player.mediaItemCount) {
                            player.seekTo(playerState.currentMediaItemIndex, playerState.currentPosition)
                        }
                    }
                }.onFailure { error ->
                    Timber.tag(TAG).w(error, "Failed to read player state, clearing data")
                    clearPersistedQueueFiles()
                }
            }
        }

        
        scope.launch {
            while (isActive) {
                delay(30.seconds)
                if (dataStore.get(PersistentQueueKey, true)) {
                    saveQueueToDisk()
                }
            }
        }

        
        scope.launch {
            while (isActive) {
                delay(10.seconds)
                if (dataStore.get(PersistentQueueKey, true) && player.isPlaying) {
                    saveQueueToDisk()
                }
            }
        }
    }

    private fun createExoPlayer(isSecondary: Boolean = false): ExoPlayer {
        val eqProcessor = CustomEqualizerAudioProcessor()
        equalizerService.addAudioProcessor(eqProcessor)

        val silenceProcessor = iad1tya.echo.music.playback.audio.SilenceDetectorAudioProcessor {
            Timber.tag(TAG).d("Silence skipped")
        }
        val normProcessor = iad1tya.echo.music.eq.audio.NormalizationGainAudioProcessor()
        val limiterProcessor = iad1tya.echo.music.eq.audio.TruePeakLimiterAudioProcessor()

        val player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(createMediaSourceFactory())
            .setRenderersFactory(createRenderersFactory(silenceProcessor, eqProcessor, normProcessor, limiterProcessor))
            .setLoadControl(
                DefaultLoadControl.Builder()
                    .setBufferDurationsMs(
                        DefaultLoadControl.DEFAULT_MIN_BUFFER_MS,
                        // 120s max buffer: enough lead to ride out short connectivity drops without
                        // the runaway RAM of the old 600s (10-min) value. The previous 600s combined with
                        // a hard 32MB byte cap + prioritizeTimeOverSizeThresholds(false) starved the TIME
                        // buffer for hi-res/FLAC (32MB << 50s of FLAC), so ExoPlayer reported "buffer full"
                        // with an empty time buffer -> repeated STATE_BUFFERING micro-stalls (the audible
                        // "trabones"/cuts on playback and at the crossfade swap, which the secondary player
                        // inherited). Reconciled below.
                        120_000,
                        500,
                        DefaultLoadControl.DEFAULT_BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS,
                    )
                    // 64MB byte ceiling guards against OOM with multiple pre-loaded/crossfade players,
                    // but prioritizeTimeOverSizeThresholds(true) lets the TIME buffer win so the min/max
                    // duration is actually honored for hi-res streams instead of being clipped to the byte cap.
                    .setTargetBufferBytes(64 * 1024 * 1024)
                    .setPrioritizeTimeOverSizeThresholds(true)
                    .build(),
            )
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                false,
            )
            .setSeekBackIncrementMs(5000)
            .setSeekForwardIncrementMs(5000)
            .setDeviceVolumeControlEnabled(true)
            .build()

        playerEqProcessors[player] = eqProcessor
        playerSilenceProcessors[player] = silenceProcessor
        playerNormProcessors[player] = normProcessor
        playerLimiterProcessors[player] = limiterProcessor

        player.apply {
                setOffloadEnabled(audioOffloadHint)
                skipSilenceEnabled = false
                // The crossfade secondary player must NOT register the service listener here: it gets its
                // own secondaryPlayerListener in prepareSecondaryPlayer, and performCrossfadeSwap re-adds the
                // service listener at swap time (nextPlayer.addListener(this)). Registering it here too
                // double-registered the listener AND — via the _playerFlow publish below — pointed
                // PlayerConnection at the empty, playWhenReady=false secondary mid-transition, flipping the
                // play/pause button to "paused" until the swap landed. (Bug A-1)
                if (!isSecondary) addListener(this@MusicService)
                addAnalyticsListener(PlaybackStatsListener(false, this@MusicService))
            }
        // Only the ACTIVE player is published. Publishing the secondary (item-less, paused) made
        // PlayerConnection.updateAttachedPlayer re-read playbackState/playWhenReady from an empty player.
        // performCrossfadeSwap publishes the new active player itself once the swap completes.
        if (!isSecondary) _playerFlow.value = player
        return player
    }

    private fun setupAudioFocusRequest() {
        audioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(
                android.media.AudioAttributes.Builder()
                    .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                    .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            .setOnAudioFocusChangeListener { focusChange ->
                handleAudioFocusChange(focusChange)
            }
            .setAcceptsDelayedFocusGain(true)
            .build()
    }

    private fun handleAudioFocusChange(focusChange: Int) {
        when (focusChange) {

            AudioManager.AUDIOFOCUS_GAIN,
            AudioManager.AUDIOFOCUS_GAIN_TRANSIENT -> {
                hasAudioFocus = true

                if (wasPlayingBeforeAudioFocusLoss && !player.isPlaying && !reentrantFocusGain) {
                    reentrantFocusGain = true
                    scope.launch {
                        delay(300)
                        if (hasAudioFocus && wasPlayingBeforeAudioFocusLoss && !player.isPlaying) {
                            
                            if (castConnectionHandler?.isCasting?.value != true) {
                                player.play()
                            }
                            wasPlayingBeforeAudioFocusLoss = false
                        }
                        reentrantFocusGain = false
                    }
                }

                player.volume = if (isMuted.value) 0f else playerVolume.value
                lastAudioFocusState = focusChange
            }

            AudioManager.AUDIOFOCUS_LOSS -> {
                hasAudioFocus = false
                wasPlayingBeforeAudioFocusLoss = player.isPlaying
                if (player.isPlaying) {
                    player.pause()
                }
                abandonAudioFocus()
                lastAudioFocusState = focusChange
            }

            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                hasAudioFocus = false
                wasPlayingBeforeAudioFocusLoss = player.isPlaying
                if (player.isPlaying) {
                    player.pause()
                }
                lastAudioFocusState = focusChange
            }

            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                hasAudioFocus = false
                wasPlayingBeforeAudioFocusLoss = player.isPlaying
                if (player.isPlaying) {
                    player.volume = if (isMuted.value) 0f else (playerVolume.value * 0.2f)
                }
                lastAudioFocusState = focusChange
            }

            AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK -> {
                hasAudioFocus = true
                player.volume = if (isMuted.value) 0f else playerVolume.value
                lastAudioFocusState = focusChange
            }
        }
    }

    private fun requestAudioFocus(): Boolean {
        if (hasAudioFocus) return true

        audioFocusRequest?.let { request ->
            val result = audioManager.requestAudioFocus(request)
            hasAudioFocus = result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
            return hasAudioFocus
        }
        return false
    }

    private fun abandonAudioFocus() {
        if (hasAudioFocus) {
            audioFocusRequest?.let { request ->
                audioManager.abandonAudioFocusRequest(request)
                hasAudioFocus = false
            }
        }
    }

    private fun clearPersistedQueueFiles() {
        runCatching { filesDir.resolve(PERSISTENT_QUEUE_FILE).delete() }
        runCatching { filesDir.resolve(PERSISTENT_AUTOMIX_FILE).delete() }
        runCatching { filesDir.resolve(PERSISTENT_PLAYER_STATE_FILE).delete() }
    }

    fun hasAudioFocusForPlayback(): Boolean {
        return hasAudioFocus
    }

    private fun waitOnNetworkError() {
        if (waitingForNetworkConnection.value) return

        
        if (retryCount >= MAX_RETRY_COUNT) {
            Timber.tag(TAG).w("Max retry count ($MAX_RETRY_COUNT) reached, stopping playback")
            stopOnError()
            retryCount = 0
            return
        }

        waitingForNetworkConnection.value = true

        
        retryJob?.cancel()
        retryJob = scope.launch {
            
            val delayMs = minOf(3000L * (1 shl retryCount), 30000L)
            Timber.tag(TAG).d("Waiting ${delayMs}ms before retry attempt ${retryCount + 1}/$MAX_RETRY_COUNT")
            delay(delayMs)

            if (isNetworkConnected.value && waitingForNetworkConnection.value) {
                retryCount++
                triggerRetry()
            }
        }
    }

    private fun triggerRetry() {
        waitingForNetworkConnection.value = false
        retryJob?.cancel()

        if (player.currentMediaItem != null) {
            
            
            if (retryCount > 3) {
                Timber.tag(TAG).d("Retry count > 3, attempting to refresh stream URL")
                val currentPosition = player.currentPosition
                player.seekTo(player.currentMediaItemIndex, currentPosition)
            }
            player.prepare()
            
            
        }
    }

    private fun skipOnError() {
        
        consecutivePlaybackErr += 2
        val nextWindowIndex = player.nextMediaItemIndex

        if (consecutivePlaybackErr <= MAX_CONSECUTIVE_ERR && nextWindowIndex != C.INDEX_UNSET) {
            player.seekTo(nextWindowIndex, C.TIME_UNSET)
            player.prepare()
            
            if (castConnectionHandler?.isCasting?.value != true) {
                player.play()
            }
            return
        }

        player.pause()
        consecutivePlaybackErr = 0
    }

    private fun stopOnError() {
        player.pause()
    }

    private fun updateNotification() {
        mediaSession.setCustomLayout(
            listOf(
                CommandButton
                    .Builder()
                    .setDisplayName(
                        getString(
                            if (currentSong.value?.song?.liked ==
                                true
                            ) {
                                R.string.action_remove_like
                            } else {
                                R.string.action_like
                            },
                        ),
                    )
                    .setIconResId(if (currentSong.value?.song?.liked == true) R.drawable.ic_heart else R.drawable.ic_heart_outline)
                    .setSessionCommand(CommandToggleLike)
                    .setEnabled(currentSong.value != null)
                    .build(),
                CommandButton
                    .Builder()
                    .setDisplayName(
                        getString(
                            when (player.repeatMode) {
                                REPEAT_MODE_OFF -> R.string.repeat_mode_off
                                REPEAT_MODE_ONE -> R.string.repeat_mode_one
                                REPEAT_MODE_ALL -> R.string.repeat_mode_all
                                else -> throw IllegalStateException()
                            },
                        ),
                    ).setIconResId(
                        when (player.repeatMode) {
                            REPEAT_MODE_OFF -> R.drawable.repeat
                            REPEAT_MODE_ONE -> R.drawable.repeat_one_on
                            REPEAT_MODE_ALL -> R.drawable.repeat_on
                            else -> throw IllegalStateException()
                        },
                    ).setSessionCommand(CommandToggleRepeatMode)
                    .build(),
                CommandButton
                    .Builder()
                    .setDisplayName(getString(if (player.shuffleModeEnabled) R.string.action_shuffle_off else R.string.action_shuffle_on))
                    .setIconResId(if (player.shuffleModeEnabled) R.drawable.shuffle_on else R.drawable.shuffle)
                    .setSessionCommand(CommandToggleShuffle)
                    .build(),
                CommandButton.Builder()
                    .setDisplayName(getString(R.string.start_radio))
                    .setIconResId(R.drawable.radio)
                    .setSessionCommand(CommandToggleStartRadio)
                    .setEnabled(currentSong.value != null)
                    .build(),
            ),
        )
    }

    private suspend fun recoverSong(
        mediaId: String,
        playbackData: YTPlayerUtils.PlaybackData? = null
    ) {
        val song = database.song(mediaId).first()
        val mediaMetadata = withContext(Dispatchers.Main) {
            player.findNextMediaItemById(mediaId)?.metadata
        } ?: return
        val duration = song?.song?.duration?.takeIf { it != -1 }
            ?: mediaMetadata.duration.takeIf { it != -1 }
            ?: (playbackData?.videoDetails ?: YTPlayerUtils.playerResponseForMetadata(mediaId)
                .getOrNull()?.videoDetails)?.lengthSeconds?.toInt()
            ?: -1
        database.query {
            if (song == null) insert(mediaMetadata.copy(duration = duration))
            else {
                var updatedSong = song.song
                if (song.song.duration == -1) {
                    updatedSong = updatedSong.copy(duration = duration)
                }
                
                if (song.song.isVideo != mediaMetadata.isVideoSong) {
                    updatedSong = updatedSong.copy(isVideo = mediaMetadata.isVideoSong)
                }
                if (updatedSong != song.song) {
                    update(updatedSong)
                }
            }
        }
        if (!database.hasRelatedSongs(mediaId)) {
            val relatedEndpoint =
                YouTube.next(WatchEndpoint(videoId = mediaId)).getOrNull()?.relatedEndpoint
                    ?: return
            val relatedPage = YouTube.related(relatedEndpoint).getOrNull() ?: return
            database.query {
                relatedPage.songs
                    .map(SongItem::toMediaMetadata)
                    .onEach(::insert)
                    .map {
                        RelatedSongMap(
                            songId = mediaId,
                            relatedSongId = it.id
                        )
                    }
                    .forEach(::insert)
            }
        }
    }

    fun playQueue(
        queue: Queue,
        playWhenReady: Boolean = true,
    ) {
        _mixActive.value = false  // fresh user-chosen queue → Mix/Radio no longer active
        if (!scope.isActive) scope = CoroutineScope(Dispatchers.Main) + Job()

        
        if (!playerInitialized.value) {
            Timber.tag(TAG).w("playQueue called before player initialization, queuing request")
            scope.launch {
                playerInitialized.first { it }
                playQueue(queue, playWhenReady)
            }
            return
        }

        currentQueue = queue
        queueTitle = null
        scope.launch { runCatching { tasteProfile() } } // warm the taste cache for smart shuffle / autoplay
        val persistShuffleAcrossQueues = dataStore.get(PersistentShuffleAcrossQueuesKey, false)
        val previousShuffleEnabled = player.shuffleModeEnabled
        if (!persistShuffleAcrossQueues) {
            player.shuffleModeEnabled = false
        }
        
        originalQueueSize = 0
        if (queue.preloadItem != null) {
            player.setMediaItem(queue.preloadItem!!.toMediaItem())
            player.prepare()
            player.playWhenReady = playWhenReady
        }
        scope.launch(SilentHandler) {
            val initialStatus =
                withContext(Dispatchers.IO) {
                    queue.getInitialStatus()
                        .filterExplicit(dataStore.get(HideExplicitKey, false))
                        .filterVideoSongs(dataStore.get(HideVideoSongsKey, false))
                }
            if (queue.preloadItem != null && player.playbackState == STATE_IDLE) return@launch
            if (initialStatus.title != null) {
                queueTitle = initialStatus.title
            }
            if (initialStatus.items.isEmpty()) return@launch
            
            originalQueueSize = initialStatus.items.size
            if (queue.preloadItem != null) {
                val safeIndex = initialStatus.mediaItemIndex.coerceIn(0, (initialStatus.items.size - 1).coerceAtLeast(0))
                player.addMediaItems(
                    0,
                    initialStatus.items.subList(0, safeIndex)
                )
                player.addMediaItems(
                    initialStatus.items.subList(
                        (safeIndex + 1).coerceAtMost(initialStatus.items.size),
                        initialStatus.items.size
                    )
                )
            } else {
                val safeIndex = initialStatus.mediaItemIndex.coerceIn(0, (initialStatus.items.size - 1).coerceAtLeast(0))
                player.setMediaItems(
                    initialStatus.items,
                    safeIndex,
                    initialStatus.position,
                )
                player.prepare()
                // Use play() (not just playWhenReady=true) so playback actually starts on the first
                // try — for direct-URL media (podcasts) setting the flag alone sometimes left it
                // prepared-but-paused until a manual pause→play.
                if (playWhenReady) player.play() else player.playWhenReady = false
            }

            
            if (player.shuffleModeEnabled) {
                val shufflePlaylistFirst = dataStore.get(ShufflePlaylistFirstKey, false)
                applyShuffleOrder(player.currentMediaItemIndex, player.mediaItemCount, shufflePlaylistFirst)
            }
            
            preloadUpcomingItems()
        }
    }

    fun startRadioSeamlessly() {

        if (!playerInitialized.value) {
            Timber.tag(TAG).w("startRadioSeamlessly called before player initialization")
            resumeAfterSeed = false // never reach the finally on this early return; don't leave it armed
            return
        }

        val currentMediaMetadata = player.currentMetadata ?: run {
            resumeAfterSeed = false
            return
        }
        val currentMediaId = currentMediaMetadata.id
        radioSeedInFlight = true

        scope.launch(SilentHandler) {
            // Appends a batch after the current item, re-orders it by the user's taste, and — if we were waiting
            // at a TRUE end-of-queue (resumeAfterSeed armed) — advances into it + resumes. Returns true if it
            // actually appended anything. Wrapped by the callers so a failure simply falls through to the next
            // source. The !isPlaying resume guard (NOT == STATE_ENDED): addMediaItems can move the player out of
            // STATE_ENDED into READY-paused, which would make a STATE_ENDED check false and leave the music
            // stopped; !isPlaying still resumes then, yet won't yank playback if the user already started
            // something else during the async fetch.
            suspend fun appendSeed(items: List<MediaItem>): Boolean {
                if (items.isEmpty()) return false
                // Recompute the index from the LIVE player at append time (not a stale value captured before the
                // network fetch), so we never remove items relative to a position that has since moved.
                val liveIndex = player.currentMediaItemIndex
                val itemCount = player.mediaItemCount
                if (itemCount > liveIndex + 1) {
                    player.removeMediaItems(liveIndex + 1, itemCount)
                }
                player.addMediaItems(liveIndex + 1, items.orderedByTaste())
                _mixActive.value = true
                if (player.shuffleModeEnabled) {
                    val shufflePlaylistFirst = dataStore.get(ShufflePlaylistFirstKey, false)
                    applyShuffleOrder(player.currentMediaItemIndex, player.mediaItemCount, shufflePlaylistFirst)
                }
                if (resumeAfterSeed && !player.isPlaying) {
                    resumeAfterSeed = false
                    player.seekTo(liveIndex + 1, 0)
                    player.play()
                }
                // A successful append created/changed the "next item" — re-arm the crossfade so the infinite-queue
                // continuation transitions smoothly (especially when we seeded EARLY because this was the last
                // item with no next). scheduleCrossfade() is idempotent (cancel + reset).
                scheduleCrossfade()
                return true
            }

            // Source 1 — a proper radio queue seeded from the last song the user heard.
            suspend fun tryRadio(): Boolean = runCatching {
                val radioQueue = YouTubeQueue(endpoint = WatchEndpoint(videoId = currentMediaId))
                val initialStatus = withContext(Dispatchers.IO) {
                    radioQueue.getInitialStatus()
                        .filterExplicit(dataStore.get(HideExplicitKey, false))
                        .filterVideoSongs(dataStore.get(HideVideoSongsKey, false))
                }
                if (initialStatus.title != null) queueTitle = initialStatus.title
                val items = initialStatus.items.filter { it.mediaId != currentMediaId }
                val ok = appendSeed(items)
                if (ok) currentQueue = radioQueue
                ok
            }.getOrDefault(false)

            // Source 2 — "related" songs of the last song (a different YT endpoint; recovers when radio is empty).
            suspend fun tryRelated(): Boolean = runCatching {
                val nextResult = withContext(Dispatchers.IO) {
                    YouTube.next(WatchEndpoint(videoId = currentMediaId)).getOrNull()
                }
                val relatedEndpoint = nextResult?.relatedEndpoint ?: return@runCatching false
                val relatedPage = withContext(Dispatchers.IO) { YouTube.related(relatedEndpoint).getOrNull() }
                val items = relatedPage?.songs.orEmpty()
                    .filter { it.id != currentMediaId }
                    .map { it.toMediaItem() }
                    .filterExplicit(dataStore.get(HideExplicitKey, false))
                    .filterVideoSongs(dataStore.get(HideVideoSongsKey, false))
                val ok = appendSeed(items)
                // CRITICAL for endlessness: the related page is FINITE. Re-point currentQueue at a radio seeded
                // from the genuine last song AND PRIME it (getInitialStatus sets `continuation`, so hasNextPage()
                // is true and the onMediaItemTransition pagination keeps loading forever). hasNextPage() is false
                // on a fresh un-loaded YouTubeQueue, so without priming pagination wouldn't fire. Best-effort: if
                // priming fails, the always-on STATE_ENDED net still re-seeds when this finite batch ends.
                if (ok) {
                    val rq = YouTubeQueue(endpoint = WatchEndpoint(videoId = currentMediaId))
                    runCatching { withContext(Dispatchers.IO) { rq.getInitialStatus() } }
                    currentQueue = rq
                }
                ok
            }.getOrDefault(false)

            try {
                // Radio first, then related. If a transient hiccup left us empty, wait briefly and try once more —
                // so a momentary network blip at the exact end-of-queue moment never permanently stops the music.
                var appended = tryRadio() || tryRelated()
                if (!appended) {
                    kotlinx.coroutines.delay(2500)
                    appended = tryRadio() || tryRelated()
                }
                // Absolute last resort: at a TRUE end-of-queue, never leave the user in silence — replay the queue.
                if (!appended && resumeAfterSeed && !player.isPlaying && player.mediaItemCount > 0) {
                    Timber.tag(TAG).w("Radio seed yielded nothing; replaying current queue so playback never stops")
                    resumeAfterSeed = false
                    player.seekTo(0, 0)
                    player.play()
                }
            } finally {
                radioSeedInFlight = false
                resumeAfterSeed = false
            }
        }
    }

    private suspend fun tasteProfile(): iad1tya.echo.music.reco.TasteProfile? {
        val now = System.currentTimeMillis()
        cachedTaste?.let { if (now - cachedTasteAt < 5 * 60_000L) return it }
        return runCatching {
            val events = withContext(Dispatchers.IO) { database.recentEventsWithSong(3000).first() }
            val library = withContext(Dispatchers.IO) {
                runCatching {
                    database.librarySongsForTaste(iad1tya.echo.music.reco.AffinityEngine.MAX_LIBRARY).first()
                }.getOrDefault(emptyList())
            }
            val followed = withContext(Dispatchers.IO) {
                runCatching { database.artistsBookmarkedByCreateDateAsc().first().map { it.artist } }.getOrDefault(emptyList())
            }
            val disliked = runCatching { dislikeStore.snapshot() }
                .getOrDefault(iad1tya.echo.music.dislike.DislikeStore.Disliked())
            val genres = iad1tya.echo.music.reco.GenreCache.snapshot(this@MusicService)
            val onboarding = iad1tya.echo.music.reco.OnboardingGenres.itunesGenres(this@MusicService)
            withContext(Dispatchers.Default) {
                iad1tya.echo.music.reco.AffinityEngine.buildProfile(events, disliked, artistGenres = genres, onboardingGenres = onboarding, librarySongs = library, followedArtists = followed)
            }.also {
                cachedTaste = it
                cachedTasteAt = now
            }
        }.getOrNull()
    }

    /**
     * Order "what plays next" so it still FEELS like a continuation of the LAST song. The incoming list is
     * already in YouTube's relatedness order (most-related-to-the-seed first); we KEEP that as the backbone and
     * let taste only NUDGE a song up/down a few spots, instead of fully re-sorting by taste — which scrambled the
     * relatedness and made the radio feel unrelated to what was just playing. Disliked songs/artists are dropped.
     */
    private suspend fun List<MediaItem>.orderedByTaste(): List<MediaItem> {
        if (size < 2) return this
        val disliked = runCatching { dislikeStore.snapshot() }
            .getOrDefault(iad1tya.echo.music.dislike.DislikeStore.Disliked())
        val filtered = this.filter { mi ->
            val m = mi.metadata ?: return@filter true
            m.id !in disliked.songs && m.artists.none { it.id != null && it.id in disliked.artists }
        }
        val p = tasteProfile() // may be null (no taste yet) → pure relatedness order, still recency/dislike-filtered
        // Songs heard in the last ~60 transitions get pushed to the BOTTOM (soft demotion, not dropped) so the
        // radio stops replaying what just played, yet can still fall back to them rather than dead-ending.
        val recentSnapshot = synchronized(recentRadioIds) { HashSet(recentRadioIds) }
        val rnd = java.util.Random()
        // Precompute the sort key ONCE per item: calling rnd inside the comparator would make it inconsistent
        // between comparisons and crash TimSort ("Comparison method violates contract").
        return filtered
            .mapIndexed { index, mi ->
                val m = mi.metadata
                val taste = if (m == null || p == null) 0.0 else p.scoreNames(m.artists.map { it.name }, m.title)
                // Lower key = earlier. `index` (relatedness rank) DOMINATES; taste shifts a song by only a few
                // spots (~4 per taste point) and a small jitter adds variety — relatedness stays the backbone.
                // A big +1000 recency penalty sinks just-played songs beneath every fresh one without removing them.
                val jitter = if (p == null) 0.0 else rnd.nextDouble() * 1.5
                val recencyPenalty = if (m != null && m.id in recentSnapshot) 1000.0 else 0.0
                val key = index.toDouble() - taste * 4.0 + jitter + recencyPenalty
                mi to key
            }
            .sortedBy { it.second }
            .map { it.first }
    }

    fun getAutomixAlbum(albumId: String) {
        scope.launch(SilentHandler) {
            YouTube
                .album(albumId)
                .onSuccess {
                    getAutomix(it.album.playlistId)
                }
        }
    }

    fun getAutomix(playlistId: String) {
        if (dataStore.get(SimilarContent, true) &&
            !(dataStore.get(DisableLoadMoreWhenRepeatAllKey, false) && player.repeatMode == REPEAT_MODE_ALL)) {
            scope.launch(SilentHandler) {
                try {
                    
                    YouTube.next(WatchEndpoint(playlistId = playlistId))
                        .onSuccess { firstResult ->
                            YouTube.next(WatchEndpoint(playlistId = firstResult.endpoint.playlistId))
                                .onSuccess { secondResult ->
                                    automixItems.value = secondResult.items
                                        .map { it.toMediaItem() }
                                        .orderedByTaste()
                                }
                                .onFailure {
                                    
                                    if (firstResult.items.isNotEmpty()) {
                                        automixItems.value = firstResult.items
                                            .map { it.toMediaItem() }
                                            .orderedByTaste()
                                    }
                                }
                        }
                        .onFailure {
                            
                            val currentSong = player.currentMetadata
                            if (currentSong != null) {
                                
                                YouTube.next(WatchEndpoint(
                                    videoId = currentSong.id
                                )).onSuccess { radioResult ->
                                    val filteredItems = radioResult.items
                                        .filter { it.id != currentSong.id }
                                        .map { it.toMediaItem() }
                                    if (filteredItems.isNotEmpty()) {
                                        automixItems.value = filteredItems.orderedByTaste()
                                    }
                                }.onFailure {
                                    
                                    YouTube.next(WatchEndpoint(videoId = currentSong.id)).getOrNull()?.relatedEndpoint?.let { relatedEndpoint ->
                                        YouTube.related(relatedEndpoint).onSuccess { relatedPage ->
                                            val relatedItems = relatedPage.songs
                                                .filter { it.id != currentSong.id }
                                                .map { it.toMediaItem() }
                                            if (relatedItems.isNotEmpty()) {
                                                automixItems.value = relatedItems.orderedByTaste()
                                            }
                                        }
                                    }
                                }
                            }
                        }
                } catch (_: Exception) {
                    
                }
            }
        }
    }

    fun addToQueueAutomix(
        item: MediaItem,
        position: Int,
    ) {
        automixItems.value =
            automixItems.value.toMutableList().apply {
                removeAt(position)
            }
        addToQueue(listOf(item))
    }

    fun playNextAutomix(
        item: MediaItem,
        position: Int,
    ) {
        automixItems.value =
            automixItems.value.toMutableList().apply {
                removeAt(position)
            }
        playNext(listOf(item))
    }

    fun clearAutomix() {
        automixItems.value = emptyList()
    }

    fun playNext(items: List<MediaItem>) {
        
        if (player.mediaItemCount == 0 || player.playbackState == STATE_IDLE) {
            player.setMediaItems(items)
            player.prepare()
            
            if (castConnectionHandler?.isCasting?.value != true) {
                player.play()
            }
            return
        }

        
        if (dataStore.get(PreventDuplicateTracksInQueueKey, false)) {
            val itemIds = items.map { it.mediaId }.toSet()
            val indicesToRemove = mutableListOf<Int>()
            val currentIndex = player.currentMediaItemIndex

            for (i in 0 until player.mediaItemCount) {
                if (i != currentIndex && player.getMediaItemAt(i).mediaId in itemIds) {
                    indicesToRemove.add(i)
                }
            }

            
            indicesToRemove.sortedDescending().forEach { index ->
                player.removeMediaItem(index)
            }
        }

        val insertIndex = player.currentMediaItemIndex + 1
        val shuffleEnabled = player.shuffleModeEnabled

        
        player.addMediaItems(insertIndex, items)
        player.prepare()

        if (shuffleEnabled) {
            
            val timeline = player.currentTimeline
            if (!timeline.isEmpty) {
                val size = timeline.windowCount
                val currentIndex = player.currentMediaItemIndex

                
                val newIndices = (insertIndex until (insertIndex + items.size)).toSet()

                
                val orderAfter = mutableListOf<Int>()
                var idx = currentIndex
                while (true) {
                    idx = timeline.getNextWindowIndex(idx, Player.REPEAT_MODE_OFF, true)
                    if (idx == C.INDEX_UNSET) break
                    if (idx != currentIndex) orderAfter.add(idx)
                }

                val prevList = mutableListOf<Int>()
                var pIdx = currentIndex
                while (true) {
                    pIdx = timeline.getPreviousWindowIndex(pIdx, Player.REPEAT_MODE_OFF, true)
                    if (pIdx == C.INDEX_UNSET) break
                    if (pIdx != currentIndex) prevList.add(pIdx)
                }
                prevList.reverse() 

                val existingOrder = (prevList + orderAfter).filter { it != currentIndex && it !in newIndices }

                
                val nextBlock = (insertIndex until (insertIndex + items.size)).toList()
                val finalOrder = IntArray(size)
                var pos = 0
                finalOrder[pos++] = currentIndex
                nextBlock.forEach { if (it in 0 until size) finalOrder[pos++] = it }
                existingOrder.forEach { if (pos < size) finalOrder[pos++] = it }

                
                if (pos < size) {
                    for (i in 0 until size) {
                        if (!finalOrder.contains(i)) {
                            finalOrder[pos++] = i
                            if (pos == size) break
                        }
                    }
                }

                player.setShuffleOrder(DefaultShuffleOrder(finalOrder, System.currentTimeMillis()))
            }
        }
        
        preloadUpcomingItems()
    }

    fun addToQueue(items: List<MediaItem>) {
        
        if (dataStore.get(PreventDuplicateTracksInQueueKey, false)) {
            val itemIds = items.map { it.mediaId }.toSet()
            val indicesToRemove = mutableListOf<Int>()
            val currentIndex = player.currentMediaItemIndex

            for (i in 0 until player.mediaItemCount) {
                if (i != currentIndex && player.getMediaItemAt(i).mediaId in itemIds) {
                    indicesToRemove.add(i)
                }
            }

            
            indicesToRemove.sortedDescending().forEach { index ->
                player.removeMediaItem(index)
            }
        }

        player.addMediaItems(items)
        if (player.shuffleModeEnabled) {
            val shufflePlaylistFirst = dataStore.get(ShufflePlaylistFirstKey, false)
            applyShuffleOrder(player.currentMediaItemIndex, player.mediaItemCount, shufflePlaylistFirst)
        }
        player.prepare()
        
        preloadUpcomingItems()
    }

    fun toggleLibrary() {
        scope.launch {
            val songToToggle = currentSong.first()
            songToToggle?.let {
                val isInLibrary = it.song.inLibrary != null
                val token = if (isInLibrary) it.song.libraryRemoveToken else it.song.libraryAddToken

                
                token?.let { feedbackToken ->
                    YouTube.feedback(listOf(feedbackToken))
                }

                
                database.query {
                    update(it.song.toggleLibrary())
                }
                currentMediaMetadata.value = player.currentMetadata
            }
        }
    }

    fun toggleLike() {
        scope.launch {
            val meta = player.currentMetadata ?: return@launch
            // Insert (if needed) + read-back + toggle + update MUST happen inside ONE database.query
            // task. database.query runs its block asynchronously on the query executor, so the old code
            // used two separate query{} calls and raced: it read the song back BEFORE the insert had
            // committed, got null and silently bailed (return@launch) — which is why the heart did
            // "absolutely nothing" on online tracks not yet saved to the library. One task = the insert
            // is guaranteed committed before we read it, so the like ALWAYS registers.
            database.query {
                var base = getSongByIdBlocking(meta.id)?.song
                if (base == null) {
                    insert(meta)
                    base = getSongByIdBlocking(meta.id)?.song
                }
                val toggled = base?.toggleLike() ?: return@query
                upsert(toggled) // insert-or-update so the like always persists
                syncUtils.likeSong(toggled)

                if (dataStore.get(AutoDownloadOnLikeKey, true) && toggled.liked) {
                    // Guard the auto-download: DownloadService.sendAddDownload(foreground=false) throws
                    // IllegalStateException on Android 8+ when started from the background, and an uncaught throw
                    // here would abort the whole query block — rolling back the like. Never let the optional
                    // download break the like itself.
                    try {
                        val downloadRequest =
                            androidx.media3.exoplayer.offline.DownloadRequest
                                .Builder(toggled.id, toggled.id.toUri())
                                .setCustomCacheKey(toggled.id)
                                .setData(toggled.title.toByteArray())
                                .build()
                        androidx.media3.exoplayer.offline.DownloadService.sendAddDownload(
                            this@MusicService,
                            ExoDownloadService::class.java,
                            downloadRequest,
                            false
                        )
                    } catch (e: Exception) {
                        Timber.tag(TAG).e(e, "auto-download on like failed (non-fatal)")
                    }
                }
            }
            currentMediaMetadata.value = player.currentMetadata
        }
    }

    fun toggleStartRadio() {
        startRadioSeamlessly()
    }

    /**
     * "No me gusta" for the current song: remembers the dislike (so it's filtered out of every
     * recommendation surface and never auto-plays again), removes it from the rest of this queue, and
     * skips to the next track. Also unlikes it if it was liked.
     */
    fun dislikeCurrentSong() {
        val mediaId = player.currentMediaItem?.mediaId ?: return
        scope.launch {
            runCatching { dislikeStore.dislikeSong(mediaId) }
            // If it was liked, drop the like (a dislike contradicts it).
            runCatching {
                val song = currentSong.first()?.song
                if (song != null && song.liked) {
                    val unliked = song.toggleLike()
                    database.query { upsert(unliked); syncUtils.likeSong(unliked) }
                }
            }
            // Purge any other copies of this track still queued ahead, then advance.
            withContext(Dispatchers.Main) {
                for (i in player.mediaItemCount - 1 downTo 0) {
                    if (i != player.currentMediaItemIndex &&
                        player.getMediaItemAt(i).mediaId == mediaId
                    ) {
                        player.removeMediaItem(i)
                    }
                }
                if (player.hasNextMediaItem()) {
                    player.seekToNext()
                }
            }
        }
    }

    private fun setupLoudnessEnhancer() {
        val audioSessionId = player.audioSessionId

        if (audioSessionId == C.AUDIO_SESSION_ID_UNSET || audioSessionId <= 0) {
            Timber.tag(TAG).w("setupLoudnessEnhancer: invalid audioSessionId ($audioSessionId), cannot create effect yet")
            return
        }

        
        if (loudnessEnhancer == null) {
            try {
                loudnessEnhancer = LoudnessEnhancer(audioSessionId)
                Timber.tag(TAG).d("LoudnessEnhancer created for sessionId=$audioSessionId")
            } catch (e: Exception) {
                reportException(e)
                loudnessEnhancer = null
                return
            }
        }

        scope.launch {
            try {
                val (currentMediaId, positionMs) = withContext(Dispatchers.Main) {
                    Pair(player.currentMediaItem?.mediaId, player.currentPosition)
                }

                val normalizeAudio = withContext(Dispatchers.IO) {
                    dataStore.data.map { it[AudioNormalizationKey] ?: true }.first()
                }
                // Safe Volume (opt-in, default off): drives the live EQ processor's attenuate-only gain.
                val safeVol = withContext(Dispatchers.IO) {
                    dataStore.data.map { it[SafeVolumeEnabledKey] ?: false }.first()
                }

                if ((normalizeAudio || safeVol) && currentMediaId != null) {
                    val format = withContext(Dispatchers.IO) {
                        database.format(currentMediaId).first()
                    }

                    Timber.tag(TAG).d("Audio normalization enabled: $normalizeAudio")
                    Timber.tag(TAG).d("Format loudnessDb: ${format?.loudnessDb}, perceptualLoudnessDb: ${format?.perceptualLoudnessDb}, measuredLoudnessDb: ${format?.measuredLoudnessDb}")


                    // Normalize EVERY track to the same reference so none plays louder than another. Use
                    // the real loudness when present, else a once-measured loudness (cached from a prior play),
                    // else a conservative default (so non-YouTube tracks without metadata don't blast at their
                    // raw level until measurement completes).
                    val hasRealLoudness = format?.loudnessDb != null || format?.perceptualLoudnessDb != null
                    // "Known" = we have a usable loudness right now (metadata OR a cached measurement) → apply
                    // instantly, no measurement. Only a TRULY unknown track (no metadata, never measured) is
                    // measured live.
                    val hasKnownLoudness = hasRealLoudness || format?.measuredLoudnessDb != null
                    val loudnessDb = effectiveLoudnessDb(
                        format?.loudnessDb, format?.perceptualLoudnessDb, format?.measuredLoudnessDb,
                    )
                    // Mirror into the in-memory hint cache so a future crossfade can pre-level this track without
                    // a blocking disk read on the main thread (Fix B).
                    if (hasKnownLoudness) currentMediaId?.let { loudnessHintCache[it] = loudnessDb }

                    // Apply the real-loudness upgrade ONLY near the START of the track (~first 8 s, where the
                    // playback fetch returns it). After that, NEVER re-level the currently-playing track: liking
                    // triggers an auto-download whose fetch can bring loudness for a track that started WITHOUT
                    // it, and applying that mid-song is exactly what made the volume rise on like / fall on unlike.
                    // DownloadUtil now also preserves existing loudness so a download can't CHANGE a
                    // known-loudness track's row; this start-window is the safety net for tracks that genuinely
                    // started with no loudness. combine() also de-dups on the loudness fields.
                    // The applied gain/makeup for THIS track is IMMUTABLE once set. The ONLY allowed change is
                    // the single legitimate early upgrade when YouTube's real loudness first arrives for a track
                    // that started without it (within ~8 s of start) AND it actually changes the value. Every
                    // other re-trigger of this function for the same playing track — a like/auto-download, the
                    // next-track prefetch firing near the end, a session re-open on screen-off — RE-ASSERTS the
                    // already-applied value and never moves the volume.
                    val targetGain = normalizationMultiplier(loudnessDb, enabled = true)
                    val targetMakeup = dbToLinear(loudnessMakeupDb(loudnessDb, enabled = true))
                    val realLoudnessJustArrived =
                        hasRealLoudness && !lastNormalizedHadLoudness && positionMs < 8_000L
                    val valueChanges = kotlin.math.abs(targetGain - lastAppliedGain) > 1e-3f ||
                        kotlin.math.abs(targetMakeup - lastAppliedMakeup) > 1e-3f
                    if (currentMediaId == lastNormalizedId && !(realLoudnessJustArrived && valueChanges)) {
                        withContext(Dispatchers.Main) {
                            NormalizationGainAudioProcessor.gain = lastAppliedGain
                            TruePeakLimiterAudioProcessor.loudnessMakeup = lastAppliedMakeup
                            loudnessEnhancer?.enabled = false
                            // Safe Volume (opt-in): re-assert the attenuate-only gain on the live EQ processor.
                            playerEqProcessors[player]?.applySafeVolume(safeVol, if (safeVol) lastAppliedGain else 1f)
                        }
                        return@launch
                    }

                    withContext(Dispatchers.Main) {
                        // Two-stage loudness normalization to a reference (TIDAL-style):
                        //  • attenuate loud masters (≤ 0 dB) here, in 16-bit, clip-free;
                        //  • boost quiet tracks UP (makeup, ≥ 0 dB) in float inside the true-peak limiter,
                        //    which catches the resulting peaks → loud + full, no clip.
                        lastAppliedGain = targetGain
                        lastAppliedMakeup = targetMakeup
                        NormalizationGainAudioProcessor.gain = targetGain
                        TruePeakLimiterAudioProcessor.loudnessMakeup = targetMakeup
                        loudnessEnhancer?.enabled = false
                        // Safe Volume (opt-in): apply the attenuate-only normalization gain to the live EQ
                        // processor (the only real DSP). Off → unity, keeping bit-perfect playback.
                        playerEqProcessors[player]?.applySafeVolume(safeVol, if (safeVol) targetGain else 1f)
                        lastNormalizedId = currentMediaId
                        lastNormalizedHadLoudness = hasRealLoudness

                        // Per-track LIVE measurement (only for TRULY unknown tracks). If we already have a
                        // usable loudness (metadata or a cached measurement), DISARM measurement and apply
                        // instantly. Otherwise ARM measurement on the current player's processor — it integrates
                        // the next ~12 s and publishes measuredLoudnessDb; the periodic check applies it ONCE.
                        val norm = playerNormProcessors[player]
                        if (hasKnownLoudness) {
                            norm?.measureThisTrack = false
                            // Already known/applied → mark so the one-shot re-level never fires for this track.
                            measuredAppliedForId = currentMediaId
                        } else {
                            // Provisional DEFAULT (≈7 dB) is already baked into loudnessDb above; arm a fresh
                            // measurement (zeroes accumulators + commit flag, keyed on this track id).
                            norm?.startMeasurement(currentMediaId)
                            // Allow the one-shot re-level to fire for this (newly measured) track.
                            if (measuredAppliedForId == currentMediaId) measuredAppliedForId = null
                        }
                        Timber.tag(TAG).i("Normalization set (loudnessDb=$loudnessDb, real=$hasRealLoudness, known=$hasKnownLoudness, makeup=${TruePeakLimiterAudioProcessor.loudnessMakeup})")
                    }
                } else {
                    lastAppliedGain = 1.0f
                    lastAppliedMakeup = 1.0f
                    NormalizationGainAudioProcessor.gain = 1.0f
                    TruePeakLimiterAudioProcessor.loudnessMakeup = 1.0f
                    withContext(Dispatchers.Main) {
                        // Clear any per-player override a crossfade pinned, so "off" is truly unity/transparent.
                        playerNormProcessors[player]?.instanceGain = null
                        playerNormProcessors[player]?.measureThisTrack = false  // don't integrate while off
                        playerLimiterProcessors[player]?.setInstanceMakeup(null, null)
                        loudnessEnhancer?.enabled = false
                        // Safe Volume off (both normalization and safe-volume off) → unity, bit-perfect.
                        playerEqProcessors[player]?.applySafeVolume(false, 1f)
                        Timber.tag(TAG).d("setupLoudnessEnhancer: normalization disabled - unity gain")
                    }
                    // Reset so RE-ENABLING normalization for the SAME track re-applies. The guard above keys on
                    // lastNormalizedId; without this reset, toggling normalization off→on mid-song was a no-op.
                    lastNormalizedId = null
                    lastNormalizedHadLoudness = false
                }
            } catch (e: Exception) {
                reportException(e)
                releaseLoudnessEnhancer()
            }
        }
    }

    /**
     * CACHE-ONLY ReplayGain-style measurement for a track that started WITHOUT loudness metadata.
     * The NormalizationGainAudioProcessor measures the first ~12 s passively; when it commits a measured
     * loudness, we CACHE it to the DB (preserving any metadata loudness) so the NEXT play is leveled from the
     * first second. We deliberately do NOT re-level the currently-playing track mid-song — the user dislikes
     * any mid-song volume change. Fires at most ONCE per track (measuredAppliedForId guard). The next play
     * reads the cached value via effectiveLoudnessDb and applies it at start, like a metadata track.
     */
    private suspend fun maybeApplyMeasuredLoudness() {
        // Gather player-thread state atomically.
        data class Snap(
            val mediaId: String?,
            val committed: Boolean,
            val measureId: String?,
            val measured: Double?,
            val overridePinned: Boolean,
        )
        val snap = withContext(Dispatchers.Main) {
            val norm = playerNormProcessors[player]
            Snap(
                mediaId = player.currentMediaItem?.mediaId,
                committed = norm?.measurementCommitted == true,
                measureId = norm?.measureTrackId,
                measured = norm?.measuredLoudnessDb,
                // A crossfade pins a per-instance gain; don't write the shared statics under it.
                overridePinned = isCrossfading || norm?.instanceGain != null,
            )
        }
        val mediaId = snap.mediaId ?: return
        if (!snap.committed) return
        if (snap.measureId != mediaId) return            // committed value belongs to a different track
        if (measuredAppliedForId == mediaId) return        // already re-leveled this track once
        if (snap.overridePinned) return                    // defer; a crossfade owns the gain right now
        val measured = snap.measured ?: return

        withContext(Dispatchers.Main) {
            val norm = playerNormProcessors[player]
            if (player.currentMediaItem?.mediaId != mediaId) return@withContext
            if (norm?.measurementCommitted != true || norm.measureTrackId != mediaId) return@withContext
            if (measuredAppliedForId == mediaId) return@withContext
            
            val targetGain = normalizationMultiplier(measured, enabled = true)
            val targetMakeup = dbToLinear(loudnessMakeupDb(measured, enabled = true))
            
            lastAppliedGain = targetGain
            lastAppliedMakeup = targetMakeup
            NormalizationGainAudioProcessor.gain = targetGain
            TruePeakLimiterAudioProcessor.loudnessMakeup = targetMakeup
            
            norm.measureThisTrack = false
            measuredAppliedForId = mediaId
            Timber.tag(TAG).i("Measured loudness applied for $mediaId: ${measured}dB")
        }

        // Cache the measured value, PRESERVING any metadata loudness (mirror the format-store preserve
        // pattern): only set measuredLoudnessDb, keep loudnessDb/perceptualLoudnessDb and the rest intact.
        runCatching {
            val existing = withContext(Dispatchers.IO) { database.format(mediaId).first() }
            if (existing != null) {
                database.query {
                    upsert(existing.copy(measuredLoudnessDb = measured))
                }
            } else {
                // For local files or files without metadata, cache the measurement in a dummy format row.
                database.query {
                    upsert(iad1tya.echo.music.db.entities.FormatEntity(
                        id = mediaId,
                        itag = 0,
                        mimeType = "audio/local",
                        codecs = "",
                        bitrate = 0,
                        sampleRate = null,
                        contentLength = 0L,
                        loudnessDb = null,
                        measuredLoudnessDb = measured,
                        playbackUrl = null
                    ))
                }
            }
        }
    }

    private fun releaseLoudnessEnhancer() {
        try {
            loudnessEnhancer?.release()
            Timber.tag(TAG).d("LoudnessEnhancer released")
        } catch (e: Exception) {
            reportException(e)
            Timber.tag(TAG).e(e, "Error releasing LoudnessEnhancer: ${e.message}")
        } finally {
            loudnessEnhancer = null
        }
    }

    private fun openAudioEffectSession() {
        if (isAudioEffectSessionOpened) return
        isAudioEffectSessionOpened = true
        setupLoudnessEnhancer()
        sendBroadcast(
            Intent(AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION).apply {
                putExtra(AudioEffect.EXTRA_AUDIO_SESSION, player.audioSessionId)
                putExtra(AudioEffect.EXTRA_PACKAGE_NAME, packageName)
                putExtra(AudioEffect.EXTRA_CONTENT_TYPE, AudioEffect.CONTENT_TYPE_MUSIC)
            },
        )
    }

    private fun closeAudioEffectSession() {
        if (!isAudioEffectSessionOpened) return
        isAudioEffectSessionOpened = false
        releaseLoudnessEnhancer()
        sendBroadcast(
            Intent(AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION).apply {
                putExtra(AudioEffect.EXTRA_AUDIO_SESSION, player.audioSessionId)
                putExtra(AudioEffect.EXTRA_PACKAGE_NAME, packageName)
            },
        )
    }

    private var previousMediaItemIndex = C.INDEX_UNSET

    override fun onMediaItemTransition(
        mediaItem: MediaItem?,
        reason: Int,
    ) {
        currentPlayingMediaId = mediaItem?.mediaId
        rememberRecentRadioId(mediaItem?.mediaId ?: player.currentMetadata?.id)
        // Sticky video mode: when the track changes while video mode is on, swap the NEW current track to
        // its video (restoring the previous one to audio). Stays in video until the user turns it off.
        if (_videoMode.value && mediaItem != null && mediaItem.mediaId != videoModeMediaId) {
            applyVideoToCurrent()
        }
        // Prefetch the NEXT track's video URL only while ACTIVELY in video mode (instant auto-advance).
        // We deliberately do NOT prefetch during normal audio playback: doing it on every track change
        // hammered YouTube with extra stream-resolution requests and got the app rate-limited, which then
        // stalled normal audio after a few songs (it resumed once the limit cooled). Toggling video resolves
        // on demand instead (a brief spinner), which is fine.
        if (_videoMode.value) {
            val nextIdx = player.nextMediaItemIndex
            if (nextIdx != C.INDEX_UNSET) {
                prefetchVideoUrl(runCatching { player.getMediaItemAt(nextIdx).mediaId }.getOrNull())
            }
        }

        // B5: remember what we've played this shuffle session (consumed by applyShuffleOrder to avoid repeats).
        if (player.shuffleModeEnabled) {
            (mediaItem?.mediaId ?: player.currentMetadata?.id)?.let { shufflePlayedIds.add(it) }
        }

        if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) {
            val repeatMode = player.repeatMode  // live value; avoids a blocking disk read on the player thread
            if (repeatMode == REPEAT_MODE_ONE &&
                previousMediaItemIndex != C.INDEX_UNSET &&
                previousMediaItemIndex != player.currentMediaItemIndex) {

                player.seekTo(previousMediaItemIndex, 0)
            }
        }
        previousMediaItemIndex = player.currentMediaItemIndex

        lastPlaybackSpeed = -1.0f 

        preloadUpcomingItems()
        setupLoudnessEnhancer()

        discordUpdateJob?.cancel()

        scrobbleManager?.onSongStop()
        if (player.playWhenReady && player.playbackState == Player.STATE_READY) {
            scrobbleManager?.onSongStart(player.currentMetadata, duration = player.duration)
        }

        
        
        if (castConnectionHandler?.isCasting?.value == true &&
            castConnectionHandler?.isSyncingFromCast != true &&
            mediaItem != null) {
            val metadata = mediaItem.metadata
            if (metadata != null) {
                
                
                val navigated = castConnectionHandler?.navigateToMediaIfInQueue(metadata.id) ?: false
                if (!navigated) {
                    
                    castConnectionHandler?.loadMedia(metadata)
                }
            }
        }

        
        if (dataStore.get(AutoLoadMoreKey, true) &&
            reason != Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT &&
            player.mediaItemCount - player.currentMediaItemIndex <= 5 &&
            currentQueue.hasNextPage() &&
            !(dataStore.get(DisableLoadMoreWhenRepeatAllKey, false) && player.repeatMode == REPEAT_MODE_ALL)
        ) {
            // Captured on the player thread: the lane of what's currently playing, so autoplay can
            // stay in the same style instead of drifting (e.g. Christian -> secular).
            val curItem = player.currentMediaItem
            val currentLaneText = listOfNotNull(
                curItem?.mediaMetadata?.title, curItem?.mediaMetadata?.artist, curItem?.mediaMetadata?.albumTitle,
            ).joinToString(" ")
            val keepLane = dataStore.get(KeepGenreLaneKey, true)
            scope.launch(SilentHandler) {
                val disliked = runCatching { dislikeStore.snapshot() }.getOrDefault(iad1tya.echo.music.dislike.DislikeStore.Disliked())
                val currentLane = if (keepLane) iad1tya.echo.music.reco.GenreLane.laneOf(currentLaneText) else null
                val mediaItems = withContext(Dispatchers.IO) {
                    var next = currentQueue.nextPage()
                        .filterExplicit(dataStore.get(HideExplicitKey, false))
                        .filterVideoSongs(dataStore.get(HideVideoSongsKey, false))
                    // Never auto-play something the user disliked (the song or a disliked artist).
                    if (!disliked.isEmpty) {
                        next = next.filterNot { mi ->
                            mi.mediaId in disliked.songs ||
                                (mi.metadata?.artists?.any { it.id != null && it.id in disliked.artists } == true)
                        }
                    }
                    // Keep the lane: if what's playing is clearly in a lane, prefer same-lane songs —
                    // but only enforce it when there are enough, so playback never dead-ends.
                    if (currentLane != null) {
                        val inLane = next.filter { mi ->
                            iad1tya.echo.music.reco.GenreLane.laneOf(
                                mi.mediaMetadata.title?.toString(),
                                mi.mediaMetadata.artist?.toString(),
                                mi.mediaMetadata.albumTitle?.toString(),
                            ) == currentLane
                        }
                        if (inLane.size >= 2) next = inLane
                    }
                    next
                }
                if (player.playbackState != STATE_IDLE && mediaItems.isNotEmpty()) {
                    player.addMediaItems(mediaItems)
                    if (player.shuffleModeEnabled) {
                        val shufflePlaylistFirst = dataStore.get(ShufflePlaylistFirstKey, false)
                        applyShuffleOrder(player.currentMediaItemIndex, player.mediaItemCount, shufflePlaylistFirst)
                    }
                }
            }
        }

        // B3 — keep the music going when a FINITE queue ends. Album / artist-top / new-release radar / imported
        // list / single-song queues have no next page, so when we reach the LAST item we seed a radio from that
        // song (its YouTube relations, taste-ordered if any history exists — so it works even on a fresh empty
        // install). Gated to genuine auto-advance or first-play (not a manual SEEK around the queue), only while
        // actually playing, only at the very last item (so the list is never truncated), and never twice for the
        // same end (radioSeedInFlight). Skipped while the first block already handles continuation (next page).
        if (dataStore.get(AutoLoadMoreKey, true) &&
            (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO ||
                reason == Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED) &&
            player.playWhenReady &&
            !radioSeedInFlight &&
            !currentQueue.hasNextPage() &&
            player.mediaItemCount > 0 &&
            !player.hasNextMediaItem() &&  // shuffle/repeat-aware "on the last item to PLAY" (not a raw timeline index)
            !(dataStore.get(DisableLoadMoreWhenRepeatAllKey, false) && player.repeatMode == REPEAT_MODE_ALL)
        ) {
            startRadioSeamlessly()
        }


        if (dataStore.get(PersistentQueueKey, true)) {
            saveQueueToDisk()
        }
    }

    override fun onIsPlayingChanged(isPlaying: Boolean) {
        if (isPlaying) {
            startPeriodicPersist()
        } else {
            // Stop the periodic wake-ups while paused/idle and save the position once so nothing is lost.
            periodicPersistJob?.cancel()
            periodicPersistJob = null
            if (dataStore.get(PersistentQueueKey, true)) {
                scope.launch { runCatching { savePlaybackPositionToDisk() } }
            }
        }
    }

    /**
     * Periodic podcast-progress + playback-position persistence, but ONLY while actually playing.
     * Replaces two always-on while(true) loops that woke the CPU every 5s/8s even when paused/idle.
     */
    private fun startPeriodicPersist() {
        if (periodicPersistJob?.isActive == true) return
        periodicPersistJob = scope.launch {
            var tick = 0
            while (true) {
                kotlinx.coroutines.delay(5000)
                tick++
                // ONE-SHOT measurement-driven re-level: if the current track had no loudness and we've now
                // integrated enough to commit a measured value, apply it ONCE (slow, inaudible ramp) + cache it.
                runCatching { maybeApplyMeasuredLoudness() }
                val podcast = withContext(Dispatchers.Main) {
                    if (!player.isPlaying) return@withContext null
                    val id = player.currentMediaItem?.mediaId
                    if (id != null && id.startsWith("http", ignoreCase = true) && player.duration > 0)
                        Triple(id, player.currentPosition, player.duration) else null
                }
                podcast?.let { (id, pos, dur) -> runCatching { podcastProgressStore.save(id, pos, dur) } }
                // Persist queue position every other tick (~10s) so a mid-song kill resumes in place.
                if (tick % 2 == 0 && dataStore.get(PersistentQueueKey, true)) {
                    val ok = withContext(Dispatchers.Main) { player.isPlaying && player.mediaItemCount > 0 }
                    if (ok) runCatching { savePlaybackPositionToDisk() }
                }
            }
        }
    }

    override fun onPlaybackStateChanged(
        @Player.State playbackState: Int,
    ) {
        
        if (playbackState == Player.STATE_ENDED) {
            val repeatMode = player.repeatMode  // live value; avoids a blocking disk read on the player thread
            if (repeatMode == REPEAT_MODE_ALL && player.mediaItemCount > 0) {
                player.seekTo(0, 0)
                player.prepare()
                player.play()
            } else if (
                player.mediaItemCount > 0 &&
                !player.hasNextMediaItem()
            ) {
                // Predictive infinite playback — the AUTHORITATIVE net. onMediaItemTransition (B3) pre-seeds a
                // radio as a head-start, but it does NOT fire when the LAST track actually FINISHES (the player
                // goes straight to STATE_ENDED with no transition), and it can miss (paused / manual seek to the
                // last track / empty seed). We trust the PLAYER via !hasNextMediaItem() — which is shuffle- AND
                // repeat-aware (true only when there's genuinely nothing more to play), unlike a raw timeline
                // index which is WRONG under shuffle — NOT currentQueue.hasNextPage() which can lie/go stale and
                // dead-end the music. We ALWAYS arm resumeAfterSeed here (even if a B3 seed
                // is already in flight): that way an in-flight head-start seed that lands AFTER the hard stop will
                // resume instead of leaving the player stopped. Only kick a NEW seed when one isn't already
                // running. ALWAYS on — never gated by the AutoLoadMore toggle — so the music never just stops at
                // the end of any queue (the user's explicit requirement).
                resumeAfterSeed = true
                if (!radioSeedInFlight) startRadioSeamlessly()
            }
        }

        
        if (dataStore.get(PersistentQueueKey, true) && !isSilenceSkipping) {
            saveQueueToDisk()
        }

        if (playbackState == Player.STATE_READY) {
            // First real playback -> safe to bring up Cast now (service is foregrounded). No-op after once.
            initializeCast()
            consecutivePlaybackErr = 0
            retryCount = 0
            waitingForNetworkConnection.value = false
            retryJob?.cancel()

            
            player.currentMediaItem?.mediaId?.let { mediaId ->
                resetRetryCount(mediaId)
                Timber.tag(TAG).d("Playback successful for $mediaId, reset retry count")
            }
            scheduleCrossfade()
        }

        if (playbackState == Player.STATE_IDLE || playbackState == Player.STATE_ENDED) {
            scrobbleManager?.onSongStop()
        }
    }

    override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
        
        if (playWhenReady && castConnectionHandler?.isCasting?.value == true) {
            player.pause()
            return
        }

        if (reason == Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST) {
            if (playWhenReady) {
                isPausedByVolumeMute = false
            }

            if (!playWhenReady && !isPausedByVolumeMute) {
                wasPlayingBeforeVolumeMute = false
            }
        }

        if (playWhenReady) {
            setupLoudnessEnhancer()
        }
    }

    override fun onEvents(
        player: Player,
        events: Player.Events,
    ) {
        if (events.containsAny(
                Player.EVENT_PLAYBACK_STATE_CHANGED,
                Player.EVENT_PLAY_WHEN_READY_CHANGED
            )
        ) {
            scheduleCrossfade()
            val isBufferingOrReady =
                player.playbackState == Player.STATE_BUFFERING || player.playbackState == Player.STATE_READY
            if (isBufferingOrReady && player.playWhenReady) {
                val focusGranted = requestAudioFocus()
                if (focusGranted) {
                    openAudioEffectSession()
                }
            } else {
                closeAudioEffectSession()
            }
        }
        if (events.containsAny(EVENT_TIMELINE_CHANGED, EVENT_POSITION_DISCONTINUITY)) {
            currentMediaMetadata.value = player.currentMetadata
            // Re-arm the crossfade trigger here too. For streamed songs (YouTube) the duration is
            // frequently still C.TIME_UNSET at STATE_READY, so the first scheduleCrossfade() bailed and
            // never retried — which is why crossfade did NOTHING on any song. A timeline change is when
            // the real duration arrives; a position discontinuity (auto-advance / seek) changes how long
            // until the trigger. Both must re-schedule. scheduleCrossfade() is idempotent (cancel + reset).
            scheduleCrossfade()
        }

        
        if (events.containsAny(Player.EVENT_IS_PLAYING_CHANGED)) {
            updateWidgetUI(player.isPlaying)
            if (player.isPlaying) {
                startWidgetUpdates()
            } else {
                stopWidgetUpdates()
            }
            if (!player.isPlaying && !events.containsAny(Player.EVENT_POSITION_DISCONTINUITY, Player.EVENT_MEDIA_ITEM_TRANSITION)) {
                scope.launch {
                    discordRpc?.close()
                }
            }
        }

        
        if (events.containsAny(Player.EVENT_MEDIA_ITEM_TRANSITION, Player.EVENT_IS_PLAYING_CHANGED) && player.isPlaying) {
            val mediaId = player.currentMetadata?.id
            if (mediaId != null) {
                scope.launch {
                    
                    database.song(mediaId).first()?.let { song ->
                        updateDiscordRPC(song)
                    }
                }
            }
        }

        
        if (events.containsAny(Player.EVENT_IS_PLAYING_CHANGED)) {
            scrobbleManager?.onPlayerStateChanged(player.isPlaying, player.currentMetadata, duration = player.duration)
        }

    }

    override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
        updateNotification()
        if (shuffleModeEnabled) {

            if (player.mediaItemCount == 0) return

            // B5: start a fresh anti-repeat session each time shuffle is enabled; the current song counts as played.
            shufflePlayedIds.clear()
            player.currentMetadata?.id?.let { shufflePlayedIds.add(it) }

            val shufflePlaylistFirst = dataStore.get(ShufflePlaylistFirstKey, false)
            val currentIndex = player.currentMediaItemIndex
            val totalCount = player.mediaItemCount

            applyShuffleOrder(currentIndex, totalCount, shufflePlaylistFirst)
        }

        
        if (dataStore.get(RememberShuffleAndRepeatKey, true)) {
            scope.launch {
                dataStore.edit { settings ->
                    settings[ShuffleModeKey] = shuffleModeEnabled
                }
            }
        }

        
        if (dataStore.get(PersistentQueueKey, true)) {
            saveQueueToDisk()
        }
    }

    override fun onRepeatModeChanged(repeatMode: Int) {
        updateNotification()
        scope.launch {
            dataStore.edit { settings ->
                settings[RepeatModeKey] = repeatMode
            }
        }

        
        if (dataStore.get(PersistentQueueKey, true)) {
            saveQueueToDisk()
        }
    }

    
    private fun applyShuffleOrder(
        currentIndex: Int,
        totalCount: Int,
        shufflePlaylistFirst: Boolean
    ) {
        if (totalCount == 0) return

        if (shufflePlaylistFirst && originalQueueSize > 0 && originalQueueSize < totalCount) {
            
            val originalIndices = (0 until originalQueueSize).filter { it != currentIndex }.toMutableList()
            val addedIndices = (originalQueueSize until totalCount).filter { it != currentIndex }.toMutableList()

            originalIndices.shuffle()
            addedIndices.shuffle()

            val shuffledIndices = IntArray(totalCount)
            var pos = 0
            shuffledIndices[pos++] = currentIndex

            if (currentIndex < originalQueueSize) {
                originalIndices.forEach { shuffledIndices[pos++] = it }
                addedIndices.forEach { shuffledIndices[pos++] = it }
            } else {
                (0 until originalQueueSize).shuffled().forEach { shuffledIndices[pos++] = it }
                addedIndices.forEach { shuffledIndices[pos++] = it }
            }
            player.setShuffleOrder(DefaultShuffleOrder(shuffledIndices, System.currentTimeMillis()))
        } else {
            val indices = (0 until totalCount).toMutableList()
            // B5 anti-repeat: which queue items were already played this shuffle session? If EVERY item has
            // been played the pool is exhausted -> start a fresh cycle so shuffle keeps flowing.
            val playedSnapshot = HashSet(shufflePlayedIds)
            if (playedSnapshot.isNotEmpty() &&
                (0 until totalCount).all { i ->
                    runCatching { player.getMediaItemAt(i).mediaId }.getOrNull()?.let { it in playedSnapshot } == true
                }
            ) {
                shufflePlayedIds.clear()
                player.currentMetadata?.id?.let { shufflePlayedIds.add(it) }
                playedSnapshot.clear()
            }
            // Smart shuffle: nudge tracks you tend to like toward the front, but keep plenty of randomness
            // (random term dominates) so it still feels shuffled, not a fixed favourites list. With no taste
            // profile yet this is just a plain shuffle.
            val p = cachedTaste
            val rnd = java.util.Random()
            // Precompute each index's key ONCE (rnd inside the comparator would crash TimSort).
            val keys = HashMap<Int, Double>(indices.size)
            indices.forEach { i ->
                val m = runCatching { player.getMediaItemAt(i).metadata }.getOrNull()
                val tasteScore = if (p != null && m != null) p.scoreNames(m.artists.map { it.name }, m.title) else 0.0
                var key = tasteScore * 0.5 + rnd.nextDouble()
                // Anti-repeat: already-played songs sink BELOW all not-yet-played ones (big offset), so the
                // whole pool is exhausted before anything repeats. Within each group the smart order applies.
                val id = m?.id
                if (id == null || id !in playedSnapshot) key += 1000.0
                keys[i] = key
            }
            indices.sortByDescending { keys[it] ?: 0.0 }
            val shuffledIndices = indices.toIntArray()

            val currentItemIndexInShuffled = shuffledIndices.indexOf(currentIndex)
            if (currentItemIndexInShuffled != -1) {
                val temp = shuffledIndices[0]
                shuffledIndices[0] = shuffledIndices[currentItemIndexInShuffled]
                shuffledIndices[currentItemIndexInShuffled] = temp
            }
            player.setShuffleOrder(DefaultShuffleOrder(shuffledIndices, System.currentTimeMillis()))
        }
    }

    override fun onPlaybackParametersChanged(playbackParameters: PlaybackParameters) {
        super.onPlaybackParametersChanged(playbackParameters)
        if (playbackParameters.speed != lastPlaybackSpeed) {
            lastPlaybackSpeed = playbackParameters.speed
            discordUpdateJob?.cancel()

            
            discordUpdateJob = scope.launch {
                delay(1000)
                if (player.playWhenReady && player.playbackState == Player.STATE_READY) {
                    currentSong.value?.let { song ->
                        updateDiscordRPC(song)
                    }
                }
            }
        }
    }

    
    private fun getHttpResponseCode(error: PlaybackException): Int? {
        var cause: Throwable? = error.cause
        while (cause != null) {
            if (cause is HttpDataSource.InvalidResponseCodeException) {
                return cause.responseCode
            }
            cause = cause.cause
        }
        return null
    }

    
    private fun isExpiredUrlError(error: PlaybackException): Boolean {
        val responseCode = getHttpResponseCode(error)
        return responseCode == 403
    }

    
    private fun isRangeNotSatisfiableError(error: PlaybackException): Boolean {
        val responseCode = getHttpResponseCode(error)
        return responseCode == 416
    }

    
    private fun isPageReloadError(error: PlaybackException): Boolean {
        val errorMessage = error.message?.lowercase() ?: ""
        val causeMessage = error.cause?.message?.lowercase() ?: ""
        val innerCauseMessage = error.cause?.cause?.message?.lowercase() ?: ""

        val reloadKeywords = listOf(
            "page needs to be reloaded",
            "pagina deve essere ricaricata",
            "la pagina deve essere ricaricata",
            "page must be reloaded",
            "reload",
            "ricaricata"
        )

        return reloadKeywords.any { keyword ->
            errorMessage.contains(keyword) ||
            causeMessage.contains(keyword) ||
            innerCauseMessage.contains(keyword)
        }
    }

    private fun isNetworkRelatedError(error: PlaybackException): Boolean {
        
        if (isExpiredUrlError(error) || isRangeNotSatisfiableError(error) || isPageReloadError(error)) {
            return false
        }
        return error.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED ||
                error.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT ||
                error.errorCode == PlaybackException.ERROR_CODE_TIMEOUT ||
                error.errorCode == PlaybackException.ERROR_CODE_IO_INVALID_HTTP_CONTENT_TYPE ||
                error.cause is java.net.ConnectException ||
                error.cause is java.net.UnknownHostException ||
                (error.cause as? PlaybackException)?.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED
    }

    
    private fun isAudioRendererError(error: PlaybackException): Boolean {
        return error.errorCode == PlaybackException.ERROR_CODE_AUDIO_TRACK_WRITE_FAILED ||
                error.errorCode == PlaybackException.ERROR_CODE_AUDIO_TRACK_INIT_FAILED ||
                (error.cause as? PlaybackException)?.errorCode == PlaybackException.ERROR_CODE_AUDIO_TRACK_WRITE_FAILED ||
                (error.cause as? PlaybackException)?.errorCode == PlaybackException.ERROR_CODE_AUDIO_TRACK_INIT_FAILED ||
                error.errorCode == PlaybackException.ERROR_CODE_FAILED_RUNTIME_CHECK
    }

    private fun isCacheOrStreamCorruptionError(error: PlaybackException): Boolean {
        return error.errorCode == PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED ||
                error.errorCode == PlaybackException.ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE
    }

    override fun onPlayerError(error: PlaybackException) {
        super.onPlayerError(error)

        
        if (!playerInitialized.value) {
            Timber.tag(TAG).e(error, "Player error occurred but player not initialized")
            return
        }

        val mediaId = player.currentMediaItem?.mediaId
        Timber.tag(TAG).w(error, "Player error occurred for $mediaId: errorCode=${error.errorCode}, message=${error.message}")
        reportException(error)

        // VIDEO MODE: if the failing item is the video track (e.g. its muxed URL expired / 403'd), the
        // audio-oriented recovery below would retry the same dead URL and eventually kill the track. Instead
        // drop the stale cached URL and fall back to AUDIO (exitVideoMode restores the normal source), so the
        // song keeps playing. The user can re-enable video to re-resolve a fresh stream.
        if (videoModeMediaId != null && mediaId == videoModeMediaId) {
            videoUrlCache.remove(mediaId)
            exitVideoMode()
            Toast.makeText(this, "Video no disponible — volviendo a audio", Toast.LENGTH_SHORT).show()
            return
        }

        
        if (mediaId != null && hasExceededRetryLimit(mediaId)) {
            Timber.tag(TAG).w("Song $mediaId has exceeded retry limit, skipping")
            markSongAsFailed(mediaId)
            handleFinalFailure()
            return
        }

        
        if (mediaId != null) {
            performAggressiveCacheClear(mediaId)
        }

        
        when {
            isAudioRendererError(error) -> {
                Timber.tag(TAG).d("AudioTrack error detected (${error.errorCode}), performing safe recovery")
                handleAudioRendererError(mediaId)
                return
            }
            isRangeNotSatisfiableError(error) -> {
                Timber.tag(TAG).d("Range Not Satisfiable (416) detected, performing strict recovery")
                handleRangeNotSatisfiableError(mediaId)
                return
            }
            isCacheOrStreamCorruptionError(error) -> {
                Timber.tag(TAG).d("Cache or stream corruption detected, clearing cache and refreshing URL")
                handleExpiredUrlError(mediaId)
                return
            }
            isPageReloadError(error) -> {
                Timber.tag(TAG).d("Page reload error detected, performing strict recovery")
                handlePageReloadError(mediaId)
                return
            }
            isExpiredUrlError(error) -> {
                Timber.tag(TAG).d("Expired URL (403) detected, refreshing stream URL")
                handleExpiredUrlError(mediaId)
                return
            }

            !isNetworkConnected.value || isNetworkRelatedError(error) -> {
                Timber.tag(TAG).d("Network-related error detected, waiting for connection")
                waitOnNetworkError()
                return
            }
        }

        
        if (error.errorCode == PlaybackException.ERROR_CODE_IO_UNSPECIFIED ||
            error.errorCode == PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS) {
            Timber.tag(TAG).d("IO error detected (${error.errorCode}), attempting recovery")
            handleGenericIOError(mediaId)
            return
        }

        
        if (dataStore.get(AutoSkipNextOnErrorKey, false)) {
            Timber.tag(TAG).d("Auto-skipping to next track due to unrecoverable error")
            skipOnError()
        } else {
            Timber.tag(TAG).d("Stopping playback due to unrecoverable error")
            stopOnError()
        }
    }

    
    private fun performAggressiveCacheClear(mediaId: String) {
        Timber.tag(TAG).d("Performing aggressive cache clear for $mediaId")

        
        songUrlCache.remove(mediaId)

        
        try {
            playerCache.removeResource(mediaId)
            Timber.tag(TAG).d("Cleared player cache for $mediaId")
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Failed to clear player cache for $mediaId")
        }

        
        try {
            YTPlayerUtils.forceRefreshForVideo(mediaId)
            Timber.tag(TAG).d("Cleared decryption caches for $mediaId")
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Failed to clear decryption caches for $mediaId")
        }
    }

    
    private fun hasExceededRetryLimit(mediaId: String): Boolean {
        val currentRetries = currentMediaIdRetryCount[mediaId] ?: 0
        return currentRetries >= MAX_RETRY_PER_SONG
    }

    
    private fun incrementRetryCount(mediaId: String) {
        val currentRetries = currentMediaIdRetryCount[mediaId] ?: 0
        currentMediaIdRetryCount[mediaId] = currentRetries + 1
        Timber.tag(TAG).d("Retry count for $mediaId: ${currentRetries + 1}/$MAX_RETRY_PER_SONG")
    }

    
    private fun resetRetryCount(mediaId: String) {
        currentMediaIdRetryCount.remove(mediaId)
        recentlyFailedSongs.remove(mediaId)
    }

    
    private fun markSongAsFailed(mediaId: String) {
        recentlyFailedSongs.add(mediaId)
        currentMediaIdRetryCount.remove(mediaId)

        
        failedSongsClearJob?.cancel()
        failedSongsClearJob = scope.launch {
            delay(5 * 60 * 1000L) 
            recentlyFailedSongs.clear()
            Timber.tag(TAG).d("Cleared recently failed songs list")
        }
    }

    
    private fun handleAudioRendererError(mediaId: String?) {
        if (mediaId == null) {
            handleFinalFailure()
            return
        }

        incrementRetryCount(mediaId)

        retryJob?.cancel()
        retryJob = scope.launch {
            try {
                
                val wasPlaying = player.playWhenReady
                player.pause()
                Timber.tag(TAG).d("Paused playback due to AudioTrack error")

                
                
                delay(RETRY_DELAY_MS * 3) 

                
                if (!playerInitialized.value) {
                    Timber.tag(TAG).w("Player no longer initialized, aborting AudioTrack recovery")
                    return@launch
                }

                val currentIndex = player.currentMediaItemIndex
                if (currentIndex != C.INDEX_UNSET) {
                    
                    val currentPosition = player.currentPosition
                    player.seekTo(currentIndex, currentPosition)
                    player.prepare()

                    Timber.tag(TAG).d("Retrying playback for $mediaId after AudioTrack error")

                    
                    if (wasPlaying) {
                        delay(500) 
                        if (hasAudioFocus && playerInitialized.value) {
                            if (castConnectionHandler?.isCasting?.value != true) {
                                player.play()
                            }
                        }
                    }
                } else {
                    Timber.tag(TAG).w("Invalid media item index during AudioTrack recovery")
                    handleFinalFailure()
                }
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "Error during AudioTrack error recovery")
                handleFinalFailure()
            }
        }
    }

    
    private fun handleRangeNotSatisfiableError(mediaId: String?) {
        if (mediaId == null) {
            handleFinalFailure()
            return
        }

        incrementRetryCount(mediaId)

        retryJob?.cancel()
        retryJob = scope.launch {
            
            performAggressiveCacheClear(mediaId)


            
            val currentIndex = player.currentMediaItemIndex
            player.seekTo(currentIndex, 0)
            player.prepare()

            Timber.tag(TAG).d("Retrying playback for $mediaId after 416 error (from position 0)")
        }
    }

    
    private fun handlePageReloadError(mediaId: String?) {
        if (mediaId == null) {
            handleFinalFailure()
            return
        }

        incrementRetryCount(mediaId)

        retryJob?.cancel()
        retryJob = scope.launch {
            Timber.tag(TAG).d("Handling page reload error for $mediaId")

            
            performAggressiveCacheClear(mediaId)

            
            val currentPosition = player.currentPosition
            val currentIndex = player.currentMediaItemIndex
            player.seekTo(currentIndex, currentPosition)
            player.prepare()

            Timber.tag(TAG).d("Retrying playback for $mediaId after page reload error")
        }
    }

    
    private fun handleExpiredUrlError(mediaId: String?) {
        if (mediaId == null) {
            handleFinalFailure()
            return
        }

        incrementRetryCount(mediaId)

        
        songUrlCache.remove(mediaId)
        Timber.tag(TAG).d("Cleared cached URL for $mediaId")

        
        try {
            YTPlayerUtils.forceRefreshForVideo(mediaId)
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Failed to clear decryption caches")
        }

        retryJob?.cancel()
        retryJob = scope.launch {

            
            val currentPosition = player.currentPosition
            val currentIndex = player.currentMediaItemIndex
            player.seekTo(currentIndex, currentPosition)
            player.prepare()

            Timber.tag(TAG).d("Retrying playback for $mediaId after 403 error")
        }
    }

    
    private fun handleGenericIOError(mediaId: String?) {
        if (mediaId == null) {
            handleFinalFailure()
            return
        }

        incrementRetryCount(mediaId)

        retryJob?.cancel()
        retryJob = scope.launch {
            performAggressiveCacheClear(mediaId)


            val currentPosition = player.currentPosition
            val currentIndex = player.currentMediaItemIndex
            player.seekTo(currentIndex, currentPosition)
            player.prepare()

            Timber.tag(TAG).d("Retrying playback for $mediaId after generic IO error")
        }
    }

    
    private fun handleFinalFailure() {
        if (dataStore.get(AutoSkipNextOnErrorKey, false)) {
            Timber.tag(TAG).d("All recovery attempts exhausted, auto-skipping to next track")
            skipOnError()
        } else {
            Timber.tag(TAG).d("All recovery attempts exhausted, stopping playback")
            stopOnError()
        }
    }

    override fun onDeviceVolumeChanged(volume: Int, muted: Boolean) {
        super.onDeviceVolumeChanged(volume, muted)
        val pauseOnMute = dataStore.get(PauseOnMute, false)

        if ((volume == 0 || muted) && pauseOnMute) {
            if (player.isPlaying) {
                wasPlayingBeforeVolumeMute = true
                isPausedByVolumeMute = true
                player.pause()
            }
        } else if (volume > 0 && !muted && pauseOnMute) {
            if (wasPlayingBeforeVolumeMute && !player.isPlaying && castConnectionHandler?.isCasting?.value != true) {
                wasPlayingBeforeVolumeMute = false
                isPausedByVolumeMute = false
                player.play()
            }
        }
    }

    private fun createCacheDataSource(): CacheDataSource.Factory =
        CacheDataSource
            .Factory()
            .setCache(downloadCache)
            .setUpstreamDataSourceFactory(
                CacheDataSource
                    .Factory()
                    .setCache(playerCache)
                    .setUpstreamDataSourceFactory(
                        OkHttpDataSource.Factory(
                            OkHttpClient
                                    .Builder()
                                    .dns(object : Dns {
                                        override fun lookup(hostname: String): List<InetAddress> {
                                            val addresses = Dns.SYSTEM.lookup(hostname)
                                            return when (this@MusicService.ipVersion) {
                                                IpVersion.IPV4 -> addresses.filter { it is Inet4Address }.ifEmpty { addresses }
                                                IpVersion.IPV6 -> addresses.filter { it is Inet6Address }.ifEmpty { addresses }
                                                IpVersion.AUTO -> addresses
                                            }
                                        }
                                    })
                                    .proxy(YouTube.proxy)
                                    .proxyAuthenticator { _, response ->
                                        YouTube.proxyAuth?.let { auth ->
                                            response.request.newBuilder()
                                                .header("Proxy-Authorization", auth)
                                                .build()
                                        } ?: response.request
                                    }
                                    .build()
                            )
                    )
            ).setCacheWriteDataSinkFactory(null)
            .setFlags(FLAG_IGNORE_CACHE_ON_ERROR)

    
    private var isSilenceSkipping = false

    private fun handleLongSilenceDetected() {
        if (!instantSilenceSkipEnabled.value) return
        if (silenceSkipJob?.isActive == true) return

        silenceSkipJob = scope.launch {
            
            delay(200)
            performInstantSilenceSkip()
        }
    }

    private suspend fun performInstantSilenceSkip() {
        val duration = player.duration.takeIf { it != C.TIME_UNSET && it > 0 } ?: return
        if (duration <= INSTANT_SILENCE_SKIP_STEP_MS) return

        isSilenceSkipping = true
        try {
            var hops = 0
            val silenceProcessor = playerSilenceProcessors[player] ?: return
            while (coroutineContext.isActive && instantSilenceSkipEnabled.value && silenceProcessor.isCurrentlySilent()) {
                val current = player.currentPosition
                val target = (current + INSTANT_SILENCE_SKIP_STEP_MS).coerceAtMost(duration - 500)

                if (target <= current) break

                
                silenceProcessor.resetTracking()
                player.seekTo(target)
                hops++

                if (hops >= 80 || target >= duration - 500) break

                delay(INSTANT_SILENCE_SKIP_SETTLE_MS)
            }
            if (hops > 0) {
                Timber.tag(TAG).d("Silence skip: jumped $hops times")
            }
        } finally {
            isSilenceSkipping = false
        }
    }

    private fun updateDiscordRPC(song: Song, showFeedback: Boolean = false) {
    }

    /**
     * Toggles video mode. INTEGRATED into the MAIN player: the current track's media source is swapped to
     * its muxed (video+audio) stream (resolved via [YTPlayerUtils.videoStreamUrlDiag], served by
     * [videoDataSourceFactory] with the right User-Agent) and rendered on the main player's TextureView.
     * One engine → background audio, native transport/seek, no double audio. Sticky across track changes
     * (see onMediaItemTransition). The music is NEVER paused; only the current track's source changes.
     */
    fun toggleVideoMode() {
        if (_videoMode.value) {
            exitVideoMode()
        } else {
            userHasUsedVideo = true
            _videoMode.value = true
            applyVideoToCurrent()
        }
    }

    /** Background-resolve a track's muxed video URL into the cache so a later toggle/switch is instant. */
    private fun prefetchVideoUrl(id: String?) {
        if (id.isNullOrEmpty() || id.isLocalMediaId() || id.startsWith("http", ignoreCase = true)) return
        val cached = videoUrlCache[id]?.takeIf { it.second > System.currentTimeMillis() }?.first
        if (cached != null) {
            applyVideoUrlToNextItem(id, cached)
            return
        }
        scope.launch(Dispatchers.IO) {
            val url = runCatching { YTPlayerUtils.videoStreamUrl(id, connectivityManager) }.getOrNull()
            if (!url.isNullOrEmpty()) {
                videoUrlCache[id] = url to (System.currentTimeMillis() + 5 * 60 * 1000L)
                withContext(Dispatchers.Main) {
                    applyVideoUrlToNextItem(id, url)
                }
            }
        }
    }

    private fun applyVideoUrlToNextItem(id: String, url: String) {
        if (!_videoMode.value) return
        val idx = player.nextMediaItemIndex
        if (idx == C.INDEX_UNSET) return
        val item = runCatching { player.getMediaItemAt(idx) }.getOrNull() ?: return
        if (item.mediaId != id) return
        
        val currentUri = item.localConfiguration?.uri?.toString()
        if (currentUri == url) return
        
        if (currentUri != null && !preloadedVideoOriginalUris.containsKey(id)) {
            preloadedVideoOriginalUris[id] = currentUri
        }

        player.replaceMediaItem(idx, item.buildUpon().setUri(url).build())
        Timber.tag(TAG).d("Pre-applied video URL to next track: $id")
    }

    /** Resolve the current track's muxed video URL and swap its source in-place (audio is never stopped). */
    private fun applyVideoToCurrent() {
        val item = player.currentMediaItem ?: return
        val id = item.mediaId
        // Only the current track is ever a video source — restore any previous one to audio first.
        if (videoModeMediaId != null && videoModeMediaId != id) restoreVideoTrackToAudio()

        // Video PODCAST episode: it already carries a direct video stream — swap to it immediately, no
        // YouTube resolution (the id here is an http audio URL, which YTPlayerUtils can't resolve anyway).
        val podcastVideo = player.currentMetadata?.podcastVideoUrl
        if (!podcastVideo.isNullOrEmpty()) { swapToVideo(id, podcastVideo, isMuxed = true); return }
        // A direct/local track with no video stream (e.g. an audio-only podcast reached while sticky video
        // is still armed) can't show video — disarm video mode and play audio quietly (no failed-resolution
        // toast, and crucially no stuck spinner: leaving _videoMode=true here would show an endless spinner
        // over the cover with no video and no on-screen toggle to exit). Mirrors the no-video YouTube path.
        if (id.startsWith("http", ignoreCase = true) || id.isLocalMediaId()) {
            _videoMode.value = false
            _videoUrl.value = null
            return
        }
        // A YouTube track that is NOT a video song can't show video → disarm video mode SILENTLY (no
        // resolution attempt, no "Video falló" toast) and keep playing audio. This is the sticky-video case
        // where the next track has no video: we drop to audio cleanly instead of erroring.
        if (player.currentMetadata?.isVideoSong != true) {
            _videoMode.value = false
            _videoUrl.value = null
            return
        }

        _videoUrl.value = null  // spinner while resolving
        val cached = videoUrlCache[id]?.takeIf { it.second > System.currentTimeMillis() }?.first
        if (!cached.isNullOrEmpty()) { swapToVideo(id, cached); return }
        scope.launch(Dispatchers.IO) {
            val result = runCatching { YTPlayerUtils.videoStreamUrlDiag(id, connectivityManager) }
                .getOrElse { Result.failure(it) }
            val url = result.getOrNull()
            withContext(Dispatchers.Main) {
                if (!_videoMode.value || player.currentMediaItem?.mediaId != id) return@withContext
                if (url.isNullOrEmpty()) {
                    _videoMode.value = false
                    _videoUrl.value = null
                    val ex = result.exceptionOrNull()
                    val reason = ex?.let { "${it.javaClass.simpleName}: ${it.message}" } ?: "sin formato de video"
                    Toast.makeText(this@MusicService, "Video falló — $reason", Toast.LENGTH_LONG).show()
                    return@withContext
                }
                videoUrlCache[id] = url to (System.currentTimeMillis() + 5 * 60 * 1000L)
                swapToVideo(id, url)
            }
        }
    }

    /** Swap the current item's source URI to [url] (the muxed stream) so the factory builds a video source
     * rendered on the main player. Keeps position + play state. */
    private fun swapToVideo(id: String, url: String, isMuxed: Boolean = false) {
        val idx = player.currentMediaItemIndex
        val item = player.currentMediaItem ?: return
        if (item.mediaId != id) return
        videoModeOriginalUri = preloadedVideoOriginalUris.remove(id) ?: item.localConfiguration?.uri?.toString()
        videoModeMediaId = id
        // Podcast video is a single muxed stream (has audio) → don't merge a 2nd audio; YouTube is video-only.
        videoModeIsMuxedPodcast = isMuxed
        
        if (item.localConfiguration?.uri?.toString() == url) {
            _videoUrl.value = url
            return
        }

        val pos = player.currentPosition
        val playing = player.playWhenReady
        player.replaceMediaItem(idx, item.buildUpon().setUri(url).build())
        player.seekTo(idx, pos)
        player.playWhenReady = playing
        player.prepare()
        _videoUrl.value = url
    }

    /** Restore the video track (wherever it sits in the queue) back to its normal audio source. */
    private fun restoreVideoTrackToAudio() {
        val vid = videoModeMediaId ?: return
        val origUri = videoModeOriginalUri
        videoModeMediaId = null
        videoModeOriginalUri = null
        videoModeIsMuxedPodcast = false
        if (origUri == null) return
        for (i in 0 until player.mediaItemCount) {
            val it = runCatching { player.getMediaItemAt(i) }.getOrNull() ?: continue
            if (it.mediaId == vid) {
                val isCurrent = i == player.currentMediaItemIndex
                val pos = if (isCurrent) player.currentPosition else 0L
                val playing = player.playWhenReady
                player.replaceMediaItem(i, it.buildUpon().setUri(origUri).build())
                if (isCurrent) {
                    player.seekTo(i, pos)
                    player.playWhenReady = playing
                    player.prepare()
                }
                break
            }
        }
    }

    /** Leaves video mode: restore the current track to audio (playback continues at the same position). */
    fun exitVideoMode() {
        if (!_videoMode.value && videoModeMediaId == null) return
        _videoMode.value = false
        _videoUrl.value = null
        restoreVideoTrackToAudio()
    }

    private fun createDataSourceFactory(): DataSource.Factory {
        return ResolvingDataSource.Factory(
            DefaultDataSource.Factory(this, createCacheDataSource())
        ) { dataSpec ->
            val mediaId = dataSpec.key ?: error("No media id")
            if (mediaId.isLocalMediaId()) return@Factory dataSpec
            // Podcast episodes (and any direct-URL media) are already a playable audio stream — play
            // the URL straight through instead of resolving it through YouTube.
            if (mediaId.startsWith("http://", ignoreCase = true) || mediaId.startsWith("https://", ignoreCase = true)) {
                return@Factory dataSpec.withUri(mediaId.toUri())
            }



            var shouldBypassCache = bypassCacheForQualityChange.contains(mediaId)

            val cachedLength = androidx.media3.datasource.cache.ContentMetadata.getContentLength(downloadCache.getContentMetadata(mediaId))
            val isFullyDownloaded = cachedLength != androidx.media3.common.C.LENGTH_UNSET.toLong() && cachedLength > 0 && downloadCache.isCached(mediaId, 0, cachedLength)

            val isCurrentlyPlaying = currentPlayingMediaId == mediaId
            val dbFormat = runBlocking(Dispatchers.IO) { database.format(mediaId).firstOrNull() }

            val lockedQuality = if (isCurrentlyPlaying && dbFormat != null) {
                when {
                    dbFormat.mimeType.contains("flac", ignoreCase = true) -> iad1tya.echo.music.constants.AudioQuality.LOSSLESS
                    dbFormat.mimeType.contains("mp4", ignoreCase = true) || dbFormat.mimeType.contains("m4a", ignoreCase = true) -> iad1tya.echo.music.constants.AudioQuality.SAAVN
                    else -> iad1tya.echo.music.constants.AudioQuality.OPUS
                }
            } else {
                audioQuality
            }

            if (!shouldBypassCache && !isFullyDownloaded && dbFormat != null) {
                val isLosslessCache = dbFormat.codecs == "flac"
                val isSaavnCache = dbFormat.codecs == "mp4a.40.2" || dbFormat.mimeType.contains("mp4", ignoreCase = true)

                val cacheMatchesTarget = when (lockedQuality) {
                    iad1tya.echo.music.constants.AudioQuality.LOSSLESS -> isLosslessCache
                    iad1tya.echo.music.constants.AudioQuality.SAAVN -> isSaavnCache
                    iad1tya.echo.music.constants.AudioQuality.OPUS -> !isLosslessCache && !isSaavnCache
                }

                if (!cacheMatchesTarget) {
                    shouldBypassCache = true
                    Timber.tag(TAG).i("Quality changed to $lockedQuality for $mediaId. Clearing playerCache to prevent container mismatch.")
                    playerCache.removeResource(mediaId)
                }
            }

            if (!shouldBypassCache) {
                if (downloadCache.isCached(
                        mediaId,
                        dataSpec.position,
                        if (dataSpec.length >= 0) dataSpec.length else 1
                    )
                ) {
                    scope.launch(Dispatchers.IO) { recoverSong(mediaId) }
                    return@Factory dataSpec
                }

                if (playerCache.isCached(mediaId, dataSpec.position, CHUNK_LENGTH)) {
                    songUrlCache[mediaId]?.takeIf { it.second > System.currentTimeMillis() }?.let {
                        scope.launch(Dispatchers.IO) { recoverSong(mediaId) }
                        return@Factory dataSpec.withUri(it.first.toUri())
                    }
                    Timber.tag(TAG).w("Ghost cache entry for $mediaId, re-fetching")
                    playerCache.removeResource(mediaId)
                }

                songUrlCache[mediaId]?.takeIf { it.second > System.currentTimeMillis() }?.let {
                    scope.launch(Dispatchers.IO) { recoverSong(mediaId) }
                    return@Factory dataSpec.withUri(it.first.toUri())
                }
            } else {
                Timber.tag("MusicService").i("BYPASSING CACHE for $mediaId due to quality change")
            }

            Timber.tag("MusicService").i("FETCHING STREAM: $mediaId | quality=$lockedQuality")
            val playbackData = runBlocking(Dispatchers.IO) {
                val dbSong = database.song(mediaId).firstOrNull()
                val knownArtist = dbSong?.artists?.joinToString { it.name }?.replace(" - Topic", "")
                val knownTitle = dbSong?.song?.title
                val knownDuration = dbSong?.song?.duration?.let { if (it > 0) it * 1000L else null }

                YTPlayerUtils.playerResponseForPlayback(
                    mediaId,
                    audioQuality = lockedQuality,
                    connectivityManager = connectivityManager,
                    context = this@MusicService,
                    knownArtist = knownArtist,
                    knownTitle = knownTitle,
                    knownDurationMs = knownDuration
                )
            }.getOrElse { throwable ->
                when (throwable) {
                    is PlaybackException -> throw throwable

                    is java.net.ConnectException, is java.net.UnknownHostException -> {
                        throw PlaybackException(
                            getString(R.string.error_no_internet),
                            throwable,
                            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED
                        )
                    }

                    is java.net.SocketTimeoutException -> {
                        throw PlaybackException(
                            getString(R.string.error_timeout),
                            throwable,
                            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT
                        )
                    }

                    else -> throw PlaybackException(
                        getString(R.string.error_unknown),
                        throwable,
                        PlaybackException.ERROR_CODE_REMOTE_ERROR
                    )
                }
            }

            val nonNullPlayback = requireNotNull(playbackData) {
                getString(R.string.error_unknown)
            }
            run {
                val format = nonNullPlayback.format

                val isFinalLossless = format.mimeType.contains("flac", ignoreCase = true)
                val isFinalSaavn = format.mimeType.contains("mp4", ignoreCase = true) || format.mimeType.contains("m4a", ignoreCase = true)

                if (dbFormat != null && !shouldBypassCache) {
                    val cacheIsLossless = dbFormat.codecs == "flac"
                    val cacheIsSaavn = dbFormat.codecs == "mp4a.40.2" || dbFormat.mimeType.contains("mp4", ignoreCase = true)

                    if (isFinalLossless != cacheIsLossless || isFinalSaavn != cacheIsSaavn) {
                        Timber.tag(TAG).w("Format fallback detected AFTER fetch. Clearing playerCache to prevent mismatch crash.")
                        playerCache.removeResource(mediaId)

                        if (isCurrentlyPlaying) {
                            Timber.tag(TAG).e("Format changed mid-stream for $mediaId. Throwing to force player restart.")
                            throw PlaybackException(
                                "Container format changed mid-stream due to fallback",
                                null,
                                PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED
                            )
                        }
                    }
                }

                // Keep any loudness we already had if this (re)fetch doesn't carry it — e.g. the
                // auto-download on "like" re-stores the format and can come back WITHOUT loudness;
                // overwriting the real value with null made normalization fall back to the default and
                // drop the volume.
                val loudnessDb = nonNullPlayback.audioConfig?.loudnessDb ?: dbFormat?.loudnessDb
                val perceptualLoudnessDb = nonNullPlayback.audioConfig?.perceptualLoudnessDb ?: dbFormat?.perceptualLoudnessDb
                // Preserve a previously-measured loudness too: a re-fetch (e.g. the like/auto-download) must
                // not wipe the cached measurement (it would force a needless re-measure on the next play).
                val measuredLoudnessDb = dbFormat?.measuredLoudnessDb

                Timber.tag(TAG).d("Storing format for $mediaId with loudnessDb: $loudnessDb, perceptualLoudnessDb: $perceptualLoudnessDb, measuredLoudnessDb: $measuredLoudnessDb")
                if (loudnessDb == null && perceptualLoudnessDb == null) {
                    Timber.tag(TAG).w("No loudness data available from YouTube for video: $mediaId")
                }

                database.query {
                    upsert(
                        FormatEntity(
                            id = mediaId,
                            itag = format.itag,
                            mimeType = format.mimeType.split(";")[0],
                            codecs = format.mimeType.split("codecs=")[1].removeSurrounding("\""),
                            bitrate = format.bitrate,
                            sampleRate = format.audioSampleRate,
                            contentLength = format.contentLength ?: 0L,
                            loudnessDb = loudnessDb,
                            perceptualLoudnessDb = perceptualLoudnessDb,
                            measuredLoudnessDb = measuredLoudnessDb,
                            playbackUrl = nonNullPlayback.playbackTracking?.videostatsPlaybackUrl?.baseUrl
                        )
                    )
                }
                scope.launch(Dispatchers.IO) { recoverSong(mediaId, nonNullPlayback) }

                
                if (bypassCacheForQualityChange.remove(mediaId)) {
                    Timber.tag("MusicService").d("Cleared bypass cache flag for $mediaId after fresh fetch")
                }

                val streamUrl = nonNullPlayback.streamUrl

                songUrlCache[mediaId] =
                    streamUrl to System.currentTimeMillis() + (nonNullPlayback.streamExpiresInSeconds * 1000L)
                
                return@Factory dataSpec.withUri(streamUrl.toUri())
            }
        }
    }

    // Data source for the VIDEO-mode track: a plain OkHttp source that sets the correct per-client
    // User-Agent for googlevideo URLs (they 403 without it). No app cache (avoids colliding with the
    // audio cache and re-streams cleanly on seek). This is the fix that makes video render on the MAIN
    // player (feeding the video URL through the normal audio data source never worked).
    private val videoDataSourceFactory: DataSource.Factory by lazy {
        val ok = okhttp3.OkHttpClient.Builder()
            .proxy(com.music.innertube.YouTube.proxy)
            .addInterceptor { chain ->
                val req = chain.request()
                val host = req.url.host
                val isYt = host.endsWith("googlevideo.com") || host.endsWith("youtube.com") ||
                    host.endsWith("googleusercontent.com") || host.endsWith("youtube-nocookie.com") ||
                    host.endsWith("ytimg.com")
                if (!isYt) return@addInterceptor chain.proceed(req)
                val c = req.url.queryParameter("c")?.trim().orEmpty()
                val agent = when {
                    c.startsWith("WEB", true) -> com.music.innertube.models.YouTubeClient.USER_AGENT_WEB
                    c.startsWith("IOS", true) -> com.music.innertube.models.YouTubeClient.IOS.userAgent
                    c.startsWith("ANDROID_VR", true) -> com.music.innertube.models.YouTubeClient.ANDROID_VR_NO_AUTH.userAgent
                    c.startsWith("ANDROID", true) -> com.music.innertube.models.YouTubeClient.MOBILE.userAgent
                    else -> com.music.innertube.models.YouTubeClient.USER_AGENT_WEB
                }
                chain.proceed(req.newBuilder().header("User-Agent", agent).build())
            }
            .build()
        DefaultDataSource.Factory(this, androidx.media3.datasource.okhttp.OkHttpDataSource.Factory(ok))
    }

    // Video mode is INTEGRATED into the main player: the current track's source is swapped to its muxed
    // (video+audio) stream (via [videoDataSourceFactory]) and rendered on the main player's TextureView.
    // One engine → background audio, native transport/seek, no double audio. Other tracks stay audio.
    private fun createMediaSourceFactory(): androidx.media3.exoplayer.source.MediaSource.Factory {
        val default = DefaultMediaSourceFactory(
            createDataSourceFactory(),
            androidx.media3.extractor.DefaultExtractorsFactory()
        )
        val videoFactory = androidx.media3.exoplayer.source.ProgressiveMediaSource.Factory(videoDataSourceFactory)
        return object : androidx.media3.exoplayer.source.MediaSource.Factory {
            override fun getSupportedTypes(): IntArray = default.supportedTypes
            override fun setDrmSessionManagerProvider(
                provider: androidx.media3.exoplayer.drm.DrmSessionManagerProvider
            ): androidx.media3.exoplayer.source.MediaSource.Factory {
                default.setDrmSessionManagerProvider(provider); return this
            }
            override fun setLoadErrorHandlingPolicy(
                policy: androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy
            ): androidx.media3.exoplayer.source.MediaSource.Factory {
                default.setLoadErrorHandlingPolicy(policy); return this
            }
            override fun createMediaSource(
                mediaItem: MediaItem
            ): androidx.media3.exoplayer.source.MediaSource {
                // Video mode: the track's item URI was swapped (swapToVideo) to an adaptive VIDEO-ONLY HD
                // stream. MERGE it with the track's normal AUDIO source (resolved from the original URI via
                // the default/ResolvingDataSource factory — the same path restore uses) → HD video + the
                // app's normal audio, synced on the same video timeline, no double audio. clipDurations
                // tolerates a tiny end-of-stream length difference between the two streams.
                if (mediaItem.mediaId == videoModeMediaId) {
                    val videoSource = videoFactory.createMediaSource(mediaItem)
                    val origUri = videoModeOriginalUri
                    // Merge a separate audio source ONLY for genuinely video-only streams (YouTube). A muxed
                    // podcast already has audio — merging would add a redundant/conflicting 2nd audio track.
                    if (origUri != null && !videoModeIsMuxedPodcast) {
                        val audioItem = mediaItem.buildUpon().setUri(origUri).build()
                        val audioSource = default.createMediaSource(audioItem)
                        return androidx.media3.exoplayer.source.MergingMediaSource(
                            false, true, videoSource, audioSource
                        )
                    }
                    return videoSource
                }
                return default.createMediaSource(mediaItem)
            }
        }
    }

    private fun createRenderersFactory(
        silenceProcessor: iad1tya.echo.music.playback.audio.SilenceDetectorAudioProcessor,
        eqProcessor: CustomEqualizerAudioProcessor,
        normProcessor: iad1tya.echo.music.eq.audio.NormalizationGainAudioProcessor,
        limiterProcessor: iad1tya.echo.music.eq.audio.TruePeakLimiterAudioProcessor
    ) =
        object : DefaultRenderersFactory(this) {
            override fun buildAudioSink(
                context: Context,
                enableFloatOutput: Boolean,
                enableAudioTrackPlaybackParams: Boolean,
            ) = DefaultAudioSink
                .Builder(this@MusicService)
                .setEnableFloatOutput(true) // ALWAYS output Float for 32-bit pure path
                .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
                .setAudioProcessorChain(
                    DefaultAudioSink.DefaultAudioProcessorChain(
                        silenceProcessor,
                        eqProcessor,
                        normProcessor,
                        limiterProcessor
                    )
                ).build()
        }

    override fun onPlaybackStatsReady(
        eventTime: AnalyticsListener.EventTime,
        playbackStats: PlaybackStats,
    ) {
        val mediaItem = eventTime.timeline.getWindow(eventTime.windowIndex, Timeline.Window()).mediaItem
        val historyDurationMs = dataStore[HistoryDuration]?.times(1000f) ?: 30000f

        if (playbackStats.totalPlayTimeMs >= historyDurationMs &&
            !dataStore.get(PauseListenHistoryKey, false)
        ) {
            database.query {
                incrementTotalPlayTime(mediaItem.mediaId, playbackStats.totalPlayTimeMs)
                try {
                    insert(
                        Event(
                            songId = mediaItem.mediaId,
                            timestamp = LocalDateTime.now(),
                            playTime = playbackStats.totalPlayTimeMs,
                        ),
                    )
                } catch (e: SQLException) {
                    Timber.d(e, "Failed to insert playback Event (stats)")
                }
            }
        }

        if (playbackStats.totalPlayTimeMs >= historyDurationMs) {
            scope.launch(Dispatchers.IO) {
                val playbackUrl = database.format(mediaItem.mediaId).first()?.playbackUrl
                    ?: YTPlayerUtils.playerResponseForMetadata(mediaItem.mediaId, null)
                        .getOrNull()?.playbackTracking?.videostatsPlaybackUrl?.baseUrl
                playbackUrl?.let {
                    YouTube.registerPlayback(null, playbackUrl)
                        .onFailure {
                            reportException(it)
                        }
                }
            }
        }
    }

    /**
     * Lightweight position checkpoint: writes only the player-state file (index + position + flags),
     * captured on the calling (Main) thread and flushed on IO. Used by the periodic saver so an app
     * update mid-song resumes at the exact position. The queue file itself is saved on queue changes.
     */
    private fun savePlaybackPositionToDisk() {
        if (player.mediaItemCount == 0) return
        val state = PersistPlayerState(
            playWhenReady = player.playWhenReady,
            repeatMode = player.repeatMode,
            shuffleModeEnabled = player.shuffleModeEnabled,
            // Persist the USER's intended volume, never the live player.volume (which is transiently
            // lowered during a crossfade or audio-focus duck). Saving the transient value and restoring
            // it later left playback permanently silent.
            volume = (if (::playerVolume.isInitialized) playerVolume.value else player.volume),
            currentPosition = player.currentPosition,
            currentMediaItemIndex = player.currentMediaItemIndex,
            playbackState = player.playbackState,
        )
        scope.launch(Dispatchers.IO) {
            runCatching {
                filesDir.resolve(PERSISTENT_PLAYER_STATE_FILE).outputStream().use { fos ->
                    ObjectOutputStream(fos).use { it.writeObject(state) }
                }
            }
        }
    }

    private fun saveQueueToDisk(synchronous: Boolean = false) {
        if (player.mediaItemCount == 0) {
            Timber.tag(TAG).d("Skipping queue save - no media items")
            return
        }

        try {
            
            val persistQueue = currentQueue.toPersistQueue(
                title = queueTitle,
                items = player.mediaItems.mapNotNull { it.metadata },
                mediaItemIndex = player.currentMediaItemIndex,
                position = player.currentPosition
            )

            val persistAutomix =
                PersistQueue(
                    title = "automix",
                    items = automixItems.value.mapNotNull { it.metadata },
                    mediaItemIndex = 0,
                    position = 0,
                )

            
            val persistPlayerState = PersistPlayerState(
                playWhenReady = player.playWhenReady,
                repeatMode = player.repeatMode,
                shuffleModeEnabled = player.shuffleModeEnabled,
                // Persist the USER's intended volume, never the live player.volume (which is transiently
            // lowered during a crossfade or audio-focus duck). Saving the transient value and restoring
            // it later left playback permanently silent.
            volume = (if (::playerVolume.isInitialized) playerVolume.value else player.volume),
                currentPosition = player.currentPosition,
                currentMediaItemIndex = player.currentMediaItemIndex,
                playbackState = player.playbackState
            )

            // Snapshot is built above on the calling (player) thread; only the file IO runs off it.
            val writeAll: () -> Unit = {
                runCatching {
                    filesDir.resolve(PERSISTENT_QUEUE_FILE).outputStream().use { fos ->
                        ObjectOutputStream(fos).use { oos ->
                            oos.writeObject(persistQueue)
                        }
                    }
                    Timber.tag(TAG).d("Queue saved successfully")
                }.onFailure {
                    Timber.tag(TAG).e(it, "Failed to save queue")
                    reportException(it)
                }

                runCatching {
                    filesDir.resolve(PERSISTENT_AUTOMIX_FILE).outputStream().use { fos ->
                        ObjectOutputStream(fos).use { oos ->
                            oos.writeObject(persistAutomix)
                        }
                    }
                    Timber.tag(TAG).d("Automix saved successfully")
                }.onFailure {
                    Timber.tag(TAG).e(it, "Failed to save automix")
                    reportException(it)
                }

                runCatching {
                    filesDir.resolve(PERSISTENT_PLAYER_STATE_FILE).outputStream().use { fos ->
                        ObjectOutputStream(fos).use { oos ->
                            oos.writeObject(persistPlayerState)
                        }
                    }
                    Timber.tag(TAG).d("Player state saved successfully")
                }.onFailure {
                    Timber.tag(TAG).e(it, "Failed to save player state")
                    reportException(it)
                }
            }
            // onDestroy must write SYNCHRONOUSLY: it cancels the service scope right after, which would abort
            // an async write and lose the final queue save. Everywhere else writes off the player thread.
            if (synchronous) writeAll() else scope.launch(Dispatchers.IO) { writeAll() }
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error during queue save operation")
            reportException(e)
        }
    }

    override fun onDestroy() {
        isRunning = false

        try {
            unregisterReceiver(screenStateReceiver)
        } catch (e: Exception) {
            Timber.tag(TAG).d("screenStateReceiver was not registered: ${e.message}")
        }
        audioManager.unregisterAudioDeviceCallback(audioDeviceCallback)
        castConnectionHandler?.release()
        if (dataStore.get(PersistentQueueKey, true)) {
            saveQueueToDisk(synchronous = true)
        }
        if (discordRpc?.isRpcRunning() == true) {
            discordRpc?.closeRPC()
        }
        discordRpc = null
        connectivityObserver.unregister()
        abandonAudioFocus()
        releaseLoudnessEnhancer()
        mediaSession.release()
        player.removeListener(this)
        player.removeListener(sleepTimer)
        playerSilenceProcessors.remove(player)
        playerNormProcessors.remove(player); playerLimiterProcessors.remove(player)
        playerEqProcessors.remove(player)?.let { eq -> equalizerService.removeAudioProcessor(eq) }

        // Release crossfade players (incl. any preloaded incoming one) so they don't leak.
        crossfadeJob?.cancel()
        crossfadeTriggerJob?.cancel()
        crossfadePreloadJob?.cancel()
        secondaryPlayer?.let { 
            playerNormProcessors.remove(it)
            playerLimiterProcessors.remove(it)
            playerEqProcessors.remove(it)?.let { eq -> equalizerService.removeAudioProcessor(eq) }
        }
        runCatching { secondaryPlayer?.release() }
        secondaryPlayer = null
        fadingPlayer?.let { 
            playerNormProcessors.remove(it)
            playerLimiterProcessors.remove(it)
            playerEqProcessors.remove(it)?.let { eq -> equalizerService.removeAudioProcessor(eq) }
        }
        runCatching { fadingPlayer?.release() }
        fadingPlayer = null
        playerNormProcessors.clear()
        playerLimiterProcessors.clear()
        playerEqProcessors.values.forEach { eq -> equalizerService.removeAudioProcessor(eq) }
        playerEqProcessors.clear()

        player.release()
        discordUpdateJob?.cancel()
        // Cancel the service scope so its long-lived collectors (DataStore flows, connectivity, the periodic
        // persist/widget while-loops) stop instead of leaking after the service is destroyed. playQueue
        // re-creates the scope if it's no longer active, so this is safe.
        runCatching { scope.coroutineContext[kotlinx.coroutines.Job]?.cancel() }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?) = super.onBind(intent) ?: binder

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo) = mediaSession

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            MusicWidgetReceiver.ACTION_PLAY_PAUSE -> {
                if (player.isPlaying) player.pause() else player.play()
                updateWidgetUI(player.isPlaying)
            }
            MusicWidgetReceiver.ACTION_LIKE -> {
                toggleLike()
            }
            MusicWidgetReceiver.ACTION_NEXT -> {
                player.seekToNext()
                updateWidgetUI(player.isPlaying)
            }
            MusicWidgetReceiver.ACTION_PREVIOUS -> {
                player.seekToPrevious()
                updateWidgetUI(player.isPlaying)
            }
            MusicWidgetReceiver.ACTION_UPDATE_WIDGET -> {
                updateWidgetUI(player.isPlaying)
            }
        }

        return super.onStartCommand(intent, flags, startId)
    }

    
    private fun updateWidgetUI(isPlaying: Boolean) {
        scope.launch {
            try {
                val songData = currentSong.value
                val song = songData?.song
                val songTitle = song?.title ?: getString(R.string.no_song_playing)
                val artistName = songData?.artists?.joinToString(", ") { it.name } ?: getString(R.string.tap_to_open)
                val isLiked = songData?.song?.liked == true

                widgetManager.updateWidgets(
                    title = songTitle,
                    artist = artistName,
                    artworkUri = song?.thumbnailUrl,
                    isPlaying = isPlaying,
                    isLiked = isLiked,
                    duration = if (player.duration != C.TIME_UNSET) player.duration else 0,
                    currentPosition = player.currentPosition
                )
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "Failed to update media metadata/notification")
            }
        }
    }

    private var widgetUpdateJob: Job? = null

    private fun startWidgetUpdates() {
        widgetUpdateJob?.cancel()
        widgetUpdateJob = scope.launch {
            while (isActive) {
                if (player.isPlaying) {
                    updateWidgetUI(true)
                }
                delay(1000)
            }
        }
    }

    private fun stopWidgetUpdates() {
        widgetUpdateJob?.cancel()
        widgetUpdateJob = null
    }

    private fun shareSong() {
        val songData = currentSong.value
        val songId = songData?.song?.id ?: return

        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, ShareLinks.song(songId))
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        startActivity(Intent.createChooser(shareIntent, null).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    }

    
    suspend fun getStreamUrl(mediaId: String): String? {
        return withContext(Dispatchers.IO) {
            try {
                val playbackData = YTPlayerUtils.playerResponseForPlayback(
                    videoId = mediaId,
                    audioQuality = audioQuality,
                    connectivityManager = connectivityManager,
                ).getOrNull()
                playbackData?.streamUrl
            } catch (e: Exception) {
                timber.log.Timber.e(e, "Failed to get stream URL for Cast")
                null
            }
        }
    }

    
    // Initializing the Cast framework spins up GMS session listeners that can auto-resume a Cast
    // session and ask Android to (re)start this service in the foreground. If that happens while the
    // app is in the background (a system/Cast-triggered service creation), Android 12+ throws
    // ForegroundServiceStartNotAllowedException and the app crashes. So we defer Cast init to the first
    // real playback — by then the service is legitimately foregrounded and the start is allowed.
    private fun initializeCast() {
        if (castInitAttempted) return
        castInitAttempted = true
        if (dataStore.get(iad1tya.echo.music.constants.EnableGoogleCastKey, true)) {
            try {
                castConnectionHandler = CastConnectionHandler(this, scope, this)
                castConnectionHandler?.initialize()
                timber.log.Timber.d("Google Cast initialized")
            } catch (e: Exception) {
                timber.log.Timber.e(e, "Failed to initialize Google Cast")
            }
        }
    }


    override fun onPositionDiscontinuity(
        oldPosition: Player.PositionInfo,
        newPosition: Player.PositionInfo,
        reason: Int
    ) {
        if (reason == Player.DISCONTINUITY_REASON_SEEK) {
            scheduleCrossfade()
        }
    }

    private fun scheduleCrossfade() {
        crossfadeTriggerJob?.cancel()
        crossfadeTriggerJob = null
        crossfadePreloadJob?.cancel()
        crossfadePreloadJob = null
        // Release any incoming player we preloaded for a transition that's no longer happening (user
        // skipped, seeked, queue changed) so we never leak a second ExoPlayer.
        if (!isCrossfading) {
            secondaryPlayer?.let {
                playerNormProcessors.remove(it)
                playerLimiterProcessors.remove(it)
                playerEqProcessors.remove(it)?.let { eq -> equalizerService.removeAudioProcessor(eq) }
                it.stop()
                it.clearMediaItems()
                it.release()
            }
            secondaryPlayer = null
        }
        if (!crossfadeEnabled || player.duration == C.TIME_UNSET || player.duration <= crossfadeDuration) return
        // Crossfade builds a SECOND ExoPlayer and copies the queue into it; the video item (a cache-less
        // muxed source with no TextureView attached on the secondary player) would break. Skip crossfade
        // entirely while video mode is on.
        if (_videoMode.value) return
        if (crossfadeGapless && isNextItemGapless()) return
        if (!player.hasNextMediaItem() && player.repeatMode != REPEAT_MODE_ONE) {
            // Last item with NO next: if auto-radio (infinite queue) is on, seed it NOW — early, while this song
            // still has time left — so a real crossfade INTO the first radio song is possible. A bare return here
            // is why the infinite queue used to continue with a hard cut. appendSeed() re-arms scheduleCrossfade()
            // once the items land, so the fade then targets the freshly-appended next song.
            if (!radioSeedInFlight && dataStore.get(AutoLoadMoreKey, true) &&
                player.currentMediaItem?.mediaId != null
            ) {
                startRadioSeamlessly()
            }
            return
        }

        val triggerTime = player.duration - crossfadeDuration.toLong()
        val delayMs = triggerTime - player.currentPosition
        if (delayMs <= 0) return

        val targetMediaId = player.currentMediaItem?.mediaId

        // Preload (build + buffer) the incoming player a few seconds BEFORE the fade so it's already
        // playing the instant the fade starts. This removes the occasional cut/gap on slow networks,
        // where the incoming player used to begin buffering only when the fade had already started.
        val preloadDelay = (delayMs - CROSSFADE_PRELOAD_LEAD_MS).coerceAtLeast(0L)
        crossfadePreloadJob = scope.launch {
            delay(preloadDelay)
            if (isActive && !isCrossfading && player.isPlaying &&
                player.currentMediaItem?.mediaId == targetMediaId
            ) {
                val targetIndex = if (player.repeatMode == REPEAT_MODE_ONE) {
                    player.currentMediaItemIndex
                } else {
                    player.nextMediaItemIndex
                }
                prepareSecondaryPlayer(targetIndex)
            }
        }

        crossfadeTriggerJob = scope.launch {
            delay(delayMs)
            if (isActive && player.isPlaying && player.currentMediaItem?.mediaId == targetMediaId && !sleepTimer.pauseWhenSongEnd) {
                startCrossfade()
            }
        }
    }

    private fun isNextItemGapless(): Boolean {
        val current = player.currentMediaItem?.mediaMetadata ?: return false
        val nextIndex = player.nextMediaItemIndex
        if (nextIndex == C.INDEX_UNSET) return false
        val next = player.getMediaItemAt(nextIndex).mediaMetadata
        return current.albumTitle != null && current.albumTitle == next.albumTitle
    }

    private fun startCrossfade() {
        if (isCrossfading) return

        
        
        // Live values — NOT runBlocking dataStore reads: two blocking disk reads here, right at the
        // crossfade trigger, stuttered the smooth transition. player.repeatMode/shuffleModeEnabled mirror
        // the persisted settings already.
        val savedRepeatMode = player.repeatMode
        val savedShuffleEnabled = player.shuffleModeEnabled

        
        val targetIndex = if (savedRepeatMode == REPEAT_MODE_ONE) {
            player.currentMediaItemIndex
        } else {
            player.nextMediaItemIndex
        }
        if (targetIndex == C.INDEX_UNSET) return

        // Reuse the player we preloaded (already buffering ahead) if present; otherwise build it now.
        if (secondaryPlayer == null) {
            prepareSecondaryPlayer(targetIndex)
        }
        val secPlayer = secondaryPlayer ?: return
        secPlayer.playWhenReady = true

        performCrossfadeSwap()


        if (savedShuffleEnabled) {
            val shufflePlaylistFirst = dataStore.get(ShufflePlaylistFirstKey, false)
            applyShuffleOrder(player.currentMediaItemIndex, player.mediaItemCount, shufflePlaylistFirst)
        }
    }

    /** Build the incoming player (full queue, seeked to [targetIndex], muted, buffering) WITHOUT swapping. */
    private fun prepareSecondaryPlayer(targetIndex: Int) {
        if (secondaryPlayer != null || isCrossfading) return
        if (targetIndex == C.INDEX_UNSET) return

        val sec = createExoPlayer(isSecondary = true)
        sec.addListener(secondaryPlayerListener)

        val items = mutableListOf<MediaItem>()
        for (i in 0 until player.mediaItemCount) {
            items.add(player.getMediaItemAt(i))
        }
        sec.setMediaItems(items)
        sec.seekTo(targetIndex, 0)
        sec.volume = 0f
        sec.repeatMode = player.repeatMode
        sec.shuffleModeEnabled = player.shuffleModeEnabled

        val incomingId = items.getOrNull(targetIndex)?.mediaId

        // FIX B: pre-level the incoming track BEFORE sec.prepare() primes its first buffers. The secondary
        // shares the NormalizationGainAudioProcessor.gain static, which still holds the OUTGOING track's
        // value — so without this the incoming track primes at the wrong (often louder) level and then
        // ramps when the async prime below lands ("enters loud then corrects"). After Fix A the incoming
        // format is usually already cached, so resolve it synchronously here and set the per-instance gain
        // up front. If it isn't cached yet, the async scope.launch below resolves it (existing fallback).
        // Resolve the incoming gain from the IN-MEMORY hint cache (populated by setupLoudnessEnhancer on every
        // track start + by the upcoming-track preload, Fix A) — NO disk read on this thread. prepareSecondaryPlayer
        // runs on Dispatchers.Main, where a runBlocking Room/DataStore read stutters the transition / risks ANR
        // (the comment in startCrossfade documents that exact regression). Cache miss → the async fallback below
        // resolves it off-main before the fade.
        var primedSyncGain = false
        if (incomingId != null && (normalizationEnabledHint || safeVolumeEnabledHint)) {
            loudnessHintCache[incomingId]?.let { loudnessDb ->
                val mult = normalizationMultiplier(loudnessDb, enabled = true)
                playerNormProcessors[sec]?.instanceGain = mult
                playerLimiterProcessors[sec]?.setInstanceMakeup(dbToLinear(loudnessMakeupDb(loudnessDb, enabled = true)), null)
                // Prime Safe Volume on the incoming player's live EQ processor so a loud track is attenuated
                // from the FIRST fade-in sample (else it swells in at full native level, then drops at swap).
                if (safeVolumeEnabledHint) playerEqProcessors[sec]?.applySafeVolume(true, mult)
                primedSyncGain = true
                Timber.tag(TAG).d("Crossfade: pre-leveled incoming $incomingId from cache (loudnessDb=$loudnessDb)")
            }
        }

        sec.playWhenReady = false // buffer ahead silently; startCrossfade flips this on at the fade
        sec.prepare()
        secondaryPlayer = sec

        // Prime the incoming player to ITS OWN track's normalization so the moment the fade starts it's
        // already at the right level (the shared companion statics still hold the OUTGOING track's values).
        // Fallback for the not-yet-cached case: only runs if the synchronous pre-level above didn't set the
        // gain (so it never overwrites an already-set instanceGain with a default).
        if (incomingId != null && !primedSyncGain) {
            scope.launch {
                val normalize = withContext(Dispatchers.IO) { dataStore.data.map { it[AudioNormalizationKey] ?: true }.first() }
                if (!normalize && !safeVolumeEnabledHint) return@launch
                val fmt = withContext(Dispatchers.IO) { database.format(incomingId).first() }
                val loudnessDb = effectiveLoudnessDb(fmt?.loudnessDb, fmt?.perceptualLoudnessDb, fmt?.measuredLoudnessDb)
                if (fmt?.loudnessDb != null || fmt?.perceptualLoudnessDb != null || fmt?.measuredLoudnessDb != null) {
                    loudnessHintCache[incomingId] = loudnessDb // warm the cache for the next crossfade into this track
                }
                withContext(Dispatchers.Main) {
                    // Apply to the incoming player whether it's still the secondary OR has already been swapped
                    // to current (the fast/fallback crossfade path swaps synchronously before this async prime
                    // resolves). `sec` is the same ExoPlayer object before and after the swap, so the map lookup
                    // by `sec` still resolves. The `isCrossfading` guard prevents re-setting the override AFTER
                    // cleanupCrossfade has cleared it (which would freeze the survivor's normalization).
                    if (secondaryPlayer === sec || (player === sec && isCrossfading)) {
                        val mult = normalizationMultiplier(loudnessDb, enabled = true)
                        playerNormProcessors[sec]?.instanceGain = mult
                        playerLimiterProcessors[sec]?.setInstanceMakeup(dbToLinear(loudnessMakeupDb(loudnessDb, enabled = true)), null)
                        if (safeVolumeEnabledHint) playerEqProcessors[sec]?.applySafeVolume(true, mult)
                    }
                }
            }
        }
    }

    private fun performCrossfadeSwap() {
        isCrossfading = true
        val nextPlayer = secondaryPlayer ?: return
        val currentPlayer = player

        fadingPlayer = currentPlayer
        // Pin the OUTGOING player to its current normalization (the companion statics still hold its
        // values right now) so when setupLoudnessEnhancer re-writes them for the incoming track, the
        // fading player keeps its own level instead of "pumping" to the new track's gain.
        playerNormProcessors[currentPlayer]?.instanceGain = NormalizationGainAudioProcessor.gain
        playerLimiterProcessors[currentPlayer]?.setInstanceMakeup(TruePeakLimiterAudioProcessor.loudnessMakeup, null)
        player = nextPlayer
        _playerFlow.value = player
        secondaryPlayer = null

        fadingPlayer?.removeListener(this)
        fadingPlayer?.removeListener(sleepTimer)

        // Stop the outgoing player from auto-advancing into the NEXT track as it fades out. It still
        // holds the full queue, so when the current song ends mid-fade it would start the next song —
        // which the incoming player is ALSO playing → "the next track plays twice at once" at the start
        // of the transition. Drop everything after its current item and disable repeat so it just ends.
        try {
            fadingPlayer?.let { fp ->
                fp.repeatMode = androidx.media3.common.Player.REPEAT_MODE_OFF
                val next = fp.currentMediaItemIndex + 1
                if (next in 1 until fp.mediaItemCount) fp.removeMediaItems(next, fp.mediaItemCount)
            }
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "crossfade: failed to cap fading player queue")
        }


        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (isCrossfading && fadingPlayer != null) {
                    if (isPlaying) {
                        fadingPlayer?.play()
                    } else {
                        fadingPlayer?.pause()
                    }
                } else {
                    player.removeListener(this)
                }
            }
        })

        nextPlayer.removeListener(secondaryPlayerListener)
        nextPlayer.addListener(this)
        nextPlayer.addListener(sleepTimer)

        sleepTimer.player = player

        try {
            (mediaSession as MediaSession).player = player
        } catch (e: Exception) {
            timber.log.Timber.e(e, "Failed to swap player in MediaSession")
        }

        crossfadeJob = scope.launch {
            val duration = crossfadeDuration.toLong()
            // Finer steps (~40 ms) so the volume ramp is smooth, not stair-stepped like the old 20 steps.
            val steps = (duration / 40L).toInt().coerceIn(24, 240)
            val stepTime = duration / steps
            val curve = try { dataStore.get(CrossfadeCurveKey, 1) } catch (e: Exception) { 1 }
            val startVolume = try { fadingPlayer?.volume ?: 1f } catch(e:Exception) { 1f }
            // Because LUFS Normalization is fixed and active, tracks play at roughly -14 LUFS,
            // leaving massive natural headroom. Thus, two tracks summing during an equal-power crossfade
            // will NEVER clip the Android mixer (they'll sum to ~-11 LUFS). We can safely remove the
            // old volume dip hack and keep the multiplier at 1.0f for a perfectly transparent blend.
            val xfHeadroom = 1f

            try {
                for (i in 0..steps) {
                    if (!isActive) break

                    while (!player.isPlaying && isActive) {
                        delay(100)
                    }

                    val progress = i / steps.toFloat()
                    val (fadeIn, fadeOut) = crossfadeGains(curve, progress)

                    try {
                        // Both players smoothly fade without needing to dynamically duck their headroom
                        player.volume = startVolume * fadeIn * xfHeadroom
                        fadingPlayer?.volume = startVolume * fadeOut * xfHeadroom
                    } catch (e: Exception) { break }

                    delay(stepTime)
                }
            } finally {
                // ALWAYS end the crossfade cleanly — even if it's cancelled (skip/stop) mid-fade, which
                // throws from delay() and would otherwise skip the restore and leave the surviving player
                // silent for the rest of the session. Restore it to the user's real volume + tear down.
                runCatching {
                    player.volume = when {
                        !::playerVolume.isInitialized -> startVolume
                        isMuted.value -> 0f
                        else -> playerVolume.value
                    }
                }
                runCatching { cleanupCrossfade() }
            }
        }
    }

    /**
     * Gain pair (incoming, outgoing) for crossfade progress [p] in 0..1, per the selected style.
     *  0 = Linear: straight amplitude ramp (1 - p); amplitude sum never exceeds 1.0.
     *  1 = Smooth/equal-power (default): sin/cos keep incoming^2 + outgoing^2 = 1 (constant power), so
     *      both tracks carry the SAME power through the blend — the natural, even crossfade.
     *  2 = Long S-curve: equal-power but eased timing (very gradual in/out).
     *  3 = Exponential (quick): each track dominates its half, snappier handover.
     */
    private fun crossfadeGains(curve: Int, p: Float): Pair<Float, Float> {
        return CrossfadeMath.getGains(curve, p)
    }


    private fun cleanupCrossfade() {
        // The crossfade is over: clear the surviving player's per-instance normalization overrides so it
        // resumes following the shared companion statics, and reset the de-dup guard so the next track
        // (re)normalizes normally via setupLoudnessEnhancer.
        playerNormProcessors[player]?.instanceGain = null
        playerLimiterProcessors[player]?.setInstanceMakeup(null, null)
        lastNormalizedId = null
        lastNormalizedHadLoudness = false
        fadingPlayer?.stop()
        fadingPlayer?.clearMediaItems()
        fadingPlayer?.let { 
            playerNormProcessors.remove(it)
            playerLimiterProcessors.remove(it)
            playerEqProcessors.remove(it)?.let { eq -> equalizerService.removeAudioProcessor(eq) }
        }
        fadingPlayer?.release()
        fadingPlayer = null
        isCrossfading = false
        sleepTimer.notifySongTransition()
    }

    companion object {
        const val ROOT = "root"
        const val SONG = "song"
        const val ARTIST = "artist"
        const val ALBUM = "album"
        const val PLAYLIST = "playlist"
        const val YOUTUBE_PLAYLIST = "youtube_playlist"
        const val SEARCH = "search"
        const val SHUFFLE_ACTION = "__shuffle__"

        const val CHANNEL_ID = "music_channel_01"
        const val NOTIFICATION_ID = 888
        const val ERROR_CODE_NO_STREAM = 1000001
        const val CHUNK_LENGTH = 512 * 1024L
        const val PERSISTENT_QUEUE_FILE = "persistent_queue.data"
        const val PERSISTENT_AUTOMIX_FILE = "persistent_automix.data"
        const val PERSISTENT_PLAYER_STATE_FILE = "persistent_player_state.data"
        const val MAX_CONSECUTIVE_ERR = 5
        const val MAX_RETRY_COUNT = 10
        // How early (ms before the fade) to build + buffer the incoming player so the crossfade has no gap.
        private const val CROSSFADE_PRELOAD_LEAD_MS = 12000L
        
        private const val MAX_GAIN_MB = 300 
        private const val MIN_GAIN_MB = -1500 

        private const val TAG = "MusicService"

        @Volatile
        var isRunning = false
            private set
    }

    private var preloadJob: kotlinx.coroutines.Job? = null

    private fun preloadUpcomingItems() {
        // Capture player state HERE on the player/callback thread (cheap, in-memory). The DataStore reads
        // (disk I/O via runBlocking) are moved INTO the coroutine below so onMediaItemTransition is never
        // blocked by a blocking disk read on the callback thread (jank / contributes to mid-song stalls).
        val currentIndex = player.currentMediaItemIndex
        if (currentIndex == androidx.media3.common.C.INDEX_UNSET) return
        // Cap at the PreloadNextSongLimit slider maximum (10) so `take(preloadLimit)` below can still honour
        // the user's configured value; the real limit is applied inside the coroutine.
        val lookahead = kotlin.math.min(10, player.mediaItemCount - currentIndex - 1)
        if (lookahead <= 0) return
        val upcomingAll = ArrayList<String>(lookahead)
        for (i in 1..lookahead) {
            upcomingAll.add(player.getMediaItemAt(currentIndex + i).mediaId)
        }

        preloadJob?.cancel()
        preloadJob = scope.launch(kotlinx.coroutines.Dispatchers.IO) {
            if (!dataStore.get(iad1tya.echo.music.constants.PreloadNextSongEnabledKey, true)) return@launch
            // Battery saver: skip the upcoming-track network preload (up to N parallel stream-URL + loudness +
            // lyrics fetches per transition) when the user has Battery Saver on. Playback is unaffected — the
            // next track just resolves on demand instead of ahead of time. Respects the OS power-save intent.
            if ((getSystemService(POWER_SERVICE) as? android.os.PowerManager)?.isPowerSaveMode == true) {
                Timber.tag(TAG).d("Preload skipped: battery saver (power save mode) is on")
                return@launch
            }
            val preloadLimit = dataStore.get(iad1tya.echo.music.constants.PreloadNextSongLimitKey, 1)
            val preloadLyrics = dataStore.get(iad1tya.echo.music.constants.PreloadLyricsEnabledKey, true)
            val upcomingMediaIds = upcomingAll.take(preloadLimit)
            for (mediaId in upcomingMediaIds) {

                if (!mediaId.isLocalMediaId() && !songUrlCache.containsKey(mediaId)) {
                    Timber.tag(TAG).d("Preloading stream for $mediaId")
                    kotlin.runCatching {
                        val dbSong = database.song(mediaId).firstOrNull()
                        val knownArtist = dbSong?.artists?.joinToString(separator = ", ") { artist -> artist.name }?.replace(" - Topic", "")
                        
                        val playbackData = iad1tya.echo.music.utils.YTPlayerUtils.playerResponseForPlayback(
                            videoId = mediaId,
                            audioQuality = audioQuality,
                            connectivityManager = connectivityManager,
                            context = this@MusicService,
                            knownArtist = knownArtist,
                            knownTitle = dbSong?.song?.title,
                            knownDurationMs = dbSong?.song?.duration?.let { if (it > 0) it * 1000L else null }
                        )

                        playbackData.getOrNull()?.let { data ->
                            songUrlCache[mediaId] = Pair(data.streamUrl, System.currentTimeMillis() + 1000 * 60 * 60)
                            Timber.tag(TAG).d("Preloaded stream for $mediaId")

                            // FIX A: cache the loudness (FormatEntity) for the UPCOMING track NOW, so when it
                            // transitions to playing, setupLoudnessEnhancer finds a non-null format and primes the
                            // correct gain at second 0 — no audible volume swell. Mirrors the resolver's
                            // FormatEntity construction exactly. Preserve any existing row's loudness: only fill
                            // when missing, never overwrite a known loudness with null.
                            kotlin.runCatching {
                                val existing = database.format(mediaId).firstOrNull()
                                val loudnessDb = data.audioConfig?.loudnessDb ?: existing?.loudnessDb
                                val perceptualLoudnessDb = data.audioConfig?.perceptualLoudnessDb ?: existing?.perceptualLoudnessDb
                                val measuredLoudnessDb = existing?.measuredLoudnessDb
                                // Mirror into the in-memory hint cache so a crossfade INTO this track can pre-level it
                                // synchronously (Fix B) with no main-thread disk read.
                                if (loudnessDb != null || perceptualLoudnessDb != null || measuredLoudnessDb != null) {
                                    loudnessHintCache[mediaId] = effectiveLoudnessDb(loudnessDb, perceptualLoudnessDb, measuredLoudnessDb)
                                }
                                // Persist the row only when we don't already have loudness cached (nothing to gain otherwise).
                                if (existing?.loudnessDb == null && existing?.perceptualLoudnessDb == null) {
                                    val format = data.format
                                    database.query {
                                        upsert(
                                            iad1tya.echo.music.db.entities.FormatEntity(
                                                id = mediaId,
                                                itag = format.itag,
                                                mimeType = format.mimeType.split(";")[0],
                                                codecs = format.mimeType.split("codecs=")[1].removeSurrounding("\""),
                                                bitrate = format.bitrate,
                                                sampleRate = format.audioSampleRate,
                                                contentLength = format.contentLength ?: 0L,
                                                loudnessDb = loudnessDb,
                                                perceptualLoudnessDb = perceptualLoudnessDb,
                                                measuredLoudnessDb = measuredLoudnessDb,
                                                playbackUrl = data.playbackTracking?.videostatsPlaybackUrl?.baseUrl
                                            )
                                        )
                                    }
                                    Timber.tag(TAG).d("Preloaded format/loudness for $mediaId (loudnessDb=$loudnessDb, perceptualLoudnessDb=$perceptualLoudnessDb)")
                                }
                            }.onFailure { e ->
                                Timber.tag(TAG).w(e, "Preload: failed to cache format/loudness for $mediaId")
                            }
                        }
                    }
                }

                if (preloadLyrics) {
                    val dbLyrics = database.lyrics(mediaId).firstOrNull()
                    if (dbLyrics == null) {
                        Timber.tag(TAG).d("Preloading lyrics for $mediaId")
                        val dbSong = database.song(mediaId).firstOrNull()
                        if (dbSong != null) {
                            kotlin.runCatching {
                                val metadata = iad1tya.echo.music.models.MediaMetadata(
                                    id = dbSong.song.id,
                                    title = dbSong.song.title,
                                    artists = dbSong.artists.map { artist -> iad1tya.echo.music.models.MediaMetadata.Artist(artist.id, artist.name) },
                                    duration = dbSong.song.duration,
                                    thumbnailUrl = dbSong.song.thumbnailUrl
                                )
                                val lyricsResult = lyricsHelper.getLyrics(metadata)
                                database.query {
                                    upsert(iad1tya.echo.music.db.entities.LyricsEntity(id = mediaId, lyrics = lyricsResult.lyrics))
                                }
                                Timber.tag(TAG).d("Preloaded lyrics for $mediaId")
                            }
                        }
                    }
                }
            }
        }
    }
}
