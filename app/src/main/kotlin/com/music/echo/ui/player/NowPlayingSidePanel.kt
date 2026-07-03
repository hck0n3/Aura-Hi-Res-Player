package iad1tya.echo.music.ui.player

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import iad1tya.echo.music.LocalPlayerConnection
import iad1tya.echo.music.R
import iad1tya.echo.music.ui.utils.rememberIsTvOrCar
import iad1tya.echo.music.ui.utils.tvFocusable

/**
 * Spotify-desktop-style persistent NOW-PLAYING panel for the RIGHT side of a wide-screen (TV / tablet / car /
 * unfolded-foldable) layout: while the user browses on the left, the current song's cover + title + transport
 * stay visible on the right. Tapping the cover opens the full split player. Self-contained (reads
 * LocalPlayerConnection); renders nothing when no song is active, and is only placed by the caller when the
 * screen is genuinely wide enough — so phones/portrait are never affected.
 */
@Composable
fun NowPlayingSidePanel(
    onExpand: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val playerConnection = LocalPlayerConnection.current ?: return
    val mediaMetadata by playerConnection.mediaMetadata.collectAsState()
    val meta = mediaMetadata ?: return
    val isPlaying by playerConnection.isPlaying.collectAsState()
    val canSkipNext by playerConnection.canSkipNext.collectAsState()
    val canSkipPrevious by playerConnection.canSkipPrevious.collectAsState()
    val isTvOrCar = rememberIsTvOrCar()

    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            AsyncImage(
                model = meta.thumbnailUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(12.dp))
                    // tvFocusable BEFORE clickable so its onFocusChanged is an ANCESTOR of the clickable focus
                    // target and the D-pad ring actually lights (descendant order would never observe it).
                    .tvFocusable(isTvOrCar, RoundedCornerShape(12.dp))
                    .clickable { onExpand() },
            )

            Spacer(Modifier.height(18.dp))

            Text(
                text = meta.title,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = meta.artists.joinToString { it.name },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(20.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                IconButton(
                    onClick = { playerConnection.seekToPrevious() },
                    enabled = canSkipPrevious,
                    modifier = Modifier.tvFocusable(isTvOrCar),
                ) {
                    Icon(painterResource(R.drawable.skip_previous), contentDescription = null, modifier = Modifier.size(30.dp))
                }
                FilledIconButton(
                    // PlayerConnection.togglePlayPause() (not the raw player extension) so it routes to the
                    // cast device when casting, matching skip prev/next below.
                    onClick = { playerConnection.togglePlayPause() },
                    modifier = Modifier
                        .size(60.dp)
                        .tvFocusable(isTvOrCar),
                ) {
                    Icon(
                        painterResource(if (isPlaying) R.drawable.pause else R.drawable.play),
                        contentDescription = null,
                        modifier = Modifier.size(32.dp),
                    )
                }
                IconButton(
                    onClick = { playerConnection.seekToNext() },
                    enabled = canSkipNext,
                    modifier = Modifier.tvFocusable(isTvOrCar),
                ) {
                    Icon(painterResource(R.drawable.skip_next), contentDescription = null, modifier = Modifier.size(30.dp))
                }
            }
        }
    }
}
