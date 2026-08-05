

package iad1tya.echo.music.ui.screens.settings.integrations

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import iad1tya.echo.music.LocalPlayerAwareWindowInsets
import iad1tya.echo.music.R
import iad1tya.echo.music.constants.ListenTogetherAutoApprovalKey
import iad1tya.echo.music.constants.ListenTogetherServerUrlKey
import iad1tya.echo.music.constants.ListenTogetherSmartResyncKey
import iad1tya.echo.music.constants.ListenTogetherSyncVolumeKey
import iad1tya.echo.music.constants.ListenTogetherUsernameKey
import iad1tya.echo.music.listentogether.ListenTogetherEvent
import iad1tya.echo.music.listentogether.ListenTogetherServer
import iad1tya.echo.music.listentogether.ListenTogetherServers
import iad1tya.echo.music.listentogether.LogEntry
import iad1tya.echo.music.listentogether.LogLevel
import iad1tya.echo.music.listentogether.RoomRole
import iad1tya.echo.music.ui.component.DefaultDialog
import iad1tya.echo.music.ui.component.IconButton
import iad1tya.echo.music.ui.component.IntegrationCard
import iad1tya.echo.music.ui.component.IntegrationCardItem
import iad1tya.echo.music.ui.newui.AuraPanel
import iad1tya.echo.music.ui.newui.AuraShapes
import iad1tya.echo.music.ui.newui.AuraSpacing
import iad1tya.echo.music.ui.newui.AuraType
import iad1tya.echo.music.ui.newui.rememberAuraPanelSkin
import iad1tya.echo.music.utils.rememberPreference
import iad1tya.echo.music.viewmodels.ListenTogetherViewModel
import kotlinx.coroutines.flow.collectLatest

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListenTogetherSettings(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
    viewModel: ListenTogetherViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    
    val connectionState by viewModel.connectionState.collectAsState()
    val roomState by viewModel.roomState.collectAsState()
    val role by viewModel.role.collectAsState()
    val pendingJoinRequests by viewModel.pendingJoinRequests.collectAsState()
    val logs by viewModel.logs.collectAsState()
    val blockedUsernames by viewModel.blockedUsernames.collectAsState()
    
    val servers by ListenTogetherServers.serversFlow.collectAsState()
    var serverUrl by rememberPreference(ListenTogetherServerUrlKey, ListenTogetherServers.defaultServerUrl)
    var username by rememberPreference(ListenTogetherUsernameKey, "")
    var autoApproval by rememberPreference(ListenTogetherAutoApprovalKey, false)
    var syncHostVolume by rememberPreference(ListenTogetherSyncVolumeKey, true)
    var smartResync by rememberPreference(ListenTogetherSmartResyncKey, true)
    
    var showServerUrlDialog by rememberSaveable { mutableStateOf(false) }
    var showUsernameDialog by rememberSaveable { mutableStateOf(false) }
    var showLogsDialog by rememberSaveable { mutableStateOf(false) }
    var showBlockedUsersDialog by rememberSaveable { mutableStateOf(false) }
    
    
    LaunchedEffect(Unit) {
        viewModel.events.collectLatest { event ->
            when (event) {
                is ListenTogetherEvent.RoomCreated -> {
                    
                }
                is ListenTogetherEvent.JoinApproved -> {
                    Toast.makeText(context, "Te uniste a la sala: ${event.roomCode}", Toast.LENGTH_SHORT).show()
                }
                is ListenTogetherEvent.JoinRejected -> {
                    Toast.makeText(context, "Solicitud rechazada: ${event.reason}", Toast.LENGTH_SHORT).show()
                }
                is ListenTogetherEvent.JoinRequestReceived -> {
                    Toast.makeText(context, "${event.username} wants to join", Toast.LENGTH_SHORT).show()
                }
                is ListenTogetherEvent.Kicked -> {
                    Toast.makeText(context, "Expulsado: ${event.reason}", Toast.LENGTH_SHORT).show()
                }
                is ListenTogetherEvent.ConnectionError -> {
                    Toast.makeText(context, "Error de conexión: ${event.error}", Toast.LENGTH_SHORT).show()
                }
                is ListenTogetherEvent.ServerError -> {
                    Toast.makeText(context, "Error: ${event.message}", Toast.LENGTH_SHORT).show()
                }
                else -> {}
            }
        }
    }
    
    
    if (showServerUrlDialog) {
        ServerChooserDialog(
            servers = servers,
            currentUrl = serverUrl,
            onSelect = { server ->
                serverUrl = server.url
                showServerUrlDialog = false
            },
            onUseCustom = { customUrl ->
                serverUrl = customUrl
                showServerUrlDialog = false
            },
            onDismiss = { showServerUrlDialog = false }
        )
    }
    
    if (showUsernameDialog) {
        var tempUsername by rememberSaveable(showUsernameDialog) { mutableStateOf(username) }

        DefaultDialog(
            onDismiss = { showUsernameDialog = false },
            icon = { Icon(painterResource(R.drawable.person), contentDescription = null) },
            title = { Text(stringResource(R.string.listen_together_username)) },
            buttons = {
                TextButton(onClick = { username = ""; showUsernameDialog = false }) {
                    Text(stringResource(R.string.reset))
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(onClick = { username = tempUsername.trim(); showUsernameDialog = false }) {
                    Text(stringResource(android.R.string.ok))
                }
            }
        ) {
            OutlinedTextField(
                value = tempUsername,
                onValueChange = { tempUsername = it },
                label = { Text(stringResource(R.string.listen_together_username)) },
                leadingIcon = {
                    Icon(painterResource(R.drawable.person), contentDescription = null)
                },
                trailingIcon = {
                    if (tempUsername.isNotBlank()) {
                        IconButton(onClick = { tempUsername = "" }, onLongClick = null) {
                            Icon(painterResource(R.drawable.close), contentDescription = null)
                        }
                    }
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
    
    // The "Crear sala" / "Unirse a la sala" dialogs used to live here, guarded by
    // showCreateRoomDialog / showJoinRoomDialog — two flags that were declared and read but never
    // set to true by anything, so neither dialog could ever open from this screen. The feature
    // itself is NOT lost: the live create/join flows are in ListenTogetherScreen.kt (:388/:407) and
    // PlayerMenu.kt (:2050/:2087). Removed as unreachable duplicates.

    if (showLogsDialog) {
        LogsDialog(
            logs = logs,
            onClear = { viewModel.clearLogs() },
            onDismiss = { showLogsDialog = false }
        )
    }

    if (showBlockedUsersDialog) {
        BlockedUsersDialog(
            blockedUsernames = blockedUsernames,
            onUnblock = { viewModel.unblockUser(it) },
            onDismiss = { showBlockedUsersDialog = false }
        )
    }

    Column(
        Modifier
            .windowInsetsPadding(LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom))
            .verticalScroll(rememberScrollState())
    ) {
        Spacer(
            Modifier.windowInsetsPadding(
                LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Top)
            )
        )
        
        
        val selectedServer = remember(serverUrl) { ListenTogetherServers.findByUrl(serverUrl) }
        
        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
            IntegrationCard(
                title = stringResource(R.string.settings),
                items = listOf(
                    IntegrationCardItem(
                        icon = painterResource(R.drawable.person),
                        title = { Text(stringResource(R.string.listen_together_blocked_users)) },
                        description = {
                            Text(
                                if (blockedUsernames.isNotEmpty()) 
                                    stringResource(R.string.listen_together_blocked_users_count, blockedUsernames.size)
                                else 
                                    stringResource(R.string.listen_together_no_blocked_users)
                            )
                        },
                        onClick = if (blockedUsernames.isNotEmpty()) {
                            { showBlockedUsersDialog = true }
                        } else null
                    ),
                    IntegrationCardItem(
                        icon = painterResource(R.drawable.cloud),
                        title = { Text(stringResource(R.string.listen_together_server_url)) },
                        description = {
                            Text(
                                selectedServer?.let { server ->
                                    "${server.name} - ${server.location}"
                                } ?: serverUrl,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        onClick = { showServerUrlDialog = true }
                    ),
                    IntegrationCardItem(
                        icon = painterResource(R.drawable.person),
                        title = { Text(stringResource(R.string.listen_together_username)) },
                        description = {
                            Text(username.ifEmpty { stringResource(R.string.not_set) })
                        },
                        onClick = if (roomState == null) {
                            { showUsernameDialog = true }
                        } else {
                            { Toast.makeText(context, context.getString(R.string.listen_together_cannot_edit_username_in_room), Toast.LENGTH_SHORT).show() }
                        }
                    ),
                    IntegrationCardItem(
                        icon = painterResource(R.drawable.done),
                        title = { Text(stringResource(R.string.listen_together_auto_approval)) },
                        description = {
                            Text(stringResource(R.string.listen_together_auto_approval_desc))
                        },
                        trailingContent = {
                            Switch(
                                checked = autoApproval,
                                onCheckedChange = { autoApproval = it },
                                
                                enabled = roomState == null || role != RoomRole.GUEST,
                                thumbContent = {
                                    Icon(
                                        painter = painterResource(
                                            id = if (autoApproval) R.drawable.check else R.drawable.close
                                        ),
                                        contentDescription = null,
                                        modifier = Modifier.size(SwitchDefaults.IconSize),
                                    )
                                }
                            )
                        },
                        
                        onClick = { if (roomState == null || role != RoomRole.GUEST) autoApproval = !autoApproval }
                    ),
                    IntegrationCardItem(
                        icon = painterResource(R.drawable.volume_up),
                        title = { Text(stringResource(R.string.listen_together_sync_volume)) },
                        description = {
                            Text(stringResource(R.string.listen_together_sync_volume_desc))
                        },
                        trailingContent = {
                            Switch(
                                checked = syncHostVolume,
                                onCheckedChange = { syncHostVolume = it },
                                thumbContent = {
                                    Icon(
                                        painter = painterResource(
                                            id = if (syncHostVolume) R.drawable.check else R.drawable.close
                                        ),
                                        contentDescription = null,
                                        modifier = Modifier.size(SwitchDefaults.IconSize),
                                    )
                                }
                            )
                        },
                        onClick = { syncHostVolume = !syncHostVolume }
                    ),
                    IntegrationCardItem(
                        icon = painterResource(R.drawable.automation_slow_connecttion),
                        title = { Text(stringResource(R.string.listen_together_smart_resync)) },
                        description = {
                            Text(stringResource(R.string.listen_together_smart_resync_desc))
                        },
                        trailingContent = {
                            Switch(
                                checked = smartResync,
                                onCheckedChange = { smartResync = it },
                                thumbContent = {
                                    Icon(
                                        painter = painterResource(
                                            id = if (smartResync) R.drawable.check else R.drawable.close
                                        ),
                                        contentDescription = null,
                                        modifier = Modifier.size(SwitchDefaults.IconSize),
                                    )
                                }
                            )
                        },
                        onClick = { smartResync = !smartResync }
                    ),
                    IntegrationCardItem(
                        icon = painterResource(R.drawable.bug_report),
                        title = { Text(stringResource(R.string.listen_together_view_logs)) },
                        description = {
                            Text(stringResource(R.string.listen_together_view_logs_desc))
                        },
                        onClick = { showLogsDialog = true }
                    )
                )
            )
        }
        
        Spacer(modifier = Modifier.height(16.dp))
    }

    TopAppBar(
        title = { Text(stringResource(R.string.listen_together)) },
        navigationIcon = {
            IconButton(
                onClick = navController::navigateUp,
                onLongClick = null,
            ) {
                Icon(
                    painterResource(R.drawable.arrow_back),
                    contentDescription = null,
                )
            }
        }
    )
}

@Composable
fun LogsDialog(
    logs: List<LogEntry>,
    onClear: () -> Unit,
    onDismiss: () -> Unit
) {
    val listState = rememberLazyListState()
    
    LaunchedEffect(logs.size) {
        if (logs.isNotEmpty()) {
            listState.animateScrollToItem(logs.size - 1)
        }
    }
    
    val context = LocalContext.current

    DefaultDialog(
        onDismiss = onDismiss,
        icon = { Icon(painterResource(R.drawable.bug_report), contentDescription = null) },
        title = { Text(stringResource(R.string.listen_together_logs)) },
        buttons = {
            TextButton(
                onClick = {
                    val textToCopy = logs.joinToString("\n") { log ->
                        buildString {
                            append(log.timestamp)
                            append(" [")
                            append(log.level.name)
                            append("] ")
                            append(log.message)
                            log.details?.let { d -> append(" -- $d") }
                        }
                    }
                    val cm = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                    val clip = android.content.ClipData.newPlainText("ListenTogetherLogs", textToCopy)
                    cm.setPrimaryClip(clip)
                    Toast.makeText(context, R.string.copied_to_clipboard, Toast.LENGTH_SHORT).show()
                },
                enabled = logs.isNotEmpty()
            ) {
                Text(stringResource(R.string.copy))
            }
            TextButton(onClick = onClear) {
                Text(stringResource(R.string.clear))
            }
            Spacer(modifier = Modifier.width(8.dp))
            Button(onClick = onDismiss) {
                Text(stringResource(android.R.string.ok))
            }
        }
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(350.dp)
        ) {
            if (logs.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.listen_together_no_logs),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(logs) { log ->
                        LogEntryItem(log)
                    }
                }
            }
        }
    }
}

@Composable
private fun ServerChooserDialog(
    servers: List<ListenTogetherServer>,
    currentUrl: String,
    onSelect: (ListenTogetherServer) -> Unit,
    onUseCustom: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var customUrl by rememberSaveable(currentUrl) { mutableStateOf(currentUrl) }
    val trimmedCustomUrl = customUrl.trim()

    // ONE flag read for the dialog, handed to every server card below. These cards are drawn INSIDE
    // `DefaultDialog`, i.e. on a dialog surface rather than on the page — which is exactly why
    // `AuraPanel`'s fill is a translucent wash and not an opaque `surface`: an opaque plate would
    // stamp a mismatched rectangle per server.
    val skin = rememberAuraPanelSkin()

    DefaultDialog(
        onDismiss = onDismiss,
        icon = { Icon(painterResource(R.drawable.cloud), contentDescription = null) },
        title = { Text(stringResource(R.string.listen_together_choose_server)) },
        buttons = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.cancel))
            }
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            servers.forEach { server ->
                val isSelected = server.url == currentUrl
                AuraPanel(
                    skin = skin,
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(if (skin.enabled) Modifier.sizeIn(minHeight = AuraSpacing.MinTouchTarget) else Modifier)
                        .clickable { onSelect(server) },
                    classicShape = RoundedCornerShape(16.dp),
                    classicColors = CardDefaults.cardColors(
                        containerColor = if (isSelected) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        }
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            // The SELECTED server takes the render's "SONANDO" wash instead of a
                            // different card colour, so selection reads the same here as everywhere
                            // else in the redesign. Painted inside the panel so the hairline stays.
                            .then(
                                if (skin.enabled && isSelected)
                                    Modifier.background(skin.accent.copy(alpha = 0.10f))
                                else Modifier
                            )
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = server.name,
                                style = if (skin.enabled) AuraType.RowTitle else MaterialTheme.typography.titleSmall,
                                color = if (skin.enabled) skin.ink else Color.Unspecified,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "${server.location} - ${server.operator}",
                                style = if (skin.enabled) AuraType.RowSubtitle else MaterialTheme.typography.bodySmall,
                                color = if (skin.enabled) skin.inkMuted else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = server.url,
                                // A URL is technical data — tracked monospace in the render.
                                style = if (skin.enabled) AuraType.QualityBadge else MaterialTheme.typography.labelSmall,
                                color = if (skin.enabled) skin.inkFaint else MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        if (isSelected) {
                            Icon(
                                painter = painterResource(R.drawable.done),
                                contentDescription = null,
                                tint = if (skin.enabled) skin.accent else MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }

            if (skin.enabled) {
                HorizontalDivider(thickness = 1.dp, color = skin.hairline)
            } else {
                HorizontalDivider()
            }

            Text(
                text = stringResource(R.string.listen_together_custom_server),
                style = if (skin.enabled) AuraType.MenuGroupLabel else MaterialTheme.typography.titleSmall,
                color = if (skin.enabled) skin.inkFaint else Color.Unspecified,
                fontWeight = if (skin.enabled) FontWeight.Normal else FontWeight.SemiBold
            )
            OutlinedTextField(
                value = customUrl,
                onValueChange = { customUrl = it },
                label = { Text(stringResource(R.string.listen_together_server_url)) },
                leadingIcon = {
                    Icon(painterResource(R.drawable.link), contentDescription = null)
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Button(
                onClick = { onUseCustom(trimmedCustomUrl) },
                enabled = trimmedCustomUrl.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
                shape = if (skin.enabled) AuraShapes.Pill else RoundedCornerShape(12.dp)
            ) {
                Text(stringResource(R.string.listen_together_use_custom_server))
            }
        }
    }
}

@Composable
fun LogEntryItem(log: LogEntry) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = log.timestamp,
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.width(8.dp))
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = when (log.level) {
                    LogLevel.ERROR -> MaterialTheme.colorScheme.errorContainer
                    LogLevel.WARNING -> Color(0xFFFFF3CD)
                    LogLevel.DEBUG -> MaterialTheme.colorScheme.surfaceVariant
                    LogLevel.INFO -> MaterialTheme.colorScheme.primaryContainer
                }
            ) {
                Text(
                    text = log.level.name,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                    color = when (log.level) {
                        LogLevel.ERROR -> MaterialTheme.colorScheme.onErrorContainer
                        LogLevel.WARNING -> Color(0xFF856404)
                        LogLevel.DEBUG -> MaterialTheme.colorScheme.onSurfaceVariant
                        LogLevel.INFO -> MaterialTheme.colorScheme.onPrimaryContainer
                    }
                )
            }
        }

        Text(
            text = log.message,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace
        )
        log.details?.let { details ->
            Text(
                text = details,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun BlockedUsersDialog(
    blockedUsernames: Set<String>,
    onUnblock: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val listState = rememberLazyListState()
    // ONE flag read for the dialog; the rows below take their colours from it.
    val skin = rememberAuraPanelSkin()

    DefaultDialog(
        onDismiss = onDismiss,
        icon = { Icon(painterResource(R.drawable.person), contentDescription = null) },
        title = { Text(stringResource(R.string.listen_together_blocked_users)) },
        buttons = {
            Button(onClick = onDismiss) {
                Text(stringResource(android.R.string.ok))
            }
        }
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(280.dp)
        ) {
            if (blockedUsernames.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.listen_together_no_blocked_users),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(blockedUsernames.toList()) { username ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(if (skin.enabled) AuraShapes.Highlight else RoundedCornerShape(12.dp))
                                .background(
                                    if (skin.enabled) skin.fill else MaterialTheme.colorScheme.surfaceVariant
                                )
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.person),
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp),
                                    tint = if (skin.enabled) skin.accent else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = username,
                                    style = if (skin.enabled) AuraType.RowTitle else MaterialTheme.typography.bodyMedium,
                                    color = if (skin.enabled) skin.ink else Color.Unspecified
                                )
                            }
                            TextButton(
                                onClick = { onUnblock(username) }
                            ) {
                                Text(stringResource(R.string.unblock))
                            }
                        }
                    }
                }
            }
        }
    }
}

