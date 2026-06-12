package com.music.innertube.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ParseCookieStringTest {

    @Test
    fun parsesYouTubeMusicCookieAndDetectsLogin() {
        val cookie = "VISITOR_INFO1_LIVE=abc123; SAPISID=xyz789/AbCdEf; HSID=A1B2C3; SSID=secret"
        val parsed = parseCookieString(cookie)
        assertEquals("xyz789/AbCdEf", parsed["SAPISID"])
        assertEquals("abc123", parsed["VISITOR_INFO1_LIVE"])
        // Same check the app uses to consider the user logged in
        assertTrue("SAPISID" in parsed)
    }

    @Test
    fun missingSapisidMeansLoggedOut() {
        val parsed = parseCookieString("VISITOR_INFO1_LIVE=abc123; PREF=f1=50000000")
        assertFalse("SAPISID" in parsed)
    }

    @Test
    fun keepsEqualsSignsInsideValues() {
        val parsed = parseCookieString("PREF=f1=50000000&f6=8; SAPISID=a=b=c")
        assertEquals("f1=50000000&f6=8", parsed["PREF"])
        assertEquals("a=b=c", parsed["SAPISID"])
    }

    @Test
    fun emptyCookieGivesEmptyMap() {
        assertTrue(parseCookieString("").isEmpty())
    }

    @Test
    fun ignoresPartsWithoutEquals() {
        val parsed = parseCookieString("garbage; SAPISID=ok")
        assertEquals(mapOf("SAPISID" to "ok"), parsed)
    }
}
