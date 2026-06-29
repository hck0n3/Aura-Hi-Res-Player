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

    private external fun initSuperpowered(licenseKey: String, sampleRate: Int)
    private external fun setPreamp(preampDb: Float)
    private external fun disableAllBands()
    private external fun setEqBand(index: Int, frequency: Float, gainDb: Float, q: Float)
    private external fun processAudio(inputBuffer: ByteBuffer, outputBuffer: ByteBuffer, numFrames: Int, encoding: Int, channels: Int, enabled: Boolean)
    private external fun releaseSuperpowered()

    fun isEnabled(): Boolean = enabled

    fun disable() {
        enabled = false
        if (isInitialized) {
            setPreamp(0f)
            disableAllBands()
        }
    }

    fun applyProfile(profile: ParametricEQ) {
        enabled = true
        currentProfile = profile
        if (isInitialized) {
            disableAllBands()
            setPreamp(profile.preamp.toFloat())
            
            // Combine manual bands and auto-correction bands
            val allBands = profile.autoBands + profile.bands
            allBands.forEachIndexed { index, band ->
                if (band.enabled) {
                    setEqBand(index, band.frequency.toFloat(), band.gain.toFloat(), band.q.toFloat())
                }
            }
        }
    }

    private var currentProfile: ParametricEQ? = null

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT && inputAudioFormat.encoding != C.ENCODING_PCM_FLOAT) {
            throw UnhandledAudioFormatException(inputAudioFormat)
        }
        initSuperpowered(licenseKey, inputAudioFormat.sampleRate)
        isInitialized = true
        
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
        
        if (isInitialized) {
            val inputSlice = inputBuffer.slice()
            processAudio(inputSlice, buffer, numFrames, inputAudioFormat.encoding, inputAudioFormat.channelCount, enabled)
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
        if (isInitialized) {
            releaseSuperpowered()
            isInitialized = false
        }
        super.onReset()
    }
}
