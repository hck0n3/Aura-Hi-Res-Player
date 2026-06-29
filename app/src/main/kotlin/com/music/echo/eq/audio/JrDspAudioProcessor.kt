package iad1tya.echo.music.eq.audio

import androidx.media3.common.audio.AudioProcessor
import java.nio.ByteBuffer

class JrDspAudioProcessor : AudioProcessor {
    class Config(
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
    )

    companion object {
        var config: Config = Config()
    }

    override fun configure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat = inputAudioFormat
    override fun isActive(): Boolean = false
    override fun queueInput(inputBuffer: ByteBuffer) {}
    override fun queueEndOfStream() {}
    override fun getOutput(): ByteBuffer = AudioProcessor.EMPTY_BUFFER
    override fun isEnded(): Boolean = true
    override fun flush() {}
    override fun reset() {}
}
