

@file:OptIn(ExperimentalCoroutinesApi::class)

package iad1tya.echo.music.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import iad1tya.echo.music.constants.AlbumSortType
import iad1tya.echo.music.constants.HideExplicitKey
import iad1tya.echo.music.db.MusicDatabase
import iad1tya.echo.music.extensions.filterExplicitAlbums
import iad1tya.echo.music.utils.dataStore
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class FavoriteAlbumsViewModel
@Inject
constructor(
    @ApplicationContext context: Context,
    database: MusicDatabase,
) : ViewModel() {
    val albums =
        context.dataStore.data
            .map { it[HideExplicitKey] ?: false }
            .distinctUntilChanged()
            .flatMapLatest { hideExplicit ->
                database.albumsLiked(AlbumSortType.CREATE_DATE, true)
                    .map { it.filterExplicitAlbums(hideExplicit) }
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
}
