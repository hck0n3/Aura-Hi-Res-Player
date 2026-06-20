package iad1tya.echo.music.license

import android.content.Context
import android.content.SharedPreferences
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Android-facing orchestrator. Persists [LicenseLogic.State] in SharedPreferences and verifies the
 * subscription online via the backend Worker (which enforces one device per key) on each app open,
 * applying the offline grace window from [LicenseLogic].
 */
object LicenseManager {

    private const val PREFS = "jr_license"
    private const val KEY_SUB = "subscription_key"
    private const val KEY_LAST_VERIFIED = "last_verified_at"
    private const val KEY_DEMO_STARTED = "demo_started_at"
    private const val KEY_LAST_SEEN = "last_seen_at"
    private const val KEY_LAST_STATE = "last_state"

    /** Last resolved gate state, cached so re-verification can happen in the background without
     *  flashing the "verificando licencia" screen every time the app opens. */
    fun lastResolvedState(context: Context): LicenseLogic.AppState? =
        prefs(context).getString(KEY_LAST_STATE, null)
            ?.let { runCatching { LicenseLogic.AppState.valueOf(it) }.getOrNull() }

    private fun saveResolved(context: Context, s: LicenseLogic.AppState) {
        prefs(context).edit().putString(KEY_LAST_STATE, s.name).apply()
    }

    private val backend = LicenseBackendClient()

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

    /**
     * Starts the demo, registering it on the server keyed to this device (ANDROID_ID). Requires a
     * connection so the device is recorded — this is what stops "clear data → fresh 3-day demo".
     * Returns false when offline / the server is unreachable (caller should ask the user to connect).
     */
    suspend fun startDemo(context: Context): Boolean = withContext(Dispatchers.IO) {
        if (!isOnline(context)) return@withContext false
        val demo = backend.fetchDemo(DeviceId.get(context), start = true) ?: return@withContext false
        if (!demo.hasDemo) return@withContext false
        val now = System.currentTimeMillis()
        val elapsed = (demo.serverTime - demo.startedAt).coerceAtLeast(0L)
        save(context, load(context).copy(demoStartedAt = now - elapsed, lastSeenAt = now))
        true
    }

    private fun outcomeOf(status: LicenseStatus): LicenseLogic.VerifyOutcome =
        when (status) {
            LicenseStatus.ACTIVE -> LicenseLogic.VerifyOutcome.ACTIVE
            LicenseStatus.ENDED, LicenseStatus.INVALID_KEY -> LicenseLogic.VerifyOutcome.ENDED
            LicenseStatus.DEVICE_MISMATCH -> LicenseLogic.VerifyOutcome.DEVICE_MISMATCH
            LicenseStatus.NETWORK_ERROR -> LicenseLogic.VerifyOutcome.UNVERIFIED
        }

    /** Full evaluation used by the gate at startup. Verifies online (Worker) when possible. */
    suspend fun evaluate(context: Context): LicenseLogic.AppState = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        var state = LicenseLogic.touch(load(context), now)
        save(context, state)

        val key = state.subscriptionKey
        if (key == null) {
            // No subscription → demo path. Sync the demo start from the Worker (keyed to this device)
            // so clearing app data can't restart the 3-day demo. When online we trust the server's
            // start time; offline we keep whatever local state we have (offline grace).
            if (isOnline(context)) {
                val demo = backend.fetchDemo(DeviceId.get(context), start = false)
                if (demo != null && demo.hasDemo) {
                    val elapsed = (demo.serverTime - demo.startedAt).coerceAtLeast(0L)
                    val serverStart = now - elapsed
                    // Floor: once a start is recorded, the demo can never be made *newer* (a server
                    // glitch or a re-register can't extend/restart the 3-day demo). Only earlier wins.
                    val start = if (state.demoStartedAt > 0L) minOf(state.demoStartedAt, serverStart) else serverStart
                    state = state.copy(demoStartedAt = start, lastSeenAt = now)
                    save(context, state)
                }
            }
            return@withContext LicenseLogic.resolve(state, LicenseLogic.VerifyOutcome.UNVERIFIED, now)
                .also { saveResolved(context, it) }
        }

        val outcome = if (isOnline(context)) {
            val status = backend.verify(key, DeviceId.get(context))
            if (status == LicenseStatus.ACTIVE) {
                state = LicenseLogic.withVerifiedNow(state, now)
                save(context, state)
            }
            outcomeOf(status)
        } else {
            LicenseLogic.VerifyOutcome.UNVERIFIED
        }
        LicenseLogic.resolve(state, outcome, now).also { saveResolved(context, it) }
    }

    /** Called from the "Ya me suscribí" entry screen. Saves the key only when verification is ACTIVE. */
    suspend fun activateSubscription(context: Context, rawKey: String): LicenseStatus =
        withContext(Dispatchers.IO) {
            val key = rawKey.trim()
            if (key.isEmpty()) return@withContext LicenseStatus.INVALID_KEY
            val status = backend.verify(key, DeviceId.get(context))
            if (status == LicenseStatus.ACTIVE) {
                val now = System.currentTimeMillis()
                save(context, LicenseLogic.withSubscriptionKey(load(context), key, now))
            }
            status
        }

    /** Re-checks the stored subscription (used by the renew / "ya pagué" screen). */
    suspend fun reverify(context: Context): LicenseStatus =
        withContext(Dispatchers.IO) {
            val state = load(context)
            val key = state.subscriptionKey ?: return@withContext LicenseStatus.INVALID_KEY
            val status = backend.verify(key, DeviceId.get(context))
            if (status == LicenseStatus.ACTIVE) {
                save(context, LicenseLogic.withVerifiedNow(state, System.currentTimeMillis()))
            }
            status
        }
}
