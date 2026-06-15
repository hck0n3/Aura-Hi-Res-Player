package iad1tya.echo.music.license

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Result of a license check performed by the backend Worker. */
enum class LicenseStatus { ACTIVE, ENDED, INVALID_KEY, DEVICE_MISMATCH, NETWORK_ERROR }

/** Pure parser of the Worker's `{ "status": "..." }` JSON. Unit-testable with plain JUnit. */
object LicenseStatusParser {

    private val json = Json { ignoreUnknownKeys = true }

    fun parse(body: String): LicenseStatus =
        try {
            when (json.parseToJsonElement(body).jsonObject["status"]?.jsonPrimitive?.contentOrNull) {
                "active" -> LicenseStatus.ACTIVE
                "ended" -> LicenseStatus.ENDED
                "device_mismatch" -> LicenseStatus.DEVICE_MISMATCH
                "invalid" -> LicenseStatus.INVALID_KEY
                else -> LicenseStatus.NETWORK_ERROR
            }
        } catch (e: Exception) {
            LicenseStatus.NETWORK_ERROR
        }
}
