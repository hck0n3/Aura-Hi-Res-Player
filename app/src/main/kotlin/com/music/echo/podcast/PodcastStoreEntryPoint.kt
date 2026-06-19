package iad1tya.echo.music.podcast

import android.content.Context
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

/** Lets plain composables (e.g. the player menu) reach the singleton [PodcastProgressStore]. */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface PodcastStoreEntryPoint {
    fun podcastProgressStore(): PodcastProgressStore

    companion object {
        fun get(context: Context): PodcastProgressStore =
            EntryPointAccessors.fromApplication(context.applicationContext, PodcastStoreEntryPoint::class.java)
                .podcastProgressStore()
    }
}
