package iad1tya.echo.music.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The shareable log is emailed to the owner by paying customers. A credential in it is a real leak —
 * one already shipped (PoTokenWebView logged a full dataSyncId at ERROR). These tests pin the three
 * the owner called out by name — a URL with `?pot=`, a cookie header, and a dataSyncId — plus the
 * neighbours that would leak the same account.
 */
class LogRedactionTest {

    private fun assertScrubbed(secret: String, input: String) {
        val out = LogRedaction.redact(input)
        assertFalse("leaked <$secret> in: $out", out.contains(secret))
        assertTrue("nothing was redacted in: $out", out.contains("<redacted>"))
    }

    // ── The three the owner named ────────────────────────────────────────────────────────────────

    @Test
    fun poTokenQueryParamIsRedacted() {
        assertScrubbed(
            "MnQ1jVLPd6cIsBKF7A9x",
            "java.io.IOException: unexpected end of stream for " +
                "https://rr3---sn-4g5e6nz7.googlevideo.com/videoplayback?expire=1754236800&" +
                "id=abc123&pot=MnQ1jVLPd6cIsBKF7A9x&mime=audio%2Fwebm",
        )
    }

    @Test
    fun poTokenRedactionKeepsTheRestOfTheUrlDiagnosable() {
        // Over-redaction is safe, but scrubbing the whole URL would destroy the videoId and the expiry
        // that make a stream failure attributable at all.
        val out = LogRedaction.redact(
            "GET https://rr3.googlevideo.com/videoplayback?expire=1754236800&id=abc123&pot=SECRETVALUE&cpn=xyz"
        )
        assertTrue(out, out.contains("expire=1754236800"))
        assertTrue(out, out.contains("id=abc123"))
        assertTrue(out, out.contains("cpn=xyz"))
        assertTrue(out, out.contains("pot=<redacted>"))
    }

    @Test
    fun theSignatureCipherParamsAreRedacted() {
        // `sig=` and the bare `s=` are the signatureCipher pair. `s=` is one character, so it is the
        // easiest one for a narrower regex to miss.
        assertScrubbed("SIGNATUREVALUE", "https://rr1.googlevideo.com/videoplayback?id=abc&sig=SIGNATUREVALUE")
        assertScrubbed("CIPHERVALUE", "https://rr1.googlevideo.com/videoplayback?id=abc&s=CIPHERVALUE&sp=sig")
    }

    @Test
    fun cookieHeaderIsRedactedIncludingEveryPairAfterTheFirst() {
        val out = LogRedaction.redact(
            "Cookie: SAPISID=aaaaaaaaaaaa; __Secure-3PAPISID=bbbbbbbbbbbb; LOGIN_INFO=cccccccccccc"
        )
        // The `;`-separated tail is exactly what a "stop at the first delimiter" rule would have left
        // in the clear.
        assertFalse(out, out.contains("aaaaaaaaaaaa"))
        assertFalse(out, out.contains("bbbbbbbbbbbb"))
        assertFalse(out, out.contains("cccccccccccc"))
        assertEquals("Cookie: <redacted>", out)
    }

    @Test
    fun cookieHeaderRedactionStopsAtTheEndOfItsOwnLine() {
        val out = LogRedaction.redact("Cookie: SAPISID=secretvalue\nPlaying videoId=_KZEkEb_dvA")
        assertFalse(out, out.contains("secretvalue"))
        assertTrue("swallowed the next line: $out", out.contains("Playing videoId=_KZEkEb_dvA"))
    }

    @Test
    fun dataSyncIdIsRedacted() {
        assertScrubbed("abcdef123456", "dataSyncId=abcdef123456 visitor session started")
        assertScrubbed("abcdef123456", "data_sync_id: abcdef123456")
        assertScrubbed("abcdef123456", """{"dataSyncId":"abcdef123456","clientName":"ANDROID_MUSIC"}""")
    }

    /**
     * The shape that actually leaked (PoTokenWebView's `generatePoToken($identifier)` at ERROR) was a
     * session id with NO key next to it, and a bare opaque string is indistinguishable from a playlist
     * id — no chokepoint can catch that in general. It is fixed at the call site by logging only the
     * length, and this pins that the length form carries nothing recoverable.
     */
    @Test
    fun theLengthOnlyFormOfAnIdentifierCarriesNoSecret() {
        val line = "generatePoToken timed out after 10000ms (idLen=94)"
        assertEquals(line, LogRedaction.redact(line))
    }

    // ── The neighbours that leak the same account ───────────────────────────────────────────────

