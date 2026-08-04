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
 * These tests exist because 0.6.142 was one release away from MASS-UNSUBSCRIBING paying customers
 * from their real YouTube channels.
 *
 * The mechanism: the uploader inferred "the user unfollowed this" from an ABSENCE
 * (`followedByUserAt == null && ytmSyncedAt != null`), while the backfill that reads the account's
 * real subscription list stamped `ytmSyncedAt` on every row and only stamped `followedByUserAt` when
 * `bookmarkedAt` happened to be set. Any row whose bookmark was missing — after a logout, a "borrar
 * contenido sincronizado", a restore, an account switch, or simply because the artist is in the
 * library only via one of their songs — was rewritten INTO the unsubscribe shape by the backfill and
 * then unsubscribed in the same pass.
 *
 * So every test here asserts the same thing about a different local event: ZERO unsubscribes. The
 * rule being pinned is that an unsubscribe needs POSITIVE evidence of intent
 * ([ArtistEntity.unfollowedByUserAt]), never the absence of a local row or marker — because absence
 * is what every wipe, migration and half-finished sync produces.
 */
class ArtistSyncPolicyTest {

    private val now: LocalDateTime = LocalDateTime.of(2026, 8, 4, 12, 0)
    private val earlier: LocalDateTime = now.minusDays(30)

    // ── helpers ─────────────────────────────────────────────────────────────────────────────────

    /** An artist the user deliberately followed and that is already subscribed on the account. */
    private fun realFollow(id: String) = ArtistEntity(
        id = id,
        name = "Follow $id",
        channelId = "UC$id",
        bookmarkedAt = earlier,
        followedByUserAt = earlier,
        ytmSyncedAt = earlier,
    )

    /**
     * An artist that is in the library only because a song of theirs is — the shape
     * `followArtistsWithContent()` produces (bookmark, no deliberate follow).
     */
    private fun incidentalBookmark(id: String) = ArtistEntity(
        id = id,
        name = "Incidental $id",
        channelId = "UC$id",
        bookmarkedAt = earlier,
    )

    /** An artist row created as a side effect of inserting a song: no markers at all. */
    private fun songSideEffect(id: String) = ArtistEntity(
        id = id,
        name = "Side effect $id",
        channelId = "UC$id",
    )

    private fun List<ArtistEntity>.pendingUnsubscribes() = filter { ArtistSyncPolicy.mayUnsubscribe(it) }

    private fun List<ArtistEntity>.assertNoUnsubscribes(what: String) {
        val doomed = pendingUnsubscribes()
        assertTrue(
            "$what would have unsubscribed ${doomed.size} real channels: ${doomed.map { it.id }}",
            doomed.isEmpty(),
        )
    }

    /** The account's real subscription read-back, applied the way LibraryUploadSync applies it. */
    private fun List<ArtistEntity>.backfillFromAccount(remoteIds: Set<String>) = map {
        if (it.id in remoteIds) ArtistSyncPolicy.afterRemoteSubscriptionSeen(it, now) else it
    }

    /** A LOCAL wipe: logout, "borrar contenido sincronizado", account switch. */
    private fun List<ArtistEntity>.localReset() = map { ArtistSyncPolicy.afterLocalReset(it) }

    /**
     * Detaching the account: BOTH logout buttons, an account switch, an unparseable cookie, a restore
     * of somebody else's library. Mirrors `DatabaseDao.clearArtistAccountSyncMarkers`.
     */
    private fun List<ArtistEntity>.accountDetached() = map { ArtistSyncPolicy.afterAccountDetached(it) }

    /**
     * "Cerrar sesión (mantener datos)" EXACTLY as it behaved when the release was blocked: six
     * DataStore keys removed, not one database row touched. Only the clear-data button ran
     * `clearAllBookmarkedArtists`; this one ran nothing, which is why the markers survived it.
     */
    private fun List<ArtistEntity>.legacyKeepDataLogout() = this

    // ── the three scenarios the blocker demanded ────────────────────────────────────────────────

