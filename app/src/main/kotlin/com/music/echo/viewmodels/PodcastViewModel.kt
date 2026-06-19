package iad1tya.echo.music.viewmodels

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import iad1tya.echo.music.podcast.PodcastCategory
import iad1tya.echo.music.podcast.PodcastEpisode
import iad1tya.echo.music.podcast.PodcastProgressStore
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
    progressStore: PodcastProgressStore,
) : ViewModel() {

    /** Per-episode listen progress (audio URL -> progress) for resume / "finished" state. */
    val progress = progressStore.progress.stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

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

    /** Region (2-letter, lowercase) used for the trending charts. Defaults to the app's content country. */
    val region = MutableStateFlow("us")

    init {
        viewModelScope.launch {
            // Use the user's saved podcast region if any, else the app's content country.
            region.value = repository.savedRegion() ?: repository.configuredCountry()
            loadTrending()
        }
    }

    fun setRegion(code: String) {
        val c = code.lowercase()
        if (c == region.value) return
        region.value = c
        trending.value = emptyMap()
        viewModelScope.launch { repository.saveRegion(c) }
        loadTrending()
    }

    fun loadTrending() {
        if (trending.value.isNotEmpty()) return
        viewModelScope.launch {
            loadingTrending = true
            val country = region.value.ifBlank { "us" }
            coroutineScope {
                repository.categories.forEach { cat ->
                    launch(Dispatchers.IO) {
                        val result = runCatching { repository.top(cat, country) }.getOrDefault(emptyList())
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
