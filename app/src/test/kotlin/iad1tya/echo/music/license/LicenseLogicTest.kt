package iad1tya.echo.music.license

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LicenseLogicTest {

    private val demoKey = "564FFF1L7EHNZOAK3FEZ"
    private val perpetualKey = "051INR8XG157MBWE32DE"
    private val day = 24L * 60 * 60 * 1000
    private val now = 1_750_000_000_000L

    @Test
    fun embeddedDemoHashMatchesRealKey() {
        assertEquals(LicenseLogic.DEMO_KEY_HASH, LicenseLogic.hashKey(demoKey))
    }

    @Test
    fun embeddedPerpetualHashMatchesRealKey() {
        assertEquals(LicenseLogic.PERPETUAL_KEY_HASH, LicenseLogic.hashKey(perpetualKey))
    }

    @Test
    fun keyInputIsNormalizedBeforeHashing() {
        assertEquals(
            LicenseLogic.hashKey(demoKey),
            LicenseLogic.hashKey("  564fff1l7ehnzoak3fez  ")
        )
    }

    @Test
    fun perpetualKeyActivatesForever() {
        val (state, result) = LicenseLogic.activate(LicenseLogic.State(), perpetualKey, now)
        assertEquals(LicenseLogic.Activation.Perpetual, result)
        assertTrue(LicenseLogic.isLicensed(state, now))
        assertTrue(LicenseLogic.isLicensed(state, now + 10_000 * day))
    }

    @Test
    fun demoKeyActivatesThreeDays() {
        val (state, result) = LicenseLogic.activate(LicenseLogic.State(), demoKey, now)
        assertEquals(LicenseLogic.Activation.Demo(now + 3 * day), result)
        assertTrue(LicenseLogic.isLicensed(state, now))
        assertTrue(LicenseLogic.isLicensed(state, now + 2 * day))
        assertFalse(LicenseLogic.isLicensed(state, now + 3 * day))
        assertFalse(LicenseLogic.isLicensed(state, now + 30 * day))
    }

    @Test
    fun demoKeyIsBurnedAfterFirstUseOnSameDevice() {
        val (afterDemo, _) = LicenseLogic.activate(LicenseLogic.State(), demoKey, now)
        val expired = now + 4 * day
        assertFalse(LicenseLogic.isLicensed(afterDemo, expired))
        val (state2, result2) = LicenseLogic.activate(afterDemo, demoKey, expired)
        assertEquals(LicenseLogic.Activation.DemoAlreadyUsed, result2)
        assertFalse(LicenseLogic.isLicensed(state2, expired))
    }

    @Test
    fun perpetualKeyStillWorksAfterDemoExpired() {
        val (afterDemo, _) = LicenseLogic.activate(LicenseLogic.State(), demoKey, now)
        val expired = now + 4 * day
        val (state2, result2) = LicenseLogic.activate(afterDemo, perpetualKey, expired)
        assertEquals(LicenseLogic.Activation.Perpetual, result2)
        assertTrue(LicenseLogic.isLicensed(state2, expired))
    }

    @Test
    fun invalidKeyRejectedAndStateUntouched() {
        val initial = LicenseLogic.State()
        val (state, result) = LicenseLogic.activate(initial, "CLAVE-FALSA-123", now)
        assertEquals(LicenseLogic.Activation.Invalid, result)
        assertEquals(initial, state)
        assertFalse(LicenseLogic.isLicensed(state, now))
    }

    @Test
    fun clockRollbackLocksDemo() {
        val (state, _) = LicenseLogic.activate(LicenseLogic.State(), demoKey, now)
        val seen = LicenseLogic.touch(state, now + day)
        // user moves the clock one day back: demo must lock
        assertFalse(LicenseLogic.isLicensed(seen, now - day))
        // normal forward time still fine
        assertTrue(LicenseLogic.isLicensed(seen, now + day + 1000))
    }

    @Test
    fun demoDaysLeftCountsDown() {
        val (state, _) = LicenseLogic.activate(LicenseLogic.State(), demoKey, now)
        assertEquals(3, LicenseLogic.demoDaysLeft(state, now))
        assertEquals(1, LicenseLogic.demoDaysLeft(state, now + 2 * day + 1))
        assertEquals(0, LicenseLogic.demoDaysLeft(state, now + 3 * day))
    }
}
