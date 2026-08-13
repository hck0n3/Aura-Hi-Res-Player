package iad1tya.echo.music.ui.newui

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.core.net.toUri
import iad1tya.echo.music.R
import iad1tya.echo.music.notices.OwnerAnnouncements
import kotlinx.coroutines.launch

/**
 * Frost popup for the newest unread owner notice. Shown when [OwnerAnnouncements.popupNotice]
 * is non-null (after open/resume refresh or hourly poll).
 * Outside/back only snoozes; [R.string.owner_notices_popup_ok] marks read (stays in Avisos
 * until 24h after publish) and advances the popup.
 */
@Composable
fun OwnerNoticePopupHost(
    enabled: Boolean = true,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val notice by OwnerAnnouncements.popupNotice.collectAsState()
    val newUi = rememberNewUiEnabled()

    if (!enabled) return
    val current = notice ?: return

    if (newUi) AuraDialogWindowEffects(enabled = true)

    AlertDialog(
        onDismissRequest = {
            // Outside/back = mark read too (owner: already-read must never flash again on next open).
            scope.launch { OwnerAnnouncements.acknowledgePopup(context) }
        },
        properties = DialogProperties(dismissOnClickOutside = true, dismissOnBackPress = true),
        confirmButton = {
            TextButton(
                onClick = {
                    scope.launch {
                        current.url?.let { url ->
                            runCatching {
                                context.startActivity(
                                    Intent(Intent.ACTION_VIEW, url.toUri())
                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                                )
                            }
                        }
                        OwnerAnnouncements.acknowledgePopup(context)
                    }
                },
            ) {
                Text(
                    text = stringResource(R.string.owner_notices_popup_ok),
                    color = if (newUi) AuraPalette.Teal else androidx.compose.material3.MaterialTheme.colorScheme.primary,
                )
            }
        },
        title = {
            Text(
                text = current.title,
                style = if (newUi) AuraType.RowTitle else androidx.compose.material3.MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (newUi) AuraPalette.OnGround else androidx.compose.material3.MaterialTheme.colorScheme.onSurface,
            )
        },
        text = {
            val bodyScroll = rememberScrollState()
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .verticalScroll(bodyScroll),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = stringResource(R.string.owner_notices_popup_eyebrow),
                    style = if (newUi) AuraType.SectionLabel else androidx.compose.material3.MaterialTheme.typography.labelSmall,
                    color = if (newUi) AuraPalette.OnGroundMuted else androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = current.body,
                    style = if (newUi) AuraType.RowSubtitle else androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                    color = if (newUi) AuraPalette.OnGround else androidx.compose.material3.MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        },
        containerColor = if (newUi) AuraPalette.FrostFill else androidx.compose.material3.MaterialTheme.colorScheme.surface,
    )
}

/** Force a notices pull when this composable enters composition (e.g. shell visible). */
@Composable
fun OwnerNoticesWarmup() {
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        OwnerAnnouncements.loadCache(context)
        OwnerAnnouncements.refresh(context, force = true)
    }
}
