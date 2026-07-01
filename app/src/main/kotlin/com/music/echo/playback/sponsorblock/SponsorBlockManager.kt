package iad1tya.echo.music.playback.sponsorblock

/**
 * Holds the SponsorBlock segments for the CURRENTLY playing YouTube track and answers "should I skip from
 * here?" queries. Pure state + logic (no coroutines / no player) so it's trivially testable; MusicService
 * drives the network fetch and the actual seek. All fields are @Volatile because [begin]/[accept] run on the
 * player (main) thread while the fetch completes on an IO thread.
 */
class SponsorBlockManager {

    @Volatile private var videoId: String? = null
    @Volatile private var segments: List<SponsorSegment> = emptyList()
    // The last skip target we handed out, so we don't re-issue the SAME seek every tick while the seek is
    // still settling (a slow/buffering seek can leave currentPosition inside the segment for a tick or two).
    @Volatile private var lastSkipTarget: Long = -1L

    /** Start tracking a new track; clears stale segments. Returns the id iff it's a fetchable YouTube id. */
    fun begin(mediaId: String?): String? {
        segments = emptyList()
        lastSkipTarget = -1L
        val id = mediaId?.takeIf { isYouTubeVideoId(it) }
        videoId = id
        return id
    }

    /** Store fetched segments, but only if the track hasn't changed since [begin] (ignore stale responses). */
    fun accept(forId: String, fetched: List<SponsorSegment>) {
        if (videoId == forId) segments = fetched
    }

    fun clear() {
        videoId = null
        segments = emptyList()
        lastSkipTarget = -1L
    }

    /**
     * If [positionMs] is inside a skippable segment, return the position (ms) to seek to (the segment end),
     * else null. A small end-margin avoids re-triggering on the boundary after the seek lands. We also dedup
     * the same target so a not-yet-landed seek isn't re-issued every tick (which would stutter); once the
     * playhead clears all segments the dedup resets so re-entering a segment (e.g. after a manual rewind) skips.
     */
    fun skipTargetFor(positionMs: Long): Long? {
        val segs = segments
        if (segs.isEmpty()) return null
        val seg = segs.firstOrNull { positionMs >= it.startMs && positionMs < it.endMs - END_MARGIN_MS }
        if (seg == null) {
            lastSkipTarget = -1L // clear of all segments → allow a fresh skip if we re-enter one
            return null
        }
        if (seg.endMs == lastSkipTarget) return null // already issued this skip; waiting for it to land
        lastSkipTarget = seg.endMs
        return seg.endMs
    }

    companion object {
        private const val END_MARGIN_MS = 400L

        /** YouTube video ids are exactly 11 chars of [A-Za-z0-9_-]. Local songs use long content:// URIs. */
        fun isYouTubeVideoId(id: String): Boolean =
            id.length == 11 && id.all { it in 'A'..'Z' || it in 'a'..'z' || it in '0'..'9' || it == '_' || it == '-' }
    }
}
