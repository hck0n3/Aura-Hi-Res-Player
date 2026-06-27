package iad1tya.echo.music.eq.audio

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.util.UnstableApi
import iad1tya.echo.music.eq.data.FilterType
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.pow

/**
 * Aura Hi-Res Player post-EQ DSP effects (port of the native C++ `data_callback`, partial).
 *
 * Implements the optional, user-toggled effects that need no extra biquad filters:
 * loudness shelves → bass enhance → harmonic exciter → multiband compressor → stereo width →
 * dialogue. Loudness makeup and the true-peak limiter now live downstream in
 * [TruePeakLimiterAudioProcessor]; this stage only applies a transparent safety [softLimit] at its
 * 16-bit output so an effect's own overshoot can't hard-clip before the limiter sees it.
 *
 * Runs after the EQ processor. Stereo-only effects pass mono through (only loudness applies to mono).
 * Configured via the static [config] flag.
 */
@UnstableApi
class JrDspAudioProcessor : AudioProcessor {

    private var sampleRate = 0
    private var channelCount = 0
    private var encoding = C.ENCODING_INVALID
    private var isActive = false

    private var outputBuffer: ByteBuffer = EMPTY_BUFFER
    private var inputEnded = false

    /** Per-instance override of the shared [config]. When non-null it takes precedence over the
     *  companion default, so the main player and the crossfade secondary player can run different
     *  DSP configs during a crossfade. Null → use the shared companion [config]. */
    @Volatile
    var instanceConfig: Config? = null

    /** Flush denormal (subnormal) magnitudes to zero so recursive feedback state can't sit at a
     *  denormal value (10-100x slower on ARM). Applied only to persistent one-pole/feedback state. */
    private fun fl(v: Float): Float = if (v < 1e-15f && v > -1e-15f) 0f else v

    // ── Per-channel one-pole filter state ──
    private var bassLpL = 0f
    private var bassLpR = 0f
    private var bassEnhLpL = 0f
    private var bassEnhLpR = 0f
    private var bassEnhHpL = 0f
    private var bassEnhHpR = 0f
    private var bassEnhLastL = 0f
    private var bassEnhLastR = 0f
    private var exciterLpL = 0f
    private var exciterLpR = 0f

    // ── Loudness compensation shelves (Fletcher-Munson), built on configure ──
    private var loudnessLoShelf: BiquadFilter? = null
    private var loudnessHiShelf: BiquadFilter? = null

    // ── "Aura signature" gentle tone (body + air), ON by default, built on configure ──
    private var sigLoShelf: BiquadFilter? = null
    private var sigHiShelf: BiquadFilter? = null
    // -1 dB. Cancels the +1 dB max signature shelf boost so the (ON-by-default) Aura signature can NEVER push
    // peaks above the input peak — otherwise, on already-hot masters, the overshoot hit the local soft-clip and
    // added harmonics (the raspy/saturated voice). The ~1 dB is restored by the downstream loudness makeup.
    private val SIG_TRIM = 0.8913

    // ── Multiband compressor (3-band LR2 crossover) state, built on configure ──
    private var mbLpf1: BiquadFilter? = null   // LP 200 Hz  (bass)
    private var mbHpf1: BiquadFilter? = null   // HP 200 Hz
    private var mbLpf2: BiquadFilter? = null   // LP 5000 Hz (mid)
    private var mbHpf2: BiquadFilter? = null   // HP 5000 Hz (high)
    private val mbEnv = FloatArray(3)
    private var mbAttCoeff = 0f
    private var mbRelCoeff = 0f

    // ── Dialogue enhancer band-pass (mono), built on configure ──
    private var dialogueHpf: BiquadFilter? = null  // HP 300 Hz
    private var dialogueLpf: BiquadFilter? = null  // LP 3000 Hz

