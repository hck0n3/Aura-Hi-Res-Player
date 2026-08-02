package com.aura.migration.source.deezer

import com.aura.migration.model.*
import com.aura.migration.net.*
import com.aura.migration.source.PlaylistSource
import com.aura.migration.source.SourceError
import kotlinx.coroutines.delay
import kotlinx.serialization.json.JsonObject

/**
 * Deezer via endpoints PUBLICOS, sin autenticacion.
 *
 * CONTEXTO IMPORTANTE: Deezer cerro el registro de apps nuevas para
 * particulares, asi que OAuth no es una opcion. Lo que SI sigue abierto y sin
 * key son los endpoints de catalogo: /playlist, /track, /album, /artist, /chart.
 *
 * Consecuencia: solo playlists PUBLICAS. El usuario debe pegar la URL.
 * Limite: 50 peticiones / 5 s -> de ahi el delay entre paginas.
 *
 * ---------------------------------------------------------------------------
 * WHOLE-LIBRARY IMPORT (additive, same "no key, no OAuth" rules)
 *
 * A user's own profile is readable through the very same public API as long as the profile is public:
 *   /user/{id}/tracks      favourite songs
 *   /user/{id}/albums      saved albums
 *   /user/{id}/artists     followed artists
 *   /user/{id}/playlists   their playlists
 *   /album/{id}/tracks     tracks of a saved album
 *
 * So pasting a PROFILE url imports the whole library. The three library collections are surfaced as
 * SYNTHETIC [SourcePlaylist] entries (see [CollectionKind]) so the resolver, the cache, the progress UI
 * and the ViewModel keep working unchanged — they still just see "a collection with an id".
 *
 * The synthetic id CARRIES THE PROFILE ID ("__deezer_favorites__:123456789") because [fetchTracks] and
 * [fetchArtists] receive ONLY that string: without it the source would not know whose library to read.
 */
class DeezerSource(private val http: HttpJson) : PlaylistSource {

    override val type = SourceType.DEEZER
    override val displayName = "Deezer"

    companion object {

        // ── synthetic collection ids ─────────────────────────────────────────────────────
        // Public on purpose: the app layer matches on these prefixes to decide where the resolved
        // tracks land (liked songs / library) instead of creating a playlist.

        /** Prefix of the synthetic id for the user's favourite songs. */
        const val ID_FAVORITES = "__deezer_favorites__"

        /** Prefix of the synthetic id for the user's saved albums. */
        const val ID_SAVED_ALBUMS = "__deezer_albums__"

        /** Prefix of the synthetic id for the artists the user follows. */
        const val ID_FOLLOWED_ARTISTS = "__deezer_artists__"

        /** Separator between the prefix and the profile id. Real Deezer ids are digits only. */
        private const val SEP = ":"

        /** Builds "__deezer_favorites__:123456789". */
        fun syntheticId(prefix: String, profileId: String): String = "$prefix$SEP$profileId"

        /** The profile id carried by a synthetic collection id, or null for a real playlist id. */
        fun profileIdOf(collectionId: String): String? =
            listOf(ID_FAVORITES, ID_SAVED_ALBUMS, ID_FOLLOWED_ARTISTS)
                .firstOrNull { collectionId.startsWith(it) }
                ?.let { collectionId.removePrefix(it).removePrefix(SEP).takeIf { p -> p.isNotBlank() } }

        /** What a synthetic id represents, or null if it is a real playlist id. */
        fun syntheticKindOf(collectionId: String): CollectionKind? = when {
            collectionId.startsWith(ID_FAVORITES) -> CollectionKind.FAVORITE_TRACKS
            collectionId.startsWith(ID_SAVED_ALBUMS) -> CollectionKind.SAVED_ALBUMS
            collectionId.startsWith(ID_FOLLOWED_ARTISTS) -> CollectionKind.FOLLOWED_ARTISTS
            else -> null
        }

        /** true if [collectionId] is one of the library collections, not a real playlist. */
        fun isSyntheticId(collectionId: String): Boolean = syntheticKindOf(collectionId) != null

        // ── internals ────────────────────────────────────────────────────────────────────

        private const val API = "https://api.deezer.com"
        private const val PAGE = 100
        private const val THROTTLE_MS = 130L   // ~38 req/5s, margen bajo el limite

        /** Deezer's window is 50 req / 5 s, so that is the sensible "come back later". */
        private const val RATE_LIMIT_RETRY_MS = 5_000L

        private val URL_RE = Regex(
            """(?:deezer\.com|deezer\.page\.link)/(?:[a-z]{2}/)?playlist/(\d+)""",
            RegexOption.IGNORE_CASE
        )

        /**
         * Profile url: deezer.com/profile/123, deezer.com/us/profile/123, deezer.com/es-es/profile/123,
         * and anything trailing (/loved, /playlists…). A BARE numeric id is NOT a profile: it already
         * means a playlist and must keep meaning that.
         */
        private val PROFILE_RE = Regex(
            """(?:deezer\.com|deezer\.page\.link)/(?:[a-z]{2}(?:[-_][a-zA-Z]{2})?/)?profile/(\d+)""",
            RegexOption.IGNORE_CASE
        )

        private val ID_RE = Regex("""^\d+$""")
    }

