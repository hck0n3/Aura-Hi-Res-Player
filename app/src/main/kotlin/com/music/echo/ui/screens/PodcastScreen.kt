package iad1tya.echo.music.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import iad1tya.echo.music.constants.CountryCodeToName
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import iad1tya.echo.music.LocalPlayerConnection
import iad1tya.echo.music.R
import iad1tya.echo.music.extensions.toMediaItem
import iad1tya.echo.music.playback.queues.ListQueue
import iad1tya.echo.music.podcast.PodcastShow
import iad1tya.echo.music.podcast.toMediaMetadata
import iad1tya.echo.music.ui.theme.BrandAccent
import iad1tya.echo.music.viewmodels.PodcastViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PodcastScreen(
    navController: NavController,
    viewModel: PodcastViewModel = hiltViewModel(),
) {
    val query by viewModel.query.collectAsState()
    val shows by viewModel.shows.collectAsState()
    val selectedShow by viewModel.selectedShow.collectAsState()
    val episodes by viewModel.episodes.collectAsState()
    val trending by viewModel.trending.collectAsState()
    val pinned by viewModel.pinned.collectAsState()
    val region by viewModel.region.collectAsState()
    var showRegionDialog by remember { mutableStateOf(false) }
    val playerConnection = LocalPlayerConnection.current

    val showPinned = selectedShow?.let { s -> pinned.any { it.id == s.id } } ?: false

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(selectedShow?.title ?: "Podcasts", maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = {
                        if (selectedShow != null) viewModel.closeShow() else navController.navigateUp()
                    }) { Icon(painterResource(R.drawable.arrow_back), contentDescription = null) }
                },
                actions = {
                    selectedShow?.let { s ->
                        IconButton(onClick = { viewModel.togglePin(s) }) {
                            Icon(
                                painter = painterResource(R.drawable.favorite),
                                contentDescription = "Guardar",
                                tint = if (showPinned) BrandAccent else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (selectedShow == null) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { viewModel.query.value = it },
                    label = { Text("Buscar podcast") },
                    leadingIcon = { Icon(painterResource(R.drawable.search), contentDescription = null) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { viewModel.search() }),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                )
                Spacer(Modifier.height(8.dp))

                if (viewModel.searching) {
                    Box(Modifier.fillMaxWidth().padding(24.dp), Alignment.Center) { CircularProgressIndicator() }
                }

                if (query.isNotBlank() && shows.isNotEmpty()) {
                    // Search results
                    LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp)) {
                        items(shows, key = { it.id }) { show ->
                            ShowRow(show) { viewModel.openShow(show) }
                        }
                    }
                } else {
                    // Region filter for the trending catalog (defaults to the app's content country).
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().clickable { showRegionDialog = true }
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                    ) {
                        Icon(painterResource(R.drawable.trending_up), contentDescription = null, modifier = Modifier.size(18.dp), tint = BrandAccent)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Lo más escuchado en: ${CountryCodeToName[region.uppercase()] ?: region.uppercase()}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = BrandAccent,
                        )
                    }
                    // Catalog: saved + trending by category
                    LazyColumn {
                        if (pinned.isNotEmpty()) {
                            item { SectionHeader("Guardados") }
                            item { ShowCarousel(pinned) { viewModel.openShow(it) } }
                        }
                        viewModel.categories.forEach { cat ->
                            val list = trending[cat]
                            if (!list.isNullOrEmpty()) {
                                item(key = "h_${cat.genreId}") { SectionHeader(cat.name) }
                                item(key = "c_${cat.genreId}") { ShowCarousel(list) { viewModel.openShow(it) } }
                            }
                        }
                        if (viewModel.loadingTrending && trending.isEmpty()) {
                            item { Box(Modifier.fillMaxWidth().padding(40.dp), Alignment.Center) { CircularProgressIndicator() } }
                        }
                        item { Spacer(Modifier.height(24.dp)) }
                    }
                }
            } else {
                if (viewModel.loadingEpisodes && episodes.isEmpty()) {
                    Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
                } else if (episodes.isEmpty()) {
                    Box(Modifier.fillMaxSize(), Alignment.Center) {
                        Text("No se pudieron cargar los episodios.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    val bySeason = episodes.groupBy { it.season }
                    LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)) {
                        bySeason.forEach { (season, eps) ->
                            if (season != null) {
                                item(key = "season_$season") { SectionHeader("Temporada $season") }
                            }
                            itemsIndexed(eps, key = { _, ep -> ep.id }) { _, ep ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            val globalIndex = episodes.indexOfFirst { it.id == ep.id }
                                            playerConnection?.playQueue(
                                                ListQueue(
                                                    title = selectedShow?.title,
                                                    items = episodes.map { it.toMediaMetadata().toMediaItem() },
                                                    startIndex = globalIndex.coerceAtLeast(0),
                                                )
                                            )
                                        }
                                        .padding(vertical = 10.dp),
                                ) {
                                    AsyncImage(
                                        model = ep.artworkUrl,
                                        contentDescription = null,
                                        modifier = Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)),
                                    )
                                    Spacer(Modifier.width(12.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(ep.title, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                        val meta = buildString {
                                            ep.episode?.let { append("Ep. $it") }
                                            ep.durationSec?.let { if (isNotEmpty()) append(" · "); append("${it / 60} min") }
                                        }
                                        if (meta.isNotBlank()) {
                                            Text(meta, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                    Icon(painterResource(R.drawable.play), contentDescription = null, modifier = Modifier.size(28.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showRegionDialog) {
        val regions = (listOf("us" to "Estados Unidos") +
            CountryCodeToName.toList().map { it.first.lowercase() to it.second }).distinctBy { it.first }
        AlertDialog(
            onDismissRequest = { showRegionDialog = false },
            title = { Text("Región de los podcasts") },
            text = {
                LazyColumn(modifier = Modifier.height(360.dp)) {
                    items(regions, key = { it.first }) { (code, name) ->
                        Text(
                            text = name,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.setRegion(code)
                                    showRegionDialog = false
                                }
                                .padding(vertical = 12.dp),
                            fontWeight = if (code == region) FontWeight.Bold else FontWeight.Normal,
                            color = if (code == region) BrandAccent else MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showRegionDialog = false }) { Text("Cerrar") } },
        )
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 8.dp),
    )
}

@Composable
private fun ShowCarousel(shows: List<PodcastShow>, onClick: (PodcastShow) -> Unit) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(shows, key = { it.id }) { show ->
            Column(
                modifier = Modifier.width(130.dp).clickable { onClick(show) },
            ) {
                AsyncImage(
                    model = show.artworkUrl,
                    contentDescription = null,
                    modifier = Modifier.size(130.dp).clip(RoundedCornerShape(12.dp)),
                )
                Spacer(Modifier.height(6.dp))
                Text(show.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(show.author, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun ShowRow(show: PodcastShow, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().clickable { onClick() }.padding(vertical = 8.dp),
    ) {
        AsyncImage(
            model = show.artworkUrl,
            contentDescription = null,
            modifier = Modifier.size(56.dp).clip(RoundedCornerShape(8.dp)),
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(show.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(show.author, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
