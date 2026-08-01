package com.aura.migration.resolver

import com.aura.migration.model.SourceTrack
import com.aura.migration.model.YtmCandidate
import kotlin.math.abs

/**
 * Puntua cuanto se parece un candidato de YouTube Music a la cancion origen.
 *
 * Pesos calibrados sobre la idea de que la DURACION es el discriminante mas
 * fuerte que existe: dos grabaciones distintas de la misma cancion casi nunca
 * duran lo mismo, y dos titulos parecidos con la misma duracion casi siempre
 * son la misma grabacion.
 *
 * Ajusta ACCEPT / REVIEW contra el conjunto dorado, NUNCA a ojo.
 */
object Scorer {

    /** >= ACCEPT y con margen sobre el segundo -> insertar automaticamente. */
    const val ACCEPT = 75f
    /** >= REVIEW -> mostrar candidatos al usuario. */
    const val REVIEW = 50f
    /** Margen minimo sobre el segundo candidato para confiar sin preguntar. */
    const val MARGIN = 10f
    /**
     * Compensa la ausencia de duracion. La duracion vale hasta 45 (el discriminante mas fuerte),
     * asi que una fuente que NO la trae topa en ~70 y jamas auto-acepta: todo cae en revision para
     * siempre. Cuando la fuente no tiene duracion Y artista+titulo casan casi perfecto con una
     * cancion real, ese acuerdo ya es fiable por si solo.
     */
    const val NO_DURATION_BONUS = 15f

    fun score(src: SourceTrack, cand: YtmCandidate): Float {
        var s = 0f

        s += durationScore(src.durationMs, cand.durationMs)
        val artistSim = Similarity.best(
            TextNormalizer.normalizeArtist(src.primaryArtist),
            TextNormalizer.normalizeArtist(cand.primaryArtist)
        )
        s += 30f * artistSim
        // ARTIST GATE (calibrated against ScorerTest, which shipped RED): with title+duration+isSong
        // alone (45+20+15 = 80 > ACCEPT) a same-titled song by a DIFFERENT artist auto-accepted —
        // "Hello" (Adele) matched Lionel Richie. A low artist similarity is disqualifying on its own,
        // not just worth fewer points: the module's #1 rule is never inserting a wrong match silently.
        // Threshold 0.5, not lower: Jaro-Winkler INFLATES unrelated pairs (best("adele","lionel
        // richie") = 0.351), while a correct artist after normalizeArtist scores >= ~0.8 and the
        // "The Weeknd"/"Weeknd" token case lands exactly AT 0.5 (strict < keeps it unpenalized).
        if (artistSim < 0.5f) s -= 35f
        val titleSim = Similarity.best(
            TextNormalizer.normalize(src.title),
            TextNormalizer.normalize(cand.title)
        )
        s += 20f * titleSim
        s += albumScore(src.album, cand.album)
        s += if (cand.isSong) 15f else -15f
        s += explicitScore(src.explicit, cand.explicit)
        s += versionPenalty(src.title, cand.title)

        // Only when the SOURCE lacks a duration (null): a genuine exact match on a real Song still
        // clears ACCEPT instead of being trapped at ~70. No effect when the source HAS a duration.
        //
        // The title test is EXACT EQUALITY of the normalized titles, NOT a similarity threshold. A 0.9
        // Jaro-Winkler cutoff was provably unsafe: "Boulevard of Broken Dreams" vs "… (Radio Version)"
        // scores 0.93, and versionPenalty does NOT fire because VERSION_TAGS lists "radio edit" but not
        // "radio version" — so the bonus lifted 63.6 -> 78.6 and SILENTLY auto-inserted a different
        // master. Duration is exactly the signal that would have killed it, and it's the one we lack
        // here, so the fallback must be strict: same artist, byte-identical normalized title, real Song.
        // Any extra qualifier ("radio version", "live", "acoustic"…) survives normalize() as trailing
        // words, so the titles differ and the bonus is withheld -> the pair stays in REVIEW for the user.
        val titlesIdentical = TextNormalizer.normalize(src.title) == TextNormalizer.normalize(cand.title)
        if (src.durationMs == null && cand.isSong && artistSim >= 0.85f && titlesIdentical) {
            s += NO_DURATION_BONUS
        }

        return s
    }

    // ---- componentes ----

    private fun durationScore(a: Long?, b: Long?): Float {
        if (a == null || b == null || a <= 0 || b <= 0) return 0f
        return when (abs(a - b)) {
            in 0..2_000L      ->  45f
            in 2_001..4_000L  ->  35f
            in 4_001..7_000L  ->  15f
            in 7_001..15_000L -> -20f
            else              -> -50f   // casi seguro otra version
        }
    }

    private fun albumScore(a: String?, b: String?): Float {
        if (a.isNullOrBlank() || b.isNullOrBlank()) return 0f
        val sim = Similarity.best(
            TextNormalizer.normalize(a), TextNormalizer.normalize(b)
        )
        return if (sim > 0.85f) 10f else 0f
    }

    private fun explicitScore(a: Boolean?, b: Boolean?): Float {
        if (a == null || b == null) return 0f
        return if (a == b) 5f else -10f
    }

    /**
     * El fallo mas comun de todo importador: traer el remix, el directo o el
     * "sped up" en vez de la version de estudio. Se ataca comparando etiquetas.
     */
    private fun versionPenalty(srcTitle: String, candTitle: String): Float {
        val srcTags = TextNormalizer.versionTagsIn(srcTitle)
        val candTags = TextNormalizer.versionTagsIn(candTitle)
        if (srcTags == candTags) return 0f
        var p = 0f
        // el candidato trae una etiqueta que el origen no pide -> muy malo.
        // -40, no -25 (calibrado contra ScorerTest, que llego EN ROJO): un remix/directo con la MISMA
        // duracion suma ~109 (45 dur + 30 artista + ~19 titulo + 15 isSong); con -25 quedaba en ~84,
        // por encima de ACCEPT=75, y COLABA. Con -40 cae a ~69: el fallo #1 de todo importador,
        // cerrado con margen. La duracion identica ya no blinda a una version distinta.
        p -= 40f * (candTags - srcTags).size
        // el origen pide una etiqueta que el candidato no tiene -> malo
        p -= 25f * (srcTags - candTags).size
        return p
    }
}