    @Test
    fun logoutThenReLoginProducesZeroUnsubscribes() {
        // A normal library: three artists the user really follows, two bookmarked only because they
        // have songs, and two that exist purely as a side effect of a song insert. The user is
        // genuinely subscribed on YouTube to ALL of them (people do subscribe to artists they merely
        // listen to) — which is precisely what made the old backfill so destructive.
        val library = listOf(
            realFollow("a"), realFollow("b"), realFollow("c"),
            incidentalBookmark("d"), incidentalBookmark("e"),
            songSideEffect("f"), songSideEffect("g"),
        )
        val remoteIds = library.map { it.id }.toSet()

        // 1. Logout → AccountSettingsViewModel calls clearAllSyncedContent → clearAllBookmarkedArtists.
        //    The ROWS survive (they are still referenced by songs); only the markers are wiped.
        val afterLogout = library.localReset()
        afterLogout.assertNoUnsubscribes("a logout")
        assertTrue(afterLogout.all { it.followedByUserAt == null && it.ytmSyncedAt == null })

        // 2. Re-login → the uploader's first act is to read the account's real subscription list and
        //    stamp those rows. THIS is the step that used to manufacture the unsubscribe queue.
        val afterReLogin = afterLogout.backfillFromAccount(remoteIds)
        afterReLogin.assertNoUnsubscribes("a logout followed by a re-login")

        // 3. And a second pass over the now-stamped rows must still be inert (the pass is scheduled,
        //    so it runs again and again; a bug that needs two passes is still a bug).
        afterReLogin.backfillFromAccount(remoteIds).assertNoUnsubscribes("a second pass after re-login")
    }

    @Test
    fun restoringAnOldBackupProducesZeroUnsubscribes() {
        // A backup taken before v40 knows nothing about the three follow columns. BackupRestoreViewModel
        // rebuilds each row from the bundle (bookmarkedAt = now) and carries the columns over from the
        // existing row — which, on a reinstall, does not exist. So every restored row is all-null.
        val restored = listOf("a", "b", "c", "d").map { id ->
            ArtistEntity(id = id, name = "Restored $id", channelId = "UC$id", bookmarkedAt = now)
        }
        restored.assertNoUnsubscribes("a restore from an old backup")

        // The user is subscribed to all four on YouTube. The next pass reads that list.
        val afterPass = restored.backfillFromAccount(setOf("a", "b", "c", "d"))
        afterPass.assertNoUnsubscribes("a restore followed by an upload pass")
        // And the restore must not have blanked anything into a destructive state: the account's real
        // subscriptions are now recorded as in-sync, which is a no-op upstream.
        assertTrue(afterPass.none { ArtistSyncPolicy.maySubscribe(it) })
    }

    @Test
    fun backfillOverAPartiallySyncedLibraryProducesZeroUnsubscribes() {
        // Half the library was migrated to v40 and marked; the other half never got there because the
        // pass ran out of budget / the network died / the process was killed. Then the remote read
        // itself is PARTIAL (a paginated fetch that timed out returns only the first page).
        val library = listOf(
            realFollow("a"),                                    // fully synced
            realFollow("b").copy(ytmSyncedAt = null),           // deliberate follow, not yet subscribed
            incidentalBookmark("c"),                            // bookmark only
            songSideEffect("d"),                                // no markers at all
            songSideEffect("e"),
        )
        val truncatedRemotePage = setOf("a", "c")               // "d" and "e" never arrived

        val afterPartialBackfill = library.backfillFromAccount(truncatedRemotePage)
        afterPartialBackfill.assertNoUnsubscribes("a partial backfill over a partially-synced library")

        // The rows the truncated page did not mention must be left exactly as they were — a read that
        // did not happen is not evidence of anything.
        assertEquals(library.first { it.id == "d" }, afterPartialBackfill.first { it.id == "d" })
        assertEquals(library.first { it.id == "e" }, afterPartialBackfill.first { it.id == "e" })

        // The one genuine pending SUBSCRIBE survives (we broke the destructive path, not the feature).
        assertEquals(listOf("b"), afterPartialBackfill.filter { ArtistSyncPolicy.maySubscribe(it) }.map { it.id })
    }

