package com.music.lrclib

import com.music.lrclib.models.Track
import com.music.lrclib.models.bestMatchingFor
import com.music.lrclib.models.rankedByArtistConfidence
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.serialization.json.Json
import kotlin.math.abs

/**
 * The one network call this file makes, behind a function type.
 *
 * This is a TEST SEAM, and it exists because the bug this module was fixed for lives HERE and not in
 * the matcher: which of the five strategies ran, what each one sent, which fields it therefore
 * constrained server-side, and whether the RAW or the cleaned artist tag reaches the matcher. A test
 * suite that only calls `List<Track>.bestMatchingFor` directly stays green while every one of those
 * decisions is reverted. `LrcLibStrategyTest` drives the real ladder through this seam.
 *
 * Production always uses [LrcLib.defaultSearch]; nothing substitutes it at runtime.
 */
internal typealias LrcLibSearch = suspend (
    trackName: String?,
    artistName: String?,
    albumName: String?,
    query: String?,
) -> List<Track>

object LrcLib {
    private val client by lazy {
        HttpClient(OkHttp) {
            install(ContentNegotiation) {
                json(
                    Json {
                        isLenient = true
                        ignoreUnknownKeys = true
                    },
                )
            }

            defaultRequest {
                url("https://lrclib.net")
            }

            expectSuccess = true
        }
    }

    // Patterns to clean from title
    private val titleCleanupPatterns = listOf(
        Regex("""\s*\(.*?(official|video|audio|lyrics|lyric|visualizer|hd|hq|4k|remaster|remix|live|acoustic|version|edit|extended|radio|clean|explicit).*?\)""", RegexOption.IGNORE_CASE),
        Regex("""\s*\[.*?(official|video|audio|lyrics|lyric|visualizer|hd|hq|4k|remaster|remix|live|acoustic|version|edit|extended|radio|clean|explicit).*?\]""", RegexOption.IGNORE_CASE),
        Regex("""\s*【.*?】"""),
        Regex("""\s*\|.*$"""),
        Regex("""\s*-\s*(official|video|audio|lyrics|lyric|visualizer).*$""", RegexOption.IGNORE_CASE),
        Regex("""\s*\(feat\..*?\)""", RegexOption.IGNORE_CASE),
        Regex("""\s*\(ft\..*?\)""", RegexOption.IGNORE_CASE),
        Regex("""\s*feat\..*$""", RegexOption.IGNORE_CASE),
        Regex("""\s*ft\..*$""", RegexOption.IGNORE_CASE),
    )

    // Patterns to extract primary artist
    private val artistSeparators = listOf(" & ", " and ", ", ", " x ", " X ", " feat. ", " feat ", " ft. ", " ft ", " featuring ", " with ")

    private fun cleanTitle(title: String): String {
        var cleaned = title.trim()
        for (pattern in titleCleanupPatterns) {
            cleaned = cleaned.replace(pattern, "")
        }
        return cleaned.trim()
    }

    /**
     * Reduces a credit list to its PRIMARY artist, because LrcLib's `artist_name=` filter wants a
     * single artist and matches poorly against a full credit list.
     *
     * This is lossy on purpose and everything after the first separator is DISCARDED:
     * "Bruno Mars, Mark Ronson" -> "Bruno Mars". That makes it correct for building the query and
     * wrong for judging a result - a local check fed this value can never recognise LrcLib's
     * "Mark Ronson" credit for the same recording. The raw tag is what goes to the matcher; see
     * [com.music.lrclib.models.compareArtists].
     */
    private fun cleanArtist(artist: String): String {
        var cleaned = artist.trim()
        // Get primary artist (first one before any separator)
        for (separator in artistSeparators) {
            if (cleaned.contains(separator, ignoreCase = true)) {
                cleaned = cleaned.split(separator, ignoreCase = true, limit = 2)[0]
                break
            }
        }
        return cleaned.trim()
    }

    private suspend fun queryLyricsWithParams(
        trackName: String? = null,
        artistName: String? = null,
        albumName: String? = null,
        query: String? = null,
    ): List<Track> = runCatching {
        client.get("/api/search") {
            if (query != null) parameter("q", query)
            if (trackName != null) parameter("track_name", trackName)
            if (artistName != null) parameter("artist_name", artistName)
            if (albumName != null) parameter("album_name", albumName)
        }.body<List<Track>>()
    }.getOrDefault(emptyList())

    /** The real network search. The only [LrcLibSearch] production code ever uses. */
    internal val defaultSearch: LrcLibSearch = { trackName, artistName, albumName, query ->
        queryLyricsWithParams(trackName, artistName, albumName, query)
    }

