package com.aura.migration.source.tidal

import com.aura.migration.model.*
import com.aura.migration.net.*
import com.aura.migration.source.PlaylistSource
import com.aura.migration.source.SourceError

/**
 * Tidal via Open API v2 (developer.tidal.com).
 *
 * !! NO usar api.tidal.com (v1). Devuelve 403 / subStatus 11004 exigiendo el
 *    scope r_usr, que NO se puede solicitar en el flujo de autorizacion.
 *    Solo funcionan los endpoints documentados en /reference/web-api.
 *
 * La v2 habla JSON:API -> respuestas con data / included / relationships.
 * Los DTOs modelan eso desde el principio; adaptarlo despues es doloroso.
 */
class TidalSource(
    private val http: HttpJson,
    private val tokenProvider: suspend () -> String?,
    private val countryCode: String = "ES",
    // The numeric user id, if known — a fallback for the `me` path segment, which some v2 deployments
    // reject (404) in favour of the explicit id. Optional so existing callers/tests are unaffected.
    private val userIdProvider: suspend () -> String? = { null },
) : PlaylistSource {

    override val type = SourceType.TIDAL
    override val displayName = "Tidal"

    private companion object {
        // TODO CONFIRMAR la base real en developer.tidal.com
        const val API = "https://openapi.tidal.com/v2"
        const val PAGE = 100
        val URL_RE = Regex(
            """tidal\.com/(?:browse/)?playlist/([0-9a-f\-]{36})""",
            RegexOption.IGNORE_CASE
        )
    }

    override fun accepts(input: String) = URL_RE.containsMatchIn(input)

    private suspend fun headers(): Map<String, String> {
        val token = tokenProvider() ?: throw SourceError.NotAuthenticated(type)
        return mapOf(
            "Authorization" to "Bearer $token",
            "Accept" to "application/vnd.api+json"
        )
    }

    /** Biblioteca del usuario autenticado. */
    override suspend fun listPlaylists(input: String?): List<SourcePlaylist> {
        // Si viene una URL suelta, resolvemos solo esa
        input?.let { raw ->
            URL_RE.find(raw)?.groupValues?.get(1)?.let { id ->
                return listOf(fetchPlaylistMeta(id))
            }
        }

        // Try the `me` alias first; if the deployment 404s it, retry with the numeric user id.
        return try {
            fetchCollection("me")
        } catch (e: SourceError.NotFound) {
            val uid = userIdProvider() ?: throw e
            fetchCollection(uid)
        }
    }

    private suspend fun fetchCollection(userSegment: String): List<SourcePlaylist> {
        val out = mutableListOf<SourcePlaylist>()
        var url: String? = "$API/userCollections/$userSegment/relationships/playlists" +
                           "?countryCode=$countryCode&include=playlists&page[limit]=$PAGE"

        while (url != null) {
            val res = http.getJson(url, headers())
            // Read playlist objects from BOTH shapes: the full resources land in `included` when
            // include=playlists is honoured; if the deployment inlines attributes under `data` instead,
            // take them there. Nodes with no attributes (bare {type,id} linkage) are skipped.
            (res.arr("included") + res.arr("data"))
                .filter { it.str("type") == "playlists" }
                .forEach { node ->
                    val a = node.obj("attributes") ?: return@forEach
                    val id = node.str("id")
                    if (id.isBlank() || out.any { it.id == id }) return@forEach
                    out += SourcePlaylist(
                        id = id,
                        name = a.str("name").ifBlank { "Playlist" },
                        description = a.strOrNull("description"),
                        trackCount = a.longOrNull("numberOfItems")?.toInt() ?: 0,
                        origin = type
                    )
                }
            url = nextPageUrl(res, "include=playlists", current = url!!)
        }
        return out
    }

    private suspend fun fetchPlaylistMeta(id: String): SourcePlaylist {
        val res = http.getJson("$API/playlists/$id?countryCode=$countryCode", headers())
        val a = res.obj("data")?.obj("attributes")
        return SourcePlaylist(
            id = id,
            name = a?.str("name")?.ifBlank { "Playlist" } ?: "Playlist",
            description = a?.strOrNull("description"),
            trackCount = a?.longOrNull("numberOfItems")?.toInt() ?: 0,
            origin = type
        )
    }

    override suspend fun fetchTracks(playlistId: String): List<SourceTrack> {
        val out = mutableListOf<SourceTrack>()
        // include=items ALONE sideloads only the track resources — their `artists`/`albums`
        // relationships stay as bare links, so `included` holds NO artist resources and every
        // extractArtists() returns empty => artist score 0 + the -35 artist gate => a hard ceiling
        // of ~50 (REVIEW) => ZERO auto-matches for the whole playlist. Nesting the includes
        // (items.artists, items.albums) makes the server put the artist/album resources in
        // `included` AND populate each track's relationship linkage, which is what the extractors
        // below read. items.albums is cheap and turns album into a real +10 discriminant.
        var url: String? = "$API/playlists/$playlistId/relationships/items" +
                           "?countryCode=$countryCode&include=items,items.artists,items.albums&page[limit]=$PAGE"
        var position = 0

        while (url != null) {
            val res = http.getJson(url, headers())

            res.arr("included")
                .filter { it.str("type") == "tracks" }
                .forEach { node ->
                    val a = node.obj("attributes") ?: return@forEach
                    out += SourceTrack(
                        title = a.str("title"),
                        artists = extractArtists(node, res),
                        album = extractAlbum(node, res),
                        durationMs = parseIso8601Duration(a.strOrNull("duration")),
                        isrc = a.strOrNull("isrc"),
                        explicit = a.strOrNull("explicit")?.toBooleanStrictOrNull(),
                        sourcePosition = position++
                    )
                }

            url = nextPageUrl(res, "include=items,items.artists,items.albums", current = url!!)
        }
        return out
    }

    /**
     * Resolves the JSON:API `links.next` cursor into an absolute URL, defensively.
     *
     * Three real hazards this guards, all of which broke long playlists:
     *  1. `links.next` MAY already be absolute. Blindly prefixing [API] produced
     *     "https://openapi.tidal.com/v2https://…", and OkHttp's Request.Builder().url(...) throws
     *     IllegalArgumentException for that — which the HTTP layer only retries for IOException, so it
     *     escaped and failed the WHOLE import.
     *  2. The cursor may drop our `include=`. Without it the next page's `included` carries no track
     *     resources, so page 2+ silently yields ZERO items and the playlist is truncated at [PAGE] with
     *     no error at all. We re-attach the include when it's missing.
     *  3. A malformed/relative-without-slash cursor: treated as "no more pages" instead of crashing.
     */
    private fun nextPageUrl(
        res: kotlinx.serialization.json.JsonObject,
        include: String,
        current: String,
    ): String? {
        val next = res.obj("links")?.strOrNull("next") ?: return null
        val absolute = when {
            next.startsWith("http://", ignoreCase = true) ||
                next.startsWith("https://", ignoreCase = true) -> next
            next.startsWith("/") -> "$API$next"
            // A cursor we don't know how to resolve (e.g. a bare "?page[cursor]=…"). Rebuild it onto the
            // CURRENT url's path instead of silently ending the list: returning null here truncated the
            // playlist with no error at all, which for a migration tool is worse than failing loudly.
            next.startsWith("?") -> current.substringBefore('?') + next
            else -> return null
        }
        // Force OUR include. Testing only for the PRESENCE of "include=" was not enough: if the server
        // echoes the cursor with the original narrower `include=items`, page 2+ carries no artist
        // resources again -> empty artists -> the -35 artist gate -> zero auto-matches from page 2 on,
        // i.e. the exact bug this whole change fixes, resurfacing after the first 100 tracks.
        val withoutInclude = absolute
            .replace(Regex("([?&])include=[^&]*&?"), "$1")
            .trimEnd('?', '&')
        val resolved = withoutInclude + (if ("?" in withoutInclude) "&" else "?") + include
        // A server echoing the same cursor would loop forever; treat it as the end of the list.
        return resolved.takeIf { it != current }
    }

    private fun extractArtists(
        node: kotlinx.serialization.json.JsonObject,
        root: kotlinx.serialization.json.JsonObject
    ): List<String> {
        val ids = node.obj("relationships")?.obj("artists")?.arr("data")
            ?.map { it.str("id") }?.toSet() ?: emptySet()
        if (ids.isEmpty()) return emptyList()
        // Preserve the relationship order (main artist first) instead of the `included` order, so
        // SourceTrack.primaryArtist is the track's lead artist, not whichever resource landed first.
        val byId = root.arr("included")
            .filter { it.str("type") == "artists" && it.str("id") in ids }
            .associate { it.str("id") to it.obj("attributes")?.strOrNull("name") }
        return node.obj("relationships")?.obj("artists")?.arr("data")
            ?.mapNotNull { byId[it.str("id")] } ?: emptyList()
    }

    /** First linked album title (nice-to-have: feeds albumScore, never required for a match). */
    private fun extractAlbum(
        node: kotlinx.serialization.json.JsonObject,
        root: kotlinx.serialization.json.JsonObject
    ): String? {
        val id = node.obj("relationships")?.obj("albums")?.arr("data")
            ?.firstOrNull()?.str("id")?.takeIf { it.isNotBlank() } ?: return null
        return root.arr("included")
            .firstOrNull { it.str("type") == "albums" && it.str("id") == id }
            ?.obj("attributes")?.strOrNull("title")
    }

    /** Tidal v2 da duraciones ISO-8601 ("PT3M21S"). */
    internal fun parseIso8601Duration(s: String?): Long? {
        if (s.isNullOrBlank()) return null
        val m = Regex("""PT(?:(\d+)H)?(?:(\d+)M)?(?:(\d+(?:\.\d+)?)S)?""").find(s)
            ?: return null
        val (h, min, sec) = m.destructured
        val total = (h.toLongOrNull() ?: 0L) * 3600 +
                    (min.toLongOrNull() ?: 0L) * 60 +
                    (sec.toDoubleOrNull() ?: 0.0).toLong()
        return total * 1000
    }
}
