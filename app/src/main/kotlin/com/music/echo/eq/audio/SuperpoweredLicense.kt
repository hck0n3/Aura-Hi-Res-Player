package iad1tya.echo.music.eq.audio

import android.content.Context
import iad1tya.echo.music.BuildConfig
import iad1tya.echo.music.echomusic.updater.ApkSignatureVerifier
import timber.log.Timber
import java.security.MessageDigest

/**
 * Where the Superpowered commercial licence key comes from at runtime.
 *
 * The key is a paid, per-application credential. It is NOT in source (see the SUPERPOWERED block in
 * `app/build.gradle.kts`): the build injects it from `local.properties` or the
 * `SUPERPOWERED_LICENSE_KEY` CI secret, and RELEASE builds embed it XORed with a keystream derived
 * from the SHA-256 of the certificate that signed them. Only a build signed with the owner's
 * certificate can undo that. A repackaged clone — which cannot be signed with that certificate,
 * because nobody else has the keystore — reconstructs garbage, we detect it here, and the clone runs
 * with no DSP instead of burning the owner's licence.
 *
 * ## The safety net matters more than the protection
 *
 * Every failure path in here ends in the same place: an EMPTY key. An empty key means
 * [CustomEqualizerAudioProcessor] never initializes the native engine at all, so audio takes the
 * plain Kotlin bypass — bit-identical passthrough, never silence, never distortion — and the EQ
 * screen shows the "engine unavailable" banner instead of drawing a curve that does nothing.
 * Guessing, retrying with a different transformation, or handing the engine a key we are not sure
 * about are all worse than saying "no key".
 *
 * ## Nothing here is ever logged
 *
 * Not the key, not the blob, not a fragment, not a length, not the certificate hash. The app log is
 * user-shareable. Only the verdict (`ok` / `absent` / `unbindable`) is recorded.
 */
object SuperpoweredLicense {

    /**
     * Domain separator mixed into the keystream. MUST match the literal in `superpoweredBind` in
     * `app/build.gradle.kts` — if the two ever disagree, every release reconstructs garbage.
     */
    private const val KEYSTREAM_DOMAIN = "aura-sp-v1"

    /**
     * Resolved once per process. `""` means "no usable key" and is a perfectly valid, cached answer —
     * re-deriving on every [CustomEqualizerAudioProcessor.onConfigure] would repeat a binder call to
     * PackageManager on the media3 playback thread for a result that cannot change.
     */
    @Volatile
    private var cached: String? = null

    private val resolveLock = Any()

    /**
     * The licence key to hand to the native engine, or `""` when there is none we can trust.
     *
     * Safe to call from any thread and from the playback thread in particular: the first call does a
     * PackageManager lookup (a few ms) and every later call is a volatile read.
     */
    fun resolveKey(context: Context): String {
        cached?.let { return it }
        synchronized(resolveLock) {
            cached?.let { return it }
            val resolved = derive(context)
            cached = resolved
            return resolved
        }
    }

    private fun derive(context: Context): String {
        val stored = BuildConfig.SUPERPOWERED_LICENSE
        if (stored.isEmpty()) {
            Timber.i(
                "SUPERPOWERED licence=absent — this build was made without SUPERPOWERED_LICENSE_KEY, so the " +
                    "DSP engine will not be started. Audio plays untouched; the EQ reports itself unavailable.",
            )
            return ""
        }

        // Debug builds (and any release built where the signing certificate was unknown) ship the key
        // as-is. Deriving there is impossible by construction — a debug APK is signed with the Android
        // debug certificate — and attempting it would take the EQ away from the owner while he develops.
        if (!BuildConfig.SUPERPOWERED_LICENSE_BOUND) {
            Timber.i("SUPERPOWERED licence=ok binding=none")
            return stored
        }

        val outcome = runCatching { reconstruct(context, stored) }
        outcome.getOrNull()?.let { key ->
            Timber.i("SUPERPOWERED licence=ok binding=certificate")
            return key
        }

        // Two distinct failures, and they must not be confused in a log the owner will read: either we
        // could not ASK who signed us (PackageManager threw, malformed blob, ...) or we asked and the
        // answer was a certificate this key was not bound to.
        //
        // The throwable itself is deliberately NOT passed to Timber: an exception from PackageManager
        // can carry the package name and APK path, and this file's rule is that nothing licence-adjacent
        // reaches a user-shareable log. The class name is enough to tell the two apart.
        val error = outcome.exceptionOrNull()
        if (error != null) {
            Timber.i(
                "SUPERPOWERED licence=unbindable reason=${error.javaClass.simpleName} — could not read this " +
                    "app's signing certificate, so the licence key cannot be reconstructed. The DSP engine " +
                    "will not be started; audio plays untouched.",
            )
        } else {
            Timber.i(
                "SUPERPOWERED licence=unbindable reason=signature_mismatch — this build is not signed with " +
                    "the certificate its licence was bound to (a repackaged copy, or a build signed with a " +
                    "different key). The DSP engine will not be started; audio plays untouched.",
            )
        }
        return ""
    }

