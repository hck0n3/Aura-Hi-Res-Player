package iad1tya.echo.music.utils

import android.app.UiModeManager
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration

/**
 * Coarse device FORM-factor detection (TV / car head unit) — distinct from [DeviceCapabilities] which is about
 * raw power. TVs and car head units are typically low-power AND benefit from the same lean rendering as a LOW
 * device, so High-Performance Mode auto-enables on them. A "CarPlay AI Box" runs full Android and reports as a
 * car (or a normal Android box) — it just runs the app normally, with perf mode on if detected as car/low-end.
 */
object DeviceForm {

    fun isTelevision(context: Context): Boolean {
        val ui = context.getSystemService(Context.UI_MODE_SERVICE) as? UiModeManager
        return ui?.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION ||
            context.packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)
    }

    fun isCar(context: Context): Boolean {
        val ui = context.getSystemService(Context.UI_MODE_SERVICE) as? UiModeManager
        return ui?.currentModeType == Configuration.UI_MODE_TYPE_CAR ||
            context.packageManager.hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE)
    }

    fun isTvOrCar(context: Context): Boolean = isTelevision(context) || isCar(context)
}
