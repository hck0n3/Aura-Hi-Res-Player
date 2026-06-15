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
)

object ReleaseRadarMatching {
    private fun norm(s: String) = s.lowercase().replace(Regex("[^a-z0-9]"), "")

    fun dedupeKey(c: ReleaseCandidate): String = norm(c.artist) + "|" + norm(c.title)

    /** Keep one per key; prefer source "yt" (directly playable). */
    fun dedupe(items: List<ReleaseCandidate>): List<ReleaseCandidate> =
        items.groupBy { dedupeKey(it) }
            .values
            .map { group -> group.firstOrNull { it.source == "yt" } ?: group.first() }

    fun isWithinWindow(date: LocalDate, ref: LocalDate, days: Long): Boolean {
        val diff = abs(date.toEpochDay() - ref.toEpochDay())
        return diff <= days
    }
}
