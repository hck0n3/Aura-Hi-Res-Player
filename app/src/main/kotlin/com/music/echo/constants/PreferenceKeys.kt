

package iad1tya.echo.music.constants

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import java.time.LocalDateTime
import java.time.ZoneOffset

import com.music.innertube.models.IpVersion

// Data Saver mode (default OFF): one switch that forces Opus audio quality and gates every
// background data consumer (preload, auto lyrics, video items, speculative video prefetch,
// canvas animations, scrobbling). It NEVER overrides the user's chosen quality upward — the
// persisted AudioQualityKey stays untouched and comes back the moment the switch goes OFF.
val DataSaverEnabledKey = booleanPreferencesKey("dataSaverEnabled")
// First-run artist onboarding (pick >=3 favourite artists) completed.
val OnboardingArtistsDoneKey = booleanPreferencesKey("onboardingArtistsDone")
// Last release tag the weekly update-check worker already notified about (notify once per version).
val LastUpdateNotifiedTagKey = stringPreferencesKey("lastUpdateNotifiedTag")
/** Comma-separated announcement ids the user has opened in Ajustes ▸ Avisos. */
val ReadAnnouncementIdsKey = stringPreferencesKey("readAnnouncementIds")
// Home shows ONLY taste-based suggestions (followed artists / history / favourites + YouTube's
// algorithm on those). When true, the generic "From the community" and "Mood & genres" browse
// sections are hidden so nothing unrelated to the user's taste appears.
val HomeTasteOnlyKey = booleanPreferencesKey("homeTasteOnly")
// Richer/editorial home layout (bigger artwork cards in the taste rows). On by default; the user
// can turn it off from Appearance settings to return to the compact card look.
val HomeRichLayoutKey = booleanPreferencesKey("homeRichLayout")
// Manual "Modo sin conexión": persistent user preference. When ON:
//  · UI shows ONLY fully downloaded / local playable content (home + library = downloads),
//  · search stays local and must not surface stream-only library rows as "playable",
//  · MusicService refuses network stream resolve (downloadCache full hits only — no playerCache URL refresh,
//    no YouTube playerResponse, no radio/automix seed).
// Toggle lives on the offline banner itself AND Settings → Contenido / top cloud icon. Default OFF.
val OfflineModeKey = booleanPreferencesKey("offline_mode")
// Optional genres the user picked during onboarding (CSV), a soft taste signal.
val OnboardingGenresKey = stringPreferencesKey("onboardingGenres")
// "No me gusta": disliked song/artist/album/playlist ids (JSON {songs:[],artists:[],albums:[],playlists:[]}).
// Filtered out of recommendations and skipped during playback so the algorithm stops surfacing them.
val DislikedItemsKey = stringPreferencesKey("dislikedItems")
// "Mantener el estilo": keep autoplay in the same lane as what's playing (e.g. Christian -> Christian,
// not jumping to secular). On by default; best-effort keyword heuristic, never dead-ends playback.
val KeepGenreLaneKey = booleanPreferencesKey("keepGenreLane")
// Allow GenreCache to fetch artist genres from iTunes over MOBILE data too. Default OFF = WiFi-only,
// today's exact behaviour — without opting in, mobile-only users never populate the genre cache and the
// smart queue's genre steering stays silently dead for them. The requests are tiny (~1 KB each, bounded
// per run), so opting in fixes that at negligible data cost. Read INSIDE GenreCache.enrich so its call
// sites (MusicService, HomeViewModel) stay untouched.
val GenreEnrichOnMobileKey = booleanPreferencesKey("genreEnrichOnMobile")
// Scheduled Spotify playlist sync: how often to re-import (days; 0 = off) and which source ids (CSV).
val SpotifyAutoSyncFreqDaysKey = intPreferencesKey("spotifyAutoSyncFreqDays")
val SpotifyAutoSyncSourceIdsKey = stringPreferencesKey("spotifyAutoSyncSourceIds")
// Scheduled YouTube Music sync: how often to re-sync everything (days; 0 = off).
// Default for a missing key is [iad1tya.echo.music.utils.YtmAutoSyncWorker.DEFAULT_FREQ_DAYS] (3):
// the whole library mirrors the account in the background without a daily battery hit.
val YtmAutoSyncFreqDaysKey = intPreferencesKey("ytmAutoSyncFreqDays")
// Last time a YouTube Music full sync actually completed (epoch millis; 0 = never). Surfaced in the sync
// screen so a scheduled sync that silently no-ops (e.g. an expired session) is visible, not a placebo.
val YtmLastSyncKey = longPreferencesKey("ytmLastSyncEpochMs")
// ---- Library UPLOAD sync (Aura -> YouTube Music), so the account works as a real backup ----------
// Master switch for pushing the library UP: new playlists are created on the account without asking,
// existing local-only playlists get linked, deliberate follows become channel subscriptions, and
// liked songs/albums are mirrored up. Turning it off restores the "ask me per playlist" behaviour and
// stops every upload.
//
// THE VALUE IS ALWAYS EXPLICIT — there is no implicit default. Every read falls back to FALSE, and
// [YtmUploadOptInV1AppliedKey] below writes the real value once, per install. Reason: this switch
// writes to a real Google account, and "the update turned it on for you" is not something a paying
// stranger can consent to after the fact.
val YtmUploadSyncKey = booleanPreferencesKey("ytmUploadSync")
// One-time (V1, FRESH key — a set flag or a versionCode bump alone will not re-run a migration) that
// decides the value of [YtmUploadSyncKey] exactly once, on the first launch that sees it:
//   - FRESH install (firstInstallTime == lastUpdateTime): ON. This is the owner's explicit request —
//     "que ahora las playlist que haga no pregunte si las quiero sincronizar con youtube music, que
//     eso ya lo haga por default" — and it is honest for someone setting the app up now: the sync
//     screen is right there, it is the first thing a new user configures, and nothing has been
//     uploaded behind their back yet.
//   - RESTORED install (a device-to-device transfer, a cloud backup, `adb restore`): OFF, same as an
//     update. `firstInstallTime == lastUpdateTime` is TRUE here too — the APK really is the first one
//     on the new phone — so this used to be misread as FRESH and switched account writes ON for
//     somebody who was NOT setting Aura up from scratch. See [iad1tya.echo.music.utils.InstallOrigin].
//   - EXISTING install being UPDATED: OFF. These users installed an Aura that never wrote to their
//     YouTube account. Merely tapping "update" is not consent to start doing so, and the damage
//     (playlists appearing, channels subscribed) is invisible to them until they open YouTube. They
//     get the same switch, defaulted off, one tap away in Ajustes -> Sincronización.
// Both cases end with an explicit stored value, so the owner keeps his default and no existing
// customer is enrolled into remote writes by the act of updating.
val YtmUploadOptInV1AppliedKey = booleanPreferencesKey("ytm_upload_opt_in_v1_applied")
// Set once [iad1tya.echo.music.utils.InstallOrigin] has been established for this install and any
// restore clean-up has run. It lives in the DataStore ON PURPOSE: the backup rules exclude
// `datastore/settings.preferences_pb`, so a database restored onto another phone arrives WITHOUT this
// flag and the classification re-runs there — which is exactly the launch that has to notice it is
// looking at somebody else's account markers.
val InstallOriginResolvedV1Key = booleanPreferencesKey("install_origin_resolved_v1")
// Last time a FULL library upload finished with nothing left pending (epoch millis; 0 = never).
val YtmUploadLastCompletedKey = longPreferencesKey("ytmUploadLastCompletedEpochMs")
// Persisted snapshot of the upload progress report (compact JSON), so the sync screen shows accurate
// "ya sincronizadas / sincronizando" counts across app restarts instead of only per session.
val YtmUploadProgressKey = stringPreferencesKey("ytmUploadProgress")
// Saved/pinned podcast shows (JSON array of {id,title,author,artworkUrl,feedUrl}).
val PinnedPodcastsKey = stringPreferencesKey("pinnedPodcasts")
// Region (2-letter) the user picked for the podcast charts (persisted across sessions).
val PodcastRegionKey = stringPreferencesKey("podcastRegion")
// Per-episode playback progress (JSON map: audioUrl -> {pos,dur,fin,at}) so podcasts resume.
val PodcastProgressKey = stringPreferencesKey("podcastProgress")
// Seed-version gate (replaces the per-feature boolean guards). Seeds re-run whenever the stored
// value is below App.CURRENT_SEED_VERSION; a restored backup carries an older value, so this
// version's feature defaults re-apply after a restore ("new features appear"). See BackupGate.
val SeedVersionKey = intPreferencesKey("seed_version")
// Legacy boolean guard, kept only to detect pre-SeedVersion installs (treated as seed v1).
val JrDefaultsAppliedKey = booleanPreferencesKey("jr_defaults_applied_v2")
// One-time guard: defaults the in-app language to Spanish unless the user picked a language.
val SpanishDefaultAppliedKey = booleanPreferencesKey("spanish_default_applied")
// One-time migration to force the canvas toggles ON (user request: all canvas/lienzo toggles enabled).
val CanvasDefaultOnAppliedKey = booleanPreferencesKey("canvas_default_on_applied")
// One-time (fresh key): auto-enable High-Performance Mode for existing users on LOW-tier / TV / car devices.
val HighPerfModeSeedAppliedKey = booleanPreferencesKey("high_perf_mode_seed_applied_v1")
// Re-migration (fresh key): corrects the over-aggressive perf-mode auto-enable. Turns High-Performance Mode
// OFF on televisions (premium: 1080p video, home carousels, high-res covers) and on capable phones wrongly
// forced into it by the old ~4 GB LOW threshold. Never enables it. See App.migratePerfModeReseedV2.
val PerfModeReseedV2AppliedKey = booleanPreferencesKey("perf_mode_reseed_v2_applied")
// Re-migration (fresh key, runs AFTER PerfModeReseedV2): the V2 reseed forced perf-mode OFF on ALL
// televisions, which also stripped it from genuinely WEAK Android-TV boxes / car head-units. This
// re-enables High-Performance Mode ONLY on genuinely LOW-tier devices whose flag isn't already ON.
// Never touches capable (tier != LOW) phones/TVs. See App.migratePerfModeCapabilityV3.
val PerfModeCapabilityV3AppliedKey = booleanPreferencesKey("perf_mode_capability_v3_applied")
// One-time migration: mini-player background back to DEFAULT so its text stays readable (gray onSurface)
// in light mode. The seeded APPLE_MUSIC mini background forced white text, illegible on a light bar.
val MiniPlayerDefaultBgAppliedKey = booleanPreferencesKey("miniplayer_default_bg_applied")
// One-time migration: appearance follows the SYSTEM theme (user request: start in system theme).
val ThemeSystemDefaultAppliedKey = booleanPreferencesKey("theme_system_default_applied")
// One-time migration v2: force the clean "system theme only" state on update (AUTO + pureBlack OFF +
// dynamic ON), so existing users land on the new system theme and never see two theme cards selected.
val ThemeSystemOnlyV2AppliedKey = booleanPreferencesKey("theme_system_only_v2_applied")
// One-time migration: turn on the requested playback defaults (crossfade ON @ 9s, skip silence ON,
// skip silence instantly ON) for existing users once; afterwards their own choices are respected.
val PlaybackDefaultsV1AppliedKey = booleanPreferencesKey("playback_defaults_v1_applied")
// v2: crossfade duration 11s + linear curve (user request). Re-applied once on update.
val PlaybackDefaultsV2AppliedKey = booleanPreferencesKey("playback_defaults_v2_applied")
val PlaybackDefaultsV3AppliedKey = booleanPreferencesKey("playback_defaults_v3_applied")
// v4: crossfade duration 10s, keep equal-power curve (user chose constant-volume over strict linear).
val PlaybackDefaultsV4AppliedKey = booleanPreferencesKey("playback_defaults_v4_applied")
// v5: crossfade duration 13s, equal-power ("transición suave") curve. Re-applied once on this update.
val PlaybackDefaultsV5AppliedKey = booleanPreferencesKey("playback_defaults_v5_applied")
// v6 (FRESH key): best crossfade default is 9s (equal-power). Re-applies once even for users whose
// V5/AudioDefaultsV2 flags already landed the old 13s default; only moves users still on that 13s default.
val CrossfadeDefault9AppliedKey = booleanPreferencesKey("crossfade_default_9_applied")

