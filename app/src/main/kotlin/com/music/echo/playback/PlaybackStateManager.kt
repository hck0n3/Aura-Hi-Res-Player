package iad1tya.echo.music.playback

import iad1tya.echo.music.models.MediaMetadata
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class PlaybackStateManager {
    val mixActive = MutableStateFlow(false)
    val videoMode = MutableStateFlow(false)
    val videoUrl = MutableStateFlow<String?>(null)

    val currentMediaMetadata = MutableStateFlow<MediaMetadata?>(null)

    @Volatile var videoModeMediaId: String? = null
    @Volatile var videoModeOriginalUri: String? = null
    @Volatile var videoModeIsMuxedPodcast = false
    @Volatile var userHasUsedVideo = false
    // Mirrors whether the full-screen player sheet is currently EXPANDED (driven by the UI via
    // PlayerConnection.setPlayerSheetExpanded). Same pattern as userHasUsedVideo: a cheap @Volatile
    // signal the service can read from any thread to gate speculative work (e.g. the video-connection
    // warm-up) to moments the user is actually looking at the player. Never touches playback.
    @Volatile var playerSheetExpanded = false
}
