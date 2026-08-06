package iad1tya.echo.music.ui.newui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.aura.migration.model.CollectionKind
import com.aura.migration.model.SourcePlaylist
import com.aura.migration.model.YtmCandidate
import com.aura.migration.source.apple.AppleMusicGuide
import iad1tya.echo.music.BuildConfig
import iad1tya.echo.music.LocalPlayerAwareWindowInsets
import iad1tya.echo.music.R
import iad1tya.echo.music.constants.TidalClientIdKey
import iad1tya.echo.music.migration.TidalAuthCallbackBus
import iad1tya.echo.music.ui.component.DefaultDialog
import iad1tya.echo.music.ui.screens.migration.AmbiguousItem
import iad1tya.echo.music.ui.screens.migration.MigrationPhase
import iad1tya.echo.music.ui.screens.migration.MigrationUiState
import iad1tya.echo.music.ui.screens.migration.MigrationViewModel
import iad1tya.echo.music.ui.screens.migration.TidalAuthUiState
import iad1tya.echo.music.ui.screens.migration.destinationRes
import iad1tya.echo.music.ui.screens.migration.openAuthUrl
import iad1tya.echo.music.ui.screens.migration.openExternal
import iad1tya.echo.music.utils.rememberPreference
import java.util.Locale

/**
 * # Migrar lista — "Interfaz nueva"
 *
 * Three screens: the source picker and its state machine ([AuraMigrationScreen]), the Tidal
 * connection ([AuraMigrationTidalScreen]) and the Apple Music guide ([AuraMigrationAppleScreen]).
 *
 * ## This is a wizard with real failure modes, and it keeps all of them
 * A migration can fail in a dozen honest ways — no YouTube Music session, an unreadable file, a
 * private Deezer profile, an expired Tidal token, Data Saver on, a track with several plausible
 * matches, zero automatic matches, a library import that creates no playlist by design. Every one of
 * those reports itself here exactly as it does on the classic screen, from the SAME state:
 * [MigrationViewModel] is untouched, every phase of [MigrationPhase] is drawn, and the destination
 * wording comes from the classic screen's own `destinationRes`, so the two skins can never promise
 * different destinations for the same collection.
 *
 * ## Reused, not rebuilt
 *  · The whole state machine, every network call and every database write: [MigrationViewModel].
 *  · The Custom Tab that carries the Tidal OAuth redirect: the classic screen's `openAuthUrl`.
 *  · The Deezer input and the error report: the classic `AlertDialog` / [DefaultDialog]. Popups are
 *    modal surfaces, not screens — the same rule Biblioteca follows for its own dialogs.
 *  · The Apple guide's steps and limitations: [AppleMusicGuide], verbatim.
 */

// ── 1. Picker + state machine ─────────────────────────────────────────────────────────────────────

