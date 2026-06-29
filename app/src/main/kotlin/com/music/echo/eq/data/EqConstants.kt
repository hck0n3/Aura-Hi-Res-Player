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

/** Factory EQ presets — optimized for Audiophile/Superpowered sound signatures. */
enum class FactoryPreset(val displayName: String, val description: String, val gains: FloatArray) {
    FLAT("Bypass", "Sonido original sin alteraciones.", FloatArray(10) { 0f }),
    
    HARMAN_TARGET("Harman Target", "La curva perfecta de estudio. Sub-bajos presentes y agudos naturales.", floatArrayOf(4.5f, 3.5f, 1.0f, -0.5f, 0f, 0f, 1.5f, 2.5f, 1.0f, 0.5f)),
    
    AUDIOPHILE("Audiophile", "Referencia de monitor. Domestica frecuencias sucias, añade calidez y aire.", floatArrayOf(2.0f, 1.5f, -0.5f, -1.0f, 0f, 0f, 0.5f, 1.0f, 1.5f, 2.0f)),
    
    SPATIAL_AIR("Spatial & Air", "Maximiza la imagen estéreo y la separación de instrumentos.", floatArrayOf(1.0f, 0.5f, -1.5f, -2.0f, -1.0f, 0.5f, 1.0f, 1.5f, 2.5f, 4.0f)),
    
    SPARKLE_DETAIL("Sparkle & Detail", "Realza los micro-detalles sutiles sin generar fatiga auditiva.", floatArrayOf(0f, 0f, -0.5f, -1.0f, 0f, 0.5f, 1.5f, 2.5f, 3.5f, 4.5f)),
    
    DEEP_PUNCH("Deep Punch", "Bajos profundos y rápidos que no ahogan a los cantantes.", floatArrayOf(6.0f, 4.5f, 1.0f, -1.5f, -0.5f, 0f, 0.5f, 1.0f, 1.5f, 1.0f)),
    
    SUB_BASS_RUMBLE("Sub-Bass Rumble", "Solo levanta las frecuencias más profundas (31Hz). Ideal para cine y electrónica.", floatArrayOf(7.0f, 3.0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f)),
    
    VOCAL_PRESENCE("Vocal Presence", "Limpia el lodo musical y resalta específicamente la voz humana.", floatArrayOf(-1.0f, -0.5f, -1.5f, -0.5f, 1.0f, 3.0f, 3.5f, 2.0f, 0.5f, 0f)),
    
    ACOUSTIC_LIVE("Acoustic / Live", "Preserva el timbre orgánico de instrumentos como si fuera un concierto en vivo.", floatArrayOf(1.0f, 1.5f, 0.5f, -1.0f, 0f, 1.5f, 2.5f, 1.5f, 1.0f, 1.5f)),
    
    TUBE_AMP_WARMTH("Tube Amp", "Simula el sonido cálido y envolvente de un amplificador de tubos clásico.", floatArrayOf(-0.5f, 0.5f, 1.5f, 2.0f, 2.5f, 2.0f, 1.0f, 0.5f, -1.0f, -2.0f)),
    
    CINEMATIC_WARMTH("Cinematic Warmth", "Sonido denso y rico con agudos suaves. Para inmersión total.", floatArrayOf(3.0f, 2.5f, 2.0f, 1.0f, 0f, -0.5f, -1.0f, -1.0f, -1.5f, -2.0f)),
    
    LOW_VOLUME_LOUDNESS("Low Vol. Enhancer", "Compensa la pérdida de audición en bajos y agudos a volúmenes bajos.", floatArrayOf(5.0f, 4.0f, 2.0f, 0f, -1.0f, 0f, 1.0f, 2.5f, 3.5f, 4.0f))
}
