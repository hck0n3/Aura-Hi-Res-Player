package iad1tya.echo.music.di

import android.content.Context
import androidx.room.Room
import com.aura.migration.MigrationEngine
import com.aura.migration.db.MatchCache
import com.aura.migration.net.HttpJson
import com.aura.migration.net.OkHttpJson
import com.aura.migration.resolver.TrackResolver
import com.aura.migration.resolver.YtmClient
import com.aura.migration.source.deezer.DeezerSource
import com.aura.migration.source.file.FileSource
import com.aura.migration.source.tidal.TidalAuth
import com.aura.migration.source.tidal.TidalSource
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import iad1tya.echo.music.constants.TidalClientIdKey
import iad1tya.echo.music.migration.MigrationCacheDatabase
import iad1tya.echo.music.migration.TidalTokenStore
import iad1tya.echo.music.migration.YtmClientInnerTube
import iad1tya.echo.music.utils.dataStore
import iad1tya.echo.music.utils.get
import okhttp3.OkHttpClient
import java.util.Locale
import javax.inject.Provider
import javax.inject.Singleton

/**
 * Wires the standalone `:migration` module into Aura.
 *
 * The module is intentionally backend-agnostic: it asks for a [YtmClient] (satisfied by Aura's own
 * InnerTube-backed [YtmClientInnerTube]), a [MatchCache] (backed by its OWN tiny Room DB, never
 * MusicDatabase), and an [HttpJson] (OkHttp). Nothing here fans out network work — the engine resolves
 * one track at a time, so this preserves the sequential, thermal-safe flow.
 */
@Module
@InstallIn(SingletonComponent::class)
object MigrationModule {

    /** Where Tidal's browser flow redirects back into the app (manifest intent-filter on MainActivity). */
    const val TIDAL_REDIRECT_URI = "echomusic://tidal-callback"

    // One client for everything the migration does over plain OkHttp (HttpJson sources + token endpoint).
    // Private on purpose: no OkHttpClient binding is exposed to the graph, so this can never collide with
    // a future app-wide client binding.
    private val migrationHttpClient by lazy { OkHttpClient() }

    @Provides
    @Singleton
    fun provideMigrationHttpJson(): HttpJson = OkHttpJson(migrationHttpClient)

    @Provides
    @Singleton
    fun provideMigrationCacheDatabase(
        @ApplicationContext context: Context,
    ): MigrationCacheDatabase = Room
        .databaseBuilder(context, MigrationCacheDatabase::class.java, MigrationCacheDatabase.DB_NAME)
        // Pure recomputable cache: dropping it on a future schema bump is always safe, never loses user data.
        .fallbackToDestructiveMigration(dropAllTables = true)
        .build()

    @Provides
    @Singleton
    fun provideMatchCache(db: MigrationCacheDatabase): MatchCache = MatchCache(db.matchCacheDao())

    @Provides
    @Singleton
    fun provideYtmClient(): YtmClient = YtmClientInnerTube()

    @Provides
    @Singleton
    fun provideTrackResolver(ytm: YtmClient, cache: MatchCache): TrackResolver = TrackResolver(ytm, cache)

    @Provides
    @Singleton
    fun provideMigrationEngine(
        ytm: YtmClient,
        resolver: TrackResolver,
        cache: MatchCache,
    ): MigrationEngine = MigrationEngine(ytm, resolver, cache)

    @Provides
    @Singleton
    fun provideDeezerSource(http: HttpJson): DeezerSource = DeezerSource(http)

    @Provides
    @Singleton
    fun provideFileSource(): FileSource = FileSource()

    // Deliberately UNSCOPED: every injection re-reads the client id from DataStore, so the user can
    // paste/replace their developer.tidal.com id in settings and the very next auth round uses it —
    // no stale singleton capture. TidalTokenStore takes it as a Provider for the same reason.
    @Provides
    fun provideTidalAuth(
        @ApplicationContext context: Context,
    ): TidalAuth = TidalAuth(
        // A pasted id (settings) overrides; otherwise fall back to the id baked into the build so the
        // owner never has to paste anything. BuildConfig.TIDAL_CLIENT_ID may be blank on a build without
        // a default — then only a pasted id works.
        clientId = context.dataStore.get(TidalClientIdKey, "")
            .ifBlank { iad1tya.echo.music.BuildConfig.TIDAL_CLIENT_ID },
        redirectUri = TIDAL_REDIRECT_URI,
    )

    @Provides
    @Singleton
    fun provideTidalTokenStore(
        @ApplicationContext context: Context,
        auth: Provider<TidalAuth>,
    ): TidalTokenStore = TidalTokenStore(context, migrationHttpClient, auth)

    @Provides
    @Singleton
    fun provideTidalSource(
        http: HttpJson,
        tokenStore: TidalTokenStore,
    ): TidalSource = TidalSource(
        http = http,
        tokenProvider = { tokenStore.accessToken() },
        countryCode = Locale.getDefault().country.takeIf { it.length == 2 } ?: "ES",
    )
}
