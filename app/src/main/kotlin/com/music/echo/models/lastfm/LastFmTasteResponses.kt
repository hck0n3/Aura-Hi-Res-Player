package iad1tya.echo.music.models.lastfm

import kotlinx.serialization.Serializable

/**
 * Minimal models for the PUBLIC, UNSIGNED Last.fm read endpoints used by the taste importer
 * (`user.getTopArtists` / `user.getLovedTracks`). Only the fields the importer actually reads are declared;
 * everything else is dropped via the client's `ignoreUnknownKeys`. NOTE: `playcount` arrives as a JSON STRING.
 */
@Serializable
data class TopArtistsResponse(
    val topartists: TopArtists? = null,
) {
    @Serializable
    data class TopArtists(
        val artist: List<Artist> = emptyList(),
    )

    @Serializable
    data class Artist(
        val name: String = "",
        // Last.fm returns playcount as a JSON string (e.g. "123") — parsed with toIntOrNull upstream.
        val playcount: String = "0",
    )
}

@Serializable
data class LovedTracksResponse(
    val lovedtracks: LovedTracks? = null,
) {
    @Serializable
    data class LovedTracks(
        val track: List<Track> = emptyList(),
    )

    @Serializable
    data class Track(
        val name: String = "",
        // The loved track's artist is a nested object; we only need its name.
        val artist: Artist? = null,
    ) {
        @Serializable
        data class Artist(
            val name: String = "",
        )
    }
}