@Composable
fun AuraMigrationScreen(
    navController: NavController,
    viewModel: MigrationViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    var showDeezerDialog by remember { mutableStateOf(false) }
    var deezerInput by rememberSaveable { mutableStateOf("") }
    var showReview by rememberSaveable { mutableStateOf(false) }

    // Re-check the YouTube session every time the screen is shown (the user may have just signed in).
    LaunchedEffect(Unit) { viewModel.refreshEnvironment() }

    // Nothing left to review — leave the review view on its own, as the classic screen does.
    LaunchedEffect(state.ambiguous.size) {
        if (state.ambiguous.isEmpty()) showReview = false
    }

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.prepareFileImport(uri)
    }

    val bloom = rememberAuraBloom(mediaId = null)
    val insets = LocalPlayerAwareWindowInsets.current
    val bottomClearance = insets
        .only(WindowInsetsSides.Bottom)
        .asPaddingValues()
        .calculateBottomPadding()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .auraScreenBackground(bloom, intensity = 0.32f),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(insets.only(WindowInsetsSides.Horizontal))
                .padding(top = auraStatusBarPadding()),
        ) {
            AuraDetailHeader(
                title = stringResource(R.string.migrate_playlist),
                // The classic back arrow is a three-way control, and losing either of the two inner
                // steps would strand the user: review → results, a loaded Deezer profile → the source
                // picker, otherwise leave the screen.
                onBack = {
                    when {
                        showReview -> showReview = false
                        state.phase == MigrationPhase.COLLECTION -> viewModel.cancelCollection()
                        else -> navController.navigateUp()
                    }
                },
            )

            if (showReview && state.phase == MigrationPhase.DONE) {
                AuraAmbiguousReview(
                    state = state,
                    bottomClearance = bottomClearance,
                    onChoose = viewModel::chooseCandidate,
                    onSkip = viewModel::skipAmbiguous,
                    onApply = { viewModel.applyResolved() },
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = AuraSpacing.Gutter,
                        end = AuraSpacing.Gutter,
                        top = 10.dp,
                        bottom = bottomClearance + 48.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    when (state.phase) {
                        MigrationPhase.PICK -> auraPickerContent(
                            state = state,
                            onFile = { filePicker.launch(arrayOf("*/*")) },
                            onDeezer = { deezerInput = ""; showDeezerDialog = true },
                            onTidal = { navController.navigate("migration/tidal") },
                            onApple = { navController.navigate("migration/apple") },
                            onYouTubeLogin = { navController.navigate("login") },
                        )

                        MigrationPhase.COLLECTION -> auraCollectionContent(
                            state = state,
                            onPick = viewModel::prepareCollectionItem,
                            onBack = { viewModel.cancelCollection() },
                        )

                        MigrationPhase.CONFIRM -> auraConfirmContent(
                            state = state,
                            onContinue = { viewModel.confirmAndStart() },
                            onCancel = { viewModel.cancelPending() },
                        )

                        MigrationPhase.RUNNING -> auraRunningContent(state)

                        MigrationPhase.DONE -> auraDoneContent(
                            state = state,
                            onReview = { showReview = true },
                            onOpenLibrary = {
                                state.localPlaylistId?.let { navController.navigate("local_playlist/$it") }
                            },
                            onMigrateAnother = { viewModel.reset() },
                        )

                        // Reported by the dialog below, exactly as on the classic screen.
                        MigrationPhase.ERROR -> Unit
                    }
                }
            }
        }
    }

    // ── Dialogs: classic, verbatim ────────────────────────────────────────────────────────────────

    if (showDeezerDialog) {
        AlertDialog(
            onDismissRequest = { showDeezerDialog = false },
            title = { Text(stringResource(R.string.migrate_deezer_title)) },
            text = {
                Column {
                    Text(stringResource(R.string.migrate_deezer_hint))
                    Spacer(Modifier.height(8.dp))
                    // Up-front, so a private profile is a fixable instruction and not a dead end. The
                    // failure itself is still reported (SourceError.PrivatePlaylist → error dialog).
                    Text(
                        text = stringResource(R.string.migrate_deezer_public_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = deezerInput,
                        onValueChange = { deezerInput = it },
                        singleLine = true,
                        placeholder = { Text(stringResource(R.string.migrate_deezer_placeholder)) },
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

// ── PICK ──────────────────────────────────────────────────────────────────────────────────────────

private fun androidx.compose.foundation.lazy.LazyListScope.auraPickerContent(
    state: MigrationUiState,
    onFile: () -> Unit,
    onDeezer: () -> Unit,
    onTidal: () -> Unit,
    onApple: () -> Unit,
    onYouTubeLogin: () -> Unit,
) {
    // HARD GATE, carried over unchanged: the migrated playlist is CREATED in the user's YouTube Music
    // account, so a YTM session is mandatory. Without it the sources are hidden entirely and the user
    // is sent to log in — otherwise they would authenticate Tidal, pick a playlist and only fail at
    // the very end.
    if (!state.signedInYouTube) {
        item(key = "aura_migrate_needs_yt") {
            AuraNoticeCard(
                text = stringResource(R.string.migrate_needs_youtube),
                modifier = Modifier.animateItem(),
            )
        }
        item(key = "aura_migrate_yt_login") {
            AuraActionButton(
                text = stringResource(R.string.migrate_youtube_login),
                onClick = onYouTubeLogin,
                modifier = Modifier
                    .fillMaxWidth()
                    .animateItem(),
            )
        }
        return
    }

    item(key = "aura_migrate_pick_desc") {
        Text(
            text = stringResource(R.string.migrate_pick_source_desc),
            style = AuraType.RowSubtitle,
            color = AuraPalette.OnGroundMuted,
            modifier = Modifier.animateItem(),
        )
    }
    item(key = "aura_migrate_sources") {
        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.animateItem(),
        ) {
            AuraSectionLabel(stringResource(R.string.migrate_source_group).uppercase(Locale.ROOT))
            AuraSourceRow(
                icon = AuraIcons.Download,
                title = stringResource(R.string.migrate_source_file),
                description = stringResource(R.string.migrate_source_file_desc),
                onClick = onFile,
            )
            AuraSourceRow(
                icon = AuraIcons.Share,
                title = stringResource(R.string.migrate_source_deezer),
                description = stringResource(R.string.migrate_source_deezer_desc),
                onClick = onDeezer,
            )
            AuraSourceRow(
                icon = AuraIcons.Library,
                title = stringResource(R.string.migrate_source_tidal),
                description = stringResource(R.string.migrate_source_tidal_desc),
                onClick = onTidal,
            )
            AuraSourceRow(
                icon = AuraIcons.Album,
                title = stringResource(R.string.migrate_source_apple),
                description = stringResource(R.string.migrate_source_apple_desc),
                onClick = onApple,
            )
        }
    }
}

// ── COLLECTION ────────────────────────────────────────────────────────────────────────────────────
// A source that exposes MANY collections at once (today: a public Deezer profile).

private fun androidx.compose.foundation.lazy.LazyListScope.auraCollectionContent(
    state: MigrationUiState,
    onPick: (SourcePlaylist) -> Unit,
    onBack: () -> Unit,
) {
    item(key = "aura_migrate_collection_head") {
        Column(
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.animateItem(),
        ) {
            Text(
                text = stringResource(R.string.migrate_collection_title),
                style = AuraType.CalloutTitle,
                color = AuraPalette.OnGround,
                maxLines = 2,
                overflow = AuraDefaultOverflow,
            )
            Text(
                text = stringResource(R.string.migrate_collection_desc),
                style = AuraType.RowSubtitle,
                color = AuraPalette.OnGroundMuted,
            )
        }
    }

    if (state.collectionLoading) {
        item(key = "aura_migrate_collection_loading") {
            AuraLoadingLine(
                text = stringResource(R.string.migrate_collection_loading),
                modifier = Modifier.animateItem(),
            )
        }
    } else if (state.collection.isEmpty()) {
        item(key = "aura_migrate_collection_empty") {
            AuraNoticeCard(
                text = stringResource(R.string.migrate_collection_empty),
                modifier = Modifier.animateItem(),
            )
        }
    } else {
        items(state.collection, key = { "${it.origin}_${it.kind}_${it.id}" }) { playlist ->
            AuraCollectionRow(
                playlist = playlist,
                onClick = { onPick(playlist) },
                modifier = Modifier.animateItem(),
            )
        }
    }

    item(key = "aura_migrate_collection_back") {
        AuraLinkButton(
            text = stringResource(R.string.migrate_collection_back),
            onClick = onBack,
            modifier = Modifier
                .fillMaxWidth()
                .animateItem(),
        )
    }
}

/**
 * One selectable collection. The badge is the whole point: a user must be able to tell at a glance
 * that "Canciones favoritas" is their LIBRARY (it will be liked / bookmarked) and not just another
 * playlist listed next to them — and the destination line says, in words, where it lands.
 */
@Composable
private fun AuraCollectionRow(
    playlist: SourcePlaylist,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isLibrary = playlist.kind != CollectionKind.PLAYLIST
    val countLine = if (playlist.kind == CollectionKind.FOLLOWED_ARTISTS) {
        // Followed artists are counted in ARTISTS, not songs — "%d canciones" there would be a lie.
        stringResource(R.string.migrate_artist_count, playlist.trackCount)
    } else {
        stringResource(R.string.migrate_tidal_track_count, playlist.trackCount)
    }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(AuraShapes.Card)
            .background(if (isLibrary) AuraPalette.NowPlayingFill else AuraPalette.SurfaceFill)
            .border(
                1.dp,
                if (isLibrary) AuraPalette.NowPlayingLine else AuraPalette.SurfaceLine,
                AuraShapes.Card,
            )
            .sizeIn(minHeight = AuraSpacing.MinTouchTarget)
            .auraClickableInternal(onClick = onClick, contentDescription = playlist.name)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = playlist.name,
                style = AuraType.RowTitle,
                color = AuraPalette.OnGround,
                maxLines = 1,
                overflow = AuraDefaultOverflow,
                modifier = Modifier.weight(1f, fill = false),
            )
            if (isLibrary) {
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(AuraPalette.Teal.copy(alpha = 0.16f))
                        .padding(horizontal = 9.dp, vertical = 3.dp),
                ) {
                    AuraTechnicalText(
                        text = stringResource(R.string.migrate_library_badge).uppercase(Locale.ROOT),
                        color = AuraPalette.Teal,
                        style = AuraType.QualityBadge,
                    )
                }
            }
        }
        Text(
            text = countLine,
            style = AuraType.RowSubtitle,
            color = AuraPalette.OnGroundMuted,
        )
        Text(
            // The classic screen's own mapping, imported rather than copied.
            text = stringResource(destinationRes(playlist.kind)),
            style = AuraType.RowSubtitle,
            color = AuraPalette.OnGroundGhost,
        )
    }
}

// ── CONFIRM ───────────────────────────────────────────────────────────────────────────────────────

private fun androidx.compose.foundation.lazy.LazyListScope.auraConfirmContent(
    state: MigrationUiState,
    onContinue: () -> Unit,
    onCancel: () -> Unit,
) {
    item(key = "aura_migrate_confirm") {
        AuraCardSurface(modifier = Modifier.animateItem()) {
            Text(
                text = state.pendingName,
                style = AuraType.PlayerTitle,
                color = AuraPalette.OnGround,
                maxLines = 2,
                overflow = AuraDefaultOverflow,
            )
            Text(
                text = when {
                    state.pendingKind == CollectionKind.FOLLOWED_ARTISTS ->
                        stringResource(R.string.migrate_artist_count, state.pendingCount)

                    state.pendingCount > 0 ->
                        stringResource(R.string.migrate_confirm_count, state.pendingCount)

                    else -> stringResource(R.string.migrate_confirm_count_unknown)
                },
                style = AuraType.RowSubtitle,
                color = AuraPalette.OnGroundMuted,
            )
            // Say WHERE this lands before anything runs: a library import creates no playlist, and
            // silently doing something other than "migrar lista" would be a nasty surprise.
            Text(
                text = stringResource(destinationRes(state.pendingKind)),
                style = AuraType.MenuLabel,
                color = AuraPalette.OnGround,
            )
            Text(
                text = stringResource(R.string.migrate_confirm_network_note),
                style = AuraType.RowSubtitle,
                color = AuraPalette.OnGroundGhost,
            )
            // Data Saver / large-import guard: never kick off a big metered import silently.
            if (state.dataSaver || state.pendingCount >= 100) {
                AuraNoticeLine(
                    text = if (state.dataSaver) {
                        stringResource(R.string.migrate_confirm_datasaver)
                    } else {
                        stringResource(R.string.migrate_confirm_large)
                    },
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                AuraQuietButton(
                    text = stringResource(android.R.string.cancel),
                    onClick = onCancel,
                    modifier = Modifier.weight(1f),
                )
                AuraActionButton(
                    text = stringResource(R.string.migrate_continue),
                    onClick = onContinue,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

// ── RUNNING ───────────────────────────────────────────────────────────────────────────────────────

private fun androidx.compose.foundation.lazy.LazyListScope.auraRunningContent(state: MigrationUiState) {
    item(key = "aura_migrate_running") {
        AuraCardSurface(modifier = Modifier.animateItem()) {
            Text(
                text = stringResource(R.string.migrate_running_title),
                style = AuraType.CalloutTitle,
                color = AuraPalette.OnGround,
            )
            AuraTechnicalText(
                text = stringResource(
                    R.string.migrate_running_progress,
                    state.progressDone,
                    state.progressTotal,
                ),
                color = AuraPalette.Teal,
            )
            if (state.progressCurrent.isNotBlank()) {
                Text(
                    text = state.progressCurrent,
                    style = AuraType.RowSubtitle,
                    color = AuraPalette.OnGroundMuted,
                    maxLines = 1,
                    overflow = AuraDefaultOverflow,
                )
            }
            AuraLinearProgress(
                fraction = if (state.progressTotal > 0) {
                    state.progressDone.toFloat() / state.progressTotal.toFloat()
                } else {
                    0f
                },
            )
        }
    }
}

// ── DONE ──────────────────────────────────────────────────────────────────────────────────────────

private fun androidx.compose.foundation.lazy.LazyListScope.auraDoneContent(
    state: MigrationUiState,
    onReview: () -> Unit,
    onOpenLibrary: () -> Unit,
    onMigrateAnother: () -> Unit,
) {
    item(key = "aura_migrate_done") {
        AuraCardSurface(modifier = Modifier.animateItem()) {
            Text(
                text = state.playlistName,
                style = AuraType.PlayerTitle,
                color = AuraPalette.OnGround,
                maxLines = 2,
                overflow = AuraDefaultOverflow,
            )
            if (state.resultKind == CollectionKind.FOLLOWED_ARTISTS) {
                // Artists never go through the resolver, so "encontradas / por revisar / no
                // encontradas" would be three lines of zeros. Report what actually happened.
                Text(
                    text = stringResource(R.string.migrate_result_artists, state.artistsAdded),
                    style = AuraType.MenuLabel,
                    color = AuraPalette.OnGround,
                )
                if (state.notFoundCount > 0) {
                    Text(
                        text = stringResource(
                            R.string.migrate_result_artists_notfound,
                            state.notFoundCount,
                        ),
                        style = AuraType.RowSubtitle,
                        color = AuraPalette.OnGroundMuted,
                    )
                }
            } else {
                Text(
                    text = when (state.resultKind) {
                        CollectionKind.FAVORITE_TRACKS ->
                            stringResource(R.string.migrate_result_liked, state.matchedCount)

                        CollectionKind.SAVED_ALBUMS ->
                            stringResource(R.string.migrate_result_library, state.matchedCount)

                        else -> stringResource(R.string.migrate_result_matched, state.matchedCount)
                    },
                    style = AuraType.MenuLabel,
                    color = AuraPalette.OnGround,
                )
                Text(
                    text = stringResource(R.string.migrate_result_ambiguous, state.ambiguousPending),
                    style = AuraType.RowSubtitle,
                    color = AuraPalette.OnGroundMuted,
                )
                Text(
                    text = stringResource(R.string.migrate_result_notfound, state.notFoundCount),
                    style = AuraType.RowSubtitle,
                    color = AuraPalette.OnGroundMuted,
                )
            }
            // A library import creates NO playlist BY DESIGN — warning there would report a
            // successful import as a failure. Only the playlist path can legitimately miss one.
            if (state.resultKind == CollectionKind.PLAYLIST && state.ytmPlaylistId == null) {
                AuraNoticeLine(text = stringResource(R.string.migrate_result_no_playlist))
            }
        }
    }
    if (state.ambiguousPending > 0) {
        item(key = "aura_migrate_review_cta") {
            AuraActionButton(
                text = stringResource(R.string.migrate_review_ambiguous, state.ambiguousPending),
                onClick = onReview,
                modifier = Modifier
                    .fillMaxWidth()
                    .animateItem(),
            )
        }
    }
    if (state.localPlaylistId != null) {
        item(key = "aura_migrate_open_library") {
            AuraQuietButton(
                text = stringResource(R.string.migrate_open_in_library),
                onClick = onOpenLibrary,
                modifier = Modifier
                    .fillMaxWidth()
                    .animateItem(),
            )
        }
    }
    item(key = "aura_migrate_another") {
        AuraLinkButton(
            text = stringResource(R.string.migrate_another),
            onClick = onMigrateAnother,
            modifier = Modifier
                .fillMaxWidth()
                .animateItem(),
        )
    }
}

// ── AMBIGUOUS REVIEW ──────────────────────────────────────────────────────────────────────────────

/**
 * The per-track match results. This is the screen's real payload: when the resolver found several
 * plausible matches it refuses to guess, and this is where the user decides. Losing it would turn
 * every ambiguous track into a silent omission.
 */
@Composable
private fun AuraAmbiguousReview(
    state: MigrationUiState,
    bottomClearance: androidx.compose.ui.unit.Dp,
    onChoose: (Int, String) -> Unit,
    onSkip: (Int) -> Unit,
    onApply: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(
                start = AuraSpacing.Gutter,
                end = AuraSpacing.Gutter,
                top = 10.dp,
                bottom = 16.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item(key = "aura_review_desc") {
                Text(
                    text = stringResource(R.string.migrate_review_desc),
                    style = AuraType.RowSubtitle,
                    color = AuraPalette.OnGroundMuted,
                    modifier = Modifier.animateItem(),
                )
            }
            itemsIndexed(
                items = state.ambiguous,
                key = { _, item -> "${item.track.primaryArtist}_${item.track.title}_${item.track.sourcePosition}" },
            ) { index, item ->
                AuraAmbiguousCard(
                    item = item,
                    onChoose = { videoId -> onChoose(index, videoId) },
                    onSkip = { onSkip(index) },
                    modifier = Modifier.animateItem(),
                )
            }
        }

        val anyChosen = state.ambiguous.any { it.chosenVideoId != null }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(AuraPalette.GroundRaised)
                .padding(bottom = bottomClearance),
        ) {
            AuraDivider()
            AuraActionButton(
                text = stringResource(R.string.migrate_review_add),
                onClick = onApply,
                enabled = anyChosen && !state.appendingResolved,
                loading = state.appendingResolved,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(AuraSpacing.Gutter),
            )
        }
    }
}

@Composable
private fun AuraAmbiguousCard(
    item: AmbiguousItem,
    onChoose: (String) -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AuraCardSurface(modifier = modifier) {
        Text(
            text = stringResource(
                R.string.migrate_review_you_searched,
                item.track.primaryArtist,
                item.track.title,
            ),
            style = AuraType.CalloutTitle,
            color = AuraPalette.OnGround,
            maxLines = 2,
            overflow = AuraDefaultOverflow,
        )
        item.candidates.forEach { candidate ->
            AuraCandidateRow(
                candidate = candidate,
                selected = item.chosenVideoId == candidate.videoId,
                onClick = { onChoose(candidate.videoId) },
            )
        }
        AuraLinkButton(
            text = if (item.resolved && item.chosenVideoId == null) {
                stringResource(R.string.migrate_review_skipped)
            } else {
                stringResource(R.string.migrate_review_skip)
            },
            onClick = onSkip,
            modifier = Modifier.align(Alignment.End),
        )
    }
}

@Composable
private fun AuraCandidateRow(
    candidate: YtmCandidate,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val subtitle = listOfNotNull(
        candidate.primaryArtist.takeIf { it.isNotBlank() },
        candidate.album?.takeIf { it.isNotBlank() },
    ).joinToString(" · ")

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(AuraShapes.Highlight)
            .background(if (selected) AuraPalette.NowPlayingFill else Color.Transparent)
            .border(
                1.dp,
                if (selected) AuraPalette.NowPlayingLine else AuraPalette.SurfaceLine,
                AuraShapes.Highlight,
            )
            .sizeIn(minHeight = AuraSpacing.MinTouchTarget)
            .auraClickableInternal(onClick = onClick, contentDescription = candidate.title)
            .padding(horizontal = 10.dp, vertical = 9.dp),
    ) {
        AuraCover(
            thumbnailUrl = candidate.thumbnailUrl,
            size = 44.dp,
            seed = candidate.videoId,
            decodeTo = 128,
        )
        Column(Modifier.weight(1f)) {
            Text(
                text = candidate.title,
                style = AuraType.RowTitle,
                color = AuraPalette.OnGround,
                maxLines = 1,
                overflow = AuraDefaultOverflow,
            )
            if (subtitle.isNotBlank()) {
                Text(
                    text = subtitle,
                    style = AuraType.RowSubtitle,
                    color = AuraPalette.OnGroundMuted,
                    maxLines = 1,
                    overflow = AuraDefaultOverflow,
                )
            }
        }
        // The classic radio dot, in the redesign's language. Not an AuraSwitch: these are exclusive
        // alternatives, and a row of switches would read as "several can be on".
        Box(
            modifier = Modifier
                .size(20.dp)
                .clip(CircleShape)
                .border(
                    2.dp,
                    if (selected) AuraPalette.Teal else AuraPalette.OnGroundDisabled,
                    CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) {
                Box(
                    Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(AuraPalette.Teal),
                )
            }
        }
    }
}

// ── 2. Tidal ──────────────────────────────────────────────────────────────────────────────────────

/**
 * Tidal — client-id setup, real OAuth (PKCE) sign-in, then import.
 *
 * Every moving part is the classic screen's: the ViewModel is the one scoped to the "migration"
 * back-stack entry (so a prepared Tidal import lands in the SAME confirm/progress/result machinery
 * the picker draws), the authorize URL is opened by the classic `openAuthUrl`, and the redirect
 * arrives through the same [TidalAuthCallbackBus].
 */
@Composable
fun AuraMigrationTidalScreen(navController: NavController) {
    val context = LocalContext.current
    val (savedClientId, setSavedClientId) = rememberPreference(TidalClientIdKey, "")
    var clientIdField by remember(savedClientId) { mutableStateOf(savedClientId) }
    // The id the app will actually use: a pasted one wins, else the one baked into the build.
    val bakedClientId = BuildConfig.TIDAL_CLIENT_ID
    val effectiveClientId = savedClientId.ifBlank { bakedClientId }
    var showClientIdEditor by rememberSaveable { mutableStateOf(false) }

    // Share the picker's ViewModel so a prepared Tidal import reuses the same state machine + engine.
    val migrationEntry = remember(navController) { navController.getBackStackEntry("migration") }
    val viewModel: MigrationViewModel = hiltViewModel(migrationEntry)
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val tidalState by viewModel.tidalState.collectAsStateWithLifecycle()

    var urlField by rememberSaveable { mutableStateOf("") }

    // Re-derive login from the encrypted token vault whenever the screen is shown.
    LaunchedEffect(Unit) { viewModel.refreshTidalAuth() }

    // Open the authorize URL once beginTidalLogin has built it off-Main, then clear it so a
    // recomposition can't re-open the browser.
    LaunchedEffect(tidalState.pendingAuthUrl) {
        tidalState.pendingAuthUrl?.let { url ->
            openAuthUrl(context, url)
            viewModel.consumeTidalAuthUrl()
        }
    }

    // Receive the OAuth redirect (delivered by MainActivity into the callback bus).
    LaunchedEffect(Unit) {
        TidalAuthCallbackBus.events.collect { cb ->
            when {
                !cb.error.isNullOrBlank() -> {
                    viewModel.tidalLoginFailed(cb.error!!)
                    TidalAuthCallbackBus.clear()
                }

                !cb.code.isNullOrBlank() -> {
                    viewModel.completeTidalLogin(cb.code!!, cb.state)
                    TidalAuthCallbackBus.clear()
                }
            }
        }
    }

    // A prepared Tidal import moves the shared state to CONFIRM; hand off to the picker.
    LaunchedEffect(uiState.phase) {
        if (uiState.phase == MigrationPhase.CONFIRM) navController.navigateUp()
    }

    val bloom = rememberAuraBloom(mediaId = null)
    val insets = LocalPlayerAwareWindowInsets.current
    val bottomClearance = insets
        .only(WindowInsetsSides.Bottom)
        .asPaddingValues()
        .calculateBottomPadding()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .auraScreenBackground(bloom, intensity = 0.32f),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(insets.only(WindowInsetsSides.Horizontal))
                .padding(top = auraStatusBarPadding()),
        ) {
            AuraDetailHeader(
                title = stringResource(R.string.migrate_source_tidal),
                onBack = navController::navigateUp,
            )
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(
                        start = AuraSpacing.Gutter,
                        end = AuraSpacing.Gutter,
                        top = 10.dp,
                        bottom = bottomClearance + 48.dp,
                    ),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text(
                    text = stringResource(R.string.migrate_tidal_intro),
                    style = AuraType.RowSubtitle,
                    color = AuraPalette.OnGroundMuted,
                )

                // Client id entry — shown only when there is NO usable id yet, or when the user
                // explicitly opens the advanced override.
                if (effectiveClientId.isBlank() || showClientIdEditor) {
                    AuraCardSurface {
                        Text(
                            text = stringResource(R.string.migrate_tidal_clientid_title),
                            style = AuraType.CalloutTitle,
                            color = AuraPalette.OnGround,
                        )
                        Text(
                            text = stringResource(R.string.migrate_tidal_clientid_how),
                            style = AuraType.RowSubtitle,
                            color = AuraPalette.OnGroundMuted,
                        )
                        AuraInputField(
                            value = clientIdField,
                            onValueChange = { clientIdField = it },
                            label = stringResource(R.string.migrate_tidal_clientid_label),
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            AuraQuietButton(
                                text = stringResource(R.string.migrate_tidal_open_dashboard),
                                onClick = {
                                    openExternal(context, "https://developer.tidal.com/dashboard")
                                },
                                modifier = Modifier.weight(1f),
                            )
                            AuraActionButton(
                                text = stringResource(R.string.migrate_tidal_save),
                                onClick = {
                                    setSavedClientId(clientIdField.trim())
                                    showClientIdEditor = false
                                },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                } else {
                    // Baked-in (or already-saved) id: a status line + the advanced override.
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(R.string.migrate_tidal_clientid_ready),
                            style = AuraType.RowSubtitle,
                            color = AuraPalette.Teal,
                        )
                        AuraLinkButton(
                            text = stringResource(R.string.migrate_tidal_clientid_change),
                            onClick = { showClientIdEditor = true },
                        )
                    }
                }

                // Sign-in / import — available as soon as ANY usable client id exists.
                if (effectiveClientId.isNotBlank()) {
                    if (!tidalState.authenticated) {
                        AuraCardSurface {
                            Text(
                                text = stringResource(R.string.migrate_tidal_login_title),
                                style = AuraType.CalloutTitle,
                                color = AuraPalette.OnGround,
                            )
                            Text(
                                text = stringResource(R.string.migrate_tidal_login_desc),
                                style = AuraType.RowSubtitle,
                                color = AuraPalette.OnGroundMuted,
                            )
                            AuraActionButton(
                                text = stringResource(R.string.migrate_tidal_login),
                                onClick = { viewModel.beginTidalLogin() },
                                enabled = !tidalState.loggingIn,
                                loading = tidalState.loggingIn,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    } else {
                        AuraTidalConnectedCard(
                            state = tidalState,
                            urlField = urlField,
                            onUrlChange = { urlField = it },
                            onImportUrl = { viewModel.prepareTidalImport(urlField.trim()) },
                            onPickPlaylist = { viewModel.prepareTidalPlaylist(it) },
                            onReload = { viewModel.loadTidalCollection() },
                            onLogout = { viewModel.logoutTidal() },
                        )
                    }

                    tidalState.error?.let { message ->
                        AuraNoticeCard(
                            text = message,
                            action = {
                                AuraLinkButton(
                                    text = stringResource(android.R.string.ok),
                                    onClick = { viewModel.dismissTidalError() },
                                )
                            },
                        )
                    }
                }

                // The file route always works, with or without a client id / login.
                Text(
                    text = stringResource(R.string.migrate_tidal_use_file),
                    style = AuraType.RowSubtitle,
                    color = AuraPalette.OnGroundGhost,
                )
            }
        }
    }
}

@Composable
private fun AuraTidalConnectedCard(
    state: TidalAuthUiState,
    urlField: String,
    onUrlChange: (String) -> Unit,
    onImportUrl: () -> Unit,
    onPickPlaylist: (SourcePlaylist) -> Unit,
    onReload: () -> Unit,
    onLogout: () -> Unit,
) {
    AuraCardSurface {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = stringResource(R.string.migrate_tidal_connected),
                style = AuraType.CalloutTitle,
                color = AuraPalette.Teal,
            )
            AuraLinkButton(
                text = stringResource(R.string.migrate_tidal_logout),
                onClick = onLogout,
            )
        }

        Text(
            text = stringResource(R.string.migrate_tidal_pick_title),
            style = AuraType.CalloutTitle,
            color = AuraPalette.OnGround,
        )
        when {
            state.collectionLoading -> AuraLoadingLine(stringResource(R.string.migrate_tidal_loading))

            state.collection.isNotEmpty() -> {
                state.collection.forEach { playlist ->
                    // TidalSource returns the user's LIBRARY (favourites / saved albums / followed
                    // artists) alongside real playlists, and the row is what makes that visible.
                    AuraCollectionRow(playlist = playlist, onClick = { onPickPlaylist(playlist) })
                }
                AuraLinkButton(
                    text = stringResource(R.string.migrate_tidal_reload),
                    onClick = onReload,
                )
            }

            else -> {
                Text(
                    text = stringResource(R.string.migrate_tidal_no_playlists),
                    style = AuraType.RowSubtitle,
                    color = AuraPalette.OnGroundMuted,
                )
                AuraLinkButton(
                    text = stringResource(R.string.migrate_tidal_reload),
                    onClick = onReload,
                )
            }
        }

        AuraDivider()

        // URL field: the minimal, always-works path — import a specific playlist by link.
        Text(
            text = stringResource(R.string.migrate_tidal_url_hint),
            style = AuraType.RowSubtitle,
            color = AuraPalette.OnGroundMuted,
        )
        AuraInputField(
            value = urlField,
            onValueChange = onUrlChange,
            label = stringResource(R.string.migrate_tidal_url_label),
            placeholder = "https://tidal.com/playlist/…",
        )
        AuraActionButton(
            text = stringResource(R.string.migrate_tidal_import),
            onClick = onImportUrl,
            enabled = urlField.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

// ── 3. Apple Music ────────────────────────────────────────────────────────────────────────────────

/**
 * Apple has a native transfer to YouTube Music on its Data & Privacy page, and since YTM IS Aura's
 * catalog the transferred playlists surface on a library refresh — there is no code migration to
 * build. This screen renders [AppleMusicGuide]'s steps and limitations and opens privacy.apple.com.
 */
@Composable
fun AuraMigrationAppleScreen(navController: NavController) {
    val context = LocalContext.current
    val bloom = rememberAuraBloom(mediaId = null)
    val insets = LocalPlayerAwareWindowInsets.current
    val bottomClearance = insets
        .only(WindowInsetsSides.Bottom)
        .asPaddingValues()
        .calculateBottomPadding()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .auraScreenBackground(bloom, intensity = 0.32f),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(insets.only(WindowInsetsSides.Horizontal))
                .padding(top = auraStatusBarPadding()),
        ) {
            AuraDetailHeader(
                title = stringResource(R.string.migrate_source_apple),
                onBack = navController::navigateUp,
            )
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(
                        start = AuraSpacing.Gutter,
                        end = AuraSpacing.Gutter,
                        top = 10.dp,
                        bottom = bottomClearance + 48.dp,
                    ),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text(
                    text = stringResource(R.string.migrate_apple_intro),
                    style = AuraType.RowSubtitle,
                    color = AuraPalette.OnGroundMuted,
                )

                AppleMusicGuide.STEPS.forEach { step ->
                    AuraCardSurface {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                            verticalAlignment = Alignment.Top,
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(AuraPalette.Teal.copy(alpha = 0.16f)),
                                contentAlignment = Alignment.Center,
                            ) {
                                AuraTechnicalText(
                                    text = step.number.toString(),
                                    color = AuraPalette.Teal,
                                    style = AuraType.QualityBadge,
                                )
                            }
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    text = step.title,
                                    style = AuraType.CalloutTitle,
                                    color = AuraPalette.OnGround,
                                    maxLines = 2,
                                    overflow = AuraDefaultOverflow,
                                )
                                Text(
                                    text = step.detail,
                                    style = AuraType.RowSubtitle,
                                    color = AuraPalette.OnGroundMuted,
                                )
                            }
                        }
                    }
                }

                AuraActionButton(
                    text = stringResource(R.string.migrate_apple_open_privacy),
                    onClick = { openExternal(context, AppleMusicGuide.PRIVACY_URL) },
                    modifier = Modifier.fillMaxWidth(),
                )

                AuraSectionLabel(
                    stringResource(R.string.migrate_apple_limitations_title).uppercase(Locale.ROOT),
                )
                AuraCardSurface {
                    AppleMusicGuide.LIMITATIONS.forEach { limitation ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = "•",
                                style = AuraType.RowSubtitle,
                                color = AuraPalette.Teal,
                            )
                            Text(
                                text = limitation,
                                style = AuraType.RowSubtitle,
                                color = AuraPalette.OnGroundMuted,
                            )
                        }
                    }
                }
            }
        }
    }
}

