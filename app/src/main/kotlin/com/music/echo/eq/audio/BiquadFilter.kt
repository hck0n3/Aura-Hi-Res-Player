package iad1tya.echo.music.eq.audio

import iad1tya.echo.music.eq.data.FilterType
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt


class BiquadFilter(
    private val sampleRate: Int,
    private var frequency: Double,
    private var gain: Double,
    private var q: Double = 1.41,
    private var filterType: FilterType = FilterType.PK,
    // Shelf slope S for LSC/HSC (miniaudio ma_loshelf2/ma_hishelf2 shelfSlope). 1.0 = max slope.
    private val shelfSlope: Double = 1.0
) {
    
    private var a0 = 0.0
    private var a1 = 0.0
    private var a2 = 0.0
    private var b0 = 0.0
    private var b1 = 0.0
    private var b2 = 0.0

    
    private var x1L = 0.0
    private var x2L = 0.0
    private var y1L = 0.0
    private var y2L = 0.0

    private var x1R = 0.0
    private var x2R = 0.0
    private var y1R = 0.0
    private var y2R = 0.0

    // The 5 coefficients the AUDIO thread actually reads, published as ONE immutable object behind a single
    // @Volatile reference. calculateCoefficients() fills the scratch fields above then publishes a new Coeffs
    // in a single atomic write, so the audio thread can never read a half-updated set (a torn read could
    // momentarily place a pole outside the unit circle → the recursive filter self-oscillates → a loud burst).
    private class Coeffs(val b0: Double, val b1: Double, val b2: Double, val a1: Double, val a2: Double)

    @Volatile
    private var coeffs = Coeffs(1.0, 0.0, 0.0, 0.0, 0.0)

    // 0 dB bypass: a peaking/shelf band sitting at ~flat gain is mathematically a pass-through, so we
    // skip the per-sample biquad entirely (CPU saving + leaves the pure signal untouched). LPQ/HPQ are
    // NEVER bypassed — a 0 dB low/high-pass is still a real filter and ignores gain. Published per-sample
    // via the same @Volatile pattern as coeffs so the audio thread reads a consistent value.
    @Volatile
    private var bypass = false

    init {
        calculateCoefficients()
    }

    /**
     * Recomputes coefficients in place for new parameters while preserving the running filter
     * state (z delays). Lets the equalizer change gains/frequencies in real time with no audible
     * click and without interrupting playback.
     */
    fun update(frequency: Double, gain: Double, q: Double, filterType: FilterType) {
        this.frequency = frequency
        this.gain = gain
        this.q = q
        this.filterType = filterType
        calculateCoefficients()
    }

    
    private fun calculateCoefficients() {
        when (filterType) {
            FilterType.PK -> calculatePeakingCoefficients()
            FilterType.LSC -> calculateLowShelfCoefficients()
            FilterType.HSC -> calculateHighShelfCoefficients()
            FilterType.LPQ -> calculateLowPassCoefficients()
            FilterType.HPQ -> calculateHighPassCoefficients()
        }
        // Publish the freshly-computed set atomically for the audio thread.
        coeffs = Coeffs(b0, b1, b2, a1, a2)
        // Only PK/LSC/HSC honor gain; a |gain| < 0.05 dB peaking/shelf band is effectively flat → bypass.
        // LPQ/HPQ are excluded on purpose (they ignore gain and are real filters even at 0 dB).
        bypass = (filterType == FilterType.PK || filterType == FilterType.LSC || filterType == FilterType.HSC) &&
            kotlin.math.abs(gain) < 0.05
    }

    
    private fun calculatePeakingCoefficients() {
        val A = 10.0.pow(gain / 40.0) 
        val omega = 2.0 * PI * frequency / sampleRate
        val sinOmega = sin(omega)
        val cosOmega = cos(omega)
        val alpha = sinOmega / (2.0 * q)

        
        b0 = 1.0 + alpha * A
        b1 = -2.0 * cosOmega
        b2 = 1.0 - alpha * A
        a0 = 1.0 + alpha / A
        a1 = -2.0 * cosOmega
        a2 = 1.0 - alpha / A

        
        b0 /= a0
        b1 /= a0
        b2 /= a0
        a1 /= a0
        a2 /= a0
        a0 = 1.0
    }

    
    private fun calculateLowShelfCoefficients() {
        val A = sqrt(10.0.pow(gain / 20.0)) 
        val omega = 2.0 * PI * frequency / sampleRate
        val sinOmega = sin(omega)
        val cosOmega = cos(omega)
        val S = shelfSlope
        val alpha = sinOmega / 2.0 * sqrt((A + 1.0 / A) * (1.0 / S - 1.0) + 2.0)
        val sqrtA = sqrt(A)

        
        val aPlusOne = A + 1.0
        val aMinusOne = A - 1.0
        val twoSqrtAAlpha = 2.0 * sqrtA * alpha

        b0 = A * (aPlusOne - aMinusOne * cosOmega + twoSqrtAAlpha)
        b1 = 2.0 * A * (aMinusOne - aPlusOne * cosOmega)
        b2 = A * (aPlusOne - aMinusOne * cosOmega - twoSqrtAAlpha)
        a0 = aPlusOne + aMinusOne * cosOmega + twoSqrtAAlpha
        a1 = -2.0 * (aMinusOne + aPlusOne * cosOmega)
        a2 = aPlusOne + aMinusOne * cosOmega - twoSqrtAAlpha

        
        b0 /= a0
        b1 /= a0
        b2 /= a0
        a1 /= a0
        a2 /= a0
        a0 = 1.0
    }

    
    private fun calculateHighShelfCoefficients() {
        val A = sqrt(10.0.pow(gain / 20.0)) 
        val omega = 2.0 * PI * frequency / sampleRate
        val sinOmega = sin(omega)
        val cosOmega = cos(omega)
        val S = shelfSlope
        val alpha = sinOmega / 2.0 * sqrt((A + 1.0 / A) * (1.0 / S - 1.0) + 2.0)
        val sqrtA = sqrt(A)

        
        val aPlusOne = A + 1.0
        val aMinusOne = A - 1.0
        val twoSqrtAAlpha = 2.0 * sqrtA * alpha

        b0 = A * (aPlusOne + aMinusOne * cosOmega + twoSqrtAAlpha)
        b1 = -2.0 * A * (aMinusOne + aPlusOne * cosOmega)
        b2 = A * (aPlusOne + aMinusOne * cosOmega - twoSqrtAAlpha)
        a0 = aPlusOne - aMinusOne * cosOmega + twoSqrtAAlpha
        a1 = 2.0 * (aMinusOne - aPlusOne * cosOmega)
        a2 = aPlusOne - aMinusOne * cosOmega - twoSqrtAAlpha

        
        b0 /= a0
        b1 /= a0
        b2 /= a0
        a1 /= a0
        a2 /= a0
        a0 = 1.0
    }


    // RBJ low-pass — matches miniaudio ma_lpf2 (gain ignored).
    private fun calculateLowPassCoefficients() {
        val omega = 2.0 * PI * frequency / sampleRate
        val sinOmega = sin(omega)
        val cosOmega = cos(omega)
        val alpha = sinOmega / (2.0 * q)

        b0 = (1.0 - cosOmega) / 2.0
        b1 = 1.0 - cosOmega
        b2 = (1.0 - cosOmega) / 2.0
        a0 = 1.0 + alpha
        a1 = -2.0 * cosOmega
        a2 = 1.0 - alpha

        b0 /= a0
        b1 /= a0
        b2 /= a0
        a1 /= a0
        a2 /= a0
        a0 = 1.0
    }


    // RBJ high-pass — matches miniaudio ma_hpf2 (gain ignored).
    private fun calculateHighPassCoefficients() {
        val omega = 2.0 * PI * frequency / sampleRate
        val sinOmega = sin(omega)
        val cosOmega = cos(omega)
        val alpha = sinOmega / (2.0 * q)

        b0 = (1.0 + cosOmega) / 2.0
        b1 = -(1.0 + cosOmega)
        b2 = (1.0 + cosOmega) / 2.0
        a0 = 1.0 + alpha
        a1 = -2.0 * cosOmega
        a2 = 1.0 - alpha

        b0 /= a0
        b1 /= a0
        b2 /= a0
        a1 /= a0
        a2 /= a0
        a0 = 1.0
    }


    fun processSample(input: Double): Double {
        // Flat PK/LSC/HSC band → pass-through. Still advance the z-state with the input so the delay line
        // stays continuous; un-bypassing mid-playback then resumes without a pop.
        if (bypass) {
            x2L = x1L
            x1L = input
            y2L = y1L
            y1L = input
            return input
        }
        val c = coeffs // snapshot the consistent coefficient set once
        val output = c.b0 * input + c.b1 * x1L + c.b2 * x2L - c.a1 * y1L - c.a2 * y2L


        x2L = x1L
        x1L = input
        y2L = y1L
        y1L = output

        return output
    }

    
    fun processStereo(inputLeft: Double, inputRight: Double): Pair<Double, Double> {
        // Flat PK/LSC/HSC band → pass-through on both channels. Advance each channel's z-state with its own
        // input so the delay lines stay continuous (no pop when un-bypassed mid-playback).
        if (bypass) {
            x2L = x1L
            x1L = inputLeft
            y2L = y1L
            y1L = inputLeft

            x2R = x1R
            x1R = inputRight
            y2R = y1R
            y1R = inputRight

            return Pair(inputLeft, inputRight)
        }
        val c = coeffs // snapshot once so L and R use the SAME consistent coefficient set
        val outputLeft = c.b0 * inputLeft + c.b1 * x1L + c.b2 * x2L - c.a1 * y1L - c.a2 * y2L
        x2L = x1L
        x1L = inputLeft
        y2L = y1L
        y1L = outputLeft


        val outputRight = c.b0 * inputRight + c.b1 * x1R + c.b2 * x2R - c.a1 * y1R - c.a2 * y2R
        x2R = x1R
        x1R = inputRight
        y2R = y1R
        y1R = outputRight

        return Pair(outputLeft, outputRight)
    }

    
    fun reset() {
        x1L = 0.0
        x2L = 0.0
        y1L = 0.0
        y2L = 0.0
        x1R = 0.0
        x2R = 0.0
        y1R = 0.0
        y2R = 0.0
    }
}