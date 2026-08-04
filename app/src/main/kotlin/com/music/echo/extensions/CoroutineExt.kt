

package iad1tya.echo.music.extensions

import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import iad1tya.echo.music.utils.reportException

fun <T> Flow<T>.collect(
    scope: CoroutineScope,
    action: suspend (value: T) -> Unit,
) {
    scope.launch {
        collect(action)
    }
}

fun <T> Flow<T>.collectLatest(
    scope: CoroutineScope,
    action: suspend (value: T) -> Unit,
) {
    scope.launch {
        collectLatest(action)
    }
}

/**
 * The scope-level exception handler used by the playback service's fire-and-forget coroutines.
 *
 * IT IS NO LONGER SILENT, and the name is kept only so this stays a one-file change to code the
 * playback service owns. It used to be `{ _, _ -> }` — a literally empty body — which made it the
 * single most expensive blind spot in the app:
 *
 *   - `MusicService.playQueue` runs `queue.getInitialStatus()` under it. Offline, a YouTube 4xx, or
 *     three exhausted retries threw straight in here and vanished. The user taps a song or a playlist
 *     and NOTHING happens: no playback, no toast, no error state, and — until now — not one byte in
 *     the log. That is the canonical "no reproduce", and it was undiagnosable by construction.
 *   - `maybeLoadMoreQueuePages` dies the same way, so the queue silently stops paginating and music
 *     just ends mid-session ("se paró solo").
 *   - `startRadioSeamlessly`'s seeding job, the automix builders and the autoplay refreshers all sit
 *     under it too.
 *
 * Reporting costs nothing in the normal case: this body runs ONLY when a coroutine has already failed.
 * `reportException` dedups by signature, so even a failure that repeats on every track cannot flood
 * the 256 KB log. CancellationException never reaches a CoroutineExceptionHandler, so ordinary job
 * cancellation (every skip, every queue rebuild) stays silent as before.
 */
val SilentHandler = CoroutineExceptionHandler { context, throwable ->
    // The coroutine's name, when one was set, is the cheapest possible attribution: it tells the owner
    // WHICH background job died without needing a deobfuscated frame.
    val where = context[kotlinx.coroutines.CoroutineName]?.name ?: "unnamed"
    timber.log.Timber.tag("REPORT").e("background coroutine failed [$where]")
    reportException(throwable)
}
