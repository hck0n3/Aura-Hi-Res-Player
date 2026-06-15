package iad1tya.echo.music.license

import android.content.Context
import android.provider.Settings
import java.util.UUID

/**
 * Stable per-device identifier for the one-device-per-subscription check. Uses ANDROID_ID (survives
 * reinstalls on the same device); falls back to a persisted random UUID when ANDROID_ID is missing or
 * the known buggy emulator value. Cached in the jr_license prefs.
 */
object DeviceId {

    private const val PREFS = "jr_license"
    private const val KEY = "device_id"
    private const val ANDROID_ID_BUG = "9774d56d682e549c"

    fun get(context: Context): String {
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.getString(KEY, null)?.takeIf { it.isNotBlank() }?.let { return it }

        val androidId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
        val id = if (androidId.isNullOrBlank() || androidId == ANDROID_ID_BUG) {
            UUID.randomUUID().toString()
        } else {
            androidId
        }
        prefs.edit().putString(KEY, id).apply()
        return id
    }
}
