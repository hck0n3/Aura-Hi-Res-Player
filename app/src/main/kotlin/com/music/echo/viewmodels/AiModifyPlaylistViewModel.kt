package iad1tya.echo.music.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import iad1tya.echo.music.db.MusicDatabase
import iad1tya.echo.music.db.entities.PlaylistSong
import iad1tya.echo.music.playlistimport.AiPlaylistPlaylistModifier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Drives "edit this playlist with a text instruction" as a strict PLAN → CONFIRM → APPLY flow.
 *
 * [plan] only reads; nothing is written until the user confirms the preview and [apply] runs. Any
 * failure leaves the state at [AiModifyPlaylistUiState.Error] with the playlist untouched (no-op).
 */
@HiltViewModel
class AiModifyPlaylistViewModel
@Inject
constructor(
    private val database: MusicDatabase,
) : ViewModel() {

    private val _state = MutableStateFlow<AiModifyPlaylistUiState>(AiModifyPlaylistUiState.Idle)
    val state: StateFlow<AiModifyPlaylistUiState> = _state.asStateFlow()

    private var job: Job? = null

    /** Asks the AI for an edit and surfaces it as a preview. Read-only: nothing is committed here. */
    fun plan(
        playlistId: String,
        songs: List<PlaylistSong>,
        prompt: String,
        provider: String,
        apiKey: String,
        baseUrl: String,
        model: String,
    ) {
        if (job?.isActive == true) return
        job = viewModelScope.launch(Dispatchers.IO) {
            _state.value = AiModifyPlaylistUiState.Planning
            val result = AiPlaylistPlaylistModifier.plan(
                database = database,
                playlistId = playlistId,
                songs = songs,
                prompt = prompt,
                provider = provider,
                apiKey = apiKey,
                baseUrl = baseUrl,
                model = model,
            )
            _state.value = result.fold(
                onSuccess = { AiModifyPlaylistUiState.Preview(it) },
                // NO-OP on failure: a wrong edit is worse than no edit, so there is no fallback guess.
                onFailure = { AiModifyPlaylistUiState.Error(it) },
            )
        }
    }

    /** Commits a plan the user has explicitly confirmed. */
    fun apply(plan: AiPlaylistPlaylistModifier.Plan) {
        if (job?.isActive == true) return
        job = viewModelScope.launch(Dispatchers.IO) {
            _state.value = AiModifyPlaylistUiState.Applying
            val result = runCatching { AiPlaylistPlaylistModifier.apply(database, plan) }
            _state.value = result.fold(
                onSuccess = {
                    AiModifyPlaylistUiState.Applied(
                        removed = plan.removals.size,
                        added = plan.additions.size,
                    )
                },
                onFailure = { AiModifyPlaylistUiState.Error(it) },
            )
        }
    }

    /** Cancels any in-flight work and returns to [AiModifyPlaylistUiState.Idle]. */
    fun reset() {
        job?.cancel()
        job = null
        _state.value = AiModifyPlaylistUiState.Idle
    }
}

sealed interface AiModifyPlaylistUiState {
    data object Idle : AiModifyPlaylistUiState
    data object Planning : AiModifyPlaylistUiState

    /** The proposed edit, awaiting the user's explicit confirmation. Nothing written yet. */
    data class Preview(val plan: AiPlaylistPlaylistModifier.Plan) : AiModifyPlaylistUiState
    data object Applying : AiModifyPlaylistUiState
    data class Applied(val removed: Int, val added: Int) : AiModifyPlaylistUiState
    data class Error(val cause: Throwable) : AiModifyPlaylistUiState
}
