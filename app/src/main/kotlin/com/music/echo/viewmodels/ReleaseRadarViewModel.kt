package iad1tya.echo.music.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import iad1tya.echo.music.db.MusicDatabase
import iad1tya.echo.music.db.entities.ReleaseRadarItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ReleaseRadarViewModel
@Inject
constructor(
    private val database: MusicDatabase,
) : ViewModel() {

    // Read the already-pruned radar table directly (newest first). ReleaseRadarWorker's weekly
    // wipe/prune already enforces "this week only", so filtering again here by fetchedAt >=
    // currentWindowStart() was redundant AND emptied the screen whenever the last successful fetch
    // landed outside the current Friday window (once-per-install seed, or a Doze-deferred worker) —
    // i.e. the "Release Radar always empty" bug for a user who does follow artists.
    val releases: StateFlow<List<ReleaseRadarItem>> =
        database.releasesByDateDesc()
            .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    init {
        // Opening the screen marks all items as seen.
        viewModelScope.launch {
            database.markAllReleasesSeen()
        }
    }
}
