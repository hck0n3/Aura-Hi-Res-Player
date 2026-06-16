package iad1tya.echo.music.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AiPlaylistParserTest {

    @Test fun parsesCleanJson() {
        val spec = AiPlaylistParser.parse(
            """{"name":"Noche","tracks":[{"title":"A","artist":"X"},{"title":"B","artist":"Y"}]}""",
            expectedCount = 20,
        ).getOrThrow()
        assertEquals("Noche", spec.name)
        assertEquals(2, spec.tracks.size)
        assertEquals("A", spec.tracks[0].title)
        assertEquals("X", spec.tracks[0].artist)
    }

    @Test fun parsesJsonInsideMarkdownFence() {
        val content = "```json\n{\"name\":\"P\",\"tracks\":[{\"title\":\"A\",\"artist\":\"X\"}]}\n```"
        val spec = AiPlaylistParser.parse(content, 20).getOrThrow()
        assertEquals("P", spec.name)
        assertEquals(1, spec.tracks.size)
    }

    @Test fun parsesJsonSurroundedByProse() {
        val content =
            "Claro, aquí tienes:\n{\"name\":\"P\",\"tracks\":[{\"title\":\"A\",\"artist\":\"X\"}]}\n¡Disfruta!"
        val spec = AiPlaylistParser.parse(content, 20).getOrThrow()
        assertEquals(1, spec.tracks.size)
        assertEquals("A", spec.tracks[0].title)
    }

    @Test fun missingNameYieldsBlankName() {
        val spec = AiPlaylistParser.parse("""{"tracks":[{"title":"A","artist":"X"}]}""", 20).getOrThrow()
        assertEquals("", spec.name)
    }

    @Test fun missingArtistIsAllowed() {
        val spec = AiPlaylistParser.parse("""{"tracks":[{"title":"A"}]}""", 20).getOrThrow()
        assertEquals("A", spec.tracks[0].title)
        assertEquals("", spec.tracks[0].artist)
    }

    @Test fun trimsToExpectedCountWhenTooMany() {
        val tracks = (1..30).joinToString(",") { """{"title":"T$it","artist":"A$it"}""" }
        val spec = AiPlaylistParser.parse("""{"name":"P","tracks":[$tracks]}""", 20).getOrThrow()
        assertEquals(20, spec.tracks.size)
    }

    @Test fun acceptsFewerThanExpected() {
        val spec = AiPlaylistParser.parse(
            """{"tracks":[{"title":"A","artist":"X"},{"title":"B","artist":"Y"}]}""",
            20,
        ).getOrThrow()
        assertEquals(2, spec.tracks.size)
    }

    @Test fun dedupesByTitleArtistCaseInsensitive() {
        val spec = AiPlaylistParser.parse(
            """{"tracks":[{"title":"Song","artist":"X"},{"title":"song","artist":"x"},{"title":"B","artist":"Y"}]}""",
            20,
        ).getOrThrow()
        assertEquals(2, spec.tracks.size)
    }

    @Test fun discardsTracksWithoutTitle() {
        val spec = AiPlaylistParser.parse(
            """{"tracks":[{"title":"","artist":"X"},{"title":"B","artist":"Y"}]}""",
            20,
        ).getOrThrow()
        assertEquals(1, spec.tracks.size)
        assertEquals("B", spec.tracks[0].title)
    }

    @Test fun malformedJsonReturnsFailure() {
        assertTrue(AiPlaylistParser.parse("not json at all", 20).isFailure)
    }

    @Test fun noValidTracksReturnsFailure() {
        assertTrue(AiPlaylistParser.parse("""{"name":"P","tracks":[]}""", 20).isFailure)
    }
}
