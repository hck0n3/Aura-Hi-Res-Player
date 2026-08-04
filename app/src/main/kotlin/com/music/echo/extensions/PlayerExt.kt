

package iad1tya.echo.music.extensions

import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.Player.REPEAT_MODE_ALL
import androidx.media3.common.Player.REPEAT_MODE_OFF
import androidx.media3.common.Player.REPEAT_MODE_ONE
import androidx.media3.common.Timeline
import androidx.media3.common.TrackSelectionParameters
import iad1tya.echo.music.models.MediaMetadata
import java.util.ArrayDeque

fun Player.togglePlayPause() {
    if (!playWhenReady && playbackState == Player.STATE_IDLE) {
        prepare()
    }
    playWhenReady = !playWhenReady
}

fun Player.toggleRepeatMode() {
    repeatMode =
        when (repeatMode) {
            REPEAT_MODE_OFF -> REPEAT_MODE_ALL
            REPEAT_MODE_ALL -> REPEAT_MODE_ONE
            REPEAT_MODE_ONE -> REPEAT_MODE_OFF
            else -> throw IllegalStateException()
        }
}

fun Player.getQueueWindows(): List<Timeline.Window> {
    val timeline = currentTimeline
    if (timeline.isEmpty) {
        return emptyList()
    }
    val currentMediaItemIndex: Int = currentMediaItemIndex
    if (currentMediaItemIndex == C.INDEX_UNSET || currentMediaItemIndex < 0 || currentMediaItemIndex >= timeline.windowCount) {
        return emptyList()
    }
    
    val queue = ArrayDeque<Timeline.Window>()
    val queueSize = timeline.windowCount

    queue.add(timeline.getWindow(currentMediaItemIndex, Timeline.Window()))

    var firstMediaItemIndex = currentMediaItemIndex
    var lastMediaItemIndex = currentMediaItemIndex
    val shuffleModeEnabled = shuffleModeEnabled
    while ((firstMediaItemIndex != C.INDEX_UNSET || lastMediaItemIndex != C.INDEX_UNSET) && queue.size < queueSize) {
        if (lastMediaItemIndex != C.INDEX_UNSET) {
            lastMediaItemIndex =
                timeline.getNextWindowIndex(lastMediaItemIndex, REPEAT_MODE_OFF, shuffleModeEnabled)
            if (lastMediaItemIndex != C.INDEX_UNSET) {
                queue.add(timeline.getWindow(lastMediaItemIndex, Timeline.Window()))
            }
        }
        if (firstMediaItemIndex != C.INDEX_UNSET && queue.size < queueSize) {
            firstMediaItemIndex = timeline.getPreviousWindowIndex(
                firstMediaItemIndex,
                REPEAT_MODE_OFF,
                shuffleModeEnabled
            )
            if (firstMediaItemIndex != C.INDEX_UNSET) {
                queue.addFirst(timeline.getWindow(firstMediaItemIndex, Timeline.Window()))
            }
        }
    }
    return queue.toList()
}

fun Player.getCurrentQueueIndex(): Int {
    if (currentTimeline.isEmpty) {
        return -1
    }
    var currentMediaItemIndex = currentMediaItemIndex
    if (currentMediaItemIndex == C.INDEX_UNSET || currentMediaItemIndex < 0 || currentMediaItemIndex >= currentTimeline.windowCount) {
        return -1
    }
    
    var index = 0
    while (currentMediaItemIndex != C.INDEX_UNSET) {
        currentMediaItemIndex = currentTimeline.getPreviousWindowIndex(
            currentMediaItemIndex,
            REPEAT_MODE_OFF,
            shuffleModeEnabled
        )
        if (currentMediaItemIndex != C.INDEX_UNSET) {
            index++
        }
    }
    return index
}

val Player.currentMetadata: MediaMetadata?
    get() = currentMediaItem?.metadata

val Player.mediaItems: List<MediaItem>
    get() =
        object : AbstractList<MediaItem>() {
            override val size: Int
                get() = mediaItemCount

            override fun get(index: Int): MediaItem = getMediaItemAt(index)
        }

fun Player.findNextMediaItemById(mediaId: String): MediaItem? {
    for (i in currentMediaItemIndex until mediaItemCount) {
        if (getMediaItemAt(i).mediaId == mediaId) {
            return getMediaItemAt(i)
        }
    }
    return null
}