    // ── the same bug wearing other hats ─────────────────────────────────────────────────────────

    @Test
    fun aDatabaseMigrationThatLeavesEveryRowNullIsInert() {
        // MIGRATION_39_40 adds the columns without backfilling: every pre-existing row is all-null.
        // That state must mean "incidental, never touch upstream", not "unfollowed".
        val justMigrated = listOf("a", "b", "c").map {
            ArtistEntity(id = it, name = "Migrated $it", channelId = "UC$it", bookmarkedAt = earlier)
                .copy(followedByUserAt = null, ytmSyncedAt = null, unfollowedByUserAt = null)
        }
        justMigrated.assertNoUnsubscribes("the v39->v40 migration")
        justMigrated.backfillFromAccount(setOf("a", "b", "c"))
            .assertNoUnsubscribes("the v39->v40 migration followed by a pass")
    }

    // ── the keep-data logout: the door the "account switch" test above never opened ──────────────

    /**
     * The blocker this pass fixes, end to end.
     *
     * [anAccountSwitchDoesNotUnsubscribeTheOldAccountsChannels] below CLAIMS to cover an account
     * switch, but it models the switch as `localReset()` — the "cerrar sesión y borrar datos" button.
     * The other button, "cerrar sesión (mantener datos)", touched no database row at all, and it is the
     * one people use when switching accounts precisely because they want to keep their library. Every
     * marker survived it.
     */
    @Test
    fun keepDataLogoutThenDifferentAccountProducesZeroUnsubscribes() {
        // On account A the user follows five artists and then unfollows two of them. The live
        // `subscribeChannel(false)` fires against A straight away — that is the feature — but it never
        // clears the markers, and the only consumer that would (the upload pass) is switched OFF by
        // default for every existing paying customer, so the rows just accumulate.
        val onAccountA = listOf(
            realFollow("a1").localToggleLike(),   // unfollowed on A -> pending UNSUBSCRIBE
            realFollow("a2").localToggleLike(),   // unfollowed on A -> pending UNSUBSCRIBE
            realFollow("a3"),                     // still followed
            incidentalBookmark("a4"),
            songSideEffect("a5"),
        )
        assertEquals(
            "the queued unfollows must really be armed, or this test proves nothing",
            2, onAccountA.pendingUnsubscribes().size,
        )

        // Account B's subscriptions have nothing to do with A's. B's owner never unfollowed anything.
        val accountBSubscriptions = setOf("b1", "b2")

        // 1. The OLD keep-data logout: nothing is cleared, so signing into B and running a pass fires
        //    two real `subscribeChannel(id, false)` calls at B.
        val legacyDoomed = onAccountA.legacyKeepDataLogout()
            .backfillFromAccount(accountBSubscriptions)
            .pendingUnsubscribes()
        assertEquals(
            "the keep-data logout must still reproduce the cross-account unsubscribe, " +
                "or this test is not pinning anything",
            listOf("a1", "a2"), legacyDoomed.map { it.id },
        )

        // 2. The fix: the same sequence, with the markers cut loose from the account on the way out.
        val afterFix = onAccountA.accountDetached()
        afterFix.assertNoUnsubscribes("a keep-data logout")
        afterFix.backfillFromAccount(accountBSubscriptions)
            .assertNoUnsubscribes("a keep-data logout followed by signing into a different account")
        // And the pass after that, because the upload pass is scheduled and runs again and again.
        afterFix.backfillFromAccount(accountBSubscriptions)
            .backfillFromAccount(accountBSubscriptions)
            .assertNoUnsubscribes("a second pass on the different account")

        // "Mantener datos" means what it says: the library is still there ("a5" never had a bookmark —
        // it is a bare row created by a song insert). The two artists the user unfollowed are gone from
        // "tus artistas" because they asked for that, not because we logged out.
        assertEquals(
            listOf("a3", "a4"),
            afterFix.filter { it.bookmarkedAt != null }.map { it.id },
        )
    }

