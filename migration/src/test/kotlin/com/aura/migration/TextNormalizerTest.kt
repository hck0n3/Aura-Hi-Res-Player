package com.aura.migration

import com.aura.migration.resolver.TextNormalizer
import org.junit.Assert.*
import org.junit.Test

class TextNormalizerTest {

    @Test fun `quita ruido de titulo`() {
        assertEquals("blinding lights",
            TextNormalizer.normalize("Blinding Lights (Official Music Video)"))
        assertEquals("bohemian rhapsody",
            TextNormalizer.normalize("Bohemian Rhapsody - 2011 Remaster"))
        assertEquals("dakiti",
            TextNormalizer.normalize("DÁKITI (feat. Jhay Cortez)"))
    }

    @Test fun `quita acentos - critico para catalogo latino`() {
        assertEquals(
            TextNormalizer.normalize("Corazón Partío"),
            TextNormalizer.normalize("Corazon Partio")
        )
        assertEquals("el nino", TextNormalizer.normalize("El Niño"))
    }

    @Test fun `NO borra etiquetas de version - son senal, no ruido`() {
        assertTrue(TextNormalizer.normalize("Song (Live)").contains("live"))
        assertTrue(TextNormalizer.normalize("Song - Remix").contains("remix"))
        assertTrue(TextNormalizer.normalize("Song (Acoustic)").contains("acoustic"))
    }

    @Test fun `conserva escrituras no latinas`() {
        assertTrue(TextNormalizer.normalize("夜に駆ける").isNotBlank())
        assertTrue(TextNormalizer.normalize("아무노래").isNotBlank())
    }

    @Test fun `artista principal ignora colaboradores`() {
        assertEquals("bad bunny", TextNormalizer.normalizeArtist("Bad Bunny & Jhay Cortez"))
        assertEquals("rosalia", TextNormalizer.normalizeArtist("ROSALÍA, The Weeknd"))
    }

    @Test fun `detecta etiquetas de version`() {
        assertTrue("live" in TextNormalizer.versionTagsIn("Wonderwall (Live at Wembley)"))
        assertTrue("remix" in TextNormalizer.versionTagsIn("Titi Me Pregunto - Remix"))
        assertTrue(TextNormalizer.versionTagsIn("Normal Song").isEmpty())
    }
}
