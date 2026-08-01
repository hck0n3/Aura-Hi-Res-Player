@file:OptIn(ExperimentalMaterial3Api::class)

package iad1tya.echo.music.ui.screens.migration

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import iad1tya.echo.music.LocalPlayerAwareWindowInsets
import iad1tya.echo.music.R
import iad1tya.echo.music.ui.component.DefaultDialog
import iad1tya.echo.music.ui.component.IconButton
import iad1tya.echo.music.ui.component.Material3SettingsGroup
import iad1tya.echo.music.ui.component.Material3SettingsItem
import iad1tya.echo.music.ui.utils.backToMain

@Composable
fun MigrationScreen(
    navController: NavController,
    viewModel: MigrationViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    var showDeezerDialog by remember { mutableStateOf(false) }
    var deezerInput by remember { mutableStateOf("") }
    var showReview by remember { mutableStateOf(false) }

    // Re-check YouTube login every time the screen is shown (the user may have just signed in).
    LaunchedEffect(Unit) { viewModel.refreshEnvironment() }

    // When there are no more ambiguous tracks to review, leave the review view automatically.
    LaunchedEffect(state.ambiguous.size) {
        if (state.ambiguous.isEmpty()) showReview = false
    }

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.prepareFileImport(uri)
    }

    Scaffold(
        modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { Text(stringResource(R.string.migrate_playlist)) },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            if (showReview) showReview = false else navController.navigateUp()
                        },
                        onLongClick = navController::backToMain,
                    ) {
                        Icon(painterResource(R.drawable.arrow_back), null)
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { innerPadding ->
        val contentModifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(
                LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom),
            )

        if (showReview && state.phase == MigrationPhase.DONE) {
            AmbiguousReviewBody(
                modifier = contentModifier,
                innerPadding = innerPadding,
                state = state,
                onChoose = viewModel::chooseCandidate,
                onSkip = viewModel::skipAmbiguous,
                onApply = { viewModel.applyResolved() },
            )
        } else {
            LazyColumn(
                modifier = contentModifier,
                contentPadding = PaddingValues(
                    top = innerPadding.calculateTopPadding(),
                    bottom = innerPadding.calculateBottomPadding() + 32.dp,
                    start = 16.dp,
                    end = 16.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                when (state.phase) {
                    MigrationPhase.PICK -> pickerContent(
                        state = state,
                        onArchivo = { filePicker.launch(arrayOf("*/*")) },
                        onDeezer = { deezerInput = ""; showDeezerDialog = true },
                        onTidal = { navController.navigate("migration/tidal") },
                        onApple = { navController.navigate("migration/apple") },
                        onYouTubeLogin = { navController.navigate("login") },
                    )

                    MigrationPhase.CONFIRM -> confirmContent(
                        state = state,
                        onContinue = { viewModel.confirmAndStart() },
                        onCancel = { viewModel.cancelPending() },
                    )

                    MigrationPhase.RUNNING -> runningContent(state)

                    MigrationPhase.DONE -> doneContent(
                        state = state,
                        onReview = { showReview = true },
                        onOpenLibrary = {
                            state.localPlaylistId?.let { navController.navigate("local_playlist/$it") }
                        },
                        onMigrateAnother = { viewModel.reset() },
                    )

                    MigrationPhase.ERROR -> Unit // handled by the dialog below
                }
            }
        }
    }

    if (showDeezerDialog) {
        AlertDialog(
            onDismissRequest = { showDeezerDialog = false },
            title = { Text(stringResource(R.string.migrate_deezer_title)) },
            text = {
                Column {
                    Text(stringResource(R.string.migrate_deezer_hint))
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = deezerInput,
                        onValueChange = { deezerInput = it },
                        singleLine = true,
                        placeholder = { Text("https://www.deezer.com/playlist/…") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = deezerInput.isNotBlank(),
                    onClick = {
                        viewModel.prepareDeezerImport(deezerInput.trim())
                        showDeezerDialog = false
                    },
                ) { Text(stringResource(R.string.migrate_continue)) }
            },
            dismissButton = {
                TextButton(onClick = { showDeezerDialog = false }) {
                    Text(stringResource(android.R.string.cancel))
                }
            },
        )
    }

    if (state.phase == MigrationPhase.ERROR) {
        state.errorMessage?.let { message ->
            DefaultDialog(
                onDismiss = { viewModel.dismissError() },
                title = { Text(stringResource(R.string.migrate_error_title)) },
                buttons = {
                    TextButton(onClick = { viewModel.dismissError() }) {
                        Text(stringResource(android.R.string.ok))
                    }
                },
            ) {
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

// ── PICK ─────────────────────────────────────────────────────────────────

private fun androidx.compose.foundation.lazy.LazyListScope.pickerContent(
    state: MigrationUiState,
    onArchivo: () -> Unit,
    onDeezer: () -> Unit,
    onTidal: () -> Unit,
    onApple: () -> Unit,
    onYouTubeLogin: () -> Unit,
) {
    // HARD GATE: the migrated playlist is CREATED in the user's YouTube Music account, so a YT Music
    // session is mandatory. Without it, hide the sources entirely and send the user to log in first —
    // otherwise they'd authenticate Tidal, pick a playlist, and only fail at the end (owner's report:
    // Tidal found the playlist + songs, then "no se puede migrar" because he wasn't signed into YTM).
    if (!state.signedInYouTube) {
        item { WarningCard(text = stringResource(R.string.migrate_needs_youtube)) }
        item {
            Button(
                onClick = onYouTubeLogin,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.migrate_youtube_login)) }
        }
        return
    }
    item {
        Text(
            text = stringResource(R.string.migrate_pick_source_desc),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    item {
        Material3SettingsGroup(
            title = stringResource(R.string.migrate_source_group),
            items = listOf(
                Material3SettingsItem(
                    icon = painterResource(R.drawable.folder_managed),
                    title = { Text(stringResource(R.string.migrate_source_file)) },
                    description = { Text(stringResource(R.string.migrate_source_file_desc)) },
                    onClick = onArchivo,
                ),
                Material3SettingsItem(
                    icon = painterResource(R.drawable.link),
                    title = { Text(stringResource(R.string.migrate_source_deezer)) },
                    description = { Text(stringResource(R.string.migrate_source_deezer_desc)) },
                    onClick = onDeezer,
                ),
                Material3SettingsItem(
                    icon = painterResource(R.drawable.library_music),
                    title = { Text(stringResource(R.string.migrate_source_tidal)) },
                    description = { Text(stringResource(R.string.migrate_source_tidal_desc)) },
                    onClick = onTidal,
                ),
                Material3SettingsItem(
                    icon = painterResource(R.drawable.apple_music_me),
                    title = { Text(stringResource(R.string.migrate_source_apple)) },
                    description = { Text(stringResource(R.string.migrate_source_apple_desc)) },
                    onClick = onApple,
                ),
            ),
        )
    }
}

// ── CONFIRM ──────────────────────────────────────────────────────────────

private fun androidx.compose.foundation.lazy.LazyListScope.confirmContent(
    state: MigrationUiState,
    onContinue: () -> Unit,
    onCancel: () -> Unit,
) {
    item {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = state.pendingName,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = if (state.pendingCount > 0)
                        stringResource(R.string.migrate_confirm_count, state.pendingCount)
                    else stringResource(R.string.migrate_confirm_count_unknown),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = stringResource(R.string.migrate_confirm_network_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                // Data Saver / large-import guard: never kick off a big metered import silently.
                if (state.dataSaver || state.pendingCount >= 100) {
                    WarningInline(
                        text = if (state.dataSaver)
                            stringResource(R.string.migrate_confirm_datasaver)
                        else stringResource(R.string.migrate_confirm_large),
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f)) {
                        Text(stringResource(android.R.string.cancel))
                    }
                    Button(onClick = onContinue, modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.migrate_continue))
                    }
                }
            }
        }
    }
}

// ── RUNNING ────────────────────────────────────────────────────────────────

private fun androidx.compose.foundation.lazy.LazyListScope.runningContent(state: MigrationUiState) {
    item {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text(
                    text = stringResource(R.string.migrate_running_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = stringResource(
                        R.string.migrate_running_progress,
                        state.progressDone,
                        state.progressTotal,
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (state.progressCurrent.isNotBlank()) {
                    Text(
                        text = state.progressCurrent,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                val fraction = if (state.progressTotal > 0)
                    state.progressDone.toFloat() / state.progressTotal.toFloat() else 0f
                LinearProgressIndicator(
                    progress = { fraction },
                    modifier = Modifier.fillMaxWidth().clip(CircleShape),
                )
            }
        }
    }
}

// ── DONE ───────────────────────────────────────────────────────────────────

private fun androidx.compose.foundation.lazy.LazyListScope.doneContent(
    state: MigrationUiState,
    onReview: () -> Unit,
    onOpenLibrary: () -> Unit,
    onMigrateAnother: () -> Unit,
) {
    item {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = state.playlistName,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = stringResource(R.string.migrate_result_matched, state.matchedCount),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = stringResource(R.string.migrate_result_ambiguous, state.ambiguousPending),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = stringResource(R.string.migrate_result_notfound, state.notFoundCount),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (state.ytmPlaylistId == null) {
                    WarningInline(text = stringResource(R.string.migrate_result_no_playlist))
                }
            }
        }
    }
    if (state.ambiguousPending > 0) {
        item {
            Button(
                onClick = onReview,
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                shape = RoundedCornerShape(16.dp),
            ) {
                Text(stringResource(R.string.migrate_review_ambiguous, state.ambiguousPending))
            }
        }
    }
    if (state.localPlaylistId != null) {
        item {
            OutlinedButton(
                onClick = onOpenLibrary,
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                shape = RoundedCornerShape(16.dp),
            ) {
                Text(stringResource(R.string.migrate_open_in_library))
            }
        }
    }
    item {
        TextButton(
            onClick = onMigrateAnother,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.migrate_another))
        }
    }
}

