package iad1tya.echo.music.utils

/**
 * Builds direct YouTube Music links for sharing. IDs are interpolated as-is so a
 * null ID behaves exactly like the previous inline string templates.
 */
object ShareLinks {
    private const val BASE = "https://music.youtube.com"

    fun song(videoId: String?) = "$BASE/watch?v=$videoId"

    fun playlist(playlistId: String?) = "$BASE/playlist?list=$playlistId"

    fun channel(channelId: String?) = "$BASE/channel/$channelId"
}
