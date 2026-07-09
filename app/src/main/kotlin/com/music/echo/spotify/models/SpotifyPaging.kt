/*
 * EchoMusic (2026)
 * © Chartreux Westia — github.com/koiverse
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package iad1tya.echo.music.spotify.models

import kotlinx.serialization.Serializable

@Serializable
data class SpotifyPaging<T>(
    val items: List<T> = emptyList(),
    val total: Int = 0,
    val limit: Int = 20,
    val offset: Int = 0,
    val next: String? = null,
    val previous: String? = null,
    val href: String? = null,
    // RAW number of items the API returned for this page, BEFORE any client-side
    // filtering that shrinks [items] (e.g. myPlaylists drops non-playlist
    // pseudo-items). Callers that paginate must advance the offset and terminate
    // on this, never items.size, or a filtered page looks "short" and truncates.
    // Defaults to 0 for callers that don't filter and don't need it.
    val rawCount: Int = 0,
)
