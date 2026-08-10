package iad1tya.echo.music.utils

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
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
     * Releases where the artist only APPEARS (guest/feature, not the primary credit) — for an
     * "Appears on" section like Spotify's. Returns (title, primaryArtist) so each can be found on
     * YouTube. Merges album credits AND song-level featuring across several storefronts so
     * collaborations that iTunes US alone omits still surface. Skips "Various Artists" / tributes.
     */
    suspend fun fetchAppearsOn(artistName: String, country: String = "us"): List<Pair<String, String>> {
        // Owner listens mostly Latin/ES — US alone misses a lot of feat./colaboraciones. Query a few
        // storefronts and merge; each call is capped and failures are swallowed per-country.
        val countries = listOf(country, "mx", "es", "us", "ar", "co").distinct()
        val merged = LinkedHashMap<String, Pair<String, String>>()
        for (store in countries) {
            for (hit in fetchAppearsOnAlbums(artistName, store) + fetchAppearsOnSongs(artistName, store)) {
                val key = normalizeTitle(hit.first)
                if (key.isNotBlank()) merged.putIfAbsent(key, hit)
            }
        }
        return merged.values.toList()
    }

    private suspend fun fetchAppearsOnAlbums(
        artistName: String,
        country: String,
    ): List<Pair<String, String>> =
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
                    if (isGuestCredit(artistName, resultArtist, title)) title to resultArtist else null
                }
                .orEmpty()
        }.onFailure {
            Timber.w("iTunes appears-on albums failed for $artistName/$country: ${it.message}")
        }.getOrDefault(emptyList())

    /**
     * Song-level featuring credits ("Song (feat. X)", "A & B"). Primary artist is the track's
     * `artistName` when our artist is only a guest; otherwise the collection artist.
     */
    private suspend fun fetchAppearsOnSongs(
        artistName: String,
        country: String,
    ): List<Pair<String, String>> =
        runCatching {
            val text = client.get("https://itunes.apple.com/search") {
                parameter("term", artistName)
                parameter("entity", "song")
                parameter("attribute", "artistTerm")
                parameter("limit", "200")
                parameter("country", country)
            }.bodyAsText()

            json.parseToJsonElement(text).jsonObject["results"]?.jsonArray
                ?.mapNotNull { el ->
                    val o = el.jsonObject
                    val trackArtist = o["artistName"]?.jsonPrimitive?.contentOrNull ?: ""
                    val collectionArtist = o["collectionArtistName"]?.jsonPrimitive?.contentOrNull
                        ?: trackArtist
                    val title = o["trackName"]?.jsonPrimitive?.contentOrNull
                        ?: o["collectionName"]?.jsonPrimitive?.contentOrNull
                        ?: return@mapNotNull null
                    val featuredInTitle = title.contains(artistName, ignoreCase = true) &&
                        (title.contains("feat", ignoreCase = true) ||
                            title.contains("ft.", ignoreCase = true) ||
                            title.contains("with ", ignoreCase = true))
                    val guestOnTrack = isGuestCredit(artistName, trackArtist, title) ||
                        (featuredInTitle && !trackArtist.equals(artistName, ignoreCase = true))
                    if (!guestOnTrack) return@mapNotNull null
                    if (collectionArtist.equals("Various Artists", ignoreCase = true)) return@mapNotNull null
                    // Prefer the OTHER credited name as the YouTube search primary.
                    val primary = when {
                        !trackArtist.equals(artistName, ignoreCase = true) &&
                            !trackArtist.contains(artistName, ignoreCase = true) -> trackArtist
                        !collectionArtist.equals(artistName, ignoreCase = true) -> collectionArtist
                        else -> trackArtist
                    }
                    title to primary
                }
                .orEmpty()
        }.onFailure {
            Timber.w("iTunes appears-on songs failed for $artistName/$country: ${it.message}")
        }.getOrDefault(emptyList())

    private fun isGuestCredit(artistName: String, creditedArtist: String, title: String): Boolean {
        if (creditedArtist.equals("Various Artists", ignoreCase = true)) return false
        if (title.contains("homenaje", ignoreCase = true) || title.contains("tribut", ignoreCase = true)) {
            return false
        }
        val credited = creditedArtist.trim()
        if (credited.isEmpty()) return false
        // Primary credit for this artist (own release) — not "appears on".
        if (credited.equals(artistName, ignoreCase = true) ||
            credited.startsWith("$artistName ", ignoreCase = true) ||
            credited.startsWith("$artistName,", ignoreCase = true) ||
            credited.startsWith("$artistName &", ignoreCase = true) ||
            credited.startsWith("$artistName feat", ignoreCase = true) ||
            credited.startsWith("$artistName ft", ignoreCase = true)
        ) {
            return false
        }
        // Guest forms: "Host & Artist", "Host feat. Artist", artist name in the credit string but
        // not as the leading primary name.
        return credited.contains(artistName, ignoreCase = true)
    }

    /**
     * Primary genre for [artistName] per iTunes (e.g. "Christian & Gospel", "Latin", "Rock", "Hip-Hop/Rap"),
     * taken from their most relevant album. Used to give the taste engine a real genre signal beyond the
     * built-in keyword lanes.
     *
     * FAILURE vs MISS contract (GenreCache depends on it — do not blur it again):
     *  - non-blank string  -> the genre.
     *  - ""                -> DEFINITIVE MISS: iTunes answered 2xx with a parsed `results` array that is
     *                         empty or carries no usable `primaryGenreName`. Safe to cache as "unknown".
     *  - null              -> FAILURE: exception, non-2xx status, unparseable body (captive portal), or a
     *                         2xx without a `results` array (throttle/error page). Callers must NOT cache
     *                         null — a transient outage must never be persisted as "this artist has no
     *                         genre".
     */
    suspend fun fetchArtistGenre(artistName: String, country: String = "us"): String? =
        runCatching {
            val response = client.get("https://itunes.apple.com/search") {
                parameter("term", artistName)
                parameter("entity", "album")
                parameter("attribute", "artistTerm")
                parameter("limit", "1")
                parameter("country", country)
            }
            if (!response.status.isSuccess()) return@runCatching null

            val results = json.parseToJsonElement(response.bodyAsText())
                .jsonObject["results"]?.jsonArray
                ?: return@runCatching null // 2xx but no results array = error/throttle page, not a miss

            results.firstOrNull()?.jsonObject?.get("primaryGenreName")?.jsonPrimitive?.contentOrNull
                ?.takeIf { it.isNotBlank() }
                ?: "" // iTunes genuinely answered and knows no genre -> definitive miss
        }.getOrNull()

    private val PARENTHETICAL = Regex("\\(.*?\\)")
    private val BRACKETED = Regex("\\[.*?\\]")
    private val EDITION_SUFFIX =
        Regex("(?i)\\s*[-–—]\\s*(ep|single|deluxe|remaster(ed)?|edition|expanded|bonus|version)\\b.*$")
    private val NON_WORD = Regex("[^\\p{L}\\p{Nd} ]")
    private val WHITESPACE = Regex("\\s+")

    /**
     * Live / acoustic edition marker to strip so that the studio and the live edition of a record each key
     * consistently no matter which store wrote the title.
     *
     * DELIBERATELY CONSERVATIVE. The two mistakes are not symmetric: a missed strip only costs ONE wasted
     * lookup (the release is searched again and, at worst, listed twice), while a WRONG strip turns a real
     * title into a shorter one that COLLIDES with another release — and Phase D keeps a single winner per
     * key, so the album that loses the collision disappears from the discography. Only two shapes qualify:
     *
     *  1. a marker introduced by a REAL separator (" - ", " – ", ":", ","), optionally followed by a
     *     venue/date clause: "X - Live at the Apollo", "X: En Vivo 2019", "X, Unplugged". The separator is
     *     what proves the tail is an edition label rather than part of the title.
     *  2. NO separator: ONLY the unambiguous multi-word Spanish forms ("en vivo", "en directo",
     *     "en concierto"), and only when they END the title. This is the shape the owner's catalog needs —
     *     iTunes writes "X (En Vivo)" (the parenthetical is already dropped above) while YouTube Music
     *     writes the bare "X En Vivo"; two keys for one record made it be reported missing, re-searched and
     *     then emitted twice by the assembly dedupe.
     *
     * What is deliberately NOT stripped without a separator, because it mutilated real albums:
     *  • a bare single word — "live", "unplugged", "acústico", "acoustic", "directo". Legitimate titles end
     *    in them: "Long Live", "MTV Unplugged", "Radio Live", "One Live".
     *  • anything that FOLLOWS the marker. The old venue clause was applied to the separator-less form too
     *    and ate the rest of the title: "We Live in Time" → "we", "Sessions Live at the Apollo" →
     *    "sessions", "Nada Es Igual Live 2019" → "nada es igual", and — worst — "MTV Unplugged in New York"
     *    → "mtv", the SAME key as "MTV Unplugged", so an artist holding both lost one. The clause survives
     *    only inside shape 1.
     *
     * The lookbehind additionally requires real title text in front, so a release whose title IS the marker
     * ("En Vivo", "Live", "Directo al Corazón") is never emptied; [normalizeTitle] double-checks that.
     *
     * Callers that must keep the two recordings apart re-add a marker themselves — see reconKey /
     * LIVE_ACOUSTIC in ArtistItemsViewModel.
     */
    private val LIVE_EDITION_SUFFIX =
        Regex(
            "(?i)(?<=[\\p{L}\\p{Nd}])" +
                "(?:" +
                // 1) after a real separator: any marker, plus an optional venue/date tail
                "(?:\\s+[-–—]\\s*|\\s*[:,]\\s*)" +
                "(?:en\\s*vivo|en\\s*directo|en\\s*concierto|unplugged|ac[uú]stico|acoustic|live|directo)\\b" +
                "(?:\\s+(?:(?:desde|en|at|from|in|@)\\b|\\d).*)?" +
                "|" +
                // 2) separator-less: only the multi-word Spanish forms, and only at the very end
                "\\s+(?:en\\s*vivo|en\\s*directo|en\\s*concierto)\\b" +
                ")\\s*$",
        )

    /**
     * Normalize an album title so "Privé - EP", "Privé (Deluxe)" and "Privé" all compare equal. Strips
     * a trailing release-type suffix ("- EP", "- Single", "- Deluxe"...) but NOT a leading word (so
     * "Single Ladies" stays intact), and drops parentheticals/punctuation/accents-insensitive symbols.
     * Live/acoustic markers are stripped by [LIVE_EDITION_SUFFIX] — see there for exactly which shapes.
     */
    fun normalizeTitle(raw: String): String {
        val base = raw.lowercase()
            .replace(PARENTHETICAL, " ")
            .replace(BRACKETED, " ")
            .replace(EDITION_SUFFIX, " ")
        val stripped = squeeze(base.replace(LIVE_EDITION_SUFFIX, " "))
        // The strip must never leave fewer than one meaningful word: if it consumed everything (a title that
        // is nothing but a marker), keep the unstripped title — a wasted lookup beats an empty key, which
        // would merge every such release into one.
        return if (stripped.isNotBlank()) stripped else squeeze(base)
    }

    private fun squeeze(s: String): String =
        s.replace(NON_WORD, " ").replace(WHITESPACE, " ").trim()
}
