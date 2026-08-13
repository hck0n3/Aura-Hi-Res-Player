package iad1tya.echo.music.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AiPlaylistConstraintsTest {

    @Test fun extractsSoloArtistFromSoloPrefix() {
        assertEquals("Bad Bunny", AiPlaylistConstraints.extractSoloArtist("solo Bad Bunny"))
    }

    @Test fun extractsSoloArtistFromCancionesDe() {
        assertEquals("Rosalía", AiPlaylistConstraints.extractSoloArtist("canciones de Rosalía"))
    }

    @Test fun extractsSoloArtistFromNadaMas() {
        assertEquals("Feid", AiPlaylistConstraints.extractSoloArtist("Feid nada más"))
    }

    @Test fun rejectsGenreOnlyPrompts() {
        assertNull(AiPlaylistConstraints.extractSoloArtist("rock para correr"))
        assertNull(AiPlaylistConstraints.extractSoloArtist("salsa romántica"))
    }

    @Test fun artistAllowedAcceptsMatchingCredit() {
        assertTrue(AiPlaylistConstraints.artistAllowed("Bad Bunny", "Bad Bunny"))
        assertTrue(AiPlaylistConstraints.artistAllowed("Bad Bunny & Jhayco", "Bad Bunny"))
    }

    @Test fun artistAllowedRejectsOtherAct() {
        assertTrue(!AiPlaylistConstraints.artistAllowed("J Balvin", "Bad Bunny"))
    }
}
