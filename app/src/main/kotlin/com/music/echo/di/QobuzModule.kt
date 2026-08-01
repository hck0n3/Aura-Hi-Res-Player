package iad1tya.echo.music.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import iad1tya.echo.music.qobuz.QobuzApi
import iad1tya.echo.music.qobuz.QobuzAuthenticator
import iad1tya.echo.music.qobuz.QobuzBundleScraper
import iad1tya.echo.music.qobuz.QobuzConfigProvider
import iad1tya.echo.music.qobuz.QobuzTokenStore
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

/**
 * Wires the OFFICIAL Qobuz client (owner's own subscription) into Aura.
 *
 * Only the LOGIN side is provided here (token store + config discovery + authenticator). The playback hot
 * path is served by the [iad1tya.echo.music.qobuz.QobuzHiRes] static holder, which App attaches to the
 * same singleton [QobuzTokenStore] at startup — so there is exactly one credential vault.
 */
@Module
@InstallIn(SingletonComponent::class)
object QobuzModule {

    // Private, not exposed to the graph (same rule as MigrationModule's client) so it can never collide
    // with an app-wide OkHttpClient binding. Short timeouts: login is interactive, don't hang the UI.
    private val qobuzHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    @Provides
    @Singleton
    fun provideQobuzTokenStore(
        @ApplicationContext context: Context,
    ): QobuzTokenStore = QobuzTokenStore(context)

    @Provides
    @Singleton
    fun provideQobuzApi(): QobuzApi = QobuzApi(qobuzHttpClient)

    @Provides
    @Singleton
    fun provideQobuzBundleScraper(): QobuzBundleScraper = QobuzBundleScraper(qobuzHttpClient)

    @Provides
    @Singleton
    fun provideQobuzConfigProvider(
        scraper: QobuzBundleScraper,
    ): QobuzConfigProvider = QobuzConfigProvider(qobuzHttpClient, scraper)

    @Provides
    @Singleton
    fun provideQobuzAuthenticator(
        api: QobuzApi,
        configProvider: QobuzConfigProvider,
    ): QobuzAuthenticator = QobuzAuthenticator(api, configProvider)
}
