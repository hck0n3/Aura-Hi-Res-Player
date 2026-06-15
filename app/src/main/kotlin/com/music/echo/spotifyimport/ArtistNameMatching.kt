/*
 * EchoMusic (2026)
 * © Chartreux Westia — github.com/koiverse
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package iad1tya.echo.music.spotifyimport

import java.text.Normalizer

/**
 * Lightweight, dependency-free artist-name comparison used when matching a
 * Spotify followed-artist to a YouTube Music search result. Kept pure (no
 * Android / network types) so it can be unit-tested on the JVM.
 */
object ArtistNameMatching {
    fun normalize(raw: String): String =
        Normalizer.normalize(raw, Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
            .lowercase()
            .replace(Regex("[^a-z0-9 ]"), "")
            .replace(Regex("\\s+"), " ")
            .trim()

    fun isLikelyMatch(a: String, b: String): Boolean {
        val na = normalize(a)
        val nb = normalize(b)
        if (na.isEmpty() || nb.isEmpty()) return false
        return na == nb || na.contains(nb) || nb.contains(na)
    }
}
