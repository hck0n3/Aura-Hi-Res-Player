package iad1tya.echo.music.ui.component

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import iad1tya.echo.music.R

@Composable
fun RingtoneProgressDialog(
    isVisible: Boolean,
    progress: Float,
    statusMessage: String,
    isComplete: Boolean,
    isSuccess: Boolean,
    // False when the ringtone was saved but could NOT be set as default (no WRITE_SETTINGS):
    // the dialog then also offers the "grant permission" action.
    appliedDirectly: Boolean = true,
    onDismiss: () -> Unit,
    onOpenSettings: () -> Unit,
    onRequestWriteSettings: () -> Unit = {}
) {
    if (!isVisible) return

    AlertDialog(
        onDismissRequest = {
            if (isComplete) onDismiss()
        },
        title = {
            Text(
                if (isComplete) {
                    if (isSuccess) "Success!" else "Failed"
                } else {
                    "Setting Ringtone..."
                },
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = if (isComplete && !isSuccess) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(16.dp))
                
                if (!isComplete) {
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = statusMessage,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            if (isComplete) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (isSuccess && !appliedDirectly) {
                        // Ringtone saved but not applied (no WRITE_SETTINGS): offer the grant so the
                        // NEXT trim can apply directly, alongside the existing picker fallback.
                        TextButton(onClick = onRequestWriteSettings) {
                            Text(stringResource(R.string.ringtone_grant_write_settings))
                        }
                    }
                    Button(
                        onClick = {
                            if (isSuccess) onOpenSettings() else onDismiss()
                        },
                    ) {
                        Text(if (isSuccess) "Open Settings" else "Close")
                    }
                }
            }
        },
        dismissButton = {
            if (isComplete && isSuccess) {
                TextButton(onClick = onDismiss) {
                    Text("Cerrar")
                }
            }
        }
    )
}
