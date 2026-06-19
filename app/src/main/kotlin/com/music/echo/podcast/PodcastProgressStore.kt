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
 * finished it, when it was last played, and the episode's display info (title, show, artwork, feed)
 * — so episodes resume where they left off, show a "finished" / "continue" state, and power the
 * "Continuar escuchando" history without re-downloading any RSS. Persisted as a small JSON map.
 */
@Singleton
class PodcastProgressStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    data class Progress(
        val audioUrl: String,
        val positionMs: Long,
        val durationMs: Long,
        val finished: Boolean,
        val updatedAt: Long,
        val title: String,
        val showTitle: String,
        val artworkUrl: String?,
        val feedUrl: String?,
    )

    private fun isFinished(posMs: Long, durMs: Long) = durMs > 0 && posMs >= durMs - 30_000

    val progress: Flow<Map<String, Progress>> =
        context.dataStore.data.map { parse(it[PodcastProgressKey]) }

    suspend fun get(url: String): Progress? = parse(context.dataStore.get(PodcastProgressKey, ""))[url]

    /** Called when the user starts an episode: stores its display info, keeps any existing position. */
    suspend fun recordPlay(episode: PodcastEpisode, feedUrl: String?, fallbackArtwork: String? = null) {
        context.dataStore.edit { prefs ->
            val map = parse(prefs[PodcastProgressKey]).toMutableMap()
            val existing = map[episode.id]
            map[episode.id] = Progress(
                audioUrl = episode.audioUrl,
                positionMs = existing?.positionMs ?: 0L,
                durationMs = existing?.durationMs ?: ((episode.durationSec ?: 0) * 1000L),
                finished = existing?.finished ?: false,
                updatedAt = System.currentTimeMillis(),
                title = episode.title,
                showTitle = episode.showTitle,
                // Episode art, else the show's cover, else keep whatever we already had — so the
                // "Continuar escuchando" card is never left without a thumbnail.
                artworkUrl = episode.artworkUrl ?: fallbackArtwork ?: existing?.artworkUrl,
                feedUrl = feedUrl ?: existing?.feedUrl,
            )
            prefs[PodcastProgressKey] = toJson(trim(map))
        }
    }

    /** Called periodically while playing: updates position/finished, keeps the display info. */
    suspend fun save(url: String, positionMs: Long, durationMs: Long) {
        if (url.isBlank() || positionMs < 0) return
        context.dataStore.edit { prefs ->
            val map = parse(prefs[PodcastProgressKey]).toMutableMap()
            val existing = map[url]
            map[url] = Progress(
                audioUrl = url,
                positionMs = positionMs,
                durationMs = durationMs,
                finished = isFinished(positionMs, durationMs),
                updatedAt = System.currentTimeMillis(),
                title = existing?.title ?: "",
                showTitle = existing?.showTitle ?: "",
                artworkUrl = existing?.artworkUrl,
                feedUrl = existing?.feedUrl,
            )
            prefs[PodcastProgressKey] = toJson(trim(map))
        }
    }

    private fun trim(map: Map<String, Progress>): Map<String, Progress> =
        map.entries.sortedByDescending { it.value.updatedAt }.take(200).associate { it.key to it.value }

    private fun parse(s: String?): Map<String, Progress> = runCatching {
        if (s.isNullOrBlank()) return emptyMap()
        val obj = JSONObject(s)
        buildMap {
            obj.keys().forEach { url ->
                val o = obj.optJSONObject(url) ?: return@forEach
                put(
                    url,
                    Progress(
                        audioUrl = url,
                        positionMs = o.optLong("pos"),
                        durationMs = o.optLong("dur"),
                        finished = o.optBoolean("fin"),
                        updatedAt = o.optLong("at"),
                        title = o.optString("t"),
                        showTitle = o.optString("s"),
                        artworkUrl = o.optString("a").takeIf { it.isNotBlank() },
                        feedUrl = o.optString("f").takeIf { it.isNotBlank() },
                    ),
                )
            }
        }
    }.getOrDefault(emptyMap())

    private fun toJson(map: Map<String, Progress>): String {
        val obj = JSONObject()
        map.forEach { (url, p) ->
            obj.put(
                url,
                JSONObject()
                    .put("pos", p.positionMs).put("dur", p.durationMs).put("fin", p.finished).put("at", p.updatedAt)
                    .put("t", p.title).put("s", p.showTitle).put("a", p.artworkUrl ?: "").put("f", p.feedUrl ?: ""),
            )
        }
        return obj.toString()
    }
}
