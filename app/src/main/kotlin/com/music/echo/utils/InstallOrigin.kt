package iad1tya.echo.music.utils

/**
 * Where the data this process just woke up on top of came from.
 *
 * The distinction matters because Android's platform backup restores this app's DATABASE but not its
 * DataStore. `res/xml/backup_rules.xml` and `res/xml/data_extraction_rules.xml` deliberately exclude
 * `datastore/settings.preferences_pb` (it holds the InnerTube cookie, which must never leave the
 * device via `adb backup` or a cloud backup) while leaving `song.db` included — see
 * [InstallOriginClassifier] for why that asymmetry is kept rather than "fixed" by excluding the DB.
 *
 * The consequence is a state no other code path produces: a full library, complete with per-account
 * artist sync markers, sitting under a signed-OUT app on a device that has never signed in. Treating
 * that as a fresh install is what let a restore turn into a mass unsubscribe of somebody else's
 * YouTube account.
 */
enum class InstallOrigin {
    /** Nobody has used Aura on this device before. The APK is the first one installed and there is no data. */
    FRESH,

    /** An install that has been updated at least once — a customer who tapped "update". */
    UPDATED,

    /**
     * The APK was installed fresh, but it woke up on data that came from somewhere else: a
     * device-to-device transfer, a cloud backup restore, or `adb restore`.
     */
    RESTORED,
}

/**
 * Classifies [InstallOrigin] from three cheap, independent signals. Pure so it can be pinned by unit
 * tests — the two decisions that hang off it (whether Aura may start WRITING to a Google account
 * without being asked, and whether the restored artist markers are trustworthy) are both invisible to
 * the user until damage is done.
 *
 * ### Why `song.db` is NOT excluded from the backup rules
 * Excluding it closes this hole outright, and was rejected: it would mean every customer loses their
 * entire library when they move to a new phone, which is a far bigger and far more likely harm than
 * the one being defended against. The app already ships its own explicit backup/restore (Ajustes ▸
 * Copia de seguridad), but that is opt-in and most people will just use "copiar apps y datos". So the
 * library keeps travelling, and the restored DATA is instead treated as untrusted: the account-scoped
 * artist markers (`ytmSyncedAt`, `unfollowedByUserAt`) are cleared on a RESTORED first launch, exactly
 * as a logout would clear them, because by construction they were written against an account whose
 * credentials did NOT come with them. `bookmarkedAt` and `followedByUserAt` survive, so the library and
 * the user's own follows arrive intact.
 */
object InstallOriginClassifier {

    /**
     * @param neverUpdated `PackageInfo.firstInstallTime == lastUpdateTime` — the APK on the device is
     *   the one that was first installed. TRUE on a platform-restored install too, which is precisely
     *   why it cannot be the only signal.
     * @param markerSeen a flag Aura wrote to a SharedPreferences file that IS included in the backup
     *   rules. Absent on a genuine first launch; present on anything restored from a device that had
     *   already run this version.
     * @param hasExistingLibrary the database already contains songs. Covers the cohort that has never
     *   run a build carrying [markerSeen] — someone transferring from 0.6.141 — for whom the marker
     *   cannot exist yet. A genuine fresh install has an empty database.
     */
    fun classify(
        neverUpdated: Boolean,
        markerSeen: Boolean,
        hasExistingLibrary: Boolean,
    ): InstallOrigin = when {
        // Updated in place: the data is this device's own, and it never left it.
        // Updated in place: the data is this device's own, and it never left it.
        !neverUpdated -> InstallOrigin.UPDATED
        // A first-ever APK install that nonetheless found data. It did not get there by itself.
        markerSeen || hasExistingLibrary -> InstallOrigin.RESTORED
        else -> InstallOrigin.FRESH
    }

    /**
     * Whether this launch must detach the artist rows from whatever account they were written under,
     * exactly as a logout does. TRUE only for a restore — the one case where the database is present
     * but the credentials that gave its markers meaning are not.
     *
     * Exists as a named function, rather than an `== RESTORED` at the call site, so the decision that
     * actually protects somebody else's YouTube account can be pinned by a test end to end instead of
     * living only inside `App`.
     */
    fun mustDetachRestoredAccountMarkers(
        neverUpdated: Boolean,
        markerSeen: Boolean,
        hasExistingLibrary: Boolean,
    ): Boolean = classify(neverUpdated, markerSeen, hasExistingLibrary) == InstallOrigin.RESTORED

    /**
     * Whether Aura may default the write-to-your-Google-account switch ON. Only a genuinely blank
     * slate qualifies: a RESTORED install looks like a fresh one to the PackageManager but belongs to
     * somebody who is moving phones and was never asked.
     */
    fun mayDefaultAccountWritesOn(
        neverUpdated: Boolean,
        markerSeen: Boolean,
        hasExistingLibrary: Boolean,
    ): Boolean = classify(neverUpdated, markerSeen, hasExistingLibrary) == InstallOrigin.FRESH
}
