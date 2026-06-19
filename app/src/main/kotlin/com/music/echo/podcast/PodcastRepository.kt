package iad1tya.echo.music.podcast

import android.content.Context
import android.util.Xml
import androidx.datastore.preferences.core.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import iad1tya.echo.music.constants.ContentCountryKey
import iad1tya.echo.music.constants.PinnedPodcastsKey
import iad1tya.echo.music.utils.dataStore
import iad1tya.echo.music.utils.get
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONObject
import org.xmlpull.v1.XmlPullParser
import java.io.StringReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Podcast engine independent of YouTube/Spotify: search/browse shows through the free Apple/iTunes
 * podcast directory (no API key) and read episodes from each show's public RSS feed. Episode audio
 * URLs are direct streams the normal player can handle. Also stores the user's pinned shows.
 */
@Singleton
class PodcastRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    val categories = listOf(
        PodcastCategory(1303, "Comedia"),
        PodcastCategory(1489, "Noticias"),
        PodcastCategory(1324, "Sociedad y cultura"),
        PodcastCategory(1321, "Negocios"),
        PodcastCategory(1304, "Educación"),
        PodcastCategory(1318, "Tecnología"),
        PodcastCategory(1545, "Deportes"),
        PodcastCategory(1512, "Salud"),
        PodcastCategory(1488, "Cine y TV"),
        PodcastCategory(1487, "Música"),
    )

    /** Region used for the podcast charts: the country configured in the app (Settings -> Content ->
     *  "País de contenido"); falls back to the device country, then "us". Lowercase 2-letter for iTunes. */
    suspend fun configuredCountry(): String {
        val configured = context.dataStore.get(ContentCountryKey, "system")
        val code = if (configured.isNullOrBlank() || configured == "system") Locale.getDefault().country else configured
        return code.lowercase(Locale.ROOT).takeIf { it.length == 2 } ?: "us"
    }

    suspend fun search(query: String): List<PodcastShow> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()
        val url = "https://itunes.apple.com/search?media=podcast&entity=podcast&limit=25&term=" +
            URLEncoder.encode(query, "UTF-8")
        val json = httpGet(url) ?: return@withContext emptyList()
        showsFromResults(json).distinctBy { it.id }
    }

    /** Trending shows in a category for a region (Apple top-podcasts chart -> lookup for feed URLs). */
    suspend fun top(category: PodcastCategory, country: String, limit: Int = 12): List<PodcastShow> = withContext(Dispatchers.IO) {
        val rss = httpGet("https://itunes.apple.com/$country/rss/toppodcasts/limit=$limit/genre=${category.genreId}/json")
            ?: return@withContext emptyList()
        val ids = runCatching {
            val feed = JSONObject(rss).optJSONObject("feed") ?: return@runCatching emptyList<String>()
            val entries = feed.optJSONArray("entry") ?: return@runCatching emptyList<String>()
            (0 until entries.length()).mapNotNull { i ->
                entries.optJSONObject(i)?.optJSONObject("id")?.optJSONObject("attributes")?.optString("im:id")
                    ?.takeIf { it.isNotBlank() }
            }
        }.getOrDefault(emptyList())
        if (ids.isEmpty()) return@withContext emptyList()
        val lookup = httpGet("https://itunes.apple.com/lookup?id=" + ids.joinToString(",")) ?: return@withContext emptyList()
        showsFromResults(lookup).distinctBy { it.id }
    }

    private fun showsFromResults(json: String): List<PodcastShow> {
        val results = runCatching { JSONObject(json).optJSONArray("results") }.getOrNull() ?: return emptyList()
        return buildList {
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
        }
    }

    suspend fun episodes(show: PodcastShow, limit: Int = 300): List<PodcastEpisode> =
        withContext(Dispatchers.IO) {
            val xml = httpGet(show.feedUrl) ?: return@withContext emptyList()
            runCatching { parseRss(xml, show, limit) }.getOrDefault(emptyList()).distinctBy { it.id }
        }

    // ---- Pinned / saved shows -------------------------------------------------------------------

    val pinnedShows: Flow<List<PodcastShow>> =
        context.dataStore.data.map { PodcastShow.listFromJson(it[PinnedPodcastsKey]) }

    suspend fun togglePin(show: PodcastShow) {
        context.dataStore.edit { prefs ->
            val current = PodcastShow.listFromJson(prefs[PinnedPodcastsKey])
            val updated = if (current.any { it.id == show.id }) current.filterNot { it.id == show.id }
            else current + show
            prefs[PinnedPodcastsKey] = PodcastShow.listToJson(updated)
        }
    }

    // ---- HTTP + RSS -----------------------------------------------------------------------------

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
        var season: Int? = null
        var episodeNum: Int? = null
        var text = StringBuilder()

        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT && episodes.size < limit) {
            when (event) {
                XmlPullParser.START_TAG -> {
                    val name = parser.name
                    when {
                        name.equals("item", true) -> {
                            inItem = true
                            title = null; audioUrl = null; itemImage = null; duration = null; season = null; episodeNum = null
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
                        inItem && name.equals("itunes:season", true) -> season = text.toString().trim().toIntOrNull()
                        inItem && name.equals("itunes:episode", true) -> episodeNum = text.toString().trim().toIntOrNull()
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
                                        season = season,
                                        episode = episodeNum,
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
        // RSS is newest-first; show oldest-first (ascending), ordered by season+episode when present.
        return episodes.reversed().sortedWith(
            compareBy({ it.season ?: Int.MAX_VALUE }, { it.episode ?: Int.MAX_VALUE })
        )
    }

    private fun parseDuration(raw: String?): Int? {
        val d = raw?.trim().orEmpty()
        if (d.isEmpty()) return null
        return if (":" in d) {
            d.split(":").mapNotNull { it.toIntOrNull() }.takeIf { it.isNotEmpty() }
                ?.fold(0) { acc, part -> acc * 60 + part }
        } else {
            d.toIntOrNull()
        }
    }
}
