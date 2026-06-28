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
    private external fun setEqBand(index: Int, frequency: Float, gainDb: Float, q: Float)
    private external fun processAudio(inputBuffer: ByteBuffer, outputBuffer: ByteBuffer, numFrames: Int, encoding: Int, channels: Int)
    private external fun releaseSuperpowered()

    fun isEnabled(): Boolean = enabled

    fun disable() {
        enabled = false
        if (isInitialized) {
            for (i in 0 until 10) {
                setEqBand(i, 1000f, 0f, 1f)
            }
        }
    }

    fun applyProfile(profile: ParametricEQ) {
        enabled = true
        if (isInitialized) {
            profile.bands.forEachIndexed { index, band ->
                setEqBand(index, band.frequency.toFloat(), band.gain.toFloat(), band.q.toFloat())
            }
        }
    }

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT && inputAudioFormat.encoding != C.ENCODING_PCM_FLOAT) {
            throw UnhandledAudioFormatException(inputAudioFormat)
        }
        initSuperpowered(licenseKey, inputAudioFormat.sampleRate)
        isInitialized = true
        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val remaining = inputBuffer.remaining()
        if (remaining == 0) return

        val bytesPerSample = if (inputAudioFormat.encoding == C.ENCODING_PCM_FLOAT) 4 else 2
        val numFrames = remaining / (bytesPerSample * inputAudioFormat.channelCount)
        val buffer = replaceOutputBuffer(remaining)
        
        if (enabled && isInitialized) {
            processAudio(inputBuffer, buffer, numFrames, inputAudioFormat.encoding, inputAudioFormat.channelCount)
        } else {
            buffer.put(inputBuffer)
        }
        
        inputBuffer.position(inputBuffer.position() + remaining)
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
