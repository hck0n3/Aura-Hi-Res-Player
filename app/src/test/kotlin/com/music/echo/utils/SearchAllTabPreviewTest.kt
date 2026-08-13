package iad1tya.echo.music.utils

import com.music.innertube.models.ArtistItem
import com.music.innertube.models.SongItem
import com.music.innertube.models.YTItem
import org.junit.Assert.assertEquals
import org.junit.Test

class SearchAllTabPreviewTest {

    @Test
    fun mixedTopResultStartingWithArtistIsNotTruncated() {
        val items: List<YTItem> = listOf(
            artist("UC1"),
            song("s1"),
            song("s2"),
            song("s3"),
        )
        assertEquals(4, items.forAllTabSearchPreview().size)
        assertEquals(listOf("UC1", "s1", "s2", "s3"), items.forAllTabSearchPreview().map { it.id })
    }

    @Test
    fun dedicatedArtistShelfCapsAtTwo() {
        val items: List<YTItem> = listOf(artist("a"), artist("b"), artist("c"))
        assertEquals(listOf("a", "b"), items.forAllTabSearchPreview().map { it.id })
    }

    @Test
    fun songShelfIsUntouched() {
        val items: List<YTItem> = listOf(song("s1"), song("s2"), song("s3"))
        assertEquals(3, items.forAllTabSearchPreview().size)
    }

    @Test
    fun novedadesClaimUniqueDropsLaterRepeats() {
        val seen = linkedSetOf<String>()
        val first = seen.claimUnique(listOf("a", "b", "c")) { it }
        val second = seen.claimUnique(listOf("b", "c", "d")) { it }
        assertEquals(listOf("a", "b", "c"), first)
        assertEquals(listOf("d"), second)
    }

    private fun artist(id: String) = ArtistItem(
        id = id,
        title = id,
        thumbnail = "",
        shuffleEndpoint = null,
        radioEndpoint = null,
    )

    private fun song(id: String) = SongItem(
        id = id,
        title = id,
        artists = emptyList(),
        thumbnail = "",
    )
}
