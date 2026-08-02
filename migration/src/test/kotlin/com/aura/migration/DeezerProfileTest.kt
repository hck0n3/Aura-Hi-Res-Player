package com.aura.migration

import com.aura.migration.model.CollectionKind
import com.aura.migration.net.HttpJson
import com.aura.migration.source.SourceError
import com.aura.migration.source.deezer.DeezerSource
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Deezer whole-library import. Pure unit tests: no network, no key, no OAuth — a fake [HttpJson]
 * answers by EXACT url, so these tests also pin down which endpoints we are allowed to hit.
 */
class DeezerProfileTest {

    /** Answers by exact url; anything unmapped is an empty page, which ends pagination. */
    private class FakeHttp(private val routes: Map<String, String>) : HttpJson {
        val calls = mutableListOf<String>()
        override suspend fun getJson(url: String, headers: Map<String, String>): JsonObject {
            calls += url
            val body = routes[url] ?: """{"data":[]}"""
            return Json.parseToJsonElement(body).jsonObject
        }
        override suspend fun getText(url: String, headers: Map<String, String>): String = ""
    }

    private val api = "https://api.deezer.com"

    private fun source(vararg routes: Pair<String, String>) = DeezerSource(FakeHttp(routes.toMap()))

    // ── entrada: perfil vs playlist ──────────────────────────────────────────────────────

    @Test fun `acepta urls de perfil`() {
        val s = source()
        listOf(
            "https://www.deezer.com/profile/123456789",
            "https://deezer.com/us/profile/123",
            "https://www.deezer.com/es/profile/987654321/loved",
            "deezer.com/es-ES/profile/42"
        ).forEach {
            assertTrue("no acepta $it", s.accepts(it))
            assertTrue("no lo ve como perfil: $it", s.isProfile(it))
        }

        assertEquals("123456789", s.parseProfileId("https://www.deezer.com/profile/123456789"))
        assertEquals("123", s.parseProfileId("https://deezer.com/us/profile/123"))
        assertEquals("987654321", s.parseProfileId("https://www.deezer.com/es/profile/987654321/loved"))
        assertEquals("42", s.parseProfileId("deezer.com/es-ES/profile/42"))
    }

    @Test fun `la playlist sigue siendo playlist`() {
        val s = source()
        // Un id pelado significa PLAYLIST y debe seguir significandolo.
        assertTrue(s.accepts("1234567890"))
        assertTrue("un id pelado no puede ser un perfil", !s.isProfile("1234567890"))
        assertEquals("1234567890", s.parseId("1234567890"))

        assertTrue(s.accepts("https://www.deezer.com/es/playlist/908622995"))
        assertTrue(!s.isProfile("https://www.deezer.com/es/playlist/908622995"))
        assertEquals("908622995", s.parseId("https://www.deezer.com/es/playlist/908622995"))
    }

    @Test fun `una url de perfil no es un id de playlist`() {
        val s = source()
        try {
            s.parseId("https://www.deezer.com/profile/123456789")
            throw AssertionError("deberia rechazarla")
        } catch (e: SourceError.Unsupported) {
            // esperado
        }
    }

    // ── ida y vuelta del id sintetico ────────────────────────────────────────────────────

    @Test fun `el id sintetico lleva el perfil dentro`() {
        val profile = "123456789"
        listOf(
            DeezerSource.ID_FAVORITES to CollectionKind.FAVORITE_TRACKS,
            DeezerSource.ID_SAVED_ALBUMS to CollectionKind.SAVED_ALBUMS,
            DeezerSource.ID_FOLLOWED_ARTISTS to CollectionKind.FOLLOWED_ARTISTS
        ).forEach { (prefix, kind) ->
            val id = DeezerSource.syntheticId(prefix, profile)
            assertEquals("$prefix:$profile", id)
            assertEquals(profile, DeezerSource.profileIdOf(id))
            assertEquals(kind, DeezerSource.syntheticKindOf(id))
            assertTrue(DeezerSource.isSyntheticId(id))
        }
    }

    @Test fun `un id real de playlist no es sintetico`() {
        assertNull(DeezerSource.profileIdOf("908622995"))
        assertNull(DeezerSource.syntheticKindOf("908622995"))
        assertTrue(!DeezerSource.isSyntheticId("908622995"))
    }

    // ── listado de la biblioteca ─────────────────────────────────────────────────────────