    /**
     * A result list plus the two facts that decide how much we are allowed to trust it: which
     * fields the query that produced it actually filtered SERVER-SIDE.
     *
     * `artist_name=` is a real artist filter and `track_name=` is a real title filter. The
     * free-text `q=` parameter is NEITHER: it is a fuzzy full-text search that happily returns
     * other artists, and by the same token other songs - a `q=` set counts as unconstrained on both
     * fields even when the artist and title were part of the query string. Whatever the server did
     * not constrain is re-judged locally; if it really did match, the local check passes anyway and
     * nothing is lost.
     */
    internal data class LyricsQueryResult(
        val tracks: List<Track>,
        val artistConstrained: Boolean,
        val titleConstrained: Boolean,
    ) {
        val isNotEmpty: Boolean get() = tracks.isNotEmpty()
    }

    internal suspend fun queryLyrics(
        artist: String,
        title: String,
        album: String? = null,
        search: LrcLibSearch = defaultSearch,
    ): LyricsQueryResult {
        val cleanedTitle = cleanTitle(title)
        val cleanedArtist = cleanArtist(artist)

        suspend fun query(
            artistConstrained: Boolean,
            titleConstrained: Boolean,
            trackName: String? = null,
            artistName: String? = null,
            albumName: String? = null,
            queryText: String? = null,
        ) = LyricsQueryResult(
            tracks = search(trackName, artistName, albumName, queryText)
                .filter { it.syncedLyrics != null || it.plainLyrics != null },
            artistConstrained = artistConstrained,
            titleConstrained = titleConstrained,
        )

        // Strategy 1: cleaned title AND artist -> both fields filtered by LrcLib
        var results = query(
            artistConstrained = true,
            titleConstrained = true,
            trackName = cleanedTitle,
            artistName = cleanedArtist,
            albumName = album,
        )
        if (results.isNotEmpty) return results

        // Strategy 2: cleaned title only -> title filtered, ANY artist can come back
        results = query(artistConstrained = false, titleConstrained = true, trackName = cleanedTitle)
        if (results.isNotEmpty) return results

        // Strategy 3: free text; artist and title are merely hints -> ANY artist AND ANY song
        results = query(
            artistConstrained = false,
            titleConstrained = false,
            queryText = "$cleanedArtist $cleanedTitle",
        )
        if (results.isNotEmpty) return results

        // Strategy 4: free text on the title alone -> ANY artist AND ANY song
        results = query(artistConstrained = false, titleConstrained = false, queryText = cleanedTitle)
        if (results.isNotEmpty) return results

        // Strategy 5: original title if different from cleaned -> both fields filtered by LrcLib
        if (cleanedTitle != title.trim()) {
            results = query(
                artistConstrained = true,
                titleConstrained = true,
                trackName = title.trim(),
                artistName = artist.trim(),
            )
        }

        return results
    }

    suspend fun getLyrics(
        title: String,
        artist: String,
        duration: Int,
        album: String? = null,
    ) = runCatching { resolveLyrics(title, artist, duration, album, defaultSearch) }

    /**
     * The body of [getLyrics], with the network call injectable. Throws when nothing clears the bar,
     * which is what [getLyrics] turns into a failed `Result`.
     */
    internal suspend fun resolveLyrics(
        title: String,
        artist: String,
        duration: Int,
        album: String? = null,
        search: LrcLibSearch = defaultSearch,
    ): String {
        val queryResult = queryLyrics(artist, title, album, search)
        val cleanedTitle = cleanTitle(title)

        // WRONG-SONG LYRICS FIX: the duration branch used to call bestMatchingForRelaxed(duration)
        // and pick purely on a +/-5s duration window, discarding the names entirely. On a title-only
        // result set (strategies 2/3/4) that routinely handed back another artist's song. The names
        // and the provenance of the result set now travel with the call; see Track.bestMatchingFor.
        //
        // The artist goes in RAW, not through cleanArtist: cleanArtist keeps only the credit before
        // the first separator, so passing its output would strip "Mark Ronson" out of
        // "Bruno Mars, Mark Ronson" before the comparison and reject the correct lyrics. The cleaned
        // form is for the query only, where LrcLib wants one artist.
        val res = queryResult.tracks.bestMatchingFor(
            duration = duration,
            trackName = cleanedTitle,
            artistName = artist,
            albumName = album,
            artistWasConstrained = queryResult.artistConstrained,
            titleWasConstrained = queryResult.titleConstrained,
        )?.let { track ->
            track.syncedLyrics ?: track.plainLyrics
        }?.let(LrcLib::Lyrics)

        return res?.text ?: throw IllegalStateException("Lyrics unavailable")
    }

