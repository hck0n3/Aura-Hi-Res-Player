package iad1tya.echo.music.ui.utils

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import iad1tya.echo.music.R
import iad1tya.echo.music.ui.component.DefaultDialog
import iad1tya.echo.music.ui.newui.AuraPalette
import iad1tya.echo.music.ui.newui.AuraType

enum class ExportFormat {
    Mp3,
    Video,
}

/**
 * Frost chooser: MP3 vs video. Video option only when [videoAvailable].
 */
@Composable
fun ExportFormatChooserDialog(
    videoAvailable: Boolean,
    onDismiss: () -> Unit,
    onChoose: (ExportFormat) -> Unit,
) {
    DefaultDialog(
        onDismiss = onDismiss,
        title = { Text(text = stringResource(R.string.export_choose_format_title)) },
        buttons = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(android.R.string.cancel))
            }
        },
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            ExportFormatRow(
                title = stringResource(R.string.export_as_mp3),
                description = stringResource(R.string.export_as_mp3_desc),
                enabled = true,
                onClick = {
                    onChoose(ExportFormat.Mp3)
                    onDismiss()
                },
            )
            Spacer(modifier = Modifier.height(8.dp))
            ExportFormatRow(
                title = stringResource(R.string.export_as_video),
                description = if (videoAvailable) {
                    stringResource(R.string.export_as_video_desc)
                } else {
                    stringResource(R.string.export_video_unavailable)
                },
                enabled = videoAvailable,
                onClick = {
                    onChoose(ExportFormat.Video)
                    onDismiss()
                },
            )
        }
    }
}

@Composable
private fun ExportFormatRow(
    title: String,
    description: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 4.dp),
    ) {
        Text(
            text = title,
            style = AuraType.MenuLabel,
            color = if (enabled) AuraPalette.OnGround else AuraPalette.OnGroundFaint,
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = description,
            style = AuraType.RowSubtitle,
            color = AuraPalette.OnGroundFaint,
        )
    }
}
