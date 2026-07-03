package com.dpi

import android.content.Context
import timber.log.Timber


class DensityScaler : BaseLifecycleContentProvider() {

    override fun onCreate(): Boolean {
        val context = context ?: return false
        val scaleFactor = getScaleFactorFromPreferences(context)
        DensityConfiguration(scaleFactor).applyDensityScaling(context)
        return true
    }

    companion object {
        private const val PREFS_NAME = "echomusic_settings"
        private const val KEY_DENSITY_SCALE = "density_scale_factor"
        private const val DEFAULT_SCALE_FACTOR = 1.0f
        // TVs report a low DPI, so the phone-tuned UI renders microscopic. Bump the default a bit on TV ONLY
        // when the user hasn't chosen a density themselves (phones/tablets are unaffected).
        private const val TV_DEFAULT_SCALE_FACTOR = 1.10f

        private fun getScaleFactorFromPreferences(context: Context): Float {
            return try {
                val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                if (prefs.contains(KEY_DENSITY_SCALE)) {
                    prefs.getFloat(KEY_DENSITY_SCALE, DEFAULT_SCALE_FACTOR)
                } else if (iad1tya.echo.music.utils.DeviceForm.isTelevision(context)) {
                    TV_DEFAULT_SCALE_FACTOR
                } else {
                    DEFAULT_SCALE_FACTOR
                }
            } catch (e: Exception) {
                Timber.tag("DensityScaler").w(e, "Failed to read scale factor from preferences")
                DEFAULT_SCALE_FACTOR
            }
        }
    }
}
