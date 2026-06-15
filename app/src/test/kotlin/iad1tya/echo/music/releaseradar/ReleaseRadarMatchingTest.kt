package iad1tya.echo.music.releaseradar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test
import java.time.LocalDate

class ReleaseRadarMatchingTest {
    private fun item(title: String, artist: String, date: LocalDate, source: String = "yt") =
        ReleaseCandidate(title = title, artist = artist, date = date, source = source, artworkUri = null, playId = title)

    @Test fun dedupeKeyIgnoresCaseAndPunctuation() {
        assertEquals(
            ReleaseRadarMatching.dedupeKey(item("Hello!", "Adele", LocalDate.of(2026, 6, 12))),
            ReleaseRadarMatching.dedupeKey(item("hello", "ADELE", LocalDate.of(2026, 6, 12)))
        )
    }
    @Test fun dedupeKeepsOnePerKeyPreferringYt() {
        val spotify = item("Song", "X", LocalDate.of(2026, 6, 12), source = "spotify")
        val yt = item("song", "x", LocalDate.of(2026, 6, 12), source = "yt")
        val result = ReleaseRadarMatching.dedupe(listOf(spotify, yt))
        assertEquals(1, result.size)
        assertEquals("yt", result.first().source)
    }
    @Test fun windowFiltersOld() {
        val ref = LocalDate.of(2026, 6, 12)
        assertTrue(ReleaseRadarMatching.isWithinWindow(LocalDate.of(2026, 6, 10), ref, days = 7))
        assertFalse(ReleaseRadarMatching.isWithinWindow(LocalDate.of(2026, 1, 1), ref, days = 7))
    }
}
