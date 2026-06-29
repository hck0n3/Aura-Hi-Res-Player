package iad1tya.echo.music.eq.audio

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.AudioProcessor.UnhandledAudioFormatException
import androidx.media3.common.audio.BaseAudioProcessor
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
    private external fun disableAllBands(ptr: Long)
    private external fun setEqBand(ptr: Long, index: Int, frequency: Float, gainDb: Float, q: Float)
    private external fun processAudio(ptr: Long, inputBuffer: ByteBuffer, outputBuffer: ByteBuffer, numFrames: Int, encoding: Int, channels: Int, enabled: Boolean)
    private external fun releaseSuperpowered(ptr: Long)

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
            setPreamp(nativePtr, profile.preamp.toFloat())
            
            // Combine manual bands and auto-correction bands
            val allBands = profile.autoBands + profile.bands
            allBands.forEachIndexed { index, band ->
                if (band.enabled) {
                    setEqBand(nativePtr, index, band.frequency.toFloat(), band.gain.toFloat(), band.q.toFloat())
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
