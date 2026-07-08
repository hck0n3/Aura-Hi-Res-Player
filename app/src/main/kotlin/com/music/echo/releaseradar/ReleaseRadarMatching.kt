package iad1tya.echo.music.releaseradar

import java.time.LocalDate
import kotlin.math.abs

data class ReleaseCandidate(
    val title: String,
    val artist: String,
    val date: LocalDate,
    val source: String,      // "yt" | "spotify"
    val artworkUri: String?,
    val playId: String,      // videoId/browseId for playback
    val followed: Boolean = false, // artist the user FOLLOWS (priority) vs just listens to
)

object ReleaseRadarMatching {
    private fun norm(s: String) = s.lowercase().replace(Regex("[^a-z0-9]"), "")

    fun dedupeKey(c: ReleaseCandidate): String = norm(c.artist) + "|" + norm(c.title)

    /** Keep one per key; prefer source "yt" (directly playable). */
    fun dedupe(items: List<ReleaseCandidate>): List<ReleaseCandidate> =
        items.groupBy { dedupeKey(it) }
            .values
            .map { group -> group.firstOrNull { it.source == "yt" } ?: group.first() }

    /**
     * Spotify's HARD cap of one track per artist: keep only the most recent release per artist
     * (a followed release wins ties so the priority artist survives the cap).
     */
    fun onePerArtist(items: List<ReleaseCandidate>): List<ReleaseCandidate> =
        items.groupBy { norm(it.artist) }
            .values
            .map { group ->
                group.maxWithOrNull(
                    compareBy<ReleaseCandidate> { it.date }.thenBy { it.followed },
                )!!
            }

    /** Order followed-artist releases first, then by release date descending, and cap the list. */
    fun rankForRadar(items: List<ReleaseCandidate>, cap: Int): List<ReleaseCandidate> =
        items.sortedWith(
            compareByDescending<ReleaseCandidate> { it.followed }.thenByDescending { it.date },
        ).take(cap)

    fun isWithinWindow(date: LocalDate, ref: LocalDate, days: Long): Boolean {
        val diff = abs(date.toEpochDay() - ref.toEpochDay())
        return diff <= days
    }
}
