package iad1tya.echo.music.eq.audio

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.util.UnstableApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Shared bus between the audio pipeline and the Compose UI.
 * [SpectrumAudioProcessor] publishes 64 normalized band magnitudes (0..1)
 * at ~30 Hz while [enabled] is true.
 */
object SpectrumBus {
    const val BAND_COUNT = 64

    @Volatile
    var enabled: Boolean = false

    private val _spectrum = MutableStateFlow(FloatArray(BAND_COUNT))
    val spectrum: StateFlow<FloatArray> = _spectrum.asStateFlow()

    internal fun publish(data: FloatArray) {
        _spectrum.value = data
    }

    fun clear() {
        _spectrum.value = FloatArray(BAND_COUNT)
    }
}

/**
 * Real-time FFT spectrum analyzer (Aura Hi-Res Player visualizer port).
 *
 * Passes audio through untouched. While the visualizer is enabled it mixes
 * the signal down to mono, runs a Hann-windowed 2048-point FFT throttled to
 * ~30 Hz and publishes 64 log-spaced bands (20 Hz – 20 kHz) to [SpectrumBus].
 */
@UnstableApi
class SpectrumAudioProcessor : AudioProcessor {

    private var sampleRate = 0
    private var channelCount = 0
    private var encoding = C.ENCODING_INVALID
    private var isActive = false

    private var outputBuffer: ByteBuffer = EMPTY_BUFFER
    private var inputEnded = false

    private val ring = FloatArray(FFT_SIZE)
    private var ringPos = 0
    private var samplesSinceFft = 0
    private var lastFftAtMs = 0L

    private val re = DoubleArray(FFT_SIZE)
    private val im = DoubleArray(FFT_SIZE)
    private val window = DoubleArray(FFT_SIZE) { 0.5 * (1.0 - cos(2.0 * Math.PI * it / (FFT_SIZE - 1))) }
    private val smoothed = FloatArray(SpectrumBus.BAND_COUNT)

    companion object {
        private val EMPTY_BUFFER: ByteBuffer = ByteBuffer.allocateDirect(0).order(ByteOrder.nativeOrder())

        private const val FFT_SIZE = 2048
        private const val MIN_FREQ = 20.0
        private const val MAX_FREQ = 20000.0
        private const val FRAME_INTERVAL_MS = 33L // ~30 Hz, same cadence as JR player
        private const val DB_FLOOR = -60.0
        private const val ATTACK = 0.55f
        private const val DECAY = 0.18f
    }

    override fun configure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        sampleRate = inputAudioFormat.sampleRate
        channelCount = inputAudioFormat.channelCount
        encoding = inputAudioFormat.encoding

        if (encoding != C.ENCODING_PCM_16BIT || channelCount > 2) {
            // Self-bypass instead of crashing on >2ch / non-16-bit (e.g. a 5.1 local file): Media3 skips an
            // inactive processor, so playback continues rather than failing fatally.
            isActive = false
            return AudioProcessor.AudioFormat.NOT_SET
        }

        ring.fill(0f)
        ringPos = 0
        samplesSinceFft = 0

