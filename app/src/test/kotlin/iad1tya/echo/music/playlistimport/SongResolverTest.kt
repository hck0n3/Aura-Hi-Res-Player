package iad1tya.echo.music.playlistimport

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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
            search = { searched = true; "search" },
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
            search = { searched = true; "search" },
        )
        assertEquals("video", result)
        assertFalse(searched)
    }

    @Test fun searchFallbackWhenNoLocalNoVideo() = runBlocking {
        var captured: String? = null
        val result = SongResolver.resolveOrdered(
            title = "Song", artist = "Artist", videoId = null,
            localMatch = { _, _ -> null },
            fromVideoId = { null },
            search = { query -> captured = query; "search" },
        )
        assertEquals("search", result)
        assertEquals("Song Artist", captured)
    }

    @Test fun videoIdMissingFromMapFallsBackToSearch() = runBlocking {
        val result = SongResolver.resolveOrdered(
            title = "Song", artist = "", videoId = "missing",
            localMatch = { _, _ -> null },
            fromVideoId = { null },
            search = { "search" },
        )
        assertEquals("search", result)
    }

    @Test fun blankTitleAndArtistReturnsNullWithoutSearching() = runBlocking {
        var searched = false
        val result = SongResolver.resolveOrdered(
            title = "", artist = "", videoId = null,
            localMatch = { _, _ -> null },
            fromVideoId = { null },
            search = { searched = true; "search" },
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
            search = { query -> captured = query; "x" },
        )
        assertEquals("OnlyTitle", captured)
    }
}
