package iad1tya.echo.music.eq.data

import android.content.Context
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

/**
 * Lets plain composables reach the singleton [EQProfileRepository] without pulling in a full EQ ViewModel.
 *
 * Added for the audio-offload gate in PlayerSettings: MusicService vetoes offload while an EQ profile is
 * applied, so the settings switch has to be able to see the same state the service reads, or it would show
 * "on" while the engine kept offload off.
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface EQProfileRepositoryEntryPoint {
    fun eqProfileRepository(): EQProfileRepository

    companion object {
        fun get(context: Context): EQProfileRepository =
            EntryPointAccessors
                .fromApplication(context.applicationContext, EQProfileRepositoryEntryPoint::class.java)
                .eqProfileRepository()
    }
}
