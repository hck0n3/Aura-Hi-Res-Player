package iad1tya.echo.music.db

import android.content.Context
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

/**
 * Lets code that Hilt cannot inject into — `App.forgetAccount` lives in a companion object and is
 * called from a settings ViewModel, from the accounts screen and from App's own cookie collector —
 * reach the one singleton [MusicDatabase] instance.
 *
 * It resolves the SAME instance `AppModule.provideInternalDatabase` hands to everybody else. Building
 * a second Room instance here would open a second connection to song.db and, worse, would bypass the
 * migration list that only that provider carries.
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface MusicDatabaseEntryPoint {
    fun musicDatabase(): MusicDatabase

    companion object {
        fun get(context: Context): MusicDatabase =
            EntryPointAccessors
                .fromApplication(context.applicationContext, MusicDatabaseEntryPoint::class.java)
                .musicDatabase()
    }
}
