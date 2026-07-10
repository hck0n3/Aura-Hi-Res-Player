

package iad1tya.echo.music

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.datastore.preferences.core.edit
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.disk.directory
import coil3.memory.MemoryCache
import coil3.request.CachePolicy
import coil3.request.allowHardware
import coil3.request.allowRgb565
import coil3.request.crossfade
import com.music.innertube.YouTube
import com.music.innertube.models.IpVersion
import com.music.innertube.models.YouTubeLocale
import com.music.kugou.KuGou
import iad1tya.echo.music.constants.*
import iad1tya.echo.music.di.ApplicationScope
import iad1tya.echo.music.extensions.toEnum
import iad1tya.echo.music.extensions.toInetSocketAddress
import iad1tya.echo.music.utils.CrashHandler
import iad1tya.echo.music.utils.cipher.CipherDeobfuscator
import iad1tya.echo.music.utils.dataStore
import iad1tya.echo.music.utils.localeAwareContext
import iad1tya.echo.music.utils.reportException
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import timber.log.Timber
import java.net.Authenticator
import java.net.PasswordAuthentication
import java.net.Proxy
import java.util.Locale
import javax.inject.Inject

@HiltAndroidApp
class App : Application(), SingletonImageLoader.Factory {

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(localeAwareContext(base))
    }

    @Inject
    @ApplicationScope
    lateinit var applicationScope: CoroutineScope

    // Same @Singleton the MusicService DSP observes — used once at startup to seed the default EQ tuning.
    // dagger.Lazy so App member-injection does NOT eagerly construct it on the MAIN thread (its init reads +
    // JSON-parses the saved profiles). We warm it off-main in onCreate instead; the DSP observer collects a
    // StateFlow, so a slightly-later first emission is fine.
    @Inject
    lateinit var eqProfileRepository: dagger.Lazy<iad1tya.echo.music.eq.data.EQProfileRepository>

    // dagger.Lazy so App member-injection does NOT construct DownloadUtil (and, transitively, the two
    // @PlayerCache/@DownloadCache SimpleCache singletons) on the MAIN thread. Each SimpleCache constructor
    // synchronously scans its whole cache dir — the ~1-minute cold-start freeze. We force construction OFF the
    // main thread in onCreate, so the Activity's later @Inject DownloadUtil gets the ready singleton cheaply.
    @Inject
    lateinit var downloadUtilLazy: dagger.Lazy<iad1tya.echo.music.playback.DownloadUtil>

    override fun onCreate() {
        super.onCreate()

        // Seed the process-wide image-cache-size mirror synchronously from a cheap SharedPreferences copy
        // (NOT DataStore) so newImageLoader() — which Coil invokes lazily on the main thread at first image
        // load — can read it without a blocking DataStore read. The authoritative DataStore value re-seeds
        // this (and refreshes the SharedPreferences copy) once initializeSettings() runs; see below.
        seedImageCacheSizeMirror(this)

        com.music.jiosaavn.DeviceRouter.init(this)
        Timber.d("Device ID: ${com.music.jiosaavn.DeviceRouter.getDeviceId()} | Assigned JioSaavn Server: ${com.music.jiosaavn.DeviceRouter.getCurrentServer()}")

        // NOTE: do NOT add a destructive deleteDatabase("song.db") on startup. The Room schema has
        // complete migration coverage (see MusicDatabase), so wiping the DB only erases the user's
        // history/stats/playlists/downloads. The old one-time `cleared_db_v5` wipe was removed.


        CrashHandler.install(this)

        // Deterministic safety net for the media3 ForegroundServiceStartNotAllowedException crash (async
        // notification-bitmap startForeground on the main looper, which the MediaSessionService.Listener
        // doesn't catch). Installed right after CrashHandler so it's the inner guard for that one exception
        // family, while everything else still reaches CrashHandler.
        iad1tya.echo.music.utils.MainThreadCrashGuard.install()


        CipherDeobfuscator.initialize(this)

        if (iad1tya.echo.music.BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
        iad1tya.echo.music.utils.AppLogger.plant(this)


        applicationScope.launch {
            initializeSettings()
            observeSettingsChanges()
        }

        // Non-critical background scheduling (weekly Release Radar + weekly app-update check). On genuinely weak
        // (LOW-tier) devices, DEFER it a few seconds so it doesn't compete with first-frame work on cold start —
        // it still runs, just after the UI is up. On CAPABLE devices it runs immediately as before. The tier check
        // is the cached, cheap CPU/RAM signal (NO DataStore/disk read on the main-thread cold-start path).
        if (iad1tya.echo.music.utils.DeviceCapabilities.tier(this) == iad1tya.echo.music.utils.DeviceTier.LOW) {
            applicationScope.launch(Dispatchers.Default) {
                kotlinx.coroutines.delay(3000)
                scheduleNonCriticalWork()
            }
        } else {
            scheduleNonCriticalWork()
        }

        // Best-effort background refresh of the self-healing player configs (owner-hosted JSON). Idempotent
        // and network-optional: on failure the app keeps its built-in hardcoded configs. Never throws. Lets
        // a YouTube cipher rotation be fixed by publishing one config, with no app update.
        applicationScope.launch(Dispatchers.IO) {
            // Load any previously-fetched configs from disk FIRST (off the main thread now — it reads + parses a
            // JSON cache), so a cached config resolves on the first stream extraction even offline; then refresh.
            iad1tya.echo.music.utils.cipher.RemotePlayerConfig.loadCache(applicationContext)
            iad1tya.echo.music.utils.cipher.RemotePlayerConfig.refresh(applicationContext)
        }

        // Cold-start freeze fix: force the two @PlayerCache/@DownloadCache SimpleCache singletons (via DownloadUtil,
        // which depends on both) OFF the main thread here, BEFORE the Activity's @Inject DownloadUtil forces them
        // during its onCreate. Each SimpleCache constructor synchronously scans its whole cache dir; done here the
        // scan runs on IO and the Activity gets the ready @Singleton cheaply. Warm the EQ repo (reads+parses saved
        // profiles) off-main too. Best-effort; never throws.
        applicationScope.launch(Dispatchers.IO) {
            runCatching { downloadUtilLazy.get() }
            runCatching { eqProfileRepository.get() }
        }

        // Preload the native Superpowered bridge off the main thread so the later CustomEqualizerAudioProcessor
        // init{} System.loadLibrary is a cheap no-op instead of a main-thread dlopen during the first player build.
        applicationScope.launch(Dispatchers.Default) {
            runCatching { System.loadLibrary("superpowered-bridge") }
        }

        // Keep the song-cache-size mirror (read by AppModule.providePlayerCache without a blocking DataStore read)
        // in sync with the authoritative DataStore value: the first emission seeds it, later changes refresh the
        // SharedPreferences copy for the next process start. Default -1 == unlimited, matching providePlayerCache.
        applicationScope.launch(Dispatchers.IO) {
            dataStore.data
                .map { it[MaxSongCacheSizeKey] ?: DEFAULT_SONG_CACHE_SIZE_MB }
                .distinctUntilChanged()
                // Best-effort, matching the sibling startup coroutines: a DataStore read error must not crash
                // the process — just report it and stop mirroring for this session.
                .catch { reportException(it) }
                .collect { updateSongCacheSizeMirror(this@App, it) }
        }
    }

    /**
     * Schedules the non-critical periodic work: the weekly Release Radar check + one-time seed, and the weekly
     * app-update check. Idempotent (unique periodic work, UPDATE policy; the seed is once-per-install), so it is
     * safe to call every start. Extracted so perf-mode can defer it off the cold-start critical path (see onCreate).
     */
    private fun scheduleNonCriticalWork() {
        // Schedule the weekly Release Radar check (aligned to the next Friday morning).
        // Safe to call every start: it uses a unique periodic work item with UPDATE policy.
        runCatching { iad1tya.echo.music.releaseradar.ReleaseRadarWorker.schedule(this) }
            .onFailure { Timber.e(it, "Failed to schedule Release Radar worker") }
        // Seed the radar ONCE per install so a fresh install isn't empty before the first Friday drop.
        // NOT on every launch: the real Release Radar only refreshes weekly (the periodic worker above).
        runCatching { iad1tya.echo.music.releaseradar.ReleaseRadarWorker.seedOnceIfNeeded(this) }
            .onFailure { Timber.e(it, "Failed to seed Release Radar") }

        // Schedule the weekly app-update check (notifies once per new version when one is found).
        runCatching { iad1tya.echo.music.echomusic.updater.UpdateCheckWorker.schedule(this) }
            .onFailure { Timber.e(it, "Failed to schedule update-check worker") }
    }

    /**
     * Best-effort memory-pressure relief for weak boxes (1-2 GB Android-TV / car units): under memory pressure,
     * drop Coil's in-RAM image cache so the process survives instead of being killed. GATED to perf-mode /
     * LOW-tier devices — on CAPABLE devices this is a no-op (nothing changes: their warm image cache is kept).
     * The LOW-tier check is cached & cheap and short-circuits before the perf-mode flag read. Never throws.
     */
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level == android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW ||
            level == android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL ||
            level == android.content.ComponentCallbacks2.TRIM_MEMORY_BACKGROUND
        ) {
            // Cached, cheap tier check only — no DataStore read on the main thread under memory pressure.
            val constrained =
                iad1tya.echo.music.utils.DeviceCapabilities.tier(this) == iad1tya.echo.music.utils.DeviceTier.LOW
            if (constrained) {
                runCatching { SingletonImageLoader.get(this).memoryCache?.clear() }
            }
        }
    }

    private suspend fun initializeSettings() {
        reseedAfterRestoreIfNeeded()
        val settings = dataStore.data.first()
        // Re-seed the image-cache-size mirror from the authoritative committed value (same 2048 fallback as
        // newImageLoader) and refresh the SharedPreferences copy so the next cold start seeds correctly. This
        // reuses the settings read above — no extra IO. StorageSettings updates the mirror synchronously on a
        // size change right before it resets the ImageLoader (see StorageSettings.kt).
        // If the cheap SharedPreferences seed used at cold start differed from the authoritative committed value
        // (first launch after update, or after a restore), the ImageLoader may have already been built with the
        // stale size — rebuild it once so the user's chosen disk-cache size (incl. 0 = disabled) takes effect this
        // session, not only the next one.
        val authoritativeImageCacheSize = settings[MaxImageCacheSizeKey] ?: DEFAULT_IMAGE_CACHE_SIZE_MB
        val imageCacheMirrorWasStale = imageCacheSizeMb() != authoritativeImageCacheSize
        updateImageCacheSizeMirror(this@App, authoritativeImageCacheSize)
        if (imageCacheMirrorWasStale) {
            runCatching { SingletonImageLoader.reset() }
        }
        // Seed the song-cache-size mirror synchronously from the authoritative committed value (reuses the
        // settings read above — no extra IO). On first launch after this update / after a device restore the
        // mirror is empty, so without this providePlayerCache would fall back to -1 (unlimited) and ignore the
        // user's configured size limit. Seeding it here (and the missing-key fallback in songCacheSizeMb) makes
        // the SimpleCache honor the configured limit from this session on.
        updateSongCacheSizeMirror(this@App, settings[MaxSongCacheSizeKey] ?: DEFAULT_SONG_CACHE_SIZE_MB)
        // ── One-time default seeds & migrations ──────────────────────────────────────────────────────────────
        // Previously ~13 helpers each ran their own dataStore.edit{} (a startup write-storm that serialized behind
        // the DataStore actor and blocked main-thread reads; migratePlaybackDefaults even wrote on EVERY launch).
        // They are coalesced into single transactions here, each gated so an already-migrated device does ZERO
        // writes. Ordering, keys/values, guard flags and the two-phase "mark done only if applied" semantics are
        // preserved exactly (in one transaction the value+flag are atomic, which is strictly stronger). Cross-batch
        // reads that a prior migration wrote (e.g. HighPerformanceModeKey chained through perf-mode migrations) all
        // read the SHARED MutablePreferences `p`, matching the old commit-then-read chaining. The two migrations
        // with non-DataStore side effects stay SEPARATE: migrateAudioDefaultsV2 (EQ repo + echo_eq_prefs, two-phase
        // on seed success) keeps its position BETWEEN the batches so Safe Volume's forced-ON still lands AFTER it,
        // and migrateLegacyIcon (PackageManager) runs last.

        // Batch A — every pure-DataStore seed/migration that runs BEFORE migrateAudioDefaultsV2, in original order.
        val seedStored = settings[iad1tya.echo.music.constants.SeedVersionKey]
        val seedLegacyApplied = settings[iad1tya.echo.music.constants.JrDefaultsAppliedKey] == true
        val batchAPending =
            iad1tya.echo.music.viewmodels.shouldReseed(seedStored, seedLegacyApplied, CURRENT_SEED_VERSION) ||
            seedStored == null ||
            settings[iad1tya.echo.music.constants.CanvasDefaultOnAppliedKey] != true ||
            settings[iad1tya.echo.music.constants.HighPerfModeSeedAppliedKey] != true ||
            settings[iad1tya.echo.music.constants.PerfModeReseedV2AppliedKey] != true ||
            settings[iad1tya.echo.music.constants.PerfModeCapabilityV3AppliedKey] != true ||
            settings[iad1tya.echo.music.constants.MiniPlayerDefaultBgAppliedKey] != true ||
            settings[iad1tya.echo.music.constants.ThemeSystemDefaultAppliedKey] != true ||
            settings[iad1tya.echo.music.constants.ThemeSystemOnlyV2AppliedKey] != true ||
            settings[iad1tya.echo.music.constants.PlaybackDefaultsV2AppliedKey] != true ||
            settings[iad1tya.echo.music.constants.PlaybackDefaultsV3AppliedKey] != true ||
            settings[iad1tya.echo.music.constants.PlaybackDefaultsV4AppliedKey] != true ||
            settings[iad1tya.echo.music.constants.PlaybackDefaultsV5AppliedKey] != true ||
            settings[iad1tya.echo.music.constants.CrossfadeDefault9AppliedKey] != true ||
            settings[iad1tya.echo.music.constants.LyricsBlurDefaultOnV1AppliedKey] != true ||
            settings[iad1tya.echo.music.constants.AddToPlaylistLastUpdatedDefaultV1AppliedKey] != true
        if (batchAPending) {
            runCatching {
                dataStore.edit { p ->
                    applySeedDefaults(p, settings)
                    applyCanvasDefaultOn(p, settings)
                    applyHighPerformanceModeSeed(p, settings)
                    // AFTER HighPerformanceModeSeed: undoes the over-aggressive force-ON (TV + ~4 GB phones).
                    applyPerfModeReseedV2(p, settings)
                    // AFTER PerfModeReseedV2: re-enables perf-mode on genuinely WEAK TV/car boxes it turned OFF.
                    applyPerfModeCapabilityV3(p, settings)
                    applyMiniPlayerDefaultBg(p, settings)
                    applyThemeSystemDefault(p, settings)
                    applyThemeSystemOnlyV2(p, settings)
                    applyPlaybackDefaults(p, settings)
                    applyLyricsBlurDefaultOnV1(p, settings)
                    applyAddToPlaylistLastUpdatedDefaultV1(p, settings)
                }
            }.onFailure { reportException(it) }
        }

        // SEPARATE (EQ repo + echo_eq_prefs side effects, own two-phase commit on seed success).
        migrateAudioDefaultsV2(settings)

        // Batch B — pure-DataStore migrations that MUST run AFTER migrateAudioDefaultsV2 (Safe Volume forced ON
        // wins over it). Both are two-phase in the original; in one transaction value+flag are written atomically,
        // so the flag is still never set without the value being applied.
        val batchBPending =
            settings[iad1tya.echo.music.constants.SafeVolumeDefaultOnAppliedKey] != true ||
            settings[iad1tya.echo.music.constants.InfinitePlaybackForcedOnKey] != true
        if (batchBPending) {
            runCatching {
                dataStore.edit { p ->
                    applySafeVolumeDefaultOn(p, settings)
                    applyInfinitePlaybackOn(p, settings)
                }
            }.onFailure { reportException(it) }
        }

        // SEPARATE (PackageManager component toggling; only acts when the removed Legacy Icon was enabled).
        migrateLegacyIcon(settings)
        val locale = Locale.getDefault()
        val languageTag = locale.language

        YouTube.locale = YouTubeLocale(
            // Forcing "es" blanks out locale.country, so derive the region from the real device locale
            // (systemRegionCode) — otherwise everyone fell back to "US" for explore/charts.
            gl = settings[ContentCountryKey]?.takeIf { it != SYSTEM_DEFAULT }
                ?: locale.country.takeIf { it in CountryCodeToName }
                ?: iad1tya.echo.music.utils.systemRegionCode().uppercase().takeIf { it in CountryCodeToName }
                ?: "US",
            hl = settings[ContentLanguageKey]?.takeIf { it != SYSTEM_DEFAULT }
                ?: locale.language.takeIf { it in LanguageCodeToName }
                ?: languageTag.takeIf { it in LanguageCodeToName }
                ?: "en"
        )

        if (languageTag == "zh-TW") {
            KuGou.useTraditionalChinese = true
        }

        if (settings[ProxyEnabledKey] == true) {
            val username = settings[ProxyUsernameKey].orEmpty()
            val password = settings[ProxyPasswordKey].orEmpty()
            val type = settings[ProxyTypeKey].toEnum(defaultValue = Proxy.Type.HTTP)

            if (username.isNotEmpty() || password.isNotEmpty()) {
                if (type == Proxy.Type.HTTP) {
                    YouTube.proxyAuth = Credentials.basic(username, password)
                } else {
                    Authenticator.setDefault(object : Authenticator() {
                        override fun getPasswordAuthentication(): PasswordAuthentication =
                            PasswordAuthentication(username, password.toCharArray())
                    })
                }
            }
            try {
                settings[ProxyUrlKey]?.let {
                    YouTube.proxy = Proxy(type, it.toInetSocketAddress())
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@App, getString(R.string.failed_to_parse_proxy), Toast.LENGTH_SHORT).show()
                }
                reportException(e)
            }
        }

        // Default OFF: discovery browses (home/explore/new releases) load via the reliable guest
        // context. With it ON + a signed-in account, YouTube Music often returns an empty/limited
        // catalog for those feeds, so "Sugerencias"/"Álbum" looked broken only while logged in.
        // Account-specific calls (library, liked, playlists, artists) force setLogin=true regardless,
        // so they keep working. Users can still turn it on in Settings.
        YouTube.useLoginForBrowse = settings[UseLoginForBrowse] ?: false
        YouTube.ipVersion = settings[IpVersionKey]?.toEnum(defaultValue = IpVersion.AUTO) ?: IpVersion.AUTO

        val channel = NotificationChannel(
            "updates",
            getString(R.string.update_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = getString(R.string.update_channel_desc)
        }
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(channel)
    }

    /**
     * After a backup restore, the restored settings file carries the previous profile's one-time
     * init guards (e.g. [iad1tya.echo.music.constants.JrDefaultsAppliedKey]); left intact they
     * suppress this version's seeded defaults, so "new features don't appear" until the backup is
     * cleared. Signalled by a marker file from [iad1tya.echo.music.viewmodels.BackupRestoreViewModel],
     * this clears those guards once so the seeds below re-run on the next launch.
     */
    private suspend fun reseedAfterRestoreIfNeeded() {
        val flag = java.io.File(
            filesDir,
            iad1tya.echo.music.viewmodels.BackupRestoreViewModel.POST_RESTORE_REINIT_FLAG,
        )
        if (!flag.exists()) return
        // A restore happened: drop the seed version so this app version's feature defaults re-apply
        // on this launch (otherwise the restored old profile suppresses them = "new features missing").
        Timber.tag("RESTORE").i("Post-restore: re-seed + fresh visitorData + disable restored proxy")
        runCatching {
            dataStore.edit { p ->
                p[iad1tya.echo.music.constants.SeedVersionKey] = 0
                // The restored backup carries a stale YouTube visitor session token; using it makes
                // every browse/search/suggestions/album call fail ("as if offline"). Drop it so a fresh
                // visitorData is fetched on this launch.
                p.remove(iad1tya.echo.music.constants.VisitorDataKey)
                // NOTE: keep InnerTubeCookie + DataSyncId + account keys — they ARE the restored login.
                // (Only visitorData is the stale browse token that must be refreshed.)
                // A restored proxy (enabled, pointing at a now-dead address) routes ALL YouTube traffic
                // into a black hole → search/suggestions/albums all fail after a restore. Disable any
                // restored proxy so online works; the user can re-enable it in Settings if they use one.
                p[iad1tya.echo.music.constants.ProxyEnabledKey] = false
            }
        }
        flag.delete()
    }

    /**
     * Seeds Aura Hi-Res Player's preferred defaults (player look, Spanish language) when the stored
     * seed version is older than [CURRENT_SEED_VERSION]. Version-gated (not per-feature booleans) so
     * a restored backup — which carries an older seed version — automatically re-applies this
     * version's defaults. Pre-SeedVersion installs (legacy boolean guard set) are treated as seed v1
     * and simply recorded, so existing users' manual changes are NOT clobbered on upgrade.
     */
    private fun applySeedDefaults(
        p: androidx.datastore.preferences.core.MutablePreferences,
        settings: androidx.datastore.preferences.core.Preferences,
    ) {
        val stored = settings[iad1tya.echo.music.constants.SeedVersionKey]
        val legacyApplied = settings[iad1tya.echo.music.constants.JrDefaultsAppliedKey] == true
        if (!iad1tya.echo.music.viewmodels.shouldReseed(stored, legacyApplied, CURRENT_SEED_VERSION)) {
            // Record the migration of a pre-SeedVersion install so we don't recompute every launch.
            if (stored == null) {
                p[iad1tya.echo.music.constants.SeedVersionKey] = CURRENT_SEED_VERSION
            }
            return
        }
        // E2: a LOW-capability device (by RAM/cores/perf-class, not brand) gets the heavy visuals OFF by
        // default on a FRESH install so it runs smooth/cool out of the box. Only affects defaults — the user
        // can still enable everything, and existing installs (already past this seed) are untouched.
        val lowEndDevice =
            iad1tya.echo.music.utils.DeviceCapabilities.tier(this) == iad1tya.echo.music.utils.DeviceTier.LOW
        run {
            // "Inspirado en Apple Music" player. The toggle is ON when UseNewPlayerDesign == false AND
            // the player background is APPLE_MUSIC — seeding LIVE_MESH before made the switch *look* on
            // but not actually apply (it only kicked in after a manual off→on). Seed APPLE_MUSIC so it
            // works from first launch.
            p[iad1tya.echo.music.constants.PlayerBackgroundStyleKey] =
                iad1tya.echo.music.constants.PlayerBackgroundStyle.APPLE_MUSIC.name
            // Mini-player stays DEFAULT (clean theme bar): a dynamic mini background forces white text,
            // which is illegible in light mode. DEFAULT keeps the readable gray (onSurface) text.
            p[iad1tya.echo.music.constants.MiniPlayerBackgroundStyleKey] =
                iad1tya.echo.music.constants.PlayerBackgroundStyle.DEFAULT.name
            p[iad1tya.echo.music.constants.UseNewPlayerDesignKey] = false
            p[iad1tya.echo.music.constants.HidePlayerSliderKey] = true

            // Lyrics: Apple Music v2 animation + glow + blur on by default.
            p[iad1tya.echo.music.constants.LyricsAnimationStyleKey] =
                iad1tya.echo.music.constants.LyricsAnimationStyle.APPLE_V2.name
            p[iad1tya.echo.music.constants.LyricsGlowEffectKey] = true
            p[iad1tya.echo.music.constants.AppleMusicLyricsBlurKey] = true

            // Visuals: spectrum visualizer, artist video + artist background video, and the cover "canvas"
            // animations (player + album) — ON by default on MID/HIGH; OFF by default on LOW-tier devices (E2)
            // so a fresh install on a weak phone is smooth and cool. Seeded ONLY when the key is still unset, so
            // a later seed-version bump never overrides a choice the user has made (and existing installs keep
            // their current values). The user can always toggle them in Settings.
            if (p[iad1tya.echo.music.constants.SpectrumVisualizerEnabledKey] == null) {
                p[iad1tya.echo.music.constants.SpectrumVisualizerEnabledKey] = !lowEndDevice
            }
            if (p[iad1tya.echo.music.constants.CanvasThumbnailAnimationKey] == null) {
                p[iad1tya.echo.music.constants.CanvasThumbnailAnimationKey] = !lowEndDevice
            }
            if (p[iad1tya.echo.music.constants.AlbumCanvasEnabledKey] == null) {
                p[iad1tya.echo.music.constants.AlbumCanvasEnabledKey] = !lowEndDevice
            }
            if (p[iad1tya.echo.music.constants.ShowArtistVideoKey] == null) {
                p[iad1tya.echo.music.constants.ShowArtistVideoKey] = !lowEndDevice
            }
            if (p[iad1tya.echo.music.constants.ShowArtistBackgroundVideoKey] == null) {
                p[iad1tya.echo.music.constants.ShowArtistBackgroundVideoKey] = !lowEndDevice
            }

            // High-Performance Mode: auto-ON ONLY on genuinely LOW-tier phones. NOT on Android TV: a television
            // is the premium large-screen experience (1080p video, home carousels, high-res covers) and perf-mode
            // would disable video mode + strip carousels. A genuinely weak TV box can still enable it manually.
            // Seeded only when unset, so a manual choice is never clobbered. When on it routes every
            // visual/decode/memory gate through the LOW path and disables video mode (audio only).
            if (p[iad1tya.echo.music.constants.HighPerformanceModeKey] == null) {
                p[iad1tya.echo.music.constants.HighPerformanceModeKey] = lowEndDevice
            }

            // Hide video songs is OFF by default (show video music too); only YouTube Shorts are
            // hidden by default.
            p[iad1tya.echo.music.constants.HideVideoSongsKey] = false
            p[iad1tya.echo.music.constants.HideYoutubeShortsKey] = true

            // Playback defaults (user request): smooth transition (crossfade) ON at 9s with the EQUAL-POWER
            // curve (1 = constant loudness, no mid-blend volume dip), skip silence ON and instantly ON.
            p[iad1tya.echo.music.constants.CrossfadeEnabledKey] = true
            p[iad1tya.echo.music.constants.CrossfadeDurationKey] = 9f
            p[iad1tya.echo.music.constants.CrossfadeCurveKey] = 1
            p[iad1tya.echo.music.constants.SkipSilenceKey] = true
            p[iad1tya.echo.music.constants.SkipSilenceInstantKey] = true

            // Appearance follows the SYSTEM theme by default (user request): light/dark AUTO and the
            // system dynamic (Material You) colour theme ON — so a fresh install has ONLY the automatic
            // system theme selected, not a manual colour. The teal accent below is just the fallback
            // used if the user later turns the dynamic theme off.
            p[iad1tya.echo.music.constants.DarkModeKey] =
                iad1tya.echo.music.ui.screens.settings.DarkMode.AUTO.name
            // pureBlack MUST be false here: with darkMode=AUTO, leaving pureBlack=true lit up BOTH the
            // "Follow system" and "AMOLED" cards at once (they're independent in the UI). "System theme
            // only" means AUTO + no pure-black.
            p[iad1tya.echo.music.constants.PureBlackKey] = false
            p[iad1tya.echo.music.constants.DynamicThemeKey] = true
            // Fallback accent (only applies when dynamic theme is OFF): matches the "AURA HI-RES" tones.
            p[iad1tya.echo.music.constants.SelectedThemeColorKey] = 0xFF36C5E0.toInt()

            // Smaller library grid thumbnails (playlists/albums/artists) so the grid looks tidier.
            p[iad1tya.echo.music.constants.GridItemsSizeKey] =
                iad1tya.echo.music.constants.GridItemSize.SMALL.name

            // Spanish default, only if the user hasn't explicitly chosen a language.
            val current = p[iad1tya.echo.music.constants.AppLanguageKey]
            if (current == null || current == SYSTEM_DEFAULT) {
                p[iad1tya.echo.music.constants.AppLanguageKey] = "es"
            }
            p[iad1tya.echo.music.constants.SeedVersionKey] = CURRENT_SEED_VERSION
            // Keep legacy flags consistent for any code still reading them.
            p[iad1tya.echo.music.constants.JrDefaultsAppliedKey] = true
            p[iad1tya.echo.music.constants.SpanishDefaultAppliedKey] = true
        }
    }

    /**
     * Flip the cover "canvas" animations (player + album) OFF for existing installs too, once — they
     * were previously seeded ON. Gated by its own flag so it never disturbs anything else and never
     * repeats; users who want them can turn them back on (and the flag keeps that choice).
     */
    private fun applyThemeSystemDefault(
        p: androidx.datastore.preferences.core.MutablePreferences,
        settings: androidx.datastore.preferences.core.Preferences,
    ) {
        // User request: appearance should start following the SYSTEM theme. Apply once (even on installs that
        // had the old forced-dark default), then remember so the user's later choice is respected.
        if (settings[iad1tya.echo.music.constants.ThemeSystemDefaultAppliedKey] == true) return
        p[iad1tya.echo.music.constants.DarkModeKey] =
            iad1tya.echo.music.ui.screens.settings.DarkMode.AUTO.name
        p[iad1tya.echo.music.constants.ThemeSystemDefaultAppliedKey] = true
    }

    /**
     * One-time (v2): force the clean "system theme only" state for EVERYONE on this update. The earlier
     * seed/migration could leave darkMode=AUTO together with pureBlack=true, which lit up BOTH the
     * "Follow system" and "AMOLED" cards at once. This resets to AUTO + pureBlack OFF + dynamic ON so the
     * user lands on the new system theme with exactly one selection. Runs once (own flag); afterwards the
     * user's later theme choices are respected.
     */
    private fun applyThemeSystemOnlyV2(
        p: androidx.datastore.preferences.core.MutablePreferences,
        settings: androidx.datastore.preferences.core.Preferences,
    ) {
        if (settings[iad1tya.echo.music.constants.ThemeSystemOnlyV2AppliedKey] == true) return
        p[iad1tya.echo.music.constants.DarkModeKey] =
            iad1tya.echo.music.ui.screens.settings.DarkMode.AUTO.name
        p[iad1tya.echo.music.constants.PureBlackKey] = false
        p[iad1tya.echo.music.constants.DynamicThemeKey] = true
        p[iad1tya.echo.music.constants.ThemeSystemOnlyV2AppliedKey] = true
    }

    /**
     * One-time: apply the requested playback defaults for EVERYONE on this update — smooth transition
     * (crossfade) ON at 9s, skip silence ON, and skip silence instantly ON. Runs once (own flag);
     * afterwards the user's later choices are respected.
     */
    private fun applyPlaybackDefaults(
        p: androidx.datastore.preferences.core.MutablePreferences,
        settings: androidx.datastore.preferences.core.Preferences,
    ) {
        // V2 — initial playback defaults (once).
        if (settings[iad1tya.echo.music.constants.PlaybackDefaultsV2AppliedKey] != true) {
            p[iad1tya.echo.music.constants.CrossfadeEnabledKey] = true
            p[iad1tya.echo.music.constants.SkipSilenceKey] = true
            p[iad1tya.echo.music.constants.SkipSilenceInstantKey] = true
            p[iad1tya.echo.music.constants.PlaybackDefaultsV1AppliedKey] = true
            p[iad1tya.echo.music.constants.PlaybackDefaultsV2AppliedKey] = true
        }
        // V3 — re-apply ONCE for existing users: 12 s + EQUAL-POWER crossfade (the old default was the
        // dip-prone LINEAR curve at 10 s, the "baja y de la nada sube" the user reported).
        if (settings[iad1tya.echo.music.constants.PlaybackDefaultsV3AppliedKey] != true) {
            p[iad1tya.echo.music.constants.CrossfadeDurationKey] = 12f
            p[iad1tya.echo.music.constants.CrossfadeCurveKey] = 1
            p[iad1tya.echo.music.constants.PlaybackDefaultsV3AppliedKey] = true
        }
        // V4 — user asked for 10 s transitions; keep the EQUAL-POWER curve (no mid dip, chosen over
        // strict-linear). Re-apply ONCE for existing users (V3 had set 12 s).
        if (settings[iad1tya.echo.music.constants.PlaybackDefaultsV4AppliedKey] != true) {
            p[iad1tya.echo.music.constants.CrossfadeDurationKey] = 10f
            p[iad1tya.echo.music.constants.CrossfadeCurveKey] = 1
            p[iad1tya.echo.music.constants.PlaybackDefaultsV4AppliedKey] = true
        }
        // V5 — smooth transition ("transición suave", EQUAL-POWER curve) ON. Re-apply ONCE for everyone.
        if (settings[iad1tya.echo.music.constants.PlaybackDefaultsV5AppliedKey] != true) {
            p[iad1tya.echo.music.constants.CrossfadeEnabledKey] = true
            p[iad1tya.echo.music.constants.CrossfadeDurationKey] = 9f
            p[iad1tya.echo.music.constants.CrossfadeCurveKey] = 1
            p[iad1tya.echo.music.constants.PlaybackDefaultsV5AppliedKey] = true
        }
        // V6 — best crossfade default is 9 s (equal-power). One-time, FRESH key so it re-applies even
        // for existing users whose V5 / AudioDefaultsV2 flags already landed the old 13 s value. Only
        // move users still on that previous 13 s DEFAULT; anyone who chose their own duration keeps it.
        // (Reads the pre-migration `settings` snapshot, exactly as before — NOT `p`.)
        if (settings[iad1tya.echo.music.constants.CrossfadeDefault9AppliedKey] != true) {
            if (settings[iad1tya.echo.music.constants.CrossfadeDurationKey] == 13f) {
                p[iad1tya.echo.music.constants.CrossfadeDurationKey] = 9f
            }
            p[iad1tya.echo.music.constants.CrossfadeDefault9AppliedKey] = true
        }
    }

    /**
     * One-time (V2): force the requested AUDIO DEFAULTS for EVERYONE on this update — EQ ON + "Audiophile"
     * preset + preamp 0.0 dB, crossfade 9 s equal-power ("transición suave"), and Safe Volume ON. Gated by
     * a FRESH key ([AudioDefaultsV2AppliedKey]) so it re-applies even for users whose per-feature flags were
     * already set by the brief 0.6.75/0.6.76 builds (a single EqAudiophileDefault boolean could only ever
     * apply ONCE per install, so bumping the version alone did NOT re-apply it — this new-key block fixes that).
     * Afterwards the user's own choices win. The EQ write hits BOTH the DSP source of truth (the injected
     * [eqProfileRepository] @Singleton the MusicService observer collects) AND the EQ screen's own prefs mirror
     * (echo_eq_prefs), matching exactly the profile the EQ ViewModel builds (id "echo_tuning", GRAPHIC bands).
     */
    private suspend fun migrateAudioDefaultsV2(settings: androidx.datastore.preferences.core.Preferences) {
        if (settings[iad1tya.echo.music.constants.AudioDefaultsV2AppliedKey] == true) return
        // Playback prefs (best-effort): crossfade 9 s equal-power ON, Safe Volume ON (default flipped to ON;
        // migrateSafeVolumeDefaultOn also forces it for users who already passed this gated migration).
        runCatching {
            dataStore.edit { p ->
                p[iad1tya.echo.music.constants.CrossfadeEnabledKey] = true
                p[iad1tya.echo.music.constants.CrossfadeDurationKey] = 9f
                p[iad1tya.echo.music.constants.CrossfadeCurveKey] = 1
                p[iad1tya.echo.music.constants.SafeVolumeEnabledKey] = true
            }
        }.onFailure { reportException(it) }
        val seeded = runCatching {
            val gains = iad1tya.echo.music.eq.data.FactoryPreset.AUDIOPHILE.gains
            val bands = iad1tya.echo.music.ui.screens.equalizer.axion.buildEqBands(gains, IntArray(gains.size))
            val profile = iad1tya.echo.music.eq.data.SavedEQProfile(
                id = "echo_tuning",
                name = "JR Tuning",
                deviceModel = "Equalizer",
                bands = bands,
                autoBands = emptyList(),
                preamp = 0.0,
                isCustom = false,
                isActive = true,
            )
            // DSP source of truth: MusicService collects combine(activeProfile, unsavedProfile){ unsaved ?: active }.
            val eqRepo = eqProfileRepository.get()
            eqRepo.saveProfile(profile)
            eqRepo.setUnsavedProfile(profile)
            eqRepo.setActiveProfile(profile.id)
            // EQ-screen UI mirror so the enabled toggle / sliders / preamp reflect the seeded Audiophile tuning.
            val eqPrefs = applicationContext.getSharedPreferences("echo_eq_prefs", Context.MODE_PRIVATE)
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

    /**
     * Force infinite playback (auto-radio at the end of an album/playlist/queue, and endless continuation when
     * you open an artist's top songs) ON for everyone — the owner wants endless playback always active. Fresh
     * key so it re-applies once even for users who had turned [AutoLoadMoreKey] off.
     */
    private fun applyInfinitePlaybackOn(
        p: androidx.datastore.preferences.core.MutablePreferences,
        settings: androidx.datastore.preferences.core.Preferences,
    ) {
        if (settings[iad1tya.echo.music.constants.InfinitePlaybackForcedOnKey] == true) return
        // Value + flag are written atomically in the shared transaction, so the flag is never set without the
        // value being applied (a failed commit rolls back both and it retries on the next launch).
        p[iad1tya.echo.music.constants.AutoLoadMoreKey] = true
        p[iad1tya.echo.music.constants.InfinitePlaybackForcedOnKey] = true
    }

    /**
     * Force "Safe Volume" (Volumen Seguro) ON for EVERYONE on this update — new installs and existing users,
     * including anyone who had previously turned it OFF (owner wants it on unconditionally). Fresh key so it
     * re-applies once even though [SafeVolumeEnabledKey] may already be set (a versionCode bump or a set flag
     * alone would NOT re-run it). This is a one-time FORCE, not a lock: afterwards the user can still toggle
     * Safe Volume off in Settings and their choice sticks. Runs AFTER migrateAudioDefaultsV2 (which used to set
     * Safe Volume OFF) so this ON write wins on the same launch.
     */
    private fun applySafeVolumeDefaultOn(
        p: androidx.datastore.preferences.core.MutablePreferences,
        settings: androidx.datastore.preferences.core.Preferences,
    ) {
        if (settings[iad1tya.echo.music.constants.SafeVolumeDefaultOnAppliedKey] == true) return
        // Value + flag written atomically (see applyInfinitePlaybackOn). Runs AFTER migrateAudioDefaultsV2 so
        // this forced-ON wins on the same launch.
        p[iad1tya.echo.music.constants.SafeVolumeEnabledKey] = true
        p[iad1tya.echo.music.constants.SafeVolumeDefaultOnAppliedKey] = true
    }

    /**
     * One-time (V1, FRESH key): seed the standard-layout lyrics blur ON. The default lyric style is
     * APPLE_V2 (seeded in [applySeedDefaults]), and its Apple-style blur is gated on
     * [iad1tya.echo.music.constants.LyricsStandardBlurKey] — the always-visible "standard lyrics blur"
     * toggle in Appearance — which defaults FALSE, so the advertised blur was invisible by default.
     * A fresh flag key is required (a set flag or versionCode bump alone won't re-run a migration).
     * One-time only: afterwards the user's own toggle choice sticks.
     */
    private fun applyLyricsBlurDefaultOnV1(
        p: androidx.datastore.preferences.core.MutablePreferences,
        settings: androidx.datastore.preferences.core.Preferences,
    ) {
        if (settings[iad1tya.echo.music.constants.LyricsBlurDefaultOnV1AppliedKey] == true) return
        p[iad1tya.echo.music.constants.LyricsStandardBlurKey] = true
        p[iad1tya.echo.music.constants.LyricsBlurDefaultOnV1AppliedKey] = true
    }

    /**
     * One-time (V1, FRESH key): default the Add-to-playlist dialog sort to LAST_UPDATED descending —
     * recently-added-to playlists first — for EVERYONE, including installs that already have
     * addToPlaylistSortType persisted (a set flag or versionCode bump alone won't re-run a migration).
     * Enum prefs are stored as their String name (see rememberEnumPreference). One-time only: the sort
     * header inside the dialog stays functional, so the user's later choice sticks.
     */
    private fun applyAddToPlaylistLastUpdatedDefaultV1(
        p: androidx.datastore.preferences.core.MutablePreferences,
        settings: androidx.datastore.preferences.core.Preferences,
    ) {
        if (settings[iad1tya.echo.music.constants.AddToPlaylistLastUpdatedDefaultV1AppliedKey] == true) return
        p[iad1tya.echo.music.constants.AddToPlaylistSortTypeKey] =
            iad1tya.echo.music.constants.PlaylistSortType.LAST_UPDATED.name
        p[iad1tya.echo.music.constants.AddToPlaylistSortDescendingKey] = true
        p[iad1tya.echo.music.constants.AddToPlaylistLastUpdatedDefaultV1AppliedKey] = true
    }

    private fun applyMiniPlayerDefaultBg(
        p: androidx.datastore.preferences.core.MutablePreferences,
        settings: androidx.datastore.preferences.core.Preferences,
    ) {
        // The mini-player was seeded with a dynamic (APPLE_MUSIC) background, which forces white text that is
        // illegible in light mode. Reset the mini-player background to DEFAULT once so its text is the
        // readable gray (onSurface). Runs once; the user can pick a dynamic mini background again later.
        if (settings[iad1tya.echo.music.constants.MiniPlayerDefaultBgAppliedKey] == true) return
        p[iad1tya.echo.music.constants.MiniPlayerBackgroundStyleKey] =
            iad1tya.echo.music.constants.PlayerBackgroundStyle.DEFAULT.name
        p[iad1tya.echo.music.constants.MiniPlayerDefaultBgAppliedKey] = true
    }

    private fun applyCanvasDefaultOn(
        p: androidx.datastore.preferences.core.MutablePreferences,
        settings: androidx.datastore.preferences.core.Preferences,
    ) {
        // User request: ALL canvas/lienzo toggles enabled. Force them ON once (even for installs that had
        // the previous default-OFF migration), then remember it so the user's later choice is respected.
        if (settings[iad1tya.echo.music.constants.CanvasDefaultOnAppliedKey] == true) return
        // E2: this one-time "canvas ON" push must respect device tier — on LOW-capability phones keep the
        // heavy cover-canvas OFF by default (otherwise it re-enabled the very decoders E2 keeps off on weak
        // devices, right after seedDefaultsIfNeeded had defaulted them off on a fresh install).
        val lowEndDevice =
            iad1tya.echo.music.utils.DeviceCapabilities.tier(this) == iad1tya.echo.music.utils.DeviceTier.LOW
        p[iad1tya.echo.music.constants.CanvasThumbnailAnimationKey] = !lowEndDevice
        p[iad1tya.echo.music.constants.AlbumCanvasEnabledKey] = !lowEndDevice
        p[iad1tya.echo.music.constants.CanvasDefaultOnAppliedKey] = true
    }

    /**
     * One-time (fresh key): auto-enable High-Performance Mode for EXISTING users on genuinely LOW-tier phones,
     * so a weak device that predates the toggle gets the lean path on this update. NOT on TV (the television
     * gets the full premium experience — see [migratePerfModeReseedV2]). Only sets it when still unset (a
     * capable phone that never had the key is left OFF); afterwards the user's manual choice wins.
     */
    private fun applyHighPerformanceModeSeed(
        p: androidx.datastore.preferences.core.MutablePreferences,
        settings: androidx.datastore.preferences.core.Preferences,
    ) {
        if (settings[iad1tya.echo.music.constants.HighPerfModeSeedAppliedKey] == true) return
        val forcePerf =
            iad1tya.echo.music.utils.DeviceCapabilities.tier(this) == iad1tya.echo.music.utils.DeviceTier.LOW
        // Reads the SHARED `p` so it sees any HighPerformanceModeKey applySeedDefaults just wrote (matching the
        // old commit-then-read chain across these perf-mode migrations).
        if (p[iad1tya.echo.music.constants.HighPerformanceModeKey] == null) {
            p[iad1tya.echo.music.constants.HighPerformanceModeKey] = forcePerf
        }
        p[iad1tya.echo.music.constants.HighPerfModeSeedAppliedKey] = true
    }

    /**
     * One-time (fresh key): corrects the earlier OVER-aggressive High-Performance Mode auto-enable. The old
     * ~4300 MB LOW threshold plus the TV/car force turned perf-mode ON on capable ~4 GB phones and on televisions,
     * which strips the home carousels and disables video mode (audio only). This turns perf-mode OFF for:
     *   - ALL televisions (the TV is the premium large-screen experience: 1080p video + carousels + covers), and
     *   - capable phones (tier != LOW under the corrected threshold) that are currently forced ON.
     * It NEVER enables perf-mode: a genuinely low-end phone that has it ON keeps it. The user can still toggle it
     * manually afterwards (e.g. re-enable it on a very weak TV box). Runs once, gated by a fresh key.
     */
    private fun applyPerfModeReseedV2(
        p: androidx.datastore.preferences.core.MutablePreferences,
        settings: androidx.datastore.preferences.core.Preferences,
    ) {
        if (settings[iad1tya.echo.music.constants.PerfModeReseedV2AppliedKey] == true) return
        val isTelevision = iad1tya.echo.music.utils.DeviceForm.isTelevision(this)
        val genuinelyLow =
            iad1tya.echo.music.utils.DeviceCapabilities.tier(this) == iad1tya.echo.music.utils.DeviceTier.LOW
        val current = p[iad1tya.echo.music.constants.HighPerformanceModeKey]
        if (isTelevision) {
            // TV = full experience, regardless of RAM tier (1080p video, carousels, high-res covers).
            p[iad1tya.echo.music.constants.HighPerformanceModeKey] = false
        } else if (current == true && !genuinelyLow) {
            // Capable phone wrongly forced into the lean path → restore the full home (carousels).
            p[iad1tya.echo.music.constants.HighPerformanceModeKey] = false
        }
        p[iad1tya.echo.music.constants.PerfModeReseedV2AppliedKey] = true
    }

    /**
     * One-time (fresh key): CAPABILITY RE-ENABLE. [migratePerfModeReseedV2] forced High-Performance Mode OFF on
     * ALL televisions to give capable TVs the premium large-screen experience — but that also stripped perf-mode
     * from genuinely WEAK Android-TV boxes / car head-units, sending them back down the heavy path. This corrects
     * that regression: for genuinely LOW-tier devices (by RAM/cores/perf-class, NOT brand) whose High-Performance
     * Mode is NOT currently true, set it TRUE. Capable devices (tier != LOW — capable phones AND TVs) are never
     * touched, so they keep their carousels + video. It NEVER disables perf-mode. Runs once, AFTER
     * migratePerfModeReseedV2 (so it wins on the same launch for LOW-tier TV boxes the reseed just turned off).
     */
    private fun applyPerfModeCapabilityV3(
        p: androidx.datastore.preferences.core.MutablePreferences,
        settings: androidx.datastore.preferences.core.Preferences,
    ) {
        if (settings[iad1tya.echo.music.constants.PerfModeCapabilityV3AppliedKey] == true) return
        val genuinelyLow =
            iad1tya.echo.music.utils.DeviceCapabilities.tier(this) == iad1tya.echo.music.utils.DeviceTier.LOW
        // Read the CURRENT value from the SHARED `p` (not the pre-migration `settings` snapshot) so we react to
        // what applyPerfModeReseedV2 just wrote earlier in this same transaction.
        // Limit to genuinely-weak TV boxes / car head units — exactly what V2 wrongly forced OFF. A LOW-tier
        // PHONE with the flag false almost always means the user deliberately opted out (to keep
        // carousels/canvas/crossfade), so don't clobber that choice; only repair the boxes V2 regressed.
        if (genuinelyLow &&
            iad1tya.echo.music.utils.DeviceForm.isTvOrCar(this) &&
            p[iad1tya.echo.music.constants.HighPerformanceModeKey] != true
        ) {
            p[iad1tya.echo.music.constants.HighPerformanceModeKey] = true
        }
        p[iad1tya.echo.music.constants.PerfModeCapabilityV3AppliedKey] = true
    }

    /**
     * The "Legacy Icon" option was removed. Users who had it enabled only have the now-deleted
     * `MainActivityLegacy` launcher alias active, so re-enable the default `MainActivityAlias`
     * (and disable the static one) to keep their home-screen icon working. Runs once.
     */
    private suspend fun migrateLegacyIcon(settings: androidx.datastore.preferences.core.Preferences) {
        if (settings[iad1tya.echo.music.constants.EnableLegacyIconKey] != true) return
        runCatching {
            val pm = packageManager
            pm.setComponentEnabledSetting(
                ComponentName(this, "iad1tya.echo.music.MainActivityAlias"),
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP,
            )
            pm.setComponentEnabledSetting(
                ComponentName(this, "iad1tya.echo.music.MainActivityStatic"),
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP,
            )
        }.onFailure { Timber.e(it, "migrateLegacyIcon: failed to reset launcher alias") }
        dataStore.edit { it[iad1tya.echo.music.constants.EnableLegacyIconKey] = false }
    }

    private fun observeSettingsChanges() {
        applicationScope.launch(Dispatchers.IO) {
            dataStore.data
                .map { it[VisitorDataKey] }
                .distinctUntilChanged()
                .collect { visitorData ->
                    YouTube.visitorData = visitorData?.takeIf { it != "null" }
                        ?: YouTube.visitorData().getOrNull()?.also { newVisitorData ->
                            dataStore.edit { settings ->
                                settings[VisitorDataKey] = newVisitorData
                            }
                        }
                }
        }

        applicationScope.launch(Dispatchers.IO) {
            dataStore.data
                .map { it[DataSyncIdKey] }
                .distinctUntilChanged()
                .collect { dataSyncId ->
                    YouTube.dataSyncId = dataSyncId?.let {
                        it.takeIf { !it.contains("||") }
                            ?: it.takeIf { it.endsWith("||") }?.substringBefore("||")
                            ?: it.substringAfter("||")
                    }
                }
        }

        applicationScope.launch(Dispatchers.IO) {
            dataStore.data
                .map { it[InnerTubeCookieKey] }
                .distinctUntilChanged()
                .collect { cookie ->
                    try {
                        YouTube.cookie = cookie
                    } catch (e: Exception) {
                        Timber.e(e, "Could not parse cookie. Clearing existing cookie.")
                        forgetAccount(this@App)
                    }
                }
        }



        applicationScope.launch(Dispatchers.IO) {
            dataStore.data
                .map { Triple(it[ContentCountryKey], it[ContentLanguageKey], it[AppLanguageKey]) }
                .distinctUntilChanged()
                .collect { (contentCountry, contentLanguage, appLanguage) ->
                    // Mirror the chosen app language to SharedPreferences so attachBaseContext can
                    // apply it at next cold start without a crash-prone blocking DataStore read.
                    getSharedPreferences("app_prefs", Context.MODE_PRIVATE).edit()
                        // No explicit in-app language => follow the device/system locale, not a
                        // forced "es". resolveAppLanguageTag() resolves SYSTEM_DEFAULT live to the
                        // real device locale, which also keeps search gl/hl on the device locale.
                        .putString("app_language", appLanguage?.takeUnless { it == SYSTEM_DEFAULT } ?: SYSTEM_DEFAULT)
                        .apply()
                    val systemLocale = Locale.getDefault()
                    val effectiveAppLocale = appLanguage
                        ?.takeUnless { it == SYSTEM_DEFAULT }
                        ?.let { Locale.forLanguageTag(it) }
                        ?: systemLocale

                    YouTube.locale = YouTubeLocale(
                        gl = contentCountry?.takeIf { it != SYSTEM_DEFAULT }
                            ?: effectiveAppLocale.country.takeIf { it in CountryCodeToName }
                            ?: systemLocale.country.takeIf { it in CountryCodeToName }
                            ?: "US",
                        hl = contentLanguage?.takeIf { it != SYSTEM_DEFAULT }
                            ?: effectiveAppLocale.toLanguageTag().takeIf { it in LanguageCodeToName }
                            ?: effectiveAppLocale.language.takeIf { it in LanguageCodeToName }
                            ?: "en"
                    )
                }
        }

        applicationScope.launch(Dispatchers.IO) {
            dataStore.data
                .map { it[IpVersionKey] }
                .distinctUntilChanged()
                .collect { ipVersion ->
                    YouTube.ipVersion = ipVersion?.toEnum(defaultValue = IpVersion.AUTO) ?: IpVersion.AUTO
                }
        }
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader {
        // Default the image cache to a large size (2 GB on disk, half the app's RAM in memory) so
        // artwork loads instantly and isn't re-downloaded; the user can still lower it in settings.
        // Read from a process-wide mirror instead of a main-thread runBlocking DataStore read (P46/H4).
        // The mirror is seeded at startup and updated SYNCHRONOUSLY by StorageSettings right before it
        // calls SingletonImageLoader.reset(), so a size change still rebuilds the loader with the fresh value.
        val cacheSize = imageCacheSizeMb()
        // Weak (LOW-tier) device: much smaller in-RAM image cache (15% vs 40%) so a low-RAM box doesn't thrash
        // decoding artwork (+ RGB_565 / no fade below). Uses the CACHED device tier (cheap, non-blocking) instead
        // of a main-thread DataStore read, so newImageLoader never blocks the main thread (P46/H4). The loader is
        // built once at process start.
        val perfMode = iad1tya.echo.music.utils.DeviceCapabilities.tier(this) == iad1tya.echo.music.utils.DeviceTier.LOW
        val memCachePercent = if (perfMode) 0.15 else 0.40
        return ImageLoader.Builder(this).apply {
            // Perf mode: no fade animation on image load + RGB_565 (16-bit) bitmaps = HALF the memory per image
            // (a touch of gradient banding, invisible on album art) — a real win on low-RAM boxes.
            if (perfMode) {
                crossfade(false)
                allowRgb565(true)
            } else {
                crossfade(250)
            }
            allowHardware(true)

            components {
                // Render embedded cover art for local audio files (content://media/.../audio/media/{id}).
                // Higher priority than Coil's default ContentUriFetcher; returns null for non-audio URIs so
                // remote/http and other content URIs fall through to the built-in fetchers unchanged.
                add(iad1tya.echo.music.utils.coil.LocalAudioArtFetcher.Factory())
            }

            memoryCache {
                MemoryCache.Builder()
                    .maxSizePercent(context, memCachePercent)
                    .build()
            }
            if (cacheSize == 0) {
                diskCachePolicy(CachePolicy.DISABLED)
            } else {
                diskCache(
                    DiskCache.Builder()
                        .directory(cacheDir.resolve("coil"))
                        .maxSizeBytes(cacheSize * 1024 * 1024L)
                        .build()
                )
            }
        }.build()
    }

    companion object {
        /** Bump when adding a new one-time default set so it re-seeds for everyone (and after restore). */
        const val CURRENT_SEED_VERSION = 6

        /**
         * Process-wide mirror of [MaxImageCacheSizeKey] so [newImageLoader] reads the image-cache size
         * WITHOUT a main-thread blocking DataStore read (P46/H4). It is:
         *  - seeded synchronously at process start from a cheap SharedPreferences copy ([seedImageCacheSizeMirror]),
         *  - re-seeded from the authoritative DataStore value once settings load (in initializeSettings), and
         *  - updated synchronously by StorageSettings right BEFORE SingletonImageLoader.reset() so the rebuilt
         *    ImageLoader reads the just-committed size ([updateImageCacheSizeMirror]).
         * The default matches newImageLoader's historical `?: 2048` fallback exactly.
         */
        const val DEFAULT_IMAGE_CACHE_SIZE_MB = 2048
        private const val IMAGE_CACHE_MIRROR_PREFS = "image_loader_prefs"
        private const val IMAGE_CACHE_MIRROR_KEY = "max_image_cache_size_mb"

        @Volatile
        private var imageCacheSizeMbMirror: Int = DEFAULT_IMAGE_CACHE_SIZE_MB

        /** Non-blocking read of the image-cache-size mirror (in MB), used by [newImageLoader]. */
        fun imageCacheSizeMb(): Int = imageCacheSizeMbMirror

        /**
         * Synchronously set the image-cache-size mirror (in-memory + a cheap SharedPreferences copy used to
         * seed the mirror on the next cold start). StorageSettings MUST call this right BEFORE
         * SingletonImageLoader.reset() so the rebuilt ImageLoader reads the just-committed size — this replaces
         * the former main-thread runBlocking DataStore read inside newImageLoader().
         */
        fun updateImageCacheSizeMirror(context: Context, sizeMb: Int) {
            imageCacheSizeMbMirror = sizeMb
            runCatching {
                context.applicationContext
                    .getSharedPreferences(IMAGE_CACHE_MIRROR_PREFS, Context.MODE_PRIVATE)
                    .edit()
                    .putInt(IMAGE_CACHE_MIRROR_KEY, sizeMb)
                    .apply()
            }
        }

        /** Seed the mirror synchronously at process start from the SharedPreferences copy (no DataStore blocking). */
        private fun seedImageCacheSizeMirror(context: Context) {
            imageCacheSizeMbMirror = runCatching {
                context.applicationContext
                    .getSharedPreferences(IMAGE_CACHE_MIRROR_PREFS, Context.MODE_PRIVATE)
                    .getInt(IMAGE_CACHE_MIRROR_KEY, DEFAULT_IMAGE_CACHE_SIZE_MB)
            }.getOrDefault(DEFAULT_IMAGE_CACHE_SIZE_MB)
        }

        /**
         * Process-wide SharedPreferences mirror of [MaxSongCacheSizeKey] so [AppModule.providePlayerCache] reads
         * the song-cache size WITHOUT a main-thread blocking DataStore read (mirrors the image-cache-size pattern
         * above). Default -1 == unlimited (NoOpCacheEvictor), matching providePlayerCache's historical `?: -1`.
         * Seeded + kept fresh reactively in [onCreate] (collect of the key's flow).
         */
        const val DEFAULT_SONG_CACHE_SIZE_MB = -1
        private const val SONG_CACHE_MIRROR_PREFS = "player_cache_prefs"
        private const val SONG_CACHE_MIRROR_KEY = "max_song_cache_size_mb"

        /** Non-blocking read of the song-cache-size mirror (in MB, -1 = unlimited), used by providePlayerCache. */
        fun songCacheSizeMb(context: Context): Int = runCatching {
            val prefs = context.applicationContext
                .getSharedPreferences(SONG_CACHE_MIRROR_PREFS, Context.MODE_PRIVATE)
            if (prefs.contains(SONG_CACHE_MIRROR_KEY)) {
                // Mirror present (steady state): cheap non-blocking read, no DataStore access.
                prefs.getInt(SONG_CACHE_MIRROR_KEY, DEFAULT_SONG_CACHE_SIZE_MB)
            } else {
                // First launch after this update (or after a device restore): the mirror hasn't been
                // seeded yet. Falling through to the default (-1 = unlimited) here would build the
                // SimpleCache with NoOpCacheEvictor even when the user configured a size limit. Do ONE
                // blocking DataStore read of the authoritative value ONLY in this unseeded case, then
                // seed the mirror so every subsequent launch hits the non-blocking path above.
                val authoritative = kotlinx.coroutines.runBlocking {
                    context.applicationContext.dataStore.data.first()[MaxSongCacheSizeKey]
                        ?: DEFAULT_SONG_CACHE_SIZE_MB
                }
                updateSongCacheSizeMirror(context, authoritative)
                authoritative
            }
        }.getOrDefault(DEFAULT_SONG_CACHE_SIZE_MB)

        /** Synchronously update the song-cache-size mirror SharedPreferences copy (read at the next process start). */
        fun updateSongCacheSizeMirror(context: Context, sizeMb: Int) {
            runCatching {
                context.applicationContext
                    .getSharedPreferences(SONG_CACHE_MIRROR_PREFS, Context.MODE_PRIVATE)
                    .edit()
                    .putInt(SONG_CACHE_MIRROR_KEY, sizeMb)
                    .apply()
            }
        }

        suspend fun forgetAccount(context: Context) {
            Timber.d("forgetAccount: Starting logout process")

            
            Timber.d("forgetAccount: Clearing DataStore preferences")
            context.dataStore.edit { settings ->
                settings.remove(InnerTubeCookieKey)
                settings.remove(VisitorDataKey)
                settings.remove(DataSyncIdKey)
                settings.remove(AccountNameKey)
                settings.remove(AccountEmailKey)
                settings.remove(AccountChannelHandleKey)
            }
            Timber.d("forgetAccount: DataStore preferences cleared")

            
            Timber.d("forgetAccount: Clearing YouTube object auth state")
            // Never log auth values (cookie/visitorData/dataSyncId) — just whether they were present.
            Timber.d("forgetAccount: Before - hadCookie=${YouTube.cookie != null}")
            YouTube.cookie = null
            YouTube.visitorData = null
            YouTube.dataSyncId = null
            Timber.d("forgetAccount: After - auth cleared")

            
            Timber.d("forgetAccount: Clearing WebView CookieManager")
            withContext(Dispatchers.Main) {
                android.webkit.CookieManager.getInstance().apply {
                    removeAllCookies { removed ->
                        Timber.d("forgetAccount: CookieManager.removeAllCookies callback: removed=$removed")
                    }
                    flush()
                }
            }
            Timber.d("forgetAccount: Logout process complete")
        }
    }
}
