package iad1tya.echo.music.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AiPlaylistPromptTest {

    @Test fun buildsSystemThenUserMessage() {
        val messages = AiPlaylistPrompt.buildMessages("rock para correr de noche", 20)
        assertEquals(2, messages.size)
        assertEquals("system", messages[0].role)
        assertEquals("user", messages[1].role)
    }

    @Test fun userMessageContainsPromptAndCount() {
        val messages = AiPlaylistPrompt.buildMessages("rock para correr de noche", 20)
        assertTrue(messages[1].content.contains("rock para correr de noche"))
        assertTrue(messages[0].content.contains("20") || messages[1].content.contains("20"))
    }

    @Test fun systemMessageAsksForJsonWithTheRequestedCount() {
        val messages = AiPlaylistPrompt.buildMessages("boleros tristes", 30)
        assertTrue(messages[0].content.contains("JSON"))
        assertTrue(messages[0].content.contains("30"))
    }

    @Test fun systemMessageAsksForAYearAndACounterExample() {
        val system = AiPlaylistPrompt.buildMessages("rock", 10)[0].content
        assertTrue(system.contains("\"year\": number"))
        assertTrue(system.contains("PROHIBIDO") || system.contains("Bad Bunny"))
    }

    @Test fun systemMessageForbidsImprovisation() {
        val system = AiPlaylistPrompt.buildMessages("solo Bad Bunny", 10)[0].content
        assertTrue(system.contains("PROHIBIDO") || system.contains("improvis"))
        assertTrue(system.contains("EXACTO") || system.contains("OBLIGATORIO"))
    }

    @Test fun soloArtistPromptLocksUserMessage() {
        val user = AiPlaylistPrompt.buildMessages("solo Bad Bunny", 12)[1].content
        assertTrue(user.contains("RESTRICCIÓN BLOQUEANTE"))
        assertTrue(user.contains("Bad Bunny"))
        assertTrue(user.contains("Cero improvisación") || user.contains("improvis"))
    }

    // --- modify -----------------------------------------------------------------------------

    @Test fun modifyNumbersTheTracksOneBasedAndCarriesTheInstruction() {
        val messages = AiPlaylistPrompt.buildModifyMessages(
            listOf(TrackQuery("Uno", "A"), TrackQuery("Dos", "B")),
            "quita las lentas",
        )
        assertEquals(2, messages.size)
        assertEquals("system", messages[0].role)
        val user = messages[1].content
        assertTrue(user.contains("1. \"Uno\" — A"))
        assertTrue(user.contains("2. \"Dos\" — B"))
        assertTrue(user.contains("quita las lentas"))
    }

    @Test fun modifyAsksForRemoveAndAdditionsSchema() {
        val system = AiPlaylistPrompt.buildModifyMessages(listOf(TrackQuery("Uno", "A")), "x")[0].content
        assertTrue(system.contains("\"remove\""))
        assertTrue(system.contains("\"additions\""))
    }

    /** The AI must only ever see positions — never a database id. */
    @Test fun modifyCapsTheSerializedPlaylist() {
        val many = (1..(AiPlaylistPrompt.MAX_MODIFY_TRACKS + 40)).map { TrackQuery("T$it", "A$it") }
        val user = AiPlaylistPrompt.buildModifyMessages(many, "quita las lentas")[1].content
        assertTrue(user.contains("${AiPlaylistPrompt.MAX_MODIFY_TRACKS}. \"T${AiPlaylistPrompt.MAX_MODIFY_TRACKS}\""))
        assertTrue(!user.contains("${AiPlaylistPrompt.MAX_MODIFY_TRACKS + 1}. \"T${AiPlaylistPrompt.MAX_MODIFY_TRACKS + 1}\""))
    }
}
