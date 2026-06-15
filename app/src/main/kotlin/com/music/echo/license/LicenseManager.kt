package iad1tya.echo.music.license

import android.content.Context
import android.content.SharedPreferences
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

/**
 * Android-facing orchestrator. Persists [LicenseLogic.State] in SharedPreferences and performs the
 * online Gumroad verification on each app open, applying the offline grace window from [LicenseLogic].
 */
object LicenseManager {

    /** Gumroad membership product id for JR-MUSIC-PRO-OFFICIAL ANDROID. Not secret; ships in the APK. */
    const val PRODUCT_ID = "wcPehkIWHRbPKR4_hZLdJQ=="

    private const val PREFS = "jr_license"
    private const val KEY_SUB = "subscription_key"
    private const val KEY_LAST_VERIFIED = "last_verified_at"
    private const val KEY_DEMO_STARTED = "demo_started_at"
    private const val KEY_LAST_SEEN = "last_seen_at"

    private val gumroad = GumroadClient()

    private fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun load(context: Context): LicenseLogic.State {
        val p = prefs(context)
        return LicenseLogic.State(
            subscriptionKey = p.getString(KEY_SUB, null)?.takeIf { it.isNotBlank() },
            lastVerifiedAt = p.getLong(KEY_LAST_VERIFIED, 0L),
            demoStartedAt = p.getLong(KEY_DEMO_STARTED, 0L),
            lastSeenAt = p.getLong(KEY_LAST_SEEN, 0L),
        )
    }

    private fun save(context: Context, state: LicenseLogic.State) {
        prefs(context).edit()
            .putString(KEY_SUB, state.subscriptionKey)
            .putLong(KEY_LAST_VERIFIED, state.lastVerifiedAt)
            .putLong(KEY_DEMO_STARTED, state.demoStartedAt)
            .putLong(KEY_LAST_SEEN, state.lastSeenAt)
            .apply()
    }

    fun isOnline(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    fun demoDaysLeft(context: Context): Int =
        LicenseLogic.demoDaysLeft(load(context), System.currentTimeMillis())

    fun startDemo(context: Context) {
        val now = System.currentTimeMillis()
        save(context, LicenseLogic.startDemo(load(context), now))
    }

    /** Full evaluation used by the gate at startup. Performs online verification when possible. */
    suspend fun evaluate(context: Context): LicenseLogic.AppState {
        val now = System.currentTimeMillis()
        var state = LicenseLogic.touch(load(context), now)
        save(context, state)

        val key = state.subscriptionKey
            ?: return LicenseLogic.resolve(state, LicenseLogic.VerifyOutcome.UNVERIFIED, now)

        val outcome = if (isOnline(context)) {
            when (gumroad.verify(PRODUCT_ID, key)) {
                GumroadVerify.Result.ACTIVE -> {
                    state = LicenseLogic.withVerifiedNow(state, now)
                    save(context, state)
                    LicenseLogic.VerifyOutcome.ACTIVE
                }
                GumroadVerify.Result.ENDED,
                GumroadVerify.Result.INVALID_KEY -> LicenseLogic.VerifyOutcome.ENDED
                GumroadVerify.Result.NETWORK_ERROR -> LicenseLogic.VerifyOutcome.UNVERIFIED
            }
        } else {
            LicenseLogic.VerifyOutcome.UNVERIFIED
        }
        return LicenseLogic.resolve(state, outcome, now)
    }

    /** Called from the "Ya me suscribí" entry screen. Saves the key only when Gumroad confirms active. */
    suspend fun activateSubscription(context: Context, rawKey: String): GumroadVerify.Result {
        val key = rawKey.trim()
        if (key.isEmpty()) return GumroadVerify.Result.INVALID_KEY
        val result = gumroad.verify(PRODUCT_ID, key)
        if (result == GumroadVerify.Result.ACTIVE) {
            val now = System.currentTimeMillis()
            save(context, LicenseLogic.withSubscriptionKey(load(context), key, now))
        }
        return result
    }

    /** Re-checks the stored subscription (used by the renew / "ya pagué" screen). */
    suspend fun reverify(context: Context): GumroadVerify.Result {
        val key = load(context).subscriptionKey ?: return GumroadVerify.Result.INVALID_KEY
        val result = gumroad.verify(PRODUCT_ID, key)
        if (result == GumroadVerify.Result.ACTIVE) {
            val now = System.currentTimeMillis()
            save(context, LicenseLogic.withVerifiedNow(load(context), now))
        }
        return result
    }
}
