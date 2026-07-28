package iad1tya.echo.music.utils

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
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
     * Album (title, trackCount) released by [artistName] per iTunes (same credit rule as [fetchAlbumTitles],
     * no extra network — trackCount is already in the search response). trackCount is 0 when iTunes omits it.
     * Lets the caller detect a TRUNCATED YouTube upload (fewer tracks than iTunes says the release has).
     */
    suspend fun fetchAlbumMeta(artistName: String, country: String = "us"): List<Pair<String, Int>> =
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
                    val credited = resultArtist.startsWith(artistName, ignoreCase = true) ||
                        artistName.startsWith(resultArtist, ignoreCase = true)
                    if (credited) title to (o["trackCount"]?.jsonPrimitive?.intOrNull ?: 0) else null
                }
                .orEmpty()
        }.onFailure {
            Timber.w("iTunes discography meta fetch failed for $artistName: ${it.message}")
        }.getOrDefault(emptyList())

    /**
     * Releases where the artist only APPEARS (is a guest/feature, not the primary credit) — for an
     * "Appears on" section like Spotify's. Returns (albumTitle, primaryArtist) so each can be found on
     * YouTube. Skips "Various Artists" compilations and tributes.
     */
    suspend fun fetchAppearsOn(artistName: String, country: String = "us"): List<Pair<String, String>> =
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
                    val isGuest = resultArtist.contains(artistName, ignoreCase = true) &&
                        !resultArtist.startsWith(artistName, ignoreCase = true) &&
                        !artistName.startsWith(resultArtist, ignoreCase = true) &&
                        !resultArtist.equals("Various Artists", ignoreCase = true) &&
                        !title.contains("homenaje", ignoreCase = true) &&
                        !title.contains("tribut", ignoreCase = true)
                    if (isGuest) title to resultArtist else null
                }
                ?.distinctBy { normalizeTitle(it.first) }
                .orEmpty()
        }.onFailure {
            Timber.w("iTunes appears-on fetch failed for $artistName: ${it.message}")
        }.getOrDefault(emptyList())

    /**
     * Primary genre for [artistName] per iTunes (e.g. "Christian & Gospel", "Latin", "Rock", "Hip-Hop/Rap"),
     * taken from their most relevant album. Null if unknown. Used to give the taste engine a real genre
     * signal beyond the built-in keyword lanes.
     */
    suspend fun fetchArtistGenre(artistName: String, country: String = "us"): String? =
        runCatching {
            val text = client.get("https://itunes.apple.com/search") {
                parameter("term", artistName)
                parameter("entity", "album")
                parameter("attribute", "artistTerm")
                parameter("limit", "1")
                parameter("country", country)
            }.bodyAsText()

            json.parseToJsonElement(text).jsonObject["results"]?.jsonArray
                ?.firstOrNull()?.jsonObject?.get("primaryGenreName")?.jsonPrimitive?.contentOrNull
                ?.takeIf { it.isNotBlank() }
        }.getOrNull()

    /**
     * Live / acoustic edition marker, stripped together with everything that follows it ("En Vivo Desde
     * Bogotá") REGARDLESS of the separator in front of it.
     *
     * Why no separator requirement: iTunes writes "X (En Vivo)" while YouTube Music usually writes the bare
     * "X En Vivo". When only the parenthesised / dashed forms were stripped, the very same record produced
     * two different keys ("x" vs "x en vivo") — so it was reported missing, re-searched (a wasted lookup),
     * and then emitted TWICE by the assembly dedupe. The word list also has to cover the Spanish/English
     * forms the app's audience actually uses (acústico/acoustic/unplugged/directo/en concierto), not just
     * "live"/"en vivo".
     *
     * Two guards keep it from eating real titles:
     *  • the lookbehind requires REAL title text in front, so a release whose title genuinely STARTS with
     *    one of these words ("En Vivo", "Directo al Corazón", "Live Your Life") is never emptied;
     *  • the marker must END the title, optionally followed by a venue/date clause ("… En Vivo desde
     *    Bogotá", "… Unplugged in New York", "… En Vivo 2020"). That is what keeps "Long Live the King"
     *    and "El Directo al Corazón" intact — an ambiguous word in the MIDDLE of a title is not a marker.
     *
     * Callers that must keep the two recordings apart re-add a marker themselves — see reconKey /
     * LIVE_ACOUSTIC in ArtistItemsViewModel.
     */
    private val LIVE_EDITION_SUFFIX =
        Regex(
            "(?i)(?<=[\\p{L}\\p{Nd}])\\s*[-–—:,]?\\s*" +
                "\\b(en\\s*vivo|en\\s*directo|en\\s*concierto|unplugged|ac[uú]stico|acoustic|live|directo)\\b" +
                "(\\s+(?:(?:desde|en|at|from|in|@)\\b|\\d).*)?\\s*$",
        )

    /**
     * Normalize an album title so "Privé - EP", "Privé (Deluxe)" and "Privé" all compare equal. Strips
     * a trailing release-type suffix ("- EP", "- Single", "- Deluxe"...) but NOT a leading word (so
     * "Single Ladies" stays intact), and drops parentheticals/punctuation/accents-insensitive symbols.
     * Live/acoustic markers are stripped by [LIVE_EDITION_SUFFIX] with or without a separator.
     */
    fun normalizeTitle(raw: String): String =
        raw.lowercase()
            .replace(Regex("\\(.*?\\)"), " ")
            .replace(Regex("\\[.*?\\]"), " ")
            .replace(
                Regex("(?i)\\s*[-–—]\\s*(ep|single|deluxe|remaster(ed)?|edition|expanded|bonus|version)\\b.*$"),
                " ",
            )
            // Superseded the "live|en vivo" entries of the list above: this one does not need a dash.
            .replace(LIVE_EDITION_SUFFIX, " ")
            .replace(Regex("[^\\p{L}\\p{Nd} ]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
}
