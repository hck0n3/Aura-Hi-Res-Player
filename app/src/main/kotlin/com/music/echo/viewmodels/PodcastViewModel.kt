package iad1tya.echo.music.viewmodels

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import iad1tya.echo.music.podcast.PodcastCategory
import iad1tya.echo.music.podcast.PodcastEpisode
import iad1tya.echo.music.podcast.PodcastRepository
import iad1tya.echo.music.podcast.PodcastShow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PodcastViewModel @Inject constructor(
    private val repository: PodcastRepository,
) : ViewModel() {

    val categories: List<PodcastCategory> = repository.categories

    val query = MutableStateFlow<String>("")
    val shows = MutableStateFlow<List<PodcastShow>>(emptyList())
    var searching by mutableStateOf(false)
        private set

    /** Trending shows per category for the catalog/empty state. */
    val trending = MutableStateFlow<Map<PodcastCategory, List<PodcastShow>>>(emptyMap())
    var loadingTrending by mutableStateOf(false)
        private set

    val selectedShow = MutableStateFlow<PodcastShow?>(null)
    val episodes = MutableStateFlow<List<PodcastEpisode>>(emptyList())
    var loadingEpisodes by mutableStateOf(false)
        private set

    val pinned = repository.pinnedShows.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    init {
        loadTrending()
    }

    fun loadTrending() {
        if (trending.value.isNotEmpty()) return
        viewModelScope.launch {
            loadingTrending = true
            coroutineScope {
                repository.categories.forEach { cat ->
                    launch(Dispatchers.IO) {
                        val result = runCatching { repository.top(cat) }.getOrDefault(emptyList())
                        if (result.isNotEmpty()) trending.update { it + (cat to result) }
                    }
                }
            }
            loadingTrending = false
        }
    }

    fun search() {
        val q = query.value.trim()
        if (q.isBlank()) return
        viewModelScope.launch {
            searching = true
            shows.value = runCatching { repository.search(q) }.getOrDefault(emptyList())
            searching = false
        }
    }

    fun openShow(show: PodcastShow) {
        selectedShow.value = show
        episodes.value = emptyList()
        viewModelScope.launch {
            loadingEpisodes = true
            episodes.value = runCatching { repository.episodes(show) }.getOrDefault(emptyList())
            loadingEpisodes = false
        }
    }

    fun closeShow() {
        selectedShow.value = null
        episodes.value = emptyList()
    }

    fun togglePin(show: PodcastShow) {
        viewModelScope.launch { repository.togglePin(show) }
    }

    fun isPinned(show: PodcastShow): Boolean = pinned.value.any { it.id == show.id }
}
