package iad1tya.echo.music.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.content.getSystemService

/**
 * Helpers to stop aggressive OEM skins (MIUI/HyperOS, ColorOS, etc.) from freezing/killing the
 * playback service in the background — which is what breaks "keeps playing" and makes the app appear
 * and disappear in Android Auto. The reliable lever is asking the OS to exempt the app from battery
 * optimization; OEM "autostart" must still be enabled by hand (no public API exists for it).
 */
object BackgroundReliability {

    /**
     * OEM skins known to aggressively freeze/kill backgrounded services (which breaks background playback and
     * makes the app "aparecer y desaparecer" in Android Auto). For these we proactively offer the battery
     * exemption + autostart shortcut once; on stock Android the OS already keeps a media service alive.
     */
    fun isAggressiveOem(): Boolean {
        val m = Build.MANUFACTURER.lowercase()
        val b = Build.BRAND.lowercase()
        return AGGRESSIVE_OEMS.any { m.contains(it) || b.contains(it) }
    }

    private val AGGRESSIVE_OEMS = listOf(
        "xiaomi", "redmi", "poco", "huawei", "honor", "oppo", "realme", "oneplus",
        "vivo", "iqoo", "meizu", "samsung", "asus", "lenovo", "tecno", "infinix", "itel",
        "motorola", "moto", "nokia", "nothing", "tcl", "sharp", "sony", "lge", "lg",
        "zte", "nubia", "blackview", "doogee", "cubot",
    )

    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        val pm = context.getSystemService<PowerManager>() ?: return true
        return pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    /** Opens the system prompt to exempt this app from battery optimization. */
    fun requestIgnoreBatteryOptimizations(context: Context) {
        runCatching {
            @Suppress("BatteryLife")
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }.onFailure {
            // Some OEMs hide the direct prompt — fall back to the battery-optimization list.
            openBatteryOptimizationSettings(context)
        }
    }

    private fun openBatteryOptimizationSettings(context: Context) {
        runCatching {
            context.startActivity(
                Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }.onFailure { openAppDetails(context) }
    }

    /** Opens this app's system settings page (where "Autostart" / battery controls usually live). */
    fun openAppDetails(context: Context) {
        runCatching {
            context.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    .setData(Uri.parse("package:${context.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }
}
