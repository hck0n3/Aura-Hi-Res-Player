package iad1tya.echo.music.ui.screens.playlist

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.collectAsState
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import com.music.innertube.YouTube
import com.music.innertube.models.SongItem
import com.music.innertube.models.WatchEndpoint
import iad1tya.echo.music.R
import iad1tya.echo.music.db.entities.ArtistEntity
import iad1tya.echo.music.models.toMediaMetadata
import iad1tya.echo.music.playback.PlayerConnection
import iad1tya.echo.music.playback.queues.YouTubeQueue
import iad1tya.echo.music.ui.component.SongListItem
import iad1tya.echo.music.ui.component.shimmer.ListItemPlaceHolder
import iad1tya.echo.music.ui.component.shimmer.ShimmerHost
import iad1tya.echo.music.ui.utils.resize
import iad1tya.echo.music.utils.listItemShape
import iad1tya.echo.music.viewmodels.LocalPlaylistViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Prominent, full-width Apple-Music-style "Add Music" button. */
@Composable
fun AddMusicButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Button(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .height(52.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
        ),
    ) {
        Icon(
            painter = painterResource(R.drawable.add),
            contentDescription = null,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = stringResource(R.string.add_music),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
    }
}

/**
 * Trailing "+" that adds the row's song instantly and flips to a check once added (Apple-Music-style
 * instant feedback). The check is also shown for a de-duped add — either way the song IS in the playlist.
 */
@Composable
fun AddOrAddedButton(
    added: Boolean,
    onAdd: () -> Unit,
) {
    if (added) {
        IconButton(onClick = {}, enabled = false) {
            Icon(
                painter = painterResource(R.drawable.done),
                contentDescription = stringResource(R.string.done),
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    } else {
        IconButton(onClick = onAdd) {
            Icon(
                painter = painterResource(R.drawable.add),
                contentDescription = stringResource(R.string.add_music),
            )
        }
    }
}

/**
 * "Suggested Songs" footer section: a header with a refresh button, plus 5 rows recommended from the
 * playlist's own content. Tapping the ROW (or title) PLAYS the song in the main player; tapping the
 * ARTWORK previews it in place (Apple behavior: artwork tap toggles the preview); the trailing "+" adds
 * the song to the playlist immediately. While the first (instant local-first) batch is still computing,
 * shimmer placeholders show instead of a blank.
 */
@Composable
fun SuggestedSongsSection(
    viewModel: LocalPlaylistViewModel,
    previewController: SongPreviewController,
    playerConnection: PlayerConnection,
    modifier: Modifier = Modifier,
) {
    val suggestions by viewModel.suggestedSongs.collectAsState()
    val isRefreshing by viewModel.isRefreshingSuggestions.collectAsState()
    val loaded by viewModel.suggestionsLoaded.collectAsState()
    val addedIds = remember { mutableStateListOf<String>() }
    val coroutineScope = rememberCoroutineScope()

    // Show shimmer while the first batch is still being computed OR a refresh top-up is in flight and we
    // have nothing yet — never a bare blank. Once loaded and genuinely empty, collapse the section.
    val showShimmer = suggestions.isEmpty() && (!loaded || isRefreshing)
    if (suggestions.isEmpty() && !showShimmer) return

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = stringResource(R.string.suggested_songs),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            IconButton(
                onClick = { viewModel.refreshSuggestions() },
                enabled = !isRefreshing,
            ) {
                Icon(
                    painter = painterResource(R.drawable.refresh),
                    contentDescription = stringResource(R.string.refresh),
                )
            }
        }

        if (showShimmer) {
            ShimmerHost {
                repeat(5) { ListItemPlaceHolder() }
            }
            return@Column
        }

        suggestions.forEachIndexed { index, song ->
            val isPreviewing = previewController.currentPreviewId == song.id
            SongListItem(
                song = song,
                isActive = isPreviewing,
                isPlaying = isPreviewing && !previewController.isLoading,
                showDownloadIcon = false,
                shape = listItemShape(index = index, count = suggestions.size),
                trailingContent = {
                    AddOrAddedButton(
                        added = song.id in addedIds,
                        onAdd = {
                            viewModel.addLocalSongs(listOf(song.id)) { addedIds.add(song.id) }
                        },
                    )
                },
                // Artwork tap = PREVIEW (Apple behavior). Row/title tap = PLAY in the main player.
                onThumbnailClick = { previewController.toggle(song.id) },
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        // Real playback must stop any active preview so two songs don't play at once.
                        previewController.stop()
                        val videoId = song.id
                        if (videoId.isNotBlank()) {
                            // Play by videoId — reliable and seeds radio (footer items are real DB rows,
                            // online-fallback ones already resolved to song.id == videoId before showing).
                            playerConnection.playQueue(
                                YouTubeQueue(WatchEndpoint(videoId = videoId), song.toMediaMetadata()),
                            )
                        } else {
                            // Extremely rare (a DB row always has a videoId key); fall back to search.
                            coroutineScope.launch(Dispatchers.IO) {
                                val match = YouTube
                                    .search(
                                        "${song.title} ${song.artists.joinToString { it.name }}",
                                        YouTube.SearchFilter.FILTER_SONG,
                                    )
                                    .getOrNull()
                                    ?.items
                                    ?.filterIsInstance<SongItem>()
                                    ?.firstOrNull()
                                if (match != null) {
                                    withContext(Dispatchers.Main) {
                                        playerConnection.playQueue(
                                            YouTubeQueue(
                                                WatchEndpoint(videoId = match.id),
                                                match.toMediaMetadata(),
                                            ),
                                        )
                                    }
                                }
                            }
                        }
                    },
            )
        }
    }
}

/**
 * "Featured Artists" footer section: a horizontal row of circular artist avatars (the distinct artists
 * appearing across the playlist, most-frequent first). Tapping navigates to the artist screen.
 */
@Composable
fun FeaturedArtistsSection(
    viewModel: LocalPlaylistViewModel,
    navController: NavController,
    modifier: Modifier = Modifier,
) {
    val artists by viewModel.featuredArtists.collectAsState()

    if (artists.isEmpty()) return

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.featured_artists),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 8.dp),
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            items(artists, key = { it.id }) { artist ->
                FeaturedArtistItem(
                    artist = artist,
                    onClick = { navController.navigate("artist/${artist.id}") },
                )
            }
        }
    }
}

@Composable
private fun FeaturedArtistItem(
    artist: ArtistEntity,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .width(84.dp)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.size(72.dp),
        ) {
            if (artist.thumbnailUrl != null) {
                AsyncImage(
                    model = artist.thumbnailUrl.resize(544, 544),
                    contentDescription = artist.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape),
                )
            } else {
                Icon(
                    painter = painterResource(R.drawable.artist),
                    contentDescription = null,
                    modifier = Modifier
                        .padding(20.dp)
                        .size(32.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = artist.name,
            style = MaterialTheme.typography.labelMedium,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
