package iad1tya.echo.music.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * The rate limiter in `reportException` and the stack-trace collapser in `AppLogger` both key on
 * [exceptionSignature]. It has to hold two properties at once, and getting either wrong is a real
 * failure the owner would feel:
 *
 *  - COLLAPSE a repeat, or a fault firing in a loop floods the 256 KB log and rotates away the very
 *    traces the report exists to carry. Keying on the raw message defeated this whenever the message
 *    embedded a moving byte offset or position — which is exactly the kind that loops.
 *  - NEVER collapse two DIFFERENT faults, or the second one silently disappears from the log.
 */
class LogSignatureTest {

    /** A throwable with a stack trace we control, so the signature is deterministic. */
    private fun throwableAt(
        message: String,
        clazz: String = "com.example.Resolver",
        method: String = "resolve",
        line: Int = 42,
    ): Throwable = RuntimeException(message).apply {
        stackTrace = arrayOf(StackTraceElement(clazz, method, "Resolver.kt", line))
    }

    // ── Must COLLAPSE: same fault, varying detail ────────────────────────────────────────────────

    @Test
    fun aMovingByteOffsetDoesNotDefeatTheLimiter() {
        assertEquals(
            exceptionSignature(throwableAt("read failed at offset 81920")),
            exceptionSignature(throwableAt("read failed at offset 98304")),
        )
    }

    @Test
    fun aMovingPlaybackPositionCollapses() {
        assertEquals(
            exceptionSignature(throwableAt("decoder stalled at position 12045ms")),
            exceptionSignature(throwableAt("decoder stalled at position 315880ms")),
        )
    }

    @Test
    fun aVaryingUrlCollapses() {
        assertEquals(
            exceptionSignature(throwableAt("failed https://rr1.googlevideo.com/videoplayback?id=aaa&pot=X")),
            exceptionSignature(throwableAt("failed https://rr7.googlevideo.com/videoplayback?id=bbb&pot=Y")),
        )
    }

    @Test
    fun aVaryingHexBlobCollapses() {
        assertEquals(
            exceptionSignature(throwableAt("bad session 0xDEADBEEF")),
            exceptionSignature(throwableAt("bad session 0xCAFEF00D")),
        )
    }

    @Test
    fun aRetryCounterCollapses() {
        assertEquals(
            exceptionSignature(throwableAt("attempt 1 of 3 failed")),
            exceptionSignature(throwableAt("attempt 3 of 3 failed")),
        )
    }

    // ── Must NOT collapse: genuinely different faults ────────────────────────────────────────────

    @Test
    fun twoDifferentMessagesStaySeparate() {
        assertNotEquals(
            exceptionSignature(throwableAt("read failed at offset 81920")),
            exceptionSignature(throwableAt("connection reset by peer")),
        )
    }

    @Test
    fun theSameMessageFromADifferentLineStaysSeparate() {
        // Digits in the FRAME are kept verbatim, so a different throw site is a different signature.
        assertNotEquals(
            exceptionSignature(throwableAt("read failed", line = 42)),
            exceptionSignature(throwableAt("read failed", line = 91)),
        )
    }

    @Test
    fun theSameMessageFromADifferentClassStaysSeparate() {
        assertNotEquals(
            exceptionSignature(throwableAt("read failed", clazz = "com.example.Resolver")),
            exceptionSignature(throwableAt("read failed", clazz = "com.example.Downloader")),
        )
    }

    @Test
    fun aDifferentExceptionTypeStaysSeparate() {
        val runtime = RuntimeException("boom").apply {
            stackTrace = arrayOf(StackTraceElement("com.example.A", "b", "A.kt", 1))
        }
        val io = java.io.IOException("boom").apply {
            stackTrace = arrayOf(StackTraceElement("com.example.A", "b", "A.kt", 1))
        }
        assertNotEquals(exceptionSignature(runtime), exceptionSignature(io))
    }

    @Test
    fun aDifferentCauseChainStaysSeparate() {
        val plain = RuntimeException("boom").apply {
            stackTrace = arrayOf(StackTraceElement("com.example.A", "b", "A.kt", 1))
        }
        val caused = RuntimeException("boom", java.io.IOException("socket")).apply {
            stackTrace = arrayOf(StackTraceElement("com.example.A", "b", "A.kt", 1))
        }
        assertNotEquals(exceptionSignature(plain), exceptionSignature(caused))
    }

    // ── The normaliser itself ────────────────────────────────────────────────────────────────────

    @Test
    fun normaliserReplacesVaryingValuesButKeepsTheShapeReadable() {
        assertEquals("read failed at offset #", normalizeLogMessage("read failed at offset 81920"))
        assertEquals("failed #", normalizeLogMessage("failed https://x.com/a?b=1"))
    }

    @Test
    fun normaliserLeavesTextWithNoVaryingPartAlone() {
        val line = "connection reset by peer"
        assertEquals(line, normalizeLogMessage(line))
    }

    @Test
    fun aThrowableWithNoStackTraceStillProducesAStableSignature() {
        val bare = RuntimeException("boom").apply { stackTrace = emptyArray() }
        assertEquals(exceptionSignature(bare), exceptionSignature(bare))
    }
}
