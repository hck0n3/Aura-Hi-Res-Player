package iad1tya.echo.music.podcast

import iad1tya.echo.music.models.MediaMetadata
import org.json.JSONArray
import org.json.JSONObject

/** A podcast show found via the Apple/iTunes directory. [feedUrl] is the public RSS feed. */
data class PodcastShow(
    val id: String,
    val title: String,
    val author: String,
    val artworkUrl: String?,
    val feedUrl: String,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("id", id).put("title", title).put("author", author)
        .put("artworkUrl", artworkUrl ?: "").put("feedUrl", feedUrl)

    companion object {
        fun fromJson(o: JSONObject) = PodcastShow(
            id = o.optString("id"),
            title = o.optString("title"),
            author = o.optString("author"),
            artworkUrl = o.optString("artworkUrl").takeIf { it.isNotBlank() },
            feedUrl = o.optString("feedUrl"),
        )

        fun listToJson(shows: List<PodcastShow>): String =
            JSONArray().apply { shows.forEach { put(it.toJson()) } }.toString()

        fun listFromJson(s: String?): List<PodcastShow> = runCatching {
            if (s.isNullOrBlank()) return emptyList()
            val arr = JSONArray(s)
            (0 until arr.length()).map { fromJson(arr.getJSONObject(it)) }
        }.getOrDefault(emptyList())
    }
}

/** A single episode. [audioUrl] is a direct, public audio stream (usually MP3) that ExoPlayer can
 *  play straight away — no YouTube/Spotify needed. */
data class PodcastEpisode(
    val id: String,
    val title: String,
    val audioUrl: String,
    /** Direct video stream (e.g. video/mp4 enclosure) if this is a VIDEO podcast — enables the audio↔video toggle. */
    val videoUrl: String? = null,
    val artworkUrl: String?,
    val showTitle: String,
    val author: String,
    val durationSec: Int?,
    val season: Int? = null,
    val episode: Int? = null,
)

/** A browseable Apple/iTunes podcast category (for the "by topic" catalog). */
data class PodcastCategory(val genreId: Int, val name: String)

/** Build player metadata whose id is the direct audio URL, so MusicService plays it straight. */
fun PodcastEpisode.toMediaMetadata() = MediaMetadata(
    id = audioUrl,
    title = title,
    artists = listOf(MediaMetadata.Artist(id = null, name = showTitle)),
    duration = durationSec ?: -1,
    thumbnailUrl = artworkUrl,
    podcastVideoUrl = videoUrl,
)
