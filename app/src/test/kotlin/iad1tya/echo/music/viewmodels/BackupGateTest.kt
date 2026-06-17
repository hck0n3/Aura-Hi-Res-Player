package iad1tya.echo.music.viewmodels

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupGateTest {

    // ── canRestoreDbVersion ──

    @Test fun sameVersionRestores() {
        // the bug: a backup from the current app (v36) must be allowed
        assertTrue(canRestoreDbVersion(backupVersion = 36, currentVersion = 36))
    }

    @Test fun olderBackupRestores() {
        assertTrue(canRestoreDbVersion(backupVersion = 30, currentVersion = 36))
    }

    @Test fun newerBackupRejected() {
        assertFalse(canRestoreDbVersion(backupVersion = 37, currentVersion = 36))
    }

    @Test fun unknownVersionAllowed() {
        assertTrue(canRestoreDbVersion(backupVersion = 0, currentVersion = 36))
    }

    // ── shouldReseed ──

    @Test fun freshInstallSeeds() {
        // no stored version, no legacy flag → fresh → seed
        assertTrue(shouldReseed(storedSeedVersion = null, legacyDefaultsApplied = false, currentSeedVersion = 1))
    }

    @Test fun existingUserDoesNotReseed() {
        // legacy boolean guard was set → treated as seed v1 → no re-run at current v1
        assertFalse(shouldReseed(storedSeedVersion = null, legacyDefaultsApplied = true, currentSeedVersion = 1))
    }

    @Test fun restoredOldProfileReseeds() {
        // restore reset the stored version to 0 → below current → re-seed (new features load)
        assertTrue(shouldReseed(storedSeedVersion = 0, legacyDefaultsApplied = true, currentSeedVersion = 1))
    }

    @Test fun upToDateDoesNotReseed() {
        assertFalse(shouldReseed(storedSeedVersion = 1, legacyDefaultsApplied = true, currentSeedVersion = 1))
    }

    @Test fun newSeedVersionReseeds() {
        // app bumped to seed v2 → an install at v1 re-seeds the new defaults
        assertTrue(shouldReseed(storedSeedVersion = 1, legacyDefaultsApplied = true, currentSeedVersion = 2))
    }
}
