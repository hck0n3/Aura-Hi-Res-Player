package iad1tya.echo.music.license

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.content.TextContent
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Talks to the JR MUSIC PRO license Worker, which verifies the Gumroad subscription and enforces one
 * device per key. Any network/parse failure maps to [LicenseStatus.NETWORK_ERROR] so the caller can
 * fall back to the offline grace window.
 */
class LicenseBackendClient(
    private val http: HttpClient = HttpClient(OkHttp),
) {
    suspend fun verify(licenseKey: String, deviceId: String): LicenseStatus =
        try {
            val payload = buildJsonObject {
                put("license_key", licenseKey)
                put("device_id", deviceId)
            }.toString()
            val body = http.post(VERIFY_URL) {
                setBody(TextContent(payload, ContentType.Application.Json))
            }.bodyAsText()
            LicenseStatusParser.parse(body)
        } catch (e: Exception) {
            LicenseStatus.NETWORK_ERROR
        }

    companion object {
        private const val VERIFY_URL = "https://round-math-d64e.toberto4000.workers.dev/verify"
    }
}
