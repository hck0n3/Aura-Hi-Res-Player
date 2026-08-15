from pathlib import Path

p = Path(r"d:/7-8-26/AURA HI-RES/app/src/main/kotlin/com/music/echo/App.kt")
t = p.read_text(encoding="utf-8")
start = t.find("    private suspend fun migrateAudioDefaultsV2")
end = t.find("    private fun applyInfinitePlaybackOn", start)
assert start != -1 and end != -1, (start, end)

new_fn = r'''    private suspend fun migrateAudioDefaultsV2(settings: androidx.datastore.preferences.core.Preferences) {
        if (settings[iad1tya.echo.music.constants.AudioDefaultsV2AppliedKey] == true) return
        // Playback prefs (best-effort): crossfade aligned with 0.6.127, Safe Volume ON.
        runCatching {
            dataStore.edit { p ->
                p[iad1tya.echo.music.constants.CrossfadeEnabledKey] = true
                // Aligned with the CrossfadeRespiro5 forced default (owner order, 0.6.127): this block
                // runs AFTER batch A on fresh installs, so mismatched values here would silently undo it.
                p[iad1tya.echo.music.constants.CrossfadeDurationKey] = 5f
                p[iad1tya.echo.music.constants.CrossfadeCurveKey] = 4
                p[iad1tya.echo.music.constants.SafeVolumeEnabledKey] = true
            }
        }.onFailure { reportException(it) }

        // EQ: NEVER overwrite an install that already has bands / an active profile. Owner complaint:
        // every update reset his EQ. Stamping Audiophile over tuned prefs is exactly that.
        val eqPrefs = applicationContext.getSharedPreferences("echo_eq_prefs", Context.MODE_PRIVATE)
        val eqRepo = eqProfileRepository.get()
        val alreadyHasEqState = eqPrefs.contains("enabled") ||
            eqPrefs.contains("preampDb") ||
            (0 until 24).any { eqPrefs.contains("band24_$it") } ||
            eqRepo.getAllProfiles().isNotEmpty() ||
            eqRepo.getActiveProfile() != null
        if (alreadyHasEqState) {
            dataStore.edit { it[iad1tya.echo.music.constants.AudioDefaultsV2AppliedKey] = true }
            return
        }

        val seeded = runCatching {
            val gains = iad1tya.echo.music.eq.data.FactoryPreset.AUDIOPHILE.gains
            val bands = iad1tya.echo.music.ui.screens.equalizer.axion.buildEqBands(gains, IntArray(gains.size))
            val profile = iad1tya.echo.music.eq.data.SavedEQProfile(
                id = "echo_tuning",
                name = "JR Tuning",
                deviceModel = "equalizer",
                bands = bands,
                autoBands = emptyList(),
                preamp = 0.0,
                isCustom = false,
                isActive = true,
            )
            // DSP source of truth: MusicService collects combine(activeProfile, unsavedProfile){ unsaved ?: active }.
            eqRepo.saveProfile(profile)
            eqRepo.setUnsavedProfile(profile)
            eqRepo.setActiveProfile(profile.id)
            // EQ-screen UI mirror so the enabled toggle / sliders / preamp reflect the seeded Audiophile tuning.
            val ed = eqPrefs.edit()
            ed.putBoolean("enabled", true)
            ed.putFloat("preampDb", 0.0f)
            gains.forEachIndexed { i, g -> ed.putFloat("band24_$i", g) }
            ed.apply()
        }.onFailure { reportException(it) }.isSuccess
        // Only mark the one-time migration done when the seed actually succeeded, so a transient failure
        // (IO error / disk full / serialization) retries on the next launch instead of being silently
        // marked complete and leaving the EQ partially seeded forever.
        if (seeded) {
            dataStore.edit { it[iad1tya.echo.music.constants.AudioDefaultsV2AppliedKey] = true }
        }
    }

'''

p.write_text(t[:start] + new_fn + t[end:], encoding="utf-8")
print("OK", end - start, "->", len(new_fn))
