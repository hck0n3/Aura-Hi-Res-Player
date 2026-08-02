package com.aura.migration

import com.aura.migration.model.CollectionKind
import com.aura.migration.net.HttpJson
import com.aura.migration.source.SourceError
import com.aura.migration.source.tidal.TidalSource
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.*
import org.junit.Test

/**
 * Tidal LIBRARY collections (favourite tracks / saved albums / followed artists).
 *
 * Pure tests: a fake [HttpJson] records every URL and replays canned JSON:API documents, so this
 * covers the two things that actually broke imports in production — the endpoint+`include=` we build
 * (a flat include silently kills every match, see TidalSource) and the synthetic-id dispatch.
 */
class TidalCollectionTest {

    // ---- fake transport -------------------------------------------------------------------

    private class FakeHttp(private val route: (String) -> String) : HttpJson {
        val urls = mutableListOf<String>()
        override suspend fun getJson(url: String, headers: Map<String, String>): JsonObject {
            urls += url
            return Json.parseToJsonElement(route(url)).jsonObject
        }
        override suspend fun getText(url: String, headers: Map<String, String>): String {
            urls += url
            return route(url)
        }
    }

    private fun source(http: HttpJson, uid: String? = null) = TidalSource(
        http = http,
        tokenProvider = { "tok" },
        countryCode = "ES",
        userIdProvider = { uid },
    )

    private companion object {
        const val API = "https://openapi.tidal.com/v2"
        const val EMPTY = """{"data":[]}"""
    }

    // ---- listPlaylists: synthetic entries -------------------------------------------------

    @Test fun `la biblioteca aparece antes que las playlists reales`() {
        val playlists = """
            {"data":[{"type":"playlists","id":"p1"}],
             "included":[{"type":"playlists","id":"p1","attributes":{"name":"Mi lista","numberOfItems":7}}]}
        """.trimIndent()
        val http = FakeHttp { url ->
            when {
                url.contains("/relationships/playlists") -> playlists
                // count probes
                url.endsWith("page[limit]=1") -> """{"data":[],"meta":{"total":42}}"""
                else -> EMPTY
            }
        }

        val out = runBlocking { source(http).listPlaylists(null) }

        assertEquals(4, out.size)
        assertEquals(
            listOf(
                TidalSource.COLLECTION_FAVORITE_TRACKS,
                TidalSource.COLLECTION_SAVED_ALBUMS,
                TidalSource.COLLECTION_FOLLOWED_ARTISTS,
                "p1",
            ),
            out.map { it.id }
        )
        assertEquals(
            listOf(
                CollectionKind.FAVORITE_TRACKS,
                CollectionKind.SAVED_ALBUMS,
                CollectionKind.FOLLOWED_ARTISTS,
                CollectionKind.PLAYLIST,
            ),
            out.map { it.kind }
        )
        assertEquals(
            listOf("Canciones favoritas", "Álbumes guardados", "Artistas seguidos", "Mi lista"),
            out.map { it.name }
        )
        // meta.total is honoured when the deployment sends it
        assertEquals(listOf(42, 42, 42, 7), out.map { it.trackCount })
    }

    @Test fun `el conteo es 0 y no revienta cuando no hay meta total`() {
        val http = FakeHttp { EMPTY }
        val out = runBlocking { source(http).listPlaylists(null) }
        assertEquals(3, out.size)
        assertTrue(out.all { it.trackCount == 0 })
    }

    @Test fun `la sonda de conteo es una sola pagina y sin include`() {
        val http = FakeHttp { EMPTY }
        runBlocking { source(http).listPlaylists(null) }

        val probes = http.urls.filter { it.endsWith("page[limit]=1") }
        assertEquals(3, probes.size)
        assertEquals(
            listOf(
                "$API/userCollections/me/relationships/tracks?countryCode=ES&page[limit]=1",
                "$API/userCollections/me/relationships/albums?countryCode=ES&page[limit]=1",
                "$API/userCollections/me/relationships/artists?countryCode=ES&page[limit]=1",
            ),
            probes
        )
    }

