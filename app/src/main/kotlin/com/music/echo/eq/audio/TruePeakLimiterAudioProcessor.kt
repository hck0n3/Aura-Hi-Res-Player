package iad1tya.echo.music.eq.audio
import androidx.media3.common.audio.BaseAudioProcessor
import java.nio.ByteBuffer
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class TruePeakLimiterAudioProcessor : BaseAudioProcessor() {
    companion object {
        var loudnessMakeup: Float = 1.0f
        var eqMakeup: Float = 1.0f
        var enabled: Boolean = true
    }
    var instanceLoudnessMakeup: Float? = null
    var instanceEqMakeup: Float? = null

    fun setInstanceMakeup(loudness: Float?, eq: Float?) {
        instanceLoudnessMakeup = loudness
        instanceEqMakeup = eq
    }
    
    fun useMeasureRampOnce() {}

    override fun queueInput(inputBuffer: ByteBuffer) {
        val remaining = inputBuffer.remaining()
        val buffer = replaceOutputBuffer(remaining)
        buffer.put(inputBuffer)
        buffer.flip()
    }
}
