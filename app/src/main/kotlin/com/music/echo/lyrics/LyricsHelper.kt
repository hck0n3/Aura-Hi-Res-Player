

package iad1tya.echo.music.lyrics

import android.content.Context
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

            // SYNCED-PREFERENCE (upstream 5.2.7 headline change). A result counts as SYNCED when
            // its text starts with an LRC timestamp ("[" after trimStart) — i.e. line-timed
            // lyrics the player can scroll. We still consume providers strictly in PREFERENCE
            // ORDER, but a non-blank *unsynced* result no longer ends the search: it is only
            // remembered as `unsyncedFallback`. We keep reading the providers that are ALREADY in
            // flight (in order) and, the moment one yields SYNCED lyrics, return those instead.
            // If no synced result ever appears, we fall back to the FIRST non-blank result. This
            // does NOT regress the synced-first case: a high-priority provider that returns synced
            // still wins immediately (we break on the first synced hit).
            var syncedWinner: LyricsWithProvider? = null
            var unsyncedFallback: LyricsWithProvider? = null
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
                val providerResult = try {
                    deferred.await()
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
                    if (lyrics.trimStart().startsWith("[")) {
                        // Synced: best possible result, stop scanning immediately.
                        syncedWinner = candidate
                        break
                    } else if (unsyncedFallback == null) {
                        // First non-blank but unsynced: keep it as the fallback and keep scanning
                        // the remaining in-flight providers for a synced upgrade.
                        unsyncedFallback = candidate
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