// ── AMBIGUOUS REVIEW ─────────────────────────────────────────────────────

@Composable
private fun AmbiguousReviewBody(
    modifier: Modifier,
    innerPadding: PaddingValues,
    state: MigrationUiState,
    onChoose: (Int, String) -> Unit,
    onSkip: (Int) -> Unit,
    onApply: () -> Unit,
) {
    Column(modifier = modifier) {
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(
                top = innerPadding.calculateTopPadding(),
                bottom = 16.dp,
                start = 16.dp,
                end = 16.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Text(
                    text = stringResource(R.string.migrate_review_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            itemsIndexed(state.ambiguous) { index, item ->
                AmbiguousCard(
                    item = item,
                    onChoose = { videoId -> onChoose(index, videoId) },
                    onSkip = { onSkip(index) },
                )
            }
        }
        val anyChosen = state.ambiguous.any { it.chosenVideoId != null }
        Surface(
            tonalElevation = 3.dp,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(
                        LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Bottom),
                    )
                    .padding(16.dp),
            ) {
                Button(
                    onClick = onApply,
                    enabled = anyChosen && !state.appendingResolved,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    if (state.appendingResolved) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                    } else {
                        Text(stringResource(R.string.migrate_review_add))
                    }
                }
            }
        }
    }
}

