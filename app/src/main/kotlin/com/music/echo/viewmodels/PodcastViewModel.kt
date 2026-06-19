package iad1tya.echo.music.viewmodels

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import iad1tya.echo.music.podcast.PodcastEpisode
import iad1tya.echo.music.podcast.PodcastRepository
import iad1tya.echo.music.podcast.PodcastShow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PodcastViewModel @Inject constructor(
    private val repository: PodcastRepository,
) : ViewModel() {

    val query = MutableStateFlow("")
    val shows = MutableStateFlow<List<PodcastShow>>(emptyList())
    var searching by mutableStateOf(false)
        private set

    val selectedShow = MutableStateFlow<PodcastShow?>(null)
    val episodes = MutableStateFlow<List<PodcastEpisode>>(emptyList())
    var loadingEpisodes by mutableStateOf(false)
        private set

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
}
