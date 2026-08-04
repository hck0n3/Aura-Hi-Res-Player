

package iad1tya.echo.music.lyrics

import android.content.Context
import com.music.innertube.YouTube

object YouTubeSubtitleLyricsProvider : LyricsProvider {
    override val name = "Aura Hi-Res (subtítulos)"

    override fun isEnabled(context: Context) = true

    override suspend fun getLyrics(
        id: String,
        title: String,
        artist: String,
        duration: Int,
        album: String?,
    ): Result<String> = YouTube.transcript(id)
}
