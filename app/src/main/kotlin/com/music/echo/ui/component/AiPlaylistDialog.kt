package iad1tya.echo.music.ui.component

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
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
import iad1tya.echo.music.playlistimport.AiPlaylistGenerator
import iad1tya.echo.music.utils.rememberPreference
import iad1tya.echo.music.viewmodels.AiPlaylistUiState
import iad1tya.echo.music.viewmodels.AiPlaylistViewModel

/**
 * Dialog for the "Lista AI" feature: the user types an idea and picks how many songs; the AI builds
 * a track list which is resolved against the catalog into a new local playlist. Reuses the AI
 * provider/key the user already configured for lyric translation (BYO key).
 */
@Composable
fun AiPlaylistDialog(
    onDismiss: () -> Unit,
    onPlaylistCreated: (String) -> Unit,
    onOpenAiSettings: () -> Unit,
    viewModel: AiPlaylistViewModel = hiltViewModel(),
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
    var count by rememberSaveable { mutableStateOf(20) }

    LaunchedEffect(state) {
        val current = state
        if (current is AiPlaylistUiState.Success) {
            Toast.makeText(
                context,
                context.getString(
                    R.string.ai_playlist_resolved_count,
                    current.resolved,
                    current.total,
                ),
                Toast.LENGTH_SHORT,
            ).show()
            val playlistId = current.playlistId
            viewModel.reset()
            onPlaylistCreated(playlistId)
        }
    }

    val busy = state is AiPlaylistUiState.Generating || state is AiPlaylistUiState.Resolving

    AlertDialog(
        onDismissRequest = { if (!busy) { viewModel.reset(); onDismiss() } },
        icon = { Icon(painterResource(R.drawable.auto_awesome), contentDescription = null) },
        title = { Text(stringResource(R.string.ai_playlist_title)) },
        text = {
            Column {
                OutlinedTextField(
                    value = prompt,
                    onValueChange = { prompt = it },
                    label = { Text(stringResource(R.string.ai_playlist_prompt_hint)) },
                    enabled = !busy,
                    minLines = 2,
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    text = stringResource(R.string.ai_playlist_count_label),
                    style = MaterialTheme.typography.labelLarge,
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(10, 20, 30, 50).forEach { option ->
                        FilterChip(
                            selected = count == option,
                            onClick = { count = option },
                            enabled = !busy,
                            label = { Text(option.toString()) },
                        )
                    }
                }

                when (val current = state) {
                    AiPlaylistUiState.Generating ->
                        BusyRow(stringResource(R.string.ai_playlist_generating))

                    is AiPlaylistUiState.Resolving ->
                        BusyRow(
                            stringResource(
                                R.string.ai_playlist_resolving,
                                current.done,
                                current.total,
                            ),
                        )

                    is AiPlaylistUiState.Error -> {
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
            TextButton(
                onClick = {
                    viewModel.generate(prompt.trim(), count, provider, apiKey, baseUrl, model)
                },
                enabled = !busy && prompt.isNotBlank(),
            ) {
                Text(stringResource(R.string.ai_playlist_generate))
            }
        },
        dismissButton = {
            TextButton(
                onClick = { if (!busy) { viewModel.reset(); onDismiss() } },
                enabled = !busy,
            ) {
                Text(stringResource(android.R.string.cancel))
            }
        },
    )
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
    cause is AiPlaylistService.MissingApiKeyException ||
        cause is AiPlaylistService.UnsupportedProviderException

private fun errorMessage(context: Context, cause: Throwable): String = when (cause) {
    is AiPlaylistService.MissingApiKeyException ->
        context.getString(R.string.ai_playlist_error_no_api_key)

    is AiPlaylistService.UnsupportedProviderException ->
        context.getString(R.string.ai_playlist_error_unsupported_provider)

    is AiPlaylistGenerator.EmptyResultException ->
        context.getString(R.string.ai_playlist_error_no_tracks)

    else -> context.getString(R.string.ai_playlist_error_generic)
}
