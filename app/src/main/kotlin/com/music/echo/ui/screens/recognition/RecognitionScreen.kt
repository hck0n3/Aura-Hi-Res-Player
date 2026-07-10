

package iad1tya.echo.music.ui.screens.recognition

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import com.music.innertube.YouTube
import com.music.innertube.models.SongItem
import iad1tya.echo.music.LocalDatabase
import iad1tya.echo.music.LocalPlayerConnection
import iad1tya.echo.music.LocalSyncUtils
import iad1tya.echo.music.R
import iad1tya.echo.music.models.toMediaMetadata
import iad1tya.echo.music.ui.component.IconButton
import iad1tya.echo.music.ui.menu.AddToPlaylistDialog
import iad1tya.echo.music.ui.screens.search.suggestions.SuggestionTrack
import iad1tya.echo.music.ui.screens.search.suggestions.SuggestionsViewModel
import iad1tya.echo.music.ui.utils.backToMain
import com.music.shazamkit.models.RecognitionResult
import com.music.shazamkit.models.RecognitionStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecognitionScreen(
    navController: NavController,
    autoStart: Boolean = false
) {
    val context = LocalContext.current
    val database = LocalDatabase.current
    val playerConnection = LocalPlayerConnection.current
    val syncUtils = LocalSyncUtils.current
    val suggestionsViewModel: SuggestionsViewModel = hiltViewModel()


    // Only reset when no session is live — otherwise entering the screen mid-recognition
    // (e.g. started from the Quick Settings tile) would clobber the Listening/Processing state.
    // A retained terminal Success/NoMatch (headless tile/notification flow finished while the app
    // was closed) must be shown, not clobbered: only clear Ready or a stale Error.
    LaunchedEffect(Unit) {
        if (!iad1tya.echo.music.recognition.MusicRecognitionService.isInProgress()) {
            when (iad1tya.echo.music.recognition.MusicRecognitionService.recognitionStatus.value) {
                is RecognitionStatus.Success, is RecognitionStatus.NoMatch -> Unit
                else -> iad1tya.echo.music.recognition.MusicRecognitionService.reset()
            }
        }
    }


    DisposableEffect(Unit) {
        onDispose {
            // Leaving the screen mid-recognition is fine: the mic foreground service keeps the session
            // alive and shows progress in its notification, so don't reset a live session.
            if (!iad1tya.echo.music.recognition.MusicRecognitionService.isInProgress()) {
                iad1tya.echo.music.recognition.MusicRecognitionService.reset()
            }
        }
    }


    val recognitionStatus by iad1tya.echo.music.recognition.MusicRecognitionService.recognitionStatus.collectAsState()

    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasPermission = isGranted
        if (isGranted) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, iad1tya.echo.music.recognition.RecognitionForegroundService::class.java)
            )
        }
    }

    fun startRecognition() {
        if (hasPermission) {
            // Route through the microphone foreground service so the ~10s listen keeps capturing audio
            // even if the user backgrounds the app or locks the screen (Android 12+ mutes AudioRecord
            // for cached apps). The screen keeps rendering states from the shared status flow.
            ContextCompat.startForegroundService(
                context,
                Intent(context, iad1tya.echo.music.recognition.RecognitionForegroundService::class.java)
            )
        } else {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    fun cancelRecognition() {
        // Actually cancel the mic session (not just the UI state): otherwise a zombie ~10s recording
        // keeps the microphone busy and silently swallows the next attempts. Passing the context also
        // stops the microphone foreground service (and its ongoing notification).
        iad1tya.echo.music.recognition.MusicRecognitionService.cancel(context)
    }

    // Auto-start when opened from the Quick Settings tile / widget / a RECOGNITION deep link with
    // autoStart: begin listening immediately instead of requiring a second tap. If the permission is
    // missing, request it exactly like the manual tap path does.
    LaunchedEffect(autoStart) {
        if (autoStart) {
            startRecognition()
        }
    }

    fun resetToReady() {
        iad1tya.echo.music.recognition.MusicRecognitionService.reset()
    }

    // Resolve-once: the first time a Success result renders, match it to a YouTube Music SongItem
    // (lazily, off the main thread) so Like / Add-to-playlist can persist the REAL song — the same
    // one the Play button would pick (same search + best-match logic as SuggestionsViewModel).
    // Keyed by trackId: a new recognition re-resolves, re-rendering the same result reuses the cache.
    var resolvedSong by remember { mutableStateOf<SongItem?>(null) }
    var resolveFailed by remember { mutableStateOf(false) }
    var resolvedTrackId by remember { mutableStateOf<String?>(null) }

    val successResult = (recognitionStatus as? RecognitionStatus.Success)?.result
    LaunchedEffect(successResult?.trackId) {
        val result = successResult ?: return@LaunchedEffect
        // Reuse the cache only for a COMPLETED resolve of this trackId. The id is latched after the
        // search finishes (success or failure), never before: latching up-front meant a cancelled
        // resolve (key change mid-search, e.g. Try Again) permanently blocked re-resolving the same
        // track — Like/Add stuck on an infinite spinner.
        if (resolvedTrackId == result.trackId && (resolvedSong != null || resolveFailed)) {
            return@LaunchedEffect
        }
        resolvedSong = null
        resolveFailed = false
        val match = withContext(Dispatchers.IO) {
            YouTube.search("${result.title} ${result.artist}", YouTube.SearchFilter.FILTER_SONG)
                .getOrNull()
                ?.items
                ?.filterIsInstance<SongItem>()
                .orEmpty()
                .let { songs -> bestSongMatch(songs, result) }
        }
        if (match != null) {
            resolvedSong = match
            resolvedTrackId = result.trackId
        } else {
            resolveFailed = true
            resolvedTrackId = result.trackId
            Toast.makeText(context, R.string.recognition_resolve_failed, Toast.LENGTH_SHORT).show()
        }
    }

    // Live liked state of the resolved song (DB flow), so the heart reflects/persists toggles.
    val librarySongFlow = remember(resolvedSong?.id) {
        resolvedSong?.id?.let { database.song(it) } ?: flowOf(null)
    }
    val librarySong by librarySongFlow.collectAsState(initial = null)
    val isLiked = librarySong?.song?.liked == true

    fun toggleLikeResolved() {
        val songItem = resolvedSong ?: return
        // UPSERT rule: insert-if-missing + read-back + toggle + upsert inside ONE query task
        // (mirrors MusicService.toggleLike semantics WITHOUT touching the playing song). insert() is
        // IGNORE-on-conflict, so an already-saved song keeps its state before the toggle.
        database.query {
            insert(songItem.toMediaMetadata())
            getSongByIdBlocking(songItem.id)?.song?.let { entity ->
                val toggled = entity.toggleLike()
                upsert(toggled)
                syncUtils.likeSong(toggled)
            }
        }
    }

    var showChoosePlaylistDialog by rememberSaveable { mutableStateOf(false) }

    // Mirrors YouTubeSongMenu's AddToPlaylistDialog wiring. Remote sync is NOT done here: the dialog
    // itself pushes the returned ids to the synced playlist's browseId (AddToPlaylistDialog) — adding
    // here too sent the song TWICE to the remote playlist.
    AddToPlaylistDialog(
        isVisible = showChoosePlaylistDialog,
        onGetSong = { _ ->
            val songItem = resolvedSong
            if (songItem == null) {
                emptyList()
            } else {
                database.withTransaction {
                    insert(songItem.toMediaMetadata())
                }
                listOf(songItem.id)
            }
        },
        onDismiss = { showChoosePlaylistDialog = false }
    )

    // History is saved by RecognitionForegroundService's success branch (single writer — the in-app
    // flow also runs through that service). Saving here too would duplicate every entry, and a
    // retained headless Success re-rendered on screen entry would duplicate it again.

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.recognize_music)) },
                navigationIcon = {
                    IconButton(
                        onClick = { navController.navigateUp() },
                        onLongClick = { navController.backToMain() }
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.arrow_back),
                            contentDescription = null
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { navController.navigate("recognition_history") }) {
                        Icon(
                            painter = painterResource(R.drawable.history),
                            contentDescription = stringResource(R.string.recognition_history)
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            AnimatedContent(
                targetState = recognitionStatus,
                transitionSpec = {
                    (fadeIn() + scaleIn()).togetherWith(fadeOut() + scaleOut())
                },
                label = "recognition_content"
            ) { status ->
                when (status) {
                    is RecognitionStatus.Ready -> {
                        ReadyState(onStartRecognition = ::startRecognition)
                    }
                    is RecognitionStatus.Listening -> {
                        ListeningState(
                            onCancel = ::cancelRecognition
                        )
                    }
                    is RecognitionStatus.Processing -> {
                        ProcessingState()
                    }
                    is RecognitionStatus.Success -> {
                        SuccessState(
                            result = status.result,
                            onPlayOnApp = { result ->
                                if (playerConnection != null) {
                                    // Actually play: direct video id when Shazam gave one, else the
                                    // proven search + best-match + YouTubeQueue path (with failure toasts).
                                    suggestionsViewModel.playTrack(
                                        SuggestionTrack(
                                            rank = 0,
                                            title = result.title,
                                            artist = result.artist,
                                            thumbnailUrl = result.coverArtHqUrl ?: result.coverArtUrl,
                                            videoId = result.youtubeVideoId,
                                        ),
                                        playerConnection
                                    )
                                } else {
                                    // No live player connection: fall back to the old search navigation.
                                    val searchQuery = "${result.title} ${result.artist}"
                                    navController.navigate("search/${java.net.URLEncoder.encode(searchQuery, "UTF-8")}")
                                }
                            },
                            isResolving = resolvedSong == null && !resolveFailed,
                            isResolved = resolvedSong != null,
                            isLiked = isLiked,
                            onToggleLike = ::toggleLikeResolved,
                            onAddToPlaylist = { showChoosePlaylistDialog = true },
                            onTryAgain = {
                                startRecognition()
                            },
                            onClose = ::resetToReady
                        )
                    }
                    is RecognitionStatus.NoMatch -> {
                        NoMatchState(
                            message = status.message,
                            onTryAgain = {
                                startRecognition()
                            }
                        )
                    }
                    is RecognitionStatus.Error -> {
                        ErrorState(
                            message = status.message,
                            onTryAgain = {
                                startRecognition()
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ReadyState(
    onStartRecognition: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        Box(
            modifier = Modifier
                .size(200.dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                            Color.Transparent
                        )
                    )
                )
                .clickable { onStartRecognition() },
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(160.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(R.drawable.mic),
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.onPrimary
                )
            }
        }
        
        Text(
            text = stringResource(R.string.tap_to_recognize),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun ListeningState(
    onCancel: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )
    
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        
        Box(
            modifier = Modifier.size(260.dp),
            contentAlignment = Alignment.Center
        ) {
            
            Box(
                modifier = Modifier
                    .size(200.dp)
                    .scale(scale)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
            )
            
            
            Box(
                modifier = Modifier
                    .size(180.dp)
                    .scale(scale * 0.9f)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
            )
            
            
            Box(
                modifier = Modifier
                    .size(160.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
                    .clickable { onCancel() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(R.drawable.mic),
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.onPrimary
                )
            }
        }
        
        Text(
            text = stringResource(R.string.listening),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary
        )
        
        OutlinedButton(onClick = onCancel) {
            Text(stringResource(R.string.cancel))
        }
    }
}

@Composable
private fun ProcessingState() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        val infiniteTransition = rememberInfiniteTransition(label = "rotate")
        val rotation by infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(
                animation = tween(2000, easing = LinearEasing)
            ),
            label = "rotation"
        )
        
        Box(
            modifier = Modifier.size(160.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(160.dp)
                    .clip(CircleShape)
                    .border(
                        width = 4.dp,
                        brush = Brush.sweepGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.primary,
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                                Color.Transparent,
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                                MaterialTheme.colorScheme.primary
                            )
                        ),
                        shape = CircleShape
                    )
            )
            
            Icon(
                painter = painterResource(R.drawable.music_note),
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        }
        
        Text(
            text = stringResource(R.string.processing),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

// Bidirectional artist match — same semantics as SuggestionsViewModel.playTrack, so the resolved
// song (Like / Add-to-playlist) is the SAME one the Play button would pick.
private fun artistMatches(ytArtistName: String, recognizedArtist: String): Boolean {
    val ytNorm = ytArtistName.trim().lowercase()
    val recNorm = recognizedArtist.trim().lowercase()
    return recNorm.contains(ytNorm) || ytNorm.contains(recNorm)
}

private suspend fun bestSongMatch(songs: List<SongItem>, result: RecognitionResult): SongItem? =
    // Shazam already gave us the exact video id: trust it above the name heuristics. If it isn't in
    // the FILTER_SONG results (video-only upload, regional variant…), resolve that exact id directly
    // so Like/Add persist EXACTLY the item the Play button plays — the name heuristics below could
    // otherwise pick a DIFFERENT track. Heuristics stay as fallback when the id resolve fails.
    result.youtubeVideoId?.let { id ->
        songs.firstOrNull { it.id == id }
            ?: YouTube.queue(videoIds = listOf(id)).getOrNull()?.firstOrNull()
    }
        ?: songs.firstOrNull { s ->
            s.title.equals(result.title, ignoreCase = true) &&
                s.artists.any { a -> artistMatches(a.name, result.artist) }
        }
        ?: songs.firstOrNull { s ->
            s.title.contains(result.title, ignoreCase = true) &&
                s.artists.any { a -> artistMatches(a.name, result.artist) }
        }
        // Title-first fallback so the right track wins over a different song by the same artist.
        ?: songs.firstOrNull { s -> s.title.equals(result.title, ignoreCase = true) }
        ?: songs.firstOrNull { s -> s.title.contains(result.title, ignoreCase = true) }
        ?: songs.firstOrNull()

@Composable
private fun SuccessState(
    result: RecognitionResult,
    onPlayOnApp: (RecognitionResult) -> Unit,
    isResolving: Boolean,
    isResolved: Boolean,
    isLiked: Boolean,
    onToggleLike: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onTryAgain: () -> Unit,
    onClose: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.padding(horizontal = 16.dp)
    ) {
        
        Card(
            modifier = Modifier
                .size(180.dp)
                .aspectRatio(1f),
            shape = RoundedCornerShape(iad1tya.echo.music.constants.ThumbnailCornerRadius),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            AsyncImage(
                model = result.coverArtHqUrl ?: result.coverArtUrl,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        
        Text(
            text = result.title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        
        Text(
            text = result.artist,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        
        result.album?.let { album ->
            Text(
                text = album,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Button(
                onClick = { onPlayOnApp(result) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    painter = painterResource(R.drawable.play),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.play_on_app))
            }

            // Like + Add-to-playlist: enabled once the recognized track has been resolved to a real
            // YouTube Music song; a brief spinner shows while that one-shot resolve is in flight.
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                FilledTonalButton(
                    onClick = onToggleLike,
                    enabled = isResolved,
                    modifier = Modifier.weight(1f)
                ) {
                    if (isResolving) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(
                            painter = painterResource(
                                if (isLiked) R.drawable.favorite else R.drawable.favorite_border
                            ),
                            contentDescription = stringResource(R.string.action_like),
                            modifier = Modifier.size(18.dp),
                            tint = if (isLiked) MaterialTheme.colorScheme.error else LocalContentColor.current
                        )
                    }
                }

                FilledTonalButton(
                    onClick = onAddToPlaylist,
                    enabled = isResolved,
                    modifier = Modifier.weight(1f)
                ) {
                    if (isResolving) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(
                            painter = painterResource(R.drawable.playlist_add),
                            contentDescription = stringResource(R.string.add_to_playlist),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            FilledTonalButton(
                onClick = onTryAgain,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    painter = painterResource(R.drawable.mic),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.re_listen))
            }
            
            
            OutlinedButton(
                onClick = onClose,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    painter = painterResource(R.drawable.close),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.close))
            }
        }
    }
}

@Composable
private fun NoMatchState(
    message: String,
    onTryAgain: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        Box(
            modifier = Modifier
                .size(120.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.errorContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(R.drawable.close),
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.onErrorContainer
            )
        }
        
        Text(
            text = stringResource(R.string.no_match_found),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 32.dp)
        )
        
        Button(onClick = onTryAgain) {
            Icon(
                painter = painterResource(R.drawable.refresh),
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(stringResource(R.string.try_again))
        }
    }
}

@Composable
private fun ErrorState(
    message: String,
    onTryAgain: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        Box(
            modifier = Modifier
                .size(120.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.errorContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(R.drawable.error),
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.onErrorContainer
            )
        }
        
        Text(
            text = stringResource(R.string.recognition_error),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 32.dp)
        )
        
        Button(onClick = onTryAgain) {
            Icon(
                painter = painterResource(R.drawable.refresh),
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(stringResource(R.string.try_again))
        }
    }
}
