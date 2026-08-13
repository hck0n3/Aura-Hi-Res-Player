package iad1tya.echo.music.playback

import iad1tya.echo.music.playback.audio.AudioOffloadGate
import iad1tya.echo.music.playback.audio.AudioOffloadGate.BlockReason
import iad1tya.echo.music.playback.audio.AudioOffloadGate.Inputs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Locks the offload gate. The failure this guards against is not a crash: it is offload engaging while
 * a DSP stage is live, which makes that stage INAUDIBLE while its screen keeps claiming it is on. The
 * asymmetry is deliberate — a false "blocked" only costs battery, a false "allowed" costs audio.
 */
class AudioOffloadGateTest {

    /** Everything off / cleared: the only genuinely bit-perfect configuration. */
    private val passthrough = Inputs(
        userWantsOffload = true,
        crossfadeEnabled = false,
        highPerformanceMode = false,
        safeVolumeEnabled = false,
        equalizerActive = false,
    )

    @Test
    fun `offload is allowed only on true passthrough`() {
        assertNull(AudioOffloadGate.blockReason(passthrough))
        assertTrue(AudioOffloadGate.allowOffload(passthrough))
    }

    @Test
    fun `the user switch alone never grants offload`() {
        assertFalse(AudioOffloadGate.allowOffload(passthrough.copy(userWantsOffload = false)))
        // ...and the switch being on is not enough either, once anything is live.
        assertFalse(AudioOffloadGate.allowOffload(passthrough.copy(equalizerActive = true)))
    }

    @Test
    fun `every live DSP stage vetoes offload`() {
        assertFalse(AudioOffloadGate.allowOffload(passthrough.copy(crossfadeEnabled = true)))
        assertFalse(AudioOffloadGate.allowOffload(passthrough.copy(safeVolumeEnabled = true)))
        assertFalse(AudioOffloadGate.allowOffload(passthrough.copy(equalizerActive = true)))
        assertFalse(AudioOffloadGate.allowOffload(passthrough.copy(spatialEnabled = true)))
        assertEquals(BlockReason.SPATIAL, AudioOffloadGate.blockReason(passthrough.copy(spatialEnabled = true)))
    }

    /**
     * The EQ term is "a profile is applied", not "the profile is audibly non-flat", because the preamp
     * and the de-esser both run inside the native runEq branch. A flat profile still processes.
     */
    @Test
    fun `an applied EQ profile vetoes even when the caller believes it is flat`() {
        assertEquals(
            BlockReason.EQUALIZER,
            AudioOffloadGate.blockReason(passthrough.copy(equalizerActive = true)),
        )
    }

    @Test
    fun `crossfade only vetoes when it can actually run`() {
        val crossfadeOn = passthrough.copy(crossfadeEnabled = true)
        assertTrue(AudioOffloadGate.crossfadeRunning(crossfadeOn))
        assertEquals(BlockReason.CROSSFADE, AudioOffloadGate.blockReason(crossfadeOn))

        // High-Performance Mode force-disables crossfade at every live site, so no second player is ever
        // built and the key alone must NOT cost offload on exactly the weak devices that need it most.
        val underPerfMode = crossfadeOn.copy(highPerformanceMode = true)
        assertFalse(AudioOffloadGate.crossfadeRunning(underPerfMode))
        assertNull(AudioOffloadGate.blockReason(underPerfMode))
        assertTrue(AudioOffloadGate.allowOffload(underPerfMode))
    }

    @Test
    fun `perf mode does not rescue offload from the other vetoes`() {
        val perfModeWithEq = passthrough.copy(highPerformanceMode = true, equalizerActive = true)
        assertFalse(AudioOffloadGate.allowOffload(perfModeWithEq))
        val perfModeWithSafeVolume = passthrough.copy(highPerformanceMode = true, safeVolumeEnabled = true)
        assertFalse(AudioOffloadGate.allowOffload(perfModeWithSafeVolume))
        val perfModeWithSpatial = passthrough.copy(highPerformanceMode = true, spatialEnabled = true)
        assertFalse(AudioOffloadGate.allowOffload(perfModeWithSpatial))
    }

    /**
     * The owner's own defaults: crossfade on, Safe Volume on, an EQ profile applied. This test exists so
     * nobody reads the feature as a battery win for him — it is not, and it must never silently become
     * one by relaxing a term.
     */
    @Test
    fun `the shipped defaults never reach offload`() {
        val ownerDefaults = Inputs(
            userWantsOffload = true,
            crossfadeEnabled = true,
            highPerformanceMode = false,
            safeVolumeEnabled = true,
            equalizerActive = true,
        )
        assertFalse(AudioOffloadGate.allowOffload(ownerDefaults))
        assertEquals(BlockReason.CROSSFADE, AudioOffloadGate.blockReason(ownerDefaults))
    }

    /**
     * Exhaustive: across every combination of the four DSP inputs, "allowed" must imply that all of them
     * are idle. This is the invariant, not the individual cases above.
     */
    @Test
    fun `allowed implies nothing is live, over the whole input space`() {
        val flags = listOf(false, true)
        var allowedCount = 0
        for (wants in flags) for (cf in flags) for (perf in flags) for (sv in flags) for (eq in flags) for (spatial in flags) {
            val inputs = Inputs(wants, cf, perf, sv, eq, spatial)
            if (AudioOffloadGate.allowOffload(inputs)) {
                allowedCount++
                assertTrue("offload allowed without the user asking: $inputs", inputs.userWantsOffload)
                assertFalse("offload allowed with Safe Volume live: $inputs", inputs.safeVolumeEnabled)
                assertFalse("offload allowed with the EQ live: $inputs", inputs.equalizerActive)
                assertFalse("offload allowed with spatial live: $inputs", inputs.spatialEnabled)
                assertFalse(
                    "offload allowed with crossfade able to run: $inputs",
                    inputs.crossfadeEnabled && !inputs.highPerformanceMode,
                )
            }
        }
        // wants=true, sv=false, eq=false, spatial=false, and (cf=false with perf either way) or (cf=true with perf=true).
        assertEquals(3, allowedCount)
    }
}
