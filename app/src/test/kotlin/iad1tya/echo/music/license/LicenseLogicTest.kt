package iad1tya.echo.music.license

import iad1tya.echo.music.license.LicenseLogic.AppState
import iad1tya.echo.music.license.LicenseLogic.State
import iad1tya.echo.music.license.LicenseLogic.VerifyOutcome
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LicenseLogicTest {

    private val day = 24L * 60 * 60 * 1000
    private val now = 1_750_000_000_000L

    // ---- demo ----

    @Test fun firstRunWhenNothingStarted() {
        assertEquals(AppState.FIRST_RUN, LicenseLogic.resolve(State(), VerifyOutcome.UNVERIFIED, now))
    }

    @Test fun startDemoSetsTimer() {
        val s = LicenseLogic.startDemo(State(), now)
        assertEquals(now, s.demoStartedAt)
        assertTrue(LicenseLogic.isDemoActive(s, now))
    }

    @Test fun startDemoIsIdempotent() {
        val s1 = LicenseLogic.startDemo(State(), now)
        val s2 = LicenseLogic.startDemo(s1, now + 10 * day)
        assertEquals(s1.demoStartedAt, s2.demoStartedAt)
    }

    @Test fun demoActiveWithinThreeDays() {
        val s = LicenseLogic.startDemo(State(), now)
        assertEquals(AppState.DEMO, LicenseLogic.resolve(s, VerifyOutcome.UNVERIFIED, now + 2 * day))
    }

    @Test fun demoExpiresAfterThreeDays() {
        val s = LicenseLogic.startDemo(State(), now)
        assertEquals(AppState.DEMO_EXPIRED, LicenseLogic.resolve(s, VerifyOutcome.UNVERIFIED, now + 3 * day))
    }

    @Test fun demoDaysLeftCountsDown() {
        val s = LicenseLogic.startDemo(State(), now)
        assertEquals(3, LicenseLogic.demoDaysLeft(s, now))
        assertEquals(1, LicenseLogic.demoDaysLeft(s, now + 2 * day + 1))
        assertEquals(0, LicenseLogic.demoDaysLeft(s, now + 3 * day))
    }

    @Test fun clockRollbackLocksDemo() {
        val started = LicenseLogic.startDemo(State(), now)
        val seen = LicenseLogic.touch(started, now + day)
        assertFalse(LicenseLogic.isDemoActive(seen, now - day))
        assertTrue(LicenseLogic.isDemoActive(seen, now + day + 1000))
    }

    // ---- subscription ----

    private fun subState(verifiedAt: Long) =
        State(subscriptionKey = "KEY", lastVerifiedAt = verifiedAt, lastSeenAt = verifiedAt)

    @Test fun activeSubscriptionEnters() {
        assertEquals(
            AppState.SUBSCRIPTION_ACTIVE,
            LicenseLogic.resolve(subState(now), VerifyOutcome.ACTIVE, now),
        )
    }

    @Test fun endedSubscriptionBlocks() {
        assertEquals(
            AppState.SUBSCRIPTION_EXPIRED,
            LicenseLogic.resolve(subState(now), VerifyOutcome.ENDED, now),
        )
    }

    @Test fun deviceMismatchBlocks() {
        assertEquals(
            AppState.DEVICE_BLOCKED,
            LicenseLogic.resolve(subState(now), VerifyOutcome.DEVICE_MISMATCH, now),
        )
    }

    @Test fun offlineWithinGraceEnters() {
        assertEquals(
            AppState.SUBSCRIPTION_ACTIVE,
            LicenseLogic.resolve(subState(now), VerifyOutcome.UNVERIFIED, now + day / 2),
        )
    }

    @Test fun offlineAtGraceLimitStillEnters() {
        assertEquals(
            AppState.SUBSCRIPTION_ACTIVE,
            LicenseLogic.resolve(subState(now), VerifyOutcome.UNVERIFIED, now + day),
        )
    }

    @Test fun offlineBeyondGraceNeedsConnection() {
        assertEquals(
            AppState.NEEDS_CONNECTION,
            LicenseLogic.resolve(subState(now), VerifyOutcome.UNVERIFIED, now + day + 1),
        )
    }

    @Test fun offlineWithoutAnyVerificationNeedsConnection() {
        val s = State(subscriptionKey = "KEY", lastVerifiedAt = 0L)
        assertEquals(
            AppState.NEEDS_CONNECTION,
            LicenseLogic.resolve(s, VerifyOutcome.UNVERIFIED, now),
        )
    }

    @Test fun clockRollbackBlocksGrace() {
        val s = LicenseLogic.touch(subState(now), now + 2 * day)
        assertEquals(
            AppState.NEEDS_CONNECTION,
            LicenseLogic.resolve(s, VerifyOutcome.UNVERIFIED, now - day),
        )
    }
}