// ── Shared migration pieces ───────────────────────────────────────────────────────────────────────

/**
 * The warning tone.
 *
 * [AuraPalette] has no error step — the reference render draws no failure state — and this wizard
 * has plenty of them. The tone is taken from Material's DARK error role rather than the ambient
 * theme's: on the light theme `colorScheme.error` is a deep red that disappears into
 * [AuraPalette.Ground], and the redesign always paints its own near-black ground.
 */
private val AuraWarnTone: Color = darkColorScheme().error

/** A source of the picker: teal glyph, title, description, chevron. */
@Composable
private fun AuraSourceRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        modifier = modifier
            .fillMaxWidth()
            .clip(AuraShapes.Card)
            .background(AuraPalette.SurfaceFill)
            .border(1.dp, AuraPalette.SurfaceLine, AuraShapes.Card)
            .sizeIn(minHeight = AuraSpacing.MinTouchTarget)
            .auraClickableInternal(onClick = onClick, contentDescription = title)
            .padding(horizontal = 14.dp, vertical = 13.dp),
    ) {
        AuraIconGlyph(icon = icon, contentDescription = null, size = 20.dp, tint = AuraPalette.Teal)
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = AuraType.RowTitle,
                color = AuraPalette.OnGround,
                maxLines = 1,
                overflow = AuraDefaultOverflow,
            )
            Text(
                text = description,
                style = AuraType.CalloutSubtitle,
                color = AuraPalette.OnGroundMuted,
                maxLines = 3,
                overflow = AuraDefaultOverflow,
            )
        }
        AuraIconGlyph(
            icon = AuraIcons.ChevronRight,
            contentDescription = null,
            size = 18.dp,
            tint = AuraPalette.OnGroundDisabled,
        )
    }
}

