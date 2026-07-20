package iad1tya.echo.music.lyrics

import android.os.SystemClock
import io.ktor.client.plugins.ResponseException
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.cancellation.CancellationException

/**
 * In-memory circuit breaker for third-party lyrics providers.
 *
 * A public lyrics endpoint that answers **4xx** is telling us something about *us*, not about the
 * song: Cloudflare has blocked this IP (403), the key is bad (401/404 on every request), or we are
 * rate-limited (429). Re-asking it for the next song, and the one after that, cannot change the
 * answer — it only burns radio, floods logcat, and (before this class existed) filed a Crashlytics
 * non-fatal per song per provider for a condition we cannot fix from the client.
 *
 * So the first 4xx mutes that provider for [COOLDOWN_MS]; the lyrics pipeline then skips it
 * entirely and falls through to the providers that still answer.
 *
 * **5xx and network/timeout failures are deliberately NOT breaking conditions** — those are
 * transient (provider restarting, tunnel flapping, user in a lift) and the provider deserves the
 * next song's request.
 *
 * State is intentionally in memory only: no new DataStore key, no new DB table. A cooldown that
 * dies with the process is the right lifetime — a fresh process is often a fresh network, and the
 * worst case of forgetting is one wasted request.
 *
 * Timing uses [SystemClock.elapsedRealtime] rather than wall clock so that a user (or the network)
 * moving the system clock cannot extend a mute to "forever" or cut it to zero.
 *
 * Thread-safe: several songs resolve concurrently (service collector, inline lyrics view, preload),
 * and every provider in the order races in parallel, so all state lives in a [ConcurrentHashMap].
 */
object LyricsProviderCircuitBreaker {
    /** How long a provider stays muted after a 4xx. Long enough to cover a listening session. */
    private const val COOLDOWN_MS = 6L * 60L * 60L * 1000L

    /** Defensive bound while walking `cause` chains — a self-referencing cause must not hang us. */
    private const val MAX_CAUSE_DEPTH = 8

    /** provider name -> elapsedRealtime() after which the provider may be tried again. */
    private val mutedUntil = ConcurrentHashMap<String, Long>()

    /** True while [providerName] is muted by a recent 4xx. Cheap enough to call per song. */
    fun isBlocked(providerName: String): Boolean {
        val until = mutedUntil[providerName] ?: return false
        if (SystemClock.elapsedRealtime() >= until) {
            // Remove only if nobody re-muted it in the meantime.
            mutedUntil.remove(providerName, until)
            return false
        }
        return true
    }

    /** A provider that just answered with real lyrics is healthy — drop any stale mute. */
    fun recordSuccess(providerName: String) {
        mutedUntil.remove(providerName)
    }

    /**
     * Mutes [providerName] iff [error] (or anything in its cause chain) is an HTTP 4xx.
     * Anything else — 5xx, socket timeouts, DNS, cancellation — is left alone on purpose.
     */
    fun recordFailure(providerName: String, error: Throwable?) {
        val status = clientErrorStatusOrNull(error) ?: return
        mutedUntil[providerName] = SystemClock.elapsedRealtime() + COOLDOWN_MS
        Timber.w(
            "Lyrics provider %s answered HTTP %d — muting it for %d h (client-side block, not a crash)",
            providerName,
            status,
            COOLDOWN_MS / 3_600_000L,
        )
    }

    /**
     * Walks the cause chain for a ktor [ResponseException] and returns its status **only when it is
     * 4xx**. Returns null for 5xx (transient), for cancellation, and when no HTTP status is present.
     *
     * The chain walk matters: providers wrap their failures (Paxsenix surfaces the ktor exception
     * through `runCatching` and a provider-level `try`), so the status is rarely the top throwable.
     */
    private fun clientErrorStatusOrNull(error: Throwable?): Int? {
        var current = error
        var depth = 0
        while (current != null && depth++ < MAX_CAUSE_DEPTH) {
            // Cancellation is normal control flow (user skipped the song) — never a breaker trip.
            if (current is CancellationException) return null
            val status = (current as? ResponseException)?.response?.status?.value
            // ONLY the statuses that are about US, never about the song. A blanket 400..499 muted healthy
            // providers on a per-song miss: YouTube Subtitles and YouTube Lyrics both run on an
            // expectSuccess client, and get_transcript answers 4xx for any video with no transcript panel —
            // so one song without subtitles would have killed that provider for the whole library for 6h.
            // 400/404/410 describe the resource; 401/403/429 describe the caller.
            if (status != null) return status.takeIf { it == 401 || it == 403 || it == 429 }
            val next = current.cause
            if (next === current) return null
            current = next
        }
        return null
    }
}
