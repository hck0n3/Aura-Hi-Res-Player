package iad1tya.echo.music.eq.audio

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.AudioProcessor.UnhandledAudioFormatException
import androidx.media3.common.audio.BaseAudioProcessor
import iad1tya.echo.music.eq.data.FilterType
import iad1tya.echo.music.eq.data.ParametricEQ
import java.nio.ByteBuffer

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class CustomEqualizerAudioProcessor(private val licenseKey: String = "akloSTZUT1k4N2ZGeGE5N2RhOGU3OWYyOGU4M2RkMGQxOGNmNjA4MDA5MjcwMjNlM2NjNzJoT0R4OGtwem1OamRtSGpFaHFG") : BaseAudioProcessor() {

    init {
        try {
            System.loadLibrary("superpowered-bridge")
        } catch (e: UnsatisfiedLinkError) {
            e.printStackTrace()
        }
    }

    private var isInitialized = false
    private var enabled = false
    private var nativePtr: Long = 0L

    private external fun initSuperpowered(licenseKey: String, sampleRate: Int): Long
    private external fun setPreamp(ptr: Long, preampDb: Float)
    private external fun setSafeVolume(ptr: Long, enabled: Boolean, gainLinear: Float)
    private external fun disableAllBands(ptr: Long)
    private external fun setEqBand(ptr: Long, index: Int, frequency: Float, gainDb: Float, q: Float, filterType: Int)
    private external fun processAudio(ptr: Long, inputBuffer: ByteBuffer, outputBuffer: ByteBuffer, numFrames: Int, encoding: Int, channels: Int, enabled: Boolean)
    private external fun releaseSuperpowered(ptr: Long)

    // Optional "Safe Volume" stage (opt-in). Kept so it can be re-applied if the native processor is
    // re-created on format change (onConfigure). gainLinear is attenuate-only (<= 1.0); 1.0 = no change.
    private var safeVolumeEnabled = false
    private var safeVolumeGain = 1f

    /**
     * Enable/disable the Safe Volume stage (per-track loudness normalization + limiter that runs even when
     * the EQ is off). [gainLinear] is the attenuate-only normalization multiplier for the current track.
     */
    fun applySafeVolume(enabled: Boolean, gainLinear: Float) {
        safeVolumeEnabled = enabled
        safeVolumeGain = gainLinear
        if (isInitialized && nativePtr != 0L) {
            setSafeVolume(nativePtr, enabled, gainLinear)
        }
    }

    fun isEnabled(): Boolean = enabled

    fun disable() {
        enabled = false
        if (isInitialized && nativePtr != 0L) {
            setPreamp(nativePtr, 0f)
            disableAllBands(nativePtr)
        }
    }

    fun applyProfile(profile: ParametricEQ) {
        enabled = true
        currentProfile = profile
        if (isInitialized && nativePtr != 0L) {
            disableAllBands(nativePtr)

            // Combine manual bands and auto-correction bands (auto first, then taste — LTI cascade).
            val allBands = profile.autoBands + profile.bands
            val enabledGains = allBands.filter { it.enabled }.map { it.gain }
            // AUTO-HEADROOM: trim the front preamp by the largest positive band boost (+1 dB safety) so a
            // boosted EQ/preset/AutoEq can never push the signal above its original level and slam the
            // limiter/soft-clip. Reuses headroomPreampDb (AutoEq/Wavelet convention). This is what keeps
            // every preset clip-free instead of relying on the limiter to gain-ride (which colors the sound).
            val effectivePreamp = headroomPreampDb(profile.preamp, enabledGains)
            setPreamp(nativePtr, effectivePreamp.toFloat())

            allBands.forEachIndexed { index, band ->
                if (band.enabled) {
                    val typeCode = when (band.filterType) {
                        FilterType.LSC -> 1  // low shelf
                        FilterType.HSC -> 2  // high shelf
                        else -> 0            // peak / parametric
                    }
                    setEqBand(nativePtr, index, band.frequency.toFloat(), band.gain.toFloat(), band.q.toFloat(), typeCode)
                }
            }
        }
    }

    private var currentProfile: ParametricEQ? = null

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT && inputAudioFormat.encoding != C.ENCODING_PCM_FLOAT) {
            throw UnhandledAudioFormatException(inputAudioFormat)
        }
        
        // Re-initialize if pointer was lost or not created
        if (nativePtr == 0L) {
            nativePtr = initSuperpowered(licenseKey, inputAudioFormat.sampleRate)
        }
        isInitialized = nativePtr != 0L

        // Restore profile if one was applied
        currentProfile?.let { applyProfile(it) }
        // Restore Safe Volume state (native processor may have just been re-created).
        if (isInitialized && nativePtr != 0L && safeVolumeEnabled) {
            setSafeVolume(nativePtr, safeVolumeEnabled, safeVolumeGain)
        }
        
        // Output format is exactly the same as the input format (pure 32-bit float supported natively)
        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val remaining = inputBuffer.remaining()
        if (remaining == 0) return

        val bytesPerSample = if (inputAudioFormat.encoding == C.ENCODING_PCM_FLOAT) 4 else 2
        val numFrames = remaining / (bytesPerSample * inputAudioFormat.channelCount)
        
        // Output size is identical to input size
        val outRemaining = remaining
        val buffer = replaceOutputBuffer(outRemaining)
        
        if (isInitialized && nativePtr != 0L) {
            val inputSlice = inputBuffer.slice()
            processAudio(nativePtr, inputSlice, buffer, numFrames, inputAudioFormat.encoding, inputAudioFormat.channelCount, enabled)
            // JNI writes directly to memory, so we must advance the ByteBuffer's position manually
            buffer.position(buffer.position() + outRemaining)
            inputBuffer.position(inputBuffer.position() + remaining)
        } else {
            // Uninitialized fallback: copy all bytes manually, which automatically advances positions for both
            buffer.put(inputBuffer)
        }
        
        buffer.flip()
    }

    override fun onReset() {
        if (isInitialized && nativePtr != 0L) {
            releaseSuperpowered(nativePtr)
            nativePtr = 0L
            isInitialized = false
        }
        super.onReset()
    }
}
