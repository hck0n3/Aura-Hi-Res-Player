package iad1tya.echo.music.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import iad1tya.echo.music.db.MusicDatabase
import iad1tya.echo.music.db.entities.ReleaseRadarItem
import iad1tya.echo.music.releaseradar.ReleaseRadarWorker
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

    // Only this week's drop: releases first seen on/after the most recent Friday. Previous weeks' drops
    // are hidden here and pruned by ReleaseRadarWorker — Spotify Release-Radar style (no endless pile-up).
    val releases: StateFlow<List<ReleaseRadarItem>> =
        database.releasesSince(ReleaseRadarWorker.currentWindowStart())
            .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    init {
        // Opening the screen marks all items as seen.
        viewModelScope.launch {
            database.markAllReleasesSeen()
        }
    }
}
