

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
import iad1tya.echo.music.utils.reportException
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
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

        val providers = resolveLyricsProviders().filter { it.isEnabled(context) }
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
            val deferreds = providers.map { provider ->
                provider to async {
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
                        reportException(e)
                        null
                    }
                }
            }

            var winner: LyricsWithProvider? = null
            for ((provider, deferred) in deferreds) {
                val providerResult = try {
                    deferred.await()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    reportException(e)
                    null
                }
                providerResult?.onFailure { reportException(it) }
                val lyrics = providerResult?.getOrNull()
                if (!lyrics.isNullOrBlank()) {
                    winner = LyricsWithProvider(lyrics, provider.name)
                    break
                }
            }
            // Once the in-order winner is decided, stop any providers still in flight.
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
                if (provider.isEnabled(context)) {
                    try {
                        provider.getAllLyrics(mediaId, songTitle, songArtists, duration, album) { lyrics ->
                            val result = LyricsResult(provider.name, lyrics)
                            allResult += result
                            callback(result)
                        }
                    } catch (e: Exception) {
                        
                        reportException(e)
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