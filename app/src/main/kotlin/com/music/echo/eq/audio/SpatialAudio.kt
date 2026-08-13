package iad1tya.echo.music.eq.audio

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import androidx.core.content.getSystemService
import iad1tya.echo.music.R

/**
 * Superpowered binaural / stereo-width profiles for Aura's spatial stage.
 *
 * These are NOT licensed Apple Spatial Audio, Sony 360RA, Dolby Atmos or DTS:X renderers.
 * Each profile is a distinct Superpowered Spatializer + Reverb (or Bauer crossfeed / M-S width)
 * configuration, chosen from published psychoacoustic traits of those products:
 *
 *  - Apple Music Spatial on headphones: front-anchored stage, modest width, a little height, dry.
 *  - Sony 360 Reality Audio: spherical field, sources beside/behind/above, more room.
 *  - Dolby Atmos Music binaural (generic): wider + phantom surrounds + more hall than Apple.
 *  - DTS Headphone:X: more aggressive rear energy than Dolby, slightly less "air".
 *  - Aura: conservative studio triangle — the least colour.
 *  - Crossfeed: Bauer-style speaker simulation, no HRTF.
 *
 * Native layout of [toNativeParams] MUST stay in lockstep with `setSpatial` in SuperpoweredBridge.cpp.
 */
enum class SpatialOutputKind {
    HEADPHONE,
    SPEAKER,
    BYPASS,
}

