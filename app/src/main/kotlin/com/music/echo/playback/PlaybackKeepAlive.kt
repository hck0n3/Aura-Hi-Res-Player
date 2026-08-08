package iad1tya.echo.music.playback

import android.content.Context
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import androidx.core.content.getSystemService
import timber.log.Timber

/**
 * Extra keep-alive while audio is actually playing.
 *
 * ExoPlayer already uses [androidx.media3.common.C.WAKE_MODE_NETWORK], but several OEM skins
 * (HyperOS, ColorOS, One UI, …) still put Wi‑Fi/CPU to sleep with the screen off — audible as a
 * multi-second silence that resumes the moment the user unlocks. Holding our own
 * [PowerManager.PARTIAL_WAKE_LOCK] + high-perf/low-latency [WifiManager.WifiLock] for the duration
 * of playback is the standard media-app belt-and-suspenders; it does not defeat a hard
 * `ScreenOffCPUCheckKill`, but it stops the soft “radio slept” stalls on most devices.
 *
 * Acquire follows [setPlaying] immediately. Release is **debounced**: Android Auto / Bluetooth
 * focus blips and crossfade player swaps briefly flip `isPlaying=false`; dropping WifiLock in that
 * window is when HyperOS + battery saver cut the stream (see owner share_log POWER_SAVE_ON +
 * keep-alive off within seconds).
 */
class PlaybackKeepAlive(context: Context) {
    private val app = context.applicationContext
    private val tag = "Aura:PlaybackKeepAlive"

    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null
    @Volatile private var held = false
    private val mainHandler = Handler(Looper.getMainLooper())
    private var pendingRelease: Runnable? = null

    @Synchronized
    fun setPlaying(playing: Boolean) {
        if (playing) {
            cancelPendingRelease()
            acquire(reason = "playing")
        } else {
            scheduleRelease()
        }
    }

    /** Re-assert locks after SCREEN_OFF — some OEMs drop WifiLocks when the display blanks. */
    @Synchronized
    fun refreshIfPlaying(playing: Boolean) {
        if (!playing) return
        cancelPendingRelease()
        if (held) {
            // Drop and re-take so a half-held OEM lock cannot leave us “held=true” with nothing.
            releaseInternal()
        }
        acquire(reason = "screen-off refresh")
    }

    @Synchronized
    fun release() {
        cancelPendingRelease()
        releaseNow(reason = "release")
    }

    private fun scheduleRelease() {
        cancelPendingRelease()
        val r = Runnable {
            synchronized(this) {
                pendingRelease = null
                releaseNow(reason = "not playing (debounced)")
            }
        }
        pendingRelease = r
        mainHandler.postDelayed(r, RELEASE_DEBOUNCE_MS)
    }

    private fun cancelPendingRelease() {
        pendingRelease?.let { mainHandler.removeCallbacks(it) }
        pendingRelease = null
    }

    private fun acquire(reason: String) {
        if (held) return
        runCatching {
            val pm = app.getSystemService<PowerManager>() ?: return
            val wl = wakeLock ?: pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, tag).also {
                it.setReferenceCounted(false)
                wakeLock = it
            }
            if (!wl.isHeld) wl.acquire(6 * 60 * 60 * 1000L) // 6h cap — never permanent
        }.onFailure {
            Timber.tag(TAG).w(it, "WakeLock acquire failed ($reason)")
        }
        runCatching {
            val wm = app.getSystemService<WifiManager>() ?: return@runCatching
            @Suppress("DEPRECATION")
            val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                WifiManager.WIFI_MODE_FULL_LOW_LATENCY
            } else {
                WifiManager.WIFI_MODE_FULL_HIGH_PERF
            }
            val lock = wifiLock ?: wm.createWifiLock(mode, tag).also {
                it.setReferenceCounted(false)
                wifiLock = it
            }
            if (!lock.isHeld) lock.acquire()
        }.onFailure {
            Timber.tag(TAG).w(it, "WifiLock acquire failed ($reason)")
        }
        held = wakeLock?.isHeld == true || wifiLock?.isHeld == true
        if (held) {
            Timber.tag(TAG).i("keep-alive on ($reason)")
        }
    }

    private fun releaseNow(reason: String) {
        if (!held && wakeLock?.isHeld != true && wifiLock?.isHeld != true) return
        releaseInternal()
        Timber.tag(TAG).i("keep-alive off ($reason)")
    }

    private fun releaseInternal() {
        runCatching {
            wakeLock?.takeIf { it.isHeld }?.release()
        }.onFailure { Timber.tag(TAG).w(it, "WakeLock release failed") }
        runCatching {
            wifiLock?.takeIf { it.isHeld }?.release()
        }.onFailure { Timber.tag(TAG).w(it, "WifiLock release failed") }
        held = false
    }

    private companion object {
        const val TAG = "PlaybackKeepAlive"
        /** Cover Auto/BT focus blips + crossfade swap gaps without holding locks after a real pause. */
        const val RELEASE_DEBOUNCE_MS = 12_000L
    }
}
