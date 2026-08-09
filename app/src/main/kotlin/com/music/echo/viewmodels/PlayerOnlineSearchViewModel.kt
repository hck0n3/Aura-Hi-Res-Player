package iad1tya.echo.music.viewmodels

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.music.innertube.YouTube
import com.music.innertube.models.filterExplicit
import com.music.innertube.models.filterVideoSongs
import com.music.innertube.models.filterYoutubeShorts
import com.music.innertube.pages.SearchSummaryPage
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import iad1tya.echo.music.constants.HideExplicitKey
import iad1tya.echo.music.constants.HideVideoSongsKey
import iad1tya.echo.music.constants.HideYoutubeShortsKey
import iad1tya.echo.music.constants.PauseSearchHistoryKey
import iad1tya.echo.music.db.MusicDatabase
import iad1tya.echo.music.db.entities.SearchHistory
import iad1tya.echo.music.utils.dataStore
import iad1tya.echo.music.utils.get
import iad1tya.echo.music.utils.reportException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Full YouTube Music [YouTube.searchSummary] for the player quick-search sheet — same depth as the
 * main Buscar results route, without needing a nav `query` argument.
 */
@HiltViewModel
class PlayerOnlineSearchViewModel
@Inject
constructor(
    @ApplicationContext private val context: Context,
    private val database: MusicDatabase,
) : ViewModel() {
    var summaryPage by mutableStateOf<SearchSummaryPage?>(null)
        private set
    var loading by mutableStateOf(false)
        private set
    var committedQuery by mutableStateOf<String?>(null)
        private set

    private var searchJob: Job? = null

    fun clearCommitted() {
        searchJob?.cancel()
        summaryPage = null
        committedQuery = null
        loading = false
    }

    fun search(raw: String) {
        val q = raw.trim()
        if (q.isEmpty()) {
            clearCommitted()
            return
        }
        searchJob?.cancel()
        committedQuery = q
        summaryPage = null
        loading = true
        searchJob = viewModelScope.launch {
            if (!context.dataStore.get(PauseSearchHistoryKey, false)) {
                withContext(Dispatchers.IO) {
                    database.query {
                        insert(SearchHistory(query = q))
                    }
                }
            }
            var attempt = 0
            var page: SearchSummaryPage? = null
            while (page == null && attempt < 3) {
                YouTube.searchSummary(q)
                    .onSuccess { page = it }
                    .onFailure { reportException(it) }
                if (page == null) {
                    attempt++
                    delay(700L * attempt)
                }
            }
            val prefs = context.dataStore.data.first()
            val hideExplicit = prefs[HideExplicitKey] ?: false
            val hideVideoSongs = prefs[HideVideoSongsKey] ?: false
            val hideShorts = prefs[HideYoutubeShortsKey] ?: false
            summaryPage = page
                ?.filterExplicit(hideExplicit)
                ?.filterVideoSongs(hideVideoSongs)
                ?.filterYoutubeShorts(hideShorts)
            loading = false
        }
    }
}
