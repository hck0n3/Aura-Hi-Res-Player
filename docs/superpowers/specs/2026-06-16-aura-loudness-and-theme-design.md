# Aura — Loudness/limiter overhaul + Aura theme (design)

Date: 2026-06-16
Branch: feat/jr-dsp-and-android-improvements

## Goal
1. **Audio**: match TIDAL-like perceived loudness ("songs sound loud and full") WITHOUT distortion — fix the "too quiet" feel — and tame residual high-frequency (treble) peaks.
2. **Theme**: add an optional "Aura Hi-Res" dark/audiophile theme (current look stays default; nothing existing breaks).

## Audio — why it sounds quiet today
`NormalizationGainAudioProcessor` is **attenuate-only** (gain in `(0,1]`): loud masters are pulled down, quiet tracks are left as-is and never boosted. Net result sits at/below reference → perceived quiet. TIDAL normalizes *to a target loudness*, boosting quiet content, with a limiter preventing clipping.

## Audio changes

### 1. Loudness: target normalization with makeup (the key fix)
- Split full normalization (`-loudnessDb`) into two stages so nothing hard-clips in 16-bit PCM mid-chain:
  - **Attenuation** (`min(0, -loudnessDb)`, ≤0 dB) stays in `NormalizationGainAudioProcessor` (unchanged).
  - **Makeup boost** (`max(0, -loudnessDb)`, capped at `maxBoostDb = 9 dB`) applied **in float inside the new limiter**, before limiting.
- New pure fn `loudnessMakeupDb(loudnessDb, enabled, maxBoostDb)` in `AudioGain.kt` (unit-tested).
- Quiet tracks now come UP to reference → consistent, full loudness ≈ TIDAL. The true-peak limiter guarantees clean output.

### 2. True-peak limiter (replaces per-sample `softLimit`)
- New `TruePeakLimiterAudioProcessor`, placed last (after `JrDsp`, before `Spectrum`).
- Applies the per-track makeup gain (`@Volatile var makeupGain`), then **2× oversamples** (linear interpolation) and soft-limits in the oversampled domain → catches inter-sample / treble transient peaks (sibilance, cymbals) that a per-sample limiter misses, with less aliasing (cleaner highs).
- Bounded soft-limit: `softLimit(x, ceiling, knee)` — linear below `knee`, `tanh` knee above, asymptote AT `ceiling` (≈ −1 dBFS, 0.95). Output magnitude can never exceed `ceiling`. Transparent below knee. Unit-tested (bounded + passthrough).
- `@Volatile var enabled` (default true); ties to normalization being on.

### 3. Multiband compressor — cleaner
- Remove fixed `MB_MAKEUP ×1.15` (it re-adds gain/peaks inconsistently; loudness is now owned by stage 1).
- Tighten the **high band** (>5 kHz) slightly to tame sustained treble. Keep transparent "glue" character. Loudness no longer comes from MB comp.

### 4. Remove Crossfeed
- Drop `CrossfeedAudioProcessor` from the chain, remove its DataStore collector + settings toggle + key. File may be deleted (unused).

### 5. Remove tube warmth
- Remove `tubeEnabled`/`tubeAmount` from `JrDspAudioProcessor.Config`, the `tube()` application, the settings toggle, and the keys. (Adds color → contrary to the clean-path goal.)

### New chain
`AudioEnhance → NormalizationGain(attenuate) → EQ → JrDsp(effects, no tube, no internal limiter) → TruePeakLimiter(makeup + 2× limit) → Spectrum → silence → SilenceSkipping → Sonic`

## Theme — Aura Hi-Res (optional)
- Centralize `BrandGradient` (currently duplicated in `WelcomeDialog`, `LicenseScreens`) into one place in `ui/theme/`.
- Add "Aura Hi-Res" as a selectable theme/style in Settings. Default stays the current theme. Dark audiophile palette + brand gradient accents on Now Playing + Home. Fully reversible.
- Palette (from approved mockup): `#DE60B3 → #9B6CFF → #3DA9ED`; dark surfaces `#0B0B10 / #16161F`.

## Testing
- Unit: `loudnessMakeupDb` (sign, cap, disabled), `softLimit` (bounded ≤ ceiling, passthrough below knee), oversampling peak reduction.
- Build: `assembleUniversalFossDebug` + `testUniversalFossDebugUnitTest` green.
- Manual: A/B loudness vs current, treble peak check on bright tracks.

## Notes / memory impact
This **supersedes** the earlier "normalization SOLO atenúa, NO añadir ganancia" stance (`echo-audio-hifi-cleanpath`): gain IS now added (bounded makeup), made safe by the true-peak limiter. Update that memory after implementation.

## Out of scope (kept intentionally)
Gumroad slug, keystore signing identity, code identifiers (`JrDspAudioProcessor` etc.), `.jrpl.json`.
