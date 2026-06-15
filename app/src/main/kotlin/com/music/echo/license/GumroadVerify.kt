package iad1tya.echo.music.license

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Pure interpreter of the Gumroad /v2/licenses/verify JSON response. No I/O, so it is unit-testable
 * with plain JUnit. A membership is ACTIVE only when the three subscription timestamps are null.
 */
object GumroadVerify {

    enum class Result { ACTIVE, ENDED, INVALID_KEY, NETWORK_ERROR }

    private val json = Json { ignoreUnknownKeys = true }

    fun interpret(body: String): Result =
        try {
            val root = json.parseToJsonElement(body).jsonObject
            val success = root["success"]?.jsonPrimitive?.booleanOrNull ?: false
            if (!success) {
                Result.INVALID_KEY
            } else {
                val purchase = root["purchase"]?.takeIf { it !is JsonNull }?.jsonObject
                if (purchase == null) {
                    Result.ACTIVE
                } else {
                    val cancelled = purchase["subscription_cancelled_at"]?.jsonPrimitive?.contentOrNull
                    val failed = purchase["subscription_failed_at"]?.jsonPrimitive?.contentOrNull
                    val ended = purchase["subscription_ended_at"]?.jsonPrimitive?.contentOrNull
                    if (cancelled == null && failed == null && ended == null) Result.ACTIVE
                    else Result.ENDED
                }
            }
        } catch (e: Exception) {
            Result.NETWORK_ERROR
        }
}
