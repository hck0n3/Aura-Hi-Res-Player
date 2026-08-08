package iad1tya.echo.music.lyrics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the timed-lyrics detection + LRC parse formats that previously produced
 * "synced but empty / desynced" panels for real provider payloads.
 */
class LyricsParseSyncTest {

    @Test
    fun timedHeadRejectsSectionHeaders() {
        assertFalse(LyricsUtils.isTimedLyrics("[Verse 1]\nHello world"))
        assertFalse(LyricsUtils.isTimedLyrics("[Chorus]\nSing along"))
        assertFalse(LyricsUtils.isTimedLyrics("Hello\n[00:12.00] never the head"))
    }

    @Test
    fun timedHeadAcceptsCommonLrcPrefixes() {
        assertTrue(LyricsUtils.isTimedLyrics("[00:13.42] Yeah"))
        assertTrue(LyricsUtils.isTimedLyrics("[0:13.42] Yeah"))
        assertTrue(LyricsUtils.isTimedLyrics("[00:13] Yeah"))
        assertTrue(LyricsUtils.isTimedLyrics("\n[3:05.12] Hello"))
    }

    @Test
    fun parseAcceptsSingleDigitMinutesAndMissingFraction() {
        val lines = LyricsUtils.parseLyrics(
            """
            [0:13.42] Yeah
            [00:26] I've been tryna call
            [1:05.4] Maybe
            [03:05.123] End
            """.trimIndent(),
        )
        assertEquals(4, lines.size)
        assertEquals(13_420L, lines[0].time)
        assertEquals(26_000L, lines[1].time)
        assertEquals(65_400L, lines[2].time)
        assertEquals(185_123L, lines[3].time)
        assertEquals("Yeah", lines[0].text)
        assertEquals("I've been tryna call", lines[1].text)
    }

    @Test
    fun sectionHeaderPlaintextIsNotParsedAsEmptySynced() {
        // Previously: startsWith("[") → parse → empty (+ HEAD) → blank "synced" UI.
        assertFalse(LyricsUtils.isTimedLyrics("[Verse 1]\nHello\nWorld"))
        assertTrue(LyricsUtils.parseLyrics("[Verse 1]\nHello\nWorld").isEmpty())
    }

    @Test
    fun findCurrentLineIndexAppliesLookAhead() {
        val lines = listOf(
            LyricsEntry(0L, ""),
            LyricsEntry(1000L, "one"),
            LyricsEntry(2000L, "two"),
            LyricsEntry(3000L, "three"),
        )
        // Lead = 300 ms: switch to "two" once position+lead > 2000 (i.e. position > 1700).
        assertEquals(1, LyricsUtils.findCurrentLineIndex(lines, 1700L)) // still "one"
        assertEquals(2, LyricsUtils.findCurrentLineIndex(lines, 1701L)) // "two"
        assertEquals(2, LyricsUtils.findCurrentLineIndex(lines, 1750L))
    }

    @Test
    fun fractionParsing() {
        assertEquals(0L, LyricsUtils.parseLrcFractionToMs(""))
        assertEquals(400L, LyricsUtils.parseLrcFractionToMs("4"))
        assertEquals(420L, LyricsUtils.parseLrcFractionToMs("42"))
        assertEquals(420L, LyricsUtils.parseLrcFractionToMs("420"))
    }
}