    override fun accepts(input: String) =
        URL_RE.containsMatchIn(input) ||
            PROFILE_RE.containsMatchIn(input) ||
            ID_RE.matches(input.trim())

    /** Extrae el id de una URL completa, un enlace corto ya resuelto, o un id pelado. */
    fun parseId(input: String): String =
        URL_RE.find(input)?.groupValues?.get(1)
            ?: input.trim().takeIf { ID_RE.matches(it) }
            ?: throw SourceError.Unsupported("URL de Deezer no reconocida")

    /** true si la entrada apunta a un PERFIL (biblioteca entera) y no a una sola playlist. */
    fun isProfile(input: String): Boolean = PROFILE_RE.containsMatchIn(input)

    /** Id numerico de un perfil de Deezer. */
    fun parseProfileId(input: String): String =
        PROFILE_RE.find(input)?.groupValues?.get(1)
            ?: throw SourceError.Unsupported("URL de perfil de Deezer no reconocida")

    override suspend fun listPlaylists(input: String?): List<SourcePlaylist> {
        val raw = input ?: throw SourceError.Unsupported("falta la URL")

        // A profile means "the whole library". Checked FIRST because a playlist url can never match
        // PROFILE_RE, so the branch below stays exactly as it was.
        PROFILE_RE.find(raw)?.groupValues?.get(1)?.let { return listLibrary(it) }

        val id = parseId(raw)
        val o = http.getJson("$API/playlist/$id")
        o.obj("error")?.let { throw SourceError.PrivatePlaylist() }
        return listOf(
            SourcePlaylist(
                id = id,
                name = o.str("title").ifBlank { "Playlist de Deezer" },
                description = o.strOrNull("description"),
                trackCount = o.longOrNull("nb_tracks")?.toInt() ?: 0,
                artworkUrl = o.strOrNull("picture_medium"),
                origin = type
            )
        )
    }

    override suspend fun fetchTracks(playlistId: String): List<SourceTrack> {
        // Synthetic collections first; a real Deezer playlist id is digits only, so no id can be
        // mistaken for one of these and the playlist path below is untouched.
        profileFor(playlistId, ID_FAVORITES)?.let { return fetchFavorites(it) }
        profileFor(playlistId, ID_SAVED_ALBUMS)?.let { return fetchSavedAlbumTracks(it) }
        // Followed artists carry no tracks at all; the pipeline reads them through fetchArtists().
        profileFor(playlistId, ID_FOLLOWED_ARTISTS)?.let { return emptyList() }

        val out = mutableListOf<SourceTrack>()
        var index = 0

        while (true) {
            val page = http.getJson("$API/playlist/$playlistId/tracks?index=$index&limit=$PAGE")
            page.obj("error")?.let { throw SourceError.PrivatePlaylist() }

            val data = page.arr("data")
            if (data.isEmpty()) break

            data.forEachIndexed { i, t ->
                out += SourceTrack(
                    title   = t.str("title_short").ifBlank { t.str("title") },
                    artists = listOfNotNull(t.obj("artist")?.strOrNull("name")),
                    album   = t.obj("album")?.strOrNull("title"),
                    // OJO: Deezer devuelve duracion en SEGUNDOS, no en ms.
                    durationMs = t.longOrNull("duration")?.times(1000),
                    isrc     = t.strOrNull("isrc"),
                    explicit = t.boolOrNull("explicit_lyrics"),
                    sourcePosition = index + i
                )
            }

            index += data.size
            if (data.size < PAGE) break
            delay(THROTTLE_MS)
        }
        return out
    }

