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
        assertTrue(messages[1].content.contains("20"))
    }

    @Test fun systemMessageAsksForJsonWithTheRequestedCount() {
        val messages = AiPlaylistPrompt.buildMessages("boleros tristes", 30)
        assertTrue(messages[0].content.contains("JSON"))
        assertTrue(messages[0].content.contains("30"))
    }
}
