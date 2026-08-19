package iad1tya.echo.music.lyrics

import android.content.Context
import com.music.unison.Unison
import iad1tya.echo.music.constants.UnisonLyricsEnabledKey
import iad1tya.echo.music.utils.dataStore
import iad1tya.echo.music.utils.get
import timber.log.Timber
import kotlin.coroutines.cancellation.CancellationException

object UnisonLyricsProvider : LyricsProvider {
    override val name: String = "Unison"

    override fun isEnabled(context: Context): Boolean =
        context.dataStore[UnisonLyricsEnabledKey] ?: true

    override suspend fun getLyrics(
        id: String,
        title: String,
        artist: String,
        duration: Int,
        album: String?,
    ): Result<String> {
        return runCatching {
            Unison.getLyrics(
                videoId = id,
                title = title,
                artist = artist,
                album = album,
                durationSeconds = duration
            ).map { convertIfTTML(it) }
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
            Unison.getAllLyrics(
                videoId = id,
                title = title,
                artist = artist,
                album = album,
                durationSeconds = duration,
                callback = { callback(convertIfTTML(it)) }
            )
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

    private fun convertIfTTML(content: String): String {
        return if (content.trimStart().startsWith("<tt", ignoreCase = true)) {
            val parsedLines = iad1tya.echo.music.betterlyrics.TTMLParser.parseTTML(content)
            iad1tya.echo.music.betterlyrics.TTMLParser.toLRC(parsedLines)
        } else {
            content
        }
    }
}