package iad1tya.echo.music.eq.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Locks the spatial profiles as real, distinct Superpowered configurations — not brand-name labels
 * on one shared HRTF. If two profiles packed the same native params, the EQ-screen chips would be
 * a placebo.
 */
class SpatialAudioProfileTest {

    @Test
    fun `native param arrays match the documented layout`() {
        SpatialAudioProfile.entries.forEach { profile ->
            val p = profile.toNativeParams()
            assertEquals(SpatialAudioProfile.NATIVE_PARAM_COUNT, p.size)
            assertEquals(profile.azL, p[0])
            assertEquals(profile.azR, p[1])
            assertEquals(profile.elL, p[2])
            assertEquals(profile.elR, p[3])
            assertEquals(profile.rearAzL, p[4])
            assertEquals(profile.rearAzR, p[5])
            assertEquals(profile.rearEl, p[6])
            assertEquals(profile.phantomGain, p[7])
            assertEquals(profile.phantomDelayMs, p[8])
            assertEquals(profile.reverbMix, p[9])
            assertEquals(profile.sound2, p[15] > 0.5f)
            assertEquals(profile.inputVolume, p[16])
            assertEquals(profile.crossfeedAmt, p[17])
            assertEquals(profile.speakerWidth, p[18])
        }
    }

    @Test
    fun `every profile packs a unique native fingerprint`() {
        val fingerprints = SpatialAudioProfile.entries.map { it.toNativeParams().toList() }
        assertEquals(fingerprints.size, fingerprints.toSet().size)
    }

    @Test
    fun `apple is dryer and more frontal than sony`() {
        val apple = SpatialAudioProfile.APPLE_FRONT
        val sony = SpatialAudioProfile.SONY_SPHERE
        assertTrue("Apple stage should be narrower than Sony", apple.azR < sony.azR)
        assertTrue("Apple elevation should be lower than Sony's sphere", apple.elL < sony.elL)
        assertTrue("Apple room should be dryer than Sony", apple.reverbMix < sony.reverbMix)
        assertTrue("Sony should use phantom rears", sony.phantomGain > apple.phantomGain)
        assertTrue("Sony uses the alternate Spatializer timbre", sony.sound2 && !apple.sound2)
    }

    @Test
    fun `cinema has more surround energy than apple`() {
        val apple = SpatialAudioProfile.APPLE_FRONT
        val cinema = SpatialAudioProfile.CINEMA
        assertTrue(cinema.phantomGain > apple.phantomGain)
        assertTrue(cinema.reverbMix > apple.reverbMix)
        assertTrue(cinema.reverbRoomSize > apple.reverbRoomSize)
    }

    @Test
    fun `crossfeed is not an HRTF profile`() {
        assertEquals(SpatialAudioProfile.ALGO_CROSSFEED, SpatialAudioProfile.CROSSFEED.algorithm)
        assertEquals(0f, SpatialAudioProfile.CROSSFEED.phantomGain)
        assertEquals(0f, SpatialAudioProfile.CROSSFEED.reverbMix)
        assertEquals(1.0f, SpatialAudioProfile.CROSSFEED.speakerWidth)
    }

    @Test
    fun `speakers remap HRTF to mid-side width, headphones keep the profile algorithm`() {
        val apple = SpatialAudioProfile.APPLE_FRONT
        assertEquals(
            SpatialAudioProfile.ALGO_SPEAKER_MS,
            SpatialAudioProfile.nativeAlgorithm(apple, SpatialOutputKind.SPEAKER),
        )
        assertEquals(
            SpatialAudioProfile.ALGO_SPATIALIZER,
            SpatialAudioProfile.nativeAlgorithm(apple, SpatialOutputKind.HEADPHONE),
        )
        assertEquals(
            SpatialAudioProfile.ALGO_CROSSFEED,
            SpatialAudioProfile.nativeAlgorithm(SpatialAudioProfile.CROSSFEED, SpatialOutputKind.HEADPHONE),
        )
    }

    @Test
    fun `fromName only exposes wide surround and crossfeed`() {
        assertEquals(SpatialAudioProfile.CROSSFEED, SpatialAudioProfile.fromName("CROSSFEED"))
        assertEquals(SpatialAudioProfile.WIDE_SURROUND, SpatialAudioProfile.fromName("WIDE_SURROUND"))
        assertEquals(SpatialAudioProfile.WIDE_SURROUND, SpatialAudioProfile.fromName("SONY_SPHERE"))
        assertEquals(SpatialAudioProfile.WIDE_SURROUND, SpatialAudioProfile.fromName(null))
        assertEquals(SpatialAudioProfile.WIDE_SURROUND, SpatialAudioProfile.fromName("not-a-profile"))
        assertEquals(
            listOf(SpatialAudioProfile.WIDE_SURROUND, SpatialAudioProfile.CROSSFEED),
            SpatialAudioProfile.uiProfiles,
        )
    }

    @Test
    fun `speaker width stays in the non-collapsing range`() {
        SpatialAudioProfile.entries.forEach { profile ->
            assertTrue("${profile.name} speakerWidth too narrow: ${profile.speakerWidth}", profile.speakerWidth >= 1.0f)
            assertTrue("${profile.name} speakerWidth too wide: ${profile.speakerWidth}", profile.speakerWidth <= 1.45f)
        }
    }
}
