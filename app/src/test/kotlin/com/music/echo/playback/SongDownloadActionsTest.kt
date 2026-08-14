package iad1tya.echo.music.playback

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Like-while-watching defers the offline download so it does not fight the live A/V mux.
 * The player download icon reads [PendingDeferredDownloads.pendingIds] — if mark/take drift,
 * the glyph freezes or ignores taps.
 */
class SongDownloadActionsTest {

    @After
    fun tearDown() {
        PendingDeferredDownloads.take("vid1")
        PendingDeferredDownloads.take("vid2")
    }

    @Test
    fun markIsVisibleAndTakeClearsIt() {
        assertFalse(PendingDeferredDownloads.contains("vid1"))
        PendingDeferredDownloads.mark("vid1", "Title", isVideoSong = true)
        assertTrue(PendingDeferredDownloads.contains("vid1"))
        assertTrue("vid1" in PendingDeferredDownloads.pendingIds.value)

        val entry = PendingDeferredDownloads.take("vid1")
        assertNotNull(entry)
        assertEquals("Title", entry!!.title)
        assertTrue(entry.isVideoSong)
        assertFalse(PendingDeferredDownloads.contains("vid1"))
        assertFalse("vid1" in PendingDeferredDownloads.pendingIds.value)
    }

    @Test
    fun takeOfUnknownIdIsNoOp() {
        assertNull(PendingDeferredDownloads.take("missing"))
    }

    @Test
    fun markOverwritesTheSameId() {
        PendingDeferredDownloads.mark("vid1", "A", isVideoSong = false)
        PendingDeferredDownloads.mark("vid1", "B", isVideoSong = true)
        val entry = PendingDeferredDownloads.take("vid1")
        assertEquals("B", entry!!.title)
        assertTrue(entry.isVideoSong)
        assertFalse("vid1" in PendingDeferredDownloads.pendingIds.value)
    }
}
