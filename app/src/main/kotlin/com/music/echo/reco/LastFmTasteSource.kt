package iad1tya.echo.music.reco

import android.content.Context
import androidx.datastore.preferences.core.edit
import iad1tya.echo.music.constants.LastFmTasteCacheKey
import iad1tya.echo.music.constants.LastFmTasteFetchedAtKey
import iad1tya.echo.music.utils.dataStore
import iad1tya.echo.music.utils.get
import iad1tya.echo.music.utils.lastfm.LastFM
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/**
 * SECONDARY, opt-in taste source: turns the user's Last.fm listening history (top artists + loved tracks) into
 * a small `lowercased-artist-name -> weight` map that the on-device [AffinityEngine] folds into per-NAME
 * affinity ONLY (never byId), capped, so it seeds cold-start / cross-app taste WITHOUT ever outranking real
 * local plays. Fetched by the daily [LastFmTasteWorker] and cached in DataStore — the recommendation path only
 * ever reads [cached] (no Last.fm network on buildProfile / orderedByTaste). Everything is best-effort: any
 * failure leaves the previous cache untouched and never throws.
 */
object LastFmTasteSource {
    /** Weight of the user's single most-played Last.fm artist; the rest scale down by relative playcount. */
    private const val LASTFM_ARTIST_SEED = 0.5

    /** Weight added per loved track for that track's artist (accumulates; capped later by the engine). */
    private const val LASTFM_LOVED_SEED = 0.4

    private val json = Json { ignoreUnknownKeys = true }
    private val mapSerializer = MapSerializer(String.serializer(), Double.serializer())

    /**
     * Fetches the user's Last.fm top artists + loved tracks, builds the weighted name map and persists it
     * (with a fetch timestamp) to DataStore. A fetch that returns nothing leaves any prior cache intact, so a
     * transient network failure never wipes a good cache. Best-effort — never throws.
     */
    suspend fun fetchAndCache(context: Context, username: String) {
        if (username.isBlank()) return
        val top = LastFM.getTopArtists(username)
        val loved = LastFM.getLovedTracks(username)
        if (top.isEmpty() && loved.isEmpty()) return // transient failure — keep the previous cache

        val weights = HashMap<String, Double>()
        // Scale top artists by their share of the single biggest playcount, so the most-played artist lands
        // near LASTFM_ARTIST_SEED and the long tail scales down proportionally.
        val maxPlaycount = top.maxOfOrNull { it.second }?.takeIf { it > 0 } ?: 1
        top.forEach { (name, playcount) ->
            val key = name.trim().lowercase()
            if (key.isNotBlank()) {
                weights.merge(key, LASTFM_ARTIST_SEED * (playcount.toDouble() / maxPlaycount), Double::plus)
            }
        }
        // Each loved track stacks a flat seed onto its artist; the engine caps the per-artist total later.
        loved.forEach { name ->
            val key = name.trim().lowercase()
            if (key.isNotBlank()) weights.merge(key, LASTFM_LOVED_SEED, Double::plus)
        }
        if (weights.isEmpty()) return

        val blob = runCatching { json.encodeToString(mapSerializer, weights) }.getOrNull() ?: return
        runCatching {
            context.dataStore.edit { prefs ->
                prefs[LastFmTasteCacheKey] = blob
                prefs[LastFmTasteFetchedAtKey] = System.currentTimeMillis()
            }
        }
    }

    /** The cached Last.fm taste map (lowercased artist name -> weight), or empty if none / it fails to parse. */
    suspend fun cached(context: Context): Map<String, Double> {
        val blob = context.dataStore.get(LastFmTasteCacheKey, "")
        if (blob.isBlank()) return emptyMap()
        return runCatching { json.decodeFromString(mapSerializer, blob) }.getOrDefault(emptyMap())
    }
}
