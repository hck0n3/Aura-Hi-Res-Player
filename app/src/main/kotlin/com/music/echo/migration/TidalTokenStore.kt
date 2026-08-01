package iad1tya.echo.music.migration

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.aura.migration.source.tidal.TidalAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import timber.log.Timber
import javax.inject.Provider

/**
 * OAuth token vault + token lifecycle for the Tidal playlist import.
 *
 * Tokens live in EncryptedSharedPreferences (file "tidal_tokens") per the migration module's rule —
 * an access/refresh token pair is a credential, it never goes into plain DataStore. The CLIENT ID is
 * NOT stored here: it is user-supplied configuration and lives in DataStore under TidalClientIdKey
 * (see constants/PreferenceKeys.kt), which is why [auth] is a Provider — every auth round reads the
 * client id the user has configured *now*, not the one at first injection.
 *
 * Flow (PKCE, no client secret — GPL project, an embedded secret would be public):
 *   1. UI calls [beginAuth] -> stores the code verifier, returns the authorize URL to open.
 *   2. Browser redirects to echomusic://tidal-callback -> MainActivity emits on [TidalAuthCallbackBus].
 *   3. UI calls [exchangeCode] with the received code -> tokens stored.
 *   4. TidalSource's tokenProvider calls [accessToken] -> auto-refreshes when expired.
 */
class TidalTokenStore(
    private val context: Context,
    private val httpClient: OkHttpClient,
    private val auth: Provider<TidalAuth>,
) {

    private val prefs: SharedPreferences by lazy { createPrefs() }
    private val refreshMutex = Mutex()

    private fun createPrefs(): SharedPreferences {
        fun build(): SharedPreferences {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            return EncryptedSharedPreferences.create(
                context,
                FILE,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )
        }
        return try {
            build()
        } catch (e: Exception) {
            // Corrupted Tink keyset (classic cause: backup restored onto another device — the Keystore
            // key that wraps the keyset never leaves the original hardware). Tokens are re-obtainable
            // with one more login, so wipe and recreate instead of bricking the Tidal import forever.
            Timber.tag(TAG).w(e, "Encrypted prefs unreadable, recreating %s", FILE)
            context.deleteSharedPreferences(FILE)
            build()
        }
    }

    /** True when we hold any credential worth trying (a refresh token or an unexpired access token). */
    val isAuthenticated: Boolean
        get() = prefs.getString(KEY_REFRESH_TOKEN, null) != null || unexpiredAccessToken() != null

    /** Step 1: build the PKCE authorize request, persist its verifier, hand back the URL to open. */
    fun beginAuth(): String {
        val request = auth.get().buildAuthRequest()
        prefs.edit().putString(KEY_CODE_VERIFIER, request.codeVerifier).apply()
        return request.url
    }

    /** Step 3: redeem the authorization code delivered via echomusic://tidal-callback. */
    suspend fun exchangeCode(code: String): Boolean {
        val verifier = prefs.getString(KEY_CODE_VERIFIER, null) ?: run {
            Timber.tag(TAG).w("exchangeCode without a stored verifier (beginAuth not called?)")
            return false
        }
        val json = postToken(auth.get().tokenExchangeBody(code, verifier)) ?: return false
        prefs.edit().remove(KEY_CODE_VERIFIER).apply()
        return storeTokens(json)
    }

    /**
     * The provider TidalSource consumes. Returns a valid access token, refreshing it when it is within
     * [EXPIRY_MARGIN_MS] of death; null means "not logged in" and surfaces as SourceError.NotAuthenticated.
     */
    suspend fun accessToken(): String? {
        unexpiredAccessToken()?.let { return it }
        val refreshToken = prefs.getString(KEY_REFRESH_TOKEN, null) ?: return null
        return refreshMutex.withLock {
            // Re-check under the lock: a concurrent caller may have already refreshed.
            unexpiredAccessToken()?.let { return@withLock it }
            val json = postToken(auth.get().refreshBody(refreshToken)) ?: return@withLock null
            if (storeTokens(json)) unexpiredAccessToken() else null
        }
    }

    /** The numeric Tidal user id from the token response, if it carried one. Used as a fallback for the
     *  `/userCollections/me` path — some v2 deployments want the id, not the literal `me`. */
    fun userId(): String? = prefs.getString(KEY_USER_ID, null)

    /** Forget everything (the "log out of Tidal" action). */
    fun logout() {
        prefs.edit().clear().apply()
    }

    // ---- internals ----

    private fun unexpiredAccessToken(): String? {
        val token = prefs.getString(KEY_ACCESS_TOKEN, null) ?: return null
        val expiresAt = prefs.getLong(KEY_EXPIRES_AT, 0L)
        return token.takeIf { System.currentTimeMillis() < expiresAt - EXPIRY_MARGIN_MS }
    }

    private fun storeTokens(json: JsonObject): Boolean {
        val access = json.string("access_token") ?: return false
        val expiresIn = json.string("expires_in")?.toLongOrNull() ?: DEFAULT_EXPIRES_IN_S
        prefs.edit().apply {
            putString(KEY_ACCESS_TOKEN, access)
            putLong(KEY_EXPIRES_AT, System.currentTimeMillis() + expiresIn * 1000)
            // A refresh response may omit refresh_token: keep the one we have.
            json.string("refresh_token")?.let { putString(KEY_REFRESH_TOKEN, it) }
            // Capture the numeric user id (Tidal returns it as user_id, sometimes nested in "user").
            (json.string("user_id")
                ?: (json["user"] as? JsonObject)?.string("id")
                ?: (json["user"] as? JsonObject)?.string("userId"))
                ?.let { putString(KEY_USER_ID, it) }
        }.apply()
        return true
    }

    private suspend fun postToken(formBody: String): JsonObject? = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder()
                .url(TidalAuth.TOKEN_ENDPOINT)
                .post(formBody.toRequestBody("application/x-www-form-urlencoded".toMediaType()))
                .build()
            httpClient.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    Timber.tag(TAG).w("Token endpoint HTTP %d", response.code)
                    return@use null
                }
                Json.parseToJsonElement(body).jsonObject
            }
        }.onFailure { Timber.tag(TAG).w(it, "Token request failed") }.getOrNull()
    }

    private fun JsonObject.string(key: String): String? =
        (this[key] as? JsonPrimitive)?.content?.takeIf { it.isNotBlank() }

    private companion object {
        const val TAG = "TidalTokenStore"
        const val FILE = "tidal_tokens"
        const val KEY_ACCESS_TOKEN = "access_token"
        const val KEY_REFRESH_TOKEN = "refresh_token"
        const val KEY_EXPIRES_AT = "expires_at"
        const val KEY_USER_ID = "user_id"
        const val KEY_CODE_VERIFIER = "code_verifier"
        const val EXPIRY_MARGIN_MS = 60_000L
        const val DEFAULT_EXPIRES_IN_S = 3_600L
    }
}
