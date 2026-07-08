package iad1tya.echo.music.utils

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.datastore.preferences.core.Preferences
import iad1tya.echo.music.constants.HighPerformanceModeKey

/**
 * Performance Mode — a single master switch that makes any device run as light as possible. When ON it treats the
 * device as [DeviceTier.ULTRA] (the most-stripped tier, below LOW) for every visual/decode/memory gate, regardless
 * of the real hardware tier, and disables the heaviest features (canvas/visualizer/artist-video, capped video
 * decode, no next-song preload, video mode off, smaller buffers + image cache, fewer/smaller Home carousels).
 * AUDIO fidelity is deliberately NOT gated here — EQ, Safe Volume, normalization and the limiter keep working.
 * The flag is auto-enabled on first launch on LOW-tier / TV / car devices and is user-toggleable.
 */
object PerformanceMode {

    /** Blocking read of the master flag (default OFF). Cheap: DataStore in-memory after first read. */
    fun isOn(context: Context): Boolean =
        context.applicationContext.dataStore.get(HighPerformanceModeKey, false)

    /**
     * The tier the app should BEHAVE as: [DeviceTier.ULTRA] (the most-stripped path, below LOW) when Performance
     * Mode is on, otherwise the real [DeviceCapabilities.tier]. Decode/render gates that already `when(deviceTier)`
     * just swap their source to this; ULTRA gets the lowest/most-restricted branch everywhere.
     */
    fun effectiveTier(context: Context): DeviceTier =
        if (isOn(context)) DeviceTier.ULTRA else DeviceCapabilities.tier(context)
}

/**
 * Compose helper for the visual on/off gates: returns the user's stored preference AND-ed with "perf mode off",
 * so a heavy visual is hidden while High-Performance Mode is on WITHOUT mutating the user's stored choice (fully
 * reversible — turning perf mode off restores their toggles exactly).
 */
@Composable
fun rememberPerfGatedBoolean(key: Preferences.Key<Boolean>, defaultValue: Boolean): State<Boolean> {
    val raw by rememberPreference(key, defaultValue)
    val perfOn by rememberPreference(HighPerformanceModeKey, false)
    return remember(raw, perfOn) { mutableStateOf(raw && !perfOn) }
}