// Owner order (0.6.127): FORCED once on update (fresh key — a set flag or versionCode bump alone never
// re-runs a one-time migration): transition 5s + curve 7 "Respiro profundo", SponsorBlock ON, and
// prevent-duplicate-tracks-in-queue ON. The user's later choices win forever after.
val Defaults0127AppliedKey = booleanPreferencesKey("defaults_0_6_127_applied")

// Owner order (0.6.127): on HIGH-tier (gama alta) glass-eligible devices, auto-enable Liquid Glass —
// the global switch + the mini-player background — once. Evaluated per install on current hardware;
// the user's later choices win forever after.
val LiquidGlassHighTierV1AppliedKey = booleanPreferencesKey("liquid_glass_high_tier_v1_applied")

// 0.6.148 — UNDO the mini-player half of the order above, once, with a FRESH key (a set flag or a
// versionCode bump alone never re-runs a one-time migration).
//
// [LiquidGlassHighTierV1AppliedKey] wrote LIQUID_GLASS into [MiniPlayerBackgroundStyleKey] on every
// HIGH-tier eligible device without being asked, and the owner objected to the result TWICE ("por qué
// me sigue saliendo el reproductor flotante y sus botones flotantes en liquid glass"). The redesign's
// mini pill now HONOURS that key (ui/newui/AuraShell.kt), so leaving the written value in place would
// hand him back, on the first launch after the update, the exact look he complained about — this time
// through a control he never touched.
//
// Scope is deliberately the narrowest that fixes it: it rewrites the value ONLY when it still reads
// LIQUID_GLASS, it touches no other key (the global glass switch is the glass door's own business),
// and it runs after every other seed so it also catches the launch where the 0.6.127 order writes that
// value for the first time. The cost of being wrong is one tap: the control that sets it is restored
// to Ajustes ▸ Apariencia in the same release, so anyone who genuinely wants the frosted pill picks it
// again and that choice wins forever after.
val MiniPlayerGlassUndoV1AppliedKey = booleanPreferencesKey("miniplayer_glass_undo_v1_applied")

// Owner order: mini-player default style = Desenfoque (BLUR) on API 31+, once. Runs after the DEFAULT
// seed / glass-undo migrations so upgrades that still sit on DEFAULT or the forced LIQUID_GLASS land
// on BLUR without clobbering a deliberate user pick of GRADIENT / GLOW / APPLE_MUSIC / LIVE_MESH.
val MiniPlayerBlurDefaultV1AppliedKey = booleanPreferencesKey("miniplayer_blur_default_v1_applied")

// Owner order (0.6.130): default curve → 4 "Ascenso" — he heard Respiro profundo's BY-DESIGN center
// valley as an unwanted gap; what he describes (both songs together, one down while the other rises,
// no space) IS the Ascenso shape. FORCED once; duration stays 5s; user choices win afterwards.
val Defaults0130CurveAppliedKey = booleanPreferencesKey("defaults_0_6_130_curve_applied")

// Owner order (0.6.132): same-album pairs were skipping the crossfade ON PURPOSE (gapless bypass,
// default ON) — he hears every skipped blend as "me corta la transición". Forced OFF once; the
// gapless purist can re-enable it in Ajustes.
val Defaults0132GaplessOffAppliedKey = booleanPreferencesKey("defaults_0_6_132_gapless_off_applied")

/** 0.6.136 one-time repair of the legacy seeded accent (0xFF36C5E0) that made the app look like the user
 *  had picked a custom colour, hiding the dynamic-theme switch and every palette selection. */
val ThemeAccentRepairV1AppliedKey = booleanPreferencesKey("theme_accent_repair_v1_applied")
// One-time (V2, FRESH key): re-apply ALL audio defaults — EQ Audiophile + preamp 0.0, crossfade 9s
// equal-power, Safe Volume OFF — for EVERYONE, including users whose per-feature flags were already set by
// the brief 0.6.75/0.6.76 builds (so the settings actually land on this update).
val AudioDefaultsV2AppliedKey = booleanPreferencesKey("audio_defaults_v2_applied")
// One-time: force infinite playback (auto-radio at end of album/playlist/queue) ON for EVERYONE — the owner
// wants endless playback always active. Fresh key so it re-applies even for users who had it toggled off.
val InfinitePlaybackForcedOnKey = booleanPreferencesKey("infinite_playback_forced_on_v1")
// One-time (FRESH key): force "Safe Volume" ON for EVERYONE on this update — new installs and existing
// users, including anyone who previously turned it off (owner wants it on unconditionally). Re-applies once
// even though SafeVolumeEnabledKey may already be set, then remembers it so the user can toggle it off after.
val SafeVolumeDefaultOnAppliedKey = booleanPreferencesKey("safe_volume_default_on_applied")
// One-time (FRESH key): seed the standard-layout lyrics blur (LyricsStandardBlurKey) ON for everyone —
// the default lyric style is APPLE_V2, whose Apple-style blur is gated on that key (default false), so the
// advertised blur was invisible by default. Re-applies once; the user can toggle it off afterwards.
val LyricsBlurDefaultOnV1AppliedKey = booleanPreferencesKey("lyrics_blur_default_on_v1_applied")
// One-time (FRESH key): default the Add-to-playlist dialog sort to LAST_UPDATED descending for EVERYONE —
// recently-added-to playlists first. Re-applies once even if AddToPlaylistSortTypeKey was already set;
// the in-dialog sort header stays functional so the user can change it afterwards.
val AddToPlaylistLastUpdatedDefaultV1AppliedKey =
    booleanPreferencesKey("add_to_playlist_last_updated_default_v1_applied")
