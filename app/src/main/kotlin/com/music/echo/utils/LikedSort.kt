package iad1tya.echo.music.utils

import iad1tya.echo.music.db.entities.Song

/**
 * Stable "liked first" display sort: liked songs bubble to the top while the relative order
 * within the liked group and within the not-liked group is preserved (sortedBy is stable).
 *
 * Apply this as the FINAL display step so the on-screen order and the play queue built from the
 * same list stay aligned (tapping a row plays the right song after the re-sort).
 */
fun List<Song>.likedFirst(): List<Song> = sortedBy { if (it.song.liked) 0 else 1 }
