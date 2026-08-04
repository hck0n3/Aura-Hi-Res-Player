

package iad1tya.echo.music.db.entities

import androidx.compose.runtime.Immutable
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.music.innertube.YouTube
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.apache.commons.lang3.RandomStringUtils
import java.time.LocalDateTime

@Immutable
@Entity(tableName = "artist")
data class ArtistEntity(
    @PrimaryKey val id: String,
    val name: String,
    val thumbnailUrl: String? = null,
    val channelId: String? = null,
    val lastUpdateTime: LocalDateTime = LocalDateTime.now(),
    val bookmarkedAt: LocalDateTime? = null,
    @ColumnInfo(name = "isLocal", defaultValue = false.toString())
    val isLocal: Boolean = false,
    /**
     * Set ONLY when the user deliberately followed this artist — they tapped "Seguir", or the artist
     * came from a source that represents their real follows (the onboarding picker, the followed-artists
     * list of a migrated Spotify/Tidal/Deezer account, or an actual YouTube channel subscription read
     * back from their account).
     *
     * This is NOT the same thing as [bookmarkedAt]. `bookmarkedAt` is also set wholesale by
     * `DatabaseDao.followArtistsWithContent()` on EVERY artist that has so much as one song in the
     * library, so that "tus artistas" fills up like Spotify. Pushing *that* set up as real YouTube
     * subscriptions is what produced the owner's report "me aparecen muchas suscripciones de cantantes
     * que no sigo". Only artists with `followedByUserAt != null` are ever subscribed on the account.
     */
    val followedByUserAt: LocalDateTime? = null,
    /**
     * Last time this artist's subscription state was successfully reconciled with the YouTube account.
     * Non-null means "YouTube currently has a subscription for this artist" (either we subscribed it,
     * or we read it back from the account's subscription list).
     */
    val ytmSyncedAt: LocalDateTime? = null,
    /**
     * POSITIVE evidence that the user removed a follow they had actually made: they tapped "Dejar de
     * seguir" on an artist whose [followedByUserAt] was set. This is the ONLY thing that can ever
     * authorise removing a subscription from the real YouTube account.
     *
     * It exists because the old two-column machine inferred the unsubscribe from an ABSENCE
     * (`followedByUserAt == null && ytmSyncedAt != null`), and absence is exactly what a logout, a
     * "borrar contenido sincronizado", a restore from an old backup, a half-finished migration or a
     * partially-written down-sync all produce. A local clear must never be readable as "the user
     * unsubscribed from all of these": that is irreversible for the customer and invisible until they
     * open YouTube. Anything that merely LOSES local state leaves this column null, and a null here
     * means the reconciler does nothing upstream, in either direction.
     *
     * The full state machine (see [iad1tya.echo.music.utils.ArtistSyncPolicy], which is what both the
     * DAO queries and the uploader implement):
     *  - followedByUserAt != null, ytmSyncedAt == null                             -> pending SUBSCRIBE
     *  - unfollowedByUserAt != null, followedByUserAt == null, ytmSyncedAt != null -> pending UNSUBSCRIBE
     *  - followedByUserAt == null, unfollowedByUserAt == null                      -> incidental, NEVER touched
     *  - followedByUserAt != null, ytmSyncedAt != null                             -> in sync, nothing to do
     */
    val unfollowedByUserAt: LocalDateTime? = null,
) {
    val isYouTubeArtist: Boolean
        get() = id.startsWith("UC") || id.startsWith("FEmusic_library_privately_owned_artist")

    val isPrivatelyOwnedArtist: Boolean
        get() = id.startsWith("FEmusic_library_privately_owned_artist")

    /**
     * Toggle a DELIBERATE follow locally, without touching the network.
     *
     * Unfollowing stamps [unfollowedByUserAt] — the positive record of the intent — for every artist
     * that could possibly correspond to a real YouTube channel (i.e. everything except [isLocal]).
     *
     * ### Why this is NOT gated on `followedByUserAt != null`
     * It used to be, and that was a shipped regression. `MIGRATION_39_40` adds all three markers as
     * NULL and deliberately does not backfill, so for the whole window between updating and the first
     * artist down-sync EVERY row reads (null, null, null) — including artists the user really is
     * subscribed to on YouTube. Gating the stamp on `followedByUserAt` meant that, in exactly that
     * window, tapping "dejar de seguir" recorded NOTHING: no marker, therefore no queue entry,
     * therefore no retry, and (with the matching guard that used to sit in [toggleLike]) not even the
     * live call. The unfollow evaporated, and the next down-sync re-bookmarked the artist and stamped
     * it as an in-sync follow — the user unfollowed and Aura silently re-followed.
     *
     * ### Why stamping it more widely is still safe
     * The marker on its own authorises nothing. `DatabaseDao.artistsPendingUnsubscribe` and
     * [iad1tya.echo.music.utils.ArtistSyncPolicy.refuseUnsubscribe] BOTH additionally require
     * `ytmSyncedAt IS NOT NULL` — a claim that the currently attached account really holds this
     * subscription, written only by the subscription read-back. On an artist the account is not
     * subscribed to, this marker is inert forever. And it is written ONLY from here, i.e. only by a
     * user tapping the follow button: no importer, no migration, no wipe and no down-sync can produce
     * it, which is the property the whole unsubscribe path is built on.
     */
    fun localToggleLike() = if (bookmarkedAt != null) {
        copy(
            bookmarkedAt = null,
            followedByUserAt = null,
            unfollowedByUserAt = if (isLocal) unfollowedByUserAt else LocalDateTime.now(),
        )
    } else {
        // Re-following cancels any queued unsubscribe: the newest deliberate action wins.
        //
        // `ytmSyncedAt` is dropped along with it, which is what makes a lost race REPAIRABLE. Each
        // toggle fires its own unscoped coroutine below, so a fast unfollow/re-follow double-tap can
        // land `subscribeChannel(false)` AFTER `subscribeChannel(true)`: YouTube ends up unsubscribed
        // while the row says followed. Keeping `ytmSyncedAt` would leave that row reading "in sync" —
        // `maySubscribe` false, `mayUnsubscribe` false — so nothing would ever notice, let alone fix
        // it, and the user's follow would simply never exist on their account. Clearing it makes the
        // row a pending SUBSCRIBE instead: if YouTube really did lose the subscription the uploader
        // restores it, and if it did not, the subscription read-back stamps `ytmSyncedAt` straight
        // back and the row costs one comparison. Self-healing in both directions.
        LocalDateTime.now().let {
            copy(bookmarkedAt = it, followedByUserAt = it, ytmSyncedAt = null, unfollowedByUserAt = null)
        }
    }

    /**
     * Toggle the follow AND tell YouTube about it right now.
     *
     * ### This call is UNCONDITIONAL, and must stay that way
     * The owner's standing requirement: *"mientras estés con la sesión iniciada, si yo me suscribo, me
     * desuscribo, o doy like o quito like, eso se tiene que sincronizar con mi cuenta de YouTube"*.
     * Every caller of this function is a user tapping the follow button on a screen or menu (see the
     * call sites in ui/menu and ui/screens) — never an importer, never a migration, never a wipe. A
     * tap is unambiguous intent, expressed against the account that is signed in AT THAT MOMENT, so
     * there is nothing here to second-guess.
     *
     * A version of this method briefly carried a guard that skipped the call when the row had no local
     * follow markers. It was wrong twice over: post-`MIGRATION_39_40` every row has no markers, so it
     * silenced the unfollow of genuinely-subscribed artists; and the risk it was aimed at (a LOCAL
     * state change being read as "unsubscribe me") belongs to the bulk reconciler, which infers intent
     * from stored rows, not to a live tap. The bulk side keeps its three-layer guard
     * ([iad1tya.echo.music.utils.ArtistSyncPolicy], the DAO predicates, and the in-Kotlin re-check);
     * this side just does what the user asked.
     *
     * ### Why the write-back parameter is REQUIRED and not defaulted
     * The call used to be fire-and-forget, and that left an unfollow it had already DELIVERED sitting
     * on the row as a queued instruction forever — see
     * [iad1tya.echo.music.utils.ArtistSyncPolicy.liveCallHonouredTheUnfollow] for how that ended up
     * reversing a subscription the user made on YouTube itself. A default value would let the next
     * call site re-introduce the bug by saying nothing at all, so the compiler asks instead. Pass
     * `database::confirmArtistUnsubscribed`.
     *
     * @param onLiveUnsubscribeConfirmed invoked with `(artistId, unfollowedByUserAt)` ONLY after
     *   YouTube has accepted an unsubscribe, so the honoured intent can be retired. Never called for
     *   a follow, and never called when the call failed — a failure must leave the marker in place,
     *   because it is the retry.
     */
    fun toggleLike(
        onLiveUnsubscribeConfirmed: suspend (artistId: String, unfollowedAt: LocalDateTime) -> Unit,
    ) = localToggleLike().also { toggled ->
        CoroutineScope(Dispatchers.IO).launch {
            // Single source of truth for "does this tap reach the account?" — see the doc there. A
            // local-only artist has no YouTube channel behind it; everything else does.
            if (!iad1tya.echo.music.utils.ArtistSyncPolicy.mustCallAccountLive(this@ArtistEntity)) {
                return@launch
            }
            // `bookmarkedAt` here is still the value BEFORE the toggle: null means the user just
            // followed (subscribe = true), non-null means they just unfollowed (subscribe = false).
            val subscribing = bookmarkedAt == null
            val targetChannelId = channelId ?: YouTube.getChannelId(id)
            if (targetChannelId.isEmpty()) return@launch
            val confirmedByAccount = YouTube.subscribeChannel(targetChannelId, subscribing).isSuccess

            // The instruction has been carried out, so it stops being an instruction. `toggled` is the
            // row we are about to persist, so its `unfollowedByUserAt` is the exact timestamp the
            // write-back must not overshoot: if the user has already unfollowed AGAIN while this call
            // was in flight, that newer marker is undelivered and must survive.
            val queuedAt = toggled.unfollowedByUserAt
            if (queuedAt != null &&
                iad1tya.echo.music.utils.ArtistSyncPolicy
                    .liveCallHonouredTheUnfollow(subscribing, confirmedByAccount)
            ) {
                // Never let a database problem escape into this unsupervised coroutine: there is no
                // CoroutineExceptionHandler here, so a throw would reach the thread's uncaught handler.
                runCatching { onLiveUnsubscribeConfirmed(id, queuedAt) }
            }
            // Otherwise best-effort only. If this never reached YouTube (offline, expired cookie,
            // unknown channelId) the row still carries the pending state, and LibraryUploadSync
            // retries it once the subscription read-back confirms the account holds it.
        }
    }

    /** Mark this artist as a deliberate follow without toggling anything (used by importers). */
    fun markFollowedByUser(now: LocalDateTime = LocalDateTime.now()) = copy(
        bookmarkedAt = bookmarkedAt ?: now,
        followedByUserAt = followedByUserAt ?: now,
        // A follow supersedes a queued unsubscribe; leaving the marker would make the reconciler
        // undo the follow that just happened.
        unfollowedByUserAt = null,
    )

    companion object {
        fun generateArtistId() = "LA" + RandomStringUtils.insecure().next(8, true, false)
    }
}
