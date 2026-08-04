package iad1tya.echo.music.utils

import iad1tya.echo.music.db.entities.ArtistEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

/**
 * The mirror image of [ArtistSyncPolicyTest].
 *
 * That file pins the SAFE direction: no local wipe, migration or restore may ever become a real
 * unsubscribe. Enforcing it introduced the opposite failure — unfollows that reached nobody — and this
 * file pins the other half of the contract, which is the owner's standing requirement:
 *
 *   *"mientras estés con la sesión iniciada, si yo me suscribo, me desuscribo, o doy like o quito
 *   like, eso se tiene que sincronizar con mi cuenta de YouTube o YouTube Music."*
 *
 * A follow/unfollow the user just tapped must reach the account IMMEDIATELY, and must survive as a
 * retryable instruction if it did not. Both properties have to hold in the state `MIGRATION_39_40`
 * leaves behind: three NULL columns on every single row, including artists the user genuinely
 * subscribes to, for as long as the 30-minute down-sync cooldown (whose key survives the update) says
 * so.
 */
class ArtistUnfollowReachesAccountTest {

    /**
     * Relative to the wall clock on purpose: `ArtistSyncPolicy` now expires a queued unfollow after
     * [ArtistSyncPolicy.PENDING_UNFOLLOW_TTL_DAYS], so a hard-coded date would silently turn these
     * tests into staleness tests as soon as it aged past the window.
     */
    private val earlier: LocalDateTime = LocalDateTime.now().minusDays(2)

    /**
     * EXACTLY what `MIGRATION_39_40` leaves behind for an artist the user really is subscribed to on
     * YouTube: the row survives the update with its bookmark, and all three follow columns are NULL
     * because the migration deliberately does not backfill (at migration time there is no way to know
     * what the account holds). Only the artist down-sync repairs it — and that is gated behind a
     * 30-minute cooldown whose `LastFullSyncKey` survives the update, or is off entirely if the user
     * turned YtmSync off.
     */
    private fun justMigratedRealSubscription(id: String = "UCreal") = ArtistEntity(
        id = id,
        name = "Artist $id",
        channelId = "UC${id}Channel",
        bookmarkedAt = earlier,
        followedByUserAt = null,
        ytmSyncedAt = null,
        unfollowedByUserAt = null,
    )

    // ── BLOCKER 2: the unfollow must reach YouTube, and must survive if it does not ──────────────

    /**
     * The owner's own repro, minutes after updating: he unfollows an artist he really is subscribed
     * to. The tap must hit `subscribeChannel(id, false)`.
     *
     * The regression made this decision depend on the row's stored markers, which the migration had
     * just blanked, so the destructive direction was skipped for every artist in the library.
     */
    @Test
    fun unfollowRightAfterTheMigrationStillCallsYouTube() {
        val artist = justMigratedRealSubscription()

        assertTrue(
            "An unfollow tapped on a genuinely-subscribed artist did not reach YouTube. This is the " +
                "owner's explicit requirement and the exact post-migration state every customer is " +
                "in for the first 30 minutes after updating.",
            ArtistSyncPolicy.mustCallAccountLive(artist),
        )
    }

    /** The non-destructive direction was never in doubt, but it is half the same requirement. */
    @Test
    fun followAlsoCallsYouTubeRegardlessOfMarkers() {
        val neverSeen = ArtistEntity(id = "UCnew", name = "New", channelId = "UCnewChannel")
        assertTrue(ArtistSyncPolicy.mustCallAccountLive(neverSeen))
    }

    /** A local-file artist has no channel behind it; there is nothing to call and nothing to queue. */
    @Test
    fun localArtistNeverReachesTheAccount() {
        val local = ArtistEntity(id = "LAabcd1234", name = "Local", isLocal = true, bookmarkedAt = earlier)
        assertFalse(ArtistSyncPolicy.mustCallAccountLive(local))
        assertNull(
            "A local artist must not queue an unsubscribe either — there is no channel to remove.",
            local.localToggleLike().unfollowedByUserAt,
        )
    }

    /**
     * If the live call did not land (offline, expired cookie, unresolved channel), the intent has to
     * survive as a retryable instruction. It survives as `unfollowedByUserAt` — and the regression
     * only stamped that when `followedByUserAt` was already set, which post-migration it never is.
     *
     * This is the assertion that fails loudest against the blocked build.
     */
    @Test
    fun unfollowRightAfterTheMigrationRecordsRetryableIntent() {
        val unfollowed = justMigratedRealSubscription().localToggleLike()

        assertNotNull(
            "The unfollow left NO trace: no marker, so `artistsPendingUnsubscribe` returns nothing, " +
                "so there is no retry and no repair. The next down-sync then re-bookmarks the artist " +
                "and stamps it as an in-sync follow — the user unfollowed and Aura re-followed.",
            unfollowed.unfollowedByUserAt,
        )
        assertNull("The bookmark must be gone from the library view.", unfollowed.bookmarkedAt)
        assertNull(unfollowed.followedByUserAt)
    }

