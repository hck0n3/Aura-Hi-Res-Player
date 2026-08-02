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
 *
 * Besides real playlists, the user's LIBRARY (favourite tracks, saved albums, followed artists) is
 * surfaced as SYNTHETIC [SourcePlaylist] entries carrying a non-PLAYLIST [CollectionKind]. That keeps
 * the resolver, the match cache, the progress UI and the ViewModel completely unchanged: to them a
 * library collection is just another playlist to walk. Only the final insertion step looks at [kind].
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

    companion object {
        // ---- Synthetic collection ids -------------------------------------------------------
        // These are NOT Tidal ids: they are the handles the whole pipeline uses to talk about the
        // user's library. The app layer matches on them too (to decide "like the song" vs "create a
        // playlist"), so they live here as constants — never as string literals at the call sites.
        // A real Tidal playlist id is a UUID, so the "__…__" shape can never collide with one.

        /** The user's favourite TRACKS. [CollectionKind.FAVORITE_TRACKS]. */
        const val COLLECTION_FAVORITE_TRACKS = "__tidal_favorites__"

        /** Albums saved to the user's collection. [CollectionKind.SAVED_ALBUMS]. */
        const val COLLECTION_SAVED_ALBUMS = "__tidal_albums__"

        /** Artists the user follows. [CollectionKind.FOLLOWED_ARTISTS]; carries no tracks. */
        const val COLLECTION_FOLLOWED_ARTISTS = "__tidal_artists__"

        /** true if [id] is one of the synthetic library collections above. */
        fun isLibraryCollection(id: String): Boolean =
            id == COLLECTION_FAVORITE_TRACKS ||
                id == COLLECTION_SAVED_ALBUMS ||
                id == COLLECTION_FOLLOWED_ARTISTS

        // TODO CONFIRMAR la base real en developer.tidal.com
        private const val API = "https://openapi.tidal.com/v2"
        private const val PAGE = 100

        // ---- include= parameters ------------------------------------------------------------
        // Every one of these is NESTED on purpose. See [fetchPlaylistTracks] for the full autopsy:
        // a flat include sideloads only the track resources, their artists/albums stay bare links,
        // `included` holds no artist resources, every track comes back artist-less and the scorer's
        // -35 artist gate kills the entire import.
        private const val INCLUDE_PLAYLIST_ITEMS = "include=items,items.artists,items.albums"
        private const val INCLUDE_FAVORITE_TRACKS = "include=tracks,tracks.artists,tracks.albums"
        // The parent album is known here, so items.albums would only re-send what we already have.
        private const val INCLUDE_ALBUM_ITEMS = "include=items,items.artists"
        private const val INCLUDE_SAVED_ALBUMS = "include=albums"
        private const val INCLUDE_FOLLOWED_ARTISTS = "include=artists"
        private const val INCLUDE_PLAYLISTS = "include=playlists"

        private val URL_RE = Regex(
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
        // Si viene una URL suelta, resolvemos solo esa.
        // NOTE: no synthetic library entries here on purpose — a shared playlist link says nothing
        // about the pasting user's own library, and there may not even be a session.
        input?.let { raw ->
            URL_RE.find(raw)?.groupValues?.get(1)?.let { id ->
                return listOf(fetchPlaylistMeta(id))
            }
        }

        // Try the `me` alias first; if the deployment 404s it, retry with the numeric user id.
        return withUserSegment { fetchLibrary(it) }
    }

    /**
     * Runs [block] against the `me` alias, retrying with the numeric user id when the deployment
     * 404s it.
     *
     * Every `/userCollections/…` endpoint has the same problem, so the fallback lives here once
     * instead of being copy-pasted into playlists + favourites + albums + artists (four chances to
     * fix it in three places and ship the fourth broken).
     *
     * Keep the block SMALL: it is retried wholesale, so it must only contain the request whose 404
     * actually means "wrong user segment". Per-item follow-up requests (e.g. the tracks of each
     * saved album) are issued OUTSIDE, or one missing album would re-run the whole collection.
     */
    private suspend fun <T> withUserSegment(block: suspend (String) -> T): T = try {
        block("me")
    } catch (e: SourceError.NotFound) {
        val uid = userIdProvider() ?: throw e
        block(uid)
    }

    /** Synthetic library collections first, then the user's real playlists. */
    private suspend fun fetchLibrary(userSegment: String): List<SourcePlaylist> {
        // Real playlists FIRST on the wire: this is the request whose 404 tells [withUserSegment]
        // that the `me` alias is unsupported, and we do not want to spend the count probes below on
        // a segment that is about to be discarded. Display order is the reverse (library on top).
        val playlists = fetchCollection(userSegment)
        return syntheticCollections(userSegment) + playlists
    }

    private suspend fun syntheticCollections(userSegment: String): List<SourcePlaylist> = listOf(
        SourcePlaylist(
            id = COLLECTION_FAVORITE_TRACKS,
            name = "Canciones favoritas",
            trackCount = collectionTotal(userSegment, "tracks"),
            origin = type,
            kind = CollectionKind.FAVORITE_TRACKS
        ),
        SourcePlaylist(
            id = COLLECTION_SAVED_ALBUMS,
            name = "Álbumes guardados",
            trackCount = collectionTotal(userSegment, "albums"),
            origin = type,
            kind = CollectionKind.SAVED_ALBUMS
        ),
        SourcePlaylist(
            id = COLLECTION_FOLLOWED_ARTISTS,
            name = "Artistas seguidos",
            trackCount = collectionTotal(userSegment, "artists"),
            origin = type,
            kind = CollectionKind.FOLLOWED_ARTISTS
        ),
    )

    /**
     * Best-effort size of a collection: ONE request with page[limit]=1 and no include (a few bytes)
     * just to read JSON:API's `meta.total`. Never paginates, never throws — a missing/unknown total
     * is reported as 0, which the UI already handles.
     */
    private suspend fun collectionTotal(userSegment: String, relationship: String): Int =
        runCatching {
            val res = http.getJson(
                "$API/userCollections/$userSegment/relationships/$relationship" +
                    "?countryCode=$countryCode&page[limit]=1",
                headers()
            )
            val meta = res.obj("meta")
            (meta?.longOrNull("total")
                ?: meta?.longOrNull("totalItems")
                ?: meta?.longOrNull("totalNumberOfItems"))?.toInt()
        }.getOrNull() ?: 0

    private suspend fun fetchCollection(userSegment: String): List<SourcePlaylist> {
        val out = mutableListOf<SourcePlaylist>()
        var url: String? = "$API/userCollections/$userSegment/relationships/playlists" +
                           "?countryCode=$countryCode&$INCLUDE_PLAYLISTS&page[limit]=$PAGE"

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
            url = nextPageUrl(res, INCLUDE_PLAYLISTS, current = url!!)
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

    /**
     * Tracks of a playlist OR of a synthetic library collection.
     *
     * A 404 on a library collection is treated as "empty", not as an error: Tidal answers an untouched
     * favourites/albums collection inconsistently across deployments, and failing a whole import
     * because the user never saved an album would be absurd. Real playlists keep throwing NotFound.
     */
    override suspend fun fetchTracks(playlistId: String): List<SourceTrack> = when (playlistId) {
        COLLECTION_FAVORITE_TRACKS ->
            emptyOnMissing { withUserSegment { fetchFavoriteTracks(it) } }
        COLLECTION_SAVED_ALBUMS ->
            emptyOnMissing { fetchSavedAlbumTracks() }
        // Followed artists carry no tracks at all; see fetchArtists().
        COLLECTION_FOLLOWED_ARTISTS -> emptyList()
        else -> fetchPlaylistTracks(playlistId)
    }

    private suspend fun <T> emptyOnMissing(block: suspend () -> List<T>): List<T> = try {
        block()
    } catch (_: SourceError.NotFound) {
        emptyList()
    }

    private suspend fun fetchPlaylistTracks(playlistId: String): List<SourceTrack> {
        val out = mutableListOf<SourceTrack>()
        // include=items ALONE sideloads only the track resources — their `artists`/`albums`
        // relationships stay as bare links, so `included` holds NO artist resources and every
        // extractArtists() returns empty => artist score 0 + the -35 artist gate => a hard ceiling
        // of ~50 (REVIEW) => ZERO auto-matches for the whole playlist. Nesting the includes
        // (items.artists, items.albums) makes the server put the artist/album resources in
        // `included` AND populate each track's relationship linkage, which is what the extractors
        // below read. items.albums is cheap and turns album into a real +10 discriminant.
        var url: String? = "$API/playlists/$playlistId/relationships/items" +
                           "?countryCode=$countryCode&$INCLUDE_PLAYLIST_ITEMS&page[limit]=$PAGE"
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

            url = nextPageUrl(res, INCLUDE_PLAYLIST_ITEMS, current = url!!)
        }
        return out
    }

    /** The user's favourite TRACKS collection. Same nested-include contract as a playlist. */
    private suspend fun fetchFavoriteTracks(userSegment: String): List<SourceTrack> {
        val out = mutableListOf<SourceTrack>()
        var url: String? = "$API/userCollections/$userSegment/relationships/tracks" +
                           "?countryCode=$countryCode&$INCLUDE_FAVORITE_TRACKS&page[limit]=$PAGE"
        var position = 0

        while (url != null) {
            val res = http.getJson(url, headers())
            orderedResources(res, "tracks").forEach { node ->
                out += (trackFrom(node, res, position) ?: return@forEach)
                position++
            }
            url = nextPageUrl(res, INCLUDE_FAVORITE_TRACKS, current = url!!)
        }
        return out
    }

    /** Every track of every saved album, album by album, in tracklist order. */
    private suspend fun fetchSavedAlbumTracks(): List<SourceTrack> {
        // Only the collection listing goes through the me/numeric fallback (see [withUserSegment]).
        val albums = withUserSegment { fetchSavedAlbums(it) }

        val out = mutableListOf<SourceTrack>()
        var position = 0
        for ((albumId, albumTitle) in albums) {
            var url: String? = "$API/albums/$albumId/relationships/items" +
                               "?countryCode=$countryCode&$INCLUDE_ALBUM_ITEMS&page[limit]=$PAGE"
            while (url != null) {
                // One album pulled from the catalogue (404) must not sink the other 200. Only THAT
                // is swallowed: an expired token or a rate limit still fails loudly, or a whole
                // import would come back half empty with no explanation.
                val res = try {
                    http.getJson(url!!, headers())
                } catch (_: SourceError.NotFound) {
                    break
                }
                // `included` is an unordered sideload bag, but for an album the order IS the
                // tracklist, so we walk the relationship linkage instead. Videos are dropped.
                orderedResources(res, "tracks").forEach { node ->
                    out += (trackFrom(node, res, position, albumTitle) ?: return@forEach)
                    position++
                }
                url = nextPageUrl(res, INCLUDE_ALBUM_ITEMS, current = url!!)
            }
        }
        return out
    }

    /** Saved albums as (id, title), in collection order. */
    private suspend fun fetchSavedAlbums(userSegment: String): List<Pair<String, String?>> {
        val out = mutableListOf<Pair<String, String?>>()
        val seen = mutableSetOf<String>()
        var url: String? = "$API/userCollections/$userSegment/relationships/albums" +
                           "?countryCode=$countryCode&$INCLUDE_SAVED_ALBUMS&page[limit]=$PAGE"

        while (url != null) {
            val res = http.getJson(url, headers())
            orderedResources(res, "albums").forEach { node ->
                val id = node.str("id")
                if (id.isBlank() || !seen.add(id)) return@forEach
                out += id to node.obj("attributes")?.strOrNull("title")
            }
            url = nextPageUrl(res, INCLUDE_SAVED_ALBUMS, current = url!!)
        }
        return out
    }

    /** Artistas seguidos. Solo responde a la coleccion sintetica de artistas. */
    override suspend fun fetchArtists(collectionId: String): List<SourceArtist> {
        if (collectionId != COLLECTION_FOLLOWED_ARTISTS) return emptyList()
        return emptyOnMissing { withUserSegment { fetchFollowedArtists(it) } }
    }

    private suspend fun fetchFollowedArtists(userSegment: String): List<SourceArtist> {
        val out = mutableListOf<SourceArtist>()
        val seen = mutableSetOf<String>()
        var url: String? = "$API/userCollections/$userSegment/relationships/artists" +
                           "?countryCode=$countryCode&$INCLUDE_FOLLOWED_ARTISTS&page[limit]=$PAGE"

        while (url != null) {
            val res = http.getJson(url, headers())
            orderedResources(res, "artists").forEach { node ->
                val id = node.str("id")
                if (id.isNotBlank() && !seen.add(id)) return@forEach
                val a = node.obj("attributes") ?: return@forEach
                val name = a.strOrNull("name") ?: return@forEach
                out += SourceArtist(name = name, artworkUrl = extractArtwork(a))
            }
            url = nextPageUrl(res, INCLUDE_FOLLOWED_ARTISTS, current = url!!)
        }
        return out
    }

    /**
     * Resources of [type] on a relationship page, in the order the RELATIONSHIP declares them.
     *
     * `included` is an unordered sideload bag — reading it directly is fine for a set, wrong for a
     * tracklist. We walk `data` (the ordered linkage) and resolve each id against `included`,
     * falling back to `included` when a deployment omits `data`.
     */
    private fun orderedResources(
        res: kotlinx.serialization.json.JsonObject,
        type: String,
    ): List<kotlinx.serialization.json.JsonObject> {
        val included = res.arr("included").filter { it.str("type") == type }
        val order = res.arr("data").filter { it.str("type") == type }.map { it.str("id") }
        if (order.isEmpty()) return included
        val byId = included.associateBy { it.str("id") }
        return order.mapNotNull { byId[it] }
    }

    /**
     * Builds a [SourceTrack] from a sideloaded `tracks` resource. [albumTitle] overrides the linked
     * album when the caller already knows it (saved albums), so we can skip the items.albums include.
     */
    private fun trackFrom(
        node: kotlinx.serialization.json.JsonObject,
        root: kotlinx.serialization.json.JsonObject,
        position: Int,
        albumTitle: String? = null,
    ): SourceTrack? {
        val a = node.obj("attributes") ?: return null
        return SourceTrack(
            title = a.str("title"),
            artists = extractArtists(node, root),
            album = albumTitle ?: extractAlbum(node, root),
            durationMs = parseIso8601Duration(a.strOrNull("duration")),
            isrc = a.strOrNull("isrc"),
            explicit = a.strOrNull("explicit")?.toBooleanStrictOrNull(),
            sourcePosition = position
        )
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

    /**
     * Cover of an artist/album resource. v2 exposes `imageLinks: [{href, meta:{width,height}}]`;
     * older shapes only carry a flat `picture`. Purely cosmetic — never fails.
     */
    private fun extractArtwork(attributes: kotlinx.serialization.json.JsonObject): String? {
        val links = attributes.arr("imageLinks")
        if (links.isNotEmpty()) {
            return links
                .maxByOrNull { it.obj("meta")?.longOrNull("width") ?: 0L }
                ?.strOrNull("href")
        }
        return attributes.strOrNull("picture")
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
