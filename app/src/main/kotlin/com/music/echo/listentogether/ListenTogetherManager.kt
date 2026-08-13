

package iad1tya.echo.music.listentogether

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import com.music.innertube.YouTube
import com.music.innertube.models.WatchEndpoint
import iad1tya.echo.music.constants.ListenTogetherSmartResyncKey
import iad1tya.echo.music.constants.ListenTogetherSyncVolumeKey
import iad1tya.echo.music.extensions.currentMetadata
import iad1tya.echo.music.extensions.metadata
import iad1tya.echo.music.extensions.toMediaItem
import iad1tya.echo.music.models.MediaMetadata
import iad1tya.echo.music.models.MediaMetadata.Album
import iad1tya.echo.music.models.MediaMetadata.Artist
import iad1tya.echo.music.models.toMediaMetadata
import iad1tya.echo.music.playback.PlayerConnection
import iad1tya.echo.music.playback.queues.YouTubeQueue
import iad1tya.echo.music.utils.dataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton


@Singleton
class ListenTogetherManager @Inject constructor(
    private val client: ListenTogetherClient,
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "ListenTogetherManager"

        /**
         * Diagnostic tag for every room publish and every room apply. Emitted at Timber INFO because
         * AppLogger only persists >= INFO, so a desync can be read off a shared log instead of guessed
         * at. NEVER carries the session token, the server URL, the room code or a username — only media
         * ids, queue indices, positions and wall-clock deltas.
         */
        private const val TRACE_TAG = "LISTEN_TOGETHER"

        /**
         * Android's (undocumented but universal) system broadcast fired whenever a stream's volume
         * changes — including from the hardware buttons. Protected system broadcast, so no export flag
         * is needed. The player screen already observes it the same way.
         */
        private const val VOLUME_CHANGED_ACTION = "android.media.VOLUME_CHANGED_ACTION"


        private const val SYNC_DEBOUNCE_THRESHOLD_MS = 1000L

        // Independent YouTube streams cannot be sample-accurate. 2–3 s was treated as "in sync",
        // which is why two phones sounded like an echo. Correct when the gap is still audible
        // but don't seek on every 50 ms of decoder jitter.
        private const val POSITION_TOLERANCE_MS = 400L

        private const val PLAYBACK_POSITION_TOLERANCE_MS = 600L


        private const val BUFFER_WAIT_TIMEOUT_MS = 8000L
    }

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    /**
     * One line per publish (host) and per apply (guest): what track, where in the queue, at what
     * position, and how stale the data was by the time it was used. `deltaMs` is the wall-clock gap
     * between capture and use measured on ONE device's clock, so it is the buffer/plumbing lag only —
     * host->guest network latency is not in it (see the serverTime note in [handlePlaybackSync]).
     */
    private fun trace(
        phase: String,
        action: String,
        mediaId: String?,
        queueIndex: Int,
        positionMs: Long,
        deltaMs: Long,
        extra: String = "",
    ) {
        Timber.tag(TRACE_TAG).i(
            "%s %s id=%s idx=%d pos=%dms delta=%dms%s",
            phase,
            action,
            mediaId ?: "-",
            queueIndex,
            positionMs,
            deltaMs,
            if (extra.isEmpty()) "" else " $extra",
        )
    }

    private fun safeQueueIndex(player: Player?): Int =
        try {
            player?.currentMediaItemIndex ?: -1
        } catch (e: Exception) {
            -1
        }

    private fun safePosition(player: Player?): Long =
        try {
            player?.currentPosition ?: -1L
        } catch (e: Exception) {
            -1L
        }

    init {
        initialize()
        observePreferences()
    }
    
    private var playerConnection: PlayerConnection? = null
    private var eventCollectorJob: Job? = null
    private var roleCollectorJob: Job? = null
    private var queueObserverJob: Job? = null
    private var volumeObserverJob: Job? = null
    private var playerSwapObserverJob: Job? = null
    private var playerListenerRegistered = false

    /**
     * The exact ExoPlayer instance [playerListener] is attached to. MusicService REPLACES its player
     * object on a crossfade swap (performCrossfadeSwap) and on the instant video swap, and neither path
     * fires onMediaItemTransition — so a listener attached once by instance is silently orphaned onto a
     * player that is about to be released, and the host stops publishing anything at all. Tracked here so
     * [observePlayerSwaps] can move the listener the moment the service republishes.
     */
    private var listenerAttachedTo: Player? = null

    private val syncHostVolumeEnabled = MutableStateFlow(true)
    private val smartResyncEnabled = MutableStateFlow(true)
    private var lastSyncedVolume: Float? = null
    private var previousMuteState: Boolean? = null
    private var muteForcedByPreference = false

    /**
     * The guest's OWN app volume, saved when it joins and restored when it leaves. Without this a guest
     * that followed a quiet host keeps that attenuation forever: MusicService persists playerVolume to
     * the datastore a second after it changes, so the host's level silently becomes the guest's new
     * default for solo listening too.
     */
    private var previousPlayerVolume: Float? = null

    /**
     * When the guest last applied an ABSOLUTE track change (CHANGE_TRACK / full state). A relative
     * SKIP_NEXT / SKIP_PREV arriving right behind one is the second half of a double-publish and must be
     * dropped, or the guest advances twice. See [ListenTogetherSync.shouldApplyRelativeSkip].
     */
    private var lastTrackChangeAppliedAt: Long = 0L

    private var lastRole: RoomRole = RoomRole.NONE
    
    
    @Volatile
    private var isSyncing = false
    
    
    private var lastSyncedIsPlaying: Boolean? = null
    private var lastSyncedTrackId: String? = null
    
    
    private var lastSyncActionTime: Long = 0L
    
    
    private var bufferingTrackId: String? = null
    
    
    private var activeSyncJob: Job? = null
    
    
    
    private var currentTrackGeneration: Int = 0

    
    private var pendingSyncState: SyncStatePayload? = null

    
    private var bufferCompleteReceivedForTrack: String? = null

    
    val connectionState = client.connectionState
    val roomState = client.roomState
    val role = client.role
    val userId = client.userId
    val pendingJoinRequests = client.pendingJoinRequests
    val bufferingUsers = client.bufferingUsers
    val logs = client.logs
    val events = client.events
    val blockedUsernames = client.blockedUsernames
    val pendingSuggestions = client.pendingSuggestions

    val isInRoom: Boolean get() = client.isInRoom
    val isHost: Boolean get() = client.isHost
    val hasPersistedSession: Boolean get() = client.hasPersistedSession
    
    
    private val _chatMessages = MutableStateFlow<List<ChatMessagePayload>>(emptyList())
    val chatMessages = _chatMessages
    
    private val playerListener = object : Player.Listener {
        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            try {
                if (isSyncing || !isHost || !isInRoom) return
                
                val connection = playerConnection ?: return
                val player = connection.player

                Timber.tag(TAG).d("Play state changed: $playWhenReady (reason: $reason)")
                
                
                val currentTrackId = player.currentMediaItem?.mediaId
                if (currentTrackId != null && currentTrackId != lastSyncedTrackId) {
                    Timber.tag(TAG)
                        .d("[SYNC] Sending track change before play state: track = $currentTrackId")
                    player.currentMetadata?.let { metadata ->
                        sendTrackChangeInternal(metadata)
                        lastSyncedTrackId = currentTrackId
                        
                        lastSyncedIsPlaying = false
                    }
                    
                    
                    if (playWhenReady) {
                        Timber.tag(TAG).d("[SYNC] Host is playing, sending PLAY after track change")
                        lastSyncedIsPlaying = true
                        val position = player.currentPosition
                        client.sendPlaybackAction(PlaybackActions.PLAY, position = position)
                    }
                    return
                }
                
                
                sendPlayState(playWhenReady, player)
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "Error in onPlayWhenReadyChanged")
            }
        }
        
        private fun sendPlayState(playWhenReady: Boolean, player: Player) {
            try {
                val position = player.currentPosition
                
                if (playWhenReady) {
                    Timber.tag(TAG).d("Host sending PLAY at position $position")
                    trace("PUBLISH", "PLAY", player.currentMediaItem?.mediaId, safeQueueIndex(player), position, 0L)
                    client.sendPlaybackAction(PlaybackActions.PLAY, position = position)
                    lastSyncedIsPlaying = true
                } else if (!playWhenReady && (lastSyncedIsPlaying == true)) {
                    Timber.tag(TAG).d("Host sending PAUSE at position $position")
                    trace("PUBLISH", "PAUSE", player.currentMediaItem?.mediaId, safeQueueIndex(player), position, 0L)
                    client.sendPlaybackAction(PlaybackActions.PAUSE, position = position)
                    lastSyncedIsPlaying = false
                }
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "Error in sendPlayState")
            }
        }
        
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            try {
                if (isSyncing || !isHost || !isInRoom) return
                if (mediaItem == null) return
                
                val connection = playerConnection ?: return
                val player = connection.player
                
                val trackId = mediaItem.mediaId
                if (trackId == lastSyncedTrackId) return
                
                lastSyncedTrackId = trackId

                lastSyncedIsPlaying = false


                player.currentMetadata?.let { metadata ->
                    Timber.tag(TAG).d("Host sending track change: ${metadata.title}")
                    trace(
                        "PUBLISH", "CHANGE_TRACK", trackId,
                        safeQueueIndex(player), safePosition(player), 0L,
                        "reason=$reason",
                    )
                    sendTrackChange(metadata)



                    val isPlaying = player.playWhenReady
                    if (isPlaying) {
                        Timber.tag(TAG).d("Host is playing during track change, sending PLAY")
                        lastSyncedIsPlaying = true
                        val position = player.currentPosition
                        trace("PUBLISH", "PLAY", trackId, safeQueueIndex(player), position, 0L)
                        client.sendPlaybackAction(PlaybackActions.PLAY, position = position)
                    }
                }
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "Error in onMediaItemTransition")
            }
        }

        override fun onPositionDiscontinuity(
            oldPosition: Player.PositionInfo,
            newPosition: Player.PositionInfo,
            reason: Int
        ) {
            try {
                if (isSyncing || !isHost || !isInRoom) return


                if (reason == Player.DISCONTINUITY_REASON_SEEK) {
                    // A seek that CROSSES media items (skip next/previous, tapping another queue row) is
                    // already fully described by the CHANGE_TRACK that onMediaItemTransition published for
                    // the destination — that message carries the track identity, the queue and position 0.
                    // Publishing a bare SEEK for it too made the guest seek its still-current OLD track to
                    // the new track's position before the track change landed. Only WITHIN-track seeks are
                    // meaningful as a relative SEEK.
                    if (oldPosition.mediaItemIndex != newPosition.mediaItemIndex) {
                        Timber.tag(TAG).d("Skipping SEEK publish: discontinuity crossed media items")
                        return
                    }
                    Timber.tag(TAG).d("Host sending SEEK to ${newPosition.positionMs}")
                    trace(
                        "PUBLISH", "SEEK", newPosition.mediaItem?.mediaId ?: lastSyncedTrackId,
                        newPosition.mediaItemIndex, newPosition.positionMs, 0L,
                    )
                    client.sendPlaybackAction(PlaybackActions.SEEK, position = newPosition.positionMs)
                }
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "Error in onPositionDiscontinuity")
            }
        }
    }

    /**
     * Attach [playerListener] to the service's CURRENT player instance, moving it if the service has
     * swapped players since last time. Idempotent.
     */
    private fun attachPlayerListener() {
        val player = try {
            playerConnection?.player
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Failed to read player for listener attach")
            null
        } ?: return
        if (playerListenerRegistered && listenerAttachedTo === player) return
        try {
            listenerAttachedTo?.takeIf { it !== player }?.removeListener(playerListener)
            player.addListener(playerListener)
            listenerAttachedTo = player
            playerListenerRegistered = true
            Timber.tag(TAG).d("Player listener attached to $player")
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Failed to attach player listener")
            playerListenerRegistered = false
            listenerAttachedTo = null
        }
    }

    /** Detach [playerListener] from whichever instance currently holds it. Idempotent. */
    private fun detachPlayerListener() {
        try {
            listenerAttachedTo?.removeListener(playerListener)
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error removing player listener")
        }
        listenerAttachedTo = null
        playerListenerRegistered = false
    }

    /**
     * Follow MusicService's player swaps. A crossfade swap (performCrossfadeSwap) and the instant video
     * swap both replace the player object and republish `playerFlow` WITHOUT firing
     * onMediaItemTransition — the callback-skipping path this codebase has been bitten by repeatedly. A
     * listener bound to the old instance would then be attached to a player that is about to be released,
     * so the host would go permanently silent: no track changes, no play/pause, no seeks.
     *
     * Crossfade is force-disabled while a room exists (MusicService gates crossfadeEnabled on
     * roomState == null), but a swap already scheduled when the room is created still commits — and the
     * video swap is not gated at all. Following the flow makes the wiring correct regardless.
     */
    private fun observePlayerSwaps() {
        if (playerSwapObserverJob?.isActive == true) return
        val connection = playerConnection ?: return
        playerSwapObserverJob = scope.launch {
            connection.service.playerFlow.collect { newPlayer ->
                if (newPlayer == null) return@collect
                if (playerConnection !== connection) return@collect
                if (!isInRoom || !isHost) return@collect
                if (listenerAttachedTo === newPlayer) return@collect
                Timber.tag(TAG).d("Service swapped player instance - moving room listener")
                trace(
                    "PUBLISH", "PLAYER_SWAP", newPlayer.currentMediaItem?.mediaId,
                    safeQueueIndex(newPlayer), safePosition(newPlayer), 0L,
                    "listener re-attached",
                )
                attachPlayerListener()
            }
        }
    }

    private fun stopPlayerSwapObservation() {
        playerSwapObserverJob?.cancel()
        playerSwapObserverJob = null
    }


    fun setPlayerConnection(connection: PlayerConnection?) {
        Timber.tag(TAG).d("setPlayerConnection: ${connection != null}, isInRoom: $isInRoom")

        try {

            val oldConnection = playerConnection
            if (playerListenerRegistered && oldConnection != null) {
                detachPlayerListener()
            }
            stopPlayerSwapObservation()
            oldConnection?.shouldBlockPlaybackChanges = null
            oldConnection?.onSkipPrevious = null
            oldConnection?.onSkipNext = null
            oldConnection?.onRestartSong = null
            
            playerConnection = connection
            
            
            connection?.shouldBlockPlaybackChanges = {
                
                isInRoom && !isHost
            }
            
            
            if (connection != null && isInRoom) {
                attachPlayerListener()

                
                // DEFECT 1 (guest lands one song ahead). These callbacks fire from
                // PlayerConnection.seekToNext/seekToPrevious AFTER `player.seekToNext()` has already
                // returned — and media3 flushes its listener events synchronously inside that call, so
                // playerListener.onMediaItemTransition above has ALREADY published an absolute
                // CHANGE_TRACK naming the destination track. Publishing a relative SKIP_NEXT on top made
                // the guest apply both: it landed on the destination and then skipped once more, i.e.
                // exactly one song ahead of the host, every time. The absolute message is strictly better
                // (it carries the media id and the queue, so it is idempotent and survives queue
                // divergence), so these now only trace.
                connection.onSkipPrevious = {
                    try {
                        if (isHost && !isSyncing) {
                            val p = playerConnection?.player
                            trace(
                                "PUBLISH", "SKIP_PREV_SUPPRESSED", p?.currentMediaItem?.mediaId,
                                safeQueueIndex(p), safePosition(p), 0L,
                                "absolute CHANGE_TRACK already published",
                            )
                        }
                    } catch (e: Exception) {
                        Timber.tag(TAG).e(e, "Error in onSkipPrevious")
                    }
                }
                connection.onSkipNext = {
                    try {
                        if (isHost && !isSyncing) {
                            val p = playerConnection?.player
                            trace(
                                "PUBLISH", "SKIP_NEXT_SUPPRESSED", p?.currentMediaItem?.mediaId,
                                safeQueueIndex(p), safePosition(p), 0L,
                                "absolute CHANGE_TRACK already published",
                            )
                        }
                    } catch (e: Exception) {
                        Timber.tag(TAG).e(e, "Error in onSkipNext")
                    }
                }
                
                
                connection.onRestartSong = {
                    try {
                        if (isHost && !isSyncing) {
                            Timber.tag(TAG).d("Host Restart Song triggered (sending 1ms as 0ms workaround)")
                            client.sendPlaybackAction(PlaybackActions.SEEK, position = 1L)
                        }
                    } catch (e: Exception) {
                        Timber.tag(TAG).e(e, "Error in onRestartSong")
                    }
                }
            }

            
            if (connection != null && isInRoom && isHost) {
                startQueueSyncObservation()
                startHeartbeat()
                startVolumeSyncObservation()
                observePlayerSwaps()
            } else {
                stopQueueSyncObservation()
                stopHeartbeat()
                stopVolumeSyncObservation()
                stopPlayerSwapObservation()
            }
            updateGuestMuteState()
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error in setPlayerConnection")
        }
    }

    private fun observePreferences() {


        scope.launch {
            context.dataStore.data
                .map { it[ListenTogetherSyncVolumeKey] ?: true }
                .distinctUntilChanged()
                .collect { enabled ->
                    syncHostVolumeEnabled.value = enabled
                }
        }

        scope.launch {
            context.dataStore.data
                .map { it[ListenTogetherSmartResyncKey] ?: true }
                .distinctUntilChanged()
                .collect { enabled ->
                    smartResyncEnabled.value = enabled
                }
        }
    }

    
    fun initialize() {
        Timber.tag(TAG).d("Initializing ListenTogetherManager")
        eventCollectorJob?.cancel()
        eventCollectorJob = scope.launch {
            client.events.collect { event ->
                try {
                    Timber.tag(TAG).d("Received event: $event")
                    handleEvent(event)
                } catch (e: Exception) {
                    Timber.tag(TAG).e(e, "Error handling event: $event")
                }
            }
        }


        roleCollectorJob?.cancel()
        roleCollectorJob = scope.launch {
            role.collect { newRole ->
                try {
                    val previousRole = lastRole
                    lastRole = newRole

                    val wasHost = previousRole == RoomRole.HOST
                    if (newRole == RoomRole.HOST && !wasHost) {
                        val connection = playerConnection
                        if (connection != null) {
                            Timber.tag(TAG).d("Role changed to HOST, starting sync services")
                            startQueueSyncObservation()
                            startHeartbeat()
                            startVolumeSyncObservation()
                            attachPlayerListener()
                            observePlayerSwaps()
                        }
                    } else if (newRole != RoomRole.HOST && wasHost) {
                        Timber.tag(TAG).d("Role changed from HOST, stopping sync services")
                        stopQueueSyncObservation()
                        stopHeartbeat()
                        stopVolumeSyncObservation()
                        stopPlayerSwapObservation()
                    }
                    updateGuestMuteState()
                } catch (e: Exception) {
                    Timber.tag(TAG).e(e, "Error in role change handler")
                }
            }
        }
    }

    private fun handleEvent(event: ListenTogetherEvent) {
        when (event) {
            is ListenTogetherEvent.Connected -> {
                Timber.tag(TAG).d("Connected to server with userId: ${event.userId}")
            }
            
            is ListenTogetherEvent.RoomCreated -> {
                Timber.tag(TAG).d("Room created: ${event.roomCode}")
                try {
                    
                    val connection = playerConnection
                    val player = connection?.player
                    attachPlayerListener()
                    observePlayerSwaps()

                    lastSyncedIsPlaying = player?.playWhenReady
                    lastSyncedTrackId = player?.currentMediaItem?.mediaId

                    
                    player?.currentMetadata?.let { metadata ->
                        Timber.tag(TAG).d("Room created with existing track: ${metadata.title}")
                        
                        sendTrackChangeInternal(metadata)
                        
                        val isPlaying = player.playWhenReady
                        if (isPlaying) {
                            lastSyncedIsPlaying = true
                            val position = player.currentPosition
                            Timber.tag(TAG).d("Host already playing on room create, sending PLAY at $position")
                            client.sendPlaybackAction(PlaybackActions.PLAY, position = position)
                        }
                    }
                    startQueueSyncObservation()
                    startHeartbeat()
                    startVolumeSyncObservation()
                } catch (e: Exception) {
                    Timber.tag(TAG).e(e, "Error handling RoomCreated event")
                }
            }
            
            is ListenTogetherEvent.JoinApproved -> {
                Timber.tag(TAG).d("Join approved for room: ${event.roomCode}")
                
                saveMuteStateOnJoin()

                applyPlaybackState(
                    currentTrack = event.state.currentTrack,
                    isPlaying = event.state.isPlaying,
                    // Same rule as every other apply path: a position captured while the host was
                    // playing has to be advanced by however long it took to reach us, or the guest
                    // starts the session already behind. handleSyncState has always done this.
                    position = compensatedRoomStatePosition(event.state.position, event.state.lastUpdate, event.state.isPlaying),
                    queue = event.state.queue

                )
                applyHostVolumeIfNeeded(event.state.volume, fromAggregateState = true)
                updateGuestMuteState()
            }
            
            is ListenTogetherEvent.PlaybackSync -> {
                Timber.tag(TAG).d("PlaybackSync received: ${event.action.action}")
                
                val actionType = event.action.action
                val isQueueOp = actionType == PlaybackActions.QUEUE_ADD ||
                        actionType == PlaybackActions.QUEUE_REMOVE ||
                        actionType == PlaybackActions.QUEUE_CLEAR
                if (!isHost || isQueueOp) {
                    handlePlaybackSync(event.action)
                }
            }
            
            is ListenTogetherEvent.UserJoined -> {
                Timber.tag(TAG).d("[SYNC] User joined: ${event.username}")
                
                if (isHost) {
                    try {
                        val connection = playerConnection
                        val player = connection?.player
                        player?.currentMetadata?.let { metadata ->
                            Timber.tag(TAG).d("[SYNC] Sending current track to newly joined user: ${metadata.title}")
                            sendTrackChangeInternal(metadata)
                            
                            if (player.playWhenReady) {
                                val pos = player.currentPosition
                                Timber.tag(TAG).d("[SYNC] Host playing, sending PLAY at $pos for new joiner")
                                client.sendPlaybackAction(PlaybackActions.PLAY, position = pos)
                            }
                            
                        }
                    } catch (e: Exception) {
                        Timber.tag(TAG).e(e, "Error handling UserJoined event")
                    }
                }
            }

            is ListenTogetherEvent.BufferWait -> {
                Timber.tag(TAG).d("BufferWait: waiting for ${event.waitingFor.size} users")
            }
            
            is ListenTogetherEvent.BufferComplete -> {
                Timber.tag(TAG).d("BufferComplete for track: ${event.trackId}")
                if (!isHost && bufferingTrackId == event.trackId) {
                    bufferCompleteReceivedForTrack = event.trackId
                    applyPendingSyncIfReady()
                }
            }
            
            is ListenTogetherEvent.SyncStateReceived -> {
                Timber.tag(TAG).d("SyncStateReceived: playing=${event.state.isPlaying}, pos=${event.state.position}, track=${event.state.currentTrack?.id}")
                if (!isHost) {
                    handleSyncState(event.state)
                }
            }
            
            is ListenTogetherEvent.Kicked -> {
                Timber.tag(TAG).d("Kicked from room: ${event.reason}")
                cleanup()
            }
            
            is ListenTogetherEvent.Disconnected -> {
                Timber.tag(TAG).d("Disconnected from server")
                
                
            }

            is ListenTogetherEvent.Reconnecting -> {
                Timber.tag(TAG).d("Reconnecting: attempt ${event.attempt}/${event.maxAttempts}")
            }
            
            is ListenTogetherEvent.Reconnected -> {
                Timber.tag(TAG).d("Reconnected to room: ${event.roomCode}, isHost: ${event.isHost}")
                try {
                    
                    val connection = playerConnection
                    val player = connection?.player
                    attachPlayerListener()


                    if (event.isHost) {
                        observePlayerSwaps()
                        
                        lastSyncedIsPlaying = player?.playWhenReady
                        lastSyncedTrackId = player?.currentMediaItem?.mediaId
                        
                        val currentMetadata = player?.currentMetadata
                        if (currentMetadata != null) {
                            
                            val serverTrackId = event.state.currentTrack?.id
                            if (serverTrackId != currentMetadata.id) {
                                Timber.tag(TAG).d("Reconnected as host, server track ($serverTrackId) differs from local (${currentMetadata.id}), syncing")
                                sendTrackChangeInternal(currentMetadata)
                            } else {
                                Timber.tag(TAG).d("Reconnected as host, server already has current track $serverTrackId")
                            }
                            
                            
                            scope.launch {
                                delay(500)
                                try {
                                    val currentPlayer = playerConnection?.player
                                    if (currentPlayer?.playWhenReady == true) {
                                        val pos = currentPlayer.currentPosition
                                        Timber.tag(TAG)
                                            .d("Reconnected host is playing, sending PLAY at $pos")
                                        client.sendPlaybackAction(PlaybackActions.PLAY, position = pos)
                                    }
                                } catch (e: Exception) {
                                    Timber.tag(TAG).e(e, "Error sending play state after reconnect")
                                }
                            }
                        }
                    } else {
                        
                        Timber.tag(TAG).d("Reconnected as guest, syncing to host's current state")
                        applyPlaybackState(
                            currentTrack = event.state.currentTrack,
                            isPlaying = event.state.isPlaying,
                            position = compensatedRoomStatePosition(event.state.position, event.state.lastUpdate, event.state.isPlaying),
                            queue = event.state.queue,
                            bypassBuffer = true
                        )
                        applyHostVolumeIfNeeded(event.state.volume, fromAggregateState = true)
                        
                        
                        
                        scope.launch {
                            delay(1000)
                            if (isInRoom && !isHost && smartResyncEnabled.value) {
                                Timber.tag(TAG).d("Requesting fresh sync after reconnect (Smart Resync)")
                                requestSync()
                            }
                        }
                    }
                } catch (e: Exception) {
                    Timber.tag(TAG).e(e, "Error handling Reconnected event")
                }
            }
            
            is ListenTogetherEvent.UserReconnected -> {
                Timber.tag(TAG).d("User reconnected: ${event.username}")
                
            }
            
            is ListenTogetherEvent.UserDisconnected -> {
                Timber.tag(TAG).d("User temporarily disconnected: ${event.username}")
                
            }

            is ListenTogetherEvent.HostChanged -> {
                Timber.tag(TAG).d("Host changed: new host is ${event.newHostName} (${event.newHostId})")
                val wasHost = isHost
                val nowIsHost = event.newHostId == userId.value
                
                if (wasHost && !nowIsHost) {
                    
                    Timber.tag(TAG).d("Local user lost host role")
                    stopQueueSyncObservation()
                    stopVolumeSyncObservation()
                    stopPlayerSwapObservation()
                    detachPlayerListener()

                    updateGuestMuteState()
                } else if (!wasHost && nowIsHost) {
                    
                    Timber.tag(TAG).d("Local user gained host role")
                    updateGuestMuteState()
                    // No longer following anyone: take our own level back BEFORE the observation below
                    // starts, so the first thing we publish as host is our volume, not the old host's.
                    restoreGuestVolume()


                    val connection = playerConnection
                    val player = connection?.player
                    attachPlayerListener()


                    startQueueSyncObservation()
                    startVolumeSyncObservation()
                    observePlayerSwaps()

                    
                    val metadata = player?.currentMetadata
                    if (metadata != null) {
                        Timber.tag(TAG).d("New host sending current track: ${metadata.title}")
                        sendTrackChangeInternal(metadata)
                        
                        
                        if (player.playWhenReady) {
                            val position = player.currentPosition
                            Timber.tag(TAG).d("New host is playing, sending PLAY at $position")
                            client.sendPlaybackAction(PlaybackActions.PLAY, position = position)
                        }
                    }
                }
            }
            
            is ListenTogetherEvent.JoinRequestReceived -> {
                Timber.tag(TAG).d("Join request received from ${event.username}")
                
            }

            is ListenTogetherEvent.LocalSuggestionApproved -> {
                try {
                    val mediaMetadata = event.payload.trackInfo.toMediaMetadata()
                    val mediaItem = mediaMetadata.toMediaItem()
                    playerConnection?.playNext(mediaItem)
                    Timber.tag(TAG).d("Approved suggestion added to queue: ${mediaMetadata.title}")
                } catch (e: Exception) {
                    Timber.tag(TAG).e(e, "Error adding approved suggestion to queue")
                }
            }
            
            is ListenTogetherEvent.ConnectionError -> {
                Timber.tag(TAG).e("Connection error: ${event.error}")
                cleanup()
            }

            is ListenTogetherEvent.ChatMessageReceived -> {
                Timber.tag(TAG).d("Chat message received from ${event.payload.username}")
                _chatMessages.value = _chatMessages.value + event.payload
            }

            else -> {  }
        }
    }
    
    private fun cleanup() {
        if (lastRole == RoomRole.GUEST) {
            restoreGuestMuteState()
            restoreGuestVolume()
        }
        detachPlayerListener()
        stopQueueSyncObservation()
        stopHeartbeat()
        stopVolumeSyncObservation()
        stopPlayerSwapObservation()

        lastSyncedIsPlaying = null
        lastSyncedTrackId = null
        bufferingTrackId = null
        isSyncing = false
        bufferCompleteReceivedForTrack = null
        lastRole = RoomRole.NONE
        lastSyncActionTime = 0L
        lastTrackChangeAppliedAt = 0L
        ++currentTrackGeneration
        _chatMessages.value = emptyList()
    }

    private fun updateGuestMuteState() {
        
        val connection = playerConnection ?: return
        
        restoreGuestMuteState()
    }
    
    
    private fun saveMuteStateOnJoin() {
        val connection = playerConnection ?: return
        
        if (previousMuteState == null) {
            previousMuteState = connection.isMuted.value
            Timber.tag(TAG).d("Saved mute state on join: ${previousMuteState}")
        }
    }

    
    private fun restoreGuestMuteState() {
        val connection = playerConnection ?: return
        val savedState = previousMuteState
        
        if (savedState != null) {
            Timber.tag(TAG).d("Restoring mute state on leave: was muted=$savedState, currently muted=${connection.isMuted.value}")
            connection.setMuted(savedState)
        } else {
            
            
            if (connection.isMuted.value) {
                Timber.tag(TAG).d("No saved mute state on leave, unmuting player as fallback")
                connection.setMuted(false)
            }
        }
        
        previousMuteState = null
        muteForcedByPreference = false
    }

    /**
     * Guests only. Applies the host's published level to the guest's OWN app attenuation
     * (MusicService.playerVolume) — never to the guest's system stream volume.
     *
     * Why this target and not the device volume: playerVolume feeds the single writer
     * `combine(playerVolume, isMuted) { ... }.collectLatest { player.volume = it }` in MusicService, the
     * exact same path the in-app volume slider already uses, so nothing new can fight the crossfade
     * ramp (which writes player.volume directly during a swap — and which is force-disabled outright
     * while a room exists). Writing the guest's STREAM_MUSIC instead would change the device globally
     * for every app, pop the system volume HUD, and cannot be undone when the room ends.
     */
    private fun applyHostVolumeIfNeeded(volume: Float?, fromAggregateState: Boolean = false) {
        if (!syncHostVolumeEnabled.value || isHost || !isInRoom) return
        val connection = playerConnection ?: return
        // RoomState/SyncState are built by the server and encoded with proto3, where an unset float
        // decodes as 0.0 — indistinguishable from "the host wants silence". Never mute a joining guest on
        // that ambiguity; an explicit SET_VOLUME action is the only message allowed to carry a real 0.
        if (fromAggregateState && volume != null && volume <= 0f) {
            Timber.tag(TAG).d("Ignoring volume 0 from aggregate room state (likely an unset proto field)")
            return
        }
        val target = volume?.coerceIn(0f, 1f) ?: return
        saveVolumeStateOnJoin()
        connection.service.playerVolume.value = target
        trace(
            "APPLY", "SET_VOLUME", connection.player.currentMediaItem?.mediaId,
            safeQueueIndex(connection.player), safePosition(connection.player), 0L,
            "volume=$target",
        )
    }

    /** Remember the guest's own attenuation once, before the host's level overwrites it. */
    private fun saveVolumeStateOnJoin() {
        val connection = playerConnection ?: return
        if (previousPlayerVolume == null) {
            previousPlayerVolume = try {
                connection.service.playerVolume.value
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "Could not read player volume on join")
                null
            }
            Timber.tag(TAG).d("Saved player volume on join: $previousPlayerVolume")
        }
    }

    /**
     * Put the guest's own attenuation back when the room ends. Without this the host's level sticks:
     * MusicService persists playerVolume to the datastore a second after any change, so a quiet host
     * would silently become the guest's new default for solo listening.
     */
    private fun restoreGuestVolume() {
        val connection = playerConnection ?: return
        val saved = previousPlayerVolume ?: return
        try {
            Timber.tag(TAG).d("Restoring player volume on leave: $saved")
            connection.service.playerVolume.value = saved
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Failed to restore player volume on leave")
        }
        previousPlayerVolume = null
    }

    /**
     * Advance a position that a SERVER-built RoomState reported at [lastUpdate] to now. Server clock vs
     * ours, so this can only ever be approximate — compensatedPosition floors the delta at 0 so a guest
     * whose clock runs behind never seeks backwards. A missing [lastUpdate] means no compensation.
     */
    private fun compensatedRoomStatePosition(position: Long, lastUpdate: Long, isPlaying: Boolean): Long =
        ListenTogetherSync.compensatedPosition(
            basePositionMs = position,
            capturedAtMs = lastUpdate,
            nowMs = System.currentTimeMillis(),
            isPlaying = isPlaying,
        )

    private fun applyPendingSyncIfReady() {
        val pending = pendingSyncState ?: return
        val pendingTrackId = pending.currentTrack?.id ?: bufferingTrackId ?: return
        val completeForTrack = bufferCompleteReceivedForTrack

        if (completeForTrack != pendingTrackId) return

        val connection = playerConnection ?: return
        val player = connection.player

        Timber.tag(TAG).d("Applying pending sync: track=$pendingTrackId, pos=${pending.position}, play=${pending.isPlaying}")
        isSyncing = true

        val willPlay = pending.isPlaying
        // DEFECT 3 (they desync on the next track). The position in `pending` was captured when the
        // host's message ARRIVED; it is only applied now, after the track buffered and the server's
        // BUFFER_COMPLETE fan-in came back — routinely seconds later. Applying it verbatim baked that
        // whole wait in as permanent lag, which is why the pair started together and drifted apart from
        // the first automatic advance onward. The other apply path (handleSyncState) has always done this
        // compensation; this one never did. Both timestamps are this device's own clock, so the
        // arithmetic is safe (see the serverTime note in handlePlaybackSync).
        val now = System.currentTimeMillis()
        val staleMs = if (pending.lastUpdate > 0L) (now - pending.lastUpdate).coerceAtLeast(0L) else 0L
        val duration = try {
            player.duration.takeIf { it != androidx.media3.common.C.TIME_UNSET } ?: 0L
        } catch (e: Exception) {
            0L
        }
        val targetPos = ListenTogetherSync.compensatedPosition(
            basePositionMs = pending.position,
            capturedAtMs = pending.lastUpdate,
            nowMs = now,
            isPlaying = willPlay,
            durationMs = duration,
        )
        val posDiff = kotlin.math.abs(player.currentPosition - targetPos)


        val tolerance = if (willPlay && player.playWhenReady) PLAYBACK_POSITION_TOLERANCE_MS else POSITION_TOLERANCE_MS

        trace(
            "APPLY", "PENDING_SYNC", pendingTrackId, safeQueueIndex(player), targetPos, staleMs,
            "raw=${pending.position} play=$willPlay diff=${posDiff}ms tol=${tolerance}ms",
        )

        if (posDiff > tolerance) {
            Timber.tag(TAG).d("Applying pending sync: seeking ${player.currentPosition} -> $targetPos (diff ${posDiff}ms > ${tolerance}ms)")
            connection.seekTo(targetPos)
        } else {
            Timber.tag(TAG).d("Applying pending sync: skipping seek (diff ${posDiff}ms < ${tolerance}ms)")
        }

        
        if (willPlay && !player.playWhenReady) {
            Timber.tag(TAG).d("Applying pending sync: starting playback")
            connection.play()
        } else if (!willPlay && player.playWhenReady) {
            Timber.tag(TAG).d("Applying pending sync: pausing playback")
            connection.pause()
        }

        scope.launch {
            delay(200)
            isSyncing = false
        }

        bufferingTrackId = null
        pendingSyncState = null
        bufferCompleteReceivedForTrack = null
    }

    private fun handlePlaybackSync(action: PlaybackActionPayload) {
        val connection = playerConnection
        if (connection == null) {
            Timber.tag(TAG).w("Cannot sync playback - no player connection")
            return
        }
        val player = connection.player
        
        Timber.tag(TAG).d("Handling playback sync: ${action.action}, position: ${action.position}")

        isSyncing = true

        try {
            when (action.action) {
                PlaybackActions.PLAY -> {
                    val basePos = action.position ?: 0L
                    val now = System.currentTimeMillis()
                    // Prefer a real server timestamp when the room server actually stamps one.
                    // Until then, advance by this device's measured one-way delay (PING/PONG RTT/2)
                    // — that does not mix two wall clocks. See ListenTogetherSync.networkCompensatedPosition.
                    val adjustedPos = action.serverTime?.let { serverTime ->
                        basePos + kotlin.math.max(0L, now - serverTime)
                    } ?: ListenTogetherSync.networkCompensatedPosition(
                        basePositionMs = basePos,
                        oneWayLatencyMs = client.oneWayLatencyMs(),
                        isPlaying = true,
                    )

                    Timber.tag(TAG).d("Guest: PLAY at position $adjustedPos, currently playing=${player.playWhenReady}")
                    trace(
                        "APPLY", "PLAY", player.currentMediaItem?.mediaId, safeQueueIndex(player),
                        adjustedPos, 0L,
                        "local=${safePosition(player)} buffering=${bufferingTrackId != null}",
                    )

                    if (bufferingTrackId != null) {
                        pendingSyncState = (pendingSyncState ?: SyncStatePayload(
                            currentTrack = roomState.value?.currentTrack,
                            isPlaying = true,
                            position = adjustedPos,
                            lastUpdate = now
                        )).copy(
                            isPlaying = true,
                            position = adjustedPos,
                            lastUpdate = now
                        )
                        applyPendingSyncIfReady()
                        return
                    }

                    
                    val posDiff = kotlin.math.abs(player.currentPosition - adjustedPos)
                    val alreadyPlaying = player.playWhenReady
                    
                    if (alreadyPlaying && posDiff < POSITION_TOLERANCE_MS && (now - lastSyncActionTime) < SYNC_DEBOUNCE_THRESHOLD_MS) {
                        Timber.tag(TAG).d("Guest: PLAY debounced - already playing and in sync (diff ${posDiff}ms)")
                        return
                    }

                    
                    
                    if (alreadyPlaying) {
                        if (posDiff > PLAYBACK_POSITION_TOLERANCE_MS) {
                            Timber.tag(TAG).d("Guest: PLAY seeking during playback ${player.currentPosition} -> $adjustedPos (diff ${posDiff}ms)")
                            connection.seekTo(adjustedPos)
                        } else {
                            Timber.tag(TAG).d("Guest: PLAY skipping seek - already playing, drift acceptable (${posDiff}ms < ${PLAYBACK_POSITION_TOLERANCE_MS}ms)")
                        }
                    } else {
                        
                        if (posDiff > POSITION_TOLERANCE_MS) {
                            Timber.tag(TAG).d("Guest: PLAY seeking while paused ${player.currentPosition} -> $adjustedPos (diff ${posDiff}ms)")
                            connection.seekTo(adjustedPos)
                        }
                        
                        Timber.tag(TAG).d("Guest: Starting playback")
                        connection.play()
                    }
                    lastSyncActionTime = now
                }
                
                PlaybackActions.PAUSE -> {
                    val pos = action.position ?: 0L
                    val now = System.currentTimeMillis()
                    
                    Timber.tag(TAG).d("Guest: PAUSE at position $pos, currently playing=${player.playWhenReady}")
                    trace(
                        "APPLY", "PAUSE", player.currentMediaItem?.mediaId, safeQueueIndex(player),
                        pos, 0L, "local=${safePosition(player)}",
                    )

                    if (bufferingTrackId != null) {
                        pendingSyncState = (pendingSyncState ?: SyncStatePayload(
                            currentTrack = roomState.value?.currentTrack,
                            isPlaying = false,
                            position = pos,
                            lastUpdate = now
                        )).copy(
                            isPlaying = false,
                            position = pos,
                            lastUpdate = now
                        )
                        applyPendingSyncIfReady()
                        return
                    }

                    
                    val posDiff = kotlin.math.abs(player.currentPosition - pos)
                    val alreadyPaused = !player.playWhenReady
                    
                    if (alreadyPaused && posDiff < POSITION_TOLERANCE_MS && (now - lastSyncActionTime) < SYNC_DEBOUNCE_THRESHOLD_MS) {
                        Timber.tag(TAG).d("Guest: PAUSE debounced - already paused and in sync (diff ${posDiff}ms)")
                        return
                    }

                    
                    if (player.playWhenReady) {
                        Timber.tag(TAG).d("Guest: Pausing playback")
                        connection.pause()
                    }
                    
                    
                    if (posDiff > POSITION_TOLERANCE_MS) {
                        Timber.tag(TAG).d("Guest: PAUSE seeking ${player.currentPosition} -> $pos (diff ${posDiff}ms)")
                        connection.seekTo(pos)
                    } else {
                        Timber.tag(TAG).d("Guest: PAUSE skipping seek (diff ${posDiff}ms < ${POSITION_TOLERANCE_MS}ms)")
                    }
                    lastSyncActionTime = now
                }

                PlaybackActions.SEEK -> {
                    val pos = action.position ?: 0L
                    val now = System.currentTimeMillis()

                    trace(
                        "APPLY", "SEEK", player.currentMediaItem?.mediaId, safeQueueIndex(player),
                        pos, 0L, "local=${safePosition(player)}",
                    )

                    if (now - lastSyncActionTime < SYNC_DEBOUNCE_THRESHOLD_MS) {
                        Timber.tag(TAG).d("Guest: SEEK debounced (only ${now - lastSyncActionTime}ms since last sync)")
                        return
                    }
                    
                    
                    if (kotlin.math.abs(player.currentPosition - pos) > POSITION_TOLERANCE_MS) {
                        Timber.tag(TAG).d("Guest: SEEK to $pos from ${player.currentPosition} (diff > ${POSITION_TOLERANCE_MS}ms)")
                        connection.seekTo(pos)
                        lastSyncActionTime = now
                    } else {
                        Timber.tag(TAG).d("Guest: SEEK ignored (position diff < ${POSITION_TOLERANCE_MS}ms)")
                    }
                }
                
                PlaybackActions.CHANGE_TRACK -> {
                    action.trackInfo?.let { track ->
                        Timber.tag(TAG).d("Guest: CHANGE_TRACK to ${track.title}, queue size=${action.queue?.size}")

                        // Absolute anchor applied: any relative SKIP_NEXT/SKIP_PREV arriving right
                        // behind this one is the second half of a host double-publish (see
                        // ListenTogetherSync.SKIP_SUPPRESSION_WINDOW_MS) and must not move us again.
                        lastTrackChangeAppliedAt = System.currentTimeMillis()
                        trace(
                            "APPLY", "CHANGE_TRACK", track.id, safeQueueIndex(player),
                            safePosition(player), 0L, "queue=${action.queue?.size ?: 0}",
                        )


                        lastSyncActionTime = 0L

                        
                        if (action.queue != null && action.queue.isNotEmpty()) {
                            val queueTitle = action.queueTitle
                            applyPlaybackState(
                                currentTrack = track,
                                isPlaying = false, 
                                position = 0,
                                queue = action.queue,
                                queueTitle = queueTitle
                            )
                        } else {
                            
                            bufferingTrackId = track.id
                            syncToTrack(track, false, 0)
                        }
                    }
                }
                
                // Relative skips are no longer published by this build (the absolute CHANGE_TRACK that
                // media3's transition callback already emitted describes the same move by identity).
                // They are still ACCEPTED, so a room hosted by an older build keeps working — but only
                // when no absolute track change just landed, otherwise applying both would move the
                // guest two songs, which is defect 1 exactly.
                PlaybackActions.SKIP_NEXT -> {
                    val now = System.currentTimeMillis()
                    if (ListenTogetherSync.shouldApplyRelativeSkip(now, lastTrackChangeAppliedAt)) {
                        Timber.tag(TAG).d("Guest: SKIP_NEXT")
                        trace("APPLY", "SKIP_NEXT", player.currentMediaItem?.mediaId, safeQueueIndex(player), safePosition(player), 0L)
                        connection.seekToNext()
                    } else {
                        Timber.tag(TAG).d("Guest: SKIP_NEXT ignored - absolute track change just applied")
                        trace(
                            "APPLY", "SKIP_NEXT_IGNORED", player.currentMediaItem?.mediaId,
                            safeQueueIndex(player), safePosition(player), now - lastTrackChangeAppliedAt,
                            "double-advance guard",
                        )
                    }
                }

                PlaybackActions.SKIP_PREV -> {
                    val now = System.currentTimeMillis()
                    if (ListenTogetherSync.shouldApplyRelativeSkip(now, lastTrackChangeAppliedAt)) {
                        Timber.tag(TAG).d("Guest: SKIP_PREV")
                        trace("APPLY", "SKIP_PREV", player.currentMediaItem?.mediaId, safeQueueIndex(player), safePosition(player), 0L)
                        connection.seekToPrevious()
                    } else {
                        Timber.tag(TAG).d("Guest: SKIP_PREV ignored - absolute track change just applied")
                        trace(
                            "APPLY", "SKIP_PREV_IGNORED", player.currentMediaItem?.mediaId,
                            safeQueueIndex(player), safePosition(player), now - lastTrackChangeAppliedAt,
                            "double-advance guard",
                        )
                    }
                }

                PlaybackActions.QUEUE_ADD -> {
                    val track = action.trackInfo
                    if (track == null) {
                        Timber.tag(TAG).w("QUEUE_ADD missing trackInfo")
                    } else {
                        Timber.tag(TAG).d("Guest: QUEUE_ADD ${track.title}, insertNext=${action.insertNext == true}")
                        scope.launch(Dispatchers.IO) {
                            
                            YouTube.queue(listOf(track.id)).onSuccess { list ->
                                val mediaItem = list.firstOrNull()?.toMediaMetadata()?.copy(
                                    suggestedBy = track.suggestedBy
                                )?.toMediaItem()
                                if (mediaItem != null) {
                                    launch(Dispatchers.Main) {
                                        
                                        connection.allowInternalSync = true
                                        if (action.insertNext == true) {
                                            connection.playNext(mediaItem)
                                        } else {
                                            connection.addToQueue(mediaItem)
                                        }
                                        connection.allowInternalSync = false
                                    }
                                } else {
                                    Timber.tag(TAG).w("QUEUE_ADD failed to resolve media item for ${track.id}")
                                }
                            }.onFailure {
                                Timber.tag(TAG).e(it, "QUEUE_ADD metadata fetch failed")
                            }
                        }
                    }
                }

                PlaybackActions.QUEUE_REMOVE -> {
                    val removeId = action.trackId
                    if (removeId.isNullOrEmpty()) {
                        Timber.tag(TAG).w("QUEUE_REMOVE missing trackId")
                    } else {
                        
                        val startIndex = player.currentMediaItemIndex + 1
                        var removeIndex = -1
                        val total = player.mediaItemCount
                        for (i in startIndex until total) {
                            val id = player.getMediaItemAt(i).mediaId
                            if (id == removeId) { removeIndex = i; break }
                        }
                        if (removeIndex >= 0) {
                            Timber.tag(TAG).d("Guest: QUEUE_REMOVE index=$removeIndex id=$removeId")
                            player.removeMediaItem(removeIndex)
                        } else {
                            Timber.tag(TAG).w("QUEUE_REMOVE id not found in queue: $removeId")
                        }
                    }
                }

                PlaybackActions.QUEUE_CLEAR -> {
                    val currentIndex = player.currentMediaItemIndex
                    val count = player.mediaItemCount
                    val itemsAfter = count - (currentIndex + 1)
                    if (itemsAfter > 0) {
                        Timber.tag(TAG).d("Guest: QUEUE_CLEAR removing $itemsAfter items after current")
                        player.removeMediaItems(currentIndex + 1, count - (currentIndex + 1))
                    }
                }

                PlaybackActions.SET_VOLUME -> {
                    applyHostVolumeIfNeeded(action.volume)
                }

                PlaybackActions.SYNC_QUEUE -> {
                    val queue = action.queue
                    val queueTitle = action.queueTitle
                    // An EMPTY queue must never be applied. Over protobuf an absent queue decodes as an
                    // empty list rather than null (MessageCodec.decodeProtobufPayload), so the old
                    // `queue != null` test let a queue-less message through and called
                    // setMediaItems(emptyList()) — wiping the guest's whole queue mid-song.
                    if (!queue.isNullOrEmpty()) {
                        Timber.tag(TAG).d("Guest: SYNC_QUEUE size=${queue.size}")

                        activeSyncJob?.cancel()
                        
                        scope.launch(Dispatchers.Main) {
                            if (playerConnection !== connection) return@launch
                            val player = connection.player
                            
                            
                            val mediaItems = queue.map { track ->
                                track.toMediaMetadata().toMediaItem()
                            }
                            

                            // Identity anchor: keep playing the song we are on, wherever it now sits.
                            val currentId = player.currentMediaItem?.mediaId
                            val newIndex = ListenTogetherSync.resolveStartIndex(
                                mediaItems.map { it.mediaId },
                                currentId,
                            )

                            val currentPos = player.currentPosition
                            val wasPlaying = player.isPlaying
                            trace(
                                "APPLY", "SYNC_QUEUE", currentId, newIndex, currentPos, 0L,
                                "queue=${mediaItems.size}",
                            )

                            connection.allowInternalSync = true
                            if (newIndex != -1) {
                                player.setMediaItems(mediaItems, newIndex, currentPos)
                            } else {
                                player.setMediaItems(mediaItems)
                            }
                            connection.allowInternalSync = false

                            
                            if (wasPlaying && !player.isPlaying) {
                                connection.play()
                            }
                            
                            
                            try {
                                connection.service.queueTitle = queueTitle
                            } catch (e: Exception) {
                                Timber.tag(TAG).e(e, "Failed to set queue title during SYNC_QUEUE")
                            }
                        }
                    }
                }
            }
        } finally {
            
            scope.launch {
                delay(200)
                isSyncing = false
            }
        }
    }
    
    private fun handleSyncState(state: SyncStatePayload) {
        val now = System.currentTimeMillis()
        // Same rule as applyPendingSyncIfReady: a position captured while the host is PLAYING has to be
        // advanced by however long it took to get here. state.lastUpdate comes from the server, so this
        // one does mix clocks — it always has; coerceAtLeast(0) inside compensatedPosition keeps a guest
        // clock that is behind from ever seeking backwards.
        val adjustedPos = ListenTogetherSync.compensatedPosition(
            basePositionMs = state.position,
            capturedAtMs = state.lastUpdate,
            nowMs = now,
            isPlaying = state.isPlaying,
        )

        Timber.tag(TAG).d("handleSyncState: playing=${state.isPlaying}, pos=${state.position} -> adj=$adjustedPos, track=${state.currentTrack?.id}")
        trace(
            "APPLY", "SYNC_STATE", state.currentTrack?.id, -1, adjustedPos,
            (now - state.lastUpdate).coerceAtLeast(0L),
            "raw=${state.position} play=${state.isPlaying} queue=${state.queue?.size ?: 0}",
        )
        
        applyPlaybackState(
            currentTrack = state.currentTrack,
            isPlaying = state.isPlaying,
            position = adjustedPos,
            queue = state.queue,
            bypassBuffer = true  
        )
        applyHostVolumeIfNeeded(state.volume, fromAggregateState = true)
    }

    private fun applyPlaybackState(
        currentTrack: TrackInfo?,
        isPlaying: Boolean,
        position: Long,
        queue: List<TrackInfo>?,
        queueTitle: String? = null,  
        bypassBuffer: Boolean = false
    ) {
        val connection = playerConnection
        if (connection == null) {
            Timber.tag(TAG).w("Cannot apply playback state - no player")
            return
        }
        val player = connection.player

        Timber.tag(TAG).d("Applying playback state: track=${currentTrack?.id}, pos=$position, queue=${queue?.size}, bypassBuffer=$bypassBuffer")

        
        activeSyncJob?.cancel()

        
        if (currentTrack == null) {
            Timber.tag(TAG).d("No track in state, pausing")
            val generation = ++currentTrackGeneration
            scope.launch(Dispatchers.Main) {
                
                if (currentTrackGeneration != generation) {
                    Timber.tag(TAG).d("Skipping stale track generation: $generation vs current $currentTrackGeneration")
                    return@launch
                }
                
                if (playerConnection !== connection) return@launch
                isSyncing = true
                connection.allowInternalSync = true
                if (queue != null && queue.isNotEmpty()) {
                    val mediaItems = queue.map { it.toMediaMetadata().toMediaItem() }
                    player.setMediaItems(mediaItems)
                } else if (queue != null) {
                    player.clearMediaItems()
                }
                connection.pause()
                try {
                    connection.service.queueTitle = queueTitle
                } catch (e: Exception) {
                    Timber.tag(TAG).e(e, "Failed to set queue title for empty state")
                }
                connection.allowInternalSync = false
                isSyncing = false
            }
            return
        }

        bufferingTrackId = currentTrack.id
        // Every path into here (CHANGE_TRACK, join, reconnect, SYNC_STATE) is an ABSOLUTE anchor, so it
        // arms the double-advance guard for relative skips. See handlePlaybackSync's SKIP_NEXT branch.
        lastTrackChangeAppliedAt = System.currentTimeMillis()
        val generation = ++currentTrackGeneration

        scope.launch(Dispatchers.Main) {
            
            if (currentTrackGeneration != generation) {
                Timber.tag(TAG).d("Skipping stale track generation: $generation vs current $currentTrackGeneration (track ${currentTrack.id})")
                return@launch
            }
            
            if (playerConnection !== connection) return@launch
            isSyncing = true
            connection.allowInternalSync = true

            try {
                
                if (currentTrackGeneration != generation) {
                    Timber.tag(TAG).d("Stale generation detected before setMediaItems: $generation vs $currentTrackGeneration")
                    return@launch
                }
                
                
                if (queue != null && queue.isNotEmpty()) {
                    val mediaItems = queue.map { it.toMediaMetadata().toMediaItem() }

                    // IDENTITY, never position: the two queues differ by an item the moment the radio
                    // appends or shuffle reorders, and a positional anchor then lands on the wrong song.
                    val startIndex = ListenTogetherSync.resolveStartIndex(
                        mediaItems.map { it.mediaId },
                        currentTrack.id,
                    )
                    if (startIndex == -1) {
                        Timber.tag(TAG).w("Current track ${currentTrack.id} not found in queue, defaulting to 0")
                        val singleItem = currentTrack.toMediaMetadata().toMediaItem()

                        player.setMediaItems(listOf(singleItem), 0, position)
                    } else {
                        player.setMediaItems(mediaItems, startIndex, position)
                    }
                    trace(
                        "APPLY", "STATE", currentTrack.id, startIndex, position, 0L,
                        "queue=${mediaItems.size} play=$isPlaying bypassBuffer=$bypassBuffer",
                    )
                } else {
                    
                    
                    
                    
                    Timber.tag(TAG).d("No queue in state, loading single track")
                    
                    val item = currentTrack.toMediaMetadata().toMediaItem()
                    player.setMediaItems(listOf(item), 0, position)
                }
                
                connection.seekTo(position)  

                
                try {
                    connection.service.queueTitle = queueTitle ?: "Listen Together"
                } catch (e: Exception) {
                    Timber.tag(TAG).e(e, "Failed to set queue title during applyPlaybackState")
                }
                
                if (bypassBuffer) {
                    
                    Timber.tag(TAG).d("Bypass buffer: immediately applying play=$isPlaying at pos=$position")
                    
                    
                    var attempts = 0
                    while (player.playbackState != Player.STATE_READY && attempts < 100) {
                        delay(50)
                        attempts++
                    }
                    if (player.playbackState == Player.STATE_READY) {
                        Timber.tag(TAG).d("Player ready after ${attempts * 50}ms, seeking to $position")
                        player.seekTo(position)
                        if (isPlaying) {
                            connection.play()
                            Timber.tag(TAG).d("Bypass: PLAY issued")
                        } else {
                            connection.pause()
                            Timber.tag(TAG).d("Bypass: PAUSE issued")
                        }
                    } else {
                        Timber.tag(TAG).w("Player not ready after 5s timeout during bypass sync")
                    }
                    
                    
                    pendingSyncState = null
                    bufferingTrackId = null
                    bufferCompleteReceivedForTrack = null
                } else {

                    connection.pause()
                    pendingSyncState = SyncStatePayload(
                        currentTrack = currentTrack,
                        isPlaying = isPlaying,
                        position = position,
                        lastUpdate = System.currentTimeMillis()
                    )
                    applyPendingSyncIfReady()
                    client.sendBufferReady(currentTrack.id)



                    val watchdogTrackId = currentTrack.id
                    scope.launch {
                        delay(BUFFER_WAIT_TIMEOUT_MS)

                        if (currentTrackGeneration != generation) return@launch


                        if (bufferingTrackId != watchdogTrackId) return@launch

                        Timber.tag(TAG).w("BufferComplete never arrived for $watchdogTrackId after ${BUFFER_WAIT_TIMEOUT_MS}ms - forcing recovery")

                        bufferCompleteReceivedForTrack = watchdogTrackId
                        applyPendingSyncIfReady()


                        if (bufferingTrackId == watchdogTrackId) {
                            Timber.tag(TAG).w("Recovery apply did not resolve buffer wait for $watchdogTrackId - requesting fresh sync")
                            requestSync()
                        }
                    }
                }
                
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "Error applying playback state")
            } finally {
                connection.allowInternalSync = false
                delay(200)
                isSyncing = false
            }
        }
    }

    private fun syncToTrack(track: TrackInfo, shouldPlay: Boolean, position: Long) {
        Timber.tag(TAG).d("syncToTrack: ${track.title}, play: $shouldPlay, pos: $position")

        
        bufferingTrackId = track.id
        val generation = currentTrackGeneration
        
        activeSyncJob?.cancel()
        activeSyncJob = scope.launch(Dispatchers.IO) {
            try {
                
                if (currentTrackGeneration != generation) {
                    Timber.tag(TAG).d("Skipping stale syncToTrack for ${track.id} (generation $generation vs $currentTrackGeneration)")
                    isSyncing = false
                    return@launch
                }
                
                
                YouTube.queue(listOf(track.id)).onSuccess { queue ->
                    Timber.tag(TAG).d("Got queue for track ${track.id}")
                    launch(Dispatchers.Main) {
                        
                        if (currentTrackGeneration != generation) {
                            Timber.tag(TAG).d("Skipping stale track application for ${track.id} (generation $generation vs $currentTrackGeneration)")
                            isSyncing = false
                            return@launch
                        }
                        
                        val connection = playerConnection ?: run {
                            isSyncing = false
                            return@launch
                        }
                        if (playerConnection !== connection) {
                            isSyncing = false
                            return@launch
                        }
                        isSyncing = true
                        
                        connection.allowInternalSync = true
                        connection.playQueue(
                            YouTubeQueue(
                                endpoint = WatchEndpoint(videoId = track.id),
                                preloadItem = queue.firstOrNull()?.toMediaMetadata()
                            )
                        )
                        try {
                            connection.service.queueTitle = "Listen Together" 
                        } catch (e: Exception) {
                            Timber.tag(TAG).e(e, "Failed to set queue title")
                        }
                        connection.allowInternalSync = false
                        
                        
                        var waitCount = 0
                        while (waitCount < 40) { 
                            
                            if (currentTrackGeneration != generation) {
                                Timber.tag(TAG).d("Generation changed while waiting for player ready - aborting sync for ${track.id}")
                                isSyncing = false
                                return@launch
                            }
                            try {
                                val player = connection.player
                                if (player.playbackState == Player.STATE_READY) {
                                    Timber.tag(TAG).d("Player ready after ${waitCount * 50}ms")
                                    break
                                }
                            } catch (e: Exception) {
                                Timber.tag(TAG).e(e, "Error checking player state")
                                break
                            }
                            delay(50)
                            waitCount++
                        }

                        
                        
                        connection.pause()

                        
                        pendingSyncState = SyncStatePayload(
                            currentTrack = track,
                            isPlaying = shouldPlay,
                            position = position,
                            lastUpdate = System.currentTimeMillis()
                        )

                        
                        applyPendingSyncIfReady()

                        
                        client.sendBufferReady(track.id)
                        Timber.tag(TAG).d("Sent buffer ready for ${track.id}, pending sync stored: pos=$position, play=$shouldPlay")

                        
                        delay(100)
                        isSyncing = false
                    }
                }.onFailure { e ->
                    Timber.tag(TAG).e(e, "Failed to load track ${track.id}")
                    playerConnection?.allowInternalSync = false
                    isSyncing = false
                }
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "Error syncing to track")
                playerConnection?.allowInternalSync = false
                isSyncing = false
            }
        }
    }

    

    
    fun connect() {
        Timber.tag(TAG).d("Connecting to server")
        client.connect()
    }

    
    fun disconnect() {
        Timber.tag(TAG).d("Disconnecting from server")
        cleanup()
        client.disconnect()
    }

    
    fun createRoom(username: String) {
        Timber.tag(TAG).d("Creating room with username: $username")
        client.createRoom(username)
    }

    
    fun joinRoom(roomCode: String, username: String) {
        Timber.tag(TAG).d("Joining room $roomCode as $username")
        client.joinRoom(roomCode, username)
    }

    
    fun leaveRoom() {
        Timber.tag(TAG).d("Leaving room")
        cleanup()
        client.leaveRoom()
    }

    
    fun approveJoin(userId: String) = client.approveJoin(userId)

    
    fun rejectJoin(userId: String, reason: String? = null) = client.rejectJoin(userId, reason)

    
    fun kickUser(userId: String, reason: String? = null) = client.kickUser(userId, reason)

    
    fun blockUser(username: String) = client.blockUser(username)

    
    fun unblockUser(username: String) = client.unblockUser(username)

    
    fun getBlockedUsernames(): Set<String> = blockedUsernames.value

    
    fun transferHost(newHostId: String) = client.transferHost(newHostId)

    
    fun sendTrackChange(metadata: MediaMetadata) {
        if (!isHost || isSyncing) return
        sendTrackChangeInternal(metadata)
    }
    
    
    private fun sendTrackChangeInternal(metadata: MediaMetadata) {
        if (!isHost) return
        
        
        val durationMs = if (metadata.duration > 0) metadata.duration.toLong() * 1000 else 180000L
        
        val trackInfo = TrackInfo(
            id = metadata.id,
            title = metadata.title,
            artist = metadata.artists.joinToString(", ") { it.name },
            album = metadata.album?.title,
            duration = durationMs,
            thumbnail = metadata.thumbnailUrl,
            suggestedBy = metadata.suggestedBy
        )
        
        Timber.tag(TAG).d("Sending track change: ${trackInfo.title}, duration: $durationMs")
        
        
        val currentQueue = try {
            playerConnection?.queueWindows?.value?.map { it.toTrackInfo() }
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Failed to get current queue")
            null
        }
        val currentTitle = try {
            playerConnection?.queueTitle?.value
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Failed to get current title")
            null
        }
        
        client.sendPlaybackAction(
            PlaybackActions.CHANGE_TRACK,
            queueTitle = currentTitle,
            trackInfo = trackInfo,
            queue = currentQueue
        )
    }

    private fun startQueueSyncObservation() {
        if (queueObserverJob?.isActive == true) return
    
        Timber.tag(TAG).d("Starting queue sync observation")
        queueObserverJob = scope.launch {
            playerConnection?.queueWindows
                ?.map { windows ->
                    windows.map { it.toTrackInfo() }
                }
                ?.distinctUntilChanged()
                ?.collectLatest { tracks ->
                    if (!isHost || !isInRoom || isSyncing) return@collectLatest
                
                    delay(500) 
                
                    Timber.tag(TAG).d("Sending SYNC_QUEUE with ${tracks.size} items")
                    val queueTitle = try {
                        playerConnection?.queueTitle?.value
                    } catch (e: Exception) {
                        Timber.tag(TAG).e(e, "Failed to get queue title")
                        null
                    }
                    client.sendPlaybackAction(
                        PlaybackActions.SYNC_QUEUE,
                        queueTitle = queueTitle,
                        queue = tracks
                    )
                }
        }
    }

    /**
     * DEFECT 2 (guests never follow the host's volume). This used to observe ONLY
     * MusicService.playerVolume — the app's internal attenuation, which is written by exactly one
     * control: the slider inside the player's overflow menu (PlayerMenu/OldPlayerMenu). The volume the
     * user actually moves — the slider on the player screen and the hardware volume buttons — is
     * Android's STREAM_MUSIC level (Player.kt writes it with AudioManager.setStreamVolume), which
     * nothing here ever read. So a host turning the volume down published nothing at all, and the
     * feature looked dead even though the whole wire protocol for it exists.
     *
     * Now the host publishes its EFFECTIVE level: internal attenuation x device stream fraction, zeroed
     * by the app's mute toggle (a host mute was previously invisible too, since muting does not change
     * playerVolume). Device volume is picked up from the system's VOLUME_CHANGED_ACTION broadcast —
     * event-driven, no polling loop, so no new battery or thermal cost.
     */
    private fun startVolumeSyncObservation() {
        if (volumeObserverJob?.isActive == true) return

        Timber.tag(TAG).d("Starting volume sync observation")
        volumeObserverJob = scope.launch {
            val connection = playerConnection ?: return@launch
            val service = connection.service
            // MusicService.playerVolume is a lateinit backing field; if the service is bound but has not
            // finished onCreate yet, reading it throws. Never let that kill the room.
            val internalVolume = try {
                service.playerVolume
            } catch (e: Exception) {
                Timber.tag(TAG).w(e, "Player volume not ready yet; volume sync will start on the next trigger")
                return@launch
            }
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager

            val deviceVolume = MutableStateFlow(readDeviceVolumeFraction(audioManager))
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(ctx: Context?, intent: Intent?) {
                    if (intent?.action == VOLUME_CHANGED_ACTION) {
                        deviceVolume.value = readDeviceVolumeFraction(audioManager)
                    }
                }
            }
            // VOLUME_CHANGED_ACTION is a protected system broadcast, so it needs no export flag (the
            // player screen registers it exactly like this already).
            val registered = runCatching {
                context.registerReceiver(receiver, IntentFilter(VOLUME_CHANGED_ACTION))
            }.isSuccess
            if (!registered) {
                Timber.tag(TAG).w("Could not observe device volume; falling back to in-app volume only")
            }

            try {
                combine(
                    internalVolume,
                    service.isMuted,
                    deviceVolume,
                ) { internal, muted, device ->
                    ListenTogetherSync.effectiveHostVolume(internal, muted, device)
                }
                    .distinctUntilChanged()
                    .collectLatest { effective ->
                        if (!isHost || !isInRoom || !syncHostVolumeEnabled.value) return@collectLatest
                        if (!ListenTogetherSync.volumeChangedEnough(lastSyncedVolume, effective)) {
                            return@collectLatest
                        }
                        lastSyncedVolume = effective
                        val p = try { connection.player } catch (e: Exception) { null }
                        trace(
                            "PUBLISH", "SET_VOLUME", p?.currentMediaItem?.mediaId,
                            safeQueueIndex(p), safePosition(p), 0L,
                            "volume=$effective",
                        )
                        client.sendPlaybackAction(PlaybackActions.SET_VOLUME, volume = effective)
                    }
            } finally {
                if (registered) {
                    runCatching { context.unregisterReceiver(receiver) }
                }
            }
        }
    }

    private fun readDeviceVolumeFraction(audioManager: AudioManager?): Float {
        val am = audioManager ?: return 1f
        return try {
            val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            if (max <= 0) 1f else am.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / max
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Failed to read device volume")
            1f
        }
    }

    private fun stopVolumeSyncObservation() {
        volumeObserverJob?.cancel()
        volumeObserverJob = null
        lastSyncedVolume = null
    }

    private fun androidx.media3.common.Timeline.Window.toTrackInfo(): TrackInfo {
        val metadata = mediaItem.metadata ?: return TrackInfo("unknown", "Unknown", "Unknown", "", 0, "")
        val durationMs = if (metadata.duration > 0) metadata.duration.toLong() * 1000 else 180000L
        return TrackInfo(
            id = metadata.id,
            title = metadata.title,
            artist = metadata.artists.joinToString(", ") { it.name },
            album = metadata.album?.title,
            duration = durationMs,
            thumbnail = metadata.thumbnailUrl,
            suggestedBy = metadata.suggestedBy
        )
    }

    private fun stopQueueSyncObservation() {
        queueObserverJob?.cancel()
        queueObserverJob = null
    }

    private fun TrackInfo.toMediaMetadata(): MediaMetadata {
        return MediaMetadata(
            id = id,
            title = title,
            artists = listOf(Artist(id = "", name = artist)),
            album = if (album != null) Album(id = "", title = album) else null,
            duration = (duration / 1000).toInt(),
            thumbnailUrl = thumbnail,
            suggestedBy = suggestedBy
        )
    }

    
    fun requestSync() {
        if (!isInRoom || isHost) {
            Timber.tag(TAG).d("requestSync: not applicable (isInRoom=$isInRoom, isHost=$isHost)")
            return
        }
        Timber.tag(TAG).d("Requesting sync from server")
        client.requestSync()
    }

    
    fun clearLogs() = client.clearLogs()

    

    
    fun suggestTrack(track: TrackInfo) = client.suggestTrack(track)

    
    fun approveSuggestion(suggestionId: String) {
        if (!isHost) return
        
        client.approveSuggestion(suggestionId)
    }

    
    fun rejectSuggestion(suggestionId: String, reason: String? = null) = client.rejectSuggestion(suggestionId, reason)
    
    
    fun forceReconnect() {
        Timber.tag(TAG).d("Forcing reconnection")
        client.forceReconnect()
    }
    
    
    fun getPersistedRoomCode(): String? = client.getPersistedRoomCode()
    
    
    fun getSessionAge(): Long = client.getSessionAge()

    
    private var heartbeatJob: Job? = null

    private fun startHeartbeat() {
        if (heartbeatJob?.isActive == true) return
        heartbeatJob = scope.launch {
            while (heartbeatJob?.isActive == true && isInRoom && isHost) {
                delay(5000L) 
                playerConnection?.player?.let { player ->
                    if (player.playWhenReady && player.playbackState == Player.STATE_READY) {
                        val pos = player.currentPosition
                        Timber.tag(TAG).d("Host heartbeat: sending PLAY at pos $pos")
                        client.sendPlaybackAction(PlaybackActions.PLAY, position = pos)
                    }
                }
            }
        }
        Timber.tag(TAG).d("Host heartbeat started (5s interval)")
    }

    private fun stopHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = null
        Timber.tag(TAG).d("Host heartbeat stopped")
    }

    
    fun sendChatMessage(message: String, replyTo: RepliedMessage? = null) {
        if (message.isBlank()) return
        client.sendChatMessage(message, replyTo)
    }
}