    /**
     * The other half of the fix, and the reason it clears two columns rather than three.
     *
     * The obvious version of this fix also clears `followedByUserAt`, on the theory that the next
     * subscription read-back re-stamps it. It only re-stamps artists the ACCOUNT actually returns — so
     * a deliberate follow that never reached YouTube (offline, dead cookie, an importer, the onboarding
     * picker) would be erased permanently and become indistinguishable from an incidental bookmark.
     * That is a silent data loss traded for nothing, since keeping the column cannot unsubscribe
     * anybody.
     */
    @Test
    fun reLoginToTheSameAccountRestoresTheFollowStateInsteadOfDroppingIt() {
        val synced = realFollow("a")                          // followed AND subscribed on the account
        val neverPushed = realFollow("b").copy(ytmSyncedAt = null) // followed; the subscribe never landed
        val library = listOf(synced, neverPushed, incidentalBookmark("c"))
        assertEquals(listOf("b"), library.filter { ArtistSyncPolicy.maySubscribe(it) }.map { it.id })

        val afterLogout = library.accountDetached()

        // "Mantener datos": nothing left the library.
        assertEquals(3, afterLogout.count { it.bookmarkedAt != null })
        // Both deliberate follows are still recorded as deliberate follows.
        assertEquals(listOf("a", "b"), afterLogout.filter { it.followedByUserAt != null }.map { it.id })
        // The follow that never reached YouTube is still queued to be pushed. THIS is what the
        // three-column version of the fix would have thrown away.
        assertTrue(ArtistSyncPolicy.maySubscribe(afterLogout.first { it.id == "b" }))

        // Sign back into the SAME account. It has "a" (we subscribed it) and not "b" (we never did).
        val afterReLogin = afterLogout.backfillFromAccount(setOf("a"))
        afterReLogin.assertNoUnsubscribes("re-logging into the same account")

        // "a" is back to exactly where it was: followed, subscribed, no work to do, no wasted write.
        val a = afterReLogin.first { it.id == "a" }
        assertNotNull(a.followedByUserAt)
        assertEquals(now, a.ytmSyncedAt)
        assertFalse(ArtistSyncPolicy.maySubscribe(a))
        // "b" is still pending, so the user's follow eventually reaches their account.
        assertTrue("the un-pushed follow must survive a logout", ArtistSyncPolicy.maySubscribe(afterReLogin.first { it.id == "b" }))
        // The incidental bookmark is still inert in both directions.
        val c = afterReLogin.first { it.id == "c" }
        assertFalse(ArtistSyncPolicy.maySubscribe(c))
        assertFalse(ArtistSyncPolicy.mayUnsubscribe(c))

        // Pinned explicitly: the variant that also clears `followedByUserAt` loses "b" for good.
        val threeColumnVariant = library.map {
            it.copy(followedByUserAt = null, ytmSyncedAt = null, unfollowedByUserAt = null)
        }.backfillFromAccount(setOf("a"))
        assertTrue(
            "clearing followedByUserAt would silently drop the follow that was never pushed",
            threeColumnVariant.none { ArtistSyncPolicy.maySubscribe(it) },
        )
    }

    /**
     * A restore never passes through the logout path: the target device keeps its OWN live login
     * (settings.preferences_pb is deliberately not restored) while song.db is replaced wholesale. So
     * the same neutralisation runs on the extracted backup DB before it is swapped in.
     */
    @Test
    fun restoringAnotherAccountsLibraryProducesZeroUnsubscribes() {
        val fromAccountA = listOf(
            realFollow("a1").localToggleLike(),
            realFollow("a2"),
            incidentalBookmark("a3"),
        )
        // Without the neutralisation the restored row is armed against whatever account is signed in.
        assertEquals(1, fromAccountA.pendingUnsubscribes().size)

        val restored = fromAccountA.accountDetached()
        restored.assertNoUnsubscribes("restoring a backup taken under another account")
        restored.backfillFromAccount(setOf("b1", "b2"))
            .assertNoUnsubscribes("restoring another account's library then running a pass")
        // The restored library is intact — a restore that emptied "tus artistas" would be its own bug.
        assertEquals(listOf("a2", "a3"), restored.filter { it.bookmarkedAt != null }.map { it.id })
    }

