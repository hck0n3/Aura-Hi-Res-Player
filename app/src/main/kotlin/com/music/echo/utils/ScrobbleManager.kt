package iad1tya.echo.music.utils

import android.content.Context
import iad1tya.echo.music.models.MediaMetadata
import iad1tya.echo.music.ui.screens.settings.ListenBrainzManager
import iad1tya.echo.music.utils.lastfm.LastFM
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Real scrobbling (Last.fm + ListenBrainz). Fully OPT-IN and network-only:
 *  - Last.fm submits only when [enableScrobbling] is on AND a session key is present (user logged in).
 *  - ListenBrainz submits only when [listenBrainzEnabled] is on AND a [listenBrainzToken] is set.
 * When nothing is enabled the play-time ticker never starts, so there is zero background work by default.
 *
 * NOTE: the constructor signature and the public method names are kept EXACTLY as the previous stub —
 * MusicService already calls onSongStart/onSongResume/onSongPause/onSongStop/onPlayerStateChanged.
 */
class ScrobbleManager(
    private val scope: CoroutineScope,
    var minSongDuration: Int = 30,
    var scrobbleDelayPercent: Float = 0.5f,
    var scrobbleDelaySeconds: Int = 50
) {
    var useNowPlaying = true
    var enableScrobbling = false
    var useSendLikes = false

    // ListenBrainz is routed through the same play-tracking so MusicService needs only one wiring point.
    var listenBrainzEnabled = false
    var listenBrainzToken = ""
    // Application context, only needed by the ListenBrainz submitter. Never used for anything else.
    var appContext: Context? = null

    private var currentMetadata: MediaMetadata? = null
    private var duration: Long? = null
    private var isPlaying = false

    private var playTimeSeconds = 0
    private var scrobbled = false
    private var startEpochMs = 0L
    private var trackJob: Job? = null
    private var scrobblingJob: Job? = null

    /** Any provider actually able to submit right now (used to decide whether to spend any work at all). */
    private fun lastFmActive() = enableScrobbling && LastFM.isInitialized() && LastFM.sessionKey != null
    private fun listenBrainzActive() = listenBrainzEnabled && listenBrainzToken.isNotBlank()
    private fun anyProviderActive() = lastFmActive() || listenBrainzActive()

    fun destroy() {
        trackJob?.cancel()
        scrobblingJob?.cancel()
    }

    fun onSongStart(metadata: MediaMetadata?, duration: Long? = null) {
        if (metadata?.id == currentMetadata?.id) return

        stopTracking()

        currentMetadata = metadata
        this.duration = duration
        playTimeSeconds = 0
        scrobbled = false
        startEpochMs = System.currentTimeMillis()

        if (metadata == null || metadata.id.isLocalMediaId()) return
        if (!anyProviderActive()) return

        val artists = metadata.artists.joinToString(", ") { it.name }

        // Last.fm "now playing"
        if (lastFmActive() && useNowPlaying) {
            scrobblingJob?.cancel()
            scrobblingJob = scope.launch {
                try {
                    LastFM.updateNowPlaying(
                        artist = artists.ifEmpty { "Unknown Artist" },
                        track = metadata.title,
                        album = metadata.album?.title,
                        duration = duration?.let { (it / 1000).toInt() }
                    )
                } catch (e: Exception) {
                    Timber.e(e, "Last.fm updateNowPlaying failed")
                }
            }
        }

        // ListenBrainz "playing_now"
        if (listenBrainzActive()) {
            val ctx = appContext
            if (ctx != null) {
                scope.launch {
                    try {
                        ListenBrainzManager.submitPlayingNow(
                            context = ctx,
                            token = listenBrainzToken.trim(),
                            title = metadata.title,
                            artistNames = artists,
                            releaseName = metadata.album?.title ?: "",
                            durationMs = duration ?: 0L,
                            positionMs = 0L
                        )
                    } catch (e: Exception) {
                        Timber.e(e, "ListenBrainz playing_now failed")
                    }
                }
            }
        }
    }

    fun onSongResume(metadata: MediaMetadata) {
        onPlayerStateChanged(true, metadata, duration)
    }

    fun onSongPause() {
        onPlayerStateChanged(false, currentMetadata, duration)
    }

    fun onPlayerStateChanged(isPlaying: Boolean, metadata: MediaMetadata?, duration: Long? = null) {
        this.isPlaying = isPlaying

        if (metadata != null && metadata.id != currentMetadata?.id) {
            onSongStart(metadata, duration)
        }

        if (isPlaying) {
            startTracking()
        } else {
            stopTracking()
        }
    }

    private fun startTracking() {
        trackJob?.cancel()
        if (scrobbled || currentMetadata == null || !anyProviderActive()) return

        trackJob = scope.launch {
            while (true) {
                delay(1000)
                playTimeSeconds++
                checkScrobbleThreshold()
            }
        }
    }

    private fun stopTracking() {
        trackJob?.cancel()
    }

    fun onSongStop() {
        stopTracking()
    }

    private fun checkScrobbleThreshold() {
        val meta = currentMetadata ?: return
        val dur = duration?.let { it / 1000 } ?: return

        if (scrobbled) return

        if (dur < minSongDuration && dur > 0) return

        val actualDuration = if (dur > 0) dur else 300L
        val percentThreshold = (actualDuration * scrobbleDelayPercent).toInt()
        val absoluteThreshold = scrobbleDelaySeconds
        val threshold = minOf(percentThreshold, absoluteThreshold)

        if (playTimeSeconds >= threshold) {
            scrobbled = true
            stopTracking()
            doScrobble(meta, actualDuration)
        }
    }

    private fun doScrobble(metadata: MediaMetadata, durationInSeconds: Long) {
        if (metadata.id.isLocalMediaId()) return
        val artists = metadata.artists.joinToString(", ") { it.name }

        val timestamp = System.currentTimeMillis() / 1000 - playTimeSeconds

        // Last.fm scrobble
        if (lastFmActive() && artists.isNotEmpty()) {
            scrobblingJob?.cancel()
            scrobblingJob = scope.launch {
                try {
                    LastFM.scrobble(
                        artist = artists,
                        track = metadata.title,
                        timestamp = timestamp,
                        album = metadata.album?.title,
                        duration = durationInSeconds.toInt()
                    )
                    Timber.d("Successfully scrobbled ${metadata.title}")
                } catch (e: Exception) {
                    Timber.e(e, "Last.fm scrobble failed")
                }
            }
        }

        // ListenBrainz "single" (finished) listen — submitted once the scrobble threshold is met, which
        // matches ListenBrainz's own guidance (>= half the track or a few minutes played).
        if (listenBrainzActive()) {
            val ctx = appContext
            if (ctx != null) {
                scope.launch {
                    try {
                        ListenBrainzManager.submitFinished(
                            context = ctx,
                            token = listenBrainzToken.trim(),
                            title = metadata.title,
                            artistNames = artists,
                            releaseName = metadata.album?.title ?: "",
                            durationMs = durationInSeconds * 1000,
                            startMs = if (startEpochMs > 0) startEpochMs else timestamp * 1000,
                            endMs = System.currentTimeMillis()
                        )
                    } catch (e: Exception) {
                        Timber.e(e, "ListenBrainz finished failed")
                    }
                }
            }
        }
    }
}
