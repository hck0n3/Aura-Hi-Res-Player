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
 * Android's platform backup restores `song.db` but NOT `datastore/settings.preferences_pb` — the
 * backup rules exclude the DataStore on purpose, because it holds the InnerTube cookie. The result is
 * a state nothing else in the app produces: a full library, complete with per-account artist sync
 * markers, under an app that has never signed in on this device.
 *
 * Two things then went wrong at once, both silently:
 *  1. `firstInstallTime == lastUpdateTime` is TRUE on a restored install (the APK really is the first
 *     one on the new phone), so it was classified as FRESH and the write-to-your-Google-account switch
 *     was defaulted ON for somebody who was moving phones, not setting Aura up.
 *  2. The restored `unfollowedByUserAt` + `ytmSyncedAt` markers — written against account A, whose
 *     cookie did NOT travel — were still actionable. Sign into account B and the uploader fires
 *     `subscribeChannel(id, false)` at B, 50 per pass, for channels B's owner never unfollowed.
 */
class InstallOriginTest {

    // ── classification ───────────────────────────────────────────────────────────────────────────

    /** Nobody has used Aura here: no marker travelled with a restore, and the database is empty. */
    @Test
    fun aGenuinelyBlankSlateIsFresh() {
        assertEquals(
            InstallOrigin.FRESH,
            InstallOriginClassifier.classify(
                neverUpdated = true,
                markerSeen = false,
                hasExistingLibrary = false,
            ),
        )
        assertFalse(
            "A genuinely fresh install has no foreign markers to defuse.",
            detachFrom(BLANK_SLATE),
        )
    }

    /** A customer who tapped "update". The APK has been replaced, so the timestamps differ. */
    @Test
    fun anUpdatedInstallIsUpdated() {
        assertEquals(
            InstallOrigin.UPDATED,
            InstallOriginClassifier.classify(
                neverUpdated = false,
                markerSeen = true,
                hasExistingLibrary = true,
            ),
        )
    }

    /**
     * THE BLOCKER. "Copiar apps y datos" / cloud restore / `adb restore`: brand-new APK, so
     * `neverUpdated` is true — but Aura's own backed-up marker came along with the data.
     */
    @Test
    fun aPlatformRestoreIsNotAFreshInstall() {
        assertEquals(
            "A restored install was classified as FRESH, which switches account writes ON for someone " +
                "who was never asked and never set the app up.",
            InstallOrigin.RESTORED,
            InstallOriginClassifier.classify(
                neverUpdated = true,
                markerSeen = true,
                hasExistingLibrary = true,
            ),
        )
    }

    /**
     * The cohort the marker cannot cover: someone transferring from a build that never wrote it (the
     * blocked 0.6.141 and everything before it). A brand-new APK that finds a populated database did
     * not get there by itself.
     */
    @Test
    fun aRestoreFromABuildWithoutTheMarkerIsStillARestore() {
        assertEquals(
            "Transferring from a pre-0.6.142 phone leaves no marker, so the database itself has to be " +
                "the tell — otherwise this whole cohort keeps the old behaviour.",
            InstallOrigin.RESTORED,
            InstallOriginClassifier.classify(
                neverUpdated = true,
                markerSeen = false,
                hasExistingLibrary = true,
            ),
        )
    }

    // ── consequence 1: the account-write switch ──────────────────────────────────────────────────

    /**
     * Driven from the RAW signals, not from a hand-picked [InstallOrigin], so this pins the whole
     * chain — the one that was broken was the classification, not `decide`.
     */
    @Test
    fun onlyAGenuinelyBlankSlateDefaultsTheAccountWriteSwitchOn() {
        // The owner's requested default, for people choosing Aura right now.
        assertTrue(
            LibraryUploadOptIn.decide(stored = null, freshInstall = freshFrom(BLANK_SLATE)),
        )
        assertFalse(
            "An updated install must never start writing to a Google account because someone tapped update.",
            LibraryUploadOptIn.decide(stored = null, freshInstall = freshFrom(UPDATED_IN_PLACE)),
        )
        assertFalse(
            "A phone transfer defaulted account writes ON. The user was moving phones, not setting " +
                "Aura up, and was never asked — new playlists and new channel subscriptions appear on " +
                "their account and stay invisible until they open YouTube.",
            LibraryUploadOptIn.decide(stored = null, freshInstall = freshFrom(PLATFORM_RESTORE)),
        )
        assertFalse(
            "A transfer from a build predating the install marker is still a transfer.",
            LibraryUploadOptIn.decide(stored = null, freshInstall = freshFrom(RESTORE_WITHOUT_MARKER)),
        )
    }

    @Test
    fun anExplicitStoredChoiceAlwaysWins() {
        listOf(BLANK_SLATE, UPDATED_IN_PLACE, PLATFORM_RESTORE, RESTORE_WITHOUT_MARKER).forEach {
            assertTrue(LibraryUploadOptIn.decide(stored = true, freshInstall = freshFrom(it)))
            assertFalse(LibraryUploadOptIn.decide(stored = false, freshInstall = freshFrom(it)))
        }
    }

    /** (neverUpdated, markerSeen, hasExistingLibrary) — the three signals `App` reads. */
    private data class Signals(
        val neverUpdated: Boolean,
        val markerSeen: Boolean,
        val hasExistingLibrary: Boolean,
    )

