package iad1tya.echo.music.license

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.content.TextContent
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put

/**
 * Talks to the Aura Hi-Res Player license Worker, which verifies the Gumroad subscription and enforces one
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

    /**
     * Server-authoritative demo. With [start] = false this only checks whether a demo is already on
     * record for [deviceId]; with [start] = true it registers one if absent. The demo start is keyed
     * to the device (ANDROID_ID) on the server, so clearing app data can't reset the 3-day demo.
     * Returns null on any network/parse error (caller falls back to local/offline handling).
     */
    suspend fun fetchDemo(deviceId: String, start: Boolean): DemoResult? =
        try {
            val payload = buildJsonObject {
                put("device_id", deviceId)
                put("start", start)
            }.toString()
            val text = http.post(DEMO_URL) {
                setBody(TextContent(payload, ContentType.Application.Json))
            }.bodyAsText()
            val obj = Json.parseToJsonElement(text).jsonObject
            when (obj["status"]?.jsonPrimitive?.contentOrNull) {
                "ok" -> {
                    val started = obj["demo_started_at"]?.jsonPrimitive?.longOrNull
                    val serverTime = obj["server_time"]?.jsonPrimitive?.longOrNull
                    if (started != null && serverTime != null) {
                        DemoResult(hasDemo = true, startedAt = started, serverTime = serverTime)
                    } else null
                }
                "none" -> {
                    val serverTime = obj["server_time"]?.jsonPrimitive?.longOrNull ?: System.currentTimeMillis()
                    DemoResult(hasDemo = false, startedAt = 0L, serverTime = serverTime)
                }
                else -> null // "error"/unknown -> treat as unreachable
            }
        } catch (e: Exception) {
            null
        }

    companion object {
        private const val VERIFY_URL = "https://round-math-d64e.toberto4000.workers.dev/verify"
        private const val DEMO_URL = "https://round-math-d64e.toberto4000.workers.dev/demo"
    }
}

/** Result of a server demo check: whether a demo exists and its server-side start time. */
data class DemoResult(val hasDemo: Boolean, val startedAt: Long, val serverTime: Long)
