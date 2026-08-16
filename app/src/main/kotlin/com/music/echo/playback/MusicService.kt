

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
import iad1tya.echo.music.constants.AudioNormalizationKey
import iad1tya.echo.music.constants.SafeVolumeEnabledKey
import iad1tya.echo.music.constants.SpatialAudioEnabledKey
import iad1tya.echo.music.constants.SpatialAudioProfileKey
import iad1tya.echo.music.constants.AudioOffload
import iad1tya.echo.music.constants.AudioQualityKey
import iad1tya.echo.music.constants.AutoDownloadOnLikeKey
import iad1tya.echo.music.constants.AutoLoadMoreKey
import iad1tya.echo.music.constants.OfflineModeKey
import iad1tya.echo.music.constants.KeepGenreLaneKey
import iad1tya.echo.music.constants.AutoSkipNextOnErrorKey
import iad1tya.echo.music.constants.CrossfadeDurationKey
import iad1tya.echo.music.constants.CrossfadeEnabledKey
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
import iad1tya.echo.music.constants.EnhancedShuffleKey
import iad1tya.echo.music.constants.PreviousQueueOfferKey
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
import androidx.media3.exoplayer.offline.DownloadService
import iad1tya.echo.music.db.MusicDatabase
import iad1tya.echo.music.db.entities.EnhancedShuffleContextEntity
import iad1tya.echo.music.db.entities.EnhancedShufflePlayedEntity
import iad1tya.echo.music.db.entities.Event
import iad1tya.echo.music.db.entities.FormatEntity
import iad1tya.echo.music.db.entities.LyricsEntity
import iad1tya.echo.music.db.entities.RelatedSongMap
import iad1tya.echo.music.db.entities.Song
import iad1tya.echo.music.di.DownloadCache
import iad1tya.echo.music.di.PlayerCache
import iad1tya.echo.music.eq.EqualizerService
import iad1tya.echo.music.eq.audio.CustomEqualizerAudioProcessor
import iad1tya.echo.music.eq.audio.SpatialAudioProfile
import iad1tya.echo.music.eq.audio.SpatialOutputKind
import iad1tya.echo.music.eq.audio.NormalizationGainAudioProcessor
import iad1tya.echo.music.eq.audio.TruePeakLimiterAudioProcessor
import iad1tya.echo.music.eq.audio.normalizationMultiplier
import iad1tya.echo.music.eq.audio.loudnessMakeupDb
import iad1tya.echo.music.eq.audio.safeVolumeGainWithEqPreamp
import iad1tya.echo.music.eq.audio.dbToLinear
import iad1tya.echo.music.eq.audio.effectiveLoudnessDb
import iad1tya.echo.music.eq.audio.isPlayingLoudnessFrozen
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
import iad1tya.echo.music.playback.audio.AudioOffloadGate
import iad1tya.echo.music.playback.audio.SilenceDetectorAudioProcessor
import iad1tya.echo.music.playback.queues.EmptyQueue
import iad1tya.echo.music.playback.queues.ListQueue
import iad1tya.echo.music.playback.queues.LocalAlbumRadio
import iad1tya.echo.music.playback.queues.Queue
import iad1tya.echo.music.playback.queues.YouTubeAlbumRadio
import iad1tya.echo.music.playback.queues.YouTubeQueue
import iad1tya.echo.music.playback.queues.filterExplicit
import iad1tya.echo.music.playback.queues.filterVideoSongs
import iad1tya.echo.music.playback.queues.filterNonMusicForAutoQueue
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
import iad1tya.echo.music.widget.PlaylistWidgetReceiver
import iad1tya.echo.music.widget.TurntableWidgetReceiver
import dagger.hilt.android.AndroidEntryPoint
import iad1tya.echo.music.utils.exportedFileUriExists
import iad1tya.echo.music.utils.isLocalMediaId
import iad1tya.echo.music.utils.parseExportedFileUriMap
import iad1tya.echo.music.constants.ExportedFileUrisKey
import iad1tya.echo.music.constants.ExportedVideoIdsKey
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
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
import kotlinx.coroutines.withTimeoutOrNull
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

    // TAIL-DETECTION jobs (see scheduleCrossfade / onTailSilenceDetected): arms the silence detector for
    // the final stretch of the current track, and re-checks a too-early "musical end" fire at the moment
    // the fade would actually be due. Cancelled on every reschedule and at fade commit.
    private var crossfadeTailArmJob: Job? = null

    private var tailQuietRecheckJob: Job? = null

    private val secondaryPlayerListener = object : Player.Listener {
        override fun onPlayerError(error: PlaybackException) {
            Timber.tag(TAG).e(error, "Secondary player error")
            secondaryPlayer?.stop()
            // NO clearMediaItems before release(): redundant (release frees everything) and it is a live
            // timeline MUTATION racing the dying player's own transition machinery — media3's
            // evaluateMediaItemTransitionReason throws its bare "impossible state" ISE when a playlist
            // edit lands exactly as an ended/auto transition is being evaluated (client crash, CRASH_REPORTS #2).
            // Full teardown (mirror cleanupCrossfade/releasePlayer): also drop the EQ processor and release()
            // the native player, or every secondary-player error leaks an ExoPlayer and permanently grows
            // EqualizerService's processor list.
            secondaryPlayer?.let {
                playerSilenceProcessors.remove(it)
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
    /** Auto wireless: media3 paused us via AUDIO_BECOMING_NOISY — resume when the car route returns. */
    private var pausedByNoisy = false
    private var pausedByNoisyAtMs = 0L
    // Single-shot, cancellable safety re-check armed at the dead-end: covers a STABLE network that never fires
    // a new connectivity event, so we don't wait forever paused. Not a loop; cancelled in triggerRetry()/READY.
    private var deadEndRecheckJob: Job? = null

    private lateinit var audioQuality: iad1tya.echo.music.constants.AudioQuality
    private lateinit var ipVersion: IpVersion

    private var currentQueue: Queue = EmptyQueue
    var queueTitle: String? = null

    /**
     * "¿Quieres volver a la cola anterior?" — the ONE queue the user walked away from when he jumped out
     * of a playlist into an album, snapshotted at the exact instant [playQueue] was about to overwrite
     * [currentQueue].
     *
     * Exactly one, never a stack: he asked for "la cola anterior". In-memory only — this is a
     * within-session courtesy, so it deliberately does NOT add a fourth object graph to
     * [saveQueueToDisk]; a process death drops it, like the offer itself.
     *
     * IDS, not songs: see [PreviousQueueCursor]. This field is the one thing 0.6.145 added to the STEADY
     * state of the media process, it can be armed for ten minutes at a time, and in a car it always is
     * (the snackbar that would clear it is on a screen the user is not looking at) — so what it holds
     * has to be small enough to be uninteresting to the low-memory killer.
     */
    private var previousQueueSnapshot: PreviousQueueCursor? = null

    /** `SystemClock.elapsedRealtime()` deadline of the armed offer; 0 when there is none. */
    private var previousQueueExpiresAt = 0L

    /**
     * Drops the snapshot [PreviousQueueRule.OFFER_TTL_MS] after it was taken, so an offer nobody answered
     * does not sit in memory — or resurface detached from what the user was doing — for the rest of the
     * session. Cancelled and re-armed on every capture; cancelled on accept/dismiss.
     */
    private var previousQueueExpiryJob: Job? = null

    private val _previousQueueOffer = MutableStateFlow<PreviousQueueOffer?>(null)

    /**
     * Emits once per detected playlist -> album detour (see [PreviousQueueRule]). The UI answers with a
     * self-expiring snackbar: the offer never interrupts playback, never steals focus, and if it is
     * ignored nothing happens. Cleared by [resumePreviousQueue] / [dismissPreviousQueueOffer] / the
     * expiry timer.
     */
    val previousQueueOffer: kotlinx.coroutines.flow.StateFlow<PreviousQueueOffer?> =
        _previousQueueOffer.asStateFlow()

    private var previousQueueOfferToken = 0L

    /**
     * Snapshot the OUTGOING queue when the user leaves a playlist for an album, so he can be offered a
     * way back to it. Called from [playQueue] immediately before `currentQueue = queue` — the exact
     * instant the old queue stops being reachable.
     *
     * Touches nothing but its own two fields: it reads the timeline and publishes a StateFlow, so it
     * cannot affect playback whether or not the offer is later accepted. It reuses [toPersistQueue] for
     * the queue's IDENTITY (type, continuation data, context) rather than inventing a second mapping, and
     * [PreviousQueueCursor] for the items — see there for why the items are not kept.
     */
    private fun captureQueueForResumeOffer(newQueue: Queue, isRestore: Boolean) {
        // The switch is honoured HERE, at the capture, not at the prompt or at the action: with it off
        // nothing is ever snapshotted, so there is no queue held in memory and no button that could do
        // nothing. (Same reasoning as the Listen Together guest check below.)
        if (!previousQueueOfferEnabledHint) return

        // A boot restore is the SAME queue coming back, not the user going anywhere. Its outgoing queue is
        // EmptyQueue (null context) so the rule already declines, but say it out loud: this must never
        // greet him with a prompt at app start.
        if (isRestore) return

        // He walked back to that list on his own (tapped it again). A pending offer would now propose
        // returning to what is already playing — drop it silently before anything else.
        previousQueueSnapshot?.contextId?.let { pending ->
            if (pending == newQueue.contextId) dismissPreviousQueueOffer()
        }

        if (!PreviousQueueRule.isDetour(currentQueue.contextId, newQueue.contextId)) return
        // Same guard saveQueueToDisk uses: an empty timeline is nothing to come back to.
        if (player.mediaItemCount == 0) return
        // A Listen Together GUEST does not own the queue: PlayerConnection.resumePreviousQueue would
        // refuse to act on it, so offering the prompt would be a button that does nothing. Never offer it.
        if (listenTogetherManager.isInRoom && !listenTogetherManager.isHost) return

        // ONE walk of the timeline, keeping the IDS and nothing else. The id list has exactly one entry
        // per index, so player.currentMediaItemIndex stays a valid index into it BY CONSTRUCTION — the
        // shrink hazard the old metadata capture had to guard against (mapNotNull could drop an item and
        // silently shift the cursor onto a different song) cannot arise. A timeline that cannot be read,
        // or an item with no media id, still declines the offer rather than offering a wrong answer.
        val cursorIndex = player.currentMediaItemIndex
        val count = player.mediaItemCount
        val ids = ArrayList<String>(count)
        var anchor: iad1tya.echo.music.models.MediaMetadata? = null
        for (i in 0 until count) {
            val item = runCatching { player.getMediaItemAt(i) }.getOrNull() ?: return
            if (item.mediaId.isEmpty()) return
            ids.add(item.mediaId)
            // The cursor's own song is the ONE item kept whole — see [PreviousQueueCursor.anchor].
            if (i == cursorIndex) anchor = item.metadata
        }
        if (cursorIndex !in ids.indices) return

        // Items deliberately empty: this call is here for the queue's IDENTITY (queueType / queueData /
        // contextId / title / cursor), which is the part of the mapping worth sharing with the restore
        // path. The songs themselves are rebuilt from the database on accept.
        val shell = runCatching {
            currentQueue.toPersistQueue(
                title = queueTitle,
                items = emptyList(),
                mediaItemIndex = cursorIndex,
                position = player.currentPosition,
            )
        }.onFailure {
            Timber.tag(TAG).w(it, "Could not snapshot the outgoing queue for the resume offer")
        }.getOrNull() ?: return

        previousQueueSnapshot = PreviousQueueCursor(
            title = shell.title,
            contextId = shell.contextId,
            queueType = shell.queueType,
            queueData = shell.queueData,
            mediaIds = ids,
            mediaItemIndex = cursorIndex,
            position = shell.position,
            anchor = anchor,
        )
        previousQueueOfferToken++
        val expiresAt = android.os.SystemClock.elapsedRealtime() + PreviousQueueRule.OFFER_TTL_MS
        previousQueueExpiresAt = expiresAt
        _previousQueueOffer.value =
            PreviousQueueOffer(previousQueueOfferToken, shell.title, expiresAt)

        // Actively DROP it when the bound passes: the offer is a within-session courtesy, and an id list
        // held for a whole session because nobody ever answered is dead memory. The token is
        // re-read inside the job so a NEWER capture (which re-arms this job anyway) can never be retired
        // by an older timer.
        val armedToken = previousQueueOfferToken
        previousQueueExpiryJob?.cancel()
        previousQueueExpiryJob = scope.launch {
            delay(PreviousQueueRule.OFFER_TTL_MS)
            if (previousQueueOfferToken == armedToken) dismissPreviousQueueOffer()
        }
    }

    /**
     * Accept the offer: reinstate the snapshotted queue AT THE SONG AND POSITION he left it.
     *
     * No seek is re-implemented here. [PersistQueue.toQueue] rebuilds a ListQueue carrying `startIndex`
     * and `position`, and [playQueue]'s `player.setMediaItems(items, safeIndex, initialStatus.position)`
     * already lands both — the same path the boot restore relies on.
     *
     * The songs are read back from the `song` table first ([rehydrate]), because the snapshot holds ids
     * rather than songs. That read is the only thing between the tap and the queue, so it happens off the
     * Main thread and the offer is retired BEFORE it starts: the prompt must not sit there looking
     * unanswered while a few hundred rows come back.
     */
    fun resumePreviousQueue() {
        val cursor = previousQueueSnapshot ?: return
        // Last line of defence on the bound: the expiry timer is a main-looper delay, which does not tick
        // while the device is in deep sleep, so a tap arriving right after a long doze could otherwise
        // reinstate a queue the user left an hour ago. Drop it instead of playing it.
        if (PreviousQueueRule.hasLapsed(
                android.os.SystemClock.elapsedRealtime(),
                previousQueueExpiresAt,
            )
        ) {
            dismissPreviousQueueOffer()
            return
        }
        // Consume BEFORE playing: the resume itself goes through playQueue, and leaving the snapshot in
        // place would let a stale offer survive the very action that satisfied it.
        previousQueueSnapshot = null
        previousQueueExpiresAt = 0L
        previousQueueExpiryJob?.cancel()
        previousQueueExpiryJob = null
        _previousQueueOffer.value = null

        // Same revival playQueue does at its own entry: the read below has to run for the resume to
        // happen at all, and launching it on a cancelled scope would be a button that does nothing.
        if (!scope.isActive) scope = CoroutineScope(Dispatchers.Main) + Job()
        scope.launch {
            // Chunked because "LIB:" is thousands of ids and Room binds one SQL variable per id — see
            // [PreviousQueueRule.ID_LOOKUP_CHUNK]. distinct() because a queue may repeat a song, and the
            // rebuild looks each id up by key anyway.
            val byId = runCatching {
                withContext(Dispatchers.IO) {
                    val out = HashMap<String, iad1tya.echo.music.models.MediaMetadata>(cursor.mediaIds.size)
                    cursor.mediaIds.distinct()
                        .chunked(PreviousQueueRule.ID_LOOKUP_CHUNK)
                        .forEach { chunk ->
                            database.getSongsByIds(chunk).forEach { out[it.song.id] = it.toMediaMetadata() }
                        }
                    out
                }
            }.onFailure {
                Timber.tag(TAG).e(it, "Could not read back the previous queue's songs")
            }.getOrDefault(emptyMap())

            // Null only if the cursor cannot be placed at all — play nothing rather than the wrong song.
            val restored = cursor.rehydrate(byId)
            if (restored == null) {
                Timber.tag(TAG).w("Previous queue could not be rebuilt (%d ids)", cursor.mediaIds.size)
                return@launch
            }
            if (restored.items.size != cursor.mediaIds.size) {
                Timber.tag(TAG).i(
                    "Previous queue rebuilt with %d of %d items (rows removed since capture)",
                    restored.items.size, cursor.mediaIds.size,
                )
            }
            runCatching { playQueue(restored.toQueue()) }.onFailure {
                Timber.tag(TAG).e(it, "Failed to resume the previous queue")
            }
        }
    }

    /**
     * Decline, let it lapse, or turn the setting off. Drops the offer AND the snapshot: once the prompt is
     * gone there is no door left to open it, so keeping the ids would only be dead memory.
     */
    fun dismissPreviousQueueOffer() {
        previousQueueSnapshot = null
        previousQueueExpiresAt = 0L
        previousQueueExpiryJob?.cancel()
        previousQueueExpiryJob = null
        _previousQueueOffer.value = null
    }

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
    // ---- LYRICS-ONLY observation of the track that is still AUDIBLE during a crossfade swap ----
    // The swap publishes the INCOMING song at the START of the fade (the notification, widget and Android Auto
    // depend on that and are NOT touched here), but for the whole fade the user still HEARS the outgoing one.
    // These two members let the lyrics view — and nothing else — follow that outgoing track on its own clock
    // until the fade commits. Pure observation: no fade math, curve, duration, swap ORDER or metadata
    // publication is affected by either of them.
    private val _crossfadeOutgoingMetadata =
        MutableStateFlow<iad1tya.echo.music.models.MediaMetadata?>(null)
    val crossfadeOutgoingMetadata: kotlinx.coroutines.flow.StateFlow<iad1tya.echo.music.models.MediaMetadata?> =
        _crossfadeOutgoingMetadata.asStateFlow()
    private var crossfadeJob: Job? = null

    /**
     * Live playback position of the OUTGOING (fading) player, or null whenever there is no readable fade in
     * flight — no crossfade, already committed, or the player was released. Main thread only: the same thread
     * the UI already reads [player].currentPosition on and the same thread the crossfade itself runs on, so
     * there is no locking and no IO here.
     *
     * Also SELF-HEALING: if the fade is gone, the outgoing-song override is cleared here as well, so the
     * lyrics view can never be stranded showing a song that stopped playing.
     */
    fun crossfadeOutgoingPositionMs(): Long? {
        val fp = fadingPlayer?.takeIf { isCrossfading }
        val pos = fp?.let { runCatching { it.currentPosition }.getOrNull() }
        if (pos == null && _crossfadeOutgoingMetadata.value != null) {
            _crossfadeOutgoingMetadata.value = null
        }
        return pos
    }

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

    // Which mediaId we've already applied loudness for THIS play. Frozen until the track changes:
    // liking + auto-download used to re-store FormatEntity mid-song and the volume jumped. Volatile
    // because the stream resolver (loader thread) also reads/writes it when priming from the same
    // player-response that yields the URL — no extra network, no wait on Room.
    @Volatile private var lastNormalizedId: String? = null
    // The id of the track currently playing, updated from onMediaItemTransition on the player thread. Lets the
    // ResolvingDataSource loader thread know "is this the current track?" WITHOUT a runBlocking hop to Main
    // (which could deadlock/stall stream resolution when Main is busy).
    @Volatile private var currentPlayingMediaId: String? = null
    // Live EQ processor of the audible player. The loader thread primes Safe Volume here before
    // open() returns, so the first sample is already at the locked level.
    @Volatile private var currentEqProcessor: CustomEqualizerAudioProcessor? = null
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
    // Initialised TRUE to match SafeVolumeEnabledKey's own default: starting false left a window before the
    // collector's first emission where a crossfade or instant-video swap would skip priming entirely.
    @Volatile private var safeVolumeEnabledHint: Boolean = true
    @Volatile private var spatialEnabledHint: Boolean = false
    @Volatile private var spatialProfileNameHint: String = SpatialAudioProfile.WIDE_SURROUND.name
    @Volatile private var tidalEnabledHint: Boolean = false
    // The offload request CURRENTLY PUBLISHED to the players (not merely the gate's latest verdict — an
    // approved enable can be waiting for a track boundary; see publishOffloadDecision). Read by
    // onPlaybackParametersChanged, which only re-publishes the speed requirement while offload is live.
    @Volatile private var audioOffloadHint: Boolean = false

    // P33 — the player-thread callbacks onMediaItemTransition/onPlaybackStatsReady used to call dataStore.get(),
    // which is a runBlocking disk-backed flow read, several times per track transition ON THE MAIN/APPLICATION
    // (ExoPlayer) thread — a blocking-I/O-on-main anti-pattern (jank risk). Mirror those prefs into memory via the
    // single collector in onCreate (same pattern as normalizationEnabledHint/audioOffloadHint) and read the fields
    // in the hot paths instead. Initial values equal the DataStore defaults, so behaviour is unchanged.
    @Volatile private var autoLoadMoreHint: Boolean = true
    @Volatile private var disableLoadMoreWhenRepeatAllHint: Boolean = false
    // Enhanced Shuffle ("Aleatorio mejorado") master switch mirrored for the player-thread callbacks
    // (onShuffleModeEnabledChanged / onMediaItemTransition) so they read a @Volatile field, never a blocking
    // DataStore read. Default matches EnhancedShuffleKey's default (ON).
    @Volatile private var enhancedShuffleHint: Boolean = true

    // "¿Volver a la cola anterior?" master switch, mirrored the same way: playQueue is a main-thread path
    // and must not add a blocking DataStore read to it. Default matches PreviousQueueOfferKey's (ON), and
    // it is seeded from the cold-start prefs snapshot in onCreate so a user who turned it OFF never gets a
    // capture in the window before the collector's first emission.
    @Volatile private var previousQueueOfferEnabledHint: Boolean = true

    // AIMP-style smooth entry on MANUAL track changes. Default matches FadeOnManualChangeKey (ON).
    @Volatile private var fadeOnManualChangeHint: Boolean = true

    private var manualFadeInJob: Job? = null

    // Which track the tail detector is currently armed for: re-scheduling the SAME track re-arms without
    // resetting the counters (a reset mid-silence would delay a genuine tail fire); a NEW track resets.
    private var tailArmedMediaId: String? = null

    // Monotonic timeline mutation counter (Main-thread writes via onTimelineChanged). Captured by
    // prepareSecondaryPlayer so scheduleCrossfade can prove a preloaded secondary's queue copy is still
    // identical to the live queue before reusing it.
    private var timelineVersion = 0L
    private var secondaryTimelineVersion = -1L

    // Shuffle score cache, SPLIT by what each half actually depends on. It used to be one
    // mediaId -> (tasteScore, primaryArtist) map cleared WHOLESALE whenever the taste profile instance
    // changed — and the profile is rebuilt on a 5-minute TTL, so every ~5 minutes the next re-apply
    // re-derived BOTH halves for the entire queue on the Main thread. The primary artist is a property
    // of the media item alone and cannot change with the profile, so it now survives that invalidation:
    // only [shuffleTasteCache] is cleared. Values are identical either way — this changes cost, not order.
    // Main-thread only (applyShuffleOrder's thread).
    //
    // LIFETIME. The split gave [shuffleArtistCache] no invalidation trigger at all — only a 20 000-entry
    // panic clear — which in a media service that lives for days is effectively permanent. It cannot be
    // handed the profile trigger the merged cache used: surviving the taste refresh is the entire reason
    // the split exists, and re-attaching it would give back the ~20-60 ms Main-thread stall it bought.
    // The honest bound is the QUEUE: both maps only ever serve items on the current timeline, so a new
    // queue makes every surviving entry dead weight (see the clear in playQueue) — and memory pressure
    // drops both outright (onTrimMemory). Both are pure memoization: clearing them changes cost, never
    // the order produced.
    private val shuffleTasteCache = HashMap<String, Double>()
    private val shuffleArtistCache = HashMap<String, String>()
    private var shuffleScoreCacheProfile: Any? = null

    /**
     * Drop the shuffle memoization caches. Pure cost, never order: every value is re-derived on demand
     * from the media item and the taste profile, so a cleared cache produces the identical shuffle.
     * Main-thread only, like the maps themselves.
     */
    private fun clearShuffleCaches() {
        shuffleTasteCache.clear()
        shuffleArtistCache.clear()
        // [shuffleScoreCacheProfile] is deliberately left alone: it is the identity of the profile the
        // taste half was derived from, and an emptied map simply refills against it. It holds no memory
        // of its own — the instance it names is the one [cachedTaste] is already holding.
    }

    // PER-SONG SILENCE MEMORY (session-scoped, owner: "5s de MÚSICA bajando, silencio omitido — y la que
    // entra arranca en su música"). Learned from the live detector on each song's first play:
    //  • tailSilenceHintMs — length of the song's trailing silence. Next plays anchor the crossfade
    //    trigger at (musical end - fade window): the decay covers the LAST 5s OF MUSIC and completes as
    //    the music ends; the silent tail never plays. First play still uses the live tail tiers.
    //  • leadSilenceHintMs — length of the song's intro silence (below ~-42 dBFS, i.e. inaudible). Next
    //    times the song ENTERS a crossfade, the incoming player starts right at its music, so the rise is
    //    heard over real audio instead of dead air.
    // Main-thread only (all writers/readers are Main). Size-capped defensively; a wrong-direction miss is
    // always safe: no hint → exact 0.6.133 behavior.
    private val tailSilenceHintMs = HashMap<String, Long>()
    private val leadSilenceHintMs = HashMap<String, Long>()

    // LEAD-HINT TRUST: the intro measurement is only meaningful if counting started at the song's REAL
    // beginning. A mid-song start (session restore seeks to the persisted position; a near-start seek
    // before the first loud frame) would measure some interior quiet run — storing that as "intro
    // silence" would make later plays seek past REAL music. Set at the arm/reset site, checked at store.
    private var leadHintTrustedForArmedTrack = false
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
    // infinite radio doesn't re-append songs already heard days/weeks ago — the last ~120 in-session transitions
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

    // Genre-aware continuation — the CONTEXT PROFILE of the finite collection in [radioSeedPool] (its
    // artists + real genre mix + weak language hint). Built LAZILY (off the player thread, runCatching)
    // on the FIRST startRadioSeamlessly for a context, then reused by every re-seed. Null (pure radio /
    // build failed / not built yet) or inactive (below minimum genre signal) → every consumer no-ops and
    // behavior is byte-identical to today. Cleared at the SAME site radioSeedPool is reassigned.
    @Volatile private var contextProfile: iad1tya.echo.music.reco.ContextProfile.Profile? = null
    // True while the radio continuation should steer toward [contextProfile]: set by the context/last-song
    // seed sources (tryContextRadio/tryRadio/tryRelated), cleared by an explicit user steer — a mood
    // (tryMood) or an autoplay chip (selectAutoplayChip) must always win over the finished context.
    @Volatile private var contextSteerActive = false

    /**
     * How many items of the CURRENT timeline came from the user's own list (the playlist/album/library
     * selection he started) instead of from the infinite radio: timeline indices `[0, listQueueSize)`
     * are HIS LIST, `[listQueueSize, mediaItemCount)` were appended by the radio.
     *
     * This is the very number [applyShuffleOrder]'s "playlist first" branch has always partitioned on
     * ([originalQueueSize] below). It is merely PUBLISHED here so the queue UI can show the boundary the
     * engine already acts on — the distinction existed and had never been surfaced. Nothing reads this
     * flow to make a playback decision, and [originalQueueSize] keeps its exact previous meaning: it is
     * now backed by the flow so every existing read/write goes through one storage and the two values
     * can never drift.
     */
    private val _listQueueSize = MutableStateFlow(0)
    val listQueueSize: kotlinx.coroutines.flow.StateFlow<Int> = _listQueueSize.asStateFlow()

    private var originalQueueSize: Int
        get() = _listQueueSize.value
        set(value) { _listQueueSize.value = value }
    // B5 — anti-repeat shuffle memory: media IDs already played in the current shuffle session. While
    // shuffling, not-yet-played songs are ordered ahead of these, so nothing repeats until the whole pool is
    // exhausted (then it auto-resets for a new cycle). Reset whenever shuffle is (re)enabled.
    private val shufflePlayedIds = LinkedHashSet<String>()
    // ARTIST SPACING across re-applies: the normalized primary artists of the last few songs actually
    // PLAYED this shuffle session, OLDEST first. applyShuffleOrder rebuilds the entire order from scratch
    // on every mutation — a radio append, the DB seed landing, a manual jump — which with crossfade ON is
    // roughly once per song, and the spacing pass used to start each rebuild with an EMPTY history. The
    // song it put right after the current one was therefore chosen with no knowledge of what had just been
    // heard, so an artist could legitimately land at slots 1, 4, 7… of successive rebuilds and the owner
    // heard "bastante seguido el mismo artista". Capped at the largest gap spacing will ever ask for.
    // Main-thread only (same thread as applyShuffleOrder and both recording sites).
    private val recentShuffleArtists = ArrayDeque<String>()
    // Enhanced Shuffle: the persistent context id of the CURRENT queue (e.g. "PL:<id>", "LIBRARY"), or null
    // when the queue has no enhanced memory (raw YT radio, album/artist, etc.) → classic in-memory shuffle.
    // Set in playQueue from the ListQueue's contextId (carried across restart via PersistQueue.contextId).
    @Volatile private var shuffleContextId: String? = null
    /** Continue-shuffle seed from the screen; consumed once in [applyPendingSeedPlayedIds]. */
    @Volatile private var pendingSeedPlayedIds: Set<String> = emptySet()

    /**
     * COVERAGE — how many items the CURRENT CONTEXT actually loaded, and WHICH context that number
     * describes. This is the proof that "everything on the timeline is played" really means "the LIST
     * finished", instead of "the user trimmed the queue down to songs he already heard".
     *
     * It used to be read off [radioSeedPool], and that was a real defect: two unrelated facts shared one
     * field. [radioSeedPool] is a RADIO SEED sample — written ONLY by playQueue, empty for a YouTubeQueue,
     * and built with `mapNotNull { it.metadata }` so it can even be SHORTER than the list. Queues adopted
     * from an external controller ([adoptExternalQueue]: Android Auto, a watch, an assistant) never go
     * through playQueue, so on a fresh process the pool was still `emptyList()` and the coverage guard
     * silently vanished for the whole car session; worse, a leftover pool from a previous in-app queue
     * (a 4-track EP, an 80-song playlist) was compared against a completely different list.
     *
     * Keyed by context id ON PURPOSE: a size that describes another context is NOT coverage, it is noise,
     * so [EnhancedShuffleCycle.coverageOf] reports UNKNOWN for it. UNKNOWN deliberately means "judge by
     * the timeline alone" (today's behaviour when the pool was empty) and NOT "report not-exhausted":
     * this same reading also drives the handoff to the infinite radio, so a permanent "not exhausted"
     * would make every external queue unable to finish and leave it re-shuffling what was already heard.
     */
    @Volatile private var contextCoverageId: String? = null
    @Volatile private var contextCoverageSize: Int = 0

    /**
     * When [adoptExternalQueue] armed a coverage fill, so [onTimelineChanged] — the first instant the
     * external items ARE the timeline — can measure the context, and only then. Zeroed once consumed (or
     * once expired) so an in-app queue landing later can never be measured into the external context.
     */
    @Volatile private var externalCoverageArmedAt = 0L

    /**
     * Contexts that completed a full no-repeat lap in THIS process. Advisory only — the authoritative
     * "this list is finished" is re-derived from the persistent memory at re-activation time (see
     * [seedEnhancedShuffleFromDb]), which is what makes it survive the process death that MIUI inflicts
     * nightly. Used to keep the cycle counter honest (one bump per completion) and to make the NO_REPEAT
     * trace say whether the list was already finished when a song started.
     */
    private val completedShuffleContexts = LinkedHashSet<String>()

    /** Records a completed lap, bounded so a long session can't grow this set without limit. */
    private fun rememberCompletedContext(contextId: String): Boolean {
        val isNew = completedShuffleContexts.add(contextId)
        while (completedShuffleContexts.size > 32) {
            val it = completedShuffleContexts.iterator()
            if (it.hasNext()) { it.next(); it.remove() } else break
        }
        return isNew
    }

    /**
     * True while the app itself is turning shuffle on for a RESTORED queue. The owner's rule is that the
     * per-list memory resets only when HE re-activates shuffle on a finished list; a process that died
     * overnight and came back restoring its queue is not him re-activating anything.
     * media3 delivers listener events synchronously inside the assignment (same contract
     * [suppressShuffleModePersist] relies on), so a try/finally around the assignment is enough.
     */
    @Volatile private var suppressShuffleActivationReset = false

    /**
     * Armed while a playQueue is between "the player's timeline was replaced" and "the new context was
     * adopted". During that window media3's synchronous PLAYLIST_CHANGED transition would otherwise file
     * the NEW list's opener into the PREVIOUS list's memory — poisoning a list the user is not even
     * listening to. The opener is recorded explicitly at the adoption site instead.
     *
     * A TIMESTAMP, not a boolean, on purpose: playQueue has several early returns and its coroutine can be
     * abandoned on an offline fetch, so a plain flag could stay armed forever and silently disable ALL
     * transition-path recording for the rest of the process. This one expires on its own.
     */
    @Volatile private var contextAdoptionPendingAt = 0L
    // Enhanced Shuffle: ALL persistent-memory writes (per-song inserts, cursor updates, cycle-complete
    // clears) go through this single-lane dispatcher so LAUNCH order == COMMIT order. Otherwise the
    // fire-and-forget per-song insert and the cycle-complete DELETE — both scheduled from the same
    // onMediaItemTransition — could interleave, letting a just-cleared context re-gain a stale row.
    // limitedParallelism(1) serialises onto the IO pool without a dedicated leaked thread.
    private val enhancedShuffleWriteDispatcher = Dispatchers.IO.limitedParallelism(1)
    /** Recently-played media ids (bounded, most-recent last) so autoplay/radio don't resurface a song you
     *  JUST heard. A soft demotion (not a hard drop) — see [orderedByTaste] — so it can never dead-end the queue. */
    private val recentRadioIds = LinkedHashSet<String>()
    /** NO-REPEAT — SESSION-WIDE played/queued media ids. Every song we've PLAYED or APPENDED to the infinite
     *  queue this session lands here (large bounded LRU, ~4000, thread-safe). Unlike [recentRadioIds] (last ~120)
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
            while (recentRadioIds.size > 120) {
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
    // Manual Next on the last finite-queue track while it is STILL PLAYING: [resumeAfterSeed] alone only
    // advances when `!player.isPlaying` (the STATE_ENDED path). This flag forces the seek into the freshly
    // appended radio even though the last album/playlist song has not finished yet.
    @Volatile private var advanceIntoRadioRequested = false

    /** True while [startRadioSeamlessly] is fetching/appending — used by PlayerConnection for manual Next. */
    val isRadioSeedInFlight: Boolean get() = radioSeedInFlight

    /**
     * Arm the post-seed seek used by manual Next at the end of a finite queue. Safe to call while a B3
     * head-start seed is already running — does not launch a second seed by itself.
     */
    fun requestAdvanceIntoRadio() {
        resumeAfterSeed = true
        advanceIntoRadioRequested = true
    }
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
    /**
     * A resolved stream URL. The quality rides in the VALUE, never in the key: #28's per-mediaId lookup shape
     * stays intact (keying it would also force a parse on read, and a YouTube videoId is base64url — ~16%
     * contain '_', so any '_'-delimited scheme mis-parses).
     *
     * [delivered] and [requested] are DIFFERENT questions and must not share a field:
     *  - [delivered] = what actually came back. This is what the container guard compares against `dbFormat`,
     *    so it MUST be derived from the response with the guard's own predicate. Fallback is routine
     *    (LOSSLESS -> Qobuz fails -> Saavn fails -> Opus), so it often differs from what we asked for.
     *  - [requested] = what we asked for. The ONLY thing that can tell a cold start "the user changed quality
     *    while the service was dead, so this entry is stale". Comparing [delivered] to the global instead would
     *    throw away every legitimate fallback entry on every restart — i.e. #28's slow first play.
     *
     * null = UNKNOWN (an entry restored from a blob written before that field existed). Readers must then fall
     * back to the global audioQuality and never treat it as a pin.
     */
    private data class CachedStream(
        val url: String,
        val expiresAt: Long,
        val delivered: iad1tya.echo.music.constants.AudioQuality? = null,
        val requested: iad1tya.echo.music.constants.AudioQuality? = null,
    )

    private val songUrlCache = java.util.concurrent.ConcurrentHashMap<String, CachedStream>()

    /** Reads an [iad1tya.echo.music.constants.AudioQuality] name out of a blob entry; absent/unparsable -> null (UNKNOWN). */
    private fun org.json.JSONObject.parseQuality(field: String): iad1tya.echo.music.constants.AudioQuality? =
        optString(field, "").takeIf { it.isNotEmpty() }?.let { name ->
            iad1tya.echo.music.constants.AudioQuality.entries.find { it.name == name }
        }

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
                    .filter { it.value.expiresAt > now }
                    .sortedByDescending { it.value.expiresAt } // freshest-expiring first ≈ most-recent (LRU proxy)
                    .take(SONG_URL_CACHE_PERSIST_MAX)
                val json = org.json.JSONObject()
                for (e in entries) {
                    val o = org.json.JSONObject()
                        .put("u", e.value.url)
                        .put("e", streamUrlExpiryMillis(e.value.url, e.value.expiresAt))
                    // "q"  = what was DELIVERED (the container guard's input).
                    // "rq" = what was REQUESTED (staleness only — see [CachedStream]).
                    // Both omitted when unknown; an absent field reads back as null = unknown, never as a pin.
                    e.value.delivered?.let { q -> o.put("q", q.name) }
                    e.value.requested?.let { q -> o.put("rq", q.name) }
                    json.put(e.key, o)
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
                val prefs = dataStore.data.first()
                val blob = prefs[iad1tya.echo.music.constants.SongUrlCacheBlobKey]
                    ?.takeIf { it.isNotBlank() } ?: return@runCatching
                // The global quality, read from the SAME snapshot as the blob (so it can't race the quality
                // collector, whose first emit deliberately returns early). It is compared ONLY against what was
                // REQUESTED ("rq"), never against what was delivered: changing the quality while the service is
                // dead must drop those entries (nothing clears them otherwise), but a fallback entry — requested
                // LOSSLESS, delivered Opus — is perfectly good and must SURVIVE. Comparing the delivered quality
                // here would bin nearly every entry a Hi-Res user has on every cold start = #28's slow first play.
                // DATA SAVER: compare against the EFFECTIVE quality (forced Opus while ON), so entries
                // requested at a higher tier are dropped exactly like after a manual quality change —
                // otherwise a cached Hi-Res URL would keep serving Hi-Res bytes past the switch.
                val globalQuality = if (prefs[iad1tya.echo.music.constants.DataSaverEnabledKey] == true) {
                    iad1tya.echo.music.constants.AudioQuality.OPUS
                } else {
                    prefs[AudioQualityKey].toEnum(iad1tya.echo.music.constants.AudioQuality.OPUS)
                }
                val json = org.json.JSONObject(blob)
                val safeNow = System.currentTimeMillis() + 60_000L
                val keys = json.keys()
                var restored = 0
                while (keys.hasNext()) {
                    val k = keys.next()
                    val o = json.optJSONObject(k) ?: continue
                    val u = o.optString("u", "")
                    val e = o.optLong("e", 0L)
                    // A blob written by an older version has neither field: keep them null (UNKNOWN) rather than
                    // guessing. The resolver then falls back to the global audioQuality and the dbFormat container
                    // guard decides — so an OLD blob still serves its URLs (#28 fast path) and can never pin a
                    // replay to a stale quality. No migration, no crash: an unparsable name also degrades to null.
                    val q = o.parseQuality("q")
                    val rq = o.parseQuality("rq")
                    // Drop ONLY what we know was REQUESTED at a quality the user no longer wants. Unknown is kept
                    // on purpose: dropping it would wipe every existing user's cache on the upgrade to this
                    // version and re-create the exact slow-first-play complaint of #28.
                    if (rq != null && rq != globalQuality) continue
                    if (u.isNotEmpty() && e > safeNow) {
                        songUrlCache.putIfAbsent(k, CachedStream(u, e, delivered = q, requested = rq))
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

    // The one entry the quality collector keeps alive when the user changes quality mid-song: it is the
    // container lock for THAT track only (swapping its container under the decoder mid-stream would break it).
    // Tracked so it can be dropped once a different track is current — otherwise it stays pinned to the OLD
    // quality for the rest of the session (the URL TTL is ~5h), and a replay silently re-pins to it, which is
    // the very bug the quality fix exists to kill.
    @Volatile private var qualityPinnedMediaId: String? = null

    /**
     * Drop the quality-change survivor because a FRESH prepare is starting: the pin protects an IN-FLIGHT
     * stream, so once nothing is in flight it has nothing left to guard and would only re-pin the track to the
     * old quality. Keying it on the track id alone would miss exactly the A/B a user does after changing the
     * quality — re-selecting the SAME song, which media3 re-prepares from scratch.
     */
    private fun dropQualityPin() {
        qualityPinnedMediaId?.let { pinned ->
            songUrlCache.remove(pinned)
            qualityPinnedMediaId = null
            persistSongUrlCache()
        }
    }

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

    private var userExplicitlyExitedVideo: Boolean
        get() = playbackState.userExplicitlyExitedVideo
        set(value) { playbackState.userExplicitlyExitedVideo = value }

    /** One re-prepare per [mediaId] when video stalls in BUFFERING/IDLE (debounced). */
    private val videoStuckRecoveryAttemptedAt = java.util.concurrent.ConcurrentHashMap<String, Long>()
    private var videoStuckRecoveryJob: kotlinx.coroutines.Job? = null
    /**
     * Bumped on every enter/exit so an in-flight video URL resolve from a previous toggle cannot
     * swap the source after the user has already left (or re-entered) video mode.
     */
    private val videoSwapGeneration = java.util.concurrent.atomic.AtomicInteger(0)

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
     * Session cap for ALL speculative video-URL prefeches (audio-only mode). Always bounded — never
     * uncapped after first video use — so cipher/PoToken contention cannot hammer every video-capable
     * track transition and cut audible audio (heat/battery + stutter rule).
     */
    private var speculativeVideoPrefetches = 0

    /** Loader-thread audio resolves in flight — speculative video prefetch yields while > 0. */
    private val audioStreamResolveInFlight = java.util.concurrent.atomic.AtomicInteger(0)

    // DATA SAVER: cached mirror of DataSaverEnabledKey (collector in onCreate). The speculative-video
    // gates below run on the main thread, where a blocking dataStore read is not acceptable; @Volatile
    // because the collector writes from a coroutine. Default false = byte-identical behavior when OFF.
    @Volatile
    private var dataSaverEnabled = false

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

    // Extra PARTIAL_WAKE_LOCK + WifiLock while playing — belt beyond ExoPlayer WAKE_MODE_NETWORK
    // so screen-off Wi‑Fi/CPU sleep does not stall audio on aggressive OEMs of every brand.
    private val playbackKeepAlive by lazy { PlaybackKeepAlive(this) }

    private val screenStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    // INSTANT VIDEO SWAP: screen off = invisible → no speculative player may keep
                    // buffering video bytes (heat/battery rule). No-op when nothing is prepared.
                    teardownInstantVideoSwap("screen off")
                    // HyperOS ScreenOffCPUCheckKill: binder spam (widgets) with the screen off is a
                    // proven playback killer on the owner's device. Widgets are not visible anyway.
                    stopWidgetUpdates()
                    // Re-assert wake/wifi locks — some skins drop ExoPlayer's when the display blanks.
                    // Auto + screen-off is the HyperOS kill window: latch Auto state and refresh locks.
                    if (::player.isInitialized && player.isPlaying) {
                        playbackKeepAlive.setAndroidAutoConnected(isAndroidAutoControllerConnected())
                        playbackKeepAlive.refreshIfPlaying(true)
                    }
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
                        startWidgetUpdates()
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
            val autoSession = isAndroidAutoControllerConnected()
            val hasPlaybackRoute = addedDevices?.any {
                when (it.type) {
                    AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
                    AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
                    -> true
                    // Wireless Auto / some head units renegotiate as bus/USB/dock rather than classic A2DP.
                    AudioDeviceInfo.TYPE_BUS,
                    AudioDeviceInfo.TYPE_USB_ACCESSORY,
                    AudioDeviceInfo.TYPE_DOCK,
                    -> autoSession
                    else -> false
                }
            } == true

            if (hasPlaybackRoute) {
                val settingResume = dataStore.get(ResumeOnBluetoothConnectKey, false)
                val autoNoisyResume = autoSession && pausedByNoisy &&
                    System.currentTimeMillis() - pausedByNoisyAtMs <= 45_000L
                if ((settingResume || autoNoisyResume) &&
                    player.playbackState == Player.STATE_READY &&
                    !player.isPlaying
                ) {
                    pausedByNoisy = false
                    player.play()
                }
            }
            applyEqForCurrentOutput()
            applySpatialFromPrefs()
        }

        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) {
            super.onAudioDevicesRemoved(removedDevices)
            applyEqForCurrentOutput()
            applySpatialFromPrefs()
        }
    }

    private val becomingNoisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action != AudioManager.ACTION_AUDIO_BECOMING_NOISY) return
            // Only Auto: headphones "pause on disconnect" stays opt-in via ResumeOnBluetoothConnectKey.
            if (!isAndroidAutoControllerConnected()) return
            if (!::player.isInitialized) return
            if (player.isPlaying || player.playWhenReady) {
                pausedByNoisy = true
                pausedByNoisyAtMs = System.currentTimeMillis()
            }
        }
    }

    private val shutdownSaveReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SHUTDOWN,
                Intent.ACTION_REBOOT,
                -> {
                    if (!dataStore.get(PersistentQueueKey, true)) return
                    runCatching { saveQueueToDisk(synchronous = true) }
                    runCatching { savePlaybackPositionToDiskSynchronous() }
                }
            }
        }
    }

    /**
     * PowerAmp-style per-output EQ: when the active audio output changes (e.g. a Bluetooth speaker
     * connects/disconnects), apply the EQ profile the user assigned to it — or do nothing if none.
     * Switches the EQ bands live, reflects them in the EQ screen, and persists the choice.
     */
    /**
     * NO_REPEAT trace — one line per song start, so "sonó una repetida" stops being unfalsifiable.
     *
     * The owner reported a repeat and could not tell whether it happened in shuffle or in linear play.
     * Without this line a report like that is undiagnosable after the fact: the played set lives in
     * memory, the context is a field, and by the time he opens the log the state has moved on. Logged at
     * INFO because AppLogger only PERSISTS >= INFO — at DEBUG it would exist only in a debug build, which
     * is exactly where the bug does not happen.
     *
     * REPEAT=YES on a line whose src is the list (not the radio) is a genuine no-repeat failure; the same
     * line tells us which mode, which context, and how full the memory was when it happened.
     */
    private fun traceNoRepeat(reason: String) {
        runCatching {
            val id = player.currentMediaItem?.mediaId ?: player.currentMetadata?.id ?: return
            val ctx = shuffleContextId
            val wasPlayed = id in shufflePlayedIds
            Timber.tag(TAG).i(
                "NO_REPEAT %s id=%s mode=%s ctx=%s repeat=%s played=%d/%d radioSeeded=%b cover=%d done=%b",
                reason,
                id,
                if (player.shuffleModeEnabled) "SHUFFLE" else "LINEAR",
                ctx ?: "none",
                if (wasPlayed) "YES" else "no",
                shufflePlayedIds.size,
                player.mediaItemCount,
                radioSeedPool.isNotEmpty(),
                // COVERAGE, separate from the seed pool: 0 = unknown for this context (see contextCoverageId).
                // A REPEAT=YES line with cover=0 says the coverage guard was blind when it happened.
                currentContextCoverage(),
                ctx != null && ctx in completedShuffleContexts,
            )
        }
    }

    /**
     * Records the primary artist of a song that just STARTED, so the next shuffle-order rebuild knows what
     * was actually heard (see [recentShuffleArtists]). Called from BOTH advance paths — the normal
     * transition and the crossfade swap, which bypasses `onMediaItemTransition` entirely and is the path
     * every auto-advance takes with crossfade ON (the default).
     *
     * Consecutive duplicates are collapsed: two songs in a row by the same artist say exactly what one
     * says for a "how long ago" metric, and collapsing keeps an older artist in scope instead of letting a
     * repeat shift it out.
     */
    private fun rememberShuffleArtist(item: MediaItem?) {
        val artist = (item?.metadata ?: player.currentMetadata)
            ?.artists?.firstOrNull()?.name?.trim()?.lowercase()
        if (artist.isNullOrEmpty()) return
        if (recentShuffleArtists.lastOrNull() == artist) return
        recentShuffleArtists.addLast(artist)
        while (recentShuffleArtists.size > ShuffleOrdering.MAX_ARTIST_WINDOW) recentShuffleArtists.removeFirst()
    }

    /**
     * Returns the EQ preamp to 0.0 dB when the user turns Safe Volume OFF.
     *
     * WHY: the preamp is output make-up applied AFTER the limiter, so while Safe Volume is on it is
     * safe — the limiter catches whatever it pushes past full scale. Turn Safe Volume off and that
     * safety net disappears while the boost stays, so the very next loud master clips. Anyone who
     * raised the preamp to compensate for Safe Volume's level drop gets distortion the moment they
     * switch to bit-perfect playback, with nothing on screen linking the two settings.
     *
     * Only ever called on a real ON -> OFF transition (see the collector), never at startup, so a user
     * who deliberately runs with Safe Volume off keeps whatever preamp they set.
     *
     * Writes the same three places [applyEqForCurrentOutput] does: the raw `echo_eq_prefs` value the
     * audio processor reads, the unsaved profile (the DSP observes
     * `combine(activeProfile, unsavedProfile) { unsaved ?: active }`, so writing only the active one
     * would be overridden by a stale unsaved), and the live equalizer service. The SAVED profile is
     * deliberately left untouched — this is a safety correction, not an edit of the user's preset.
     */
    private fun resetEqPreamp() {
        if (!::eqProfileRepository.isInitialized) return
        val effective = eqProfileRepository.unsavedProfile.value ?: eqProfileRepository.activeProfile.value
        runCatching {
            getSharedPreferences("echo_eq_prefs", Context.MODE_PRIVATE)
                .edit()
                .putFloat("preampDb", 0f)
                .apply()
        }
        if (effective == null || effective.preamp == 0.0) return
        val flattened = effective.copy(preamp = 0.0)
        eqProfileRepository.setUnsavedProfile(flattened)
        if (::equalizerService.isInitialized) {
            runCatching { equalizerService.applyProfile(flattened) }
        }
        Timber.tag(TAG).i("Safe Volume turned off -> EQ preamp reset from %.1f dB to 0.0 dB", effective.preamp)
    }

    /**
     * An offload ENABLE that the gate has approved but that has deliberately not been applied yet.
     * Main-thread only: its writer (the gate collector) and both of its flush points
     * (onMediaItemTransition, onIsPlayingChanged) all run there.
     */
    private var pendingOffloadEnable = false

    /**
     * Publishes an [AudioOffloadGate] verdict to the players — ASYMMETRICALLY, on purpose.
     *
     * Changing the offload preference is a track re-selection, which makes media3 re-configure the
     * audio renderer: audible mid-song. That cost is worth paying in exactly one direction.
     *  - TURNING OFFLOAD OFF is an AUDIO-CORRECTNESS event and must happen NOW. The user has just
     *    switched the EQ / Safe Volume / crossfade ON, and until this lands their samples still leave
     *    the app untouched — a control that looks alive and is not. Correctness beats a click.
     *  - TURNING OFFLOAD ON is only a BATTERY event, and battery is never urgent. Applying it mid-song
     *    would spend an audible re-init on a saving that is just as available one song later. It would
     *    also cut short the ~300 ms ramp the native bridge runs when Safe Volume is switched OFF (it
     *    keeps the chain alive while safeVolumeGainCurrent != 1.0f precisely so the level glides
     *    instead of stepping) — tearing the sink down mid-glide turns that into the level jump the
     *    ramp exists to avoid. So it is stashed and flushed at the next natural boundary.
     *
     * A later veto always wins: a pending enable is dropped the moment the gate says "blocked".
     */
    private fun publishOffloadDecision(allowOffload: Boolean) {
        if (!allowOffload) {
            pendingOffloadEnable = false
            applyOffloadRequest(false)
            return
        }
        if (audioOffloadHint) return
        if (player.isPlaying) {
            pendingOffloadEnable = true
            Timber.tag(TAG).i("Offload allowed but deferred to the next track boundary (mid-song re-init avoided)")
        } else {
            applyOffloadRequest(true)
        }
    }

    /**
     * Flush point for a deferred offload ENABLE — called only where a renderer re-init costs nothing
     * audible (a track boundary, or playback not running). Safe on the crossfade-swap path by
     * construction: a pending enable can only exist while the gate is open, and the gate is closed
     * whenever crossfade can run, so no swap is ever in flight here.
     */
    private fun flushPendingOffloadEnable() {
        if (!pendingOffloadEnable) return
        pendingOffloadEnable = false
        applyOffloadRequest(true)
    }

    private fun applyOffloadRequest(enabled: Boolean) {
        audioOffloadHint = enabled
        player.setOffloadEnabled(enabled)
        secondaryPlayer?.setOffloadEnabled(enabled)
    }

    private fun applyEqForCurrentOutput() {
        if (!::eqProfileRepository.isInitialized || !::equalizerService.isInitialized) return
        scope.launch {
            val store = iad1tya.echo.music.eq.data.EqDeviceProfileStore
            // Feature off or no assignment → leave the live EQ exactly as the user left it.
            // Never call equalizerService.disable() / putBoolean("enabled", false) from this path.
            if (!store.isAutoApplyEnabled(this@MusicService)) return@launch
            val key = store.currentOutputKey(this@MusicService)
            val profileId = store.assignedProfileId(this@MusicService, key) ?: return@launch
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

    /**
     * Pushes the live Superpowered spatial stage to every player processor. Device routing is
     * resolved here so a HDMI/soundbar path is a real bypass (not HRTF on room speakers).
     */
    private fun applySpatialFromPrefs() {
        val profile = SpatialAudioProfile.fromName(spatialProfileNameHint)
        val kind = SpatialAudioProfile.detectOutputKind(this)
        val live = spatialEnabledHint && kind != SpatialOutputKind.BYPASS
        val algorithm = SpatialAudioProfile.nativeAlgorithm(profile, kind)
        val params = profile.toNativeParams()
        playerEqProcessors.values.forEach { it.applySpatial(live, algorithm, params) }
    }

    private fun applyTidalFromPrefs() {
        playerEqProcessors.values.forEach { it.applyTidalSimulation(tidalEnabledHint) }
    }

    override fun onCreate() {
        super.onCreate()
        isRunning = true

        // Catch ForegroundServiceStartNotAllowedException (e.g. when playback is (re)started while the app
        // is in the background) so it's logged/reported instead of crashing. (From upstream Echo-Music.)
        setListener(object : Listener {
            override fun onForegroundServiceStartNotAllowedException() {
                // Verified against the media3 1.10.1 bytecode: the throw happens INSIDE
                // MediaNotificationManager.startForeground, BEFORE setForegroundServiceNotification — so
                // this cycle posts NO notification at all and the service stays backgrounded and reapable.
                // Catching it avoids the crash, but silently leaves audio playing with no notification and
                // no transport controls, which then dies whenever the system decides to reclaim it.
                // Pausing turns that into something the user can see and act on.
                Timber.tag(TAG).w("Foreground service start refused by the system — pausing instead of playing unmanaged")
                runCatching {
                    if (::player.isInitialized && player.playWhenReady) player.pause()
                    // The crossfade renders through SEPARATE ExoPlayer instances. If the refusal lands
                    // mid-fade, pausing only the primary leaves the other one audible — the same
                    // "playing with no notification, no transport, reapable" state, just for ~9s.
                    // Pause only: no change to the fade timing, curve or swap logic.
                    secondaryPlayer?.pause()
                    fadingPlayer?.pause()
                }.onFailure {
                    // media3 asserts the application thread on every call. If this listener is ever
                    // dispatched off the main looper the pause silently would not happen — without this,
                    // "paused" and "failed to pause" looked identical from the outside.
                    Timber.tag(TAG).w(it, "Could not pause after the foreground-service refusal")
                }
                // Deliberately NOT reportException: on Android 12+ this is a routine, expected refusal
                // (background start without an exemption), not a defect. It was filing a Crashlytics
                // non-fatal every time — the owner's log shows three in one second from a single event.
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
        playerInitialized.value = true
        Timber.tag(TAG).d("Player successfully initialized")

        // FIX B1 (#28.1): rehydrate the in-memory stream-URL cache from DataStore so the first play/resume
        // after an app-update restart can serve a still-valid resolved URL instead of re-running the slow
        // resolver. Best-effort, off the main thread; only non-expired entries are restored.
        loadPersistedSongUrlCache()

        // Warm up the poToken WebView shortly after startup so the FIRST song starts faster (the slow
        // botguard/WebView init happens ahead of play time instead of when you press play). Fully guarded;
        // no-ops if the session/WebView isn't ready yet. Delayed so cipher init + visitorData settle first.
        // Enhanced Shuffle: one-shot orphan prune — drop persistent no-repeat memory for "PL:<id>" contexts
        // whose playlist was deleted (the tables are FK-less by design, so nothing cascades). Bounds growth
        // without hooking every playlist-delete site. Cheap, best-effort, off the main thread.
        scope.launch(Dispatchers.IO) {
            runCatching {
                database.pruneOrphanEnhancedPlayed()
                database.pruneOrphanEnhancedContext()
            }
        }

        scope.launch(Dispatchers.IO) {
            kotlinx.coroutines.delay(500)
            // Prewarm the signature timestamp (memoized in YTPlayerUtils; each resolve now starts it
            // ASYNC and only sts-using clients await it): computing it cold means NewPipe's ~2.8 MB
            // player.js fetch/parse on the first resolve's critical path. Gated like the other warmups:
            // MID/HIGH tier only (LOW/ULTRA keep the lazy-on-first-resolve path — their pre-batch
            // behavior) and never under Data Saver (a ~2.8 MB startup download is exactly what that
            // switch promises to avoid). Fire-and-forget — returns immediately.
            val warmTierEarly = iad1tya.echo.music.utils.PerformanceMode.effectiveTier(this@MusicService)
            val dataSaverOn = dataStore.get(iad1tya.echo.music.constants.DataSaverEnabledKey, false)
            if (!dataSaverOn &&
                (warmTierEarly == iad1tya.echo.music.utils.DeviceTier.MID ||
                    warmTierEarly == iad1tya.echo.music.utils.DeviceTier.HIGH)
            ) {
                runCatching { iad1tya.echo.music.utils.YTPlayerUtils.prewarmSignatureTimestamp() }
            }
            // Also warm the cipher player.js + WebView so the FIRST song's URL resolution is fast too (and
            // stays warm/reused for every song after). On MID/HIGH tier run it in PARALLEL with the poToken
            // prewarm (two WebViews at once is fine on capable RAM) so both are ready sooner; on LOW/ULTRA
            // keep it sequential so two WebViews don't spin up at once on weak/low-RAM devices. Best-effort.
            // DATA SAVER gates the cipher prewarm too (thermal/battery audit): prewarmCipher triggers the
            // ~2 MB base.js download when the 6 h disk cache is cold — the exact class of startup download
            // Data Saver promises to avoid, and it ran UNGATED on every tier while the sts prewarm right
            // above was correctly gated. Under Data Saver the cipher warms lazily on the first resolve.
            val warmTier = iad1tya.echo.music.utils.PerformanceMode.effectiveTier(this@MusicService)
            if (!dataSaverOn) {
                if (warmTier == iad1tya.echo.music.utils.DeviceTier.MID ||
                    warmTier == iad1tya.echo.music.utils.DeviceTier.HIGH
                ) {
                    scope.launch(Dispatchers.IO) {
                        runCatching { iad1tya.echo.music.utils.YTPlayerUtils.prewarmCipher() }
                    }
                    // PoToken prewarm — MID/HIGH only. This call used to be a SILENT NO-OP for everyone
                    // (gated on MAIN_CLIENT.useWebPoTokens, which is false for ANDROID_VR); with the gate
                    // fixed in YTPlayerUtils it really warms the WebView token used by the WEB_REMIX/TVHTML5
                    // fallback clients (~2-5s blocking in THIS background coroutine, 8s cap). LOW/ULTRA
                    // devices deliberately skip it — no startup WebView cost there, they keep the
                    // lazy-on-first-need path, which is exactly today's effective behavior for them.
                    runCatching { iad1tya.echo.music.utils.YTPlayerUtils.prewarmPoToken() }
                } else {
                    runCatching { iad1tya.echo.music.utils.YTPlayerUtils.prewarmCipher() }
                }
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
            // The callback's `service` field was declared lateinit in the original import and NEVER
            // assigned anywhere in this repo's history, so every `::service.isInitialized` check in it was
            // permanently false and every unguarded use would have thrown. Assign it here, before
            // MediaLibrarySession.Builder below, so external controllers (Android Auto, watches,
            // assistants) can reach the service. Qualified receiver: inside apply{} a bare `this` is the
            // callback, not the service.
            service = this@MusicService
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
        enhancedShuffleHint = prefs[EnhancedShuffleKey] ?: true
        previousQueueOfferEnabledHint = prefs[PreviousQueueOfferKey] ?: true
        fadeOnManualChangeHint = prefs[iad1tya.echo.music.constants.FadeOnManualChangeKey] ?: true


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
        registerReceiver(
            becomingNoisyReceiver,
            IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY),
        )
        val shutdownFilter = IntentFilter().apply {
            addAction(Intent.ACTION_SHUTDOWN)
            addAction(Intent.ACTION_REBOOT)
        }
        registerReceiver(shutdownSaveReceiver, shutdownFilter)

        // DATA SAVER: the eager init must already reflect the forced-Opus effective quality — the
        // collector below re-asserts it, but a resolve racing the first emit must never go out Hi-Res.
        audioQuality = if (prefs[iad1tya.echo.music.constants.DataSaverEnabledKey] == true) {
            iad1tya.echo.music.constants.AudioQuality.OPUS
        } else {
            prefs[AudioQualityKey].toEnum(iad1tya.echo.music.constants.AudioQuality.OPUS)
        }
        ipVersion = prefs[IpVersionKey].toEnum(IpVersion.AUTO)
        // Repair: a persisted ~0 volume means it was captured mid-crossfade/duck by the old bug (a real
        // "I want silence" never persists as 0 — the user pauses/mutes instead). Treat it as full.
        playerVolume = MutableStateFlow(
            (prefs[PlayerVolumeKey] ?: 1f).let { if (it.isNaN() || it < 0.05f) 1f else it.coerceIn(0f, 1f) },
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
                .map { prefs ->
                    val quality = prefs[AudioQualityKey]?.let { value ->
                        iad1tya.echo.music.constants.AudioQuality.entries.find { it.name == value }
                    } ?: iad1tya.echo.music.constants.AudioQuality.OPUS
                    // DATA SAVER: force Opus while the switch is ON. Only ever downgrades (Opus is the
                    // lowest tier); the persisted AudioQualityKey is untouched, so the user's chosen
                    // quality comes back the moment the switch goes OFF.
                    if (prefs[iad1tya.echo.music.constants.DataSaverEnabledKey] == true) {
                        iad1tya.echo.music.constants.AudioQuality.OPUS
                    } else {
                        quality
                    }
                }
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

                    // NOT `?: return@collect`: changing quality with nothing loaded is the COMMON case (the user is
                    // sitting in Settings, player empty/idle). Returning early skipped the clear entirely, so every
                    // URL already resolved this session stayed pinned at the old quality.
                    val mediaId = player.currentMediaItem?.mediaId
                    val currentUrl = mediaId?.let { songUrlCache[it] }

                    // Clear cache for upcoming songs so they fetch the new quality
                    songUrlCache.clear()

                    // Restore the currently playing song's URL so it doesn't break. This surviving entry IS the
                    // mid-song container lock: it carries the quality it was resolved at, so a re-resolve of the
                    // in-flight track (e.g. a seek past the buffer) keeps its container instead of swapping it
                    // under the decoder. Every OTHER song is now uncached → next play resolves at the new quality.
                    if (mediaId != null && currentUrl != null) {
                        songUrlCache[mediaId] = currentUrl
                        qualityPinnedMediaId = mediaId
                    } else {
                        qualityPinnedMediaId = null
                    }

                    // Re-persist NOW (#28): the blob is the cross-restart mirror of this map. Without this write the
                    // DataStore copy kept the OLD-quality URLs and the next cold start re-seeded exactly what we
                    // just cleared — the quality change silently undone by a restart. Off-main, best-effort.
                    persistSongUrlCache()

                    // Re-trigger prefetch to fetch the next songs in the new quality. No-op on an empty queue
                    // (guards on INDEX_UNSET), so this is safe now that a null mediaId reaches here.
                    preloadUpcomingItems()
                }
        }

        // DATA SAVER: keep the main-thread mirror current for the speculative-video gates
        // (prefetchCurrentVideoUrl / maybeWarmVideoConnection / maybePrepareInstantVideoSwap).
        scope.launch {
            dataStore.data
                .map { it[iad1tya.echo.music.constants.DataSaverEnabledKey] ?: false }
                .distinctUntilChanged()
                .collect { dataSaverEnabled = it }
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
                .map { it[iad1tya.echo.music.constants.SponsorBlockEnabledKey] ?: true }
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
            // DATA SAVER: ListenBrainz submissions are background network — gated OFF while ON
            // (the user's ListenBrainzEnabledKey stays persisted and resumes when the switch goes OFF).
            dataStore.data.map {
                val listenBrainz = it[iad1tya.echo.music.constants.ListenBrainzEnabledKey] ?: false
                val dataSaver = it[iad1tya.echo.music.constants.DataSaverEnabledKey] ?: false
                if (dataSaver) false else listenBrainz
            }.distinctUntilChanged().collect {
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
            // DATA SAVER: no automatic lyrics fetch while ON (manual lookups in the UI still work).
            dataStore.data.map {
                val showLyrics = it[ShowLyricsKey] ?: false
                val dataSaver = it[iad1tya.echo.music.constants.DataSaverEnabledKey] ?: false
                if (dataSaver) false else showLyrics
            }.distinctUntilChanged(),
        ) { mediaMetadata, showLyrics ->
            mediaMetadata to showLyrics
        }.collectLatest(scope) { (mediaMetadata, showLyrics) ->
            if (showLyrics && mediaMetadata != null) {
                val stored = database.lyrics(mediaMetadata.id).first()
                if (stored == null) {
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
                } else {
                    // WRONG-SONG LYRICS REPAIR. A row that already exists is never re-fetched, so a
                    // lyric that LrcLib mis-matched by duration alone would stay wrong on this device
                    // forever - fixing the matcher does nothing for anyone who already hit the bug.
                    // Re-verify it once, here, at the moment it is about to be displayed. Rows that
                    // are already verified or that the user wrote themselves return immediately
                    // without a query or a request; see LyricsMatchRepair for the safety guards.
                    // Inside collectLatest, so skipping to another song cancels the check.
                    iad1tya.echo.music.lyrics.LyricsMatchRepair.verifyAndRepair(
                        database = database,
                        lyricsHelper = lyricsHelper,
                        mediaMetadata = mediaMetadata,
                        stored = stored,
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
            // Tracks the PREVIOUS value so the preamp reset below fires only on a real user toggle.
            // This flow also emits once at startup with the CURRENT value: acting on that emission would
            // wipe the user's own preamp every single launch for anyone who keeps Safe Volume off.
            var previouslyEnabled: Boolean? = null
            dataStore.data
                .map { it[SafeVolumeEnabledKey] ?: true }
                .distinctUntilChanged()
                .collect { enabled ->
                    val wasEnabled = previouslyEnabled
                    previouslyEnabled = enabled
                    safeVolumeEnabledHint = enabled // mirror for the crossfade pre-level
                    setupLoudnessEnhancer()
                    if (wasEnabled == true && !enabled) resetEqPreamp()
                }
        }

        scope.launch {
            combine(
                dataStore.data.map { it[SpatialAudioEnabledKey] ?: false }.distinctUntilChanged(),
                dataStore.data.map { it[SpatialAudioProfileKey] ?: SpatialAudioProfile.WIDE_SURROUND.name }
                    .distinctUntilChanged(),
            ) { enabled, profileName -> enabled to profileName }
                .collect { (enabled, profileName) ->
                    spatialEnabledHint = enabled
                    spatialProfileNameHint = profileName
                    applySpatialFromPrefs()
                }
        }

        scope.launch {
            dataStore.data
                .map { it[iad1tya.echo.music.constants.TidalSimulationEnabledKey] ?: false }
                .distinctUntilChanged()
                .collect { enabled ->
                    tidalEnabledHint = enabled
                    applyTidalFromPrefs()
                }
        }

        // NOTE (0.6.145): two collectors used to sit here, feeding AudioEnhanceProcessor.enabled and
        // JrDspAudioProcessor.config from DataStore. Both target classes are inert stubs — isActive()
        // returns false and NEITHER is ever inserted into any AudioProcessorChain (see
        // createRenderersFactory, which builds the chain from silence/eq/norm/limiter only) — so those
        // writes reached nothing at all, and no screen renders the keys any more (SoundSettings dropped
        // them). They mattered here because the OLD offload gate vetoed on those same keys, i.e. a dead
        // stub was holding the gate permanently shut. The gate below no longer consults them, so the
        // dead wiring is gone with it. The preference keys survive only inside SoundEffectsSnapshot
        // (backup/restore of raw values), which is unaffected.

        // AUDIO OFFLOAD GATE — reduced to terms backed by LIVE code (0.6.142).
        //
        // Offload hands the ENCODED stream to the DSP hardware: the decoder stops producing PCM on the CPU,
        // so no AudioProcessor runs at all. Anything that really processes audio must therefore veto it.
        //
        // The previous gate vetoed on a seven-term OR, four of whose terms named processors that DO NOTHING:
        //   AudioNormalizationKey  -> NormalizationGainAudioProcessor.isActive() == false (inert stub); the key
        //                             only writes the dead NormalizationGainAudioProcessor.gain /
        //                             TruePeakLimiterAudioProcessor.loudnessMakeup statics. Every path that
        //                             actually moves the volume is gated on safeVolumeEnabledHint, not on this.
        //   AuraSignatureToneEnabledKey + JrLoudness/JrExciter/JrStereoWidth/JrDialogue
        //                          -> JrDspAudioProcessor, which is an inert stub AND is never inserted into any
        //                             AudioProcessorChain (createRenderersFactory builds the chain from
        //                             silence/eq/norm/limiter only); MusicService just assigns its static config.
        //   AudioEnhanceEnabledKey -> AudioEnhanceProcessor.isActive() == false, also never in any chain.
        // Worse, the toggles for AudioNormalizationKey and AuraSignatureToneEnabledKey were removed from
        // SoundSettings, so both were pinned at their `true` default with no way back — the OR could never be
        // false and the "Audio offload" switch in PlayerSettings was a placebo for 100% of users.
        //
        // What survives, each verified to process audio for real:
        //   - Crossfade: two ExoPlayers plus continuous volume ramps, AND the sink-level PCM tap the
        //     level-based segue reads — none of which exist on an offloaded (still encoded) stream.
        //     Behaviour untouched; this is still the term the PlayerSettings copy refers to. Read as
        //     "crossfade is actually RUNNING", not "the key is set": High-Performance Mode force-disables
        //     crossfade at every live site (the crossfadeEnabled collector below ANDs !HighPerformanceMode,
        //     and scheduleCrossfade / onTailSilenceDetected / maybePrepareInstantVideoSwap each re-check
        //     highPerformanceModeHint), so under HPM no second player is ever built and the key alone is a
        //     dead veto — on exactly the weak devices offload helps most. The Listen Together room state,
        //     which also suppresses crossfade, is deliberately NOT read here: it flips mid-session and
        //     offload changes are a track re-selection, so a room join would cost an audible re-init for a
        //     saving that lasts as long as the room. Vetoing through it is the conservative direction.
        //   - Safe Volume: a LIVE native Superpowered stage (applySafeVolume -> setSafeVolume) inside
        //     CustomEqualizerAudioProcessor. Default ON.
        //   - The EQ actually being active. This is NOT a preference key — the DSP source of truth is the
        //     eqProfileRepository profile the collector above feeds to applyProfile()/disable(). The old gate
        //     never mentioned the EQ at all and only shielded it by accident, through the dead
        //     AuraSignatureToneEnabledKey term. Reading the repository makes the veto explicit and, because
        //     these are hot Flows, re-evaluates the moment the user changes or clears the profile mid-session
        //     (the DataStore flows do the same for crossfade and Safe Volume — this gate is never stale).
        //     That ONE term also covers the EQ PREAMP and the DE-ESSER, and it has to: in
        //     SuperpoweredBridge.processAudio the preamp (frontGain) and the de-esser both live INSIDE the
        //     runEq branch, and neither has a toggle of its own — the preamp is a field of the profile, and
        //     the de-esser is unconditional whenever the EQ is on. A FLAT profile therefore still processes,
        //     which is why the term is "a profile is applied", not "the profile is audibly non-flat".
        //   - Silence / tail detection needs NO term: tailDetectEnabled is armed only by scheduleCrossfade,
        //     downstream of its `if (!crossfadeEnabled ...) return`, and instant skip-silence is hardcoded
        //     off — so it cannot be running while the crossfade term is false. Anything that ever arms tail
        //     detection WITHOUT crossfade has to add a term here, or it will measure encoded garbage.
        //
        // INTERACTION WITH THE hiResDsp FLOAT TAKEOVER (createRenderersFactory's ForwardingAudioSink): the two
        // are mutually exclusive BY CONSTRUCTION and cannot both claim the stream. The takeover only arms when
        // MimeTypes.AUDIO_RAW == inputFormat.sampleMimeType; an offloaded track reaches the sink still encoded
        // (audio/mp4a-latm, audio/opus, ...), so delegateWouldSkipChain is false, hiResDsp stays null and the
        // takeover does not run. Offload OFF -> the decoder emits raw PCM and the EQ runs either in media3's
        // int16 chain or, on hi-res float, in the takeover. So "offload ON" always means "no DSP wanted", which
        // is exactly what this gate now guarantees. Getting this wrong in either direction would silently
        // bypass the EQ again — the bug the takeover was added to fix.
        //
        // This gate decides only the offload MODE. What the app needs offload to be CAPABLE of —
        // gapless always, speed change while tempo/pitch are non-default — is folded in by
        // setOffloadEnabled itself, which reads the player's current playback parameters on every
        // call. That keeps this collector and the onPlaybackParametersChanged re-publish from ever
        // producing two different requests, and it can only make the selector refuse offload, never
        // accept it where this gate said no.
        //
        // The predicate itself lives in AudioOffloadGate (playback/audio) instead of in this lambda, for
        // the same reason CrossfadeMath and CrossfadeLyricsPin were lifted out: a decision that can
        // silence the EQ has to be unit-testable, and nothing inside MusicService is. This collector is
        // now only plumbing — flows in, publication out.
        //   - Spatial audio: a LIVE native Superpowered stage (applySpatial -> setSpatial) inside
        //     CustomEqualizerAudioProcessor. Default OFF. When on it must veto offload or the EQ
        //     screen's spatial switch would be a placebo.
        combine(
            dataStore.data.map { it[AudioOffload] ?: false }.distinctUntilChanged(),
            dataStore.data.map { p ->
                (p[CrossfadeEnabledKey] ?: false) to
                    (p[iad1tya.echo.music.constants.HighPerformanceModeKey] ?: false)
            }.distinctUntilChanged(),
            dataStore.data.map { it[SafeVolumeEnabledKey] ?: true }.distinctUntilChanged(),
            combine(
                eqProfileRepository.activeProfile,
                eqProfileRepository.unsavedProfile,
            ) { active, unsaved -> (unsaved ?: active) != null }.distinctUntilChanged(),
            combine(
                dataStore.data.map { it[SpatialAudioEnabledKey] ?: false }.distinctUntilChanged(),
                dataStore.data.map { it[iad1tya.echo.music.constants.TidalSimulationEnabledKey] ?: false }.distinctUntilChanged()
            ) { spatial, tidal -> spatial || tidal },
        ) { offloadPref, (crossfadeKey, perfMode), safeVolume, eqActive, spatialOrTidal ->
            AudioOffloadGate.allowOffload(
                AudioOffloadGate.Inputs(
                    userWantsOffload = offloadPref,
                    crossfadeEnabled = crossfadeKey,
                    highPerformanceMode = perfMode,
                    safeVolumeEnabled = safeVolume,
                    equalizerActive = eqActive,
                    spatialEnabled = spatialOrTidal,
                ),
            )
        }.distinctUntilChanged()
        .collectLatest(scope) { useOffload ->
            publishOffloadDecision(useOffload)
        }

        // P33 — keep the memory mirrors for the player-thread hot paths (onMediaItemTransition /
        // onPlaybackStatsReady) in sync, so those callbacks read a @Volatile field instead of a blocking
        // runBlocking DataStore read on the main thread. Same defaults as the original dataStore.get calls.
        scope.launch {
            dataStore.data.collect { prefs ->
                autoLoadMoreHint = prefs[AutoLoadMoreKey] ?: true
                disableLoadMoreWhenRepeatAllHint = prefs[DisableLoadMoreWhenRepeatAllKey] ?: false
                enhancedShuffleHint = prefs[EnhancedShuffleKey] ?: true
                val previousQueueOfferEnabled = prefs[PreviousQueueOfferKey] ?: true
                previousQueueOfferEnabledHint = previousQueueOfferEnabled
                // Turning the switch OFF must also retire an offer already armed: leaving it live would
                // hand him a prompt he just asked never to see. (This collector is on the Main scope, the
                // same thread that writes the snapshot in playQueue.)
                if (!previousQueueOfferEnabled && previousQueueSnapshot != null) {
                    dismissPreviousQueueOffer()
                }
                fadeOnManualChangeHint = prefs[iad1tya.echo.music.constants.FadeOnManualChangeKey] ?: true
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
                    prefs[CrossfadeDurationKey] ?: 5f,
                    prefs[CrossfadeGaplessKey] ?: false
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
                        // isNaN: coerceIn propagates NaN (every comparison is false), so a corrupt persisted
                        // volume stuck the player silent/lowered forever — same repair as the boot read.
                        playerVolume.value = playerState.volume.let { if (it.isNaN() || it < 0.05f) 1f else it.coerceIn(0f, 1f) }

                        
                        // Two indices disagree here, and the intuitive choice is the WRONG one.
                        //
                        // The player's current index came from the QUEUE file, which is only rewritten when
                        // the timeline actually changes (0.6.139 stopped rewriting it every 10 s). This
                        // state file is written every ~10 s, so ITS index is the fresher one — it names the
                        // song that was really playing. Preferring the "anchored" index therefore resumes
                        // on an EARLIER song, which is worse than the filter skew it was meant to avoid.
                        //
                        // So: keep using the fresher saved index. The residual skew (filters dropping items
                        // BEFORE the current song shift this index by k) is bounded and pre-existing;
                        // fixing it properly means persisting the current song's ID and anchoring by id,
                        // which is a change to the persisted format and belongs in its own release.
                        if (playerState.currentMediaItemIndex < player.mediaItemCount) {
                            player.seekTo(playerState.currentMediaItemIndex, playerState.currentPosition)
                        }

                        // Restore shuffle from the PER-QUEUE snapshot, not from ShuffleModeKey.
                        //
                        // ShuffleModeKey stores the user's last TOGGLE, which is deliberately no longer
                        // overwritten by the programmatic per-queue reset — otherwise the app's own reset
                        // erased the remembered preference. But that makes it the wrong source here: turn
                        // shuffle on for playlist A, then start playlist B (correctly un-shuffled), play B in
                        // order, kill the app — and a boot-time read of ShuffleModeKey would bring B back
                        // SHUFFLED, which the user never asked for.
                        //
                        // playerState already carries the shuffle state of THIS queue (persisted on every
                        // queue change and every 10s of playback) and was being written and then ignored.
                        // Setting it here also lands AFTER the queue is populated, so the listener can
                        // actually build a shuffle order — the boot-time read runs while the player is still
                        // empty, where onShuffleModeEnabledChanged early-returns on mediaItemCount == 0.
                        //
                        // …but landing after the queue is populated is NOT sufficient on its own: media3
                        // ignores setShuffleModeEnabled when the value is unchanged, so it fires no event.
                        // On a restore the flag is ALREADY true (the boot read set it from ShuffleModeKey,
                        // and playQueue deliberately skips its reset-to-false when isRestore), so assigning
                        // true here was a silent no-op and the shuffle session never started: empty played
                        // set, media3's own random order, and the persistent memory sitting unread in the
                        // DB — i.e. songs heard yesterday coming straight back. Start it explicitly when
                        // the assignment cannot fire the listener itself.
                        if (dataStore.get(RememberShuffleAndRepeatKey, true)) {
                            val wanted = playerState.shuffleModeEnabled
                            val listenerWillFire = player.shuffleModeEnabled != wanted
                            // Restoring shuffle is the APP re-installing a previous state, not the user
                            // re-activating it — and only the latter may reset a finished list's memory.
                            // Covers both deliveries: the listener (media3 dispatches it synchronously
                            // inside the assignment) and the explicit call for the silent no-op case.
                            suppressShuffleActivationReset = true
                            try {
                                player.shuffleModeEnabled = wanted
                                if (wanted && !listenerWillFire) beginShuffleSession(isUserActivation = false)
                            } finally {
                                suppressShuffleActivationReset = false
                            }
                        }
                    }
                }.onFailure { error ->
                    Timber.tag(TAG).w(error, "Failed to read player state, clearing data")
                    clearPersistedQueueFiles()
                }
            }
        }

        
        // #55 (battery/heat): this tick is deliberately NOT gated on isPlaying — a queue edited while paused
        // must still survive a kill — but saveQueueToDisk() serializes THREE full object graphs with
        // ObjectOutputStream, and re-running that every 30s for the entire life of the service (paused, screen
        // off, forever) on an unchanged queue was pure CPU + flash wear. Skip when nothing changed instead.
        scope.launch {
            while (isActive) {
                delay(30.seconds)
                if (dataStore.get(PersistentQueueKey, true) && queueDirty) {
                    queueDirty = false
                    saveQueueToDisk()
                }
            }
        }

        
        scope.launch {
            while (isActive) {
                delay(10.seconds)
                // POSITION only — not saveQueueToDisk(). This loop serialized THREE full object graphs
                // (queue + automix + state, O(N) getMediaItemAt walk on Main) every 10 s of playback:
                // on a 5000-item radio queue that was ~1 MB of flash writes six times a minute, all to
                // refresh a position the periodic persist already saves.
                // COHERENCE guard: while queueDirty, the queue FILE still describes the OLD timeline —
                // writing a position/index measured against the NEW one would desync the pair, and a
                // process kill in that window restored the wrong song (index applied to the old queue).
                // So a dirty queue takes the full save here (clearing the flag); clean queues take the
                // cheap position-only write. Net cost: one full save per real mutation, not six/minute.
                if (dataStore.get(PersistentQueueKey, true) && player.isPlaying) {
                    if (queueDirty) {
                        queueDirty = false
                        saveQueueToDisk()
                    } else {
                        runCatching { savePlaybackPositionToDisk() }
                    }
                }
            }
        }
    }

    private fun createExoPlayer(isSecondary: Boolean = false): ExoPlayer {
        // The Context is what lets the processor resolve the Superpowered licence key, which is bound to
        // this app's signing certificate (see SuperpoweredLicense). It keeps only applicationContext.
        val eqProcessor = CustomEqualizerAudioProcessor(this)
        equalizerService.addAudioProcessor(eqProcessor)

        val silenceProcessor = iad1tya.echo.music.playback.audio.SilenceDetectorAudioProcessor {
            // TAIL DETECTION → early crossfade. Fires on the audio pipeline thread, ONLY while
            // tailDetectEnabled (armed exclusively for the CURRENT player during the final stretch — up
            // to ~30s — by scheduleCrossfade's tail-arm job; the secondary player is never armed). Two
            // tiers, told apart on the Main hop: true silence (dead tail) and "musical end" (~-25 dBFS,
            // the mastered fade-out) — either way the fade starts at the end of the MUSIC, not the FILE.
            // (The old lambda was a dead debug log; the skip-silence feature itself remains hardcoded OFF.)
            scope.launch { onTailSilenceDetected() }
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
                        // Songs must start as soon as the first packets arrive. 2.5–4s was a video-merge
                        // cushion that made every skip feel late. Keep a short start gate; rebuffer still
                        // waits longer so a stall does not ping-pong STATE_BUFFERING.
                        if (useSmallBuffer) 400 else 700,
                        if (useSmallBuffer) 1_500 else 2_000,
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
        if (!isSecondary) currentEqProcessor = eqProcessor
        playerSilenceProcessors[player] = silenceProcessor
        playerNormProcessors[player] = normProcessor
        playerLimiterProcessors[player] = limiterProcessor
        applySpatialFromPrefs()

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
        // ONE per failure, not two. With `+= 2` against MAX_CONSECUTIVE_ERR = 5 the real budget was 2 skips,
        // so THREE unresolvable songs in a row hard-paused playback. On a slower device — where resolves
        // routinely hit the 30s timeout and surface as "song unavailable" — that is exactly the reported
        // "one song plays, the next doesn't, then it just stops". The counter now means what its name says.
        consecutivePlaybackErr += 1
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
        val customLayout = listOf(
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
        )
        mediaSession.setCustomLayout(customLayout)
        // #44 — ALSO push it to the media-notification controller, or the like never updates in Android Auto.
        // Verified in the media3 1.10.1 bytecode: the GLOBAL setCustomLayout(list) only does
        // `sessionLegacyStub.customLayout = list` — a field write. The PER-CONTROLLER overload additionally
        // calls updateLegacySessionPlaybackState(), which is what actually rebuilds and BROADCASTS the
        // PlaybackStateCompat carrying the custom actions. Android Auto is a LEGACY client and reads the heart
        // from that PlaybackStateCompat, so with only the global call it kept showing the stale icon until some
        // PLAYER event (play/pause, track change, seek) republished the state by another route — and a "like"
        // changes nothing about the player. The phone notification was unaffected because its provider is
        // re-invoked separately, which is exactly why this looked like an Auto-only bug.
        // Additive and idempotent; null until that controller connects.
        mediaSession.mediaNotificationControllerInfo?.let { mediaSession.setCustomLayout(it, customLayout) }
    }

    private suspend fun recoverSong(
        mediaId: String,
        playbackData: YTPlayerUtils.PlaybackData? = null,
        // True ONLY when the bytes are served from a user-initiated DOWNLOAD (full or partial hit of the
        // downloadCache): the user downloaded precisely to avoid network use, so the metadata/related
        // lookups below must not burn data in the background. Streaming-cache hits stay false — their
        // related prefetch feeds Mix-from-Playlist / Home and belongs to an online session.
        isOfflinePlayback: Boolean = false,
    ) {
        val song = database.song(mediaId).first()
        val mediaMetadata = withContext(Dispatchers.Main) {
            player.findNextMediaItemById(mediaId)?.metadata
        } ?: return
        val duration = song?.song?.duration?.takeIf { it != -1 }
            ?: mediaMetadata.duration.takeIf { it != -1 }
            ?: if (isOfflinePlayback) {
                -1 // downloaded playback: never hit the network just to fill in a duration
            } else {
                (playbackData?.videoDetails ?: YTPlayerUtils.playerResponseForMetadata(mediaId)
                    .getOrNull()?.videoDetails)?.lengthSeconds?.toInt() ?: -1
            }
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
        if (!isOfflinePlayback && !database.hasRelatedSongs(mediaId)) {
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

        // LAST instant the outgoing queue is still reachable — the next line drops it. A playlist -> album
        // jump snapshots it here so the user can be offered a way back; every other transition (including
        // the boot restore, whose outgoing queue is EmptyQueue with a null context) is a no-op.
        captureQueueForResumeOffer(queue, isRestore)

        // The shuffle memoization caches describe the queue that is being replaced right here: from this
        // point every entry in them is keyed to a media id that is about to leave the timeline. Dropping
        // them is what gives [shuffleArtistCache] a bounded lifetime (it has no other invalidation) and
        // it costs nothing measurable — a brand-new queue's ids miss the cache anyway. Order-neutral by
        // construction: both values are re-derived, identically, on demand.
        clearShuffleCaches()

        currentQueue = queue
        queueTitle = null
        // Arm the adoption latch: from here until the context is adopted below, the transition path must
        // not attribute this queue's opener to the PREVIOUS list.
        contextAdoptionPendingAt = android.os.SystemClock.elapsedRealtime()
        // NOTE: the Enhanced Shuffle context is deliberately NOT adopted here — see the adoption site
        // further down, after the items actually land on the timeline. Flipping it at this point (before
        // an async fetch that can take seconds on mobile data) meant every song the OLD queue advanced to
        // while the NEW one loaded was recorded into the NEW queue's memory: songs marked as heard in a
        // playlist that never played them, and their real playlist never learning they were.
        scope.launch { runCatching { tasteProfile() } } // warm the taste cache for smart shuffle / autoplay
        // Shuffle reset on a NEW queue is intentional when PersistentShuffleAcrossQueues is off.
        // Two things made it read as "shuffle is broken":
        //
        //  1. A RESTORE came through here too. Restoring the persistent queue after the process died is
        //     the SAME queue coming back, not the user starting a new one — so shuffle was cleared on
        //     every single app start, which is what made "remember shuffle" a permanent no-op.
        //
        //  2. The clear below fires onShuffleModeEnabledChanged(false), which PERSISTS false to
        //     ShuffleModeKey. So the app's own reset destroyed the user's remembered preference; the
        //     boot-time read at :1211 could then only ever see false. suppressShuffleModePersist stops
        //     this programmatic reset from being mistaken for a user action. (The dead
        //     `previousShuffleEnabled` local that used to sit here was the fingerprint of the
        //     restore-afterwards line that was never written.)
        val persistShuffleAcrossQueues = dataStore.get(PersistentShuffleAcrossQueuesKey, false)
        if (!persistShuffleAcrossQueues && !isRestore && player.shuffleModeEnabled) {
            suppressShuffleModePersist = true
            try {
                player.shuffleModeEnabled = false
            } finally {
                suppressShuffleModePersist = false
            }
        }
        
        originalQueueSize = 0
        if (queue.preloadItem != null) {
            // Drop the quality-change survivor HERE, not in playQueue's prologue: the pin protects an IN-FLIGHT
            // stream, and until this line the old stream is still playing. playQueue is async and can abort
            // without ever touching the player (empty initial status, getInitialStatus throwing offline —
            // SilentHandler swallows it), which would have left the still-audible track with its lock deleted
            // and nothing able to restore it. Only the two sites that actually re-point the player may drop it.
            dropQualityPin()
            player.setMediaItem(queue.preloadItem!!.toMediaItem())
            player.prepare()
            player.playWhenReady = playWhenReady
        }
        scope.launch(SilentHandler) {
            val rawStatus =
                withContext(Dispatchers.IO) {
                    // Do NOT apply filterNonMusicForAutoQueue here: user-chosen queues (LocalAlbumRadio,
                    // ListQueue, playlists, liked) often have musicVideoType=null on plain audio rows from
                    // the DB — filtering them emptied albums so taps silently no-op'd (0.6.176 regression).
                    // Non-music filtering belongs only on automatic radio/related appends.
                    queue.getInitialStatus()
                        .filterExplicit(dataStore.get(HideExplicitKey, false))
                        .filterVideoSongs(dataStore.get(HideVideoSongsKey, false) || dataStore.get(iad1tya.echo.music.constants.DataSaverEnabledKey, false))
                }
            // Duplicate ROWS of one mediaId (a playlist can hold the same song twice) defeat the id-keyed
            // no-repeat memory: both rows play, and the second reads as a repeat. Context queues dedupe at
            // load (first occurrence wins; the tapped start item is preserved by remapping its index).
            // Classic queues keep duplicates — the user's literal list is not ours to edit.
            val initialStatus = if (enhancedShuffleHint &&
                queue.contextId != null &&
                rawStatus.items.size != rawStatus.items.distinctBy { it.mediaId }.size
            ) {
                val startItem = rawStatus.items.getOrNull(rawStatus.mediaItemIndex)
                val deduped = rawStatus.items.distinctBy { it.mediaId }
                val newIndex = startItem?.let { s -> deduped.indexOfFirst { it.mediaId == s.mediaId } }
                    ?.takeIf { it >= 0 } ?: 0
                rawStatus.copy(items = deduped, mediaItemIndex = newIndex)
            } else {
                rawStatus
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
                // The other site that genuinely ends the in-flight stream — see the note at the preloadItem
                // branch above.
                dropQualityPin()
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
            // Genre-aware continuation: a NEW context invalidates the old profile. Rebuilt lazily from the
            // fresh pool on the first re-seed (startRadioSeamlessly); steering stays off until then.
            contextProfile = null
            contextSteerActive = false
            // #34 — starting an explicit COLLECTION (playlist/album/list) supersedes any lingering Home-mood
            // bias: a stale mood chip must NOT hijack the infinite continuation of a playlist ("nada que ver").
            // A mood the user taps AFTER this (setActiveMood, no playQueue) survives, so the deliberate-mood
            // case still works.
            if (queue !is iad1tya.echo.music.playback.queues.YouTubeQueue) {
                activeMoodParams = null
                activeMoodTitle = null
            }


            // Enhanced Shuffle: adopt this queue's persistent context id (e.g. "PL:<id>", "AP:liked") only
            // NOW, once its items ARE the timeline, and only if this queue is still the live one. Adopting
            // it at the top of playQueue opened a window — the whole async fetch — in which the old queue
            // was still playing while the new queue's context was already installed, so every advance in
            // that window was filed under the wrong playlist. Null for any non-ListQueue or a ListQueue
            // without a contextId → no persistent memory, classic shuffle. On a RESTORE the restored
            // ListQueue carries the contextId saved in PersistQueue, so memory resumes.
            if (currentQueue === queue) {
                shuffleContextId = queue.contextId
                pendingSeedPlayedIds = if (enhancedShuffleHint) queue.seedPlayedIds else emptySet()
                contextAdoptionPendingAt = 0L // adopted: the transition path may record again
                // COVERAGE of this context — the SIZE OF THE LIST, taken from the very items that just became
                // the timeline, and stamped with the context it describes. Deliberately NOT radioSeedPool.size:
                // that pool drops items whose metadata is null and is empty for a YouTubeQueue, so it under-
                // reports the list. An external adopt can no longer inherit this number either — the id is
                // part of it. See [contextCoverageId].
                contextCoverageId = queue.contextId
                contextCoverageSize = if (queue.contextId != null) initialStatus.items.size else 0
                externalCoverageArmedAt = 0L // an in-app queue landed: nothing external left to measure

                // THE OPENER — the song the user actually tapped to start this list.
                //
                // media3 fires its PLAYLIST_CHANGED transition SYNCHRONOUSLY inside setMediaItems above,
                // which happens ~57 lines BEFORE this adoption. At that moment shuffleContextId still
                // named the PREVIOUS list (or was null on a cold start), so the opener is the ONE song of
                // the whole session that no recording site files under the right list: every later song
                // records correctly, only the first is lost. And since the opener is the song he CHOSE,
                // the songs that come back are precisely his favourites.
                //
                // Recorded here, from queue.contextId directly (not the field), inside the liveness guard
                // so a superseded queue can never write into the live one's bucket.
                val openerId = player.currentMediaItem?.mediaId ?: player.currentMetadata?.id
                val openerCtx = queue.contextId
                // playWhenReady is the PARAMETER, not player.playWhenReady: a queue that was prepared but
                // never actually played must not be marked as heard. isRestore is excluded for the same
                // reason — the restore path deliberately leaves playback paused awaiting the first real
                // user play, and the seek below records the resumed song once it moves.
                if (enhancedShuffleHint && openerCtx != null && openerId != null && playWhenReady && !isRestore) {
                    val now = System.currentTimeMillis()
                    scope.launch(enhancedShuffleWriteDispatcher) {
                        runCatching {
                            database.insertEnhancedPlayed(EnhancedShufflePlayedEntity(openerCtx, openerId, now))
                        }
                    }
                }
            }

            if (queue.startShuffled &&
                !player.shuffleModeEnabled &&
                // Only if this queue is still the LIVE one: with two rapid playQueue calls the first
                // (slower) fetch could otherwise enable shuffle for a queue the user already replaced —
                // and the enable-path DB persist would then write song #1 into the WRONG context.
                currentQueue === queue
            ) {
                // Enhanced Shuffle FIX (replay bug, part 2): the screens' Shuffle BUTTONS used to only
                // pre-shuffle the item list with shuffle MODE off — which bypassed the entire enhanced
                // system: no memory-aware order, no B5, no played-recording → the button replayed played
                // songs in every configuration. Turning the mode on HERE — after the items landed — fires
                // onShuffleModeEnabledChanged with a populated queue, which resets B5, applies the
                // memory-aware order and seeds the persistent memory. The items are already pre-shuffled,
                // so the starting song stays random.
                player.shuffleModeEnabled = true
            } else if (player.shuffleModeEnabled) {
                val shufflePlaylistFirst = dataStore.get(ShufflePlaylistFirstKey, false)
                // B5: fresh anti-repeat session for the NEW queue — without this the set still held the
                // PREVIOUS queue's ids, falsely sinking any songs the two queues share. Gated on the
                // enhanced setting so classic-shuffle (setting OFF) behavior stays byte-identical.
                if (enhancedShuffleHint) {
                    shufflePlayedIds.clear()
                    // Same reset for the artist history: it described the PREVIOUS queue.
                    recentShuffleArtists.clear()
                    rememberShuffleArtist(player.currentMediaItem)
                    player.currentMetadata?.id?.let { cur ->
                        shufflePlayedIds.add(cur)
                        // Persist the opener too: a process death before its natural transition-insert
                        // resurrected the session's first song as unplayed (one guaranteed repeat).
                        val curCtx = shuffleContextId
                        if (curCtx != null) {
                            val now = System.currentTimeMillis()
                            scope.launch(enhancedShuffleWriteDispatcher) {
                                runCatching { database.insertEnhancedPlayed(EnhancedShufflePlayedEntity(curCtx, cur, now)) }
                            }
                        }
                    }
                }
                applyPendingSeedPlayedIds()
                applyShuffleOrder(player.currentMediaItemIndex, player.mediaItemCount, shufflePlaylistFirst)
                // Enhanced Shuffle FIX (replay bug, part 1): this queue started while shuffle was ALREADY
                // ON, so onShuffleModeEnabledChanged — the only place the persistent memory was loaded —
                // never fires (true→true is swallowed by media3). The playlist screen (reading the DB) then
                // showed songs correctly marked as played while the shuffle ORDER (reading the in-memory
                // set) treated them as unplayed and REPLAYED them. Seed from the DB here too. This branch
                // also covers the boot RESTORE (onCreate set shuffle on an empty player, consuming the only
                // callback; the later per-queue snapshot write is true→true and never re-fires it).
                val seedCtx = shuffleContextId
                if (enhancedShuffleHint && seedCtx != null) {
                    // Starting a list DELIBERATELY while shuffle is on is the user activating shuffle for
                    // that list — so if the list is already finished, this is condition (b) of the owner's
                    // rule and the seed resets the lap instead of loading a fully-played memory. A RESTORE
                    // is not: the process died and came back on its own, and the finished list must stay
                    // finished (it hands off to the infinite radio, exactly as it did before dying).
                    seedEnhancedShuffleFromDb(seedCtx, shufflePlaylistFirst, isUserActivation = !isRestore)
                }
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
        // Offline mode: never seed radio / related / YouTube next — that is network by definition.
        if (dataStore.get(OfflineModeKey, false)) {
            resumeAfterSeed = false
            advanceIntoRadioRequested = false
            return
        }

        if (!playerInitialized.value) {
            Timber.tag(TAG).w("startRadioSeamlessly called before player initialization")
            resumeAfterSeed = false // never reach the finally on this early return; don't leave it armed
            advanceIntoRadioRequested = false
            return
        }

        // A B3 head-start (or a prior call) is already fetching — do NOT launch a second seed (registry #60).
        // Callers that need to jump into the result must [requestAdvanceIntoRadio] first.
        if (radioSeedInFlight) {
            return
        }

        val currentMediaMetadata = player.currentMetadata ?: run {
            resumeAfterSeed = false
            advanceIntoRadioRequested = false
            return
        }
        val currentMediaId = currentMediaMetadata.id

        // Claimed SYNCHRONOUSLY — that is the whole point of the guard. `scope` is the NON-immediate
        // Dispatchers.Main, so `launch` always posts and the body runs in a LATER main-thread message. Setting
        // the flag inside the coroutine therefore leaves the guard unclaimed for the rest of the CURRENT
        // dispatch, and media3's ListenerSet delivers every callback of one player update in a single
        // synchronous flush: onMediaItemTransition (B3 pre-seed) and onEvents (scheduleCrossfade -> seed) both
        // see `false` and BOTH fire. The second seed's appendSeed then removes the tail the first just appended,
        // while sessionPlayedIds has already burned those ids via the #22 no-repeat filter — the batch is lost
        // for good, and the crossfade gets armed twice against an index the second seed deletes.
        //
        // The cancelled-scope leak this used to guard against is handled by invokeOnCompletion below, which
        // runs even when the coroutine body never starts.
        radioSeedInFlight = true

        val seedJob = scope.launch(SilentHandler) {
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

            // Genre-aware continuation: build the CONTEXT PROFILE of the finished collection ONCE (first
            // re-seed of this context), from the WHOLE radioSeedPool — playlist/album/EP/single uniformly
            // (a 1-track single profiles too; only a pure radio, pool empty, has nothing to profile).
            // Off the player thread (IO), runCatching → a failed build leaves the profile null and every
            // consumer behaves exactly as today. Also fire-and-forget enrich of the CONTEXT's own artists
            // (WiFi-only, GenreCache bounds to 40 + semaphore 4 — mirrors the Path A learn site) so the
            // profile's coverage of THIS context warms up within the session. No cache-bias violation:
            // we enrich exactly the population we score (registry #39).
            // Rebuild on EVERY re-seed, not once per context: the profile froze the genre shares at build
            // time, and the enrich below keeps teaching GenreCache new artists — so a profile built from a
            // cold cache (fresh install, WiFi-only gate, first listen to a genre) stayed blind for the
            // whole context life while the cluster step right next to it already saw the fresh snapshot,
            // and the off-context sink judged with stale shares. The build is pure and bounded, runs on
            // IO, fires only at re-seed time (not per boundary), and the `radioSeedPool !== pool` identity
            // guard below discards a build that a context switch overtook. Monotonic cache growth means an
            // active profile can only get MORE informed — fail-neutral in every direction.
            if (radioSeedPool.isNotEmpty()) {
                runCatching {
                    val pool = radioSeedPool
                    val built = withContext(Dispatchers.IO) {
                        val genres = iad1tya.echo.music.reco.GenreCache.snapshot(this@MusicService)
                        iad1tya.echo.music.reco.ContextProfile.build(
                            pool.map { mm ->
                                iad1tya.echo.music.reco.ContextProfile.Track(
                                    artists = mm.artists.map { it.name },
                                    title = mm.title,
                                    album = mm.album?.title,
                                )
                            },
                            genres,
                        )
                    }
                    // IDENTITY guard: playQueue may have swapped the context while we were parked on IO
                    // (it reassigns radioSeedPool and nulls the profile). Assigning then would pin the OLD
                    // playlist's profile onto the NEW context for its whole life — steer toward the wrong
                    // genres. The pool reference changes ONLY at that reassignment site, so === is exact.
                    if (radioSeedPool !== pool) return@runCatching
                    contextProfile = built
                    scope.launch(Dispatchers.IO + SilentHandler) {
                        val names = pool.flatMap { it.artists }.map { it.name }.filter { it.isNotBlank() }.distinct()
                        if (names.isNotEmpty()) {
                            runCatching {
                                iad1tya.echo.music.reco.GenreCache.enrich(this@MusicService, names, onlyWifi = true)
                            }
                        }
                    }
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
                // ENRICH BEFORE SCORING (see [ENRICH_BEFORE_SCORE_MS]) — the ROOT CAUSE of the genre
                // mixing, as opposed to the two mitigations further down (the CTX_SINK partition and the
                // unknown-genre nudge). Both that partition and orderedByTaste's own steer read a
                // GenreCache SNAPSHOT; taking it before this batch's own artists had ever been looked up
                // meant scoring a batch we knew nothing about. Launched on `scope` so the timeout cancels
                // only the WAIT — the run itself survives and still fills the cache for the next batch,
                // which is exactly the fire-and-forget behaviour it replaces. Skipped when the steer is
                // off (the snapshot is unused) and when the player is parked at a true end of queue
                // waiting for these very items (resumeAfterSeed), where a wait would be silence.
                //
                // FIRST, before `liveIndex` below: this suspends for up to a second and a half, and that
                // index must be read from the LIVE player as late as possible — capturing it and then
                // waiting is precisely the staleness its own comment exists to prevent.
                val steerNeedsGenres = contextSteerActive && keepGenreLaneHint &&
                    contextProfile?.active == true
                if (steerNeedsGenres && !resumeAfterSeed) {
                    val candidateArtists = items
                        .flatMap { it.metadata?.artists.orEmpty() }
                        .map { it.name }
                        .filter { it.isNotBlank() }
                        .distinct()
                        .take(GENRE_LEARN_PER_RUN)
                    if (candidateArtists.isNotEmpty()) {
                        val enrichJob = scope.launch(Dispatchers.IO + SilentHandler) {
                            runCatching {
                                iad1tya.echo.music.reco.GenreCache.enrich(
                                    this@MusicService, candidateArtists, onlyWifi = true,
                                )
                            }
                        }
                        val waited = withTimeoutOrNull(ENRICH_BEFORE_SCORE_MS) { enrichJob.join() } != null
                        Timber.tag(TAG).i(
                            "CTX_GENRE enrich-before-score: %d artists, completed=%b",
                            candidateArtists.size, waited,
                        )
                    }
                }
                // Recompute the index from the LIVE player at append time (not a stale value captured before the
                // network fetch), so we never remove items relative to a position that has since moved.
                val liveIndex = player.currentMediaItemIndex
                val itemCount = player.mediaItemCount
                // Order FIRST, then decide. orderedByTaste() can return an EMPTY list (e.g. every candidate is a
                // hard-disliked artist), and the old order — remove-then-append — truncated the queue to the
                // current track and appended nothing, leaving the resume seekTo() pointing past the end. Never
                // destroy the tail before we know we have something to put in its place.
                // OFF-CONTEXT DROP (the reason "smart queue" predictions felt unrelated): the steering
                // term is a bounded NUDGE — clamped to [-4,+6] against an index-dominated key, it can
                // displace a candidate ~10 ranks but can never REMOVE it, so when YouTube returns a bad
                // batch for a niche context (salsa, worship), the wrong songs still played, just slightly
                // later. This drops candidates whose genre is KNOWN and has ZERO share in the context —
                // and only those. Hard constraints, in order of the registry rules they serve:
                //  • unknown-genre candidates stay ELIGIBLE untouched (#39/#41: a cache-derived signal
                //    must never drop unknowns, or the infinite queue collapses onto the library);
                //  • candidates from EVERY context lane survive (any share > 0), so a mixed playlist
                //    never collapses onto its dominant genre;
                //  • the drop applies only when >= 10 candidates survive (stricter than the pagination
                //    path's >= 2 because this feeds the big primary injection) — otherwise the batch is
                //    kept unfiltered, and appendSeed's callers already fall through to the next source,
                //    so never-silence holds;
                //  • gated on the same user toggle (default ON) that gates the shipped pagination drop,
                //    making the defense symmetric instead of new; ONE GenreCache snapshot per batch.
                val profile = contextProfile
                val laneOrdered = if (
                    contextSteerActive && keepGenreLaneHint &&
                    profile != null && profile.active && profile.genreShare.isNotEmpty()
                ) {
                    val genres = withContext(Dispatchers.IO) {
                        runCatching { iad1tya.echo.music.reco.GenreCache.snapshot(this@MusicService) }
                            .getOrDefault(emptyMap())
                    }
                    // SINK, never drop. iTunes labels vary WITHIN a genre ("Salsa y Tropical" vs "Pop" on
                    // Marc Anthony himself), so an exact-lane DROP removed legitimate adjacent artists —
                    // worse than the weak steering it replaced. This is an UNCONDITIONAL stable partition
                    // (no survivor threshold, no removal): known-off-context candidates go to the batch
                    // tail AND their ids are handed to orderedByTaste, which is what actually keeps them
                    // there. Tail position by itself is not enough — the pull cap lifts up to 8 ranks and
                    // the exploration quota promotes fresh artists to the front. Never-silence holds
                    // trivially: nothing is removed, so the batch can never shrink to empty here.
                    val (inContext, offContext) = items.partition { mi ->
                        val m = mi.metadata
                        // Context ARTISTS are never off-context, whatever label iTunes gave them — the
                        // profile's own steerTerm has the same precedence, and dropping/sinking an artist
                        // who is IN the playlist would be self-evidently wrong (finding: frozen shares vs
                        // fresh snapshot resolved playlist artists into lanes the profile never counted).
                        val ctxArtist = m?.artists?.any { a -> a.name.trim().lowercase() in profile.artistSet } == true
                        if (ctxArtist) return@partition true
                        val lane = iad1tya.echo.music.reco.GenreLane.laneOfTrack(
                            genres,
                            m?.artists?.firstOrNull()?.name.orEmpty(),
                            m?.title.orEmpty(),
                            m?.album?.title,
                        )
                        when {
                            // Unknown lane: untouched (#39/#41 — cache-derived signal never judges unknowns).
                            lane == null -> true
                            (profile.genreShare[lane] ?: 0.0) > 0.0 -> true
                            // CHRISTIAN is the strict keyword lane, and an artist NAME alone can fabricate
                            // it ("Cristian Castro"). Sink only when the track's OWN text earns the lane;
                            // any other origin keeps steerTerm's +6 push (shipped behaviour), never a sink.
                            lane == iad1tya.echo.music.reco.GenreLane.CHRISTIAN ->
                                !iad1tya.echo.music.reco.GenreLane.isKeywordChristian(
                                    m?.title.orEmpty(), null, m?.album?.title,
                                )
                            else -> false
                        }
                    }
                    if (offContext.isNotEmpty()) {
                        Timber.tag(TAG).i(
                            "CTX_SINK appendSeed: sank %d/%d off-context candidates to the tail",
                            offContext.size, items.size,
                        )
                    }
                    // The ids travel WITH the order: tail position alone was not enough, because the
                    // exploration quota downstream promotes a fresh artist to the front and an
                    // off-context candidate is fresh almost by definition — the sink was being undone.
                    (inContext + offContext) to offContext.mapNotNullTo(HashSet()) { it.mediaId }
                } else items to emptySet<String>()
                val toAppend = laneOrdered.first.orderedByTaste(laneOrdered.second)
                if (toAppend.isEmpty()) return false
                // Truncate the tail ONLY when playing in order. `liveIndex` is a TIMELINE index, but under
                // shuffle playback follows the shuffle order, so "everything after liveIndex" is an arbitrary
                // slice — not the played tail. Starting a radio from the middle of a shuffled 50-track queue
                // would delete ~46 songs the user had not heard yet. In shuffle we only append; the shuffle
                // order is rebuilt below and the no-repeat filter already stops played tracks coming back.
                if (!player.shuffleModeEnabled && itemCount > liveIndex + 1) {
                    player.removeMediaItems(liveIndex + 1, itemCount)
                }
                player.addMediaItems(liveIndex + 1, toAppend)
                sessionPlayedIds.addAll(toAppend.mapNotNull { it.mediaId }) // NO-REPEAT: record what we appended
                _mixActive.value = true
                if (player.shuffleModeEnabled) {
                    val shufflePlaylistFirst = dataStore.get(ShufflePlaylistFirstKey, false)
                    applyShuffleOrder(player.currentMediaItemIndex, player.mediaItemCount, shufflePlaylistFirst)
                }
                // STATE_ENDED arms resumeAfterSeed and waits for !isPlaying. Manual Next also arms
                // advanceIntoRadioRequested so we SEEK into the radio while the last finite track is
                // still audibly playing — otherwise Next appears to do nothing until the song ends.
                if ((resumeAfterSeed && !player.isPlaying) || advanceIntoRadioRequested) {
                    resumeAfterSeed = false
                    advanceIntoRadioRequested = false
                    player.seekTo(liveIndex + 1, 0)
                    player.playWhenReady = true
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
                // Genre-aware continuation: an automatic last-song seed still continues the finished context,
                // so steer its batches toward the context profile (no-op while the profile is null/inactive).
                contextSteerActive = true
                val radioQueue = YouTubeQueue(endpoint = WatchEndpoint(videoId = seed), automaticRadio = true)
                val initialStatus = withContext(Dispatchers.IO) {
                    radioQueue.getInitialStatus()
                        .filterExplicit(dataStore.get(HideExplicitKey, false))
                        .filterVideoSongs(dataStore.get(HideVideoSongsKey, false) || dataStore.get(iad1tya.echo.music.constants.DataSaverEnabledKey, false))
                        .filterNonMusicForAutoQueue()
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
                contextSteerActive = true // same automatic-continuation reasoning as tryRadio
                val nextResult = withContext(Dispatchers.IO) {
                    YouTube.next(WatchEndpoint(videoId = seed)).getOrNull()
                }
                val relatedEndpoint = nextResult?.relatedEndpoint ?: return@runCatching false
                val relatedPage = withContext(Dispatchers.IO) { YouTube.related(relatedEndpoint).getOrNull() }
                val items = relatedPage?.songs.orEmpty()
                    .filter { it.id != seed && it.id != currentMediaId }
                    .map { it.toMediaItem() }
                    .filterExplicit(dataStore.get(HideExplicitKey, false))
                    .filterVideoSongs(dataStore.get(HideVideoSongsKey, false) || dataStore.get(iad1tya.echo.music.constants.DataSaverEnabledKey, false))
                    .filterNonMusicForAutoQueue()
                val ok = appendSeed(items)
                // CRITICAL for endlessness: the related page is FINITE. Re-point currentQueue at a radio seeded
                // from the genuine last song AND PRIME it (getInitialStatus sets `continuation`, so hasNextPage()
                // is true and the onMediaItemTransition pagination keeps loading forever). hasNextPage() is false
                // on a fresh un-loaded YouTubeQueue, so without priming pagination wouldn't fire. Best-effort: if
                // priming fails, the always-on STATE_ENDED net still re-seeds when this finite batch ends.
                if (ok) {
                    val rq = YouTubeQueue(endpoint = WatchEndpoint(videoId = seed), automaticRadio = true)
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
                // #34 — an EXPLICIT mood steer always beats the finished context: stop context steering
                // before this batch is ordered, so the mood's own character is preserved.
                contextSteerActive = false
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
                    .filterVideoSongs(dataStore.get(HideVideoSongsKey, false) || dataStore.get(iad1tya.echo.music.constants.DataSaverEnabledKey, false))
                    .filterNonMusicForAutoQueue()
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
                // Genre-aware continuation: this IS the context's own continuation — steer its batches
                // toward the context profile (no-op while the profile is null/inactive).
                contextSteerActive = true
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
                // RE-SEED ANCHOR (kills compounding drift): only tail items that BELONG to the original
                // context count as "tail" — on a re-seed the live tail is the previously appended RADIO
                // songs, and seeding from them made each re-seed drift off the drift of the last one.
                // Intersecting keeps #34's intent exactly (the tail OF THE CONTEXT as it played); when the
                // whole live tail is already radio, fall back to the context pool itself (recent-last order).
                // Gated on the ACTIVE context profile like every other genre-aware step, so a null/inactive
                // profile leaves the seed selection byte-identical to today (fail-neutral rule).
                val steerActive = contextProfile?.active == true
                val contextIds = radioSeedPool.mapTo(HashSet()) { it.id }
                val anchoredTail = if (steerActive) tailPool.filter { it.id in contextIds } else tailPool
                // Recent-first so the DISTINCT-artist reps come from the END of what was playing, not the start.
                val contextPool = anchoredTail.ifEmpty { radioSeedPool }.asReversed()
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
                // GENRE-CLUSTER REPRESENTATIVES: cluster the WHOLE context by KNOWN genre (GenreCache lane;
                // unknown-genre tracks form no cluster but stay eligible via the artist/track paths below) and
                // pick one representative per cluster — ACROSS clusters by context share (largest first),
                // WITHIN a cluster by global taste. So a mixed playlist seeds its real genre mix instead of
                // whatever 4 artists the tail happened to hold, and a pure salsa playlist still seeds all-salsa.
                // Gated on the ACTIVE context profile (fail-neutral: inactive → empty → seeds exactly as today).
                val clusterReps: List<String> =
                    if (steerActive) runCatching {
                        val genres = withContext(Dispatchers.IO) {
                            iad1tya.echo.music.reco.GenreCache.snapshot(this@MusicService)
                        }
                        val clusters = LinkedHashMap<String, MutableList<iad1tya.echo.music.models.MediaMetadata>>()
                        radioSeedPool.forEach { mm ->
                            if (mm.ytId() == null) return@forEach
                            val lane = iad1tya.echo.music.reco.GenreLane.laneOfTrack(
                                genres, mm.artists.firstOrNull()?.name, mm.title, mm.album?.title,
                            ) ?: return@forEach
                            clusters.getOrPut(lane) { mutableListOf() }.add(mm)
                        }
                        clusters.values
                            .sortedByDescending { it.size }
                            .mapNotNull { tracks ->
                                tracks.maxByOrNull { mm ->
                                    if (profile == null) 0.0 else profile.scoreNames(mm.artists.map { it.name }, mm.title)
                                }?.ytId()
                            }
                    }.getOrDefault(emptyList()) else emptyList()
                // Seeds (up to 4 distinct ids) that capture the RANGE: the current/last song first ("more like
                // what just played"), then one representative per context GENRE CLUSTER (largest share first),
                // then one per DISTINCT ARTIST (recent-first, taste-ranked), then more distinct recent TRACKS
                // (so a SINGLE-ARTIST ALBUM still multi-seeds across its range).
                val perArtistIds = ranked.mapNotNull { it.ytId() }
                val poolIds = contextPool.mapNotNull { it.ytId() }
                val seeds = (listOfNotNull(seedVideoId) + clusterReps + perArtistIds + poolIds).distinct().take(4)
                if (seeds.size < 2) return@runCatching false // truly one usable track → let tryRadio do last-song
                // Fetch each seed's radio page, off the player thread. With an ACTIVE profile the per-seed
                // cap grows 12 → 16 (headroom so the context steering in orderedByTaste has material to
                // demote into; still <= 4 fetches); inactive keeps today's 12 exactly (fail-neutral rule).
                val perSeedCap = if (steerActive) 16 else 12
                val perSeed = withContext(Dispatchers.IO) {
                    seeds.map { sv ->
                        runCatching {
                            YouTubeQueue(endpoint = WatchEndpoint(videoId = sv)).getInitialStatus()
                                .items.filter { it.mediaId != sv && it.mediaId != currentMediaId }.take(perSeedCap)
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
                    .filterVideoSongs(dataStore.get(HideVideoSongsKey, false) || dataStore.get(iad1tya.echo.music.constants.DataSaverEnabledKey, false))
                    .filterNonMusicForAutoQueue()
                val ok = appendSeed(items) // appendSeed already runs orderedByTaste + records no-repeat + crossfade
                if (ok) {
                    // Prime a radio from a seed so the Path A pagination keeps going after this merged batch.
                    // Prefer the TOP GENRE-CLUSTER representative (the context's dominant genre) so the
                    // crossfade-OFF pagination continues on that genre instead of a single-song radio of
                    // whatever happened to be first; without cluster info this is seeds.first() as before.
                    val rq = YouTubeQueue(endpoint = WatchEndpoint(videoId = clusterReps.firstOrNull() ?: seeds.first()), automaticRadio = true)
                    runCatching { withContext(Dispatchers.IO) { rq.getInitialStatus() } }
                    currentQueue = rq
                }
                ok
            }.getOrDefault(false)

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
            if (!appended && (resumeAfterSeed || advanceIntoRadioRequested) &&
                !player.isPlaying && player.mediaItemCount > 0
            ) {
                Timber.tag(TAG).w("Radio seed yielded nothing; replaying current queue so playback never stops")
                resumeAfterSeed = false
                advanceIntoRadioRequested = false
                player.seekTo(0, 0)
                player.play()
            }
        }
        // Release the claim from a completion handler rather than a `finally`. invokeOnCompletion fires even
        // when the coroutine body NEVER RAN (a launch on an already-cancelled scope completes immediately), so
        // the flag can no longer stick true and silently kill every re-seed path for the rest of the process.
        seedJob.invokeOnCompletion {
            radioSeedInFlight = false
            resumeAfterSeed = false
            advanceIntoRadioRequested = false
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
                val chipQueue = YouTubeQueue(endpoint = chip.endpoint, automaticRadio = true)
                val initialStatus = withContext(Dispatchers.IO) {
                    runCatching {
                        chipQueue.getInitialStatus()
                            .filterExplicit(dataStore.get(HideExplicitKey, false))
                            .filterVideoSongs(dataStore.get(HideVideoSongsKey, false) || dataStore.get(iad1tya.echo.music.constants.DataSaverEnabledKey, false))
                            .filterNonMusicForAutoQueue()
                    }.getOrNull()
                } ?: return@launch
                val items = initialStatus.items.filter { it.mediaId != currentMediaId }
                if (items.isEmpty()) return@launch
                // Genre-aware continuation: a chip is an EXPLICIT user steer — it wins over the finished
                // context, so stop context steering before this batch (and its pagination) is ordered.
                contextSteerActive = false
                // Same append semantics as appendSeed in startRadioSeamlessly: recompute the index from the
                // LIVE player at append time, replace only the tail AFTER the current item (the tail is
                // radio/autoplay content), and keep the current song playing untouched.
                val liveIndex = player.currentMediaItemIndex
                val itemCount = player.mediaItemCount
                // Order FIRST, then decide (same reason as appendSeed): orderedByTaste() can return EMPTY when
                // every candidate is hard-disliked, and truncating the tail before knowing that leaves the queue
                // with nothing after the current track.
                val toAppend = items.orderedByTaste()
                if (toAppend.isEmpty()) return@launch
                if (itemCount > liveIndex + 1) {
                    player.removeMediaItems(liveIndex + 1, itemCount)
                }
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
     * replaying songs heard days/weeks ago (which the last-~120 in-session [recentRadioIds] can't see). One DB
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
    /**
     * @param deprioritized ids that must end up BEHIND everything else (off-context candidates the caller
     * sank). Tail position alone is not enough: `pull` can lift an item up to 8 ranks and the exploration
     * quota promotes a FRESH artist straight to the front — and an off-context candidate is fresh almost
     * by definition, so the sink was being undone by the very pass that follows it. A +1000 offset is
     * strictly larger than every other term combined, so no pull, jitter or quota can reorder across it,
     * and within the group the normal ordering still applies.
     */
    private suspend fun List<MediaItem>.orderedByTaste(
        deprioritized: Set<String> = emptySet(),
    ): List<MediaItem> {
        if (size < 2) return this
        val disliked = runCatching { dislikeStore.snapshot() }
            .getOrDefault(iad1tya.echo.music.dislike.DislikeStore.Disliked())
        val filtered = this.filter { mi ->
            val m = mi.metadata ?: return@filter true
            m.id !in disliked.songs && m.artists.none { it.id != null && it.id in disliked.artists }
        }
        val p = tasteProfile() // may be null (no taste yet) → pure relatedness order, still recency/dislike-filtered
        // "Already heard" now has TWO memories: the last ~120 in-session transitions ([recentRadioIds]) AND the
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
        // Genre-aware continuation — ONE bounded additive nudge toward the FINISHED context's profile
        // ([iad1tya.echo.music.reco.ContextProfile.steerTerm], clamped [-4, +6]), same class as the #5
        // co-rel pull and the #7 soft push. Only while an AUTOMATIC continuation is running
        // (contextSteerActive — moods/chips clear it: an explicit steer wins) AND the profile passed its
        // minimum-signal gate. ONE GenreCache snapshot per batch, never per candidate (battery). An
        // UNKNOWN genre is a NUDGE of exactly one rank (ContextProfile.UNKNOWN_GENRE_PUSH), never a
        // filter. Null/inactive profile → ctx == 0.0 everywhere → key math identical to today.
        val ctxProfile = if (contextSteerActive) contextProfile?.takeIf { it.active } else null
        val ctxGenres: Map<String, String> = if (ctxProfile == null) emptyMap() else runCatching {
            withContext(Dispatchers.IO) { iad1tya.echo.music.reco.GenreCache.snapshot(this@MusicService) }
        }.getOrDefault(emptyMap())
        // EXPLORATION GUARD (the amplifier behind the owner's "una de un género, otra de otro"):
        // withExplorationQuota runs AFTER the sort and hands ~1 slot in 5 to an artist the taste profile
        // does not know — and a candidate we have no genre for is "fresh" almost by definition, so the
        // quota lifted exactly the candidates the steer had just pushed back, straight to the front. That
        // is the ~1-in-5 cadence he described. The codebase already knew this hazard for the CTX_SINK
        // group (the +1000 offset below exists because of it) and simply never closed it for the ordinary
        // steer. ONE rule covers both, and it is deliberately NARROW
        // ([iad1tya.echo.music.reco.ContextProfile.blocksExploration]): only a candidate whose genre we
        // actually KNOW and know to be off-context (ctx > 0 AND a non-null lane) may be kept out of a
        // reserved exploration slot. An UNKNOWN genre buys NOTHING here — registry #39/#41: GenreCache
        // fills only with artists already around the user, so an artist the radio surfaces for the FIRST
        // time is unknown BY CONSTRUCTION, and blocking on absence emptied the fresh partition on a cold
        // cache, no-opped the quota on every automatic continuation and left the batch head to the taste
        // pull — the radio narrowing onto the library, which is the very thing #39 forbids. A blocked
        // candidate keeps its sorted position and is never dropped; when EVERY fresh candidate is blocked
        // the quota simply no-ops and the batch comes out in its sorted order, so nothing can collapse.
        val explorationBlocked = HashSet<String>(deprioritized)
        var ctxKnownGenre = 0
        var ctxUnknownGenre = 0
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
            // Genre-aware continuation: the candidate's context-affinity nudge. Bounded [-4, +6] inside
            // steerTerm; runCatching so a surprise in the lane lookup can never kill the batch (the term
            // just goes neutral). 0.0 whenever the profile is off — the key below is then byte-identical.
            // The lane is hoisted OUT of the term so the exploration rule below can tell "wrong genre"
            // from "no genre at all"; it stays null when the lookup throws, so a failure can only make
            // the rule MORE permissive, never block a candidate we know nothing about.
            var ctxLane: String? = null
            val ctx = if (ctxProfile == null || m == null) 0.0 else runCatching {
                val lane = iad1tya.echo.music.reco.GenreLane.laneOfTrack(
                    ctxGenres, m.artists.firstOrNull()?.name, m.title, m.album?.title,
                )
                ctxLane = lane
                if (lane == null) ctxUnknownGenre++ else ctxKnownGenre++
                iad1tya.echo.music.reco.ContextProfile.steerTerm(ctxProfile, m.artists.map { it.name }, lane)
            }.getOrDefault(0.0)
            // See [explorationBlocked]: a candidate the steer pushed back for a KNOWN off-context genre
            // must not be pulled to the front by the quota that runs after the sort. An unknown genre is
            // NOT a reason (#39/#41). Membership only — position and eligibility unchanged.
            if (iad1tya.echo.music.reco.ContextProfile.blocksExploration(ctx, ctxLane)) {
                mi.mediaId?.let { explorationBlocked.add(it) }
            }
            // Lower key = earlier. `index` (relatedness rank) DOMINATES; taste/co-rel only NUDGE a song up a few
            // spots and jitter adds variety — relatedness stays the backbone.
            // Assertiveness (owner): taste weight raised so anchor artists/genres win more often against
            // raw YouTube relatedness, still capped so a favorite cannot scramble a short page.
            val jitter = if (p == null) 0.0 else rnd.nextDouble() * 1.0
            val pull = (taste * 5.5 + coRel * 2.5).coerceAtMost(10.0)
            // See [deprioritized]: dominates every other term so a sunk off-context candidate can never be
            // lifted back into the head by the pull cap or the exploration quota.
            val sunk = if (m != null && m.id in deprioritized) 1000.0 else 0.0
            val key = index.toDouble() - pull + soft + jitter + ctx + sunk
            // NO-REPEAT: "heard" is now SESSION-WIDE ([sessionPlayedIds] — everything played OR appended this
            // session), broadened beyond the last-~120 [recentSnapshot] and the ~5-min DB [playedHistory].
            val heard = m != null && (m.id in sessionPlayedIds || m.id in recentSnapshot || m.id in playedHistory)
            Triple(mi, key, heard)
        }
        // Phase B #4 — exploration quota: reserve ~1-in-5 slots for a FRESH artist (not yet in the taste profile)
        // so radio isn't pure exploit. Runs BEFORE spacing so the final spacedByArtist pass still guarantees no
        // same-artist streaks. Phase A #3 — artist-diversity: applied LAST to the unheard pool only (not the
        // heardTail fallback below), so neither taste nor exploration can re-cluster an artist. Both passes are
        // in-memory, order- and length-preserving; null profile / no fresh → identical to today.
        // GENRE TRACE — one INFO line per radio append (AppLogger only PERSISTS >= INFO, so DEBUG would
        // exist only in a debug build, i.e. exactly where the bug does not happen). "how many candidates
        // did we actually know the genre of" is the number that decides whether the steer can work at
        // all, so a report of "me mezcla géneros" can be settled from a shared log. Same style as
        // CTX_SINK / SHUFFLE_SPACING.
        if (ctxProfile != null) {
            Timber.tag(TAG).i(
                "CTX_GENRE append: candidates=%d known=%d unknown=%d blockedFromExploration=%d " +
                    "profile(coverage=%.2f knownArtists=%d lanes=%d cache=%d)",
                filtered.size, ctxKnownGenre, ctxUnknownGenre, explorationBlocked.size,
                ctxProfile.coverage, ctxProfile.knownArtists, ctxProfile.genreShare.size, ctxGenres.size,
            )
        }
        val unheard = keyed.filterNot { it.third }.sortedBy { it.second }.map { it.first }
            .withExplorationQuota(p, explorationBlocked)
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
     * Phase B #4 — exploration quota. Reserve roughly every 15th slot for a "fresh" candidate: one whose primary
     * artist is NOT already in the taste profile ([iad1tya.echo.music.reco.TasteProfile.isKnownArtist]), so radio
     * doesn't tunnel into pure exploitation — but far less often than the old 1-in-5 / 1-in-10 cadences that made
     * context-faithful radio feel random. Never drops or duplicates anything —
     * output length == input length, and each partition keeps its incoming (taste/relatedness) order. Null profile
     * (no taste yet), lists under 8, or no fresh/known split → returns the list unchanged (today's behaviour).
     * In-memory only, no network, no extra cost.
     *
     * [blocked] are ids that may NOT claim a reserved exploration slot: a CTX_SINK id, or a candidate the
     * genre steer pushed back for a genre we KNOW and know to be off-context
     * ([iad1tya.echo.music.reco.ContextProfile.blocksExploration]). Without that rule the quota undid the
     * steer — the reserved slots went to precisely the songs it had just demoted (the owner's "una
     * del género, otra que no tiene nada que ver").
     *
     * An UNKNOWN genre is deliberately NOT a reason to block, and that boundary is load-bearing: "not in
     * your taste profile" and "we have no idea what genre this is" describe the SAME candidate almost
     * every time, so blocking on absence would empty this fresh partition on a cold or partial GenreCache
     * and turn the discovery reserve off exactly when discovery is happening (registry #39/#41).
     *
     * A blocked candidate is only moved OUT of the fresh partition: it keeps its sorted position, is
     * never dropped, and if every fresh candidate is blocked the interleave no-ops (order unchanged).
     * Empty [blocked] (the steer is off) → byte-identical to before.
     */
    private fun List<MediaItem>.withExplorationQuota(
        p: iad1tya.echo.music.reco.TasteProfile?,
        blocked: Set<String> = emptySet(),
    ): List<MediaItem> {
        if (p == null || size < 8) return this
        val known = ArrayList<MediaItem>(size)
        val fresh = ArrayList<MediaItem>()
        for (mi in this) {
            val artist = mi.metadata?.artists?.firstOrNull()?.name
            val isFresh = artist != null && !p.isKnownArtist(artist) &&
                (blocked.isEmpty() || mi.mediaId !in blocked)
            if (isFresh) fresh.add(mi) else known.add(mi)
        }
        // Nothing to interleave (all known or all fresh) → preserve the existing order exactly.
        if (fresh.isEmpty() || known.isEmpty()) return this
        val out = ArrayList<MediaItem>(size)
        val ki = known.iterator()
        val fi = fresh.iterator()
        var pos = 0
        while (ki.hasNext() || fi.hasNext()) {
            val takeFresh = pos % 15 == 14 && fi.hasNext()
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

        
        if (dataStore.get(PreventDuplicateTracksInQueueKey, true)) {
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

                applyingShuffleOrder = true
                try {
                    player.setShuffleOrder(DefaultShuffleOrder(finalOrder, System.currentTimeMillis()))
                } finally {
                    applyingShuffleOrder = false
                }
            }
        }
        
        preloadUpcomingItems()
    }

    fun addToQueue(items: List<MediaItem>) {
        
        if (dataStore.get(PreventDuplicateTracksInQueueKey, true)) {
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
            // Read on Main BEFORE database.query — that block runs on Room's queryExecutor
            // (pool-N-thread-M). Touching ExoPlayer there throws IllegalStateException.
            val currentlyPlayingId = player.currentMediaItem?.mediaId
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
                // Playing as a music video but DB row was audio-only → keep the video flag so later
                // taps still open video mode (and sync doesn't demote the track).
                if (base != null && meta.isVideoSong && !base.isVideo) {
                    base = base.copy(isVideo = true)
                    upsert(base)
                }
                val toggled = base?.toggleLike() ?: return@query
                val toggledFinal =
                    if (meta.isVideoSong && !toggled.isVideo) toggled.copy(isVideo = true) else toggled
                upsert(toggledFinal) // insert-or-update so the like always persists
                syncUtils.likeSong(toggledFinal)

                if (dataStore.get(AutoDownloadOnLikeKey, true) && toggledFinal.liked) {
                    // Guard the auto-download: DownloadService.sendAddDownload(foreground=false) throws
                    // IllegalStateException on Android 8+ when started from the background, and an uncaught throw
                    // here would abort the whole query block — rolling back the like. Never let the optional
                    // download break the like itself.
                    //
                    // While THIS track is in video mode, defer the WHOLE offline download
                    // (audio+video). Audio-only enqueue still races the live mux → hitch/403.
                    // Flushed in exitVideoMode.
                    val watchingThisInVideo =
                        _videoMode.value && currentlyPlayingId == toggledFinal.id
                    try {
                        enqueueSongDownloads(
                            this@MusicService,
                            toggledFinal.id,
                            toggledFinal.title,
                            isVideoSong = meta.isVideoSong,
                            deferWhileLiveVideo = watchingThisInVideo,
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

    /**
     * Lock the live Safe Volume gain for [mediaId] from a stream resolve (same player-response as
     * the URL — zero extra network). No-op if this isn't the audible track, or if this play is
     * already frozen (like + auto-download must not move the volume).
     *
     * Safe to call from the loader thread: [currentEqProcessor] / [lastNormalizedId] are volatile
     * and [CustomEqualizerAudioProcessor.applySafeVolume] is internally locked.
     */
    private fun lockLoudnessIfCurrent(
        mediaId: String,
        loudnessDb: Double?,
        perceptualLoudnessDb: Double?,
        measuredLoudnessDb: Double?,
    ) {
        val effective = effectiveLoudnessDb(loudnessDb, perceptualLoudnessDb, measuredLoudnessDb)
        loudnessHintCache[mediaId] = effective
        if (mediaId != currentPlayingMediaId) return
        if (isPlayingLoudnessFrozen(mediaId, lastNormalizedId)) return
        if (!normalizationEnabledHint && !safeVolumeEnabledHint) return
        applyAndFreezeLoudness(mediaId, effective)
    }

    private fun applyAndFreezeLoudness(mediaId: String, loudnessDb: Double) {
        val gain = normalizationMultiplier(loudnessDb, enabled = true)
        val makeup = dbToLinear(loudnessMakeupDb(loudnessDb, enabled = true))
        lastAppliedGain = gain
        lastAppliedMakeup = makeup
        lastNormalizedId = mediaId
        NormalizationGainAudioProcessor.gain = gain
        TruePeakLimiterAudioProcessor.loudnessMakeup = makeup
        if (safeVolumeEnabledHint) {
            currentEqProcessor?.applySafeVolume(true, safeVolumeAppliedGain(gain * makeup))
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
                val currentMediaId = withContext(Dispatchers.Main) {
                    player.currentMediaItem?.mediaId
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

                    val hasRealLoudness = format?.loudnessDb != null || format?.perceptualLoudnessDb != null
                    val hasKnownLoudness = hasRealLoudness || format?.measuredLoudnessDb != null
                    val loudnessDb = effectiveLoudnessDb(
                        format?.loudnessDb, format?.perceptualLoudnessDb, format?.measuredLoudnessDb,
                    )
                    if (hasKnownLoudness) loudnessHintCache[currentMediaId] = loudnessDb

                    if (isPlayingLoudnessFrozen(currentMediaId, lastNormalizedId)) {
                        withContext(Dispatchers.Main) {
                            NormalizationGainAudioProcessor.gain = lastAppliedGain
                            TruePeakLimiterAudioProcessor.loudnessMakeup = lastAppliedMakeup
                            loudnessEnhancer?.enabled = false
                            playerEqProcessors[player]?.applySafeVolume(
                                safeVol,
                                if (safeVol) safeVolumeAppliedGain(lastAppliedGain * lastAppliedMakeup) else 1f,
                            )
                        }
                        return@launch
                    }

                    // No format row yet: the stream resolver primes from the SAME player-response as
                    // the URL before open() returns. Applying DEFAULT here would freeze a provisional
                    // gain that a later download upgrades — the swell on Me gusta.
                    if (format == null) return@launch

                    val targetGain = normalizationMultiplier(loudnessDb, enabled = true)
                    val targetMakeup = dbToLinear(loudnessMakeupDb(loudnessDb, enabled = true))

                    withContext(Dispatchers.Main) {
                        if (isPlayingLoudnessFrozen(currentMediaId, lastNormalizedId)) {
                            NormalizationGainAudioProcessor.gain = lastAppliedGain
                            TruePeakLimiterAudioProcessor.loudnessMakeup = lastAppliedMakeup
                            loudnessEnhancer?.enabled = false
                            playerEqProcessors[player]?.applySafeVolume(
                                safeVol,
                                if (safeVol) safeVolumeAppliedGain(lastAppliedGain * lastAppliedMakeup) else 1f,
                            )
                            return@withContext
                        }
                        lastAppliedGain = targetGain
                        lastAppliedMakeup = targetMakeup
                        NormalizationGainAudioProcessor.gain = targetGain
                        TruePeakLimiterAudioProcessor.loudnessMakeup = targetMakeup
                        loudnessEnhancer?.enabled = false
                        playerEqProcessors[player]?.applySafeVolume(
                            safeVol,
                            if (safeVol) safeVolumeAppliedGain(targetGain * targetMakeup) else 1f,
                        )
                        lastNormalizedId = currentMediaId
                        playerNormProcessors[player]?.measureThisTrack = false
                        measuredAppliedForId = currentMediaId
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
                        // The OTHER player too, whichever it is: while a fade is actually running,
                        // performCrossfadeSwap has already set player = incoming and secondaryPlayer = null,
                        // and the OUTGOING track lives on fadingPlayer. Clearing only secondaryPlayer was a
                        // no-op exactly when it mattered — the two players would sit up to 3 dB apart for the
                        // rest of the blend. Before the swap it is secondaryPlayer that holds the primed gain,
                        // so cover both; each is null when it doesn't apply.
                        playerEqProcessors[player]?.applySafeVolume(false, 1f)
                        secondaryPlayer?.let { playerEqProcessors[it]?.applySafeVolume(false, 1f) }
                        fadingPlayer?.let { playerEqProcessors[it]?.applySafeVolume(false, 1f) }
                        Timber.tag(TAG).d("setupLoudnessEnhancer: normalization disabled - unity gain")
                    }
                    // Reset so RE-ENABLING normalization for the SAME track re-applies. The guard above keys on
                    // lastNormalizedId; without this reset, toggling normalization off→on mid-song was a no-op.
                    lastNormalizedId = null
                }
            } catch (e: Exception) {
                reportException(e)
                releaseLoudnessEnhancer()
            }
        }
    }

    /**
     * CACHE-ONLY ReplayGain-style measurement for a track that started WITHOUT loudness metadata.
     * When a measurement commits we persist it so the NEXT play is levelled from the first second.
     * We never change the live gain — a mid-song volume change is exactly what the owner hears
     * after liking a song (auto-download) and refuses.
     */
    private suspend fun maybeApplyMeasuredLoudness() {
        data class Snap(
            val mediaId: String?,
            val committed: Boolean,
            val measureId: String?,
            val measured: Double?,
        )
        val snap = withContext(Dispatchers.Main) {
            val norm = playerNormProcessors[player]
            Snap(
                mediaId = player.currentMediaItem?.mediaId,
                committed = norm?.measurementCommitted == true,
                measureId = norm?.measureTrackId,
                measured = norm?.measuredLoudnessDb,
            )
        }
        val mediaId = snap.mediaId ?: return
        if (!snap.committed) return
        if (snap.measureId != mediaId) return
        if (measuredAppliedForId == mediaId) return
        val measured = snap.measured ?: return

        measuredAppliedForId = mediaId
        withContext(Dispatchers.Main) {
            playerNormProcessors[player]?.measureThisTrack = false
        }

        runCatching {
            val existing = withContext(Dispatchers.IO) { database.format(mediaId).first() }
            if (existing != null) {
                database.query {
                    upsert(existing.copy(measuredLoudnessDb = measured))
                }
            } else {
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

    /**
     * Marks the persistent queue dirty (#55). media3 fires this for EVERY timeline mutation — add, remove and
     * `moveMediaItem` — which is what makes it a correct trigger where a count/index/id signature was not:
     * a drag-reorder of upcoming tracks changes none of those three.
     */
    /**
     * Set by [adoptExternalQueue] when an EXTERNAL controller (Android Auto, a watch, an assistant)
     * hands media3 a queue directly, and consumed in [onTimelineChanged] once those items are actually
     * on the timeline — which is the earliest moment a shuffle session can be started (it bails on an
     * empty queue).
     */
    @Volatile private var pendingExternalShuffle = false

    /** When [pendingExternalShuffle] was armed, so a request that never lands cannot fire much later. */
    @Volatile private var pendingExternalShuffleAt = 0L

    /** Landing signature of the external queue being adopted — see [adoptExternalQueue]. */
    @Volatile private var externalExpectedCount = 0
    @Volatile private var externalExpectedFirstId: String? = null

    /**
     * Adopts a queue that did NOT come through [playQueue].
     *
     * `onSetMediaItems` returns items straight to media3, so the service never learned about them:
     * [shuffleContextId] kept pointing at the last IN-APP queue, and every song played from the car was
     * recorded into that other playlist's memory — poisoning a list the user was not even listening to,
     * while the one actually playing learned nothing. Passing null is the correct, honest answer for a
     * source with no persistent bucket (search, an album, a radio): no memory is better than wrong
     * memory.
     */
    fun adoptExternalQueue(
        contextId: String?,
        shuffle: Boolean,
        expectedCount: Int = 0,
        expectedFirstId: String? = null,
    ) {
        shuffleContextId = contextId
        pendingExternalShuffle = shuffle
        pendingExternalShuffleAt = android.os.SystemClock.elapsedRealtime()
        // LANDING SIGNATURE: the arm below is consumed by onTimelineChanged, which fires for EVERY
        // timeline mutation in the window — a radio append, a restore, our own playQueue. Consuming it on
        // the WRONG landing measured a foreign timeline into the external context (poisoning its coverage
        // AND its memory via the shuffle-session start). The signature pins consumption to THIS queue's
        // actual arrival; a non-matching change leaves the arm armed so the real landing is still caught.
        externalExpectedCount = expectedCount
        externalExpectedFirstId = expectedFirstId
        // COVERAGE — the half of the job this function used to skip entirely. It adopted the context but
        // left the coverage of the PREVIOUS in-app queue in place (or, on a fresh process, none at all),
        // and coverage is what proves a list really finished. Consequences, both real: with no coverage the
        // "everything played" reading was taken straight off the LIVE TIMELINE, so a shrinking queue could
        // complete a cycle it had not played (registry row 94(e)); with a FOREIGN coverage (12-song car
        // queue vs an 80-song playlist measured earlier) the list could never finish, so the handoff to the
        // infinite radio never fired and the queue just re-shuffled what was already heard.
        // Zero = unknown until the items land; onTimelineChanged fills it in.
        contextCoverageId = contextId
        contextCoverageSize = 0
        // Armed even for a NULL context (search, a loose song): the arm is what rebuilds the radio seed
        // pool from the landed items, and a null-context queue needs that just as much — without it the
        // continuation after a car search fell back to last-song seeding while the pool sat empty.
        externalCoverageArmedAt = pendingExternalShuffleAt
        // RADIO SEEDS — the other half this function used to skip. radioSeedPool / contextProfile were
        // written ONLY by playQueue, which external queues never traverse, so when a car queue finished,
        // the smart continuation seeded and STEERED from the phone's LAST in-app collection — yesterday's
        // playlist deciding today's recommendations, which users read as "predictions unrelated to what I
        // am listening to". Cleared here (not rebuilt: the items have not landed yet); onTimelineChanged
        // refills the pool from the real timeline. Until then tryContextRadio bails on the empty pool and
        // the continuation degrades to last-song seeding — related to the CAR's song, which is honest.
        radioSeedPool = emptyList()
        contextProfile = null
        contextSteerActive = false
    }

    override fun onTimelineChanged(timeline: androidx.media3.common.Timeline, reason: Int) {
        // Self-inflicted shuffle-order re-applies change NOTHING the queue file stores (timeline order)
        // and must not schedule the next re-apply — see [applyingShuffleOrder].
        if (!applyingShuffleOrder) {
            queueDirty = true
            shuffleOrderStale = true
        }
        // COVERAGE for an EXTERNALLY adopted queue: this is the first instant its items ARE the timeline,
        // so it is the only place the size of the car's list can be learned (onSetMediaItems hands them
        // straight to media3, never touching playQueue). Armed by adoptExternalQueue and consumed here —
        // exactly once, and only while its arm is fresh — so an in-app queue landing later can never be
        // measured into the external context. Runs for a LINEAR external queue too: the user can toggle
        // shuffle from the car afterwards, and the coverage must already be there when he does.
        if (externalCoverageArmedAt != 0L && !applyingShuffleOrder) {
            if (android.os.SystemClock.elapsedRealtime() - externalCoverageArmedAt > EXTERNAL_SHUFFLE_ARM_TIMEOUT_MS) {
                externalCoverageArmedAt = 0L
            } else if (
                // contextCoverageId may legitimately be NULL (a car search, a loose song): the coverage
                // number is then meaningless, but the seed-pool rebuild below is not — that queue still
                // deserves a real continuation. Require only that the coverage id still MATCHES the live
                // context, which is trivially true for the null case and still rejects a stale one.
                player.mediaItemCount > 0 && contextCoverageId == shuffleContextId &&
                // LANDING SIGNATURE match: only THE adopted queue's arrival consumes the arm. Any other
                // timeline change in the window (a radio append, a restore, an in-app queue landing)
                // leaves it ARMED so the real landing is still measured — consuming on a foreign landing
                // used to measure that foreign timeline into the external context, wipe the live
                // session's memory via the session start below, and poison the external bucket.
                (externalExpectedCount == 0 ||
                    (player.mediaItemCount == externalExpectedCount &&
                        runCatching { player.getMediaItemAt(0).mediaId }.getOrNull() == externalExpectedFirstId))
            ) {
                contextCoverageSize = player.mediaItemCount
                externalCoverageArmedAt = 0L
                // Multi-seed parity with in-app queues: now that the car's items ARE the timeline, they
                // become the radio seed pool, so the continuation after this queue draws from the WHOLE
                // collection the user chose — not from the last song alone, and never from the previous
                // in-app queue (adoptExternalQueue cleared that). Bounded by the timeline size itself.
                radioSeedPool = (0 until player.mediaItemCount)
                    .mapNotNull { i -> runCatching { player.getMediaItemAt(i).metadata }.getOrNull() }
                // A plain tap (no shuffle action) with shuffle mode REMEMBERED ON: media3 never fires
                // onShuffleModeEnabledChanged (the value did not change), so without this the session ran
                // with the PREVIOUS queue's played set — ids overlapping across queues could read the car
                // queue as exhausted after a song or two. Mirrors what playQueue does for in-app queues.
                // isUserActivation = true: a deliberate list start is the owner's rule condition (b), so a
                // FINISHED list resets and replays instead of handing off after one song; an unfinished
                // list's memory is untouched (shouldResetForNewCycle guarantees it).
                if (!pendingExternalShuffle && player.shuffleModeEnabled && enhancedShuffleHint) {
                    // Posted, not called inline: this callback runs inside media3's ListenerSet flush, and
                    // applyShuffleOrder invoked from INSIDE a flush has its own onTimelineChanged DEFERRED
                    // past the applyingShuffleOrder window — the self-event would then re-arm stale/dirty
                    // as if a real mutation happened. One message later the flush is over and the
                    // suppression works as documented.
                    scope.launch { beginShuffleSession(isUserActivation = true) }
                }
            }
        }
        // An external controller's items have landed: NOW the shuffle session can start. Enabling the
        // mode fires onShuffleModeEnabledChanged; if it is already on, media3 stays silent and we must
        // start the session ourselves (the same media3 rule that left restored queues without memory).
        if (pendingExternalShuffle) {
            // Expire it rather than letting it sit armed: a shuffle tap on a collection that turns out to
            // be EMPTY never produces a timeline change of its own, and a stale flag would then force
            // shuffle onto whatever unrelated queue the user starts next, minutes later.
            if (android.os.SystemClock.elapsedRealtime() - pendingExternalShuffleAt > EXTERNAL_SHUFFLE_ARM_TIMEOUT_MS) {
                pendingExternalShuffle = false
            } else if (player.mediaItemCount > 0) {
                pendingExternalShuffle = false
                // beginShuffleSession posted out of the listener flush — same reasoning as the coverage
                // branch above. setShuffleModeEnabled stays inline: its own listener dispatch handles it.
                if (player.shuffleModeEnabled) {
                    scope.launch { beginShuffleSession() }
                } else {
                    player.shuffleModeEnabled = true
                }
            }
        }
        // Invalidates any preloaded crossfade secondary: its queue COPY becomes the live queue at the
        // swap, so it may only be reused while the timeline it copied is byte-identical. A counter — not
        // a content compare — because ANY mutation (append, remove, reorder, replaceMediaItem with the
        // same id) must invalidate, and comparing 5000 ids per reschedule would defeat the optimization.
        timelineVersion++
    }

    override fun onMediaItemTransition(
        mediaItem: MediaItem?,
        reason: Int,
    ) {
        val previousMediaId = currentPlayingMediaId
        currentPlayingMediaId = mediaItem?.mediaId
        val trackChanged = previousMediaId != mediaItem?.mediaId
        mediaItem?.mediaId?.let { id ->
            if (id != lastNormalizedId) lastNormalizedId = null
            if (normalizationEnabledHint || safeVolumeEnabledHint) {
                loudnessHintCache[id]?.let { applyAndFreezeLoudness(id, it) }
            }
        }
        // A track boundary is the cheap moment to engage audio offload: the renderer re-init the
        // preference change causes is inaudible here. See publishOffloadDecision.
        flushPendingOffloadEnable()
        // LYRICS PIN — manual-skip net. Observation only: nothing here reads or writes fade math, curve,
        // duration, swap order or volume; the fade keeps running exactly as it did. A skip during a fade
        // cancels NOTHING (the only crossfadeJob?.cancel() is in onDestroy), so the outgoing-song pin used to
        // outlive its own track and paint song A's lyrics over song C. The swap itself never reaches this
        // callback (it publishes the incoming item directly and attaches this listener to the incoming player
        // afterwards), so a transition arriving with a pin held means the user — or the queue — moved
        // somewhere the pinned song is not. Null items are left alone on purpose: media3 fires a spurious
        // null transition around a swap and the pinned song is still audible then.
        _crossfadeOutgoingMetadata.value?.let { pinnedOutgoing ->
            if (CrossfadeLyricsPin.shouldReleaseOnTransition(pinnedOutgoing.id, mediaItem?.mediaId)) {
                _crossfadeOutgoingMetadata.value = null
            }
        }
        rememberRecentRadioId(mediaItem?.mediaId ?: player.currentMetadata?.id)
        // Automatic YouTube radio only: skip non-music uploads (tutorials/how-tos) with null musicVideoType.
        // Never skip user-tapped songs / playlists (YouTubeQueue without automaticRadio) — those often lack
        // a type when hydrated from DB and must still play.
        run {
            val autoMeta = player.currentMetadata
            val autoRadio = (currentQueue as? YouTubeQueue)?.automaticRadio == true
            if (
                autoRadio &&
                autoMeta != null &&
                autoMeta.musicVideoType == null &&
                !autoMeta.id.isLocalMediaId() &&
                mediaItem != null
            ) {
                Timber.tag(TAG).i("NO_MUSIC skip automatic-radio id=${autoMeta.id.take(11)}")
                val next = player.nextMediaItemIndex
                if (next != C.INDEX_UNSET) {
                    player.seekTo(next, 0)
                    return
                }
            }
        }
        // A per-track Opus override (refetchCurrentInOpus) only applies to the track it was set for; drop it
        // once a genuinely different (non-null) track becomes current so a later track isn't forced to Opus.
        if (forceOpusForMediaId != null && mediaItem != null && mediaItem.mediaId != forceOpusForMediaId) {
            forceOpusForMediaId = null
        }
        // Same idea for the quality-change survivor: its only job was to protect the container of the track whose
        // stream was IN FLIGHT when the quality changed. Once a different track is current, drop it so a replay
        // resolves at the NEW quality. (The crossfade path skips this callback and collects the pin in
        // cleanupCrossfade instead; a fresh prepare of the SAME track drops it in dropQualityPin.)
        qualityPinnedMediaId?.let { pinned ->
            if (mediaItem != null && mediaItem.mediaId != pinned) {
                songUrlCache.remove(pinned)
                qualityPinnedMediaId = null
                persistSongUrlCache()
            }
        }
        // NOTE: SponsorBlock is fetched from applyAutoAdvanceSideEffects() below (shared with the crossfade
        // swap path) — calling it here too issued TWO fetches per track against a free community API.
        runCatching { flushAllPendingSongDownloads(this) }
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
        } else if (
            // Owner: tapping a VIDEO starts playback IN video mode (manual SEEK / new queue only —
            // AUTO advances stay audio unless sticky video was already on above).
            // GUARD: exitVideoMode() calls seekTo() internally which re-fires this callback with
            // MEDIA_ITEM_TRANSITION_REASON_SEEK. Without the flag check that internal seek would
            // immediately re-enter video mode, making the toggle button appear broken.
            // PLAYLIST_CHANGED = entirely new queue (user tapped a video from search / library) →
            // always honour: it is a fresh, deliberate user action so clear the flag first.
            // SEEK within the same queue = might be the internal seekTo from exitVideoMode → respect
            // userExplicitlyExitedVideo so the toggle is not overridden.
            mediaItem != null && trackChanged &&
            (reason == Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED ||
                (reason == Player.MEDIA_ITEM_TRANSITION_REASON_SEEK && !userExplicitlyExitedVideo)) &&
            (player.currentMetadata?.isVideoSong == true || exportedMuxedVideoUri(mediaItem.mediaId) != null) &&
            !(iad1tya.echo.music.utils.PerformanceMode.isOn(this) &&
                !iad1tya.echo.music.utils.DeviceForm.isTvOrCar(this))
        ) {
            userExplicitlyExitedVideo = false
            userHasUsedVideo = true
            videoSwapMeasureStart()
            if (!tryInstantVideoSwap()) {
                teardownInstantVideoSwap("auto video on manual video tap")
                // Resolve the video URL first; arm videoMode only when ready so audio keeps
                // playing through the resolve window (owner: play → stop ~4s → resume was the
                // old path that set videoMode=true then swapToVideo mid-stream).
                applyVideoToCurrent(armModeWhenReady = true)
            }
            val nextIdx = player.nextMediaItemIndex
            if (nextIdx != C.INDEX_UNSET) {
                runCatching { player.getMediaItemAt(nextIdx).mediaId }.getOrNull()
                    ?.let { prebuildNextVideoItem(nextIdx, it) }
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

        // AIMP-style SMOOTH ENTRY on manual changes (owner request: "cuando cambio una canción cae de
        // golpe"): a user-initiated switch — next/prev/tapping a song (SEEK) or starting another list
        // (PLAYLIST_CHANGED) — fades the NEW song in over ~400ms instead of slamming to full level.
        // AUTO advances are untouched (the crossfade owns those); a running crossfade is never fought.
        // NO playWhenReady guard here: on the tap-a-song path this callback can arrive before playQueue
        // sets playWhenReady, and the fade itself WAITS for real audio anyway (see fadeInOnManualChange).
        if (fadeOnManualChangeHint && !isCrossfading &&
            (reason == Player.MEDIA_ITEM_TRANSITION_REASON_SEEK ||
                reason == Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED)
        ) {
            fadeInOnManualChange()
        }

        // BEFORE the add below: the trace must report whether this song was ALREADY played when it
        // started, and recording it first would make every line read "repeat=YES".
        traceNoRepeat("transition")

        // A manual jump (queue-sheet tap, previous-press) is order-relevant even though it mutates no
        // timeline: landing on an already-played song puts the play head INSIDE the sunk played tail, and
        // with the stale-skip the next boundaries would walk that tail — up to the whole tail of repeats
        // where the old per-boundary re-apply allowed exactly one. Arm the re-apply so the next boundary
        // re-sinks the tail behind the unplayed remainder.
        if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_SEEK && player.shuffleModeEnabled) {
            shuffleOrderStale = true
        }

        // B5: remember what we've played this shuffle session (consumed by applyShuffleOrder to avoid repeats).
        if (player.shuffleModeEnabled) {
            val playedId = (mediaItem?.mediaId ?: player.currentMetadata?.id)
            playedId?.let { shufflePlayedIds.add(it) }
            // …and WHOSE song it was, so the next order rebuild can keep spacing that artist out.
            rememberShuffleArtist(mediaItem)
        }
        // Enhanced Shuffle: record the play into the PERSISTENT per-context memory — ONE single-row IGNORE
        // insert per transition, routed through the single-lane writer so it can never commit AFTER a
        // cycle-complete clear. Deliberately NOT gated on shuffleModeEnabled: the user's mental model is
        // "don't repeat what I already heard IN THIS LIST", and linear listening counts — half a playlist
        // heard in order used to come back as "unplayed" the moment shuffle was activated (top perceived-
        // repeat cause). Only the in-memory B5 set (the session-order tool) stays shuffle-gated above.
        run {
            val playedId = (mediaItem?.mediaId ?: player.currentMetadata?.id)
            val ctx = shuffleContextId
            // SURGICAL exclusion, not a blanket filter on PLAYLIST_CHANGED: that reason is also how a song
            // started from Android Auto arrives, and suppressing it wholesale would stop the car's songs
            // from ever being recorded. Only the timeline replacement that playQueue itself just caused —
            // while its context is still unadopted — is skipped, because that transition would file the NEW
            // list's opener under the PREVIOUS list. playQueue records that opener itself, correctly.
            val adoptionInFlight = contextAdoptionPendingAt != 0L &&
                android.os.SystemClock.elapsedRealtime() - contextAdoptionPendingAt < CONTEXT_ADOPTION_WINDOW_MS
            val isQueueReplacement = reason == Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED
            if (enhancedShuffleHint && ctx != null && playedId != null &&
                !(adoptionInFlight && isQueueReplacement)
            ) {
                val now = System.currentTimeMillis()
                scope.launch(enhancedShuffleWriteDispatcher) {
                    runCatching { database.insertEnhancedPlayed(EnhancedShufflePlayedEntity(ctx, playedId, now)) }
                }
            }
        }

        // Enhanced Shuffle — EARLY radio handoff. Re-seeding the persistent memory makes the shuffle order
        // [current, unplayed…, already-played tail…]. If media3 walked all the way to the true last item, that
        // already-played tail would REPLAY (breaking the no-repeat promise) before the last-item handoff below
        // ever fired. So the MOMENT the unplayed pool empties — every id in this context is now played, yet a
        // tail still sits ahead — drop the tail and continue into the infinite smart radio here, one lap early.
        // That is exactly the user's "when the list ends, continue with the infinite list, no longer random."
        // Guards mirror the last-item handoff; REPEAT_MODE_OFF only, so repeat-all/one are never interrupted.
        // AUTO-only: a manual skip/seek onto the last-unplayed song must NOT complete the cycle or hand off — only
        // natural end-of-track advance empties the pool "for real". (Non-AUTO reset would wipe persistent memory.)
        run {
            val exhaustCtx = shuffleContextId
            if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO &&
                autoLoadMoreHint && enhancedShuffleHint && player.shuffleModeEnabled && exhaustCtx != null &&
                player.playWhenReady && !radioSeedInFlight &&
                player.repeatMode == Player.REPEAT_MODE_OFF &&
                player.hasNextMediaItem() &&            // a tail still sits ahead...
                isEnhancedContextExhausted()            // ...and it is entirely already-played
            ) {
                markEnhancedContextCycleComplete(exhaustCtx)
                shuffleContextId = null                 // radio is no longer this context — stop recording into it
                startRadioSeamlessly()
            }
        }

        if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) {
            val repeatMode = player.repeatMode  // live value; avoids a blocking disk read on the player thread
            if (repeatMode == REPEAT_MODE_ONE &&
                previousMediaItemIndex != C.INDEX_UNSET &&
                previousMediaItemIndex != player.currentMediaItemIndex) {

                player.seekTo(previousMediaItemIndex, 0)
            }
        }
        // Shared with the crossfade swap path — see applyAutoAdvanceSideEffects.
        applyAutoAdvanceSideEffects()
        setupLoudnessEnhancer()

        discordUpdateJob?.cancel()


        // "Almost at the end" must be measured in PLAYBACK order. mediaItemCount - currentMediaItemIndex is
        // a TIMELINE distance: under shuffle the current index jumps around, so it read "5 left" on roughly
        // one transition in ten no matter how much was actually unplayed, paginating early and repeatedly
        // (network + battery). The three other end-of-queue triggers already use hasNextMediaItem(); this
        // one site was missed.
        // Walk the timeline in PLAYBACK order (getNextWindowIndex honours shuffleModeEnabled), counting at
        // most 6 hops — bounded work on the player thread, and correct with or without shuffle.
        maybeLoadMoreQueuePages(reason == Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT)
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
            // Enhanced Shuffle: reaching the last item to play while shuffling a context means the whole
            // context MAY have cycled — but this outer block deliberately also fires on manual SEEKs, and a
            // manual jump onto the last playback-order item is NOT a completed cycle. Marking there would
            // brand a barely-started playlist as finished (and its next re-activation would then reset a
            // memory that was still half full). The mark requires the same proof as every other
            // cycle-complete site: a natural AUTO advance, repeat OFF, and a genuinely exhausted context.
            // The radio handoff below still runs either way (it's the queue-end UX, independent of memory).
            if (enhancedShuffleHint && player.shuffleModeEnabled &&
                reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO &&
                player.repeatMode == Player.REPEAT_MODE_OFF &&
                isEnhancedContextExhausted()
            ) {
                shuffleContextId?.let { markEnhancedContextCycleComplete(it) }
            }
            // Radio owns the queue from here EITHER WAY — detach the context so foreign radio ids are
            // never recorded into the playlist's persistent memory (a linear listener's nightly radio
            // tail used to accumulate unbounded rows). The memory itself is NEVER wiped here: it is kept
            // until the user re-activates shuffle on this list (see seedEnhancedShuffleFromDb).
            shuffleContextId = null
            startRadioSeamlessly()
        }


        if (persistentQueueHint) {
            // Same content-vs-position split as onPlaybackStateChanged: an auto-advance where nothing was
            // appended only moves the INDEX, which the position file already captures.
            if (queueDirty) {
                queueDirty = false
                saveQueueToDisk()
            } else {
                runCatching { savePlaybackPositionToDisk() }
            }
        }
    }

    /**
     * Queue PAGINATION (auto-load-more) for the track that just became current. Extracted so the
     * CROSSFADE SWAP path can call it too: with crossfade ON (the default) every auto-advance skips
     * onMediaItemTransition, so a long YouTube playlist/album never pulled its next page and fell into
     * the infinite-radio safety net instead of continuing the list the user actually chose.
     */
    private fun maybeLoadMoreQueuePages(isRepeatTransition: Boolean) {
        val remainingInPlaybackOrder = run {
            val timeline = player.currentTimeline
            var idx = player.currentMediaItemIndex
            // Guard the empty/transition edge: on an empty timeline currentMediaItemIndex is
            // C.INDEX_UNSET (-1), and getNextWindowIndex(-1, ...) indexes windows[-1] on a length-0
            // array -> ArrayIndexOutOfBoundsException (length=0; index=-1), which crashed the app from
            // onMediaItemTransition when a queue emptied (e.g. every track failing to resolve). With no
            // valid current item there is no "near the end" to measure, so report a large count -> the
            // autoload gate below is false and we never touch the timeline at -1.
            if (timeline.isEmpty || idx == C.INDEX_UNSET) {
                Int.MAX_VALUE
            } else {
                var n = 0
                while (n <= 5) {
                    idx = timeline.getNextWindowIndex(idx, Player.REPEAT_MODE_OFF, player.shuffleModeEnabled)
                    if (idx == C.INDEX_UNSET) break
                    n++
                }
                n
            }
        }
        if (autoLoadMoreHint &&
            !isRepeatTransition &&
            remainingInPlaybackOrder <= 5 &&
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
                    var next = currentQueue.nextPage()
                        .filterExplicit(dataStore.get(HideExplicitKey, false))
                        .filterVideoSongs(dataStore.get(HideVideoSongsKey, false) || dataStore.get(iad1tya.echo.music.constants.DataSaverEnabledKey, false))
                    // Non-music filter only on automatic radio / album-radio continuation — never on
                    // user playlist pagination (YouTubePlaylistQueue) where rows may lack a type.
                    val filterAutoNonMusic =
                        (currentQueue as? YouTubeQueue)?.automaticRadio == true ||
                            currentQueue is LocalAlbumRadio ||
                            currentQueue is YouTubeAlbumRadio
                    if (filterAutoNonMusic) {
                        next = next.filterNonMusicForAutoQueue()
                    }
                    // ENRICH BEFORE SCORING (see [ENRICH_BEFORE_SCORE_MS]). This is the enrich run that
                    // used to fire AFTER the items were queued, moved AHEAD of the snapshot below so
                    // the lane filter and orderedByTaste's steer are decided with what we just learned
                    // instead of one batch late. It is launched on `scope`, so the budget bounds only
                    // how long we WAIT: on timeout the run continues exactly as the fire-and-forget did.
                    // The page fetch above already returned, so ~5 items (~17 min) still sit ahead of the
                    // play head — this can never be heard.
                    // Its INPUT is not identical to the old run's, and cannot be: the old one read the
                    // FINAL `mediaItems` (post dislike/lane/session filters), which do not exist yet here
                    // — they are what this run has to inform. It reads the RAW page instead: a superset in
                    // content, still capped at GENRE_LEARN_PER_RUN, still WiFi-gated, still one `scope`
                    // launch, so the ceiling on requests and battery is unchanged; what differs is that
                    // some of the 12 slots can go to candidates the filters below then drop.
                    val enrichNames = if (!keepLane) emptyList() else
                        (curArtists + next.flatMap { it.metadata?.artists.orEmpty() }.map { it.name })
                            .filter { it.isNotBlank() }
                            .distinct()
                            .take(GENRE_LEARN_PER_RUN)
                    if (enrichNames.isNotEmpty()) {
                        val enrichJob = scope.launch(Dispatchers.IO + SilentHandler) {
                            runCatching {
                                iad1tya.echo.music.reco.GenreCache.enrich(this@MusicService, enrichNames, onlyWifi = true)
                            }
                        }
                        val waited = withTimeoutOrNull(ENRICH_BEFORE_SCORE_MS) { enrichJob.join() } != null
                        Timber.tag(TAG).i(
                            "CTX_GENRE enrich-before-score (pagination): %d artists, completed=%b",
                            enrichNames.size, waited,
                        )
                    }
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
                    // Two DIFFERENT strictnesses, keyed on the lane's SIGNAL ORIGIN — never on its value:
                    //  - KEYWORD-derived CHRISTIAN reads the track's own text, so it needs no cache and an
                    //    unknown candidate is, in practice, secular -> keep the ORIGINAL strict "must match".
                    //  - Anything derived from GenreCache (INCLUDING a "Christian & Gospel" artist) must be soft:
                    //    the cache is only enriched with artists from YOUR library (HomeViewModel), so a new radio
                    //    artist is unknown BY CONSTRUCTION. Strictness there would drop every unknown candidate and
                    //    collapse autoplay onto library artists (repetitive, no discovery). So we only drop
                    //    candidates whose genre we KNOW and know to be different; unknown stays eligible.
                    if (currentLane != null) {
                        val strictLane = currentLane == iad1tya.echo.music.reco.GenreLane.CHRISTIAN &&
                            iad1tya.echo.music.reco.GenreLane.isKeywordChristian(curTitle, curArtist, curAlbum)
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
                // The genre-learning run that used to live HERE (fire-and-forget, after the items were
                // already queued) now runs above, BEFORE the snapshot that scores them — same
                // GENRE_LEARN_PER_RUN cap, same WiFi gate, same `scope` launch, so the ceiling on network
                // and battery is unchanged; it necessarily reads the RAW page rather than this final list
                // (see there). Enriching a batch after deciding its order is what made the smart queue
                // right one batch and wrong the next.
                //
                // Feeding it INDIVIDUAL artist names still matters (mediaMetadata.artist is a ", "-joined
                // byline): iTunes is queried with attribute=artistTerm, so "Bad Bunny, Chencho Corleone"
                // can only ever MISS — and a miss is cached forever, so we would permanently burn a
                // request per collab while never learning either artist. GenreCache is keyed by ONE
                // artist name, which is what the lane looks up.
            }
        }
    }

    /**
     * SponsorBlock: fetch skippable non-music segments for the track that just became current (YouTube ids
     * only; local songs have long content:// ids and are skipped). Stale responses are ignored inside the
     * manager. Extracted so the CROSSFADE SWAP path can call it too — with crossfade ON (the default) every
     * auto-advance skips onMediaItemTransition, so segments were only ever fetched for manually started
     * tracks and the feature looked dead despite being ON by default.
     */
    private fun applySponsorBlockFor(mediaId: String?) {
        if (sponsorBlockEnabled) {
            val sbVideoId = sponsorBlock.begin(mediaId)
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
    }

    /**
     * Everything that must happen when a NEW track becomes current on a NATURAL auto-advance, shared by the
     * two advance paths: onMediaItemTransition (crossfade OFF / manual) and beginCrossfadeSwap (crossfade
     * ON — the DEFAULT, whose swap skips the transition callback entirely). Without this mirror, scrobbling,
     * Cast follow-along, SponsorBlock, upcoming-track prefetch, the REPEAT_ONE index guard and the speed
     * cache all silently stopped working in the app's default configuration.
     */
    private fun applyAutoAdvanceSideEffects() {
        previousMediaItemIndex = player.currentMediaItemIndex
        lastPlaybackSpeed = -1.0f
        preloadUpcomingItems()
        applySponsorBlockFor(player.currentMediaItem?.mediaId)

        scrobbleManager?.onSongStop()
        if (player.playWhenReady && player.playbackState == Player.STATE_READY) {
            scrobbleManager?.onSongStart(player.currentMetadata, duration = player.duration)
            // onSongStart only sends "now playing" and RESETS the counters — it does not start the
            // play-time ticker; only onPlayerStateChanged(isPlaying=true) does, and that used to arrive
            // via EVENT_IS_PLAYING_CHANGED. On a crossfade swap the incoming player is ALREADY playing
            // before the service listener is attached, so that event never reaches us and the track was
            // never actually scrobbled (only "now playing" was sent). Start the ticker explicitly.
            if (player.isPlaying) {
                scrobbleManager?.onPlayerStateChanged(true, player.currentMetadata, player.duration)
            }
        }

        if (castConnectionHandler?.isCasting?.value == true &&
            castConnectionHandler?.isSyncingFromCast != true
        ) {
            player.currentMediaItem?.metadata?.let { metadata ->
                val navigated = castConnectionHandler?.navigateToMediaIfInQueue(metadata.id) ?: false
                if (!navigated) {
                    castConnectionHandler?.loadMedia(metadata)
                }
            }
        }
    }

    override fun onIsPlayingChanged(isPlaying: Boolean) {
        val autoConnected = isAndroidAutoControllerConnected()
        playbackKeepAlive.setAndroidAutoConnected(autoConnected)
        playbackKeepAlive.setPlaying(isPlaying)
        if (isPlaying) {
            startPeriodicPersist()
            if ((getSystemService(POWER_SERVICE) as? android.os.PowerManager)?.isPowerSaveMode == true) {
                // Proven contributor to BT/AA cuts on HyperOS: saver throttles network → underruns.
                // Also pairs with ScreenOffCPUCheckKill when the OEM demotes the process.
                Timber.tag(TAG).w(
                    "POWER_SAVE_ON while playing — turn off battery saver to avoid Bluetooth/Android Auto cuts",
                )
            }
        } else {
            // Nothing is audible right now, so a deferred offload ENABLE can land for free — and this
            // is what makes it land at all for a listener who pauses instead of finishing the track.
            flushPendingOffloadEnable()
            // Stop the periodic wake-ups while paused/idle and save the position once so nothing is lost.
            periodicPersistJob?.cancel()
            periodicPersistJob = null
            if (dataStore.get(PersistentQueueKey, true)) {
                // Same dirty split every other writer uses. This was the one UNGATED position write left:
                // pause right after a queue mutation and the position file described a timeline the queue
                // file did not have yet — and while PAUSED nothing else flushes for up to 30 s, so a kill
                // in that window restored onto the wrong song. Flushing here also lets the slow paused
                // loop skip an already-done save.
                if (queueDirty) {
                    queueDirty = false
                    runCatching { saveQueueToDisk() }
                } else {
                    scope.launch { runCatching { savePlaybackPositionToDisk() } }
                }
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

    /**
     * Set whenever the timeline actually changes, cleared by the periodic saver (#55). Starts true so the
     * first tick always persists.
     *
     * A DIRTY FLAG, not a shape hash. The obvious "count:index:currentId" signature silently misses a
     * drag-REORDER of upcoming tracks below the current index (`player.moveMediaItem`) — count, index and
     * current id are all unchanged — as well as remove-one-then-add-one and any in-place replacement. Those
     * are exactly the edits a user makes while PAUSED, which is the case the un-gated 30s save exists to
     * protect: reorder the queue, screen off, MIUI reaps the service, order lost. media3 fires
     * onTimelineChanged for every one of them.
     */
    @Volatile private var queueDirty = true

    /**
     * True while there is an ORDER-RELEVANT change the shuffle order has not absorbed yet (an append, a
     * toggle, the DB seed landing, a user queue edit). The per-boundary re-apply in beginCrossfadeSwap
     * consumes it; when it is false the re-apply is SKIPPED — re-scoring and re-sorting the whole queue on
     * the Main thread at every song boundary, then re-broadcasting the order to Android Auto over Binder,
     * was the per-boundary burst car users heard as micro-stutters. Starts true so the first apply runs.
     */
    @Volatile private var shuffleOrderStale = true

    /**
     * True only while WE are inside player.setShuffleOrder. media3 dispatches onTimelineChanged
     * SYNCHRONOUSLY on this same (Main) thread, so this plain flag is race-free. It stops our own
     * order re-applies from (a) marking the queue dirty — PersistQueue stores items in TIMELINE order,
     * so those full saves rewrote identical bytes, pure Main-thread churn + flash wear per boundary —
     * and (b) marking the shuffle order stale, which would make every re-apply schedule the next one.
     *
     * CONTRACT: the synchronous dispatch holds ONLY when setShuffleOrder is called OUTSIDE a media3
     * listener callback — from inside a ListenerSet flush the self-event is DEFERRED past this window.
     * That is why every beginShuffleSession call that originates in a listener callback is POSTED
     * (scope.launch) instead of called inline.
     */
    private var applyingShuffleOrder = false

    /**
     * True only while playQueue is programmatically clearing shuffle for a NEW queue, so
     * onShuffleModeEnabledChanged can tell that reset apart from a real user toggle and skip
     * persisting it. Without this the app's own reset overwrote ShuffleModeKey with false and
     * "remember shuffle and repeat" could never survive an app restart.
     *
     * Written and read on the player/main thread only; @Volatile is belt-and-braces.
     */
    @Volatile private var suppressShuffleModePersist = false

    private fun startPeriodicPersist() {
        if (periodicPersistJob?.isActive == true) return
        periodicPersistJob = scope.launch {
            var tick = 0
            while (true) {
                kotlinx.coroutines.delay(5000)
                tick++
                // ONE-SHOT measurement cache: persist a measured loudness for the NEXT play. Never
                // moves the live gain (like + auto-download used that path to swell the volume).
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
                    if (ok) {
                        // COHERENCE: while queueDirty the queue FILE still describes the OLD timeline, so a
                        // position written now would point INTO a queue that no longer matches — restore
                        // then lands on the wrong song. This tick used to FLUSH the whole queue here
                        // (a full triple-graph snapshot on Main) to close that window, but the dedicated
                        // coherence loop already flushes dirty queues on its own 10 s cadence — doing it
                        // from BOTH writers doubled the Main-thread churn for nothing. Now: skip the
                        // position write while dirty (never write an incoherent pair) and let the
                        // dedicated loop do the one flush.
                        if (!queueDirty) runCatching { savePlaybackPositionToDisk() }
                    }
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
            // Full triple-graph serialization ONLY when the queue content actually changed (queueDirty =
            // any timeline mutation). Plain state flips (BUFFERING→READY fires ≥1× per song) re-wrote the
            // ENTIRE queue to flash each time — on big radio queues that was megabytes per song of pure
            // wear. The position file carries index/position/state, so restore loses nothing.
            if (queueDirty) {
                queueDirty = false
                saveQueueToDisk()
            } else {
                runCatching { savePlaybackPositionToDisk() }
            }
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

        if (_videoMode.value && !_videoUrl.value.isNullOrEmpty()) {
            when (playbackState) {
                // BUFFERING is a healthy rebuffer — do not arm stuck recovery (see maybeRecoverStuckVideo).
                Player.STATE_IDLE -> scheduleVideoStuckRecoveryCheck()
                Player.STATE_READY, Player.STATE_BUFFERING -> videoStuckRecoveryJob?.cancel()
            }
            if (playbackState == Player.STATE_READY && player.bufferedPosition >= 8_000L) {
                runCatching { flushAllPendingSongDownloads(this) }
            }
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
            pausedByNoisy = false
        }

        if (reason == Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST) {
            if (playWhenReady) {
                isPausedByVolumeMute = false
                pausedByNoisy = false
            } else if (!isPausedByVolumeMute) {
                // Real user pause — never auto-resume on route reconnect.
                pausedByNoisy = false
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
            // Every enable that reaches this callback is a USER activation (a toggle, a Shuffle button,
            // the car) EXCEPT the queue restore, which announces itself with suppressShuffleActivationReset.
            // Captured NOW (the flag's contract is synchronous dispatch), applied one message later:
            // this callback runs inside media3's ListenerSet flush, and applyShuffleOrder invoked from
            // inside a flush gets its self-onTimelineChanged DEFERRED past the applyingShuffleOrder
            // window, re-arming stale/dirty as if a real mutation happened. Posting exits the flush; the
            // queue file stores TIMELINE order, so nothing below observes the order's timing.
            val isUserActivation = !suppressShuffleActivationReset
            scope.launch { beginShuffleSession(isUserActivation = isUserActivation) }
        }


        // Persist the USER's choice only. A programmatic reset (playQueue clearing shuffle for a new
        // queue) must not overwrite it, or "remember shuffle" can never survive a restart.
        if (!suppressShuffleModePersist && dataStore.get(RememberShuffleAndRepeatKey, true)) {
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

    /**
     * Starts an anti-repeat shuffle session for the CURRENT queue: reset the in-memory played set, count
     * the current song, order the queue, then seed the persistent memory and re-order once it loads.
     *
     * Extracted from [onShuffleModeEnabledChanged] because that callback is NOT a reliable trigger.
     * media3 early-returns from `setShuffleModeEnabled` when the value is unchanged, so a queue RESTORED
     * with shuffle already on never fired it, and this whole session never started:
     *   1. boot sets player.shuffleModeEnabled from ShuffleModeKey while the player is still EMPTY, so
     *      this session bails on mediaItemCount == 0;
     *   2. playQueue's reset-to-false is deliberately skipped on a restore (`!isRestore`), so the flag
     *      stays true;
     *   3. the per-queue restore then assigns true again — the SAME value — and media3 stays silent.
     * The net effect was shuffle running with an EMPTY played set and media3's default random order: the
     * persistent memory was intact in the DB, and nobody ever read it, so already-heard songs came back
     * after every restart. Callers that change the flag get this via the callback; the restore path calls
     * it directly. Safe to call twice — it is idempotent for a given queue.
     *
     * [isUserActivation] carries the owner's rule: a finished list keeps its memory until HE turns shuffle
     * on again for it. Only a true user activation may reset the lap; a restore (the app re-installing its
     * own previous state) may not.
     */
    /**
     * Player/queue shuffle control: turn shuffle ON (starts a session via the media3 callback), or if
     * already ON force a fresh anti-repeat session + reshuffle. media3 ignores setShuffleModeEnabled(true)
     * when the flag is already true, so a second tap would otherwise be a no-op.
     */
    fun toggleShuffleOrReshuffle() {
        if (player.shuffleModeEnabled) {
            beginShuffleSession(isUserActivation = true)
        } else {
            player.shuffleModeEnabled = true
        }
    }

    private fun beginShuffleSession(isUserActivation: Boolean = true) {
            if (player.mediaItemCount == 0) return

            // B5: start a fresh anti-repeat session each time shuffle is enabled; the current song counts as played.
            shufflePlayedIds.clear()
            // A new session starts with no artist history; the opener re-seeds it below.
            recentShuffleArtists.clear()
            rememberShuffleArtist(player.currentMediaItem)
            player.currentMetadata?.id?.let { cur ->
                shufflePlayedIds.add(cur)
                // Enhanced Shuffle: persist the CURRENT song too. Both DB insert sites record the newly-
                // current item of a LATER transition/swap, so the song playing at enable time (song #1 of a
                // button-started shuffle) was never written — after a process kill it came back unmarked and
                // could replay within the same cycle.
                val enableCtx = shuffleContextId
                if (enhancedShuffleHint && enableCtx != null) {
                    val now = System.currentTimeMillis()
                    scope.launch(enhancedShuffleWriteDispatcher) {
                        runCatching { database.insertEnhancedPlayed(EnhancedShufflePlayedEntity(enableCtx, cur, now)) }
                    }
                }
            }

            applyPendingSeedPlayedIds()

            val shufflePlaylistFirst = dataStore.get(ShufflePlaylistFirstKey, false)
            val currentIndex = player.currentMediaItemIndex
            val totalCount = player.mediaItemCount

            // Apply the order NOW (fallback = today's behaviour) so the player never blocks on the DB read.
            applyShuffleOrder(currentIndex, totalCount, shufflePlaylistFirst)

            // Enhanced Shuffle ("Aleatorio mejorado"): if this queue has a persistent context, SEED the
            // played-set from the on-disk memory (∩ current queue ids, + the current song) and RE-APPLY the
            // order once loaded, so re-enabling shuffle re-shuffles only the UNPLAYED remainder and never
            // repeats a song until the whole context has cycled. The DB read is async; if it hasn't finished
            // the fallback above already plays — we just refine the order when it lands.
            val ctx = shuffleContextId
            if (enhancedShuffleHint && ctx != null) {
                seedEnhancedShuffleFromDb(ctx, shufflePlaylistFirst, isUserActivation)
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

    /** COVERAGE of the LIVE context, or [EnhancedShuffleCycle.COVERAGE_UNKNOWN] when it is not known. */
    private fun currentContextCoverage(): Int =
        EnhancedShuffleCycle.coverageOf(shuffleContextId, contextCoverageId, contextCoverageSize)

    /**
     * Merge the screen's Continue seed (shuffle memory ∪ lifetime plays) into this session, then persist
     * those ids so a process death does not resurrect them as unplayed. Consumed once; Start over sends
     * an empty set so history is not re-imported.
     */
    private fun applyPendingSeedPlayedIds() {
        val ids = pendingSeedPlayedIds
        pendingSeedPlayedIds = emptySet()
        if (!enhancedShuffleHint || ids.isEmpty()) return
        shufflePlayedIds.addAll(ids)
        val ctx = shuffleContextId ?: return
        val now = System.currentTimeMillis()
        scope.launch(enhancedShuffleWriteDispatcher) {
            ids.forEach { id ->
                runCatching { database.insertEnhancedPlayed(EnhancedShufflePlayedEntity(ctx, id, now)) }
            }
        }
    }

    /**
     * Enhanced Shuffle: true when every id in the CURRENT timeline is already in [shufflePlayedIds] — i.e.
     * the unplayed pool is empty and continuing would only replay already-heard songs. Player-thread only,
     * O(queue size) with NO allocation (the ids are read through an accessor, not copied into a list — this
     * runs on every auto-advance with crossfade ON, on queues of thousands of items). Any missing/failed id
     * read is treated as "not exhausted" (never a false positive).
     */
    private fun isEnhancedContextExhausted(): Boolean =
        EnhancedShuffleCycle.isCycleComplete(
            timelineSize = player.mediaItemCount,
            coverageSize = currentContextCoverage(),
            playedIds = shufflePlayedIds,
        ) { i -> runCatching { player.getMediaItemAt(i).mediaId }.getOrNull() }

    /**
     * Enhanced Shuffle: a context finished a full no-repeat lap (every song played).
     *
     * It does NOT wipe the memory any more — that is the owner's rule: "lo que ya se reprodujo de la lista
     * no se vuelva a repetir A MENOS QUE ya se haya finalizado la reproducción de esa lista **Y** el
     * usuario vuelva a activar el aleatorio". Two conditions, not one. Completion is only the FIRST; the
     * lap is reset when he turns shuffle on again for this list (see [seedEnhancedShuffleFromDb]). Until
     * then the memory stays, so returning to the list later still knows what he heard.
     *
     * What completion does do — unchanged — is hand the queue over to the infinite radio at the call sites,
     * so playback never stops and never loops the songs it just played.
     *
     * The marker itself is the memory: "finished" = the persistent played-set covers the whole list, which
     * is re-derived at re-activation time and therefore survives the process dying overnight for free. The
     * in-process set is advisory (trace + one cycle bump per completion).
     */
    private fun markEnhancedContextCycleComplete(contextId: String) {
        val isNew = rememberCompletedContext(contextId)
        if (!isNew) return // already counted this lap in this process; the DB write is not idempotent (a counter)
        val now = System.currentTimeMillis()
        // Same single-lane writer as the per-song insert, so this can never be reordered against the
        // per-song rows launched before it.
        scope.launch(enhancedShuffleWriteDispatcher) {
            runCatching {
                database.insertEnhancedContextIgnore(EnhancedShuffleContextEntity(contextId = contextId, updatedAt = now))
                database.incrementEnhancedCycle(contextId, now)
            }
        }
    }

    /**
     * Enhanced Shuffle: THE ONLY place the persistent per-context memory is wiped. Reached exclusively from
     * [seedEnhancedShuffleFromDb] when both of the owner's conditions hold — the list is finished AND he
     * just re-activated shuffle on it — so a fresh lap begins with the count reset for THAT list only.
     *
     * The wipe and the re-insert of what the new lap has ALREADY heard are one unit of work on the
     * single-lane writer: split in two, a per-song insert launched in between would be deleted, and those
     * songs would come back as unplayed after a process death (a guaranteed repeat per restart).
     * [openerIds] is the new lap's in-memory set — the song that opened it plus anything played while the
     * DB read was in flight, whose own inserts were queued BEFORE this DELETE and are about to be erased.
     */
    private fun resetEnhancedContextMemory(contextId: String, openerIds: Collection<String>) {
        completedShuffleContexts.remove(contextId)
        val now = System.currentTimeMillis()
        val openers = openerIds.toList() // snapshot: the live set belongs to the player thread
        scope.launch(enhancedShuffleWriteDispatcher) {
            runCatching {
                database.clearEnhancedContext(contextId)
                database.insertEnhancedContextIgnore(EnhancedShuffleContextEntity(contextId = contextId, updatedAt = now))
                openers.forEach { id ->
                    database.insertEnhancedPlayed(EnhancedShufflePlayedEntity(contextId, id, now))
                }
            }
        }
    }

    /**
     * Enhanced Shuffle: SEED the in-memory B5 played-set from the PERSISTENT per-context memory (∩ current
     * queue ids, + the current song) and RE-APPLY the order once loaded — or, when the list turns out to be
     * FINISHED and this is a user re-activation, reset the lap instead. Shared by two callers:
     *  - beginShuffleSession — shuffle toggled ON for a queue with a context (the toggle, a Shuffle button,
     *    the car, or a restore — the restore passes isUserActivation = false);
     *  - playQueue — a queue STARTS while shuffle is ALREADY ON. The toggle callback never fires then
     *    (true→true is not a change), which used to leave the order blind to persisted plays: the UI
     *    (reading the DB) showed songs as played, yet the ORDER (reading this set) replayed them.
     * The DB read is async; the current order keeps playing and is refined when the seed lands.
     *
     * WHY the "is it finished?" question is answered HERE and not from a stored flag: the persistent set is
     * the ground truth, so the answer survives a process death with no schema change, and it stays correct
     * when the LIST changed since it finished — three songs added to a completed playlist make it unfinished
     * again, and those three are exactly what should play first. A stored "completed" flag could not know.
     */
    private fun seedEnhancedShuffleFromDb(ctx: String, shufflePlaylistFirst: Boolean, isUserActivation: Boolean) {
        scope.launch(Dispatchers.IO) {
            val persisted = runCatching { database.playedSongIdsForContext(ctx) }
                .getOrNull()?.toHashSet() ?: return@launch
            if (persisted.isEmpty()) return@launch
            withContext(Dispatchers.Main) {
                // Bail if the world moved on while we were reading (queue/shuffle/context changed).
                if (!player.shuffleModeEnabled || shuffleContextId != ctx || player.mediaItemCount == 0) {
                    return@withContext
                }
                val queueIds = (0 until player.mediaItemCount).map {
                    runCatching { player.getMediaItemAt(it).mediaId }.getOrNull()
                }
                // Condition (a): is this list genuinely finished? Judged against the CONTEXT (its coverage),
                // never against the live timeline alone — a queue the user trimmed down to songs he already
                // heard reads "all played" while the list still holds unheard ones (registry row 94(e)).
                val cycleComplete = EnhancedShuffleCycle.isCycleComplete(
                    timelineSize = queueIds.size,
                    coverageSize = currentContextCoverage(),
                    playedIds = persisted,
                ) { i -> queueIds[i] }
                if (EnhancedShuffleCycle.shouldResetForNewCycle(isUserActivation, cycleComplete)) {
                    // (a) AND (b): finished list + the user turning shuffle on again = the count resets, for
                    // THIS list only, and a fresh lap starts. shufflePlayedIds was already cleared by the
                    // caller (it holds the current song, plus anything played while this read was in
                    // flight), so it IS the new lap and the order below is a full re-shuffle.
                    player.currentMetadata?.id?.let { shufflePlayedIds.add(it) }
                    resetEnhancedContextMemory(ctx, shufflePlayedIds)
                    Timber.tag(TAG).i("NO_REPEAT cycle-reset ctx=%s size=%d (finished + re-activated)", ctx, queueIds.size)
                    applyShuffleOrder(player.currentMediaItemIndex, player.mediaItemCount, shufflePlaylistFirst)
                    return@withContext
                }
                val seed = LinkedHashSet<String>()
                queueIds.forEach { id -> if (id != null && id in persisted) seed.add(id) }
                player.currentMetadata?.id?.let { seed.add(it) }
                // UNION, never clear: songs the user played/skipped DURING this async DB read were
                // already added to shufflePlayedIds by onMediaItemTransition on the Main thread. A
                // clear() here would drop them and let them repeat. Each caller has already reset the
                // set for its own session, so adding the persisted set is a pure union of "played
                // before" + "played during the read window".
                shufflePlayedIds.addAll(seed)
                applyShuffleOrder(player.currentMediaItemIndex, player.mediaItemCount, shufflePlaylistFirst)
            }
        }
    }

    /**
     * ONE walk of the timeline feeding everything the shuffle order needs: the media ids (the
     * played/unplayed split), the cached taste score, and an INTERNED primary-artist key per index
     * ([ShuffleOrdering.ARTIST_UNKNOWN] when the item has no usable artist), plus how many DISTINCT
     * artists the queue holds so the caller can size the spacing window.
     *
     * Both branches of [applyShuffleOrder] used to walk the timeline for themselves — the "playlist
     * first" branch twice, once per played-check — and only the main one ever looked at artists at all.
     * Sharing a single walk pays for the artist keys the other branch never had.
     *
     * Reuses [shuffleTasteCache] / [shuffleArtistCache]: this runs on the Main thread on every re-apply
     * (a radio append, a toggle, a completed lap), and scoring thousands of items from scratch there was
     * a measured 20-60 ms stall. Artist names are trimmed + lowercased so a stray space cannot split one
     * artist in two and defeat the spacing.
     */
    private class ShuffleItemKeys(
        val mediaIds: Array<String?>,
        val taste: DoubleArray,
        val artistKey: IntArray,
        val interned: Map<String, Int>,
        /** How many songs the QUEUE'S BUSIEST artist holds — the constraint that binds artist spacing. */
        val densestArtistCount: Int,
    ) {
        val distinctArtists: Int get() = interned.size
    }

    private fun shuffleItemKeys(totalCount: Int, p: iad1tya.echo.music.reco.TasteProfile?): ShuffleItemKeys {
        val mediaIds = arrayOfNulls<String>(totalCount)
        val taste = DoubleArray(totalCount)
        val artistKey = IntArray(totalCount)
        val interned = HashMap<String, Int>()
        // Interned ids are handed out as 1..distinct, so an IntArray indexed by id counts them with no
        // per-item allocation (this loop walks thousands of items on the Main thread).
        val perArtistCount = IntArray(totalCount + 1)
        var densest = 0
        // ONE timeline read and ONE reusable Window for the whole walk. `player.getMediaItemAt(i)` is
        // literally `getCurrentTimeline().getWindow(i, sharedWindow).mediaItem`, so calling it per item
        // paid an application-thread check and a playbackInfo hop N times over — on the Main thread, on
        // every re-apply, and the queue only grows while the infinite radio appends. Same MediaItem
        // instances, same values; the walk just stops re-entering the player for each one. A null
        // timeline (player released mid-walk) degrades to today's null-item path, item by item.
        val timeline: Timeline? = runCatching { player.currentTimeline }.getOrNull()
        val timelineWindows = timeline?.windowCount ?: 0
        val window = Timeline.Window()
        for (i in 0 until totalCount) {
            val item = if (i < timelineWindows) {
                runCatching { timeline?.getWindow(i, window)?.mediaItem }.getOrNull()
            } else {
                null
            }
            val itemId = item?.mediaId
            mediaIds[i] = itemId
            // Two lookups, two lifetimes: the artist survives a taste-profile refresh, the score does not.
            val name = itemId?.let { shuffleArtistCache[it] } ?: run {
                val a = item?.metadata?.artists?.firstOrNull()?.name?.trim()?.lowercase().orEmpty()
                if (itemId != null) {
                    if (shuffleArtistCache.size > 20_000) shuffleArtistCache.clear()
                    shuffleArtistCache[itemId] = a
                }
                a
            }
            taste[i] = itemId?.let { shuffleTasteCache[it] } ?: run {
                val m = item?.metadata
                val t = if (p != null && m != null) p.scoreNames(m.artists.map { it.name }, m.title) else 0.0
                if (itemId != null) {
                    if (shuffleTasteCache.size > 20_000) shuffleTasteCache.clear()
                    shuffleTasteCache[itemId] = t
                }
                t
            }
            if (name.isEmpty()) {
                artistKey[i] = ShuffleOrdering.ARTIST_UNKNOWN
            } else {
                val id = interned.getOrPut(name) { interned.size + 1 }
                artistKey[i] = id
                val c = perArtistCount[id] + 1
                perArtistCount[id] = c
                if (c > densest) densest = c
            }
        }
        return ShuffleItemKeys(mediaIds, taste, artistKey, interned, densest)
    }

    /**
     * The artists heard just BEFORE this order, oldest first, translated into [keys]' interned ids.
     *
     * Lookup only — an artist that is not in the current queue cannot collide with anything in it, and
     * interning it here would inflate the distinct-artist count the spacing window is derived from.
     */
    private fun recentArtistSeed(keys: ShuffleItemKeys): IntArray =
        IntArray(recentShuffleArtists.size) { i ->
            keys.interned[recentShuffleArtists[i]] ?: ShuffleOrdering.ARTIST_UNKNOWN
        }

    /**
     * One INFO line per applied order describing the artist adjacency it actually produced, so
     * "me pone bastante seguido el mismo artista" stops being unfalsifiable. INFO because AppLogger only
     * PERSISTS >= INFO — at DEBUG the line would exist only in a debug build, i.e. exactly where the bug
     * does not happen. `gap1` > 0 on a queue with several artists is a real spacing failure; `gap1` > 0
     * with `artists=1` is a single-artist list, where spacing has nothing to work with.
     */
    private fun traceShuffleSpacing(
        branch: String,
        order: IntArray,
        keys: ShuffleItemKeys,
        window: Int,
        elapsedMs: Long,
    ) {
        runCatching {
            val adj = ShuffleOrdering.artistAdjacency(order, keys.artistKey)
            // `ms` is the whole re-apply (timeline walk + scoring + sort + spacing) on the MAIN thread.
            // Reported because "en Android Auto sigo teniendo micro cortes" needs a number: this cost
            // scales with the queue, and the infinite radio only ever grows it. Same INFO reasoning as
            // the rest of the line — AppLogger persists >= INFO, and the bug only happens in release.
            Timber.tag(TAG).i(
                "SHUFFLE_SPACING %s n=%d artists=%d window=%d head=%d headArtists=%d gap1=%d gap2=%d minGap=%d topArtist=%d ms=%d",
                branch,
                order.size,
                keys.distinctArtists,
                window,
                adj.measured,
                adj.distinct,
                adj.gap1,
                adj.gap2,
                adj.minGap,
                adj.topArtistCount,
                elapsedMs,
            )
        }
    }

    private fun applyShuffleOrder(
        currentIndex: Int,
        totalCount: Int,
        shufflePlaylistFirst: Boolean
    ) {
        if (totalCount == 0) return
        val applyStartNs = System.nanoTime()

        // SCORE CACHE (thermal audit): with crossfade ON this whole function re-runs on EVERY
        // auto-advance, and scoring 5000 items (scoreNames + lowercase allocations each) cost 20-60 ms on
        // the Main thread per advance. Taste and primary artist are stable per mediaId for a given taste
        // profile — cache them and only the cheap random term is fresh per apply. Hoisted above the branch
        // because BOTH branches now need artist keys.
        val cachedProfile = cachedTaste
        if (shuffleScoreCacheProfile !== cachedProfile) {
            // Only the TASTE half depends on the profile — see the field declarations.
            shuffleTasteCache.clear()
            shuffleScoreCacheProfile = cachedProfile
        }
        val itemKeys = shuffleItemKeys(totalCount, cachedProfile)
        val artistWindow = ShuffleOrdering.artistWindowFor(
            distinctArtists = itemKeys.distinctArtists,
            totalItems = totalCount,
            densestArtistCount = itemKeys.densestArtistCount,
        )

        if (shufflePlaylistFirst && originalQueueSize > 0 && originalQueueSize < totalCount) {
            // "Playlist first" keeps original songs ahead of appended (radio) ones — but this branch used
            // to shuffle the originals UNIFORMLY, with no played-sink at all: after any append, played
            // originals were reordered back into the upcoming order while unplayed ones remained → the
            // no-repeat promise was absent under this setting. Same partition as B5: within the original
            // group, unplayed precede played; the appended group (fresh radio) sits between them.
            val playedSnapshot = HashSet(shufflePlayedIds)
            fun idxPlayed(i: Int): Boolean = itemKeys.mediaIds.getOrNull(i)?.let { it in playedSnapshot } == true

            val originalAll = (0 until originalQueueSize).filter { it != currentIndex }
            val originalUnplayed = originalAll.filterNot(::idxPlayed).toMutableList()
            val originalPlayed = originalAll.filter(::idxPlayed).toMutableList()
            // The APPENDED group needs the same played/unplayed split as the original one. It used to be
            // shuffled as one undifferentiated block, so once the playlist finished and the infinite radio
            // started appending, every radio track already heard was re-shuffled uniformly back among the
            // unheard ones on the next re-apply — and with crossfade ON that re-apply happens at EVERY song
            // boundary. The no-repeat promise silently stopped at the playlist's edge.
            val addedAll = (originalQueueSize until totalCount).filter { it != currentIndex }
            val addedUnplayed = addedAll.filterNot(::idxPlayed).toMutableList()
            val addedPlayed = addedAll.filter(::idxPlayed).toMutableList()

            originalUnplayed.shuffle()
            originalPlayed.shuffle()
            addedUnplayed.shuffle()
            addedPlayed.shuffle()

            val shuffledIndices = IntArray(totalCount)
            // Swap groups for the spacing pass below: the four partitions are ordered blocks that encode
            // both the "playlist first" intent and the no-repeat sink, so spacing may reorder WITHIN a
            // partition and must never move an entry across one. The current song gets a group of its own
            // (it is frozen at slot 0 anyway).
            val groupByIndex = IntArray(totalCount) { 4 }
            originalUnplayed.forEach { groupByIndex[it] = 0 }
            addedUnplayed.forEach { groupByIndex[it] = 1 }
            originalPlayed.forEach { groupByIndex[it] = 2 }
            addedPlayed.forEach { groupByIndex[it] = 3 }
            var pos = 0
            shuffledIndices[pos++] = currentIndex
            originalUnplayed.forEach { shuffledIndices[pos++] = it }
            addedUnplayed.forEach { shuffledIndices[pos++] = it }
            // Played entries last, playlist before radio — the same "playlist first" intent, applied to the
            // leftovers so a re-heard tail can never outrank something still unheard.
            originalPlayed.forEach { shuffledIndices[pos++] = it }
            addedPlayed.forEach { shuffledIndices[pos++] = it }
            // ARTIST SPACING — this branch had NONE. "Playlist first" shuffled each partition uniformly and
            // handed the result straight to media3, so with the setting ON the owner's "me pone bastante
            // seguido el mismo artista" was simply what uniform random looks like. Same best-effort pass the
            // main branch uses, with the current song frozen at slot 0 seeding the history.
            ShuffleOrdering.spaceArtists(
                order = shuffledIndices,
                artistKey = itemKeys.artistKey,
                groupKey = groupByIndex,
                window = artistWindow,
                startAt = 1,
                seedRecent = recentArtistSeed(itemKeys),
            )
            traceShuffleSpacing(
                "playlist-first", shuffledIndices, itemKeys, artistWindow,
                (System.nanoTime() - applyStartNs) / 1_000_000L,
            )
            applyingShuffleOrder = true
            try {
                player.setShuffleOrder(DefaultShuffleOrder(shuffledIndices, System.currentTimeMillis()))
            } finally {
                applyingShuffleOrder = false
            }
            shuffleOrderStale = false
        } else {
            // Flat IntArray, not a MutableList<Int>: the list boxed an Integer per queue item and its
            // comparator boxed a Double per comparison — see ShuffleOrdering.sortIndicesByKeyDescending
            // for why the replacement produces the byte-identical order.
            val shuffledIndices = IntArray(totalCount) { it }
            // B5 anti-repeat: which queue items were already played this shuffle session? If EVERY item has
            // been played the pool is exhausted -> start a fresh cycle so shuffle keeps flowing.
            val playedSnapshot = HashSet(shufflePlayedIds)
            // !radioSeedInFlight: a radio seed is about to APPEND unplayed items — an "all played" reading
            // in that window is premature and the reset would un-sink the played tail right before fresh
            // songs land (the exhaustion handoffs deliberately keep the set intact through the handoff).
            if (playedSnapshot.isNotEmpty() && !radioSeedInFlight &&
                (0 until totalCount).all { i -> itemKeys.mediaIds[i]?.let { it in playedSnapshot } == true }
            ) {
                // IN-MEMORY reset — the "keep the music flowing" fallback, and ONLY when the handoff to the
                // infinite radio is not available for this context (continuation disabled, repeat on, no
                // context, feature off). Where the handoff IS available it must win, because clearing the
                // set here also erases the very evidence the handoff runs on: isEnhancedContextExhausted()
                // reads this set, so a reset now means the next auto-advance sees a "fresh" list and the
                // finished one silently starts another lap instead of continuing into the smart queue —
                // which is exactly what happened after a process death (the seed re-fills the set from disk,
                // this reset empties it again). Skipping the reset costs nothing audible: with every song
                // played the +1000 unplayed bonus applies to none of them, so the order below is the same
                // uniform shuffle either way — it just stays TRUTHFUL about the list being finished.
                // Never silence: media3 walks the whole shuffle order regardless of what this set says.
                val handoffAvailable = enhancedShuffleHint && autoLoadMoreHint &&
                    player.shuffleModeEnabled && shuffleContextId != null &&
                    player.repeatMode == Player.REPEAT_MODE_OFF &&
                    EnhancedShuffleCycle.coversContext(totalCount, currentContextCoverage())
                if (!handoffAvailable) {
                    shufflePlayedIds.clear()
                    player.currentMetadata?.id?.let { shufflePlayedIds.add(it) }
                    playedSnapshot.clear()
                }
                // Enhanced Shuffle: the whole context cycled → MARK it finished. It no longer wipes the
                // persistent memory: per the owner's rule the count resets only when he re-activates
                // shuffle on a finished list, so the memory has to outlive the completion (this was one of
                // ~five sites that used to reset it early). The COVERAGE gate stays and now reads the
                // context's own coverage instead of the radio seed pool: a user-trimmed timeline reading
                // "all played" is NOT proof the list cycled.
                shuffleContextId?.takeIf {
                    enhancedShuffleHint && EnhancedShuffleCycle.coversContext(totalCount, currentContextCoverage())
                }?.let { ctx -> markEnhancedContextCycleComplete(ctx) }
            }
            // Smart shuffle: nudge tracks you tend to like toward the front, but RANDOM MUST DOMINATE.
            // The old factor (taste * 0.5, taste up to ~1.7) put a favourite artist's songs 0.85 above a
            // uniform [0,1) draw — near PARITY with the entire random range — so on a big playlist that
            // artist's whole block sorted to the front and re-toggling shuffle recomputed the same bias:
            // the owner's exact "me bombardea el mismo artista / eso no tiene nada de aleatorio". Capped
            // taste contribution is now ±0.255 vs random's 1.0 (~4:1): a mild nudge, visibly shuffled.
            val rnd = java.util.Random()
            // Precompute each index's key ONCE (rnd inside the comparator would crash TimSort). A flat
            // DoubleArray, not a HashMap<Int, Double>: this runs on the Main thread at every song boundary
            // and the map boxed two objects per queue item — thousands of short-lived allocations per
            // advance, which is the same thermal budget the score cache exists to protect.
            val keys = DoubleArray(totalCount)
            // Swap groups for the spacing pass: 0 = not yet played, 1 = already played. GROUP MEMBERSHIP,
            // not the key threshold: a much-skipped artist's unplayed song can carry a NEGATIVE taste term
            // and land under 1000, so a `key >= 1000` test misread it as played — the scan then crossed
            // into real played territory and could lift an already-played song above the boundary (a repeat
            // before the cycle closed). The recorded membership can't be fooled by the key's value.
            val groupByIndex = IntArray(totalCount)
            for (i in 0 until totalCount) {
                val itemId = itemKeys.mediaIds[i]
                var key = itemKeys.taste[i].coerceIn(-1.7, 1.7) * 0.15 + rnd.nextDouble()
                // Anti-repeat: already-played songs sink BELOW all not-yet-played ones (big offset), so the
                // whole pool is exhausted before anything repeats. Within each group the smart order applies.
                val isUnplayed = itemId == null || itemId !in playedSnapshot
                if (isUnplayed) key += 1000.0
                keys[i] = key
                groupByIndex[i] = if (isUnplayed) 0 else 1
            }
            ShuffleOrdering.sortIndicesByKeyDescending(shuffledIndices, keys)

            // Anchor the CURRENT song at the front by ROTATION, not by swapping — see
            // ShuffleOrdering.anchorCurrentFirst for why a swap repeated songs at the end of every cycle.
            //
            // ORDER MATTERS: anchor FIRST, space SECOND. It used to be the other way round, and that was
            // the defect behind the owner's third report of "me ponía bastante seguido el mismo artista":
            // the rotation manufactures a brand-new `(current, next)` adjacency that the spacing pass — run
            // before it — had never seen. `order[1]` is literally the next song media3 will play, so the
            // single most audible adjacency in the whole order was the ONLY one nobody checked. Spacing now
            // runs on the final layout with slot 0 frozen (startAt = 1), so the current song constrains its
            // successor instead of being invisible to it.
            ShuffleOrdering.anchorCurrentFirst(shuffledIndices, currentIndex)
            // ARTIST SPACING (owner reports: "cuando va por un cantante solo de ese cantante me pone",
            // "me bombardea el mismo artista, eso no tiene nada de aleatorio", and now "a cada rato en modo
            // aleatorio me ponía bastante seguido el mismo artista"). See ShuffleOrdering.spaceArtists for
            // why it maximises the achieved gap instead of enforcing a fixed one, and why the previous
            // fixed-gap-of-2 version failed closed on dense pools. [recentArtistSeed] is the other half of
            // the fix: this whole function is rebuilt from scratch on every mutation (with crossfade ON,
            // about once per song), and each rebuild used to start with an EMPTY artist history.
            ShuffleOrdering.spaceArtists(
                order = shuffledIndices,
                artistKey = itemKeys.artistKey,
                groupKey = groupByIndex,
                window = artistWindow,
                startAt = 1,
                seedRecent = recentArtistSeed(itemKeys),
            )
            traceShuffleSpacing(
                "smart", shuffledIndices, itemKeys, artistWindow,
                (System.nanoTime() - applyStartNs) / 1_000_000L,
            )
            applyingShuffleOrder = true
            try {
                player.setShuffleOrder(DefaultShuffleOrder(shuffledIndices, System.currentTimeMillis()))
            } finally {
                applyingShuffleOrder = false
            }
            shuffleOrderStale = false
        }
    }

    override fun onPlaybackParametersChanged(playbackParameters: PlaybackParameters) {
        super.onPlaybackParametersChanged(playbackParameters)
        // Tempo/pitch is something the offload path can only deliver on a HAL that advertises
        // offloadVariableRateSupported=1; media3 otherwise swallows the AudioTrack failure and the
        // Tempo & pitch dialog becomes a placebo (full evidence in setOffloadEnabled). Re-publish
        // the offload request so the track selector re-evaluates it with the speed requirement this
        // value implies: offload is declined while a non-default tempo/pitch is set on a device that
        // cannot honour it — the decoder then emits PCM and media3's own Sonic does the stretch —
        // and offload comes back by itself as soon as the user returns to 1.0x, so the battery cost
        // lasts exactly as long as the feature is in use. This is the ONLY listener hook needed:
        // it fires for every writer of playbackParameters, not just the dialog.
        //
        // Guarded on audioOffloadHint because it is only meaningful while the app is actually asking
        // for offload. With the hint false the mode is already AUDIO_OFFLOAD_MODE_DISABLED and the
        // selector short-circuits before it ever reads the flag, and the settings collector above
        // rebuilds the full request — reading these same playback parameters — whenever the gate
        // flips. Re-applying here anyway would only cost a pointless track reselection.
        if (audioOffloadHint) {
            player.setOffloadEnabled(true)
            secondaryPlayer?.setOffloadEnabled(true)
        }
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
        // Top-level code only. Walking the cause chain here was tried and reverted: the format-guard's own
        // CONTAINER_MALFORMED surfaces at top level as IO_UNSPECIFIED and would route to handleExpiredUrlError
        // instead of handleGenericIOError — but both do the same purge + re-prepare, so the only real effect
        // was moving a SimpleCache file-unlink onto the main looper inside onPlayerError (the exact cost
        // registry #74 flagged). No behaviour gained, a main-thread cost added.
        // CONTAINER_UNSUPPORTED (3003) + NoDeclaredBrand: googlevideo often returns HTML/empty when the
        // URL is dead or n-transform failed — not a playable container. Treat like a bad stream so we
        // drop the cached URL and re-resolve (owner log 0.6.162: Source error / extractors could not read).
        return error.errorCode == PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED ||
                error.errorCode == PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED ||
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

        // VIDEO MODE: if the failing item is the video track (e.g. its muxed URL expired / 403'd or decoder error),
        // drop the stale cached URL and fall back to AUDIO (exitVideoMode restores the normal source), so the
        // song keeps playing. Do not let video failures hit retry-limit/stopOnError.
        val isVideoFailure = _videoMode.value || (videoModeMediaId != null && mediaId == videoModeMediaId) ||
            (mediaId != null && videoModeItems.containsKey(mediaId))
        if (isVideoFailure) {
            if (mediaId != null) {
                videoUrlCache.remove(mediaId)
                resetRetryCount(mediaId)
            }
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

        // NO blanket cache purge here. This used to call performAggressiveCacheClear(mediaId)
        // UNCONDITIONALLY, before the error was even classified — and that is the most likely cause of
        // #57 ("las canciones se adelantan, cortan microsegundos y aparecen más adelante, de forma
        // random").
        //
        // The purge does playerCache.removeResource(mediaId): it DELETES the already-downloaded bytes of
        // the song that is playing RIGHT NOW. media3 then has to re-open the source, and the re-open
        // flushes the AudioTrack — position follows bytes WRITTEN to the sink, so everything written but
        // not yet heard is discarded and playback resumes AHEAD of where the user was listening. That is
        // exactly a micro-cut plus a small forward jump, on any transient error, at random.
        //
        // It was also redundant, and in the commonest case actively wrong:
        //  - handleRangeNotSatisfiableError, handlePageReloadError and handleGenericIOError already call
        //    performAggressiveCacheClear themselves, so those paths lose nothing;
        //  - handleExpiredUrlError (403 — the frequent one, and the corruption branch routes here too)
        //    deliberately clears ONLY songUrlCache + the decryption cache and KEEPS the bytes, because a
        //    stale URL says nothing about audio already downloaded. The blanket call overrode that
        //    intent and threw the good bytes away anyway;
        //  - the offline branch just waits for the network — the bytes are fine, the link is down;
        //  - isAudioRendererError is a problem with the SINK, not the source;
        //  - isNetworkRelatedError does a bounded retry, which the purge turned into a full re-download
        //    on every attempt.
        // Every branch that genuinely needs a purge performs its own, correctly scoped.


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
                // The cached BYTES are what is bad here (CONTAINER_MALFORMED / READ_POSITION_OUT_OF_RANGE),
                // so they must actually be deleted. This used to call handleExpiredUrlError, which only
                // drops songUrlCache and deliberately KEEPS the bytes — correct for a 403, wrong for
                // corruption. It only ever worked because the blanket purge above happened to delete them
                // first; with that gone, the resolver's "ghost cache entry — keeping cached bytes" path
                // would re-serve the SAME corrupt data on every retry, so a song that used to hiccup once
                // and heal would stutter through its 3 retries and get skipped — on every single play.
                val noDeclaredBrand = error.message?.contains("NoDeclaredBrand", ignoreCase = true) == true ||
                    generateSequence(error as Throwable?) { it.cause }
                        .any { it.message?.contains("NoDeclaredBrand", ignoreCase = true) == true }
                val attempts = if (mediaId != null) (currentMediaIdRetryCount[mediaId] ?: 0) + 1 else 0
                Timber.tag(TAG).i(
                    "CONTAINER_3003 id=${mediaId?.take(11)} attempts=$attempts noBrand=$noDeclaredBrand code=${error.errorCode}",
                )
                if (mediaId != null) {
                    performAggressiveCacheClear(mediaId)
                    // Stale FormatEntity can re-pin a dead container after HTML/empty googlevideo replies.
                    bypassCacheForQualityChange.add(mediaId)
                    scope.launch(Dispatchers.IO) {
                        runCatching { database.deleteFormat(mediaId) }
                    }
                }
                // After repeated NoDeclaredBrand (HTML/empty stream), stop retrying the same dead URL —
                // surface final failure / skip instead of looping "Volver a intentar".
                if (noDeclaredBrand && mediaId != null && hasExceededRetryLimit(mediaId)) {
                    Timber.tag(TAG).w("CONTAINER_3003 giving up for ${mediaId.take(11)} after NoDeclaredBrand retries")
                    markSongAsFailed(mediaId)
                    handleFinalFailure()
                    return
                }
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
                    // Drop the resolved URL — the comment above says this branch's own diagnosis is "a DEAD
                    // deciphered URL", and the retry goes through the resolver, which reuses songUrlCache
                    // while its TTL holds. Without this the bounded retry re-fetches the IDENTICAL dead URL
                    // three times and then skips a song that a fresh resolve would have played. The BYTES
                    // stay: a dead URL says nothing about audio already on disk.
                    songUrlCache.remove(mediaId)
                }
                Timber.tag(TAG).d("Connected but network-like error; bounded retry with a fresh URL")
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
        // High-Performance Mode defaults to audio-only, but if the user explicitly taps the Video toggle,
        // we should respect their intent and let them switch. We no longer block it here.
        if (_videoMode.value) {
            exitVideoMode()
        } else {
            enterVideoModeInternal(forceExplicit = true)
        }
    }

    /**
     * Force video mode ON for the current item (no-op if already on). Used when opening from
     * Vídeos exportados — playback should start in video; the user can still exit later.
     *
     * [forceFromUserTap] bypasses the High-Performance Mode block: a deliberate tap on a video
     * poster is an explicit user request and must always work, even on low-end devices.
     */
    fun enterVideoModeIfNeeded(forceFromUserTap: Boolean = false) {
        if (!forceFromUserTap &&
            iad1tya.echo.music.utils.PerformanceMode.isOn(this) &&
            !iad1tya.echo.music.utils.DeviceForm.isTvOrCar(this)
        ) {
            return
        }
        if (_videoMode.value) return
        enterVideoModeInternal(forceExplicit = forceFromUserTap)
    }

    private fun enterVideoModeInternal(forceExplicit: Boolean = false) {
        userExplicitlyExitedVideo = false
        userHasUsedVideo = true
        player.currentMediaItem?.mediaId?.let { resetRetryCount(it) }
        videoSwapMeasureStart()
        val gen = videoSwapGeneration.incrementAndGet()
        if (!tryInstantVideoSwap()) {
            teardownInstantVideoSwap("video mode on via normal path")
            _videoMode.value = true
            // Do NOT pause downloads until swapToVideo actually commits. Pausing here and then
            // early-outing (no video / resolve fail) left Exo downloads frozen and the player
            // download icon stuck with no tap response.
            applyVideoToCurrent(swapGeneration = gen, forceExplicit = forceExplicit)
        } else {
            pauseOfflineDownloadsForVideoPlayback()
        }
        val nextIdx = player.nextMediaItemIndex
        if (nextIdx != C.INDEX_UNSET) {
            runCatching { player.getMediaItemAt(nextIdx).mediaId }.getOrNull()
                ?.let { prebuildNextVideoItem(nextIdx, it) }
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

    private fun isVideoDownloadComplete(songId: String): Boolean {
        val vidKey = videoDownloadMediaId(songId)
        val cachedLength = androidx.media3.datasource.cache.ContentMetadata
            .getContentLength(downloadCache.getContentMetadata(vidKey))
        return cachedLength != C.LENGTH_UNSET.toLong() && cachedLength > 0 &&
            downloadCache.isCached(vidKey, 0, cachedLength)
    }

    /**
     * Local MP4 from AudioExportService (Vídeos exportados). Muxed A+V — play offline with
     * [swapToVideo] `isMuxed = true`, never YouTube resolve.
     */
    private fun exportedMuxedVideoUri(songId: String): String? {
        if (songId.isBlank()) return null
        val videoIds = dataStore.get(ExportedVideoIdsKey, "")
            .split(',')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        if (songId !in videoIds) return null
        val raw = runBlocking(Dispatchers.IO) {
            dataStore.data.first()[ExportedFileUrisKey].orEmpty()
        }
        val uri = parseExportedFileUriMap(raw)[songId] ?: return null
        return uri.takeIf { exportedFileUriExists(this, it) }
    }

    private fun currentEqPreampDb(): Double {
        val prefsPreamp = getSharedPreferences("echo_eq_prefs", Context.MODE_PRIVATE)
            .getFloat("preampDb", 0f).toDouble()
        if (!::eqProfileRepository.isInitialized) return prefsPreamp
        val profile = eqProfileRepository.unsavedProfile.value ?: eqProfileRepository.activeProfile.value
        return profile?.preamp ?: prefsPreamp
    }

    private fun safeVolumeAppliedGain(baseGain: Float): Float =
        safeVolumeGainWithEqPreamp(baseGain, currentEqPreampDb())

    private fun scheduleVideoStuckRecoveryCheck() {
        videoStuckRecoveryJob?.cancel()
        if (!_videoMode.value || _videoUrl.value.isNullOrEmpty()) return
        videoStuckRecoveryJob = scope.launch {
            // Idle-only recovery waits a beat for a transient IDLE→BUFFERING transition after a swap.
            delay(5_000)
            withContext(Dispatchers.Main) { maybeRecoverStuckVideo() }
        }
    }

    /**
     * Re-prepare once when video mode is on but the pipeline is dead ([Player.STATE_IDLE]) while the
     * user still wants playback. **Must not treat [Player.STATE_BUFFERING] as stuck** — normal video
     * rebuffers often last several seconds; calling `prepare()` there forced a full restart and looked
     * exactly like "se traba y al rato continúa" (0.6.161).
     */
    private fun maybeRecoverStuckVideo() {
        if (!_videoMode.value || _videoUrl.value.isNullOrEmpty()) return
        if (!player.playWhenReady) return
        val state = player.playbackState
        if (state != Player.STATE_IDLE) return
        val id = player.currentMediaItem?.mediaId ?: return
        if (id != videoModeMediaId) return
        val now = System.currentTimeMillis()
        val last = videoStuckRecoveryAttemptedAt[id] ?: 0L
        if (now - last < 30_000L) return
        videoStuckRecoveryAttemptedAt[id] = now
        Timber.tag(TAG).w("Video stuck in IDLE — re-preparing $id")
        val playing = player.playWhenReady
        player.prepare()
        player.playWhenReady = playing
    }

    /** Resolve the current track's muxed video URL and swap its source in-place (audio is never stopped). */
    private fun applyVideoToCurrent(
        armModeWhenReady: Boolean = false,
        swapGeneration: Int = videoSwapGeneration.get(),
        forceExplicit: Boolean = false,
    ) {
        val item = player.currentMediaItem ?: return
        val id = item.mediaId
        // Restore any OTHER tracked video items (the previous track, or a stale pre-built one) to audio;
        // the current id is about to be (re)swapped to video below.
        restoreVideoTracksExcept(id)

        // Video PODCAST episode: it already carries a direct video stream — swap to it immediately, no
        // YouTube resolution (the id here is an http audio URL, which YTPlayerUtils can't resolve anyway).
        val podcastVideo = player.currentMetadata?.podcastVideoUrl
        if (!podcastVideo.isNullOrEmpty()) {
            if (armModeWhenReady) {
                _videoMode.value = true
            }
            swapToVideo(id, podcastVideo, isMuxed = true)
            return
        }
        // Exported MP4 (Vídeos exportados): local muxed file — offline video without YT resolve.
        // Must run BEFORE the isVideoSong gate: library rows often lack that flag after export.
        val exportedVideo = exportedMuxedVideoUri(id)
        if (!exportedVideo.isNullOrEmpty()) {
            if (armModeWhenReady) {
                _videoMode.value = true
            } else if (!_videoMode.value) {
                return
            }
            swapToVideo(id, exportedVideo, isMuxed = true)
            return
        }
        // A direct/local track with no video stream (e.g. an audio-only podcast reached while sticky video
        // is still armed) can't show video — disarm video mode and play audio quietly (no failed-resolution
        // toast, and crucially no stuck spinner: leaving _videoMode=true here would show an endless spinner
        // over the cover with no video and no on-screen toggle to exit). Mirrors the no-video YouTube path.
        if (id.startsWith("http", ignoreCase = true) || id.isLocalMediaId()) {
            disarmVideoModeKeepAudio()
            return
        }
        // A YouTube track that is NOT a video song can't show video → disarm video mode SILENTLY (no
        // resolution attempt, no "Video falló" toast) and keep playing audio. This is the sticky-video case
        // where the next track has no video: we drop to audio cleanly instead of erroring.
        if (!forceExplicit && player.currentMetadata?.isVideoSong != true) {
            disarmVideoModeKeepAudio()
            return
        }

        val offlineOnly = dataStore.get(OfflineModeKey, false)
        val videoDownloaded = isVideoDownloadComplete(id)
        if (offlineOnly || videoDownloaded) {
            if (!videoDownloaded) {
                if (!armModeWhenReady && _videoMode.value) {
                    Toast.makeText(
                        this,
                        getString(R.string.error_offline_not_downloaded),
                        Toast.LENGTH_SHORT,
                    ).show()
                }
                disarmVideoModeKeepAudio()
                return
            }
            if (armModeWhenReady) {
                _videoMode.value = true
            } else if (!_videoMode.value) return
            swapToVideo(id, offlineVideoCacheUri(id))
            return
        }
        if (offlineOnly) return

        val cached = videoUrlCache[id]?.takeIf { it.second > System.currentTimeMillis() }?.first
        if (!cached.isNullOrEmpty()) {
            videoSwapMark("applyVideoToCurrent: URL cache HIT")
            if (armModeWhenReady) {
                _videoMode.value = true
            } else if (!_videoMode.value) return
            swapToVideo(id, cached)
            return
        }
        videoSwapMark("applyVideoToCurrent: URL cache MISS → live resolve")
        // Keep playing audio during resolve when arming lazily; spinner only if already in video mode.
        if (!armModeWhenReady) {
            _videoUrl.value = null  // spinner while resolving
        }
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
                if (videoSwapGeneration.get() != swapGeneration) return@withContext
                if (player.currentMediaItem?.mediaId != id) return@withContext
                if (url.isNullOrEmpty()) {
                    if (!armModeWhenReady) {
                        disarmVideoModeKeepAudio()
                        val ex = result.exceptionOrNull()
                        val reason = ex?.let { "${it.javaClass.simpleName}: ${it.message}" } ?: "sin formato de video"
                        Toast.makeText(this@MusicService, "Video falló — $reason", Toast.LENGTH_LONG).show()
                    }
                    return@withContext
                }
                videoUrlCache[id] = url to (System.currentTimeMillis() + 5 * 60 * 1000L)
                if (armModeWhenReady) {
                    _videoMode.value = true
                } else if (!_videoMode.value) {
                    return@withContext
                }
                swapToVideo(id, url)
            }
        }
    }

    /** Drop video chrome and keep audio. Safe to call when downloads were never paused. */
    private fun disarmVideoModeKeepAudio() {
        _videoMode.value = false
        _videoUrl.value = null
        resumeOfflineDownloadsAfterVideoPlayback()
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
            ?.takeUnless { it.contains("googlevideo.com", ignoreCase = true) }
            ?: preloadedVideoOriginalUris.remove(id)
                ?.takeUnless { it.contains("googlevideo.com", ignoreCase = true) }
            ?: item.localConfiguration?.uri?.toString()
                ?.takeUnless { it.contains("googlevideo.com", ignoreCase = true) }
            ?: id
        videoModeOriginalUri = origUri
        videoModeMediaId = id
        // Podcast video is a single muxed stream (has audio) → don't merge a 2nd audio; YouTube is video-only.
        videoModeIsMuxedPodcast = isMuxed
        // Register in the shared map so createMediaSource builds this item's video+audio source (the map, not
        // the single id, is now authoritative there).
        videoModeItems[id] = VideoTrackState(url, origUri, isMuxed)

        val playing = player.playWhenReady
        val sameUri = item.localConfiguration?.uri?.toString() == url

        // Muxed local/export: audio path often already points at this same file via ResolvingDataSource.
        // Early-return without rebuild left videoModeItems set but the player still on the audio factory
        // → surface stayed black / toggle looked like "needs internet". Always rebuild for muxed.
        if (sameUri && !isMuxed) {
            _videoUrl.value = url
            if (playing) player.playWhenReady = true
            // Only re-prepare from IDLE. Calling prepare() while BUFFERING restarts the pipeline and
            // looks like "se traba / buffering y al rato sigue" (same class of bug as maybeRecoverStuckVideo).
            if (playing && player.playbackState == Player.STATE_IDLE) {
                player.prepare()
                player.playWhenReady = true
            }
            pauseOfflineDownloadsForVideoPlayback()
            scheduleVideoStuckRecoveryCheck()
            return
        }

        val pos = player.currentPosition
        // Tag forces MediaItem inequality when URI is unchanged (muxed export already playing as audio).
        val videoItem = item.buildUpon()
            .setUri(url)
            .setTag(if (isMuxed) "muxed-video-$id" else item.localConfiguration?.tag)
            .build()
        player.replaceMediaItem(idx, videoItem)
        // Video swap: seek keyframe-aligned (CLOSEST_SYNC) so the first video frame decodes sooner — an EXACT
        // seek must decode every frame from the previous keyframe up to pos before it can show anything.
        // Restored to DEFAULT (EXACT) immediately so ONLY this swap seek is keyframe-aligned; all audio seeks
        // stay exact. In practice capable-only: video mode is force-off in High-Performance Mode. Audio-only
        // playback never reaches swapToVideo, so the audio path is byte-identical.
        player.setSeekParameters(androidx.media3.exoplayer.SeekParameters.CLOSEST_SYNC)
        player.seekTo(idx, pos)
        player.setSeekParameters(androidx.media3.exoplayer.SeekParameters.DEFAULT)
        // Muxed same-URI rebuild must prepare even from READY so ProgressiveMediaSource takes over.
        // YouTube (different URI, same tag/mediaId) also requires prepare() so ExoPlayer actually
        // recreates the MediaPeriod instead of ignoring the URI change on the active window.
        player.prepare()
        _videoUrl.value = url
        pauseOfflineDownloadsForVideoPlayback()
        scheduleVideoStuckRecoveryCheck()
        // Keep playWhenReady as the user left it. A previous "wait for 2.5s buffered" gate set
        // playWhenReady=false, which (a) stopped playback for several seconds on every enter and
        // (b) was then captured by exitVideoMode as "user paused" so the next enter never resumed
        // and the cover overlay never received onRenderedFirstFrame.
        if (playing) {
            player.playWhenReady = true
        }
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
            // YouTube audio items use the mediaId as URI. If we lost originalAudioUri (rapid
            // toggles storing the video URL as "original"), fall back to the id so restore still
            // leaves a resolvable audio source instead of a googlevideo URI with videoMode off.
            val origUri = state.originalAudioUri
                ?.takeUnless { it.contains("googlevideo.com", ignoreCase = true) }
                ?: vid.takeUnless { it.startsWith("http", ignoreCase = true) || it.isLocalMediaId() }
                ?: continue
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
        // Local exported MP4 — no network; register as muxed before the YT path.
        val exportedNext = exportedMuxedVideoUri(nextId)
        if (!exportedNext.isNullOrEmpty()) {
            val item = runCatching { player.getMediaItemAt(nextIdx) }.getOrNull() ?: return
            if (item.mediaId != nextId) return
            val origUri = item.localConfiguration?.uri?.toString()
            videoModeItems[nextId] = VideoTrackState(exportedNext, origUri, true)
            if (origUri != exportedNext) {
                player.replaceMediaItem(
                    nextIdx,
                    item.buildUpon().setUri(exportedNext).setTag("muxed-video-$nextId").build(),
                )
            }
            return
        }
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
     * Speculatively resolve the CURRENT track's video URL into [videoUrlCache] so the first toggle is instant.
     * NEVER touches the player/audio graph — a failed resolve is swallowed and the toggle still falls back to
     * the live resolve exactly as today. Gated to avoid rate-limit / mid-song stutter:
     *   - video mode currently OFF
     *   - at most 8 speculative resolves per session (ALWAYS — never uncapped after first video use)
     *   - before first video use: capable devices only (not High-Performance / LOW / ULTRA)
     *   - skip when audio stream resolve or cipher/PoToken is busy (mutex contention cuts audio)
     *   - skip when [ThermalManager.isHot]
     *   - a genuine YouTube VIDEO song (isVideoSong == true)
     */
    private fun prefetchCurrentVideoUrl() {
        // A toggle-to-video is only possible from audio; when already in video mode the swap has run.
        if (_videoMode.value) return
        // DATA SAVER: no speculative video-URL resolves — the toggle resolves on demand instead.
        if (dataSaverEnabled) return
        // Heat: never start speculative cipher work on a thermally throttled device.
        if (iad1tya.echo.music.utils.ThermalManager.isHot.value) return
        // Do not contend with the live audio resolve (loader thread) for shared WebView mutexes.
        if (audioStreamResolveInFlight.get() > 0 || YTPlayerUtils.isStreamResolveBusy) return
        // Cheap in-memory checks FIRST: bail on a non-video / local / direct-URL track BEFORE paying for the
        // PerformanceMode reads in the first-toggle gate below (those only matter for a genuine video song).
        val id = player.currentMediaItem?.mediaId ?: return
        if (id.isEmpty() || id.isLocalMediaId() || id.startsWith("http", ignoreCase = true)) return
        if (player.currentMetadata?.isVideoSong != true) return
        // ALWAYS cap speculative resolves for the session (audio-only listeners + post-first-use alike).
        if (speculativeVideoPrefetches >= 8) return
        // Before the user has opened video once: only capable devices pay the speculative cipher cost.
        if (!userHasUsedVideo) {
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
        speculativeVideoPrefetches++
        scope.launch(Dispatchers.IO) {
            try {
                // Bail if audio resolve grabbed the locks while we were queued.
                if (audioStreamResolveInFlight.get() > 0 || YTPlayerUtils.isStreamResolveBusy) return@launch
                if (iad1tya.echo.music.utils.ThermalManager.isHot.value) return@launch
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
        userExplicitlyExitedVideo = true
        videoSwapGeneration.incrementAndGet()
        videoStuckRecoveryJob?.cancel()
        teardownInstantVideoSwap("exit video mode")
        val leavingId = player.currentMediaItem?.mediaId
        leavingId?.let { resetRetryCount(it) }
        _videoMode.value = false
        _videoUrl.value = null
        prebuildingIds.clear()
        restoreVideoTracksExcept(null)   // restore ALL tracked video items (current + any pre-built) to audio
        // Resume offline pipeline + flush any download deferred while watching.
        resumeOfflineDownloadsAfterVideoPlayback()
        runCatching { flushAllPendingSongDownloads(this) }
            .onFailure { Timber.tag(TAG).w(it, "flush all pending song downloads failed") }
        // Re-arm the instant-swap pre-prepare (fully re-gated inside) so toggling video back on soon after
        // is instant again; delayed so it never competes with the audio restore's own re-prepare.
        if (playerSheetExpanded) scheduleInstantVideoPrepare(INSTANT_VIDEO_PREPARE_DELAY_MS)
    }

    /**
     * Offline Exo downloads share the same network as the live video+audio merge. Pause them for the
     * whole video session so they cannot 403/bandwidth-fight the playing mux (owner hitch reports).
     */
    private fun pauseOfflineDownloadsForVideoPlayback() {
        runCatching {
            DownloadService.sendPauseDownloads(this, ExoDownloadService::class.java, false)
        }.onFailure { Timber.tag(TAG).w(it, "pause downloads for video failed") }
    }

    private fun resumeOfflineDownloadsAfterVideoPlayback() {
        runCatching {
            DownloadService.sendResumeDownloads(this, ExoDownloadService::class.java, false)
        }.onFailure { Timber.tag(TAG).w(it, "resume downloads after video failed") }
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
                // Offline mode: direct URLs still need the network — refuse them.
                if (dataStore.get(OfflineModeKey, false)) {
                    throw PlaybackException(
                        getString(R.string.error_offline_not_downloaded),
                        null,
                        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
                    )
                }
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
                    // Download-served: no background network (duration / related prefetch) — the user
                    // downloaded this exactly to avoid data use.
                    scope.launch(Dispatchers.IO) { recoverSong(mediaId, isOfflinePlayback = true) }
                    return@Factory dataSpec
                }
            }

            // Exported SAF/file URI (AudioExportService) — playable with zero network, same as a download.
            val exportedUri = runBlocking(Dispatchers.IO) {
                val raw = dataStore.data.first()[ExportedFileUrisKey].orEmpty()
                parseExportedFileUriMap(raw)[mediaId]
            }
            if (!exportedUri.isNullOrBlank() && exportedFileUriExists(this, exportedUri)) {
                scope.launch(Dispatchers.IO) { recoverSong(mediaId, isOfflinePlayback = true) }
                return@Factory dataSpec.withUri(exportedUri.toUri())
            }

            // Strict offline: ONLY a full downloadCache hit OR a valid exported URI may play.
            // playerCache / songUrlCache / YT resolve all need the network — refuse them while OfflineModeKey is ON.
            if (dataStore.get(OfflineModeKey, false)) {
                throw PlaybackException(
                    getString(R.string.error_offline_not_downloaded),
                    null,
                    PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
                )
            }

            // Read Room NOW — BEFORE serving any playerCache/songUrlCache hit — for the container-mismatch guard
            // below, which decides whether the CACHED BYTES may be served or must be bypassed+refetched.
            // Timed (slow-start telemetry): this runBlocking sits on the loader thread ahead of every
            // resolve, so its cost is folded into the RESOLVE_TIMING db= stage.
            val dbFormatReadStartMs = android.os.SystemClock.elapsedRealtime()
            val dbFormat = runBlocking(Dispatchers.IO) { database.format(mediaId).firstOrNull() }
            val dbFormatReadMs = android.os.SystemClock.elapsedRealtime() - dbFormatReadStartMs

            // refetchCurrentInOpus() forces this track to Opus, overriding both the global quality and the
            // "locked" quality of the currently-playing track (below).
            val forceOpus = forceOpusForMediaId == mediaId
            // Mid-song container lock, scoped to the SESSION — deliberately read from songUrlCache, NOT from the
            // persisted FormatEntity. dbFormat is a DB row that outlives the process, so pinning to it made the
            // lock permanent: a song first played at OPUS re-pinned to OPUS on every later replay, forever, and
            // re-upserted the opus row — the container guard below could never fire against it either, since it
            // was derived from the very row it compares. A live cache entry instead means "this session already
            // resolved this URL", which is exactly the mid-song case worth protecting: the quality collector
            // preserves ONLY the playing track's entry when a quality change clears the map, so an in-flight track
            // keeps its container while every replay finds no entry → falls through to the global quality.
            // DELIVERED, not requested: this value is compared against dbFormat by the guard below, and dbFormat
            // describes what was actually served.
            val cachedQuality = songUrlCache[mediaId]?.delivered
            val lockedQuality = when {
                forceOpus -> iad1tya.echo.music.constants.AudioQuality.OPUS
                isCurrentlyPlaying && cachedQuality != null -> cachedQuality
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
                    // Partial download served from downloadCache: same offline intent as the full hit above.
                    scope.launch(Dispatchers.IO) { recoverSong(mediaId, isOfflinePlayback = true) }
                    return@Factory dataSpec
                }

                if (playerCache.isCached(mediaId, dataSpec.position, CHUNK_LENGTH)) {
                    songUrlCache[mediaId]?.takeIf { it.expiresAt > System.currentTimeMillis() }?.let {
                        scope.launch(Dispatchers.IO) { recoverSong(mediaId) }
                        return@Factory dataSpec.withUri(it.url.toUri())
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

                songUrlCache[mediaId]?.takeIf { it.expiresAt > System.currentTimeMillis() }?.let {
                    scope.launch(Dispatchers.IO) { recoverSong(mediaId) }
                    return@Factory dataSpec.withUri(it.url.toUri())
                }
            }

            if (shouldBypassCache) {
                Timber.tag("MusicService").i("BYPASSING CACHE for $mediaId due to quality change")
            }

            Timber.tag("MusicService").i("FETCHING STREAM: $mediaId | quality=$lockedQuality")
            val playbackData = try {
                audioStreamResolveInFlight.incrementAndGet()
                runBlocking(Dispatchers.IO) {
                    val dbSongReadStartMs = android.os.SystemClock.elapsedRealtime()
                    val dbSong = database.song(mediaId).firstOrNull()
                    val knownArtist = dbSong?.artists?.joinToString { it.name }?.replace(" - Topic", "")
                    val knownTitle = dbSong?.song?.title
                    val knownDuration = dbSong?.song?.duration?.let { if (it > 0) it * 1000L else null }
                    // Both loader-thread Room reads (format above + song here) reported as RESOLVE_TIMING db=.
                    val preResolveDbMs = dbFormatReadMs + (android.os.SystemClock.elapsedRealtime() - dbSongReadStartMs)

                    YTPlayerUtils.playerResponseForPlayback(
                        mediaId,
                        audioQuality = lockedQuality,
                        connectivityManager = connectivityManager,
                        context = this@MusicService,
                        knownArtist = knownArtist,
                        knownTitle = knownTitle,
                        knownDurationMs = knownDuration,
                        preResolveDbMs = preResolveDbMs
                    )
                }
            } finally {
                audioStreamResolveInFlight.decrementAndGet()
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

                        // Don't throw when this is the FIRST open of a fresh period (position 0). There the
                        // extractor has not been sniffed yet, so it re-sniffs these very bytes and handles
                        // whatever container arrives — the throw is pure harm: a gratuitous fatal error plus
                        // a re-prepare (one audible cut) at the START of a LOSSLESS/SAAVN track whose fuzzy
                        // Qobuz/Saavn lookup (a 9s-timeout external search — routine, and nondeterministic
                        // per attempt) landed on a different container than the persisted row.
                        //
                        // From the media3 1.10.1 bytecode: BundledExtractorsAdapter.init sniffs the extractor
                        // ONCE and caches it for the period's life; ProgressiveMediaPeriod.startLoading gates
                        // setLoadPosition on `prepared`, so a fresh period's first open is position 0. The
                        // implication is one-way: position 0 ⇒ (almost always) not-yet-committed. It is NOT a
                        // biconditional — a prepared, unknown-length/unseekable period can re-open at 0 via
                        // configureRetry(0,0); that needs !isLengthKnown, unreachable for these
                        // Content-Length'd streams, and if it ever hit, the mismatched bytes reach the
                        // committed extractor which raises ParserException → the same fatal recovery we
                        // produce today. So this only ever degrades to current behaviour, never worse.
                        //
                        // HONEST SCOPE (registry #57 stays OPEN): a MID-SONG re-resolve always re-opens at a
                        // NONZERO offset, so the throw below still fires for every mid-song container flip —
                        // exactly the "corta microsegundos y aparece más adelante" case. This change only
                        // removes the cut at track START (the explicitly-tapped-song residue). On OPUS the
                        // whole fallback roulette is skipped so neither can fire — the testable prediction.
                        //
                        // Falling through instead still repairs everything: playerCache.removeResource above
                        // already dropped the stale-container bytes, and execution reaches the FormatEntity
                        // upsert below, which corrects the row. bypassCacheForQualityChange is unnecessary
                        // here — the loop it guards against only existed because the throw pre-empted that
                        // upsert.
                        if (isCurrentlyPlaying && dataSpec.position != 0L) {
                            // Registry #57: do NOT throw mid-song — that forced an audible cut/restart.
                            // Cache bytes were already purged above; falling through upserts the corrected
                            // FormatEntity so the next open uses the right container without a hard restart.
                            bypassCacheForQualityChange.add(mediaId)
                            Timber.tag(TAG).w(
                                "Format changed mid-stream for $mediaId — purged cache and continuing without restart",
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

                // Prime Safe Volume from THIS same player-response (no extra network, no Room wait)
                // BEFORE open() returns, so the first decoded sample is already at the locked level.
                lockLoudnessIfCurrent(mediaId, loudnessDb, perceptualLoudnessDb, measuredLoudnessDb)

                database.query {
                    upsert(
                        FormatEntity(
                            id = mediaId,
                            itag = format.itag,
                            mimeType = format.mimeType.split(";")[0],
                            // Derive the codec safely. split("codecs=")[1] threw IndexOutOfBounds for a
                            // mimeType with no codecs parameter; and an empty codec reads back as OPUS, which
                            // makes the format guard mis-fire on EVERY open for a LOSSLESS/SAAVN user — the
                            // #57 mechanism. Fall back to the container, then to the row we already have.
                            codecs = codecsFromMimeType(format.mimeType, dbFormat?.codecs),
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

                // Stamp the quality that was DELIVERED, derived from the response with the SAME predicate the
                // container guard uses (isFinalLossless/isFinalSaavn) — NEVER `lockedQuality`, which is only what
                // we ASKED for. Fallback is routine (LOSSLESS -> Qobuz fails -> Saavn fails -> Opus), so stamping
                // the request would make this entry disagree with the FormatEntity describing the same stream:
                // the guard would then see a container mismatch for the very track that is playing, purge its
                // cached bytes and re-resolve on every re-open — #28 all over again, on a loop.
                val deliveredQuality = when {
                    isFinalLossless -> iad1tya.echo.music.constants.AudioQuality.LOSSLESS
                    isFinalSaavn -> iad1tya.echo.music.constants.AudioQuality.SAAVN
                    else -> iad1tya.echo.music.constants.AudioQuality.OPUS
                }
                songUrlCache[mediaId] = CachedStream(
                    url = streamUrl,
                    expiresAt = System.currentTimeMillis() + (nonNullPlayback.streamExpiresInSeconds * 1000L),
                    delivered = deliveredQuality,
                    // NOT `lockedQuality`: its middle branch IS the cached DELIVERED value, so stamping it here
                    // would feed a delivered value straight back into the field whose only reader asks "what did
                    // the user ASK for?" — re-overloading the field this type exists to split. `forceOpus` is
                    // genuinely the request for this track (and dropping it at cold start is right: a per-session
                    // refetch must not survive a restart); otherwise the request is the global quality.
                    requested = if (forceOpus) iad1tya.echo.music.constants.AudioQuality.OPUS else audioQuality,
                )
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
                    c.startsWith("TV", true) -> com.music.innertube.models.YouTubeClient.TVHTML5.userAgent
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
        ResolvingDataSource.Factory(
            DefaultDataSource.Factory(
                this,
                createCacheDataSource().apply {
                    setUpstreamDataSourceFactory(
                        ChunkingDataSourceFactory(
                            androidx.media3.datasource.okhttp.OkHttpDataSource.Factory(videoOkHttpClient),
                        ),
                    )
                },
            ),
        ) { dataSpec ->
            songIdFromOfflineVideoCacheUri(dataSpec.uri.toString())?.let { songId ->
                val vidKey = videoDownloadMediaId(songId)
                val cachedLength = androidx.media3.datasource.cache.ContentMetadata
                    .getContentLength(downloadCache.getContentMetadata(vidKey))
                val fullyDownloaded = cachedLength != C.LENGTH_UNSET.toLong() && cachedLength > 0 &&
                    downloadCache.isCached(vidKey, 0, cachedLength)
                if (fullyDownloaded) {
                    return@Factory dataSpec.buildUpon().setKey(vidKey).setUri(vidKey.toUri()).build()
                }
                throw PlaybackException(
                    getString(R.string.error_offline_not_downloaded),
                    null,
                    PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
                )
            }
            dataSpec
        }
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
        // DATA SAVER: no speculative connection warm-up (it transfers real bytes).
        if (dataSaverEnabled) return
        if (_videoMode.value) return
        if (isCrossfading) return
        if (!playerInitialized.value) return
        // Screen off / thermal: no speculative CDN bytes while invisible or hot.
        if ((getSystemService(POWER_SERVICE) as? android.os.PowerManager)?.isInteractive == false) return
        if (iad1tya.echo.music.utils.ThermalManager.isHot.value) return
        if (audioStreamResolveInFlight.get() > 0 || YTPlayerUtils.isStreamResolveBusy) return
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
                // Pull a small init segment ahead of the toggle so swapToVideo's first prepare
                // already has TCP + CDN edge hot. Still BOUNDED (never body.bytes()).
                val request = okhttp3.Request.Builder()
                    .url(url)
                    .header("Range", "bytes=0-${VIDEO_WARM_BYTES - 1}")
                    .build()
                videoOkHttpClient.newCall(request).execute().use { response ->
                    response.body.source().let { source ->
                        val blackhole = okio.Buffer()
                        var drained = 0L
                        while (drained < VIDEO_WARM_BYTES) {
                            val read = source.read(blackhole, 8192)
                            if (read == -1L) break
                            drained += read
                            blackhole.clear()
                        }
                    }
                }
                Timber.tag(TAG).d("Video connection warmed for $id (${VIDEO_WARM_BYTES}B)")
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
        // DATA SAVER: no speculative pre-prepare (it buffers ~video-bitrate data ahead of any toggle).
        if (dataSaverEnabled) return
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
            // Full gain (attenuation x makeup) — must match what the main path applied for this same track,
            // or the published player starts at a different level than the one it replaces.
            if (safeVolumeEnabledHint) {
                playerEqProcessors[pre]?.applySafeVolume(
                    true,
                    safeVolumeAppliedGain(lastAppliedGain * lastAppliedMakeup),
                )
            }
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
     * [_playerFlow] (the UI re-attaches the TextureView to the new player), move the MediaSession,
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
            playerEqProcessors[pre]?.applySafeVolume(
                safeVolumeEnabledHint,
                if (safeVolumeEnabledHint) safeVolumeAppliedGain(lastAppliedGain * lastAppliedMakeup) else 1f,
            )

            // ---- COMMIT: publish (mirrors performCrossfadeSwap's swap block) ----
            committed = true
            instantVideoPlayer = null
            instantVideoPlayerId = null
            instantVideoPlayerUrl = null
            instantVideoPreparedAtPosMs = 0L
            old.removeListener(this)
            pre.removeListener(instantVideoPlayerListener)
            pre.addListener(this)
            player = pre
            _playerFlow.value = pre // UI (PlayerVideoSurface/MiniPlayer/PiP) re-attaches the surface here
            currentEqProcessor = playerEqProcessors[pre]
            try {
                (mediaSession as MediaSession).player = pre
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "Instant-video swap: failed to swap player in MediaSession")
            }
            pre.volume = if (isMuted.value) 0f else playerVolume.value
            pre.playWhenReady = playing
            _videoMode.value = true
            pauseOfflineDownloadsForVideoPlayback()
            _videoUrl.value = url
            // Old player: silence, detach its surface, full release (mirrors cleanupCrossfade's teardown).
            // NO clearMediaItems: redundant before release() and races media3 transition eval (CRASH_REPORTS #2/#5).
            runCatching {
                old.volume = 0f
                old.stop()
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
            // NO clearMediaItems: redundant before release() and races media3 transition eval (CRASH_REPORTS #2/#5).
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
                val delegateSink = DefaultAudioSink
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
                // SINK-LEVEL silence tap: DefaultAudioSink routes the hi-res FLOAT pipeline AROUND the
                // custom processor chain (bytecode-verified), so on 24-bit content the silence detector was
                // never fed — long silent tails on the owner's Lossless path kept producing dead gaps. This
                // wrapper measures the sink's INPUT (any PCM: 16-bit or float) before delegating.
                // Measure-only on a DUPLICATE buffer (independent position/order — the original is never
                // touched); the processor itself no-ops when the in-chain path already feeds it. handleBuffer
                // is re-called with the SAME buffer until consumed, so only the not-yet-measured region is
                // fed (identity + high-water mark) — re-measuring would inflate the silence counters.
                // HI-RES DSP RESCUE (same root cause, second half): the float branch skips the chain for the
                // WHOLE chain, not just the silence detector — so on hi-res the 10-band EQ, its preamp, the
                // de-esser, Safe Volume and the -1 dBFS limiter (all inside CustomEqualizerAudioProcessor)
                // never ran either, while the EQ screen kept drawing its curve. Verified from the media3
                // 1.10.1 bytecode: DefaultAudioSink.configure builds its pipeline as
                //   addAll(availableAudioProcessors)                     // [trimming, channelMapping]
                //   if (shouldUseFloatOutput(pcmEncoding)) { add(toFloatPcm); goto BUILD }   // <-- skips below
                //   add(toInt16Pcm); add(audioProcessorChain.getAudioProcessors())           // ONLY call site
                // and shouldUseFloatOutput = enableFloatOutput && Util.isEncodingHighResolutionPcm(enc)
                // (24-bit / 32-bit / float, either endianness). So when the sink is about to take that branch
                // we run the chain HERE instead, in 32-bit float — which is Superpowered's native domain
                // (SuperpoweredBridge.processAudio's encoding==4 path skips the short<->float conversions the
                // 16-bit path pays) — and hand the sink float, exactly what its own toFloatPcm would have
                // produced. Nothing is downsampled: 24-bit ints are exact in a float32 mantissa.
                object : androidx.media3.exoplayer.audio.ForwardingAudioSink(delegateSink) {
                    private var tapEncoding = C.ENCODING_INVALID
                    private var tapSampleRate = 0
                    private var tapChannels = 0
                    private var tapLastBuffer: java.nio.ByteBuffer? = null
                    private var tapMeasuredEnd = -1

                    /** Non-null only while the delegate would take its float branch: the custom chain,
                     *  driven from here because the sink refuses to. Null = the sink's own int16 pipeline
                     *  owns the chain (unchanged, proven path) or this is a passthrough/offload stream. */
                    private var hiResDsp: androidx.media3.common.audio.AudioProcessingPipeline? = null
                    private var hiResPtUs = 0L
                    private var hiResAccessUnits = 0
                    private var hiResEosQueued = false

                    override fun configure(
                        inputFormat: androidx.media3.common.Format,
                        specifiedBufferSize: Int,
                        outputChannels: IntArray?,
                    ) {
                        tapEncoding = inputFormat.pcmEncoding
                        tapSampleRate = inputFormat.sampleRate
                        tapChannels = inputFormat.channelCount
                        tapLastBuffer = null
                        tapMeasuredEnd = -1

                        // Mirror of DefaultAudioSink.configure's own decision. Guards, in order:
                        //  - !lowEnd            : matches setEnableFloatOutput(!lowEnd) above.
                        //  - AUDIO_RAW          : the sink only builds a processor pipeline for raw PCM;
                        //                         encoded passthrough/offload has no chain at all.
                        //  - highResolutionPcm  : the exact predicate that makes the sink skip the chain.
                        //  - outputChannels null: a channel map keeps ChannelMappingAudioProcessor active,
                        //                         which is 16-bit-only — leave that (already broken upstream
                        //                         on float) exactly as it behaves today.
                        val delegateWouldSkipChain = !lowEnd &&
                            androidx.media3.common.MimeTypes.AUDIO_RAW == inputFormat.sampleMimeType &&
                            androidx.media3.common.util.Util.isEncodingHighResolutionPcm(inputFormat.pcmEncoding) &&
                            outputChannels == null
                        var delegateFormat = inputFormat
                        var pipeline: androidx.media3.common.audio.AudioProcessingPipeline? = null
                        if (delegateWouldSkipChain) {
                            // ToFloatPcmAudioProcessor is media3's own converter and self-bypasses when the
                            // input is already float, so a float decoder output costs zero extra work.
                            // normProcessor/limiterProcessor are deliberately NOT here: both are inert stubs
                            // (isActive() == false) that an AudioProcessingPipeline would drop anyway.
                            val built = androidx.media3.common.audio.AudioProcessingPipeline(
                                com.google.common.collect.ImmutableList.of(
                                    androidx.media3.exoplayer.audio.ToFloatPcmAudioProcessor(),
                                    eqProcessor,
                                ),
                            )
                            val outFormat = try {
                                built.configure(
                                    androidx.media3.common.audio.AudioProcessor.AudioFormat(inputFormat),
                                )
                            } catch (e: androidx.media3.common.audio.AudioProcessor.UnhandledAudioFormatException) {
                                throw androidx.media3.exoplayer.audio.AudioSink.ConfigurationException(e, inputFormat)
                            }
                            // Only take over when the chain is provably rate/channel/format preserving —
                            // that invariant is what lets handleBuffer forward presentationTimeUs unchanged
                            // and skip any position/latency correction.
                            if (outFormat.encoding == C.ENCODING_PCM_FLOAT &&
                                outFormat.sampleRate == inputFormat.sampleRate &&
                                outFormat.channelCount == inputFormat.channelCount
                            ) {
                                built.flush() // activates the processors; the pipeline is inert until this
                                pipeline = built
                                delegateFormat = inputFormat.buildUpon()
                                    .setPcmEncoding(C.ENCODING_PCM_FLOAT)
                                    .build()
                            } else {
                                // Defensive: leave the shared EQ processor in a clean state and fall back to
                                // today's behaviour rather than feeding the sink something it didn't expect.
                                built.reset()
                            }
                        }
                        hiResDsp = pipeline
                        hiResEosQueued = false
                        super.configure(delegateFormat, specifiedBufferSize, outputChannels)
                        logAudioPath(inputFormat, delegateFormat, pipeline != null)
                    }

                    override fun handleBuffer(
                        buffer: java.nio.ByteBuffer,
                        presentationTimeUs: Long,
                        encodedAccessUnitCount: Int,
                    ): Boolean {
                        runCatching {
                            val start = buffer.position()
                            val end = buffer.limit()
                            val fromPos = if (buffer === tapLastBuffer && tapMeasuredEnd in (start + 1)..end) {
                                tapMeasuredEnd // same buffer re-offered: only the region beyond the mark is new
                            } else {
                                start
                            }
                            if (end > fromPos) {
                                // ALLOCATION GATE (audio thread): duplicate() ran for EVERY buffer of
                                // every song even when measureExternal's own first two lines would
                                // return immediately — which is the normal 16-bit case, where this
                                // processor is already inside the sink's chain. Same two conditions,
                                // checked before the allocation instead of after it; no measurement
                                // changes, and the high-water bookkeeping below is untouched so a
                                // later latch still measures from exactly where it would have.
                                if (silenceProcessor.needsExternalMeasure()) {
                                    val dup = buffer.duplicate()
                                    dup.position(fromPos)
                                    dup.limit(end)
                                    silenceProcessor.measureExternal(dup, tapEncoding, tapSampleRate, tapChannels)
                                }
                                tapLastBuffer = buffer
                                tapMeasuredEnd = end
                            }
                        }
                        val dsp = hiResDsp
                            ?: return super.handleBuffer(buffer, presentationTimeUs, encodedAccessUnitCount)
                        // Same drain-then-feed loop DefaultAudioSink runs for its own pipeline. The delegate
                        // is handed the pipeline's OUTPUT buffer (a stable instance until drained, which its
                        // `buffer == inputBuffer` assertion requires) and the timestamp of the input that
                        // produced it — the chain is 1:1 and zero-latency, so that timestamp is exact.
                        while (true) {
                            val pendingOutput = dsp.output
                            if (pendingOutput.hasRemaining()) {
                                if (!super.handleBuffer(pendingOutput, hiResPtUs, hiResAccessUnits)) return false
                                continue
                            }
                            if (!buffer.hasRemaining()) return true
                            val remainingBefore = buffer.remaining()
                            hiResPtUs = presentationTimeUs
                            hiResAccessUnits = encodedAccessUnitCount
                            dsp.queueInput(buffer)
                            // Took nothing and produced nothing: report "not consumed" so the renderer
                            // re-offers the same buffer next cycle instead of spinning on the audio thread.
                            if (buffer.remaining() == remainingBefore) return false
                        }
                    }

                    override fun playToEndOfStream() {
                        // FLOAT pipeline never calls the processor's queueEndOfStream (it isn't in the
                        // chain) — mirror the EOS trailing-silence snapshot from here. Idempotent.
                        runCatching { silenceProcessor.markEndOfStreamExternal() }
                        val dsp = hiResDsp
                        if (dsp != null && dsp.isOperational) {
                            if (!hiResEosQueued) {
                                dsp.queueEndOfStream()
                                hiResEosQueued = true
                            }
                            // Drain before the delegate latches end-of-stream. The chain has no tail, so this
                            // is normally a no-op; if the delegate is full we return and get called again.
                            // InitializationException is NOT declared by playToEndOfStream and the renderer
                            // does not catch it here — abandoning the (empty) drain is the only safe answer;
                            // the sink raises its own error on the next cycle.
                            try {
                                while (true) {
                                    val pendingOutput = dsp.output
                                    if (!pendingOutput.hasRemaining()) break
                                    if (!super.handleBuffer(pendingOutput, hiResPtUs, hiResAccessUnits)) return
                                }
                            } catch (e: androidx.media3.exoplayer.audio.AudioSink.InitializationException) {
                                Timber.tag(TAG).e(e, "Hi-res DSP: end-of-stream drain aborted")
                            }
                        }
                        super.playToEndOfStream()
                    }

                    override fun flush() {
                        hiResDsp?.flush()
                        hiResEosQueued = false
                        // Seek/discontinuity: the next buffer is unrelated to the measured region, and the
                        // decoder may hand back the SAME ByteBuffer instance with new content.
                        tapLastBuffer = null
                        tapMeasuredEnd = -1
                        super.flush()
                    }

                    override fun reset() {
                        // Releases the native Superpowered processor, mirroring what the sink's own
                        // pipeline.reset() does for the int16 path. configure() always precedes the next
                        // handleBuffer, so dropping the reference here can only fall back to passthrough.
                        hiResDsp?.reset()
                        hiResDsp = null
                        hiResEosQueued = false
                        tapLastBuffer = null
                        tapMeasuredEnd = -1
                        super.reset()
                    }

                    override fun release() {
                        // release() does NOT route through reset(), and on this path the delegate's own
                        // pipeline does not contain the EQ processor — so without this the native
                        // Superpowered instance (~270 KB + 64 filters) would leak once per released player,
                        // i.e. on every crossfade/video swap. Idempotent after reset().
                        hiResDsp?.reset()
                        hiResDsp = null
                        super.release()
                    }

                    /** One INFO line per audio-configuration change so a shared log PROVES whether the EQ /
                     *  Safe Volume chain is in the path, instead of it being inferred from the UI. */
                    private fun logAudioPath(
                        inputFormat: androidx.media3.common.Format,
                        delegateFormat: androidx.media3.common.Format,
                        rescued: Boolean,
                    ) {
                        val isRaw = androidx.media3.common.MimeTypes.AUDIO_RAW == inputFormat.sampleMimeType
                        val path = when {
                            rescued -> "SINK_FLOAT_DSP"
                            !isRaw -> "PASSTHROUGH_NO_DSP"
                            else -> "MEDIA3_INT16_CHAIN"
                        }
                        Timber.tag(TAG).i(
                            "AUDIO_PATH mime=%s enc=%s rate=%d ch=%d floatOutEnabled=%b sinkEnc=%s path=%s superpowered=%s eqOn=%b",
                            inputFormat.sampleMimeType ?: "?",
                            pcmEncodingName(inputFormat.pcmEncoding),
                            inputFormat.sampleRate,
                            inputFormat.channelCount,
                            !lowEnd,
                            pcmEncodingName(delegateFormat.pcmEncoding),
                            path,
                            if (isRaw) "IN_PATH" else "BYPASSED",
                            eqProcessor.isEnabled(),
                        )
                    }

                    private fun pcmEncodingName(encoding: Int): String = when (encoding) {
                        C.ENCODING_PCM_8BIT -> "PCM_8BIT($encoding)"
                        C.ENCODING_PCM_16BIT -> "PCM_16BIT($encoding)"
                        C.ENCODING_PCM_16BIT_BIG_ENDIAN -> "PCM_16BIT_BE($encoding)"
                        C.ENCODING_PCM_24BIT -> "PCM_24BIT($encoding)"
                        C.ENCODING_PCM_24BIT_BIG_ENDIAN -> "PCM_24BIT_BE($encoding)"
                        C.ENCODING_PCM_32BIT -> "PCM_32BIT($encoding)"
                        C.ENCODING_PCM_32BIT_BIG_ENDIAN -> "PCM_32BIT_BE($encoding)"
                        C.ENCODING_PCM_FLOAT -> "PCM_FLOAT($encoding)"
                        else -> "enc($encoding)"
                    }
                }
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
                // UPDATE totalPlayTime is a no-op if the song row does not exist yet. Insert first so
                // the "already played" checkmark (independent of Aleatorio mejorado) actually appears
                // on albums, EPs and YouTube playlists the user just heard.
                mediaItem.metadata?.let { insert(it) }
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
        val state = capturePersistPlayerState()
        scope.launch(Dispatchers.IO) {
            runCatching { writePersistPlayerState(state) }
        }
        checkpointEnhancedShuffleCursor()
    }

    /** Same payload as [savePlaybackPositionToDisk] but blocks — for ACTION_SHUTDOWN / REBOOT. */
    private fun savePlaybackPositionToDiskSynchronous() {
        if (!::player.isInitialized || player.mediaItemCount == 0) return
        runCatching {
            writePersistPlayerState(capturePersistPlayerState())
        }
        checkpointEnhancedShuffleCursor()
    }

    private fun capturePersistPlayerState(): PersistPlayerState =
        PersistPlayerState(
            playWhenReady = player.playWhenReady,
            repeatMode = player.repeatMode,
            shuffleModeEnabled = player.shuffleModeEnabled,
            volume = (if (::playerVolume.isInitialized) playerVolume.value else player.volume),
            currentPosition = player.currentPosition,
            currentMediaItemIndex = player.currentMediaItemIndex,
            playbackState = player.playbackState,
        )

    private fun writePersistPlayerState(state: PersistPlayerState) {
        filesDir.resolve(PERSISTENT_PLAYER_STATE_FILE).outputStream().use { fos ->
            ObjectOutputStream(fos).use { it.writeObject(state) }
        }
    }

    private fun checkpointEnhancedShuffleCursor() {
        val ctx = shuffleContextId
        if (enhancedShuffleHint && ctx != null && player.shuffleModeEnabled) {
            val sid = player.currentMetadata?.id
            val pos = player.currentPosition
            val now = System.currentTimeMillis()
            scope.launch(enhancedShuffleWriteDispatcher) {
                runCatching {
                    database.insertEnhancedContextIgnore(
                        EnhancedShuffleContextEntity(contextId = ctx, lastSongId = sid, lastPositionMs = pos, updatedAt = now)
                    )
                    database.updateEnhancedContextCursor(ctx, sid, pos, now)
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

    /**
     * The media process's answer to memory pressure.
     *
     * Until now this service had none: `Application.onTrimMemory` trimmed Coil's image cache (same
     * process — the service declares no `android:process`) and nothing else here gave anything back. A
     * foreground media service that never responds to pressure is precisely what the low-memory killer
     * reaps, and when it is reaped Android Auto stops listing the app — no dialog, no crash, nothing the
     * user can report. This is a cheap, honest signal that the process is willing to shrink.
     *
     * Strictly non-playback. It frees ONE class of thing — memoized values that are re-derived on demand
     * — plus the pending resume offer at the harshest levels. It does not touch the player, the crossfade
     * state, the silence hints the crossfade times itself from, or anything the audio path reads. A
     * Service's `onTrimMemory` is delivered on the Main thread, which is the thread that owns both maps.
     */
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        runCatching {
            // Matched by VALUE, never by `>=`. The constants are not a pressure scale: TRIM_MEMORY_
            // UI_HIDDEN (20) sits BETWEEN RUNNING_CRITICAL (15) and BACKGROUND (40) and means nothing
            // more than "the Activity went away" — which, with the phone in a pocket and the car
            // driving, is the app's normal state. A `>=` test would fire on it every single time.
            val pressure = level == android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW ||
                level == android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL ||
                level == android.content.ComponentCallbacks2.TRIM_MEMORY_BACKGROUND ||
                level == android.content.ComponentCallbacks2.TRIM_MEMORY_MODERATE ||
                level == android.content.ComponentCallbacks2.TRIM_MEMORY_COMPLETE
            if (!pressure) return
            clearShuffleCaches()
            // The offer is a courtesy; under REAL pressure it is not worth a byte. Only at the levels
            // that mean the process is next in line, because dropping it retires a prompt the user may
            // be looking at.
            val severe = level == android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL ||
                level == android.content.ComponentCallbacks2.TRIM_MEMORY_MODERATE ||
                level == android.content.ComponentCallbacks2.TRIM_MEMORY_COMPLETE
            if (severe && previousQueueSnapshot != null) {
                Timber.tag(TAG).i("Dropping the resume offer under memory pressure (level=%d)", level)
                dismissPreviousQueueOffer()
            }
        }
    }

    private fun isPowerSaveModeActive(): Boolean =
        (getSystemService(POWER_SERVICE) as? android.os.PowerManager)?.isPowerSaveMode == true

    /**
     * True when Android Auto (phone projection) or a car head-unit MediaSession controller is connected.
     * Crossfade + binder broadcasts to Auto are a known micro-cut source (registry #104/#114); force hard cuts.
     */
    private fun isAndroidAutoControllerConnected(): Boolean {
        if (!::mediaSession.isInitialized) return false
        return runCatching {
            mediaSession.connectedControllers.any { info ->
                val pkg = info.packageName.orEmpty().lowercase()
                pkg.contains("android.projection") ||
                    pkg.contains("gearhead") ||
                    pkg.contains("android.auto") ||
                    pkg.contains("car.apps") ||
                    pkg.endsWith(".car") ||
                    iad1tya.echo.music.utils.DeviceForm.isCar(this)
            }
        }.getOrDefault(iad1tya.echo.music.utils.DeviceForm.isCar(this))
    }

    override fun onDestroy() {
        isRunning = false
        playbackKeepAlive.release()
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
        try {
            unregisterReceiver(becomingNoisyReceiver)
        } catch (e: Exception) {
            Timber.tag(TAG).d("becomingNoisyReceiver was not registered: ${e.message}")
        }
        try {
            unregisterReceiver(shutdownSaveReceiver)
        } catch (e: Exception) {
            Timber.tag(TAG).d("shutdownSaveReceiver was not registered: ${e.message}")
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
        crossfadeTailArmJob?.cancel()
        tailQuietRecheckJob?.cancel()
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
        // The fading player is gone, so the lyrics view's outgoing-song override must not outlive it.
        _crossfadeOutgoingMetadata.value = null
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
            ACTION_CLEAR_SONG_CACHE -> {
                intent.getStringExtra(EXTRA_SONG_ID)?.takeIf { it.isNotBlank() }?.let { clearSongCache(it) }
            }
            // Playlist widget: the per-card play button. The receiver forwards the tapped card here; without
            // this branch the intent reached onStartCommand and fell through, so the button did nothing.
            PlaylistWidgetReceiver.ACTION_PLAY_TARGET -> {
                playWidgetTarget(
                    targetType = intent.getStringExtra(PlaylistWidgetReceiver.EXTRA_TARGET_TYPE),
                    targetId = intent.getStringExtra(PlaylistWidgetReceiver.EXTRA_TARGET_ID),
                    targetTitle = intent.getStringExtra(PlaylistWidgetReceiver.EXTRA_TARGET_TITLE),
                )
            }
            // Turntable widget: sent when a widget is added/resized so it stops showing the default layout.
            TurntableWidgetReceiver.ACTION_UPDATE_TURNTABLE_WIDGET -> {
                updateWidgetUI(player.isPlaying)
            }
        }

        return super.onStartCommand(intent, flags, startId)
    }

    /**
     * Playlist widget → per-card play button. Builds the same queue the matching in-app screen would build
     * and hands it to [playQueue] (which clears the #27 restore veto, so a widget tap plays immediately).
     *
     * Defensive by design: a missing/unknown type, a deleted playlist, an empty list or a failed YouTube
     * fetch only logs — a home-screen tap must never crash the service nor disturb what is already playing.
     */
    private fun playWidgetTarget(targetType: String?, targetId: String?, targetTitle: String?) {
        val type = targetType?.takeIf { it.isNotBlank() }
        val id = targetId?.takeIf { it.isNotBlank() }
        if (type == null || id == null) {
            Timber.tag(TAG).w("playWidgetTarget: missing target (type=$targetType, id=$targetId)")
            return
        }
        scope.launch(SilentHandler) {
            val resolved: Pair<List<MediaItem>, String?>? = withContext(Dispatchers.IO) {
                runCatching {
                    when (type) {
                        PlaylistWidgetReceiver.TARGET_TYPE_LIKED ->
                            database.likedSongsByCreateDateAsc().first()
                                .map { it.toMediaItem() } to "AP:liked"

                        PlaylistWidgetReceiver.TARGET_TYPE_DOWNLOADED ->
                            database.downloadedSongsByCreateDateAsc().first()
                                .map { it.toMediaItem() } to "AP:downloaded"

                        // The id is the top-N size the widget card was built with ("50").
                        PlaylistWidgetReceiver.TARGET_TYPE_TOP ->
                            database.mostPlayedSongs(0L, limit = id.toIntOrNull() ?: 50).first()
                                .map { it.toMediaItem() } to null

                        PlaylistWidgetReceiver.TARGET_TYPE_LOCAL ->
                            database.playlistSongs(id).first()
                                .map { it.song.toMediaItem() } to "PL:$id"

                        // Online cards carry the browseId. A playlist saved in the library keeps its songs
                        // locally, so resolve that first and only hit the network for a speed-dial entry
                        // that isn't in the library.
                        PlaylistWidgetReceiver.TARGET_TYPE_ONLINE -> {
                            // bookmarkedAt != null: a playlist the user REMOVED from the app keeps its row
                            // (and its songs) as a tombstone so the sync won't resurrect it. Without this
                            // check the widget would happily play that stale local snapshot of a playlist
                            // the user deleted, instead of fetching the current one from YouTube.
                            val local = database.playlistByBrowseId(id).first()
                                ?.takeIf { it.playlist.bookmarkedAt != null }
                            val localSongs =
                                local?.let { database.playlistSongs(it.playlist.id).first() }.orEmpty()
                            if (local != null && localSongs.isNotEmpty()) {
                                localSongs.map { it.song.toMediaItem() } to "PL:${local.playlist.id}"
                            } else {
                                YouTube.playlist(id).getOrNull()?.songs.orEmpty()
                                    .map { it.toMediaItem() } to null
                            }
                        }

                        else -> null
                    }
                }.onFailure {
                    Timber.tag(TAG).w(it, "playWidgetTarget: failed to load $type/$id")
                }.getOrNull()
            }
            if (resolved == null) {
                Timber.tag(TAG).w("playWidgetTarget: nothing to play for $type/$id")
                return@launch
            }
            val (items, contextId) = resolved
            if (items.isEmpty()) {
                Timber.tag(TAG).w("playWidgetTarget: empty target $type/$id")
                return@launch
            }
            playQueue(
                ListQueue(
                    title = targetTitle,
                    items = items,
                    contextId = contextId,
                )
            )
        }
    }

    /**
     * Refetch ("volver a obtener"): drop everything that would let the NEXT play of [songId] serve the OLD
     * audio. Clearing songUrlCache is the load-bearing half — the resolver returns a cached-URL hit long
     * before it would re-ask YouTube, so removing the bytes alone changes nothing. The persisted mirror is
     * rewritten in the same breath or the stale URL simply returns on the next cold start.
     *
     * #28 forbids dropping cached BYTES *implicitly* (when we merely lack a URL — the "ghost cache" churn);
     * this path is the user explicitly asking for a fresh stream, which is the orthogonal case. The per-mediaId
     * lookup shape and the persistence invariant it protects are untouched.
     *
     * Disk work runs off the main thread (onStartCommand is Main); the caches are concurrent, so no lock.
     */
    private fun clearSongCache(songId: String) {
        songUrlCache.remove(songId)
        persistSongUrlCache()
        scope.launch(Dispatchers.IO) {
            runCatching { playerCache.removeResource(songId) }
                .onFailure { Timber.tag(TAG).d(it, "clearSongCache: playerCache removal failed (non-fatal)") }
        }
        Timber.tag(TAG).i("clearSongCache: dropped cached stream URL + bytes for $songId")
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
            // #55 (battery/heat) + HyperOS ScreenOffCPUCheckKill (owner exit_reasons): never tick binder
            // updates when there is no widget, and never while the screen is off (home widgets are not
            // visible). A failed presence probe must NOT default to "has widgets" — that re-introduced
            // the every-second waste for everyone.
            while (isActive) {
                val hasWidgets = runCatching { widgetManager.hasAnyWidget() }.getOrDefault(false)
                val screenOn = (getSystemService(POWER_SERVICE) as? android.os.PowerManager)
                    ?.isInteractive != false
                when {
                    hasWidgets && player.isPlaying && screenOn -> {
                        updateWidgetUI(true)
                        delay(1000)
                    }
                    hasWidgets && player.isPlaying -> {
                        // Playing with screen off: skip binder; re-check presence infrequently.
                        delay(30_000)
                    }
                    else -> delay(60_000)
                }
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

    /**
     * AIMP-style smooth entry: wait until audio is actually rendering, then a short ~400ms sine ramp
     * so the skip is not a slam. Owner: songs were taking too long to start — the old 1.6s quieterstep
     * swell (first quarter almost silent) felt like the track had not begun. Volume-only. The finally
     * ALWAYS restores the exact user volume (mute-aware), so a cancelled ramp can never strand it low.
     */
    private fun fadeInOnManualChange() {
        manualFadeInJob?.cancel()
        if (!::playerVolume.isInitialized) return
        val target = if (isMuted.value) 0f else playerVolume.value
        if (target <= 0f) return
        lateinit var self: Job
        self = scope.launch {
            try {
                player.volume = 0f
                // WAIT for the audio to actually RENDER before ramping (bounded): a wall-clock ramp from
                // the transition callback finished into SILENCE and the real audio then slammed in.
                var waited = 0L
                while (isActive && waited < 8_000L &&
                    !(player.isPlaying && player.playbackState == Player.STATE_READY)
                ) {
                    delay(40)
                    waited += 40
                }
                if (!isActive || !player.isPlaying) return@launch
                // Audible on the first step (~−12 dB), full level in ~400ms. Equal-power sine, no
                // smootherstep hold-at-silence.
                val steps = 16
                val stepTime = 400L / steps
                for (i in 1..steps) {
                    if (!isActive || isCrossfading) break
                    val p = i / steps.toFloat()
                    val floor = 0.25f
                    player.volume = target * (floor + (1f - floor) *
                        kotlin.math.sin(p * (Math.PI / 2.0).toFloat()))
                    delay(stepTime)
                }
            } finally {
                runCatching {
                    // Exact restore, mute-aware — never strand the volume below the user's setting.
                    // IDENTITY guard: on rapid skips a NEWER fade may already own the volume (it just set
                    // 0f); a cancelled older job restoring FULL volume after that would kill the new
                    // fade-in. Only the job still registered as current restores.
                    if (manualFadeInJob === self && !isCrossfading && ::playerVolume.isInitialized) {
                        player.volume = if (isMuted.value) 0f else playerVolume.value
                    }
                }
            }
        }
        manualFadeInJob = self
    }

    private fun scheduleCrossfade() {
        crossfadeTriggerJob?.cancel()
        crossfadeTriggerJob = null
        crossfadePreloadJob?.cancel()
        crossfadePreloadJob = null
        crossfadeReadyJob?.cancel()
        crossfadeReadyJob = null
        crossfadeTailArmJob?.cancel()
        crossfadeTailArmJob = null
        tailQuietRecheckJob?.cancel()
        tailQuietRecheckJob = null
        // Tail-silence detection is only valid inside the fade window this call is about to (re)compute —
        // disarm on every (re)schedule (track change, seek, queue change) so a stale arm can't fire.
        playerSilenceProcessors[player]?.tailDetectEnabled = false
        // Release any incoming player we preloaded for a transition that's no longer happening (user
        // skipped, seeked, queue changed) so we never leak a second ExoPlayer.
        if (!isCrossfading) {
            // REUSE a still-valid preload (thermal audit): scheduleCrossfade fires from ~6 event sites
            // (playWhenReady flips, rebuffer→READY, in-song seeks...), and unconditionally tearing the
            // buffered secondary down meant building 2-4 full ExoPlayers per song — each with native
            // processor init, an O(N) queue copy and up to 12 s of re-buffering (network + decode heat).
            // Keep it ONLY when:
            //  • the TIMELINE VERSION is unchanged (a counter bumped by every onTimelineChanged): the
            //    secondary holds a queue COPY that becomes the LIVE queue at the swap, so ANY timeline
            //    mutation — append, remove, drag-reorder past the next item, replaceMediaItem with the
            //    same id (Opus refetch, video URI) — makes the copy stale. Same-target+same-count alone
            //    provably missed reorders and replacements (adversarial round);
            //  • the next target still matches (shuffle reorder without timeline change);
            //  • AND every early-return below would NOT fire — a kept player is only legal on the path
            //    that reaches the trigger scheduling, otherwise it sits prepared with NO trigger job
            //    (an orphan holding codecs + 12 s of buffer indefinitely).
            val keepPreload = secondaryPlayer?.let { sec ->
                val targetIdx = if (player.repeatMode == REPEAT_MODE_ONE) {
                    player.currentMediaItemIndex
                } else {
                    player.nextMediaItemIndex
                }
                val liveTarget = if (targetIdx != C.INDEX_UNSET && targetIdx < player.mediaItemCount) {
                    runCatching { player.getMediaItemAt(targetIdx).mediaId }.getOrNull()
                } else null
                liveTarget != null &&
                    secondaryTimelineVersion == timelineVersion &&
                    runCatching { sec.currentMediaItem?.mediaId }.getOrNull() == liveTarget &&
                    !highPerformanceModeHint && crossfadeEnabled && !_videoMode.value &&
                    player.duration != C.TIME_UNSET && player.duration > crossfadeDuration &&
                    !(crossfadeGapless && isNextItemGapless())
            } == true
            if (!keepPreload) {
                secondaryPlayer?.let {
                    // Silence too — this is the MOST frequent teardown of the three (it runs whenever a
                    // preloaded incoming player is discarded: skip, seek, queue change), so omitting it here
                    // leaked a HashMap entry keyed by a released ExoPlayer on nearly every user interaction.
                    playerSilenceProcessors.remove(it)
                    playerNormProcessors.remove(it)
                    playerLimiterProcessors.remove(it)
                    playerEqProcessors.remove(it)?.let { eq -> equalizerService.removeAudioProcessor(eq) }
                    it.stop()
                    // NO clearMediaItems: redundant before release() and a mutation-race trigger (see
                    // secondaryPlayerListener teardown / CRASH_REPORTS #2).
                    it.release()
                }
                // Inside the if — an unconditional null here ORPHANED the kept player (nulled without
                // release, rebuilt from scratch anyway): the exact leak this block exists to prevent.
                secondaryPlayer = null
            }
        }
        // High-Performance Mode: crossfade is force-disabled (crossfadeEnabled already reflects this via the
        // perf-gated flow at collect time). This explicit, cheap @Volatile guard makes the intent robust and
        // self-documenting — transitions fall back to normal gapless/simple playback (a single decoder, no
        // second ExoPlayer) on weak/TV/car devices. No-op on capable devices: perf mode off → hint false →
        // falls through to the unchanged 9s equal-power crossfade path below. The cleanup above still ran, so
        // any incoming player preloaded before perf mode toggled on is released rather than leaked.
        if (highPerformanceModeHint) return
        if (!crossfadeEnabled || player.duration == C.TIME_UNSET) return
        if (player.duration <= crossfadeDuration) {
            traceCrossfade("skip-short", "dur=${player.duration}ms <= fade window — no blend possible")
            return
        }
        // Crossfade builds a SECOND ExoPlayer and copies the queue into it; the video item (a cache-less
        // muxed source with no TextureView attached on the secondary player) would break. Skip crossfade
        // entirely while video mode is on.
        if (_videoMode.value) return
        if (crossfadeGapless && isNextItemGapless()) {
            traceCrossfade("gapless-bypass", "same-album pair -> deliberate gapless advance (Ajustes)")
            return
        }
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

        val targetMediaId = player.currentMediaItem?.mediaId

        // PER-SONG TAIL MEMORY: if a previous play measured this song's trailing silence, anchor the
        // trigger at (musical end - fade window) instead of (file end - fade window) — the decay covers
        // the last seconds of MUSIC and completes right as the music ends; the silent tail never plays.
        // Clamped so a bad hint can never pull the trigger absurdly early; no hint → live tail tiers
        // below remain the first-play path.
        var tailHint = (targetMediaId?.let { tailSilenceHintMs[it] } ?: 0L)
            .coerceAtMost((player.duration * 2) / 5)
        // The hint may never pull the trigger into the song's opening seconds: on a short track (or with
        // a bogus learned tail) the fade would start at position ~0 — and under REPEAT_ONE that state
        // re-arms itself into an endless fade→swap loop, building an ExoPlayer per pass. Under 3s of
        // audible pre-fade play, drop the hint (the live tail tiers still cover the real ending).
        if (player.duration - tailHint - crossfadeDuration.toLong() < 3_000L) tailHint = 0L
        val triggerTime = player.duration - tailHint - crossfadeDuration.toLong()
        // Already INSIDE the fade window (near-end seek; radio items landing during the last seconds and
        // re-arming this schedule): fire the fade NOW instead of bailing. The old `return` here is why a
        // late re-arm could still end in a hard cut — every guard re-runs inside startCrossfade anyway.
        val delayMs = (triggerTime - player.currentPosition).coerceAtLeast(0L)
        if (tailHint > 0) {
            traceCrossfade("hint-anchor", "learnedTail=${tailHint}ms — fade covers the last music, not the silence")
        }

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

        // TAIL DETECTION — arm the detector for the final stretch of THIS track (its own job so the
        // window can be WIDER than the preload lead). Two tiers fire the fade at the end of the MUSIC
        // instead of the FILE:
        //  • true silence (≥3.5s under ~-42 dBFS): the audible content is over — long silent tails no
        //    longer produce a dead gap before the next song;
        //  • "musical end" (≥2.5s under ~-25 dBFS): the song entered its mastered fade-out / quiet ending
        //    — the crossfade starts THERE, over a still-audible ending, so the blend (old going down +
        //    new rising on top) is actually HEARD. Position-guarded in the handler.
        // Window: the WHOLE track (owner order — no silent gap ever; mid-song safety lives in the
        // handler's position-tiered thresholds). Measure-only. HONEST
        // SCOPE: media3 only feeds custom processors on the 16-bit INT pipeline (Opus/AAC/16-bit FLAC —
        // the vast majority of content); the hi-res FLOAT pipeline (24-bit on capable devices) bypasses
        // the whole custom chain — covered since 0.6.131 by the sink-level tap (ForwardingAudioSink →
        // measureExternal), so FLOAT content is measured too.
        // WHOLE-TRACK arming (owner order: the transition must fire NO MATTER how many seconds of silence
        // the song carries — never a dead gap). The position-tiered handler keeps mid-song safety: far from
        // the end TRUE silence needs ≥7s continuous (a skit/grand-pause can't fire), near the end 3.5s, and
        // the -25dB "musical end" tier only acts inside (fade+4s). Same-track re-arms preserve counters
        // (identity check + the processor no longer wipes state on a brief disarm); a new track resets.
        // Cost: per-frame abs+compare on the already-hot audio thread — trivial vs decode/EQ.
        playerSilenceProcessors[player]?.let {
            val armId = player.currentMediaItem?.mediaId
            if (tailArmedMediaId != armId) {
                it.resetTracking()
                tailArmedMediaId = armId
                leadHintTrustedForArmedTrack = player.currentPosition <= 2_000L
            } else if (player.currentPosition > 2_000L && it.leadingSilenceUsOrNegative() < 0L) {
                // Same-track re-arm past the intro with nothing finalized yet: a seek moved counting away
                // from the beginning — the eventual finalize would be interior audio, not the intro.
                leadHintTrustedForArmedTrack = false
            }
            it.tailDetectEnabled = true
        }

        crossfadeTriggerJob = scope.launch {
            delay(delayMs)
            if (isActive && player.isPlaying && player.currentMediaItem?.mediaId == targetMediaId) {
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

    /** Per-song tail memory writer. Sub-2s "tails" mean the song has no real silent tail — an EXACT (EOS)
     *  measurement saying so also CLEARS any stale learned value. Implausible values (> half the song) are
     *  ignored outright. */
    private fun storeTailHint(mediaId: String?, hintMs: Long, durationMs: Long) {
        if (mediaId == null || durationMs == C.TIME_UNSET) return
        if (hintMs > durationMs / 2) return
        if (hintMs < 2_000L) {
            tailSilenceHintMs.remove(mediaId)
            return
        }
        if (tailSilenceHintMs.size > 400) tailSilenceHintMs.clear()
        tailSilenceHintMs[mediaId] = hintMs
    }

    private var lastCrossfadeTraceKey: String? = null

    /**
     * One shareable-log line per DISTINCT crossfade event (deduped per track+event so the many
     * reschedules can't spam). Turns every "esta transición falló" report into an attributable verdict
     * in Ajustes ▸ Registros: fade fired (which tier), swap committed, or WHY it cut instead.
     */
    private fun traceCrossfade(event: String, detail: String) {
        val id = player.currentMediaItem?.mediaId ?: "?"
        val key = "$id:$event"
        if (key == lastCrossfadeTraceKey) return
        lastCrossfadeTraceKey = key
        runCatching {
            Timber.tag(TAG).i("CROSSFADE_TRACE id=%s ev=%s %s", id, event, detail)
            iad1tya.echo.music.utils.PlaybackLogManager.log(
                iad1tya.echo.music.utils.PlaybackLogLevel.INFO,
                "CROSSFADE_TRACE",
                "id=$id ev=$event $detail"
            )
        }
    }

    /**
     * TAIL-DETECTION handler (Main thread). The CURRENT player's detector — armed for the final stretch
     * (≤30s) by [scheduleCrossfade]'s tail-arm job — reported one of its two tiers:
     *  • TRUE SILENCE (≥3.5s under ~-42 dBFS; ≥7s when still far from the end — mid-song skit/pause
     *    safety): the audible content ended, fire the fade now, every extra second is dead air;
     *  • "MUSICAL END" (≥2.5s under ~-25 dBFS): the mastered fade-out — fire only within (fade+4s) of
     *    the real end so the blend covers a STILL-AUDIBLE ending (the audible-crossfade segue); earlier
     *    fires defer to a live re-check at the moment the fade would be due.
     * Every normal crossfade guard re-runs inside [startCrossfade]; disarms itself so one detection fires
     * at most one fade; stale fires after a swap/seek no-op via disarm-on-reschedule + live-state checks.
     */
    private fun onTailSilenceDetected() {
        val proc = playerSilenceProcessors[player] ?: return
        if (!proc.tailDetectEnabled) return
        val silentNow = proc.isCurrentlySilent()
        val quietNow = proc.isCurrentlyQuiet()
        // Main-hop recheck: if audible content resumed between the audio-thread fire and this hop, that
        // was a mid-tail pause, not the end of the music — keep the detector armed and bail (a LATER
        // episode in the window can still fire).
        if (!silentNow && !quietNow) return
        // INTRO SILENCE ≠ TAIL. Whole-track arming means opening dead air also accumulates. After ≥7s the
        // far-from-end path used to fire a "tail" crossfade and SKIP the song (owner log 0.6.163:
        // OjMLBY2cVPk / "A Man You Would Write About" — tier=silence remaining≈291s ~7s after start).
        // leadingSilenceUs is finalized only at the FIRST loud frame: while it is still negative we have
        // never heard music on this arm, so this silence cannot be a trailing tail. Stay armed; do NOT
        // schedule the 7s recheck (that recheck was the skip). After music starts, notifiedThisSilence
        // resets and a real end-of-song silence can fire normally.
        if (silentNow && proc.leadingSilenceUsOrNegative() < 0L) {
            return
        }
        // TIER 1 distance scaling — far from the end, TRUE silence must persist LONGER (7s vs 3.5s)
        // before firing: with the 30s arm window a ≥3.5s noise-gated pause (album skit, grand pause,
        // live-set gap) at remaining≈27s would otherwise fade+advance and skip real music. A genuine
        // dead tail keeps accumulating silence and still fires at 7s in — most of the gap still dies.
        if (silentNow) {
            val duration = player.duration
            if (duration != C.TIME_UNSET) {
                val remaining = duration - player.currentPosition
                val fireWindowMs = crossfadeDuration.toLong() + 4_000L
                if (remaining > fireWindowMs && proc.silenceDurationUs() < 7_000_000L) {
                    // No recheck loop while paused (frozen position/counters would re-arm forever).
                    if (!player.isPlaying) return
                    tailQuietRecheckJob?.cancel()
                    tailQuietRecheckJob = scope.launch {
                        delay(((7_000_000L - proc.silenceDurationUs()) / 1_000L).coerceAtLeast(250L))
                        onTailSilenceDetected() // re-evaluates LIVE state; bails if audio resumed
                    }
                    return
                }
            }
        }
        // TIER GATE — "musical end" (quiet but not silent, ~-25 dBFS) only fires NEAR the real end: the
        // fade must land where it would soon happen anyway, just anchored to the music instead of the
        // file. Too early (a quiet bridge 25s out) → schedule a re-check for the moment the fade would be
        // due; if the ending is STILL quiet then, fire — if the music came back, the live state bails and
        // the detector stays armed. TRUE silence (with the persistence above) fires anywhere in the
        // window: nothing audible remains and every extra second is dead air.
        if (!silentNow) {
            // No recheck loop while paused: position is frozen, so a scheduled recheck would just re-arm
            // itself forever. Bail armed; the file-end trigger remains the fallback after resume.
            if (!player.isPlaying) return
            val duration = player.duration
            if (duration == C.TIME_UNSET) return
            val remaining = duration - player.currentPosition
            val fireWindowMs = crossfadeDuration.toLong() + 4_000L
            if (remaining > fireWindowMs) {
                tailQuietRecheckJob?.cancel()
                tailQuietRecheckJob = scope.launch {
                    delay((remaining - fireWindowMs).coerceAtLeast(250L))
                    onTailSilenceDetected() // re-evaluates LIVE state; bails if the music resumed
                }
                return
            }
        }
        proc.tailDetectEnabled = false
        if (isCrossfading || !crossfadeEnabled || highPerformanceModeHint || _videoMode.value) return
        if (!player.isPlaying) return
        if (crossfadeGapless && isNextItemGapless()) {
            traceCrossfade("gapless-bypass", "same-album pair -> deliberate gapless advance (Ajustes)")
            return
        }
        if (!player.hasNextMediaItem() && player.repeatMode != REPEAT_MODE_ONE) return
        // LEARN this song's tail for the per-song memory (silent tier ONLY: its run start is the true end
        // of audible content; the quiet tier's run start is the START of a still-audible mastered fade-out
        // — anchoring 5s before THAT would cut real music on later plays). trailing ≈ remaining + run; the
        // sink buffer skews it ≤~0.5s toward "earlier", which can only trim threshold-level noise. The EOS
        // snapshot in cleanupCrossfade refines this with the exact value when the decoder reached EOS.
        if (silentNow) {
            val dur = player.duration
            if (dur != C.TIME_UNSET) {
                storeTailHint(
                    player.currentMediaItem?.mediaId,
                    (dur - player.currentPosition) + proc.silenceDurationUs() / 1_000L,
                    dur
                )
            }
        }
        // Deliberately do NOT cancel crossfadeTriggerJob: if this early fade can't actually start (the
        // secondary misses its READY window, or the user pauses during the bounded wait) the file-end-
        // anchored trigger must survive as the fallback — cancelling it here left the track with NO fade at
        // all. If the early fade DOES start, beginCrossfadeSwap cancels the stale jobs at the commit point.
        traceCrossfade(
            "tail-fire",
            "tier=${if (silentNow) "silence" else "quiet"} remaining=${
                player.duration.takeIf { it != C.TIME_UNSET }?.minus(player.currentPosition) ?: -1
            }ms"
        )
        startCrossfade()
    }

    private fun startCrossfade() {
        if (isCrossfading) return
        // Tail detection's job is done the moment any fade actually starts (either path) — disarm.
        playerSilenceProcessors[player]?.tailDetectEnabled = false

        
        
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
            // DYNAMIC bound (owner: "algunas transiciones las corta"): the old fixed 2.5s gave up long
            // before slow-resolving incoming tracks were ready (a Lossless resolve alone can take longer)
            // and fell to a HARD CUT. Wait as long as the OUTGOING still has audible time left (~800ms
            // floor to land the swap) — a late, shorter blend always beats a cut.
            var waited = 0L
            while (isActive && secPlayer.playbackState != Player.STATE_READY) {
                val dur = player.duration
                val remaining = if (dur == C.TIME_UNSET) 0L else dur - player.currentPosition
                if (remaining <= 800L) break
                if (!player.isPlaying || player.currentMediaItem?.mediaId != targetMediaId) break
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
            } else {
                // Single-player path will hard-cut — make the failure VISIBLE in the shareable log.
                traceCrossfade("cut-not-ready", "waited=${waited}ms incoming never READY (slow resolve/buffer)")
            }
        }
    }

    /** Flip the (already-READY) incoming player on and run the swap + fade. Extracted so both the fast path
     *  and the bounded ready-wait in [startCrossfade] share one swap site. */
    private fun beginCrossfadeSwap(secPlayer: ExoPlayer, savedShuffleEnabled: Boolean) {
        if (isCrossfading) return
        secPlayer.playWhenReady = true

        performCrossfadeSwap()

        // The fade COMMITTED — kill the file-end-anchored jobs NOW. They deliberately survive the
        // can't-start paths (secondary missed READY, pause during the bounded wait) as the fallback, but
        // once the swap really happened they are stale — and under REPEAT_ONE the swapped-in player plays
        // the SAME mediaId, so the old trigger's mediaId guard would pass after a short (≤8s) fade ended
        // and audibly RESTART the song mid-play. Cancelling at the commit point closes that hole while
        // keeping the fallback intact.
        crossfadeTriggerJob?.cancel()
        crossfadeTriggerJob = null
        crossfadePreloadJob?.cancel()
        crossfadePreloadJob = null
        crossfadeTailArmJob?.cancel()
        crossfadeTailArmJob = null
        tailQuietRecheckJob?.cancel()
        tailQuietRecheckJob = null

        traceCrossfade("swap-ok", "blend running (curve+duration per Ajustes)")

        // A crossfade swap IS a natural auto-advance — but it reaches the next track through a path that
        // never fires onMediaItemTransition. Everything that normally happens there must be mirrored here or
        // it silently stops working in the app's DEFAULT configuration (crossfade ON): scrobbling, Cast
        // follow-along, SponsorBlock segments, upcoming-track prefetch, and pulling the next page of a long
        // playlist/album (whose absence made the queue fall into the infinite radio instead of continuing).
        applyAutoAdvanceSideEffects()
        // Same ordering rule as the transition path: trace BEFORE either recording block below, or the
        // line always reads "repeat=YES" and tells us nothing.
        traceNoRepeat("crossfade-swap")
        // REPEAT_ONE swaps the SAME track in over and over; treating those as fresh advances paginated the
        // queue on every loop (a page fetched + appended per repeat). The transition path suppresses
        // pagination on repeats for exactly this reason — mirror it.
        maybeLoadMoreQueuePages(isRepeatTransition = player.repeatMode == REPEAT_MODE_ONE)

        // Linear-play recording under crossfade: the swap path skips onMediaItemTransition, so without
        // this a LINEAR listen (shuffle off, crossfade on — every auto-advance is a swap) left no trace in
        // the persistent context memory, and activating shuffle later replayed songs heard minutes before.
        // Mirrors the ungated insert in onMediaItemTransition; the shuffle branch below records its own.
        if (!savedShuffleEnabled) {
            val linearId = player.currentMediaItem?.mediaId ?: player.currentMetadata?.id
            val linearCtx = shuffleContextId
            if (enhancedShuffleHint && linearCtx != null && linearId != null) {
                val now = System.currentTimeMillis()
                scope.launch(enhancedShuffleWriteDispatcher) {
                    runCatching { database.insertEnhancedPlayed(EnhancedShufflePlayedEntity(linearCtx, linearId, now)) }
                }
            }
        }

        if (savedShuffleEnabled) {
            // Enhanced Shuffle: the crossfade swap advances the queue via a path that SKIPS
            // onMediaItemTransition — where B5 + the persistent no-repeat bookkeeping normally record the
            // just-started song as played. With crossfade ON (every auto-advance is a swap) that recording
            // NEVER ran, so shufflePlayedIds stayed near-empty and applyShuffleOrder below kept re-shuffling a
            // pool where nothing was marked played → already-heard songs resurfaced as the "next" song
            // (reported: the shuffle jumps to a song that isn't the right continuation). Record the song the
            // swap just made current here, mirroring onMediaItemTransition's B5 block, BEFORE re-applying the
            // order so played songs correctly sink and the cycle-exhaustion self-reset counts them.
            val playedId = player.currentMediaItem?.mediaId ?: player.currentMetadata?.id
            playedId?.let { shufflePlayedIds.add(it) }
            // ARTIST SPACING mirror: this path skips onMediaItemTransition, so without this line the
            // artist history would only ever fill with crossfade OFF — i.e. never, for this owner.
            rememberShuffleArtist(player.currentMediaItem)
            val ctx = shuffleContextId
            if (enhancedShuffleHint && ctx != null && playedId != null) {
                val now = System.currentTimeMillis()
                scope.launch(enhancedShuffleWriteDispatcher) {
                    runCatching { database.insertEnhancedPlayed(EnhancedShufflePlayedEntity(ctx, playedId, now)) }
                }
            }

            // Enhanced Shuffle — cycle exhaustion. The add above may have just COMPLETED the context (this
            // swap made the last unplayed song current). This is the ONLY place that's knowable in time
            // under crossfade: the swap path skips onMediaItemTransition (where the early-handoff lives),
            // and a plain applyShuffleOrder here would run its all-played self-reset SYNCHRONOUSLY — wiping
            // the memory before any later check could observe the exhaustion (verified: that made a
            // scheduleCrossfade-time check dead code). A swap is by definition a NATURAL auto-advance, so
            // the early-handoff's AUTO-only semantics hold. On exhaustion: MARK the lap complete (the
            // memory is kept — it resets only when the user re-activates shuffle on this list), detach the
            // context, seed the infinite radio while this last song still plays, and SKIP this swap's
            // re-shuffle — the self-reset would un-sink the played tail; appendSeed() re-applies the order
            // once the radio items land (unplayed radio sorts ahead; the tail stays sunk).
            val exhaustCtx = shuffleContextId
            // player.shuffleModeEnabled: LIVE check on top of the captured savedShuffleEnabled — the swap
            // can run up to 2.5s after capture (READY-wait), and if the user turned shuffle OFF in that
            // window this destructive branch (memory wipe + radio) must not fire on an un-shuffled queue.
            if (enhancedShuffleHint && exhaustCtx != null && player.shuffleModeEnabled &&
                player.repeatMode == REPEAT_MODE_OFF && autoLoadMoreHint &&
                !radioSeedInFlight && isEnhancedContextExhausted()
            ) {
                markEnhancedContextCycleComplete(exhaustCtx)
                shuffleContextId = null
                startRadioSeamlessly()
                // KNOWN bounded edge: until appendSeed re-applies the order, the swapped-in player keeps
                // media3's own random shuffle order — if this LAST song ends before the seed lands (very
                // short song + slow network) one already-played song may briefly replay, then the radio
                // takes over (its items sort ahead; the tail stays sunk via the !radioSeedInFlight reset
                // gate). Accepted: bounded, self-healing, and never silence.
            } else {
                // LAP-COMPLETION probe: with the stale-skip below, the all-played self-reset and the
                // cycle-complete mark inside applyShuffleOrder would be unreachable in configs where the
                // exhaustion handoff above does not fire (repeat-all, continuation off) — shuffle would
                // simply never re-shuffle again. O(1) precheck first so the common mid-lap swap stays
                // scan-free; the O(N) confirm runs at most once per completed lap. REPEAT_ONE loops the
                // same song and needs no re-shuffle; radioSeedInFlight defers to the seed's own re-apply.
                if (!shuffleOrderStale &&
                    player.repeatMode != REPEAT_MODE_ONE &&
                    !radioSeedInFlight &&
                    shufflePlayedIds.size >= player.mediaItemCount &&
                    player.mediaItemCount > 0
                ) {
                    val allPlayed = (0 until player.mediaItemCount).all { i ->
                        runCatching { player.getMediaItemAt(i).mediaId }.getOrNull()?.let { it in shufflePlayedIds } != false
                    }
                    if (allPlayed) shuffleOrderStale = true
                }
                if (shuffleOrderStale) {
                    // Re-apply ONLY when something order-relevant actually changed (an append, a toggle,
                    // the DB seed landing, a manual SEEK, a completed lap). Re-running the full O(N)
                    // scoring + sort + spacing on the MAIN thread at EVERY song boundary — during the 5 s
                    // dual-player fade, with media3 then re-broadcasting the whole shuffle order to
                    // Android Auto over Binder — was the per-boundary burst car users heard as
                    // micro-stutters. Deferring the just-played sink is safe within a lap because the
                    // incoming player CARRIES the in-force curated order (see prepareSecondaryPlayer):
                    // every unplayed item stays ahead of the play head, so no-repeat holds going forward
                    // and the sink lands on the next real mutation.
                    shuffleOrderStale = false
                    val shufflePlaylistFirst = dataStore.get(ShufflePlaylistFirstKey, false)
                    applyShuffleOrder(player.currentMediaItemIndex, player.mediaItemCount, shufflePlaylistFirst)
                }
            }
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

        // QUEUE COPY — the secondary BECOMES the live player at the swap (performCrossfadeSwap does
        // `player = nextPlayer`), so it genuinely needs the WHOLE queue: everything the user can seek back
        // to and everything still to come. Preparing "only the next item" would destroy the queue once per
        // song. What CAN go is the per-item cost of reading it.
        //
        // ONE timeline read + ONE reusable Window, exactly as in shuffleItemKeys: `getMediaItemAt(i)` is
        // `getCurrentTimeline().getWindow(i, sharedWindow).mediaItem` and `mediaItemCount` is
        // `getCurrentTimeline().getWindowCount()` (both verified in the media3-common 1.10.1 bytecode), so
        // the old loop paid an application-thread check and a playbackInfo hop per item, N+1 times over,
        // on the Main thread, once per song, on a queue the infinite radio only grows. The ArrayList is
        // also pre-sized now: mutableListOf() started at capacity 10 and doubled its way up, which on a
        // four-figure queue is ~9 reallocations plus the array copies behind them.
        //
        // IDENTICAL RESULT: same MediaItem instances, same order, same count, handed to the same
        // setMediaItems call. Deliberately NOT wrapped in runCatching — today this loop has no catch
        // either, and swallowing a failure here would hand the secondary a PARTIAL queue that becomes the
        // live one at the swap. Reusing one Window is what media3 itself does across successive
        // getMediaItemAt calls; `.mediaItem` is a reference read, so the next getWindow cannot disturb it.
        val liveTimeline = player.currentTimeline
        val itemCount = liveTimeline.windowCount
        val copyWindow = Timeline.Window()
        val items = ArrayList<MediaItem>(itemCount)
        for (i in 0 until itemCount) {
            items.add(liveTimeline.getWindow(i, copyWindow).mediaItem)
        }
        sec.setMediaItems(items)
        // Stamp which live-timeline version this COPY mirrors — the reuse check in scheduleCrossfade
        // compares against it. Read BEFORE this function's own player reads complete; onTimelineChanged
        // runs on this same Main thread, so no mutation can interleave mid-copy.
        secondaryTimelineVersion = timelineVersion
        val incomingId = items.getOrNull(targetIndex)?.mediaId
        // PER-SONG INTRO MEMORY: start the incoming player right at its learned first audible frame — the
        // crossfade rise lands on real music instead of dead intro silence. No hint → 0 (exact old
        // behavior). The seek happens while this player is still muted and unstarted: inaudible.
        val leadSkipMs = incomingId?.let { leadSilenceHintMs[it] } ?: 0L
        sec.seekTo(targetIndex, leadSkipMs)
        sec.volume = 0f
        sec.repeatMode = player.repeatMode
        sec.shuffleModeEnabled = player.shuffleModeEnabled
        // CARRY THE IN-FORCE SHUFFLE ORDER ONTO THE SECONDARY. Without this, each secondary rolls media3's
        // OWN uniform-random permutation (setMediaItems + shuffleModeEnabled builds a fresh
        // DefaultShuffleOrder) — the curated order lives only inside the LIVE ExoPlayer object and dies
        // with it at the swap. The per-boundary re-apply used to repaint it microseconds after every swap,
        // which masked this; with the stale-skip in place nothing repaints it, and shuffle would degenerate
        // to memoryless random-with-replacement: repeats mid-lap, no artist spacing, premature radio
        // handoff — the exact bug class rows 90/92/94/96/101/102 exist to prevent. The walk is the same
        // pointer chase playNext uses; O(N), no scoring, no Binder re-broadcast (the secondary is not the
        // MediaSession player), and the copy is made microseconds after setMediaItems on this same Main
        // thread, so the length cannot mismatch.
        if (player.shuffleModeEnabled && items.isNotEmpty()) {
            runCatching {
                // The SAME Timeline instance the copy above walked — not a second `player.currentTimeline`
                // read. onTimelineChanged runs on this same Main thread, so nothing can have mutated
                // between the two, and sharing the reference makes that guarantee structural instead of a
                // comment. The order walk itself is unchanged, and so is the order it produces.
                val order = IntArray(items.size)
                var oi = 0
                var w = liveTimeline.getFirstWindowIndex(true)
                while (w != C.INDEX_UNSET && oi < order.size) {
                    order[oi++] = w
                    w = liveTimeline.getNextWindowIndex(w, Player.REPEAT_MODE_OFF, true)
                }
                if (oi == order.size) {
                    sec.setShuffleOrder(DefaultShuffleOrder(order, System.currentTimeMillis()))
                }
            }
        }
        // Carry the USER's playback settings across the swap. Speed/pitch and the chosen audio output are
        // set on the player object, not on a preference — so with crossfade ON (the default) every
        // auto-advance silently reset 1.25x/+2 semitones back to normal and dropped the selected output.
        sec.playbackParameters = player.playbackParameters
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            preferredDeviceId?.let { id ->
                runCatching {
                    audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
                        .find { it.id == id }
                        ?.let { sec.setPreferredAudioDevice(it) }
                }
            }
        }

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
                val makeup = dbToLinear(loudnessMakeupDb(loudnessDb, enabled = true))
                playerNormProcessors[sec]?.instanceGain = mult
                playerLimiterProcessors[sec]?.setInstanceMakeup(makeup, null)
                // Prime Safe Volume on the incoming player's live EQ processor so a loud track is attenuated
                // from the FIRST fade-in sample (else it swells in at full native level, then drops at swap).
                // MUST be the SAME full gain (attenuation x makeup) the main path applies when this track
                // becomes current — priming only the attenuate half would make a quiet track fade in lower
                // than it plays a moment later, i.e. an audible jump at the swap. No fade timing/curve here.
                if (safeVolumeEnabledHint) playerEqProcessors[sec]?.applySafeVolume(true, mult * makeup)
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
                loudnessHintCache[incomingId] = loudnessDb
                withContext(Dispatchers.Main) {
                    if (secondaryPlayer === sec || (player === sec && isCrossfading)) {
                        val mult = normalizationMultiplier(loudnessDb, enabled = true)
                        val makeup = dbToLinear(loudnessMakeupDb(loudnessDb, enabled = true))
                        playerNormProcessors[sec]?.instanceGain = mult
                        playerLimiterProcessors[sec]?.setInstanceMakeup(makeup, null)
                        if (safeVolumeEnabledHint) playerEqProcessors[sec]?.applySafeVolume(true, mult * makeup)
                        if (player === sec) {
                            lastAppliedGain = mult
                            lastAppliedMakeup = makeup
                            lastNormalizedId = incomingId
                        }
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
        // Bookkeeping only — no fade math, curve or duration is touched here. This callback-skipping path is
        // also the ONLY writer gap for currentPlayingMediaId (:3548 in onMediaItemTransition is the other, and
        // the only one): without this, after ANY swap the field still names the OUTGOING track for the whole of
        // the incoming one, so the resolver's `isCurrentlyPlaying` is false while that track plays. Its single
        // reader then falls back to the GLOBAL quality, the container guard compares that against a dbFormat
        // describing a fallback container, mismatches, and purges the playing track's cached bytes on every
        // re-open. With crossfade ON (the default here) every advance is a swap, so that was permanent.
        currentPlayingMediaId = nextPlayer.currentMediaItem?.mediaId
        // The UI's song identity ALSO only has two writers, and the other one lives in onEvents behind
        // TIMELINE_CHANGED/POSITION_DISCONTINUITY — neither fires for a swap, and the service listener is
        // attached to the incoming player only further down (after its own transition already happened).
        // Without this, with crossfade ON the widget/notification/Android-Auto kept showing the PREVIOUS
        // song's title, artist, artwork and like state while the progress bar advanced against the new one.
        nextPlayer.currentMetadata?.let { currentMediaMetadata.value = it }
        val currentPlayer = player

        // LEARN the outgoing track's intro silence (finalized at its first loud frame): next time this
        // song ENTERS a crossfade, the incoming player starts right at its music — the rise is heard over
        // real audio instead of dead intro air. Undercount-safe by construction (frames before arming are
        // simply not counted), so a stored skip can never eat music.
        playerSilenceProcessors[currentPlayer]?.leadingSilenceUsOrNegative()?.takeIf { it >= 0 }?.let { us ->
            val ms = us / 1_000L
            val id = currentPlayer.currentMediaItem?.mediaId
            if (id != null && id == tailArmedMediaId && leadHintTrustedForArmedTrack && ms in 1_000..20_000) {
                if (leadSilenceHintMs.size > 400) leadSilenceHintMs.clear()
                leadSilenceHintMs[id] = ms
            }
        }

        fadingPlayer = currentPlayer
        // Observation-only, for the lyrics view: this is the track the user KEEPS HEARING for the length of
        // the fade even though the incoming one was published above. Recorded here so the lyrics can stay on
        // it (and on its clock) until cleanupCrossfade commits. Null metadata simply leaves the override off,
        // i.e. the lyrics behave exactly as they did before. Nothing below this line changes.
        _crossfadeOutgoingMetadata.value = currentPlayer.currentMetadata
        // Pin the OUTGOING player to its current normalization (the companion statics still hold its
        // values right now) so when setupLoudnessEnhancer re-writes them for the incoming track, the
        // fading player keeps its own level instead of "pumping" to the new track's gain.
        playerNormProcessors[currentPlayer]?.instanceGain = NormalizationGainAudioProcessor.gain
        playerLimiterProcessors[currentPlayer]?.setInstanceMakeup(TruePeakLimiterAudioProcessor.loudnessMakeup, null)
        player = nextPlayer
        _playerFlow.value = player
        currentEqProcessor = playerEqProcessors[nextPlayer]
        val incomingIdNow = nextPlayer.currentMediaItem?.mediaId
        if (incomingIdNow != null && (normalizationEnabledHint || safeVolumeEnabledHint)) {
            loudnessHintCache[incomingIdNow]?.let { ld ->
                lastAppliedGain = normalizationMultiplier(ld, enabled = true)
                lastAppliedMakeup = dbToLinear(loudnessMakeupDb(ld, enabled = true))
            }
            lastNormalizedId = incomingIdNow
        }
        secondaryPlayer = null

        fadingPlayer?.removeListener(this)

        // Stop the outgoing player from auto-advancing into the NEXT track as it fades out. It still
        // holds the full queue, so when the current song ends mid-fade it would start the next song —
        // which the incoming player is ALSO playing → "the next track plays twice at once" at the start
        // of the transition.
        //
        // MUST NOT mutate the fading playlist (removeMediaItems/clearMediaItems): that races media3's
        // evaluateMediaItemTransitionReason and throws a bare IllegalStateException on the main Handler
        // (CRASH_REPORTS #2 + #5 — Xiaomi users mid-playlist with crossfade ON). pauseAtEndOfMediaItems
        // parks the player at EOS without touching the timeline; release() in cleanupCrossfade frees it.
        try {
            fadingPlayer?.let { fp ->
                fp.repeatMode = androidx.media3.common.Player.REPEAT_MODE_OFF
                fp.pauseAtEndOfMediaItems = true
            }
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "crossfade: failed to park fading player at end of item")
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


        try {
            (mediaSession as MediaSession).player = player
        } catch (e: Exception) {
            timber.log.Timber.e(e, "Failed to swap player in MediaSession")
        }

        crossfadeJob = scope.launch {
            val configured = crossfadeDuration.toLong()
            // OUTGOING decay must COMPLETE within the fading track's audible life (owner: "la que sale
            // NO baja"): a fade that starts late — dynamic ready-wait, tail fire near the end — used to
            // run its full configured length, so the outgoing's file ENDED while its ramp was still near
            // full volume: heard as no decay at all. Cap its ramp to the actual remaining time.
            val fpRemaining = fadingPlayer?.let { fp ->
                val d = fp.duration
                if (d == C.TIME_UNSET) Long.MAX_VALUE else (d - fp.currentPosition).coerceAtLeast(0L)
            } ?: Long.MAX_VALUE
            val durOut = minOf(configured, (fpRemaining - 250L).coerceAtLeast(600L))
            val durIn = configured
            val curve = try { dataStore.get(CrossfadeCurveKey, 4) } catch (e: Exception) { 4 }
            val startVolume = try { fadingPlayer?.volume ?: 1f } catch(e:Exception) { 1f }
            // Because LUFS Normalization is fixed and active, tracks play at roughly -14 LUFS,
            // leaving massive natural headroom. Thus, two tracks summing during an equal-power crossfade
            // will NEVER clip the Android mixer (they'll sum to ~-11 LUFS). We can safely remove the
            // old volume dip hack and keep the multiplier at 1.0f for a perfectly transparent blend.
            val xfHeadroom = 1f

            try {
                // DUAL-CLOCK blend. The old single stepped loop had a `while (!player.isPlaying) delay`
                // that FROZE the whole fade while the incoming track buffered its start (routine on
                // streamed/Lossless songs) — the outgoing sat pinned at full volume for those seconds and
                // then died with its ramp barely begun: the owner's exact "la que entra está bien pero la
                // que sale no baja". Now each side runs on ITS OWN playback clock:
                //  • OUTGOING advances only while the fading player actually renders → its decay is always
                //    audible, always completes before its content ends, freezes correctly on user pause;
                //  • INCOMING advances only while the new player renders → a buffering start can neither
                //    freeze the outgoing nor slam the incoming in at mid-level.
                var outElapsed = 0L
                var inElapsed = 0L
                var lastT = android.os.SystemClock.elapsedRealtime()
                var safety = 0L
                while (isActive) {
                    val now = android.os.SystemClock.elapsedRealtime()
                    val dt = (now - lastT).coerceAtLeast(0L)
                    lastT = now
                    safety += dt
                    val fp = fadingPlayer
                    if (fp?.isPlaying == true) outElapsed += dt
                    if (player.isPlaying) inElapsed += dt
                    // Outgoing counts as fully faded when it's gone (null/ended) — never stalls the loop.
                    val outDone = fp == null || fp.playbackState == Player.STATE_ENDED
                    val outP = if (outDone) 1f else (outElapsed / durOut.toFloat()).coerceAtMost(1f)
                    val inP = (inElapsed / durIn.toFloat()).coerceAtMost(1f)
                    val fadeIn = crossfadeGains(curve, inP).first
                    val fadeOut = crossfadeGains(curve, outP).second

                    try {
                        // Both players smoothly fade without needing to dynamically duck their headroom
                        player.volume = startVolume * fadeIn * xfHeadroom
                        fp?.volume = startVolume * fadeOut * xfHeadroom
                    } catch (e: Exception) { break }

                    if (inP >= 1f && outP >= 1f) break
                    if (safety > durIn + durOut + 30_000L) break // pathological stall — bail to cleanup
                    delay(40)
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
        // Incoming track is already the audible one. Do NOT clear lastNormalizedId: that disarmed
        // the freeze for the rest of the song, so liking it (auto-download) re-levelled mid-play.
        // Refine the per-song tail memory with the EXACT end-of-stream measurement when the decoder
        // reached EOS (it runs ahead of the playback clock, so this is usually available even though the
        // silent tail itself never audibly played). Read BEFORE stop() — duration/item may reset after.
        fadingPlayer?.let { fp ->
            val trailingUs = playerSilenceProcessors[fp]?.trailingSilenceUsOrNegative() ?: -1L
            if (trailingUs >= 0) {
                runCatching { storeTailHint(fp.currentMediaItem?.mediaId, trailingUs / 1_000L, fp.duration) }
            }
        }
        fadingPlayer?.stop()
        // NO clearMediaItems: this teardown fires at fade end — often the exact moment the outgoing
        // player's own content ENDS (the 0.6.133 durOut cap makes that overlap routine). A playlist
        // mutation landing while its transition machinery evaluates the ended/auto transition hits
        // media3's bare "impossible state" IllegalStateException in evaluateMediaItemTransitionReason
        // (retraced client crash, CRASH_REPORTS #2). release() below frees everything anyway.
        fadingPlayer?.let {
            // Bookkeeping only — no fade math touched. Silence was the one map this teardown forgot, so every
            // crossfade left a dead entry holding a released ExoPlayer for the whole session.
            playerSilenceProcessors.remove(it)
            playerNormProcessors.remove(it)
            playerLimiterProcessors.remove(it)
            playerEqProcessors.remove(it)?.let { eq -> equalizerService.removeAudioProcessor(eq) }
        }
        fadingPlayer?.release()
        fadingPlayer = null
        isCrossfading = false
        _isCrossfading.value = false // observation-only mirror for the UI; does not alter the swap
        // The fade committed: the incoming track is now the audible one, so the lyrics view stops following
        // the outgoing song and returns to the live one. Observation-only, like the mirror above.
        _crossfadeOutgoingMetadata.value = null
        // Collect the quality-change survivor here rather than at the swap: the fade is over and fadingPlayer is
        // already stopped/cleared/released above, so dropping its URL entry cannot trigger a re-open. Needed
        // because performCrossfadeSwap skips onMediaItemTransition, and with crossfade ON every advance is a
        // swap — so the transition-based collector would never run and the pin would outlive its track.
        qualityPinnedMediaId?.let { pinned ->
            if (pinned != player.currentMediaItem?.mediaId) {
                songUrlCache.remove(pinned)
                qualityPinnedMediaId = null
                persistSongUrlCache()
            }
        }
    }

    companion object {
        // ConcurrentHashMap: read/written from both Main (applyVideoToCurrent, onPlayerError), Dispatchers.IO
        // (prebuildNextVideoItem) and DownloadUtil.
        internal val videoUrlCache = java.util.concurrent.ConcurrentHashMap<String, Pair<String, Long>>()

        /**
         * How many artists a single autoplay continuation may look a genre up for. Small on purpose: it is
         * WiFi-only, off the playback path, and misses are cached, so coverage fills in over a few songs
         * without ever turning the radio into a burst of network work.
         */
        private const val GENRE_LEARN_PER_RUN = 12

        /**
         * How long a radio batch may WAIT for genre enrichment before it is scored.
         *
         * Root cause of the owner's "la cola inteligente me mezcla géneros": enrichment ran
         * fire-and-forget AFTER the batch was already queued, so the batch that was actually SCORED saw
         * the cache as it was BEFORE it learned anything about those artists — they scored as unknown
         * and rode YouTube's raw relatedness in. The NEXT batch, enriched by the previous run, came out
         * right. Right / wrong / right / wrong, exactly as reported.
         *
         * Bounded, fail-open and NEVER a source of silence:
         *  - the enrich is launched on the SERVICE scope and only the WAIT is timed out, so a timeout
         *    costs nothing — the run continues and still persists, i.e. today's exact behaviour;
         *  - both call sites hold a live playback buffer when they fire (the finishing song is still
         *    playing / at least 5 items remain in playback order), so 1.5 s is inaudible;
         *  - the one case with NO buffer — a true end-of-queue with the player parked waiting for these
         *    very items (resumeAfterSeed) — skips the wait entirely.
         */
        private const val ENRICH_BEFORE_SCORE_MS = 1500L

        const val ROOT = "root"
        const val SONG = "song"
        const val ARTIST = "artist"
        const val ALBUM = "album"
        const val PLAYLIST = "playlist"
        const val YOUTUBE_PLAYLIST = "youtube_playlist"
        const val SEARCH = "search"
        const val SHUFFLE_ACTION = "__shuffle__"

        /** How long an external controller's shuffle request stays armed before it is discarded. */
        private const val EXTERNAL_SHUFFLE_ARM_TIMEOUT_MS = 15_000L

        /**
         * How long the context-adoption latch stays valid. Covers the gap between replacing the timeline
         * and adopting the new context (a synchronous hop in the local case, one network fetch in the
         * worst case) and then expires, so an abandoned playQueue can never disable recording for good.
         */
        private const val CONTEXT_ADOPTION_WINDOW_MS = 20_000L

        // Refetch: sent by the song menus to drop a song's cached stream URL + bytes (see clearSongCache).
        const val ACTION_CLEAR_SONG_CACHE = "iad1tya.echo.music.ACTION_CLEAR_SONG_CACHE"
        const val EXTRA_SONG_ID = "songId"

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
        // 15s gives slow Lossless/resolve paths enough runway to reach READY before the fade window.
        private const val CROSSFADE_PRELOAD_LEAD_MS = 15000L
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
        // competes with the running track's own startup buffering. 1200 ms is enough once the URL is warm
        // without waiting a full 2.5 s of dead air before the speculative path can help.
        private const val INSTANT_VIDEO_PREPARE_DELAY_MS = 1200L
        // Never pre-prepare inside this margin of the crossfade preload moment (preload lead + margin):
        // guarantees the video pre-player and the crossfade secondary never race to exist at once.
        private const val INSTANT_VIDEO_CROSSFADE_MARGIN_MS = 3000L
        // The live position must be at least this far inside the pre-player's buffered window at swap
        // time, or we fall back to the normal path instead of publishing a player about to rebuffer.
        private const val INSTANT_VIDEO_MIN_BUFFER_AHEAD_MS = 1500L
        /** Bounded init-segment warm before audio→video toggle (unmetered + capable only). */
        private const val VIDEO_WARM_BYTES = 768L * 1024L

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
            // DATA SAVER: skip the speculative upcoming-track preload entirely — the next track
            // resolves on demand instead of ahead of time (same shape as the battery-saver skip below).
            if (dataStore.get(iad1tya.echo.music.constants.DataSaverEnabledKey, false)) {
                Timber.tag(TAG).d("Preload skipped: data saver is on")
                return@launch
            }
            // Battery saver used to SKIP all preload — but with crossfade still armed that produced
            // CROSSFADE_TRACE cut-not-ready (~4s silence) on Android Auto (owner share_log-3). Keep a
            // LIGHT url-only prefetch of the next track so hard cuts (crossfade is also gated off under
            // saver) start without a resolve stall. Extras (loudness/lyrics) stay off.
            val powerSave = (getSystemService(POWER_SERVICE) as? android.os.PowerManager)?.isPowerSaveMode == true
            if (powerSave) {
                Timber.tag(TAG).d("Preload: battery saver — url-only next track (extras skipped)")
            }
            // High-Performance Mode: DON'T skip entirely. Keep a LIGHT, url-only prefetch (resolve + cache the
            // stream URL so the first frame starts fast on low-end/TV/car too); only the heavier per-song
            // extras (loudness/format DB caching + lyrics) are gated OFF below. Battery Saver uses the same
            // light path (see powerSave above).
            val perfMode = iad1tya.echo.music.utils.PerformanceMode.isOn(this@MusicService)
            if (perfMode && !powerSave) {
                Timber.tag(TAG).d("Preload: high-performance mode — url-only (extras skipped)")
            }
            val urlOnlyPreload = powerSave || perfMode
            // Default 2 (was 1) so the very next song is ready even while the current one is still resolving;
            // under battery saver / Auto: just the next 1 URL to limit radio wakeups.
            val preloadLimit = if (powerSave) 1
            else dataStore.get(iad1tya.echo.music.constants.PreloadNextSongLimitKey, 2)
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
                        
                        // Capture the quality ONCE: it is both the resolve argument and the value stamped into the
                        // cache below, and a concurrent quality change must not make those two disagree.
                        val resolveQuality = audioQuality
                        val playbackData = iad1tya.echo.music.utils.YTPlayerUtils.playerResponseForPlayback(
                            videoId = mediaId,
                            audioQuality = resolveQuality,
                            connectivityManager = connectivityManager,
                            context = this@MusicService,
                            knownArtist = knownArtist,
                            knownTitle = dbSong?.song?.title,
                            knownDurationMs = dbSong?.song?.duration?.let { if (it > 0) it * 1000L else null }
                        )

                        playbackData.getOrNull()?.let { data ->
                            // Mirror the main resolver's TTL: honour the real stream expiry instead of a
                            // hardcoded 1h (a googlevideo URL can expire sooner and would then 403).
                            // Prefetch always REQUESTS the global quality, but stamp what was DELIVERED (same
                            // predicate as the resolver + the container guard): a prefetched track that fell back
                            // must not carry a wrong pin into the persisted blob or into the guard's comparison.
                            val preMime = data.format.mimeType
                            val preQuality = when {
                                preMime.contains("flac", ignoreCase = true) -> iad1tya.echo.music.constants.AudioQuality.LOSSLESS
                                preMime.contains("mp4", ignoreCase = true) || preMime.contains("m4a", ignoreCase = true) ->
                                    iad1tya.echo.music.constants.AudioQuality.SAAVN
                                else -> iad1tya.echo.music.constants.AudioQuality.OPUS
                            }
                            songUrlCache[mediaId] = CachedStream(
                                url = data.streamUrl,
                                expiresAt = System.currentTimeMillis() + (data.streamExpiresInSeconds * 1000L),
                                delivered = preQuality,
                                requested = resolveQuality,
                            )
                            Timber.tag(TAG).d("Preloaded stream for $mediaId")

                            // FIX A: cache the loudness (FormatEntity) for the UPCOMING track NOW, so when it
                            // transitions to playing, setupLoudnessEnhancer finds a non-null format and primes the
                            // correct gain at second 0 — no audible volume swell. Mirrors the resolver's
                            // FormatEntity construction exactly. Preserve any existing row's loudness: only fill
                            // when missing, never overwrite a known loudness with null.
                            // Gated OFF in High-Performance Mode / battery saver (url-only prefetch there).
                            if (!urlOnlyPreload) kotlin.runCatching {
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
                                                // Derive the codec safely. split("codecs=")[1] threw IndexOutOfBounds for a
                            // mimeType with no codecs parameter; and an empty codec reads back as OPUS, which
                            // makes the format guard mis-fire on EVERY open for a LOSSLESS/SAAVN user — the
                            // #57 mechanism. Fall back to the container, then to the row we already have.
                            codecs = codecsFromMimeType(format.mimeType, existing?.codecs),
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

                if (preloadLyrics && !urlOnlyPreload) {
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
                                    // The provider MUST be recorded. Omitting it let the column fall
                                    // back to "Unknown", which erased the provenance of every
                                    // preloaded row and hid real LrcLib results behind an
                                    // unattributable label.
                                    upsert(
                                        iad1tya.echo.music.db.entities.LyricsEntity(
                                            id = mediaId,
                                            lyrics = lyricsResult.lyrics,
                                            provider = lyricsResult.provider,
                                        ),
                                    )
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

/**
 * The enhanced shuffle's CYCLE decisions, as pure functions: when a list counts as FINISHED, and when its
 * per-list no-repeat memory may be reset. No Android, no media3, no player — so they can be unit-tested.
 *
 * This class of bug ("the shuffle repeats") has now been fixed seven times, and the reason it kept coming
 * back is that the decision lived inline in [MusicService], tangled with the live player, where no test
 * could reach it. Ordering already moved out to [ShuffleOrdering] for the same reason; this is the other
 * half — the *when*, not the *order*.
 *
 * The two facts these functions keep apart:
 *  - COVERAGE: how many items the CONTEXT (the list the user opened) actually loaded. Used to be read off
 *    the radio seed pool, which is a different fact that merely lived in the same field: it is written by
 *    one code path only, is empty for a queue handed over by Android Auto, and can hold the size of a
 *    completely different list. Judging "everything played" against the LIVE TIMELINE instead of the
 *    context is what let a shrinking queue finish a lap it never played.
 *  - COMPLETION: every song of the context is in the played set. Completion hands the queue to the
 *    infinite radio; it does NOT by itself reset the memory — that needs the user to re-activate shuffle.
 */
object EnhancedShuffleCycle {

    /** Coverage is unknown: judge by the timeline alone (see [coversContext] for why not "not finished"). */
    const val COVERAGE_UNKNOWN = 0

    /**
     * Coverage only counts when it describes THIS context. A size measured for another list is not a
     * weaker signal, it is a wrong one: it made a 12-song car queue impossible to finish against an
     * 80-song playlist measured minutes earlier, and it made an 80-song list finish against a 4-track EP.
     */
    fun coverageOf(contextId: String?, coverageContextId: String?, coverageSize: Int): Int =
        if (contextId != null && contextId == coverageContextId && coverageSize > 0) coverageSize
        else COVERAGE_UNKNOWN

    /**
     * Does the live timeline still cover the whole context?
     *
     * UNKNOWN coverage answers YES on purpose. "If we don't know, report not finished" looks like the safe
     * side and is strictly worse: the very same reading decides the handoff to the infinite radio, so a
     * permanent "not finished" means the list can never end, the radio never takes over, and the queue
     * loops re-shuffling songs already heard — the exact complaint this feature exists to prevent.
     */
    fun coversContext(timelineSize: Int, coverageSize: Int): Boolean =
        coverageSize <= COVERAGE_UNKNOWN || timelineSize >= coverageSize

    /**
     * Has this list been played to the end? Every id must be known AND already played, and the pool being
     * judged must still cover the context.
     *
     * [idAt] is an accessor rather than a list so the hot path (every auto-advance, and with crossfade ON
     * that is every song) can read straight from the player's timeline without copying thousands of ids.
     * An id that cannot be read answers "not finished" — an unreadable item is never proof of completion.
     */
    inline fun isCycleComplete(
        timelineSize: Int,
        coverageSize: Int,
        playedIds: Set<String>,
        idAt: (Int) -> String?,
    ): Boolean {
        if (timelineSize <= 0) return false
        if (!coversContext(timelineSize, coverageSize)) return false
        for (i in 0 until timelineSize) {
            val id = idAt(i) ?: return false
            if (id !in playedIds) return false
        }
        return true
    }

    /**
     * The owner's rule, in one line: "lo que ya se reprodujo de la lista no se vuelva a repetir A MENOS QUE
     * ya se haya finalizado la reproducción de esa lista Y el usuario vuelva a activar el aleatorio".
     *
     * TWO conditions. Finishing the list alone must not reset anything — the memory has to still be there
     * when he comes back to that list later. The reset belongs to the moment he turns shuffle on again.
     */
    fun shouldResetForNewCycle(isUserActivation: Boolean, cycleComplete: Boolean): Boolean =
        isUserActivation && cycleComplete
}
