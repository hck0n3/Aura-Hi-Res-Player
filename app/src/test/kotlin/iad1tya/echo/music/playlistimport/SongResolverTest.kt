package iad1tya.echo.music.playlistimport

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Exercises [SongResolver.resolveOrdered]'s precedence logic with the three lookups faked.
 * Uses [String] as the result type so no database/YouTube/Android types are needed.
 */
class SongResolverTest {

    @Test fun localMatchTakesPrecedenceAndSkipsSearch() = runBlocking {
        var searched = false
        val result = SongResolver.resolveOrdered(
            title = "t", artist = "a", videoId = "vid",
            localMatch = { _, _ -> "local" },
            fromVideoId = { "video" },
            search = { _, _ -> searched = true; "search" },
        )
        assertEquals("local", result)
        assertFalse(searched)
    }

    @Test fun videoIdUsedWhenNoLocalMatch() = runBlocking {
        var searched = false
        val result = SongResolver.resolveOrdered(
            title = "t", artist = "a", videoId = "vid",
            localMatch = { _, _ -> null },
            fromVideoId = { id -> if (id == "vid") "video" else null },
            search = { _, _ -> searched = true; "search" },
        )
        assertEquals("video", result)
        assertFalse(searched)
    }

    @Test fun searchFallbackWhenNoLocalNoVideo() = runBlocking {
        var capturedQuery: String? = null
        var capturedArtist: String? = null
        val result = SongResolver.resolveOrdered(
            title = "Song", artist = "Artist", videoId = null,
            localMatch = { _, _ -> null },
            fromVideoId = { null },
            search = { query, artist ->
                capturedQuery = query
                capturedArtist = artist
                "search"
            },
        )
        assertEquals("search", result)
        assertEquals("Song Artist", capturedQuery)
        assertEquals("Artist", capturedArtist)
    }

    @Test fun videoIdMissingFromMapFallsBackToSearch() = runBlocking {
        val result = SongResolver.resolveOrdered(
            title = "Song", artist = "", videoId = "missing",
            localMatch = { _, _ -> null },
            fromVideoId = { null },
            search = { _, _ -> "search" },
        )
        assertEquals("search", result)
    }

    @Test fun blankTitleAndArtistReturnsNullWithoutSearching() = runBlocking {
        var searched = false
        val result = SongResolver.resolveOrdered(
            title = "", artist = "", videoId = null,
            localMatch = { _, _ -> null },
            fromVideoId = { null },
            search = { _, _ -> searched = true; "search" },
        )
        assertNull(result)
        assertFalse(searched)
    }

    @Test fun queryOmitsBlankArtist() = runBlocking {
        var captured: String? = null
        SongResolver.resolveOrdered(
            title = "OnlyTitle", artist = "", videoId = null,
            localMatch = { _, _ -> null },
            fromVideoId = { null },
            search = { query, _ -> captured = query; "x" },
        )
        assertEquals("OnlyTitle", captured)
    }

    @Test fun artistMatchesIsAccentInsensitive() {
        assertTrue(SongResolver.artistMatches("Bad Bunny", "bad bunny"))
        assertTrue(SongResolver.artistMatches("José José", "Jose Jose"))
        assertTrue(SongResolver.artistMatches("Bad Bunny & Jhayco", "Bad Bunny"))
        assertFalse(SongResolver.artistMatches("J Balvin", "Bad Bunny"))
    }
}
