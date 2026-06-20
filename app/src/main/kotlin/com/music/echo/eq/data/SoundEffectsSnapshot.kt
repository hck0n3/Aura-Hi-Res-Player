package iad1tya.echo.music.eq.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import iad1tya.echo.music.constants.AudioEnhanceEnabledKey
import iad1tya.echo.music.constants.AuraSignatureToneEnabledKey
import iad1tya.echo.music.constants.JrBassEnhanceAmountKey
import iad1tya.echo.music.constants.JrBassEnhanceEnabledKey
import iad1tya.echo.music.constants.JrDialogueAmountKey
import iad1tya.echo.music.constants.JrDialogueEnabledKey
import iad1tya.echo.music.constants.JrExciterAmountKey
import iad1tya.echo.music.constants.JrExciterEnabledKey
import iad1tya.echo.music.constants.JrHrtfEnabledKey
import iad1tya.echo.music.constants.JrLoudnessEnabledKey
import iad1tya.echo.music.constants.JrMbCompEnabledKey
import iad1tya.echo.music.constants.JrStereoWidthEnabledKey
import iad1tya.echo.music.constants.JrStereoWidthKey
import iad1tya.echo.music.utils.dataStore
import kotlinx.coroutines.flow.first

/**
 * Snapshot of the global sound-effect toggles + levels (Aura signature, bass, exciter, multiband comp,
 * stereo width, dialogue, HRTF, loudness, low-quality enhance). Lets an EQ profile carry the effects so
 * activating it restores them, and so they're included when a profile is exported. Booleans are 1f/0f.
 */
object SoundEffectsSnapshot {

    /** Reads the current effect settings into a portable map (matches the JrDsp defaults). */
    suspend fun capture(context: Context): Map<String, Float> {
        val p = context.dataStore.data.first()
        fun bool(k: androidx.datastore.preferences.core.Preferences.Key<Boolean>, def: Boolean) =
            if (p[k] ?: def) 1f else 0f
        fun flt(k: androidx.datastore.preferences.core.Preferences.Key<Float>, def: Float) = p[k] ?: def
        return mapOf(
            "audioEnhance" to bool(AudioEnhanceEnabledKey, false),
            "auraSignature" to bool(AuraSignatureToneEnabledKey, true),
            "loudness" to bool(JrLoudnessEnabledKey, false),
            "hrtf" to bool(JrHrtfEnabledKey, false),
            "bassEnabled" to bool(JrBassEnhanceEnabledKey, false),
            "bassAmount" to flt(JrBassEnhanceAmountKey, 0.28f),
            "exciterEnabled" to bool(JrExciterEnabledKey, false),
            "exciterAmount" to flt(JrExciterAmountKey, 0.15f),
            "mbComp" to bool(JrMbCompEnabledKey, false),
            "stereoEnabled" to bool(JrStereoWidthEnabledKey, false),
            "stereoWidth" to flt(JrStereoWidthKey, 1.0f),
            "dialogueEnabled" to bool(JrDialogueEnabledKey, false),
            "dialogueAmount" to flt(JrDialogueAmountKey, 0.35f),
        )
    }

    /** Writes a previously-captured map back to the live settings (the DSP collectors apply it). */
    suspend fun apply(context: Context, e: Map<String, Float>) {
        if (e.isEmpty()) return
        context.dataStore.edit { p ->
            fun b(name: String, k: androidx.datastore.preferences.core.Preferences.Key<Boolean>) {
                e[name]?.let { p[k] = it > 0.5f }
            }
            fun f(name: String, k: androidx.datastore.preferences.core.Preferences.Key<Float>) {
                e[name]?.let { p[k] = it }
            }
            b("audioEnhance", AudioEnhanceEnabledKey)
            b("auraSignature", AuraSignatureToneEnabledKey)
            b("loudness", JrLoudnessEnabledKey)
            b("hrtf", JrHrtfEnabledKey)
            b("bassEnabled", JrBassEnhanceEnabledKey)
            f("bassAmount", JrBassEnhanceAmountKey)
            b("exciterEnabled", JrExciterEnabledKey)
            f("exciterAmount", JrExciterAmountKey)
            b("mbComp", JrMbCompEnabledKey)
            b("stereoEnabled", JrStereoWidthEnabledKey)
            f("stereoWidth", JrStereoWidthKey)
            b("dialogueEnabled", JrDialogueEnabledKey)
            f("dialogueAmount", JrDialogueAmountKey)
        }
    }
}