    private val BLANK_SLATE = Signals(neverUpdated = true, markerSeen = false, hasExistingLibrary = false)
    private val UPDATED_IN_PLACE = Signals(neverUpdated = false, markerSeen = true, hasExistingLibrary = true)
    private val PLATFORM_RESTORE = Signals(neverUpdated = true, markerSeen = true, hasExistingLibrary = true)
    private val RESTORE_WITHOUT_MARKER =
        Signals(neverUpdated = true, markerSeen = false, hasExistingLibrary = true)

    private fun freshFrom(s: Signals) = InstallOriginClassifier
        .mayDefaultAccountWritesOn(s.neverUpdated, s.markerSeen, s.hasExistingLibrary)

    private fun detachFrom(s: Signals) = InstallOriginClassifier
        .mustDetachRestoredAccountMarkers(s.neverUpdated, s.markerSeen, s.hasExistingLibrary)

    // ── consequence 2: the restored markers must be defused ──────────────────────────────────────

    /**
     * The full path, in the order Android and the user walk it. Every step uses the real production
     * function that the corresponding code path calls.
     */
    @Test
    fun aRestoredLibraryCannotUnsubscribeTheNextAccountItMeets() {
        // Relative to the wall clock: a queued unfollow expires after
        // ArtistSyncPolicy.PENDING_UNFOLLOW_TTL_DAYS, and this test is about account identity,
        // not about staleness — a fixed date would eventually pass for the wrong reason.
        val stamped = LocalDateTime.now().minusDays(2)

        // 1. On account A the down-sync stamped followedByUserAt + ytmSyncedAt (it runs with the
        //    upload switch off — the down-sync does not consult it).
        // 2. The user unfollowed 40 artists: unfollowedByUserAt written, ytmSyncedAt left in place.
        //    Nothing cleared them — the only thing that does is flushPendingUnsubscribes, which the
        //    switch gates off for every updated install.
        val fromAccountA = (1..40).map { i ->
            ArtistEntity(
                id = "UC$i",
                name = "Artist $i",
                channelId = "UC${i}Channel",
                bookmarkedAt = stamped,
                followedByUserAt = null,
                ytmSyncedAt = stamped,
                unfollowedByUserAt = stamped,
            )
        }
        assertTrue(
            "Precondition: on account A these ARE genuine pending unsubscribes.",
            fromAccountA.all { ArtistSyncPolicy.mayUnsubscribe(it) },
        )

        // 3. New phone. Android restores song.db WITH the markers and does NOT restore the DataStore,
        //    so the app comes up signed out. `forgetAccount` never ran — the user never signed in on
        //    this device — and MIGRATION_39_40 never ran either, the DB is already v40.
        //
        //    This is the step that did not exist. Note it is driven by the REAL classification of the
        //    real signals, not by asserting on a hand-picked enum: the gap was that nothing recognised
        //    this launch as a restore, so nothing ever called the (already correct) detach.
        val afterRestoreCleanup = if (detachFrom(PLATFORM_RESTORE)) {
            fromAccountA.map { ArtistSyncPolicy.afterAccountDetached(it) }
        } else {
            fromAccountA
        }

        // 4. The user signs into account B and a sync runs: the read-back stamps B's real list.
        val underAccountB = afterRestoreCleanup.map {
            ArtistSyncPolicy.afterRemoteSubscriptionSeen(it, LocalDateTime.now())
        }

        val doomed = underAccountB.filter { ArtistSyncPolicy.mayUnsubscribe(it) }
        assertTrue(
            "A restored database would unsubscribe ${doomed.size} channels from an account that was " +
                "never asked: ${doomed.map { it.id }}",
            doomed.isEmpty(),
        )

        // And the two things the user must NOT lose on a phone transfer.
        assertTrue(
            "The library must survive a phone transfer — song.db is included in the backup rules on " +
                "purpose, and the cleanup must not undo that.",
            afterRestoreCleanup.all { it.bookmarkedAt != null },
        )
    }

    /**
     * Signing in is as much of a boundary as signing out. A database that reaches a login without
     * having passed through a logout is, by definition, carrying markers written under an account we
     * cannot identify — `adb restore`, device transfer, or a library copied from another device.
     *
     * Pins the shape of what `LoginScreen` now runs before `restartApp`, and pins that it is the SAME
     * transition `forgetAccount` uses (`clearArtistAccountSyncMarkers` /
     * [ArtistSyncPolicy.afterAccountDetached]) rather than a second, drifting copy of the rule.
     */
    @Test
    fun signingInDetachesWhateverAccountTheRowsBelongedTo() {
        val stamped = LocalDateTime.now().minusDays(2)
        val carriedIn = ArtistEntity(
            id = "UCx",
            name = "From another account",
            channelId = "UCxChannel",
            bookmarkedAt = stamped,
            followedByUserAt = stamped,
            ytmSyncedAt = stamped,
            unfollowedByUserAt = stamped,
        )

        val afterSignIn = ArtistSyncPolicy.afterAccountDetached(carriedIn)

        assertNull("The account-scoped subscription claim must go.", afterSignIn.ytmSyncedAt)
        assertNull("The unfollow aimed at the OLD account must go.", afterSignIn.unfollowedByUserAt)
        assertFalse(ArtistSyncPolicy.mayUnsubscribe(afterSignIn))

        // What must NOT go: the library, and the user's own follow (which is a statement about the
        // user, not about any account — clearing it would destroy follows that were never pushed up).
        assertNotNull("'Mantener datos' and a phone transfer both keep the library.", afterSignIn.bookmarkedAt)
        assertNotNull(afterSignIn.followedByUserAt)
    }
}
