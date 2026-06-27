package iad1tya.echo.music.eq.audio

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.util.UnstableApi
import iad1tya.echo.music.eq.data.ParametricEQ
import iad1tya.echo.music.eq.data.ParametricEQBand
import timber.log.Timber
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.pow


@UnstableApi
@SuppressWarnings("Deprecated")
class CustomEqualizerAudioProcessor : AudioProcessor {

    private var sampleRate = 0
    private var channelCount = 0
    private var encoding = C.ENCODING_INVALID
    private var isActive = false
    private var equalizerEnabled = false

    private var inputBuffer: ByteBuffer = EMPTY_BUFFER
    private var outputBuffer: ByteBuffer = EMPTY_BUFFER
    private var inputEnded = false

    // @Volatile: these are swapped under @Synchronized from a non-audio thread but read on the audio thread;
    // the volatile reference gives the audio thread a safe, up-to-date snapshot (no stale/empty filter set).
    @Volatile private var filters: List<BiquadFilter> = emptyList()
    @Volatile private var preampGain: Double = 1.0
    private var pendingProfile: ParametricEQ? = null

    companion object {
        private const val TAG = "CustomEqualizerAudioProcessor"
        private val EMPTY_BUFFER: ByteBuffer = ByteBuffer.allocateDirect(0).order(ByteOrder.nativeOrder())
    }

    /** Soft clip a normalized (-1..1-ish) sample: linear below 0.95, then a tanh knee toward ~1.0 so a
     *  high-Q transient rounds off instead of hard-clipping audibly before the downstream limiter. */
    private fun softClip(x: Double): Double {
        val a = kotlin.math.abs(x)
        if (a <= 0.95) return x
        val s = if (x < 0) -1.0 else 1.0
        return s * (0.95 + 0.05 * kotlin.math.tanh((a - 0.95) / 0.05))
    }

    
    @Synchronized
    fun applyProfile(parametricEQ: ParametricEQ) {
        if (sampleRate == 0) {
            Timber.tag(TAG)
                .d("Audio processor not configured yet. Storing profile as pending with ${parametricEQ.bands.size} bands")
            pendingProfile = parametricEQ
            return
        }

        // Auto-headroom: never let a boosted band push the signal past 0 dBFS (prevents the
        // hard-clip in processAudioBuffer16Bit, the main source of EQ distortion).
        preampGain = 10.0.pow(
            headroomPreampDb(parametricEQ.preamp, parametricEQ.bands.filter { it.enabled }.map { it.gain }) / 20.0,
        )
        // Cancel that auto-headroom downstream so boosting bands doesn't drop the volume; the
        // true-peak limiter catches the restored peaks (loud EQ, no clipping).
        TruePeakLimiterAudioProcessor.eqMakeup = dbToLinear(
            eqMakeupDb(parametricEQ.preamp, parametricEQ.bands.filter { it.enabled }.map { it.gain }),
        )

        val active = parametricEQ.bands.filter { it.enabled && it.frequency < sampleRate / 2.0 }
        if (equalizerEnabled && filters.isNotEmpty() && filters.size == active.size) {
            // Real-time, gapless: update existing biquad coefficients in place and keep filter
            // state (no reset) so dragging a band never clicks, stutters or stops playback.
            active.forEachIndexed { i, band ->
                filters[i].update(band.frequency, band.gain, band.q, band.filterType)
            }
        } else {
            // Structure changed (first enable, sample-rate or band-count change) → rebuild.
            createFilters(parametricEQ.bands)
            equalizerEnabled = true
            filters.forEach { it.reset() }
        }

        Timber.tag(TAG)
            .d("Applied EQ profile with ${filters.size} bands and ${parametricEQ.preamp} dB preamp")
    }

    
    @Synchronized
    fun disable() {
        equalizerEnabled = false
        filters = emptyList()
        preampGain = 1.0
        TruePeakLimiterAudioProcessor.eqMakeup = 1f
        pendingProfile = null
        Timber.tag(TAG).d("Equalizer disabled")
    }

    
    fun isEnabled(): Boolean = equalizerEnabled

    
    private fun createFilters(bands: List<ParametricEQBand>) {
        if (sampleRate == 0) {
            Timber.tag(TAG).w("Cannot create filters: sample rate not set")
            return
        }

        
        filters = bands
            .filter { it.enabled && it.frequency < sampleRate / 2.0 }
            .map { band ->
                BiquadFilter(
                    sampleRate = sampleRate,
                    frequency = band.frequency,
                    gain = band.gain,
                    q = band.q,
                    filterType = band.filterType
                )
            }

        Timber.tag(TAG)
            .d("Created ${filters.size} biquad filters from ${bands.size} bands (PK/LSC/HSC)")
    }

    override fun configure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        sampleRate = inputAudioFormat.sampleRate
        channelCount = inputAudioFormat.channelCount
        encoding = inputAudioFormat.encoding

        Timber.tag(TAG)
            .d("Configured: sampleRate=$sampleRate, channels=$channelCount, encoding=$encoding")

        
        pendingProfile?.let { profile ->
            preampGain = 10.0.pow(
                headroomPreampDb(profile.preamp, profile.bands.filter { it.enabled }.map { it.gain }) / 20.0,
            )
            TruePeakLimiterAudioProcessor.eqMakeup = dbToLinear(
                eqMakeupDb(profile.preamp, profile.bands.filter { it.enabled }.map { it.gain }),
            )
            createFilters(profile.bands)
            equalizerEnabled = true
            pendingProfile = null
            Timber.tag(TAG)
                .d("Applied pending profile with ${filters.size} bands and ${profile.preamp} dB preamp")
        }

        
        if (encoding != C.ENCODING_PCM_FLOAT || channelCount > 2) {
            // Self-bypass instead of crashing on >2ch / non-float (e.g. a 5.1 local file): Media3 skips an
            // inactive processor, so playback continues (unprocessed) rather than failing fatally.
            isActive = false
            return AudioProcessor.AudioFormat.NOT_SET
        }