// Manual override: force the wide "Spotify split" layout ON even on a device the app wouldn't auto-detect as
// big (e.g. a phone/tablet the user WANTS the split on). ORed into rememberIsWideLayout() — and it is ALSO
// the manual escape hatch for rememberIsTvOrCar(), since TV boxes / car head units running plain Android
// don't report as TV.
val ForceSplitViewKey = booleanPreferencesKey("force_split_view")
// Rotating to landscape with the Apple-Music animated canvas active used to REPLACE the whole player with a
// bare fullscreen canvas + an auto-hiding transport — no cover, title, progress, queue or lyrics (registry
// #48: "en horizontal se pierde toda la interfaz, solo queda la imagen animada"). That immersive view is now
// an explicit CHOICE, default OFF, instead of an automatic consequence of rotating. The VIDEO fullscreen
// path is separate and unaffected.
val ImmersiveCanvasOnRotateKey = booleanPreferencesKey("immersive_canvas_on_rotate")
// Which side the persistent now-playing panel sits on in the wide split (some Android-auto users prefer it
// on the left). false = right (default), true = left.
val SidePanelOnLeftKey = booleanPreferencesKey("side_panel_on_left")
// Show the persistent now-playing side panel in the wide "Spotify split". Default ON; users who prefer the
// browse area at full width (rail + content only) can hide it. Gated into MainActivity's showSideNowPlaying.
val ShowNowPlayingPanelKey = booleanPreferencesKey("show_now_playing_panel")
// Set before the YouTube Music login (which cold-restarts the app) so that, after the restart, we
// return the user to the YouTube Music sync selection screen — like Spotify's import flow.
val OpenYtmSyncAfterLoginKey = booleanPreferencesKey("open_ytm_sync_after_login")
val SpotifySpDcKey = stringPreferencesKey("spotify_sp_dc")
val SpotifySpKeyKey = stringPreferencesKey("spotify_sp_key")
val SpotifyAccountNameKey = stringPreferencesKey("spotify_account_name")
val SpotifyAccountAvatarUrlKey = stringPreferencesKey("spotify_account_avatar_url")
val SpotifyAccessTokenKey = stringPreferencesKey("spotify_access_token")
val SpotifyAccessTokenExpiresAtKey = longPreferencesKey("spotify_access_token_expires_at")
val EnableLegacyIconKey = booleanPreferencesKey("enableLegacyIcon")
val EnableHighRefreshRateKey = booleanPreferencesKey("enableHighRefreshRate")
val DynamicThemeKey = booleanPreferencesKey("dynamicTheme")
// Aura Hi-Res theme: opt-in audiophile palette derived from the brand purple. Default off (current
// look stays the default). Wired into echomusicTheme in MainActivity.
val SelectedThemeColorKey = intPreferencesKey("selectedThemeColor")
// The accent the user picked BY HAND (hex field / HSV wheel in ThemeScreen), kept so the "custom"
// swatch keeps showing their colour and the picker reopens on it even after they try a preset.
// FRESH key on purpose: [SelectedThemeColorKey] stays the one live accent seed the whole theme
// pipeline reads, so nobody's existing colour moves or resets on update (project rule: never
// repurpose a key). 0 == never set a custom colour, which is exactly today's behaviour.
val CustomAccentColorKey = intPreferencesKey("customAccentColorArgb")
// Optional per-role colour overrides for classic ColorScheme + AuraPalette (ThemeScreen
// "Personalizar roles"). FRESH keys; 0 == automatic (today's derived look). Never repurpose.
val CustomBackgroundColorKey = intPreferencesKey("customBackgroundArgb")
val CustomSurfaceColorKey = intPreferencesKey("customSurfaceArgb")
val CustomOnBackgroundColorKey = intPreferencesKey("customOnBackgroundArgb")
val CustomOnSurfaceVariantColorKey = intPreferencesKey("customOnSurfaceVariantArgb")
val CustomOutlineColorKey = intPreferencesKey("customOutlineArgb")
val CustomOnPrimaryColorKey = intPreferencesKey("customOnPrimaryArgb")
// How literally the accent seed is applied — see [iad1tya.echo.music.ui.theme.AccentVividness].
// FRESH key, default SOFT == byte-for-byte today's Material 3 tonal look.
val AccentVividnessKey = stringPreferencesKey("accentVividness")
// Named, hand-authored app theme — see [iad1tya.echo.music.ui.theme.ThemePreset]. FRESH key, default
// NONE == byte-for-byte today's seed-driven look, so an update repaints nobody. Set to MUESTREO the
// whole colour scheme (surfaces included) comes from the app icon's palette instead of the seed
// engine; selecting any swatch, Dynamic or a typed hex puts it back to NONE.
val AppThemePresetKey = stringPreferencesKey("appThemePreset")
val DarkModeKey = stringPreferencesKey("darkMode")
val PureBlackKey = booleanPreferencesKey("pureBlack")
val PureBlackMiniPlayerKey = booleanPreferencesKey("pureBlackMiniPlayer")
val DensityScaleKey = floatPreferencesKey("density_scale_factor")

enum class DensityScale(val value: Float, val label: String) {
    NATIVE(1.0f, "Native (100%)"),
    SLIGHTLY_COMPACT(0.85f, "Slightly Compact (85%)"),
    COMPACT(0.75f, "Compact (75%)"),
    VERY_COMPACT(0.65f, "Very Compact (65%)"),
    ULTRA_COMPACT(0.55f, "Ultra Compact (55%)");

    companion object {
        fun fromValue(value: Float): DensityScale = entries.find { it.value == value } ?: NATIVE
    }
}

val DefaultOpenTabKey = stringPreferencesKey("defaultOpenTab")
val GridItemsSizeKey = stringPreferencesKey("gridItemSize")
val SliderStyleKey = stringPreferencesKey("sliderStyle")
val SquigglySliderKey = booleanPreferencesKey("squigglySlider")
val GlobalHapticsKey = booleanPreferencesKey("globalHaptics")
val SwipeToSongKey = booleanPreferencesKey("SwipeToSong")
val SwipeToRemoveSongKey = booleanPreferencesKey("SwipeToRemoveSong")
val UseNewPlayerDesignKey= booleanPreferencesKey("useNewPlayerDesign")
val UseNewMiniPlayerDesignKey = booleanPreferencesKey("useNewMiniPlayerDesign")
val ShowCodecOnPlayerKey = booleanPreferencesKey("showCodecOnPlayer")
val HidePlayerSliderKey = booleanPreferencesKey("hidePlayerSlider")
val HidePlayerThumbnailKey = booleanPreferencesKey("hidePlayerThumbnail")
val ThumbnailCornerRadiusKey = floatPreferencesKey("thumbnailCornerRadius")
val CropAlbumArtKey = booleanPreferencesKey("cropAlbumArt")
val SeekExtraSeconds = booleanPreferencesKey("seekExtraSeconds")
val PauseOnMute = booleanPreferencesKey("pauseOnMute")
val ResumeOnBluetoothConnectKey = booleanPreferencesKey("resumeOnBluetoothConnect")
val KeepScreenOn = booleanPreferencesKey("keepScreenOn")

enum class SliderStyle {
    DEFAULT,
    WAVY,
    SLIM
}

const val SYSTEM_DEFAULT = "SYSTEM_DEFAULT"
val AppLanguageKey = stringPreferencesKey("appLanguage")
val ContentLanguageKey = stringPreferencesKey("contentLanguage")
val ContentCountryKey = stringPreferencesKey("contentCountry")
val SuggestionRegionKey = stringPreferencesKey("suggestionRegion")
val EnableKugouKey = booleanPreferencesKey("enableKugou")
val EnableLrcLibKey = booleanPreferencesKey("enableLrclib")
val EnableBetterLyricsKey = booleanPreferencesKey("enableBetterLyrics")
val EnableSimpMusicKey = booleanPreferencesKey("enableSimpMusic")
val EnableYouLyPlusKey = booleanPreferencesKey("enableYouLyPlus")
val EnablePaxsenixKey = booleanPreferencesKey("enablePaxsenix")
val UnisonLyricsEnabledKey = booleanPreferencesKey("unison_lyrics_enabled")
val HideExplicitKey = booleanPreferencesKey("hideExplicit")
val HideVideoSongsKey = booleanPreferencesKey("hideVideoSongs")
val HideYoutubeShortsKey = booleanPreferencesKey("hideYoutubeShorts")
val ShowArtistDescriptionKey = booleanPreferencesKey("showArtistDescription")
val ShowArtistSubscriberCountKey = booleanPreferencesKey("showArtistSubscriberCount")
val ShowMonthlyListenersKey = booleanPreferencesKey("showMonthlyListeners")
val ShowArtistVideoKey = booleanPreferencesKey("showArtistVideo")
val ShowArtistBackgroundVideoKey = booleanPreferencesKey("showArtistBackgroundVideo")
val ProxyEnabledKey = booleanPreferencesKey("proxyEnabled")
val ProxyUrlKey = stringPreferencesKey("proxyUrl")
val ProxyTypeKey = stringPreferencesKey("proxyType")
val ProxyUsernameKey = stringPreferencesKey("proxyUsername")
val ProxyPasswordKey = stringPreferencesKey("proxyPassword")
val YtmSyncKey = booleanPreferencesKey("ytmSync")

