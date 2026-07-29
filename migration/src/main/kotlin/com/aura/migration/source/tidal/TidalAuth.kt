package com.aura.migration.source.tidal

import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * OAuth 2.0 con PKCE para la Open API de Tidal (v2).
 *
 * SIN CLIENT SECRET. El proyecto es GPL: cualquier secret incrustado es un
 * secret publico. PKCE existe justo para este caso.
 *
 * !! CONFIRMA los tres hosts en developer.tidal.com antes de compilar.
 *    Cambiaron durante la beta y son lo unico que no puedo fijar por ti.
 */
class TidalAuth(
    private val clientId: String,
    private val redirectUri: String   // App Link, p.ej. "https://tuapp.com/callback/tidal"
) {

    companion object {
        // TODO CONFIRMAR en developer.tidal.com/documentation
        const val AUTHORIZE_ENDPOINT = "https://login.tidal.com/authorize"
        const val TOKEN_ENDPOINT     = "https://auth.tidal.com/v1/oauth2/token"

        /** Minimo imprescindible para importar. NO pidas write si no escribes. */
        const val SCOPES = "user.read collection.read playlists.read"
    }

    data class AuthRequest(val url: String, val codeVerifier: String)

    fun buildAuthRequest(): AuthRequest {
        val verifier = randomUrlSafe(64)
        val challenge = base64Url(sha256(verifier.toByteArray()))
        val url = buildString {
            append(AUTHORIZE_ENDPOINT)
            append("?response_type=code")
            append("&client_id=").append(enc(clientId))
            append("&redirect_uri=").append(enc(redirectUri))
            append("&scope=").append(enc(SCOPES))
            append("&code_challenge_method=S256")
            append("&code_challenge=").append(challenge)
        }
        return AuthRequest(url, verifier)
    }

    /** Cuerpo x-www-form-urlencoded para canjear el code por tokens. */
    fun tokenExchangeBody(code: String, codeVerifier: String): String =
        listOf(
            "grant_type" to "authorization_code",
            "code" to code,
            "client_id" to clientId,
            "redirect_uri" to redirectUri,
            "code_verifier" to codeVerifier
        ).joinToString("&") { "${it.first}=${enc(it.second)}" }

    fun refreshBody(refreshToken: String): String =
        listOf(
            "grant_type" to "refresh_token",
            "refresh_token" to refreshToken,
            "client_id" to clientId
        ).joinToString("&") { "${it.first}=${enc(it.second)}" }

    // ---- utilidades PKCE ----

    private fun randomUrlSafe(bytes: Int): String {
        val b = ByteArray(bytes).also { SecureRandom().nextBytes(it) }
        return base64Url(b)
    }

    private fun sha256(b: ByteArray): ByteArray =
        MessageDigest.getInstance("SHA-256").digest(b)

    private fun base64Url(b: ByteArray): String =
        Base64.encodeToString(b, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)

    private fun enc(s: String): String =
        java.net.URLEncoder.encode(s, "UTF-8")
}
