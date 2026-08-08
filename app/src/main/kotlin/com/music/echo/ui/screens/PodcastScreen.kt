package iad1tya.echo.music.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import iad1tya.echo.music.LocalPlayerAwareWindowInsets
import iad1tya.echo.music.LocalPlayerConnection
import iad1tya.echo.music.R
import iad1tya.echo.music.constants.CountryCodeToName
import iad1tya.echo.music.extensions.toMediaItem
import iad1tya.echo.music.models.MediaMetadata
import iad1tya.echo.music.playback.PlayerConnection
import iad1tya.echo.music.playback.queues.ListQueue
import iad1tya.echo.music.podcast.PodcastCategory
import iad1tya.echo.music.podcast.PodcastEpisode
import iad1tya.echo.music.podcast.PodcastProgressStore
import iad1tya.echo.music.podcast.PodcastShow
import iad1tya.echo.music.podcast.toMediaMetadata
import iad1tya.echo.music.ui.component.DefaultDialog
import iad1tya.echo.music.ui.component.ListDialog
import iad1tya.echo.music.ui.newui.AuraCoverCard
import iad1tya.echo.music.ui.newui.AuraDefaultOverflow
import iad1tya.echo.music.ui.newui.AuraIconButton
import iad1tya.echo.music.ui.newui.AuraIconGlyph
import iad1tya.echo.music.ui.newui.AuraIcons
import iad1tya.echo.music.ui.newui.AuraPalette
import iad1tya.echo.music.ui.newui.AuraPanelSkin
import iad1tya.echo.music.ui.newui.AuraSectionHeader
import iad1tya.echo.music.ui.newui.AuraShapes
import iad1tya.echo.music.ui.newui.AuraSpacing
import iad1tya.echo.music.ui.newui.AuraType
import iad1tya.echo.music.ui.newui.auraScreenBackground
import iad1tya.echo.music.ui.newui.rememberAuraBloom
import iad1tya.echo.music.ui.newui.rememberAuraPanelSkin
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
    val progress by viewModel.progress.collectAsState()
    var showRegionDialog by remember { mutableStateOf(false) }
    val playerConnection = LocalPlayerConnection.current

    val showPinned = selectedShow?.let { s -> pinned.any { it.id == s.id } } ?: false

    val skin = rememberAuraPanelSkin()
    val ground = if (skin.enabled && skin.darkGround) AuraPalette.Ground else MaterialTheme.colorScheme.surface
    val accent = if (skin.enabled) skin.accent else BrandAccent

    if (skin.enabled && skin.darkGround) {
        PremiumPodcastScreen(
            navController = navController,
            viewModel = viewModel,
            query = query,
            shows = shows,
            selectedShow = selectedShow,
            episodes = episodes,
            trending = trending,
            pinned = pinned,
            region = region,
            progress = progress,
            showPinned = showPinned,
            showRegionDialog = showRegionDialog,
            onShowRegionDialog = { showRegionDialog = it },
            playerConnection = playerConnection,
        )
        return
    }

    Scaffold(
        containerColor = ground,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        selectedShow?.title ?: stringResource(R.string.podcasts),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontWeight = FontWeight.Bold,
                    )
                },
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
                                tint = if (showPinned) accent
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = ground,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().background(ground).padding(padding)) {
            ClassicPodcastBody(
                viewModel = viewModel,
                query = query,
                shows = shows,
                selectedShow = selectedShow,
                episodes = episodes,
                trending = trending,
                pinned = pinned,
                region = region,
                progress = progress,
                skin = skin,
                accent = accent,
                onShowRegionDialog = { showRegionDialog = true },
                playerConnection = playerConnection,
            )
        }
    }

    if (showRegionDialog && !(skin.enabled && skin.darkGround)) {
        ClassicRegionDialog(
            region = region,
            onSelect = {
                viewModel.setRegion(it)
                showRegionDialog = false
            },
            onDismiss = { showRegionDialog = false },
        )
    }
}