    /**
     * Undo the build-time transformation, or return null if this app is not the one the key was bound to.
     *
     * ApkSignatureVerifier is the single implementation of "which certificate signed me?" in this
     * codebase — the in-app updater asks it the same question before installing an APK.
     */
    private fun reconstruct(context: Context, stored: String): String? = reconstruct(
        stored = stored,
        expectedChecksum = BuildConfig.SUPERPOWERED_LICENSE_CHECK,
        signatureHashes = ApkSignatureVerifier.installedSignatureHashes(context),
    )

    /**
     * The pure half of [reconstruct]: everything except asking Android who signed us.
     *
     * Tries every certificate the package is signed with (normally exactly one; a multi-signer APK has
     * more) and accepts the first candidate whose checksum matches the one recorded at build time. That
     * checksum is what makes this exact rather than a guess: a wrong certificate produces 32 bits that
     * have no reason to match, so we find out BEFORE the engine is ever handed anything.
     *
     * `internal` (not private) only so `SuperpoweredLicenseTest` can pin it to a golden vector shared
     * with `superpoweredBind` in `app/build.gradle.kts`. If those two implementations ever drift, every
     * release reconstructs garbage and the whole user base loses the EQ at once — so it gets a test.
     */
    internal fun reconstruct(
        stored: String,
        expectedChecksum: String,
        signatureHashes: Collection<String>,
    ): String? {
        if (expectedChecksum.length != 8) return null

        // java.util.Base64 (API 26+, and minSdk is 26) rather than android.util.Base64: it is the exact
        // counterpart of the encoder the build script uses, and it also works under plain JVM unit tests.
        val blob = java.util.Base64.getDecoder().decode(stored)
        if (blob.isEmpty()) return null

        for (hash in signatureHashes.sorted()) {
            val certDigest = hexToBytes(hash) ?: continue
            val candidateBytes = ByteArray(blob.size)
            val stream = keystream(certDigest, blob.size)
            for (i in blob.indices) {
                candidateBytes[i] = (blob[i].toInt() xor stream[i].toInt()).toByte()
            }
            if (checksumOf(candidateBytes) == expectedChecksum) {
                return String(candidateBytes, Charsets.UTF_8)
            }
        }
        return null
    }

    /**
     * SHA-256(domain || certDigest || counter), concatenated until [length] bytes are available.
     *
     * MUST stay byte-for-byte identical to `superpoweredBind` in `app/build.gradle.kts`.
     */
    private fun keystream(certDigest: ByteArray, length: Int): ByteArray {
        val out = ByteArray(length)
        var offset = 0
        var counter = 0
        while (offset < length) {
            val md = MessageDigest.getInstance("SHA-256")
            md.update(KEYSTREAM_DOMAIN.toByteArray(Charsets.UTF_8))
            md.update(certDigest)
            md.update(
                byteArrayOf(
                    (counter ushr 24).toByte(),
                    (counter ushr 16).toByte(),
                    (counter ushr 8).toByte(),
                    counter.toByte(),
                ),
            )
            val block = md.digest()
            var i = 0
            while (i < block.size && offset < length) {
                out[offset] = block[i]
                offset++
                i++
            }
            counter++
        }
        return out
    }

    /** First 4 bytes of SHA-256(key), lowercase hex. Mirrors `superpoweredKeyChecksum` in the build script. */
    private fun checksumOf(keyBytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(keyBytes)
            .take(4).joinToString("") { "%02x".format(it) }

    /** 64 lowercase hex chars -> 32 bytes, or null if the string is not that. */
    private fun hexToBytes(hex: String): ByteArray? {
        if (hex.length != 64) return null
        val out = ByteArray(32)
        for (i in 0 until 32) {
            val high = Character.digit(hex[i * 2], 16)
            val low = Character.digit(hex[i * 2 + 1], 16)
            if (high < 0 || low < 0) return null
            out[i] = ((high shl 4) or low).toByte()
        }
        return out
    }
}
