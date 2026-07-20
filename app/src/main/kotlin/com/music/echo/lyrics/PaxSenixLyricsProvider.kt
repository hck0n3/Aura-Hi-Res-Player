

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
        return try {
            Paxsenix.getLyrics(title, artist, duration, album)
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Exception in getLyrics")
            Result.failure(e)
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
        try {
            Paxsenix.getAllLyrics(title, artist, duration, album, callback)
        } catch (e: CancellationException) {
            // Song skipped while listing — propagate so the job actually stops.
            throw e
        } catch (e: Exception) {
            // Feed the breaker from this path too, so a 403 seen while browsing all providers
            // mutes Paxsenix for the per-song path as well.
            LyricsProviderCircuitBreaker.recordFailure(name, e)
            Timber.tag(TAG).w(e, "Error fetching lyrics from Paxsenix")
        }
    }
}
