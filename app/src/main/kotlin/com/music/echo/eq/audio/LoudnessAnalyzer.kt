package iad1tya.echo.music.eq.audio

import kotlin.math.PI
import kotlin.math.log10
import kotlin.math.tan

/**
 * ITU-R BS.1770 integrated loudness meter (the same measure Spotify/YouTube/TIDAL normalize to).
 *
 * Pure JVM math (no Android), so it's unit-testable: feed decoded PCM (float, interleaved, range
 * [-1, 1]) in chunks via [process], then read [integratedLufs]. Used to measure a DOWNLOADED track
 * once and store its loudness, so its volume normalization is accurate even when YouTube's metadata
 * is missing or off — without ever distorting (the true-peak limiter downstream still guards peaks).
 *
 * Implementation: per-channel K-weighting (stage-1 high shelf + stage-2 high-pass, designed for the
 * actual sample rate via bilinear transform), mean square over 400 ms blocks with 75 % overlap, then
 * absolute (-70 LUFS) + relative (-10 LU) gating, exactly as the standard specifies.
 */
class LoudnessAnalyzer(
    private val sampleRate: Int,
    channelCount: Int,
) {
    private val channels = channelCount.coerceIn(1, 2)

    // K-weighting biquads (one pair of filter states per channel).
    private val pre = Array(channels) { Biquad(stage1Coeffs(sampleRate)) }
    private val rlb = Array(channels) { Biquad(stage2Coeffs(sampleRate)) }

    private val blockSamples = (0.400 * sampleRate).toInt().coerceAtLeast(1) // 400 ms
    private val stepSamples = (blockSamples / 4).coerceAtLeast(1)            // 75 % overlap (100 ms)

    // Running sum of weighted squared, per current (overlapping) block window.
    private val ring = FloatArray(blockSamples)
    private var ringPos = 0
    private var filled = 0
    private var sinceStep = 0

    /** Mean-square (already channel-weighted) of every 400 ms block, used for the gated average. */
    private val blockMeanSquares = ArrayList<Double>()

    /** Feed interleaved PCM in [-1, 1]. [frames] = samples per channel actually present in [pcm]. */
    fun process(pcm: FloatArray, frames: Int) {
        var i = 0
        while (i < frames) {
            var weighted = 0.0
            for (c in 0 until channels) {
                val x = pcm[i * channels + c]
                val y = rlb[c].step(pre[c].step(x))
                weighted += (y * y).toDouble() // L/R weight = 1.0 in BS.1770
            }
            // Maintain a sliding 400 ms window as a ring buffer of per-frame weighted energy.
            val old = ring[ringPos]
            ring[ringPos] = weighted.toFloat()
            ringPos = (ringPos + 1) % blockSamples
            if (filled < blockSamples) filled++
            windowSum += weighted - old
            if (filled >= blockSamples) {
                sinceStep++
                if (sinceStep >= stepSamples) {
                    sinceStep = 0
                    blockMeanSquares.add(windowSum / blockSamples)
                }
            }
            i++
        }
    }

    private var windowSum = 0.0

    /**
     * Integrated loudness in LUFS over everything fed so far, or null if there isn't enough signal
     * (e.g. all silence / too short to form a single 400 ms block).
     */
    val integratedLufs: Double?
        get() {
            if (blockMeanSquares.isEmpty()) return null
            // Block loudness, absolute gate at -70 LUFS.
            val absKept = blockMeanSquares.filter { it > 0.0 && blockLoudness(it) > ABSOLUTE_GATE }
            if (absKept.isEmpty()) return null
            // Relative gate: mean of abs-kept blocks, minus 10 LU.
            val absMean = absKept.average()
            val relThreshold = blockLoudness(absMean) - 10.0
            val relKept = absKept.filter { blockLoudness(it) > relThreshold }
            val kept = if (relKept.isEmpty()) absKept else relKept
            return blockLoudness(kept.average())
        }

    private fun blockLoudness(meanSquare: Double): Double = -0.691 + 10.0 * log10(meanSquare)

    private class Biquad(c: DoubleArray) {
        private val b0 = c[0]; private val b1 = c[1]; private val b2 = c[2]
        private val a1 = c[3]; private val a2 = c[4]
        private var z1 = 0.0; private var z2 = 0.0
        fun step(x0: Float): Float {
            val x = x0.toDouble()
            val y = b0 * x + z1
            z1 = b1 * x - a1 * y + z2
            z2 = b2 * x - a2 * y
            return y.toFloat()
        }
    }

    companion object {
        private const val ABSOLUTE_GATE = -70.0 // LUFS

        /** Stage 1: high-shelf "pre-filter" (returns b0,b1,b2,a1,a2; a0 normalized to 1). */
        internal fun stage1Coeffs(fs: Int): DoubleArray {
            val f0 = 1681.9744509555319
            val g = 3.99984385397
            val q = 0.7071752369554193
            val k = tan(PI * f0 / fs)
            val vh = Math.pow(10.0, g / 20.0)
            val vb = Math.pow(vh, 0.4996667741545416)
            val a0 = 1.0 + k / q + k * k
            return doubleArrayOf(
                (vh + vb * k / q + k * k) / a0,
                2.0 * (k * k - vh) / a0,
                (vh - vb * k / q + k * k) / a0,
                2.0 * (k * k - 1.0) / a0,
                (1.0 - k / q + k * k) / a0,
            )
        }

        /** Stage 2: RLB high-pass. */
        internal fun stage2Coeffs(fs: Int): DoubleArray {
            val f0 = 38.13547087613982
            val q = 0.5003270373253953
            val k = tan(PI * f0 / fs)
            val a0 = 1.0 + k / q + k * k
            return doubleArrayOf(
                1.0,
                -2.0,
                1.0,
                2.0 * (k * k - 1.0) / a0,
                (1.0 - k / q + k * k) / a0,
            )
        }

        /**
         * Convert measured integrated loudness to the app's per-track `loudnessDb` convention (how far
         * the track is from the ~-14 LUFS streaming reference; positive = louder → attenuate, negative
         * = quieter → boost), matching what YouTube supplies. So a -20 LUFS track → -6 (needs +6 dB).
         */
        const val REFERENCE_LUFS = -14.0
        fun lufsToLoudnessDb(lufs: Double): Double = lufs - REFERENCE_LUFS
    }
}
