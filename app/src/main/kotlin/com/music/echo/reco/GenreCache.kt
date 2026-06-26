package iad1tya.echo.music.reco

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.core.content.getSystemService
import iad1tya.echo.music.utils.iTunesDiscography
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/**
 * On-device cache of "artist -> primary genre" (from iTunes), so the taste engine can reason about real
 * genres (Latin, Rock, Hip-Hop, Christian & Gospel...) instead of only the built-in keyword lanes.
 *
 * Keyed by lowercased artist name so it matches both local songs and YouTube items. A blank value is a
 * cached "unknown" so we don't keep re-fetching the same artist. Fetching is WiFi-gated (the user's
 * choice) and runs in the background — taste falls back gracefully to artist affinity until it fills in.
 */
object GenreCache {
    private const val PREFS = "artist_genres"

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

    /**
     * Fill in genres for [artistNames] we don't know yet. Caches a blank for misses so we don't retry.
     * Honours [onlyWifi]; safe to call often (it skips known artists and bounds the work).
     */
    suspend fun enrich(context: Context, artistNames: List<String>, onlyWifi: Boolean) {
        if (onlyWifi && !isWifi(context)) return
        val pending = artistNames
            .map { it.trim() }
            .filter { it.isNotBlank() && !has(context, it.lowercase()) }
            .distinctBy { it.lowercase() }
            .take(40)
        if (pending.isEmpty()) return

        val sem = Semaphore(4)
        val results = coroutineScope {
            pending.map { name ->
                async {
                    sem.withPermit {
                        name.lowercase() to iTunesDiscography.fetchArtistGenre(name).orEmpty()
                    }
                }
            }.awaitAll()
        }
        // Persist ALL results in ONE SharedPreferences commit instead of up to 40 separate apply() fsyncs.
        // Blank values are still cached as "unknown" so misses aren't refetched.
        prefs(context).edit().also { e ->
            results.forEach { (key, genre) -> e.putString(key, genre) }
        }.apply()
    }
}