    override suspend fun fetchArtists(collectionId: String): List<SourceArtist> {
        val profileId = profileFor(collectionId, ID_FOLLOWED_ARTISTS) ?: return emptyList()

        val out = mutableListOf<SourceArtist>()
        var index = 0
        while (true) {
            val page = getLibrary("$API/user/$profileId/artists?index=$index&limit=$PAGE")
            val data = page.arr("data")
            if (data.isEmpty()) break

            data.forEach { a ->
                val name = a.strOrNull("name") ?: return@forEach
                out += SourceArtist(name = name, artworkUrl = a.strOrNull("picture_medium"))
            }

            index += data.size
            if (data.size < PAGE) break
            delay(THROTTLE_MS)
        }
        return out
    }

    /**
     * El objeto ligero de /tracks a veces NO trae isrc. Solo si lo necesitas
     * y falta, se pide el track completo. Nunca por defecto: cuesta 1 req/track.
     */
    suspend fun enrichIsrc(trackId: String): String? =
        http.getJson("$API/track/$trackId").strOrNull("isrc")

    // ── biblioteca: listado ──────────────────────────────────────────────────────────────

    /**
     * The four collections of a public profile, in the order the user expects to see them.
     *
     * Every probe is tolerant on its own (one private collection must not kill the rest), but if NOTHING
     * at all is readable the profile itself is private -> [SourceError.PrivatePlaylist], which the UI
     * already turns into "hazlo publico y vuelve a intentarlo".
     */
    private suspend fun listLibrary(profileId: String): List<SourcePlaylist> {
        val out = mutableListOf<SourcePlaylist>()

        // limit=1: we only want `total` (for the progress UI) and one cover for the card.
        probe("$API/user/$profileId/tracks?index=0&limit=1") { it.obj("album")?.strOrNull("cover_medium") }
            ?.let {
                out += SourcePlaylist(
                    id = syntheticId(ID_FAVORITES, profileId),
                    name = "Canciones favoritas",
                    trackCount = it.total,
                    artworkUrl = it.artwork,
                    origin = type,
                    kind = CollectionKind.FAVORITE_TRACKS
                )
            }
        delay(THROTTLE_MS)

        // Saved albums are paginated in full here (not probed) because `total` counts ALBUMS, not
        // tracks: the progress UI would say 40 while importing 500 songs.
        savedAlbumsOrNull(profileId)?.let { albums ->
            out += SourcePlaylist(
                id = syntheticId(ID_SAVED_ALBUMS, profileId),
                name = "Álbumes guardados",
                trackCount = albums.sumOf { it.trackCount },
                artworkUrl = albums.firstOrNull()?.cover,
                origin = type,
                kind = CollectionKind.SAVED_ALBUMS
            )
        }
        delay(THROTTLE_MS)

        probe("$API/user/$profileId/artists?index=0&limit=1") { it.strOrNull("picture_medium") }
            ?.let {
                out += SourcePlaylist(
                    id = syntheticId(ID_FOLLOWED_ARTISTS, profileId),
                    name = "Artistas seguidos",
                    // Artists have no tracks; the count is how many artists will be added.
                    trackCount = it.total,
                    artworkUrl = it.artwork,
                    origin = type,
                    kind = CollectionKind.FOLLOWED_ARTISTS
                )
            }
        delay(THROTTLE_MS)

        out += userPlaylists(profileId)

        if (out.isEmpty()) throw SourceError.PrivatePlaylist()
        return out
    }

