package iad1tya.echo.music.playback

/**
 * WHEN the lyrics view stops following the OUTGOING song of a crossfade swap.
 *
 * Background. With crossfade ON (forced on for everyone in App.kt, default 5 s) every auto-advance goes
 * through `performCrossfadeSwap`, which publishes the INCOMING song immediately — the notification, widget
 * and Android Auto depend on that — while the user still HEARS the outgoing one. So the swap "pins" the
 * outgoing metadata for the lyrics view only, and the lyrics run on the outgoing player's own clock until
 * the pin is released.
 *
 * The bug this exists to fix: the pin used to be released in exactly ONE place, `cleanupCrossfade()`, which
 * is reached only from the fade loop's `finally`, and that loop exits on `inP >= 1f && outP >= 1f` — i.e. it
 * waits for the INCOMING ramp too. The two ramps are NOT the same length (`durOut` is capped to the fading
 * track's remaining audible life, `durIn` is the full configured duration), the incoming clock FREEZES while
 * the incoming track buffers or while the user pauses, and nothing cancels the fade job on a manual skip. All
 * three produce the same symptom in the wild: the lyrics on screen belong to a song that is no longer
 * playing, sometimes for several seconds, sometimes for a whole pause, sometimes over a completely different
 * track.
 *
 * The rule here: release on AUDIBILITY, not on ramp progress. These predicates are pure so they can be unit
 * tested — a private member of an Android Service cannot be (same reason [CrossfadeMath] was extracted).
 *
 * INVARIANT (checked by CrossfadeLyricsPinTest): every predicate here can only ever ADVANCE the moment the
 * lyrics return to the live song, never delay it. Neither function reads or writes fade math, curve,
 * duration, swap order or player volume — the caller passes in values it has already computed for the audio,
 * and does not feed anything back.
 */
object CrossfadeLyricsPin {

    /**
     * Curve gain (relative to the outgoing track's own playback level, i.e. `fadeOut` straight out of
     * [CrossfadeMath.getGains]) at or below which the outgoing song counts as inaudible under a
     * near-full-level incoming song. -34 dB relative to the level the track was just playing at.
     *
     * DELIBERATELY the curve gain and NOT the absolute volume written to the player
     * (`startVolume * fadeOut * headroom`): `startVolume` is the user's real volume, so an absolute
     * threshold releases the pin on the very first tick of every fade for anyone playing quietly (and
     * instantly for a muted session) — that would show the NEXT song's lyrics while the previous one is
     * still clearly audible, which is the same defect this fix exists to remove, only mirrored. The same
     * trap is documented on `MusicService.crossfadeOutgoingPositionMs`.
     */
    const val INAUDIBLE_CURVE_GAIN = 0.02f

    /**
     * Should the outgoing-song pin be released on this tick of the fade loop?
     *
     * @param pinned whether a pin is currently held at all (false → nothing to do).
     * @param outgoingGone the loop's own `outDone`: the fading player is null or STATE_ENDED. Its content is
     *   over, so there is nothing left to hear from it.
     * @param outgoingCurveGain the `fadeOut` value the loop just computed from the selected curve, in 0..1
     *   RELATIVE to the outgoing track's playing level. With the default curve 4 this is exactly 0 from
     *   p = 0.85 onward, so the pin is released a beat before the ramp formally completes — which is the
     *   point: `outP` reaching 1 can be seconds away from the last audible sample.
     * @param outgoingDetectedSilent `SilenceDetectorAudioProcessor.isCurrentlySilent()` for the FADING
     *   player. This is the same signal the tail-silence crossfade trigger fires on, and it needs 3.5 s of
     *   continuous sub-threshold audio before it goes true, so it cannot mistake a musical gap for the end.
     *   It covers the case the gain can never cover: a fade fired ON the silent tail, where the outgoing has
     *   been inaudible since t = 0 while `fadeOut` is still ~1.
     *
     * Not-a-number is treated as "release" (`!(x > t)`), because releasing early is the safe direction: the
     * cost of releasing too late is the reported bug, the cost of releasing too early is bounded by the
     * pre-existing behaviour (the lyrics simply follow the live song, which is what they did before the pin
     * existed at all).
     */
    fun shouldRelease(
        pinned: Boolean,
        outgoingGone: Boolean,
        outgoingCurveGain: Float,
        outgoingDetectedSilent: Boolean,
    ): Boolean {
        if (!pinned) return false
        if (outgoingGone || outgoingDetectedSilent) return true
        return !(outgoingCurveGain > INAUDIBLE_CURVE_GAIN)
    }

    /**
     * Should the pin be released because the LIVE item is no longer the song it was pinned to?
     *
     * This is the manual-skip net. Nothing cancels the fade job when the user skips mid-fade (the only
     * `crossfadeJob?.cancel()` lives in `onDestroy`), so without this the lyrics stay on song A while song C
     * plays — the worst version of the bug, and the one no gain threshold can see.
     *
     * The crossfade swap itself does NOT come through `onMediaItemTransition` (it publishes the incoming
     * item directly and only attaches the service listener to the incoming player afterwards), so a
     * transition arriving while a pin is held means something OTHER than the swap moved the current item.
     *
     * @param liveMediaId null is deliberately NOT a release: media3 fires a spurious null-item transition
     *   around a swap, and blanking the pin there would drop the lyrics off a song the user is still
     *   hearing.
     */
    fun shouldReleaseOnTransition(pinnedId: String?, liveMediaId: String?): Boolean =
        pinnedId != null && liveMediaId != null && pinnedId != liveMediaId
}
