package iad1tya.echo.music.license

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.request.forms.submitForm
import io.ktor.client.statement.bodyAsText
import io.ktor.http.Parameters

/**
 * Thin ktor wrapper around Gumroad's license verification endpoint. Any network/parse failure maps to
 * [GumroadVerify.Result.NETWORK_ERROR] so the caller can fall back to the offline grace window.
 */
class GumroadClient(
    private val http: HttpClient = HttpClient(OkHttp),
) {
    suspend fun verify(productId: String, licenseKey: String): GumroadVerify.Result =
        try {
            val body = http.submitForm(
                url = VERIFY_URL,
                formParameters = Parameters.build {
                    append("product_id", productId)
                    append("license_key", licenseKey)
                    append("increment_uses_count", "false")
                },
            ).bodyAsText()
            GumroadVerify.interpret(body)
        } catch (e: Exception) {
            GumroadVerify.Result.NETWORK_ERROR
        }

    companion object {
        private const val VERIFY_URL = "https://api.gumroad.com/v2/licenses/verify"
    }
}