/** The flat, hairlined card every step of the wizard sits in. */
@Composable
private fun AuraCardSurface(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(AuraShapes.Card)
            .background(AuraPalette.SurfaceFill)
            .border(1.dp, AuraPalette.SurfaceLine, AuraShapes.Card)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(11.dp),
        content = content,
    )
}

/** A blocking failure or precondition, as a card. [action] is the optional dismiss/next control. */
@Composable
private fun AuraNoticeCard(
    text: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier
            .fillMaxWidth()
            .clip(AuraShapes.Card)
            .background(AuraWarnTone.copy(alpha = 0.10f))
            .border(1.dp, AuraWarnTone.copy(alpha = 0.30f), AuraShapes.Card)
            .padding(14.dp),
    ) {
        AuraWarnGlyph()
        Text(
            text = text,
            style = AuraType.RowSubtitle,
            color = AuraPalette.OnGround,
            modifier = Modifier.weight(1f),
        )
        action?.invoke()
    }
}

/** The same warning, inline inside a card. */
@Composable
private fun AuraNoticeLine(text: String, modifier: Modifier = Modifier) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        AuraWarnGlyph(size = 16.dp)
        Text(
            text = text,
            style = AuraType.CalloutSubtitle,
            color = AuraWarnTone,
        )
    }
}

