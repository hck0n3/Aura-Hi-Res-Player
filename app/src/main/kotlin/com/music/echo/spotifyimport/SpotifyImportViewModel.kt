/*
 * EchoMusic (2026)
 * © Chartreux Westia — github.com/koiverse
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package iad1tya.echo.music.spotifyimport

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import iad1tya.echo.music.utils.reportException
import javax.inject.Inject

@HiltViewModel
class SpotifyImportViewModel @Inject constructor(
    private val repository: SpotifyImportRepository,
    private val importManager: SpotifyImportManager,
) : ViewModel() {
    private val _uiState = MutableStateFlow(SpotifyImportUiState(isLoading = true))
    val uiState: StateFlow<SpotifyImportUiState> = _uiState.asStateFlow()

    private var sources: List<SpotifyImportSource> = emptyList()

    init {
        restoreSession()
        // Mirror the background import's progress/summary/error so the screen stays in sync even if it
        // was closed and reopened while the import keeps running in the manager.
        viewModelScope.launch {
            importManager.progress.collect { p -> _uiState.update { it.copy(progress = p) } }
        }
        viewModelScope.launch {
            importManager.summary.collect { s -> _uiState.update { it.copy(summary = s) } }
        }
        viewModelScope.launch {
            importManager.error.collect { e -> if (e != null) _uiState.update { it.copy(errorMessage = e) } }
        }
    }

    fun restoreSession() {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { repository.restoreSession() }
                .onSuccess { session ->
                    _uiState.update {
                        it.copy(
                            isAuthenticated = session.isAuthenticated,
                            accountName = session.accountName,
                            accountAvatarUrl = session.accountAvatarUrl,
                            isLoading = false,
                        )
                    }
                    if (session.isAuthenticated) {
                        loadSources()
                    }
                }
                .onFailure { error ->
                    if (error is CancellationException) throw error
                    reportException(error)
                    _uiState.update {
                        it.copy(
                            isAuthenticated = false,
                            isLoading = false,
                            errorMessage = error.message,
                        )
                    }
                }
        }
    }

    fun connectWithCookies(
        spDc: String,
        spKey: String,
    ) {
        if (spDc.isBlank()) return
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            runCatching { repository.connectWithCookies(spDc = spDc, spKey = spKey) }
                .onSuccess { session ->
                    _uiState.update {
                        it.copy(
                            isAuthenticated = true,
                            accountName = session.accountName,
                            accountAvatarUrl = session.accountAvatarUrl,
                            isLoading = false,
                        )
                    }
                    loadSources()
                }
                .onFailure { error ->
                    if (error is CancellationException) throw error
                    reportException(error)
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = error.message,
                        )
                    }
                }
        }
    }

    fun loadSources() {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            runCatching { repository.loadSources() }
                .onSuccess { loadedSources ->
                    sources = loadedSources
                    val selectedIds = loadedSources.mapTo(LinkedHashSet()) { it.id }
                    _uiState.update {
                        it.copy(
                            isAuthenticated = true,
                            sources = loadedSources.map(SpotifyImportSource::toUi),
                            selectedSourceIds = selectedIds,
                            isLoading = false,
                        )
                    }
                }
                .onFailure { error ->
                    if (error is CancellationException) throw error
                    reportException(error)
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = error.message,
                        )
                    }
                }
        }
    }

    fun toggleSource(sourceId: String) {
        _uiState.update { state ->
            val selected =
                if (sourceId in state.selectedSourceIds) {
                    state.selectedSourceIds - sourceId
                } else {
                    state.selectedSourceIds + sourceId
                }
            state.copy(selectedSourceIds = selected)
        }
    }

    fun selectAllSources() {
        _uiState.update { state ->
            state.copy(selectedSourceIds = state.sources.mapTo(LinkedHashSet()) { it.id })
        }
    }

    fun clearSelection() {
        _uiState.update { it.copy(selectedSourceIds = emptySet()) }
    }

    fun logout() {
        if (uiState.value.progress != null) return
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            runCatching { repository.logout() }
                .onSuccess {
                    sources = emptyList()
                    _uiState.update { SpotifyImportUiState() }
                }
                .onFailure { error ->
                    if (error is CancellationException) throw error
                    reportException(error)
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = error.message,
                        )
                    }
                }
        }
    }

    fun importSelectedSources() {
        val selectedIds = uiState.value.selectedSourceIds
        if (selectedIds.isEmpty() || importManager.isRunning || uiState.value.progress != null) return
        val selectedSources = sources.filter { it.id in selectedIds }
        if (selectedSources.isEmpty()) return
        _uiState.update { it.copy(summary = null, errorMessage = null) }
        // Runs in the manager (process-lifetime scope) so the user can leave this screen and keep
        // using the app; a notification fires when it finishes.
        importManager.start(selectedSources)
    }

    fun cancelImport() {
        importManager.cancel()
        _uiState.update { it.copy(progress = null) }
    }

    fun dismissSummary() {
        importManager.consumeSummary()
        _uiState.update { it.copy(summary = null) }
    }

    fun dismissError() {
        importManager.consumeError()
        _uiState.update { it.copy(errorMessage = null) }
    }
}

private fun SpotifyImportSource.toUi(): SpotifyImportSourceUi =
    SpotifyImportSourceUi(
        id = id,
        title = title,
        subtitle = subtitle,
        thumbnailUrl = thumbnailUrl,
        trackCount = trackCount,
        type = type,
    )
