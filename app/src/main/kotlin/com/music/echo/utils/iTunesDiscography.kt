package iad1tya.echo.music.utils

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import timber.log.Timber

/**
 * Real artist discography from the public iTunes Search API (no key/token needed). Used to find albums
 * that YouTube Music omits from an artist's page so they can be searched on YouTube and added back.
 */
object iTunesDiscography {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private val client by lazy {
        HttpClient(OkHttp) {
            install(HttpTimeout) {
                connectTimeoutMillis = 12_000
                requestTimeoutMillis = 20_000
                socketTimeoutMillis = 20_000
            }
            expectSuccess = false
        }
    }

    /** Album titles released by [artistName] according to iTunes (credited to that artist). */
    suspend fun fetchAlbumTitles(artistName: String, country: String = "us"): List<String> =
        runCatching {
            val text = client.get("https://itunes.apple.com/search") {
                parameter("term", artistName)
                parameter("entity", "album")
                parameter("attribute", "artistTerm")
                parameter("limit", "200")
                parameter("country", country)
            }.bodyAsText()

            json.parseToJsonElement(text).jsonObject["results"]?.jsonArray
                ?.mapNotNull { el ->
                    val o = el.jsonObject
                    val resultArtist = o["artistName"]?.jsonPrimitive?.contentOrNull ?: ""
                    val title = o["collectionName"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                    // Keep the artist's OWN releases (primary credit) — including "...4.40" variants — but
                    // drop singles where they are only a guest ("X & Juan Luis Guerra") and tributes /
                    // "Various Artists" compilations.
                    val credited = resultArtist.startsWith(artistName, ignoreCase = true) ||
                        artistName.startsWith(resultArtist, ignoreCase = true)
                    if (credited) title else null
                }
                ?.distinct()
                .orEmpty()
        }.onFailure {
            Timber.w("iTunes discography fetch failed for $artistName: ${it.message}")
        }.getOrDefault(emptyList())

    /**
     * Normalize an album title so "Privé - EP", "Privé (Deluxe)" and "Privé" all compare equal. Strips
     * a trailing release-type suffix ("- EP", "- Single", "- Deluxe"...) but NOT a leading word (so
     * "Single Ladies" stays intact), and drops parentheticals/punctuation/accents-insensitive symbols.
     */
    fun normalizeTitle(raw: String): String =
        raw.lowercase()
            .replace(Regex("\\(.*?\\)"), " ")
            .replace(Regex("\\[.*?\\]"), " ")
            .replace(
                Regex("(?i)\\s*[-–—]\\s*(ep|single|deluxe|remaster(ed)?|edition|expanded|bonus|version|live|en vivo)\\b.*$"),
                " ",
            )
            .replace(Regex("[^\\p{L}\\p{Nd} ]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
}
