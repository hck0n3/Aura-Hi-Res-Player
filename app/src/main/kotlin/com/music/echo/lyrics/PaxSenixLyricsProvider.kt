package iad1tya.echo.music.lyrics

import android.content.Context
import com.music.paxsenix.Paxsenix
import iad1tya.echo.music.constants.EnablePaxsenixKey
import iad1tya.echo.music.utils.dataStore
import iad1tya.echo.music.utils.get
import timber.log.Timber
import kotlin.coroutines.cancellation.CancellationException

object PaxSenixLyricsProvider : LyricsProvider {
    private const val TAG = "PaxSenixProvider"

    override val name = "Paxsenix"

    override fun isEnabled(context: Context): Boolean {
        
        val enabled = context.dataStore[EnablePaxsenixKey] ?: true
        if (enabled) {
            Paxsenix.init(context)
        }
        return enabled
    }

    override suspend fun getLyrics(
        id: String,
        title: String,
        artist: String,
        duration: Int,
        album: String?,
    ): Result<String> {
        Timber.tag(TAG).d("getLyrics: title='$title', artist='$artist', duration=$duration")
        return runCatching {
            Paxsenix.getLyrics(title, artist, duration, album)
        }.getOrElse { e ->
            // Silent failure — no stacktrace spam
            when (e) {
                is CancellationException -> throw e
                else -> {
                    val msg = e.message?.take(120) ?: e.javaClass.simpleName
                    Timber.tag(TAG).d("Lyrics fetch failed: $msg")
                    throw IllegalStateException("Lyrics unavailable")
                }
            }
        }
    }

    override suspend fun getAllLyrics(
        id: String,
        title: String,
        artist: String,
        duration: Int,
        album: String?,
        callback: (String) -> Unit,
    ) {
        Timber.tag(TAG).d("getAllLyrics called")
        runCatching {
            Paxsenix.getAllLyrics(title, artist, duration, album, callback)
        }.onFailure { e ->
            when (e) {
                is CancellationException -> throw e
                else -> {
                    // Feed the breaker from this path too, so a 403 seen while browsing all providers
                    // mutes Paxsenix for the per-song path as well.
                    LyricsProviderCircuitBreaker.recordFailure(name, e)
                    val msg = e.message?.take(120) ?: e.javaClass.simpleName
                    Timber.tag(TAG).d("Error fetching lyrics: $msg")
                }
            }
        }
    }
}