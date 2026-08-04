

package iad1tya.echo.music.utils

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter


enum class PlaybackLogLevel {
    INFO,
    WARNING,
    ERROR,
    DEBUG,
    BOT 
}


data class PlaybackLogEntry(
    val timestamp: String,
    val level: PlaybackLogLevel,
    val message: String,
    val details: String? = null
)


object PlaybackLogManager {
    private const val MAX_LOG_ENTRIES = 500
    
    private val _logs = MutableStateFlow<List<PlaybackLogEntry>>(emptyList())
    val logs: StateFlow<List<PlaybackLogEntry>> = _logs.asStateFlow()
    
    
    fun log(level: PlaybackLogLevel, message: String, details: String? = null) {
        val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss.SSS"))
        val entry = PlaybackLogEntry(timestamp, level, message, details)

        // MIRROR FAILURES TO THE SHAREABLE LOG. This class is a StateFlow in RAM: its entries reach the
        // owner only if the app CRASHES (CrashHandler embeds the last 25) or if the user happens to open
        // the in-app playback dialog. So for the most common complaint of all — the app does not crash,
        // it just does not play — every playback verdict the app had already computed died with the
        // process and the shared log contained nothing about it.
        //
        // Only WARNING and ERROR are mirrored. INFO/DEBUG/BOT stay in RAM: INFO fires on every
        // successful resolve and per queue operation, and mirroring it would trade the signal for
        // volume in a 256 KB capped file. Failures are rare by definition, so this cannot flood the log
        // and costs nothing while playback is healthy.
        when (level) {
            PlaybackLogLevel.ERROR -> timber.log.Timber.tag("PLAYBACK").e(detailed(message, details))
            PlaybackLogLevel.WARNING -> timber.log.Timber.tag("PLAYBACK").w(detailed(message, details))
            else -> Unit
        }
        
        
        val currentLogs = _logs.value.toMutableList()
        currentLogs.add(entry)
        
        
        _logs.value = if (currentLogs.size > MAX_LOG_ENTRIES) {
            currentLogs.takeLast(MAX_LOG_ENTRIES)
        } else {
            currentLogs
        }
    }
    
    
    fun clearLogs() {
        _logs.value = emptyList()
    }

    private fun detailed(message: String, details: String?): String =
        if (details.isNullOrBlank()) message else "$message — $details"
}
