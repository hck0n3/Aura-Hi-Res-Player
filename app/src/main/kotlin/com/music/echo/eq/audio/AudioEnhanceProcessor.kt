package iad1tya.echo.music.eq.audio

import androidx.media3.common.audio.AudioProcessor
import java.nio.ByteBuffer

class AudioEnhanceProcessor : AudioProcessor {
    companion object {
        var enabled: Boolean = false
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
