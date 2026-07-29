package com.aura.migration

import com.aura.migration.model.SourceTrack
import com.aura.migration.model.YtmCandidate
import com.aura.migration.resolver.Scorer
import org.junit.Assert.*
import org.junit.Test

class ScorerTest {

    private fun src(t: String, a: String, d: Long? = 200_000, al: String? = null) =
        SourceTrack(title = t, artists = listOf(a), album = al, durationMs = d)

    private fun cand(t: String, a: String, d: Long? = 200_000, al: String? = null,
                     song: Boolean = true) =
        YtmCandidate("vid", t, listOf(a), al, d, isSong = song)

    @Test fun `match exacto supera ACCEPT`() {
        val s = Scorer.score(src("Blinding Lights", "The Weeknd"),
                             cand("Blinding Lights", "The Weeknd"))
        assertTrue("esperado >= ${Scorer.ACCEPT}, fue $s", s >= Scorer.ACCEPT)
    }

    @Test fun `duracion muy distinta hunde la puntuacion`() {
        val s = Scorer.score(src("Blinding Lights", "The Weeknd", 200_000),
                             cand("Blinding Lights", "The Weeknd", 400_000))
        assertTrue("version larga no deberia aceptarse, fue $s", s < Scorer.ACCEPT)
    }

    @Test fun `remix no pedido se penaliza`() {
        val s = Scorer.score(src("Titi Me Pregunto", "Bad Bunny"),
                             cand("Titi Me Pregunto - Remix", "Bad Bunny"))
        assertTrue("remix colado, fue $s", s < Scorer.ACCEPT)
    }

    @Test fun `directo no pedido se penaliza`() {
        val s = Scorer.score(src("Wonderwall", "Oasis"),
                             cand("Wonderwall (Live at Wembley)", "Oasis"))
        assertTrue("directo colado, fue $s", s < Scorer.ACCEPT)
    }

    @Test fun `video suelto puntua menos que cancion`() {
        val song  = Scorer.score(src("Song", "Artist"), cand("Song", "Artist", song = true))
        val video = Scorer.score(src("Song", "Artist"), cand("Song", "Artist", song = false))
        assertTrue(song > video)
    }

    @Test fun `tildes no penalizan`() {
        val s = Scorer.score(src("Corazón Partío", "Alejandro Sanz"),
                             cand("Corazon Partio", "Alejandro Sanz"))
        assertTrue("las tildes rompen el match, fue $s", s >= Scorer.ACCEPT)
    }

    @Test fun `artista equivocado no pasa`() {
        val s = Scorer.score(src("Hello", "Adele"), cand("Hello", "Lionel Richie"))
        assertTrue(s < Scorer.ACCEPT)
    }
}
