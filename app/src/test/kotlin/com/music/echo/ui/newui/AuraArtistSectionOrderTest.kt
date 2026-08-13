package iad1tya.echo.music.ui.newui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Owner: exploring an artist, "Tu biblioteca" must sit AFTER albums and videos — not
 * under populares, which packed the top of the page.
 */
class AuraArtistSectionOrderTest {

    @Test
    fun librarySitsAfterAlbumsAndVideos() {
        val titles = listOf(
            "Canciones populares",
            "Álbumes",
            "Vídeos",
            "Listas",
        )
        assertEquals(3, artistLibraryInsertIndex(titles))
    }

    @Test
    fun withoutVideosLibrarySitsAfterAlbums() {
        val titles = listOf("Canciones populares", "Albums", "Playlists")
        assertEquals(2, artistLibraryInsertIndex(titles))
    }

    @Test
    fun withoutAlbumsOrVideosLibrarySitsAfterPopulares() {
        val titles = listOf("Top songs", "Similar artists")
        assertEquals(1, artistLibraryInsertIndex(titles))
    }

    @Test
    fun emptyPagePutsLibraryAtTheStart() {
        assertEquals(0, artistLibraryInsertIndex(emptyList()))
    }

    @Test
    fun englishVideoTitleCountsAsVideos() {
        val titles = listOf("Top songs", "Albums", "Music videos", "Singles")
        assertEquals(3, artistLibraryInsertIndex(titles))
    }

    @Test
    fun youtubeSongsShelfCountsAsPopulares() {
        assertTrue(isArtistPopularSectionTitle("Songs"))
        assertTrue(isArtistPopularSectionTitle("Canciones"))
        assertTrue(isArtistPopularSectionTitle("Canciones más escuchadas"))
        assertEquals(0, appleArtistSectionRank("Songs"))
        assertEquals(0, appleArtistSectionRank("Canciones"))
    }
}