    @Test fun `una URL de playlist suelta NO trae la biblioteca`() {
        val http = FakeHttp {
            """{"data":{"type":"playlists","id":"x","attributes":{"name":"Compartida"}}}"""
        }
        val out = runBlocking {
            source(http).listPlaylists("https://tidal.com/playlist/aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee")
        }
        assertEquals(1, out.size)
        assertEquals(CollectionKind.PLAYLIST, out[0].kind)
        assertEquals("Compartida", out[0].name)
    }

    @Test fun `la ruta de playlists reales no cambia de include`() {
        val http = FakeHttp { EMPTY }
        runBlocking { source(http).listPlaylists(null) }
        assertEquals(
            "$API/userCollections/me/relationships/playlists" +
                "?countryCode=ES&include=playlists&page[limit]=100",
            http.urls.first()
        )
    }

    // ---- favourite tracks -----------------------------------------------------------------

    @Test fun `favoritas usa el include anidado y respeta el orden de data`() {
        val page = """
            {"data":[{"type":"tracks","id":"t1"},{"type":"tracks","id":"t2"}],
             "included":[
               {"type":"tracks","id":"t2","attributes":{"title":"Dos","duration":"PT3M0S"},
                "relationships":{"artists":{"data":[{"type":"artists","id":"a2"}]}}},
               {"type":"tracks","id":"t1","attributes":{"title":"Uno","duration":"PT4M0S","isrc":"ES123"},
                "relationships":{"artists":{"data":[{"type":"artists","id":"a1"}]},
                                 "albums":{"data":[{"type":"albums","id":"al1"}]}}},
               {"type":"artists","id":"a1","attributes":{"name":"Artista Uno"}},
               {"type":"artists","id":"a2","attributes":{"name":"Artista Dos"}},
               {"type":"albums","id":"al1","attributes":{"title":"Album Uno"}}
             ]}
        """.trimIndent()
        val http = FakeHttp { page }

        val out = runBlocking { source(http).fetchTracks(TidalSource.COLLECTION_FAVORITE_TRACKS) }

        assertEquals(
            "$API/userCollections/me/relationships/tracks" +
                "?countryCode=ES&include=tracks,tracks.artists,tracks.albums&page[limit]=100",
            http.urls.single()
        )
        // `included` came shuffled; the RELATIONSHIP order wins
        assertEquals(listOf("Uno", "Dos"), out.map { it.title })
        assertEquals(listOf(0, 1), out.map { it.sourcePosition })
        // the whole point of the nested include: artists must NOT be empty
        assertEquals(listOf("Artista Uno", "Artista Dos"), out.map { it.primaryArtist })
        assertEquals("Album Uno", out[0].album)
        assertEquals("ES123", out[0].isrc)
        assertTrue(out[0].durationMs == 240_000L)
    }

    @Test fun `favoritas reinyecta el include anidado en la pagina 2`() {
        var call = 0
        val http = FakeHttp {
            if (call++ == 0) {
                // Tidal echoes the cursor with a NARROWER include — the exact bug that emptied
                // every artist from page 2 on.
                """{"data":[],"included":[],"links":{"next":"?page[cursor]=abc&include=tracks"}}"""
            } else EMPTY
        }

        runBlocking { source(http).fetchTracks(TidalSource.COLLECTION_FAVORITE_TRACKS) }

        assertEquals(2, http.urls.size)
        assertEquals(
            "$API/userCollections/me/relationships/tracks" +
                "?page[cursor]=abc&include=tracks,tracks.artists,tracks.albums",
            http.urls[1]
        )
    }

    @Test fun `una coleccion vacia devuelve lista vacia`() {
        val http = FakeHttp { EMPTY }
        val out = runBlocking { source(http).fetchTracks(TidalSource.COLLECTION_FAVORITE_TRACKS) }
        assertTrue(out.isEmpty())
    }

    @Test fun `un 404 en la coleccion no revienta la importacion`() {
        val http = FakeHttp { throw SourceError.NotFound() }
        val tracks = runBlocking { source(http).fetchTracks(TidalSource.COLLECTION_FAVORITE_TRACKS) }
        val artists = runBlocking { source(http).fetchArtists(TidalSource.COLLECTION_FOLLOWED_ARTISTS) }
        assertTrue(tracks.isEmpty())
        assertTrue(artists.isEmpty())
    }