@Composable
private fun PremiumPodcastScreen(
    navController: NavController,
    viewModel: PodcastViewModel,
    query: String,
    shows: List<PodcastShow>,
    selectedShow: PodcastShow?,
    episodes: List<PodcastEpisode>,
    trending: Map<PodcastCategory, List<PodcastShow>>,
    pinned: List<PodcastShow>,
    region: String,
    progress: Map<String, PodcastProgressStore.Progress>,
    showPinned: Boolean,
    showRegionDialog: Boolean,
    onShowRegionDialog: (Boolean) -> Unit,
    playerConnection: PlayerConnection?,
) {
    val mediaMetadata by playerConnection?.mediaMetadata?.collectAsState()
        ?: remember { mutableStateOf(null) }
    val bloom = rememberAuraBloom(mediaMetadata?.id)
    val insets = LocalPlayerAwareWindowInsets.current
    val topInset = remember(insets) { insets.only(WindowInsetsSides.Top) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .auraScreenBackground(bloom, intensity = 0.40f),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(topInset),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 4.dp, end = AuraSpacing.Gutter, top = 8.dp),
            ) {
                AuraIconButton(
                    icon = AuraIcons.ChevronRight,
                    contentDescription = "Atrás",
                    onClick = {
                        if (selectedShow != null) viewModel.closeShow() else navController.navigateUp()
                    },
                    size = 22.dp,
                    tint = AuraPalette.OnGroundMuted,
                    modifier = Modifier.rotate(180f),
                )
                Text(
                    text = selectedShow?.title ?: stringResource(R.string.podcasts),
                    style = AuraType.ScreenTitle,
                    color = AuraPalette.OnGround,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 8.dp),
                )
                selectedShow?.let { s ->
                    IconButton(onClick = { viewModel.togglePin(s) }) {
                        Icon(
                            painter = painterResource(R.drawable.favorite),
                            contentDescription = "Guardar",
                            tint = if (showPinned) AuraPalette.Teal else AuraPalette.OnGroundMuted,
                        )
                    }
                }
            }

            if (selectedShow == null) {
                PodcastSearchField(
                    value = query,
                    onValueChange = { viewModel.query.value = it },
                    onSearch = { viewModel.search() },
                )

                if (viewModel.searching) {
                    Box(Modifier.fillMaxWidth().padding(24.dp), Alignment.Center) {
                        CircularProgressIndicator(color = AuraPalette.Teal)
                    }
                }

                LazyColumn(
                    contentPadding = insets.only(WindowInsetsSides.Bottom).asPaddingValues(),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    if (query.isNotBlank() && shows.isNotEmpty()) {
                        items(shows, key = { it.id }) { show ->
                            PremiumShowRow(show) { viewModel.openShow(show) }
                        }
                    } else {
                        item(key = "region") {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onShowRegionDialog(true) }
                                    .padding(
                                        horizontal = AuraSpacing.Gutter,
                                        vertical = 10.dp,
                                    ),
                            ) {
                                Icon(
                                    painterResource(R.drawable.trending_up),
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                    tint = AuraPalette.Teal,
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    "Lo más escuchado en: ${CountryCodeToName[region.uppercase()] ?: region.uppercase()}",
                                    style = AuraType.RowTitle,
                                    color = AuraPalette.Teal,
                                )
                            }
                        }

                        val continueList = progress.values
                            .filter { !it.finished && it.title.isNotBlank() && it.positionMs > 0 }
                            .sortedByDescending { it.updatedAt }
                            .take(15)
                        if (continueList.isNotEmpty()) {
                            item(key = "continue_h") {
                                AuraSectionHeader(title = "Continuar escuchando")
                            }
                            item(key = "continue_c") {
                                LazyRow(
                                    contentPadding = PaddingValues(horizontal = AuraSpacing.Gutter),
                                    horizontalArrangement = Arrangement.spacedBy(11.dp),
                                    modifier = Modifier.padding(top = AuraSpacing.SectionGap),
                                ) {
                                    items(continueList, key = { it.audioUrl }) { p ->
                                        AuraCoverCard(
                                            title = p.title,
                                            subtitle = "▶ min ${p.positionMs / 60000} · ${p.showTitle}",
                                            thumbnailUrl = p.artworkUrl,
                                            seed = p.audioUrl,
                                            width = 118.dp,
                                            onClick = {
                                                val md = MediaMetadata(
                                                    id = p.audioUrl,
                                                    title = p.title,
                                                    artists = listOf(
                                                        MediaMetadata.Artist(null, p.showTitle),
                                                    ),
                                                    duration = (p.durationMs / 1000).toInt(),
                                                    thumbnailUrl = p.artworkUrl,
                                                )
                                                playerConnection?.playQueue(
                                                    ListQueue(
                                                        title = p.showTitle,
                                                        items = listOf(md.toMediaItem()),
                                                        startIndex = 0,
                                                        position = p.positionMs,
                                                    ),
                                                )
                                            },
                                        )
                                    }
                                }
                            }
                        }

                        if (pinned.isNotEmpty()) {
                            item(key = "pinned_h") {
                                AuraSectionHeader(title = stringResource(R.string.home_your_podcasts))
                            }
                            item(key = "pinned_c") {
                                SavedShowsGrid(
                                    shows = pinned.distinctBy { it.id },
                                    skin = rememberAuraPanelSkin(),
                                    premium = true,
                                ) { viewModel.openShow(it) }
                            }
                        }

                        viewModel.categories.forEach { cat ->
                            val list = trending[cat]
                            if (!list.isNullOrEmpty()) {
                                item(key = "h_${cat.genreId}") {
                                    AuraSectionHeader(title = cat.name)
                                }
                                item(key = "c_${cat.genreId}") {
                                    LazyRow(
                                        contentPadding = PaddingValues(horizontal = AuraSpacing.Gutter),
                                        horizontalArrangement = Arrangement.spacedBy(11.dp),
                                        modifier = Modifier.padding(top = AuraSpacing.SectionGap),
                                    ) {
                                        items(list, key = { it.id }) { show ->
                                            AuraCoverCard(
                                                title = show.title,
                                                subtitle = show.author,
                                                thumbnailUrl = show.artworkUrl,
                                                seed = show.id,
                                                width = 118.dp,
                                                onClick = { viewModel.openShow(show) },
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        if (viewModel.loadingTrending && trending.isEmpty()) {
                            item(key = "loading") {
                                Box(
                                    Modifier.fillMaxWidth().padding(40.dp),
                                    Alignment.Center,
                                ) {
                                    CircularProgressIndicator(color = AuraPalette.Teal)
                                }
                            }
                        }
                        item(key = "spacer") { Spacer(Modifier.height(24.dp)) }
                    }
                }
            } else {
                PremiumEpisodeList(
                    selectedShow = selectedShow,
                    episodes = episodes,
                    progress = progress,
                    loading = viewModel.loadingEpisodes,
                    insets = insets,
                    onPlay = { ep0, resumeMs ->
                        val globalIndex = episodes.indexOfFirst { it.id == ep0.id }
                        viewModel.recordPlay(ep0, selectedShow.feedUrl, selectedShow.artworkUrl)
                        playerConnection?.playQueue(
                            ListQueue(
                                title = selectedShow.title,
                                items = episodes.map { it.toMediaMetadata().toMediaItem() },
                                startIndex = globalIndex.coerceAtLeast(0),
                                position = resumeMs,
                            ),
                        )
                    },
                )
            }
        }
    }

    if (showRegionDialog) {
        ListDialog(onDismiss = { onShowRegionDialog(false) }) {
            item {
                Text(
                    "Región de los podcasts",
                    style = AuraType.ScreenTitle,
                    color = AuraPalette.OnGround,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                )
            }
            val regions = (listOf("us" to "Estados Unidos") +
                CountryCodeToName.toList().map { it.first.lowercase() to it.second })
                .distinctBy { it.first }
            items(regions, key = { it.first }) { (code, name) ->
                Text(
                    text = name,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            viewModel.setRegion(code)
                            onShowRegionDialog(false)
                        }
                        .padding(horizontal = 24.dp, vertical = 12.dp),
                    style = AuraType.RowTitle,
                    color = if (code == region) AuraPalette.Teal else AuraPalette.OnGround,
                    fontWeight = if (code == region) FontWeight.Bold else FontWeight.Normal,
                )
            }
            item {
                TextButton(
                    onClick = { onShowRegionDialog(false) },
                    modifier = Modifier.padding(8.dp),
                ) {
                    Text("Cerrar", color = AuraPalette.Teal)
                }
            }
        }
    }
}

