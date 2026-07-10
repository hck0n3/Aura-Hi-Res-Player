package iad1tya.echo.music.ui.screens.library

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import com.music.innertube.YouTube
import iad1tya.echo.music.LocalPlayerAwareWindowInsets
import iad1tya.echo.music.LocalPlayerConnection
import iad1tya.echo.music.ui.utils.tvFocusRestorer
import iad1tya.echo.music.R
import iad1tya.echo.music.playback.queues.YouTubeAlbumRadio
import iad1tya.echo.music.releaseradar.ReleaseRadarWorker
import iad1tya.echo.music.ui.component.EmptyPlaceholder
import iad1tya.echo.music.utils.reportException
import iad1tya.echo.music.viewmodels.ReleaseRadarViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReleaseRadarScreen(
    navController: NavController,
    viewModel: ReleaseRadarViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val playerConnection = LocalPlayerConnection.current
    val coroutineScope = rememberCoroutineScope()
    val releases by viewModel.releases.collectAsState()
    val lazyListState = rememberLazyListState()

    // No auto-refresh on open: the radar is a weekly Friday drop (rebuilt by ReleaseRadarWorker). A manual
    // refresh is still available via the toolbar action below.

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.release_radar_title)) },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(
                            painter = painterResource(R.drawable.arrow_back),
                            contentDescription = null,
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            ReleaseRadarWorker.runNow(context)
                            Toast.makeText(
                                context,
                                context.getString(R.string.release_radar_refreshing),
                                Toast.LENGTH_SHORT,
                            ).show()
                        },
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.refresh),
                            contentDescription = stringResource(R.string.release_radar_refreshing),
                        )
                    }
                },
            )
        },
        modifier = Modifier.fillMaxSize(),
    ) { paddingValues ->
        LazyColumn(
            state = lazyListState,
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .windowInsetsPadding(LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Bottom))
                .tvFocusRestorer(),
        ) {
            if (releases.isEmpty()) {
                item {
                    EmptyPlaceholder(
                        icon = R.drawable.new_release,
                        text = stringResource(R.string.release_radar_empty),
                    )
                }
            }

            items(
                items = releases,
                key = { it.id },
            ) { item ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            navController.navigate("album/${item.playId}")
                        }
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    AsyncImage(
                        model = item.artworkUri,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(56.dp)
                            .clip(RoundedCornerShape(4.dp)),
                    )

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = item.title,
                            style = MaterialTheme.typography.bodyLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = "${item.artist} · ${item.releaseDate.year}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }

                    // Explicit PLAY affordance (About promise: "el radar puede reproducir cada estreno
                    // completo"). Radar rows are albums/releases (playId = album browseId, no direct song),
                    // so resolve the album's audio playlist and play the WHOLE release as its own queue —
                    // same album-radio path AlbumScreen/NewReleaseScreen use. The row body keeps opening
                    // the album page. Legacy rows can have a blank playId (same guard as
                    // HomeViewModel.getNewFromArtists) — no button for those.
                    if (item.playId.isNotBlank()) IconButton(
                        onClick = {
                            coroutineScope.launch(Dispatchers.IO) {
                                YouTube.album(item.playId)
                                    .onSuccess { albumPage ->
                                        withContext(Dispatchers.Main) {
                                            playerConnection?.playQueue(
                                                YouTubeAlbumRadio(albumPage.album.playlistId),
                                            )
                                        }
                                    }
                                    .onFailure { error ->
                                        reportException(error)
                                        withContext(Dispatchers.Main) {
                                            Toast.makeText(
                                                context,
                                                context.getString(R.string.error_unknown),
                                                Toast.LENGTH_SHORT,
                                            ).show()
                                        }
                                    }
                            }
                        },
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.play),
                            contentDescription = stringResource(R.string.play),
                        )
                    }
                }
            }
        }
    }
}