    @Test
    fun aLostUnfollowRefollowRaceStaysRepairable() {
        // Both toggles fire their own unscoped coroutine, so `subscribeChannel(false)` can land AFTER
        // `subscribeChannel(true)`: YouTube is unsubscribed while the row says followed. If the row
        // still claimed to be in sync, nothing could ever notice — maySubscribe false, mayUnsubscribe
        // false — and the user's follow would never exist on their account.
        val unfollowed = realFollow("a").localToggleLike()
        val reFollowed = unfollowed.localToggleLike()

        assertNull(reFollowed.unfollowedByUserAt)
        assertNotNull(reFollowed.followedByUserAt)
        assertNull("a re-follow must not keep claiming the account has this subscription", reFollowed.ytmSyncedAt)
        assertTrue("the uploader must be able to repair a lost race", ArtistSyncPolicy.maySubscribe(reFollowed))
        assertFalse(ArtistSyncPolicy.mayUnsubscribe(reFollowed))

        // And if the race was NOT lost — YouTube really does have it — the read-back settles the row
        // back to "in sync" at the cost of one comparison, with nothing sent upstream.
        val settled = ArtistSyncPolicy.afterRemoteSubscriptionSeen(reFollowed, now)
        assertFalse(ArtistSyncPolicy.maySubscribe(settled))
        assertFalse(ArtistSyncPolicy.mayUnsubscribe(settled))
    }

    @Test
    fun anAccountSwitchDoesNotUnsubscribeTheOldAccountsChannels() {
        // Sign out of account A (local reset), sign in to account B, whose subscription list is
        // completely different. Rows belonging to A's world are now unknown to B.
        val library = listOf(realFollow("a1"), realFollow("a2"), incidentalBookmark("a3"))
        val afterSwitch = library.localReset().backfillFromAccount(setOf("b1", "b2"))
        afterSwitch.assertNoUnsubscribes("an account switch")
    }

    @Test
    fun clearSyncedContentIsPurelyLocal() {
        // A user with a queued, genuine unfollow taps "borrar contenido sincronizado". The queued
        // unsubscribe is dropped along with everything else — a local reset is not an instruction to
        // go and change their YouTube account, in EITHER direction.
        val queued = realFollow("a").localToggleLike()
        assertTrue(ArtistSyncPolicy.mayUnsubscribe(queued))
        val afterReset = ArtistSyncPolicy.afterLocalReset(queued)
        assertFalse(ArtistSyncPolicy.mayUnsubscribe(afterReset))
        assertNull(afterReset.unfollowedByUserAt)
    }

    @Test
    fun aRowRewrittenByAnUnrelatedFeatureCannotBecomeAnUnsubscribe() {
        // Any feature that does `artist.copy(...)` — an image backfill, a channelId resolve, a rename —
        // must not be able to arm the destructive path. Only the explicit marker can.
        val touched = incidentalBookmark("a").copy(
            name = "Renamed", thumbnailUrl = "https://x/y.jpg", channelId = "UCresolved",
            lastUpdateTime = now, ytmSyncedAt = now,
        )
        assertFalse(ArtistSyncPolicy.mayUnsubscribe(touched))
        assertEquals(ArtistSyncPolicy.UnsubscribeRefusal.NO_RECORDED_INTENT, ArtistSyncPolicy.refuseUnsubscribe(touched))
    }

    // ── the feature must still work (a fix that breaks the feature is not a fix) ─────────────────

