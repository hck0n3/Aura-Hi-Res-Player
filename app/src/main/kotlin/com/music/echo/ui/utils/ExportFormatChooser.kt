package iad1tya.echo.music.ui.utils

import android.net.ConnectivityManager
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.getSystemService
import iad1tya.echo.music.R
import iad1tya.echo.music.ui.component.DefaultDialog
import iad1tya.echo.music.ui.newui.AuraPalette
import iad1tya.echo.music.ui.newui.AuraType
import iad1tya.echo.music.utils.YTPlayerUtils
import iad1tya.echo.music.utils.isLocalMediaId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

enum class ExportFormat {
    Mp3,
    Video,
}

private sealed class VideoProbe {
    data object Loading : VideoProbe()
    data object Available : VideoProbe()
    data class Unavailable(val messageRes: Int) : VideoProbe()
}

/**
 * Frost chooser: MP3 vs video. Video is enabled only after a live stream probe succeeds
 * (not merely [iad1tya.echo.music.models.MediaMetadata.isVideoSong], which is often false for YTM
 * catalog rows that still have a video stream).
 */
@Composable
fun ExportFormatChooserDialog(
    songId: String,
    onDismiss: () -> Unit,
    onChoose: (ExportFormat) -> Unit,
) {
    val context = LocalContext.current
    var videoProbe by remember(songId) { mutableStateOf<VideoProbe>(VideoProbe.Loading) }

    LaunchedEffect(songId) {
        if (songId.isBlank() || songId.isLocalMediaId()) {
            videoProbe = VideoProbe.Unavailable(R.string.export_video_unavailable)
            return@LaunchedEffect
        }
        videoProbe = VideoProbe.Loading
        val result = withContext(Dispatchers.IO) {
            withTimeoutOrNull(20_000L) {
                val cm = context.getSystemService<ConnectivityManager>()
                    ?: return@withTimeoutOrNull VideoProbe.Unavailable(R.string.export_video_unavailable)
                val diag = YTPlayerUtils.videoStreamUrlDiag(
                    videoId = songId,
                    connectivityManager = cm,
                    videoMaxHeight = 720,
                )
                diag.fold(
                    onSuccess = { url ->
                        if (url.isNotBlank()) VideoProbe.Available
                        else VideoProbe.Unavailable(R.string.export_video_unavailable)
                    },
                    onFailure = {
                        VideoProbe.Unavailable(R.string.export_video_probe_failed)
                    },
                )
            } ?: VideoProbe.Unavailable(R.string.export_video_probe_timeout)
        }
        videoProbe = result
    }

    val videoEnabled = videoProbe is VideoProbe.Available
    val videoDescription = when (val p = videoProbe) {
        VideoProbe.Loading -> stringResource(R.string.export_video_probing)
        VideoProbe.Available -> stringResource(R.string.export_as_video_desc)
        is VideoProbe.Unavailable -> stringResource(p.messageRes)
    }

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
            Text(
                text = stringResource(R.string.export_vs_download_hint),
                style = AuraType.RowSubtitle,
                color = AuraPalette.OnGroundFaint,
                modifier = Modifier.padding(bottom = 12.dp),
            )
            ExportFormatRow(
                title = stringResource(R.string.export_as_mp3),
                description = stringResource(R.string.export_as_mp3_desc),
                enabled = true,
                trailing = null,
                onClick = {
                    onChoose(ExportFormat.Mp3)
                    onDismiss()
                },
            )
            Spacer(modifier = Modifier.height(8.dp))
            ExportFormatRow(
                title = stringResource(R.string.export_as_video),
                description = videoDescription,
                enabled = videoEnabled,
                trailing = if (videoProbe is VideoProbe.Loading) {
                    {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = AuraPalette.Teal,
                        )
                    }
                } else {
                    null
                },
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
    trailing: (@Composable () -> Unit)?,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 4.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                style = AuraType.MenuLabel,
                color = if (enabled) AuraPalette.OnGround else AuraPalette.OnGroundFaint,
                modifier = Modifier.weight(1f),
            )
            trailing?.invoke()
        }
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = description,
            style = AuraType.RowSubtitle,
            color = AuraPalette.OnGroundFaint,
        )
    }
}
