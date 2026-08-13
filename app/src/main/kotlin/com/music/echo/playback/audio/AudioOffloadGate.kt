package iad1tya.echo.music.playback.audio

/**
 * The decision "may this playback use the AudioTrack OFFLOAD path?", as a pure function.
 *
 * WHY THIS IS A GATE AT ALL. Offload hands the still-ENCODED stream to the audio DSP hardware: the
 * decoder stops producing PCM on the CPU, the app's `AudioProcessor` chain is never built, and the
 * CPU sleeps between buffers. That is the single biggest screen-off battery saving available to a
 * music player — and it is also, exactly, a state in which NOTHING of ours can touch the samples.
 * So the rule is one-directional and non-negotiable:
 *
 *   offload is allowed ONLY when the chain has nothing to do — i.e. genuinely bit-perfect passthrough.
 *
 * If any live stage wants the samples, offload must stay OFF, even at the cost of battery. Getting
 * this wrong the other way (offload engaged while the EQ is on) makes the EQ INAUDIBLE while its
 * screen keeps drawing the curve — a placebo, and a far worse bug than warmth.
 *
 * WHAT COUNTS AS "LIVE", verified against the native bridge (`SuperpoweredBridge.cpp`, `processAudio`)
 * rather than against the settings screen:
 *
 *  - The whole DSP block is skipped — pure memcpy passthrough — when
 *    `!runEq && !activeSafeVolumeEnabled && safeVolumeGainCurrent == 1.0f && !activeSpatialEnabled`.
 *    That is the ONLY state in which playback is bit-identical, and it is precisely the state this
 *    gate opens on.
 *  - `runEq` is [Inputs.equalizerActive]. Note what rides inside it: the EQ bands, the preamp
 *    (`frontGain = activePreampMultiplier`, applied only inside the block), the DE-ESSER (nested
 *    inside the `runEq` branch), the -3 dBFS limiter and the 0.95 soft-clip knee. So a single "is an
 *    EQ profile applied" term already covers the preamp and the de-esser; there is no separate
 *    toggle for either, and a FLAT profile still enables the de-esser, which is why "profile present"
 *    (not "profile audibly non-flat") is the honest term.
 *  - Safe Volume is its own native stage plus the limiter, so it vetoes on its own.
 *  - Crossfade needs two live ExoPlayers, per-buffer gain ramps AND the sink-level PCM tap that the
 *    level-based segue reads — none of which exist on a stream that reaches the sink still encoded.
 *  - Silence/tail detection needs no term of its own: `tailDetectEnabled` is armed exclusively by
 *    `scheduleCrossfade`, downstream of its `if (!crossfadeEnabled ...) return` guard, and the
 *    instant skip-silence mode is hardcoded off. Tail detection therefore CANNOT be running while
 *    the crossfade term is false. (Stated here so a future change that arms tail detection without
 *    crossfade knows it has to add a term.)
 *
 * WHAT THIS OBJECT DELIBERATELY DOES NOT DECIDE: the offload REQUEST's capability flags (gapless
 * always, speed change while tempo/pitch are non-default) live in `Player.setOffloadEnabled`, which
 * derives them from the player's own live state. Those can only make media3 REFUSE offload, never
 * grant it where this gate said no.
 */
object AudioOffloadGate {

    /**
     * Everything the decision reads. All five are settings/DSP state that can change MID-SESSION, so
     * the caller must feed this from hot flows and re-publish on every change — a gate evaluated only
     * at startup would leave offload engaged when the user turns the EQ on mid-song.
     */
    data class Inputs(
        /** The user's "Activar descarga (offload)" switch. Never enough on its own. */
        val userWantsOffload: Boolean,
        /** `CrossfadeEnabledKey`. Not the same thing as crossfade actually RUNNING — see [crossfadeRunning]. */
        val crossfadeEnabled: Boolean,
        /**
         * `HighPerformanceModeKey`. Perf mode force-disables crossfade at every live site, so the key
         * alone is a DEAD veto under it — on exactly the weak devices offload helps most.
         */
        val highPerformanceMode: Boolean,
        /** `SafeVolumeEnabledKey` (default ON): a live native levelling stage + limiter. */
        val safeVolumeEnabled: Boolean,
        /**
         * An EQ profile is applied to the DSP, i.e. `unsavedProfile ?: activeProfile != null` — the
         * same resolution the MusicService collector uses to call `applyProfile()` / `disable()`.
         * Reading the repository, not a preference key, is what makes this term true even when the EQ
         * was applied from the EQ screen without touching any switch.
         */
        val equalizerActive: Boolean,
        /** `SpatialAudioEnabledKey` (default OFF): live Superpowered HRTF / crossfeed / speaker-width stage. */
        val spatialEnabled: Boolean = false,
    )

    /** Why offload is impossible right now, independently of the user's own switch. */
    enum class BlockReason {
        CROSSFADE,
        SAFE_VOLUME,
        EQUALIZER,
        SPATIAL,
    }

    /**
     * Crossfade is only a veto when it can actually RUN. High-Performance Mode force-disables it at
     * every live site: the crossfadeEnabled collector ANDs `!HighPerformanceMode`, and every entry point
     * that can start a fade re-checks `highPerformanceModeHint` itself — `scheduleCrossfade`,
     * `onTailSilenceDetected` and `maybePrepareInstantVideoSwap`. So under perf mode no second player is
     * ever built. (`beginCrossfadeSwap` and `prepareSecondaryPlayer` do NOT read the hint; they are only
     * reachable downstream of the three sites above, which is why the guarantee still holds.)
     */
    fun crossfadeRunning(inputs: Inputs): Boolean =
        inputs.crossfadeEnabled && !inputs.highPerformanceMode

    /**
     * The DSP-side veto. Null means the chain is provably idle: bit-perfect passthrough, offload is
     * safe. Order is fixed (crossfade, Safe Volume, EQ) so the reason a caller shows is stable.
     */
    fun blockReason(inputs: Inputs): BlockReason? = when {
        crossfadeRunning(inputs) -> BlockReason.CROSSFADE
        inputs.safeVolumeEnabled -> BlockReason.SAFE_VOLUME
        inputs.equalizerActive -> BlockReason.EQUALIZER
        inputs.spatialEnabled -> BlockReason.SPATIAL
        else -> null
    }

    /** The final answer handed to the players: the user asked for it AND nothing needs the samples. */
    fun allowOffload(inputs: Inputs): Boolean =
        inputs.userWantsOffload && blockReason(inputs) == null
}