    @Test
    fun aDeliberateUnfollowStillUnsubscribes() {
        val unfollowed = realFollow("a").localToggleLike()
        assertNotNull("the unfollow must be recorded positively", unfollowed.unfollowedByUserAt)
        assertNull(unfollowed.followedByUserAt)
        assertTrue(ArtistSyncPolicy.mayUnsubscribe(unfollowed))

        // Once honoured, both the subscription and the intent are gone, so it is never retried.
        val done = ArtistSyncPolicy.afterUnsubscribed(unfollowed)
        assertFalse(ArtistSyncPolicy.mayUnsubscribe(done))
        assertNull(done.ytmSyncedAt)
        assertNull(done.unfollowedByUserAt)
    }

    @Test
    fun anUnfollowThatNeverReachedYouTubeIsRetriedAfterTheNextBackfill() {
        // Offline unfollow: the local row is armed but the network write failed, so YouTube still has
        // the subscription and reports it in the next read-back. The backfill must NOT bulldoze the
        // queued intent — that is the self-heal the old asymmetry was trying to provide, kept intact.
        val queued = realFollow("a").localToggleLike()
        val afterBackfill = ArtistSyncPolicy.afterRemoteSubscriptionSeen(queued, now)
        assertTrue("the pending unfollow must survive the read-back", ArtistSyncPolicy.mayUnsubscribe(afterBackfill))
        assertNull(afterBackfill.followedByUserAt)
        assertEquals(now, afterBackfill.ytmSyncedAt)
    }

    @Test
    fun reFollowingCancelsAQueuedUnsubscribe() {
        val queued = realFollow("a").localToggleLike()
        val reFollowed = queued.localToggleLike()
        assertNull(reFollowed.unfollowedByUserAt)
        assertFalse(ArtistSyncPolicy.mayUnsubscribe(reFollowed))
        // markFollowedByUser (importers, onboarding) must cancel it too.
        assertNull(queued.markFollowedByUser(now).unfollowedByUserAt)
        assertFalse(ArtistSyncPolicy.mayUnsubscribe(queued.markFollowedByUser(now)))
    }

    @Test
    fun unBookmarkingAnIncidentalArtistNeverUnsubscribes() {
        // The user tidies up "tus artistas" and removes an artist that was only there because one of
        // their songs is in the library. The account holds no subscription for them, so nothing about
        // this may reach it.
        //
        // NOTE — this test used to assert `unfollowedByUserAt == null` here, i.e. that the intent
        // marker was withheld from any row without a `followedByUserAt`. That mechanism is what caused
        // the shipped regression: after MIGRATION_39_40 EVERY row has a null `followedByUserAt`,
        // including artists the user really is subscribed to, so withholding the marker silently
        // discarded genuine unfollows (see ArtistUnfollowReachesAccountTest). The marker is now
        // recorded for any non-local artist; what protects this case is the thing that always did the
        // real work — `ytmSyncedAt`, which is written ONLY from the account's own subscription list.
        // No subscription, no unsubscribe, whatever the marker says.
        // The stamp is pinned to the test clock so the assertions below do not depend on the wall
        // clock of the machine running them.
        val tidiedUp = incidentalBookmark("a").localToggleLike().copy(unfollowedByUserAt = now)
        assertNotNull(
            "The tap must be recorded — withholding the marker is what discarded real unfollows.",
            tidiedUp.unfollowedByUserAt,
        )
        assertEquals(
            ArtistSyncPolicy.UnsubscribeRefusal.NOT_SUBSCRIBED,
            ArtistSyncPolicy.refuseUnsubscribe(tidiedUp),
        )
        assertFalse(ArtistSyncPolicy.mayUnsubscribe(tidiedUp))

        // And it stays inert. The one way a marker on a never-subscribed artist could ever wake up is
        // the user subscribing to that same artist DIRECTLY on YouTube much later, at which point the
        // read-back stamps `ytmSyncedAt`. The TTL is what stops a forgotten tidy-up from undoing a
        // subscription the user has only just made.
        val muchLater = now.plusDays(ArtistSyncPolicy.PENDING_UNFOLLOW_TTL_DAYS + 1)
        val subscribedOnYouTubeLater = ArtistSyncPolicy.afterRemoteSubscriptionSeen(tidiedUp, muchLater)
        assertFalse(
            "A long-forgotten tidy-up undid a subscription the user made afterwards on YouTube.",
            ArtistSyncPolicy.mayUnsubscribe(subscribedOnYouTubeLater, muchLater),
        )

        // Within the window it IS honoured — that is the whole point of recording the intent, and the
        // repair path an offline unfollow depends on.
        val soonAfter = now.plusHours(1)
        val stillPending = ArtistSyncPolicy.afterRemoteSubscriptionSeen(tidiedUp, soonAfter)
        assertTrue(ArtistSyncPolicy.mayUnsubscribe(stillPending, soonAfter))
    }