    // ── HRTF binaural virtual room (ITD delay + head-shadow LP + Schroeder room) ──
    private val hrtfBufL = FloatArray(HRTF_DELAY_MAX)
    private val hrtfBufR = FloatArray(HRTF_DELAY_MAX)
    private var hrtfBufPos = 0
    private var hrtfDelaySamp = 28      // contralateral ITD (~0.63 ms)
    private var hrtfShadAlpha = 0.10f   // head-shadow LP (fc ≈ 700 Hz)
    private var hrtfShadLpL = 0f
    private var hrtfShadLpR = 0f
    private val roomBufL = FloatArray(ROOM_DELAY_MAX)
    private val roomBufR = FloatArray(ROOM_DELAY_MAX)
    private var roomBufPos = 0
    private var roomDelayL = 0          // ~12 ms early reflection
    private var roomDelayR = 0          // ~19 ms early reflection
    private var roomLpStateL = 0f
    private var roomLpStateR = 0f

    // ── Coefficients (recomputed on configure from sample rate) ──
    private var bassLpAlpha = 0f   // fc 150 Hz
    private var bassLp2Alpha = 0f  // fc 180 Hz
    private var bassHpAlpha = 0f   // fc 60 Hz
    private var exciterAlpha = 0f  // fc 6 kHz

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
        /** True when at least one effect would alter the signal. (Loudness makeup + true-peak
         *  limiting live in [TruePeakLimiterAudioProcessor], not here.) */
        val anyActive: Boolean
            get() = signatureEnabled || loudnessEnabled || hrtfEnabled || bassEnhanceEnabled ||
                exciterEnabled || mbCompEnabled || dialogueEnabled ||
                (stereoWidthEnabled && stereoWidth != 1.0f)
    }

    companion object {
        private val EMPTY_BUFFER: ByteBuffer = ByteBuffer.allocateDirect(0).order(ByteOrder.nativeOrder())

        // Multiband compressor fixed params (linear RMS thresholds + ratios per band: bass/mid/high).
        // High band has a lower threshold + higher ratio so sustained treble (sibilance, cymbals) is
        // tamed more firmly. No makeup gain — loudness is owned by the loudness/limiter stage now.
        private val MB_THRESH = floatArrayOf(0.10f, 0.16f, 0.08f)
        private val MB_RATIO = floatArrayOf(2.5f, 1.8f, 3.2f)

        // HRTF ring-buffer sizes (samples) — match desktop jr_audio_engine.
        private const val HRTF_DELAY_MAX = 96
        private const val ROOM_DELAY_MAX = 1024

        /** Updated from MusicService when any DSP-effect preference changes. */
        @Volatile
        var config: Config = Config()
    }

    override fun configure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        sampleRate = inputAudioFormat.sampleRate
        channelCount = inputAudioFormat.channelCount
        encoding = inputAudioFormat.encoding

        if (encoding != C.ENCODING_PCM_16BIT || channelCount > 2) {
            // Self-bypass instead of crashing on >2ch / non-16-bit (e.g. a 5.1 local file): Media3 skips an
            // inactive processor, so playback continues (unprocessed) rather than failing fatally.
            isActive = false
            return AudioProcessor.AudioFormat.NOT_SET
        }

        val fs = sampleRate.toFloat()
        bassLpAlpha = (1.0 - exp(-2.0 * Math.PI * 150.0 / fs)).toFloat()
        bassLp2Alpha = (1.0 - exp(-2.0 * Math.PI * 180.0 / fs)).toFloat()
        bassHpAlpha = exp(-2.0 * Math.PI * 60.0 / fs).toFloat()
        exciterAlpha = (1.0 - exp(-2.0 * Math.PI * 6000.0 / fs)).toFloat()

        // Fletcher-Munson base curve (att=0): lo +3 dB @200 Hz, hi +2 dB @5 kHz, slope 0.707.
        loudnessLoShelf = BiquadFilter(sampleRate, 200.0, 3.0, 0.707, FilterType.LSC, shelfSlope = 0.707)
        loudnessHiShelf = BiquadFilter(sampleRate, 5000.0, 2.0, 0.707, FilterType.HSC, shelfSlope = 0.707)

        // Aura signature: very gentle body (+1 dB low shelf @100 Hz) + air (+0.6 dB high shelf
        // @12 kHz), a soft house curve. Kept low on purpose: on already-hot, high-quality masters a
        // bigger boost pushed bass/treble peaks into the limiter (and the device amp at max volume),
        // which audibly distorted. ON by default but near-transparent.
        sigLoShelf = BiquadFilter(sampleRate, 100.0, 1.0, 0.707, FilterType.LSC, shelfSlope = 0.707)
        sigHiShelf = BiquadFilter(sampleRate, 12000.0, 0.6, 0.707, FilterType.HSC, shelfSlope = 0.707)

        // Multiband compressor LR2 crossovers (Q 0.5) at 200 Hz and 5 kHz.
        mbLpf1 = BiquadFilter(sampleRate, 200.0, 0.0, 0.5, FilterType.LPQ)
        mbHpf1 = BiquadFilter(sampleRate, 200.0, 0.0, 0.5, FilterType.HPQ)
        mbLpf2 = BiquadFilter(sampleRate, 5000.0, 0.0, 0.5, FilterType.LPQ)
        mbHpf2 = BiquadFilter(sampleRate, 5000.0, 0.0, 0.5, FilterType.HPQ)
        mbAttCoeff = exp(-1.0 / (0.010 * fs)).toFloat()  // 10 ms attack
        mbRelCoeff = exp(-1.0 / (0.150 * fs)).toFloat()  // 150 ms release

        // Dialogue enhancer center band-pass: 300 Hz – 3 kHz (Q 0.707).
        dialogueHpf = BiquadFilter(sampleRate, 300.0, 0.0, 0.707, FilterType.HPQ)
        dialogueLpf = BiquadFilter(sampleRate, 3000.0, 0.0, 0.707, FilterType.LPQ)

        // HRTF: ITD ~0.63 ms, head-shadow LP fc 700 Hz, room reflections 12/19 ms.
        hrtfDelaySamp = minOf(HRTF_DELAY_MAX - 1, (0.00063 * fs).toInt())
        hrtfShadAlpha = (1.0 - exp(-6.2832 * 700.0 / fs)).toFloat()
        roomDelayL = minOf(ROOM_DELAY_MAX - 1, (0.012 * fs).toInt())
        roomDelayR = minOf(ROOM_DELAY_MAX - 1, (0.019 * fs).toInt())
        resetState()

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

        val cfg = instanceConfig ?: config
        if (!cfg.anyActive) {
            outputBuffer.put(inputBuffer)
            outputBuffer.flip()
            return
        }

        if (channelCount == 2) {
            val frames = remaining / 4 // 2 ch * 2 bytes
            repeat(frames) {
                var l = inputBuffer.getShort().toFloat() / 32768.0f
                var r = inputBuffer.getShort().toFloat() / 32768.0f

                if (cfg.signatureEnabled) {
                    val (ll, rr) = sigLoShelf!!.processStereo(l.toDouble(), r.toDouble())
                    val (hl, hr) = sigHiShelf!!.processStereo(ll, rr)
                    l = (hl * SIG_TRIM).toFloat()
                    r = (hr * SIG_TRIM).toFloat()
                }

                if (cfg.loudnessEnabled) {
                    val (ll, rr) = loudnessLoShelf!!.processStereo(l.toDouble(), r.toDouble())
                    val (hl, hr) = loudnessHiShelf!!.processStereo(ll, rr)
                    l = hl.toFloat()
                    r = hr.toFloat()
                }

                if (cfg.hrtfEnabled) {
                    hrtfBufL[hrtfBufPos] = l
                    hrtfBufR[hrtfBufPos] = r

                    // Schroeder early reflections with damped feedback.
                    val rDelLPos = (roomBufPos - roomDelayL + ROOM_DELAY_MAX) % ROOM_DELAY_MAX
                    val rDelRPos = (roomBufPos - roomDelayR + ROOM_DELAY_MAX) % ROOM_DELAY_MAX
                    val rawRefL = roomBufL[rDelLPos]
                    val rawRefR = roomBufR[rDelRPos]
                    roomLpStateL = fl(0.5f * (rawRefL * 0.35f) + 0.5f * roomLpStateL)
                    roomLpStateR = fl(0.5f * (rawRefR * 0.35f) + 0.5f * roomLpStateR)
                    roomBufL[roomBufPos] = l + roomLpStateL
                    roomBufR[roomBufPos] = r + roomLpStateR

                    // Contralateral path: opposite-ear delayed signal through head-shadow LP.
                    val delPos = (hrtfBufPos - hrtfDelaySamp + HRTF_DELAY_MAX) % HRTF_DELAY_MAX
                    val delR = hrtfBufL[delPos]
                    val delL = hrtfBufR[delPos]
                    hrtfShadLpL = fl(hrtfShadAlpha * delL + (1.0f - hrtfShadAlpha) * hrtfShadLpL)
                    hrtfShadLpR = fl(hrtfShadAlpha * delR + (1.0f - hrtfShadAlpha) * hrtfShadLpR)

                    val refL = rawRefL * 0.20f + rawRefR * 0.15f
                    val refR = rawRefR * 0.20f + rawRefL * 0.15f

                    // 66% direct + 20% contralateral shadow + 14% room reflections.
                    l = l * 0.66f + hrtfShadLpL * 0.20f + refL * 0.14f
                    r = r * 0.66f + hrtfShadLpR * 0.20f + refR * 0.14f

                    hrtfBufPos = (hrtfBufPos + 1) % HRTF_DELAY_MAX
                    roomBufPos = (roomBufPos + 1) % ROOM_DELAY_MAX
                }

                if (cfg.bassEnhanceEnabled) {
                    bassLpL = fl(bassLpAlpha * l + (1f - bassLpAlpha) * bassLpL)
                    bassLpR = fl(bassLpAlpha * r + (1f - bassLpAlpha) * bassLpR)
                    val enhL = bassLpL * abs(bassLpL) * 6f
                    val enhR = bassLpR * abs(bassLpR) * 6f
                    bassEnhLpL = fl(bassLp2Alpha * enhL + (1f - bassLp2Alpha) * bassEnhLpL)
                    bassEnhLpR = fl(bassLp2Alpha * enhR + (1f - bassLp2Alpha) * bassEnhLpR)
                    bassEnhHpL = fl(bassHpAlpha * (bassEnhHpL + bassEnhLpL - bassEnhLastL))
                    bassEnhHpR = fl(bassHpAlpha * (bassEnhHpR + bassEnhLpR - bassEnhLastR))
                    bassEnhLastL = bassEnhLpL
                    bassEnhLastR = bassEnhLpR
                    l += bassEnhHpL * cfg.bassEnhanceAmount
                    r += bassEnhHpR * cfg.bassEnhanceAmount
                }

                if (cfg.exciterEnabled) {
                    exciterLpL = fl(exciterAlpha * l + (1f - exciterAlpha) * exciterLpL)
                    exciterLpR = fl(exciterAlpha * r + (1f - exciterAlpha) * exciterLpR)
                    val hpL = l - exciterLpL
                    val hpR = r - exciterLpR
                    val satL = hpL * 0.1f + max(0f, hpL) * hpL * 3.5f
                    val satR = hpR * 0.1f + max(0f, hpR) * hpR * 3.5f
                    l += satL * cfg.exciterAmount
                    r += satR * cfg.exciterAmount
                }

                if (cfg.mbCompEnabled) {
                    // Split into bass/mid/high bands (LR2 crossovers from the same input).
                    val (bL, bR) = mbLpf1!!.processStereo(l.toDouble(), r.toDouble())
                    val (m1L, m1R) = mbHpf1!!.processStereo(l.toDouble(), r.toDouble())
                    val (mL, mR) = mbLpf2!!.processStereo(m1L, m1R)
                    val (hL, hR) = mbHpf2!!.processStereo(l.toDouble(), r.toDouble())

                    val g0 = mbBandGain(0, bL, bR)
                    val g1 = mbBandGain(1, mL, mR)
                    val g2 = mbBandGain(2, hL, hR)

                    // LR2 sum: mid is phase-inverted to cancel crossover phase issues.
                    l = (bL * g0 - mL * g1 + hL * g2).toFloat()
                    r = (bR * g0 - mR * g1 + hR * g2).toFloat()
                }

                if (cfg.stereoWidthEnabled && cfg.stereoWidth != 1.0f) {
                    val m = (l + r) * 0.5f
                    val s = (l - r) * 0.5f * cfg.stereoWidth
                    l = m + s
                    r = m - s
                }

                if (cfg.dialogueEnabled) {
                    val mono = (l + r) * 0.5
                    val hp = dialogueHpf!!.processSample(mono)
                    val band = dialogueLpf!!.processSample(hp).toFloat()
                    l += band * cfg.dialogueAmount
                    r += band * cfg.dialogueAmount
                }

                // Transparent safety only: keeps an effect's overshoot from hard-clipping at the
                // 16-bit output (asymptote 0.99, knee 0.88 — gentle, fewer harmonics). Real limiting is downstream.
                l = softLimit(l, ceiling = 0.99f, knee = 0.88f)
                r = softLimit(r, ceiling = 0.99f, knee = 0.88f)

                outputBuffer.putShort((l * 32768.0f).coerceIn(-32768.0f, 32767.0f).toInt().toShort())
                outputBuffer.putShort((r * 32768.0f).coerceIn(-32768.0f, 32767.0f).toInt().toShort())
            }
            while (inputBuffer.hasRemaining()) outputBuffer.put(inputBuffer.get())
        } else {
            // Mono: only loudness shelves apply (stereo effects pass through).
            val samples = remaining / 2
            repeat(samples) {
                var x = inputBuffer.getShort().toFloat() / 32768.0f
                if (cfg.signatureEnabled) {
                    x = sigLoShelf!!.processSample(x.toDouble()).toFloat()
                    x = (sigHiShelf!!.processSample(x.toDouble()) * SIG_TRIM).toFloat()
                }
                if (cfg.loudnessEnabled) {
                    x = loudnessLoShelf!!.processSample(x.toDouble()).toFloat()
                    x = loudnessHiShelf!!.processSample(x.toDouble()).toFloat()
                }
                x = softLimit(x, ceiling = 0.99f, knee = 0.88f)
                outputBuffer.putShort((x * 32768.0f).coerceIn(-32768.0f, 32767.0f).toInt().toShort())
            }
            while (inputBuffer.hasRemaining()) outputBuffer.put(inputBuffer.get())
        }

        outputBuffer.flip()
    }

    /** Envelope-follow + downward compression gain for one MB band (transparent, no makeup). */
    private fun mbBandGain(band: Int, l: Double, r: Double): Float {
        val lvl = max(abs(l), abs(r)).toFloat()
        mbEnv[band] = fl(if (lvl > mbEnv[band]) {
            mbAttCoeff * mbEnv[band] + (1f - mbAttCoeff) * lvl
        } else {
            mbRelCoeff * mbEnv[band]
        })
        return if (mbEnv[band] > MB_THRESH[band]) {
            (MB_THRESH[band] / mbEnv[band]).toDouble().pow(1.0 - 1.0 / MB_RATIO[band]).toFloat()
        } else {
            1f
        }
    }

    private fun resetState() {
        bassLpL = 0f; bassLpR = 0f
        bassEnhLpL = 0f; bassEnhLpR = 0f
        bassEnhHpL = 0f; bassEnhHpR = 0f
        bassEnhLastL = 0f; bassEnhLastR = 0f
        exciterLpL = 0f; exciterLpR = 0f
        loudnessLoShelf?.reset()
        loudnessHiShelf?.reset()
        sigLoShelf?.reset()
        sigHiShelf?.reset()
        mbLpf1?.reset(); mbHpf1?.reset(); mbLpf2?.reset(); mbHpf2?.reset()
        mbEnv[0] = 0f; mbEnv[1] = 0f; mbEnv[2] = 0f
        dialogueHpf?.reset(); dialogueLpf?.reset()
        hrtfBufL.fill(0f); hrtfBufR.fill(0f); hrtfBufPos = 0
        hrtfShadLpL = 0f; hrtfShadLpR = 0f
        roomBufL.fill(0f); roomBufR.fill(0f); roomBufPos = 0
        roomLpStateL = 0f; roomLpStateR = 0f
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
        resetState()
    }

    override fun reset() {
        @Suppress("DEPRECATION")
        flush()
        sampleRate = 0
        channelCount = 0
        encoding = C.ENCODING_INVALID
        isActive = false
    }
}
