package iad1tya.echo.music.models

import iad1tya.echo.music.db.entities.Song

/**
 * A "Tu mix de [Género]" Home shelf: songs from the user's own library/history that belong to one of
 * their favourite genres (derived on-device via GenreCache), ranked by the taste model. Fully local.
 */
data class GenreMix(
    val genre: String,
    val songs: List<Song>,
)
