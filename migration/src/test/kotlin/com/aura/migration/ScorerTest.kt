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

    // --- NO_DURATION_BONUS: la rama que se activa cuando la FUENTE no trae duracion.
    // Ninguno de los tests de arriba la ejecuta (todos usan d=200_000), asi que iba sin cobertura.

    @Test fun `sin duracion, match identico si auto-acepta`() {
        val s = Scorer.score(src("Blinding Lights", "The Weeknd", d = null),
                             cand("Blinding Lights", "The Weeknd"))
        assertTrue("un match exacto sin duracion deberia aceptarse, fue $s", s >= Scorer.ACCEPT)
    }

    @Test fun `sin duracion, otra version NO auto-acepta aunque el titulo se parezca`() {
        // Caso real que colaba: titleSim ~0.93 y "radio version" NO esta en VERSION_TAGS, asi que
        // versionPenalty no dispara. Sin duracion no hay nada que descarte el otro master -> debe
        // quedarse en revision, no insertarse solo.
        val s = Scorer.score(src("Boulevard of Broken Dreams", "Green Day", d = null),
                             cand("Boulevard of Broken Dreams (Radio Version)", "Green Day"))
        assertTrue("otra version colada sin duracion, fue $s", s < Scorer.ACCEPT)
    }

    @Test fun `sin duracion, directo NO auto-acepta`() {
        val s = Scorer.score(src("Suspicious Minds", "Elvis Presley", d = null),
                             cand("Suspicious Minds (Live)", "Elvis Presley"))
        assertTrue("directo colado sin duracion, fue $s", s < Scorer.ACCEPT)
    }

    @Test fun `sin duracion, artista equivocado sigue sin pasar`() {
        val s = Scorer.score(src("Hello", "Adele", d = null), cand("Hello", "Lionel Richie"))
        assertTrue("artista equivocado colado sin duracion, fue $s", s < Scorer.ACCEPT)
    }

    @Test fun `sin duracion, un video suelto no se beneficia del bono`() {
        val s = Scorer.score(src("Song", "Artist", d = null),
                             cand("Song", "Artist", song = false))
        assertTrue("un video no-cancion no deberia auto-aceptarse, fue $s", s < Scorer.ACCEPT)
    }
}
