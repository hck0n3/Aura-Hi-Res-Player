

package iad1tya.echo.music.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.music.innertube.YouTube
import com.music.innertube.models.AlbumItem
import com.music.innertube.models.filterExplicit
import iad1tya.echo.music.constants.HideExplicitKey
import iad1tya.echo.music.db.MusicDatabase
import iad1tya.echo.music.utils.dataStore
import iad1tya.echo.music.utils.filterToSubscribedArtists
import iad1tya.echo.music.utils.get
import iad1tya.echo.music.utils.reportException
import iad1tya.echo.music.utils.subscribedArtistKeys
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class NewReleaseViewModel
@Inject
constructor(
    @ApplicationContext val context: Context,
    database: MusicDatabase,
) : ViewModel() {
    private val _newReleaseAlbums = MutableStateFlow<List<AlbumItem>>(emptyList())
    val newReleaseAlbums = _newReleaseAlbums.asStateFlow()

    init {
        viewModelScope.launch {
            YouTube
                .newReleaseAlbums()
                .onSuccess { albums ->
                    val subscribed = database.subscribedArtistKeys()
                    _newReleaseAlbums.value =
                        albums
                            .filterExplicit(context.dataStore.get(HideExplicitKey, false))
                            .filterToSubscribedArtists(subscribed)
                }.onFailure {
                    reportException(it)
                }
        }
    }
}
