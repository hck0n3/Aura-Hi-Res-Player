

package iad1tya.echo.music.playback

/**
 * "¿Quieres volver a la cola anterior?"
 *
 * The owner's case, in his words: he is listening to a PLAYLIST, finds a song he likes, opens its ALBUM
 * and plays the whole album — and when he comes back he wants the option to resume the queue he left,
 * from where he was.
 *
 * The whole detection reads [Queue.contextId][iad1tya.echo.music.playback.queues.Queue.contextId], which
 * every queue already carries for Enhanced Shuffle. No new signal, no new storage, no heuristics. This
 * object is deliberately free of Android and of the player so the rule can be pinned by plain JUnit.
 */
object PreviousQueueRule {

    /**
     * A LISTENING LIST: a collection he sat down with, whose cursor (song 43 of 300, at 1:36) he cannot
     * reproduce by hand. "PL:" a playlist, "AP:" an auto-playlist (liked, downloads…), "LIB:" the whole
     * library tab. Everything else — "AL:" an album, "AR:" an artist, and a null context (a radio, a mix,
     * a search result, an album started from its Play button) — is a PLACE HE WENT TO LOOK SOMETHING UP.
     *
     * ONE list, used for BOTH sides of the transition, and that is the whole rule: leaving a listening
     * list for something that is NOT a listening list is a detour; anything else is not. The two-list
     * version of this (a "source" set and a wider "deliberate switch" set) could silently drift apart and
     * turn "he restarted the same playlist" — or "he swapped playlist A for playlist B" — into a prompt.
     */
    private val LISTENING_LIST_PREFIXES = listOf("PL:", "AP:", "LIB:")

    /**
     * How long an armed offer stays alive, measured on `SystemClock.elapsedRealtime()` (so time the phone
     * spent asleep in a pocket COUNTS — that is the case this bound exists for).
     *
     * Ten minutes: the offer models "me fui a mirar otra cosa y vuelvo". Three or four songs into the
     * album he jumped to, the album IS what he is listening to now, and a prompt proposing to undo it is
     * noise detached from anything he did. In practice the prompt is shown seconds after arming — it waits
     * only for him to leave the screen he jumped to — so this bound never touches the normal flow; it
     * exists so an offer armed before the app was backgrounded cannot surface an hour later, and so the
     * snapshot (a whole queue's metadata, in memory) is not retained for a session.
     */
    const val OFFER_TTL_MS = 10L * 60L * 1000L

    /**
     * Margin the SHELL adds to the deadline before raising the prompt, so an offer is never shown so
     * close to lapsing that answering it would be refused.
     *
     * The snackbar lives ~10 s (`SnackbarDuration.Long`) and he needs a moment to reach for it. Without
     * this margin the honest bound would produce exactly the failure the whole design avoids — a button
     * that does nothing — for anyone whose back press landed in the last seconds of the window.
     */
    const val DISPLAY_GRACE_MS = 15_000L

    /**
     * True when leaving [previousContextId] for [newContextId] is the detour worth offering a way back
     * from.
     *
     * The DESTINATION is matched by exclusion, not by an "AL:" prefix, and that is load-bearing. The
     * album screen only tags its queue "AL:<id>" from its SHUFFLE button; its Play button and a tap on a
     * track both start a `LocalAlbumRadio` with a NULL contextId. His literal case — "voy al álbum y
     * reproduzco todo el álbum" — is exactly those two, so a rule keyed on "AL:" would have compiled,
     * tested green and never once fired in his hands.
     *
     * Every transition and its verdict:
     *  - playlist -> album, playlist -> artist, playlist -> untagged queue (the owner's flow): **YES**.
     *    He left a list he was sitting with to look something up.
     *  - auto-playlist ("AP:liked", "AP:downloaded"…) -> the same three: **YES**, identical shape.
     *  - LIBRARY -> album / artist / untagged: **YES**. "LIB:LIBRARY" is his whole library played as one
     *    list — the longest queue the app can build and the one whose cursor is least reproducible by
     *    hand, so if any context deserves a way back it is this one. (It is also rare: the four other
     *    library tabs file themselves under "AP:", so "LIB:" means the LIBRARY tab and nothing else.)
     *  - playlist A -> playlist B, playlist -> auto-playlist, playlist -> library, library -> playlist:
     *    **NO**. Swapping one listening list for another is him deciding what to hear next, not wandering
     *    off; offering to drag him back would fire during ordinary use.
     *  - the SAME context on both sides (re-tapping the list already playing): **NO**, and by
     *    construction — the destination is a listening list too, so the single-list rule already declines.
     *  - album -> album, artist -> artist, album -> artist, album -> playlist: **NO**. An album is not a
     *    source. Two reasons, and either alone settles it. (1) An album is a bounded object one tap from
     *    where he already is: pressing Play on it again costs him one track's position, whereas a
     *    playlist's cursor is genuinely lost. (2) Mechanically it could only ever half-work — an album
     *    queue carries "AL:" ONLY when started from its Shuffle button, so making albums a source would
     *    fire for a shuffled album and stay silent for the very same album started with Play. A rule that
     *    fires for one of two identical-looking actions is worse than one that never fires.
     *  - a null SOURCE (a radio, a mix, a search result, the boot restore's EmptyQueue) -> anything:
     *    **NO**. There is no identifiable list to return to.
     */
    fun isDetour(previousContextId: String?, newContextId: String?): Boolean {
        val from = previousContextId ?: return false
        if (LISTENING_LIST_PREFIXES.none { from.startsWith(it) }) return false
        // The album Play button and a tapped track: no context at all. This is the common case.
        val to = newContextId ?: return true
        return LISTENING_LIST_PREFIXES.none { to.startsWith(it) }
    }

    /**
     * Whether an offer armed with [expiresAtElapsedRealtimeMs] is past its bound.
     *
     * Both clocks must be `SystemClock.elapsedRealtime()`. It is checked in THREE places on purpose: the
     * service drops the snapshot on a timer, the UI refuses to raise a lapsed prompt, and the resume
     * refuses to act on one — because the timer is a main-looper `delay`, which does not tick while the
     * device is in deep sleep, so on wake the drop can be a beat late.
     */
    fun hasLapsed(nowElapsedRealtimeMs: Long, expiresAtElapsedRealtimeMs: Long): Boolean =
        nowElapsedRealtimeMs >= expiresAtElapsedRealtimeMs
}

/**
 * One pending "volver a la cola anterior" offer.
 *
 * [token] increments per capture so the UI can tell a NEW offer from a recomposition of the same one and
 * show the prompt exactly once. [title] is the outgoing queue's title (the playlist name) when it had
 * one; null falls back to the untitled wording. [expiresAtElapsedRealtime] is the deadline
 * ([PreviousQueueRule.OFFER_TTL_MS] after capture) carried WITH the offer so the shell can decline to
 * raise a prompt whose button would already be dead.
 */
data class PreviousQueueOffer(
    val token: Long,
    val title: String?,
    val expiresAtElapsedRealtime: Long,
)
