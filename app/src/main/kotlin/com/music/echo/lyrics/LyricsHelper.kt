

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
            // The provider sitting LAST in the order is the declared last resort (Paxsenix by
            // default: its public endpoint 403s behind Cloudflare). Eager `async` made that
            // ordering cosmetic — the request was already on the wire before the consumption loop
            // could decide it was not needed, so a permanently-blocked provider was hit for every
            // single song. CoroutineStart.LAZY makes the demotion real: it is created but not
            // dispatched, and only starts if every provider ahead of it came back empty.
            val lastResortIndex = providers.lastIndex.takeIf { providers.size > 1 } ?: -1
            val deferreds = providers.mapIndexed { index, provider ->
                val startMode =
                    if (index == lastResortIndex) CoroutineStart.LAZY else CoroutineStart.DEFAULT
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

            var winner: LyricsWithProvider? = null
            for ((provider, deferred) in deferreds) {
                // Reaching a provider means every provider before it already returned empty, so this
                // is exactly the moment the last-resort provider becomes worth trying. `start()`
                // is a no-op for the eager ones (already running), and because LAZY is applied to
                // a SINGLE provider — the last — starting just this one can never serialize a
                // parallel tail: by the time the loop gets here, there is no tail left.
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
                    LyricsProviderCircuitBreaker.recordSuccess(provider.name)
                    winner = LyricsWithProvider(lyrics, provider.name)
                    break
                }
            }
            // Once the in-order winner is decided, stop any providers still in flight. This also
            // completes any LAZY deferred that was never started, so the enclosing coroutineScope
            // (which waits on every child) cannot hang on an unstarted coroutine.
            deferreds.forEach { (_, deferred) -> deferred.cancel() }
            winner ?: LyricsWithProvider(LYRICS_NOT_FOUND, "Unknown")
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