val ShowAudioFallbackToastKey = booleanPreferencesKey("show_audio_fallback_toast")
val AudioQualityKey = stringPreferencesKey("audioQuality")
val IpVersionKey = stringPreferencesKey("ipVersion")

enum class AudioQuality {
    OPUS,
    SAAVN,
    LOSSLESS,
}

val DownloadQualityKey = stringPreferencesKey("downloadQuality")

enum class DownloadQuality {
    YOUTUBE,
    SAAVN,
    LOSSLESS,
}

val AudioOffload = booleanPreferencesKey("enableOffload")

val PersistentQueueKey = booleanPreferencesKey("persistentQueue")
val PersistentShuffleAcrossQueuesKey = booleanPreferencesKey("persistentShuffleAcrossQueues")
val RememberShuffleAndRepeatKey = booleanPreferencesKey("rememberShuffleAndRepeat")
val ShuffleModeKey = booleanPreferencesKey("shuffleMode")
// Enhanced Shuffle ("Aleatorio mejorado"), default ON. When on, the shuffle button gains PERSISTENT
// per-context no-repeat memory: within a playlist / the whole library it never repeats a song until every
// song has played (memory survives restarts, days, and toggling shuffle off/on), then the completed cycle
// resets and hands off to the infinite radio. Off = classic in-memory shuffle.
val EnhancedShuffleKey = booleanPreferencesKey("enhanced_shuffle")
// "¿Volver a la cola anterior?", default ON. When on, leaving a listening list (playlist / auto-playlist /
// library) to play an album or an artist snapshots the outgoing queue IN MEMORY and, once the user leaves
// the screen he jumped to, a snackbar offers to resume it at the same song and second. Off = nothing is
// captured at all (MusicService.captureQueueForResumeOffer returns first thing), not merely a hidden
// prompt. The snapshot expires after PreviousQueueRule.OFFER_TTL_MS and never touches disk.
val PreviousQueueOfferKey = booleanPreferencesKey("previousQueueOffer")

// AIMP-style smooth entry on MANUAL track changes (skip/next/tap): the new song fades in over ~400ms
// instead of slamming in at full level. Default ON (owner request). Auto-advance crossfade unaffected.
val FadeOnManualChangeKey = booleanPreferencesKey("fade_on_manual_change")
val SkipSilenceKey = booleanPreferencesKey("skipSilence")
val SkipSilenceInstantKey = booleanPreferencesKey("skipSilenceInstant")
val AudioNormalizationKey = booleanPreferencesKey("audioNormalization")
// "Safe Volume" (default ON as of this update — one-time forced ON for everyone via
// SafeVolumeDefaultOnAppliedKey; still user-toggleable afterwards): brings loud masters DOWN toward a
// reference (attenuate-only) + a gentle limiter, applied in the live float Superpowered EQ processor, so
// loud tracks don't blast at full native level. Does not break Hi-Res output.
val SafeVolumeEnabledKey = booleanPreferencesKey("safeVolume")
val AutoLoadMoreKey = booleanPreferencesKey("autoLoadMore")
val DisableLoadMoreWhenRepeatAllKey = booleanPreferencesKey("disableLoadMoreWhenRepeatAll")
val AutoDownloadOnLikeKey = booleanPreferencesKey("autoDownloadOnLike")
val SimilarContent = booleanPreferencesKey("similarContent")
val AutoSkipNextOnErrorKey = booleanPreferencesKey("autoSkipNextOnError")
val StopMusicOnTaskClearKey = booleanPreferencesKey("stopMusicOnTaskClear")
val ShufflePlaylistFirstKey = booleanPreferencesKey("shufflePlaylistFirst")
val PreventDuplicateTracksInQueueKey = booleanPreferencesKey("preventDuplicateTracksInQueue")
val CrossfadeEnabledKey = booleanPreferencesKey("crossfadeEnabled")
val CrossfadeDurationKey = floatPreferencesKey("crossfadeDuration")
val CrossfadeGaplessKey = booleanPreferencesKey("crossfadeGapless")
// Transition curve: 0 = Linear, 1 = Smooth/equal-power (default, no volume dip), 2 = Long S-curve,
// 3 = Exponential (quick). (Matches CrossfadeMath.getGains + the PlayerSettings labels.)
val CrossfadeCurveKey = intPreferencesKey("crossfadeCurve")

// 0.6.92 Descifrado de YouTube — wall-clock ms of the last user-initiated cipher refresh, for a light
// UI cooldown on the "Descifrado de YouTube" settings screen. Does not affect cipher behaviour.
val CipherManualRefreshAtKey = longPreferencesKey("cipherManualRefreshAtMs")


val MaxImageCacheSizeKey = intPreferencesKey("maxImageCacheSize")
val MaxSongCacheSizeKey = intPreferencesKey("maxSongCacheSize")
val ExportDirectoryUriKey = stringPreferencesKey("exportDirectoryUri")
val ExportingSongIdsKey = stringPreferencesKey("exportingSongIds")
val ExportedSongIdsKey = stringPreferencesKey("exportedSongIds")
/** Song ids exported as social MP4 (Biblioteca ▸ Vídeos exportados). */
val ExportedVideoIdsKey = stringPreferencesKey("exportedVideoIds")
/**
 * SAF content URIs for exported files: `songId\u001Furi\u001EsongId\u001Furi…`
 * Used to share / reopen the file the user exported (not the YouTube stream).
 */
val ExportedFileUrisKey = stringPreferencesKey("exportedFileUris")
/** Set of deleted playlist browseIds so remote sync does not re-import them */
val SuppressedPlaylistIdsKey = stringPreferencesKey("suppressedPlaylistIds")
val EnableExportAsMp3Key = booleanPreferencesKey("enableExportAsMp3")
/** One-shot: seed Export as MP3 ON for installs that never wrote the key. */
val EnableExportAsMp3DefaultOnV1AppliedKey = booleanPreferencesKey("enable_export_as_mp3_default_on_v1_applied")
/**
 * One-shot (0.6.160): clear Coil memory+disk cache after LocalAudioArtFetcher started preferring
 * ID3 APIC over MediaStore loadThumbnail (which had cached blank/generic thumbs for the same URIs).
 */
val LocalAudioArtApicV1AppliedKey = booleanPreferencesKey("local_audio_art_apic_v1_applied")

/**
 * One-shot (0.6.164): clear Coil caches after localaudioart models switched to encoded
 * `localaudioart://a/…#apic2` (nested content:// strings were failing Coil/ContentResolver).
 */
val LocalAudioArtApicV2AppliedKey = booleanPreferencesKey("local_audio_art_apic_v2_applied")

val PauseListenHistoryKey = booleanPreferencesKey("pauseListenHistory")
val PauseSearchHistoryKey = booleanPreferencesKey("pauseSearchHistory")
val DisableScreenshotKey = booleanPreferencesKey("disableScreenshot")

val DiscordTokenKey = stringPreferencesKey("discordToken")
val EnableDiscordRPCKey = booleanPreferencesKey("discordRPCEnable")
val DiscordUseDetailsKey = booleanPreferencesKey("discordUseDetails")
val DiscordStatusKey = stringPreferencesKey("discordStatus")
val DiscordButton1TextKey = stringPreferencesKey("discordButton1Text")
val DiscordButton1VisibleKey = booleanPreferencesKey("discordButton1Visible")
val DiscordButton2TextKey = stringPreferencesKey("discordButton2Text")
val DiscordButton2VisibleKey = booleanPreferencesKey("discordButton2Visible")
val DiscordActivityTypeKey = stringPreferencesKey("discordActivityType")
val DiscordActivityNameKey = stringPreferencesKey("discordActivityName")
val DiscordAdvancedModeKey = booleanPreferencesKey("discordAdvancedMode")


val EnableGoogleCastKey = booleanPreferencesKey("enableGoogleCast")


val ListenTogetherServerUrlKey = stringPreferencesKey("listenTogetherServerUrl")
val ListenTogetherUsernameKey = stringPreferencesKey("listenTogetherUsername")
val ListenTogetherAutoApprovalKey = booleanPreferencesKey("listenTogetherAutoApproval")
val ListenTogetherSyncVolumeKey = booleanPreferencesKey("listenTogetherSyncVolume")
val ListenTogetherSmartResyncKey = booleanPreferencesKey("listenTogetherSmartResync")
val ListenTogetherBlockedUsersKey = stringPreferencesKey("listenTogetherBlockedUsers")
val ListenTogetherInTopBarKey = booleanPreferencesKey("listenTogetherInTopBar")

