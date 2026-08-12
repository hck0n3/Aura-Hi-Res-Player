@file:OptIn(ExperimentalMaterial3Api::class)

package iad1tya.echo.music.ui.screens.settings

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import iad1tya.echo.music.LocalPlayerAwareWindowInsets
import iad1tya.echo.music.R
import iad1tya.echo.music.ui.component.IconButton
import iad1tya.echo.music.ui.newui.AuraPalette
import iad1tya.echo.music.ui.newui.rememberAuraPanelSkin
import iad1tya.echo.music.utils.SupportContact

/**
 * Ajustes ▸ Reportar y sugerir — opens the user's mail app to [SupportContact.EMAIL].
 * Nothing is sent without the user confirming in their own mail client.
 */
@Composable
fun FeedbackScreen(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
) {
    val context = LocalContext.current
    val skin = rememberAuraPanelSkin()
    val ground = if (skin.enabled && skin.darkGround) AuraPalette.Ground else MaterialTheme.colorScheme.surface
    val ink = if (skin.enabled) skin.ink else MaterialTheme.colorScheme.onSurface

    var kind by remember { mutableStateOf(SupportContact.Kind.BUG) }
    var message by remember { mutableStateOf("") }
    var attachLogs by remember { mutableStateOf(true) }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = ground,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            LargeTopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.feedback_title),
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = navController::navigateUp,
                        onLongClick = null,
                    ) {
                        Icon(painterResource(R.drawable.arrow_back), contentDescription = null)
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .windowInsetsPadding(
                    LocalPlayerAwareWindowInsets.current.only(
                        WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom,
                    ),
                )
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            Text(
                text = stringResource(R.string.feedback_howto_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = ink,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.feedback_howto_body, SupportContact.EMAIL),
                style = MaterialTheme.typography.bodyMedium,
                color = ink.copy(alpha = 0.85f),
            )
            Spacer(Modifier.height(16.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = kind == SupportContact.Kind.BUG,
                    onClick = {
                        kind = SupportContact.Kind.BUG
                        attachLogs = true
                    },
                    label = { Text(stringResource(R.string.feedback_kind_bug)) },
                )
                FilterChip(
                    selected = kind == SupportContact.Kind.SUGGESTION,
                    onClick = {
                        kind = SupportContact.Kind.SUGGESTION
                        attachLogs = false
                    },
                    label = { Text(stringResource(R.string.feedback_kind_suggestion)) },
                )
            }

            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = message,
                onValueChange = { message = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp),
                placeholder = { Text(stringResource(R.string.feedback_message_hint)) },
            )

            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.feedback_attach_logs),
                        style = MaterialTheme.typography.bodyLarge,
                        color = ink,
                    )
                    Text(
                        text = stringResource(R.string.feedback_attach_logs_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = ink.copy(alpha = 0.7f),
                    )
                }
                Switch(checked = attachLogs, onCheckedChange = { attachLogs = it })
            }

            Spacer(Modifier.height(16.dp))
            Button(
                onClick = {
                    val ok = SupportContact.openFeedback(
                        context = context,
                        kind = kind,
                        userMessage = message,
                        attachLogs = attachLogs,
                    )
                    if (!ok) {
                        Toast.makeText(
                            context,
                            context.getString(R.string.feedback_no_mail_app),
                            Toast.LENGTH_LONG,
                        ).show()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.feedback_send))
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.feedback_send_hint, SupportContact.EMAIL),
                style = MaterialTheme.typography.bodySmall,
                color = ink.copy(alpha = 0.7f),
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}
