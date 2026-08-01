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
            url = res.obj("links")?.strOrNull("next")?.let { "$API$it" }
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
        var url: String? = "$API/playlists/$playlistId/relationships/items" +
                           "?countryCode=$countryCode&include=items&page[limit]=$PAGE"
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
                        album = null,   // requiere include=albums; opcional
                        durationMs = parseIso8601Duration(a.strOrNull("duration")),
                        isrc = a.strOrNull("isrc"),
                        explicit = a.strOrNull("explicit")?.toBooleanStrictOrNull(),
                        sourcePosition = position++
                    )
                }

            url = res.obj("links")?.strOrNull("next")?.let { "$API$it" }
        }
        return out
    }

    private fun extractArtists(
        node: kotlinx.serialization.json.JsonObject,
        root: kotlinx.serialization.json.JsonObject
    ): List<String> {
        val ids = node.obj("relationships")?.obj("artists")?.arr("data")
            ?.map { it.str("id") }?.toSet() ?: emptySet()
        if (ids.isEmpty()) return emptyList()
        return root.arr("included")
            .filter { it.str("type") == "artists" && it.str("id") in ids }
            .mapNotNull { it.obj("attributes")?.strOrNull("name") }
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