    /**
     * The queued intent must actually be honoured once the account confirms it holds the subscription
     * — i.e. the full repair chain, not just the marker. This walks the real production functions.
     */
    @Test
    fun theQueuedUnfollowSurvivesTheDownSyncAndBecomesAnUnsubscribe() {
        val unfollowed = justMigratedRealSubscription().localToggleLike()

        // The down-sync / upload pass reads FEmusic_library_corpus_artists and finds the artist still
        // subscribed (the live call never landed). `afterRemoteSubscriptionSeen` must preserve the
        // queued unfollow rather than overwrite it with "in sync".
        val afterReadBack = ArtistSyncPolicy.afterRemoteSubscriptionSeen(unfollowed, LocalDateTime.now())

        assertNotNull("The read-back erased the user's unfollow.", afterReadBack.unfollowedByUserAt)
        assertNull(
            "The read-back re-followed an artist the user had just unfollowed.",
            afterReadBack.followedByUserAt,
        )
        assertTrue(
            "The unfollow never becomes an upstream unsubscribe, so YouTube is never told.",
            ArtistSyncPolicy.mayUnsubscribe(afterReadBack),
        )
    }

    /**
     * ...and the safe direction still holds afterwards. Widening the marker must not have widened what
     * the reconciler is allowed to do: with no `ytmSyncedAt` (i.e. no evidence the account holds this
     * subscription) the queued row stays inert forever.
     */
    @Test
    fun aQueuedUnfollowOnAnArtistTheAccountDoesNotHoldStaysInert() {
        val unfollowed = justMigratedRealSubscription().localToggleLike()

        assertEquals(
            ArtistSyncPolicy.UnsubscribeRefusal.NOT_SUBSCRIBED,
            ArtistSyncPolicy.refuseUnsubscribe(unfollowed),
        )
        assertFalse(ArtistSyncPolicy.mayUnsubscribe(unfollowed))
    }

    /** Re-following supersedes the queued unfollow: the newest deliberate action wins. */
    @Test
    fun reFollowingCancelsTheQueuedUnsubscribe() {
        val reFollowed = justMigratedRealSubscription().localToggleLike().localToggleLike()

        assertNull(reFollowed.unfollowedByUserAt)
        assertNotNull(reFollowed.followedByUserAt)
        assertNotNull(reFollowed.bookmarkedAt)
        assertFalse(ArtistSyncPolicy.mayUnsubscribe(reFollowed))
    }

    // ── an HONOURED unfollow must stop being a standing order ────────────────────────────────────

    /**
     * The defect the widened marker introduced.
     *
     * `localToggleLike` now stamps `unfollowedByUserAt` on EVERY non-local artist — necessary, because
     * post-`MIGRATION_39_40` a real subscription and an incidental bookmark are indistinguishable. But
     * `toggleLike` was fire-and-forget, so after a live unsubscribe that SUCCEEDED the marker stayed
     * put with `ytmSyncedAt` null: inert, and unreachable by every cleanup
     * (`artistsPendingUnsubscribe` requires `ytmSyncedAt IS NOT NULL`, so the STALE_INTENT retirement
     * never sees it either).
     *
     * Then the user changes their mind inside the TTL and re-subscribes to that artist DIRECTLY on
     * YouTube. The read-back stamps `ytmSyncedAt`, [ArtistSyncPolicy.afterRemoteSubscriptionSeen]
     * preserves the marker on purpose, and the row becomes a genuine-looking pending unsubscribe that
     * the very same pass flushes — Aura silently reversing a subscription the user just made.
     *
     * Walks the real production functions end to end.
     */
    @Test
    fun aDeliveredUnfollowDoesNotReverseALaterReSubscribeOnYouTube() {
        // 1. The user unfollows in Aura. Post-migration the row has no ytmSyncedAt.
        val queued = justMigratedRealSubscription().localToggleLike()
        assertNotNull(queued.unfollowedByUserAt)

        // 2. The live call LANDS. Production must retire the honoured intent.
        assertTrue(
            "A confirmed live unsubscribe has to retire the marker; leaving it makes an instruction " +
                "that has already been carried out into a permanent standing order.",
            ArtistSyncPolicy.liveCallHonouredTheUnfollow(subscribing = false, confirmedByAccount = true),
        )
        val settled = ArtistSyncPolicy.afterUnsubscribed(queued)
        assertNull(settled.unfollowedByUserAt)
        assertNull(settled.ytmSyncedAt)

        // 3. Days later — well inside PENDING_UNFOLLOW_TTL_DAYS — the user subscribes to that same
        //    artist on YouTube itself, and Aura reads it back.
        val reSubscribed = ArtistSyncPolicy.afterRemoteSubscriptionSeen(settled, LocalDateTime.now())

        // 4. Nothing may be queued against it.
        assertEquals(
            "Aura is about to undo a subscription the user made on YouTube itself. The unfollow it is " +
                "acting on was already delivered — it should never have survived step 2.",
            ArtistSyncPolicy.UnsubscribeRefusal.NO_RECORDED_INTENT,
            ArtistSyncPolicy.refuseUnsubscribe(reSubscribed),
        )
        assertFalse(ArtistSyncPolicy.mayUnsubscribe(reSubscribed))
        assertNotNull("The read-back must still record the real subscription.", reSubscribed.ytmSyncedAt)
    }

