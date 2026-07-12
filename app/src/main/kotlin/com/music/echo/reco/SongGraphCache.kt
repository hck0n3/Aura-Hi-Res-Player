package iad1tya.echo.music.reco

import android.content.Context
import com.music.innertube.YouTube
import com.music.innertube.models.WatchEndpoint
import iad1tya.echo.music.utils.isLocalMediaId
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/**
 * On-device "song similarity graph" (co-relatedness). For each anchor song the user liked, we cache the ids
 * of the songs YouTube says are related to it. The taste engine then prefers candidates that are related to
 * MULTIPLE liked songs — a poor-man's collaborative filter, no ML — so radio knows your SONGS, not just your
 * artists. Nothing extra leaves the phone beyond the same related-songs call the radio already makes.
 *
 * Keyed by the anchor's (YouTube) songId; the value is the anchor's related ids joined by ",". A blank value
 * is a cached "no relations" so misses aren't refetched. Fetching is WiFi-gated (the user's choice) and runs
 * in the background alongside [GenreCache] — the co-rel bonus falls back to zero until it fills in.
 */
object SongGraphCache {
    private const val PREFS = "song_graph"

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** anchorId -> its related song ids. Blank entries (cached misses) are skipped, so an empty graph = no effect. */
    fun snapshot(context: Context): Map<String, Set<String>> =
        prefs(context).all.mapNotNull { (k, v) ->
            val ids = (v as? String)?.split(",")?.filter { it.isNotBlank() }?.toSet()
            if (ids.isNullOrEmpty()) null else k to ids
        }.toMap()

    private fun has(context: Context, key: String) = prefs(context).contains(key)

    /** Only ONLINE YouTube ids make sense as anchors — local content:// / file:// and direct-url http don't. */
    private fun isYouTubeId(id: String): Boolean =
        id.isNotBlank() && !id.isLocalMediaId() && !id.startsWith("http", ignoreCase = true)

    /**
     * Fill in the related-song sets for the [anchorSongIds] we don't know yet. Caches a blank for misses so we
     * don't retry. Honours [onlyWifi]; safe to call often (it skips known anchors and bounds the work). Non-
     * YouTube ids are skipped. Everything is best-effort — never throws.
     */
    suspend fun enrich(context: Context, anchorSongIds: List<String>, onlyWifi: Boolean) {
        if (onlyWifi && !GenreCache.isWifi(context)) return
        // [anchorSongIds] is the CURRENT bounded liked set (the caller passes the recent likes). It doubles as
        // the KEEP set: any cached anchor no longer in it (unliked / aged out of the recent window) is pruned, so
        // the graph stays bounded to the liked set instead of growing forever.
        val keep = anchorSongIds.filter { isYouTubeId(it) }.distinct().toSet()
        if (keep.isEmpty()) return
        val pending = keep.filter { !has(context, it) }.take(20)

        val sem = Semaphore(4)
        val results = if (pending.isEmpty()) emptyList() else coroutineScope {
            pending.map { anchor ->
                async {
                    sem.withPermit {
                        anchor to runCatching {
                            YouTube.next(WatchEndpoint(videoId = anchor)).getOrNull()?.items
                                ?.asSequence()
                                ?.map { it.id }
                                ?.filter { it.isNotBlank() && it != anchor }
                                ?.distinct()
                                ?.take(20)
                                ?.joinToString(",")
                                .orEmpty()
                        }.getOrDefault("")
                    }
                }
            }.awaitAll()
        }
        val stale = prefs(context).all.keys - keep
        if (results.isEmpty() && stale.isEmpty()) return
        // Persist new entries AND drop stale ones in ONE commit. Blank values stay cached as "no relations".
        prefs(context).edit().also { e ->
            results.forEach { (anchor, ids) -> e.putString(anchor, ids) }
            stale.forEach { e.remove(it) }
        }.apply()
    }
}