/**
 * The app's own warning vector. [AuraIcons] has no warning symbol (the render draws no failure
 * state) and the rule is not to substitute a Material icon for one of its hand-drawn glyphs — so
 * this uses the drawable the classic migration screen already draws, tinted.
 */
@Composable
private fun AuraWarnGlyph(size: androidx.compose.ui.unit.Dp = 20.dp) {
    androidx.compose.material3.Icon(
        painter = painterResource(R.drawable.warning),
        contentDescription = null,
        tint = AuraWarnTone,
        modifier = Modifier.size(size),
    )
}

/** An indeterminate wait with its own label ("Leyendo el perfil…", "Cargando tus listas…"). */
@Composable
private fun AuraLoadingLine(text: String, modifier: Modifier = Modifier) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(18.dp),
            strokeWidth = 2.dp,
            color = AuraPalette.Teal,
        )
        Text(
            text = text,
            style = AuraType.RowSubtitle,
            color = AuraPalette.OnGroundMuted,
        )
    }
}

/**
 * Determinate progress. Drawn as two rounded boxes rather than a `LinearProgressIndicator` so it
 * carries the palette; the fraction only changes once per resolved track, so no per-frame work.
 */
@Composable
private fun AuraLinearProgress(fraction: Float, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(6.dp)
            .clip(AuraShapes.Pill)
            .background(AuraPalette.TrackEmpty),
    ) {
        Box(
            Modifier
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .fillMaxHeight()
                .clip(AuraShapes.Pill)
                .background(AuraPalette.Teal),
        )
    }
}

