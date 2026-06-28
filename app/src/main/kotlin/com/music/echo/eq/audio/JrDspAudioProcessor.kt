package iad1tya.echo.music.eq.audio
import androidx.media3.common.audio.BaseAudioProcessor
import java.nio.ByteBuffer
import iad1tya.echo.music.constants.AudioQuality
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class JrDspAudioProcessor : BaseAudioProcessor() {
    data class Config(
        val signatureEnabled: Boolean = true,
        val loudnessEnabled: Boolean = false,
        val hrtfEnabled: Boolean = false,
        val bassEnhanceEnabled: Boolean = false,
        val bassEnhanceAmount: Float = 0.28f,
        val exciterEnabled: Boolean = false,
        val exciterAmount: Float = 0.15f,
        val mbCompEnabled: Boolean = false,
        val stereoWidthEnabled: Boolean = false,
        val stereoWidth: Float = 1.0f,
        val dialogueEnabled: Boolean = false,
        val dialogueAmount: Float = 0.35f,
    ) {
        val anyActive: Boolean = false
    }
    companion object {
        var config: Config? = null
    }
    override fun queueInput(inputBuffer: ByteBuffer) {
        val remaining = inputBuffer.remaining()
        val buffer = replaceOutputBuffer(remaining)
        buffer.put(inputBuffer)
        buffer.flip()
    }
}
