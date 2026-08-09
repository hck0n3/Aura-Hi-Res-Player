package iad1tya.echo.music.utils.coil

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalAudioArtFetcherTest {

    @Test
    fun uriFor_wrapsContentEncodedAndAddsCacheBust() {
        val wrapped = LocalAudioArtFetcher.uriFor("content://media/external/audio/media/42")
        assertTrue(wrapped.startsWith(LocalAudioArtFetcher.SCHEME_PREFIX + "//a/"))
        assertTrue(wrapped.endsWith("#apic2"))
        assertEquals(
            "content://media/external/audio/media/42",
            LocalAudioArtFetcher.unwrapModel(wrapped),
        )
    }

    @Test
    fun unwrapModel_acceptsLegacyWithoutFragment() {
        val legacy = "localaudioart:content://media/external/audio/media/7"
        assertEquals(
            "content://media/external/audio/media/7",
            LocalAudioArtFetcher.unwrapModel(legacy),
        )
    }

    @Test
    fun unwrapModel_acceptsLegacyWithApic1() {
        val legacy = "localaudioart:content://media/external/audio/media/7#apic1"
        assertEquals(
            "content://media/external/audio/media/7",
            LocalAudioArtFetcher.unwrapModel(legacy),
        )
    }

    @Test
    fun unwrapModel_acceptsFileScheme() {
        val wrapped = LocalAudioArtFetcher.uriFor("file:///storage/emulated/0/Music/a.mp3")
        assertEquals(
            "file:///storage/emulated/0/Music/a.mp3",
            LocalAudioArtFetcher.unwrapModel(wrapped),
        )
    }

    @Test
    fun unwrapModel_rejectsHttpAndBarePaths() {
        assertNull(LocalAudioArtFetcher.unwrapModel("https://i.ytimg.com/vi/x/hqdefault.jpg"))
        assertNull(LocalAudioArtFetcher.unwrapModel("localaudioart:https://evil.example/x"))
        assertNull(LocalAudioArtFetcher.unwrapModel("content://media/external/audio/media/1"))
        assertNull(LocalAudioArtFetcher.unwrapModel("localaudioart://a/https%3A%2F%2Fevil.example%2Fx"))
    }
}
