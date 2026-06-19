package iad1tya.echo.music.podcast

import android.content.Context
import androidx.datastore.preferences.core.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import iad1tya.echo.music.constants.PodcastProgressKey
import iad1tya.echo.music.utils.dataStore
import iad1tya.echo.music.utils.get
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Remembers, per podcast episode (keyed by its audio URL), how far the user listened, whether they
 * finished it, and when it was last played — so episodes resume where they left off and show a
 * "finished" / "continue" state. Persisted in DataStore as a small JSON map.
 */
@Singleton
class PodcastProgressStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    data class Progress(
        val positionMs: Long,
        val durationMs: Long,
        val finished: Boolean,
        val updatedAt: Long,
    )

    /** Treat an episode as finished when within ~30s of the end. */
    private fun isFinished(posMs: Long, durMs: Long) = durMs > 0 && posMs >= durMs - 30_000

    val progress: Flow<Map<String, Progress>> =
        context.dataStore.data.map { parse(it[PodcastProgressKey]) }

    suspend fun get(url: String): Progress? = parse(context.dataStore.get(PodcastProgressKey, ""))[url]

    suspend fun save(url: String, positionMs: Long, durationMs: Long) {
        if (url.isBlank() || positionMs < 0) return
        context.dataStore.edit { prefs ->
            val map = parse(prefs[PodcastProgressKey]).toMutableMap()
            map[url] = Progress(positionMs, durationMs, isFinished(positionMs, durationMs), System.currentTimeMillis())
            // Cap the stored set so it can't grow unbounded (keep the 200 most recent).
            val trimmed = map.entries.sortedByDescending { it.value.updatedAt }.take(200).associate { it.key to it.value }
            prefs[PodcastProgressKey] = toJson(trimmed)
        }
    }

    private fun parse(s: String?): Map<String, Progress> = runCatching {
        if (s.isNullOrBlank()) return emptyMap()
        val obj = JSONObject(s)
        buildMap {
            obj.keys().forEach { url ->
                val o = obj.optJSONObject(url) ?: return@forEach
                put(url, Progress(o.optLong("pos"), o.optLong("dur"), o.optBoolean("fin"), o.optLong("at")))
            }
        }
    }.getOrDefault(emptyMap())

    private fun toJson(map: Map<String, Progress>): String {
        val obj = JSONObject()
        map.forEach { (url, p) ->
            obj.put(url, JSONObject().put("pos", p.positionMs).put("dur", p.durationMs).put("fin", p.finished).put("at", p.updatedAt))
        }
        return obj.toString()
    }
}
