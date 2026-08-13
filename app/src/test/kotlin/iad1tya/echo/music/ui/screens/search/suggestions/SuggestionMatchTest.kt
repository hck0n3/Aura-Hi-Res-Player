package iad1tya.echo.music.ui.screens.search.suggestions

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SuggestionMatchTest {

    @Test fun cacheKeysSeparateSongFromVideo() {
        val song = SuggestionMatch.cacheKey(SuggestionMatch.Kind.SONG, "SOS", "SZA")
        val video = SuggestionMatch.cacheKey(SuggestionMatch.Kind.VIDEO, "SOS", "SZA")
        assertNotEquals(song, video)
        assertTrue(song.startsWith("SONG:"))
        assertTrue(video.startsWith("VIDEO:"))
    }

    @Test fun primaryArtistKeepsCommaInName() {
        assertEquals(
            "Tyler, The Creator",
            SuggestionMatch.primaryArtistName("Tyler, The Creator"),
        )
        assertEquals(
            "Bad Bunny",
            SuggestionMatch.primaryArtistName("Bad Bunny & Jhayco"),
        )
        assertEquals(
            "SZA",
            SuggestionMatch.primaryArtistName("SZA feat. Travis Scott"),
        )
    }

    @Test fun artistMatchRejectsShortContains() {
        assertFalse(SuggestionMatch.artistMatches("Ed Sheeran", "Ed"))
        assertFalse(SuggestionMatch.artistMatches("Steve", "Eve"))
        assertTrue(SuggestionMatch.artistMatches("Bad Bunny & Jhayco", "Bad Bunny"))
        assertTrue(SuggestionMatch.artistMatches("Tyler, The Creator", "Tyler, The Creator"))
    }

    @Test fun titleMatchRejectsShortContains() {
        assertFalse(SuggestionMatch.titleMatches("Love Story", "Love"))
        assertTrue(SuggestionMatch.titleMatches("Love Story (Taylor's Version)", "Love Story"))
        assertTrue(SuggestionMatch.titleMatches("SOS", "SOS"))
    }

    @Test fun pickSongRequiresTitleAndArtist() {
        val titles = listOf("GUTS", "GUTS", "Vampire")
        val artists = listOf(
            listOf("Someone Else"),
            listOf("Olivia Rodrigo"),
            listOf("Olivia Rodrigo"),
        )
        assertEquals(
            1,
            SuggestionMatch.pickTitleArtist(titles, artists, "GUTS", "Olivia Rodrigo"),
        )
        assertNull(
            SuggestionMatch.pickTitleArtist(titles, artists, "Invented Track", "Olivia Rodrigo"),
        )
    }

    @Test fun pickAlbumDoesNotTakeFirstHit() {
        val titles = listOf("SOS", "SOS")
        val artists = listOf(listOf("Wrong Act"), listOf("SZA"))
        assertEquals(1, SuggestionMatch.pickTitleArtist(titles, artists, "SOS", "SZA"))
        assertNull(SuggestionMatch.pickTitleArtist(titles, artists, "SOS", "Nobody"))
    }

    @Test fun pickArtistRequiresNameMatch() {
        assertEquals(
            1,
            SuggestionMatch.pickArtist(listOf("Tyler Childers", "Tyler, The Creator"), "Tyler, The Creator"),
        )
        assertNull(SuggestionMatch.pickArtist(listOf("Ed Sheeran", "Edgar"), "Ed"))
    }
}
