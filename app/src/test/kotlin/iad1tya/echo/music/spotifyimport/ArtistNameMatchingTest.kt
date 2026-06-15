package iad1tya.echo.music.spotifyimport

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ArtistNameMatchingTest {
    @Test fun normalizesCaseAndAccents() {
        assertEquals("beyonce", ArtistNameMatching.normalize("Beyoncé"))
        assertEquals("acdc", ArtistNameMatching.normalize("AC/DC"))
        assertEquals("the weeknd", ArtistNameMatching.normalize("  The   Weeknd "))
    }

    @Test fun matchesWhenNormalizedEqual() {
        assertTrue(ArtistNameMatching.isLikelyMatch("Beyoncé", "BEYONCE"))
    }

    @Test fun rejectsDifferentArtists() {
        assertFalse(ArtistNameMatching.isLikelyMatch("Drake", "Drizzy XYZ"))
    }
}