    private suspend fun userPlaylists(profileId: String): List<SourcePlaylist> {
        val out = mutableListOf<SourcePlaylist>()
        var index = 0

        while (true) {
            val page = try {
                getLibrary("$API/user/$profileId/playlists?index=$index&limit=$PAGE")
            } catch (e: SourceError.PrivatePlaylist) {
                return out   // private/absent playlists must not kill the collections above
            } catch (e: SourceError.NotFound) {
                return out
            }

            val data = page.arr("data")
            if (data.isEmpty()) break

            data.forEach { p ->
                val id = p.strOrNull("id") ?: return@forEach
                // "Loved Tracks" is exposed as a pseudo-playlist too; we already surface it as
                // FAVORITE_TRACKS, and importing both would duplicate the whole thing.
                if (p.boolOrNull("is_loved_track") == true) return@forEach
                out += SourcePlaylist(
                    id = id,
                    name = p.str("title").ifBlank { "Playlist de Deezer" },
                    description = p.strOrNull("description"),
                    trackCount = p.longOrNull("nb_tracks")?.toInt() ?: 0,
                    artworkUrl = p.strOrNull("picture_medium"),
                    origin = type,
                    kind = CollectionKind.PLAYLIST
                )
            }

            index += data.size
            if (data.size < PAGE) break
            delay(THROTTLE_MS)
        }
        return out
    }

    // ── biblioteca: contenido ────────────────────────────────────────────────────────────

    private suspend fun fetchFavorites(profileId: String): List<SourceTrack> {
        val out = mutableListOf<SourceTrack>()
        var index = 0

        while (true) {
            val page = getLibrary("$API/user/$profileId/tracks?index=$index&limit=$PAGE")
            val data = page.arr("data")
            if (data.isEmpty()) break

            data.forEachIndexed { i, t -> out += mapLibraryTrack(t, index + i) }

            index += data.size
            if (data.size < PAGE) break
            delay(THROTTLE_MS)
        }
        return out
    }

    /**
     * Every track of every saved album, album by album, in the order the albums are saved.
     *
     * This is the request-hungriest path in the whole importer (>= 1 request per album), so the throttle
     * applies to the ALBUM loop as well, not only to pagination.
     */
    private suspend fun fetchSavedAlbumTracks(profileId: String): List<SourceTrack> {
        val albums = savedAlbumsOrNull(profileId) ?: throw SourceError.PrivatePlaylist()
        val out = mutableListOf<SourceTrack>()
        var position = 0

        for (album in albums) {
            delay(THROTTLE_MS)
            var index = 0
            while (true) {
                val page = try {
                    getLibrary("$API/album/${album.id}/tracks?index=$index&limit=$PAGE")
                } catch (e: SourceError.NotFound) {
                    break   // album pulled from the catalogue: skip it, don't kill the import
                } catch (e: SourceError.PrivatePlaylist) {
                    break
                }

                val data = page.arr("data")
                if (data.isEmpty()) break

                // /album/{id}/tracks omits the `album` object (it is implied by the endpoint), so the
                // title travels down from the listing — album is a real discriminant for the scorer.
                data.forEach { t -> out += mapLibraryTrack(t, position++, fallbackAlbum = album.title) }

                index += data.size
                if (data.size < PAGE) break
                delay(THROTTLE_MS)
            }
        }
        return out
    }

    private data class AlbumRef(
        val id: String,
        val title: String?,
        val trackCount: Int,
        val cover: String?
    )