    /**
     * The other half, and the reason the write-back is conditional on SUCCESS rather than on the call
     * having been attempted: a live unsubscribe that did NOT land is still owed, and the marker is the
     * entire retry mechanism. Retiring it there would silently destroy a real unfollow — the blocker
     * this whole file exists to prevent, in miniature.
     */
    @Test
    fun anUndeliveredUnfollowKeepsItsMarkerSoItCanBeRetried() {
        assertFalse(
            "A FAILED live unsubscribe (offline, expired cookie) must leave the intent queued.",
            ArtistSyncPolicy.liveCallHonouredTheUnfollow(subscribing = false, confirmedByAccount = false),
        )

        val queued = justMigratedRealSubscription().localToggleLike()
        // The read-back confirms the account really does still hold the subscription...
        val afterReadBack = ArtistSyncPolicy.afterRemoteSubscriptionSeen(queued, LocalDateTime.now())
        // ...so the queued unfollow becomes a real upstream unsubscribe, exactly as before.
        assertTrue(
            "The retry path is gone: an unfollow that never reached YouTube is now lost forever.",
            ArtistSyncPolicy.mayUnsubscribe(afterReadBack),
        )
    }

    /**
     * A FOLLOW never triggers the write-back. The subscribe direction has no intent marker to retire,
     * and clearing `ytmSyncedAt` there would drop the account's confirmation of a subscription that
     * demonstrably exists.
     */
    @Test
    fun aConfirmedFollowNeverRetiresAnything() {
        assertFalse(ArtistSyncPolicy.liveCallHonouredTheUnfollow(subscribing = true, confirmedByAccount = true))
        assertFalse(ArtistSyncPolicy.liveCallHonouredTheUnfollow(subscribing = true, confirmedByAccount = false))
    }

    /**
     * The race the DAO predicate exists for. The live call is asynchronous, so the user can re-follow
     * (or unfollow again) before it returns; the write-back must not clobber the newer action.
     *
     * `DatabaseDao.confirmArtistUnsubscribed` encodes this as `followedByUserAt IS NULL AND
     * unfollowedByUserAt IS NOT NULL AND unfollowedByUserAt <= :unfollowedAt`. This pins the two row
     * shapes those conditions have to reject; change one, change both.
     */
    @Test
    fun theWriteBackMustNotClobberAnActionTakenWhileTheCallWasInFlight() {
        val queued = justMigratedRealSubscription().localToggleLike()
        val deliveredAt = requireNotNull(queued.unfollowedByUserAt)

        // (a) re-followed in flight -> `followedByUserAt IS NULL` is false, so no row matches.
        val reFollowed = queued.localToggleLike()
        assertNotNull(reFollowed.followedByUserAt)
        assertNull(reFollowed.unfollowedByUserAt)

        // (b) re-followed and unfollowed AGAIN in flight -> a NEWER, undelivered instruction.
        val queuedAgain = reFollowed.localToggleLike()
        val newerMarker = requireNotNull(queuedAgain.unfollowedByUserAt)
        assertTrue(
            "The write-back is keyed on the timestamp it delivered; a marker written afterwards is a " +
                "separate instruction that has NOT been delivered and must survive.",
            newerMarker >= deliveredAt,
        )
        assertNull(queuedAgain.followedByUserAt)
    }

