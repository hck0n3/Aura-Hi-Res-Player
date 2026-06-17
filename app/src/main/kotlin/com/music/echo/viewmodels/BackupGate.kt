package iad1tya.echo.music.viewmodels

/**
 * Pure backup-compatibility rules (no Android deps → JVM unit-testable).
 *
 * The old restore hard-coded a max DB version of 35; once the schema advanced to 36 a backup made
 * by the *current* app was rejected as "from a newer version" and would not restore. The gate now
 * compares against the live DB version instead.
 */

/**
 * A restored DB is compatible when its SQLite `user_version` is not newer than the app's current
 * schema version (Room can open and migrate same-or-older). [backupVersion] == 0 means "unknown"
 * (couldn't read the header) and is allowed — Room will validate/migrate on open.
 */
fun canRestoreDbVersion(backupVersion: Int, currentVersion: Int): Boolean =
    backupVersion <= currentVersion

/**
 * Seeds (one-time default sets) must re-run when the stored seed version is older than the app's
 * [currentSeedVersion]. A restored backup carries the old profile's (lower/absent) seed version, so
 * this returns true after a restore → this version's feature defaults re-apply ("new features
 * appear"). Absent stored value is treated as [legacyVersion] (the boolean-guard era = seed v1) when
 * the legacy "defaults applied" flag was set, otherwise 0 (fresh install).
 */
fun shouldReseed(storedSeedVersion: Int?, legacyDefaultsApplied: Boolean, currentSeedVersion: Int, legacyVersion: Int = 1): Boolean {
    val effective = storedSeedVersion ?: if (legacyDefaultsApplied) legacyVersion else 0
    return effective < currentSeedVersion
}