/** The affirmative button: teal fill, dark ink. [loading] swaps the label for a spinner. */
@Composable
private fun AuraActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
) {
    Box(
        modifier = modifier
            .sizeIn(minHeight = 52.dp)
            .clip(AuraShapes.Card)
            .background(
                if (enabled) AuraPalette.Teal else AuraPalette.Teal.copy(alpha = 0.28f),
            )
            .auraClickableInternal(
                onClick = onClick,
                enabled = enabled,
                contentDescription = text,
            )
            .padding(horizontal = 20.dp, vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.dp,
                color = AuraPalette.OnAccent,
            )
        } else {
            Text(
                text = text,
                style = AuraType.Chip,
                color = AuraPalette.OnAccent,
                maxLines = 1,
                overflow = AuraDefaultOverflow,
            )
        }
    }
}

/** The secondary button: the wash + hairline every other Aura surface uses. */
@Composable
private fun AuraQuietButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Box(
        modifier = modifier
            .sizeIn(minHeight = 52.dp)
            .clip(AuraShapes.Card)
            .background(AuraPalette.SurfaceFill)
            .border(1.dp, AuraPalette.SurfaceLine, AuraShapes.Card)
            .auraClickableInternal(
                onClick = onClick,
                enabled = enabled,
                contentDescription = text,
            )
            .padding(horizontal = 20.dp, vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = AuraType.Chip,
            color = if (enabled) AuraPalette.OnGround else AuraPalette.OnGroundDisabled,
            maxLines = 1,
            overflow = AuraDefaultOverflow,
        )
    }
}

