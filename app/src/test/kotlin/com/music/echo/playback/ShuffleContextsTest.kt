package iad1tya.echo.music.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ShuffleContextsTest {

    @Test
    fun followedPlaylistSharesOnlineBucketWhetherBrowseIdHasVlPrefix() {
        assertEquals("OL:PLabc", ShuffleContexts.forPlaylist(false, "LP123", "VLPLabc"))
        assertEquals("OL:PLabc", ShuffleContexts.forPlaylist(false, "LP123", "PLabc"))
        assertEquals(
            ShuffleContexts.onlinePlaylist("VLPLabc"),
            ShuffleContexts.forPlaylist(false, "LP999", "PLabc"),
        )
    }

    @Test
    fun ownedPlaylistKeepsLocalBucketEvenWithBrowseId() {
        assertEquals("PL:LP123", ShuffleContexts.forPlaylist(true, "LP123", "VLPLxyz"))
        assertEquals("PL:LP123", ShuffleContexts.forPlaylist(true, "LP123", null))
    }

    @Test
    fun continueSeedUnionsShuffleMemoryAndLifetimePlays() {
        val seed = ShuffleContexts.seedPlayedIds(
            resetMemory = false,
            songIds = listOf("a", "b", "c"),
            shufflePlayed = setOf("a"),
            playTimeMs = { id -> if (id == "c") 12_000L else 0L },
        )
        assertEquals(setOf("a", "c"), seed)
    }

    @Test
    fun startOverDoesNotReimportHistory() {
        val seed = ShuffleContexts.seedPlayedIds(
            resetMemory = true,
            songIds = listOf("a", "b"),
            shufflePlayed = setOf("a"),
            playTimeMs = { 99_000L },
        )
        assertTrue(seed.isEmpty())
    }
}
