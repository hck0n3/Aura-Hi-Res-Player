package iad1tya.echo.music.license

/**
 * Pure licensing rules for JR MUSIC PRO. No Android dependencies so the whole state machine is
 * unit-testable. The app boots in a keyless 3-day demo; a Gumroad subscription is re-verified online
 * on each open, with a 3-day offline grace window.
 */
object LicenseLogic {

    /** Demo lasts 3 days from the moment the user taps "Probar gratis". */
    const val DEMO_DURATION_MS = 3L * 24 * 60 * 60 * 1000

    /** How long the app keeps working without a fresh successful online verification. */
    const val OFFLINE_GRACE_MS = 1L * 24 * 60 * 60 * 1000

    /** Small tolerance so timezone hops don't lock a legit user; day-scale rollbacks do. */
    private const val CLOCK_ROLLBACK_TOLERANCE_MS = 5L * 60 * 1000

    data class State(
        val subscriptionKey: String? = null,
        val lastVerifiedAt: Long = 0L,
        val demoStartedAt: Long = 0L,
        val lastSeenAt: Long = 0L,
    )

    /** Simplified result of an already-performed online verification. */
    enum class VerifyOutcome { ACTIVE, ENDED, DEVICE_MISMATCH, UNVERIFIED }

    enum class AppState {
        FIRST_RUN, DEMO, DEMO_EXPIRED,
        SUBSCRIPTION_ACTIVE, SUBSCRIPTION_EXPIRED, NEEDS_CONNECTION, DEVICE_BLOCKED,
    }

    fun touch(state: State, now: Long): State =
        state.copy(lastSeenAt = maxOf(state.lastSeenAt, now))

    fun startDemo(state: State, now: Long): State =
        if (state.demoStartedAt > 0L) state
        else state.copy(demoStartedAt = now, lastSeenAt = maxOf(state.lastSeenAt, now))

    fun withVerifiedNow(state: State, now: Long): State =
        state.copy(lastVerifiedAt = now, lastSeenAt = maxOf(state.lastSeenAt, now))

    fun withSubscriptionKey(state: State, key: String, now: Long): State =
        state.copy(
            subscriptionKey = key,
            lastVerifiedAt = now,
            lastSeenAt = maxOf(state.lastSeenAt, now),
        )

    fun isDemoActive(state: State, now: Long): Boolean =
        state.demoStartedAt > 0L &&
            now + CLOCK_ROLLBACK_TOLERANCE_MS >= state.lastSeenAt &&
            now < state.demoStartedAt + DEMO_DURATION_MS

    fun demoDaysLeft(state: State, now: Long): Int {
        if (!isDemoActive(state, now)) return 0
        val remaining = state.demoStartedAt + DEMO_DURATION_MS - now
        val day = 24L * 60 * 60 * 1000
        return ((remaining + day - 1) / day).toInt()
    }

    private fun withinGrace(state: State, now: Long): Boolean =
        state.lastVerifiedAt > 0L &&
            now + CLOCK_ROLLBACK_TOLERANCE_MS >= state.lastSeenAt &&
            now - state.lastVerifiedAt <= OFFLINE_GRACE_MS

    /** Final decision given the (already obtained) verification outcome. */
    fun resolve(state: State, outcome: VerifyOutcome, now: Long): AppState {
        if (state.subscriptionKey != null) {
            return when (outcome) {
                VerifyOutcome.ACTIVE -> AppState.SUBSCRIPTION_ACTIVE
                VerifyOutcome.ENDED -> AppState.SUBSCRIPTION_EXPIRED
                VerifyOutcome.DEVICE_MISMATCH -> AppState.DEVICE_BLOCKED
                VerifyOutcome.UNVERIFIED ->
                    if (withinGrace(state, now)) AppState.SUBSCRIPTION_ACTIVE
                    else AppState.NEEDS_CONNECTION
            }
        }
        return when {
            isDemoActive(state, now) -> AppState.DEMO
            state.demoStartedAt > 0L -> AppState.DEMO_EXPIRED
            else -> AppState.FIRST_RUN
        }
    }
}
