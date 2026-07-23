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
     *  4 = Asymmetric rise (owner-tuned): BOTH directions are gradual — the outgoing decays over the
     *      WHOLE window on a smoothstep-eased equal-power cosine (gentle start, gentle landing), and the
     *      incoming rises over the first ~85% on a smoothstep-eased sine, so it is audibly "making
     *      itself noticed" for most of the blend and arrives at full level with ZERO slope (no audible
     *      "kink"). The asymmetry keeps the radio-segue "natural rise in intensity" feel. Never the
     *      default; selectable only.
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
            4 -> {
                // Smoothstep easing (3t²−2t³) has zero slope at both ends: the incoming ramp starts
                // gently AND lands gently at full level; the outgoing decays gently all the way out.
                // Rise spans ~85% of the window (owner-tuned): the incoming is audibly "making itself
                // noticed" for most of the blend before settling at full level near the end.
                val pin = (p / 0.85f).coerceAtMost(1f)
                val sIn = pin * pin * (3f - 2f * pin)
                val sOut = p * p * (3f - 2f * p)
                sin(sIn * half) to cos(sOut * half)
            }
            else -> p to (1f - p)
        }
    }
}