/**
 * Publishes this player's audio-offload REQUEST — the mode AND what the app needs offload to be
 * able to do. Offload hands the still-encoded stream to the DSP and lets the CPU sleep, so it is
 * the single biggest screen-off battery saving in the app; the request therefore stays as
 * permissive as it can honestly be.
 *
 * Why the two "required" flags are set at all. Verified against the media3 1.10.1 bytecode:
 *  - AudioTrackAudioOutputProvider.getOutputConfig hardcodes usePlaybackParameters = true on the
 *    OFFLOAD branch, so DefaultAudioSink.setPlaybackParameters really does forward tempo/pitch to
 *    AudioTrack.setPlaybackParams. (The DefaultRenderersFactory enableAudioTrackPlaybackParams
 *    flag only governs the PCM and passthrough branches — it never reaches offload.)
 *  - AudioTrackAudioOutput.setPlaybackParameters asks for AUDIO_FALLBACK_MODE_FAIL and then
 *    swallows the resulting IllegalArgumentException with a Log.w. On a HAL that does not export
 *    offloadVariableRateSupported=1 the speed silently stays at 1.0 while the Tempo & pitch dialog
 *    keeps showing the value the user picked. A control that looks like it works and does not is
 *    worse than no control, so the request has to make media3 DECLINE offload instead.
 *  - DefaultTrackSelector.rendererSupportsOffload is the only consumer of either flag:
 *      speed:   isSpeedChangeSupportRequired && no AUDIO_OFFLOAD_SPEED_CHANGE_SUPPORTED -> refused.
 *               BLANKET: costs offload on every device whose HAL lacks variable-rate offload.
 *      gapless: refused only when the FORMAT actually carries gapless data (encoderDelay or
 *               encoderPadding != 0) AND the device lacks gapless offload (media3 never reports it
 *               below API 33). Content without gapless data keeps offload.
 *
 * Hence the deliberate asymmetry:
 *  - Speed change is required DYNAMICALLY, only while the playback parameters are actually
 *    non-default. Requiring it unconditionally would trade every user's screen-off battery — a
 *    standing complaint — for a feature almost nobody touches. Because the requirement is derived
 *    from the player's own current parameters, this function rebuilds the whole truth on every
 *    call, so its two callers (the settings gate and onPlaybackParametersChanged in MusicService)
 *    cannot drift apart.
 *  - Gapless is required ALWAYS. It cannot be made dynamic: there is no "the user is relying on
 *    gapless now" signal, the gap simply appears between album tracks. Its cost is per-FORMAT
 *    rather than per-device, and anyone who reaches offload at all has already turned crossfade
 *    off, turned Safe Volume off and cleared the EQ (see the gate in MusicService) — i.e. is
 *    asking for the untouched path, and is exactly the listener who would hear that click.
 *
 * Interaction with the ForwardingAudioSink float DSP takeover (hiResDsp in MusicService): none,
 * and only in the safe direction. Both flags are pure extra AND-conditions inside
 * rendererSupportsOffload, so this can only ever move a stream from "offloaded, still encoded at
 * the sink" to "decoded PCM at the sink". The takeover arms on MimeTypes.AUDIO_RAW, i.e. only on
 * the PCM side, so this can never bypass the EQ — at worst it restores the DSP path.
 *
 * Safe to re-apply mid-playback: ExoPlayerImpl.setTrackSelectionParameters no-ops when the
 * parameters compare equal, and AudioOffloadPreferences.equals covers all three fields.
 */
fun Player.setOffloadEnabled(enabled: Boolean) {
    // Pitch counts too: on the offload path both go through the same AudioTrack.setPlaybackParams
    // call, gated by the same HAL capability. The exact comparison against 1f is safe because the
    // only writers are the Tempo & pitch dialog — whose reset lands on exactly 1f and 2^0 — and the
    // crossfade mirror of that same value.
    val needsSpeedChange = playbackParameters.speed != 1f || playbackParameters.pitch != 1f
    trackSelectionParameters = trackSelectionParameters.buildUpon()
        .setAudioOffloadPreferences(
            TrackSelectionParameters.AudioOffloadPreferences
                .Builder()
                .setAudioOffloadMode(
                    if (enabled) {
                        TrackSelectionParameters.AudioOffloadPreferences.AUDIO_OFFLOAD_MODE_ENABLED
                    } else {
                        TrackSelectionParameters.AudioOffloadPreferences.AUDIO_OFFLOAD_MODE_DISABLED
                    }
                )
                .setIsSpeedChangeSupportRequired(needsSpeedChange)
                .setIsGaplessSupportRequired(true)
                .build()
        ).build()
}
