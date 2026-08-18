

package iad1tya.echo.music.utils

import android.os.SystemClock
import androidx.datastore.preferences.core.edit
import com.music.innertube.YouTube
import iad1tya.echo.music.constants.VisitorDataKey
import iad1tya.echo.music.utils.cipher.CipherDeobfuscator
import iad1tya.echo.music.utils.PlaybackLogManager
import iad1tya.echo.music.utils.PlaybackLogLevel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong


object BotDetectionMitigator {
    private const val TAG = "BotDetectionMitigator"

    private val failureCount = AtomicInteger(0)

    // Every fresh guest identity re-mints a BotGuard poToken from scratch (jnn-pa Create+GenerateIT +
    // a WebView cold start). With no cooldown, a run of consecutive unplayable songs — e.g. a whole
    // queue affected by the same server-side block — rotated on EVERY song: 15+ rotations inside 90s
    // in the owner's diagnostic. Minting that many fresh attestation sessions back-to-back from the
    // same device/IP is itself a classic automation signature to a fraud-detection system, so the
    // "fix" could plausibly be making the account look MORE bot-like, not less. The retry after a
    // skipped rotation still happens (call sites always re-resolve) — this only stops re-rolling the
    // identity when the last one is still fresh, which costs nothing on the (common) case where the
    // block is server-side and no amount of rotating would have helped anyway.
    private val lastRotationAtMs = AtomicLong(Long.MIN_VALUE / 2)
    private const val ROTATION_COOLDOWN_MS = 45_000L

    
    
    private val GEO_ERROR_SIGNATURES = listOf(
        "not available in your country",
        "not available in your region",
        "not available in this country",
        "not available in this region",
        "geo-restricted",
        "GEO_RESTRICTED",
        "NOT_AVAILABLE_IN_THIS_COUNTRY",
        "only available in certain countries",
        "country restriction",
        "region restriction",
    )

    
    private val BOT_ERROR_SIGNATURES = listOf(
        "Sign in to confirm",
        "confirm you're not a bot",
        "automated queries",
        "Error 2000",
        "403",
        "This content isn't available on this device",
    )

    
    fun notifyPlaybackFailure(isLoggedIn: Boolean, errorMessage: String? = null): Boolean {
        if (isLoggedIn) return false
        if (isGeoError(errorMessage)) return false

        failureCount.incrementAndGet()
        return true
    }

    
    fun notifyPlaybackSuccess() {
        failureCount.set(0)
    }

    
    suspend fun rotateGuestSession() {
        val now = SystemClock.elapsedRealtime()
        val sinceLastMs = now - lastRotationAtMs.get()
        if (sinceLastMs < ROTATION_COOLDOWN_MS) {
            Timber.tag(TAG).d("Skipping guest session rotation — last one was ${sinceLastMs}ms ago (cooldown ${ROTATION_COOLDOWN_MS}ms)")
            // Still clear failureCount: the caller is about to retry with the CURRENT (already recent)
            // identity, which is the intended behavior — only the identity churn is being throttled.
            failureCount.set(0)
            return
        }
        lastRotationAtMs.set(now)

        Timber.tag(TAG).i("Rotating guest session to bypass bot detection...")
        PlaybackLogManager.log(
            PlaybackLogLevel.BOT,
            "Rotating guest session",
            "Bypassing bot detection by refreshing visitorData (locale preserved)"
        )

        withContext(Dispatchers.IO) {
            
            val currentLocale = YouTube.locale

            
            YouTube.visitorData = null
            
            YouTube.refreshVisitorData().onSuccess { newData ->
                Timber.tag(TAG).i("New visitorData obtained successfully for region ${currentLocale.gl}.")
                
                
                CipherDeobfuscator.appContext?.dataStore?.edit { settings ->
                    settings[VisitorDataKey] = newData
                }
            }.onFailure { e ->
                Timber.tag(TAG).e(e, "Failed to refresh visitorData during rotation")
                
                YouTube.locale = currentLocale
            }
        }
        
        failureCount.set(0)
    }

    
    fun isGeoError(message: String?): Boolean {
        if (message == null) return false
        val lower = message.lowercase()
        return GEO_ERROR_SIGNATURES.any { lower.contains(it.lowercase()) }
    }

    
    fun isBotDetectionError(message: String?): Boolean {
        if (message == null) return false
        val lower = message.lowercase()
        return BOT_ERROR_SIGNATURES.any { lower.contains(it.lowercase()) }
    }

    fun reset() {
        failureCount.set(0)
    }
}
