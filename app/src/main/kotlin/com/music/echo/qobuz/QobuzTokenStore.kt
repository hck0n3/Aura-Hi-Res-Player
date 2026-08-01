package iad1tya.echo.music.qobuz

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * Credential vault for the owner's Qobuz link, mirroring [iad1tya.echo.music.migration.TidalTokenStore].
 *
 * The user_auth_token is a real subscription credential, so it lives in EncryptedSharedPreferences (file
 * "qobuz_session"), never in plain DataStore. The WORKING app_id/app_secret pair that signed a valid
 * getFileUrl is stored alongside it, so the playback path never has to re-scrape or re-discover the secret
 * — it just signs with what already worked. Tier metadata (label + hi-res entitlement) is cached only for
 * the settings screen's honest status line.
 *
 * THREADING: every public entry point is `suspend` and hops to [Dispatchers.IO]. Creating the prefs is not
 * a cheap field read — it builds an AndroidKeyStore master key and opens an encrypted file, i.e. real
 * keystore + disk I/O. Doing that from a ViewModel `init` (composition) or from the playback resolver
 * meant that work landed on whatever thread happened to ask first, including Main.
 *
 * FAILURE POLICY: nothing here throws. A vault that cannot be opened reads as "not linked" so playback
 * resolution degrades to the normal path instead of dying; the token is re-obtainable with one login.
 */
class QobuzTokenStore(
    private val context: Context,
) {
    @Volatile private var prefs: SharedPreferences? = null

    // Latched after a failed creation so the playback path does not re-pay a failing MasterKey.build on
    // every single track. User-initiated writes ([save]) retry regardless — see prefsOrNull(retry = true).
    @Volatile private var creationFailed = false

    private val creationLock = Any()

    /**
     * The encrypted prefs, or null when the vault cannot be opened at all. Must be called off Main.
     *
     * @param retry ignores the failure latch (used for explicit, user-initiated writes).
     */
    private fun prefsOrNull(retry: Boolean = false): SharedPreferences? {
        prefs?.let { return it }
        if (creationFailed && !retry) return null
        return synchronized(creationLock) {
            val existing = prefs
            when {
                existing != null -> existing
                creationFailed && !retry -> null
                else -> try {
                    createPrefs().also {
                        prefs = it
                        creationFailed = false
                    }
                } catch (e: Exception) {
                    creationFailed = true
                    Timber.tag(TAG).w(e, "Qobuz vault unavailable — treating account as not linked")
                    null
                }
            }
        }
    }

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
            // Same recovery as TidalTokenStore: a keyset restored onto another device is unreadable (the
            // Keystore key never leaves the original hardware). The token is re-obtainable with one login,
            // so wipe and recreate instead of bricking the Qobuz link forever.
            Timber.tag(TAG).w(e, "Encrypted prefs unreadable, recreating %s", FILE)
            context.deleteSharedPreferences(FILE)
            build()
        }
    }

    suspend fun isLoggedIn(): Boolean = withContext(Dispatchers.IO) {
        !prefsOrNull()?.getString(KEY_TOKEN, null).isNullOrBlank()
    }

    /** The full session used to sign playback requests, or null when not linked / vault unreadable. */
    suspend fun session(): QobuzSession? = withContext(Dispatchers.IO) {
        val prefs = prefsOrNull() ?: return@withContext null
        val token = prefs.getString(KEY_TOKEN, null)?.takeIf { it.isNotBlank() }
            ?: return@withContext null
        val appId = prefs.getString(KEY_APP_ID, null)?.takeIf { it.isNotBlank() }
            ?: return@withContext null
        val appSecret = prefs.getString(KEY_APP_SECRET, null)?.takeIf { it.isNotBlank() }
            ?: return@withContext null
        QobuzSession(
            token = token,
            appId = appId,
            appSecret = appSecret,
            email = prefs.getString(KEY_EMAIL, null),
            tierLabel = prefs.getString(KEY_TIER, null),
            hiresEntitled = prefs.getBoolean(KEY_HIRES, false),
        )
    }

    /** @return true when the session was actually persisted (false = vault unavailable). */
    suspend fun save(session: QobuzSession): Boolean = withContext(Dispatchers.IO) {
        val prefs = prefsOrNull(retry = true) ?: return@withContext false
        prefs.edit().apply {
            putString(KEY_TOKEN, session.token)
            putString(KEY_APP_ID, session.appId)
            putString(KEY_APP_SECRET, session.appSecret)
            session.email?.let { putString(KEY_EMAIL, it) } ?: remove(KEY_EMAIL)
            session.tierLabel?.let { putString(KEY_TIER, it) } ?: remove(KEY_TIER)
            putBoolean(KEY_HIRES, session.hiresEntitled)
        }.apply()
        true
    }

    suspend fun logout() = withContext(Dispatchers.IO) {
        prefsOrNull(retry = true)?.edit()?.clear()?.apply()
        Unit
    }

    private companion object {
        const val TAG = "QobuzTokenStore"
        const val FILE = "qobuz_session"
        const val KEY_TOKEN = "user_auth_token"
        const val KEY_APP_ID = "app_id"
        const val KEY_APP_SECRET = "app_secret"
        const val KEY_EMAIL = "email"
        const val KEY_TIER = "tier"
        const val KEY_HIRES = "hires"
    }
}
