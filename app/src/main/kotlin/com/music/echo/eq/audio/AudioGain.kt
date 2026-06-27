package iad1tya.echo.music.eq.audio

import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.tanh

/**
 * Pure audio-gain helpers (no Android/media3) so they can be unit-tested on the JVM.
 *
 * The signal path stays clean and clip-free (the "HiFi" goal): the EQ never boosts past 0 dBFS,
 * loud masters are attenuated to a reference, and quiet tracks are brought UP toward that reference
 * (loudness target, TIDAL-style) by a bounded makeup gain whose peaks are caught by the true-peak
 * limiter — so the output is loud and full without ever hard-clipping.
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
 * Loudness makeup gain (dB, >= 0) that brings a quiet track UP toward the reference loudness so it
 * plays as loud as a streaming service (TIDAL/Spotify-style), instead of being left quiet. [loudnessDb]
 * is the stream's loudness relative to reference (YouTube convention: normalize by `-loudnessDb`). Only
 * the positive (boost) part is returned here — attenuation of loud masters is handled separately by
 * [normalizationMultiplier]. Capped at [maxBoostDb] so a very quiet track isn't boosted so hard that it
 * over-drives the downstream true-peak limiter — too much makeup makes the limiter reduce gain heavily,
 * which sounds like harmonic saturation / a harsh, "boxy" voice, especially at max volume.
 *
 * Capped at [maxBoostDb] = +12 dB — the configuration the user confirmed sounded best (the v0.0.9
 * setup). The artifacts they later heard (saturation, then pumping) came from the per-download
 * loudness measurement added afterwards, not from this makeup, so that measurement was removed and
 * this stays at +12.
 */
fun loudnessMakeupDb(
    loudnessDb: Double?,
    enabled: Boolean,
    maxBoostDb: Double = 12.0,
): Double {
    if (!enabled || loudnessDb == null) return 0.0
    return (-loudnessDb).coerceIn(0.0, maxBoostDb)
}

/**
 * Loudness (dB vs reference) to assume for a track with NO loudness metadata. Many non-YouTube sources
 * (Saavn/Qobuz/local) don't report loudness; treating them as a typical loud master (positive value →
 * a small attenuation, never a boost) keeps them from playing noticeably LOUDER than normalized tracks,
 * so the whole library stays at a consistent volume.
 */
const val DEFAULT_UNKNOWN_LOUDNESS_DB: Double = 7.0

/**
 * The loudness value (dB vs reference) to normalize a track by: the real [loudnessDb] if present, else
 * [perceptualLoudnessDb], else [DEFAULT_UNKNOWN_LOUDNESS_DB]. Ensures EVERY track is normalized (none
 * plays at its raw, un-leveled volume).
 */
fun effectiveLoudnessDb(loudnessDb: Double?, perceptualLoudnessDb: Double?): Double =
    loudnessDb ?: perceptualLoudnessDb ?: DEFAULT_UNKNOWN_LOUDNESS_DB

/** Linear amplitude multiplier for a dB gain (e.g. -6 dB → 0.501, +6 dB → 1.995). */
fun dbToLinear(db: Double): Float = 10.0.pow(db / 20.0).toFloat()

/**
 * Makeup (dB, >= 0) that cancels the EQ's *auto*-headroom attenuation downstream, so boosting bands
 * no longer drops the overall volume. Equals how much [headroomPreampDb] pulled the signal below the
 * user's own pre-amp (= max positive band boost + 1 dB safety), capped at [maxCompDb]. The signal is
 * boosted back up after the EQ inside the true-peak limiter, which catches the boosted peaks → loud
 * EQ without clipping. The user's own pre-amp setting is preserved (not compensated).
 */
fun eqMakeupDb(
    userPreampDb: Double,
    enabledBandGainsDb: List<Double>,
    maxCompDb: Double = 12.0,
): Double {
    val comp = userPreampDb - headroomPreampDb(userPreampDb, enabledBandGainsDb)
    return comp.coerceIn(0.0, maxCompDb)
}

/**
 * Bounded soft limiter: transparent below [knee], then a `tanh` knee whose asymptote sits exactly at
 * [ceiling], so the output magnitude can NEVER exceed [ceiling] (no hard clip) yet stays clean. Used
 * by the true-peak limiter in an oversampled domain so inter-sample / treble transient peaks are
 * caught with minimal aliasing. [ceiling] defaults to ~-0.45 dBFS.
 */
fun softLimit(x: Float, ceiling: Float = 0.95f, knee: Float = 0.80f): Float {
    val ax = abs(x)
    if (ax <= knee) return x
    val range = ceiling - knee
    val comp = knee + range * tanh((ax - knee) / range)
    return if (x < 0f) -comp else comp
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
