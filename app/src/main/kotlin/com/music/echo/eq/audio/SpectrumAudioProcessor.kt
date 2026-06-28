package iad1tya.echo.music.eq.audio
import androidx.media3.common.audio.BaseAudioProcessor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.nio.ByteBuffer

object SpectrumBus {
    const val BAND_COUNT = 64
    @Volatile var enabled: Boolean = false
    private val _spectrum = MutableStateFlow(FloatArray(BAND_COUNT))
    val spectrum: StateFlow<FloatArray> = _spectrum.asStateFlow()
    internal fun publish(data: FloatArray) { _spectrum.value = data }
    fun clear() { _spectrum.value = FloatArray(BAND_COUNT) }
}

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class SpectrumAudioProcessor : BaseAudioProcessor() {
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
