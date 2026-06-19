package iad1tya.echo.music.dislike

import android.content.Context
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

/** Lets plain composables (e.g. the album/artist/playlist menus) reach the singleton [DislikeStore]. */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface DislikeStoreEntryPoint {
    fun dislikeStore(): DislikeStore

    companion object {
        fun get(context: Context): DislikeStore =
            EntryPointAccessors.fromApplication(context.applicationContext, DislikeStoreEntryPoint::class.java)
                .dislikeStore()
    }
}