val ListenTogetherSessionTokenKey = stringPreferencesKey("listenTogetherSessionToken")
val ListenTogetherRoomCodeKey = stringPreferencesKey("listenTogetherRoomCode")
val ListenTogetherUserIdKey = stringPreferencesKey("listenTogetherUserId")
val ListenTogetherIsHostKey = booleanPreferencesKey("listenTogetherIsHost")
val ListenTogetherSessionTimestampKey = longPreferencesKey("listenTogetherSessionTimestamp")

val LastFMSessionKey = stringPreferencesKey("lastfmSession")
val LastFMUsernameKey = stringPreferencesKey("lastfmUsername")
val EnableLastFMScrobblingKey = booleanPreferencesKey("lastfmScrobblingEnable")
val LastFMUseNowPlaying = booleanPreferencesKey("lastfmUseNowPlaying")

val LastFMUseSendLikes = booleanPreferencesKey("lastfmUseSendLikes")

// Last.fm taste import (opt-in, SECONDARY signal). When ON for a logged-in Last.fm user, a daily worker caches
// the user's Last.fm top artists + loved tracks and the on-device AffinityEngine folds them into per-NAME
// affinity ONLY as a capped, cross-app seed — real local plays always stay primary. Default OFF => zero change.
val UseLastFmTasteKey = booleanPreferencesKey("lastfmTasteEnabled")
val LastFmTasteCacheKey = stringPreferencesKey("lastfmTasteCache")
val LastFmTasteFetchedAtKey = longPreferencesKey("lastfmTasteFetchedAt")

val ScrobbleDelayPercentKey = floatPreferencesKey("scrobbleDelayPercent")
val ScrobbleMinSongDurationKey = intPreferencesKey("scrobbleMinSongDuration")
val ScrobbleDelaySecondsKey = intPreferencesKey("scrobbleDelaySeconds")

// ListenBrainz scrobbling (opt-in, network-only). Default OFF; nothing is submitted unless enabled AND a
// user token is set. Routed through ScrobbleManager alongside Last.fm so playback wiring stays untouched.
val ListenBrainzEnabledKey = booleanPreferencesKey("listenBrainzEnabled")
val ListenBrainzTokenKey = stringPreferencesKey("listenBrainzToken")

val ChipSortTypeKey = stringPreferencesKey("chipSortType")
val SongSortTypeKey = stringPreferencesKey("songSortType")
val SongSortDescendingKey = booleanPreferencesKey("songSortDescending")
val PlaylistSongSortTypeKey = stringPreferencesKey("playlistSongSortType")
val PlaylistSongSortDescendingKey = booleanPreferencesKey("playlistSongSortDescending")
val ArtistSortTypeKey = stringPreferencesKey("artistSortType")
val ArtistSortDescendingKey = booleanPreferencesKey("artistSortDescending")
val AlbumSortTypeKey = stringPreferencesKey("albumSortType")
val AlbumSortDescendingKey = booleanPreferencesKey("albumSortDescending")
val PlaylistSortTypeKey = stringPreferencesKey("playlistSortType")
val PlaylistSortDescendingKey = booleanPreferencesKey("playlistSortDescending")
val AddToPlaylistSortTypeKey = stringPreferencesKey("addToPlaylistSortType")
val AddToPlaylistSortDescendingKey = booleanPreferencesKey("addToPlaylistSortDescending")
val ArtistSongSortTypeKey = stringPreferencesKey("artistSongSortType")
val ArtistSongSortDescendingKey = booleanPreferencesKey("artistSongSortDescending")
val MixSortTypeKey = stringPreferencesKey("mixSortType")
// NOTE: this used to store into "albumSortDescending" (a copy/paste collision with AlbumSortDescendingKey),
// so the Mix tab and the Albums tab shared a single stored value. Own key now; the Mix tab falls back to its
// default once on update.
val MixSortDescendingKey = booleanPreferencesKey("mixSortDescending")

val LocalSongsMinDurationSecondsKey = intPreferencesKey("local_songs_min_duration_seconds")
val LocalSongsExcludedFoldersKey = stringSetPreferencesKey("local_songs_excluded_folders")
/** When non-empty, the local scanner imports ONLY tracks under these folders (MediaStore relative paths). */
val LocalSongsIncludedFoldersKey = stringSetPreferencesKey("local_songs_included_folders")
val LocalSongsSortTypeKey = stringPreferencesKey("local_songs_sort_type")
val LocalSongsSortDescendingKey = booleanPreferencesKey("local_songs_sort_descending")

val SongFilterKey = stringPreferencesKey("songFilter")
val ArtistFilterKey = stringPreferencesKey("artistFilter")
val AlbumFilterKey = stringPreferencesKey("albumFilter")

val LastFullSyncKey = longPreferencesKey("last_full_sync")


const val SYNC_COOLDOWN = 30 * 60L

val ArtistViewTypeKey = stringPreferencesKey("artistViewType")
val AlbumViewTypeKey = stringPreferencesKey("albumViewType")
val PlaylistViewTypeKey = stringPreferencesKey("playlistViewType")
val SongViewTypeKey = stringPreferencesKey("songViewType")

val PlaylistEditLockKey = booleanPreferencesKey("playlistEditLock")
val QuickPicksKey = stringPreferencesKey("discover")
val PreferredLyricsProviderKey = stringPreferencesKey("lyricsProvider")
val LyricsProviderOrderKey = stringPreferencesKey("lyricsProviderOrder")
val QueueEditLockKey = booleanPreferencesKey("queueEditLockV2")
val RandomizeHomeOrderKey = booleanPreferencesKey("randomizeHomeOrder")
val AlbumCanvasEnabledKey = booleanPreferencesKey("albumCanvasEnabled")

val ShowLikedPlaylistKey = booleanPreferencesKey("show_liked_playlist")
val ShowDownloadedPlaylistKey = booleanPreferencesKey("show_downloaded_playlist")
val ShowExportedPlaylistKey = booleanPreferencesKey("show_exported_playlist")
val ShowExportedVideosPlaylistKey = booleanPreferencesKey("show_exported_videos_playlist")
val ShowTopPlaylistKey = booleanPreferencesKey("show_top_playlist")
val ShowCachedPlaylistKey = booleanPreferencesKey("show_cached_playlist")
val ShowUploadedPlaylistKey = booleanPreferencesKey("show_uploaded_playlist")
// Pinned playlists on Home (was "showSpeedDial" / marcación rápida). New key so old OFF default
// does not hide playlists the user just pinned.
val ShowSpeedDialKey = booleanPreferencesKey("showPinnedPlaylistsOnHome")
val ShowCommentButtonKey = booleanPreferencesKey("show_comment_button")

enum class LibraryViewType {
    LIST,
    GRID,
    ;

    fun toggle() =
        when (this) {
            LIST -> GRID
            GRID -> LIST
        }
}

enum class SongFilter {
    LIBRARY,
    LIKED,
    DOWNLOADED,
    UPLOADED,
    EXPORTED
}

enum class ArtistFilter {
    LIBRARY,
    LIKED
}

enum class AlbumFilter {
    LIBRARY,
    LIKED,
    UPLOADED
}

enum class SongSortType {
    CREATE_DATE,
    NAME,
    ARTIST,
    PLAY_TIME,
}

enum class PlaylistSongSortType {
    CUSTOM,
    CREATE_DATE,
    NAME,
    ARTIST,
    PLAY_TIME,
}

enum class AutoPlaylistSongSortType {
    CREATE_DATE,
    NAME,
    ARTIST,
    PLAY_TIME,
}

enum class ArtistSortType {
    CREATE_DATE,
    NAME,
    SONG_COUNT,
    PLAY_TIME,
}

enum class ArtistSongSortType {
    CREATE_DATE,
    NAME,
    PLAY_TIME,
}

enum class AlbumSortType {
    CREATE_DATE,
    NAME,
    ARTIST,
    YEAR,
    SONG_COUNT,
    LENGTH,
    PLAY_TIME,
}

enum class PlaylistSortType {
    CREATE_DATE,
    NAME,
    SONG_COUNT,
    LAST_UPDATED,
}

enum class MixSortType {
    CREATE_DATE,
    NAME,
    LAST_UPDATED,
}

enum class GridItemSize {
    BIG,
    SMALL,
}

enum class MyTopFilter {
    ALL_TIME,
    DAY,
    WEEK,
    MONTH,
    YEAR,
    ;

    fun toTimeMillis(): Long =
        when (this) {
            DAY ->
                LocalDateTime
                    .now()
                    .minusDays(1)
                    .toInstant(ZoneOffset.UTC)
                    .toEpochMilli()

            WEEK ->
                LocalDateTime
                    .now()
                    .minusWeeks(1)
                    .toInstant(ZoneOffset.UTC)
                    .toEpochMilli()

            MONTH ->
                LocalDateTime
                    .now()
                    .minusMonths(1)
                    .toInstant(ZoneOffset.UTC)
                    .toEpochMilli()

            YEAR ->
                LocalDateTime
                    .now()
                    .minusMonths(12)
                    .toInstant(ZoneOffset.UTC)
                    .toEpochMilli()

            ALL_TIME -> 0
        }
}