    // ── BLOCKER 3: a queued unsubscribe must not be thrown away for want of a channelId ──────────

    /**
     * `channelId` is null on rows stamped by `fetchRemoteSubscriptionIds` (it stamps `ytmSyncedAt`
     * from the account's id list and never writes a channelId) and on rows whose `getChannelId()`
     * failed during the down-sync. Those rows ARE subscribed. The uploader used to add them to
     * `cleared`, which runs `clearArtistsSyncedToYtm` and drops both `ytmSyncedAt` and the intent —
     * YouTube never contacted, unfollow gone, permanently.
     */
    @Test
    fun aQueuedUnsubscribeWithNoChannelIdIsResolvedNotDiscarded() {
        val queued = ArtistEntity(
            id = "UCnoChannel",
            name = "Stamped by the id list",
            channelId = null,
            unfollowedByUserAt = earlier,
            ytmSyncedAt = earlier,
        )

        assertTrue("This row is a genuine pending unsubscribe.", ArtistSyncPolicy.mayUnsubscribe(queued))
        assertEquals(
            "A missing channelId must be RESOLVED from the artist's own id, exactly as the subscribe " +
                "path already does — never treated as 'nothing to unsubscribe from'.",
            ArtistSyncPolicy.MissingChannelAction.RESOLVE_FROM_ID,
            ArtistSyncPolicy.onMissingChannelId(queued, resolutionAttempted = false),
        )
    }

    /** Resolution failed this pass: keep the instruction, try again. Never silently retire it. */
    @Test
    fun anUnresolvableChannelKeepsTheUnsubscribeQueued() {
        val queued = ArtistEntity(
            id = "UCnoChannel",
            name = "Resolution failed",
            channelId = null,
            unfollowedByUserAt = earlier,
            ytmSyncedAt = earlier,
        )

        assertEquals(
            "Discarding the intent here is what made the unfollow permanent and invisible.",
            ArtistSyncPolicy.MissingChannelAction.KEEP_QUEUED,
            ArtistSyncPolicy.onMissingChannelId(queued, resolutionAttempted = true),
        )
    }

    /**
     * The one case where retiring the intent is honest: an artist from the user's own uploaded library
     * has no public channel and never will, so there is genuinely nothing to unsubscribe from and
     * retrying forever would just burn budget.
     */
    @Test
    fun aPrivatelyOwnedArtistRetiresTheIntentOnceResolutionFailed() {
        val uploaded = ArtistEntity(
            id = "FEmusic_library_privately_owned_artistABC",
            name = "My own upload",
            channelId = null,
            unfollowedByUserAt = earlier,
            ytmSyncedAt = earlier,
        )

        assertEquals(
            ArtistSyncPolicy.MissingChannelAction.RETIRE_INTENT,
            ArtistSyncPolicy.onMissingChannelId(uploaded, resolutionAttempted = true),
        )
    }

    // ── the invariant the widened marker must not break ──────────────────────────────────────────

    /**
     * Widening `unfollowedByUserAt` must not have made any NON-tap path able to write it. The marker
     * is produced by `localToggleLike` alone; every reconciler-side transition must leave a row that
     * had none without one.
     */
    @Test
    fun noReconcilerTransitionCanManufactureTheIntentMarker() {
        val rows = listOf(
            ArtistEntity(id = "a", name = "A", channelId = "UCa", bookmarkedAt = earlier),
            ArtistEntity(id = "b", name = "B", channelId = "UCb"),
            ArtistEntity(
                id = "c", name = "C", channelId = "UCc",
                bookmarkedAt = earlier, followedByUserAt = earlier, ytmSyncedAt = earlier,
            ),
        )
        val now = LocalDateTime.now()

        rows.forEach { row ->
            assertNull(
                "afterRemoteSubscriptionSeen manufactured an unfollow intent for ${row.id}",
                ArtistSyncPolicy.afterRemoteSubscriptionSeen(row, now).unfollowedByUserAt,
            )
            assertNull(
                "afterLocalReset manufactured an unfollow intent for ${row.id}",
                ArtistSyncPolicy.afterLocalReset(row).unfollowedByUserAt,
            )
            assertNull(
                "afterAccountDetached manufactured an unfollow intent for ${row.id}",
                ArtistSyncPolicy.afterAccountDetached(row).unfollowedByUserAt,
            )
            assertNull(
                "markFollowedByUser manufactured an unfollow intent for ${row.id}",
                row.markFollowedByUser(now).unfollowedByUserAt,
            )
        }
    }
}
