package iad1tya.echo.music.eq.data

/** Canonical 24-band ISO 1/3-octave EQ data — matches desktop JR DSP Pro exactly. */
object EqConstants {
    val FREQUENCIES = doubleArrayOf(
        20.0, 31.0, 50.0, 80.0, 100.0, 160.0, 200.0, 315.0,
        440.0, 500.0, 800.0, 1000.0, 1600.0, 2000.0, 2500.0, 3150.0,
        4000.0, 5000.0, 6300.0, 8000.0, 10000.0, 12500.0, 16000.0, 20000.0
    )

    /** Compact axis labels aligned 1:1 with [FREQUENCIES]. */
    val FREQUENCY_LABELS = listOf(
        "20", "31", "50", "80", "100", "160", "200", "315",
        "440", "500", "800", "1k", "1.6k", "2k", "2.5k", "3.15k",
        "4k", "5k", "6.3k", "8k", "10k", "12.5k", "16k", "20k"
    )

    /** ISO 1/3-octave Q = 1/(2^(1/6) - 2^(-1/6)). */
    const val Q = 4.318
    const val BAND_COUNT = 24
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
    FLAT("Flat", FloatArray(24) { 0f }),
    BASS_BOOST("Bass Boost", floatArrayOf(6f, 5f, 4f, 3f, 2f, 1f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f)),
    TREBLE_BOOST("Treble+", floatArrayOf(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 1f, 1f, 2f, 2f, 3f, 4f, 5f, 4f, 3f)),
    V_SHAPE("V-Shape", floatArrayOf(5f, 4f, 3f, 2f, 1f, 0f, -1f, -2f, -2f, -2f, -2f, -1f, 0f, 0f, 0f, 0f, 0f, 0f, 1f, 1f, 2f, 3f, 4f, 3f)),
    VOCAL("Vocal", floatArrayOf(-1f, -1f, 0f, 0f, 1f, 1f, 1f, 2f, 2f, 3f, 3f, 4f, 4f, 3f, 3f, 2f, 2f, 2f, 1f, 1f, 0f, 0f, 0f, 0f)),
    CLASSICAL("Classical", floatArrayOf(3f, 3f, 2f, 1f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 1f, 1f, 1f, 1f, 2f, 2f, 2f, 2f, 1f, 0f)),
    JAZZ("Jazz", floatArrayOf(2f, 2f, 2f, 1f, 1f, 0f, 0f, -1f, -1f, -1f, -1f, 0f, 0f, 1f, 1f, 2f, 2f, 2f, 1f, 1f, 1f, 1f, 0f, -1f)),
}