    /** Saved albums in order, or null when the collection is not readable (private profile). */
    private suspend fun savedAlbumsOrNull(profileId: String): List<AlbumRef>? {
        val out = mutableListOf<AlbumRef>()
        var index = 0

        while (true) {
            val page = try {
                getLibrary("$API/user/$profileId/albums?index=$index&limit=$PAGE")
            } catch (e: SourceError.PrivatePlaylist) {
                return if (out.isEmpty()) null else out
            } catch (e: SourceError.NotFound) {
                return if (out.isEmpty()) null else out
            }

            val data = page.arr("data")
            if (data.isEmpty()) break

            data.forEach { a ->
                val id = a.strOrNull("id") ?: return@forEach
                out += AlbumRef(
                    id = id,
                    title = a.strOrNull("title"),
                    trackCount = a.longOrNull("nb_tracks")?.toInt() ?: 0,
                    cover = a.strOrNull("cover_medium")
                )
            }

            index += data.size
            if (data.size < PAGE) break
            delay(THROTTLE_MS)
        }
        return out
    }

    // ── helpers ──────────────────────────────────────────────────────────────────────────

    /**
     * Same mapping as the playlist path, plus `contributors[]`: a Deezer track only exposes ONE
     * `artist`, and losing the featured artists costs matches on collaborations.
     */
    private fun mapLibraryTrack(
        t: JsonObject,
        position: Int,
        fallbackAlbum: String? = null
    ): SourceTrack = SourceTrack(
        title = t.str("title_short").ifBlank { t.str("title") },
        artists = contributorNames(t),
        album = t.obj("album")?.strOrNull("title") ?: fallbackAlbum,
        // OJO: Deezer devuelve duracion en SEGUNDOS, no en ms. Sin el *1000 el scorer compara
        // 200 con 200000 y hunde TODAS las puntuaciones sin decir nada.
        durationMs = t.longOrNull("duration")?.times(1000),
        isrc = t.strOrNull("isrc"),
        explicit = t.boolOrNull("explicit_lyrics"),
        sourcePosition = position
    )

    private fun contributorNames(t: JsonObject): List<String> {
        val contributors = t.arr("contributors").mapNotNull { it.strOrNull("name") }
        return contributors.ifEmpty { listOfNotNull(t.obj("artist")?.strOrNull("name")) }
    }

    /** GET + Deezer's "200 OK with an error object" mapped onto our typed errors. */
    private suspend fun getLibrary(url: String): JsonObject {
        val res = http.getJson(url)
        res.obj("error")?.let { throw it.asSourceError() }
        return res
    }

    /**
     * Deezer answers 200 OK with {"error":{"type":…,"message":…,"code":…}}. Code 4 is the quota and 700
     * is "service busy": both are "come back later", NOT "this is private" — and neither may crash.
     */
    private fun JsonObject.asSourceError(): SourceError {
        val code = longOrNull("code")
        val message = strOrNull("message").orEmpty()
        return if (code == 4L || code == 700L || message.contains("quota", ignoreCase = true)) {
            SourceError.RateLimited(RATE_LIMIT_RETRY_MS)
        } else {
            SourceError.PrivatePlaylist()
        }
    }

    private data class Probe(val total: Int, val artwork: String?)

    /**
     * Cheap "does this collection exist and how big is it" call. Returns null when the collection is not
     * readable; a rate limit still propagates, because retrying later is the right answer for it.
     */
    private suspend fun probe(url: String, artwork: (JsonObject) -> String?): Probe? {
        val res = try {
            getLibrary(url)
        } catch (e: SourceError.PrivatePlaylist) {
            return null
        } catch (e: SourceError.NotFound) {
            return null
        }
        val total = res.longOrNull("total")?.toInt()
            ?: res.longOrNull("nb_tracks")?.toInt()
            ?: res.arr("data").size
        return Probe(total, res.arr("data").firstOrNull()?.let(artwork))
    }

    /**
     * The profile id inside a synthetic collection id, or null if [collectionId] is not that collection.
     * The prefix without a profile id is unusable: fetching would silently read nobody's library.
     */
    private fun profileFor(collectionId: String, prefix: String): String? {
        if (!collectionId.startsWith(prefix)) return null
        val profile = collectionId.removePrefix(prefix).removePrefix(SEP)
        if (profile.isBlank()) {
            throw SourceError.Unsupported("coleccion de Deezer sin perfil: $collectionId")
        }
        return profile
    }
}
