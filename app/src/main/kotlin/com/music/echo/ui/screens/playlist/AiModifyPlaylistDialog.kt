package iad1tya.echo.music.ui.screens.playlist

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import iad1tya.echo.music.R
import iad1tya.echo.music.api.AiPlaylistService
import iad1tya.echo.music.constants.AiProviderKey
import iad1tya.echo.music.constants.OpenRouterApiKey
import iad1tya.echo.music.constants.OpenRouterBaseUrlKey
import iad1tya.echo.music.constants.OpenRouterModelKey
import iad1tya.echo.music.db.entities.PlaylistSong
import iad1tya.echo.music.playlistimport.AiPlaylistPlaylistModifier
import iad1tya.echo.music.utils.rememberPreference
import iad1tya.echo.music.viewmodels.AiModifyPlaylistUiState
import iad1tya.echo.music.viewmodels.AiModifyPlaylistViewModel

/**
 * Dialog for "Editar con IA": the user describes a change in plain language, Aura's keyless AI
 * proposes it, and — unlike a blind apply — the proposal is shown as a PREVIEW that the user must
 * confirm before anything is written. Removals are destructive and the AI can be wrong, so an edit
 * never happens unattended.
 *
 * If the AI is unavailable the dialog says so and NOTHING changes (no-op); it never guesses an edit.
 */
@Composable
fun AiModifyPlaylistDialog(
    playlistId: String,
    songs: List<PlaylistSong>,
    onDismiss: () -> Unit,
    onOpenAiSettings: () -> Unit,
    viewModel: AiModifyPlaylistViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsState()

    val provider by rememberPreference(AiProviderKey, "OpenRouter")
    val apiKey by rememberPreference(OpenRouterApiKey, "")
    val baseUrl by rememberPreference(
        OpenRouterBaseUrlKey,
        "https://openrouter.ai/api/v1/chat/completions",
    )
    val model by rememberPreference(OpenRouterModelKey, "google/gemini-2.5-flash-lite")

    var prompt by rememberSaveable { mutableStateOf("") }

    // The plan under review. Held here (rather than read straight off the state) so the preview stays
    // on screen while Applying runs; cleared on failure, which returns the user to the prompt field.
    var pendingPlan by remember { mutableStateOf<AiPlaylistPlaylistModifier.Plan?>(null) }
    LaunchedEffect(state) {
        when (val current = state) {
            is AiModifyPlaylistUiState.Preview -> pendingPlan = current.plan
            is AiModifyPlaylistUiState.Error -> pendingPlan = null
            AiModifyPlaylistUiState.Idle -> pendingPlan = null
            else -> {}
        }
    }

    LaunchedEffect(state) {
        val current = state
        if (current is AiModifyPlaylistUiState.Applied) {
            Toast.makeText(
                context,
                context.getString(
                    R.string.ai_modify_playlist_applied,
                    current.removed,
                    current.added,
                ),
                Toast.LENGTH_SHORT,
            ).show()
            viewModel.reset()
            onDismiss()
        }
    }

    val busy = state is AiModifyPlaylistUiState.Planning || state is AiModifyPlaylistUiState.Applying
    val close = {
        if (!busy) {
            viewModel.reset()
            onDismiss()
        }
    }

    AlertDialog(
        onDismissRequest = close,
        icon = { Icon(painterResource(R.drawable.auto_awesome), contentDescription = null) },
        title = { Text(stringResource(R.string.ai_modify_playlist_title)) },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 380.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                val plan = pendingPlan
                if (plan == null) {
                    OutlinedTextField(
                        value = prompt,
                        onValueChange = { prompt = it },
                        label = { Text(stringResource(R.string.ai_modify_playlist_prompt_hint)) },
                        enabled = !busy,
                        minLines = 2,
                        maxLines = 4,
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    // CONFIRMATION PREVIEW — exactly what will be committed, nothing hidden.
                    PlanPreview(plan)
                }

                when (val current = state) {
                    AiModifyPlaylistUiState.Planning ->
                        BusyRow(stringResource(R.string.ai_modify_playlist_planning))

                    AiModifyPlaylistUiState.Applying ->
                        BusyRow(stringResource(R.string.ai_modify_playlist_applying))

                    is AiModifyPlaylistUiState.Error -> {
                        Spacer(Modifier.height(16.dp))
                        Text(
                            text = errorMessage(context, current.cause),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        if (showsSettingsAction(current.cause)) {
                            TextButton(onClick = onOpenAiSettings) {
                                Text(stringResource(R.string.ai_playlist_open_ai_settings))
                            }
                        }
                    }

                    else -> {}
                }
            }
        },
        confirmButton = {
            val plan = pendingPlan
            if (plan == null) {
                TextButton(
                    onClick = {
                        viewModel.plan(
                            playlistId = playlistId,
                            songs = songs,
                            prompt = prompt.trim(),
                            provider = provider,
                            apiKey = apiKey,
                            baseUrl = baseUrl,
                            model = model,
                        )
                    },
                    enabled = !busy && prompt.isNotBlank(),
                ) {
                    Text(stringResource(R.string.ai_modify_playlist_review))
                }
            } else {
                TextButton(
                    onClick = { viewModel.apply(plan) },
                    enabled = !busy,
                ) {
                    Text(stringResource(R.string.ai_modify_playlist_apply))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = close, enabled = !busy) {
                Text(stringResource(android.R.string.cancel))
            }
        },
    )
}

/** The planned edit, spelled out: every removal and every addition the user is about to approve. */
@Composable
private fun PlanPreview(plan: AiPlaylistPlaylistModifier.Plan) {
    Text(
        text = stringResource(R.string.ai_modify_playlist_preview_intro),
        style = MaterialTheme.typography.bodyMedium,
    )

    if (plan.removals.isNotEmpty()) {
        Spacer(Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.ai_modify_playlist_will_remove, plan.removals.size),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.error,
        )
        plan.removals.forEach { removal ->
            Text(
                text = "− ${removal.title} — ${removal.artist}",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }

    if (plan.additions.isNotEmpty()) {
        Spacer(Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.ai_modify_playlist_will_add, plan.additions.size),
            style = MaterialTheme.typography.labelLarge,
        )
        plan.additions.forEach { song ->
            Text(
                text = "+ ${song.title} — ${song.artists.joinToString(", ") { it.name }}",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }

    if (plan.skippedAdditions > 0) {
        Spacer(Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.ai_modify_playlist_skipped, plan.skippedAdditions),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun BusyRow(text: String) {
    Spacer(Modifier.height(16.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
        Spacer(Modifier.width(12.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

private fun showsSettingsAction(cause: Throwable): Boolean =
    cause is AiPlaylistService.AiServiceUnavailableException ||
        cause is AiPlaylistService.UnsupportedProviderException

private fun errorMessage(context: Context, cause: Throwable): String = when (cause) {
    is AiPlaylistService.AiServiceUnavailableException ->
        context.getString(R.string.ai_modify_playlist_error_unavailable)

    is AiPlaylistService.UnsupportedProviderException ->
        context.getString(R.string.ai_playlist_error_unsupported_provider)

    is AiPlaylistPlaylistModifier.NoChangesException ->
        context.getString(R.string.ai_modify_playlist_error_no_changes)

    else -> context.getString(R.string.ai_modify_playlist_error_generic)
}
