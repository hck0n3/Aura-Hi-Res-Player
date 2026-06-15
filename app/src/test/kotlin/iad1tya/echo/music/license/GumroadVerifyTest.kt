package iad1tya.echo.music.license

import org.junit.Assert.assertEquals
import org.junit.Test

class GumroadVerifyTest {

    @Test fun activeWhenAllSubscriptionFieldsNull() {
        val body = """
            {"success":true,"purchase":{
              "subscription_cancelled_at":null,
              "subscription_failed_at":null,
              "subscription_ended_at":null}}
        """.trimIndent()
        assertEquals(GumroadVerify.Result.ACTIVE, GumroadVerify.interpret(body))
    }

    @Test fun endedWhenCancelledHasDate() {
        val body = """{"success":true,"purchase":{"subscription_cancelled_at":"2026-05-01T00:00:00Z"}}"""
        assertEquals(GumroadVerify.Result.ENDED, GumroadVerify.interpret(body))
    }

    @Test fun endedWhenFailedHasDate() {
        val body = """{"success":true,"purchase":{"subscription_failed_at":"2026-05-01T00:00:00Z"}}"""
        assertEquals(GumroadVerify.Result.ENDED, GumroadVerify.interpret(body))
    }

    @Test fun endedWhenEndedHasDate() {
        val body = """{"success":true,"purchase":{"subscription_ended_at":"2026-05-01T00:00:00Z"}}"""
        assertEquals(GumroadVerify.Result.ENDED, GumroadVerify.interpret(body))
    }

    @Test fun activeWhenSubscriptionFieldsAbsent() {
        val body = """{"success":true,"purchase":{"email":"x@y.com"}}"""
        assertEquals(GumroadVerify.Result.ACTIVE, GumroadVerify.interpret(body))
    }

    @Test fun activeWhenPurchaseIsJsonNull() {
        val body = """{"success":true,"purchase":null}"""
        assertEquals(GumroadVerify.Result.ACTIVE, GumroadVerify.interpret(body))
    }

    @Test fun invalidKeyWhenSuccessFalse() {
        val body = """{"success":false,"message":"That license does not exist for the provided product."}"""
        assertEquals(GumroadVerify.Result.INVALID_KEY, GumroadVerify.interpret(body))
    }

    @Test fun networkErrorWhenBodyNotJson() {
        assertEquals(GumroadVerify.Result.NETWORK_ERROR, GumroadVerify.interpret("<html>502 Bad Gateway</html>"))
    }
}
