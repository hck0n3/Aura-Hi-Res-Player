package iad1tya.echo.music.playback

import kotlin.math.cos
import kotlin.math.pow
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
     *  5 = V-fade (sequential, Poweramp-style "fade out, then in"): the outgoing fades fully OUT over
     *      the first half, then the incoming fades fully IN over the second half — the two songs are
     *      NEVER audible at once, so key/tempo clashes are impossible by design. The handover is
     *      back-to-back (a=b=0.5): the instant the outgoing dies the incoming is born — no silence
     *      gap, so the never-silence invariant holds (the research's configurable-gap variant was
     *      deliberately dropped for that reason).
     *  6 = Logarithmic / dB-linear (R=60dB, ffmpeg "log" / audio taper): a straight line on the dB
     *      scale — the decay the ear perceives as EVEN (the classic mastering/radio fade). Each track
     *      sits near −30dB at the midpoint, so the songs barely overlap audibly: the old one "dies
     *      for real" and the new one is born. The −60dB floor is spliced out exactly (subtract and
     *      renormalize) so the endpoints land at exactly 0 and 1.
     *  7 = Dipped (parabolic, the classic Ableton/Serato crossfader "dipped" family): both gains are
     *      squares, so the summed amplitude dips to 0.5 (−6dB) at the center — an audible "breath"
     *      between songs without ever reaching silence. The middle ground between equal-power (no
     *      valley) and the V-fade (full valley). (Same math as curve 3; exposed under its canonical
     *      DJ name so the owner can A/B it by character.)
     *  8 = Equal-GAIN raised cosine ("hsin", Hann window): sin²/cos² — amplitudes sum to EXACTLY 1 at
     *      every point, with zero-slope ends (the gentlest possible start and landing). The only pair
     *      that can never bump on correlated material (same-take edits, versions/remixes); on
     *      uncorrelated songs it has a soft −3dB power valley mid-blend. The equal-gain sibling of
     *      curve 2's equal-power S — the most educational A/B in the set.
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
            5 -> {
                // Sequential V: g_out = cos(π/2·min(1, p/0.5)) dies at the midpoint; g_in =
                // sin(π/2·max(0, (p−0.5)/0.5)) is born there. No overlap, and no gap either.
                val fadeOut = cos(half * (p / 0.5f).coerceAtMost(1f))
                val fadeIn = sin(half * ((p - 0.5f) / 0.5f).coerceAtLeast(0f))
                fadeIn to fadeOut
            }
            6 -> {
                // dB-linear: raw g_out = 10^(−R·p/20) with R=60 → 10^(−3p); mirrored for g_in.
                // Exact endpoint splice: subtract the −60dB floor (10^−3) and renormalize so the
                // curve reaches exactly 0/1 at the ends (still monotonic, still dB-straight).
                val floor = 1e-3f // 10^(−60/20)
                val fadeOut = (10f.pow(-3f * p) - floor) / (1f - floor)
                val fadeIn = (10f.pow(-3f * (1f - p)) - floor) / (1f - floor)
                fadeIn to fadeOut
            }
            7 -> (p * p) to ((1f - p) * (1f - p))
            8 -> {
                // Raised cosine (Hann): sin²/cos² — exact unity amplitude sum, zero-slope ends.
                val s = sin(p * half)
                val c = cos(p * half)
                (s * s) to (c * c)
            }
            else -> p to (1f - p)
        }
    }
}
