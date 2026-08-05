package iad1tya.echo.music.eq.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Pins the runtime half of the Superpowered licence binding.
 *
 * The transformation is written twice — `superpoweredBind` in `app/build.gradle.kts` produces the blob
 * at build time, [SuperpoweredLicense] undoes it on the device. Nothing at runtime can detect a
 * mismatch between the two beyond "the key is wrong": the app would ship, install, play music and
 * quietly have no EQ for every paying user at once. So both sides are pinned to the SAME golden vector
 * — the build script `check()`s it at configuration time, this test checks it here.
 *
 * If you change the algorithm, change it in both places and regenerate [GOLDEN_BLOB].
 */
class SuperpoweredLicenseTest {

    private companion object {
        /** SHA-256 of a fictional signing certificate, in the lowercase hex ApkSignatureVerifier emits. */
        const val GOLDEN_CERT = "00112233445566778899aabbccddeeff00112233445566778899aabbccddeeff"

        /** 41 bytes on purpose: longer than one SHA-256 block, so the keystream counter must advance. */
        const val GOLDEN_KEY = "AuraGoldenVectorKey-0123456789+/=abcdefgh"

        /** What `superpoweredBind(GOLDEN_KEY, GOLDEN_CERT)` produces, Base64. */
        const val GOLDEN_BLOB = "jtt6qa0FokrPxZizx+797Uuw9+VKqbM6zs3zxiEXCxIE7azZl951bZ8="

        /** First 4 bytes of SHA-256(GOLDEN_KEY), hex — `superpoweredKeyChecksum` in the build script. */
        const val GOLDEN_CHECKSUM = "61c334f1"

        /** Any other certificate. Stands in for "someone repackaged and re-signed the APK". */
        const val OTHER_CERT = "ffeeddccbbaa99887766554433221100ffeeddccbbaa99887766554433221100"
    }

    @Test fun ownCertificateReconstructsTheKeyExactly() {
        assertEquals(
            GOLDEN_KEY,
            SuperpoweredLicense.reconstruct(GOLDEN_BLOB, GOLDEN_CHECKSUM, setOf(GOLDEN_CERT)),
        )
    }

    /**
     * Not "a wrong key" that would then be handed to the engine — no key at all, which is what makes the
     * processor skip native init entirely and pass audio through untouched.
     */
    @Test fun repackagedCloneWithAnotherCertificateGetsNothing() {
        assertNull(SuperpoweredLicense.reconstruct(GOLDEN_BLOB, GOLDEN_CHECKSUM, setOf(OTHER_CERT)))
    }

    @Test fun multiSignerPackageStillFindsItsOwnCertificate() {
        assertEquals(
            GOLDEN_KEY,
            SuperpoweredLicense.reconstruct(GOLDEN_BLOB, GOLDEN_CHECKSUM, setOf(OTHER_CERT, GOLDEN_CERT)),
        )
    }

    @Test fun noSignaturesAtAllIsACleanNoKeyNeverACrash() {
        assertNull(SuperpoweredLicense.reconstruct(GOLDEN_BLOB, GOLDEN_CHECKSUM, emptySet()))
    }

    @Test fun malformedCertificateHashesAreSkippedInsteadOfThrowing() {
        assertNull(
            SuperpoweredLicense.reconstruct(
                GOLDEN_BLOB,
                GOLDEN_CHECKSUM,
                setOf("", "zz", "not-hex", GOLDEN_CERT.dropLast(1)),
            ),
        )
    }

    /** A build with no checksum cannot prove anything, so it must refuse rather than guess. */
    @Test fun missingChecksumRefusesToGuess() {
        assertNull(SuperpoweredLicense.reconstruct(GOLDEN_BLOB, "", setOf(GOLDEN_CERT)))
    }
}