/** The tertiary, text-only action ("Migrar otra", "Recargar listas", "Omitir"). */
@Composable
private fun AuraLinkButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Box(
        modifier = modifier
            .sizeIn(minHeight = AuraSpacing.MinTouchTarget)
            .clip(AuraShapes.Pill)
            .auraClickableInternal(
                onClick = onClick,
                enabled = enabled,
                contentDescription = text,
            )
            .padding(horizontal = 14.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = AuraType.Chip,
            color = if (enabled) AuraPalette.Teal else AuraPalette.OnGroundDisabled,
            maxLines = 1,
            overflow = AuraDefaultOverflow,
        )
    }
}

/**
 * A single-line text entry in the redesign's language, built the same way the Ajustes search field
 * is: a `SurfaceFill` box with a hairline holding a [BasicTextField].
 */
@Composable
private fun AuraInputField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        AuraSectionLabel(label.uppercase(Locale.ROOT))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(AuraShapes.Card)
                .background(AuraPalette.SurfaceFill)
                .border(1.dp, AuraPalette.SurfaceLine, AuraShapes.Card)
                .sizeIn(minHeight = AuraSpacing.MinTouchTarget)
                .padding(horizontal = 14.dp, vertical = 13.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            if (value.isEmpty() && placeholder != null) {
                Text(
                    text = placeholder,
                    style = AuraType.MenuLabel,
                    color = AuraPalette.OnGroundGhost,
                    maxLines = 1,
                    overflow = AuraDefaultOverflow,
                )
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = AuraType.MenuLabel.copy(color = AuraPalette.OnGround),
                cursorBrush = SolidColor(AuraPalette.Teal),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                // TalkBack has no floating label to read off a BasicTextField, so the field carries
                // the same name the visible label above it shows.
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { contentDescription = label },
            )
        }
    }
}
