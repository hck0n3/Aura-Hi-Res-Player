

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
import kotlinx.coroutines.SupervisorJob
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
    // Waits (bounded) for the incoming player to reach STATE_READY at position 0 before the fade actually
    // swaps, so a LATE-ARMED / not-yet-buffered secondary never fades in half-buffered (clipped first ms).
    private var crossfadeReadyJob: Job? = null

    private val secondaryPlayerListener = object : Player.Listener {
        override fun onPlayerError(error: PlaybackException) {
            Timber.tag(TAG).e(error, "Secondary player error")
            secondaryPlayer?.stop()
            secondaryPlayer?.clearMediaItems()
            // Full teardown (mirror cleanupCrossfade/releasePlayer): also drop the EQ processor and release()
            // the native player, or every secondary-player error leaks an ExoPlayer and permanently grows
            // EqualizerService's processor list.
            secondaryPlayer?.let {
                playerNormProcessors.remove(it)
                playerLimiterProcessors.remove(it)
                playerEqProcessors.remove(it)?.let { eq -> equalizerService.removeAudioProcessor(eq) }
                it.release()
            }
            secondaryPlayer = null
        }
    }

    // SupervisorJob: an uncaught exception in any child (retry, crossfade, widget, collectors) must NOT
    // cancel the whole service scope. Matches the app's applicationScope convention.
    private var scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private val binder = MusicBinder()

    inner class MusicBinder : Binder() {
        val service: MusicService
            get() = this@MusicService
    }

    private lateinit var connectivityManager: ConnectivityManager
    lateinit var connectivityObserver: NetworkConnectivityObserver
    val waitingForNetworkConnection = MutableStateFlow(false)
    private val isNetworkConnected = MutableStateFlow(false)
    // True only while playback is PAUSED specifically because the network retry escalator hit its dead-end
    // (MAX_RETRY_COUNT). Gates the bounded auto-resume in triggerRetry() so we ONLY force play() for a
    // network-caused pause. A genuine user/external pause clears it (see onPlayWhenReadyChanged), so a manual
    // pause is never auto-resumed. Cleared on successful playback (STATE_READY) and after resuming.
    private var pausedByNetwork = false
    // Wall-clock time the dead-end pause was armed. triggerRetry() only auto-resumes within
    // STALE_RESUME_WINDOW_MS, so a reconnection hours later re-buffers but never surprise-plays.
    private var pausedByNetworkAtMs = 0L
    // Set true by stopOnError() immediately before its player.pause(), so the resulting onPlayWhenReadyChanged
    // can tell OUR error-pause (keep pausedByNetwork) from a real user/external pause (clear pausedByNetwork).
    private var expectingOwnStopPause = false
    // Single-shot, cancellable safety re-check armed at the dead-end: covers a STABLE network that never fires
    // a new connectivity event, so we don't wait forever paused. Not a loop; cancelled in triggerRetry()/READY.
    private var deadEndRecheckJob: Job? = null

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
    // Read-only OBSERVATION flag only. Mirrors the plain [isCrossfading] boolean so the UI can observe a
    // crossfade swap; it does NOT touch the crossfade timing, duration, curve, equal-power gains, gapless
    // logic or preload. Set true at the existing swap start (performCrossfadeSwap) and false at the existing
    // swap end (cleanupCrossfade) — nothing else in the crossfade changes.
    private val _isCrossfading = MutableStateFlow(false)
    /** Read-only view for PlayerConnection: a spurious null-item transition during a crossfade swap must not
     *  blank the now-playing UI, but outside a crossfade a null transition is real and should update. */
    val crossfadingNow: Boolean get() = isCrossfading
    /** StateFlow of the crossfade-swap state, surfaced to the UI as PlayerConnection.isCrossfading. True only
     *  while a crossfade swap is actively in progress (performCrossfadeSwap → cleanupCrossfade). */
    val isCrossfadingFlow: kotlinx.coroutines.flow.StateFlow<Boolean> = _isCrossfading.asStateFlow()
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

    // P33 — the player-thread callbacks onMediaItemTransition/onPlaybackStatsReady used to call dataStore.get(),
    // which is a runBlocking disk-backed flow read, several times per track transition ON THE MAIN/APPLICATION
    // (ExoPlayer) thread — a blocking-I/O-on-main anti-pattern (jank risk). Mirror those prefs into memory via the
    // single collector in onCreate (same pattern as normalizationEnabledHint/audioOffloadHint) and read the fields
    // in the hot paths instead. Initial values equal the DataStore defaults, so behaviour is unchanged.
    @Volatile private var autoLoadMoreHint: Boolean = true
    @Volatile private var disableLoadMoreWhenRepeatAllHint: Boolean = false
    @Volatile private var keepGenreLaneHint: Boolean = true
    @Volatile private var persistentQueueHint: Boolean = true
    @Volatile private var historyDurationMsHint: Float = 30000f
    @Volatile private var pauseListenHistoryHint: Boolean = false
    // High-Performance Mode master switch, mirrored for the player-thread hot paths (scheduleCrossfade) so
    // they read a @Volatile field instead of a blocking DataStore read on the transition callback thread.
    @Volatile private var highPerformanceModeHint: Boolean = false

    // SponsorBlock: skip non-music segments (opt-in). Manager holds the current track's segments; the watcher
    // job polls position once a second while enabled and seeks past any segment the playhead enters.
    private val sponsorBlock = iad1tya.echo.music.playback.sponsorblock.SponsorBlockManager()
    @Volatile private var sponsorBlockEnabled: Boolean = false
    private var sponsorBlockJob: kotlinx.coroutines.Job? = null
    // The track id whose MEASURED loudness we've already committed to the gains + DB (so the one-shot
    // measurement-driven re-level fires at most ONCE per song, never re-levels twice). Null = none yet.
    @Volatile private var measuredAppliedForId: String? = null

    private var discordRpc: DiscordRPC? = null
    private var lastPlaybackSpeed = 1.0f
    private var discordUpdateJob: kotlinx.coroutines.Job? = null

    private var scrobbleManager: ScrobbleManager? = null

    val automixItems = MutableStateFlow<List<MediaItem>>(emptyList())

    // ---- Autoplay suggestion chips (YT Music queue-footer parity) ----
    // Chips describing WHERE the infinite autoplay/radio can steer next (default "related to the seed",
    // plus artist radios and mixes from the seed's related page). Refreshed at most ONCE per seed
    // (autoplayChipsSeedId cache) right after a radio seed lands — never on a timer, so this adds at most
    // one bounded network call per seed change. Chips only steer WHICH endpoint feeds the autoplay; the
    // appended items always flow through orderedByTaste(), so the relatedness-order invariant holds.
    private val _autoplayChips = MutableStateFlow<List<AutoplayChip>>(emptyList())
    val autoplayChips: kotlinx.coroutines.flow.StateFlow<List<AutoplayChip>> = _autoplayChips.asStateFlow()
    private val _autoplaySelectedChip = MutableStateFlow<AutoplayChip?>(null)
    val autoplaySelectedChip: kotlinx.coroutines.flow.StateFlow<AutoplayChip?> = _autoplaySelectedChip.asStateFlow()
    // The seed the current chip set was built from — the once-per-seed network bound for the chip refresh.
    @Volatile private var autoplayChipsSeedId: String? = null

    // Cached on-device taste model (see AffinityEngine), used to order what plays next (autoplay/radio)
    // by your taste and to drop "No me gusta". Rebuilt at most every few minutes.
    @Volatile private var cachedTaste: iad1tya.echo.music.reco.TasteProfile? = null
    @Volatile private var cachedTasteAt: Long = 0L

    // Cached co-relatedness counts (see SongGraphCache): candidateId -> how many of the user's liked-song
    // anchors YouTube says this candidate is related to. Lets the radio prefer songs co-related to MULTIPLE
    // liked songs (poor-man's collaborative filter). Rebuilt at most every few minutes (same TTL as
    // [cachedTaste]) — bounded background work, NEVER per-song. Empty graph → empty map → zero behaviour change.
    @Volatile private var cachedCoRel: Map<String, Int>? = null
    @Volatile private var cachedCoRelAt: Long = 0L

    // Bounded, cached set of song ids the user has RECENTLY PLAYED (from the on-device event history), so the
    // infinite radio doesn't re-append songs already heard days/weeks ago — the last ~60 in-session transitions
    // in [recentRadioIds] can't see that far back. One DB read every few minutes; O(1) membership at append
    // time (no per-append DB hit, no heat). Refreshed on the same ~5-min TTL as [cachedTaste].
    @Volatile private var cachedPlayedIds: Set<String>? = null
    @Volatile private var cachedPlayedIdsAt: Long = 0L

    // Active "mood" (a Home mood chip the user tapped). When set, the infinite radio SEEDS from the mood
    // (YouTube.home(params = moodParams) sections' songs) instead of the last song — still passed through
    // orderedByTaste() so the relatedness/taste ordering invariant is preserved. Null → last-song seeding
    // (exactly today's behavior). Set via setActiveMood(); read on the radio-seed path only.
    @Volatile private var activeMoodParams: String? = null
    @Volatile private var activeMoodTitle: String? = null

    // Phase A #1/#6 — snapshot of the FINITE collection the user started from (album/playlist/multi-song list),
    // used to multi-seed the infinite radio from the collection's CONTENT (its artist/genre mix) instead of only
    // the last song. Empty for a directly-started radio (YouTubeQueue) or a single track → falls back to last-song
    // seeding (unchanged). Reassigned on every playQueue, so a fresh finite queue overwrites any prior pool.
    @Volatile private var radioSeedPool: List<iad1tya.echo.music.models.MediaMetadata> = emptyList()


    private var originalQueueSize: Int = 0
    // B5 — anti-repeat shuffle memory: media IDs already played in the current shuffle session. While
    // shuffling, not-yet-played songs are ordered ahead of these, so nothing repeats until the whole pool is
    // exhausted (then it auto-resets for a new cycle). Reset whenever shuffle is (re)enabled.
    private val shufflePlayedIds = LinkedHashSet<String>()
    /** Recently-played media ids (bounded, most-recent last) so autoplay/radio don't resurface a song you
     *  JUST heard. A soft demotion (not a hard drop) — see [orderedByTaste] — so it can never dead-end the queue. */
    private val recentRadioIds = LinkedHashSet<String>()
    /** NO-REPEAT — SESSION-WIDE played/queued media ids. Every song we've PLAYED or APPENDED to the infinite
     *  queue this session lands here (large bounded LRU, ~4000, thread-safe). Unlike [recentRadioIds] (last ~60)
     *  it spans the WHOLE session, so the radio pagination (Path A) and the re-seed / [orderedByTaste] paths can
     *  HARD-DROP anything already heard/queued — the infinite queue never repeats a song. */
    private val sessionPlayedIds: MutableSet<String> = java.util.Collections.synchronizedSet(
        java.util.Collections.newSetFromMap(
            object : java.util.LinkedHashMap<String, Boolean>(4096, 0.75f, false) {
                override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Boolean>?): Boolean = size > 4000
            }
        )
    )
    private fun rememberRecentRadioId(id: String?) {
        if (id.isNullOrBlank()) return
        sessionPlayedIds.add(id) // NO-REPEAT: session-wide memory of everything actually played
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
    // Idempotent watchdog armed by STATE_ENDED when a head-start (B3) seed was ALREADY in flight (so we skipped
    // launching a second one): if that in-flight seed settles with nothing appended, it clears the flags and the
    // player would dead-end. Once the seed settles, this re-checks and kicks a fresh seed if we're still stopped
    // at a true end-of-queue. Single-shot, cancelled on the next STATE_READY, so it can never stack or loop.
    private var radioResumeWatchdogJob: Job? = null

    private var consecutivePlaybackErr = 0
    private var retryJob: Job? = null
    private var retryCount = 0
    private var silenceSkipJob: Job? = null

    // ConcurrentHashMap: structurally mutated from the ExoPlayer loader thread (ResolvingDataSource resolver),
    // the Main thread (quality collector / refetchCurrentInOpus) and an IO coroutine (preloadUpcomingItems).
    private val songUrlCache = java.util.concurrent.ConcurrentHashMap<String, Pair<String, Long>>()

    // FIX A (#27 phantom playback): true after a PERSISTENT-QUEUE RESTORE left the player IDLE with the
    // restored items + saved seek set but deliberately NOT prepared, so an external media-button PLAY (BT /
    // headset / car AVRCP / widget) at process start can't cold-start the queue. Cleared the moment the
    // player is genuinely prepared (leaves STATE_IDLE) — see onPlaybackStateChanged. Only the RESTORE path
    // defers prepare; a normal user-initiated playQueue still prepares+plays exactly as before.
    // #27 PHANTOM PLAYBACK: true while a queue was RESTORED this process and the user hasn't genuinely engaged
    // yet. While true, MediaLibrarySessionCallback.onPlayerCommandRequest VETOES external COMMAND_PLAY_PAUSE
    // (BT/AVRCP/watch/Android-Auto/notification), so a cold-restored, never-touched queue (restored prepared-
    // but-paused → media3's Util.handlePlayButtonAction would prepare()+play() it on any external PLAY) can't
    // cold-start on its own. Direct in-app player calls (PlayerConnection → service.player) bypass the session
    // callback entirely and are UNAFFECTED. Read by the callback; only mutated inside MusicService.
    @Volatile
    internal var awaitingFirstUserPlay: Boolean = false
        private set
    // #27: once the user has foregrounded the app this process, a LATE async restore must NOT re-arm the veto
    // (that race could leave the notification/BT play inert while the app is open). Monotonic; never reset.
    @Volatile private var userHasForegroundedThisProcess = false

    // FIX B1 (#28.1): LRU cap for the persisted mirror of songUrlCache. The blob is a tiny JSON map written
    // to DataStore on a resolve and read once on cold start — no polling, negligible battery cost.
    private val SONG_URL_CACHE_PERSIST_MAX = 300

    /**
     * FIX B1: authoritative absolute expiry (epoch millis) for a resolved stream URL. Prefers the googlevideo
     * `expire=` query param (unix SECONDS); falls back to [storedExpireMillis] (already an absolute-millis
     * value computed at resolve time — e.g. for Saavn/Qobuz URLs that carry no expire param), and to a
     * conservative now+5h if both are missing/already past. Never throws.
     */
    private fun streamUrlExpiryMillis(url: String, storedExpireMillis: Long): Long {
        val fromUrl = runCatching {
            Regex("[?&]expire=(\\d+)").find(url)?.groupValues?.getOrNull(1)?.toLongOrNull()?.let { it * 1000L }
        }.getOrNull()
        val now = System.currentTimeMillis()
        return when {
            fromUrl != null && fromUrl > now -> fromUrl
            storedExpireMillis > now -> storedExpireMillis
            else -> now + 5L * 60 * 60 * 1000
        }
    }

    /**
     * FIX B1: persist the (non-expired, LRU-bounded) songUrlCache to DataStore so a resolved stream URL
     * survives a process restart / app update — the first play/resume after an update then serves the cached
     * URL instead of re-running the slow resolver. Best-effort, off the main thread; never throws.
     */
    private fun persistSongUrlCache() {
        scope.launch(Dispatchers.IO) {
            runCatching {
                val now = System.currentTimeMillis()
                val entries = songUrlCache.entries
                    .filter { it.value.second > now }
                    .sortedByDescending { it.value.second } // freshest-expiring first ≈ most-recent (LRU proxy)
                    .take(SONG_URL_CACHE_PERSIST_MAX)
                val json = org.json.JSONObject()
                for (e in entries) {
                    json.put(
                        e.key,
                        org.json.JSONObject()
                            .put("u", e.value.first)
                            .put("e", streamUrlExpiryMillis(e.value.first, e.value.second))
                    )
                }
                dataStore.edit { it[iad1tya.echo.music.constants.SongUrlCacheBlobKey] = json.toString() }
            }.onFailure { Timber.tag(TAG).d(it, "persistSongUrlCache failed (non-fatal)") }
        }
    }

    /**
     * FIX B1: on cold start, load the persisted songUrlCache. Only NON-expired entries (with a 60s safety
     * margin) are restored, so we never serve a stale URL; putIfAbsent never clobbers a fresher live resolve.
     * Best-effort, off the main thread; never throws.
     */
    private fun loadPersistedSongUrlCache() {
        scope.launch(Dispatchers.IO) {
            runCatching {
                val blob = dataStore.data.first()[iad1tya.echo.music.constants.SongUrlCacheBlobKey]
                    ?.takeIf { it.isNotBlank() } ?: return@runCatching
                val json = org.json.JSONObject(blob)
                val safeNow = System.currentTimeMillis() + 60_000L
                val keys = json.keys()
                var restored = 0
                while (keys.hasNext()) {
                    val k = keys.next()
                    val o = json.optJSONObject(k) ?: continue
                    val u = o.optString("u", "")
                    val e = o.optLong("e", 0L)
                    if (u.isNotEmpty() && e > safeNow) {
                        songUrlCache.putIfAbsent(k, u to e)
                        restored++
                    }
                }
                Timber.tag(TAG).d("Restored $restored persisted stream URL(s) from DataStore")
            }.onFailure { Timber.tag(TAG).d(it, "loadPersistedSongUrlCache failed (non-fatal)") }
        }
    }

    // synchronizedSet: same multi-thread mutation profile as songUrlCache (loader thread + Main + IO).
    private val bypassCacheForQualityChange = java.util.Collections.synchronizedSet(mutableSetOf<String>())

    // Set by refetchCurrentInOpus(): pins THIS track to the Opus (WebM/Opus) audio format on its next
    // (re)fetch, overriding both the global AudioQuality and the currently-playing "locked quality" (which
    // otherwise pins to the DB format's container). Cleared once a different track becomes current.
    @Volatile private var forceOpusForMediaId: String? = null

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

    // ConcurrentHashMap: read/written from both Main (applyVideoToCurrent, onPlayerError) and Dispatchers.IO
    // (prebuildNextVideoItem resolves + writes before its withContext(Main)). Those windows overlap on a real
    // cross-thread data race, so mirror the songUrlCache/loudnessHintCache/videoModeItems convention in this file.
    private val videoUrlCache = java.util.concurrent.ConcurrentHashMap<String, Pair<String, Long>>()

    // Best VIDEO-ONLY height to request for video mode. On Android TV (big screen, detected server-side via
    // UiModeManager in DeviceForm.isTelevision) we derive the target height from a LIVE bandwidth estimate so
    // video STARTS at a sustainable resolution instead of always demanding 1080p — the root cause of TV video
    // stalling like the network is failing (a single fixed-resolution ProgressiveMediaSource can't drop). The
    // chosen video-only stream is MERGED with a separate audio track (see createMediaSourceFactory). Phones/
    // tablets get null → YTPlayerUtils keeps its existing metered-aware cap (720p WiFi / 360p data), unchanged.
    private val videoModeMaxHeight: Int?
        get() = if (iad1tya.echo.music.utils.DeviceForm.isTelevision(this)) bandwidthAwareVideoHeight() else null

    // Map the current downstream bandwidth estimate to a TV video-only target height. Capped at the TV 1080
    // ceiling. NOTE: DefaultBandwidthMeter.bitrateEstimate never actually returns 0 — before any real transfer
    // it hands back a synthetic country/network INITIAL estimate (~4-8 Mbps), so a cold TV must not read that
    // as "fast enough for 1080". The 1080 gate is therefore raised to 8 Mbps so only a genuinely strong,
    // measured estimate yields 1080; a cold synthetic estimate now yields <=720 (safe). The tier's maxVideoDim
    // is aligned in createExoPlayer to allow up to 1080 (1920 wide) on TV so the chosen track is never silently
    // rejected. The existing 720p (null) fallback in the resolve paths still covers a failed selection.
    private fun bandwidthAwareVideoHeight(): Int {
        val estimate = runCatching {
            androidx.media3.exoplayer.upstream.DefaultBandwidthMeter.getSingletonInstance(this).bitrateEstimate
        }.getOrDefault(0L)
        return when {
            estimate >= 8_000_000L -> 1080       // genuinely strong measured link only
            estimate >= 3_500_000L -> 720        // cold synthetic estimate (~4-8 Mbps) lands here → <=720
            estimate >= 1_500_000L -> 480
            else -> 360
        }.coerceAtMost(1080)                     // never exceed the TV 1080 request
    }

    private var userHasUsedVideo: Boolean
        get() = playbackState.userHasUsedVideo
        set(value) { playbackState.userHasUsedVideo = value }

    // PLAYER-EXPANDED SIGNAL — mirrored from the UI (PlayerConnection.setPlayerSheetExpanded), same
    // pattern as userHasUsedVideo. Used ONLY to gate speculative, user-visible-moment work (the video
    // connection warm-up below); never read by any playback/audio path.
    private val playerSheetExpanded: Boolean
        get() = playbackState.playerSheetExpanded

    /** UI → service: the full-screen player sheet was expanded/collapsed. Expanding is the natural
     *  "the user may toggle video next" moment, so it also kicks the bounded connection warm-up. */
    fun setPlayerSheetExpanded(expanded: Boolean) {
        playbackState.playerSheetExpanded = expanded
        if (expanded) {
            maybeWarmVideoConnection()
            // INSTANT VIDEO SWAP: expanding is the "user may toggle video next" moment — attempt the
            // pre-prepare (all hard gates re-checked inside; no-op when anything fails).
            scheduleInstantVideoPrepare()
        } else {
            // Sheet collapsed → the speculative player has no plausible toggle anymore; release it.
            teardownInstantVideoSwap("player sheet collapsed")
        }
    }

    /**
     * Session cap for SPECULATIVE video-URL prefetches launched BEFORE the user has ever toggled video
     * ([userHasUsedVideo] == false). Without a bound, capable devices would resolve on EVERY video-song
     * transition — exactly the per-track-change resolving that once hammered YouTube, rate-limited the app
     * and stalled normal AUDIO (see the onMediaItemTransition prebuild note). The first toggle is usually
     * early in a session, so a cap of 3 still makes it instant for real video users while keeping
     * audio-only listeners at <=3 extra resolves per session (in-memory, resets per process).
     */
    private var preFirstUseVideoPrefetches = 0

    private val _mixActive get() = playbackState.mixActive
    val mixActive: kotlinx.coroutines.flow.StateFlow<Boolean> get() = playbackState.mixActive

    private val _videoMode get() = playbackState.videoMode
    val videoMode: kotlinx.coroutines.flow.StateFlow<Boolean> get() = playbackState.videoMode

    private val _videoUrl get() = playbackState.videoUrl
    val videoUrl: kotlinx.coroutines.flow.StateFlow<String?> get() = playbackState.videoUrl.asStateFlow()

    private val preloadedVideoOriginalUris = mutableMapOf<String, String>()

    // Multi-item video tracking (generalizes the single videoModeMediaId). Every mediaId here has its player
    // MediaItem URI currently set to a VIDEO stream — the playing video track AND any UPCOMING item that was
    // pre-built for a seamless auto-advance (see prebuildNextVideoItem). createMediaSource is authoritative
    // off this map: any id present → it builds the MergingMediaSource (video-only + merged audio), so an item
    // can become a video source BEFORE it is current and the transition needs no swap on the running track.
    // ConcurrentHashMap because createMediaSource may be invoked off the main thread.
    private data class VideoTrackState(
        val videoUrl: String,
        val originalAudioUri: String?,
        val isMuxedPodcast: Boolean,
    )
    private val videoModeItems = java.util.concurrent.ConcurrentHashMap<String, VideoTrackState>()
    // Ids with a video-URL resolve currently in flight (dedupe; cleared in a finally / on exit).
    private val prebuildingIds = java.util.Collections.synchronizedSet(mutableSetOf<String>())

    // ---- INSTANT VIDEO SWAP (pre-prepared dual-player publish; kill switch in companion) ----
    // While the expanded player shows a video song in AUDIO mode (and every hard gate passes — see
    // maybePrepareInstantVideoSwap), a SECOND ExoPlayer is pre-prepared with the CURRENT track's merged
    // video+audio source, muted + paused. toggleVideoMode then PUBLISHES it (exactly like
    // performCrossfadeSwap publishes the crossfade secondary) instead of rebuilding the running item in
    // place — audio never halts. On ANY doubt (not READY, live position outside its buffered window, a
    // crossfade secondary exists, any exception) the pre-player is released and the EXISTING swapToVideo
    // path runs byte-identically. Max 2 ExoPlayers ever: prepareSecondaryPlayer tears this one down first,
    // and pre-prepare is skipped while any crossfade secondary/fading player exists.
    private var instantVideoPlayer: ExoPlayer? = null
    private var instantVideoPlayerId: String? = null
    private var instantVideoPlayerUrl: String? = null
    // Position the pre-player was prepared (and started buffering) at. Its playable window is roughly
    // [this, bufferedPosition]; the swap-time gate falls back to the normal path outside it.
    private var instantVideoPreparedAtPosMs = 0L
    private var instantVideoPrepareJob: Job? = null
    // Pre-prepare registrations, SEPARATE from videoModeItems on purpose: registering the current id in
    // videoModeItems while video mode is OFF would make any audio-path rebuild of the current item come out
    // as a (broken) video source. createMediaSource consults this map ONLY when the item's URI equals the
    // registered video URL — true only for the pre-player's own item, never for the main player's audio item.
    private val instantSwapItems = java.util.concurrent.ConcurrentHashMap<String, VideoTrackState>()
    private val instantVideoPlayerListener = object : Player.Listener {
        override fun onPlayerError(error: PlaybackException) {
            // Speculative player only — release it; the toggle simply falls back to the normal swap path.
            Timber.tag(TAG).w(error, "Instant-video pre-player error")
            releaseInstantVideoPlayer("pre-player error")
        }
    }

    // ---- DEBUG-ONLY video-swap latency instrumentation (release builds: dead fields, zero work) ----
    // Timestamps the audio→video toggle pipeline so the real on-device split (resolve vs network vs
    // buffer-fill vs decoder-init vs first frame) can be measured before/after optimizations:
    //   T0 toggle → swapToVideo → onLoadStarted/onLoadCompleted → decoder init → READY → first frame.
    // Armed in toggleVideoMode, disarmed at onRenderedFirstFrame. Pure Timber logging; NEVER touches
    // playback, and the AnalyticsListener is only registered on debug builds (see createExoPlayer).
    @Volatile private var videoSwapT0 = 0L
    @Volatile private var videoSwapLoadStartLogged = false
    @Volatile private var videoSwapLoadCompleteLogged = false
    @Volatile private var videoSwapReadyLogged = false

    private fun videoSwapMark(stage: String) {
        if (!iad1tya.echo.music.BuildConfig.DEBUG) return
        val t0 = videoSwapT0
        if (t0 == 0L) return
        Timber.tag("VideoSwapPerf").d(
            "%s +%d ms", stage, android.os.SystemClock.elapsedRealtime() - t0
        )
    }

    /** Arm a new measurement window at the toggle (debug builds only; no-op otherwise). */
    private fun videoSwapMeasureStart() {
        if (!iad1tya.echo.music.BuildConfig.DEBUG) return
        videoSwapT0 = android.os.SystemClock.elapsedRealtime()
        videoSwapLoadStartLogged = false
        videoSwapLoadCompleteLogged = false
        videoSwapReadyLogged = false
        Timber.tag("VideoSwapPerf").d("T0 toggleVideoMode")
    }

    private val videoSwapDebugListener = object : AnalyticsListener {
        override fun onLoadStarted(
            eventTime: AnalyticsListener.EventTime,
            loadEventInfo: androidx.media3.exoplayer.source.LoadEventInfo,
            mediaLoadData: androidx.media3.exoplayer.source.MediaLoadData,
            retryCount: Int,
        ) {
            if (videoSwapT0 != 0L && !videoSwapLoadStartLogged) {
                videoSwapLoadStartLogged = true
                videoSwapMark("onLoadStarted(first, trackType=${mediaLoadData.trackType})")
            }
        }

        override fun onLoadCompleted(
            eventTime: AnalyticsListener.EventTime,
            loadEventInfo: androidx.media3.exoplayer.source.LoadEventInfo,
            mediaLoadData: androidx.media3.exoplayer.source.MediaLoadData,
        ) {
            if (videoSwapT0 != 0L && !videoSwapLoadCompleteLogged) {
                videoSwapLoadCompleteLogged = true
                videoSwapMark(
                    "onLoadCompleted(first, ${loadEventInfo.bytesLoaded} B in ${loadEventInfo.loadDurationMs} ms)"
                )
            }
        }

        override fun onVideoDecoderInitialized(
            eventTime: AnalyticsListener.EventTime,
            decoderName: String,
            initializedTimestampMs: Long,
            initializationDurationMs: Long,
        ) {
            videoSwapMark("videoDecoderInitialized($decoderName, ${initializationDurationMs} ms)")
        }

        override fun onPlaybackStateChanged(eventTime: AnalyticsListener.EventTime, state: Int) {
            if (state == Player.STATE_READY && videoSwapT0 != 0L && !videoSwapReadyLogged) {
                videoSwapReadyLogged = true
                videoSwapMark("STATE_READY")
            }
        }

        override fun onRenderedFirstFrame(
            eventTime: AnalyticsListener.EventTime,
            output: Any,
            renderTimeMs: Long,
        ) {
            videoSwapMark("renderedFirstFrame — measurement end")
            videoSwapT0 = 0L
        }
    }


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
                    // INSTANT VIDEO SWAP: screen off = invisible → no speculative player may keep
                    // buffering video bytes (heat/battery rule). No-op when nothing is prepared.
                    teardownInstantVideoSwap("screen off")
                    if (!player.isPlaying) {
                        scope.launch(Dispatchers.IO) {
                            discordRpc?.closeRPC()
                        }
                    }
                }
                Intent.ACTION_SCREEN_ON -> {
                    // Re-attempt the instant-swap pre-prepare (fully re-gated inside: only if the player
                    // sheet is still expanded over a video song in audio mode, unmetered, capable, etc).
                    scheduleInstantVideoPrepare(INSTANT_VIDEO_PREPARE_DELAY_MS)
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

        // FIX B1 (#28.1): rehydrate the in-memory stream-URL cache from DataStore so the first play/resume
        // after an app-update restart can serve a still-valid resolved URL instead of re-running the slow
        // resolver. Best-effort, off the main thread; only non-expired entries are restored.
        loadPersistedSongUrlCache()

        // Warm up the poToken WebView shortly after startup so the FIRST song starts faster (the slow
        // botguard/WebView init happens ahead of play time instead of when you press play). Fully guarded;
        // no-ops if the session/WebView isn't ready yet. Delayed so cipher init + visitorData settle first.
        scope.launch(Dispatchers.IO) {
            kotlinx.coroutines.delay(500)
            runCatching { iad1tya.echo.music.utils.YTPlayerUtils.prewarmPoToken() }
            // Also warm the cipher player.js + WebView so the FIRST song's URL resolution is fast too (and
            // stays warm/reused for every song after). On MID/HIGH tier run it in PARALLEL with the poToken
            // prewarm (two WebViews at once is fine on capable RAM) so both are ready sooner; on LOW/ULTRA
            // keep it sequential so two WebViews don't spin up at once on weak/low-RAM devices. Best-effort.
            val warmTier = iad1tya.echo.music.utils.PerformanceMode.effectiveTier(this@MusicService)
            if (warmTier == iad1tya.echo.music.utils.DeviceTier.MID ||
                warmTier == iad1tya.echo.music.utils.DeviceTier.HIGH
            ) {
                scope.launch(Dispatchers.IO) {
                    runCatching { iad1tya.echo.music.utils.YTPlayerUtils.prewarmCipher() }
                }
            } else {
                runCatching { iad1tya.echo.music.utils.YTPlayerUtils.prewarmCipher() }
            }
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

        // Cold-start: read the whole settings snapshot ONCE instead of firing several separate blocking
        // dataStore.get(...) actor round-trips (each is its own runBlocking { data.first() } disk read)
        // during onCreate. Same keys, same defaults, same downstream usage — just one read instead of many.
        val prefs = runBlocking { dataStore.data.first() }
        player.repeatMode = prefs[RepeatModeKey] ?: REPEAT_MODE_OFF


        if (prefs[RememberShuffleAndRepeatKey] ?: true) {
            player.shuffleModeEnabled = prefs[ShuffleModeKey] ?: false
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

        audioQuality = prefs[AudioQualityKey].toEnum(iad1tya.echo.music.constants.AudioQuality.OPUS)
        ipVersion = prefs[IpVersionKey].toEnum(IpVersion.AUTO)
        // Repair: a persisted ~0 volume means it was captured mid-crossfade/duck by the old bug (a real
        // "I want silence" never persists as 0 — the user pauses/mutes instead). Treat it as full.
        playerVolume = MutableStateFlow(
            (prefs[PlayerVolumeKey] ?: 1f).let { if (it < 0.05f) 1f else it.coerceIn(0f, 1f) },
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
                // INSTANT VIDEO SWAP: speculative buffering is unmetered-only. On ANY network change,
                // re-check — connection lost or now metered → drop the pre-player (no-op when idle).
                if (!isConnected ||
                    runCatching { connectivityManager.isActiveNetworkMetered }.getOrDefault(true)
                ) {
                    teardownInstantVideoSwap("network lost or metered")
                }
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

        // SponsorBlock enable toggle: start/stop the position watcher and (when turned on mid-playback)
        // immediately fetch segments for the current track.
        scope.launch {
            dataStore.data
                .map { it[iad1tya.echo.music.constants.SponsorBlockEnabledKey] ?: false }
                .distinctUntilChanged()
                .collect { enabled ->
                    sponsorBlockEnabled = enabled
                    if (enabled) {
                        if (sponsorBlockJob?.isActive != true) sponsorBlockJob = startSponsorBlockWatcher()
                        val vid = sponsorBlock.begin(player.currentMediaItem?.mediaId)
                        if (vid != null) {
                            scope.launch(Dispatchers.IO) {
                                sponsorBlock.accept(
                                    vid,
                                    iad1tya.echo.music.playback.sponsorblock.SponsorBlockService.fetchSegments(vid),
                                )
                            }
                        }
                    } else {
                        sponsorBlockJob?.cancel()
                        sponsorBlockJob = null
                        sponsorBlock.clear()
                    }
                }
        }

        // ── Scrobbling (Last.fm + ListenBrainz) ──
        // Fully OPT-IN and network-only. The manager no-ops unless a provider is enabled AND its credentials
        // exist (Last.fm session key / ListenBrainz token), so with the defaults (everything OFF) this adds
        // no background work. This is the ONLY scrobbling wiring added to MusicService; the existing
        // onSongStart/onSongResume/onSongPause/onSongStop/onPlayerStateChanged call sites are untouched.
        scrobbleManager = ScrobbleManager(scope).apply {
            appContext = applicationContext
        }
        scope.launch {
            dataStore.data.map { it[EnableLastFMScrobblingKey] ?: false }.distinctUntilChanged().collect {
                scrobbleManager?.enableScrobbling = it
            }
        }
        scope.launch {
            dataStore.data.map { it[LastFMUseNowPlaying] ?: false }.distinctUntilChanged().collect {
                scrobbleManager?.useNowPlaying = it
            }
        }
        scope.launch {
            dataStore.data.map { it[iad1tya.echo.music.constants.LastFMUseSendLikes] ?: false }.distinctUntilChanged().collect {
                scrobbleManager?.useSendLikes = it
            }
        }
        scope.launch {
            dataStore.data.map { it[iad1tya.echo.music.constants.LastFMSessionKey] }.distinctUntilChanged().collect { sessionKey ->
                iad1tya.echo.music.utils.lastfm.LastFM.sessionKey = sessionKey?.takeIf { it.isNotBlank() }
            }
        }
        scope.launch {
            dataStore.data.map { it[ScrobbleMinSongDurationKey] ?: iad1tya.echo.music.utils.lastfm.LastFM.DEFAULT_SCROBBLE_MIN_SONG_DURATION }
                .distinctUntilChanged().collect { scrobbleManager?.minSongDuration = it }
        }
        scope.launch {
            dataStore.data.map { it[ScrobbleDelayPercentKey] ?: iad1tya.echo.music.utils.lastfm.LastFM.DEFAULT_SCROBBLE_DELAY_PERCENT }
                .distinctUntilChanged().collect { scrobbleManager?.scrobbleDelayPercent = it }
        }
        scope.launch {
            dataStore.data.map { it[ScrobbleDelaySecondsKey] ?: iad1tya.echo.music.utils.lastfm.LastFM.DEFAULT_SCROBBLE_DELAY_SECONDS }
                .distinctUntilChanged().collect { scrobbleManager?.scrobbleDelaySeconds = it }
        }
        scope.launch {
            dataStore.data.map { it[iad1tya.echo.music.constants.ListenBrainzEnabledKey] ?: false }.distinctUntilChanged().collect {
                scrobbleManager?.listenBrainzEnabled = it
            }
        }
        scope.launch {
            dataStore.data.map { it[iad1tya.echo.music.constants.ListenBrainzTokenKey] ?: "" }.distinctUntilChanged().collect {
                scrobbleManager?.listenBrainzToken = it
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
                .map { it[SafeVolumeEnabledKey] ?: true }
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

        // P33 — keep the memory mirrors for the player-thread hot paths (onMediaItemTransition /
        // onPlaybackStatsReady) in sync, so those callbacks read a @Volatile field instead of a blocking
        // runBlocking DataStore read on the main thread. Same defaults as the original dataStore.get calls.
        scope.launch {
            dataStore.data.collect { prefs ->
                autoLoadMoreHint = prefs[AutoLoadMoreKey] ?: true
                disableLoadMoreWhenRepeatAllHint = prefs[DisableLoadMoreWhenRepeatAllKey] ?: false
                keepGenreLaneHint = prefs[KeepGenreLaneKey] ?: true
                persistentQueueHint = prefs[PersistentQueueKey] ?: true
                historyDurationMsHint = (prefs[HistoryDuration]?.times(1000f)) ?: 30000f
                pauseListenHistoryHint = prefs[PauseListenHistoryKey] ?: false
                highPerformanceModeHint = prefs[iad1tya.echo.music.constants.HighPerformanceModeKey] ?: false
            }
        }



        combine(
            dataStore.data.map { prefs ->
                Triple(
                    // High-Performance Mode disables crossfade: it runs a SECOND ExoPlayer (double decode) per
                    // transition — the biggest CPU/RAM cost left on weak/TV/car devices. Transitions become hard cuts.
                    (prefs[CrossfadeEnabledKey] ?: false) && !(prefs[iad1tya.echo.music.constants.HighPerformanceModeKey] ?: false),
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
                                    // FIX A (#27): RESTORE — set items + seek but do NOT prepare; leave the
                                    // player IDLE so a boot-time BT/widget PLAY can't cold-start it. Prepared
                                    // lazily on the first genuine in-app play (PlayerConnection.play /
                                    // togglePlayPause / seek all prepare an IDLE player).
                                    isRestore = true,
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

        // High-Performance Mode trims ONLY the max-buffer + byte ceiling (less RAM on weak/TV/car devices).
        // Min-buffer, rebuffer and prioritizeTimeOverSizeThresholds are left untouched so hi-res/FLAC never
        // regresses into the "buffer full / empty time buffer" micro-stall described below.
        // The SMALL (light) buffer profile must cover genuinely low-end HARDWARE too, not only when the
        // High-Performance toggle is on. A low-RAM device left on the heavy 64MB/120s profile gets reaped by
        // the Low-Memory-Killer mid-song and restarts paused. So use the small profile when the toggle is on,
        // OR the effective device tier is ULTRA/LOW, OR the OS flags the device as low-RAM. (effectiveTier
        // already maps a low-RAM device to LOW, and returns ULTRA when the toggle is on; the extra checks are
        // explicit and defensive.)
        val perfMode = iad1tya.echo.music.utils.PerformanceMode.isOn(this)
        val effectiveTier = iad1tya.echo.music.utils.PerformanceMode.effectiveTier(this)
        val isLowRamDevice =
            (getSystemService(Context.ACTIVITY_SERVICE) as? android.app.ActivityManager)?.isLowRamDevice == true
        val useSmallBuffer = perfMode ||
            effectiveTier == iad1tya.echo.music.utils.DeviceTier.ULTRA ||
            effectiveTier == iad1tya.echo.music.utils.DeviceTier.LOW ||
            isLowRamDevice
        val maxBufferMs = if (useSmallBuffer) 60_000 else 120_000
        val targetBufferBytes = if (useSmallBuffer) 32 * 1024 * 1024 else 64 * 1024 * 1024
        // Music-VIDEO quality adapts to the device so switching to video never overwhelms a weak TV box / low-end
        // phone. This caps ONLY the video track the ABR selects (audio is untouched, and it's a no-op for
        // audio-only playback), so it keeps gama-baja/TV optimized without harming Hi-Res audio.
        // ALIGNMENT: on a TV form factor the video path can request up to 1080p (1920 wide, via
        // videoModeMaxHeight). A 1280 cap (1080 tall but 1920 wide) would silently REJECT that track → no video
        // renders and it looks like an endless network stall. So on TV, raise the ULTRA/LOW cap to 1920 so the
        // bandwidth-chosen height (≤1080) is always selectable; non-TV low-tier keeps the tighter 1280 cap.
        val isTv = iad1tya.echo.music.utils.DeviceForm.isTelevision(this)
        val maxVideoDim = when (effectiveTier) {
            iad1tya.echo.music.utils.DeviceTier.ULTRA -> if (isTv) 1920 else 1280
            iad1tya.echo.music.utils.DeviceTier.LOW -> if (isTv) 1920 else 1280
            iad1tya.echo.music.utils.DeviceTier.MID -> 1920
            iad1tya.echo.music.utils.DeviceTier.HIGH -> Int.MAX_VALUE
        }
        val videoTrackSelector = androidx.media3.exoplayer.trackselection.DefaultTrackSelector(this).apply {
            parameters = buildUponParameters().setMaxVideoSize(maxVideoDim, maxVideoDim).build()
        }
        val player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(createMediaSourceFactory())
            .setTrackSelector(videoTrackSelector)
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
                        // inherited). Reconciled below. (High-Performance Mode uses 60s.)
                        maxBufferMs,
                        // bufferForPlaybackMs: ~750ms buffered before playback (re)starts — a fast start
                        // without risking an immediate re-stall on hi-res/low-end (upstream 97787ed value).
                        // Applies in BOTH buffer profiles; the min/max duration tier split (maxBufferMs) above
                        // is untouched.
                        750,
                        // bufferForPlaybackAfterRebufferMs: ~2.5s to resume after a stall — faster than the 5s
                        // default but not so thin it ping-pongs stalls on weak/hi-res streams. Profile-independent.
                        2500,
                    )
                    // 64MB byte ceiling guards against OOM with multiple pre-loaded/crossfade players,
                    // but prioritizeTimeOverSizeThresholds(true) lets the TIME buffer win so the min/max
                    // duration is actually honored for hi-res streams instead of being clipped to the byte cap.
                    // (High-Performance Mode uses 32MB.)
                    .setTargetBufferBytes(targetBufferBytes)
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
                // Debug-only video-swap latency probe (see videoSwapDebugListener). Registered on every
                // player instance so it survives the crossfade publish of a new main player; it only logs
                // while a measurement window is armed (videoSwapT0 != 0) and is absent on release builds.
                if (iad1tya.echo.music.BuildConfig.DEBUG) addAnalyticsListener(videoSwapDebugListener)
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
            // Dead-end: too many failed attempts. PAUSE, but do NOT abandon the state. Keep
            // waitingForNetworkConnection = true and flag the pause as network-caused, so the connectivity
            // collector in onCreate resumes playback when the network returns. There is no polling loop:
            // resume happens on a connectivity event OR via the single bounded re-check armed below.
            Timber.tag(TAG).w("Max retry count ($MAX_RETRY_COUNT) reached; pausing and waiting for the network to resume")
            // Capture the user's intent BEFORE our own stopOnError() flips playWhenReady to false. If the user
            // had already paused during the retry storm, playWhenReady is false here and stopOnError()'s pause()
            // is a no-op (so the onPlayWhenReadyChanged handshake never fires) — in that case we must NOT re-arm
            // an auto-resume, or a reconnect would wrongly resume over the user's manual pause.
            val wasPlaying = player.playWhenReady
            stopOnError()
            retryCount = 0
            pausedByNetwork = wasPlaying
            pausedByNetworkAtMs = System.currentTimeMillis()
            waitingForNetworkConnection.value = true
            // Safety net for a STABLE network that never emits another connectivity event (so the collector
            // never re-fires): after a short delay, try exactly ONCE if we're still network-paused and
            // connectivity says we're online. Single-shot (no loop) and cancellable, so it can't stack or
            // drain the battery.
            deadEndRecheckJob?.cancel()
            deadEndRecheckJob = scope.launch {
                delay(DEAD_END_RECHECK_MS)
                if (pausedByNetwork && isNetworkConnected.value) {
                    triggerRetry()
                }
            }
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
        deadEndRecheckJob?.cancel()

        if (player.currentMediaItem != null) {
            if (retryCount > 3) {
                Timber.tag(TAG).d("Retry count > 3, attempting to refresh stream URL")
                val currentPosition = player.currentPosition
                player.seekTo(player.currentMediaItemIndex, currentPosition)
            }
            player.prepare()
            // If playback was PAUSED by the network dead-end (not by the user), resume now that connectivity is
            // back. prepare() alone re-buffers but keeps playWhenReady = false (stopOnError() paused us), so we
            // must explicitly play(). Two bounds keep this safe: (1) any genuine user/external pause clears
            // pausedByNetwork (see onPlayWhenReadyChanged), so we never override an intentional pause; (2) we
            // only auto-play within STALE_RESUME_WINDOW_MS, so a reconnection long after the outage re-buffers
            // but stays paused.
            if (pausedByNetwork) {
                pausedByNetwork = false
                val fresh = System.currentTimeMillis() - pausedByNetworkAtMs <= STALE_RESUME_WINDOW_MS
                if (fresh && castConnectionHandler?.isCasting?.value != true) {
                    // Reclaim audio focus first so we don't resume over whatever took over during the outage.
                    requestAudioFocus()
                    player.play()
                }
            }
        } else {
            pausedByNetwork = false
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
        // Mark that the imminent pause is OURS, so onPlayWhenReadyChanged keeps pausedByNetwork (whereas a
        // genuine user/external pause clears it and is therefore never auto-resumed).
        expectingOwnStopPause = true
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
        // FIX A (#27): true ONLY for the persistent-queue restore at process start. When true, the queue's
        // media items + saved seek are set but the player is left IDLE (NOT prepared), so an external
        // media-button PLAY at boot can't cold-start it. Any normal caller keeps the default (false) and
        // prepares+plays exactly as before.
        isRestore: Boolean = false,
    ) {
        // #27: a genuine user-initiated playQueue (playWhenReady=true) clears the restore veto so external
        // controls work normally. A restore calls this with playWhenReady=false and leaves it armed.
        if (playWhenReady) awaitingFirstUserPlay = false
        _mixActive.value = false  // fresh user-chosen queue → Mix/Radio no longer active
        // Fresh user queue → the old autoplay chips no longer describe what will play next. Clearing the
        // seed cache also re-allows one refresh for the NEXT radio seed (still once-per-seed bounded).
        _autoplayChips.value = emptyList()
        _autoplaySelectedChip.value = null
        autoplayChipsSeedId = null
        if (!scope.isActive) scope = CoroutineScope(Dispatchers.Main) + Job()

        
        if (!playerInitialized.value) {
            Timber.tag(TAG).w("playQueue called before player initialization, queuing request")
            scope.launch {
                playerInitialized.first { it }
                playQueue(queue, playWhenReady, isRestore)
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
                if (isRestore) {
                    // FIX A (#27): RESTORE — prepare the queue (so the mini-player / notification / Android Auto
                    // show the song + the widget/UI resume works) but leave it PAUSED, and arm
                    // awaitingFirstUserPlay. The real anti-phantom guard is the onPlayerCommandRequest veto in
                    // MediaLibrarySessionCallback: while armed, an external PLAY (BT/headset/car/watch/notif)
                    // is rejected BEFORE media3 can prepare()+play() the restored queue. Cleared the instant the
                    // user genuinely engages (in-app play, opening the app, widget tap, or real playback start).
                    player.prepare()
                    player.playWhenReady = false
                    // Don't re-arm if the user already foregrounded the app (race: restore runs async after bind).
                    if (!userHasForegroundedThisProcess) awaitingFirstUserPlay = true
                } else {
                    player.prepare()
                    // Use play() (not just playWhenReady=true) so playback actually starts on the first
                    // try — for direct-URL media (podcasts) setting the flag alone sometimes left it
                    // prepared-but-paused until a manual pause→play.
                    if (playWhenReady) player.play() else player.playWhenReady = false
                }
            }

            // NO-REPEAT: seed the session-wide dedupe with the ENTIRE initial queue so the infinite radio's
            // pagination / re-seed can never resurface a song that was already part of the queue the user
            // started from. Records the full list regardless of the preload/normal branch above.
            sessionPlayedIds.addAll(initialStatus.items.mapNotNull { it.mediaId })

            // Phase A #1/#6 — multi-seed pool: snapshot the collection's tracks so a later re-seed preserves its
            // artist/genre mix instead of collapsing to the single last song. Skip pure radios (YouTubeQueue) —
            // those are already a single-song radio and must keep last-song seeding. Reassigned every playQueue.
            radioSeedPool = if (queue is iad1tya.echo.music.playback.queues.YouTubeQueue) emptyList()
                else initialStatus.items.mapNotNull { it.metadata }
            // #34 — starting an explicit COLLECTION (playlist/album/list) supersedes any lingering Home-mood
            // bias: a stale mood chip must NOT hijack the infinite continuation of a playlist ("nada que ver").
            // A mood the user taps AFTER this (setActiveMood, no playQueue) survives, so the deliberate-mood
            // case still works.
            if (queue !is iad1tya.echo.music.playback.queues.YouTubeQueue) {
                activeMoodParams = null
                activeMoodTitle = null
            }


            if (player.shuffleModeEnabled) {
                val shufflePlaylistFirst = dataStore.get(ShufflePlaylistFirstKey, false)
                applyShuffleOrder(player.currentMediaItemIndex, player.mediaItemCount, shufflePlaylistFirst)
            }
            
            preloadUpcomingItems()
        }
    }

    /**
     * Set (or clear) the ACTIVE MOOD that biases the infinite radio's seed. When [params] is non-null, the
     * next time the radio needs to seed/append ([startRadioSeamlessly]) it seeds from the mood's Home feed
     * (YouTube.home(params)) instead of the last song — still ordered by [orderedByTaste] so relatedness/taste
     * is preserved. Pass null to restore last-song seeding (exactly today's behavior). Cheap: stores two
     * @Volatile fields; the network fetch only happens on the next actual seed. Called from the Home mood UI
     * via PlayerConnection.setActiveMood.
     */
    fun setActiveMood(params: String?, title: String?) {
        activeMoodParams = params
        activeMoodTitle = title
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
            // Resolve the YouTube videoId to seed the radio from. For a normal online track the mediaId IS the
            // videoId. For a LOCAL library track (content://) or a direct-URL (http) podcast the mediaId is NOT a
            // YouTube id — tryRadio/tryRelated would fail and we'd loop forever on the replay last-resort instead
            // of getting real infinite radio. So look the current song up on YouTube by "title artist" and seed
            // from that match. Runs on IO (network). Null → no YouTube identity found → skip radio, fall through
            // to the replay last-resort. (Kept off the player thread; only addMediaItems below runs on Main.)
            val seedVideoId: String? = withContext(Dispatchers.IO) {
                if (!currentMediaId.isLocalMediaId() &&
                    !currentMediaId.startsWith("http", ignoreCase = true)
                ) {
                    currentMediaId
                } else {
                    val artistText = currentMediaMetadata.artists.joinToString(" ") { it.name }
                    val query = listOf(currentMediaMetadata.title, artistText)
                        .filter { it.isNotBlank() }
                        .joinToString(" ")
                    if (query.isBlank()) null
                    else runCatching {
                        YouTube.search(query, YouTube.SearchFilter.FILTER_SONG).getOrNull()
                            ?.items?.filterIsInstance<SongItem>()?.firstOrNull()?.id
                    }.getOrNull()
                }
            }

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
                val toAppend = items.orderedByTaste()
                player.addMediaItems(liveIndex + 1, toAppend)
                sessionPlayedIds.addAll(toAppend.mapNotNull { it.mediaId }) // NO-REPEAT: record what we appended
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

            // Source 1 — a proper radio queue seeded from the last song the user heard (or, for a local/direct-URL
            // track, from its resolved YouTube match). No seed id → nothing to seed from → let a later source /
            // the replay last-resort handle it.
            suspend fun tryRadio(): Boolean = runCatching {
                val seed = seedVideoId ?: return@runCatching false
                val radioQueue = YouTubeQueue(endpoint = WatchEndpoint(videoId = seed))
                val initialStatus = withContext(Dispatchers.IO) {
                    radioQueue.getInitialStatus()
                        .filterExplicit(dataStore.get(HideExplicitKey, false))
                        .filterVideoSongs(dataStore.get(HideVideoSongsKey, false))
                }
                if (initialStatus.title != null) queueTitle = initialStatus.title
                val items = initialStatus.items.filter { it.mediaId != seed && it.mediaId != currentMediaId }
                val ok = appendSeed(items)
                if (ok) currentQueue = radioQueue
                ok
            }.getOrDefault(false)

            // Source 2 — "related" songs of the last song (a different YT endpoint; recovers when radio is empty).
            suspend fun tryRelated(): Boolean = runCatching {
                val seed = seedVideoId ?: return@runCatching false
                val nextResult = withContext(Dispatchers.IO) {
                    YouTube.next(WatchEndpoint(videoId = seed)).getOrNull()
                }
                val relatedEndpoint = nextResult?.relatedEndpoint ?: return@runCatching false
                val relatedPage = withContext(Dispatchers.IO) { YouTube.related(relatedEndpoint).getOrNull() }
                val items = relatedPage?.songs.orEmpty()
                    .filter { it.id != seed && it.id != currentMediaId }
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
                    val rq = YouTubeQueue(endpoint = WatchEndpoint(videoId = seed))
                    runCatching { withContext(Dispatchers.IO) { rq.getInitialStatus() } }
                    currentQueue = rq
                }
                ok
            }.getOrDefault(false)

            // Source 0 — ACTIVE MOOD. When the user has selected a Home mood, seed the infinite radio from that
            // mood's Home feed (YouTube.home(params)) instead of the last song. The songs still flow through
            // orderedByTaste() (relatedness/taste order + recently-played exclusion) so the invariant holds. The
            // mood pool is finite, so we point currentQueue at EmptyQueue: when this batch nears its end the
            // last-item radio-seed net re-invokes startRadioSeamlessly → tryMood() again → a fresh mood batch,
            // keeping it endless AND all-mood, one bounded home() fetch per re-seed. Null mood → returns false →
            // falls through to today's last-song seeding unchanged.
            suspend fun tryMood(): Boolean = runCatching {
                val moodParams = activeMoodParams ?: return@runCatching false
                val page = withContext(Dispatchers.IO) {
                    YouTube.home(params = moodParams).getOrNull()
                } ?: return@runCatching false
                val items = page.sections
                    .flatMap { it.items }
                    .filterIsInstance<SongItem>()
                    .distinctBy { it.id }
                    .filter { it.id != currentMediaId }
                    .map { it.toMediaItem() }
                    .filterExplicit(dataStore.get(HideExplicitKey, false))
                    .filterVideoSongs(dataStore.get(HideVideoSongsKey, false))
                val ok = appendSeed(items)
                if (ok) {
                    activeMoodTitle?.let { queueTitle = it }
                    // Finite pool → no pagination from a stale last-song queue; the end-of-queue net re-seeds the mood.
                    currentQueue = EmptyQueue
                }
                ok
            }.getOrDefault(false)

            // Source 0.5 — CONTEXT multi-seed. When the user started from an album/playlist/list
            // (radioSeedPool > 1), seed the infinite radio from a REPRESENTATIVE SAMPLE of that collection (not
            // just the last song) so the continuation keeps the collection's artist/genre MIX. Picks up to 4
            // distinct-artist seeds (always incl. the current/last song), fetches each one's YouTube radio page,
            // round-robin MERGES + dedupes them, then the shared appendSeed() taste-orders + no-repeat-filters +
            // re-arms crossfade. currentQueue is primed from a seed so the Path A pagination keeps going. Pool <= 1
            // or empty (a pure radio) → returns false → the last-song tryRadio handles it, unchanged. Bounded
            // (<= 4 seed getInitialStatus + 1 prime), off the player thread; only ever runs on a RE-SEED when a
            // finite collection ends.
            suspend fun tryContextRadio(): Boolean = runCatching {
                if (radioSeedPool.size <= 1) return@runCatching false
                val profile = runCatching { tasteProfile() }.getOrNull()
                // Only online YouTube ids are usable as radio seeds (skip local content:// and direct-URL http).
                fun iad1tya.echo.music.models.MediaMetadata.ytId(): String? =
                    id.takeIf { !it.isLocalMediaId() && !it.startsWith("http", ignoreCase = true) }
                // #34 — seed from the LIVE recently-played TAIL (what JUST played), not the play-time first-page
                // snapshot: for a genre-ordered playlist the tail is the genre the user hears at the end, so the
                // continuation matches it (radioSeedPool page 1 = the head genre → felt unrelated). Falls back to
                // radioSeedPool if the timeline read is empty. Safe: runs on the Main-dispatched scope before IO.
                val liveIdx = player.currentMediaItemIndex
                val tailPool: List<iad1tya.echo.music.models.MediaMetadata> =
                    if (liveIdx >= 0) {
                        (maxOf(0, liveIdx - 24)..liveIdx).mapNotNull {
                            runCatching { player.getMediaItemAt(it).metadata }.getOrNull()
                        }
                    } else emptyList()
                // Recent-first so the DISTINCT-artist reps come from the END of what was playing, not the start.
                val contextPool = tailPool.ifEmpty { radioSeedPool }.asReversed()
                // Distinct primary artist → one representative track (recent-first order).
                val byArtist = LinkedHashMap<String, iad1tya.echo.music.models.MediaMetadata>()
                contextPool.forEach { mm ->
                    val key = mm.artists.firstOrNull()?.name?.lowercase() ?: return@forEach
                    if (mm.ytId() != null) byArtist.putIfAbsent(key, mm)
                }
                // Prefer higher-taste artists for the (bounded) seed set.
                val ranked = byArtist.values.sortedByDescending { mm ->
                    if (profile == null) 0.0 else profile.scoreNames(mm.artists.map { it.name }, mm.title)
                }
                // Seeds (up to 4 distinct ids) that capture the RANGE: the current/last song first ("more like
                // what just played"), then one representative per DISTINCT ARTIST (recent-first, taste-ranked),
                // then more distinct recent TRACKS (so a SINGLE-ARTIST ALBUM still multi-seeds across its range).
                val perArtistIds = ranked.mapNotNull { it.ytId() }
                val poolIds = contextPool.mapNotNull { it.ytId() }
                val seeds = (listOfNotNull(seedVideoId) + perArtistIds + poolIds).distinct().take(4)
                if (seeds.size < 2) return@runCatching false // truly one usable track → let tryRadio do last-song
                // Fetch each seed's radio page (bounded to 12 items each), off the player thread.
                val perSeed = withContext(Dispatchers.IO) {
                    seeds.map { sv ->
                        runCatching {
                            YouTubeQueue(endpoint = WatchEndpoint(videoId = sv)).getInitialStatus()
                                .items.filter { it.mediaId != sv && it.mediaId != currentMediaId }.take(12)
                        }.getOrDefault(emptyList())
                    }
                }
                // Round-robin MERGE so no single seed dominates; dedupe by id. Visit EVERY position up to the
                // longest page (never break early on a no-add pass) so a unique item after an intra-page duplicate
                // is not stranded. mediaId is non-null (media3 @NonNull).
                val merged = ArrayList<MediaItem>()
                val seen = HashSet<String>()
                val maxSize = perSeed.maxOfOrNull { it.size } ?: 0
                for (i in 0 until maxSize) {
                    for (lst in perSeed) {
                        if (i < lst.size && seen.add(lst[i].mediaId)) merged.add(lst[i])
                    }
                }
                val items = merged
                    .filterExplicit(dataStore.get(HideExplicitKey, false))
                    .filterVideoSongs(dataStore.get(HideVideoSongsKey, false))
                val ok = appendSeed(items) // appendSeed already runs orderedByTaste + records no-repeat + crossfade
                if (ok) {
                    // Prime a radio from a seed so the Path A pagination keeps going after this merged batch.
                    val rq = YouTubeQueue(endpoint = WatchEndpoint(videoId = seeds.first()))
                    runCatching { withContext(Dispatchers.IO) { rq.getInitialStatus() } }
                    currentQueue = rq
                }
                ok
            }.getOrDefault(false)

            try {
                // Mood (if active) first, then radio, then related. If a transient hiccup left us empty, wait
                // briefly and try once more — so a momentary network blip at the exact end-of-queue moment never
                // permanently stops the music.
                var appended = tryMood() || tryContextRadio() || tryRadio() || tryRelated()
                if (!appended) {
                    kotlinx.coroutines.delay(2500)
                    appended = tryMood() || tryContextRadio() || tryRadio() || tryRelated()
                }
                // Autoplay chips: the seed just landed (appendSeed ran) → refresh the queue-footer
                // suggestions for THIS seed. Bounded: no-ops if this seed's chips are already loaded.
                if (appended) seedVideoId?.let { refreshAutoplaySuggestions(it) }
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

    /**
     * AUTOPLAY CHIPS (YT Music queue-footer parity) — refresh the suggestion chips for [seedId], the song
     * the infinite autoplay is currently seeded from. At most ONE network refresh per seed (cached via
     * [autoplayChipsSeedId]); never on a timer. Fetches the seed's related page (YouTube.next →
     * relatedEndpoint → YouTube.related) on IO and builds:
     *   1. the default "related" chip (a radio seeded from the seed song itself),
     *   2. up to 5 artist-radio chips (artists carrying a ready-made radioEndpoint, disliked filtered out),
     *   3. mix/playlist chips that carry a radioEndpoint.
     * Chips only offer WHERE to steer the autoplay; selecting one goes through [selectAutoplayChip],
     * whose items still flow through orderedByTaste() (relatedness-order invariant).
     */
    private fun refreshAutoplaySuggestions(seedId: String) {
        if (!autoLoadMoreHint) return // chips are part of autoplay; OFF = no speculative fetch at all
        if (seedId.isEmpty() || seedId.isLocalMediaId() || seedId.startsWith("http", ignoreCase = true)) return
        if (autoplayChipsSeedId == seedId) return // once per seed
        autoplayChipsSeedId = seedId
        scope.launch(SilentHandler) {
            val disliked = runCatching { dislikeStore.snapshot() }
                .getOrDefault(iad1tya.echo.music.dislike.DislikeStore.Disliked())
            val relatedPage = withContext(Dispatchers.IO) {
                runCatching {
                    val nextResult = YouTube.next(WatchEndpoint(videoId = seedId)).getOrNull()
                    nextResult?.relatedEndpoint?.let { YouTube.related(it).getOrNull() }
                }.getOrNull()
            }
            // Seed moved on while we fetched → these chips describe a stale seed; drop them.
            if (autoplayChipsSeedId != seedId) return@launch
            val defaultChip = AutoplayChip(
                label = getString(R.string.autoplay_chip_related),
                endpoint = WatchEndpoint(videoId = seedId),
                kind = AutoplayChip.Kind.RELATED,
            )
            val artistChips = relatedPage?.artists.orEmpty()
                .filter { it.id !in disliked.artists }
                .mapNotNull { artist ->
                    artist.radioEndpoint?.let { AutoplayChip(artist.title, it, AutoplayChip.Kind.ARTIST) }
                }
                .take(5)
            val mixChips = relatedPage?.playlists.orEmpty()
                .filter { it.id !in disliked.playlists }
                .mapNotNull { playlist ->
                    playlist.radioEndpoint?.let { AutoplayChip(playlist.title, it, AutoplayChip.Kind.MIX) }
                }
                .take(5)
            _autoplayChips.value = listOf(defaultChip) + artistChips + mixChips
            // A NEW seed resets the steer to the default related chip (the last-song-seeded radio that is
            // actually playing) — a previously selected artist/mix chip no longer reflects the live queue.
            _autoplaySelectedChip.value = defaultChip
        }
    }

    /**
     * User tapped an autoplay chip: RE-SEED the autoplay tail from [chip]'s WatchEndpoint, reusing the
     * exact appendSeed machinery of [startRadioSeamlessly] (drop the tail after the live index, append
     * `.orderedByTaste()` — relatedness order stays the backbone, taste only nudges, disliked dropped —
     * mark Mix active, re-arm the crossfade) and re-point [currentQueue] at the primed chip queue so the
     * onMediaItemTransition pagination continues from the chip's radio. A chip is a TEMPORARY user steer:
     * nothing here overrides the default last-song seeding of future automatic seeds.
     */
    fun selectAutoplayChip(chip: AutoplayChip) {
        if (!playerInitialized.value) return
        val currentMediaId = player.currentMediaItem?.mediaId ?: return
        // Optimistic highlight so the tap feels instant — but capture the previous steer and REVERT on
        // every failure path below, so a failed/empty fetch never leaves the UI claiming a steer that
        // the live queue doesn't reflect. compareAndSet (not plain set) so we never clobber a NEWER
        // selection (a new seed's refreshAutoplaySuggestions reset, or a second tap) that landed while
        // this attempt was in flight.
        val previousChip = _autoplaySelectedChip.value
        _autoplaySelectedChip.value = chip
        fun revertChip() { _autoplaySelectedChip.compareAndSet(chip, previousChip) }
        scope.launch(SilentHandler) {
            // RACE HARDENING: a chip tap can race an in-flight automatic seed (head-start B3 /
            // STATE_ENDED net) — whichever landed last would silently overwrite the other's tail.
            // The chip is a direct user action, so it wins: wait (bounded, mirroring
            // armRadioResumeWatchdog) for the seed to settle, then CLAIM radioSeedInFlight for this
            // append so any concurrent automatic seed bails on its own guard while the chip re-seeds.
            var waited = 0L
            while (radioSeedInFlight && waited < 15_000) {
                kotlinx.coroutines.delay(250)
                waited += 250
            }
            if (radioSeedInFlight) { // seed hung for 15 s — don't stack a second rewrite on top of it
                revertChip()
                return@launch
            }
            radioSeedInFlight = true
            var applied = false
            try {
                val chipQueue = YouTubeQueue(endpoint = chip.endpoint)
                val initialStatus = withContext(Dispatchers.IO) {
                    runCatching {
                        chipQueue.getInitialStatus()
                            .filterExplicit(dataStore.get(HideExplicitKey, false))
                            .filterVideoSongs(dataStore.get(HideVideoSongsKey, false))
                    }.getOrNull()
                } ?: return@launch
                val items = initialStatus.items.filter { it.mediaId != currentMediaId }
                if (items.isEmpty()) return@launch
                // Same append semantics as appendSeed in startRadioSeamlessly: recompute the index from the
                // LIVE player at append time, replace only the tail AFTER the current item (the tail is
                // radio/autoplay content), and keep the current song playing untouched.
                val liveIndex = player.currentMediaItemIndex
                val itemCount = player.mediaItemCount
                if (itemCount > liveIndex + 1) {
                    player.removeMediaItems(liveIndex + 1, itemCount)
                }
                val toAppend = items.orderedByTaste()
                player.addMediaItems(liveIndex + 1, toAppend)
                sessionPlayedIds.addAll(toAppend.mapNotNull { it.mediaId }) // NO-REPEAT: record what we appended
                _mixActive.value = true
                if (initialStatus.title != null) queueTitle = initialStatus.title
                if (player.shuffleModeEnabled) {
                    val shufflePlaylistFirst = dataStore.get(ShufflePlaylistFirstKey, false)
                    applyShuffleOrder(player.currentMediaItemIndex, player.mediaItemCount, shufflePlaylistFirst)
                }
                // getInitialStatus above primed the continuation → hasNextPage() is true and the existing
                // pagination in onMediaItemTransition keeps loading this chip's radio forever.
                currentQueue = chipQueue
                scheduleCrossfade()
                applied = true
            } finally {
                // Only the claim is released here — resumeAfterSeed belongs to startRadioSeamlessly's
                // own finally and is NOT touched by the chip path. Any path that did NOT rewrite the
                // tail (null/empty fetch, exception swallowed by SilentHandler) reverts the highlight
                // so the chip UI never claims a steer the live queue doesn't reflect.
                radioSeedInFlight = false
                if (!applied) revertChip()
            }
        }
    }

    /**
     * Race hardening for the STATE_ENDED net. If the queue truly ended while a head-start (B3) seed was still in
     * flight, we did NOT launch a second seed (radioSeedInFlight guard). Should that in-flight seed settle having
     * appended nothing, it clears radioSeedInFlight + resumeAfterSeed in its finally and the player is left
     * stopped at end-of-queue with no next item — a dead-end. This bounded, single-shot, idempotent watchdog
     * waits for the seed to settle, then re-checks the LIVE player: only if we're still genuinely stopped at a
     * true end-of-queue (no seed running, nothing more to play) does it re-arm resumeAfterSeed and kick a fresh
     * seed — which itself has a replay last-resort, so playback can never dead-end online. If the seed already
     * appended/resumed us, every condition is false → no-op. Runs on [scope] (Main), so player access is safe.
     */
    private fun armRadioResumeWatchdog() {
        radioResumeWatchdogJob?.cancel()
        radioResumeWatchdogJob = scope.launch {
            // Poll until the in-flight seed settles (it retries with a ~2.5s backoff); give up after a bound so a
            // genuinely stuck seed can never keep this alive (that seed's own replay last-resort still covers us).
            var waited = 0
            while (radioSeedInFlight && waited < 15_000) {
                delay(500)
                waited += 500
            }
            if (!radioSeedInFlight &&
                !player.isPlaying &&
                player.playbackState == Player.STATE_ENDED &&
                player.mediaItemCount > 0 &&
                !player.hasNextMediaItem()
            ) {
                Timber.tag(TAG).w("In-flight radio seed settled empty at end-of-queue; re-seeding so playback doesn't dead-end")
                resumeAfterSeed = true
                startRadioSeamlessly()
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
            // SECONDARY, opt-in Last.fm taste seed. Gated: only when the toggle is ON AND a Last.fm username
            // exists — otherwise emptyMap => zero behavior change. Reads the daily-worker cache only (no network).
            // On IO: the DataStore gate reads + cache read use the runBlocking accessor, so keep them off Main.
            val lastfm = withContext(Dispatchers.IO) {
                if (
                    dataStore.get(iad1tya.echo.music.constants.UseLastFmTasteKey, false) &&
                    dataStore.get(iad1tya.echo.music.constants.LastFMUsernameKey, "").isNotBlank()
                ) iad1tya.echo.music.reco.LastFmTasteSource.cached(this@MusicService) else emptyMap()
            }
            withContext(Dispatchers.Default) {
                iad1tya.echo.music.reco.AffinityEngine.buildProfile(events, disliked, artistGenres = genres, onboardingGenres = onboarding, librarySongs = library, followedArtists = followed, externalArtistWeights = lastfm)
            }.also {
                cachedTaste = it
                cachedTasteAt = now
            }
        }.getOrNull()
    }

    /**
     * Cached co-relatedness map (see [iad1tya.echo.music.reco.SongGraphCache]): candidateId -> how many of the
     * user's liked-song anchors YouTube says this candidate is related to (0..N). Built by counting, across the
     * graph's cached anchors, how many of their related-sets contain each candidate id — so a song co-related to
     * several liked songs scores higher. Rebuilt at most once / 5 min (same cadence as [tasteProfile]) on a
     * background dispatcher: bounded work, NEVER per-song, never throws. Empty graph (cold start / feature idle)
     * → empty map → the co-rel term in [orderedByTaste] is a no-op (zero behaviour change).
     */
    private suspend fun coRelMap(): Map<String, Int> {
        val now = System.currentTimeMillis()
        cachedCoRel?.let { if (now - cachedCoRelAt < 5 * 60_000L) return it }
        val map = runCatching {
            withContext(Dispatchers.IO) {
                val graph = iad1tya.echo.music.reco.SongGraphCache.snapshot(this@MusicService)
                if (graph.isEmpty()) return@withContext emptyMap<String, Int>()
                val counts = HashMap<String, Int>()
                // Every cached liked-song anchor's related-set adds +1 to each id it contains, so counts[x] =
                // number of liked anchors x is co-related to. Uses ALL anchors (deterministic) — the graph is
                // kept bounded to the recent-liked set by SongGraphCache.enrich's prune, so this stays small.
                graph.values.forEach { related ->
                    related.forEach { rid -> counts.merge(rid, 1, Int::plus) }
                }
                counts
            }
        }.getOrDefault(emptyMap())
        cachedCoRel = map
        cachedCoRelAt = now
        return map
    }

    /**
     * Bounded, cached set of song ids the user has RECENTLY PLAYED (on-device event history). Used by
     * [orderedByTaste] to exclude already-heard songs from the primary radio pool so the infinite queue stops
     * replaying songs heard days/weeks ago (which the last-~60 in-session [recentRadioIds] can't see). One DB
     * read every ~5 min (same TTL as [tasteProfile]); membership is O(1) at append time — no per-append DB hit.
     */
    private suspend fun recentlyPlayedIds(): Set<String> {
        val now = System.currentTimeMillis()
        cachedPlayedIds?.let { if (now - cachedPlayedIdsAt < 5 * 60_000L) return it }
        val ids = runCatching {
            withContext(Dispatchers.IO) { database.recentEventsWithSong(600).first() }
                .mapTo(HashSet<String>()) { it.song.id }
        }.getOrDefault(emptySet())
        cachedPlayedIds = ids
        cachedPlayedIdsAt = now
        return ids
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
        // "Already heard" now has TWO memories: the last ~60 in-session transitions ([recentRadioIds]) AND the
        // broader on-device listening history ([recentlyPlayedIds], cached ~5 min). A song in EITHER is excluded
        // from the primary radio pool and kept only as a fallback TAIL — so the infinite queue stops resurfacing
        // songs the user heard days/weeks ago, yet can never dead-end (heard items remain as a last resort, same
        // safety philosophy as the old +1000 soft penalty). YouTube relatedness ORDER is preserved WITHIN each
        // bucket: the per-item sort key keeps `index` (relatedness rank) as its dominant term.
        val recentSnapshot = synchronized(recentRadioIds) { HashSet(recentRadioIds) }
        val playedHistory = recentlyPlayedIds()
        // Phase B #5 — read the cached co-relatedness counts ONCE here (NEVER per item): candidateId -> how many
        // liked-song anchors YouTube says it's related to. Empty on cold start / until the WiFi-gated graph fills.
        val coRelCounts = coRelMap()
        val rnd = java.util.Random()
        // Precompute the sort key ONCE per item: calling rnd inside the comparator would make it inconsistent
        // between comparisons and crash TimSort ("Comparison method violates contract").
        val keyed = filtered.mapIndexed { index, mi ->
            val m = mi.metadata
            val taste = if (m == null || p == null) 0.0 else p.scoreNames(m.artists.map { it.name }, m.title)
            // Phase B #5 — co-relatedness bonus (0..3): a candidate related to several liked songs ranks a few
            // spots earlier (max ~6, comparable to a strong taste artist). Empty map → 0 → no change.
            val coRel = if (m == null) 0 else (coRelCounts[m.id] ?: 0).coerceAtMost(3)
            // Phase B #7 — "Menos de esto" graded feedback: a BOUNDED penalty (~6 spots LATER), NOT a drop. The
            // hard-dislike sledgehammer (filtered out above) stays untouched and separate. Reuses `disliked`.
            val soft = if (m != null && (
                    m.id in disliked.softSongs ||
                    m.artists.any { (it.id != null && it.id in disliked.softArtists) || it.name.lowercase() in disliked.softArtists }
                )) 6.0 else 0.0
            // Lower key = earlier. `index` (relatedness rank) DOMINATES; taste/co-rel only NUDGE a song up a few
            // spots and jitter adds variety — relatedness stays the backbone.
            // Registry #25: the `index` rank stays dominant and taste's coefficient (4.0) is unchanged. #5 adds a
            // co-rel pull, #7 a soft push; CAP the combined forward (taste+coRel) pull at 8 so a favorite +
            // highly-co-related song stays a NUDGE (not a scramble) even on a short ~12-item page.
            val jitter = if (p == null) 0.0 else rnd.nextDouble() * 1.5
            val pull = (taste * 4.0 + coRel * 2.0).coerceAtMost(8.0)
            val key = index.toDouble() - pull + soft + jitter
            // NO-REPEAT: "heard" is now SESSION-WIDE ([sessionPlayedIds] — everything played OR appended this
            // session), broadened beyond the last-~60 [recentSnapshot] and the ~5-min DB [playedHistory].
            val heard = m != null && (m.id in sessionPlayedIds || m.id in recentSnapshot || m.id in playedHistory)
            Triple(mi, key, heard)
        }
        // Phase B #4 — exploration quota: reserve ~1-in-5 slots for a FRESH artist (not yet in the taste profile)
        // so radio isn't pure exploit. Runs BEFORE spacing so the final spacedByArtist pass still guarantees no
        // same-artist streaks. Phase A #3 — artist-diversity: applied LAST to the unheard pool only (not the
        // heardTail fallback below), so neither taste nor exploration can re-cluster an artist. Both passes are
        // in-memory, order- and length-preserving; null profile / no fresh → identical to today.
        val unheard = keyed.filterNot { it.third }.sortedBy { it.second }.map { it.first }
            .withExplorationQuota(p)
            .spacedByArtist()
        // No fresh candidates left? Fall back to the ordered already-heard tail rather than dead-ending.
        val heardTail = keyed.filter { it.third }.sortedBy { it.second }.map { it.first }
        // NO-REPEAT: when there are ANY unheard candidates, DROP the heard ones entirely (a hard filter, not a
        // soft tail). Only when everything is already heard do we fall back to the heard tail — the last-resort
        // "never dead-end / never silence" guarantee. `index` still dominates each bucket's sort (relatedness
        // order preserved; taste only nudges — we filter, never re-sort).
        return if (unheard.isNotEmpty()) unheard else heardTail
    }

    /** Greedy artist-spacing: keep the incoming (taste/relatedness) order as the base, but when the next item
     *  repeats a primary artist placed in the last 2 slots, skip ahead to the best-ranked item by a different
     *  artist (fallback: take the head). Preserves the backbone, kills same-artist streaks. */
    private fun List<MediaItem>.spacedByArtist(): List<MediaItem> {
        if (size < 3) return this
        val remaining = ArrayList(this)
        val out = ArrayList<MediaItem>(size)
        val recent = ArrayDeque<String>()
        while (remaining.isNotEmpty()) {
            var idx = remaining.indexOfFirst { mi ->
                val a = mi.metadata?.artists?.firstOrNull()?.name?.lowercase()
                a == null || a !in recent
            }
            if (idx < 0) idx = 0
            val pick = remaining.removeAt(idx)
            out.add(pick)
            pick.metadata?.artists?.firstOrNull()?.name?.lowercase()?.let {
                recent.addLast(it); if (recent.size > 2) recent.removeFirst()
            }
        }
        return out
    }

    /**
     * Phase B #4 — exploration quota. Reserve roughly every 5th slot for a "fresh" candidate: one whose primary
     * artist is NOT already in the taste profile ([iad1tya.echo.music.reco.TasteProfile.isKnownArtist]), so radio
     * doesn't tunnel into pure exploitation of artists you already know. Never drops or duplicates anything —
     * output length == input length, and each partition keeps its incoming (taste/relatedness) order. Null profile
     * (no taste yet), lists under 5, or no fresh/known split → returns the list unchanged (today's behaviour).
     * In-memory only, no network, no extra cost.
     */
    private fun List<MediaItem>.withExplorationQuota(p: iad1tya.echo.music.reco.TasteProfile?): List<MediaItem> {
        if (p == null || size < 5) return this
        val known = ArrayList<MediaItem>(size)
        val fresh = ArrayList<MediaItem>()
        for (mi in this) {
            val artist = mi.metadata?.artists?.firstOrNull()?.name
            if (artist != null && !p.isKnownArtist(artist)) fresh.add(mi) else known.add(mi)
        }
        // Nothing to interleave (all known or all fresh) → preserve the existing order exactly.
        if (fresh.isEmpty() || known.isEmpty()) return this
        val out = ArrayList<MediaItem>(size)
        val ki = known.iterator()
        val fi = fresh.iterator()
        var pos = 0
        while (ki.hasNext() || fi.hasNext()) {
            val takeFresh = pos % 5 == 4 && fi.hasNext()
            out.add(if (takeFresh) fi.next() else if (ki.hasNext()) ki.next() else fi.next())
            pos++
        }
        return out
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

    /**
     * Toggleable "No me gusta" for the current song. If the current track is ALREADY disliked, this is an
     * UNDO: it only removes the id from the [dislikeStore] — no skip, no (un)like, and nothing that could
     * re-run loudness normalization mid-song. Otherwise it runs the existing [dislikeCurrentSong] body
     * unchanged (dislike + unlike-if-liked via UPSERT + queue purge + skip).
     */
    fun toggleDislikeCurrentSong() {
        val mediaId = player.currentMediaItem?.mediaId ?: return
        scope.launch {
            val alreadyDisliked = runCatching { dislikeStore.snapshot().songs.contains(mediaId) }
                .getOrDefault(false)
            if (alreadyDisliked) {
                runCatching { dislikeStore.undislikeSong(mediaId) }
            } else {
                // Only dislike if the SAME track is still current (the snapshot read suspends briefly;
                // dislikeCurrentSong re-reads the current item, so a track change mid-await must bail
                // rather than dislike the wrong song).
                if (player.currentMediaItem?.mediaId == mediaId) {
                    dislikeCurrentSong()
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
                // Safe Volume (default ON): drives the live EQ processor's attenuate-only gain.
                val safeVol = withContext(Dispatchers.IO) {
                    dataStore.data.map { it[SafeVolumeEnabledKey] ?: true }.first()
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
        // A per-track Opus override (refetchCurrentInOpus) only applies to the track it was set for; drop it
        // once a genuinely different (non-null) track becomes current so a later track isn't forced to Opus.
        if (forceOpusForMediaId != null && mediaItem != null && mediaItem.mediaId != forceOpusForMediaId) {
            forceOpusForMediaId = null
        }
        // SponsorBlock: fetch skippable non-music segments for the NEW track (YouTube ids only; local songs
        // have long content:// ids and are skipped). Stale responses are ignored inside the manager.
        if (sponsorBlockEnabled) {
            val sbVideoId = sponsorBlock.begin(mediaItem?.mediaId)
            if (sbVideoId != null) {
                scope.launch(Dispatchers.IO) {
                    sponsorBlock.accept(
                        sbVideoId,
                        iad1tya.echo.music.playback.sponsorblock.SponsorBlockService.fetchSegments(sbVideoId),
                    )
                }
            }
        } else {
            sponsorBlock.clear()
        }
        // Sticky video mode. On a track change while video mode is on:
        //  - FAST PATH: if the incoming track was PRE-BUILT as a video (Merging) source ahead of time
        //    (prebuildNextVideoItem), ADOPT it with NO replaceMediaItem/prepare on the now-running track —
        //    that in-place rebuild is exactly what forced STATE_BUFFERING and caused the brief stop the user
        //    reported. Its source is already video+audio, so we only update UI/state and restore the track we
        //    just left back to audio.
        //  - FALLBACK: not pre-built in time (slow resolve), or the incoming item isn't a YouTube video song
        //    (podcast/local/non-video) → on-demand swap (applyVideoToCurrent → brief spinner), exactly as
        //    before. No black screen, no regression.
        if (_videoMode.value && mediaItem != null) {
            val newId = mediaItem.mediaId
            val prebuilt = videoModeItems[newId]
            if (prebuilt != null) {
                _videoUrl.value = prebuilt.videoUrl
                restoreVideoTracksExcept(newId)   // restore previous video track(s) to audio; refreshes single-field state
            } else if (newId != videoModeMediaId) {
                applyVideoToCurrent()
            }
        }
        // PRE-BUILD the NEXT track as a video source NOW (while the current one plays) so the next
        // auto-advance is seamless: the item becomes video BEFORE it is current, so the transition needs no
        // swap on the running track. Reuses videoUrlCache. Only while ACTIVELY in video mode — we deliberately
        // do NOT resolve during normal audio playback: doing it on every track change hammered YouTube with
        // extra stream-resolution requests and got the app rate-limited, which then stalled normal audio.
        // Safe no-op if it can't resolve or the next item isn't a YouTube video song (that case uses the
        // on-demand fallback above).
        if (_videoMode.value) {
            val nextIdx = player.nextMediaItemIndex
            if (nextIdx != C.INDEX_UNSET) {
                val nid = runCatching { player.getMediaItemAt(nextIdx).mediaId }.getOrNull()
                if (nid != null) prebuildNextVideoItem(nextIdx, nid)
            }
        }
        // INSTANT VIDEO SWAP: any pre-prepared player was built for the PREVIOUS track — release it, then
        // (if the expanded player is still up, in audio mode) re-attempt for the NEW track after a short
        // delay so it never competes with this track's own startup buffering. For a cached URL the delayed
        // attempt succeeds directly; for a miss, prefetchCurrentVideoUrl below re-triggers it on resolve.
        teardownInstantVideoSwap("media item transition")
        if (!_videoMode.value && playerSheetExpanded) {
            scheduleInstantVideoPrepare(INSTANT_VIDEO_PREPARE_DELAY_MS)
        }
        // AUDIO→VIDEO toggle speed: when video mode is OFF (this fires on the initial track of a queue and on
        // every track change), proactively pre-resolve THIS track's video URL into videoUrlCache in the
        // background so a subsequent on-demand toggle swaps near-instantly (cache hit, no network wait). Fully
        // self-gated + fire-and-forget on IO; a no-op that never affects audio when video was never used.
        prefetchCurrentVideoUrl()

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

        
        if (autoLoadMoreHint &&
            reason != Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT &&
            player.mediaItemCount - player.currentMediaItemIndex <= 5 &&
            currentQueue.hasNextPage() &&
            !(disableLoadMoreWhenRepeatAllHint && player.repeatMode == REPEAT_MODE_ALL)
        ) {
            // Captured on the player thread: what's currently playing, so autoplay can stay in the same
            // style instead of drifting. Title/artist/album are kept SEPARATE (not pre-joined) because the
            // lane now also needs the primary ARTIST on its own to look up its real genre.
            val curItem = player.currentMediaItem
            val curTitle = curItem?.mediaMetadata?.title?.toString()
            val curArtist = curItem?.mediaMetadata?.artist?.toString()
            val curAlbum = curItem?.mediaMetadata?.albumTitle?.toString()
            // The STRUCTURED artist list (not the joined byline) — only this is usable as a genre-cache key.
            val curArtists = curItem?.metadata?.artists.orEmpty().map { it.name }
            val keepLane = keepGenreLaneHint
            scope.launch(SilentHandler) {
                val disliked = runCatching { dislikeStore.snapshot() }.getOrDefault(iad1tya.echo.music.dislike.DislikeStore.Disliked())
                val mediaItems = withContext(Dispatchers.IO) {
                    // The lane is the REAL genre of what's playing (iTunes genre per artist), not a single
                    // hardcoded style. ONE SharedPreferences read per continuation — never per candidate
                    // (battery), and none at all when the user turned "keep the style" off.
                    val genres = if (keepLane) {
                        runCatching { iad1tya.echo.music.reco.GenreCache.snapshot(this@MusicService) }.getOrDefault(emptyMap())
                    } else {
                        emptyMap()
                    }
                    // Unknown genre -> null lane -> no enforcement at all.
                    val currentLane = if (keepLane) {
                        iad1tya.echo.music.reco.GenreLane.laneOfTrack(genres, curArtist, curTitle, curAlbum)
                    } else {
                        null
                    }
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
                    //
                    // Two DIFFERENT strictnesses, because the two lane signals have opposite blind spots:
                    //  - CHRISTIAN comes from keywords over the track's own text, so it needs no cache and an
                    //    unknown candidate is, in practice, secular -> keep the ORIGINAL strict "must match".
                    //  - A genre lane comes from GenreCache, which is only enriched with artists from YOUR
                    //    library (HomeViewModel), so a brand-new radio artist is unknown by construction.
                    //    Requiring a match there would drop every unknown candidate and collapse autoplay onto
                    //    library artists (repetitive, no discovery). So we only drop candidates whose genre we
                    //    KNOW and know to be different; unknown stays eligible.
                    if (currentLane != null) {
                        val strictLane = currentLane == iad1tya.echo.music.reco.GenreLane.CHRISTIAN
                        val inLane = next.filter { mi ->
                            val lane = iad1tya.echo.music.reco.GenreLane.laneOfTrack(
                                genres,
                                mi.mediaMetadata.artist?.toString(),
                                mi.mediaMetadata.title?.toString(),
                                mi.mediaMetadata.albumTitle?.toString(),
                            )
                            if (strictLane) lane == currentLane else lane == null || lane == currentLane
                        }
                        if (inLane.size >= 2) next = inLane
                    }
                    // NO-REPEAT (Path A hard dedupe): the primary radio pagination NEVER deduped, so YouTube
                    // RD… continuations re-surfaced the seed & earlier songs → guaranteed repeats. Hard-drop
                    // anything already played/queued this session. If this empties the batch we append NOTHING
                    // (the guard below no-ops) and leave hasNextPage untouched, so the next transition pulls the
                    // next page; the STATE_ENDED net re-seeds if the pages ever run truly dry. Never a repeat.
                    next = next.filterNot { it.mediaId in sessionPlayedIds }
                    // Phase A #2 — route the steady-state continuation through orderedByTaste() too, so it is
                    // taste-ordered + artist-spaced (spacedByArtist) rather than raw YouTube order. We're inside
                    // withContext(Dispatchers.IO) so calling the suspend member is fine; it re-dedupes/dislike-
                    // filters (harmless after the manual filters above) and preserves the relatedness backbone.
                    next = next.orderedByTaste()
                    next
                }
                if (player.playbackState != STATE_IDLE && mediaItems.isNotEmpty()) {
                    player.addMediaItems(mediaItems)
                    sessionPlayedIds.addAll(mediaItems.mapNotNull { it.mediaId }) // NO-REPEAT: record what we appended
                    if (player.shuffleModeEnabled) {
                        val shufflePlaylistFirst = dataStore.get(ShufflePlaylistFirstKey, false)
                        applyShuffleOrder(player.currentMediaItemIndex, player.mediaItemCount, shufflePlaylistFirst)
                    }
                }
                // Learn the genre of the artists autoplay actually surfaces, so the lane above stops being blind
                // outside the library. Fire-and-forget AFTER the items are queued (never delays playback), WiFi
                // ONLY, bounded, and misses are cached as "unknown" — so the cost decays to zero as it fills in.
                //
                // Dispatchers.IO is NOT optional: `scope` is Main (the player's looper), so without it the WiFi
                // check (a binder IPC) and every iTunes resumption would run on the playback thread.
                //
                // Feed it INDIVIDUAL artist names (mediaMetadata.artist is a ", "-joined byline): iTunes is
                // queried with attribute=artistTerm, so "Bad Bunny, Chencho Corleone" can only ever MISS — and a
                // miss is cached forever, so we'd permanently burn a request per collab while never learning
                // either artist. GenreCache is keyed by ONE artist name, which is what the lane looks up.
                if (keepLane && mediaItems.isNotEmpty()) {
                    scope.launch(Dispatchers.IO + SilentHandler) {
                        val names = (curArtists + mediaItems.flatMap { it.metadata?.artists.orEmpty() }.map { it.name })
                            .filter { it.isNotBlank() }
                            .distinct()
                            .take(GENRE_LEARN_PER_RUN)
                        if (names.isNotEmpty()) {
                            runCatching {
                                iad1tya.echo.music.reco.GenreCache.enrich(this@MusicService, names, onlyWifi = true)
                            }
                        }
                    }
                }
            }
        }

        // B3 — keep the music going when a FINITE queue ends. Album / artist-top / new-release radar / imported
        // list / single-song queues have no next page, so as soon as the LAST item becomes current (i.e. the last
        // song STARTS playing) we PRE-SEED a radio from that song (its YouTube relations, taste-ordered if any
        // history exists — so it works even on a fresh empty install), OFF the player thread. Fetching + appending
        // while the last song is still playing makes the infinite queue VISIBLE during that song and buffers the
        // next stream BEFORE it ends → no gap / micro-cut at the transition.
        // Fires on ANY transition reason EXCEPT a pure REPEAT (so it also covers the user manually STARTING a
        // finite artist queue or SEEKING onto the last item — not only AUTO auto-advance / PLAYLIST_CHANGED).
        // Still guarded: only the very last item to PLAY (shuffle/repeat-aware, so the list is never truncated),
        // only while actually playing, never twice for the same end (radioSeedInFlight), and skipped while the
        // first block already handles continuation (next page).
        if (autoLoadMoreHint &&
            reason != Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT &&
            player.playWhenReady &&
            !radioSeedInFlight &&
            !currentQueue.hasNextPage() &&
            player.mediaItemCount > 0 &&
            !player.hasNextMediaItem() &&  // shuffle/repeat-aware "on the last item to PLAY" (not a raw timeline index)
            !(disableLoadMoreWhenRepeatAllHint && player.repeatMode == REPEAT_MODE_ALL)
        ) {
            // Autoplay chips: this last-item moment is the seed of the upcoming radio — surface the
            // suggestion chips for it right away (once-per-seed cached; local/http ids no-op inside).
            player.currentMediaItem?.mediaId?.let { refreshAutoplaySuggestions(it) }
            startRadioSeamlessly()
        }


        if (persistentQueueHint) {
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
    /** Poll the playhead once a second and seek past any SponsorBlock segment it enters. Runs on the player
     *  (main) thread so player access is safe; only started while SponsorBlock is enabled. */
    private fun startSponsorBlockWatcher(): kotlinx.coroutines.Job = scope.launch(Dispatchers.Main) {
        while (isActive) {
            kotlinx.coroutines.delay(1000)
            if (!player.isPlaying) continue
            val pos = player.currentPosition
            val target = sponsorBlock.skipTargetFor(pos) ?: continue
            val duration = player.duration
            val safeTarget = if (duration > 0) minOf(target, duration) else target
            if (safeTarget > pos) player.seekTo(safeTarget)
        }
    }

    /**
     * Reload the CURRENTLY-playing track forcing the Opus (WebM/Opus) audio format, continuing from the
     * current position. Mirrors the in-place reload used for an IP-version change: pin this track to Opus,
     * drop its cached URL and bypass any non-Opus cached bytes, then stop → seek(current) → prepare so the
     * ResolvingDataSource re-resolves it in Opus (the format-change container check is skipped while the
     * bypass flag is set, so no mid-stream throw). Runs on the player (Main) thread via [scope]. Local files
     * and direct-URL (podcast) media have a fixed container and are ignored.
     */
    fun refetchCurrentInOpus() {
        scope.launch {
            val mediaId = player.currentMediaItem?.mediaId ?: return@launch
            if (castConnectionHandler?.isCasting?.value == true) {
                Timber.tag(TAG).d("refetchCurrentInOpus: casting, ignoring (a local reload would not affect the cast stream)")
                return@launch
            }
            if (mediaId.isLocalMediaId() ||
                mediaId.startsWith("http://", ignoreCase = true) ||
                mediaId.startsWith("https://", ignoreCase = true)
            ) {
                Timber.tag(TAG).d("refetchCurrentInOpus: $mediaId is local/direct-URL, ignoring")
                return@launch
            }

            val currentPosition = player.currentPosition
            val currentIndex = player.currentMediaItemIndex
            val wasPlaying = player.isPlaying

            forceOpusForMediaId = mediaId
            bypassCacheForQualityChange.add(mediaId)
            songUrlCache.remove(mediaId)

            player.stop()
            player.seekTo(currentIndex, currentPosition)
            player.prepare()
            if (wasPlaying) player.play()

            Timber.tag(TAG).i("refetchCurrentInOpus: reloading $mediaId in Opus at ${currentPosition}ms")
        }
    }

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
        // #27: do NOT clear awaitingFirstUserPlay here — the restored queue reaches STATE_READY while PAUSED,
        // so clearing on any non-IDLE state would drop the veto during the restore itself. It is cleared only
        // on genuine engagement (real playback via EVENT_IS_PLAYING_CHANGED, a genuine playQueue/widget play,
        // or the app coming to the foreground).

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
                if (!radioSeedInFlight) {
                    startRadioSeamlessly()
                } else {
                    // A head-start (B3) seed is ALREADY running; don't launch a second one (radioSeedInFlight
                    // guard against duplicate/looping seeds). But if that in-flight seed settles having appended
                    // NOTHING, its finally clears radioSeedInFlight + resumeAfterSeed and the player would
                    // dead-end here (stopped, no next item). Arm the idempotent watchdog to re-evaluate once the
                    // seed settles.
                    armRadioResumeWatchdog()
                }
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
            pausedByNetwork = false
            retryJob?.cancel()
            deadEndRecheckJob?.cancel()
            // Playback (re)started with something to play → the dead-end watchdog is moot; drop it so a stale
            // timer can never fire a redundant re-seed later.
            radioResumeWatchdogJob?.cancel()


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

        // Tell OUR error-pause (stopOnError set expectingOwnStopPause right before pausing) apart from a real
        // user/external pause. A pause we did NOT initiate clears pausedByNetwork, so it can never be
        // auto-resumed — this is what makes "a manual pause is never auto-resumed" actually hold. Any resume
        // invalidates a pending expectation so a later real pause can't be misattributed as ours.
        if (!playWhenReady) {
            if (expectingOwnStopPause) {
                expectingOwnStopPause = false
            } else {
                pausedByNetwork = false
            }
        } else {
            expectingOwnStopPause = false
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
                // #27: real audio is now playing → the user genuinely engaged (or an allowed control started
                // it). Drop the restore veto so all external controls work normally from here on. Cleared on
                // isPlaying (not on STATE_READY, which a cold restore reaches while PAUSED).
                awaitingFirstUserPlay = false
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

    // Unresolvable-song dead-end (see YTPlayerUtils.StreamResolutionException, mapped to ERROR_CODE_NO_STREAM
    // in the loader). This is NOT a network error — it must fail fast with a message + skip, never wait/retry
    // or silently pause. Walk the WHOLE cause chain (like getHttpResponseCode): our PlaybackException(NO_STREAM)
    // is thrown from inside the ResolvingDataSource, so media3's Loader wraps it in UnexpectedLoaderException
    // (an IOException) and ExoPlayer wraps THAT again — the NO_STREAM code / StreamResolutionException ends up
    // 2+ levels deep, so a one-level check would miss it and fall through to handleGenericIOError (the exact
    // "stuck / never loads" behavior this fix kills). Anchoring on StreamResolutionException is most robust.
    private fun isNoStreamError(error: PlaybackException): Boolean {
        var cause: Throwable? = error
        while (cause != null) {
            if (cause is YTPlayerUtils.StreamResolutionException) return true
            if (cause is PlaybackException && cause.errorCode == ERROR_CODE_NO_STREAM) return true
            cause = cause.cause
        }
        return false
    }

    // The real, user-facing reason for an unresolvable song lives on the innermost StreamResolutionException
    // (region-locked / premium / members-only / timed-out …), NOT on the top-level ExoPlaybackException whose
    // message is a generic loader string. Walk the chain to recover it so the toast surfaces WHY.
    private fun noStreamReason(error: PlaybackException): String? {
        var cause: Throwable? = error
        while (cause != null) {
            if (cause is YTPlayerUtils.StreamResolutionException) return cause.reason
            if (cause is PlaybackException && cause.errorCode == ERROR_CODE_NO_STREAM) return cause.message
            cause = cause.cause
        }
        return error.message
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
            isNoStreamError(error) && isNetworkConnected.value -> {
                // UNRESOLVABLE SONG dead-end (fix #3): the song genuinely can't be served by any client
                // AND we ARE online (so it's not just the network being down). Surface the reason and SKIP
                // past it (regardless of the AutoSkipNextOnErrorKey toggle) — never silently pause forever,
                // never loop in a fake "no internet" state. When OFFLINE, this branch is skipped and the
                // `!isNetworkConnected.value` branch below waits for the network instead (a resolve failure
                // while offline may just be the outage, not a genuinely unavailable song).
                Timber.tag(TAG).w(error, "Unresolvable song (no stream) for $mediaId: ${noStreamReason(error)}")
                handleUnresolvableSong(mediaId, noStreamReason(error))
                return
            }
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

            !isNetworkConnected.value -> {
                // GENUINELY OFFLINE — wait for the network to come back. waitOnNetworkError() is already
                // bounded (MAX_RETRY_COUNT, then a single-shot re-check), so this never loops forever.
                Timber.tag(TAG).d("Offline — waiting for the network to return")
                waitOnNetworkError()
                return
            }
            isNetworkRelatedError(error) -> {
                // CONNECTED but the error still looks network-ish (fix #2). This is the fake "no internet"
                // trap: a dead deciphered URL / bad content-type keeps failing while we ARE online, so an
                // unbounded wait/retry would loop forever. Bound it PER SONG: count each attempt and, once
                // the per-song limit is hit, treat the song as unrecoverable (skip) instead of looping.
                if (mediaId != null) {
                    incrementRetryCount(mediaId)
                    if (hasExceededRetryLimit(mediaId)) {
                        Timber.tag(TAG).w("Connected but $mediaId keeps failing network-like; giving up (skip)")
                        markSongAsFailed(mediaId)
                        handleFinalFailure()
                        return
                    }
                }
                Timber.tag(TAG).d("Connected but network-like error; bounded retry")
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
            try {
                performAggressiveCacheClear(mediaId)

                val currentIndex = player.currentMediaItemIndex
                player.seekTo(currentIndex, 0)
                player.prepare()

                Timber.tag(TAG).d("Retrying playback for $mediaId after 416 error (from position 0)")
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "handleRangeNotSatisfiableError retry failed")
                reportException(e)
            }
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
            try {
                Timber.tag(TAG).d("Handling page reload error for $mediaId")

                performAggressiveCacheClear(mediaId)

                val currentPosition = player.currentPosition
                val currentIndex = player.currentMediaItemIndex
                player.seekTo(currentIndex, currentPosition)
                player.prepare()

                Timber.tag(TAG).d("Retrying playback for $mediaId after page reload error")
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "handlePageReloadError retry failed")
                reportException(e)
            }
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
            try {
                val currentPosition = player.currentPosition
                val currentIndex = player.currentMediaItemIndex
                player.seekTo(currentIndex, currentPosition)
                player.prepare()

                Timber.tag(TAG).d("Retrying playback for $mediaId after 403 error")
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "handleExpiredUrlError retry failed")
                reportException(e)
            }
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
            try {
                performAggressiveCacheClear(mediaId)

                val currentPosition = player.currentPosition
                val currentIndex = player.currentMediaItemIndex
                player.seekTo(currentIndex, currentPosition)
                player.prepare()

                Timber.tag(TAG).d("Retrying playback for $mediaId after generic IO error")
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "handleGenericIOError retry failed")
                reportException(e)
            }
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

    // Surface + auto-skip an UNSERVEABLE song (fix #3). Shows a brief message with the real reason and
    // SKIPS past the track REGARDLESS of the AutoSkipNextOnErrorKey toggle — an unresolvable song must
    // never silently pause forever or loop in a fake "no internet" state. Runs on the player callback
    // thread (main looper), so Toast is safe here.
    private fun handleUnresolvableSong(mediaId: String?, reason: String?) {
        val base = "Canción no disponible"
        val clean = reason?.trim()?.takeIf {
            it.isNotEmpty() &&
                it != getString(R.string.error_unknown) &&
                it != getString(R.string.error_no_internet) &&
                it != getString(R.string.error_timeout)
        }
        val msg = if (clean != null) "$base: $clean" else base
        runCatching { Toast.makeText(this, msg, Toast.LENGTH_SHORT).show() }
        if (mediaId != null) markSongAsFailed(mediaId)
        skipOnError()
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
        // High-Performance Mode defaults to audio-only. EXCEPTION: on TV/car the user explicitly asked to be able
        // to switch to video on demand — a big screen is where video matters most. So we allow the opt-in there
        // even in perf mode (the video track is resolution-capped by device tier in createExoPlayer, so it stays
        // gama-baja friendly). On non-TV low-end devices in perf mode, video stays blocked (heaviest decode path).
        if (iad1tya.echo.music.utils.PerformanceMode.isOn(this) &&
            !iad1tya.echo.music.utils.DeviceForm.isTvOrCar(this)) {
            if (_videoMode.value) exitVideoMode()
            return
        }
        if (_videoMode.value) {
            exitVideoMode()
        } else {
            userHasUsedVideo = true
            videoSwapMeasureStart() // debug-only latency probe: T0 = the audio→video toggle
            // INSTANT FAST PATH: publish the pre-prepared dual player if (and only if) it is healthy —
            // tryInstantVideoSwap sets _videoMode/_videoUrl itself on success. On ANY doubt it returns
            // false having released the pre-player, and the EXISTING path below runs byte-identically.
            if (!tryInstantVideoSwap()) {
                // Entering video via the NORMAL path → any leftover speculative player must not coexist
                // with the in-place rebuild (teardown rule: video-on via normal path releases it).
                teardownInstantVideoSwap("video mode on via normal path")
                _videoMode.value = true
                applyVideoToCurrent()
            }
            // Also pre-build the NEXT item now so the FIRST auto-advance is already seamless (no track change
            // fires on a plain toggle, so onMediaItemTransition wouldn't otherwise get a chance to pre-build).
            val nextIdx = player.nextMediaItemIndex
            if (nextIdx != C.INDEX_UNSET) {
                runCatching { player.getMediaItemAt(nextIdx).mediaId }.getOrNull()
                    ?.let { prebuildNextVideoItem(nextIdx, it) }
            }
        }
    }

    // NOTE (historical): a prior attempt "pre-swapped" the next item's URI to the video stream while the
    // single videoModeMediaId still pointed at the CURRENT track. Because that id wasn't recognised as a
    // video item, createMediaSource built it via the DEFAULT factory, whose audio ResolvingDataSource (keyed
    // on the mediaId) overrode the pre-set video URI back to the AUDIO stream → the next item played
    // audio-only, and swapToVideo then saw uri == url and returned WITHOUT prepare() → blank/frozen video.
    // The fix (prebuildNextVideoItem + the videoModeItems map read in createMediaSource) marks the upcoming
    // id as a video item FIRST, so its source is built through videoFactory (which honours the video URI
    // directly) and the ResolvingDataSource only ever touches the SEPARATE merged audio sub-source.

    /** Resolve the current track's muxed video URL and swap its source in-place (audio is never stopped). */
    private fun applyVideoToCurrent() {
        val item = player.currentMediaItem ?: return
        val id = item.mediaId
        // Restore any OTHER tracked video items (the previous track, or a stale pre-built one) to audio;
        // the current id is about to be (re)swapped to video below.
        restoreVideoTracksExcept(id)

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
        if (!cached.isNullOrEmpty()) {
            videoSwapMark("applyVideoToCurrent: URL cache HIT")
            swapToVideo(id, cached); return
        }
        videoSwapMark("applyVideoToCurrent: URL cache MISS → live resolve")
        scope.launch(Dispatchers.IO) {
            val maxH = videoModeMaxHeight
            var result = runCatching { YTPlayerUtils.videoStreamUrlDiag(id, connectivityManager, maxH) }
                .getOrElse { Result.failure(it) }
            // TV robustness: if 1080p video-only selection failed at runtime, fall back to the default (720p)
            // resolution so video mode never black-screens (no regression vs. phone/tablet behaviour).
            if (maxH != null && result.getOrNull().isNullOrEmpty()) {
                result = runCatching { YTPlayerUtils.videoStreamUrlDiag(id, connectivityManager, null) }
                    .getOrElse { Result.failure(it) }
            }
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
        videoSwapMark("swapToVideo entry")
        val idx = player.currentMediaItemIndex
        val item = player.currentMediaItem ?: return
        if (item.mediaId != id) return
        // Prefer a previously-captured original audio URI (pre-built entry or preload map) so we never store
        // the video URL itself as the "audio" URI for an item that is already showing video.
        val origUri = videoModeItems[id]?.originalAudioUri
            ?: preloadedVideoOriginalUris.remove(id)
            ?: item.localConfiguration?.uri?.toString()
        videoModeOriginalUri = origUri
        videoModeMediaId = id
        // Podcast video is a single muxed stream (has audio) → don't merge a 2nd audio; YouTube is video-only.
        videoModeIsMuxedPodcast = isMuxed
        // Register in the shared map so createMediaSource builds this item's video+audio source (the map, not
        // the single id, is now authoritative there).
        videoModeItems[id] = VideoTrackState(url, origUri, isMuxed)

        if (item.localConfiguration?.uri?.toString() == url) {
            _videoUrl.value = url
            return
        }

        val pos = player.currentPosition
        val playing = player.playWhenReady
        player.replaceMediaItem(idx, item.buildUpon().setUri(url).build())
        // Video swap: seek keyframe-aligned (CLOSEST_SYNC) so the first video frame decodes sooner — an EXACT
        // seek must decode every frame from the previous keyframe up to pos before it can show anything.
        // Restored to DEFAULT (EXACT) immediately so ONLY this swap seek is keyframe-aligned; all audio seeks
        // stay exact. In practice capable-only: video mode is force-off in High-Performance Mode. Audio-only
        // playback never reaches swapToVideo, so the audio path is byte-identical.
        player.setSeekParameters(androidx.media3.exoplayer.SeekParameters.CLOSEST_SYNC)
        player.seekTo(idx, pos)
        player.setSeekParameters(androidx.media3.exoplayer.SeekParameters.DEFAULT)
        player.playWhenReady = playing
        player.prepare()
        _videoUrl.value = url
    }

    /**
     * Restore tracked video items (wherever they sit in the queue) back to their normal audio source,
     * EXCEPT [keepId] (the one that should stay a video source). Pass null to restore ALL (leaving video
     * mode). Only the CURRENT item, if restored, does a prepare(); non-current items are replaced in place
     * with no effect on the running track. Keeps the single-field bookkeeping consistent with what remains.
     */
    private fun restoreVideoTracksExcept(keepId: String?) {
        val toRestore = videoModeItems.keys.filter { it != keepId }
        for (vid in toRestore) {
            val state = videoModeItems.remove(vid) ?: continue
            val origUri = state.originalAudioUri ?: continue
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
        val kept = keepId?.let { videoModeItems[it] }
        if (kept != null) {
            videoModeMediaId = keepId
            videoModeOriginalUri = kept.originalAudioUri
            videoModeIsMuxedPodcast = kept.isMuxedPodcast
        } else {
            videoModeMediaId = null
            videoModeOriginalUri = null
            videoModeIsMuxedPodcast = false
        }
    }

    /**
     * AUTO-ADVANCE fast path: pre-build the UPCOMING item ([nextIdx]/[nextId]) as a video (Merging) source
     * BEFORE it becomes current, so the auto-advance transition needs NO replaceMediaItem/prepare on the
     * running track (that in-place rebuild forced STATE_BUFFERING → the brief stop). We resolve the next
     * track's video URL (reusing videoUrlCache) and, on the main thread, replace ONLY the next (non-current)
     * item's URI with the video URL + register it in [videoModeItems] so createMediaSource builds video+audio
     * directly when media3 preloads/plays that window.
     *
     * Fully guarded so it can never regress audio or the on-demand toggle: only genuine YouTube video songs,
     * NEVER the current/running item, and a graceful no-op if resolution fails or the queue moved — in which
     * case the transition simply falls back to the on-demand swap (applyVideoToCurrent → brief spinner).
     */
    private fun prebuildNextVideoItem(nextIdx: Int, nextId: String) {
        if (nextId.isEmpty() || nextId.isLocalMediaId() || nextId.startsWith("http", ignoreCase = true)) return
        if (videoModeItems.containsKey(nextId)) return // already pre-built as video
        // Only genuine YouTube VIDEO songs can be shown as merged video-only + audio. Anything else
        // (audio-only song, podcast, non-video) falls back to the on-demand path at its own transition.
        val nextMeta = runCatching { player.getMediaItemAt(nextIdx).metadata }.getOrNull()
        if (nextMeta?.isVideoSong != true) return
        if (!prebuildingIds.add(nextId)) return // a resolve for this id is already in flight
        scope.launch(Dispatchers.IO) {
            try {
                val maxH = videoModeMaxHeight
                var url = videoUrlCache[nextId]?.takeIf { it.second > System.currentTimeMillis() }?.first
                if (url.isNullOrEmpty()) {
                    url = runCatching { YTPlayerUtils.videoStreamUrl(nextId, connectivityManager, maxH) }.getOrNull()
                    // TV robustness: if 1080p came back empty, fall back to the default resolution.
                    if (url.isNullOrEmpty() && maxH != null) {
                        url = runCatching { YTPlayerUtils.videoStreamUrl(nextId, connectivityManager, null) }.getOrNull()
                    }
                }
                val resolved = url
                if (resolved.isNullOrEmpty()) return@launch
                videoUrlCache[nextId] = resolved to (System.currentTimeMillis() + 5 * 60 * 1000L)
                withContext(Dispatchers.Main) {
                    // Re-validate on the main thread: still in video mode, the target is still the NEXT item
                    // (never the current/running one — that would re-introduce the stall), not already built.
                    if (!_videoMode.value) return@withContext
                    if (videoModeItems.containsKey(nextId)) return@withContext
                    val idx = player.nextMediaItemIndex
                    if (idx == C.INDEX_UNSET || idx == player.currentMediaItemIndex) return@withContext
                    val item = runCatching { player.getMediaItemAt(idx) }.getOrNull() ?: return@withContext
                    if (item.mediaId != nextId) return@withContext
                    val origUri = item.localConfiguration?.uri?.toString()
                    // Register FIRST so the createMediaSource triggered by replaceMediaItem sees the video
                    // state and builds video+audio (not audio-only — the earlier pre-swap failure).
                    videoModeItems[nextId] = VideoTrackState(resolved, origUri, false)
                    if (origUri != resolved) {
                        // Replace ONLY the upcoming (non-current) item → no STATE_BUFFERING on the running track.
                        player.replaceMediaItem(idx, item.buildUpon().setUri(resolved).build())
                    }
                }
            } finally {
                prebuildingIds.remove(nextId)
            }
        }
    }

    /**
     * AUDIO→VIDEO TOGGLE LATENCY FIX. Proactively resolve the CURRENT (audio) track's video URL into
     * [videoUrlCache] in the BACKGROUND so that when the user later flips the on-demand video toggle,
     * [applyVideoToCurrent] hits the cache (line ~3783) and goes straight to [swapToVideo] with NO synchronous
     * network round-trip (cipher / PoToken / format selection via [YTPlayerUtils.videoStreamUrlDiag] — the
     * dominant, avoidable cost). The perceived toggle latency then drops to just the media3 buffer fill.
     *
     * Fire-and-forget on [Dispatchers.IO]; the ONLY main-thread work is the cheap gate reads below, and it
     * NEVER touches the player/audio graph — a failed resolve is swallowed and the toggle still falls back to
     * the live resolve exactly as today. Gated to avoid the documented rate-limit risk (resolving video for
     * every track once hammered YouTube and stalled audio — see onMediaItemTransition prebuild note):
     *   - video mode currently OFF (a toggle-to-video is only possible from audio; when ON the swap already ran),
     *   - EITHER the user has used video once THIS session ([userHasUsedVideo], in-memory, resets per process)
     *     — prefetch then runs on ANY device — OR, to cover the session's FIRST toggle, the device is CAPABLE
     *     (not High-Performance Mode, not LOW/ULTRA tier) AND fewer than 3 speculative resolves have been
     *     launched this session ([preFirstUseVideoPrefetches]); weak devices never pay the speculative cipher
     *     cost, and audio-only listeners are bounded at <=3 extra resolves/session instead of one per
     *     video-song transition (the fleet-wide traffic pattern behind the old rate-limit incident),
     *   - a genuine YouTube VIDEO song (isVideoSong == true; skips local / http-podcast / audio-only ids), so
     *     pure audio-only queues never trigger a speculative resolve.
     * Idempotent: no-op if a fresh URL is already cached or a resolve for this id is already in flight
     * (shared [prebuildingIds], keyed by a distinct id from the next-item prebuild so they never collide).
     */
    private fun prefetchCurrentVideoUrl() {
        // A toggle-to-video is only possible from audio; when already in video mode the swap has run.
        if (_videoMode.value) return
        // Cheap in-memory checks FIRST: bail on a non-video / local / direct-URL track BEFORE paying for the
        // PerformanceMode reads in the first-toggle gate below (those only matter for a genuine video song).
        val id = player.currentMediaItem?.mediaId ?: return
        if (id.isEmpty() || id.isLocalMediaId() || id.startsWith("http", ignoreCase = true)) return
        if (player.currentMetadata?.isVideoSong != true) return
        // FIRST-TOGGLE COVERAGE (capable devices only, CAPPED at 3 resolves/session). Normally we speculatively
        // resolve only AFTER the user has opened video once this session (userHasUsedVideo). That left the VERY
        // FIRST toggle of a session paying the full synchronous resolve (applyVideoToCurrent cache-miss →
        // cipher/PoToken/format, seconds) PLUS the swap re-buffer → the >5s the user reported. So ALSO
        // pre-resolve BEFORE first use — but ONLY on a CAPABLE device (NOT High-Performance Mode and NOT
        // LOW/ULTRA tier): a weak device must never pay the speculative cipher cost for a feature it may never
        // open — and ONLY for the first 3 launched resolves of the session (preFirstUseVideoPrefetches).
        // The first toggle is usually early in a session, so the cap keeps it instant for real video users
        // while bounding audio-only listeners at <=3 extra resolves/session — WITHOUT the cap this would be
        // one resolve per video-song transition, the exact fleet-wide traffic pattern that once rate-limited
        // the app and stalled normal AUDIO (see the onMediaItemTransition prebuild note). This is a small
        // metadata/cipher resolve (NOT the video bytes), so it runs on any network; on a capable device the
        // URL is then ready the moment the user first taps video. Once userHasUsedVideo is true the existing
        // path is preserved unchanged (prefetch on ANY device, per transition, uncapped).
        val preFirstUse = !userHasUsedVideo
        if (preFirstUse) {
            if (preFirstUseVideoPrefetches >= 3) return
            val perfMode = iad1tya.echo.music.utils.PerformanceMode.isOn(this)
            val tier = iad1tya.echo.music.utils.PerformanceMode.effectiveTier(this)
            val capable = !perfMode &&
                tier != iad1tya.echo.music.utils.DeviceTier.LOW &&
                tier != iad1tya.echo.music.utils.DeviceTier.ULTRA
            if (!capable) return
        }
        // Already resolved and still fresh → the toggle is already instant; nothing to do.
        val cached = videoUrlCache[id]?.takeIf { it.second > System.currentTimeMillis() }?.first
        if (!cached.isNullOrEmpty()) return
        if (!prebuildingIds.add(id)) return // a resolve for this id is already in flight (dedupe)
        // Count only resolves actually LAUNCHED (past the cache/dedupe checks), so cache hits and in-flight
        // dupes never burn the pre-first-use budget.
        if (preFirstUse) preFirstUseVideoPrefetches++
        scope.launch(Dispatchers.IO) {
            try {
                val maxH = videoModeMaxHeight
                var url = runCatching { YTPlayerUtils.videoStreamUrl(id, connectivityManager, maxH) }.getOrNull()
                // TV robustness: if 1080p came back empty, fall back to the default resolution (matches
                // applyVideoToCurrent / prebuildNextVideoItem) so the pre-resolved URL is never black-screened.
                if (url.isNullOrEmpty() && maxH != null) {
                    url = runCatching { YTPlayerUtils.videoStreamUrl(id, connectivityManager, null) }.getOrNull()
                }
                val resolved = url
                // Same TTL as the on-demand resolve → applyVideoToCurrent's cache read accepts it as fresh.
                if (!resolved.isNullOrEmpty()) {
                    videoUrlCache[id] = resolved to (System.currentTimeMillis() + 5 * 60 * 1000L)
                    // If the user is looking at the expanded player right now, also warm the connection
                    // (fully re-gated inside: unmetered + capable + video song + once per URL) and attempt
                    // the instant-swap pre-prepare (same trigger moment; delayed so it never competes with
                    // the just-started track's own buffering; every hard gate re-checked at fire time).
                    if (playerSheetExpanded) {
                        withContext(Dispatchers.Main) {
                            maybeWarmVideoConnection()
                            scheduleInstantVideoPrepare(INSTANT_VIDEO_PREPARE_DELAY_MS)
                        }
                    }
                }
            } finally {
                prebuildingIds.remove(id)
            }
        }
    }

    /** Leaves video mode: restore the current track to audio (playback continues at the same position).
     *  The video→audio path itself is UNTOUCHED by the instant-swap feature; the teardown below only
     *  releases a speculative pre-player (normally none exists while video mode is on — defensive), and
     *  the trailing schedule merely re-arms the speculative pre-prepare for a possible re-toggle. */
    fun exitVideoMode() {
        if (!_videoMode.value && videoModeMediaId == null && videoModeItems.isEmpty()) return
        teardownInstantVideoSwap("exit video mode")
        _videoMode.value = false
        _videoUrl.value = null
        prebuildingIds.clear()
        restoreVideoTracksExcept(null)   // restore ALL tracked video items (current + any pre-built) to audio
        // Re-arm the instant-swap pre-prepare (fully re-gated inside) so toggling video back on soon after
        // is instant again; delayed so it never competes with the audio restore's own re-prepare.
        if (playerSheetExpanded) scheduleInstantVideoPrepare(INSTANT_VIDEO_PREPARE_DELAY_MS)
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

            // FULLY-DOWNLOADED short-circuit — a *complete* downloaded file is container-locked as a whole, so
            // it's always safe to serve WITHOUT the DB container check (its container can't drift with the
            // global quality) and we skip the runBlocking DB read below. PARTIAL downloads are deliberately NOT
            // served here: their tail is still missing, and if the user switched global quality mid-download the
            // tail would arrive in a new container (old-container prefix + new-container tail = garbled /
            // ERROR_CODE_PARSING_CONTAINER_MALFORMED). Partials fall through to the dbFormat container-mismatch
            // guard, then get served (container matches) or bypassed+refetched (mismatch) by the post-guard
            // downloadCache handling below. A playerCache / songUrlCache hit is likewise NOT served here.
            if (!shouldBypassCache && isFullyDownloaded) {
                if (downloadCache.isCached(
                        mediaId,
                        dataSpec.position,
                        if (dataSpec.length >= 0) dataSpec.length else 1
                    )
                ) {
                    scope.launch(Dispatchers.IO) { recoverSong(mediaId) }
                    return@Factory dataSpec
                }
            }

            // Read Room NOW — BEFORE serving any playerCache/songUrlCache hit — for the locked-quality /
            // container decisions the fetch below needs AND the container-mismatch guard.
            val dbFormat = runBlocking(Dispatchers.IO) { database.format(mediaId).firstOrNull() }

            // refetchCurrentInOpus() forces this track to Opus, overriding both the global quality and the
            // "locked" container of the currently-playing track (below).
            val forceOpus = forceOpusForMediaId == mediaId
            val lockedQuality = when {
                forceOpus -> iad1tya.echo.music.constants.AudioQuality.OPUS
                isCurrentlyPlaying && dbFormat != null -> when {
                    dbFormat.mimeType.contains("flac", ignoreCase = true) -> iad1tya.echo.music.constants.AudioQuality.LOSSLESS
                    dbFormat.mimeType.contains("mp4", ignoreCase = true) || dbFormat.mimeType.contains("m4a", ignoreCase = true) -> iad1tya.echo.music.constants.AudioQuality.SAAVN
                    else -> iad1tya.echo.music.constants.AudioQuality.OPUS
                }
                else -> audioQuality
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

            // CONTAINER-CHECKED cache hit — only NOW serve a partial-download / playerCache / songUrlCache
            // entry. The container-mismatch guard above has either confirmed the cached container matches the
            // target quality or set shouldBypassCache (and ghost-removed the mismatched playerCache entry) to
            // force a fresh fetch. So no cache hit is ever served with a container mismatch (garbled audio).
            if (!shouldBypassCache) {
                // PARTIAL download whose container the guard above confirmed matches the target quality — serve
                // the cached bytes (the CacheDataSource fills the missing tail from the network in the same,
                // matching container). A mismatching partial set shouldBypassCache above and skips this block.
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
                    // FIX C (#28.2): cached BYTES are present but we have no fresh stream URL (e.g. after an
                    // app-update restart, when songUrlCache started empty). Do NOT delete the cached bytes and
                    // force a full re-download — that was the "ghost cache" churn that made every song slow
                    // after an update (and why "clear song cache" wrongly seemed to help). Instead KEEP the
                    // cached bytes and fall through to re-resolve ONLY the URL below; the fresh URI is stored in
                    // songUrlCache and returned, and the CacheDataSource serves the cached bytes while fetching
                    // just the missing tail from the refreshed URI.
                    Timber.tag(TAG).w("Ghost cache entry for $mediaId — keeping cached bytes, re-resolving URL only")
                }

                songUrlCache[mediaId]?.takeIf { it.second > System.currentTimeMillis() }?.let {
                    scope.launch(Dispatchers.IO) { recoverSong(mediaId) }
                    return@Factory dataSpec.withUri(it.first.toUri())
                }
            }

            if (shouldBypassCache) {
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
                    // UNRESOLVABLE SONG dead-end (fix #1): region-locked, premium/members-only,
                    // deleted-but-listed, age-restricted-for-guests, no playable format/URL, or the
                    // resolution timed out. This is NOT a network problem — map it to NO_STREAM carrying
                    // the real reason, so onPlayerError skips the song with a message instead of looping
                    // forever in a fake "no internet" state. NEVER map this to a network code.
                    is iad1tya.echo.music.utils.YTPlayerUtils.StreamResolutionException -> {
                        throw PlaybackException(
                            throwable.reason,
                            throwable,
                            ERROR_CODE_NO_STREAM
                        )
                    }

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
                // FIX B1 (#28.1): persist the freshly-resolved URL (whole cache snapshot) so it survives a
                // restart / app update. Off the main thread; never blocks this resolve.
                persistSongUrlCache()

                return@Factory dataSpec.withUri(streamUrl.toUri())
            }
        }
    }

    // Data source for the VIDEO-mode track: a plain OkHttp source that sets the correct per-client
    // User-Agent for googlevideo URLs (they 403 without it). No app cache (avoids colliding with the
    // audio cache and re-streams cleanly on seek). This is the fix that makes video render on the MAIN
    // player (feeding the video URL through the normal audio data source never worked).
    // The OkHttpClient is held separately so maybeWarmVideoConnection can pre-open the SAME pooled
    // TCP+TLS connection the swap will use (OkHttp reuses pooled connections per host).
    private val videoOkHttpClient: okhttp3.OkHttpClient by lazy {
        okhttp3.OkHttpClient.Builder()
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
    }

    private val videoDataSourceFactory: DataSource.Factory by lazy {
        DefaultDataSource.Factory(
            this,
            androidx.media3.datasource.okhttp.OkHttpDataSource.Factory(videoOkHttpClient)
        )
    }

    // Video URLs whose googlevideo connection we've already warmed this session (once per exact URL —
    // a rotated/expired URL is a new host/params and may warm again). Bounded by the once-per-URL set;
    // there is no timer and no retry loop.
    private val warmedVideoUrls = java.util.Collections.synchronizedSet(mutableSetOf<String>())

    /**
     * VIDEO-SWAP CONNECTION WARM-UP (safe core of the "instant video toggle" work). When the user is
     * looking at the expanded player over a video song whose video URL is already resolved, pre-open the
     * TCP+TLS connection to its googlevideo host with a single 1-byte Range request through
     * [videoOkHttpClient] — the SAME pooled client the swap's data source uses — so a subsequent toggle
     * skips the cold-connection cost (~200-500 ms of the observed swap latency). It transfers ONE byte,
     * runs at most once per URL, and never touches the player/audio graph.
     *
     * HARD GATES (all must hold — heat/battery rule: speculative network only on unmetered + capable):
     * player sheet EXPANDED, video mode OFF, no crossfade swap in flight, current track is a video song,
     * URL already in [videoUrlCache] (we never resolve here), network UNMETERED, device capable
     * (no High-Performance Mode, tier not LOW/ULTRA). Must be called on the main thread (player access).
     */
    private fun maybeWarmVideoConnection() {
        if (!playerSheetExpanded) return
        if (_videoMode.value) return
        if (isCrossfading) return
        if (!playerInitialized.value) return
        val id = player.currentMediaItem?.mediaId ?: return
        if (id.isEmpty() || id.isLocalMediaId() || id.startsWith("http", ignoreCase = true)) return
        if (player.currentMetadata?.isVideoSong != true) return
        val url = videoUrlCache[id]?.takeIf { it.second > System.currentTimeMillis() }?.first ?: return
        if (url in warmedVideoUrls) return
        if (runCatching { connectivityManager.isActiveNetworkMetered }.getOrDefault(true)) return
        val perfMode = iad1tya.echo.music.utils.PerformanceMode.isOn(this)
        val tier = iad1tya.echo.music.utils.PerformanceMode.effectiveTier(this)
        if (perfMode ||
            tier == iad1tya.echo.music.utils.DeviceTier.LOW ||
            tier == iad1tya.echo.music.utils.DeviceTier.ULTRA
        ) return
        if (!warmedVideoUrls.add(url)) return // raced by another caller — already warming
        scope.launch(Dispatchers.IO) {
            runCatching {
                val request = okhttp3.Request.Builder()
                    .url(url)
                    .header("Range", "bytes=0-0")
                    .build()
                videoOkHttpClient.newCall(request).execute().use { response ->
                    // BOUNDED drain — never body.bytes(): a server that ignores the Range and replies
                    // 200 would hand us the WHOLE video as one heap ByteArray (OOM + data burn on a
                    // speculative call). Read at most ~2 KB: a proper 206 to bytes=0-0 is 1 byte and
                    // hits EOF (body exhausted → connection returns to the pool, which is the whole
                    // point of the warm-up); anything bigger is abandoned and use{} closes it.
                    response.body.source().let { source ->
                        val blackhole = okio.Buffer()
                        var drained = 0L
                        while (drained < 2048) {
                            val read = source.read(blackhole, 1024)
                            if (read == -1L) break
                            drained += read
                            blackhole.clear()
                        }
                    }
                }
                Timber.tag(TAG).d("Video connection warmed for $id")
            }.onFailure {
                // Allow one later re-attempt for this URL (e.g. transient DNS blip).
                warmedVideoUrls.remove(url)
            }
        }
    }

    /** Debounced trigger for the instant-video pre-prepare. Cancels any pending attempt; the gates are
     *  (re)checked on the main thread at fire time, so a stale schedule can never prepare wrongly. */
    private fun scheduleInstantVideoPrepare(delayMs: Long = 0L) {
        if (!INSTANT_VIDEO_SWAP_ENABLED) return
        instantVideoPrepareJob?.cancel()
        instantVideoPrepareJob = scope.launch {
            if (delayMs > 0) delay(delayMs)
            maybePrepareInstantVideoSwap()
        }
    }

    /**
     * INSTANT VIDEO SWAP pre-prepare. Builds a muted, paused SECONDARY ExoPlayer around the CURRENT
     * track's merged video+audio source (the exact MergingMediaSource path createMediaSource builds for
     * video items) so a later toggleVideoMode can publish it with no in-place rebuild of the running item
     * (that rebuild is the audio halt in the normal path). Speculative work → every gate is HARD:
     *
     *  1. [INSTANT_VIDEO_SWAP_ENABLED] kill switch on.
     *  2. Player sheet EXPANDED (the only moment a video toggle is plausible) + screen interactive
     *     + not in OS battery saver (heat/battery: no speculative work when invisible/saving).
     *  3. Video mode OFF (when ON the swap already happened) and the main player initialized.
     *  4. NO crossfade machinery live (isCrossfading / fadingPlayer / secondaryPlayer) and not within
     *     [INSTANT_VIDEO_CROSSFADE_MARGIN_MS] of the crossfade preload moment → max 2 players, ever.
     *  5. Current track is a genuine YouTube VIDEO song (not local/http/podcast/audio-only).
     *  6. Video URL already resolved in [videoUrlCache] (this function NEVER resolves).
     *  7. Network UNMETERED (speculative ~video-bitrate buffering must never touch mobile data).
     *  8. Device capable: no High-Performance Mode, tier not LOW/ULTRA (TV/low tiers excluded).
     *
     * Idempotent: a healthy pre-player for the same id+URL is kept as-is. Main thread only.
     */
    private fun maybePrepareInstantVideoSwap() {
        if (!INSTANT_VIDEO_SWAP_ENABLED) return
        if (!playerSheetExpanded) return
        if (_videoMode.value) return
        if (!playerInitialized.value) return
        // Crossfade coexistence guard (direction 1: don't prepare while crossfade machinery is live).
        // Direction 2 is in prepareSecondaryPlayer, which tears the pre-player down before building its own.
        if (isCrossfading || fadingPlayer != null || secondaryPlayer != null) return
        if (crossfadeEnabled && !highPerformanceModeHint) {
            val dur = player.duration
            if (dur != C.TIME_UNSET && dur > crossfadeDuration) {
                val preloadAt = dur - crossfadeDuration.toLong() - CROSSFADE_PRELOAD_LEAD_MS
                if (player.currentPosition >= preloadAt - INSTANT_VIDEO_CROSSFADE_MARGIN_MS) return
            }
        }
        val item = player.currentMediaItem ?: return
        val id = item.mediaId
        if (id.isEmpty() || id.isLocalMediaId() || id.startsWith("http", ignoreCase = true)) return
        if (player.currentMetadata?.isVideoSong != true) return
        val url = videoUrlCache[id]?.takeIf { it.second > System.currentTimeMillis() }?.first ?: return
        if (runCatching { connectivityManager.isActiveNetworkMetered }.getOrDefault(true)) return
        val perfMode = iad1tya.echo.music.utils.PerformanceMode.isOn(this)
        val tier = iad1tya.echo.music.utils.PerformanceMode.effectiveTier(this)
        if (perfMode ||
            tier == iad1tya.echo.music.utils.DeviceTier.LOW ||
            tier == iad1tya.echo.music.utils.DeviceTier.ULTRA
        ) return
        if ((getSystemService(POWER_SERVICE) as? android.os.PowerManager)?.isPowerSaveMode == true) return
        if ((getSystemService(POWER_SERVICE) as? android.os.PowerManager)?.isInteractive == false) return
        // Already prepared for this exact id+URL and not errored → keep it (idempotent re-trigger).
        instantVideoPlayer?.let { existing ->
            if (instantVideoPlayerId == id && instantVideoPlayerUrl == url &&
                existing.playbackState != Player.STATE_IDLE
            ) return
            releaseInstantVideoPlayer("stale pre-player (track/url changed)")
        }
        // The merged source needs the item's normal AUDIO uri to merge back in; without it we can't build.
        val origUri = item.localConfiguration?.uri?.toString() ?: return

        // Register FIRST (separate map + URI-match guard in createMediaSource) so the pre-player's
        // setMediaItem builds the MergingMediaSource (video-only + normal audio), never audio-only.
        instantSwapItems[id] = VideoTrackState(url, origUri, false)
        var pre: ExoPlayer? = null
        try {
            pre = createExoPlayer(isSecondary = true)
            pre.addListener(instantVideoPlayerListener)
            pre.setMediaItem(item.buildUpon().setUri(url).build())
            // Keyframe-aligned seeks for the whole pre-prepare life (mirrors swapToVideo's CLOSEST_SYNC swap
            // seek); restored to DEFAULT right after the publish seek in tryInstantVideoSwap.
            pre.setSeekParameters(androidx.media3.exoplayer.SeekParameters.CLOSEST_SYNC)
            val pos = player.currentPosition
            pre.seekTo(pos)
            pre.volume = 0f
            pre.playWhenReady = false
            // Safe Volume is per-EQ-processor state (no companion static): mirror the CURRENT track's applied
            // gain onto the pre-player so the published player is level-identical from its first sample.
            // Norm/limiter need nothing: their processors follow the companion statics, which already hold
            // this same track's values (never re-normalizes — no instanceGain pin, so nothing to clean up).
            if (safeVolumeEnabledHint) playerEqProcessors[pre]?.applySafeVolume(true, lastAppliedGain)
            pre.prepare()
            instantVideoPlayer = pre
            instantVideoPlayerId = id
            instantVideoPlayerUrl = url
            instantVideoPreparedAtPosMs = pos
            Timber.tag(TAG).d("Instant-video pre-player preparing for $id @ $pos ms")
        } catch (e: Exception) {
            // Speculative only — never let a failed prepare leak a player or the factory registration.
            Timber.tag(TAG).w(e, "Instant-video pre-prepare failed (normal path unaffected)")
            instantSwapItems.clear()
            instantVideoPlayer = null
            instantVideoPlayerId = null
            instantVideoPlayerUrl = null
            pre?.let { p ->
                p.removeListener(instantVideoPlayerListener)
                playerSilenceProcessors.remove(p)
                playerNormProcessors.remove(p)
                playerLimiterProcessors.remove(p)
                playerEqProcessors.remove(p)?.let { eq -> equalizerService.removeAudioProcessor(eq) }
                runCatching { p.release() }
            }
        }
    }

    /**
     * INSTANT VIDEO SWAP fast path (called ONLY from toggleVideoMode, audio→video direction). Publishes
     * the pre-prepared player exactly like performCrossfadeSwap publishes the crossfade secondary:
     * seek to the LIVE position first, adopt the live queue, swap the player reference, republish
     * [_playerFlow] (the UI re-attaches the TextureView to the new player), move MediaSession/SleepTimer,
     * restore volume/play state, then release the old player. Returns true only when fully published;
     * ANY doubt (gates below, or any pre-commit exception) releases the pre-player and returns false so
     * the caller falls through to the EXISTING swapToVideo path unchanged.
     */
    private fun tryInstantVideoSwap(): Boolean {
        if (!INSTANT_VIDEO_SWAP_ENABLED) return false
        val pre = instantVideoPlayer ?: return false
        val id = instantVideoPlayerId
        val url = instantVideoPlayerUrl
        // --- Swap-time health gates (ANY failure → release + normal path) ---
        val old = player
        val item = old.currentMediaItem
        if (id == null || url == null || item == null || item.mediaId != id ||
            isCrossfading || fadingPlayer != null || secondaryPlayer != null ||
            pre.playbackState != Player.STATE_READY ||
            pre.currentMediaItem?.mediaId != id
        ) {
            releaseInstantVideoPlayer("swap gates failed (not READY / track changed / crossfade live)")
            return false
        }
        val livePos = old.currentPosition
        val buffered = runCatching { pre.bufferedPosition }.getOrDefault(0L)
        // Lazy position sync: valid only inside the window the paused pre-player actually buffered.
        if (livePos < instantVideoPreparedAtPosMs ||
            livePos + INSTANT_VIDEO_MIN_BUFFER_AHEAD_MS > buffered
        ) {
            releaseInstantVideoPlayer("live position outside pre-buffered window")
            return false
        }
        // MED (shuffle order): the instant path rebuilds the queue in TIMELINE order (addMediaItems around
        // the current period), which does NOT carry the live shuffle order. With shuffle ON, skip the fast
        // path entirely and let the normal swapToVideo path — which keeps the running player and its shuffle
        // order intact — handle the toggle instead.
        if (old.shuffleModeEnabled) {
            releaseInstantVideoPlayer("shuffle enabled — preserve order via normal path")
            return false
        }
        var committed = false
        return try {
            // ---- PRE-COMMIT (old player untouched; a throw here falls back with zero side effects) ----
            val playing = old.playWhenReady
            pre.seekTo(livePos) // keyframe-aligned (CLOSEST_SYNC since prepare), same as swapToVideo's seek
            pre.setSeekParameters(androidx.media3.exoplayer.SeekParameters.DEFAULT)
            // Adopt the LIVE queue around the already-playing period (addMediaItems never rebuilds it),
            // so the published player has the exact up-to-date queue — no stale-copy divergence.
            val curIdx = old.currentMediaItemIndex
            if (curIdx > 0) {
                pre.addMediaItems(0, (0 until curIdx).map { old.getMediaItemAt(it) })
            }
            if (curIdx + 1 < old.mediaItemCount) {
                pre.addMediaItems((curIdx + 1 until old.mediaItemCount).map { old.getMediaItemAt(it) })
            }
            pre.repeatMode = old.repeatMode
            pre.shuffleModeEnabled = old.shuffleModeEnabled
            // Hand the registration over to the normal video bookkeeping, exactly as swapToVideo does —
            // from here on, transitions / exitVideoMode / error recovery see the standard video state.
            val state = instantSwapItems.remove(id) ?: VideoTrackState(url, item.localConfiguration?.uri?.toString(), false)
            videoModeItems[id] = state
            videoModeMediaId = id
            videoModeOriginalUri = state.originalAudioUri
            videoModeIsMuxedPodcast = false

            // HIGH fix: unconditionally re-assert the CURRENT Safe Volume state onto the pre-player right
            // before publish (mirrors the per-track re-assert at ~line 2801). This guarantees the published
            // player is level-identical to the running one at the swap instant — never a mid-song level jump
            // even if lastAppliedGain / the Safe Volume toggle changed between pre-prepare and this swap.
            playerEqProcessors[pre]?.applySafeVolume(safeVolumeEnabledHint, if (safeVolumeEnabledHint) lastAppliedGain else 1f)

            // ---- COMMIT: publish (mirrors performCrossfadeSwap's swap block) ----
            committed = true
            instantVideoPlayer = null
            instantVideoPlayerId = null
            instantVideoPlayerUrl = null
            instantVideoPreparedAtPosMs = 0L
            old.removeListener(this)
            old.removeListener(sleepTimer)
            pre.removeListener(instantVideoPlayerListener)
            pre.addListener(this)
            pre.addListener(sleepTimer)
            player = pre
            _playerFlow.value = pre // UI (PlayerVideoSurface/MiniPlayer/PiP) re-attaches the surface here
            sleepTimer.player = pre
            try {
                (mediaSession as MediaSession).player = pre
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "Instant-video swap: failed to swap player in MediaSession")
            }
            pre.volume = if (isMuted.value) 0f else playerVolume.value
            pre.playWhenReady = playing
            _videoMode.value = true
            _videoUrl.value = url
            // Old player: silence, detach its surface, full release (mirrors cleanupCrossfade's teardown).
            runCatching {
                old.volume = 0f
                old.stop()
                old.clearMediaItems()
                old.clearVideoSurface()
            }
            playerSilenceProcessors.remove(old)
            playerNormProcessors.remove(old)
            playerLimiterProcessors.remove(old)
            playerEqProcessors.remove(old)?.let { eq -> equalizerService.removeAudioProcessor(eq) }
            runCatching { old.release() }
            videoSwapMark("INSTANT swap published (pre-prepared dual player)")
            true
        } catch (e: Exception) {
            if (!committed) {
                // Nothing was published — undo any bookkeeping and fall back to the normal path.
                videoModeItems.remove(id)
                videoModeMediaId = null
                videoModeOriginalUri = null
                releaseInstantVideoPlayer("exception before publish: ${e.message}")
                false
            } else {
                // Already published; the new player IS the player. Log and carry on (normal video mode).
                Timber.tag(TAG).e(e, "Instant-video swap: post-publish exception (continuing on new player)")
                reportException(e)
                true
            }
        }
    }

    /** Release the speculative pre-player + its processor bookkeeping + the factory registration.
     *  No-op when nothing is prepared. Never touches the main player, crossfade, or video state. */
    private fun releaseInstantVideoPlayer(reason: String) {
        val pre = instantVideoPlayer
        instantVideoPlayer = null
        instantVideoPlayerId = null
        instantVideoPlayerUrl = null
        instantVideoPreparedAtPosMs = 0L
        instantSwapItems.clear()
        if (pre == null) return
        Timber.tag(TAG).d("Instant-video pre-player released: $reason")
        pre.removeListener(instantVideoPlayerListener)
        playerSilenceProcessors.remove(pre)
        playerNormProcessors.remove(pre)
        playerLimiterProcessors.remove(pre)
        playerEqProcessors.remove(pre)?.let { eq -> equalizerService.removeAudioProcessor(eq) }
        runCatching {
            pre.stop()
            pre.clearMediaItems()
            pre.release()
        }
    }

    /** Full teardown: cancel any pending pre-prepare AND release the pre-player. Called on sheet collapse,
     *  track transition, network→metered, video-on via the normal path, crossfade preload, destroy. */
    private fun teardownInstantVideoSwap(reason: String) {
        instantVideoPrepareJob?.cancel()
        instantVideoPrepareJob = null
        releaseInstantVideoPlayer(reason)
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
                // Video mode: the track's item URI was set (swapToVideo for the current track, or
                // prebuildNextVideoItem for an UPCOMING one) to an adaptive VIDEO-ONLY HD stream. MERGE it
                // with the track's normal AUDIO source (resolved from the original URI via the
                // default/ResolvingDataSource factory) → HD video + the app's normal audio, synced on the
                // same video timeline, no double audio. clipDurations tolerates a tiny end-of-stream length
                // difference between the two streams.
                //
                // Keyed off the SET/MAP (not the single videoModeMediaId) so ANY tracked video item builds a
                // video source — including a pre-built next item that becomes current with NO swap on the
                // running track. The video URI on the item is used DIRECTLY by videoFactory (plain OkHttp), so
                // it is never overwritten by the audio ResolvingDataSource (keyed on the mediaId) — that
                // overwrite is exactly why the earlier "pre-swap the next item" attempt built it audio-only.
                // The ResolvingDataSource only ever resolves the SEPARATE merged audio sub-source below.
                // INSTANT VIDEO SWAP pre-prepare: the speculative registration lives in a SEPARATE map and
                // only applies when the item's URI IS the registered video URL — true only for the
                // pre-player's own item. The main player's audio item keeps its audio URI, so an audio-path
                // rebuild of the same id can never match → the audio pipeline is byte-identical (and with
                // the feature idle both maps miss, falling straight through to the default factory).
                val vstate = videoModeItems[mediaItem.mediaId]
                    ?: instantSwapItems[mediaItem.mediaId]?.takeIf {
                        it.videoUrl == mediaItem.localConfiguration?.uri?.toString()
                    }
                if (vstate != null) {
                    val videoSource = videoFactory.createMediaSource(mediaItem)
                    val origUri = vstate.originalAudioUri
                    // Merge a separate audio source ONLY for genuinely video-only streams (YouTube). A muxed
                    // podcast already has audio — merging would add a redundant/conflicting 2nd audio track.
                    if (origUri != null && !vstate.isMuxedPodcast) {
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
            ) = run {
                // Float (32-bit) output is the pure path, but on genuinely low-end/TV/low-RAM devices some
                // weak audio HALs mis-handle float output and play back accelerated/pitched-up. Fall back to
                // the integer path there — same low-end signal as the buffer profile in createExoPlayer. Keep
                // float ON for MID/HIGH.
                // NOTE: the media3 `enableFloatOutput` param is always false here (this factory never calls
                // setEnableAudioFloatOutput), so gating on it would disable float everywhere and break the
                // 32-bit path on capable devices. Gate on the RAW HARDWARE tier instead: float ON unless the
                // hardware is genuinely low-end. Deliberately NOT gated on High-Performance Mode / effectiveTier
                // — perf mode is a memory/decode saver and must NOT strip 32-bit float audio fidelity (a MID/HIGH
                // device with perf mode manually ON keeps float).
                val rawTier = iad1tya.echo.music.utils.DeviceCapabilities.tier(this@MusicService)
                val isLowRamDevice =
                    (this@MusicService.getSystemService(Context.ACTIVITY_SERVICE) as? android.app.ActivityManager)?.isLowRamDevice == true
                val lowEnd = rawTier == iad1tya.echo.music.utils.DeviceTier.LOW ||
                    rawTier == iad1tya.echo.music.utils.DeviceTier.ULTRA ||
                    isLowRamDevice
                DefaultAudioSink
                    .Builder(this@MusicService)
                    .setEnableFloatOutput(!lowEnd)
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
        }.apply {
            // Fall through to the software decoder when a vendor HW AAC/HE-AAC decoder mis-decodes (a known
            // cause of accelerated/garbled audio on some devices).
            setEnableDecoderFallback(true)
        }

    override fun onPlaybackStatsReady(
        eventTime: AnalyticsListener.EventTime,
        playbackStats: PlaybackStats,
    ) {
        val mediaItem = eventTime.timeline.getWindow(eventTime.windowIndex, Timeline.Window()).mediaItem
        val historyDurationMs = historyDurationMsHint

        if (playbackStats.totalPlayTimeMs >= historyDurationMs &&
            !pauseListenHistoryHint
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
        // The expanded flag lives in the process-wide PlaybackStateManager, which OUTLIVES this
        // service instance. If the UI died without collapsing (its onDispose reset is best-effort),
        // a recreated service would inherit "expanded" and keep speculative video warm-ups alive
        // with no player on screen — reset it here.
        playbackState.playerSheetExpanded = false

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

        // Release the speculative instant-video pre-player (if any) so it never leaks past the service.
        teardownInstantVideoSwap("service destroyed")

        // Release crossfade players (incl. any preloaded incoming one) so they don't leak.
        crossfadeJob?.cancel()
        crossfadeTriggerJob?.cancel()
        crossfadePreloadJob?.cancel()
        crossfadeReadyJob?.cancel()
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
                if (player.isPlaying) {
                    player.pause()
                } else {
                    // #27: a home-screen widget tap is a GENUINE user action → allow it to start a restored
                    // queue (prepare() is a no-op if already prepared) AND drop the veto so external controls
                    // work normally afterwards. This is a direct player call, so the onPlayerCommandRequest
                    // veto never applies to it.
                    if (player.playbackState == Player.STATE_IDLE) player.prepare()
                    awaitingFirstUserPlay = false
                    player.play()
                }
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

    
    /**
     * #27: the app came to the foreground (user opened it) — genuine engagement, so drop the cold-restore PLAY
     * veto and let all external controls (BT/AA/notification/watch) work normally. Called from MainActivity.
     */
    fun onAppForegrounded() {
        userHasForegroundedThisProcess = true
        awaitingFirstUserPlay = false
    }

    /**
     * #27: an external controller EXPLICITLY selecting a song (e.g. Android Auto browse → tap a track, routed
     * through onSetMediaItems) is genuine engagement — distinct from a stray reconnect PLAY on the restored
     * queue — so drop the veto and let it play. A phantom BT reconnect sends a bare PLAY, never onSetMediaItems.
     */
    internal fun onControllerSelectedItem() {
        awaitingFirstUserPlay = false
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
        crossfadeReadyJob?.cancel()
        crossfadeReadyJob = null
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
        // High-Performance Mode: crossfade is force-disabled (crossfadeEnabled already reflects this via the
        // perf-gated flow at collect time). This explicit, cheap @Volatile guard makes the intent robust and
        // self-documenting — transitions fall back to normal gapless/simple playback (a single decoder, no
        // second ExoPlayer) on weak/TV/car devices. No-op on capable devices: perf mode off → hint false →
        // falls through to the unchanged 9s equal-power crossfade path below. The cleanup above still ran, so
        // any incoming player preloaded before perf mode toggled on is released rather than leaked.
        if (highPerformanceModeHint) return
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

        // START-CLIP FIX: never fade in a half-buffered incoming player. On the normal preloaded path the
        // secondary is already STATE_READY (buffered at position 0) with the full ~12 s lead, so we swap
        // immediately — byte-identical to before. On the LATE-ARMED path (radio just appended the next item,
        // a near-end seek, or the streamed duration arrived late) the secondary was built with ~0 ms buffered;
        // flipping playWhenReady and swapping NOW left the incoming still resolving its URL / buffering while
        // the OUTGOING player — capped to its current item — hit STATE_ENDED, so the join went silent and the
        // new song's first moment was clipped. Instead, wait (bounded) for STATE_READY, THEN swap + fade from a
        // clean position 0. If it can't ready within the bound, fall through to media3's single-player
        // auto-advance (a clean hard cut) rather than a clipped pop-in. Curve/duration untouched.
        if (secPlayer.playbackState == Player.STATE_READY) {
            beginCrossfadeSwap(secPlayer, savedShuffleEnabled)
            return
        }
        val targetMediaId = player.currentMediaItem?.mediaId
        crossfadeReadyJob?.cancel()
        crossfadeReadyJob = scope.launch {
            var waited = 0L
            while (isActive && secPlayer.playbackState != Player.STATE_READY &&
                waited < CROSSFADE_READY_TIMEOUT_MS
            ) {
                delay(50)
                waited += 50
            }
            // Abort if the world moved on while we waited (user skipped/paused, a new crossfade armed, the
            // current track changed, or this secondary was already released) — never swap a stale transition.
            if (!isActive || isCrossfading || secondaryPlayer !== secPlayer ||
                !player.isPlaying || player.currentMediaItem?.mediaId != targetMediaId
            ) return@launch
            if (secPlayer.playbackState == Player.STATE_READY) {
                beginCrossfadeSwap(secPlayer, savedShuffleEnabled)
            }
            // else: not ready within the bound → leave the single-player path to hard-cut cleanly (no swap).
        }
    }

    /** Flip the (already-READY) incoming player on and run the swap + fade. Extracted so both the fast path
     *  and the bounded ready-wait in [startCrossfade] share one swap site. */
    private fun beginCrossfadeSwap(secPlayer: ExoPlayer, savedShuffleEnabled: Boolean) {
        if (isCrossfading) return
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

        // INSTANT VIDEO SWAP: the crossfade secondary and the speculative video pre-player must NEVER
        // coexist (max 2 ExoPlayers, same envelope as before the feature). Crossfade wins — the video
        // pre-player is pure speculation; the toggle falls back to the normal swap path.
        teardownInstantVideoSwap("crossfade secondary player preparing")

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
        // Observation-only mirror for the UI (set AFTER the null-guard so it can't strand true); does not
        // alter the swap. Also drop any per-track Opus force from a refetch on the OUTGOING track: the swap
        // moves us to the next track via a path that skips onMediaItemTransition, so it must be cleared here.
        _isCrossfading.value = true
        forceOpusForMediaId = null
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
        _isCrossfading.value = false // observation-only mirror for the UI; does not alter the swap
        sleepTimer.notifySongTransition()
    }

    companion object {
        /**
         * How many artists a single autoplay continuation may look a genre up for. Small on purpose: it is
         * WiFi-only, off the playback path, and misses are cached, so coverage fills in over a few songs
         * without ever turning the radio into a burst of network work.
         */
        private const val GENRE_LEARN_PER_RUN = 12
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
        // Auto-resume after a network dead-end only if reconnection happens within this window, so a
        // reconnection hours later re-buffers but never surprise-plays.
        private const val STALE_RESUME_WINDOW_MS = 30 * 60 * 1000L
        // One-shot delay before the dead-end safety re-check (covers a stable network that never re-emits).
        private const val DEAD_END_RECHECK_MS = 45_000L
        // How early (ms before the fade) to build + buffer the incoming player so the crossfade has no gap.
        private const val CROSSFADE_PRELOAD_LEAD_MS = 12000L
        // Max time to wait for a LATE-ARMED / not-yet-buffered incoming player to reach STATE_READY before the
        // fade swaps. The outgoing has ~crossfadeDuration of runway from the trigger, so this stays well within
        // it; if READY isn't reached in time, we fall back to a clean single-player hard cut (no clipped pop-in).
        private const val CROSSFADE_READY_TIMEOUT_MS = 2500L

        // KILL SWITCH for the instant audio→video dual-player swap (pre-prepared secondary publish).
        // Flip to false to disable the whole feature at runtime: every hook becomes a no-op and the
        // audio pipeline / normal swapToVideo path are byte-identical to the pre-feature behaviour.
        //
        // GATED OFF for the public release: instant dual-player swap gated off pending on-device audio
        // verification (review flagged a possible mid-song level jump + LoadControl starvation). With this
        // false, scheduleInstantVideoPrepare / maybePrepareInstantVideoSwap / tryInstantVideoSwap all early-
        // return, so NO speculative pre-player is ever created (no wasted buffering) and toggleVideoMode
        // falls to the byte-identical swapToVideo path. The safe connection warm-up
        // (maybeWarmVideoConnection) is independent of this flag and stays ACTIVE.
        @Volatile
        var INSTANT_VIDEO_SWAP_ENABLED = false
        // Delay before pre-preparing after a track transition / video exit, so the speculative player never
        // competes with the running track's own startup buffering (750 ms floor + rebuffer window).
        private const val INSTANT_VIDEO_PREPARE_DELAY_MS = 2500L
        // Never pre-prepare inside this margin of the crossfade preload moment (preload lead + margin):
        // guarantees the video pre-player and the crossfade secondary never race to exist at once.
        private const val INSTANT_VIDEO_CROSSFADE_MARGIN_MS = 3000L
        // The live position must be at least this far inside the pre-player's buffered window at swap
        // time, or we fall back to the normal path instead of publishing a player about to rebuffer.
        private const val INSTANT_VIDEO_MIN_BUFFER_AHEAD_MS = 1500L
        
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
        // Prefetch ONLY the UPCOMING items (after the current index). The current/just-tapped track is
        // resolved by the ResolvingDataSource on play — prefetching it here double-resolves AND can poison
        // songUrlCache with a wrong-container URL.
        val lookahead = kotlin.math.min(10, player.mediaItemCount - currentIndex - 1)
        val upcomingAll = ArrayList<String>(kotlin.math.max(0, lookahead))
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
            // High-Performance Mode: DON'T skip entirely. Keep a LIGHT, url-only prefetch (resolve + cache the
            // stream URL so the first frame starts fast on low-end/TV/car too); only the heavier per-song
            // extras (loudness/format DB caching + lyrics) are gated OFF below. Battery Saver above still
            // skips everything.
            val perfMode = iad1tya.echo.music.utils.PerformanceMode.isOn(this@MusicService)
            if (perfMode) {
                Timber.tag(TAG).d("Preload: high-performance mode — url-only (extras skipped)")
            }
            // Default 2 (was 1) so the very next song is ready even while the current one is still resolving;
            // the user's slider value is still honoured when set.
            val preloadLimit = dataStore.get(iad1tya.echo.music.constants.PreloadNextSongLimitKey, 2)
            val preloadLyrics = dataStore.get(iad1tya.echo.music.constants.PreloadLyricsEnabledKey, true)
            // Only the next N upcoming tracks per the slider (the current track is resolved on play by the
            // ResolvingDataSource). distinct() so a duplicated id isn't resolved twice.
            val upcomingMediaIds = upcomingAll.take(preloadLimit).distinct()
            for (mediaId in upcomingMediaIds) {
                // Skip a fully-DOWNLOADED upcoming song — it plays straight from the download cache, so
                // re-resolving its URL is wasted work (and could poison songUrlCache with a wrong-container URL).
                // NOTE: do NOT also skip on a playerCache hit here. songUrlCache is in-memory (empty on a fresh
                // process), so skipping a playerCache-cached song would leave the resolver later hitting
                // playerCache.isCached with no URL, taking the "Ghost cache entry" path that DELETES the cached
                // bytes and re-downloads — destroying cross-session cache. The `!songUrlCache.containsKey(mediaId)`
                // guard just below already prevents redundant resolves for anything already resolved this session.
                if (downloadCache.isCached(mediaId, 0, 1)) continue

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
                            // Mirror the main resolver's TTL: honour the real stream expiry instead of a
                            // hardcoded 1h (a googlevideo URL can expire sooner and would then 403).
                            songUrlCache[mediaId] = Pair(data.streamUrl, System.currentTimeMillis() + (data.streamExpiresInSeconds * 1000L))
                            Timber.tag(TAG).d("Preloaded stream for $mediaId")

                            // FIX A: cache the loudness (FormatEntity) for the UPCOMING track NOW, so when it
                            // transitions to playing, setupLoudnessEnhancer finds a non-null format and primes the
                            // correct gain at second 0 — no audible volume swell. Mirrors the resolver's
                            // FormatEntity construction exactly. Preserve any existing row's loudness: only fill
                            // when missing, never overwrite a known loudness with null.
                            // Gated OFF in High-Performance Mode (url-only prefetch there).
                            if (!perfMode) kotlin.runCatching {
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

                if (preloadLyrics && !perfMode) {
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

/**
 * One autoplay suggestion chip for the queue's Autoplay footer (YT Music parity). [label] is what the
 * chip shows (localized for the default related chip; artist/mix names come from YouTube as-is),
 * [endpoint] is the WatchEndpoint the autoplay is re-seeded from when the chip is selected, and [kind]
 * distinguishes the default related chip from artist radios and mixes/playlists.
 */
data class AutoplayChip(
    val label: String,
    val endpoint: WatchEndpoint,
    val kind: Kind,
) {
    enum class Kind { RELATED, ARTIST, MIX }
}