    @Test
    fun bareCookiePairsWithNoHeaderPrefixAreRedacted() {
        assertScrubbed("aaaaaaaaaaaaaaaa", "restoring session SAPISID=aaaaaaaaaaaaaaaa for request")
    }

    @Test
    fun authorizationHeaderAndBearerTokenAreRedacted() {
        assertScrubbed("ya29.a0AfH6SMBx", "Authorization: Bearer ya29.a0AfH6SMBx")
        assertScrubbed("ya29.a0AfH6SMBx", "request failed with Bearer ya29.a0AfH6SMBx")
        assertScrubbed("1a2b3c4d5e6f", "SAPISIDHASH 1754236800_1a2b3c4d5e6f")
    }

    @Test
    fun visitorDataIsRedacted() {
        assertScrubbed("CgtBQkNERUZHSElKSw", """{"visitorData":"CgtBQkNERUZHSElKSw"}""")
        assertScrubbed("CgtBQkNERUZHSElKSw", "X-Goog-Visitor-Id: CgtBQkNERUZHSElKSw")
    }

    @Test
    fun accountEmailIsRedacted() {
        assertScrubbed("toberto4000@gmail.com", "sync failed for account toberto4000@gmail.com")
    }

    @Test
    fun licenceKeysAreRedacted() {
        // Gumroad grouped form and the plain UUID form.
        assertScrubbed("A1B2C3D4-E5F6A7B8-C9D0E1F2-A3B4C5D6", "licence check for A1B2C3D4-E5F6A7B8-C9D0E1F2-A3B4C5D6 failed")
        assertScrubbed("550e8400-e29b-41d4-a716-446655440000", "key=550e8400-e29b-41d4-a716-446655440000")
        assertScrubbed("hunter2hunter2", "licenseKey=hunter2hunter2 rejected")
    }

    @Test
    fun accessAndRefreshTokensAreRedacted() {
        assertScrubbed("tok_abcdef123456", """{"access_token":"tok_abcdef123456"}""")
        assertScrubbed("ref_abcdef123456", "refresh_token=ref_abcdef123456")
        assertScrubbed("qobuzsecret123", "?app_secret=x&access_token=qobuzsecret123")
    }

    @Test
    fun apiKeyQueryParamIsRedacted() {
        assertScrubbed("AIzaSyDummyKeyValue123", "https://youtubei.googleapis.com/youtubei/v1/player?key=AIzaSyDummyKeyValue123")
    }

    @Test
    fun aBareOpaqueBlobIsRedacted() {
        // The backstop for a raw token printed with no key at all to key off.
        val blob = "A".repeat(140)
        assertScrubbed(blob, "poToken generated $blob")
    }

    // ── Over-redaction guards: the log must stay diagnosable ────────────────────────────────────

    @Test
    fun ordinaryDiagnosticLinesAreLeftAlone() {
        val untouched = listOf(
            "RESOLVE_TIMING videoId=_KZEkEb_dvA total=842ms client=ANDROID_MUSIC ok",
            "CROSSFADE_TRACE swap begin from=_KZEkEb_dvA to=dQw4w9WgXcQ at=4820ms",
            "NO_REPEAT contextId=PL:RDAMVM_KZEkEb_dvA pool=42 played=17",
            "Playback failed: MediaCodecRenderer error code 3003 at position 12045ms",
            "java.net.SocketTimeoutException: timeout at okhttp3.internal.http2.Http2Stream.takeHeaders",
        )
        untouched.forEach { assertEquals("wrongly redacted: $it", it, LogRedaction.redact(it)) }
    }

    @Test
    fun theWordCookieInProseIsNotTreatedAsAHeader() {
        val line = "cookie refresh failed after 3 attempts"
        assertEquals(line, LogRedaction.redact(line))
    }

    @Test
    fun videoAndPlaylistIdsAreShortEnoughToSurviveTheOpaqueBlobRule() {
        val line = "queue seeded from PLZbXA4lyCtqoWi2FS6MKfPfmdWjs1Zj0k with videoId _KZEkEb_dvA"
        assertEquals(line, LogRedaction.redact(line))
    }

    @Test
    fun emptyInputIsReturnedUnchanged() {
        assertEquals("", LogRedaction.redact(""))
    }

    @Test
    fun redactionIsIdempotent() {
        val once = LogRedaction.redact("Cookie: SAPISID=secret\ndataSyncId=abc123456789")
        assertEquals(once, LogRedaction.redact(once))
    }
}