    suspend fun getAllLyrics(
        title: String,
        artist: String,
        duration: Int,
        album: String? = null,
        callback: (String) -> Unit,
    ) = collectLyrics(title, artist, duration, album, defaultSearch, callback)

    /** The body of [getAllLyrics], with the network call injectable. */
    internal suspend fun collectLyrics(
        title: String,
        artist: String,
        duration: Int,
        album: String? = null,
        search: LrcLibSearch = defaultSearch,
        callback: (String) -> Unit,
    ) {
        val queryResult = queryLyrics(artist, title, album, search)
        val tracks = queryResult.tracks
        val cleanedTitle = cleanTitle(title)
        val cleanedArtist = cleanArtist(artist)
        var count = 0
        var plain = 0

        val sortedTracks = when {
            duration == -1 -> {
                tracks.sortedByDescending { track ->
                    var score = 0.0

                    if (track.syncedLyrics != null) score += 1.0

                    val titleSimilarity = calculateStringSimilarity(cleanedTitle, track.trackName)
                    val artistSimilarity = calculateStringSimilarity(cleanedArtist, track.artistName)
                    score += (titleSimilarity + artistSimilarity) / 2.0
                    
                    score
                }
            }
            else -> {
                // This list feeds the user-facing "search lyrics" sheet, where a human reads the
                // result before it is saved. Wrong-artist hits are pushed to the BOTTOM but are not
                // removed: this sheet is the escape hatch for the songs the automatic gate in
                // resolveLyrics() declines, and filtering it too would strand exactly those users.
                val byDuration = tracks.sortedBy { abs(it.duration.toInt() - duration) }
                if (queryResult.artistConstrained) {
                    byDuration
                } else {
                    // Raw tag, not cleanedArtist - see the note in resolveLyrics().
                    byDuration.rankedByArtistConfidence(artist)
                }
            }
        }

        sortedTracks.forEach { track ->
            currentCoroutineContext().ensureActive()
            if (count <= 4) {
                if (track.syncedLyrics != null && duration == -1) {
                    count++
                    track.syncedLyrics.let(callback)
                } else {
                    // Relaxed duration matching (±5 seconds)
                    if (track.syncedLyrics != null && abs(track.duration.toInt() - duration) <= 5) {
                        count++
                        track.syncedLyrics.let(callback)
                    }
                    if (track.plainLyrics != null && abs(track.duration.toInt() - duration) <= 5 && plain == 0) {
                        count++
                        plain++
                        track.plainLyrics.let(callback)
                    }
                }
            }
        }
    }

    private fun calculateStringSimilarity(str1: String, str2: String): Double {
        val s1 = str1.trim().lowercase()
        val s2 = str2.trim().lowercase()
        
        if (s1 == s2) return 1.0
        if (s1.isEmpty() || s2.isEmpty()) return 0.0
        
        return when {
            s1.contains(s2) || s2.contains(s1) -> 0.8
            else -> {
                val maxLength = maxOf(s1.length, s2.length)
                val distance = levenshteinDistance(s1, s2)
                1.0 - (distance.toDouble() / maxLength)
            }
        }
    }

    private fun levenshteinDistance(str1: String, str2: String): Int {
        val len1 = str1.length
        val len2 = str2.length
        val matrix = Array(len1 + 1) { IntArray(len2 + 1) }
        
        for (i in 0..len1) matrix[i][0] = i
        for (j in 0..len2) matrix[0][j] = j
        
        for (i in 1..len1) {
            for (j in 1..len2) {
                val cost = if (str1[i - 1] == str2[j - 1]) 0 else 1
                matrix[i][j] = minOf(
                    matrix[i - 1][j] + 1,      // deletion
                    matrix[i][j - 1] + 1,      // insertion
                    matrix[i - 1][j - 1] + cost // substitution
                )
            }
        }
        
        return matrix[len1][len2]
    }

    suspend fun lyrics(
        artist: String,
        title: String,
    ) = runCatching {
        queryLyrics(artist = artist, title = title, album = null).tracks
    }

    @JvmInline
    value class Lyrics(
        val text: String,
    ) {
        val sentences
            get() =
                runCatching {
                    buildMap {
                        put(0L, "")
                        text.trim().lines().filter { it.length >= 10 }.forEach {
                            put(
                                it[8].digitToInt() * 10L +
                                    it[7].digitToInt() * 100 +
                                    it[5].digitToInt() * 1000 +
                                    it[4].digitToInt() * 10000 +
                                    it[2].digitToInt() * 60 * 1000 +
                                    it[1].digitToInt() * 600 * 1000,
                                it.substring(10),
                            )
                        }
                    }
                }.getOrNull()
    }
}


