package iad1tya.echo.music.ui.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.music.innertube.models.AlbumItem
import com.music.innertube.models.SongItem
import com.music.innertube.models.YTItem
import iad1tya.echo.music.R

/**
 * Speed-dial item: a round cover with the title (and artist) centered BELOW it — a lighter, more
 * modern look than the old square tile with the title overlaid on the artwork.
 */
@Composable
fun SpeedDialGridItem(
    item: YTItem,
    isPinned: Boolean,
    modifier: Modifier = Modifier,
    isActive: Boolean = false,
    isPlaying: Boolean = false,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
        ) {
            ItemThumbnail(
                thumbnailUrl = item.thumbnail,
                isActive = isActive,
                isPlaying = isPlaying,
                shape = CircleShape,
                modifier = Modifier.fillMaxSize()
            )

            if (isPinned) {
                Icon(
                    painter = painterResource(R.drawable.ic_push_pin),
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .size(14.dp)
                )
            }
        }

        Spacer(Modifier.height(6.dp))

        Text(
            text = item.title,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )

        val subtitle = when (item) {
            is SongItem -> item.artists.joinToString { it.name }
            is AlbumItem -> item.artists?.joinToString { it.name }
            else -> null
        }
        if (!subtitle.isNullOrBlank()) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
