package iad1tya.echo.music.eq.audio
import androidx.media3.common.audio.BaseAudioProcessor
import java.nio.ByteBuffer
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class NormalizationGainAudioProcessor : BaseAudioProcessor() {
    companion object {
        var gain: Float = 1.0f
    }
    var instanceGain: Float? = null
    var measureThisTrack: Boolean = false
    var measurementCommitted: Boolean = false
    var measureTrackId: String = ""
    var measuredLoudnessDb: Double = 0.0

    fun startMeasurement(id: String) {}

    override fun onConfigure(inputAudioFormat: androidx.media3.common.audio.AudioProcessor.AudioFormat): androidx.media3.common.audio.AudioProcessor.AudioFormat {
        return androidx.media3.common.audio.AudioProcessor.AudioFormat.NOT_SET
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val remaining = inputBuffer.remaining()
        val buffer = replaceOutputBuffer(remaining)
        buffer.put(inputBuffer)
        buffer.flip()
    }
}
