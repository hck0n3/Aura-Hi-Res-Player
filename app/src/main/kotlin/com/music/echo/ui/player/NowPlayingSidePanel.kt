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
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import iad1tya.echo.music.utils.makeTimeString

/**
 * Spotify-desktop-style persistent NOW-PLAYING panel for the RIGHT side of a wide-screen (TV / tablet / car /
 * unfolded-foldable) layout: while the user browses on the left, the current song's cover + title + a real
 * timeline (seek), like / dislike, and transport stay visible on the right. Tapping the cover opens the full
 * split player. Self-contained (reads LocalPlayerConnection); renders nothing when no song is active, and is
 * only placed by the caller when the screen is genuinely wide — so phones/portrait are never affected. Every
 * control carries the D-pad focus ring (tvFocusable) so a TV remote always shows where it is.
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
    val currentSong by playerConnection.currentSong.collectAsState(initial = null)
    val liked = currentSong?.song?.liked == true
    val disliked by playerConnection.currentSongDisliked.collectAsState()
    val isTvOrCar = rememberIsTvOrCar()

    var sliderPosition by remember { mutableStateOf<Long?>(null) }
    var position by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    // Cheap 500ms position ticker (kept light for low-end / TV). Re-keys per song.
    LaunchedEffect(playerConnection, meta.id) {
        while (true) {
            position = playerConnection.player.currentPosition.coerceAtLeast(0L)
            duration = playerConnection.player.duration.let { if (it > 0L) it else 0L }
            kotlinx.coroutines.delay(500)
        }
    }

    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 16.dp),
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

            Spacer(Modifier.height(16.dp))

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

            Spacer(Modifier.height(14.dp))

            // Timeline / seek — D-pad left/right scrubs when the slider holds focus.
            val safeDuration = duration.coerceAtLeast(1L)
            val shownPosition = (sliderPosition ?: position).coerceIn(0L, safeDuration)
            Slider(
                value = shownPosition.toFloat(),
                valueRange = 0f..safeDuration.toFloat(),
                onValueChange = { sliderPosition = it.toLong() },
                onValueChangeFinished = {
                    sliderPosition?.let { playerConnection.seekTo(it) }
                    sliderPosition = null
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .tvFocusable(isTvOrCar, RoundedCornerShape(50)),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = makeTimeString(shownPosition),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = makeTimeString(duration),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(10.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                IconButton(
                    // Toggle (not one-shot dislike) + live state, parity with the phone player's thumbs.
                    onClick = { playerConnection.toggleDislikeCurrentSong() },
                    modifier = Modifier.tvFocusable(isTvOrCar),
                ) {
                    Icon(
                        painterResource(R.drawable.thumb_down),
                        contentDescription = "No me gusta",
                        tint = if (disliked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(22.dp),
                    )
                }
                IconButton(
                    onClick = { playerConnection.seekToPrevious() },
                    enabled = canSkipPrevious,
                    modifier = Modifier.tvFocusable(isTvOrCar),
                ) {
                    Icon(painterResource(R.drawable.skip_previous), contentDescription = null, modifier = Modifier.size(28.dp))
                }
                FilledIconButton(
                    // PlayerConnection.togglePlayPause() (not the raw player extension) so it routes to the
                    // cast device when casting, matching skip prev/next.
                    onClick = { playerConnection.togglePlayPause() },
                    modifier = Modifier
                        .size(58.dp)
                        .tvFocusable(isTvOrCar),
                ) {
                    Icon(
                        painterResource(if (isPlaying) R.drawable.pause else R.drawable.play),
                        contentDescription = null,
                        modifier = Modifier.size(30.dp),
                    )
                }
                IconButton(
                    onClick = { playerConnection.seekToNext() },
                    enabled = canSkipNext,
                    modifier = Modifier.tvFocusable(isTvOrCar),
                ) {
                    Icon(painterResource(R.drawable.skip_next), contentDescription = null, modifier = Modifier.size(28.dp))
                }
                IconButton(
                    onClick = { playerConnection.toggleLike() },
                    modifier = Modifier.tvFocusable(isTvOrCar),
                ) {
                    Icon(
                        painterResource(if (liked) R.drawable.favorite else R.drawable.favorite_border),
                        contentDescription = "Me gusta",
                        tint = if (liked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
        }
    }
}
