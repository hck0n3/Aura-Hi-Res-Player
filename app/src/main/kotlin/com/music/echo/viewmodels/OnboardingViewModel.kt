package iad1tya.echo.music.viewmodels

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.music.innertube.YouTube
import com.music.innertube.models.ArtistItem
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import iad1tya.echo.music.constants.OnboardingArtistsDoneKey
import iad1tya.echo.music.db.MusicDatabase
import iad1tya.echo.music.db.entities.ArtistEntity
import iad1tya.echo.music.utils.dataStore
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import javax.inject.Inject

/**
 * First-run onboarding: search YouTube Music artists, pick the ones the user likes (>= 3), then
 * bookmark them as followed artists so the rest of the app (home/library) can be seeded by their
 * taste. Stored locally, so it survives — and is independent of — signing in later.
 */
@OptIn(FlowPreview::class)
@HiltViewModel
class OnboardingViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: MusicDatabase,
) : ViewModel() {

    val query = MutableStateFlow("")
    val results = MutableStateFlow<List<ArtistItem>>(emptyList())
    val selected = MutableStateFlow<Set<String>>(emptySet())
    private val selectedItems = LinkedHashMap<String, ArtistItem>()
    var searching by mutableStateOf(false)
        private set

    init {
        viewModelScope.launch {
            query.debounce(350).collectLatest { q ->
                if (q.isBlank()) {
                    results.value = emptyList()
                    return@collectLatest
                }
                searching = true
                val r = runCatching {
                    YouTube.search(q, YouTube.SearchFilter.FILTER_ARTIST).getOrNull()
                }.getOrNull()
                results.value = r?.items?.filterIsInstance<ArtistItem>()?.distinctBy { it.id }.orEmpty()
                searching = false
            }
        }
    }

    fun toggle(item: ArtistItem) {
        selectedItems[item.id] = item
        selected.value = if (item.id in selected.value) selected.value - item.id else selected.value + item.id
    }

    /** Bookmark all selected artists and mark onboarding done. */
    suspend fun finish() {
        val now = LocalDateTime.now()
        database.withTransaction {
            selected.value.forEach { id ->
                val item = selectedItems[id] ?: return@forEach
                val existing = database.artist(item.id).first()?.artist
                if (existing == null) {
                    insert(
                        ArtistEntity(
                            id = item.id,
                            name = item.title,
                            thumbnailUrl = item.thumbnail,
                            channelId = item.channelId,
                            bookmarkedAt = now,
                        )
                    )
                } else {
                    update(
                        existing.copy(
                            bookmarkedAt = existing.bookmarkedAt ?: now,
                            lastUpdateTime = now,
                        )
                    )
                }
            }
        }
        context.dataStore.edit { it[OnboardingArtistsDoneKey] = true }
    }
}
