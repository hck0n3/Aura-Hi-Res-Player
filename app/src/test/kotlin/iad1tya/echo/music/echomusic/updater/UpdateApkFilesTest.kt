package iad1tya.echo.music.echomusic.updater

import iad1tya.echo.music.echomusic.updater.UpdateApkFiles.ApkVerdict
import iad1tya.echo.music.echomusic.updater.UpdateApkFiles.PartState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Locks the updater's file and version logic.
 *
 * The bug these tests exist for was silent: with one fixed `echomusic.apk` on disk, the install screen
 * pointed the system installer at whatever the PREVIOUS update had left there. The user saw a normal
 * install prompt, installed, and stayed on the old build — no crash, no error, and every fix shipped
 * since was invisible to them. So the two properties that matter are asserted directly: two versions
 * can never share a filename, and nothing is installable until the archive itself says it is the right
 * version.
 */
class UpdateApkFilesTest {

    // ---- filenames -----------------------------------------------------------------------------

    @Test
    fun `two releases never collide on disk`() {
        val a = UpdateApkFiles.apkFileName("v0.6.145")
        val b = UpdateApkFiles.apkFileName("v0.6.146-beta1")
        assertEquals("Aura-Hi-Res-Player-0.6.145.apk", a)
        assertEquals("Aura-Hi-Res-Player-0.6.146-beta1.apk", b)
        assertFalse(a == b)
    }

    @Test
    fun `tag decoration does not change the file a version maps to`() {
        assertEquals(UpdateApkFiles.apkFileName("0.6.145"), UpdateApkFiles.apkFileName("v0.6.145"))
        assertEquals(UpdateApkFiles.apkFileName("5.0.1"), UpdateApkFiles.apkFileName("b5.0.1"))
    }

    @Test
    fun `a beta word is not mistaken for the beta tag prefix`() {
        // Only a "b" followed by a digit is tag decoration; "beta9" is a name in its own right.
        assertEquals("beta9", UpdateApkFiles.normalizeVersion("beta9"))
        assertEquals("5.0.1", UpdateApkFiles.normalizeVersion("b5.0.1"))
    }

    @Test
    fun `the version comes from the network so it can never escape the update folder`() {
        val name = UpdateApkFiles.apkFileName("../../../../data/data/iad1tya.echo.music/x")
        assertFalse(name.contains("/"))
        assertFalse(name.contains("\\"))
        assertFalse(name.contains(".."))
        assertTrue(name.startsWith(UpdateApkFiles.FILE_PREFIX))
        assertTrue(name.endsWith(UpdateApkFiles.APK_SUFFIX))
    }

    @Test
    fun `a blank or dot-only version still produces a usable name`() {
        assertEquals("Aura-Hi-Res-Player-unknown.apk", UpdateApkFiles.apkFileName("   "))
        assertEquals("Aura-Hi-Res-Player-unknown.apk", UpdateApkFiles.apkFileName(".."))
    }

    @Test
    fun `an unfinished download is not named like an installable APK`() {
        val part = UpdateApkFiles.partFileName("0.6.146")
        assertFalse(part.endsWith(UpdateApkFiles.APK_SUFFIX))
        assertTrue(part.endsWith(UpdateApkFiles.PART_SUFFIX))
        assertFalse(part == UpdateApkFiles.apkFileName("0.6.146"))
    }

    // ---- what may be deleted -------------------------------------------------------------------

    @Test
    fun `cleanup removes other releases and the legacy fixed name, and keeps the current one`() {
        val names = listOf(
            "Aura-Hi-Res-Player-0.6.144.apk",
            "Aura-Hi-Res-Player-0.6.145.apk",
            "Aura-Hi-Res-Player-0.6.145.apk.part",
            "echomusic.apk",
            "echo_temp.zip",
        )
        val doomed = UpdateApkFiles.deletableNames(names, keepVersion = "v0.6.145")
        assertTrue("Aura-Hi-Res-Player-0.6.144.apk" in doomed)
        assertTrue("echomusic.apk" in doomed)
        assertTrue("echo_temp.zip" in doomed)
        assertFalse("Aura-Hi-Res-Player-0.6.145.apk" in doomed)
        assertFalse("Aura-Hi-Res-Player-0.6.145.apk.part" in doomed)
    }

    @Test
    fun `cleanup never touches a file this app did not write`() {
        val names = listOf("backup.db", "mi cancion.mp3", "Aura-Hi-Res-Player-0.6.140.apk", "notes.txt")
        val doomed = UpdateApkFiles.deletableNames(names, keepVersion = null)
        assertEquals(listOf("Aura-Hi-Res-Player-0.6.140.apk"), doomed)
    }

    @Test
    fun `the Downloads sweep is narrower than the private one`() {
        val names = listOf(
            "Aura-Hi-Res-Player-0.6.144.apk",
            "Aura-Hi-Res-Player-0.6.145.apk",
            "Aura-Hi-Res-Player-0.6.146-beta1-NOSUB.apk", // the owner's private build: never ours to delete
            "echomusic.apk",                              // in Downloads this could be anyone's
            "some-other-app.apk",
            "family.jpg",
        )
        val doomed = UpdateApkFiles.publicApksToDelete(
            names,
            keepNames = setOf("Aura-Hi-Res-Player-0.6.145.apk"),
        )
        assertEquals(listOf("Aura-Hi-Res-Player-0.6.144.apk"), doomed)
    }

    // ---- partial downloads ---------------------------------------------------------------------