enum class SpatialAudioProfile(
    val titleRes: Int,
    val summaryRes: Int,
    /** 0 = Superpowered Spatializer (HRTF), 1 = Bauer crossfeed. Speaker routing remaps in [nativeAlgorithm]. */
    val algorithm: Int,
    val azL: Float,
    val azR: Float,
    val elL: Float,
    val elR: Float,
    val rearAzL: Float,
    val rearAzR: Float,
    val rearEl: Float,
    val phantomGain: Float,
    val phantomDelayMs: Float,
    val reverbMix: Float,
    val reverbWidth: Float,
    val reverbDamp: Float,
    val reverbRoomSize: Float,
    val reverbPredelayMs: Float,
    val reverbLowCutHz: Float,
    val sound2: Boolean,
    val inputVolume: Float,
    val crossfeedAmt: Float,
    val speakerWidth: Float,
) {
    AURA(
        titleRes = R.string.spatial_profile_aura,
        summaryRes = R.string.spatial_profile_aura_summary,
        algorithm = 0,
        azL = 328f, azR = 32f, elL = 8f, elR = 8f,
        rearAzL = 250f, rearAzR = 110f, rearEl = 4f,
        phantomGain = 0f, phantomDelayMs = 12f,
        reverbMix = 0.05f, reverbWidth = 0.78f, reverbDamp = 0.58f,
        reverbRoomSize = 0.42f, reverbPredelayMs = 6f, reverbLowCutHz = 140f,
        sound2 = false, inputVolume = 0.94f,
        crossfeedAmt = 0.30f, speakerWidth = 1.16f,
    ),
    APPLE_FRONT(
        titleRes = R.string.spatial_profile_apple,
        summaryRes = R.string.spatial_profile_apple_summary,
        algorithm = 0,
        azL = 318f, azR = 42f, elL = 18f, elR = 18f,
        rearAzL = 255f, rearAzR = 105f, rearEl = 8f,
        phantomGain = 0.06f, phantomDelayMs = 11f,
        reverbMix = 0.08f, reverbWidth = 0.70f, reverbDamp = 0.62f,
        reverbRoomSize = 0.40f, reverbPredelayMs = 8f, reverbLowCutHz = 120f,
        sound2 = false, inputVolume = 0.90f,
        crossfeedAmt = 0.28f, speakerWidth = 1.22f,
    ),
    SONY_SPHERE(
        titleRes = R.string.spatial_profile_sony,
        summaryRes = R.string.spatial_profile_sony_summary,
        algorithm = 0,
        azL = 282f, azR = 78f, elL = 32f, elR = 32f,
        rearAzL = 240f, rearAzR = 120f, rearEl = 22f,
        phantomGain = 0.18f, phantomDelayMs = 14f,
        reverbMix = 0.16f, reverbWidth = 0.92f, reverbDamp = 0.45f,
        reverbRoomSize = 0.72f, reverbPredelayMs = 18f, reverbLowCutHz = 80f,
        sound2 = true, inputVolume = 0.86f,
        crossfeedAmt = 0.34f, speakerWidth = 1.34f,
    ),
    CINEMA(
        titleRes = R.string.spatial_profile_cinema,
        summaryRes = R.string.spatial_profile_cinema_summary,
        algorithm = 0,
        azL = 305f, azR = 55f, elL = 22f, elR = 22f,
        rearAzL = 248f, rearAzR = 112f, rearEl = 12f,
        phantomGain = 0.26f, phantomDelayMs = 18f,
        reverbMix = 0.20f, reverbWidth = 0.95f, reverbDamp = 0.40f,
        reverbRoomSize = 0.78f, reverbPredelayMs = 22f, reverbLowCutHz = 90f,
        sound2 = false, inputVolume = 0.84f,
        crossfeedAmt = 0.32f, speakerWidth = 1.40f,
    ),
    WIDE_SURROUND(
        titleRes = R.string.spatial_profile_wide,
        summaryRes = R.string.spatial_profile_wide_summary,
        algorithm = 0,
        azL = 295f, azR = 65f, elL = 14f, elR = 14f,
        rearAzL = 235f, rearAzR = 125f, rearEl = 6f,
        phantomGain = 0.32f, phantomDelayMs = 16f,
        reverbMix = 0.14f, reverbWidth = 0.88f, reverbDamp = 0.48f,
        reverbRoomSize = 0.62f, reverbPredelayMs = 14f, reverbLowCutHz = 100f,
        sound2 = true, inputVolume = 0.85f,
        crossfeedAmt = 0.36f, speakerWidth = 1.38f,
    ),
    CROSSFEED(
        titleRes = R.string.spatial_profile_crossfeed,
        summaryRes = R.string.spatial_profile_crossfeed_summary,
        algorithm = 1,
        azL = 0f, azR = 0f, elL = 0f, elR = 0f,
        rearAzL = 0f, rearAzR = 0f, rearEl = 0f,
        phantomGain = 0f, phantomDelayMs = 0f,
        reverbMix = 0f, reverbWidth = 1f, reverbDamp = 0.5f,
        reverbRoomSize = 0.5f, reverbPredelayMs = 0f, reverbLowCutHz = 0f,
        sound2 = false, inputVolume = 1f,
        crossfeedAmt = 0.32f, speakerWidth = 1.0f,
    ),
    ;

    fun toNativeParams(): FloatArray = floatArrayOf(
        azL, azR, elL, elR,
        rearAzL, rearAzR, rearEl, phantomGain, phantomDelayMs,
        reverbMix, reverbWidth, reverbDamp, reverbRoomSize, reverbPredelayMs, reverbLowCutHz,
        if (sound2) 1f else 0f, inputVolume, crossfeedAmt, speakerWidth,
    )

    companion object {
        const val ALGO_SPATIALIZER = 0
        const val ALGO_CROSSFEED = 1
        const val ALGO_SPEAKER_MS = 2

        const val NATIVE_PARAM_COUNT = 19

        fun fromName(raw: String?): SpatialAudioProfile =
            entries.firstOrNull { it.name == raw } ?: APPLE_FRONT

        fun nativeAlgorithm(profile: SpatialAudioProfile, kind: SpatialOutputKind): Int = when (kind) {
            SpatialOutputKind.BYPASS -> ALGO_SPATIALIZER
            SpatialOutputKind.SPEAKER -> ALGO_SPEAKER_MS
            SpatialOutputKind.HEADPHONE -> profile.algorithm
        }

        fun detectOutputKind(context: Context): SpatialOutputKind {
            val am = context.getSystemService<AudioManager>() ?: return SpatialOutputKind.SPEAKER
            val devices = runCatching {
                am.getDevices(AudioManager.GET_DEVICES_OUTPUTS).toList()
            }.getOrDefault(emptyList())
            if (devices.isEmpty()) return SpatialOutputKind.SPEAKER

            fun has(type: Int) = devices.any { it.type == type }
            fun hasAny(vararg types: Int) = types.any { has(it) }

            if (hasAny(
                    AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
                    AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
                    AudioDeviceInfo.TYPE_BLE_HEADSET,
                )
            ) {
                return SpatialOutputKind.HEADPHONE
            }
            if (hasAny(
                    AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
                    AudioDeviceInfo.TYPE_WIRED_HEADSET,
                    AudioDeviceInfo.TYPE_USB_HEADSET,
                    AudioDeviceInfo.TYPE_USB_DEVICE,
                )
            ) {
                return SpatialOutputKind.HEADPHONE
            }
            if (hasAny(
                    AudioDeviceInfo.TYPE_HDMI,
                    AudioDeviceInfo.TYPE_HDMI_ARC,
                    AudioDeviceInfo.TYPE_AUX_LINE,
                    AudioDeviceInfo.TYPE_DOCK,
                )
            ) {
                return SpatialOutputKind.BYPASS
            }
            if (Build.VERSION.SDK_INT >= 29 && has(AudioDeviceInfo.TYPE_HDMI_EARC)) {
                return SpatialOutputKind.BYPASS
            }
            if (Build.VERSION.SDK_INT >= 31 && has(AudioDeviceInfo.TYPE_BLE_SPEAKER)) {
                return SpatialOutputKind.SPEAKER
            }
            return SpatialOutputKind.SPEAKER
        }
    }
}