        isActive = true
        return inputAudioFormat
    }

    override fun isActive(): Boolean = isActive

    override fun queueInput(inputBuffer: ByteBuffer) {
        val remaining = inputBuffer.remaining()
        if (remaining == 0) return

        if (outputBuffer === EMPTY_BUFFER || outputBuffer.capacity() < remaining) {
            outputBuffer = ByteBuffer.allocateDirect(remaining).order(ByteOrder.nativeOrder())
        } else {
            outputBuffer.clear()
        }

        if (!SpectrumBus.enabled) {
            outputBuffer.put(inputBuffer)
            outputBuffer.flip()
            return
        }

        val startPos = inputBuffer.position()
        val bytesPerFrame = 2 * channelCount
        val frames = remaining / bytesPerFrame

        repeat(frames) {
            val mono: Float = if (channelCount == 2) {
                val l = inputBuffer.getShort().toFloat() / 32768.0f
                val r = inputBuffer.getShort().toFloat() / 32768.0f
                (l + r) * 0.5f
            } else {
                inputBuffer.getShort().toFloat() / 32768.0f
            }
            ring[ringPos] = mono
            ringPos = (ringPos + 1) and (FFT_SIZE - 1)
            samplesSinceFft++
        }

        // Copy input through untouched
        inputBuffer.position(startPos)
        outputBuffer.put(inputBuffer)
        outputBuffer.flip()

        maybeComputeSpectrum()
    }

    private fun maybeComputeSpectrum() {
        if (samplesSinceFft < FFT_SIZE / 2) return
        val now = System.currentTimeMillis()
        if (now - lastFftAtMs < FRAME_INTERVAL_MS) return
        lastFftAtMs = now
        samplesSinceFft = 0

        // Unroll ring buffer (oldest sample first) with Hann window
        for (i in 0 until FFT_SIZE) {
            re[i] = ring[(ringPos + i) and (FFT_SIZE - 1)] * window[i]
            im[i] = 0.0
        }
        fft(re, im)

        val bands = FloatArray(SpectrumBus.BAND_COUNT)
        val nyquist = sampleRate / 2.0
        val maxFreq = min(MAX_FREQ, nyquist)
        val logMin = ln(MIN_FREQ)
        val logMax = ln(maxFreq)
        val binHz = sampleRate.toDouble() / FFT_SIZE

        for (b in 0 until SpectrumBus.BAND_COUNT) {
            val f0 = kotlin.math.exp(logMin + (logMax - logMin) * b / SpectrumBus.BAND_COUNT)
            val f1 = kotlin.math.exp(logMin + (logMax - logMin) * (b + 1) / SpectrumBus.BAND_COUNT)
            var bin0 = (f0 / binHz).toInt().coerceIn(1, FFT_SIZE / 2 - 1)
            val bin1 = (f1 / binHz).toInt().coerceIn(bin0, FFT_SIZE / 2 - 1)
            var peak = 0.0
            while (bin0 <= bin1) {
                val mag = sqrt(re[bin0] * re[bin0] + im[bin0] * im[bin0]) / (FFT_SIZE / 4.0)
                peak = max(peak, mag)
                bin0++
            }
            val db = 20.0 * log10(max(peak, 1e-9))
            val norm = ((db - DB_FLOOR) / -DB_FLOOR).coerceIn(0.0, 1.0)

            // Fast attack, slow decay — keeps bars lively without flicker
            val prev = smoothed[b]
            val target = norm.toFloat()
            smoothed[b] = if (target > prev) {
                prev + (target - prev) * ATTACK
            } else {
                prev + (target - prev) * DECAY
            }
            bands[b] = smoothed[b]
        }

        SpectrumBus.publish(bands)
    }

    /** In-place iterative radix-2 Cooley-Tukey FFT. */
    private fun fft(re: DoubleArray, im: DoubleArray) {
        val n = re.size
        var j = 0
        for (i in 1 until n) {
            var bit = n shr 1
            while (j and bit != 0) {
                j = j xor bit
                bit = bit shr 1
            }
            j = j or bit
            if (i < j) {
                var tmp = re[i]; re[i] = re[j]; re[j] = tmp
                tmp = im[i]; im[i] = im[j]; im[j] = tmp
            }
        }
        var len = 2
        while (len <= n) {
            val ang = -2.0 * Math.PI / len
            val wRe = cos(ang)
            val wIm = kotlin.math.sin(ang)
            var i = 0
            while (i < n) {
                var curRe = 1.0
                var curIm = 0.0
                for (k in 0 until len / 2) {
                    val evenRe = re[i + k]
                    val evenIm = im[i + k]
                    val oddRe = re[i + k + len / 2] * curRe - im[i + k + len / 2] * curIm
                    val oddIm = re[i + k + len / 2] * curIm + im[i + k + len / 2] * curRe
                    re[i + k] = evenRe + oddRe
                    im[i + k] = evenIm + oddIm
                    re[i + k + len / 2] = evenRe - oddRe
                    im[i + k + len / 2] = evenIm - oddIm
                    val nextRe = curRe * wRe - curIm * wIm
                    curIm = curRe * wIm + curIm * wRe
                    curRe = nextRe
                }
                i += len
            }
            len = len shl 1
        }
    }

    override fun getOutput(): ByteBuffer {
        val buffer = outputBuffer
        outputBuffer = EMPTY_BUFFER
        return buffer
    }

    override fun isEnded(): Boolean = inputEnded && outputBuffer.remaining() == 0

    override fun queueEndOfStream() {
        inputEnded = true
    }

    @Deprecated("Deprecated in Java")
    override fun flush() {
        outputBuffer = EMPTY_BUFFER
        inputEnded = false
        ring.fill(0f)
        ringPos = 0
        samplesSinceFft = 0
    }

    override fun reset() {
        @Suppress("DEPRECATION")
        flush()
        sampleRate = 0
        channelCount = 0
        encoding = C.ENCODING_INVALID
        isActive = false
        smoothed.fill(0f)
        SpectrumBus.clear()
    }
}
