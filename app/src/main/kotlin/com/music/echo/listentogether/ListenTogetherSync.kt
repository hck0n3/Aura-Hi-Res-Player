package iad1tya.echo.music.listentogether

/**
 * Pure reconciliation rules for Listen Together — no player, no network, no Android.
 *
 * Everything here answers one question: "given what the host published and what the guest currently
 * holds, what should the guest do?". Keeping it player-free is the point: the three defects this file
 * exists for (guest landing one song ahead, guest lagging after the buffer wait, host volume never
 * reaching anyone) were all decision bugs, not plumbing bugs, and decisions are testable.
 */
object ListenTogetherSync {

    /**
     * How long after applying an absolute CHANGE_TRACK a *relative* SKIP_NEXT / SKIP_PREV is ignored.
     *
     * A single host skip publishes BOTH: media3 fires onMediaItemTransition synchronously inside
     * `player.seekToNext()`, so the absolute CHANGE_TRACK for the destination goes out first, and the
     * relative skip follows from PlayerConnection's `onSkipNext` callback. Applying both moves the guest
     * two tracks. Current builds no longer publish the relative skip at all; this window keeps a guest
     * correct when the HOST is an older build that still does.
     */
    const val SKIP_SUPPRESSION_WINDOW_MS = 4_000L

    /**
     * Index of [currentTrackId] inside [queueIds], matched by IDENTITY (media id), or -1 when absent.
     *
     * Never derive this from a published position: host and guest queues drift by an item the moment the
     * radio appends or shuffle reorders, and a positional anchor then lands on the wrong song forever.
     */
    fun resolveStartIndex(queueIds: List<String>, currentTrackId: String?): Int {
        if (currentTrackId.isNullOrEmpty()) return -1
        return queueIds.indexOfFirst { it == currentTrackId }
    }

    /**
     * True when a relative skip may be applied — i.e. no absolute track change landed in the last
     * [SKIP_SUPPRESSION_WINDOW_MS]. [lastTrackChangeAppliedAtMs] is 0 when none has ever been applied.
     */
    fun shouldApplyRelativeSkip(
        nowMs: Long,
        lastTrackChangeAppliedAtMs: Long,
        windowMs: Long = SKIP_SUPPRESSION_WINDOW_MS,
    ): Boolean {
        if (lastTrackChangeAppliedAtMs <= 0L) return true
        return (nowMs - lastTrackChangeAppliedAtMs) >= windowMs
    }

    /**
     * The position to actually seek to, given a position that was captured at [capturedAtMs] and is only
     * being applied now, at [nowMs].
     *
     * While the host is PLAYING its clock keeps running, so every millisecond the guest spent waiting
     * (buffering the track, waiting for the server's BUFFER_COMPLETE fan-in) is a millisecond of
     * permanent lag unless it is added back here. While the host is PAUSED the position is frozen and
     * must be applied verbatim.
     *
     * [durationMs] <= 0 means "unknown" and disables the end clamp. Both timestamps must come from the
     * SAME device's clock — never mix a host timestamp with a guest `now`, cross-device clock skew makes
     * that arithmetic meaningless.
     */
    fun compensatedPosition(
        basePositionMs: Long,
        capturedAtMs: Long,
        nowMs: Long,
        isPlaying: Boolean,
        durationMs: Long = 0L,
    ): Long {
        val base = basePositionMs.coerceAtLeast(0L)
        if (!isPlaying || capturedAtMs <= 0L) return clampToDuration(base, durationMs)
        val elapsed = (nowMs - capturedAtMs).coerceAtLeast(0L)
        return clampToDuration(base + elapsed, durationMs)
    }

    /**
     * Advance [basePositionMs] by the guest's measured one-way network delay (RTT/2 from PING/PONG
     * on THIS device). Unlike [compensatedPosition], this does not mix two wall clocks: both the
     * ping and the pong are timestamped locally. Without it, a PLAY that spent 200–800 ms on the
     * wire lands the guest that far behind the host, and the 2–3 s seek gates treated that as "in
     * sync". Capped so a stalled ping cannot jump the guest seconds ahead.
     */
    fun networkCompensatedPosition(
        basePositionMs: Long,
        oneWayLatencyMs: Long,
        isPlaying: Boolean,
        durationMs: Long = 0L,
    ): Long {
        val base = basePositionMs.coerceAtLeast(0L)
        if (!isPlaying) return clampToDuration(base, durationMs)
        val extra = oneWayLatencyMs.coerceIn(0L, MAX_ONE_WAY_COMPENSATION_MS)
        return clampToDuration(base + extra, durationMs)
    }

    const val MAX_ONE_WAY_COMPENSATION_MS = 2_500L

    private fun clampToDuration(positionMs: Long, durationMs: Long): Long =
        if (durationMs > 0L) positionMs.coerceAtMost(durationMs) else positionMs

    /**
     * The single 0..1 scalar the host publishes as "my level".
     *
     * [internalVolume] is the app's own attenuation (MusicService.playerVolume), [streamFraction] is the
     * device's STREAM_MUSIC level as a fraction of its maximum — the one the on-screen slider and the
     * hardware buttons actually move — and [muted] is the app's mute toggle. A guest applies the result
     * to its OWN internal attenuation, so it scales the host's relative level onto whatever the guest's
     * device is set to; the guest's system volume is never touched.
     */
    fun effectiveHostVolume(
        internalVolume: Float,
        muted: Boolean,
        streamFraction: Float,
    ): Float {
        if (muted) return 0f
        val internal = if (internalVolume.isNaN()) 1f else internalVolume.coerceIn(0f, 1f)
        val stream = if (streamFraction.isNaN()) 1f else streamFraction.coerceIn(0f, 1f)
        return (internal * stream).coerceIn(0f, 1f)
    }

    /**
     * True when a newly computed host volume is different enough from the last published one to be worth
     * a message. Also true when nothing has been published yet ([lastPublished] null).
     */
    fun volumeChangedEnough(lastPublished: Float?, next: Float, epsilon: Float = 0.01f): Boolean {
        if (lastPublished == null) return true
        return kotlin.math.abs(lastPublished - next) >= epsilon
    }
}