    // ---- saved albums ---------------------------------------------------------------------

    @Test fun `albumes guardados recorre cada album en orden de tracklist`() {
        val collection = """
            {"data":[{"type":"albums","id":"al1"},{"type":"albums","id":"al2"}],
             "included":[{"type":"albums","id":"al2","attributes":{"title":"Segundo"}},
                         {"type":"albums","id":"al1","attributes":{"title":"Primero"}}]}
        """.trimIndent()
        val al1 = """
            {"data":[{"type":"tracks","id":"t1"},{"type":"videos","id":"v1"},{"type":"tracks","id":"t2"}],
             "included":[
               {"type":"tracks","id":"t1","attributes":{"title":"A1"},
                "relationships":{"artists":{"data":[{"type":"artists","id":"x"}]}}},
               {"type":"tracks","id":"t2","attributes":{"title":"A2"},
                "relationships":{"artists":{"data":[{"type":"artists","id":"x"}]}}},
               {"type":"videos","id":"v1","attributes":{"title":"Clip"}},
               {"type":"artists","id":"x","attributes":{"name":"Equis"}}
             ]}
        """.trimIndent()
        val al2 = """
            {"data":[{"type":"tracks","id":"t3"}],
             "included":[
               {"type":"tracks","id":"t3","attributes":{"title":"B1"},
                "relationships":{"artists":{"data":[{"type":"artists","id":"y"}]}}},
               {"type":"artists","id":"y","attributes":{"name":"Ye"}}
             ]}
        """.trimIndent()

        val http = FakeHttp { url ->
            when {
                url.contains("/userCollections/") -> collection
                url.contains("/albums/al1/") -> al1
                url.contains("/albums/al2/") -> al2
                else -> EMPTY
            }
        }

        val out = runBlocking { source(http).fetchTracks(TidalSource.COLLECTION_SAVED_ALBUMS) }

        assertEquals(
            listOf(
                "$API/userCollections/me/relationships/albums" +
                    "?countryCode=ES&include=albums&page[limit]=100",
                "$API/albums/al1/relationships/items" +
                    "?countryCode=ES&include=items,items.artists&page[limit]=100",
                "$API/albums/al2/relationships/items" +
                    "?countryCode=ES&include=items,items.artists&page[limit]=100",
            ),
            http.urls
        )
        // videos dropped, album order preserved, position continuous across albums
        assertEquals(listOf("A1", "A2", "B1"), out.map { it.title })
        assertEquals(listOf(0, 1, 2), out.map { it.sourcePosition })
        // the parent album title is injected, so items.albums is not needed
        assertEquals(listOf("Primero", "Primero", "Segundo"), out.map { it.album })
        assertEquals(listOf("Equis", "Equis", "Ye"), out.map { it.primaryArtist })
    }

    // ---- followed artists -----------------------------------------------------------------

    @Test fun `artistas seguidos devuelve nombre y portada`() {
        val page = """
            {"data":[{"type":"artists","id":"a1"},{"type":"artists","id":"a2"}],
             "included":[
               {"type":"artists","id":"a1","attributes":{"name":"Uno",
                 "imageLinks":[{"href":"small","meta":{"width":80}},{"href":"big","meta":{"width":750}}]}},
               {"type":"artists","id":"a2","attributes":{"name":"Dos","picture":"pic"}}
             ]}
        """.trimIndent()
        val http = FakeHttp { page }

        val out = runBlocking { source(http).fetchArtists(TidalSource.COLLECTION_FOLLOWED_ARTISTS) }

        assertEquals(
            "$API/userCollections/me/relationships/artists" +
                "?countryCode=ES&include=artists&page[limit]=100",
            http.urls.single()
        )
        assertEquals(listOf("Uno", "Dos"), out.map { it.name })
        assertEquals(listOf("big", "pic"), out.map { it.artworkUrl })
    }

