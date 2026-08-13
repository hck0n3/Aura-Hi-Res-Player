

package iad1tya.echo.music.viewmodels

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.music.innertube.YouTube
import com.music.innertube.models.AlbumItem
import com.music.innertube.models.ArtistItem
import com.music.innertube.models.PlaylistItem
import com.music.innertube.models.YTItem
import iad1tya.echo.music.db.MusicDatabase
import iad1tya.echo.music.utils.reportException
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import iad1tya.echo.music.utils.Wikipedia
import iad1tya.echo.music.utils.AppleMusicAboutAlbum
import javax.inject.Inject

@HiltViewModel
class AlbumViewModel
@Inject
constructor(
    // `private val` (not a bare constructor param) because the fetch now lives in the retryable
    // [load], and a constructor parameter is only in scope for property initializers / init blocks.
    private val database: MusicDatabase,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    val albumId = savedStateHandle.get<String>("albumId")!!
    val playlistId = MutableStateFlow("")
    val albumWithSongs =
        database
            .albumWithSongs(albumId)
            .stateIn(viewModelScope, SharingStarted.Eagerly, null)
    var otherVersions = MutableStateFlow<List<AlbumItem>>(emptyList())
    var releasesForYou = MutableStateFlow<List<AlbumItem>>(emptyList())
    var moreFromArtist = MutableStateFlow<List<AlbumItem>>(emptyList())
    var youMightAlsoLike = MutableStateFlow<List<YTItem>>(emptyList())
    var appearsOn = MutableStateFlow<List<PlaylistItem>>(emptyList())
    var description = MutableStateFlow<String?>(null)
    var descriptionRuns = MutableStateFlow<List<com.music.innertube.models.Run>?>(null)

    // Terminal-failure flag: true once the album fetch stops without having succeeded. The screen uses it
    // to stop the endless spinner and offer Retry, mirroring ArtistItemsViewModel.hasFailed. Irrelevant
    // while Room already has the album — the screen only consults it when there is nothing to render, and
    // it checks [notFound] FIRST because a deleted album is not a connection problem.
    private val _hasFailed = MutableStateFlow(false)
    val hasFailed: StateFlow<Boolean> = _hasFailed

    // Terminal NOT_FOUND: the album does not exist on YouTube any more (and its cached copy was deleted).
    // Distinct from [hasFailed], which means "couldn't reach it" — a connection error with a Retry button
    // is a lie here, because retrying can never succeed. The screen shows a plain "not available" message.
    private val _notFound = MutableStateFlow(false)
    val notFound: StateFlow<Boolean> = _notFound

    private var loadJob: Job? = null

    init {
        load()
    }

    fun load() {
        _hasFailed.value = false
        _notFound.value = false
        // Supersede any in-flight load, so a second Retry tap can't race the first one's terminal write.
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            val album = database.album(albumId).first()
            val hasSongs = database.albumWithSongs(albumId).first()?.songs?.isNotEmpty() == true
            if (album?.description != null) {
                description.value = album.description
            }
            // Retry a transient/throttled cold first fetch (YouTube throttles or cancels the very first
            // network call), mirroring ArtistViewModel. Without this, a throttled cold entry showed an
            // empty album until the user backed out and re-entered ("needs multiple entries to show
            // content"). Room already persists the song list; the only gap was this single un-retried fetch.
            var attempt = 0
            var loaded = false
            // Distinct from [loaded]: a terminal NOT_FOUND also stops the loop, but it is NOT a success.
            var succeeded = false
            // `isActive` in the condition: YouTube.album is runCatching{}-wrapped, so a CANCELLED job gets
            // a plain failure back instead of unwinding and would otherwise burn its remaining attempts.
            while (!loaded && attempt < 3 && isActive) {
            YouTube
                .album(albumId, withSongs = !hasSongs)
                .onSuccess {
                    loaded = true
                    succeeded = true
                    playlistId.value = it.album.playlistId
                    otherVersions.value = it.otherVersions
                    releasesForYou.value = it.releasesForYou
                    if (it.description != null) {
                        description.value = it.description
                    }
                    descriptionRuns.value = it.descriptionRuns
                    database.transaction {
                        if (album == null) {
                            insert(it)
                        } else {
                            update(album.album, it, album.artists)
                        }
                    }

                    val albumArtists = it.album.artists
                    val firstArtistId = albumArtists?.firstOrNull()?.id
                    val singleArtist = albumArtists?.size == 1
                    if (firstArtistId != null) {
                        viewModelScope.launch(Dispatchers.IO) {
                            YouTube.artist(firstArtistId).onSuccess { artistPage ->
                                moreFromArtist.value = artistPage.sections.asSequence()
                                    .flatMap { section -> section.items }
                                    .filterIsInstance<AlbumItem>()
                                    .filter { album -> album.id != albumId }
                                    .distinctBy { album -> album.id }
                                    .take(12)
                                    .toList()
                                appearsOn.value = artistPage.sections.asSequence()
                                    .flatMap { section -> section.items }
                                    .filterIsInstance<PlaylistItem>()
                                    .distinctBy { playlist -> playlist.id }
                                    .take(12)
                                    .toList()
                                val similar = artistPage.sections.asSequence()
                                    .filter { section -> isSimilarArtistSection(section.title) }
                                    .flatMap { section -> section.items }
                                    .distinctBy { item -> item.id }
                                    .take(12)
                                    .toList()
                                youMightAlsoLike.value = similar.ifEmpty {
                                    artistPage.sections.asSequence()
                                        .flatMap { section -> section.items }
                                        .filterIsInstance<ArtistItem>()
                                        .distinctBy { artist -> artist.id }
                                        .take(12)
                                        .toList()
                                }
                                if (singleArtist) {
                                    val artistEntity = database.getArtistById(firstArtistId)
                                    if (artistEntity?.thumbnailUrl == null) {
                                        database.query {
                                            getArtistById(firstArtistId)?.let { currentArtist ->
                                                update(currentArtist, artistPage)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    
                    if (description.value == null && descriptionRuns.value == null) {
                        viewModelScope.launch(Dispatchers.IO) {
                            val artistName = album?.artists?.firstOrNull()?.name 
                                ?: database.albumWithSongs(albumId).first()?.artists?.firstOrNull()?.name
                            val wikiDescription = Wikipedia.fetchAlbumInfo(it.album.title, artistName)
                            if (wikiDescription != null) {
                                description.value = wikiDescription
                                val currentAlbum = database.album(albumId).first()
                                if (currentAlbum != null) {
                                    database.query {
                                        update(currentAlbum.album.copy(description = wikiDescription))
                                    }
                                }
                            } else {
                                val appleDescription = AppleMusicAboutAlbum.fetchAlbumDescription(it.album.title, artistName)
                                if (appleDescription != null) {
                                    description.value = appleDescription
                                    val currentAlbum = database.album(albumId).first()
                                    if (currentAlbum != null) {
                                        database.query {
                                            update(currentAlbum.album.copy(description = appleDescription))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }.onFailure { err ->
                    if (err.message?.contains("NOT_FOUND") == true) {
                        // Album is genuinely gone: terminal, so stop retrying and delete the cached copy.
                        // Flag it as NOT_FOUND rather than letting it fall through to the generic
                        // "check your connection" + Retry, which can never succeed for a deleted album.
                        loaded = true
                        _notFound.value = true
                        reportException(err)
                        val albumToDelete = album?.album
                        if (albumToDelete != null) {
                            database.query {
                                delete(albumToDelete)
                            }
                        }
                    } else if (attempt >= 2) {
                        // Generic/transient failure (throttle/cancel): report only after retries are
                        // exhausted, and crucially do NOT delete or blank the cached album — a cold
                        // throttled fetch must never wipe an album Room already has.
                        reportException(err)
                    }
                }
            if (!loaded) {
                attempt++
                if (attempt < 3) kotlinx.coroutines.delay(700L * attempt)
            }
            }
            // `isActive`: a job superseded by a newer load() returns failure from the runCatching{} wrapper
            // instead of unwinding, and there is no suspension point before this write on the last attempt
            // — so a dead job could flip the screen to the error state while its replacement is running.
            if (isActive) {
                _hasFailed.value = !succeeded
            }
        }
    }

    private fun isSimilarArtistSection(title: String): Boolean {
        val t = title.lowercase()
        return t.contains("similar") ||
            t.contains("also like") ||
            t.contains("fans might") ||
            t.contains("artistas similares") ||
            t.contains("you might") ||
            t.contains("quizá")
    }
}
