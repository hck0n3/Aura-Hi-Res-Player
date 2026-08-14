package iad1tya.echo.music.ui.newui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Apple album/playlist rows: duration, disc numbers, and when the artist line is omitted.
 * If these drift, the owner sees the old YTM list again (cover on every track, no times).
 */
class AuraAppleListTest {

    @Test
    fun durationFormatsMinutesAndSeconds() {
        assertEquals("3:05", auraAppleDurationLabel(185))
        assertEquals("0:07", auraAppleDurationLabel(7))
        assertEquals("1:00:01", auraAppleDurationLabel(3601))
    }

    @Test
    fun durationHidesUnknownAndZero() {
        assertNull(auraAppleDurationLabel(null))
        assertNull(auraAppleDurationLabel(0))
        assertNull(auraAppleDurationLabel(-1))
    }

    @Test
    fun albumOmitsArtistWhenItMatchesTheAlbum() {
        assertNull(auraAppleAlbumSubtitle("Rosalía", "Rosalía"))
        assertNull(auraAppleAlbumSubtitle("  Rosalía  ", "rosalía"))
        assertEquals("Feat. Artist", auraAppleAlbumSubtitle("Feat. Artist", "Rosalía"))
        assertNull(auraAppleAlbumSubtitle("", "Rosalía"))
    }

    @Test
    fun trackNumberFollowsDiscOrderNotFilteredIndex() {
        val album = listOf("a", "b", "c", "d")
        // "b" hidden by explicit filter → remaining c is still track 3, not 2.
        assertEquals(3, auraAppleTrackNumber(album, "c", fallbackIndex = 1))
        assertEquals(1, auraAppleTrackNumber(album, "a", fallbackIndex = 0))
        assertEquals(4, auraAppleTrackNumber(emptyList(), "missing", fallbackIndex = 3))
    }
}
