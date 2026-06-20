

package iad1tya.echo.music.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.music.innertube.YouTube
import com.music.innertube.models.filterExplicit
import com.music.innertube.pages.ExplorePage
import iad1tya.echo.music.constants.HideExplicitKey
import iad1tya.echo.music.db.MusicDatabase
import iad1tya.echo.music.utils.dataStore
import iad1tya.echo.music.utils.get
import iad1tya.echo.music.utils.reportException
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ExploreViewModel
@Inject
constructor(
    @ApplicationContext val context: Context,
    val database: MusicDatabase,
) : ViewModel() {
    val explorePage = MutableStateFlow<ExplorePage?>(null)
    // True while the first/most-recent explore fetch is in flight. The UI uses this (instead of
    // "explorePage == null") so a failed load shows an empty/retry state rather than spinning forever.
    val isLoading = MutableStateFlow(true)

    private suspend fun load() {
        isLoading.value = true
        val result = YouTube.explore()
        val page = result.getOrNull()
        result.exceptionOrNull()?.let { reportException(it) }

        // The explore page locates the "new releases" shelf via a "more" button that isn't always
        // present (notably when signed in), so it often came back empty -> "no se pudieron cargar las
        // sugerencias". Fall back to the dedicated FEmusic_new_releases_albums endpoint, which reads the
        // albums grid directly and doesn't depend on the explore-page layout.
        var albums = page?.newReleaseAlbums.orEmpty()
        if (albums.isEmpty()) {
            albums = YouTube.newReleaseAlbums().getOrNull().orEmpty()
        }

        if (albums.isNotEmpty() || page != null) {
            val artists: MutableMap<Int, String> = mutableMapOf()
            val favouriteArtists: MutableMap<Int, String> = mutableMapOf()
            database.allArtistsByPlayTime().first().let { list ->
                var favIndex = 0
                for ((artistsIndex, artist) in list.withIndex()) {
                    artists[artistsIndex] = artist.id
                    if (artist.artist.bookmarkedAt != null) {
                        favouriteArtists[favIndex] = artist.id
                        favIndex++
                    }
                }
            }
            val sortedAlbums = albums
                .sortedBy { album ->
                    val artistIds = album.artists.orEmpty().mapNotNull { it.id }
                    artistIds.firstNotNullOfOrNull { artistId ->
                        if (artistId in favouriteArtists.values) {
                            favouriteArtists.entries.firstOrNull { it.value == artistId }?.key
                        } else {
                            artists.entries.firstOrNull { it.value == artistId }?.key
                        }
                    } ?: Int.MAX_VALUE
                }.filterExplicit(context.dataStore.get(HideExplicitKey, false))
            explorePage.value = ExplorePage(
                newReleaseAlbums = sortedAlbums,
                moodAndGenres = page?.moodAndGenres ?: emptyList(),
            )
        }
        isLoading.value = false
    }

    fun retry() {
        viewModelScope.launch(Dispatchers.IO) { load() }
    }

    init {
        viewModelScope.launch(Dispatchers.IO) {
            load()
        }
    }
}
