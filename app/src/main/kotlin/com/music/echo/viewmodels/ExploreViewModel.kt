

package iad1tya.echo.music.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.music.innertube.YouTube
import com.music.innertube.models.AlbumItem
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
    // True only when the load genuinely FAILED (the new-releases fallback threw — the authoritative
    // fetch, since it is always attempted whenever the explore page yields no albums shelf). Lets the
    // "Álbum" tab tell a real error ("couldn't load, check connection") apart from a
    // successful-but-empty shelf ("no new releases right now") — no more one generic message for both.
    val loadFailed = MutableStateFlow(false)

    private suspend fun load() {
        isLoading.value = true
        loadFailed.value = false
        val result = YouTube.explore()
        val page = result.getOrNull()
        result.exceptionOrNull()?.let { reportException(it) }

        // The explore page locates the "new releases" shelf via a "more" button that isn't always
        // present (notably when signed in), so it often came back empty -> "no se pudieron cargar las
        // sugerencias". Fall back to the dedicated FEmusic_new_releases_albums endpoint, which reads the
        // albums grid directly and doesn't depend on the explore-page layout.
        var albums = page?.newReleaseAlbums.orEmpty()
        var fallbackResult: Result<List<AlbumItem>>? = null
        if (albums.isEmpty()) {
            fallbackResult = YouTube.newReleaseAlbums()
            fallbackResult.exceptionOrNull()?.let { reportException(it) }
            albums = fallbackResult.getOrNull().orEmpty()
        }

        if (albums.isEmpty()) {
            // Nothing to publish as albums. Keep any moodAndGenres the explore page did return so the
            // Explore tab still works. Classify by the FALLBACK's outcome: reaching here means the
            // fallback was always attempted (albums stayed empty), and explore() "succeeding" without
            // an albums shelf is the COMMON case (signed-in layout) — so a thrown fallback (offline) is
            // a real failure even when result.isFailure is false. Only a successful-but-empty fallback
            // means "no releases right now". (result.isFailure is a defensive default only.)
            if (page != null) {
                explorePage.value = ExplorePage(
                    newReleaseAlbums = emptyList(),
                    moodAndGenres = page.moodAndGenres,
                )
            }
            loadFailed.value = fallbackResult?.isFailure ?: result.isFailure
            isLoading.value = false
            return
        }

        run {
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
