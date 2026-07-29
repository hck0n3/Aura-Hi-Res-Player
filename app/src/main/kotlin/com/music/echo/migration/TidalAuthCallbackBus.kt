package iad1tya.echo.music.migration

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Hands the Tidal OAuth redirect (echomusic://tidal-callback?code=...&state=...) from
 * MainActivity.handleDeepLinkIntent to whichever migration screen is waiting for it.
 *
 * replay=1 on purpose: the redirect usually arrives through onNewIntent while the browser Custom Tab
 * is still closing — the collecting screen may not be resumed yet. The replayed value lets it catch
 * the callback when it comes back; [clear] drops it once consumed so a stale code is never re-processed.
 */
object TidalAuthCallbackBus {

    data class Callback(
        val code: String?,
        val state: String?,
        /** OAuth error code (e.g. "access_denied" when the user cancels), if Tidal sent one. */
        val error: String?,
    )

    private val _events = MutableSharedFlow<Callback>(replay = 1, extraBufferCapacity = 1)
    val events: SharedFlow<Callback> = _events.asSharedFlow()

    fun emit(code: String?, state: String?, error: String?) {
        _events.tryEmit(Callback(code = code, state = state, error = error))
    }

    /** Call after consuming a callback so re-subscribers don't replay a used authorization code. */
    fun clear() {
        _events.resetReplayCache()
    }
}
