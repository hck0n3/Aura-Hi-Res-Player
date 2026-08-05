package iad1tya.echo.music.echomusic.updater

import android.content.Context
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.os.Build
import java.io.File
import java.security.MessageDigest

/**
 * Defense-in-depth for the in-app updater: before launching the system installer on a downloaded APK,
 * confirm it is signed with the SAME certificate as the currently-installed app. Android also rejects a
 * signature mismatch at install time, but checking up-front lets us abort cleanly (and never hand the
 * user a tampered file) instead of relying on a confusing system error.
 */
object ApkSignatureVerifier {

    fun matchesInstalledSignature(context: Context, apkFile: File): Boolean {
        return try {
            if (!apkFile.exists()) return false
            val installed = installedSignatureHashes(context)
            val downloaded = apkSignatureHashes(context, apkFile.absolutePath)
            installed.isNotEmpty() && downloaded.isNotEmpty() && installed == downloaded
        } catch (e: Exception) {
            timber.log.Timber.e(e, "APK signature verification failed")
            false
        }
    }

    /**
     * SHA-256 (lowercase hex) of every certificate the INSTALLED app is signed with — the app's own
     * identity, as Android reports it.
     *
     * Public because it is also the input to the Superpowered licence binding
     * ([iad1tya.echo.music.eq.audio.SuperpoweredLicense]): "which certificate signed me?" must have
     * exactly one implementation in this codebase, so the updater's answer and the licence's answer can
     * never drift apart. May throw (e.g. [PackageManager.NameNotFoundException]); callers handle it.
     */
    fun installedSignatureHashes(context: Context): Set<String> {
        val pm = context.packageManager
        val pkg = context.packageName
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val info = pm.getPackageInfo(pkg, PackageManager.GET_SIGNING_CERTIFICATES)
            (info.signingInfo?.apkContentsSigners).toHashes()
        } else {
            @Suppress("DEPRECATION")
            val info = pm.getPackageInfo(pkg, PackageManager.GET_SIGNATURES)
            @Suppress("DEPRECATION")
            info.signatures.toHashes()
        }
    }

    private fun apkSignatureHashes(context: Context, apkPath: String): Set<String> {
        val pm = context.packageManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val info = pm.getPackageArchiveInfo(apkPath, PackageManager.GET_SIGNING_CERTIFICATES)
            (info?.signingInfo?.apkContentsSigners).toHashes()
        } else {
            @Suppress("DEPRECATION")
            val info = pm.getPackageArchiveInfo(apkPath, PackageManager.GET_SIGNATURES)
            @Suppress("DEPRECATION")
            info?.signatures.toHashes()
        }
    }

    private fun Array<Signature>?.toHashes(): Set<String> =
        this?.mapNotNull { sig ->
            runCatching {
                MessageDigest.getInstance("SHA-256").digest(sig.toByteArray())
                    .joinToString("") { "%02x".format(it) }
            }.getOrNull()
        }?.toSet().orEmpty()
}
