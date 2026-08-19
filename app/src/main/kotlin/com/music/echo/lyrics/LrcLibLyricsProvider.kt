package iad1tya.echo.music.lyrics

import android.content.Context
import com.music.lrclib.LrcLib
import iad1tya.echo.music.constants.EnableLrcLibKey
import iad1tya.echo.music.utils.dataStore
import iad1tya.echo.music.utils.get
import timber.log.Timber
import kotlin.coroutines.cancellation.CancellationException

object LrcLibLyricsProvider : LyricsProvider {
    override val name = "LrcLib"

    override fun isEnabled(context: Context): Boolean = context.dataStore[EnableLrcLibKey] ?: true

    override suspend fun getLyrics(
        id: String,
        title: String,
        artist: String,
        duration: Int,
        album: String?,
    ): Result<String> {
        return runCatching {
            LrcLib.getLyrics(title, artist, duration, album)
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
            LrcLib.getAllLyrics(title, artist, duration, album, callback)
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