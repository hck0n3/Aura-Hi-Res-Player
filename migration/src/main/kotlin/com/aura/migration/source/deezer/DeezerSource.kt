package com.aura.migration.source.deezer

import com.aura.migration.model.*
import com.aura.migration.net.*
import com.aura.migration.source.PlaylistSource
import com.aura.migration.source.SourceError
import kotlinx.coroutines.delay

/**
 * Deezer via endpoints PUBLICOS, sin autenticacion.
 *
 * CONTEXTO IMPORTANTE: Deezer cerro el registro de apps nuevas para
 * particulares, asi que OAuth no es una opcion. Lo que SI sigue abierto y sin
 * key son los endpoints de catalogo: /playlist, /track, /album, /artist, /chart.
 *
 * Consecuencia: solo playlists PUBLICAS. El usuario debe pegar la URL.
 * Limite: 50 peticiones / 5 s -> de ahi el delay entre paginas.
 */
class DeezerSource(private val http: HttpJson) : PlaylistSource {

    override val type = SourceType.DEEZER
    override val displayName = "Deezer"

    private companion object {
        const val API = "https://api.deezer.com"
        const val PAGE = 100
        const val THROTTLE_MS = 130L   // ~38 req/5s, margen bajo el limite
        val URL_RE = Regex(
            """(?:deezer\.com|deezer\.page\.link)/(?:[a-z]{2}/)?playlist/(\d+)""",
            RegexOption.IGNORE_CASE
        )
        val ID_RE = Regex("""^\d+$""")
    }

    override fun accepts(input: String) =
        URL_RE.containsMatchIn(input) || ID_RE.matches(input.trim())

    /** Extrae el id de una URL completa, un enlace corto ya resuelto, o un id pelado. */
    fun parseId(input: String): String =
        URL_RE.find(input)?.groupValues?.get(1)
            ?: input.trim().takeIf { ID_RE.matches(it) }
            ?: throw SourceError.Unsupported("URL de Deezer no reconocida")

    override suspend fun listPlaylists(input: String?): List<SourcePlaylist> {
        val id = parseId(input ?: throw SourceError.Unsupported("falta la URL"))
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

    /**
     * El objeto ligero de /tracks a veces NO trae isrc. Solo si lo necesitas
     * y falta, se pide el track completo. Nunca por defecto: cuesta 1 req/track.
     */
    suspend fun enrichIsrc(trackId: String): String? =
        http.getJson("$API/track/$trackId").strOrNull("isrc")
}
