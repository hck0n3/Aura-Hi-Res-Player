package com.aura.migration.resolver

import java.text.Normalizer

/**
 * Normalizacion de titulos y artistas antes de buscar/comparar.
 *
 * REGLA CRITICA: solo se quita lo que VERIFICABLEMENTE sobra.
 * "Remix", "Live", "Acoustic", "Instrumental", "Deluxe" y los numeros
 * romanos NO son ruido: son la diferencia entre la cancion correcta y
 * otra grabacion distinta. Si los borras, el matcher miente.
 */
object TextNormalizer {

    private val NOISE: List<Regex> = listOf(
        """\(feat\.?[^)]*\)""",
        """\[feat\.?[^\]]*\]""",
        """\s*-\s*feat\.?\s.*$""",
        """\s*\bft\.\s[^,\-\(\)]*""",
        """\s*-\s*(\d{4}\s*)?remaster(ed)?(\s*\d{4})?\s*$""",
        """\s*\(\s*(\d{4}\s*)?remaster(ed)?[^)]*\)""",
        """\s*\(official\s*(music\s*)?(video|audio)\)""",
        """\s*\(lyrics?\s*video\)""",
        """\s*\(visuali[sz]er\)""",
        """\s*\(audio\)""",
        """\s*\(video\s*oficial\)""",
        """\s*\(letra\)""",
        """\s*-\s*topic\s*$""",
        """\s*\[\s*explicit\s*\]""",
        """\s*\(\s*explicit\s*\)""",
        """\s*-\s*single\s*$""",
        """\s*\(\s*single\s*version\s*\)"""
    ).map { Regex(it, RegexOption.IGNORE_CASE) }

    /**
     * Quita acentos. CRITICO para catalogo latino:
     * "Corazon Partio" vs "Corazón Partío" debe puntuar 1.0, no 0.7.
     */
    fun stripDiacritics(s: String): String =
        Normalizer.normalize(s, Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")

    fun normalize(raw: String): String {
        var s = raw.lowercase()
        NOISE.forEach { s = it.replace(s, " ") }
        s = stripDiacritics(s)
        // conserva letras y numeros de cualquier alfabeto (JP, KR, AR incluidos)
        s = s.replace(Regex("[^\\p{L}\\p{N}\\s]"), " ")
        return s.replace(Regex("\\s+"), " ").trim()
    }

    fun normalizeArtist(raw: String): String =
        normalize(
            raw.replace(Regex("""\s*(&|,|\bx\b|\bvs\.?\b|\band\b|\by\b)\s*.*$""",
                RegexOption.IGNORE_CASE), "")
        )

    /**
     * Etiquetas de version. Si el candidato tiene una que el origen no tiene
     * (o al reves), es otra grabacion y hay que penalizar duro.
     */
    val VERSION_TAGS = listOf(
        "live", "en vivo", "en directo", "remix", "acoustic", "acustico",
        "instrumental", "karaoke", "cover", "sped up", "slowed",
        "reverb", "demo", "radio edit", "extended", "unplugged", "mtv"
    )

    fun versionTagsIn(title: String): Set<String> {
        val t = stripDiacritics(title.lowercase())
        return VERSION_TAGS.filter { t.contains(stripDiacritics(it)) }.toSet()
    }
}
