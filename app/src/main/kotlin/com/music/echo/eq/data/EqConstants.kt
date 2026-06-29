package iad1tya.echo.music.eq.data

/** Canonical 10-band ISO standard equalizer, optimized for 32-bit floating point processing. */
object EqConstants {
    val FREQUENCIES = doubleArrayOf(
        31.5, 62.5, 125.0, 250.0, 500.0, 1000.0, 2000.0, 4000.0, 8000.0, 16000.0
    )

    /** Compact axis labels aligned 1:1 with [FREQUENCIES]. */
    val FREQUENCY_LABELS = listOf(
        "31", "62", "125", "250", "500", "1k", "2k", "4k", "8k", "16k"
    )

    /** Musical octave Q for 10-band. */
    const val Q = 1.414
    const val BAND_COUNT = 10
    const val GAIN_MIN = -18f
    const val GAIN_MAX = 18f
    const val PREAMP_MIN = -20f
    const val PREAMP_MAX = 6f
}

/** Band filter shape — code matches desktop bandType (0=Peak, 1=LowShelf, 2=HighShelf). */
enum class EqBandType(val code: Int) {
    PEAK(0),
    LOW_SHELF(1),
    HIGH_SHELF(2);

    companion object {
        fun fromCode(code: Int): EqBandType = entries.firstOrNull { it.code == code } ?: PEAK
    }
}

/** Factory EQ presets — gain curves copied verbatim from desktop native_dsp_ui.js. */
enum class FactoryPreset(val displayName: String, val gains: FloatArray) {
    FLAT("Flat", FloatArray(10) { 0f }),
    BASS_BOOST("Bass Boost", floatArrayOf(6f, 4f, 2f, 0f, 0f, 0f, 0f, 0f, 0f, 0f)),
    TREBLE_BOOST("Treble+", floatArrayOf(0f, 0f, 0f, 0f, 0f, 0f, 1f, 2f, 3f, 4f)),
    V_SHAPE("V-Shape", floatArrayOf(5f, 3f, 1f, -1f, -2f, -1f, 1f, 2f, 3f, 4f)),
    VOCAL("Vocal", floatArrayOf(-1f, 0f, 1f, 2f, 3f, 4f, 3f, 2f, 1f, 0f)),
    CLASSICAL("Classical", floatArrayOf(3f, 2f, 1f, 0f, 0f, 0f, 1f, 1f, 2f, 2f)),
    JAZZ("Jazz", floatArrayOf(2f, 1f, 0f, -1f, -1f, 0f, 1f, 2f, 1f, 0f)),
}