    @Test
    fun `a partial download resumes and a finished one is recognised`() {
        assertEquals(PartState.RESUME, UpdateApkFiles.partState(bytesOnDisk = 10, expectedBytes = 100))
        assertEquals(PartState.COMPLETE, UpdateApkFiles.partState(bytesOnDisk = 100, expectedBytes = 100))
        assertEquals(PartState.RESTART, UpdateApkFiles.partState(bytesOnDisk = 0, expectedBytes = 100))
    }

    @Test
    fun `bytes on disk longer than the release restart instead of resuming`() {
        // Exactly what the old fixed-name updater produced: the previous release's APK sitting where
        // the new one was expected. Asking for "bytes=<bigger>-" can only ever be answered with 416.
        assertEquals(PartState.RESTART, UpdateApkFiles.partState(bytesOnDisk = 120, expectedBytes = 100))
    }

    @Test
    fun `with no size from the server what is on disk is still resumed`() {
        assertEquals(PartState.RESUME, UpdateApkFiles.partState(bytesOnDisk = 10, expectedBytes = 0))
        assertEquals(PartState.RESTART, UpdateApkFiles.partState(bytesOnDisk = 0, expectedBytes = 0))
    }

    // ---- the match predicate -------------------------------------------------------------------

    private val installed = 865L

    @Test
    fun `the APK that declares the offered version is accepted`() {
        assertEquals(
            ApkVerdict.MATCHES_TARGET,
            UpdateApkFiles.verdictFor("v0.6.146-beta1", "0.6.146-beta1", 866L, installed),
        )
    }

    @Test
    fun `the previous release on disk is rejected — the whole bug in one assertion`() {
        // The user is on 865 and is offered v0.6.146. The file on disk is 0.6.145 (versionCode 864):
        // it exists, it is a real Aura APK and it passes the signature check, so nothing but the
        // version inside it can tell it apart from the update the user asked for.
        assertEquals(
            ApkVerdict.REJECT,
            UpdateApkFiles.verdictFor("v0.6.146-beta1", "0.6.145", 864L, installed),
        )
    }

    @Test
    fun `re-installing the build already running is rejected too`() {
        assertEquals(
            ApkVerdict.REJECT,
            UpdateApkFiles.verdictFor("v0.6.147", "0.6.146-beta1", installed, installed),
        )
    }

    @Test
    fun `an unreadable archive is rejected — that is the truncation check`() {
        assertEquals(ApkVerdict.REJECT, UpdateApkFiles.verdictFor("v0.6.146", null, 0L, installed))
        assertEquals(ApkVerdict.REJECT, UpdateApkFiles.verdictFor("v0.6.146", "", 0L, installed))
    }

    @Test
    fun `a tag that drifts from the built versionName does not brick updating`() {
        // Refusing here would turn a cosmetic tag mismatch into an app that can never update again,
        // so a strictly newer build is still installed — but it is a different verdict, and logged.
        assertEquals(
            ApkVerdict.NEWER_THAN_INSTALLED,
            UpdateApkFiles.verdictFor("v0.6.147", "0.6.147-hotfix", 870L, installed),
        )
    }

    @Test
    fun `a downgrade offered by the server is never installed`() {
        assertEquals(
            ApkVerdict.REJECT,
            UpdateApkFiles.verdictFor("v0.6.100", "0.6.99", 700L, installed),
        )
    }

    // ---- what counts as an update ----------------------------------------------------------------

    @Test
    fun `a beta build is not offered the previous stable release`() {
        // GitHub's "latest release" skips prereleases, so a user on 0.6.146-beta1 is told about
        // 0.6.145. Under the old `current != target` rule that counted as an update, and taking it
        // installs the OLDER build — the reported symptom, reached from the check instead of the disk.
        assertFalse(UpdateApkFiles.isNewerRelease(target = "v0.6.145", current = "0.6.146-beta1"))
    }

    @Test
    fun `the finished release does supersede its own prereleases`() {
        assertTrue(UpdateApkFiles.isNewerRelease(target = "v0.6.146", current = "0.6.146-beta1"))
        assertTrue(UpdateApkFiles.isNewerRelease(target = "v0.6.146-beta2", current = "0.6.146-beta1"))
        assertFalse(UpdateApkFiles.isNewerRelease(target = "v0.6.146-beta1", current = "0.6.146"))
    }

    @Test
    fun `a normal update is still offered and the same version is not`() {
        assertTrue(UpdateApkFiles.isNewerRelease(target = "v0.6.146", current = "0.6.145"))
        assertFalse(UpdateApkFiles.isNewerRelease(target = "v0.6.145", current = "0.6.145"))
        assertFalse(UpdateApkFiles.isNewerRelease(target = "v0.6.144", current = "0.6.145"))
    }

    @Test
    fun `version parts compare numerically, not as text`() {
        assertTrue(UpdateApkFiles.isNewerRelease(target = "v0.6.10", current = "0.6.9"))
        assertFalse(UpdateApkFiles.isNewerRelease(target = "v0.6.9", current = "0.6.10"))
    }

    // ---- work tagging --------------------------------------------------------------------------

    @Test
    fun `the work tag identifies the release, not the tag spelling`() {
        assertEquals(UpdateApkFiles.versionTag("0.6.145"), UpdateApkFiles.versionTag("v0.6.145"))
        assertFalse(UpdateApkFiles.versionTag("0.6.145") == UpdateApkFiles.versionTag("0.6.146"))
    }
}
