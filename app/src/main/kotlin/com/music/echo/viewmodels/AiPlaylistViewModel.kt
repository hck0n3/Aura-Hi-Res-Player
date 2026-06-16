package iad1tya.echo.music.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import iad1tya.echo.music.db.MusicDatabase
import iad1tya.echo.music.playlistimport.AiPlaylistGenerator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AiPlaylistViewModel
@Inject
constructor(
    private val database: MusicDatabase,
) : ViewModel() {

    private val _state = MutableStateFlow<AiPlaylistUiState>(AiPlaylistUiState.Idle)
    val state: StateFlow<AiPlaylistUiState> = _state.asStateFlow()

    private var job: Job? = null

    fun generate(
        prompt: String,
        count: Int,
        provider: String,
        apiKey: String,
        baseUrl: String,
        model: String,
    ) {
        if (job?.isActive == true) return
        job = viewModelScope.launch(Dispatchers.IO) {
            _state.value = AiPlaylistUiState.Generating
            val result = AiPlaylistGenerator.generate(
                database = database,
                prompt = prompt,
                count = count,
                provider = provider,
                apiKey = apiKey,
                baseUrl = baseUrl,
                model = model,
                onResolveProgress = { done, total ->
                    _state.value = AiPlaylistUiState.Resolving(done, total)
                },
            )
            _state.value = result.fold(
                onSuccess = {
                    AiPlaylistUiState.Success(
                        playlistId = it.playlistId,
                        name = it.name,
                        resolved = it.resolved,
                        total = it.total,
                    )
                },
                onFailure = { AiPlaylistUiState.Error(it) },
            )
        }
    }

    /** Cancels any in-flight generation and returns to [AiPlaylistUiState.Idle]. */
    fun reset() {
        job?.cancel()
        job = null
        _state.value = AiPlaylistUiState.Idle
    }
}

sealed interface AiPlaylistUiState {
    data object Idle : AiPlaylistUiState
    data object Generating : AiPlaylistUiState
    data class Resolving(val done: Int, val total: Int) : AiPlaylistUiState
    data class Success(
        val playlistId: String,
        val name: String,
        val resolved: Int,
        val total: Int,
    ) : AiPlaylistUiState

    data class Error(val cause: Throwable) : AiPlaylistUiState
}
