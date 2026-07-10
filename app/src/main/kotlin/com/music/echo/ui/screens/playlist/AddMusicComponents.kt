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
import iad1tya.echo.music.R
import iad1tya.echo.music.db.entities.ArtistEntity
import iad1tya.echo.music.ui.component.SongListItem
import iad1tya.echo.music.ui.utils.resize
import iad1tya.echo.music.utils.listItemShape
import iad1tya.echo.music.viewmodels.LocalPlaylistViewModel

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
 * "Suggested Songs" footer section: a header with a refresh button, plus 5 rows recommended from the
 * playlist's own content. Each row previews in place on tap and has a "+" to add it to the playlist.
 */
@Composable
fun SuggestedSongsSection(
    viewModel: LocalPlaylistViewModel,
    previewController: SongPreviewController,
    modifier: Modifier = Modifier,
) {
    val suggestions by viewModel.suggestedSongs.collectAsState()

    if (suggestions.isEmpty()) return

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
            IconButton(onClick = { viewModel.refreshSuggestions() }) {
                Icon(
                    painter = painterResource(R.drawable.refresh),
                    contentDescription = stringResource(R.string.refresh),
                )
            }
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
                    IconButton(onClick = { viewModel.addLocalSongs(listOf(song.id)) }) {
                        Icon(
                            painter = painterResource(R.drawable.add),
                            contentDescription = stringResource(R.string.add_music),
                        )
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { previewController.toggle(song.id) },
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
