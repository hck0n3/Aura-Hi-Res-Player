package com.aura.migration.resolver

import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/**
 * Jaro-Winkler. Elegido sobre Levenshtein porque premia coincidencias
 * al principio de la cadena, que es donde vive la senal en titulos y
 * nombres de artista.
 *
 * Devuelve 0.0 (nada que ver) .. 1.0 (identicos).
 */
object Similarity {

    fun jaroWinkler(a: String, b: String, prefixScale: Double = 0.1): Float {
        val jaro = jaro(a, b)
        if (jaro < 0.7) return jaro.toFloat()   // sin bonus si la base es mala
        var prefix = 0
        val maxPrefix = min(4, min(a.length, b.length))
        while (prefix < maxPrefix && a[prefix] == b[prefix]) prefix++
        return (jaro + prefix * prefixScale * (1 - jaro)).toFloat()
    }

    private fun jaro(a: String, b: String): Double {
        if (a == b) return 1.0
        if (a.isEmpty() || b.isEmpty()) return 0.0

        val window = max(0, floor(max(a.length, b.length) / 2.0).toInt() - 1)
        val aFlags = BooleanArray(a.length)
        val bFlags = BooleanArray(b.length)
        var matches = 0

        for (i in a.indices) {
            val lo = max(0, i - window)
            val hi = min(i + window + 1, b.length)
            for (j in lo until hi) {
                if (!bFlags[j] && a[i] == b[j]) {
                    aFlags[i] = true; bFlags[j] = true; matches++; break
                }
            }
        }
        if (matches == 0) return 0.0

        var transpositions = 0
        var k = 0
        for (i in a.indices) {
            if (!aFlags[i]) continue
            while (!bFlags[k]) k++
            if (a[i] != b[k]) transpositions++
            k++
        }
        val t = transpositions / 2.0
        val m = matches.toDouble()
        return (m / a.length + m / b.length + (m - t) / m) / 3.0
    }

    /**
     * Similitud tolerante al orden de palabras.
     * "Bad Bunny Tito" vs "Tito Bad Bunny" -> alta.
     */
    fun tokenSetSimilarity(a: String, b: String): Float {
        val ta = a.split(" ").filter { it.isNotBlank() }.toSet()
        val tb = b.split(" ").filter { it.isNotBlank() }.toSet()
        if (ta.isEmpty() || tb.isEmpty()) return 0f
        val inter = ta.intersect(tb).size.toFloat()
        return inter / max(ta.size, tb.size).toFloat()
    }

    /** Combina ambas: coge la mejor. Robusto ante reordenamientos y erratas. */
    fun best(a: String, b: String): Float =
        max(jaroWinkler(a, b), tokenSetSimilarity(a, b))
}
