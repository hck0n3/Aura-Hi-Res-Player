package iad1tya.echo.music.license

import android.content.Context
import android.content.SharedPreferences

/**
 * Persists [LicenseLogic.State] in private SharedPreferences. The demo key burn is
 * per device install data, the perpetual key never expires.
 */
object LicenseManager {

    private const val PREFS = "jr_license"
    private const val KEY_PERPETUAL = "perpetual"
    private const val KEY_DEMO_USED = "demo_used"
    private const val KEY_DEMO_STARTED_AT = "demo_started_at"
    private const val KEY_LAST_SEEN_AT = "last_seen_at"

    private fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun load(context: Context): LicenseLogic.State {
        val p = prefs(context)
        return LicenseLogic.State(
            perpetual = p.getBoolean(KEY_PERPETUAL, false),
            demoUsed = p.getBoolean(KEY_DEMO_USED, false),
            demoStartedAt = p.getLong(KEY_DEMO_STARTED_AT, 0L),
            lastSeenAt = p.getLong(KEY_LAST_SEEN_AT, 0L),
        )
    }

    private fun save(context: Context, state: LicenseLogic.State) {
        prefs(context).edit()
            .putBoolean(KEY_PERPETUAL, state.perpetual)
            .putBoolean(KEY_DEMO_USED, state.demoUsed)
            .putLong(KEY_DEMO_STARTED_AT, state.demoStartedAt)
            .putLong(KEY_LAST_SEEN_AT, state.lastSeenAt)
            .apply()
    }

    fun isLicensed(context: Context): Boolean {
        val now = System.currentTimeMillis()
        val state = LicenseLogic.touch(load(context), now)
        save(context, state)
        return LicenseLogic.isLicensed(state, now)
    }

    fun isPerpetual(context: Context): Boolean = load(context).perpetual

    /** True when the demo was used on this device and is no longer active. */
    fun isDemoExpired(context: Context): Boolean {
        val state = load(context)
        return state.demoUsed && !state.perpetual &&
            !LicenseLogic.isLicensed(state, System.currentTimeMillis())
    }

    fun demoDaysLeft(context: Context): Int =
        LicenseLogic.demoDaysLeft(load(context), System.currentTimeMillis())

    fun activate(context: Context, key: String): LicenseLogic.Activation {
        val now = System.currentTimeMillis()
        val (state, result) = LicenseLogic.activate(load(context), key, now)
        save(context, state)
        return result
    }
}
