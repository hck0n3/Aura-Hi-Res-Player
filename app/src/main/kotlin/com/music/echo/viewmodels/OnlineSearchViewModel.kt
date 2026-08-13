
package iad1tya.echo.music.viewmodels

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.music.innertube.YouTube
import com.music.innertube.models.filterExplicit
import com.music.innertube.models.filterVideoSongs
import com.music.innertube.models.filterYoutubeShorts
import com.music.innertube.pages.SearchSummaryPage
import iad1tya.echo.music.constants.HideExplicitKey
import iad1tya.echo.music.constants.HideVideoSongsKey
import iad1tya.echo.music.constants.HideYoutubeShortsKey
import iad1tya.echo.music.models.ItemsPage
import iad1tya.echo.music.utils.dataStore
import iad1tya.echo.music.utils.reportException
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.net.URLDecoder
import javax.inject.Inject

@HiltViewModel
class OnlineSearchViewModel
@Inject
constructor(
    @ApplicationContext val context: Context,
    savedStateHandle: SavedStateHandle,
    private val podcastRepository: iad1tya.echo.music.podcast.PodcastRepository,
) : ViewModel() {
    val query = try {
        URLDecoder.decode(savedStateHandle.get<String>("query")!!, "UTF-8")
    } catch (e: IllegalArgumentException) {
        savedStateHandle.get<String>("query")!!
    }
    val filter = MutableStateFlow<YouTube.SearchFilter?>(null)
    var summaryPage by mutableStateOf<SearchSummaryPage?>(null)
    val viewStateMap = mutableStateMapOf<String, ItemsPage?>()

    /** True after every retry of the current All-tab / chip fetch failed. Stops the endless skeleton. */
    var hasFailed by mutableStateOf(false)
        private set

    /** Universal search: podcasts from the Apple/RSS engine, shown alongside the YouTube results. */
    var podcastResults by mutableStateOf<List<iad1tya.echo.music.podcast.PodcastShow>>(emptyList())
        private set

    init {
        viewModelScope.launch {
            podcastResults = runCatching { podcastRepository.search(query) }.getOrDefault(emptyList()).take(8)
        }
    }

    init {
        viewModelScope.launch {
            filter.collect { filter ->
                if (filter == null) {
                    if (summaryPage == null) loadSummary()
                } else {
                    if (viewStateMap[filter.value] == null) loadFiltered(filter)
                }
            }
        }
    }

    fun retry() {
        viewModelScope.launch {
            hasFailed = false
            val current = filter.value
            if (current == null) {
                summaryPage = null
                loadSummary()
            } else {
                viewStateMap.remove(current.value)
                loadFiltered(current)
            }
        }
    }

    private suspend fun loadSummary() {
        hasFailed = false
        var attempt = 0
        while (summaryPage == null && attempt < 3) {
            YouTube
                .searchSummary(query)
                .onSuccess {
                    val (hideExplicit, hideVideoSongs, hideYoutubeShorts) = hideFlags()
                    summaryPage =
                        it.filterExplicit(
                            hideExplicit,
                        ).filterVideoSongs(hideVideoSongs).filterYoutubeShorts(hideYoutubeShorts)
                }.onFailure {
                    reportException(it)
                }
            if (summaryPage == null) {
                attempt++
                delay(700L * attempt)
            }
        }
        if (summaryPage == null) hasFailed = true
    }

    private suspend fun loadFiltered(filter: YouTube.SearchFilter) {
        hasFailed = false
        YouTube
            .search(query, filter)
            .onSuccess { result ->
                val (hideExplicit, hideVideoSongs, hideYoutubeShorts) = hideFlags()
                viewStateMap[filter.value] =
                    ItemsPage(
                        result.items
                            .distinctBy { it.id }
                            .filterExplicit(
                                hideExplicit,
                            )
                            .let { items ->
                                if (filter.value == YouTube.SearchFilter.FILTER_VIDEO.value) items
                                else items.filterVideoSongs(hideVideoSongs)
                            }
                            .filterYoutubeShorts(hideYoutubeShorts),
                        result.continuation,
                    )
            }.onFailure {
                reportException(it)
            }
        if (viewStateMap[filter.value] == null) hasFailed = true
    }

    /**
     * Reads the three "hide" filter flags off the main thread via a single suspending DataStore
     * snapshot. Previously these were three blocking `runBlocking` `dataStore.get(...)` reads that
     * ran on the main dispatcher every time a search/filter/pagination call resumed (P20). `data.first()`
     * suspends instead of blocking and performs the disk read on DataStore's own IO dispatcher.
     */
    private suspend fun hideFlags(): Triple<Boolean, Boolean, Boolean> {
        val prefs = context.dataStore.data.first()
        return Triple(
            prefs[HideExplicitKey] ?: false,
            prefs[HideVideoSongsKey] ?: false,
            prefs[HideYoutubeShortsKey] ?: false,
        )
    }

    fun loadMore() {
        val filter = filter.value?.value
        viewModelScope.launch {
            if (filter == null) return@launch
            val viewState = viewStateMap[filter] ?: return@launch
            val continuation = viewState.continuation
            if (continuation != null) {
                val searchResult =
                    YouTube.searchContinuation(continuation).getOrNull() ?: return@launch
                val (hideExplicit, hideVideoSongs, hideYoutubeShorts) = hideFlags()
                val newItems = searchResult.items
                    .filterExplicit(hideExplicit)
                    .let { items ->
                        if (filter == YouTube.SearchFilter.FILTER_VIDEO.value) items
                        else items.filterVideoSongs(hideVideoSongs)
                    }
                    .filterYoutubeShorts(hideYoutubeShorts)
                viewStateMap[filter] = ItemsPage(
                    (viewState.items + newItems).distinctBy { it.id },
                    searchResult.continuation
                )
            }
        }
    }
}