    @Test fun `el perfil lista las tres colecciones y luego las playlists`() = runBlocking {
        val s = source(
            "$api/user/7/tracks?index=0&limit=1" to
                """{"data":[{"id":1,"album":{"cover_medium":"fav.jpg"}}],"total":312}""",
            "$api/user/7/albums?index=0&limit=100" to
                """{"data":[
                    {"id":11,"title":"Un Verano Sin Ti","nb_tracks":23,"cover_medium":"a1.jpg"},
                    {"id":12,"title":"Motomami","nb_tracks":16,"cover_medium":"a2.jpg"}
                   ],"total":2}""",
            "$api/user/7/artists?index=0&limit=1" to
                """{"data":[{"id":21,"name":"Rosalia","picture_medium":"r.jpg"}],"total":57}""",
            "$api/user/7/playlists?index=0&limit=100" to
                """{"data":[
                    {"id":"908622995","title":"Salsa brava","nb_tracks":80,"picture_medium":"p.jpg"},
                    {"id":"1","title":"Loved Tracks","nb_tracks":312,"is_loved_track":true}
                   ],"total":2}"""
        )

        val out = s.listPlaylists("https://www.deezer.com/profile/7")

        assertEquals(4, out.size)

        assertEquals("Canciones favoritas", out[0].name)
        assertEquals("${DeezerSource.ID_FAVORITES}:7", out[0].id)
        assertEquals(CollectionKind.FAVORITE_TRACKS, out[0].kind)
        assertEquals(312, out[0].trackCount)
        assertEquals("fav.jpg", out[0].artworkUrl)

        assertEquals("Álbumes guardados", out[1].name)
        assertEquals("${DeezerSource.ID_SAVED_ALBUMS}:7", out[1].id)
        assertEquals(CollectionKind.SAVED_ALBUMS, out[1].kind)
        // 23 + 16 canciones, NO "2 albumes": es lo que ve la barra de progreso.
        assertEquals(39, out[1].trackCount)

        assertEquals("Artistas seguidos", out[2].name)
        assertEquals("${DeezerSource.ID_FOLLOWED_ARTISTS}:7", out[2].id)
        assertEquals(CollectionKind.FOLLOWED_ARTISTS, out[2].kind)
        assertEquals(57, out[2].trackCount)

        // La pseudo-playlist "Loved Tracks" se descarta: ya esta como FAVORITE_TRACKS.
        assertEquals("Salsa brava", out[3].name)
        assertEquals("908622995", out[3].id)
        assertEquals(CollectionKind.PLAYLIST, out[3].kind)
        assertEquals(80, out[3].trackCount)
    }

    @Test fun `un perfil privado avisa de que es privado`() {
        val s = source(
            "$api/user/7/tracks?index=0&limit=1" to PRIVATE,
            "$api/user/7/albums?index=0&limit=100" to PRIVATE,
            "$api/user/7/artists?index=0&limit=1" to PRIVATE,
            "$api/user/7/playlists?index=0&limit=100" to PRIVATE
        )
        try {
            runBlocking { s.listPlaylists("https://www.deezer.com/profile/7") }
            throw AssertionError("deberia avisar de perfil privado")
        } catch (e: SourceError.PrivatePlaylist) {
            // esperado
        }
    }

    @Test fun `la cuota de Deezer sale como RateLimited, no como privado`() {
        val s = source(
            "$api/user/7/tracks?index=0&limit=1" to
                """{"error":{"type":"Exception","message":"Quota limit exceeded","code":4}}"""
        )
        try {
            runBlocking { s.listPlaylists("https://www.deezer.com/profile/7") }
            throw AssertionError("deberia ser RateLimited")
        } catch (e: SourceError.RateLimited) {
            assertTrue(e.retryAfterMs > 0)
        }
    }

    // ── contenido ────────────────────────────────────────────────────────────────────────

    @Test fun `favoritas mapea segundos a milisegundos y los contribuidores`() = runBlocking {
        val s = source(
            "$api/user/7/tracks?index=0&limit=100" to
                """{"data":[
                    {"id":1,"title":"Titi Me Pregunto","title_short":"Titi Me Pregunto",
                     "duration":243,"isrc":"QM6MZ2200011","explicit_lyrics":true,
                     "artist":{"name":"Bad Bunny"},"album":{"title":"Un Verano Sin Ti"},
                     "contributors":[{"name":"Bad Bunny"},{"name":"Chencho Corleone"}]},
                    {"id":2,"title":"Despecha","duration":180,
                     "artist":{"name":"Rosalia"},"album":{"title":"Despecha"}}
                   ],"total":2}"""
        )

        val tracks = s.fetchTracks(DeezerSource.syntheticId(DeezerSource.ID_FAVORITES, "7"))

        assertEquals(2, tracks.size)
        // 243 SEGUNDOS -> 243000 ms. Sin esto el scorer compara 243 con 243000 y no casa nada.
        assertEquals(243_000L, tracks[0].durationMs)
        assertEquals(180_000L, tracks[1].durationMs)
        assertEquals(listOf("Bad Bunny", "Chencho Corleone"), tracks[0].artists)
        assertEquals(listOf("Rosalia"), tracks[1].artists)
        assertEquals("Un Verano Sin Ti", tracks[0].album)
        assertEquals("QM6MZ2200011", tracks[0].isrc)
        assertEquals(true, tracks[0].explicit)
        assertEquals(0, tracks[0].sourcePosition)
        assertEquals(1, tracks[1].sourcePosition)
    }

