package iad1tya.echo.music.reco

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.core.content.getSystemService
import iad1tya.echo.music.constants.GenreEnrichOnMobileKey
import iad1tya.echo.music.utils.dataStore
import iad1tya.echo.music.utils.iTunesDiscography
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * On-device cache of "artist -> primary genre" (from iTunes), so the taste engine can reason about real
 * genres (Latin, Rock, Hip-Hop, Christian & Gospel...) instead of only the built-in keyword lanes.
 *
 * Keyed by lowercased artist name so it matches both local songs and YouTube items. A blank value is a
 * cached DEFINITIVE "unknown" (iTunes answered and knows no genre) so we don't keep re-fetching the same
 * artist; transient fetch FAILURES are never persisted. Fetching is WiFi-gated by default (user
 * preference, see [GenreEnrichOnMobileKey]) and runs in the background — taste falls back gracefully to
 * artist affinity until it fills in.
 */
object GenreCache {
    private const val PREFS = "artist_genres"

    /**
     * Abort an enrich batch once this many fetches FAIL in a row — several consecutive failures is a
     * throttle/outage tell, and each enrich trigger (context finish, pagination batch, Home refresh)
     * must not turn into a doomed request burst (battery gate).
     */
    private const val MAX_CONSECUTIVE_FAILURES = 3

    /** iTunes allows ~20 requests/min; keep one enrich burst comfortably under that. */
    private const val MAX_BATCH = 15

    /**
     * Artists whose genre fetch FAILED this process (exception / non-2xx / garbage body). Deliberately
     * in-memory only: a transient failure must NEVER be persisted (that permanently poisoned the cache
     * as "unknown genre"), but we also must not re-attempt on every enrich trigger — so a failed artist
     * waits until the next app session for its retry.
     */
    private val failedThisSession: MutableSet<String> = ConcurrentHashMap.newKeySet()

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun snapshot(context: Context): Map<String, String> =
        prefs(context).all.mapNotNull { (k, v) ->
            val g = v as? String
            if (g.isNullOrBlank()) null else k to g
        }.toMap()

    private fun has(context: Context, key: String) = prefs(context).contains(key)

    private fun put(context: Context, key: String, genre: String) {
        prefs(context).edit().putString(key, genre).apply()
    }

    /** True when the active network is WiFi (used to honour the "solo con WiFi" preference). */
    fun isWifi(context: Context): Boolean {
        val cm = context.getSystemService<ConnectivityManager>() ?: return false
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
    }

    /** Any active network that claims internet. Guards the opted-in mobile path so airplane mode / no
     *  network doesn't burn artists into [failedThisSession] on attempts that cannot possibly succeed. */
    private fun hasInternet(context: Context): Boolean {
        val cm = context.getSystemService<ConnectivityManager>() ?: return false
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    /**
     * Fill in genres for [artistNames] we don't know yet. Safe to call often (it skips known artists and
     * bounds the work).
     *
     * FAILURE vs MISS (see [iTunesDiscography.fetchArtistGenre]'s contract): a DEFINITIVE miss ("") is
     * cached as a blank so it isn't refetched; a FAILURE (null) persists NOTHING — it only lands in
     * [failedThisSession] so it isn't re-attempted until the next app session, and the cache can only
     * ever GAIN entries. After [MAX_CONSECUTIVE_FAILURES] failures in a row the batch aborts; artists it
     * never attempted stay eligible for later enrich runs.
     *
     * Network gate: `onlyWifi = true` (what every call site passes) means "honour the user's network
     * preference" — WiFi-only by DEFAULT (today's exact behaviour), unless the user opted in to
     * [GenreEnrichOnMobileKey]. The preference is read HERE, not by callers, precisely so the call sites
     * (MusicService, HomeViewModel) keep compiling untouched. `onlyWifi = false` still means "no gate".
     */
    suspend fun enrich(context: Context, artistNames: List<String>, onlyWifi: Boolean) = withContext(Dispatchers.IO) {
        // withContext(IO) is a self-defence, not a formality: this is reachable from MusicService's scope, which
        // is Main (the player's looper). The WiFi check is a binder IPC and `has()` can force a synchronous XML
        // load — neither belongs on the playback thread, whatever the caller passes.
        if (onlyWifi && !isWifi(context)) {
            // Not on WiFi: only proceed if the user explicitly allowed mobile-data enrichment AND there
            // is actually a network to talk to. (DataStore read is suspend + we're already on IO.)
            val allowMobile = context.dataStore.data.first()[GenreEnrichOnMobileKey] ?: false
            if (!allowMobile || !hasInternet(context)) return@withContext
        }
        val pending = artistNames
            .map { it.trim() }
            .filter { it.isNotBlank() && !has(context, it.lowercase()) && it.lowercase() !in failedThisSession }
            .distinctBy { it.lowercase() }
            .take(MAX_BATCH)
        if (pending.isEmpty()) return@withContext

        val sem = Semaphore(4)
        val consecutiveFailures = AtomicInteger(0)
        val results = coroutineScope {
            pending.map { name ->
                async {
                    sem.withPermit {
                        // With 4-way concurrency "consecutive" is approximate — good enough for a
                        // throttle tell. Skipped artists are NOT marked failed: they were never tried.
                        if (consecutiveFailures.get() >= MAX_CONSECUTIVE_FAILURES) return@withPermit null
                        val key = name.lowercase()
                        val genre = iTunesDiscography.fetchArtistGenre(name)
                        if (genre == null) {
                            failedThisSession.add(key)
                            consecutiveFailures.incrementAndGet()
                            null
                        } else {
                            consecutiveFailures.set(0)
                            key to genre
                        }
                    }
                }
            }.awaitAll().filterNotNull()
        }
        if (results.isEmpty()) return@withContext
        // Persist ALL results in ONE SharedPreferences commit instead of one apply() fsync per artist.
        // Blank values are DEFINITIVE misses cached as "unknown" so they aren't refetched; failures never
        // reach this point.
        prefs(context).edit().also { e ->
            results.forEach { (key, genre) -> e.putString(key, genre) }
        }.apply()
    }
}