    @Test fun `los artistas seguidos no tienen canciones`() {
        val http = FakeHttp { fail("no deberia haber ninguna peticion"); EMPTY }
        val out = runBlocking { source(http).fetchTracks(TidalSource.COLLECTION_FOLLOWED_ARTISTS) }
        assertTrue(out.isEmpty())
        assertTrue(http.urls.isEmpty())
    }

    @Test fun `fetchArtists ignora cualquier id que no sea la coleccion de artistas`() {
        val http = FakeHttp { fail("no deberia haber ninguna peticion"); EMPTY }
        assertTrue(runBlocking { source(http).fetchArtists("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee") }.isEmpty())
        assertTrue(runBlocking { source(http).fetchArtists(TidalSource.COLLECTION_FAVORITE_TRACKS) }.isEmpty())
        assertTrue(http.urls.isEmpty())
    }

    // ---- the me / numeric-id fallback, shared by every collection --------------------------

    @Test fun `si me da 404 se reintenta con el id numerico en toda coleccion`() {
        val http = FakeHttp { url ->
            if (url.contains("/userCollections/me/")) throw SourceError.NotFound()
            EMPTY
        }

        runBlocking {
            val s = source(http, uid = "12345")
            s.listPlaylists(null)
            s.fetchTracks(TidalSource.COLLECTION_FAVORITE_TRACKS)
            s.fetchTracks(TidalSource.COLLECTION_SAVED_ALBUMS)
            s.fetchArtists(TidalSource.COLLECTION_FOLLOWED_ARTISTS)
        }

        // every collection retried against the numeric id, none of them gave up on the 404
        assertTrue(http.urls.any { it.startsWith("$API/userCollections/12345/relationships/playlists") })
        assertTrue(http.urls.any { it.startsWith("$API/userCollections/12345/relationships/tracks?countryCode=ES&include=") })
        assertTrue(http.urls.any { it.startsWith("$API/userCollections/12345/relationships/albums?countryCode=ES&include=") })
        assertTrue(http.urls.any { it.startsWith("$API/userCollections/12345/relationships/artists?countryCode=ES&include=") })
        // the count probes ran against the resolved segment, not the rejected `me`
        assertTrue(http.urls.any { it == "$API/userCollections/12345/relationships/tracks?countryCode=ES&page[limit]=1" })
    }

    @Test fun `sin id numerico un 404 de me no propaga en las colecciones de biblioteca`() {
        val http = FakeHttp { throw SourceError.NotFound() }
        val s = source(http, uid = null)
        assertTrue(runBlocking { s.fetchTracks(TidalSource.COLLECTION_SAVED_ALBUMS) }.isEmpty())
        assertTrue(runBlocking { s.fetchArtists(TidalSource.COLLECTION_FOLLOWED_ARTISTS) }.isEmpty())
    }

    // ---- playlists keep behaving exactly as before ------------------------------------------

    @Test fun `una playlist real sigue usando su endpoint y su include`() {
        val http = FakeHttp { EMPTY }
        runBlocking { source(http).fetchTracks("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee") }
        assertEquals(
            "$API/playlists/aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee/relationships/items" +
                "?countryCode=ES&include=items,items.artists,items.albums&page[limit]=100",
            http.urls.single()
        )
    }

    @Test fun `una playlist real sigue propagando el 404`() {
        val http = FakeHttp { throw SourceError.NotFound() }
        try {
            runBlocking { source(http).fetchTracks("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee") }
            fail("un 404 de una playlist real debe seguir siendo un error")
        } catch (_: SourceError.NotFound) {
            // expected
        }
    }

    @Test fun `isLibraryCollection reconoce solo los ids sinteticos`() {
        assertTrue(TidalSource.isLibraryCollection(TidalSource.COLLECTION_FAVORITE_TRACKS))
        assertTrue(TidalSource.isLibraryCollection(TidalSource.COLLECTION_SAVED_ALBUMS))
        assertTrue(TidalSource.isLibraryCollection(TidalSource.COLLECTION_FOLLOWED_ARTISTS))
        assertFalse(TidalSource.isLibraryCollection("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee"))
    }
}
