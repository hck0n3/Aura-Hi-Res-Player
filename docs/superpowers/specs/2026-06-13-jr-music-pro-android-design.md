# JR Music Pro — Android Improvements & Desktop-Parity DSP

- **Date:** 2026-06-13
- **Branch:** `feat/jr-dsp-and-android-improvements`
- **App:** Echo-Music-5.1.8 (Android, Kotlin/Compose, package namespace `iad1tya.echo.music`, multi-module Gradle)
- **Desktop reference (source of truth):** `C:\Users\Hck0n3\Desktop\2` — Electron app "JR Music Pro" with a native C++ audio engine (`native/src/jr_audio_engine.cpp`, miniaudio) and DSP UI (`src/ui/renderer/dsp_pro.html`).
- **Status:** Approved (user: audio/DSP first, then the rest). Defaults = seed-on-first-run. EQ = full parity, phased.

## Goal

Bring the Android app to feature/behavior parity with the desktop JR Music Pro where it matters, fix two real bugs (Google login sync, no shareable logs), pre-activate a set of defaults, clean up the About screen and remove dead UI, and — the headline item — reimplement the desktop DSP as a real Media3 audio-processing chain.

## Build / verification

- JDK 21.0.8; Android SDK copied to LOCALAPPDATA; `JAVA_HOME` set per-command (see auto-memory `local-build-toolchain`).
- After each workstream: `./gradlew :app:assembleUniversalFossDebug` (or the project's universal/foss task) + `./gradlew :app:lintUniversalFossDebug`. Keep build green.
- Manual smoke test of the touched screen where feasible.

---

## Sequencing (user-directed)

**Audio/DSP (W8) FIRST**, then: W2 (login fix) → W4 (defaults + legacy icon) → W5 (LT home button) → W1 (About) → W3 (haptics) → W6 (logs) → W7 (playlist import) → W9 (error-handling/verify pass, ongoing).

Each workstream is independent and separately committable. Build stays green throughout.

---

## W8 · Equalizer / DSP — desktop parity (PRIORITY, phased)

### Why the previous attempt didn't match the desktop

- Desktop DSP = native C++ (`jr_audio_engine.cpp`) processing f32 PCM in a miniaudio callback — a full custom signal chain.
- Android plays through ExoPlayer/Media3. Real processing here = a custom `AudioProcessor` doing PCM math. The app currently ships **one** real processor (`eq/audio/CustomEqualizerAudioProcessor.kt`, parametric biquad). Bass boost / virtualizer / spatial used Android's built-in `audiofx`, which is limited and has **no** compressor/convolver/waveshaper — so desktop parity was impossible that way.
- Parity **is** achievable: port each C++ block to Kotlin PCM math inside the Media3 chain. All blocks are standard biquad (RBJ) + one-pole IIR + per-sample arithmetic — same class of code as the existing biquad EQ.

### Target: port `jr_audio_engine.cpp` `data_callback` exactly

Signal chain, in order (all f32 internally; stereo unless noted):

1. **Master gain** = `volume * preamp * replayGain` (linear). Preamp dB clamp [-20…+6] (UI) / [-30…+6] (engine); ReplayGain dB clamp [-30…+12]. `gain = 10^(dB/20)`.
2. **HPF 20 Hz** subsonic cleanup, Q 0.707 (on by default).
3. **Parametric EQ — 24 bands**, dispatched per band type:
   - Freqs (ISO ⅓-oct + 440): `20,31,50,80,100,160,200,315,440,500,800,1000,1600,2000,2500,3150,4000,5000,6300,8000,10000,12500,16000,20000`
   - Q = `4.318` (= 1/(2^(1/6)−2^(−1/6))) per band.
   - Gain clamp [-18…+18] dB. Band only applied when `|gain| > 0.01`.
   - Band type: `0`=Peaking bell, `1`=Low shelf, `2`=High shelf.
   - Gated by global `EQ enabled`.
4. **Loudness compensation** (Fletcher-Munson, volume-adaptive): low-shelf 200 Hz `+3..+9 dB`, high-shelf 5 kHz `+2..+5 dB`; extra boost as volume drops below −5 dB. Q 0.707.
5. **HRTF (binaural)** *or* **Crossfeed** (mutually exclusive, stereo):
   - HRTF: ITD delay ≈0.63 ms + head-shadow LP ≈700 Hz + Schroeder room reflections (delays ~12 ms / ~19 ms, LP-damped feedback). Mix: 66% direct + 20% contralateral + 14% room. Spatial profiles 0–3 (Bypass / "Dolby Atmos Headphone" / "DTS Headphone:X" / "DTS:X Ultra") map to HRTF on with these labels.
   - Crossfeed: one-pole LP fc≈1200 Hz on opposite channel, mix `amt` (default 0.35): `out = direct*(1-amt) + lpOpposite*amt`.
6. **Bass enhance** (psychoacoustic): LP 150 Hz → 2nd-harmonic generation `x*|x|*6` → bandpass (LP 180 Hz, HP 60 Hz) → add `* amt` (default 0.28).
7. **Harmonic exciter**: HP extraction (LP fc≈6 kHz, take residual) → asymmetric saturation `hp*0.1 + max(0,hp)*hp*3.5` → add `* amt` (default 0.15 in UI; 0.12 engine default).
8. **Tube warmth**: drive=`1+amt*6`, wet=`min(1, amt*1.5)`, asymmetric tanh with offset `0.15*amt`; `out = dry*x + wet*sat`. amt default 0.25.
9. **Multiband compressor** (3-band, LR2 crossovers 200 Hz & 5 kHz, Q 0.5): per-band envelope (attack 10 ms, release 150 ms), thresholds `{0.10,0.16,0.10}` (linear), ratios `{2.5,1.8,3.0}`, makeup ×1.15; recombine with mid phase-inverted: `out = bass*g0 - mid*g1 + high*g2`.
10. **Stereo width** (M/S): `M=(l+r)/2`, `S=(l-r)/2*width`, width clamp [0…3] (UI 0–2), 1.0 = identity.
11. **Dialogue enhancer**: mono = (l+r)/2 → HP 300 Hz → LP 3 kHz → add to both channels `* amt` (default 0.35).
12. **Soft limiter** (on by default): knee at 0.80, `comp = 0.80 + excess/(1+excess*5)`, sign-preserving. Tape-style, no pumping.
13. **64-band spectrum capture**: peak per bin over up to 1024 frames, decay ×0.85 (instant rise). Drives the visualizer.

Per-track init (on load): recompute all filter coefficients for the actual sample rate/channels (see `LoadTrack` in the cpp for exact coefficient formulas — one-pole alphas use `1 - exp(-2π*fc/fs)`).

### Engine setter API (param ranges) — mirror as Kotlin DSP state

`setVolume(0..1)`, `setPreAmp(dB)`, `setReplayGain(dB)`, `setCrossfeed(bool)`, `setPEQNode(idx, freq, gain, q)`, `setBandType(idx, 0|1|2)`, `setDSPEnabled(bool)`, `setLimiter(bool)`, `setLoudness(bool)`, `setBassEnhance(bool, amt)`, `setHRTF(bool)`, `setSpatialProfile(0..3)`, `setDialogueEnhance(bool, amt)`, `setStereoWidth(bool, width)`, `setHarmonicExciter(bool, amt)`, `setTubeWarmth(bool, amt)`, `setMBComp(bool)`, `getSpectrum()->float[64]`.

### UI to match (`dsp_pro.html` "JR DSP Pro")

- **Left:** Pre-Amp slider (−20…+6 dB); Mastering (Soft Limiter, Crossfeed, EQ Global); Procesado (Loudness; Bass Perceptual + slider; Audio Espacial/Binaural profile dropdown; Stereo Width + slider; Harmonic Exciter + slider; Tube Warmth + slider; MB Compressor; Dialogue Enhancer + slider); Audiophile engine (Oversampling, Dither — see out-of-scope); Signal info.
- **Center:** EQ response curve canvas + 24-band Hz labels + dB scale (+15…−15); toolbar: EQ ON, Flat, Bass+, Treble+, V-Shape, Vocal, Classical, Jazz, FFT; 24 band sliders.
- **Right:** Profiles list; Save profile; Export/Import JSON; AutoEQ `.txt` import; AutoEQ online (optional).

### Android implementation approach

- New module/package `eq` extension: `JrDspAudioProcessor : AudioProcessor` (or a small chain of processors) implementing steps 1–12 in f32; keep/refactor the existing `CustomEqualizerAudioProcessor` biquad code for step 3. Reuse `EqualizerService` to attach to the ExoPlayer chain (`MusicService`).
- DSP state + persistence: extend `eq/data` + `EQProfileRepository`; expose params via the EQ ViewModel (replace/augment `ui/screens/equalizer/axion`).
- Spectrum: feed existing spectrum visualizer setting (`SpectrumVisualizerEnabledKey`).
- Presets (Flat/Bass+/Treble+/V-Shape/Vocal/Classical/Jazz): extract exact band curves from the desktop renderer JS (`src/ui/renderer/js/*` `data-factory` handlers) — **TODO at 8A**.

### Phases (each shippable)

- **8A** — 24-band graphic EQ (exact freqs/Q, ±18 dB, per-band Peak/LowShelf/HighShelf) + Pre-amp + global EQ enable + factory presets + curve/sliders UI. Wire real biquads in Media3. *(Foundation.)*
- **8B** — Mastering/dynamics: Soft Limiter, Loudness (adaptive), 3-band MB Compressor.
- **8C** — Spatial: Crossfeed, HRTF + binaural profiles, Stereo Width.
- **8D** — Color: Bass Enhance, Harmonic Exciter, Tube Warmth, Dialogue Enhancer (amount sliders + exact defaults).
- **8E** — 64-band spectrum analyser + profile save/export/import (`.json`); AutoEQ `.txt` import (optional).

### Out of scope (Android-irrelevant)

WASAPI exclusive mode, oversampling, dither — desktop-output-only, no benefit on Android. Note this in the EQ screen footer.

---

## W2 · Google login auto-close + sync (bug fix)

- **Root cause:** `ui/screens/LoginScreen.kt:99-116` calls `context.startActivity(intent)` + `Runtime.getRuntime().exit(0)` on successful login — hard-kills/relaunches the app. `App.kt:143-175` already reactively observes DataStore and live-updates `YouTube.cookie/visitorData/dataSyncId`, so the restart is unnecessary and *is* the "have to close & reopen" symptom.
- **Fix:** on `YouTube.accountInfo().onSuccess`: keep persisting account keys, then `navController.navigateUp()` (close WebView) + trigger sync via `LocalSyncUtils` so library/playlists pull immediately. Mirror the existing `forgetAccount` reactive pattern. Remove the process kill.

## W4 · Pre-activated defaults + delete Legacy Icon

- **Seed-on-first-run** (new `JrDefaultsAppliedKey`) AND flip code defaults:
  - `MiniPlayerBackgroundStyleKey` → `LIVE_MESH`
  - `UseNewPlayerDesignKey` → `false` (Apple Music inspired ON) and set `PlayerBackgroundStyleKey` → `APPLE_MUSIC`
  - `HidePlayerSliderKey` → `true` (the "Hide volume slider" on AMI player)
  - `AppleMusicLyricsBlurKey` → already `true` ✓
- **Delete Legacy Icon:** remove toggle in `AppearanceSettings.kt:1003-1026`, the `handleIconChange`/`IconUtils` legacy path, manifest `legacy_icon` activity-alias, and the `legacy_icon*` drawables/mipmaps. Keep `EnableLegacyIconKey` only if needed for migration cleanup.

## W5 · Remove Listen Together button on home

- Remove the home top-bar Listen Together entry point (gated by `ListenTogetherInTopBarKey`; branded home top bar added in commit a96bc07). Locate exact composable in the home/top-bar code. The rest of Listen Together (player menu) stays.

## W1 · "Acerca de" rewrite + PC icon

- `ui/screens/settings/AboutScreen.kt`: delete Developer/Support/App link sections. New: `R.drawable.jr_logo` (PC app icon, in repo) on top, "JR Music Pro", version + architecture badges, then a **categorized feature list** built from the app's real features (Playback & audio, DSP equalizer, Lyrics, Library & YouTube sync, Import (YT/Spotify/JR), Listen Together, Canvas, Themes, etc.). No external/social links.

## W3 · Global haptics

- Add `Modifier.hapticClickable` helper (uses `HapticFeedbackConstants` / `LocalHapticFeedback`). Wire shared components: `ui/component/IconButton.kt`, `Material3SettingsItem`, player transport buttons, list-row clicks. Covers "all buttons" via shared components rather than per-call edits. Optional global toggle (default ON).

## W6 · Settings → Logs (copy + share)

- Add a persistent Timber `Tree` writing to a rotating file in `filesDir/logs/` (cap size, keep last N).
- New **Logs** settings screen: shows recent log + last crash report, with **Copy** and **Share** (via existing FileProvider).
- Add **Share** to `ui/screens/CrashActivity` (crash log already built in `utils/CrashHandler.kt`).

## W7 · Playlist import from JR Music Pro PC (`.jrpl.json`)

- **Format (confirmed):** `{ "version":1, "name":"…", "exportedAt":"ISO", "tracks":[ {"title","artist","album","duration","youtubeVideoId"} ] }`. File extension `.jrpl.json` (also accept `.json`).
- **Import:** extend existing `ui/menu/ImportPlaylistDialog.kt` / import path. Create local playlist → per track: match local by title+artist (case-insensitive), else resolve by `youtubeVideoId`, else search by title+artist. Reuse Echo's existing resolve/add-to-library plumbing (Spotify import path is a model).
- **Bonus:** matching **export** to `.jrpl.json` for round-trip.

## W9 · Error-handling + verification pass

- After each workstream: build + lint, wrap new risky paths (file IO, parsing, native/audio) in try/catch with Timber logging, verify no regressions. W6 logs make field issues shareable.

---

## Open TODOs captured during implementation

- 8A: extract exact factory-preset band curves (Bass+/Treble+/V-Shape/Vocal/Classical/Jazz) from desktop renderer JS in `C:\Users\Hck0n3\Desktop\2\src\ui\renderer\js\`.
- W5: pin exact composable rendering the LT button on home.
- W2: confirm `SyncUtils` entry point used at startup/login to reuse for post-login sync.
