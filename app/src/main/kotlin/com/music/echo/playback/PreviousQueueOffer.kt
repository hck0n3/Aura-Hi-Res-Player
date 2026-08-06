

package iad1tya.echo.music.playback

import iad1tya.echo.music.models.MediaMetadata
import iad1tya.echo.music.models.PersistQueue
import iad1tya.echo.music.models.QueueData
import iad1tya.echo.music.models.QueueType

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
     * snapshot ([PreviousQueueCursor], in memory) is not retained for a session.
     *
     * The bound is NOT what keeps the retention small — see [PreviousQueueCursor]. In a car the snackbar
     * can never be answered (the phone UI is not on screen), so every offer armed before the drive rides
     * the full ten minutes; whatever the snapshot holds, it holds for the whole ten minutes.
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
     * How many ids go into ONE `WHERE id IN (...)` when [PreviousQueueCursor] is rehydrated.
     *
     * Room binds one SQL variable per id and Android's SQLite has capped that at 999 for most of its
     * history (`SQLITE_MAX_VARIABLE_NUMBER`; only recent versions raise it). "LIB:" is the whole library
     * as one queue, i.e. thousands of ids, so an unchunked query would throw on exactly the case this
     * design exists for. 500 leaves room under the old cap with no per-chunk cost worth measuring.
     */
    const val ID_LOOKUP_CHUNK = 500

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

/**
 * The outgoing queue reduced to what it takes to REBUILD it: its media ids, its cursor and its identity.
 *
 * It deliberately does NOT hold the queue's [MediaMetadata] objects, and that is the whole point of this
 * class. The offer lives up to [PreviousQueueRule.OFFER_TTL_MS] inside a long-lived foreground media
 * process, and [PreviousQueueRule.isDetour] admits "LIB:" — the whole library played as one list, the
 * biggest queue the app can build — so keeping every item's title, artists, album, thumbnail URL and
 * library timestamps made the worst case a multi-megabyte retention in the one process the OS reaps
 * first under pressure. A media id is one short string; the metadata behind it is an object graph.
 *
 * The songs come back from the `song` table on accept ([rehydrate]). That is sound for every context the
 * rule admits: "PL:" (a local playlist), "AP:" (liked / downloaded / the library tabs) and "LIB:" (the
 * library tab) are ALL built out of `song` rows in the first place — the queue was read from the very
 * table it is read back from.
 *
 * [anchor] is the ONE item kept whole: the song the cursor is on. Every other id can, in principle, fail
 * to resolve (its row deleted while the offer was pending); the cursor's may not, because landing on the
 * same song at the same second is the entire promise of the feature. Keeping it costs one object.
 */
data class PreviousQueueCursor(
    val title: String?,
    val contextId: String?,
    val queueType: QueueType,
    val queueData: QueueData?,
    /** Every media id on the outgoing timeline, in timeline order, one per index — never a subset. */
    val mediaIds: List<String>,
    val mediaItemIndex: Int,
    val position: Long,
    val anchor: MediaMetadata?,
)

/**
 * Rebuild the captured queue from [byId] (`song` rows looked up by media id), as the [PersistQueue] the
 * restore path already knows how to turn into a playable queue.
 *
 * The CURSOR is recomputed, not carried: [PreviousQueueCursor.mediaItemIndex] indexes the ids as they
 * were captured, and a row deleted while the offer was pending shortens the rebuilt list — replaying the
 * old index against the shorter list would resume on a DIFFERENT song, which is precisely the silent
 * failure this feature must not have. Counting the survivors ahead of the cursor is exact, and it stays
 * exact when the same song appears twice in the queue (a playlist with a duplicate), which is why the
 * walk is by index rather than a lookup of the cursor's id.
 *
 * Returns null when the cursor itself cannot be placed — nothing is played rather than the wrong thing.
 * With [PreviousQueueCursor.anchor] set (it always is at capture) that can only happen if the captured
 * index was out of range, i.e. never.
 */
fun PreviousQueueCursor.rehydrate(byId: Map<String, MediaMetadata>): PersistQueue? {
    if (mediaIds.isEmpty()) return null
    val items = ArrayList<MediaMetadata>(mediaIds.size)
    var cursor = -1
    for (i in mediaIds.indices) {
        val onCursor = i == mediaItemIndex
        val item = byId[mediaIds[i]] ?: (if (onCursor) anchor else null) ?: continue
        if (onCursor) cursor = items.size
        items.add(item)
    }
    if (cursor < 0) return null
    return PersistQueue(
        title = title,
        items = items,
        mediaItemIndex = cursor,
        position = position,
        queueType = queueType,
        queueData = queueData,
        contextId = contextId,
    )
}
