package iad1tya.echo.music.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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

    // --- year / era check -------------------------------------------------------------------

    @Test fun dropsTracksOutsideTheRequestedEra() {
        val spec = AiPlaylistParser.parse(
            """{"tracks":[{"title":"A","artist":"X","year":1985},{"title":"B","artist":"Y","year":2015}]}""",
            20,
            eraRange = 1980..1989,
        ).getOrThrow()
        assertEquals(1, spec.tracks.size)
        assertEquals("A", spec.tracks[0].title)
    }

    @Test fun keepsTracksWithoutAYearEvenWhenAnEraWasAsked() {
        val spec = AiPlaylistParser.parse(
            """{"tracks":[{"title":"A","artist":"X"}]}""",
            20,
            eraRange = 1980..1989,
        ).getOrThrow()
        assertEquals(1, spec.tracks.size)
    }

    @Test fun withoutAnEraTheYearIsIgnored() {
        val spec = AiPlaylistParser.parse(
            """{"tracks":[{"title":"A","artist":"X","year":1985},{"title":"B","artist":"Y","year":2015}]}""",
            20,
        ).getOrThrow()
        assertEquals(2, spec.tracks.size)
    }

    @Test fun detectsSpanishAndNumericDecadeForms() {
        assertEquals(1980..1989, AiPlaylistParser.eraRange("punk de los 80"))
        assertEquals(1990..1999, AiPlaylistParser.eraRange("rock años 90"))
        assertEquals(1970..1979, AiPlaylistParser.eraRange("década de los 70"))
        assertEquals(2000..2009, AiPlaylistParser.eraRange("pop 2000s"))
        assertEquals(1980..1989, AiPlaylistParser.eraRange("synthwave 80s"))
    }

    @Test fun severalDecadesWidenIntoOneSpanInsteadOfNarrowing() {
        assertEquals(1970..1989, AiPlaylistParser.eraRange("rock de los 70 y 80"))
    }

    @Test fun aBareCountIsNotReadAsADecade() {
        // "los 20 mejores" is a count, not the 2020s — a false era would silently drop good songs.
        assertNull(AiPlaylistParser.eraRange("los 20 mejores temas de rock"))
        assertNull(AiPlaylistParser.eraRange("100 canciones para correr"))
        assertNull(AiPlaylistParser.eraRange("boleros tristes"))
    }

    @Test fun aCountIsNotReadAsADecadeEvenWhenADecadeWordIsPresent() {
        // "década" arms the check, but the only number is the requested COUNT: reading "40" as the
        // 1940s would drop nearly every generated track and gut the playlist.
        assertNull(AiPlaylistParser.eraRange("las 40 mejores canciones de la década"))
    }

    // --- edit parsing -----------------------------------------------------------------------

    @Test fun parsesEditWithRemovalsAndAdditions() {
        val edit = AiPlaylistParser.parseEdit(
            """{"remove":[1,3],"additions":[{"title":"New","artist":"Z"}]}""",
            trackCount = 5,
        ).getOrThrow()
        // 1-based in the reply → 0-based indices.
        assertEquals(listOf(0, 2), edit.removeIndices)
        assertEquals(1, edit.additions.size)
        assertEquals("New", edit.additions[0].title)
    }

    @Test fun editDropsOutOfRangeAndDuplicateIndices() {
        val edit = AiPlaylistParser.parseEdit(
            """{"remove":[0,1,1,99,-4,2]}""",
            trackCount = 3,
        ).getOrThrow()
        // 0 → -1 (invalid), 99/-4 out of range, 1 deduped → only 1 and 2 survive as 0 and 1.
        assertEquals(listOf(0, 1), edit.removeIndices)
    }

    @Test fun editAcceptsQuotedNumbersAndFences() {
        val edit = AiPlaylistParser.parseEdit(
            "```json\n{\"remove\":[\"2\"],\"additions\":[]}\n```",
            trackCount = 4,
        ).getOrThrow()
        assertEquals(listOf(1), edit.removeIndices)
    }

    @Test fun editIgnoresNonNumericRemovals() {
        val edit = AiPlaylistParser.parseEdit(
            """{"remove":["Some Song"],"additions":[{"title":"New","artist":"Z"}]}""",
            trackCount = 4,
        ).getOrThrow()
        assertTrue(edit.removeIndices.isEmpty())
        assertEquals(1, edit.additions.size)
    }

    @Test fun emptyEditReturnsFailureSoTheCallerNoOps() {
        assertTrue(AiPlaylistParser.parseEdit("""{"remove":[],"additions":[]}""", 5).isFailure)
        assertTrue(AiPlaylistParser.parseEdit("not json", 5).isFailure)
    }
}
