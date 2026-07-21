

package iad1tya.echo.music.lyrics

import android.content.Context
import android.os.SystemClock
import android.util.LruCache
import iad1tya.echo.music.constants.LyricsProviderOrderKey
import iad1tya.echo.music.constants.PreferredLyricsProvider
import iad1tya.echo.music.constants.PreferredLyricsProviderKey
import iad1tya.echo.music.db.entities.LyricsEntity.Companion.LYRICS_NOT_FOUND
import iad1tya.echo.music.extensions.toEnum
import iad1tya.echo.music.models.MediaMetadata
import iad1tya.echo.music.utils.NetworkConnectivityObserver
import iad1tya.echo.music.utils.dataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber
import kotlin.coroutines.cancellation.CancellationException
import javax.inject.Inject

class LyricsHelper
@Inject
constructor(
    @ApplicationContext private val context: Context,
    private val networkConnectivity: NetworkConnectivityObserver,
) {
    
    private suspend fun resolveLyricsProviders(): List<LyricsProvider> {
        val preferences = context.dataStore.data.first()
        val orderString = preferences[LyricsProviderOrderKey].orEmpty()

        if (orderString.isNotBlank()) {
            return LyricsProviderRegistry.getOrderedProviders(orderString)
        }

        
        val preferredEnum = preferences[PreferredLyricsProviderKey]
            .toEnum(PreferredLyricsProvider.YOULYPLUS)
        val preferredName = LyricsProviderRegistry.getProviderNameForEnum(preferredEnum)
        val defaultOrder = LyricsProviderRegistry.getDefaultProviderOrder()
        val migratedOrder = listOf(preferredName) + defaultOrder.filter { it != preferredName }
        return migratedOrder.mapNotNull { LyricsProviderRegistry.getProviderByName(it) }
    }



    private val cache = LruCache<String, List<LyricsResult>>(MAX_CACHE_SIZE)
    private var currentLyricsJob: Job? = null

    suspend fun getLyrics(mediaMetadata: MediaMetadata): LyricsWithProvider {
        // Re-opening the same song within a session is instant, and the 2–3 fetchers that can
        // fire for a single song (service collector + inline view fallback + preload) dedupe
        // onto one cached result instead of each hitting the network. Keyed by song id so it
        // is coherent with how the result is read back below.
        cache.get(mediaMetadata.id)?.firstOrNull()?.let { cached ->
            return LyricsWithProvider(cached.lyrics, cached.providerName)
        }

        val isNetworkAvailable = try {
            networkConnectivity.isCurrentlyConnected()
        } catch (e: Exception) {
            true
        }
        if (!isNetworkAvailable) {
            return LyricsWithProvider(LYRICS_NOT_FOUND, "Unknown")
        }

        // Providers muted by a recent 4xx (Cloudflare block / rate limit) are dropped before any
        // coroutine is created — a blocked provider costs exactly one map lookup per song instead
        // of a request, a timeout and a stack trace.
        val providers = resolveLyricsProviders()
            .filter { it.isEnabled(context) && !LyricsProviderCircuitBreaker.isBlocked(it.name) }
        if (providers.isEmpty()) {
            return LyricsWithProvider(LYRICS_NOT_FOUND, "Unknown")
        }

        // Structured concurrency (coroutineScope, NOT a detached SupervisorJob scope): every
        // provider is queried CONCURRENTLY so a slow/failing high-priority provider no longer
        // serially delays the rest (the old sequential loop paid each provider's timeout back
        // to back). Results are still consumed in PREFERENCE ORDER, so the quality ranking is
        // preserved — a fast low-priority provider can't beat a higher-priority one, and
        // Paxsenix stays the last resort. Because this is structured, a caller cancellation
        // (the user skipping to another song) cancels every in-flight provider fetch: no
        // orphaned network work and no late result that could surface for the wrong song.
        val result = coroutineScope {
            // Lazy last-resort TAIL. The last two providers in the order (Paxsenix + Unison by
            // default) are both unreliable: Paxsenix's public endpoint 403s behind Cloudflare, and
            // Unison's crowd-sourced DB is near-empty (404s for most songs). Neither should be hit
            // for a song a reliable provider already covers. Eager `async` made the ordering
            // cosmetic — the request was on the wire before the loop could decide it wasn't needed,
            // so a blocked/empty provider was hit for every single song. Making only the SINGLE
            // last provider lazy would demote just Unison and flip Paxsenix back to eager (hammering
            // Cloudflare again), so the last TWO are lazy. They dispatch only when every provider
            // ahead of them returned empty, and are then started TOGETHER (below) so the tail still
            // runs concurrently — awaiting them one-by-one would otherwise serialize their timeouts.
            val lazyFromIndex = when {
                providers.size <= 1 -> Int.MAX_VALUE          // nothing to demote
                providers.size == 2 -> 1                       // only the last is a last resort
                else -> providers.size - 2                     // last two are last resorts
            }
            val deferreds = providers.mapIndexed { index, provider ->
                val startMode =
                    if (index >= lazyFromIndex) CoroutineStart.LAZY else CoroutineStart.DEFAULT
                provider to async(start = startMode) {
                    try {
                        withTimeoutOrNull(PROVIDER_TIMEOUT_MS) {
                            provider.getLyrics(
                                mediaMetadata.id,
                                mediaMetadata.title,
                                mediaMetadata.artists.joinToString { it.name },
                                mediaMetadata.duration,
                                mediaMetadata.album?.title,
                            )
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        LyricsProviderCircuitBreaker.recordFailure(provider.name, e)
                        Timber.w(e, "Lyrics provider %s threw", provider.name)
                        null
                    }
                }
            }

            // SYNCED-PREFERENCE (upstream 5.2.7 headline change). A result counts as SYNCED when its
            // first non-blank content is a real LRC timestamp ([mm:ss...]) — line-timed lyrics the
            // player can scroll. NB: the test requires the DIGITS of a timestamp, not a bare "[" —
            // otherwise a plaintext lyric that opens with a section header ("[Verse 1]", "[Chorus]")
            // would be mis-picked as synced and could beat a cleaner higher-priority unsynced result.
            //
            // We still consume providers strictly in PREFERENCE ORDER. A non-blank *unsynced* result
            // no longer ends the search — it is kept as `unsyncedFallback` — and we keep reading the
            // already-in-flight providers hoping for a synced upgrade. But that hunt is time-BOUNDED:
            // once we hold a displayable result, a single slow provider must not make a plaintext-only
            // song wait its full 8s timeout for lyrics we could already show. After the first non-blank
            // result we give the remaining providers only SYNCED_HUNT_MS to produce synced lyrics, then
            // return the fallback. The synced-first case is unaffected: a high-priority provider that
            // returns synced still wins immediately.
            var syncedWinner: LyricsWithProvider? = null
            var unsyncedFallback: LyricsWithProvider? = null
            var huntDeadlineMs = Long.MAX_VALUE
            for (index in deferreds.indices) {
                val (provider, deferred) = deferreds[index]
                // Lazy tail guard: a last-resort provider must only start when EVERYTHING before it
                // was empty. If we already hold a non-blank result, waking the tail would break that
                // invariant and slow the common path for no gain (we already have lyrics to show), so
                // skip the whole lazy tail. The eager providers above are already running, so awaiting
                // them to hunt for a synced upgrade costs no extra latency or network work.
                if (index >= lazyFromIndex && (syncedWinner != null || unsyncedFallback != null)) {
                    continue
                }
                // First time we reach the lazy tail with nothing found yet: start ALL remaining lazy
                // providers at once so the tail runs CONCURRENTLY (awaiting them one-by-one below
                // would serialize their full timeouts). start() is a no-op for the eager providers
                // already running. The guard above guarantees this is only reached when every
                // provider before the tail produced no non-blank result.
                if (index == lazyFromIndex) {
                    for (j in index until deferreds.size) deferreds[j].second.start()
                }
                deferred.start()
                // Once a displayable (unsynced) result is in hand, cap how long we keep hunting for a
                // synced upgrade. remaining <= 0 means the grace window elapsed — stop and show it.
                val remainingHuntMs =
                    if (unsyncedFallback != null) huntDeadlineMs - SystemClock.elapsedRealtime() else Long.MAX_VALUE
                if (remainingHuntMs <= 0L) break
                val providerResult = try {
                    if (remainingHuntMs == Long.MAX_VALUE) {
                        deferred.await()
                    } else {
                        // A provider that hasn't answered within the grace window is skipped (its own
                        // 8s timeout and the final cancel() still clean it up). getOrNull-of-null =
                        // treat as no result this round.
                        withTimeoutOrNull(remainingHuntMs) { deferred.await() }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    LyricsProviderCircuitBreaker.recordFailure(provider.name, e)
                    Timber.w(e, "Lyrics provider %s failed", provider.name)
                    null
                }
                providerResult?.onFailure { error ->
                    // A third-party provider refusing us is an expected, unfixable-by-us condition,
                    // not a crash: log it, trip the breaker on 4xx, and do NOT file a non-fatal.
                    LyricsProviderCircuitBreaker.recordFailure(provider.name, error)
                    Timber.w(error, "Lyrics provider %s returned no lyrics", provider.name)
                }
                val lyrics = providerResult?.getOrNull()
                if (!lyrics.isNullOrBlank()) {
                    // A non-blank return is a success for THIS provider regardless of sync state.
                    LyricsProviderCircuitBreaker.recordSuccess(provider.name)
                    val candidate = LyricsWithProvider(lyrics, provider.name)
                    if (SYNCED_LINE.containsMatchIn(lyrics.trimStart().take(24))) {
                        // Synced: best possible result, stop scanning immediately.
                        syncedWinner = candidate
                        break
                    } else if (unsyncedFallback == null) {
                        // First non-blank but unsynced: keep it as the fallback, start the grace
                        // window, and keep scanning the in-flight providers for a synced upgrade.
                        unsyncedFallback = candidate
                        huntDeadlineMs = SystemClock.elapsedRealtime() + SYNCED_HUNT_MS
                    }
                }
            }
            // Prefer synced; otherwise the first non-blank (unsynced) result.
            val resolved = syncedWinner ?: unsyncedFallback
            // Once the winner is decided, stop any providers still in flight. This also completes
            // any LAZY deferred that was never started, so the enclosing coroutineScope (which
            // waits on every child) cannot hang on an unstarted coroutine.
            deferreds.forEach { (_, deferred) -> deferred.cancel() }
            resolved ?: LyricsWithProvider(LYRICS_NOT_FOUND, "Unknown")
        }

        if (result.lyrics != LYRICS_NOT_FOUND) {
            cache.put(mediaMetadata.id, listOf(LyricsResult(result.provider, result.lyrics)))
        }
        return result
    }

    suspend fun getAllLyrics(
        mediaId: String,
        songTitle: String,
        songArtists: String,
        duration: Int,
        album: String? = null,
        callback: (LyricsResult) -> Unit,
    ) {
        currentLyricsJob?.cancel()

        val cacheKey = "$songArtists-$songTitle".replace(" ", "")
        cache.get(cacheKey)?.let { results ->
            results.forEach {
                callback(it)
            }
            return
        }

        
        
        val isNetworkAvailable = try {
            networkConnectivity.isCurrentlyConnected()
        } catch (e: Exception) {
            
            true
        }
        
        if (!isNetworkAvailable) {
            
            return
        }

        val allResult = mutableListOf<LyricsResult>()
        val providers = resolveLyricsProviders()
        currentLyricsJob = CoroutineScope(SupervisorJob()).launch {
            providers.forEach { provider ->
                // Same mute list as the single-lyrics path: a provider that just 403'd has nothing
                // to add to the "all providers" sheet either.
                if (provider.isEnabled(context) && !LyricsProviderCircuitBreaker.isBlocked(provider.name)) {
                    try {
                        provider.getAllLyrics(mediaId, songTitle, songArtists, duration, album) { lyrics ->
                            val result = LyricsResult(provider.name, lyrics)
                            allResult += result
                            callback(result)
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        LyricsProviderCircuitBreaker.recordFailure(provider.name, e)
                        Timber.w(e, "Lyrics provider %s failed while listing all lyrics", provider.name)
                    }
                }
            }
            cache.put(cacheKey, allResult)
        }

        currentLyricsJob?.join()
    }

    fun cancelCurrentLyricsJob() {
        currentLyricsJob?.cancel()
        currentLyricsJob = null
    }

    companion object {
        private const val MAX_CACHE_SIZE = 3
        // Per-provider cap. Providers run concurrently, so this bounds a single hung provider
        // without stacking (the old sequential loop could wait this long for EACH provider).
        private const val PROVIDER_TIMEOUT_MS = 8_000L
        // After the first non-blank (unsynced) result, how long we keep hunting for a SYNCED
        // upgrade before showing the fallback. Short so a plaintext-only song still appears fast;
        // long enough that a concurrent provider's synced result usually lands first.
        private const val SYNCED_HUNT_MS = 1_200L
        // A result is SYNCED only if its first line is a real LRC timestamp ([m:ss] / [mm:ss...]),
        // NOT a bare "[" — otherwise "[Verse 1]" plaintext headers would be mistaken for synced.
        private val SYNCED_LINE = Regex("""^\[\d{1,2}:\d{2}""")
    }
}

data class LyricsResult(
    val providerName: String,
    val lyrics: String,
)

data class LyricsWithProvider(
    val lyrics: String,
    val provider: String,
)