enum class QuickPicks {
    QUICK_PICKS,
    LAST_LISTEN,
}

enum class PreferredLyricsProvider {
    LRCLIB,
    KUGOU,
    BETTER_LYRICS,
    SIMPMUSIC,
    YOULYPLUS,
    PAXSENIX,
}

enum class PlayerButtonsStyle {
    DEFAULT,
    PRIMARY,
    TERTIARY
}

enum class PlayerBackgroundStyle {
    DEFAULT,
    GRADIENT,
    BLUR,
    GLOW_ANIMATED,
    APPLE_MUSIC,
    LIVE_MESH,
    // Liquid Glass (Beta): backdrop-sampling glass surfaces (ui/component/backdrop engine).
    // Runtime-gated: API 31+, raw tier MID/HIGH, not TV/car, Performance Mode off (isGlassEligible).
    LIQUID_GLASS,
}

// ── Liquid Glass (Beta) ──
// Master switch is DEFAULT OFF; the effect additionally requires the runtime eligibility gate
// (GlassEffect.isGlassEligible) so it can never light up on TV/car, LOW-tier or Performance Mode devices.
val LiquidGlassGlobalEnabledKey = booleanPreferencesKey("liquidGlassGlobalEnabled")
// 0 = theme-adaptive (dark text on light glass, white on dark) — NOT hardcoded white (upstream bug).
val LiquidGlassTextColorKey = intPreferencesKey("liquidGlassTextColor")
// 0 = theme-adaptive surface tint.
val LiquidGlassSurfaceTintColorKey = intPreferencesKey("liquidGlassSurfaceTintColor")
val LiquidGlassSurfaceOpacityKey = floatPreferencesKey("liquidGlassSurfaceOpacity")
val LiquidGlassVibrancyKey = floatPreferencesKey("liquidGlassVibrancy")
val LiquidGlassBlurRadiusKey = floatPreferencesKey("liquidGlassBlurRadius")
val LiquidGlassLensHeightKey = floatPreferencesKey("liquidGlassLensHeight")
val LiquidGlassLensAmountKey = floatPreferencesKey("liquidGlassLensAmount")
val LiquidGlassChromaticAberrationKey = booleanPreferencesKey("liquidGlassChromaticAberration")
val LiquidGlassDepthEffectKey = booleanPreferencesKey("liquidGlassDepthEffect")
val LiquidGlassPlayerEnabledKey = booleanPreferencesKey("liquidGlassPlayerEnabled")
val LiquidGlassMiniPlayerEnabledKey = booleanPreferencesKey("liquidGlassMiniPlayerEnabled")
val LiquidGlassNavBarEnabledKey = booleanPreferencesKey("liquidGlassNavBarEnabled")

val TopSize = stringPreferencesKey("topSize")
val HistoryDuration = floatPreferencesKey("historyDuration")

val PlayerButtonsStyleKey = stringPreferencesKey("player_buttons_style")
val PlayerBackgroundStyleKey = stringPreferencesKey("playerBackgroundStyle")
val MiniPlayerBackgroundStyleKey = stringPreferencesKey("miniPlayerBackgroundStyle")
val ShowLyricsKey = booleanPreferencesKey("showLyrics")
val SwipeLyricsKey = booleanPreferencesKey("swipeLyrics")
val EnableLyricsThumbnailPlayPauseKey = booleanPreferencesKey("enableLyricsThumbnailPlayPause")
val LyricsTextPositionKey = stringPreferencesKey("lyricsTextPosition")
val LyricsClickKey = booleanPreferencesKey("lyricsClick")
val LyricsScrollKey = booleanPreferencesKey("lyricsScrollKey")
val LyricsRomanizeJapaneseKey = booleanPreferencesKey("lyricsRomanizeJapanese")
val LyricsRomanizeKoreanKey = booleanPreferencesKey("lyricsRomanizeKorean")
val LyricsRomanizeChineseKey = booleanPreferencesKey("lyricsRomanizeChinese")
val LyricsRomanizeRussianKey = booleanPreferencesKey("lyricsRomanizeRussian")
val LyricsRomanizeUkrainianKey = booleanPreferencesKey("lyricsRomanizeUkrainian")
val LyricsRomanizeSerbianKey = booleanPreferencesKey("lyricsRomanizeSerbian")
val LyricsRomanizeBulgarianKey = booleanPreferencesKey("lyricsRomanizeBulgarian")
val LyricsRomanizeBelarusianKey = booleanPreferencesKey("lyricsRomanizeBelarusian")
val LyricsRomanizeKyrgyzKey = booleanPreferencesKey("lyricsRomanizeKyrgyz")
val LyricsRomanizeMacedonianKey = booleanPreferencesKey("lyricsRomanizeMacedonian")
val LyricsRomanizeHindiKey = booleanPreferencesKey("lyricsRomanizeHindi")
val LyricsRomanizePunjabiKey = booleanPreferencesKey("lyricsRomanizePunjabi")
val LyricsRomanizeAsMainKey = booleanPreferencesKey("lyricsRomanizeAsMain")
val LyricsRomanizeCyrillicByLineKey = booleanPreferencesKey("lyricsRomanizeCyrillicByLine")
// Opt-in (default false): when the lyrics of an English-looking song open, prompt "¿Traducir?"
// and translate via the FREE keyless AI on confirm. Off = no prompt (previous behavior).
val AskTranslateLyricsOnOpenKey = booleanPreferencesKey("askTranslateLyricsOnOpen")
/** When true, opening lyrics auto-translates to [TranslateLanguageKey] (default: Español Latinoamérica). */
val AutoTranslateLyricsKey = booleanPreferencesKey("autoTranslateLyrics")
/** One-shot: seed auto-translate ON + target es-419 for everyone who still has English translate defaults. */
val LyricsEsLatamAutoTranslateV1AppliedKey = booleanPreferencesKey("lyrics_es_latam_auto_translate_v1_applied")
// AI text-to-playlist feature toggle (Library → "Lista AI"). Kill-switch, defaults on.
val AiPlaylistEnabledKey = booleanPreferencesKey("ai_playlist_enabled")
// Opt-in (default false): daily worker that rebuilds the ONE persistent "Recomendado para ti (IA)"
// playlist from the user's listening history (AutoRecoPlaylistWorker). Off = zero network/battery cost.
val AiRecommendedPlaylistKey = booleanPreferencesKey("aiRecommendedPlaylist")
val OpenRouterApiKey = stringPreferencesKey("openRouterApiKey")
val AiProviderKey = stringPreferencesKey("aiProvider")
val OpenRouterBaseUrlKey = stringPreferencesKey("openRouterBaseUrl")
val OpenRouterModelKey = stringPreferencesKey("openRouterModel")
val TranslateModeKey = stringPreferencesKey("translateMode")
val TranslateLanguageKey = stringPreferencesKey("translateLanguage")
val DeeplApiKey = stringPreferencesKey("deeplApiKey")
val DeeplFormalityKey = stringPreferencesKey("deeplFormality")
val LyricsGlowEffectKey = booleanPreferencesKey("lyricsGlowEffect")
val AppleMusicLyricsBlurKey = booleanPreferencesKey("appleMusicLyricsBlur")
val LyricsStandardBlurKey = booleanPreferencesKey("lyricsStandardBlur")
val HideStatusBarOnFullscreenKey = booleanPreferencesKey("hideStatusBarOnFullscreen")

val LyricsAnimationStyleKey = stringPreferencesKey("lyricsAnimationStyle")
enum class LyricsAnimationStyle {
    NONE,
    FADE,
    GLOW,
    SLIDE,
    KARAOKE,
    APPLE,
    APPLE_V2,
    echomusic_1,
    LYRICS_V2,
    METRO_LYRICS,
}

val LyricsTextSizeKey = floatPreferencesKey("lyricsTextSize")
val LyricsLineSpacingKey = floatPreferencesKey("lyricsLineSpacing")

val PlayerVolumeKey = floatPreferencesKey("playerVolume")
val RepeatModeKey = intPreferencesKey("repeatMode")

val SearchSourceKey = stringPreferencesKey("searchSource")
val SwipeThumbnailKey = booleanPreferencesKey("swipeThumbnail")
val RotatingThumbnailKey = booleanPreferencesKey("rotatingThumbnail")
val CanvasThumbnailAnimationKey = booleanPreferencesKey("canvasThumbnailAnimation")
val SwipeSensitivityKey = floatPreferencesKey("swipeSensitivity")

enum class SearchSource {
    LOCAL,
    ONLINE,
    ;

    fun toggle() =
        when (this) {
            LOCAL -> ONLINE
            ONLINE -> LOCAL
        }
}

