

package iad1tya.echo.music.playback

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM
import androidx.media3.common.Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM
import androidx.media3.common.Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM
import androidx.media3.common.Player.REPEAT_MODE_OFF
import androidx.media3.common.Player.STATE_ENDED
import androidx.media3.common.Timeline
import androidx.media3.exoplayer.ExoPlayer
import iad1tya.echo.music.db.MusicDatabase
import iad1tya.echo.music.extensions.currentMetadata
import iad1tya.echo.music.extensions.getCurrentQueueIndex
import iad1tya.echo.music.extensions.getQueueWindows
import iad1tya.echo.music.extensions.metadata
import iad1tya.echo.music.extensions.togglePlayPause
import iad1tya.echo.music.playback.MusicService.MusicBinder
import iad1tya.echo.music.playback.queues.Queue
import iad1tya.echo.music.utils.reportException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber

@OptIn(ExperimentalCoroutinesApi::class)
class PlayerConnection(
    context: Context,
    binder: MusicBinder,
    val database: MusicDatabase,
    scope: CoroutineScope,
) : Player.Listener {
    private companion object {
        private const val TAG = "PlayerConnection"
        private const val PLAYER_INIT_TIMEOUT_MS = 5000L 
    }

    val service = binder.service
    private val playerReadinessFlow = service.isPlayerReady
    
    
    private fun getPlayerSafe(): ExoPlayer {
        return try {
            if (!playerReadinessFlow.value) {
                Timber.tag(TAG).w("Player accessed before service initialization complete; returning best-effort reference")
            }
            service.player
        } catch (e: UninitializedPropertyAccessException) {
            Timber.tag(TAG).e(e, "Fatal: player property accessed but not initialized")
            throw IllegalStateException("MusicService.player not initialized; possible race condition in service startup", e)
        }
    }

    
    val player: ExoPlayer
        get() = getPlayerSafe()

    
    private val isPlayerInitialized = MutableStateFlow(service.isPlayerReady.value)

    val playbackState: MutableStateFlow<Int>
    private val playWhenReady: MutableStateFlow<Boolean>
    val isPlaying: kotlinx.coroutines.flow.StateFlow<Boolean>
    
    init {
        Timber.tag(TAG).d("PlayerConnection init: playerReady=${playerReadinessFlow.value}")
        
        
        val initialState = try {
            val initialPlayer = getPlayerSafe()
            Triple(initialPlayer.playbackState, initialPlayer.playWhenReady, 
                   initialPlayer.playWhenReady && initialPlayer.playbackState != STATE_ENDED)
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error during PlayerConnection initialization, using defaults")
            Triple(Player.STATE_IDLE, false, false)
        }
        
        playbackState = MutableStateFlow(initialState.first)
        playWhenReady = MutableStateFlow(initialState.second)
        isPlaying = combine(playbackState, playWhenReady) { state, ready ->
            ready && state != STATE_ENDED
        }.stateIn(
            scope,
            SharingStarted.Lazily,
            initialState.third
        )
        
        
        scope.launch {
            playerReadinessFlow.collect { ready ->
                isPlayerInitialized.value = ready
                if (ready) {
                    Timber.tag(TAG).d("Service player initialization detected by PlayerConnection")
                }
            }
        }
        
        Timber.tag(TAG).d("PlayerConnection state flows initialized successfully")
    }
    
    
    val isEffectivelyPlaying = combine(
        isPlaying,
        service.castConnectionHandler?.isCasting ?: MutableStateFlow(false),
        service.castConnectionHandler?.castIsPlaying ?: MutableStateFlow(false)
    ) { localPlaying, isCasting, castPlaying ->
        if (isCasting) castPlaying else localPlaying
    }.stateIn(
        scope,
        SharingStarted.Lazily,
        player.playbackState != STATE_ENDED && player.playWhenReady
    )
    
    val mediaMetadata = MutableStateFlow(player.currentMetadata)
    val currentSong =
        mediaMetadata.flatMapLatest {
            database.song(it?.id)
        }
    val currentLyrics = mediaMetadata.flatMapLatest { mediaMetadata ->
        database.lyrics(mediaMetadata?.id)
    }
    val currentFormat =
        mediaMetadata.flatMapLatest { mediaMetadata ->
            database.format(mediaMetadata?.id)
        }

    /** True while the CURRENT song is in the "No me gusta" store (live: current mediaId × DislikeStore). */
    val currentSongDisliked: kotlinx.coroutines.flow.StateFlow<Boolean> =
        combine(mediaMetadata, service.dislikeStore.disliked) { meta, disliked ->
            val id = meta?.id
            id != null && id in disliked.songs
        }.stateIn(scope, SharingStarted.Lazily, false)

    /** Autoplay suggestion chips for the queue footer (YT Music parity); see MusicService.autoplayChips. */
    val autoplayChips: kotlinx.coroutines.flow.StateFlow<List<AutoplayChip>> = service.autoplayChips

    /** The chip currently steering the autoplay (default = the "related" chip of the live seed). */
    val autoplaySelectedChip: kotlinx.coroutines.flow.StateFlow<AutoplayChip?> = service.autoplaySelectedChip

    /**
     * Pending "¿volver a la cola anterior?" offer, or null. Read-only mirror of
     * [MusicService.previousQueueOffer]; the shell shows it as a snackbar.
     */
    val previousQueueOffer: kotlinx.coroutines.flow.StateFlow<PreviousQueueOffer?> =
        service.previousQueueOffer

    /**
     * Accept the offer. Blocked for a Listen Together GUEST for the same reason as
     * [startRadioSeamlessly]: a guest must never re-point the shared queue.
     */
    fun resumePreviousQueue() {
        if (shouldBlockPlaybackChanges?.invoke() == true) {
            Timber.tag(TAG).d("resumePreviousQueue blocked - Listen Together guest")
            return
        }
        try {
            service.resumePreviousQueue()
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error in resumePreviousQueue")
        }
    }

    /** The offer was declined or expired on its own. Never touches playback. */
    fun dismissPreviousQueueOffer() {
        try {
            service.dismissPreviousQueueOffer()
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error in dismissPreviousQueueOffer")
        }
    }

    val queueTitle = MutableStateFlow<String?>(null)
    val queueWindows = MutableStateFlow<List<Timeline.Window>>(emptyList())
    val currentMediaItemIndex = MutableStateFlow(-1)
    val currentWindowIndex = MutableStateFlow(-1)

    val shuffleModeEnabled = MutableStateFlow(false)
    val repeatMode = MutableStateFlow(REPEAT_MODE_OFF)

    val canSkipPrevious = MutableStateFlow(true)
    val canSkipNext = MutableStateFlow(true)

    val error = MutableStateFlow<PlaybackException?>(null)
    val isMuted = service.isMuted
    val videoMode = service.videoMode
    val videoUrl = service.videoUrl
    val mixActive = service.mixActive

    /**
     * Timeline boundary between the user's own list and the infinite radio: a queue window whose
     * `firstPeriodIndex` is `< listQueueSize` came from the list he started, anything at or after it was
     * appended by the radio. `0` means "no list context" (a radio/mix queue, or an externally adopted
     * one) — treat the whole queue as one block then. Read-only mirror of MusicService.listQueueSize.
     */
    val listQueueSize = service.listQueueSize

    // True only while a crossfade swap is in progress (backed by MusicService's MutableStateFlow). The UI
    // reads this to react to the swap (e.g. suppress the spurious null-item transition the fading player fires).
    val isCrossfading: kotlinx.coroutines.flow.StateFlow<Boolean> = service.isCrossfadingFlow
    /**
     * Metadata of the track that is still AUDIBLE while a crossfade swap is in flight (the outgoing, fading
     * player), null whenever no swap is in flight. Only the LYRICS view reads it: everything else — the
     * now-playing UI, notification, widget, Android Auto — keeps following [mediaMetadata], which the swap
     * still publishes at the exact same moment it always did.
     */
    val crossfadeOutgoingMetadata = service.crossfadeOutgoingMetadata

    /**
     * Live position of that outgoing player, or null when it is not readable (no swap, already committed,
     * released). Callers MUST fall back to [player].currentPosition on null. Main thread only.
     */
    fun crossfadeOutgoingPositionMs(): Long? =
        try {
            service.crossfadeOutgoingPositionMs()
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error reading crossfade outgoing position")
            null
        }

    fun toggleVideoMode() = service.toggleVideoMode()
    fun exitVideoMode() = service.exitVideoMode()

    /**
     * Reload the currently-playing track forcing the Opus (WebM/Opus) audio format, continuing playback at
     * the current position. Delegates to [MusicService.refetchCurrentInOpus].
     */
    fun refetchCurrentInOpus() {
        try {
            service.refetchCurrentInOpus()
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error in refetchCurrentInOpus")
        }
    }

    val waitingForNetworkConnection = service.waitingForNetworkConnection
    
    
    var shouldBlockPlaybackChanges: (() -> Boolean)? = null
    
    
    @Volatile
    var allowInternalSync: Boolean = false

    var onSkipPrevious: (() -> Unit)? = null
    var onSkipNext: (() -> Unit)? = null

    private var attachedPlayer: Player? = null

    init {
        try {
            
            scope.launch {
                service.playerFlow.collect { newPlayer ->
                    if (newPlayer != null && newPlayer != attachedPlayer) {
                        updateAttachedPlayer(newPlayer)
                    }
                }
            }
            
            
            if (attachedPlayer == null && service.isPlayerReady.value) {
                 updateAttachedPlayer(player)
            }

            Timber.tag(TAG).d("PlayerConnection flow observer registered")
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Failed to initialize PlayerConnection listener or state")
            
            throw e
        }
    }

    private fun updateAttachedPlayer(newPlayer: Player) {
        attachedPlayer?.removeListener(this)
        attachedPlayer = newPlayer
        newPlayer.addListener(this)
        
        
        playbackState.value = newPlayer.playbackState
        playWhenReady.value = newPlayer.playWhenReady
        // Re-attaching on reconnect / player swap: if the new player momentarily has no current item (null
        // metadata), KEEP the last-known metadata instead of blanking the now-playing UI. The next real
        // transition / onMediaMetadataChanged refreshes it.
        mediaMetadata.value = newPlayer.currentMetadata ?: mediaMetadata.value
        queueTitle.value = service.queueTitle
        queueWindows.value = newPlayer.getQueueWindows()
        currentWindowIndex.value = newPlayer.getCurrentQueueIndex()
        currentMediaItemIndex.value = newPlayer.currentMediaItemIndex
        shuffleModeEnabled.value = newPlayer.shuffleModeEnabled
        repeatMode.value = newPlayer.repeatMode
        
        Timber.tag(TAG).d("Attached to new player instance: $newPlayer")
    }

    fun playQueue(queue: Queue) {
        if (!playerReadinessFlow.value) {
            Timber.tag(TAG).w("playQueue called before player ready; delegating to service")
        }
        try {
            service.playQueue(queue)
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error in playQueue")
            throw e
        }
    }

    fun startRadioSeamlessly() {
        
        if (shouldBlockPlaybackChanges?.invoke() == true) {
            Timber.tag("PlayerConnection").d("startRadioSeamlessly blocked - Listen Together guest")
            return
        }
        if (!playerReadinessFlow.value) {
            Timber.tag(TAG).w("startRadioSeamlessly called before player ready; delegating to service")
        }
        try {
            service.startRadioSeamlessly()
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error in startRadioSeamlessly")
            throw e
        }
    }

    fun playNext(item: MediaItem) = playNext(listOf(item))

    fun playNext(items: List<MediaItem>) {
        
        if (!allowInternalSync && shouldBlockPlaybackChanges?.invoke() == true) {
            Timber.tag("PlayerConnection").d("playNext blocked - Listen Together guest")
            return
        }
        try {
            service.playNext(items)
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error in playNext")
            throw e
        }
    }

    fun addToQueue(item: MediaItem) = addToQueue(listOf(item))

    fun addToQueue(items: List<MediaItem>) {
        
        if (!allowInternalSync && shouldBlockPlaybackChanges?.invoke() == true) {
            Timber.tag("PlayerConnection").d("addToQueue blocked - Listen Together guest")
            return
        }
        try {
            service.addToQueue(items)
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error in addToQueue")
            throw e
        }
    }

    fun toggleLike() {
        try {
            service.toggleLike()
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error in toggleLike")
        }
    }

    fun dislikeCurrentSong() {
        try {
            service.dislikeCurrentSong()
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error in dislikeCurrentSong")
        }
    }

    /**
     * Toggleable dislike for the current song: undo (store-only removal, no skip) if already disliked,
     * else the full dislike flow (dislike + unlike-if-liked via UPSERT + purge + skip). Observe
     * [currentSongDisliked] for the live state.
     */
    fun toggleDislikeCurrentSong() {
        try {
            service.toggleDislikeCurrentSong()
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error in toggleDislikeCurrentSong")
        }
    }

    /** UI → service: the full-screen player sheet was expanded (true) or collapsed (false). */
    fun setPlayerSheetExpanded(expanded: Boolean) {
        try {
            service.setPlayerSheetExpanded(expanded)
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error in setPlayerSheetExpanded")
        }
    }

    /** Re-seed the autoplay tail from [chip]'s endpoint (blocked for Listen Together guests, like
     *  startRadioSeamlessly — a guest must never mutate the shared queue). */
    fun selectAutoplayChip(chip: AutoplayChip) {
        if (shouldBlockPlaybackChanges?.invoke() == true) {
            Timber.tag(TAG).d("selectAutoplayChip blocked - Listen Together guest")
            return
        }
        try {
            service.selectAutoplayChip(chip)
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error in selectAutoplayChip")
        }
    }

    /**
     * Set (or clear) the active Home MOOD that biases the infinite radio's seed. Non-null [params] makes the
     * next radio seed/append come from that mood's Home feed instead of the last song (still taste/relatedness
     * ordered); null restores last-song seeding. Delegates to [MusicService.setActiveMood]. Called by the Home
     * mood UI.
     */
    fun setActiveMood(params: String?, title: String?) {
        try {
            service.setActiveMood(params, title)
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error in setActiveMood")
        }
    }

    fun toggleMute() {
        service.toggleMute()
    }

    fun setMuted(muted: Boolean) {
        service.setMuted(muted)
    }

    fun toggleLibrary() {
        try {
            service.toggleLibrary()
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error in toggleLibrary")
        }
    }

    
    fun togglePlayPause() {
        try {
            val castHandler = service.castConnectionHandler
            if (castHandler?.isCasting?.value == true) {
                if (castHandler.castIsPlaying.value) {
                    castHandler.pause()
                } else {
                    castHandler.play()
                }
            } else {
                player.togglePlayPause()
            }
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error in togglePlayPause")
        }
    }
    
    
    fun play() {
        try {
            val castHandler = service.castConnectionHandler
            if (castHandler?.isCasting?.value == true) {
                castHandler.play()
            } else {
                if (player.playbackState == Player.STATE_IDLE) {
                    player.prepare()
                }
                player.playWhenReady = true
            }
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error in play")
        }
    }
    
    
    fun pause() {
        try {
            val castHandler = service.castConnectionHandler
            if (castHandler?.isCasting?.value == true) {
                castHandler.pause()
            } else {
                player.playWhenReady = false
            }
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error in pause")
        }
    }

    
    fun seekTo(position: Long) {
        try {
            val castHandler = service.castConnectionHandler
            if (castHandler?.isCasting?.value == true) {
                castHandler.seekTo(position)
            } else {
                player.seekTo(position)
            }
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error in seekTo")
        }
    }

    fun seekToNext() {
        try {
            
            val castHandler = service.castConnectionHandler
            if (castHandler?.isCasting?.value == true) {
                castHandler.skipToNext()
                return
            }
            player.seekToNext()
            if (player.playbackState == Player.STATE_IDLE || player.playbackState == Player.STATE_ENDED) {
                player.prepare()
            }
            player.playWhenReady = true
            onSkipNext?.invoke()
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error in seekToNext")
        }
    }

    var onRestartSong: (() -> Unit)? = null

    fun seekToPrevious() {
        try {
            
            val castHandler = service.castConnectionHandler
            if (castHandler?.isCasting?.value == true) {
                castHandler.skipToPrevious()
                return
            }

            
            
            if (player.currentPosition > 3000 || !player.hasPreviousMediaItem()) {
                player.seekTo(0)
                if (player.playbackState == Player.STATE_IDLE || player.playbackState == Player.STATE_ENDED) {
                    player.prepare()
                }
                player.playWhenReady = true
                onRestartSong?.invoke()
            } else {
                
                player.seekToPreviousMediaItem()
                if (player.playbackState == Player.STATE_IDLE || player.playbackState == Player.STATE_ENDED) {
                    player.prepare()
                }
                player.playWhenReady = true
                onSkipPrevious?.invoke()
            }
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error in seekToPrevious")
        }
    }

    override fun onPlaybackStateChanged(state: Int) {
        playbackState.value = state
        error.value = player.playerError
    }

    override fun onPlayWhenReadyChanged(
        newPlayWhenReady: Boolean,
        reason: Int,
    ) {
        playWhenReady.value = newPlayWhenReady
    }

    override fun onMediaItemTransition(
        mediaItem: MediaItem?,
        reason: Int,
    ) {
        // During a crossfade swap we're briefly still attached to the OUTGOING (fading) player, whose
        // capped/repeat-off queue ends and fires a null-item transition. Never let that null blank the whole
        // player UI (artwork + controls are gated on non-null metadata). Keep the last metadata, falling back
        // to the live player's current item; only update when we actually have something.
        val newMeta = mediaItem?.metadata ?: player.currentMediaItem?.metadata
        // Suppress a NULL transition ONLY during an active crossfade swap (the outgoing fading player's capped
        // queue fires a spurious null-item transition we must ignore). OUTSIDE a crossfade a null is real and a
        // non-null is a genuine new track — always update, so the artwork never freezes on the previous cover.
        if (newMeta != null || !service.crossfadingNow) {
            mediaMetadata.value = newMeta
        }
        currentMediaItemIndex.value = player.currentMediaItemIndex
        currentWindowIndex.value = player.getCurrentQueueIndex()
        updateCanSkipPreviousAndNext()
    }

    override fun onMediaMetadataChanged(mediaMetadata: androidx.media3.common.MediaMetadata) {
        // Media3's combined metadata changed for the current item (async title/artwork resolution, or a new
        // item becoming current). Refresh our now-playing StateFlow from the item's tag so the cover — and
        // anything that reads the current song, e.g. add-to-playlist — never stays stuck on the previous track.
        // Only update when the tag is present (don't blank on a transient null).
        player.currentMediaItem?.metadata?.let { this.mediaMetadata.value = it }
    }

    override fun onTimelineChanged(
        timeline: Timeline,
        reason: Int,
    ) {
        queueWindows.value = player.getQueueWindows()
        queueTitle.value = service.queueTitle
        currentMediaItemIndex.value = player.currentMediaItemIndex
        currentWindowIndex.value = player.getCurrentQueueIndex()
        updateCanSkipPreviousAndNext()
    }

    override fun onShuffleModeEnabledChanged(enabled: Boolean) {
        shuffleModeEnabled.value = enabled
        queueWindows.value = player.getQueueWindows()
        currentWindowIndex.value = player.getCurrentQueueIndex()
        updateCanSkipPreviousAndNext()
    }

    override fun onRepeatModeChanged(mode: Int) {
        repeatMode.value = mode
        updateCanSkipPreviousAndNext()
    }

    override fun onPlayerErrorChanged(playbackError: PlaybackException?) {
        if (playbackError != null) {
            reportException(playbackError)
        }
        error.value = playbackError
    }

    private fun updateCanSkipPreviousAndNext() {
        if (!player.currentTimeline.isEmpty && player.currentMediaItemIndex != androidx.media3.common.C.INDEX_UNSET && player.currentMediaItemIndex >= 0 && player.currentMediaItemIndex < player.currentTimeline.windowCount) {
            val window =
                player.currentTimeline.getWindow(player.currentMediaItemIndex, Timeline.Window())
            canSkipPrevious.value = player.isCommandAvailable(COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM) ||
                    !window.isLive ||
                    player.isCommandAvailable(COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
            canSkipNext.value = window.isLive &&
                    window.isDynamic ||
                    player.isCommandAvailable(COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
        } else {
            canSkipPrevious.value = false
            canSkipNext.value = false
        }
    }

    fun dispose() {
        try {
            attachedPlayer?.removeListener(this)
            attachedPlayer = null
            Timber.tag(TAG).d("PlayerConnection disposed successfully")
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error during PlayerConnection disposal")
        }
    }
}