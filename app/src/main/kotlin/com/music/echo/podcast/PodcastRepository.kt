package iad1tya.echo.music.podcast

import android.util.Xml
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.xmlpull.v1.XmlPullParser
import java.io.StringReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Podcast engine independent of YouTube/Spotify: search shows through the free Apple/iTunes podcast
 * directory (no API key) and read episodes from each show's public RSS feed. Episode audio URLs are
 * direct streams that the normal player can handle (see MusicService's direct-URL passthrough).
 */
@Singleton
class PodcastRepository @Inject constructor() {

    suspend fun search(query: String): List<PodcastShow> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()
        val url = "https://itunes.apple.com/search?media=podcast&entity=podcast&limit=25&term=" +
            URLEncoder.encode(query, "UTF-8")
        val json = httpGet(url) ?: return@withContext emptyList()
        val results = JSONObject(json).optJSONArray("results") ?: return@withContext emptyList()
        buildList {
            for (i in 0 until results.length()) {
                val o = results.optJSONObject(i) ?: continue
                val feed = o.optString("feedUrl").takeIf { it.isNotBlank() } ?: continue
                add(
                    PodcastShow(
                        id = feed,
                        title = o.optString("collectionName").ifBlank { o.optString("trackName") },
                        author = o.optString("artistName"),
                        artworkUrl = o.optString("artworkUrl600").takeIf { it.isNotBlank() }
                            ?: o.optString("artworkUrl100").takeIf { it.isNotBlank() },
                        feedUrl = feed,
                    )
                )
            }
        }.distinctBy { it.id } // iTunes can return the same feed twice -> unique keys for the list
    }

    suspend fun episodes(show: PodcastShow, limit: Int = 100): List<PodcastEpisode> =
        withContext(Dispatchers.IO) {
            val xml = httpGet(show.feedUrl) ?: return@withContext emptyList()
            runCatching { parseRss(xml, show, limit) }.getOrDefault(emptyList())
                .distinctBy { it.id } // guard against feeds with duplicate enclosure URLs
        }

    private fun httpGet(urlStr: String): String? = runCatching {
        val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15000
            readTimeout = 20000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "AuraHiRes/1.0 (podcast)")
        }
        conn.inputStream.bufferedReader().use { it.readText() }
    }.getOrNull()

    private fun parseRss(xml: String, show: PodcastShow, limit: Int): List<PodcastEpisode> {
        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        parser.setInput(StringReader(xml))

        val episodes = ArrayList<PodcastEpisode>()
        var inItem = false
        var title: String? = null
        var audioUrl: String? = null
        var itemImage: String? = null
        var duration: String? = null
        var text = StringBuilder()

        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT && episodes.size < limit) {
            when (event) {
                XmlPullParser.START_TAG -> {
                    val name = parser.name
                    when {
                        name.equals("item", true) -> {
                            inItem = true
                            title = null; audioUrl = null; itemImage = null; duration = null
                        }
                        inItem && name.equals("enclosure", true) -> {
                            val u = parser.getAttributeValue(null, "url")
                            val type = parser.getAttributeValue(null, "type") ?: ""
                            if (!u.isNullOrBlank() && (type.startsWith("audio") || audioUrl == null)) audioUrl = u
                        }
                        inItem && (name.equals("itunes:image", true) || name.equals("image", true)) -> {
                            parser.getAttributeValue(null, "href")?.let { itemImage = it }
                        }
                        else -> text = StringBuilder()
                    }
                }
                XmlPullParser.TEXT -> text.append(parser.text)
                XmlPullParser.END_TAG -> {
                    val name = parser.name
                    when {
                        inItem && name.equals("title", true) && title == null -> title = text.toString().trim()
                        inItem && name.equals("itunes:duration", true) -> duration = text.toString().trim()
                        name.equals("item", true) -> {
                            val a = audioUrl
                            if (!a.isNullOrBlank()) {
                                episodes.add(
                                    PodcastEpisode(
                                        id = a,
                                        title = title?.ifBlank { show.title } ?: show.title,
                                        audioUrl = a,
                                        artworkUrl = itemImage ?: show.artworkUrl,
                                        showTitle = show.title,
                                        author = show.author,
                                        durationSec = parseDuration(duration),
                                    )
                                )
                            }
                            inItem = false
                        }
                    }
                    text = StringBuilder()
                }
            }
            event = parser.next()
        }
        return episodes
    }

    /** iTunes durations come as seconds ("3600") or "HH:MM:SS" / "MM:SS". */
    private fun parseDuration(raw: String?): Int? {
        val d = raw?.trim().orEmpty()
        if (d.isEmpty()) return null
        return if (":" in d) {
            d.split(":").mapNotNull { it.toIntOrNull() }
                .takeIf { it.isNotEmpty() }
                ?.fold(0) { acc, part -> acc * 60 + part }
        } else {
            d.toIntOrNull()
        }
    }
}
