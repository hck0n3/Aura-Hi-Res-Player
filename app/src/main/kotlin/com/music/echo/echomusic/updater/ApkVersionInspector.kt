package iad1tya.echo.music.echomusic.updater

import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.pm.PackageInfoCompat
import java.io.File

/**
 * Reads the version out of an APK file on disk.
 *
 * `PackageManager.getPackageArchiveInfo` parses the archive itself, so its answer cannot be faked by a
 * stale, half-downloaded or corrupted file: a truncated APK simply fails to parse and comes back null.
 * That makes it the last line of defence before the installer is launched — the updater's own state
 * (a filename, a stored path, a WorkManager result) can all be stale, but the archive cannot lie about
 * what it contains.
 *
 * The decision itself lives in [UpdateApkFiles.verdictFor] so it can be unit-tested without a device.
 */
object ApkVersionInspector {

    data class ApkInfo(val versionName: String?, val versionCode: Long)

    /** Version declared by the APK at [file]; `ApkInfo(null, 0)` when it cannot be parsed. */
    fun read(context: Context, file: File): ApkInfo = try {
        if (!file.isFile || file.length() <= 0L) {
            ApkInfo(null, 0L)
        } else {
            val info = context.packageManager.getPackageArchiveInfo(file.absolutePath, 0)
            if (info == null) {
                ApkInfo(null, 0L)
            } else {
                ApkInfo(info.versionName, PackageInfoCompat.getLongVersionCode(info))
            }
        }
    } catch (e: Exception) {
        timber.log.Timber.w(e, "Could not read APK version")
        ApkInfo(null, 0L)
    }

    /** versionCode of the running app, used to reject an APK that is not actually newer. */
    fun installedVersionCode(context: Context): Long = try {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        PackageInfoCompat.getLongVersionCode(info)
    } catch (e: Exception) {
        0L
    }

    /** Is the file on disk really the release we were told to fetch? */
    fun verdict(context: Context, file: File, targetVersion: String): UpdateApkFiles.ApkVerdict {
        val apk = read(context, file)
        return UpdateApkFiles.verdictFor(
            targetVersion = targetVersion,
            apkVersionName = apk.versionName,
            apkVersionCode = apk.versionCode,
            installedVersionCode = installedVersionCode(context),
        )
    }

    /**
     * Full pre-install gate: the file must exist, declare an acceptable version, and be signed with our
     * certificate. Returns true only when all three hold.
     */
    fun isInstallable(context: Context, file: File, targetVersion: String): Boolean {
        if (!file.isFile) return false
        val verdict = verdict(context, file, targetVersion)
        if (verdict == UpdateApkFiles.ApkVerdict.REJECT) {
            timber.log.Timber.w("Downloaded APK rejected: not version %s", targetVersion)
            return false
        }
        if (verdict == UpdateApkFiles.ApkVerdict.NEWER_THAN_INSTALLED) {
            timber.log.Timber.w("APK versionName differs from tag %s but is newer than installed", targetVersion)
        }
        return ApkSignatureVerifier.matchesInstalledSignature(context, file)
    }
}
