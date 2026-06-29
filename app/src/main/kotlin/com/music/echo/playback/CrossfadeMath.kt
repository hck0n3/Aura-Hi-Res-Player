package iad1tya.echo.music.playback

import kotlin.math.cos
import kotlin.math.sin

object CrossfadeMath {

    /**
     * Gain pair (incoming, outgoing) for crossfade progress [p] in 0..1, per the selected style.
     *  0 = Linear: straight amplitude ramp (1 - p); amplitude sum never exceeds 1.0.
     *  1 = Smooth/equal-power (default): sin/cos keep incoming^2 + outgoing^2 = 1 (constant power), so
     *      both tracks carry the SAME power through the blend — the natural, even crossfade.
     *  2 = Long S-curve: equal-power but eased timing (very gradual in/out).
     *  3 = Exponential (quick): each track dominates its half, snappier handover.
     */
    fun getGains(curve: Int, p: Float): Pair<Float, Float> {
        val half = (Math.PI / 2.0).toFloat()
        return when (curve) {
            1 -> sin(p * half) to cos(p * half)
            2 -> {
                val s = p * p * (3f - 2f * p) // smoothstep
                sin(s * half) to cos(s * half)
            }
            3 -> (p * p) to ((1f - p) * (1f - p))
            else -> p to (1f - p)
        }
    }
}
