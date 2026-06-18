

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
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
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

    override fun onCreate() {
        super.onCreate()

        com.music.jiosaavn.DeviceRouter.init(this)
        Timber.d("Device ID: ${com.music.jiosaavn.DeviceRouter.getDeviceId()} | Assigned JioSaavn Server: ${com.music.jiosaavn.DeviceRouter.getCurrentServer()}")

        val prefs = getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("cleared_db_v5", false)) {
            deleteDatabase("song.db")
            prefs.edit().putBoolean("cleared_db_v5", true).apply()
        }

        
        CrashHandler.install(this)

        
        CipherDeobfuscator.initialize(this)

        if (iad1tya.echo.music.BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
        iad1tya.echo.music.utils.AppLogger.plant(this)


        applicationScope.launch {
            initializeSettings()
            observeSettingsChanges()
        }

        // Schedule the weekly Release Radar check (aligned to the next Friday morning).
        // Safe to call every start: it uses a unique periodic work item with UPDATE policy.
        runCatching { iad1tya.echo.music.releaseradar.ReleaseRadarWorker.schedule(this) }
            .onFailure { Timber.e(it, "Failed to schedule Release Radar worker") }
        // Also refresh the new-releases list on every app start so it's up to date from the moment
        // the user opens the app (KEEP policy means concurrent triggers don't pile up).
        runCatching { iad1tya.echo.music.releaseradar.ReleaseRadarWorker.runNow(this) }
            .onFailure { Timber.e(it, "Failed to trigger Release Radar refresh") }

        // Schedule the weekly app-update check (notifies once per new version when one is found).
        runCatching { iad1tya.echo.music.echomusic.updater.UpdateCheckWorker.schedule(this) }
            .onFailure { Timber.e(it, "Failed to schedule update-check worker") }
    }

    private suspend fun initializeSettings() {
        reseedAfterRestoreIfNeeded()
        val settings = dataStore.data.first()
        seedDefaultsIfNeeded(settings)
        migrateLegacyIcon(settings)
        val locale = Locale.getDefault()
        val languageTag = locale.language

        YouTube.locale = YouTubeLocale(
            gl = settings[ContentCountryKey]?.takeIf { it != SYSTEM_DEFAULT }
                ?: locale.country.takeIf { it in CountryCodeToName }
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

        YouTube.useLoginForBrowse = settings[UseLoginForBrowse] ?: true
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
    private suspend fun seedDefaultsIfNeeded(settings: androidx.datastore.preferences.core.Preferences) {
        val stored = settings[iad1tya.echo.music.constants.SeedVersionKey]
        val legacyApplied = settings[iad1tya.echo.music.constants.JrDefaultsAppliedKey] == true
        if (!iad1tya.echo.music.viewmodels.shouldReseed(stored, legacyApplied, CURRENT_SEED_VERSION)) {
            // Record the migration of a pre-SeedVersion install so we don't recompute every launch.
            if (stored == null) {
                dataStore.edit { it[iad1tya.echo.music.constants.SeedVersionKey] = CURRENT_SEED_VERSION }
            }
            return
        }
        dataStore.edit { p ->
            // "Inspirado en Apple Music" player. The toggle is ON when UseNewPlayerDesign == false AND
            // the player background is APPLE_MUSIC — seeding LIVE_MESH before made the switch *look* on
            // but not actually apply (it only kicked in after a manual off→on). Seed APPLE_MUSIC so it
            // works from first launch.
            p[iad1tya.echo.music.constants.PlayerBackgroundStyleKey] =
                iad1tya.echo.music.constants.PlayerBackgroundStyle.APPLE_MUSIC.name
            p[iad1tya.echo.music.constants.MiniPlayerBackgroundStyleKey] =
                iad1tya.echo.music.constants.PlayerBackgroundStyle.APPLE_MUSIC.name
            p[iad1tya.echo.music.constants.UseNewPlayerDesignKey] = false
            p[iad1tya.echo.music.constants.HidePlayerSliderKey] = true

            // Lyrics: Apple Music v2 animation + glow + blur on by default.
            p[iad1tya.echo.music.constants.LyricsAnimationStyleKey] =
                iad1tya.echo.music.constants.LyricsAnimationStyle.APPLE_V2.name
            p[iad1tya.echo.music.constants.LyricsGlowEffectKey] = true
            p[iad1tya.echo.music.constants.AppleMusicLyricsBlurKey] = true

            // Visuals on by default: spectrum visualizer, player canvas, album canvas, artist video +
            // artist background ("canvas") video.
            p[iad1tya.echo.music.constants.SpectrumVisualizerEnabledKey] = true
            p[iad1tya.echo.music.constants.CanvasThumbnailAnimationKey] = true
            p[iad1tya.echo.music.constants.AlbumCanvasEnabledKey] = true
            p[iad1tya.echo.music.constants.ShowArtistVideoKey] = true
            p[iad1tya.echo.music.constants.ShowArtistBackgroundVideoKey] = true

            // Content filters on by default: hide video songs + hide YouTube Shorts.
            p[iad1tya.echo.music.constants.HideVideoSongsKey] = true
            p[iad1tya.echo.music.constants.HideYoutubeShortsKey] = true

            // AMOLED (pure black) dark theme on by default; dynamic (per-artwork) theme off.
            p[iad1tya.echo.music.constants.DarkModeKey] =
                iad1tya.echo.music.ui.screens.settings.DarkMode.ON.name
            p[iad1tya.echo.music.constants.PureBlackKey] = true
            p[iad1tya.echo.music.constants.DynamicThemeKey] = false
            // Accent matches the "AURA HI-RES" title tones (tasteful teal). Material's TonalSpot
            // palette tones it down so it reads cohesive, not garish. Not the wallpaper color.
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
                        .putString("app_language", appLanguage?.takeUnless { it == SYSTEM_DEFAULT } ?: "es")
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
        val cacheSize = runBlocking {
            dataStore.data.map { it[MaxImageCacheSizeKey] ?: 512 }.first()
        }
        return ImageLoader.Builder(this).apply {
            crossfade(true)
            allowHardware(Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
            
            memoryCache {
                MemoryCache.Builder()
                    .maxSizePercent(context, 0.25)
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
