package iad1tya.echo.music.utils

import android.content.Context
import android.os.Build
import android.os.PowerManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * Process-wide DEVICE THERMAL state, used ONLY to throttle the heaviest CONTINUOUS visual effects (spectrum,
 * animated canvas, blurred/animated background, Apple-Music lyrics, artist video) when the OS itself reports
 * the device is heating up.
 *
 * It NEVER touches audio/playback and NEVER throttles a cool device:
 *  - Below API 29 (no thermal API) the poll never runs, so [isHot] is permanently false.
 *  - While the OS reports status < MODERATE, [isHot] is false.
 * In both cases every gated effect stays BYTE-IDENTICAL to today. Only once the OS reports
 * THERMAL_STATUS_MODERATE (or hotter) do the heavy visuals drop to their cheap/off path — exactly like
 * Performance Mode — so a hot device (even a capable one) can cool down.
 */
object ThermalManager {

    // A single process-wide scope for the derived [isHot] flow. Never cancelled (lives for the process).
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // 0 == PowerManager.THERMAL_STATUS_NONE. Stays NONE forever on API < 29 (the poll never runs).
    private val _thermalStatus = MutableStateFlow(0)

    /** Current OS thermal status (a PowerManager.THERMAL_STATUS_* value). Always NONE (0) below API 29. */
    val thermalStatus: StateFlow<Int> = _thermalStatus.asStateFlow()

    /**
     * True once the OS reports MODERATE or hotter. Always false below API 29 and while the device is cool, so a
     * cool/capable device is unaffected. THERMAL_STATUS_MODERATE is a static-final int (== 2) that the compiler
     * inlines, so referencing it here is safe on every API level (no runtime field lookup on API < 29).
     */
    val isHot: StateFlow<Boolean> =
        _thermalStatus
            .map { it >= PowerManager.THERMAL_STATUS_MODERATE }
            .stateIn(scope, SharingStarted.Eagerly, false)

    // Shared low-frequency poll of the OS thermal status. Only touched under the API-29 guard.
    private var pollJob: Job? = null
    private var refCount = 0

    /**
     * Ref-counted start of a shared, low-frequency thermal poll so many composables share ONE loop. We POLL
     * PowerManager.currentThermalStatus every ~10s rather than registering a callback listener — temperature
     * changes slowly, the poll is trivially cheap, and it avoids the nested OnThermalStatusListener SAM type.
     * No-op below API 29 and best-effort (a missing service just leaves the device reported as cool).
     */
    @Synchronized
    fun acquire(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        if (refCount++ > 0) return
        val appContext = context.applicationContext
        pollJob = scope.launch {
            val pm = appContext.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return@launch
            while (isActive) {
                runCatching { _thermalStatus.value = pm.currentThermalStatus }
                delay(10_000L)
            }
        }
    }

    /** Stops the shared poll once the last user goes away. No-op below API 29. */
    @Synchronized
    fun release(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        if (refCount == 0) return
        if (--refCount > 0) return
        pollJob?.cancel()
        pollJob = null
        // Reset to NONE so a subsequent (cool) session doesn't start throttled off a stale hot reading.
        _thermalStatus.value = 0
    }
}

/**
 * Registers the OS thermal listener on first use (via [DisposableEffect]) and unregisters it onDispose,
 * returning whether the device is currently HOT (MODERATE+). Recomposes only when the status crosses the
 * threshold. Cheap and best-effort.
 *
 * Cool/capable path is unchanged: this returns false below API 29 and whenever the OS reports the device is
 * cool, so every `heavyEffect && !rememberDeviceThrottle()` (or `cheapPath || rememberDeviceThrottle()`) gate is
 * byte-identical to today until the OS actually reports MODERATE+ thermal.
 */
@Composable
fun rememberDeviceThrottle(): Boolean {
    val context = LocalContext.current
    DisposableEffect(Unit) {
        ThermalManager.acquire(context)
        onDispose { ThermalManager.release(context) }
    }
    val hot by ThermalManager.isHot.collectAsState()
    return hot
}
