/*
 * ArchiveTune (2026)
 * © Chartreux Westia — github.com/koiverse
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package iad1tya.echo.music.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import iad1tya.echo.music.constants.ExportedVideoIdsKey
import iad1tya.echo.music.constants.HideExplicitKey
import iad1tya.echo.music.constants.SongSortDescendingKey
import iad1tya.echo.music.constants.SongSortType
import iad1tya.echo.music.constants.SongSortTypeKey
import iad1tya.echo.music.db.MusicDatabase
import iad1tya.echo.music.db.entities.Song
import iad1tya.echo.music.extensions.filterExplicit
import iad1tya.echo.music.extensions.toEnum
import iad1tya.echo.music.localmedia.LocalSongScanConfig
import iad1tya.echo.music.localmedia.LocalSongScanSummary
import iad1tya.echo.music.localmedia.LocalSongScanner
import iad1tya.echo.music.utils.dataStore
import iad1tya.echo.music.utils.reportException
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class LocalSongsViewModel
@Inject
constructor(
    @ApplicationContext context: Context,
    database: MusicDatabase,
    private val localSongScanner: LocalSongScanner,
) : ViewModel() {
    private val _scanState = MutableStateFlow(LocalSongsScanState())
    val scanState = _scanState.asStateFlow()

    val songs = database.localSongs().stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        emptyList(),
    )

    /**
     * MP4 social exports shown as Biblioteca ▸ Local ▸ Vídeos.
     * Ids come from [ExportedVideoIdsKey]; order follows the export list / song sort prefs.
     */
    val exportedVideos =
        context.dataStore.data
            .map {
                Triple(
                    it[SongSortTypeKey].toEnum(SongSortType.CREATE_DATE) to (it[SongSortDescendingKey] ?: true),
                    it[HideExplicitKey] ?: false,
                    it[ExportedVideoIdsKey] ?: "",
                )
            }
            .distinctUntilChanged()
            .flatMapLatest { (sortDesc, hideExplicit, exportedVideoIds) ->
                val (sortType, descending) = sortDesc
                val ids = exportedVideoIds.split(",").filter { it.isNotBlank() }
                if (ids.isEmpty()) return@flatMapLatest flowOf(emptyList<Song>())
                database.getSongsByIdsFlow(ids).map {
                    it.filterExplicit(hideExplicit).sortedAsExported(ids, sortType, descending)
                }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun scanDevice(scanConfig: LocalSongScanConfig = LocalSongScanConfig()) {
        if (_scanState.value.isScanning) return
        viewModelScope.launch(Dispatchers.IO) {
            _scanState.value = _scanState.value.copy(isScanning = true, errorMessage = null)
            runCatching { localSongScanner.scanDevice(scanConfig) }
                .onSuccess { summary ->
                    _scanState.value = LocalSongsScanState(
                        isScanning = false,
                        lastSummary = summary,
                        errorMessage = null,
                    )
                }
                .onFailure { error ->
                    reportException(error)
                    _scanState.value = _scanState.value.copy(
                        isScanning = false,
                        errorMessage = error.message,
                    )
                }
        }
    }
}

data class LocalSongsScanState(
    val isScanning: Boolean = false,
    val lastSummary: LocalSongScanSummary? = null,
    val errorMessage: String? = null,
)
