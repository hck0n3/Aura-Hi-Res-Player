package iad1tya.echo.music.ui.screens.artist

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.music.innertube.YouTube
import com.music.innertube.models.AlbumItem
import com.music.innertube.models.ArtistItem
import com.music.innertube.models.PlaylistItem
import com.music.innertube.models.SongItem
import com.music.innertube.models.WatchEndpoint
import com.music.innertube.models.YTItem
import iad1tya.echo.music.LocalPlayerAwareWindowInsets
import iad1tya.echo.music.LocalPlayerConnection
import iad1tya.echo.music.R
import iad1tya.echo.music.constants.HideExplicitKey
import iad1tya.echo.music.models.toMediaMetadata
import iad1tya.echo.music.playback.queues.YouTubeQueue
import iad1tya.echo.music.ui.component.IconButton
import iad1tya.echo.music.ui.component.YouTubeGridItem
import iad1tya.echo.music.utils.rememberPreference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Holds the already-loaded items of an artist section so they can be shown in a vertical grid even
 * when YouTube provides no "more" browse endpoint (so the "see all" arrow always works).
 *
 * For synthetic "Videos oficiales" (search-backed, no browse moreEndpoint), [continuation] +
 * [searchQuery] / [artistNameFilter] let the grid paginate via [com.music.innertube.YouTube.searchContinuation].
 */
object ArtistSectionBuffer {
    var title: String = ""
    var items: List<YTItem> = emptyList()
    var continuation: String? = null
    var searchQuery: String? = null
    var artistNameFilter: String? = null

    /** Stashed by [iad1tya.echo.music.viewmodels.ArtistViewModel] when synthesizing Videos oficiales. */
    var videoSearchContinuation: String? = null
    var videoSearchQuery: String? = null
    var videoSearchArtistFilter: String? = null

    fun open(title: String, items: List<YTItem>) {
        this.title = title
        this.items = items
        val officialVideos = title.equals("Videos oficiales", ignoreCase = true)
        if (officialVideos) {
            continuation = videoSearchContinuation
            searchQuery = videoSearchQuery
            artistNameFilter = videoSearchArtistFilter
        } else {
            continuation = null
            searchQuery = null
            artistNameFilter = null
        }
    }

    const val MAX_SEARCH_ITEMS = 200
}

/**
 * Next page of a search-backed artist video shelf. Filters video songs by artist; caps at [ArtistSectionBuffer.MAX_SEARCH_ITEMS].
 */
suspend fun loadArtistVideoSearchContinuation(
    currentItems: List<YTItem>,
    continuation: String,
    artistNameFilter: String?,
    hideExplicit: Boolean,
): Pair<List<YTItem>, String?> = withContext(Dispatchers.IO) {
    val page = YouTube.searchContinuation(continuation).getOrNull()
        ?: return@withContext currentItems to null
    val artist = artistNameFilter
    val added = page.items
        .filterIsInstance<SongItem>()
        .filter { v ->
            v.isVideoSong &&
                (artist.isNullOrBlank() || v.artists.any { it.name.contains(artist, ignoreCase = true) }) &&
                (!hideExplicit || !v.explicit)
        }
    val merged = (currentItems + added).distinctBy { it.id }.take(ArtistSectionBuffer.MAX_SEARCH_ITEMS)
    val next = when {
        merged.size >= ArtistSectionBuffer.MAX_SEARCH_ITEMS -> null
        added.isEmpty() && page.continuation == continuation -> null
        else -> page.continuation
    }
    merged to next
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ArtistAlbumsGridScreen(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
) {
    val playerConnection = LocalPlayerConnection.current ?: return
    val isPlaying by playerConnection.isEffectivelyPlaying.collectAsState()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsState()
    val coroutineScope = rememberCoroutineScope()
    val hideExplicit by rememberPreference(HideExplicitKey, false)

    val title = ArtistSectionBuffer.title
    var items by remember { mutableStateOf(ArtistSectionBuffer.items) }
    var continuation by remember { mutableStateOf(ArtistSectionBuffer.continuation) }
    val artistNameFilter = remember { ArtistSectionBuffer.artistNameFilter }
    var loadingMore by remember { mutableStateOf(false) }
    val gridState = rememberLazyGridState()

    LaunchedEffect(gridState) {
        snapshotFlow {
            val nearEnd = gridState.layoutInfo.visibleItemsInfo.any { it.key == "loading" }
            Triple(nearEnd, continuation, loadingMore)
        }.collect { (shouldLoadMore, token, busy) ->
            if (!shouldLoadMore || token == null || busy ||
                items.size >= ArtistSectionBuffer.MAX_SEARCH_ITEMS
            ) {
                return@collect
            }
            loadingMore = true
            try {
                val (merged, next) = loadArtistVideoSearchContinuation(
                    currentItems = items,
                    continuation = token,
                    artistNameFilter = artistNameFilter,
                    hideExplicit = hideExplicit,
                )
                items = merged
                ArtistSectionBuffer.items = merged
                continuation = next
                ArtistSectionBuffer.continuation = next
            } finally {
                loadingMore = false
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(title, maxLines = 1) },
            navigationIcon = {
                IconButton(onClick = navController::navigateUp, onLongClick = null) {
                    Icon(painter = painterResource(R.drawable.arrow_back), contentDescription = null)
                }
            },
            scrollBehavior = scrollBehavior,
        )
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        // Panel-relative columns (split-screen aware): phone / narrow pane keeps 3; a wider pane adds columns.
        val columns = (maxWidth / 152.dp).toInt().coerceAtLeast(3)
        LazyVerticalGrid(
            state = gridState,
            columns = GridCells.Fixed(columns),
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            // Strip the TOP inset: the Column's own TopAppBar already occupies the app-bar band, so
            // reserving AppBarHeight again in the grid's contentPadding left a big empty band at the top.
            contentPadding = LocalPlayerAwareWindowInsets.current
                .only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom).asPaddingValues(),
        ) {
            items(
                items = items.sortedByDescending { (it as? AlbumItem)?.year ?: 0 },
                key = { it.id }
            ) { item ->
                YouTubeGridItem(
                    item = item,
                    isActive = when (item) {
                        is SongItem -> mediaMetadata?.id == item.id
                        is AlbumItem -> mediaMetadata?.album?.id == item.id
                        else -> false
                    },
                    isPlaying = isPlaying,
                    coroutineScope = coroutineScope,
                    thumbnailRatio = 1f,
                    albumSubtitleYearOnly = true,
                    modifier = Modifier
                        .combinedClickable(
                            onClick = {
                                when (item) {
                                    is SongItem -> playerConnection.playQueue(
                                        YouTubeQueue(WatchEndpoint(videoId = item.id), item.toMediaMetadata())
                                    )
                                    is AlbumItem -> navController.navigate("album/${item.id}")
                                    is ArtistItem -> navController.navigate("artist/${item.id}")
                                    is PlaylistItem -> navController.navigate("online_playlist/${item.id}")
                                }
                            },
                            // Kept as an explicit no-op. Unlike the top-bar buttons, this is
                            // Modifier.combinedClickable, where onLongClick was ALREADY optional —
                            // so the empty lambda here was a choice, not boilerplate forced by a
                            // mandatory parameter. Dropping it would make a long press play the
                            // song / open the album, a content-behaviour change nobody asked for.
                            onLongClick = {},
                        ),
                )
            }
            if (continuation != null) {
                item(span = { GridItemSpan(maxLineSpan) }, key = "loading") {
                    Box(Modifier.height(72.dp))
                }
            }
        }
        }
    }
}
