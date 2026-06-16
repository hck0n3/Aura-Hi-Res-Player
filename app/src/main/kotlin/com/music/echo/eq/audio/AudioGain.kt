package iad1tya.echo.music.eq.audio

import kotlin.math.pow

/**
 * Pure audio-gain helpers (no Android/media3) so they can be unit-tested on the JVM.
 *
 * Both exist to keep the signal path clean and clip-free (the "HiFi" goal): the EQ never boosts
 * past 0 dBFS, and loudness normalization only ever attenuates.
 */

/**
 * Effective EQ pre-amp (dB): the user's pre-amp minus the largest positive band gain (plus 1 dB
 * safety when any band boosts), so a boosted band can never push the signal above its original
 * level and hard-clip. Mirrors the AutoEq/Wavelet convention.
 */
fun headroomPreampDb(userPreampDb: Double, enabledBandGainsDb: List<Double>): Double {
    val maxBoost = (enabledBandGainsDb.maxOrNull() ?: 0.0).coerceAtLeast(0.0)
    return userPreampDb - maxBoost - if (maxBoost > 0.0) 1.0 else 0.0
}

/**
 * Attenuate-only loudness-normalization multiplier (linear, in (0, 1]). [loudnessDb] is the stream's
 * loudness relative to the reference (YouTube convention: apply `-loudnessDb` to normalize). We clamp
 * the gain to <= 0 dB so loud masters are tamed but nothing is ever boosted into clipping; quiet
 * tracks are left untouched. Floored at -[maxAttenuationDb].
 */
fun normalizationMultiplier(
    loudnessDb: Double?,
    enabled: Boolean,
    maxAttenuationDb: Double = 12.0,
): Float {
    if (!enabled || loudnessDb == null) return 1f
    val gainDb = (-loudnessDb).coerceIn(-maxAttenuationDb, 0.0)
    return 10.0.pow(gainDb / 20.0).toFloat()
}

/**
 * One reconstructed sample inside a clipped run: continues the pre-clip linear trajectory
 * ([anchorNewest] + slope * [stepsIntoClip]) so a flat clipped top is rounded into a peak instead of
 * a harsh corner. Bounded to +/-[maxOvershoot] so it can never run away (the limiter still caps
 * output). Mitigation only — hard clipping is destructive and cannot be fully restored in real time.
 */
fun declipSample(
    anchorNewest: Float,
    anchorPrev: Float,
    stepsIntoClip: Int,
    maxOvershoot: Float = 1.35f,
): Float {
    val slope = anchorNewest - anchorPrev
    return (anchorNewest + slope * stepsIntoClip).coerceIn(-maxOvershoot, maxOvershoot)
}
