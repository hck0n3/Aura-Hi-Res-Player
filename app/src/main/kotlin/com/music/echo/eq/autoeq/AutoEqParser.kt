package iad1tya.echo.music.eq.autoeq

import kotlin.math.exp
import kotlin.math.ln

/** One parsed AutoEq peaking band. */
data class AutoEqBand(val fc: Double, val gainDb: Double, val q: Double)

/** A parsed AutoEq "ParametricEQ" profile: a global pre-amp plus peaking bands. */
data class AutoEqProfile(val preampDb: Double, val bands: List<AutoEqBand>)

/**
 * Pure parser for AutoEq ParametricEQ text (jaakkopasanen/AutoEq), e.g.:
 *
 * ```
 * Preamp: -6.7 dB
 * Filter 1: ON PK Fc 21 Hz Gain 5.5 dB Q 0.70
 * Filter 2: ON PK Fc 105 Hz Gain -2.1 dB Q 1.41
 * ```
 *
 * Only ON peaking (PK) filters are kept (the only type AutoEq emits for ParametricEQ).
 */
object AutoEqParser {

    private val preampRegex = Regex("""Preamp:\s*(-?\d+(?:\.\d+)?)\s*dB""", RegexOption.IGNORE_CASE)
    private val filterRegex = Regex(
        """Filter\s+\d+:\s*ON\s+PK\s+Fc\s+(\d+(?:\.\d+)?)\s*Hz\s+Gain\s+(-?\d+(?:\.\d+)?)\s*dB\s+Q\s+(\d+(?:\.\d+)?)""",
        RegexOption.IGNORE_CASE,
    )

    fun parse(text: String): AutoEqProfile {
        val preamp = preampRegex.find(text)?.groupValues?.get(1)?.toDoubleOrNull() ?: 0.0
        val bands = filterRegex.findAll(text).mapNotNull { m ->
            val fc = m.groupValues[1].toDoubleOrNull() ?: return@mapNotNull null
            val gain = m.groupValues[2].toDoubleOrNull() ?: return@mapNotNull null
            val q = m.groupValues[3].toDoubleOrNull() ?: return@mapNotNull null
            if (fc <= 0.0 || q <= 0.0) null else AutoEqBand(fc, gain, q)
        }.toList()
        return AutoEqProfile(preamp, bands)
    }
}

/**
 * Projects a parametric [profile] onto fixed graphic-EQ [frequencies] (Hz) by summing each peaking
 * band's contribution as a Gaussian bell in log-frequency (σ ≈ 1/(2·Q) octaves → full gain at Fc,
 * narrower for higher Q). A good approximation for mapping AutoEq PEQ onto a 1/3-octave graphic EQ.
 * Returned gains are clamped to [gainMin]..[gainMax].
 */
fun projectAutoEqToBands(
    profile: AutoEqProfile,
    frequencies: DoubleArray,
    gainMin: Float = -18f,
    gainMax: Float = 18f,
): FloatArray {
    val ln2 = ln(2.0)
    return FloatArray(frequencies.size) { i ->
        val f = frequencies[i]
        var sum = 0.0
        for (b in profile.bands) {
            val octaves = ln(f / b.fc) / ln2
            val sigma = 1.0 / (2.0 * b.q)          // bell width in octaves
            val weight = exp(-(octaves * octaves) / (2.0 * sigma * sigma))
            sum += b.gainDb * weight
        }
        sum.toFloat().coerceIn(gainMin, gainMax)
    }
}
