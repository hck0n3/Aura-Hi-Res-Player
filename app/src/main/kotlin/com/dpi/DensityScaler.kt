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

        // IMPORTANT: do NOT scale density on TV here. TV density is already handled (once, via
        // createConfigurationContext) in Utils.localeAwareContext. A non-1.0 factor here goes through
        // DensityConfiguration.updateConfiguration() on the shared Resources, which — because the manifest
        // doesn't list density/uiMode in configChanges — recreates the Activity, re-applies, and loops
        // (the flicker/relaunch storm on TV). Keep the phone-proven path: honor a user override, else 1.0.
        private fun getScaleFactorFromPreferences(context: Context): Float {
            return try {
                val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                prefs.getFloat(KEY_DENSITY_SCALE, DEFAULT_SCALE_FACTOR)
            } catch (e: Exception) {
                Timber.tag("DensityScaler").w(e, "Failed to read scale factor from preferences")
                DEFAULT_SCALE_FACTOR
            }
        }
    }
}