        isActive = true
        return inputAudioFormat
    }

    override fun isActive(): Boolean = isActive

    override fun queueInput(inputBuffer: ByteBuffer) {
        if (!equalizerEnabled || filters.isEmpty()) {
            
            val remaining = inputBuffer.remaining()
            if (remaining == 0) return

            
            if (outputBuffer.capacity() < remaining) {
                outputBuffer = ByteBuffer.allocateDirect(remaining).order(ByteOrder.nativeOrder())
            } else {
                outputBuffer.clear()
            }
            outputBuffer.put(inputBuffer)
            outputBuffer.flip()
            return
        }

        val inputSize = inputBuffer.remaining()
        if (inputSize == 0) {
            return
        }

        
        
        if (outputBuffer === EMPTY_BUFFER || outputBuffer === inputBuffer) {
            
            outputBuffer = ByteBuffer.allocateDirect(inputSize).order(ByteOrder.nativeOrder())
        } else if (outputBuffer.capacity() < inputSize) {
            
            outputBuffer = ByteBuffer.allocateDirect(inputSize).order(ByteOrder.nativeOrder())
        } else {
            
            outputBuffer.clear()
        }

        
        when (encoding) {
            C.ENCODING_PCM_FLOAT -> {


                processAudioBufferFloat(inputBuffer, outputBuffer)
            }
            else -> {

                outputBuffer.put(inputBuffer)
            }
        }

        outputBuffer.flip()
        
    }

    
    private fun processAudioBuffer16Bit(input: ByteBuffer, output: ByteBuffer) {
        
        
        

        val sampleCount = input.remaining() / 2 

        repeat(sampleCount / channelCount) {
            when (channelCount) {
                1 -> {
                    
                    val sample = input.getShort().toDouble() / 32768.0 
                    var processed = sample

                    
                    for (filter in filters) {
                        processed = filter.processSample(processed)
                    }

                    
                    processed *= preampGain

                    
                    val outputSample = (softClip(processed) * 32768.0).coerceIn(-32768.0, 32767.0).toInt().toShort()
                    output.putShort(outputSample)
                }
                2 -> {
                    
                    val leftSample = input.getShort().toDouble() / 32768.0
                    val rightSample = input.getShort().toDouble() / 32768.0

                    var processedLeft = leftSample
                    var processedRight = rightSample

                    
                    for (filter in filters) {
                        val (left, right) = filter.processStereo(processedLeft, processedRight)
                        processedLeft = left
                        processedRight = right
                    }

                    
                    processedLeft *= preampGain
                    processedRight *= preampGain

                    
                    val outputLeft = (softClip(processedLeft) * 32768.0).coerceIn(-32768.0, 32767.0).toInt().toShort()
                    val outputRight = (softClip(processedRight) * 32768.0).coerceIn(-32768.0, 32767.0).toInt().toShort()

                    output.putShort(outputLeft)
                    output.putShort(outputRight)
                }
                else -> {

                    repeat(channelCount) {
                        output.putShort(input.getShort())
                    }
                }
            }
        }
    }

    /** FLOAT-in/FLOAT-out clone of [processAudioBuffer16Bit]. Identical preamp + biquad math; only the
     *  I/O boundary differs: 4 B/sample, reads getFloat(), writes putFloat(softClip(...)). softClip is
     *  load-bearing now (no 16-bit coerce safety net). */
    private fun processAudioBufferFloat(input: ByteBuffer, output: ByteBuffer) {
        val sampleCount = input.remaining() / 4

        repeat(sampleCount / channelCount) {
            when (channelCount) {
                1 -> {
                    val sample = input.getFloat().toDouble()
                    var processed = sample

                    for (filter in filters) {
                        processed = filter.processSample(processed)
                    }

                    processed *= preampGain

                    output.putFloat(softClip(processed).toFloat())
                }
                2 -> {
                    val leftSample = input.getFloat().toDouble()
                    val rightSample = input.getFloat().toDouble()

                    var processedLeft = leftSample
                    var processedRight = rightSample

                    for (filter in filters) {
                        val (left, right) = filter.processStereo(processedLeft, processedRight)
                        processedLeft = left
                        processedRight = right
                    }

                    processedLeft *= preampGain
                    processedRight *= preampGain

                    output.putFloat(softClip(processedLeft).toFloat())
                    output.putFloat(softClip(processedRight).toFloat())
                }
                else -> {
                    repeat(channelCount) {
                        output.putFloat(input.getFloat())
                    }
                }
            }
        }
    }

    override fun getOutput(): ByteBuffer {
        
        val buffer = outputBuffer
        outputBuffer = EMPTY_BUFFER
        return buffer
    }

    override fun isEnded(): Boolean {
        return inputEnded && outputBuffer.remaining() == 0
    }

    @Deprecated("Deprecated in Java")
    override fun flush() {
        outputBuffer = EMPTY_BUFFER
        inputEnded = false

        
        filters.forEach { it.reset() }
    }

    override fun reset() {
        @Suppress("DEPRECATION")
        flush()
        inputBuffer = EMPTY_BUFFER
        sampleRate = 0
        channelCount = 0
        encoding = C.ENCODING_INVALID
        isActive = false
        filters.forEach { it.reset() }
    }

    override fun queueEndOfStream() {
        inputEnded = true
    }
}
