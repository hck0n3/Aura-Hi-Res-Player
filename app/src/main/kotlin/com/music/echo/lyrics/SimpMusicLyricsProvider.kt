package iad1tya.echo.music.lyrics

import android.content.Context
import iad1tya.echo.music.constants.EnableSimpMusicKey
import iad1tya.echo.music.utils.dataStore
import iad1tya.echo.music.utils.get
import com.music.simpmusic.SimpMusicLyrics
import timber.log.Timber
import kotlin.coroutines.cancellation.CancellationException

object SimpMusicLyricsProvider : LyricsProvider {
    override val name = "SimpMusic"

    override fun isEnabled(context: Context): Boolean = context.dataStore[EnableSimpMusicKey] ?: true

    override suspend fun getLyrics(
        id: String,
        title: String,
        artist: String,
        duration: Int,
        album: String?,
    ): Result<String> {
        return runCatching {
            SimpMusicLyrics.getLyrics(id, duration)
        }.getOrElse { e ->
            when (e) {
                is CancellationException -> throw e
                else -> {
                    val msg = e.message?.take(120) ?: e.javaClass.simpleName
                    Timber.tag(name).d("Lyrics fetch failed: $msg")
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
        runCatching {
            SimpMusicLyrics.getAllLyrics(id, duration, callback)
        }.onFailure { e ->
            when (e) {
                is CancellationException -> throw e
                else -> {
                    LyricsProviderCircuitBreaker.recordFailure(name, e)
                    val msg = e.message?.take(120) ?: e.javaClass.simpleName
                    Timber.tag(name).d("Error fetching lyrics: $msg")
                }
            }
        }
    }
}