@Composable
private fun PodcastSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    onSearch: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AuraSpacing.Gutter, vertical = 10.dp)
            .clip(AuraShapes.Card)
            .background(AuraPalette.SurfaceFill)
            .border(1.dp, AuraPalette.SurfaceLine, AuraShapes.Card)
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        AuraIconGlyph(
            icon = AuraIcons.Search,
            contentDescription = null,
            size = 16.dp,
            tint = AuraPalette.OnGroundFaint,
        )
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (value.isEmpty()) {
                Text(
                    text = "Buscar podcast",
                    style = AuraType.RowSubtitle,
                    color = AuraPalette.OnGroundGhost,
                    maxLines = 1,
                    overflow = AuraDefaultOverflow,
                )
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = AuraType.RowSubtitle.copy(color = AuraPalette.OnGround),
                cursorBrush = SolidColor(AuraPalette.Teal),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSearch() }),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (value.isNotEmpty()) {
            AuraIconButton(
                icon = AuraIcons.Plus,
                contentDescription = "Borrar la búsqueda",
                onClick = { onValueChange("") },
                size = 16.dp,
                tint = AuraPalette.OnGroundFaint,
                modifier = Modifier.rotate(45f),
            )
        }
    }
}

@Composable
private fun PremiumShowRow(show: PodcastShow, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = AuraSpacing.Gutter, vertical = 10.dp),
    ) {
        AsyncImage(
            model = show.artworkUrl,
            contentDescription = null,
            modifier = Modifier
                .size(56.dp)
                .clip(AuraShapes.Artwork),
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                show.title,
                style = AuraType.RowTitle,
                color = AuraPalette.OnGround,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                show.author,
                style = AuraType.RowSubtitle,
                color = AuraPalette.OnGroundMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun PremiumEpisodeList(
    selectedShow: PodcastShow,
    episodes: List<PodcastEpisode>,
    progress: Map<String, PodcastProgressStore.Progress>,
    loading: Boolean,
    insets: androidx.compose.foundation.layout.WindowInsets,
    onPlay: (PodcastEpisode, Long) -> Unit,
) {
    when {
        loading && episodes.isEmpty() -> {
            Box(Modifier.fillMaxSize(), Alignment.Center) {
                CircularProgressIndicator(color = AuraPalette.Teal)
            }
        }
        episodes.isEmpty() -> {
            Box(Modifier.fillMaxSize(), Alignment.Center) {
                Text(
                    "No se pudieron cargar los episodios.",
                    color = AuraPalette.OnGroundMuted,
                )
            }
        }
        else -> {
            val bySeason = episodes.groupBy { it.season }
            LazyColumn(
                contentPadding = PaddingValues(
                    start = AuraSpacing.Gutter,
                    end = AuraSpacing.Gutter,
                    top = 8.dp,
                    bottom = insets.only(WindowInsetsSides.Bottom).asPaddingValues().calculateBottomPadding(),
                ),
            ) {
                bySeason.forEach { (season, eps) ->
                    if (season != null) {
                        item(key = "season_$season") {
                            AuraSectionHeader(
                                title = "Temporada $season",
                                modifier = Modifier.padding(horizontal = 0.dp),
                            )
                        }
                    }
                    itemsIndexed(eps, key = { _, ep -> ep.id }) { _, ep ->
                        val prog = progress[ep.id]
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    val resumeMs = prog?.takeIf { !it.finished }?.positionMs ?: 0L
                                    onPlay(ep, resumeMs)
                                }
                                .padding(vertical = 10.dp),
                        ) {
                            AsyncImage(
                                model = ep.artworkUrl ?: selectedShow.artworkUrl,
                                contentDescription = null,
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(AuraShapes.Artwork),
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    ep.title,
                                    style = AuraType.RowTitle,
                                    color = AuraPalette.OnGround,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                val meta = buildString {
                                    ep.episode?.let { append("Ep. $it") }
                                    ep.durationSec?.let {
                                        if (isNotEmpty()) append(" · ")
                                        append("${it / 60} min")
                                    }
                                }
                                val stateText = when {
                                    prog?.finished == true -> "✓ Finalizado"
                                    prog != null && prog.positionMs > 0 ->
                                        "▶ Continuar (min ${prog.positionMs / 60000})"
                                    else -> null
                                }
                                if (meta.isNotBlank()) {
                                    Text(
                                        meta,
                                        style = AuraType.CalloutSubtitle,
                                        color = AuraPalette.OnGroundMuted,
                                    )
                                }
                                if (stateText != null) {
                                    Text(
                                        stateText,
                                        style = AuraType.CalloutSubtitle,
                                        color = AuraPalette.Teal,
                                    )
                                }
                            }
                            AuraIconGlyph(
                                icon = AuraIcons.Play,
                                contentDescription = null,
                                size = 22.dp,
                                tint = AuraPalette.OnGround,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ClassicPodcastBody(
    viewModel: PodcastViewModel,
    query: String,
    shows: List<PodcastShow>,
    selectedShow: PodcastShow?,
    episodes: List<PodcastEpisode>,
    trending: Map<PodcastCategory, List<PodcastShow>>,
    pinned: List<PodcastShow>,
    region: String,
    progress: Map<String, PodcastProgressStore.Progress>,
    skin: AuraPanelSkin,
    accent: Color,
    onShowRegionDialog: () -> Unit,
    playerConnection: PlayerConnection?,
) {
    if (selectedShow == null) {
        OutlinedTextField(
            value = query,
            onValueChange = { viewModel.query.value = it },
            label = { Text("Buscar podcast") },
            leadingIcon = { Icon(painterResource(R.drawable.search), contentDescription = null) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { viewModel.search() }),
            colors = OutlinedTextFieldDefaults.colors(),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        )
        Spacer(Modifier.height(8.dp))

        if (viewModel.searching) {
            Box(Modifier.fillMaxWidth().padding(24.dp), Alignment.Center) {
                CircularProgressIndicator()
            }
        }

        if (query.isNotBlank() && shows.isNotEmpty()) {
            LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp)) {
                items(shows, key = { it.id }) { show ->
                    ShowRow(show, skin) { viewModel.openShow(show) }
                }
            }
        } else {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onShowRegionDialog)
                    .padding(horizontal = 16.dp, vertical = 6.dp),
            ) {
                Icon(
                    painterResource(R.drawable.trending_up),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = accent,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "Lo más escuchado en: ${CountryCodeToName[region.uppercase()] ?: region.uppercase()}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = accent,
                )
            }
            LazyColumn {
                val continueList = progress.values
                    .filter { !it.finished && it.title.isNotBlank() && it.positionMs > 0 }
                    .sortedByDescending { it.updatedAt }
                    .take(15)
                if (continueList.isNotEmpty()) {
                    item { SectionHeader("Continuar escuchando", skin) }
                    item {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            items(continueList, key = { it.audioUrl }) { p ->
                                ContinueCard(p, skin) {
                                    val md = MediaMetadata(
                                        id = p.audioUrl,
                                        title = p.title,
                                        artists = listOf(MediaMetadata.Artist(null, p.showTitle)),
                                        duration = (p.durationMs / 1000).toInt(),
                                        thumbnailUrl = p.artworkUrl,
                                    )
                                    playerConnection?.playQueue(
                                        ListQueue(
                                            title = p.showTitle,
                                            items = listOf(md.toMediaItem()),
                                            startIndex = 0,
                                            position = p.positionMs,
                                        ),
                                    )
                                }
                            }
                        }
                    }
                }
                if (pinned.isNotEmpty()) {
                    item { SectionHeader("Guardados", skin) }
                    item {
                        SavedShowsGrid(pinned.distinctBy { it.id }, skin) { viewModel.openShow(it) }
                    }
                }
                viewModel.categories.forEach { cat ->
                    val list = trending[cat]
                    if (!list.isNullOrEmpty()) {
                        item(key = "h_${cat.genreId}") { SectionHeader(cat.name, skin) }
                        item(key = "c_${cat.genreId}") {
                            ShowCarousel(list, skin) { viewModel.openShow(it) }
                        }
                    }
                }
                if (viewModel.loadingTrending && trending.isEmpty()) {
                    item {
                        Box(Modifier.fillMaxWidth().padding(40.dp), Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    }
                }
                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    } else {
        if (viewModel.loadingEpisodes && episodes.isEmpty()) {
            Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
        } else if (episodes.isEmpty()) {
            Box(Modifier.fillMaxSize(), Alignment.Center) {
                Text(
                    "No se pudieron cargar los episodios.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            val bySeason = episodes.groupBy { it.season }
            LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)) {
                bySeason.forEach { (season, eps) ->
                    if (season != null) {
                        item(key = "season_$season") { SectionHeader("Temporada $season", skin) }
                    }
                    itemsIndexed(eps, key = { _, ep -> ep.id }) { _, ep ->
                        val prog = progress[ep.id]
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    val globalIndex = episodes.indexOfFirst { it.id == ep.id }
                                    val resumeMs = prog?.takeIf { !it.finished }?.positionMs ?: 0L
                                    viewModel.recordPlay(
                                        ep,
                                        selectedShow.feedUrl,
                                        selectedShow.artworkUrl,
                                    )
                                    playerConnection?.playQueue(
                                        ListQueue(
                                            title = selectedShow.title,
                                            items = episodes.map { it.toMediaMetadata().toMediaItem() },
                                            startIndex = globalIndex.coerceAtLeast(0),
                                            position = resumeMs,
                                        ),
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
                                Text(
                                    ep.title,
                                    style = MaterialTheme.typography.bodyLarge,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                val meta = buildString {
                                    ep.episode?.let { append("Ep. $it") }
                                    ep.durationSec?.let {
                                        if (isNotEmpty()) append(" · ")
                                        append("${it / 60} min")
                                    }
                                }
                                val stateText = when {
                                    prog?.finished == true -> "✓ Finalizado"
                                    prog != null && prog.positionMs > 0 ->
                                        "▶ Continuar (min ${prog.positionMs / 60000})"
                                    else -> null
                                }
                                if (meta.isNotBlank()) {
                                    Text(meta, style = MaterialTheme.typography.bodySmall)
                                }
                                if (stateText != null) {
                                    Text(
                                        stateText,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = accent,
                                    )
                                }
                            }
                            Icon(
                                painterResource(R.drawable.play),
                                contentDescription = null,
                                modifier = Modifier.size(28.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ClassicRegionDialog(
    region: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val regions = (listOf("us" to "Estados Unidos") +
        CountryCodeToName.toList().map { it.first.lowercase() to it.second }).distinctBy { it.first }
    DefaultDialog(
        onDismiss = onDismiss,
        title = { Text("Región de los podcasts") },
        buttons = {
            TextButton(onClick = onDismiss) { Text("Cerrar") }
        },
    ) {
        LazyColumn(modifier = Modifier.height(360.dp)) {
            items(regions, key = { it.first }) { (code, name) ->
                Text(
                    text = name,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelect(code) }
                        .padding(vertical = 12.dp),
                    fontWeight = if (code == region) FontWeight.Bold else FontWeight.Normal,
                    color = if (code == region) BrandAccent else MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String, skin: AuraPanelSkin = rememberAuraPanelSkin()) {
    Text(
        text = text,
        style = if (skin.enabled) AuraType.MenuGroupLabel else MaterialTheme.typography.titleMedium,
        fontWeight = if (skin.enabled) FontWeight.Normal else FontWeight.Bold,
        color = if (skin.enabled) skin.inkFaint else Color.Unspecified,
        modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 8.dp),
    )
}

@Composable
private fun ContinueCard(
    p: PodcastProgressStore.Progress,
    skin: AuraPanelSkin = rememberAuraPanelSkin(),
    onClick: () -> Unit,
) {
    val accent = if (skin.enabled) skin.accent else BrandAccent
    Column(modifier = Modifier.width(150.dp).clickable { onClick() }) {
        PodcastArtwork(p.artworkUrl, 150.dp, skin = skin)
        Spacer(Modifier.height(6.dp))
        Text(
            p.title,
            style = if (skin.enabled) AuraType.RowTitle else MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = if (skin.enabled) skin.ink else Color.Unspecified,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            p.showTitle,
            style = if (skin.enabled) AuraType.CalloutSubtitle else MaterialTheme.typography.bodySmall,
            color = if (skin.enabled) skin.inkMuted else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            "▶ min ${p.positionMs / 60000}",
            style = if (skin.enabled) AuraType.CalloutSubtitle else MaterialTheme.typography.bodySmall,
            color = accent,
        )
    }
}

@Composable
private fun ShowCarousel(
    shows: List<PodcastShow>,
    skin: AuraPanelSkin = rememberAuraPanelSkin(),
    onClick: (PodcastShow) -> Unit,
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(shows, key = { it.id }) { show ->
            Column(
                modifier = Modifier.width(130.dp).clickable { onClick(show) },
            ) {
                PodcastArtwork(show.artworkUrl, 130.dp, skin = skin)
                Spacer(Modifier.height(6.dp))
                Text(
                    show.title,
                    style = if (skin.enabled) AuraType.RowTitle else MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (skin.enabled) skin.ink else Color.Unspecified,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    show.author,
                    style = if (skin.enabled) AuraType.CalloutSubtitle else MaterialTheme.typography.bodySmall,
                    color = if (skin.enabled) skin.inkMuted else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun PodcastArtwork(
    url: String?,
    size: androidx.compose.ui.unit.Dp,
    corner: androidx.compose.ui.unit.Dp = 12.dp,
    skin: AuraPanelSkin = rememberAuraPanelSkin(),
) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(corner))
            .background(if (skin.enabled) skin.fill else MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        if (!url.isNullOrBlank()) {
            AsyncImage(
                model = url,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Icon(
                painterResource(R.drawable.queue_music),
                contentDescription = null,
                tint = if (skin.enabled) skin.inkMuted else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(size * 0.4f),
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SavedShowsGrid(
    shows: List<PodcastShow>,
    skin: AuraPanelSkin = rememberAuraPanelSkin(),
    premium: Boolean = false,
    onClick: (PodcastShow) -> Unit,
) {
    if (premium) {
        FlowRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AuraSpacing.Gutter)
                .padding(top = AuraSpacing.SectionGap),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            shows.forEach { show ->
                AuraCoverCard(
                    title = show.title,
                    thumbnailUrl = show.artworkUrl,
                    seed = show.id,
                    width = 104.dp,
                    onClick = { onClick(show) },
                )
            }
        }
    } else {
        FlowRow(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            shows.forEach { show ->
                Column(modifier = Modifier.width(104.dp).clickable { onClick(show) }) {
                    PodcastArtwork(show.artworkUrl, 104.dp, skin = skin)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        show.title,
                        style = if (skin.enabled) AuraType.CalloutSubtitle else MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = if (skin.enabled) skin.ink else Color.Unspecified,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun ShowRow(
    show: PodcastShow,
    skin: AuraPanelSkin = rememberAuraPanelSkin(),
    onClick: () -> Unit,
) {
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
            Text(
                show.title,
                style = if (skin.enabled) AuraType.RowTitle else MaterialTheme.typography.titleMedium,
                color = if (skin.enabled) skin.ink else Color.Unspecified,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                show.author,
                style = if (skin.enabled) AuraType.RowSubtitle else MaterialTheme.typography.bodySmall,
                color = if (skin.enabled) skin.inkMuted else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