val VisitorDataKey = stringPreferencesKey("visitorData")
val DataSyncIdKey = stringPreferencesKey("dataSyncId")
val InnerTubeCookieKey = stringPreferencesKey("innerTubeCookie")

// 0.6.104 FIX D (#28.3): one-time flag — clear the (possibly stale) visitorData/dataSyncId session tokens
// ONCE on this update so a fresh session token is fetched, fixing "won't play for some users" caused by a
// poisoned persisted session. A fresh flag key is required (a set flag / versionCode bump alone won't re-run
// a one-time migration). Never touches InnerTubeCookieKey (the login).
val SessionRefreshedFor104Key = booleanPreferencesKey("session_refreshed_for_104")
// One-time (FRESH key): force the lyric style to Apple Music (APPLE_V2) once on 0.6.104 so it becomes the
// default for everyone (still toggleable afterwards). Mirrors the SafeVolume default-on migration pattern.
val LyricsAppleDefaultFor104Key = booleanPreferencesKey("lyrics_apple_default_for_104")

// 0.6.104 FIX B1 (#28.1): persisted mirror of MusicService.songUrlCache (mediaId -> {url, expireEpochMillis})
// as a compact JSON blob, LRU-bounded, so a resolved stream URL survives a process restart (app update) and
// the first play/resume after an update doesn't re-run the full slow resolver. Only NON-expired entries are
// ever loaded/served.
val SongUrlCacheBlobKey = stringPreferencesKey("song_url_cache_blob")
val AccountNameKey = stringPreferencesKey("accountName")
val AccountEmailKey = stringPreferencesKey("accountEmail")
val AccountChannelHandleKey = stringPreferencesKey("accountChannelHandle")
val UseLoginForBrowse = booleanPreferencesKey("useLoginForBrowse")
val LastOpenedVersionCodeKey = intPreferencesKey("lastOpenedVersionCode")

// One-time: whether the proactive background-reliability prompt (battery exemption + autostart) was already
// offered on an aggressive OEM (MIUI/HyperOS/ColorOS/etc.), so we don't nag. See BackgroundReliability.
val BatteryReliabilityPromptShownKey = booleanPreferencesKey("batteryReliabilityPromptShown")

// Epoch ms of the newest OEM playback-kill (ScreenOffCPU / OneKeyClean / …) we already surfaced in the
// reliability dialog. Re-prompt when ExitReasonReporter finds a NEWER kill — exemption alone does not
// stop HyperOS ScreenOffCPUCheckKill.
val BatteryReliabilityOemKillPromptTsKey = longPreferencesKey("batteryReliabilityOemKillPromptTs")

val LanguageCodeToName =
    mapOf(
        "af" to "Afrikaans",
        "az" to "Azərbaycan",
        "id" to "Bahasa Indonesia",
        "ms" to "Bahasa Malaysia",
        "ca" to "Català",
        "cs" to "Čeština",
        "da" to "Dansk",
        "de" to "Deutsch",
        "et" to "Eesti",
        "en-GB" to "English (UK)",
        "en" to "English (US)",
        "es" to "Español (España)",
        "es-419" to "Español (Latinoamérica)",
        "eu" to "Euskara",
        "fil" to "Filipino",
        "fr" to "Français",
        "fr-CA" to "Français (Canada)",
        "gl" to "Galego",
        "hr" to "Hrvatski",
        "zu" to "IsiZulu",
        "is" to "Íslenska",
        "it" to "Italiano",
        "sw" to "Kiswahili",
        "lt" to "Lietuvių",
        "hu" to "Magyar",
        "nl" to "Nederlands",
        "no" to "Norsk",
        "or" to "Odia",
        "uz" to "O‘zbe",
        "pl" to "Polski",
        "pt-PT" to "Português",
        "pt" to "Português (Brasil)",
        "ro" to "Română",
        "sq" to "Shqip",
        "sk" to "Slovenčina",
        "sl" to "Slovenščina",
        "fi" to "Suomi",
        "sv" to "Svenska",
        "bo" to "Tibetan བོད་སྐད།",
        "vi" to "Tiếng Việt",
        "tr" to "Türkçe",
        "bg" to "Български",
        "ky" to "Кыргызча",
        "kk" to "Қазақ Тілі",
        "mk" to "Македонски",
        "mn" to "Монгол",
        "ru" to "Русский",
        "sr" to "Српски",
        "uk" to "Українська",
        "el" to "Ελληνικά",
        "hy" to "Հայերեն",
        "iw" to "עברית",
        "ur" to "اردو",
        "ar" to "العربية",
        "fa" to "فارسی",
        "ne" to "नेपाली",
        "mr" to "मराठी",
        "hi" to "हिन्दी",
        "bn" to "বাংলা",
        "pa" to "ਪੰਜਾਬੀ",
        "gu" to "ગુજરાતી",
        "ta" to "தமிழ்",
        "te" to "తెలుగు",
        "kn" to "ಕನ್ನಡ",
        "ml" to "മലയാളം",
        "si" to "සිංහල",
        "th" to "ภาษาไทย",
        "lo" to "ລາວ",
        "my" to "ဗမာ",
        "ka" to "ქართული",
        "am" to "አማርኛ",
        "km" to "ខ្មែរ",
        "zh-CN" to "中文 (简体)",
        "zh-TW" to "中文 (繁體)",
        "zh-HK" to "中文 (香港)",
        "ja" to "日本語",
        "ko" to "한국어",
    )

val CountryCodeToName =
    mapOf(
        "DZ" to "Algeria",
        "AR" to "Argentina",
        "AU" to "Australia",
        "AT" to "Austria",
        "AZ" to "Azerbaijan",
        "BH" to "Bahrain",
        "BD" to "Bangladesh",
        "BY" to "Belarus",
        "BE" to "Belgium",
        "BO" to "Bolivia",
        "BA" to "Bosnia and Herzegovina",
        "BR" to "Brazil",
        "BG" to "Bulgaria",
        "KH" to "Cambodia",
        "CA" to "Canada",
        "CL" to "Chile",
        "HK" to "Hong Kong",
        "CO" to "Colombia",
        "CR" to "Costa Rica",
        "HR" to "Croatia",
        "CY" to "Cyprus",
        "CZ" to "Czech Republic",
        "DK" to "Denmark",
        "DO" to "Dominican Republic",
        "EC" to "Ecuador",
        "EG" to "Egypt",
        "SV" to "El Salvador",
        "EE" to "Estonia",
        "FI" to "Finland",
        "FR" to "France",
        "GE" to "Georgia",
        "DE" to "Germany",
        "GH" to "Ghana",
        "GR" to "Greece",
        "GT" to "Guatemala",
        "HN" to "Honduras",
        "HU" to "Hungary",
        "IS" to "Iceland",
        "IN" to "India",
        "ID" to "Indonesia",
        "IQ" to "Iraq",
        "IE" to "Ireland",
        "IL" to "Israel",
        "IT" to "Italy",
        "JM" to "Jamaica",
        "JP" to "Japan",
        "JO" to "Jordan",
        "KZ" to "Kazakhstan",
        "KE" to "Kenya",
        "KR" to "South Korea",
        "KW" to "Kuwait",
        "LA" to "Lao",
        "LV" to "Latvia",
        "LB" to "Lebanon",
        "LY" to "Libya",
        "LI" to "Liechtenstein",
        "LT" to "Lithuania",
        "LU" to "Luxembourg",
        "MK" to "Macedonia",
        "MY" to "Malaysia",
        "MT" to "Malta",
        "MX" to "Mexico",
        "ME" to "Montenegro",
        "MA" to "Morocco",
        "NP" to "Nepal",
        "NL" to "Netherlands",
        "NZ" to "New Zealand",
        "NI" to "Nicaragua",
        "NG" to "Nigeria",
        "NO" to "Norway",
        "OM" to "Oman",
        "PK" to "Pakistan",
        "PA" to "Panama",
        "PG" to "Papua New Guinea",
        "PY" to "Paraguay",
        "PE" to "Peru",
        "PH" to "Philippines",
        "PL" to "Poland",
        "PT" to "Portugal",
        "PR" to "Puerto Rico",
        "QA" to "Qatar",
        "RO" to "Romania",
        "RU" to "Russian Federation",
        "SA" to "Saudi Arabia",
        "SN" to "Senegal",
        "RS" to "Serbia",
        "SG" to "Singapore",
        "SK" to "Slovakia",
        "SI" to "Slovenia",
        "ZA" to "South Africa",
        "ES" to "Spain",
        "LK" to "Sri Lanka",
        "SE" to "Sweden",
        "CH" to "Switzerland",
        "TW" to "Taiwan",
        "TZ" to "Tanzania",
        "TH" to "Thailand",
        "TN" to "Tunisia",
        "TR" to "Turkey",
        "UG" to "Uganda",
        "UA" to "Ukraine",
        "AE" to "United Arab Emirates",
        "GB" to "United Kingdom",
        "US" to "United States",
        "UY" to "Uruguay",
        "VE" to "Venezuela (Bolivarian Republic)",
        "VN" to "Vietnam",
        "YE" to "Yemen",
        "ZW" to "Zimbabwe",
    )

