package iad1tya.echo.music.license

import java.security.MessageDigest

/**
 * Pure licensing rules for JR MUSIC PRO. No Android dependencies so the whole
 * state machine is unit-testable. Keys are embedded as SHA-256 hashes only.
 */
object LicenseLogic {

    const val DEMO_KEY_HASH = "345b202209a766873deb1be6fac50978701f1985f6d3b5e0e2e8645fd2fbffbd"
    const val PERPETUAL_KEY_HASH = "fd3fb55e07dc01ee00d298f44511008e7e2e414886f67cc1a64ca1ee93b67a93"

    const val DEMO_DURATION_MS = 3L * 24 * 60 * 60 * 1000

    /** Small grace so timezone hops don't lock a legit demo, but day-scale rollbacks do. */
    private const val CLOCK_ROLLBACK_TOLERANCE_MS = 5L * 60 * 1000

    data class State(
        val perpetual: Boolean = false,
        val demoUsed: Boolean = false,
        val demoStartedAt: Long = 0L,
        val lastSeenAt: Long = 0L,
    )

    sealed class Activation {
        data object Perpetual : Activation()
        data class Demo(val expiresAt: Long) : Activation()
        data object DemoAlreadyUsed : Activation()
        data object Invalid : Activation()
    }

    fun hashKey(raw: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(raw.trim().uppercase().toByteArray(Charsets.UTF_8))
            .joinToString(separator = "") { "%02x".format(it) }

    fun isLicensed(state: State, now: Long): Boolean =
        state.perpetual || isDemoActive(state, now)

    private fun isDemoActive(state: State, now: Long): Boolean =
        state.demoUsed &&
            state.demoStartedAt > 0L &&
            now + CLOCK_ROLLBACK_TOLERANCE_MS >= state.lastSeenAt &&
            now < state.demoStartedAt + DEMO_DURATION_MS

    fun demoDaysLeft(state: State, now: Long): Int {
        if (!isDemoActive(state, now)) return 0
        val remaining = state.demoStartedAt + DEMO_DURATION_MS - now
        val day = 24L * 60 * 60 * 1000
        return ((remaining + day - 1) / day).toInt()
    }

    /** Records the latest time the app was seen running, to detect clock rollbacks. */
    fun touch(state: State, now: Long): State =
        state.copy(lastSeenAt = maxOf(state.lastSeenAt, now))

    fun activate(state: State, key: String, now: Long): Pair<State, Activation> =
        when (hashKey(key)) {
            PERPETUAL_KEY_HASH ->
                state.copy(perpetual = true) to Activation.Perpetual

            DEMO_KEY_HASH ->
                if (state.demoUsed) {
                    state to Activation.DemoAlreadyUsed
                } else {
                    state.copy(
                        demoUsed = true,
                        demoStartedAt = now,
                        lastSeenAt = now,
                    ) to Activation.Demo(now + DEMO_DURATION_MS)
                }

            else -> state to Activation.Invalid
        }
}