@Composable
private fun AmbiguousCard(
    item: AmbiguousItem,
    onChoose: (String) -> Unit,
    onSkip: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = stringResource(
                    R.string.migrate_review_you_searched,
                    item.track.primaryArtist,
                    item.track.title,
                ),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )
            item.candidates.forEach { candidate ->
                CandidateRow(
                    candidate = candidate,
                    selected = item.chosenVideoId == candidate.videoId,
                    onClick = { onChoose(candidate.videoId) },
                )
            }
            TextButton(
                onClick = onSkip,
                modifier = Modifier.align(Alignment.End),
            ) {
                Text(
                    text = if (item.resolved && item.chosenVideoId == null)
                        stringResource(R.string.migrate_review_skipped)
                    else stringResource(R.string.migrate_review_skip),
                )
            }
        }
    }
}

@Composable
private fun CandidateRow(
    candidate: com.aura.migration.model.YtmCandidate,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = if (selected) MaterialTheme.colorScheme.secondaryContainer
        else MaterialTheme.colorScheme.surfaceContainerLow,
        onClick = onClick,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(MaterialTheme.shapes.small)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                if (!candidate.thumbnailUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = candidate.thumbnailUrl,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Icon(
                        painter = painterResource(R.drawable.music_note),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = candidate.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = listOfNotNull(
                        candidate.primaryArtist.takeIf { it.isNotBlank() },
                        candidate.album?.takeIf { it.isNotBlank() },
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            RadioButton(selected = selected, onClick = onClick)
        }
    }
}

// ── shared bits ─────────────────────────────────────────────────────────

@Composable
private fun WarningCard(text: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
        ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                painter = painterResource(R.drawable.warning),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
            )
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun WarningInline(text: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            painter = painterResource(R.drawable.warning),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(18.dp),
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
    }
}