val SuggestionRegionSlugToName =
    mapOf(
        "system" to "System Default",
        "us" to "Global (USA)",
        "in" to "India",
        "gb" to "United Kingdom",
        "ca" to "Canada",
        "au" to "Australia",
        "jp" to "Japan",
        "kr" to "South Korea",
        "de" to "Germany",
        "fr" to "France",
        "br" to "Brazil",
        "mx" to "Mexico",
        "ru" to "Russia",
        "it" to "Italy",
        "es" to "Spain",
        "nl" to "Netherlands",
        "se" to "Sweden",
        "no" to "Norway",
        "dk" to "Denmark",
        "fi" to "Finland",
        "pl" to "Poland",
        "tr" to "Turkey",
        "za" to "South Africa",
        "ng" to "Nigeria",
        "id" to "Indonesia",
        "my" to "Malaysia",
        "ph" to "Philippines",
        "th" to "Thailand",
        "vn" to "Vietnam",
        "tw" to "Taiwan",
        "hk" to "Hong Kong",
        "sg" to "Singapore",
        "ar" to "Argentina",
        "co" to "Colombia",
        "cl" to "Chile",
        "pe" to "Peru",
        "eg" to "Egypt",
        "sa" to "Saudi Arabia",
        "ae" to "United Arab Emirates",
        "il" to "Israel"
    )

// EQ screen real-time FFT level meter (Android Visualizer — observation-only, no DSP). Default ON on capable
// devices; default OFF under High-Performance Mode or on LOW-tier hardware. User can override either way.
val EqFftMeterEnabledKey = booleanPreferencesKey("eq_fft_meter_enabled")

// High-Performance Mode: master toggle for low-end devices (Android car head units, cheap tablets, <=4GB RAM,
// Android TV). When ON, all heavy VISUALS + video decode + memory are cut (canvas/visualizer/artist-video off,
// video decode capped to 1280p, next-song preload off, video mode disabled, smaller buffers/image cache) while
// AUDIO fidelity (EQ/Safe Volume/normalization) is untouched. Auto-enabled on first launch on LOW-tier / TV / car.
val HighPerformanceModeKey = booleanPreferencesKey("high_performance_mode")
// One-tap "Improve low quality": declipper + HF regeneration for low-bitrate/distorted tracks. Off by default.
val AudioEnhanceEnabledKey = booleanPreferencesKey("audio_enhance_low_quality")
val PreloadNextSongEnabledKey = booleanPreferencesKey("preload_next_song_enabled")
val PreloadNextSongLimitKey = intPreferencesKey("preload_next_song_limit")
val PreloadLyricsEnabledKey = booleanPreferencesKey("preload_lyrics_enabled")

// ── JR DSP effects (desktop parity) ──
// Loudness makeup + true-peak limiting moved to TruePeakLimiterAudioProcessor (always on, no key).
// Aura signature: subtle body+air house curve, ON by default (toggle in Sound settings).
val AuraSignatureToneEnabledKey = booleanPreferencesKey("aura_signature_tone_enabled")
val JrLoudnessEnabledKey = booleanPreferencesKey("jr_dsp_loudness_enabled")
val JrHrtfEnabledKey = booleanPreferencesKey("jr_dsp_hrtf_enabled")
val JrBassEnhanceEnabledKey = booleanPreferencesKey("jr_dsp_bass_enhance_enabled")
val JrBassEnhanceAmountKey = floatPreferencesKey("jr_dsp_bass_enhance_amount")
val JrExciterEnabledKey = booleanPreferencesKey("jr_dsp_exciter_enabled")
val JrExciterAmountKey = floatPreferencesKey("jr_dsp_exciter_amount")
val JrMbCompEnabledKey = booleanPreferencesKey("jr_dsp_mb_comp_enabled")
val JrStereoWidthEnabledKey = booleanPreferencesKey("jr_dsp_stereo_width_enabled")
val JrStereoWidthKey = floatPreferencesKey("jr_dsp_stereo_width")
val JrDialogueEnabledKey = booleanPreferencesKey("jr_dsp_dialogue_enabled")
val JrDialogueAmountKey = floatPreferencesKey("jr_dsp_dialogue_amount")

// Superpowered binaural / stereo-width stage (opt-in, default OFF). Independent of the EQ profile.
val SpatialAudioEnabledKey = booleanPreferencesKey("spatial_audio_enabled")
val SpatialAudioProfileKey = stringPreferencesKey("spatial_audio_profile")

// ── SponsorBlock ──
// Opt-in (default OFF). When ON, non-music segments (sponsor / self-promo / interaction reminders and the
// "non-music" parts of music videos) are auto-skipped during playback via the community SponsorBlock API.
// Deliberately does NOT skip intro/outro/preview so real song intros/outros are never cut.
val SponsorBlockEnabledKey = booleanPreferencesKey("sponsorblock_enabled")

// ── Terms & Conditions ──
// Explicit user acceptance of the in-app legal terms (legal/TermsInfo.TERMS_VERSION).
// NEVER seed these keys from App.kt migrations: acceptance must be a real user action
// (an auto-seeded acceptance is legally worthless and contradicts clause 1 of the terms).
// Int compared against the TERMS_VERSION constant, so bumping the constant re-shows the
// blocking acceptance screen automatically (no fresh key needed — that rule is for forced
// boolean seed migrations).
val TermsAcceptedVersionKey = intPreferencesKey("termsAcceptedVersion")
// Epoch millis of the moment the user tapped "Aceptar y continuar" (audit trail).
val TermsAcceptedAtKey = longPreferencesKey("termsAcceptedAtMillis")
// App versionCode the user was running when they accepted (audit trail).
val TermsAcceptedAppVersionKey = intPreferencesKey("termsAcceptedAppVersionCode")

// ── Playlist migration (Tidal / Deezer / archivo → YouTube Music) ──
// Tidal Open API client id for the PKCE OAuth flow (no client secret — the project is GPL, an embedded
// secret would be a public secret). User-supplied from developer.tidal.com; blank = Tidal import not
// configured. Only the ID lives here — access/refresh TOKENS are credentials and live in
// EncryptedSharedPreferences (migration/TidalTokenStore, file "tidal_tokens"), never in DataStore.
val TidalClientIdKey = stringPreferencesKey("tidal_client_id")

// ── Qobuz hi-res (owner's OWN subscription) ──
// When ON and the user has linked their Qobuz account (token in EncryptedSharedPreferences, file
// "qobuz_session"), the LOSSLESS resolve path streams a signed FLAC from the user's real Qobuz
// subscription instead of the third-party proxy. DEFAULT OFF: nothing changes for users without Qobuz.
// Auto-flipped ON once, right after a successful link, so the owner doesn't have to hunt for the toggle;
// user choices win afterwards. Gated additionally on being linked (QobuzHiRes.isActive), so a stray ON
// value with no token is inert.
val UseOwnQobuzHiResKey = booleanPreferencesKey("use_own_qobuz_hires")

// ── "Interfaz nueva" (new UI beta, ui/newui) ──
// Single master switch for the redesigned presentation layer. DEFAULT FALSE: the app boots into the
// classic UI exactly as before, so an unsuspecting user never sees the beta.
//
// Contract (do not weaken it):
//  · PRESENTATION ONLY. The new screens reuse every existing ViewModel/repository/action lambda; no
//    business logic is duplicated, so a bug fixed in one is fixed in both.
//  · PER-SCREEN FALLBACK. Every gated screen goes through a *Host composable in ui/newui/NewUiHosts.kt.
//    If a screen has no new implementation yet it renders the classic one, flag or not.
//  · TOGGLING IS INERT. Flipping this key must not touch the database, playback, the queue, or any other
//    preference. Turning it OFF returns the app to byte-identical classic behaviour.
// Launch default is ON (fresh installs / unset key). Existing explicit `false` is never overwritten.
val NewUiEnabledKey = booleanPreferencesKey("new_ui_enabled")
val MiniPlayerGlowDefaultV1AppliedKey = booleanPreferencesKey("miniplayer_glow_default_v1_applied")
// Classic mini-player: Liquid Glass on glass-eligible devices, "Seguir el tema" (DEFAULT) otherwise.
// FRESH key; runs after glow/blur/undo so upgrades land on the owner order without clobbering
// LIVE_MESH / other deliberate picks. New UI pill remaps LIQUID_GLASS → GLOW_ANIMATED (AuraShell).
val MiniPlayerClassicGlassDefaultV1AppliedKey =
    booleanPreferencesKey("miniplayer_classic_glass_default_v1_applied")
val NewUiLaunchDefaultV1AppliedKey = booleanPreferencesKey("new_ui_launch_default_v1_applied")
