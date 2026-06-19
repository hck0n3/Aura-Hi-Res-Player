package iad1tya.echo.music.podcast

import iad1tya.echo.music.models.MediaMetadata

/** A podcast show found via the Apple/iTunes directory. [feedUrl] is the public RSS feed. */
data class PodcastShow(
    val id: String,
    val title: String,
    val author: String,
    val artworkUrl: String?,
    val feedUrl: String,
)

/** A single episode. [audioUrl] is a direct, public audio stream (usually MP3) that ExoPlayer can
 *  play straight away — no YouTube/Spotify needed. */
data class PodcastEpisode(
    val id: String,
    val title: String,
    val audioUrl: String,
    val artworkUrl: String?,
    val showTitle: String,
    val author: String,
    val durationSec: Int?,
)

/** Build player metadata whose id is the direct audio URL, so MusicService plays it straight. */
fun PodcastEpisode.toMediaMetadata() = MediaMetadata(
    id = audioUrl,
    title = title,
    artists = listOf(MediaMetadata.Artist(id = null, name = showTitle)),
    duration = durationSec ?: -1,
    thumbnailUrl = artworkUrl,
)