    @Test
    fun aStaleQueuedUnfollowIsRefusedRatherThanFired() {
        val queued = realFollow("a").localToggleLike().copy(
            unfollowedByUserAt = now.minusDays(ArtistSyncPolicy.PENDING_UNFOLLOW_TTL_DAYS + 1),
            ytmSyncedAt = now,
        )
        assertEquals(
            ArtistSyncPolicy.UnsubscribeRefusal.STALE_INTENT,
            ArtistSyncPolicy.refuseUnsubscribe(queued, now),
        )

        // A clock rolled BACKWARDS must not make a fresh instruction look ancient, nor an ancient one
        // fresh enough to fire unexpectedly. A future-dated marker is treated as fresh (never dropped).
        val futureDated = queued.copy(unfollowedByUserAt = now.plusDays(2))
        assertFalse(ArtistSyncPolicy.isStale(futureDated, now))
    }

    @Test
    fun localArtistsAreNeverTouchedUpstream() {
        val local = realFollow("a").copy(isLocal = true).localToggleLike()
        assertEquals(ArtistSyncPolicy.UnsubscribeRefusal.LOCAL_ARTIST, ArtistSyncPolicy.refuseUnsubscribe(local))
        assertFalse(ArtistSyncPolicy.maySubscribe(realFollow("b").copy(isLocal = true, ytmSyncedAt = null)))
    }

    @Test
    fun subscribeIsNeverDrivenByTheIncidentalBookmark() {
        // The original complaint: "me aparecen muchas suscripciones de cantantes que no sigo".
        assertFalse(ArtistSyncPolicy.maySubscribe(incidentalBookmark("a")))
        assertFalse(ArtistSyncPolicy.maySubscribe(songSideEffect("b")))
        assertTrue(ArtistSyncPolicy.maySubscribe(realFollow("c").copy(ytmSyncedAt = null)))
    }

    // ── regression pin: the exact shape of the shipped bug ──────────────────────────────────────

    /** The backfill statement as it was written when the release was blocked. */
    private fun legacyBackfill(a: ArtistEntity, at: LocalDateTime) =
        if (a.bookmarkedAt != null) {
            a.copy(followedByUserAt = a.followedByUserAt ?: a.bookmarkedAt, ytmSyncedAt = at)
        } else {
            a.copy(ytmSyncedAt = at)
        }

    /** The pending-unsubscribe predicate as it was written when the release was blocked. */
    private fun legacyPendingUnsubscribe(a: ArtistEntity) =
        !a.isLocal && a.followedByUserAt == null && a.ytmSyncedAt != null

    @Test
    fun theShippedBugIsReproducibleAndTheNewPolicyRefusesIt() {
        val library = listOf(
            realFollow("a"), incidentalBookmark("b"), songSideEffect("c"), songSideEffect("d"),
        )
        val remoteIds = library.map { it.id }.toSet()

        // Logout wipes the bookmarks, then the very next pass reads the account and backfills.
        val wiped = library.localReset()
        val legacyAfterPass = wiped.map { legacyBackfill(it, now) }
        val legacyDoomed = legacyAfterPass.filter { legacyPendingUnsubscribe(it) }

        assertEquals(
            "the old code must still reproduce the mass unsubscribe, or this test is not pinning anything",
            library.size, legacyDoomed.size,
        )
        // Same inputs, current code: nothing is removed from the account.
        wiped.backfillFromAccount(remoteIds).assertNoUnsubscribes("the reproduction of the shipped bug")
    }
}