    @Test fun `los albumes guardados se expanden album a album y en orden`() = runBlocking {
        val s = source(
            "$api/user/7/albums?index=0&limit=100" to
                """{"data":[
                    {"id":11,"title":"Un Verano Sin Ti","nb_tracks":2},
                    {"id":12,"title":"Motomami","nb_tracks":1}
                   ],"total":2}""",
            "$api/album/11/tracks?index=0&limit=100" to
                """{"data":[
                    {"id":101,"title":"Moscow Mule","duration":245,"artist":{"name":"Bad Bunny"}},
                    {"id":102,"title":"Después de la Playa","duration":230,"artist":{"name":"Bad Bunny"}}
                   ]}""",
            "$api/album/12/tracks?index=0&limit=100" to
                """{"data":[
                    {"id":103,"title":"Saoko","duration":135,"artist":{"name":"Rosalia"}}
                   ]}"""
        )

        val tracks = s.fetchTracks(DeezerSource.syntheticId(DeezerSource.ID_SAVED_ALBUMS, "7"))

        assertEquals(3, tracks.size)
        assertEquals(listOf("Moscow Mule", "Después de la Playa", "Saoko"), tracks.map { it.title })
        assertEquals(listOf(0, 1, 2), tracks.map { it.sourcePosition })
        // /album/{id}/tracks no repite el objeto album: el titulo baja desde el listado.
        assertEquals("Un Verano Sin Ti", tracks[0].album)
        assertEquals("Motomami", tracks[2].album)
        assertEquals(245_000L, tracks[0].durationMs)
    }

    @Test fun `los artistas seguidos se leen por fetchArtists y no traen canciones`() = runBlocking {
        val s = source(
            "$api/user/7/artists?index=0&limit=100" to
                """{"data":[
                    {"id":21,"name":"Rosalia","picture_medium":"r.jpg"},
                    {"id":22,"name":"Bad Bunny","picture_medium":"b.jpg"}
                   ],"total":2}"""
        )
        val id = DeezerSource.syntheticId(DeezerSource.ID_FOLLOWED_ARTISTS, "7")

        val artists = s.fetchArtists(id)
        assertEquals(listOf("Rosalia", "Bad Bunny"), artists.map { it.name })
        assertEquals("r.jpg", artists[0].artworkUrl)

        // Una coleccion de artistas no tiene canciones que resolver.
        assertTrue(s.fetchTracks(id).isEmpty())
        // Y una playlist normal no devuelve artistas.
        assertTrue(s.fetchArtists("908622995").isEmpty())
    }

    // ── la playlist publica de siempre no cambia ─────────────────────────────────────────

    @Test fun `la playlist publica sigue funcionando igual`() = runBlocking {
        val http = FakeHttp(
            mapOf(
                "$api/playlist/908622995" to
                    """{"id":908622995,"title":"Salsa brava","nb_tracks":2,"picture_medium":"p.jpg"}""",
                "$api/playlist/908622995/tracks?index=0&limit=100" to
                    """{"data":[
                        {"id":1,"title":"Pedro Navaja","duration":440,"artist":{"name":"Ruben Blades"},
                         "album":{"title":"Siembra"}}
                       ]}"""
            )
        )
        val s = DeezerSource(http)

        val meta = s.listPlaylists("https://www.deezer.com/es/playlist/908622995").single()
        assertEquals("Salsa brava", meta.name)
        assertEquals(CollectionKind.PLAYLIST, meta.kind)
        assertEquals(2, meta.trackCount)

        val tracks = s.fetchTracks("908622995")
        assertEquals(1, tracks.size)
        assertEquals("Pedro Navaja", tracks[0].title)
        assertEquals(440_000L, tracks[0].durationMs)
        // Solo se han tocado los endpoints de siempre.
        assertTrue(http.calls.none { it.contains("/user/") })
    }

    private companion object {
        const val PRIVATE =
            """{"error":{"type":"OAuthException","message":"You can't access this resource","code":200}}"""
    }
}
