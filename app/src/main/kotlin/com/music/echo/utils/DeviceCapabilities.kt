package iad1tya.echo.music.utils

import android.app.ActivityManager
import android.content.Context
import android.os.Build

/**
 * Device performance tier derived from CAPABILITY (RAM, CPU cores, the OEM-declared media performance class,
 * the low-RAM flag) — never from brand/model. Used to scale heavy work down on weaker phones (smaller decode
 * buffers, no forced max-bitrate, canvas/visualizer off by default) while leaving capable phones at full.
 */
enum class DeviceTier { LOW, MID, HIGH }

object DeviceCapabilities {

    @Volatile
    private var cached: DeviceTier? = null

    /** Cached after the first call (capabilities don't change at runtime). */
    fun tier(context: Context): DeviceTier {
        cached?.let { return it }
        return compute(context.applicationContext).also { cached = it }
    }

    private fun compute(context: Context): DeviceTier {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val lowRam = am?.isLowRamDevice == true
        val cores = Runtime.getRuntime().availableProcessors().coerceAtLeast(1)
        val totalRamMb = runCatching {
            val mi = ActivityManager.MemoryInfo()
            am?.getMemoryInfo(mi)
            ((mi.totalMem) / (1024L * 1024L)).toInt()
        }.getOrDefault(0) // 0 = unknown → don't penalize on this signal alone
        // Android 12+ vendors may declare a media performance class (0 = none/unknown).
        val perfClass = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Build.VERSION.MEDIA_PERFORMANCE_CLASS
        } else {
            0
        }

        // RAM is the primary signal — but MemoryInfo.totalMem reports PHYSICAL ram minus kernel/reserved
        // carveouts, so a nominal 4 GB phone reports ~3.6–3.9 GB and a 6 GB phone ~5.5–5.8 GB. Thresholds
        // are set against those REPORTED values (not the nominal capacity). Cores are only a fallback when RAM
        // is unknown — availableProcessors() counts ONLINE cores (big cores get parked), so it must never
        // DEMOTE a high-RAM device, or a flagship could lock to MID just because cores were parked at startup.
        return when {
            lowRam -> DeviceTier.LOW
            totalRamMb in 1..4300 -> DeviceTier.LOW           // ~4 GB and below (reported)
            totalRamMb in 1..6800 -> DeviceTier.MID           // ~6 GB phones
            totalRamMb > 6800 -> DeviceTier.HIGH              // 8 GB+ flagships (RAM is enough on its own)
            // RAM unknown (0): fall back to core count + the OEM media-performance class.
            cores <= 4 -> DeviceTier.LOW
            cores <= 6 || perfClass in 1..30 -> DeviceTier.MID
            else -> DeviceTier.HIGH
        }
    }
}
