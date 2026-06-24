package iad1tya.echo.music.reco

import android.content.Context
import iad1tya.echo.music.constants.OnboardingGenresKey
import iad1tya.echo.music.utils.dataStore
import kotlinx.coroutines.flow.first

/**
 * Bridges the first-run "¿Qué géneros te gustan?" step into the recommendation engine. The onboarding chips
 * are Spanish labels; iTunes (which the taste engine learns genres from) uses English primaryGenreName
 * values — so we map them, then [AffinityEngine] seeds those genres as baseline affinity. Previously the
 * onboarding selection was saved but never read (dead write).
 */
object OnboardingGenres {
    private val MAP = mapOf(
        "Pop" to "Pop",
        "Rock" to "Rock",
        "Hip-Hop / Rap" to "Hip-Hop/Rap",
        "R&B" to "R&B/Soul",
        "Electrónica" to "Electronic",
        "Reggaetón" to "Latin",
        "Latina" to "Latin",
        "Trap" to "Hip-Hop/Rap",
        "K-Pop" to "K-Pop",
        "Indie" to "Alternative",
        "Jazz" to "Jazz",
        "Clásica" to "Classical",
        "Country" to "Country",
        "Metal" to "Metal",
        "Cristiana / Gospel" to "Christian & Gospel",
        "Salsa" to "Latin",
        "Bachata" to "Latin",
        "Banda / Regional" to "Latin",
        "Blues" to "Blues",
        "Folk" to "Singer/Songwriter",
        "Punk" to "Alternative",
        "Soul" to "R&B/Soul",
        "Funk" to "R&B/Soul",
        "Lo-fi" to "Electronic",
    )

    /** The user's onboarding genre picks, mapped to iTunes genre names (empty if none / not chosen). */
    suspend fun itunesGenres(context: Context): List<String> = runCatching {
        val raw = context.dataStore.data.first()[OnboardingGenresKey].orEmpty()
        raw.split(",").map { it.trim() }.mapNotNull { MAP[it] }.distinct()
    }.getOrDefault(emptyList())
}
